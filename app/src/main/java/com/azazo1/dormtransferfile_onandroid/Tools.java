package com.azazo1.dormtransferfile_onandroid;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Handler;
import android.widget.TextView;

import androidx.annotation.NonNull;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Vector;

public final class Tools {
    private static final Vector<Runnable> posts = new Vector<>();
    private static final Thread postsRunner = new Thread("Posts Runner") {
        @Override
        public void run() {
            try {
                while (!Thread.interrupted()) {
                    if (!posts.isEmpty()) {
                        Runnable runnable = posts.remove(0);
                        runnable.run();
                    }
                    Thread.sleep(50);
                }
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }

        {
            setDaemon(true);
            start();
        }
    };

    public static void stopPostsRunner() {
        postsRunner.interrupt();
    }

    public static void showErrorDialog(@NonNull Exception e, Context context, Runnable callOnConfirm) {
        e.printStackTrace();
        TextView text = new TextView(context);
        StringWriter writer = new StringWriter();
        e.printStackTrace(new PrintWriter(writer));
        text.setText(writer.toString());
        text.setPadding(10, 10, 10, 10);
        new AlertDialog.Builder(context).setTitle(e.getMessage()).setView(text).setPositiveButton(R.string.confirm, (DialogInterface.OnClickListener) (dialog, which) -> {
            if (callOnConfirm != null) {
                callOnConfirm.run();
            }
        }).show();
    }

    public static void showErrorDialogInHandler(@NonNull Exception e, Context context, Runnable callOnConfirm, @NonNull Handler handler) {
        handler.post(() -> showErrorDialog(e, context, callOnConfirm));
    }

    /**
     * 将任务放在一个子线程中执行，注意不要提交超长时间的任务
     */
    public static void postInThread(Runnable runnable) {
        posts.add(runnable);
    }

    /**
     * 将任务放在一个新建的专用的子线程中执行，可以提交超长时间的任务，调用此方法后无需调用返回值的start方法
     */
    @NonNull
    public static Thread postInNewThread(Runnable runnable, String threadName) {
        Thread thread = new Thread(threadName) {
            @Override
            public void run() {
                runnable.run();
            }
        };
        thread.setDaemon(true);
        thread.start();
        return thread;
    }
}
