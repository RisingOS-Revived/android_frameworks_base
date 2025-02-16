/*
 * SPDX-FileCopyrightText: 2023-2024 the risingOS Android Project
 * SPDX-FileCopyrightText: 2025 DerpFest AOSP
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.systemui.island

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.app.ActivityTaskManager
import android.app.ActivityOptions
import android.app.Notification
import android.app.PendingIntent
import android.content.pm.ApplicationInfo
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.Icon
import android.graphics.Region
import android.graphics.Typeface
import android.media.AudioManager
import android.os.AsyncTask
import android.os.Bundle
import android.os.Handler
import android.os.UserHandle
import android.os.VibrationEffect
import android.os.Vibrator
import android.service.notification.StatusBarNotification
import android.telecom.TelecomManager
import android.text.SpannableString
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.RelativeSizeSpan
import android.text.style.StyleSpan
import android.text.TextUtils
import android.util.AttributeSet
import android.util.IconDrawableFactory
import android.util.Log
import android.view.animation.AccelerateInterpolator
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.MotionEvent
import android.view.GestureDetector
import android.view.GestureDetector.SimpleOnGestureListener
import android.view.VelocityTracker
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.view.ViewTreeObserver
import androidx.transition.AutoTransition
import androidx.transition.TransitionManager
import com.android.systemui.island.IslandAnimator
import com.android.systemui.island.IslandGestureHandler
import com.android.systemui.island.IslandNotificationManager
import com.android.systemui.res.R
import com.android.systemui.shared.system.TaskStackChangeListener
import com.android.systemui.shared.system.TaskStackChangeListeners
import com.android.systemui.statusbar.policy.HeadsUpManager
import com.android.systemui.statusbar.notification.stack.NotificationStackScrollLayout
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton

import com.android.settingslib.drawable.CircleFramedDrawable

import kotlin.math.abs
import kotlin.text.Regex
import java.util.concurrent.Executors
import java.util.concurrent.Executor
import java.util.concurrent.ExecutorService
import java.util.concurrent.RejectedExecutionException
import java.util.Locale
import java.lang.ref.WeakReference

class IslandView : ExtendedFloatingActionButton {

    companion object {
        private const val TAG = "IslandView"
        private const val ANIMATION_DURATION = 600L
        private const val ANIMATION_DELAY = 150L
        private const val DISMISS_ANIMATION_DURATION = 300L
        private const val MAX_TEXT_LENGTH = 28
        private const val SCALE_START = 0f
        private const val SCALE_PEAK = 1.1f
        private const val SCALE_END = 1f
        private const val ALPHA_START = 0f
        private const val ALPHA_END = 1f
    }

    private var notificationStackScroller: WeakReference<NotificationStackScrollLayout>? = null
    private var headsUpManager: WeakReference<HeadsUpManager>? = null

    private var subtitleColor: Int = Color.parseColor("#66000000")
    private var titleSpannable: SpannableString = SpannableString("")
    private var islandText: SpannableStringBuilder = SpannableStringBuilder()
    private var notifTitle: String = ""
    private var notifContent: String = ""
    private var notifSubContent: String = ""
    private var notifPackage: String = ""
    private var topActivityPackage: String = ""

    private var isIslandAnimating = false
    private var isDismissed = true
    private var isTouchInsetsRemoved = true
    private var isExpanded = false
    private var isNowPlaying = false
    private var isPostPoned = false
    private var isStackRegistered = false
    private var isLongPress = false

    private val effectClick: VibrationEffect = VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK)
    private val effectTick: VibrationEffect = VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK)

    private var telecomManager: TelecomManager? = null
    private var vibrator: Vibrator? = null

    private val bgExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private lateinit var animator: IslandAnimator
    private lateinit var notificationManager: IslandNotificationManager
    private var gestureHandler: IslandGestureHandler? = null

    private val taskStackChangeListener = object : TaskStackChangeListener {
        override fun onTaskStackChanged() {
            try {
                bgExecutor.execute {
                    updateForegroundTaskSync()
                }
            } catch (e: RejectedExecutionException) {}
        }
    }

    private val insetsListener = ViewTreeObserver.OnComputeInternalInsetsListener { internalInsetsInfo ->
        internalInsetsInfo.touchableRegion.setEmpty()
        internalInsetsInfo.setTouchableInsets(ViewTreeObserver.InternalInsetsInfo.TOUCHABLE_INSETS_REGION)
        val mainLocation = IntArray(2)
        getLocationOnScreen(mainLocation)
        internalInsetsInfo.touchableRegion.set(Region(
            mainLocation[0],
            mainLocation[1],
            mainLocation[0] + width,
            mainLocation[1] + height
        ))
    }

    constructor(context: Context) : super(context) { init(context) }

    constructor(context: Context, attrs: AttributeSet) : super(context, attrs) { init(context) }

    constructor(context: Context, attrs: AttributeSet, defStyleAttr: Int) : super(context, attrs, defStyleAttr) { init(context) }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        TaskStackChangeListeners.getInstance().unregisterTaskStackListener(taskStackChangeListener)
        cleanUpResources()
        notificationStackScroller = null
        headsUpManager = null
        telecomManager = null
        vibrator = null
        gestureHandler = null
        bgExecutor.shutdownNow()
        handler.removeCallbacksAndMessages(null)
    }
    
    private fun updateForegroundTaskSync() {
        try {
            val focusedStack = ActivityTaskManager.getService().getFocusedRootTaskInfo()
            topActivityPackage = focusedStack?.topActivity?.packageName ?: ""
        } catch (e: Exception) {}
    }

    fun init(context: Context) {
        this.visibility = View.GONE
        telecomManager = context.getSystemService(Context.TELECOM_SERVICE) as TelecomManager
        vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        animator = IslandAnimator(this)
        notificationManager = IslandNotificationManager(context)
        TaskStackChangeListeners.getInstance().registerTaskStackListener(taskStackChangeListener)
    }

    fun setScroller(scroller: NotificationStackScrollLayout?) {
        this.notificationStackScroller = WeakReference(scroller)
    }

    fun setHeadsupManager(headsUp: HeadsUpManager?) {
        this.headsUpManager = WeakReference(headsUp)
    }

    private fun removeHun() {
        val key = headsUpManager?.get()?.getTopEntry()?.row?.entry?.key ?: return
        val reason = "HUN removed" // Provide a meaningful reason for the removal
        headsUpManager?.get()?.removeNotification(key, true /* releaseImmediately */, false /* animate */, reason)
    }

    fun showIsland(show: Boolean, expandedFraction: Float) {
        if (show) {
            animateShowIsland(expandedFraction)
        } else {
            animateDismissIsland()
        }
    }

    fun animateShowIsland(expandedFraction: Float) {
        if (expandedFraction > 0.0f) return
        
        post {
            notificationStackScroller?.get()?.visibility = View.GONE
            setIslandContents(true)
            
            if (!shouldShowIslandNotification() || this.icon == null && this.text.isBlank()) {
                isPostPoned = true
                return@post
            }
            
            if (isIslandAnimating && !isDismissed) {
                isPostPoned = true
                shrink()
                postOnAnimationDelayed({ hide() }, IslandAnimator.ANIMATION_DELAY)
                return@post
            }
            
            show()
            translationX = 0f
            isDismissed = false
            isIslandAnimating = true

            animator.createShowAnimator().start()

            postOnAnimationDelayed({
                extend()
                isPostPoned = false
                postOnAnimationDelayed({ addInsetsListener() }, IslandAnimator.ANIMATION_DELAY)
            }, IslandAnimator.ANIMATION_DELAY)
        }
    }

    fun animateDismissIsland() {
        if (isDismissed) return
        
        post {
            resetLayout()
            shrink()

            animator.createDismissAnimator().start()

            postOnAnimationDelayed({
                hide()
                isIslandAnimating = false
                isDismissed = true
                removeInsetsListener()
                postOnAnimationDelayed({
                    if (isDismissed && !isIslandAnimating && isTouchInsetsRemoved && !isPostPoned) {
                        notificationStackScroller?.get()?.visibility = View.VISIBLE
                        cleanUpResources()
                    }
                }, 500L)
            }, IslandAnimator.ANIMATION_DELAY)
        }
    }
    
    fun cleanUpResources() {
        recycleBitmap((this.icon as? BitmapDrawable)?.bitmap)
        this.icon = null
        vibrator?.cancel()
        vibrator = null
        this.visibility = View.GONE
    }

    fun updateIslandVisibility(expandedFraction: Float) {
        if (expandedFraction > 0.0f) {
            notificationStackScroller?.get()?.visibility = View.VISIBLE
            this.visibility = View.GONE
            isDismissed = true
            removeInsetsListener()
        } else if (!isDismissed && isIslandAnimating && expandedFraction == 0.0f) {
            notificationStackScroller?.get()?.visibility = View.GONE
            this.visibility = View.VISIBLE
            addInsetsListener()
        }
    }

    fun addInsetsListener() {
        if (!isTouchInsetsRemoved) return
        viewTreeObserver.addOnComputeInternalInsetsListener(insetsListener)
        isTouchInsetsRemoved = false
    }

    fun removeInsetsListener() {
        if (isTouchInsetsRemoved) return
        viewTreeObserver.removeOnComputeInternalInsetsListener(insetsListener)
        isTouchInsetsRemoved = true
    }

    fun setIslandBackgroundColorTint() {
        this.backgroundTintList = ColorStateList.valueOf(context.getColor(R.color.island_background_color))
        setTextColor(ColorStateList.valueOf(context.getColor(R.color.island_title_color)))
        subtitleColor = context.getColor(R.color.island_subtitle_color)
    }

    private fun prepareIslandContent() {
        val sbn = headsUpManager?.get()?.getTopEntry()?.row?.entry?.sbn ?: return
        val notification = sbn.notification
        val (islandTitle, islandText) = notificationManager.resolveNotificationContent(notification)
        val iconDrawable = sequenceOf(
            Notification.EXTRA_CONVERSATION_ICON,
            Notification.EXTRA_LARGE_ICON_BIG,
            Notification.EXTRA_LARGE_ICON,
            Notification.EXTRA_SMALL_ICON
        ).mapNotNull { key -> notificationManager.getDrawableFromExtras(notification.extras, key, context) }
            .firstOrNull() ?: notificationManager.getNotificationIcon(sbn, notification) ?: return
        val appLabel = notificationManager.getAppLabel(getActiveAppVolumePackage())
        isNowPlaying = sbn.packageName == "com.android.systemui" &&
                       islandTitle.toLowerCase(Locale.ENGLISH).equals(
                           context.getString(R.string.now_playing_on, appLabel).toLowerCase(Locale.ENGLISH)
                       )
        val isSystem = sbn.packageName == "android" || sbn.packageName == "com.android.systemui"
        notifTitle = when {
            isNowPlaying ->
                { islandText.takeIf { it.isNotBlank() } ?: return } // island now playing
            isSystem && !isNowPlaying -> { "" } // USB debugging notification etc
            else -> {
                islandTitle.takeIf { it.isNotBlank() } ?: return // normal apps
            }
        }
        notifContent = if (isNowPlaying) {
            "" // No content for now playing notifications
        } else {
            islandText.takeIf { it.isNotBlank() } ?: "" // Normal apps
        }
        notifSubContent = if (isNowPlaying) "" else notification.extras.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString().orEmpty()
        titleSpannable = SpannableString(notifTitle.ifEmpty { notifContent }).apply {
            setSpan(StyleSpan(Typeface.BOLD), 0, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        val resources = context.resources
        val bitmap = drawableToBitmap(iconDrawable)
        val roundedIcon = CircleFramedDrawable(bitmap, this.iconSize)
        this.icon = roundedIcon
        this.iconTint = null
        this.bringToFront()
        notifPackage = if (isNowPlaying) getActiveAppVolumePackage() else sbn.packageName
        setOnTouchListener(sbn.notification.contentIntent, notifPackage)
    }

    private fun getActiveAppVolumePackage(): String {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        for (av in audioManager.listAppVolumes()) {
            if (av.isActive) {
                return av.getPackageName()
            }
        }
        return ""
    }

    fun drawableToBitmap(drawable: Drawable): Bitmap {
        if (drawable is BitmapDrawable) {
            return drawable.bitmap
        }
        val bitmap = Bitmap.createBitmap(drawable.intrinsicWidth, drawable.intrinsicHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)
        recycleBitmap((this.icon as? BitmapDrawable)?.bitmap)
        return bitmap
    }

    private fun recycleBitmap(bitmap: Bitmap?) {
        if (bitmap != null && !bitmap.isRecycled) {
            bitmap.recycle()
        }
    }

    private fun SpannableStringBuilder.appendSpannable(spanText: String, size: Float, singleLine: Boolean) {
        if (!spanText.isBlank()) {
            val spannableText = SpannableString(spanText).apply {
                setSpan(ForegroundColorSpan(subtitleColor), 0, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                setSpan(RelativeSizeSpan(size), 0, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
            append(if (!singleLine) "\n" else " ")
            append(spannableText)
        }
    }

    private fun dismissWithCleanup() {
        visibility = View.GONE
        translationX = 0f
        alpha = 1f
        isDismissed = true
        cleanUpResources()
        removeHun()
        removeInsetsListener()
        isIslandAnimating = false
    }

    private fun setOnTouchListener(intent: PendingIntent?, packageName: String) {
        val threshold = dpToPx(40f)
        gestureHandler = IslandGestureHandler(
            context = context,
            view = this,
            threshold = threshold,
            onDismiss = { direction -> animator.createDismissWithDirectionAnimator(direction) {
                visibility = View.GONE
                translationX = 0f
                isDismissed = true
                cleanUpResources()
                removeHun()
                removeInsetsListener()
                isIslandAnimating = false
            }},
            onSingleTap = { 
                if (intent == null && isDeviceRinging()) {
                    telecomManager?.acceptRingingCall()
                } else {
                    handleIntentLaunch(intent, packageName)
                }
                AsyncTask.execute { vibrator?.vibrate(effectTick) }
            },
            onLongPress = {
                if (isDeviceRinging()) {
                    telecomManager?.endCall()
                } else {
                    setIslandContents(false)
                    isExpanded = true
                    postOnAnimationDelayed({ expandIslandView() }, IslandAnimator.ANIMATION_DELAY)
                }
                AsyncTask.execute { vibrator?.vibrate(effectClick) }
            },
            onCleanup = { dismissWithCleanup() }
        )
        
        setOnTouchListener { _, event -> 
            gestureHandler?.onTouchEvent(event) ?: true
        }
    }

    private fun handleIntentLaunch(pendingIntent: PendingIntent?, packageName: String) {
        val appIntent = context.packageManager.getLaunchIntentForPackage(packageName)?.apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        }

        if (pendingIntent != null) {
            try {
                val options = ActivityOptions.makeBasic()
                options.setPendingIntentBackgroundActivityStartMode(
                    ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED)
                pendingIntent.send(context, 0, appIntent, null, null, null, options.toBundle())
            } catch (e: Exception) {
                appIntent?.let { context.startActivityAsUser(it, UserHandle.CURRENT) }
            }
        } else {
            appIntent?.let { context.startActivityAsUser(it, UserHandle.CURRENT) }
        }
    }

    private fun dpToPx(dp: Float): Float {
        return dp * context.resources.displayMetrics.density
    }

    private fun isDeviceRinging(): Boolean {
        return telecomManager?.isRinging ?: false
    }

    private fun resetLayout() {
        if (isExpanded) {
            val params = this.layoutParams as ViewGroup.MarginLayoutParams
            params.width = ViewGroup.LayoutParams.WRAP_CONTENT
            val margin = 0
            params.setMargins(margin, params.topMargin, margin, params.bottomMargin)
            this.layoutParams = params
        }
        removeSpans(islandText)
        isExpanded = false
    }

    fun expandIslandView() {
        TransitionManager.beginDelayedTransition(parent as ViewGroup, AutoTransition())
        val params = this.layoutParams as ViewGroup.MarginLayoutParams
        params.width = ViewGroup.LayoutParams.MATCH_PARENT
        val margin = resources.getDimensionPixelSize(R.dimen.island_side_margin)
        params.setMargins(margin, params.topMargin, margin, params.bottomMargin)
        this.layoutParams = params
    }

    private fun buildSpannableText(title: SpannableString, content: String, subContent: String, singleLine: Boolean): SpannableStringBuilder {
        return SpannableStringBuilder().apply {
            append(title as CharSequence)
            if (!content.isBlank()) {
                appendSpannable(content, 0.9f, singleLine)
            }
            if (!notifSubContent.isBlank()) {
                appendSpannable(subContent, 0.85f, singleLine)
            }
        }
    }

    private fun setIslandContents(singleLine: Boolean) {
        this.iconSize = if (singleLine) resources.getDimensionPixelSize(R.dimen.island_icon_size) / 2 else resources.getDimensionPixelSize(R.dimen.island_icon_size)
        prepareIslandContent()
        this.apply {
            this.islandText = buildSpannableText(titleSpannable, notifContent, notifSubContent, singleLine)
            if (singleLine) {
                val maxLength = 28
                val singleLineText = if (islandText.length > maxLength) {
                    val spanText = SpannableStringBuilder().append(islandText, 0, maxLength)
                    val ellipsisSpannable = SpannableString("...")
                    ellipsisSpannable.setSpan(ForegroundColorSpan(subtitleColor), 0, 3, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    spanText.append(ellipsisSpannable)
                } else {
                    islandText
                }
                this.text = singleLineText
            } else {
                this.text = islandText
            }
            this.isSingleLine = singleLine
            this.ellipsize = TextUtils.TruncateAt.END
            this.isSelected = singleLine
        }
    }

    private fun removeSpans(builder: SpannableStringBuilder) {
        val spans = builder.getSpans(0, builder.length, Object::class.java)
        for (span in spans) { builder.removeSpan(span) }
        builder.clear()
    }

    private fun shouldShowIslandNotification(): Boolean {
        return !isCurrentNotifActivityOnTop(notifPackage) or !isCurrentNotifActivityOnTop(getActiveAppVolumePackage())
    }

    fun isCurrentNotifActivityOnTop(packageName: String): Boolean {
        return topActivityPackage.isNotEmpty() && topActivityPackage == packageName
    }

}
