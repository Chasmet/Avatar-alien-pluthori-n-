package fr.chk.alienlive;

import android.media.Image;
import android.graphics.Rect;
import com.google.mediapipe.framework.image.ByteBufferImageBuilder;
import com.google.mediapipe.framework.image.MPImage;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/** Reuses RGB and plane storage on the camera thread. Rotation is applied once,
 * before inference, so points and pose use upright, unmirrored coordinates. */
final class UprightCameraFrame {
    private final byte[][] plane = new byte[3][];
    private byte[] rgb;
    private ByteBuffer input;

    MPImage convert(Image image, int rotation) {
        Image.Plane[] planes = image.getPlanes();
        for (int i = 0; i < 3; i++) {
            ByteBuffer source = planes[i].getBuffer().duplicate();
            int length = source.remaining();
            if (plane[i] == null || plane[i].length < length) plane[i] = new byte[length];
            source.get(plane[i], 0, length);
        }
        Rect crop = image.getCropRect();
        int w = crop.width(), h = crop.height();
        int outW = rotation % 180 == 0 ? w : h;
        int outH = rotation % 180 == 0 ? h : w;
        if (rgb == null || rgb.length != w*h*3) {
            rgb = new byte[w*h*3];
            input = ByteBuffer.allocateDirect(rgb.length).order(ByteOrder.nativeOrder());
        }
        int ys = planes[0].getRowStride(), us = planes[1].getRowStride(), vs = planes[2].getRowStride();
        int yp = planes[0].getPixelStride(), up = planes[1].getPixelStride(), vp = planes[2].getPixelStride();
        int step = rotation == 90 ? outW : rotation == 270 ? -outW : rotation == 180 ? -1 : 1;
        for (int row = 0; row < h; row++) {
            int sy = crop.top + row;
            int dst = rotation == 90 ? h-1-row : rotation == 180 ? w*(h-row)-1 :
                      rotation == 270 ? (w-1)*h+row : row*w;
            int yi = sy*ys + crop.left*yp;
            int uBase = (sy/2)*us, vBase = (sy/2)*vs;
            for (int col = 0; col < w; col++, yi += yp, dst += step) {
                int chroma = (crop.left+col)/2;
                int y = 298*Math.max(0,(plane[0][yi]&255)-16);
                int u = (plane[1][uBase+chroma*up]&255)-128;
                int v = (plane[2][vBase+chroma*vp]&255)-128;
                int index = 3*dst;
                rgb[index] = channel(y+409*v);
                rgb[index+1] = channel(y-100*u-208*v);
                rgb[index+2] = channel(y+516*u);
            }
        }
        input.clear();
        input.put(rgb).rewind();
        return new ByteBufferImageBuilder(input, outW, outH, MPImage.IMAGE_FORMAT_RGB).build();
    }
    private static byte channel(int value) {
        return (byte)Math.min(255,Math.max(0,(value+128)>>8));
    }
}
