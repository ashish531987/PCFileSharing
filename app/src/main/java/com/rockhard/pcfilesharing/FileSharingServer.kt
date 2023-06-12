package com.rockhard.pcfilesharing

import android.content.Context
import android.database.Cursor
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.ThumbnailUtils
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Base64
import android.util.Log
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import com.rockhard.pcfilesharing.MainActivity.Companion.LOG_TAG
import io.ktor.http.content.PartData
import io.ktor.http.content.forEachPart
import io.ktor.http.content.streamProvider
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.call
import io.ktor.server.engine.embeddedServer
import io.ktor.server.html.respondHtml
import io.ktor.server.netty.Netty
import io.ktor.server.request.receiveMultipart
import io.ktor.server.response.header
import io.ktor.server.response.respondOutputStream
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import io.ktor.util.pipeline.PipelineContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.html.FormEncType
import kotlinx.html.FormMethod
import kotlinx.html.HTML
import kotlinx.html.a
import kotlinx.html.b
import kotlinx.html.body
import kotlinx.html.checkBoxInput
import kotlinx.html.div
import kotlinx.html.fileInput
import kotlinx.html.form
import kotlinx.html.h4
import kotlinx.html.head
import kotlinx.html.img
import kotlinx.html.link
import kotlinx.html.meta
import kotlinx.html.onClick
import kotlinx.html.p
import kotlinx.html.script
import kotlinx.html.span
import kotlinx.html.style
import kotlinx.html.submitInput
import kotlinx.html.table
import kotlinx.html.td
import kotlinx.html.title
import kotlinx.html.tr
import java.io.ByteArrayOutputStream
import java.io.FileInputStream
import java.io.FileOutputStream


private const val PATH_PARAM = "path"

class FileSharingServer(
    applicationEngine: Netty = Netty,
    port: Int = 8080,
    context: Context
) {
    private val nettyServer = embeddedServer(applicationEngine, port) {
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
        val pathParameter = call.request.queryParameters[PATH_PARAM].orEmpty()
        val pathParameterDocumentFiles = ArrayList<DocumentFile>()
        if (pathParameter.isNotEmpty()) {
            Log.d(
                MainActivity.LOG_TAG,
                "Query parameter is $pathParameter"
            )            // Download file
            DocumentFile.fromTreeUri(context, SpUtil.getString(SpUtil.FOLDER_URI, "").toUri())
                ?.let {
                    val pathArray = pathParameter.split("/")
                    var documentFile = it
                    var found = false
                    var skip = true
                    for (path in pathArray) {
                        found = false
                        if (documentFile.name.equals(path)) {
                            pathParameterDocumentFiles.add(documentFile)
                            skip = false
                            continue
                        }
                        if(!skip){
                            for (file in documentFile.listFiles()) {
                                if (file.name.equals(path)) {
                                    found = true
                                    documentFile = file
                                    pathParameterDocumentFiles.add(documentFile)
                                    break
                                }
                            }
                            if (found) continue
                            else break
                        }
                    }
                    if (found) {
                        if (documentFile.isFile) {
                            call.response.header(
                                "Content-Disposition",
                                "attachment; filename=\"${documentFile.name}\""
                            )
                            context.applicationContext.contentResolver.openFileDescriptor(
                                documentFile.uri,
                                "r"
                            )
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
                        } else {
                            call.respondHtml(
                                block = webPageResponse(
                                    context,
                                    documentFile,
                                    pathParameterDocumentFiles
                                )
                            )
                        }
                    } else {
                        DocumentFile.fromTreeUri(context, SpUtil.getString(SpUtil.FOLDER_URI, "").toUri())
                            ?.let { rootTreeDocumentFile ->
                                if(pathParameterDocumentFiles.isEmpty()){
                                    pathParameterDocumentFiles.add(rootTreeDocumentFile)
                                }
                                call.respondHtml(block = webPageResponse(context, rootTreeDocumentFile, pathParameterDocumentFiles))
                            }
                    }
                }
        } else {
            DocumentFile.fromTreeUri(context, SpUtil.getString(SpUtil.FOLDER_URI, "").toUri())
                ?.let {
                    pathParameterDocumentFiles.add(it)
                    call.respondHtml(block = webPageResponse(context, it, pathParameterDocumentFiles))
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
            "}\n" +
            "input[type=button], input[type=submit], input[type=reset] {\n" +
            "  background-color: #1976D2;\n" +
            "  border: none;\n" +
            "  color: white;\n" +
            "  padding: 10px 16px;\n" +
            "  text-decoration: none;\n" +
            "  margin: 4px 2px;\n" +
            "  cursor: pointer;\n" +
            "}\n" +
            "input[type=file]::file-selector-button {\n" +
            "  margin-inline-end: 0;\n" +
            "  padding: 10px 16px;\n" +
            "  background-color: #1976D2;\n" +
            "  color: white;\n" +
            "  margin: 4px 2px;\n" +
            "  border: none;\n" +
            "  border-radius: 0;\n" +
            "}\n" +
            "h4{\n" +
            "color: #1976D2;\n" +
            "}\n"

    private val scriptHtml =
        "   function toggleCheckboxes(source) {\n" +
                "    checkboxes = document.getElementsByName('foo');\n" +
                "    var i = 0; var n = checkboxes.length;\n" +
                "    while (i != n) {\n" +
                "      checkboxes[i].checked = source.checked; i++\n" +
                "    }\n" +
                "  }" +
                "  function downloadAction(source) {\n" +
                "    checkboxes = document.getElementsByName('foo');\n" +
                "    var i = 0; var n = checkboxes.length;\n" +
                "    while (i != n) {\n" +
                "      if(checkboxes[i].checked) { console.log(checkboxes[i].value);\n" +
                "let a= document.createElement('a');\n" +
                "a.target= '_blank';\n" +
                "a.href= '/?Path='+checkboxes[i].value;\n" +
                "a.click();}" +
                "      i++;\n" +
                "    }\n" +
                "  }"

    private fun webPageResponse(
        context: Context,
        parentDirDocument: DocumentFile,
        pathParameter: ArrayList<DocumentFile>
    ): HTML.() -> Unit = {
        Log.d(LOG_TAG, "Size of DocumentFiles : ${pathParameter.size}")
        head {
            meta { charset = "utf-8" }
            meta { name = "viewport"; content = "width=device-width, initial-scale=1" }
            title { +"Mobile Remote File Sharing made easy" }
            link(
                href = "https://fonts.googleapis.com/css2?family=Material+Symbols+Outlined:opsz,wght,FILL,GRAD@48,400,0,0",
                rel = "stylesheet"
            )
            style { +styleSheet }
            script { +scriptHtml }
        }
        body {
            div {
                h4 {
                    +"Mobile File Sharing Server"
                }
                for (docFile in pathParameter){
                    a(
                        href = "?$PATH_PARAM=${
                            docFile.uri.lastPathSegment?.split(":")?.get(1)
                        }", "style"
                    ) {
                        +"${docFile.name} / "
                    }
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
                        td {
                            checkBoxInput {
                                onClick = "toggleCheckboxes(this)"
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
                        Log.d(MainActivity.LOG_TAG, "Path is ${documentFile.uri.toString()}")
                        tr {
                            td {
                                checkBoxInput {
                                    name = "foo"
                                    value = "${documentFile.name}"
                                }
                            }
                            td {
                                if (documentFile.isDirectory) {
                                    span(classes = "material-symbols-outlined") {
                                        +"folder"
                                    }
                                } else {
                                    if (documentFile.name?.endsWith(
                                            "jpg",
                                            ignoreCase = true
                                        ) == true
                                    ) {
                                        context.applicationContext.contentResolver.openFileDescriptor(
                                            documentFile.uri,
                                            "r"
                                        )
                                            ?.use { fd ->
                                                val bitmap =
                                                    BitmapFactory.decodeFileDescriptor(fd.fileDescriptor)
                                                val thumbnail =
                                                    ThumbnailUtils.extractThumbnail(bitmap, 48, 48)
                                                val baos = ByteArrayOutputStream()
                                                thumbnail.compress(
                                                    Bitmap.CompressFormat.JPEG,
                                                    100,
                                                    baos
                                                )
                                                val b = Base64.encodeToString(
                                                    baos.toByteArray(),
                                                    Base64.DEFAULT
                                                )
                                                img(src = "data:image/gif;base64,$b")
                                            }
                                    } else {
                                        span(classes = "material-symbols-outlined") {
                                            +"draft"
                                        }
                                    }
                                }
                            }
                            td {
//                                if (documentFile.isDirectory) {
                                a(
                                    href = "?$PATH_PARAM=${
                                        documentFile.uri.lastPathSegment?.split(":")?.get(1)
                                    }", "style"
                                ) {
                                    +"${documentFile.name}"
                                }
//                                } else {
//                                a(href = "?$FILE_PATH_PARAM=${documentFile.name}", "style") {
//                                        +"${documentFile.name}"
//                                    }
//                                }
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
            uri, null, null, null, null, null
        )

        cursor?.use {
            // moveToFirst() returns false if the cursor has 0 rows. Very handy for
            // "if there's anything to look at, look at it" conditionals.
            if (it.moveToFirst()) {

                // Note it's called "Display Name". This is
                // provider-specific, and might not necessarily be the file name.
                val columnIndex = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val displayName: String =
                    it.getString(if (columnIndex > 0) columnIndex else 0)
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
                    android.text.format.Formatter.formatFileSize(
                        context,
                        it.getString(sizeIndex).toLong()
                    )
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

    public fun start(wait: Boolean) {
        nettyServer.start(wait = wait)
    }

    public fun stop(gracePeriodMillis: Long) {
        nettyServer.stop(gracePeriodMillis = gracePeriodMillis)
    }
}