/*
 * Copyright (C) 2008 ZXing authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.zxing.decode;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.provider.Browser;
import android.util.Log;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.DecodeHintType;
import com.google.zxing.R;
import com.google.zxing.Result;
import com.google.zxing.camera.CameraManager;
import com.google.zxing.view.ViewfinderResultPointCallback;
import com.google.zxing.activity.CaptureActivity;

import java.util.Collection;
import java.util.Map;


/**
 * This class handles all the messaging which comprises the state machine for
 * capture.
 *
 * @author dswitkin@google.com (Daniel Switkin)
 */
public final class CaptureActivityHandler extends Handler {

    private static final String TAG = CaptureActivityHandler.class
            .getSimpleName();

    private final CaptureActivity activity;

    /**
     * 真正负责扫描任务的核心线程
     */
    private final DecodeThread decodeThread;

    private State state;

    private final CameraManager cameraManager;

    /**
     * 当前扫描的状态
     */
    private enum State {
        /**
         * 预览
         */
        PREVIEW,
        /**
         * 扫描成功
         */
        SUCCESS,
        /**
         * 结束扫描
         */
        DONE
    }

    public CaptureActivityHandler(CaptureActivity activity,
                                  Collection<BarcodeFormat> decodeFormats,
                                  Map<DecodeHintType, ?> baseHints, String characterSet,
                                  CameraManager cameraManager) {
        this.activity = activity;

        // 启动扫描线程
        decodeThread = new DecodeThread(activity, decodeFormats, baseHints,
                characterSet, new ViewfinderResultPointCallback(
                activity.getViewfinderView()));
        decodeThread.start();

        state = State.SUCCESS;

        // Start ourselves capturing previews and decoding.
        this.cameraManager = cameraManager;

        // 开启相机预览界面
        cameraManager.startPreview();

        restartPreviewAndDecode();
    }

    @Override
    public void handleMessage(Message message) {
        if (message.what == R.id.restart_preview) { // 准备进行下一次扫描
            Log.d(TAG, "Got restart preview message");
            restartPreviewAndDecode();
        } else if (message.what == R.id.decode_succeeded) {
            Log.d(TAG, "Got decode succeeded message");
            state = State.SUCCESS;
            Bundle bundle = message.getData();
            Bitmap barcode = null;
            float scaleFactor = 1.0f;
            if (bundle != null) {
                byte[] compressedBitmap = bundle
                        .getByteArray(DecodeThread.BARCODE_BITMAP);
                if (compressedBitmap != null) {
                    barcode = BitmapFactory.decodeByteArray(
                            compressedBitmap, 0, compressedBitmap.length,
                            null);
                    // Mutable copy:
                    barcode = barcode.copy(Bitmap.Config.ARGB_8888, true);
                }
                scaleFactor = bundle
                        .getFloat(DecodeThread.BARCODE_SCALED_FACTOR);
            }
            activity.handleDecode((Result) message.obj, barcode,
                    scaleFactor);
        } else if (message.what == R.id.decode_failed) {
            // We're decoding as fast as possible, so when one decode fails,
            // start another.
            state = State.PREVIEW;
            cameraManager.requestPreviewFrame(decodeThread.getHandler(),
                    R.id.decode);
        } else if (message.what == R.id.return_scan_result) {
            Log.d(TAG, "Got return scan result message");
            activity.setResult(Activity.RESULT_OK, (Intent) message.obj);
            activity.finish();
        } else if (message.what == R.id.launch_product_query) {
            Log.d(TAG, "Got product query message");
            String url = (String) message.obj;

            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_WHEN_TASK_RESET);
            intent.setData(Uri.parse(url));

            /**
             * 这段代码是zxing项目组想要用chrome打开浏览器浏览url
             */
            ResolveInfo resolveInfo = activity.getPackageManager()
                    .resolveActivity(intent,
                            PackageManager.MATCH_DEFAULT_ONLY);
            String browserPackageName = null;
            if (resolveInfo != null && resolveInfo.activityInfo != null) {
                browserPackageName = resolveInfo.activityInfo.packageName;
                Log.d(TAG, "Using browser in package " + browserPackageName);
            }

            // Needed for default Android browser / Chrome only apparently
            if ("com.android.browser".equals(browserPackageName)
                    || "com.android.chrome".equals(browserPackageName)) {
                intent.setPackage(browserPackageName);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                intent.putExtra(Browser.EXTRA_APPLICATION_ID,
                        browserPackageName);
            }

            try {
                activity.startActivity(intent);
            } catch (ActivityNotFoundException ignored) {
                Log.w(TAG, "Can't find anything to handle VIEW of URI "
                        + url);
            }
        }
    }

    public void quitSynchronously() {
        state = State.DONE;
        cameraManager.stopPreview();
        Message quit = Message.obtain(decodeThread.getHandler(), R.id.quit);
        quit.sendToTarget();

        try {
            // Wait at most half a second; should be enough time, and onPause()
            // will timeout quickly
            decodeThread.join(500L);
        } catch (InterruptedException e) {
            // continue
        }

        // Be absolutely sure we don't send any queued up messages
        removeMessages(R.id.decode_succeeded);
        removeMessages(R.id.decode_failed);
    }

    /**
     * 完成一次扫描后，只需要再调用此方法即可
     */
    private void restartPreviewAndDecode() {
        if (state == State.SUCCESS) {
            state = State.PREVIEW;

            // 向decodeThread绑定的handler（DecodeHandler)发送解码消息
            cameraManager.requestPreviewFrame(decodeThread.getHandler(),
                    R.id.decode);
            activity.drawViewfinder();
        }
    }

}
