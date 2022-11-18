/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.helpers.ext.k8s.request;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.category.element.UnitTests;
import io.harness.delegate.beans.executioncapability.HttpConnectionExecutionCapability;
import io.harness.delegate.beans.executioncapability.SelectorCapability;
import io.harness.delegate.task.mixin.HttpConnectionExecutionCapabilityGenerator;
import io.harness.rule.Owner;
import io.harness.rule.OwnerRule;

import software.wings.WingsBaseTest;
import software.wings.beans.AzureConfig;
import software.wings.beans.GcpConfig;
import software.wings.beans.KubernetesClusterConfig;
import software.wings.beans.RancherConfig;
import software.wings.beans.SettingAttribute;
import software.wings.delegatetasks.validation.capabilities.ClusterMasterUrlValidationCapability;
import software.wings.service.impl.ContainerServiceParams;

import java.util.Collections;
import java.util.HashSet;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class K8sClusterConfigTest extends WingsBaseTest {
  private static final String MASTER_URL = "http://a.b.c";

  @Test
  @Owner(developers = {OwnerRule.YOGESH, OwnerRule.ACASIAN})
  @Category(UnitTests.class)
  public void fetchRequiredExecutionCapabilities() {
    testGcpConfig();
    testAzureConfig();
    testKubernetesConfig();
    testKubernetesConfigWithDelegate();
    testContainerServiceParamsNoMasterUrl("");
    testContainerServiceParamsNoMasterUrl(null);
    testContainerServiceParamsWithMasterUrl();
  }

  @Test
  @Owner(developers = OwnerRule.SHUBHAM_MAHESHWARI)
  @Category(UnitTests.class)
  public void testRancherConfig() {
    K8sClusterConfig clusterConfig =
        K8sClusterConfig.builder().cloudProvider(RancherConfig.builder().rancherUrl(MASTER_URL).build()).build();

    assertThat(clusterConfig.fetchRequiredExecutionCapabilities(null))
        .containsExactlyInAnyOrder(
            HttpConnectionExecutionCapability.builder().host("a.b.c").port(-1).scheme("http").build());
  }

  private void testKubernetesConfigWithDelegate() {
    K8sClusterConfig clusterConfig =
        K8sClusterConfig.builder()
            .cloudProvider(KubernetesClusterConfig.builder()
                               .delegateSelectors(new HashSet<>(Collections.singletonList("delegateSelectors")))
                               .useKubernetesDelegate(true)
                               .build())
            .build();

    assertThat(clusterConfig.fetchRequiredExecutionCapabilities(null))
        .containsExactlyInAnyOrder(SelectorCapability.builder()
                                       .selectors(new HashSet<>(Collections.singletonList("delegateSelectors")))
                                       .build());
  }

  private void testKubernetesConfig() {
    K8sClusterConfig clusterConfig = K8sClusterConfig.builder()
                                         .cloudProvider(KubernetesClusterConfig.builder().masterUrl(MASTER_URL).build())
                                         .build();

    assertThat(clusterConfig.fetchRequiredExecutionCapabilities(null))
        .containsExactlyInAnyOrder(
            HttpConnectionExecutionCapability.builder().host("a.b.c").port(-1).scheme("http").build());
  }

  private void testContainerServiceParamsNoMasterUrl(String masterUrl) {
    KubernetesClusterConfig clusterConfig = KubernetesClusterConfig.builder().masterUrl("http://localhost").build();
    ContainerServiceParams params =
        ContainerServiceParams.builder()
            .settingAttribute(SettingAttribute.Builder.aSettingAttribute().withValue(clusterConfig).build().toDTO())
            .masterUrl(masterUrl)
            .build();

    assertThat(params.fetchRequiredExecutionCapabilities(null))
        .containsExactlyInAnyOrder(
            ClusterMasterUrlValidationCapability.builder().containerServiceParams(params).build());
  }

  private void testContainerServiceParamsWithMasterUrl() {
    KubernetesClusterConfig clusterConfig = KubernetesClusterConfig.builder().masterUrl("http://localhost").build();
    ContainerServiceParams params =
        ContainerServiceParams.builder()
            .settingAttribute(SettingAttribute.Builder.aSettingAttribute().withValue(clusterConfig).build().toDTO())
            .masterUrl("http://masterUrl")
            .build();

    assertThat(params.fetchRequiredExecutionCapabilities(null))
        .containsExactlyInAnyOrder(HttpConnectionExecutionCapabilityGenerator.buildHttpConnectionExecutionCapability(
            "http://masterUrl", null));
  }

  private void testGcpConfig() {
    K8sClusterConfig clusterConfig =
        K8sClusterConfig.builder().masterUrl(MASTER_URL).cloudProvider(GcpConfig.builder().build()).build();

    assertThat(clusterConfig.fetchRequiredExecutionCapabilities(null))
        .containsExactlyInAnyOrder(
            HttpConnectionExecutionCapability.builder().host("a.b.c").port(-1).scheme("http").build());
  }

  private void testAzureConfig() {
    K8sClusterConfig clusterConfig =
        K8sClusterConfig.builder().masterUrl(MASTER_URL).cloudProvider(AzureConfig.builder().build()).build();

    assertThat(clusterConfig.fetchRequiredExecutionCapabilities(null))
        .containsExactlyInAnyOrder(
            HttpConnectionExecutionCapability.builder().host("a.b.c").port(-1).scheme("http").build());
  }
}
