package com.example.trafficsigndetector

import android.app.Activity
import android.content.res.AssetManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.nfc.Tag
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Environment
import android.util.Log
import android.view.SurfaceView
import com.example.trafficsigndetector.model.tensorflow.TrafficSignDetector
import com.example.trafficsigndetector.setting.ImageSetting.MAXHEIGHT
import com.example.trafficsigndetector.setting.ImageSetting.MAXWIDTH
import org.json.JSONException
import org.opencv.android.CameraBridgeViewBase
import org.opencv.android.JavaCameraView
import org.opencv.android.OpenCVLoader
import org.opencv.android.Utils
import org.opencv.core.Core
import org.opencv.core.Mat
import org.opencv.core.Point
import org.opencv.core.Scalar
import org.opencv.imgcodecs.Imgcodecs
import org.opencv.imgproc.Imgproc
import java.io.File
import java.io.IOException
import java.io.InputStream

class MainActivity : Activity(), CameraBridgeViewBase.CvCameraViewListener2  {

    private var cameraView: JavaCameraView? = null

    private lateinit var tmpMats: Array<Mat?>
    private var emptyMat: Mat? = null

    private val TAG = "MainActivity"
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        if (!OpenCVLoader.initDebug()) {
            Log.e(TAG, "OpenCV not loaded")
        } else {
            Log.e(TAG, "OpenCV loaded")
        }
        cameraView = initCameraView()
    }

    override fun onDestroy() {
        super.onDestroy()

        trafficSignDetector?.close()

    }

    override fun onResume() {
        super.onResume()
        initCameraView()
    }

    override fun onPause() {
        super.onPause()
        if (cameraView != null){
            cameraView!!.disableView()
        }
    }


    private var trafficSignDetector: TrafficSignDetector? = null

    private fun initTFLiteModel() {
        val tmpMap: MutableMap<String?, Any?> =
            HashMap()
        tmpMap["Mat"] = tmpMats

        val funMap: MutableMap<String?, Any?> =
            HashMap()
        funMap["EmptyMat"] = emptyMat

        val othersMap: MutableMap<String?, Any?> =
            HashMap()
        othersMap["activity"] = this


        trafficSignDetector = TrafficSignDetector(tmpMap, funMap, othersMap)
    }

    private fun initCameraView(): JavaCameraView? {
        var openCvCameraView = findViewById<JavaCameraView>(R.id.HelloOpenCvView)
        openCvCameraView.setCvCameraViewListener(this)
        openCvCameraView.visibility = SurfaceView.VISIBLE
        openCvCameraView.setMaxFrameSize(MAXWIDTH, MAXHEIGHT)
        openCvCameraView.enableFpsMeter()
        openCvCameraView.enableView()
        return openCvCameraView

    }

    override fun onCameraViewStarted(width: Int, height: Int) {
        val len = 2
        tmpMats = arrayOfNulls(len)
        for (i in 0 until len) {
            tmpMats[i] = Mat()
        }
        emptyMat = Mat()

        initTFLiteModel()

    }

    override fun onCameraViewStopped() {}

    override fun onCameraFrame(inputFrame: CameraBridgeViewBase.CvCameraViewFrame): Mat {
        val rgbImg = inputFrame.rgba()

        Imgproc.cvtColor(rgbImg, rgbImg, Imgproc.COLOR_RGBA2RGB)
//        Imgproc.cvtColor(rgbImg, rgbImg, Imgproc.COLOR_RGBA2BGRA)

//        Imgproc.cvtColor(mat, mat, Imgproc.COLOR_RGBA2BGRA)
        val jsonArray = trafficSignDetector!!.detectImage(rgbImg)
//        val file =
//            File(Environment.getExternalStorageDirectory().path + File.separator + "${System.currentTimeMillis()}.jpg")
//        if (!file.exists()) {
//            file.createNewFile()
//        }
//
//        Imgcodecs.imwrite(file.path, mat)

        for (i in 0 until jsonArray!!.length()) {
            try {
                val jsonObject = jsonArray.getJSONObject(i)
                val newMinX = jsonObject.getInt("xmin")-(jsonObject.getInt("xmax")-jsonObject.getInt("xmin"))
                val newMinY = jsonObject.getInt("ymin")-(jsonObject.getInt("ymax")-jsonObject.getInt("ymin"))

                val point1 = Point(
                    newMinX.toDouble(),
                    newMinY.toDouble()
                )
                val point2 = Point(
                    jsonObject.getInt("xmin").toDouble(),
                    jsonObject.getInt("ymin").toDouble()
                )

                Imgproc.rectangle(rgbImg, point1, point2, Scalar(255.0), 3)

                Log.e(TAG, jsonObject.getString("label"))
            } catch (e: JSONException) {
                e.printStackTrace()
            }
        }
        return rgbImg
    }
}
