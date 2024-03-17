package dls.repo;

import dls.exception.DlsValidationException;
import dls.vo.CatalogVO;
import org.springframework.data.jpa.domain.Specification;

import jakarta.persistence.criteria.CriteriaBuilder;

import java.sql.Array;
import java.sql.JDBCType;
import java.sql.Timestamp;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

public class ExplorerViewSpecification{

//    public static final String METADATA_VALUE_PATTERN = "'.*'";

    public static Specification<CatalogVO> hasPermittedUser(Long uid) {
        return (root, query, cb) ->
//        cb.isNotNull(cb.function("array_position", JDBCType.INTEGER.getDeclaringClass(), root.get("permittedUsers"), cb.literal(uid).as(Long.class)));
         cb.equal(cb.literal(uid).as(Long.class), cb.function("ANY", Long.class, root.get("permittedUsers")));
    }

    public static Specification<CatalogVO> hasTypeIdIn(List<Long> ids) {
        return (root, query, cb) -> {
            CriteriaBuilder.In<Long> in = cb.in(root.get("id"));
            ids.forEach(in::value);
            return in;
        };
    }

    public static Specification<CatalogVO> hasNameLike(String name) {
        return (root, query, cb) ->
                cb.like(root.get("name"), name.replace('*', '%'));
    }

    public static Specification<CatalogVO> hasSavepointLike(String savepoint) {
        return (root, query, cb) ->
                cb.like( cb.lower(root.get("savepoint")), savepoint.replace('*', '%').toLowerCase());
    }

    /**
     * metadata based spec
     * @param name
     * @param value
     * @return
     */
    public static Specification<CatalogVO> hasMetadata(String name, String value) {
        name = name.replace('*', '%');
        return (root, query, cb) ->
                cb.like(cb.literal("metadata ->> 'key1'"), cb.literal(value));
    }
    public static Specification<CatalogVO> hasNameRegex(String nameRegex) {
        try {
            Pattern.compile(nameRegex);
        } catch (PatternSyntaxException e) {
            throw new DlsValidationException(nameRegex + " is not a valid regex");
        }
        return (root, query, cb) ->
                cb.isNotNull(cb.function("regexp_match", String[].class, root.get("name"), cb.literal(nameRegex).as(String.class) ));
//                cb.concat(root.get( "name ~ "), nameRegex);
    }

    public static Specification<CatalogVO> hasParentLike(String parent) {
        return (root, query, cb) ->
                cb.like(root.<String>get("parent"), parent.replace('*','%'));
    }

    public static Specification<CatalogVO> hasPathLike(String path) {
        return (root, query, cb) ->
                cb.equal(root.get("path"),  path);
    }

    /*public static Specification<ExplorerViewVO> hasCreatedOn(Timestamp createdOn) {
        return (root, query, cb) ->
                cb.equal(root.<Timestamp>get("createdOn"), createdOn);
    }*/

    public static Specification<CatalogVO> hasCreatedOnBetween(Timestamp start, Timestamp end) {
        return (root, query, cb) ->
                cb.between(root.<Timestamp>get("createdOn"), start, end);
    }

    public static Specification<CatalogVO> hasCreatedBy(String createdBy) {
        return (root, query, cb) ->
                cb.equal(root.<String>get("createdBy"), createdBy);
    }


    public static Specification<CatalogVO> hasType(char type) {
        return (root, query, cb) ->
                cb.equal(root.<String>get("type"), type);
    }

    private static final String FILE_SIZE_SPLIT_REGEX = "\\d+(\\.\\d{1,3})?";
    private static final String FILE_COUNT_SPLIT_REGEX = "\\d+";
    private static final Pattern fileSizeSplitPattern = Pattern.compile(FILE_SIZE_SPLIT_REGEX);

    public static Specification<CatalogVO> hasSize(String size) {

        return (root, query, cb) -> {
            String[] tokens = size.trim().split(FILE_SIZE_SPLIT_REGEX);
            String unit = tokens[1].toLowerCase();

            Matcher matcher = fileSizeSplitPattern.matcher(size);

            double value = 0;
            var validationException = new DlsValidationException("File size query value max limit is 100 petabytes");
            if (matcher.find()) {
                try {
                    value = Double.parseDouble(matcher.group());
                } catch (NumberFormatException e) {
                    throw validationException;
                }
                value = switch (unit) {
                    case "b" -> {
                        if(value <= 100L * 1024  * 1024 * 1024 * 1024 * 1024) yield value;
                        else throw validationException;
                    }
                    case "kb" -> {
                        if(value <= 100L * 1024  * 1024 * 1024 * 1024) yield value * 1024;
                        else throw validationException;
                    }
                    case "mb" -> {
                        if(value <= 100L * 1024  * 1024 * 1024) yield value * 1024 * 1024;
                        else throw validationException;
                    }
                    case "gb" -> {
                        if(value <= 100 * 1024  * 1024) yield value * 1024 * 1024 * 1024;
                        else throw validationException;
                    }
                    case "tb" -> {
                        if(value <= 100 * 1024) yield value * 1024 * 1024 * 1024 * 1024;
                        else throw validationException;
                    }
                    case "pb" -> {
                        if(value <= 100) yield value * 1024 * 1024 * 1024 * 1024 * 1024 ;
                        else throw validationException;
                    }
                    default -> value;
                };
            }

            String operator = tokens[0];

            return switch (operator) {
                case ">" -> cb.greaterThan(root.get("size"), value);
                case "<" -> cb.lessThan(root.get("size"), value);
                case ">=" -> cb.greaterThanOrEqualTo(root.get("size"), value);
                case "<=" -> cb.lessThanOrEqualTo(root.get("size"), value);
                case "!=" -> cb.notEqual(root.get("size"), value);
                case "=" -> cb.equal(root.get("size"), value);
                default -> throw new DlsValidationException("Invalid size operator: " + operator);
            };

        };
    }

    public static Specification<CatalogVO> hasUploaded(Boolean uploaded) {
        return (root, query, cb) ->
                cb.equal(root.<Boolean>get("uploaded"), uploaded);
    }

    public static Specification<CatalogVO>  hasFileCount(String fileCount) {

        return (root, query, cb) -> {

            String[] tokens = fileCount.trim().split(FILE_COUNT_SPLIT_REGEX);
            String operator = tokens[0];
            long value = Long.parseLong(fileCount.replace(operator,""));


            return switch (operator) {
                case ">" -> cb.greaterThan(root.get("fileCount"), value);
                case "<" -> cb.lessThan(root.get("fileCount"), value);
                case ">=" -> cb.greaterThanOrEqualTo(root.get("fileCount"), value);
                case "<=" -> cb.lessThanOrEqualTo(root.get("fileCount"), value);
                case "!=" -> cb.notEqual(root.get("fileCount"), value);
                case "=" -> cb.equal(root.get("fileCount"), value);
                default -> throw new DlsValidationException("Invalid file count compare operator: " + operator);
            };

        };
    }


}