package com.sanyog.lyricify;

import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.media.AudioManager;
import android.os.Build;
import android.os.Bundle;
import android.os.StrictMode;
import android.preference.PreferenceManager;
import android.text.Html;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.ContextCompat;

import com.google.android.material.switchmaterial.SwitchMaterial;


import com.squareup.okhttp.Callback;
import com.squareup.okhttp.OkHttpClient;
import com.squareup.okhttp.Request;
import com.squareup.okhttp.Response;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;
import jp.wasabeef.blurry.Blurry;

//import static com.sanyog.lyricify.StopServiceBroadcastReceiver.shouldCloseService;
import static com.sanyog.lyricify.Utils.SPOTIFY_PACKAGE;
import static com.sanyog.lyricify.Utils.getTimeStamp;
import static com.sanyog.lyricify.Utils.getTimeStampFromDate;
import static com.sanyog.lyricify.Utils.openApp;

public class MainActivity extends AppCompatActivity implements ReceiverCallback{
    private ImageButton togglePlayPause;
    private LinearLayout mediaButtons;
    private static SwitchMaterial adSwitch;
    private SpotifyBroadcastReceiver spotifyBroadcastReceiver;
    private AudioManager audioManager;
    private TextView songInfoTextView, track, isPlaying, lastUpdated;
    private int playbackPosition;
    SharedPreferences prefs;
    ArrayList<Double> times = new ArrayList<>();
    ArrayList<String> lyrics = new ArrayList<>();
    ArrayList<Double> times_new = new ArrayList<>();
    TextView outputTextlyrics;
    final String base_url = "https://apic-desktop.musixmatch.com/ws/1.1/macro.subtitles.get?format=json&namespace=lyrics_synched&part=lyrics_crowd%2Cuser%2Clyrics_verified_by&tags=nowplaying&user_language=en&f_subtitle_length_max_deviation=1&subtitle_format=mxm&app_id=web-desktop-app-v1.0";
    String final_url;
    String q_track, q_artist, q_album;
    Thread showLyricsThread;
    Song gSong;
    String track_old = "";
    ImageView album_art;
    String album_art_url = "";
    Toolbar toolbar;
    final String api_key_default = "21062742510293ae06df230e7cd334d26ae4cf17646b37514af666";
    String api_key = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        StrictMode.ThreadPolicy policy = new StrictMode.ThreadPolicy.Builder().permitAll().build();

        StrictMode.setThreadPolicy(policy);

        outputTextlyrics = findViewById(R.id.terminalOutput);
        album_art = findViewById(R.id.albumartimage);
        //abl = findViewById(R.id.abl);

        //toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        adSwitch = findViewById(R.id.adSwitch);
        adSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> checkSwitch(isChecked));
        track = findViewById(R.id.track);
        songInfoTextView = findViewById(R.id.songInfoTextView);
        isPlaying = findViewById(R.id.isPlaying);
        lastUpdated = findViewById(R.id.lastUpdated);
        togglePlayPause = findViewById(R.id.togglePlayPause);
        mediaButtons = findViewById(R.id.mediaButtons);
        mediaButtons.setVisibility(View.GONE);
        audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        spotifyBroadcastReceiver = new SpotifyBroadcastReceiver(this);
        prefs = PreferenceManager.getDefaultSharedPreferences(this);
        if(!prefs.contains("Launched"))
        {
            showAlertDialog();
            setAPIkey();
        }
    }

    public void showLyrics() {
        Log.d("Inside", "showLyrics()");
        work();
        showLyricsThread = new Thread() {
            public void run() {
                if (showLyricsThread.interrupted()) {
                    Thread.currentThread().interrupt();
                    return;
                } else {
                    try {
                        for (int i = 0; i < lyrics.size(); i++) {
                            Log.d("Inside last loop", "I is at " + String.valueOf(i));
                            Thread.sleep((long) (times_new.get(i) * 1000));
                            updateUi(String.valueOf(lyrics.get(i)));
                            Log.d("Playback position", String.valueOf(gSong.getPlaybackPosition()));
                            if (i + 1 == lyrics.size()) {
                                return;
                            }
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        showLyricsThread = null;
                        runOnUiThread(
                                new Runnable() {
                                    @Override
                                    public void run() {
                                        final String ot = "Lyrics will appear here";
                                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                                            outputTextlyrics.setText(Html.fromHtml(ot, Html.FROM_HTML_MODE_COMPACT));
                                        } else {
                                            outputTextlyrics.setText(Html.fromHtml(ot));
                                        }
                                    }
                                }
                        );
                        return;
                    }
                }
            }
        };
        showLyricsThread.start();
    }

    public JSONObject getLyrics() {
        final_url = base_url + "&" + api_key + "&" + q_track + "&" + q_artist + "&" + q_album;
        Log.d("Key", api_key);
        Log.d("Final URL", final_url);
        try {
            OkHttpClient client = new OkHttpClient();
            Request request = new Request.Builder()
                    .addHeader("authority", "apic-desktop.musixmatch.com")
                    .addHeader("method", "GET")
                    .addHeader("scheme", "https")
                    .addHeader("accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3;q=0.9")
                    .addHeader("accept-language", "en-GB")
                    .addHeader("cookie", "_ga=GA1.2.1690725417.1617372359; x-mxm-user-id=mxm%3A394566873cac28d30d2fdfff94965a8e; x-mxm-token-guid=fa9cc7fa-58a3-415e-9a7f-afa7d612520b; mxm-encrypted-token=; AWSELB=55578B011601B1EF8BC274C33F9043CA947F99DCFF8378C231564BC3E68894E08BD389E37D70BDE22DD3BE8B057337BA725B076A5437EFD5DCF9DA7B0658AA87EB7AE701D7; AWSELBCORS=55578B011601B1EF8BC274C33F9043CA947F99DCFF8378C231564BC3E68894E08BD389E37D70BDE22DD3BE8B057337BA725B076A5437EFD5DCF9DA7B0658AA87EB7AE701D7; _gid=GA1.2.398348776.1622458629; _gat=1")
                    .addHeader("user-agent", "Mozilla/5.0 (Windows NT 10.0; WOW64) AppleWebKit/537.36 (KHTML, like Gecko) Musixmatch/3.14.4564-master.20200505002 Chrome/78.0.3904.130 Electron/7.1.5 Safari/537.36")
                    .url(final_url)
                    .method("GET", null)
                    .build();
            final Response[] responses = {null};
            client.setFollowRedirects(true);

            client.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Request request, IOException e) {

                }

                @Override
                public void onResponse(final Response response) throws IOException {
                    if (!response.isSuccessful()) {
                        throw new IOException("Unexpected code " + response);
                    } else {
                        responses[0] = response;
                        Log.d("Response", response.toString());
                        try {
                            Thread.sleep(5000);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }
                }
            });

            try {
                responses[0] = client.newCall(request).execute();
            } catch (IOException e) {
                e.printStackTrace();
            }
            final String myResponse = responses[0].body().string();
            JSONObject Jobject = new JSONObject(myResponse);
            Log.d("JSON DATA", Jobject.toString());
            return Jobject;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    public void parseLyrics(JSONObject jsonObject) {
        JSONArray new_json = null;
        JSONArray new_jobj = null;
        String testabc = "";
        if (jsonObject == null) {
            return;
        }
        try {
            new_json = jsonObject.getJSONObject("message").getJSONObject("body").getJSONObject("macro_calls").getJSONObject("track.subtitles.get").getJSONObject("message").getJSONObject("body").getJSONArray("subtitle_list");
            album_art_url = get_album_art_url(jsonObject);
            Log.d("Album art URL", album_art_url);
            testabc = String.valueOf(new_json.getJSONObject(0).getJSONObject("subtitle").getString("subtitle_body"));
            new_jobj = new JSONArray(testabc);

        } catch (JSONException e) {
            e.printStackTrace();
        }
        if (new_jobj == null) {

        } else {
            for (int i = 0; i < new_jobj.length(); i++) {
                try {
                    JSONObject oneObject = new_jobj.getJSONObject(i);
                    times.add(oneObject.getJSONObject("time").getDouble("total"));
                    lyrics.add(String.valueOf(oneObject.getString("text")));
                } catch (JSONException e) {

                }
            }
        }
        if(album_art_url != null && album_art_url != "" && album_art_url != " ") {
            Bitmap b = null;
            b = getBitmapFromURL(album_art_url);
            int col = getDominantColor(b);
            changeNavBarColour(col);
            Log.d("colour", String.valueOf(col));
            if (b != null) {
                Blurry.with(getApplicationContext()).from(b).into(album_art);
            }
        }
    }

    public void work() {
        Log.d("Inside","work()");
        parseLyrics(getLyrics());
        for (int i = 0; i < times.size(); i++) {
            if (i == 0) {
                times_new.add(times.get(i));
                continue;
            } else {
                times_new.add((times.get(i) - times.get(i - 1)));
            }
        }
        Log.d("New times list", String.valueOf(times_new));

    }

    public void updateUi(String text) {
        text = "<b>" + text + "</b>";
        String finalText = text;
        runOnUiThread(
                new Runnable() {
                    @Override
                    public void run() {
                        final String ot = finalText;
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                            outputTextlyrics.setText(Html.fromHtml(finalText, Html.FROM_HTML_MODE_COMPACT));
                        } else {
                            outputTextlyrics.setText(Html.fromHtml(finalText));
                        }
                    }
                }
        );
    }

    public Bitmap getBitmapFromURL(String imageUrl) {
        try {
            Log.d("imageurl", imageUrl);
            URL url = new URL(imageUrl);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setDoInput(true);
            connection.connect();
            InputStream input = connection.getInputStream();
            Bitmap myBitmap = BitmapFactory.decodeStream(input);
            return myBitmap;
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    @Override
    public void metadataChanged(Song song) {
        gSong = song;
        track.setText(String.format("%s (%s)", song.getTrack(), getTimeStamp(song.getLength())));
        songInfoTextView.setText(String.format("By %s\nFrom %s",
                song.getArtist(), song.getAlbum()));
        lastUpdated.setText(String.format("(info updated @%s)", getTimeStampFromDate(song.getTimeSent())));
        Log.d("Playback", String.valueOf(song.getPlaybackPosition()));
        track_old = song.getTrack();
        q_track = song.getTrack();
        q_artist = song.getArtist();
        q_album = song.getAlbum();
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        api_key = sp.getString("API_key", api_key_default);
        song.setPlaybackPosition(0);
        try {
            q_track = URLEncoder.encode(q_track, "utf-8");
            q_artist = URLEncoder.encode(q_artist, "utf-8");
            q_album = URLEncoder.encode(q_album, "utf-8");
        } catch (UnsupportedEncodingException e) {
            Log.d("UnsupportedEncoding", "Failed to encode");
        }
        q_track = "q_track=" + q_track;
        q_artist = "q_artist=" + q_artist;
        q_album = "q_album=" + q_album;
        api_key = "usertoken="+api_key;
        Log.d("q_track", q_track);
        Log.d("q_artist", q_artist);
        Log.d("q_album", q_album);
        Log.i("New Song", song.getTrack());
    }

    @Override
    public void playbackStateChanged(boolean playState, int playbackPos, Song song) {
        playbackPosition = playbackPos;
        updatePlayPauseButton(playState);
        if (playState) {
            isPlaying.setText(R.string.play_state_text);
        } else {
            isPlaying.setText(R.string.last_detected_song);
        }
        handleNewSongIntent(playState, song);
    }

    private void handleNewSongIntent(boolean playState, Song song) {

    }

    private long calculateTime(Song songFromIntent) {
        return songFromIntent.timeRemaining(playbackPosition) - songFromIntent.getElapsedTime();
    }



    private void mute() {
        if (getMusicVolume() != 0) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_MUTE, 0);
            } else {
                audioManager.setStreamMute(AudioManager.STREAM_MUSIC, true);
            }
        }
    }

    private void unmute() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_UNMUTE, 0);
        } else {
            audioManager.setStreamMute(AudioManager.STREAM_MUSIC, false);
        }
    }

    public void checkSwitch(boolean isChecked) {
        if (isChecked) {
            if (isSpotifyInstalled()) {
                startService();
            } else {
                adSwitch.setChecked(!adSwitch.isChecked());
                Toast.makeText(this, "Spotify is not installed", Toast.LENGTH_LONG).show();
            }
            mediaButtons.setVisibility(View.VISIBLE);
        } else {
            stopService();
            mediaButtons.setVisibility(View.GONE);
        }
    }

    private void startService() {
        IntentFilter filter = new IntentFilter();
        filter.addAction(SpotifyBroadcastReceiver.BroadcastTypes.PLAYBACK_STATE_CHANGED);
        filter.addAction(SpotifyBroadcastReceiver.BroadcastTypes.METADATA_CHANGED);
        registerReceiver(spotifyBroadcastReceiver, filter);
    }

    private void stopService() {
        try {
            unregisterReceiver(spotifyBroadcastReceiver);
        } catch (Exception e) {
            Log.wtf("error while stopping", e.getMessage());
        }
        songInfoTextView.setText("");
        isPlaying.setText("");
        track.setText("");
        lastUpdated.setText("");
    }
    private int getMusicVolume() {
        return audioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
    }

    public void handleOpenSpotify(View view) {
        openSpotify();
    }

    public void openSpotify() {
        if (isSpotifyInstalled()) {
            openApp(getApplicationContext(), SPOTIFY_PACKAGE);
        } else {
            Toast.makeText(this, "Could not find Spotify!", Toast.LENGTH_SHORT).show();
        }
    }

    private void changeNavBarColour(int col) {
        getWindow().setNavigationBarColor(col);
        getWindow().setStatusBarColor(col);
    }

    @Override
    protected void onResume() {
        super.onResume();
        updatePlayPauseButton(audioManager.isMusicActive());
    }

    public static void closeSwitch() {
        adSwitch.setChecked(false);
    }


    @Override
    public void onBackPressed() {
        moveTaskToBack(true);
    }

    public void handleMuteUnmute(View view) {
        int id = view.getId();
        if (id == R.id.unMute) {
            unmute();
        } else if (id == R.id.mute) {
            mute();
        }
    }

    public void handleMedia(View view) {
        int id = view.getId();
        if (id == R.id.previous) {
            if (audioManager.isMusicActive()) {
                KeyEvent event = new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PREVIOUS);
                audioManager.dispatchMediaKeyEvent(event);
            }
        } else if (id == R.id.togglePlayPause) {// get music playing info
            boolean isMusicActive = audioManager.isMusicActive();
            if (isMusicActive) {
                KeyEvent pause = new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PAUSE);
                if (showLyricsThread != null) {
                    showLyricsThread.interrupt();
                }
                audioManager.dispatchMediaKeyEvent(pause);
                updatePlayPauseButton(false);
            } else {
                KeyEvent play = new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PLAY);
                times.clear();
                lyrics.clear();
                times_new.clear();
                showLyrics();
                audioManager.dispatchMediaKeyEvent(play);
                updatePlayPauseButton(true);
            }
        } else if (id == R.id.next) {
            if (audioManager.isMusicActive()) {
                KeyEvent next = new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_NEXT);
                audioManager.dispatchMediaKeyEvent(next);
            }
        }
    }

    private void updatePlayPauseButton(boolean isMusicActive) {
        if (isMusicActive) {
            togglePlayPause.setImageDrawable(ContextCompat.getDrawable(this, R.drawable.ic_baseline_pause));
        } else {
            togglePlayPause.setImageDrawable(ContextCompat.getDrawable(this, R.drawable.ic_baseline_play_arrow));
        }
    }

    private void showAlertDialog() {
        TextView titleView = new TextView(this);
        titleView.setText(getString(R.string.dialog_title));
        titleView.setTextSize(20.0f);
        titleView.setPadding(15, 20, 15, 20);
        titleView.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
        TextView messageView = new TextView(this);
        messageView.setText(getString(R.string.dialog_message));
        messageView.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
        messageView.setTextSize(16.0f);
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setCustomTitle(titleView);
        builder.setView(messageView);
        builder.setCancelable(false);
        builder.setPositiveButton(R.string.dialog_positive_btn, (dialog, which) -> {
        });
        builder.setNegativeButton(R.string.dialog_negative_btn, (dialog, which) -> {
        });
        AlertDialog dialog = builder.create();
        dialog.show();
        Button positive = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
        Button negative = dialog.getButton(AlertDialog.BUTTON_NEGATIVE);
        positive.setOnClickListener(v -> {
            if (isSpotifyInstalled()) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    Intent spotifySettings = new Intent("android.intent.action.APPLICATION_PREFERENCES").setPackage(SPOTIFY_PACKAGE);
                    startActivity(spotifySettings);
                    Toast.makeText(this, "Scroll down and Enable Device Broadcast Status…", Toast.LENGTH_LONG).show();
                } else {
                    openSpotify();
                    Toast.makeText(this, "Enable Device Broadcast Status from Spotify settings…", Toast.LENGTH_LONG).show();
                }
            } else {
                Toast.makeText(this, "Couldn't find Spotify installed!", Toast.LENGTH_SHORT).show();
            }
        });
        negative.setOnClickListener(v -> {
            dialog.dismiss();
        });
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        sp.edit().putBoolean("Launched", true).apply();
    }

    public void setAPIkey()
    {
        AlertDialog.Builder alert = new AlertDialog.Builder(this);
        final EditText edittext = new EditText(this);
        alert.setView(edittext);
        alert.setTitle("Enter your API key.");
        alert.setMessage("Please enter your API key below. If you don't have one, skip this section");
        alert.setPositiveButton("Done", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int whichButton) {
                String api_key = edittext.getText().toString();
                SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
                sp.edit().putString("API_key", api_key).apply();
            }
        });

        alert.setNegativeButton("I don't have a key", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int whichButton) {
                //TODO Show an alert with steps to get an API key
                //Toast.makeText(getApplicationContext(), "Lyrics won't work without an API key", Toast.LENGTH_LONG).show();
            }
        });
        alert.show();

    }
    private boolean isSpotifyInstalled() {
        final PackageManager packageManager = this.getPackageManager();
        Intent intent = packageManager.getLaunchIntentForPackage(SPOTIFY_PACKAGE);
        if (intent == null) {
            return false;
        }
        List<ResolveInfo> list = packageManager.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY);
        return !list.isEmpty();
    }

    public static int getDominantColor(Bitmap bitmap) {
        if (bitmap == null) {
            return Color.TRANSPARENT;
        }
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        int size = width * height;
        int pixels[] = new int[size];
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height);
        int color;
        int r = 0;
        int g = 0;
        int b = 0;
        int a;
        int count = 0;
        for (int i = 0; i < pixels.length; i++) {
            color = pixels[i];
            a = Color.alpha(color);
            if (a > 0) {
                r += Color.red(color);
                g += Color.green(color);
                b += Color.blue(color);
                count++;
            }
        }
        r /= count;
        g /= count;
        b /= count;
        r = (r << 16) & 0x00FF0000;
        g = (g << 8) & 0x0000FF00;
        b = b & 0x000000FF;
        color = 0xFF000000 | r | g | b;
        return color;
    }
    public String get_album_art_url(JSONObject jobj)
    {
        String ab_url = null;
        try {
            ab_url = jobj.getJSONObject("message").getJSONObject("body").getJSONObject("macro_calls").getJSONObject("matcher.track.get").getJSONObject("message").getJSONObject("body").getJSONObject("track").getString("album_coverart_800x800");
            Log.d("ab_url 800", " "+ab_url.length());
            if(ab_url == "" || ab_url == " " || ab_url == null || ab_url.length() == 0)
            {
                throw new JSONException("");
            }
        } catch (JSONException e) {
            try {
                ab_url = jobj.getJSONObject("message").getJSONObject("body").getJSONObject("macro_calls").getJSONObject("matcher.track.get").getJSONObject("message").getJSONObject("body").getJSONObject("track").getString("album_coverart_500x500");
                Log.d("ab_url 500"," "+ ab_url.length());
                if(ab_url == "" || ab_url == " " || ab_url == null || ab_url.length() == 0)
                {
                    throw new JSONException("");
                }
            }
            catch(JSONException e1)
            {
                try {
                    ab_url = jobj.getJSONObject("message").getJSONObject("body").getJSONObject("macro_calls").getJSONObject("matcher.track.get").getJSONObject("message").getJSONObject("body").getJSONObject("track").getString("album_coverart_350x350");
                    Log.d("ab_url 350", " "+ab_url.length());
                    if(ab_url == "" || ab_url == " " || ab_url == null || ab_url.length() == 0)
                    {
                        throw new JSONException("");
                    }
                }
                catch(JSONException e2)
                {
                    try {
                        ab_url = jobj.getJSONObject("message").getJSONObject("body").getJSONObject("macro_calls").getJSONObject("matcher.track.get").getJSONObject("message").getJSONObject("body").getJSONObject("track").getString("album_coverart_100x100");
                        Log.d("ab_url 100", " "+ab_url.length());
                        if(ab_url == "" || ab_url == " " || ab_url == null || ab_url.length() == 0)
                        {
                            throw new JSONException("");
                        }
                    }
                    catch(JSONException e3)
                    {

                    }
                }
            }
            e.printStackTrace();
        }
        return ab_url;
    }
}