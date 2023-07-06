/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.k8s.rancher;

import static io.harness.delegate.task.k8s.rancher.RancherKubeConfigGenerator.KUBECONFIG_GEN_ERROR_LOG_MESSAGE;
import static io.harness.rule.OwnerRule.ABHINAV2;

import static java.lang.String.format;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.exception.ngexception.RancherClientRuntimeException;
import io.harness.k8s.model.KubernetesConfig;
import io.harness.rancher.RancherConnectionHelperService;
import io.harness.rule.Owner;

import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

public class RancherKubeConfigGeneratorTest extends CategoryTest {
  @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();

  @InjectMocks @Spy RancherKubeConfigGenerator rancherKubeConfigGenerator;

  @Mock RancherConnectionHelperService rancherConnectionHelperService;

  @Test
  @Owner(developers = ABHINAV2)
  @Category(UnitTests.class)
  public void testCreateKubeconfigFailure() {
    doThrow(RuntimeException.class).when(rancherConnectionHelperService).generateKubeconfig(any(), any(), any());
    assertThatThrownBy(()
                           -> rancherKubeConfigGenerator.createKubernetesConfig(
                               RancherClusterActionDTO.builder().clusterName("cluster").clusterUrl("url").build()))
        .isInstanceOf(RancherClientRuntimeException.class)
        .hasMessageContaining(format(KUBECONFIG_GEN_ERROR_LOG_MESSAGE, "url", "cluster"));
  }

  @Test
  @Owner(developers = ABHINAV2)
  @Category(UnitTests.class)
  public void testCreateKubeconfigEmptyResponse() {
    doReturn("").when(rancherConnectionHelperService).generateKubeconfig(any(), any(), any());
    assertThatThrownBy(()
                           -> rancherKubeConfigGenerator.createKubernetesConfig(
                               RancherClusterActionDTO.builder().clusterName("cluster").clusterUrl("url").build()))
        .isInstanceOf(RancherClientRuntimeException.class)
        .hasMessageContaining(format(KUBECONFIG_GEN_ERROR_LOG_MESSAGE, "url", "cluster"));
  }

  @Test
  @Owner(developers = ABHINAV2)
  @Category(UnitTests.class)
  public void testCreateKubeconfigInvalidResponse() {
    doReturn("some: invalid, {yaml}").when(rancherConnectionHelperService).generateKubeconfig(any(), any(), any());
    assertThatThrownBy(()
                           -> rancherKubeConfigGenerator.createKubernetesConfig(
                               RancherClusterActionDTO.builder().clusterName("cluster").clusterUrl("url").build()))
        .isInstanceOf(RancherClientRuntimeException.class)
        .hasMessageContaining(format(KUBECONFIG_GEN_ERROR_LOG_MESSAGE, "url", "cluster"));
  }

  @Test
  @Owner(developers = ABHINAV2)
  @Category(UnitTests.class)
  public void testCreateKubeconfig() {
    String certAuthData = "cert-authority-data";
    String sampleToken = "sample-token";
    String masterUrl = "https://master-url.test.io";

    String sampleKubeConfig = "apiVersion: v1\n"
        + "kind: Config\n"
        + "clusters:\n"
        + "- name: \"local\"\n"
        + "  cluster:\n" + format("    server: \"%s\"\n", masterUrl)
        + format("    certificate-authority-data: \"%s\"\n", certAuthData) + "users:\n"
        + "- name: \"local\"\n"
        + "  user:\n" + format("    token: \"%s\"\n", sampleToken) + "contexts:\n"
        + "- name: \"local\"\n"
        + "  context:\n"
        + "    user: \"local\"\n"
        + "    cluster: \"local\"\n"
        + "current-context: \"local\"";

    doReturn(sampleKubeConfig).when(rancherConnectionHelperService).generateKubeconfig(any(), any(), any());
    KubernetesConfig kubernetesConfig =
        rancherKubeConfigGenerator.createKubernetesConfig(RancherClusterActionDTO.builder().namespace("ns").build());

    assertThat(String.valueOf(kubernetesConfig.getCaCert())).isEqualTo(certAuthData);
    assertThat(kubernetesConfig.getMasterUrl()).isEqualTo(masterUrl);
    assertThat(kubernetesConfig.getServiceAccountTokenSupplier().get()).isEqualTo(sampleToken);
    assertThat(kubernetesConfig.getNamespace()).isEqualTo("ns");
  }
}
