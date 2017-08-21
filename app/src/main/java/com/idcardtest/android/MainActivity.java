package com.idcardtest.android;

import com.google.gson.Gson;
import com.hanvon.HWCloudManager;
import com.hanvon.utils.BitmapUtil;

import android.Manifest;
import android.app.ProgressDialog;
import android.content.ContentUris;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.provider.DocumentsContract;
import android.provider.MediaStore;
import android.support.annotation.NonNull;
import android.support.annotation.RequiresApi;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.NotificationCompat;
import android.support.v4.content.ContextCompat;
import android.support.v4.content.FileProvider;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.hanvon.HWCloudManager;
import com.hanvon.utils.BitmapUtil;
import com.idcardtest.android.Gson.Result;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

public class MainActivity extends AppCompatActivity implements View.OnClickListener {
    private static final int CHOOSE_PHOTO = 0;
    private static final int TAKE_PHOTO = 1;
    private static final int START_QUERY = 2;

    private Button choosePhoto;
    private Button startQuery;
    private Button takePhoto;

    private ImageView imageView;

    private TextView nameText;
    private TextView sexText;
    private TextView bornData;
    private TextView addressText;
    private TextView cardId;

    private ProgressDialog pd;

    private DiscernHandler discernHandler;

    private Uri imageUri;

    String picPath = null;

    String result = null;

    private HWCloudManager hwCloudManagerIdcard;//请求发起对象

    private List<String> permissionList;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        ActionBar actionBar=getSupportActionBar();
        if(actionBar!=null){
            actionBar.hide();
        }

        hwCloudManagerIdcard = new HWCloudManager(this, "e29f4a69-921d-4acb-969a-8c464e2666d2");
        discernHandler = new DiscernHandler();

        choosePhoto = (Button) findViewById(R.id.choose_photo);
        startQuery = (Button) findViewById(R.id.start_query);
        takePhoto = (Button) findViewById(R.id.take_photo);

        imageView = (ImageView) findViewById(R.id.image_view);

        nameText = (TextView) findViewById(R.id.name);
        sexText = (TextView) findViewById(R.id.sex);
        bornData = (TextView) findViewById(R.id.born_data);
        addressText = (TextView) findViewById(R.id.address);
        cardId = (TextView) findViewById(R.id.card_id);


        choosePhoto.setOnClickListener(this);
        startQuery.setOnClickListener(this);
        takePhoto.setOnClickListener(this);
    }

    @Override
    public void onClick(View view) {
        switch (view.getId()) {

            case R.id.take_photo: {//照相
                permissionList=new ArrayList<>();//请求权限
                if (ContextCompat.checkSelfPermission(MainActivity.this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                    permissionList.add(Manifest.permission.CAMERA);
                }
                if (ContextCompat.checkSelfPermission(MainActivity.this,Manifest.permission.WRITE_EXTERNAL_STORAGE)!=PackageManager.PERMISSION_GRANTED){
                    permissionList.add(Manifest.permission.WRITE_EXTERNAL_STORAGE);
                }
                if (!permissionList.isEmpty()){
                    String[] permissions=permissionList.toArray(new String[permissionList.size()]);
                    ActivityCompat.requestPermissions(MainActivity.this,permissions,1);
                }
                else {
                    takePhoto();
                }
            }
            break;

            case R.id.choose_photo://选择图片
                if (ContextCompat.checkSelfPermission(MainActivity.this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                    ActivityCompat.requestPermissions(MainActivity.this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, 0);
                } else {
                    openAlbum();
                }
                break;

            case R.id.start_query:
                pd = ProgressDialog.show(MainActivity.this, "", "正在识别请稍后......");
                DiscernThread discernThread = new DiscernThread();
                new Thread(discernThread).start();
                break;
        }
    }

    public class DiscernThread implements Runnable {

        public void run() {
            try {
                result = hwCloudManagerIdcard.idCardLanguage(picPath);

            } catch (Exception e) {
                e.printStackTrace();
            }
            Bundle mBundle = new Bundle();
            mBundle.putString("responce", result);
            Message msg = new Message();
            msg.setData(mBundle);
            discernHandler.sendMessage(msg);

        }

    }


    public class DiscernHandler extends Handler {
        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            pd.dismiss();
            Bundle bundle = msg.getData();
            String responce = bundle.getString("responce");
            parseJSONWithGSON(responce);
        }
    }


    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (requestCode) {
            case TAKE_PHOTO:
                if (resultCode == RESULT_OK) {
                    displayImage(picPath);
                }
                break;

            case CHOOSE_PHOTO:
                if (resultCode == RESULT_OK) {
                    if (Build.VERSION.SDK_INT >= 19) {
                        handleImageOnKitKat(data);
                        //也可以用before
                    } else {
                        handleImageBeforeKitKat(data);
                    }
                }
                break;
            default:
                break;
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.KITKAT)
    private void handleImageOnKitKat(Intent data) {
        Uri uri = data.getData();
        if (DocumentsContract.isDocumentUri(this, uri)) {
            String docId = DocumentsContract.getDocumentId(uri);
            if ("com.android.providers.media.documents".equals(uri.getAuthority())) {
                String id = docId.split(":")[1];
                String selection = MediaStore.Images.Media._ID + "=" + id;
                picPath = getImagePath(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, selection);
            } else if ("com.android.providers.downloads.documents".equals(uri.getAuthority())) {
                Uri contentUri = ContentUris.withAppendedId(Uri.parse("content://downloads/public_downloads"), Long.valueOf(docId));
                picPath = getImagePath(contentUri, null);
            }
        } else if ("content".equalsIgnoreCase(uri.getScheme())) {
            picPath = getImagePath(uri, null);
        } else if ("file".equalsIgnoreCase(uri.getScheme())) {
            picPath = uri.getPath();
        } else {
            getImagePath(uri, null);

        }
        displayImage(picPath);
    }

    private void handleImageBeforeKitKat(Intent data) {
        Uri uri = data.getData();
        picPath = getImagePath(uri, null);
        displayImage(picPath);
    }

    private String getImagePath(Uri uri, String select) {
        String path = null;
        Cursor cursor = getContentResolver().query(uri, null, select, null, null);
        if (cursor != null) {
            if (cursor.moveToFirst()) {
                path = cursor.getString(cursor.getColumnIndex(MediaStore.Images.Media.DATA));
            }
            cursor.close();
        }
        return path;
    }

    private void displayImage(String imagePath) {
        if (imagePath != null) {
            Bitmap bitmap = BitmapFactory.decodeFile(imagePath);
            imageView.setImageBitmap(bitmap);
        } else {
            Toast.makeText(MainActivity.this, "failed to get image", Toast.LENGTH_SHORT).show();
        }
    }

    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        switch (requestCode){
            case 0:{//选择图片权限
                    if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                        openAlbum();
                    } else {
                        Toast.makeText(MainActivity.this, "you denied the permission", Toast.LENGTH_SHORT).show();
                    }
                }
                break;

            case 1:{//拍照权限
                    if (grantResults.length > 0) {
                        for (int result:grantResults){
                            if(result!=PackageManager.PERMISSION_GRANTED){
                                Toast.makeText(this,"必须同意所有权限才能使用本程序",Toast.LENGTH_SHORT).show();
                                finish();
                                return;
                            }
                        }
                        takePhoto();
                    } else {
                        Toast.makeText(MainActivity.this, "you denied the permission", Toast.LENGTH_SHORT).show();
                        finish();
                    }
                }
                break;

            default:
                break;
            }


        }

    public void takePhoto() {
        final String CACHE_IMG = Environment.getExternalStorageDirectory() + "/demo/";//获取sd卡路径
        String fileName = "defaultImage.jpg";//存储在存储卡里面
        File outputImage = new File(CACHE_IMG, fileName);//创建存储照片的对象
        //File outputImage=new File(getExternalCacheDir(),"output_image.jpg");//存储在关联目录里面，可以跳过权限申请。
        picPath = outputImage.getPath();//得到拍照的真实路径
        try {
            if (!outputImage.exists()) {
                outputImage.mkdirs();
            }
            if ((outputImage.exists())) {
                outputImage.delete();
            }
            outputImage.createNewFile();
        } catch (IOException e) {
            e.printStackTrace();
        }
        if (Build.VERSION.SDK_INT >= 24) {
            imageUri = FileProvider.getUriForFile(MainActivity.this, "com.example.cameraalbumtest.fileprovider", outputImage);
            //获取封装后的Uri
        } else {
            imageUri = Uri.fromFile(outputImage);//获取Uri
        }
        Intent intent = new Intent();
        intent.setAction("android.media.action.IMAGE_CAPTURE");//启动相机
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);//能在这个程序里面使用这个Uri
        intent.putExtra(MediaStore.EXTRA_OUTPUT, imageUri);//让相机把图片存储在这个位置。
        startActivityForResult(intent, TAKE_PHOTO);

    }

    private void openAlbum() {
        //Intent intent=new Intent();
        Intent intent = new Intent(Intent.ACTION_PICK, android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        //intent.setAction("android.intent.action.GET_CONTENT");//调用文件管理器的样子
        //intent.setType("image/*");

        //Intent intent = new Intent();//打开相册的样子
        //intent.setAction(Intent.ACTION_PICK);
        //intent.setType("image/*");
        startActivityForResult(intent, CHOOSE_PHOTO);
    }

    public void parseJSONWithGSON(String jsonData){
        Gson gson=new Gson();

        Result result=gson.fromJson(jsonData,Result.class);

        nameText.setText(result.getName());
        sexText.setText(result.getGender());
        bornData.setText(result.getByear()+"年"+result.getBmonth()+"月"+result.getBday()+"日");
        addressText.setText(result.getAddress());
        cardId.setText(result.getIdnumber());


    }

}
