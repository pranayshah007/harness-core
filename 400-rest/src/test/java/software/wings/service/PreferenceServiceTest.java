/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service;

import static io.harness.beans.PageRequest.PageRequestBuilder.aPageRequest;
import static io.harness.beans.SearchFilter.Operator.EQ;
import static io.harness.beans.SearchFilter.Operator.EXISTS;
import static io.harness.beans.SearchFilter.Operator.IN;
import static io.harness.rule.OwnerRule.AADITI;
import static io.harness.rule.OwnerRule.AGORODETKI;
import static io.harness.rule.OwnerRule.UJJAWAL;

import static software.wings.beans.CGConstants.GLOBAL_APP_ID;
import static software.wings.common.Constants.ACCOUNT_ID_KEY;
import static software.wings.service.impl.PreferenceServiceImpl.USER_ID_KEY;
import static software.wings.utils.WingsTestConstants.ACCOUNT_ID;
import static software.wings.utils.WingsTestConstants.USER_ID;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;

import io.harness.category.element.UnitTests;
import io.harness.exception.InvalidRequestException;
import io.harness.rule.Owner;

import software.wings.WingsBaseTest;
import software.wings.beans.DeploymentPreference;
import software.wings.beans.HarnessTagFilter;
import software.wings.beans.Preference;
import software.wings.dl.WingsPersistence;
import software.wings.service.intfc.PreferenceService;
import software.wings.utils.WingsTestConstants;

import com.google.inject.Inject;
import dev.morphia.mapping.Mapper;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;

public class PreferenceServiceTest extends WingsBaseTest {
  private static final String TEST_USER_ID = "123";
  private static final String TEST_PREFERENCE_ID = "AEtq6ZDIQMyH2JInYeifWQ";
  private static final String PREFERENCE_ID = "PREFERENCE_ID";

  private static Preference preference = new DeploymentPreference();

  @Inject private WingsPersistence wingsPersistence;
  @InjectMocks @Inject private PreferenceService preferenceService;

  @Before
  public void setUp() {
    preference.setAccountId(WingsTestConstants.INTEGRATION_TEST_ACCOUNT_ID);
    preference.setUserId(TEST_USER_ID);
    preference.setUuid(TEST_PREFERENCE_ID);
    preference.setAppId(GLOBAL_APP_ID);
  }

  @Test
  @Owner(developers = UJJAWAL)
  @Category(UnitTests.class)
  public void shouldGet() {
    Preference savedPreference = wingsPersistence.saveAndGet(Preference.class, preference);
    assertThat(
        preferenceService.get(WingsTestConstants.INTEGRATION_TEST_ACCOUNT_ID, TEST_USER_ID, savedPreference.getUuid()))
        .isEqualTo(savedPreference);
  }

  @Test
  @Owner(developers = UJJAWAL)
  @Category(UnitTests.class)
  public void shouldCreate() {
    Preference createdPreference =
        preferenceService.save(WingsTestConstants.INTEGRATION_TEST_ACCOUNT_ID, TEST_USER_ID, preference);
    assertThat(wingsPersistence.get(Preference.class, createdPreference.getUuid())).isEqualTo(preference);
  }

  @Test
  @Owner(developers = UJJAWAL)
  @Category(UnitTests.class)
  public void shouldList() {
    preference.setAppId(GLOBAL_APP_ID);
    preference.setAccountId(WingsTestConstants.INTEGRATION_TEST_ACCOUNT_ID);
    preference.setUserId(TEST_USER_ID);
    Preference savedPreference = wingsPersistence.saveAndGet(Preference.class, preference);
    assertThat(preferenceService.list(
                   aPageRequest().addFilter("accountId", EQ, WingsTestConstants.INTEGRATION_TEST_ACCOUNT_ID).build(),
                   TEST_USER_ID))
        .hasSize(1)
        .containsExactly(savedPreference);
  }

  @Test
  @Owner(developers = UJJAWAL)
  @Category(UnitTests.class)
  public void shouldUpdate() {
    Preference savedPreference = wingsPersistence.saveAndGet(Preference.class, preference);
    savedPreference.setName("NEW NAME");
    preferenceService.update(
        WingsTestConstants.INTEGRATION_TEST_ACCOUNT_ID, TEST_USER_ID, savedPreference.getUuid(), savedPreference);
    assertThat(wingsPersistence.get(Preference.class, savedPreference.getUuid())).isEqualTo(preference);
  }

  @Test
  @Owner(developers = UJJAWAL)
  @Category(UnitTests.class)
  public void shouldDelete() {
    wingsPersistence.saveAndGet(Preference.class, preference);
    preferenceService.delete(WingsTestConstants.INTEGRATION_TEST_ACCOUNT_ID, TEST_USER_ID, TEST_PREFERENCE_ID);
    assertThat(wingsPersistence.createQuery(Preference.class)
                   .filter(ACCOUNT_ID_KEY, WingsTestConstants.INTEGRATION_TEST_ACCOUNT_ID)
                   .filter(USER_ID_KEY, TEST_USER_ID)
                   .filter(Mapper.ID_KEY, TEST_PREFERENCE_ID)
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
}
