package fr.chk.alienlive;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.view.MotionEvent;
import android.view.View;

/** Repères tactiles posés sur l'aperçu GLB : yeux gauche/droit et centre des lèvres.
 * Leur placement est indicatif en 2D, puis appliqué aux régions du vrai maillage.
 */
public final class FacePointsOverlay extends View {
    public interface Listener { void onMove(int point,float x,float y); default void onRelease(){} }
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private FaceCalibration settings;
    private Listener listener;
    private int dragging=-1;
    private final float density;
    public FacePointsOverlay(Context context){
        super(context);
        density=getResources().getDisplayMetrics().density;
        setLayerType(View.LAYER_TYPE_SOFTWARE,null);
    }
    public void configure(FaceCalibration cal,Listener changed){
        settings=cal;listener=changed;
        invalidate();
    }
    private float screenX(float x){return getWidth()*(.13f+x*.74f);}
    private float screenY(float y){return getHeight()*(.055f+(1f-y)*.89f);}
    private float normalizedX(float x){return FaceCalibration.clamp((x/getWidth()-.13f)/.74f);}
    private float normalizedY(float y){return FaceCalibration.clamp(1f-(y/getHeight()-.055f)/.89f);}
    @Override protected void onDraw(Canvas c){
        super.onDraw(c);
        if(settings==null)return;
        int[] colors={Color.rgb(98,233,253),Color.rgb(98,233,253),Color.rgb(250,157,210)};
        String[] labels={"G","D","B"};
        for(int i=0;i<3;i++){
            float[] point=settings.point(i);
            float x=screenX(point[0]),y=screenY(point[1]);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(2.5f*density);
            paint.setColor(colors[i]);
            c.drawCircle(x,y,13f*density,paint);
            c.drawLine(x-20*density,y,x+20*density,y,paint);
            c.drawLine(x,y-20*density,x,y+20*density,paint);
            paint.setStyle(Paint.Style.FILL);
            paint.setTextSize(12f*density);
            paint.setColor(Color.WHITE);
            c.drawText(labels[i],x+17*density,y-14*density,paint);
        }
        paint.setColor(0xBBE3D6FF);
        paint.setTextSize(10f*density);
        c.drawText("GLISSER G / D / B POUR CALIBRER",8f*density,getHeight()-11f*density,paint);
    }
    @Override public boolean onTouchEvent(MotionEvent e){
        if(settings==null||listener==null)return false;
        if(e.getActionMasked()==MotionEvent.ACTION_DOWN){
            float distance=Float.MAX_VALUE;
            for(int i=0;i<3;i++){
                float[] point=settings.point(i);
                float dx=e.getX()-screenX(point[0]),dy=e.getY()-screenY(point[1]);
                float d=dx*dx+dy*dy;
                if(d<distance){distance=d;dragging=i;}
            }
            if(distance>(64f*density)*(64f*density)){dragging=-1;return false;}
            getParent().requestDisallowInterceptTouchEvent(true);
            return true;
        }
        if(e.getActionMasked()==MotionEvent.ACTION_MOVE&&dragging>=0){
            float x=normalizedX(e.getX()),y=normalizedY(e.getY());
            settings.setPoint(dragging,x,y);
            listener.onMove(dragging,x,y);
            invalidate();
            return true;
        }
        if(e.getActionMasked()==MotionEvent.ACTION_UP||e.getActionMasked()==MotionEvent.ACTION_CANCEL){
            if(dragging>=0){dragging=-1;listener.onRelease();performClick();invalidate();return true;}
        }
        return true;
    }
    @Override public boolean performClick(){super.performClick();return true;}
}
