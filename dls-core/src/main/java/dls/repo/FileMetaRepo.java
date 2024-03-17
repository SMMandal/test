package dls.repo;

import dls.vo.FileMetaVO;
import dls.vo.UserVO;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Map;

public interface FileMetaRepo extends JpaRepository<FileMetaVO, Long>, JpaSpecificationExecutor<FileMetaVO> {

	@Query(nativeQuery = true, value = "SELECT name, value FROM file_meta WHERE user_id = :userId")
	List <Map<String, String>> findDistinctByUserId(@Param("userId") Long userId);

//	List <FileMetaVO> findDistinctByNameAndValue(@Param("name") String name, @Param("value") String value);
	@Modifying
	@Query("delete from file_meta b where id in :ids")
	void deleteByIds(@Param("ids") List<Long> ids);
	Integer deleteByUser(UserVO user);

    void deleteByFileId(Long id);

	@Modifying
	@Query(value = "INSERT INTO file_meta (id, name, value, file_id, user_id) VALUES (nextval('hibernate_sequence'), :name, :value, :fileId, :userId)", nativeQuery = true)
	void insertMetadata( @Param("name") String name, @Param("value") String value, @Param("fileId") Long fileId, @Param("userId") Long userId);
}
