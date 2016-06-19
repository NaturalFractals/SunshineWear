/*
 * Copyright (C) 2014 The Android Open Source Project
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

package com.example.android.sunshine.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.wearable.watchface.CanvasWatchFaceService;
import android.support.wearable.watchface.WatchFaceStyle;
import android.text.format.Time;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.WindowInsets;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.PendingResult;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.wearable.Asset;
import com.google.android.gms.wearable.DataApi;
import com.google.android.gms.wearable.DataEvent;
import com.google.android.gms.wearable.DataEventBuffer;
import com.google.android.gms.wearable.DataItem;
import com.google.android.gms.wearable.DataItemBuffer;
import com.google.android.gms.wearable.DataMap;
import com.google.android.gms.wearable.DataMapItem;
import com.google.android.gms.wearable.Wearable;

import java.io.InputStream;
import java.lang.ref.WeakReference;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;

/**
 * Digital watch face with seconds. In ambient mode, the seconds aren't displayed. On devices with
 * low-bit ambient mode, the text is drawn without anti-aliasing in ambient mode.
 */
public class MyWatchFace extends CanvasWatchFaceService {
    private static final Typeface NORMAL_TYPEFACE =
            Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL);

    /**
     * Update rate in milliseconds for interactive mode. We update once a second since seconds are
     * displayed in interactive mode.
     */
    private static final long INTERACTIVE_UPDATE_RATE_MS = TimeUnit.SECONDS.toMillis(1);

    /**
     * Handler message id for updating the time periodically in interactive mode.
     */
    private static final int MSG_UPDATE_TIME = 0;
    /**
     * URI for wearable update
     */
    private static final String WEARABLE_UPDATE_URI = "/wearable-update";
    private GoogleApiClient mGoogleApiClient = null;
    //min temp to be displayed on watch
    private String minTemperature;
    //max temp to be display on watch
    private String maxTemperature;
    //weather icon to be dispalyed on watch
    private Bitmap weatherIcon;
    //current date
    private String currentDate;

    @Override
    public Engine onCreateEngine() {
        return new Engine();
    }

    private class Engine extends CanvasWatchFaceService.Engine implements GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener, ResultCallback<DataItemBuffer>, DataApi.DataListener {
        final Handler mUpdateTimeHandler = new EngineHandler(this);

        final BroadcastReceiver mTimeZoneReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                mTime.clear(intent.getStringExtra("time-zone"));
                mTime.setToNow();
            }
        };

        boolean mRegisteredTimeZoneReceiver = false;

        Paint mBackgroundPaint;
        Paint mTextPaint;

        boolean mAmbient;

        Time mTime;

        float mXOffset;
        float mYOffset;
        float mMaxTempYOffset;
        float mMinTempYOffset;
        float mMaxTempXOffset;
        float mMinTempXOffset;
        float mWeatherXOffset;

        /**
         * Whether the display supports fewer bits for each color in ambient mode. When true, we
         * disable anti-aliasing in ambient mode.
         */
        boolean mLowBitAmbient;

        @Override
        public void onCreate(SurfaceHolder holder) {
            super.onCreate(holder);

            setWatchFaceStyle(new WatchFaceStyle.Builder(MyWatchFace.this)
                    .setCardPeekMode(WatchFaceStyle.PEEK_MODE_VARIABLE)
                    .setBackgroundVisibility(WatchFaceStyle.BACKGROUND_VISIBILITY_INTERRUPTIVE)
                    .setShowSystemUiTime(false)
                    .build());
            Resources resources = MyWatchFace.this.getResources();
            mYOffset = resources.getDimension(R.dimen.digital_y_offset);

            mBackgroundPaint = new Paint();
            mBackgroundPaint.setColor(resources.getColor(R.color.digital_background));

            mTextPaint = new Paint();
            mTextPaint = createTextPaint(resources.getColor(R.color.digital_text));

            mTime = new Time();

            mGoogleApiClient = new GoogleApiClient.Builder(MyWatchFace.this)
                    .addApi(Wearable.API)
                    .addConnectionCallbacks(this)
                    .addOnConnectionFailedListener(this)
                    .build();

            mGoogleApiClient.connect();

            PendingResult<DataItemBuffer> pendingResult = Wearable.DataApi.getDataItems(mGoogleApiClient);
            pendingResult.setResultCallback(new ResultCallback<DataItemBuffer>() {
                @Override
                public void onResult(DataItemBuffer dataItems) {
                    for(DataItem dataItem: dataItems) {
                        Log.d("", "pendingResult: " + dataItem.getUri().getPath());
                        if(dataItem.getUri().getPath().equals(WEARABLE_UPDATE_URI)) {
                            DataMap dataMap = DataMapItem.fromDataItem(dataItem).getDataMap();
                            minTemperature = dataMap.getString("minTemperature", null);
                            maxTemperature = dataMap.getString("maxTemperature", null);
                            currentDate = dataMap.getString("date", null);
                            Asset asset = dataMap.getAsset("weatherIcon");
                            new BitmapLoadTask().execute(asset);
                        }
                    }
                }
            });
        }


        @Override
        public void onDestroy() {
            mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME);
            super.onDestroy();
        }

        private Paint createTextPaint(int textColor) {
            Paint paint = new Paint();
            paint.setColor(textColor);
            paint.setTypeface(NORMAL_TYPEFACE);
            paint.setAntiAlias(true);
            paint.setTextAlign(Paint.Align.CENTER);
            return paint;
        }

        @Override
        public void onVisibilityChanged(boolean visible) {
            super.onVisibilityChanged(visible);

            if (visible) {
                registerReceiver();

                // Update time zone in case it changed while we weren't visible.
                mTime.clear(TimeZone.getDefault().getID());
                mTime.setToNow();
            } else {
                unregisterReceiver();
            }

            // Whether the timer should be running depends on whether we're visible (as well as
            // whether we're in ambient mode), so we may need to start or stop the timer.
            updateTimer();
        }

        private void registerReceiver() {
            if (mRegisteredTimeZoneReceiver) {
                return;
            }
            mRegisteredTimeZoneReceiver = true;
            IntentFilter filter = new IntentFilter(Intent.ACTION_TIMEZONE_CHANGED);
            MyWatchFace.this.registerReceiver(mTimeZoneReceiver, filter);
        }

        private void unregisterReceiver() {
            if (!mRegisteredTimeZoneReceiver) {
                return;
            }
            mRegisteredTimeZoneReceiver = false;
            MyWatchFace.this.unregisterReceiver(mTimeZoneReceiver);
        }

        @Override
        public void onApplyWindowInsets(WindowInsets insets) {
            super.onApplyWindowInsets(insets);

            // Load resources that have alternate values for round watches.
            Resources resources = MyWatchFace.this.getResources();
            boolean isRound = insets.isRound();
            mXOffset = resources.getDimension(isRound
                    ? R.dimen.digital_x_offset_round : R.dimen.digital_x_offset);
            float textSize = resources.getDimension(isRound
                    ? R.dimen.digital_text_size_round : R.dimen.digital_text_size);

            mTextPaint.setTextSize(textSize);
            mMaxTempXOffset = resources.getDimension(R.dimen.digital_max_temp_x_offset);
            mMinTempYOffset = resources.getDimension(R.dimen.digital_min_temp_x_offset);
            mMinTempYOffset = resources.getDimension(R.dimen.digital_min_temp_y_offset);
            mWeatherXOffset = resources.getDimension(R.dimen.digital_weather_x_offset);
        }

        @Override
        public void onPropertiesChanged(Bundle properties) {
            super.onPropertiesChanged(properties);
            mLowBitAmbient = properties.getBoolean(PROPERTY_LOW_BIT_AMBIENT, false);
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
                if (mLowBitAmbient) {
                    mTextPaint.setAntiAlias(!inAmbientMode);
                }
                invalidate();
            }

            // Whether the timer should be running depends on whether we're visible (as well as
            // whether we're in ambient mode), so we may need to start or stop the timer.
            updateTimer();
        }

        @Override
        public void onDraw(Canvas canvas, Rect bounds) {
            // Draw the background.
            canvas.drawRect(0, 0, bounds.width(), bounds.height(), mBackgroundPaint);

            float centerX = bounds.width() / 2;
            float centerY = bounds.height() / 2;

            // Draw H:MM in ambient mode or H:MM:SS in interactive mode.
            mTime.setToNow();
            String text = mAmbient
                    ? String.format("%d:%02d", mTime.hour, mTime.minute)
                    : String.format("%d:%02d", mTime.hour, mTime.minute, mTime.second);
            canvas.drawText(text, bounds.width() / 2, mYOffset, mTextPaint);
            canvas.drawLine(bounds.width() / 2 - 50, mYOffset + 80, bounds.width() / 2 + 50, mYOffset + 80, mTextPaint);
            if(currentDate != null) {
                canvas.drawText(currentDate, bounds.width() / 2, mYOffset + 50, mTextPaint);
            }
            if(weatherIcon != null) {
                canvas.drawBitmap(weatherIcon, centerX - 140, centerY + 20, mBackgroundPaint);
            }
            if(minTemperature != null && maxTemperature != null) {
                canvas.drawText(minTemperature,  centerX + 80, centerY + 80, mTextPaint);
                canvas.drawText(maxTemperature, centerX, centerY + 80, mTextPaint);
            }

        }

        /**
         * Starts the {@link #mUpdateTimeHandler} timer if it should be running and isn't currently
         * or stops it if it shouldn't be running but currently is.
         */
        private void updateTimer() {
            mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME);
            if (shouldTimerBeRunning()) {
                mUpdateTimeHandler.sendEmptyMessage(MSG_UPDATE_TIME);
            }
        }

        /**
         * Returns whether the {@link #mUpdateTimeHandler} timer should be running. The timer should
         * only run when we're visible and in interactive mode.
         */
        private boolean shouldTimerBeRunning() {
            return isVisible() && !isInAmbientMode();
        }

        /**
         * Handle updating the time periodically in interactive mode.
         */
        private void handleUpdateTimeMessage() {
            invalidate();
            if (shouldTimerBeRunning()) {
                long timeMs = System.currentTimeMillis();
                long delayMs = INTERACTIVE_UPDATE_RATE_MS
                        - (timeMs % INTERACTIVE_UPDATE_RATE_MS);
                mUpdateTimeHandler.sendEmptyMessageDelayed(MSG_UPDATE_TIME, delayMs);
            }
        }

        @Override
        public void onConnected(Bundle bundle) {
            Wearable.DataApi.getDataItems(mGoogleApiClient).setResultCallback(this);
            Wearable.DataApi.addListener(mGoogleApiClient, this);
        }

        @Override
        public void onConnectionSuspended(int i) {
            Log.d("", "Connection Suspended");
        }

        @Override
        public void onConnectionFailed(ConnectionResult connectionResult) {
            Log.d("", "CONNECTION FAILED");
        }

        @Override
        public void onResult(DataItemBuffer dataItems) {

        }

        @Override
        public void onDataChanged(DataEventBuffer dataEventBuffer) {
            for(DataEvent dataEvent: dataEventBuffer) {
                if (dataEvent.getType() == DataEvent.TYPE_CHANGED &&
                        dataEvent.getDataItem().getUri().getPath().equals(WEARABLE_UPDATE_URI)) {
                    DataItem dataItem = dataEvent.getDataItem();
                    DataMapItem dataMapItem = DataMapItem.fromDataItem(dataEvent.getDataItem());
                    Asset asset = dataMapItem.getDataMap().getAsset("weatherIcon");
                    weatherIcon = loadBitmapFromAsset(asset);
                    DataMap dataMap = DataMapItem.fromDataItem(dataItem).getDataMap();
                    minTemperature = dataMap.getString("minTemperature");
                    maxTemperature = dataMap.getString("maxTemperature");
                    currentDate = dataMap.getString("date");
                }
            }
            dataEventBuffer.release();
        }
    }

    public Bitmap loadBitmapFromAsset(Asset asset) {
        if (asset == null) {
            throw new IllegalArgumentException("Asset must be non-null");
        }

        ConnectionResult result = mGoogleApiClient.blockingConnect(100, TimeUnit.MILLISECONDS);
        if (!result.isSuccess()) {
            return null;
        }

        // Convert asset into a file descriptor and block until it's ready
        InputStream assetInputStream = Wearable.DataApi.getFdForAsset(mGoogleApiClient, asset).await().getInputStream();
        mGoogleApiClient.disconnect();

        if (assetInputStream == null) {
            Log.w("", "Requested an unknown Asset.");
            return null;
        }

        // Decode the stream into a bitmap
        return BitmapFactory.decodeStream(assetInputStream);
    }

    private static class EngineHandler extends Handler {
        private final WeakReference<MyWatchFace.Engine> mWeakReference;

        public EngineHandler(MyWatchFace.Engine reference) {
            mWeakReference = new WeakReference<>(reference);
        }

        @Override
        public void handleMessage(Message msg) {
            MyWatchFace.Engine engine = mWeakReference.get();
            if (engine != null) {
                switch (msg.what) {
                    case MSG_UPDATE_TIME:
                        engine.handleUpdateTimeMessage();
                        break;
                }
            }
        }
    }

    private class BitmapLoadTask extends AsyncTask<Asset, Void, Bitmap> {
        @Override
        protected Bitmap doInBackground(Asset... params) {
            if(params.length > 0) {
                Asset asset = params[0];
                InputStream inputStream = Wearable.DataApi.getFdForAsset(mGoogleApiClient, asset)
                        .await()
                        .getInputStream();

                if (inputStream == null) {
                    return null;
                }
                return BitmapFactory.decodeStream(inputStream);
            } else {
                return null;
            }
        }

        @Override
        protected void onPostExecute(Bitmap bitmap) {
            super.onPostExecute(bitmap);
            if(bitmap != null) {
                weatherIcon = bitmap;
            }
        }
    }


}
