package dls.service;

import dls.repo.FileRepo;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.*;
import org.apache.hadoop.io.IOUtils;
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
public class HdfsService
		implements IFileManagementService {
	
	@Autowired private FileRepo fileRepo;		
	@Autowired private FileSystem hdfs;
	@Value("${local.fs.failsafe.path}") 
	private String failsafeFilePath;


	@Value("${dls.enable.hdfs}")
	private Boolean enableHDFS;

	@Autowired
	private Configuration configuration;

	@Override
	public void write(@NonNull MultipartFile multipart, final String fsPath, final Long fileId) {
		Mono.fromSupplier(() -> {		
						
			try {
//				String [] splits = fsPath.split("/",3);
//				String userRoot = Joiner.on('/').skipNulls().join(splits[0], splits[1]);
				String userRoot = fsPath.substring(0,fsPath.lastIndexOf('/'));
				String baseDir = failsafeFilePath.concat("/" + userRoot + "/" /*+ fileId*/);
				File file = new File(baseDir.concat("/").concat(Objects.requireNonNull(multipart.getOriginalFilename())));
				File fileDir = new File(baseDir);
				
				if (!file.exists()) {					
					if(!fileDir.mkdirs()) {
						log.error("Error in creating local directory");						
					} 
			    }		
				
				multipart.transferTo(file);

				if(enableHDFS) {
					hdfs.moveFromLocalFile(new org.apache.hadoop.fs.Path(file.getAbsolutePath()), new org.apache.hadoop.fs.Path(fsPath));

					log.info("Copied to hadoop {}/{}", hdfs.getWorkingDirectory(), fsPath);
					if (!fileDir.delete()) {
						log.error("Error in deleting local directory");
					}
				}
				return true;
				
			} catch (IOException e) {
				log.error("Fatal exception in storing uploaded file {} in DLS local. {}",fsPath, e.getMessage());
				return false;
			}
			
		})
		.subscribe(status -> fileRepo.findById(fileId).ifPresent(o -> {
			if(!enableHDFS) {
				o.setStorage("L");
				o.setUploaded(true);
			} else {
				o.setStorage("H");
				o.setUploaded(status);
			}
			fileRepo.saveAndFlush(o);
		}));
		
	}

	@Override
	public void writeBundle(@NonNull File bundleFile, String fsPath, final Long fileId) {
		Mono.fromSupplier(() -> {		
						
			try {
				hdfs.moveFromLocalFile(new org.apache.hadoop.fs.Path(bundleFile.getAbsolutePath()), new org.apache.hadoop.fs.Path(fsPath));
				log.info("Uploaded to HDFS");
				return true;
				
			} catch (IOException e) {
				log.error("Fatal exception in storing uploaded file {} in DLS local. {}",fsPath, e.getMessage());
				return false;
			}
			
		})
		.subscribe(status -> fileRepo.findById(fileId).ifPresent(o -> {
			o.setUploaded(status);
			fileRepo.saveAndFlush(o);
			log.info("Status of bundle file upload updated in database");
		}));
		
	}
	



	@Override
	public void append(@NonNull String fsPath, @NonNull MultipartFile multipartFile) throws IOException {
		org.apache.hadoop.fs.Path existingFile = new org.apache.hadoop.fs.Path(fsPath);
		org.apache.hadoop.fs.Path newFile = new org.apache.hadoop.fs.Path(fsPath.concat(".part"));

		try (FSDataOutputStream out = hdfs.create(newFile, true)) {
			ByteArrayInputStream in = new ByteArrayInputStream(multipartFile.getBytes());
			IOUtils.copyBytes(in, out, configuration, true);
		}
		hdfs.concat(existingFile, new org.apache.hadoop.fs.Path[]{newFile});
		hdfs.delete(newFile, false);
	}



	@Override
	public void delete(boolean recursive, String fsPath) {

		try {
			log.info("deleting {} from HDFS", fsPath);
			hdfs.delete(new org.apache.hadoop.fs.Path(fsPath), recursive);

		} catch(IOException ex) {
			log.error(ex.getMessage());
		}
	}



	@Override
	public boolean archive(@NonNull String fsPath, String createdOn) throws IOException {
		return hdfs.rename(new org.apache.hadoop.fs.Path(fsPath),
				new org.apache.hadoop.fs.Path(fsPath.concat("_" + createdOn)));
	}

	@Override
	public void renameDirectory(@NonNull String srcDir, @NonNull String destDir) throws IOException {

		log.info("from {} to {}", srcDir, destDir);
		org.apache.hadoop.fs.Path srcPath = new org.apache.hadoop.fs.Path(srcDir).getParent();
		org.apache.hadoop.fs.Path destPath = new org.apache.hadoop.fs.Path(destDir).getParent();
		if(!hdfs.exists(destPath)) {
			hdfs.mkdirs(destPath);
		}
		RemoteIterator<LocatedFileStatus> files = hdfs.listFiles(srcPath, false);
		while(files.hasNext()) {
			LocatedFileStatus file = files.next();
			if(file.isFile()) {
				FileUtil.copy(hdfs, file.getPath()	, hdfs, destPath, true, true, configuration);
			}
		}

    }


}
