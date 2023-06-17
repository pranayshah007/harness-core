/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service;

import static io.harness.rule.OwnerRule.NANDAN;

import static software.wings.utils.WingsTestConstants.ACCOUNT_ID;
import static software.wings.utils.WingsTestConstants.ACCOUNT_NAME;
import static software.wings.utils.WingsTestConstants.APP_ID;
import static software.wings.utils.WingsTestConstants.PASSWORD;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.failBecauseExceptionWasNotThrown;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.category.element.UnitTests;
import io.harness.data.structure.UUIDGenerator;
import io.harness.exception.GeneralException;
import io.harness.exception.InvalidAccessRequestException;
import io.harness.exception.InvalidArgumentsException;
import io.harness.rule.Owner;

import software.wings.WingsBaseTest;
import software.wings.beans.Account;
import software.wings.beans.User;
import software.wings.beans.security.AccessRequest;
import software.wings.beans.security.AccessRequestDTO;
import software.wings.beans.security.HarnessUserGroup;
import software.wings.dl.WingsPersistence;
import software.wings.service.intfc.AccessRequestService;
import software.wings.service.intfc.AccountService;
import software.wings.service.intfc.HarnessUserGroupService;
import software.wings.service.intfc.UserService;

import com.google.common.collect.Sets;
import com.google.inject.Inject;
import dev.morphia.query.UpdateOperations;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;

@OwnedBy(HarnessTeam.PL)
@TargetModule(HarnessModule._970_RBAC_CORE)
public class AccessRequestServiceTest extends WingsBaseTest {
  @Mock private AccountService accountService;
  @Mock private UserService userService;
  @Mock private HarnessUserGroupService harnessUserGroupService;
  @Inject private WingsPersistence wingsPersistence;
  @InjectMocks @Inject private AccessRequestService accessRequestService;

  private String accountId1 = UUIDGenerator.generateUuid();
  private String harnessUserGroupId = UUIDGenerator.generateUuid();
  private String userId1 = UUIDGenerator.generateUuid();
  private String userId2 = UUIDGenerator.generateUuid();
  private String userName1 = "USER1";
  private String userName2 = "USER2";
  private String USER_NOT_PART_OF_HARNESS_USER_GROUP = "userNotPartOfHarnessUserGroup";
  private String USER_NOT_PART_OF_HARNESS = "userNotPartOfHarness";
  private String userEmailId1 = "user1@harness.io";
  private String userEmailId2 = "user2@harness.io";
  private String emailIds = userEmailId1 + ", " + userEmailId2;

  private Account account1 = Account.Builder.anAccount().withAccountName(ACCOUNT_NAME).withUuid(ACCOUNT_ID).build();
  private User user1 =
      User.Builder.anUser().uuid(userId1).appId(APP_ID).email(userEmailId1).name(userName1).password(PASSWORD).build();
  private User user2 =
      User.Builder.anUser().uuid(userId2).appId(APP_ID).email(userEmailId2).name(userName2).password(PASSWORD).build();
  private User userNotPartOfHarnessUserGroup = User.Builder.anUser()
                                                   .uuid(USER_NOT_PART_OF_HARNESS_USER_GROUP)
                                                   .appId(USER_NOT_PART_OF_HARNESS_USER_GROUP)
                                                   .email(USER_NOT_PART_OF_HARNESS_USER_GROUP)
                                                   .name(USER_NOT_PART_OF_HARNESS_USER_GROUP)
                                                   .password(PASSWORD)
                                                   .build();

  private HarnessUserGroup harnessUserGroup =
      HarnessUserGroup.builder()
          .uuid(harnessUserGroupId)
          .accountIds(Sets.newHashSet(ACCOUNT_ID))
          .name("test")
          .description("test")
          .groupType(HarnessUserGroup.GroupType.RESTRICTED)
          .memberIds(Sets.newHashSet(Arrays.asList(user1.getUuid(), user2.getUuid())))
          .build();
  @Before
  public void setupMocks() {
    account1.setHarnessSupportAccessAllowed(false);
    when(accountService.get(anyString())).thenReturn(account1);
    when(userService.get(userId1)).thenReturn(user1);
    when(userService.get(USER_NOT_PART_OF_HARNESS_USER_GROUP)).thenReturn(userNotPartOfHarnessUserGroup);
    when(userService.getUserByEmail(userEmailId1)).thenReturn(user1);
    when(userService.getUserByEmail(userEmailId2)).thenReturn(user2);
    when(userService.getUserByEmail(USER_NOT_PART_OF_HARNESS_USER_GROUP)).thenReturn(userNotPartOfHarnessUserGroup);
    when(harnessUserGroupService.get(harnessUserGroup.getUuid())).thenReturn(harnessUserGroup);
    when(harnessUserGroupService.isHarnessSupportUser(userId1)).thenReturn(true);
    when(harnessUserGroupService.isHarnessSupportUser(userId2)).thenReturn(true);
  }

  @Test
  @Owner(developers = NANDAN)
  @Category(UnitTests.class)
  public void testAccessRequest_createGroupAccessRequest() {
    long accessStartAt = Instant.now().toEpochMilli();
    long accessEndAt = Instant.now().plus(24, ChronoUnit.HOURS).toEpochMilli();
    AccessRequestDTO accessRequestDTO = AccessRequestDTO.builder()
                                            .accountId(ACCOUNT_ID)
                                            .harnessUserGroupId(harnessUserGroupId)
                                            .accessStartAt(accessStartAt)
                                            .accessEndAt(accessEndAt)
                                            .build();
    AccessRequest accessRequest = accessRequestService.createAccessRequest(accessRequestDTO);

    assertThat(accessRequest).isNotNull();
    assertThat(accessRequest.getAccountId()).isEqualTo(ACCOUNT_ID);
    assertThat(accessRequest.getHarnessUserGroupId()).isEqualTo(harnessUserGroupId);
    assertThat(accessRequest.getAccessStartAt()).isEqualTo(accessStartAt);
    assertThat(accessRequest.getAccessEndAt()).isEqualTo(accessEndAt);
    assertThat(accessRequest.getAccessType()).isEqualTo(AccessRequest.AccessType.GROUP_ACCESS);
  }

  @Test
  @Owner(developers = NANDAN)
  @Category(UnitTests.class)
  public void testAccessRequest_createMemberAccessRequest() {
    long accessStartAt = Instant.now().toEpochMilli();
    long accessEndAt = Instant.now().plus(24, ChronoUnit.HOURS).toEpochMilli();
    AccessRequestDTO accessRequestDTO = AccessRequestDTO.builder()
                                            .accountId(ACCOUNT_ID)
                                            .emailIds(Sets.newHashSet(emailIds))
                                            .accessStartAt(accessStartAt)
                                            .accessEndAt(accessEndAt)
                                            .build();

    AccessRequest accessRequest = accessRequestService.createAccessRequest(accessRequestDTO);

    assertThat(accessRequest).isNotNull();
    assertThat(accessRequest.getAccountId()).isEqualTo(ACCOUNT_ID);
    assertThat(accessRequest.getMemberIds())
        .isEqualTo(Sets.newHashSet(Arrays.asList(user1.getUuid(), user2.getUuid())));
    assertThat(accessRequest.getAccessStartAt()).isEqualTo(accessStartAt);
    assertThat(accessRequest.getAccessEndAt()).isEqualTo(accessEndAt);
    assertThat(accessRequest.getAccessType()).isEqualTo(AccessRequest.AccessType.MEMBER_ACCESS);
  }

  @Test
  @Owner(developers = NANDAN)
  @Category(UnitTests.class)
  public void testAccessRequest_createMemberAccessRequesForNonHarnessSupportGroupUser() {
    long accessStartAt = Instant.now().toEpochMilli();
    long accessEndAt = Instant.now().plus(24, ChronoUnit.HOURS).toEpochMilli();
    AccessRequestDTO accessRequestDTO = AccessRequestDTO.builder()
                                            .accountId(ACCOUNT_ID)
                                            .emailIds(Sets.newHashSet(USER_NOT_PART_OF_HARNESS_USER_GROUP))
                                            .accessStartAt(accessStartAt)
                                            .accessEndAt(accessEndAt)
                                            .build();

    try {
      AccessRequest accessRequest = accessRequestService.createAccessRequest(accessRequestDTO);
      failBecauseExceptionWasNotThrown(InvalidAccessRequestException.class);
    } catch (Exception ex) {
      assertThat(ex).isInstanceOf(InvalidAccessRequestException.class);
    }
  }

  @Test
  @Owner(developers = NANDAN)
  @Category(UnitTests.class)
  public void testAccessRequest_getAccos() {
    AccessRequest accessRequest = AccessRequest.builder()
                                      .accountId(ACCOUNT_ID)
                                      .accessActive(true)
                                      .accessType(AccessRequest.AccessType.GROUP_ACCESS)
                                      .memberIds(Sets.newHashSet("user1", "user2"))
                                      .harnessUserGroupId("groupId")
                                      .build();
    wingsPersistence.save(accessRequest);
    accessRequest = AccessRequest.builder()
                        .accountId("account1")
                        .accessActive(true)
                        .accessType(AccessRequest.AccessType.MEMBER_ACCESS)
                        .memberIds(Sets.newHashSet("user1", "user2"))
                        .build();
    wingsPersistence.save(accessRequest);
    harnessUserGroup = HarnessUserGroup.builder().uuid("groupId").memberIds(Sets.newHashSet("user1")).build();
    wingsPersistence.save(harnessUserGroup);
    List<String> ids = accessRequestService.getAccountsHavingActiveAccessRequestForUser("user1");
    assertThat(ids.size()).isEqualTo(2);
  }

  @Test
  @Owner(developers = NANDAN)
  @Category(UnitTests.class)
  public void testAccessRequest_createMemberAccessRequesForNonHarnessUser() {
    long accessStartAt = Instant.now().toEpochMilli();
    long accessEndAt = Instant.now().plus(24, ChronoUnit.HOURS).toEpochMilli();
    AccessRequestDTO accessRequestDTO = AccessRequestDTO.builder()
                                            .accountId(ACCOUNT_ID)
                                            .emailIds(Sets.newHashSet(USER_NOT_PART_OF_HARNESS))
                                            .accessStartAt(accessStartAt)
                                            .accessEndAt(accessEndAt)
                                            .build();

    try {
      AccessRequest accessRequest = accessRequestService.createAccessRequest(accessRequestDTO);
      failBecauseExceptionWasNotThrown(InvalidAccessRequestException.class);
    } catch (Exception ex) {
      assertThat(ex).isInstanceOf(GeneralException.class);
      assertThat(ex.getMessage()).contains("No User present with emailId");
    }
  }

  @Test
  @Owner(developers = NANDAN)
  @Category(UnitTests.class)
  public void testAccessRequest_createMemberAccessRequestWithoutMembers() {
    long accessStartAt = Instant.now().toEpochMilli();
    long accessEndAt = Instant.now().plus(24, ChronoUnit.HOURS).toEpochMilli();
    AccessRequestDTO accessRequestDTO = AccessRequestDTO.builder()
                                            .accountId(ACCOUNT_ID)
                                            .emailIds(Sets.newHashSet())
                                            .accessStartAt(accessStartAt)
                                            .accessEndAt(accessEndAt)
                                            .build();

    try {
      AccessRequest accessRequest = accessRequestService.createAccessRequest(accessRequestDTO);
      failBecauseExceptionWasNotThrown(InvalidAccessRequestException.class);
    } catch (Exception ex) {
      assertThat(ex).isInstanceOf(InvalidAccessRequestException.class);
      assertThat(ex.getMessage()).contains("without members specified");
    }
  }

  @Test
  @Owner(developers = NANDAN)
  @Category(UnitTests.class)
  public void testAccessRequest_checkForValidRestrictedAccount() {
    long accessStartAt = Instant.now().toEpochMilli();
    long accessEndAt = Instant.now().minus(24, ChronoUnit.HOURS).toEpochMilli();
    AccessRequestDTO accessRequestDTO = AccessRequestDTO.builder()
                                            .accountId(ACCOUNT_ID)
                                            .emailIds(Sets.newHashSet(emailIds))
                                            .accessStartAt(accessStartAt)
                                            .accessEndAt(accessEndAt)
                                            .build();
    account1.setHarnessSupportAccessAllowed(true);
    assertThatThrownBy(() -> accessRequestService.createAccessRequest(accessRequestDTO))
        .isInstanceOf(InvalidAccessRequestException.class)
        .hasMessage(String.format(
            "accountId: %s is not a restricted account and doesn't not require Access Request", account1.getUuid()));
  }

  @Test
  @Owner(developers = NANDAN)
  @Category(UnitTests.class)
  public void testAccessRequest_checkForValidAccessTime() {
    long accessStartAt = Instant.now().toEpochMilli();
    long accessEndAt = Instant.now().minus(1, ChronoUnit.SECONDS).toEpochMilli();
    AccessRequestDTO accessRequestDTO = AccessRequestDTO.builder()
                                            .accountId(ACCOUNT_ID)
                                            .emailIds(Sets.newHashSet(emailIds))
                                            .accessStartAt(accessStartAt)
                                            .accessEndAt(accessEndAt)
                                            .build();
    assertThatThrownBy(() -> accessRequestService.createAccessRequest(accessRequestDTO))
        .isInstanceOf(InvalidArgumentsException.class)
        .hasMessage("Access Start Time needs to be before Access End Time");
  }

  @Test
  @Owner(developers = NANDAN)
  @Category(UnitTests.class)
  public void testAccessRequest_delete() {
    long accessStartAt = Instant.now().toEpochMilli();
    long accessEndAt = Instant.now().plus(24, ChronoUnit.HOURS).toEpochMilli();
    AccessRequestDTO accessRequestDTO = AccessRequestDTO.builder()
                                            .accountId(ACCOUNT_ID)
                                            .emailIds(Sets.newHashSet(emailIds))
                                            .accessStartAt(accessStartAt)
                                            .accessEndAt(accessEndAt)
                                            .build();
    AccessRequest accessRequest = accessRequestService.createAccessRequest(accessRequestDTO);

    boolean deleted = accessRequestService.delete(accessRequest.getUuid());
    assertThat(deleted).isTrue();

    AccessRequest accessRequest1 = accessRequestService.get(accessRequest.getUuid());

    assertThat(accessRequest1).isNull();
  }

  @Test
  @Owner(developers = NANDAN)
  @Category(UnitTests.class)
  public void testAccessRequest_getAccessRequest() {
    long accessStartAt = Instant.now().toEpochMilli();
    long accessEndAt = Instant.now().plus(24, ChronoUnit.HOURS).toEpochMilli();
    AccessRequestDTO accessRequestDTO = AccessRequestDTO.builder()
                                            .accountId(ACCOUNT_ID)
                                            .emailIds(Sets.newHashSet(emailIds))
                                            .accessStartAt(accessStartAt)
                                            .accessEndAt(accessEndAt)
                                            .build();
    AccessRequest accessRequest = accessRequestService.createAccessRequest(accessRequestDTO);

    AccessRequest savedAccessRequest = accessRequestService.get(accessRequest.getUuid());

    assertThat(savedAccessRequest).isNotNull();
    assertThat(savedAccessRequest.getUuid()).isEqualTo(accessRequest.getUuid());
  }

  @Test
  @Owner(developers = NANDAN)
  @Category(UnitTests.class)
  public void testAccessRequest_getActiveAccessRequestForHarnessUserGroup() {
    long accessStartAt = Instant.now().toEpochMilli();
    long accessEndAt = Instant.now().plus(24, ChronoUnit.HOURS).toEpochMilli();
    AccessRequestDTO accessRequestDTO1 = AccessRequestDTO.builder()
                                             .accountId(ACCOUNT_ID)
                                             .harnessUserGroupId(harnessUserGroupId)
                                             .accessStartAt(accessStartAt)
                                             .accessEndAt(accessEndAt)
                                             .build();
    AccessRequest accessRequest1 = accessRequestService.createAccessRequest(accessRequestDTO1);

    accessStartAt = Instant.now().toEpochMilli();
    accessEndAt = Instant.now().plus(48, ChronoUnit.HOURS).toEpochMilli();
    AccessRequestDTO accessRequestDTO2 = AccessRequestDTO.builder()
                                             .accountId(ACCOUNT_ID)
                                             .harnessUserGroupId(harnessUserGroupId)
                                             .accessStartAt(accessStartAt)
                                             .accessEndAt(accessEndAt)
                                             .build();

    AccessRequest accessRequest2 = accessRequestService.createAccessRequest(accessRequestDTO2);

    List<AccessRequest> accessRequestList = accessRequestService.getActiveAccessRequest(harnessUserGroupId);

    assertThat(accessRequestList.size()).isEqualTo(2);
    assertThat(accessRequestList.get(0).getUuid()).isEqualTo(accessRequest1.getUuid());
    assertThat(accessRequestList.get(1).getUuid()).isEqualTo(accessRequest2.getUuid());
  }

  @Test
  @Owner(developers = NANDAN)
  @Category(UnitTests.class)
  public void testAccessRequest_getActiveAccessRequestForAccount() {
    long accessStartAt = Instant.now().toEpochMilli();
    long accessEndAt = Instant.now().plus(24, ChronoUnit.HOURS).toEpochMilli();
    AccessRequestDTO accessRequestDTO = AccessRequestDTO.builder()
                                            .accountId(ACCOUNT_ID)
                                            .harnessUserGroupId(harnessUserGroupId)
                                            .accessStartAt(accessStartAt)
                                            .accessEndAt(accessEndAt)
                                            .build();
    AccessRequest accessRequest = accessRequestService.createAccessRequest(accessRequestDTO);

    List<AccessRequest> accessRequestList = accessRequestService.getActiveAccessRequestForAccount(ACCOUNT_ID);

    assertThat(accessRequestList.size()).isEqualTo(1);
    assertThat(accessRequestList.get(0).getUuid()).isEqualTo(accessRequest.getUuid());
  }

  @Test
  @Owner(developers = NANDAN)
  @Category(UnitTests.class)
  public void testAccessRequest_getAllAccessRequestForAccount() {
    long accessReq1StartAt = Instant.now().toEpochMilli();
    long accessReq1EndAt = Instant.now().plus(24, ChronoUnit.HOURS).toEpochMilli();
    AccessRequestDTO accessReq1DTO = AccessRequestDTO.builder()
                                         .accountId(ACCOUNT_ID)
                                         .harnessUserGroupId(harnessUserGroupId)
                                         .accessStartAt(accessReq1StartAt)
                                         .accessEndAt(accessReq1EndAt)
                                         .build();
    AccessRequest accessReq1 = accessRequestService.createAccessRequest(accessReq1DTO);

    long accessReq2StartAt = Instant.now().minus(2, ChronoUnit.HOURS).toEpochMilli();
    long accessReq2EndAt = Instant.now().minus(1, ChronoUnit.HOURS).toEpochMilli();
    AccessRequestDTO accessReq2DTO = AccessRequestDTO.builder()
                                         .accountId(ACCOUNT_ID)
                                         .harnessUserGroupId(harnessUserGroupId)
                                         .accessStartAt(accessReq2StartAt)
                                         .accessEndAt(accessReq2EndAt)
                                         .build();
    AccessRequest accessReq2 = accessRequestService.createAccessRequest(accessReq2DTO);
    accessReq2.setAccessActive(false);

    UpdateOperations<AccessRequest> updateOperations = wingsPersistence.createUpdateOperations(AccessRequest.class);
    updateOperations.set("accessActive", false);
    wingsPersistence.update(accessReq2, updateOperations);

    List<AccessRequest> accessRequestList = accessRequestService.getAllAccessRequestForAccount(ACCOUNT_ID);

    assertThat(accessRequestList.size()).isEqualTo(2);
    assertThat(accessRequestList.get(0).getUuid()).isEqualTo(accessReq1.getUuid());
    assertThat(accessRequestList.get(0).isAccessActive()).isTrue();
    assertThat(accessRequestList.get(1).getUuid()).isEqualTo(accessReq2.getUuid());
    assertThat(accessRequestList.get(1).isAccessActive()).isFalse();
  }

  @Test
  @Owner(developers = NANDAN)
  @Category(UnitTests.class)
  public void testAccessRequest_getActiveAccessRequestForAccountAndUser() {
    long accessStartAt = Instant.now().toEpochMilli();
    long accessEndAt = Instant.now().plus(24, ChronoUnit.HOURS).toEpochMilli();
    AccessRequestDTO accessRequestDTO = AccessRequestDTO.builder()
                                            .accountId(ACCOUNT_ID)
                                            .emailIds(Sets.newHashSet(emailIds))
                                            .accessStartAt(accessStartAt)
                                            .accessEndAt(accessEndAt)
                                            .build();
    AccessRequest accessRequest = accessRequestService.createAccessRequest(accessRequestDTO);

    List<AccessRequest> accessRequestList =
        accessRequestService.getActiveAccessRequestForAccountAndUser(ACCOUNT_ID, user1.getUuid());
    assertThat(accessRequestList.size()).isEqualTo(1);
    assertThat(accessRequestList.get(0).getUuid()).isEqualTo(accessRequest.getUuid());
  }
}
