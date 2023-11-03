package com.azazo1.dormtransferfile_onandroid;

import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

import com.azazo1.dormtransferfile.MsgType;
import com.azazo1.dormtransferfile.SCMConnector;

import java.io.IOException;
import java.util.HashMap;

public class ReceiverActivity extends AppCompatActivity {
    private SCMConnector connector;
    /**
     * 用于临时储存查询IP请求, 储存值为 (消息序号码:要查询的ConnectionCode), 用于实现对服务器回复的正确响应
     */
    private final HashMap<Integer, Integer> queryPair = new HashMap<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_receiver);
        findViewById(R.id.sender_list_refresh_button).setOnClickListener(v -> fetchAvailableSenders());
        try {
            connector = new SCMConnector();

        } catch (IOException e) {
            Tools.showErrorDialog(e, this, this::finish);
        }
    }

    private int fetchAvailableSenders() {
        try {
            return connector.fetchAvailableSenders();
        } catch (IOException e) {
            Tools.showErrorDialog(e, this, this::reconnect);
        }
        return -1;
    }

    private int querySenderServerAddress(int connCode) {
        try {
            int seq = connector.querySenderServerAddress(connCode);
            queryPair.put(seq, connCode);
        } catch (IOException e) {
            Tools.showErrorDialog(e, this, this::reconnect);
        }
        return -1;
    }

    private void reconnect() {
        if (connector != null) {
            connector.close();
        }
        try {
            connector = new SCMConnector();
        } catch (IOException e) {
            Tools.showErrorDialog(e, this, this::finish); // 再次重连失败则关闭
        }
    }

    private void handleMsg() {
        try {
            int responseCode = connector.readResponseCode();
            int msgTypeCode = connector.readMsgTypeCode();
            switch (msgTypeCode) {
                case MsgType.FETCH_AVAILABLE_SENDER -> {
                    var rst = MsgType.FetchAvailableSenders.parseMsg(connector.in);
                    refreshListView(rst);
                }
                case MsgType.QUERY_SENDER_SERVER_ADDRESS -> {
                    var rst = MsgType.QuerySenderServerAddress.parseMsg(connector.in);
                    if (queryPair.containsKey(responseCode)) {
                        // todo 开始连接文件传输者
                    } else {
                        Tools.showErrorDialog(new UnexpectedValue("Unexpected msg sequence code: " + responseCode), this, null);
                    }
                }
            }
        } catch (IOException e) {
            Tools.showErrorDialog(e, this, this::reconnect);
        }
    }

    private void refreshListView(HashMap<Integer, String> senderList) {
        // todo 刷新可用文件传输者列表
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        queryPair.clear();
        if (connector != null) {
            connector.close();
        }
    }
}