package nudt.wifiP2P.util

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.plugins.statuspages.*

fun Application.configureException() {
    // 处理http 404 或 500等请求
    install(StatusPages) {
        // 配置500
        exception<Throwable> { call, err ->
            call.error(getMessage(err))
            call.json500(getMessage(err))
        }
        // 处理http 404
        status(HttpStatusCode.NotFound) { call, _ ->
            call.json404()
        }
        // 处理http 405
        status(HttpStatusCode.MethodNotAllowed) { call, _ ->
            call.json405()
        }
    }
}

// 获取异常全部信息
private fun getMessage(err: Throwable): String {
    val strBuffer = StringBuffer("${err.message}\n")
    for (se in err.stackTrace) {
        strBuffer.append("\tat ${se.className}(${se.fileName}:${se.lineNumber})\n")
    }
    strBuffer.deleteCharAt(strBuffer.length - 1)
    strBuffer.append("}")
    return strBuffer.toString()
}
