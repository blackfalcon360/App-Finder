package com.blackfalcon.appfinder;

import android.content.Context;
import android.content.SharedPreferences;

public class Store {
    private final SharedPreferences p;

    public Store(Context c) {
        p = c.getSharedPreferences("appfinder", Context.MODE_PRIVATE);
    }

    public String lang() { return p.getString("lang", "ur"); }
    public void setLang(String l) { p.edit().putString("lang", l).apply(); }
    public boolean dark() { return p.getBoolean("dark", true); }
    public void setDark(boolean v) { p.edit().putBoolean("dark", v).apply(); }
    public boolean rtl() { return !lang().equals("en"); }

    public String tr(String ur, String en, String ar) {
        String l = lang();
        if (l.equals("en")) return en;
        if (l.equals("ar")) return ar;
        return ur;
    }
}
