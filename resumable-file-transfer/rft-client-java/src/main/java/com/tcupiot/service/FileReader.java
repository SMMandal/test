package com.tcupiot.service;

import com.tcupiot.beans.FileInfo;
import com.tcupiot.beans.UploadConfig;
import io.rsocket.Payload;
import io.rsocket.util.DefaultPayload;
import reactor.core.publisher.Flux;

import java.io.Console;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Read the file content in client and stream it to server.
 *
 * @author Sumanta Ghosh
 */
public class FileReader {

    private static final Console console = System.console();

    public static void init(FileInfo fileInfo) throws IOException {

        Path p = Paths.get(fileInfo.getFilePath());
//        SeekableByteChannel channel = Files.newByteChannel(p, StandardOpenOption.READ);
        if(fileInfo.getFileName() == null || fileInfo.getFileName().isEmpty()) {
            fileInfo.setFileName(p.getFileName().toString());
        }
        fileInfo.setFileSize(Files.size(p));
    }

    public static Flux<Payload> read(UploadConfig config, FileInfo fileInfo, Payload skipBlocks) {

        int partNo = Integer.parseInt(skipBlocks.getDataUtf8());
        AtomicLong splitId = new AtomicLong(partNo);
        Path p = Paths.get(fileInfo.getFilePath());
        try {
            SeekableByteChannel channel = Files.newByteChannel(p, StandardOpenOption.READ);
//            fileInfo.setFileName(p.getFileName().toString());
            if(fileInfo.getFileName() == null) {
                fileInfo.setFileName(p.getFileName().toString());
            }
            long size = Files.size(p);
            fileInfo.setFileSize(size);
            String totalSize = formatSize(size);
            console.printf("%s", "\rChecking file status..");
            return Flux.generate(sink -> {
                try {
                    if (splitId.get() > 0) {
                        channel.position(splitId.get() * config.getBlockSize());
                    }
                    ByteBuffer dataBuffer = ByteBuffer.allocate(config.getBlockSize());
                    byte[] longVal = (splitId.incrementAndGet() + "").getBytes();
                    ByteBuffer metaBuffer = ByteBuffer.wrap(longVal);
                    int read = channel.read(dataBuffer);
                    dataBuffer.flip();
                    sink.next(DefaultPayload.create(dataBuffer, metaBuffer));
                    long uploadedSize = config.getBlockSize() * splitId.get();
                    String percentage =  (int)((float)(uploadedSize * 100 / size)) + "%";
                    console.printf("%s", "\rTransferred " + formatSize(uploadedSize) + "   of " + totalSize + "    : " +percentage + "   ");

                    if (read == -1 || read == 0) {
                        channel.close();
                        sink.complete();
                        console.printf("%s", "\r" + totalSize + " of " + totalSize + " transferred successfully     ");
                    }
                } catch (Throwable t) {
                    t.printStackTrace();
                    sink.error(t);
                }
            });
        } catch (Exception e) {
            return Flux.error(e);
        }
    }

    /**
     * Petty print file size
     *
     * @param v file size numeric value
     * @return petty printed file size string
     */
    public static String formatSize1(long v) {
        if (v < 1000) return v + " B";
        int z = (63 - Long.numberOfLeadingZeros(v)) / 10;
        return String.format("%.1f %sB", (double) v / (1L << (z * 10)), " KMGTPE".charAt(z));
    }

    public static String formatSize(long value) {
        String args[] = { "B", "KB", "MB", "GB", "TB" };
        StringBuilder sb = new StringBuilder();
        int i;
        if (value < 1024L) {
            sb.append(String.valueOf(value));
            i = 0;
        } else if (value < 1048576L) {
            sb.append(String.format("%.1f", value / 1024.0));
            i = 1;
        } else if (value < 1073741824L) {
            sb.append(String.format("%.2f", value / 1048576.0));
            i = 2;
        } else if (value < 1099511627776L) {
            sb.append(String.format("%.3f", value / 1073741824.0));
            i = 3;
        } else {
            sb.append(String.format("%.4f", value / 1099511627776.0));
            i = 4;
        }
        sb.append(' ');
        sb.append(args[i]);
        return sb.toString();
    }
}
