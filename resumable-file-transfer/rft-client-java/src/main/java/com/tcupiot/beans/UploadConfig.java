package com.tcupiot.beans;

import com.tcupiot.exception.ValidationException;

/**
 * @author Sumanta Ghosh
 */
public class UploadConfig {
    private String serverUrl;
    // default blocksize is 2MB
    private Integer blockSize = 2 * 1024 * 1024;
    private Boolean resume;
    private Boolean overwrite;

    public String getServerUrl() {
        return serverUrl;
    }

    public Integer getBlockSize() {
        return blockSize;
    }

    public Boolean getResume() {
        return resume;
    }

    public Boolean getOverwrite() {
        return overwrite;
    }

    public void setServerUrl(String serverUrl) {
        this.serverUrl = serverUrl;
    }

    public void setBlockSize(Integer blockSize) {
        this.blockSize = blockSize;
    }

    public void setResume(Boolean resume) {
        this.resume = resume;
    }

    public void setOverwrite(Boolean overwrite) {
        this.overwrite = overwrite;
    }

    public void validate() throws ValidationException {
        if(null != blockSize && blockSize > 16777215) {
            throw new ValidationException("Blocksize can not be more than 16777215");
        }
    }
}