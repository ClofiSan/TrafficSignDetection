package com.example.trafficsigndetector.setting

object TensorFlowSetting {
    const val DIM_BATCH_SIZE = 1
    const val DIM_PIXEL_SIZE = 3

    const val DIM_IMG_SIZE_X = 300
    const val DIM_IMG_SIZE_Y = 300

    const val MODELFILE = "graph.tflite"
    const val LABEL_PATH = "retrained_labels.txt"
}