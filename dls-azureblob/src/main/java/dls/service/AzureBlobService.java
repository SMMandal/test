package dls.service;

import dls.repo.FileRepo;
import dls.service.AzureRepo;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import reactor.core.publisher.Mono;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.util.Objects;

@Slf4j
@Service
public class AzureBlobService implements IFileManagementService, IBlobService {
	@Autowired private FileRepo fileRepo;
	@Value("${local.fs.failsafe.path}") 
	private String failsafeFilePath;

	@Value("${dls.enable.azureblob}")
	private Boolean enableBlob;
	
	@Autowired
	AzureRepo azureRepo;

	@Override
	public void write(@NonNull MultipartFile multipart, final String fsPath, final Long fileId) {
		Mono.fromSupplier(() -> {	
			try {
				boolean success=false;
				String userRoot = fsPath.substring(0,fsPath.lastIndexOf('/'));
				String baseDir = failsafeFilePath.concat("/" + userRoot + "/" );
				File file = new File(baseDir.concat("/").concat(Objects.requireNonNull(multipart.getOriginalFilename())));
				File fileDir = new File(baseDir);
				
				if (!file.exists()) {					
					if(!fileDir.mkdirs()) {
						log.error("Error in creating local directory");						
					} 
			    }		
				
				//Copy the file to local directory
				multipart.transferTo(file);

				if(enableBlob) {
					//Copy the local file to Azure blob
				    success = azureRepo.uploadBlob(userRoot, multipart);
					
					if (!fileDir.delete()) {
						log.error("Error in deleting local directory");
					}
				}
				
				return success;
			} catch (IOException e) {
				log.error("Fatal exception in storing uploaded file {} in DLS local. {}",fsPath, e.getMessage());
				return false;
			}	
		})
		.subscribe(status -> fileRepo.findById(fileId).ifPresent(o -> {
			if(!enableBlob) {
				o.setStorage("L");
				o.setUploaded(true);
			} else {
				o.setStorage("B");
				o.setUploaded(status);
			}
			fileRepo.saveAndFlush(o);
		}));
	}

	@Override
	public String getBlobURL(String blobPath) {
		try {
			if(enableBlob) {
				return(azureRepo.downloadBlobByURL(blobPath));
			}
		} catch (Exception ex) {
			log.error(ex.toString());
		}
		return null;
	}
	
	@Override
	public void writeBundle(@NonNull File bundleFile, String fsPath, final Long fileId) {
		//TODO
	}
	
	@Override
	public void append(@NonNull String fsPath, @NonNull MultipartFile multipartFile) throws IOException {
		try {
			if(enableBlob) {
				azureRepo.appendBlob(fsPath, multipartFile);
			} else {
				log.info("Blob service not enabled");
			}
		} catch (Exception ex) {
			log.error(ex.toString());
		}
	}

	@Override
	public void delete(boolean recursive, String fsPath) {
		try {
			if(enableBlob) {
				azureRepo.deleteBlob(fsPath);
			} else {
				log.info("Blob service not enabled");
			}
		} catch (Exception ex) {
			log.error(ex.toString());
		}
	}

	@Override
	public boolean archive(@NonNull String fsPath, String createdOn) throws IOException {
		try {
			if(enableBlob) {
				Boolean success = azureRepo.copyBlob(fsPath, fsPath.concat("_" + createdOn));
				if(success) {
					azureRepo.deleteBlob(fsPath);
					return true;
				}
			} else {
				log.info("Blob service not enabled");
				return false;
			}
		} catch (Exception ex) {
			log.error(ex.toString());
		}
		return false;
	}

	@Override
	public void renameDirectory(@NonNull String srcDir, @NonNull String destDir) throws IOException {
		//TODO
    }
}
