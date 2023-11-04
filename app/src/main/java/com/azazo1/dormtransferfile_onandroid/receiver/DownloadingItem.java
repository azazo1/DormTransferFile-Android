package com.azazo1.dormtransferfile_onandroid.receiver;

import com.azazo1.dormtransferfile.FileTransferClient;
import com.azazo1.dormtransferfile.SpeedCalculator;

import java.io.File;
import java.util.concurrent.atomic.AtomicLong;

public class DownloadingItem {
    private volatile long fileSize;
    private volatile String filename;
    private volatile File storeFile;
    private volatile State state;
    private final AtomicLong now = new AtomicLong(); // 当前下载的比特数
    private final SpeedCalculator speedCalculator = new SpeedCalculator();

    public enum State {
        CONNECTING, DOWNLOADING, OVER, ERROR
    }

    /**
     * 获取文件名
     */
    public String getFilename() {
        return filename;
    }

    /**
     * 获取下载的内容所储存在的文件对象
     */
    public File getStoreFile() {
        return storeFile;
    }

    /**
     * 获取速度描述
     *
     * @return 类似 1KB/s 1MB/s 1GB/s
     */
    public String getSpeedText() {
        return FileTransferClient.formatFileSize(speedCalculator.getSpeed()) + "/s";
    }

    /**
     * 获取下载状态
     */
    public State getState() {
        return state;
    }

    /**
     * 0.0~1.0
     */
    public double getProgress() {
        return 1.0 * now.get() / fileSize;
    }

    public long getRemainSize() {
        return fileSize - now.get();
    }

    public void setProgress(long now) {
        this.now.set(now);
        speedCalculator.update(now, System.currentTimeMillis());
    }

    public void setState(State state) {
        this.state = state;
    }

    public void setFileSize(long fileSize) {
        this.fileSize = fileSize;
    }

    public void setFilename(String filename) {
        this.filename = filename;
    }

    public void setStoreFile(File storeFile) {
        this.storeFile = storeFile;
    }
}
