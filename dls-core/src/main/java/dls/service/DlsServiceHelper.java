package dls.service;

import com.google.common.base.Joiner;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import dls.exception.DlsNotFoundException;
import dls.exception.DlsPrivacyException;
import dls.exception.DlsSecurityException;
import dls.exception.DlsValidationException;
import dls.repo.*;
import dls.vo.*;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import reactor.core.publisher.Flux;

import jakarta.validation.constraints.NotNull;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.Map.Entry;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Component
@Slf4j
public class DlsServiceHelper {

	public static final String METADATA_VAL_REGEX = "^[^*,'\"=&!]+$"; // 17-Jul-23
//	public static final String METADATA_VAL_REGEX = "[\\sa-zA-Z_0-9\\.\\\\/\\+\\:\\-]+"; // allowing timestamp characters 19.8.19
	public static final String METADATA_KEY_REGEX = "(public@|private@)?\\w+";

	private static final String STANDARD = "STANDARD";
	public static final String INCONSISTENT = "INCONSISTENT";
	private static final String STRICT = "STRICT";
	@Autowired private FileRepo fileRepo;

	private static final String KV_REGEX = "(public@|private@)?(([^\\s,=]+=[^,=]+)(?:,\\s*)?)+";

	private static final Pattern pattern = Pattern.compile(KV_REGEX);

	public static final int MAX_METADATA_COUNT = 1000;

	public static final int MAX_KEY_LEN = 255;
	public static final int MIN_KEY_LEN = 3;
	public static final int MAX_VAL_LEN = 255;
	public static final int MIN_VAL_LEN = 1;
//	protected static final String[] ACTION_ARR = {"RWD", "RW", "R"};
	protected static final String[] QUALIFIER_ARR = {"INCONSISTENT"};

	@Autowired private UserService uservice;
//	@Autowired private DirectoryService dirService;
	@Autowired private PermissionService permissionService;
	@Autowired private MetaDataSchemaService mdsService;
	@Autowired private FileMetaRepo fileMetaRepo;
	@Autowired private MetaDataSchemaRepo metaDataSchemaRepo;
	@Autowired private DirectoryRepo directoryRepo;
	@Autowired private PermissionRepo permissionRepo;

	@Value("${local.fs.bundle.path}") 
	private String bundleFilePath;

	public Map <String, String> validate(@NotNull String filename, String savepoint, String originalFileName, String... metadata) throws DlsValidationException {

		if(! filename.equalsIgnoreCase(originalFileName)) {

			throw new DlsValidationException("unmatching.filename");
		}

		return validate(metadata);

	}


	public Map <String, String> validate(String... metadata) throws DlsValidationException {

		Map<String, String> map = Maps.newTreeMap(String.CASE_INSENSITIVE_ORDER);

		if(null != metadata && metadata.length > 0) {

			if(metadata.length > MAX_METADATA_COUNT) {

				throw new DlsValidationException("too.many.metadata");
			}

			for(String m : metadata)
			{
				Matcher matcher = pattern.matcher(m);

				if(! matcher.matches())
				{
					throw new DlsValidationException("invalid.kv.format");
				}

				//Map <String, String> kvMap = Splitter.on(",").omitEmptyStrings().withKeyValueSeparator("=").split(m.replace("\"", " "));

				Map <String, String> kvMap = new HashMap<>();


				kvMap.put(m.replace("\"", " ").split("=")[0].trim(),
						m.replace("\"", " ").split("=")[1].trim().replaceFirst("public@", ""));


				for(String k : kvMap.keySet()){

					k = k.trim();
					if(!k.matches(METADATA_KEY_REGEX)) {
						throw new DlsValidationException("invalid.metadata.key");
					}


					if(k.length() > MAX_KEY_LEN) {
						throw new DlsValidationException("invalid.metadata.key.max.length");
					}

					if(k.length() < MIN_KEY_LEN) {
						throw new DlsValidationException("invalid.metadata.key.min.length");
					}

					if(map.containsKey(k.replace("private@",""))) {
						throw new DlsValidationException("duplicate.kv.key");
					}

					String v = kvMap.get(k);
					v = v.trim();
					if(!v.matches(METADATA_VAL_REGEX)) {
						throw new DlsValidationException("invalid.metadata.value");
					}

					if(v.length() > MAX_VAL_LEN) {
						throw new DlsValidationException("invalid.metadata.value.max.length");
					}

					if(v.length() < MIN_VAL_LEN) {
						throw new DlsValidationException("invalid.metadata.value.min.length");
					}

					if(k.equalsIgnoreCase(v)) {
						throw new DlsValidationException("funny.metadata");
					}

				}




				map.putAll(kvMap);

			}
		}


		return map;

	}

	public String generateDfsPath(UserVO user, String filename, String savepoint, Long directoryId)  {

		String filepath = filename;
		String directoryIdStr ="";
		
		if(filename.contains(".")) {			
			int i = filename.lastIndexOf(".");
			filepath = Joiner.on("/").skipNulls().join(Lists.newArrayList(filename.substring(i+1), filename.substring(0, i)));

		} 	

		String tcupuser = Optional.ofNullable(user.getTenant().getTcupUser())
				.orElseThrow(() -> new DataIntegrityViolationException("file.upload.error.noTcupUser"));
		String dlsuser = Optional.ofNullable(user.getDlsUser())
				.orElseThrow(() -> new DataIntegrityViolationException("file.upload.error.noDlsUser"));

		if(null != savepoint) {
			savepoint = StringUtils.trimTrailingCharacter(
					StringUtils.trimLeadingCharacter(savepoint, '/'), '/');
		}
		
		if(null != directoryId && directoryId !=0)
		{
			directoryIdStr = directoryId.toString();
			return Joiner.on("/").skipNulls().join(	tcupuser, dlsuser,directoryIdStr, savepoint, filename);
		}
		else	
			return Joiner.on("/").skipNulls().join(	tcupuser, dlsuser, savepoint, filename);

	}

	public FileVO validateFile(UserVO user, String fsPath) throws DlsNotFoundException {

		if(null == fsPath)
			return null;

//		ExampleMatcher matcher = ExampleMatcher.matching().withIgnoreNullValues().withIgnorePaths("id");

//		FileVO query = FileVO.builder().user(user).fsPath(fsPath).deleted(false).build();

		return Optional
				.ofNullable(fileRepo.findByUserAndFsPathAndDeleted(user, fsPath, false))
				.orElseThrow(DlsNotFoundException::new);

//		return fileRepo.findOne(Example.of(query, matcher)).orElseThrow(DlsNotFoundException::new);

	}

	public boolean validateMetaSchema(String apiKey, String dlsKey, String[] metadata)
			throws DlsSecurityException, DlsPrivacyException, DlsNotFoundException {
		List<MetaSchemaVO> tSchemaMetaData = mdsService.getMetadataSchema(apiKey, dlsKey);
		int noOfMeta = metadata.length;
		Set<String> tempSet = new HashSet<String>();
		for (MetaSchemaVO metaSchemaVO : tSchemaMetaData) {
			tempSet.add(metaSchemaVO.getName());
		}
		//check for schematic 
		if(!tSchemaMetaData.get(0).getTenant().getSchematic())
			return true;
		Integer kLen = tSchemaMetaData.get(0).getTenant().getMaxKeyLen();
		Integer vLen = tSchemaMetaData.get(0).getTenant().getMaxValueLen();
		Integer maxMetaSize = tSchemaMetaData.get(0).getTenant().getMaxMetaPerFile();
		if(noOfMeta>maxMetaSize)
			throw new DlsValidationException("invalid.metadata.validation.exceed.no.max");

		List<String> pMetadataKeyList = new ArrayList<String>();
		for (String i : metadata) {
			String key = i.substring(1, i.indexOf("="));
			String value = i.substring(i.indexOf("=") + 1, i.length() - 1);
			if(key.length()>kLen)
				throw new DlsValidationException("invalid.metadata.validation.exceed.key.length");
			if(value.length()>vLen)
				throw new DlsValidationException("invalid.metadata.validation.exceed.value.length");
			pMetadataKeyList.add(key);
		}
		if (tempSet.containsAll(pMetadataKeyList)) {
			// all the provided meta keys present in the meta schema in DB
			log.info("File meta successfully validated against provided schema");
			return true;

		} else {
			// all the provided meta keys not present in the meta schema in DB
			// throw error
			log.info("File doesn't comply to the schema");
			throw new DlsValidationException("invalid.metadata.validation.not.present");
		}
	}



	public void insertMetaForStandardRule(UserVO user, String fsPath, Map<String,String> mapOfMetaValStandardEnf){

		FileVO fileVo = fileRepo.findByUserAndFsPathAndDeleted(user, fsPath, false);
		log.info("mapOfMetaValStandardEnf ::: "+mapOfMetaValStandardEnf);
		Iterator<Entry<String, String>> it = mapOfMetaValStandardEnf.entrySet().iterator();
		while(it.hasNext())
		{
			FileMetaVO fileMetaVO = FileMetaVO.builder().build();
			Map.Entry pair = (Map.Entry)it.next();
			fileMetaVO.setName(pair.getKey().toString());
			fileMetaVO.setValue(pair.getValue().toString());
			fileMetaVO.setQualifier(QUALIFIER_ARR);
			fileMetaVO.setUser(user);
			fileMetaVO.setFile(fileVo);
			fileMetaRepo.saveAndFlush(fileMetaVO);
		}
	}

	public boolean  validateOrgRoleAccess(UserVO loggedInUser, String lowerOrgHierachy,TenantVO tVO) throws DlsNotFoundException {
		/* UserVO uVOHighOrgH=uservice.getUserVO(tVO, higherOrgHierachy); */
		if(!loggedInUser.getAdmin())
			throw new DlsValidationException("Privileages not provided to a non admin user");
		UserVO uVOLowOrgH=uservice.getUserVO(tVO, lowerOrgHierachy);
		String[] orgPosnHU=loggedInUser.getOrgPosition();
		String[] orgPosnLU=uVOLowOrgH.getOrgPosition();

		for(int i=0;i<orgPosnHU.length;i++) {
			for(int j=0;j<orgPosnLU.length;j++) {
				if(orgPosnLU[j].contains(orgPosnHU[i])) {
					int posn=orgPosnLU[j].indexOf(orgPosnHU[i]);
					if (posn ==0)
						return true;
				}

			}

		}
		throw new DlsValidationException("Insufficient Privileages");

	}



	
	public void writeToBundle(@NonNull MultipartFile multipart, String bundleName,String directoryPath) {
		
			try {
				String baseDir = bundleFilePath.concat("/"+bundleName+"/"+directoryPath);
				File file = new File(baseDir.concat("/").concat(Objects.requireNonNull(multipart.getOriginalFilename())));
				File fileDir = new File(baseDir);
				
				if (!file.exists()) {					
					if(!fileDir.mkdirs()) {
						log.info("Directory already present ..");						
					} 
			    }		
				
				multipart.transferTo(file);
				
//				if(!fileDir.delete()) {
//					log.error("Error in deleting local directory");
//				}
				return;
				
			} catch (IOException e) {
				log.error("Fatal exception in storing uploaded file {} in DLS local."+e.getMessage());
				//log.error("Fatal exception in storing uploaded file {} in DLS local. {}",fsPath, e.getMessage());
				return;
			}
		
	}


	public void createBundleDescriptorFile(String descriptor, String bundleName) throws IOException {
		// TODO Auto-generated method stub
		String baseDir = bundleFilePath.concat("/"+bundleName);
		FileWriter writer = new FileWriter(baseDir+"/"+"_descriptor.json");
		writer.write(descriptor);
		writer.close();
		
	}
	
	public String createBundleJSONHash(String jsonBundleDescriptor) throws NoSuchAlgorithmException {
		MessageDigest digest = MessageDigest.getInstance("SHA-256");
		byte[] jsonBundleDescriptorHash=digest.digest(jsonBundleDescriptor.getBytes());
		return new String(jsonBundleDescriptorHash);
	}

	
	public void complyDirectoryMetadataRule(@NonNull DirectoryVO directory, @NonNull List<FileMetaVO> fileMetaVOS, FileVO file, UserVO user) {

		List<DirectoryMetaVO> metaRules = directory.getDirectoryMetaVOList();
		if(metaRules.stream().allMatch(DirectoryMetaVO::getIsMeta)) return;
		String enforcementType = Optional.ofNullable(directory.getEnforcementType()).orElse(STRICT);
		int ruleCount = metaRules.size();
		int metaCount = fileMetaVOS.size();
		long mandatoryMetaCount = metaRules.stream()
				.filter(m -> Optional.ofNullable(m.getValue_mandatory()).orElse(Boolean.TRUE))
				.count();
		if(STRICT.equalsIgnoreCase(enforcementType)) {
			if(metaCount > ruleCount ) {
				throw new DataIntegrityViolationException("invalid.metarule.strict.metadata.extra");
			}
			if(metaCount < mandatoryMetaCount) {
				throw new DataIntegrityViolationException("invalid.metarule.strict.metadata.mandatory.missing");
			}
			Set<String> mandatoryNames = metaRules.stream()
					.filter(m -> Optional.ofNullable(m.getValue_mandatory()).orElse(Boolean.TRUE))
					.filter(m -> !m.getIsMeta())
					.map(DirectoryMetaVO::getName)
					.map(String::toUpperCase)
					.collect(Collectors.toSet());
			Set<String> ruleNames = metaRules.stream()
					.filter(m -> !m.getIsMeta())
					.map(DirectoryMetaVO::getName)
					.map(String::toUpperCase)
					.collect(Collectors.toSet());
			Set<String> presentNames = fileMetaVOS.stream()
					.map(FileMetaVO::getName)
					.map(String::toUpperCase)
					.collect(Collectors.toSet());
			if(!presentNames.containsAll(mandatoryNames)) {
				throw new DataIntegrityViolationException("invalid.metarule.strict.metadata.missing");
			}
			presentNames.removeAll(ruleNames);
			if(presentNames.size() > 0) {
				throw new DataIntegrityViolationException("invalid.metarule.strict.metadata.extra");
			}

		}
		List<FileMetaVO> metaFromRule = Lists.newArrayList();

		metaRules.stream().filter(m -> !m.getIsMeta()).forEach(rule ->
				fileMetaVOS
						.stream()
						.filter(meta -> meta.getName().equalsIgnoreCase(rule.getName()))
						.findFirst()
						.ifPresentOrElse(v-> log.debug("{} passed directory rule", v.getName()),
								() -> {
								if (STRICT.equalsIgnoreCase(enforcementType) && rule.getValue_mandatory()) {
									throw new DataIntegrityViolationException("invalid.metarule.strict.metadata.mandatory.missing");
								} else if(rule.getValue() != null || rule.getValue_numeric() != null) {
									String val = (rule.getValue() == null) ? rule.getValue_numeric().toString() : rule.getValue();
									metaFromRule.add( FileMetaVO.builder()
											.value(val)
											.name(rule.getName())
											.file(file)
											.user(user)
											.value_numeric(rule.getValue_numeric())
											.schema(rule.getSchema())
											.build());
								}
							})
				);
		fileMetaVOS.addAll(metaFromRule);

	}

	@Transactional("transactionManager")
	public DirectoryVO saveDirectoryAndPermission(DirectoryVO directoryVO) {

		ArrayList<PermissionVO> permissionVOS = Lists.newArrayList(directoryVO.getPermission());
		directoryVO.setPermission(null);
		DirectoryVO saved = directoryRepo.save(directoryVO);
		permissionVOS.forEach(p -> p.setDirectory(saved));
		saved.setPermission(permissionRepo.saveAll(permissionVOS));
		return saved;

	}
}
