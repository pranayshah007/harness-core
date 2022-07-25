/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.batch.processing.tasklet.util;

import static io.harness.batch.processing.tasklet.util.InstanceMetaDataUtils.getInstanceCategory;
import static io.harness.rule.OwnerRule.HITESH;
import static io.harness.rule.OwnerRule.UTSAV;

import static junit.framework.TestCase.assertFalse;
import static junit.framework.TestCase.assertTrue;
import static org.assertj.core.api.Assertions.assertThat;

import io.harness.batch.processing.BatchProcessingTestBase;
import io.harness.batch.processing.writer.constants.K8sCCMConstants;
import io.harness.category.element.UnitTests;
import io.harness.ccm.commons.beans.billing.InstanceCategory;
import io.harness.ccm.commons.constants.CloudProvider;
import io.harness.ccm.commons.constants.InstanceMetaDataConstants;
import io.harness.rule.Owner;

import com.google.common.collect.ImmutableMap;
import java.util.HashMap;
import java.util.Map;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class InstanceMetaDataUtilsTest extends BatchProcessingTestBase {
  private static final String NODE_POOL_NAME = "manager-pool";

  @Test
  @Owner(developers = UTSAV)
  @Category(UnitTests.class)
  public void testMetadataUpdate() throws Exception {
    Map<String, String> fromMap = new HashMap<>();
    fromMap.put("k1", "newV1");
    fromMap.put("k2", "v2");
    fromMap.put("k3", "v3");

    Map<String, String> toMap = new HashMap<>();
    toMap.put("k1", "oldV1");
    toMap.put("k2", "v2");
    toMap.put("k4", "v4");

    assertThat(InstanceMetaDataUtils.carryUpdatedMapKeyFromTo(fromMap, toMap)).isTrue();
    assertThat(toMap).isEqualTo(ImmutableMap.of("k1", "newV1", "k2", "v2", "k3", "v3", "k4", "v4"));
  }

  @Test
  @Owner(developers = UTSAV)
  @Category(UnitTests.class)
  public void testPopulateNodePoolNameFromLabel() throws Exception {
    assertTrue(isNodePoolNameCorrect(ImmutableMap.of(K8sCCMConstants.GKE_NODE_POOL_KEY, NODE_POOL_NAME)));
    assertTrue(isNodePoolNameCorrect(ImmutableMap.of(K8sCCMConstants.AKS_NODE_POOL_KEY, NODE_POOL_NAME)));
    assertTrue(isNodePoolNameCorrect(ImmutableMap.of(K8sCCMConstants.EKSCTL_NODE_POOL_KEY, NODE_POOL_NAME)));
  }

  private static boolean isNodePoolNameCorrect(Map<String, String> labelsMap) {
    Map<String, String> metaData = new HashMap<>();

    InstanceMetaDataUtils.populateNodePoolNameFromLabel(labelsMap, metaData);
    return NODE_POOL_NAME.equals(metaData.get(InstanceMetaDataConstants.NODE_POOL_NAME));
  }

  @Test
  @Owner(developers = UTSAV)
  @Category(UnitTests.class)
  public void shouldIdentifyGCPSpotInstances() {
    assertTrue(isGCPSpotInstance(ImmutableMap.of("cloud.google.com/gke-preemptible", "true")));
    assertTrue(isGCPSpotInstance(ImmutableMap.of("preemptible", "true", "preemptible-node", "true")));
    assertTrue(isGCPSpotInstance(ImmutableMap.of("preemptible-node", "true", "preemptible", "true")));
    assertTrue(isGCPSpotInstance(ImmutableMap.of("preemptible", "true")));
    assertTrue(isGCPSpotInstance(ImmutableMap.of("preemptible-node", "true")));
  }

  @Test
  @Owner(developers = UTSAV)
  @Category(UnitTests.class)
  public void shouldIdentifyGCPOnDemandInstances() {
    assertFalse(isGCPSpotInstance(ImmutableMap.of("cloud.google.com/gke-preemptible", "false")));
    assertFalse(isGCPSpotInstance(ImmutableMap.of("preemptible", "false", "preemptible-node", "false")));
    assertFalse(isGCPSpotInstance(ImmutableMap.of("preemptible-node", "false", "preemptible", "false")));
    assertFalse(isGCPSpotInstance(ImmutableMap.of("preemptible", "false")));
    assertFalse(isGCPSpotInstance(ImmutableMap.of("preemptible-node", "false")));
    assertFalse(isGCPSpotInstance(ImmutableMap.of()));
  }

  private static boolean isGCPSpotInstance(final Map<String, String> labels) {
    final InstanceCategory instanceCategory = getInstanceCategory(CloudProvider.GCP, labels, null);
    return instanceCategory == InstanceCategory.SPOT;
  }

  @Test
  @Owner(developers = HITESH)
  @Category(UnitTests.class)
  public void shouldReturnAwsSpotInstance() {
    Map<String, String> label = new HashMap<>();
    label.put(K8sCCMConstants.REGION, InstanceMetaDataConstants.REGION);
    label.put(K8sCCMConstants.INSTANCE_FAMILY, InstanceMetaDataConstants.INSTANCE_FAMILY);
    label.put(K8sCCMConstants.OPERATING_SYSTEM, InstanceMetaDataConstants.OPERATING_SYSTEM);
    label.put(K8sCCMConstants.AWS_LIFECYCLE_KEY, "Ec2");
    label.put("kubernetes.io/lifecycle", "spot");

    InstanceCategory instanceCategory = getInstanceCategory(CloudProvider.AWS, label, null);
    assertThat(instanceCategory).isEqualTo(InstanceCategory.SPOT);
  }

  @Test
  @Owner(developers = HITESH)
  @Category(UnitTests.class)
  public void shouldReturnAwsSpotInstance2() {
    Map<String, String> label = new HashMap<>();
    label.put(K8sCCMConstants.REGION, InstanceMetaDataConstants.REGION);
    label.put(K8sCCMConstants.INSTANCE_FAMILY, InstanceMetaDataConstants.INSTANCE_FAMILY);
    label.put(K8sCCMConstants.OPERATING_SYSTEM, InstanceMetaDataConstants.OPERATING_SYSTEM);
    label.put(K8sCCMConstants.AWS_LIFECYCLE_KEY, "Ec2");
    label.put("eks.amazonaws.com/capacityType", "spot");

    InstanceCategory instanceCategory = getInstanceCategory(CloudProvider.AWS, label, null);
    assertThat(instanceCategory).isEqualTo(InstanceCategory.SPOT);
  }

  @Test
  @Owner(developers = HITESH)
  @Category(UnitTests.class)
  public void shouldReturnAzureSpotInstance() {
    Map<String, String> label = new HashMap<>();
    label.put(K8sCCMConstants.REGION, InstanceMetaDataConstants.REGION);
    label.put(K8sCCMConstants.INSTANCE_FAMILY, InstanceMetaDataConstants.INSTANCE_FAMILY);
    label.put(K8sCCMConstants.OPERATING_SYSTEM, InstanceMetaDataConstants.OPERATING_SYSTEM);
    label.put(K8sCCMConstants.AZURE_LIFECYCLE_KEY, "spot");

    InstanceCategory instanceCategory = getInstanceCategory(CloudProvider.AZURE, label, null);
    assertThat(instanceCategory).isEqualTo(InstanceCategory.SPOT);
  }

  @Test
  @Owner(developers = HITESH)
  @Category(UnitTests.class)
  public void shouldReturnAzureOndemandInstance() {
    Map<String, String> label = new HashMap<>();
    label.put(K8sCCMConstants.REGION, InstanceMetaDataConstants.REGION);
    label.put(K8sCCMConstants.INSTANCE_FAMILY, InstanceMetaDataConstants.INSTANCE_FAMILY);
    label.put(K8sCCMConstants.OPERATING_SYSTEM, InstanceMetaDataConstants.OPERATING_SYSTEM);

    InstanceCategory instanceCategory = getInstanceCategory(CloudProvider.AZURE, label, null);
    assertThat(instanceCategory).isEqualTo(InstanceCategory.ON_DEMAND);
  }

  @Test
  @Owner(developers = UTSAV)
  @Category(UnitTests.class)
  public void testSpotLabelFromPoolNameForBancoOnly() {
    InstanceCategory spotInstanceCategory =
        getInstanceCategory(CloudProvider.AWS, ImmutableMap.of("node-pool-name", "abc-spot"), "aYXZz76ETU-_3LLQSzBt1Q");
    assertThat(spotInstanceCategory).isEqualTo(InstanceCategory.SPOT);

    InstanceCategory onDemandInstanceCategory =
        getInstanceCategory(CloudProvider.AWS, ImmutableMap.of("node-pool-name", "abc-spot"), "randomName");
    assertThat(onDemandInstanceCategory).isEqualTo(InstanceCategory.ON_DEMAND);
  }
}
