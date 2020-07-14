package fm.kcr.mobile.android;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.view.View;
import android.widget.Button;

public class MainActivity
        extends Activity
        implements ServiceConnection, PlaybackService.OnPlaybackStateChangeListener {
    PlaybackService playbackService = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        this.setContentView(R.layout.activity_main);

        ((Button) this.findViewById(R.id.playbackButton)).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (MainActivity.this.playbackService == null) {
                    return;
                }

                PlaybackService.PlaybackState state = MainActivity.this.playbackService.getState();
                switch (state) {
                    case STOPPED:
                        MainActivity.this.playbackService.play();
                        break;
                    case PLAYBACK:
                        MainActivity.this.playbackService.stop();
                        break;
                }
            }
        });

        Intent playbackIntent = new Intent(this, PlaybackService.class);
        this.startService(playbackIntent);
        this.bindService(playbackIntent, this, 0);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (this.playbackService != null) {
            this.unbindService(this);
        }
    }

    private void update(PlaybackService.PlaybackState state) {
        boolean buttonEnabled;
        int buttonText;

        switch (state) {
            case STOPPED:
                buttonEnabled = true;
                buttonText = R.string.playback_play;
                break;
            case WANT_PLAYBACK:
                buttonEnabled = false;
                buttonText = R.string.playback_connecting;
                break;
            case PLAYBACK:
                buttonEnabled = true;
                buttonText = R.string.playback_stop;
                break;
            default:
                buttonEnabled = false;
                buttonText = R.string.playback_loading;
        }

        Button playbackButton = ((Button) this.findViewById(R.id.playbackButton));
        playbackButton.setEnabled(buttonEnabled);
        playbackButton.setText(buttonText);
    }

    @Override
    public void onServiceConnected(ComponentName name, IBinder binder) {
        this.playbackService = ((PlaybackService.PlaybackBinder) binder).getService();
        this.playbackService.setOnPlaybackStateChangeListener(this);
        this.update(this.playbackService.getState());
    }

    @Override
    public void onServiceDisconnected(ComponentName name) {
        if (this.playbackService == null) {
            return;
        }
        this.playbackService.setOnPlaybackStateChangeListener(null);
        this.playbackService = null;
    }

    @Override
    public void onPlaybackStateChange(PlaybackService.PlaybackState state) {
        this.update(state);
    }
}
