/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.scheduler;

import static io.harness.beans.FeatureName.PL_LDAP_PARALLEL_GROUP_SYNC;
import static io.harness.ng.core.common.beans.Generation.CG;
import static io.harness.rule.OwnerRule.SHASHANK;
import static io.harness.rule.OwnerRule.UJJAWAL;

import static software.wings.integration.SSO.LDAP.LdapTestHelper.buildLdapSettings;
import static software.wings.scheduler.LdapGroupSyncJob.MAX_LDAP_SYNC_TIMEOUT;
import static software.wings.scheduler.LdapGroupSyncJob.MIN_LDAP_SYNC_TIMEOUT;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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
import software.wings.beans.sso.LdapTestResponse;
import software.wings.beans.sso.LdapUserResponse;
import software.wings.delegatetasks.DelegateProxyFactory;
import software.wings.features.api.PremiumFeature;
import software.wings.service.impl.SSOServiceImpl;
import software.wings.service.intfc.SSOSettingService;
import software.wings.service.intfc.UserGroupService;
import software.wings.service.intfc.UserService;
import software.wings.service.intfc.ldap.LdapDelegateService;
import software.wings.service.intfc.security.SecretManager;

import com.google.inject.Inject;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class LdapGroupSyncJobHelperTest extends CategoryTest {
  public static final String ACCOUNT_ID = UUIDGenerator.generateUuid();
  public static final String SSO_ID = UUIDGenerator.generateUuid();
  @Inject HPersistence hPersistence;
  @InjectMocks private LdapGroupSyncJobHelper ldapGroupSyncJobHelper;
  @Mock private LdapDelegateService ldapDelegateService;
  @Mock private DelegateProxyFactory delegateProxyFactory;
  @Mock private SSOSettingService ssoSettingService;
  @Mock private SSOServiceImpl ssoService;
  @Mock private UserService userService;
  @Mock private UserGroupService userGroupService;
  @Mock private FeatureFlagService featureFlagService;
  @Mock private PremiumFeature premiumFeature;
  private LdapSettings ldapSettings;
  @Mock private SecretManager secretManager;

  @Before
  public void setup() {
    MockitoAnnotations.initMocks(this);
    ldapGroupSyncJobHelper = spy(ldapGroupSyncJobHelper);
    when(featureFlagService.isEnabled(any(), any())).thenReturn(false);
    when(premiumFeature.isAvailableForAccount(any())).thenReturn(true);
    doReturn(LdapTestResponse.builder().status(LdapTestResponse.Status.SUCCESS).build())
        .when(ssoService)
        .validateLdapConnectionSettings(any(), any());
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
  @Owner(developers = SHASHANK)
  @Category(UnitTests.class)
  public void shouldSyncUserGroupParallel() {
    UserGroup userGroup = mock(UserGroup.class);
    Account account = new Account();
    String ssoId = UUIDGenerator.generateUuid();
    account.setUuid(ACCOUNT_ID);
    when(userGroup.getName()).thenReturn("userGroupName");
    when(userGroup.getAccountId()).thenReturn(UUIDGenerator.generateUuid());
    userGroup.setAccountId(ACCOUNT_ID);
    doReturn(LdapGroupResponse.builder().selectable(true).build())
        .when(ldapGroupSyncJobHelper)
        .fetchGroupDetails(any(), any(), any());
    doReturn(userGroup).when(ldapGroupSyncJobHelper).syncUserGroupMetadata(any(), any());
    doReturn(true).when(ldapGroupSyncJobHelper).validateUserGroupStates(any());

    ldapGroupSyncJobHelper.syncUserGroupsParallel(
        account.getUuid(), mock(LdapSettings.class), Collections.singletonList(userGroup), ssoId);
    verify(ssoSettingService, times(1)).closeSyncFailureAlertIfOpen(account.getUuid(), ssoId);
  }

  @Test
  @Owner(developers = SHASHANK)
  @Category(UnitTests.class)
  public void shouldCallSyncUserGroupParallelWhenFFIsON() {
    UserGroup userGroup = mock(UserGroup.class);
    Account account = new Account();
    String ssoId = SSO_ID;
    account.setUuid(ACCOUNT_ID);
    when(userGroup.getName()).thenReturn("userGroupName");
    when(userGroup.getAccountId()).thenReturn(UUIDGenerator.generateUuid());
    userGroup.setAccountId(ACCOUNT_ID);
    ldapSettings = buildLdapSettings();
    ldapSettings.setAccountId(ACCOUNT_ID);
    ldapSettings.setUuid(SSO_ID);
    List<UserGroup> userGroups = Collections.singletonList(userGroup);

    doReturn(LdapGroupResponse.builder().selectable(true).build())
        .when(ldapGroupSyncJobHelper)
        .fetchGroupDetails(any(), any(), any());
    doReturn(userGroup).when(ldapGroupSyncJobHelper).syncUserGroupMetadata(any(), any());
    doReturn(true).when(ldapGroupSyncJobHelper).validateUserGroupStates(any());
    doReturn(userGroups).when(userGroupService).getUserGroupsBySsoId(any(), any());
    doReturn(Optional.empty()).when(secretManager).getEncryptedDataDetails(any(), any(), any(), any());

    when(featureFlagService.isEnabled(PL_LDAP_PARALLEL_GROUP_SYNC, ACCOUNT_ID)).thenReturn(true);
    ldapGroupSyncJobHelper.syncJob(ldapSettings);
    verify(ldapGroupSyncJobHelper, times(1)).syncUserGroupsParallel(ACCOUNT_ID, ldapSettings, userGroups, SSO_ID);
  }

  @Test
  @Owner(developers = SHASHANK)
  @Category(UnitTests.class)
  public void shouldNotCallSyncUserGroupParallelWhenFFIsOFF() {
    UserGroup userGroup = mock(UserGroup.class);
    Account account = new Account();
    String ssoId = SSO_ID;
    account.setUuid(ACCOUNT_ID);
    when(userGroup.getName()).thenReturn("userGroupName");
    when(userGroup.getAccountId()).thenReturn(UUIDGenerator.generateUuid());
    userGroup.setAccountId(ACCOUNT_ID);
    ldapSettings = buildLdapSettings();
    ldapSettings.setAccountId(ACCOUNT_ID);
    ldapSettings.setUuid(SSO_ID);
    List<UserGroup> userGroups = Collections.singletonList(userGroup);

    doReturn(LdapGroupResponse.builder().selectable(true).build())
        .when(ldapGroupSyncJobHelper)
        .fetchGroupDetails(any(), any(), any());
    doReturn(userGroup).when(ldapGroupSyncJobHelper).syncUserGroupMetadata(any(), any());
    doReturn(true).when(ldapGroupSyncJobHelper).validateUserGroupStates(any());
    doReturn(userGroups).when(userGroupService).getUserGroupsBySsoId(any(), any());
    doReturn(Optional.empty()).when(secretManager).getEncryptedDataDetails(any(), any(), any(), any());

    when(featureFlagService.isEnabled(PL_LDAP_PARALLEL_GROUP_SYNC, ACCOUNT_ID)).thenReturn(false);
    ldapGroupSyncJobHelper.syncJob(ldapSettings);
    verify(ldapGroupSyncJobHelper, times(1)).syncUserGroups(ACCOUNT_ID, ldapSettings, userGroups, SSO_ID);
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
    verify(userService, times(1)).addUserToUserGroups(any(), any(), any(), eq(true), eq(true));
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
    verify(userService, times(1)).addUserToUserGroups(any(), any(), any(), eq(true), eq(true));
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
    verify(userService, times(1)).addUserToUserGroups(any(), any(), any(), eq(true), eq(true));
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
    when(userService.isUserAssignedToAccountInGeneration(user, "ACCOUNT_ID", CG)).thenReturn(true);

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
    when(userService.isUserAssignedToAccountInGeneration(user, "ACCOUNT_ID", CG)).thenReturn(true);

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
    verify(userService, times(2)).addUserToUserGroups(any(), any(), any(), eq(true), eq(true));
  }
}
