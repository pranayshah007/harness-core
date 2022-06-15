/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.service;

import static io.harness.rule.OwnerRule.ARPIT;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.delegate.beans.DelegateType;
import io.harness.delegate.beans.UpgradeCheckResult;
import io.harness.delegate.service.impl.DelegateUpgraderServiceImpl;
import io.harness.rule.Owner;

import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

@OwnedBy(HarnessTeam.DEL)
@RunWith(MockitoJUnitRunner.class)
public class DelegateUpgraderServiceTest {
  private static final String TEST_ACCOUNT_ID1 = "accountId1";
  private static final String LATEST_DELEGATE_IMAGE_TAG = "harness/delegate:latest";
  private static final String LATEST_UPGRADER_IMAGE_TAG = "harness/upgrader:latest";
  private static final String DELEGATE_IMAGE_TAG_1 = "harness/delegate:1";
  private static final String UPGRADER_IMAGE_TAG_1 = "harness/upgrader:1";

  private DelegateUpgraderServiceImpl underTest;

  @Mock private DelegateVersionService delegateVersionService;

  @Before
  public void setUp() {
    underTest = new DelegateUpgraderServiceImpl(delegateVersionService);
  }

  @Test
  @Owner(developers = ARPIT)
  @Category(UnitTests.class)
  public void shouldUpgradeDelegateImageTag() {
    when(delegateVersionService.getDelegateImageTag(TEST_ACCOUNT_ID1, DelegateType.KUBERNETES))
        .thenReturn(LATEST_DELEGATE_IMAGE_TAG);
    UpgradeCheckResult upgradeCheckResult1 = underTest.getDelegateImageTag(TEST_ACCOUNT_ID1, DELEGATE_IMAGE_TAG_1);

    assertThat(upgradeCheckResult1.isShouldUpgrade()).isTrue();
    assertThat(upgradeCheckResult1.getImageTag()).isEqualTo(LATEST_DELEGATE_IMAGE_TAG);
  }

  @Test
  @Owner(developers = ARPIT)
  @Category(UnitTests.class)
  public void shouldNotUpgradeDelegateImageTag() {
    when(delegateVersionService.getDelegateImageTag(TEST_ACCOUNT_ID1, DelegateType.KUBERNETES))
        .thenReturn(DELEGATE_IMAGE_TAG_1);
    UpgradeCheckResult upgradeCheckResult1 = underTest.getDelegateImageTag(TEST_ACCOUNT_ID1, DELEGATE_IMAGE_TAG_1);

    assertThat(upgradeCheckResult1.isShouldUpgrade()).isFalse();
    assertThat(upgradeCheckResult1.getImageTag()).isEqualTo(DELEGATE_IMAGE_TAG_1);
  }

  @Test
  @Owner(developers = ARPIT)
  @Category(UnitTests.class)
  public void shouldUpgradeUpgraderImageTag() {
    when(delegateVersionService.getUpgraderImageTag(TEST_ACCOUNT_ID1, DelegateType.KUBERNETES))
        .thenReturn(LATEST_UPGRADER_IMAGE_TAG);
    UpgradeCheckResult upgradeCheckResult1 = underTest.getUpgraderImageTag(TEST_ACCOUNT_ID1, UPGRADER_IMAGE_TAG_1);

    assertThat(upgradeCheckResult1.isShouldUpgrade()).isTrue();
    assertThat(upgradeCheckResult1.getImageTag()).isEqualTo(LATEST_UPGRADER_IMAGE_TAG);
  }

  @Test
  @Owner(developers = ARPIT)
  @Category(UnitTests.class)
  public void shouldNotUpgradeUpgraderImageTag() {
    when(delegateVersionService.getUpgraderImageTag(TEST_ACCOUNT_ID1, DelegateType.KUBERNETES))
        .thenReturn(UPGRADER_IMAGE_TAG_1);
    UpgradeCheckResult upgradeCheckResult1 = underTest.getUpgraderImageTag(TEST_ACCOUNT_ID1, UPGRADER_IMAGE_TAG_1);

    assertThat(upgradeCheckResult1.isShouldUpgrade()).isFalse();
    assertThat(upgradeCheckResult1.getImageTag()).isEqualTo(UPGRADER_IMAGE_TAG_1);
  }
}
