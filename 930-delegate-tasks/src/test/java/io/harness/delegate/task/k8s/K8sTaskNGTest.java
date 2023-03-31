/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.task.k8s;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.delegate.task.k8s.K8sTaskNG.KUBECONFIG_FILENAME;
import static io.harness.rule.OwnerRule.ABOSII;

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.fail;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyBoolean;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.DecryptableEntity;
import io.harness.category.element.UnitTests;
import io.harness.connector.task.git.GitDecryptionHelper;
import io.harness.delegate.beans.DelegateTaskPackage;
import io.harness.delegate.beans.DelegateTaskResponse;
import io.harness.delegate.beans.TaskData;
import io.harness.delegate.beans.connector.awsconnector.AwsConnectorDTO;
import io.harness.delegate.beans.connector.awsconnector.AwsCredentialDTO;
import io.harness.delegate.beans.connector.awsconnector.AwsCredentialType;
import io.harness.delegate.beans.connector.awsconnector.AwsManualConfigSpecDTO;
import io.harness.delegate.beans.connector.gcpconnector.GcpConnectorCredentialDTO;
import io.harness.delegate.beans.connector.gcpconnector.GcpConnectorDTO;
import io.harness.delegate.beans.connector.gcpconnector.GcpCredentialType;
import io.harness.delegate.beans.connector.gcpconnector.GcpManualDetailsDTO;
import io.harness.delegate.beans.connector.helm.HttpHelmAuthType;
import io.harness.delegate.beans.connector.helm.HttpHelmAuthenticationDTO;
import io.harness.delegate.beans.connector.helm.HttpHelmConnectorDTO;
import io.harness.delegate.beans.connector.helm.HttpHelmUsernamePasswordDTO;
import io.harness.delegate.beans.connector.scm.genericgitconnector.GitConfigDTO;
import io.harness.delegate.beans.logstreaming.CommandUnitsProgress;
import io.harness.delegate.beans.logstreaming.ILogStreamingTaskClient;
import io.harness.delegate.beans.storeconfig.GcsHelmStoreDelegateConfig;
import io.harness.delegate.beans.storeconfig.GitStoreDelegateConfig;
import io.harness.delegate.beans.storeconfig.HttpHelmStoreDelegateConfig;
import io.harness.delegate.beans.storeconfig.S3HelmStoreDelegateConfig;
import io.harness.delegate.k8s.K8sRequestHandler;
import io.harness.delegate.task.ManifestDelegateConfigHelper;
import io.harness.exception.ExceptionUtils;
import io.harness.exception.InvalidRequestException;
import io.harness.filesystem.FileIo;
import io.harness.k8s.config.K8sGlobalConfigService;
import io.harness.k8s.model.HelmVersion;
import io.harness.k8s.model.K8sDelegateTaskParams;
import io.harness.k8s.model.KubernetesConfig;
import io.harness.logging.CommandExecutionStatus;
import io.harness.rule.Owner;
import io.harness.security.encryption.EncryptedDataDetail;
import io.harness.security.encryption.SecretDecryptionService;

import com.google.inject.Inject;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import org.apache.commons.lang3.NotImplementedException;
import org.joor.Reflect;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@OwnedBy(CDP)
@FieldDefaults(level = AccessLevel.PRIVATE)
public class K8sTaskNGTest extends CategoryTest {
  final CommandUnitsProgress emptyCommandUnitsProgress = CommandUnitsProgress.builder().build();
  final DelegateTaskPackage delegateTaskPackage =
      DelegateTaskPackage.builder().data(TaskData.builder().build()).build();
  private ManifestDelegateConfigHelper manifestDelegateConfigHelper = new ManifestDelegateConfigHelper();

  @Mock BooleanSupplier preExecute;
  @Mock Consumer<DelegateTaskResponse> consumer;
  @Mock ILogStreamingTaskClient logStreamingTaskClient;
  @Mock Map<String, K8sRequestHandler> k8sTaskTypeToRequestHandler;
  @Mock ContainerDeploymentDelegateBaseHelper containerDeploymentDelegateBaseHelper;
  @Mock K8sGlobalConfigService k8sGlobalConfigService;
  @Mock GitDecryptionHelper gitDecryptionHelper;
  @Mock SecretDecryptionService decryptionService;

  @Inject
  @InjectMocks
  K8sTaskNG k8sTaskNG = new K8sTaskNG(delegateTaskPackage, logStreamingTaskClient, consumer, preExecute);

  @Mock K8sDeployRequest k8sDeployRequest;
  @Mock K8sInfraDelegateConfig k8sInfraDelegateConfig;
  @Mock K8sRequestHandler instanceSyncRequestHandler;
  @Mock K8sRequestHandler rollingRequestHandler;

  final String mockKubeConfigFileContent = "TestKubeConfig";
  final KubernetesConfig mockKubeConfig = KubernetesConfig.builder().build();

  final String kubectlPath = "tools/kubectl";
  final String goTemplateClientPath = "tools/go-template";
  final String helmV2Path = "tools/v2/helm";
  final String helmV3Path = "tools/v3/helm";
  final String ocPath = "tools/oc";
  final String kustomizePath = "tools/kustomize";

  @Before
  public void setup() {
    MockitoAnnotations.initMocks(this);
    Reflect.on(k8sTaskNG).set("manifestDelegateConfigHelper", manifestDelegateConfigHelper);
    doReturn(instanceSyncRequestHandler).when(k8sTaskTypeToRequestHandler).get(K8sTaskType.INSTANCE_SYNC.name());
    doReturn(rollingRequestHandler).when(k8sTaskTypeToRequestHandler).get(K8sTaskType.DEPLOYMENT_ROLLING.name());
    doReturn(mockKubeConfigFileContent)
        .when(containerDeploymentDelegateBaseHelper)
        .getKubeconfigFileContent(any(K8sInfraDelegateConfig.class), anyString());
    doReturn(mockKubeConfig)
        .when(containerDeploymentDelegateBaseHelper)
        .decryptAndGetKubernetesConfig(any(K8sInfraDelegateConfig.class), anyString());
    doReturn(kubectlPath).when(k8sGlobalConfigService).getKubectlPath(anyBoolean());
    doReturn(goTemplateClientPath).when(k8sGlobalConfigService).getGoTemplateClientPath();
    doReturn(helmV2Path).when(k8sGlobalConfigService).getHelmPath(HelmVersion.V2);
    doReturn(helmV3Path).when(k8sGlobalConfigService).getHelmPath(HelmVersion.V3);
    doReturn(ocPath).when(k8sGlobalConfigService).getOcPath();
    doReturn(kustomizePath).when(k8sGlobalConfigService).getKustomizePath(false);
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testRunInstanceSyncTask() throws Exception {
    final K8sDeployResponse syncResponse = K8sDeployResponse.builder().build();

    doReturn(K8sTaskType.INSTANCE_SYNC).when(k8sDeployRequest).getTaskType();
    doReturn(syncResponse)
        .when(instanceSyncRequestHandler)
        .executeTask(k8sDeployRequest, null, logStreamingTaskClient, emptyCommandUnitsProgress);

    K8sDeployResponse result = k8sTaskNG.run(k8sDeployRequest);

    verify(instanceSyncRequestHandler)
        .executeTask(k8sDeployRequest, null, logStreamingTaskClient, emptyCommandUnitsProgress);
    assertThat(result).isEqualTo(syncResponse);
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testRunInstanceSyncFailed() throws Exception {
    InvalidRequestException thrownException = new InvalidRequestException("failed to sync");
    doReturn(K8sTaskType.INSTANCE_SYNC).when(k8sDeployRequest).getTaskType();
    doThrow(thrownException)
        .when(instanceSyncRequestHandler)
        .executeTask(k8sDeployRequest, null, logStreamingTaskClient, emptyCommandUnitsProgress);

    K8sDeployResponse result = k8sTaskNG.run(k8sDeployRequest);

    verify(instanceSyncRequestHandler)
        .executeTask(k8sDeployRequest, null, logStreamingTaskClient, emptyCommandUnitsProgress);
    assertThat(result.getCommandExecutionStatus()).isEqualTo(CommandExecutionStatus.FAILURE);
    assertThat(result.getErrorMessage()).isEqualTo(ExceptionUtils.getMessage(thrownException));
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testLogK8sVersion() throws Exception {
    final K8sDelegateTaskParams delegateTaskParams = K8sDelegateTaskParams.builder().build();
    doReturn(rollingRequestHandler).when(k8sTaskTypeToRequestHandler).get(K8sTaskType.VERSION.name());

    k8sTaskNG.logK8sVersion(k8sDeployRequest, delegateTaskParams, emptyCommandUnitsProgress);

    verify(rollingRequestHandler)
        .executeTask(k8sDeployRequest, delegateTaskParams, logStreamingTaskClient, emptyCommandUnitsProgress);
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testLogK8sVersionFailed() throws Exception {
    final K8sDelegateTaskParams delegateTaskParams = K8sDelegateTaskParams.builder().build();
    doReturn(rollingRequestHandler).when(k8sTaskTypeToRequestHandler).get(K8sTaskType.VERSION.name());
    doThrow(new InvalidRequestException("failed"))
        .when(rollingRequestHandler)
        .executeTask(k8sDeployRequest, delegateTaskParams, logStreamingTaskClient, emptyCommandUnitsProgress);

    assertThatCode(() -> k8sTaskNG.logK8sVersion(k8sDeployRequest, delegateTaskParams, emptyCommandUnitsProgress))
        .doesNotThrowAnyException();

    verify(rollingRequestHandler)
        .executeTask(k8sDeployRequest, delegateTaskParams, logStreamingTaskClient, emptyCommandUnitsProgress);
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void tetRunUnimplemented() {
    assertThatThrownBy(() -> k8sTaskNG.run(new Object[] {k8sDeployRequest}))
        .isInstanceOf(NotImplementedException.class);
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testRun() throws Exception {
    testRunWithManifest(null, null);
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testRunFailed() throws Exception {
    InvalidRequestException thrownException = new InvalidRequestException("failed to sync");
    doReturn(K8sTaskType.DEPLOYMENT_ROLLING).when(k8sDeployRequest).getTaskType();
    doReturn(k8sInfraDelegateConfig).when(k8sDeployRequest).getK8sInfraDelegateConfig();
    doThrow(thrownException)
        .when(rollingRequestHandler)
        .executeTask(eq(k8sDeployRequest), any(K8sDelegateTaskParams.class), eq(logStreamingTaskClient),
            eq(emptyCommandUnitsProgress));
    doReturn(null)
        .doReturn(K8sManifestDelegateConfig.builder().build())
        .when(k8sDeployRequest)
        .getManifestDelegateConfig();
    K8sDeployResponse result = k8sTaskNG.run(k8sDeployRequest);

    verify(rollingRequestHandler)
        .executeTask(eq(k8sDeployRequest), any(K8sDelegateTaskParams.class), eq(logStreamingTaskClient),
            eq(emptyCommandUnitsProgress));
    assertThat(result.getCommandExecutionStatus()).isEqualTo(CommandExecutionStatus.FAILURE);
    assertThat(result.getErrorMessage()).isEqualTo(ExceptionUtils.getMessage(thrownException));
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testRunK8sManifestDelegateConfigGit() throws Exception {
    final GitConfigDTO gitConfigDTO = GitConfigDTO.builder().build();
    final List<EncryptedDataDetail> encryptedDataDetails =
        singletonList(EncryptedDataDetail.builder().fieldName("accessKey").build());

    testRunWithManifest(K8sManifestDelegateConfig.builder()
                            .storeDelegateConfig(GitStoreDelegateConfig.builder()
                                                     .gitConfigDTO(gitConfigDTO)
                                                     .encryptedDataDetails(encryptedDataDetails)
                                                     .build())
                            .build(),
        null);
    verify(gitDecryptionHelper).decryptGitConfig(gitConfigDTO, encryptedDataDetails);
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testRunHelmChartManifestDelegateConfigHttpV2() throws Exception {
    char[] str = {'1', '2', '3'};
    doReturn(str).when(decryptionService).getDecryptedValue(any());
    HttpHelmUsernamePasswordDTO usernamePasswordDTO = HttpHelmUsernamePasswordDTO.builder().build();
    final List<EncryptedDataDetail> encryptedDataDetails =
        singletonList(EncryptedDataDetail.builder().fieldName("accessKey").build());
    final HelmChartManifestDelegateConfig manifestConfig =
        HelmChartManifestDelegateConfig.builder()
            .storeDelegateConfig(HttpHelmStoreDelegateConfig.builder()
                                     .httpHelmConnector(HttpHelmConnectorDTO.builder()
                                                            .auth(HttpHelmAuthenticationDTO.builder()
                                                                      .credentials(usernamePasswordDTO)
                                                                      .authType(HttpHelmAuthType.USER_PASSWORD)
                                                                      .build())
                                                            .build())
                                     .encryptedDataDetails(encryptedDataDetails)
                                     .build())
            .helmVersion(HelmVersion.V2)
            .build();

    testRunWithManifest(manifestConfig, HelmVersion.V2);

    verify(decryptionService).decrypt(usernamePasswordDTO, encryptedDataDetails);
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testRunHelmChartManifestDelegateConfigS3V3() throws Exception {
    final AwsManualConfigSpecDTO manualConfigSpecDTO = AwsManualConfigSpecDTO.builder().build();
    final List<EncryptedDataDetail> encryptedDataDetails =
        singletonList(EncryptedDataDetail.builder().fieldName("aws-accessKey").build());
    final HelmChartManifestDelegateConfig manifestConfig =
        HelmChartManifestDelegateConfig.builder()
            .storeDelegateConfig(
                S3HelmStoreDelegateConfig.builder()
                    .awsConnector(AwsConnectorDTO.builder()
                                      .credential(AwsCredentialDTO.builder()
                                                      .awsCredentialType(AwsCredentialType.MANUAL_CREDENTIALS)
                                                      .config(manualConfigSpecDTO)
                                                      .build())
                                      .build())
                    .encryptedDataDetails(encryptedDataDetails)
                    .build())
            .helmVersion(HelmVersion.V3)
            .build();
    testRunWithManifest(manifestConfig, HelmVersion.V3);

    verify(decryptionService).decrypt(manualConfigSpecDTO, encryptedDataDetails);
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testRunHelmChartManifestDelegateConfigS3InheritFromDelegate() throws Exception {
    final List<EncryptedDataDetail> encryptedDataDetails = emptyList();
    final HelmChartManifestDelegateConfig manifestConfig =
        HelmChartManifestDelegateConfig.builder()
            .storeDelegateConfig(
                S3HelmStoreDelegateConfig.builder()
                    .awsConnector(AwsConnectorDTO.builder()
                                      .credential(AwsCredentialDTO.builder()
                                                      .awsCredentialType(AwsCredentialType.INHERIT_FROM_DELEGATE)
                                                      .build())
                                      .build())
                    .encryptedDataDetails(encryptedDataDetails)
                    .build())
            .helmVersion(HelmVersion.V3)
            .build();
    testRunWithManifest(manifestConfig, HelmVersion.V3);

    verify(decryptionService, never()).decrypt(any(DecryptableEntity.class), eq(encryptedDataDetails));
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testRunHelmChartManifestDelegateConfigGcsV2() throws Exception {
    final GcpManualDetailsDTO manualDetailsDTO = GcpManualDetailsDTO.builder().build();
    final List<EncryptedDataDetail> encryptedDataDetails =
        singletonList(EncryptedDataDetail.builder().fieldName("accessKey").build());
    final HelmChartManifestDelegateConfig manifestConfig =
        HelmChartManifestDelegateConfig.builder()
            .storeDelegateConfig(
                GcsHelmStoreDelegateConfig.builder()
                    .gcpConnector(GcpConnectorDTO.builder()
                                      .credential(GcpConnectorCredentialDTO.builder()
                                                      .gcpCredentialType(GcpCredentialType.MANUAL_CREDENTIALS)
                                                      .config(manualDetailsDTO)
                                                      .build())
                                      .build())
                    .encryptedDataDetails(encryptedDataDetails)
                    .build())
            .helmVersion(HelmVersion.V2)
            .build();

    testRunWithManifest(manifestConfig, HelmVersion.V2);

    verify(decryptionService).decrypt(manualDetailsDTO, encryptedDataDetails);
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testRunHelmChartManifestDelegateConfigGcsInheritFromDelegate() throws Exception {
    final List<EncryptedDataDetail> encryptedDataDetails = emptyList();
    final HelmChartManifestDelegateConfig manifestConfig =
        HelmChartManifestDelegateConfig.builder()
            .storeDelegateConfig(
                GcsHelmStoreDelegateConfig.builder()
                    .gcpConnector(GcpConnectorDTO.builder()
                                      .credential(GcpConnectorCredentialDTO.builder()
                                                      .gcpCredentialType(GcpCredentialType.INHERIT_FROM_DELEGATE)
                                                      .build())
                                      .build())
                    .encryptedDataDetails(encryptedDataDetails)
                    .build())
            .helmVersion(HelmVersion.V2)
            .build();

    testRunWithManifest(manifestConfig, HelmVersion.V2);

    verify(decryptionService, never()).decrypt(any(DecryptableEntity.class), eq(encryptedDataDetails));
  }

  private void testRunWithManifest(ManifestDelegateConfig manifest, HelmVersion usedHelmVersion) throws Exception {
    final K8sDeployResponse taskResponse = K8sDeployResponse.builder().build();
    final ArgumentCaptor<K8sDelegateTaskParams> delegateTaskParamsCaptor =
        ArgumentCaptor.forClass(K8sDelegateTaskParams.class);

    char[] str = {'1', '2', '3'};
    doReturn(str).when(decryptionService).getDecryptedValue(any());
    doReturn(K8sTaskType.DEPLOYMENT_ROLLING).when(k8sDeployRequest).getTaskType();
    doReturn(manifest).when(k8sDeployRequest).getManifestDelegateConfig();
    doReturn(k8sInfraDelegateConfig).when(k8sDeployRequest).getK8sInfraDelegateConfig();
    doReturn(taskResponse)
        .when(rollingRequestHandler)
        .executeTask(eq(k8sDeployRequest), any(K8sDelegateTaskParams.class), eq(logStreamingTaskClient),
            eq(emptyCommandUnitsProgress));
    doReturn(manifest)
        .doReturn(manifest == null ? K8sManifestDelegateConfig.builder().build() : manifest)
        .when(k8sDeployRequest)
        .getManifestDelegateConfig();

    Reflect.on(manifestDelegateConfigHelper).set("gitDecryptionHelper", gitDecryptionHelper);
    Reflect.on(manifestDelegateConfigHelper).set("decryptionService", decryptionService);

    K8sDeployResponse result = k8sTaskNG.run(k8sDeployRequest);

    verify(rollingRequestHandler)
        .executeTask(eq(k8sDeployRequest), delegateTaskParamsCaptor.capture(), eq(logStreamingTaskClient),
            eq(emptyCommandUnitsProgress));
    verify(containerDeploymentDelegateBaseHelper, times(1))
        .persistKubernetesConfig(any(KubernetesConfig.class), anyString());

    K8sDelegateTaskParams k8sDelegateTaskParams = delegateTaskParamsCaptor.getValue();
    assertCleanupWorkingDirectory(k8sDelegateTaskParams);
    assertClientPaths(manifest, k8sDelegateTaskParams, usedHelmVersion);
    assertThat(result).isEqualTo(taskResponse);
  }

  private void assertCleanupWorkingDirectory(K8sDelegateTaskParams delegateTaskParams) {
    assertThat(delegateTaskParams.getWorkingDirectory()).isNotNull();
    try {
      assertThat(FileIo.checkIfFileExist(delegateTaskParams.getWorkingDirectory())).isFalse();
    } catch (IOException e) {
      fail("Failed to check if working directory got cleaned up. Error: " + e.getMessage());
    }
  }

  private void assertClientPaths(
      ManifestDelegateConfig manifest, K8sDelegateTaskParams delegateTaskParams, HelmVersion helmVersion) {
    assertThat(delegateTaskParams.getKubeconfigPath()).isEqualTo(KUBECONFIG_FILENAME);
    assertThat(delegateTaskParams.getKubectlPath()).isEqualTo(kubectlPath);
    if (manifest != null) {
      if (manifest.getManifestType().equals(ManifestType.K8S_MANIFEST)) {
        assertThat(delegateTaskParams.getGoTemplateClientPath()).isEqualTo(goTemplateClientPath);
      }
      if (manifest.getManifestType().equals(ManifestType.HELM_CHART)) {
        if (null == helmVersion) {
          assertThat(delegateTaskParams.getHelmPath()).isNull();
        } else if (HelmVersion.V2 == helmVersion) {
          assertThat(delegateTaskParams.getHelmPath()).isEqualTo(helmV2Path);
        } else if (HelmVersion.V3 == helmVersion) {
          assertThat(delegateTaskParams.getHelmPath()).isEqualTo(helmV3Path);
        }
      }
      if (manifest.getManifestType().equals(ManifestType.KUSTOMIZE)) {
        assertThat(delegateTaskParams.getKustomizeBinaryPath()).isEqualTo(kustomizePath);
      }
      if (manifest.getManifestType().equals(ManifestType.OPENSHIFT_TEMPLATE)) {
        assertThat(delegateTaskParams.getOcPath()).isEqualTo(ocPath);
      }
    }
  }
}
