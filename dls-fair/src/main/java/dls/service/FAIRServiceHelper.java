package dls.service;

import com.diffplug.common.base.Errors;
import com.google.common.base.Joiner;
import com.google.common.base.Splitter;
import com.google.common.base.Strings;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import dls.bean.*;
import dls.exception.DlsNotFoundException;
import dls.exception.DlsPrivacyException;
import dls.exception.DlsSecurityException;
import dls.exception.DlsValidationException;
import dls.repo.*;
import dls.util.BeanValidationConstraint;
import dls.vo.*;
import dls.web.FAIRController;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.hateoas.server.mvc.WebMvcLinkBuilder;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static dls.service.FAIRServiceHelper.FAIRNodes.*;
import static dls.service.FileServiceHelper.DLS_;
import static dls.service.MetaDataSchemaService.IRI_TYPE;
import static dls.util.BeanValidationConstraint.URL_REGEX;
import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.linkTo;
import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.methodOn;
import static org.springframework.http.HttpStatus.CONFLICT;
import static org.springframework.http.HttpStatus.CREATED;


@Component
@Slf4j
public class FAIRServiceHelper {

	private static final String IDENTIFIER = "dct:identifier";
	public static final String DCT_IDENTIFIER = "dct:identifier";
	@Autowired private UserService uservice;
	@Autowired private Environment ev;
	@Autowired private DirectoryService directoryService;
	@Autowired private DlsServiceHelper dlsServiceHelper;
	@Autowired private FAIRRepo fairRepo;
	@Autowired private FileRepo fileRepo;
	@Autowired private FileMetaRepo fileMetaRepo;
	@Autowired private PermissionRepo permissionRepo;
	@Autowired private MetaDataSchemaRepo metaDataSchemaRepo;
	@Autowired private DirectoryServiceHelper directoryServiceHelper;
	@Autowired private AuditRepo auditRepo;
	@PersistenceContext private EntityManager em;
	@Value("${dls.ontology.prefixes}") List <String> knownPrefixes;

	List<DlsResponse> create(Flux <DirectoryVO> flux, final String key) {

		List<DlsResponse> responses = Lists.newArrayList();
		flux
				.onErrorContinue((e, o) -> responses.add(DlsResponse.builder().code(HttpStatus.CONFLICT.value())
					.messages(Set.of(e.getMessage())).value(((FAIRDescriptor)o).getIdentifier()).key(DCT_IDENTIFIER).build()))
				.groupBy(this::checkDirectoryUnique)
				.subscribe(f -> {
					if(f.key()) {
						f.map(dlsServiceHelper::saveDirectoryAndPermission)
									.doOnNext(d -> responses.add(DlsResponse.builder().code(HttpStatus.CREATED.value())
											.messages(Set.of("CREATED")).value(d.getDirectory()).key(key).build()))
								.subscribe(d -> {
									AuditVO audit = AuditVO.builder()
											.success(true)
											.eventTime(Timestamp.from(Instant.now()))
											.entityId(d.getId())
											.entity(d.getEnforcementType().concat(":").concat(d.getDirectory()))
											.user(d.getCreatedBy())
											.event(AuditEvent.CREATED.name())
											.build();
									auditRepo.save(audit);
								});
					} else {
						f.map(d -> responses.add(DlsResponse.builder().code(HttpStatus.CONFLICT.value())
								.messages(Set.of("Already Exists")).value(d.getDirectory()).key(key).build())).subscribe();
					}
				});

		return responses;
	}

	boolean checkDirectoryUnique(DirectoryVO d) {
		return null ==  fairRepo.findByDirectoryIgnoreCaseAndDeleted(d.getDirectory(),
				true);
	}

	DirectoryVO toDirectoryVO(FAIRDescriptor descriptor, UserVO userVO) {

		List<DirectoryMetaVO> rules = Lists.newArrayList();
		String enforcementType = null;
		if(descriptor instanceof FAIRRepositoryDescriptor) {
			FAIRRepositoryDescriptor repo = (FAIRRepositoryDescriptor) descriptor;
			rules.addAll(fromRepo(repo, userVO));
			enforcementType = FAIRNodes.REPOSITORY.name();
		} else if(descriptor instanceof FAIRCatalogDescriptor) {
			FAIRCatalogDescriptor repo = (FAIRCatalogDescriptor) descriptor;
			rules.addAll(fromCatalog(repo, userVO));
			enforcementType = FAIRNodes.CATALOG.name();
		} else if(descriptor instanceof FAIRDatasetDescriptor) {
			FAIRDatasetDescriptor repo = (FAIRDatasetDescriptor) descriptor;
			rules.addAll(fromDataset(repo, userVO));
			enforcementType = FAIRNodes.DATASET.name();
		} else if(descriptor instanceof FAIRDistributionDescriptor) {
			FAIRDistributionDescriptor repo = (FAIRDistributionDescriptor) descriptor;
			rules.addAll(fromDistribution(repo, userVO));
			enforcementType = FAIRNodes.DISTRIBUTION.name();
		}

		List<FAIRPermission> list = Optional.ofNullable(descriptor.getPermissions()).orElse(Lists.newArrayList());
		if(list.stream().flatMap(p -> p.getUsers().stream()).anyMatch(u -> u.equalsIgnoreCase(userVO.getDlsUser()))) {
			throw new DataIntegrityViolationException("Permission can not be set for the directory creator");
		}

		list.add(FAIRPermission.builder().action("RWD")
				.users(Lists.newArrayList(userVO.getDlsUser())).build());

		List<PermissionVO> vos = Flux.fromIterable(list)
				.map(fp -> Permission.builder().action(fp.getAction()).users(fp.getUsers()).build())
				.flatMap(p -> directoryServiceHelper.buildPermissionPerUser(p, userVO)
				).collect(Collectors.toList()).block();

		return DirectoryVO.builder()
				.directory(descriptor.getIdentifier())
				.createdOn(Timestamp.from(Instant.now()))
				.deletedOn(Timestamp.from(Instant.now()))
				.enforcementType(enforcementType)
				.deleted(true)
				.permission(Lists.newArrayList())
				.directoryMetaVOList(rules)
				.createdBy(userVO)
				.tenant(userVO.getTenant())
				.permission(vos)
				.build();
	}

	List <DirectoryMetaVO> fromRepo(FAIRRepositoryDescriptor repo, UserVO userVO) {

		List<DirectoryMetaVO> rules = Lists.newArrayList();
		rules.add(DirectoryMetaVO.builder().user(userVO).name("dct:description").value(repo.getDescription()).type("TEXT").build());
		rules.add(DirectoryMetaVO.builder().user(userVO).name("dct:title").value(repo.getTitle()).type("TEXT").build());
		rules.add(DirectoryMetaVO.builder().user(userVO).name("dct:publisher").value(repo.getPublisher()).type("TEXT").build());
		if(null !=repo.getLanguages())
		rules.add(DirectoryMetaVO.builder().user(userVO).name("dct:languages")
				.value(Joiner.on(',').skipNulls().join(repo.getLanguages()))
				.type("TEXT").build());
		rules.add(DirectoryMetaVO.builder().user(userVO).name("dct:license").value(repo.getLicense()).type("TEXT").build());
		rules.add(DirectoryMetaVO.builder().user(userVO).name("dct:subject").value(repo.getSubject()).type("TEXT").build());
		rules.add(DirectoryMetaVO.builder().user(userVO).name("dct:alternative").value(repo.getAlternative()).type("TEXT").build());
		rules.add(DirectoryMetaVO.builder().user(userVO).name("dct:rights").value(repo.getRights()).type("TEXT").build());
		rules.add(DirectoryMetaVO.builder().user(userVO).name("r3d:institution").value(repo.getInstitution()).type("TEXT").build());
		rules.add(DirectoryMetaVO.builder().user(userVO).name("dct:hasVersion").value(repo.getHasVersion()).type("TEXT").build());
		checkAndAddCustomMetadata(repo.getCustom(), rules, userVO);
		return rules;
	}

	List <DirectoryMetaVO> fromCatalog(FAIRCatalogDescriptor repo, UserVO userVO) {

		List<DirectoryMetaVO> rules = Lists.newArrayList();
		rules.add(DirectoryMetaVO.builder().user(userVO).name("dct:description").value(repo.getDescription()).type("TEXT").build());
		rules.add(DirectoryMetaVO.builder().user(userVO).name("dct:title").value(repo.getTitle()).type("TEXT").build());
		rules.add(DirectoryMetaVO.builder().user(userVO).name("dct:publisher").value(repo.getPublisher()).type("TEXT").build());
		if(null !=repo.getLanguages())
		rules.add(DirectoryMetaVO.builder().user(userVO).name("dct:languages")
				.value(Joiner.on(',').skipNulls().join(repo.getLanguages())).type("TEXT").build());
		rules.add(DirectoryMetaVO.builder().user(userVO).name("dct:license").value(repo.getLicense()).type("TEXT").build());
		rules.add(DirectoryMetaVO.builder().user(userVO).name("dcat:themeTaxonomy").value(repo.getThemeTaxonomy()).type("TEXT").build());
		rules.add(DirectoryMetaVO.builder().user(userVO).name("foaf:homepage").value(repo.getHomepage()).type("TEXT").build());
		rules.add(DirectoryMetaVO.builder().user(userVO).name("dct:rights").value(repo.getRights()).type("TEXT").build());
		rules.add(DirectoryMetaVO.builder().user(userVO).name("dct:hasVersion").value(repo.getHasVersion()).type("TEXT").build());
		checkAndAddCustomMetadata(repo.getCustom(), rules, userVO);
		return rules;
	}

	List <DirectoryMetaVO> fromDataset(FAIRDatasetDescriptor repo, UserVO userVO) {

		List<DirectoryMetaVO> rules = Lists.newArrayList();
		rules.add(DirectoryMetaVO.builder().user(userVO).name("dct:description").value(repo.getDescription()).type("TEXT").build());
		rules.add(DirectoryMetaVO.builder().user(userVO).name("dct:title").value(repo.getTitle()).type("TEXT").build());
		rules.add(DirectoryMetaVO.builder().user(userVO).name("dct:publisher").value(repo.getPublisher()).type("TEXT").build());
		if(null !=repo.getLanguages())
		rules.add(DirectoryMetaVO.builder().user(userVO).name("dct:languages")
				.value(Joiner.on(',').skipNulls().join(repo.getLanguages())).type("TEXT").build());
		rules.add(DirectoryMetaVO.builder().user(userVO).name("dct:license").value(repo.getLicense()).type("TEXT").build());
		rules.add(DirectoryMetaVO.builder().user(userVO).name("dct:rights").value(repo.getRights()).type("TEXT").build());
		if(null !=repo.getTheme())
		rules.add(DirectoryMetaVO.builder().user(userVO).name("dcat:theme").value(Joiner.on(',').skipNulls().join(repo.getTheme()))
				.type("TEXT").build());
		rules.add(DirectoryMetaVO.builder().user(userVO).name("dcat:contactPoint").value(repo.getContactPoint()).type("TEXT").build());
		if(null !=repo.getKeywords())
		rules.add(DirectoryMetaVO.builder().user(userVO).name("dcat:keywords").value(Joiner.on(',').skipNulls().join(repo.getKeywords()))
				.type("TEXT").build());
		rules.add(DirectoryMetaVO.builder().user(userVO).name("dcat:landingPage").value(repo.getLandingPage()).type("TEXT").build());
		rules.add(DirectoryMetaVO.builder().user(userVO).name("dct:hasVersion").value(repo.getHasVersion()).type("TEXT").build());
		checkAndAddCustomMetadata(repo.getCustom(), rules, userVO);
		return rules;
	}


	List <DirectoryMetaVO> fromDistribution(FAIRDistributionDescriptor dist, UserVO userVO) {

		List<DirectoryMetaVO> rules = Lists.newArrayList();

		rules.add(DirectoryMetaVO.builder().user(userVO).name("dct:title").type("TEXT").value(dist.getTitle()).build());
		rules.add(DirectoryMetaVO.builder().user(userVO).name("dct:license").type("TEXT").value(dist.getLicense()).build());
		rules.add(DirectoryMetaVO.builder().user(userVO).name("dct:hasVersion").type("TEXT").value(dist.getHasVersion()).build());
		rules.add(DirectoryMetaVO.builder().user(userVO).name("dct:rights").type("TEXT").value(dist.getRights()).build());
		rules.add(DirectoryMetaVO.builder().user(userVO).name("dct:description").type("TEXT").value(dist.getDescription()).build());
		rules.add(DirectoryMetaVO.builder().user(userVO).name("dcat:accessURL").value(dist.getAccessURL()).build());
		rules.add(DirectoryMetaVO.builder().user(userVO).name("dcat:mediaType").type("TEXT").value(dist.getMediaType()).build());
		rules.add(DirectoryMetaVO.builder().user(userVO).name("dcat:format").type("TEXT").value(dist.getFormat()).build());
		if(null != dist.getByteSize())
		rules.add(DirectoryMetaVO.builder().user(userVO).name("dcat:byteSize").type("NUMERIC").value_numeric(Double.valueOf(dist.getByteSize())).build());

		Map <String, String> result = Maps.newHashMap();
		metaDataSchemaRepo.findByTenantIdAndType(userVO.getTenant().getId(), MetaDataSchemaService.IRI_TYPE)
				.forEach(v-> result.put(v.getDescription(), v.getName()));

		Map<String, String> custom = dist.getCustom();
		if(null != custom && !custom.isEmpty()) {
			for(String key : custom.keySet()) {
				if(!key.matches("\\w+:\\w+")) {
					throw new DlsValidationException("Invalid format of custom metadata - ".concat(key));
				}
				String prefix = key.split(":")[0];
				if(!result.containsValue(prefix) && knownPrefixes.stream().noneMatch(p -> p.equalsIgnoreCase(prefix))) {
					throw new DataIntegrityViolationException("Prefix is not registered in DLS - ".concat(prefix));
				}
				rules.add(DirectoryMetaVO.builder().user(userVO)
						.name(key)
						.value(custom.get(key))
						.build());
			}
		}
		return rules;
	}

	void checkAndAddCustomMetadata(/*FAIRDescriptor repo,*/ Map<String, String> custom , List<DirectoryMetaVO> rules, UserVO userVO) {

		Map <String, String> result = Maps.newHashMap();
		metaDataSchemaRepo.findByTenantIdAndType(userVO.getTenant().getId(), MetaDataSchemaService.IRI_TYPE)
				.forEach(v-> result.put(v.getDescription(), v.getName()));

//		Map<String, String> custom = repo.getCustom();
		if(null != custom && !custom.isEmpty()) {
			for(String key : custom.keySet()) {
				if(!key.matches("\\w+:\\w+")) {
					throw new DataIntegrityViolationException("Invalid format of custom metadata - ".concat(key));
				}
				String prefix = key.split(":")[0];
				if(!result.containsValue(prefix)  && knownPrefixes.stream().noneMatch(p -> p.equalsIgnoreCase(prefix))) {
					throw new DataIntegrityViolationException("Prefix is not registered in DLS - ".concat(prefix));
				}
				rules.add(DirectoryMetaVO.builder().user(userVO)
						.name(key)
						.value_mandatory(false)
						.value(custom.get(key))
						.type("TEXT").build());
			}
		}

	}

	private void checkReadAccess(UserVO loggedInAs, DirectoryVO vo) {

		boolean hasReadAccess = vo.getPermission().stream()
				.filter(p -> p.getPermittedUser().equals(loggedInAs.getId()))
				.anyMatch(p -> p.getAction().contains("R"));
		if(!hasReadAccess) throw new DataIntegrityViolationException("no.read.privilege");
	}

	void checkWriteAccess(UserVO loggedInAs, DirectoryVO vo) {

		boolean hasReadAccess = vo.getPermission().stream()
				.filter(p -> p.getPermittedUser().equals(loggedInAs.getId()))
				.anyMatch(p -> p.getAction().contains("W"));
		if(!hasReadAccess) throw new DataIntegrityViolationException("no.write.privilege");
	}

	void checkDeleteAccess(UserVO loggedInAs, DirectoryVO vo) {

		boolean hasReadAccess = vo.getPermission().stream()
				.filter(p -> p.getPermittedUser().equals(loggedInAs.getId()))
				.anyMatch(p -> p.getAction().contains("D"));
		if(!hasReadAccess) throw new DataIntegrityViolationException("no.delete.privilege");
	}

	FAIRRepository buildRepoFromDirectory(String apiKey, String dlsKey, String repoId, DirectoryVO vo) throws DlsSecurityException, DlsPrivacyException, DlsNotFoundException {


		UserVO loggedInAs = uservice.authorize(apiKey, dlsKey);
		checkReadAccess(loggedInAs, vo);

		FAIRRepository repo = FAIRRepository.builder().repository(Maps.newHashMap()).build();
		vo.getDirectoryMetaVOList().forEach(v -> {
			if(null != v.getValue())
			repo.getRepository().put(v.getName(), v.getValue());
		});
		repo.getRepository().put("dct:identifier", vo.getDirectory());
		fairRepo.findByParent(vo.getId())
				.forEach(d -> {
					String[] split = d.getDirectory().split("/");

					try {
						repo.add(linkTo(WebMvcLinkBuilder.methodOn(FAIRController.class).getCatalog(apiKey, dlsKey, repoId, split[split.length-1] )).withRel("catalogs"));
					} catch (DlsPrivacyException | DlsSecurityException | DlsNotFoundException e) {
						log.error(e.getMessage());
					}
				});

		org.springframework.hateoas.Link selfLink = linkTo(WebMvcLinkBuilder.methodOn(FAIRController.class).getRepository(apiKey, dlsKey, repoId)).withSelfRel();
		repo.add(selfLink);

		AuditVO audit = AuditVO.builder()
				.success(true)
				.eventTime(Timestamp.from(Instant.now()))
				.entity(REPOSITORY.name().concat(":").concat(vo.getDirectory()))
				.entityId(vo.getId())
				.user(loggedInAs)
				.event(AuditEvent.READ.name())
				.build();
		auditRepo.save(audit);
		return repo;
	}


	FAIRCatalog buildCatalogFromDirectory(String apiKey, String dlsKey, String repoId, String catId, DirectoryVO vo) throws DlsSecurityException, DlsPrivacyException, DlsNotFoundException {


		UserVO loggedInAs = uservice.authorize(apiKey, dlsKey);
		checkReadAccess(loggedInAs, vo);
		FAIRCatalog cat = FAIRCatalog.builder().catalog(Maps.newHashMap()).build();
		vo.getDirectoryMetaVOList().forEach(v -> {
			if(null != v.getValue())
			cat.getCatalog().put(v.getName(), v.getValue());
		});
		String parts [] = vo.getDirectory().split("/");
		cat.getCatalog().put("dct:identifier", parts[parts.length-1]);
		fairRepo.findByParent(vo.getId())
				.forEach(d -> {
					String[] split = d.getDirectory().split("/");

					try {
						cat.add(linkTo(WebMvcLinkBuilder.methodOn(FAIRController.class).getDataset(apiKey, dlsKey, repoId, catId, split[split.length-1] )).withRel("datasets"));
					} catch (DlsPrivacyException | DlsSecurityException | DlsNotFoundException e) {
						log.error(e.getMessage());
					}
				});

		org.springframework.hateoas.Link selfLink = linkTo(WebMvcLinkBuilder.methodOn(FAIRController.class).getCatalog(apiKey, dlsKey, repoId, catId)).withSelfRel();
		cat.add(selfLink);
		AuditVO audit = AuditVO.builder()
				.success(true)
				.eventTime(Timestamp.from(Instant.now()))
				.entity(CATALOG.name().concat(":").concat(vo.getDirectory()))
				.user(loggedInAs)
				.entityId(vo.getId())
				.event(AuditEvent.READ.name())
				.build();
		auditRepo.save(audit);
		return cat;
	}

	FAIRDataset buildDatasetFromDirectory(String apiKey, String dlsKey, String repoId, String catId, String datasetId, DirectoryVO vo) throws DlsSecurityException, DlsPrivacyException, DlsNotFoundException {


		UserVO loggedInAs = uservice.authorize(apiKey, dlsKey);
		checkReadAccess(loggedInAs, vo);
		FAIRDataset ds = FAIRDataset.builder().dataset(Maps.newHashMap()).build();
		vo.getDirectoryMetaVOList().forEach(v -> {
			if(null != v.getValue())
			ds.getDataset().put(v.getName(), v.getValue());
		});
		String parts [] = vo.getDirectory().split("/");
		ds.getDataset().put("dct:identifier", parts[parts.length-1]);

		fairRepo.findByParent(vo.getId())
				.forEach(d -> {
					String[] split = d.getDirectory().split("/");

					try {
						ds.add(linkTo(WebMvcLinkBuilder.methodOn(FAIRController.class).getDistribution(apiKey, dlsKey, repoId, catId, datasetId, split[split.length-1] )).withRel("distributions"));
					} catch (DlsPrivacyException | DlsSecurityException | DlsNotFoundException e) {
						log.error(e.getMessage());
					}
				});

		org.springframework.hateoas.Link selfLink = linkTo(WebMvcLinkBuilder.methodOn(FAIRController.class).getDataset(apiKey, dlsKey, repoId, catId, datasetId)).withSelfRel();
		ds.add(selfLink);
		AuditVO audit = AuditVO.builder()
				.success(true)
				.eventTime(Timestamp.from(Instant.now()))
				.entity(DATASET.name().concat(":").concat(vo.getDirectory()))
				.user(loggedInAs)
				.entityId(vo.getId())
				.event(AuditEvent.READ.name())
				.build();
		auditRepo.save(audit);
		return ds;
	}

	FAIRDistribution buildDistributionFromDirectory(String apiKey, String dlsKey, String repoId, String catId, String datasetId, String distId, DirectoryVO vo) throws DlsSecurityException, DlsPrivacyException, DlsNotFoundException {


		UserVO loggedInAs = uservice.authorize(apiKey, dlsKey);
		checkReadAccess(loggedInAs, vo);
		FAIRDistribution dist = FAIRDistribution.builder().distribution(Maps.newHashMap()).build();
		vo.getDirectoryMetaVOList().forEach(v -> {
			if(null != v.getValue())
			dist.getDistribution().put(v.getName(), null != v.getValue_numeric() ? String.format("%1$.0f", v.getValue_numeric())  : v.getValue() );
		});
		String parts [] = vo.getDirectory().split("/");
		dist.getDistribution().put("dct:identifier", parts[parts.length-1]);
		fairRepo.findByParent(vo.getId())
				.forEach(d -> {
					String[] split = d.getDirectory().split("/");

				});

		org.springframework.hateoas.Link selfLink = linkTo(WebMvcLinkBuilder.methodOn(FAIRController.class).getDistribution(apiKey, dlsKey, repoId, catId, datasetId, distId)).withSelfRel();
		dist.add(selfLink);
		AuditVO audit = AuditVO.builder()
				.success(true)
				.eventTime(Timestamp.from(Instant.now()))
				.entity(DISTRIBUTION.name().concat(":").concat(vo.getDirectory()))
				.user(loggedInAs)
				.entityId(vo.getId())
				.event(AuditEvent.READ.name())
				.build();
		auditRepo.save(audit);
		return dist;
	}

	FAIRRepository buildRepoFromTenant(String apiKey, String dlsKey, UserVO user) throws DlsSecurityException, DlsPrivacyException, DlsNotFoundException {


//		UserVO loggedInAs = uservice.authorize(apiKey, dlsKey);
//		checkReadAccess(loggedInAs, vo);

		FAIRRepository repo = FAIRRepository.builder().repository(Maps.newHashMap()).build();
//		vo.getDirectoryMetaVOList().forEach(v -> {
//			repo.getRepository().put(v.getName(), v.getValue());
//		});
		String repoId = user.getTenant().getTcupUser();
		repo.getRepository().put("dct:identifier", repoId);


		try {
			repo.add(linkTo(WebMvcLinkBuilder.methodOn(FAIRController.class).getCatalog(apiKey, dlsKey, repoId, user.getDlsUser() )).withRel("catalogs"));
		} catch (DlsPrivacyException | DlsSecurityException | DlsNotFoundException e) {
			log.error(e.getMessage());
		}

		org.springframework.hateoas.Link selfLink = linkTo(WebMvcLinkBuilder.methodOn(FAIRController.class).getRepository(apiKey, dlsKey, repoId)).withSelfRel();
		repo.add(selfLink);

		return repo;
	}

	FAIRCatalog buildCatalogFromUser(String apiKey, String dlsKey, UserVO user) throws DlsSecurityException, DlsPrivacyException, DlsNotFoundException {


//		UserVO loggedInAs = uservice.authorize(apiKey, dlsKey);
//		checkReadAccess(loggedInAs, vo);

		FAIRCatalog repo = FAIRCatalog.builder().catalog(Maps.newHashMap()).build();
//		vo.getDirectoryMetaVOList().forEach(v -> {
//			repo.getRepository().put(v.getName(), v.getValue());
//		});
		String catId = user.getDlsUser();
		repo.getCatalog().put("dct:identifier", catId);


//		try {
//			repo.add(linkTo(methodOn(FAIRController.class).getCatalog(apiKey, dlsKey, repoId, user.getDlsUser() )).withRel("catalogs"));
//		} catch (DlsPrivacyException | DlsSecurityException | DlsNotFoundException e) {
//			e.printStackTrace();
//		}

		org.springframework.hateoas.Link selfLink = linkTo(WebMvcLinkBuilder.methodOn(FAIRController.class)
				.getCatalog(apiKey, dlsKey, user.getTenant().getTcupUser(), catId  )).withSelfRel();

		repo.add(selfLink);

		return repo;
	}

	FAIRDataset buildDatasetFromRegularDirectory(String apiKey, String dlsKey, DirectoryVO vo) throws DlsSecurityException, DlsPrivacyException, DlsNotFoundException {

		String repoId = vo.getCreatedBy().getTenant().getTcupUser();
		String catId = vo.getCreatedBy().getDlsUser();
		String datasetId = vo.getId().toString();

		UserVO loggedInAs = uservice.authorize(apiKey, dlsKey);
		checkReadAccess(loggedInAs, vo);

		FAIRDataset ds = FAIRDataset.builder().dataset(Maps.newHashMap()).build();
//		vo.getDirectoryMetaVOList().forEach(v -> {
//			ds.getDataset().put(v.getName(), v.getValue());
//		});
		ds.getDataset().put("dct:identifier", vo.getId().toString());
		ds.getDataset().put("dct:description", vo.getDirectory());

		vo.getFiles()
				.forEach(f -> {


					try {
						ds.add(linkTo(WebMvcLinkBuilder.methodOn(FAIRController.class).getDistribution(apiKey, dlsKey, repoId, catId, datasetId, f.getId().toString() )).withRel("datasets"));
					} catch (DlsPrivacyException | DlsSecurityException | DlsNotFoundException e) {
						log.error(e.getMessage());
					}
				});

		org.springframework.hateoas.Link selfLink = linkTo(WebMvcLinkBuilder.methodOn(FAIRController.class)
				.getDataset(apiKey, dlsKey, repoId, catId, datasetId)).withSelfRel();
		ds.add(selfLink);

		return ds;
	}

	String parseAndBuildSQLFromMetadata(List<String> metadata, String directoryExpression, Set<String>  includes, Boolean includeDirectory) {

		String query = "SELECT * FROM directory d ";

		if(null != metadata && !metadata.isEmpty()) {
			query = query.concat(" JOIN directory_meta dm ON d.id = dm.directory_id ");
		}

		String whereClause = null;

		if(null != includes && !includes.isEmpty()) {
			if(null == includeDirectory || !includeDirectory) {
				whereClause = " d.enforcement_type IN ( " + Joiner.on(',').skipNulls()
						.join(includes.stream().map(StringUtils::quote).collect(Collectors.toList())) + " ) ";
			}
			if(null != includeDirectory && includeDirectory) {
				whereClause = Joiner.on(" OR ").skipNulls().join(whereClause, " d.enforcement_type IS NULL ");
			}
		}

//		String directoryClause = null;
//		if(null != directoryExpression) {
//			directoryClause = " d.parent IN (SELECT d1.id FROM directory d1 where "+ directoryExpression +") ";
//		}
//		whereClause = Joiner.on(" AND ").skipNulls().join(whereClause, directoryClause);
		whereClause = Joiner.on(" AND ").skipNulls().join(whereClause, directoryExpression);




		String metadataClause =  Optional.ofNullable(metadata)
				.map(list -> {
					List<String> queryList = Flux.fromIterable(list)
							.map(q -> q.replace('*','%'))
							.map(this::parseMetadataQueryToSQL)
							.collectList().block();
					return	Joiner.on(" OR ").skipNulls().join(queryList);

		}).orElse(null);

		whereClause = Joiner.on(" AND ").skipNulls().join(whereClause, metadataClause);

		query =  Joiner.on(" WHERE ").skipNulls().join(query, whereClause);

		log.info(query);

		return query;
	}

	private String parseMetadataQueryToSQL (String query) {

		if(query.matches("[^\\s,<>!=]+[ ]*[!=<>]+[ ]*'.+'")) {
			List<String> split = Splitter.onPattern("(>=|<=|>|<|!=|=)").trimResults().splitToList(query);
			String l = split.get(0);
			String r = split.get(1);
			String o = query.replaceAll(r, "").replaceAll(l, "").trim();
			String valueSql = Errors.suppress().getWithDefault(() -> {
				String d = r.replaceAll("'","");
				Double.parseDouble(d);
				return "AND (value like "
						.concat(r)
						.concat(" OR value_numeric ")
						.concat(o)
						.concat(d)
						.concat(")");
			}, "AND value like ".concat(r).concat(")"));

			return " (name like '".concat(l).concat("' ").concat(valueSql);

		} else if(query.matches("'.+'")) {
			return " value like ".concat(query);
		} else {
			return " name like '".concat(query).concat("'");
		}
	}

	boolean isDistributionValidWithDLS(FAIRDistributionDescriptor distribution, UserVO user, List<DlsResponse> responses) {

		String accessURL = distribution.getAccessURL();
		boolean external = accessURL.contains(BeanValidationConstraint.URL_REGEX);

		FileVO fileVO = fileRepo.findByFsPathAndDeleted(accessURL, false);

		boolean success = true;
		String errMessage = "";

		if(null == fileVO && external) {
			createNewExternalFile(distribution, user);
		} else if(null == fileVO) {
			errMessage ="accessURL is not an existing file in DLS";
			success = false;
		}

		else if(!external && distribution.getByteSize() != fileVO.getSizeInByte()) {
			errMessage ="Size of the distribution does not match with DLS file. Remove attribute 'dcat:byteSize'";
			success = false;
		}

		if(!success) {
			responses.add(DlsResponse.builder()
					.code(HttpStatus.CONFLICT.value())
					.key("dcat:accessURL")
					.value(distribution.getAccessURL())
					.messages(Set.of(errMessage))
					.build());
		}
		return success;
	}

	private void createNewExternalFile(FAIRDistributionDescriptor distribution, UserVO user) {
		FileVO vo = FileVO.builder()
				.fileName(distribution.getAccessURL())
				.user(user)
				.uploaded(true)
				.fsPath(distribution.getAccessURL())
				.external(true)
				.deleted(false)
				.createdOn(Timestamp.from(Instant.now()))
				.build();
		fileRepo.save(vo);
	}


	void validateFAIRHierarchy(String repoId, String catalogId, String datasetId, String distId) {

		if(null != distId && (null == datasetId || null == catalogId || null == repoId)) {
			throw new DlsValidationException("Repository, catalog and dataset identifiers need to be mentioned for this distribution " + distId);
		} else if(null != datasetId && (null == catalogId || null == repoId)) {
			throw new DlsValidationException("Both repository and catalog identifiers need to be mentioned for this dataset " + datasetId);
		} else if(null != catalogId && null == repoId) {
			throw new DlsValidationException("Repository identifier needs to be mentioned for this catalog " + catalogId);
		}
	}

	public FAIRDistribution buildDistributionFromFile(String apiKey, String dlsKey, DirectoryVO directoryVO, FileVO file) throws DlsSecurityException, DlsPrivacyException, DlsNotFoundException {

		String rId = directoryVO.getCreatedBy().getTenant().getTcupUser();
		String cId = directoryVO.getCreatedBy().getDlsUser();
		String dsId = directoryVO.getId().toString();

		UserVO loggedInAs = uservice.authorize(apiKey, dlsKey);
		DirectoryVO vo = file.getDirectory();
		checkReadAccess(loggedInAs, vo);
		FAIRDistribution dist = FAIRDistribution.builder().distribution(Maps.newHashMap()).build();

		Flux.fromIterable(file.getMeta())
				.filter(m -> ! m.getName().startsWith(FileServiceHelper.DLS_))
				.doOnNext(m -> m.setName(FileServiceHelper.DLS_.concat(m.getName())))
				.subscribe(v -> dist.getDistribution().put(v.getName(), v.getValue()));

		String distId = file.getId().toString();
		dist.getDistribution().put("dct:identifier", distId);
		dist.getDistribution().put("dcat:accessURL", file.getFsPath());
		dist.getDistribution().put("dcat:byteSize", file.getSizeInByte().toString());
		dist.getDistribution().put("dct:hasVersion", file.getSavepoint());



		org.springframework.hateoas.Link selfLink = linkTo(WebMvcLinkBuilder.methodOn(FAIRController.class)
				.getDistribution(apiKey, dlsKey, rId, cId, dsId, distId)).withSelfRel();
		dist.add(selfLink);

		return dist;

	}

	public String buildDirectoryLikeExpression(final String repoId, final String catalogId, final String datasetId) {

		if(null == repoId && null == catalogId && null == datasetId) {
			return null;
		}
		String expression = ((null == repoId) ? "%" : repoId).concat("/")
				.concat((null == catalogId) ? "%" : catalogId) ;

		String datasetQ = (null != datasetId) ? datasetId.concat("/%") : (null == catalogId ? null : "%");

		expression = Joiner.on("/").skipNulls().join(expression, datasetQ);

		Set <String> expressions = Sets.newHashSet(expression);

//		expressions.add(StringUtils.trimTrailingCharacter(StringUtils.trimTrailingCharacter(expression, '%'), '/'));

		for (int i = expression.split("/").length; i < 4 ; i++) {
			expression = expression.concat("/%");
			expressions.add(expression);
		}
		return Joiner.on(" OR ").skipNulls().join(expressions.stream().map(e -> "d.directory like '"+ e +"'").collect(Collectors.toList()));
	}



	public enum FAIRNodes {
		REPOSITORY, CATALOG, DATASET, DISTRIBUTION, NONE;
	}

}
