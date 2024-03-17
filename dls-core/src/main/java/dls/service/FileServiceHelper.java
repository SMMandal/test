package dls.service;

import com.diffplug.common.base.Errors;
import com.google.common.base.Joiner;
import com.google.common.collect.Lists;
import dls.bean.FileDescriptor;
import dls.bean.MetaSchema;
import dls.bean.Permission;
import dls.exception.DlsNotFoundException;
import dls.exception.DlsPrivacyException;
import dls.exception.DlsSecurityException;
import dls.exception.DlsValidationException;
import dls.repo.*;
import dls.vo.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Example;
import org.springframework.data.domain.ExampleMatcher;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import reactor.core.publisher.Flux;

import java.io.IOException;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static dls.bean.FileDescriptor.UploadMode.APPEND;
import static dls.bean.FileDescriptor.UploadMode.ARCHIVE;
import static dls.bean.FileDescriptor.UploadMode.OVERWRITE;

@Component
@Slf4j
public class FileServiceHelper {

	private static final int KB = 1024;
	static final String DLS_ARCHIVE_TO = "dls:archivedTo";
	static final String DLS_LINEAGE = "dls:lineage";
	static final String DLS_INTERNAL = "dls:internal";
	static final String DLS_RENAMED_TO = "dls:renamedTo";
	static final String DLS_ = "dls:";
	public static final String ARCHIVE_TIMESTAMP_FORMAT = "ddMMyyyyHHmmss";
	public static final String LINKED = "linked";
	@Autowired private UserService uservice;
	@Autowired private DlsServiceHelper dhelper;
	@Autowired private FileRepo fileRepo;
	@Autowired private CatalogTextSearchRepo catalogTextSearchRepo;
	@Autowired private FileMetaRepo fileMetaRepo;
	@Autowired private LinkRepo linkRepo;
	@Autowired private DirectoryRepo directoryRepo;
	@Autowired private CommentRepo commentRepo;
	@Autowired private IFileManagementService dfsService;
//	@Autowired private MetaDataSchemaService mdsService;
	@Autowired private PermissionService permissionService;
	@Autowired private MetaDataSchemaRepo metaDataSchemaRepo;
	@Autowired private CatalogRepo catalogRepo;
	protected static Map<String,String> mapOfMetaValStandardEnf = new HashMap<>();
//	protected static Boolean standardEnfInsertMeta = false;
	@Value("${web.hdfs.path}") 
	private String webHdfsPath;
	@Value("${local.fs.bundle.path}") 
	private String bundleFilePath;
	@Value("${local.fs.failsafe.path}")
	private String failsafeFilePath;
	@Value("${dls.enable.hdfs}")
	private Boolean enableHDFS;
	@Value("${dls.enable.azureblob}")
	private Boolean enableAzureBlob;
	@Value("${dls.append.file.max.size}")
	private short maxAppendSize;
	@Value("${local.fs.bundle.path}")
	private String bundlePath;
	@Value("${dls.staging-url}" )
	private String stagingURL;



	String saveAndUpload(String filename,
			String savepoint, MultipartFile file,
			FileDescriptor.UploadMode mode, String directory, String[] metadata, UserVO user ,String comment ) throws IOException {

		checkAvailableStorage(user.getTenant(), file.getSize());
		String originalFileName = Optional.ofNullable(file).map(MultipartFile::getOriginalFilename).orElse(filename);
		Map <String, String>  props = dhelper.validate(filename, savepoint, originalFileName, metadata);

		FileVO vo = FileVO.builder()
				.fileName(filename)
				.savepoint(savepoint)
				.sizeInByte(file.getSize())
				.user(user)
				.deleted(false)
				.external(false)
				.createdOn(Timestamp.from(Instant.now()))
				.build();

		List<FileMetaVO> meta = matchSchemaAndBuildMetadata(user, vo, props);

		DirectoryVO directoryVO = checkDirectory(directory, vo, user, meta);
		vo.setDirectory(directoryVO);
		String fsPath = dhelper.generateDfsPath(user, filename, savepoint,
				Optional.ofNullable(directoryVO).map(DirectoryVO::getId).orElse(null));
		vo.setFsPath(fsPath);
		SimpleDateFormat dateFormat = new SimpleDateFormat(ARCHIVE_TIMESTAMP_FORMAT);
		dateFormat.setTimeZone(TimeZone.getTimeZone("GMT"));
		String createdOn = dateFormat.format(new Date());

		if((mode == OVERWRITE || mode == APPEND || mode == ARCHIVE) && null != vo.getLock()) {
			throw new DataIntegrityViolationException("file.locked");
		}
		try {			
			switch (mode) {
			case OVERWRITE: {
				FileVO saved = checkAndUpdateFile(fsPath, user, meta, file.getSize(), mode, createdOn, comment);
				dfsService.write(file, fsPath, saved.getId());
				break;
			}
			case APPEND: {
				FileVO saved = checkAndUpdateFile(fsPath, user, meta, file.getSize(), mode, createdOn, comment);
//				if(!enableHDFS) {
					dfsService.append(saved.getFsPath(),file);
//				} else {
//					dfsService.appendToHDFS(fsPath, file);
//				}
				break;
			}
			case ARCHIVE: {
				checkAndUpdateFile(fsPath, user, meta, file.getSize(), mode, createdOn, comment);
				vo.setMeta(meta);
				if(enableAzureBlob) {
					vo.setStorage("B");
				} else {
					vo.setStorage(enableHDFS ? "H" : "L");
				}
				meta.add(FileMetaVO.builder()
						.user(user)
						.file(vo)
						.name(DLS_LINEAGE)
						.value(mode.name())
						.build());
				FileVO saved = fileRepo.saveAndFlush(vo);
				if(!enableHDFS) {
					if(dfsService.archive(fsPath, createdOn)) {
						dfsService.write(file, fsPath, saved.getId());
					}
				}
				else if(dfsService.archive(fsPath, createdOn)) {
					dfsService.write(file, fsPath, saved.getId());
				}
				break;
			}
			case RESTRICT : default : {
				meta.add(FileMetaVO.builder()
						.user(user)
						.file(vo)
						.name(DLS_LINEAGE)
						.value("CREATED")
						.build());
				vo.setMeta(meta);
				FileVO saved = fileRepo.saveAndFlush(vo);
//				if(standardEnfInsertMeta)
				dhelper.insertMetaForStandardRule(user,fsPath,mapOfMetaValStandardEnf);
				
				if(comment != null && !comment.isEmpty())
				{
					addComment(comment, saved, user);
				}
				dfsService.write(file, fsPath, saved.getId());
				break;
			}
			}
		} catch (DataIntegrityViolationException e) {
			throw new DataIntegrityViolationException((e.getMessage() == null) ? "already.exists" : e.getMessage());
		}

		return fsPath;
	}

	public void addComment(String comment,FileVO fileVO,UserVO user)
	{
		if(null != fileVO.getLock()) {
			throw new DataIntegrityViolationException("file.locked");
		}
		commentRepo.saveAndFlush(CommentsVO.builder().comment(comment).createdOn(Timestamp.from(Instant.now())).
				file(fileVO).tenant(user.getTenant()).user(user).build());
	}

	DirectoryVO  checkDirectory(String directory, FileVO file, UserVO user, List<FileMetaVO> fileMetaVOS) {

		if(null == directory) {
			return null;
		}
		return Optional
				.ofNullable(directoryRepo.getDirectoryByPermittedUser(directory, user.getTenant().getId(), user.getId()))
				.map(d -> {
					if(d.getPermission()
							.stream()
							.noneMatch(p -> p.getAction().contains("W") &&
									Lists.newArrayList(p.getAcquiredUser(), p.getPermittedUser()).contains(user.getId()))) {
						throw new DataIntegrityViolationException("no.write.privilege");
					}
					dhelper.complyDirectoryMetadataRule(d, fileMetaVOS, file, user);
					return d;
				})
				.orElseThrow(() -> new DataIntegrityViolationException("source.directory.notPresent"));

	}

	void checkAvailableStorage(TenantVO tenantVO, Long fileSize) {

		Long allocatedStorage = Optional.ofNullable(tenantVO.getAllocatedStorage()).orElse(Long.MAX_VALUE);
		Long usedStorage = Optional.ofNullable(fileRepo.getUsedStorage(tenantVO.getId())).orElse(0L);
		Long availableStorage = allocatedStorage - usedStorage;

		if(availableStorage < fileSize) {
			throw new DataIntegrityViolationException("insufficient.storage");
		}

	}

	List<FileMetaVO> matchSchemaAndBuildMetadata(UserVO user, FileVO file, Map <String, String> props) {



		TenantVO tenant = user.getTenant();
		Flux<MetaSchemaVO> schemaFlux = Flux.fromIterable(metaDataSchemaRepo.findByTenantId(tenant.getId()));

		Flux<FileMetaVO> voFlux = Flux.fromIterable(props.keySet())
				.map(p -> {
					String privacy = null;
					String keyName;
					if(p.contains("@")) {
						privacy = p.split("@")[0];
						keyName = p.split("@")[1];
					} else {
						keyName = p;
					}
					FileMetaVO metaVO = null;
					if(null != file.getMeta()) {

						metaVO = file.getMeta().stream()
								.filter(vo -> vo.getName().equalsIgnoreCase(keyName))
								.findFirst()
								.orElse(FileMetaVO.builder().name(keyName)
										.value(props.get(p))
										.file(file)
										.user(user)
										.build());
						metaVO.setValue(props.get(p));
					} else {
								metaVO = FileMetaVO.builder().name(keyName)
										.value(props.get(p))
										.file(file)
										.user(user)
										.build();
					}
					if(privacy != null) {
						String[] qualifier = metaVO.getQualifier();
						if(null == qualifier) {
							metaVO.setQualifier(new String[]{privacy});
						} else {
							List<String> list = new ArrayList<>(Arrays.stream(qualifier).toList());
							list.add(privacy);
							metaVO.setQualifier(list.toArray(new String[0]));
						}

						if(privacy.equalsIgnoreCase("private")) {
							metaVO.setName(user.getId() + "@" + metaVO.getName());
						}
					}
					return metaVO;
				})
				.cache();

		boolean schematic = Optional.ofNullable(tenant.getSchematic()).orElse(Boolean.FALSE);
		boolean allowAdhoc = Optional.ofNullable(tenant.getAllowAdhoc()).orElse(Boolean.FALSE);
		Integer maxKeyLen = Optional.ofNullable(tenant.getMaxKeyLen()).orElse(Integer.MAX_VALUE);
		Integer maxValueLen = Optional.ofNullable(tenant.getMaxValueLen()).orElse(Integer.MAX_VALUE);
		Integer maxMetaPerFile = Optional.ofNullable(tenant.getMaxMetaPerFile()).orElse(Integer.MAX_VALUE);

		if(schematic && props.size() > maxMetaPerFile) {
			throw new DataIntegrityViolationException("mismatch.schema.count");
		}
		if( schematic && !allowAdhoc ) {

			voFlux
					.map(r -> schemaFlux.any(s -> s.getName().equalsIgnoreCase(r.getName()))
							.block())
					.reduce((b1, b2) -> b1 && b2)
					.blockOptional()
					.ifPresent(b -> {
						if(!b) throw new DataIntegrityViolationException("mismatch.schema.rule");
					});

		}

		return voFlux
				.map(r -> {
						if(schematic &&! allowAdhoc) {

							MetaSchemaVO schema = Objects.requireNonNull(
										schemaFlux
												.filter(s -> s.getName().equalsIgnoreCase(r.getName()))
												.blockFirst());
								r.setSchema(schema);
								if(MetaSchema.MetadataType.NUMERIC.name().equalsIgnoreCase(schema.getType())) {
									r.setValue_numeric(Errors.suppress().getWithDefault(()->Double.valueOf(r.getValue()), null));
								}
						}
							return r;
						}
				)
				.doOnNext(r -> {
					if(schematic ) {
						if(r.getName().length() > maxKeyLen) {
							throw new DataIntegrityViolationException("mismatch.schema.key.length");
						}
						if(r.getValue().length() > maxValueLen) {
							throw new DataIntegrityViolationException("mismatch.schema.val.length");
						}
					}
				})
				.collectList().block();

	}
	



	FileVO checkAndUpdateFile(String fsPath, UserVO user, List <FileMetaVO> meta,
			long fileSize, FileDescriptor.UploadMode mode, String createdOn, String comment) {

		String errorCode = switch (mode) {
            case ARCHIVE -> "archive.source.mismatch";
            case APPEND -> "append.source.mismatch";
            case OVERWRITE -> "overwrite.source.mismatch";
            default -> "";
        };

        FileVO query = FileVO.builder().fsPath(fsPath).user(user).deleted(false).build();
		ExampleMatcher matcher = ExampleMatcher.matching().withIgnoreNullValues().withIgnorePaths("id");
		final String finalErrorCode = errorCode;
		FileVO existingFile = fileRepo.findOne(Example.of(query, matcher)).orElseThrow(() -> new DlsValidationException(finalErrorCode));

		if((mode == OVERWRITE || mode == APPEND || mode == ARCHIVE)
				&& null != existingFile.getLock()) {
			throw new DataIntegrityViolationException("file.locked");
		}

		boolean hdfsEnabled = Optional.ofNullable(existingFile.getStorage())
				.map(s -> s.equalsIgnoreCase("H"))
				.orElse(Boolean.TRUE);
		if(enableHDFS != hdfsEnabled) {
			throw new DataIntegrityViolationException("mismatch.storage");
		}
		
		boolean blobEnabled = Optional.ofNullable(existingFile.getStorage())
				.map(s -> s.equalsIgnoreCase("B"))
				.orElse(Boolean.TRUE);
		if(enableAzureBlob != blobEnabled) {
			throw new DataIntegrityViolationException("mismatch.storage");
		}

		addComment(comment, existingFile, user);
		String changedFsPath = existingFile.getFsPath().concat("_").concat(createdOn);
		if(ARCHIVE == mode) {
			existingFile.setFsPath(changedFsPath);

			catalogRepo.findById(existingFile.getId()).ifPresent(c -> {
				c.setSize(existingFile.getSizeInByte());
				c.setPath(changedFsPath);
				catalogRepo.save(c);
			});
			return fileRepo.save(existingFile);
		}

		saveFileLineage(existingFile, changedFsPath, user);
		fileMetaRepo.deleteInBatch(existingFile.getMeta());

		meta.forEach(m -> m.setFile(existingFile));
		meta.add(FileMetaVO.builder()
				.user(user)
				.file(existingFile)
				.name(DLS_LINEAGE)
				.value(mode.name())
				.build());

		existingFile.setMeta(meta);
		if(mode == APPEND) {
			fileSize += existingFile.getSizeInByte();

		}
		existingFile.setCreatedOn(Timestamp.from(Instant.now()));
		existingFile.setSizeInByte(fileSize);

		FileVO saved = fileRepo.save(existingFile);

		catalogRepo.findById(existingFile.getId()).ifPresent(c -> {
			c.setSize(existingFile.getSizeInByte());

			if(mode == ARCHIVE) {
				c.setPath(changedFsPath);
			}

			if(mode == APPEND) {
				var metaList = saved.getMeta();
				c.setMetadataIds( metaList.stream()
						.filter(m -> !m.getName().startsWith("dls:"))
						.map(FileMetaVO::getId).toArray(Long[]::new) );
				c.setMetadataList(metaList.stream()
						.filter(m -> !m.getName().startsWith("dls:"))
						.map(m -> {
							String name = m.getName();
							Long privateTo = null;
							String [] split = m.getName().split("@");
							if(split.length > 1) {
								name = split[1];
								privateTo = Long.parseLong( split[0] );
							}
							return new CatalogVO.Metadata(name, m.getValue(), m.getUser().getId(), privateTo) ;
						}).collect(Collectors.toList()));
			}

			catalogRepo.save(c);
		});
		return saved;

	}
	void saveFileLineage(FileVO existingFile, String changedFsPath, UserVO user) {

		FileVO backup = FileVO.builder().build();
		BeanUtils.copyProperties(existingFile, backup, "id", "fsPath", "meta");
		backup.setFsPath(changedFsPath);
		backup.setMeta(Lists.newArrayList());
		final Timestamp time = new Timestamp(existingFile.getCreatedOn().getTime());
		backup.setCreatedOn(time);
		existingFile.getMeta().forEach(m -> {
			FileMetaVO mvo = FileMetaVO.builder().build();
			BeanUtils.copyProperties(m, mvo, "id", "file");
			mvo.setFile(backup);
			backup.getMeta().add(mvo);
		});
		backup.getMeta().add(FileMetaVO.builder()
				.user(user)
				.file(backup)
				.name(DLS_INTERNAL)
				.value("true")
				.build());

		fileRepo.save(backup);
	}




	CatalogTextSearchVO getFile(String apiKey, String dlsKey, String fileURI, boolean temp) throws DlsSecurityException, DlsNotFoundException, DlsPrivacyException {

		UserVO user = uservice.authorize(apiKey, dlsKey);

		CatalogTextSearchVO result = catalogTextSearchRepo.getByFilePathNoDeleted(fileURI);
		if(null == result) {
			throw new DlsNotFoundException();
		}
		if(!Objects.equals(user.getId(), result.getUser().getId())) { // file owner is different
			if (result.getDirectory() == null || result.getDirectory().getPermission() == null) {
				throw new DlsNotFoundException();
			}


			else if (result.getDirectory().getPermission().stream()
					.noneMatch(p -> Objects.equals(p.getPermittedUser(), user.getId())
							&& p.getAction().contains(Permission.Action.R.name())
					)) {

				throw new DataIntegrityViolationException("no.read.privilege");
			}
		}

//		if(! Optional.ofNullable(result.getAction())
//				.map(a -> a.contains(Permission.Action.R.name()))
//				.orElse(Boolean.TRUE)) {
//			throw new DataIntegrityViolationException("no.read.privilege");
//		}



//		if(results.stream().allMatch(CatalogTextSearchVO::getDeleted)) {
//			throw new DataIntegrityViolationException("deleted.file");
//		}

//		CatalogTextSearchVO result = results
//				.stream()
//				.filter(r -> !r.getDeleted())
//				.findFirst()
//				.orElseThrow(DlsNotFoundException::new);

		if(!temp && !Optional.ofNullable(result.getUploaded()).orElse(Boolean.FALSE)) {
			throw new DataIntegrityViolationException("transfer.fail");
		}

		if(Optional.ofNullable(result.getExternal()).orElse(Boolean.FALSE)) {
			throw new DataIntegrityViolationException("outofband.file");
		}


		return result;
	}

	public static String maskPrivateMetadata(String metadata, UserVO user) {

		if(null == metadata) return null;
		metadata = metadata.replaceAll("public@","");
		metadata = Joiner.on(',').join(Arrays.stream(metadata.split(",")).map(m -> {
			String [] splits = m.split("=");
			if(splits[0]. contains("@")) {
				String key = splits[0].split("@")[1];
				try {
					long id = Long.parseLong(splits[0].split("@")[0]);
					splits[0] = key + "[private]";
					if (id != user.getId()) {
						splits[1] = "***";
					}
				} catch (Exception e) {

				}
				return Joiner.on('=').join(splits);
			}
			return  m;
		}).collect(Collectors.toList()));


		return metadata;
	}

	void addOldFileToLineage(FileVO oldFile, FileVO vo, UserVO user, String fileUri, String path) {

		SimpleDateFormat dateFormat = new SimpleDateFormat(ARCHIVE_TIMESTAMP_FORMAT);
		dateFormat.setTimeZone(TimeZone.getTimeZone("GMT"));
		String createdOn = dateFormat.format(new Date());
		String changedFsPath = fileUri.concat("_").concat(createdOn);

		BeanUtils.copyProperties(vo, oldFile, "id", "meta");
		oldFile.setFsPath(changedFsPath);
		oldFile.setDeleted(true);
		oldFile.setDeletedOn(Timestamp.from(Instant.now()));
		oldFile.setMeta(Lists.newArrayList());
		vo.getMeta()
				.forEach(m -> oldFile.getMeta().add(FileMetaVO.builder().file(oldFile).user(user).name(m.getName()).value(m.getValue()).build())
				);
		oldFile.getMeta().add(FileMetaVO.builder().file(oldFile).user(user).name(DLS_INTERNAL).value("true").build());
		oldFile.getMeta().add(FileMetaVO.builder().file(oldFile).user(user).name(DLS_RENAMED_TO).value(path).build());
	}




}
