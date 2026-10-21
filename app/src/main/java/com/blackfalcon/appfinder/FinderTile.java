package com.blackfalcon.appfinder;

import android.annotation.TargetApi;
import android.app.PendingIntent;
import android.content.Intent;
import android.graphics.drawable.Icon;
import android.os.Build;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;

/** Quick Settings tile (like Wi-Fi) that opens the app with the search box focused. */
public class FinderTile extends TileService {

    @Override
    public void onStartListening() {
        Tile t = getQsTile();
        if (t == null) return;
        Store st = new Store(this);
        t.setLabel(st.tr("ایپ تلاش", "Find App", "بحث عن تطبيق"));
        t.setIcon(Icon.createWithResource(this, R.drawable.ic_tile));
        t.setState(Tile.STATE_ACTIVE);
        t.updateTile();
    }

    @Override
    public void onClick() {
        final Intent i = new Intent(this, MainActivity.class);
        i.putExtra("focus", true);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        Runnable r = () -> launch(i);
        if (isLocked()) unlockAndRun(r);
        else r.run();
    }

    void launch(Intent i) {
        if (Build.VERSION.SDK_INT >= 34) launch34(i);
        else launchOld(i);
    }

    @TargetApi(34)
    void launch34(Intent i) {
        PendingIntent pi = PendingIntent.getActivity(this, 0, i,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        startActivityAndCollapse(pi);
    }

    @SuppressWarnings("deprecation")
    void launchOld(Intent i) {
        startActivityAndCollapse(i);
    }
}
