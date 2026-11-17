package de.grit.teaser;

import android.app.job.JobParameters;
import android.app.job.JobService;

public class TeaserJob extends JobService {
    @Override
    public boolean onStartJob(JobParameters params) {
        new Thread(() -> {
            try { Teasers.check(this, true); } catch (Exception ignored) { }
            jobFinished(params, false);
        }).start();
        return true;
    }

    @Override
    public boolean onStopJob(JobParameters params) { return true; }
}
