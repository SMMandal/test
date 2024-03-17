package dls.service;

import dls.exception.DlsNotFoundException;
import dls.exception.DlsPrivacyException;
import dls.exception.DlsSecurityException;
import dls.repo.PermissionRepo;
import dls.repo.TenantRepo;
import dls.repo.UserRepo;
import dls.vo.DirectoryVO;
import dls.vo.PermissionVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@Slf4j
public class PermissionService {

	private static final String DLS_NS = "dls:";
	@Autowired private UserService uservice;
	@Autowired private PermissionRepo permissionRepo;
	@Autowired private UserRepo userRepo;
	@Autowired private TenantRepo tenantRepo;


	public List<PermissionVO> getPermission(DirectoryVO directory) throws DlsSecurityException, DlsPrivacyException, DlsNotFoundException {
		return  permissionRepo.findByDirectory(directory);
	}
	
	public List<Long> getWritePermittedUsers(DirectoryVO directory,Long tenantId){
		return permissionRepo.getWritePermittedUsers(tenantId, directory.getId());		
	}
}
