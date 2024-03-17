package dls.repo;

import dls.vo.LinkVO;
import dls.vo.UserVO;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface LinkRepo extends JpaRepository<LinkVO, Long> {
	
	@Query(value="SELECT DISTINCT RELATION FROM LINK ORDER BY RELATION", nativeQuery=true)
	List <String> getAllRelationNames();

	Integer deleteByUser(UserVO user);

}
