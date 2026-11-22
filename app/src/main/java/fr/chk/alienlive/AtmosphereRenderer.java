package fr.chk.alienlive;

import android.graphics.Canvas;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.content.res.Resources;
import android.graphics.PorterDuffColorFilter;
import android.graphics.PorterDuff;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.os.SystemClock;

import java.util.Random;

/**
 * CHK Atmosphere: procedural weather, depth/parallax and weather cycles.
 * Rendered on the device; no internet, no weather API, no microphone.
 *
 * Rendering choices are conservative for mobile: low object counts, cached arrays,
 * deterministic motion, no allocations per particle. Atmospheric effects are
 * illustrative 2D layers, not simulated physical clouds or full 3D worlds.
 */
public final class AtmosphereRenderer {
    public static final String[] WEATHER_NAMES = {
        "Météo évolutive", "Ciel calme", "Brume et poussière", "Pluie", "Tempête", "Orage"
    };
    public static final String[] MODE_NAMES = {
        "Équilibré 30 FPS", "Économie 20 FPS"
    };
    private static final long CHANGE_MS = 65_000L;
    private static final long FADE_MS = 12_000L;
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Bitmap cloudTexture;
    private final Paint air = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final RectF reusableOval = new RectF();
    private Shader cachedGlow;
    private PorterDuffColorFilter cachedCloudTint;
    private int tintKey=-1;
    private int glowScene=-1;
    private float glowW=-1f,glowH=-1f;
    private final float[] x = new float[160], y = new float[160], size = new float[160], speed = new float[160], phase = new float[160];
    private final float[] cloudX = new float[18], cloudY = new float[18], cloudScale = new float[18];
    private final long started = SystemClock.uptimeMillis();
    private int lastScene=-1, lastMode=-1, lastIntensity=-1;

    public AtmosphereRenderer(Resources resources) {
        cloudTexture=BitmapFactory.decodeResource(resources,R.drawable.cloud_volume);
        Random random = new Random(0xC0FEE45);
        for (int i=0; i<x.length; i++) {
            x[i]=random.nextFloat();y[i]=random.nextFloat();size[i]=0.4f+random.nextFloat()*1.8f;
            speed[i]=0.25f+random.nextFloat()*1.6f;phase[i]=random.nextFloat()*6.283f;
        }
        for (int i=0;i<cloudX.length;i++) {
            cloudX[i]=random.nextFloat()*1.45f-0.22f;
            cloudY[i]=0.07f+random.nextFloat()*0.76f;
            cloudScale[i]=0.65f+random.nextFloat()*1.2f;
        }
    }

    public void reset() { lastScene=-1; }
    public String currentWeather(int mode,int scene) {
        int weather = effectiveMode(mode,scene,SystemClock.uptimeMillis()-started);
        return WEATHER_NAMES[weather];
    }
    public void renderBehind(Canvas c, float w,float h, int scene,int selectedMode,int intensity,float poseX,float poseY,boolean saver) {
        long millis = SystemClock.uptimeMillis()-started;
        int current=effectiveMode(selectedMode,scene,millis);
        float fade = selectedMode==0 ? Math.min(1f,(millis%CHANGE_MS)/(float)FADE_MS) : 1f;
        int previous = selectedMode==0 && millis>=CHANGE_MS ? weatherForCycle(scene,(millis/CHANGE_MS)-1) : current;
        if (lastScene!=scene || lastMode!=selectedMode || lastIntensity!=intensity) {
            lastScene=scene;lastMode=selectedMode;lastIntensity=intensity;
        }
        float amount = 0.22f + Math.max(0,Math.min(100,intensity))/100f*0.78f;
        float t=millis/1000f;
        // The environment itself subtly sways with the virtual point-of-view.
        c.save();
        float dx=(poseX-.5f)*w*.045f,dy=(poseY-.5f)*h*.016f;
        c.translate(dx,dy);
        if(selectedMode==0 && fade<1f) {
            weatherLayer(c,w,h,scene,previous,t,amount*(1f-fade),saver);
        }
        weatherLayer(c,w,h,scene,current,t,amount*fade,saver);
        c.restore();
    }
    public void renderFront(Canvas c,float w,float h,int scene,int selectedMode,int intensity,boolean saver) {
        long millis=SystemClock.uptimeMillis()-started;
        int mode=effectiveMode(selectedMode,scene,millis);
        float amount = (0.22f + Math.max(0,Math.min(100,intensity))/100f*0.78f);
        float t=millis/1000f;
        // Close dust and droplets cross in front of the performer for depth.
        drawParticles(c,w,h,t,scene,mode,amount,saver,true);
        if(mode==5) {
            float strobe = (float)Math.pow(Math.max(0,Math.sin(t*2.1f)*Math.sin(t*0.67f)),12f);
            if(strobe>0.28f) {
                air.setShader(null);air.setColor(Color.argb((int)(70f*strobe*amount),196,211,253));
                c.drawRect(0,0,w,h,air);
            }
        }
    }
    private static int effectiveMode(int chosen,int scene,long ms) {
        return chosen==0?weatherForCycle(scene,ms/CHANGE_MS):Math.min(5,Math.max(1,chosen));
    }
    private static int weatherForCycle(int scene,long cycle) {
        // Space has atmospheric *effects*, not terrestrial rain outside a spaceship.
        int[][] cycles={
            {1,2,4,5,1,3}, {1,2,5,4,2,1}, {2,3,1,4,5,2}, {2,3,1,4,5,2},
            {2,1,2,5,1,2}, {1,2,1,4,1,2}, {1,2,1,5,1,2}, {2,4,1,5,2,4}
        };
        int[] preset=cycles[(scene%cycles.length+cycles.length)%cycles.length];
        return preset[(int)((cycle%preset.length+preset.length)%preset.length)];
    }
    private void weatherLayer(Canvas c,float w,float h,int scene,int mode,float t,float amount,boolean saver) {
        if (amount<=0.001f) return;
        final boolean weatherHeavy=(mode==4||mode==5);
        int baseA=(int)(amount*(mode==1?12:mode==2?29:weatherHeavy?52:25));
        p.setShader(null);
        p.setColor(Color.argb(Math.min(255,baseA),scene==2?126:32,scene==2?83:79,scene==2?49:117));
        c.drawRect(0,0,w,h,p);
        // Light changes are intentionally gradual to evoke a day-night cycle.
        float day=(float)Math.sin(t*.015f + scene*.8f);
        int shade=(int)(Math.max(0,-day)*36f*amount);
        p.setColor(Color.argb(shade,8,10,40));c.drawRect(0,0,w,h,p);
        drawClouds(c,w,h,t,scene,mode,amount,saver);
        drawGroundMist(c,w,h,t,scene,mode,amount,saver);
        drawParticles(c,w,h,t,scene,mode,amount,saver,false);
        drawMovingObjects(c,w,h,t,scene,amount);
        if(scene==2)drawAncientTempleEnergy(c,w,h,t,amount,saver);
        if(scene>=4&&scene<=7)drawNewSceneEffects(c,w,h,t,scene,amount,saver);
        if((mode==3||mode==5)&&scene!=1)drawRain(c,w,h,t,amount,saver,mode==5);
        if(weatherHeavy && scene!=1) drawWind(c,w,h,t,amount,saver);
        drawLighting(c,w,h,t,scene,mode,amount);
        if(mode==5)drawLightning(c,w,h,t,scene,amount);
    }
    /** Effets propres aux quatre nouveaux décors. Tout est derrière le GLB.
     *  Peu de géométrie : 12-40 particules par environnement, économie de batterie respectée.
     */
    private void drawNewSceneEffects(Canvas c,float w,float h,float t,int scene,float amount,boolean saver) {
        air.setStyle(Paint.Style.FILL);
        air.setShader(null);
        final int n=saver?11:32;
        for(int j=0;j<n;j++){
            int i=(j*3+17)%x.length;
            float tt=t*(scene==6?.22f:scene==7?.085f:.053f);
            float xx=fract(x[i]+tt*speed[i]*(j%2==0?1f:-.70f))*w;
            float yy=fract(y[i]+t*(scene==4?-.035f:scene==5?-.018f:scene==6?.015f:.011f))*(h*.83f);
            float pulse=.5f+.5f*(float)Math.sin(t*(scene==5?2.2f:1.13f)+phase[i]);
            int alpha=(int)Math.min(240,Math.max(0,amount*(45+115*pulse)));
            float sizePx=Math.max(1f,w*(scene==4?.0031f:scene==5?.0027f:.0022f));
            if(scene==4){
                air.setColor(Color.argb(alpha,88,208,255));
                c.drawCircle(xx,yy,sizePx*(.6f+pulse),air);
                if(j%6==0){
                    air.setStyle(Paint.Style.STROKE);air.setStrokeWidth(Math.max(1f,w*.0009f));
                    air.setColor(Color.argb(alpha/3,131,210,255));
                    c.drawCircle(xx,yy,sizePx*(3.0f+pulse*2.0f),air);
                    air.setStyle(Paint.Style.FILL);
                }
            }else if(scene==5){
                air.setColor(Color.argb(alpha,65,239,165));
                c.drawCircle(xx,yy,sizePx*(.6f+pulse),air);
            }else if(scene==6){
                // Navettes lumineuses au loin, trajectoires en perspective.
                air.setColor(Color.argb(alpha/3,76,154,219));
                c.drawLine(xx-w*.032f,yy+w*.006f,xx,yy,air);
                air.setColor(Color.argb(alpha,166,213,255));
                c.drawCircle(xx,yy,Math.max(1f,sizePx*.7f),air);
            }else if(j%4==0){
                // Météores diffus derrière le personnage, en complément de la poussière.
                air.setStyle(Paint.Style.STROKE);
                air.setStrokeWidth(Math.max(1f,w*.0014f));
                air.setColor(Color.argb(alpha/3,242,173,142));
                c.drawLine(xx-w*.045f,yy-h*.032f,xx,yy,air);
                air.setStyle(Paint.Style.FILL);
            }else{
                air.setColor(Color.argb(alpha/2,244,175,130));
                c.drawCircle(xx,yy,sizePx*.8f,air);
            }
        }
        air.setStyle(Paint.Style.FILL);
    }
    private void drawClouds(Canvas c,float w,float h,float t,int scene,int mode,float amount,boolean saver) {
        int count = scene==0?(saver?2:4):scene==1?(saver?3:6):(saver?6:12);
        boolean heavy=(mode>=2);
        float movement=(mode==4?0.032f:mode==5?0.026f:.012f);
        int key=mode*10+scene;
        if(cachedCloudTint==null||tintKey!=key){
            cachedCloudTint=new PorterDuffColorFilter(
                Color.rgb(mode==4?145:scene==2?196:157, mode==4?113:scene==2?162:167, mode==4?96:scene==2?123:202),
                PorterDuff.Mode.MULTIPLY);
            tintKey=key;
        }
        PorterDuffColorFilter cloudTint=cachedCloudTint;
        for(int i=0;i<count;i++) {
            float depth=0.35f+(i%4)*.18f;
            float nx=fract(cloudX[i]+t*movement*depth*(i%2==0?1f:-.65f));
            float px=(nx*1.6f-.3f)*w;
            float py=(scene==0?cloudY[i]*.50f:scene==1?cloudY[i]*.68f:cloudY[i])*h;
            float cw=w*(.2f+cloudScale[i]*.28f)*depth*2f;
            float ch=h*(.033f+cloudScale[i]*.022f);
            int alpha=(int)(amount*(heavy?28:11)*(i%3==0?1.5f:1f));
            if(scene==1) alpha=(int)(alpha*.7f);
            if(scene==0) alpha=(int)(alpha*.35f);
            if(alpha<1)continue;
            int r=mode==4?145:scene==2?196:157;
            int g=mode==4?113:scene==2?162:167;
            int b=mode==4?96:scene==2?123:202;
            air.setShader(null);air.setStyle(Paint.Style.FILL);
            if(cloudTexture!=null) {
                air.setAlpha(Math.min(255,alpha*5));
                air.setColorFilter(cloudTint);
                reusableOval.set(px-cw*.6f,py-ch*1.7f,px+cw*.6f,py+ch*1.7f);
                c.drawBitmap(cloudTexture,null,reusableOval,air);
                air.setColorFilter(null);air.setAlpha(255);
            } else {
                air.setColor(Color.argb(alpha,r,g,b));
                reusableOval.set(px-cw*.5f,py-ch*.8f,px+cw*.5f,py+ch*.8f);
                c.drawOval(reusableOval,air);
            }
        }
    }
    /** Bancs de brume de profondeur intermédiaire, dérive continue sans allocations bitmap. */
    private void drawGroundMist(Canvas c,float w,float h,float t,int scene,int mode,float amount,boolean saver){
        if(scene==1||cloudTexture==null||mode==1)return;
        int bands=saver?2:4;
        for(int i=0;i<bands;i++){
            float drift=(float)Math.sin(t*(.082f+i*.012f)+i*1.71f);
            float px=w*(.20f+i*.23f+drift*.14f);
            float py=h*(.64f+i*.085f)+(float)Math.sin(t*.17f+i)*h*.008f;
            float cw=w*(.57f+i*.12f),ch=h*(.10f+i*.025f);
            reusableOval.set(px-cw,py-ch,px+cw,py+ch);
            air.setColorFilter(cachedCloudTint);
            air.setAlpha((int)Math.min(255f,(mode>=4?170f:110f)*amount));
            c.drawBitmap(cloudTexture,null,reusableOval,air);
        }
        air.setColorFilter(null);
        air.setAlpha(255);
    }
    private void drawParticles(Canvas c,float w,float h,float t,int scene,int mode,float amount,boolean saver,boolean foreground) {
        int n=saver?42:95;
        int offset=foreground?80:0;
        if(foreground)n=saver?23:45;
        for(int j=0;j<n;j++) {
            int i=(j+offset)%x.length;
            float depth=foreground?(1.15f+(i%5)*.2f):(.35f+(i%5)*.14f);
            float wind=(mode==4?.10f:mode==5?.068f:.018f);
            float xx=fract(x[i]+t*speed[i]*wind*depth+(float)Math.sin(t*.3f+phase[i])*.003f)*w;
            float yy=fract(y[i]+t*speed[i]*(mode==3||mode==5?.13f:.012f)*depth)*h;
            float radius=(foreground?1.1f:.65f)*size[i]*depth*Math.max(1f,w/700f);
            int a=(int)((foreground?42:25)*amount*(.5f+.5f*(float)Math.sin(t*1.4f+phase[i])));
            if(mode==2||mode==4)a=(int)(a*1.7f);
            air.setShader(null);
            air.setColor(Color.argb(Math.min(160,Math.max(2,a)),scene==2?229:191,scene==2?173:207,scene==2?118:246));
            c.drawCircle(xx,yy,radius,air);
        }
    }
    private void drawRain(Canvas c,float w,float h,float t,float amount,boolean saver,boolean storm) {
        air.setShader(null);air.setStyle(Paint.Style.STROKE);
        air.setStrokeCap(Paint.Cap.ROUND);
        air.setStrokeWidth(Math.max(.65f,w*.0014f));
        air.setColor(Color.argb((int)(amount*(storm?79:48)),188,202,244));
        int n=saver?30:65;
        float drift=storm?-.07f:-.025f;
        for(int i=0;i<n;i++) {
            float xx=fract(x[i]+t*speed[i]*drift)*w;
            float yy=fract(y[i]+t*(.38f+speed[i]*.2f))*h;
            float sz=h*(.017f+.009f*size[i]);
            c.drawLine(xx,yy,xx+sz*drift*3,yy+sz,air);
        }
        air.setStyle(Paint.Style.FILL);
    }
    private void drawWind(Canvas c,float w,float h,float t,float amount,boolean saver) {
        air.setStyle(Paint.Style.STROKE);
        air.setStrokeWidth(Math.max(1f,w*.0013f));
        int n=saver?3:7;
        for(int i=0;i<n;i++) {
            float yy=fract(.2f+i*.13f+t*.002f)*h;
            float xx=fract(i*.21f+t*.12f)*w;
            path.reset();
            path.moveTo(xx-150,yy);
            path.cubicTo(xx-70,yy-20,xx+20,yy+20,xx+170,yy-9);
            air.setColor(Color.argb((int)(amount*26),224,199,163));
            c.drawPath(path,air);
        }
        air.setStyle(Paint.Style.FILL);
    }
    /** Bioluminescence au fond du décor Annunaki : colonnes d'énergie, balises
     *  lointaines et halos pulsants. Tout est dessiné DERRIÈRE l'avatar 3D.
     */
    private void drawAncientTempleEnergy(Canvas c,float w,float h,float t,float power,boolean saver){
        air.setShader(null);
        air.setStyle(Paint.Style.STROKE);
        air.setStrokeCap(Paint.Cap.ROUND);
        int count=saver?2:4;
        for(int i=0;i<count;i++){
            float x0=w*(.11f+i*.26f);
            float baseY=h*(.46f+.045f*(i%2));
            float peakY=h*(.16f+.06f*(i%3));
            float flicker=.45f+.55f*(float)Math.pow(Math.sin(t*.67f+i*1.7f),2);
            int alpha=(int)(power*(saver?28:47)*flicker);
            air.setStrokeWidth(Math.max(1f,w*.004f));
            air.setColor(Color.argb(alpha,96,176,251));
            c.drawLine(x0,baseY,x0,peakY,air);
            air.setStrokeWidth(Math.max(1f,w*.0014f));
            air.setColor(Color.argb(Math.min(200,alpha*2),179,220,255));
            c.drawLine(x0,baseY,x0,peakY,air);
            air.setColor(Color.argb(Math.min(160,alpha*2),97,185,244));
            air.setStyle(Paint.Style.FILL);
            c.drawCircle(x0,peakY,Math.max(1f,w*.004f)*(1f+flicker),air);
            air.setStyle(Paint.Style.STROKE);
        }
        air.setStyle(Paint.Style.FILL);
        if(!saver){
            for(int i=0;i<7;i++){
                float px=w*(.12f+.12f*i+(float)Math.sin(t*.27f+i)*.015f);
                float py=h*(.34f+.07f*(i%3)+(float)Math.cos(t*.31f+i)*.027f);
                air.setColor(Color.argb((int)(power*29*(.4f+.6f*Math.abs(Math.sin(t*.43f+i)))),
                    176,206,253));
                c.drawCircle(px,py,Math.max(1f,w*.002f),air);
            }
        }
    }
    private void drawMovingObjects(Canvas c,float w,float h,float t,int scene,float amount) {
        air.setShader(null);
        if(scene==0||scene==1||scene==6) {
            // A slowly crossing distant craft, behind the presenter.
            for(int i=0;i<2;i++) {
                float xx=fract(.74f-i*.45f-t*(.007f+i*.002f))*w;
                float yy=(.16f+.16f*i+(float)Math.sin(t*.35f+i)*.018f)*h;
                float rw=w*(i==0?.025f:.014f);
                air.setColor(Color.argb((int)(amount*103),35,45,81));
                reusableOval.set(xx-rw*1.8f,yy-rw*.22f,xx+rw*1.8f,yy+rw*.22f);
                c.drawOval(reusableOval,air);
                air.setColor(Color.argb((int)(amount*133),141,218,244));
                c.drawCircle(xx,yy,rw*.27f,air);
            }
        } else {
            // Flying debris and natural haze above the temple.
            for(int i=0;i<4;i++) {
                float xx=fract(x[i+105]+t*.003f*speed[i])*w;
                float yy=fract(y[i+105]-t*.0016f)*h;
                air.setColor(Color.argb((int)(amount*24),231,191,148));
                c.drawCircle(xx,yy,Math.max(2f,w*.004f*size[i]),air);
            }
        }
    }
    private void drawLighting(Canvas c,float w,float h,float t,int scene,int mode,float amount) {
        // Light pulse reflected in the foreground, e.g. illumination from screens.
        float strength=(float)(.5f+.5f*Math.sin(t*(scene==0?.9f:.2f)));
        int glow=(int)(amount*(mode==5?40:16)*strength);
        if(glow==0)return;
        if(cachedGlow==null||glowScene!=scene||glowW!=w||glowH!=h) {
            cachedGlow=new RadialGradient(scene==2?w*.23f:w*.81f,h*.26f,Math.max(w,h)*.56f,
                new int[]{Color.rgb(scene==2?246:88,scene==2?176:153,scene==2?83:248),Color.TRANSPARENT},
                null,Shader.TileMode.CLAMP);
            glowScene=scene;glowW=w;glowH=h;
        }
        air.setShader(cachedGlow);air.setAlpha(glow);
        c.drawRect(0,0,w,h,air);air.setShader(null);air.setAlpha(255);
    }
    private void drawLightning(Canvas c,float w,float h,float t,int scene,float amount) {
        float cycle=(t+scene*2.8f)%12.8f;
        if(cycle>.22f)return;
        float x0=w*(scene==2?.68f:.79f);
        float y0=h*.04f;
        float bold=(1f-cycle/.22f)*amount;
        path.reset();path.moveTo(x0,y0);
        path.lineTo(x0-w*.042f,y0+h*.12f);
        path.lineTo(x0+w*.019f,y0+h*.20f);
        path.lineTo(x0-w*.063f,y0+h*.34f);
        path.lineTo(x0-w*.09f,y0+h*.43f);
        air.setShader(null);air.setStyle(Paint.Style.STROKE);
        air.setStrokeCap(Paint.Cap.ROUND);air.setStrokeJoin(Paint.Join.ROUND);
        air.setStrokeWidth(Math.max(2f,w*.008f));
        air.setColor(Color.argb((int)(bold*70),98,161,250));c.drawPath(path,air);
        air.setStrokeWidth(Math.max(1f,w*.002f));
        air.setColor(Color.argb((int)(bold*193),231,243,255));c.drawPath(path,air);
        air.setStyle(Paint.Style.FILL);
    }
    private static float fract(float a) { return a-(float)Math.floor(a); }
}
