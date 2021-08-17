package tsa.videocall.sdk.hb.subscriber

import android.content.Context
import android.view.View
import android.view.ViewGroup
import io.reactivex.android.schedulers.AndroidSchedulers
import org.webrtc.RendererCommon
import tsa.videocall.sdk.hb.utils.CustomSurfaceView
import tsa.videocall.sdk.hb.session.TSAVideoCallSession
import tsa.videocall.sdk.hb.utils.TSAVideoCallStream
import tsa.videocall.sdk.hb.utils.TsaVideoCallError

class TSAVideoCallSubscriber(context: Context, private val stream: TSAVideoCallStream, session: TSAVideoCallSession) {

    private var renderer: CustomSurfaceView? = null

    init {
        AndroidSchedulers.mainThread().scheduleDirect {
            renderer = CustomSurfaceView(context)
            renderer?.getSurfaceViewRenderer()?.init(session.getEglBaseContext(), null)
            renderer?.getSurfaceViewRenderer()?.setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FILL)
            renderer?.getSurfaceViewRenderer()?.setMirror(true)
            stream.getMediaStream().let { track->
                track.videoTracks.firstOrNull()?.addSink(renderer?.getSurfaceViewRenderer())
            }
        }
    }

    private var subscriberListener: TSAVideoCallSubscriberListener? = null

    fun setSubscriberListener(listener: TSAVideoCallSubscriberListener){
        this.subscriberListener = listener
    }

    fun setParentContainer(view: View){
        AndroidSchedulers.mainThread().scheduleDirect {
            (view as ViewGroup).addView(renderer)
        }
    }

    fun getView() : CustomSurfaceView?{
        return renderer
    }

    internal fun getMediaStream(): TSAVideoCallStream{
        return stream
    }

    internal fun onUnpublished(){
        subscriberListener?.onDisconnected(this)
    }

    internal fun onConnected(){
        subscriberListener?.onConnected(this)
    }

    internal fun onError(tsaVideoCallError: TsaVideoCallError){
        subscriberListener?.onError(this, tsaVideoCallError)
    }

}