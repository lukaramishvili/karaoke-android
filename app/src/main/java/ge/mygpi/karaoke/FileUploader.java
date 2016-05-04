package ge.mygpi.karaoke;

import android.app.ProgressDialog;
import android.util.Log;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLEncoder;

import javax.net.ssl.HttpsURLConnection;

/**
 * Created by luka on 4/29/16.
 */
public class FileUploader {
    //doc: http://bit.ly/1iCoElu

    public static int uploadVideo(String UserId, String sourcePath, final MainActivity activity, UploadCallback successCallback) {

        activity.runOnUiThread(new Runnable() {
            public void run() {
                activity.uploadProgress = ProgressDialog.show(
                        activity, "", "Uploading file...",
                        true);
            }
        });
        String upLoadServerUri = "https://karaoke.mygpi.ge/Video/UploadVideoDevice";

        HttpsURLConnection conn = null;
        DataOutputStream dos = null;
        String lineEnd = "\r\n";
        String twoHyphens = "--";
        String boundary = "*****";
        int bytesRead, bytesAvailable, bufferSize;
        byte[] buffer;
        int maxBufferSize = 1 * 1024 * 1024;
        File sourceFile = new File(sourcePath);
        String fileName = (new File(sourcePath)).getName();
        final String uploadFilePath = sourceFile.getParent();
        final String uploadFileName = sourceFile.getName();

        int serverResponseCode = 0;

        if (!sourceFile.isFile()) {

            activity.uploadProgress.dismiss();

            Log.e("uploadFile", "Source File not exist :"
                    + uploadFilePath + "" + uploadFileName);

            activity.runOnUiThread(new Runnable() {
                public void run() {
                    activity.toast("Video not found: " + uploadFilePath + "/" + uploadFileName);
                }
            });

            return 0;

        }
        else
        {
            try {
                // open a URL connection to the Servlet
                FileInputStream fileInputStream = new FileInputStream(sourceFile);
                URL url = new URL(upLoadServerUri);

                // Open a HTTP connection to the URL
                conn = (HttpsURLConnection) url.openConnection();
                conn.setDoInput(true); // Allow Inputs
                conn.setDoOutput(true); // Allow Outputs
                conn.setUseCaches(false); // Don't use a Cached Copy
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Connection", "Keep-Alive");
                conn.setRequestProperty("ENCTYPE", "multipart/form-data");
                conn.setRequestProperty("Content-Type", "multipart/form-data;boundary=" + boundary);
                //send UserId as HTTP header instead of HTTP POST
                conn.setRequestProperty("UserId", UserId);
                conn.setRequestProperty("file", fileName);

                dos = new DataOutputStream(conn.getOutputStream());

                //dos.writeBytes((URLEncoder.encode("UserId", "UTF-8") + "=" + URLEncoder.encode(UserId, "UTF-8")).getBytes());

                dos.writeBytes(twoHyphens + boundary + lineEnd);
                dos.writeBytes("Content-Disposition: form-data; name=\"file\";filename=\""
                        + fileName + "\"" + lineEnd);

                dos.writeBytes(lineEnd);

                // create a buffer of  maximum size
                bytesAvailable = fileInputStream.available();

                bufferSize = Math.min(bytesAvailable, maxBufferSize);
                buffer = new byte[bufferSize];

                // read file and write it into form...
                bytesRead = fileInputStream.read(buffer, 0, bufferSize);

                while (bytesRead > 0) {
                    dos.write(buffer, 0, bufferSize);
                    bytesAvailable = fileInputStream.available();
                    bufferSize = Math.min(bytesAvailable, maxBufferSize);
                    bytesRead = fileInputStream.read(buffer, 0, bufferSize);
                }

                // send multipart form data necesssary after file data...
                dos.writeBytes(lineEnd);
                dos.writeBytes(twoHyphens + boundary + twoHyphens + lineEnd);

                // Responses from the server (code and message)
                serverResponseCode = conn.getResponseCode();
                String serverResponseMessage = conn.getResponseMessage();
                BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                String line;
                String serverResponseText = "";
                while ((line = br.readLine()) != null) {
                    serverResponseText += line;
                }

                Log.i("uploadFile", "HTTP Response is : "
                        + serverResponseMessage + ": " + serverResponseCode);

                if (serverResponseCode == 200) {

                    successCallback.serverResponseCode = serverResponseCode;
                    successCallback.serverResponseText = serverResponseText;
                    activity.runOnUiThread(successCallback);
                }

                //close the streams //
                fileInputStream.close();
                dos.flush();
                dos.close();
            } catch (MalformedURLException ex) {

                activity.uploadProgress.dismiss();
                ex.printStackTrace();

                activity.runOnUiThread(new Runnable() {
                    public void run() {
                        activity.toast("MalformedURLException Exception : check script url.");
                    }
                });

                Log.e("Upload file to server", "error: " + ex.getMessage(), ex);
            } catch (Exception e) {

                activity.uploadProgress.dismiss();
                e.printStackTrace();

                activity.runOnUiThread(new Runnable() {
                    public void run() {
                        activity.toast("Got Exception : see logcat ");
                    }
                });
                Log.e("Upload Exception", "Exception : " + e.getMessage(), e);
            }
        }
        activity.uploadProgress.dismiss();
        return serverResponseCode;
    }
}
