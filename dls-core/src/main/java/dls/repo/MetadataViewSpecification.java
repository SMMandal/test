package dls.repo;

import dls.vo.MetadataViewVO;
import org.springframework.data.jpa.domain.Specification;

import jakarta.persistence.criteria.Predicate;
import java.util.Optional;

public class MetadataViewSpecification {
    public static final String METADATA_VALUE_PATTERN = "'.*'";
    public static Specification<MetadataViewVO> hasNameLike(String name) {
        return (root, query, cb) ->
                cb.like(root.get("name"), name.replace('*', '%'));
    }

    public static Specification<MetadataViewVO> hasValueLike(String value) {
        return (root, query, cb) ->
                cb.like(root.get("value"), value.replace('*', '%'));
    }

    public static Specification<MetadataViewVO> hasType(char type) {
        return (root, query, cb) ->
                cb.equal(root.get("type"), type);
    }

    public static Specification<MetadataViewVO> hasCreatedBy(String createdBy) {
        return (root, query, cb) ->
                cb.equal(root.get("createdBy"), createdBy);
    }
    public static Specification<MetadataViewVO> hasNameValueLike(String meta) {

        return Optional.ofNullable(meta)
                .map(m -> m.replace("%", "\\%").replace("*", "%"))
                .map(m -> {

                    var split = m.split(" *= *");
                    String name = split.length > 1 || !split[0].matches(METADATA_VALUE_PATTERN) ? split[0] : null;
                    String value = split.length > 1 && split[1].matches(METADATA_VALUE_PATTERN) ? split[1]
                            : split[0].matches(METADATA_VALUE_PATTERN) ? split[0] : null;


                    return (Specification<MetadataViewVO>) (root, query, cb) -> {

                        Predicate p1 = Optional.ofNullable(name).map(n ->
//                                cb.equal(cb.literal(uid).as(Long.class), cb.function("ANY", Long.class, root.get("permittedUsers")))
//                                        cb.like(root.get("name"), n.toLowerCase().replace('*', '%')))
                                        cb.like(cb.function("lower", String.class, root.get("name")), n.toLowerCase().replace('*', '%')))
                                .orElse(cb.conjunction());

                        Predicate p2 = Optional.ofNullable(value).map(v ->
                                        cb.like(cb.function("lower", String.class, root.get("value")), v.toLowerCase().replace('*', '%').replace("'", "")))
//                                        cb.like(root.get("value"), v.toLowerCase().replace('*', '%').replace("'", "")))
                                .orElse(cb.conjunction());

                        return cb.and(p1, p2);
                    };

                }).orElse((root, query, cb) -> cb.conjunction());
    }
}
