package dev.example.autotube

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.BaseAdapter
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import coil.load

/** Цвета тёмной темы в стиле видеохостинга. */
object Ui {
    val BG = 0xFF0F0F0F.toInt()
    val CHIP = 0xFF272727.toInt()
    val GRAY = 0xFFAAAAAA.toInt()
}

class ItemAdapter(private val ctx: Context) : BaseAdapter() {
    private var items: List<Item> = emptyList()

    fun set(list: List<Item>) { items = list; notifyDataSetChanged() }
    override fun getCount() = items.size
    override fun getItem(position: Int) = items[position]
    override fun getItemId(position: Int) = position.toLong()

    private fun dp(v: Int) = (v * ctx.resources.displayMetrics.density).toInt()

    private fun build(): LinearLayout {
        val row = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 0, 0, dp(16))
        }
        val w = ctx.resources.displayMetrics.widthPixels - dp(16)
        val img = ImageView(ctx).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            setBackgroundColor(Ui.CHIP)
        }
        row.addView(img, LinearLayout.LayoutParams(w, w * 9 / 16))
        val col = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(4), dp(8), dp(4), 0)
        }
        col.addView(TextView(ctx).apply {
            textSize = 15f; maxLines = 2; setTextColor(Color.WHITE); typeface = Typeface.DEFAULT_BOLD
        })
        col.addView(TextView(ctx).apply { textSize = 12f; maxLines = 1; setTextColor(Ui.GRAY) })
        row.addView(col, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        return row
    }

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val row = (convertView as? LinearLayout) ?: build()
        val item = items[position]
        val img = row.getChildAt(0) as ImageView
        val col = row.getChildAt(1) as LinearLayout
        (col.getChildAt(0) as TextView).text = item.title
        (col.getChildAt(1) as TextView).text = item.subtitle
        if (item.thumb.isNotEmpty()) img.load(item.thumb) else img.setImageDrawable(null)
        return row
    }
}
