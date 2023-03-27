/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service;

import static io.harness.beans.FeatureName.SPG_ENABLE_SHARING_FILTERS;
import static io.harness.beans.PageRequest.PageRequestBuilder.aPageRequest;
import static io.harness.beans.SearchFilter.Operator.EQ;
import static io.harness.beans.SearchFilter.Operator.EXISTS;
import static io.harness.beans.SearchFilter.Operator.IN;
import static io.harness.rule.OwnerRule.AADITI;
import static io.harness.rule.OwnerRule.AGORODETKI;
import static io.harness.rule.OwnerRule.RAFAEL;
import static io.harness.rule.OwnerRule.UJJAWAL;

import static software.wings.beans.Account.Builder.anAccount;
import static software.wings.beans.CGConstants.GLOBAL_APP_ID;
import static software.wings.beans.User.Builder.anUser;
import static software.wings.utils.WingsTestConstants.ACCOUNT1_ID;
import static software.wings.utils.WingsTestConstants.ACCOUNT_ID;
import static software.wings.utils.WingsTestConstants.ACCOUNT_NAME;
import static software.wings.utils.WingsTestConstants.APP_ID;
import static software.wings.utils.WingsTestConstants.INTEGRATION_TEST_ACCOUNT_ID;
import static software.wings.utils.WingsTestConstants.PASSWORD;
import static software.wings.utils.WingsTestConstants.USER1_EMAIL;
import static software.wings.utils.WingsTestConstants.USER1_ID;
import static software.wings.utils.WingsTestConstants.USER1_NAME;
import static software.wings.utils.WingsTestConstants.USER_EMAIL;
import static software.wings.utils.WingsTestConstants.USER_GROUP_ID;
import static software.wings.utils.WingsTestConstants.USER_ID;
import static software.wings.utils.WingsTestConstants.USER_NAME;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import io.harness.category.element.UnitTests;
import io.harness.exception.InvalidRequestException;
import io.harness.ff.FeatureFlagService;
import io.harness.rule.Owner;

import software.wings.WingsBaseTest;
import software.wings.beans.Account;
import software.wings.beans.DeploymentPreference;
import software.wings.beans.HarnessTagFilter;
import software.wings.beans.Preference;
import software.wings.beans.Preference.PreferenceKeys;
import software.wings.beans.User;
import software.wings.beans.security.UserGroup;
import software.wings.beans.security.UserGroup.UserGroupBuilder;
import software.wings.dl.WingsPersistence;
import software.wings.service.impl.PreferenceServiceImpl;
import software.wings.service.intfc.PreferenceService;
import software.wings.service.intfc.UserGroupService;
import software.wings.service.intfc.UserService;

import com.google.inject.Inject;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;

public class PreferenceServiceTest extends WingsBaseTest {
  private static final String TEST_USER_ID = "123";
  private static final String TEST_PREFERENCE_ID = "AEtq6ZDIQMyH2JInYeifWQ";
  private static final String PREFERENCE_ID = "PREFERENCE_ID";

  private static Preference preference = new DeploymentPreference();
  private static Preference preferenceWithShareUserGroupId = new DeploymentPreference();

  private UserGroupBuilder userGroupBuilder = UserGroup.builder().uuid(USER_GROUP_ID);

  private Account.Builder accountAdmin =
      anAccount().withAccountName(ACCOUNT_NAME).withUuid(ACCOUNT_ID).withAppId(APP_ID);

  private User.Builder userAdminBuilder = anUser()
                                              .uuid(USER_ID)
                                              .defaultAccountId(ACCOUNT_ID)
                                              .appId(APP_ID)
                                              .email(USER_EMAIL)
                                              .name(USER_NAME)
                                              .password(PASSWORD);

  private User.Builder userBuilder = anUser()
                                         .uuid(USER1_ID)
                                         .defaultAccountId(ACCOUNT1_ID)
                                         .appId(APP_ID)
                                         .email(USER1_EMAIL)
                                         .name(USER1_NAME)
                                         .password(PASSWORD);
  @Inject private WingsPersistence wingsPersistence;

  @Mock private FeatureFlagService featureFlagService;

  @Mock private UserService userService;
  @Mock private UserGroupService userGroupService;

  @InjectMocks @Inject private PreferenceService preferenceService;

  @Inject private PreferenceServiceImpl preferenceServiceImpl;

  @Before
  public void setUp() {
    preference.setAccountId(INTEGRATION_TEST_ACCOUNT_ID);
    preference.setUserId(TEST_USER_ID);
    preference.setUuid(TEST_PREFERENCE_ID);
    preference.setAppId(GLOBAL_APP_ID);

    preferenceWithShareUserGroupId.setAccountId(ACCOUNT_ID);
    preferenceWithShareUserGroupId.setUserGroupsIdToShare(new HashSet<>(List.of(USER_GROUP_ID)));
    preferenceWithShareUserGroupId.setUserId(USER_ID);
    preferenceWithShareUserGroupId.setUuid(PREFERENCE_ID);
    preferenceWithShareUserGroupId.setAppId(APP_ID);
  }

  @Test
  @Owner(developers = UJJAWAL)
  @Category(UnitTests.class)
  public void shouldGet() {
    Preference savedPreference = wingsPersistence.saveAndGet(Preference.class, preference);
    assertThat(preferenceService.get(INTEGRATION_TEST_ACCOUNT_ID, TEST_USER_ID, savedPreference.getUuid()))
        .isEqualTo(savedPreference);

    Preference savedPreferenceWithUGID = wingsPersistence.saveAndGet(Preference.class, preferenceWithShareUserGroupId);
    assertThat(preferenceService.get(ACCOUNT_ID, USER_ID, savedPreferenceWithUGID.getUuid()))
        .isEqualTo(savedPreferenceWithUGID);
  }

  @Test
  @Owner(developers = UJJAWAL)
  @Category(UnitTests.class)
  public void shouldCreate() {
    Preference createdPreference = preferenceService.save(INTEGRATION_TEST_ACCOUNT_ID, TEST_USER_ID, preference);
    assertThat(wingsPersistence.get(Preference.class, createdPreference.getUuid())).isEqualTo(preference);

    Preference createdPreferenceWithUGID = preferenceService.save(ACCOUNT_ID, USER_ID, preferenceWithShareUserGroupId);
    assertThat(wingsPersistence.get(Preference.class, createdPreferenceWithUGID.getUuid()))
        .isEqualTo(preferenceWithShareUserGroupId);
  }

  @Test
  @Owner(developers = RAFAEL)
  @Category(UnitTests.class)
  public void shouldCreateWithFFEnabled() {
    User user = userBuilder.accounts(List.of(accountAdmin.build())).build();
    UserGroup userGroup = userGroupBuilder.build();
    when(featureFlagService.isEnabled(eq(SPG_ENABLE_SHARING_FILTERS), any())).thenReturn(true);
    when(userService.get(ACCOUNT_ID, USER_ID)).thenReturn(user);
    when(userGroupService.getAdminUserGroup(ACCOUNT_ID)).thenReturn(userGroup);
    when(userGroupService.listByAccountId(ACCOUNT_ID, user, true)).thenReturn(List.of(userGroup));

    Preference createdPreference = preferenceService.save(INTEGRATION_TEST_ACCOUNT_ID, TEST_USER_ID, preference);
    assertThat(wingsPersistence.get(Preference.class, createdPreference.getUuid())).isEqualTo(preference);

    Preference createdPreferenceWithUGID = preferenceService.save(ACCOUNT_ID, USER_ID, preferenceWithShareUserGroupId);
    assertThat(wingsPersistence.get(Preference.class, createdPreferenceWithUGID.getUuid()))
        .isEqualTo(preferenceWithShareUserGroupId);
  }

  @Test(expected = InvalidRequestException.class)
  @Owner(developers = RAFAEL)
  @Category(UnitTests.class)
  public void shouldCreateWithFFRaiseException() {
    when(featureFlagService.isEnabled(eq(SPG_ENABLE_SHARING_FILTERS), any())).thenReturn(true);
    preferenceService.save(ACCOUNT_ID, USER_ID, preferenceWithShareUserGroupId);
  }

  @Test(expected = InvalidRequestException.class)
  @Owner(developers = RAFAEL)
  @Category(UnitTests.class)
  public void shouldCreateWithFFRaiseExceptionUserExists() {
    wingsPersistence.saveAndGet(Preference.class, preferenceWithShareUserGroupId);
    preferenceService.save(ACCOUNT_ID, USER_ID, preferenceWithShareUserGroupId);
  }

  @Test
  @Owner(developers = UJJAWAL)
  @Category(UnitTests.class)
  public void shouldList() {
    preference.setAppId(GLOBAL_APP_ID);
    preference.setAccountId(INTEGRATION_TEST_ACCOUNT_ID);
    preference.setUserId(TEST_USER_ID);
    Preference savedPreference = wingsPersistence.saveAndGet(Preference.class, preference);
    assertThat(preferenceService.list(aPageRequest().addFilter("accountId", EQ, INTEGRATION_TEST_ACCOUNT_ID).build(),
                   INTEGRATION_TEST_ACCOUNT_ID, TEST_USER_ID))
        .hasSize(1)
        .containsExactly(savedPreference);
  }

  @Test
  @Owner(developers = RAFAEL)
  @Category(UnitTests.class)
  public void shouldListWithUserGroupWhenFFEnable() {
    User user = anUser().userGroups(List.of(userGroupBuilder.build())).uuid(TEST_USER_ID).appId(GLOBAL_APP_ID).build();
    when(featureFlagService.isEnabled(eq(SPG_ENABLE_SHARING_FILTERS), any())).thenReturn(true);
    when(userService.get(TEST_USER_ID)).thenReturn(user);
    when(userGroupService.listByAccountId(INTEGRATION_TEST_ACCOUNT_ID, user, true))
        .thenReturn(List.of(userGroupBuilder.build()));
    preference.setAppId(GLOBAL_APP_ID);
    preference.setAccountId(INTEGRATION_TEST_ACCOUNT_ID);
    preference.setUserId(TEST_USER_ID);
    preferenceWithShareUserGroupId.setUserGroupsIdToShare(Set.of(USER_GROUP_ID));
    preferenceWithShareUserGroupId.setAccountId(INTEGRATION_TEST_ACCOUNT_ID);
    preferenceWithShareUserGroupId.setUserId(USER_ID);
    preferenceWithShareUserGroupId.setAppId(GLOBAL_APP_ID);
    Preference savedPreference = wingsPersistence.saveAndGet(Preference.class, preference);
    Preference savedPreference2 = wingsPersistence.saveAndGet(Preference.class, preferenceWithShareUserGroupId);
    assertThat(preferenceService.list(aPageRequest().addFilter("accountId", EQ, INTEGRATION_TEST_ACCOUNT_ID).build(),
                   INTEGRATION_TEST_ACCOUNT_ID, TEST_USER_ID))
        .hasSize(2)
        .contains(savedPreference)
        .contains(savedPreference2);
  }

  @Test
  @Owner(developers = UJJAWAL)
  @Category(UnitTests.class)
  public void shouldUpdate() {
    Preference savedPreference = wingsPersistence.saveAndGet(Preference.class, preference);
    savedPreference.setName("NEW NAME");
    preferenceService.update(INTEGRATION_TEST_ACCOUNT_ID, TEST_USER_ID, savedPreference.getUuid(), savedPreference);
    assertThat(wingsPersistence.get(Preference.class, savedPreference.getUuid())).isEqualTo(preference);
  }

  @Test
  @Owner(developers = RAFAEL)
  @Category(UnitTests.class)
  public void shouldUpdateWithFF() {
    when(featureFlagService.isEnabled(eq(SPG_ENABLE_SHARING_FILTERS), any())).thenReturn(true);
    User user = userBuilder.accounts(List.of(accountAdmin.build())).build();
    UserGroup userGroup = userGroupBuilder.build();
    when(userService.get(ACCOUNT_ID, USER_ID)).thenReturn(user);
    when(userGroupService.getAdminUserGroup(ACCOUNT_ID)).thenReturn(userGroup);
    when(userGroupService.listByAccountId(ACCOUNT_ID, user, true)).thenReturn(List.of(userGroup));
    preferenceWithShareUserGroupId.setUserGroupsIdToShare(Set.of(USER_GROUP_ID));
    preferenceWithShareUserGroupId.setAccountId(ACCOUNT_ID);
    preferenceWithShareUserGroupId.setUserId(USER_ID);
    preferenceWithShareUserGroupId.setAppId(GLOBAL_APP_ID);
    Preference savedPreference = wingsPersistence.saveAndGet(Preference.class, preferenceWithShareUserGroupId);
    savedPreference.setName("NEW NAME");
    preferenceService.update(ACCOUNT_ID, USER_ID, savedPreference.getUuid(), savedPreference);
    Preference postUpdatePreference = wingsPersistence.get(Preference.class, savedPreference.getUuid());

    assertThat(postUpdatePreference).isEqualTo(preferenceWithShareUserGroupId);
    assertThat(postUpdatePreference.getUserGroupsIdToShare().size()).isEqualTo(1);
  }

  @Test
  @Owner(developers = UJJAWAL)
  @Category(UnitTests.class)
  public void shouldDelete() {
    wingsPersistence.saveAndGet(Preference.class, preference);
    preferenceService.delete(INTEGRATION_TEST_ACCOUNT_ID, TEST_USER_ID, TEST_PREFERENCE_ID);
    assertThat(wingsPersistence.createQuery(Preference.class)
                   .filter(PreferenceKeys.accountId, INTEGRATION_TEST_ACCOUNT_ID)
                   .filter(PreferenceKeys.userId, TEST_USER_ID)
                   .filter(PreferenceKeys.id, TEST_PREFERENCE_ID)
                   .get())
        .isNull();
  }

  @Test
  @Owner(developers = RAFAEL)
  @Category(UnitTests.class)
  public void shouldDeleteWithFF() {
    when(featureFlagService.isEnabled(eq(SPG_ENABLE_SHARING_FILTERS), any())).thenReturn(true);
    User user = userBuilder.accounts(List.of(accountAdmin.build())).build();
    UserGroup userGroup = userGroupBuilder.build();
    when(userService.get(ACCOUNT_ID, USER_ID)).thenReturn(user);
    when(userGroupService.getAdminUserGroup(ACCOUNT_ID)).thenReturn(userGroup);
    when(userGroupService.listByAccountId(ACCOUNT_ID, user, true)).thenReturn(List.of(userGroup));
    preferenceWithShareUserGroupId.setUserGroupsIdToShare(Set.of(USER_GROUP_ID));
    preferenceWithShareUserGroupId.setAccountId(ACCOUNT_ID);
    preferenceWithShareUserGroupId.setUserId(USER_ID);
    preferenceWithShareUserGroupId.setAppId(GLOBAL_APP_ID);
    wingsPersistence.saveAndGet(Preference.class, preferenceWithShareUserGroupId);
    preferenceService.delete(ACCOUNT_ID, USER_ID, PREFERENCE_ID);
    assertThat(wingsPersistence.createQuery(Preference.class)
                   .filter(PreferenceKeys.accountId, ACCOUNT_ID)
                   .filter(PreferenceKeys.userId, USER_ID)
                   .filter(PreferenceKeys.id, PREFERENCE_ID)
                   .get())
        .isNull();
  }

  @Test
  @Owner(developers = AADITI)
  @Category(UnitTests.class)
  public void shouldCRUDPreferenceWithTagFilterAndIncludeIndirectExecutions() {
    // create preference
    DeploymentPreference deploymentPreferenceWithTagFilters = new DeploymentPreference();
    deploymentPreferenceWithTagFilters.setAccountId(ACCOUNT_ID);
    deploymentPreferenceWithTagFilters.setUserId(USER_ID);
    deploymentPreferenceWithTagFilters.setUuid(PREFERENCE_ID);
    deploymentPreferenceWithTagFilters.setIncludeIndirectExecutions(true);
    deploymentPreferenceWithTagFilters.setHarnessTagFilter(
        HarnessTagFilter.builder()
            .matchAll(true)
            .conditions(asList(HarnessTagFilter.TagFilterCondition.builder().name("key").operator(EXISTS).build()))
            .build());
    preferenceService.save(ACCOUNT_ID, USER_ID, deploymentPreferenceWithTagFilters);
    DeploymentPreference createdDeploymentPreference =
        (DeploymentPreference) preferenceService.get(ACCOUNT_ID, USER_ID, PREFERENCE_ID);
    assertThat(createdDeploymentPreference.isIncludeIndirectExecutions()).isTrue();
    assertThat(createdDeploymentPreference.getHarnessTagFilter().isMatchAll()).isTrue();
    assertThat(createdDeploymentPreference.getHarnessTagFilter().getConditions().get(0).getName()).isEqualTo("key");
    assertThat(createdDeploymentPreference.getUiDisplayTagString()).isEqualTo("key");

    // update preference
    createdDeploymentPreference.setIncludeIndirectExecutions(false);
    createdDeploymentPreference.setHarnessTagFilter(HarnessTagFilter.builder()
                                                        .matchAll(true)
                                                        .conditions(asList(HarnessTagFilter.TagFilterCondition.builder()
                                                                               .name("key")
                                                                               .operator(IN)
                                                                               .values(asList("value1"))
                                                                               .build()))
                                                        .build());
    preferenceService.update(ACCOUNT_ID, USER_ID, PREFERENCE_ID, createdDeploymentPreference);
    DeploymentPreference updatedDeploymentPreference =
        (DeploymentPreference) preferenceService.get(ACCOUNT_ID, USER_ID, PREFERENCE_ID);
    assertThat(updatedDeploymentPreference.isIncludeIndirectExecutions()).isFalse();
    assertThat(updatedDeploymentPreference.getHarnessTagFilter().isMatchAll()).isTrue();
    assertThat(updatedDeploymentPreference.getHarnessTagFilter().getConditions().get(0).getName()).isEqualTo("key");
    assertThat(updatedDeploymentPreference.getHarnessTagFilter().getConditions().get(0).getValues()).contains("value1");
    assertThat(updatedDeploymentPreference.getUiDisplayTagString()).isEqualTo("key:value1");

    // delete preference
    preferenceService.delete(ACCOUNT_ID, USER_ID, PREFERENCE_ID);
    assertThat(preferenceService.get(ACCOUNT_ID, USER_ID, PREFERENCE_ID)).isNull();
  }

  @Test(expected = InvalidRequestException.class)
  @Owner(developers = AGORODETKI)
  @Category(UnitTests.class)
  public void shouldThrowExceptionOnUpdatingPreferenceNameToExistingOne() {
    Preference preferenceWithSameName = new DeploymentPreference();
    preferenceWithSameName.setAccountId(ACCOUNT_ID);
    preferenceWithSameName.setUserId(TEST_USER_ID);
    preferenceWithSameName.setUuid(PREFERENCE_ID);
    preferenceWithSameName.setAppId(GLOBAL_APP_ID);
    preferenceWithSameName.setName("OTHER_NAME");
    preference.setName("NAME");
    preference.setAccountId(ACCOUNT_ID);

    preferenceService.save(ACCOUNT_ID, TEST_USER_ID, preference);
    preferenceService.save(ACCOUNT_ID, TEST_USER_ID, preferenceWithSameName);

    preferenceWithSameName.setName("NAME");

    preferenceService.update(ACCOUNT_ID, TEST_USER_ID, PREFERENCE_ID, preferenceWithSameName);
  }

  @Test
  @Owner(developers = RAFAEL)
  @Category(UnitTests.class)
  public void shouldHasUserGroupAdmin() {
    User user = userBuilder.accounts(List.of(accountAdmin.build())).build();
    User user1 = userAdminBuilder.accounts(List.of(accountAdmin.build())).build();
    UserGroup userGroup = userGroupBuilder.build();

    when(userService.get(ACCOUNT_ID, USER_ID)).thenReturn(user);
    when(userGroupService.getAdminUserGroup(ACCOUNT_ID)).thenReturn(userGroup);
    when(userGroupService.listByAccountId(ACCOUNT_ID, user, true)).thenReturn(List.of(userGroup));

    boolean had = preferenceServiceImpl.hasUserGroupAdmin(ACCOUNT_ID, USER_ID);
    assertThat(had).isEqualTo(true);

    when(userService.get(ACCOUNT1_ID, USER1_ID)).thenReturn(user);
    when(userGroupService.getAdminUserGroup(ACCOUNT1_ID)).thenReturn(userGroup);
    when(userGroupService.listByAccountId(ACCOUNT1_ID, user1, true)).thenReturn(List.of());

    boolean hadnt = preferenceServiceImpl.hasUserGroupAdmin(ACCOUNT1_ID, USER1_ID);
    assertThat(hadnt).isEqualTo(false);
  }
}
