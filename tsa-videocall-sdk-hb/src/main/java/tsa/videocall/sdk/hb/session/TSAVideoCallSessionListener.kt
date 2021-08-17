package tsa.videocall.sdk.hb.session

import tsa.videocall.sdk.hb.utils.TSAVideoCallStream
import tsa.videocall.sdk.hb.utils.TsaVideoCallError

interface TSAVideoCallSessionListener {

    fun onConnected(session: TSAVideoCallSession)
    fun onDisconnected(session: TSAVideoCallSession)
    fun onStreamReceived(session: TSAVideoCallSession, stream: TSAVideoCallStream)
    fun onStreamDropped(session: TSAVideoCallSession, stream: TSAVideoCallStream)
    fun onMessageReceived(session: TSAVideoCallSession, message: String)
    fun onFileReceived(session: TSAVideoCallSession, fileName: String, filePath: String)
    fun onError(session: TSAVideoCallSession, error: TsaVideoCallError)


}