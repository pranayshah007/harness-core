/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.service;

import static io.harness.beans.FeatureName.USE_IMMUTABLE_DELEGATE;
import static io.harness.delegate.beans.DelegateType.DOCKER;
import static io.harness.delegate.beans.DelegateType.KUBERNETES;
import static io.harness.delegate.beans.VersionOverrideType.DELEGATE_IMAGE_TAG;
import static io.harness.delegate.beans.VersionOverrideType.DELEGATE_JAR;
import static io.harness.delegate.beans.VersionOverrideType.UPGRADER_IMAGE_TAG;
import static io.harness.delegate.beans.VersionOverrideType.WATCHER_JAR;

import static java.util.Collections.emptyList;
import static org.apache.commons.lang3.StringUtils.EMPTY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import io.harness.category.element.UnitTests;
import io.harness.delegate.beans.VersionOverride;
import io.harness.delegate.beans.VersionOverride.VersionOverrideKeys;
import io.harness.delegate.service.intfc.DelegateRingService;
import io.harness.ff.FeatureFlagService;
import io.harness.persistence.HPersistence;

import software.wings.app.MainConfiguration;
import software.wings.service.impl.infra.InfraDownloadService;

import com.google.common.collect.ImmutableList;
import java.util.Collections;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class DelegateVersionServiceTest {
  public static final String ACCOUNT_ID = "accountId";
  public static final String HARNESS_OVERRIDE_TAG = "harness/delegate:override";
  public static final String HARNESS_DELEGATE_RING_IMAGE = "harness/delegate:ring";
  public static final String HARNESS_DELEGATE_PORTAL_IMAGE = "harness/delegate:portal";
  public static final String HARNESS_UPGRADER_RING_IMAGE = "harness/upgrader:ring";
  public static final String HARNESS_UPGRADER_PORTAL_IMAGE = "harness/upgrader:portal";
  public static final String DEFAULT_DELEGATE_IMAGE = "harness/delegate:latest";
  public static final String DEFAULT_UPGRADER_IMAGE = "harness/upgrader:latest";
  public static final List<String> DELEGATE_JAR_RING = ImmutableList.of("delegate1.jar", "delegate2.jar");
  public static final String WATCHER_JAR_RING = "watcher1.jar";
  public static final String ACCOUNT_DELEGATE_JAR = "delegate-1.0.jar";
  public static final String ACCOUNT_WATCHER_JAR = "watcher-2.0.jar";

  private DelegateVersionService underTest;
  @Mock private DelegateRingService delegateRingService;
  @Mock private FeatureFlagService featureFlagService;
  @Mock private InfraDownloadService infraDownloadService;
  @Mock(answer = Answers.RETURNS_DEEP_STUBS) private MainConfiguration managerConfig;
  @Mock(answer = Answers.RETURNS_DEEP_STUBS) private HPersistence persistence;

  @Before
  public void setUp() {
    underTest = spy(new DelegateVersionService(
        delegateRingService, infraDownloadService, featureFlagService, managerConfig, persistence));
  }

  @Test
  @Category(UnitTests.class)
  public void whenAllDelegateOverridesThenUseAccount() {
    final VersionOverride override =
        VersionOverride.builder(ACCOUNT_ID).overrideType(DELEGATE_IMAGE_TAG).version(HARNESS_OVERRIDE_TAG).build();

    mockDelegateOverrides(override, true, true, true);
    assertOverrides(HARNESS_OVERRIDE_TAG, DEFAULT_UPGRADER_IMAGE, emptyList(), null, KUBERNETES);
  }

  @Test
  @Category(UnitTests.class)
  public void whenDelegateImageTagAccountOverrideThenUseIt() {
    final VersionOverride override =
        VersionOverride.builder(ACCOUNT_ID).overrideType(DELEGATE_IMAGE_TAG).version(HARNESS_OVERRIDE_TAG).build();

    mockDelegateOverrides(override, false, false, false);
    assertOverrides(HARNESS_OVERRIDE_TAG, DEFAULT_UPGRADER_IMAGE, emptyList(), null, KUBERNETES);
  }

  @Test
  @Category(UnitTests.class)
  public void whenEmptyDelegateImageTagAccountOverrideThenUseDefault() {
    final VersionOverride override =
        VersionOverride.builder(ACCOUNT_ID).overrideType(DELEGATE_IMAGE_TAG).version(EMPTY).build();

    mockDelegateOverrides(override, false, false, false);
    assertOverrides(DEFAULT_DELEGATE_IMAGE, DEFAULT_UPGRADER_IMAGE, emptyList(), null, KUBERNETES);
  }

  @Test
  @Category(UnitTests.class)
  public void whenDelegateImageTagRingOverrideThenUseIt() {
    mockDelegateOverrides(null, true, false, true);
    assertOverrides(HARNESS_DELEGATE_RING_IMAGE, DEFAULT_UPGRADER_IMAGE, emptyList(), null, KUBERNETES);
  }

  @Test
  @Category(UnitTests.class)
  public void whenDelegateImageTagRingOverrideButNotImmutableThenUseDefault() {
    mockDelegateOverrides(null, true, false, false);
    assertOverrides(DEFAULT_DELEGATE_IMAGE, DEFAULT_UPGRADER_IMAGE, emptyList(), null, KUBERNETES);
  }

  @Test
  @Category(UnitTests.class)
  public void whenDelegateImageTagRingOverrideAndImmutableButNotK8SThenUseDefault() {
    mockDelegateOverrides(null, true, false, true);
    assertOverrides(DEFAULT_DELEGATE_IMAGE, DEFAULT_UPGRADER_IMAGE, emptyList(), null, DOCKER);
  }

  @Test
  @Category(UnitTests.class)
  public void whenDelegateImageTagConfigOverrideThenUseIt() {
    mockDelegateOverrides(null, false, true, false);
    assertOverrides(HARNESS_DELEGATE_PORTAL_IMAGE, DEFAULT_UPGRADER_IMAGE, emptyList(), null, KUBERNETES);
  }

  @Test
  @Category(UnitTests.class)
  public void whenNoDelegateOverrideUseDefault() {
    mockDelegateOverrides(null, false, false, false);
    assertOverrides(DEFAULT_DELEGATE_IMAGE, DEFAULT_UPGRADER_IMAGE, emptyList(), null, KUBERNETES);
  }

  @Test
  @Category(UnitTests.class)
  public void whenAllUpgraderOverridesThenUseAccount() {
    final VersionOverride override =
        VersionOverride.builder(ACCOUNT_ID).overrideType(UPGRADER_IMAGE_TAG).version(HARNESS_OVERRIDE_TAG).build();

    mockUpgraderOverrides(override, true, true, true);
    assertOverrides(DEFAULT_DELEGATE_IMAGE, HARNESS_OVERRIDE_TAG, emptyList(), null, KUBERNETES);
  }

  @Test
  @Category(UnitTests.class)
  public void whenUpgraderImageTagAccountOverrideThenUseIt() {
    final VersionOverride override =
        VersionOverride.builder(ACCOUNT_ID).overrideType(UPGRADER_IMAGE_TAG).version(HARNESS_OVERRIDE_TAG).build();

    mockUpgraderOverrides(override, true, false, false);
    assertOverrides(DEFAULT_DELEGATE_IMAGE, HARNESS_OVERRIDE_TAG, emptyList(), null, KUBERNETES);
  }

  @Test
  @Category(UnitTests.class)
  public void whenEmptyUpgraderImageTagAccountOverrideThenUseDefault() {
    final VersionOverride override =
        VersionOverride.builder(ACCOUNT_ID).overrideType(UPGRADER_IMAGE_TAG).version(EMPTY).build();

    mockUpgraderOverrides(override, true, false, false);
    assertOverrides(DEFAULT_DELEGATE_IMAGE, DEFAULT_UPGRADER_IMAGE, emptyList(), null, KUBERNETES);
  }

  @Test
  @Category(UnitTests.class)
  public void whenUpgraderImageTagRingOverrideThenUseIt() {
    mockUpgraderOverrides(null, true, false, true);
    assertOverrides(DEFAULT_DELEGATE_IMAGE, HARNESS_UPGRADER_RING_IMAGE, emptyList(), null, KUBERNETES);
  }

  @Test
  @Category(UnitTests.class)
  public void whenUpgraderImageTagRingOverrideButNotImmutableThenUseDefault() {
    mockUpgraderOverrides(null, true, false, false);
    assertOverrides(DEFAULT_DELEGATE_IMAGE, DEFAULT_UPGRADER_IMAGE, emptyList(), null, KUBERNETES);
  }

  @Test
  @Category(UnitTests.class)
  public void whenUpgraderImageTagRingOverrideAndImmutableButNotK8SThenUseDefault() {
    mockUpgraderOverrides(null, true, false, true);
    assertOverrides(DEFAULT_DELEGATE_IMAGE, DEFAULT_UPGRADER_IMAGE, emptyList(), null, DOCKER);
  }

  @Test
  @Category(UnitTests.class)
  public void whenUpgraderImageTagConfigOverrideThenUseIt() {
    mockUpgraderOverrides(null, false, true, false);
    assertOverrides(DEFAULT_DELEGATE_IMAGE, HARNESS_UPGRADER_PORTAL_IMAGE, emptyList(), null, KUBERNETES);
  }

  @Test
  @Category(UnitTests.class)
  public void whenNoUpgraderOverrideUseDefault() {
    mockUpgraderOverrides(null, false, false, false);
    assertOverrides(DEFAULT_DELEGATE_IMAGE, DEFAULT_UPGRADER_IMAGE, emptyList(), null, KUBERNETES);
  }

  @Test
  @Category(UnitTests.class)
  public void whenAllDelegateJarOverrideThenUseAccount() {
    final VersionOverride override =
        VersionOverride.builder(ACCOUNT_ID).overrideType(DELEGATE_JAR).version(ACCOUNT_DELEGATE_JAR).build();

    mockDelegateJarOverrides(override, true);
    assertOverrides(DEFAULT_DELEGATE_IMAGE, DEFAULT_UPGRADER_IMAGE, Collections.singletonList(ACCOUNT_DELEGATE_JAR),
        null, KUBERNETES);
  }

  @Test
  @Category(UnitTests.class)
  public void whenAccountDelegateJarOverrideThenUseIt() {
    final VersionOverride override =
        VersionOverride.builder(ACCOUNT_ID).overrideType(DELEGATE_JAR).version(ACCOUNT_DELEGATE_JAR).build();

    mockDelegateJarOverrides(override, false);
    assertOverrides(DEFAULT_DELEGATE_IMAGE, DEFAULT_UPGRADER_IMAGE, Collections.singletonList(ACCOUNT_DELEGATE_JAR),
        null, KUBERNETES);
  }

  @Test
  @Category(UnitTests.class)
  public void whenRingDelegateJarOverrideThenUseIt() {
    mockDelegateJarOverrides(null, true);
    assertOverrides(DEFAULT_DELEGATE_IMAGE, DEFAULT_UPGRADER_IMAGE, DELEGATE_JAR_RING, null, KUBERNETES);
  }

  @Test
  @Category(UnitTests.class)
  public void whenNoDelegateJarOverrideThenUseEmpty() {
    mockDelegateJarOverrides(null, false);
    assertOverrides(DEFAULT_DELEGATE_IMAGE, DEFAULT_UPGRADER_IMAGE, emptyList(), null, KUBERNETES);
  }

  @Test
  @Category(UnitTests.class)
  public void whenAllWatcherJarOverrideThenUseAccount() {
    final VersionOverride override =
        VersionOverride.builder(ACCOUNT_ID).overrideType(WATCHER_JAR).version(ACCOUNT_WATCHER_JAR).build();

    mockWatcherJarOverrides(override, true);
    assertOverrides(DEFAULT_DELEGATE_IMAGE, DEFAULT_UPGRADER_IMAGE, emptyList(), ACCOUNT_WATCHER_JAR, KUBERNETES);
  }

  @Test
  @Category(UnitTests.class)
  public void whenAccountWatcherJarOverrideThenUseIt() {
    final VersionOverride override =
        VersionOverride.builder(ACCOUNT_ID).overrideType(WATCHER_JAR).version(ACCOUNT_WATCHER_JAR).build();

    mockWatcherJarOverrides(override, false);
    assertOverrides(DEFAULT_DELEGATE_IMAGE, DEFAULT_UPGRADER_IMAGE, emptyList(), ACCOUNT_WATCHER_JAR, KUBERNETES);
  }

  @Test
  @Category(UnitTests.class)
  public void whenRingWatcherJarOverrideThenUseIt() {
    mockWatcherJarOverrides(null, true);
    assertOverrides(DEFAULT_DELEGATE_IMAGE, DEFAULT_UPGRADER_IMAGE, emptyList(), WATCHER_JAR_RING, KUBERNETES);
  }

  @Test
  @Category(UnitTests.class)
  public void whenNoWatcherJarOverrideThenUseEmpty() {
    doReturn(null).when(underTest).getWatcherJarVersions(ACCOUNT_ID);
    mockWatcherJarOverrides(null, false);
    assertOverrides(DEFAULT_DELEGATE_IMAGE, DEFAULT_UPGRADER_IMAGE, emptyList(), null, KUBERNETES);
  }

  private void mockDelegateOverrides(final VersionOverride override, final boolean isRingDelegateOverride,
      final boolean isPortalDelegateOverride, final boolean isImmutableDelegate) {
    mockVersionOverride(override, null, null, null);

    when(featureFlagService.isEnabled(USE_IMMUTABLE_DELEGATE, ACCOUNT_ID)).thenReturn(isImmutableDelegate);
    if (isRingDelegateOverride) {
      when(delegateRingService.getDelegateImageTag(ACCOUNT_ID)).thenReturn(HARNESS_DELEGATE_RING_IMAGE);
    }

    when(managerConfig.getPortal().getDelegateDockerImage())
        .thenReturn(isPortalDelegateOverride ? HARNESS_DELEGATE_PORTAL_IMAGE : null);
    when(managerConfig.getPortal().getUpgraderDockerImage()).thenReturn(null);
    doReturn(null).when(underTest).getWatcherJarVersions(ACCOUNT_ID);
  }

  private void mockUpgraderOverrides(final VersionOverride override, final boolean isRingUpgraderOverride,
      final boolean isPortalUpgraderOverride, final boolean isImmutableDelegate) {
    mockVersionOverride(null, override, null, null);

    when(featureFlagService.isEnabled(USE_IMMUTABLE_DELEGATE, ACCOUNT_ID)).thenReturn(isImmutableDelegate);
    if (isRingUpgraderOverride) {
      when(delegateRingService.getUpgraderImageTag(ACCOUNT_ID)).thenReturn(HARNESS_UPGRADER_RING_IMAGE);
    }

    when(managerConfig.getPortal().getDelegateDockerImage()).thenReturn(null);
    when(managerConfig.getPortal().getUpgraderDockerImage())
        .thenReturn(isPortalUpgraderOverride ? HARNESS_UPGRADER_PORTAL_IMAGE : null);
    doReturn(null).when(underTest).getWatcherJarVersions(ACCOUNT_ID);
  }

  private void mockDelegateJarOverrides(final VersionOverride override, final boolean isRingOverride) {
    mockVersionOverride(null, null, override, null);

    when(featureFlagService.isEnabled(USE_IMMUTABLE_DELEGATE, ACCOUNT_ID)).thenReturn(false);

    if (isRingOverride) {
      when(delegateRingService.getDelegateVersions(ACCOUNT_ID)).thenReturn(DELEGATE_JAR_RING);
    }
    when(managerConfig.getPortal().getDelegateDockerImage()).thenReturn(null);
    when(managerConfig.getPortal().getUpgraderDockerImage()).thenReturn(null);
    doReturn(null).when(underTest).getWatcherJarVersions(ACCOUNT_ID);
  }

  private void mockWatcherJarOverrides(final VersionOverride override, final boolean isRingOverride) {
    mockVersionOverride(null, null, null, override);

    when(featureFlagService.isEnabled(USE_IMMUTABLE_DELEGATE, ACCOUNT_ID)).thenReturn(false);

    if (isRingOverride) {
      when(delegateRingService.getWatcherVersions(ACCOUNT_ID)).thenReturn(WATCHER_JAR_RING);
    }
    when(managerConfig.getPortal().getDelegateDockerImage()).thenReturn(null);
    when(managerConfig.getPortal().getUpgraderDockerImage()).thenReturn(null);
  }

  private void mockVersionOverride(final VersionOverride delegateImageOverride, final VersionOverride upgraderOverride,
      final VersionOverride delegateJarOverride, final VersionOverride watcherOverride) {
    when(persistence.createQuery(VersionOverride.class)
             .filter(VersionOverrideKeys.accountId, ACCOUNT_ID)
             .filter(VersionOverrideKeys.overrideType, DELEGATE_IMAGE_TAG)
             .get())
        .thenReturn(delegateImageOverride);

    when(persistence.createQuery(VersionOverride.class)
             .filter(VersionOverrideKeys.accountId, ACCOUNT_ID)
             .filter(VersionOverrideKeys.overrideType, UPGRADER_IMAGE_TAG)
             .get())
        .thenReturn(upgraderOverride);
    when(persistence.createQuery(VersionOverride.class)
             .filter(VersionOverrideKeys.accountId, ACCOUNT_ID)
             .filter(VersionOverrideKeys.overrideType, DELEGATE_JAR)
             .get())
        .thenReturn(delegateJarOverride);
    when(persistence.createQuery(VersionOverride.class)
             .filter(VersionOverrideKeys.accountId, ACCOUNT_ID)
             .filter(VersionOverrideKeys.overrideType, WATCHER_JAR)
             .get())
        .thenReturn(watcherOverride);
  }

  private void assertOverrides(final String expectedDelegateTag, final String expectedUpgraderTag,
      final List<String> delegateJars, final String watcherJars, final String delegateType) {
    final String actualDelegate = underTest.getDelegateImageTag(ACCOUNT_ID, delegateType);
    final String actualUpgrader = underTest.getUpgraderImageTag(ACCOUNT_ID, delegateType);
    final List<String> actualDelegateJar = underTest.getDelegateJarVersions(ACCOUNT_ID);
    final String actualWatcherJar = underTest.getWatcherJarVersions(ACCOUNT_ID);

    assertThat(actualDelegate).isEqualTo(expectedDelegateTag);
    assertThat(actualUpgrader).isEqualTo(expectedUpgraderTag);
    assertThat(actualDelegateJar).isEqualTo(delegateJars);
    assertThat(actualWatcherJar).isEqualTo(watcherJars);
  }
}
