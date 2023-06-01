package nudt.wifiP2P.util

import android.content.Context
import android.os.Build
import com.unisound.client.SpeechConstants
import com.unisound.client.SpeechSynthesizer

object TTSPlayer {
    private var mTTSPlayer: SpeechSynthesizer? = null
    private val CPU_ABI = Build.SUPPORTED_ABIS

    @Synchronized
    fun getInstance(context: Context): SpeechSynthesizer? {
        if (mTTSPlayer == null) {
            mTTSPlayer = SpeechSynthesizer(
                context, "uoqs45cwipg7ui6hiwsks4mwgu3shbx52rmylvq3",
                "55b032057956265c7407b4a7de56081e"
            )
            mTTSPlayer!!.setOption(
                SpeechConstants.TTS_SERVICE_MODE,
                SpeechConstants.TTS_SERVICE_MODE_LOCAL
            )
            mTTSPlayer!!.setOption(
                SpeechConstants.TTS_KEY_FRONTEND_MODEL_PATH,
                context.filesDir.toString() + "/unisound/tts/frontend_model"
            )
            mTTSPlayer!!.setOption(
                SpeechConstants.TTS_KEY_BACKEND_MODEL_PATH,
                context.filesDir.toString() + "/unisound/tts/backend_lzl"
            )
            mTTSPlayer!!.init("")
        }
        return mTTSPlayer
    }

    @JvmStatic
    fun playText(context: Context, text: String?) {
        if (isCPUNotSupport) {
            return
        }
        getInstance(context)!!.playText(text)
    }

    fun init(context: Context) {
        if (isCPUNotSupport) {
            return
        }
        getInstance(context)
    }

    private val isCPUNotSupport: Boolean
        private get() {
            var support = false
            for (abi in CPU_ABI) {
                if (abi == "armeabi") {
                    support = true
                    break
                }
            }
            return !support
        }
}