package tech.shutu.jni;

import android.graphics.Bitmap;

/**
 * Created by raomengyang on 24/12/2016.
 */

public class YuvUtils {

    public static native void allocateMemo(int src_yuv_length, int src_argb_length, int dst_length);

    public static native void rgbToYuvByAlgorithms(int[] aRGB, byte[] dst_yuv, int src_width, int src_height);

    public static native void rgbToYuvBylibyuv(Object srcBitmap, byte[] dst_yuv);

    public static native void rgbToYuvWidthScaleBylibyuv(Object bitmap, byte[] dst_yuv, int src_width, int src_height, int dst_width, int dst_height);

    public static native void scaleAndRotateYV12ToI420(byte[] src_data, byte[] dst_data, int src_width, int src_height, int rotation, int dst_width, int dst_height);

    public static native void releaseMemo();

    public static native void scaleYV12ToI420(byte[] yv12Data, byte[] I420Data, int src_width, int src_height, int dst_width, int dst_height);

    public static native void scaleYV12ToN12(byte[] yv12Data, byte[] n12Data, int src_width, int src_height, int dst_width, int dst_height);

    public static native void convertI420ToN12(byte[] I420Data, byte[] n12Data, int width, int height);

    public static native void convertI420ToN21(byte[] I420Data, byte[] n21Data, int width, int height);

    public static native void convertYV12ToN12(byte[] yv12Data, byte[] n21Data, int width, int height);

    public static native void convertY12ToN12(byte[] y12Data, byte[] n12Data, int width, int height);

    public static native void scaleYV12(byte[] srcYv12Data, byte[] dstYv12Data, int src_width, int src_height, int dst_width, int dst_height);

    public static native void convertYV12ToI420(byte[] y12Data, byte[] i420Data);
    public static native void convertNV21ToN12(byte[] n21Data, byte[] n12Data,int width, int height);

    /**
     * Bitmap转换成Drawable
     * Bitmap bm = xxx; //xxx根据你的情况获取
     * BitmapDrawable bd = new BitmapDrawable(getResource(), bm);
     * 因为BtimapDrawable是Drawable的子类，最终直接使用bd对象即可。
     */
    public static byte[] getNV21(int inputWidth, int inputHeight, Bitmap srcBitmap) {
        int[] argb = new int[inputWidth * inputHeight];
        if (null != srcBitmap) {
            try {
                srcBitmap.getPixels(argb, 0, inputWidth, 0, 0, inputWidth, inputHeight);
            } catch (Exception e) {
                e.printStackTrace();
                return null;
            }
            // byte[] yuv = new byte[inputWidth * inputHeight * 3 / 2];
            // encodeYUV420SP(yuv, argb, inputWidth, inputHeight);
            if (null != srcBitmap && !srcBitmap.isRecycled()) {
                srcBitmap.recycle();
                srcBitmap = null;
            }
            return colorconvertRGB_IYUV_I420(argb, inputWidth, inputHeight);
        } else return null;
    }

    private static void encodeYUV420SP(byte[] yuv420sp, int[] argb, int width, int height) {
        final int frameSize = width * height;
        int yIndex = 0;
        int uvIndex = frameSize;

        int a, R, G, B, Y, U, V;
        int index = 0;
        for (int j = 0; j < height; j++) {
            for (int i = 0; i < width; i++) {

                a = (argb[index] & 0xff000000) >> 24; // a is not used obviously
                R = (argb[index] & 0xff0000) >> 16;
                G = (argb[index] & 0xff00) >> 8;
                B = (argb[index] & 0xff) >> 0;

                // well known RGB to YUV algorithm
                Y = ((66 * R + 129 * G + 25 * B + 128) >> 8) + 16;
                U = ((-38 * R - 74 * G + 112 * B + 128) >> 8) + 128;
                V = ((112 * R - 94 * G - 18 * B + 128) >> 8) + 128;

			/* NV21 has a plane of Y and interleaved planes of VU each sampled by a factor of 2 				meaning for every 4 Y pixels there are 1 V and 1 U.  Note the sampling is 					every otherpixel AND every other scanline.*/
                yuv420sp[yIndex++] = (byte) ((Y < 0) ? 0 : ((Y > 255) ? 255 : Y));
                if (j % 2 == 0 && index % 2 == 0) {
                    yuv420sp[uvIndex++] = (byte) ((V < 0) ? 0 : ((V > 255) ? 255 : V));
                    yuv420sp[uvIndex++] = (byte) ((U < 0) ? 0 : ((U > 255) ? 255 : U));
                }
                index++;
            }
        }
    }

    public static byte[] colorconvertRGB_IYUV_I420(int[] aRGB, int width, int height) {
        final int frameSize = width * height;
        final int chromasize = frameSize / 4;

        int yIndex = 0;
        int uIndex = frameSize;
        int vIndex = frameSize + chromasize;
        byte[] yuv = new byte[width * height * 3 / 2];

        int a, R, G, B, Y, U, V;
        int index = 0;
        for (int j = 0; j < height; j++) {
            for (int i = 0; i < width; i++) {
                //a = (aRGB[index] & 0xff000000) >> 24; //not using it right now
                R = (aRGB[index] & 0xff0000) >> 16;
                G = (aRGB[index] & 0xff00) >> 8;
                B = (aRGB[index] & 0xff) >> 0;

                Y = ((66 * R + 129 * G + 25 * B + 128) >> 8) + 16;
                U = ((-38 * R - 74 * G + 112 * B + 128) >> 8) + 128;
                V = ((112 * R - 94 * G - 18 * B + 128) >> 8) + 128;

                yuv[yIndex++] = (byte) ((Y < 0) ? 0 : ((Y > 255) ? 255 : Y));

                if (j % 2 == 0 && index % 2 == 0) {
                    yuv[vIndex++] = (byte) ((U < 0) ? 0 : ((U > 255) ? 255 : U));
                    yuv[uIndex++] = (byte) ((V < 0) ? 0 : ((V > 255) ? 255 : V));
                }
                index++;
            }
        }
        return yuv;
    }


    public static void NV21ToNV12(byte[] nv21,byte[] nv12,int width,int height){
        if(nv21 == null || nv12 == null)return;
        int framesize = width*height;
        int i = 0,j = 0;
        System.arraycopy(nv21, 0, nv12, 0, framesize);
        for(i = 0; i < framesize; i++){
            nv12[i] = nv21[i];
        }
        for (j = 0; j < framesize/2; j+=2)
        {
            nv12[framesize + j-1] = nv21[j+framesize];
        }
        for (j = 0; j < framesize/2; j+=2)
        {
            nv12[framesize + j] = nv21[j+framesize-1];
        }
    }
}
