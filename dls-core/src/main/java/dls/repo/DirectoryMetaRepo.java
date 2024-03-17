package dls.repo;

import dls.vo.DirectoryMetaVO;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DirectoryMetaRepo extends JpaRepository<DirectoryMetaVO, Long> {
//	DirectoryMetaVO save(DirectoryMetaVO model);
//	DirectoryMetaVO findByNameAndDirectoryId(String name,Long directoryId);
//	List<DirectoryMetaVO> findByDirectoryId(Long directoryId);
	@Modifying
	@Query("delete from directory_meta t where t.id = ?1")
	void delete(Long entityId);
	DirectoryMetaVO findByDirectoryIdAndNameAndIsMeta(@Param("directory_id") Long directoryId, @Param("name") String name, @Param("is_meta") Boolean isMeta);


    void deleteByDirectoryIdAndIsMeta(@Param("directory_id") Long id, @Param("is_meta") boolean b);
}
