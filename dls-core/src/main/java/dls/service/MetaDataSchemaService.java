package dls.service;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import dls.bean.DlsResponse;
import dls.bean.MetaSchema;
import dls.bean.Relation;
import dls.exception.DlsNotFoundException;
import dls.exception.DlsPrivacyException;
import dls.exception.DlsSecurityException;
import dls.repo.MetaDataSchemaRepo;
import dls.repo.RelationRepo;
import dls.repo.TenantRepo;
import dls.vo.MetaSchemaVO;
import dls.vo.RelationVO;
import dls.vo.TenantVO;
import dls.vo.UserVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Example;
import org.springframework.data.domain.ExampleMatcher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import jakarta.validation.Valid;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

import static dls.exception.GlobalExceptionHandler.*;

@Service
@Slf4j
public class MetaDataSchemaService {

	public static final String IRI_TYPE = "IRI";
	@Autowired private UserService uservice;
	@Autowired private MetaDataSchemaRepo metaDataSchemaRepo;
	@Autowired private TenantRepo tenantRepo;
//	@Autowired private DlsServiceHelper hService;
	@Autowired private RelationRepo relationRepo;
//	@Autowired private DirectoryService dService;
	@Value("${dls.ontology.prefixes}")
	public List<String> dlsKnownPrefixes;
//	@Autowired private FileService fileService;

	public MetaSchema listMetaDataSchema(String apiKey, String dlsKey, String name, MetaSchema.MetadataType type) throws DlsSecurityException, DlsPrivacyException, DlsNotFoundException {

		UserVO user = uservice.authorize(apiKey, dlsKey);
		TenantVO tenant = user.getTenant();
		final Boolean schematic = tenant.getSchematic();
		if(null == schematic || !schematic) {
			throw new DlsNotFoundException();
		}

		ExampleMatcher caseInsensitiveExampleMatcher = ExampleMatcher.matchingAll().withIgnoreCase()
				.withMatcher("name", ExampleMatcher.GenericPropertyMatchers.contains().ignoreCase())
				.withMatcher("type", ExampleMatcher.GenericPropertyMatchers.ignoreCase());

		Example<MetaSchemaVO> example = Example.of(MetaSchemaVO.builder()
				.tenant(user.getTenant())
				.name(name).type((null == type) ? null : type.name())
				.deleted(false)
				.build(),
				caseInsensitiveExampleMatcher);
		List<MetaSchema.MetadataDef> defs = metaDataSchemaRepo.findAll(example).stream().map(this::vo2bean).collect(Collectors.toList());
		return MetaSchema.builder()
				.metadataList(defs.isEmpty() ? null : defs)
				.metadataConfig(
					MetaSchema.MetadataConfig.builder()
					.allowAdhoc(tenant.getAllowAdhoc())
					.maxKeyLen(tenant.getMaxKeyLen())
					.maxValueLen(tenant.getMaxValueLen())
					.maxMetaPerFile(tenant.getMaxMetaPerFile()).build())
				.build();



	}

	public List <DlsResponse> createMetaDataSchema(MetaSchema schema, String apiKey, String dlsKey) throws DlsSecurityException, DlsPrivacyException {

		UserVO user = uservice.authorize(apiKey, dlsKey);
		if(!user.getAdmin()) {
			throw new DlsPrivacyException();
		}
//		Map <String,Integer> response = Maps.newHashMap();
		List <DlsResponse> responses = Lists.newArrayList();
		if(null != schema.getMetadataConfig()) {

			MetaSchema.@Valid MetadataConfig config = schema.getMetadataConfig();
			TenantVO tenant = user.getTenant();
			Optional.ofNullable(config.getMaxKeyLen()).ifPresent(tenant::setMaxKeyLen);
			Optional.ofNullable(config.getMaxValueLen()).ifPresent(tenant::setMaxValueLen);
			Optional.ofNullable(config.getMaxMetaPerFile()).ifPresent(tenant::setMaxMetaPerFile);
			Optional.ofNullable(config.getAllowAdhoc()).ifPresent(tenant::setAllowAdhoc);
			tenant.setSchematic(true);
			tenantRepo.save(user.getTenant());
//			response.put("config", HttpStatus.RESET_CONTENT.value());
			responses.add(DlsResponse.builder()
					.key("config")
//					.value(UPDATED)
					.code(HttpStatus.RESET_CONTENT.value())
					.messages(Set.of(UPDATED))
					.build());
		}

		final Integer maxKeyLen = Optional.ofNullable(user.getTenant().getMaxKeyLen()).orElse(Integer.MAX_VALUE);

		Optional.ofNullable(schema.getMetadataList()).ifPresent(list ->
				Flux.fromIterable(list)
						.map(this::bean2vo)
						.doOnNext(vo -> vo.setTenant(user.getTenant()))
						.subscribe(vo -> {
							try {
								if(vo.getName().length() <= maxKeyLen) {
									metaDataSchemaRepo.save(vo);
//									response.put(vo.getName(), HttpStatus.CREATED.value());
									responses.add(DlsResponse.builder()
											.key("name")
											.value(vo.getName())
											.code(HttpStatus.CREATED.value())
											.messages(Set.of(CREATED))
											.build());
								} else {
//									response.put(vo.getName(), HttpStatus.PAYLOAD_TOO_LARGE.value());
									responses.add(DlsResponse.builder()
											.key("name")
											.value(vo.getName())
											.code(HttpStatus.PAYLOAD_TOO_LARGE.value())
											.messages(Set.of("too long"))
											.build());
								}

							} catch (DataIntegrityViolationException e) {

//								response.put(vo.getName(), HttpStatus.CONFLICT.value());
								responses.add(DlsResponse.builder()
										.key("name")
										.value(vo.getName())
										.code(HttpStatus.CONFLICT.value())
										.messages(Set.of(ALREADY_EXISTS))
										.build());
								log.error(e.getMessage());
							}
						})
		);

		return responses;

	}

	private MetaSchemaVO bean2vo(MetaSchema.MetadataDef def) {
		return MetaSchemaVO.builder()
				.name(def.getName())
				.deleted(Boolean.FALSE)
				.type(def.getType().toUpperCase())
				.description(def.getDescription())
				.build();
	}

	private MetaSchema.MetadataDef vo2bean(MetaSchemaVO vo) {
		return MetaSchema.MetadataDef.builder().description(vo.getDescription())
				.name(vo.getName()).type(vo.getType()).build();
	}

	private boolean checkMetaSchemaInUse(TenantVO tenant, String metaName) {

		// TODO:
		return false;
	}

	public void deleteMetaData(String apiKey, String dlsKey, String metaDataName) throws DlsSecurityException, DlsPrivacyException, DlsNotFoundException {

		UserVO user = uservice.authorize(apiKey, dlsKey);
		if(!user.getAdmin()) {
			throw new DlsPrivacyException();
		}
		MetaSchemaVO vo = Optional.ofNullable(metaDataSchemaRepo.findByNameIgnoreCaseAndTenantIdAndDeleted(metaDataName, user.getTenant().getId(), false))
				.orElseThrow(DlsNotFoundException::new);
		if(checkMetaSchemaInUse(user.getTenant(), metaDataName)) {
			throw new DataIntegrityViolationException("metadata.in.use");
		}
		vo.setDeleted(true);
		vo.setDeletedOn(Timestamp.from(Instant.now()));
		metaDataSchemaRepo.save(vo);

	}

	public void deleteSchema(String apiKey, String dlsKey) throws DlsSecurityException, DlsPrivacyException {

		UserVO user = uservice.authorize(apiKey, dlsKey);
		if(!user.getAdmin()) {
			throw new DlsPrivacyException();
		}
		TenantVO tenantVO = tenantRepo.findByApiKey(apiKey);
		if(!tenantVO.getSchematic()) {
			throw new DataIntegrityViolationException("schema.config.already.deleted");
		}
		tenantVO.setMaxKeyLen(null);
		tenantVO.setAllowAdhoc(null);
		tenantVO.setMaxMetaPerFile(null);
		tenantVO.setSchematic(false);
		tenantVO.setMaxValueLen(null);
		tenantRepo.saveAndFlush(tenantVO);

		metaDataSchemaRepo.findByTenantId(tenantVO.getId()).forEach(vo -> {
			vo.setDeletedOn(Timestamp.from(Instant.now()));
			vo.setDeleted(true);
			metaDataSchemaRepo.save(vo);
		});

	}





	public List<MetaSchemaVO> getMetadataSchema(String apiKey,String dlsKey) throws DlsSecurityException, DlsPrivacyException, DlsNotFoundException {

		UserVO user = uservice.authorize(apiKey, dlsKey);	
		ExampleMatcher matcher = ExampleMatcher.matchingAll()
				.withIgnoreNullValues();
		MetaSchemaVO vo = MetaSchemaVO.builder().tenant(user.getTenant()).build();
		return Optional.of(metaDataSchemaRepo.findAll(Example.of(vo, matcher))).orElseThrow(DlsNotFoundException::new);

	}

	public void createRelation(List<Relation> relationList, String apiKey, String dlsKey) throws DlsSecurityException, DlsPrivacyException {

		UserVO user = uservice.authorize(apiKey, dlsKey);
		if(!user.getAdmin()) {
			throw new DlsPrivacyException();
		}
		List<RelationVO> rVOList= new ArrayList<>();
		for (Relation relation : relationList) {
			
			RelationVO rVO= RelationVO.builder().build();
			BeanUtils.copyProperties(relation, rVO);
			rVOList.add(rVO);
		}
		relationRepo.saveAll(rVOList);
	}

    public void registerOntology(String apiKey, String dlsKey, String prefix, String vocabularyLink) throws DlsSecurityException, DlsPrivacyException {

		UserVO user = uservice.authorize(apiKey, dlsKey);
		if(!user.getAdmin()) {
			throw new DlsPrivacyException();
		}
		if(dlsKnownPrefixes.stream().anyMatch(s -> prefix.trim().equalsIgnoreCase(s.trim()) )) {
			throw new DataIntegrityViolationException("dls.reserved.ontology.prefix");
		}
		metaDataSchemaRepo.save(MetaSchemaVO.builder().deleted(true)
				.tenant(user.getTenant())
				.name(prefix)
				.description(vocabularyLink)
				.type(IRI_TYPE)
				.build());

    }

	public Map<String, String> getOntology(String apiKey, String dlsKey) throws DlsSecurityException, DlsPrivacyException, DlsNotFoundException {
		
		UserVO user = uservice.authorize(apiKey, dlsKey);
		Map <String, String> result = Maps.newHashMap();
		metaDataSchemaRepo.findByTenantIdAndType(user.getTenant().getId(), IRI_TYPE)
		.forEach(v-> result.put(v.getName(), v.getDescription()));
		if(result.isEmpty())
			throw new DlsNotFoundException();
		return result;
	}

	public void deleteOntology(String apiKey, String dlsKey, String prefix) throws DlsSecurityException, DlsPrivacyException, DlsNotFoundException {

		UserVO user = uservice.authorize(apiKey, dlsKey);
		if(!user.getAdmin()) {
			throw new DlsPrivacyException();
		}
		MetaSchemaVO obj = Optional.ofNullable(metaDataSchemaRepo
				.findByNameIgnoreCaseAndTenantIdAndDeletedAndType(prefix, user.getTenant().getId(), true, IRI_TYPE))
			.orElseThrow(DlsNotFoundException::new);

		metaDataSchemaRepo.delete(obj);
	}
}
