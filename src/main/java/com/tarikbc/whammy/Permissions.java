package com.tarikbc.whammy;
import android.app.Activity; import android.content.Intent; import android.net.Uri;
import android.os.Environment; import android.provider.Settings;
public class Permissions {
    public static boolean hasAllFiles() { return Environment.isExternalStorageManager(); }
    public static void requestAllFiles(Activity a) {
        Intent i = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
            Uri.parse("package:" + a.getPackageName()));
        try { a.startActivity(i); }
        catch (Exception e) { a.startActivity(new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)); }
    }
}
