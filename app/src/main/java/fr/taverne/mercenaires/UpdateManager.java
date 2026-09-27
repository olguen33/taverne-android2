package fr.taverne.mercenaires;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.DownloadManager;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Environment;
import android.provider.Settings;
import android.widget.Toast;
import org.json.JSONObject;
import java.io.File;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Locale;
import javax.net.ssl.HttpsURLConnection;

final class UpdateManager {
    private static final String MANIFEST_URL="https://raw.githubusercontent.com/olguen33/taverne-android2/main/latest.json";
    private static final String APK_URL="https://raw.githubusercontent.com/olguen33/taverne-android2/main/La-Taverne.apk";
    private final Activity activity;
    private Uri pendingInstall;
    UpdateManager(Activity activity){this.activity=activity;}

    void check(boolean manual){
        new Thread(()->{
            try{
                HttpsURLConnection connection=(HttpsURLConnection)new URL(MANIFEST_URL+"?v="+System.currentTimeMillis()).openConnection();connection.setUseCaches(false);connection.setConnectTimeout(10000);connection.setReadTimeout(10000);
                JSONObject manifest;try(InputStream stream=connection.getInputStream()){byte[] bytes=readLimited(stream,4096);manifest=new JSONObject(new String(bytes,StandardCharsets.UTF_8));}finally{connection.disconnect();}
                int version=manifest.getInt("versionCode");String url=manifest.getString("apkUrl"),digest=manifest.getString("sha256");
                if(!APK_URL.equals(url)||!digest.matches("[a-fA-F0-9]{64}"))throw new IllegalArgumentException("Manifeste invalide");
                int installed=activity.getPackageManager().getPackageInfo(activity.getPackageName(),0).versionCode;
                activity.runOnUiThread(()->{if(version<=installed){if(manual)toast("L'application est à jour.");return;}
                    new AlertDialog.Builder(activity).setTitle("Mise à jour disponible").setMessage("Une nouvelle version de La Taverne est prête. Android te demandera de confirmer son installation.")
                        .setNegativeButton("Plus tard",null).setPositiveButton("Mettre à jour",(dialog,which)->download(version,url,digest)).show();});
            }catch(Exception e){if(manual)activity.runOnUiThread(()->toast("Vérification impossible. Réessaie plus tard."));}
        }).start();
    }
    private static byte[] readLimited(InputStream stream,int limit)throws Exception{java.io.ByteArrayOutputStream out=new java.io.ByteArrayOutputStream();byte[] buffer=new byte[1024];int n;while((n=stream.read(buffer))!=-1){if(out.size()+n>limit)throw new IllegalArgumentException("Réponse trop grande");out.write(buffer,0,n);}return out.toByteArray();}
    private void download(int version,String url,String expected){
        toast("Téléchargement de la mise à jour…");
        new Thread(()->{
            try{
                DownloadManager manager=(DownloadManager)activity.getSystemService(Context.DOWNLOAD_SERVICE);
                String name="La-Taverne-v"+version+".apk";
                File directory=activity.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
                if(directory==null)throw new IllegalStateException("Stockage indisponible");
                File target=new File(directory,name);if(target.exists()&&!target.delete())throw new IllegalStateException("Ancien téléchargement inaccessible");
                DownloadManager.Request request=new DownloadManager.Request(Uri.parse(url));request.setTitle("La Taverne · mise à jour");request.setMimeType("application/vnd.android.package-archive");request.setDestinationInExternalFilesDir(activity,Environment.DIRECTORY_DOWNLOADS,name);
                long id=manager.enqueue(request);int status=0;
                for(int attempt=0;attempt<300;attempt++){
                    try(Cursor cursor=manager.query(new DownloadManager.Query().setFilterById(id))){if(cursor!=null&&cursor.moveToFirst())status=cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS));}
                    if(status==DownloadManager.STATUS_SUCCESSFUL||status==DownloadManager.STATUS_FAILED)break;
                    Thread.sleep(1000);
                }
                if(status!=DownloadManager.STATUS_SUCCESSFUL)throw new IllegalStateException("Téléchargement interrompu");
                Uri uri=manager.getUriForDownloadedFile(id);if(uri==null)throw new IllegalStateException("Fichier inaccessible");
                MessageDigest sha=MessageDigest.getInstance("SHA-256");try(InputStream input=activity.getContentResolver().openInputStream(uri)){if(input==null)throw new IllegalStateException("Fichier inaccessible");byte[] buffer=new byte[8192];int count;while((count=input.read(buffer))!=-1)sha.update(buffer,0,count);}
                StringBuilder actual=new StringBuilder();for(byte b:sha.digest())actual.append(String.format(Locale.ROOT,"%02x",b&255));
                if(!actual.toString().equalsIgnoreCase(expected))throw new IllegalStateException("Fichier non vérifié");
                android.content.pm.PackageInfo archive=activity.getPackageManager().getPackageArchiveInfo(target.getAbsolutePath(),0);
                if(archive==null||!activity.getPackageName().equals(archive.packageName)||archive.versionCode!=version)throw new IllegalStateException("Version de l’APK incorrecte");
                activity.runOnUiThread(()->install(uri));
            }catch(Exception e){activity.runOnUiThread(()->toast("Mise à jour impossible : "+e.getMessage()));}
        }).start();
    }
    private void install(Uri uri){
        if(!activity.getPackageManager().canRequestPackageInstalls()){
            pendingInstall=uri;toast("Autorise La Taverne à installer ses mises à jour, puis reviens dans l'application.");
            Intent settings=new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,Uri.parse("package:"+activity.getPackageName()));activity.startActivity(settings);return;
        }
        pendingInstall=null;Intent intent=new Intent(Intent.ACTION_VIEW);intent.setDataAndType(uri,"application/vnd.android.package-archive");intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);activity.startActivity(intent);
    }
    void resume(){if(pendingInstall!=null&&activity.getPackageManager().canRequestPackageInstalls())install(pendingInstall);}
    private void toast(String value){Toast.makeText(activity,value,Toast.LENGTH_LONG).show();}
}
