package com.example.trafficsigndetector.model.tensorflow

import org.json.JSONException
import org.json.JSONObject

public class CarCommand {

    fun generateCommand(o: Int, v: Int, c: Int, d: Int, r: Int, a: Int): String {
        val jsonObject = JSONObject()
        try {
            jsonObject.put("o", o)
            jsonObject.put("v", v)
            jsonObject.put("c", c)
            jsonObject.put("d", d)
            jsonObject.put("r", r)
            jsonObject.put("a", a)
        } catch (e: JSONException) {
            e.printStackTrace()
        }

        return jsonObject.toString()
    }
}