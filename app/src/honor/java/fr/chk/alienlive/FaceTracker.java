package fr.chk.alienlive;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.ImageFormat;
import android.graphics.PointF;
import android.graphics.Rect;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.SystemClock;
import android.util.Size;
import android.view.Surface;
import android.view.WindowManager;

import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.face.Face;
import com.google.mlkit.vision.face.FaceContour;
import com.google.mlkit.vision.face.FaceDetection;
import com.google.mlkit.vision.face.FaceDetectorOptions;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/** Analyse faciale hors ligne ML Kit (mode Honor). Aucun upload, aucune API web. */
public final class FaceTracker {
    public interface Listener {
        void onPose(float x,float y,float roll,float mouth);
        void onStatus(String status);
    }
    private final Context context;
    private final Listener listener;
    private final com.google.mlkit.vision.face.FaceDetector detector;
    private final AtomicBoolean busy = new AtomicBoolean();
    private CameraDevice camera;
    private CameraCaptureSession session;
    private ImageReader reader;
    private HandlerThread cameraThread;
    private Handler cameraHandler;
    private int rotation=90;
    private int intervalMs=110;
    private volatile boolean stopped=true;
    private long lastFrame=0, lastFound=0;
    private volatile boolean announced=false;

    public FaceTracker(Context ctx,Listener callback) {
        context=ctx; listener=callback;
        FaceDetectorOptions opts=new FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
            .setContourMode(FaceDetectorOptions.CONTOUR_MODE_ALL)
            .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
            .setMinFaceSize(0.20f)
            .build();
        detector=FaceDetection.getClient(opts);
    }
    public void setSaver(boolean saver) {intervalMs=saver?180:85;}
    @SuppressLint("MissingPermission")
    public synchronized void start(){
        if(!stopped)return;
        stopped=false;
        cameraThread=new HandlerThread("chk-face-mlkit");
        cameraThread.start();
        cameraHandler=new Handler(cameraThread.getLooper());
        try{
            CameraManager mgr=(CameraManager)context.getSystemService(Context.CAMERA_SERVICE);
            String front=null;
            Size size=new Size(640,480);
            for(String id:mgr.getCameraIdList()){
                CameraCharacteristics ch=mgr.getCameraCharacteristics(id);
                if(ch.get(CameraCharacteristics.LENS_FACING)!=null&&
                   ch.get(CameraCharacteristics.LENS_FACING)==CameraCharacteristics.LENS_FACING_FRONT){
                    front=id;
                    Integer orient=ch.get(CameraCharacteristics.SENSOR_ORIENTATION);
                    int sensor=orient==null?90:orient;
                    int d=((WindowManager)context.getSystemService(Context.WINDOW_SERVICE)).getDefaultDisplay().getRotation();
                    int degree=d==Surface.ROTATION_90?90:d==Surface.ROTATION_180?180:d==Surface.ROTATION_270?270:0;
                    rotation=(sensor+degree)%360;
                    StreamConfigurationMap map=ch.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
                    if(map!=null){
                        Size[] sizes=map.getOutputSizes(ImageFormat.YUV_420_888);
                        if(sizes!=null&&sizes.length>0){
                            long best=Long.MAX_VALUE;
                            for(Size s:sizes){
                                if(s.getWidth()<480||s.getHeight()<360)continue;
                                long score=Math.abs(s.getWidth()-640L)*3L+Math.abs(s.getHeight()-480L)*3L;
                                if(score<best){best=score;size=s;}
                            }
                            if(best==Long.MAX_VALUE)size=sizes[0];
                        }
                    }
                    break;
                }
            }
            if(front==null){listener.onStatus("Caméra avant absente");stop();return;}
            reader=ImageReader.newInstance(size.getWidth(),size.getHeight(),ImageFormat.YUV_420_888,3);
            reader.setOnImageAvailableListener(r->{
                Image img=null;
                try{
                    img=r.acquireLatestImage();
                    if(img==null)return;
                    long now=SystemClock.uptimeMillis();
                    if(stopped || now-lastFrame<intervalMs || !busy.compareAndSet(false,true)){
                        img.close();return;
                    }
                    lastFrame=now;
                    final Image pending=img;
                    final int orientedWidth=rotation==90||rotation==270?img.getHeight():img.getWidth();
                    final int orientedHeight=rotation==90||rotation==270?img.getWidth():img.getHeight();
                    InputImage input=InputImage.fromMediaImage(img,rotation);
                    detector.process(input).addOnSuccessListener(faces->{
                        if(stopped)return;
                        if(!faces.isEmpty()){
                            Face f=faces.get(0);
                            Rect bounds=f.getBoundingBox();
                            float yaw=f.getHeadEulerAngleY();
                            float pitch=f.getHeadEulerAngleX();
                            float px=clamp(1f-bounds.exactCenterX()/orientedWidth+yaw/180f);
                            float py=clamp(bounds.exactCenterY()/orientedHeight+pitch/240f);
                            float roll=-f.getHeadEulerAngleZ();
                            float mouth=mouthOpen(f);
                            lastFound=SystemClock.uptimeMillis();
                            listener.onPose(px,py,roll,mouth);
                            if(!announced){announced=true;listener.onStatus("Visage suivi • lèvres et mouvements actifs");}
                        }else if(now-lastFound>1600L&&announced){
                            announced=false;
                            listener.onStatus("Visage non détecté : regarde la caméra");
                        }
                    }).addOnFailureListener(e->listener.onStatus("Suivi facial : "+e.getClass().getSimpleName()))
                      .addOnCompleteListener(t->{pending.close();busy.set(false);});
                }catch(Exception e){
                    if(img!=null)img.close();
                    busy.set(false);
                    listener.onStatus("Image caméra indisponible");
                }
            },cameraHandler);
            mgr.openCamera(front,new CameraDevice.StateCallback(){
                @Override public void onOpened(CameraDevice c){if(stopped){c.close();return;}camera=c;configure();}
                @Override public void onDisconnected(CameraDevice c){c.close();listener.onStatus("Caméra déconnectée");}
                @Override public void onError(CameraDevice c,int code){c.close();listener.onStatus("Erreur caméra : "+code);}
            },cameraHandler);
            listener.onStatus("Initialisation suivi facial local…");
        }catch(Exception e){listener.onStatus("Caméra indisponible : "+e.getClass().getSimpleName());stop();}
    }
    private void configure(){
        if(stopped||camera==null||reader==null)return;
        try{
            camera.createCaptureSession(Arrays.asList(reader.getSurface()),new CameraCaptureSession.StateCallback(){
                @Override public void onConfigured(CameraCaptureSession s){
                    if(stopped){s.close();return;}
                    session=s;
                    try{
                        CaptureRequest.Builder b=camera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
                        b.addTarget(reader.getSurface());
                        b.set(CaptureRequest.CONTROL_AF_MODE,CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_VIDEO);
                        s.setRepeatingRequest(b.build(),null,cameraHandler);
                    }catch(Exception e){listener.onStatus("Démarrage caméra refusé");}
                }
                @Override public void onConfigureFailed(CameraCaptureSession s){listener.onStatus("Session caméra refusée");}
            },cameraHandler);
        }catch(Exception e){listener.onStatus("Erreur configuration caméra");}
    }
    private static float clamp(float x){return Math.max(0f,Math.min(1f,x));}
    private static float mouthOpen(Face f){
        FaceContour up=f.getContour(FaceContour.UPPER_LIP_BOTTOM);
        FaceContour down=f.getContour(FaceContour.LOWER_LIP_TOP);
        if(up==null||down==null)return 0;
        List<PointF> u=up.getPoints(), d=down.getPoints();
        if(u.isEmpty()||d.isEmpty())return 0;
        // Contour central : écart vertical entre lèvre supérieure et inférieure.
        PointF upper=u.get(u.size()/2);
        PointF lower=d.get(d.size()/2);
        float gap=(lower.y-upper.y)/Math.max(1f,f.getBoundingBox().width());
        return clamp((gap-0.008f)*12f);
    }
    public synchronized void stop(){
        stopped=true;
        try{if(session!=null)session.close();}catch(Exception ignore){}
        session=null;
        try{if(camera!=null)camera.close();}catch(Exception ignore){}
        camera=null;
        try{if(reader!=null)reader.close();}catch(Exception ignore){}
        reader=null;
        if(cameraThread!=null){cameraThread.quitSafely();cameraThread=null;cameraHandler=null;}
        busy.set(false);
        announced=false;
    }
}