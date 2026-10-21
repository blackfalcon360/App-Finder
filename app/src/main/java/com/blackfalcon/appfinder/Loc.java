package com.blackfalcon.appfinder;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ResolveInfo;
import android.database.Cursor;
import android.net.Uri;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;

/**
 * Best-effort: reads the home-screen launcher's own database (if the launcher allows it)
 * to find where an app's icon sits: dock, home-screen page, or inside a folder.
 */
public class Loc {
    public static final int UNREADABLE = 0, NOT_ON_HOME = 1, FOUND = 2;

    /** kind: "screen" | "dock" | "folder"; name = folder title; page = 1-based screen number. */
    public static class Place {
        public String kind = "screen";
        public String name = "";
        public int page = 0;
    }

    public int status = UNREADABLE;
    public final ArrayList<Place> places = new ArrayList<Place>();

    static final String[] AUTH = {
        "com.android.launcher3.settings",
        "com.android.launcher2.settings",
        "com.android.launcher.settings",
        "com.google.android.apps.nexuslauncher.settings",
        "com.sec.android.app.launcher.settings",
        "com.miui.home.launcher.settings",
        "com.huawei.android.launcher.settings",
        "com.oppo.launcher.settings",
        "com.bbk.launcher2.settings",
        "com.transsion.hilauncher.settings",
        "net.oneplus.launcher.settings",
        "com.teslacoilsw.launcher.settings"
    };

    public static Loc find(Context c, String pkg) {
        Loc res = new Loc();
        ArrayList<String> auths = new ArrayList<String>();
        try {
            Intent home = new Intent(Intent.ACTION_MAIN);
            home.addCategory(Intent.CATEGORY_HOME);
            ResolveInfo ri = c.getPackageManager().resolveActivity(home, 0);
            if (ri != null && ri.activityInfo != null) {
                auths.add(ri.activityInfo.packageName + ".settings");
            }
        } catch (Exception e) { }
        for (String a : AUTH) auths.add(a);

        HashSet<String> tried = new HashSet<String>();
        for (String a : auths) {
            if (!tried.add(a)) continue;
            Cursor cur = null;
            try {
                cur = c.getContentResolver().query(Uri.parse("content://" + a + "/favorites"),
                        null, null, null, null);
                if (cur == null) continue;
                if (read(cur, pkg, res)) return res;
            } catch (Exception e) {
                // SecurityException / IllegalArgumentException: launcher does not allow it
            } finally {
                if (cur != null) cur.close();
            }
        }
        return res;
    }

    // returns true if the table was readable (res.status then FOUND or NOT_ON_HOME)
    static boolean read(Cursor cur, String pkg, Loc res) {
        int iId = cur.getColumnIndex("_id");
        int iTitle = cur.getColumnIndex("title");
        int iIntent = cur.getColumnIndex("intent");
        int iCont = cur.getColumnIndex("container");
        int iScreen = cur.getColumnIndex("screen");
        int iType = cur.getColumnIndex("itemType");
        if (iId < 0 || iIntent < 0 || iCont < 0 || iScreen < 0) return false;

        ArrayList<long[]> matches = new ArrayList<long[]>(); // {container, screen}
        HashMap<Long, String> folderTitle = new HashMap<Long, String>();
        HashMap<Long, long[]> folderPos = new HashMap<Long, long[]>(); // {container, screen}
        String a = "component=" + pkg + "/";
        String b = "package=" + pkg + ";";
        while (cur.moveToNext()) {
            long id = cur.getLong(iId);
            long cont = cur.getLong(iCont);
            long screen = cur.getLong(iScreen);
            int type = iType >= 0 ? cur.getInt(iType) : 0;
            if (type == 2) {
                folderTitle.put(id, iTitle >= 0 ? cur.getString(iTitle) : "");
                folderPos.put(id, new long[] {cont, screen});
                continue;
            }
            String in = cur.getString(iIntent);
            if (in != null && (in.contains(a) || in.contains(b))) {
                matches.add(new long[] {cont, screen});
            }
        }
        for (long[] m : matches) {
            Place p = new Place();
            long cont = m[0];
            if (cont == -101) {
                p.kind = "dock";
            } else if (cont == -100) {
                p.kind = "screen";
                p.page = (int) m[1] + 1;
            } else {
                p.kind = "folder";
                String t = folderTitle.get(cont);
                p.name = t == null ? "" : t;
                long[] fp = folderPos.get(cont);
                if (fp != null && fp[0] == -100) p.page = (int) fp[1] + 1;
            }
            res.places.add(p);
        }
        res.status = res.places.isEmpty() ? NOT_ON_HOME : FOUND;
        return true;
    }
}
