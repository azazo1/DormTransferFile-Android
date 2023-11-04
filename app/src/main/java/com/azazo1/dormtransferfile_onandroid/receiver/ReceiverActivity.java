package com.azazo1.dormtransferfile_onandroid.receiver;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.text.Editable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;

import com.azazo1.dormtransferfile.FileTransferClient;
import com.azazo1.dormtransferfile.MsgType;
import com.azazo1.dormtransferfile.SCMConnector;
import com.azazo1.dormtransferfile_onandroid.R;
import com.azazo1.dormtransferfile_onandroid.Tools;
import com.azazo1.dormtransferfile_onandroid.UnexpectedValue;

import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.HashMap;
import java.util.Locale;
import java.util.Vector;
import java.util.concurrent.atomic.AtomicBoolean;

public class ReceiverActivity extends AppCompatActivity {
    public Handler handler;
    private volatile SCMConnector connector;
    private EditText connCodeInput;
    private ListView sendersListView;
    private SendersListAdapter sendersListAdapter;
    private ListView downloadingListView;
    private DownloadingListAdapter downloadingListAdapter;
    private BackgroundHandler backgroundMsgHandler;
    /**
     * 用于临时储存查询IP请求, 储存值为 (消息序号码:要查询的ConnectionCode), 用于实现对服务器回复的正确响应
     */
    private final HashMap<Integer, Integer> queryPair = new HashMap<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_receiver);
        setTitle(R.string.receiver_activity_title);
        handler = new Handler();
        Button refreshButton = findViewById(R.id.sender_list_refresh_button);
        refreshButton.setClickable(false);
        refreshButton.setOnClickListener(v -> fetchAvailableSenders());

        sendersListView = findViewById(R.id.senders_list_view);
        sendersListAdapter = new SendersListAdapter();
        sendersListView.setAdapter(sendersListAdapter);

        connCodeInput = findViewById(R.id.connection_code_input);
        Button confirmCodeButton = findViewById(R.id.confirm_code_button);
        confirmCodeButton.setClickable(false);
        confirmCodeButton.setOnClickListener(v -> {
            Editable text = connCodeInput.getText();
            if (text.length() != 0) {
                querySenderServerAddress(Integer.parseInt("" + text));
            }
        });

        downloadingListView = findViewById(R.id.downloading_list_view);
        downloadingListAdapter = new DownloadingListAdapter();
        downloadingListView.setAdapter(downloadingListAdapter);
        Tools.postInThread(() -> {
            try {
                connector = new SCMConnector(200);
                backgroundMsgHandler = new BackgroundHandler(this); // 等待连接建立后才执行
                backgroundMsgHandler.start();

                handler.post(() -> {
                    refreshButton.setClickable(true);
                    confirmCodeButton.setClickable(true);
                    Toast.makeText(this, R.string.connected_to_scm, Toast.LENGTH_SHORT).show();
                });
            } catch (Exception e) {
                Tools.showErrorDialogInHandler(e, this, this::finish, handler);
            }
        });
    }

    /**
     * 超长时间任务，不放在 Tools.posts 中，在独立一个线程中执行
     */
    private void connectToSender(MsgType.Pair<String, Integer> address) {
        DownloadingItem item = new DownloadingItem();
        item.setState(DownloadingItem.State.CONNECTING);
        handler.post(() -> {
            downloadingListAdapter.add(item);
        });
        Tools.postInNewThread(() -> {
            try (FileTransferClient client = new FileTransferClient(new InetSocketAddress(address.first, address.second))) {

                long fileSize = client.receiveFileSize();
                String filename = client.receiveFilename();
                File fileToStore = getCacheDir().toPath().resolve(filename).toFile();

                item.setFileSize(fileSize);
                item.setFilename(filename);
                item.setStoreFile(fileToStore);
                item.setState(DownloadingItem.State.DOWNLOADING);

                client.receiveFileData(fileToStore, fileSize, (now, total) -> {
                    item.setProgress(now);
                });

                item.setState(DownloadingItem.State.OVER);
            } catch (IOException e) {
                item.setState(DownloadingItem.State.ERROR);
                Tools.showErrorDialogInHandler(e, this, null, handler);
            }
        }, "DownloadingThread");
    }

    private void fetchAvailableSenders() {
        Tools.postInThread(() -> {
            try {
                connector.fetchAvailableSenders();
            } catch (IOException e) {
                Tools.showErrorDialogInHandler(e, this, this::reconnectToSCMServer, handler);
            }
        });
    }

    private void querySenderServerAddress(int connCode) {
        Tools.postInThread(() -> {
            try {
                int seq = connector.querySenderServerAddress(connCode);
                queryPair.put(seq, connCode);
            } catch (IOException e) {
                Tools.showErrorDialogInHandler(e, this, this::reconnectToSCMServer, handler);
            }
        });
    }

    private void reconnectToSCMServer() {
        Tools.postInThread(() -> {
            if (connector != null) {
                connector.close();
            }
            try {
                connector = new SCMConnector();
            } catch (IOException e) {
                Tools.showErrorDialogInHandler(e, this, this::finish, handler); // 再次重连失败则关闭
            }
        });
    }

    public void handleMsg() { // 此方法由 BackgroundHandler 在子线程内调用
        try {
            int responseCode = connector.readResponseCode();
            int msgTypeCode = connector.readMsgTypeCode();
            switch (msgTypeCode) {
                case MsgType.FETCH_AVAILABLE_SENDER -> {
                    var rst = MsgType.FetchAvailableSenders.parseMsg(connector.in);
                    handler.post(() -> refreshListView(rst));
                }
                case MsgType.QUERY_SENDER_SERVER_ADDRESS -> {
                    var rst = MsgType.QuerySenderServerAddress.parseMsg(connector.in);
                    if (queryPair.containsKey(responseCode)) {
                        queryPair.remove(responseCode);
                        if (rst == null) {
                            Tools.showErrorDialogInHandler(new UnexpectedValue("This sender is not available"), this, null, handler);
                            break;
                        }
                        connectToSender(rst);
                    } else {
                        Tools.showErrorDialogInHandler(new UnexpectedValue("Unexpected msg sequence code: " + responseCode), this, null, handler);
                    }
                }
            }
        } catch (IOException e) {
            if (!isDestroyed()) { // 若是 activity 结束了之后导致的报错则忽略
                Tools.showErrorDialogInHandler(e, this, this::reconnectToSCMServer, handler);
            }
        }
    }

    private void refreshListView(@NonNull HashMap<Integer, String> sendersList) {
        sendersListAdapter.clear();
        for (int connCode : sendersList.keySet()) {
            String filename = sendersList.get(connCode);
            sendersListAdapter.add(new MsgType.Pair<>(connCode, filename));
        }
        sendersListAdapter.notifyDataSetChanged();
        if (sendersList.isEmpty()) {
            Toast.makeText(this, R.string.no_available_senders, Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        queryPair.clear();
        if (connector != null) {
            connector.close();
        }
        var ignore = getCacheDir().listFiles(pathname -> { // 清除缓存
            System.out.println(pathname.toString());
            boolean delete = pathname.delete();
            return false;
        });
    }

    private class SendersListAdapter extends ArrayAdapter<MsgType.Pair<Integer, String>> {
        public final Vector<MsgType.Pair<Integer, String>> senders = new Vector<>();

        public SendersListAdapter() {
            super(ReceiverActivity.this, R.layout.item_sender);
        }

        @Override
        public void add(@Nullable MsgType.Pair<Integer, String> object) {
            senders.add(object);
            super.add(object);
        }

        @Override
        public void clear() {
            super.clear();
            senders.clear();
        }

        @NonNull
        @Override
        public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
            View view;
            MsgType.Pair<TextView, TextView> viewHolder; // 连接码Text和文件名Text
            if (convertView == null) {
                view = LayoutInflater.from(ReceiverActivity.this).inflate(R.layout.item_sender, parent, false);
                viewHolder = new MsgType.Pair<>(view.findViewById(R.id.connection_code_text), view.findViewById(R.id.filename_text));
                view.setTag(viewHolder);
            } else {
                view = convertView;
                //noinspection unchecked
                viewHolder = (MsgType.Pair<TextView, TextView>) convertView.getTag();
            }
            MsgType.Pair<Integer, String> item = senders.get(position);

            TextView downloadButton = view.findViewById(R.id.click_to_download_button);
            downloadButton.setClickable(true);
            downloadButton.setOnClickListener(v -> querySenderServerAddress(item.first));

            viewHolder.first.setText(String.format(Locale.getDefault(), getString(R.string.connection_code_format), item.first));
            viewHolder.second.setText(String.format(Locale.getDefault(), getString(R.string.filename_format), item.second));
            return view;
        }
    }

    private class DownloadingListAdapter extends ArrayAdapter<DownloadingItem> {
        private final Vector<DownloadingItem> downloadingItems = new Vector<>();

        public DownloadingListAdapter() {
            super(ReceiverActivity.this, R.layout.item_downloading);
        }

        @Override
        public void add(@Nullable DownloadingItem object) {
            downloadingItems.add(object);
            super.add(object);
        }

        @Override
        public void clear() {
            downloadingItems.clear();
            super.clear();
        }

        @SuppressWarnings("ConstantConditions")
        @NonNull
        @Override
        public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
            View view;
            HashMap<Integer, View> viewHolder;
            DownloadingItem item = downloadingItems.get(position);
            if (convertView == null) {
                view = LayoutInflater.from(ReceiverActivity.this).inflate(R.layout.item_downloading, parent, false);
                viewHolder = new HashMap<>();
                TextView filenameText = view.findViewById(R.id.filename_text);
                viewHolder.put(R.id.filename_text, filenameText);
                TextView remainSizeText = view.findViewById(R.id.remain_size_text);
                viewHolder.put(R.id.remain_size_text, remainSizeText);

                TextView speedAndShareText = view.findViewById(R.id.speed_and_share_text);
                viewHolder.put(R.id.speed_and_share_text, speedAndShareText);
                speedAndShareText.setBackgroundColor(getResources().getColor(R.color.clickable_text_bg, getTheme()));

                ProgressBar progressBar = view.findViewById(R.id.download_progress_bar);
                progressBar.setMax(100);
                progressBar.setMin(0);
                viewHolder.put(R.id.download_progress_bar, progressBar);

                // 持续更新状态, 超长时间任务
                Tools.postInNewThread(() -> {
                    AtomicBoolean running = new AtomicBoolean(true);
                    while (running.get()) {
                        handler.post(() -> {
                            switch (item.getState()) {
                                case CONNECTING ->
                                        speedAndShareText.setText(R.string.connecting_text);
                                case DOWNLOADING -> {
                                    progressBar.setProgress((int) (item.getProgress() * 100));
                                    remainSizeText.setText(FileTransferClient.formatFileSize(item.getRemainSize()));
                                    speedAndShareText.setText(String.format("[%s]", item.getSpeedText()));
                                }
                                case OVER -> {
                                    filenameText.setText(item.getFilename());
                                    speedAndShareText.setText(R.string.share);
                                    speedAndShareText.setOnClickListener((v) -> share(item.getStoreFile()));
                                    remainSizeText.setText(FileTransferClient.formatFileSize(0));
                                    progressBar.setProgress(100);
                                    running.set(false);
                                }
                                case ERROR -> {
                                    speedAndShareText.setText(R.string.error_clickable_text);
                                    running.set(false);
                                }
                            }
                        });
                        try {
                            Thread.sleep(50);
                        } catch (InterruptedException e) {
                            throw new RuntimeException(e);
                        }
                    }
                }, "Progress Updater:" + position);

                view.setTag(viewHolder);
            } else {
                view = convertView;
                //noinspection unchecked
                viewHolder = (HashMap<Integer, View>) convertView.getTag();
            }
            ((TextView) viewHolder.get(R.id.filename_text)).setText(item.getFilename());
            return view;
        }
    }

    private void share(File file) {
        // 分享文件到其他应用
        Intent intent = new Intent(Intent.ACTION_SEND);
        Uri contentUri = FileProvider.getUriForFile(this, "com.azazo1.dormtransferfile_onandroid.fileprovider", file);
        intent.putExtra(Intent.EXTRA_STREAM, contentUri);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        intent.setType("*/*");
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivity(Intent.createChooser(intent, "分享文件"));
    }
}