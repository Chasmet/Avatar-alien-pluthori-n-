package fr.chk.alienlive;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.PointF;
import android.graphics.YuvImage;
import android.graphics.Rect;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.media.FaceDetector;
import android.media.Image;
import android.media.ImageReader;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.SystemClock;
import android.util.Log;
import android.util.Size;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.util.Arrays;

/** Suivi facial local léger et sans API réseau. Pas de détection labiale fine. */
public final class FaceTracker {
    public interface Listener { void onPose(float x,float y,float roll,float mouth); void onStatus(String status); default void onExpression(float yaw,float pitch,float blinkLeft,float blinkRight){} }
    private final Context context;
    private final Listener listener;
    private HandlerThread thread;
    private Handler handler;
    private ImageReader reader;
    private CameraDevice device;
    private CameraCaptureSession session;
    private int sensorOrientation=90;
    private int rotation=90;
    private long lastProcessed=0;
    private int interval=160;
    private volatile boolean stopped=true;
    private float mouthBaseline=-1f, lastMouthLevel=0f;

    public FaceTracker(Context c, Listener l) {context=c;listener=l;}
    public void setSaver(boolean value) {interval=value?260:115;}
    @SuppressLint("MissingPermission")
    public void start() {
        if(!stopped) return;
        stopped=false;
        thread=new HandlerThread("chk-face-camera");thread.start();handler=new Handler(thread.getLooper());
        try {
            CameraManager manager=(CameraManager)context.getSystemService(Context.CAMERA_SERVICE);
            String found=null;
            for(String id:manager.getCameraIdList()) {
                CameraCharacteristics ch=manager.getCameraCharacteristics(id);
                Integer direction=ch.get(CameraCharacteristics.LENS_FACING);
                if(direction!=null && direction==CameraCharacteristics.LENS_FACING_FRONT) {
                    found=id;Integer orientation=ch.get(CameraCharacteristics.SENSOR_ORIENTATION);
                    if(orientation!=null) sensorOrientation=orientation;
                    int display=((android.view.WindowManager)context.getSystemService(Context.WINDOW_SERVICE)).getDefaultDisplay().getRotation();
                    int degrees=display==android.view.Surface.ROTATION_90?90:display==android.view.Surface.ROTATION_180?180:display==android.view.Surface.ROTATION_270?270:0;
                    rotation=(sensorOrientation+degrees)%360;
                    break;
                }
            }
            if(found==null) {listener.onStatus("Aucune caméra avant détectée");stop();return;}
            reader=ImageReader.newInstance(320,240,ImageFormat.YUV_420_888,2);
            reader.setOnImageAvailableListener(r->{
                Image img=null;
                try {
                    img=r.acquireLatestImage();
                    if(img==null||stopped)return;
                    long time=SystemClock.uptimeMillis();
                    if(time-lastProcessed<interval)return;
                    lastProcessed=time;
                    findFace(img);
                } catch(Exception err) {Log.w("CHKAlienFace", "Image", err);}
                finally {if(img!=null)img.close();}
            },handler);
            manager.openCamera(found,new CameraDevice.StateCallback(){
                @Override public void onOpened(CameraDevice c){if(stopped){c.close();return;}device=c;configure();}
                @Override public void onDisconnected(CameraDevice c){c.close();listener.onStatus("Caméra déconnectée");}
                @Override public void onError(CameraDevice c,int error){c.close();listener.onStatus("Erreur caméra : "+error);}
            },handler);
        } catch(SecurityException | CameraAccessException e){ listener.onStatus("Caméra indisponible : "+e.getMessage());stop(); }
    }
    private void configure(){
        if(device==null||reader==null||stopped)return;
        try {
            device.createCaptureSession(Arrays.asList(reader.getSurface()),new CameraCaptureSession.StateCallback(){
                @Override public void onConfigured(CameraCaptureSession s){
                    if(stopped){s.close();return;}
                    session=s;
                    try {
                        CaptureRequest.Builder b=device.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
                        b.addTarget(reader.getSurface());
                        b.set(CaptureRequest.CONTROL_AF_MODE,CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_VIDEO);
                        s.setRepeatingRequest(b.build(),null,handler);
                        listener.onStatus("Suivi visage et lèvres (expérimental)");
                    } catch(Exception e){listener.onStatus("Capture indisponible");}
                }
                @Override public void onConfigureFailed(CameraCaptureSession s){listener.onStatus("Capture caméra refusée");}
            },handler);
        }catch(CameraAccessException e){listener.onStatus("Session caméra impossible");}
    }
    private void findFace(Image img) {
        byte[] nv21=nv21(img);
        YuvImage yuv=new YuvImage(nv21,ImageFormat.NV21,img.getWidth(),img.getHeight(),null);
        ByteArrayOutputStream out=new ByteArrayOutputStream();
        yuv.compressToJpeg(new Rect(0,0,img.getWidth(),img.getHeight()),65,out);
        byte[] bytes=out.toByteArray();
        Bitmap src=BitmapFactory.decodeByteArray(bytes,0,bytes.length);
        if(src==null)return;
        Matrix matrix=new Matrix();matrix.postRotate(rotation);matrix.postScale(-1,1);
        Bitmap upright=Bitmap.createBitmap(src,0,0,src.getWidth(),src.getHeight(),matrix,false);
        src.recycle();
        int width=upright.getWidth()&~1, height=upright.getHeight();
        Bitmap rgb=Bitmap.createBitmap(width,height,Bitmap.Config.RGB_565);
        android.graphics.Canvas cv=new android.graphics.Canvas(rgb);
        cv.drawBitmap(upright,0,0,null);upright.recycle();
        FaceDetector.Face[] faces=new FaceDetector.Face[1];
        FaceDetector detector=new FaceDetector(width,height,1);
        int count=detector.findFaces(rgb,faces);
        if(count>0&&faces[0]!=null){
            PointF midpoint=new PointF();faces[0].getMidPoint(midpoint);
            final float x=Math.max(0f,Math.min(1f,midpoint.x/width));
            final float y=Math.max(0f,Math.min(1f,midpoint.y/height));
            final float roll=faces[0].pose(FaceDetector.Face.EULER_Z);
            float mouth=estimateMouth(rgb,midpoint,faces[0].eyesDistance());
            listener.onPose(x,y,roll,mouth);
        }
        rgb.recycle();
    }
    /** Indice approximatif d'ouverture de la bouche déduit des pixels de la caméra,
     * sans accès au microphone utilisé par TikTok. Ce n'est PAS un suivi phonétique précis. */
    private float estimateMouth(Bitmap image,PointF eyes,float eyeDistance){
        if(eyeDistance<8f)return 0f;
        int width=image.getWidth(), height=image.getHeight();
        float cx=eyes.x, cy=eyes.y+eyeDistance*.95f;
        int left=Math.max(0,(int)(cx-eyeDistance*.43f));
        int right=Math.min(width-1,(int)(cx+eyeDistance*.43f));
        int top=Math.max(0,(int)(cy-eyeDistance*.15f));
        int bottom=Math.min(height-1,(int)(cy+eyeDistance*.35f));
        int n=0, dark=0;
        for(int py=top;py<=bottom;py+=2){
            for(int px=left;px<=right;px+=2){
                int color=image.getPixel(px,py);
                int lum=(android.graphics.Color.red(color)*3+android.graphics.Color.green(color)*6+android.graphics.Color.blue(color))/10;
                if(lum<65)dark++;
                n++;
            }
        }
        if(n<8)return 0f;
        float darkness=dark/(float)n;
        if(mouthBaseline<0)mouthBaseline=darkness;
        if(darkness<mouthBaseline)mouthBaseline=mouthBaseline*.90f+darkness*.10f;
        else mouthBaseline=mouthBaseline*.997f+darkness*.003f;
        float movement=Math.abs(darkness-lastMouthLevel);
        lastMouthLevel=darkness;
        return Math.max(0f,Math.min(1f,(darkness-mouthBaseline)*3.6f+movement*1.9f));
    }
    private static byte[] nv21(Image image){
        int w=image.getWidth(),h=image.getHeight();
        byte[] out=new byte[w*h*3/2];
        Image.Plane[] planes=image.getPlanes();
        // PixelStride/RowStride compatibles avec les buffers YUV de Camera2.
        for(int row=0;row<h;row++){
            ByteBuffer bb=planes[0].getBuffer();int base=row*planes[0].getRowStride();
            for(int col=0;col<w;col++)out[row*w+col]=bb.get(base+col*planes[0].getPixelStride());
        }
        for(int row=0;row<h/2;row++){
            ByteBuffer u=planes[1].getBuffer(),v=planes[2].getBuffer();
            for(int col=0;col<w/2;col++){
                int off=w*h+row*w+col*2;
                out[off]=v.get(row*planes[2].getRowStride()+col*planes[2].getPixelStride());
                out[off+1]=u.get(row*planes[1].getRowStride()+col*planes[1].getPixelStride());
            }
        }
        return out;
    }
    public void stop(){
        stopped=true;
        try{if(session!=null)session.close();}catch(Exception ignored){}
        session=null;
        try{if(device!=null)device.close();}catch(Exception ignored){}
        device=null;
        try{if(reader!=null)reader.close();}catch(Exception ignored){}
        reader=null;
        if(thread!=null){thread.quitSafely();thread=null;handler=null;}
        mouthBaseline=-1f;lastMouthLevel=0f;lastProcessed=0L;
    }
}
