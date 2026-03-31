package com.gielinordailies;

import lombok.extern.slf4j.Slf4j;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.sound.sampled.*;
import java.io.BufferedInputStream;
import java.io.InputStream;

@Slf4j
@Singleton
public class SoundPlayer
{
    private static final String SOUND_FILE = "/com/gielinordailies/task_complete.wav";

    private volatile boolean playing = false;

    @Inject
    public SoundPlayer()
    {
    }

    /**
     * Play the task completion sound. Non-blocking — runs on the audio system thread.
     * Skips if a sound is already playing to avoid overlap.
     */
    public void playTaskComplete()
    {
        if (playing)
        {
            return;
        }

        try
        {
            InputStream is = getClass().getResourceAsStream(SOUND_FILE);
            if (is == null)
            {
                log.warn("Gielinor Dailies: Sound file not found: {}", SOUND_FILE);
                return;
            }

            BufferedInputStream bis = new BufferedInputStream(is);
            AudioInputStream audioStream = AudioSystem.getAudioInputStream(bis);
            Clip clip = AudioSystem.getClip();

            clip.addLineListener(event -> {
                if (event.getType() == LineEvent.Type.STOP)
                {
                    clip.close();
                    playing = false;
                }
            });

            playing = true;
            clip.open(audioStream);
            clip.start();
        }
        catch (Exception e)
        {
            log.warn("Gielinor Dailies: Error playing sound", e);
            playing = false;
        }
    }

    public void shutdown()
    {
        // No executor to shut down — Clip handles its own thread
    }
}
