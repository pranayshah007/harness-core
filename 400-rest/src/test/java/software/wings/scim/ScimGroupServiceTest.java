/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.scim;

import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.rule.OwnerRule.KAPIL;
import static io.harness.rule.OwnerRule.PRATEEK;
import static io.harness.rule.OwnerRule.UJJAWAL;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.category.element.UnitTests;
import io.harness.exception.UnauthorizedException;
import io.harness.rule.Owner;
import io.harness.scim.AddOperation;
import io.harness.scim.Member;
import io.harness.scim.OktaAddOperation;
import io.harness.scim.OktaRemoveOperation;
import io.harness.scim.OktaReplaceOperation;
import io.harness.scim.PatchRequest;
import io.harness.scim.RemoveOperation;
import io.harness.scim.ScimGroup;
import io.harness.scim.ScimListResponse;
import io.harness.scim.service.ScimGroupService;

import software.wings.WingsBaseTest;
import software.wings.beans.Account;
import software.wings.beans.User;
import software.wings.beans.security.UserGroup;
import software.wings.dl.WingsPersistence;
import software.wings.service.impl.AuditServiceHelper;
import software.wings.service.intfc.UserGroupService;
import software.wings.service.intfc.UserService;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.gson.JsonObject;
import com.google.inject.Inject;
import dev.morphia.query.Query;
import dev.morphia.query.UpdateOperations;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;

@FieldDefaults(level = AccessLevel.PRIVATE)
@Slf4j
public class ScimGroupServiceTest extends WingsBaseTest {
  private static final String USER_ID_1 = "id1";
  private static final String USER_ID_2 = "id2";
  private static final String VALUE = "value";
  private static final String MEMBERS = "members";
  private static final String GROUP_ID = "groupId";
  private static final String ACCOUNT_ID = "accountId";
  private static final String RANDOM_ID = "Random";
  private static final Integer MAX_RESULT_COUNT = 20;

  @Inject WingsPersistence realWingsPersistence;
  @Mock WingsPersistence wingsPersistence;

  @Mock UserGroupService userGroupService;
  @Mock AuditServiceHelper auditServiceHelper;
  @Mock UserService userService;
  @Inject @InjectMocks ScimGroupService scimGroupService;

  UpdateOperations<UserGroup> updateOperations;
  Query<UserGroup> userGroupQuery;

  ObjectMapper mapper = new ObjectMapper();

  @Before
  public void setup() throws IllegalAccessException {
    updateOperations = realWingsPersistence.createUpdateOperations(UserGroup.class);
    userGroupQuery = realWingsPersistence.createQuery(UserGroup.class);
  }

  @Test
  @Owner(developers = UJJAWAL)
  @Category(UnitTests.class)
  public void testUpdateGroupRemoveMembersShouldPass() {
    PatchRequest patchRequest = getRemoveOperation();

    UserGroup userGroup = UserGroup.builder().memberIds(Arrays.asList(RANDOM_ID)).build();
    when(userGroupService.get(anyString(), anyString())).thenReturn(userGroup);
    when(wingsPersistence.createUpdateOperations(UserGroup.class)).thenReturn(updateOperations);

    when(wingsPersistence.update(any(UserGroup.class), any())).thenReturn(null);
    scimGroupService.updateGroup(GROUP_ID, ACCOUNT_ID, patchRequest);

    verify(wingsPersistence, times(0)).update(userGroup, updateOperations);

    userGroup.setMemberIds(Arrays.asList(USER_ID_1, USER_ID_2));
    scimGroupService.updateGroup(GROUP_ID, ACCOUNT_ID, patchRequest);

    verify(wingsPersistence, times(1)).update(userGroup, updateOperations);
  }

  @Test
  @Owner(developers = UJJAWAL)
  @Category(UnitTests.class)
  public void testUpdateGroupRemoveMembersShouldPassOkta() {
    PatchRequest patchRequest = getOktaRemoveOperation();

    UserGroup userGroup = UserGroup.builder()
                              .memberIds(Arrays.asList(RANDOM_ID))
                              .name("user_group")
                              .uuid(GROUP_ID)
                              .accountId(ACCOUNT_ID)
                              .appId(generateUuid())
                              .createdAt(1L)
                              .build();

    when(userGroupService.get(anyString(), anyString())).thenReturn(userGroup);
    when(wingsPersistence.createUpdateOperations(UserGroup.class)).thenReturn(updateOperations);

    when(wingsPersistence.update(any(UserGroup.class), any())).thenReturn(null);
    scimGroupService.updateGroup(GROUP_ID, ACCOUNT_ID, patchRequest);

    verify(wingsPersistence, times(0)).update(userGroup, updateOperations);

    userGroup.setMemberIds(Arrays.asList(USER_ID_1, USER_ID_2));
    scimGroupService.updateGroup(GROUP_ID, ACCOUNT_ID, patchRequest);

    verify(wingsPersistence, times(1)).update(userGroup, updateOperations);
  }

  @Test
  @Owner(developers = PRATEEK)
  @Category(UnitTests.class)
  public void testUpdateGroupRemoveMembersShouldPassOkta_andLogInAudit() {
    PatchRequest patchRequest = getOktaRemoveOperation();

    UserGroup userGroup = UserGroup.builder()
                              .memberIds(Arrays.asList(RANDOM_ID))
                              .name("user_group")
                              .uuid(GROUP_ID)
                              .accountId(ACCOUNT_ID)
                              .appId(generateUuid())
                              .createdAt(1L)
                              .build();

    when(userGroupService.get(anyString(), anyString())).thenReturn(userGroup);
    when(wingsPersistence.createUpdateOperations(UserGroup.class)).thenReturn(updateOperations);

    when(wingsPersistence.update(any(UserGroup.class), any())).thenReturn(null);
    scimGroupService.updateGroup(GROUP_ID, ACCOUNT_ID, patchRequest);

    verify(wingsPersistence, times(0)).update(userGroup, updateOperations);

    userGroup.setMemberIds(Arrays.asList(USER_ID_1, USER_ID_2));
    scimGroupService.updateGroup(GROUP_ID, ACCOUNT_ID, patchRequest);

    verify(wingsPersistence, times(1)).update(userGroup, updateOperations);
    verify(auditServiceHelper, times(2)).reportForAuditingUsingAccountId(anyString(), any(), any(), any());
    verify(userService, times(1)).get(anyString());
  }

  @Test
  @Owner(developers = UJJAWAL)
  @Category(UnitTests.class)
  public void testUpdateGroupAddMembersShouldPass() {
    PatchRequest patchRequest = getAddOperation();

    UserGroup userGroup = UserGroup.builder()
                              .memberIds(Arrays.asList(USER_ID_2))
                              .name("user_group")
                              .uuid(GROUP_ID)
                              .accountId(ACCOUNT_ID)
                              .appId(generateUuid())
                              .createdAt(1L)
                              .build();

    User user = new User();
    user.setUuid(USER_ID_2);
    when(userGroupService.get(anyString(), anyString())).thenReturn(userGroup);
    when(wingsPersistence.get(eq(User.class), anyString())).thenReturn(user);
    when(wingsPersistence.createUpdateOperations(UserGroup.class)).thenReturn(updateOperations);

    when(wingsPersistence.update(any(UserGroup.class), any())).thenReturn(null);
    scimGroupService.updateGroup(GROUP_ID, ACCOUNT_ID, patchRequest);

    verify(wingsPersistence, times(1)).update(userGroup, updateOperations);
  }

  @Test
  @Owner(developers = PRATEEK)
  @Category(UnitTests.class)
  public void testUpdateGroupAddMembersShouldPass_andLogAudit() {
    PatchRequest patchRequest = getAddOperation();

    UserGroup userGroup = UserGroup.builder()
                              .memberIds(Arrays.asList(USER_ID_2))
                              .name("user_group")
                              .uuid(GROUP_ID)
                              .accountId(ACCOUNT_ID)
                              .appId(generateUuid())
                              .createdAt(1L)
                              .build();

    User user = new User();
    user.setUuid(USER_ID_2);
    when(userGroupService.get(anyString(), anyString())).thenReturn(userGroup);
    when(wingsPersistence.get(eq(User.class), anyString())).thenReturn(user);
    when(wingsPersistence.createUpdateOperations(UserGroup.class)).thenReturn(updateOperations);

    when(wingsPersistence.update(any(UserGroup.class), any())).thenReturn(null);
    scimGroupService.updateGroup(GROUP_ID, ACCOUNT_ID, patchRequest);

    verify(wingsPersistence, times(1)).update(userGroup, updateOperations);
    verify(auditServiceHelper, times(2)).reportForAuditingUsingAccountId(anyString(), any(), any(), any());
    verify(userService, times(1)).get(anyString());
  }

  @Test
  @Owner(developers = UJJAWAL)
  @Category(UnitTests.class)
  public void testUpdateGroupAddMembersShouldPassOkta() {
    PatchRequest patchRequest = getOktaAddOperation();

    UserGroup userGroup = UserGroup.builder()
                              .memberIds(Arrays.asList(USER_ID_2))
                              .name("user_group")
                              .uuid(GROUP_ID)
                              .accountId(ACCOUNT_ID)
                              .appId(generateUuid())
                              .createdAt(1L)
                              .build();

    User user = new User();
    user.setUuid(USER_ID_2);
    when(userGroupService.get(anyString(), anyString())).thenReturn(userGroup);
    when(wingsPersistence.get(eq(User.class), anyString())).thenReturn(user);
    when(wingsPersistence.createUpdateOperations(UserGroup.class)).thenReturn(updateOperations);

    when(wingsPersistence.update(any(UserGroup.class), any())).thenReturn(null);
    scimGroupService.updateGroup(GROUP_ID, ACCOUNT_ID, patchRequest);

    verify(wingsPersistence, times(1)).update(userGroup, updateOperations);
  }

  @Test
  @Owner(developers = UJJAWAL)
  @Category(UnitTests.class)
  public void testUpdateGroupAddMembersShouldFail() {
    updateOperations = realWingsPersistence.createUpdateOperations(UserGroup.class);
    PatchRequest patchRequest = getInvalidOperation();
    User user = new User();
    user.setUuid(USER_ID_2);

    UserGroup userGroup = UserGroup.builder()
                              .memberIds(Collections.emptyList())
                              .name("user_group")
                              .uuid(GROUP_ID)
                              .accountId(ACCOUNT_ID)
                              .appId(generateUuid())
                              .createdAt(1L)
                              .build();

    when(userGroupService.get(anyString(), anyString())).thenReturn(userGroup);
    when(wingsPersistence.createUpdateOperations(UserGroup.class)).thenReturn(updateOperations);
    when(wingsPersistence.get(eq(User.class), anyString())).thenReturn(user);

    when(wingsPersistence.update(any(UserGroup.class), any())).thenReturn(null);
    scimGroupService.updateGroup(GROUP_ID, ACCOUNT_ID, patchRequest);

    verify(wingsPersistence, times(0)).update(userGroup, updateOperations);
  }

  @Test
  @Owner(developers = UJJAWAL)
  @Category(UnitTests.class)
  public void testUpdateGroupAddMembersShouldFailOkta() {
    updateOperations = realWingsPersistence.createUpdateOperations(UserGroup.class);
    PatchRequest patchRequest = getInvalidOktaOperation();
    User user = new User();
    user.setUuid(USER_ID_2);

    UserGroup userGroup = UserGroup.builder()
                              .memberIds(Collections.emptyList())
                              .name("user_group")
                              .uuid(GROUP_ID)
                              .accountId(ACCOUNT_ID)
                              .appId(generateUuid())
                              .createdAt(1L)
                              .build();

    when(userGroupService.get(anyString(), anyString())).thenReturn(userGroup);
    when(wingsPersistence.createUpdateOperations(UserGroup.class)).thenReturn(updateOperations);
    when(wingsPersistence.get(eq(User.class), anyString())).thenReturn(user);

    when(wingsPersistence.update(any(UserGroup.class), any())).thenReturn(null);
    scimGroupService.updateGroup(GROUP_ID, ACCOUNT_ID, patchRequest);

    verify(wingsPersistence, times(0)).update(userGroup, updateOperations);
  }

  @Test
  @Owner(developers = UJJAWAL)
  @Category(UnitTests.class)
  public void testReplaceOperationOkta() {
    PatchRequest patchRequest = getOktaReplaceOperation();

    UserGroup userGroup = UserGroup.builder()
                              .memberIds(Collections.emptyList())
                              .name("user_group")
                              .uuid(GROUP_ID)
                              .accountId(ACCOUNT_ID)
                              .appId(generateUuid())
                              .createdAt(1L)
                              .build();

    when(userGroupService.get(anyString(), anyString())).thenReturn(userGroup);
    when(wingsPersistence.createUpdateOperations(UserGroup.class)).thenReturn(updateOperations);

    scimGroupService.updateGroup(GROUP_ID, ACCOUNT_ID, patchRequest);
    verify(wingsPersistence, times(1)).update(userGroup, updateOperations);
  }

  @Test
  @Owner(developers = UJJAWAL)
  @Category(UnitTests.class)
  public void testSearchGroup() {
    String filter = "value eq 'username@harness.io'";
    int count = 1;
    int startIndex = 1;

    when(wingsPersistence.createQuery(UserGroup.class)).thenReturn(userGroupQuery);
    ScimListResponse<ScimGroup> scimGroupScimListResponse =
        scimGroupService.searchGroup(filter, ACCOUNT_ID, count, startIndex);

    assertThat(scimGroupScimListResponse).isNotNull();
    assertThat(scimGroupScimListResponse.getItemsPerPage()).isEqualTo(count);
    assertThat(scimGroupScimListResponse.getStartIndex()).isEqualTo(startIndex);
  }

  @Test
  @Owner(developers = KAPIL)
  @Category(UnitTests.class)
  public void testSearchGroup_WithStartIndexAndCountAsNULL() {
    String filter = "value eq 'username@harness.io'";
    Integer count = null;
    Integer startIndex = null;

    when(wingsPersistence.createQuery(UserGroup.class)).thenReturn(userGroupQuery);
    ScimListResponse<ScimGroup> scimGroupScimListResponse =
        scimGroupService.searchGroup(filter, ACCOUNT_ID, count, startIndex);

    assertThat(scimGroupScimListResponse).isNotNull();
    assertThat(scimGroupScimListResponse.getItemsPerPage()).isEqualTo(MAX_RESULT_COUNT);
    assertThat(scimGroupScimListResponse.getStartIndex()).isEqualTo(0);
  }

  @Test
  @Owner(developers = UJJAWAL)
  @Category(UnitTests.class)
  public void testGetNullGroup() {
    when(userGroupService.get(ACCOUNT_ID, GROUP_ID)).thenReturn(null);
    try {
      scimGroupService.getGroup(GROUP_ID, ACCOUNT_ID);
    } catch (UnauthorizedException ue) {
      assertThat(ue).isNotNull();
    }
  }

  @Test
  @Owner(developers = UJJAWAL)
  @Category(UnitTests.class)
  public void testGetGroup() {
    User user = new User();
    user.setUuid(USER_ID_1);
    user.setName("UserName");
    user.setEmail("emailId");

    UserGroup userGroup = UserGroup.builder()
                              .memberIds(Arrays.asList(USER_ID_1))
                              .members(Arrays.asList(user))
                              .name("user_group")
                              .uuid(GROUP_ID)
                              .accountId(ACCOUNT_ID)
                              .appId(generateUuid())
                              .createdAt(1L)
                              .build();

    when(userGroupService.get(ACCOUNT_ID, GROUP_ID)).thenReturn(userGroup);

    ScimGroup scimGroup = scimGroupService.getGroup(GROUP_ID, ACCOUNT_ID);

    assertThat(scimGroup).isNotNull();
    assertThat(scimGroup.getId()).isEqualTo(userGroup.getUuid());
    assertThat(scimGroup.getDisplayName()).isEqualTo(userGroup.getName());
    assertThat(scimGroup.getMembers().size()).isEqualTo(userGroup.getMemberIds().size());
    assertThat(scimGroup.getMembers().get(0).getDisplay()).isEqualTo(user.getEmail());
    assertThat(scimGroup.getMembers().get(0).getValue()).isEqualTo(user.getUuid());
  }

  @Test
  @Owner(developers = UJJAWAL)
  @Category(UnitTests.class)
  public void testGetEmptyMemberGroup() {
    UserGroup userGroup = UserGroup.builder()
                              .name("user_group")
                              .uuid(GROUP_ID)
                              .accountId(ACCOUNT_ID)
                              .appId(generateUuid())
                              .createdAt(1L)
                              .build();

    when(userGroupService.get(ACCOUNT_ID, GROUP_ID)).thenReturn(userGroup);

    ScimGroup scimGroup = scimGroupService.getGroup(GROUP_ID, ACCOUNT_ID);

    assertThat(scimGroup).isNotNull();
    assertThat(scimGroup.getId()).isEqualTo(userGroup.getUuid());
    assertThat(scimGroup.getDisplayName()).isEqualTo(userGroup.getName());
    assertThat(scimGroup.getMembers().size()).isEqualTo(0);
  }

  @Test
  @Owner(developers = UJJAWAL)
  @Category(UnitTests.class)
  public void testDeleteGroup() {
    Account account = new Account();
    account.setUuid(generateUuid());
    account.setAccountName("ACCOUNT_NAME");

    UserGroup userGroup = UserGroup.builder()
                              .uuid(generateUuid())
                              .name("group_name")
                              .accountId(account.getUuid())
                              .memberIds(Collections.emptyList())
                              .build();

    when(userGroupService.get(anyString(), anyString())).thenReturn(userGroup);
    when(wingsPersistence.createUpdateOperations(UserGroup.class)).thenReturn(updateOperations);

    scimGroupService.deleteGroup(userGroup.getUuid(), account.getUuid());
    verify(wingsPersistence, times(1)).delete(account.getUuid(), UserGroup.class, userGroup.getUuid());
  }

  @Test
  @Owner(developers = UJJAWAL)
  @Category(UnitTests.class)
  public void testCreateGroupWithNoMembers() {
    Account account = new Account();
    account.setUuid(generateUuid());
    account.setAccountName("ACCOUNT_NAME");

    ScimGroup scimGroup = new ScimGroup();
    scimGroup.setDisplayName("display-name");
    scimGroup.setMembers(null);

    UserGroup userGroup = UserGroup.builder()
                              .uuid(generateUuid())
                              .name("display-name")
                              .accountId(account.getUuid())
                              .memberIds(Collections.emptyList())
                              .build();

    when(userGroupService.get(eq(account.getUuid()), any())).thenReturn(userGroup);
    when(wingsPersistence.createQuery(UserGroup.class)).thenReturn(userGroupQuery);

    ScimGroup scimGroupCreated = scimGroupService.createGroup(scimGroup, account.getUuid());
    assertThat(scimGroupCreated).isNotNull();
    assertThat(scimGroup.getMembers()).isNull();
    assertThat(scimGroup.getDisplayName()).isEqualTo(scimGroupCreated.getDisplayName());
  }

  @Test
  @Owner(developers = UJJAWAL)
  @Category(UnitTests.class)
  public void testCreateGroupWithMembers() {
    Account account = new Account();
    account.setUuid(generateUuid());
    account.setAccountName("ACCOUNT_NAME");

    Member member1 = new Member();
    member1.setDisplay("display1");
    member1.setValue("value1");

    Member member2 = new Member();
    member2.setDisplay("display2");
    member2.setValue("value2");

    List<Member> members = new ArrayList<>();
    members.add(member1);
    members.add(member2);

    List<String> memberIds = new ArrayList<>();
    memberIds.add("userId1");
    memberIds.add("userId2");

    ScimGroup scimGroup = new ScimGroup();
    scimGroup.setDisplayName("display-name");
    scimGroup.setMembers(members);

    User user1 = new User();
    user1.setUuid("userId1");

    User user2 = new User();
    user2.setUuid("userId2");

    List<User> userGroupMembers = new ArrayList<>();
    userGroupMembers.add(user1);
    userGroupMembers.add(user2);

    UserGroup userGroup = UserGroup.builder()
                              .uuid(generateUuid())
                              .name("display-name")
                              .accountId(account.getUuid())
                              .memberIds(memberIds)
                              .members(userGroupMembers)
                              .build();
    User user = new User();
    user.setUuid(generateUuid());

    when(userGroupService.get(eq(account.getUuid()), any())).thenReturn(userGroup);
    when(wingsPersistence.get(eq(User.class), anyString())).thenReturn(user);
    when(wingsPersistence.createQuery(UserGroup.class)).thenReturn(userGroupQuery);

    ScimGroup scimGroupCreated = scimGroupService.createGroup(scimGroup, account.getUuid());

    assertThat(scimGroupCreated).isNotNull();
    assertThat(scimGroupCreated.getMembers()).isNotNull();
    assertThat(scimGroupCreated.getMembers().size()).isEqualTo(members.size());
    assertThat(scimGroupCreated.getDisplayName()).isEqualTo(scimGroup.getDisplayName());
  }

  @Test
  @Owner(developers = PRATEEK)
  @Category(UnitTests.class)
  public void testUpdateGroupWithMembers() {
    Account account = new Account();
    account.setUuid(generateUuid());
    account.setAccountName("ACCOUNT_NAME");

    Member member1 = new Member();
    member1.setDisplay("display1");
    member1.setValue("value1");

    List<Member> members = new ArrayList<>();
    members.add(member1);

    List<String> memberIds = new ArrayList<>();
    memberIds.add("userId1");

    ScimGroup scimGroup = new ScimGroup();
    scimGroup.setDisplayName("display-name");
    scimGroup.setMembers(members);

    User user1 = new User();
    user1.setUuid("userId1");

    List<User> userGroupMembers = new ArrayList<>();
    userGroupMembers.add(user1);
    String groupUuid = generateUuid();

    UserGroup userGroup = UserGroup.builder().uuid(groupUuid).name("display-name").accountId(account.getUuid()).build();

    UserGroup updatedUserGroup = UserGroup.builder()
                                     .uuid(groupUuid)
                                     .name("display-name")
                                     .accountId(account.getUuid())
                                     .memberIds(memberIds)
                                     .members(userGroupMembers)
                                     .build();
    User user = new User();
    user.setUuid(generateUuid());

    when(userGroupService.get(anyString(), any(), anyBoolean())).thenReturn(userGroup).thenReturn(updatedUserGroup);
    when(wingsPersistence.get(eq(User.class), anyString())).thenReturn(user);
    when(wingsPersistence.createQuery(UserGroup.class)).thenReturn(userGroupQuery);
    when(wingsPersistence.createUpdateOperations(UserGroup.class)).thenReturn(updateOperations);
    when(wingsPersistence.update(any(UserGroup.class), any())).thenReturn(null);

    scimGroupService.updateGroup(groupUuid, account.getUuid(), scimGroup);

    verify(auditServiceHelper, times(2)).reportForAuditingUsingAccountId(anyString(), any(), any(), any());
    verify(userService, times(1)).get(anyString());
  }

  private PatchRequest getOktaReplaceOperation() {
    JsonObject jsonObject = new JsonObject();
    jsonObject.addProperty("id", USER_ID_1);
    jsonObject.addProperty("displayName", USER_ID_2);
    JsonNode jsonNode;
    try {
      jsonNode = mapper.readTree(jsonObject.toString());
      OktaReplaceOperation replaceOperation = new OktaReplaceOperation(MEMBERS, jsonNode);
      return new PatchRequest(Collections.singletonList(replaceOperation));
    } catch (IOException ioe) {
      log.error("IO Exception while creating okta replace operation in SCIM", ioe);
    }
    return null;
  }

  private PatchRequest getAddOperation() {
    ArrayNode operation = mapper.createArrayNode();
    JsonNode x = mapper.createObjectNode();
    ((ObjectNode) x).put(VALUE, USER_ID_1);
    operation.add(x);
    AddOperation addOperation = new AddOperation(MEMBERS, operation);
    return new PatchRequest(Collections.singletonList(addOperation));
  }

  private PatchRequest getOktaAddOperation() {
    ArrayNode operation = mapper.createArrayNode();
    JsonNode x = mapper.createObjectNode();
    ((ObjectNode) x).put(VALUE, USER_ID_1);
    operation.add(x);
    OktaAddOperation addOperation = new OktaAddOperation(MEMBERS, operation);
    return new PatchRequest(Collections.singletonList(addOperation));
  }

  private PatchRequest getInvalidOperation() {
    ArrayNode operation = mapper.createArrayNode();
    JsonNode x = mapper.createObjectNode();
    ((ObjectNode) x).put(VALUE, USER_ID_1);
    operation.add(x);
    AddOperation addOperation = new AddOperation(RANDOM_ID, operation);
    return new PatchRequest(Collections.singletonList(addOperation));
  }

  private PatchRequest getInvalidOktaOperation() {
    ArrayNode operation = mapper.createArrayNode();
    JsonNode x = mapper.createObjectNode();
    ((ObjectNode) x).put(VALUE, USER_ID_1);
    operation.add(x);
    OktaAddOperation addOperation = new OktaAddOperation(RANDOM_ID, operation);
    return new PatchRequest(Collections.singletonList(addOperation));
  }

  private PatchRequest getRemoveOperation() {
    ArrayNode operation = mapper.createArrayNode();
    JsonNode x = mapper.createObjectNode();
    ((ObjectNode) x).put(VALUE, USER_ID_1);
    operation.add(x);
    RemoveOperation removeOperation = new RemoveOperation(MEMBERS, operation);
    return new PatchRequest(Collections.singletonList(removeOperation));
  }

  private PatchRequest getOktaRemoveOperation() {
    ArrayNode operation = mapper.createArrayNode();
    JsonNode x = mapper.createObjectNode();
    ((ObjectNode) x).put(VALUE, USER_ID_1);
    operation.add(x);
    OktaRemoveOperation removeOperation = new OktaRemoveOperation(MEMBERS, operation);
    return new PatchRequest(Collections.singletonList(removeOperation));
  }
}
