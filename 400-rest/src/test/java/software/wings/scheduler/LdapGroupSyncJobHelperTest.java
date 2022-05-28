/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.scheduler;

import static io.harness.rule.OwnerRule.UJJAWAL;

import static software.wings.scheduler.LdapGroupSyncJob.MAX_LDAP_SYNC_TIMEOUT;
import static software.wings.scheduler.LdapGroupSyncJob.MIN_LDAP_SYNC_TIMEOUT;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.data.structure.UUIDGenerator;
import io.harness.ff.FeatureFlagService;
import io.harness.persistence.HPersistence;
import io.harness.rule.Owner;

import software.wings.beans.Account;
import software.wings.beans.User;
import software.wings.beans.security.UserGroup;
import software.wings.beans.sso.LdapGroupResponse;
import software.wings.beans.sso.LdapSettings;
import software.wings.beans.sso.LdapUserResponse;
import software.wings.delegatetasks.DelegateProxyFactory;
import software.wings.service.intfc.SSOSettingService;
import software.wings.service.intfc.UserGroupService;
import software.wings.service.intfc.UserService;
import software.wings.service.intfc.ldap.LdapDelegateService;

import com.google.inject.Inject;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class LdapGroupSyncJobHelperTest extends CategoryTest {
  @Inject HPersistence hPersistence;
  @InjectMocks private LdapGroupSyncJobHelper ldapGroupSyncJobHelper;
  @Mock private LdapDelegateService ldapDelegateService;
  @Mock private DelegateProxyFactory delegateProxyFactory;
  @Mock private SSOSettingService ssoSettingService;
  @Mock private UserService userService;
  @Mock private UserGroupService userGroupService;

  @Mock private FeatureFlagService featureFlagService;

  @Before
  public void setup() {
    MockitoAnnotations.initMocks(this);
    ldapGroupSyncJobHelper = spy(ldapGroupSyncJobHelper);
    when(featureFlagService.isEnabled(any(), any())).thenReturn(false);
  }

  @Test
  @Owner(developers = UJJAWAL)
  @Category(UnitTests.class)
  public void shouldSyncUserGroup() {
    UserGroup userGroup = mock(UserGroup.class);
    Account account = new Account();
    String ssoId = UUIDGenerator.generateUuid();
    account.setUuid(UUIDGenerator.generateUuid());
    when(userGroup.getName()).thenReturn("userGroupName");
    when(userGroup.getAccountId()).thenReturn(UUIDGenerator.generateUuid());
    userGroup.setAccountId(UUIDGenerator.generateUuid());
    doReturn(LdapGroupResponse.builder().selectable(true).build())
        .when(ldapGroupSyncJobHelper)
        .fetchGroupDetails(any(), any(), any());
    doReturn(userGroup).when(ldapGroupSyncJobHelper).syncUserGroupMetadata(any(), any());
    doReturn(true).when(ldapGroupSyncJobHelper).validateUserGroupStates(any());
    ldapGroupSyncJobHelper.syncUserGroups(
        account.getUuid(), mock(LdapSettings.class), Collections.singletonList(userGroup), ssoId);
    verify(ssoSettingService, times(1)).closeSyncFailureAlertIfOpen(account.getUuid(), ssoId);
  }

  @Test
  @Owner(developers = UJJAWAL)
  @Category(UnitTests.class)
  public void testLdapSyncTimeout() {
    long NEGATIVE_TIME = -10000;
    long HALF_MINUTE = 30 * 1000;
    long ONE_MINUTE = 60 * 1000;
    long TWO_MINUTE = 2 * 60 * 1000;
    long THREE_MINUTE = 3 * 60 * 1000;
    long FOUR_MINUTE = 3 * 60 * 1000;
    long VERY_LARGE_TIME = 10000 * 60 * 1000;

    // less than 0 minute should return MIN_LDAP_SYNC_TIMEOUT
    long ldapSyncTimeoutTest = ldapGroupSyncJobHelper.getLdapSyncTimeout(NEGATIVE_TIME);
    assertThat(MIN_LDAP_SYNC_TIMEOUT).isEqualTo(ldapSyncTimeoutTest);

    // less than 1 minute should return MIN_LDAP_SYNC_TIMEOUT
    ldapSyncTimeoutTest = ldapGroupSyncJobHelper.getLdapSyncTimeout(HALF_MINUTE);
    assertThat(MIN_LDAP_SYNC_TIMEOUT).isEqualTo(ldapSyncTimeoutTest);

    // 1 minute should return 1 minute
    ldapSyncTimeoutTest = ldapGroupSyncJobHelper.getLdapSyncTimeout(ONE_MINUTE);
    assertThat(ONE_MINUTE).isEqualTo(ldapSyncTimeoutTest);

    // 2 mins should return 2 minutes as is
    ldapSyncTimeoutTest = ldapGroupSyncJobHelper.getLdapSyncTimeout(TWO_MINUTE);
    assertThat(TWO_MINUTE).isEqualTo(ldapSyncTimeoutTest);

    // 3 minutes should return 3 minute
    ldapSyncTimeoutTest = ldapGroupSyncJobHelper.getLdapSyncTimeout(THREE_MINUTE);
    assertThat(THREE_MINUTE).isEqualTo(ldapSyncTimeoutTest);

    // 4 minutes should return MAX_LDAP_SYNC_TIMEOUT
    ldapSyncTimeoutTest = ldapGroupSyncJobHelper.getLdapSyncTimeout(FOUR_MINUTE);
    assertThat(MAX_LDAP_SYNC_TIMEOUT).isEqualTo(ldapSyncTimeoutTest);

    // Anything greater than MAX_LDAP_SYNC_TIMEOUT should return MAX_LDAP_SYNC_TIMEOUT
    ldapSyncTimeoutTest = ldapGroupSyncJobHelper.getLdapSyncTimeout(VERY_LARGE_TIME);
    assertThat(MAX_LDAP_SYNC_TIMEOUT).isEqualTo(ldapSyncTimeoutTest);
  }

  @Test
  @Owner(developers = UJJAWAL)
  @Category(UnitTests.class)
  public void shouldSyncUserGroupWithNullInputs() {
    UserGroup userGroup = mock(UserGroup.class);
    Account account = new Account();
    account.setUuid(UUIDGenerator.generateUuid());
    when(userGroup.getName()).thenReturn("userGroupName");
    when(userGroup.getAccountId()).thenReturn(UUIDGenerator.generateUuid());
    userGroup.setAccountId(UUIDGenerator.generateUuid());
    doReturn(LdapGroupResponse.builder().selectable(true).build())
        .when(ldapGroupSyncJobHelper)
        .fetchGroupDetails(any(), any(), any());
    doReturn(userGroup).when(ldapGroupSyncJobHelper).syncUserGroupMetadata(any(), any());
    doReturn(true).when(ldapGroupSyncJobHelper).validateUserGroupStates(any());
    boolean exceptionThrown = false;
    try {
      ldapGroupSyncJobHelper.syncUserGroupMembers(account.getUuid(), null, null);
    } catch (Exception e) {
      exceptionThrown = true;
    }
    assertThat(exceptionThrown).isEqualTo(false);
    verify(userService, times(0)).addUserToUserGroups(any(), any(), any(), eq(true), eq(true));
  }

  @Test
  @Owner(developers = UJJAWAL)
  @Category(UnitTests.class)
  public void shouldSyncUserGroupWithEmailNullInAddedGroupMembersInputs() {
    UserGroup userGroup = UserGroup.builder().name("userGroupName").accountId("ACCOUNT_ID").build();
    Account account = new Account();
    account.setUuid("ACCOUNT_ID");
    doReturn(LdapGroupResponse.builder().selectable(true).build())
        .when(ldapGroupSyncJobHelper)
        .fetchGroupDetails(any(), any(), any());
    doReturn(userGroup).when(ldapGroupSyncJobHelper).syncUserGroupMetadata(any(), any());
    doReturn(true).when(ldapGroupSyncJobHelper).validateUserGroupStates(any());
    boolean exceptionThrown = false;

    Map<LdapUserResponse, Set<UserGroup>> addedGroupMembers = new HashMap<>();
    LdapUserResponse ldapUserResponse =
        LdapUserResponse.builder().name("ldap").email(null).userId(null).dn("dn").build();

    Set<UserGroup> userGroupSet = new HashSet<>();
    userGroupSet.add(userGroup);
    addedGroupMembers.put(ldapUserResponse, userGroupSet);

    try {
      ldapGroupSyncJobHelper.syncUserGroupMembers(account.getUuid(), null, addedGroupMembers);
    } catch (Exception e) {
      exceptionThrown = true;
    }
    assertThat(exceptionThrown).isEqualTo(false);
    verify(userService, times(0)).addUserToUserGroups(any(), any(), any(), eq(true), eq(true));
  }

  @Test
  @Owner(developers = UJJAWAL)
  @Category(UnitTests.class)
  public void shouldSyncUserGroupWithEmailNullInAddedGroupMembersInputs2() {
    UserGroup userGroup = UserGroup.builder().name("userGroupName").accountId("ACCOUNT_ID").build();
    Account account = new Account();
    account.setUuid("ACCOUNT_ID");
    doReturn(LdapGroupResponse.builder().selectable(true).build())
        .when(ldapGroupSyncJobHelper)
        .fetchGroupDetails(any(), any(), any());
    doReturn(userGroup).when(ldapGroupSyncJobHelper).syncUserGroupMetadata(any(), any());
    doReturn(true).when(ldapGroupSyncJobHelper).validateUserGroupStates(any());
    boolean exceptionThrown = false;

    Map<LdapUserResponse, Set<UserGroup>> addedGroupMembers = new HashMap<>();
    LdapUserResponse ldapUserResponse =
        LdapUserResponse.builder().name("ldap").email(null).userId("userId").dn("dn").build();

    Set<UserGroup> userGroupSet = new HashSet<>();
    userGroupSet.add(userGroup);
    addedGroupMembers.put(ldapUserResponse, userGroupSet);

    when(featureFlagService.isEnabled(any(), any())).thenReturn(true);
    when(userService.getUserByUserId(any(), any())).thenReturn(null);

    try {
      ldapGroupSyncJobHelper.syncUserGroupMembers(account.getUuid(), null, addedGroupMembers);
    } catch (Exception e) {
      exceptionThrown = true;
    }
    assertThat(exceptionThrown).isEqualTo(false);
    verify(userService).inviteUser(any(), eq(false), eq(true));
    verify(userService, times(0)).addUserToUserGroups(any(), any(), any(), eq(true), eq(true));
  }

  @Test
  @Owner(developers = UJJAWAL)
  @Category(UnitTests.class)
  public void shouldSyncUserGroupWithEmailNullInAddedGroupMembersInputs4() {
    UserGroup userGroup = UserGroup.builder().name("userGroupName").accountId("ACCOUNT_ID").build();
    Account account = new Account();
    account.setUuid("ACCOUNT_ID");
    doReturn(LdapGroupResponse.builder().selectable(true).build())
        .when(ldapGroupSyncJobHelper)
        .fetchGroupDetails(any(), any(), any());
    doReturn(userGroup).when(ldapGroupSyncJobHelper).syncUserGroupMetadata(any(), any());
    doReturn(true).when(ldapGroupSyncJobHelper).validateUserGroupStates(any());
    boolean exceptionThrown = false;

    Map<LdapUserResponse, Set<UserGroup>> addedGroupMembers = new HashMap<>();
    LdapUserResponse ldapUserResponse =
        LdapUserResponse.builder().name("ldap").email(null).userId("userId").dn("dn").build();

    Set<UserGroup> userGroupSet = new HashSet<>();
    userGroupSet.add(userGroup);
    addedGroupMembers.put(ldapUserResponse, userGroupSet);

    User user = new User();
    user.setEmail("test@harness.io");
    user.setUuid("user_uuid");

    when(featureFlagService.isEnabled(any(), any())).thenReturn(true);
    when(userService.getUserByUserId(any(), any())).thenReturn(user);
    when(userService.isUserAssignedToAccount(any(), any())).thenReturn(false);

    try {
      ldapGroupSyncJobHelper.syncUserGroupMembers(account.getUuid(), null, addedGroupMembers);
    } catch (Exception e) {
      exceptionThrown = true;
    }
    assertThat(exceptionThrown).isEqualTo(false);
    verify(userService).inviteUser(any(), eq(false), eq(true));
    verify(userService, times(0)).addUserToUserGroups(any(), any(), any(), eq(true), eq(true));
  }

  @Test
  @Owner(developers = UJJAWAL)
  @Category(UnitTests.class)
  public void shouldSyncUserGroupWithEmailNullInAddedGroupMembersInputs3() {
    UserGroup userGroup = UserGroup.builder().name("userGroupName").accountId("ACCOUNT_ID").build();
    Account account = new Account();
    account.setUuid("ACCOUNT_ID");
    doReturn(LdapGroupResponse.builder().selectable(true).build())
        .when(ldapGroupSyncJobHelper)
        .fetchGroupDetails(any(), any(), any());
    doReturn(userGroup).when(ldapGroupSyncJobHelper).syncUserGroupMetadata(any(), any());
    doReturn(true).when(ldapGroupSyncJobHelper).validateUserGroupStates(any());
    boolean exceptionThrown = false;

    Map<LdapUserResponse, Set<UserGroup>> addedGroupMembers = new HashMap<>();
    LdapUserResponse ldapUserResponse =
        LdapUserResponse.builder().name("ldap").email(null).userId("userId").dn("dn").build();

    Set<UserGroup> userGroupSet = new HashSet<>();
    userGroupSet.add(userGroup);
    addedGroupMembers.put(ldapUserResponse, userGroupSet);

    User user = new User();
    user.setEmail("test@harness.io");
    user.setUuid("user_uuid");

    when(featureFlagService.isEnabled(any(), any())).thenReturn(true);
    when(userService.getUserByUserId(any(), any())).thenReturn(user);
    when(userService.isUserAssignedToAccount(any(), any())).thenReturn(true);

    try {
      ldapGroupSyncJobHelper.syncUserGroupMembers(account.getUuid(), null, addedGroupMembers);
    } catch (Exception e) {
      exceptionThrown = true;
    }
    assertThat(exceptionThrown).isEqualTo(false);
    verify(userService, times(0)).inviteUser(any(), eq(false), eq(true));
    verify(userService, times(1)).addUserToUserGroups(any(), any(), any(), eq(true), eq(true));
  }

  @Test
  @Owner(developers = UJJAWAL)
  @Category(UnitTests.class)
  public void shouldSyncUserGroupWithEmailNullInAddedGroupMembersInputs5() {
    UserGroup userGroup = UserGroup.builder().name("userGroupName").accountId("ACCOUNT_ID").build();
    Account account = new Account();
    account.setUuid("ACCOUNT_ID");
    doReturn(LdapGroupResponse.builder().selectable(true).build())
        .when(ldapGroupSyncJobHelper)
        .fetchGroupDetails(any(), any(), any());
    doReturn(userGroup).when(ldapGroupSyncJobHelper).syncUserGroupMetadata(any(), any());
    doReturn(true).when(ldapGroupSyncJobHelper).validateUserGroupStates(any());
    boolean exceptionThrown = false;

    Map<LdapUserResponse, Set<UserGroup>> addedGroupMembers = new HashMap<>();
    LdapUserResponse ldapUserResponse1 =
        LdapUserResponse.builder().name("ldap").email("email@harness.io").userId("userId").dn("dn").build();

    LdapUserResponse ldapUserResponse2 =
        LdapUserResponse.builder().name("ldap2").email("mail@hanrs.com").userId("userId2").dn("dn2").build();

    Set<UserGroup> userGroupSet = new HashSet<>();
    userGroupSet.add(userGroup);
    addedGroupMembers.put(ldapUserResponse1, userGroupSet);
    addedGroupMembers.put(ldapUserResponse2, userGroupSet);

    User user = new User();
    user.setEmail("test@harness.io");
    user.setUuid("user_uuid");

    when(featureFlagService.isEnabled(any(), any())).thenReturn(true);
    when(userService.getUserByUserId(any(), any())).thenReturn(user);
    when(userService.isUserAssignedToAccount(any(), any())).thenReturn(true);

    try {
      ldapGroupSyncJobHelper.syncUserGroupMembers(account.getUuid(), null, addedGroupMembers);
    } catch (Exception e) {
      exceptionThrown = true;
    }
    assertThat(exceptionThrown).isEqualTo(false);
    verify(userService, times(0)).inviteUser(any(), eq(false), eq(true));
    verify(userService, times(2)).addUserToUserGroups(any(), any(), any(), eq(true), eq(true));
  }

  @Test
  @Owner(developers = UJJAWAL)
  @Category(UnitTests.class)
  public void shouldSyncUserGroupWithEmailNullInAddedGroupMembersInputs6() {
    UserGroup userGroup = UserGroup.builder().name("userGroupName").accountId("ACCOUNT_ID").build();
    Account account = new Account();
    account.setUuid("ACCOUNT_ID");
    doReturn(LdapGroupResponse.builder().selectable(true).build())
        .when(ldapGroupSyncJobHelper)
        .fetchGroupDetails(any(), any(), any());
    doReturn(userGroup).when(ldapGroupSyncJobHelper).syncUserGroupMetadata(any(), any());
    doReturn(true).when(ldapGroupSyncJobHelper).validateUserGroupStates(any());
    boolean exceptionThrown = false;

    Map<LdapUserResponse, Set<UserGroup>> addedGroupMembers = new HashMap<>();
    LdapUserResponse ldapUserResponse1 =
        LdapUserResponse.builder().name("ldap").email(null).userId("userId").dn("dn").build();

    LdapUserResponse ldapUserResponse2 =
        LdapUserResponse.builder().name("ldap2").email(null).userId("userId2").dn("dn2").build();

    Set<UserGroup> userGroupSet = new HashSet<>();
    userGroupSet.add(userGroup);
    addedGroupMembers.put(ldapUserResponse1, userGroupSet);
    addedGroupMembers.put(ldapUserResponse2, userGroupSet);

    User user = new User();
    user.setEmail("test@harness.io");
    user.setUuid("user_uuid");

    when(featureFlagService.isEnabled(any(), any())).thenReturn(true);
    when(userService.getUserByUserId(any(), any())).thenReturn(null);
    when(userService.isUserAssignedToAccount(any(), any())).thenReturn(true);

    try {
      ldapGroupSyncJobHelper.syncUserGroupMembers(account.getUuid(), null, addedGroupMembers);
    } catch (Exception e) {
      exceptionThrown = true;
    }

    assertThat(exceptionThrown).isEqualTo(false);
    verify(userService, times(2)).inviteUser(any(), eq(false), eq(true));
    verify(userService, times(0)).addUserToUserGroups(any(), any(), any(), eq(true), eq(true));
  }
}
