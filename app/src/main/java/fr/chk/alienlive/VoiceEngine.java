package fr.chk.alienlive;

import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.AudioTrack;
import android.media.MediaRecorder;
import android.os.Build;
import android.os.Process;

/** Local offline voice effect: granular two-tap pitch shifter and subtle robotic texture.
 *  Plays through the Android media output. It does NOT replace another app's microphone.
 *  Audio output remains within this app; Android does not replace other apps' microphone.
 */
public final class VoiceEngine {
    public interface Listener { void onState(String message); }
    public static final int SAMPLE_RATE = 44100;
    private static final float[] PITCH={.68f,.82f,1.18f,1f,1f,.53f,.91f,.76f};
    private static final int RING = 1 << 15;
    private final Listener listener;
    private volatile boolean running;
    private volatile int preset = 0;
    private volatile float depth=0.75f, effectMix=0.85f, level=0f;
    public static final String[] PRESETS = {
        "Pluthor — voix abyssale","Pluthor — souverain","Annunaki — métal",
        "Spectral — aigu","Voix normale (contrôle)","Colosse — très grave",
        "Reptilien — rauque","Oracle — mystérieux"
    };
    public void setDepth(float v){depth=Math.max(0f,Math.min(1f,v));}
    public void setMix(float v){effectMix=Math.max(0f,Math.min(1f,v));}
    public float getLevel(){return level;}

    private volatile Thread worker;
    private volatile WLipSync lipSync;
    private boolean playingBack;
    WLipSync.Pose lipPose(){
        WLipSync engine=lipSync;
        return running&&engine!=null?engine.pose():WLipSync.Pose.REST;
    }
    private AudioRecord input;
    private AudioTrack output;
    private final float[] history = new float[RING];
    private final float[] echoHistory = new float[4096];
    private long wrote = 0;
    private float phase = 0f;
    private int echoAt = 0;
    private float previousInput=0f,highPassed=0f,noiseEnvelope=0f;

    public VoiceEngine(Listener listener) { this.listener = listener; }
    public synchronized boolean isRunning() { return running; }
    public void setPreset(int p) { preset = Math.max(0,Math.min(PRESETS.length-1,p)); }
    private void state(String s) { if(listener!=null)listener.onState(s); }

    /** Caller must have requested and received RECORD_AUDIO runtime permission. */
    @SuppressWarnings("MissingPermission")
    public synchronized boolean start() { return start(true,false); }

    @SuppressWarnings("MissingPermission")
    synchronized boolean start(boolean playback,boolean analyzeLips) {
        if(running && playingBack==playback && (lipSync!=null)==analyzeLips)return true;
        if(worker!=null || input!=null)stop(false);
        try {
            int minIn = AudioRecord.getMinBufferSize(SAMPLE_RATE,AudioFormat.CHANNEL_IN_MONO,AudioFormat.ENCODING_PCM_16BIT);
            int minOut = AudioTrack.getMinBufferSize(SAMPLE_RATE,AudioFormat.CHANNEL_OUT_MONO,AudioFormat.ENCODING_PCM_16BIT);
            if(minIn<=0 || (playback && minOut<=0)) {state("Audio non pris en charge par ce téléphone");return false;}
            input = new AudioRecord(MediaRecorder.AudioSource.MIC,SAMPLE_RATE,AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT, Math.max(8192,minIn*2));
            if(input.getState()!=AudioRecord.STATE_INITIALIZED){release();state("Microphone indisponible");return false;}
            if(playback){
                AudioAttributes.Builder a = new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH);
                if(Build.VERSION.SDK_INT>=29) a.setAllowedCapturePolicy(AudioAttributes.ALLOW_CAPTURE_BY_ALL);
                AudioFormat fmt = new AudioFormat.Builder().setSampleRate(SAMPLE_RATE)
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build();
                output = new AudioTrack(a.build(),fmt,Math.max(8192,minOut*2),AudioTrack.MODE_STREAM,AudioManager.AUDIO_SESSION_ID_GENERATE);
                if(output.getState()!=AudioTrack.STATE_INITIALIZED){release();state("Sortie audio indisponible");return false;}
            }
            wrote=0;phase=0;echoAt=0;
            previousInput=0f;highPassed=0f;noiseEnvelope=0f;level=0f;
            java.util.Arrays.fill(history,0f);java.util.Arrays.fill(echoHistory,0f);
            input.startRecording();
            if(input.getRecordingState()!=AudioRecord.RECORDSTATE_RECORDING){
                release();state("Microphone indisponible");return false;
            }
            if(playback)output.play();
            playingBack=playback;
            WLipSync analyzer=analyzeLips?new WLipSync(SAMPLE_RATE):null;
            lipSync=analyzer;
            AudioRecord recorder=input;
            AudioTrack player=output;
            running=true;
            worker = new Thread(()->loop(recorder,player,analyzer),"CHK-Voice-FX");
            worker.start();
            state(playback?"Voix activée dans CHK":"wLipSync actif • micro seul");
            return true;
        } catch (SecurityException e) {release();state("Autorisation microphone refusée");return false;}
          catch (Exception e) {release();state("Audio non initialisé : "+e.getClass().getSimpleName());return false;}
    }
    private void loop(AudioRecord recorder,AudioTrack player,WLipSync analyzer) {
        Process.setThreadPriority(Process.THREAD_PRIORITY_AUDIO);
        final short[] in = new short[512];
        final short[] out = new short[512];
        long silentSamples=0;
        while(running && worker==Thread.currentThread()) {
            int count;
            try{ count=recorder.read(in,0,in.length); }
            catch(Exception e){ if(running) state("Enregistrement interrompu");break; }
            if(count<=0){if(count<0)state("Le microphone a été interrompu");break;}
            if(!running || worker!=Thread.currentThread())break;
            if(analyzer!=null)analyzer.accept(in,count);
            float energy=0f;
            for(int i=0;i<count;i++){
                energy+=((float)in[i]/32768f)*((float)in[i]/32768f);
                if(player!=null)out[i] = transform(in[i]);
            }
            float measured=(float)Math.sqrt(energy/Math.max(1,count));
            level=level*(measured>level?.48f:.82f)+measured*(measured>level?.52f:.18f);
            if(energy<0.0000001f){silentSamples+=count;}
            else {silentSamples=0;}
            if(silentSamples>SAMPLE_RATE*4){
                state("Aucun son du micro : vérifie si une autre application le monopolise");
                silentSamples=0;
            }
            if(player==null)continue;
            try {int written=0;while(written<count&&running){int n=player.write(out,written,count-written);if(n<=0)break;written+=n;}}
            catch(Exception e){if(running)state("Lecture audio interrompue");break;}
        }
        if(worker==Thread.currentThread())running=false;
    }
    private float read(float delay) {
        float index = (float)wrote-delay;
        int lo=(int)Math.floor(index);
        float sub=index-lo;
        float a=history[lo&(RING-1)];
        float b=history[(lo+1)&(RING-1)];
        return a+(b-a)*sub;
    }
    private short transform(short sample) {
        float inputSample=sample/32768f;
        // Filtrage passe-haut et réduction du souffle, sans latence supplémentaire.
        highPassed=inputSample-previousInput+.995f*highPassed;
        previousInput=inputSample;
        float filtered=highPassed;
        noiseEnvelope+=((Math.abs(filtered)>noiseEnvelope)?.10f:.003f)*(Math.abs(filtered)-noiseEnvelope);
        if(noiseEnvelope<.002f)filtered*=.30f;
        history[(int)(wrote&(RING-1))]=filtered;
        int mode=preset;
        float pitch=1f+(PITCH[Math.min(mode,PITCH.length-1)]-1f)*(.45f+depth*.9f);
        float v;
        if(mode==4 || wrote<SAMPLE_RATE/5){v=filtered;}
        else {
            float sweep=SAMPLE_RATE*.1179f;
            phase+=(1f-pitch)/sweep;
            if(phase>=1f)phase-=1f;
            if(phase<0f)phase+=1f;
            float other=phase+.5f;
            if(other>=1f)other-=1f;
            float w=.5f-.5f*(float)Math.cos(2f*Math.PI*phase);
            float a=read(SAMPLE_RATE*.0612f+sweep*phase);
            float b=read(SAMPLE_RATE*.0612f+sweep*other);
            v=w*a+(1f-w)*b;
        }
        if(mode!=4) {
            int delay=mode==7?3308:(mode==2?1544:2425);
            float old=echoHistory[(echoAt-delay)&(echoHistory.length-1)];
            echoHistory[echoAt]=v;
            echoAt=(echoAt+1)&(echoHistory.length-1);
            float mix=mode==2?0.09f:(mode==7?.30f:.17f);
            v=v*(1f-Math.min(.25f,mix*.35f))+old*mix;
            if(mode==1 || mode==6) v+=.10f*v*(float)Math.sin(2*Math.PI*120*wrote/SAMPLE_RATE);
            if(mode==2) v+=.07f*v*(float)Math.sin(2*Math.PI*48*wrote/SAMPLE_RATE);
        }
        wrote++;
        v=filtered*(1f-effectMix)+v*effectMix;
        float clipped = v*1.3f;
        clipped=clipped/(1f+Math.abs(clipped)*.4f);
        return (short)(Math.max(-1,Math.min(1,clipped))*32767f);
    }
    public synchronized void stop() { stop(true); }
    private void stop(boolean announce) {
        running=false;level=0f;
        if(input!=null){try{input.stop();}catch(Exception ignored){}}
        if(output!=null){try{output.pause();output.flush();}catch(Exception ignored){}}
        Thread t=worker;
        worker=null;
        if(t!=null&&t!=Thread.currentThread()){try{t.join(250);}catch(InterruptedException e){Thread.currentThread().interrupt();}}
        lipSync=null;
        release();
        if(announce)state("Microphone arrêté");
    }
    private void release() {
        if(input!=null){try{input.release();}catch(Exception ignored){} input=null;}
        if(output!=null){try{output.pause();output.flush();output.release();}catch(Exception ignored){}output=null;}
    }
}
