package com.google.zxing.view;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.drawable.BitmapDrawable;
import android.util.AttributeSet;
import android.view.View;

import com.google.zxing.R;
import com.google.zxing.ResultPoint;
import com.google.zxing.camera.CameraManager;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;



/**
 * This view is overlaid on top of the camera preview. It adds the viewfinder
 * rectangle and partial transparency outside it, as well as the laser scanner
 * animation and result points.
 * <p/>
 * <br/>
 * <br/>
 * 该视图是覆盖在相机的预览视图之上的一层视图。扫描区构成原理，其实是在预览视图上画四块遮罩层，
 * 中间留下的部分保持透明，并画上一条激光线，实际上该线条就是展示而已，与扫描功能没有任何关系。
 *
 * @author dswitkin@google.com (Daniel Switkin)
 */
public final class ViewfinderView extends View {

    /**
     * 刷新界面的时间
     */
    private static final long ANIMATION_DELAY = 10L;
    private static final int OPAQUE = 0xFF;

    private int CORNER_PADDING;

    /**
     * 扫描框中的中间线的宽度
     */
    private static int MIDDLE_LINE_WIDTH;

    /**
     * 扫描框中的中间线的与扫描框左右的间隙
     */
    private static int MIDDLE_LINE_PADDING;

    /**
     * 中间那条线每次刷新移动的距离
     */
    private static final int SPEEN_DISTANCE = 10;

    /**
     * 画笔对象的引用
     */
    private Paint paint;

    /**
     * 中间滑动线的最顶端位置
     */
    private int slideTop;

    /**
     * 中间滑动线的最底端位置
     */
    private int slideBottom;

    private static final int MAX_RESULT_POINTS = 20;

    private Bitmap resultBitmap;

    /**
     * 遮掩层的颜色
     */
    private final int maskColor;
    private final int resultColor;

    private final int resultPointColor;
    private List<ResultPoint> possibleResultPoints;

    private List<ResultPoint> lastPossibleResultPoints;

    /**
     * 第一次绘制控件
     */
    boolean isFirst = true;

    private CameraManager cameraManager;

    // This constructor is used when the class is built from an XML resource.
    public ViewfinderView(Context context, AttributeSet attrs) {
        super(context, attrs);

        CORNER_PADDING = dip2px(context, 0.0F);
        MIDDLE_LINE_PADDING = dip2px(context, 20.0F);
        MIDDLE_LINE_WIDTH = dip2px(context, 3.0F);

        paint = new Paint(Paint.ANTI_ALIAS_FLAG); // 开启反锯齿

        Resources resources = getResources();
        maskColor = resources.getColor(R.color.viewfinder_mask); // 遮掩层颜色
        resultColor = resources.getColor(R.color.result_view);

        resultPointColor = resources.getColor(R.color.possible_result_points);
        possibleResultPoints = new ArrayList<ResultPoint>(5);
        lastPossibleResultPoints = null;

    }

    public void setCameraManager(CameraManager cameraManager) {
        this.cameraManager = cameraManager;
    }

    @Override
    public void onDraw(Canvas canvas) {
        if (cameraManager == null) {
            return; // not ready yet, early draw before done configuring
        }
        Rect frame = cameraManager.getFramingRect();
        if (frame == null) {
            return;
        }

        // 绘制遮掩层
        drawCover(canvas, frame);

        if (resultBitmap != null) { // 绘制扫描结果的图
            // Draw the opaque result bitmap over the scanning rectangle
            paint.setAlpha(0xA0);
            canvas.drawBitmap(resultBitmap, null, frame, paint);
        } else {

            // 画扫描框边上的角
            drawRectEdges(canvas, frame);

            // 绘制扫描线
            drawScanningLine(canvas, frame);

            List<ResultPoint> currentPossible = possibleResultPoints;
            Collection<ResultPoint> currentLast = lastPossibleResultPoints;
            if (currentPossible.isEmpty()) {
                lastPossibleResultPoints = null;
            } else {
                possibleResultPoints = new ArrayList<ResultPoint>(5);
                lastPossibleResultPoints = currentPossible;
                paint.setAlpha(OPAQUE);
                paint.setColor(resultPointColor);
                for (ResultPoint point : currentPossible) {
                    canvas.drawCircle(frame.left + point.getX(), frame.top
                            + point.getY(), 6.0f, paint);
                }
            }
            if (currentLast != null) {
                paint.setAlpha(OPAQUE / 2);
                paint.setColor(resultPointColor);
                for (ResultPoint point : currentLast) {
                    canvas.drawCircle(frame.left + point.getX(), frame.top
                            + point.getY(), 3.0f, paint);
                }
            }

            // 只刷新扫描框的内容，其他地方不刷新
            postInvalidateDelayed(ANIMATION_DELAY, frame.left, frame.top,
                    frame.right, frame.bottom);

        }
    }

    /**
     * 绘制扫描线
     *
     * @param canvas
     * @param frame  扫描框
     */
    private void drawScanningLine(Canvas canvas, Rect frame) {

        // 初始化中间线滑动的最上边和最下边
        if (isFirst) {
            isFirst = false;
            slideTop = frame.top;
            slideBottom = frame.bottom;
        }

        // 绘制中间的线,每次刷新界面，中间的线往下移动SPEEN_DISTANCE
        slideTop += SPEEN_DISTANCE;
        if (slideTop >= slideBottom) {
            slideTop = frame.top;
        }

        // 从图片资源画扫描线
        Rect lineRect = new Rect();
        lineRect.left = frame.left + MIDDLE_LINE_PADDING;
        lineRect.right = frame.right - MIDDLE_LINE_PADDING;
        lineRect.top = slideTop;
        lineRect.bottom = (slideTop + MIDDLE_LINE_WIDTH);
        canvas.drawBitmap(((BitmapDrawable) getResources()
                        .getDrawable(R.drawable.scan_laser)).getBitmap(), null,
                lineRect, paint);

    }

    /**
     * 绘制遮掩层
     *
     * @param canvas
     * @param frame
     */
    private void drawCover(Canvas canvas, Rect frame) {

        // 获取屏幕的宽和高
        int width = canvas.getWidth();
        int height = canvas.getHeight();

        // Draw the exterior (i.e. outside the framing rect) darkened
        paint.setColor(resultBitmap != null ? resultColor : maskColor);

        // 画出扫描框外面的阴影部分，共四个部分，扫描框的上面到屏幕上面，扫描框的下面到屏幕下面
        // 扫描框的左边面到屏幕左边，扫描框的右边到屏幕右边
        canvas.drawRect(0, 0, width, frame.top, paint);
        canvas.drawRect(0, frame.top, frame.left, frame.bottom + 1, paint);
        canvas.drawRect(frame.right + 1, frame.top, width, frame.bottom + 1,
                paint);
        canvas.drawRect(0, frame.bottom + 1, width, height, paint);
    }

    /**
     * 描绘方形的四个角
     *
     * @param canvas
     * @param frame
     */
    private void drawRectEdges(Canvas canvas, Rect frame) {

        paint.setColor(Color.WHITE);
        paint.setAlpha(OPAQUE);

        Resources resources = getResources();
        /**
         * 这些资源可以用缓存进行管理，不需要每次刷新都新建
         */
        Bitmap bitmapCornerTopleft = BitmapFactory.decodeResource(resources,
                R.drawable.scan_corner_top_left);
        Bitmap bitmapCornerTopright = BitmapFactory.decodeResource(resources,
                R.drawable.scan_corner_top_right);
        Bitmap bitmapCornerBottomLeft = BitmapFactory.decodeResource(resources,
                R.drawable.scan_corner_bottom_left);
        Bitmap bitmapCornerBottomRight = BitmapFactory.decodeResource(
                resources, R.drawable.scan_corner_bottom_right);

        canvas.drawBitmap(bitmapCornerTopleft, frame.left + CORNER_PADDING,
                frame.top + CORNER_PADDING, paint);
        canvas.drawBitmap(bitmapCornerTopright, frame.right - CORNER_PADDING
                        - bitmapCornerTopright.getWidth(), frame.top + CORNER_PADDING,
                paint);
        canvas.drawBitmap(bitmapCornerBottomLeft, frame.left + CORNER_PADDING,
                2 + (frame.bottom - CORNER_PADDING - bitmapCornerBottomLeft
                        .getHeight()), paint);
        canvas.drawBitmap(bitmapCornerBottomRight, frame.right - CORNER_PADDING
                - bitmapCornerBottomRight.getWidth(), 2 + (frame.bottom
                - CORNER_PADDING - bitmapCornerBottomRight.getHeight()), paint);

        bitmapCornerTopleft.recycle();
        bitmapCornerTopleft = null;
        bitmapCornerTopright.recycle();
        bitmapCornerTopright = null;
        bitmapCornerBottomLeft.recycle();
        bitmapCornerBottomLeft = null;
        bitmapCornerBottomRight.recycle();
        bitmapCornerBottomRight = null;

    }

    public void drawViewfinder() {
        Bitmap resultBitmap = this.resultBitmap;
        this.resultBitmap = null;
        if (resultBitmap != null) {
            resultBitmap.recycle();
        }
        invalidate();
    }

    /**
     * Draw a bitmap with the result points highlighted instead of the live
     * scanning display.
     *
     * @param barcode An image of the decoded barcode.
     */
    public void drawResultBitmap(Bitmap barcode) {
        resultBitmap = barcode;
        invalidate();
    }

    public void addPossibleResultPoint(ResultPoint point) {
        List<ResultPoint> points = possibleResultPoints;
        synchronized (points) {
            points.add(point);
            int size = points.size();
            if (size > MAX_RESULT_POINTS) {
                // trim it
                points.subList(0, size - MAX_RESULT_POINTS / 2).clear();
            }
        }
    }

    /**
     * dp转px
     *
     * @param context
     * @param dipValue
     * @return
     */
    public int dip2px(Context context, float dipValue) {
        final float scale = context.getResources().getDisplayMetrics().density;
        return (int) (dipValue * scale + 0.5f);
    }

}
