package tsa.videocall.sdk.hb.subscriber

import tsa.videocall.sdk.hb.utils.TsaVideoCallError

public interface TSAVideoCallSubscriberListener {
    fun onConnected(subscriber: TSAVideoCallSubscriber)
    fun onDisconnected(subscriber: TSAVideoCallSubscriber)
    fun onError(subscriber: TSAVideoCallSubscriber, error: TsaVideoCallError)
}