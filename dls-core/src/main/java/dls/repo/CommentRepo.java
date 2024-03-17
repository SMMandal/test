package dls.repo;

import dls.vo.CommentsVO;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;

public interface CommentRepo  extends JpaRepository<CommentsVO, Long> ,
        JpaSpecificationExecutor<CommentsVO> {
	
	List<CommentsVO> findByFileId(Long fileId);

}
