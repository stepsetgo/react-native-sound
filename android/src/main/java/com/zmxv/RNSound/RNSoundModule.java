package com.zmxv.RNSound;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.MediaPlayer.OnCompletionListener;
import android.media.MediaPlayer.OnErrorListener;
import android.os.Build;
import android.util.Log;

import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.Callback;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.ReadableMap;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.modules.core.DeviceEventManagerModule;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class RNSoundModule extends ReactContextBaseJavaModule {
    final static Object NULL = null;
    Map<Double, MediaPlayer> playerPool = new HashMap<>();
    ReactApplicationContext context;
    Integer category;
    Boolean mixWithOthers = true;
    Double focusedPlayerKey;
    Boolean wasPlayingBeforeFocusChange = false;
    //    private AudioFocusRequest focusRequest;
    private AudioManager.OnAudioFocusChangeListener focusChangeListener =
        new AudioManager.OnAudioFocusChangeListener() {
            public void onAudioFocusChange(int focusChange) {
                AudioManager am = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
                switch (focusChange) {
                    case (AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK):
                        break;
                    case (AudioManager.AUDIOFOCUS_LOSS_TRANSIENT):
                        break;
                    case (AudioManager.AUDIOFOCUS_LOSS):
                        break;
                    case (AudioManager.AUDIOFOCUS_GAIN):
                        break;
                    default:
                        break;
                }
            }
        };


    public RNSoundModule(ReactApplicationContext context) {
        super(context);
        this.context = context;
        this.category = null;
    }

    private void setOnPlay(boolean isPlaying, final Double playerKey) {
        final ReactContext reactContext = this.context;
        WritableMap params = Arguments.createMap();
        params.putBoolean("isPlaying", isPlaying);
        params.putDouble("playerKey", playerKey);
        reactContext
            .getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class)
            .emit("onPlayChange", params);
    }

    @Override
    public String getName() {
        return "RNSound";
    }

    @ReactMethod
    public void prepare(final String fileName, final Double key, final ReadableMap options, final Callback callback) {

        MediaPlayer player = createMediaPlayer(fileName);
        if (player == null) {
            WritableMap e = Arguments.createMap();
            e.putInt("code", -1);
            e.putString("message", "resource not found");
            callback.invoke(e, NULL);
            return;
        }
        this.playerPool.put(key, player);

        final RNSoundModule module = this;

        if (module.category != null) {
            Integer usage = null; // TO DO UPDATE USAGE

            AudioAttributes playbackAttributes = null;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                playbackAttributes = new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(module.category)
                    .build();

                player.setAudioAttributes(playbackAttributes);
            }

            if (!module.mixWithOthers) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {

                    AudioFocusRequest focusRequest = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                        .setAudioAttributes(playbackAttributes)
                        .setOnAudioFocusChangeListener(this.focusChangeListener)
                        .build();
                }
            }

        }

        player.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
            boolean callbackWasCalled = false;

            @Override
            public synchronized void onPrepared(MediaPlayer mp) {
                if (callbackWasCalled) return;
                callbackWasCalled = true;

                WritableMap props = Arguments.createMap();
                props.putDouble("duration", mp.getDuration() * .001);
                try {
                    callback.invoke(NULL, props);
                } catch (RuntimeException runtimeException) {
                    // The callback was already invoked
                    Log.e("RNSoundModule", "Exception", runtimeException);
                }
            }

        });

        player.setOnErrorListener(new OnErrorListener() {
            boolean callbackWasCalled = false;

            @Override
            public synchronized boolean onError(MediaPlayer mp, int what, int extra) {
                if (callbackWasCalled) return true;
                callbackWasCalled = true;
                try {
                    WritableMap props = Arguments.createMap();
                    props.putInt("what", what);
                    props.putInt("extra", extra);
                    callback.invoke(props, NULL);
                } catch (RuntimeException runtimeException) {
                    // The callback was already invoked
                    Log.e("RNSoundModule", "Exception", runtimeException);
                }
                return true;
            }
        });

        try {
            if (options.hasKey("loadSync") && options.getBoolean("loadSync")) {
                player.prepare();
            } else {
                player.prepareAsync();
            }
        } catch (Exception ignored) {
            // When loading files from a file, we useMediaPlayer.create, which actually
            // prepares the audio for us already. So we catch and ignore this error
            Log.e("RNSoundModule", "Exception", ignored);
        }
    }

    protected MediaPlayer createMediaPlayer(final String fileName) {
        int res = this.context.getResources().getIdentifier(fileName, "raw", this.context.getPackageName());
        MediaPlayer mediaPlayer = new MediaPlayer();
        if (res != 0) {
            try {
                AssetFileDescriptor afd = context.getResources().openRawResourceFd(res);
                mediaPlayer.setDataSource(afd.getFileDescriptor(), afd.getStartOffset(), afd.getLength());
                afd.close();
            } catch (IOException e) {
                Log.e("RNSoundModule", "Exception", e);
                return null;
            }
            return mediaPlayer;
        }

        if (fileName.startsWith("http://") || fileName.startsWith("https://")) {
            AudioAttributes playbackAttributes = null;
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
                playbackAttributes = new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build();

                mediaPlayer.setAudioAttributes(playbackAttributes);
            }

            Log.i("RNSoundModule", fileName);
            try {
                mediaPlayer.setDataSource(fileName);
            } catch (IOException e) {
                Log.e("RNSoundModule", "Exception", e);
                return null;
            }
            return mediaPlayer;
        }

        if (fileName.startsWith("asset:/")) {
            try {
                AssetFileDescriptor descriptor = this.context.getAssets().openFd(fileName.replace("asset:/", ""));
                mediaPlayer.setDataSource(descriptor.getFileDescriptor(), descriptor.getStartOffset(), descriptor.getLength());
                descriptor.close();
                return mediaPlayer;
            } catch (IOException e) {
                Log.e("RNSoundModule", "Exception", e);
                return null;
            }
        }

        File file = new File(fileName);
        if (file.exists()) {
            AudioAttributes playbackAttributes = null;
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
                playbackAttributes = new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build();

                mediaPlayer.setAudioAttributes(playbackAttributes);
            }

            Log.i("RNSoundModule", fileName);
            try {
                mediaPlayer.setDataSource(fileName);
            } catch (IOException e) {
                Log.e("RNSoundModule", "Exception", e);
                return null;
            }
            return mediaPlayer;
        }

        return null;
    }



    @ReactMethod
    public void play(final Double key, final Callback callback) {
        MediaPlayer player = this.playerPool.get(key);
        final RNSoundModule module = this;
        if (player == null) {
            setOnPlay(false, key);
            if (callback != null) {
                callback.invoke(false);
            }
            return;
        }
        if (player.isPlaying()) {
            return;
        }


        // Request audio focus in Android system
        if (!this.mixWithOthers) {
            boolean playbackNowAuthorized = false;
            int audioFocusRequest;
            this.focusedPlayerKey = key;
            AudioManager audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {


                AudioAttributes playbackAttributes = new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(module.category)
                    .build();
                AudioFocusRequest focusRequest = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                    .setAudioAttributes(playbackAttributes)
                    .setOnAudioFocusChangeListener(this.focusChangeListener)
                    .build();
                audioFocusRequest = audioManager.requestAudioFocus(focusRequest);
            } else {
                audioFocusRequest = audioManager.requestAudioFocus(focusChangeListener, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK);
            }

            if (audioFocusRequest == AudioManager.AUDIOFOCUS_REQUEST_FAILED) {
                playbackNowAuthorized = false;
            } else if (audioFocusRequest == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                playbackNowAuthorized = true;
                player.start();
                setOnPlay(true, key);
            }
        }


        player.setOnCompletionListener(new OnCompletionListener() {
            boolean callbackWasCalled = false;

            @Override
            public synchronized void onCompletion(MediaPlayer mp) {
                if (!mixWithOthers) {
                    AudioManager audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {

                        AudioAttributes playbackAttributes = new AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(module.category)
                            .build();
                        AudioFocusRequest focusRequest = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                            .setAudioAttributes(playbackAttributes)
                            .setOnAudioFocusChangeListener(focusChangeListener)
                            .build();
                        audioManager.abandonAudioFocusRequest(focusRequest);
                    } else {
                        audioManager.abandonAudioFocus(focusChangeListener);
                    }
                }
                if (!mp.isLooping()) {
                    setOnPlay(false, key);
                    if (callbackWasCalled) return;
                    callbackWasCalled = true;
                    try {
                        callback.invoke(true);
                    } catch (Exception e) {
                        //Catches the exception: java.lang.RuntimeException·Illegal callback invocation from native module
                    }
                }
            }
        });

        player.setOnErrorListener(new OnErrorListener() {
            boolean callbackWasCalled = false;

            @Override
            public synchronized boolean onError(MediaPlayer mp, int what, int extra) {
                AudioManager audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {

                    AudioAttributes playbackAttributes = new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(module.category)
                        .build();
                    AudioFocusRequest focusRequest = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                        .setAudioAttributes(playbackAttributes)
                        .setOnAudioFocusChangeListener(focusChangeListener)
                        .build();
                    audioManager.abandonAudioFocusRequest(focusRequest);
                } else {
                    audioManager.abandonAudioFocus(focusChangeListener);
                }
                setOnPlay(false, key);
                if (callbackWasCalled) return true;
                callbackWasCalled = true;
                try {
                    callback.invoke(true);
                } catch (Exception e) {
                    //Catches the exception: java.lang.RuntimeException·Illegal callback invocation from native module
                }
                return true;
            }
        });

        player.start();
        setOnPlay(true, key);
    }

    @ReactMethod
    public void pause(final Double key, final Callback callback) {
        MediaPlayer player = this.playerPool.get(key);
        if (player != null && player.isPlaying()) {
            player.pause();
        }

        if (callback != null) {
            callback.invoke();
        }
    }


    @ReactMethod
    public void stop(final Double key, final Callback callback) {
        MediaPlayer player = this.playerPool.get(key);
        final RNSoundModule module = this;
        if (player != null && player.isPlaying()) {
            player.pause();
            player.seekTo(0);
        }

        // Release audio focus in Android system
        if (!this.mixWithOthers && key == this.focusedPlayerKey) {
            AudioManager audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                AudioAttributes playbackAttributes = new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(module.category)
                    .build();
                AudioFocusRequest focusRequest = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                    .setAudioAttributes(playbackAttributes)
                    .setOnAudioFocusChangeListener(focusChangeListener)
                    .build();
                audioManager.abandonAudioFocusRequest(focusRequest);
            } else {
                audioManager.abandonAudioFocus(focusChangeListener);
            }
        }

        callback.invoke();
    }

    @ReactMethod
    public void reset(final Double key) {
        MediaPlayer player = this.playerPool.get(key);
        if (player != null) {
            player.reset();
        }
    }

    @ReactMethod
    public void release(final Double key) {
        MediaPlayer player = this.playerPool.get(key);
        final RNSoundModule module = this;

        if (player != null) {
            player.reset();
            player.release();
            this.playerPool.remove(key);

            // Release audio focus in Android system
            if (!this.mixWithOthers && key == this.focusedPlayerKey) {
                AudioManager audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {

                    AudioAttributes playbackAttributes = new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(module.category)
                        .build();
                    AudioFocusRequest focusRequest = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                        .setAudioAttributes(playbackAttributes)
                        .setOnAudioFocusChangeListener(focusChangeListener)
                        .build();
                    audioManager.abandonAudioFocusRequest(focusRequest);
                } else {
                    audioManager.abandonAudioFocus(focusChangeListener);
                }
            }
        }
    }

    @Override
    public void onCatalystInstanceDestroy() {
        java.util.Iterator it = this.playerPool.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry entry = (Map.Entry) it.next();
            MediaPlayer player = (MediaPlayer) entry.getValue();
            if (player != null) {
                player.reset();
                player.release();
            }
            it.remove();
        }
    }

    @ReactMethod
    public void setVolume(final Double key, final Float left, final Float right) {
        MediaPlayer player = this.playerPool.get(key);
        if (player != null) {
            player.setVolume(left, right);
        }
    }

    @ReactMethod
    public void getSystemVolume(final Callback callback) {
        try {
            AudioManager audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);

            callback.invoke((float) audioManager.getStreamVolume(AudioManager.STREAM_MUSIC) / audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC));
        } catch (Exception error) {
            WritableMap e = Arguments.createMap();
            e.putInt("code", -1);
            e.putString("message", error.getMessage());
            callback.invoke(e);
        }
    }

    @ReactMethod
    public void setSystemVolume(final Float value) {
        AudioManager audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);

        int volume = Math.round(audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC) * value);
        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, volume, 0);
    }

    @ReactMethod
    public void setLooping(final Double key, final Boolean looping) {
        MediaPlayer player = this.playerPool.get(key);
        if (player != null) {
            player.setLooping(looping);
        }
    }

    @ReactMethod
    public void setSpeed(final Double key, final Float speed) {
        if (android.os.Build.VERSION.SDK_INT < 23) {
            Log.w("RNSoundModule", "setSpeed ignored due to sdk limit");
            return;
        }

        MediaPlayer player = this.playerPool.get(key);
        if (player != null) {
            player.setPlaybackParams(player.getPlaybackParams().setSpeed(speed));
        }
    }

    @ReactMethod
    public void setCurrentTime(final Double key, final Float sec) {
        MediaPlayer player = this.playerPool.get(key);
        if (player != null) {
            player.seekTo((int) Math.round(sec * 1000));
        }
    }

    @ReactMethod
    public void getCurrentTime(final Double key, final Callback callback) {
        MediaPlayer player = this.playerPool.get(key);
        if (player == null) {
            callback.invoke(-1, false);
            return;
        }
        callback.invoke(player.getCurrentPosition() * .001, player.isPlaying());
    }

    //turn speaker on
    @ReactMethod
    public void setSpeakerphoneOn(final Double key, final Boolean speaker) {
        MediaPlayer player = this.playerPool.get(key);
        if (player != null) {
            AudioAttributes playbackAttributes = null;
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
                playbackAttributes = new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build();

                player.setAudioAttributes(playbackAttributes);
            }

            AudioManager audioManager = (AudioManager) this.context.getSystemService(Context.AUDIO_SERVICE);
            if (speaker) {
                audioManager.setMode(AudioManager.MODE_IN_COMMUNICATION);
            } else {
                audioManager.setMode(AudioManager.MODE_NORMAL);
            }
            audioManager.setSpeakerphoneOn(speaker);
        }
    }

    @ReactMethod
    public void setCategory(final String category, final Boolean mixWithOthers) {
//        this.category = category;

        switch (category) {
            case "Playback":
                this.category = AudioAttributes.CONTENT_TYPE_MUSIC;
                break;
            case "Ambient":
                this.category = AudioAttributes.CONTENT_TYPE_MOVIE;
                break;
            case "System":
                this.category = AudioAttributes.CONTENT_TYPE_UNKNOWN;
                break;
            case "Voice":
                this.category = AudioAttributes.CONTENT_TYPE_SPEECH;
                break;
            case "Ring":
                this.category = AudioAttributes.CONTENT_TYPE_UNKNOWN;
                break;
            case "Alarm":
                this.category = AudioAttributes.CONTENT_TYPE_UNKNOWN;
                break;
            default:
                Log.e("RNSoundModule", String.format("Unrecognised category %s", this.category));
                break;
        }
        this.mixWithOthers = mixWithOthers;
    }

    @ReactMethod
    public void enable(final Boolean enabled) {
        // no op
    }

    @Override
    public Map<String, Object> getConstants() {
        final Map<String, Object> constants = new HashMap<>();
        constants.put("IsAndroid", true);
        return constants;
    }
}