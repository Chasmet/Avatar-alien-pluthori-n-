package fr.chk.alienlive;

import android.content.Context;
import android.media.Image;
import com.google.mediapipe.framework.image.MPImage;
import com.google.mediapipe.tasks.core.BaseOptions;
import com.google.mediapipe.tasks.vision.core.RunningMode;
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarker;
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarkerResult;
import com.google.mediapipe.tasks.components.containers.Category;
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark;
import java.util.List;

/** One video-mode graph per camera session. Synchronous inference on the
 * camera worker provides backpressure and guarantees buffer lifetimes. */
final class MediaPipeTracker implements AutoCloseable {
    private final FaceLandmarker landmarker;
    private final UprightCameraFrame rgb = new UprightCameraFrame();

    MediaPipeTracker(Context context) {
        FaceLandmarker.FaceLandmarkerOptions options = FaceLandmarker.FaceLandmarkerOptions.builder()
            .setBaseOptions(BaseOptions.builder().setModelAssetPath("face_landmarker.task").build())
            .setRunningMode(RunningMode.VIDEO).setNumFaces(1)
            .setOutputFaceBlendshapes(true).setOutputFacialTransformationMatrixes(true).build();
        landmarker = FaceLandmarker.createFromOptions(context, options);
    }

    Frame detect(Image image, int rotation, long timestamp) {
        try (MPImage input = rgb.convert(image, rotation)) {
            return detect(input, timestamp);
        }
    }

    Frame detect(MPImage input, long timestamp) {
        FaceLandmarkerResult result = landmarker.detectForVideo(input,timestamp);
        if (result.faceLandmarks().isEmpty()) return null;
        List<NormalizedLandmark> points = result.faceLandmarks().get(0);
        if (!result.faceBlendshapes().isPresent() || !result.facialTransformationMatrixes().isPresent())
            throw new IllegalStateException("Résultat du modèle facial incomplet");
        Frame f = new Frame();
        // Forehead, chin and temples yield a stable head centre independent of mouth movement.
        f.x = 1f - (points.get(234).x()+points.get(454).x())*.5f;
        f.y = (points.get(10).y()+points.get(152).y())*.5f;
        float[] m = result.facialTransformationMatrixes().get().get(0);
        // Column-major, OpenGL space (Y up, +Z towards viewer).
        f.yaw = degrees(Math.atan2(m[8],m[10]));
        f.pitch = degrees(Math.atan2(m[9],Math.hypot(m[8],m[10])));
        f.roll = -degrees(Math.atan2(m[1],m[0]));
        float close = 0f, smileL = 0f, smileR = 0f, squintL = 0f, squintR = 0f;
        for (Category c : result.faceBlendshapes().get().get(0)) {
            float score = c.score();
            switch (c.categoryName()) {
                case "jawOpen" -> f.mouth = score;
                case "mouthClose" -> close = score;
                case "mouthPucker", "mouthFunnel" -> f.rounded = Math.max(f.rounded,score);
                case "mouthSmileLeft" -> smileL = score;
                case "mouthSmileRight" -> smileR = score;
                case "eyeBlinkLeft" -> f.blinkLeft = score;
                case "eyeBlinkRight" -> f.blinkRight = score;
                case "cheekSquintLeft" -> squintL = score;
                case "cheekSquintRight" -> squintR = score;
                case "eyeLookInLeft", "eyeLookOutRight" -> f.gazeX -= score*.5f;
                case "eyeLookOutLeft", "eyeLookInRight" -> f.gazeX += score*.5f;
                case "eyeLookUpLeft", "eyeLookUpRight" -> f.gazeY += score*.5f;
                case "eyeLookDownLeft", "eyeLookDownRight" -> f.gazeY -= score*.5f;
                default -> { }
            }
        }
        // Inner lip separation keeps a clenched jaw with parted lips expressive
        // and prevents the avatar talking through sealed lips.
        float gap = separation(points.get(13),points.get(14),input.getWidth(),input.getHeight());
        float span = separation(points.get(33),points.get(263),input.getWidth(),input.getHeight());
        float visible = unit((gap/Math.max(1f,span)-.009f)*4.8f);
        f.mouth = Math.min(unit(visible*1.8f),Math.max(visible,f.mouth*(1f-close)));
        f.cheekLeft = Math.max(squintL,smileL*.55f);
        f.cheekRight = Math.max(squintR,smileR*.55f);
        f.smile = Math.max(smileL,smileR);
        return f;
    }
    private static float separation(NormalizedLandmark a, NormalizedLandmark b,int w,int h) {
        return (float)Math.hypot((a.x()-b.x())*w,(a.y()-b.y())*h);
    }
    private static float degrees(double radians){return (float)Math.toDegrees(radians);}
    private static float unit(float n){return Math.max(0f,Math.min(1f,n));}
    static final class Frame {
        float x,y,roll,yaw,pitch,mouth,rounded,blinkLeft,blinkRight,smile,cheekLeft,cheekRight,gazeX,gazeY;
    }
    @Override public void close(){landmarker.close();}
}
