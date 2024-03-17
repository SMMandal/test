package dls.service;

import com.diffplug.common.base.Errors;
import com.google.common.base.Joiner;
import com.google.common.base.Splitter;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import dls.bean.*;
import dls.exception.DlsNotFoundException;
import dls.exception.DlsPrivacyException;
import dls.exception.DlsSecurityException;
import dls.exception.GlobalExceptionHandler;
import dls.repo.*;
import dls.vo.*;
import dls.web.FAIRController;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.compress.utils.Lists;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.hateoas.server.mvc.WebMvcLinkBuilder;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static dls.exception.GlobalExceptionHandler.DELETED;
import static dls.exception.GlobalExceptionHandler.UPDATED;
import static dls.service.FAIRServiceHelper.FAIRNodes.REPOSITORY;
import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.linkTo;
import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.methodOn;
import static org.springframework.http.HttpStatus.*;


@Service
@Slf4j
public class FAIRService {

	private static final String IDENTIFIER = "dct:identifier";
	@Autowired private UserService uservice;
	@Autowired private Environment ev;
	@Autowired private DirectoryService directoryService;
	@Autowired private DlsServiceHelper dlsServiceHelper;
	@Autowired private DirectoryMetaRepo directoryMetaRepo;
	@Autowired private FAIRRepo fairRepo;
	@Autowired private FileRepo fileRepo;
	@Autowired private FAIRServiceHelper helper;
	@Autowired private PermissionRepo permissionRepo;
	@Autowired private MetaDataSchemaRepo metaDataSchemaRepo;
	@Autowired private DirectoryServiceHelper directoryServiceHelper;
	@Autowired private AuditRepo auditRepo;
	@PersistenceContext private EntityManager em;
	@Autowired UserRepo userRepo;

	private final String dateFormat = "dd-MMM-yyyy HH:mm:ss Z";


	public List<DlsResponse> createRepository(String apiKey, String dlsKey, List<FAIRRepositoryDescriptor> elements)
			throws DlsSecurityException, DlsPrivacyException {

		UserVO user = uservice.authorize(apiKey, dlsKey);
		return helper.create(Flux.fromIterable(elements)
				.map(e -> helper.toDirectoryVO(e, user)), "Repository");
	}

	public List<DlsResponse> createCatalog(String apiKey, String dlsKey, String repoId, List<FAIRCatalogDescriptor> elements)
			throws DlsSecurityException, DlsPrivacyException, DlsNotFoundException {

		UserVO user = uservice.authorize(apiKey, dlsKey);
		DirectoryVO parent = Optional.ofNullable(fairRepo.findByDirectoryIgnoreCaseAndDeletedAndTenantId(repoId, true, user.getTenant().getId()))
				.orElseThrow(DlsNotFoundException::new);
		helper.checkWriteAccess(user, parent);
		return helper.create(Flux.fromIterable(elements)
				.doOnNext(e -> e.setIdentifier(repoId.concat("/").concat(e.getIdentifier())))
				.map(e -> helper.toDirectoryVO(e, user)).map(e -> {e.setParent(parent.getId()); return e;}), "Catalog");
	}

	public List<DlsResponse> createDataset(String apiKey, String dlsKey, String repoId, String catalogId, List<FAIRDatasetDescriptor> elements)
			throws DlsSecurityException, DlsPrivacyException, DlsNotFoundException {

		UserVO user = uservice.authorize(apiKey, dlsKey);
		String parentId = repoId.concat("/").concat(catalogId);
		DirectoryVO parent = Optional.ofNullable(fairRepo
				.findByDirectoryIgnoreCaseAndDeletedAndTenantId(parentId, true, user.getTenant().getId()))
				.orElseThrow(DlsNotFoundException::new);
		helper.checkWriteAccess(user, parent);
		return helper.create(Flux.fromIterable(elements)
				.doOnNext(e -> e.setIdentifier(parentId.concat("/").concat(e.getIdentifier())))
				.map(e -> helper.toDirectoryVO(e, user))
				.map(e -> {e.setParent(parent.getId()); return e;} ), "Dataset");
	}




	public FAIRRepository getRepository(String apiKey, String dlsKey, String repoId)
			throws DlsSecurityException, DlsPrivacyException, DlsNotFoundException {

		UserVO user = uservice.authorize(apiKey, dlsKey);
		FAIRRepository repo =Optional.ofNullable(
				fairRepo.findByDirectoryIgnoreCaseAndDeletedAndTenantId(repoId, true, user.getTenant().getId()))
				.map(vo -> Errors.suppress().getWithDefault(() -> helper.buildRepoFromDirectory(apiKey, dlsKey, repoId, vo), null))
				.orElseGet(() -> !user.getTenant().getTcupUser().equalsIgnoreCase(repoId) ? null
						: Errors.suppress().getWithDefault(() -> helper.buildRepoFromTenant(apiKey, dlsKey, user), null));

		return Optional.ofNullable(repo).orElseThrow(DlsNotFoundException::new);
	}

	public FAIRCatalog getCatalog(String apiKey, String dlsKey, String repoId, String catalogId)
			throws DlsSecurityException, DlsPrivacyException, DlsNotFoundException {

		UserVO user = uservice.authorize(apiKey, dlsKey);
		FAIRCatalog cat = Optional.ofNullable(fairRepo
				.findByDirectoryIgnoreCaseAndDeletedAndTenantId(repoId.concat("/").concat(catalogId), true, user.getTenant().getId()))
				.map(vo -> Errors.suppress().getWithDefault(() -> helper.buildCatalogFromDirectory(apiKey, dlsKey, repoId, catalogId, vo), null))
				.orElseGet(() ->  user.getTenant().getTcupUser().equalsIgnoreCase(repoId) && user.getDlsUser().equalsIgnoreCase(catalogId) ?
						 Errors.suppress().getWithDefault(() -> helper.buildCatalogFromUser(apiKey, dlsKey, user), null)
						: null);
		return Optional.ofNullable(cat).orElseThrow(DlsNotFoundException::new);
	}

	public FAIRDataset getDataset(String apiKey, String dlsKey, String repoId, String catalogId, String datasetId)
			throws DlsSecurityException, DlsPrivacyException, DlsNotFoundException {

		UserVO user = uservice.authorize(apiKey, dlsKey);
		FAIRDataset ds = Optional.ofNullable(fairRepo
				.findByDirectoryIgnoreCaseAndDeletedAndTenantId(Joiner.on('/').skipNulls().join(repoId, catalogId, datasetId),
						true, user.getTenant().getId()))
				.map(vo -> Errors.suppress().getWithDefault(() ->
						helper.buildDatasetFromDirectory(apiKey, dlsKey, repoId, catalogId, datasetId, vo), null) )
				.orElseGet(() -> Errors.suppress().getWithDefault(() ->
						fairRepo.findById(Long.parseLong(datasetId))
								.filter(dir -> repoId.equalsIgnoreCase(dir.getCreatedBy().getTenant().getTcupUser()))
								.filter(dir -> catalogId.equalsIgnoreCase(dir.getCreatedBy().getDlsUser()))
								.map(vo -> Errors.suppress().getWithDefault(() -> helper.buildDatasetFromRegularDirectory(apiKey, dlsKey, vo), null))
								.orElse(null), null) );

		return Optional.ofNullable(ds).orElseThrow(DlsNotFoundException::new);
	}



	public FAIRDistribution getDistribution(String apiKey, String dlsKey, String repoId, String catalogId, String datasetId, String distributionId)
			throws DlsSecurityException, DlsPrivacyException, DlsNotFoundException {

		UserVO user = uservice.authorize(apiKey, dlsKey);
		String dirStr = Joiner.on('/').skipNulls().join(repoId, catalogId, datasetId, distributionId);
		DirectoryVO directoryVO = fairRepo.findByDirectoryIgnoreCaseAndDeletedAndTenantId(dirStr,true, user.getTenant().getId());

		if(null == directoryVO) {
			throw new DlsNotFoundException();
		}
		
		FAIRDistribution dist = Optional.ofNullable(directoryVO)
				.map(vo -> Errors.suppress().getWithDefault(() ->
							helper.buildDistributionFromDirectory(apiKey, dlsKey, repoId, catalogId, datasetId, distributionId, vo), null))
				.orElseGet(() ->
					Errors.suppress().getWithDefault(() ->
							fileRepo.findById(Long.parseLong(distributionId)), null)
							.filter(file -> repoId.equalsIgnoreCase(file.getUser().getTenant().getTcupUser()))
							.filter(file -> catalogId.equalsIgnoreCase(file.getUser().getDlsUser()))
							.filter(file -> Errors.suppress().getWithDefault(() ->
									Long.valueOf(datasetId), Long.MAX_VALUE).equals(file.getDirectory().getId()))
							.map(file -> Errors.suppress().getWithDefault(() ->
									helper.buildDistributionFromFile(apiKey, dlsKey, file.getDirectory(), file), null))
							.orElse(null)
							);
		return Optional.ofNullable(dist).orElseThrow(DlsNotFoundException::new);

	}

	public Map<String, Object> find(String apiKey, String dlsKey, String repoId, String catalogId,
									 String datasetId, List<String> query, final Set<FAIRServiceHelper.FAIRNodes> exclude, Boolean includeDirectory)
			throws DlsSecurityException, DlsPrivacyException, DlsNotFoundException {

		UserVO user = uservice.authorize(apiKey, dlsKey);

		Set<String> includes = Stream.of(FAIRServiceHelper.FAIRNodes.values()).map(Enum::name).collect(Collectors.toSet());
		if(null != exclude) {
			includes.removeIf(e -> exclude.stream().map(Enum::name).anyMatch(e::equalsIgnoreCase));
		}

		List<DirectoryVO> children = null;

		String directoryExpression = helper.buildDirectoryLikeExpression(repoId, catalogId, datasetId);;




		String qstring = helper.parseAndBuildSQLFromMetadata(query, directoryExpression, includes, includeDirectory);
		Query q = em.createNativeQuery(
				qstring,
				DirectoryVO.class);

		List<DirectoryVO> directoryVOList = q.getResultList();
		if(directoryVOList.isEmpty()) throw new DlsNotFoundException();
		Map <String, Object> result = Maps.newLinkedHashMap();

		Set<FAIRRepository> repos = Sets.newIdentityHashSet();
		Set<FAIRCatalog> catalogs = Sets.newIdentityHashSet();
		Set<FAIRDataset> datasets = Sets.newIdentityHashSet();
		Set<FAIRDistribution> distributions = Sets.newIdentityHashSet();

		Flux.fromIterable(directoryVOList)
				.subscribe(directoryVO -> {

					String enforcement = directoryVO.getEnforcementType();

					switch (null == enforcement ? FAIRServiceHelper.FAIRNodes.NONE :
							FAIRServiceHelper.FAIRNodes.valueOf(enforcement.trim().toUpperCase())) {

						case REPOSITORY: {
							FAIRRepository fairRepository =
									Errors.suppress().getWithDefault(() -> helper.buildRepoFromDirectory(apiKey, dlsKey, directoryVO.getDirectory(), directoryVO), null);
							if(null != fairRepository ) repos.add(fairRepository);
							break;
						}

						case CATALOG:  {
							String[] parts = directoryVO.getDirectory().split("/");
							FAIRCatalog fairCatalog =
									Errors.suppress().getWithDefault(() -> helper.buildCatalogFromDirectory(apiKey, dlsKey, parts[0], parts[1], directoryVO), null);
							if(null !=  fairCatalog)  catalogs.add(fairCatalog);
							break;
						}
						case DATASET: {
							String[] parts = directoryVO.getDirectory().split("/");
							FAIRDataset fairDataset =
									Errors.suppress().getWithDefault(() -> helper.buildDatasetFromDirectory(apiKey, dlsKey, parts[0], parts[1],parts[2], directoryVO), null);
							if(null !=  fairDataset) datasets.add(fairDataset);
							break;
						}
						case DISTRIBUTION: {
							String[] parts = directoryVO.getDirectory().split("/");
							FAIRDistribution fairDistribution =
									Errors.suppress().getWithDefault(() ->
											helper.buildDistributionFromDirectory(apiKey, dlsKey, parts[0], parts[1],parts[2], parts[3], directoryVO), null);
							if(null !=  fairDistribution) distributions.add(fairDistribution);
							break;
						}
						case NONE :

							try {
								String rId = directoryVO.getCreatedBy().getTenant().getTcupUser();
								String cId = directoryVO.getCreatedBy().getDlsUser();
								String dsId = directoryVO.getId().toString();

								repos.add(helper.buildRepoFromTenant(apiKey, dlsKey, directoryVO.getCreatedBy()));
								catalogs.add(helper.buildCatalogFromUser(apiKey, dlsKey, directoryVO.getCreatedBy()));
								datasets.add(helper.buildDatasetFromDirectory(apiKey, dlsKey, rId, cId, dsId, directoryVO));
								Flux.fromIterable(directoryVO.getFiles())
										.subscribe(file -> {
											FAIRDistribution fairDistribution =
													Errors.suppress().getWithDefault(() ->
															helper.buildDistributionFromFile(apiKey, dlsKey, directoryVO, file), null);
											if(null !=  fairDistribution) distributions.add(fairDistribution);
										});

							} catch (DlsSecurityException | DlsPrivacyException | DlsNotFoundException e) {
								log.error(e.getMessage());
							}

					}
				});

		if(!repos.isEmpty() && !exclude.contains(REPOSITORY)) result.put("repository-list", repos);
		if(!catalogs.isEmpty() && !exclude.contains(FAIRServiceHelper.FAIRNodes.CATALOG))result.put("catalog-list", catalogs);
		if(!datasets.isEmpty() && !exclude.contains(FAIRServiceHelper.FAIRNodes.DATASET))result.put("dataset-list", datasets);
		if(!distributions.isEmpty() && !exclude.contains(FAIRServiceHelper.FAIRNodes.DISTRIBUTION))result.put("distribution-list", distributions);
		if(result.size() == 0) throw new DlsNotFoundException();
		return result;
	}


	public List<DlsResponse> updatePermission(String apiKey, String dlsKey, String repoId, String catalogId,
								   String datasetId, String distId, List<FAIRPermission> permissions) throws DlsSecurityException, DlsPrivacyException, DlsNotFoundException {

		UserVO user = uservice.authorize(apiKey, dlsKey);

		helper.validateFAIRHierarchy(repoId, catalogId, datasetId, distId);
		String dirStr = Joiner.on('/').skipNulls().join(repoId, catalogId, datasetId, distId);
		DirectoryVO dir = Optional.
				ofNullable(fairRepo.findByDirectoryIgnoreCaseAndDeletedAndTenantId(dirStr, true, user.getTenant().getId()))
				.orElseThrow(DlsNotFoundException::new);
		// accessor must be creator
		if(!dir.getCreatedBy().getId().equals(user.getId())) {
			throw new DlsPrivacyException();
		}
		AuditVO audit = AuditVO.builder()
				.success(true)
				.eventTime(Timestamp.from(Instant.now()))
				.entityId(dir.getId())
				.entity(dir.getEnforcementType().concat(":").concat(dir.getDirectory()))
				.user(user)
				.event(AuditEvent.PERMISSION_UPDATED.name())
				.build();

		//Delete existing permission of the user before updating the permission of the user
		List<UserVO> uservoList;
		PermissionVO permVO;
		if(null != dir.getPermission() && !dir.getPermission().isEmpty()) { //Directory has some existing permission(s)
			for(FAIRPermission permission : permissions) {
				uservoList = userRepo.findUserListForUsers(permission.getUsers(), user.getTenant().getId());
				if(null != uservoList) { // Some/All the usernames are valid
					for(UserVO userVO : uservoList) {
						//Check and get the user's permission on the directory
						permVO = dir.getPermission().stream().filter(perm -> userVO.getId().equals(perm.getPermittedUser())).findAny().orElse(null);		
						if(null != permVO) { //user has permission on the directory
							permissionRepo.delete(permVO);
						}			
					}
				}
			}
		}

		List<DlsResponse> responses = Lists.newArrayList();
		Flux.fromIterable(permissions)
				.flatMap(p -> {
					return Flux.fromIterable(p.getUsers())
							.map(u -> userRepo.findByTenantAndDlsUser(user.getTenant(), u))
							.map(UserVO::getId)
							.map(id -> PermissionVO
									.builder()
									.directory(dir)
									.permittedUser(id)
									.tenant(user.getTenant())
									.user(user)
									.action(p.getAction())
									.build())
							.onErrorContinue((e,o) -> {
								responses.add(DlsResponse.builder().code(HttpStatus.CONFLICT.value()).messages(Set.of("Invalid username")).build());
								log.error(e.getMessage());
							});

				})
				.doOnNext(permissionRepo::save)
				.onErrorContinue((e,o) -> responses.add(DlsResponse.builder().code(HttpStatus.CONFLICT.value()).messages(Set.of("Action is already set in user's permission")).build()))
				.subscribe(v -> responses.add(DlsResponse.builder().code(HttpStatus.RESET_CONTENT.value()).value(GlobalExceptionHandler.UPDATED).build()));
		auditRepo.save(audit);
		return responses;

	}

	public List <DlsResponse> createDistribution(String apiKey, String dlsKey, String repoId, String catalogId, String datasetId,
								   List<FAIRDistributionDescriptor> elements) throws DlsSecurityException, DlsPrivacyException, DlsNotFoundException {

		UserVO user = uservice.authorize(apiKey, dlsKey);
		String parentId = Joiner.on('/').skipNulls().join(repoId, catalogId, datasetId);
		DirectoryVO parent = Optional.ofNullable(fairRepo
				.findByDirectoryIgnoreCaseAndDeletedAndTenantId(parentId, true, user.getTenant().getId()))
				.orElseThrow(DlsNotFoundException::new);
		helper.checkWriteAccess(user, parent);
		List<DlsResponse> responses = Lists.newArrayList();

		elements.removeIf(d -> ! helper.isDistributionValidWithDLS(d, user, responses));
		if(!elements.isEmpty())
			responses.addAll(helper.create(Flux.fromIterable(elements)
				.doOnNext(e -> e.setIdentifier(parentId.concat("/").concat(e.getIdentifier())))
				.map(e -> helper.toDirectoryVO(e, user))
				.map(e -> {e.setParent(parent.getId()); return e;} ), "Distribution"));
		return responses;
	}

	public List <DlsResponse> updateMetadata(String apiKey, String dlsKey,
											 String repoId, String catalogId, String datasetId, String distId, Map<String, String> metadata) throws DlsSecurityException, DlsPrivacyException, DlsNotFoundException {

		UserVO user = uservice.authorize(apiKey, dlsKey);
		helper.validateFAIRHierarchy(repoId, catalogId, datasetId, distId);

		String dirStr = Joiner.on('/').skipNulls().join(repoId, catalogId, datasetId, distId);
		DirectoryVO dir = Optional.
				ofNullable(fairRepo.findByDirectoryIgnoreCaseAndDeletedAndTenantId(dirStr, true, user.getTenant().getId()))
//				.map(DirectoryVO::getDirectoryMetaVOList)
				.orElseThrow(DlsNotFoundException::new);
		AuditVO audit = AuditVO.builder()
				.success(true)
				.eventTime(Timestamp.from(Instant.now()))
				.entityId(dir.getId())
				.entity(dir.getEnforcementType().concat(":").concat(dir.getDirectory()))
				.user(user)
				.event(AuditEvent.METADATA_UPDATED.name())
				.build();
		helper.checkWriteAccess(user, dir);
		List<DirectoryMetaVO> dirMeta = dir.getDirectoryMetaVOList();
		List<DirectoryMetaVO> existingMeta = Optional.ofNullable(dirMeta)
				.orElseThrow(() -> new DataIntegrityViolationException("No metadata exists to update"));

		List <DlsResponse> responses = Lists.newArrayList();

		metadata.forEach((k,v) -> {
			existingMeta.stream()
					.filter(m -> m.getName().equalsIgnoreCase(k.trim()))
					.findFirst()
					.ifPresentOrElse(m -> {

						m.setValue(v);
						directoryMetaRepo.save(m);
						DlsResponse response = DlsResponse.builder().key("key").value(m.getName()).build();
						response.setMessages(Set.of(GlobalExceptionHandler.UPDATED));
						response.setCode(HttpStatus.RESET_CONTENT.value());
						responses.add(response);

						},
							() ->	{
								directoryMetaRepo.save(DirectoryMetaVO.builder().directory(dir)
									.name(k).type("TEXT").user(user)
									.value(v)
									.build());
								DlsResponse response = DlsResponse.builder().key("key").value(k).build();
								response.setMessages(Set.of(GlobalExceptionHandler.CREATED));
								response.setCode(HttpStatus.CREATED.value());
								responses.add(response);
								audit.setEvent(AuditEvent.METADATA_ADDED.name());
					});
		});

		auditRepo.save(audit);

		return responses;

	}

	@Transactional("transactionManager")
	public String deleteMetadata(String apiKey, String dlsKey, String repoId, String catalogId, String datasetId, String distId, String key)
			throws DlsNotFoundException, DlsSecurityException, DlsPrivacyException {

		UserVO user = uservice.authorize(apiKey, dlsKey);
		helper.validateFAIRHierarchy(repoId, catalogId, datasetId, distId);
		String dirStr = Joiner.on('/').skipNulls().join(repoId, catalogId, datasetId, distId);
		List<DirectoryMetaVO> dirMeta = Optional.
				ofNullable(fairRepo.findByDirectoryIgnoreCaseAndDeletedAndTenantId(dirStr, true, user.getTenant().getId()))
				.map(DirectoryVO::getDirectoryMetaVOList)
				.orElseThrow(DlsNotFoundException::new);

		Optional.ofNullable(dirMeta)
				.map(meta -> Flux.fromIterable(meta)
						.filter(m -> m.getName().equalsIgnoreCase(key.trim()))
						.doOnNext(m -> log.info("deleting metadata key {}", m.getName()))
						.map(DirectoryMetaVO::getId)
						.subscribe(directoryMetaRepo::delete))
				.orElseThrow(() -> new DataIntegrityViolationException("No metadata exists to delete"));

		DirectoryVO d = dirMeta.get(0).getDirectory();
		AuditVO audit = AuditVO.builder()
				.success(true)
				.eventTime(Timestamp.from(Instant.now()))
				.entityId(d.getId())
				.entity(d.getEnforcementType().concat(":").concat(d.getDirectory()))
				.user(user)
				.event(AuditEvent.METADATA_DELETED.name())
				.build();
		auditRepo.save(audit);
		return GlobalExceptionHandler.DELETED;
	}

//	public List<DlsResponse> addMetadata(String apiKey, String dlsKey, String repoId, String catalogId,
//										 String datasetId, String distId, Map<String, String> metadata) throws DlsSecurityException, DlsPrivacyException, DlsNotFoundException {
//
//		UserVO user = uservice.authorize(apiKey, dlsKey);
//		helper.validateFAIRHierarchy(repoId, catalogId, datasetId, distId);
//		String dirStr = Joiner.on('/').skipNulls().join(repoId, catalogId, datasetId, distId);
//		DirectoryVO dir = Optional.
//				ofNullable(fairRepo.findByDirectoryIgnoreCaseAndDeletedAndTenantId(dirStr, true, user.getTenant().getId()))
//				.orElseThrow(DlsNotFoundException::new);
//
//		helper.checkWriteAccess(user, dir);
//
//		List <DlsResponse> responses = Lists.newArrayList();
//		Flux.fromIterable(metadata.keySet())
//				.onErrorContinue((e,o) -> responses.add(DlsResponse.builder().value(o.toString()).key("key").code(CONFLICT.value()).build()))
//				.map(k -> DirectoryMetaVO.builder().directory(dir)
//						.name(k).type("TEXT").user(user)
//						.value(metadata.get(k))
//						.build())
//				.doOnNext(directoryMetaRepo::save)
//				.subscribe(m -> responses.add(DlsResponse.builder().code(CREATED.value())
//						.key("key").value(m.getName())
//						.messages(Set.of(GlobalExceptionHandler.CREATED)).build()));
//		return responses;
//
//	}

	@Transactional("transactionManager")
    public void delete(String apiKey, String dlsKey, String repoId, String catalogId, String datasetId, String distId)
			throws DlsSecurityException, DlsPrivacyException, DlsNotFoundException {
		UserVO user = uservice.authorize(apiKey, dlsKey);
		helper.validateFAIRHierarchy(repoId, catalogId, datasetId, distId);
		String dirStr = Joiner.on('/').skipNulls().join(repoId, catalogId, datasetId, distId);
		DirectoryVO dir = Optional.
				ofNullable(fairRepo.findByDirectoryIgnoreCaseAndDeletedAndTenantId(dirStr, true, user.getTenant().getId()))
				.orElseThrow(DlsNotFoundException::new);
		helper.checkDeleteAccess(user, dir);
		if( fairRepo.findByParent(dir.getId()).size() > 0) throw new DataIntegrityViolationException("sub.datapoint.exists");
		fairRepo.delete(dir);
		AuditVO audit = AuditVO.builder()
				.success(true)
				.eventTime(Timestamp.from(Instant.now()))
				.entityId(dir.getId())
				.entity(dir.getEnforcementType().concat(":").concat(dir.getDirectory()))
				.user(user)
				.event(AuditEvent.DELETED.name())
				.build();
		auditRepo.save(audit);
    }

	public void deletePermission(String apiKey, String dlsKey, String repoId, String catalogId, String datasetId, String distId, String dlsuser) throws DlsSecurityException, DlsPrivacyException, DlsNotFoundException {

		UserVO user = uservice.authorize(apiKey, dlsKey);

		helper.validateFAIRHierarchy(repoId, catalogId, datasetId, distId);
		String dirStr = Joiner.on('/').skipNulls().join(repoId, catalogId, datasetId, distId);
		DirectoryVO dir = Optional.
				ofNullable(fairRepo.findByDirectoryIgnoreCaseAndDeletedAndTenantId(dirStr, true, user.getTenant().getId()))
				.orElseThrow(DlsNotFoundException::new);
		// accessor must be creator
		if(!dir.getCreatedBy().getId().equals(user.getId())) {
			throw new DlsPrivacyException();
		}
		if(null == dir.getPermission() || dir.getPermission().isEmpty()) {
			throw new DlsNotFoundException();
		}
		
		UserVO userVO = userRepo.findUserForUsers(dlsuser, user.getTenant().getId());
		if(null == userVO) {
			throw new DlsNotFoundException();
		}
		List<PermissionVO> permList = dir.getPermission();
		PermissionVO permVO = permList.stream().filter(perm -> userVO.getId().equals(perm.getPermittedUser())).findAny().orElse(null);		
		if(null == permVO) {
			throw new DlsNotFoundException();
		}
		permissionRepo.delete(permVO);
		//permissionRepo.deleteAll(permList);
		AuditVO audit = AuditVO.builder()
				.success(true)
				.eventTime(Timestamp.from(Instant.now()))
				.entityId(dir.getId())
				.entity(dir.getEnforcementType().concat(":").concat(dir.getDirectory()))
				.user(user)
				.event(AuditEvent.PERMISSION_DELETED.name())
				.build();
		auditRepo.save(audit);
	}

	public void updateProvenance(String apiKey, String dlsKey, String repoId, String catalogId, String datasetId,
										String distId, FAIRProvenanceDescriptor provenance) throws DlsSecurityException, DlsPrivacyException, DlsNotFoundException {

		UserVO user = uservice.authorize(apiKey, dlsKey);
		helper.validateFAIRHierarchy(repoId, catalogId, datasetId, distId);
		if(! provenance.getCustom().keySet().stream().allMatch(k -> k.startsWith("prov:"))) {
			throw new DataIntegrityViolationException("not.valid.prov.attribute");
		}
		String dirStr = Joiner.on('/').skipNulls().join(repoId, catalogId, datasetId, distId);
		DirectoryVO dir = Optional.
				ofNullable(fairRepo.findByDirectoryIgnoreCaseAndDeletedAndTenantId(dirStr, true, user.getTenant().getId()))
//				.map(DirectoryVO::getDirectoryMetaVOList)
				.orElseThrow(DlsNotFoundException::new);
		Map <String, String> provMap = Maps.newHashMap(provenance.getCustom());
		if(null != provenance.getWasGeneratedBy()) {
			provMap.put("prov:wasGeneratedBy", provenance.getWasGeneratedBy());
		}

		String provenanceStr = Joiner.on(",").withKeyValueSeparator('=').join(provMap);

		AuditVO audit = AuditVO.builder()
				.success(true)
				.eventTime(Optional.ofNullable(provenance.getAtTime()).orElse(Timestamp.from(Instant.now())))
				.entityId(dir.getId())
				.entity(dir.getEnforcementType().concat(":").concat(dir.getDirectory()))
				.user(user)
				.event(provenance.getEvent())
				.provenance(provenanceStr)
				.build();
		auditRepo.save(audit);

	}

	public List<FAIRProvenance> getProvenance(String apiKey, String dlsKey, String repoId,
														String catalogId, String datasetId, String distId, /*, Timestamp fromTime, Timestamp toTime,*/
											  /*AuditEvent event,*/ Integer pageNo) throws DlsSecurityException, DlsPrivacyException, DlsNotFoundException {

		UserVO user = uservice.authorize(apiKey, dlsKey);
		helper.validateFAIRHierarchy(repoId, catalogId, datasetId, distId);
		String dirStr = Joiner.on('/').skipNulls().join(repoId, catalogId, datasetId, distId);
//		DirectoryVO dir = Optional.
//				ofNullable(fairRepo.findByDirectoryIgnoreCaseAndDeletedAndTenantId(dirStr, true, user.getTenant().getId()))
//				.orElseThrow(DlsNotFoundException::new);
//		AuditVO query = AuditVO.builder()
//				.entity(dir.getEnforcementType().concat(":").concat(dir.getDirectory()))
//				.entityId(dir.getId())
//				.event((null == event) ? null : event.name()).build();
//		Page<AuditVO> auditVOS = Optional.of(auditRepo.findAll(Example.of(query), PageRequest.of(pageNo, 100, Sort.by("eventTime"))))
//				.orElseThrow(DlsNotFoundException::new);



		List<AuditVO> auditVOS = Optional.ofNullable(auditRepo.findByEntityContains(dirStr, PageRequest.of(pageNo, 100)))
				.orElseThrow(DlsNotFoundException::new);

		List<FAIRProvenance> provenanceList = Flux.fromIterable(auditVOS)
				.filter(a -> a.getUser().getTenant().getId().equals(user.getTenant().getId()))
				.map(a -> {
					FAIRProvenance prov = FAIRProvenance.builder().build();
					Map <String, String> metadata = Maps.newLinkedHashMap();
					Optional
							.ofNullable(a.getProvenance() )
							.ifPresent(p ->
								metadata.putAll(Splitter.on(',')
										.omitEmptyStrings()
										.withKeyValueSeparator('=')
										.split(p)));

					metadata.put("prov:atTime", new SimpleDateFormat(dateFormat).format( a.getEventTime()));
					metadata.put("prov:event", a.getEvent());
//					fairRepo.findById(a.getEntityId()).ifPresent(d -> {
					String [] tokens = a.getEntity().split(":", 2);
					metadata.put("prov:entity", tokens[0]);
					metadata.put("prov:value", tokens[1].split("/")[StringUtils.countOccurrencesOf(tokens[1], "/")]);
					metadata.put("prov:wasGeneratedBy",
							metadata.containsKey("prov:wasGeneratedBy")
							? metadata.get("prov:wasGeneratedBy") :
							a.getUser().getDlsUser());
					String [] parts = tokens[1].split("/");
					try {
					switch (FAIRServiceHelper.FAIRNodes.valueOf(tokens[0])) {
						case REPOSITORY:
							prov.add(linkTo(WebMvcLinkBuilder.methodOn(FAIRController.class).getRepository(apiKey, dlsKey, parts[0])).withRel("entity"));
							break;
						case CATALOG:
							prov.add(linkTo(WebMvcLinkBuilder.methodOn(FAIRController.class).getCatalog(apiKey, dlsKey, parts[0], parts[1])).withRel("entity"));
							break;
						case DATASET:
							prov.add(linkTo(WebMvcLinkBuilder.methodOn(FAIRController.class).getDataset(apiKey, dlsKey, parts[0], parts[1], parts[2])).withRel("entity"));
							break;
						case DISTRIBUTION:
							prov.add(linkTo(WebMvcLinkBuilder.methodOn(FAIRController.class).getDistribution(apiKey, dlsKey, parts[0], parts[1], parts[2], parts[3])).withRel("entity"));
							break;
					}
					} catch (Exception e) {
						log.error(e.getMessage());
					}

					/*});*/
					prov.setProvenance(metadata);
					return prov;

				})
				.collectList().block();

		if(provenanceList.isEmpty()) throw new DlsNotFoundException();
		return provenanceList;
	}

	public List<FAIRPermission> getPermission(String apiKey, String dlsKey, String repoId, String catalogId, String datasetId, String distId)
			throws DlsSecurityException, DlsPrivacyException, DlsNotFoundException {

		UserVO user = uservice.authorize(apiKey, dlsKey);
		helper.validateFAIRHierarchy(repoId, catalogId, datasetId, distId);
		String dirStr = Joiner.on('/').skipNulls().join(repoId, catalogId, datasetId, distId);
		DirectoryVO dir = Optional.
				ofNullable(fairRepo.findByDirectoryIgnoreCaseAndDeletedAndTenantId(dirStr, true, user.getTenant().getId()))
				.orElseThrow(DlsNotFoundException::new);
		// accessor must be creator
		if(!dir.getCreatedBy().getId().equals(user.getId())) {
			throw new DlsPrivacyException();
		}
		
		List<PermissionVO> permission = dir.getPermission();
		List<FAIRPermission> permissions = 
				Optional.ofNullable(permission)
				        .map(list -> getFAIRPermission(list)
				        		.collectList().block() ) 														   
				        .orElseThrow(DlsNotFoundException::new);
		if(permissions.isEmpty()) throw new DlsNotFoundException();
		
		AuditVO audit = AuditVO.builder()
				.success(true)
				.eventTime(Timestamp.from(Instant.now()))
				.entityId(dir.getId())
				.entity(dir.getEnforcementType().concat(":").concat(dir.getDirectory()))
				.user(user)
				.event(AuditEvent.PERMISSION_READ.name())
				.build();
		auditRepo.save(audit);
		return permissions;
	}
	
	private Flux<FAIRPermission> getFAIRPermission(List<PermissionVO> p) {
		return Flux.fromIterable(p)
				.groupBy(PermissionVO::getAction)
				.flatMap(f -> {
					String action = f.key();
					return f.map(PermissionVO::getPermittedUser)
							.buffer()
							.map(userIds -> {
								List<String> users = userRepo.findAllById(userIds).stream().map(UserVO::getDlsUser)
										.collect(Collectors.toList());
								return FAIRPermission.builder().action(action).users(users).build();
							});
				});
	}
}
