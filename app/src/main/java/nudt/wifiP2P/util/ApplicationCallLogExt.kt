package nudt.wifiP2P.util

/**
 * 只为 ApplicationCall 拓展日志简化访问
 */

import io.ktor.server.application.*
import io.ktor.server.request.*

private fun ApplicationCall.logStr(): String {
    val path = this.request.path()
    val httpMethod = this.request.httpMethod.value
    val userAgent = this.request.headers["User-Agent"]
    return "Path: $path, HTTPMethod: $httpMethod, UserAgent: $userAgent \n"
}

fun ApplicationCall.info(msg: String) = this.application.environment.log.info(msg)
fun ApplicationCall.info(format: String, vararg arguments: Any) =
    this.application.environment.log.info(format, *arguments)

fun ApplicationCall.info(msg: String, t: Throwable) = this.application.environment.log.info(msg, t)

fun ApplicationCall.debug(msg: String) = this.application.environment.log.debug(msg)
fun ApplicationCall.debug(format: String, vararg arguments: Any) =
    this.application.environment.log.debug(format, *arguments)

fun ApplicationCall.debug(msg: String, t: Throwable) =
    this.application.environment.log.debug(msg, t)

fun ApplicationCall.warn(msg: String) = this.application.environment.log.warn(msg)
fun ApplicationCall.warn(format: String, vararg arguments: Any) =
    this.application.environment.log.warn(format, *arguments)

fun ApplicationCall.warn(msg: String, t: Throwable) = this.application.environment.log.warn(msg, t)

fun ApplicationCall.error(msg: String) {
    this.application.environment.log.error(msg)
    this.application.environment.log.error(this.logStr())
}

fun ApplicationCall.error(format: String, vararg arguments: Any) =
    this.application.environment.log.error(format, *arguments)

fun ApplicationCall.error(msg: String, t: Throwable) =
    this.application.environment.log.error(msg, t)


fun Application.info(msg: String) = this.environment.log.info(msg)
fun Application.info(format: String, vararg arguments: Any) =
    this.environment.log.info(format, *arguments)

fun Application.info(msg: String, t: Throwable) = this.environment.log.info(msg, t)

fun Application.debug(msg: String) = this.environment.log.debug(msg)
fun Application.debug(format: String, vararg arguments: Any) =
    this.environment.log.debug(format, *arguments)

fun Application.debug(msg: String, t: Throwable) = this.environment.log.debug(msg, t)

fun Application.warn(msg: String) = this.environment.log.warn(msg)
fun Application.warn(format: String, vararg arguments: Any) =
    this.environment.log.warn(format, *arguments)

fun Application.warn(msg: String, t: Throwable) = this.environment.log.warn(msg, t)

fun Application.error(msg: String) = this.environment.log.error(msg)
fun Application.error(format: String, vararg arguments: Any) =
    this.environment.log.error(format, *arguments)

fun Application.error(msg: String, t: Throwable) = this.environment.log.error(msg, t)
