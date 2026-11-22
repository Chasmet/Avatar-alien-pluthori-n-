package fr.chk.alienlive;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.view.MotionEvent;
import android.view.View;

/**
 * Repères visuels sur le GLB. Mode précis : zoom 100–500 %, panoramique à deux
 * doigts, placement au doigt et ajustement fin jusqu'à 0,1 %.
 * Le viewport est partagé avec Alien3DView pour garder les repères alignés.
 */
public final class FacePointsOverlay extends View {
    public interface Listener {
        void onMove(int point,float x,float y);
        default void onRelease(){}
        default void onViewport(float zoom,float offsetX,float offsetY){}
    }
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private FaceCalibration settings;
    private Listener listener;
    private final float density;
    private int dragging=-1, selected=2;
    private float zoom=1f,offsetX=0f,offsetY=0f;
    private float pointerDistance=0f,lastCenterX=0f,lastCenterY=0f;
    private boolean precision=false,pinching=false;
    private static final int[] COLORS={0xFF67E8F9,0xFF67E8F9,0xFFFFA5D4};
    private static final String[] LABELS={"G","D","B"};

    public FacePointsOverlay(Context context){
        super(context);
        density=getResources().getDisplayMetrics().density;
        setLayerType(View.LAYER_TYPE_SOFTWARE,null);
    }
    public void configure(FaceCalibration cal,Listener changed){
        settings=cal;listener=changed;invalidate();
    }
    public void setPrecision(boolean enabled){
        precision=enabled;dragging=-1;pinching=false;invalidate();
    }
    public boolean isPrecision(){return precision;}
    public int selectedPoint(){return selected;}
    public float zoom(){return zoom;}
    public void selectPoint(int point){
        if(point>=0&&point<=2){selected=point;invalidate();}
    }
    public void zoomBy(float factor){
        setViewport(zoom*factor,offsetX,offsetY);
    }
    public void resetViewport(){setViewport(1f,0f,0f);}
    private static float limit(float v,float min,float max){return Math.max(min,Math.min(max,v));}
    private void setViewport(float z,float dx,float dy){
        zoom=limit(z,1f,5f);
        offsetX=limit(dx,-1.5f,1.5f);
        offsetY=limit(dy,-1.5f,1.5f);
        if(listener!=null)listener.onViewport(zoom,offsetX,offsetY);
        invalidate();
    }
    // Base mapping unifiée avec l'ancien panneau. Tous les gestes inversent
    // exactement la même transformation que celle du zoom de la vue.
    private float originX(float n){return getWidth()*(.13f+n*.74f);}
    private float originY(float n){return getHeight()*(.055f+(1f-n)*.89f);}
    private float screenX(float n){
        float w=getWidth();return w*.5f+(originX(n)-w*.5f)*zoom+offsetX*w;
    }
    private float screenY(float n){
        float h=getHeight();return h*.5f+(originY(n)-h*.5f)*zoom+offsetY*h;
    }
    private float normalizedX(float x){
        float w=Math.max(1,getWidth());
        float base=w*.5f+(x-w*.5f-offsetX*w)/zoom;
        return FaceCalibration.clamp((base/w-.13f)/.74f);
    }
    private float normalizedY(float y){
        float h=Math.max(1,getHeight());
        float base=h*.5f+(y-h*.5f-offsetY*h)/zoom;
        return FaceCalibration.clamp(1f-(base/h-.055f)/.89f);
    }
    public void nudgeSelected(float dx,float dy){
        if(settings==null)return;
        float[] point=settings.point(selected);
        float x=FaceCalibration.clamp(point[0]+dx);
        float y=FaceCalibration.clamp(point[1]+dy);
        settings.setPoint(selected,x,y);
        if(listener!=null)listener.onMove(selected,x,y);
        invalidate();
    }
    @Override protected void onDraw(Canvas canvas){
        super.onDraw(canvas);
        if(settings==null)return;
        for(int i=0;i<3;i++){
            float[] point=settings.point(i);
            float px=screenX(point[0]),py=screenY(point[1]);
            boolean active=precision&&selected==i;
            float r=(active?21f:precision?13.5f:13f)*density;
            // Contour noir pour que les repères soient visibles sur une peau claire.
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(5f*density);paint.setColor(0xE9000015);
            canvas.drawCircle(px,py,r,paint);
            paint.setStrokeWidth((active?3.5f:2.5f)*density);
            paint.setColor(COLORS[i]);canvas.drawCircle(px,py,r,paint);
            canvas.drawLine(px-r*1.42f,py,px+r*1.42f,py,paint);
            canvas.drawLine(px,py-r*1.42f,px,py+r*1.42f,paint);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(0xE9000015);
            canvas.drawCircle(px+r*.9f,py-r*.9f,10.5f*density,paint);
            paint.setColor(Color.WHITE);
            paint.setTextSize(12f*density);
            paint.setTextAlign(Paint.Align.CENTER);
            canvas.drawText(LABELS[i],px+r*.9f,py-r*.9f+4f*density,paint);
        }
        if(!precision){
            paint.setStyle(Paint.Style.FILL);
            paint.setTextAlign(Paint.Align.LEFT);
            paint.setTextSize(11f*density);paint.setColor(0xFFE6D6FF);
            canvas.drawText("GLISSER LES POINTS G / D / B",8f*density,
                getHeight()-12f*density,paint);
        }
    }
    private static float span(MotionEvent event){
        if(event.getPointerCount()<2)return 0f;
        float x=event.getX(0)-event.getX(1),y=event.getY(0)-event.getY(1);
        return (float)Math.hypot(x,y);
    }
    private static float midX(MotionEvent event){return (event.getX(0)+event.getX(1))*.5f;}
    private static float midY(MotionEvent event){return (event.getY(0)+event.getY(1))*.5f;}
    @Override public boolean onTouchEvent(MotionEvent event){
        if(settings==null||listener==null)return false;
        int action=event.getActionMasked();
        if(action==MotionEvent.ACTION_DOWN){
            dragging=-1;pinching=false;
            float min=Float.MAX_VALUE;
            int nearest=-1;
            for(int i=0;i<3;i++){
                float[] p=settings.point(i);
                float dx=event.getX()-screenX(p[0]),dy=event.getY()-screenY(p[1]);
                float dist=dx*dx+dy*dy;
                if(dist<min){min=dist;nearest=i;}
            }
            if(min<Math.pow((precision?57f:60f)*density,2)){
                selected=nearest;dragging=selected;
                getParent().requestDisallowInterceptTouchEvent(true);
                invalidate();return true;
            }
            if(precision){
                // Appui libre : place directement le repère déjà sélectionné.
                dragging=selected;
                getParent().requestDisallowInterceptTouchEvent(true);
                return true;
            }
            return false;
        }
        if(action==MotionEvent.ACTION_POINTER_DOWN&&event.getPointerCount()>=2){
            pinching=true;dragging=-1;
            pointerDistance=Math.max(10f,span(event));
            lastCenterX=midX(event);lastCenterY=midY(event);
            getParent().requestDisallowInterceptTouchEvent(true);
            return true;
        }
        if(action==MotionEvent.ACTION_MOVE){
            if(pinching&&event.getPointerCount()>=2){
                float nextDistance=Math.max(10f,span(event));
                float cx=midX(event),cy=midY(event);
                float previousZoom=zoom;
                float nextZoom=limit(zoom*nextDistance/pointerDistance,1f,5f);
                float ratio=nextZoom/previousZoom;
                float w=Math.max(1f,getWidth()),h=Math.max(1f,getHeight());
                float nx=offsetX+(lastCenterX/w-.5f-offsetX)*(1f-ratio)
                    +(cx-lastCenterX)/w;
                float ny=offsetY+(lastCenterY/h-.5f-offsetY)*(1f-ratio)
                    +(cy-lastCenterY)/h;
                setViewport(nextZoom,nx,ny);
                pointerDistance=nextDistance;
                lastCenterX=cx;lastCenterY=cy;
                return true;
            }
            if(dragging>=0&&!pinching){
                float x=normalizedX(event.getX()),y=normalizedY(event.getY());
                settings.setPoint(dragging,x,y);
                listener.onMove(dragging,x,y);
                invalidate();return true;
            }
            return true;
        }
        if(action==MotionEvent.ACTION_POINTER_UP){
            pinching=true;dragging=-1;return true;
        }
        if(action==MotionEvent.ACTION_UP){
            if(dragging>=0&&!pinching){
                float x=normalizedX(event.getX()),y=normalizedY(event.getY());
                settings.setPoint(dragging,x,y);
                listener.onMove(dragging,x,y);
                listener.onRelease();
                performClick();
            }
            dragging=-1;pinching=false;
            invalidate();return true;
        }
        if(action==MotionEvent.ACTION_CANCEL){
            dragging=-1;pinching=false;invalidate();return true;
        }
        return true;
    }
    @Override public boolean performClick(){super.performClick();return true;}
}
