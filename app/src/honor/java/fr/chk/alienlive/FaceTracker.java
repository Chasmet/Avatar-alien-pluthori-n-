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
import android.os.Build;
import android.os.Looper;
import android.util.Log;
import com.google.android.gms.tasks.Tasks;
import java.util.List;


/** Suivi facial local pour le live et la calibration sur smartphone. */
public final class FaceTracker {
    public interface Listener {
        void onPose(float x,float y,float roll,float mouth);
        void onStatus(String status);
        default void onGaze(float x,float y){}
        default void onEngine(String name){}
        default void onExpression(float yaw,float pitch,float blinkLeft,float blinkRight){}
        default void onCheeksAndLips(float left,float right,float rounded,boolean lipContours){}
        default void onFacialMeasurements(float leftEyeOpen,float rightEyeOpen,float mouth,float smile){}
    }
    private final Context context;
    private final Listener listener;
    private final TextureView preview;
    private Surface previewSurface;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private Pipeline pipeline;
    private CameraDevice camera;
    private CameraCaptureSession session;
    private ImageReader reader;
    private HandlerThread cameraThread;
    private Handler cameraHandler;
    private int rotation=90;
    private volatile int intervalMs=50;

    private volatile int generation;

    private volatile boolean stopped=true;
    private long lastFrame;


    public FaceTracker(Context ctx,Listener callback){this(ctx,callback,null);}
    public FaceTracker(Context ctx,Listener callback,TextureView previewView) {
        context=ctx; listener=callback;preview=previewView;
    }
    // Owned by one camera handler, including initialization, inference and release.
    private final class Pipeline {
        final int id;
        final ContourExpressions contours=new ContourExpressions();
        com.google.mlkit.vision.face.FaceDetector fallback;
        MediaPipeTracker mobile;
        boolean preferMobile, announced;
        long lastFound;
        Pipeline(int id){
            this.id=id;
            preferMobile=Build.VERSION.SDK_INT>=24 && AlienData.prefs(context).getBoolean("mediaPipeFace",true);
        }
        void publish(Runnable update){
            ui.post(()->{if(!stopped && generation==id)update.run();});
        }
        void process(Image img,long now) throws Exception {
            if(preferMobile){
                try{
                    if(mobile==null){
                        mobile=new MediaPipeTracker(context);
                        publish(()->listener.onEngine("MediaPipe"));
                    }
                    MediaPipeTracker.Frame f=mobile.detect(img,rotation,now);
                    if(f==null){missing(now);return;}
                    publish(()->{
                        listener.onExpression(clampAngle(f.yaw,36f),clampAngle(f.pitch,28f),f.blinkLeft,f.blinkRight);
                        listener.onGaze(f.gazeX,f.gazeY);
                        listener.onPose(clamp(f.x),clamp(f.y),f.roll,f.mouth);
                        listener.onCheeksAndLips(f.cheekLeft,f.cheekRight,f.rounded,true);
                        listener.onFacialMeasurements(1f-f.blinkLeft,1f-f.blinkRight,f.mouth,f.smile);
                    });
                    found(now,"MediaPipe");
                    return;
                }catch(RuntimeException | LinkageError error){
                    Log.w("CHK-Face","MediaPipe indisponible, passage au suivi local ML Kit",error);
                    preferMobile=false;
                    if(mobile!=null){mobile.close();mobile=null;}
                    publish(()->listener.onGaze(0f,0f));
                    announced=false;
                }
            }
            if(fallback==null){
                FaceDetectorOptions opts=new FaceDetectorOptions.Builder()
                    .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
                    .setContourMode(FaceDetectorOptions.CONTOUR_MODE_ALL)
                    .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
                    .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
                    .setMinFaceSize(.14f).build();
                fallback=FaceDetection.getClient(opts);
                publish(()->listener.onEngine("ML Kit"));
            }
            List<Face> faces=Tasks.await(fallback.process(InputImage.fromMediaImage(img,rotation)));
            if(faces.isEmpty()){missing(now);return;}
            Face f=faces.get(0);
            Rect bounds=f.getBoundingBox();
            boolean rotate=rotation==90||rotation==270;
            float px=clamp(1f-bounds.exactCenterX()/(rotate?img.getHeight():img.getWidth()));
            float py=clamp(bounds.exactCenterY()/(rotate?img.getWidth():img.getHeight()));
            float yaw=f.getHeadEulerAngleY(),pitch=f.getHeadEulerAngleX(),roll=-f.getHeadEulerAngleZ();
            Float probability=f.getSmilingProbability();
            float smile=probability==null?-1f:clamp(probability);
            contours.update(points(f,FaceContour.LEFT_EYE),points(f,FaceContour.RIGHT_EYE),
                points(f,FaceContour.UPPER_LIP_BOTTOM),points(f,FaceContour.LOWER_LIP_TOP),
                points(f,FaceContour.LEFT_CHEEK),points(f,FaceContour.RIGHT_CHEEK),points(f,FaceContour.NOSE_BOTTOM),
                f.getLeftEyeOpenProbability(),f.getRightEyeOpenProbability(),yaw,pitch,smile,now);
            float l=contours.eyeLeft,r=contours.eyeRight,mouth=contours.mouth;
            float cl=contours.cheekLeft,cr=contours.cheekRight,round=contours.rounded;
            boolean lips=contours.lipContours;
            publish(()->{
                listener.onExpression(clampAngle(yaw,36f),clampAngle(pitch,28f),eyeClosed(l),eyeClosed(r));
                listener.onPose(px,py,roll,mouth);
                listener.onCheeksAndLips(cl,cr,round,lips);
                listener.onFacialMeasurements(l,r,mouth,smile);
            });
            found(now,"ML Kit");
        }
        void found(long now,String engine){
            lastFound=now;
            if(!announced){
                announced=true;
                publish(()->listener.onStatus("Visage suivi • "+engine));
            }
        }
        void missing(long now){
            if(announced && now-lastFound>450L){
                announced=false;
                contours.reset();
                publish(()->{
                    listener.onCheeksAndLips(0f,0f,0f,false);
                    listener.onGaze(0f,0f);
                    listener.onStatus("Visage non détecté : regarde la caméra");
                });
            }
        }
        void close(){
            if(mobile!=null)mobile.close();
            if(fallback!=null)fallback.close();
        }
    }
    public void setSaver(boolean saver) {intervalMs=saver?120:50;}
    @SuppressLint("MissingPermission")
    public synchronized void start(){
        if(!stopped)return;
        stopped=false;
        final int activeGeneration=++generation;
        final Pipeline tracking=new Pipeline(activeGeneration);
        pipeline=tracking;
        cameraThread=new HandlerThread("chk-face-camera");
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
                if(stopped || generation!=activeGeneration)return;
                try(Image img=r.acquireLatestImage()){
                    if(img==null)return;
                    long now=SystemClock.uptimeMillis();
                    if(now-lastFrame<intervalMs)return;
                    lastFrame=now;
                    tracking.process(img,now);
                }catch(Exception e){
                    Log.w("CHK-Face","Image caméra indisponible",e);
                    tracking.missing(SystemClock.uptimeMillis());
                }
            },cameraHandler);
            mgr.openCamera(front,new CameraDevice.StateCallback(){
                @Override public void onOpened(CameraDevice c){if(stopped||activeGeneration!=generation){c.close();return;}camera=c;configure();}
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
    private static List<PointF> points(Face face,int type){
        FaceContour contour=face.getContour(type);
        return contour==null?java.util.Collections.emptyList():contour.getPoints();
    }
    public synchronized void stop(){
        stopped=true;
        try{if(session!=null)session.close();}catch(Exception ignore){}
        session=null;
        try{if(camera!=null)camera.close();}catch(Exception ignore){}
        camera=null;
        ImageReader previousReader=reader;
        Pipeline previousPipeline=pipeline;
        if(cameraHandler!=null)cameraHandler.post(()->{
            try{if(previousPipeline!=null)previousPipeline.close();}
            finally{if(previousReader!=null)previousReader.close();}
        });
        pipeline=null;
        try{if(previewSurface!=null)previewSurface.release();}catch(Exception ignore){}
        previewSurface=null;
        reader=null;
        if(cameraThread!=null){cameraThread.quitSafely();cameraThread=null;cameraHandler=null;}

    }
}