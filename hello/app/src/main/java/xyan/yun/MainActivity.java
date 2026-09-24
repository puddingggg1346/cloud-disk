package xyan.yun;

import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.OpenableColumns;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.PathInterpolator;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.*;
import java.net.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {
  static final String BASE = "http://[2409:8a55:1511:41f0:f0e7:36ff:feca:df42]:3000";
  String token;
  TextInputEditText inUser, inPass;
  MaterialButton btnLogin, btnRegister;
  View host, indicator, boxUser, boxPass;
  NotchOutlineDrawable outline;
  boolean ready = false;
  float yUser, yPass;
  FileAdapter adapter;

  @Override protected void onCreate(Bundle b){
    super.onCreate(b);
    setupKeyboardShift();
    showLogin();
    checkUpdate();
  }

  void checkUpdate(){
    Executors.newSingleThreadExecutor().execute(()->{
      try{
        String res = req("GET", BASE + "/version", null, null);
        JSONObject j = new JSONObject(res);
        int latest = j.optInt("versionCode", 0);
        String name = j.optString("versionName", "");
        int cur = getPackageManager().getPackageInfo(getPackageName(), 0).versionCode;
        if(latest > cur){
          final String vn = name;
          runOnUiThread(()->{
            new androidx.appcompat.app.AlertDialog.Builder(this)
              .setTitle("发现新版本 " + vn)
              .setMessage("是否下载更新？")
              .setPositiveButton("更新", (d,w) -> downloadApk())
              .setNegativeButton("取消", null)
              .show();
          });
        }
      }catch(Exception ignored){}
    });
  }

  void downloadApk(){
    toast("开始下载...");
    Executors.newSingleThreadExecutor().execute(()->{
      try{
        File dir = getExternalFilesDir(null);
        File apk = new File(dir, "update.apk");
        HttpURLConnection c = (HttpURLConnection)new URL(BASE + "/app.apk").openConnection();
        c.setConnectTimeout(10000); c.setReadTimeout(120000);
        InputStream is = c.getInputStream();
        FileOutputStream fo = new FileOutputStream(apk);
        byte[] buf = new byte[8192]; int n;
        while((n=is.read(buf))>0) fo.write(buf,0,n);
        fo.close(); is.close();
        runOnUiThread(()->installApk(apk));
      }catch(Exception e){ toast("下载失败: "+e.getMessage()); }
    });
  }

  void installApk(File apk){
    try{
      Uri uri = androidx.core.content.FileProvider.getUriForFile(
        this, getPackageName() + ".fileprovider", apk);
      Intent i = new Intent(Intent.ACTION_VIEW);
      i.setDataAndType(uri, "application/vnd.android.package-archive");
      i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
      startActivity(i);
    }catch(Exception e){ toast("安装失败: "+e.getMessage()); }
  }

  void setupKeyboardShift(){
    final View root = getWindow().getDecorView();
    androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
      int ime = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.ime()).bottom;
      View content = findViewById(android.R.id.content);
      if (content != null) {
        float target = -ime * 0.5f;
        if (Math.abs(content.getTranslationY() - target) > 1f) {
          content.animate().translationY(target)
            .setDuration(260)
            .setInterpolator(new PathInterpolator(0.2f, 0f, 0f, 1f))
            .start();
        }
      }
      return insets;
    });
  }

  void showLogin(){
    setContentView(R.layout.activity_login);
    inUser = findViewById(R.id.in_user);
    inPass = findViewById(R.id.in_pass);
    btnLogin = findViewById(R.id.btn_login);
    btnRegister = findViewById(R.id.btn_register);
    boxUser = findViewById(R.id.box_user);
    boxPass = findViewById(R.id.box_pass);
    indicator = findViewById(R.id.indicator);
    host = findViewById(R.id.host);

    outline = new NotchOutlineDrawable();
    indicator.setBackground(outline);

    btnLogin.setOnClickListener(v -> auth("/login", true));
    btnRegister.setOnClickListener(v -> auth("/register", false));

    host.post(() -> {
      ViewGroup.LayoutParams lp = indicator.getLayoutParams();
      lp.height = boxUser.getHeight();
      indicator.setLayoutParams(lp);

      float dy = 5 * getResources().getDisplayMetrics().density;
      yUser = yInHost(boxUser) + dy;
      yPass = yInHost(boxPass) + dy;
      indicator.setTranslationY(yUser);

      ready = true;
      syncFocus();
    });

    View.OnFocusChangeListener l = (v, f) -> syncFocus();
    inUser.setOnFocusChangeListener(l);
    inPass.setOnFocusChangeListener(l);
    boxUser.setOnClickListener(v -> inUser.requestFocus());
    boxPass.setOnClickListener(v -> inPass.requestFocus());

    inUser.requestFocus();
  }

  void syncFocus(){
    if(!ready) return;
    boolean fu = inUser.hasFocus(), fp = inPass.hasFocus();
    outline.setShowNotch(fu || fp);
    if(fu) slideTo(yUser);
    else if(fp) slideTo(yPass);
  }

  void slideTo(float y){
    indicator.animate().cancel();
    indicator.animate().translationY(y)
      .setDuration(220)
      .setInterpolator(new PathInterpolator(0.2f, 0f, 0f, 1f))
      .start();
  }

  float yInHost(View v){
    int[] h = new int[2]; host.getLocationOnScreen(h);
    int[] t = new int[2]; v.getLocationOnScreen(t);
    return t[1] - h[1];
  }

  void auth(String path, boolean isLogin){
    String u = inUser.getText()==null ? "" : inUser.getText().toString().trim();
    String p = inPass.getText()==null ? "" : inPass.getText().toString().trim();
    if(u.isEmpty()||p.isEmpty()){ toast("请输入账号密码"); return; }
    btnLogin.setEnabled(false); btnRegister.setEnabled(false);
    btnLogin.setText(isLogin ? "登录中..." : "注册中...");
    Executors.newSingleThreadExecutor().execute(()->{
      try{
        String res = req("POST", BASE+path, null,
          new JSONObject().put("username",u).put("password",p).toString());
        JSONObject j = new JSONObject(res);
        if(isLogin){
          String t = j.optString("token","").trim();
          if(t.isEmpty()){ toast("登录失败: "+j.optString("error",res)); resetBtn(true); return; }
          token = t;
          runOnUiThread(this::showFiles);
        } else {
          toast("注册: "+j.optString("message", j.optString("error", res)));
          resetBtn(false);
        }
      }catch(Exception e){ toast("错误:"+e.getMessage()); resetBtn(isLogin); }
    });
  }

  void resetBtn(boolean isLogin){
    runOnUiThread(()->{
      btnLogin.setEnabled(true); btnRegister.setEnabled(true);
      btnLogin.setText(isLogin ? "登录" : "注册");
    });
  }

  void showFiles(){
    setContentView(R.layout.activity_files);
    MaterialToolbar tb = findViewById(R.id.toolbar);
    tb.setNavigationOnClickListener(v -> showLogin());
    findViewById(R.id.btn_upload).setOnClickListener(v -> pickFile());
    RecyclerView rv = findViewById(R.id.rv_files);
    rv.setLayoutManager(new LinearLayoutManager(this));
    adapter = new FileAdapter();
    rv.setAdapter(adapter);
    refresh();
  }

  void refresh(){
    Executors.newSingleThreadExecutor().execute(()->{
      try{
        String res = req("GET", BASE+"/files", token, null);
        JSONArray arr = new JSONArray(res);
        List<FileItem> list = new ArrayList<>();
        for(int i=0;i<arr.length();i++){
          JSONObject o = arr.getJSONObject(i);
          list.add(new FileItem(o.getString("name"), o.optLong("size", 0)));
        }
        runOnUiThread(()->adapter.setData(list));
      }catch(Exception e){ toast("错误:"+e.getMessage()); }
    });
  }

  void pickFile(){
    Intent i = new Intent(Intent.ACTION_GET_CONTENT);
    i.setType("*/*"); i.addCategory(Intent.CATEGORY_OPENABLE);
    startActivityForResult(Intent.createChooser(i,"选择文件"), 1001);
  }

  @Override protected void onActivityResult(int rq, int rs, Intent data){
    super.onActivityResult(rq,rs,data);
    if(rq==1001 && rs==RESULT_OK && data!=null && data.getData()!=null) upload(data.getData());
  }

  void upload(Uri uri){
    Executors.newSingleThreadExecutor().execute(()->{
      try{
        String name = queryName(uri);
        String b = "----b"+System.currentTimeMillis();
        HttpURLConnection c = (HttpURLConnection)new URL(BASE+"/upload").openConnection();
        c.setRequestMethod("POST"); c.setDoOutput(true);
        c.setConnectTimeout(10000); c.setReadTimeout(60000);
        c.setRequestProperty("Authorization","Bearer "+token);
        c.setRequestProperty("Content-Type","multipart/form-data; boundary="+b);
        DataOutputStream o = new DataOutputStream(c.getOutputStream());
        o.writeBytes("--"+b+"\r\nContent-Disposition: form-data; name=\"file\"; filename=\""+name+"\"\r\nContent-Type: application/octet-stream\r\n\r\n");
        InputStream fi = getContentResolver().openInputStream(uri);
        byte[] buf = new byte[8192]; int n;
        while((n=fi.read(buf))>0) o.write(buf,0,n);
        fi.close();
        o.writeBytes("\r\n--"+b+"--\r\n"); o.flush(); o.close();
        int code = c.getResponseCode();
        toast(code<400 ? "上传成功: "+name : "上传失败: "+code);
        if(code<400) refresh();
      }catch(Exception e){ toast("错误:"+e.getMessage()); }
    });
  }

  void download(String name){
    Executors.newSingleThreadExecutor().execute(()->{
      try{
        HttpURLConnection c = (HttpURLConnection)new URL(BASE+"/download/"+URLEncoder.encode(name,"UTF-8").replace("+","%20")).openConnection();
        c.setRequestProperty("Authorization","Bearer "+token);
        c.setConnectTimeout(10000); c.setReadTimeout(60000);
        int code = c.getResponseCode();
        if(code>=400){ toast("下载失败: "+code); return; }
        String savedPath = saveToDownloads(name, c.getInputStream());
        toast("已保存: " + savedPath);
      }catch(Exception e){ toast("错误:"+e.getMessage()); }
    });
  }

  String saveToDownloads(String name, InputStream is) throws Exception {
    if (android.os.Build.VERSION.SDK_INT >= 29) {
      android.content.ContentValues cv = new android.content.ContentValues();
      cv.put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, name);
      cv.put(android.provider.MediaStore.MediaColumns.MIME_TYPE, "application/octet-stream");
      cv.put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH,
          Environment.DIRECTORY_DOWNLOADS + "/CloudDisk");
      Uri uri = getContentResolver().insert(
          android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, cv);
      if (uri == null) throw new IOException("MediaStore insert failed");
      OutputStream os = getContentResolver().openOutputStream(uri);
      byte[] buf = new byte[8192]; int n;
      while ((n = is.read(buf)) > 0) os.write(buf, 0, n);
      os.close(); is.close();
      return "Download/CloudDisk/" + name;
    } else {
      File dir = new File(Environment.getExternalStoragePublicDirectory(
          Environment.DIRECTORY_DOWNLOADS), "CloudDisk");
      if (!dir.exists()) dir.mkdirs();
      File out = new File(dir, name);
      FileOutputStream fo = new FileOutputStream(out);
      byte[] buf = new byte[8192]; int n;
      while ((n = is.read(buf)) > 0) fo.write(buf, 0, n);
      fo.close(); is.close();
      return out.getAbsolutePath();
    }
  }

  String queryName(Uri uri){
    String name="file";
    Cursor c = getContentResolver().query(uri,null,null,null,null);
    if(c!=null){ try{ if(c.moveToFirst()){ int i=c.getColumnIndex(OpenableColumns.DISPLAY_NAME); if(i>=0) name=c.getString(i); } } finally { c.close(); } }
    return name;
  }

  String req(String m, String url, String tok, String body) throws Exception {
    HttpURLConnection c = (HttpURLConnection)new URL(url).openConnection();
    c.setRequestMethod(m); c.setConnectTimeout(10000); c.setReadTimeout(20000);
    if(tok!=null) c.setRequestProperty("Authorization","Bearer "+tok);
    if(body!=null){
      c.setDoOutput(true);
      c.setRequestProperty("Content-Type","application/json");
      c.getOutputStream().write(body.getBytes("UTF-8"));
    }
    int code = c.getResponseCode();
    InputStream is = code<400 ? c.getInputStream() : c.getErrorStream();
    if(is==null) return "";
    BufferedReader r = new BufferedReader(new InputStreamReader(is,"UTF-8"));
    StringBuilder sb = new StringBuilder(); String l;
    while((l=r.readLine())!=null) sb.append(l);
    return sb.toString();
  }

  void toast(String s){
    runOnUiThread(()->{
      Toast t = new Toast(this);
      View v = LayoutInflater.from(this).inflate(R.layout.toast_custom, null);
      ((TextView)v.findViewById(R.id.toast_text)).setText(s);
      t.setView(v);
      t.setDuration(Toast.LENGTH_SHORT);
      t.show();
    });
  }

  static class FileItem {
    final String name; final long size;
    FileItem(String n, long s){ name = n; size = s; }
  }

  String fmtSize(long b){
    if(b < 1024*1024) return String.format(java.util.Locale.US, "%.1f KB", b/1024.0);
    if(b < 1024L*1024*1024) return String.format(java.util.Locale.US, "%.1f MB", b/(1024.0*1024));
    return String.format(java.util.Locale.US, "%.2f GB", b/(1024.0*1024*1024));
  }

  class FileAdapter extends RecyclerView.Adapter<FileAdapter.VH> {
    List<FileItem> data = new ArrayList<>();
    void setData(List<FileItem> l){ data = l; notifyDataSetChanged(); }
    @NonNull @Override public VH onCreateViewHolder(@NonNull ViewGroup p, int t){
      return new VH(LayoutInflater.from(p.getContext()).inflate(R.layout.item_file, p, false));
    }
    @Override public void onBindViewHolder(@NonNull VH h, int pos){
      FileItem it = data.get(pos);
      h.tvName.setText(it.name);
      h.tvSize.setText(fmtSize(it.size));
      h.btn.setOnClickListener(v -> download(it.name));
    }
    @Override public int getItemCount(){ return data.size(); }
    class VH extends RecyclerView.ViewHolder {
      TextView tvName, tvSize; MaterialButton btn;
      VH(View v){ super(v); tvName = v.findViewById(R.id.tv_name); tvSize = v.findViewById(R.id.tv_size); btn = v.findViewById(R.id.btn_download); }
    }
  }
}
