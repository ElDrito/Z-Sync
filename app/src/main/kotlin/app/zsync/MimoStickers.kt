package app.zsync

import android.content.Context

object MimoStickers {
    val all = intArrayOf(
        R.drawable.mimo_00,
        R.drawable.mimo_01,
        R.drawable.mimo_02,
        R.drawable.mimo_03,
        R.drawable.mimo_04,
        R.drawable.mimo_05,
        R.drawable.mimo_06,
        R.drawable.mimo_07,
        R.drawable.mimo_08,
        R.drawable.mimo_09,
        R.drawable.mimo_10,
        R.drawable.mimo_11,
        R.drawable.mimo_12,
        R.drawable.mimo_13,
        R.drawable.mimo_14,
        R.drawable.mimo_15,
        R.drawable.mimo_16,
        R.drawable.mimo_17,
        R.drawable.mimo_18,
        R.drawable.mimo_19,
        R.drawable.mimo_20,
        R.drawable.mimo_21,
        R.drawable.mimo_22,
        R.drawable.mimo_23,
        R.drawable.mimo_24,
        R.drawable.mimo_25,
        R.drawable.mimo_26,
        R.drawable.mimo_27,
        R.drawable.mimo_28,
        R.drawable.mimo_29,
        R.drawable.mimo_30,
        R.drawable.mimo_31,
    )

    @Volatile var current: Int = all[0]
        private set

    fun advance(context: Context) {
        val prefs = context.getSharedPreferences("ui", Context.MODE_PRIVATE)
        val i = (prefs.getInt("mimoSticker", -1) + 1).mod(all.size)
        prefs.edit().putInt("mimoSticker", i).apply()
        current = all[i]
    }
}
