package dls.repo;

import dls.vo.MetadataViewVO;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;

public interface MetadataViewRepo extends
        JpaRepository<MetadataViewVO, Long>,
        JpaSpecificationExecutor<MetadataViewVO> {

    List<MetadataViewVO> findByTypeIdIn(List<Long> typeIds);
}