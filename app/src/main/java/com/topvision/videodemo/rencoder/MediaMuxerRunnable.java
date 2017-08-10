package com.topvision.videodemo.rencoder;

import android.media.MediaCodec;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.os.Environment;
import android.support.annotation.IntDef;
import android.util.Log;


import com.topvision.videodemo.CameraWrapper;
import com.topvision.videodemo.FileSwapHelper;
import com.topvision.videodemo.MyApplication;

import java.io.IOException;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.ref.WeakReference;
import java.nio.ByteBuffer;
import java.util.Vector;

/**
 * 混合音视频线程
 * Created by robi on 2016-04-01 10:45.
 */
public class MediaMuxerRunnable extends Thread {

    public static final int TRACK_VIDEO = 0;
    public static final int TRACK_AUDIO = 1;
    public static boolean DEBUG = true;
    private static MediaMuxerRunnable mediaMuxerThread;
    private final Object lock = new Object();
    private MediaMuxer mediaMuxer;
    private Vector<MuxerData> muxerDatas;
    private volatile boolean isExit = false;
    private int videoTrackIndex = -1;
    private int audioTrackIndex = -1;
    private volatile boolean isVideoAdd;
    private volatile boolean isAudioAdd;
    private AudioRunnable audioThread;
    private VideoRunnable videoThread;
    private FileSwapHelper fileSwapHelper;
    //混合音视频的线程开启状态
    private boolean isThreadStart = false;

    private MediaMuxerRunnable() {
    }


    /**
     * 开启混合音视频线程
     * create at 2017/3/22 17:12
     */
    public static void startMuxer() {
        if (mediaMuxerThread == null) {
            synchronized (MediaMuxerRunnable.class) {
                if (mediaMuxerThread == null) {
                    mediaMuxerThread = new MediaMuxerRunnable();
                    mediaMuxerThread.start();
                }
            }
        }
    }

    public static void stopMuxer() {
        if (mediaMuxerThread != null) {
            mediaMuxerThread.exit();
            try {
                mediaMuxerThread.join();
            } catch (InterruptedException e) {

            }
            mediaMuxerThread = null;
        }
    }

    public static void addVideoFrameData(byte[] data) {
        if (mediaMuxerThread != null) {
            mediaMuxerThread.addVideoData(data);
        }
    }


    /**
     * 初始化混合器,分别开启音频线程和视频线程
     * create at 2017/3/22 17:14
     */
    private void initMuxer() {
        muxerDatas = new Vector<>();
        fileSwapHelper = new FileSwapHelper();
        //音频线程
        audioThread = new AudioRunnable(new WeakReference<MediaMuxerRunnable>(this));
        //视频线程
        videoThread = new VideoRunnable(CameraWrapper.SRC_IMAGE_WIDTH, CameraWrapper.SRC_IMAGE_HEIGHT, new WeakReference<MediaMuxerRunnable>(this));

        audioThread.start();
        videoThread.start();

        try {
            readyStart();
        } catch (IOException e) {
            Log.e("angcyo-->", "initMuxer 异常:" + e.toString());
            restart();
        }
    }

    private void addVideoData(byte[] data) {
        if (videoThread != null) {
            videoThread.add(data);
        }
    }

    public boolean isAudioAdd() {
        return isAudioAdd;
    }

    public boolean isVideoAdd() {
        return isVideoAdd;
    }


    /**
     * 设置更换文件
     * create at 2017/3/22 18:06
     */
    private void readyStart() throws IOException {
        fileSwapHelper.requestSwapFile(true);
       // readyStart(fileSwapHelper.getNextFileName(), false);
      //  readyStart(Environment.getExternalStorageDirectory().getPath()+"/AV_"+System.currentTimeMillis()+".mp4", false);
        readyStart(MyApplication.instance.getFilesDir()+"/"+System.currentTimeMillis()+".mp4", false);
    }

    private void readyStart(String filePath, boolean restart) throws IOException {
        isExit = false;
        isVideoAdd = false;
        isAudioAdd = false;
        muxerDatas.clear();

        mediaMuxer = new MediaMuxer(filePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
//        if (audioThread != null) {
//            if(DEBUG) Log.e("ang-->", "配置Audio解码.");
//            audioThread.prepare();
//            audioThread.prepareAudioRecord();
//        }
//        if (videoThread != null) {
//            if(DEBUG) Log.e("ang-->", "配置Video解码.");
//            videoThread.prepare();
//        }
//        if (!restart) {
//            if (DEBUG) Log.e("ang-->", "Start 混合线程.");
//            new Thread(this).start();
//            mediaMuxerThread.start();
//        }

        if (audioThread != null) {
            //通知音频线程 音视频混合器已经准备好了
//            new Thread(audioThread).readyStart();
            audioThread.setMuxerReady(true);
        }
        //通知视频线程 音视频混合器已经准备好了
        if (videoThread != null) {
//            new Thread(videoThread).readyStart();
            videoThread.setMuxerReady(true);
        }

//        if (DEBUG)
        Log.e("angcyo-->", "readyStart(String filePath, boolean restart) 保存至:" + filePath);
    }

    public synchronized void addTrackIndex(@TrackIndex int index, MediaFormat mediaFormat) {
        if (isMuxerStart()) {
            return;
        }

        /*轨迹改变之后,重启混合器*/
        if ((index == TRACK_AUDIO && isAudioAdd())
                ||
                (index == TRACK_VIDEO && isVideoAdd())) {
            restart();
            return;
        }

        if (mediaMuxer != null) {
            int track = 0;
            try {
                track = mediaMuxer.addTrack(mediaFormat);
            } catch (Exception e) {
                Log.e("angcyo-->", "addTrack 异常:" + e.toString());
                restart();
                return;
            }

            if (index == TRACK_VIDEO) {
                videoTrackIndex = track;
                isVideoAdd = true;
                if (DEBUG) Log.e("angcyo-->", "添加视轨 完成");
            } else {
                audioTrackIndex = track;
                isAudioAdd = true;
                if (DEBUG) Log.e("angcyo-->", "添加音轨 完成");
            }
            requestStart();
        }
    }

    private void exit() {
        if (videoThread != null) {
            videoThread.exit();
            try {
                videoThread.join();
            } catch (InterruptedException e) {

            }
        }
        if (audioThread != null) {
            audioThread.exit();
            try {
                audioThread.join();
            } catch (InterruptedException e) {

            }
        }

        isExit = true;
        synchronized (lock) {
            lock.notify();
        }
    }


    public void addMuxerData(MuxerData data) {
        if (DEBUG) Log.e("ang-->", "收到混合数据..." + data.bufferInfo.size);

        if (!isMuxerStart()) {
            return;
        }

        muxerDatas.add(data);
        synchronized (lock) {
            lock.notify();
        }
    }


    /**
     * 重新更换一个文件进行保存音视频文件
     * create at 2017/3/22 17:50
     */
    private void restart() {
        fileSwapHelper.requestSwapFile(true);
        String nextFileName = fileSwapHelper.getNextFileName();
        restart(nextFileName);
    }

    private void restart(String filePath) {
        restartAudioVideo();
//        if(DEBUG) Log.e("angcyo-->", "退出音视频线程完成.");
        readyStop();
        //-----------------

        try {
            readyStart(filePath, true);
        } catch (Exception e) {
//            e.printStackTrace();
//            if (DEBUG)
            Log.e("angcyo-->", "readyStart(filePath, true) " + "重启混合器失败 尝试再次重启!" + e.toString());
            restart();
            return;
        }
        if (DEBUG) Log.e("angcyo-->", "重启混合器完成");
    }


    @Override
    public void run() {
        isThreadStart = true;
        initMuxer();
        //判断是否退出程序
        while (!isExit) {
            if (isMuxerStart()) {
                if (muxerDatas.isEmpty()) {
                    synchronized (lock) {
                        try {
                            if (DEBUG) Log.e("ang-->", "等待混合数据...");
                            lock.wait();
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }
                } else {
//                    if (fileSwapHelper.requestSwapFile()) {
//                        //需要切换文件
//                        String nextFileName = fileSwapHelper.getNextFileName();
//                        if (DEBUG) Log.e("angcyo-->", "正在重启混合器..." + nextFileName);
//                        restart(nextFileName);
//                    } else {
                    MuxerData data = muxerDatas.remove(0);
                    int track;
                    if (data.trackIndex == TRACK_VIDEO) {
                        track = videoTrackIndex;
                    } else {
                        track = audioTrackIndex;
                    }
                    if (DEBUG) Log.e("ang-->", "写入混合数据 " + data.bufferInfo.size);
                    try {
                        mediaMuxer.writeSampleData(track, data.byteBuf, data.bufferInfo);
                    } catch (Exception e) {
//                            e.printStackTrace();
//                            if (DEBUG)
                        Log.e("angcyo-->", "写入混合数据失败!" + e.toString());

                        restart();
                    }
//                    }
                }
            } else {
                synchronized (lock) {
                    try {
                        if (DEBUG) Log.e("angcyo-->", "等待音视轨添加...");
                        lock.wait();
                    } catch (InterruptedException e) {
                        e.printStackTrace();
//                        Log.e("angcyo-->", "addTrack 异常:" + e.toString());
                    }
                }
            }
        }
        readyStop();
        isThreadStart = false;
        if (DEBUG) Log.e("angcyo-->", "混合器退出...");
    }

    private void requestStart() {
        synchronized (lock) {
            if (isMuxerStart()) {
                mediaMuxer.start();
                if (DEBUG) Log.e("angcyo-->", "requestStart 启动混合器 开始等待数据输入...");
                lock.notify();
            }
        }
    }

    public boolean isMuxerStart() {
        return isAudioAdd && isVideoAdd;
    }


    /**
     * 重新初始化一些参数
     * create at 2017/3/22 17:51
     */
    private void restartAudioVideo() {
        if (audioThread != null) {
            audioTrackIndex = -1;
            isAudioAdd = false;
            audioThread.restart();
        }
        if (videoThread != null) {
            videoTrackIndex = -1;
            isVideoAdd = false;
            videoThread.restart();
        }
    }


    /**
     * 关闭mediaMuxer
     * create at 2017/3/22 17:59
     */
    private void readyStop() {
        if (mediaMuxer != null) {
            try {
                mediaMuxer.stop();
            } catch (Exception e) {
                Log.e("angcyo-->", "mediaMuxer.stop() 异常:" + e.toString());

            }
            try {
                mediaMuxer.release();
            } catch (Exception e) {
                Log.e("angcyo-->", "mediaMuxer.release() 异常:" + e.toString());

            }
            mediaMuxer = null;
        }
    }

    @IntDef({TRACK_VIDEO, TRACK_AUDIO})
    @Retention(RetentionPolicy.SOURCE)
    public @interface TrackIndex {
    }

    /**
     * 封装需要传输的数据类型
     */
    public static class MuxerData {
        int trackIndex;
        ByteBuffer byteBuf;
        MediaCodec.BufferInfo bufferInfo;

        public MuxerData(@TrackIndex int trackIndex, ByteBuffer byteBuf, MediaCodec.BufferInfo bufferInfo) {
            this.trackIndex = trackIndex;
            this.byteBuf = byteBuf;
            this.bufferInfo = bufferInfo;
        }
    }

}
