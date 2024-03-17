package dls.repo;

import dls.vo.TenantVO;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TenantRepo extends JpaRepository <TenantVO, Long> {

	TenantVO findByApiKey(String apiKey);
	TenantVO findByTcupUser(String tcupUser);
	
}
