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
import android.graphics.SurfaceTexture;
import android.view.TextureView;
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
        default void onExpression(float yaw,float pitch,float blinkLeft,float blinkRight){}
        default void onFacialMeasurements(float leftEyeOpen,float rightEyeOpen,float mouth,float smile){}
    }
    private final Context context;
    private final Listener listener;
    private final TextureView preview;
    private Surface previewSurface;
    private final com.google.mlkit.vision.face.FaceDetector detector;
    private final AtomicBoolean busy = new AtomicBoolean();
    private CameraDevice camera;
    private CameraCaptureSession session;
    private ImageReader reader;
    private HandlerThread cameraThread;
    private Handler cameraHandler;
    private int rotation=90;
    private int intervalMs=50;
    private float gapNeutral=.008f;
    private float openedSmooth=0f;
    private long lastStatusAt=0;
    private volatile boolean stopped=true;
    private long lastFrame=0, lastFound=0;
    private volatile boolean announced=false;

    public FaceTracker(Context ctx,Listener callback){this(ctx,callback,null);}
    public FaceTracker(Context ctx,Listener callback,TextureView previewView) {
        context=ctx; listener=callback;preview=previewView;
        FaceDetectorOptions opts=new FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
            .setContourMode(FaceDetectorOptions.CONTOUR_MODE_ALL)
            .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
            .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
            .setMinFaceSize(0.14f)
            .build();
        detector=FaceDetection.getClient(opts);
    }
    public void setSaver(boolean saver) {intervalMs=saver?120:50;}
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
                            // Le yaw/pitch est appliqué séparément à la tête 3D ;
                            // ne pas le réinjecter comme déplacement de l'image.
                            float px=clamp(1f-bounds.exactCenterX()/orientedWidth);
                            float py=clamp(bounds.exactCenterY()/orientedHeight);
                            float roll=-f.getHeadEulerAngleZ();
                            float mouth=mouthOpen(f);
                            float lx=eyeClosed(f.getLeftEyeOpenProbability());
                            float rx=eyeClosed(f.getRightEyeOpenProbability());
                            listener.onExpression(clampAngle(yaw,36f),clampAngle(pitch,28f),lx,rx);
                            lastFound=SystemClock.uptimeMillis();
                            listener.onPose(px,py,roll,mouth);
                            Float smile=f.getSmilingProbability();
                            Float leftOpen=f.getLeftEyeOpenProbability();
                            Float rightOpen=f.getRightEyeOpenProbability();
                            listener.onFacialMeasurements(leftOpen==null?-1f:clamp(leftOpen),
                                rightOpen==null?-1f:clamp(rightOpen),mouth,
                                smile==null?-1f:clamp(smile));
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
            java.util.List<Surface> surfaces=new java.util.ArrayList<>();
            surfaces.add(reader.getSurface());
            if(preview!=null&&preview.isAvailable()){
                SurfaceTexture tex=preview.getSurfaceTexture();
                if(tex!=null){
                    tex.setDefaultBufferSize(reader.getWidth(),reader.getHeight());
                    previewSurface=new Surface(tex);
                    surfaces.add(previewSurface);
                }
            }
            camera.createCaptureSession(surfaces,new CameraCaptureSession.StateCallback(){
                @Override public void onConfigured(CameraCaptureSession s){
                    if(stopped){s.close();return;}
                    session=s;
                    try{
                        CaptureRequest.Builder b=camera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
                        b.addTarget(reader.getSurface());
                        if(previewSurface!=null)b.addTarget(previewSurface);
                        b.set(CaptureRequest.CONTROL_AF_MODE,CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_VIDEO);
                        b.set(CaptureRequest.CONTROL_AE_MODE,CaptureRequest.CONTROL_AE_MODE_ON);
                        b.set(CaptureRequest.CONTROL_AE_ANTIBANDING_MODE,CaptureRequest.CONTROL_AE_ANTIBANDING_MODE_AUTO);
                        s.setRepeatingRequest(b.build(),null,cameraHandler);
                    }catch(Exception e){listener.onStatus("Démarrage caméra refusé");}
                }
                @Override public void onConfigureFailed(CameraCaptureSession s){listener.onStatus("Session caméra refusée");}
            },cameraHandler);
        }catch(Exception e){listener.onStatus("Erreur configuration caméra");}
    }
    private static float clamp(float x){return Math.max(0f,Math.min(1f,x));}
    private static float clampAngle(float angle,float magnitude){
        return Math.max(-1f,Math.min(1f,angle/magnitude));
    }
    private static float eyeClosed(Float probability){
        if(probability==null||probability<0f)return 0f;
        return clamp((.85f-probability)*1.5f);
    }
    private static float medianPointY(java.util.List<PointF> pts,float centerX,float tolerance){
        float sum=0f,weights=0f,nearest=Float.MAX_VALUE,nearestY=0f;
        for(PointF p:pts){
            float distance=Math.abs(p.x-centerX);
            if(distance<nearest){nearest=distance;nearestY=p.y;}
            if(distance<tolerance){
                float weight=1f-distance/tolerance;
                sum+=p.y*weight;weights+=weight;
            }
        }
        // Les contours ne présentent pas toujours un point exactement au centre.
        return weights>0f?sum/weights:nearestY;
    }
    /** Ecart reel des levres par les contours ML Kit, normalise par largeur de visage.
      * Le seuil neutre suit lentement l'ouverture minimale observee : evite qu'un
      * sourire fige passe pour une conversation. */
    private float mouthOpen(Face face){
        FaceContour top=face.getContour(FaceContour.UPPER_LIP_BOTTOM);
        FaceContour bottom=face.getContour(FaceContour.LOWER_LIP_TOP);
        if(top==null||bottom==null)return 0f;
        List<PointF> a=top.getPoints(),b=bottom.getPoints();
        if(a.isEmpty()||b.isEmpty())return 0f;
        float center=face.getBoundingBox().exactCenterX();
        float width=Math.max(1f,face.getBoundingBox().width());
        float tolerance=width*.09f;
        float upperY=medianPointY(a,center,tolerance);
        float lowerY=medianPointY(b,center,tolerance);
        float gap=Math.max(0f,(lowerY-upperY)/width);
        if(gap<gapNeutral)gapNeutral=gapNeutral*.80f+gap*.20f;
        else gapNeutral=gapNeutral*.998f+gap*.002f;
        float open=clamp((gap-gapNeutral-.0018f)*33.0f);
        // Ouverture rapide et retour fluide au repos, même sur la voix parlée.
        openedSmooth+=(open-openedSmooth)*(open>openedSmooth?.72f:.53f);
        return openedSmooth;
    }
    public synchronized void stop(){
        stopped=true;
        try{if(session!=null)session.close();}catch(Exception ignore){}
        session=null;
        try{if(camera!=null)camera.close();}catch(Exception ignore){}
        camera=null;
        try{if(reader!=null)reader.close();}catch(Exception ignore){}
        try{if(previewSurface!=null)previewSurface.release();}catch(Exception ignore){}
        previewSurface=null;
        reader=null;
        if(cameraThread!=null){cameraThread.quitSafely();cameraThread=null;cameraHandler=null;}
        busy.set(false);
        announced=false;
        gapNeutral=.008f;openedSmooth=0f;
    }
}