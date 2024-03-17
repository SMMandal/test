package dls.service;

import dls.repo.FileRepo;
import lombok.extern.slf4j.Slf4j;
import org.apache.hadoop.fs.FileSystem;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;

@Slf4j

public class DfsSyncService {
	
	@Autowired private FileRepo fileRepo;		
	@Autowired private FileSystem hdfs;

	@Value("${local.fs.failsafe.path}") 
	private String failsafeFilePath;
	@Value("${dls.enable.hdfs}")
	private Boolean enableHDFS;

//	@Autowired
//	private Configuration configuration;
	


	@Scheduled(fixedRateString = "#{${dls.hdfs.sync.delay} * 1000}", initialDelay = 60000)
	public void scheduleFileSync() {
		
		try {
			Path basePath = FileSystems.getDefault().getPath(failsafeFilePath);
		
			Flux.fromStream(Files.walk(basePath, 10, java.nio.file.FileVisitOption.values()))
				.map(Path::toFile)
				.filter(File::isFile)
				.subscribe(f -> {
					String fsPath = f.getAbsolutePath().replaceAll(failsafeFilePath, "");
					fsPath = StringUtils.trimLeadingCharacter(fsPath, '/');
					fileRepo.findByFsPathAndDeleted(fsPath, false);
//					Long id = Long.parseLong(fsPath.split("/")[1]);
					fileRepo.findByFsPathAndStorage(fsPath, "H").ifPresent(vo -> {
						
						boolean deleted = false;
						
						if(null != vo.getDeleted() && vo.getDeleted()) {
							
							deleted = f.getParentFile().delete();
							
						} else {
							
							try {
							
								hdfs.moveFromLocalFile(new org.apache.hadoop.fs.Path(f.getAbsolutePath()), new org.apache.hadoop.fs.Path(vo.getFsPath()));
								deleted = f.getParentFile().delete();
								
								Mono.just(vo)
								.subscribe(o -> {		
									o.setUploaded(true);
									fileRepo.saveAndFlush(o);
									log.info("Finished file sync for {}", f.getName());	
								});
							} catch (IOException ex) {
								log.error("Error moving file from staging to HDFS {}", f.getName());	
							}
						}
						
						if(!deleted) {
							log.error("File could not be deleted from local store");
						}
					});
					
					
					
				});
			
		}catch(IOException e) {
			log.error("File sync aborted for {}", e.getMessage());
		}
		
	}




}
