/*
 * Copyright 2013 i'm SpA
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package it.imwatch.nfclottery;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.media.AudioManager;
import android.os.Build;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.support.v7.app.ActionBar;
import android.support.v7.app.ActionBarActivity;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.TextView;
import de.keyboardsurfer.android.widget.crouton.Crouton;
import de.keyboardsurfer.android.widget.crouton.Style;

import java.util.Locale;

/**
 * The Activity that shows the winner's name.
 * <p/>
 * Note: this is marked as <code>exported="false"</code> in the
 * manifest, so this Activity can only be started from within
 * this app.
 */
public class WinnerActivity extends ActionBarActivity implements TextToSpeech.OnInitListener {

    public static final String EXTRA_WINNER_NAME = "winner_name";
    public static final String EXTRA_WINNER_EMAIL = "winner_email";

    private TextToSpeech mTtsEngine;

    // Funny (?) phrase that is read if the TTS is triggered randomly
    // (which I really can't see how could happen, but still...)
    private String mTextToRead = "I have no idea what I'm doing! LOL";

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                             WindowManager.LayoutParams.FLAG_FULLSCREEN);

        try {
            // This could crash in the AppCompat package on Android 4+
            final ActionBar actionBar = getSupportActionBar();
            if (actionBar != null) actionBar.hide();
        }
        catch (Exception ignored) {}

        setContentView(R.layout.winner_activity);

        mTtsEngine = new TextToSpeech(this, this);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
            findViewById(R.id.txt_winner_email).setSystemUiVisibility(View.SYSTEM_UI_FLAG_LOW_PROFILE);
        }

        final Intent intent = getIntent();
        final String winnerName = intent.getStringExtra(EXTRA_WINNER_NAME);
        final String winnerEmail = intent.getStringExtra(EXTRA_WINNER_EMAIL);

        ((TextView) findViewById(R.id.txt_winner_name)).setText(winnerName);
        ((TextView) findViewById(R.id.txt_winner_email)).setText(winnerEmail);

        mTextToRead = winnerName + ".\n\n" + getString(R.string.winner_tts_outro);

        setVolumeControlStream(AudioManager.STREAM_MUSIC);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        // Stops and shuts down the TTS engine
        if (mTtsEngine.isSpeaking()) {
            mTtsEngine.stop();
        }
        mTtsEngine.shutdown();
    }

    @SuppressLint("ResourceAsColor")
    @Override
    public void onInit(int status) {
        if (status == TextToSpeech.SUCCESS) {
            int result = mTtsEngine.setLanguage(Locale.UK);

            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                result = mTtsEngine.setLanguage(Locale.US);

                if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    Crouton.makeText(this, getString(R.string.error_no_tts),
                                     new Style.Builder(Style.ALERT)
                                         .setTextColor(android.R.color.black)
                                         .build())
                           .show();

                    Log.e("WinnerActivity", "English TTS is not supported/installed");
                }
            }
            else {
                speakOut(mTextToRead);
            }
        }
        else {
            Log.e("TTS", "Initilization Failed!");
        }
    }

    /**
     * Reads aloud the winner name.
     *
     * @param textToRead The winner name and congratulations phrase
     */
    private void speakOut(String textToRead) {
        mTtsEngine.speak(getString(R.string.winner_label), TextToSpeech.QUEUE_FLUSH, null);
        mTtsEngine.speak(textToRead, TextToSpeech.QUEUE_ADD, null);
    }
}