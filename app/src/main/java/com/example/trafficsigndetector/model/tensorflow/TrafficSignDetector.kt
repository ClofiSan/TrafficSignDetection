package com.example.trafficsigndetector.model.tensorflow

import android.app.Activity
import android.content.res.AssetManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import com.example.trafficsigndetector.setting.ImageSetting
import com.example.trafficsigndetector.setting.TensorFlowSetting
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import org.opencv.android.Utils
import android.os.Bundle
import android.os.Environment
import org.opencv.core.Mat
import org.opencv.core.Size
import org.opencv.imgcodecs.Imgcodecs
import org.opencv.imgproc.Imgproc
import org.tensorflow.lite.Interpreter
import java.io.*
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

class TrafficSignDetector(
    tmpMap: Map<String?, Any?>,
    funMap: Map<String?, Any?>,
    othersMap: Map<String?, Any?>
){
    private val TAG = "TrafficSignDetector"
    private var tfliteModel: MappedByteBuffer? = null
    protected var tflite: Interpreter? = null

    var labelList: MutableList<String?>? = null

    private val intValues =
        IntArray(TensorFlowSetting.DIM_IMG_SIZE_X * TensorFlowSetting.DIM_IMG_SIZE_Y)
    protected var imgData: ByteBuffer? = null

    private val tmpMats: Array<Mat>? = tmpMap["Mat"] as Array<Mat>?
    private val emptyMat: Mat? = funMap["EmptyMat"] as Mat?

    private lateinit var boxArray: Array<Array<FloatArray>>
    private lateinit var labelArray: Array<FloatArray>
    private lateinit var scoreArray: Array<FloatArray>
    private lateinit var numArray: FloatArray

    init {
        try {

            val activity = (othersMap["activity"] as Activity?)!!
            tfliteModel = loadModelFile(activity)
            labelList = loadLabelList(activity)
            val tfliteOptions =
                Interpreter.Options()
            tfliteOptions.setUseNNAPI(true)
            tfliteOptions.setNumThreads(2)
            tflite = Interpreter(tfliteModel!!, tfliteOptions)

            imgData = ByteBuffer.allocateDirect(
                4 * TensorFlowSetting.DIM_BATCH_SIZE *
                        TensorFlowSetting.DIM_IMG_SIZE_X *
                        TensorFlowSetting.DIM_IMG_SIZE_Y *
                        TensorFlowSetting.DIM_PIXEL_SIZE)
            imgData!!.order(ByteOrder.nativeOrder())

            boxArray = Array(1) { Array(10) { FloatArray(4) } }
            labelArray = Array(1) { FloatArray(10) }
            scoreArray = Array(1) { FloatArray(10) }
            numArray = FloatArray(1)

        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    private fun convertBitmapToByteBuffer(bitmap: Bitmap) {
        if (imgData == null) {
            Log.e(TAG,"imageData is null")
            return
        }

        imgData!!.rewind()

        bitmap.getPixels(
            intValues,
            0,
            bitmap.width,
            0,
            0,
            bitmap.width,
            bitmap.height
        )
//        imgData!!.rewind()

        var pixel = 0
        for (i in 0 until TensorFlowSetting.DIM_IMG_SIZE_X) {
            for (j in 0 until TensorFlowSetting.DIM_IMG_SIZE_Y) {
                val v = intValues[pixel++]
                imgData!!.putFloat((((v shr 16 and 0xFF ) - 128 ) / 128.0).toFloat())
                imgData!!.putFloat((((v shr 8 and 0xFF )- 128 ) / 128.0).toFloat())
                imgData!!.putFloat((((v and 0xFF) - 128) / 128.0).toFloat())
            }
        }
    }
    fun detectImage(src: Mat): JSONArray? {
        val processedMat = tmpMats!![1]
        emptyMat!!.copyTo(processedMat)

        Imgproc.resize(src, processedMat, Size(300.0, 300.0))

        if (tflite != null) {
            val bmp = Bitmap.createBitmap(
                processedMat.width(), processedMat.height(),
                Bitmap.Config.ARGB_8888
            )
            Utils.matToBitmap(processedMat, bmp)

            convertBitmapToByteBuffer(bmp)

            val inputs = arrayOf<Any?>(imgData)
            val outputs: MutableMap<Int?, Any?> =
                HashMap<Int?, Any?>()
            outputs[0] = boxArray
            outputs[1] = labelArray
            outputs[2] = scoreArray
            outputs[3] = numArray
            tflite!!.runForMultipleInputsOutputs(inputs, outputs)
            val jsonArray = JSONArray()
            for (i in 0 until numArray[0].toInt()) {
                if (scoreArray[0][i] > 0.7) {
                    val jsonObject = JSONObject()
                    val ymin = (boxArray[0][i][0] * ImageSetting.MAXHEIGHT).toInt()
                    val xmin = (boxArray[0][i][1] * ImageSetting.MAXWIDTH).toInt()
                    val ymax = (boxArray[0][i][2] * ImageSetting.MAXHEIGHT).toInt()
                    val xmax = (boxArray[0][i][3] * ImageSetting.MAXWIDTH).toInt()
//                    Log.e(TAG,xmin.toString())
                    try {
                        jsonObject.put("ymin", ymin)
                        jsonObject.put("xmin", xmin)
                        jsonObject.put("ymax", ymax)
                        jsonObject.put("xmax", xmax)
                        jsonObject.put("score", scoreArray[0][i])
                        jsonObject.put("label", labelList!![labelArray[0][i].toInt()])
                        jsonArray.put(jsonObject)
                    } catch (e: JSONException) {
                        e.printStackTrace()
                    }
                }
            }
            return jsonArray

        }
        return null
    }


    @Throws(IOException::class)
    private fun loadLabelList(activity: Activity?): MutableList<String?> {
        val labels: MutableList<String?> =
            ArrayList()
        val reader = BufferedReader(
            InputStreamReader(
                activity!!.assets.open(TensorFlowSetting.LABEL_PATH)
            )
        )
        var line: String?
        while (reader.readLine().also { line = it } != null) {
            labels.add(line)
        }
        reader.close()

        return labels
    }

    @Throws(IOException::class)
    private fun loadModelFile(activity: Activity?): MappedByteBuffer {
        val fileDescriptor =
            activity!!.assets.openFd(TensorFlowSetting.MODELFILE)
        val inputStream =
            FileInputStream(fileDescriptor.fileDescriptor)
        val fileChannel = inputStream.channel
        val startOffset = fileDescriptor.startOffset
        val declaredLength = fileDescriptor.declaredLength

        return fileChannel.map(
            FileChannel.MapMode.READ_ONLY,
            startOffset,
            declaredLength
        )
    }

    fun close() {
        if (tflite != null) {
            tflite!!.close()
            tflite = null
        }
        tfliteModel = null
    }


}