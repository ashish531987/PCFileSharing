package com.rockhard.pcfilesharing

import java.net.Inet4Address
import java.net.NetworkInterface

object SpUtil {
    const val HTTP_PROTOCOL = "http://"
    const val PREFS_FILENAME = "com.rockhard.pcfilesharing.prefs"
    const val FOLDER_URI = "folder_uri"
    const val PORT = 8080

    @JvmStatic
    fun storeString(key: String, text: String) {
        val editor = App.instance.getSharedPreferences(PREFS_FILENAME, 0)!!.edit()
        editor.putString(key, text)
        editor.apply()
    }

    @JvmStatic
    fun getString(key: String, def:String): String {
        val text = App.instance.getSharedPreferences(PREFS_FILENAME, 0).getString(key, def)?:""
        return text
    }

    @JvmStatic
    fun bytesToHumanReadableSize(bytes: Long) = when {
        bytes >= 1 shl 30 -> "%.1f GB".format(bytes / (1 shl 30))
        bytes >= 1 shl 20 -> "%.1f MB".format(bytes / (1 shl 20))
        bytes >= 1 shl 10 -> "%.0f kB".format(bytes / (1 shl 10))
        else -> "$bytes bytes"
    }

    @JvmStatic
    fun getIpv4HostAddress(): String {
        NetworkInterface.getNetworkInterfaces()?.toList()?.map { networkInterface ->
            networkInterface.inetAddresses?.toList()?.find {
                !it.isLoopbackAddress && it is Inet4Address
            }?.let { return it.hostAddress }
        }
        return ""
    }
}