package fr.chk.alienlive;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.view.View;

/**
 * Local layered video-stage prototype. Background scenery and atmospheric
 * animation are rendered on-device; portraits remain non-rigged placeholders.
 */
public final class AlienStageView extends View {
    private Bitmap characterBitmap, sceneBitmap;
    private final PortraitAnimator portraitAnimator=new PortraitAnimator();
    private volatile float mouthOpen=0f, headYaw=0f, headPitch=0f, eyeLeft=0f, eyeRight=0f;
    private float targetMouth=0f;
    private volatile float voiceMouth=0f;
    private volatile long lastFaceAt=0L;
    private boolean animateMouth=true, animateEyes=true, followHead=true;
    private float mouthSensitivity=1.7f;
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Paint labelPaint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final AtmosphereRenderer atmosphere;
    private volatile float headX=.5f,headY=.5f,headRoll=0f;
    private volatile boolean faceFound=false;
    private boolean clean=false,animate=true,saveBattery=true,show3D=false;
    private int selectedScene=0, selectedMode=0,intensity=65;
    private long lastReload=0;

    public AlienStageView(Context context,AttributeSet attrs) {
        super(context,attrs);
        setLayerType(View.LAYER_TYPE_HARDWARE,null);
        atmosphere=new AtmosphereRenderer(getResources());
        reload();
    }
    public void reload() {
        int scene=AlienData.scene(getContext());
        int character=AlienData.character(getContext());
        if(sceneBitmap!=null)sceneBitmap.recycle();
        if(characterBitmap!=null)characterBitmap.recycle();
        sceneBitmap=scene==AlienData.CUSTOM_SCENE&&AlienData.file(getContext(),true).exists()
            ?BitmapFactory.decodeFile(AlienData.file(getContext(),true).getPath())
            :BitmapFactory.decodeResource(getResources(),AlienData.BACKGROUNDS[scene]);
        characterBitmap=character==AlienData.CUSTOM_CHARACTER&&AlienData.file(getContext(),false).exists()
            ?BitmapFactory.decodeFile(AlienData.file(getContext(),false).getPath())
            :BitmapFactory.decodeResource(getResources(),AlienData.PORTRAITS[character]);
        selectedScene=scene;
        selectedMode=AlienData.prefs(getContext()).getInt("weatherMode",0);
        intensity=AlienData.prefs(getContext()).getInt("weatherIntensity",65);
        animate=AlienData.prefs(getContext()).getBoolean("animatedWeather",true);
        saveBattery=AlienData.prefs(getContext()).getBoolean("saveBattery",false);
        animateMouth=AlienData.prefs(getContext()).getBoolean("animateMouth",true);
        animateEyes=AlienData.prefs(getContext()).getBoolean("animateEyes",true);
        followHead=AlienData.prefs(getContext()).getBoolean("followHead",true);
        mouthSensitivity=AlienData.prefs(getContext()).getInt("mouthSensitivity",75)/50f;
        lastReload=SystemClock.uptimeMillis();
        atmosphere.reset();
        invalidate();
    }
    public void setCleanMode(boolean enable) { clean=enable;invalidate(); }
    public void set3DMode(boolean enable){show3D=enable;postInvalidateOnAnimation();}
    public void updatePose(float x,float y,float roll,float mouth) {
        float gain=Math.max(.2f,Math.min(3f,mouthSensitivity));
        headX+=(x-headX)*.34f;headY+=(y-headY)*.32f;headRoll+=(roll-headRoll)*.30f;
        targetMouth=Math.max(0f,Math.min(1f,mouth*gain));
        // Fermeture plus rapide que l'ancien lissage a 26% : reduit le retard visible.
        float speed=targetMouth>mouthOpen?.68f:.56f;
        mouthOpen+=(targetMouth-mouthOpen)*speed;
        lastFaceAt=SystemClock.uptimeMillis();
        faceFound=true;
        postInvalidateOnAnimation();
    }
    public void updateExpression(float yaw,float pitch,float blinkLeft,float blinkRight){
        headYaw+=(yaw-headYaw)*.35f;headPitch+=(pitch-headPitch)*.35f;
        eyeLeft+=(blinkLeft-eyeLeft)*.42f;eyeRight+=(blinkRight-eyeRight)*.42f;
        postInvalidateOnAnimation();
    }
    public float getMouthLevel(){return mouthOpen;}
    public void setVoiceMouth(float value){voiceMouth=Math.max(0f,Math.min(1f,value));if(voiceMouth>0.01f)postInvalidateOnAnimation();}
    @Override protected void onDraw(Canvas c) {
        super.onDraw(c);
        float w=getWidth(),h=getHeight();
        if(w<=0||h<=0)return;
        c.drawColor(Color.rgb(7,7,22));
        if(sceneBitmap!=null)drawCover(c,sceneBitmap,w,h);
        if(animate)atmosphere.renderBehind(c,w,h,selectedScene,selectedMode,intensity,headX,headY,saveBattery);
        if(characterBitmap!=null&&!show3D) {
            long time=SystemClock.uptimeMillis();
            boolean freshFace=faceFound&&(time-lastFaceAt)<1700L;
            if(!freshFace)mouthOpen*=.80f;
            float breathing=(float)Math.sin(time*.00145f);
            float positionX=followHead&&freshFace?(headX-.5f):0f;
            float positionY=followHead&&freshFace?(headY-.5f):0f;
            float cx=w*.50f+positionX*w*.48f+breathing*w*.003f;
            float cy=h*.52f+positionY*h*.29f+breathing*h*.004f;
            float portraitW=w*.86f;
            float portraitH=portraitW*characterBitmap.getHeight()/characterBitmap.getWidth();
            float turn=freshFace&&followHead?Math.max(-1f,Math.min(1f,headYaw)):0f;
            float tilt=freshFace&&followHead?Math.max(-1f,Math.min(1f,headPitch)):0f;
            c.save();
            c.rotate((freshFace&&followHead?headRoll*.85f:0f)+breathing*.30f,cx,cy);
            float sizeX=1f-Math.abs(turn)*.055f;
            c.scale(sizeX,1f,cx,cy);
            paint.setAlpha(255);
            RectF portrait=new RectF(cx-portraitW/2f,cy-portraitH/2f,cx+portraitW/2f,cy+portraitH/2f);
            c.translate(portrait.left,portrait.top);
            c.scale(portrait.width()/characterBitmap.getWidth(),
                    portrait.height()/characterBitmap.getHeight());
            // Le maillage transforme la texture originale plutot qu'un point noir dessine.
            portraitAnimator.draw(c,characterBitmap,AlienData.character(getContext()),
                Math.max(freshFace?mouthOpen:0f,voiceMouth*.70f),turn,tilt,
                animateEyes&&freshFace?eyeLeft:0f,
                animateEyes&&freshFace?eyeRight:0f,
                animateMouth||animateEyes||followHead);
            c.restore();
        }
        if(animate)atmosphere.renderFront(c,w,h,selectedScene,selectedMode,intensity,saveBattery);
        if(!clean) {
            labelPaint.setStyle(Paint.Style.FILL);
            labelPaint.setColor(0xA0000016);
            c.drawRoundRect(new RectF(w*.07f,h-97,w*.93f,h-19),18,18,labelPaint);
            labelPaint.setTypeface(android.graphics.Typeface.create("sans-serif-medium",1));
            labelPaint.setTextAlign(Paint.Align.CENTER);
            labelPaint.setTextSize(14f*getResources().getDisplayMetrics().scaledDensity);
            labelPaint.setColor(0xFFF2E9FF);
            c.drawText((AlienData.character(getContext())==AlienData.CUSTOM_CHARACTER&&GlbCollection.selected(getContext())!=null?GlbCollection.activeName(getContext()):AlienData.NAMES[AlienData.character(getContext())]),w/2f,h-67,labelPaint);
            labelPaint.setTextSize(11f*getResources().getDisplayMetrics().scaledDensity);
            labelPaint.setColor(0xFFE4CCE8);
            c.drawText(animate?"Atmosphère : "+atmosphere.currentWeather(selectedMode,selectedScene):"Atmosphère désactivée",w/2f,h-40,labelPaint);
        }
        if(animate && isShown() && getWindowVisibility()==VISIBLE) postInvalidateDelayed(saveBattery?50L:33L);
    }
    @Override protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        // No persistent render loop once the Activity is no longer visible.
    }
    private void drawCover(Canvas c,Bitmap b,float w,float h){
        // Cadre et proportions identiques à la version stable.
        float scale=Math.max(w/b.getWidth(),h/b.getHeight());
        float sw=b.getWidth()*scale,sh=b.getHeight()*scale;
        float sway=(float)Math.sin(SystemClock.uptimeMillis()*.00013f)*w*.012f+(headX-.5f)*w*.018f;
        paint.setAlpha(255);
        c.drawBitmap(b,null,new RectF((w-sw)/2f+sway,(h-sh)/2f,(w+sw)/2f+sway,(h+sh)/2f),paint);
    }
}
