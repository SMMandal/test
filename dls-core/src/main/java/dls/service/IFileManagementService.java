package dls.service;

import lombok.NonNull;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;

/**
 * Interface for abstraction of file operations in DLS
 * @author Sumanta Ghosh
 */
public interface IFileManagementService {
	/**
	 * Write a file uploaded by user to disk.
	 * @param multipart
	 * @param fsPath
	 * @param fileId
	 */
	void write(@NonNull MultipartFile multipart, final String fsPath, final Long fileId);
	void writeBundle(@NonNull File bundleFile, String fsPath, final Long fileId);
	void append(@NonNull String fsPath, @NonNull MultipartFile multipartFile) throws IOException;
	void delete(boolean recursive, String fsPath);
	boolean archive(@NonNull String fsPath, String createdOn) throws IOException;
	void renameDirectory(@NonNull String srcDir, @NonNull String destDir) throws IOException;
}
