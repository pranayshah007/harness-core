/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ff;

import static io.harness.beans.FeatureName.CV_DEMO;
import static io.harness.beans.FeatureName.GLOBAL_DISABLE_HEALTH_CHECK;
import static io.harness.beans.FeatureName.SEARCH_REQUEST;
import static io.harness.rule.OwnerRule.DEEPAK;
import static io.harness.rule.OwnerRule.NANDAN;
import static io.harness.rule.OwnerRule.PHOENIKX;
import static io.harness.rule.OwnerRule.UTKARSH;
import static io.harness.rule.OwnerRule.VIKAS;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.FeatureFlagTestBase;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.FeatureFlag;
import io.harness.beans.FeatureName;
import io.harness.category.element.UnitTests;
import io.harness.configuration.DeployMode;
import io.harness.exception.InvalidRequestException;
import io.harness.persistence.HPersistence;
import io.harness.rule.Owner;

import com.google.common.collect.Sets;
import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@FieldDefaults(level = AccessLevel.PRIVATE)
@OwnedBy(HarnessTeam.PL)
public class FeatureFlagServiceTest extends FeatureFlagTestBase {
  @Inject HPersistence persistence;
  @Inject FeatureFlagService featureFlagService;

  @Before
  public void setup() {}

  @Test
  @Owner(developers = VIKAS)
  @Category(UnitTests.class)
  public void testGetFeatureFlag_WhenFlagNotPresentInDB() {
    Optional<FeatureFlag> featureFlag = featureFlagService.getFeatureFlag(SEARCH_REQUEST);
    assertThat(featureFlag.isPresent()).isFalse();
  }

  @Test
  @Owner(developers = VIKAS)
  @Category(UnitTests.class)
  public void testGetFeatureFlag_WhenFlagPresentInDB() {
    FeatureFlag featureFlag = FeatureFlag.builder().name(SEARCH_REQUEST.name()).enabled(true).build();
    persistence.save(featureFlag);
    Optional<FeatureFlag> featureFlagInDb = featureFlagService.getFeatureFlag(SEARCH_REQUEST);
    assertThat(featureFlagInDb.isPresent()).isTrue();
    assertThat(featureFlagInDb.get().getName()).isEqualTo(SEARCH_REQUEST.name());
  }

  @Test
  @Owner(developers = VIKAS)
  @Category(UnitTests.class)
  public void testIsEnabled_WhenFlagNotPresentInDB() {
    boolean featureFlagEnabled = featureFlagService.isEnabled(SEARCH_REQUEST, null);
    assertThat(featureFlagEnabled).isFalse();
  }

  @Test
  @Owner(developers = VIKAS)
  @Category(UnitTests.class)
  public void testIsEnabled_WhenFlagPresentInDB() {
    FeatureFlag featureFlag = FeatureFlag.builder().name(SEARCH_REQUEST.name()).enabled(true).build();
    persistence.save(featureFlag);
    boolean featureFlagEnabled = featureFlagService.isEnabled(SEARCH_REQUEST, null);
    assertThat(featureFlagEnabled).isTrue();
  }

  @Test
  @Owner(developers = UTKARSH)
  @Category(UnitTests.class)
  public void testGetAccountIds_ScopeAccount() {
    FeatureName featureName = CV_DEMO;
    Set<String> accountIds = featureFlagService.getAccountIds(featureName);
    assertThat(accountIds).hasSize(0);

    FeatureFlag featureFlag =
        FeatureFlag.builder().name(featureName.name()).enabled(false).accountIds(Sets.newHashSet()).build();
    persistence.save(featureFlag);
    featureFlagService.isEnabledReloadCache(featureName, "accountId");
    accountIds = featureFlagService.getAccountIds(featureName);
    assertThat(accountIds).hasSize(0);

    featureFlag.setAccountIds(Sets.newHashSet("accountId"));
    persistence.save(featureFlag);
    featureFlagService.isEnabledReloadCache(featureName, "accountId");
    accountIds = featureFlagService.getAccountIds(featureName);
    assertThat(accountIds).hasSize(1);
  }

  @Test
  @Owner(developers = UTKARSH)
  @Category(UnitTests.class)
  public void testGetAccountIds_ScopeGlobal() {
    FeatureName featureName = GLOBAL_DISABLE_HEALTH_CHECK;
    Set<String> accountIds = featureFlagService.getAccountIds(featureName);
    assertThat(accountIds).hasSize(0);

    FeatureFlag featureFlag =
        FeatureFlag.builder().name(featureName.name()).enabled(false).accountIds(Sets.newHashSet()).build();
    persistence.save(featureFlag);
    featureFlagService.isEnabledReloadCache(featureName, "accountId");
    accountIds = featureFlagService.getAccountIds(featureName);
    assertThat(accountIds).hasSize(0);

    featureFlag.setAccountIds(Sets.newHashSet("accountId"));
    persistence.save(featureFlag);
    featureFlagService.isEnabledReloadCache(featureName, "accountId");
    accountIds = featureFlagService.getAccountIds(featureName);
    assertThat(accountIds).hasSize(1);
  }

  @Test
  @Owner(developers = UTKARSH)
  @Category(UnitTests.class)
  public void testEnableGlobally_UpdateFeatureFlag() {
    FeatureName featureName = CV_DEMO;

    FeatureFlag featureFlag =
        FeatureFlag.builder().name(featureName.name()).enabled(false).accountIds(Sets.newHashSet()).build();
    persistence.save(featureFlag);
    featureFlagService.enableGlobally(featureName);
    Optional<FeatureFlag> featureFlagOptional = featureFlagService.getFeatureFlag(featureName);
    assertThat(featureFlagOptional.isPresent()).isTrue();
    assertThat(featureFlagOptional.get().isEnabled()).isTrue();
    assertThat(featureFlagOptional.get().getName()).isEqualTo(featureFlag.getName());
  }

  @Test
  @Owner(developers = NANDAN)
  @Category(UnitTests.class)
  public void testGetGloballyEnabledFeatureFlags() {
    FeatureFlag testFeatureFlag_1 = FeatureFlag.builder()
                                        .name("test_1")
                                        .enabled(true)
                                        .obsolete(false)
                                        .accountIds(Collections.singleton("test"))
                                        .build();

    FeatureFlag testFeatureFlag_2 = FeatureFlag.builder()
                                        .name("test_2")
                                        .enabled(false)
                                        .obsolete(false)
                                        .accountIds(Collections.singleton("test"))
                                        .build();

    persistence.save(testFeatureFlag_1);
    persistence.save(testFeatureFlag_2);

    List<FeatureFlag> featureFlags = new ArrayList<>();

    featureFlags.add(testFeatureFlag_1);
    featureFlags.add(testFeatureFlag_2);

    List<FeatureFlag> globallyEnabledFeatureFlags = featureFlagService.getGloballyEnabledFeatureFlags();

    assertThat(globallyEnabledFeatureFlags.size() == 1).isTrue();
    assertThat(globallyEnabledFeatureFlags.get(0).getName()).isEqualTo("test_1");
  }

  @Test(expected = InvalidRequestException.class)
  @Owner(developers = PHOENIKX)
  @Category(UnitTests.class)
  public void testUpdateFeatureFlagForAccount_FeatureFlagNotFound() {
    featureFlagService.updateFeatureFlagForAccount(CV_DEMO.name(), "abcde", false);
  }

  @Test
  @Owner(developers = PHOENIKX)
  @Category(UnitTests.class)
  public void testUpdateFeatureFlagForAccount_EnableFeatureFlag() {
    FeatureName featureName = CV_DEMO;
    FeatureFlag featureFlag = FeatureFlag.builder().name(featureName.name()).enabled(false).obsolete(false).build();
    persistence.save(featureFlag);
    FeatureFlag updatedFeatureFlag = featureFlagService.updateFeatureFlagForAccount(featureName.name(), "abcde", true);
    assertThat(updatedFeatureFlag).isNotNull();
    assertThat(updatedFeatureFlag.getAccountIds()).contains("abcde");
  }

  @Test
  @Owner(developers = PHOENIKX)
  @Category(UnitTests.class)
  public void testUpdateFeatureFlagForAccount_DisableFeatureFlag() {
    FeatureName featureName = CV_DEMO;
    FeatureFlag featureFlag = FeatureFlag.builder()
                                  .name(featureName.name())
                                  .enabled(false)
                                  .accountIds(Sets.newHashSet("abcde"))
                                  .obsolete(false)
                                  .build();
    persistence.save(featureFlag);
    FeatureFlag updatedFeatureFlag = featureFlagService.updateFeatureFlagForAccount(featureName.name(), "abcde", false);
    assertThat(updatedFeatureFlag).isNotNull();
    assertThat(updatedFeatureFlag.getAccountIds()).isEmpty();
  }

  @Test
  @Owner(developers = DEEPAK)
  @Category(UnitTests.class)
  public void testIntializeFeatureFlags_1() {
    FeatureFlag enabledFeatureFlag = FeatureFlag.builder()
                                         .name(CV_DEMO.name())
                                         .enabled(true)
                                         .accountIds(Sets.newHashSet("abcde"))
                                         .obsolete(false)
                                         .build();
    persistence.save(enabledFeatureFlag);

    FeatureFlag disabledFeatureFlag =
        FeatureFlag.builder().name(SEARCH_REQUEST.name()).enabled(false).obsolete(false).build();
    persistence.save(disabledFeatureFlag);

    featureFlagService.initializeFeatureFlags(DeployMode.ONPREM, GLOBAL_DISABLE_HEALTH_CHECK.toString());
    FeatureFlag cvDemoFeatureFlag = featureFlagService.getFeatureFlag(CV_DEMO).get();
    FeatureFlag searchRequestFF = featureFlagService.getFeatureFlag(SEARCH_REQUEST).get();
    FeatureFlag globaldisableFF = featureFlagService.getFeatureFlag(GLOBAL_DISABLE_HEALTH_CHECK).get();

    assertThat(cvDemoFeatureFlag).isNotNull();
    assertThat(cvDemoFeatureFlag.isEnabled()).isFalse();

    assertThat(searchRequestFF).isNotNull();
    assertThat(searchRequestFF.getAccountIds()).isNull();
    assertThat(cvDemoFeatureFlag.isEnabled()).isFalse();

    assertThat(globaldisableFF).isNotNull();
    assertThat(globaldisableFF.isEnabled()).isTrue();
  }
}
