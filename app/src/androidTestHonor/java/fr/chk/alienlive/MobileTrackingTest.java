package fr.chk.alienlive;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.media.Image;
import android.media.ImageReader;
import android.media.ImageWriter;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import java.io.InputStream;
import java.nio.ByteBuffer;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

/** Exercises the actual native Android graph and Camera2 YUV/rotation path.
 * Test-only downsample of Google's mediapipe-assets/portrait.jpg. */
@RunWith(AndroidJUnit4.class)
public class MobileTrackingTest {
    @Test public void portraitAndReacquisitionFromRotatedYuv() throws Exception {
        var instrumentation=InstrumentationRegistry.getInstrumentation();
        Bitmap portrait;
        try(InputStream input=instrumentation.getContext().getAssets().open("face-test.jpg")){
            portrait=BitmapFactory.decodeStream(input);
        }
        try(MediaPipeTracker tracker=new MediaPipeTracker(instrumentation.getTargetContext());
            ImageReader reader=ImageReader.newInstance(480,360,ImageFormat.YUV_420_888,2);
            ImageWriter writer=ImageWriter.newInstance(reader.getSurface(),2)){
            for(int i=0;i<5;i++){
                boolean blank=i==2 || i==3;
                Image queued=writer.dequeueInputImage();
                for(int n=0;n<3;n++){
                    Image.Plane plane=queued.getPlanes()[n];
                    ByteBuffer bytes=plane.getBuffer();
                    int w=n==0?480:240,h=n==0?360:180;
                    for(int y=0;y<h;y++)for(int x=0;x<w;x++){
                        int value=128;
                        if(n==0){
                            value=16;
                            if(!blank){
                                int pixel=portrait.getPixel((359-y)*portrait.getWidth()/360,x*portrait.getHeight()/480);
                                value+=((66*((pixel>>16)&255)+129*((pixel>>8)&255)+25*(pixel&255)+128)>>8);
                            }
                        }
                        bytes.put(y*plane.getRowStride()+x*plane.getPixelStride(),(byte)value);
                    }
                }
                writer.queueInputImage(queued);
                Image captured=null;
                for(int retry=0;retry<100 && captured==null;retry++){
                    captured=reader.acquireNextImage();
                    if(captured==null)Thread.sleep(10);
                }
                assertNotNull("Camera2 YUV frame",captured);
                try(Image frame=captured){
                    MediaPipeTracker.Frame mapped=tracker.detect(frame,90,(i+1)*100L);
                    if(blank){assertNull(mapped);continue;}
                    assertNotNull("Face on upright input",mapped);
                    assertTrue(mapped.x>.15f && mapped.x<.85f);
                    assertTrue(mapped.smile>.05f);
                    assertTrue(Float.isFinite(mapped.pitch) && Float.isFinite(mapped.gazeX));
                }
            }
        }finally{portrait.recycle();}
    }
}
