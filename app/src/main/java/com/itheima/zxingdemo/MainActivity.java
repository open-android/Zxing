package com.itheima.zxingdemo;

import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.WriterException;
import com.google.zxing.activity.CaptureActivity;
import com.google.zxing.common.BitMatrix;

import java.util.Hashtable;

public class MainActivity extends AppCompatActivity implements View.OnClickListener {

    private Button mBtn1;
    private EditText mEt;
    private Button mBtn2;
    private ImageView mImage;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        initView();
    }

    private void initView() {
        mBtn1 = (Button) findViewById(R.id.btn1);

        mBtn1.setOnClickListener(this);
        mEt = (EditText) findViewById(R.id.et);
        mBtn2 = (Button) findViewById(R.id.btn2);
        mBtn2.setOnClickListener(this);
        mImage = (ImageView) findViewById(R.id.image);
        mImage.setOnClickListener(this);
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.btn1:
                startActivity(new Intent(MainActivity.this, CaptureActivity.class));
                break;
            case R.id.btn2:
                String content = mEt.getText().toString().trim();
                try {
                    Bitmap bitmap = Create2DCode(content);
                    mImage.setImageBitmap(bitmap);
                } catch (WriterException e) {
                    e.printStackTrace();
                }
                break;
        }
    }

    /**
     * 用字符串生成二维码
     *
     * @param str
     * @return
     * @throws WriterException
     */
    public Bitmap Create2DCode(String str) throws WriterException {
        Hashtable<EncodeHintType, String> hints = new Hashtable<EncodeHintType, String>();
        hints.put(EncodeHintType.CHARACTER_SET, "UTF-8");
        // 生成二维矩阵,编码时指定大小,不要生成了图片以后再进行缩放,这样会模糊导致识别失败
        BitMatrix matrix = new MultiFormatWriter().encode(str, BarcodeFormat.QR_CODE, 480, 480, hints);
        int width = matrix.getWidth();
        int height = matrix.getHeight();
        // 二维矩阵转为一维像素数组,也就是一直横着排了
        int[] pixels = new int[width * height];
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                if (matrix.get(x, y)) {
                    pixels[y * width + x] = 0xff000000;
                }
            }
        }

        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        // 通过像素数组生成bitmap,具体参考api
        bitmap.setPixels(pixels, 0, width, 0, 0, width, height);
        return bitmap;
    }

}
