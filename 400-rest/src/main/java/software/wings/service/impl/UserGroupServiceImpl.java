/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.beans.PageRequest.PageRequestBuilder.aPageRequest;
import static io.harness.beans.SearchFilter.Operator.EQ;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.exception.WingsException.USER;
import static io.harness.mongo.MongoConfig.NO_LIMIT;
import static io.harness.mongo.MongoUtils.setUnset;
import static io.harness.persistence.HQuery.excludeAuthority;
import static io.harness.validation.Validator.notNullCheck;
import static io.harness.validation.Validator.unEqualCheck;

import static software.wings.beans.security.UserGroup.DEFAULT_ACCOUNT_ADMIN_USER_GROUP_NAME;
import static software.wings.beans.security.UserGroupSearchTermType.APPLICATION_NAME;
import static software.wings.security.PermissionAttribute.Action.ABORT_WORKFLOW;
import static software.wings.security.PermissionAttribute.Action.EXECUTE;
import static software.wings.security.PermissionAttribute.Action.EXECUTE_PIPELINE;
import static software.wings.security.PermissionAttribute.Action.EXECUTE_WORKFLOW;
import static software.wings.security.PermissionAttribute.Action.EXECUTE_WORKFLOW_ROLLBACK;
import static software.wings.security.PermissionAttribute.PermissionType.ALL_APP_ENTITIES;
import static software.wings.security.PermissionAttribute.PermissionType.APP_TEMPLATE;
import static software.wings.security.PermissionAttribute.PermissionType.CE_ADMIN;
import static software.wings.security.PermissionAttribute.PermissionType.CE_VIEWER;
import static software.wings.security.PermissionAttribute.PermissionType.DEPLOYMENT;
import static software.wings.security.PermissionAttribute.PermissionType.USER_PERMISSION_MANAGEMENT;
import static software.wings.security.PermissionAttribute.PermissionType.USER_PERMISSION_READ;

import static dev.morphia.mapping.Mapper.ID_KEY;
import static java.lang.Boolean.TRUE;
import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.emptySet;
import static java.util.Collections.singletonList;
import static java.util.function.Function.identity;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.elasticsearch.common.util.set.Sets.newHashSet;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.beans.FeatureName;
import io.harness.beans.PageRequest;
import io.harness.beans.PageRequest.PageRequestBuilder;
import io.harness.beans.PageResponse;
import io.harness.beans.SearchFilter;
import io.harness.beans.SearchFilter.Operator;
import io.harness.ccm.config.CCMSettingService;
import io.harness.data.structure.EmptyPredicate;
import io.harness.eraro.ErrorCode;
import io.harness.event.handler.impl.EventPublishHelper;
import io.harness.exception.GeneralException;
import io.harness.exception.InvalidArgumentsException;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.UserGroupAlreadyExistException;
import io.harness.exception.WingsException;
import io.harness.ff.FeatureFlagService;
import io.harness.mongo.MongoPersistence;
import io.harness.persistence.HIterator;
import io.harness.persistence.HPersistence;
import io.harness.persistence.UuidAware;
import io.harness.scheduler.PersistentScheduler;

import software.wings.beans.Account;
import software.wings.beans.Application;
import software.wings.beans.Application.ApplicationKeys;
import software.wings.beans.Base;
import software.wings.beans.EntityType;
import software.wings.beans.Event.Type;
import software.wings.beans.User;
import software.wings.beans.User.UserKeys;
import software.wings.beans.UserGroupEntityReference;
import software.wings.beans.UserInvite;
import software.wings.beans.notification.NotificationSettings;
import software.wings.beans.security.AccountPermissions;
import software.wings.beans.security.AppPermission;
import software.wings.beans.security.UserGroup;
import software.wings.beans.security.UserGroup.UserGroupKeys;
import software.wings.beans.security.UserGroupSearchTermType;
import software.wings.beans.sso.SSOSettings;
import software.wings.beans.sso.SSOType;
import software.wings.dl.WingsPersistence;
import software.wings.features.RbacFeature;
import software.wings.features.api.UsageLimitedFeature;
import software.wings.scheduler.LdapGroupSyncJobHelper;
import software.wings.security.AppFilter;
import software.wings.security.EnvFilter;
import software.wings.security.ExecutableElementsFilter;
import software.wings.security.Filter;
import software.wings.security.GenericEntityFilter;
import software.wings.security.PermissionAttribute;
import software.wings.security.PermissionAttribute.PermissionType;
import software.wings.security.UserPermissionInfo;
import software.wings.security.UserThreadLocal;
import software.wings.service.UserGroupUtils;
import software.wings.service.impl.workflow.UserGroupDeleteEventHandler;
import software.wings.service.intfc.AccountService;
import software.wings.service.intfc.AppService;
import software.wings.service.intfc.AuthService;
import software.wings.service.intfc.SSOSettingService;
import software.wings.service.intfc.UserGroupService;
import software.wings.service.intfc.UserService;
import software.wings.service.intfc.pagerduty.PagerDutyService;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Sets;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import com.mongodb.ReadPreference;
import dev.morphia.query.CountOptions;
import dev.morphia.query.FindOptions;
import dev.morphia.query.Query;
import dev.morphia.query.UpdateOperations;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import javax.validation.constraints.NotNull;
import javax.validation.executable.ValidateOnExecution;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.hibernate.validator.constraints.NotBlank;

@OwnedBy(PL)
@ValidateOnExecution
@Singleton
@Slf4j
@TargetModule(HarnessModule._360_CG_MANAGER)
public class UserGroupServiceImpl implements UserGroupService {
  public static final String DEFAULT_USER_GROUP_DESCRIPTION = "Default account admin user group";

  private static final Pattern userGroupNamePattern = Pattern.compile("^[a-zA-Z0-9 -._]*$");

  @Inject private ExecutorService executors;
  @Inject private WingsPersistence wingsPersistence;
  @Inject private MongoPersistence mongoPersistence;
  @Inject private UserService userService;
  @Inject private AccountService accountService;
  @Inject private AuthService authService;
  @Inject private SSOSettingService ssoSettingService;
  @Inject private PagerDutyService pagerDutyService;
  @Inject private EventPublishHelper eventPublishHelper;
  @Inject private UserGroupDeleteEventHandler userGroupDeleteEventHandler;
  @Inject private AuditServiceHelper auditServiceHelper;
  @Inject private CCMSettingService ccmSettingService;
  @Inject @Named("BackgroundJobScheduler") private PersistentScheduler jobScheduler;
  @Inject @Named(RbacFeature.FEATURE_NAME) private UsageLimitedFeature rbacFeature;
  @Inject private FeatureFlagService featureFlagService;
  @Inject private LdapGroupSyncJobHelper ldapGroupSyncJobHelper;
  @Inject private AppService appService;

  @Override
  public UserGroup save(UserGroup userGroup) {
    notNullCheck(UserGroupKeys.accountId, userGroup.getAccountId());
    checkUserGroupsCountWithinLimit(userGroup.getAccountId());
    checkForUserGroupWithEmptyName(userGroup);
    checkForUserGroupWithSameName(userGroup);
    validateUserGroupName(userGroup.getName());

    if (null == userGroup.getNotificationSettings()) {
      NotificationSettings notificationSettings = new NotificationSettings(false, true, emptyList(), null, "", "");
      userGroup.setNotificationSettings(notificationSettings);
    }
    AccountPermissions accountPermissions =
        Optional.ofNullable(userGroup.getAccountPermissions()).orElse(AccountPermissions.builder().build());
    userGroup.setAccountPermissions(addDefaultCePermissions(accountPermissions));
    UserGroup savedUserGroup = wingsPersistence.saveAndGet(UserGroup.class, userGroup);
    Account account = accountService.get(userGroup.getAccountId());
    notNullCheck("account", account);
    loadUsers(savedUserGroup, account);
    evictUserPermissionInfoCacheForUserGroup(savedUserGroup);

    if (!ccmSettingService.isCloudCostEnabled(savedUserGroup.getAccountId())) {
      maskCePermissions(savedUserGroup);
    }

    auditServiceHelper.reportForAuditingUsingAccountId(account.getUuid(), null, userGroup, Type.CREATE);
    log.info("Auditing creation of new userGroup={} and account={}", userGroup.getName(), account.getAccountName());
    eventPublishHelper.publishSetupRbacEvent(userGroup.getAccountId(), savedUserGroup.getUuid(), EntityType.USER_GROUP);
    return savedUserGroup;
  }

  private void validateUserGroupName(String name) {
    Matcher matcher = userGroupNamePattern.matcher(name);

    if (!matcher.matches()) {
      throw new InvalidRequestException("User group name is not valid.");
    }
  }

  private AccountPermissions addDefaultCePermissions(AccountPermissions accountPermissions) {
    Set<PermissionType> accountPermissionsSet =
        accountPermissions == null || CollectionUtils.isEmpty(accountPermissions.getPermissions())
        ? new HashSet<>()
        : accountPermissions.getPermissions();

    accountPermissionsSet.add(PermissionType.CE_VIEWER);
    if (accountPermissionsSet.contains(PermissionType.ACCOUNT_MANAGEMENT)) {
      accountPermissionsSet.add(PermissionType.CE_ADMIN);
    }
    return AccountPermissions.builder().permissions(accountPermissionsSet).build();
  }

  private void checkForUserGroupWithSameName(UserGroup userGroup) {
    UserGroup existingUserGroup = fetchUserGroupByName(userGroup.getAccountId(), userGroup.getName());
    if (existingUserGroup != null && !existingUserGroup.getUuid().equals(userGroup.getUuid())) {
      throw new UserGroupAlreadyExistException("User Group with same name already exists.");
    }
  }

  private void checkForUserGroupWithEmptyName(UserGroup userGroup) {
    if (isBlank(userGroup.getName())) {
      throw new GeneralException("User group can't be created without group name.", USER);
    }
  }

  private void checkUserGroupsCountWithinLimit(String accountId) {
    long maxNumberOfUserGroupsAllowed = rbacFeature.getMaxUsageAllowedForAccount(accountId);
    long numberOfUserGroupsOfAccount = getCountOfUserGroups(accountId);
    if (numberOfUserGroupsOfAccount >= maxNumberOfUserGroupsAllowed) {
      throw new WingsException(ErrorCode.USAGE_LIMITS_EXCEEDED,
          String.format("Cannot create more than %d user groups", maxNumberOfUserGroupsAllowed), WingsException.USER);
    }
  }
  @Override
  public long getCountOfUserGroups(String accountId) {
    notNullCheck(UserGroupKeys.accountId, accountId, USER);
    return wingsPersistence.createQuery(UserGroup.class).filter(UserGroupKeys.accountId, accountId).count();
  }

  @Override
  public PageResponse<UserGroup> list(String accountId, PageRequest<UserGroup> req, boolean loadUsers,
      UserGroupSearchTermType searchTermType, String searchTerm) {
    notNullCheck(UserGroupKeys.accountId, accountId, USER);
    Account account = accountService.get(accountId);
    notNullCheck("account", account, USER);
    req.addFilter(UserGroupKeys.accountId, EQ, accountId);
    if (isSearchByApplicationFeatureEnabled(accountId) && APPLICATION_NAME.equals(searchTermType)
        && isNotEmpty(searchTerm)) {
      Set<String> applicationIdsMatchingSearchTerm = getApplicationsMatchingTheSearchTerm(accountId, searchTerm);
      if (isEmpty(applicationIdsMatchingSearchTerm)) {
        return new PageResponse<>();
      }
      populateAppIdFilter(req, applicationIdsMatchingSearchTerm);
    }
    PageResponse<UserGroup> res = wingsPersistence.query(UserGroup.class, req);

    log.info("Page response for user groups list: {}", res);

    // Using a custom comparator since our mongo apis don't support alphabetical sorting with case insensitivity.
    // Currently, it only supports ASC and DSC.
    res.getResponse().sort((ug1, ug2) -> StringUtils.compareIgnoreCase(ug1.getName(), ug2.getName()));
    if (loadUsers) {
      loadUsersForUserGroups(res.getResponse(), account);
    }

    if (!ccmSettingService.isCloudCostEnabled(accountId)) {
      res.getResponse().forEach(this::maskCePermissions);
    }

    return res;
  }

  private boolean isSearchByApplicationFeatureEnabled(String accountId) {
    return featureFlagService.isEnabled(FeatureName.SEARCH_USERGROUP_BY_APPLICATION, accountId);
  }

  private Set<String> getApplicationsMatchingTheSearchTerm(String accountId, String searchTerm) {
    PageResponse<Application> applicationList = getAllApplicationsStartingWithSearchTerm(accountId, searchTerm);
    if (isEmpty(applicationList)) {
      return Collections.emptySet();
    }
    return applicationList.stream().map(Base::getUuid).collect(Collectors.toSet());
  }

  private PageResponse<Application> getAllApplicationsStartingWithSearchTerm(String accountId, String searchTerm) {
    SearchFilter applicationSearchFilter = SearchFilter.builder()
                                               .fieldName(ApplicationKeys.name)
                                               .op(Operator.STARTS_WITH)
                                               .fieldValues(new Object[] {searchTerm})
                                               .build();
    PageRequest<Application> applicationPageRequest = aPageRequest()
                                                          .withLimit("100")
                                                          .addFilter(ApplicationKeys.accountId, EQ, accountId)
                                                          .addFilter(applicationSearchFilter)
                                                          .build();
    return appService.list(applicationPageRequest);
  }

  private void populateAppIdFilter(PageRequest<UserGroup> req, Set<String> applicationIds) {
    SearchFilter searchFilterForAllApplication = SearchFilter.builder()
                                                     .fieldName(UserGroupKeys.appFilterType)
                                                     .op(EQ)
                                                     .fieldValues(new Object[] {AppFilter.FilterType.ALL})
                                                     .build();
    SearchFilter searchFilterForSpecifiedIdsApplication = SearchFilter.builder()
                                                              .fieldName(UserGroupKeys.appFilterType)
                                                              .op(EQ)
                                                              .fieldValues(new Object[] {AppFilter.FilterType.SELECTED})
                                                              .fieldName(UserGroupKeys.appIds)
                                                              .op(Operator.IN)
                                                              .fieldValues(applicationIds.toArray())
                                                              .build();
    req.addFilter("", Operator.OR, searchFilterForAllApplication, searchFilterForSpecifiedIdsApplication);
  }

  private void loadUsersForUserGroups(List<UserGroup> userGroups, Account account) {
    PageRequest<User> req = aPageRequest().addFilter(UserKeys.accounts, Operator.HAS, account).build();
    PageResponse<User> res = userService.list(req, false);
    List<User> allUsersList = res.getResponse();
    if (isEmpty(allUsersList)) {
      return;
    }

    Map<String, User> userMap = allUsersList.stream().collect(Collectors.toMap(User::getUuid, identity()));
    userGroups.forEach(userGroup -> {
      List<String> memberIds = userGroup.getMemberIds();
      log.info("User group: {}, has member IDs: {}", userGroup.getName(), memberIds);
      if (isEmpty(memberIds)) {
        userGroup.setMembers(new ArrayList<>());
        return;
      }
      List<User> members = new ArrayList<>();
      memberIds.forEach(memberId -> {
        User user = userMap.get(memberId);
        if (user != null) {
          members.add(user);
        }
      });
      log.info("For user group: {} Members added- size: {}, members: {}", userGroup.getName(), members.size(), members);
      userGroup.setMembers(members);
    });
  }

  @Override
  public UserGroup getUserGroupSummary(UserGroup userGroup) {
    if (userGroup == null) {
      return null;
    }
    return UserGroup.builder().uuid(userGroup.getUuid()).name(userGroup.getName()).build();
  }

  @Override
  public List<UserGroup> getUserGroupSummary(List<UserGroup> userGroupList) {
    if (isEmpty(userGroupList)) {
      return emptyList();
    }
    return userGroupList.stream().map(this::getUserGroupSummary).collect(toList());
  }

  @Override
  public List<UserGroup> filter(String accountId, List<String> userGroupIds) {
    return getQuery(accountId, userGroupIds).asList();
  }

  @Override
  public List<UserGroup> filter(String accountId, List<String> userGroupIds, List<String> fieldsNeededInResponse) {
    if (isEmpty(fieldsNeededInResponse)) {
      return filter(accountId, userGroupIds);
    }
    Query<UserGroup> userGroupQuery = getQuery(accountId, userGroupIds);
    fieldsNeededInResponse.forEach(field -> userGroupQuery.project(field, true));
    return userGroupQuery.asList();
  }

  private Query<UserGroup> getQuery(String accountId, List<String> userGroupIds) {
    Query<UserGroup> userGroupQuery =
        wingsPersistence.createQuery(UserGroup.class).filter(UserGroupKeys.accountId, accountId);
    if (isNotEmpty(userGroupIds)) {
      userGroupQuery.field("_id").in(userGroupIds);
    }
    return userGroupQuery;
  }

  @Override
  public void deleteByAccountId(String accountId) {
    List<UserGroup> userGroups =
        wingsPersistence.createQuery(UserGroup.class).filter(UserGroupKeys.accountId, accountId).asList();
    for (UserGroup userGroup : userGroups) {
      delete(accountId, userGroup.getUuid(), true);
    }
  }

  @Override
  public UserGroup get(String accountId, String userGroupId) {
    return get(accountId, userGroupId, true);
  }

  @Override
  public UserGroup get(String varId) {
    return wingsPersistence.get(UserGroup.class, varId);
  }

  @Override
  @Nullable
  public UserGroup getByName(String accountId, String name) {
    UserGroup userGroup = wingsPersistence.createQuery(UserGroup.class)
                              .filter(UserGroupKeys.accountId, accountId)
                              .filter(UserGroupKeys.name, name)
                              .get();
    if (userGroup != null && !ccmSettingService.isCloudCostEnabled(userGroup.getAccountId())) {
      maskCePermissions(userGroup);
    }
    return userGroup;
  }

  @Nullable
  @Override
  public List<UserGroup> listByName(String accountId, List<String> names) {
    names = names.stream().filter(StringUtils::isNotEmpty).collect(toList());

    Query<UserGroup> query = wingsPersistence.createQuery(UserGroup.class)
                                 .filter(UserGroupKeys.accountId, accountId)
                                 .field(UserGroupKeys.name)
                                 .in(names);

    List<UserGroup> userGroups = new LinkedList<>();
    try (HIterator<UserGroup> iterator = new HIterator<>(query.fetch())) {
      while (iterator.hasNext()) {
        userGroups.add(iterator.next());
      }
    }

    if (!ccmSettingService.isCloudCostEnabled(accountId)) {
      userGroups.forEach(this::maskCePermissions);
    }
    return userGroups;
  }

  @Override
  public UserGroup get(String accountId, String userGroupId, boolean loadUsers) {
    UserGroup userGroup = wingsPersistence.createQuery(UserGroup.class).filter(UserGroup.ID_KEY2, userGroupId).get();
    if (userGroup == null) {
      return null;
    }

    if (loadUsers) {
      Account account = accountService.get(accountId);
      loadUsers(userGroup, account);
    }

    if (!ccmSettingService.isCloudCostEnabled(userGroup.getAccountId())) {
      maskCePermissions(userGroup);
    }
    return userGroup;
  }

  private void loadUsers(UserGroup userGroup, Account account) {
    if (userGroup.getMemberIds() != null) {
      PageRequest<User> req = aPageRequest()
                                  .addFilter(ID_KEY, Operator.IN, userGroup.getMemberIds().toArray())
                                  .addFilter(UserKeys.accounts, Operator.IN, account)
                                  .build();

      PageResponse<User> res = userService.list(req, false);
      List<User> userList = res.getResponse();
      userList.sort((u1, u2) -> StringUtils.compareIgnoreCase(u1.getName(), u2.getName()));
      userGroup.setMembers(userList);
    } else {
      userGroup.setMembers(new ArrayList<>());
    }
  }

  @Override
  public void maskCePermissions(UserGroup userGroup) {
    AccountPermissions accountPermissions = userGroup.getAccountPermissions();
    if (accountPermissions != null) {
      Set<PermissionType> accountPermissionSet =
          Optional.ofNullable(accountPermissions.getPermissions()).orElse(emptySet());
      accountPermissionSet.removeAll(newHashSet(CE_ADMIN, CE_VIEWER));
      userGroup.setAccountPermissions(AccountPermissions.builder().permissions(accountPermissionSet).build());
    }
  }

  @Override
  public UserGroup updateOverview(UserGroup userGroup) {
    notNullCheck("name", userGroup.getName());
    UserGroup userGroupFromDB = get(userGroup.getAccountId(), userGroup.getUuid());
    checkForUserGroupWithSameName(userGroup);
    checkForUserGroupWithEmptyName(userGroup);
    validateUserGroupName(userGroup.getName());
    if (UserGroupUtils.isAdminUserGroup(userGroupFromDB)) {
      throw new WingsException(
          ErrorCode.UPDATE_NOT_ALLOWED, "Can not update name/description of Account Administrator user group");
    }
    UpdateOperations<UserGroup> operations =
        wingsPersistence.createUpdateOperations(UserGroup.class).set(UserGroupKeys.name, userGroup.getName());
    setUnset(operations, UserGroupKeys.description, userGroup.getDescription());
    return update(userGroup, operations);
  }

  @Override
  public UserGroup updateNotificationSettings(
      String accountId, String groupId, NotificationSettings newNotificationSettings) {
    if (null == newNotificationSettings) {
      return get(accountId, groupId);
    }

    if (EmptyPredicate.isNotEmpty(newNotificationSettings.getPagerDutyIntegrationKey())) {
      pagerDutyService.validateKey(newNotificationSettings.getPagerDutyIntegrationKey());
      pagerDutyService.validateCreateTestEvent(newNotificationSettings.getPagerDutyIntegrationKey());
    }

    UpdateOperations<UserGroup> update = wingsPersistence.createUpdateOperations(UserGroup.class)
                                             .set(UserGroupKeys.notificationSettings, newNotificationSettings);

    Query<UserGroup> query = wingsPersistence.createQuery(UserGroup.class)
                                 .field(UserGroupKeys.accountId)
                                 .equal(accountId)
                                 .field("_id")
                                 .equal(groupId);

    UserGroup updatedGroup = wingsPersistence.findAndModify(query, update, HPersistence.returnNewOptions);
    if (null == updatedGroup) {
      log.error("No user group found. groupId={}, accountId={}", groupId, accountId);
      throw new WingsException(ErrorCode.INVALID_ARGUMENT, "No user group found");
    }
    auditServiceHelper.reportForAuditingUsingAccountId(accountId, null, updatedGroup, Type.UPDATE_NOTIFICATION_SETTING);
    log.info(
        "Auditing update in notification setting for userGroup={} in account={}", updatedGroup.getName(), accountId);
    return updatedGroup;
  }

  @Override
  public UserGroup updateMembers(UserGroup userGroupToUpdate, boolean sendNotification, boolean toBeAudited) {
    Set<String> newMemberIds = isEmpty(userGroupToUpdate.getMemberIds())
        ? Sets.newHashSet()
        : Sets.newHashSet(userGroupToUpdate.getMemberIds());
    newMemberIds.removeIf(EmptyPredicate::isEmpty);

    log.info("New memberIds for user group {}: {}", userGroupToUpdate.getName(), newMemberIds);

    UserGroup existingUserGroup = get(userGroupToUpdate.getAccountId(), userGroupToUpdate.getUuid());
    log.info("Existing memberIds in user group {}: {}", existingUserGroup.getName(), existingUserGroup.getMemberIds());

    if (UserGroupUtils.isAdminUserGroup(existingUserGroup) && newMemberIds.isEmpty()) {
      throw new WingsException(
          ErrorCode.UPDATE_NOT_ALLOWED, "Account Administrator user group must have at least one user");
    }

    Set<String> existingMemberIds = isEmpty(existingUserGroup.getMemberIds())
        ? Sets.newHashSet()
        : Sets.newHashSet(existingUserGroup.getMemberIds());

    UpdateOperations<UserGroup> operations = wingsPersistence.createUpdateOperations(UserGroup.class);
    setUnset(operations, UserGroupKeys.memberIds, newMemberIds);
    UserGroup updatedUserGroup = update(userGroupToUpdate, operations);

    log.info("Updated user group: {}", updatedUserGroup.getName());

    // auditing addition/removal of users in/from user group
    if (toBeAudited) {
      Set<String> memberIdsToBeAdded = Sets.difference(newMemberIds, existingMemberIds);
      Set<String> memberIdsToBeRemoved = Sets.difference(existingMemberIds, newMemberIds);

      auditServiceHelper.reportForAuditingUsingAccountId(
          userGroupToUpdate.getAccountId(), userGroupToUpdate, updatedUserGroup, Type.UPDATE);
      memberIdsToBeAdded.forEach(userId -> {
        User user = userService.get(userId);
        auditServiceHelper.reportForAuditingUsingAccountId(userGroupToUpdate.getAccountId(), null, user, Type.ADD);
      });
      memberIdsToBeRemoved.forEach(userId -> {
        User user = userService.get(userId);
        auditServiceHelper.reportForAuditingUsingAccountId(userGroupToUpdate.getAccountId(), null, user, Type.REMOVE);
      });
    }

    if (isNotEmpty(existingUserGroup.getMemberIds())) {
      newMemberIds.addAll(existingUserGroup.getMemberIds());
    }

    if (isNotEmpty(newMemberIds)) {
      evictUserPermissionInfoCacheForUsers(
          userGroupToUpdate.getAccountId(), newMemberIds.stream().distinct().collect(toList()));
    }

    if (sendNotification) {
      Set<String> newlyAddedMemberIds = Sets.difference(newMemberIds, existingMemberIds);
      Account account = accountService.get(updatedUserGroup.getAccountId());
      for (String member : newlyAddedMemberIds) {
        User user = userService.get(member);
        if (user != null) {
          userService.sendAddedGroupEmail(user, account, singletonList(userGroupToUpdate));
        }
      }
    }

    return updatedUserGroup;
  }

  @Override
  public UserGroup removeMembers(
      UserGroup userGroup, Collection<User> members, boolean sendNotification, boolean toBeAudited) {
    if (isEmpty(members)) {
      return userGroup;
    }
    List<User> groupMembers = userGroup.getMembers();
    log.info("Group members in the user group- {} are: {}", userGroup.getName(), groupMembers);

    userGroup.getMemberIds().removeAll(members.stream().map(User::getUuid).collect(toList()));
    return updateMembers(userGroup, sendNotification, toBeAudited);
  }

  @Override
  public UserGroup setUserGroupPermissions(
      String accountId, String userGroupId, AccountPermissions accountPermissions, Set<AppPermission> appPermissions) {
    UserGroup userGroup = get(accountId, userGroupId);
    checkImplicitPermissions(accountPermissions, accountId, userGroup.getName());
    checkDeploymentPermissions(userGroup);
    validateAppFilterForAppPermissions(appPermissions);
    UpdateOperations<UserGroup> operations = wingsPersistence.createUpdateOperations(UserGroup.class);
    setUnset(operations, UserGroupKeys.appPermissions, appPermissions);
    AccountPermissions accountPermissionsUpdate =
        Optional.ofNullable(accountPermissions).orElse(AccountPermissions.builder().build());
    setUnset(operations, UserGroupKeys.accountPermissions, accountPermissionsUpdate);
    UserGroup updatedUserGroup = update(userGroup, operations);
    evictUserPermissionInfoCacheForUserGroup(updatedUserGroup);
    if (!ccmSettingService.isCloudCostEnabled(updatedUserGroup.getAccountId())) {
      maskCePermissions(updatedUserGroup);
    }
    return updatedUserGroup;
  }

  private void checkDeploymentPermissions(UserGroup userGroup) {
    if (isEmpty(userGroup.getAppPermissions())) {
      return;
    }
    Set<AppPermission> newAppPermissions = new HashSet<>();
    for (AppPermission appPermission : userGroup.getAppPermissions()) {
      if (isNotEmpty(appPermission.getActions())
          && (appPermission.getPermissionType() == ALL_APP_ENTITIES
              || appPermission.getPermissionType() == DEPLOYMENT)) {
        Set<PermissionAttribute.Action> actionSet = new HashSet<>();
        appPermission.getActions().forEach(action -> {
          if (action != null && action.equals(EXECUTE)) {
            actionSet.add(EXECUTE_PIPELINE);
            actionSet.add(EXECUTE_WORKFLOW);
            actionSet.add(EXECUTE_WORKFLOW_ROLLBACK);
            actionSet.add(ABORT_WORKFLOW);
          }
          actionSet.add(action);
        });
        appPermission.setActions(actionSet);
      }
      newAppPermissions.add(appPermission);
    }
    if (!newAppPermissions.equals(userGroup.getAppPermissions())) {
      userGroup.setAppPermissions(newAppPermissions);
    }
  }

  public void maintainTemplatePermissions(UserGroup userGroup) {
    boolean hasAccountLevelTemplateManagementPermission = false;
    final AccountPermissions accountPermissions = userGroup.getAccountPermissions();
    if (accountPermissions != null && accountPermissions.getPermissions() != null) {
      final Set<PermissionType> accountPermissionSet = accountPermissions.getPermissions();
      if (!accountPermissionSet.isEmpty()) {
        hasAccountLevelTemplateManagementPermission = accountPermissionSet.contains(PermissionType.TEMPLATE_MANAGEMENT);
      }
    }

    Set<AppPermission> appPermissions = userGroup.getAppPermissions();
    if (isNotEmpty(appPermissions)
        && appPermissions.stream().anyMatch(appPermission
            -> appPermission.getPermissionType() != null && appPermission.getPermissionType().equals(APP_TEMPLATE))) {
      return;
    }

    boolean hasAllAppAccess = false;
    Set<String> allowedAppIds = new HashSet<>();

    if (isNotEmpty(appPermissions)) {
      hasAllAppAccess = appPermissions.stream().anyMatch(appPermission
          -> appPermission.getAppFilter() != null && appPermission.getAppFilter().getFilterType() != null
              && appPermission.getAppFilter().getFilterType().equals(AppFilter.FilterType.ALL));
      if (!hasAllAppAccess) {
        allowedAppIds =
            appPermissions.stream()
                .filter(appPermission
                    -> appPermission.getAppFilter() != null && appPermission.getAppFilter().getFilterType() != null
                        && appPermission.getAppFilter().getFilterType().equals(AppFilter.FilterType.SELECTED)
                        && isNotEmpty(appPermission.getAppFilter().getIds()))
                .map(appPermission -> appPermission.getAppFilter().getIds())
                .flatMap(Collection::stream)
                .collect(toSet());
      }
    }

    if (hasAllAppAccess || (isNotEmpty(allowedAppIds))) {
      AppPermission applicationTemplatePermission =
          AppPermission.builder()
              .permissionType(PermissionType.APP_TEMPLATE)
              .appFilter(hasAllAppAccess
                      ? AppFilter.builder().filterType(AppFilter.FilterType.ALL).build()
                      : AppFilter.builder().filterType(AppFilter.FilterType.SELECTED).ids(allowedAppIds).build())
              .entityFilter(GenericEntityFilter.builder().filterType(GenericEntityFilter.FilterType.ALL).build())
              .actions(hasAccountLevelTemplateManagementPermission
                      ? new HashSet<>(Arrays.asList(PermissionAttribute.Action.CREATE, PermissionAttribute.Action.READ,
                          PermissionAttribute.Action.UPDATE, PermissionAttribute.Action.DELETE))
                      : new HashSet<>(Collections.singletonList(PermissionAttribute.Action.READ)))
              .build();
      if (userGroup.getAppPermissions() == null) {
        userGroup.setAppPermissions(new HashSet<>());
      }
      userGroup.getAppPermissions().add(applicationTemplatePermission);
    }
  }

  public void addParentsReference(
      String userGroupId, String accountId, String appId, String entityId, String entityType) {
    UserGroup userGroup = Optional.ofNullable(mongoPersistence.get(UserGroup.class, userGroupId)).orElse(null);
    if (userGroup == null) {
      // log statement for userGroups which are deleted but are being referenced in a pipeline of deployment freeze
      // window
      log.error(
          "UserGroup does not exist but {} with id {} and appId {} is referencing it", entityType, entityId, appId);
      return;
    }
    userGroup.addParent(UserGroupEntityReference.builder()
                            .entityType(entityType)
                            .id(entityId)
                            .appId(appId)
                            .accountId(accountId)
                            .build());
    UpdateOperations<UserGroup> ops = mongoPersistence.createUpdateOperations(UserGroup.class);
    setUnset(ops, "parents", userGroup.getParents());
    mongoPersistence.update(userGroup, ops);
  }

  public void removeParentsReference(
      String userGroupId, String accountId, String appId, String entityId, String entityType) {
    UserGroup userGroup = Optional.ofNullable(mongoPersistence.get(UserGroup.class, userGroupId)).orElse(null);
    if (userGroup == null) {
      // log statement for userGroups which are deleted but are being referenced in a pipeline or in a deployment freeze
      // window
      log.error(
          "UserGroup does not exist but {} with id {} and appId {} is referencing it", entityType, entityId, appId);
      return;
    }
    userGroup.removeParent(UserGroupEntityReference.builder()
                               .entityType(entityType)
                               .id(entityId)
                               .appId(appId)
                               .accountId(accountId)
                               .build());
    UpdateOperations<UserGroup> ops = mongoPersistence.createUpdateOperations(UserGroup.class);
    setUnset(ops, "parents", userGroup.getParents());
    mongoPersistence.update(userGroup, ops);
  }

  private void validateAppFilterForAppPermissions(Set<AppPermission> appPermissions) {
    if (appPermissions == null) {
      return;
    }
    appPermissions.forEach(appPermission -> {
      AppFilter appFilter = appPermission.getAppFilter();
      Filter entityFilter = appPermission.getEntityFilter();

      if (appFilter == null || appFilter.getFilterType() == null) {
        throw new InvalidRequestException("Invalid Request: Missing App Filter");
      }

      switch (appFilter.getFilterType()) {
        case AppFilter.FilterType.ALL:
          if (appFilter.getIds() != null) {
            throw new InvalidRequestException("Invalid Request: Dynamic Filter ALL cannot contain ids");
          }
          break;
        case AppFilter.FilterType.SELECTED:
          if (isAppPermissionWithEmptyIds(appPermission)) {
            throw new InvalidRequestException("Invalid Request: Please provide atleast one application");
          }
          break;
        case AppFilter.FilterType.EXCLUDE_SELECTED:
          if (isAppPermissionWithEmptyIds(appPermission)) {
            throw new InvalidRequestException("Invalid Request: Please provide atleast one application");
          } else if (isEntityFilterWithCustomIds(entityFilter)) {
            throw new InvalidRequestException("Invalid Request: Cannot add custom entities to a Dynamic Filter");
          }
          break;
        default:
          throw new InvalidRequestException("Invalid Request: Please provide a valid application filter");
      }
    });
  }

  @Override
  public UserGroup updatePermissions(UserGroup userGroup) {
    checkImplicitPermissions(userGroup.getAccountPermissions(), userGroup.getAccountId(), userGroup.getName());
    checkDeploymentPermissions(userGroup);
    validateAppFilterForAppPermissions(userGroup.getAppPermissions());
    AccountPermissions accountPermissions =
        Optional.ofNullable(userGroup.getAccountPermissions()).orElse(AccountPermissions.builder().build());
    userGroup.setAccountPermissions(accountPermissions);
    UpdateOperations<UserGroup> operations = wingsPersistence.createUpdateOperations(UserGroup.class);
    setUnset(operations, UserGroupKeys.appPermissions, userGroup.getAppPermissions());
    setUnset(operations, UserGroupKeys.accountPermissions, userGroup.getAccountPermissions());
    UserGroup updatedUserGroup = update(userGroup, operations);
    evictUserPermissionInfoCacheForUserGroup(updatedUserGroup);
    userGroup = wingsPersistence.get(UserGroup.class, userGroup.getUuid());
    if (userGroup.getAccountId() != null) {
      auditServiceHelper.reportForAuditingUsingAccountId(
          userGroup.getAccountId(), userGroup, updatedUserGroup, Type.MODIFY_PERMISSIONS);
      log.info("Auditing modification of permissions for userGroup={} in account={}", updatedUserGroup.getName(),
          userGroup.getAccountId());
    }
    return updatedUserGroup;
  }

  private void checkImplicitPermissions(AccountPermissions accountPermissions, String accountId, String name) {
    if (null == accountPermissions) {
      return;
    }

    Set<PermissionType> permissions = accountPermissions.getPermissions();
    if (isNotEmpty(permissions) && permissions.contains(USER_PERMISSION_MANAGEMENT)
        && !permissions.contains(USER_PERMISSION_READ)) {
      log.info("Received account permissions {} are not in proper format for account {}, userGroupName {}", permissions,
          accountId, name);
      throw new InvalidRequestException("Invalid account permission.", ErrorCode.INVALID_ACCOUNT_PERMISSION, USER);
    }
  }

  @Override
  public boolean existsLinkedUserGroup(String accountId, String ssoId) {
    return 0
        != wingsPersistence.createQuery(UserGroup.class, excludeAuthority)
               .filter(UserGroupKeys.accountId, accountId)
               .filter(UserGroupKeys.isSsoLinked, TRUE)
               .filter(UserGroupKeys.linkedSsoId, ssoId)
               .count(new CountOptions().limit(1));
  }

  private UserGroup update(UserGroup userGroup, UpdateOperations<UserGroup> operations) {
    notNullCheck("uuid", userGroup.getUuid());
    notNullCheck(UserGroupKeys.accountId, userGroup.getAccountId());
    Query<UserGroup> query = wingsPersistence.createQuery(UserGroup.class)
                                 .filter(ID_KEY, userGroup.getUuid())
                                 .filter(UserGroupKeys.accountId, userGroup.getAccountId());
    wingsPersistence.update(query, operations);
    return get(userGroup.getAccountId(), userGroup.getUuid());
  }

  @Override
  public boolean delete(String accountId, String userGroupId, boolean forceDelete) {
    UserGroup userGroup = get(accountId, userGroupId, false);
    notNullCheck("userGroup", userGroup);
    if (!(userGroup.getParents().isEmpty())) {
      throw new InvalidRequestException(
          "This userGroup is being referenced in either approval step/stage or notification strategy or as a runtimeInput in an execution step/stage or in a deployment freeze window. Please make sure to remove the references to delete this userGroup.");
    }
    if (!forceDelete && UserGroupUtils.isAdminUserGroup(userGroup)) {
      return false;
    }
    Query<UserGroup> userGroupQuery = wingsPersistence.createQuery(UserGroup.class)
                                          .filter(UserGroupKeys.accountId, accountId)
                                          .filter(ID_KEY, userGroupId);
    boolean deleted = wingsPersistence.delete(userGroupQuery);
    if (deleted) {
      evictUserPermissionInfoCacheForUserGroup(userGroup);
      executors.submit(() -> userGroupDeleteEventHandler.handleUserGroupDelete(accountId, userGroupId));
      auditServiceHelper.reportDeleteForAuditingUsingAccountId(accountId, userGroup);
      log.info("Auditing deletion of userGroupId={} and accountId={}", userGroup.getUuid(), accountId);
    }
    return deleted;
  }

  @Override
  @Nullable
  public UserGroup getDefaultUserGroup(String accountId) {
    return wingsPersistence.createQuery(UserGroup.class)
        .filter(UserGroupKeys.accountId, accountId)
        .field(UserGroupKeys.name)
        .equal(DEFAULT_ACCOUNT_ADMIN_USER_GROUP_NAME)
        .field(UserGroupKeys.isDefault)
        .equal(true)
        .get();
  }

  @Override
  public UserGroup cloneUserGroup(
      final String accountId, final String uuid, final String newName, final String newDescription) {
    validateUserGroupName(newName);
    UserGroup existingGroup = get(accountId, uuid, true);
    notNullCheck("userGroup", existingGroup);
    unEqualCheck(existingGroup.getName(), newName);
    UserGroup newClonedGroup = existingGroup.cloneWithNewName(newName, newDescription);
    return save(newClonedGroup);
  }

  private void evictUserPermissionInfoCacheForUserGroup(UserGroup userGroup) {
    authService.evictPermissionAndRestrictionCacheForUserGroup(userGroup);
  }

  private void evictUserPermissionInfoCacheForUsers(String accountId, List<String> memberIds) {
    authService.evictUserPermissionAndRestrictionCacheForAccount(accountId, memberIds);
  }

  @Override
  public List<UserGroup> listByAccountId(String accountId, User user, boolean loadUsers) {
    PageRequestBuilder pageRequest = aPageRequest()
                                         .withLimit(Long.toString(getCountOfUserGroups(accountId)))
                                         .addFilter(UserGroupKeys.accountId, EQ, accountId)
                                         .addFilter(UserGroupKeys.memberIds, Operator.HAS, user.getUuid());
    return list(accountId, pageRequest.build(), loadUsers, null, null).getResponse();
  }

  @Override
  public List<UserGroup> listByAccountId(String accountId) {
    PageRequestBuilder pageRequest = aPageRequest()
                                         .withLimit(Long.toString(getCountOfUserGroups(accountId)))
                                         .addFilter(UserGroupKeys.accountId, EQ, accountId);
    return list(accountId, pageRequest.build(), true, null, null).getResponse();
  }

  @Override
  public List<String> fetchUserGroupsMemberIds(String accountId, List<String> userGroupIds) {
    if (isEmpty(userGroupIds)) {
      return new ArrayList<>();
    }

    List<UserGroup> userGroupList;
    if (isNotBlank(accountId)) {
      userGroupList = wingsPersistence.createQuery(UserGroup.class)
                          .filter(UserGroupKeys.accountId, accountId)
                          .field(UserGroup.ID_KEY2)
                          .in(userGroupIds)
                          .project(UserGroupKeys.memberIds, true)
                          .asList();
    } else {
      userGroupList = wingsPersistence.createQuery(UserGroup.class, excludeAuthority)
                          .field(UserGroup.ID_KEY2)
                          .in(userGroupIds)
                          .project(UserGroupKeys.memberIds, true)
                          .asList();
    }

    return userGroupList.stream()
        .filter(userGroup -> !isEmpty(userGroup.getMemberIds()))
        .flatMap(userGroup -> userGroup.getMemberIds().stream())
        .collect(toList());
  }

  @Override
  public List<UserGroup> fetchUserGroupNamesFromIds(Collection<String> userGroupIds) {
    if (isEmpty(userGroupIds)) {
      return asList();
    }

    return wingsPersistence.createQuery(UserGroup.class, excludeAuthority)
        .field(UserGroup.ID_KEY2)
        .in(userGroupIds)
        .project(UserGroupKeys.name, true)
        .project(UserGroup.ID_KEY2, true)
        .asList()
        .stream()
        .filter(userGroup -> !isEmpty(userGroup.getName()))
        .collect(toList());
  }

  @Override
  public List<UserGroup> fetchUserGroupNamesFromIdsUsingSecondary(Collection<String> userGroupIds) {
    if (isEmpty(userGroupIds)) {
      return emptyList();
    }

    return wingsPersistence.createAnalyticsQuery(UserGroup.class, excludeAuthority)
        .field(UserGroup.ID_KEY2)
        .in(userGroupIds)
        .project(UserGroupKeys.name, true)
        .project(UserGroup.ID_KEY2, true)
        .asList(new FindOptions().readPreference(ReadPreference.secondaryPreferred()))
        .stream()
        .filter(userGroup -> !isEmpty(userGroup.getName()))
        .collect(toList());
  }

  @Override
  public boolean verifyUserAuthorizedToAcceptOrRejectApproval(String accountId, List<String> userGroupIds) {
    if (isEmpty(userGroupIds)) {
      return false;
    }

    User user = UserThreadLocal.get();
    List<String> userGroupMembers = fetchUserGroupsMemberIds(accountId, userGroupIds);

    return userService.isUserVerified(user) && isNotEmpty(userGroupMembers)
        && userGroupMembers.contains(user.getUuid());
  }

  @Override
  public boolean verifyApiKeyAuthorizedToAcceptOrRejectApproval(
      List<String> apiKeysUserGroupIds, List<String> userGroupIds) {
    if (isEmpty(userGroupIds)) {
      return false;
    }

    if (CollectionUtils.containsAny(apiKeysUserGroupIds, userGroupIds)) {
      return true;
    } else {
      return false;
    }
  }

  @Override
  public UserGroup linkToSsoGroup(@NotBlank String accountId, @NotBlank String userGroupId, @NotNull SSOType ssoType,
      @NotBlank String ssoId, @NotBlank String ssoGroupId, @NotBlank String ssoGroupName) {
    UserGroup group = get(accountId, userGroupId, false);

    if (null == group) {
      throw new InvalidRequestException("Invalid UserGroup ID.");
    }
    if (group.isSsoLinked()) {
      throw new InvalidRequestException("SSO Provider already linked to the group. Try unlinking first.");
    }

    SSOSettings ssoSettings = ssoSettingService.getSsoSettings(ssoId);

    if (null == ssoSettings) {
      throw new InvalidRequestException("Invalid ssoId");
    }

    Map<String, Object> updatedFields = new HashMap<>();
    updatedFields.put(UserGroupKeys.isSsoLinked, TRUE);
    updatedFields.put(UserGroupKeys.linkedSsoType, ssoType);
    updatedFields.put(UserGroupKeys.linkedSsoId, ssoId);
    updatedFields.put(UserGroupKeys.linkedSsoDisplayName, ssoSettings.getDisplayName());
    updatedFields.put(UserGroupKeys.ssoGroupId, ssoGroupId);
    updatedFields.put(UserGroupKeys.ssoGroupName, ssoGroupName);

    wingsPersistence.updateFields(UserGroup.class, group.getUuid(), updatedFields);
    UserGroup updatedGroup = get(accountId, userGroupId, true);

    auditServiceHelper.reportForAuditingUsingAccountId(accountId, group, updatedGroup, Type.LINK_SSO);

    if (ssoType == SSOType.LDAP) {
      ldapGroupSyncJobHelper.syncJob(ssoSettings);
    }

    return updatedGroup;
  }

  @Override
  public UserGroup unlinkSsoGroup(@NotBlank String accountId, @NotBlank String userGroupId, boolean retainMembers) {
    UserGroup group = get(accountId, userGroupId, false);

    if (null == group) {
      throw new InvalidRequestException("Invalid UserGroup ID.");
    }
    if (!group.isSsoLinked()) {
      throw new InvalidRequestException("Group is not linked to any SSO group.");
    }

    if (!retainMembers) {
      removeUserGroupFromInvites(accountId, userGroupId);
      group.setMemberIds(emptyList());
      group = updateMembers(group, false, false);
    }

    group.setSsoLinked(false);
    group.setLinkedSsoType(null);
    group.setLinkedSsoId(null);
    group.setSsoGroupId(null);

    auditServiceHelper.reportForAuditingUsingAccountId(accountId, null, group, Type.UNLINK_SSO);
    log.info("Auditing unlink from SSO Group for groupId={}", group.getUuid());

    return save(group);
  }

  private void removeUserGroupFromInvites(String accountId, String userGroupId) {
    Query<UserInvite> query = userService.getInvitesQueryFromAccountId(accountId);
    try (HIterator<UserInvite> userInvites = new HIterator<>(query.limit(NO_LIMIT).fetch())) {
      for (UserInvite invite : userInvites) {
        invite.getUserGroups().removeIf(x -> x.getUuid().equals(userGroupId));
        log.info("Removing user group id {} from userInvites for unlink SSO group flow in account {}", userGroupId,
            accountId);
        wingsPersistence.save(invite);
      }
    }
  }

  @Override
  public void removeAppIdsFromAppPermissions(UserGroup userGroup, Set<String> appIds) {
    boolean isModified = false;
    boolean hasEmptyPermission = false;
    log.info("[USERGROUP-DEBUG]: appIds to be deleted {} from usergroup {} accountId {}", appIds, userGroup.getUuid(),
        userGroup.getAccountId());

    if (isEmpty(appIds)) {
      return;
    }

    Set<AppPermission> groupAppPermissions = userGroup.getAppPermissions();
    log.info("[USERGROUP-DEBUG]: group app permissions {} for usergroup {} accountId {}", groupAppPermissions,
        userGroup.getUuid(), userGroup.getAccountId());
    if (isEmpty(groupAppPermissions)) {
      return;
    }

    for (AppPermission permission : groupAppPermissions) {
      if (!isAppPermissionSelected(permission)) {
        continue;
      }

      AppFilter filter = permission.getAppFilter();
      Set<String> ids = filter.getIds();

      log.info("[USERGROUP-DEBUG]: permission filter ids {} for usergroup {} accountId {}", ids, userGroup.getUuid(),
          userGroup.getAccountId());

      if (ids != null && ids.removeIf(appIds::contains)) {
        isModified = true;
      }

      if (isEmpty(ids)) {
        hasEmptyPermission = true;
      }
    }
    log.info("[USERGROUP-DEBUG]: hasEmptyPermission {} for usergroup {} accountId {}", hasEmptyPermission,
        userGroup.getUuid(), userGroup.getAccountId());

    if (hasEmptyPermission) {
      removeEmptyAppPermissions(userGroup);
      isModified = true;
    }
    log.info("[USERGROUP-DEBUG]: isModified {} for usergroup {} accountId {}", isModified, userGroup.getUuid(),
        userGroup.getAccountId());

    if (isModified) {
      log.info("Pruning app ids from user group: " + userGroup.getUuid());
      UpdateOperations<UserGroup> operations = wingsPersistence.createUpdateOperations(UserGroup.class);
      setUnset(operations, UserGroupKeys.appPermissions, userGroup.getAppPermissions());
      Query<UserGroup> query = wingsPersistence.createQuery(UserGroup.class)
                                   .filter(ID_KEY, userGroup.getUuid())
                                   .filter(UserGroupKeys.accountId, userGroup.getAccountId());
      wingsPersistence.update(query, operations);
      updateUserPermissionAndRestrictionCache(userGroup);
    }
  }

  private void pruneEntityIdFromUserGroup(String appId, String entityId) {
    String accountId = appService.getAccountIdByAppId(appId);
    if (isEmpty(accountId) || isEmpty(entityId)) {
      return;
    }
    Set<String> deletedEntityIds = new HashSet<>();
    deletedEntityIds.add(entityId);

    try (HIterator<UserGroup> userGroupIterator =
             new HIterator<>(wingsPersistence.createQuery(UserGroup.class, excludeAuthority)
                                 .filter(UserGroupKeys.accountId, accountId)
                                 .fetch())) {
      while (userGroupIterator.hasNext()) {
        final UserGroup userGroup = userGroupIterator.next();
        removeEntityIdsFromAppPermissions(userGroup, deletedEntityIds);
      }
    }
  }

  private void removeEntityIdsFromAppPermissions(UserGroup userGroup, Set<String> entityIds) {
    boolean isModified = false;
    Set<AppPermission> appPermissions = userGroup.getAppPermissions();
    Set<AppPermission> newAppPermissions = new HashSet<>();
    if (isEmpty(appPermissions) || isEmpty(entityIds)) {
      return;
    }
    for (AppPermission permission : appPermissions) {
      if (permission == null) {
        continue;
      }
      boolean includeCurrentPermission = true;
      Filter filter = permission.getEntityFilter();
      if (filter != null && filter.getIds() != null && filter.getIds().removeIf(entityIds::contains)) {
        isModified = true;
        if (isEmpty(filter.getIds()) && isEntityFilterSelected(filter)) {
          includeCurrentPermission = false;
        }
      }
      if (filter instanceof ExecutableElementsFilter && ((ExecutableElementsFilter) filter).getFilter().getIds() != null
          && ((ExecutableElementsFilter) filter).getFilter().getIds().removeIf(entityIds::contains)) {
        isModified = true;
        if (isEmpty(((ExecutableElementsFilter) filter).getFilter().getIds())
            && GenericEntityFilter.FilterType.SELECTED.equals(
                ((ExecutableElementsFilter) filter).getFilter().getFilterType())) {
          includeCurrentPermission = false;
        }
      }
      if (includeCurrentPermission) {
        newAppPermissions.add(permission);
      }
    }
    if (isModified) {
      userGroup.setAppPermissions(newAppPermissions);
      log.info("Pruning entity ids from user group: {} for entityIds {}", userGroup.getUuid(), entityIds);
      updatePermissions(userGroup);
    }
  }

  private boolean isEntityFilterSelected(Filter filter) {
    if (filter == null) {
      throw new InvalidArgumentsException("Filter in appPermission for userGroup is null.");
    }
    if (filter instanceof GenericEntityFilter) {
      String filterType = ((GenericEntityFilter) filter).getFilterType();
      return GenericEntityFilter.FilterType.SELECTED.equals(filterType);
    }
    if (filter instanceof EnvFilter) {
      Set<String> filterTypes = ((EnvFilter) filter).getFilterTypes();
      return isNotEmpty(filterTypes) && filterTypes.contains(EnvFilter.FilterType.SELECTED);
    }
    return false;
  }

  @Override
  public List<UserGroup> getUserGroupsBySsoId(String accountId, String ssoId) {
    PageRequest<UserGroup> pageRequest = aPageRequest()
                                             .withLimit(Long.toString(getCountOfUserGroups(accountId)))
                                             .addFilter(UserGroupKeys.accountId, EQ, accountId)
                                             .addFilter(UserGroupKeys.isSsoLinked, EQ, true)
                                             .addFilter(UserGroupKeys.linkedSsoId, EQ, ssoId)
                                             .build();
    PageResponse<UserGroup> pageResponse = list(accountId, pageRequest, true, null, null);
    return pageResponse.getResponse();
  }

  @Override
  public UserGroup fetchUserGroupByName(String accountId, String groupName) {
    return wingsPersistence.createQuery(UserGroup.class)
        .filter(UserGroupKeys.accountId, accountId)
        .filter(UserGroupKeys.name, groupName)
        .get();
  }

  @Override
  public UserGroup getAdminUserGroup(String accountId) {
    PageRequest<UserGroup> pageRequest =
        aPageRequest()
            .addFilter(UserGroupKeys.name, EQ, UserGroup.DEFAULT_ACCOUNT_ADMIN_USER_GROUP_NAME)
            .addFilter(UserGroupKeys.isDefault, EQ, true)
            .build();

    return list(accountId, pageRequest, true, null, null).getResponse().get(0);
  }

  @Override
  public boolean deleteNonAdminUserGroups(String accountId) {
    return listByAccountId(accountId)
        .stream()
        .filter(userGroup -> !UserGroupUtils.isAdminUserGroup(userGroup))
        .map(userGroup -> delete(accountId, userGroup.getUuid(), false))
        .reduce(true, (a, b) -> a && b);
  }

  @Override
  public boolean deleteUserGroupsByName(String accountId, List<String> userGroupsToRetain) {
    if (CollectionUtils.isEmpty(userGroupsToRetain)) {
      throw new IllegalArgumentException("'userGroupsToRetain' is empty");
    }

    Set<String> userGroupsToDelete = listByAccountId(accountId)
                                         .stream()
                                         .filter(userGroup -> !userGroupsToRetain.contains(userGroup.getName()))
                                         .map(UuidAware::getUuid)
                                         .collect(toSet());

    for (String userGroupToDelete : userGroupsToDelete) {
      delete(accountId, userGroupToDelete, false);
    }

    return true;
  }

  @Override
  public List<UserGroup> getUserGroupsFromUserInvite(UserInvite userInvite) {
    Object[] userIds = userInvite.getUserGroups().stream().map(UuidAware::getUuid).toArray();
    if (isEmpty(userIds)) {
      return emptyList();
    }
    PageRequestBuilder pageRequest =
        aPageRequest().withLimit(Integer.toString(userIds.length)).addFilter(UserGroup.ID_KEY2, Operator.IN, userIds);
    return list(userInvite.getAccountId(), pageRequest.build(), true, null, null).getResponse();
  }

  /**
   * Removing permissions which were created for specific applications (filter type is SELECTED) if all of those
   * applications are deleted
   */
  private void removeEmptyAppPermissions(UserGroup userGroup) {
    Set<AppPermission> appPermissions = userGroup.getAppPermissions();
    Set<AppPermission> updatedAppPermissions =
        appPermissions.stream()
            .filter(appPermission
                -> !(isAppPermissionSelected(appPermission) && isAppPermissionWithEmptyIds(appPermission)))
            .collect(Collectors.toSet());
    userGroup.setAppPermissions(updatedAppPermissions);
  }

  private boolean isAppPermissionSelected(AppPermission appPermission) {
    AppFilter appFilter = appPermission.getAppFilter();
    if (appFilter == null) {
      return false;
    }

    String filterType = appFilter.getFilterType();
    if (isEmpty(filterType)) {
      return false;
    }

    return filterType.equals(AppFilter.FilterType.SELECTED);
  }

  private boolean isAppPermissionWithEmptyIds(AppPermission appPermission) {
    return isEmpty(appPermission.getAppFilter().getIds());
  }

  private boolean isEntityFilterWithCustomIds(Filter entityFilter) {
    if (entityFilter != null && isNotEmpty(entityFilter.getIds())) {
      return true;
    }

    return false;
  }

  private void updateUserPermissionAndRestrictionCache(UserGroup userGroup) {
    notNullCheck("Invalid userGroup", userGroup);
    if (isNotEmpty(userGroup.getMemberIds())) {
      userGroup.getMemberIds().forEach(userId -> {
        User user = userService.get(userId);
        String accountId = userGroup.getAccountId();
        authService.updateUserPermissionCacheInfo(accountId, user, false);
        UserPermissionInfo userPermissionInfo = authService.getUserPermissionInfo(accountId, user, false);
        authService.updateUserRestrictionCacheInfo(accountId, user, userPermissionInfo, false);
      });
    }
  }
  @Override
  public void pruneByApplication(String appId) {
    Set<String> deletedIds = new HashSet<>();
    deletedIds.add(appId);

    String accountId = appService.getAccountIdByAppId(appId);
    log.info("[USERGROUP-DEBUG]: deleted appId {} with app {} for account {}", deletedIds, appId, accountId);
    Query<UserGroup> query = createQueryForUserGroup(accountId, deletedIds);
    log.info("[USERGROUP-DEBUG]: appId {} query {}", appId, query);
    try (HIterator<UserGroup> userGroupIterator = new HIterator<>(query.fetch())) {
      while (userGroupIterator.hasNext()) {
        final UserGroup userGroup = userGroupIterator.next();
        removeAppIdsFromAppPermissions(userGroup, deletedIds);
      }
    }
  }

  @VisibleForTesting
  protected Query<UserGroup> createQueryForUserGroup(String accountId, Set<String> deletedIds) {
    if (isNotEmpty(accountId)) {
      return wingsPersistence.createQuery(UserGroup.class)
          .filter(UserGroupKeys.accountId, accountId)
          .project(UserGroup.ID_KEY2, true)
          .project(UserGroupKeys.accountId, true)
          .project(UserGroupKeys.appPermissions, true)
          .project(UserGroupKeys.memberIds, true);
    } else {
      return wingsPersistence.createQuery(UserGroup.class)
          .field(UserGroupKeys.appIds)
          .in(deletedIds)
          .project(UserGroup.ID_KEY2, true)
          .project(UserGroupKeys.accountId, true)
          .project(UserGroupKeys.appPermissions, true)
          .project(UserGroupKeys.memberIds, true);
    }
  }

  @Override
  public void pruneByService(String appId, String serviceId) {
    pruneEntityIdFromUserGroup(appId, serviceId);
  }

  @Override
  public void pruneByEnvironment(String appId, String envId) {
    pruneEntityIdFromUserGroup(appId, envId);
  }

  @Override
  public void pruneByInfrastructureProvisioner(String appId, String infrastructureProvisionerId) {
    pruneEntityIdFromUserGroup(appId, infrastructureProvisionerId);
  }

  @Override
  public void pruneByPipeline(String appId, String pipelineId) {
    pruneEntityIdFromUserGroup(appId, pipelineId);
  }

  @Override
  public void pruneByWorkflow(String appId, String workflowId) {
    pruneEntityIdFromUserGroup(appId, workflowId);
  }

  @Override
  public void pruneByTemplate(String appId, String templateId) {
    pruneEntityIdFromUserGroup(appId, templateId);
  }
}
