/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.batch.processing.pricing.data;

import static io.harness.rule.OwnerRule.HITESH;
import static io.harness.rule.OwnerRule.UTSAV;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.ccm.commons.constants.CloudProvider;
import io.harness.rule.Owner;

import org.junit.Test;
import org.junit.experimental.categories.Category;

public class CloudProviderTest extends CategoryTest {
  @Test
  @Owner(developers = HITESH)
  @Category(UnitTests.class)
  public void testGetCloudProviderName() {
    CloudProvider cloudProvider = CloudProvider.GCP;
    assertThat(cloudProvider.getCloudProviderName()).isNotEmpty().isEqualTo("google");
  }

  @Test
  @Owner(developers = UTSAV)
  @Category(UnitTests.class)
  public void testGetK8sService() {
    CloudProvider cloudProvider = CloudProvider.GCP;
    assertThat(cloudProvider.getK8sService()).isNotEmpty().isEqualTo("gke");
  }

  @Test
  @Owner(developers = UTSAV)
  @Category(UnitTests.class)
  public void testValueOfNullCloudProvider() {
    CloudProvider cloudProvider = CloudProvider.fromCloudProviderName(null);
    assertThat(cloudProvider).isEqualTo(CloudProvider.UNKNOWN);
  }

  @Test
  @Owner(developers = UTSAV)
  @Category(UnitTests.class)
  public void testParseCloudProvider() {
    CloudProvider cloudProvider = CloudProvider.fromCloudProviderName("GCP");
    assertThat(cloudProvider).isEqualTo(CloudProvider.GCP);
  }
}
