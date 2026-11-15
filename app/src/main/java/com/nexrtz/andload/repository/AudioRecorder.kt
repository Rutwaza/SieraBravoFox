package com.nexrtz.andload.repository

import android.content.ContentValues
import android.content.Context
import android.media.MediaRecorder
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File

class AudioRecorder(private val context: Context) {

    private var recorder: MediaRecorder? = null
    private var outputFile: File? = null
    private var outputUri: Uri? = null
    private var outputDescriptor: android.os.ParcelFileDescriptor? = null

    fun start(): Boolean {
        if (recorder != null) return false

        val newRecorder = MediaRecorder()
        return runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Audio.Media.DISPLAY_NAME, "recording_" + System.currentTimeMillis() + ".m4a")
                    put(MediaStore.Audio.Media.MIME_TYPE, "audio/mp4")
                    put(MediaStore.Audio.Media.RELATIVE_PATH, Environment.DIRECTORY_MUSIC + "/NeuralPlayer")
                    put(MediaStore.Audio.Media.IS_PENDING, 1)
                }
                val uri = context.contentResolver.insert(
                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                    values
                ) ?: error("Unable to create MediaStore recording")
                val descriptor = context.contentResolver.openFileDescriptor(uri, "w")
                    ?: error("Unable to open recording destination")
                outputUri = uri
                outputDescriptor = descriptor
                newRecorder.setOutputFile(descriptor.fileDescriptor)
            } else {
                val directory = context.getExternalFilesDir(Environment.DIRECTORY_MUSIC)
                    ?: context.filesDir
                if (!directory.exists()) directory.mkdirs()
                val file = File(directory, "recording_" + System.currentTimeMillis() + ".m4a")
                outputFile = file
                newRecorder.setOutputFile(file.absolutePath)
            }

            newRecorder.setAudioSource(MediaRecorder.AudioSource.MIC)
            newRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            newRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            newRecorder.setAudioEncodingBitRate(128_000)
            newRecorder.setAudioSamplingRate(44_100)
            newRecorder.prepare()
            newRecorder.start()
            recorder = newRecorder
        }.onFailure {
            newRecorder.release()
            outputDescriptor?.close()
            outputUri?.let { context.contentResolver.delete(it, null, null) }
            outputDescriptor = null
            outputUri = null
            outputFile = null
        }.isSuccess
    }

    fun stop(): Uri? {
        val activeRecorder = recorder ?: return null
        val uri = outputUri
        val file = outputFile
        recorder = null
        outputUri = null
        outputFile = null

        return runCatching {
            activeRecorder.stop()
            activeRecorder.release()
            outputDescriptor?.close()
            outputDescriptor = null

            if (uri != null) {
                val values = ContentValues().apply {
                    put(MediaStore.Audio.Media.IS_PENDING, 0)
                }
                context.contentResolver.update(uri, values, null, null)
                uri
            } else {
                file?.let {
                    MediaScannerConnection.scanFile(
                        context,
                        arrayOf(it.absolutePath),
                        arrayOf("audio/mp4"),
                        null
                    )
                    Uri.fromFile(it)
                }
            }
        }.getOrElse {
            activeRecorder.release()
            outputDescriptor?.close()
            outputDescriptor = null
            uri?.let { context.contentResolver.delete(it, null, null) }
            null
        }
    }

    fun release() {
        recorder?.release()
        outputDescriptor?.close()
        recorder = null
        outputDescriptor = null
        outputUri = null
        outputFile = null
    }
}
