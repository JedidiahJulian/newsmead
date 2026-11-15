package com.newsmead.fragments.account

import android.app.Activity
import android.content.Intent
import androidx.annotation.StringRes
import androidx.appcompat.app.AlertDialog
import com.newsmead.R
import com.newsmead.activities.AccountActivity
import com.newsmead.data.FirebaseHelper
import com.newsmead.data.StudyConfig

/**
 * Shared gate for account-backed actions when browsing in per-session guest mode.
 */
internal object GuestLoginPrompt {
    fun isGuest(): Boolean = !StudyConfig.ACCOUNT_FREE_STUDY && FirebaseHelper.getUid() == "null"

    fun show(activity: Activity, @StringRes messageRes: Int) {
        if (activity.isFinishing || activity.isDestroyed) return
        AlertDialog.Builder(activity)
            .setTitle(R.string.guest_login_prompt_title)
            .setMessage(messageRes)
            .setPositiveButton(R.string.guest_login_prompt_action) { _, _ ->
                openAccount(activity, allowBack = true)
            }
            .setNegativeButton(R.string.guest_login_prompt_cancel, null)
            .show()
    }

    fun openAccount(activity: Activity, allowBack: Boolean, clearTask: Boolean = false) {
        val intent = Intent(activity, AccountActivity::class.java)
        if (allowBack) {
            intent.putExtra(AccountActivity.EXTRA_ALLOW_BACK_TO_GUEST, true)
        }
        if (clearTask) {
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        activity.startActivity(intent)
        if (clearTask) {
            activity.finish()
        }
    }
}
