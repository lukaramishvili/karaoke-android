package ge.mygpi.karaoke;

import java.io.File;
import java.io.IOException;
import java.util.Date;

import android.app.Activity;
import android.content.Context;
import android.hardware.Camera;
import android.media.CamcorderProfile;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.os.Environment;
import android.os.StatFs;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.Toast;
import com.facebook.FacebookSdk;

public class MainActivity extends Activity{
    //doc: http://bit.ly/1QEum1E

    private Camera myCamera;
    private MyCameraSurfaceView myCameraSurfaceView;
    private MediaRecorder mediaRecorder;

    Button record_button;
    Button flip_button;
    SurfaceHolder surfaceHolder;
    boolean recording;

    Long recordingId = (long) 0;

    float nFreeSpaceAvailable = -1;
    static final String LOCATION_NA = "n/a";
    static final String LOCATION_INTERNAL = "internal";
    static final String LOCATION_EXTERNAL = "external";
    String saveLocationType = MainActivity.LOCATION_NA;
    File saveDir;
    public static float bytesAvailable(File f) {
        StatFs stat = new StatFs(f.getPath());
        long bytesAvailable = (long)stat.getBlockSize() * (long)stat.getAvailableBlocks();
        return bytesAvailable;// for megabytes, return bytesAvailable / (1024.f * 1024.f);
    }
    private void detectBestSaveLocation(){
        Context context = getApplicationContext();
        if(Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED)){
            saveLocationType = LOCATION_EXTERNAL;
            saveDir = context.getExternalFilesDir(Environment.DIRECTORY_MOVIES);
            nFreeSpaceAvailable = bytesAvailable(saveDir);
        } else {
            saveLocationType = LOCATION_INTERNAL;
            saveDir = context.getFilesDir();
            nFreeSpaceAvailable = bytesAvailable(saveDir);
        }
    }
    private void toast(String message){
        Toast.makeText(MainActivity.this, message, Toast.LENGTH_LONG).show();
    }
    private void notifyNotEnoughFreeSpace(){
        toast("Please free up space before recording");
    }
    private boolean fFreeSpaceIsEnough(){
        return nFreeSpaceAvailable > 50*000*000;
    }

    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        FacebookSdk.sdkInitialize(getApplicationContext());

        //also sets nFreeSpaceAvailable
        detectBestSaveLocation();
        if(!fFreeSpaceIsEnough()) {
            notifyNotEnoughFreeSpace();
        }

        /*File mydir = saveDir;
        File[] files = mydir.listFiles();
        String msg="";
        int c = 0;
        for(int i = 0; i < files.length; i++){
            msg+=(files[i].getName() + files[i].getPath());c++;
        }
        toast(c+msg);*/

        recording = false;

        setContentView(R.layout.activity_main);

        //Get Camera for preview
        myCamera = getCameraInstance();
        if(myCamera == null){
            Toast.makeText(MainActivity.this, "Failed to get Camera", Toast.LENGTH_LONG).show();
        }

        myCameraSurfaceView = new MyCameraSurfaceView(this, myCamera);
        FrameLayout myCameraPreview = (FrameLayout)findViewById(R.id.videoview);
        myCameraPreview.addView(myCameraSurfaceView);

        record_button = (Button)findViewById(R.id.record_button);
        record_button.setOnClickListener(recordButtonOnClickListener);
        
        flip_button = (Button)findViewById(R.id.record_button);
        flip_button.setOnClickListener(flipButtonOnClickListener);
    }

    Button.OnClickListener recordButtonOnClickListener
            = new Button.OnClickListener(){

        @Override
        public void onClick(View v) {

            if(!fFreeSpaceIsEnough()) {
                notifyNotEnoughFreeSpace();
                return;
            }

            if(recording){
                // stop recording and release camera
                mediaRecorder.stop();  // stop the recording

                releaseMediaRecorder(); // release the MediaRecorder object

                record_button.setText("START");
                //Exit after saved
                //finish();

                // TODO: 4/29/16 upload file here
                //
            }else{

                //Release Camera before MediaRecorder start
                releaseCamera();

                if(!prepareMediaRecorder()){
                    Toast.makeText(MainActivity.this,
                            "Fail in prepareMediaRecorder()!\n - Ended -",
                            Toast.LENGTH_LONG).show();
                    finish();
                }

                mediaRecorder.start();
                recording = true;
                record_button.setText("STOP");
            }
        }};
    
    Button.OnClickListener flipButtonOnClickListener
            = new Button.OnClickListener(){
        @Override
        public void onClick(View v) {
            // TODO: 4/29/16 implement flip button
        }
    };

    private Camera getCameraInstance(){
        // TODO Auto-generated method stub
        Camera c = null;
        try {
            c = Camera.open(); // attempt to get a Camera instance
            c.setDisplayOrientation(90);
        }
        catch (Exception e){
            // Camera is not available (in use or does not exist)
        }
        return c; // returns null if camera is unavailable
    }

    private boolean prepareMediaRecorder(){
        myCamera = getCameraInstance();
        mediaRecorder = new MediaRecorder();

        myCamera.unlock();
        mediaRecorder.setCamera(myCamera);

        mediaRecorder.setAudioSource(MediaRecorder.AudioSource.CAMCORDER);
        mediaRecorder.setVideoSource(MediaRecorder.VideoSource.CAMERA);

        mediaRecorder.setProfile(CamcorderProfile.get(CamcorderProfile.QUALITY_HIGH));
        //mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
        //mediaRecorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264);

        Date now = new Date();
        recordingId = Long.valueOf(now.getTime());
        mediaRecorder.setOutputFile(saveDir + "/" + recordingId.toString() + ".mp4");
        mediaRecorder.setMaxDuration(10*60*000); // Set max duration 10 min.
        mediaRecorder.setMaxFileSize(300*000*000); // Set max file size 300M

        mediaRecorder.setPreviewDisplay(myCameraSurfaceView.getHolder().getSurface());
        mediaRecorder.setOrientationHint(90);

        try {
            mediaRecorder.prepare();

        } catch (IllegalStateException e) {
            releaseMediaRecorder();
            return false;
        } catch (IOException e) {
            releaseMediaRecorder();
            return false;
        }
        return true;

    }

    @Override
    protected void onPause() {
        super.onPause();
        releaseMediaRecorder();       // if you are using MediaRecorder, release it first
        releaseCamera();              // release the camera immediately on pause event
    }

    private void releaseMediaRecorder(){
        if (mediaRecorder != null) {
            mediaRecorder.reset();   // clear recorder configuration
            mediaRecorder.release(); // release the recorder object
            mediaRecorder = null;
            myCamera.lock();           // lock camera for later use
        }
    }

    private void releaseCamera(){
        if (myCamera != null){
            myCamera.release();        // release the camera for other applications
            myCamera = null;
        }
    }

    public class MyCameraSurfaceView extends SurfaceView implements SurfaceHolder.Callback{

        private SurfaceHolder mHolder;
        private Camera mCamera;

        public MyCameraSurfaceView(Context context, Camera camera) {
            super(context);
            mCamera = camera;

            // Install a SurfaceHolder.Callback so we get notified when the
            // underlying surface is created and destroyed.
            mHolder = getHolder();
            mHolder.addCallback(this);
            // deprecated setting, but required on Android versions prior to 3.0
            mHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
        }

        @Override
        public void surfaceChanged(SurfaceHolder holder, int format, int weight,
                                   int height) {
            // If your preview can change or rotate, take care of those events here.
            // Make sure to stop the preview before resizing or reformatting it.

            if (mHolder.getSurface() == null){
                // preview surface does not exist
                return;
            }

            // stop preview before making changes
            try {
                mCamera.stopPreview();
            } catch (Exception e){
                // ignore: tried to stop a non-existent preview
            }

            // make any resize, rotate or reformatting changes here

            // start preview with new settings
            try {
                mCamera.setPreviewDisplay(mHolder);
                mCamera.startPreview();

            } catch (Exception e){
            }
        }

        @Override
        public void surfaceCreated(SurfaceHolder holder) {
            // TODO Auto-generated method stub
            // The Surface has been created, now tell the camera where to draw the preview.
            try {
                mCamera.setPreviewDisplay(holder);
                mCamera.startPreview();
            } catch (IOException e) {
            }
        }

        @Override
        public void surfaceDestroyed(SurfaceHolder holder) {
            // TODO Auto-generated method stub

        }
    }
}