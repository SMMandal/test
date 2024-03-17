package dls.repo;

import com.diffplug.common.base.Errors;
import com.google.common.base.Splitter;
import com.google.common.collect.Lists;
import dls.exception.DlsValidationException;
import dls.vo.CatalogTextSearchVO;
import dls.vo.FileMetaVO;
import dls.vo.FileSearchCriteria;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.jpa.domain.Specification;

import jakarta.persistence.criteria.*;
import java.util.List;
import java.util.Optional;
import java.util.StringTokenizer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Data
@AllArgsConstructor
@NoArgsConstructor

public class CatalogSpecification implements Specification<CatalogTextSearchVO> {

	private static final long serialVersionUID = 1L;
	public static final String OPERATOR_REGEX = " *((>=)|(<=)|(!=)|>|<|=) *";
	private FileSearchCriteria criteria;

	@Override
	public Predicate toPredicate(Root<CatalogTextSearchVO> root, CriteriaQuery<?> query, CriteriaBuilder builder) {
		
		List<Predicate> pList = Lists.newArrayList();
		// checking external files
		if(null != criteria.getExternal()) {
			
			pList.add(builder.equal(root.<Boolean> get("external"), criteria.getExternal()));
		}	
		
		// checking successfully transfered files
		if(null != criteria.getTransferred()) {
			
			pList.add(builder.equal(root.<Boolean> get("uploaded"), criteria.getTransferred()));
		}	
		
		if(null != criteria.getDeleted()) {
			
			pList.add(builder.equal(root.<Boolean> get("deleted"), criteria.getDeleted()));
		}	

		if(null != criteria.getBundled() ) {

			pList.add(builder.equal(root.<Boolean> get("bundled"), criteria.getBundled()));
		}
		
		if(null != criteria.getFilename()) {

			buildPredicateFromCsv("fileName", criteria.getFilename(), pList, root, builder);
		}
		
		if(null != criteria.getSavepoint()) {
			buildPredicateFromCsv("savepoint", criteria.getSavepoint(), pList, root, builder);
		}

		if(null != criteria.getDirectoryId()) {
			pList.add(builder.equal(root.<Long> get("directory"), criteria.getDirectoryId()));
		}

		if(Optional.ofNullable(criteria.getFindInRootDirectory()).orElse(Boolean.FALSE)) {
			pList.add(builder.isNull(root.<String> get("directory")));
		}
		
		if(null != criteria.getUri()) {

			Splitter.on(",")
					.omitEmptyStrings()
					.trimResults()
					.splitToList(criteria.getUri())
					.stream()
					.distinct()
					.map(uri -> uri.replace('*', '%')/*.concat("%")*/)
					.map(uri -> builder.like(root.get("fsPath"), uri))
					.reduce(builder::or)
					.ifPresent(pList::add);


		}
		
		if(null != criteria.getFromTime() && null != criteria.getToTime()) {
			
			pList.add(builder.greaterThanOrEqualTo(root.get("createdOn"), criteria.getFromTime()));
			pList.add(builder.lessThanOrEqualTo(root.get("createdOn"), criteria.getToTime()));
		}	
		
		if(null != criteria.getSizeInByte() && null != criteria.getSizeCheckOperator()) {
			
			switch(criteria.getSizeCheckOperator()) {
			case ">" : pList.add(builder.greaterThan(root.get("sizeInByte"), criteria.getSizeInByte())); break;
			case "<" : pList.add(builder.lessThan(root.get("sizeInByte"), criteria.getSizeInByte())); break;
			case ">=" : pList.add(builder.greaterThanOrEqualTo(root.get("sizeInByte"), criteria.getSizeInByte())); break;
			case "<=" : pList.add(builder.lessThanOrEqualTo(root.get("sizeInByte"), criteria.getSizeInByte())); break;
			case "=" : pList.add(builder.equal(root.<Long> get("sizeInByte"), criteria.getSizeInByte())); break;
			
			}
			
		}

		Boolean locked = criteria.getLocked();
		if(null != locked && !locked) {
			pList.add(builder.isNull(root.get("lock")));
		} else if (null != locked && locked) {
			pList.add(builder.isNotNull(root.get("lock")));
		}
		// metadata
		if(null != criteria.getMetadata()) {


			Subquery <Long> sq = query.subquery(Long.class);

			Root <FileMetaVO> meta = sq.from(FileMetaVO.class);



			Predicate p = null;
//			final StringTokenizer tokenizer =
//					Errors.suppress().getWithDefault(() -> new StringTokenizer(criteria.getMetadata(), ",", true), null);

			for(String elem : criteria.getMetadata().split(" *(,|&) *")) {
//			while(tokenizer.hasMoreElements()) {

//				String elem = tokenizer.nextElement().toString();
				String[] kv = elem.replace('*', '%').split(OPERATOR_REGEX);

				String str1 = Errors.suppress().getWithDefault(() -> kv[0], null);
				String str2 = Errors.suppress().getWithDefault(() -> kv[1].replace("%", "\\%").replace("'", ""), null);

				Predicate temp = null;

				if (kv.length > 1) {
					Pattern pattern = Pattern.compile(OPERATOR_REGEX);
					Matcher matcher = pattern.matcher(elem);
					String operator = "=";
					if (matcher.find())
					{
						operator = matcher.group().trim();
					}
					Predicate value = null;
					if(!operator.equalsIgnoreCase("=")) {
						try {Double.parseDouble(str2);} catch (Exception e) {
							throw new DlsValidationException("Invalid non-numeric value used with logical operator");
						}
					}
					switch (operator) {
						case ">=" : value = builder.greaterThanOrEqualTo(meta.get("value_numeric"), str2); break;
						case "<=" : value = builder.lessThanOrEqualTo(meta.get("value_numeric"), str2); break;
						case ">" : value = builder.greaterThan(meta.get("value_numeric"), str2); break;
						case "<" : value = builder.lessThan(meta.get("value_numeric"), str2); break;
						case "!=" : value = builder.notEqual(meta.get("value_numeric"), str2); break;
						default:
							value = builder.like(meta.get("value"), str2);
					}
					temp = builder.and(builder.like(builder.upper(meta.get("name")), str1.toUpperCase()), value);

				} else if (kv.length == 1 && ! str1.contains("'")) {
					temp = builder.like(builder.upper(meta.get("name")), str1.toUpperCase());

				} else if (kv.length == 1 && str1.contains("'")) {
					temp = builder.like(meta.get("value"), str1.replace("%", "\\%").replace("'", ""));

				}

				if(null == p) {
					p = temp;
				} else {
					p = builder.or(p, temp);
				}


			}


			sq.select(meta.get("file")).where(p);

			pList.add(builder.in(root.<Long>get("id")).value(sq));



		}	
		
		
		if(null != criteria.getText() && !criteria.getText().isEmpty()) {

			pList.add(builder.isTrue(builder.function("fts", Boolean.class, builder.literal(criteria.getText()))));
		}	

//		pList.add(builder.and(builder.equal(root.<Long> get("user"), criteria.getUserVO())));

		pList.add(builder.or(builder.isTrue(builder.function("arr_search", Boolean.class, builder.literal(criteria.getUserVO().getDlsUser()))),
				builder.equal(root.<Long> get("user"), criteria.getUserVO())));
//		query.distinct(true);
		
		return builder.and(pList.toArray(new Predicate[0]));
		
	}

	private void buildPredicateFromCsv(String key, String val, List<Predicate> pList,
									   Root<CatalogTextSearchVO> root, CriteriaBuilder builder) {
		Splitter.on(",")
				.omitEmptyStrings()
				.trimResults()
				.splitToList(val)
				.stream()
				.distinct()
				.map(str -> str.replace('*', '%'))
				.map(str -> builder.like(root.get(key), str))
				.reduce(builder::or)
				.ifPresent(pList::add);
	}

//	private Predicate buildPredicateFromMetadataString(Root <FileMetaVO> meta , CriteriaBuilder builder) {
//
//		Predicate predicate = null;
////		final StringTokenizer tokenizer =
////				Errors.suppress().getWithDefault(() -> new StringTokenizer(criteria.getMetadata(), ",", true), null);
//
//		for(String elem : criteria.getMetadata().split("(,|&)")) {
//
////			String elem = tokenizer.nextElement().toString();
//			String[] kv = elem.replace('*', '%').split("=");
//
//			String str1 = Errors.suppress().getWithDefault(() -> kv[0], null);
//			String str2 = Errors.suppress().getWithDefault(() -> kv[1].replace("'", ""), null);
//
//			Predicate temp = null;
//
//			if (kv.length > 1) {
//				temp = builder.and(builder.like(meta.get("name"), str1),builder.like(meta.get("value"), str2));
//
//			} else if (kv.length == 1 && ! str1.contains("'")) {
//				temp = builder.like(meta.get("name"), str1);
//
//			} else if (kv.length == 1 && str1.contains("'")) {
//				temp = builder.like(meta.get("value"), str1.replace("'", ""));
//
//			}
//
//			if(null == predicate) {
//				predicate = temp;
//			} else {
//				predicate = builder.or(predicate, temp);
//			}
//
//
//		}
//		return predicate;
//
//	}

//	public static void main(String[] args) {
//
//		String exp = "key1!=110";
//
//		for (String s :exp.split(OPERATOR_REGEX)) {
//			System.out.println("part: " + s);
//		}
//		Pattern pattern = Pattern.compile(OPERATOR_REGEX);
//		Matcher matcher = pattern.matcher(exp);
//		if (matcher.find())
//		{
//			System.out.println("operator: " + matcher.group());
//		}
//	}

}
