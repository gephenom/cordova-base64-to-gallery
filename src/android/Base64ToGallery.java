package it.nexxa.base64ToGallery;

import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Base64;
import android.util.Log;
import android.webkit.MimeTypeMap;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaPlugin;
import org.json.JSONArray;
import org.json.JSONException;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Calendar;

public class Base64ToGallery extends CordovaPlugin {

    public static final String EMPTY_STR = "";

    @Override
    public boolean execute(String action, JSONArray args,
                           CallbackContext callbackContext) throws JSONException {

        String base64 = args.optString(0);
        String filePrefix = args.optString(1);
        boolean mediaScannerEnabled = args.optBoolean(2);

        // isEmpty() requires API level 9
        if (base64.equals(EMPTY_STR)) {
            callbackContext.error("Missing base64 string");
        }

        // Create the bitmap from the base64 string
        byte[] decodedString = Base64.decode(base64, Base64.DEFAULT);
        Bitmap bmp = BitmapFactory.decodeByteArray(decodedString, 0, decodedString.length);

        if (bmp == null) {
            callbackContext.error("The image could not be decoded");

        } else {

            // Save the image
            File imageFile;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                imageFile = mySave(bmp, filePrefix);
            } else {
                imageFile = savePhoto(bmp, filePrefix);
            }

            if (imageFile == null) {
                callbackContext.error("Error while saving image");
            }

            // Update image gallery
            if (mediaScannerEnabled && Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                scanPhoto(imageFile);
            }

            callbackContext.success(imageFile.toString());
        }

        return true;
    }

    private File mySave(Bitmap bitmap, String prefix) {
        Context context = cordova.getActivity().getApplicationContext();

        File file = new File(context.getFilesDir(), prefix + System.currentTimeMillis() + ".png");
        try {
            FileOutputStream fos = new FileOutputStream(file);
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, fos);
            fos.flush();
            fos.close();
        } catch (IOException e) {
            e.printStackTrace();
        }

        ContentValues values = new ContentValues();
        values.put(MediaStore.Images.ImageColumns.DISPLAY_NAME, file.getName());
        values.put(MediaStore.Images.ImageColumns.MIME_TYPE, MimeTypeMap.getSingleton().getMimeTypeFromExtension("png"));
        Uri uri = context.getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
        OutputStream os = null;
        InputStream is = null;
        try {
            os = context.getContentResolver().openOutputStream(uri);
            is = new FileInputStream(file);
            byte[] buffer = new byte[1024];
            int len;
            while ((len = is.read(buffer)) != -1) {
                os.write(buffer, 0, len);
            }
            os.flush();
            os.close();
            is.close();
        } catch (IOException e) {
            e.printStackTrace();
            if (os != null) {
                try {
                    os.close();
                } catch (IOException ioException) {
                    ioException.printStackTrace();
                }
            }
            if (is != null) {
                try {
                    is.close();
                } catch (IOException ioException) {
                    ioException.printStackTrace();
                }
            }
        }
        return file;
    }

    private File savePhoto(Bitmap bmp, String prefix) {
        File retVal = null;

        try {
            String deviceVersion = Build.VERSION.RELEASE;
            Calendar c = Calendar.getInstance();
            String date = EMPTY_STR
                    + c.get(Calendar.YEAR)
                    + c.get(Calendar.MONTH)
                    + c.get(Calendar.DAY_OF_MONTH)
                    + c.get(Calendar.HOUR_OF_DAY)
                    + c.get(Calendar.MINUTE)
                    + c.get(Calendar.SECOND);

            int check = deviceVersion.compareTo("2.3.3");

            File folder;

            /*
             * File path = Environment.getExternalStoragePublicDirectory(
             * Environment.DIRECTORY_PICTURES ); //this throws error in Android
             * 2.2
             */
            if (check >= 1) {
                folder = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES);

                if (!folder.exists()) {
                    folder.mkdirs();
                }

            } else {
                folder = Environment.getExternalStorageDirectory();
            }

            File imageFile = new File(folder, prefix + date + ".png");

            FileOutputStream out = new FileOutputStream(imageFile);
            bmp.compress(Bitmap.CompressFormat.PNG, 100, out);
            out.flush();
            out.close();

            retVal = imageFile;

        } catch (Exception e) {
            Log.e("Base64ToGallery", "An exception occured while saving image: " + e.toString());
        }

        return retVal;
    }

    /**
     * Invoke the system's media scanner to add your photo to the Media Provider's database,
     * making it available in the Android Gallery application and to other apps.
     */
    private void scanPhoto(File imageFile) {
        Intent mediaScanIntent = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
        Uri contentUri = Uri.fromFile(imageFile);

        mediaScanIntent.setData(contentUri);

        cordova.getActivity().sendBroadcast(mediaScanIntent);
    }
}
