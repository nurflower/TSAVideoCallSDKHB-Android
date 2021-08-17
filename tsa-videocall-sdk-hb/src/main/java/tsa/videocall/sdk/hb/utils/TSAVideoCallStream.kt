package tsa.videocall.sdk.hb.utils

import org.webrtc.MediaStream

class TSAVideoCallStream (private val mediaStream: MediaStream){
    fun getMediaStream(): MediaStream{
        return mediaStream
    }
}