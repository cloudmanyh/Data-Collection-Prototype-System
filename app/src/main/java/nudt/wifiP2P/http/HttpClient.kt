package nudt.wifiP2P.http

import android.util.Log
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.android.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.logging.*
import io.ktor.client.request.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import nudt.wifiP2P.bean.DevicePlanBean
import nudt.wifiP2P.common.Constants

object HttpClient {
    public var host: String = "localhost"

    val client = HttpClient(Android) {
        install(Logging) {
            logger = object : Logger {
                override fun log(message: String) {
                    Log.d("HttpClient", message)
                }
            }
            level = LogLevel.ALL
        }
        install(ContentNegotiation) {
            json()
        }
        engine {
            connectTimeout = 100_000
            socketTimeout = 100_000
        }
    }

    suspend fun canSend(
        id: Int,
    ): DevicePlanBean {
        return client.get("http://$host:${Constants.HTTP_PORT}/canSend") {
            parameter("id", id)
        }.body()
    }

    suspend inline fun <reified T> get(
        path: String,
        crossinline block: HttpRequestBuilder.() -> Unit = {}
    ): Flow<T> {
        return flow {
            emit(client.get("http://$host:${Constants.HTTP_PORT}$path") {
                apply(block)
            }.body())
        }
    }

    suspend inline fun <reified T> post(
        path: String,
        crossinline block: HttpRequestBuilder.() -> Unit = {}
    ): Flow<T> {
        return flow {
            client.post("$host:${Constants.HTTP_PORT}$path") {
                apply(block)
            }.let {
                emit(it.body())
            }
        }
    }
}