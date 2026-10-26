package com.gathin.tvhome;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Outline;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;

public class PrayerActivity extends Activity {

    private TextView prayerLocation;
    private TextView prayerHijri;
    private TextView prayerNextName;
    private TextView prayerNextTime;
    private TextView prayerCountdown;
    private ProgressBar prayerProgress;
    private LinearLayout prayerSlots;
    private ImageView prayerHeroImage;
    private TextView prayerHeroCaption;
    private View btnChangeZone;
    private final Handler tick = new Handler(Looper.getMainLooper());
    private final Runnable refresh = this::populateUi;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_prayer);

        prayerLocation = findViewById(R.id.prayerLocation);
        prayerHijri = findViewById(R.id.prayerHijri);
        prayerNextName = findViewById(R.id.prayerNextName);
        prayerNextTime = findViewById(R.id.prayerNextTime);
        prayerCountdown = findViewById(R.id.prayerCountdown);
        prayerProgress = findViewById(R.id.prayerProgress);
        prayerSlots = findViewById(R.id.prayerSlots);
        prayerHeroImage = findViewById(R.id.prayerHeroImage);
        prayerHeroCaption = findViewById(R.id.prayerHeroCaption);
        btnChangeZone = findViewById(R.id.btnChangeZone);

        if (btnChangeZone != null) {
            btnChangeZone.setOnClickListener(v -> showZonePicker());
            setupFocusAnimation(btnChangeZone);
        }
        setupFocusAnimation(findViewById(R.id.cardTazkirah));
        setupFocusAnimation(findViewById(R.id.cardAyat));
        setupFocusAnimation(findViewById(R.id.cardQuote));

        View hero = findViewById(R.id.prayerHeroFrame);
        if (hero != null) {
            final float radius = 18f * getResources().getDisplayMetrics().density;
            hero.setClipToOutline(true);
            hero.setOutlineProvider(new ViewOutlineProvider() {
                @Override
                public void getOutline(View view, Outline outline) {
                    outline.setRoundRect(0, 0, Math.max(1, view.getWidth()),
                            Math.max(1, view.getHeight()), radius);
                }
            });
            hero.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, orr, ob) -> v.invalidateOutline());
        }

        populateUi();
        PrayerTimes.syncIfDue(this, () -> runOnUiThread(this::populateUi));
        DailyAyat.syncIfDue(this, () -> runOnUiThread(this::populateUi));
    }

    @Override
    protected void onResume() {
        super.onResume();
        tick.removeCallbacks(refresh);
        tick.post(refresh);
    }

    @Override
    protected void onPause() {
        tick.removeCallbacks(refresh);
        super.onPause();
    }

    private void populateUi() {
        if (isFinishing()) return;

        prayerLocation.setText(PrayerTimes.getShortZoneName(this) + "  •  JAKIM");
        prayerHijri.setText(PrayerTimes.getHijriDate());

        PrayerTimes.NextInfo next = PrayerTimes.getNextInfo(this);
        prayerNextName.setText(next.name);
        prayerNextTime.setText(next.timeStr);
        prayerCountdown.setText(PrayerTimes.formatCountdown(next.remainingMs));
        prayerProgress.setProgress(Math.round(next.progress * 1000f));

        prayerHeroImage.setImageResource(DailyReflection.imageRes());
        prayerHeroCaption.setText(DailyReflection.imageCaption());

        DailyReflection.Item taz = DailyReflection.tazkirah();
        ((TextView) findViewById(R.id.tazkirahText)).setText(taz.text);
        ((TextView) findViewById(R.id.tazkirahSource)).setText(taz.source);

        DailyAyat.Ayat ayat = DailyAyat.getToday(this);
        ((TextView) findViewById(R.id.ayatCardRef)).setText(ayat.reference);
        ((TextView) findViewById(R.id.ayatCardArabic)).setText(ayat.arabic);
        ((TextView) findViewById(R.id.ayatCardTranslation)).setText(ayat.translation);

        DailyReflection.Item quote = DailyReflection.quote();
        ((TextView) findViewById(R.id.quoteText)).setText(quote.text);
        ((TextView) findViewById(R.id.quoteSource)).setText(quote.source);

        fillSlots();
        tick.removeCallbacks(refresh);
        tick.postDelayed(refresh, 30_000L);
    }

    private void fillSlots() {
        PrayerTimes.PrayerItem[] items = PrayerTimes.getPrayerItems(this);
        if (items == null) return;
        prayerSlots.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(this);
        View first = null;
        for (int i = 0; i < items.length; i++) {
            View v = inflater.inflate(R.layout.item_prayer_slot, prayerSlots, false);
            TextView name = v.findViewById(R.id.slotName);
            TextView time = v.findViewById(R.id.slotTime);
            name.setText(items[i].name);
            time.setText(items[i].timeStr);
            v.setSelected(items[i].isNext);
            if (items[i].isNext) {
                name.setTextColor(0xFF4ADE80);
                time.setTextColor(0xFF4ADE80);
            } else if (items[i].isPast) {
                name.setTextColor(0x6694A3B8);
                time.setTextColor(0x99FFFFFF);
            }
            setupFocusAnimation(v);
            prayerSlots.addView(v);
            if (first == null) first = v;
        }
        if (first != null && getCurrentFocus() == null) first.requestFocus();
    }

    private void showZonePicker() {
        final List<PrayerTimes.PrayerZone> zones = PrayerTimes.getPresetZones();
        String activeZone = PrayerTimes.getActiveZone(this);
        String activeName = PrayerTimes.getActiveZoneName(this);
        int selectedIndex = 0;
        boolean named = false;
        CharSequence[] labels = new CharSequence[zones.size()];
        for (int i = 0; i < zones.size(); i++) {
            PrayerTimes.PrayerZone z = zones.get(i);
            labels[i] = z.getDisplayName();
            if (z.name.equalsIgnoreCase(activeName)) {
                selectedIndex = i;
                named = true;
            } else if (!named && z.code.equalsIgnoreCase(activeZone)) {
                selectedIndex = i;
            }
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert);
        builder.setTitle("Pilih zon waktu solat JAKIM");
        builder.setSingleChoiceItems(labels, selectedIndex, (dialog, which) -> {
            PrayerTimes.PrayerZone chosen = zones.get(which);
            PrayerTimes.setPrayerZone(PrayerActivity.this, chosen);
            dialog.dismiss();
            Toast.makeText(PrayerActivity.this, "Zon solat: " + chosen.name, Toast.LENGTH_SHORT).show();
            populateUi();
        });
        builder.setNegativeButton("Tutup", (dialog, which) -> dialog.dismiss());
        builder.show();
    }

    private void setupFocusAnimation(View v) {
        if (v == null) return;
        v.setOnFocusChangeListener((view, hasFocus) -> {
            float scale = hasFocus ? 1.05f : 1.0f;
            view.animate().scaleX(scale).scaleY(scale).setDuration(150).start();
        });
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            finish();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }
}
