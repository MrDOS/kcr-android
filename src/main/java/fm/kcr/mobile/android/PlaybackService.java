package fm.kcr.mobile.android;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;
import android.widget.Toast;

import java.io.IOException;
import java.util.Locale;

public class PlaybackService
        extends Service
        implements MediaPlayer.OnCompletionListener, MediaPlayer.OnErrorListener, MediaPlayer.OnPreparedListener {
    private static final int PLAYBACK_NOTIFICATION_ID = 0x4B4352;
    private static final String STREAM_URL = "http://icecast.commedia.org.uk:8000/keithcommunityradio.mp3";

    private OnPlaybackStateChangeListener playbackStateChangeListener = null;

    private PlaybackState state = PlaybackState.STOPPED;
    private MediaPlayer mediaPlayer = null;

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return Service.START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return new PlaybackBinder();
    }

    /**
     * Set a listener for playback state changes. May be set to <code>null</code> to clear the listener.
     *
     * @param listener the playback state change listener
     */
    public void setOnPlaybackStateChangeListener(OnPlaybackStateChangeListener listener) {
        this.playbackStateChangeListener = listener;
    }

    private void setState(PlaybackState state) {
        this.state = state;

        if (this.playbackStateChangeListener == null) {
            return;
        }

        this.playbackStateChangeListener.onPlaybackStateChange(this.state);
    }

    public PlaybackState getState() {
        return this.state;
    }

    public void play() {
        if (this.state != PlaybackState.STOPPED) {
            /* We're either already playing, or preparing to play. */
            return;
        }

        Notification.Builder notificationBuilder;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel("fm.kcr.mobile.android", "PlaybackService", NotificationManager.IMPORTANCE_NONE);
            channel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
            NotificationManager manager = (NotificationManager) this.getSystemService(Context.NOTIFICATION_SERVICE);
            manager.createNotificationChannel(channel);
            notificationBuilder = new Notification.Builder(this, "fm.kcr.mobile.android");
        } else {
            notificationBuilder = new Notification.Builder(this);
        }

        notificationBuilder
                .setOngoing(true)
                .setSmallIcon(R.drawable.ic_head)
                .setContentTitle("KCR")
                .setContentText("Playing KCR.")
                .setContentIntent(PendingIntent.getActivity(this, 0, new Intent(this, MainActivity.class), 0));

        this.startForeground(PlaybackService.PLAYBACK_NOTIFICATION_ID, notificationBuilder.build());

        this.mediaPlayer = new MediaPlayer();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            this.mediaPlayer.setAudioAttributes(
                    new AudioAttributes.Builder()
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .build());
        }
        try {
            this.mediaPlayer.setDataSource(PlaybackService.STREAM_URL);
        } catch (IOException e) {
            Toast.makeText(this, "Failure loading URL: " + e.getMessage(), Toast.LENGTH_LONG).show();
            return;
        }
        this.mediaPlayer.setOnCompletionListener(this);
        this.mediaPlayer.setOnErrorListener(this);
        this.mediaPlayer.setOnPreparedListener(this);
        this.mediaPlayer.prepareAsync();
        this.setState(PlaybackState.WANT_PLAYBACK);
    }

    public void stop() {
        if (this.state == PlaybackState.STOPPED) {
            return;
        }

        try {
            this.mediaPlayer.stop();
        } catch (IllegalStateException e) {
            /* We're about to throw out the media player, so we don't really care if stopping it
             * gracefully fell through (which is most likely if it's still preparing). */
        }
        this.mediaPlayer.release();
        this.mediaPlayer = null;
        this.setState(PlaybackState.STOPPED);

        this.stopForeground(true);
    }

    @Override
    public void onCompletion(MediaPlayer mp) {
        Toast.makeText(this, "Stream ended.", Toast.LENGTH_SHORT).show();
        this.stop();
    }

    @Override
    public boolean onError(MediaPlayer mp, int what, int extra) {
        Toast.makeText(this, String.format(Locale.getDefault(), "Failure playing back media (%d, %d)", what, extra), Toast.LENGTH_LONG).show();
        this.stop();
        return true;
    }

    @Override
    public void onPrepared(MediaPlayer player) {
        /* TODO: Confirm it's possible for this to be called while handling a `stop()` request.
         * If this is called from another thread (which I suspect it is), it's probably possible. If
         * it isn't, though, this check can be removed. */
        if (this.state != PlaybackState.WANT_PLAYBACK) {
            return;
        }

        player.start();
        player.setWakeMode(this, PowerManager.PARTIAL_WAKE_LOCK);
        this.setState(PlaybackState.PLAYBACK);
    }

    public class PlaybackBinder extends Binder {
        public PlaybackService getService() {
            return PlaybackService.this;
        }
    }

    /**
     * Subscribe to notifications on changes to the playback state.
     */
    public interface OnPlaybackStateChangeListener {
        /**
         * Called when the playback state changes.
         *
         * @param state the new state
         */
        void onPlaybackStateChange(PlaybackState state);
    }

    public enum PlaybackState {
        /**
         * Playback is stopped.
         */
        STOPPED,
        /**
         * Playback has been requested, but it hasn't started yet.
         */
        WANT_PLAYBACK,
        /**
         * Playback is running.
         */
        PLAYBACK
    }
}
