package com.azazo1.dormtransferfile_onandroid;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.widget.TextView;

import androidx.annotation.NonNull;

import java.io.PrintWriter;
import java.io.StringWriter;

public final class Tools {

    public static void showErrorDialog(@NonNull Exception e, Context context, Runnable callOnConfirm) {
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
}
