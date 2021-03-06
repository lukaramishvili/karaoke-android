package ge.mygpi.karaoke;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.Iterator;
import java.util.List;

import android.Manifest;
import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.hardware.Camera;
import android.media.CamcorderProfile;
import android.media.MediaPlayer;
import android.media.MediaRecorder;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.FileObserver;
import android.os.Handler;
import android.os.StatFs;
import android.provider.MediaStore;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.util.Log;
import android.view.KeyEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.MediaController;
import android.widget.RelativeLayout;
import android.widget.Toast;

import com.facebook.AccessToken;
import com.facebook.AccessTokenTracker;
import com.facebook.CallbackManager;
import com.facebook.FacebookCallback;
import com.facebook.FacebookException;
import com.facebook.FacebookSdk;
import com.facebook.GraphRequest;
import com.facebook.GraphResponse;
import com.facebook.Profile;
import com.facebook.ProfileTracker;
import com.facebook.login.LoginResult;
import com.facebook.login.widget.LoginButton;

import org.json.JSONException;
import org.json.JSONObject;

import javax.net.ssl.HttpsURLConnection;

public class MainActivity extends Activity{
    //doc: http://bit.ly/1QEum1E

    private boolean confirmExit = false;

    private boolean onCreateCalled = false;

    private boolean fCameraPermissionBeingRequested = false;

    private Camera myCamera;
    private int camId = Camera.CameraInfo.CAMERA_FACING_BACK;
    private MyCameraSurfaceView myCameraSurfaceView;
    private MediaRecorder mediaRecorder;

    int REQUEST_TAKE_GALLERY_VIDEO = 101;
    int REQUEST_CAMERA_ACCESS = 103;

    private LoginButton loginButton;

    CallbackManager callbackManager;
    ProfileTracker profileTracker;
    AccessTokenTracker accessTokenTracker;
    AccessToken accessToken;

    boolean loggedIn = false;
    String UserId = "";

    public ProgressDialog uploadProgress;

    ImageButton record_button;
    ImageButton browse_button;
    ImageButton flip_button;
    ImageButton retake_button;
    ImageButton upload_button;
    ImageButton gpi_logo;
    ImageButton mygpi_logo;
    SurfaceHolder surfaceHolder;
    boolean recording;

    CustomVideoView lyricsVideo;
    boolean fVideoLoaded = false;

    CustomVideoView previewVideo;

    Long recordingId = (long) 0;

    Date lastRecordingStartTime;

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
        Toast.makeText(MainActivity.this, message, Toast.LENGTH_SHORT).show();
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

    private int getLyricsVideoNumber(){
        //start detect video depending on day
        Calendar firstDay = new GregorianCalendar(2016, 4/*Jan=0,4=May*/, 31, 0, 0, 0);
        firstDay.set(Calendar.HOUR_OF_DAY, 0);
        firstDay.set(Calendar.MINUTE, 0);
        firstDay.set(Calendar.SECOND, 0);
        firstDay.set(Calendar.MILLISECOND, 0);
        Calendar today = new GregorianCalendar();
        today.set(Calendar.HOUR_OF_DAY, 0);
        today.set(Calendar.MINUTE, 0);
        today.set(Calendar.SECOND, 0);
        today.set(Calendar.MILLISECOND, 0);
        long diffMillis = today.getTime().getTime() - firstDay.getTime().getTime();
        int diffDays = (int) (diffMillis / (1000*60*60*24));
        //end detect video depending on day
        return (diffDays % 3) + 1;
    }

    private void prepareCamera() {
        myCamera = getCameraInstance(camId);
        if (myCamera == null) {

            if(ContextCompat.checkSelfPermission(MainActivity.this, android.Manifest.permission.CAMERA)
                    != PackageManager.PERMISSION_GRANTED){
                if(ActivityCompat.shouldShowRequestPermissionRationale(MainActivity.this, android.Manifest.permission.CAMERA)){
                    toast("Please allow camera access to record video.");
                }
                if(!fCameraPermissionBeingRequested) {
                    fCameraPermissionBeingRequested = true;
                    ActivityCompat.requestPermissions(MainActivity.this,
                            new String[]{Manifest.permission.CAMERA},
                            REQUEST_CAMERA_ACCESS);
                }
                return;//prepareCamera will be called in grant permissions callback, success section
            } else {
                toast("Failed to get Camera");//permission granted but still no camera
            }

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

    private void showLinkedDialog(String title, String cancelLabel, String message){
        LinkedAlertDialog.create(MainActivity.this, title, cancelLabel, message).show();
    }

    private void uploadVideoFromPath(final String path){
        new Thread(new Runnable() {
            public void run() {
                //this will also create and show the uploadProgress dialog
                FileUploader.uploadVideo(UserId, path, MainActivity.this, new UploadCallback() {
                    @Override
                    public void run() {
                        Boolean serverSuccess = false;
                        String serverMessage = "N/A";
                        try {
                            JSONObject jsonResult = new JSONObject(this.serverResponseText);
                            if(jsonResult.getBoolean("Success")){
                                String videoUrl = jsonResult.getString("Url");
                                String msg = "ვიდეო ატვირთულია.\n\n იხილეთ ატვირთული ვიდეო აქ: \n\n"
                                        + " https://karaoke.mygpi.ge" + videoUrl;
                                showLinkedDialog("ვიდეო ატვირთულია", "გამოსვლა", msg);
                            } else {
                                toast("Server returned problem when saving video");
                            }
                        } catch (JSONException e){
                            toast("Server error while uploading");
                        }
                    }
                });
            }
        }).start();
    }

    private void facebookLoginCallback(final AccessToken argCurrentAccessToken){
        if(argCurrentAccessToken == null){
            return;
        }
        GraphRequest request = GraphRequest.newMeRequest(
                argCurrentAccessToken,
                new GraphRequest.GraphJSONObjectCallback() {
                    @Override
                    public void onCompleted(JSONObject object, GraphResponse response) {
                        try {
                            // Application code
                            final String id = object.getString("id");
                            final String name = object.getString("name");
                            final String email = object.has("email")
                                    ? object.getString("email")
                                    : "no-email@example.com";
                            final String avatar = object.getJSONObject("picture")
                                    .getJSONObject("data")
                                    .getString("url");
                            final String cover = object.has("cover")
                                    ?   object.getJSONObject("cover").getString("source")
                                    : avatar;
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
                                        String urlParameters = "id=" + id + "&name=" + name + "&email=" + email + "&avatar=" + avatar + "&cover=" + cover + "&accesstoken=" + argCurrentAccessToken.getToken();
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
                                            runOnUiThread(new Runnable(){public void run(){toast("Logged in as " + name);}});
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
                            //showLinkedDialog("asd","asd","incor fb" + response.getRawResponse());
                        }
                    }
                });
        Bundle parameters = new Bundle();
        parameters.putString("fields", "id,name,email,picture.type(large),cover");
        request.setParameters(parameters);
        request.executeAsync();
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

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           String permissions[], int[] grantResults) {
        if(requestCode == REQUEST_CAMERA_ACCESS) {
            fCameraPermissionBeingRequested = false;
            // If request is cancelled, the result arrays are empty.
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // permission was granted, yay! Do the
                // contacts-related task you need to do.
                prepareCamera();
            } else {
                // permission denied, boo! Disable the
                // functionality that depends on this permission.
                toast("You need to grant permission to camera to use this app.");
            }
        }
        // other 'elseif' lines to check for other
        // permissions this app might request
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        profileTracker.stopTracking();
        accessTokenTracker.stopTracking();
    }

    public void initUIAndEvents(){

        setContentView(R.layout.activity_main);

        record_button = (ImageButton)findViewById(R.id.record_button);
        record_button.setOnClickListener(recordButtonOnClickListener);

        browse_button = (ImageButton)findViewById(R.id.browse_button);
        browse_button.setOnClickListener(browseButtonOnClickListener);

        flip_button = (ImageButton)findViewById(R.id.flip_button);
        flip_button.setOnClickListener(flipButtonOnClickListener);

        retake_button = (ImageButton)findViewById(R.id.retake_button);
        retake_button.setOnClickListener(retakeButtonOnClickListener);

        upload_button = (ImageButton)findViewById(R.id.upload_button);
        upload_button.setOnClickListener(uploadButtonOnClickListener);

        gpi_logo = (ImageButton)findViewById(R.id.gpi_logo);
        gpi_logo.setOnClickListener(gpiLogoOnClickListener);

        mygpi_logo = (ImageButton)findViewById(R.id.mygpi_logo);
        mygpi_logo.setOnClickListener(mygpiLogoOnClickListener);
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

        initUIAndEvents();//contains setContentView and onClick handlers

        callbackManager = CallbackManager.Factory.create();

        // we don't use this callback because we need additional details - email, cover, etc
        profileTracker = new ProfileTracker() {
            @Override
            protected void onCurrentProfileChanged(
                    Profile oldProfile,
                    Profile currentProfile) {
                if(currentProfile != null) {
                    //logged in
                    //currentProfile.getId(), getName(), getProfilePictureUri(256, 256)
                } else {
                    //logged out
                }
            }
        };

        //start accessTokenTracker code
        accessTokenTracker = new AccessTokenTracker() {
            @Override
            protected void onCurrentAccessTokenChanged(
                    AccessToken oldAccessToken,
                    AccessToken currentAccessToken) {
                // Set the access token using
                // currentAccessToken when it's loaded or set.
                accessToken = currentAccessToken;
                facebookLoginCallback(accessToken);
            }
        };
        // If the access token is available already assign it.
        accessToken = AccessToken.getCurrentAccessToken();
        facebookLoginCallback(accessToken);
        //end accessTokenTracker code

        loginButton = (LoginButton)findViewById(R.id.login_button);
        loginButton.setReadPermissions(Arrays.asList(
                "public_profile", "email", "user_friends"));
        //IMPORTANT: not using this callback anymore because it only fires on physically clicking LoginButton
        loginButton.registerCallback(callbackManager, new FacebookCallback<LoginResult>() {
            @Override
            public void onSuccess(final LoginResult loginResult) {
                //access token: loginResult.getAccessToken().getToken()
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
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        toast("Lyrics loaded. You can now record video.");
                    }
                });
            }
        });
        //lyricsVideo.setVideoURI(Uri.parse("https://karaoke.mygpi.ge/source.mp4"));
        lyricsVideo.setVideoURI(Uri.parse("http://karaoke.mygpi.ge/gpivideos/" + Integer.valueOf(getLyricsVideoNumber()).toString() + ".mp4"));
        //end setup video

        prepareCamera();

        onCreateCalled = true;
    }

    Button.OnClickListener recordButtonOnClickListener
            = new Button.OnClickListener(){

        @Override
        public void onClick(View v) {

            if(accessToken == null){
                toast("Please log in before recording");
                return;
            }

            if(!fFreeSpaceIsEnough()) {
                notifyNotEnoughFreeSpace();
                return;
            }

            if(recording){

                Date nowDate = new Date();
                if(lastRecordingStartTime != null && Long.valueOf(nowDate.getTime() - lastRecordingStartTime.getTime()) > 1 * 1000) {

                    recording = false;

                    //first, set up monitoring file save; actual recorder stopping below

                    //wait for MediaRecorder finishing saving file
                    FileObserver fo = new FileObserver(saveDir.getAbsolutePath(), FileObserver.CLOSE_WRITE) {
                        @Override
                        public void onEvent(int event, String path) {
                            if (path.equals((new File(getVideoSavePath(recordingId))).getName())) {
                                //video save to disk finished callback
                                stopWatching();
                                runOnUiThread(new Runnable() {
                                    @Override
                                    public void run() {
                                        previewRecordedVideo();
                                    }
                                });
                            }
                        }
                    };
                    fo.startWatching();


                    // stop recording and release camera
                    mediaRecorder.stop();  // stop the recording
                    releaseMediaRecorder(); // release the MediaRecorder object
                    //reset button text
                    record_button.setImageResource(R.drawable.record_button);
                    //record_button.setText(R.string.start_record_label);

                    lyricsVideo.pause();
                } else {
                    //don't allow stopping recording in the first three seconds - causes error
                }

            } else {

                //wait for lyrics video to load and start
                if(fVideoLoaded) {
                    lyricsVideo.setPlayPauseListener(new CustomVideoView.PlayPauseListener() {
                        @Override
                        public void onPlay() {
                            //play callback

                            //Release Camera before MediaRecorder start
                            releaseCamera();

                            if(!prepareMediaRecorder()){
                                toast("Fail in prepareMediaRecorder()!\n - Ended -");
                                finish();
                            }

                            mediaRecorder.start();
                            recording = true;
                            //record_button.setText(R.string.stop_record_label);
                            record_button.setImageResource(R.drawable.stop_button);

                            lastRecordingStartTime = new Date();
                        }
                        @Override
                        public void onPause() {
                            //paused callback
                        }
                    });
                    lyricsVideo.seekTo(0);
                    lyricsVideo.start();
                } else {
                    toast("Please wait for the lyrics to load.");
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

    Button.OnClickListener browseButtonOnClickListener
            = new Button.OnClickListener(){
        @Override
        public void onClick(View v) {
            // TODO: 4/30/16 check login and then allow upload only if the user has no videos yet

            if(accessToken == null){
                toast("Please log in before uploading");
                return;
            }

            Intent intent = new Intent();
            intent.setType("video/*");
            intent.setAction(Intent.ACTION_GET_CONTENT);
            startActivityForResult(Intent.createChooser(intent,"Select Video"),REQUEST_TAKE_GALLERY_VIDEO);
        }
    };

    Button.OnClickListener retakeButtonOnClickListener
            = new Button.OnClickListener(){
        @Override
        public void onClick(View v) {
            retakeVideo();
        }
    };

    Button.OnClickListener uploadButtonOnClickListener
            = new Button.OnClickListener(){
        @Override
        public void onClick(View v) {
            //this will also create and show the uploadProgress dialog
            uploadVideoFromPath(getVideoSavePath(recordingId));
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

    public void previewRecordedVideo(){
        RelativeLayout mainContainer = (RelativeLayout)findViewById(R.id.mainContainer);
        RelativeLayout previewContainer = (RelativeLayout)findViewById(R.id.previewContainer);
        mainContainer.setVisibility(View.GONE);
        previewContainer.setVisibility(View.VISIBLE);

        //start setup video
        previewVideo = (CustomVideoView) findViewById(R.id.previewVideo);
        final MediaController previewControls = new MediaController(this){
            /*@Override
            public void hide() {
                prevent hiding controls
            }*/
            //retake video on back key (DOESN'T WORK)
            @Override
            public boolean dispatchKeyEvent(KeyEvent event) {
                if(event.getKeyCode() == KeyEvent.KEYCODE_BACK) {
                    retakeVideo();
                }
                return true;
            }
        };
        previewControls.setAnchorView(previewVideo);
        previewControls.setMediaPlayer(previewVideo);
        previewVideo.setMediaController(previewControls);
        previewControls.requestFocus();
        previewVideo.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
            @Override
            public void onPrepared(MediaPlayer mp) {
                //video is ready to be played
                previewVideo.setPlayPauseListener(new CustomVideoView.PlayPauseListener() {
                    @Override
                    public void onPlay() {
                        //play started callback
                    }
                    @Override
                    public void onPause() {
                        //paused callback
                    }
                });
                //the controls still hide when video finishes
                previewControls.show(2000000000);
                mp.start();
            }
        });
        previewVideo.setVideoPath(getVideoSavePath(recordingId));
        //end setup video
    }

    public void retakeVideo(){
        RelativeLayout mainContainer = (RelativeLayout)findViewById(R.id.mainContainer);
        RelativeLayout previewContainer = (RelativeLayout)findViewById(R.id.previewContainer);

        previewContainer.setVisibility(View.GONE);
        mainContainer.setVisibility(View.VISIBLE);
    }

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

    private void switchCamera() {
        //only continue if we have multiple cameras
        int nextCameraId = camId + 1;
        if (nextCameraId >= Camera.getNumberOfCameras()) {
            nextCameraId = 0;
        }
        camId = nextCameraId;
        resetCamera();
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
                //after onResume, without re-calling setContentView, camera preview didn't work
                //also onClick handlers were lost. the following method contains code for both
                //IMPORTANT: this code also gets called on app start
                initUIAndEvents();
                prepareCamera();
            }
        }
    }

    @Override
    public void onBackPressed() {
        RelativeLayout previewContainer = (RelativeLayout) findViewById(R.id.previewContainer);
        if (previewContainer.getVisibility() == View.VISIBLE) {
            retakeVideo();
        } else {
            if (confirmExit) {
                finish(); // finish activity
            } else {
                toast("Press Back again to Exit.");
                confirmExit = true;
                new Handler().postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        confirmExit = false;
                    }
                }, 3 * 1000);
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