package dls.repo;

import dls.vo.DirectoryVO;
import dls.vo.PermissionVO;
import dls.vo.TenantVO;
import org.hibernate.annotations.SQLDelete;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface PermissionRepo extends JpaRepository<PermissionVO, Long> {
	
	List<PermissionVO> findByDirectory(DirectoryVO directory);
	
	@Query(value = "select * from permission where permitted_user = :userId and directory_id = :directoryId",
			nativeQuery = true)
	PermissionVO findPermissionsContaining(@Param("userId") Long userId,@Param("directoryId") Long directoryId);
	
	@Query(value = "select distinct unnest(acquired_user || permitted_user) from permission where tenant_id=:tenantId and action like '%W%' AND directory_id=:directoryId",
			nativeQuery = true)
	List<Long> getWritePermittedUsers(@Param("tenantId") Long tenantId,@Param("directoryId") Long directoryId);
	
	List <PermissionVO> findByTenantAndDirectoryId(TenantVO tenant, Long directoryId);
	
	List <PermissionVO> findByTenantAndDirectoryIdAndPermittedUser(TenantVO tenant, Long directoryId, Long permittedUser);
	
	List <PermissionVO> findByTenantAndPermittedUser(TenantVO tenant, Long permittedUser);
	
//	List <PermissionVO> findByTenantAndPermittedGroupAndDirectoryId(TenantVO tenant, String permittedGroup,Long directoryId);

	@Query(value = "select count(*) from permission P join directory D on P.directory_id = D.id where " +
			"D.directory = :directory and P.action like :action and P.permitted_user = :user",
			nativeQuery = true)
	Long checkPermission(@Param("user") Long user, @Param("directory") String directory, @Param("action") String action);

	@SQLDelete(sql = "delete from permission where id = :id")
	void deleteById(@Param("id") Long id);
}
