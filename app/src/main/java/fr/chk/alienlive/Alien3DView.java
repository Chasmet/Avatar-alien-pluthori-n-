package fr.chk.alienlive;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.PixelFormat;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.opengl.GLUtils;
import android.opengl.Matrix;
import android.util.Log;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.util.Arrays;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.ShortBuffer;
import java.util.ArrayList;
import java.util.List;
import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

/** Lightweight local glTF 2.0 GLB viewer for the model shipped with the APK.
 * OpenGL ES 2.0, API 21+, no account, server or runtime downloads.
 * Uses real mesh rotation and the GLB's separately modeled jaw; this is not
 * full facial rigging or phoneme-level lip sync.
 */
public final class Alien3DView extends GLSurfaceView {
    public interface Status { void onReady(); void onError(String message); }
    private final StageRenderer renderer;
    private volatile float yaw=0f,pitch=0f,roll=0f,mouth=0f,blinkLeft=0f,blinkRight=0f,x=.5f,y=.5f;
    private volatile float smileLeft,smileRight,lipSlide,cheekLeft, cheekRight, rounded;
    private volatile long expressionTime;
    public void setCheeksAndLips(float left,float right,float pucker){
        cheekLeft=left; cheekRight=right; rounded=pucker;
    }
    private volatile boolean reverseVertical=false;
    private volatile FaceCalibration faceCalibration=null;
    private volatile float calibrationZoom=1f,calibrationPanX=0f,calibrationPanY=0f;
    private volatile boolean calibrationViewActive=false;
    private volatile boolean saver=false;
    private volatile boolean eyesEnabled=true;
    private volatile boolean naturalModel=true;
    // Charge d'abord le véritable modèle historique, non sa reconstruction basse résolution.
    private volatile String activeAsset="pluthorien_alpha.glb";
    private volatile File activeCustomFile=null;
    private volatile boolean reloadModel=false;
    private boolean framesActive=false;
    private final Runnable frameTicker=new Runnable(){
        @Override public void run(){
            if(!framesActive)return;
            if(isShown())requestRender();
            postDelayed(this,saver?50L:33L);
        }
    };
    private volatile boolean useHead=true;

    public Alien3DView(Context context,Status status){this(context,status,true);}
    public Alien3DView(Context context, Status status,boolean aboveWindow){
        super(context);
        setEGLContextClientVersion(2);
        setEGLConfigChooser(8,8,8,8,16,0);
        getHolder().setFormat(PixelFormat.TRANSLUCENT);
        setZOrderOnTop(aboveWindow);
        if(!aboveWindow)setZOrderMediaOverlay(false);
        renderer=new StageRenderer(context,status);
        setRenderer(renderer);
        setRenderMode(RENDERMODE_WHEN_DIRTY);
        setClickable(true);
    }
    public void setPose(float x,float y,float roll,float mouth){
        this.x=x;this.y=y;this.roll=roll;this.mouth=mouth;
    }
    public void setExpression(float yaw,float pitch,float leftBlink,float rightBlink){
        this.yaw=yaw;this.pitch=pitch;
        expressionTime=android.os.SystemClock.uptimeMillis();
        this.blinkLeft=leftBlink;this.blinkRight=rightBlink;
    }
    private volatile float gazeX,gazeY;
    public void setGaze(float x,float y){gazeX=x;gazeY=y;}
    private volatile float spokenRound,spokenWide;
    public void setSpokenShape(float round,float wide){
        spokenRound=round;spokenWide=wide;
    }
    public void setLipCorners(float left,float right,float slide){
        smileLeft=left;smileRight=right;lipSlide=slide;
    }
    public void clearTracking(){
        setPose(.5f,.5f,0f,0f);
        yaw=pitch=blinkLeft=blinkRight=0f;
        setLipCorners(0f,0f,0f);
        setCheeksAndLips(0f,0f,0f);
        setGaze(0f,0f);
        expressionTime=-450L;
    }
    public void setReverseVertical(boolean enabled){reverseVertical=enabled;}
    public void setFaceCalibration(FaceCalibration cal){faceCalibration=cal;requestRender();}
    /** Partage le même zoom/panoramique que FacePointsOverlay pendant la calibration.
     * Ce réglage n'agit pas sur la taille des GLB en LIVE.
     */
    public void setCalibrationViewport(float zoom,float offsetX,float offsetY){
        calibrationViewActive=true;
        calibrationZoom=Math.max(1f,Math.min(5f,zoom));
        calibrationPanX=Math.max(-1.5f,Math.min(1.5f,offsetX));
        calibrationPanY=Math.max(-1.5f,Math.min(1.5f,offsetY));
        requestRender();
    }
    public void refreshFaceRig(){if(activeCustomFile!=null){reloadModel=true;requestRender();}}
    public void setBatterySaver(boolean enable){saver=enable;}
    public void setEyeAnimations(boolean enabled){eyesEnabled=enabled;}
    public void setNaturalModel(boolean enable){naturalModel=enable;}
    public void setCharacter(int index,boolean originalAlpha){
        if(index<0||index>=AlienData.MODEL_FILES.length)return;
        String asset=index==0&&originalAlpha?"pluthorien_alpha.glb":AlienData.MODEL_FILES[index];
        boolean wasCustom=activeCustomFile!=null;
        activeCustomFile=null;
        if(wasCustom||!asset.equals(activeAsset)){
            activeAsset=asset;
            reloadModel=true;
            requestRender();
        }else if(renderer.loaded){
            // Après un retour du mode portrait, restaurer la vue 3D déjà chargée.
            post(()->renderer.callback.onReady());
        }
    }
    /** Affiche un GLB local importé par l'utilisateur, sans remplacer les avatars intégrés. */
    public void setCustomModel(File source){
        if(source==null||!source.isFile())return;
        File previous=activeCustomFile;
        if(previous!=null&&previous.equals(source)&&renderer.loaded)return;
        activeCustomFile=source;
        reloadModel=true;
        requestRender();
    }
    @Override public void onResume(){
        super.onResume();framesActive=true;
        removeCallbacks(frameTicker);post(frameTicker);
    }
    @Override public void onPause(){
        framesActive=false;removeCallbacks(frameTicker);
        super.onPause();
    }
    public void setHeadTracking(boolean enable){useHead=enable;}

    private final class StageRenderer implements GLSurfaceView.Renderer {
        private final Context context;
        private final Status callback;
        private final float[] projection=new float[16],view=new float[16],root=new float[16],tmp=new float[16],mvp=new float[16],local=new float[16],vm=new float[16];
        private final float[] le=new float[2],re=new float[2],mo=new float[2];
        private final List<Mesh> meshes=new ArrayList<>();
        private int program,positionLoc,uvLoc,mvpLoc,baseLoc,texLoc,useTexLoc,lightLoc,lipLoc;
        private int mouthMorphLoc,blinkMorphLoc,mouthMorphWeightLoc,blinkMorphWeightLoc;
        private int smileLoc;
        private int eyePointsLoc,mouthPointLoc;
        private int importedLoc,faceLoc,gazeLoc,breathLoc,headMotionLoc;
        private int textureId;
        private float centerX=0f,centerY=0f,centerZ=0f,customScale=1f;
        private float faceMinY=0f,faceWidth=1f,faceHeight=1f;
        private volatile boolean loaded;
        private float easedYaw,easedPitch,easedRoll,easedJaw,easedBlinkL,easedBlinkR,easedSmileL,easedSmileR,easedSlide,easedX=.5f,easedY=.5f;
        private int detailLoc, surfaceLoc, frontZLoc;
        private final String VERTEX="""
            attribute vec3 aPos;
            attribute vec2 aUv;
            attribute vec3 aMouthMorph;
            attribute vec3 aBlinkMorph;
            uniform mat4 uMvp;
            uniform float uLip, uMouthWeight, uImported, uBreath, uFrontZ;
            uniform vec2 uBlinkWeight;
            uniform vec3 uCorners;
            uniform vec2 uGaze, uHeadMotion, uMouthPoint;
            uniform vec4 uFace, uEyePoints, uDetail, uSurface;
            varying vec2 vUv;
            varying float vLight;
            float region(vec2 d,vec2 radius) {
                vec2 t=d/radius;
                return exp(-dot(t,t));
            }
            void main(){
                vec2 scale=max(uFace.zw,vec2(.001));
                vec2 uv=(aPos.xy-uFace.xy)/scale+vec2(.5,0.);
                uv.y+=uSurface.z;
                float side=smoothstep(-.025,.025,uv.x-.5);
                float eyeBlink=mix(uBlinkWeight.y,uBlinkWeight.x,side);
                vec3 pos=aPos+aMouthMorph*uMouthWeight+aBlinkMorph*eyeBlink;
                vec2 lip=uv-uMouthPoint;
                float smile=mix(uCorners.y,uCorners.x,smoothstep(-.045,.045,lip.x));
                float lipMask=region(lip,vec2(.17,.060));
                float front=uSurface.x;
                if(uImported>.5)
                    front*=smoothstep(uFrontZ-.02*scale.x,uFrontZ+.05*scale.x,aPos.z);
                float leftWeight=region(uv-mix(uEyePoints.zw,uMouthPoint,.48)-vec2(.055,0.),vec2(.10,.080));
                float rightWeight=region(uv-mix(uEyePoints.xy,uMouthPoint,.48)+vec2(.055,0.),vec2(.10,.080));
                float cheek=uDetail.x*leftWeight+uDetail.y*rightWeight;
                pos.y+=front*scale.y*(.025*cheek+.010*smile*lipMask);
                pos.z+=front*scale.x*(.012*cheek+.026*uDetail.z*lipMask);
                pos.x+=front*scale.x*lip.x*lipMask*(.18*smile-.43*uDetail.z+.34*uDetail.w*(1.-uDetail.z));
                pos.x+=front*scale.x*.038*uCorners.z*lipMask;
                vec2 dl=uv-uEyePoints.xy, dr=uv-uEyePoints.zw;
                float le=region(dl,vec2(.095,.036)), re=region(dr,vec2(.095,.036));
                pos.y-=front*uSurface.y*scale.y*(dl.y*le*uBlinkWeight.y+dr.y*re*uBlinkWeight.x)*1.55;
                pos.xy+=front*uGaze*(le*(1.-uBlinkWeight.y)+re*(1.-uBlinkWeight.x))*scale*.012;
                if(uImported>.5){
                    pos.y+=uBreath*max(0.0,.35-uv.y)*scale.y*.012;
                    float neck=smoothstep(.39,.53,uv.y);
                    vec2 angle=uHeadMotion*neck;
                    vec3 origin=vec3(uFace.x,uFace.y+.40*scale.y,0.0);
                    vec3 v=pos-origin;
                    float c=cos(angle.x),sn=sin(angle.x);
                    v=vec3(v.x*c+v.z*sn,v.y,-v.x*sn+v.z*c);
                    c=cos(angle.y);sn=sin(angle.y);
                    pos=origin+vec3(v.x,v.y*c-v.z*sn,v.y*sn+v.z*c);
                }else{
                    float upperLip=region(pos.xy-vec2(0.,-.30),vec2(.177,.078));
                    pos.y+=uLip*.022*upperLip;
                    pos.z+=uLip*.009*upperLip;
                }
                vUv=aUv;
                vLight=.86+.14*max(0.,pos.z);
                gl_Position=uMvp*vec4(pos,1.);
            }
            """;
        private final String FRAGMENT="precision mediump float;varying vec2 vUv;varying float vLight;uniform sampler2D uTexture;uniform vec4 uBase;uniform float uUseTexture;void main(){vec4 c=uBase;if(uUseTexture>0.5)c*=texture2D(uTexture,vUv);gl_FragColor=vec4(c.rgb*vLight,c.a);}";
        StageRenderer(Context ctx,Status status){context=ctx.getApplicationContext();callback=status;}
        @Override public void onSurfaceCreated(GL10 ignored,EGLConfig cfg){
            GLES20.glClearColor(0f,0f,0f,0f);
            GLES20.glEnable(GLES20.GL_DEPTH_TEST);
            GLES20.glDisable(GLES20.GL_CULL_FACE);
            GLES20.glEnable(GLES20.GL_BLEND);
            GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA,GLES20.GL_ONE_MINUS_SRC_ALPHA);
            try{
                program=link(VERTEX,FRAGMENT);
                positionLoc=GLES20.glGetAttribLocation(program,"aPos");
                uvLoc=GLES20.glGetAttribLocation(program,"aUv");
                mvpLoc=GLES20.glGetUniformLocation(program,"uMvp");
                baseLoc=GLES20.glGetUniformLocation(program,"uBase");
                texLoc=GLES20.glGetUniformLocation(program,"uTexture");
                useTexLoc=GLES20.glGetUniformLocation(program,"uUseTexture");
                lipLoc=GLES20.glGetUniformLocation(program,"uLip");
                importedLoc=GLES20.glGetUniformLocation(program,"uImported");
                faceLoc=GLES20.glGetUniformLocation(program,"uFace");
                gazeLoc=GLES20.glGetUniformLocation(program,"uGaze");
                breathLoc=GLES20.glGetUniformLocation(program,"uBreath");
                headMotionLoc=GLES20.glGetUniformLocation(program,"uHeadMotion");
                mouthMorphLoc=GLES20.glGetAttribLocation(program,"aMouthMorph");
                blinkMorphLoc=GLES20.glGetAttribLocation(program,"aBlinkMorph");
                mouthMorphWeightLoc=GLES20.glGetUniformLocation(program,"uMouthWeight");
                blinkMorphWeightLoc=GLES20.glGetUniformLocation(program,"uBlinkWeight");
                detailLoc=GLES20.glGetUniformLocation(program,"uDetail");
                surfaceLoc=GLES20.glGetUniformLocation(program,"uSurface");
                frontZLoc=GLES20.glGetUniformLocation(program,"uFrontZ");
                smileLoc=GLES20.glGetUniformLocation(program,"uCorners");
                eyePointsLoc=GLES20.glGetUniformLocation(program,"uEyePoints");
                mouthPointLoc=GLES20.glGetUniformLocation(program,"uMouthPoint");
                lightLoc=0;
                reloadModel=false;
                readModel();
                loaded=!meshes.isEmpty();
                if(loaded)post(()->callback.onReady());
                else post(()->callback.onError("GLB sans géométrie"));
            }catch(Exception e){
                loaded=false;Log.e("CHK-GLB","Impossible de charger le modèle",e);
                post(()->callback.onError("3D indisponible : "+e.getClass().getSimpleName()));
            }
        }
        @Override public void onSurfaceChanged(GL10 gl,int width,int height){
            GLES20.glViewport(0,0,width,height);
            float ratio=Math.max(.1f,width/(float)Math.max(1,height));
            Matrix.perspectiveM(projection,0,48f,ratio,.1f,80f);
        }
        @Override public void onDrawFrame(GL10 gl){
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT|GLES20.GL_DEPTH_BUFFER_BIT);
            if(reloadModel){
                reloadModel=false;
                try{
                    readModel();
                    loaded=!meshes.isEmpty();
                    if(loaded)post(()->callback.onReady());
                    else post(()->callback.onError("Modèle 3D vide"));
                }catch(Exception error){
                    loaded=false;
                    Log.e("CHK-GLB","Chargement de "+activeAsset,error);
                    post(()->callback.onError("Modèle 3D indisponible"));
                }
            }
            if(!loaded)return;
            FaceCalibration cal=faceCalibration;
            float blend=cal==null?.16f:cal.smoothingFactor();
            easedYaw+=(yaw-easedYaw)*blend;
            easedPitch+=(pitch-easedPitch)*blend;
            easedRoll+=(roll-easedRoll)*blend;
            easedJaw+=(mouth-easedJaw)*(mouth>easedJaw?.66f:.46f);
            float t=android.os.SystemClock.uptimeMillis()*.001f;
            boolean tracking=android.os.SystemClock.uptimeMillis()-expressionTime<450L;
            float blinkPulse=!tracking && (t%4.9f)>4.71f?(float)Math.sin(((t%4.9f)-4.71f)/.19f*3.1415927f):0f;
            easedBlinkL+=((eyesEnabled?(tracking?blinkLeft:blinkPulse*.93f):0f)-easedBlinkL)*.52f;
            easedBlinkR+=((eyesEnabled?(tracking?blinkRight:blinkPulse*.93f):0f)-easedBlinkR)*.52f;
            easedSmileL+=((tracking?smileLeft:0f)-easedSmileL)*.40f;
            easedSmileR+=((tracking?smileRight:0f)-easedSmileR)*.40f;
            easedSlide+=((tracking?lipSlide:0f)-easedSlide)*.50f;
            easedX+=(x-easedX)*blend;
            easedY+=(y-easedY)*blend;
            Matrix.setLookAtM(view,0,0f,0f,3.7f,0f,-.13f,0f,0f,1f,0f);
            Matrix.setIdentityM(root,0);
            // Calibrage : déplacer/zoomer les vrais sommets au même rythme que les
            // repères à l'écran. FOV verticale 48° et caméra à 3,7 unités.
            if(calibrationZoom>1f||calibrationPanX!=0f||calibrationPanY!=0f){
                float worldHeight=3.7f*2f*.4452287f;
                float aspect=Math.max(.1f,getWidth()/(float)Math.max(1,getHeight()));
                Matrix.translateM(root,0,calibrationPanX*worldHeight*aspect,
                     -calibrationPanY*worldHeight,0f);
                Matrix.scaleM(root,0,calibrationZoom,calibrationZoom,calibrationZoom);
            }
            boolean imported=activeCustomFile!=null;
            float breath=calibrationViewActive?0f:(float)Math.sin(t*1.7f);
            Matrix.translateM(root,0,(easedX-.5f)*.34f,(.5f-easedY)*.23f+(imported?breath*.008f:0f),0f);
            if(imported&&!calibrationViewActive&&!tracking){
                Matrix.rotateM(root,0,(float)Math.sin(t*.38f)*1.65f,0f,0f,1f);
                Matrix.rotateM(root,0,(float)Math.sin(t*.26f)*1.4f,0f,1f,0f);
            }
            float vertical=reverseVertical?-easedPitch:easedPitch;
            if(useHead){
                Matrix.rotateM(root,0,-easedRoll*(imported?.20f:.75f),0f,0f,1f);
                if(!imported){
                    Matrix.rotateM(root,0,-easedYaw*27f,0f,1f,0f);
                    Matrix.rotateM(root,0,-vertical*20f,1f,0f,0f);
                }
            }
            GLES20.glUseProgram(program);
            GLES20.glUniform1f(importedLoc,imported?1f:0f);
            boolean classicAlpha="pluthorien_alpha.glb".equals(activeAsset);
            float basisW=imported?faceWidth:classicAlpha?.90f:1.46f;
            float basisH=imported?faceHeight:1.50f;
            GLES20.glUniform4f(faceLoc,imported?centerX:0f,imported?faceMinY:-.64f,basisW,basisH);
            GLES20.glUniform1f(frontZLoc,centerZ);
            GLES20.glUniform4f(detailLoc,tracking?cheekLeft:0f,tracking?cheekRight:0f,Math.max(tracking?rounded:0f,spokenRound),spokenWide);
            if(cal!=null){
                cal.fillEffectivePoint(0,le);cal.fillEffectivePoint(1,re);cal.fillEffectivePoint(2,mo);
            }else{
                le[0]=.375f;re[0]=.625f;le[1]=re[1]=.705f;mo[0]=.5f;mo[1]=.54f;
            }
            float eyeOffset=imported?0f:.055f;
            float mouthOffset=imported?0f:-.31f;
            GLES20.glUniform4f(eyePointsLoc,le[0]-eyeOffset,le[1],re[0]+eyeOffset,re[1]);
            GLES20.glUniform2f(mouthPointLoc,mo[0],mo[1]+mouthOffset);
            float gazeGain=eyesEnabled?(cal==null?1f:cal.gazeRange/55f):0f;
            GLES20.glUniform2f(gazeLoc,gazeGain*(tracking?gazeX:(float)Math.sin(t*.67f)*.15f),
                gazeGain*(tracking?gazeY:(float)Math.sin(t*.49f)*.09f));
            GLES20.glUniform1f(breathLoc,breath);
            GLES20.glUniform2f(headMotionLoc,imported&&useHead
                ?(-easedYaw*.40f+(float)Math.sin(t*.24f)*.018f):0f,
                imported&&useHead?(-vertical*.28f+(float)Math.sin(t*.19f)*.013f):0f);
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D,textureId);
            GLES20.glUniform1i(texLoc,0);
            for(Mesh mesh:meshes){
                boolean classic=!imported&&"pluthorien_alpha.glb".equals(activeAsset);
                // Tous les volumes d'origine sont préservés dans le mode fidèle.
                if(!imported&&!classic&&naturalModel&&!mesh.textured&&!mesh.jaw&&!mesh.volume)continue;
                Matrix.setIdentityM(local,0);
                if(mesh.jaw){
                    if(classic){
                        // Pivot ET amplitude exacts du modèle Alpha de la v0.6.9.
                        Matrix.translateM(local,0,0f,-.3166667f,0f);
                        Matrix.rotateM(local,0,-easedJaw*23f,1f,0f,0f);
                    }else{
                        Matrix.translateM(local,0,0f,-.300f-easedJaw*.018f,0f);
                        Matrix.rotateM(local,0,-easedJaw*21f,1f,0f,0f);
                        Matrix.translateM(local,0,0f,.300f,0f);
                    }
                }
                if(imported){
                    // Centre la géométrie réelle du GLB, sans l'étirer ou l'aplatir.
                    Matrix.translateM(local,0,-centerX*customScale,-centerY*customScale,-centerZ*customScale);
                    Matrix.scaleM(local,0,customScale,customScale,customScale);
                }
                Matrix.multiplyMM(tmp,0,root,0,local,0);
                Matrix.multiplyMM(vm,0,view,0,tmp,0);
                Matrix.multiplyMM(mvp,0,projection,0,vm,0);
                GLES20.glUniformMatrix4fv(mvpLoc,1,false,mvp,0);
                GLES20.glUniform4fv(baseLoc,1,mesh.color,0);
                GLES20.glUniform1f(useTexLoc,mesh.textured?1f:0f);
                // Déformation locale de la lèvre haute sur les sommets texturés.
                GLES20.glUniform1f(lipLoc,!imported&&!classic&&mesh.textured&&!mesh.jaw?easedJaw*.35f:0f);
                GLES20.glUniform1f(mouthMorphWeightLoc,mesh.openMorph!=null?easedJaw:0f);
                GLES20.glUniform2f(blinkMorphWeightLoc,easedBlinkL,
                    easedBlinkR);
                boolean facialSurface=imported||mesh.textured;
                GLES20.glUniform4f(surfaceLoc,facialSurface?1f:0f,mesh.blinkMorph==null?1f:0f,
                    classic&&mesh.jaw?-.3166667f/basisH:0f,0f);
                GLES20.glUniform3f(smileLoc,easedSmileL,easedSmileR,easedSlide);
                if(mesh.openMorph!=null){
                    mesh.openMorph.position(0);
                    GLES20.glEnableVertexAttribArray(mouthMorphLoc);
                    GLES20.glVertexAttribPointer(mouthMorphLoc,3,GLES20.GL_FLOAT,false,0,mesh.openMorph);
                }else{
                    GLES20.glDisableVertexAttribArray(mouthMorphLoc);
                    GLES20.glVertexAttrib3f(mouthMorphLoc,0f,0f,0f);
                }
                if(mesh.blinkMorph!=null){
                    mesh.blinkMorph.position(0);
                    GLES20.glEnableVertexAttribArray(blinkMorphLoc);
                    GLES20.glVertexAttribPointer(blinkMorphLoc,3,GLES20.GL_FLOAT,false,0,mesh.blinkMorph);
                }else{
                    GLES20.glDisableVertexAttribArray(blinkMorphLoc);
                    GLES20.glVertexAttrib3f(blinkMorphLoc,0f,0f,0f);
                }
                mesh.positions.position(0);
                GLES20.glEnableVertexAttribArray(positionLoc);
                GLES20.glVertexAttribPointer(positionLoc,3,GLES20.GL_FLOAT,false,0,mesh.positions);
                if(mesh.uvs!=null){
                    mesh.uvs.position(0);
                    GLES20.glEnableVertexAttribArray(uvLoc);
                    GLES20.glVertexAttribPointer(uvLoc,2,GLES20.GL_FLOAT,false,0,mesh.uvs);
                } else {
                    GLES20.glDisableVertexAttribArray(uvLoc);
                    GLES20.glVertexAttrib2f(uvLoc,0f,0f);
                }
                mesh.indices.position(0);
                GLES20.glDrawElements(GLES20.GL_TRIANGLES,mesh.count,GLES20.GL_UNSIGNED_SHORT,mesh.indices);
            }
            GLES20.glDisableVertexAttribArray(positionLoc);
            GLES20.glDisableVertexAttribArray(uvLoc);
            GLES20.glDisableVertexAttribArray(mouthMorphLoc);
            GLES20.glDisableVertexAttribArray(blinkMorphLoc);
        }
        private void readModel() throws Exception {
            meshes.clear();
            if(activeCustomFile==null){centerX=centerY=centerZ=0f;customScale=1f;faceMinY=0f;faceWidth=faceHeight=1f;}
            if(textureId!=0){
                int[] ids={textureId};
                GLES20.glDeleteTextures(1,ids,0);
                textureId=0;
            }
            byte[] bytes;
            try(InputStream in=activeCustomFile!=null?new FileInputStream(activeCustomFile):context.getAssets().open(activeAsset)){
                ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] block=new byte[32768];int n;
                while((n=in.read(block))>=0){out.write(block,0,n);if(out.size()>65_000_000)throw new IllegalArgumentException("GLB supérieur à 65 Mo");}
                bytes=out.toByteArray();
            }
            ByteBuffer file=ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
            if(file.getInt(0)!=0x46546c67||file.getInt(4)!=2)throw new IllegalArgumentException("GLB version incorrecte");
            int jsonLength=file.getInt(12),binaryHeader=20+jsonLength;
            if(binaryHeader+8>bytes.length)throw new IllegalArgumentException("GLB incomplet");
            JSONObject gltf=new JSONObject(new String(bytes,20,jsonLength,"UTF-8"));
            int dataOffset=binaryHeader+8;
            JSONArray views=gltf.getJSONArray("bufferViews");
            JSONArray accessors=gltf.getJSONArray("accessors");
            JSONArray materials=gltf.optJSONArray("materials");
            JSONArray nodes=gltf.getJSONArray("nodes");
            JSONArray originalMeshes=gltf.getJSONArray("meshes");
            // Decode the actual photo texture embedded in the GLB.
            JSONArray images=gltf.optJSONArray("images");
            if(images!=null&&images.length()>0){
                JSONObject im=images.getJSONObject(0);
                JSONObject v=views.getJSONObject(im.getInt("bufferView"));
                int offset=dataOffset+v.optInt("byteOffset",0),length=v.getInt("byteLength");
                if(offset>=0&&length>0&&offset+length<=bytes.length){
                    BitmapFactory.Options options=new BitmapFactory.Options();
                    options.inJustDecodeBounds=true;
                    BitmapFactory.decodeByteArray(bytes,offset,length,options);
                    options.inJustDecodeBounds=false;
                    // 4096x2048 d'origine ramené à 2048x1024 pour réduire la mémoire GPU.
                    options.inSampleSize=1;
                    while(Math.max(options.outWidth,options.outHeight)/options.inSampleSize>2048)
                        options.inSampleSize*=2;
                    Bitmap bitmap=BitmapFactory.decodeByteArray(bytes,offset,length,options);
                    if(bitmap!=null){
                        int[] names=new int[1];GLES20.glGenTextures(1,names,0);textureId=names[0];
                        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D,textureId);
                        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D,GLES20.GL_TEXTURE_MIN_FILTER,GLES20.GL_LINEAR);
                        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D,GLES20.GL_TEXTURE_MAG_FILTER,GLES20.GL_LINEAR);
                        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D,GLES20.GL_TEXTURE_WRAP_S,GLES20.GL_CLAMP_TO_EDGE);
                        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D,GLES20.GL_TEXTURE_WRAP_T,GLES20.GL_CLAMP_TO_EDGE);
                        GLUtils.texImage2D(GLES20.GL_TEXTURE_2D,0,bitmap,0);
                        bitmap.recycle();
                    }
                }
            }
            if(activeCustomFile!=null)resetCustomBounds();
            for(int i=0;i<nodes.length();i++){
                JSONObject node=nodes.getJSONObject(i);
                int meshIndex=node.optInt("mesh",-1);
                if(meshIndex<0)continue;
                JSONObject glbMesh=originalMeshes.getJSONObject(meshIndex);
                String name=node.optString("name","");
                boolean jaw=name.startsWith("Jaw_");
                boolean volume=name.startsWith("Sculpt_");
                JSONArray primitives=glbMesh.getJSONArray("primitives");
                for(int j=0;j<primitives.length();j++){
                    JSONObject primitive=primitives.getJSONObject(j);
                    JSONObject attrs=primitive.getJSONObject("attributes");
                    float[] positions=readFloats(file,dataOffset,views,accessors,attrs.getInt("POSITION"),3);
                    float[] uv=attrs.has("TEXCOORD_0")?readFloats(file,dataOffset,views,accessors,attrs.getInt("TEXCOORD_0"),2):null;
                    if(primitive.optInt("mode",4)!=4)continue;
                    int vertexCount=positions.length/3;
                    int[] indices;
                    if(primitive.has("indices"))indices=readIndices(file,dataOffset,views,accessors,primitive.getInt("indices"));
                    else{
                        indices=new int[vertexCount];
                        for(int k=0;k<vertexCount;k++)indices[k]=k;
                    }
                    JSONObject material=materials==null?null:materials.optJSONObject(primitive.optInt("material",-1));
                    JSONObject pbr=material==null?null:material.optJSONObject("pbrMetallicRoughness");
                    JSONArray colors=pbr==null?null:pbr.optJSONArray("baseColorFactor");
                    float[] color=new float[]{1f,1f,1f,1f};
                    if(colors!=null)for(int k=0;k<4;k++)color[k]=(float)colors.getDouble(k);
                    boolean textured=pbr!=null&&pbr.has("baseColorTexture")&&textureId!=0&&uv!=null;
                    float[] lipDelta=null,blinkDelta=null;
                    if(activeCustomFile!=null||name.startsWith("Head_")){
                        lipDelta=GlbFaceTargets.read(glbMesh,primitive,true,file,dataOffset,views,accessors,vertexCount);
                        blinkDelta=GlbFaceTargets.read(glbMesh,primitive,false,file,dataOffset,views,accessors,vertexCount);
                    }
                    if(activeCustomFile!=null){
                        if(lipDelta==null||blinkDelta==null){
                            GlbFaceAutoRig.Face auto=GlbFaceAutoRig.fromPositions(positions,faceCalibration);
                            if(lipDelta==null)lipDelta=auto.mouth;
                            if(blinkDelta==null)blinkDelta=auto.eyes;
                        }
                        updateCustomBounds(positions);
                    }
                    for(GlbMeshChunker.Chunk chunk:
                          GlbMeshChunker.split(positions,uv,indices,lipDelta,blinkDelta)){
                        meshes.add(new Mesh(chunk.points,chunk.uv,chunk.indices,color,
                            jaw,textured,volume,chunk.lip,chunk.blink));
                    }
                }
            }
            if(meshes.isEmpty())throw new IllegalArgumentException("Aucune surface triangulaire dans le GLB");
            if(activeCustomFile!=null)finishCustomBounds();
        }
        private float minPX,maxPX,minPY,maxPY,minPZ,maxPZ;
        private void resetCustomBounds(){
            minPX=minPY=minPZ=Float.POSITIVE_INFINITY;
            maxPX=maxPY=maxPZ=Float.NEGATIVE_INFINITY;
        }
        private void updateCustomBounds(float[] p){
            for(int i=0;i<p.length;i+=3){
                minPX=Math.min(minPX,p[i]);maxPX=Math.max(maxPX,p[i]);
                minPY=Math.min(minPY,p[i+1]);maxPY=Math.max(maxPY,p[i+1]);
                minPZ=Math.min(minPZ,p[i+2]);maxPZ=Math.max(maxPZ,p[i+2]);
            }
        }
        private void finishCustomBounds(){
            centerX=(minPX+maxPX)*.5f;
            centerY=(minPY+maxPY)*.5f;
            centerZ=(minPZ+maxPZ)*.5f;
            faceMinY=minPY;
            faceWidth=Math.max(.001f,maxPX-minPX);
            faceHeight=Math.max(.001f,maxPY-minPY);
            float extent=Math.max(faceHeight,faceWidth*1.2f);
            customScale=extent>.001f?Math.min(9f,2.15f/extent):1f;
        }
        private float[] readFloats(ByteBuffer source,int base,JSONArray views,JSONArray accessors,int id,int components)throws Exception{
            JSONObject a=accessors.getJSONObject(id),v=views.getJSONObject(a.getInt("bufferView"));
            if(a.getInt("componentType")!=5126)throw new IllegalArgumentException("Unsupported float accessor");
            int count=a.getInt("count"),stride=v.optInt("byteStride",components*4);
            int offset=base+v.optInt("byteOffset",0)+a.optInt("byteOffset",0);
            if(count<1||count>600000||offset<0||offset+(long)(count-1)*stride+components*4>source.capacity())throw new IllegalArgumentException("GLB float out of bounds");
            float[] result=new float[count*components];
            for(int i=0;i<count;i++)for(int j=0;j<components;j++)result[i*components+j]=source.getFloat(offset+i*stride+j*4);
            return result;
        }
        private int[] readIndices(ByteBuffer source,int base,JSONArray views,JSONArray accessors,int id)throws Exception{
            JSONObject a=accessors.getJSONObject(id),v=views.getJSONObject(a.getInt("bufferView"));
            int count=a.getInt("count"),type=a.getInt("componentType"),size=type==5125?4:(type==5123?2:1);
            int stride=v.optInt("byteStride",size),offset=base+v.optInt("byteOffset",0)+a.optInt("byteOffset",0);
            if(count<3||count>3000000||offset<0||offset+(long)(count-1)*stride+size>source.capacity())throw new IllegalArgumentException("GLB indices hors limite");
            int[] result=new int[count];
            for(int i=0;i<count;i++){
                int p=offset+i*stride;
                long value=type==5125?(source.getInt(p)&0xffffffffL):(type==5123?(source.getShort(p)&0xffff):(source.get(p)&255));
                if(value>Integer.MAX_VALUE)throw new IllegalArgumentException("Index GLB hors limites");
                result[i]=(int)value;
            }
            return result;
        }
        private int compile(int shaderType,String source){
            int shader=GLES20.glCreateShader(shaderType);
            GLES20.glShaderSource(shader,source);GLES20.glCompileShader(shader);
            int[] ok=new int[1];GLES20.glGetShaderiv(shader,GLES20.GL_COMPILE_STATUS,ok,0);
            if(ok[0]==0)throw new IllegalStateException(GLES20.glGetShaderInfoLog(shader));
            return shader;
        }
        private int link(String vs,String fs){
            int vert=compile(GLES20.GL_VERTEX_SHADER,vs),frag=compile(GLES20.GL_FRAGMENT_SHADER,fs);
            int p=GLES20.glCreateProgram();GLES20.glAttachShader(p,vert);GLES20.glAttachShader(p,frag);GLES20.glLinkProgram(p);
            int[] ok=new int[1];GLES20.glGetProgramiv(p,GLES20.GL_LINK_STATUS,ok,0);
            if(ok[0]==0)throw new IllegalStateException(GLES20.glGetProgramInfoLog(p));
            GLES20.glDeleteShader(vert);GLES20.glDeleteShader(frag);return p;
        }
    }
    private static final class Mesh {
        final FloatBuffer positions,uvs;
        final ShortBuffer indices;
        final float[] color;
        final boolean jaw,textured,volume;
        final FloatBuffer openMorph,blinkMorph;
        final int count;
        Mesh(float[] pos,float[] uv,short[] index,float[] rgba,boolean jaw,boolean textured,
             boolean volume,float[] lipDelta,float[] blinkDelta){
            positions=ByteBuffer.allocateDirect(pos.length*4).order(ByteOrder.nativeOrder()).asFloatBuffer();positions.put(pos);positions.position(0);
            if(uv!=null){uvs=ByteBuffer.allocateDirect(uv.length*4).order(ByteOrder.nativeOrder()).asFloatBuffer();uvs.put(uv);uvs.position(0);}else uvs=null;
            indices=ByteBuffer.allocateDirect(index.length*2).order(ByteOrder.nativeOrder()).asShortBuffer();indices.put(index);indices.position(0);
            count=index.length;color=rgba;this.jaw=jaw;this.textured=textured;this.volume=volume;
            openMorph=makeBuffer(lipDelta);
            blinkMorph=makeBuffer(blinkDelta);
        }
    }
    private static FloatBuffer makeBuffer(float[] src){
        if(src==null)return null;
        FloatBuffer b=ByteBuffer.allocateDirect(src.length*4).order(ByteOrder.nativeOrder()).asFloatBuffer();
        b.put(src);b.position(0);return b;
    }
}