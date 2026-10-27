package com.movies.premium.data.remote.simkl

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import okhttp3.Interceptor
import okhttp3.Response

/**
 * Where the replica reads the user's SIMKL **client id** from.
 *
 * **The original's client id is not shipped.** The original embeds the SIMKL client id
 * `f75f84b1…fdc5ba` in three places — the `simkl-api-key` header its OkHttp interceptor
 * adds (`com.app.mlounge.di.d`), the body of `POST oauth2/device`
 * (`SimklDeviceCodeRequest`) and the body of `POST oauth2/token`
 * (`SimklDeviceTokenRequest`). That credential belongs to the original *developer*:
 * every request made with it is billed and attributed to their SIMKL application, so
 * shipping it would make the replica impersonate their app.
 *
 * The replica therefore expects the person running it to register their own client at
 * `https://simkl.com/account/apiclients` and to enter the id in the app. The id is
 * then read back through this interface.
 *
 * [SimklModule] provides the implementation over the preferences store:
 *
 * ```kotlin
 * @Provides
 * @Singleton
 * fun simklClientIdProvider(prefs: AppPreferences): SimklClientIdProvider =
 *     FlowSimklClientIdProvider(prefs.simklClientId) { prefs.setSimklClientId(it) }
 * ```
 */
interface SimklClientIdProvider {

    /** The client id the user registered with SIMKL, or null when there is none. */
    suspend fun clientId(): String?

    /** Stores (or, with null, forgets) the client id. */
    suspend fun setClientId(value: String?)
}

/**
 * The last known SIMKL client id, kept so that the OkHttp interceptor can read it
 * without blocking.
 *
 * [SimklApiKeyInterceptor] takes a **non-suspend** `() -> String?`, because OkHttp calls
 * interceptors on its own worker threads. Reading the DataStore from there would mean
 * blocking that thread (or reaching for a `runBlocking`, which is just as bad), so the
 * value is mirrored into this holder instead:
 *
 * - [observe] collects the preferences flow on a background scope, so the holder is
 *   up to date before the first request is made;
 * - [update] is called by the provider whenever the id is read or written.
 *
 * The holder is a singleton; see `SimklModule` for the wiring.
 */
class SimklClientIdCache {

    @Volatile
    private var cached: String? = null

    /** The last known client id, or null when there is none. */
    fun get(): String? = cached

    /** Stores the last known client id; a blank value clears the holder. */
    fun update(value: String?) {
        cached = value?.trim()?.takeIf { it.isNotEmpty() }
    }

    /**
     * Keeps the holder in step with [flow].
     *
     * The collection runs on [scope]; the caller owns the scope's lifetime.
     */
    fun observe(flow: Flow<String?>, scope: CoroutineScope) {
        scope.launch {
            flow.collect { update(it) }
        }
    }
}

/**
 * A [SimklClientIdProvider] backed by a flow and a write function.
 *
 * @param source where the stored client id comes from.
 * @param sink where a new client id is written to.
 * @param cache the holder the OkHttp interceptor reads; when it is given, the provider
 * keeps it up to date on every read and write.
 */
class FlowSimklClientIdProvider(
    private val source: Flow<String?>,
    private val sink: suspend (String?) -> Unit,
    private val cache: SimklClientIdCache? = null,
) : SimklClientIdProvider {

    override suspend fun clientId(): String? {
        val value = source.first()
        cache?.update(value)
        return value
    }

    override suspend fun setClientId(value: String?) {
        cache?.update(value)
        sink(value)
    }
}

/**
 * The provider used when no client id has been configured.
 *
 * It behaves like a device without a registered client: the device-code login cannot
 * start and the `simkl-api-key` header is left out.
 */
object NoSimklClientId : SimklClientIdProvider {
    override suspend fun clientId(): String? = null
    override suspend fun setClientId(value: String?) = Unit
}

/**
 * Adds the `simkl-api-key` header SIMKL expects.
 *
 * Mirrors the original's `com.app.mlounge.di.d`, which adds the header and
 * `Content-Type: application/json`. The original hard-codes the client id; the replica
 * takes it from the user, so the header is only added when one is configured — an
 * empty `simkl-api-key` would make SIMKL reject the call with a confusing error.
 *
 * [SimklModule] wires this into the SIMKL OkHttp client:
 *
 * ```kotlin
 * base.newBuilder()
 *     .addInterceptor(SimklApiKeyInterceptor { cache.get() })
 *     .build()
 * ```
 *
 * @param clientId supplies the client id at request time; it is read on every call so
 * that a client id entered after the client was built is picked up. It must not block.
 */
class SimklApiKeyInterceptor(
    private val clientId: () -> String?,
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val builder = chain.request().newBuilder()
            .addHeader("Content-Type", "application/json")
        clientId()?.trim()?.takeIf { it.isNotEmpty() }?.let { key ->
            builder.addHeader(HEADER_API_KEY, key)
        }
        return chain.proceed(builder.build())
    }

    companion object {
        /** The header the original's interceptor sends. */
        const val HEADER_API_KEY = "simkl-api-key"
    }
}
