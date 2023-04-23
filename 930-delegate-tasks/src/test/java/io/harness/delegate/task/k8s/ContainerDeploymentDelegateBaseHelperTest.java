/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.task.k8s;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.rule.OwnerRule.ABOSII;
import static io.harness.rule.OwnerRule.ACASIAN;
import static io.harness.rule.OwnerRule.ANSHUL;
import static io.harness.rule.OwnerRule.LOVISH_BANSAL;
import static io.harness.rule.OwnerRule.MLUKIC;
import static io.harness.rule.OwnerRule.YOGESH;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.container.ContainerInfo;
import io.harness.delegate.beans.azure.AzureConfigContext;
import io.harness.delegate.beans.connector.awsconnector.AwsConnectorDTO;
import io.harness.delegate.beans.connector.awsconnector.AwsCredentialDTO;
import io.harness.delegate.beans.connector.azureconnector.AzureConnectorDTO;
import io.harness.delegate.beans.connector.gcpconnector.GcpConnectorCredentialDTO;
import io.harness.delegate.beans.connector.gcpconnector.GcpConnectorDTO;
import io.harness.delegate.beans.connector.gcpconnector.GcpCredentialType;
import io.harness.delegate.beans.connector.gcpconnector.GcpManualDetailsDTO;
import io.harness.delegate.beans.connector.k8Connector.KubernetesAuthCredentialDTO;
import io.harness.delegate.beans.connector.k8Connector.KubernetesAuthDTO;
import io.harness.delegate.beans.connector.k8Connector.KubernetesClusterConfigDTO;
import io.harness.delegate.beans.connector.k8Connector.KubernetesClusterDetailsDTO;
import io.harness.delegate.beans.connector.k8Connector.KubernetesCredentialDTO;
import io.harness.delegate.beans.connector.k8Connector.KubernetesCredentialType;
import io.harness.delegate.beans.connector.k8Connector.KubernetesUserNamePasswordDTO;
import io.harness.delegate.task.aws.eks.AwsEKSV2DelegateTaskHelper;
import io.harness.delegate.task.gcp.helpers.GkeClusterHelper;
import io.harness.encryption.SecretRefData;
import io.harness.exception.InvalidRequestException;
import io.harness.k8s.KubernetesContainerService;
import io.harness.k8s.model.KubernetesConfig;
import io.harness.k8s.model.kubeconfig.KubeConfigAuthPluginHelper;
import io.harness.logging.LogCallback;
import io.harness.logging.LogLevel;
import io.harness.rule.Owner;
import io.harness.security.encryption.EncryptedDataDetail;
import io.harness.security.encryption.SecretDecryptionService;

import software.wings.delegatetasks.azure.AzureAsyncTaskHelper;

import com.google.common.collect.ImmutableMap;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.extensions.DaemonSet;
import io.fabric8.kubernetes.api.model.extensions.Deployment;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.Spy;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

@OwnedBy(CDP)
public class ContainerDeploymentDelegateBaseHelperTest extends CategoryTest {
  @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();
  @Mock private KubernetesContainerService kubernetesContainerService;
  @Mock private GkeClusterHelper gkeClusterHelper;
  @Mock private K8sYamlToDelegateDTOMapper k8sYamlToDelegateDTOMapper;
  @Mock private SecretDecryptionService secretDecryptionService;
  @Mock private AzureAsyncTaskHelper azureAsyncTaskHelper;
  @Mock LogCallback logCallback;
  @Mock private AwsEKSV2DelegateTaskHelper awsEKSDelegateTaskHelper;

  @Spy @InjectMocks ContainerDeploymentDelegateBaseHelper containerDeploymentDelegateBaseHelper;
  private static final String WORK_DIR = "./repository/k8s";

  @Before
  public void setup() {
    doNothing().when(logCallback).saveExecutionLog(anyString());
    doNothing().when(logCallback).saveExecutionLog(anyString(), any(LogLevel.class));
  }

  @Test
  @Owner(developers = YOGESH)
  @Category(UnitTests.class)
  public void testGetContainerInfosWhenReadyByLabels() {
    KubernetesConfig kubernetesConfig = KubernetesConfig.builder().namespace("default").build();
    List<Pod> existingPods = asList(new Pod());
    List<? extends HasMetadata> controllers = getMockedControllers();

    doReturn(controllers).when(kubernetesContainerService).getControllers(any(KubernetesConfig.class), anyMap());

    containerDeploymentDelegateBaseHelper.getContainerInfosWhenReadyByLabels(
        kubernetesConfig, logCallback, ImmutableMap.of("name", "value"), existingPods);

    verify(kubernetesContainerService, times(1))
        .getContainerInfosWhenReady(
            kubernetesConfig, "deployment-name", 0, -1, 30, existingPods, false, logCallback, true, 0, "default");
    verify(kubernetesContainerService, times(1))
        .getContainerInfosWhenReady(
            kubernetesConfig, "daemonSet-name", 0, -1, 30, existingPods, true, logCallback, true, 0, "default");
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testGetContainerInfosWhenReadyByLabelsEmptyControllers() {
    KubernetesConfig kubernetesConfig = KubernetesConfig.builder().namespace("default").build();
    List<Pod> existingPods = asList(new Pod());
    List<? extends HasMetadata> controllers = emptyList();

    doReturn(controllers).when(kubernetesContainerService).getControllers(any(KubernetesConfig.class), anyMap());

    List<ContainerInfo> result = containerDeploymentDelegateBaseHelper.getContainerInfosWhenReadyByLabels(
        kubernetesConfig, logCallback, ImmutableMap.of("name", "value"), existingPods);
    assertThat(result).isEmpty();
  }

  @Test
  @Owner(developers = ACASIAN)
  @Category(UnitTests.class)
  public void testCreateDirectKubernetesConfig() {
    KubernetesClusterConfigDTO clusterConfigDTO =
        KubernetesClusterConfigDTO.builder()
            .credential(KubernetesCredentialDTO.builder()
                            .kubernetesCredentialType(KubernetesCredentialType.INHERIT_FROM_DELEGATE)
                            .build())
            .build();
    DirectK8sInfraDelegateConfig config = DirectK8sInfraDelegateConfig.builder()
                                              .namespace("default")
                                              .kubernetesClusterConfigDTO(clusterConfigDTO)
                                              .build();

    KubernetesConfig expectedKubernetesConfig = KubernetesConfig.builder().username("test".toCharArray()).build();
    doReturn(expectedKubernetesConfig)
        .when(k8sYamlToDelegateDTOMapper)
        .createKubernetesConfigFromClusterConfig(eq(clusterConfigDTO), eq("default"));

    KubernetesConfig kubernetesConfig =
        containerDeploymentDelegateBaseHelper.createKubernetesConfig(config, WORK_DIR, null);
    assertThat(kubernetesConfig).isEqualTo(expectedKubernetesConfig);

    verify(k8sYamlToDelegateDTOMapper, times(1))
        .createKubernetesConfigFromClusterConfig(eq(clusterConfigDTO), eq("default"));
  }

  @Test
  @Owner(developers = ACASIAN)
  @Category(UnitTests.class)
  public void testCreateGcpManualCredentialsKubernetesConfig() {
    char[] secret = "secret".toCharArray();
    GcpK8sInfraDelegateConfig config =
        GcpK8sInfraDelegateConfig.builder()
            .cluster("cluster1")
            .namespace("default")
            .gcpConnectorDTO(
                GcpConnectorDTO.builder()
                    .credential(GcpConnectorCredentialDTO.builder()
                                    .gcpCredentialType(GcpCredentialType.MANUAL_CREDENTIALS)
                                    .config(GcpManualDetailsDTO.builder()
                                                .secretKeyRef(SecretRefData.builder().decryptedValue(secret).build())
                                                .build())
                                    .build())
                    .build())
            .build();

    MockedStatic kubeConfigAuthPluginHelper = mockStatic(KubeConfigAuthPluginHelper.class);
    Mockito.when(KubeConfigAuthPluginHelper.isExecAuthPluginBinaryAvailable(any(), any())).thenReturn(true);
    KubernetesConfig expectedKubernetesConfig = KubernetesConfig.builder().password(secret).build();
    doReturn(expectedKubernetesConfig)
        .when(gkeClusterHelper)
        .getCluster(eq(secret), eq(false), eq("cluster1"), eq("default"), any(LogCallback.class));

    KubernetesConfig kubernetesConfig =
        containerDeploymentDelegateBaseHelper.createKubernetesConfig(config, WORK_DIR, logCallback);
    kubeConfigAuthPluginHelper.close();
    assertThat(kubernetesConfig).isEqualTo(expectedKubernetesConfig);

    verify(gkeClusterHelper, times(1))
        .getCluster(eq(secret), eq(false), eq("cluster1"), eq("default"), any(LogCallback.class));
  }

  @Test
  @Owner(developers = ACASIAN)
  @Category(UnitTests.class)
  public void testCreateGcpInheritFromDelegateKubernetesConfig() {
    GcpK8sInfraDelegateConfig config =
        GcpK8sInfraDelegateConfig.builder()
            .cluster("cluster1")
            .namespace("default")
            .gcpConnectorDTO(GcpConnectorDTO.builder()
                                 .credential(GcpConnectorCredentialDTO.builder()
                                                 .gcpCredentialType(GcpCredentialType.INHERIT_FROM_DELEGATE)
                                                 .build())
                                 .build())
            .build();

    MockedStatic kubeConfigAuthPluginHelper = mockStatic(KubeConfigAuthPluginHelper.class);
    Mockito.when(KubeConfigAuthPluginHelper.isExecAuthPluginBinaryAvailable(any(), any())).thenReturn(true);

    KubernetesConfig expectedKubernetesConfig = KubernetesConfig.builder().username("test".toCharArray()).build();
    doReturn(expectedKubernetesConfig)
        .when(gkeClusterHelper)
        .getCluster(eq(null), eq(true), eq("cluster1"), eq("default"), any(LogCallback.class));

    KubernetesConfig kubernetesConfig =
        containerDeploymentDelegateBaseHelper.createKubernetesConfig(config, WORK_DIR, logCallback);
    kubeConfigAuthPluginHelper.close();
    assertThat(kubernetesConfig).isEqualTo(expectedKubernetesConfig);

    verify(gkeClusterHelper, times(1))
        .getCluster(eq(null), eq(true), eq("cluster1"), eq("default"), any(LogCallback.class));
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testCreateKubernetesConfigUnknownType() {
    K8sInfraDelegateConfig k8sInfraDelegateConfig = mock(K8sInfraDelegateConfig.class);

    assertThatThrownBy(
        () -> containerDeploymentDelegateBaseHelper.createKubernetesConfig(k8sInfraDelegateConfig, WORK_DIR, null))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("Unhandled K8sInfraDelegateConfig ");
  }

  private List<? extends HasMetadata> getMockedControllers() {
    HasMetadata controller_1 = mock(Deployment.class);
    HasMetadata controller_2 = mock(DaemonSet.class);
    ObjectMeta metaData_1 = mock(ObjectMeta.class);
    ObjectMeta metaData_2 = mock(ObjectMeta.class);
    when(controller_1.getKind()).thenReturn("Deployment");
    when(controller_2.getKind()).thenReturn("DaemonSet");
    when(controller_1.getMetadata()).thenReturn(metaData_1);
    when(controller_2.getMetadata()).thenReturn(metaData_2);
    when(metaData_1.getName()).thenReturn("deployment-name");
    when(metaData_2.getName()).thenReturn("daemonSet-name");
    return asList(controller_1, controller_2);
  }

  @Test
  @Owner(developers = ANSHUL)
  @Category(UnitTests.class)
  public void testGetControllerCountByLabels() {
    KubernetesConfig kubernetesConfig = KubernetesConfig.builder().namespace("default").build();
    Map<String, String> labels = new HashMap<>();

    List<? extends HasMetadata> controllers = getMockedControllers();
    doReturn(controllers).when(kubernetesContainerService).getControllers(any(KubernetesConfig.class), anyMap());
    assertThat(containerDeploymentDelegateBaseHelper.getControllerCountByLabels(kubernetesConfig, labels)).isEqualTo(2);
  }

  @Test
  @Owner(developers = YOGESH)
  @Category(UnitTests.class)
  public void getExistingPodsByLabels() {
    KubernetesConfig kubernetesConfig = KubernetesConfig.builder().namespace("default").build();
    Map<String, String> labels = new HashMap<>();

    when(kubernetesContainerService.getPods(kubernetesConfig, labels)).thenReturn(asList(new Pod()));

    final List<Pod> pods = containerDeploymentDelegateBaseHelper.getExistingPodsByLabels(kubernetesConfig, labels);
    assertThat(pods).hasSize(1);
    verify(kubernetesContainerService, times(1)).getPods(kubernetesConfig, labels);
  }

  @Test
  @Owner(developers = YOGESH)
  @Category(UnitTests.class)
  public void testGetContainerInfosWhenReadyByLabel() {
    KubernetesConfig kubernetesConfig = mock(KubernetesConfig.class);
    List<Pod> existingPods = asList(new Pod());

    when(kubernetesContainerService.getPods(eq(kubernetesConfig), anyMap())).thenReturn(existingPods);
    doReturn(null)
        .when(containerDeploymentDelegateBaseHelper)
        .getContainerInfosWhenReadyByLabels(any(KubernetesConfig.class), any(LogCallback.class), anyMap(), anyList());

    containerDeploymentDelegateBaseHelper.getContainerInfosWhenReadyByLabels(
        kubernetesConfig, logCallback, ImmutableMap.of("name", "value"), existingPods);

    verify(containerDeploymentDelegateBaseHelper, times(1))
        .getContainerInfosWhenReadyByLabels(
            kubernetesConfig, logCallback, ImmutableMap.of("name", "value"), existingPods);
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testGetKubeconfigFileContentDirectK8sInfraDelegateConfig() {
    final List<EncryptedDataDetail> encryptionDataDetails = emptyList();
    final KubernetesAuthCredentialDTO credentials = KubernetesUserNamePasswordDTO.builder().build();
    final KubernetesClusterConfigDTO kubernetesClusterConfig =
        KubernetesClusterConfigDTO.builder()
            .credential(KubernetesCredentialDTO.builder()
                            .config(KubernetesClusterDetailsDTO.builder()
                                        .auth(KubernetesAuthDTO.builder().credentials(credentials).build())
                                        .build())
                            .kubernetesCredentialType(KubernetesCredentialType.MANUAL_CREDENTIALS)
                            .build())
            .build();
    final DirectK8sInfraDelegateConfig k8sInfraDelegateConfig = DirectK8sInfraDelegateConfig.builder()
                                                                    .kubernetesClusterConfigDTO(kubernetesClusterConfig)
                                                                    .encryptionDataDetails(encryptionDataDetails)
                                                                    .namespace("default")
                                                                    .build();
    final KubernetesConfig kubernetesConfig = KubernetesConfig.builder().build();

    doReturn(kubernetesConfig)
        .when(k8sYamlToDelegateDTOMapper)
        .createKubernetesConfigFromClusterConfig(kubernetesClusterConfig, "default");

    containerDeploymentDelegateBaseHelper.getKubeconfigFileContent(k8sInfraDelegateConfig, WORK_DIR);
    verify(kubernetesContainerService).getConfigFileContent(kubernetesConfig);
    verify(secretDecryptionService).decrypt(credentials, encryptionDataDetails);
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testGetKubeconfigFileContentDirectGcpK8sInfraDelegateConfig() {
    final List<EncryptedDataDetail> encryptionDataDetails = emptyList();
    final char[] serviceAccountKeyFileContent = "secret".toCharArray();
    final GcpManualDetailsDTO credentials =
        GcpManualDetailsDTO.builder()
            .secretKeyRef(SecretRefData.builder().decryptedValue(serviceAccountKeyFileContent).build())
            .build();
    final GcpK8sInfraDelegateConfig gcpK8sInfraDelegateConfig =
        GcpK8sInfraDelegateConfig.builder()
            .encryptionDataDetails(encryptionDataDetails)
            .gcpConnectorDTO(GcpConnectorDTO.builder()
                                 .credential(GcpConnectorCredentialDTO.builder()
                                                 .config(credentials)
                                                 .gcpCredentialType(GcpCredentialType.MANUAL_CREDENTIALS)
                                                 .build())
                                 .build())
            .cluster("cluster")
            .namespace("default")
            .build();
    final KubernetesConfig kubernetesConfig = KubernetesConfig.builder().build();
    MockedStatic kubeConfigAuthPluginHelper = mockStatic(KubeConfigAuthPluginHelper.class);
    Mockito.when(KubeConfigAuthPluginHelper.isExecAuthPluginBinaryAvailable(any(), any())).thenReturn(true);
    doReturn(kubernetesConfig)
        .when(gkeClusterHelper)
        .getCluster(serviceAccountKeyFileContent, false, "cluster", "default", null);

    containerDeploymentDelegateBaseHelper.getKubeconfigFileContent(gcpK8sInfraDelegateConfig, WORK_DIR);
    kubeConfigAuthPluginHelper.close();
    verify(gkeClusterHelper).getCluster(serviceAccountKeyFileContent, false, "cluster", "default", null);
    verify(secretDecryptionService).decrypt(credentials, encryptionDataDetails);
  }

  @Test
  @Owner(developers = MLUKIC)
  @Category(UnitTests.class)
  public void testGetAdminKubeconfigFileContentAzureK8sInfraDelegateConfig() throws IOException {
    final List<EncryptedDataDetail> encryptionDataDetails = emptyList();
    final AzureConnectorDTO azureConnectorDTO = AzureConnectorDTO.builder().build();
    final AzureK8sInfraDelegateConfig azureK8sInfraDelegateConfig = AzureK8sInfraDelegateConfig.builder()
                                                                        .azureConnectorDTO(azureConnectorDTO)
                                                                        .subscription("sub")
                                                                        .resourceGroup("rg")
                                                                        .cluster("aks")
                                                                        .namespace("default")
                                                                        .encryptionDataDetails(encryptionDataDetails)
                                                                        .useClusterAdminCredentials(true)
                                                                        .build();
    final KubernetesConfig kubernetesConfig = KubernetesConfig.builder().build();

    when(azureAsyncTaskHelper.getClusterConfig(any(AzureConfigContext.class), anyString(), any()))
        .thenReturn(kubernetesConfig);

    containerDeploymentDelegateBaseHelper.getKubeconfigFileContent(azureK8sInfraDelegateConfig, WORK_DIR);
    verify(kubernetesContainerService).getConfigFileContent(kubernetesConfig);
  }

  @Test
  @Owner(developers = MLUKIC)
  @Category(UnitTests.class)
  public void testGetUserKubeconfigFileContentAzureK8sInfraDelegateConfig() throws IOException {
    final List<EncryptedDataDetail> encryptionDataDetails = emptyList();
    final AzureConnectorDTO azureConnectorDTO = AzureConnectorDTO.builder().build();
    final AzureK8sInfraDelegateConfig azureK8sInfraDelegateConfig = AzureK8sInfraDelegateConfig.builder()
                                                                        .azureConnectorDTO(azureConnectorDTO)
                                                                        .subscription("sub")
                                                                        .resourceGroup("rg")
                                                                        .cluster("aks")
                                                                        .namespace("default")
                                                                        .encryptionDataDetails(encryptionDataDetails)
                                                                        .useClusterAdminCredentials(false)
                                                                        .build();
    final KubernetesConfig kubernetesConfig = KubernetesConfig.builder().build();

    when(azureAsyncTaskHelper.getClusterConfig(any(AzureConfigContext.class), anyString(), any()))
        .thenReturn(kubernetesConfig);

    containerDeploymentDelegateBaseHelper.getKubeconfigFileContent(azureK8sInfraDelegateConfig, WORK_DIR);
    verify(kubernetesContainerService).getConfigFileContent(kubernetesConfig);
  }

  @Test
  @Owner(developers = LOVISH_BANSAL)
  @Category(UnitTests.class)
  public void testGetUserKubeconfigFileContentEksK8sInfraDelegateConfig() {
    final List<EncryptedDataDetail> encryptionDataDetails = emptyList();
    final AwsConnectorDTO awsConnectorDTO =
        AwsConnectorDTO.builder().credential(AwsCredentialDTO.builder().build()).build();
    final EksK8sInfraDelegateConfig eksK8sInfraDelegateConfig = EksK8sInfraDelegateConfig.builder()
                                                                    .awsConnectorDTO(awsConnectorDTO)
                                                                    .cluster("eks")
                                                                    .namespace("default")
                                                                    .encryptionDataDetails(encryptionDataDetails)
                                                                    .build();

    containerDeploymentDelegateBaseHelper.getKubeconfigFileContent(eksK8sInfraDelegateConfig, WORK_DIR);
    verify(awsEKSDelegateTaskHelper, times(1)).getKubeConfig(eq(awsConnectorDTO), eq("eks"), eq("default"), eq(null));
    verify(kubernetesContainerService).getConfigFileContent(any());
  }
}
