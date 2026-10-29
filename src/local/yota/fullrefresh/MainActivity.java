package local.yota.fullrefresh;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

public final class MainActivity extends Activity {
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        int padding = (int) (24 * getResources().getDisplayMetrics().density);
        content.setPadding(padding, padding, padding, padding);
        TextView heading = new TextView(this);
        heading.setText("yota+ refresh");
        heading.setTextSize(26);
        content.addView(heading);
        TextView instructions = new TextView(this);
        instructions.setText("在墨水屏显示一个小刷新图标。\n\n点击图标：请求一次全刷。\n拖动：调整图标位置。\n长按：关闭悬浮图标。\n\n首次使用请允许“显示在其他应用上层”。也可从常驻通知关闭。");
        instructions.setTextSize(17);
        content.addView(instructions);
        Button start = new Button(this);
        start.setText("显示悬浮按钮");
        start.setOnClickListener(v -> {
            if (!Settings.canDrawOverlays(this)) {
                startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName())));
            } else {
                startForegroundService(new Intent(this, RefreshService.class));
                finish();
            }
        });
        content.addView(start);
        Button stop = new Button(this);
        stop.setText("关闭悬浮按钮");
        stop.setOnClickListener(v -> stopService(new Intent(this, RefreshService.class)));
        content.addView(stop);
        setContentView(content);
    }
}
