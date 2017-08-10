package com.topvision.videodemo.util;

import android.hardware.Camera;


/**
 * =====================================================================================
 * <p/>
 * 版权：深圳国保警用装备制造有限公司，版权所有(c)2017
 * <p/>
 * 作者：Administrator on 2017/6/22 17:16
 * <p/>
 * 邮箱：xjs250@163.com
 * <p/>
 * 创建日期：2017/6/22 17:16
 * <p/>
 * 描述：
 * =====================================================================================
 */
public class CameraPreviewCallback implements Camera.PreviewCallback {
    private static final String TAG = "CameraPreviewCallback";


    public CameraPreviewCallback() {


    }

    @Override
    public void onPreviewFrame(byte[] data, Camera camera) {
     //   Log.e(TAG, "----onPreviewFrame: "+Thread.currentThread().getId());
        //回收缓存，下次仍然会使用，所以不需要再开辟新的缓存，达到优化的目的/
        // 注意，先addCallbackBuffer，然后再处理帧数据，否则会降低帧率 也不要在这里处理耗时的，否则会降低帧率
        camera.addCallbackBuffer(data);//必须放在encodeFrame下面否则数据刷新太快，编码不过来，就会导致界面上下不一致的现象
       // videoEncoder.encodeFrame(data);
    }
    public void close() {
       // videoEncoder.close();
    }

}