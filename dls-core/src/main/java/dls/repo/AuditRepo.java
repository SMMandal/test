package dls.repo;


import dls.vo.AuditVO;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AuditRepo extends JpaRepository<AuditVO, Long> {

    List<AuditVO> findByEntityAndEntityIdInAndUserId(String entity, List<Long> entityId, Long userId);
    List<AuditVO> findByEntityContains(String entity, Pageable pageable);

}
