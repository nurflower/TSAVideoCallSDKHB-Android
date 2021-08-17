package tsa.videocall.sdk.hb.utils

import android.content.Context
import android.view.LayoutInflater
import android.widget.ImageView
import android.widget.RelativeLayout
import android.widget.TextView
import org.webrtc.SurfaceViewRenderer
import tsa.videocall.sdk.hb.R


class CustomSurfaceView(context: Context): RelativeLayout(context) {

    private var messageTextView: TextView
    private var micImageView: ImageView
    private var surfaceViewRenderer: SurfaceViewRenderer

    init {
        val layoutInflater = context.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
        layoutInflater.inflate(R.layout.surfaceview_options, this, true)

        surfaceViewRenderer = getChildAt(0) as SurfaceViewRenderer

        messageTextView = getChildAt(1) as TextView

        micImageView = getChildAt(2) as ImageView
    }

    fun getSurfaceViewRenderer(): SurfaceViewRenderer{
        return surfaceViewRenderer
    }

}