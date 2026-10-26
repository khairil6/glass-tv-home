package com.gathin.tvhome;

import java.util.Calendar;

/** Tazkirah and quote of the day for the prayer screen. */
final class DailyReflection {

    static final class Item {
        final String label;
        final String text;
        final String source;

        Item(String label, String text, String source) {
            this.label = label;
            this.text = text;
            this.source = source;
        }
    }

    private static final int[] IMAGES = {
            R.drawable.islam_putra,
            R.drawable.islam_nabawi,
            R.drawable.islam_haram
    };

    private static final String[] IMAGE_CAPTIONS = {
            "Masjid Putra, Putrajaya",
            "Masjid Nabawi, Madinah",
            "Masjidil Haram, Makkah"
    };

    private static final Item[] TAZKIRAH = {
            new Item("Tazkirah",
                    "Solat pada waktunya lebih baik daripada dunia dan seisinya. Jangan biarkan urusan dunia menunda panggilan Allah.",
                    "Peringatan harian"),
            new Item("Tazkirah",
                    "Perbanyak istighfar. Pintu rezeki, ketenangan dan keampunan dibuka dengan lidah yang basah mengingat Allah.",
                    "Peringatan harian"),
            new Item("Tazkirah",
                    "Senyum kepada saudara adalah sedekah. Mulakan rumah dengan kata yang lembut dan niat yang ikhlas.",
                    "Peringatan harian"),
            new Item("Tazkirah",
                    "Jaga solat Subuh. Siapa yang solat Subuh berjemaah, dia dalam jaminan Allah sepanjang hari.",
                    "Peringatan harian"),
            new Item("Tazkirah",
                    "Hati yang tenang bukan kerana dunia yang sempurna, tetapi kerana zikir yang tidak putus.",
                    "Peringatan harian"),
            new Item("Tazkirah",
                    "Berbakti kepada ibu bapa adalah jalan ke syurga yang paling dekat. Doakan mereka setiap selepas solat.",
                    "Peringatan harian"),
            new Item("Tazkirah",
                    "Sedekah tidak mengurangkan harta. Apa yang engkau berikan kerana Allah, itulah yang kekal.",
                    "Peringatan harian"),
            new Item("Tazkirah",
                    "Jika marah, diam. Jika ragu, tanya. Jika mampu, maafkan. Itulah akhlak yang dibawa Nabi.",
                    "Peringatan harian")
    };

    private static final Item[] QUOTES = {
            new Item("Quote of the Day",
                    "Sesungguhnya amalan itu bergantung kepada niat, dan setiap orang akan mendapat apa yang dia niatkan.",
                    "Hadis riwayat al-Bukhari & Muslim"),
            new Item("Quote of the Day",
                    "Sebaik-baik kamu ialah orang yang mempelajari Al-Quran dan mengajarkannya.",
                    "Hadis riwayat al-Bukhari"),
            new Item("Quote of the Day",
                    "Seseorang daripada kamu tidak beriman sehingga dia mengasihi saudaranya sebagaimana dia mengasihi dirinya sendiri.",
                    "Hadis riwayat al-Bukhari & Muslim"),
            new Item("Quote of the Day",
                    "Bertakwalah kepada Allah di mana sahaja kamu berada, iringi kejahatan dengan kebaikan yang menghapusnya, dan bergaullah dengan manusia secara elok.",
                    "Hadis riwayat al-Tirmidzi"),
            new Item("Quote of the Day",
                    "Senyummu kepada saudaramu adalah sedekah.",
                    "Hadis riwayat al-Tirmidzi"),
            new Item("Quote of the Day",
                    "Siapa yang beriman kepada Allah dan hari akhir, hendaklah dia berkata baik atau diam.",
                    "Hadis riwayat al-Bukhari & Muslim"),
            new Item("Quote of the Day",
                    "Orang yang kuat bukanlah yang unggul dalam gusti, tetapi yang dapat menguasai dirinya ketika marah.",
                    "Hadis riwayat al-Bukhari"),
            new Item("Quote of the Day",
                    "Dunia ini penjara bagi orang beriman dan syurga bagi orang kafir.",
                    "Hadis riwayat Muslim")
    };

    private DailyReflection() {
    }

    static int imageRes() {
        return IMAGES[dayIndex(IMAGES.length, 1)];
    }

    static String imageCaption() {
        return IMAGE_CAPTIONS[dayIndex(IMAGE_CAPTIONS.length, 1)];
    }

    static Item tazkirah() {
        return TAZKIRAH[dayIndex(TAZKIRAH.length, 3)];
    }

    static Item quote() {
        return QUOTES[dayIndex(QUOTES.length, 7)];
    }

    private static int dayIndex(int len, int offset) {
        int day = Calendar.getInstance().get(Calendar.DAY_OF_YEAR);
        int i = (day + offset) % len;
        return i < 0 ? -i : i;
    }
}
