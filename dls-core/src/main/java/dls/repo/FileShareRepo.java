package dls.repo;

import dls.vo.FileShareVO;
import dls.vo.FileVO;
import dls.vo.UserVO;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface FileShareRepo extends JpaRepository<FileShareVO, Long> {

	Integer deleteByUser(UserVO user);

	Integer deleteByFile(FileVO file);

	List<FileShareVO> findByFileId(Long fileId);

	Long countByUserId(Long userId);

	List <FileShareVO> findByUserIdAndFileId(Long userId, Long fileId);


}
