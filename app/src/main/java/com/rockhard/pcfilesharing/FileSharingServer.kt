package com.rockhard.pcfilesharing

import android.R
import android.R.attr.bitmap
import android.content.Context
import android.database.Cursor
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.media.ThumbnailUtils
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import android.util.Base64
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.core.net.toFile
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import com.rockhard.pcfilesharing.MainActivity.Companion.LOG_TAG
import io.ktor.http.content.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.html.*
import io.ktor.server.netty.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.util.pipeline.PipelineContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.html.*
import java.io.ByteArrayOutputStream
import java.io.FileInputStream
import java.io.FileOutputStream


class FileSharingServer(
    applicationEngine: Netty = Netty,
    port: Int = 8080,
    context: Context
    ) {
    val nettyServer = embeddedServer(applicationEngine, port){
        routing {
            get("/") {
                indexPageHandler(context)
            }
            post("/upload") {
                uploadHandler(context)
            }
        }
    }

    private suspend fun PipelineContext<Unit, ApplicationCall>.uploadHandler(
        context: Context,
    ) {
        var fileName = "myFile"
        val multipartData = call.receiveMultipart()

        multipartData.forEachPart { part ->
            part.headers.names().forEach { key ->
                Log.d(MainActivity.LOG_TAG, "Header key: $key , value : ${part.headers.get(key)}")
            }
            when (part) {
                is PartData.FormItem -> {
                    Log.d(MainActivity.LOG_TAG, "Part belongs to Form")
                }

                is PartData.FileItem -> {
                    Log.d(MainActivity.LOG_TAG, "Part belongs to File")
                    part.contentDisposition?.let { contentDisposition ->
                        fileName = part.originalFileName as String
                        DocumentFile.fromTreeUri(
                            context.applicationContext,
                            SpUtil.getString(SpUtil.FOLDER_URI, "").toUri()
                        )?.let { documentFile ->

                            val file = documentFile.createFile(
                                contentDisposition.disposition,
                                fileName
                            )
                            file?.let { it1 ->
                                context.applicationContext.contentResolver.openFileDescriptor(
                                    it1.uri,
                                    "w"
                                )
                                    ?.use { parcelFileDescriptor ->
                                        FileOutputStream(parcelFileDescriptor.fileDescriptor).use {
                                            part.streamProvider().use { its ->
                                                its.copyTo(it)
                                            }
                                            call.respondText("$fileName is uploaded successfully")
                                        }
                                    }
                            }
                        }
                    }
                }

                else -> {
                    Log.d(MainActivity.LOG_TAG, "Part doesn't belong to Form or File")
                }
            }
            part.dispose()
        }
    }

    private suspend fun PipelineContext<Unit, ApplicationCall>.indexPageHandler(
        context: Context
    ) {
        var pathParameter = call.request.queryParameters["path"].orEmpty()
        Log.d(MainActivity.LOG_TAG, "Query parameter is $pathParameter")
        if (pathParameter.isNotEmpty()) {
            // Download file
            val rootDirs =
                getRootChildFolder(context, SpUtil.getString(SpUtil.FOLDER_URI, "").toUri())
            rootDirs?.forEach { fl ->
                if (fl.name.equals(pathParameter)) {
                    // Check if its a file or folder
                    // if file then download that file
                    // else return list of children folder
                    if(fl.isFile) {
                        call.response.header(
                            "Content-Disposition",
                            "attachment; filename=\"${fl.name}\""
                        )
                        context.applicationContext.contentResolver.openFileDescriptor(fl.uri, "r")
                            ?.use { fd ->
                                FileInputStream(fd.fileDescriptor).use {
                                    withContext(Dispatchers.IO) {
                                        call.respondOutputStream {
                                            while (it.available() > 0) {
                                                write(it.readBytes())
                                            }
                                        }
                                    }
                                }
                            }
                    } else{
                        call.respondHtml(block = webPageResponse(context, fl, pathParameter))
                    }
                }
            }
        } else {
            DocumentFile.fromTreeUri(context, SpUtil.getString(SpUtil.FOLDER_URI, "").toUri())?.let{
                call.respondHtml(block = webPageResponse(context, it, it.name.toString()))
            }
        }
    }

    private val styleSheet = ".customers {\n" +
            "  font-family: Arial, Helvetica, sans-serif;\n" +
            "  border-collapse: collapse;\n" +
            "  width: 100%;\n" +
            "}\n" +
            "\n" +
            ".customers td, #customers th {\n" +
            "  border: 1px solid #ddd;\n" +
            "  padding: 8px;\n" +
            "}\n" +
            "\n" +
            ".customers tr:nth-child(even){background-color: #f2f2f2;}\n" +
            "\n" +
            ".customers tr:hover {background-color: #ddd;}\n" +
            "\n" +
            ".customers th {\n" +
            "  padding-top: 12px;\n" +
            "  padding-bottom: 12px;\n" +
            "  text-align: left;\n" +
            "  background-color: #04AA6D;\n" +
            "  color: white;\n" +
            "}\n"+
            "input[type=button], input[type=submit], input[type=reset] {\n" +
            "  background-color: #1976D2;\n" +
            "  border: none;\n" +
            "  color: white;\n" +
            "  padding: 10px 16px;\n" +
            "  text-decoration: none;\n" +
            "  margin: 4px 2px;\n" +
            "  cursor: pointer;\n" +
            "}\n"+
            "input[type=file]::file-selector-button {\n" +
            "  margin-inline-end: 0;\n" +
            "  padding: 10px 16px;\n" +
            "  background-color: #1976D2;\n" +
            "  color: white;\n" +
            "  margin: 4px 2px;\n" +
            "  border: none;\n" +
            "  border-radius: 0;\n" +
            "}\n"+
            "h4{\n" +
            "color: #1976D2;\n"+
            "}\n"

    private val scriptHtml =
            "   function toggleCheckboxes(source) {\n" +
            "    checkboxes = document.getElementsByName('foo');\n" +
            "    var i = 0; var n = checkboxes.length;\n"+
            "    while (i != n) {\n" +
            "      checkboxes[i].checked = source.checked; i++\n" +
            "    }\n" +
            "  }" +
            "  function downloadAction(source) {\n" +
            "    checkboxes = document.getElementsByName('foo');\n" +
            "    var i = 0; var n = checkboxes.length;\n"+
            "    while (i != n) {\n" +
            "      if(checkboxes[i].checked) { console.log(checkboxes[i].value);\n" +
                    "let a= document.createElement('a');\n" +
                    "a.target= '_blank';\n" +
                    "a.href= '/?Path='+checkboxes[i].value;\n" +
                    "a.click();}"+
            "      i++;\n" +
            "    }\n" +
            "  }"

    private fun webPageResponse(context: Context, parentDirDocument: DocumentFile, pathParameter: String): HTML.() -> Unit = {
        head {
            meta { charset = "utf-8" }
            meta { name = "viewport"; content = "width=device-width, initial-scale=1" }
            title { +"Mobile Remote File Sharing made easy" }
            link(href="https://fonts.googleapis.com/css2?family=Material+Symbols+Outlined:opsz,wght,FILL,GRAD@48,400,0,0", rel="stylesheet")
            style { +styleSheet }
            script{ +scriptHtml }
        }
        body {
            div {
                h4 {
                    +"Mobile File Sharing Server"
                }
                b {
                    +"${
                        SpUtil.getString(SpUtil.FOLDER_URI, "").toUri().lastPathSegment?.split(":")
                            ?.get(1)
                    }"
                }
                div {
                    p { +"Click on the \"Choose Files\" button to upload files:" }
                    form(
                        action = "/upload",
                        encType = FormEncType.multipartFormData,
                        method = FormMethod.post
                    ) {
                        fileInput(name = "myFile") {
                            multiple = true
                        }
                        submitInput { value = "Upload" }
                    }
                }
                submitInput {
                    value = "Download"
                    onClick = "downloadAction(this)"
                }
                table(classes = "customers") {
                    tr {
                        td{
                            checkBoxInput{
                                onClick="toggleCheckboxes(this)"
                            }
                        }
                        td {
                            h4 {
                                +"Type"
                            }
                        }
                        td {
                            h4 {
                                +"Files/Folders"
                            }
                        }
                        td {
                            h4 {
                                +"Size"
                            }
                        }
                    }
                    parentDirDocument.listFiles().forEach { documentFile ->
                        tr {
                            td{
                                checkBoxInput{
                                    name="foo"
                                    value="${documentFile.name}"
                                }
                            }
                            td{
                                if (documentFile.isDirectory) {
                                    span(classes = "material-symbols-outlined"){
                                        +"folder"
                                    }
                                } else {
                                    if(documentFile.name?.endsWith("jpg", ignoreCase = true) == true){
                                        context.applicationContext.contentResolver.openFileDescriptor(documentFile.uri, "r")
                                            ?.use { fd ->
                                                val bitmap = BitmapFactory.decodeFileDescriptor(fd.fileDescriptor)
                                                val thumbnail = ThumbnailUtils.extractThumbnail(bitmap, 48, 48)
                                                val baos = ByteArrayOutputStream()
                                                thumbnail.compress(Bitmap.CompressFormat.JPEG, 100, baos)
                                                val b = Base64.encodeToString(baos.toByteArray(), Base64.DEFAULT)
                                                img(src="data:image/gif;base64,$b")
                                            }
                                    } else {
                                        span(classes = "material-symbols-outlined"){
                                            +"draft"
                                        }
                                    }
                                }
                            }
                            td {
//                                if (documentFile.isDirectory) {
//                                    a(href = "?path=${documentFile.uri.lastPathSegment?.split(":")?.get(1)}", "style") {
//                                        +"${documentFile.name}"
//                                    }
//                                } else {
                                a(href = "?path=${documentFile.name}", "style") {
                                        +"${documentFile.name}"
//                                    }
                                }
                            }
                            td {
                                +"${
//                                    context.applicationContext.contentResolver.openFileDescriptor(documentFile.uri, "r")
//                                        ?.use { fd ->  fd.statSize }
//                                        ?.let {
//                                            android.text.format.Formatter.formatFileSize(
//                                                context.applicationContext,
//                                                it
//                                            )
//                                        }
                                    dumpImageMetaData(context, documentFile.uri)
                                }"
                            }
                        }
                    }
                }
            }
        }
    }
    private fun dumpImageMetaData(context: Context, uri: Uri): String {

        val contentResolver = context.contentResolver

        // The query, because it only applies to a single document, returns only
        // one row. There's no need to filter, sort, or select fields,
        // because we want all fields for one document.
        val cursor: Cursor? = contentResolver.query(
            uri, null, null, null, null, null)

        cursor?.use {
            // moveToFirst() returns false if the cursor has 0 rows. Very handy for
            // "if there's anything to look at, look at it" conditionals.
            if (it.moveToFirst()) {

                // Note it's called "Display Name". This is
                // provider-specific, and might not necessarily be the file name.
                val columnIndex = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val displayName: String =
                    it.getString(if(columnIndex > 0) columnIndex else 0)
//                Log.i(LOG_TAG, "Display Name: $displayName")

                val sizeIndex: Int = it.getColumnIndex(OpenableColumns.SIZE)
                // If the size is unknown, the value stored is null. But because an
                // int can't be null, the behavior is implementation-specific,
                // and unpredictable. So as
                // a rule, check if it's null before assigning to an int. This will
                // happen often: The storage API allows for remote files, whose
                // size might not be locally known.
                val size: String = if (!it.isNull(sizeIndex)) {
                    // Technically the column stores an int, but cursor.getString()
                    // will do the conversion automatically.
                    android.text.format.Formatter.formatFileSize(context, it.getString(sizeIndex).toLong())
                } else {
                    "Unknown"
                }
                return size
//                Log.i(LOG_TAG, "Size: $size")
            }
        }
        return "Unknown"
    }

    private fun getRootChildFolder(context: Context, dirUri: Uri): List<DocumentFile>? {
        return DocumentFile.fromTreeUri(context.applicationContext, dirUri)?.listFiles()?.toList()
    }
    public fun start(wait: Boolean){
        nettyServer.start(wait=wait)
    }
    public fun stop(gracePeriodMillis: Long){
        nettyServer.stop(gracePeriodMillis=gracePeriodMillis)
    }
}