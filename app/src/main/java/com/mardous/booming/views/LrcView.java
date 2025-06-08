/*
 * Copyright (c) 2024 Christians Martínez Alvarado
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.mardous.booming.views;

import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.os.Looper;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.text.TextUtils;
import android.text.format.DateUtils;
import android.util.AttributeSet;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.Scroller;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.mardous.booming.R;
import com.mardous.booming.extensions.resources.ColorExtKt;
import com.mardous.booming.lyrics.LrcEntry;
import com.mardous.booming.lyrics.LrcLyrics;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 歌词 Created by wcy on 2015/11/9.
 */
@SuppressLint("StaticFieldLeak")
public class LrcView extends View {
    private static final long TIMELINE_KEEP_TIME = 4 * DateUtils.SECOND_IN_MILLIS;

    private final List<LrcEntry> mLrcEntryList = new ArrayList<>();
    private final TextPaint mLrcPaint = new TextPaint();
    private final TextPaint mTimePaint = new TextPaint();
    private Drawable mPlayDrawable;
    private float mDividerHeight;
    private long mAnimationDuration;
    private int mNormalTextColor;
    private float mNormalTextSize;
    private int mCurrentTextColor;
    private float mCurrentTextSize;
    private int mDrawableWidth;
    private int mTimeTextWidth;
    private String mDefaultLabel;
    private float mLrcPadding;
    private OnPlayClickListener mOnPlayClickListener;
    private ValueAnimator mAnimator;
    private GestureDetector mGestureDetector;
    private Scroller mScroller;
    private float mOffset;
    private int mCurrentLine;
    private boolean isShowTimeline;
    private boolean isTouching;
    private boolean isFling;
    private int mTextGravity; // 歌词显示位置，靠左/居中/靠右
    private final Runnable hideTimelineRunnable = new Runnable() {
        @Override
        public void run() {
            if (hasLrc() && isShowTimeline) {
                isShowTimeline = false;
                smoothScrollTo(mCurrentLine);
            }
        }
    };

    private final GestureDetector.SimpleOnGestureListener mSimpleOnGestureListener = new GestureDetector.SimpleOnGestureListener() {
        @Override
        public boolean onDown(@NonNull MotionEvent e) {
            if (hasLrc() && mOnPlayClickListener != null) {
                mScroller.forceFinished(true);
                removeCallbacks(hideTimelineRunnable);
                isTouching = true;
                isShowTimeline = true;
                invalidate();
                return true;
            }
            return super.onDown(e);
        }

        @Override
        public boolean onScroll(MotionEvent e1, @NonNull MotionEvent e2, float distanceX, float distanceY) {
            if (hasLrc()) {
                mOffset -= distanceY;
                mOffset = Math.min(mOffset, getOffset(0));
                mOffset = Math.max(mOffset, getOffset(mLrcEntryList.size() - 1));
                invalidate();
                getParent().requestDisallowInterceptTouchEvent(true);
                return true;
            }
            return super.onScroll(e1, e2, distanceX, distanceY);
        }

        @Override
        public boolean onFling(MotionEvent e1, @NonNull MotionEvent e2, float velocityX, float velocityY) {
            if (hasLrc()) {
                mScroller.fling(
                        0,
                        (int) mOffset,
                        0,
                        (int) velocityY,
                        0,
                        0,
                        (int) getOffset(mLrcEntryList.size() - 1),
                        (int) getOffset(0));
                isFling = true;
                invalidate();
                return true;
            }
            return super.onFling(e1, e2, velocityX, velocityY);
        }

        @Override
        public boolean onSingleTapConfirmed(@NonNull MotionEvent e) {
            if (hasLrc() && mOnPlayClickListener != null) {
                float y = e.getY() - mOffset;
                int tappedLine = getTappedLine(y);
                if (tappedLine >= 0 && tappedLine < mLrcEntryList.size()) {
                    long tappedLineTime = mLrcEntryList.get(tappedLine).getTime();
                    if (mOnPlayClickListener.onPlayClick(tappedLineTime)) {
                        isShowTimeline = false;
                        removeCallbacks(hideTimelineRunnable);
                        mCurrentLine = tappedLine;
                        smoothScrollTo(mCurrentLine);
                        invalidate();
                        return true;
                    }
                }
            }
            return super.onSingleTapConfirmed(e);
        }
    };

    private int getTappedLine(float y) {
        float cumulativeHeight = 0;
        for (int i = 0; i < mLrcEntryList.size(); i++) {
            LrcEntry entry = mLrcEntryList.get(i);
            float lineHeight = entry.getHeight();
            float lineCenter = cumulativeHeight + (lineHeight / 2);

            if (Math.abs(y - lineCenter) < lineHeight / 2) {
                return i;
            }
            cumulativeHeight += lineHeight + mDividerHeight;
        }
        return -1;
    }

    public LrcView(Context context) {
        this(context, null);
    }

    public LrcView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public LrcView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(attrs);
    }

    private void init(AttributeSet attrs) {
        try (TypedArray ta = getContext().obtainStyledAttributes(attrs, R.styleable.LrcView)) {
            mCurrentTextSize = ta.getDimension(R.styleable.LrcView_lrcTextSize, getResources().getDimension(R.dimen.lrc_text_size));
            mNormalTextSize = ta.getDimension(R.styleable.LrcView_lrcNormalTextSize, getResources().getDimension(R.dimen.lrc_text_size));
            if (mNormalTextSize == 0) {
                mNormalTextSize = mCurrentTextSize;
            }

            mDividerHeight = ta.getDimension(R.styleable.LrcView_lrcDividerHeight, getResources().getDimension(R.dimen.lrc_divider_height));
            int defDuration = getResources().getInteger(R.integer.lrc_animation_duration);
            mAnimationDuration = ta.getInt(R.styleable.LrcView_lrcAnimationDuration, defDuration);
            mAnimationDuration = (mAnimationDuration < 0) ? defDuration : mAnimationDuration;
            mNormalTextColor = ta.getColor(R.styleable.LrcView_lrcNormalTextColor, ContextCompat.getColor(getContext(), R.color.lrc_normal_text_color));
            mCurrentTextColor = ta.getColor(R.styleable.LrcView_lrcCurrentTextColor, ContextCompat.getColor(getContext(), R.color.lrc_current_text_color));
            mDefaultLabel = ta.getString(R.styleable.LrcView_lrcLabel);
            mDefaultLabel = TextUtils.isEmpty(mDefaultLabel) ? getContext().getString(R.string.empty_label) : mDefaultLabel;
            mLrcPadding = ta.getDimension(R.styleable.LrcView_lrcPadding, 0);
            mPlayDrawable = ta.getDrawable(R.styleable.LrcView_lrcPlayDrawable);
            mPlayDrawable = (mPlayDrawable == null) ? ContextCompat.getDrawable(getContext(), R.drawable.ic_play_24dp) : mPlayDrawable;
            mTextGravity = ta.getInteger(R.styleable.LrcView_lrcTextGravity, LrcEntry.GRAVITY_CENTER);

            float timelineHeight = ta.getDimension(R.styleable.LrcView_lrcTimelineHeight, getResources().getDimension(R.dimen.lrc_timeline_height));
            float timeTextSize = ta.getDimension(R.styleable.LrcView_lrcTimeTextSize, getResources().getDimension(R.dimen.lrc_time_text_size));

            mDrawableWidth = (int) getResources().getDimension(R.dimen.lrc_drawable_width);
            mTimeTextWidth = (int) getResources().getDimension(R.dimen.lrc_time_width);

            mLrcPaint.setAntiAlias(true);
            mLrcPaint.setTextSize(mNormalTextSize);
            mLrcPaint.setTextAlign(Paint.Align.LEFT);
            mTimePaint.setAntiAlias(true);
            mTimePaint.setTextSize(timeTextSize);
            mTimePaint.setTextAlign(Paint.Align.CENTER);
            //noinspection SuspiciousNameCombination
            mTimePaint.setStrokeWidth(timelineHeight);
            mTimePaint.setStrokeCap(Paint.Cap.ROUND);

            mGestureDetector = new GestureDetector(getContext(), mSimpleOnGestureListener);
            mGestureDetector.setIsLongpressEnabled(false);
            mScroller = new Scroller(getContext());
        }
    }

    public void setDraggable(boolean draggable, OnPlayClickListener onPlayClickListener) {
        if (draggable) {
            if (onPlayClickListener == null) {
                throw new IllegalArgumentException("if draggable == true, onPlayClickListener must not be null");
            }
            mOnPlayClickListener = onPlayClickListener;
        } else {
            mOnPlayClickListener = null;
        }
    }

    public void setLabel(@NonNull String label) {
        runOnUi(() -> {
            mDefaultLabel = label;
            invalidate();
        });
    }

    public void setLRCContent(@Nullable LrcLyrics lyrics) {
        reset();
        if (lyrics != null && lyrics.getHasLines()) {
            mLrcEntryList.addAll(lyrics.getValidLines());
        }
        Collections.sort(mLrcEntryList);
        initEntryList();
        invalidate();
    }

    public boolean hasLrc() {
        return !mLrcEntryList.isEmpty();
    }

    public void updateTime(long time) {
        runOnUi(() -> {
            if (!hasLrc()) {
                return;
            }
            int line = findShowLine(time);
            if (line != mCurrentLine) {
                mCurrentLine = line;
                if (!isShowTimeline) {
                    smoothScrollTo(line);
                } else {
                    invalidate();
                }
            }
        });
    }

    @Deprecated
    public void onDrag(long time) {
        updateTime(time);
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        if (changed) {
            initPlayDrawable();
            initEntryList();
            if (hasLrc()) {
                smoothScrollTo(mCurrentLine, 0L);
            }
        }
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        super.onDraw(canvas);

        int centerY = getHeight() / 2;

        if (!hasLrc()) {
            mLrcPaint.setColor(mCurrentTextColor);

            StaticLayout staticLayout = StaticLayout.Builder.obtain(mDefaultLabel, 0, mDefaultLabel.length(), mLrcPaint, getWidth() - (int) (mLrcPadding * 2))
                    .setAlignment(Layout.Alignment.ALIGN_CENTER)
                    .setLineSpacing(0f, 1f)
                    .setIncludePad(false)
                    .build();
            drawText(canvas, staticLayout, centerY);
            return;
        }

        canvas.translate(0, mOffset);

        float y = 0;
        for (int i = 0; i < mLrcEntryList.size(); i++) {
            if (i > 0) {
                y += ((mLrcEntryList.get(i - 1).getHeight() + mLrcEntryList.get(i).getHeight()) >> 1) + mDividerHeight;
            }
            if (i == mCurrentLine) {
                mLrcPaint.setTypeface(Typeface.defaultFromStyle(Typeface.BOLD));
                mLrcPaint.setTextSize(mCurrentTextSize);
                mLrcPaint.setColor(mCurrentTextColor);
            } else {
                mLrcPaint.setTypeface(Typeface.DEFAULT);
                mLrcPaint.setTextSize(mNormalTextSize);
                mLrcPaint.setColor(ColorExtKt.withAlpha(mNormalTextColor, 0.5f));
            }
            drawText(canvas, mLrcEntryList.get(i).getStaticLayout(), y);
        }
    }

    private void drawText(@NonNull Canvas canvas, @Nullable StaticLayout staticLayout, float y) {
        if (staticLayout == null)
            return;

        canvas.save();
        canvas.translate(mLrcPadding, y - (staticLayout.getHeight() >> 1));
        staticLayout.draw(canvas);
        canvas.restore();
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_UP
                || event.getAction() == MotionEvent.ACTION_CANCEL) {
            isTouching = false;
            if (hasLrc() && !isFling) {
                postDelayed(hideTimelineRunnable, TIMELINE_KEEP_TIME);
            }
        }
        return mGestureDetector.onTouchEvent(event);
    }

    @Override
    public void computeScroll() {
        if (mScroller.computeScrollOffset()) {
            mOffset = mScroller.getCurrY();
            invalidate();
        }

        if (isFling && mScroller.isFinished()) {
            isFling = false;
            if (hasLrc() && !isTouching) {
                postDelayed(hideTimelineRunnable, TIMELINE_KEEP_TIME);
            }
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        removeCallbacks(hideTimelineRunnable);
        super.onDetachedFromWindow();
    }

    private void initPlayDrawable() {
        int l = (mTimeTextWidth - mDrawableWidth) / 2;
        int t = getHeight() / 2 - mDrawableWidth / 2;
        int r = l + mDrawableWidth;
        int b = t + mDrawableWidth;
        mPlayDrawable.setBounds(l, t, r, b);
    }

    private void initEntryList() {
        if (!hasLrc() || getWidth() == 0) {
            return;
        }

        for (LrcEntry lrcEntry : mLrcEntryList) {
            lrcEntry.init(mLrcPaint, (int) getLrcWidth(), mTextGravity);
        }

        mOffset = getHeight() / 2F;
    }

    private void reset() {
        endAnimation();
        mScroller.forceFinished(true);
        isShowTimeline = false;
        isTouching = false;
        isFling = false;
        removeCallbacks(hideTimelineRunnable);
        mLrcEntryList.clear();
        mOffset = 0;
        mCurrentLine = 0;
    }

    private void smoothScrollTo(int line) {
        smoothScrollTo(line, mAnimationDuration);
    }

    private void smoothScrollTo(int line, long duration) {
        float offset = getOffset(line);

        mAnimator = ValueAnimator.ofFloat(mOffset, offset);
        mAnimator.setDuration(duration);
        mAnimator.setInterpolator(new AccelerateDecelerateInterpolator());
        mAnimator.addUpdateListener(
                animation -> {
                    mOffset = (float) animation.getAnimatedValue();
                    invalidate();
                });
        mAnimator.start();
    }

    private void endAnimation() {
        if (mAnimator != null && mAnimator.isRunning()) {
            mAnimator.end();
        }
    }

    private int findShowLine(long time) {
        int left = 0;
        int right = mLrcEntryList.size();
        while (left <= right) {
            int middle = (left + right) / 2;
            long middleTime = mLrcEntryList.get(middle).getTime();

            if (time < middleTime) {
                right = middle - 1;
            } else {
                if (middle + 1 >= mLrcEntryList.size() || time < mLrcEntryList.get(middle + 1).getTime()) {
                    return middle;
                }

                left = middle + 1;
            }
        }

        return 0;
    }

    private float getOffset(int line) {
        if (mLrcEntryList.get(line).offset == Float.MIN_VALUE) {
            float offset = getHeight() / 2F;
            for (int i = 1; i <= line; i++) {
                offset -= ((mLrcEntryList.get(i - 1).getHeight() + mLrcEntryList.get(i).getHeight()) >> 1) + mDividerHeight;
            }
            mLrcEntryList.get(line).offset = offset;
        }
        return mLrcEntryList.get(line).offset;
    }

    private float getLrcWidth() {
        return getWidth() - (mLrcPadding * 3.20f);
    }

    private void runOnUi(Runnable r) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            r.run();
        } else {
            post(r);
        }
    }

    public interface OnPlayClickListener {
        boolean onPlayClick(long time);
    }
}
