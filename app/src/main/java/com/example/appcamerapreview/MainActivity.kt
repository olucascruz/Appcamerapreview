package com.example.appcamerapreview

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.graphics.SurfaceTexture
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.media.ImageReader
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.HandlerThread
import android.view.Surface
import android.view.TextureView
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter


class MainActivity : AppCompatActivity() {

    lateinit var  capReq: CaptureRequest.Builder
    lateinit var  handler: Handler
    private lateinit var handlerThread: HandlerThread
    private lateinit var cameraManager: CameraManager
    lateinit var textureView: TextureView
    lateinit var cameraCaptureSession: CameraCaptureSession
    lateinit var cameraDevice: CameraDevice
    lateinit var imageReader: ImageReader
    lateinit var btCapture: Button
    lateinit var imageView: ImageView
    lateinit var btDownload: Button
    lateinit var currentImageBitmap: Bitmap


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        getPermissions()

        textureView = findViewById(R.id.textureView)
        cameraManager = getSystemService(Context.CAMERA_SERVICE) as CameraManager
        handlerThread = HandlerThread("videoThread")
        handlerThread.start()
        handler = Handler((handlerThread).looper)
        textureView.surfaceTextureListener = object: TextureView.SurfaceTextureListener{
            override fun onSurfaceTextureAvailable(p0: SurfaceTexture, p1: Int, p2: Int) {
                if (ActivityCompat.checkSelfPermission(
                        this@MainActivity,
                        Manifest.permission.CAMERA
                    ) == PackageManager.PERMISSION_GRANTED
                ) {
                    openCamera()
                }
            }

            override fun onSurfaceTextureSizeChanged(p0: SurfaceTexture, p1: Int, p2: Int) {

            }

            override fun onSurfaceTextureDestroyed(p0: SurfaceTexture): Boolean {
                return false
            }

            override fun onSurfaceTextureUpdated(p0: SurfaceTexture) {

            }

        }
        btCapture = findViewById(R.id.btCapture)
        btDownload = findViewById(R.id.btDownload)
        btDownload.visibility = View.INVISIBLE
        imageView = findViewById(R.id.imageView)


        imageReader = ImageReader.newInstance( 1000, 1000, ImageFormat.JPEG, 1)
        imageReader.setOnImageAvailableListener({ p0 ->
            val image = p0?.acquireLatestImage()
            if(image != null){
                val buffer = image.planes[0].buffer
                val bytes = ByteArray(buffer.capacity())
                buffer[bytes]
                val bitmapImage = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, null)
                val matrix = Matrix()
                matrix.postRotate(90f)
                var newBitmap = Bitmap.createBitmap(bitmapImage, 0, 0, bitmapImage.width, bitmapImage.height, matrix, true)
                newBitmap = Bitmap.createScaledBitmap(newBitmap, 420, 250, true);
                runOnUiThread {
                    imageView.setImageBitmap(newBitmap)
                }
                image.close()
                currentImageBitmap = newBitmap
            }
        },handler )

        btCapture.apply {
            setOnClickListener{
                capReq = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE)
                capReq.addTarget(imageReader.surface)
                cameraCaptureSession.capture(capReq.build(), null, null)
                btDownload.visibility = View.VISIBLE

            }
        }


        btDownload.apply {
            setOnClickListener {
                if(currentImageBitmap != null) {
                    savePhoto(this@MainActivity, currentImageBitmap)
                    btDownload.visibility = View.INVISIBLE
                }
            }
        }



    }

    private fun getPermissions(){
        val permissions = mutableListOf<String>()

        if(checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            permissions.add(Manifest.permission.CAMERA)
        }
        if(checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            permissions.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }
        if(permissions.size > 0) requestPermissions(permissions.toTypedArray(), 101)

    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        grantResults.forEach {
            if(it != PackageManager.PERMISSION_GRANTED){
                getPermissions()
            }else{
                openCamera()
            }
        }
    }

    fun openCamera(){

        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.CAMERA
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            getPermissions()
            return
        }
        cameraManager.openCamera(cameraManager.cameraIdList[0], object: CameraDevice.StateCallback(){
            override fun onOpened(p0: CameraDevice) {
                cameraDevice = p0
                capReq = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
                val surface = Surface(textureView.surfaceTexture)
                capReq.addTarget(surface)
                cameraDevice.createCaptureSession(listOf(surface, imageReader.surface), object: CameraCaptureSession.StateCallback(){

                    override fun onConfigured(p0: CameraCaptureSession) {
                        cameraCaptureSession = p0
                        cameraCaptureSession.setRepeatingRequest(capReq.build(), null, null)
                    }

                    override fun onConfigureFailed(p0: CameraCaptureSession) {
                        TODO("Not yet implemented")
                    }
                }, handler)
            }
            override fun onDisconnected(p0: CameraDevice) {
                TODO("Not yet implemented")
            }

            override fun onError(p0: CameraDevice, p1: Int) {
                TODO("Not yet implemented")
            }
        }, handler)

    }

    private fun savePhoto(context: Context, bitmap: Bitmap){
        val currentDateTime = LocalDateTime.now()
        val formatter = DateTimeFormatter.ofPattern("dd_MM_yyyy-HH_mm_ss_SSS") // Define o formato desejado

        val formattedDateTime = currentDateTime.format(formatter)
        val fileName = "$formattedDateTime.jpeg"
        val storageDirectory = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "AppCameraPreview")

        // Verifique se o diretório de destino existe, senão crie-o
        if (!storageDirectory.exists()) {
            storageDirectory.mkdirs()
        }

        val file = File(storageDirectory, fileName)

        try {
            val fos = FileOutputStream(file)
            bitmap.compress(Bitmap.CompressFormat.JPEG, 100, fos) // Salva a imagem como JPEG
            fos.close()
            val toast = Toast.makeText(this, "Saved", Toast.LENGTH_SHORT)
            toast.show()

        } catch (e: IOException) {
            e.printStackTrace()
        }
    }
}
