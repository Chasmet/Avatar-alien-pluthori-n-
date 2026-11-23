package fr.chk.alienlive;

import android.Manifest;
import android.os.Build;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;
import static org.junit.Assume.assumeTrue;

@RunWith(AndroidJUnit4.class)
public class LipSyncTest {
    /** Band-pass filters excited by a synthetic glottal pulse train. */
    static short[] vowel(int... centers){
        double[] gain={1,.63,.29},width={90,120,160};
        double[] a1=new double[3],a2=new double[3],b0=new double[3],s1=new double[3],s2=new double[3];
        for(int k=0;k<3;k++){
            double beta=Math.tan(Math.PI*width[k]/44100d);
            double g=1/(1+beta);
            a1[k]=-2*g*Math.cos(2*Math.PI*centers[k]/44100d);
            a2[k]=2*g-1;b0[k]=1-g;
        }
        double[] raw=new double[8820];
        double max=0;
        for(int i=0;i<raw.length;i++){
            double impulse=i%320==0?1:0;
            for(int k=0;k<3;k++){
                double output=b0[k]*impulse+s1[k];
                s1[k]=-a1[k]*output+s2[k];s2[k]=-b0[k]*impulse-a2[k]*output;
                raw[i]+=gain[k]*output;
            }
            max=Math.max(max,Math.abs(raw[i]));
        }
        short[] pcm=new short[raw.length];
        for(int i=0;i<raw.length;i++)pcm[i]=(short)(14000*raw[i]/max);
        return pcm;
    }
    @Test public void vowelShapesAndSilence(){
        WLipSync engine=new WLipSync(44100);
        short[] a=vowel(800,1200,2700),i=vowel(320,2300,3000);
        engine.accept(a,a.length);
        assertEquals("A",engine.pose().vowel);
        assertTrue(engine.pose().open>.7f);
        engine.accept(i,i.length);
        assertEquals("I",engine.pose().vowel);
        assertTrue(engine.pose().wide>engine.pose().open);
        engine.accept(new short[a.length],a.length);
        assertSame(WLipSync.Pose.REST,engine.pose());
    }
    @Test public void microphoneOnlyCanStopAndRestart(){
        assumeTrue(Build.VERSION.SDK_INT>=28);
        var instrumentation=InstrumentationRegistry.getInstrumentation();
        instrumentation.getUiAutomation().grantRuntimePermission(
            instrumentation.getTargetContext().getPackageName(),Manifest.permission.RECORD_AUDIO);
        VoiceEngine engine=new VoiceEngine(null);
        try {
            for(int i=0;i<2;i++){
                assertTrue(engine.start(false,true));
                assertTrue(engine.isRunning());
                engine.stop();
                assertFalse(engine.isRunning());
                assertSame(WLipSync.Pose.REST,engine.lipPose());
            }
        }finally{engine.stop();}
    }
}
