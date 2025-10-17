package com.example.refrotech

import android.content.Context
import android.util.AttributeSet
import androidx.recyclerview.widget.RecyclerView

/**
 * RecyclerView that fully expands inside a ScrollView.
 * Ensures all items are visible regardless of count.
 */
class ExpandedRecyclerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
) : RecyclerView(context, attrs, defStyle) {

    override fun onMeasure(widthSpec: Int, heightSpec: Int) {
        // Make RecyclerView expand to show all items
        val expandSpec = MeasureSpec.makeMeasureSpec(
            Int.MAX_VALUE shr 2, // large number to allow full expansion
            MeasureSpec.AT_MOST
        )
        super.onMeasure(widthSpec, expandSpec)
    }
}
