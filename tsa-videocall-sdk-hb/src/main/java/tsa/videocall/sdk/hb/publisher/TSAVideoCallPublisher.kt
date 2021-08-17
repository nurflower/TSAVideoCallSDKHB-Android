package tsa.videocall.sdk.hb.publisher

import android.content.Context
import android.graphics.Bitmap
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.RelativeLayout
import androidx.constraintlayout.widget.ConstraintLayout
import io.reactivex.android.schedulers.AndroidSchedulers
import org.webrtc.EglRenderer
import org.webrtc.MediaStream
import org.webrtc.RendererCommon
import tsa.videocall.sdk.hb.utils.CustomSurfaceView
import tsa.videocall.sdk.hb.session.TSAVideoCallSession

class TSAVideoCallPublisher(context: Context, private val session: TSAVideoCallSession) {

    private var publisherListener: TSAVideoCallPublisherListener? = null
    private var renderer: CustomSurfaceView? = null
    private var userName: String? = null

    private var root: View? = null
    private var rootLayoutParams: ViewGroup.LayoutParams? = null

    init {
        AndroidSchedulers.mainThread().scheduleDirect {
            renderer = CustomSurfaceView(context)
            renderer?.getSurfaceViewRenderer()?.apply {
                init(session.getEglBaseContext(), null)
                setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FILL)
                setMirror(true)
                setZOrderOnTop(true)
            }
        }
    }

    fun setPublisherListener(listener: TSAVideoCallPublisherListener){
        this.publisherListener = listener
    }

    fun setParentContainer(view: FrameLayout){
        this.root = view
        this.rootLayoutParams = view.layoutParams
    }

    fun setUserName(name: String){
        this.userName = name
    }

    fun getUserName(): String{
        return userName ?: "Unknown"
    }

    internal fun setMediaStream(mediaStream: MediaStream?){
        AndroidSchedulers.mainThread().scheduleDirect {
            mediaStream?.let { track->
                track.videoTracks.firstOrNull()?.addSink(renderer?.getSurfaceViewRenderer())
            }
        }
    }

    internal fun onPublished(){
        publisherListener?.onStreamCreated(this)
        AndroidSchedulers.mainThread().scheduleDirect {
            (root as ViewGroup).addView(renderer)
        }
    }

    internal fun onUnpublished(){
        publisherListener?.onStreamDestroyed(this)
    }

    internal fun captureScreen(){
        renderer?.getSurfaceViewRenderer()?.addFrameListener(object : EglRenderer.FrameListener{
            override fun onFrame(p0: Bitmap?) {
                p0?.let { bitmap ->
                    session.sendSnapshot(bitmap)
                }
                AndroidSchedulers.mainThread().scheduleDirect {
                    renderer?.getSurfaceViewRenderer()?.removeFrameListener(this)
                    root?.layoutParams = rootLayoutParams
                    session.getSubscriber()?.getView()?.visibility = View.VISIBLE
                }
            }
        }, 1F)
    }

    internal fun makeFullScreen(){
        AndroidSchedulers.mainThread().scheduleDirect {
            session.getSubscriber()?.getView()?.visibility = View.GONE
            when (root?.layoutParams) {
                is ConstraintLayout.LayoutParams -> {
                    root?.layoutParams = ConstraintLayout.LayoutParams(ConstraintLayout.LayoutParams.MATCH_PARENT, ConstraintLayout.LayoutParams.MATCH_PARENT)
                }
                is LinearLayout.LayoutParams -> {
                    root?.layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.MATCH_PARENT)
                }
                is FrameLayout.LayoutParams -> {
                    root?.layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
                }
                is RelativeLayout.LayoutParams -> {
                    root?.layoutParams = RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.MATCH_PARENT, RelativeLayout.LayoutParams.MATCH_PARENT)
                }
            }
        }
    }

    fun getView() : CustomSurfaceView?{
        return renderer
    }

    fun cameraOn(){
        session.configureVideo(true)

    }

    fun cameraOff(){
        session.configureVideo(false)
    }

    fun micOn(){
        session.configureAudio(true)
    }

    fun micOff(){
        session.configureAudio(false)
    }

    fun switchCamera(){
        session.switchCamera()
    }

    fun hangup(){
        session.unpublish(this)
    }

}
