package dls.repo;

import dls.vo.CatalogTextSearchVO;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface CatalogTextSearchRepo extends JpaRepository<CatalogTextSearchVO, Long> , JpaSpecificationExecutor<CatalogTextSearchVO> {

    @Query(value = "select * from catalog_text_search where user_id = :userId and fs_path = :fsPath",
            nativeQuery = true)
    List<CatalogTextSearchVO> getFilePath(@Param("userId") Long userId, @Param("fsPath") String fsPath);


    @Query(value = "select * from catalog_text_search where deleted = false and fs_path = :fsPath",
            nativeQuery = true)
    CatalogTextSearchVO getByFilePathNoDeleted(@Param("fsPath") String fsPath);
}
