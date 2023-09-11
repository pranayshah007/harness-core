/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.manifest.resources;

import static io.harness.rule.OwnerRule.ACHYUTH;
import static io.harness.rule.OwnerRule.MLUKIC;
import static io.harness.rule.OwnerRule.PRATYUSH;
import static io.harness.utils.DelegateOwner.getNGTaskSetupAbstractionsWithOwner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.DelegateTaskRequest;
import io.harness.category.element.UnitTests;
import io.harness.cdng.featureFlag.CDFeatureFlagHelper;
import io.harness.cdng.k8s.K8sEntityHelper;
import io.harness.cdng.manifest.ManifestStoreType;
import io.harness.cdng.manifest.resources.dtos.HelmChartResponseDTO;
import io.harness.cdng.manifest.resources.dtos.HelmManifestInternalDTO;
import io.harness.cdng.manifest.yaml.HelmChartManifestOutcome;
import io.harness.cdng.manifest.yaml.OciHelmChartConfig;
import io.harness.cdng.manifest.yaml.kinds.HelmChartManifest;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.connector.ConnectorResponseDTO;
import io.harness.connector.services.ConnectorService;
import io.harness.delegate.beans.connector.ConnectorConfigDTO;
import io.harness.delegate.beans.connector.ConnectorType;
import io.harness.delegate.beans.connector.awsconnector.AwsConnectorDTO;
import io.harness.delegate.beans.connector.helm.EcrHelmApiListTagsTaskParams;
import io.harness.delegate.beans.connector.helm.HttpHelmConnectorDTO;
import io.harness.delegate.beans.connector.helm.OciHelmAuthType;
import io.harness.delegate.beans.connector.helm.OciHelmAuthenticationDTO;
import io.harness.delegate.beans.connector.helm.OciHelmConnectorDTO;
import io.harness.delegate.beans.connector.helm.OciHelmDockerApiListTagsTaskParams;
import io.harness.delegate.beans.connector.helm.OciHelmDockerApiListTagsTaskResponse;
import io.harness.delegate.beans.connector.helm.OciHelmUsernamePasswordDTO;
import io.harness.delegate.beans.storeconfig.HttpHelmStoreDelegateConfig;
import io.harness.delegate.beans.storeconfig.StoreDelegateConfig;
import io.harness.delegate.task.helm.HelmFetchChartVersionResponse;
import io.harness.encryption.SecretRefData;
import io.harness.exception.InvalidRequestException;
import io.harness.grpc.DelegateServiceGrpcClient;
import io.harness.k8s.model.HelmVersion;
import io.harness.ng.core.service.services.ServiceEntityService;
import io.harness.pms.yaml.ParameterField;
import io.harness.pms.yaml.YamlNode;
import io.harness.rule.Owner;
import io.harness.security.encryption.EncryptedDataDetail;
import io.harness.service.DelegateGrpcClientWrapper;

import software.wings.beans.TaskType;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;

@OwnedBy(HarnessTeam.CDP)
public class HelmChartServiceImplTest extends CategoryTest {
  public static final String accountId = "acc";
  public static final String orgId = "org";
  public static final String projId = "proj";
  public static final String serviceRef = "svc";
  private static final String connectorId = "connId";
  private static final String connectorName = "connName";
  @Mock private CDFeatureFlagHelper cdFeatureFlagHelper;
  @Mock private DelegateGrpcClientWrapper delegateGrpcClientWrapper;
  @Mock private ServiceEntityService serviceEntityService;
  @Mock private ConnectorService connectorService;
  @Mock private K8sEntityHelper k8sEntityHelper;
  @Mock private DelegateServiceGrpcClient delegateServiceGrpcClient;
  @InjectMocks @Spy HelmChartServiceImpl helmChartServiceImpl;

  @Before
  public void setup() {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = ACHYUTH)
  @Category(UnitTests.class)
  public void testLocateManifestInService() throws IOException {
    String manifest = "manifest:\n"
        + "                      identifier: m1\n"
        + "                      type: HelmChart\n"
        + "                      spec:\n"
        + "                        store:\n"
        + "                          type: Http\n"
        + "                          spec:\n"
        + "                            connectorRef: account.http_helm_2\n"
        + "                        chartName: nginx\n"
        + "                        chartVersion: <+input>\n"
        + "                        helmVersion: V3\n"
        + "                        skipResourceVersioning: false\n";

    String manifestPath = "manifest";

    YamlNode yamlNode = YamlNode.fromYamlPath(manifest, manifestPath);

    doReturn(yamlNode)
        .when(serviceEntityService)
        .getYamlNodeForFqn(eq(accountId), eq(orgId), eq(projId), eq(serviceRef), anyString());

    HelmManifestInternalDTO helmManifestInternalDTO =
        helmChartServiceImpl.locateManifestInService(accountId, orgId, projId, serviceRef, manifestPath);

    HelmChartManifest helmChartManifest = (HelmChartManifest) helmManifestInternalDTO.getSpec();

    assertThat(helmChartManifest.getChartName().getValue()).isEqualTo("nginx");
    assertThat(helmChartManifest.getHelmVersion()).isEqualTo(HelmVersion.V3);
  }

  @Test
  @Owner(developers = ACHYUTH)
  @Category(UnitTests.class)
  public void testGetHelmChartVersionDetails() {
    String manifestPath = "stages.HelmDeploy.spec.serviceConfig.serviceDefinition.spec.manifests.m1";

    HelmChartManifestOutcome helmChartManifestOutcome =
        HelmChartManifestOutcome.builder().chartName(ParameterField.createValueField("my-chart")).build();

    StoreDelegateConfig storeDelegateConfig = HttpHelmStoreDelegateConfig.builder()
                                                  .repoName("repo")
                                                  .repoDisplayName("repo")
                                                  .httpHelmConnector(HttpHelmConnectorDTO.builder().build())
                                                  .build();

    HelmFetchChartVersionResponse helmFetchChartVersionResponse =
        HelmFetchChartVersionResponse.builder().chartVersionsList(Arrays.asList("0.1.0", "0.1.1")).build();

    doReturn(helmChartManifestOutcome).when(helmChartServiceImpl).getHelmChartManifestOutcome(any());
    doReturn(new HelmManifestInternalDTO())
        .when(helmChartServiceImpl)
        .locateManifestInService(eq(accountId), eq(orgId), eq(projId), eq(serviceRef), eq(manifestPath));
    doReturn(HelmVersion.V3).when(helmChartServiceImpl).getHelmVersionBasedOnFF(any(), eq(accountId));
    doReturn(storeDelegateConfig)
        .when(helmChartServiceImpl)
        .getStoreDelegateConfig(any(), eq(accountId), eq(orgId), eq(projId), eq(""), eq(""), eq(""), eq(""));
    doReturn(new HashSet<>()).when(helmChartServiceImpl).getDelegateSelectors(any());
    doReturn(helmFetchChartVersionResponse).when(delegateGrpcClientWrapper).executeSyncTaskV2(any());
    doReturn(true).when(cdFeatureFlagHelper).isEnabled(eq(accountId), any());

    doReturn(true).when(delegateServiceGrpcClient).isTaskTypeSupported(any(), any());

    HelmChartResponseDTO helmChartResponseDTO = helmChartServiceImpl.getHelmChartVersionDetailsV2(
        accountId, orgId, projId, serviceRef, manifestPath, "", "", "", "", "", null, "");
    assertThat(helmChartResponseDTO.getHelmChartVersions()).contains("0.1.0", "0.1.1");
  }

  @Test
  @Owner(developers = MLUKIC)
  @Category(UnitTests.class)
  public void testGetHelmChartVersionDetailsWithOCIHelmConnector() {
    String manifestPath = "stages.HelmDeploy.spec.serviceConfig.serviceDefinition.spec.manifests.m1";
    String chartName = "helm/testchart";

    OciHelmChartConfig ociHelmChartConfig = OciHelmChartConfig.builder().build();

    HelmChartManifestOutcome helmChartManifestOutcome = HelmChartManifestOutcome.builder()
                                                            .chartName(ParameterField.createValueField(chartName))
                                                            .store(ociHelmChartConfig)
                                                            .build();

    OciHelmAuthenticationDTO ociHelmAuthenticationDTO = OciHelmAuthenticationDTO.builder()
                                                            .authType(OciHelmAuthType.USER_PASSWORD)
                                                            .credentials(getOciHelmAuthenticationDTO(false))
                                                            .build();

    OciHelmConnectorDTO ociHelmConnectorDTO = OciHelmConnectorDTO.builder()
                                                  .helmRepoUrl("test.azurecr.io")
                                                  .auth(ociHelmAuthenticationDTO)
                                                  .delegateSelectors(new HashSet<>())
                                                  .build();

    ConnectorInfoDTO connectorInfoDTO = ConnectorInfoDTO.builder()
                                            .name(connectorName)
                                            .projectIdentifier(projId)
                                            .orgIdentifier(orgId)
                                            .connectorType(ConnectorType.OCI_HELM_REPO)
                                            .connectorConfig(ociHelmConnectorDTO)
                                            .identifier(connectorId)
                                            .build();

    ConnectorResponseDTO connectorResponseDTO = ConnectorResponseDTO.builder().connector(connectorInfoDTO).build();

    doReturn(Optional.of(connectorResponseDTO)).when(connectorService).get(accountId, orgId, projId, connectorId);

    EncryptedDataDetail encryptedDataDetail = EncryptedDataDetail.builder().build();
    doReturn(Arrays.asList(encryptedDataDetail)).when(k8sEntityHelper).getEncryptionDataDetails(any(), any());

    OciHelmDockerApiListTagsTaskResponse ociHelmDockerApiListTagsTaskResponse =
        OciHelmDockerApiListTagsTaskResponse.builder()
            .chartName(chartName)
            .chartVersions(Arrays.asList("0.1.0", "0.1.1"))
            .lastTag("0.1.1")
            .build();

    doReturn(helmChartManifestOutcome).when(helmChartServiceImpl).getHelmChartManifestOutcome(any());
    doReturn(new HelmManifestInternalDTO())
        .when(helmChartServiceImpl)
        .locateManifestInService(eq(accountId), eq(orgId), eq(projId), eq(serviceRef), eq(manifestPath));

    doReturn(ociHelmDockerApiListTagsTaskResponse).when(delegateGrpcClientWrapper).executeSyncTaskV2(any());
    doReturn(true).when(cdFeatureFlagHelper).isEnabled(eq(accountId), any());
    doReturn(true).when(delegateServiceGrpcClient).isTaskTypeSupported(any(), any());

    HelmChartResponseDTO helmChartResponseDTO = helmChartServiceImpl.getHelmChartVersionDetailsV2(
        accountId, orgId, projId, serviceRef, manifestPath, connectorId, chartName, "", "", "", null, null);
    String taskTypeName = TaskType.OCI_HELM_DOCKER_API_LIST_TAGS_TASK_NG.name();
    DelegateTaskRequest delegateTaskRequest =
        DelegateTaskRequest.builder()
            .accountId(accountId)
            .taskType(taskTypeName)
            .taskParameters(getOciHelmDockerApiListTagsTaskParam(
                ociHelmConnectorDTO, chartName, Arrays.asList(encryptedDataDetail)))
            .executionTimeout(java.time.Duration.ofSeconds(6000L))
            .taskSetupAbstractions(getAbstraction())
            .build();
    Mockito.verify(delegateGrpcClientWrapper, times(1)).executeSyncTaskV2(delegateTaskRequest);
    assertThat(helmChartResponseDTO.getHelmChartVersions()).contains("0.1.0", "0.1.1");
    assertThat(helmChartResponseDTO.getLastTag()).isEqualTo("0.1.1");
  }

  @Test
  @Owner(developers = PRATYUSH)
  @Category(UnitTests.class)
  public void testGetHelmChartVersionDetailsWithOCIHelmAWSConnector() {
    String manifestPath = "stages.HelmDeploy.spec.serviceConfig.serviceDefinition.spec.manifests.m1";
    String chartName = "helm/testchart";
    String region = "us-west-1";

    OciHelmChartConfig ociHelmChartConfig = OciHelmChartConfig.builder().build();

    HelmChartManifestOutcome helmChartManifestOutcome = HelmChartManifestOutcome.builder()
                                                            .chartName(ParameterField.createValueField(chartName))
                                                            .store(ociHelmChartConfig)
                                                            .build();

    AwsConnectorDTO awsConnectorDTO = AwsConnectorDTO.builder().delegateSelectors(new HashSet<>()).build();

    ConnectorInfoDTO connectorInfoDTO = ConnectorInfoDTO.builder()
                                            .name(connectorName)
                                            .projectIdentifier(projId)
                                            .orgIdentifier(orgId)
                                            .connectorType(ConnectorType.AWS)
                                            .connectorConfig(awsConnectorDTO)
                                            .identifier(connectorId)
                                            .build();

    ConnectorResponseDTO connectorResponseDTO = ConnectorResponseDTO.builder().connector(connectorInfoDTO).build();

    doReturn(Optional.of(connectorResponseDTO)).when(connectorService).get(accountId, orgId, projId, connectorId);

    EncryptedDataDetail encryptedDataDetail = EncryptedDataDetail.builder().build();
    doReturn(Arrays.asList(encryptedDataDetail)).when(k8sEntityHelper).getEncryptionDataDetails(any(), any());

    OciHelmDockerApiListTagsTaskResponse ociHelmDockerApiListTagsTaskResponse =
        OciHelmDockerApiListTagsTaskResponse.builder()
            .chartName(chartName)
            .chartVersions(Arrays.asList("0.1.0", "0.1.1"))
            .lastTag("0.1.1")
            .build();

    doReturn(helmChartManifestOutcome).when(helmChartServiceImpl).getHelmChartManifestOutcome(any());
    doReturn(new HelmManifestInternalDTO())
        .when(helmChartServiceImpl)
        .locateManifestInService(eq(accountId), eq(orgId), eq(projId), eq(serviceRef), eq(manifestPath));

    doReturn(ociHelmDockerApiListTagsTaskResponse).when(delegateGrpcClientWrapper).executeSyncTaskV2(any());
    doReturn(true).when(cdFeatureFlagHelper).isEnabled(eq(accountId), any());
    doReturn(true).when(delegateServiceGrpcClient).isTaskTypeSupported(any(), any());

    HelmChartResponseDTO helmChartResponseDTO = helmChartServiceImpl.getHelmChartVersionDetailsV2(
        accountId, orgId, projId, serviceRef, manifestPath, connectorId, chartName, region, "", "", null, null);
    String taskTypeName = TaskType.ECR_HELM_API_LIST_TAGS_TASK.name();
    DelegateTaskRequest delegateTaskRequest =
        DelegateTaskRequest.builder()
            .accountId(accountId)
            .taskType(taskTypeName)
            .taskParameters(getOciHelmEcrApiListTagsTaskParam(
                awsConnectorDTO, chartName, Collections.singletonList(encryptedDataDetail), region))
            .executionTimeout(java.time.Duration.ofSeconds(6000L))
            .taskSetupAbstractions(getAbstraction())
            .build();
    Mockito.verify(delegateGrpcClientWrapper, times(1)).executeSyncTaskV2(delegateTaskRequest);
    assertThat(helmChartResponseDTO.getHelmChartVersions()).contains("0.1.0", "0.1.1");
    assertThat(helmChartResponseDTO.getLastTag()).isEqualTo("0.1.1");
  }

  @Test
  @Owner(developers = PRATYUSH)
  @Category(UnitTests.class)
  public void throwExceptionWhenRegionIsEmpty() {
    String chartName = "helm/testchart";
    assertThatThrownBy(()
                           -> helmChartServiceImpl.getHelmChartVersionDetails(accountId, orgId, projId, connectorId,
                               chartName, "", "", "", "", ManifestStoreType.OCI, "ECR", "", ""))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("query param region: must not be null");
  }

  @Test
  @Owner(developers = ACHYUTH)
  @Category(UnitTests.class)
  public void testGetConnector() {
    doReturn(
        Optional.of(ConnectorResponseDTO.builder()
                        .connector(ConnectorInfoDTO.builder()
                                       .connectorConfig(
                                           HttpHelmConnectorDTO.builder().helmRepoUrl("https://bitnami.com").build())
                                       .connectorType(ConnectorType.HTTP_HELM_REPO)
                                       .build())
                        .build()))
        .when(connectorService)
        .get(accountId, orgId, projId, "connectorId");

    ConnectorInfoDTO connectorInfoDTO = helmChartServiceImpl.getConnector(accountId, orgId, projId, "connectorId");

    assertThat(((HttpHelmConnectorDTO) connectorInfoDTO.getConnectorConfig()).getHelmRepoUrl())
        .isEqualTo("https://bitnami.com");
  }

  private OciHelmUsernamePasswordDTO getOciHelmAuthenticationDTO(boolean isAnonymous) {
    if (isAnonymous) {
      return OciHelmUsernamePasswordDTO.builder().build();
    } else {
      return OciHelmUsernamePasswordDTO.builder()
          .username("test")
          .passwordRef(SecretRefData.builder().decryptedValue("test".toCharArray()).build())
          .build();
    }
  }

  private Map<String, String> getAbstraction() {
    Map<String, String> owner = getNGTaskSetupAbstractionsWithOwner(accountId, orgId, projId);
    Map<String, String> abstractions = new HashMap<>(owner);
    if (orgId != null) {
      abstractions.put("orgIdentifier", orgId);
    }
    if (projId != null && orgId != null) {
      abstractions.put("projectIdentifier", projId);
    }
    abstractions.put("ng", "true");
    abstractions.put("owner", orgId + "/" + projId);
    return abstractions;
  }

  private OciHelmDockerApiListTagsTaskParams getOciHelmDockerApiListTagsTaskParam(
      ConnectorConfigDTO connectorConfigDTO, String chartName, List<EncryptedDataDetail> encryptedDataDetailList) {
    return OciHelmDockerApiListTagsTaskParams.builder()
        .ociHelmConnector((OciHelmConnectorDTO) connectorConfigDTO)
        .encryptionDetails(encryptedDataDetailList)
        .chartName("/" + chartName)
        .pageSize(100000)
        .build();
  }

  private EcrHelmApiListTagsTaskParams getOciHelmEcrApiListTagsTaskParam(ConnectorConfigDTO connectorConfigDTO,
      String chartName, List<EncryptedDataDetail> encryptedDataDetailList, String region) {
    return EcrHelmApiListTagsTaskParams.builder()
        .encryptionDetails(encryptedDataDetailList)
        .chartName("/" + chartName)
        .pageSize(1000)
        .awsConnectorDTO((AwsConnectorDTO) connectorConfigDTO)
        .region(region)
        .registryId(null)
        .build();
  }
}
