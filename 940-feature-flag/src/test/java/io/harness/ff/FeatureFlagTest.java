/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ff;

import static io.harness.beans.FeatureFlag.Scope.GLOBAL;
import static io.harness.rule.OwnerRule.BRETT;
import static io.harness.rule.OwnerRule.RUSHABH;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;

import io.harness.FeatureFlagTestBase;
import io.harness.beans.FeatureFlag;
import io.harness.beans.FeatureName;
import io.harness.beans.PageRequest;
import io.harness.beans.SearchFilter;
import io.harness.category.element.UnitTests;
import io.harness.configuration.DeployMode;
import io.harness.persistence.HPersistence;
import io.harness.rule.Owner;

import com.google.inject.Inject;
import java.util.HashSet;
import java.util.Set;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Spy;

public class FeatureFlagTest extends FeatureFlagTestBase {
  private static final FeatureName FEATURE =
      FeatureName.CV_DEMO; // Just pick a different one if this one should be deleted.

  private static final String ACCOUNT_ID = "ACCOUNT_ID";
  private static final String TEST_ACCOUNT_ID = "TEST_ACCOUNT_ID";
  private static final String TEST_ACCOUNT_ID_X = "TEST_ACCOUNT_ID_X";
  private static final String TEST_ACCOUNT_ID_Y = "TEST_ACCOUNT_ID_Y";

  @Inject @InjectMocks @Spy private io.harness.ff.FeatureFlagServiceImpl featureFlagService;

  @Inject private HPersistence persistence;

  private Set<String> listWith = new HashSet<>(asList(TEST_ACCOUNT_ID, TEST_ACCOUNT_ID_X));
  private Set<String> listWithout = new HashSet<>(asList(TEST_ACCOUNT_ID_X, TEST_ACCOUNT_ID_Y));

  private FeatureFlag ffTrue = FeatureFlag.builder().name(FEATURE.name()).enabled(true).build();
  private FeatureFlag ffFalse = FeatureFlag.builder().name(FEATURE.name()).enabled(false).build();
  private FeatureFlag ffTrueWith =
      FeatureFlag.builder().name(FEATURE.name()).enabled(true).accountIds(listWith).build();
  private FeatureFlag ffFalseWith =
      FeatureFlag.builder().name(FEATURE.name()).enabled(false).accountIds(listWith).build();
  private FeatureFlag ffTrueWithout =
      FeatureFlag.builder().name(FEATURE.name()).enabled(true).accountIds(listWithout).build();
  private FeatureFlag ffFalseWithout =
      FeatureFlag.builder().name(FEATURE.name()).enabled(false).accountIds(listWithout).build();

  private PageRequest<FeatureFlag> ffPageRequest = new PageRequest<>();
  private PageRequest<FeatureFlag> ffPageRequestTypeNull = new PageRequest<>();

  /**
   * setup for test.
   */
  @Before
  public void setUp() throws Exception {
    ffPageRequest.addFilter("name", SearchFilter.Operator.EQ, FEATURE.name());
    ffPageRequestTypeNull.addFilter("name", SearchFilter.Operator.EQ);
  }

  @Test
  @Owner(developers = BRETT)
  @Category(UnitTests.class)
  public void shouldBeEnabledWhenTrue() {
    persistence.save(ffTrue);
    assertThat(featureFlagService.isEnabled(FEATURE, ACCOUNT_ID)).isTrue();
  }

  @Test
  @Owner(developers = BRETT)
  @Category(UnitTests.class)
  public void shouldBeDisabledWhenFalse() {
    persistence.save(ffFalse);
    assertThat(featureFlagService.isEnabled(FEATURE, ACCOUNT_ID)).isFalse();
  }

  @Test
  @Owner(developers = BRETT)
  @Category(UnitTests.class)
  public void shouldWorkWhenAccountIdMissingTrue() {
    persistence.save(ffTrue);
    assertThat(featureFlagService.isEnabled(FEATURE, null)).isTrue();
  }

  @Test
  @Owner(developers = BRETT)
  @Category(UnitTests.class)
  public void shouldWorkWhenAccountIdMissingFalse() {
    persistence.save(ffFalse);
    assertThat(featureFlagService.isEnabled(FEATURE, null)).isFalse();
  }

  @Test
  @Owner(developers = BRETT)
  @Category(UnitTests.class)
  public void shouldWorkWhenAccountIdMissingTrueWith() {
    persistence.save(ffTrueWith);
    assertThat(featureFlagService.isEnabled(FEATURE, null)).isTrue();
  }

  @Test
  @Owner(developers = BRETT)
  @Category(UnitTests.class)
  public void shouldWorkWhenAccountIdMissingFalseWith() {
    persistence.save(ffFalseWith);
    assertThat(featureFlagService.isEnabled(FEATURE, null)).isFalse();
  }

  @Test
  @Owner(developers = BRETT)
  @Category(UnitTests.class)
  public void shouldWorkWhenAccountIdMissingTrueWithout() {
    persistence.save(ffTrueWithout);
    assertThat(featureFlagService.isEnabled(FEATURE, null)).isTrue();
  }

  @Test
  @Owner(developers = BRETT)
  @Category(UnitTests.class)
  public void shouldWorkWhenAccountIdMissingFalseWithout() {
    persistence.save(ffFalseWithout);
    assertThat(featureFlagService.isEnabled(FEATURE, null)).isFalse();
  }

  @Test
  @Owner(developers = BRETT)
  @Category(UnitTests.class)
  public void shouldBeEnabledWhenWhiteListedTrueWith() {
    persistence.save(ffTrueWith);
    assertThat(featureFlagService.isEnabled(FEATURE, TEST_ACCOUNT_ID)).isTrue();
  }

  @Test
  @Owner(developers = BRETT)
  @Category(UnitTests.class)
  public void shouldBeEnabledWhenWhiteListedFalseWith() {
    // This tests whitelisting
    persistence.save(ffFalseWith);
    assertThat(featureFlagService.isEnabled(FEATURE, TEST_ACCOUNT_ID)).isTrue();
  }

  @Test
  @Owner(developers = BRETT)
  @Category(UnitTests.class)
  public void shouldBeEnabledWhenWhiteListedTrueWithout() {
    persistence.save(ffTrueWithout);
    assertThat(featureFlagService.isEnabled(FEATURE, TEST_ACCOUNT_ID)).isTrue();
  }

  @Test
  @Owner(developers = BRETT)
  @Category(UnitTests.class)
  public void shouldBeEnabledWhenWhiteListedFalseWithout() {
    persistence.save(ffFalseWithout);
    assertThat(featureFlagService.isEnabled(FEATURE, TEST_ACCOUNT_ID)).isFalse();
  }

  @Test
  @Owner(developers = RUSHABH)
  @Category(UnitTests.class)
  public void testFeatureFlagEnabledInConfig() {
    featureFlagService.initializeFeatureFlags(DeployMode.KUBERNETES_ONPREM, FEATURE.name());
    for (FeatureName featureName : FeatureName.values()) {
      assertThat(featureFlagService.isEnabled(featureName, null)).isEqualTo(featureName == FEATURE);
    }
  }

  @Test
  @Owner(developers = RUSHABH)
  @Category(UnitTests.class)
  public void testFeatureFlagEnabledInConfigSaas() {
    featureFlagService.initializeFeatureFlags(DeployMode.KUBERNETES, FEATURE.name());

    for (FeatureName featureName : FeatureName.values()) {
      assertThat(featureFlagService.isEnabled(featureName, "dummy")).isFalse();
    }
  }

  @Test
  @Owner(developers = RUSHABH)
  @Category(UnitTests.class)
  public void testWithBadFlagEnabledValues() {
    featureFlagService.initializeFeatureFlags(DeployMode.KUBERNETES_ONPREM, "wrongName");

    for (FeatureName featureName : FeatureName.values()) {
      if (featureName.getScope() == GLOBAL) {
        assertThat(featureFlagService.isGlobalEnabled(featureName)).isFalse();
      } else {
        assertThat(featureFlagService.isEnabled(featureName, "accountId")).isFalse();
      }
    }
  }
}
