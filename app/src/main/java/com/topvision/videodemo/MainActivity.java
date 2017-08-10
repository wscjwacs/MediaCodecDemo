package com.topvision.videodemo;

import android.app.Activity;
import android.content.Context;

import android.graphics.SurfaceTexture;
 import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.v7.widget.AppCompatButton;
import android.util.Log;
import android.view.View;
 import android.view.WindowManager;
import android.widget.TextView;
import com.topvision.videodemo.cpu.CpuUtil;

import java.io.File;
import java.util.Arrays;

public class MainActivity extends Activity implements CameraWrapper.CamOpenOverCallback {

    CameraTexturePreview mCameraTexturePreview;
    public static final String TAG = "MainActivity";
    public static Context context;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.e(TAG, "onCreate");
        //全屏
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);


        setContentView(R.layout.activity_main);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        mCameraTexturePreview = (CameraTexturePreview) findViewById(R.id.camera_textureview);
        context = this;


        findViewById(R.id.startAndStop).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startAndStop(v);
            }
        });

        findViewById(R.id.switchCamera).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                switchCamera(v);
            }
        });

        openCamera();

    }




    public void switchCamera(View view) {
        CameraWrapper.getInstance().doStopCamera();
        CameraWrapper.getInstance().switchCameraId();
        openCamera();
    }





    public void startAndStop(View view) {
        time = System.currentTimeMillis();
        String tag = (String) view.getTag();
        if (tag.equalsIgnoreCase("stop")) {
            CameraWrapper.getInstance().getmCameraPreviewCallback().stopRecording();
            CameraWrapper.getInstance().mCamera.setPreviewCallback(null);
            // CameraWrapper.getInstance().doStopCamera();
            view.setTag("start");
            ((TextView) view).setText("开始");
            hideMessage();

        } else {
            CameraWrapper.getInstance().mCamera.setPreviewCallback(CameraWrapper.getInstance().mCameraPreviewCallback);
            CameraWrapper.getInstance().getmCameraPreviewCallback().startRecording();

            //  openCamera();
            view.setTag("stop");
            ((TextView) view).setText("停止");
            showMessage();
            yulu();
        }
    }



    private void openCamera() {
        Thread openThread = new Thread() {
            @Override
            public void run() {
                CameraWrapper.getInstance().doOpenCamera(MainActivity.this);
            }
        };
        openThread.start();
    }

    @Override
    public void cameraHasOpened() {
        //等
        SurfaceTexture surface = null;
        while (true) {
            surface = this.mCameraTexturePreview.getSurfaceTexture();
            if (surface != null) {
                break;
            }
        }

        CameraWrapper.getInstance().doStartPreview(surface);
    }


    AppCompatButton cpuInfo;



    /**
     * 显示cpu使用率
     * create at 2017/3/23 11:08
     */
    private void showMessage() {
        cpuInfo = (AppCompatButton) findViewById(R.id.cpu_info);
        handlerTime.sendEmptyMessageDelayed(1, 1000);
    }


    private void hideMessage() {
        time = 0;
        handlerTime.removeMessages(1);
    }

    long time = 0;
    Handler handlerTime = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            if (msg.what == 1) {// 计时
                cpuInfo.setText("cpu:" + CpuUtil.getUsedPercentValue(MainActivity.this) + ",time:" + (transformHMS(System.currentTimeMillis() + 1000 - time)));
                handlerTime.sendEmptyMessageDelayed(1, 1000);
            }
        }
    };

    @Override
    protected void onDestroy() {
        super.onDestroy();
        CameraWrapper.getInstance().doStopCamera();
    }

    /**
     * 转换为时分秒格式
     * create at 2017/3/27 10:18
     */
    public static String transformHMS(long elapsed) {
        int hour, minute, second, milli;

        //milli = (int) (elapsed % 1000);
        elapsed = elapsed / 1000;

        second = (int) (elapsed % 60);
        elapsed = elapsed / 60;

        minute = (int) (elapsed % 60);
        elapsed = elapsed / 60;

        hour = (int) (elapsed % 60);

        return String.format("%02d:%02d:%02d", hour, minute, second);
    }



    /**
     * 启动定时器，定时时间到文件保存完后再调用此方法 ,预录四十秒，十秒钟保存一个文件
     */
    private static void yulu(){
        // 123456_456_20100101093309_0002.mp4           123456_456_20100101093552_0003IMP.mp4

        File file = new File(MyApplication.instance.getFilesDir().getAbsolutePath());
        //文件是按照日期来排序的
        File [] fileNum = file.listFiles();
        if (fileNum.length<=0||fileNum==null){
            return;
        }
        //每次判断如果文件超过十个（4个）,删除第一个
        if (fileNum.length>4)
        {
            Arrays.sort(fileNum);
            fileNum[0].delete();
        }
    }






    long currentRec;
    int  time2 = -1;
    public Runnable timeRun = new Runnable() {
        @Override
        public void run() {

            time2++;
              handler.postDelayed(timeRun, 1000);//用于计时的线程(秒计时器)

                currentRec++;
                if (currentRec > 10) {
                    currentRec = 0;

                }
        }
    };
    /**
     * 计时线程
     */
    public Handler handler = new Handler(){
        @Override
        public void handleMessage(Message msg)
        {
            super.handleMessage(msg);
            //将计时线程从新放到消息队列中
            handler.post(timeRun);
        }

    };
}
