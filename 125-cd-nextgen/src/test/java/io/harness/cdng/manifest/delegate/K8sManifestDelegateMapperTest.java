/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cdng.manifest.delegate;

import static io.harness.delegate.beans.connector.ConnectorType.AWS;
import static io.harness.delegate.beans.connector.ConnectorType.GCP;
import static io.harness.delegate.beans.connector.ConnectorType.HTTP_HELM_REPO;
import static io.harness.rule.OwnerRule.ABOSII;
import static io.harness.rule.OwnerRule.ACASIAN;
import static io.harness.rule.OwnerRule.ACHYUTH;
import static io.harness.rule.OwnerRule.NAMAN_TALAYCHA;
import static io.harness.rule.OwnerRule.PRATYUSH;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.cdng.CDStepHelper;
import io.harness.cdng.common.beans.SetupAbstractionKeys;
import io.harness.cdng.featureFlag.CDFeatureFlagHelper;
import io.harness.cdng.infra.beans.K8sDirectInfrastructureOutcome;
import io.harness.cdng.k8s.K8sEntityHelper;
import io.harness.cdng.manifest.yaml.CustomRemoteStoreConfig;
import io.harness.cdng.manifest.yaml.GcsStoreConfig;
import io.harness.cdng.manifest.yaml.GitStore;
import io.harness.cdng.manifest.yaml.GitStoreConfig;
import io.harness.cdng.manifest.yaml.GithubStore;
import io.harness.cdng.manifest.yaml.HelmChartManifestOutcome;
import io.harness.cdng.manifest.yaml.HelmCommandFlagType;
import io.harness.cdng.manifest.yaml.HelmManifestCommandFlag;
import io.harness.cdng.manifest.yaml.HttpStoreConfig;
import io.harness.cdng.manifest.yaml.K8sManifestOutcome;
import io.harness.cdng.manifest.yaml.KustomizeManifestOutcome;
import io.harness.cdng.manifest.yaml.OpenshiftManifestOutcome;
import io.harness.cdng.manifest.yaml.S3StoreConfig;
import io.harness.cdng.manifest.yaml.harness.HarnessStore;
import io.harness.cdng.manifest.yaml.kinds.kustomize.OverlayConfiguration;
import io.harness.cdng.manifest.yaml.storeConfig.StoreConfig;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.delegate.beans.connector.awsconnector.AwsConnectorDTO;
import io.harness.delegate.beans.connector.awsconnector.AwsCredentialDTO;
import io.harness.delegate.beans.connector.awsconnector.AwsCredentialType;
import io.harness.delegate.beans.connector.gcpconnector.GcpConnectorCredentialDTO;
import io.harness.delegate.beans.connector.gcpconnector.GcpConnectorDTO;
import io.harness.delegate.beans.connector.gcpconnector.GcpCredentialType;
import io.harness.delegate.beans.connector.helm.HttpHelmAuthType;
import io.harness.delegate.beans.connector.helm.HttpHelmAuthenticationDTO;
import io.harness.delegate.beans.connector.helm.HttpHelmConnectorDTO;
import io.harness.delegate.beans.connector.scm.genericgitconnector.GitConfigDTO;
import io.harness.delegate.beans.storeconfig.CustomRemoteStoreDelegateConfig;
import io.harness.delegate.beans.storeconfig.GcsHelmStoreDelegateConfig;
import io.harness.delegate.beans.storeconfig.GitStoreDelegateConfig;
import io.harness.delegate.beans.storeconfig.HttpHelmStoreDelegateConfig;
import io.harness.delegate.beans.storeconfig.LocalFileStoreDelegateConfig;
import io.harness.delegate.beans.storeconfig.S3HelmStoreDelegateConfig;
import io.harness.delegate.task.k8s.HelmChartManifestDelegateConfig;
import io.harness.delegate.task.k8s.K8sManifestDelegateConfig;
import io.harness.delegate.task.k8s.KustomizeManifestDelegateConfig;
import io.harness.delegate.task.k8s.ManifestDelegateConfig;
import io.harness.delegate.task.k8s.ManifestType;
import io.harness.delegate.task.k8s.OpenshiftManifestDelegateConfig;
import io.harness.filestore.dto.node.FileNodeDTO;
import io.harness.filestore.dto.node.FileStoreNodeDTO;
import io.harness.filestore.dto.node.FolderNodeDTO;
import io.harness.filestore.service.FileStoreService;
import io.harness.helm.HelmSubCommandType;
import io.harness.k8s.model.HelmVersion;
import io.harness.ng.core.filestore.FileUsage;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.yaml.ParameterField;
import io.harness.rule.Owner;

import java.util.List;
import java.util.Optional;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

@OwnedBy(HarnessTeam.CDP)
public class K8sManifestDelegateMapperTest extends CategoryTest {
  private static final String NAMESPACE = "default";
  private static final String INFRA_KEY = "svcId_envId";

  @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();

  @Mock private CDStepHelper cdStepHelper;

  @Mock private CDFeatureFlagHelper cdFeatureFlagHelper;

  @Mock private FileStoreService fileStoreService;

  @Mock private K8sEntityHelper k8sEntityHelper;

  @Mock private StoreConfig storeConfig;

  @InjectMocks K8sManifestDelegateMapper k8sManifestDelegateMapper;

  private final Ambiance ambiance = Ambiance.newBuilder()
                                        .putSetupAbstractions(SetupAbstractionKeys.accountId, "test-account")
                                        .putSetupAbstractions(SetupAbstractionKeys.orgIdentifier, "test-org")
                                        .putSetupAbstractions(SetupAbstractionKeys.projectIdentifier, "test-project")
                                        .build();

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testGetManifestDelegateConfigForK8s() {
    K8sManifestOutcome manifestOutcome =
        K8sManifestOutcome.builder()
            .store(GitStore.builder()
                       .branch(ParameterField.createValueField("test"))
                       .connectorRef(ParameterField.createValueField("org.connectorRef"))
                       .paths(ParameterField.createValueField(asList("file1", "file2")))
                       .build())
            .build();
    doReturn(ConnectorInfoDTO.builder().connectorConfig(GitConfigDTO.builder().build()).build())
        .when(cdStepHelper)
        .getConnector(nullable(String.class), any(Ambiance.class));

    doReturn(GitStoreDelegateConfig.builder().build())
        .when(cdStepHelper)
        .getGitStoreDelegateConfig(any(GitStoreConfig.class), any(ConnectorInfoDTO.class),
            any(K8sManifestOutcome.class), anyList(), any(Ambiance.class));

    ManifestDelegateConfig delegateConfig =
        k8sManifestDelegateMapper.getManifestDelegateConfig(manifestOutcome, ambiance);
    assertThat(delegateConfig.getManifestType()).isEqualTo(ManifestType.K8S_MANIFEST);
    assertThat(delegateConfig).isInstanceOf(K8sManifestDelegateConfig.class);
    assertThat(delegateConfig.getStoreDelegateConfig()).isNotNull();
    assertThat(delegateConfig.getStoreDelegateConfig()).isInstanceOf(GitStoreDelegateConfig.class);
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testGetManifestDelegateConfigForHelmChart() {
    List<HelmManifestCommandFlag> commandFlags = asList(HelmManifestCommandFlag.builder()
                                                            .commandType(HelmCommandFlagType.Fetch)
                                                            .flag(ParameterField.createValueField("--test"))
                                                            .build());
    HelmChartManifestOutcome manifestOutcome =
        HelmChartManifestOutcome.builder()
            .store(GitStore.builder()
                       .branch(ParameterField.createValueField("test"))
                       .connectorRef(ParameterField.createValueField("org.connectorRef"))
                       .folderPath(ParameterField.createValueField("helm-chart"))
                       .build())
            .skipResourceVersioning(ParameterField.createValueField(true))
            .helmVersion(HelmVersion.V3)
            .commandFlags(commandFlags)
            .build();

    doReturn(ConnectorInfoDTO.builder().connectorConfig(GitConfigDTO.builder().build()).build())
        .when(cdStepHelper)
        .getConnector(nullable(String.class), any(Ambiance.class));

    doReturn(GitStoreDelegateConfig.builder().build())
        .when(cdStepHelper)
        .getGitStoreDelegateConfig(any(GitStoreConfig.class), any(ConnectorInfoDTO.class),
            any(HelmChartManifestOutcome.class), anyList(), any(Ambiance.class));

    ManifestDelegateConfig delegateConfig =
        k8sManifestDelegateMapper.getManifestDelegateConfig(manifestOutcome, ambiance);
    assertThat(delegateConfig.getManifestType()).isEqualTo(ManifestType.HELM_CHART);
    assertThat(delegateConfig).isInstanceOf(HelmChartManifestDelegateConfig.class);
    HelmChartManifestDelegateConfig helmChartDelegateConfig = (HelmChartManifestDelegateConfig) delegateConfig;
    assertThat(helmChartDelegateConfig.getStoreDelegateConfig()).isNotNull();
    assertThat(helmChartDelegateConfig.getStoreDelegateConfig()).isInstanceOf(GitStoreDelegateConfig.class);
    assertThat(helmChartDelegateConfig.getHelmVersion()).isEqualTo(HelmVersion.V3);
    assertThat(helmChartDelegateConfig.getHelmCommandFlag().getValueMap()).containsKeys(HelmSubCommandType.FETCH);
    assertThat(helmChartDelegateConfig.getHelmCommandFlag().getValueMap()).containsValues("--test");
  }

  @Test
  @Owner(developers = PRATYUSH)
  @Category(UnitTests.class)
  public void testGetManifestDelegateConfigForK8sManifestUsingHarnessStore() {
    List<String> files = asList("/path/to/k8s/template/deploy.yaml", "/path/to/k8s/template/service.yaml");
    K8sManifestOutcome manifestOutcome =
        K8sManifestOutcome.builder()
            .store(HarnessStore.builder().files(ParameterField.createValueField(files)).build())
            .build();

    doReturn(Optional.of(getFileStoreNode("/path/to/k8s/template/deploy.yaml", "deploy.yaml")))
        .doReturn(Optional.of(getFileStoreNode("/path/to/k8s/template/service.yaml", "service.yaml")))
        .when(fileStoreService)
        .getWithChildrenByPath(any(), any(), any(), any(), eq(false));
    ManifestDelegateConfig delegateConfig =
        k8sManifestDelegateMapper.getManifestDelegateConfig(manifestOutcome, ambiance);
    assertThat(delegateConfig.getManifestType()).isEqualTo(ManifestType.K8S_MANIFEST);
    assertThat(delegateConfig).isInstanceOf(K8sManifestDelegateConfig.class);
    K8sManifestDelegateConfig k8sManifestDelegateConfig = (K8sManifestDelegateConfig) delegateConfig;
    assertThat(k8sManifestDelegateConfig.getStoreDelegateConfig()).isNotNull();
    assertThat(k8sManifestDelegateConfig.getStoreDelegateConfig()).isInstanceOf(LocalFileStoreDelegateConfig.class);
    LocalFileStoreDelegateConfig localFileStoreDelegateConfig =
        (LocalFileStoreDelegateConfig) k8sManifestDelegateConfig.getStoreDelegateConfig();
    assertThat(localFileStoreDelegateConfig.getFilePaths())
        .isEqualTo(asList("/path/to/k8s/template/deploy.yaml", "/path/to/k8s/template/service.yaml"));
  }

  @Test
  @Owner(developers = PRATYUSH)
  @Category(UnitTests.class)
  public void testGetManifestDelegateConfigForK8sUsingCustomRemoteStore() {
    String k8sPath = "/path/to/k8s";
    String extractionScript = "git clone something.git";
    K8sManifestOutcome manifestOutcome =
        K8sManifestOutcome.builder()
            .store(CustomRemoteStoreConfig.builder()
                       .filePath(ParameterField.createValueField(k8sPath))
                       .extractionScript(ParameterField.createValueField(extractionScript))
                       .build())
            .build();

    ManifestDelegateConfig delegateConfig =
        k8sManifestDelegateMapper.getManifestDelegateConfig(manifestOutcome, ambiance);
    assertThat(delegateConfig.getManifestType()).isEqualTo(ManifestType.K8S_MANIFEST);
    assertThat(delegateConfig).isInstanceOf(K8sManifestDelegateConfig.class);
    K8sManifestDelegateConfig k8sManifestDelegateConfig = (K8sManifestDelegateConfig) delegateConfig;
    assertThat(k8sManifestDelegateConfig.getStoreDelegateConfig()).isNotNull();
    assertThat(k8sManifestDelegateConfig.getStoreDelegateConfig()).isInstanceOf(CustomRemoteStoreDelegateConfig.class);
    CustomRemoteStoreDelegateConfig customRemoteStoreDelegateConfig =
        (CustomRemoteStoreDelegateConfig) k8sManifestDelegateConfig.getStoreDelegateConfig();
    assertThat(customRemoteStoreDelegateConfig.getCustomManifestSource().getFilePaths()).isEqualTo(asList(k8sPath));
    assertThat(customRemoteStoreDelegateConfig.getCustomManifestSource().getScript()).isEqualTo(extractionScript);
    assertThat(customRemoteStoreDelegateConfig.getCustomManifestSource().getAccountId()).isEqualTo("test-account");
  }

  @Test
  @Owner(developers = ACASIAN)
  @Category(UnitTests.class)
  public void testGetManifestDelegateConfigForKustomize() {
    KustomizeManifestOutcome manifestOutcome =
        KustomizeManifestOutcome.builder()
            .store(GitStore.builder()
                       .branch(ParameterField.createValueField("test"))
                       .connectorRef(ParameterField.createValueField("org.connectorRef"))
                       .paths(ParameterField.createValueField(asList("file1")))
                       .folderPath(ParameterField.createValueField("kustomize-dir"))
                       .build())
            .pluginPath(ParameterField.createValueField("/usr/bin/kustomize"))
            .build();

    doReturn(ConnectorInfoDTO.builder().connectorConfig(GitConfigDTO.builder().build()).build())
        .when(cdStepHelper)
        .getConnector(nullable(String.class), any(Ambiance.class));

    doReturn(GitStoreDelegateConfig.builder().build())
        .when(cdStepHelper)
        .getGitStoreDelegateConfig(any(GitStoreConfig.class), any(ConnectorInfoDTO.class),
            any(KustomizeManifestOutcome.class), anyList(), any(Ambiance.class));

    ManifestDelegateConfig delegateConfig =
        k8sManifestDelegateMapper.getManifestDelegateConfig(manifestOutcome, ambiance);
    assertThat(delegateConfig.getManifestType()).isEqualTo(ManifestType.KUSTOMIZE);
    assertThat(delegateConfig).isInstanceOf(KustomizeManifestDelegateConfig.class);
    assertThat(delegateConfig.getStoreDelegateConfig()).isNotNull();
    assertThat(delegateConfig.getStoreDelegateConfig()).isInstanceOf(GitStoreDelegateConfig.class);
    KustomizeManifestDelegateConfig kustomizeManifestDelegateConfig = (KustomizeManifestDelegateConfig) delegateConfig;
    assertThat(kustomizeManifestDelegateConfig.getPluginPath()).isEqualTo("/usr/bin/kustomize");
    assertThat(kustomizeManifestDelegateConfig.getKustomizeDirPath()).isEqualTo("kustomize-dir");
    assertThat(kustomizeManifestDelegateConfig.getStoreDelegateConfig()).isInstanceOf(GitStoreDelegateConfig.class);
  }

  @Test
  @Owner(developers = PRATYUSH)
  @Category(UnitTests.class)
  public void testGetManifestDelegateConfigForKustomizeUsingHarnessStore() {
    List<String> files = asList("/path/to/kustomize");
    KustomizeManifestOutcome manifestOutcome =
        KustomizeManifestOutcome.builder()
            .store(HarnessStore.builder().files(ParameterField.createValueField(files)).build())
            .pluginPath(ParameterField.createValueField("/usr/bin/kustomize"))
            .build();

    doReturn(Optional.of(getFolderStoreNode("/path/to/kustomize", "kustomize")))
        .when(fileStoreService)
        .getWithChildrenByPath(any(), any(), any(), any(), eq(true));

    ManifestDelegateConfig delegateConfig =
        k8sManifestDelegateMapper.getManifestDelegateConfig(manifestOutcome, ambiance);
    assertThat(delegateConfig.getManifestType()).isEqualTo(ManifestType.KUSTOMIZE);
    assertThat(delegateConfig).isInstanceOf(KustomizeManifestDelegateConfig.class);
    assertThat(delegateConfig.getStoreDelegateConfig()).isNotNull();
    KustomizeManifestDelegateConfig kustomizeManifestDelegateConfig = (KustomizeManifestDelegateConfig) delegateConfig;
    assertThat(kustomizeManifestDelegateConfig.getPluginPath()).isEqualTo("/usr/bin/kustomize");
    assertThat(kustomizeManifestDelegateConfig.getKustomizeDirPath()).isEqualTo(".");
    assertThat(kustomizeManifestDelegateConfig.getStoreDelegateConfig())
        .isInstanceOf(LocalFileStoreDelegateConfig.class);
    LocalFileStoreDelegateConfig localFileStoreDelegateConfig =
        (LocalFileStoreDelegateConfig) kustomizeManifestDelegateConfig.getStoreDelegateConfig();
    assertThat(localFileStoreDelegateConfig.getFilePaths()).isEqualTo(asList("/path/to/kustomize"));
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testGetManifestDelegateConfigForHelmChartUsingHttpRepo() {
    String connectorRef = "org.http_helm_connector";
    String chartName = "chartName";
    String chartVersion = "chartVersion";
    HttpHelmConnectorDTO httpHelmConnectorConfig =
        HttpHelmConnectorDTO.builder()
            .auth(HttpHelmAuthenticationDTO.builder().authType(HttpHelmAuthType.ANONYMOUS).build())
            .build();
    HelmChartManifestOutcome manifestOutcome =
        HelmChartManifestOutcome.builder()
            .store(HttpStoreConfig.builder().connectorRef(ParameterField.createValueField(connectorRef)).build())
            .chartName(ParameterField.createValueField(chartName))
            .chartVersion(ParameterField.createValueField(chartVersion))
            .build();

    doReturn(ConnectorInfoDTO.builder()
                 .identifier("http-helm-connector")
                 .connectorType(HTTP_HELM_REPO)
                 .connectorConfig(httpHelmConnectorConfig)
                 .build())
        .when(cdStepHelper)
        .getConnector(nullable(String.class), any(Ambiance.class));

    doReturn(K8sDirectInfrastructureOutcome.builder().namespace(NAMESPACE).infrastructureKey(INFRA_KEY).build())
        .when(cdStepHelper)
        .getInfrastructureOutcome(ambiance);

    ManifestDelegateConfig delegateConfig =
        k8sManifestDelegateMapper.getManifestDelegateConfig(manifestOutcome, ambiance);
    assertThat(delegateConfig.getManifestType()).isEqualTo(ManifestType.HELM_CHART);
    assertThat(delegateConfig).isInstanceOf(HelmChartManifestDelegateConfig.class);
    HelmChartManifestDelegateConfig helmChartDelegateConfig = (HelmChartManifestDelegateConfig) delegateConfig;
    assertThat(helmChartDelegateConfig.getStoreDelegateConfig()).isNotNull();
    assertThat(helmChartDelegateConfig.getStoreDelegateConfig()).isInstanceOf(HttpHelmStoreDelegateConfig.class);
  }

  @Test
  @Owner(developers = ACASIAN)
  @Category(UnitTests.class)
  public void testGetManifestDelegateConfigForOpenshift() {
    OpenshiftManifestOutcome manifestOutcome =
        OpenshiftManifestOutcome.builder()
            .store(GitStore.builder()
                       .branch(ParameterField.createValueField("test"))
                       .connectorRef(ParameterField.createValueField("org.connectorRef"))
                       .paths(ParameterField.createValueField(asList("file1", "file2")))
                       .build())
            .build();

    doReturn(ConnectorInfoDTO.builder().connectorConfig(GitConfigDTO.builder().build()).build())
        .when(cdStepHelper)
        .getConnector(nullable(String.class), any(Ambiance.class));

    doReturn(GitStoreDelegateConfig.builder().build())
        .when(cdStepHelper)
        .getGitStoreDelegateConfig(any(GitStoreConfig.class), any(ConnectorInfoDTO.class),
            any(OpenshiftManifestOutcome.class), anyList(), any(Ambiance.class));

    ManifestDelegateConfig delegateConfig =
        k8sManifestDelegateMapper.getManifestDelegateConfig(manifestOutcome, ambiance);
    assertThat(delegateConfig.getManifestType()).isEqualTo(ManifestType.OPENSHIFT_TEMPLATE);
    assertThat(delegateConfig).isInstanceOf(OpenshiftManifestDelegateConfig.class);
    assertThat(delegateConfig.getStoreDelegateConfig()).isNotNull();
    assertThat(delegateConfig.getStoreDelegateConfig()).isInstanceOf(GitStoreDelegateConfig.class);
  }

  @Test
  @Owner(developers = PRATYUSH)
  @Category(UnitTests.class)
  public void testGetManifestDelegateConfigForOpenshiftUsingHarnessStore() {
    List<String> files = asList("/path/to/openshift/template.yaml");
    OpenshiftManifestOutcome manifestOutcome =
        OpenshiftManifestOutcome.builder()
            .store(HarnessStore.builder().files(ParameterField.createValueField(files)).build())
            .build();

    doReturn(Optional.of(getFileStoreNode("/path/to/openshift/template.yaml", "template.yaml")))
        .when(fileStoreService)
        .getWithChildrenByPath(any(), any(), any(), any(), eq(false));
    ManifestDelegateConfig delegateConfig =
        k8sManifestDelegateMapper.getManifestDelegateConfig(manifestOutcome, ambiance);
    assertThat(delegateConfig.getManifestType()).isEqualTo(ManifestType.OPENSHIFT_TEMPLATE);
    assertThat(delegateConfig).isInstanceOf(OpenshiftManifestDelegateConfig.class);
    OpenshiftManifestDelegateConfig openshiftManifestDelegateConfig = (OpenshiftManifestDelegateConfig) delegateConfig;
    assertThat(openshiftManifestDelegateConfig.getStoreDelegateConfig()).isNotNull();
    assertThat(openshiftManifestDelegateConfig.getStoreDelegateConfig())
        .isInstanceOf(LocalFileStoreDelegateConfig.class);
    LocalFileStoreDelegateConfig localFileStoreDelegateConfig =
        (LocalFileStoreDelegateConfig) openshiftManifestDelegateConfig.getStoreDelegateConfig();
    assertThat(localFileStoreDelegateConfig.getFilePaths()).isEqualTo(asList("/path/to/openshift/template.yaml"));
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testGetManifestDelegateConfigForHelmChartUsingAwsS3Repo() {
    String connectorRef = "org.aws_s3_repo";
    String bucketName = "bucketName";
    String region = "region";
    String folderPath = "basePath";
    String chartName = "chartName";
    String chartVersion = "chartVersion";
    AwsConnectorDTO awsConnectorConfig =
        AwsConnectorDTO.builder()
            .credential(AwsCredentialDTO.builder().awsCredentialType(AwsCredentialType.INHERIT_FROM_DELEGATE).build())
            .build();

    HelmChartManifestOutcome manifestOutcome =
        HelmChartManifestOutcome.builder()
            .store(S3StoreConfig.builder()
                       .connectorRef(ParameterField.createValueField(connectorRef))
                       .bucketName(ParameterField.createValueField(bucketName))
                       .region(ParameterField.createValueField(region))
                       .folderPath(ParameterField.createValueField(folderPath))
                       .build())
            .chartName(ParameterField.createValueField(chartName))
            .chartVersion(ParameterField.createValueField(chartVersion))
            .build();

    doReturn(ConnectorInfoDTO.builder()
                 .identifier("aws-helm-connector")
                 .connectorType(AWS)
                 .connectorConfig(awsConnectorConfig)
                 .build())
        .when(cdStepHelper)
        .getConnector(nullable(String.class), any(Ambiance.class));

    doReturn(K8sDirectInfrastructureOutcome.builder().namespace(NAMESPACE).infrastructureKey(INFRA_KEY).build())
        .when(cdStepHelper)
        .getInfrastructureOutcome(ambiance);

    ManifestDelegateConfig delegateConfig =
        k8sManifestDelegateMapper.getManifestDelegateConfig(manifestOutcome, ambiance);
    assertThat(delegateConfig.getManifestType()).isEqualTo(ManifestType.HELM_CHART);
    assertThat(delegateConfig).isInstanceOf(HelmChartManifestDelegateConfig.class);
    HelmChartManifestDelegateConfig helmChartDelegateConfig = (HelmChartManifestDelegateConfig) delegateConfig;
    assertThat(helmChartDelegateConfig.getStoreDelegateConfig()).isNotNull();
    assertThat(helmChartDelegateConfig.getStoreDelegateConfig()).isInstanceOf(S3HelmStoreDelegateConfig.class);
    S3HelmStoreDelegateConfig s3StoreDelegateConfig =
        (S3HelmStoreDelegateConfig) helmChartDelegateConfig.getStoreDelegateConfig();
    assertThat(s3StoreDelegateConfig.getBucketName()).isEqualTo(bucketName);
    assertThat(s3StoreDelegateConfig.getRegion()).isEqualTo(region);
    assertThat(s3StoreDelegateConfig.getFolderPath()).isEqualTo(folderPath);
    assertThat(s3StoreDelegateConfig.getAwsConnector()).isEqualTo(awsConnectorConfig);
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testGetManifestDelegateConfigForHelmChartUsingGcsRepo() {
    String connectorRef = "org.aws_s3_repo";
    String bucketName = "bucketName";
    String folderPath = "basePath";
    String chartName = "chartName";
    String chartVersion = "chartVersion";
    GcpConnectorDTO gcpConnectorDTO =
        GcpConnectorDTO.builder()
            .credential(
                GcpConnectorCredentialDTO.builder().gcpCredentialType(GcpCredentialType.INHERIT_FROM_DELEGATE).build())
            .build();

    HelmChartManifestOutcome helmChartManifestOutcome =
        HelmChartManifestOutcome.builder()
            .chartVersion(ParameterField.createValueField(chartVersion))
            .chartName(ParameterField.createValueField(chartName))
            .store(GcsStoreConfig.builder()
                       .connectorRef(ParameterField.createValueField(connectorRef))
                       .bucketName(ParameterField.createValueField(bucketName))
                       .folderPath(ParameterField.createValueField(folderPath))
                       .build())
            .build();

    doReturn(ConnectorInfoDTO.builder()
                 .identifier("gcp-helm-connector")
                 .connectorType(GCP)
                 .connectorConfig(gcpConnectorDTO)
                 .build())
        .when(cdStepHelper)
        .getConnector(nullable(String.class), any(Ambiance.class));

    doReturn(K8sDirectInfrastructureOutcome.builder().namespace(NAMESPACE).infrastructureKey(INFRA_KEY).build())
        .when(cdStepHelper)
        .getInfrastructureOutcome(ambiance);

    ManifestDelegateConfig delegateConfig =
        k8sManifestDelegateMapper.getManifestDelegateConfig(helmChartManifestOutcome, ambiance);
    assertThat(delegateConfig.getManifestType()).isEqualTo(ManifestType.HELM_CHART);
    assertThat(delegateConfig).isInstanceOf(HelmChartManifestDelegateConfig.class);
    HelmChartManifestDelegateConfig helmChartDelegateConfig = (HelmChartManifestDelegateConfig) delegateConfig;
    assertThat(helmChartDelegateConfig.getStoreDelegateConfig()).isNotNull();
    assertThat(helmChartDelegateConfig.getStoreDelegateConfig()).isInstanceOf(GcsHelmStoreDelegateConfig.class);
    GcsHelmStoreDelegateConfig gcsHelmStoreDelegateConfig =
        (GcsHelmStoreDelegateConfig) helmChartDelegateConfig.getStoreDelegateConfig();
    assertThat(gcsHelmStoreDelegateConfig.getBucketName()).isEqualTo(bucketName);
    assertThat(gcsHelmStoreDelegateConfig.getFolderPath()).isEqualTo(folderPath);
    assertThat(gcsHelmStoreDelegateConfig.getGcpConnector()).isEqualTo(gcpConnectorDTO);
  }

  @Test
  @Owner(developers = ACHYUTH)
  @Category(UnitTests.class)
  public void testGetManifestDelegateConfigForKustomizeNegCase() {
    when(storeConfig.getKind()).thenReturn("xyz");
    KustomizeManifestOutcome manifestOutcome = KustomizeManifestOutcome.builder()
                                                   .store(storeConfig)
                                                   .pluginPath(ParameterField.createValueField("/usr/bin/kustomize"))
                                                   .build();

    assertThatThrownBy(() -> k8sManifestDelegateMapper.getManifestDelegateConfig(manifestOutcome, ambiance))
        .isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  @Owner(developers = NAMAN_TALAYCHA)
  @Category(UnitTests.class)
  public void getKustomizeManifestBasePathTest() {
    GithubStore githubStore = GithubStore.builder().folderPath(ParameterField.createValueField("kustomize/")).build();
    KustomizeManifestOutcome kustomizeManifestOutcome =
        KustomizeManifestOutcome.builder()
            .overlayConfiguration(ParameterField.createValueField(
                OverlayConfiguration.builder()
                    .kustomizeYamlFolderPath(ParameterField.createValueField("env/prod/"))
                    .build()))
            .build();
    List<String> paths = k8sManifestDelegateMapper.getKustomizeManifestBasePath(githubStore, kustomizeManifestOutcome);

    assertThat(paths.get(0)).isEqualTo("kustomize/");
  }

  @Test
  @Owner(developers = NAMAN_TALAYCHA)
  @Category(UnitTests.class)
  public void getKustomizeManifestBasePathCase2Test() {
    GithubStore githubStore = GithubStore.builder().folderPath(ParameterField.createValueField("kustomize/")).build();
    KustomizeManifestOutcome kustomizeManifestOutcome =
        KustomizeManifestOutcome.builder()
            .overlayConfiguration(ParameterField.createValueField(OverlayConfiguration.builder().build()))
            .build();
    List<String> paths = k8sManifestDelegateMapper.getKustomizeManifestBasePath(githubStore, kustomizeManifestOutcome);

    assertThat(paths.get(0)).isEqualTo("/");
  }

  private FileStoreNodeDTO getFileStoreNode(String path, String name) {
    return FileNodeDTO.builder()
        .name(name)
        .identifier("identifier")
        .fileUsage(FileUsage.MANIFEST_FILE)
        .parentIdentifier("folder")
        .content("Test")
        .path(path)
        .build();
  }

  private FileStoreNodeDTO getFolderStoreNode(String path, String name) {
    return FolderNodeDTO.builder().name(name).identifier("identifier").parentIdentifier("k8s").path(path).build();
  }
}