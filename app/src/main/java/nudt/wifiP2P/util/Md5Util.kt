package nudt.wifiP2P.util

import android.os.ParcelFileDescriptor
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.io.InputStream
import java.security.MessageDigest

object Md5Util {
    fun getMd5(file: File?): String? {
        var inputStream: InputStream? = null
        val buffer = ByteArray(8192)
        var numRead: Int
        val md5: MessageDigest
        return try {
            inputStream = FileInputStream(file)
            md5 = MessageDigest.getInstance("MD5")
            while (inputStream.read(buffer).also { numRead = it } > 0) {
                md5.update(buffer, 0, numRead)
            }
            inputStream.close()
            inputStream = null
            md5ToString(md5.digest())
        } catch (e: Exception) {
            null
        } finally {
            if (inputStream != null) {
                try {
                    inputStream.close()
                } catch (e: IOException) {
                    e.printStackTrace()
                }
            }
        }
    }

    fun getMd5(file: ParcelFileDescriptor?): String? {
        var inputStream: InputStream? = null
        val buffer = ByteArray(8192)
        var numRead: Int
        val md5: MessageDigest
        return try {
            inputStream = FileInputStream(file!!.fileDescriptor)
            md5 = MessageDigest.getInstance("MD5")
            while (inputStream.read(buffer).also { numRead = it } > 0) {
                md5.update(buffer, 0, numRead)
            }
            inputStream.close()
            inputStream = null
            md5ToString(md5.digest())
        } catch (e: Exception) {
            null
        } finally {
            if (inputStream != null) {
                try {
                    inputStream.close()
                } catch (e: IOException) {
                    e.printStackTrace()
                }
            }
        }
    }

    private fun md5ToString(md5Bytes: ByteArray): String {
        val hexValue = StringBuilder()
        for (b in md5Bytes) {
            val `val` = b.toInt() and 0xff
            if (`val` < 16) {
                hexValue.append("0")
            }
            hexValue.append(Integer.toHexString(`val`))
        }
        return hexValue.toString()
    }
}