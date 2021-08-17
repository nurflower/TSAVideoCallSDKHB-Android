package tsa.videocall.sdk.hb.utils

import android.util.Log
import org.webrtc.SdpObserver
import org.webrtc.SessionDescription

abstract class RTCSdpObserver(private val name: String): SdpObserver {

    override fun onCreateSuccess(sessionDescription: SessionDescription?) {
        Log.e(TAG, "$name => onCreateSuccess: ${sessionDescription?.type?.canonicalForm()}")
    }

    override fun onSetSuccess() {
        Log.e(TAG, "$name => onSetSuccess: ")
    }

    override fun onSetFailure(reason: String?) {
        Log.e(TAG, "$name => onSetFailure: $reason")
    }

    override fun onCreateFailure(reason: String?) {
        Log.e(TAG, "$name => onCreateFailure: $reason")
    }

    companion object {
        private val TAG: String = RTCSdpObserver::class.java.simpleName
    }
}