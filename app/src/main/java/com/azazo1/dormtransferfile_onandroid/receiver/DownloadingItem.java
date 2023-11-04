package com.azazo1.dormtransferfile_onandroid.receiver;

import com.azazo1.dormtransferfile.FileTransferClient;
import com.azazo1.dormtransferfile.MsgType;

import java.io.File;
import java.util.Vector;
import java.util.concurrent.atomic.AtomicLong;

public class DownloadingItem {
    private volatile long fileSize;
    private volatile String filename;
    private volatile File storeFile;
    private volatile State state;
    private final AtomicLong now = new AtomicLong(); // 当前下载的比特数
    private static final int snapshotNumber = 100; // progressSnapshot 中存放的元素个数，越大计算结果越像平均速度
    /**
     * 记录下载的比特数和对应的时间戳，用于记录下载速度
     */
    private final Vector<MsgType.Pair<Long, Long>> progressSnapshot = new Vector<>();

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
        if (progressSnapshot.isEmpty()) {
            return FileTransferClient.formatFileSize(0) + "/s";
        }
        var firstPair = progressSnapshot.firstElement();
        var lastPair = progressSnapshot.lastElement();
        long deltaTime = lastPair.second - firstPair.second;
        long deltaBytes = lastPair.first - firstPair.first;
        return FileTransferClient.formatFileSize((int) (1.0 * deltaBytes / deltaTime * 1000)) + "/s";
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
        progressSnapshot.add(new MsgType.Pair<>(now, System.currentTimeMillis()));
        while (progressSnapshot.size() > snapshotNumber) {
            progressSnapshot.remove(0);
        }
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
