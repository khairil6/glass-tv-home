package com.gathin.tvhome;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;

public class NewsActivity extends Activity {

    private LinearLayout localList;
    private LinearLayout worldList;
    private TextView updated;
    private TextView footer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_news);
        localList = findViewById(R.id.newsLocalList);
        worldList = findViewById(R.id.newsWorldList);
        updated = findViewById(R.id.newsUpdated);
        footer = findViewById(R.id.newsFooter);
        populate();
        NewsFeed.syncIfDue(this, this::populate);
    }

    private void populate() {
        fill(localList, NewsFeed.getLocal(this));
        fill(worldList, NewsFeed.getWorld(this));
        String when = NewsFeed.getUpdatedLabel(this);
        updated.setText(when.isEmpty() ? "Memuat…" : "Dikemaskini " + when);
        footer.setText("Google News Malaysia & World  •  Malay Mail  •  BBC  •  MSN via Google");
        View first = localList.getChildCount() > 0 ? localList.getChildAt(0)
                : worldList.getChildCount() > 0 ? worldList.getChildAt(0) : null;
        if (first != null) first.requestFocus();
    }

    private void fill(LinearLayout parent, List<NewsFeed.Item> items) {
        parent.removeAllViews();
        LayoutInflater inflater = getLayoutInflater();
        for (NewsFeed.Item item : items) {
            View row = inflater.inflate(R.layout.item_news, parent, false);
            TextView tag = row.findViewById(R.id.newsTag);
            TextView title = row.findViewById(R.id.newsTitle);
            TextView meta = row.findViewById(R.id.newsMeta);
            tag.setText(item.tag());
            tag.setTextColor(item.tagColor());
            title.setText(item.title);
            meta.setText(item.source);
            row.setOnClickListener(v -> open(item));
            row.setOnFocusChangeListener((v, hasFocus) -> {
                float scale = hasFocus ? 1.02f : 1f;
                v.animate().scaleX(scale).scaleY(scale).setDuration(140).start();
            });
            parent.addView(row);
        }
        if (items.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText("Tiada tajuk buat masa ini");
            empty.setTextColor(0xFF94A3B8);
            empty.setTextSize(14);
            parent.addView(empty);
        }
    }

    private void open(NewsFeed.Item item) {
        if (item.url == null || item.url.isEmpty()) return;
        try {
            Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse(item.url));
            i.addCategory(Intent.CATEGORY_BROWSABLE);
            startActivity(i);
        } catch (Exception e) {
            Toast.makeText(this, item.title, Toast.LENGTH_LONG).show();
        }
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
