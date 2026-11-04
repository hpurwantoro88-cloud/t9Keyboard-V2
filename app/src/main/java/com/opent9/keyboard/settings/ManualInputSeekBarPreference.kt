package com.opent9.keyboard.settings

import android.content.Context
import android.os.Build
import android.text.InputType
import android.util.AttributeSet
import android.view.Gravity
import android.view.ViewGroup
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.SeekBar
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.preference.PreferenceViewHolder
import androidx.preference.SeekBarPreference
import com.opent9.keyboard.R

class ManualInputSeekBarPreference @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = androidx.preference.R.attr.seekBarPreferenceStyle,
    defStyleRes: Int = 0
) : SeekBarPreference(context, attrs, defStyleAttr, defStyleRes) {

    internal var currentSeekBar: SeekBar? = null
    internal var currentValueTextView: TextView? = null

    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        super.onBindViewHolder(holder)
        currentSeekBar = holder.findViewById(androidx.preference.R.id.seekbar) as? SeekBar
        val tv = holder.findViewById(androidx.preference.R.id.seekbar_value) as? TextView
        currentValueTextView = tv

        if (tv != null && showSeekBarValue) {
            setupValueDisplay(tv)
        }
    }

    private fun setupValueDisplay(tv: TextView) {
        val enabled = isEnabled
        tv.isEnabled = enabled
        tv.isClickable = enabled
        tv.isFocusable = enabled

        if (enabled) {
            tv.setBackgroundResource(R.drawable.bg_preference_seekbar_value)
            tv.setOnClickListener {
                showManualInputDialog()
            }
            tv.setOnLongClickListener {
                showManualInputDialog()
                true
            }
        } else {
            tv.background = null
            tv.setOnClickListener(null)
            tv.setOnLongClickListener(null)
        }

        val density = context.resources.displayMetrics.density
        val padH = (8 * density).toInt()
        val padV = (4 * density).toInt()
        tv.setPadding(padH, padV, padH, padV)
        tv.minWidth = (46 * density).toInt()
        tv.minHeight = (32 * density).toInt()
        tv.gravity = Gravity.CENTER

        val tooltip = context.getString(R.string.pref_seekbar_tooltip)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            tv.tooltipText = tooltip
        }
        tv.contentDescription = context.getString(
            R.string.pref_seekbar_content_description,
            title ?: "",
            value
        )
    }

    override fun onClick() {
        super.onClick()
        showManualInputDialog()
    }

    fun showManualInputDialog(): AlertDialog? {
        if (!isEnabled) return null

        val dialogContext = androidx.appcompat.view.ContextThemeWrapper(
            context,
            androidx.appcompat.R.style.Theme_AppCompat_DayNight
        )
        val minVal = min
        val maxVal = max
        val currentVal = value

        val editText = EditText(dialogContext).apply {
            inputType = if (minVal < 0) {
                InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_SIGNED
            } else {
                InputType.TYPE_CLASS_NUMBER
            }
            setText(currentVal.toString())
            selectAll()
            gravity = Gravity.CENTER
            textSize = 20f
            imeOptions = EditorInfo.IME_ACTION_DONE
            setSingleLine(true)
        }

        val density = dialogContext.resources.displayMetrics.density
        val container = FrameLayout(dialogContext).apply {
            val padHorizontal = (24 * density).toInt()
            val padVertical = (8 * density).toInt()
            setPadding(padHorizontal, padVertical, padHorizontal, padVertical)
            addView(
                editText,
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            )
        }

        val dialogTitle = title?.toString() ?: dialogContext.getString(R.string.pref_seekbar_manual_input_title)
        val rangeMsg = dialogContext.getString(R.string.pref_seekbar_range_hint, minVal, maxVal)

        val dialog = AlertDialog.Builder(dialogContext)
            .setTitle(dialogTitle)
            .setMessage(rangeMsg)
            .setView(container)
            .setPositiveButton(R.string.dialog_ok, null)
            .setNegativeButton(R.string.dialog_cancel, null)
            .create()

        dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE)

        dialog.setOnShowListener {
            val positiveBtn = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            positiveBtn?.setOnClickListener {
                val inputStr = editText.text.toString().trim()
                if (inputStr.isEmpty()) {
                    editText.error = dialogContext.getString(R.string.pref_seekbar_error_empty)
                    return@setOnClickListener
                }
                val enteredVal = inputStr.toIntOrNull()
                if (enteredVal == null) {
                    editText.error = dialogContext.getString(R.string.pref_seekbar_error_invalid)
                    return@setOnClickListener
                }
                if (enteredVal !in minVal..maxVal) {
                    editText.error = dialogContext.getString(R.string.pref_seekbar_input_invalid, minVal, maxVal)
                    return@setOnClickListener
                }

                editText.error = null
                if (callChangeListener(enteredVal)) {
                    value = enteredVal
                    currentSeekBar?.progress = enteredVal - minVal
                }
                val imm = dialogContext.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
                imm?.hideSoftInputFromWindow(editText.windowToken, 0)
                dialog.dismiss()
            }

            editText.setOnEditorActionListener { _, actionId, _ ->
                if (actionId == EditorInfo.IME_ACTION_DONE || actionId == EditorInfo.IME_NULL) {
                    positiveBtn?.performClick()
                    true
                } else {
                    false
                }
            }

            editText.requestFocus()
            editText.post {
                val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
                imm?.showSoftInput(editText, InputMethodManager.SHOW_IMPLICIT)
            }
        }

        dialog.show()
        return dialog
    }
}
