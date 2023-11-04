package com.azazo1.dormtransferfile_onandroid;

import android.content.Intent;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

import com.azazo1.dormtransferfile_onandroid.receiver.ReceiverActivity;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        findViewById(R.id.sender_intent_button).setOnClickListener(v -> startSender());
        findViewById(R.id.receiver_intent_button).setOnClickListener(v -> startReceiver());
    }

    public void startReceiver() {
        Intent intent = new Intent(this, ReceiverActivity.class);
        startActivity(intent);
    }

    public void startSender() {
        Intent intent = new Intent(this, SenderActivity.class);
        startActivity(intent);
    }

}