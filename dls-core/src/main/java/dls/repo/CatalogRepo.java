package dls.repo;

import dls.vo.CatalogVO;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface CatalogRepo extends JpaRepository<CatalogVO, Long>,
        JpaSpecificationExecutor<CatalogVO>
{
    CatalogVO findByPathAndType(String path, char type);

    @Query(nativeQuery = true, value = "SELECT * FROM CATALOG WHERE path = :path AND type = :type AND :userId = ANY(permitted_users)")
    CatalogVO findByPathAndTypeAndPermittedUser(@Param("path") String path,
                                                @Param("type") char type,
                                                @Param("userId") Long userId);

    @Query(nativeQuery = true, value = "SELECT * FROM CATALOG WHERE type = :type AND parent IS NULL AND :userId = ANY(permitted_users)")
    List<CatalogVO> findByTypeAndParentIsNullAndPermittedUser(@Param("type") char type,
                                                              @Param("userId") Long userId);
    @Query(nativeQuery = true, value = "SELECT * FROM CATALOG WHERE type = :type AND :userId = ANY(permitted_users)")
    List<CatalogVO> findByTypeAndPermittedUser(@Param("type") char type,
                                               @Param("userId") Long userId);
    @Query(nativeQuery = true, value = "SELECT * FROM CATALOG WHERE type = :type AND  ( parent LIKE :parent OR parent LIKE :parentWildCard) AND :userId = ANY(permitted_users)")
    List<CatalogVO> findByTypeAndParentLikeAndPermittedUser(@Param("type") char type,
                                                            @Param("parent") String parent,
                                                            @Param("parentWildCard") String parentWildCard,
                                                            @Param("userId") Long userId);
    @Query(nativeQuery = true, value = "SELECT COUNT(*) FROM CATALOG WHERE type = :type AND parent IS NULL AND :userId = ANY(permitted_users)")
    Integer countByTypeAndParentIsNullAndPermittedUser(@Param("type") char type,
                                                       @Param("userId") Long userId);
    @Query(nativeQuery = true, value = "SELECT * FROM CATALOG WHERE type = :type AND  parent = :parent  AND :userId = ANY(permitted_users)")
    List<CatalogVO> findByTypeAndParentAndPermittedUser(@Param("type") char type,
                                                        @Param("parent") String parent,
                                                        @Param("userId") Long userId);
}
