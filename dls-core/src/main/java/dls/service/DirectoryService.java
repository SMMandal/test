package dls.service;

import com.google.common.base.Joiner;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import dls.bean.*;
import dls.exception.DlsNotFoundException;
import dls.exception.DlsPrivacyException;
import dls.exception.DlsSecurityException;
import dls.exception.DlsValidationException;
import dls.repo.*;
import dls.vo.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ArrayUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.InvalidDataAccessApiUsageException;
import org.springframework.data.domain.Example;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;

import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;

import static dls.exception.GlobalExceptionHandler.ALREADY_EXISTS;
import static dls.exception.GlobalExceptionHandler.UPDATED;
import static dls.util.BeanValidationConstraint.DIRECTORY_LEN;
import static org.springframework.http.HttpStatus.*;


@Service
@Slf4j
public class DirectoryService {

	private static final String DIRECTORY = "directory";
	@Autowired private UserService uservice;
	@Autowired private DirectoryRepo directoryRepo;
	@Autowired private TenantRepo tenantRepo;
	@Autowired private PermissionRepo permissionRepo;
	@Autowired private UserRepo userRepo;
	@Autowired private DirectoryMetaRepo dirMetaRepo;
	@Autowired private MetaDataSchemaService mdService;
	@Autowired private DlsServiceHelper dlsServiceHelper;
	@Autowired private Environment ev;
	@Autowired private DirectoryServiceHelper directoryServiceHelper;
	@Autowired private DlsServiceHelper dhelper;

	/**
	 * Update directory metadata rule for a directory
	 * @param apiKey
	 * @param dlsKey
	 * @param directory
	 * @param rules
	 * @param enforcement
	 * @return
	 * @throws DlsSecurityException
	 * @throws DlsPrivacyException
	 * @throws DlsNotFoundException
	 */
	public List<DlsResponse> updateDirectoryRule(String apiKey, String dlsKey, String directory, List<MetadataRule> rules, String enforcement)
			throws DlsSecurityException, DlsPrivacyException, DlsNotFoundException {


		UserVO user = uservice.authorize(apiKey, dlsKey);
		TenantVO tenant = user.getTenant();

		if(!user.getAdmin() && permissionRepo.checkPermission(user.getId(), directory, "%"+Permission.Util.DIRECTORY_CREATE+"%") == 0) {
			throw new DlsPrivacyException();
		}

//		if(!user.getAdmin()) {
//			throw new DlsPrivacyException();
//		}
		DirectoryVO directoryVO = Optional.ofNullable(directoryRepo.
				findByDirectoryIgnoreCaseAndDeletedAndTenantId(directory, false, tenant.getId())).
				orElseThrow(DlsNotFoundException::new);

		List<DirectoryMetaVO> vos = directoryServiceHelper.matchSchemaAndBuildDirectoryRuleToVO(tenant, rules);

//				.subscribe(dirMetaRepo::saveAll);
//		dirMetaRepo.saveAll(Objects.requireNonNull(Flux.fromIterable(vos)
//				.doOnNext(vo -> vo.setUser(user))
//				.doOnNext(vo -> vo.setDirectory(directoryVO))
//				.collectList().block()));
		List<DlsResponse> errors = Lists.newArrayList();
		List<DlsResponse> responses = Flux.fromIterable(vos)
				.doOnNext(vo -> vo.setUser(user))
				.doOnNext(vo -> vo.setDirectory(directoryVO))
				.doOnNext(dirMetaRepo::save)
				.map(r -> DlsResponse.builder().messages(Set.of("UPDATED"))
						.code(RESET_CONTENT.value()).key("name").value(r.getName()).build())
				.onErrorContinue((e,m) -> errors.add(DlsResponse.builder().messages(Set.of(ALREADY_EXISTS))
						.code(CONFLICT.value()).key("name").value(((DirectoryMetaVO)m).getName()).build()))
				.collectList().block();

		directoryVO.setEnforcementType(enforcement);
//		dirMetaRepo.saveAll(vos);
		directoryRepo.save(directoryVO);
		Optional.ofNullable(responses).ifPresent(errors::addAll);
		return errors;
	}











	Flux<Directory> expandDirectoryPath(final Directory directory) {

		final String directoryName = directory.getDirectory();

		return Flux.create(sink -> {

			String path = StringUtils.trimTrailingCharacter(directoryName, '/');
			while(!path.isEmpty()) {

				Directory target = Directory.builder().directory(path).build();

				if(directoryName.equalsIgnoreCase(path)) {
					target.setPermissions(directory.getPermissions());
					target.setRule(directory.getRule());
					target.setEnforcement(directory.getEnforcement());
					target.setMetadata(directory.getMetadata());
				}

				path = path.substring(0, path.lastIndexOf('/'));
				sink.next(target);
			}
			sink.complete();
		});

	}



	Long findParentId(Map <String, Long> directoryIdMap, String directory, Long tenantId) {

		String parent = StringUtils.trimTrailingCharacter(directory, '/')
				.substring(0, directory.lastIndexOf('/'));
		return directoryIdMap.computeIfAbsent(parent,v -> directoryRepo.getIdByDirectory(tenantId, parent));
	}


	/**
	 * Create a new directory along with permission and directory rule
	 * @param apiKey
	 * @param dlsKey
	 * @param directories
	 * @return
	 * @throws DlsSecurityException
	 * @throws DlsPrivacyException
	 */
	public List<DlsResponse> createDirectory(String apiKey, String dlsKey, List<Directory> directories) throws DlsSecurityException, DlsPrivacyException
	{


		UserVO user = uservice.authorize(apiKey, dlsKey);
		Map <String, DlsResponse> response = Maps.newHashMap();
		Map <String, Long> dMap = Maps.newHashMap();
		directories.stream()
				.peek(d -> d.setDirectory(StringUtils.trimTrailingCharacter(
						(d.getDirectory().startsWith("/")
								? d.getDirectory()
								: "/".concat(d.getDirectory())).trim(),
						'/')))
//				.peek(d -> d.setDirectory(d.getDirectory().replaceAll("/{2,}", "/")))
				.map(Directory::getDirectory)
				.forEach(name -> response.put(name, DlsResponse.builder()
						.key(DIRECTORY)
						.value(name)
						.code(CREATED.value())
						.messages(Set.of(CREATED.name()))
						.build()));

		BiConsumer <Throwable, Object> decodeError = (e, d) -> {


			    final String name = (d instanceof DirectoryVO) ?
					((DirectoryVO)d).getDirectory() : (d instanceof Directory) ?
					((Directory)d).getDirectory() : (d instanceof PermissionVO) ?
						((PermissionVO)d).getDirectory().getDirectory() : DIRECTORY;
			log.error("directory {} and error is {}", name, e.getMessage());


			String m = Optional.ofNullable(ev.getProperty(e.getMessage())).orElse(e.getMessage());
			String message = (e instanceof InvalidDataAccessApiUsageException || e.getMessage().contains("ConstraintViolationException"))
					? "Directory already exists" : m;


			response.computeIfPresent(name,
					(k,v) -> DlsResponse.builder()
						.key(DIRECTORY)
						.value(name)
						.code(CONFLICT.value())
						.messages(Set.of(message))
						.build());
//				response.computeIfPresent(name, (k,v) -> message);

		};


		Flux.fromIterable(directories)
				.doOnNext(d -> checkDirectoryExists(user, d.getDirectory()))
				.doOnNext(d -> checkDirectoryPermission(user, d, Permission.Util.DIRECTORY_CREATE))
				.flatMap(this::expandDirectoryPath)
				.map(d -> directoryServiceHelper.buildDirectoryToVO(d, user))
				.doOnNext(d -> {if(!response.containsKey(d.getDirectory())) d.setDirectoryMetaVOList(null);})
				.sort(Comparator.comparingInt(d -> StringUtils.countOccurrencesOf(d.getDirectory(), "/")))
				.doOnNext(v -> v.setParent(findParentId(dMap, v.getDirectory(), user.getTenant().getId())))
				.map(dlsServiceHelper::saveDirectoryAndPermission)
//				.map(vo -> Mono.just(vo).doOnNext(d -> d.setPermission(null)).map(directoryRepo::save).block())
				.doOnNext(vo -> dMap.put(vo.getDirectory(), vo.getId()))
				.flatMap(d -> Flux.fromIterable(d.getPermission()).doOnNext(p -> p.setDirectory(d)))
				.onErrorContinue(decodeError)
			.subscribe()
		;

		return Lists.newArrayList(response.values());
	}

	private void checkDirectoryExists(UserVO user, String directory) {

		if(directory.matches(".*/{2,}.*")) {
			throw new DlsValidationException("Contains multiple successive '/' characters as directory separator");
		}
		if(Arrays.stream(directory.split("/")).anyMatch(d -> d.length() > DIRECTORY_LEN)) {
			throw new DlsValidationException("too.long.directory");
		}
		if(null != directoryRepo.findByDirectoryIgnoreCaseAndDeletedAndTenantId(directory, false, user.getTenant().getId())) {
			throw new InvalidDataAccessApiUsageException("Duplicate directory name");
		}
	}

	void checkDirectoryPermission(UserVO user, Directory d, char action)  {

		if(! Optional.ofNullable(user.getAdmin()).orElse(Boolean.FALSE)) {
			String parent = getParentDirectory(d.getDirectory());
			if(permissionRepo.checkPermission(user.getId(), parent, "%"+action+"%").intValue() == 0) {
				throw new RuntimeException("Not authorized");
			}
		}
	}


	private List<MetadataRule> buildDirectoryRuleFromVO(List<DirectoryMetaVO> vos) {
		return vos.stream()
				.filter(vo -> !vo.getIsMeta())
				.map(vo -> MetadataRule.builder()
				.name(vo.getName())
				.type(vo.getType())
				.valueMandatory(vo.getValue_mandatory())
				.defaultValue((null != vo.getValue_numeric()) ? vo.getValue_numeric().toString() : vo.getValue())
				.build())
				.collect(Collectors.toList());

	}

	private String buildDirectoryMetadataFromVO(List<DirectoryMetaVO> vos) {
		String val = Joiner.on(',').join(vos.stream()
				.filter(DirectoryMetaVO::getIsMeta)
				.map(vo -> vo.getName().concat("=").concat(vo.getValue()))
				.collect(Collectors.toList()));
		return val.isEmpty() ? null : val;

	}

	private Permission collectPermissionByAction(Flux<PermissionVO> pFlux) {

		Permission permission = Permission.builder().build();
		List <String> users = Lists.newArrayList();
		pFlux
				.doOnNext(p -> Permission.Util.parseAction(p.getAction(), permission))
				.flatMap(p -> Flux.fromArray(ArrayUtils.add(p.getAcquiredUser(), p.getPermittedUser())))
				.map(userRepo::findById)
				.map(Optional::get)
				.map(UserVO::getDlsUser)
				.distinct()
				.subscribe(users::add);
		permission.setUsers(users);
		return permission;
	}


	/**
	 *
	 * @param apiKey
	 * @param dlsKey
	 * @param directory
	 * @return
	 * @throws DlsSecurityException
	 * @throws DlsPrivacyException
	 * @throws DlsNotFoundException
	 */
	public List<Directory> getDirectory(String apiKey, String dlsKey, String directory, String metadataQuery)
			throws DlsSecurityException, DlsPrivacyException,DlsNotFoundException {

		UserVO user = uservice.authorize(apiKey, dlsKey);
//		directory = StringUtils.trimTrailingCharacter(directory, '/');
//		String parent = directory.substring(0, directory.lastIndexOf('/'));
//		Long permCount = Errors.suppress().getWithDefault(() ->
//				permissionRepo.checkPermission(user.getId(), StringUtils.trimTrailingCharacter(directory, '/'), "%"+Permission.Util.DIRECTORY_READ+"%"),
//				0L);
//		if(!user.getAdmin() &&  permCount == 0L) {
//			throw new DlsPrivacyException();
//		}


//		Example<DirectoryVO> search = Example.of(DirectoryVO.builder()
//				.deleted(Boolean.FALSE)
//				.tenant(user.getTenant())
//				.directory(directory).build());
		List<Directory> response = Lists.newArrayList();

		directory = (directory == null) ? "*" : directory;
		List<DirectoryVO> directoryVOList = directoryRepo.getDirectoryNamesByPermittedUser(directory.replace('*','%'), user.getTenant().getId(), user.getId());
		if(directoryVOList.isEmpty()) {
			throw new DlsNotFoundException();
		}



		Flux.fromIterable(directoryVOList)
				.filter(d -> applyMetadataQuery(d.getDirectoryMetaVOList(), metadataQuery))
				.groupBy(DirectoryVO::getDirectory)
				.subscribe(dFlux -> {
					List<Permission> permissions = Lists.newArrayList();
					List<MetadataRule> rules = Lists.newArrayList();
					AtomicReference<String> metadata = new AtomicReference<>(null);
					AtomicReference<String> enforcement = new AtomicReference<>(null);
					AtomicReference<Date> createdOn = new AtomicReference<>();
					dFlux
							.doOnNext(vo -> rules.addAll(buildDirectoryRuleFromVO(vo.getDirectoryMetaVOList())))
							.doOnNext(vo -> {
								if(null == createdOn.get()) {
									createdOn.set(vo.getCreatedOn());
								}
								enforcement.set(vo.getEnforcementType());
								metadata.set(buildDirectoryMetadataFromVO(vo.getDirectoryMetaVOList()));
							})
							.flatMap(vo -> Flux.fromIterable(vo.getPermission()))
							.groupBy(PermissionVO::getAction)
							.map(this::collectPermissionByAction)
							.subscribe(permissions::add);

					response.add(Directory.builder()
							.directory(dFlux.key())
									.createdOn(createdOn.get())
							.permissions(permissions)
									.enforcement(enforcement.get())
							.rule(rules)
									.metadata(FileServiceHelper.maskPrivateMetadata(metadata.get(),user))
							.build());
				});
		if(response.isEmpty()) {
			throw new DlsNotFoundException();
		}
		return response;

	}




//	public static void main(String[] args) {
//		String meta = "624@key1=value1,622@key2=value2,622@key3=value3";
//		System.out.println( maskPrivateMetadata(meta, UserVO.builder().id(622L).dlsUser("user1").build()));
//	}
	private boolean applyMetadataQuery(List<DirectoryMetaVO> vos, String metadataQuery) {

		if(metadataQuery == null) return true;

		String q = validateMetadataQuery(metadataQuery);

		List<Metadata> list = Lists.newArrayList();
		Arrays.stream(q.split(",")).forEach(m -> {
			String key;
			String value;
			var arr = m.split("=");
			if(arr.length > 1) {
				key = arr[0].trim();
				value = arr[1].trim().replaceAll("'","");

			} else if(arr[0].matches("'.+'")){
				key = null;
				value = arr[0].trim().replaceAll("'", "");
			} else {
				key = arr[0].trim();
				value = null;
			}
			list.add(Metadata.builder().key(key).value(value).build());
		});


		return vos.stream().anyMatch(v ->
				list.stream().anyMatch(m ->
					(m.getKey() == null || v.getName().equalsIgnoreCase(m.getKey()) )
							&& (m.getValue() == null || v.getValue().equalsIgnoreCase(m.getValue()))
				));
	}

	private String validateMetadataQuery(String metadata) {
		if(null != metadata && metadata.contains("&")) {
			if(metadata.matches(".*,.*&.*")) {
				throw new DlsValidationException("In metadata query, all AND (&) conditions must precede the OR (,) conditions");
			}
		}
		return metadata;
	}


	@Transactional("transactionManager")
	public void deleteDirectoryPermission(String apiKey, String dlsKey, String directory, String permittedUser)
			throws DlsSecurityException, DlsPrivacyException, DlsNotFoundException {

		UserVO user = uservice.authorize(apiKey, dlsKey);
//		if(! Optional.ofNullable(user.getAdmin()).orElse(Boolean.FALSE)) {
//			throw new DlsPrivacyException();
//		}
		String parent = getParentDirectory(directory);;
		if(!user.getAdmin() && permissionRepo.checkPermission(user.getId(), parent, "%"+Permission.Util.DIRECTORY_DELETE+"%") == 0) {
			throw new DlsPrivacyException();
		}

		Long permittedUserId = (permittedUser == null) ? null :
				Optional.ofNullable(userRepo.findByTenantAndDlsUser(user.getTenant(), permittedUser))
				.orElseThrow(DlsNotFoundException::new)
				.getId();

		DirectoryVO directoryVO = Optional.ofNullable(directory).map(d ->
				directoryRepo.findByDirectoryIgnoreCaseAndTenantIdAndCreatedByIdAndDeleted(d, user.getTenant().getId(),
						user.getId(), false)).orElse(null);
		if(null != directory && null == directoryVO) {
			throw new DlsNotFoundException();
		}

		List<PermissionVO> vos = permissionRepo.findAll(
				Example.of(PermissionVO.builder().permittedUser(permittedUserId).directory(directoryVO).build()));
//		List<PermissionVO> vos = Optional
//				.ofNullable(directoryRepo.getDirectoryByPermittedUser(directory, user.getTenant().getId(), permittedUserId))
//				.orElseThrow(DlsNotFoundException::new)
//				.getPermission();
		if(vos.isEmpty()) {
			throw new DlsNotFoundException();
		}
		List<PermissionVO> list = vos.stream()
				.filter(p -> null == permittedUser || p.getPermittedUser().equals(permittedUserId))
				.collect(Collectors.toList());
		permissionRepo.deleteAll(list);
		permissionRepo.flush();


	}


	public String deleteDirectory(String apiKey, String dlsKey, String directory)
			throws DlsSecurityException, DlsPrivacyException, DlsNotFoundException {

		UserVO user = uservice.authorize(apiKey, dlsKey);
		String parent = getParentDirectory(directory);
		if(!user.getAdmin() && permissionRepo.checkPermission(user.getId(), parent, "%"+Permission.Util.DIRECTORY_DELETE+"%") == 0) {
			throw new DlsPrivacyException();
		}

		DirectoryVO directoryVO = Optional
				.ofNullable(directoryRepo.findByDirectoryIgnoreCaseAndDeletedAndTenantId(directory, false, user.getTenant().getId()))
				.orElseThrow(DlsNotFoundException::new);

		if(directoryVO.getFiles().stream()
				.anyMatch(f -> ! Optional.ofNullable(f.getDeleted()).orElse(Boolean.FALSE))) {

			throw new DataIntegrityViolationException("error.directory.delete.file.exists");
		}

		if (!directoryRepo.findByParent(directoryVO.getId()).isEmpty()) {
			throw new DataIntegrityViolationException("error.directory.delete.subdir.exists");
		}

		permissionRepo.deleteAll(directoryVO.getPermission());
		directoryRepo.delete(directoryVO);

		return "Directory Successfully Deleted";	


	}

	private String getParentDirectory(String directory) {

		if(null == directory) return null;
		directory = StringUtils.trimTrailingCharacter(directory, '/');
		directory = StringUtils.trimLeadingCharacter(directory, '/');
		directory = "/".concat(directory);

		int i = directory.lastIndexOf('/') ;
		if(i > 0) return  directory.substring(0, i);
		else return "/";
	}

//	public static void main(String[] args) {
//		String[] dirs = {"/d1/d11",
//				"/d1/d11/d111",
//				"/d1",
//				"/",
//				"",
//				"/direct/ory"
//		};
//		Arrays.stream(dirs).map(DirectoryService::getParentDirectory).forEach(System.out::println);
//	}

	/**
	 *
	 * @param apiKey
	 * @param dlsKey
	 * @param directory
	 * @param permissions
	 * @return
	 * @throws DlsNotFoundException
	 * @throws DlsSecurityException
	 * @throws DlsPrivacyException
	 */
	public List<DlsResponse> updatePermission(String apiKey, String dlsKey, String directory, List<Permission> permissions) throws DlsNotFoundException, DlsSecurityException, DlsPrivacyException
	{
		UserVO user = uservice.authorize(apiKey, dlsKey);
//		if(!user.getAdmin()) {
//			throw new DlsPrivacyException();
//		}
		if(!user.getAdmin() && permissionRepo.checkPermission(user.getId(), directory, "%"+Permission.Util.DIRECTORY_CREATE+"%") == 0) {
			throw new DlsPrivacyException();
		}

		DirectoryVO directoryVO = Optional.ofNullable(
				directoryRepo.findByDirectoryIgnoreCaseAndDeletedAndTenantId(directory, false, user.getTenant().getId()))
				.orElseThrow(DlsNotFoundException::new);

//		permissionRepo.saveAll(permissions.stream()
//				.flatMap(p -> buildPermissionPerUser(p, user))
//				.peek(p -> p.setDirectory(directoryVO))
//				.collect(Collectors.toList()));

		List <DlsResponse> errors = Lists.newArrayList();
		List <DlsResponse> responses =
				Flux.fromIterable(permissions)
				.flatMap(p -> directoryServiceHelper.buildPermissionPerUser(p, user))
				.doOnNext(p -> p.setDirectory(directoryVO))
				.doOnNext(p -> {
					if(p.getPermittedUserName().equalsIgnoreCase(user.getDlsUser())) {
						throw new DlsValidationException("Permission can not be set for the directory creator");
					}
				})
				.doOnNext(permissionRepo::save)
				.map(p -> DlsResponse.builder().value(p.getPermittedUserName())
					.key("users")
					.code(RESET_CONTENT.value())
					.messages(Set.of(UPDATED))
					.build())
				.onErrorContinue((e,p) -> errors.add(DlsResponse.builder()
						.value((p instanceof PermissionVO) ? ((PermissionVO)p).getPermittedUserName()
								: ( (p instanceof UserVO) ? ((UserVO)p).getDlsUser() :  p.toString()))
						.key("users")
						.code(CONFLICT.value())
						.messages(Set.of((e instanceof DlsValidationException /*| e instanceof DataIntegrityViolationException*/)
								? e.getMessage() : "Permission ".concat(ALREADY_EXISTS)))
						.build()))
				.collect(Collectors.toList()).block();

		Optional.ofNullable(responses).ifPresent(errors::addAll);
		return errors;


	}


	@Transactional("transactionManager")
	public void deleteDirectoryRule(String apiKey, String dlsKey, String directory, String metaName)
			throws DlsNotFoundException, DlsPrivacyException, DlsSecurityException {

		UserVO user = uservice.authorize(apiKey, dlsKey);
//		if(!user.getAdmin()) {
//			throw new DlsPrivacyException();
//		}
		String parent = getParentDirectory(directory);
		if(!user.getAdmin() && permissionRepo.checkPermission(user.getId(), parent, "%"+Permission.Util.DIRECTORY_DELETE+"%") == 0) {
			throw new DlsPrivacyException();
		}

		DirectoryVO directoryVO = Optional.ofNullable(
				directoryRepo.findByDirectoryIgnoreCaseAndDeletedAndTenantId(directory, false, user.getTenant().getId()))
				.orElseThrow(DlsNotFoundException::new);


		List<Long> directoryMetaIdList = Flux.fromIterable(directoryVO.getDirectoryMetaVOList())
				.filter(m -> ! m.getIsMeta())
				.filter(m -> null == metaName || m.getName().equalsIgnoreCase(metaName))
//				.doOnNext(m -> directoryVO.getDirectoryMetaVOList().remove(m))
//				.peek(d -> d.setDirectory(null))
				.map(DirectoryMetaVO::getId)
				.collect(Collectors.toList())
				.block()
				;

		if(null == directoryMetaIdList || directoryMetaIdList.isEmpty()) {
			throw new DlsNotFoundException();
		}
		log.error("deleting {}", directoryMetaIdList);
		directoryMetaIdList.forEach(dirMetaRepo::delete);

	}


	public void updateDirectoryMetadata(String apiKey, String dlsKey, String directory, String[] metadata) throws DlsSecurityException, DlsPrivacyException, DlsNotFoundException {

		UserVO user = uservice.authorize(apiKey, dlsKey);
		TenantVO tenant = user.getTenant();
		if(!user.getAdmin() && permissionRepo.checkPermission(user.getId(), directory, "%"+Permission.Util.DIRECTORY_CREATE+"%") == 0) {
			throw new DlsPrivacyException();
		}

		Map <String, String>  props = dhelper.validate(metadata);

		final DirectoryVO directoryVO = Optional.ofNullable(directoryRepo.
						findByDirectoryIgnoreCaseAndDeletedAndTenantId(directory, false, tenant.getId())).
				orElseThrow(DlsNotFoundException::new);


		List<DirectoryMetaVO> vos = directoryServiceHelper.matchSchemaAndBuildDirectoryRuleToVO(tenant,
				props.entrySet().stream().map(e ->
					MetadataRule.builder()
							.name(e.getKey().replaceFirst("(?i)private@", user.getId() + "@"))
							.defaultValue(e.getValue())
							.type("TEXT")
							.build()
				).toList()).stream().peek(m -> m.setIsMeta(true)).toList();
//		List<DlsResponse> errors = Lists.newArrayList();
		List<DlsResponse> responses = Flux.fromIterable(vos)
//				.doOnNext(vo -> vo.setUser(user))
				.doOnNext(vo -> vo.setDirectory(directoryVO))
				.doOnNext(vo -> {

					DirectoryMetaVO searched = dirMetaRepo.findByDirectoryIdAndNameAndIsMeta(directoryVO.getId(), vo.getName(), true);
					if(null != searched) {
						vo.setId(searched.getId());
					}
					if(null == searched ||  ! searched.getValue().equals(vo.getValue())) {
						vo.setUser(user);
					} else {
						vo.setUser(searched.getUser());
					}
				})

				.doOnNext(dirMetaRepo::save)
				.map(r -> DlsResponse.builder().messages(Set.of("UPDATED"))
						.code(RESET_CONTENT.value()).key("name").value(r.getName()).build())
				.onErrorContinue((e,m) -> {

						}
				)
				.collectList().block();

//		dirMetaRepo.saveAll(vos);
//		directoryRepo.save(directoryVO);
//		Optional.ofNullable(responses).ifPresent(errors::addAll);
//		return errors;
	}

	public void deleteDirectoryMetadata(String apiKey, String dlsKey, String directory, String name) throws DlsSecurityException, DlsPrivacyException, DlsNotFoundException {

		UserVO user = uservice.authorize(apiKey, dlsKey);
		TenantVO tenant = user.getTenant();
		String parent = getParentDirectory(directory);
		if(!user.getAdmin() && permissionRepo.checkPermission(user.getId(), parent, "%"+Permission.Util.DIRECTORY_DELETE+"%") == 0) {
			throw new DlsPrivacyException();
		}

		final DirectoryVO directoryVO = Optional.ofNullable(directoryRepo.
						findByDirectoryIgnoreCaseAndDeletedAndTenantId(directory, false, tenant.getId())).
				orElseThrow(DlsNotFoundException::new);


		List<DirectoryMetaVO> vos = directoryVO.getDirectoryMetaVOList().stream()
				.filter(DirectoryMetaVO::getIsMeta)
				.filter(m -> m.getName().equalsIgnoreCase(name))
				.toList();

		if(vos.isEmpty()) throw new DlsNotFoundException();

		dirMetaRepo.deleteAllInBatch(vos);


	}
}


@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
class Metadata {
	private String key;
	private String value;
}