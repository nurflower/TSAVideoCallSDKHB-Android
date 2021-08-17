package tsa.videocall.sdk.hb.broker

import android.util.Log
import io.socket.client.IO
import io.socket.client.Socket
import io.socket.emitter.Emitter
import org.json.JSONObject

class TSAVideoCallBrokerSocket(brokerURL: String, brokerPath: String) {

    private var socket: Socket? = null
    var brokerListener: TSAVideoCallBrokerListener? = null

    private var onConnect = Emitter.Listener {
        brokerListener?.onConnected()
    }

    private var onDisconnect = Emitter.Listener {
        brokerListener?.onDisconnected()
    }

    private var onEvent = Emitter.Listener {

        val message = it[0] as JSONObject
        val operation = message["operation"] as String
        val event = message["event"] as String

        when(operation){
            "BIOMETRICS" -> {
                val data = message["data"] as JSONObject
                brokerListener?.onBiometricsEvent(event, data)
            }
            "CALL" -> {
                val data = message["data"] as JSONObject
                brokerListener?.onCallEvent(event, data)
            }
            "RECORD" -> {
                brokerListener?.onRecordEvent(event)
            }
            "CHAT" -> {
                val data = message["data"] as JSONObject
                brokerListener?.onChatEvent(event, data)
            }
            else -> {

            }
        }
        Log.d("TSAVideoCallBroker", message.toString())
    }

    private var onError = Emitter.Listener {
        Log.e("TSAVideoCallBroker", "$it" )
    }

    init {
        val options = IO.Options()
        options.path = brokerPath

        socket = IO.socket(brokerURL, options)
        socket?.once(Socket.EVENT_CONNECT, onConnect)
        socket?.on(Socket.EVENT_DISCONNECT, onDisconnect)
        socket?.on(Socket.EVENT_ERROR, onError)
        socket?.on("event", onEvent)
        socket?.connect()
    }


    fun applyBrokerListener(brokerListener: TSAVideoCallBrokerListener){
        this.brokerListener = brokerListener
    }

    fun joinRoom(room: Long){
        val operation = JSONObject()
        operation.put("operation", "ROOM")
        operation.put("event","JOIN")
        operation.put("data", room)
        socket?.emit("event", operation)
    }

    fun sendMessage(room: Long, message: String){
        val operation = JSONObject()
        operation.put("operation", "CHAT")
        operation.put("event", "MESSAGE")
        operation.put("room", room)
        operation.put("textMessage", message)
        socket?.emit("event", operation)
    }


    fun sendFinish(room: Long){
        val operation = JSONObject()
        operation.put("operation", "CALL")
        operation.put("event", "FINISH")
        val data = JSONObject()
        data.put("room", room)
        operation.put("data", data)
        socket?.emit("event", operation)
    }

    fun sendRemoteStream(room: Long){
        val operation = JSONObject()
        operation.put("operation", "JANUS")
        operation.put("event", "REMOTE_STREAM")
        val data = JSONObject()
        data.put("room", room)
        operation.put("data", data)
        socket?.emit("event", operation)
    }

    fun disconnect(){
        socket?.close()
    }
}