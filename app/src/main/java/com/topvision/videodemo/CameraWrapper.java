package com.topvision.videodemo;

import android.annotation.SuppressLint;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.graphics.SurfaceTexture;
import android.graphics.YuvImage;
import android.hardware.Camera;
import android.os.AsyncTask;
import android.os.Environment;
import android.util.Log;
import android.view.SurfaceHolder;
import android.widget.Toast;


import com.topvision.videodemo.encoder.MediaEncoder;
import com.topvision.videodemo.rencoder.FileUtils;
import com.topvision.videodemo.rencoder.MediaMuxerRunnable;
import com.topvision.videodemo.util.FileUtil;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

import tech.shutu.jni.YuvUtils;

@SuppressLint("NewApi")
public class CameraWrapper {
//    public static final int DST_IMAGE_HEIGHT = 1080;
//    public static final int DST_IMAGE_WIDTH = 1920;
//    public static final int SRC_IMAGE_HEIGHT = 1080;
//    public static final int SRC_IMAGE_WIDTH = 1920;
public static final int DST_IMAGE_HEIGHT = 720;
    public static final int DST_IMAGE_WIDTH = 1280;
    public static final int SRC_IMAGE_HEIGHT = 720;
    public static final int SRC_IMAGE_WIDTH = 1280;
    private static final String TAG = "CameraWrapper";
    private static final boolean DEBUG = true;    // TODO set false on release
    private static CameraWrapper mCameraWrapper;
    /**
     * callback methods from encoder
     */
    private final MediaEncoder.MediaEncoderListener mMediaEncoderListener = new MediaEncoder.MediaEncoderListener() {
        @Override
        public void onPrepared(final MediaEncoder encoder) {
            if (DEBUG) Log.v(TAG, "onPrepared:encoder=" + encoder);
//            if (encoder instanceof MediaVideoEncoder)
//                mCameraView.setVideoEncoder((MediaVideoEncoder)encoder);
        }

        @Override
        public void onStopped(final MediaEncoder encoder) {
            if (DEBUG) Log.v(TAG, "onStopped:encoder=" + encoder);
//            if (encoder instanceof MediaVideoEncoder)
//                mCameraView.setVideoEncoder(null);
        }
    };
    Camera.PreviewCallback previewCallback;
    public  Camera mCamera;
    private Camera.Parameters mCameraParamters;
    private boolean mIsPreviewing = false;
    private float mPreviewRate = -1.0f;
    public CameraPreviewCallback mCameraPreviewCallback;
    //    private byte[] mImageCallbackBuffer = new byte[CameraWrapper.IMAGE_WIDTH
//            * CameraWrapper.IMAGE_HEIGHT * 3 / 2];
    private boolean isBlur = false;
    //    private int openCameraId = Camera.CameraInfo.CAMERA_FACING_BACK;
    private int openCameraId = Camera.CameraInfo.CAMERA_FACING_BACK;
    boolean startRecordingFlag = false;

    private CameraWrapper() {
    }

    public static CameraWrapper getInstance() {
        if (mCameraWrapper == null) {
            synchronized (CameraWrapper.class) {
                if (mCameraWrapper == null) {
                    mCameraWrapper = new CameraWrapper();
                }
            }
        }
        return mCameraWrapper;
    }

    private static String getSaveFilePath(String fileName) {
        StringBuilder fullPath = new StringBuilder();
        fullPath.append(FileUtils.getExternalStorageDirectory());
        fullPath.append(FileUtils.getMainDirName());
        fullPath.append("/video2/");
        fullPath.append(fileName);
        fullPath.append(".mp4");

        String string = fullPath.toString();
        File file = new File(string);
        File parentFile = file.getParentFile();
        if (!parentFile.exists()) {
            parentFile.mkdirs();
        }
        return string;
    }

    public void switchCameraId() {
        if (openCameraId == Camera.CameraInfo.CAMERA_FACING_BACK) {
            openCameraId = Camera.CameraInfo.CAMERA_FACING_FRONT;
        } else {
            openCameraId = Camera.CameraInfo.CAMERA_FACING_BACK;
        }
    }

    public void doOpenCamera(CamOpenOverCallback callback) {
        Log.i(TAG, "Camera open....");
        int numCameras = Camera.getNumberOfCameras();
        Camera.CameraInfo info = new Camera.CameraInfo();
        for (int i = 0; i < numCameras; i++) {
            Camera.getCameraInfo(i, info);
            if (info.facing == openCameraId) {
                mCamera = Camera.open(i);
                break;
            }
        }
        if (mCamera == null) {
            Log.d(TAG, "No front-facing camera found; opening default");
            mCamera = Camera.open();    // opens first back-facing camera
        }
        if (mCamera == null) {
            throw new RuntimeException("Unable to open camera");
        }
        Log.i(TAG, "Camera open over....");
        callback.cameraHasOpened();
    }

    public void doStartPreview(SurfaceHolder holder, float previewRate) {
        Log.i(TAG, "doStartPreview...");
        if (mIsPreviewing) {
            this.mCamera.stopPreview();
            return;
        }

        try {
            this.mCamera.setPreviewDisplay(holder);
        } catch (IOException e) {
            e.printStackTrace();
        }
        initCamera();
    }

    public void doStartPreview(SurfaceTexture surface) {
        Log.i(TAG, "doStartPreview()");
        if (mIsPreviewing) {
            this.mCamera.stopPreview();
            return;
        }

        try {
            this.mCamera.setPreviewTexture(surface);
        } catch (IOException e) {
            e.printStackTrace();
        }
        initCamera();
    }


    /**
     * 释放camera 资源
     * create at 2017/3/23 9:53
     */
    public void doStopCamera() {
        Log.i(TAG, "doStopCamera");
        if (this.mCamera != null) {
            if (mCameraPreviewCallback != null) {
                mCameraPreviewCallback.close();
            }
            this.mCamera.setPreviewCallback(null);
            this.mCamera.stopPreview();
            this.mIsPreviewing = false;
            this.mPreviewRate = -1f;
            this.mCamera.release();
            this.mCamera = null;

            startRecordingFlag = false;
        }
    }

    private void initCamera() {
        if (this.mCamera != null) {
            this.mCameraParamters = this.mCamera.getParameters();
            this.mCameraParamters.setPreviewFormat(ImageFormat.YV12);
            this.mCameraParamters.setFlashMode("off");
            this.mCameraParamters.setWhiteBalance(Camera.Parameters.WHITE_BALANCE_AUTO);
            this.mCameraParamters.setSceneMode(Camera.Parameters.SCENE_MODE_AUTO);
            this.mCameraParamters.setPreviewSize(SRC_IMAGE_WIDTH, SRC_IMAGE_HEIGHT);
            this.mCamera.setDisplayOrientation(90);
            mCameraPreviewCallback = new CameraPreviewCallback();
//            mCamera.addCallbackBuffer(mImageCallbackBuffer);
//            mCamera.setPreviewCallbackWithBuffer(mCameraPreviewCallback);
           // mCamera.setPreviewCallback(mCameraPreviewCallback);
            List<String> focusModes = this.mCameraParamters.getSupportedFocusModes();
            for (Camera.Size size : this.mCameraParamters.getSupportedPreviewSizes()) {
                Log.e("tvLog", "width=" + size.width + "," + size.height);
            }
            if (focusModes.contains("continuous-video")) {
                this.mCameraParamters
                        .setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO);
            }
            this.mCamera.setParameters(this.mCameraParamters);
            this.mCamera.startPreview();

            this.mIsPreviewing = true;
        }
    }

    public void setBlur(boolean blur) {
        isBlur = blur;
    }

    public void setPreviewCallback(Camera.PreviewCallback callback) {
        previewCallback = callback;
    }

    public interface CamOpenOverCallback {
        public void cameraHasOpened();
    }

    class CameraPreviewCallback implements Camera.PreviewCallback {

        private CameraPreviewCallback() {
            //startRecording();
        }

        public void close() {
            stopRecording();
        }


        /**
         * 开始录制
         * create at 2017/3/22 17:10
         */
        public void startRecording() {
            startRecordingFlag = true;
            MediaMuxerRunnable.startMuxer();

        }


        /**
         * 停止录制
         * create at 2017/3/22 17:10
         */
        public void stopRecording() {
            startRecordingFlag = false;
            MediaMuxerRunnable.stopMuxer();
        }

        byte[] dstYuv = new byte[CameraWrapper.DST_IMAGE_WIDTH * CameraWrapper.DST_IMAGE_HEIGHT * 3 / 2];
        int count = 0;

        /**
         * 照相机预览数据回调
         * 这边的data是NV21格式
         * create at 2017/3/22 17:59
         */
        @Override
        public void onPreviewFrame(byte[] data, Camera camera) {

            //当启动录制的视频把视频源数据加入编码中
            if (startRecordingFlag) {
                //做适当的丢帧
//                if (count % 2 == 0) {
                MediaMuxerRunnable.addVideoFrameData(data);
                //  }
                count++;

            } else {
                count = 0;
            }
//            camera.addCallbackBuffer(data);
        }
    }

    public CameraPreviewCallback getmCameraPreviewCallback() {
        return mCameraPreviewCallback;
    }

    public void setmCameraPreviewCallback(CameraPreviewCallback mCameraPreviewCallback) {
        this.mCameraPreviewCallback = mCameraPreviewCallback;
    }

}
