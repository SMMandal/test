package dls.repo;

import dls.vo.DirectoryVO;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Set;

public interface FAIRRepo extends JpaRepository<DirectoryVO, Long> {
	DirectoryVO findByDirectoryIgnoreCaseAndDeleted(String directory, Boolean deleted);
	DirectoryVO findByDirectoryIgnoreCaseAndDeletedAndTenantId(String directory, Boolean deleted,Long tenantId);
	@Query(value = "select * from directory where parent = :parent", nativeQuery = true)
	List<DirectoryVO> findByParent(Long parent);

	/*('REPO', 'CAT', 'DS', 'DIST')*/
	@Query(value = "select id from directory where enforcement_type in :types " +
			"AND parent in (select id from directory where directory like :directory)", nativeQuery = true)
	List<DirectoryVO> findChildren(@Param("directory") String directory, @Param("types") Set<String> types);

	@Query(value = "select id from directory where enforcement_type in :types OR enforcement_type IS NULL " +
			"AND parent in (select id from directory where directory like :directory)", nativeQuery = true)
	List<DirectoryVO> findChildrenWithDirectory(@Param("directory") String directory, @Param("types") Set<String> types);

	@Query(value = "select id from directory where enforcement_type in :types OR enforcement_type IS NULL " +
			"AND parent in (select id from directory where directory like :directory)", nativeQuery = true)
	List<DirectoryVO> findChildrenWithDirectoryForMetadata(@Param("directory") String directory, @Param("types") Set<String> types);


}
