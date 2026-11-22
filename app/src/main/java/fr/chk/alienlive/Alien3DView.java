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
    private volatile float yaw=0f,pitch=0f,roll=0f,mouth=0f,blink=0f,x=.5f,y=.5f;
    private volatile boolean saver=false;
    private volatile boolean naturalModel=true;
    // Charge d'abord le véritable modèle historique, non sa reconstruction basse résolution.
    private volatile String activeAsset="pluthorien_alpha.glb";
    private volatile boolean reloadModel=false;
    private boolean framesActive=false;
    private final Runnable frameTicker=new Runnable(){
        @Override public void run(){
            if(!framesActive)return;
            requestRender();
            postDelayed(this,saver?50L:33L);
        }
    };
    private volatile boolean useHead=true;

    public Alien3DView(Context context, Status status){
        super(context);
        setEGLContextClientVersion(2);
        setEGLConfigChooser(8,8,8,8,16,0);
        getHolder().setFormat(PixelFormat.TRANSLUCENT);
        setZOrderOnTop(true);
        renderer=new StageRenderer(context,status);
        setRenderer(renderer);
        setRenderMode(RENDERMODE_WHEN_DIRTY);
        setClickable(true);
    }
    public void setPose(float x,float y,float roll,float mouth){
        this.x=x;this.y=y;this.roll=roll;this.mouth=mouth;
    }
    public void setExpression(float yaw,float pitch,float blink){this.yaw=yaw;this.pitch=pitch;this.blink=blink;}
    public void setBatterySaver(boolean enable){saver=enable;}
    public void setNaturalModel(boolean enable){naturalModel=enable;}
    public void setCharacter(int index,boolean originalAlpha){
        if(index<0||index>=AlienData.MODEL_FILES.length)return;
        String asset=index==0&&originalAlpha?"pluthorien_alpha.glb":AlienData.MODEL_FILES[index];
        if(!asset.equals(activeAsset)){
            activeAsset=asset;
            reloadModel=true;
            requestRender();
        }else if(renderer.loaded){
            // Après un retour du mode portrait, restaurer la vue 3D déjà chargée.
            post(()->renderer.callback.onReady());
        }
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
        private final float[] projection=new float[16],view=new float[16],root=new float[16],tmp=new float[16],mvp=new float[16],local=new float[16];
        private final List<Mesh> meshes=new ArrayList<>();
        private int program,positionLoc,uvLoc,mvpLoc,baseLoc,texLoc,useTexLoc,lightLoc,lipLoc;
        private int mouthMorphLoc,blinkMorphLoc,mouthMorphWeightLoc,blinkMorphWeightLoc;
        private int textureId;
        private volatile boolean loaded;
        private float easedYaw,easedPitch,easedRoll,easedJaw,easedBlink,easedX=.5f,easedY=.5f;
        private final String VERTEX="attribute vec3 aPos;attribute vec2 aUv;attribute vec3 aMouthMorph;attribute vec3 aBlinkMorph;uniform mat4 uMvp;uniform float uLip;uniform float uMouthWeight;uniform float uBlinkWeight;varying vec2 vUv;varying float vLight;void main(){vec3 pos=aPos+aMouthMorph*uMouthWeight+aBlinkMorph*uBlinkWeight;float region=exp(-32.0*pos.x*pos.x-165.0*(pos.y+0.30)*(pos.y+0.30));pos.y+=uLip*0.022*region;pos.z+=uLip*0.009*region;vUv=aUv;vLight=0.84+0.16*max(0.0,pos.z);gl_Position=uMvp*vec4(pos,1.0);}";
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
                mouthMorphLoc=GLES20.glGetAttribLocation(program,"aMouthMorph");
                blinkMorphLoc=GLES20.glGetAttribLocation(program,"aBlinkMorph");
                mouthMorphWeightLoc=GLES20.glGetUniformLocation(program,"uMouthWeight");
                blinkMorphWeightLoc=GLES20.glGetUniformLocation(program,"uBlinkWeight");
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
            float blend=.16f;
            easedYaw+=(yaw-easedYaw)*blend;
            easedPitch+=(pitch-easedPitch)*blend;
            easedRoll+=(roll-easedRoll)*blend;
            easedJaw+=(mouth-easedJaw)*(mouth>easedJaw?.66f:.46f);
            easedBlink+=(blink-easedBlink)*.52f;
            easedX+=(x-easedX)*blend;
            easedY+=(y-easedY)*blend;
            Matrix.setLookAtM(view,0,0f,0f,3.7f,0f,-.13f,0f,0f,1f,0f);
            Matrix.setIdentityM(root,0);
            Matrix.translateM(root,0,(easedX-.5f)*.34f,(.5f-easedY)*.23f,0f);
            if(useHead){
                Matrix.rotateM(root,0,-easedRoll*.75f,0f,0f,1f);
                Matrix.rotateM(root,0,-easedYaw*27f,0f,1f,0f);
                Matrix.rotateM(root,0,easedPitch*20f,1f,0f,0f);
            }
            GLES20.glUseProgram(program);
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D,textureId);
            GLES20.glUniform1i(texLoc,0);
            for(Mesh mesh:meshes){
                boolean classic="pluthorien_alpha.glb".equals(activeAsset);
                // Tous les volumes d'origine sont préservés dans le mode fidèle.
                if(!classic&&naturalModel&&!mesh.textured&&!mesh.jaw&&!mesh.volume)continue;
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
                Matrix.multiplyMM(tmp,0,root,0,local,0);
                float[] vm=new float[16];
                Matrix.multiplyMM(vm,0,view,0,tmp,0);
                Matrix.multiplyMM(mvp,0,projection,0,vm,0);
                GLES20.glUniformMatrix4fv(mvpLoc,1,false,mvp,0);
                GLES20.glUniform4fv(baseLoc,1,mesh.color,0);
                GLES20.glUniform1f(useTexLoc,mesh.textured?1f:0f);
                // Déformation locale de la lèvre haute sur les sommets texturés.
                GLES20.glUniform1f(lipLoc,!classic&&mesh.textured&&!mesh.jaw?easedJaw*.35f:0f);
                GLES20.glUniform1f(mouthMorphWeightLoc,mesh.openMorph!=null?easedJaw:0f);
                GLES20.glUniform1f(blinkMorphWeightLoc,mesh.blinkMorph!=null?easedBlink:0f);
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
            GLES20.glDisableVertexAttribArray(mouthMorphLoc);
            GLES20.glDisableVertexAttribArray(blinkMorphLoc);
                    GLES20.glVertexAttrib2f(uvLoc,0f,0f);
                }
                mesh.indices.position(0);
                GLES20.glDrawElements(GLES20.GL_TRIANGLES,mesh.count,GLES20.GL_UNSIGNED_SHORT,mesh.indices);
            }
            GLES20.glDisableVertexAttribArray(positionLoc);
            GLES20.glDisableVertexAttribArray(uvLoc);
        }
        private void readModel() throws Exception {
            meshes.clear();
            if(textureId!=0){
                int[] ids={textureId};
                GLES20.glDeleteTextures(1,ids,0);
                textureId=0;
            }
            byte[] bytes;
            try(InputStream in=context.getAssets().open(activeAsset)){
                ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] block=new byte[32768];int n;
                while((n=in.read(block))>=0){out.write(block,0,n);if(out.size()>25_000_000)throw new IllegalArgumentException("Model too large");}
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
                    Bitmap bitmap=BitmapFactory.decodeByteArray(bytes,offset,length);
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
                    short[] indices=readIndices(file,dataOffset,views,accessors,primitive.getInt("indices"));
                    JSONObject pbr=materials.getJSONObject(primitive.optInt("material",0)).getJSONObject("pbrMetallicRoughness");
                    JSONArray colors=pbr.optJSONArray("baseColorFactor");
                    float[] color=new float[]{1f,1f,1f,1f};
                    if(colors!=null)for(int k=0;k<4;k++)color[k]=(float)colors.getDouble(k);
                    boolean textured=pbr.has("baseColorTexture")&&textureId!=0;
                    float[] lipDelta=null,blinkDelta=null;
                    JSONArray targets=primitive.optJSONArray("targets");
                    if(name.startsWith("Head_")&&targets!=null&&targets.length()>3){
                        lipDelta=readFloats(file,dataOffset,views,accessors,
                                targets.getJSONObject(0).getInt("POSITION"),3);
                        blinkDelta=readFloats(file,dataOffset,views,accessors,
                                targets.getJSONObject(3).getInt("POSITION"),3);
                        if(lipDelta.length!=positions.length||blinkDelta.length!=positions.length)
                            throw new IllegalArgumentException("Blendshapes du visage invalides");
                    }
                    meshes.add(new Mesh(positions,uv,indices,color,jaw,textured,volume,lipDelta,blinkDelta));
                }
            }
        }
        private float[] readFloats(ByteBuffer source,int base,JSONArray views,JSONArray accessors,int id,int components)throws Exception{
            JSONObject a=accessors.getJSONObject(id),v=views.getJSONObject(a.getInt("bufferView"));
            if(a.getInt("componentType")!=5126)throw new IllegalArgumentException("Unsupported float accessor");
            int count=a.getInt("count"),stride=v.optInt("byteStride",components*4);
            int offset=base+v.optInt("byteOffset",0)+a.optInt("byteOffset",0);
            if(count<1||count>100000||offset+(count-1)*stride+components*4>source.capacity())throw new IllegalArgumentException("GLB float out of bounds");
            float[] result=new float[count*components];
            for(int i=0;i<count;i++)for(int j=0;j<components;j++)result[i*components+j]=source.getFloat(offset+i*stride+j*4);
            return result;
        }
        private short[] readIndices(ByteBuffer source,int base,JSONArray views,JSONArray accessors,int id)throws Exception{
            JSONObject a=accessors.getJSONObject(id),v=views.getJSONObject(a.getInt("bufferView"));
            int count=a.getInt("count"),type=a.getInt("componentType"),size=type==5125?4:(type==5123?2:1);
            int stride=v.optInt("byteStride",size),offset=base+v.optInt("byteOffset",0)+a.optInt("byteOffset",0);
            if(count<3||count>300000||offset+(count-1)*stride+size>source.capacity())throw new IllegalArgumentException("GLB indices hors limite");
            short[] result=new short[count];
            for(int i=0;i<count;i++){
                int p=offset+i*stride;
                long value=type==5125?(source.getInt(p)&0xffffffffL):(type==5123?(source.getShort(p)&0xffff):(source.get(p)&255));
                if(value>65535)throw new IllegalArgumentException("GLB nécessite des index >16 bits");
                result[i]=(short)value;
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