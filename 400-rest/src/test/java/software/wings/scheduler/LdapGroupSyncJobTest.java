/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.scheduler;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.rule.OwnerRule.MOHIT;
import static io.harness.rule.OwnerRule.PIYUSH;

import static software.wings.beans.Account.Builder.anAccount;
import static software.wings.beans.User.Builder.anUser;
import static software.wings.scheduler.LdapGroupSyncJob.MAX_LDAP_SYNC_TIMEOUT;
import static software.wings.scheduler.LdapGroupSyncJob.MIN_LDAP_SYNC_TIMEOUT;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.category.element.UnitTests;
import io.harness.data.structure.UUIDGenerator;
import io.harness.ff.FeatureFlagService;
import io.harness.rule.Owner;

import software.wings.beans.Account;
import software.wings.beans.User;
import software.wings.beans.security.UserGroup;
import software.wings.beans.sso.LdapGroupResponse;
import software.wings.beans.sso.LdapSettings;
import software.wings.beans.sso.LdapUserResponse;
import software.wings.dl.WingsPersistence;
import software.wings.service.intfc.SSOSettingService;
import software.wings.service.intfc.UserService;

import com.google.inject.Inject;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.junit.MockitoJUnitRunner;

@OwnedBy(PL)
@TargetModule(HarnessModule._360_CG_MANAGER)
@RunWith(MockitoJUnitRunner.class)
public class LdapGroupSyncJobTest extends CategoryTest {
  @Inject WingsPersistence wingsPersistence;
  @InjectMocks private LdapGroupSyncJob ldapGroupSyncJob;
  @Mock private SSOSettingService ssoSettingService;
  @Mock private UserService userService;
  @Mock private FeatureFlagService featureFlagService;

  @Before
  public void setup() {
    MockitoAnnotations.initMocks(this);
    ldapGroupSyncJob = spy(ldapGroupSyncJob);
    when(featureFlagService.isEnabled(any(), any())).thenReturn(false);
  }

  @Test
  @Owner(developers = MOHIT)
  @Category(UnitTests.class)
  public void shouldSyncUserGroup() {
    UserGroup userGroup = mock(UserGroup.class);
    Account account = new Account();
    String ssoId = UUIDGenerator.generateUuid();
    account.setUuid(UUIDGenerator.generateUuid());
    when(userGroup.getName()).thenReturn("userGroup123");
    when(userGroup.getAccountId()).thenReturn(UUIDGenerator.generateUuid());
    userGroup.setAccountId(UUIDGenerator.generateUuid());
    doReturn(LdapGroupResponse.builder().selectable(true).build())
        .when(ldapGroupSyncJob)
        .fetchGroupDetails(any(), any(), any());
    doReturn(userGroup).when(ldapGroupSyncJob).syncUserGroupMetadata(any(), any());
    doReturn(true).when(ldapGroupSyncJob).validateUserGroupStates(any());
    ldapGroupSyncJob.syncUserGroups(
        account.getUuid(), mock(LdapSettings.class), Collections.singletonList(userGroup), ssoId);
    verify(ssoSettingService, times(1)).closeSyncFailureAlertIfOpen(account.getUuid(), ssoId);
  }

  @Test
  @Owner(developers = MOHIT)
  @Category(UnitTests.class)
  public void shouldNotThrowExceptionWhenSyncUserGroupFails() {
    UserGroup userGroup = mock(UserGroup.class);
    Account account = new Account();
    String ssoId = UUIDGenerator.generateUuid();
    account.setUuid(UUIDGenerator.generateUuid());
    when(userGroup.getName()).thenReturn("userGroup123");
    when(userGroup.getAccountId()).thenReturn(UUIDGenerator.generateUuid());
    userGroup.setAccountId(UUIDGenerator.generateUuid());
    LdapSettings ldapSettings = mock(LdapSettings.class);
    doReturn(LdapGroupResponse.builder().selectable(false).build())
        .when(ldapGroupSyncJob)
        .fetchGroupDetails(any(), any(), any());
    doReturn(userGroup).when(ldapGroupSyncJob).syncUserGroupMetadata(any(), any());
    doReturn(true).when(ldapGroupSyncJob).validateUserGroupStates(any());
    when(ldapSettings.getDisplayName()).thenReturn("display_name");

    ldapGroupSyncJob.syncUserGroups(account.getUuid(), ldapSettings, Collections.singletonList(userGroup), ssoId);
    verify(ssoSettingService, times(1))
        .raiseSyncFailureAlert(account.getUuid(), ssoId,
            "Ldap Sync failed for groups: "
                + "[" + userGroup.getName() + "]");
  }

  @Test
  @Owner(developers = MOHIT)
  @Category(UnitTests.class)
  public void testgetUserGroupsByEmailMap() {
    String accountId = UUIDGenerator.generateUuid();
    Account account = anAccount().withUuid(accountId).build();
    String email = "user@harness.io";
    User user = anUser().email(email).accounts(Arrays.asList(account)).build();
    UserGroup userGroup1 = UserGroup.builder().members(Arrays.asList(user)).accountId(accountId).build();
    UserGroup userGroup2 = UserGroup.builder().members(Arrays.asList()).accountId(accountId).build();
    LdapUserResponse ldapUser = LdapUserResponse.builder().email(email).name("user").build();
    Map<String, Set<UserGroup>> emailToUserGroups = ldapGroupSyncJob.getUserGroupsByEmailMap(accountId,
        new HashMap<LdapUserResponse, Set<UserGroup>>() {
          { put(ldapUser, new HashSet<>(Collections.singleton(userGroup2))); }
        },
        new HashMap<UserGroup, Set<User>>() {
          { put(userGroup1, new HashSet<>(Collections.singletonList(user))); }
        });
    assertThat(emailToUserGroups.get(email).contains(userGroup2)).isTrue();
    assertThat(emailToUserGroups.get(email).contains(userGroup1)).isFalse();
  }

  @Test
  @Owner(developers = PIYUSH)
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
    long ldapSyncTimeout = ldapGroupSyncJob.getLdapSyncTimeout(NEGATIVE_TIME);
    assertThat(MIN_LDAP_SYNC_TIMEOUT).isEqualTo(ldapSyncTimeout);

    // less than 1 minute should return MIN_LDAP_SYNC_TIMEOUT
    ldapSyncTimeout = ldapGroupSyncJob.getLdapSyncTimeout(HALF_MINUTE);
    assertThat(MIN_LDAP_SYNC_TIMEOUT).isEqualTo(ldapSyncTimeout);

    // 1 minute should return 1 minute
    ldapSyncTimeout = ldapGroupSyncJob.getLdapSyncTimeout(ONE_MINUTE);
    assertThat(ONE_MINUTE).isEqualTo(ldapSyncTimeout);

    // 2 mins should return 2 minutes as is
    ldapSyncTimeout = ldapGroupSyncJob.getLdapSyncTimeout(TWO_MINUTE);
    assertThat(TWO_MINUTE).isEqualTo(ldapSyncTimeout);

    // 3 minutes should return 3 minute
    ldapSyncTimeout = ldapGroupSyncJob.getLdapSyncTimeout(THREE_MINUTE);
    assertThat(THREE_MINUTE).isEqualTo(ldapSyncTimeout);

    // 4 minutes should return MAX_LDAP_SYNC_TIMEOUT
    ldapSyncTimeout = ldapGroupSyncJob.getLdapSyncTimeout(FOUR_MINUTE);
    assertThat(MAX_LDAP_SYNC_TIMEOUT).isEqualTo(ldapSyncTimeout);

    // Anything greater than MAX_LDAP_SYNC_TIMEOUT should return MAX_LDAP_SYNC_TIMEOUT
    ldapSyncTimeout = ldapGroupSyncJob.getLdapSyncTimeout(VERY_LARGE_TIME);
    assertThat(MAX_LDAP_SYNC_TIMEOUT).isEqualTo(ldapSyncTimeout);
  }
}
