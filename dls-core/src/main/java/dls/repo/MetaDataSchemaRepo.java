package dls.repo;

import dls.vo.MetaSchemaVO;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MetaDataSchemaRepo extends JpaRepository<MetaSchemaVO, Long> {
	MetaSchemaVO save(MetaSchemaVO model);
//	@Cacheable
	List<MetaSchemaVO> findByTenantId(Long tenantId);
	MetaSchemaVO findByNameAndTenantId(String name,Long tenantId);
	MetaSchemaVO findByName(String name);
	MetaSchemaVO findByNameAndDeleted(String name,Boolean deleted);
	MetaSchemaVO findByNameIgnoreCaseAndTenantIdAndDeleted(String name, Long tenantId, Boolean deleted);
	MetaSchemaVO findByNameIgnoreCaseAndTenantIdAndDeletedAndType(String name, Long tenantId, Boolean deleted, String type);
	List <MetaSchemaVO> findByTenantIdAndType(Long tenantId, String type);
	void deleteByName(String name);
	void deleteByTenantId(Long tenantId);
}
