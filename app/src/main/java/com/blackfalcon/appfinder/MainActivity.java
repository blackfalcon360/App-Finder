package com.blackfalcon.appfinder;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public class MainActivity extends Activity {

    static final int BLUE = 0xFF1E88E5;

    Store st;
    PackageManager pm;
    final ArrayList<AppEntry> all = new ArrayList<AppEntry>();
    final ArrayList<AppEntry> shown = new ArrayList<AppEntry>();
    int cBg, cCard, cText, cSub;
    EditText search;
    TextView count;
    ListView lv;
    BaseAdapter ad;
    LinearLayout searchPane;
    ScrollView setScroll;
    LinearLayout setBox;
    TextView[] tabViews;
    RadioGroup rgLang, rgTheme;
    int[] langIds = new int[3];
    int[] themeIds = new int[2];

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        st = new Store(this);
        pm = getPackageManager();
        buildUI();
        showTab(0);
        loadApps();
        if (getIntent() != null && getIntent().getBooleanExtra("focus", false)) focusSearch();
    }

    @Override
    protected void onNewIntent(Intent i) {
        super.onNewIntent(i);
        setIntent(i);
        if (i.getBooleanExtra("focus", false)) {
            showTab(0);
            search.setText("");
            focusSearch();
        }
    }

    void focusSearch() {
        search.requestFocus();
        search.postDelayed(() -> {
            InputMethodManager im = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            if (im != null) im.showSoftInput(search, InputMethodManager.SHOW_IMPLICIT);
        }, 350);
    }

    String tr(String ur, String en, String ar) { return st.tr(ur, en, ar); }

    int dp(int v) { return (int) (v * getResources().getDisplayMetrics().density + 0.5f); }

    GradientDrawable round(int color, int r) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(r));
        return g;
    }

    TextView tv(String s, int size, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(size);
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }

    // ---------- UI ----------
    void buildUI() {
        boolean dark = st.dark();
        cBg = dark ? 0xFF000000 : 0xFFF2F2F2;
        cCard = dark ? 0xFF1A1A1A : 0xFFFFFFFF;
        cText = dark ? 0xFFFFFFFF : 0xFF111111;
        cSub = dark ? 0xFFAAAAAA : 0xFF555555;

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(cBg);
        root.setLayoutDirection(st.rtl() ? View.LAYOUT_DIRECTION_RTL : View.LAYOUT_DIRECTION_LTR);

        TextView title = tv("App Finder", 22, cText, true);
        title.setPadding(dp(14), dp(14), dp(14), dp(6));
        root.addView(title);

        String[] names = {tr("تلاش", "Search", "بحث"), tr("سیٹنگز", "Settings", "الإعدادات")};
        LinearLayout tabs = new LinearLayout(this);
        tabs.setOrientation(LinearLayout.HORIZONTAL);
        tabs.setPadding(dp(8), dp(4), dp(8), dp(8));
        tabViews = new TextView[2];
        for (int i = 0; i < 2; i++) {
            final int idx = i;
            TextView t = tv(names[i], 15, cText, false);
            t.setGravity(Gravity.CENTER);
            t.setPadding(dp(8), dp(9), dp(8), dp(9));
            t.setOnClickListener(v -> showTab(idx));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            lp.setMargins(dp(4), 0, dp(4), 0);
            tabs.addView(t, lp);
            tabViews[i] = t;
        }
        root.addView(tabs);

        FrameLayout content = new FrameLayout(this);

        // search pane
        searchPane = new LinearLayout(this);
        searchPane.setOrientation(LinearLayout.VERTICAL);
        search = new EditText(this);
        search.setHint(tr("ایپ کا نام لکھیں…", "Type an app name…", "اكتب اسم التطبيق…"));
        search.setHintTextColor(0xFF777777);
        search.setTextColor(Color.BLACK);
        search.setTextSize(16);
        search.setSingleLine(true);
        search.setInputType(InputType.TYPE_CLASS_TEXT);
        search.setBackground(round(0xFFFFFFFF, 10));
        search.setPadding(dp(12), dp(10), dp(12), dp(10));
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void afterTextChanged(Editable e) { filter(e.toString()); }
        });
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        sp.setMargins(dp(12), dp(4), dp(12), dp(4));
        searchPane.addView(search, sp);
        count = tv("", 12, cSub, false);
        count.setPadding(dp(14), dp(2), dp(14), dp(4));
        searchPane.addView(count);

        lv = new ListView(this);
        lv.setDivider(new ColorDrawable(cCard));
        lv.setDividerHeight(dp(1));
        ad = new BaseAdapter() {
            @Override public int getCount() { return shown.size(); }
            @Override public Object getItem(int p) { return shown.get(p); }
            @Override public long getItemId(int p) { return p; }
            @Override public View getView(int p, View cv, ViewGroup parent) { return row(shown.get(p)); }
        };
        lv.setAdapter(ad);
        lv.setOnItemClickListener((parent, view, pos, id) -> actions(shown.get(pos)));
        searchPane.addView(lv, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        content.addView(searchPane, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        // settings pane
        setScroll = new ScrollView(this);
        setBox = new LinearLayout(this);
        setBox.setOrientation(LinearLayout.VERTICAL);
        setBox.setPadding(dp(14), dp(4), dp(14), dp(16));
        setScroll.addView(setBox);
        content.addView(setScroll, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        root.addView(content, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        TextView credit = tv("By: Black Falcon \uD83E\uDD85", 13, cSub, false);
        credit.setGravity(Gravity.CENTER);
        credit.setPadding(0, dp(8), 0, dp(10));
        root.addView(credit);

        setContentView(root);
        buildSettings();
        filter("");
    }

    void showTab(int i) {
        for (int k = 0; k < 2; k++) {
            boolean sel = k == i;
            tabViews[k].setTextColor(sel ? Color.WHITE : cText);
            tabViews[k].setBackground(round(sel ? BLUE : cCard, 20));
        }
        searchPane.setVisibility(i == 0 ? View.VISIBLE : View.GONE);
        setScroll.setVisibility(i == 1 ? View.VISIBLE : View.GONE);
    }

    View row(AppEntry e) {
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setBackgroundColor(cCard);
        r.setPadding(dp(14), dp(8), dp(14), dp(8));
        ImageView iv = new ImageView(this);
        if (e.icon == null) {
            try { e.icon = pm.getActivityIcon(e.cn); } catch (Exception ex) { }
        }
        if (e.icon != null) iv.setImageDrawable(e.icon);
        LinearLayout.LayoutParams ip = new LinearLayout.LayoutParams(dp(44), dp(44));
        ip.setMargins(0, 0, dp(12), 0);
        r.addView(iv, ip);
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.addView(tv(e.label, 16, cText, true));
        col.addView(tv(e.pkg, 11, cSub, false));
        r.addView(col, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        return r;
    }

    // ---------- data ----------
    void loadApps() {
        new Thread(() -> {
            Intent q = new Intent(Intent.ACTION_MAIN);
            q.addCategory(Intent.CATEGORY_LAUNCHER);
            List<ResolveInfo> list = pm.queryIntentActivities(q, 0);
            final ArrayList<AppEntry> tmp = new ArrayList<AppEntry>();
            for (ResolveInfo ri : list) {
                if (ri.activityInfo == null) continue;
                AppEntry e = new AppEntry();
                e.pkg = ri.activityInfo.packageName;
                e.cn = new ComponentName(e.pkg, ri.activityInfo.name);
                CharSequence lb = ri.loadLabel(pm);
                e.label = lb == null ? e.pkg : lb.toString();
                tmp.add(e);
            }
            Collections.sort(tmp, new Comparator<AppEntry>() {
                @Override public int compare(AppEntry a, AppEntry b) {
                    return a.label.compareToIgnoreCase(b.label);
                }
            });
            runOnUiThread(() -> {
                all.clear();
                all.addAll(tmp);
                filter(search.getText().toString());
            });
        }).start();
    }

    void filter(String q) {
        String s = q.trim().toLowerCase(Locale.ROOT);
        shown.clear();
        for (AppEntry e : all) {
            if (s.length() == 0 || e.label.toLowerCase(Locale.ROOT).contains(s)
                    || e.pkg.toLowerCase(Locale.ROOT).contains(s)) {
                shown.add(e);
            }
        }
        count.setText(shown.size() + tr(" ایپس", " apps", " تطبيق"));
        ad.notifyDataSetChanged();
    }

    // ---------- actions ----------
    void actions(final AppEntry e) {
        final String[] items = {
            tr("ایپ کھولیں", "Open app", "فتح التطبيق"),
            tr("ہوم اسکرین پر فولڈر تلاش کریں", "Find its folder on home screen", "ابحث عن مجلده في الشاشة الرئيسية"),
            tr("ایپ کی معلومات", "App info", "معلومات التطبيق")
        };
        new AlertDialog.Builder(this)
            .setTitle(e.label)
            .setItems(items, (dlg, which) -> {
                if (which == 0) openApp(e);
                else if (which == 1) showLocation(e);
                else appInfo(e);
            })
            .setNegativeButton(tr("بند", "Close", "إغلاق"), null)
            .show();
    }

    void openApp(AppEntry e) {
        try {
            Intent i = new Intent(Intent.ACTION_MAIN);
            i.addCategory(Intent.CATEGORY_LAUNCHER);
            i.setComponent(e.cn);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(i);
        } catch (Exception ex) {
            Toast.makeText(this, tr("ایپ نہیں کھل سکی", "Could not open app", "تعذر فتح التطبيق"),
                    Toast.LENGTH_SHORT).show();
        }
    }

    void appInfo(AppEntry e) {
        try {
            Intent i = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:" + e.pkg));
            startActivity(i);
        } catch (Exception ex) { }
    }

    void goHome() {
        Intent h = new Intent(Intent.ACTION_MAIN);
        h.addCategory(Intent.CATEGORY_HOME);
        h.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(h);
    }

    void showLocation(final AppEntry e) {
        Loc loc = Loc.find(this, e.pkg);
        String msg;
        if (loc.status == Loc.FOUND) {
            StringBuilder sb = new StringBuilder();
            for (Loc.Place p : loc.places) {
                if (p.kind.equals("dock")) {
                    sb.append(tr("ڈاک (نیچے کی پٹی) میں", "In the dock (bottom bar)", "في الشريط السفلي"));
                } else if (p.kind.equals("folder")) {
                    sb.append(tr("فولڈر: ", "Folder: ", "المجلد: "))
                      .append(p.name.length() > 0 ? p.name : tr("(بے نام)", "(unnamed)", "(بدون اسم)"));
                    if (p.page > 0) sb.append(tr(" — ہوم اسکرین صفحہ ", " \u2014 home screen page ", " \u2014 الصفحة ")).append(p.page);
                } else {
                    sb.append(tr("ہوم اسکرین صفحہ ", "Home screen page ", "الصفحة الرئيسية ")).append(p.page);
                }
                sb.append("\n");
            }
            msg = sb.toString().trim();
        } else if (loc.status == Loc.NOT_ON_HOME) {
            msg = tr("یہ ایپ ہوم اسکرین پر نہیں ہے؛ صرف ایپ ڈرار میں ہے۔",
                    "This app is not on your home screen; it is only in the app drawer.",
                    "هذا التطبيق ليس في الشاشة الرئيسية؛ هو في قائمة التطبيقات فقط.");
        } else {
            msg = tr("آپ کا لانچر اپنی ترتیب دوسری ایپس کو پڑھنے نہیں دیتا، اس لیے فولڈر معلوم نہیں ہو سکا۔ ہوم اسکرین پر لانچر کی اپنی سرچ استعمال کریں، یا نیچے سے ایپ سیدھی کھول لیں۔",
                    "Your launcher does not let other apps read its layout, so the folder could not be determined. Use the launcher's own search on the home screen, or just open the app directly.",
                    "المشغّل لديك لا يسمح للتطبيقات الأخرى بقراءة ترتيبه، لذا تعذر تحديد المجلد. استخدم بحث المشغّل نفسه أو افتح التطبيق مباشرة.");
        }
        new AlertDialog.Builder(this)
            .setTitle(e.label)
            .setMessage(msg)
            .setPositiveButton(tr("ہوم کھولیں", "Open Home", "فتح الرئيسية"), (dlg, w) -> goHome())
            .setNeutralButton(tr("ایپ کھولیں", "Open app", "فتح التطبيق"), (dlg, w) -> openApp(e))
            .setNegativeButton(tr("بند", "Close", "إغلاق"), null)
            .show();
    }

    // ---------- settings ----------
    TextView label(String s) {
        TextView t = tv(s, 16, cText, true);
        t.setPadding(0, dp(14), 0, dp(6));
        return t;
    }

    RadioGroup radios(String[] labels, int checked, int[] ids) {
        RadioGroup g = new RadioGroup(this);
        for (int i = 0; i < labels.length; i++) {
            RadioButton r = new RadioButton(this);
            r.setText(labels[i]);
            r.setTextColor(cText);
            r.setTextSize(15);
            r.setId(View.generateViewId());
            ids[i] = r.getId();
            g.addView(r);
        }
        g.check(ids[checked]);
        return g;
    }

    void buildSettings() {
        setBox.removeAllViews();
        setBox.addView(label(tr("زبان", "Language", "اللغة")));
        String l = st.lang();
        rgLang = radios(new String[] {"اردو", "English", "العربية"},
                l.equals("en") ? 1 : (l.equals("ar") ? 2 : 0), langIds);
        setBox.addView(rgLang);

        setBox.addView(label(tr("تھیم", "Theme", "المظهر")));
        rgTheme = radios(new String[] {tr("ڈارک موڈ", "Dark mode", "الوضع الداكن"),
            tr("لائٹ موڈ", "Light mode", "الوضع الفاتح")}, st.dark() ? 0 : 1, themeIds);
        setBox.addView(rgTheme);

        Button save = new Button(this);
        save.setText(tr("محفوظ کریں", "Save", "حفظ"));
        save.setTextColor(Color.WHITE);
        save.setBackground(round(BLUE, 10));
        save.setOnClickListener(v -> saveSettings());
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, dp(14), 0, 0);
        setBox.addView(save, lp);

        TextView info = tv(tr(
            "نوٹیفکیشن پینل میں بٹن: اوپر سے نیچے سوائپ کر کے پینل کھولیں ← پینسل/ایڈٹ (✎) ← \"Find App / ایپ تلاش\" ٹائل کو اوپر والے حصے میں گھسیٹیں۔ پھر وائی فائی کی طرح اسے دبانے سے یہ ایپ کھلے گی اور سرچ باکس تیار ہو گا۔\n\nفولڈر تلاش: ایپ لانچر کا ڈیٹا پڑھنے کی کوشش کرتی ہے۔ کئی فونز (خاص کر نئے اینڈرائیڈ) میں لانچر اجازت نہیں دیتا؛ ایسی صورت میں ایپ صاف بتا دیتی ہے۔ اینڈرائیڈ کسی ایپ کو یہ اختیار نہیں دیتا کہ وہ ہوم اسکرین کا فولڈر خود کھول دے، اس لیے ایپ جگہ بتاتی ہے اور \"ہوم کھولیں\" سے ہوم اسکرین پر لے جاتی ہے۔",
            "Notification-panel button: swipe down to open the panel \u2192 tap the edit (pencil) icon \u2192 drag the \"Find App\" tile into the active area. Tapping it, like Wi-Fi, opens this app with the search box ready.\n\nFolder lookup: the app tries to read your launcher's layout. Many phones (especially newer Android) do not allow this; the app then says so clearly. Android does not let an app open a home-screen folder for you, so it tells you where the icon is and \"Open Home\" takes you to the home screen.",
            "زر لوحة الإشعارات: اسحب للأسفل لفتح اللوحة \u2190 اضغط أيقونة التعديل \u2190 اسحب بلاطة \"Find App\" إلى المنطقة النشطة. الضغط عليها، مثل Wi-Fi، يفتح التطبيق وحقل البحث جاهز.\n\nالبحث عن المجلد: يحاول التطبيق قراءة ترتيب المشغّل. كثير من الهواتف (خاصة الأحدث) لا تسمح بذلك، وعندها يخبرك التطبيق بوضوح. لا يسمح أندرويد لتطبيق بفتح مجلد في الشاشة الرئيسية، لذا يعرض التطبيق مكان الأيقونة ويأخذك إلى الشاشة الرئيسية."),
            12, cSub, false);
        info.setPadding(0, dp(18), 0, 0);
        setBox.addView(info);
    }

    void saveSettings() {
        String[] langs = {"ur", "en", "ar"};
        int cur = rgLang.getCheckedRadioButtonId();
        for (int i = 0; i < 3; i++) if (langIds[i] == cur) st.setLang(langs[i]);
        st.setDark(rgTheme.getCheckedRadioButtonId() == themeIds[0]);
        buildUI();
        showTab(1);
        filter("");
        Toast.makeText(this, tr("محفوظ ہو گیا", "Saved", "تم الحفظ"), Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onBackPressed() {
        new AlertDialog.Builder(this)
            .setTitle(tr("ایپ بند کریں؟", "Exit app?", "إغلاق التطبيق؟"))
            .setMessage(tr("کیا آپ واقعی باہر نکلنا چاہتے ہیں؟", "Do you really want to exit?",
                    "هل تريد الخروج فعلًا؟"))
            .setPositiveButton(tr("ہاں", "Yes", "نعم"), (dlg, w) -> finish())
            .setNegativeButton(tr("نہیں", "No", "لا"), null)
            .show();
    }
}
