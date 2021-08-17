package tsa.videocall.sdk.hb.session

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.media.AudioManager
import android.util.Log
import io.reactivex.schedulers.Schedulers
import org.json.JSONObject
import org.webrtc.*
import org.webrtc.audio.JavaAudioDeviceModule
import org.webrtc.voiceengine.WebRtcAudioUtils
import tsa.videocall.sdk.JanusManager
import tsa.videocall.sdk.hb.*
import tsa.videocall.sdk.hb.api.TSAVideoCallApi
import tsa.videocall.sdk.hb.api.initRetrofit
import tsa.videocall.sdk.hb.broker.TSAVideoCallBrokerListener
import tsa.videocall.sdk.hb.broker.TSAVideoCallBrokerSocket
import tsa.videocall.sdk.hb.publisher.TSAVideoCallPublisher
import tsa.videocall.sdk.hb.subscriber.TSAVideoCallSubscriber
import tsa.videocall.sdk.hb.utils.*
import tsa.videocall.sdk.hb.utils.RTCPeerConnectionObserver
import tsa.videocall.sdk.listener.JanusCallingEventListener
import tsa.videocall.sdk.listener.OnJanusListener
import tsa.videocall.sdk.model.config.JanusCommand
import tsa.videocall.sdk.model.config.JanusError
import tsa.videocall.sdk.model.config.JanusState
import tsa.videocall.sdk.plugin.JanusPlugin
import tsa.videocall.sdk.plugin.JanusPluginName
import java.util.concurrent.Executors

class TSAVideoCallSession (private val context: Context, private val config: TSAVideoCallConfig) {

    private var mSessionListener: TSAVideoCallSessionListener? = null
    private lateinit var videoRoomPlugin: JanusPlugin

    private val executor = Executors.newSingleThreadExecutor()
    private var janusWSConnected: Boolean? = null
    private val eglBase by lazy { EglBase.create() }
    private var peerConnectionFactory: PeerConnectionFactory? = null
    private var publisherPeerConnection: PeerConnection? = null
    private var publisher: TSAVideoCallPublisher? = null
    private var subscriber: TSAVideoCallSubscriber? = null
    private var subscriberPeerConnection: PeerConnection? = null
    private var publisherMediaStream: MediaStream? = null
    private var cameraVideoCapturer: CameraVideoCapturer? = null

    private var tsaVideoCallBroker: TSAVideoCallBrokerSocket? = null
    private var roomId: Long? = null

    private var tsaVideoCallBrokerListener = object : TSAVideoCallBrokerListener {

        override fun onConnected() {
            fetchRoomId()
        }

        override fun onDisconnected() {

        }

        override fun onError(message: String) {

        }

        override fun onBiometricsEvent(event: String, data: JSONObject) {
            when(event){
                "START_SELFIE" -> {
                    publisher?.makeFullScreen()
                }
                "SNAPSHOT" -> {
                    publisher?.captureScreen()
                }
                else ->{}
            }
        }

        override fun onCallEvent(event: String, data: JSONObject) {
            when(event){
                "FINISH" -> {
                    subscriber?.getMediaStream()?.let { stream ->
                        mSessionListener?.onStreamDropped(this@TSAVideoCallSession, stream)
                    }
                }
                "REDIRECT" -> {
                    val callHash = data["callHash"] as String
                    config.changeCallHash(callHash)
                    roomId = (data["room"] as Int).toLong()
                    stopSession()
                    initJanus()
                    connect()

                }
                else -> {

                }
            }
        }

        override fun onChatEvent(event: String, data: JSONObject) {
            when(event){
                "MESSAGE" -> {
                    val message = data["textMessage"] as String
                    mSessionListener?.onMessageReceived(this@TSAVideoCallSession, message)

                }
                "FILE" -> {
                    val fileName = data["filename"] as String
                    val path = data["url"] as String
                    mSessionListener?.onFileReceived(this@TSAVideoCallSession, fileName, path)

                }
                else -> { }
            }
        }

        override fun onRecordEvent(event: String) {
            if (event == "START"){
                executor.execute {
                    roomId?.let {
                        videoRoomPlugin.execute(JanusCommand.Configure(record = true))
                    }
                }
            }
        }

    }

    private fun stopSession(){
        cameraVideoCapturer?.stopCapture()
        cameraVideoCapturer?.dispose()
        publisher?.getView()?.getSurfaceViewRenderer()?.clearImage()
        publisher?.getView()?.getSurfaceViewRenderer()?.release()
        subscriber?.getView()?.getSurfaceViewRenderer()?.clearImage()
        subscriber?.getView()?.getSurfaceViewRenderer()?.release()
        publisherPeerConnection?.dispose()
        subscriberPeerConnection?.dispose()
        tsaVideoCallBroker?.brokerListener = null
        videoRoomPlugin.close()
        videoRoomPlugin.janusCallingEventListener = null
        videoRoomPlugin.onJanusListener = null
    }

    @SuppressLint("CheckResult")
    private fun fetchRoomId(){
        val map = HashMap<String, String>()
        map["callHash"] = config.getCallHash()
        TSAVideoCallApi.retrofitService.callCheck(config.getAuth(), map)
            .subscribeOn(Schedulers.io())
            .subscribe( {
                val response = JSONObject(it.string())
                if ((response["status"] as String).equals("OK", ignoreCase = true)){
                    tsaVideoCallBroker?.joinRoom((response["room"] as Int).toLong())
                    initSession((response["room"] as Int).toLong())
                }else{
                    val error = TsaVideoCallError(TsaVideoCallError.ErrorType.SessionError, TsaVideoCallError.ErrorCode.CallCheckError, message = "Cannot fetch room from callHash")
                    mSessionListener?.onError(this, error)
                }
            }) {
                val error = TsaVideoCallError(TsaVideoCallError.ErrorType.SessionError, TsaVideoCallError.ErrorCode.CallCheckRequestError, message = "Call check request error ${it.localizedMessage}")
                mSessionListener?.onError(this, error)
            }
    }

    @SuppressLint("CheckResult")
    private fun callStart(){
        val body = HashMap<String, String>()
        body["callHash"] = config.getCallHash()
        body["appVersion"] = config.getLibVersion()
        TSAVideoCallApi.retrofitService.callStart(config.getAuth(), body)
            .subscribeOn(Schedulers.io())
            .subscribe ({
                val response = JSONObject(it.string())
                if (!(response["status"] as String).equals("OK", true)){
                    val error = TsaVideoCallError(TsaVideoCallError.ErrorType.SessionError, TsaVideoCallError.ErrorCode.CallStartError, message = "Cannot send call start event")
                    mSessionListener?.onError(this, error)
                }
            },{
                val error = TsaVideoCallError(TsaVideoCallError.ErrorType.SessionError, TsaVideoCallError.ErrorCode.CallCheckRequestError, message = "Call start request error ${it.localizedMessage}")
                mSessionListener?.onError(this, error)
            })
    }

    private var onJanusListener = object : OnJanusListener{

        override fun onJanusStateChanged(plugin: JanusPluginName, state: JanusState) {
            if (state == JanusState.READY){
                videoRoomPlugin.execute(JanusCommand.CheckRoom(roomId))
            }
            if (state == JanusState.ATTACHED){
                videoRoomPlugin.execute(JanusCommand.Subscribe)
            }
            if (state == JanusState.CLOSED){
                mSessionListener?.onDisconnected(this@TSAVideoCallSession)
            }
        }

        override fun onJanusConnectionChanged(isConnected: Boolean) {
            if (janusWSConnected == false && isConnected) {
                videoRoomPlugin.execute(JanusCommand.Claim)
            }
            if (!isConnected) { mSessionListener?.onError(this@TSAVideoCallSession, TsaVideoCallError(
                TsaVideoCallError.ErrorType.SessionError, TsaVideoCallError.ErrorCode.ConnectionFailed, message = "Connection failed or closed")
            )}
            janusWSConnected = isConnected
        }
    }

    private var onJanusCallingEventListener = object : JanusCallingEventListener{

        override fun onJanusIncoming(
            handleId: Long?,
            userId: Long,
            remoteSdp: SessionDescription
        ) {
            configureSubscriberPeerConnection(handleId, userId, remoteSdp)
        }

        override fun onJanusAccepted(userId: String, remoteSdp: SessionDescription) {

        }

        override fun onJanusLocalAccepted(remoteSdp: SessionDescription) {
            setPublisherRemoteDescription(remoteSdp){}
        }

        override fun onJanusHangup() {
            mSessionListener?.onDisconnected(this@TSAVideoCallSession)
        }

        override fun onJanusError(error: JanusError) {
            val sessionError = TsaVideoCallError(errorType = TsaVideoCallError.ErrorType.SessionError, errorCode = TsaVideoCallError.ErrorCode.MediaServerError, message = "code: ${error.code} message: ${error.name}")
            mSessionListener?.onError(this@TSAVideoCallSession, sessionError)
        }

        override fun onJanusJoined(privateId: Long) {
            publisherCreateOffer()
        }

        override fun onNewRemoteFeed(feedId: Long) {
            videoRoomPlugin.execute(JanusCommand.AttachSubscriber(feedId))
        }

        override fun onUnpublished(handleId: Long?) {
            if(handleId == videoRoomPlugin.handleId){
                roomId?.let { room->
                    tsaVideoCallBroker?.sendFinish(room)
                    stopSession()
                    publisher?.onUnpublished()
                }
            }else{
                subscriber?.onUnpublished()
            }
        }

        override fun onPublisherLeft(handleId: Long?) {

        }

        override fun onTalking(handleId: Long?, audioLevel: Int?) {

        }

        override fun onStoppedTalking(handleId: Long?) {

        }

        override fun onRoomChecked(isExists: Boolean, room: Long?) {
            if (isExists){
                mSessionListener?.onConnected(this@TSAVideoCallSession)
            }else{
                videoRoomPlugin.execute(JanusCommand.CreateRoom(room))
            }
        }

        override fun onRoomCreated(room: Long?) {
            mSessionListener?.onConnected(this@TSAVideoCallSession)
        }

        override fun onSubscriberConfigured(
            handleId: Long?,
            userId: Long,
            remoteSdp: SessionDescription
        ) {
            setSubscriberRemoteDescription(peerConnection = subscriberPeerConnection, sdp = remoteSdp){
                subscriberCreateAnswer(handleId, subscriberPeerConnection)
            }
        }

        override fun onSubscriberStarted(handleId: Long?, userId: Long?) {
            roomId?.let { room ->
                tsaVideoCallBroker?.sendRemoteStream(room)
                subscriber?.onConnected()
            }
        }
    }

    private fun configureSubscriberPeerConnection(handleId: Long?, userId: Long, remoteSdp: SessionDescription) {
        executor.execute {
            val googleStun = PeerConnection.IceServer.builder(config.getIceServers() ?: defaultICEServers).createIceServer()
            val iceServers = listOf(googleStun)
            val configuration = PeerConnection.RTCConfiguration(iceServers)
                .apply {
                    tcpCandidatePolicy = PeerConnection.TcpCandidatePolicy.ENABLED
                    sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
                    bundlePolicy = PeerConnection.BundlePolicy.MAXBUNDLE
                    continualGatheringPolicy =
                        PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY
                    enableRtpDataChannel = false
                    enableDtlsSrtp = true
                    enableCpuOveruseDetection = true
                }

            subscriberPeerConnection = peerConnectionFactory?.createPeerConnection(configuration,  object : RTCPeerConnectionObserver() {

                override fun onIceGatheringChange(iceGatheringState: PeerConnection.IceGatheringState?) {
                    super.onIceGatheringChange(iceGatheringState)
                    if (iceGatheringState == PeerConnection.IceGatheringState.COMPLETE) {
                        videoRoomPlugin.execute(JanusCommand.TrickleComplete(handleId = handleId))
                    }
                }

                @SuppressLint("NullSafeMutableLiveData")
                override fun onAddStream(mediaStream: MediaStream?) {
                    super.onAddStream(mediaStream)
                    if (mediaStream != null){
                        val stream = TSAVideoCallStream(mediaStream)
                        mSessionListener?.onStreamReceived(this@TSAVideoCallSession, stream)
                    }
                }

                override fun onIceCandidate(iceCandidate: IceCandidate?) {
                    super.onIceCandidate(iceCandidate)
                    if (iceCandidate == null) {
                        videoRoomPlugin.execute(JanusCommand.TrickleComplete(handleId))
                    } else {
                        videoRoomPlugin.execute(JanusCommand.Trickle(handleId, iceCandidate))
                    }
                }

            })

            setSubscriberRemoteDescription(peerConnection = subscriberPeerConnection, sdp = remoteSdp){
                subscriberCreateAnswer(handleId, subscriberPeerConnection)
            }
        }
    }

    private fun setSubscriberRemoteDescription(peerConnection: PeerConnection?, sdp: SessionDescription, onSetSuccess: () -> Unit) {
        executor.execute {
            peerConnection?.setRemoteDescription(
                object : RTCSdpObserver("Set Remote SDP") {
                    override fun onSetSuccess() {
                        super.onSetSuccess()
                        onSetSuccess()
                    }
                },
                sdp
            )
        }
    }

    private fun subscriberCreateAnswer(handleId: Long?, peerConnection: PeerConnection?){
        executor.execute {
            peerConnection?.createAnswer(object : RTCSdpObserver("Create Answer") {
                override fun onCreateSuccess(sessionDescription: SessionDescription?) {
                    if (sessionDescription != null) {
                        super.onCreateSuccess(sessionDescription)
                        setSubscriberLocalDescription(peerConnection, sessionDescription)
                        videoRoomPlugin.execute(JanusCommand.Answer(handleId, sessionDescription))
                    }
                }
            }, MediaConstraints())
        }
    }

    private fun setSubscriberLocalDescription(peerConnection: PeerConnection?, sdp: SessionDescription) {
        peerConnection?.setLocalDescription(object : RTCSdpObserver("Set Local SDP") {
            override fun onSetSuccess() {
                Log.e(TAG, "setLocalDescription onSetSuccess: ")
            }
        }, sdp)
    }

    init {
        initJanus()
    }

    private fun initJanus(){
        JanusManager.getInstance().init(context.applicationContext)
        videoRoomPlugin = JanusManager.getInstance().videoRoomPlugin
        videoRoomPlugin.onJanusListener = onJanusListener
        videoRoomPlugin.janusCallingEventListener = onJanusCallingEventListener
    }

    fun setSessionListener(sessionListener: TSAVideoCallSessionListener){
        mSessionListener = sessionListener
    }

    fun connect(){
        initRetrofit(config.getWebURL())
        initBroker()
    }

    private fun initBroker(){
        tsaVideoCallBroker = TSAVideoCallBrokerSocket(config.getWebSocketBrokerURL(), config.getWebSocketBrokerPath())
        tsaVideoCallBroker?.applyBrokerListener(tsaVideoCallBrokerListener)
    }

    private fun initSession(roomId: Long){
        this.roomId = roomId
        initPeerConnectionFactory()
        createPeerConnectionFactory()
        videoRoomPlugin.initWS(config.getWebSocketMediaServerURL())
        videoRoomPlugin.observeWebSocket()
    }

    fun disconnect(){
        stopSession()
    }

    fun publish(publisher: TSAVideoCallPublisher){
        this.publisher = publisher
        createPublisherMediaStream()
        createPublisherPeerConnection()
        executor.execute {
            roomId?.let {
                videoRoomPlugin.execute(JanusCommand.JoinRoom(it, publisher.getUserName()))
            }
        }
    }

    internal fun unpublish(publisher: TSAVideoCallPublisher){
        executor.execute{
            videoRoomPlugin.execute(JanusCommand.Unpublish())
        }
    }

    internal fun sendSnapshot(bitmap: Bitmap){
        val base64 = Utils.convert(bitmap)
        roomId?.let { room ->
            val body = HashMap<String, String>()
            body["room"] = room.toString()
            body["selfie"] = base64
            TSAVideoCallApi.retrofitService.uploadImages(config.getAuth(), body)
                .subscribeOn(Schedulers.io())
                .subscribe ({
                    Log.d("TSAVideoCallSession", "$it")
                },{
                    Log.d("TSAVideoCallSession", "$it error")
                })

        }
    }

    fun subscribe(subscriber: TSAVideoCallSubscriber){
        this.subscriber = subscriber
    }

    internal fun unsubscribe(subscriber: TSAVideoCallSubscriber){

    }

    private fun initPeerConnectionFactory() {
        executor.execute {
            PeerConnectionFactory.initialize(
                PeerConnectionFactory.InitializationOptions.builder(context)
                    .setFieldTrials
                        ("WebRTC-IntelVP8/Enabled/WebRTC-H264HighProfile/Enabled/WebRTC-MediaTekH264/Enabled/")
                    .setEnableInternalTracer(true)
                    .createInitializationOptions()
            )
        }
    }

    private fun createPeerConnectionFactory() {
        executor.execute {
            val decoderFactory = DefaultVideoDecoderFactory(eglBase.eglBaseContext)
            val encoderFactory = DefaultVideoEncoderFactory(eglBase.eglBaseContext, true, true)

            val audioDeviceModule = JavaAudioDeviceModule
                .builder(context)
                .setUseHardwareAcousticEchoCanceler(true)
                .setUseHardwareNoiseSuppressor(true)
                .createAudioDeviceModule()

            val options = PeerConnectionFactory.Options()
            peerConnectionFactory = PeerConnectionFactory.builder()
                .setOptions(options)
                .setAudioDeviceModule(audioDeviceModule)
                .setVideoDecoderFactory(decoderFactory)
                .setVideoEncoderFactory(encoderFactory)
                .createPeerConnectionFactory()
            audioDeviceModule.release()
        }
    }

    private fun createPublisherPeerConnection() {
        executor.execute {
            val googleStun = PeerConnection.IceServer.builder(config.getIceServers() ?: defaultICEServers).createIceServer()
            val iceServers = listOf(googleStun)

            val configuration = PeerConnection.RTCConfiguration(iceServers)
                .apply {
                    tcpCandidatePolicy = PeerConnection.TcpCandidatePolicy.ENABLED
                    sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
                    bundlePolicy = PeerConnection.BundlePolicy.MAXBUNDLE
                    continualGatheringPolicy =
                        PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY
                    enableRtpDataChannel = false
                    enableDtlsSrtp = true
                    enableCpuOveruseDetection = true
                }

            publisherPeerConnection = peerConnectionFactory?.createPeerConnection(configuration, object : RTCPeerConnectionObserver(){
                override fun onIceGatheringChange(iceGatheringState: PeerConnection.IceGatheringState?) {
                    super.onIceGatheringChange(iceGatheringState)
                    if (iceGatheringState == PeerConnection.IceGatheringState.COMPLETE) {
                        videoRoomPlugin.execute(JanusCommand.TrickleComplete())
                    }
                }

                @SuppressLint("NullSafeMutableLiveData")
                override fun onAddStream(mediaStream: MediaStream?) {
                    super.onAddStream(mediaStream)
                }

                override fun onIceCandidate(iceCandidate: IceCandidate?) {
                    super.onIceCandidate(iceCandidate)
                    if (iceCandidate == null) {
                        videoRoomPlugin.execute(JanusCommand.TrickleComplete())
                    } else {
                        videoRoomPlugin.execute(JanusCommand.Trickle(ice = iceCandidate))
                    }
                }
            })

            publisherMediaStream?.let { localMediaStream ->
                localMediaStream.audioTracks.firstOrNull()?.let {
                    publisherPeerConnection?.addTrack(it)
                }
                localMediaStream.videoTracks.firstOrNull()?.let {
                    publisherPeerConnection?.addTrack(it)
                }
            }
        }
    }

    private fun createPublisherMediaStream() {
        executor.execute {
            val localMediaStream = peerConnectionFactory?.createLocalMediaStream(LOCAL_MEDIA_IDS)
                .also {
                    this.publisherMediaStream = it
                }
            localMediaStream?.addTrack(createPublisherAudioSource())
            localMediaStream?.addTrack(createPublisherVideoSource())
            publisher?.setMediaStream(localMediaStream)
        }
    }

    private fun createPublisherAudioSource(): AudioTrack? {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        audioManager.isSpeakerphoneOn = true
        audioManager.mode = AudioManager.MODE_IN_COMMUNICATION

        WebRtcAudioUtils.setWebRtcBasedAcousticEchoCanceler(true)
        WebRtcAudioUtils.setWebRtcBasedNoiseSuppressor(true)
        WebRtcAudioUtils.setWebRtcBasedAutomaticGainControl(true)

        val audioConstraints = MediaConstraints()
            .apply {
                mandatory.add(MediaConstraints.KeyValuePair("googEchoCancellation", "true"))
                mandatory.add(MediaConstraints.KeyValuePair("googEchoCancellation2", "true"))
                mandatory.add(MediaConstraints.KeyValuePair("googDAEchoCancellation", "true"))
                mandatory.add(MediaConstraints.KeyValuePair("googAutoGainControl", "true"))
                mandatory.add(MediaConstraints.KeyValuePair("googAutoGainControl2", "true"))
                mandatory.add(MediaConstraints.KeyValuePair("googNoiseSuppression", "true"))
                mandatory.add(MediaConstraints.KeyValuePair("googNoiseSuppression2", "true"))
                mandatory.add(MediaConstraints.KeyValuePair("googTypingNoiseDetection", "true"))
                mandatory.add(MediaConstraints.KeyValuePair("googHighpassFilter", "true"))
                mandatory.add(MediaConstraints.KeyValuePair("googAudioMirroring", "false"))
            }


        val audioSource = peerConnectionFactory?.createAudioSource(audioConstraints)
        return peerConnectionFactory?.createAudioTrack(AUDIO_TRACK_ID, audioSource)
    }

    private fun createPublisherVideoSource(): VideoTrack? {
        val cameraEnumerator = if (Camera2Enumerator.isSupported(context)) {
            Camera2Enumerator(context)
        } else {
            Camera1Enumerator(true)
        }

        fun findDeviceCamera(
            cameraEnumerator: CameraEnumerator,
            frontFacing: Boolean
        ) =
            cameraEnumerator.deviceNames.firstOrNull { cameraEnumerator.isFrontFacing(it) == frontFacing }


        var deviceName = findDeviceCamera(cameraEnumerator, true)
        if (deviceName == null) {
            deviceName = findDeviceCamera(cameraEnumerator, false)
        }
        cameraVideoCapturer = cameraEnumerator.createCapturer(deviceName, null)

        val surfaceTextureHelper = SurfaceTextureHelper.create("CaptureThread", eglBase.eglBaseContext)

        val videoSource = peerConnectionFactory?.createVideoSource(cameraVideoCapturer?.isScreencast ?: false)

        cameraVideoCapturer?.initialize(
            surfaceTextureHelper,
            context,
            videoSource?.capturerObserver
        )
        cameraVideoCapturer?.startCapture(1280, 720, 30)
        return peerConnectionFactory?.createVideoTrack(VIDEO_TRACK_ID, videoSource)
    }

    private fun publisherCreateOffer() {
        executor.execute {
            val mediaConstraints = MediaConstraints()
                .apply {
                    mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
                    mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", "true"))
                }
            publisherPeerConnection?.createOffer(object : RTCSdpObserver("Create Offer") {
                override fun onCreateSuccess(sessionDescription: SessionDescription?) {
                    if (sessionDescription != null) {
                        super.onCreateSuccess(sessionDescription)
                        setPublisherLocalDescription(sessionDescription)
                        videoRoomPlugin.execute(JanusCommand.Call(sessionDescription, userId = publisher?.getUserName()))
                    }
                }
            }, mediaConstraints)
        }
    }

    private fun setPublisherLocalDescription(sessionDescription: SessionDescription){

        publisherPeerConnection?.setLocalDescription(object : RTCSdpObserver("Set Local SDP") {
            override fun onSetSuccess() {
                Log.d(TAG, "setLocalDescription onSetSuccess")
            }
        }, sessionDescription)
    }

    private fun setPublisherRemoteDescription(sessionDescription: SessionDescription, onSetSuccess: () -> Unit) {
        executor.execute {
            publisherPeerConnection?.setRemoteDescription(
                object : RTCSdpObserver("Set Remote SDP") {
                    override fun onSetSuccess() {
                        super.onSetSuccess()
                        onSetSuccess()
                        publisher?.onPublished()
                        callStart()
                    }
                },
                sessionDescription
            )
        }
    }

    fun getPublisher(): TSAVideoCallPublisher?{
        return publisher
    }

    fun getSubscriber(): TSAVideoCallSubscriber?{
        return subscriber
    }

    internal fun getEglBaseContext(): EglBase.Context{
        return eglBase.eglBaseContext
    }


    internal fun configureVideo(video: Boolean){
        executor.execute {
            publisherMediaStream?.videoTracks?.get(0)?.setEnabled(video)
            videoRoomPlugin.execute(JanusCommand.ConfigureMedia(video = video))
        }
    }

    internal fun configureAudio(audio: Boolean){
        executor.execute {
            publisherMediaStream?.audioTracks?.get(0)?.setEnabled(audio)
            videoRoomPlugin.execute(JanusCommand.ConfigureMedia(audio = audio))
        }
    }

    internal fun switchCamera(){
        cameraVideoCapturer?.switchCamera(null)
    }


    companion object {
        private val TAG: String = TSAVideoCallSession::class.java.simpleName

        private val defaultICEServers = listOf(
            "stun:stun.l.google.com:19302",
            "stun:stun1.l.google.com:19302",
            "stun:stun2.l.google.com:19302",
            "stun:stun3.l.google.com:19302",
            "stun:stun4.l.google.com:19302"
        )

        private const val LOCAL_MEDIA_IDS = "ARDAMS"
        private const val VIDEO_TRACK_ID = "ARDAMSv0"
        private const val AUDIO_TRACK_ID = "ARDAMSa0"

    }
}