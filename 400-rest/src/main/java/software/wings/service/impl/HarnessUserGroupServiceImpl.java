/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl;

import static io.harness.annotations.dev.HarnessModule._970_RBAC_CORE;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.mongo.MongoUtils.setUnset;
import static io.harness.persistence.HQuery.excludeAuthority;
import static io.harness.validation.Validator.notNullCheck;

import static software.wings.beans.Account.AccountKeys;

import static com.google.common.collect.Sets.newHashSet;
import static com.google.common.collect.Sets.symmetricDifference;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.beans.FeatureName;
import io.harness.beans.PageRequest;
import io.harness.beans.PageResponse;
import io.harness.configuration.DeployMode;
import io.harness.exception.AccessRequestPresentException;
import io.harness.exception.InvalidArgumentsException;
import io.harness.exception.WingsException;
import io.harness.ff.FeatureFlagService;

import software.wings.beans.Account;
import software.wings.beans.Event.Type;
import software.wings.beans.User;
import software.wings.beans.security.AccessRequest;
import software.wings.beans.security.HarnessSupportUserDTO;
import software.wings.beans.security.HarnessUserGroup;
import software.wings.beans.security.HarnessUserGroupDTO;
import software.wings.dl.WingsPersistence;
import software.wings.service.intfc.AccessRequestService;
import software.wings.service.intfc.AccountService;
import software.wings.service.intfc.AuthService;
import software.wings.service.intfc.HarnessUserGroupService;
import software.wings.service.intfc.UserService;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets.SetView;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.mongodb.morphia.Key;
import org.mongodb.morphia.query.Query;
import org.mongodb.morphia.query.UpdateOperations;

@Singleton
@Slf4j
@OwnedBy(HarnessTeam.PL)
@TargetModule(_970_RBAC_CORE)
public class HarnessUserGroupServiceImpl implements HarnessUserGroupService {
  @Inject WingsPersistence wingsPersistence;
  @Inject private AuthService authService;
  @Inject private AccountService accountService;
  @Inject private AccessRequestService accessRequestService;
  @Inject private UserService userService;
  @Inject private AuditServiceHelper auditServiceHelper;
  @Inject private FeatureFlagService featureFlagService;

  @Override
  public HarnessUserGroup save(HarnessUserGroup harnessUserGroup) {
    String uuid = wingsPersistence.save(harnessUserGroup);
    String accountId = (String) harnessUserGroup.getAccountIds().toArray()[0];
    auditServiceHelper.reportForAuditingUsingAccountId(accountId, null, harnessUserGroup, Type.CREATE);
    return wingsPersistence.get(HarnessUserGroup.class, uuid);
  }

  @Override
  public PageResponse<HarnessUserGroup> list(PageRequest<HarnessUserGroup> req) {
    return wingsPersistence.query(HarnessUserGroup.class, req);
  }

  @Override
  public List<Account> listAllowedSupportAccounts(Set<String> excludeAccountIds, Set<String> fieldsToBeIncluded) {
    List<Account> supportedAccounts = accountService.listHarnessSupportAccounts(excludeAccountIds, fieldsToBeIncluded);
    supportedAccounts.sort(new AccountComparator());
    return supportedAccounts;
  }

  private static class AccountComparator implements Comparator<Account>, Serializable {
    @Override
    public int compare(Account lhs, Account rhs) {
      return lhs.getAccountName().compareToIgnoreCase(rhs.getAccountName());
    }
  }

  @Override
  public HarnessUserGroup get(String uuid) {
    return wingsPersistence.get(HarnessUserGroup.class, uuid);
  }

  @Override
  public HarnessUserGroup updateMembers(String uuid, String accountId, Set<String> memberIds) {
    HarnessUserGroup harnessUserGroup = get(uuid);

    if (harnessUserGroup == null) {
      throw new WingsException("No Harness user group found");
    }

    Set<String> oldMemberIds = harnessUserGroup.getMemberIds();

    UpdateOperations<HarnessUserGroup> updateOperations =
        wingsPersistence.createUpdateOperations(HarnessUserGroup.class);
    setUnset(updateOperations, "memberIds", memberIds);
    wingsPersistence.update(wingsPersistence.createQuery(HarnessUserGroup.class).filter("_id", uuid), updateOperations);
    HarnessUserGroup updatedUserGroup = get(uuid);

    SetView<String> membersAffected = symmetricDifference(updatedUserGroup.getMemberIds(), oldMemberIds);

    Set<String> accountsAffected = listAllowedSupportAccounts(Collections.emptySet(), Set.of(AccountKeys.uuid))
                                       .stream()
                                       .map(Account::getUuid)
                                       .collect(Collectors.toSet());

    authService.evictUserPermissionAndRestrictionCacheForAccounts(
        accountsAffected, Lists.newArrayList(membersAffected));

    auditServiceHelper.reportForAuditingUsingAccountId(accountId, harnessUserGroup, updatedUserGroup, Type.UPDATE);
    return updatedUserGroup;
  }

  @Override
  public HarnessUserGroup updateMembers(String uuid, String accountId, HarnessUserGroupDTO harnessUserGroupDTO) {
    Set<String> memberIds = getMemberIds(harnessUserGroupDTO);
    return updateMembers(uuid, accountId, memberIds);
  }

  @Override
  public boolean isHarnessSupportEnabledForAccount(String accountId) {
    Account account = accountService.get(accountId);
    notNullCheck("Invalid account with id: " + accountId, account);
    return account.isHarnessSupportAccessAllowed();
  }

  @Override
  public boolean isHarnessSupportEnabled(String accountId, String userId) {
    Account account = accountService.get(accountId);
    notNullCheck("Invalid account with id: " + accountId, account);

    if (account.isHarnessSupportAccessAllowed()) {
      return true;
    }

    List<AccessRequest> userSpecificAccessRequestList =
        accessRequestService.getActiveAccessRequestForAccountAndUser(accountId, userId);

    if (!userSpecificAccessRequestList.isEmpty()) {
      log.info("{} Active Member Access Request present for accountId: {} userId: {}",
          userSpecificAccessRequestList.size(), accountId, userId);
      return true;
    }

    List<HarnessUserGroup> restrictedHarnessUserGroupList =
        listHarnessUserGroup(accountId, userId, HarnessUserGroup.GroupType.RESTRICTED);

    if (restrictedHarnessUserGroupList.isEmpty()) {
      return false;
    }

    for (HarnessUserGroup restrictedHarnessUserGroup : restrictedHarnessUserGroupList) {
      List<AccessRequest> activeAccessRequestList =
          accessRequestService.getActiveAccessRequest(restrictedHarnessUserGroup.getUuid());
      if (activeAccessRequestList.size() > 0) {
        log.info("{} Active Group Access Request present for accountId: {} userId: {} harnessUserGroupId: {} ",
            activeAccessRequestList.size(), accountId, userId, restrictedHarnessUserGroup.getUuid());
        return true;
      }
    }
    log.info("No Active Access Request present for accountId: {} userId: {}", accountId, userId);
    return false;
  }

  @Override
  public boolean isHarnessSupportUser(String userId) {
    String deployMode = System.getenv(DeployMode.DEPLOY_MODE);
    if (DeployMode.isOnPrem(deployMode)) {
      return false;
    }

    Query<HarnessUserGroup> query = wingsPersistence.createQuery(HarnessUserGroup.class, excludeAuthority);
    query.filter("memberIds", userId);
    Key<HarnessUserGroup> userGroupKey = query.getKey();
    return userGroupKey != null;
  }

  @Override
  public boolean delete(String accountId, String uuid) {
    if (featureFlagService.isEnabled(FeatureName.LIMITED_ACCESS_FOR_HARNESS_USER_GROUP, accountId)) {
      List<AccessRequest> accessRequestList = accessRequestService.getActiveAccessRequest(uuid);
      if (isNotEmpty(accessRequestList)) {
        throw new AccessRequestPresentException(
            String.format("Cannot delete harnessUserGroupId: %s , as %d Active Access Request present associated to it",
                uuid, accessRequestList.size()));
      }
      HarnessUserGroup harnessUserGroup = wingsPersistence.get(HarnessUserGroup.class, uuid);
      auditServiceHelper.reportDeleteForAuditingUsingAccountId(accountId, harnessUserGroup);
    }

    return wingsPersistence.delete(HarnessUserGroup.class, uuid);
  }

  @Override
  public HarnessUserGroup createHarnessUserGroup(String accountId, HarnessUserGroupDTO harnessUserGroupDTO) {
    Set<String> memberIds = getMemberIds(harnessUserGroupDTO);

    HarnessUserGroup harnessUserGroup = HarnessUserGroup.builder()
                                            .name(harnessUserGroupDTO.getName())
                                            .description(harnessUserGroupDTO.getDescription())
                                            .memberIds(memberIds)
                                            .accountIds(harnessUserGroupDTO.getAccountIds())
                                            .groupType(HarnessUserGroup.GroupType.RESTRICTED)
                                            .build();
    auditServiceHelper.reportForAuditingUsingAccountId(accountId, null, harnessUserGroup, Type.CREATE);
    return save(harnessUserGroup);
  }

  @Override
  public HarnessUserGroup createHarnessUserGroup(String name, String description, Set<String> memberIds,
      Set<String> accountIds, HarnessUserGroup.GroupType groupType) {
    HarnessUserGroup harnessUserGroup = HarnessUserGroup.builder()
                                            .name(name)
                                            .description(description)
                                            .memberIds(memberIds)
                                            .accountIds(accountIds)
                                            .groupType(groupType)
                                            .build();

    return save(harnessUserGroup);
  }

  @Override
  public List<HarnessUserGroup> listHarnessUserGroupForAccount(String accountId) {
    Account account = accountService.get(accountId);
    notNullCheck("Invalid account with id: " + accountId, account);
    Query<HarnessUserGroup> query = wingsPersistence.createQuery(HarnessUserGroup.class, excludeAuthority);
    query.filter("accountIds", accountId);
    return query.asList();
  }

  public List<HarnessUserGroup> listHarnessUserGroup(
      String accountId, String memberId, HarnessUserGroup.GroupType groupType) {
    Account account = accountService.get(accountId);
    notNullCheck("Invalid account with id: " + accountId, account);
    Query<HarnessUserGroup> query = wingsPersistence.createQuery(HarnessUserGroup.class, excludeAuthority);
    query.filter("accountIds", accountId);
    query.filter("memberIds", memberId);
    query.filter("groupType", groupType);
    return query.asList();
  }

  public List<User> listAllHarnessSupportUsers() {
    Set<User> userSet = new HashSet<>();
    Query<HarnessUserGroup> query = wingsPersistence.createQuery(HarnessUserGroup.class, excludeAuthority);
    List<HarnessUserGroup> harnessUserGroupList = query.asList();
    harnessUserGroupList.forEach(harnessUserGroup -> {
      harnessUserGroup.getMemberIds().forEach(memberId -> {
        try {
          User user = userService.get(memberId);
          if (user != null) {
            userSet.add(user);
          }
        } catch (WingsException e) {
          log.error("User with id {} is invalid, not adding to the set", memberId, e);
        }
      });
    });
    List<User> userList = new ArrayList<>(userSet);
    Collections.sort(userList, Comparator.comparing(user -> user.getEmail()));
    return userList;
  }

  @Override
  public List<User> listAllHarnessSupportUserInternal() {
    Query<HarnessUserGroup> query = wingsPersistence.createQuery(HarnessUserGroup.class, excludeAuthority);
    List<HarnessUserGroup> harnessUserGroupList = query.asList();
    Set<String> userIds = harnessUserGroupList.stream()
                              .map(HarnessUserGroup::getMemberIds)
                              .flatMap(Set::stream)
                              .collect(Collectors.toSet());
    return userService.getUsers(userIds);
  }

  @Override
  public HarnessSupportUserDTO toHarnessSupportUser(User user) {
    notNullCheck("HarnessSupportUser: Invalid user", user);
    notNullCheck("HarnessSupportUser: userId " + user.getUuid() + "doesn't have emailId", user.getName());

    return HarnessSupportUserDTO.builder().name(user.getName()).emailId(user.getEmail()).id(user.getUuid()).build();
  }

  @Override
  public List<HarnessSupportUserDTO> toHarnessSupportUser(List<User> userList) {
    List<HarnessSupportUserDTO> harnessSupportUserDTOList = new ArrayList<>();

    if (isNotEmpty(userList)) {
      userList.forEach(user -> harnessSupportUserDTOList.add(toHarnessSupportUser(user)));
    }

    return harnessSupportUserDTOList;
  }

  private Set<String> getMemberIds(HarnessUserGroupDTO harnessUserGroupDTO) {
    if (isEmpty(harnessUserGroupDTO.getEmailIds())) {
      throw new InvalidArgumentsException("No Email Ids specified");
    }

    // this is for the case of workflow, with multiple emailIds.
    if (harnessUserGroupDTO.getEmailIds().size() == 1) {
      String emailIds = (String) harnessUserGroupDTO.getEmailIds().toArray()[0];
      if (emailIds.contains(",")) {
        List<String> tokenizedEmailIds = tokenizeInput(emailIds);
        harnessUserGroupDTO.setEmailIds(newHashSet(tokenizedEmailIds));
      }
    }

    Set<String> memberIds = new HashSet<>();
    harnessUserGroupDTO.getEmailIds().forEach(emailId -> {
      User user = userService.getUserByEmail(emailId);
      notNullCheck(String.format("No User present with emailId : %s", emailId), user);
      memberIds.add(userService.getUserByEmail(emailId).getUuid());
    });
    return memberIds;
  }

  private List<String> tokenizeInput(String emailIds) {
    List<String> output = new ArrayList<>();
    StringTokenizer tokenizer = new StringTokenizer(emailIds, ",");
    while (tokenizer.hasMoreElements()) {
      output.add(tokenizer.nextToken().replaceAll(" ", ""));
    }
    return output;
  }
}
