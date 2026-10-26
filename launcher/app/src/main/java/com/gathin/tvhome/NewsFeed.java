package com.gathin.tvhome;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.text.Html;
import android.text.TextUtils;
import android.util.Log;
import android.util.Xml;

import org.xmlpull.v1.XmlPullParser;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

/**
 * Latest headlines for the home ticker: Malaysia (local) and world (global).
 * Google News RSS is the primary source (aggregates MSN, The Star, Malay Mail, BBC, …);
 * Malay Mail and BBC World are used if Google is blocked.
 */
final class NewsFeed {
    private static final String TAG = "TvHome.News";
    private static final String PREFS = "news_feed_cache";
    private static final String KEY_LOCAL = "local_json";
    private static final String KEY_WORLD = "world_json";
    private static final String KEY_UPDATED = "updated";
    private static final long REFRESH_MS = 15L * 60 * 1000;

    private static final String UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";

    private static final String[] LOCAL_FEEDS = {
            "https://news.google.com/rss?hl=en-MY&gl=MY&ceid=MY:en",
            "https://www.malaymail.com/feed/rss/malaysia",
            "https://www.freemalaysiatoday.com/feed"
    };
    private static final String[] WORLD_FEEDS = {
            "https://news.google.com/rss/headlines/section/topic/WORLD?hl=en-MY&gl=MY&ceid=MY:en",
            "https://feeds.bbci.co.uk/news/world/rss.xml"
    };

    static final int TAG_LOCAL = 0;
    static final int TAG_WORLD = 1;
    private static final int MAX_EACH = 10;

    static class Item {
        final String title;
        final String source;
        final String url;
        final int kind;
        final long published;

        Item(String title, String source, String url, int kind, long published) {
            this.title = title;
            this.source = source;
            this.url = url;
            this.kind = kind;
            this.published = published;
        }

        String tag() {
            return kind == TAG_LOCAL ? "MY" : "GLOBAL";
        }

        int tagColor() {
            return kind == TAG_LOCAL ? 0xFFFBBF24 : 0xFF38BDF8;
        }
    }

    private NewsFeed() {
    }

    static void syncIfDue(Context context, Runnable onUpdated) {
        syncInternal(context, onUpdated, false);
    }

    static void sync(Context context, Runnable onUpdated) {
        syncInternal(context, onUpdated, true);
    }

    private static void syncInternal(Context context, Runnable onUpdated, boolean force) {
        Context app = context.getApplicationContext();
        new Thread(() -> {
            try {
                SharedPreferences prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
                long last = prefs.getLong(KEY_UPDATED, 0);
                if (!force && System.currentTimeMillis() - last < REFRESH_MS
                        && !prefs.getString(KEY_LOCAL, "").isEmpty()) {
                    return;
                }
                List<Item> local = fetchFirst(LOCAL_FEEDS, TAG_LOCAL);
                List<Item> world = fetchFirst(WORLD_FEEDS, TAG_WORLD);
                if (local.isEmpty() && world.isEmpty()) {
                    Log.w(TAG, "All news feeds empty");
                    return;
                }
                prefs.edit()
                        .putString(KEY_LOCAL, encode(local))
                        .putString(KEY_WORLD, encode(world))
                        .putLong(KEY_UPDATED, System.currentTimeMillis())
                        .apply();
                Log.i(TAG, "News synced local=" + local.size() + " world=" + world.size());
                if (onUpdated != null) {
                    new Handler(Looper.getMainLooper()).post(onUpdated);
                }
            } catch (Exception e) {
                Log.w(TAG, "News sync failed: " + e.getMessage());
            }
        }, "news-sync").start();
    }

    static List<Item> getLocal(Context context) {
        return decode(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(KEY_LOCAL, ""), TAG_LOCAL);
    }

    static List<Item> getWorld(Context context) {
        return decode(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(KEY_WORLD, ""), TAG_WORLD);
    }

    static List<Item> getTickerItems(Context context) {
        List<Item> local = getLocal(context);
        List<Item> world = getWorld(context);
        List<Item> mixed = new ArrayList<>();
        int n = Math.max(local.size(), world.size());
        for (int i = 0; i < n; i++) {
            if (i < local.size()) mixed.add(local.get(i));
            if (i < world.size()) mixed.add(world.get(i));
        }
        return mixed;
    }

    static String getUpdatedLabel(Context context) {
        long t = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getLong(KEY_UPDATED, 0);
        if (t <= 0) return "";
        return new SimpleDateFormat("h:mm a", Locale.getDefault()).format(new Date(t));
    }

    private static List<Item> fetchFirst(String[] urls, int kind) {
        for (String url : urls) {
            List<Item> list = parseRss(fetchXml(url), kind);
            if (!list.isEmpty()) {
                Log.i(TAG, "Feed ok " + kind + " n=" + list.size() + " " + url);
                return list;
            }
        }
        return new ArrayList<>();
    }

    private static String fetchXml(String urlStr) {
        HttpURLConnection conn = null;
        try {
            URL url = new URL(urlStr);
            conn = (HttpURLConnection) url.openConnection();
            conn.setInstanceFollowRedirects(true);
            conn.setConnectTimeout(8000);
            conn.setReadTimeout(8000);
            conn.setRequestMethod("GET");
            conn.setRequestProperty("User-Agent", UA);
            conn.setRequestProperty("Accept", "application/rss+xml, application/xml, text/xml, */*");
            int code = conn.getResponseCode();
            if (code / 100 == 3) {
                String loc = conn.getHeaderField("Location");
                conn.disconnect();
                if (loc != null) return fetchXml(loc);
                return "";
            }
            InputStream in = code == 200 ? conn.getInputStream() : conn.getErrorStream();
            if (in == null) return "";
            BufferedReader reader = new BufferedReader(new InputStreamReader(in, "UTF-8"));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) sb.append(line).append('\n');
            reader.close();
            return code == 200 ? sb.toString() : "";
        } catch (Exception e) {
            Log.w(TAG, "fetchXml " + urlStr + ": " + e.getMessage());
            return "";
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    private static List<Item> parseRss(String xml, int kind) {
        List<Item> out = new ArrayList<>();
        if (xml == null || xml.isEmpty()) return out;
        LinkedHashSet<String> seen = new LinkedHashSet<>();
        try {
            XmlPullParser p = Xml.newPullParser();
            p.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false);
            p.setInput(new StringReader(xml));
            boolean inItem = false;
            String title = "", link = "", source = "", pub = "";
            int event = p.getEventType();
            while (event != XmlPullParser.END_DOCUMENT && out.size() < MAX_EACH) {
                if (event == XmlPullParser.START_TAG) {
                    String name = p.getName();
                    if ("item".equalsIgnoreCase(name) || "entry".equalsIgnoreCase(name)) {
                        inItem = true;
                        title = link = source = pub = "";
                    } else if (inItem) {
                        if ("title".equalsIgnoreCase(name)) title = nextText(p);
                        else if ("link".equalsIgnoreCase(name)) {
                            String href = p.getAttributeValue(null, "href");
                            String rel = p.getAttributeValue(null, "rel");
                            String text = nextText(p);
                            if (href != null && (rel == null || "alternate".equalsIgnoreCase(rel))) {
                                link = href;
                            } else if (!TextUtils.isEmpty(text) && TextUtils.isEmpty(link)) {
                                link = text;
                            }
                        } else if ("source".equalsIgnoreCase(name)) source = nextText(p);
                        else if ("pubDate".equalsIgnoreCase(name) || "published".equalsIgnoreCase(name)
                                || "updated".equalsIgnoreCase(name) || "dc:date".equalsIgnoreCase(name)) {
                            pub = nextText(p);
                        } else if ("description".equalsIgnoreCase(name)
                                || "content".equalsIgnoreCase(name)
                                || "summary".equalsIgnoreCase(name)) {
                            skipNested(p);
                        }
                    }
                } else if (event == XmlPullParser.END_TAG) {
                    String name = p.getName();
                    if (inItem && ("item".equalsIgnoreCase(name) || "entry".equalsIgnoreCase(name))) {
                        Item item = buildItem(title, source, link, pub, kind);
                        if (item != null) {
                            String key = item.title.toLowerCase(Locale.ROOT);
                            if (seen.add(key)) out.add(item);
                        }
                        inItem = false;
                    }
                }
                event = p.next();
            }
        } catch (Exception e) {
            Log.w(TAG, "parseRss: " + e.getMessage());
        }
        return out;
    }

    private static String nextText(XmlPullParser p) {
        try {
            return clean(p.nextText());
        } catch (Exception e) {
            return "";
        }
    }

    private static void skipNested(XmlPullParser p) {
        try {
            int depth = 1;
            while (depth > 0) {
                int e = p.next();
                if (e == XmlPullParser.START_TAG) depth++;
                else if (e == XmlPullParser.END_TAG) depth--;
                else if (e == XmlPullParser.END_DOCUMENT) break;
            }
        } catch (Exception ignored) {
        }
    }

    private static Item buildItem(String title, String source, String url, String pub, int kind) {
        title = clean(title);
        if (title.length() < 12) return null;
        if (title.toLowerCase(Locale.ROOT).contains("see more headlines")) return null;
        source = clean(source);
        if (source.isEmpty()) source = guessSource(title, url);
        title = stripSourceSuffix(title, source);
        if (title.length() < 12) return null;
        return new Item(title, source, url == null ? "" : url.trim(), kind, parseDate(pub));
    }

    private static String guessSource(String title, String url) {
        int dash = title.lastIndexOf(" - ");
        if (dash > 12 && dash < title.length() - 3) {
            String tail = title.substring(dash + 3).trim();
            if (tail.length() < 40 && !tail.contains(".")) return tail;
        }
        if (url != null) {
            if (url.contains("malaymail")) return "Malay Mail";
            if (url.contains("thestar")) return "The Star";
            if (url.contains("nst.com")) return "NST";
            if (url.contains("freemalaysiatoday") || url.contains("fmt")) return "FMT";
            if (url.contains("bbc.")) return "BBC";
            if (url.contains("msn.com")) return "MSN";
        }
        return kindDefault(url);
    }

    private static String kindDefault(String url) {
        return url != null && url.contains("bbc") ? "BBC" : "News";
    }

    private static String stripSourceSuffix(String title, String source) {
        if (source == null || source.isEmpty()) return title;
        String suffix = " - " + source;
        if (title.endsWith(suffix)) return title.substring(0, title.length() - suffix.length()).trim();
        int dash = title.lastIndexOf(" - ");
        if (dash > 12) {
            String tail = title.substring(dash + 3).trim();
            if (tail.equalsIgnoreCase(source) || tail.length() < 28) {
                return title.substring(0, dash).trim();
            }
        }
        return title;
    }

    private static String clean(String s) {
        if (s == null) return "";
        s = s.replace('\n', ' ').replace('\r', ' ').trim();
        if (s.contains("&") || s.contains("<")) {
            s = Html.fromHtml(s, Html.FROM_HTML_MODE_LEGACY).toString().trim();
        }
        return s.replaceAll("\\s+", " ");
    }

    private static long parseDate(String raw) {
        if (raw == null || raw.isEmpty()) return 0;
        String[] patterns = {
                "EEE, dd MMM yyyy HH:mm:ss Z",
                "EEE, dd MMM yyyy HH:mm:ss z",
                "yyyy-MM-dd'T'HH:mm:ssX",
                "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'"
        };
        for (String pat : patterns) {
            try {
                SimpleDateFormat f = new SimpleDateFormat(pat, Locale.US);
                if (pat.contains("'Z'")) f.setTimeZone(TimeZone.getTimeZone("UTC"));
                Date d = f.parse(raw.trim());
                if (d != null) return d.getTime();
            } catch (Exception ignored) {
            }
        }
        return 0;
    }

    private static String encode(List<Item> items) {
        StringBuilder sb = new StringBuilder();
        for (Item it : items) {
            if (sb.length() > 0) sb.append('\n');
            sb.append(esc(it.title)).append('\t')
                    .append(esc(it.source)).append('\t')
                    .append(esc(it.url)).append('\t')
                    .append(it.published);
        }
        return sb.toString();
    }

    private static List<Item> decode(String raw, int kind) {
        List<Item> out = new ArrayList<>();
        if (raw == null || raw.isEmpty()) return out;
        String[] lines = raw.split("\n");
        for (String line : lines) {
            String[] p = line.split("\t", -1);
            if (p.length < 3) continue;
            long pub = 0;
            if (p.length >= 4) {
                try { pub = Long.parseLong(p[3]); } catch (Exception ignored) {}
            }
            String title = unesc(p[0]);
            if (title.length() < 8) continue;
            out.add(new Item(title, unesc(p[1]), unesc(p[2]), kind, pub));
        }
        return out;
    }

    private static String esc(String s) {
        if (s == null) return "";
        return s.replace('\t', ' ').replace('\n', ' ');
    }

    private static String unesc(String s) {
        return s == null ? "" : s;
    }
}
