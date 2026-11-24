package fr.chk.alienlive;

import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.Handler;
import android.os.Looper;
import android.view.PixelCopy;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class LiveAuditTest {
    @Test public void closedLipsCanSlideWithoutOpening(){
        // The corner line is rotated by 36.9 degrees.
        assertEquals(0f,MediaPipeTracker.projectedGap(8,6,-.6f,.8f),.0001f);
        assertEquals(5f,MediaPipeTracker.projectedGap(-3,4,-.6f,.8f),.0001f);
        assertEquals(5f,MediaPipeTracker.projectedGap(5,10,-.6f,.8f),.0001f);
    }
    @Test public void reorderedSparseJawLeavesOtherVerticesAtRest() throws Exception{
        JSONObject mesh=new JSONObject("{extras:{targetNames:['mouthSmile','jawOpen']}}");
        JSONObject primitive=new JSONObject("{targets:[{POSITION:4},{POSITION:0}]}");
        JSONArray views=new JSONArray("[{byteLength:1},{byteOffset:4,byteLength:12}]");
        JSONArray accessors=new JSONArray("[{type:'VEC3',componentType:5126,count:3,sparse:{count:1,"+
            "indices:{bufferView:0,componentType:5121},values:{bufferView:1}}}]");
        ByteBuffer bin=ByteBuffer.allocate(16).order(ByteOrder.LITTLE_ENDIAN);
        bin.put(0,(byte)2);bin.putFloat(8,.35f);
        float[] jaw=GlbFaceTargets.read(mesh,primitive,true,bin,0,views,accessors,3);
        assertArrayEquals(new float[]{0,0,0,0,0,0,0,.35f,0},jaw,0f);
        assertNull(GlbFaceTargets.read(mesh,primitive,false,bin,0,views,accessors,3));
    }

    @Test public void untexturedUnindexedModelIsVisible() throws Exception{
        String json="{\"asset\":{\"version\":\"2.0\"},\"scenes\":[{\"nodes\":[0]}],\"scene\":0,"+
            "\"nodes\":[{\"mesh\":0}],\"meshes\":[{\"primitives\":[{\"attributes\":{\"POSITION\":0}}]}],"+
            "\"buffers\":[{\"byteLength\":36}],\"bufferViews\":[{\"buffer\":0,\"byteLength\":36}],"+
            "\"accessors\":[{\"bufferView\":0,\"componentType\":5126,\"count\":3,\"type\":\"VEC3\",\"min\":[-1,-1,0],\"max\":[1,1,0]}]}";
        while(json.length()%4!=0)json+=" ";
        ByteBuffer glb=ByteBuffer.allocate(28+json.length()+36).order(ByteOrder.LITTLE_ENDIAN);
        glb.putInt(0x46546c67).putInt(2).putInt(glb.capacity()).putInt(json.length()).putInt(0x4e4f534a);
        glb.put(json.getBytes(StandardCharsets.UTF_8)).putInt(36).putInt(0x004e4942);
        for(float v:new float[]{-1,-1,0,1,-1,0,0,1,0})glb.putFloat(v);
        File f=File.createTempFile("triangle",".glb",InstrumentationRegistry.getInstrumentation().getTargetContext().getCacheDir());
        try(FileOutputStream out=new FileOutputStream(f)){out.write(glb.array());}
        CountDownLatch ready=new CountDownLatch(1);
        AtomicReference<String> error=new AtomicReference<>();
        AtomicReference<Alien3DView> preview=new AtomicReference<>();
        try(ActivityScenario<SettingsActivity> activity=ActivityScenario.launch(SettingsActivity.class)){
            activity.onActivity(a->{
                Alien3DView view=new Alien3DView(a,new Alien3DView.Status(){
                    public void onReady(){ready.countDown();}
                    public void onError(String message){error.set(message);ready.countDown();}
                });
                view.setCustomModel(f);preview.set(view);a.setContentView(view);view.onResume();
            });
            assertTrue("Initialisation du modèle",ready.await(20,TimeUnit.SECONDS));
            assertNull(error.get());
            Bitmap pixels=Bitmap.createBitmap(64,64,Bitmap.Config.ARGB_8888);
            boolean visible=false;
            for(int i=0;i<20&&!visible;i++){
                AtomicInteger status=new AtomicInteger(-1);
                CountDownLatch copied=new CountDownLatch(1);
                PixelCopy.request(preview.get(),pixels,r->{status.set(r);copied.countDown();},new Handler(Looper.getMainLooper()));
                assertTrue(copied.await(3,TimeUnit.SECONDS));
                visible=status.get()==PixelCopy.SUCCESS&&Color.red(pixels.getPixel(32,32))>100;
                if(!visible)Thread.sleep(60);
            }
            assertTrue("Triangle clair au centre du rendu OpenGL",visible);
            activity.onActivity(a->preview.get().onPause());
        }finally{f.delete();}
    }
}
