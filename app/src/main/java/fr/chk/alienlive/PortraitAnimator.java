package fr.chk.alienlive;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;

/** Visible but deliberately subtle proxy for mouth movement in the prototype.
 * Final photorealistic characters need facial mesh and blendshape rigs. */
public final class PortraitAnimator {
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF oval=new RectF();
    private final Path path=new Path();
    private static final float[] MOUTH_X={.51f,.52f,.56f,.52f,.52f,.50f};
    private static final float[] MOUTH_Y={.61f,.58f,.60f,.59f,.55f,.60f};
    public void draw(Canvas canvas,RectF portrait,int character,float mouth,boolean enabled){
        if(!enabled || mouth<.07f)return;
        int slot=Math.max(0,Math.min(MOUTH_X.length-1,character));
        float x=portrait.left+MOUTH_X[slot]*portrait.width();
        float y=portrait.top+MOUTH_Y[slot]*portrait.height();
        // The mouth location for each provisional portrait is approximate.
        float rx=portrait.width()*(slot==4?.073f:.063f);
        float ry=portrait.height()*(.002f+.021f*mouth);
        float opacity=Math.min(1f,(mouth-.07f)*1.5f);
        oval.set(x-rx,y-ry,x+rx,y+ry);
        canvas.save();
        canvas.clipRect(portrait);
        paint.setShader(new LinearGradient(x,y-ry,x,y+ry,
            new int[]{Color.argb((int)(180*opacity),20,8,17),Color.argb((int)(234*opacity),10,2,8),Color.argb((int)(162*opacity),68,24,36)},
            null,Shader.TileMode.CLAMP));
        paint.setStyle(Paint.Style.FILL);
        canvas.drawOval(oval,paint);
        paint.setShader(null);
        paint.setStrokeWidth(Math.max(1f,portrait.width()*.003f));
        paint.setColor(Color.argb((int)(76*opacity),213,142,151));
        paint.setStyle(Paint.Style.STROKE);
        canvas.drawOval(oval,paint);
        paint.setStyle(Paint.Style.FILL);
        canvas.restore();
    }
}
