package local.yota.fullrefresh;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.Point;
import android.hardware.display.DisplayManager;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.provider.Settings;
import android.util.Log;
import android.view.Display;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.Toast;
import java.lang.reflect.Method;

public final class RefreshService extends Service {
    private static final String TAG = "YotaFullRefresh";
    private static final String STOP = "local.yota.fullrefresh.STOP";
    private static final String REFRESH = "local.yota.fullrefresh.REFRESH";
    private final Handler handler = new Handler(Looper.getMainLooper());
    private WindowManager windows;
    private Context epdContext;
    private ImageView button;
    private View refreshWindow;
    private WindowManager.LayoutParams position;
    private Class<?> epd;
    private Display display;
    private boolean busy;
    private boolean moved;
    private boolean held;
    private float downX, downY;
    private int originalX, originalY;
    private final Runnable longPress = () -> {
        if (!moved) {
            held = true;
            stopSelf();
        }
    };

    @Override public IBinder onBind(Intent intent) { return null; }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && STOP.equals(intent.getAction())) {
            stopSelf();
            return START_NOT_STICKY;
        }
        showNotification();
        if (button == null) {
            try {
                if (!Settings.canDrawOverlays(this)) throw new IllegalStateException("请先允许悬浮窗权限");
                epd = Class.forName("com.yotadevices.sdk.Epd");
                display = findEpd();
                if (display == null) throw new IllegalStateException("未找到 Yota 墨水屏");
                epdContext = createDisplayContext(display);
                windows = (WindowManager) epdContext.getSystemService(Context.WINDOW_SERVICE);
                createButton();
                Log.i(TAG, "overlay ready display=" + display.getDisplayId() + " name=" + display.getName());
            } catch (Exception error) {
                fail("无法显示刷新按钮", error);
                stopSelf();
            }
        }
        if (intent != null && REFRESH.equals(intent.getAction()) && button != null)
            handler.post(this::requestRefresh);
        return START_NOT_STICKY;
    }

    private Display findEpd() throws Exception {
        DisplayManager manager = (DisplayManager) getSystemService(DISPLAY_SERVICE);
        Method isEpd = epd.getMethod("isEpdDisplay", Display.class);
        for (Display candidate : manager.getDisplays()) {
            if (Boolean.TRUE.equals(isEpd.invoke(null, candidate))) return candidate;
        }
        for (Display candidate : manager.getDisplays()) {
            if (candidate.getDisplayId() != Display.DEFAULT_DISPLAY &&
                candidate.getName().toLowerCase(java.util.Locale.ROOT).contains("epd")) return candidate;
        }
        return null;
    }

    private void showNotification() {
        NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        NotificationChannel channel = new NotificationChannel("refresh", "悬浮刷新按钮", NotificationManager.IMPORTANCE_LOW);
        channel.setShowBadge(false);
        manager.createNotificationChannel(channel);
        PendingIntent close = PendingIntent.getService(this, 1,
            new Intent(this, RefreshService.class).setAction(STOP), PendingIntent.FLAG_UPDATE_CURRENT);
        PendingIntent settings = PendingIntent.getActivity(this, 0,
            new Intent(this, MainActivity.class), PendingIntent.FLAG_UPDATE_CURRENT);
        Notification notification = new Notification.Builder(this, "refresh")
            .setSmallIcon(android.R.drawable.ic_popup_sync)
            .setContentTitle("yota+ refresh 已开启")
            .setContentText("点击背屏刷新图标请求全刷；长按图标关闭")
            .setContentIntent(settings).setOngoing(true)
            .addAction(new Notification.Action.Builder(android.R.drawable.ic_menu_close_clear_cancel, "关闭", close).build())
            .build();
        startForeground(1, notification);
    }

    private int dp(int value) {
        return Math.round(value * epdContext.getResources().getDisplayMetrics().density);
    }

    private void createButton() {
        button = new ImageView(epdContext);
        button.setImageDrawable(new RefreshIconDrawable());
        button.setScaleType(ImageView.ScaleType.FIT_CENTER);
        button.setContentDescription("点击全刷，拖动移动，长按关闭");
        position = new WindowManager.LayoutParams(dp(22), dp(22),
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, PixelFormat.TRANSLUCENT);
        position.gravity = Gravity.TOP | Gravity.LEFT;
        position.setTitle("YotaFullRefreshButton");
        SharedPreferences saved = getSharedPreferences("position", MODE_PRIVATE);
        position.x = saved.getInt("x", dp(8));
        position.y = saved.getInt("y", dp(100));
        clampPosition();
        button.setOnTouchListener((v, event) -> {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    downX = event.getRawX(); downY = event.getRawY();
                    originalX = position.x; originalY = position.y;
                    moved = false; held = false;
                    handler.postDelayed(longPress, 900);
                    return true;
                case MotionEvent.ACTION_MOVE:
                    float dx = event.getRawX() - downX, dy = event.getRawY() - downY;
                    if (Math.hypot(dx, dy) > dp(6)) moved = true;
                    if (moved && button != null) {
                        handler.removeCallbacks(longPress);
                        position.x = originalX + Math.round(dx);
                        position.y = originalY + Math.round(dy);
                        clampPosition();
                        windows.updateViewLayout(button, position);
                    }
                    return true;
                case MotionEvent.ACTION_UP:
                    handler.removeCallbacks(longPress);
                    if (!held) {
                        if (moved) getSharedPreferences("position", MODE_PRIVATE).edit()
                            .putInt("x", position.x).putInt("y", position.y).apply();
                        else requestRefresh();
                    }
                    return true;
                case MotionEvent.ACTION_CANCEL:
                    handler.removeCallbacks(longPress);
                    return true;
                default: return true;
            }
        });
        windows.addView(button, position);
    }

    private void clampPosition() {
        Point size = new Point();
        display.getSize(size);
        position.x = Math.max(0, Math.min(position.x, size.x - position.width));
        position.y = Math.max(0, Math.min(position.y, size.y - position.height - dp(24)));
    }

    private void requestRefresh() {
        if (busy || windows == null) return;
        busy = true;
        try {
            // A separate display-sized window submits the full-update request while
            // leaving the reader visible and its focus, scroll and input untouched.
            // Alpha 1/255 keeps the layer composited rather than optimized away.
            refreshWindow = new View(epdContext);
            refreshWindow.setBackgroundColor(Color.argb(1, 255, 255, 255));
            WindowManager.LayoutParams layout = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE |
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN |
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT);
            layout.gravity = Gravity.TOP | Gravity.LEFT;
            layout.setTitle("YotaFullRefreshRequest");
            windows.addView(refreshWindow, layout);
            refreshWindow.postDelayed(() -> {
                try {
                    if (refreshWindow == null || !refreshWindow.isAttachedToWindow())
                        throw new IllegalStateException("刷新窗口未连接");
                    epd.getMethod("setViewDithering", View.class, int.class).invoke(null, refreshWindow, 1);
                    epd.getMethod("fullUpdate", View.class, boolean.class).invoke(null, refreshWindow, true);
                    Object params = epd.getMethod("getEpdUpdateParams", View.class).invoke(null, refreshWindow);
                    Object type = params.getClass().getMethod("getUpdateType").invoke(params);
                    // Y54 sends type 4 through requestEpdUpdate (one shot), not
                    // setEpdParams. Reading the persistent type is not an ACK.
                    Log.i(TAG, "fullUpdate request returned display=" + display.getDisplayId()
                        + " requestedType=4 persistentType=" + type);
                } catch (Exception error) { fail("全刷请求失败", error); }
                handler.postDelayed(this::finishRefresh, 1200);
            }, 80);
        } catch (Exception error) {
            fail("全刷请求失败", error);
            finishRefresh();
        }
    }

    private void finishRefresh() {
        if (refreshWindow != null && windows != null) {
            try { windows.removeViewImmediate(refreshWindow); }
            catch (Exception error) { Log.w(TAG, "remove refresh layer", error); }
            refreshWindow = null;
        }
        busy = false;
    }

    private void fail(String message, Exception error) {
        Log.e(TAG, message, error);
        Toast.makeText(this, message + "：" + error.getClass().getSimpleName(), Toast.LENGTH_LONG).show();
    }

    @Override public void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        finishRefresh();
        if (button != null && windows != null) {
            try { windows.removeViewImmediate(button); }
            catch (Exception error) { Log.w(TAG, "remove button", error); }
            button = null;
        }
        stopForeground(true);
        super.onDestroy();
    }
}
