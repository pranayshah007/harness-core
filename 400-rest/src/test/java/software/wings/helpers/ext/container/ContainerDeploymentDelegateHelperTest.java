/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.helpers.ext.container;

import static io.harness.k8s.model.KubernetesClusterAuthType.OIDC;
import static io.harness.rule.OwnerRule.ACASIAN;
import static io.harness.rule.OwnerRule.ADWAIT;
import static io.harness.rule.OwnerRule.ANSHUL;
import static io.harness.rule.OwnerRule.SHUBHAM_MAHESHWARI;

import static java.util.Collections.emptyList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.harness.category.element.UnitTests;
import io.harness.delegate.task.k8s.ContainerDeploymentDelegateBaseHelper;
import io.harness.exception.InvalidRequestException;
import io.harness.k8s.KubernetesContainerService;
import io.harness.k8s.model.KubernetesConfig;
import io.harness.k8s.model.OidcGrantType;
import io.harness.k8s.oidc.OidcTokenRetriever;
import io.harness.logging.LogCallback;
import io.harness.logging.LogLevel;
import io.harness.rule.Owner;

import software.wings.WingsBaseTest;
import software.wings.beans.KubernetesClusterConfig;
import software.wings.beans.RancherConfig;
import software.wings.beans.SettingAttribute;
import software.wings.beans.command.ExecutionLogCallback;
import software.wings.cloudprovider.gke.GkeClusterService;
import software.wings.delegatetasks.rancher.RancherTaskHelper;
import software.wings.helpers.ext.k8s.request.K8sClusterConfig;
import software.wings.service.impl.ContainerServiceParams;
import software.wings.service.intfc.security.EncryptionService;

import com.github.scribejava.apis.openid.OpenIdOAuth2AccessToken;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;

public class ContainerDeploymentDelegateHelperTest extends WingsBaseTest {
  @Mock private KubernetesContainerService kubernetesContainerService;
  @Mock LogCallback logCallback;
  @Mock private EncryptionService encryptionService;
  @Mock private RancherTaskHelper rancherTaskHelper;
  @Mock private GkeClusterService gkeClusterService;
  @Spy @InjectMocks private OidcTokenRetriever oidcTokenRetriever;
  @Spy @InjectMocks ContainerDeploymentDelegateBaseHelper containerDeploymentDelegateBaseHelper;
  @Spy @InjectMocks ContainerDeploymentDelegateHelper containerDeploymentDelegateHelper;

  @Before
  public void setup() {
    doNothing().when(logCallback).saveExecutionLog(anyString());
    doNothing().when(logCallback).saveExecutionLog(anyString(), any(LogLevel.class));
  }

  @Test
  @Owner(developers = ADWAIT)
  @Category(UnitTests.class)
  public void testGetConfigFileContent() throws Exception {
    OpenIdOAuth2AccessToken accessToken = mock(OpenIdOAuth2AccessToken.class);
    doReturn("id_token").when(accessToken).getOpenIdToken();
    doReturn(3600).when(accessToken).getExpiresIn();
    doReturn("bearer").when(accessToken).getTokenType();
    doReturn("refresh_token").when(accessToken).getRefreshToken();

    doReturn(accessToken).when(oidcTokenRetriever).getAccessToken(any());

    KubernetesClusterConfig clusterConfig = KubernetesClusterConfig.builder()
                                                .accountId("accId")
                                                .authType(OIDC)
                                                .oidcClientId("clientId".toCharArray())
                                                .oidcGrantType(OidcGrantType.password)
                                                .oidcIdentityProviderUrl("url")
                                                .oidcPassword("pwd".toCharArray())
                                                .oidcUsername("user")
                                                .oidcSecret("secret".toCharArray())
                                                .masterUrl("masterUrl")
                                                .build();

    // Test generating KubernetesConfig from KubernetesClusterConfig
    KubernetesConfig kubeConfig = clusterConfig.createKubernetesConfig("namespace");
    assertThat(kubeConfig)
        .isEqualTo(KubernetesConfig.builder()
                       .masterUrl("masterUrl")
                       .namespace("namespace")
                       .accountId("accId")
                       .authType(OIDC)
                       .oidcIdentityProviderUrl("url")
                       .oidcUsername("user")
                       .oidcGrantType(OidcGrantType.password)
                       .oidcPassword("pwd".toCharArray())
                       .oidcClientId("clientId".toCharArray())
                       .oidcSecret("secret".toCharArray())
                       .build());
  }

  @Test
  @Owner(developers = ACASIAN)
  @Category(UnitTests.class)
  public void k8sVersionIsGreaterOrEqualTo116() {
    KubernetesClusterConfig kubernetesClusterConfig = KubernetesClusterConfig.builder().build();
    KubernetesConfig kubernetesConfig = KubernetesConfig.builder().namespace("default").build();
    ContainerServiceParams containerServiceParams =
        ContainerServiceParams.builder()
            .settingAttribute(
                SettingAttribute.Builder.aSettingAttribute().withValue(kubernetesClusterConfig).build().toDTO())
            .build();
    String version = "1.16";

    doReturn(kubernetesConfig).when(containerDeploymentDelegateHelper).getKubernetesConfig(containerServiceParams);
    doReturn(version).when(kubernetesContainerService).getVersionAsString(kubernetesConfig);

    boolean result = containerDeploymentDelegateHelper.useK8sSteadyStateCheck(
        true, containerServiceParams, new ExecutionLogCallback());
    assertThat(result).isTrue();
  }

  @Test
  @Owner(developers = ACASIAN)
  @Category(UnitTests.class)
  public void k8sVersionIsGreaterOrEqualTo116WithCharacter() {
    KubernetesConfig kubernetesConfig = KubernetesConfig.builder().namespace("default").build();
    ContainerServiceParams containerServiceParams =
        ContainerServiceParams.builder()
            .settingAttribute(SettingAttribute.Builder.aSettingAttribute()
                                  .withValue(KubernetesClusterConfig.builder().build())
                                  .build()
                                  .toDTO())
            .encryptionDetails(Collections.emptyList())
            .build();
    String version = "1.16+144";

    doReturn(kubernetesConfig).when(containerDeploymentDelegateHelper).getKubernetesConfig(containerServiceParams);
    doReturn(version).when(kubernetesContainerService).getVersionAsString(kubernetesConfig);

    boolean result = containerDeploymentDelegateHelper.useK8sSteadyStateCheck(
        true, containerServiceParams, new ExecutionLogCallback());
    assertThat(result).isTrue();
  }

  @Test
  @Owner(developers = ACASIAN)
  @Category(UnitTests.class)
  public void k8sVersionIsLessThan116() {
    KubernetesConfig kubernetesConfig = KubernetesConfig.builder().namespace("default").build();
    ContainerServiceParams containerServiceParams =
        ContainerServiceParams.builder()
            .settingAttribute(SettingAttribute.Builder.aSettingAttribute()
                                  .withValue(KubernetesClusterConfig.builder().build())
                                  .build()
                                  .toDTO())
            .encryptionDetails(Collections.emptyList())
            .build();
    String version = "1.15";

    doReturn(kubernetesConfig).when(containerDeploymentDelegateHelper).getKubernetesConfig(containerServiceParams);
    doReturn(version).when(kubernetesContainerService).getVersionAsString(kubernetesConfig);

    boolean result = containerDeploymentDelegateHelper.useK8sSteadyStateCheck(
        true, containerServiceParams, new ExecutionLogCallback());
    assertThat(result).isFalse();
  }

  @Test
  @Owner(developers = ANSHUL)
  @Category(UnitTests.class)
  public void testIsK8sVersion116OrAboveWithFeatureFlagDisabled() {
    boolean result = containerDeploymentDelegateHelper.useK8sSteadyStateCheck(
        false, ContainerServiceParams.builder().build(), new ExecutionLogCallback());
    assertThat(result).isFalse();
  }

  @Test
  @Owner(developers = ANSHUL)
  @Category(UnitTests.class)
  public void testGetKubernetesConfig() {
    K8sClusterConfig k8sClusterConfig =
        K8sClusterConfig.builder()
            .cloudProvider(KubernetesClusterConfig.builder().masterUrl("https://example.com").build())
            .cloudProviderEncryptionDetails(emptyList())
            .build();

    KubernetesConfig kubernetesConfig = containerDeploymentDelegateHelper.getKubernetesConfig(k8sClusterConfig, false);
    assertThat(kubernetesConfig.getMasterUrl()).isEqualTo("https://example.com");
  }

  @Test
  @Owner(developers = SHUBHAM_MAHESHWARI)
  @Category(UnitTests.class)
  public void testGetKubernetesConfigWithRancher() throws IOException {
    RancherConfig rancherConfig = RancherConfig.builder().rancherUrl("sampleRancherUrl").build();
    K8sClusterConfig k8sClusterConfig = K8sClusterConfig.builder()
                                            .cloudProvider(rancherConfig)
                                            .cloudProviderEncryptionDetails(emptyList())
                                            .clusterName("sampleCluster")
                                            .namespace("sampleNamespace")
                                            .build();

    doReturn(mock(KubernetesConfig.class))
        .when(rancherTaskHelper)
        .createKubeconfig(any(RancherConfig.class), anyList(), anyString(), anyString());
    KubernetesConfig kubernetesConfig = containerDeploymentDelegateHelper.getKubernetesConfig(k8sClusterConfig, false);
    verify(rancherTaskHelper, times(1))
        .createKubeconfig(rancherConfig, Collections.emptyList(), "sampleCluster", "sampleNamespace");
  }

  @Test(expected = InvalidRequestException.class)
  @Owner(developers = SHUBHAM_MAHESHWARI)
  @Category(UnitTests.class)
  public void testGetKubernetesConfigWithRancherWithException() throws IOException {
    RancherConfig rancherConfig = RancherConfig.builder().rancherUrl("sampleRancherUrl").build();
    K8sClusterConfig k8sClusterConfig = K8sClusterConfig.builder()
                                            .cloudProvider(rancherConfig)
                                            .cloudProviderEncryptionDetails(emptyList())
                                            .clusterName("sampleCluster")
                                            .namespace("sampleNamespace")
                                            .build();

    doThrow(new RuntimeException("some exception message"))
        .when(rancherTaskHelper)
        .createKubeconfig(any(RancherConfig.class), anyList(), anyString(), anyString());
    KubernetesConfig kubernetesConfig = containerDeploymentDelegateHelper.getKubernetesConfig(k8sClusterConfig, false);
  }

  @Test(expected = InvalidRequestException.class)
  @Owner(developers = SHUBHAM_MAHESHWARI)
  @Category(UnitTests.class)
  public void testGetKubernetesConfigForContainerParamsWithRancherWithException() throws IOException {
    RancherConfig rancherConfig = RancherConfig.builder().rancherUrl("sampleRancherUrl").build();
    software.wings.beans.dto.SettingAttribute settingAttribute = mock(software.wings.beans.dto.SettingAttribute.class);
    ContainerServiceParams params = ContainerServiceParams.builder()
                                        .settingAttribute(settingAttribute)
                                        .clusterName("sampleCluster")
                                        .namespace("sampleNamespace")
                                        .build();

    doReturn(rancherConfig).when(settingAttribute).getValue();
    doThrow(new RuntimeException("some exception message"))
        .when(rancherTaskHelper)
        .createKubeconfig(any(RancherConfig.class), nullable(List.class), anyString(), anyString());
    KubernetesConfig kubernetesConfig = containerDeploymentDelegateHelper.getKubernetesConfig(params);
  }

  @Test
  @Owner(developers = SHUBHAM_MAHESHWARI)
  @Category(UnitTests.class)
  public void testGetKubernetesConfigForContainerParamsWithRancher() throws IOException {
    RancherConfig rancherConfig = RancherConfig.builder().rancherUrl("sampleRancherUrl").build();
    software.wings.beans.dto.SettingAttribute settingAttribute = mock(software.wings.beans.dto.SettingAttribute.class);
    ContainerServiceParams params = ContainerServiceParams.builder()
                                        .settingAttribute(settingAttribute)
                                        .clusterName("sampleCluster")
                                        .namespace("sampleNamespace")
                                        .build();

    doReturn(rancherConfig).when(settingAttribute).getValue();
    doReturn(mock(KubernetesConfig.class))
        .when(rancherTaskHelper)
        .createKubeconfig(any(RancherConfig.class), anyList(), anyString(), anyString());
    KubernetesConfig kubernetesConfig = containerDeploymentDelegateHelper.getKubernetesConfig(params);
    verify(rancherTaskHelper, times(1)).createKubeconfig(rancherConfig, null, "sampleCluster", "sampleNamespace");
  }
}
