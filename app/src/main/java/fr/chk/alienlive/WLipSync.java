package fr.chk.alienlive;

import java.util.Arrays;

/**
 * Android/Java port of wLipSync's 16 kHz MFCC and cosine profile classifier.
 * Upstream github.com/mrxz/wLipSync, MIT, see assets/licenses/wLipSync.txt.
 * Works on the original mono PCM stream before the voice effect. All FFT and
 * filter workspaces are allocated once. Only published poses cross threads.
 */
final class WLipSync {
    private static final int RATE=16000, WINDOW=1024, BANDS=30, MFCC=12, TAPS=48;
    private static final String LABELS="AIUEOS";
    static final class Pose {
        static final Pose REST=new Pose(0f,0f,0f,"");
        final float open,round,wide;
        final String vowel;
        Pose(float open,float round,float wide,String vowel){
            this.open=open; this.round=round; this.wide=wide; this.vowel=vowel;
        }
    }

    private final int sampleRate;
    private final float[] ring;
    private final float[] lowPass=new float[TAPS], hamming=new float[WINDOW];
    private final float[] real=new float[WINDOW], imaginary=new float[WINDOW];
    private final float[] cos=new float[WINDOW/2],sin=new float[WINDOW/2];
    private final int[] reversed=new int[WINDOW];
    private final float[][] mel=new float[BANDS][WINDOW/2+1], dct=new float[MFCC][BANDS];
    private final int[] firstBin=new int[BANDS],lastBin=new int[BANDS];
    private final float[] bands=new float[BANDS], features=new float[MFCC], weights=new float[6];
    private final float[] referenceNorm=new float[LipProfile.CENTERS.length];
    private int write,filled,since;
    private volatile Pose latest=Pose.REST;

    WLipSync(int sampleRate){
        this.sampleRate=sampleRate;
        ring=new float[(int)Math.ceil(WINDOW*(double)sampleRate/RATE)+TAPS];
        double cutoff=7500d/sampleRate, sum=0;
        for(int i=0;i<TAPS;i++){
            double x=i-(TAPS-1)*.5;
            lowPass[i]=(float)(Math.sin(2*Math.PI*cutoff*x)/(Math.PI*x)*
                (.54-.46*Math.cos(2*Math.PI*i/(TAPS-1))));
            sum+=lowPass[i];
        }
        for(int i=0;i<TAPS;i++)lowPass[i]/=sum;
        for(int i=0;i<WINDOW;i++){
            reversed[i]=Integer.reverse(i) >>> (32-10);
            hamming[i]=(float)(.54-.46*Math.cos(2*Math.PI*i/(WINDOW-1)));
        }
        for(int i=0;i<WINDOW/2;i++){
            cos[i]=(float)Math.cos(-2*Math.PI*i/WINDOW);
            sin[i]=(float)Math.sin(-2*Math.PI*i/WINDOW);
        }
        double melStep=Math.log1p(RATE/1400d)/(BANDS+1);
        for(int b=0;b<BANDS;b++){
            double lo=700*Math.expm1(b*melStep), mid=700*Math.expm1((b+1)*melStep);
            double hi=700*Math.expm1((b+2)*melStep);
            firstBin[b]=(int)Math.ceil(lo*WINDOW/RATE)+1;
            lastBin[b]=Math.min(WINDOW/2,(int)Math.floor(hi*WINDOW/RATE));
            for(int k=firstBin[b];k<=lastBin[b];k++){
                double hz=k*(double)RATE/WINDOW;
                mel[b][k]=(float)(2*Math.min((hz-lo)/(mid-lo),(hi-hz)/(hi-mid))/(hi-lo));
            }
            for(int c=0;c<MFCC;c++)dct[c][b]=(float)Math.cos(Math.PI*(b+.5)*(c+1)/BANDS);
        }
        for(int i=0;i<referenceNorm.length;i++){
            double squared=0;
            for(float v:LipProfile.CENTERS[i])squared+=v*v;
            referenceNorm[i]=(float)Math.sqrt(squared);
        }
    }
    Pose pose(){return latest;}
    void accept(short[] pcm,int count){
        for(int i=0;i<count;i++){
            ring[write]=pcm[i]/32768f;
            if(++write==ring.length)write=0;
            if(filled<ring.length)filled++;
            if(++since>=sampleRate/40 && filled==ring.length){
                since=0; analyze();
            }
        }
    }
    private void analyze(){
        float previous=0f;
        double energy=0;
        for(int i=0;i<WINDOW;i++){
            int index=(write+(int)((long)i*sampleRate/RATE))%ring.length;
            float sample=0;
            for(float tap:lowPass){
                sample+=tap*ring[index];
                if(++index==ring.length)index=0;
            }
            energy+=sample*sample;
            real[reversed[i]]=(sample-.97f*previous)*hamming[i];
            previous=sample;
        }
        float volume=clamp(((float)Math.sqrt(energy/WINDOW)-.005f)/.042f);
        if(volume==0f){latest=Pose.REST;return;}
        fft();
        for(int k=0;k<=WINDOW/2;k++)real[k]=(float)Math.hypot(real[k],imaginary[k]);
        for(int b=0;b<BANDS;b++){
            double total=0;
            for(int k=firstBin[b];k<=lastBin[b];k++)total+=mel[b][k]*real[k];
            bands[b]=(float)(10*Math.log10(Math.max(1e-12,total)));
        }
        double norm=0;
        for(int c=0;c<MFCC;c++){
            float sum=0;
            for(int b=0;b<BANDS;b++)sum+=dct[c][b]*bands[b];
            features[c]=sum;norm+=sum*sum;
        }
        norm=Math.sqrt(norm);
        Arrays.fill(weights,0);
        for(int row=0;row<referenceNorm.length;row++){
            double dot=0;
            for(int c=0;c<MFCC;c++)dot+=features[c]*LipProfile.CENTERS[row][c];
            double similarity=Math.max(0, dot/Math.max(1e-9,norm*referenceNorm[row]));
            int label=LABELS.indexOf(LipProfile.LABELS.charAt(row));
            weights[label]=Math.max(weights[label],(float)similarity);
        }
        int best=0;
        for(int i=1;i<weights.length;i++)if(weights[i]>weights[best])best=i;
        float peak=weights[best],total=0;
        if(peak<=0f){latest=Pose.REST;return;}
        // Divide by the peak before raising to the 100th power. The normalized
        // weights are identical and quiet or atypical vowels cannot underflow.
        for(int i=0;i<weights.length;i++){
            weights[i]=(float)Math.pow(weights[i]/peak,100);
            total+=weights[i];
        }
        for(int i=0;i<weights.length;i++)weights[i]/=total;
        float a=weights[0],i=weights[1],u=weights[2],e=weights[3],o=weights[4];
        latest=new Pose(volume*(a+.22f*i+.28f*u+.49f*e+.67f*o+.075f*weights[5]),
            volume*(u+.78f*o),volume*(i+.68f*e),LABELS.substring(best,best+1));
    }
    private static float clamp(float v){return Math.max(0f,Math.min(1f,v));}
    private void fft(){
        Arrays.fill(imaginary,0);
        for(int half=1;half<WINDOW;half*=2){
            int stride=WINDOW/(2*half);
            for(int start=0;start<WINDOW;start+=2*half){
                for(int j=0;j<half;j++){
                    int a=start+j,b=a+half,k=j*stride;
                    float re=real[b]*cos[k]-imaginary[b]*sin[k], im=real[b]*sin[k]+imaginary[b]*cos[k];
                    real[b]=real[a]-re;imaginary[b]=imaginary[a]-im;
                    real[a]+=re;imaginary[a]+=im;
                }
            }
        }
    }
}
