package com.rockhard.pcfilesharing

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.html.*
import io.ktor.server.netty.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.html.*


class MainActivity : AppCompatActivity() {
    companion object {
        const val ACTION_STOP_FOREGROUND = "${BuildConfig.APPLICATION_ID}.stop"
        const val LOG_TAG = "PC_FILE_SHARING"
        const val REQUEST_CODE = 12123
    }

    private val viewModel: MainActivityViewModel by viewModels()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        val tvDirName = findViewById<TextView>(R.id.tv_dir_name)
        viewModel.currentDirName.observe(this) {
            it?.let { selectedDir ->
                tvDirName.text = Uri.decode(selectedDir.toUri().lastPathSegment)
            }
        }
        findViewById<Button>(R.id.btn_select_dir).setOnClickListener {
            openDocumentTree()
        }
        findViewById<Button>(R.id.btn_start_sharing).setOnClickListener {
            startService()
        }
        findViewById<Button>(R.id.btn_stop_sharing).setOnClickListener {
            stopService()
        }
        val tvUrl = findViewById<TextView>(R.id.tv_url)
        viewModel.currentAppUrl.observe(this) {
            it?.let {
                tvUrl.text = it
            }
        }
        Log.d(
            LOG_TAG,
            "Notification enabled : ${
                NotificationManagerCompat.from(this).areNotificationsEnabled()
            }"
        )
    }

    private fun openDocumentTree() {
//        val uriString = viewModel.getRootDir()
//        when {
//            uriString == "" -> {
//                Log.w(LOGTAG, "uri not stored")
        askPermission()
//            }
//            arePermissionsGranted(uriString) -> {
////                makeDoc(Uri.parse(uriString))
//            }
//            else -> {
//                Log.w(LOGTAG, "uri permission not stored")
//                askPermission()
//            }
//        }
    }

    private fun askPermission() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
        startActivityForResult(intent, REQUEST_CODE)
    }

    private fun arePermissionsGranted(uriString: String): Boolean {
        // list of all persisted permissions for our app
        val list = contentResolver.persistedUriPermissions
        for (i in list.indices) {
            val persistedUriString = list[i].uri.toString()
            //Log.d(LOGTAG, "comparing $persistedUriString and $uriString")
            if (persistedUriString == uriString && list[i].isWritePermission && list[i].isReadPermission) {
                //Log.d(LOGTAG, "permission ok")
                return true
            }
        }
        return false
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode == RESULT_OK && requestCode == REQUEST_CODE) {
            if (data != null) {
                //this is the uri user has provided us
                val treeUri: Uri? = data.data
                if (treeUri != null) {
                    Log.i(LOG_TAG, "got uri: $treeUri")
                    // here we should do some checks on the uri, we do not want root uri
                    // because it will not work on Android 11, or perhaps we have some specific
                    // folder name that we want, etc
                    if (Uri.decode(treeUri.toString()).endsWith(":")) {
                        Toast.makeText(this, "Cannot use root folder!", Toast.LENGTH_SHORT).show()
                        // consider asking user to select another folder
                        return
                    }
                    // here we ask the content resolver to persist the permission for us
                    val takeFlags: Int = Intent.FLAG_GRANT_READ_URI_PERMISSION or
                            Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                    contentResolver.takePersistableUriPermission(
                        treeUri,
                        takeFlags
                    )

                    // we should store the string fo further use
                    viewModel.setRootDir(treeUri.toString())
                }
            }
        }
    }

    private fun startService() {
        val serviceIntent = Intent(this, KtorService::class.java)
        serviceIntent.putExtra("inputExtra", "Foreground Service Example in Android")
        ContextCompat.startForegroundService(this, serviceIntent)
    }

    private fun stopService() {
        val intentStop = Intent(this, KtorService::class.java)
        intentStop.action = ACTION_STOP_FOREGROUND
        startService(intentStop)
    }
}