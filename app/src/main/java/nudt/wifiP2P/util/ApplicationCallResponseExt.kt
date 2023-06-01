// 拓展 ApplicationCall json返回
package nudt.wifiP2P.util

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import kotlinx.serialization.Serializable

// 统一异常返回
@Serializable
data class ResponseErrorResult(
    val code: Int,
    val msg: String,
    val errMsg: String? = null,
    val errCode: Int = 0
)

// 200 成功 | 失败
@Serializable
data class Response200Result<T : Any>(
    val code: Int = 200,
    val msg: String = "success",
    val errCode: Int = 0,
    val state: Boolean = false,
    val data: T? = null
)


// 所有异常返回模板
suspend fun ApplicationCall.jsonError(
    code: HttpStatusCode,
    msg: String,
    errMsg: String? = null,
    errCode: Int = 0
) {
    respond(
        code, ResponseErrorResult(
            code.value,
            msg,
            errMsg,
            errCode
        )
    )
}

// 200 - success
suspend fun <T : Any> ApplicationCall.jsonOk(msg: String, data: T? = null) {
    respond(Response200Result(200, msg, 0, true, data = data))
}

// 200 - error
suspend fun ApplicationCall.jsonErr(msg: String, errCode: Int = 0) {
    respond(Response200Result(200, msg, errCode, false, data = null))
}

// 500
suspend fun ApplicationCall.json500(errMsg: String? = null, errCode: Int = 0) {
    jsonError(HttpStatusCode.InternalServerError, "服务器内部错误", errMsg, errCode)
}

// 404
suspend fun ApplicationCall.json404(errCode: Int = 0) {
    jsonError(HttpStatusCode.NotFound, "找不到的路径", "访问的路径被删除或者不存在!", errCode)
}

// 405
suspend fun ApplicationCall.json405(errCode: Int = 0) {
    jsonError(HttpStatusCode.NotFound, "资源被禁止", "不允许使用请求行中所指定的方法", errCode)
}

// 403
suspend fun ApplicationCall.json403(msg: String, errCode: Int = 0) {
    jsonError(HttpStatusCode.Forbidden, "验证失败禁止访问", msg, errCode)
}

// 401
suspend fun ApplicationCall.json401(msg: String, errCode: Int = 0) {
    jsonError(HttpStatusCode.Unauthorized, "当前请求需要验证", msg, errCode)
}
