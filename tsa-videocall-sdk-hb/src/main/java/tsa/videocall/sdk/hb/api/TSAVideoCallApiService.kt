package tsa.videocall.sdk.hb.api

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import io.reactivex.Observable
import io.reactivex.plugins.RxJavaPlugins
import okhttp3.ResponseBody
import retrofit2.Retrofit
import retrofit2.adapter.rxjava2.RxJava2CallAdapterFactory
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST

private val moshi = Moshi.Builder()
    .add(KotlinJsonAdapterFactory())
    .build()

private var retrofit: Retrofit? = null

fun initRetrofit(url: String){
    retrofit = Retrofit.Builder()
        .addConverterFactory(MoshiConverterFactory.create(moshi))
        .addCallAdapterFactory(RxJava2CallAdapterFactory.create())
        .baseUrl(url)
        .build()
}

interface TSAVideoCallApiService {

    @POST("call/check")
    fun callCheck(@Header("Authorization") auth: String, @Body body: Map<String, String>): Observable<ResponseBody>

    @POST("call/start")
    fun callStart(@Header("Authorization") auth: String, @Body body: Map<String, String>): Observable<ResponseBody>

    @POST("file/uploadImages")
    fun uploadImages(@Header("Authorization") auth: String, @Body body: Map<String, String>): Observable<ResponseBody>

}

object TSAVideoCallApi{
    val retrofitService: TSAVideoCallApiService by lazy {
        retrofit!!.create(TSAVideoCallApiService::class.java)
    }
}