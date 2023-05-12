/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.scim;

import static io.harness.annotations.dev.HarnessModule._360_CG_MANAGER;
import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.exception.WingsException.GROUP;

import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.UnauthorizedException;
import io.harness.exception.WingsException;
import io.harness.scim.Member;
import io.harness.scim.PatchOperation;
import io.harness.scim.PatchRequest;
import io.harness.scim.ScimGroup;
import io.harness.scim.ScimListResponse;
import io.harness.scim.ScimMultiValuedObject;
import io.harness.scim.service.ScimGroupService;

import software.wings.beans.Event;
import software.wings.beans.User;
import software.wings.beans.security.UserGroup;
import software.wings.beans.security.UserGroup.UserGroupKeys;
import software.wings.dl.WingsPersistence;
import software.wings.service.impl.AuditServiceHelper;
import software.wings.service.intfc.UserGroupService;
import software.wings.service.intfc.UserService;

import com.google.inject.Inject;
import dev.morphia.query.FindOptions;
import dev.morphia.query.Query;
import dev.morphia.query.UpdateOperations;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.SetUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.util.Strings;

@Slf4j
@OwnedBy(PL)
@TargetModule(_360_CG_MANAGER)
public class ScimGroupServiceImpl implements ScimGroupService {
  @Inject private UserGroupService userGroupService;
  @Inject private WingsPersistence wingsPersistence;
  @Inject private AuditServiceHelper auditServiceHelper;
  @Inject private UserService userService;

  private static final String EXC_MSG_GROUP_DOESNT_EXIST = "Group does not exist";
  private static final String DISPLAY_NAME = "displayName";
  private static final Integer MAX_RESULT_COUNT = 20;
  private static final String REPLACE_OKTA = "replace";
  private static final String REPLACE = "Replace";
  private static final String ADD = "Add";
  private static final String ADD_OKTA = "add";
  private static final String REMOVE = "Remove";
  private static final String REMOVE_OKTA = "remove";

  @Override
  public ScimListResponse<ScimGroup> searchGroup(String filter, String accountId, Integer count, Integer startIndex) {
    startIndex = startIndex == null ? 0 : startIndex;
    Integer tempStartIndex = startIndex == 0 ? 0 : startIndex - 1;
    count = count == null ? MAX_RESULT_COUNT : count;
    ScimListResponse<ScimGroup> searchGroupResponse = new ScimListResponse<>();
    log.info("SCIM: Searching groups in account {} with filter: {}", accountId, filter);
    String searchQuery = null;

    if (StringUtils.isNotEmpty(filter)) {
      try {
        filter = URLDecoder.decode(filter, "UTF-8");
        String[] split = filter.split(" eq ");
        String operand = split[1];
        searchQuery = operand.substring(1, operand.length() - 1);
        log.info("SCIM: Search query is {}, for accountId {}", searchQuery, accountId);
      } catch (Exception ex) {
        log.error("SCIM: Failed to process for account {} group search query: {} ", accountId, filter, ex);
      }
    }

    List<ScimGroup> groupList = new ArrayList<>();

    try {
      groupList = searchUserGroupByGroupName(accountId, searchQuery, count, tempStartIndex);
      groupList.forEach(searchGroupResponse::resource);
    } catch (WingsException ex) {
      log.info("SCIM: Search in account {} for group , query: {}", accountId, searchQuery, ex);
    }

    searchGroupResponse.startIndex(startIndex);
    searchGroupResponse.itemsPerPage(count);
    searchGroupResponse.totalResults(groupList.size());
    log.info("SCIM: Search Group Response is {}, for accountId {}",
        searchGroupResponse.getResources().stream().map(ScimGroup::getDisplayName).collect(Collectors.toList()),
        accountId);
    return searchGroupResponse;
  }

  private List<ScimGroup> searchUserGroupByGroupName(
      String accountId, String searchQuery, Integer count, Integer startIndex) {
    Query<UserGroup> userGroupQuery = wingsPersistence.createQuery(UserGroup.class)
                                          .field(UserGroupKeys.accountId)
                                          .equal(accountId)
                                          .field(UserGroupKeys.importedByScim)
                                          .equal(true);

    if (StringUtils.isNotEmpty(searchQuery)) {
      userGroupQuery.field(UserGroupKeys.name).equal(searchQuery);
    }

    List<UserGroup> userGroupList = userGroupQuery.asList(new FindOptions().skip(startIndex).limit(count));

    List<ScimGroup> scimGroupList = new ArrayList<>();
    for (UserGroup userGroup : userGroupList) {
      scimGroupList.add(buildGroupResponse(userGroup));
    }
    return scimGroupList;
  }

  private void updateDisplayName(
      ScimGroup scimGroup, UserGroup userGroup, UpdateOperations<UserGroup> updateOperations) {
    String newDisplayName = scimGroup.getDisplayName();
    if (StringUtils.isNotEmpty(newDisplayName) && !userGroup.getName().equals(newDisplayName)) {
      updateOperations.set(UserGroupKeys.name, newDisplayName);
    }
  }

  @Override
  public Response updateGroup(String groupId, String accountId, ScimGroup scimGroup) {
    log.info(
        "SCIM: Update group call with accountId: {}, groupId {}, group resource:{}", accountId, groupId, scimGroup);
    UserGroup userGroup = userGroupService.get(accountId, groupId, false);
    if (userGroup == null) {
      return Response.status(Status.NOT_FOUND).build();
    }

    UpdateOperations<UserGroup> updateOperations = wingsPersistence.createUpdateOperations(UserGroup.class);
    updateDisplayName(scimGroup, userGroup, updateOperations);
    log.info("SCIM: SCIM Group {} is being synced, for accountId {}", scimGroup.getDisplayName(), accountId);
    Set<String> newMemberIds = new HashSet<>(getMembersOfUserGroup(scimGroup));
    Set<String> existingMemberIds =
        isEmpty(userGroup.getMemberIds()) ? new HashSet<>() : new HashSet<>(userGroup.getMemberIds());
    updateMembers(scimGroup, updateOperations, accountId);
    wingsPersistence.update(userGroup, updateOperations);
    auditUserGroupUpdates(groupId, accountId, userGroup, SetUtils.difference(newMemberIds, existingMemberIds),
        SetUtils.difference(existingMemberIds, newMemberIds), "PUT");

    log.info("SCIM: Update group call successful accountId {}, groupId  {}, group resource: {}", accountId, groupId,
        scimGroup);
    return Response.status(Status.OK).entity(scimGroup).build();
  }

  private void updateMembers(ScimGroup scimGroup, UpdateOperations<UserGroup> updateOperations, String accountId) {
    updateOperations.set(UserGroupKeys.memberIds, getMembersOfUserGroup(scimGroup));
    log.info("SCIM: Member userIds provided by the SCIM Provider are {}, for account {}",
        getMembersOfUserGroup(scimGroup), accountId);
  }

  @Override
  public void deleteGroup(String groupId, String accountId) {
    log.info("SCIM: Deleting from account {}, group {}", accountId, groupId);
    wingsPersistence.delete(accountId, UserGroup.class, groupId);
    log.info("SCIM: Deleted from account {}, group {}", accountId, groupId);
  }

  private String processReplaceOperationOnGroup(String groupId, String accountId, PatchOperation patchOperation) {
    if (!DISPLAY_NAME.equals(patchOperation.getPath())) {
      log.error(
          "SCIM: Expected replace operation only on the displayName. Received it on path: {}, for accountId: {}, for GroupId {}",
          patchOperation.getPath(), accountId, groupId);
      // no operation needed. Pass
    } else {
      try {
        return patchOperation.getValue(String.class);
      } catch (Exception ex) {
        log.error("SCIM: Failed to process the operation: {}, for accountId: {}, for GroupId {}",
            patchOperation.toString(), accountId, groupId, ex);
      }
    }
    throw new InvalidRequestException("Failed to update group name");
  }

  private String processOktaReplaceOperationOnGroup(String groupId, String accountId, PatchOperation patchOperation) {
    try {
      if (patchOperation.getValue(ScimMultiValuedObject.class) != null) {
        return patchOperation.getValue(ScimMultiValuedObject.class).getDisplayName();
      }
    } catch (Exception ex) {
      log.error("SCIM: Failed to process the REPLACE_OKTA operation: {}, for accountId: {}, for GroupId {}",
          patchOperation.toString(), accountId, groupId, ex);
    }
    throw new InvalidRequestException("Failed to update group name");
  }

  @Override
  public Response updateGroup(String groupId, String accountId, PatchRequest patchRequest) {
    String operation = isNotEmpty(patchRequest.getOperations()) ? patchRequest.getOperations().toString() : null;
    String schemas = isNotEmpty(patchRequest.getSchemas()) ? patchRequest.getSchemas().toString() : null;
    log.info(
        "SCIM: Updating Group: Patch Request Logging\nOperations {}\n, Schemas {}\n,External Id {}\n, Meta {}, for accountId {}",
        operation, schemas, patchRequest.getExternalId(), patchRequest.getMeta(), accountId);
    UserGroup existingGroup = userGroupService.get(accountId, groupId);

    if (existingGroup == null) {
      return Response.status(Status.NOT_FOUND).build();
    }

    List<String> existingMemberIds =
        isNotEmpty(existingGroup.getMemberIds()) ? existingGroup.getMemberIds() : new ArrayList<>();
    Set<String> newMemberIds = new HashSet<>(existingMemberIds);
    String newGroupName = null;
    Set<String> memberIdsToBeAdded = null;
    Set<String> memberIdsToBeRemoved = null;

    for (PatchOperation patchOperation : patchRequest.getOperations()) {
      Set<String> userIdsFromOperation = getUserIdsFromOperation(patchOperation, accountId, groupId);
      switch (patchOperation.getOpType()) {
        case REPLACE: {
          newGroupName = processReplaceOperationOnGroup(groupId, accountId, patchOperation);
          break;
        }
        case REPLACE_OKTA: {
          newGroupName = processOktaReplaceOperationOnGroup(groupId, accountId, patchOperation);
          break;
        }
        case ADD:
        case ADD_OKTA: {
          Set<String> originalMemberIds = new HashSet<>(newMemberIds);
          updateNewMemberIds(userIdsFromOperation, newMemberIds, accountId);
          memberIdsToBeAdded = SetUtils.difference(newMemberIds, originalMemberIds);
          break;
        }
        case REMOVE:
        case REMOVE_OKTA: {
          newMemberIds.removeAll(userIdsFromOperation);
          memberIdsToBeRemoved = userIdsFromOperation;
          break;
        }
        default: {
          log.error("SCIM: Received unexpected PATCH operation: {}", patchOperation.toString());
          break;
        }
      }
    }

    List<String> finalMemberIds = new ArrayList<>(newMemberIds);
    UpdateOperations<UserGroup> updateOperations = wingsPersistence.createUpdateOperations(UserGroup.class);
    boolean updateGroup = false;
    if (StringUtils.isNotEmpty(newGroupName)) {
      updateOperations.set(UserGroupKeys.name, newGroupName);
      updateGroup = true;
    }
    if (!existingMemberIds.equals(finalMemberIds)) {
      updateOperations.set(UserGroupKeys.memberIds, finalMemberIds);
      updateGroup = true;
    }
    if (updateGroup) {
      updateOperations.set(UserGroupKeys.importedByScim, true);
      wingsPersistence.update(existingGroup, updateOperations);
      auditUserGroupUpdates(groupId, accountId, existingGroup, memberIdsToBeAdded, memberIdsToBeRemoved, "PATCH");
    }
    return Response.status(Status.NO_CONTENT).build();
  }

  private void updateNewMemberIds(Set<String> userIdsFromOperation, Set<String> newMemberIds, String accountId) {
    log.info("SCIM: User Ids received from SCIM provider are {}, for accountId {}", userIdsFromOperation, accountId);
    for (String userId : userIdsFromOperation) {
      User user = wingsPersistence.get(User.class, userId);
      if (user != null) {
        newMemberIds.add(userId);
      } else {
        log.info("SCIM: No user exists in the db with the id {}", userId);
      }
    }
  }

  private Set<String> getUserIdsFromOperation(PatchOperation patchOperation, String accountId, String groupId) {
    if (patchOperation.getPath() != null && patchOperation.getPath().contains("members[")) {
      try {
        Set<String> userIds = new HashSet<>();
        String filter = URLDecoder.decode(patchOperation.getPath(), "UTF-8");
        String[] split = filter.split(" eq ");
        String operand = split[1];
        userIds.add(operand.substring(1, operand.length() - 2));
        return userIds;
      } catch (Exception ex) {
        log.error("SCIM: Not able to decode path. Received it in path: {}, for accountId: {}, for GroupId {}",
            patchOperation.getPath(), accountId, groupId, ex);
      }
    }

    if (!"members".equals(patchOperation.getPath())) {
      log.warn("SCIM: Expect operation only on the members. Received it in path: {}, for accountId: {}, for GroupId {}",
          patchOperation.getPath(), accountId, groupId);
      return Collections.emptySet();
    }

    try {
      List<? extends ScimMultiValuedObject> operations = patchOperation.getValues(ScimMultiValuedObject.class);
      if (!isEmpty(operations)) {
        return operations.stream().map(operation -> (String) operation.getValue()).collect(Collectors.toSet());
      }
      log.error("SCIM: Operations received is null. Skipping remove operation processing for groupId: {}", groupId);
    } catch (Exception ex) {
      log.error("SCIM: Failed to process the operation: {}, for accountId: {}, for GroupId {}", patchOperation,
          accountId, groupId, ex);
    }

    return Collections.emptySet();
  }

  @Override
  public ScimGroup getGroup(String groupId, String accountId) {
    UserGroup userGroup = userGroupService.get(accountId, groupId);
    if (userGroup == null) {
      log.info("SCIM: UserGroup with id {} is not found in account {}", groupId, accountId);
      throw new UnauthorizedException(EXC_MSG_GROUP_DOESNT_EXIST, GROUP);
    }
    ScimGroup scimGroup = buildGroupResponse(userGroup);
    log.info("SCIM: Response for accountId {} to get group {} with call: {}", accountId, groupId, scimGroup);
    return scimGroup;
  }

  private ScimGroup buildGroupResponse(UserGroup userGroup) {
    ScimGroup scimGroup = new ScimGroup();
    if (userGroup != null) {
      scimGroup.setId(userGroup.getUuid());
      scimGroup.setDisplayName(userGroup.getName());
      List<Member> memberList = new ArrayList<>();

      if (isNotEmpty(userGroup.getMembers())) {
        userGroup.getMembers().forEach(member -> {
          Member member1 = new Member();
          member1.setValue(member.getUuid());
          member1.setDisplay(member.getEmail());
          memberList.add(member1);
        });
      }

      scimGroup.setMembers(memberList);
    }
    return scimGroup;
  }

  private UserGroup checkIfUserGroupAlreadyPresentByName(String accountId, String userGroupName) {
    if (Strings.isEmpty(userGroupName)) {
      return null;
    }

    Query<UserGroup> userGroupQuery = wingsPersistence.createQuery(UserGroup.class)
                                          .field(UserGroupKeys.accountId)
                                          .equal(accountId)
                                          .field(UserGroupKeys.name)
                                          .equal(userGroupName);

    List<UserGroup> userGroupList = userGroupQuery.asList();
    if (isNotEmpty(userGroupList)) {
      return userGroupList.get(0);
    }
    return null;
  }

  @Override
  public ScimGroup createGroup(ScimGroup groupQuery, String accountId) {
    log.info("SCIM: Creating group in account {} where name {} with call: {}", accountId, groupQuery.getDisplayName(),
        groupQuery);

    UserGroup userGroupAlreadyPresent = checkIfUserGroupAlreadyPresentByName(accountId, groupQuery.getDisplayName());

    if (userGroupAlreadyPresent != null) {
      return getGroup(userGroupAlreadyPresent.getUuid(), accountId);
    }

    UserGroup userGroup = UserGroup.builder()
                              .accountId(accountId)
                              .name(groupQuery.getDisplayName())
                              .memberIds(getMembersOfUserGroup(groupQuery))
                              .importedByScim(true)
                              .build();
    log.info("SCIM: User ids received for account {} from SCIM provider are {}", accountId,
        getMembersOfUserGroup(groupQuery));
    userGroupService.save(userGroup);

    groupQuery.setId(userGroup.getUuid());
    log.info("SCIM: Completed creating group with name {} for account {} and call: {}", groupQuery.getDisplayName(),
        accountId, groupQuery);
    return getGroup(userGroup.getUuid(), accountId);
  }

  private List<String> getMembersOfUserGroup(ScimGroup scimGroup) {
    List<String> newMemberIds = new ArrayList<>();
    if (isNotEmpty(scimGroup.getMembers())) {
      scimGroup.getMembers().forEach(member -> {
        if (!newMemberIds.contains(member.getValue())) {
          User user = wingsPersistence.get(User.class, member.getValue());
          if (user != null) {
            newMemberIds.add(member.getValue());
          } else {
            log.info("SCIM: No user exists in the db with the id {}", member.getValue());
          }
        }
      });
    }
    return newMemberIds;
  }

  private void auditUserGroupUpdates(String groupId, String accountId, UserGroup userGroup,
      Set<String> memberIdsToBeAdded, Set<String> memberIdsToBeRemoved, String operation) {
    UserGroup updatedUserGroup = userGroupService.get(accountId, groupId);

    log.info(
        "SCIM_AUDITS_{}: auditing user group updates for group {} in account {} with memberIdsTobeAdded: {}, memberIdsToBeRemoved: {}",
        operation, groupId, accountId, getStringBuilderForMemberIds(memberIdsToBeAdded).toString(),
        getStringBuilderForMemberIds(memberIdsToBeRemoved).toString());
    auditServiceHelper.reportForAuditingUsingAccountId(
        userGroup.getAccountId(), userGroup, updatedUserGroup, Event.Type.UPDATE);
    if (isNotEmpty(memberIdsToBeAdded)) {
      memberIdsToBeAdded.forEach(userId -> {
        User user = userService.get(userId);
        auditServiceHelper.reportForAuditingUsingAccountId(userGroup.getAccountId(), null, user, Event.Type.ADD);
      });
    }
    if (isNotEmpty(memberIdsToBeRemoved)) {
      memberIdsToBeRemoved.forEach(userId -> {
        User user = userService.get(userId);
        auditServiceHelper.reportForAuditingUsingAccountId(userGroup.getAccountId(), null, user, Event.Type.REMOVE);
      });
    }
  }

  private StringBuilder getStringBuilderForMemberIds(Set<String> memberIds) {
    StringBuilder sb = new StringBuilder();
    if (isNotEmpty(memberIds)) {
      memberIds.forEach(id -> sb.append(id).append(" "));
    }
    return sb;
  }
}
