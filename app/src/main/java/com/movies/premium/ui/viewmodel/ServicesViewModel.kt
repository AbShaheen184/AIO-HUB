package com.movies.premium.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.movies.premium.data.local.prefs.AppPreferences
import com.movies.premium.data.repository.ServiceAccount
import com.movies.premium.data.repository.ServiceId
import com.movies.premium.data.repository.ServicesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ServiceUiState(
    val service: ServiceId,
    val connected: Boolean = false,
    val account: ServiceAccount? = null,
    val validating: Boolean = false,
    val error: String? = null,
)

data class ServicesUiState(
    val services: List<ServiceUiState> = ServiceId.entries.map { ServiceUiState(it) },
)

/**
 * Backs the services screen.
 *
 * The original shows one card per supported service, each with the account details
 * once the user has signed in. Tokens are stored in [AppPreferences] so the sign-in
 * survives a restart.
 */
@HiltViewModel
class ServicesViewModel @Inject constructor(
    private val servicesRepository: ServicesRepository,
    private val preferences: AppPreferences,
) : SafeViewModel() {

    private val _uiState = MutableStateFlow(ServicesUiState())
    val uiState: StateFlow<ServicesUiState> = _uiState.asStateFlow()

    /**
     * The SIMKL **client id** the user registered.
     *
     * The original hard-codes its own SIMKL client id, so the replica does not ship
     * one: the user registers an application at `https://simkl.com/account/apiclients`
     * and pastes the id here. Without it the device-code login cannot start and the
     * `simkl-api-key` header is left out of every request.
     */
    val simklClientId: StateFlow<String?> = preferences.simklClientId
        .stateIn(scope, SharingStarted.WhileSubscribed(5_000), null)

    /** Stores (or, with null, forgets) the SIMKL client id. */
    fun setSimklClientId(value: String?) {
        scope.launch { preferences.setSimklClientId(value?.trim()?.takeIf { it.isNotEmpty() }) }
    }

    init {
        loadTokens()
    }

    /**
     * The EasyNews user name, or null when the user is not signed in.
     *
     * The original derives the "signed in" state from a non-empty stored user
     * name — it never asks easynews.com whether the account works.
     */
    val easynewsUsername: StateFlow<String?> = preferences.easynewsUsername
        .stateIn(scope, SharingStarted.WhileSubscribed(5_000), null)

    /** Reads the stored tokens and refreshes the account details of linked services. */
    private fun loadTokens() {
        scope.launch {
            val tokens = mapOf(
                ServiceId.SIMKL to preferences.simklToken.first(),
                ServiceId.TORBOX to preferences.torboxToken.first(),
                ServiceId.REALDEBRID to preferences.realDebridToken.first(),
                ServiceId.TRAKT to preferences.traktToken.first(),
                ServiceId.ALLDEBRID to preferences.allDebridToken.first(),
                ServiceId.PREMIUMIZE to preferences.premiumizeToken.first(),
                ServiceId.OFFCLOUD to preferences.offcloudToken.first(),
            )
            _uiState.value = _uiState.value.copy(
                services = ServiceId.entries.map { service ->
                    val token = tokens[service]
                    ServiceUiState(service = service, connected = !token.isNullOrBlank())
                },
            )
            tokens.forEach { (service, token) ->
                if (!token.isNullOrBlank()) refreshAccount(service, token)
            }
            loadEasynews()
        }
    }

    /**
     * Reads the stored EasyNews account.
     *
     * The original stores the user name and the password in the plaintext and
     * calls the account "signed in" as soon as the user name is not empty.
     */
    private suspend fun loadEasynews() {
        val username = preferences.easynewsUsername.first()
        if (username.isNullOrBlank()) return
        update(ServiceId.EASYNEWS) {
            it.copy(
                connected = true,
                account = ServiceAccount(ServiceId.EASYNEWS, listOf("Username" to username)),
            )
        }
    }

    /**
     * Validates [token] against [service] and stores it when the service accepts it.
     *
     * A token the service rejects is not stored, matching the original's behaviour of
     * only keeping a working credential.
     */
    fun connect(service: ServiceId, token: String) {
        if (token.isBlank()) return
        // EasyNews is not a token service — it has its own sign-in.
        if (service == ServiceId.EASYNEWS) return
        scope.launch {
            update(service) { it.copy(validating = true, error = null) }
            servicesRepository.validate(service, token.trim())
                .onSuccess { account ->
                    storeToken(service, token.trim())
                    update(service) {
                        it.copy(validating = false, connected = true, account = account, error = null)
                    }
                }
                .onFailure { error ->
                    update(service) {
                        it.copy(
                            validating = false,
                            connected = false,
                            error = error.message?.takeIf { m -> m.isNotBlank() } ?: "Couldn't connect",
                        )
                    }
                }
        }
    }

    /** Forgets the stored credential for [service]. */
    fun disconnect(service: ServiceId) {
        scope.launch {
            if (service == ServiceId.EASYNEWS) {
                preferences.setEasynewsCredentials(null, null)
            } else {
                storeToken(service, null)
            }
            update(service) { it.copy(connected = false, account = null, error = null) }
        }
    }

    /**
     * Signs in to EasyNews with the user's name and password.
     *
     * The original does not ask easynews.com whether the account works — it
     * stores the pair and calls the account "signed in" as soon as the user
     * name is not empty. The replica does the same.
     */
    fun connectEasynews(username: String, password: String) {
        val name = username.trim()
        if (name.isEmpty()) return
        scope.launch {
            preferences.setEasynewsCredentials(name, password)
            update(ServiceId.EASYNEWS) {
                it.copy(
                    connected = true,
                    account = ServiceAccount(ServiceId.EASYNEWS, listOf("Username" to name)),
                    error = null,
                )
            }
        }
    }

    private fun refreshAccount(service: ServiceId, token: String) {
        scope.launch {
            servicesRepository.validate(service, token)
                .onSuccess { account ->
                    update(service) { it.copy(account = account) }
                }
        }
    }

    private suspend fun storeToken(service: ServiceId, token: String?) {
        when (service) {
            ServiceId.SIMKL -> preferences.setSimklToken(token)
            ServiceId.TORBOX -> preferences.setTorboxToken(token)
            ServiceId.REALDEBRID -> preferences.setRealDebridToken(token)
            ServiceId.EASYNEWS -> preferences.setEasynewsCredentials(null, null)
            ServiceId.TRAKT -> preferences.setTraktToken(token)
            ServiceId.ALLDEBRID -> preferences.setAllDebridToken(token)
            ServiceId.PREMIUMIZE -> preferences.setPremiumizeToken(token)
            ServiceId.OFFCLOUD -> preferences.setOffcloudToken(token)
        }
    }

    private fun update(service: ServiceId, transform: (ServiceUiState) -> ServiceUiState) {
        _uiState.value = _uiState.value.copy(
            services = _uiState.value.services.map {
                if (it.service == service) transform(it) else it
            },
        )
    }
}
