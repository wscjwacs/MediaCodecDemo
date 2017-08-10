package com.topvision.videodemo.rencoder;

import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.media.MediaFormat;
import android.util.Log;


import com.topvision.videodemo.CameraWrapper;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Vector;

import tech.shutu.jni.YuvUtils;

/**
 * Created by robi on 2016-04-01 10:50.
 */
public class VideoRunnable extends Thread {
    public static boolean DEBUG = false;

    private static final String TAG = "VideoRunnable";
    private static final boolean VERBOSE = true; // lots of logging
    // parameters for the encoder
    private static final String MIME_TYPE = "video/avc"; // H.264 Advanced Video
    private static final int FRAME_RATE = 25; // 15fps
    private static final int IFRAME_INTERVAL = 1; // 1 between
    // I-frames
    private static final int TIMEOUT_USEC = 10000;
    private static final int COMPRESS_RATIO = 256;
    private static final int BIT_RATE = CameraWrapper.DST_IMAGE_HEIGHT * CameraWrapper.DST_IMAGE_WIDTH * 3 * 8 * FRAME_RATE / COMPRESS_RATIO; // bit rate CameraWrapper.
    private final Object lock = new Object();
    //
    byte[] mFrameData;
    byte[] tempFrameData;
    byte[] resultFrameData;
    Vector<byte[]> frameBytes;
    private int mWidth;
    private int mHeight;
    private MediaCodec mMediaCodec;
    private MediaCodec.BufferInfo mBufferInfo;
    private int mColorFormat;
    private long mStartTime = 0;
    private volatile boolean isExit = false;
    private WeakReference<MediaMuxerRunnable> mediaMuxerRunnable;
    private MediaFormat mediaFormat;
    private MediaCodecInfo codecInfo;
    private volatile boolean isStart = false;
    private volatile boolean isMuxerReady = false;
    byte [] rotateNv21 ;
    private boolean isAddOSD = true;

    public VideoRunnable(int mWidth, int mHeight, WeakReference<MediaMuxerRunnable> mediaMuxerRunnable) {
        this.mWidth = mWidth;
        this.mHeight = mHeight;
        this.mediaMuxerRunnable = mediaMuxerRunnable;
        frameBytes = new Vector<byte[]>();
        prepare();
    }


    /**
     * 选择合适的color
     * create at 2017/3/22 18:24
     */
    private static int selectColorFormat(MediaCodecInfo codecInfo,
                                         String mimeType) {
        MediaCodecInfo.CodecCapabilities capabilities = codecInfo
                .getCapabilitiesForType(mimeType);
        for (int i = 0; i < capabilities.colorFormats.length; i++) {
            int colorFormat = capabilities.colorFormats[i];
            Log.e("tvLog", "" + colorFormat);
            if (isRecognizedFormat(colorFormat)) {
                return colorFormat;
            }
        }
        if (DEBUG) Log.e(TAG,
                "couldn't find a good color format for " + codecInfo.getName()
                        + " / " + mimeType);
        return 0; // not reached
    }


    /**
     * 是否是官方提供的顏色類別
     * create at 2017/3/22 18:25
     */
    private static boolean isRecognizedFormat(int colorFormat) {
        switch (colorFormat) {
            // these are the formats we know how to handle for this test
            case MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar:
            case MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420PackedPlanar:
            case MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar:
            case MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420PackedSemiPlanar:
            case MediaCodecInfo.CodecCapabilities.COLOR_TI_FormatYUV420PackedSemiPlanar:
                return true;
            default:
                return false;
        }
    }


    /**
     * 通过编码类型获取MediaCodecInfo对象
     * create at 2017/3/22 18:24
     */
    private static MediaCodecInfo selectCodec(String mimeType) {
        int numCodecs = MediaCodecList.getCodecCount();
        for (int i = 0; i < numCodecs; i++) {
            MediaCodecInfo codecInfo = MediaCodecList.getCodecInfoAt(i);
            if (!codecInfo.isEncoder()) {
                continue;
            }
            String[] types = codecInfo.getSupportedTypes();
            for (int j = 0; j < types.length; j++) {
                if (types[j].equalsIgnoreCase(mimeType)) {
                    return codecInfo;
                }
            }
        }
        return null;
    }


    public void exit() {
        isExit = true;
    }

    public void add(byte[] data) {
        if (frameBytes != null && isMuxerReady) {
            frameBytes.add(data);
        }
    }

    /**
     * 准备一些需要的参数
     * create at 2017/3/22 18:13
     */
    private void prepare() {
        if (DEBUG) Log.i(TAG, "VideoEncoder()");
        //
        mFrameData = new byte[this.mWidth * this.mHeight * 3 / 2];
        rotateNv21 = new byte[this.mWidth * this.mHeight * 3 / 2];
         tempFrameData = new byte[this.mWidth * this.mHeight * 3 / 2];
//        resultFrameData = new byte[this.mWidth * this.mHeight * 3 / 2];

        mBufferInfo = new MediaCodec.BufferInfo();

        codecInfo = selectCodec(MIME_TYPE);
        if (codecInfo == null) {
            if (DEBUG) Log.e(TAG, "Unable to find an appropriate codec for " + MIME_TYPE);
            return;
        }
        if (VERBOSE)
            if (DEBUG) Log.d(TAG, "found codec: " + codecInfo.getName());

        printSupportColorFormat(codecInfo, MIME_TYPE);
        mColorFormat = selectColorFormat(codecInfo, MIME_TYPE);
        if (VERBOSE)
            if (DEBUG) Log.d(TAG, "found colorFormat: " + mColorFormat);
        mediaFormat = MediaFormat.createVideoFormat(MIME_TYPE,this.mHeight,//注意这里旋转后有一个大坑，就是要交换mHeight，和mWidth的位置。否则录制出来的视频时花屏的。
                this.mWidth);
        mediaFormat.setInteger(MediaFormat.KEY_BIT_RATE, BIT_RATE);
        mediaFormat.setInteger(MediaFormat.KEY_FRAME_RATE, FRAME_RATE);
        mediaFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT, mColorFormat);
        mediaFormat.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, IFRAME_INTERVAL);
        if (VERBOSE)
            if (DEBUG) Log.d(TAG, "format: " + mediaFormat);
    }

    private void startMediaCodec() throws IOException {
        mMediaCodec = MediaCodec.createByCodecName(codecInfo.getName());
        mMediaCodec.configure(mediaFormat, null, null,
                MediaCodec.CONFIGURE_FLAG_ENCODE);
        mMediaCodec.start();

        isStart = true;
    }

    private void printSupportColorFormat(MediaCodecInfo codecInfo, String MIME_TYPE) {
        MediaCodecInfo.CodecCapabilities capabilities = codecInfo
                .getCapabilitiesForType(MIME_TYPE);
        for (int i = 0; i < capabilities.colorFormats.length; i++) {
            int colorFormat = capabilities.colorFormats[i];
            Log.e("colorFormat", "" + colorFormat);
        }
    }

    private void stopMediaCodec() {
        if (mMediaCodec != null) {
            mMediaCodec.stop();
            mMediaCodec.release();
            mMediaCodec = null;
        }
        isStart = false;
        if (DEBUG) Log.e("angcyo-->", "stop video 录制...");
    }

    public synchronized void restart() {
        isStart = false;
        isMuxerReady = false;
        frameBytes.clear();
    }

    public void setMuxerReady(boolean muxerReady) {
        synchronized (lock) {
            if (DEBUG)
                Log.e("angcyo-->", Thread.currentThread().getId() + " video -- setMuxerReady..." + muxerReady);
            isMuxerReady = muxerReady;
            lock.notifyAll();
        }
    }

    private void encodeFrame(byte[] input/* , byte[] output */) {
        if (VERBOSE)
            if (DEBUG) Log.i(TAG, "encodeFrame()");
        long startTime = System.currentTimeMillis();

//        if (mColorFormat == MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar) {
//            //I420 格式
//            YuvUtils.convertYV12ToI420(input, mFrameData);
//        } else {
        //YuvUtils.convertYV12ToN12(input, mFrameData, mWidth, mHeight);

        YuvUtils.scaleAndRotateYV12ToI420(input,rotateNv21,mWidth,mHeight,90,mHeight,mWidth);
        if (isAddOSD){
            YuvUtils.convertI420ToN21(rotateNv21,tempFrameData,mHeight,mWidth);
//        com.jiangdg.yuvosd.YuvUtils.YUV420spRotateOfBack(input, rotateNv21,mWidth,mHeight,  90);//将画面旋转九十度（摄像头采集的画面默认是旋转九十度的所以，必须旋转回来）
            com.jiangdg.yuvosd.YuvUtils.AddYuvOsd(tempFrameData,mHeight, mWidth, mFrameData,//给视频加上时间水印。（注意旋转后的数据，加水印的时候图片的宽和高应该对换，否则加不上去）
                    new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date()),// 开始将NV21转换为YUV420p(YV21)
                    mColorFormat,true);
        }
//


        long endScaleTime = System.currentTimeMillis();
        Log.e("tvLog", String.valueOf(endScaleTime - startTime));
        ByteBuffer[] inputBuffers = mMediaCodec.getInputBuffers();
        ByteBuffer[] outputBuffers = mMediaCodec.getOutputBuffers();
        int inputBufferIndex = mMediaCodec.dequeueInputBuffer(TIMEOUT_USEC);
        if (VERBOSE)
            if (DEBUG) Log.i(TAG, "inputBufferIndex-->" + inputBufferIndex);
        if (inputBufferIndex >= 0) {
            long endTime = System.nanoTime();
            long ptsUsec = (endTime - mStartTime) / 1000;
            if (VERBOSE)
                if (DEBUG) Log.i(TAG, "resentationTime: " + ptsUsec);
            ByteBuffer inputBuffer = inputBuffers[inputBufferIndex];
            inputBuffer.clear();
            inputBuffer.put(mFrameData);
            mMediaCodec.queueInputBuffer(inputBufferIndex, 0,
                    mFrameData.length, System.nanoTime() / 1000, 0);
        } else {
            // either all in use, or we timed out during initial setup
            if (VERBOSE)
                if (DEBUG) Log.d(TAG, "input buffer not available");
        }

        int outputBufferIndex = mMediaCodec.dequeueOutputBuffer(mBufferInfo, TIMEOUT_USEC);
        if (VERBOSE)
            if (DEBUG) Log.i(TAG, "outputBufferIndex-->" + outputBufferIndex);
        do {
            if (outputBufferIndex == MediaCodec.INFO_TRY_AGAIN_LATER) {
            } else if (outputBufferIndex == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
                outputBuffers = mMediaCodec.getOutputBuffers();
            } else if (outputBufferIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                MediaFormat newFormat = mMediaCodec.getOutputFormat();
                MediaMuxerRunnable mediaMuxerRunnable = this.mediaMuxerRunnable.get();
                if (mediaMuxerRunnable != null) {
                    mediaMuxerRunnable.addTrackIndex(MediaMuxerRunnable.TRACK_VIDEO, newFormat);
                }

                if (DEBUG)
                    Log.e("angcyo-->", "添加视轨 INFO_OUTPUT_FORMAT_CHANGED " + newFormat.toString());
            } else if (outputBufferIndex < 0) {
            } else {
                if (VERBOSE)
                    if (DEBUG) Log.d(TAG, "perform encoding");
                ByteBuffer outputBuffer = outputBuffers[outputBufferIndex];
                if (outputBuffer == null) {
                    throw new RuntimeException("encoderOutputBuffer " + outputBufferIndex +
                            " was null");
                }
                if ((mBufferInfo.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                    // The codec config data was pulled out and fed to the muxer when we got
                    // the INFO_OUTPUT_FORMAT_CHANGED status.  Ignore it.
                    if (VERBOSE) if (DEBUG) Log.d(TAG, "ignoring BUFFER_FLAG_CODEC_CONFIG");
                    mBufferInfo.size = 0;
                }
                if (mBufferInfo.size != 0) {
                    MediaMuxerRunnable mediaMuxerRunnable = this.mediaMuxerRunnable.get();

                    if (mediaMuxerRunnable != null && !mediaMuxerRunnable.isVideoAdd()) {
                        MediaFormat newFormat = mMediaCodec.getOutputFormat();
                        if (DEBUG) Log.e("angcyo-->", "添加视轨  " + newFormat.toString());
                        mediaMuxerRunnable.addTrackIndex(MediaMuxerRunnable.TRACK_VIDEO, newFormat);
                    }
                    // adjust the ByteBuffer values to match BufferInfo (not needed?)
                    outputBuffer.position(mBufferInfo.offset);
                    outputBuffer.limit(mBufferInfo.offset + mBufferInfo.size);

                    if (mediaMuxerRunnable != null && mediaMuxerRunnable.isMuxerStart()) {
                        mediaMuxerRunnable.addMuxerData(new MediaMuxerRunnable.MuxerData(
                                MediaMuxerRunnable.TRACK_VIDEO, outputBuffer, mBufferInfo
                        ));
                    }
                    if (VERBOSE) {
                        if (DEBUG) Log.d(TAG, "sent " + mBufferInfo.size + " frameBytes to muxer");
                    }
                }
                mMediaCodec.releaseOutputBuffer(outputBufferIndex, false);
            }
            outputBufferIndex = mMediaCodec.dequeueOutputBuffer(mBufferInfo, TIMEOUT_USEC);
        } while (outputBufferIndex >= 0);
    }


    @Override
    public void run() {
        //线程中的状态标识,停止线程则将isExit设为true即可
        while (!isExit) {
            //标识是否需要开启硬编码
            if (!isStart) {
                stopMediaCodec();
                if (!isMuxerReady) {
                    synchronized (lock) {
                        try {
                            if (DEBUG) Log.e("ang-->", "video -- 等待混合器准备...");
                            lock.wait();
                        } catch (InterruptedException e) {
                        }
                    }
                }
                //判断音视频混合器是否已准备,如果已准备则开启视频硬编码
                if (isMuxerReady) {
                    try {
                        if (DEBUG) Log.e("angcyo-->", "video -- startMediaCodec...");
                        startMediaCodec();
                    } catch (IOException e) {
                        isStart = false;
                        try {
                            Thread.sleep(100);
                        } catch (InterruptedException e1) {
                        }
                    }
                }

            } else if (!frameBytes.isEmpty()) {
                byte[] bytes = this.frameBytes.remove(0);
//                if(DEBUG) Log.e("ang-->", "解码视频数据:" + bytes.length);
                try {
                    encodeFrame(bytes);
                } catch (Exception e) {
                    if (DEBUG) Log.e("angcyo-->", "解码视频(Video)数据 失败");
                    e.printStackTrace();
                }
            }
        }


        if (DEBUG) Log.e("angcyo-->", "Video 录制线程 退出...");
    }
}
