package com.azazo1.dormtransferfile_onandroid.receiver;

import com.azazo1.dormtransferfile_onandroid.Tools;

public class BackgroundHandler {
    private final ReceiverActivity activity;
    private final Thread backgroundThread;

    public BackgroundHandler(ReceiverActivity activity) {
        this.activity = activity;
        backgroundThread = new Thread("Receiver_Background") {
            @Override
            public void run() {
                BackgroundHandler.this.run();
            }
        };
        backgroundThread.setDaemon(true);
    }

    private void run() {
        while (!activity.isDestroyed()) {
            try {
                activity.handleMsg(); // 阻塞的
            } catch (Exception e) {
                Tools.showErrorDialogInHandler(e, activity, activity::finish, activity.handler);
            }
        }
    }

    public void start() {
        backgroundThread.start();
    }
}
