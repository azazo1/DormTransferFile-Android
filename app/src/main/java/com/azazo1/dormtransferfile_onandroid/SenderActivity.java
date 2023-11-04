package com.azazo1.dormtransferfile_onandroid;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.documentfile.provider.DocumentFile;

import com.azazo1.dormtransferfile.FileTransferClient;
import com.azazo1.dormtransferfile.FileTransferSenderServer;
import com.azazo1.dormtransferfile.MsgType;
import com.azazo1.dormtransferfile.SCMConnector;
import com.azazo1.dormtransferfile.SpeedCalculator;

import org.jetbrains.annotations.Contract;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

public class SenderActivity extends AppCompatActivity {
    private static final int DEFAULT_BUFFER_SIZE = 8192;
    private SCMConnector connector;
    private TextView titleText;
    private TextView stateText;
    private ProgressBar progressBar;
    private FileTransferSenderServer server;
    private ActivityResultLauncher<Intent> launcher;
    public static final int PORT = 18546;
    public Handler handler;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sender);
        handler = new Handler();
        titleText = findViewById(R.id.send_title_text);
        stateText = findViewById(R.id.send_state_text);
        progressBar = findViewById(R.id.send_progress_bar);

        launcher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), this::onActivityResult);

        // 接受"分享"
        Intent intent = getIntent();
        String action = intent.getAction();
        String type = intent.getType();

        Tools.postInThread(() -> {
            try {
                connector = new SCMConnector(1000);
                handler.post(() -> {
                    Toast.makeText(this, R.string.connected_to_scm, Toast.LENGTH_SHORT).show();

                    if (action.equals(Intent.ACTION_SEND) && type != null) { // 通过分享打开的此活动
                        try {
                            Uri uri = intent.getParcelableExtra(Intent.EXTRA_STREAM);
                            DocumentFile file = DocumentFile.fromSingleUri(this, uri);
                            if (file == null) {
                                throw new FileNotFoundException(uri.toString());
                            }
                            String filename = file.getName();
                            try (InputStream is = getContentResolver().openInputStream(uri)) {
                                prepareForSending(filename, is);
                            }
                        } catch (IOException e) {
                            Tools.showErrorDialog(e, this, this::finish);
                        }
                    } else {
                        chooseFile(); // 不是通过分享打开的此活动
                    }
                });
            } catch (IOException e) {
                Tools.showErrorDialogInHandler(e, this, this::finish, handler);
            }
        });
    }

    public void chooseFile() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("*/*");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        launcher.launch(Intent.createChooser(intent, getString(R.string.select_a_file)));
    }

    protected void onActivityResult(@NonNull ActivityResult result) {
        Intent data = result.getData();
        if (result.getResultCode() == RESULT_OK) {
            if (data != null) {
                Uri uri = data.getData();
                try {
                    DocumentFile file = DocumentFile.fromSingleUri(this, uri);
                    if (file == null) {
                        throw new FileNotFoundException(uri.toString());
                    }
                    InputStream is = getContentResolver().openInputStream(uri);
                    prepareForSending(file.getName(), is);
                } catch (IOException e) {
                    Tools.showErrorDialog(e, this, this::finish);
                }
                return;
            }
        }
        Tools.showErrorDialog(new IllegalArgumentException(getString(R.string.please_choose_a_file)), this, this::finish);
    }

    public void prepareForSending(String filename, InputStream is) throws IOException {
        titleText.setText(String.format(getString(R.string.send_title_format), filename));
        stateText.setText(R.string.copying_file);
        MsgType.Pair<File, Long> copied = getCopiedFile(filename, is);
        File copiedFile = copied.first;
        long fileSize = copied.second;
        titleText.setText(String.format(getString(R.string.send_title_with_size_format), filename, FileTransferClient.formatFileSize(fileSize)));
        stateText.setText(R.string.waiting_for_registration_result);
        progressBar.setMax(100);
        progressBar.setMin(0);

        Tools.postInThread(() -> { // 注册发送者
            try {
                connector.registerSender(filename, PORT);
            } catch (IOException e) {
                Tools.showErrorDialogInHandler(e, this, this::finish, handler);
            }
        });
        Tools.postInNewThread(() -> { // 发送文件
            try {
                connector.readResponseCode();
                connector.readMsgTypeCode();
                var connCode = MsgType.RegisterSender.parseMsg(connector.in);
                handler.post(() -> stateText.setText(String.format(getString(R.string.waiting_for_connection_format), connCode)));

                server = new FileTransferSenderServer(PORT, copiedFile);
                SpeedCalculator speedCalculator = new SpeedCalculator();
                server.launchSending((now, total) -> handler.post(() -> {
                    if (now == 0) { // 接收到客户端
                        connector.close(); // 清除服务器上的注册信息
                    }
                    speedCalculator.update(now, System.currentTimeMillis());
                    stateText.setText(String.format(getString(R.string.state_sending_format),
                            FileTransferClient.formatFileSize(total - now),
                            FileTransferClient.formatFileSize(speedCalculator.getSpeed()) + "/s"));
                    progressBar.setProgress((int) (100 * 1.0 * now / total));
                }));
                handler.post(() -> stateText.setText(R.string.send_over));
            } catch (IOException e) {
                if (!isDestroyed()) { // 关闭后不显示错误
                    Tools.showErrorDialogInHandler(e, this, this::finish, handler);
                }
            }
        }, "Sending Thread");
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (connector != null) {
            connector.close();
        }
        if (server != null) {
            server.close();
        }
        var ignore = getCacheDir().listFiles(pathname -> { // 清除缓存
            System.out.println(pathname.toString());
            boolean delete = pathname.delete();
            return false;
        });
    }

    /**
     * 复制文件到 cache 文件，返回 cache 中的文件和文件大小
     */
    @NonNull
    @Contract("_, _ -> new")
    private MsgType.Pair<File, Long> getCopiedFile(String filename, @NonNull InputStream is) throws IOException {
        File targetFile = getCacheDir().toPath().resolve(filename).toFile();
        long copiedBytes = 0;
        try (FileOutputStream out = new FileOutputStream(targetFile)) {
            byte[] buffer = new byte[DEFAULT_BUFFER_SIZE];
            int read;
            while ((read = is.read(buffer, 0, DEFAULT_BUFFER_SIZE)) >= 0) {
                out.write(buffer, 0, read);
                copiedBytes += read;
            }
        }
        return new MsgType.Pair<>(targetFile, copiedBytes);
    }
}