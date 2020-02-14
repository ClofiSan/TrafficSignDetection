package com.example.trafficsigndetector

import android.app.Activity
import android.nfc.Tag
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.view.SurfaceView
import com.example.trafficsigndetector.model.tensorflow.TrafficSignDetector
import org.json.JSONException
import org.opencv.android.CameraBridgeViewBase
import org.opencv.android.JavaCameraView
import org.opencv.android.OpenCVLoader
import org.opencv.core.Core
import org.opencv.core.Mat
import org.opencv.core.Point
import org.opencv.core.Scalar
import org.opencv.imgproc.Imgproc

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
        val jsonArray = trafficSignDetector!!.detectImage(rgbImg)
        Log.e(TAG,"before inference")


        for (i in 0 until jsonArray!!.length()) {
            try {
                val jsonObject = jsonArray.getJSONObject(i)
//                val point1 = Point(
//                    jsonObject.getInt("xmin").toDouble(),
//                    jsonObject.getInt("ymin").toDouble()
//                )
//
//                val point2 = Point(
//                    jsonObject.getInt("xmax").toDouble(),
//                    jsonObject.getInt("ymax").toDouble()
//                )

//                Core.rectangle(rgbImg, point1, point2, Scalar(255.0), 3)

                Log.e(TAG, jsonObject.getString("label"))
            } catch (e: JSONException) {
                e.printStackTrace()
            }
        }
        Log.e(TAG,"after inference")
        return rgbImg
    }
}
