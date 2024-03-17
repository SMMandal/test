package com.tcupiot.beans;

import com.tcupiot.exception.ValidationException;
/**
 * @author Sumanta Ghosh
 */
public class FileInfo {

    private String filePath;
    private String fileName;
    private Long fileSize;
    private String metadata;
    private String savepoint;
    private String directory;
    private String comment;

    public String getFilePath() {
        return filePath;
    }

    public String getMetadata() {
        return metadata;
    }

    public String getSavepoint() {
        return savepoint;
    }

    public String getDirectory() {
        return directory;
    }

    public String getComment() {
        return comment;
    }

    public void setFilePath(String filePath) {
        this.filePath = filePath;
    }

    public void setMetadata(String metadata) {
        this.metadata = metadata;
    }

    public void setSavepoint(String savepoint) {
        this.savepoint = savepoint;
    }

    public void setDirectory(String directory) {
        this.directory = directory;
    }

    public void setComment(String comment) {
        this.comment = comment;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public Long getFileSize() {
        return fileSize;
    }

    public void setFileSize(Long fileSize) {
        this.fileSize = fileSize;
    }

    /**
     * Validate the user input regarding the file parameters.
     * The regex used for validation need to be same as them being used in DLS server code
     * @throws ValidationException
     */
    public void validate() throws ValidationException {

//        if(null != fileName && !fileName.matches("\\S+")) throw new ValidationException("File name can not have white spaces");
        if(null != fileName && fileName.length() > 255) throw new ValidationException("File name can not be lengthier than 255 characters");
        if(null != savepoint && !savepoint.matches("^((/)?([\\w-.:]*[a-zA-Z][\\w-.:]*)+){1,10}$")) throw new ValidationException("Invalid savepoint name format");
        if(null != savepoint && savepoint.length() > 50) throw new ValidationException("Savepoint name can not be lengthier than 50 characters");
//        if(null != directory && !directory.matches("^(/([\\w-]+))+$")) throw new ValidationException("Invalid directory name format");
        if(null != directory && directory.length() > 255) throw new ValidationException("Directory name can not be lengthier than 255 characters");
        if(null != comment && comment.length() > 255) throw new ValidationException("Comment can not be lengthier than 255 characters");

    }

}

