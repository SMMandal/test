package dls.bean;

import lombok.Data;

import java.sql.Timestamp;


@Data
public class DlsFileStatus  {
    private Timestamp startedAt;
    private Timestamp pausedAt;
    private Timestamp resumedAt;
    private String status;
    private Long bytesTransferred;

    public enum UploadStatus {
        UPLOADING,
        UPLOADED,
        PAUSED,
        RESUMED
    }
}
