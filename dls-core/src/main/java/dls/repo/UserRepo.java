package dls.repo;

import dls.vo.TenantVO;
import dls.vo.UserVO;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface UserRepo extends JpaRepository<UserVO, Long> {

	UserVO findByTenantAndDlsUser(TenantVO tenant, String dlsUser);
	List <UserVO> findByTenantAndAdmin(TenantVO tenant, Boolean admin);
	List <UserVO> findByTenant(TenantVO tenant);
//	List <UserVO> findByGroups(String [] groups);
	
//	@Query(value = "select * from users where groups @> cast(ARRAY[:group] AS text[]) and tenant_id = :tenantId",
//			nativeQuery = true)
//	List <UserVO> findUsersContaining(@Param("group") String group,@Param("tenantId") Long tenantId);

	@Query(value = "select count(*) from users where tenant_id = :tenantId and org_position IS NOT null",
			nativeQuery = true)
	Long countByTenantWithNullOrgPos(/*@Param("org") String oldOrg,*/ @Param("tenantId") Long tenantId);


	@Cacheable("userId")
	@Query(value = "SELECT dls_user FROM users WHERE id = :id", nativeQuery = true)
	String getDlsUserById(Long id);

	@Cacheable("userName")
	@Query(value = "SELECT id FROM users WHERE tenant_id = :tenantId AND dls_user = :dlsUser", nativeQuery = true)
	Long getDlsUserByName(@Param("tenantId") Long tenantId, @Param("dlsUser") String dlsUser);
	@Query(value = "select id from users where org_position && string_to_array(:orgPos, ',') and admin = 't' and tenant_id = :tenantId",
			nativeQuery = true)
	List <Long> findAdminsContainingOrgPos(@Param("tenantId") Long tenantId, @Param("orgPos") String  orgPos);

	@Query(value = "select count(*) from users where org_position && string_to_array(:orgPos, ',') and tenant_id = :tenantId",
			nativeQuery = true)
	Long getCountContainingOrgPos(@Param("tenantId") Long tenantId, @Param("orgPos") String  orgPos);
	
	@Query(value = "select * from users where dls_user IN (:users) AND tenant_id = :tenantId",
			nativeQuery = true)
	List<UserVO> findUserListForUsers(@Param("users") List<String> users, @Param("tenantId") Long tenantId);

	@Query(value = "select * from users where dls_user NOT IN (:users) AND tenant_id = :tenantId",
			nativeQuery = true)
	List<UserVO> findUsersNotInList(@Param("users") List<String> users, @Param("tenantId") Long tenantId);
	
	@Query(value = "select count(*) from users where dls_user IN (:users) AND tenant_id = :tenantId",
			nativeQuery = true)
	Long countByUsers(@Param("users") List<String> users, @Param("tenantId") Long tenantId);
	
	@Query(value = "select * from users where groups @> cast(ARRAY[:group] AS text[]) and dls_user = :user and tenant_id = :tenantId",
			nativeQuery = true)
	UserVO findUserContainingGroup(@Param("group") String group,@Param("user") String user,@Param("tenantId") Long tenantId);
	
	@Query(value = "select * from users where dls_user = :user AND tenant_id = :tenantId",
			nativeQuery = true)
	UserVO findUserForUsers(@Param("user") String user, @Param("tenantId") Long tenantId);
}
