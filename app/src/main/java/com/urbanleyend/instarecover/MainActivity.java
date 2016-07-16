package com.urbanleyend.instarecover;

import android.Manifest;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.support.annotation.Nullable;
import android.support.design.widget.FloatingActionButton;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Patterns;
import android.view.View;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Toast;

import com.urbanleyend.instarecover.customcomponents.ImageViewer;
import com.urbanleyend.instarecover.task.AsyncResponse;
import com.urbanleyend.instarecover.task.AsyncVideoResponse;
import com.urbanleyend.instarecover.task.DownloadVideoTask;
import com.urbanleyend.instarecover.task.DownloadWebPageTask;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.util.Calendar;
import java.util.Map;

public class MainActivity extends AppCompatActivity implements AsyncResponse, AsyncVideoResponse {

    public final static String APP_FILENAME_PREFIX = R.string.app_name + "Image";
    public final static String APP_FILENAME_VIDEO_PREFIX = R.string.app_name + "Video";
    private final int TIMEOUT_CONNECTION = 5000;//5sec
    private final int TIMEOUT_SOCKET = 30000;//30sec

    private ImageViewer mViewer;
    private SharedPreferences prefs;
    private boolean writeAccepted;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        prefs = this.getSharedPreferences("com.urbanleyend.instarecover", Context.MODE_PRIVATE);

        if (haveWritePermissions()) {
            writeAccepted = true;
        } else{
            Toast toast = Toast.makeText(this, R.string.no_allow_to_save, Toast.LENGTH_SHORT);
            toast.show();
        }

        setContentView(R.layout.activity_main);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        mViewer = (ImageViewer) findViewById(R.id.viewer);

        FloatingActionButton fab = (FloatingActionButton) findViewById(R.id.fab);
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (!writeAccepted) {
                    Toast toast = Toast.makeText(MainActivity.this, R.string.no_allow_to_save, Toast.LENGTH_SHORT);
                    toast.show();
                    return;
                }

                if (mViewer.isVideo()) {
                    saveVideo();
                } else {
                    saveImage();
                }
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();

        String url = "";

        ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        if(clipboard.hasPrimaryClip()== true){
            ClipData.Item item = clipboard.getPrimaryClip().getItemAt(0);
            url = item.getText().toString();
        }

        urlHandle(url);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();

        if (id == R.id.action_gallery) {
            Toast toast = Toast.makeText(this, "Do something else, like not open the gallery", Toast.LENGTH_SHORT);
            toast.show();
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    public void processFinish( Map<String, Object> output) {
        if (!output.containsKey(DownloadWebPageTask.IMAGE)) {
            Toast toast = Toast.makeText(this, R.string.image_not_found, Toast.LENGTH_SHORT);
            toast.show();
            return;
        }

        if ((Boolean) output.get(DownloadWebPageTask.IS_VIDEO) && !output.containsKey(DownloadWebPageTask.VIDEO)) {
            Toast toast = Toast.makeText(this, R.string.video_not_found, Toast.LENGTH_SHORT);
            toast.show();
            return;
        }

        mViewer.setImage((String) output.get(DownloadWebPageTask.IMAGE));
        mViewer.setUser((String) output.get(DownloadWebPageTask.USERNAME));
        mViewer.setProfileImage((String) output.get(DownloadWebPageTask.PROFILE_PIC));
        mViewer.setVideoInfo((Boolean) output.get(DownloadWebPageTask.IS_VIDEO), (String) output.get(DownloadWebPageTask.VIDEO));
    }

    @Override
    public void videoProcessFinish(File output) {
        sendBroadcast(new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, Uri.fromFile(output)));

        Toast toast = Toast.makeText(this, R.string.video_saved, Toast.LENGTH_SHORT);
        toast.show();
    }

    private void urlHandle(String url) {
        if (!Patterns.WEB_URL.matcher(url).matches()) {
            Toast toast = Toast.makeText(this, R.string.invalid_url, Toast.LENGTH_SHORT);
            toast.show();
            return;
        }

        DownloadWebPageTask task = new DownloadWebPageTask();
        task.delegate = this;
        task.execute(url);
    }

    private void saveVideo() {
        String videoUrl = mViewer.getVideo();

        saveVideoToExternalStorage(videoUrl);
    }

    private void saveImage() {
        Bitmap bitmap = mViewer.getImageBitmap();

        saveImageToExternalStorage(bitmap);
    }

    private void saveVideoToExternalStorage(String videoUrl) {
        try {
            File path = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES + "/InstaRecover");
            if (!path.exists()) {
                path.mkdirs();
            }

            String filename = APP_FILENAME_VIDEO_PREFIX + Calendar.getInstance().getTimeInMillis()+".mp4";

            DownloadVideoTask task = new DownloadVideoTask();
            task.delegate = this;
            task.execute(path.getAbsolutePath(), filename, videoUrl);
        } catch (Exception e) {
            e.printStackTrace();
            Toast toast = Toast.makeText(this, R.string.video_saved_error, Toast.LENGTH_SHORT);
            toast.show();
        }
    }

    private void saveImageToExternalStorage(Bitmap image) {
        try {
            File path = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES + "/InstaRecover");
            if (!path.exists()) {
                path.mkdirs();
            }

            String filename = APP_FILENAME_PREFIX + Calendar.getInstance().getTimeInMillis()+".png";
            File file = new File(path, filename);
            file.createNewFile();
            OutputStream fOut = new FileOutputStream(file);

            image.compress(Bitmap.CompressFormat.PNG, 100, fOut);
            fOut.flush();
            fOut.close();

            MediaStore.Images.Media.insertImage(this.getContentResolver(), file.getAbsolutePath(), file.getName(), file.getName());

            Toast toast = Toast.makeText(this, R.string.image_saved, Toast.LENGTH_SHORT);
            toast.show();

        } catch (Exception e) {
            e.printStackTrace();
            Toast toast = Toast.makeText(this, R.string.image_saved_error, Toast.LENGTH_SHORT);
            toast.show();
        }
    }

    public boolean haveWritePermissions() {
        boolean tmpResult = false;

        String permission = Manifest.permission.WRITE_EXTERNAL_STORAGE;
        if (hasPermission(permission)) {
            tmpResult = true;
        } else {
            if (shouldWeAsk(permission)) {
                requestPermissions(permission);
                markAsAsked(permission);
            }
        }

        return tmpResult;
    }

    private void requestPermissions(String permission) {
        String[] perms = {permission};

        int permsRequestCode = 200;

        ActivityCompat.requestPermissions(this, perms, permsRequestCode);
    }

    @Override
    public void onRequestPermissionsResult(int permsRequestCode, String[] permissions, int[] grantResults){
        switch(permsRequestCode){
            case 200:
                writeAccepted = grantResults[0]==PackageManager.PERMISSION_GRANTED;
                break;
        }
    }

    private boolean hasPermission(String permission){
        if(isPreLollipop()){
            return(ContextCompat.checkSelfPermission(this, permission)== PackageManager.PERMISSION_GRANTED);
        }

        return true;
    }

    private boolean shouldWeAsk(String permission){
        return (prefs.getBoolean(permission, true));
    }

    private void markAsAsked(String permission){
        prefs.edit().putBoolean(permission, false).apply();
    }

    private boolean isPreLollipop(){
        return(Build.VERSION.SDK_INT>Build.VERSION_CODES.LOLLIPOP_MR1);
    }
}
