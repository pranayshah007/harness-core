/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.service;

import static io.harness.delegate.beans.VersionOverrideType.DELEGATE_IMAGE_TAG;
import static io.harness.rule.OwnerRule.JENNY;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

import io.harness.category.element.UnitTests;
import io.harness.delegate.beans.DelegateRing;
import io.harness.delegate.beans.SupportedDelegateVersion;
import io.harness.delegate.beans.VersionOverride;
import io.harness.delegate.utils.DelegateRingConstants;
import io.harness.persistence.HPersistence;
import io.harness.rule.Owner;
import io.harness.service.intfc.DelegateRingService;

import software.wings.WingsBaseTest;
import software.wings.beans.Account;

import com.google.inject.Inject;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;

public class DelegateVersionTest extends WingsBaseTest {
  public static final String ACCOUNT_ID = "accountId";
  public static final String HARNESS_DELEGATE_RING_IMAGE = "harness/delegate:ring";

  @Mock private DelegateRingService delegateRingService;
  @Inject private HPersistence persistence;
  @Inject @InjectMocks private DelegateVersionService delegateVersionService;

  @Before
  public void setUp() {
    initMocks(this);
    setUpTestData();
  }

  @Test
  @Owner(developers = JENNY)
  @Category(UnitTests.class)
  public void testSupportedDelegateVersion() {
    when(delegateRingService.getDelegateImageTag(ACCOUNT_ID)).thenReturn(HARNESS_DELEGATE_RING_IMAGE);
    SupportedDelegateVersion supportedDelegateVersion = delegateVersionService.getSupportedDelegateVersion(ACCOUNT_ID);
    assertThat(supportedDelegateVersion).isNotNull();
    assertThat(supportedDelegateVersion.getLatestSupportedVersion()).isEqualTo(HARNESS_DELEGATE_RING_IMAGE);
    assertThat(supportedDelegateVersion.getLatestSupportedMinimalVersion()).isEqualTo("ring.minimal");
  }

  @Test
  @Owner(developers = JENNY)
  @Category(UnitTests.class)
  public void testSupportedDelegateVersionWithVersionOverride() {
    final VersionOverride overrideImmutable =
        VersionOverride.builder(ACCOUNT_ID).overrideType(DELEGATE_IMAGE_TAG).version("latest:88").build();
    persistence.save(overrideImmutable);

    SupportedDelegateVersion supportedDelegateVersion = delegateVersionService.getSupportedDelegateVersion(ACCOUNT_ID);
    assertThat(supportedDelegateVersion).isNotNull();
    assertThat(supportedDelegateVersion.getLatestSupportedVersion()).isEqualTo("latest:88");
    assertThat(supportedDelegateVersion.getLatestSupportedMinimalVersion()).isEqualTo("88.minimal");
  }

  private void setUpTestData() {
    persistence.save(
        Account.Builder.anAccount().withUuid(ACCOUNT_ID).withRingName(DelegateRingConstants.RING_NAME_1).build());
    persistence.save(DelegateRing.builder()
                         .ringName(DelegateRingConstants.RING_NAME_1)
                         .delegateImageTag(HARNESS_DELEGATE_RING_IMAGE)
                         .build());
  }
}
