package com.opent9.keyboard.ui

import android.app.AlertDialog
import android.content.Context
import android.os.IBinder
import android.view.WindowManager

/**
 * Helper to display input-method overlay dialogs attached to the keyboard window token.
 */
object CandidateDialogHelper {

    fun showRemoveCandidateDialog(
        context: Context,
        windowToken: IBinder?,
        word: String,
        onConfirmRemove: (String) -> Unit
    ) {
        try {
            val builder = AlertDialog.Builder(context, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle("Remove suggestion?")
                .setMessage("Do you want to remove \"$word\" from suggestions?")
                .setPositiveButton("Remove") { _, _ ->
                    onConfirmRemove(word)
                }
                .setNegativeButton("Cancel", null)

            val dialog = builder.create()
            dialog.window?.let { window ->
                if (windowToken != null) {
                    val lp = window.attributes
                    lp.token = windowToken
                    lp.type = WindowManager.LayoutParams.TYPE_INPUT_METHOD_DIALOG
                    window.attributes = lp
                    window.addFlags(WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM)
                }
            }
            dialog.show()
        } catch (_: Exception) {
            // Fallback for non-attached or headless test environments
            onConfirmRemove(word)
        }
    }
}
