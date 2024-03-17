package dls.service;

import com.diffplug.common.base.Errors;
import com.google.common.base.Joiner;
import com.google.common.collect.Lists;
import dls.bean.Directory;
import dls.bean.MetaSchema;
import dls.bean.MetadataRule;
import dls.bean.Permission;
import dls.exception.DlsValidationException;
import dls.repo.MetaDataSchemaRepo;
import dls.repo.UserRepo;
import dls.vo.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static dls.util.BeanValidationConstraint.DIRECTORY_META_REGEX;

@Component
public class DirectoryServiceHelper {

    @Autowired private UserRepo userRepo;
    @Autowired private MetaDataSchemaRepo mdRepo;
    @Autowired private DlsServiceHelper dhelper;

    DirectoryVO buildDirectoryToVO(final Directory directory, UserVO user) {

        DirectoryVO target = DirectoryVO.builder()
                .directory(directory.getDirectory())
                .createdBy(user)
                .createdOn(Timestamp.from(Instant.now()))
                .tenant(user.getTenant())
                .deleted(false)
                .enforcementType(directory.getEnforcement())
                .permission(buildPermissionVO(directory, user))
                .build();



        List<DirectoryMetaVO> metaVOS = Lists.newArrayList();

        if(null != directory.getRule()) {

            Flux
                    .fromIterable(matchSchemaAndBuildDirectoryRuleToVO(user.getTenant(), directory.getRule()))
                    .doOnNext(vo -> vo.setDirectory(target))
                    .doOnNext(vo -> vo.setUser(user))
                    .collectList()
                    .subscribe(metaVOS::addAll);
        }

        if(null != directory.getMetadata()) {
            var metadataArr = directory.getMetadata().split(",");
            if(! Arrays.stream(metadataArr).allMatch(m -> m.matches(DIRECTORY_META_REGEX))) {
//            if(!directory.getMetadata().matches(DIRECTORY_META_REGEX)) {
                throw new DlsValidationException("Invalid metadata format");
            }
            Map<String, String> props = dhelper.validate(metadataArr);
            Flux
                    .fromIterable(matchSchemaAndBuildDirectoryRuleToVO(user.getTenant(), props.entrySet().stream().map(e -> {
                        if(e.getKey().contains("private@")) {
                            throw new DlsValidationException("Private metadata is not supported in directory.");
                        }
                                return MetadataRule.builder()
                                        .name(e.getKey().replace("public@", "")
                                                .replaceFirst("(?i)private@", user.getId() + "@"))
                                        .defaultValue(e.getValue())
                                        .type("TEXT")
                                        .build();
                            }
                    ).toList()))
                    .doOnNext(m -> m.setIsMeta(true))
                    .doOnNext(vo -> vo.setDirectory(target))
                    .doOnNext(vo -> vo.setUser(user))
                    .collectList()
                    .subscribe(metaVOS::addAll);
        }
        if(!metaVOS.isEmpty()) {
            target.setDirectoryMetaVOList(metaVOS);
        }
        return target;
    }

    List<PermissionVO> buildPermissionVO(Directory directory, UserVO user) {

        List<Permission> list = Optional.ofNullable(directory.getPermissions()).orElse(Lists.newArrayList());
        if(list.stream().flatMap(p -> p.getUsers().stream()).anyMatch(u -> u.equalsIgnoreCase(user.getDlsUser()))) {
            throw new DataIntegrityViolationException("self.permission.issue");
        }

        list.add(Permission.builder().action("RWDABC")
                .users(Lists.newArrayList(user.getDlsUser())).build());

        return Flux.fromIterable(list)
                .flatMap(p -> buildPermissionPerUser(p, user)
                ).collect(Collectors.toList()).block();
    }

    Flux<PermissionVO> buildPermissionPerUser(Permission p, UserVO user) {

        return Flux.fromIterable(p.getUsers())
                .map(u -> Optional.ofNullable(userRepo.findByTenantAndDlsUser(user.getTenant(), u))
                        .orElseThrow(() -> new DlsValidationException("User '".concat(u).concat("' mentioned in permission does not exist"))))
                .doOnNext(u -> validateOrgPosOfAdminAndUser(user.getOrgPosition(), u.getOrgPosition()))
                .map(u -> PermissionVO.builder()
//						.action(Optional.ofNullable(p.getAction()).map(String::toUpperCase).orElse(null))
                        .action(Permission.Util.buildAction(p.getAction(), p.getDirectoryAction()/*, p.getMetadataAction()*/))
                        .tenant(user.getTenant())
                        .user(user)
                        .permittedUserName(u.getDlsUser())
                        .permittedUser(u.getId())
                        .acquiredUser(findAcquiredUser(u))
                        .build());
    }

    private void validateOrgPosOfAdminAndUser(String [] adminOrgPos, String [] userOrgPos) {

        if(null == adminOrgPos && null == userOrgPos) {
            return;
        }
        if(null == adminOrgPos || null == userOrgPos) {
            throw new DataIntegrityViolationException("User's organization position is not set or different");
        }
        if(Stream.of(adminOrgPos)
                .noneMatch(a -> Stream.of(userOrgPos)
                        .anyMatch(u -> u.contains(a)))) {
            throw new DataIntegrityViolationException("User's organization position is not set or different");
        }

    }


    private  Long [] findAcquiredUser(UserVO userVO) {

        String [] orgPos = userVO.getOrgPosition();
        return (null != orgPos) ? Flux.fromArray(orgPos)
                .map(p -> StringUtils.trimTrailingCharacter(p, '/'))
                .map(p -> {
                    List<String> ps = Lists.newArrayList();
                    do{
                        ps.add(p);
                        int i = p.lastIndexOf('/');
                        p = (i > 0) ? p.substring(0, i) : "";

                    } while(!p.isEmpty());
                    return Joiner.on(',').join(ps) ;
                })
                .flatMap(ps -> {
                    List<Long> vos = userRepo.findAdminsContainingOrgPos(userVO.getTenant().getId(), ps);
                    return Flux.fromIterable(vos);
                })
                .filter(id -> !(id.equals(userVO.getId()) && Optional.ofNullable(userVO.getAdmin()).orElse(Boolean.FALSE)) )
                .collectList()
                .blockOptional()
                .orElse(Lists.newArrayList())
                .toArray(Long[]::new)
                : null;
    }

    public List<DirectoryMetaVO> matchSchemaAndBuildDirectoryRuleToVO(TenantVO tenant, List<MetadataRule> rules) {

//		List<DirectoryMetaVO> vos = Lists.newArrayList();

        boolean schematic = Optional.ofNullable(tenant.getSchematic()).orElse(Boolean.FALSE);
        boolean allowAdhoc = Optional.ofNullable(tenant.getAllowAdhoc()).orElse(Boolean.FALSE);
        Integer maxKeyLen = Optional.ofNullable(tenant.getMaxKeyLen()).orElse(Integer.MAX_VALUE);
        Integer maxValueLen = Optional.ofNullable(tenant.getMaxValueLen()).orElse(Integer.MAX_VALUE);
        Integer maxMetaPerFile = Optional.ofNullable(tenant.getMaxMetaPerFile()).orElse(Integer.MAX_VALUE);

        if(schematic && rules.size() > maxMetaPerFile) {
            throw new DataIntegrityViolationException("mismatch.schema.count");
        }

        Flux<MetaSchemaVO> schemaFlux = Flux.fromIterable(mdRepo.findByTenantId(tenant.getId()))
                .cache();
        Flux<DirectoryMetaVO> voFlux = Flux.fromIterable(rules)
                .map(this::buildDirectoryMetaVO)
//				.doOnNext(vos::add)

                .cache();


        if(schematic && ! allowAdhoc) {


            List <String> unmatchingMetadata = Lists.newArrayList();

            if(! voFlux
                    .map(r -> {
                        Boolean match = schemaFlux.any(s -> s.getName().equalsIgnoreCase(r.getName())
                                && s.getType().equalsIgnoreCase(r.getType()))
                                .blockOptional().orElse(Boolean.FALSE);
                        if(!match) {
                            unmatchingMetadata.add(r.getName().concat("(").concat(r.getType()).concat(")"));
                        }
                        return match;
                    })

                    .reduce((b1, b2) -> b1 && b2)
                    .blockOptional().orElse(Boolean.FALSE))
            {
                String errMsg = "No schema exists for metadata "
                        .concat(Joiner.on(", ").skipNulls().join(unmatchingMetadata));
                throw new DataIntegrityViolationException(errMsg);
            }

        }

        return voFlux
                .doOnNext(r ->
                        Optional.of(schemaFlux).ifPresent(schemaf -> {
                            MetaSchemaVO schema =  schemaf.filter(s -> s.getName().equalsIgnoreCase(r.getName())
                                    && s.getType().equalsIgnoreCase(r.getType()))
                                    .blockFirst();
                            r.setSchema(schema);
                        })

                )
                .doOnNext(r -> {
                    if(schematic ) {
                        if(r.getName().length() > maxKeyLen) {
                            throw new DataIntegrityViolationException("mismatch.schema.key.length");
                        }
                        int len = Optional.ofNullable(r.getValue()).map(String::length)
                                .orElse(Optional.ofNullable(r.getValue_numeric())
                                        .orElse(0D).toString().length());
                        if(len > maxValueLen) {
                            throw new DataIntegrityViolationException("mismatch.schema.val.length");
                        }
                    }
                })
                .collectList()
                .block()
                ;


//		return vos;
    }

    private DirectoryMetaVO buildDirectoryMetaVO(MetadataRule r) {

        Boolean valueMandatory = Optional.ofNullable(r.getValueMandatory()).orElse(Boolean.FALSE);

        if(null != r.getDefaultValue() && valueMandatory) {
            throw new DataIntegrityViolationException("rule.default.mandatory.conflict");
        }
        String type = r.getType().toUpperCase();
        DirectoryMetaVO vo = DirectoryMetaVO.builder()
                .deleted(false)
                .name(r.getName())
                .type(type)
                .value_mandatory(valueMandatory)
                .value(r.getDefaultValue())
                .build();
        String val = r.getDefaultValue();

        if(MetaSchema.MetadataType.NUMERIC.name().equalsIgnoreCase(type)) {
            vo.setValue_numeric(Errors.createRethrowing(e -> new DlsValidationException("value.mismatch")).get(() -> Double.valueOf(val)));

        } else {
            vo.setValue(val);
        }
        return vo;
    }
}
