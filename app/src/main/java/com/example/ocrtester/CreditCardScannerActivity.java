package com.example.ocrtester;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCaptureException;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;

import java.io.ByteArrayInputStream;
import java.nio.ByteBuffer;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


public class CreditCardScannerActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";
    private ListenableFuture<ProcessCameraProvider> cameraProviderFuture;
    private ExecutorService cameraExecutor;
    private PreviewView previewView;
    private TextView textView;
    private Button captureButton;
    private ImageCapture imageCapture;
    private static final int PERMISSION_REQUEST_CODE = 123; // 可以使用您自己的數字
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // 初始化相機執行緒池
        cameraExecutor = Executors.newSingleThreadExecutor();
        previewView = findViewById(R.id.previewView);
        textView = findViewById(R.id.textView);
        // 檢查權限
        if (arePermissionsGranted()) {
            doBind();
            //doBind2();
        } else {
            // 如果權限尚未授予，請求權限
            requestPermissions();
        }

        // 初始化拍攝按鈕
        captureButton = findViewById(R.id.captureButton);
        captureButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                captureImage();
            }
        });


    }
    private  void doBind(){
        // 如果權限已經授予，繼續執行相機相關操作
        // 取得相機提供者

        cameraProviderFuture = ProcessCameraProvider.getInstance(this);
        cameraProviderFuture.addListener(() -> {
            try {
                ProcessCameraProvider cameraProvider = cameraProviderFuture.get();
                bindPreview(cameraProvider);
                captureImage();

            } catch (ExecutionException | InterruptedException e) {
                Log.e(TAG, "Error starting camera preview: " + e.getMessage());
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private void bindPreview(@NonNull ProcessCameraProvider cameraProvider) {

        // 建立預覽用例
        Preview preview = new Preview.Builder()
                .build();
        // 創建影像捕獲用例
        imageCapture = new ImageCapture.Builder()
                .setTargetRotation(previewView.getDisplay().getRotation())
                .build();
        // 選擇後置相機
        CameraSelector cameraSelector = new CameraSelector.Builder()
                .requireLensFacing(CameraSelector.LENS_FACING_BACK)
                .build();

        try {
            // 綁定預覽用例到相機提供者
            preview.setSurfaceProvider(previewView.getSurfaceProvider());
            if(previewView != null)
            {
                Log.e(TAG, "previewView get" );
            }
            if(previewView.getSurfaceProvider() != null)
            {
                Log.e(TAG, "getSurfaceProvider!" );
            }
            // 解綁之前綁定的用例（如果有）
            cameraProvider.unbindAll();

            // 綁定預覽用例到相機提供者
            //cameraProvider.bindToLifecycle(this, cameraSelector, preview);
            cameraProvider.bindToLifecycle(this, cameraSelector, imageCapture, preview);
            Log.e(TAG, "Bind sucess" );

        } catch (Exception e) {
            Log.e(TAG, "Error binding preview: " + e.getMessage());
        }

    }
    private void captureImage() {
        // 檢查相機是否已綁定預覽
        if (previewView != null && previewView.getSurfaceProvider() != null) {


            // 拍攝影像
            imageCapture.takePicture(cameraExecutor, new ImageCapture.OnImageCapturedCallback() {
                @Override
                public void onCaptureSuccess(@NonNull ImageProxy image) {
                    // 在這裡處理拍攝到的影像並進行文字辨識

                    // 取得影像資料
                    ImageProxy.PlaneProxy[] planes = image.getPlanes();
                    ByteBuffer buffer = planes[0].getBuffer();
                    byte[] data = new byte[buffer.remaining()];
                    buffer.get(data);
                    BitmapFactory.Options options = new BitmapFactory.Options();
                    options.inJustDecodeBounds = false;
                    BitmapFactory.decodeByteArray(data, 0, data.length, options);

                    // 將影像資料轉換為 InputStream
                    ByteArrayInputStream inputStream = new ByteArrayInputStream(data);

                    // 使用 BitmapFactory.decodeStream 解碼影像資料
                    Bitmap bitmap = BitmapFactory.decodeStream(inputStream);

                    // 檢查 bitmap 是否為空
                    if (bitmap != null) {
                        // 进行 OCR 识别
                        InputImage inputImage = InputImage.fromBitmap(bitmap, image.getImageInfo().getRotationDegrees());
                        TextRecognizer textRecognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);

                        textRecognizer.process(inputImage)
                                .addOnSuccessListener(text -> {
                                    // 获取 OCR 结果
                                    String ocrResult = text.getText();
                                    // 提取信用卡号码
                                    String creditCardNumber = extractCreditCardNumber(ocrResult);
                                    // 在 UI 线程中更新 TextView
                                    runOnUiThread(() -> textView.setText(creditCardNumber));
                                })
                                .addOnFailureListener(e -> {
                                    // 处理 OCR 失败的情况
                                    Log.e("OCR", "OCR failed: " + e.getMessage());
                                });
                    } else {
                        Log.e("OCR", "Bitmap is null");
                    }

                    image.close();
                }

                @Override
                public void onError(@NonNull ImageCaptureException exception) {
                    // 處理拍攝錯誤
                    Log.e(TAG, "Error capturing image: " + exception.getMessage());
                }
            });
        } else {
            Log.e(TAG, "Preview view or surface provider is null");
        }
    }



    private void extractAndDisplayCreditCardNumber(String text) {
        // 提取信用卡号码
        String creditCardNumber = extractCreditCardNumber(text);

        // 在UI线程中更新TextView，显示提取的信用卡号码
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                textView.setText(creditCardNumber);
            }
        });
    }
    private String extractCreditCardNumber(String text) {
        // 使用正则表达式提取信用卡号码
        Pattern pattern = Pattern.compile("\\b\\d{4}[ -]?\\d{4}[ -]?\\d{4}[ -]?\\d{4}\\b");
        Matcher matcher = pattern.matcher(text);

        // 如果找到匹配的结果，则返回第一个匹配的信用卡号码
        if (matcher.find()) {
            return matcher.group();
        }

        // 如果未找到匹配的结果，则返回空字符串或其他适当的提示
        return "抓取號碼失敗，請重新對準後再拍攝";
    }
    private boolean arePermissionsGranted() {
        // 檢查相機權限
        boolean cameraPermission = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED;
        // 檢查儲存空間權限
        //boolean storagePermission = ContextCompat.checkSelfPermission(this, Manifest.permission.MANAGE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;

        return cameraPermission;
    }
    private void requestPermissions() {
        ActivityCompat.requestPermissions(this, new String[] {
                Manifest.permission.CAMERA,
                Manifest.permission.MANAGE_EXTERNAL_STORAGE
        }, PERMISSION_REQUEST_CODE);
    }
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (arePermissionsGranted()) {
                doBind();

            } else {
                // 如果權限未授予，可以顯示一個錯誤訊息或採取其他適當的操作
                Toast.makeText(this, "Permissions not granted", Toast.LENGTH_SHORT).show();
            }
        }
    }
    @Override
    protected void onDestroy() {
        super.onDestroy();
        // 释放相机资源
        cameraExecutor.shutdown();
    }
}