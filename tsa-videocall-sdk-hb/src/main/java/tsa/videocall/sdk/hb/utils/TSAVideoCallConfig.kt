package tsa.videocall.sdk.hb.utils


class TSAVideoCallConfig(private val webURL: String,
                         private val webSocketMediaServerURL: String,
                         private val webSocketBrokerURL: String,
                         private val webSocketBrokerPath: String,
                         private var callHash: String,
                         private val authData: String,
                         private val iceServers: List<String>? = null) {
    private val libVersion = "0.0.1"

    fun getWebURL(): String{
        return webURL
    }

    fun getWebSocketMediaServerURL(): String{
        return webSocketMediaServerURL
    }

    fun getWebSocketBrokerURL(): String{
        return webSocketBrokerURL
    }

    fun getWebSocketBrokerPath(): String{
        return webSocketBrokerPath
    }

    fun getCallHash(): String{
        return callHash
    }

    fun getAuth(): String{
        return " Basic $authData"
    }

    fun getLibVersion(): String{
        return libVersion
    }

    fun changeCallHash(callHash: String){
        this.callHash = callHash
    }

    fun getIceServers(): List<String>?{
        return iceServers
    }
}