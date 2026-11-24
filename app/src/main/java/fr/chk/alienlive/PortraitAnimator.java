package fr.chk.alienlive;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;

/**
 * Deformation 2D locale du visage, sans API externe.
 * Un portrait fixe ne possede pas de rig facial : le maillage restitue une
 * ouverture de machoire et un clignement approximes. Ce n'est pas du
 * lip-sync phoneme par phoneme ni un avatar 3D.
 */
public final class PortraitAnimator {
    private static final int COLS=32, ROWS=40;
    private final float[] vertices=new float[(COLS+1)*(ROWS+1)*2];
    private final Paint image=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);
    private final Paint cavity=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint lips=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF bounds=new RectF();
    private static final float[] MOUTH_X={.51f,.52f,.56f,.52f,.52f,.50f};
    private static final float[] MOUTH_Y={.61f,.58f,.60f,.59f,.55f,.60f};
    private static final float[] EYES_Y={.46f,.44f,.46f,.45f,.43f,.46f};

    private static float clamp(float x){return Math.max(0f,Math.min(1f,x));}
    private static float gauss(float x,float sigma){float z=x/sigma;return (float)Math.exp(-z*z*1.55f);}
    private int maskSlot=-1;
    private final float[] horizontalMask=new float[COLS+1],
        cheekAtRightMask=new float[COLS+1],
        cheekAtLeftMask=new float[COLS+1],
        leftEyeMask=new float[COLS+1],
        rightEyeMask=new float[COLS+1];
    private final float[] aroundMask=new float[ROWS+1],
        chinMask=new float[ROWS+1],
        cheekBandMask=new float[ROWS+1],
        lipBandYMask=new float[ROWS+1],
        eyeBandMask=new float[ROWS+1],
        faceMask=new float[ROWS+1];
    private void prepareMasks(int slot,float mx,float my,float eyeY){
        if(slot==maskSlot)return;
        for(int i=0;i<=COLS;i++){
            float u=i/(float)COLS;
            horizontalMask[i]=gauss(u-mx,.20f);
            cheekAtRightMask[i]=gauss(u-(mx-.13f),.09f);
            cheekAtLeftMask[i]=gauss(u-(mx+.13f),.09f);
            leftEyeMask[i]=gauss(u-.365f,.078f);
            rightEyeMask[i]=gauss(u-.635f,.078f);
        }
        for(int j=0;j<=ROWS;j++){
            float v=j/(float)ROWS;
            aroundMask[j]=gauss(v-my,.18f);
            chinMask[j]=gauss(v-(my+.17f),.13f);
            cheekBandMask[j]=gauss(v-(my+eyeY)*.5f,.064f);
            lipBandYMask[j]=gauss(v-my,.042f);
            eyeBandMask[j]=gauss(v-eyeY,.048f);
            faceMask[j]=gauss(v-.48f,.29f);
        }
        maskSlot=slot;
    }
    public void draw(Canvas canvas,Bitmap bitmap,int character,float mouth,
                     float yaw,float pitch,float leftBlink,float rightBlink,float cheekL,float cheekR,
                     float pucker,float wide,float smileLeft,float smileRight,float slide,float gazeX,float gazeY,boolean enabled) {
        if(bitmap==null||bitmap.isRecycled())return;
        int slot=Math.max(0,Math.min(MOUTH_X.length-1,character));
        if(!enabled){
            canvas.drawBitmap(bitmap,0,0,image);
            return;
        }
        int w=bitmap.getWidth(),h=bitmap.getHeight();
        float mx=MOUTH_X[slot],my=MOUTH_Y[slot],eyeY=EYES_Y[slot];
        prepareMasks(slot,mx,my,eyeY);
        float m=clamp(mouth),left=clamp(leftBlink),right=clamp(rightBlink);
        float headYaw=Math.max(-1f,Math.min(1f,yaw));
        float headPitch=Math.max(-1f,Math.min(1f,pitch));
        int p=0;
        for(int j=0;j<=ROWS;j++){
            float v=j/(float)ROWS;
            for(int i=0;i<=COLS;i++){
                float u=i/(float)COLS;
                float x=u,y=v;
                float horizontal=horizontalMask[i];
                // La levre superieure monte, la machoire descend.
                float below=clamp((v-my+.008f)/.11f);
                float above=clamp((my-v+.008f)/.065f);
                float around=aroundMask[j];
                y+=m*horizontal*(.046f*below*around-.014f*above*around);
                // Le menton accompagne le mouvement de machoire.
                y+=m*horizontal*.015f*chinMask[j];
                float cheekBand=cheekBandMask[j];
                float lift=cheekBand*(cheekR*cheekAtRightMask[i]+cheekL*cheekAtLeftMask[i]);
                float lipBand=horizontal*lipBandYMask[j];
                float side=clamp(.5f+(u-mx)/.09f);
                float smile=smileRight+(smileLeft-smileRight)*side;
                x+=.035f*slide*lipBand;
                y-=.020f*lift+.007f*smile*lipBand;
                x+=(u-mx)*lipBand*(.19f*smile-.42f*pucker+.34f*wide*(1f-pucker));
                // Contraction locale des paupieres avec souplesse.
                float leftEye=leftEyeMask[i];
                float rightEye=rightEyeMask[i];
                float eyeBand=eyeBandMask[j];
                y+=eyeBand*(leftEye*right+rightEye*left)*.014f*(v<eyeY?1f:-1f);
                float eyeMask=eyeBand*(leftEye*(1f-right)+rightEye*(1f-left));
                x+=gazeX*.010f*eyeMask;
                y-=gazeY*.007f*eyeMask;
                // Perspective 2.5D subtile, impossible de remplacer une vraie tete 3D.
                float face=faceMask[j];
                x+=headYaw*face*(.020f+(u-.5f)*.040f);
                y+=headPitch*face*.015f;
                vertices[p++]=x*w;vertices[p++]=y*h;
            }
        }
        canvas.drawBitmapMesh(bitmap,COLS,ROWS,vertices,0,null,0,image);

        // Ombre interne douce seulement quand la machoire est ouverte.
        // Les levres restent issues de la texture de l'avatar, non d'un dessin opaque.
        if(m>.10f){
            float x=(mx+.035f*slide)*w, y=(my+.003f)*h;
            float rx=w*(.055f+.013f*m)*(1f-.30f*pucker+.15f*wide);
            float ry=h*(.0035f+.015f*m);
            int alpha=(int)(130f*clamp((m-.09f)*1.25f));
            cavity.setShader(new RadialGradient(x,y,Math.max(rx,ry),
                new int[]{Color.argb(alpha,10,5,13),
                          Color.argb((int)(alpha*.75f),38,14,24),
                          Color.TRANSPARENT},
                new float[]{0f,.63f,1f},Shader.TileMode.CLAMP));
            bounds.set(x-rx,y-ry,x+rx,y+ry);
            canvas.drawOval(bounds,cavity);
            cavity.setShader(null);
            lips.setColor(Color.argb((int)(36*m),231,157,153));
            lips.setStyle(Paint.Style.STROKE);
            lips.setStrokeWidth(Math.max(1f,w*.002f));
            canvas.drawOval(bounds,lips);
            lips.setStyle(Paint.Style.FILL);
        }
    }
}
