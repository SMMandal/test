package dls.repo;

import dls.vo.DirectoryVO;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigInteger;
import java.util.List;
import java.util.Map;

public interface DirectoryRepo extends JpaRepository<DirectoryVO, Long> {

	@Query(value = "select id from directory where tenant_id = :tenantId and directory = :directory and deleted != 't'", nativeQuery = true)
	Long getIdByDirectory(@Param("tenantId") Long tenantId, @Param("directory") String directory);

	DirectoryVO findByDirectoryIgnoreCaseAndDeletedAndTenantId(String directory, Boolean deleted,Long tenantId);
	DirectoryVO findByDirectoryIgnoreCaseAndCreatedByIdAndDeleted(String directory, Long createdById,Boolean deleted);

	DirectoryVO findByDirectoryIgnoreCaseAndTenantIdAndCreatedByIdAndDeleted(String directory, Long tenantId,Long createdById,Boolean deleted);

	@Query(value = "select * from directory where parent = :parent",
			nativeQuery = true)
	List<DirectoryVO> findByParent(Long parent);
	
	@Query(value = "select * from directory where created_by_id = :createdById and directory like :directory and deleted = 'f'",
			nativeQuery = true)
	List <DirectoryVO> getDirectoryList(@Param("createdById") Long createdById,@Param("directory") String directory);
	

	
	@Query(value = "SELECT distinct t1.* from directory t1\n" + 
			"INNER JOIN permission t2 ON t2.directory_id=t1.id\n" + 
			"where (t2.permitted_user = :createdById or t2.acquired_user @> cast(ARRAY[:createdById] AS bigint[])) and \n" + 
			"t1.directory = :directory and t1.tenant_id =:tenantId and t1.deleted='f'",
			nativeQuery = true)
	DirectoryVO getDirectoryByPermittedUser(@Param("directory") String directory,@Param("tenantId") Long tenantId,@Param("createdById") Long createdById);


	@Query(value = "SELECT t1.* from directory t1\n" +
			"INNER JOIN permission t2 ON t2.directory_id=t1.id\n" +
			"where (t2.permitted_user = :loggedInUserId or t2.acquired_user @> cast(ARRAY[:loggedInUserId] AS bigint[])) and \n" +
			"t1.directory like :directory and t1.tenant_id =:tenantId and t1.deleted='f' ",
			nativeQuery = true)
	List<DirectoryVO> getDirectoryNamesByPermittedUser(@Param("directory") String directory, @Param("tenantId") Long tenantId, @Param("loggedInUserId") Long loggedInUserId);


	@Query(value = "SELECT directory_id, count(*) FROM file WHERE deleted = :deleted AND user_id = :user AND id NOT IN " +
			"(SELECT file_id FROM file_meta WHERE user_id = :user AND name = 'dls:internal' OR " +
			"(name = 'dls:lineage' AND value IN ('OVERWRITE', 'APPEND'))) GROUP BY directory_id", nativeQuery = true)
	List<Map<String, BigInteger>> getFileCount(@Param("user") Long user, @Param("deleted") boolean delete);

	@Query(value = "SELECT COUNT(*) FROM file_share WHERE user_id = :user", nativeQuery = true)
	Long getSharedFileCount(@Param("user") Long user);

}
