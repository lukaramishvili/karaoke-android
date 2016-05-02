package ge.mygpi.karaoke;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Arrays;
import java.util.Date;
import java.util.Iterator;
import java.util.List;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.hardware.Camera;
import android.media.CamcorderProfile;
import android.media.MediaPlayer;
import android.media.MediaRecorder;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.FileObserver;
import android.os.StatFs;
import android.provider.MediaStore;
import android.provider.Settings;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.MediaController;
import android.widget.Toast;
import android.widget.VideoView;

import com.facebook.CallbackManager;
import com.facebook.FacebookCallback;
import com.facebook.FacebookException;
import com.facebook.FacebookSdk;
import com.facebook.GraphRequest;
import com.facebook.GraphResponse;
import com.facebook.login.LoginResult;
import com.facebook.login.widget.LoginButton;

import org.json.JSONException;
import org.json.JSONObject;

import javax.net.ssl.HttpsURLConnection;

public class MainActivity extends Activity{
    //doc: http://bit.ly/1QEum1E

    CallbackManager callbackManager;

    private boolean onCreateCalled = false;

    private Camera myCamera;
    private int camId = Camera.CameraInfo.CAMERA_FACING_BACK;
    private MyCameraSurfaceView myCameraSurfaceView;
    private MediaRecorder mediaRecorder;

    int REQUEST_TAKE_GALLERY_VIDEO = 101;

    private LoginButton loginButton;

    boolean loggedIn = false;
    String UserId = "";

    public ProgressDialog uploadProgress;

    ImageButton record_button;
    ImageButton upload_button;
    ImageButton flip_button;
    ImageButton gpi_logo;
    ImageButton mygpi_logo;
    SurfaceHolder surfaceHolder;
    boolean recording;

    CustomVideoView lyricsVideo;
    boolean fVideoLoaded = false;

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
    private String getVideoSavePath(Long id){
        return saveDir + "/" + recordingId.toString() + ".mp4";
    }
    public void toast(String message){
        Toast.makeText(MainActivity.this, message, Toast.LENGTH_LONG).show();
    }
    public void openUrl(String url) {
        Intent browse = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
        startActivity(browse);
    }
    private void notifyNotEnoughFreeSpace(){
        toast("Please free up space before recording");
    }
    private boolean fFreeSpaceIsEnough(){
        return nFreeSpaceAvailable > 50*000*000;
    }

    private void prepareCamera() {
        myCamera = getCameraInstance(camId);
        if (myCamera == null) {
            toast("Failed to get Camera");
        }

        myCameraSurfaceView = new MyCameraSurfaceView(this, myCamera);
        FrameLayout myCameraPreview = (FrameLayout) findViewById(R.id.cameraPreview);
        myCameraPreview.addView(myCameraSurfaceView);
    }

    public String getPath(Uri uri) {
        String[] filePathColumn = {MediaStore.Images.Media.DATA};
        Cursor cursor = getContentResolver().query(uri, filePathColumn, null, null, null);

        int columnindex = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA);
        cursor.moveToFirst();
        String file_path = cursor.getString(columnindex);
        Log.e(getClass().getName(), "file_path" + file_path);
        Uri fileUri = Uri.parse("file://" + file_path);
        cursor.close();
        return file_path;
    }

    private void uploadVideoFromPath(final String path){
        new Thread(new Runnable() {
            public void run() {
                //this will also create and show the uploadProgress dialog
                FileUploader.uploadVideo(UserId, path, MainActivity.this, new UploadCallback() {
                    @Override
                    public void run() {
                        String msg = "File Upload Completed.\n\n See uploaded file here : \n\n"
                                + " https://karaoke.mygpi.ge/Video/" + this.serverResponseText;

                        LinkedAlertDialog.create(MainActivity.this.getApplicationContext(),
                                "ვიდეო ატვირთულია", "გამოსვლა", msg);
                    }
                });
            }
        }).start();
    }

    @Override
    protected void onActivityResult(final int requestCode, final int resultCode, final Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_TAKE_GALLERY_VIDEO) {
            if(resultCode == RESULT_OK) {
                if (data.getData() != null) {
                    Uri selectedImage = data.getData();
                    String path = getPath(selectedImage);

                    uploadVideoFromPath(path);
                } else {
                    Toast.makeText(getApplicationContext(), "Failed to select video", Toast.LENGTH_LONG).show();
                }
            } else {
                //Video upload cancelled by the user
            }
        } else {
            callbackManager.onActivityResult(requestCode, resultCode, data);
        }
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

        recording = false;

        setContentView(R.layout.activity_main);

        callbackManager = CallbackManager.Factory.create();
        loginButton = (LoginButton)findViewById(R.id.login_button);
        loginButton.setReadPermissions(Arrays.asList(
                "public_profile", "email", "user_friends"));
        loginButton.registerCallback(callbackManager, new FacebookCallback<LoginResult>() {
            @Override
            public void onSuccess(final LoginResult loginResult) {
                //access token: loginResult.getAccessToken().getToken()
                GraphRequest request = GraphRequest.newMeRequest(
                        loginResult.getAccessToken(),
                        new GraphRequest.GraphJSONObjectCallback() {
                            @Override
                            public void onCompleted(JSONObject object, GraphResponse response) {
                                try {
                                    // Application code
                                    final String id = object.getString("id");
                                    final String name = object.getString("name");
                                    final String email = object.getString("email");
                                    final String avatar = object.getJSONObject("picture")
                                            .getJSONObject("data")
                                            .getString("url");
                                    final String cover = object.getJSONObject("cover").getString("source");
                                    //start POST data to server
                                    new Thread(new Runnable() {
                                        public void run() {
                                            try {
                                                URL urlPostUserData = new URL("https://karaoke.mygpi.ge/Account/ExternalSignupDevice");
                                                HttpsURLConnection connPostUserData = (HttpsURLConnection)urlPostUserData.openConnection();
                                                connPostUserData.setRequestMethod("POST");
                                                connPostUserData.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
                                                connPostUserData.setDoOutput(true);
                                                OutputStream wr = connPostUserData.getOutputStream();
                                                String urlParameters = "id=" + id + "&name=" + name + "&email=" + email + "&avatar=" + avatar + "&cover=" + cover + "&accesstoken=" + loginResult.getAccessToken().getToken();
                                                wr.write(urlParameters.getBytes("UTF-8"));
                                                connPostUserData.connect();
                                                BufferedReader br = new BufferedReader(new InputStreamReader(connPostUserData.getInputStream()));
                                                String input;
                                                String response = "";
                                                while ((input = br.readLine()) != null) {
                                                    response += input;
                                                }
                                                try {
                                                    JSONObject jsonResponse = new JSONObject(response);
                                                    MainActivity.this.loggedIn = true;
                                                    MainActivity.this.UserId = jsonResponse.getString("UserId");
                                                } catch(JSONException e){
                                                    runOnUiThread(new Runnable(){public void run(){toast("When authorizing, server returned malformed json.");}});
                                                }
                                                br.close();
                                            } catch (MalformedURLException e) {
                                                runOnUiThread(new Runnable(){public void run(){toast("Malformed url exception.");}});
                                            } catch (IOException e) {
                                                runOnUiThread(new Runnable(){public void run(){toast("sending i/o error. try again.");}});
                                            }
                                        }
                                    }).start();
                                    //end POST data to server
                                } catch (JSONException e) {
                                    toast("Incorrect answer from Facebook.");
                                }
                            }
                        });
                Bundle parameters = new Bundle();
                parameters.putString("fields", "id,name,email,picture.type(large),cover");
                request.setParameters(parameters);
                request.executeAsync();
            }

            @Override
            public void onCancel() {
                toast("Facebook Login was cancelled.");
            }

            @Override
            public void onError(FacebookException exception) {
                toast("Facebook error: " + exception.getMessage());//also there's exception.getCause()
            }
        });


        //start setup video
        lyricsVideo = (CustomVideoView) findViewById(R.id.lyricsVideo);
        //COMMENTED: don't add controls
        //lyricsVideo.setMediaController(new MediaController(this));
        lyricsVideo.requestFocus();
        lyricsVideo.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
            @Override
            public void onPrepared(MediaPlayer mp) {
                fVideoLoaded = true;
            }
        });
        lyricsVideo.setVideoURI(Uri.parse("https://beluxhome.com/recordvideo/source.mp4"));
        //end setup video

        prepareCamera();

        record_button = (ImageButton)findViewById(R.id.record_button);
        record_button.setOnClickListener(recordButtonOnClickListener);

        upload_button = (ImageButton)findViewById(R.id.upload_button);
        upload_button.setOnClickListener(uploadButtonOnClickListener);

        flip_button = (ImageButton)findViewById(R.id.flip_button);
        flip_button.setOnClickListener(flipButtonOnClickListener);

        gpi_logo = (ImageButton)findViewById(R.id.gpi_logo);
        gpi_logo.setOnClickListener(gpiLogoOnClickListener);

        mygpi_logo = (ImageButton)findViewById(R.id.mygpi_logo);
        mygpi_logo.setOnClickListener(mygpiLogoOnClickListener);

        onCreateCalled = true;
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
                //first, set up monitoring file save; actual recorder stopping below

                //wait for MediaRecorder finishing saving file
                FileObserver fo = new FileObserver(saveDir.getAbsolutePath(), FileObserver.CLOSE_WRITE){
                    @Override
                    public void onEvent(int event, String path) {
                        if(path.equals((new File(getVideoSavePath(recordingId))).getName())) {
                            stopWatching();
                            // TODO: 4/29/16 show video with play button in the middle
                            // TODO: 4/29/16 also show two additional buttons: upload and retake
                            // TODO: 4/29/16 only call the following upload code if user clicked upload
                            //start upload code
                            new Thread(new Runnable() {
                                public void run() {
                                    //this will also create and show the uploadProgress dialog
                                    uploadVideoFromPath(getVideoSavePath(recordingId));
                                }
                            }).start();
                            //end upload code
                        }
                    }
                };
                fo.startWatching();


                // stop recording and release camera
                mediaRecorder.stop();  // stop the recording
                releaseMediaRecorder(); // release the MediaRecorder object
                //reset button text
                //record_button.setText(R.string.start_record_label);
                // TODO: 4/30/16 change image to btn_stop_recording
                toast("Video recorded");

                lyricsVideo.stopPlayback();

            } else {

                //Release Camera before MediaRecorder start
                releaseCamera();

                if(!prepareMediaRecorder()){
                    toast("Fail in prepareMediaRecorder()!\n - Ended -");
                    finish();
                }

                mediaRecorder.start();
                recording = true;
                //record_button.setText(R.string.stop_record_label);
                // TODO: 4/30/16 change image to btn_record_video

                //play video simultaneously while recording
                if(fVideoLoaded) {
                    lyricsVideo.setPlayPauseListener(new CustomVideoView.PlayPauseListener() {

                        @Override
                        public void onPlay() {
                            //play callback
                            // TODO: 4/30/16 if the video loads too slow and doesn't match recording start,
                            // TODO: 4/30/16 |->then move above recording start code here
                        }

                        @Override
                        public void onPause() {
                            //paused callback
                        }
                    });
                    lyricsVideo.start();
                } else {
                    toast("Video not loaded yet");
                }
            }
        }};

    Button.OnClickListener flipButtonOnClickListener
            = new Button.OnClickListener(){
        @Override
        public void onClick(View v) {
            //only allow switching when not recording
            if(!recording) {
                switchCamera();
            }
        }
    };

    Button.OnClickListener uploadButtonOnClickListener
            = new Button.OnClickListener(){
        @Override
        public void onClick(View v) {
            // TODO: 4/30/16 check login and then allow upload only if the user has no videos yet
            Intent intent = new Intent();
            intent.setType("video/*");
            intent.setAction(Intent.ACTION_GET_CONTENT);
            startActivityForResult(Intent.createChooser(intent,"Select Video"),REQUEST_TAKE_GALLERY_VIDEO);
        }
    };

    Button.OnClickListener gpiLogoOnClickListener
            = new Button.OnClickListener(){
        @Override
        public void onClick(View v) {
            openUrl("https://gpih.ge");
        }
    };

    Button.OnClickListener mygpiLogoOnClickListener
            = new Button.OnClickListener(){
        @Override
        public void onClick(View v) {
            openUrl("https://mygpi.ge");
        }
    };

    private Camera getCameraInstance(int cameraId){
        Camera c = null;
        try {
            c = Camera.open(cameraId); // attempt to get a Camera instance
            c.setDisplayOrientation(90);
        }
        catch (Exception e){
            // Camera is not available (in use or does not exist)
        }
        return c; // returns null if camera is unavailable
    }

    private void switchCamera(){
        //only continue if we have multiple cameras
        if (Camera.getNumberOfCameras() > 1) {
            if (camId == Camera.CameraInfo.CAMERA_FACING_BACK) {
                camId = Camera.CameraInfo.CAMERA_FACING_FRONT;
            } else {
                camId = Camera.CameraInfo.CAMERA_FACING_BACK;
            }
            resetCamera();
        }
    }

    private void resetCamera(){
        if(myCamera != null) {
            releaseCamera();
        }
        FrameLayout myCameraPreview = (FrameLayout) findViewById(R.id.cameraPreview);
        myCameraPreview.removeView(myCameraSurfaceView);
        myCameraSurfaceView = null;
        prepareCamera();
    }

    private boolean prepareMediaRecorder(){

        myCamera = getCameraInstance(camId);
        mediaRecorder = new MediaRecorder();

        myCamera.unlock();
        mediaRecorder.setCamera(myCamera);

        mediaRecorder.setAudioSource(MediaRecorder.AudioSource.CAMCORDER);
        mediaRecorder.setVideoSource(MediaRecorder.VideoSource.CAMERA);

        /*if(camId == Camera.CameraInfo.CAMERA_FACING_BACK) {
            mediaRecorder.setProfile(CamcorderProfile.get(CamcorderProfile.QUALITY_HIGH));
        } else {
            mediaRecorder.setProfile(CamcorderProfile.get(CamcorderProfile.QUALITY_LOW));
        }*/
        mediaRecorder.setProfile(CamcorderProfile.get(CamcorderProfile.QUALITY_480P));

        //mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
        //mediaRecorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264);

        Date now = new Date();
        recordingId = Long.valueOf(now.getTime());
        mediaRecorder.setOutputFile(getVideoSavePath(recordingId));
        mediaRecorder.setMaxDuration(5*60*1000); // Set max duration 5 min.
        mediaRecorder.setMaxFileSize(300*1000*1000); // Set max file size 300M

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

    @Override
    protected void onResume() {
        super.onResume();
        if(onCreateCalled) {
            if (myCamera == null) {
                prepareCamera();
            }
        }
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
            myCamera.stopPreview();
            myCamera.setPreviewCallback(null);
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
                //start resize preview frame to the aspect ratio of video
                int previewWidth = this.getWidth();
                int previewHeight = this.getHeight();
                // Get the width of the screen
                int screenWidth = getWindowManager().getDefaultDisplay().getWidth();
                int screenHeight = getWindowManager().getDefaultDisplay().getHeight();
                float screenProportion = (float) screenWidth / (float) screenHeight;
                Camera.Parameters camParams = mCamera.getParameters();
                List<Camera.Size> sizes = camParams.getSupportedPreviewSizes();
                Camera.Size maxSize = sizes.get(0);
                for(Iterator<Camera.Size> i = sizes.iterator(); i.hasNext(); ) {
                    Camera.Size curSize = i.next();
                    if(curSize.width > maxSize.width){
                        maxSize = curSize;
                    }
                }
                float videoProportion = (float) maxSize.width / (float) maxSize.height;
                ViewGroup.LayoutParams lp = this.getLayoutParams();
            /*if (videoProportion > screenProportion) {
                lp.width = previewWidth;
                lp.height = (int) ((float) previewWidth / videoProportion);
            } else {
                lp.width = (int) (videoProportion * (float) previewHeight);
                lp.height = previewHeight;
            }*/
                //getSupportedPreviewSizes() reports 1920x1080 even when the video is 1080x1920;
                //our app only has portrait mode, so only consider that situation
                lp.height = (int) ((float) previewWidth * videoProportion);
                this.setLayoutParams(lp);
                //end resize preview frame to the aspect ratio of video

                mCamera.setPreviewDisplay(mHolder);
                mCamera.startPreview();

            } catch (Exception e){
            }
        }

        @Override
        public void surfaceCreated(SurfaceHolder holder) {
            // The Surface has been created, now tell the camera where to draw the preview.
            //COMMENTED; caused NullException on resume
            /*try {
                //mCamera.setPreviewDisplay(holder);
                //mCamera.startPreview();
            } catch (IOException e) {
            }*/
        }

        @Override
        public void surfaceDestroyed(SurfaceHolder holder) {
            // TODO should we destroy anything on surfaceDestroyed?
        }
    }
}