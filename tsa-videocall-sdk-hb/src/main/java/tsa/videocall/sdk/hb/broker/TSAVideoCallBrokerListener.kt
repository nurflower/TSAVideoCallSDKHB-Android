package tsa.videocall.sdk.hb.broker

import org.json.JSONObject

interface TSAVideoCallBrokerListener {
    fun onConnected()
    fun onDisconnected()
    fun onError(message: String)
    fun onBiometricsEvent(event: String, data: JSONObject)
    fun onCallEvent(event: String, data: JSONObject)
    fun onChatEvent(event: String, data: JSONObject)
    fun onRecordEvent(event: String)
}