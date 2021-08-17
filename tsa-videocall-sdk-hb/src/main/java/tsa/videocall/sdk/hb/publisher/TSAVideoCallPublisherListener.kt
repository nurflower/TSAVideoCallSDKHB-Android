package tsa.videocall.sdk.hb.publisher

import tsa.videocall.sdk.hb.utils.TsaVideoCallError

public interface TSAVideoCallPublisherListener {
    fun onStreamCreated(publisher: TSAVideoCallPublisher)
    fun onStreamDestroyed(publisher: TSAVideoCallPublisher)
    fun onError(publisher: TSAVideoCallPublisher, error: TsaVideoCallError)
}