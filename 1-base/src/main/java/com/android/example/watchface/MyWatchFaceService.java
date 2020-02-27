/*
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.example.watchface;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.os.BatteryManager;
import android.os.Handler;
import android.os.Message;
import android.support.wearable.watchface.CanvasWatchFaceService;
import android.support.wearable.watchface.WatchFaceStyle;
import android.util.Log;
import android.view.SurfaceHolder;
import android.graphics.BitmapFactory;
import android.widget.Toast;


import java.util.Calendar;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;

import static android.graphics.BitmapFactory.*;

/**
 * Analog watch face with a ticking second hand. In ambient mode, the second hand isn't shown. On
 * devices with low-bit ambient mode, the hands are drawn without anti-aliasing in ambient mode.
 */
public class MyWatchFaceService extends CanvasWatchFaceService {

    /**
     * Update rate in milliseconds for interactive mode. We update once a second to advance the
     * second hand.
     */
    private static final long INTERACTIVE_UPDATE_RATE_MS = TimeUnit.SECONDS.toMillis(1);
    private static final float HAND_END_CAP_RADIUS = 4f;
    private static final float SHADOW_RADIUS = 6f;

    @Override
    public Engine onCreateEngine() {

        return new Engine();
    }

    private class Engine extends CanvasWatchFaceService.Engine {

        Bitmap mBackgroundBitmap = decodeResource(getResources(), R.drawable.custom_background);

        Bitmap mSecondhandBitmap = decodeResource(getResources(), R.drawable.hand_second_image);
        Bitmap mMinutehandBitmap = decodeResource(getResources(), R.drawable.hand_minute_image);
        Bitmap nHourhandBitmap = decodeResource(getResources(), R.drawable.hand_hour_image);

        /* Handler to update the time once a second in interactive mode. */
        private final Handler mUpdateTimeHandler = new Handler() {
            @Override
            public void handleMessage(Message message) {
                if (R.id.message_update == message.what) {
                    invalidate();
                    if (shouldTimerBeRunning()) {
                        long timeMs = System.currentTimeMillis();
                        long delayMs = INTERACTIVE_UPDATE_RATE_MS
                                - (timeMs % INTERACTIVE_UPDATE_RATE_MS);
                        mUpdateTimeHandler.sendEmptyMessageDelayed(R.id.message_update, delayMs);
                    }
                }
            }
        };

        private final BroadcastReceiver mTimeZoneReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                mCalendar.setTimeZone(TimeZone.getDefault());
                invalidate();
            }
        };

        private boolean mRegisteredTimeZoneReceiver = false;

        private static final float STROKE_WIDTH = 3f;

        private Calendar mCalendar;

        private Paint mBackgroundPaint;
        private Paint mHandPaint;

        private boolean mAmbient;

        private float mHourHandLength;
        private float mMinuteHandLength;
        private float mSecondHandLength;

        private int mWidth;
        private int mHeight;
        private float mCenterX;
        private float mCenterY;
        private float mScale = 1;
        private float mScaleHands = 1;

        @Override
        public void onCreate(SurfaceHolder holder) {
            super.onCreate(holder);

            //setWatchFaceStyle(new WatchFaceStyle.Builder(MyWatchFaceService.this).build());
            setWatchFaceStyle(new WatchFaceStyle.Builder(MyWatchFaceService.this).setAcceptsTapEvents(true).build());


            mBackgroundPaint = new Paint();
            mBackgroundPaint.setColor(Color.BLACK);

            mHandPaint = new Paint();
            mHandPaint.setColor(Color.WHITE);
            mHandPaint.setStrokeWidth(STROKE_WIDTH);
            mHandPaint.setAntiAlias(true);
            mHandPaint.setStrokeCap(Paint.Cap.ROUND);
            mHandPaint.setShadowLayer(SHADOW_RADIUS, 0,0,Color.BLACK);
            mHandPaint.setStyle(Paint.Style.STROKE);

            mCalendar = Calendar.getInstance();

        }

        @Override
        public void onDestroy() {
            mUpdateTimeHandler.removeMessages(R.id.message_update);
            super.onDestroy();
        }

        @Override
        public void onTimeTick() {
            super.onTimeTick();
            invalidate();
        }

        @Override
        public void onAmbientModeChanged(boolean inAmbientMode) {
            super.onAmbientModeChanged(inAmbientMode);
            if (mAmbient != inAmbientMode) {
                mAmbient = inAmbientMode;
                invalidate();
            }

            /*
             * Whether the timer should be running depends on whether we're visible (as well as
             * whether we're in ambient mode), so we may need to start or stop the timer.
             */
            updateTimer();
        }

        @Override
        public void onSurfaceChanged(SurfaceHolder holder, int format, int width, int height) {
            super.onSurfaceChanged(holder, format, width, height);
            mWidth = width;
            mHeight = height;



            mScale = ((float) width) / (float) mBackgroundBitmap.getWidth();

            mBackgroundBitmap = Bitmap.createScaledBitmap
                    (mBackgroundBitmap, (int)(mBackgroundBitmap.getWidth() * mScale),
                            (int)(mBackgroundBitmap.getHeight() * mScale), true);

            mScaleHands = ((float) width) / (float) mBackgroundBitmap.getWidth();

            mSecondhandBitmap = Bitmap.createScaledBitmap
                    (mSecondhandBitmap, (int)(mSecondhandBitmap.getWidth() * mScaleHands ),
                           (int)(mSecondhandBitmap.getHeight() * mScaleHands ), true);

            mMinutehandBitmap = Bitmap.createScaledBitmap
                    (mMinutehandBitmap, (int)(mMinutehandBitmap.getWidth() * mScaleHands ),
                            (int)(mMinutehandBitmap.getHeight() * mScaleHands ), true);

            nHourhandBitmap = Bitmap.createScaledBitmap
                    (nHourhandBitmap, (int)(nHourhandBitmap.getWidth() * mScaleHands ),
                            (int)(nHourhandBitmap.getHeight() * mScaleHands ), true);

            /*
             * Find the coordinates of the center point on the screen.
             * Ignore the window insets so that, on round watches
             * with a "chin", the watch face is centered on the entire screen,
             * not just the usable portion.
             */
            mCenterX = mWidth / 2f;
            mCenterY = mHeight / 2f;
            /*
             * Calculate the lengths of the watch hands and store them in member variables.
             */
            mHourHandLength = 0.5f * mWidth / 2f; //mCenterX - 80;
            mMinuteHandLength = 0.7f * width / 2f; //mCenterX - 40;
            mSecondHandLength = 0.9f * width / 2f; //mCenterX - 20;
        }


        private void drawHand(Canvas canvas, float handLength) {
            canvas.drawRoundRect(mCenterX - HAND_END_CAP_RADIUS,
                    mCenterY - handLength, mCenterX + HAND_END_CAP_RADIUS,
                    mCenterY + HAND_END_CAP_RADIUS, HAND_END_CAP_RADIUS,
                    HAND_END_CAP_RADIUS, mHandPaint);
        }

        @Override
        public void onDraw(Canvas canvas, Rect bounds) {
            long now = System.currentTimeMillis();
            mCalendar.setTimeInMillis(now);

            // Draw the background.
            //canvas.drawRect(0, 0, canvas.getWidth(), canvas.getHeight(), mBackgroundPaint);
            canvas.drawBitmap(mBackgroundBitmap, 0, 0, mBackgroundPaint);
            //canvas.drawBitmap(mSecondhandBitmap, mCenterX - 13f, -45f, mBackgroundPaint);

            BatteryManager bm = (BatteryManager)getSystemService(BATTERY_SERVICE);
            int batLevel = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY);
            Paint paint = new Paint();
            //paint.setColor(Color.WHITE);
            //paint.setStyle(Style.FILL);
            //canvas.drawPaint(paint);

            paint.setColor(Color.WHITE);
            paint.setTextSize(20);
            //canvas.drawText("Some Text", mCenterX, mCenterY, paint);
            canvas.drawText(String.valueOf(batLevel) + "%", mCenterX - mWidth / 12f, mCenterY + mHeight / 8f, paint);


            Calendar calendar = Calendar.getInstance();
            int daynow = calendar.get(Calendar.DAY_OF_MONTH);
            canvas.drawText(String.valueOf(daynow), mCenterX + mWidth / 3.2f, mCenterY + mHeight / 42f, paint);

            /*
             * These calculations reflect the rotation in degrees per unit of time, e.g.,
             * 360 / 60 = 6 and 360 / 12 = 30.
             */
            final float seconds =
                    (mCalendar.get(Calendar.SECOND) + mCalendar.get(Calendar.MILLISECOND) / 1000f);
            final float secondsRotation = seconds * 6f;

            final float minutesRotation = mCalendar.get(Calendar.MINUTE) * 6f;

            final float hourHandOffset = mCalendar.get(Calendar.MINUTE) / 2f;
            final float hoursRotation = (mCalendar.get(Calendar.HOUR) * 30) + hourHandOffset;

            // save the canvas state before we begin to rotate it
            canvas.save();

            /*
            //simple line vector hands
            canvas.rotate(hoursRotation, mCenterX, mCenterY);
            canvas.drawLine(mCenterX, mCenterY, mCenterX, mCenterY - mHourHandLength, mHandPaint);

            canvas.rotate(minutesRotation - hoursRotation, mCenterX, mCenterY);
            canvas.drawLine(mCenterX, mCenterY, mCenterX, mCenterY - mMinuteHandLength, mHandPaint);

            if (!mAmbient) {
                canvas.rotate(secondsRotation - minutesRotation, mCenterX, mCenterY);
                canvas.drawLine(mCenterX, mCenterY, mCenterX, mCenterY - mSecondHandLength,
                        mHandPaint);
            }
            // restore the canvas' original orientation.
            canvas.restore();

            */
            // /*
            // paperclip vector style hands
            canvas.rotate(hoursRotation, mCenterX, mCenterY);
            //drawHand(canvas, mHourHandLength); //vector
            canvas.drawBitmap(nHourhandBitmap, mCenterX -  nHourhandBitmap.getWidth() * mScaleHands /2f,   mCenterY  - nHourhandBitmap.getHeight() * mScaleHands /2f, mBackgroundPaint);
            canvas.rotate(minutesRotation - hoursRotation, mCenterX, mCenterY);
            //drawHand(canvas, mMinuteHandLength); //vector
            canvas.drawBitmap(mMinutehandBitmap, mCenterX -  mMinutehandBitmap.getWidth() * mScaleHands /2f,   mCenterY  - mMinutehandBitmap.getHeight() * mScaleHands /2f, mBackgroundPaint);
            canvas.rotate(secondsRotation - minutesRotation, mCenterX, mCenterY);
            //canvas.drawLine(mCenterX, mCenterY - HAND_END_CAP_RADIUS, mCenterX,mCenterY - mSecondHandLength, mHandPaint);
            canvas.drawBitmap(mSecondhandBitmap, mCenterX -  mSecondhandBitmap.getWidth() * mScaleHands /2f,   mCenterY - mSecondhandBitmap.getHeight() * mScaleHands /2f, mBackgroundPaint);


            //canvas.drawCircle(mCenterX, mCenterY, HAND_END_CAP_RADIUS * 2f, mHandPaint);


            canvas.restore();
            //Log.d("err", "second-----------------------");
             // */
        }

        @Override
        public void onVisibilityChanged(boolean visible) {
            super.onVisibilityChanged(visible);

            if (visible) {
                registerReceiver();

                // Update time zone in case it changed while we weren't visible.
                mCalendar.setTimeZone(TimeZone.getDefault());
                invalidate();
            } else {
                unregisterReceiver();
            }

            /*
            * Whether the timer should be running depends on whether we're visible
            * (as well as whether we're in ambient mode),
            * so we may need to start or stop the timer.
            */
            updateTimer();
        }

        private void registerReceiver() {
            if (mRegisteredTimeZoneReceiver) {
                return;
            }
            mRegisteredTimeZoneReceiver = true;
            IntentFilter filter = new IntentFilter(Intent.ACTION_TIMEZONE_CHANGED);
            MyWatchFaceService.this.registerReceiver(mTimeZoneReceiver, filter);
        }

        private void unregisterReceiver() {
            if (!mRegisteredTimeZoneReceiver) {
                return;
            }
            mRegisteredTimeZoneReceiver = false;
            MyWatchFaceService.this.unregisterReceiver(mTimeZoneReceiver);
        }

        private void updateTimer() {
            mUpdateTimeHandler.removeMessages(R.id.message_update);
            if (shouldTimerBeRunning()) {
                mUpdateTimeHandler.sendEmptyMessage(R.id.message_update);
            }
        }


        public void onTapCommand(int tapType, int x, int y, long eventTime) {
            switch (tapType) {
                case TAP_TYPE_TOUCH:
                    // The user has started touching the screen.
                    break;
                case TAP_TYPE_TOUCH_CANCEL:
                    // The user has started a different gesture or otherwise cancelled the tap.
                    break;
                case TAP_TYPE_TAP:
                    // The user has completed the tap gesture.
                    // TODO: Add code to handle the tap gesture.
                    //Toast.makeText(getApplicationContext(), "Hello from Nick", Toast.LENGTH_SHORT).show();
                    Toast.makeText(getBaseContext(), "Watch app by Nick Agro", Toast.LENGTH_SHORT).show();
                    //Log.d("err", "onTapCommand: tap detected");
                    break;
            }
            invalidate();
        }


        /**
         * Returns whether the {@link #mUpdateTimeHandler} timer should be running. The timer
         * should only run when we're visible and in interactive mode.
         */
        private boolean shouldTimerBeRunning() {
            return isVisible() && !isInAmbientMode();
        }
    }
}
