package dls.service;

import com.google.common.base.Joiner;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import dls.bean.DlsResponse;
import dls.bean.Tenant;
import dls.bean.User;
import dls.exception.*;
import dls.repo.*;
import dls.vo.FileVO;
import dls.vo.TenantVO;
import dls.vo.UserVO;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ArrayUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Example;
import org.springframework.data.domain.ExampleMatcher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import java.util.stream.Stream;

import static org.springframework.http.HttpStatus.CONFLICT;

@Service
@Slf4j
@Transactional("transactionManager")
public class UserService {

	@Autowired private TenantRepo tenantRepo;
	@Autowired private UserRepo userRepo;
	
	@Autowired private LinkRepo linkRepo;
	@Autowired private FileShareRepo shareRepo;
	@Autowired private FileMetaRepo fileMetaRepo;
	@Autowired private FileRepo fileRepo;
	@Autowired private IFileManagementService dfsService;
//	@Autowired private DlsServiceHelper hService;
	@Value("${default.dls.admin.name}")
	private String dlsAdmin;
	
	public TenantVO authenticate(String apiKey) throws DlsSecurityException  {
		
		if(null == apiKey) throw new DlsSecurityException();
		
		return Optional.ofNullable(tenantRepo.findByApiKey(apiKey)).orElseThrow(DlsSecurityException::new);
				
	}

	@Cacheable("authorizations")
	public UserVO authorize(String apiKey, String dlsKey) throws DlsSecurityException, DlsPrivacyException {
		
		if(null == apiKey) throw new DlsSecurityException();
		
		TenantVO tenant = authenticate(apiKey);
		
		if(null == dlsKey) throw new DlsPrivacyException();
		
		ExampleMatcher matcher = ExampleMatcher.matchingAll().withIgnoreNullValues().withIgnorePaths("id");
		
		UserVO vo = UserVO.builder().tenant(tenant).dlsKey(dlsKey).build();
        
		return userRepo.findOne(Example.of(vo, matcher)).orElseThrow(DlsSecurityException::new);
	}

	public List<DlsResponse> register(TenantVO tenant, List<String> users) {

		List <DlsResponse> errors = Lists.newArrayList();
		List<DlsResponse> responses = Flux.fromIterable(users)
				.map(u -> UserVO.builder().tenant(tenant).dlsKey(generateDlsKey(null)).admin(false).dlsUser(u).build())
				.doOnNext(userRepo::save)
				.onErrorContinue((e, o) -> errors.add(DlsResponse.builder().key("user").value(o.toString())
						.code(CONFLICT.value()).messages(Set.of(GlobalExceptionHandler.ALREADY_EXISTS)).build()))
				.map(u -> DlsResponse.builder().messages(Set.of(GlobalExceptionHandler.CREATED))
						.code(HttpStatus.CREATED.value()).value(u.getDlsUser()).key(u.getDlsKey()).build())
				.collectList().block();
		Optional.ofNullable(responses).ifPresent(errors::addAll);
		return errors ;
	}
	
	public List<DlsResponse> registerWithKey(TenantVO tenant, List<User> users) {

//		List <String> duplicates = Lists.newArrayList();
		List<DlsResponse> errors = Lists.newArrayList();

		List<DlsResponse> responses = Flux.fromIterable(users)
				.map(u -> UserVO.builder()
						.tenant(tenant)
						.admin(Boolean.FALSE)
						.dlsKey(u.getDlsKey())
						.dlsUser(u.getDlsUser()).build())
				.doOnError(e -> log.error("error ... {}", e.getMessage()))
				.doOnNext(userRepo::save)
				.map(u -> DlsResponse.builder().messages(Set.of(GlobalExceptionHandler.CREATED))
						.code(HttpStatus.CREATED.value())
						.value(u.getDlsKey())
						.key(u.getDlsUser())
						.build())
//				.doOnError(Throwable::printStackTrace)

				.onErrorContinue((e, u) -> errors.add(DlsResponse.builder().messages(Set.of(GlobalExceptionHandler.ALREADY_EXISTS))
						.code(CONFLICT.value())
						.value(((UserVO) u).getDlsKey())
						.key(((UserVO) u).getDlsUser())
						.build()))
				.collectList().block();
		;


		Optional.ofNullable(responses).ifPresent(errors::addAll);
//		if(null != responses) {
//			responses.addAll(responses);
//		}

		return errors;
	}

	public List <User> getUser(TenantVO tenant, String dlsUser) throws DlsNotFoundException {

		ExampleMatcher matcher = ExampleMatcher.matchingAll().withIgnoreNullValues().withIgnorePaths("id");

		UserVO vo = UserVO.builder().tenant(tenant).dlsUser(dlsUser).build();

		List <UserVO> vos = Optional.of(userRepo.findAll(Example.of(vo, matcher))).orElseThrow(DlsNotFoundException::new);

		List <User> users = Lists.newArrayList();

		vos.forEach(v -> {

			User u = User.builder().build();
			BeanUtils.copyProperties(v, u);
			users.add(u);
		});
		return users;
	}

	public List<Map <String, String>> getUserRole(String apiKey, String dlsKey, String dlsUser) throws DlsSecurityException, DlsPrivacyException, DlsNotFoundException {

		UserVO userVO = authorize(apiKey, dlsKey);

		if(null == userVO.getAdmin() || !userVO.getAdmin()) {
			if(null != dlsUser && !dlsUser.equalsIgnoreCase(userVO.getDlsUser())) {
				throw new DlsPrivacyException();
			} else {
				return Lists.newArrayList(userToMap(userVO));
			}
		} else {
			List<Map <String, String>> list = Lists.newArrayList();
			List<UserVO> users = (dlsUser == null) ? userRepo.findByTenant(userVO.getTenant())
					: userRepo.findUserListForUsers(Lists.newArrayList(dlsUser), userVO.getTenant().getId());
			if(null == users || users.isEmpty()) {
				throw new DlsNotFoundException();
			}
			users.forEach(u -> {
				list.add(userToMap(u));
			});
			return list;

		}



	}

	public void updateUserRole(UserVO loggedInUser, String dlsUser, String orgPosition, Boolean createAdmin) {

		final String organization = loggedInUser.getTenant().getOrganization();
		String[] orgPositions = loggedInUser.getOrgPosition();
		if(null == organization || null == orgPositions){
			throw new DataIntegrityViolationException("inconsistent.tenant.organization");
		}
		if(!orgPosition.startsWith(organization.concat("/"))) {
			throw new DataIntegrityViolationException("inconsistent.tenant.organization");
		}
		// I need upper org position to create admin
		if(createAdmin &&
				Stream.of(orgPositions)
						.map(s -> s.concat("/"))
						.noneMatch(orgPosition::startsWith)
		)
//				! orgPosition.startsWith(loggedInUser.getOrgPosition().concat("/")))
		{
			throw new DataIntegrityViolationException("insufficient.org.position");
		}

		if(!createAdmin && ! (
				Stream.of(orgPositions)
						.anyMatch(orgPosition::contentEquals) ||

//				orgPosition.contentEquals(loggedInUser.getOrgPosition()) ||

//				orgPosition.startsWith(loggedInUser.getOrgPosition().concat("/"))
						Stream.of(orgPositions)
								.map(s -> s.concat("/"))
								.anyMatch(orgPosition::startsWith)
		)) {
			throw new DataIntegrityViolationException("insufficient.org.position");
		}

		String parentOrgPos = orgPosition.substring(0, orgPosition.lastIndexOf('/'));
		if(Optional.ofNullable(userRepo.getCountContainingOrgPos(loggedInUser.getTenant().getId(), parentOrgPos)) .orElse(0L) == 0) {
			log.error("Parent org_position does not exist {}", parentOrgPos);
			throw new DataIntegrityViolationException("parent.orgPost.not.exists");
		}
		UserVO userVO = userRepo.findByTenantAndDlsUser(loggedInUser.getTenant(), dlsUser);
		if(null == userVO) {
			throw new DataIntegrityViolationException("invalid.dlsUser");
		}

		if(null != userVO.getOrgPosition() &&
				Stream.of(userVO.getOrgPosition()).anyMatch(o -> o.equalsIgnoreCase(orgPosition))) {
			throw new DataIntegrityViolationException("already.exists");
		}
		userVO.setOrgPosition(ArrayUtils.add(userVO.getOrgPosition(), orgPosition));
		userVO.setAdmin(createAdmin);
		userVO.setLastUpdatedBy(loggedInUser);
		userRepo.save(userVO);

	}



	public void deleteUserRole(UserVO loggedInUser, String dlsUser) {

		UserVO userVO = Optional.ofNullable(userRepo.findByTenantAndDlsUser(loggedInUser.getTenant(), dlsUser))
				.orElseThrow(() -> new DataIntegrityViolationException("invalid.dlsUser"));

		userVO.setOrgPosition(null);
		userVO.setAdmin(false);
		userVO.setLastUpdatedOn(Timestamp.from(Instant.now()));
		userVO.setLastUpdatedBy(loggedInUser);
		userRepo.save(userVO);

	}



	private Map<String, String> userToMap(UserVO userVO) {
		Map <String, String> userMap = Maps.newHashMap();
		userMap.put("user-name", userVO.getDlsUser());
		userMap.put("admin-user", Optional.ofNullable(userVO.getAdmin()).orElse(Boolean.FALSE).toString());
		Optional.ofNullable(userVO.getOrgPosition()).ifPresent(s -> userMap.put("org-pos", Joiner.on(',').skipNulls().join(s)));

		return userMap;
	}

	public List <String> getAllUserNames(TenantVO tenant) throws DlsNotFoundException {
		
		ExampleMatcher matcher = ExampleMatcher.matchingAll().withIgnoreNullValues().withIgnorePaths("id");
		
		UserVO vo = UserVO.builder().tenant(tenant).build();
		
		List <UserVO> vos = Optional.of(userRepo.findAll(Example.of(vo, matcher))).orElseThrow(DlsNotFoundException::new);
				
		List <String> users = Lists.newArrayList();
		
		vos.forEach(v -> users.add(v.getDlsUser()));
		return users;
	}


	@Transactional("transactionManager")
	public String provision(Tenant t) {

		String organization = t.getOrganization();
		TenantVO tenant = TenantVO.builder().admin(false).organization(organization).apiKey(t.getApiKey()).tcupUser(t.getTcupUser()).build();
		tenantRepo.saveAndFlush(tenant);


		if(null != t.getDlsAdminUser()) {
			dlsAdmin = t.getDlsAdminUser();
		}
		UserVO user = UserVO.builder()
				.admin(true)
				.tenant(tenant)
				.dlsKey(generateDlsKey(t.getDlsAdminKey()))
				.dlsUser(dlsAdmin)
				.build();
		Optional.ofNullable(organization)
				.ifPresent(o -> user.setOrgPosition(new String[]{o}));

		userRepo.saveAndFlush(user);
		return "Tenant created : ".concat(t.getTcupUser()).concat(" with Admin DLS key ").concat(user.getDlsKey());
	}
	
	private String generateDlsKey(String adminDlsKey) {		
		return (adminDlsKey == null) ? UUID.randomUUID().toString() : adminDlsKey;
	}

	public String getDlsAdminKey(String tenantAPIKey) throws DlsNotFoundException {
		
		TenantVO tenant = Optional.ofNullable(tenantRepo.findByApiKey(tenantAPIKey)).orElseThrow(DlsNotFoundException::new);
		List<UserVO> vos = Optional.ofNullable(userRepo.findByTenantAndAdmin(tenant, Boolean.TRUE)).orElseThrow(DlsNotFoundException::new);
		List <String> dlsKeys = Lists.newArrayList();
		vos.forEach(vo -> dlsKeys.add(vo.getDlsKey()));
		return Joiner.on(",").join(dlsKeys);
	}

	
	public void deleteTenant(String apiKey, String username) throws DlsSecurityException, DlsPrivacyException, DlsNotFoundException {
		
		TenantVO tenant = authenticate(apiKey);
		if(!tenant.getAdmin()) {
			throw new DlsPrivacyException();
		}
				
		List<UserVO> users = userRepo.findByTenant(tenant);
		if(users.isEmpty() || users.stream().noneMatch(u -> u.getAdmin() && username.equals(u.getDlsUser()))) {
			throw new DlsNotFoundException();
		}
		
		if(users.size() > 1) {
			throw new DataIntegrityViolationException("user.exists");
		}
		
		UserVO user = users.stream()
				.filter(u -> u.getAdmin() && username.equals(u.getDlsUser()))
				.findFirst()
				.orElseThrow(DlsNotFoundException::new);

		String path = tenant.getTcupUser().concat("/").concat(user.getDlsUser());
		dfsService.delete(true, path);
//		dfsService.deleteFileLocal(path);

		log.info("{} links deleted", linkRepo.deleteByUser(user));
		log.info("{} shares deleted", shareRepo.deleteByUser(user));
		log.info("{} metadata deleted", fileMetaRepo.deleteByUser(user));
		log.info("{} files deleted", fileRepo.deleteByUser(user));
		userRepo.delete(user);
		
	}

	public void deleteUser(String apiKey, String dlsKey, String username) throws DlsSecurityException, DlsPrivacyException, DlsNotFoundException {
		
		UserVO authenticationUser = authorize(apiKey, dlsKey);
		if(!authenticationUser.getAdmin()) {
			throw new DlsPrivacyException();
		}
		
		UserVO user = Optional.ofNullable(userRepo.findByTenantAndDlsUser(authenticationUser.getTenant(), username)).orElseThrow(DlsNotFoundException::new);
		String path = authenticationUser.getTenant().getTcupUser().concat("/").concat(user.getDlsUser());
		dfsService.delete(true, path);
//		dfsService.deleteFileLocal(path);
		List <FileVO> files = fileRepo.findByUser(user);
		log.info("{} links deleted", linkRepo.deleteByUser(user));
		log.info("{} shares deleted", shareRepo.deleteByUser(user));
		files.forEach(f -> log.info("{} shares deleted", shareRepo.deleteByFile(f)));
		
		log.info("{} metadata deleted", fileMetaRepo.deleteByUser(user));
		
		log.info("{} files deleted", fileRepo.deleteByUser(user));
		userRepo.delete(user);
		
	}

	public void updateOrganization(String tenant, String organization) throws DlsNotFoundException {

		TenantVO tenantVO = tenantRepo.findByTcupUser(tenant);
		if(null == tenantVO) {
			throw new DlsNotFoundException();
		}
		String oldOrg = tenantVO.getOrganization();

		if(null != oldOrg && userRepo.countByTenantWithNullOrgPos(tenantVO.getId()) > 0) {
			throw new DlsValidationException("user.org.exists");
		}
		tenantVO.setOrganization(organization);

		userRepo.findByTenantAndAdmin(tenantVO, true)
				.forEach(u -> u.setOrgPosition((organization == null) ? null : new String[]{organization}));

		tenantRepo.save(tenantVO);

	}
	
	public UserVO getUserVO(TenantVO tenant, String dlsUser) throws DlsNotFoundException {
		
		ExampleMatcher matcher = ExampleMatcher.matchingAll().withIgnoreNullValues().withIgnorePaths("id");
		
		UserVO vo = UserVO.builder().tenant(tenant).dlsUser(dlsUser).build();
		
		List <UserVO> vos = Optional.of(userRepo.findAll(Example.of(vo, matcher))).orElseThrow(DlsNotFoundException::new);
		
		return vos.get(0);
	} 
	
//	public void updateUserGroup(UserVO loggedInUserVO, List<String> users, String groupName) throws DlsNotFoundException {
//		 List<String> distinctUsersList=users.stream().distinct().collect(Collectors.toList());
//		 Long userCount=userRepo.countByUsers(users, loggedInUserVO.getTenant().getId());
//		if (userCount != distinctUsersList.size())
//			 throw new DlsValidationException("All provided users dont exist");
//		//check permission based on Org Hierarchy
//		for (String user : distinctUsersList) {
//			hService.validateOrgRoleAccess(loggedInUserVO,user,loggedInUserVO.getTenant());
//		}
//		 List<UserVO> usersVo = userRepo.findUserListForUsers(distinctUsersList,loggedInUserVO.getTenant().getId());
//		  for (UserVO userVO : usersVo) {
//			  String[] group= userVO.getGroups();
//			  List<String> listGroup=
//					  new ArrayList<String>();
//			  if(group!=null)
//			  listGroup.addAll(Arrays.asList(group));
//			  if(listGroup.contains(groupName))
//				  throw new  DlsValidationException("User already exists in the group");
//			  listGroup.add(groupName);
//			  group=listGroup.toArray(new String[listGroup.size()]);
//			  userVO.setGroups(group);
//		}
//		 userRepo.saveAll(usersVo);
//
//
//	}
//
//	public void deleteUserGroup(UserVO loggedInUserVO, String groupName, String user) throws DlsNotFoundException {
//
//		UserVO usr = null;
//		List<UserVO> usersVoList = userRepo.findUsersContaining(groupName, loggedInUserVO.getTenant().getId());
//		 System.out.println("usersVoList ::: "+usersVoList);
//		 if(usersVoList == null || usersVoList.isEmpty())
//			 throw new DlsValidationException("group.does.not.exist");
//
//		 if(user == null || user.isEmpty())
//		 {
//
//			 LinkedList<String> groupList = new LinkedList<String>();
//			 String[] groupListArr = {};
//			  for (UserVO userVO : usersVoList) {
//				  hService.validateOrgRoleAccess(loggedInUserVO, userVO.getDlsUser(), loggedInUserVO.getTenant());
//				  if(userVO.getGroups().length > 1)
//				  {
//					  groupList = new LinkedList<String>(Arrays.asList(userVO.getGroups()));
//					  if(groupList.contains(groupName))
//					  groupList.remove(groupName);
//					  groupListArr = groupList.toArray(new String[groupList.size()]);
//					  userVO.setGroups(groupListArr);
//				  }
//				  else
//				  userVO.setGroups(null);
//			}
//
//			 userRepo.saveAll(usersVoList);
//		 }
//		 else
//		 {
//			 usr = userRepo.findByTenantAndDlsUser(loggedInUserVO.getTenant(), user);
//			 if(usr == null)
//				 throw new DlsValidationException("user.does.not.exist");
//			 else
//			 usr = userRepo.findUserContainingGroup(groupName, user, loggedInUserVO.getTenant().getId());
//			 if(usr != null)
//			 {
//				 hService.validateOrgRoleAccess(loggedInUserVO, usr.getDlsUser(), loggedInUserVO.getTenant());
//				 usr.setGroups(null);
//			 }
//			 else
//				 throw new DlsValidationException("user.does.not.belong.to.group");
//
//		 }
//
//
//
//
//	}

    public String setStorage(String tcupUser, Long storageLimit) throws DlsNotFoundException {

		TenantVO tenant = Optional.ofNullable(tenantRepo.findByTcupUser(tcupUser)).orElseThrow(DlsNotFoundException::new);
		tenant.setAllocatedStorage(storageLimit);

		return "UPDATED";
    }

	public Map<String,Long> getStorage(String tcupUser) throws DlsNotFoundException {

		TenantVO tenant = Optional.ofNullable(tenantRepo.findByTcupUser(tcupUser)).orElseThrow(DlsNotFoundException::new);
		Map <String,Long> storage = Maps.newHashMap();
		Long allocatedStorage = Optional.ofNullable(tenant.getAllocatedStorage()).orElse(0L);
		storage.put("allocated-storage", allocatedStorage);
		Long usedStorage = Optional.ofNullable(fileRepo.getUsedStorage(tenant.getId())).orElse(0L);
		storage.put("used-storage", usedStorage);
		Long availableStorage = allocatedStorage - usedStorage;
		storage.put("available-storage", (availableStorage < 0) ? 0 : availableStorage);
		return storage;
	}
}
