package software.wings.service.impl;

import static org.mongodb.morphia.mapping.Mapper.ID_KEY;
import static software.wings.dl.MongoHelper.setUnset;
import static software.wings.dl.PageRequest.PageRequestBuilder.aPageRequest;

import com.google.inject.Inject;
import com.google.inject.Singleton;

import org.mongodb.morphia.query.Query;
import org.mongodb.morphia.query.UpdateOperations;
import org.mongodb.morphia.query.UpdateResults;
import software.wings.beans.Account;
import software.wings.beans.SearchFilter.Operator;
import software.wings.beans.User;
import software.wings.beans.security.UserGroup;
import software.wings.dl.PageRequest;
import software.wings.dl.PageResponse;
import software.wings.dl.WingsPersistence;
import software.wings.service.intfc.AccountService;
import software.wings.service.intfc.AppService;
import software.wings.service.intfc.AuthService;
import software.wings.service.intfc.ServiceResourceService;
import software.wings.service.intfc.UserGroupService;
import software.wings.service.intfc.UserService;
import software.wings.utils.Validator;

import java.util.List;
import java.util.stream.Collectors;
import javax.validation.executable.ValidateOnExecution;

/**
 * Created by rishi
 */
@ValidateOnExecution
@Singleton
public class UserGroupServiceImpl implements UserGroupService {
  @Inject private WingsPersistence wingsPersistence;
  @Inject private UserService userService;
  @Inject private AccountService accountService;
  @Inject private AppService appService;
  @Inject private ServiceResourceService serviceResourceService;
  @Inject private AuthService authService;

  @Override
  public UserGroup save(UserGroup userGroup) {
    Validator.notNullCheck("accountId", userGroup.getAccountId());
    UserGroup savedUserGroup = Validator.duplicateCheck(
        () -> wingsPersistence.saveAndGet(UserGroup.class, userGroup), "name", userGroup.getName());
    evictUserPermissionInfoCacheForUserGroup(savedUserGroup);
    return savedUserGroup;
  }

  @Override
  public PageResponse<UserGroup> list(String accountId, PageRequest<UserGroup> req) {
    Validator.notNullCheck("accountId", accountId);
    Account account = accountService.get(accountId);
    Validator.notNullCheck("account", account);
    req.addFilter("accountId", Operator.EQ, accountId);
    PageResponse<UserGroup> res = wingsPersistence.query(UserGroup.class, req);
    res.getResponse().forEach(userGroup -> loadUsers(userGroup, account));
    return res;
  }

  @Override
  public UserGroup get(String accountId, String userGroupId) {
    return get(accountId, userGroupId, true);
  }

  private UserGroup get(String accountId, String userGroupId, boolean loadUsers) {
    PageRequest<UserGroup> req = aPageRequest()
                                     .addFilter("accountId", Operator.EQ, accountId)
                                     .addFilter(ID_KEY, Operator.EQ, userGroupId)
                                     .build();
    UserGroup userGroup = wingsPersistence.get(UserGroup.class, req);
    if (loadUsers && userGroup != null) {
      Account account = accountService.get(accountId);
      loadUsers(userGroup, account);
    }

    return userGroup;
  }

  private void loadUsers(UserGroup userGroup, Account account) {
    if (userGroup.getMemberIds() != null) {
      PageRequest<User> req = aPageRequest()
                                  .addFilter(ID_KEY, Operator.IN, userGroup.getMemberIds().toArray())
                                  .addFilter("accounts", Operator.IN, account)
                                  .build();
      PageResponse<User> res = userService.list(req);
      userGroup.setMembers(res.getResponse());
    }
  }

  @Override
  public UserGroup updateOverview(UserGroup userGroup) {
    Validator.notNullCheck("name", userGroup.getName());
    UpdateOperations<UserGroup> operations =
        wingsPersistence.createUpdateOperations(UserGroup.class).set("name", userGroup.getName());
    setUnset(operations, "description", userGroup.getDescription());
    return update(userGroup, operations);
  }

  @Override
  public UserGroup updateMembers(UserGroup userGroup) {
    List<String> memberIds = null;
    if (userGroup.getMembers() != null) {
      memberIds = userGroup.getMembers().stream().map(User::getUuid).collect(Collectors.toList());
    }
    UpdateOperations<UserGroup> operations = wingsPersistence.createUpdateOperations(UserGroup.class);
    setUnset(operations, "memberIds", memberIds);
    UserGroup updatedUserGroup = update(userGroup, operations);
    evictUserPermissionInfoCacheForUserGroup(updatedUserGroup);
    return updatedUserGroup;
  }

  @Override
  public UserGroup updatePermissions(UserGroup userGroup) {
    UpdateOperations<UserGroup> operations = wingsPersistence.createUpdateOperations(UserGroup.class);
    setUnset(operations, "appPermissions", userGroup.getAppPermissions());
    setUnset(operations, "accountPermissions", userGroup.getAccountPermissions());
    UserGroup updatedUserGroup = update(userGroup, operations);
    evictUserPermissionInfoCacheForUserGroup(updatedUserGroup);
    return updatedUserGroup;
  }

  private UserGroup update(UserGroup userGroup, UpdateOperations<UserGroup> operations) {
    Validator.notNullCheck("uuid", userGroup.getUuid());
    Validator.notNullCheck("accountId", userGroup.getAccountId());
    Query<UserGroup> query = wingsPersistence.createQuery(UserGroup.class)
                                 .field(ID_KEY)
                                 .equal(userGroup.getUuid())
                                 .field("accountId")
                                 .equal(userGroup.getAccountId());
    UpdateResults updateResults = wingsPersistence.update(query, operations);
    if (updateResults.getUpdatedCount() > 0) {
      evictUserPermissionInfoCacheForUserGroup(userGroup);
    }
    return get(userGroup.getAccountId(), userGroup.getUuid());
  }

  @Override
  public boolean delete(String accountId, String userGroupId) {
    UserGroup userGroup = get(accountId, userGroupId, false);
    Validator.notNullCheck("userGroup", userGroup);
    Query<UserGroup> userGroupQuery = wingsPersistence.createQuery(UserGroup.class)
                                          .field(UserGroup.ACCOUNT_ID_KEY)
                                          .equal(accountId)
                                          .field(ID_KEY)
                                          .equal(userGroupId);
    boolean deleted = wingsPersistence.delete(userGroupQuery);
    if (deleted) {
      evictUserPermissionInfoCacheForUserGroup(userGroup);
    }
    return deleted;
  }

  private void evictUserPermissionInfoCacheForUserGroup(UserGroup userGroup) {
    authService.evictAccountUserPermissionInfoCache(userGroup.getAccountId(), userGroup.getMembers());
  }
}
