package dls.repo;

import dls.vo.FileVO;
import dls.vo.UserVO;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface FileRepo extends JpaRepository<FileVO, Long> , JpaSpecificationExecutor<FileVO> {

	Integer deleteByUser(UserVO user);

	List<FileVO> findByUser(UserVO user);

	List<FileVO> findByFsPathIn(List<String> fsPaths);

	List<FileVO> findByFsPathContaining(String fsPath);

//	FileVO findByFsPath(String fsPath);



//	Optional<FileVO> findByIdAndStorage(Long id, String storage);
	Optional<FileVO> findByFsPathAndStorage(String fsPath, String storage);

	@Query(value = "select * from file where user_id = :userId and fs_path like :fsPath  order by created_on",
			nativeQuery = true)
	List <FileVO> getArchiveList(@Param("userId") Long userId, @Param("fsPath") String fsPath);

	@Query(value = "select sum(size_in_byte) from file join users on file.user_id = users.id and users.tenant_id = :tenandId and file.deleted = false",
			nativeQuery = true)
	Long getUsedStorage(@Param("tenandId") Long tenantId);

	FileVO findByFsPathAndDeleted(String fsPath, boolean deleted);

	FileVO findByUserAndFsPathAndDeleted(UserVO user, String fsPath, boolean deleted);
	
	List<FileVO> findByUserAndDirectoryId(UserVO user,Long directoryId);
	List<FileVO> findByUserIdAndFileNameAndDeleted(Long userId,String fileName, Boolean deleted);
	
	List<FileVO> findByBundleHashAndDeletedAndUserId(String bundleHash, Boolean deleted, Long userId);
}
