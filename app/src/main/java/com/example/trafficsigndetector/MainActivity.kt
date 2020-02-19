package com.example.trafficsigndetector

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.pm.PackageManager
import android.content.res.AssetManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.nfc.Tag
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Message
import android.util.Log
import android.view.SurfaceView
import android.view.View
import android.view.Window
import android.view.WindowManager
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.example.trafficsigndetector.model.tensorflow.CarCommand
import com.example.trafficsigndetector.model.tensorflow.TrafficSignDetector
import com.example.trafficsigndetector.setting.ImageSetting.MAXHEIGHT
import com.example.trafficsigndetector.setting.ImageSetting.MAXWIDTH
import com.example.trafficsigndetector.util.PermissionUtils
import com.example.trafficsigndetector.util.PermissionUtils.requestMultiPermissions
import com.example.trafficsigndetector.util.PermissionUtils.requestPermissionsResult
import com.example.trafficsigndetector.util.USBSerial
import com.hoho.android.usbserial.util.SerialInputOutputManager
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
import java.util.*
import kotlin.collections.HashMap

class MainActivity : Activity(), CameraBridgeViewBase.CvCameraViewListener2  {

    private var usbSerial: USBSerial? = null
    private var carCommand: CarCommand? = null
    private var command: String? = null

    var o=0
    var v=0
    var c=0
    var d=0
    var r=0
    var a=0


    private var trafficSignDetector: TrafficSignDetector? = null

    private var cameraView: JavaCameraView? = null

    private lateinit var tmpMats: Array<Mat?>
    private var emptyMat: Mat? = null

    private val TAG = "MainActivity"
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
//        setScreen()
        setContentView(R.layout.activity_main)
        if (!OpenCVLoader.initDebug()) {
            Log.e(TAG, "OpenCV not loaded")
        } else {
            Log.e(TAG, "OpenCV loaded")
        }


    }
    
    override fun onDestroy() {
        super.onDestroy()
        trafficSignDetector?.close()
    }

    override fun onResume() {
        super.onResume()
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.CAMERA
            ) != PackageManager.PERMISSION_GRANTED || ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.BLUETOOTH
            ) != PackageManager.PERMISSION_GRANTED || ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.BLUETOOTH_ADMIN
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermission()
        } else {
            initCameraView()
        }
    }

    override fun onPause() {
        super.onPause()
        if (cameraView != null){
            cameraView!!.disableView()
        }
    }

    /*
    * serial
    * */
    private val serialListener = object : SerialInputOutputManager.Listener {

        override fun onRunError(e: Exception) {
            Log.d("msg", "错误")
        }

        override fun onNewData(data: ByteArray) {
            val msg = String(data)
            Log.e("msg", "这是我从串口接收的信息：$msg")
        }
    }
    private fun initSerial() {
        usbSerial = USBSerial(this, serialListener)
        usbSerial!!.initUsbSerial()
        initovcdra()
        sendCommandTimer.schedule(sendCommandTask, 150, 150)
    }

    private fun initovcdra(){
        o=0
        v=10
        c=0
        d=0
        r=0
        a=0
    }
    private val controlHandler = @SuppressLint("HandlerLeak")
    object : Handler() {
        override fun handleMessage(msg: Message) {
            super.handleMessage(msg)
            if (msg.what == 0) {
                Log.d("msg",command)
                if (usbSerial!!.isConnect()) {
                    usbSerial!!.sendMsg(command)
                }
            }
        }
    }
    internal var sendCommandTimer = Timer()
    internal var sendCommandTask: TimerTask = object : TimerTask() {
        override fun run() {
            carCommand = CarCommand()
            command = carCommand?.run { generateCommand(o, v, c, d, r, a)}
            controlHandler.sendEmptyMessage(0)
        }
    }

    /**
     * Permission
     */
    private fun requestPermission() {
        requestMultiPermissions(this, mPermissionGrant)
    }
    private val mPermissionGrant: PermissionUtils.PermissionGrant = object : PermissionUtils.PermissionGrant {
        override fun onPermissionGranted(requestCode: Int) {
            Toast.makeText(
                this@MainActivity,
                "Result Permission Grant CODE_MULTI_PERMISSION",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<String>,
        grantResults: IntArray
    ) {
        requestPermissionsResult(
            this,
            requestCode,
            permissions,
            grantResults,
            mPermissionGrant
        )
        cameraView = initCameraView()
    }

    /*
    * detect
    * */


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
    private fun setScreen() {
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window.setFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN,
            WindowManager.LayoutParams.FLAG_FULLSCREEN
        )
        requestWindowFeature(Window.FEATURE_NO_TITLE)
    }

    private fun initCameraView(): JavaCameraView? {
        var openCvCameraView = findViewById<JavaCameraView>(R.id.HelloOpenCvView)
        openCvCameraView.setCvCameraViewListener(this)
        openCvCameraView.visibility = SurfaceView.VISIBLE
        openCvCameraView.setMaxFrameSize(MAXWIDTH, MAXHEIGHT)
        openCvCameraView.enableFpsMeter()
        openCvCameraView.enableView()
        initSerial()
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

    override fun onCameraViewStopped() {

    }

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

        if (jsonArray!!.length()!=0){
            r = 5
            a = 30
            for (i in 0 until jsonArray!!.length()) {
                try {
                    val jsonObject = jsonArray.getJSONObject(i)
                    val newMinX = jsonObject.getInt("xmin")-(jsonObject.getInt("xmax")-jsonObject.getInt("xmin"))
                    val newMinY = jsonObject.getInt("ymin")-(jsonObject.getInt("ymax")-jsonObject.getInt("ymin"))

                    val point1 = Point(
                        jsonObject.getInt("xmin").toDouble(),
                        jsonObject.getInt("ymin").toDouble()
                    )
                    val point2 = Point(
                        jsonObject.getInt("xmax").toDouble(),
                        jsonObject.getInt("ymax").toDouble()
                    )

                    Imgproc.rectangle(rgbImg, point1, point2, Scalar(255.0), 3)

                    Log.e(TAG, jsonObject.getString("label"))
                } catch (e: JSONException) {
                    e.printStackTrace()
                }
            }
        }else{
            r = 0
            a = 0
        }

        return rgbImg
    }
}
