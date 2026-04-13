package com.gielinordailies;

import lombok.extern.slf4j.Slf4j;
import net.runelite.client.audio.AudioPlayer;

import javax.inject.Inject;
import javax.inject.Singleton;

@Slf4j
@Singleton
public class SoundPlayer
{
    @Inject
    private AudioPlayer audioPlayer;

    @Inject
    public SoundPlayer()
    {
    }

    /**
     * Play the task completion sound using RuneLite's AudioPlayer.
     */
    public void playTaskComplete()
    {
        try
        {
            audioPlayer.play(getClass(), "task_complete.wav", 0f);
        }
        catch (Exception e)
        {
            log.warn("Gielinor Dailies: Error playing sound", e);
        }
    }

    public void shutdown()
    {
        // AudioPlayer manages its own lifecycle
    }
}
