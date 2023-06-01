package nudt.wifiP2P

import android.annotation.SuppressLint
import android.app.Activity
import android.app.Service
import android.content.Intent
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

@SuppressLint("Registered")
open class BaseActivity : AppCompatActivity() {
    protected fun setTitle(title: String?) {
        val actionBar = supportActionBar
        if (actionBar != null) {
            actionBar.title = title
        }
    }

    protected fun <T : Activity?> startActivity(tClass: Class<T>?) {
        startActivity(Intent(this, tClass))
    }

    protected fun <T : Service?> startService(tClass: Class<T>?) {
        startService(Intent(this, tClass))
    }

    protected fun <T : Service?> stopService(tClass: Class<T>?) {
        stopService(Intent(this, tClass))
    }

    protected fun showToast(message: String?) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
}