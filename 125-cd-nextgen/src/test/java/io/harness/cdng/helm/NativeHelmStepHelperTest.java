/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.helm;

import static io.harness.beans.EnvironmentType.NON_PROD;
import static io.harness.beans.EnvironmentType.PROD;
import static io.harness.cdng.k8s.K8sStepHelper.RELEASE_NAME;
import static io.harness.delegate.beans.connector.ConnectorType.AWS;
import static io.harness.delegate.beans.connector.ConnectorType.GCP;
import static io.harness.delegate.beans.connector.ConnectorType.GITHUB;
import static io.harness.delegate.beans.connector.ConnectorType.HTTP_HELM_REPO;
import static io.harness.delegate.beans.connector.ConnectorType.OCI_HELM_REPO;
import static io.harness.eraro.ErrorCode.GENERAL_ERROR;
import static io.harness.logging.CommandExecutionStatus.FAILURE;
import static io.harness.logging.CommandExecutionStatus.SUCCESS;
import static io.harness.rule.OwnerRule.ABHINAV2;
import static io.harness.rule.OwnerRule.ABOSII;
import static io.harness.rule.OwnerRule.ACASIAN;
import static io.harness.rule.OwnerRule.ACHYUTH;
import static io.harness.rule.OwnerRule.PRATYUSH;
import static io.harness.rule.OwnerRule.TARUN_UBA;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.emptyMap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.beans.FeatureName;
import io.harness.category.element.UnitTests;
import io.harness.cdng.CDStepHelper;
import io.harness.cdng.K8sHelmCommonStepHelper;
import io.harness.cdng.common.beans.SetupAbstractionKeys;
import io.harness.cdng.featureFlag.CDFeatureFlagHelper;
import io.harness.cdng.helm.beans.NativeHelmExecutionPassThroughData;
import io.harness.cdng.infra.beans.K8sDirectInfrastructureOutcome;
import io.harness.cdng.k8s.K8sEntityHelper;
import io.harness.cdng.k8s.K8sRollingStepParameters;
import io.harness.cdng.k8s.K8sStepPassThroughData;
import io.harness.cdng.k8s.beans.CustomFetchResponsePassThroughData;
import io.harness.cdng.k8s.beans.HelmValuesFetchResponsePassThroughData;
import io.harness.cdng.k8s.beans.StepExceptionPassThroughData;
import io.harness.cdng.manifest.ManifestStoreType;
import io.harness.cdng.manifest.delegate.K8sManifestDelegateMapper;
import io.harness.cdng.manifest.steps.outcome.ManifestsOutcome;
import io.harness.cdng.manifest.yaml.CustomRemoteStoreConfig;
import io.harness.cdng.manifest.yaml.GcsStoreConfig;
import io.harness.cdng.manifest.yaml.GitStore;
import io.harness.cdng.manifest.yaml.HelmChartManifestOutcome;
import io.harness.cdng.manifest.yaml.HelmCommandFlagType;
import io.harness.cdng.manifest.yaml.HelmManifestCommandFlag;
import io.harness.cdng.manifest.yaml.HttpStoreConfig;
import io.harness.cdng.manifest.yaml.InheritFromManifestStoreConfig;
import io.harness.cdng.manifest.yaml.K8sManifestOutcome;
import io.harness.cdng.manifest.yaml.ManifestOutcome;
import io.harness.cdng.manifest.yaml.OciHelmChartConfig;
import io.harness.cdng.manifest.yaml.OciHelmChartStoreGenericConfig;
import io.harness.cdng.manifest.yaml.S3StoreConfig;
import io.harness.cdng.manifest.yaml.ValuesManifestOutcome;
import io.harness.cdng.manifest.yaml.harness.HarnessStore;
import io.harness.cdng.manifest.yaml.oci.OciHelmChartStoreConfigWrapper;
import io.harness.cdng.manifest.yaml.storeConfig.StoreConfig;
import io.harness.cdng.stepsdependency.constants.OutcomeExpressionConstants;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.connector.ConnectorResponseDTO;
import io.harness.connector.services.ConnectorService;
import io.harness.connector.validator.scmValidators.GitConfigAuthenticationInfoHelper;
import io.harness.delegate.beans.connector.awsconnector.AwsConnectorDTO;
import io.harness.delegate.beans.connector.awsconnector.AwsCredentialDTO;
import io.harness.delegate.beans.connector.awsconnector.AwsCredentialType;
import io.harness.delegate.beans.connector.gcpconnector.GcpConnectorCredentialDTO;
import io.harness.delegate.beans.connector.gcpconnector.GcpConnectorDTO;
import io.harness.delegate.beans.connector.gcpconnector.GcpCredentialType;
import io.harness.delegate.beans.connector.helm.HttpHelmAuthType;
import io.harness.delegate.beans.connector.helm.HttpHelmAuthenticationDTO;
import io.harness.delegate.beans.connector.helm.HttpHelmConnectorDTO;
import io.harness.delegate.beans.connector.helm.OciHelmAuthType;
import io.harness.delegate.beans.connector.helm.OciHelmAuthenticationDTO;
import io.harness.delegate.beans.connector.helm.OciHelmConnectorDTO;
import io.harness.delegate.beans.connector.k8Connector.K8sTaskCapabilityHelper;
import io.harness.delegate.beans.connector.k8Connector.KubernetesClusterConfigDTO;
import io.harness.delegate.beans.connector.k8Connector.KubernetesCredentialDTO;
import io.harness.delegate.beans.connector.k8Connector.KubernetesCredentialType;
import io.harness.delegate.beans.connector.rancher.RancherTaskCapabilityHelper;
import io.harness.delegate.beans.connector.scm.GitAuthType;
import io.harness.delegate.beans.connector.scm.genericgitconnector.GitConfigDTO;
import io.harness.delegate.beans.executioncapability.GitConnectionNGCapability;
import io.harness.delegate.beans.logstreaming.CommandUnitProgress;
import io.harness.delegate.beans.logstreaming.CommandUnitsProgress;
import io.harness.delegate.beans.logstreaming.UnitProgressData;
import io.harness.delegate.beans.logstreaming.UnitProgressDataMapper;
import io.harness.delegate.beans.storeconfig.CustomRemoteStoreDelegateConfig;
import io.harness.delegate.beans.storeconfig.GcsHelmStoreDelegateConfig;
import io.harness.delegate.beans.storeconfig.GitStoreDelegateConfig;
import io.harness.delegate.beans.storeconfig.HttpHelmStoreDelegateConfig;
import io.harness.delegate.beans.storeconfig.LocalFileStoreDelegateConfig;
import io.harness.delegate.beans.storeconfig.OciHelmStoreDelegateConfig;
import io.harness.delegate.beans.storeconfig.S3HelmStoreDelegateConfig;
import io.harness.delegate.task.TaskParameters;
import io.harness.delegate.task.git.GitFetchFilesConfig;
import io.harness.delegate.task.git.GitFetchRequest;
import io.harness.delegate.task.git.GitFetchResponse;
import io.harness.delegate.task.git.TaskStatus;
import io.harness.delegate.task.helm.HelmFetchFileConfig;
import io.harness.delegate.task.helm.HelmFetchFileResult;
import io.harness.delegate.task.helm.HelmInstallCommandRequestNG;
import io.harness.delegate.task.helm.HelmValuesFetchRequest;
import io.harness.delegate.task.helm.HelmValuesFetchResponse;
import io.harness.delegate.task.k8s.DirectK8sInfraDelegateConfig;
import io.harness.delegate.task.k8s.HelmChartManifestDelegateConfig;
import io.harness.delegate.task.k8s.K8sInfraDelegateConfig;
import io.harness.delegate.task.k8s.ManifestDelegateConfig;
import io.harness.delegate.task.k8s.ManifestType;
import io.harness.delegate.task.k8s.RancherK8sInfraDelegateConfig;
import io.harness.delegate.task.localstore.LocalStoreFetchFilesResult;
import io.harness.delegate.task.localstore.ManifestFiles;
import io.harness.delegate.task.manifests.request.CustomManifestValuesFetchParams;
import io.harness.delegate.task.manifests.response.CustomManifestValuesFetchResponse;
import io.harness.exception.ExceptionUtils;
import io.harness.exception.GeneralException;
import io.harness.exception.InvalidRequestException;
import io.harness.filestore.dto.node.FileNodeDTO;
import io.harness.filestore.dto.node.FileStoreNodeDTO;
import io.harness.filestore.dto.node.FolderNodeDTO;
import io.harness.filestore.service.FileStoreService;
import io.harness.filestore.utils.FileStoreNodeUtils;
import io.harness.git.model.FetchFilesResult;
import io.harness.git.model.GitFile;
import io.harness.helm.HelmSubCommandType;
import io.harness.k8s.K8sCommandUnitConstants;
import io.harness.k8s.model.HelmVersion;
import io.harness.logging.CommandExecutionStatus;
import io.harness.logging.LogCallback;
import io.harness.logging.LogLevel;
import io.harness.logging.UnitProgress;
import io.harness.logging.UnitStatus;
import io.harness.manifest.CustomSourceFile;
import io.harness.ng.core.filestore.FileUsage;
import io.harness.plancreator.steps.TaskSelectorYaml;
import io.harness.plancreator.steps.common.StepElementParameters;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.execution.failure.FailureData;
import io.harness.pms.contracts.execution.failure.FailureType;
import io.harness.pms.contracts.execution.tasks.TaskRequest;
import io.harness.pms.contracts.refobjects.RefObject;
import io.harness.pms.contracts.refobjects.RefType;
import io.harness.pms.data.OrchestrationRefType;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.expression.EngineExpressionService;
import io.harness.pms.rbac.PipelineRbacHelper;
import io.harness.pms.sdk.core.data.OptionalOutcome;
import io.harness.pms.sdk.core.execution.SdkGraphVisualizationDataService;
import io.harness.pms.sdk.core.execution.invokers.StrategyHelper;
import io.harness.pms.sdk.core.resolver.outcome.OutcomeService;
import io.harness.pms.sdk.core.steps.executables.TaskChainResponse;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.pms.yaml.ParameterField;
import io.harness.rule.Owner;
import io.harness.serializer.KryoSerializer;
import io.harness.steps.EntityReferenceExtractorUtils;
import io.harness.steps.StepHelper;
import io.harness.supplier.ThrowingSupplier;
import io.harness.tasks.ResponseData;

import software.wings.beans.ServiceHookDelegateConfig;
import software.wings.beans.TaskType;

import com.google.common.collect.ImmutableMap;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.joor.Reflect;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Spy;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

public class NativeHelmStepHelperTest extends CategoryTest {
  @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();
  @Mock private ConnectorService connectorService;

  @Mock private OutcomeService outcomeService;
  @Mock private NativeHelmStepExecutor nativeHelmStepExecutor;
  @Mock private KryoSerializer kryoSerializer;
  @Mock private CDFeatureFlagHelper cdFeatureFlagHelper;
  @Mock private EngineExpressionService engineExpressionService;
  @Mock private SdkGraphVisualizationDataService sdkGraphVisualizationDataService;

  // internally used fields -- don't remove
  @Mock private PipelineRbacHelper pipelineRbacHelper;
  @Mock private EntityReferenceExtractorUtils entityReferenceExtractorUtils;
  @Mock private StepHelper stepHelper;
  @Mock private GitConfigAuthenticationInfoHelper gitConfigAuthenticationInfoHelper;
  @Mock private FileStoreService fileStoreService;
  @Mock private FileStoreNodeUtils fileStoreNodeUtils;

  @Spy @InjectMocks private K8sManifestDelegateMapper manifestDelegateMapper;
  @Spy @InjectMocks private K8sEntityHelper k8sEntityHelper;
  @Spy @InjectMocks private NativeHelmStepHelper nativeHelmStepHelper;
  @Spy @InjectMocks private CDStepHelper cdStepHelper;

  @Mock private LogCallback mockLogCallback;
  private final Ambiance ambiance = Ambiance.newBuilder()
                                        .putSetupAbstractions(SetupAbstractionKeys.accountId, "test-account")
                                        .putSetupAbstractions(SetupAbstractionKeys.orgIdentifier, "test-org")
                                        .putSetupAbstractions(SetupAbstractionKeys.projectIdentifier, "test-project")
                                        .build();
  private static final String SOME_URL = "https://url.com/owner/repo.git";
  private static final String INFRA_KEY = "svcId_envId";
  private static final String ENCODED_INFRA_KEY = "c26979e4-1d8c-344e-8181-45f484c57fe5";
  @Before
  public void setup() {
    doReturn(mockLogCallback).when(cdStepHelper).getLogCallback(any(), eq(ambiance), anyBoolean());
    doReturn(true)
        .when(cdFeatureFlagHelper)
        .isEnabled(AmbianceUtils.getAccountId(ambiance), FeatureName.USE_LATEST_CHARTMUSEUM_VERSION);
    doAnswer(invocation -> invocation.getArgument(1, String.class))
        .when(engineExpressionService)
        .renderExpression(eq(ambiance), any());
    Reflect.on(nativeHelmStepHelper).set("cdStepHelper", cdStepHelper);
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testGetHelmChartManifestsOutcome() {
    HelmChartManifestOutcome helmChartManifestOutcome =
        HelmChartManifestOutcome.builder()
            .helmVersion(HelmVersion.V3)
            .store(GitStore.builder().build())
            .skipResourceVersioning(ParameterField.createValueField(true))
            .build();
    ValuesManifestOutcome valuesManifestOutcome = ValuesManifestOutcome.builder().build();
    List<ManifestOutcome> manifestOutcomes = new ArrayList<>();
    manifestOutcomes.add(helmChartManifestOutcome);
    manifestOutcomes.add(valuesManifestOutcome);

    assertThat(nativeHelmStepHelper.getHelmSupportedManifestOutcome(manifestOutcomes))
        .isEqualTo(helmChartManifestOutcome);
  }

  @Test
  @Owner(developers = PRATYUSH)
  @Category(UnitTests.class)
  public void testGetHelmChartManifestsOutcomeWithHarnessAndInheritFromManifestStore() {
    HelmChartManifestOutcome helmChartManifestOutcome =
        HelmChartManifestOutcome.builder()
            .identifier("HelmChart")
            .helmVersion(HelmVersion.V3)
            .skipResourceVersioning(ParameterField.createValueField(true))
            .store(HarnessStore.builder().build())
            .build();
    ValuesManifestOutcome valuesManifestOutcome =
        ValuesManifestOutcome.builder().store(InheritFromManifestStoreConfig.builder().build()).build();
    List<ManifestOutcome> manifestOutcomes = new ArrayList<>();
    manifestOutcomes.add(helmChartManifestOutcome);
    manifestOutcomes.add(valuesManifestOutcome);

    assertThatThrownBy(() -> nativeHelmStepHelper.getHelmSupportedManifestOutcome(manifestOutcomes))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage(
            "InheritFromManifest store type is not supported with Manifest identifier: HelmChart, Manifest type: HelmChart, Manifest store type: Harness");
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
                       .paths(ParameterField.createValueField(asList("file1", "file2")))
                       .build())
            .skipResourceVersioning(ParameterField.createValueField(true))
            .helmVersion(HelmVersion.V3)
            .commandFlags(commandFlags)
            .build();

    doReturn(
        Optional.of(ConnectorResponseDTO.builder()
                        .connector(ConnectorInfoDTO.builder().connectorConfig(GitConfigDTO.builder().build()).build())
                        .build()))
        .when(connectorService)
        .get(any(), any(), any(), any());

    ManifestDelegateConfig delegateConfig = nativeHelmStepHelper.getManifestDelegateConfig(manifestOutcome, ambiance);
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
  public void testGetManifestDelegateConfigForHelmChartUsingHarnessStore() {
    List<String> files = asList("account:/path/to/helm/chart");
    HelmChartManifestOutcome manifestOutcome =
        HelmChartManifestOutcome.builder()
            .store(HarnessStore.builder().files(ParameterField.createValueField(files)).build())
            .helmVersion(HelmVersion.V3)
            .build();

    FileStoreNodeDTO folderStoreNodeDTO = getFolderStoreNode("/path/to/helm/chart", "chart");
    doReturn(Optional.of(folderStoreNodeDTO))
        .when(fileStoreService)
        .getWithChildrenByPath(any(), any(), any(), any(), eq(false));
    ManifestDelegateConfig delegateConfig2 = nativeHelmStepHelper.getManifestDelegateConfig(manifestOutcome, ambiance);
    assertThat(delegateConfig2.getManifestType()).isEqualTo(ManifestType.HELM_CHART);
    assertThat(delegateConfig2).isInstanceOf(HelmChartManifestDelegateConfig.class);
    HelmChartManifestDelegateConfig helmChartDelegateConfig2 = (HelmChartManifestDelegateConfig) delegateConfig2;
    assertThat(helmChartDelegateConfig2.getStoreDelegateConfig()).isNotNull();
    assertThat(helmChartDelegateConfig2.getStoreDelegateConfig()).isInstanceOf(LocalFileStoreDelegateConfig.class);
    LocalFileStoreDelegateConfig localFileStoreDelegateConfig2 =
        (LocalFileStoreDelegateConfig) helmChartDelegateConfig2.getStoreDelegateConfig();
    assertThat(localFileStoreDelegateConfig2.getFilePaths()).isEqualTo(asList("path/to/helm/chart"));
    assertThat(localFileStoreDelegateConfig2.getFolder()).isEqualTo("chart");
  }

  @Test
  @Owner(developers = PRATYUSH)
  @Category(UnitTests.class)
  public void testGetManifestDelegateConfigForHelmChartUsingCustomRemoteStore() {
    String chartPath = "/path/to/helm/chart";
    String extractionScript = "git clone something.git";
    HelmChartManifestOutcome manifestOutcome =
        HelmChartManifestOutcome.builder()
            .store(CustomRemoteStoreConfig.builder()
                       .filePath(ParameterField.createValueField(chartPath))
                       .extractionScript(ParameterField.createValueField(extractionScript))
                       .build())
            .helmVersion(HelmVersion.V3)
            .build();

    ManifestDelegateConfig delegateConfig = nativeHelmStepHelper.getManifestDelegateConfig(manifestOutcome, ambiance);
    assertThat(delegateConfig.getManifestType()).isEqualTo(ManifestType.HELM_CHART);
    assertThat(delegateConfig).isInstanceOf(HelmChartManifestDelegateConfig.class);
    HelmChartManifestDelegateConfig helmChartDelegateConfig = (HelmChartManifestDelegateConfig) delegateConfig;
    assertThat(helmChartDelegateConfig.getStoreDelegateConfig()).isNotNull();
    assertThat(helmChartDelegateConfig.getStoreDelegateConfig()).isInstanceOf(CustomRemoteStoreDelegateConfig.class);
    CustomRemoteStoreDelegateConfig customRemoteStoreDelegateConfig =
        (CustomRemoteStoreDelegateConfig) helmChartDelegateConfig.getStoreDelegateConfig();
    assertThat(customRemoteStoreDelegateConfig.getCustomManifestSource().getFilePaths()).isEqualTo(asList(chartPath));
    assertThat(customRemoteStoreDelegateConfig.getCustomManifestSource().getScript()).isEqualTo(extractionScript);
    assertThat(customRemoteStoreDelegateConfig.getCustomManifestSource().getAccountId()).isEqualTo("test-account");
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

    doReturn(Optional.of(ConnectorResponseDTO.builder()
                             .connector(ConnectorInfoDTO.builder()
                                            .identifier("http-helm-connector")
                                            .connectorType(HTTP_HELM_REPO)
                                            .connectorConfig(httpHelmConnectorConfig)
                                            .build())
                             .build()))
        .when(connectorService)
        .get(any(), any(), any(), any());

    K8sDirectInfrastructureOutcome k8sDirectInfrastructureOutcome =
        K8sDirectInfrastructureOutcome.builder().namespace("default").infrastructureKey(INFRA_KEY).build();
    doReturn(k8sDirectInfrastructureOutcome).when(cdStepHelper).getInfrastructureOutcome(eq(ambiance));

    ManifestDelegateConfig delegateConfig = nativeHelmStepHelper.getManifestDelegateConfig(manifestOutcome, ambiance);
    assertThat(delegateConfig.getManifestType()).isEqualTo(ManifestType.HELM_CHART);
    assertThat(delegateConfig).isInstanceOf(HelmChartManifestDelegateConfig.class);
    HelmChartManifestDelegateConfig helmChartDelegateConfig = (HelmChartManifestDelegateConfig) delegateConfig;
    assertThat(helmChartDelegateConfig.getStoreDelegateConfig()).isNotNull();
    assertThat(helmChartDelegateConfig.getStoreDelegateConfig()).isInstanceOf(HttpHelmStoreDelegateConfig.class);
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
    K8sDirectInfrastructureOutcome k8sDirectInfrastructureOutcome =
        K8sDirectInfrastructureOutcome.builder().namespace("default").infrastructureKey(INFRA_KEY).build();

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

    doReturn(Optional.of(ConnectorResponseDTO.builder()
                             .connector(ConnectorInfoDTO.builder()
                                            .identifier("aws-helm-connector")
                                            .connectorType(AWS)
                                            .connectorConfig(awsConnectorConfig)
                                            .build())
                             .build()))
        .when(connectorService)
        .get(any(), any(), any(), any());
    doReturn(k8sDirectInfrastructureOutcome).when(cdStepHelper).getInfrastructureOutcome(eq(ambiance));

    ManifestDelegateConfig delegateConfig = nativeHelmStepHelper.getManifestDelegateConfig(manifestOutcome, ambiance);
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
    K8sDirectInfrastructureOutcome k8sDirectInfrastructureOutcome =
        K8sDirectInfrastructureOutcome.builder().namespace("default").infrastructureKey(INFRA_KEY).build();

    doReturn(Optional.of(ConnectorResponseDTO.builder()
                             .connector(ConnectorInfoDTO.builder()
                                            .identifier("gcp-helm-connector")
                                            .connectorType(GCP)
                                            .connectorConfig(gcpConnectorDTO)
                                            .build())
                             .build()))
        .when(connectorService)
        .get(any(), any(), any(), any());
    doReturn(k8sDirectInfrastructureOutcome).when(cdStepHelper).getInfrastructureOutcome(eq(ambiance));

    ManifestDelegateConfig delegateConfig =
        nativeHelmStepHelper.getManifestDelegateConfig(helmChartManifestOutcome, ambiance);
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
  @Owner(developers = ACASIAN)
  @Category(UnitTests.class)
  public void testShouldPrepareHelmGitValuesFetchTask() {
    K8sDirectInfrastructureOutcome k8sDirectInfrastructureOutcome =
        K8sDirectInfrastructureOutcome.builder().namespace("default").build();
    GitStore gitStore = GitStore.builder()
                            .branch(ParameterField.createValueField("master"))
                            .folderPath(ParameterField.createValueField("path/to/helm/chart"))
                            .connectorRef(ParameterField.createValueField("git-connector"))
                            .build();
    HelmChartManifestOutcome helmChartManifestOutcome =
        HelmChartManifestOutcome.builder()
            .identifier("helm")
            .store(gitStore)
            .valuesPaths(ParameterField.createValueField(asList("path/to/helm/chart/valuesOverride.yaml")))
            .build();
    Map<String, ManifestOutcome> manifestOutcomeMap = ImmutableMap.of("helm", helmChartManifestOutcome);
    RefObject manifests = RefObject.newBuilder()
                              .setName(OutcomeExpressionConstants.MANIFESTS)
                              .setKey(OutcomeExpressionConstants.MANIFESTS)
                              .setRefType(RefType.newBuilder().setType(OrchestrationRefType.OUTCOME).build())
                              .build();

    RefObject infra = RefObject.newBuilder()
                          .setName(OutcomeExpressionConstants.INFRASTRUCTURE_OUTCOME)
                          .setKey(OutcomeExpressionConstants.INFRASTRUCTURE_OUTCOME)
                          .setRefType(RefType.newBuilder().setType(OrchestrationRefType.OUTCOME).build())
                          .build();

    StepElementParameters stepElementParameters =
        StepElementParameters.builder().spec(HelmDeployStepParams.infoBuilder().build()).build();
    OptionalOutcome manifestsOutcome =
        OptionalOutcome.builder().found(true).outcome(new ManifestsOutcome(manifestOutcomeMap)).build();
    doReturn(manifestsOutcome).when(outcomeService).resolveOptional(eq(ambiance), eq(manifests));
    doReturn(k8sDirectInfrastructureOutcome).when(outcomeService).resolve(eq(ambiance), eq(infra));

    doReturn(
        Optional.of(ConnectorResponseDTO.builder()
                        .connector(ConnectorInfoDTO.builder()
                                       .connectorConfig(
                                           GitConfigDTO.builder().gitAuthType(GitAuthType.HTTP).url(SOME_URL).build())
                                       .name("test")
                                       .build())
                        .build()))
        .when(connectorService)
        .get(any(), any(), any(), any());

    TaskChainResponse taskChainResponse =
        nativeHelmStepHelper.startChainLink(nativeHelmStepExecutor, ambiance, stepElementParameters);
    assertThat(taskChainResponse).isNotNull();
    assertThat(taskChainResponse.getPassThroughData()).isNotNull();
    assertThat(taskChainResponse.getPassThroughData()).isInstanceOf(K8sStepPassThroughData.class);
    K8sStepPassThroughData passThroughData = (K8sStepPassThroughData) taskChainResponse.getPassThroughData();
    assertThat(passThroughData.getValuesManifestOutcomes()).isNotEmpty();
    assertThat(passThroughData.getValuesManifestOutcomes().size()).isEqualTo(1);
    List<ValuesManifestOutcome> valuesManifestOutcome = passThroughData.getValuesManifestOutcomes();
    assertThat(valuesManifestOutcome.get(0).getIdentifier()).isEqualTo(helmChartManifestOutcome.getIdentifier());
    assertThat(valuesManifestOutcome.get(0).getStore()).isEqualTo(helmChartManifestOutcome.getStore());
    ArgumentCaptor<Object> argumentCaptor = ArgumentCaptor.forClass(Object.class);
    verify(kryoSerializer, times(3)).asDeflatedBytes(argumentCaptor.capture());
    TaskParameters taskParameters = (TaskParameters) argumentCaptor.getAllValues().get(0);
    assertThat(taskParameters).isInstanceOf(GitFetchRequest.class);
    GitFetchRequest gitFetchRequest = (GitFetchRequest) taskParameters;
    assertThat(gitFetchRequest.getGitFetchFilesConfigs()).isNotEmpty();
    assertThat(gitFetchRequest.getGitFetchFilesConfigs().size()).isEqualTo(2);
    List<GitFetchFilesConfig> gitFetchFilesConfigs = gitFetchRequest.getGitFetchFilesConfigs();
    assertThat(gitFetchFilesConfigs.get(0).getGitStoreDelegateConfig().getPaths()).isNotEmpty();
    assertThat(gitFetchFilesConfigs.get(0).getGitStoreDelegateConfig().getPaths().size()).isEqualTo(1);
    assertThat(gitFetchFilesConfigs.get(0).getGitStoreDelegateConfig().getPaths().get(0))
        .isEqualTo("path/to/helm/chart/values.yaml");
    assertThat(gitFetchFilesConfigs.get(1).getGitStoreDelegateConfig().getPaths()).isNotEmpty();
    assertThat(gitFetchFilesConfigs.get(1).getGitStoreDelegateConfig().getPaths().size()).isEqualTo(1);
    assertThat(gitFetchFilesConfigs.get(1).getGitStoreDelegateConfig().getPaths().get(0))
        .isEqualTo("path/to/helm/chart/valuesOverride.yaml");
    assertThat(argumentCaptor.getAllValues().get(1)).isInstanceOf(GitConnectionNGCapability.class);
  }

  @Test
  @Owner(developers = PRATYUSH)
  @Category(UnitTests.class)
  public void testShouldPrepareHelmGitValuesFetchTaskWithValuesOverride() {
    K8sDirectInfrastructureOutcome k8sDirectInfrastructureOutcome =
        K8sDirectInfrastructureOutcome.builder().namespace("default").build();
    GitStore gitStore = GitStore.builder()
                            .branch(ParameterField.createValueField("master"))
                            .folderPath(ParameterField.createValueField("path/to/helm/chart"))
                            .connectorRef(ParameterField.createValueField("git-connector"))
                            .build();
    HelmChartManifestOutcome helmChartManifestOutcome =
        HelmChartManifestOutcome.builder()
            .identifier("helm")
            .store(gitStore)
            .valuesPaths(ParameterField.createValueField(asList("path/to/helm/chart/valuesOverride.yaml")))
            .build();
    List<String> overridePaths = new ArrayList<>(asList("folderPath/values2.yaml", "folderPath/values3.yaml"));
    GitStore gitStore2 = GitStore.builder()
                             .branch(ParameterField.createValueField("master"))
                             .paths(ParameterField.createValueField(overridePaths))
                             .connectorRef(ParameterField.createValueField("git-connector"))
                             .build();
    List<String> files = asList("org:/folderPath/values5.yaml");
    HarnessStore harnessStore = HarnessStore.builder().files(ParameterField.createValueField(files)).build();
    ValuesManifestOutcome valuesManifestOutcome1 =
        ValuesManifestOutcome.builder().identifier("helmOverride").store(gitStore2).build();
    InheritFromManifestStoreConfig inheritFromManifestStore =
        InheritFromManifestStoreConfig.builder()
            .paths(ParameterField.createValueField(asList("path/to/helm/chart/values4.yaml")))
            .build();
    ValuesManifestOutcome valuesManifestOutcome2 =
        ValuesManifestOutcome.builder().identifier("helmOverride2").store(inheritFromManifestStore).build();
    ValuesManifestOutcome valuesManifestOutcome3 =
        ValuesManifestOutcome.builder().identifier("helmOverride3").store(harnessStore).build();
    Map<String, ManifestOutcome> manifestOutcomeMap = ImmutableMap.of("helm", helmChartManifestOutcome, "helmOverride",
        valuesManifestOutcome1, "helmOverride2", valuesManifestOutcome2, "helmOverride3", valuesManifestOutcome3);
    RefObject manifests = RefObject.newBuilder()
                              .setName(OutcomeExpressionConstants.MANIFESTS)
                              .setKey(OutcomeExpressionConstants.MANIFESTS)
                              .setRefType(RefType.newBuilder().setType(OrchestrationRefType.OUTCOME).build())
                              .build();

    RefObject infra = RefObject.newBuilder()
                          .setName(OutcomeExpressionConstants.INFRASTRUCTURE_OUTCOME)
                          .setKey(OutcomeExpressionConstants.INFRASTRUCTURE_OUTCOME)
                          .setRefType(RefType.newBuilder().setType(OrchestrationRefType.OUTCOME).build())
                          .build();

    StepElementParameters stepElementParameters =
        StepElementParameters.builder().spec(HelmDeployStepParams.infoBuilder().build()).build();
    OptionalOutcome manifestsOutcome =
        OptionalOutcome.builder().found(true).outcome(new ManifestsOutcome(manifestOutcomeMap)).build();
    doReturn(manifestsOutcome).when(outcomeService).resolveOptional(eq(ambiance), eq(manifests));
    doReturn(k8sDirectInfrastructureOutcome).when(outcomeService).resolve(eq(ambiance), eq(infra));

    doReturn(
        Optional.of(ConnectorResponseDTO.builder()
                        .connector(ConnectorInfoDTO.builder()
                                       .connectorConfig(
                                           GitConfigDTO.builder().gitAuthType(GitAuthType.HTTP).url(SOME_URL).build())
                                       .name("test")
                                       .build())
                        .build()))
        .when(connectorService)
        .get(any(), any(), any(), any());

    doReturn(Optional.of(getFileStoreNode("folderPath/values5.yaml", "values5.yaml")))
        .when(fileStoreService)
        .getWithChildrenByPath(any(), any(), any(), any(), eq(true));
    TaskChainResponse taskChainResponse =
        nativeHelmStepHelper.startChainLink(nativeHelmStepExecutor, ambiance, stepElementParameters);
    assertThat(taskChainResponse).isNotNull();
    assertThat(taskChainResponse.getPassThroughData()).isNotNull();
    assertThat(taskChainResponse.getPassThroughData()).isInstanceOf(K8sStepPassThroughData.class);
    K8sStepPassThroughData passThroughData = (K8sStepPassThroughData) taskChainResponse.getPassThroughData();
    assertThat(passThroughData.getValuesManifestOutcomes()).isNotEmpty();
    assertThat(passThroughData.getValuesManifestOutcomes().size()).isEqualTo(4);
    List<ValuesManifestOutcome> valuesManifestOutcome = passThroughData.getValuesManifestOutcomes();
    assertThat(valuesManifestOutcome.get(0).getIdentifier()).isEqualTo(helmChartManifestOutcome.getIdentifier());
    assertThat(valuesManifestOutcome.get(0).getStore()).isEqualTo(helmChartManifestOutcome.getStore());
    assertThat(valuesManifestOutcome.get(1).getIdentifier()).isEqualTo(valuesManifestOutcome1.getIdentifier());
    assertThat(valuesManifestOutcome.get(1).getStore()).isEqualTo(valuesManifestOutcome1.getStore());
    assertThat(valuesManifestOutcome.get(3).getIdentifier()).isEqualTo(valuesManifestOutcome2.getIdentifier());
    assertThat(valuesManifestOutcome.get(3).getStore()).isEqualTo(valuesManifestOutcome2.getStore());
    assertThat(valuesManifestOutcome.get(2).getIdentifier()).isEqualTo(valuesManifestOutcome3.getIdentifier());
    assertThat(valuesManifestOutcome.get(2).getStore()).isEqualTo(valuesManifestOutcome3.getStore());
    Map<String, LocalStoreFetchFilesResult> localStoreFetchFilesResultMap =
        passThroughData.getLocalStoreFileMapContents();
    assertThat(localStoreFetchFilesResultMap.size()).isEqualTo(1);
    assertThat(localStoreFetchFilesResultMap.get("helmOverride3").getLocalStoreFileContents().size()).isEqualTo(1);
    assertThat(localStoreFetchFilesResultMap.get("helmOverride3").getLocalStoreFileContents().get(0)).isEqualTo("Test");
    List<ManifestFiles> manifestFiles = passThroughData.getManifestFiles();
    assertThat(manifestFiles.size()).isEqualTo(0);
    ArgumentCaptor<Object> argumentCaptor = ArgumentCaptor.forClass(Object.class);
    verify(kryoSerializer, times(5)).asDeflatedBytes(argumentCaptor.capture());
    TaskParameters taskParameters = (TaskParameters) argumentCaptor.getAllValues().get(0);
    assertThat(taskParameters).isInstanceOf(GitFetchRequest.class);
    GitFetchRequest gitFetchRequest = (GitFetchRequest) taskParameters;
    assertThat(gitFetchRequest.getGitFetchFilesConfigs()).isNotEmpty();
    assertThat(gitFetchRequest.getGitFetchFilesConfigs().size()).isEqualTo(4);
    List<GitFetchFilesConfig> gitFetchFilesConfigs = gitFetchRequest.getGitFetchFilesConfigs();
    assertThat(gitFetchFilesConfigs.get(1).getGitStoreDelegateConfig().getPaths()).isNotEmpty();
    assertThat(gitFetchFilesConfigs.get(1).getGitStoreDelegateConfig().getPaths().size()).isEqualTo(1);
    assertThat(gitFetchFilesConfigs.get(1).getGitStoreDelegateConfig().getPaths().get(0))
        .isEqualTo("path/to/helm/chart/values.yaml");
    assertThat(gitFetchFilesConfigs.get(2).getGitStoreDelegateConfig().getPaths()).isNotEmpty();
    assertThat(gitFetchFilesConfigs.get(2).getGitStoreDelegateConfig().getPaths().size()).isEqualTo(1);
    assertThat(gitFetchFilesConfigs.get(2).getGitStoreDelegateConfig().getPaths().get(0))
        .isEqualTo("path/to/helm/chart/valuesOverride.yaml");
    assertThat(gitFetchFilesConfigs.get(0).getGitStoreDelegateConfig().getPaths()).isNotEmpty();
    assertThat(gitFetchFilesConfigs.get(0).getGitStoreDelegateConfig().getPaths().size()).isEqualTo(2);
    assertThat(gitFetchFilesConfigs.get(0).getGitStoreDelegateConfig().getPaths()).isEqualTo(overridePaths);
    assertThat(gitFetchFilesConfigs.get(3).getGitStoreDelegateConfig().getPaths()).isNotEmpty();
    assertThat(gitFetchFilesConfigs.get(3).getGitStoreDelegateConfig().getPaths().size()).isEqualTo(1);
    assertThat(gitFetchFilesConfigs.get(3).getGitStoreDelegateConfig().getPaths().get(0))
        .isEqualTo("path/to/helm/chart/values4.yaml");
    assertThat(argumentCaptor.getAllValues().get(1)).isInstanceOf(GitConnectionNGCapability.class);
  }

  @Test
  @Owner(developers = ACASIAN)
  @Category(UnitTests.class)
  public void testShouldPrepareHelmS3ValuesFetchTask() {
    K8sDirectInfrastructureOutcome k8sDirectInfrastructureOutcome =
        K8sDirectInfrastructureOutcome.builder().namespace("default").infrastructureKey(INFRA_KEY).build();
    S3StoreConfig s3Store = S3StoreConfig.builder()
                                .bucketName(ParameterField.createValueField("bucket"))
                                .region(ParameterField.createValueField("us-east-1"))
                                .folderPath(ParameterField.createValueField("path/to/helm/chart"))
                                .connectorRef(ParameterField.createValueField("aws-connector"))
                                .build();

    HelmChartManifestOutcome helmChartManifestOutcome =
        HelmChartManifestOutcome.builder()
            .identifier("helm")
            .store(s3Store)
            .chartName(ParameterField.createValueField("chart"))
            .valuesPaths(ParameterField.createValueField(asList("valuesOverride.yaml")))
            .build();
    Map<String, ManifestOutcome> manifestOutcomeMap = ImmutableMap.of("helm", helmChartManifestOutcome);
    RefObject manifests = RefObject.newBuilder()
                              .setName(OutcomeExpressionConstants.MANIFESTS)
                              .setKey(OutcomeExpressionConstants.MANIFESTS)
                              .setRefType(RefType.newBuilder().setType(OrchestrationRefType.OUTCOME).build())
                              .build();

    RefObject infra = RefObject.newBuilder()
                          .setName(OutcomeExpressionConstants.INFRASTRUCTURE_OUTCOME)
                          .setKey(OutcomeExpressionConstants.INFRASTRUCTURE_OUTCOME)
                          .setRefType(RefType.newBuilder().setType(OrchestrationRefType.OUTCOME).build())
                          .build();

    StepElementParameters stepElementParameters =
        StepElementParameters.builder().spec(HelmDeployStepParams.infoBuilder().build()).build();
    OptionalOutcome manifestsOutcome =
        OptionalOutcome.builder().found(true).outcome(new ManifestsOutcome(manifestOutcomeMap)).build();
    doReturn(manifestsOutcome).when(outcomeService).resolveOptional(eq(ambiance), eq(manifests));
    doReturn(k8sDirectInfrastructureOutcome).when(outcomeService).resolve(eq(ambiance), eq(infra));
    doReturn(k8sDirectInfrastructureOutcome).when(cdStepHelper).getInfrastructureOutcome(eq(ambiance));
    doReturn(Optional.of(
                 ConnectorResponseDTO.builder()
                     .connector(ConnectorInfoDTO.builder()
                                    .connectorConfig(
                                        AwsConnectorDTO.builder()
                                            .credential(AwsCredentialDTO.builder()
                                                            .awsCredentialType(AwsCredentialType.INHERIT_FROM_DELEGATE)
                                                            .build())
                                            .build())
                                    .name("helm-s3-repo-display")
                                    .identifier("helm-s3-repo")
                                    .connectorType(AWS)
                                    .build())
                     .build()))
        .when(connectorService)
        .get(any(), any(), any(), any());

    TaskChainResponse taskChainResponse =
        nativeHelmStepHelper.startChainLink(nativeHelmStepExecutor, ambiance, stepElementParameters);
    assertThat(taskChainResponse).isNotNull();
    assertThat(taskChainResponse.getPassThroughData()).isNotNull();
    assertThat(taskChainResponse.getPassThroughData()).isInstanceOf(K8sStepPassThroughData.class);
    ArgumentCaptor<Object> taskParametersArgumentCaptor = ArgumentCaptor.forClass(Object.class);
    verify(kryoSerializer, times(2)).asDeflatedBytes(taskParametersArgumentCaptor.capture());
    TaskParameters taskParameters = (TaskParameters) taskParametersArgumentCaptor.getAllValues().get(0);
    assertThat(taskParameters).isInstanceOf(HelmValuesFetchRequest.class);
    HelmValuesFetchRequest helmValuesFetchRequest = (HelmValuesFetchRequest) taskParameters;
    assertThat(helmValuesFetchRequest.getTimeout()).isNotNull();
    assertThat(helmValuesFetchRequest.getHelmChartManifestDelegateConfig().getStoreDelegateConfig())
        .isInstanceOf(S3HelmStoreDelegateConfig.class);
    S3HelmStoreDelegateConfig s3StoreConfig =
        (S3HelmStoreDelegateConfig) helmValuesFetchRequest.getHelmChartManifestDelegateConfig()
            .getStoreDelegateConfig();
    assertThat(s3StoreConfig.getBucketName()).isEqualTo("bucket");
    assertThat(s3StoreConfig.getRegion()).isEqualTo("us-east-1");
    assertThat(s3StoreConfig.getFolderPath()).isEqualTo("path/to/helm/chart");
    assertThat(s3StoreConfig.getRepoName()).isEqualTo("cb5f5f77-2f80-3cbe-8127-ec10513c9a66");
    assertThat(s3StoreConfig.getRepoDisplayName()).isEqualTo("helm-s3-repo-display");
    List<HelmFetchFileConfig> helmFetchFileConfigs = helmValuesFetchRequest.getHelmFetchFileConfigList();
    assertThat(helmFetchFileConfigs.size()).isEqualTo(2);
    assertThat(helmFetchFileConfigs.get(1).getIdentifier()).isEqualTo(helmChartManifestOutcome.getIdentifier());
    assertThat(helmFetchFileConfigs.get(1).getManifestType()).isEqualTo("HelmChart");
    assertThat(helmFetchFileConfigs.get(1).getFilePaths())
        .isEqualTo(helmChartManifestOutcome.getValuesPaths().getValue());
    assertThat(helmFetchFileConfigs.get(0).getIdentifier()).isEqualTo(helmChartManifestOutcome.getIdentifier());
    assertThat(helmFetchFileConfigs.get(0).getManifestType()).isEqualTo("HelmChart");
    assertThat(helmFetchFileConfigs.get(0).getFilePaths()).isEqualTo(asList("values.yaml"));
  }

  @Test
  @Owner(developers = PRATYUSH)
  @Category(UnitTests.class)
  public void testShouldPrepareHelmS3ValuesFetchTaskWithValuesOverride() {
    K8sDirectInfrastructureOutcome k8sDirectInfrastructureOutcome =
        K8sDirectInfrastructureOutcome.builder().namespace("default").infrastructureKey(INFRA_KEY).build();
    S3StoreConfig s3Store = S3StoreConfig.builder()
                                .bucketName(ParameterField.createValueField("bucket"))
                                .region(ParameterField.createValueField("us-east-1"))
                                .folderPath(ParameterField.createValueField("path/to/helm/chart"))
                                .connectorRef(ParameterField.createValueField("aws-connector"))
                                .build();

    HelmChartManifestOutcome helmChartManifestOutcome =
        HelmChartManifestOutcome.builder()
            .identifier("helm")
            .store(s3Store)
            .chartName(ParameterField.createValueField("chart"))
            .valuesPaths(ParameterField.createValueField(asList("valuesOverride.yaml")))
            .build();
    List<String> overridePaths = new ArrayList<>(asList("folderPath/values2.yaml", "folderPath/values3.yaml"));
    GitStore gitStore2 = GitStore.builder()
                             .branch(ParameterField.createValueField("master"))
                             .paths(ParameterField.createValueField(overridePaths))
                             .connectorRef(ParameterField.createValueField("git-connector"))
                             .build();
    ValuesManifestOutcome valuesManifestOutcome1 =
        ValuesManifestOutcome.builder().identifier("helmOverride").store(gitStore2).build();
    InheritFromManifestStoreConfig inheritFromManifestStore =
        InheritFromManifestStoreConfig.builder().paths(ParameterField.createValueField(asList("values4.yaml"))).build();
    ValuesManifestOutcome valuesManifestOutcome2 =
        ValuesManifestOutcome.builder().identifier("helmOverride2").store(inheritFromManifestStore).build();
    List<String> files = asList("org:/path/to/helm/chart");
    HarnessStore harnessStore = HarnessStore.builder().files(ParameterField.createValueField(files)).build();
    ValuesManifestOutcome valuesManifestOutcome3 =
        ValuesManifestOutcome.builder().identifier("helmOverride3").store(harnessStore).build();
    Map<String, ManifestOutcome> manifestOutcomeMap = ImmutableMap.of("helm", helmChartManifestOutcome, "helmOverride",
        valuesManifestOutcome1, "helmOverride2", valuesManifestOutcome2, "helmOverride3", valuesManifestOutcome3);
    RefObject manifests = RefObject.newBuilder()
                              .setName(OutcomeExpressionConstants.MANIFESTS)
                              .setKey(OutcomeExpressionConstants.MANIFESTS)
                              .setRefType(RefType.newBuilder().setType(OrchestrationRefType.OUTCOME).build())
                              .build();

    RefObject infra = RefObject.newBuilder()
                          .setName(OutcomeExpressionConstants.INFRASTRUCTURE_OUTCOME)
                          .setKey(OutcomeExpressionConstants.INFRASTRUCTURE_OUTCOME)
                          .setRefType(RefType.newBuilder().setType(OrchestrationRefType.OUTCOME).build())
                          .build();

    StepElementParameters stepElementParameters =
        StepElementParameters.builder().spec(HelmDeployStepParams.infoBuilder().build()).build();
    OptionalOutcome manifestsOutcome =
        OptionalOutcome.builder().found(true).outcome(new ManifestsOutcome(manifestOutcomeMap)).build();
    doReturn(manifestsOutcome).when(outcomeService).resolveOptional(eq(ambiance), eq(manifests));
    doReturn(k8sDirectInfrastructureOutcome).when(outcomeService).resolve(eq(ambiance), eq(infra));
    doReturn(k8sDirectInfrastructureOutcome).when(cdStepHelper).getInfrastructureOutcome(eq(ambiance));
    doReturn(Optional.of(
                 ConnectorResponseDTO.builder()
                     .connector(ConnectorInfoDTO.builder()
                                    .connectorConfig(
                                        AwsConnectorDTO.builder()
                                            .credential(AwsCredentialDTO.builder()
                                                            .awsCredentialType(AwsCredentialType.INHERIT_FROM_DELEGATE)
                                                            .build())
                                            .build())
                                    .name("helm-s3-repo-display")
                                    .identifier("helm-s3-repo")
                                    .connectorType(AWS)
                                    .build())
                     .build()))
        .when(connectorService)
        .get(any(), any(), any(), any());

    doReturn(Optional.of(getFileStoreNode("path/to/helm/chart/values5.yaml", "values5.yaml")))
        .when(fileStoreService)
        .getWithChildrenByPath(any(), any(), any(), any(), eq(true));
    TaskChainResponse taskChainResponse =
        nativeHelmStepHelper.startChainLink(nativeHelmStepExecutor, ambiance, stepElementParameters);
    assertThat(taskChainResponse).isNotNull();
    assertThat(taskChainResponse.getPassThroughData()).isNotNull();
    assertThat(taskChainResponse.getPassThroughData()).isInstanceOf(K8sStepPassThroughData.class);
    K8sStepPassThroughData passThroughData = (K8sStepPassThroughData) taskChainResponse.getPassThroughData();
    assertThat(passThroughData.getValuesManifestOutcomes()).isNotEmpty();
    assertThat(passThroughData.getValuesManifestOutcomes().size()).isEqualTo(3);
    List<ValuesManifestOutcome> valuesManifestOutcome = passThroughData.getValuesManifestOutcomes();
    assertThat(valuesManifestOutcome.get(0).getIdentifier()).isEqualTo(valuesManifestOutcome1.getIdentifier());
    assertThat(valuesManifestOutcome.get(0).getStore()).isEqualTo(valuesManifestOutcome1.getStore());
    assertThat(valuesManifestOutcome.get(2).getIdentifier()).isEqualTo(valuesManifestOutcome2.getIdentifier());
    assertThat(valuesManifestOutcome.get(2).getStore()).isEqualTo(valuesManifestOutcome2.getStore());
    assertThat(valuesManifestOutcome.get(1).getIdentifier()).isEqualTo(valuesManifestOutcome3.getIdentifier());
    assertThat(valuesManifestOutcome.get(1).getStore()).isEqualTo(valuesManifestOutcome3.getStore());
    Map<String, LocalStoreFetchFilesResult> localStoreFetchFilesResultMap =
        passThroughData.getLocalStoreFileMapContents();
    assertThat(localStoreFetchFilesResultMap.size()).isEqualTo(1);
    assertThat(localStoreFetchFilesResultMap.get("helmOverride3").getLocalStoreFileContents().size()).isEqualTo(1);
    assertThat(localStoreFetchFilesResultMap.get("helmOverride3").getLocalStoreFileContents().get(0)).isEqualTo("Test");
    List<ManifestFiles> manifestFiles = passThroughData.getManifestFiles();
    assertThat(manifestFiles.size()).isEqualTo(0);
    ArgumentCaptor<Object> taskParametersArgumentCaptor = ArgumentCaptor.forClass(Object.class);
    verify(kryoSerializer, times(2)).asDeflatedBytes(taskParametersArgumentCaptor.capture());
    TaskParameters taskParameters = (TaskParameters) taskParametersArgumentCaptor.getAllValues().get(0);
    assertThat(taskParameters).isInstanceOf(HelmValuesFetchRequest.class);
    HelmValuesFetchRequest helmValuesFetchRequest = (HelmValuesFetchRequest) taskParameters;
    assertThat(helmValuesFetchRequest.getTimeout()).isNotNull();
    assertThat(helmValuesFetchRequest.getHelmChartManifestDelegateConfig().getStoreDelegateConfig())
        .isInstanceOf(S3HelmStoreDelegateConfig.class);
    S3HelmStoreDelegateConfig s3StoreConfig =
        (S3HelmStoreDelegateConfig) helmValuesFetchRequest.getHelmChartManifestDelegateConfig()
            .getStoreDelegateConfig();
    assertThat(s3StoreConfig.getBucketName()).isEqualTo("bucket");
    assertThat(s3StoreConfig.getRegion()).isEqualTo("us-east-1");
    assertThat(s3StoreConfig.getFolderPath()).isEqualTo("path/to/helm/chart");
    assertThat(s3StoreConfig.getRepoName()).isEqualTo("cb5f5f77-2f80-3cbe-8127-ec10513c9a66");
    assertThat(s3StoreConfig.getRepoDisplayName()).isEqualTo("helm-s3-repo-display");
    List<HelmFetchFileConfig> helmFetchFileConfigs = helmValuesFetchRequest.getHelmFetchFileConfigList();
    assertThat(helmFetchFileConfigs.size()).isEqualTo(3);
    assertThat(helmFetchFileConfigs.get(1).getIdentifier()).isEqualTo(helmChartManifestOutcome.getIdentifier());
    assertThat(helmFetchFileConfigs.get(1).getManifestType()).isEqualTo("HelmChart");
    assertThat(helmFetchFileConfigs.get(1).getFilePaths())
        .isEqualTo(helmChartManifestOutcome.getValuesPaths().getValue());
    assertThat(helmFetchFileConfigs.get(0).getIdentifier()).isEqualTo(helmChartManifestOutcome.getIdentifier());
    assertThat(helmFetchFileConfigs.get(0).getManifestType()).isEqualTo("HelmChart");
    assertThat(helmFetchFileConfigs.get(0).getFilePaths()).isEqualTo(asList("values.yaml"));
    assertThat(helmFetchFileConfigs.get(2).getIdentifier()).isEqualTo(valuesManifestOutcome2.getIdentifier());
    assertThat(helmFetchFileConfigs.get(2).getManifestType()).isEqualTo(valuesManifestOutcome2.getType());
    assertThat(helmFetchFileConfigs.get(2).getFilePaths()).isEqualTo(asList("values4.yaml"));
  }

  @Test
  @Owner(developers = ACASIAN)
  @Category(UnitTests.class)
  public void testShouldPrepareHelmGcsValuesFetchTask() {
    K8sDirectInfrastructureOutcome k8sDirectInfrastructureOutcome =
        K8sDirectInfrastructureOutcome.builder().namespace("default").infrastructureKey(INFRA_KEY).build();
    GcsStoreConfig gcsStore = GcsStoreConfig.builder()
                                  .bucketName(ParameterField.createValueField("bucket"))
                                  .folderPath(ParameterField.createValueField("path/to/helm/chart"))
                                  .connectorRef(ParameterField.createValueField("gcs-connector"))
                                  .build();

    HelmChartManifestOutcome helmChartManifestOutcome =
        HelmChartManifestOutcome.builder()
            .identifier("helm")
            .store(gcsStore)
            .chartName(ParameterField.createValueField("chart"))
            .valuesPaths(ParameterField.createValueField(asList("valuesOverride.yaml")))
            .build();
    Map<String, ManifestOutcome> manifestOutcomeMap = ImmutableMap.of("helm", helmChartManifestOutcome);
    RefObject manifests = RefObject.newBuilder()
                              .setName(OutcomeExpressionConstants.MANIFESTS)
                              .setKey(OutcomeExpressionConstants.MANIFESTS)
                              .setRefType(RefType.newBuilder().setType(OrchestrationRefType.OUTCOME).build())
                              .build();

    RefObject infra = RefObject.newBuilder()
                          .setName(OutcomeExpressionConstants.INFRASTRUCTURE_OUTCOME)
                          .setKey(OutcomeExpressionConstants.INFRASTRUCTURE_OUTCOME)
                          .setRefType(RefType.newBuilder().setType(OrchestrationRefType.OUTCOME).build())
                          .build();

    StepElementParameters stepElementParameters =
        StepElementParameters.builder().spec(HelmDeployStepParams.infoBuilder().build()).build();
    OptionalOutcome manifestsOutcome =
        OptionalOutcome.builder().found(true).outcome(new ManifestsOutcome(manifestOutcomeMap)).build();
    doReturn(manifestsOutcome).when(outcomeService).resolveOptional(eq(ambiance), eq(manifests));
    doReturn(k8sDirectInfrastructureOutcome).when(outcomeService).resolve(eq(ambiance), eq(infra));

    doReturn(Optional.of(
                 ConnectorResponseDTO.builder()
                     .connector(ConnectorInfoDTO.builder()
                                    .connectorConfig(
                                        GcpConnectorDTO.builder()
                                            .credential(GcpConnectorCredentialDTO.builder()
                                                            .gcpCredentialType(GcpCredentialType.INHERIT_FROM_DELEGATE)
                                                            .build())
                                            .build())
                                    .name("helm-gcs-repo-display")
                                    .identifier("helm-gcs-repo")
                                    .connectorType(GCP)
                                    .build())
                     .build()))
        .when(connectorService)
        .get(any(), any(), any(), any());
    doReturn(k8sDirectInfrastructureOutcome).when(cdStepHelper).getInfrastructureOutcome(eq(ambiance));

    TaskChainResponse taskChainResponse =
        nativeHelmStepHelper.startChainLink(nativeHelmStepExecutor, ambiance, stepElementParameters);
    assertThat(taskChainResponse).isNotNull();
    assertThat(taskChainResponse.getPassThroughData()).isNotNull();
    assertThat(taskChainResponse.getPassThroughData()).isInstanceOf(K8sStepPassThroughData.class);
    ArgumentCaptor<Object> taskParametersArgumentCaptor = ArgumentCaptor.forClass(Object.class);
    verify(kryoSerializer, times(3)).asDeflatedBytes(taskParametersArgumentCaptor.capture());
    TaskParameters taskParameters = (TaskParameters) taskParametersArgumentCaptor.getAllValues().get(0);
    assertThat(taskParameters).isInstanceOf(HelmValuesFetchRequest.class);
    HelmValuesFetchRequest helmValuesFetchRequest = (HelmValuesFetchRequest) taskParameters;
    assertThat(helmValuesFetchRequest.getTimeout()).isNotNull();
    assertThat(helmValuesFetchRequest.getHelmChartManifestDelegateConfig().getStoreDelegateConfig())
        .isInstanceOf(GcsHelmStoreDelegateConfig.class);
    GcsHelmStoreDelegateConfig gcsStoreConfig =
        (GcsHelmStoreDelegateConfig) helmValuesFetchRequest.getHelmChartManifestDelegateConfig()
            .getStoreDelegateConfig();
    assertThat(gcsStoreConfig.getBucketName()).isEqualTo("bucket");
    assertThat(gcsStoreConfig.getFolderPath()).isEqualTo("path/to/helm/chart");
    assertThat(gcsStoreConfig.getRepoName()).isEqualTo("09f7eae9-8501-3fa9-92e2-9c6931713c00");
    assertThat(gcsStoreConfig.getRepoDisplayName()).isEqualTo("helm-gcs-repo-display");
    List<HelmFetchFileConfig> helmFetchFileConfigs = helmValuesFetchRequest.getHelmFetchFileConfigList();
    assertThat(helmFetchFileConfigs.size()).isEqualTo(2);
    assertThat(helmFetchFileConfigs.get(1).getIdentifier()).isEqualTo(helmChartManifestOutcome.getIdentifier());
    assertThat(helmFetchFileConfigs.get(1).getManifestType()).isEqualTo("HelmChart");
    assertThat(helmFetchFileConfigs.get(1).getFilePaths())
        .isEqualTo(helmChartManifestOutcome.getValuesPaths().getValue());
    assertThat(helmFetchFileConfigs.get(0).getIdentifier()).isEqualTo(helmChartManifestOutcome.getIdentifier());
    assertThat(helmFetchFileConfigs.get(0).getManifestType()).isEqualTo("HelmChart");
    assertThat(helmFetchFileConfigs.get(0).getFilePaths()).isEqualTo(asList("values.yaml"));
  }

  @Test
  @Owner(developers = PRATYUSH)
  @Category(UnitTests.class)
  public void testShouldPrepareHelmGcsValuesFetchTaskWithValuesOverride() {
    K8sDirectInfrastructureOutcome k8sDirectInfrastructureOutcome =
        K8sDirectInfrastructureOutcome.builder().namespace("default").infrastructureKey(INFRA_KEY).build();
    GcsStoreConfig gcsStore = GcsStoreConfig.builder()
                                  .bucketName(ParameterField.createValueField("bucket"))
                                  .folderPath(ParameterField.createValueField("path/to/helm/chart"))
                                  .connectorRef(ParameterField.createValueField("gcs-connector"))
                                  .build();

    HelmChartManifestOutcome helmChartManifestOutcome =
        HelmChartManifestOutcome.builder()
            .identifier("helm")
            .store(gcsStore)
            .chartName(ParameterField.createValueField("chart"))
            .valuesPaths(ParameterField.createValueField(asList("valuesOverride.yaml")))
            .build();
    List<String> overridePaths = new ArrayList<>(asList("folderPath/values2.yaml", "folderPath/values3.yaml"));
    GitStore gitStore2 = GitStore.builder()
                             .branch(ParameterField.createValueField("master"))
                             .paths(ParameterField.createValueField(overridePaths))
                             .connectorRef(ParameterField.createValueField("git-connector"))
                             .build();
    ValuesManifestOutcome valuesManifestOutcome1 =
        ValuesManifestOutcome.builder().identifier("helmOverride").store(gitStore2).build();
    InheritFromManifestStoreConfig inheritFromManifestStore =
        InheritFromManifestStoreConfig.builder().paths(ParameterField.createValueField(asList("values4.yaml"))).build();
    ValuesManifestOutcome valuesManifestOutcome2 =
        ValuesManifestOutcome.builder().identifier("helmOverride2").store(inheritFromManifestStore).build();
    List<String> files = asList("org:/path/to/helm/chart");
    HarnessStore harnessStore = HarnessStore.builder().files(ParameterField.createValueField(files)).build();
    ValuesManifestOutcome valuesManifestOutcome3 =
        ValuesManifestOutcome.builder().identifier("helmOverride3").store(harnessStore).build();
    Map<String, ManifestOutcome> manifestOutcomeMap = ImmutableMap.of("helm", helmChartManifestOutcome, "helmOverride",
        valuesManifestOutcome1, "helmOverride2", valuesManifestOutcome2, "helmOverride3", valuesManifestOutcome3);
    RefObject manifests = RefObject.newBuilder()
                              .setName(OutcomeExpressionConstants.MANIFESTS)
                              .setKey(OutcomeExpressionConstants.MANIFESTS)
                              .setRefType(RefType.newBuilder().setType(OrchestrationRefType.OUTCOME).build())
                              .build();

    RefObject infra = RefObject.newBuilder()
                          .setName(OutcomeExpressionConstants.INFRASTRUCTURE_OUTCOME)
                          .setKey(OutcomeExpressionConstants.INFRASTRUCTURE_OUTCOME)
                          .setRefType(RefType.newBuilder().setType(OrchestrationRefType.OUTCOME).build())
                          .build();

    StepElementParameters stepElementParameters =
        StepElementParameters.builder().spec(HelmDeployStepParams.infoBuilder().build()).build();
    OptionalOutcome manifestsOutcome =
        OptionalOutcome.builder().found(true).outcome(new ManifestsOutcome(manifestOutcomeMap)).build();
    doReturn(manifestsOutcome).when(outcomeService).resolveOptional(eq(ambiance), eq(manifests));
    doReturn(k8sDirectInfrastructureOutcome).when(outcomeService).resolve(eq(ambiance), eq(infra));

    doReturn(Optional.of(
                 ConnectorResponseDTO.builder()
                     .connector(ConnectorInfoDTO.builder()
                                    .connectorConfig(
                                        GcpConnectorDTO.builder()
                                            .credential(GcpConnectorCredentialDTO.builder()
                                                            .gcpCredentialType(GcpCredentialType.INHERIT_FROM_DELEGATE)
                                                            .build())
                                            .build())
                                    .name("helm-gcs-repo-display")
                                    .identifier("helm-gcs-repo")
                                    .connectorType(GCP)
                                    .build())
                     .build()))
        .when(connectorService)
        .get(any(), any(), any(), any());
    doReturn(k8sDirectInfrastructureOutcome).when(cdStepHelper).getInfrastructureOutcome(eq(ambiance));
    doReturn(Optional.of(getFileStoreNode("path/to/helm/chart/values5.yaml", "values5.yaml")))
        .when(fileStoreService)
        .getWithChildrenByPath(any(), any(), any(), any(), eq(true));
    TaskChainResponse taskChainResponse =
        nativeHelmStepHelper.startChainLink(nativeHelmStepExecutor, ambiance, stepElementParameters);
    assertThat(taskChainResponse).isNotNull();
    assertThat(taskChainResponse.getPassThroughData()).isNotNull();
    assertThat(taskChainResponse.getPassThroughData()).isInstanceOf(K8sStepPassThroughData.class);
    K8sStepPassThroughData passThroughData = (K8sStepPassThroughData) taskChainResponse.getPassThroughData();
    assertThat(passThroughData.getValuesManifestOutcomes()).isNotEmpty();
    assertThat(passThroughData.getValuesManifestOutcomes().size()).isEqualTo(3);
    List<ValuesManifestOutcome> valuesManifestOutcome = passThroughData.getValuesManifestOutcomes();
    assertThat(valuesManifestOutcome.get(0).getIdentifier()).isEqualTo(valuesManifestOutcome1.getIdentifier());
    assertThat(valuesManifestOutcome.get(0).getStore()).isEqualTo(valuesManifestOutcome1.getStore());
    assertThat(valuesManifestOutcome.get(2).getIdentifier()).isEqualTo(valuesManifestOutcome2.getIdentifier());
    assertThat(valuesManifestOutcome.get(2).getStore()).isEqualTo(valuesManifestOutcome2.getStore());
    assertThat(valuesManifestOutcome.get(1).getIdentifier()).isEqualTo(valuesManifestOutcome3.getIdentifier());
    assertThat(valuesManifestOutcome.get(1).getStore()).isEqualTo(valuesManifestOutcome3.getStore());
    Map<String, LocalStoreFetchFilesResult> localStoreFetchFilesResultMap =
        passThroughData.getLocalStoreFileMapContents();
    assertThat(localStoreFetchFilesResultMap.size()).isEqualTo(1);
    assertThat(localStoreFetchFilesResultMap.get("helmOverride3").getLocalStoreFileContents().size()).isEqualTo(1);
    assertThat(localStoreFetchFilesResultMap.get("helmOverride3").getLocalStoreFileContents().get(0)).isEqualTo("Test");
    List<ManifestFiles> manifestFiles = passThroughData.getManifestFiles();
    assertThat(manifestFiles.size()).isEqualTo(0);
    ArgumentCaptor<Object> taskParametersArgumentCaptor = ArgumentCaptor.forClass(Object.class);
    verify(kryoSerializer, times(3)).asDeflatedBytes(taskParametersArgumentCaptor.capture());
    TaskParameters taskParameters = (TaskParameters) taskParametersArgumentCaptor.getAllValues().get(0);
    assertThat(taskParameters).isInstanceOf(HelmValuesFetchRequest.class);
    HelmValuesFetchRequest helmValuesFetchRequest = (HelmValuesFetchRequest) taskParameters;
    assertThat(helmValuesFetchRequest.getTimeout()).isNotNull();
    assertThat(helmValuesFetchRequest.getHelmChartManifestDelegateConfig().getStoreDelegateConfig())
        .isInstanceOf(GcsHelmStoreDelegateConfig.class);
    GcsHelmStoreDelegateConfig gcsStoreConfig =
        (GcsHelmStoreDelegateConfig) helmValuesFetchRequest.getHelmChartManifestDelegateConfig()
            .getStoreDelegateConfig();
    assertThat(gcsStoreConfig.getBucketName()).isEqualTo("bucket");
    assertThat(gcsStoreConfig.getFolderPath()).isEqualTo("path/to/helm/chart");
    assertThat(gcsStoreConfig.getRepoName()).isEqualTo("09f7eae9-8501-3fa9-92e2-9c6931713c00");
    assertThat(gcsStoreConfig.getRepoDisplayName()).isEqualTo("helm-gcs-repo-display");
    List<HelmFetchFileConfig> helmFetchFileConfigs = helmValuesFetchRequest.getHelmFetchFileConfigList();
    assertThat(helmFetchFileConfigs.size()).isEqualTo(3);
    assertThat(helmFetchFileConfigs.get(1).getIdentifier()).isEqualTo(helmChartManifestOutcome.getIdentifier());
    assertThat(helmFetchFileConfigs.get(1).getManifestType()).isEqualTo("HelmChart");
    assertThat(helmFetchFileConfigs.get(1).getFilePaths())
        .isEqualTo(helmChartManifestOutcome.getValuesPaths().getValue());
    assertThat(helmFetchFileConfigs.get(0).getIdentifier()).isEqualTo(helmChartManifestOutcome.getIdentifier());
    assertThat(helmFetchFileConfigs.get(0).getManifestType()).isEqualTo("HelmChart");
    assertThat(helmFetchFileConfigs.get(0).getFilePaths()).isEqualTo(asList("values.yaml"));
    assertThat(helmFetchFileConfigs.get(2).getIdentifier()).isEqualTo(valuesManifestOutcome2.getIdentifier());
    assertThat(helmFetchFileConfigs.get(2).getManifestType()).isEqualTo(valuesManifestOutcome2.getType());
    assertThat(helmFetchFileConfigs.get(2).getFilePaths()).isEqualTo(asList("values4.yaml"));
  }

  @Test
  @Owner(developers = PRATYUSH)
  @Category(UnitTests.class)
  public void testShouldExecuteHelmTaskForHarnessStore() {
    List<String> files = asList("org:/path/to/helm/chart");
    K8sDirectInfrastructureOutcome k8sDirectInfrastructureOutcome =
        K8sDirectInfrastructureOutcome.builder().namespace("default").build();
    HarnessStore harnessStore = HarnessStore.builder().files(ParameterField.createValueField(files)).build();
    HelmChartManifestOutcome helmChartManifestOutcome =
        HelmChartManifestOutcome.builder()
            .identifier("helm")
            .store(harnessStore)
            .valuesPaths(ParameterField.createValueField(asList("org:/path/to/helm/chart/valuesOverride.yaml")))
            .build();

    RefObject manifests = RefObject.newBuilder()
                              .setName(OutcomeExpressionConstants.MANIFESTS)
                              .setKey(OutcomeExpressionConstants.MANIFESTS)
                              .setRefType(RefType.newBuilder().setType(OrchestrationRefType.OUTCOME).build())
                              .build();

    RefObject infra = RefObject.newBuilder()
                          .setName(OutcomeExpressionConstants.INFRASTRUCTURE_OUTCOME)
                          .setKey(OutcomeExpressionConstants.INFRASTRUCTURE_OUTCOME)
                          .setRefType(RefType.newBuilder().setType(OrchestrationRefType.OUTCOME).build())
                          .build();

    Map<String, ManifestOutcome> manifestOutcomeMap = ImmutableMap.of("helm", helmChartManifestOutcome);
    StepElementParameters stepElementParameters =
        StepElementParameters.builder().spec(HelmDeployStepParams.infoBuilder().build()).build();
    OptionalOutcome manifestsOutcome =
        OptionalOutcome.builder().found(true).outcome(new ManifestsOutcome(manifestOutcomeMap)).build();
    doReturn(manifestsOutcome).when(outcomeService).resolveOptional(eq(ambiance), eq(manifests));
    doReturn(k8sDirectInfrastructureOutcome).when(outcomeService).resolve(eq(ambiance), eq(infra));

    doReturn(Optional.of(getFileStoreNode("path/to/helm/chart/values.yaml", "values.yaml")))
        .doReturn(Optional.of(getFileStoreNode("path/to/helm/chart/valuesOverride.yaml", "valuesOverride.yaml")))
        .doReturn(Optional.of(getFolderStoreNode("/path/to/helm/chart", "chart")))
        .when(fileStoreService)
        .getWithChildrenByPath(any(), any(), any(), any(), eq(true));

    ManifestFiles manifestFiles = ManifestFiles.builder()
                                      .filePath("/path/to/helm/chart/chart.yaml")
                                      .fileName("chart.yaml")
                                      .fileContent("Chart File")
                                      .build();
    mockStatic(FileStoreNodeUtils.class);
    when(FileStoreNodeUtils.mapFileNodes(any(), any())).thenReturn(asList(manifestFiles));

    nativeHelmStepHelper.startChainLink(nativeHelmStepExecutor, ambiance, stepElementParameters);
    ArgumentCaptor<List> valuesFilesContentCaptor = ArgumentCaptor.forClass(List.class);
    verify(nativeHelmStepExecutor, times(1))
        .executeHelmTask(eq(helmChartManifestOutcome), eq(ambiance), eq(stepElementParameters),
            valuesFilesContentCaptor.capture(),
            eq(NativeHelmExecutionPassThroughData.builder()
                    .infrastructure(k8sDirectInfrastructureOutcome)
                    .manifestFiles(asList(manifestFiles))
                    .lastActiveUnitProgressData(null)
                    .build()),
            eq(false), eq(getUnitProgressData()));
    List<String> valuesFilesContent = valuesFilesContentCaptor.getValue();
    assertThat(valuesFilesContent).isNotEmpty();
    assertThat(valuesFilesContent.size()).isEqualTo(2);
  }

  @Test
  @Owner(developers = ACHYUTH)
  @Category(UnitTests.class)
  public void testShouldPrepareHelmCustomManifestValuesFetchTask() {
    String extractionScript = "git clone something.git";
    List<TaskSelectorYaml> delegateSelector = asList(new TaskSelectorYaml("sample-delegate"));
    CustomRemoteStoreConfig customRemoteStoreConfig =
        CustomRemoteStoreConfig.builder()
            .filePath(ParameterField.createValueField("folderPath/values.yaml"))
            .extractionScript(ParameterField.createValueField(extractionScript))
            .delegateSelectors(ParameterField.createValueField(delegateSelector))
            .build();

    K8sDirectInfrastructureOutcome k8sDirectInfrastructureOutcome =
        K8sDirectInfrastructureOutcome.builder().namespace("default").build();
    HelmChartManifestOutcome helmChartManifestOutcome = HelmChartManifestOutcome.builder()
                                                            .identifier("helm")
                                                            .store(customRemoteStoreConfig)
                                                            .valuesPaths(ParameterField.createValueField(null))
                                                            .build();

    RefObject manifests = RefObject.newBuilder()
                              .setName(OutcomeExpressionConstants.MANIFESTS)
                              .setKey(OutcomeExpressionConstants.MANIFESTS)
                              .setRefType(RefType.newBuilder().setType(OrchestrationRefType.OUTCOME).build())
                              .build();

    RefObject infra = RefObject.newBuilder()
                          .setName(OutcomeExpressionConstants.INFRASTRUCTURE_OUTCOME)
                          .setKey(OutcomeExpressionConstants.INFRASTRUCTURE_OUTCOME)
                          .setRefType(RefType.newBuilder().setType(OrchestrationRefType.OUTCOME).build())
                          .build();

    Map<String, ManifestOutcome> manifestOutcomeMap = ImmutableMap.of("helm", helmChartManifestOutcome);
    StepElementParameters stepElementParameters =
        StepElementParameters.builder()
            .spec(HelmDeployStepParams.infoBuilder()
                      .delegateSelectors(ParameterField.createValueField(delegateSelector))
                      .build())
            .build();
    OptionalOutcome manifestsOutcome =
        OptionalOutcome.builder().found(true).outcome(new ManifestsOutcome(manifestOutcomeMap)).build();
    doReturn(manifestsOutcome).when(outcomeService).resolveOptional(eq(ambiance), eq(manifests));
    doReturn(k8sDirectInfrastructureOutcome).when(outcomeService).resolve(eq(ambiance), eq(infra));

    TaskChainResponse taskChainResponse =
        nativeHelmStepHelper.startChainLink(nativeHelmStepExecutor, ambiance, stepElementParameters);
    assertThat(taskChainResponse).isNotNull();
    assertThat(taskChainResponse.getTaskRequest().getDelegateTaskRequest().getRequest().getSelectorsCount())
        .isEqualTo(2);
    assertThat(taskChainResponse.getPassThroughData()).isNotNull();
    assertThat(taskChainResponse.getPassThroughData()).isInstanceOf(K8sStepPassThroughData.class);
    K8sStepPassThroughData passThroughData = (K8sStepPassThroughData) taskChainResponse.getPassThroughData();
    assertThat(passThroughData.getValuesManifestOutcomes()).isEmpty();
    assertThat(passThroughData.getCustomFetchContent()).isNull();
    assertThat(passThroughData.getZippedManifestFileId()).isNull();
    ArgumentCaptor<Object> argumentCaptor = ArgumentCaptor.forClass(Object.class);
    verify(kryoSerializer, times(1)).asDeflatedBytes(argumentCaptor.capture());
    TaskParameters taskParameters = (TaskParameters) argumentCaptor.getAllValues().get(0);
    assertThat(taskParameters).isInstanceOf(CustomManifestValuesFetchParams.class);
    CustomManifestValuesFetchParams customManifestValuesFetchRequest = (CustomManifestValuesFetchParams) taskParameters;
    assertThat(customManifestValuesFetchRequest.getCustomManifestSource().getAccountId()).isEqualTo("test-account");
    assertThat(customManifestValuesFetchRequest.getCustomManifestSource().getScript()).isEqualTo(extractionScript);
    assertThat(customManifestValuesFetchRequest.getCustomManifestSource().getFilePaths())
        .isEqualTo(asList("folderPath/values.yaml"));
    assertThat(customManifestValuesFetchRequest.getCustomManifestSource().getZippedManifestFileId()).isNull();
    assertThat(customManifestValuesFetchRequest.getFetchFilesList().size()).isEqualTo(1);
  }

  @Test
  @Owner(developers = ACHYUTH)
  @Category(UnitTests.class)
  public void testShouldPrepareHelmCustomManifestValuesFetchTaskWithValuesOverride() {
    String extractionScript = "git clone something.git";
    List<TaskSelectorYaml> delegateSelector = asList(new TaskSelectorYaml("sample-delegate"));
    CustomRemoteStoreConfig customRemoteStoreConfig =
        CustomRemoteStoreConfig.builder()
            .filePath(ParameterField.createValueField("folderPath/values.yaml"))
            .extractionScript(ParameterField.createValueField(extractionScript))
            .delegateSelectors(ParameterField.createValueField(delegateSelector))
            .build();

    K8sDirectInfrastructureOutcome k8sDirectInfrastructureOutcome =
        K8sDirectInfrastructureOutcome.builder().namespace("default").build();

    GitStore gitStore = GitStore.builder()
                            .branch(ParameterField.createValueField("master"))
                            .folderPath(ParameterField.createValueField("path/to/helm/chart"))
                            .connectorRef(ParameterField.createValueField("git-connector"))
                            .build();
    HelmChartManifestOutcome helmChartManifestOutcome =
        HelmChartManifestOutcome.builder()
            .identifier("helm")
            .store(gitStore)
            .valuesPaths(ParameterField.createValueField(asList("path/to/helm/chart/valuesOverride.yaml")))
            .build();
    ValuesManifestOutcome valuesManifestOutcome =
        ValuesManifestOutcome.builder().identifier("helmOverride").store(customRemoteStoreConfig).build();

    RefObject manifests = RefObject.newBuilder()
                              .setName(OutcomeExpressionConstants.MANIFESTS)
                              .setKey(OutcomeExpressionConstants.MANIFESTS)
                              .setRefType(RefType.newBuilder().setType(OrchestrationRefType.OUTCOME).build())
                              .build();

    RefObject infra = RefObject.newBuilder()
                          .setName(OutcomeExpressionConstants.INFRASTRUCTURE_OUTCOME)
                          .setKey(OutcomeExpressionConstants.INFRASTRUCTURE_OUTCOME)
                          .setRefType(RefType.newBuilder().setType(OrchestrationRefType.OUTCOME).build())
                          .build();

    Map<String, ManifestOutcome> manifestOutcomeMap =
        ImmutableMap.of("helm", helmChartManifestOutcome, "helmOverride", valuesManifestOutcome);
    StepElementParameters stepElementParameters =
        StepElementParameters.builder()
            .spec(HelmDeployStepParams.infoBuilder()
                      .delegateSelectors(ParameterField.createValueField(delegateSelector))
                      .build())
            .build();
    OptionalOutcome manifestsOutcome =
        OptionalOutcome.builder().found(true).outcome(new ManifestsOutcome(manifestOutcomeMap)).build();
    doReturn(manifestsOutcome).when(outcomeService).resolveOptional(eq(ambiance), eq(manifests));
    doReturn(k8sDirectInfrastructureOutcome).when(outcomeService).resolve(eq(ambiance), eq(infra));
    doReturn(
        Optional.of(ConnectorResponseDTO.builder()
                        .connector(ConnectorInfoDTO.builder()
                                       .connectorConfig(
                                           GitConfigDTO.builder().gitAuthType(GitAuthType.HTTP).url(SOME_URL).build())
                                       .name("test")
                                       .build())
                        .build()))
        .when(connectorService)
        .get(any(), any(), any(), any());

    TaskChainResponse taskChainResponse =
        nativeHelmStepHelper.startChainLink(nativeHelmStepExecutor, ambiance, stepElementParameters);
    assertThat(taskChainResponse).isNotNull();
    assertThat(taskChainResponse.getTaskRequest().getDelegateTaskRequest().getRequest().getSelectorsCount())
        .isEqualTo(2);
    assertThat(taskChainResponse.getPassThroughData()).isNotNull();
    assertThat(taskChainResponse.getPassThroughData()).isInstanceOf(K8sStepPassThroughData.class);
    K8sStepPassThroughData passThroughData = (K8sStepPassThroughData) taskChainResponse.getPassThroughData();
    assertThat(passThroughData.getValuesManifestOutcomes().size()).isEqualTo(1);
    assertThat(passThroughData.getValuesManifestOutcomes()).isEqualTo(asList(valuesManifestOutcome));
    assertThat(passThroughData.getCustomFetchContent()).isNull();
    assertThat(passThroughData.getZippedManifestFileId()).isNull();
    ArgumentCaptor<Object> argumentCaptor = ArgumentCaptor.forClass(Object.class);
    verify(kryoSerializer, times(1)).asDeflatedBytes(argumentCaptor.capture());
    TaskParameters taskParameters = (TaskParameters) argumentCaptor.getAllValues().get(0);
    assertThat(taskParameters).isInstanceOf(CustomManifestValuesFetchParams.class);
    CustomManifestValuesFetchParams customManifestValuesFetchRequest = (CustomManifestValuesFetchParams) taskParameters;
    assertThat(customManifestValuesFetchRequest.getFetchFilesList().size()).isEqualTo(1);
    assertThat(customManifestValuesFetchRequest.getFetchFilesList().get(0).getCustomManifestSource().getFilePaths())
        .isEqualTo(asList("folderPath/values.yaml"));
    assertThat(customManifestValuesFetchRequest.getFetchFilesList().get(0).getCustomManifestSource().getScript())
        .isEqualTo(extractionScript);
    assertThat(customManifestValuesFetchRequest.getFetchFilesList().get(0).getCustomManifestSource().getAccountId())
        .isEqualTo("test-account");
  }

  @Test
  @Owner(developers = ACASIAN)
  @Category(UnitTests.class)
  public void testShouldPrepareHelmHttpValuesFetchTask() {
    K8sDirectInfrastructureOutcome k8sDirectInfrastructureOutcome =
        K8sDirectInfrastructureOutcome.builder().namespace("default").infrastructureKey(INFRA_KEY).build();
    HttpStoreConfig httpStore =
        HttpStoreConfig.builder().connectorRef(ParameterField.createValueField("http-connector")).build();

    HelmChartManifestOutcome helmChartManifestOutcome =
        HelmChartManifestOutcome.builder()
            .identifier("helm")
            .store(httpStore)
            .chartName(ParameterField.createValueField("chart"))
            .valuesPaths(ParameterField.createValueField(asList("valuesOverride.yaml")))
            .build();
    Map<String, ManifestOutcome> manifestOutcomeMap = ImmutableMap.of("helm", helmChartManifestOutcome);
    RefObject manifests = RefObject.newBuilder()
                              .setName(OutcomeExpressionConstants.MANIFESTS)
                              .setKey(OutcomeExpressionConstants.MANIFESTS)
                              .setRefType(RefType.newBuilder().setType(OrchestrationRefType.OUTCOME).build())
                              .build();

    RefObject infra = RefObject.newBuilder()
                          .setName(OutcomeExpressionConstants.INFRASTRUCTURE_OUTCOME)
                          .setKey(OutcomeExpressionConstants.INFRASTRUCTURE_OUTCOME)
                          .setRefType(RefType.newBuilder().setType(OrchestrationRefType.OUTCOME).build())
                          .build();

    StepElementParameters stepElementParameters =
        StepElementParameters.builder().spec(HelmDeployStepParams.infoBuilder().build()).build();
    OptionalOutcome manifestsOutcome =
        OptionalOutcome.builder().found(true).outcome(new ManifestsOutcome(manifestOutcomeMap)).build();
    doReturn(manifestsOutcome).when(outcomeService).resolveOptional(eq(ambiance), eq(manifests));
    doReturn(k8sDirectInfrastructureOutcome).when(outcomeService).resolve(eq(ambiance), eq(infra));
    doReturn(k8sDirectInfrastructureOutcome).when(cdStepHelper).getInfrastructureOutcome(eq(ambiance));
    doReturn(
        Optional.of(
            ConnectorResponseDTO.builder()
                .connector(
                    ConnectorInfoDTO.builder()
                        .connectorConfig(
                            HttpHelmConnectorDTO.builder()
                                .auth(HttpHelmAuthenticationDTO.builder().authType(HttpHelmAuthType.ANONYMOUS).build())
                                .build())
                        .name("helm-http-repo-display")
                        .identifier("helm-http-repo")
                        .connectorType(HTTP_HELM_REPO)
                        .build())
                .build()))
        .when(connectorService)
        .get(any(), any(), any(), any());

    TaskChainResponse taskChainResponse =
        nativeHelmStepHelper.startChainLink(nativeHelmStepExecutor, ambiance, stepElementParameters);
    assertThat(taskChainResponse).isNotNull();
    assertThat(taskChainResponse.getPassThroughData()).isNotNull();
    assertThat(taskChainResponse.getPassThroughData()).isInstanceOf(K8sStepPassThroughData.class);
    ArgumentCaptor<Object> taskParametersArgumentCaptor = ArgumentCaptor.forClass(Object.class);
    verify(kryoSerializer, times(2)).asDeflatedBytes(taskParametersArgumentCaptor.capture());
    TaskParameters taskParameters = (TaskParameters) taskParametersArgumentCaptor.getAllValues().get(0);
    assertThat(taskParameters).isInstanceOf(HelmValuesFetchRequest.class);
    HelmValuesFetchRequest helmValuesFetchRequest = (HelmValuesFetchRequest) taskParameters;
    assertThat(helmValuesFetchRequest.getTimeout()).isNotNull();
    assertThat(helmValuesFetchRequest.getHelmChartManifestDelegateConfig().getStoreDelegateConfig())
        .isInstanceOf(HttpHelmStoreDelegateConfig.class);
    HttpHelmStoreDelegateConfig httpStoreConfig =
        (HttpHelmStoreDelegateConfig) helmValuesFetchRequest.getHelmChartManifestDelegateConfig()
            .getStoreDelegateConfig();
    assertThat(httpStoreConfig.getRepoName()).isEqualTo("0755aa99-0254-3266-895a-2697d0d27b68");
    assertThat(httpStoreConfig.getRepoDisplayName()).isEqualTo("helm-http-repo-display");
    List<HelmFetchFileConfig> helmFetchFileConfigs = helmValuesFetchRequest.getHelmFetchFileConfigList();
    assertThat(helmFetchFileConfigs.size()).isEqualTo(2);
    assertThat(helmFetchFileConfigs.get(1).getIdentifier()).isEqualTo(helmChartManifestOutcome.getIdentifier());
    assertThat(helmFetchFileConfigs.get(1).getManifestType()).isEqualTo("HelmChart");
    assertThat(helmFetchFileConfigs.get(1).getFilePaths())
        .isEqualTo(helmChartManifestOutcome.getValuesPaths().getValue());
    assertThat(helmFetchFileConfigs.get(0).getIdentifier()).isEqualTo(helmChartManifestOutcome.getIdentifier());
    assertThat(helmFetchFileConfigs.get(0).getManifestType()).isEqualTo("HelmChart");
    assertThat(helmFetchFileConfigs.get(0).getFilePaths()).isEqualTo(asList("values.yaml"));
  }

  @Test
  @Owner(developers = PRATYUSH)
  @Category(UnitTests.class)
  public void testShouldPrepareOCIHelmValuesFetchTask() {
    K8sDirectInfrastructureOutcome k8sDirectInfrastructureOutcome =
        K8sDirectInfrastructureOutcome.builder().namespace("default").infrastructureKey(INFRA_KEY).build();
    OciHelmChartConfig httpStore =
        OciHelmChartConfig.builder()
            .config(ParameterField.createValueField(
                OciHelmChartStoreConfigWrapper.builder()
                    .spec(OciHelmChartStoreGenericConfig.builder()
                              .connectorRef(ParameterField.createValueField("oci-helm-connector"))
                              .build())
                    .build()))
            .build();

    HelmChartManifestOutcome helmChartManifestOutcome =
        HelmChartManifestOutcome.builder()
            .identifier("helm")
            .store(httpStore)
            .chartName(ParameterField.createValueField("chart"))
            .valuesPaths(ParameterField.createValueField(asList("valuesOverride.yaml")))
            .build();
    Map<String, ManifestOutcome> manifestOutcomeMap = ImmutableMap.of("helm", helmChartManifestOutcome);
    RefObject manifests = RefObject.newBuilder()
                              .setName(OutcomeExpressionConstants.MANIFESTS)
                              .setKey(OutcomeExpressionConstants.MANIFESTS)
                              .setRefType(RefType.newBuilder().setType(OrchestrationRefType.OUTCOME).build())
                              .build();

    RefObject infra = RefObject.newBuilder()
                          .setName(OutcomeExpressionConstants.INFRASTRUCTURE_OUTCOME)
                          .setKey(OutcomeExpressionConstants.INFRASTRUCTURE_OUTCOME)
                          .setRefType(RefType.newBuilder().setType(OrchestrationRefType.OUTCOME).build())
                          .build();

    StepElementParameters stepElementParameters =
        StepElementParameters.builder().spec(HelmDeployStepParams.infoBuilder().build()).build();
    OptionalOutcome manifestsOutcome =
        OptionalOutcome.builder().found(true).outcome(new ManifestsOutcome(manifestOutcomeMap)).build();
    doReturn(manifestsOutcome).when(outcomeService).resolveOptional(eq(ambiance), eq(manifests));
    doReturn(k8sDirectInfrastructureOutcome).when(outcomeService).resolve(eq(ambiance), eq(infra));
    doReturn(k8sDirectInfrastructureOutcome).when(cdStepHelper).getInfrastructureOutcome(eq(ambiance));
    doReturn(
        Optional.of(
            ConnectorResponseDTO.builder()
                .connector(
                    ConnectorInfoDTO.builder()
                        .connectorConfig(
                            OciHelmConnectorDTO.builder()
                                .auth(OciHelmAuthenticationDTO.builder().authType(OciHelmAuthType.ANONYMOUS).build())
                                .build())
                        .name("OCI-HELM-REPO-display")
                        .identifier("OCI-HELM-REPO")
                        .connectorType(OCI_HELM_REPO)
                        .build())
                .build()))
        .when(connectorService)
        .get(any(), any(), any(), any());

    TaskChainResponse taskChainResponse =
        nativeHelmStepHelper.startChainLink(nativeHelmStepExecutor, ambiance, stepElementParameters);
    assertThat(taskChainResponse).isNotNull();
    assertThat(taskChainResponse.getPassThroughData()).isNotNull();
    assertThat(taskChainResponse.getPassThroughData()).isInstanceOf(K8sStepPassThroughData.class);
    ArgumentCaptor<Object> taskParametersArgumentCaptor = ArgumentCaptor.forClass(Object.class);
    verify(kryoSerializer, times(2)).asDeflatedBytes(taskParametersArgumentCaptor.capture());
    TaskParameters taskParameters = (TaskParameters) taskParametersArgumentCaptor.getAllValues().get(0);
    assertThat(taskParameters).isInstanceOf(HelmValuesFetchRequest.class);
    HelmValuesFetchRequest helmValuesFetchRequest = (HelmValuesFetchRequest) taskParameters;
    assertThat(helmValuesFetchRequest.getTimeout()).isNotNull();
    assertThat(helmValuesFetchRequest.getHelmChartManifestDelegateConfig().getStoreDelegateConfig())
        .isInstanceOf(OciHelmStoreDelegateConfig.class);
    OciHelmStoreDelegateConfig ociHelmStoreConfig =
        (OciHelmStoreDelegateConfig) helmValuesFetchRequest.getHelmChartManifestDelegateConfig()
            .getStoreDelegateConfig();
    assertThat(ociHelmStoreConfig.getRepoName()).isEqualTo("dd43c344-96a8-3b93-8136-baa4d0b4cbe6");
    assertThat(ociHelmStoreConfig.getRepoDisplayName()).isEqualTo("OCI-HELM-REPO-display");
    List<HelmFetchFileConfig> helmFetchFileConfigs = helmValuesFetchRequest.getHelmFetchFileConfigList();
    assertThat(helmFetchFileConfigs.size()).isEqualTo(2);
    assertThat(helmFetchFileConfigs.get(1).getIdentifier()).isEqualTo(helmChartManifestOutcome.getIdentifier());
    assertThat(helmFetchFileConfigs.get(1).getManifestType()).isEqualTo("HelmChart");
    assertThat(helmFetchFileConfigs.get(1).getFilePaths())
        .isEqualTo(helmChartManifestOutcome.getValuesPaths().getValue());
    assertThat(helmFetchFileConfigs.get(0).getIdentifier()).isEqualTo(helmChartManifestOutcome.getIdentifier());
    assertThat(helmFetchFileConfigs.get(0).getManifestType()).isEqualTo("HelmChart");
    assertThat(helmFetchFileConfigs.get(0).getFilePaths()).isEqualTo(asList("values.yaml"));
  }

  @Test
  @Owner(developers = PRATYUSH)
  @Category(UnitTests.class)
  public void testShouldPrepareHelmHttpValuesFetchTaskWithValuesOverride() {
    K8sDirectInfrastructureOutcome k8sDirectInfrastructureOutcome =
        K8sDirectInfrastructureOutcome.builder().namespace("default").infrastructureKey(INFRA_KEY).build();
    HttpStoreConfig httpStore =
        HttpStoreConfig.builder().connectorRef(ParameterField.createValueField("http-connector")).build();

    HelmChartManifestOutcome helmChartManifestOutcome =
        HelmChartManifestOutcome.builder()
            .identifier("helm")
            .store(httpStore)
            .chartName(ParameterField.createValueField("chart"))
            .valuesPaths(ParameterField.createValueField(asList("valuesOverride.yaml")))
            .build();
    List<String> overridePaths = new ArrayList<>(asList("folderPath/values2.yaml", "folderPath/values3.yaml"));
    GitStore gitStore2 = GitStore.builder()
                             .branch(ParameterField.createValueField("master"))
                             .paths(ParameterField.createValueField(overridePaths))
                             .connectorRef(ParameterField.createValueField("git-connector"))
                             .build();
    ValuesManifestOutcome valuesManifestOutcome1 =
        ValuesManifestOutcome.builder().identifier("helmOverride").store(gitStore2).build();
    InheritFromManifestStoreConfig inheritFromManifestStore =
        InheritFromManifestStoreConfig.builder().paths(ParameterField.createValueField(asList("values4.yaml"))).build();
    ValuesManifestOutcome valuesManifestOutcome2 =
        ValuesManifestOutcome.builder().identifier("helmOverride2").store(inheritFromManifestStore).build();
    List<String> files = asList("org:/path/to/helm/chart");
    HarnessStore harnessStore = HarnessStore.builder().files(ParameterField.createValueField(files)).build();
    ValuesManifestOutcome valuesManifestOutcome3 =
        ValuesManifestOutcome.builder().identifier("helmOverride3").store(harnessStore).build();
    Map<String, ManifestOutcome> manifestOutcomeMap = ImmutableMap.of("helm", helmChartManifestOutcome, "helmOverride",
        valuesManifestOutcome1, "helmOverride2", valuesManifestOutcome2, "helmOverride3", valuesManifestOutcome3);
    RefObject manifests = RefObject.newBuilder()
                              .setName(OutcomeExpressionConstants.MANIFESTS)
                              .setKey(OutcomeExpressionConstants.MANIFESTS)
                              .setRefType(RefType.newBuilder().setType(OrchestrationRefType.OUTCOME).build())
                              .build();

    RefObject infra = RefObject.newBuilder()
                          .setName(OutcomeExpressionConstants.INFRASTRUCTURE_OUTCOME)
                          .setKey(OutcomeExpressionConstants.INFRASTRUCTURE_OUTCOME)
                          .setRefType(RefType.newBuilder().setType(OrchestrationRefType.OUTCOME).build())
                          .build();

    StepElementParameters stepElementParameters =
        StepElementParameters.builder().spec(HelmDeployStepParams.infoBuilder().build()).build();
    OptionalOutcome manifestsOutcome =
        OptionalOutcome.builder().found(true).outcome(new ManifestsOutcome(manifestOutcomeMap)).build();
    doReturn(manifestsOutcome).when(outcomeService).resolveOptional(eq(ambiance), eq(manifests));
    doReturn(k8sDirectInfrastructureOutcome).when(outcomeService).resolve(eq(ambiance), eq(infra));
    doReturn(k8sDirectInfrastructureOutcome).when(cdStepHelper).getInfrastructureOutcome(eq(ambiance));
    doReturn(
        Optional.of(
            ConnectorResponseDTO.builder()
                .connector(
                    ConnectorInfoDTO.builder()
                        .connectorConfig(
                            HttpHelmConnectorDTO.builder()
                                .auth(HttpHelmAuthenticationDTO.builder().authType(HttpHelmAuthType.ANONYMOUS).build())
                                .build())
                        .name("helm-http-repo-display")
                        .identifier("helm-http-repo")
                        .connectorType(HTTP_HELM_REPO)
                        .build())
                .build()))
        .when(connectorService)
        .get(any(), any(), any(), any());

    doReturn(Optional.of(getFileStoreNode("path/to/helm/chart/values5.yaml", "values5.yaml")))
        .when(fileStoreService)
        .getWithChildrenByPath(any(), any(), any(), any(), eq(true));
    TaskChainResponse taskChainResponse =
        nativeHelmStepHelper.startChainLink(nativeHelmStepExecutor, ambiance, stepElementParameters);
    assertThat(taskChainResponse).isNotNull();
    assertThat(taskChainResponse.getPassThroughData()).isNotNull();
    assertThat(taskChainResponse.getPassThroughData()).isInstanceOf(K8sStepPassThroughData.class);
    K8sStepPassThroughData passThroughData = (K8sStepPassThroughData) taskChainResponse.getPassThroughData();
    assertThat(passThroughData.getValuesManifestOutcomes().size()).isEqualTo(3);
    List<ValuesManifestOutcome> valuesManifestOutcome = passThroughData.getValuesManifestOutcomes();
    assertThat(valuesManifestOutcome.get(0).getIdentifier()).isEqualTo(valuesManifestOutcome1.getIdentifier());
    assertThat(valuesManifestOutcome.get(0).getStore()).isEqualTo(valuesManifestOutcome1.getStore());
    assertThat(valuesManifestOutcome.get(2).getIdentifier()).isEqualTo(valuesManifestOutcome2.getIdentifier());
    assertThat(valuesManifestOutcome.get(2).getStore()).isEqualTo(valuesManifestOutcome2.getStore());
    assertThat(valuesManifestOutcome.get(1).getIdentifier()).isEqualTo(valuesManifestOutcome3.getIdentifier());
    assertThat(valuesManifestOutcome.get(1).getStore()).isEqualTo(valuesManifestOutcome3.getStore());
    Map<String, LocalStoreFetchFilesResult> localStoreFetchFilesResultMap =
        passThroughData.getLocalStoreFileMapContents();
    assertThat(localStoreFetchFilesResultMap.size()).isEqualTo(1);
    assertThat(localStoreFetchFilesResultMap.get("helmOverride3").getLocalStoreFileContents().size()).isEqualTo(1);
    assertThat(localStoreFetchFilesResultMap.get("helmOverride3").getLocalStoreFileContents().get(0)).isEqualTo("Test");
    List<ManifestFiles> manifestFiles = passThroughData.getManifestFiles();
    assertThat(manifestFiles.size()).isEqualTo(0);
    ArgumentCaptor<Object> taskParametersArgumentCaptor = ArgumentCaptor.forClass(Object.class);
    verify(kryoSerializer, times(2)).asDeflatedBytes(taskParametersArgumentCaptor.capture());
    TaskParameters taskParameters = (TaskParameters) taskParametersArgumentCaptor.getAllValues().get(0);
    assertThat(taskParameters).isInstanceOf(HelmValuesFetchRequest.class);
    HelmValuesFetchRequest helmValuesFetchRequest = (HelmValuesFetchRequest) taskParameters;
    assertThat(helmValuesFetchRequest.getTimeout()).isNotNull();
    assertThat(helmValuesFetchRequest.getHelmChartManifestDelegateConfig().getStoreDelegateConfig())
        .isInstanceOf(HttpHelmStoreDelegateConfig.class);
    HttpHelmStoreDelegateConfig httpStoreConfig =
        (HttpHelmStoreDelegateConfig) helmValuesFetchRequest.getHelmChartManifestDelegateConfig()
            .getStoreDelegateConfig();
    assertThat(httpStoreConfig.getRepoName()).isEqualTo("0755aa99-0254-3266-895a-2697d0d27b68");
    assertThat(httpStoreConfig.getRepoDisplayName()).isEqualTo("helm-http-repo-display");
    List<HelmFetchFileConfig> helmFetchFileConfigs = helmValuesFetchRequest.getHelmFetchFileConfigList();
    assertThat(helmFetchFileConfigs.size()).isEqualTo(3);
    assertThat(helmFetchFileConfigs.get(1).getIdentifier()).isEqualTo(helmChartManifestOutcome.getIdentifier());
    assertThat(helmFetchFileConfigs.get(1).getManifestType()).isEqualTo("HelmChart");
    assertThat(helmFetchFileConfigs.get(1).getFilePaths())
        .isEqualTo(helmChartManifestOutcome.getValuesPaths().getValue());
    assertThat(helmFetchFileConfigs.get(0).getIdentifier()).isEqualTo(helmChartManifestOutcome.getIdentifier());
    assertThat(helmFetchFileConfigs.get(0).getManifestType()).isEqualTo("HelmChart");
    assertThat(helmFetchFileConfigs.get(0).getFilePaths()).isEqualTo(asList("values.yaml"));
    assertThat(helmFetchFileConfigs.get(2).getIdentifier()).isEqualTo(valuesManifestOutcome2.getIdentifier());
    assertThat(helmFetchFileConfigs.get(2).getManifestType()).isEqualTo(valuesManifestOutcome2.getType());
    assertThat(helmFetchFileConfigs.get(2).getFilePaths()).isEqualTo(asList("values4.yaml"));
  }

  @Test
  @Owner(developers = ACASIAN)
  @Category(UnitTests.class)
  public void shouldHandleCustomManifestValuesFetchResponse() throws Exception {
    StepElementParameters stepElementParams =
        StepElementParameters.builder().spec(HelmDeployStepParams.infoBuilder().build()).build();

    StoreConfig store = CustomRemoteStoreConfig.builder().build();
    HelmChartManifestOutcome helmChartManifestOutcome =
        HelmChartManifestOutcome.builder().identifier("id").store(store).build();
    K8sDirectInfrastructureOutcome k8sDirectInfrastructureOutcome = K8sDirectInfrastructureOutcome.builder().build();
    K8sStepPassThroughData passThroughData = K8sStepPassThroughData.builder()
                                                 .manifestOutcome(helmChartManifestOutcome)
                                                 .manifestFiles(new ArrayList<>())
                                                 .infrastructure(k8sDirectInfrastructureOutcome)
                                                 .shouldOpenFetchFilesStream(true)
                                                 .shouldCloseFetchFilesStream(true)
                                                 .build();
    Map<String, Collection<CustomSourceFile>> valuesFilesContentMap = new HashMap<>();
    valuesFilesContentMap.put("id",
        asList(CustomSourceFile.builder().fileContent("values yaml payload").filePath("path/to/values.yaml").build()));
    UnitProgressData unitProgressData = UnitProgressData.builder().build();
    CustomManifestValuesFetchResponse customManifestValuesFetchResponse =
        CustomManifestValuesFetchResponse.builder()
            .valuesFilesContentMap(valuesFilesContentMap)
            .zippedManifestFileId("zip")
            .commandExecutionStatus(SUCCESS)
            .unitProgressData(unitProgressData)
            .build();
    Map<String, ResponseData> responseDataMap =
        ImmutableMap.of("custom-manifest-values-fetch-response", customManifestValuesFetchResponse);
    ThrowingSupplier responseDataSuplier = StrategyHelper.buildResponseDataSupplier(responseDataMap);

    nativeHelmStepHelper.executeNextLink(
        nativeHelmStepExecutor, ambiance, stepElementParams, passThroughData, responseDataSuplier);

    K8sStepPassThroughData updatedK8sStepPassThroughData =
        passThroughData.toBuilder()
            .customFetchContent(customManifestValuesFetchResponse.getValuesFilesContentMap())
            .zippedManifestFileId(customManifestValuesFetchResponse.getZippedManifestFileId())
            .build();
    updatedK8sStepPassThroughData.setShouldOpenFetchFilesStream(false);
    ArgumentCaptor<List> valuesFilesContentCaptor = ArgumentCaptor.forClass(List.class);
    verify(nativeHelmStepExecutor, times(1))
        .executeHelmTask(eq(helmChartManifestOutcome), eq(ambiance), eq(stepElementParams),
            valuesFilesContentCaptor.capture(),
            eq(NativeHelmExecutionPassThroughData.builder()
                    .infrastructure(k8sDirectInfrastructureOutcome)
                    .manifestFiles(emptyList())
                    .zippedManifestId(updatedK8sStepPassThroughData.getZippedManifestFileId())
                    .lastActiveUnitProgressData(null)
                    .build()),
            eq(updatedK8sStepPassThroughData.getShouldOpenFetchFilesStream()), eq(unitProgressData));
    List<String> valuesFilesContent = valuesFilesContentCaptor.getValue();
    assertThat(valuesFilesContent).isNotEmpty();
    assertThat(valuesFilesContent.size()).isEqualTo(1);
  }

  @Test
  @Owner(developers = PRATYUSH)
  @Category(UnitTests.class)
  public void shouldHandleCustomManifestValuesFetchResponseWithGitTask() throws Exception {
    StepElementParameters stepElementParams =
        StepElementParameters.builder().spec(K8sRollingStepParameters.infoBuilder().build()).build();

    StoreConfig store = GitStore.builder()
                            .branch(ParameterField.createValueField("master"))
                            .paths(ParameterField.createValueField(asList("path/to/manifest/templates")))
                            .connectorRef(ParameterField.createValueField("git-connector"))
                            .build();
    ValuesManifestOutcome valuesManifestOutcome =
        ValuesManifestOutcome.builder().identifier("k8s").store(CustomRemoteStoreConfig.builder().build()).build();
    K8sStepPassThroughData passThroughData = K8sStepPassThroughData.builder()
                                                 .manifestOutcome(K8sManifestOutcome.builder().store(store).build())
                                                 .manifestOutcomeList(asList(valuesManifestOutcome))
                                                 .infrastructure(K8sDirectInfrastructureOutcome.builder().build())
                                                 .shouldOpenFetchFilesStream(true)
                                                 .shouldCloseFetchFilesStream(true)
                                                 .build();
    Map<String, Collection<CustomSourceFile>> valuesFilesContentMap = new HashMap<>();
    valuesFilesContentMap.put("id",
        asList(CustomSourceFile.builder().fileContent("values yaml payload").filePath("path/to/values.yaml").build()));
    UnitProgressData unitProgressData = UnitProgressData.builder().build();
    CustomManifestValuesFetchResponse customManifestValuesFetchResponse =
        CustomManifestValuesFetchResponse.builder()
            .valuesFilesContentMap(valuesFilesContentMap)
            .zippedManifestFileId("zip")
            .commandExecutionStatus(SUCCESS)
            .unitProgressData(unitProgressData)
            .build();

    Map<String, ResponseData> responseDataMap =
        ImmutableMap.of("custom-manifest-values-fetch-response", customManifestValuesFetchResponse);

    doReturn(
        Optional.of(ConnectorResponseDTO.builder()
                        .connector(ConnectorInfoDTO.builder()
                                       .connectorConfig(
                                           GitConfigDTO.builder().gitAuthType(GitAuthType.HTTP).url(SOME_URL).build())
                                       .name("test")
                                       .build())
                        .build()))
        .when(connectorService)
        .get(any(), any(), any(), any());
    ThrowingSupplier responseDataSuplier = StrategyHelper.buildResponseDataSupplier(responseDataMap);

    TaskChainResponse taskChainResponse = nativeHelmStepHelper.executeNextLink(
        nativeHelmStepExecutor, ambiance, stepElementParams, passThroughData, responseDataSuplier);

    K8sStepPassThroughData updatedK8sStepPassThroughData =
        passThroughData.toBuilder()
            .manifestOutcomeList(asList(ValuesManifestOutcome.builder().store(store).build(), valuesManifestOutcome))
            .customFetchContent(customManifestValuesFetchResponse.getValuesFilesContentMap())
            .zippedManifestFileId(customManifestValuesFetchResponse.getZippedManifestFileId())
            .shouldOpenFetchFilesStream(false)
            .build();

    assertThat(taskChainResponse).isNotNull();
    assertThat(taskChainResponse.getPassThroughData()).isNotNull();
    assertThat(taskChainResponse.getPassThroughData()).isInstanceOf(K8sStepPassThroughData.class);
    assertThat(taskChainResponse.getPassThroughData()).isEqualTo(updatedK8sStepPassThroughData);
    assertThat(taskChainResponse.getTaskRequest().getDelegateTaskRequest().getTaskName())
        .isEqualTo(TaskType.GIT_FETCH_NEXT_GEN_TASK.getDisplayName());
  }

  @Test
  @Owner(developers = PRATYUSH)
  @Category(UnitTests.class)
  public void shouldHandleCustomManifestValuesFetchResponseForHelmChart() throws Exception {
    StepElementParameters stepElementParams =
        StepElementParameters.builder().spec(K8sRollingStepParameters.infoBuilder().build()).build();

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

    doReturn(Optional.of(ConnectorResponseDTO.builder()
                             .connector(ConnectorInfoDTO.builder()
                                            .identifier("http-helm-connector")
                                            .connectorType(HTTP_HELM_REPO)
                                            .connectorConfig(httpHelmConnectorConfig)
                                            .build())
                             .build()))
        .when(connectorService)
        .get(nullable(String.class), nullable(String.class), nullable(String.class), nullable(String.class));
    K8sDirectInfrastructureOutcome k8sDirectInfrastructureOutcome =
        K8sDirectInfrastructureOutcome.builder().namespace("default").infrastructureKey(INFRA_KEY).build();
    doReturn(k8sDirectInfrastructureOutcome).when(cdStepHelper).getInfrastructureOutcome(eq(ambiance));
    ValuesManifestOutcome valuesManifestOutcome =
        ValuesManifestOutcome.builder().identifier("helm").store(CustomRemoteStoreConfig.builder().build()).build();
    K8sStepPassThroughData passThroughData =
        K8sStepPassThroughData.builder()
            .manifestOutcome(manifestOutcome)
            .manifestOutcomeList(asList(valuesManifestOutcome))
            .manifestStoreTypeVisited(new HashSet<>(Collections.singletonList(ManifestStoreType.CUSTOM_REMOTE)))
            .infrastructure(K8sDirectInfrastructureOutcome.builder().build())
            .shouldOpenFetchFilesStream(true)
            .shouldCloseFetchFilesStream(false)
            .build();
    Map<String, Collection<CustomSourceFile>> valuesFilesContentMap = new HashMap<>();
    valuesFilesContentMap.put("id",
        asList(CustomSourceFile.builder().fileContent("values yaml payload").filePath("path/to/values.yaml").build()));
    UnitProgressData unitProgressData = UnitProgressData.builder().build();
    CustomManifestValuesFetchResponse customManifestValuesFetchResponse =
        CustomManifestValuesFetchResponse.builder()
            .valuesFilesContentMap(valuesFilesContentMap)
            .zippedManifestFileId("zip")
            .commandExecutionStatus(SUCCESS)
            .unitProgressData(unitProgressData)
            .build();

    Map<String, ResponseData> responseDataMap =
        ImmutableMap.of("custom-manifest-values-fetch-response", customManifestValuesFetchResponse);
    ThrowingSupplier responseDataSuplier = StrategyHelper.buildResponseDataSupplier(responseDataMap);

    TaskChainResponse taskChainResponse = nativeHelmStepHelper.executeNextLink(
        nativeHelmStepExecutor, ambiance, stepElementParams, passThroughData, responseDataSuplier);

    K8sStepPassThroughData updatedK8sStepPassThroughData =
        passThroughData.toBuilder()
            .manifestOutcomeList(asList(valuesManifestOutcome))
            .customFetchContent(customManifestValuesFetchResponse.getValuesFilesContentMap())
            .zippedManifestFileId(customManifestValuesFetchResponse.getZippedManifestFileId())
            .shouldOpenFetchFilesStream(false)
            .shouldCloseFetchFilesStream(true)
            .build();

    assertThat(taskChainResponse).isNotNull();
    assertThat(taskChainResponse.getPassThroughData()).isNotNull();
    assertThat(taskChainResponse.getPassThroughData()).isInstanceOf(K8sStepPassThroughData.class);
    assertThat(taskChainResponse.getPassThroughData()).isEqualTo(updatedK8sStepPassThroughData);
    assertThat(taskChainResponse.getTaskRequest().getDelegateTaskRequest().getTaskName())
        .isEqualTo(TaskType.HELM_VALUES_FETCH_NG.getDisplayName());
  }

  @Test
  @Owner(developers = ACASIAN)
  @Category(UnitTests.class)
  public void shouldHandleHelmValueFetchResponse() throws Exception {
    StepElementParameters stepElementParams =
        StepElementParameters.builder().spec(HelmDeployStepParams.infoBuilder().build()).build();

    K8sStepPassThroughData passThroughData = K8sStepPassThroughData.builder()
                                                 .manifestOutcome(HelmChartManifestOutcome.builder().build())
                                                 .infrastructure(K8sDirectInfrastructureOutcome.builder().build())
                                                 .shouldOpenFetchFilesStream(true)
                                                 .shouldCloseFetchFilesStream(true)
                                                 .build();

    UnitProgressData unitProgressData = UnitProgressData.builder().build();
    HelmValuesFetchResponse helmValuesFetchResponse = HelmValuesFetchResponse.builder()
                                                          .valuesFileContent("values yaml payload")
                                                          .commandExecutionStatus(SUCCESS)
                                                          .unitProgressData(unitProgressData)
                                                          .build();
    Map<String, ResponseData> responseDataMap = ImmutableMap.of("helm-value-fetch-response", helmValuesFetchResponse);
    ThrowingSupplier responseDataSuplier = StrategyHelper.buildResponseDataSupplier(responseDataMap);

    nativeHelmStepHelper.executeNextLink(
        nativeHelmStepExecutor, ambiance, stepElementParams, passThroughData, responseDataSuplier);

    ArgumentCaptor<List> valuesFilesContentCaptor = ArgumentCaptor.forClass(List.class);
    verify(nativeHelmStepExecutor, times(1))
        .executeHelmTask(eq(passThroughData.getManifestOutcome()), eq(ambiance), eq(stepElementParams),
            valuesFilesContentCaptor.capture(),
            eq(NativeHelmExecutionPassThroughData.builder()
                    .infrastructure(passThroughData.getInfrastructure())
                    .lastActiveUnitProgressData(unitProgressData)
                    .build()),
            eq(passThroughData.getShouldOpenFetchFilesStream()), eq(unitProgressData));

    List<String> valuesFilesContent = valuesFilesContentCaptor.getValue();
    assertThat(valuesFilesContent).isNotEmpty();
    assertThat(valuesFilesContent.get(0)).isEqualTo("values yaml payload");
  }

  @Test
  @Owner(developers = PRATYUSH)
  @Category(UnitTests.class)
  public void shouldHandleHelmValueFetchResponseOnNewDelegate() throws Exception {
    StepElementParameters stepElementParams =
        StepElementParameters.builder().spec(HelmDeployStepParams.infoBuilder().build()).build();

    String manifestIdentifier = "manifest-identifier";
    HelmFetchFileResult valuesYamlList =
        HelmFetchFileResult.builder().valuesFileContents(new ArrayList<>(asList("values yaml payload"))).build();
    Map<String, HelmFetchFileResult> helmChartValuesFileMapContent = new HashMap<>();
    helmChartValuesFileMapContent.put(manifestIdentifier, valuesYamlList);
    List<ManifestFiles> manifestFilesList = asList(ManifestFiles.builder().build());
    K8sStepPassThroughData passThroughData =
        K8sStepPassThroughData.builder()
            .manifestOutcome(HelmChartManifestOutcome.builder().identifier(manifestIdentifier).build())
            .infrastructure(K8sDirectInfrastructureOutcome.builder().build())
            .manifestFiles(manifestFilesList)
            .build();

    UnitProgressData unitProgressData = UnitProgressData.builder().build();
    HelmValuesFetchResponse helmValuesFetchResponse = HelmValuesFetchResponse.builder()
                                                          .helmChartValuesFileMapContent(helmChartValuesFileMapContent)
                                                          .commandExecutionStatus(SUCCESS)
                                                          .unitProgressData(unitProgressData)
                                                          .build();
    Map<String, ResponseData> responseDataMap = ImmutableMap.of("helm-value-fetch-response", helmValuesFetchResponse);
    ThrowingSupplier responseDataSuplier = StrategyHelper.buildResponseDataSupplier(responseDataMap);

    nativeHelmStepHelper.executeNextLink(
        nativeHelmStepExecutor, ambiance, stepElementParams, passThroughData, responseDataSuplier);

    ArgumentCaptor<List> valuesFilesContentCaptor = ArgumentCaptor.forClass(List.class);
    verify(nativeHelmStepExecutor, times(1))
        .executeHelmTask(eq(passThroughData.getManifestOutcome()), eq(ambiance), eq(stepElementParams),
            valuesFilesContentCaptor.capture(),
            eq(NativeHelmExecutionPassThroughData.builder()
                    .infrastructure(passThroughData.getInfrastructure())
                    .lastActiveUnitProgressData(unitProgressData)
                    .manifestFiles(manifestFilesList)
                    .build()),
            eq(passThroughData.getShouldOpenFetchFilesStream()), eq(unitProgressData));

    List<String> valuesFilesContent = valuesFilesContentCaptor.getValue();
    assertThat(valuesFilesContent).isNotEmpty();
    assertThat(valuesFilesContent).isEqualTo(valuesYamlList.getValuesFileContents());
  }

  @Test
  @Owner(developers = PRATYUSH)
  @Category(UnitTests.class)
  public void shouldHandleHelmValueFetchResponseWithNativeHelmStepExecutor() throws Exception {
    StepElementParameters stepElementParams =
        StepElementParameters.builder().spec(HelmDeployStepParams.infoBuilder().build()).build();

    List<String> manifestIdentifiers = asList("manifest-identifier", "manifest-identifier2", "manifest-identifier3");
    List<ManifestOutcome> manifestOutcomeList = new ArrayList<>();
    HelmFetchFileResult valuesYamlList =
        HelmFetchFileResult.builder().valuesFileContents(new ArrayList<>(asList("values yaml payload"))).build();
    Map<String, HelmFetchFileResult> helmChartValuesFileMapContent = new HashMap<>();
    helmChartValuesFileMapContent.put(manifestIdentifiers.get(0), valuesYamlList);
    manifestOutcomeList.add(ValuesManifestOutcome.builder()
                                .identifier(manifestIdentifiers.get(1))
                                .store(HarnessStore.builder().build())
                                .build());
    Collection<CustomSourceFile> valuesYamlList2 =
        asList(CustomSourceFile.builder().filePath("/path").fileContent("values yaml payload").build());
    Map<String, Collection<CustomSourceFile>> customFetchContent = new HashMap<>();
    customFetchContent.put(manifestIdentifiers.get(1), valuesYamlList2);
    LocalStoreFetchFilesResult valuesYamlList3 =
        LocalStoreFetchFilesResult.builder()
            .LocalStoreFileContents(new ArrayList<>(asList("values yaml payload")))
            .build();
    manifestOutcomeList.add(ValuesManifestOutcome.builder()
                                .identifier(manifestIdentifiers.get(2))
                                .store(CustomRemoteStoreConfig.builder().build())
                                .build());
    Map<String, LocalStoreFetchFilesResult> localStoreFetchFilesResultMap = new HashMap<>();
    localStoreFetchFilesResultMap.put(manifestIdentifiers.get(2), valuesYamlList3);
    List<ManifestFiles> manifestFilesList = asList(ManifestFiles.builder().build());
    K8sStepPassThroughData passThroughData = K8sStepPassThroughData.builder()
                                                 .manifestOutcome(HelmChartManifestOutcome.builder()
                                                                      .identifier(manifestIdentifiers.get(0))
                                                                      .store(HttpStoreConfig.builder().build())
                                                                      .build())
                                                 .infrastructure(K8sDirectInfrastructureOutcome.builder().build())
                                                 .manifestFiles(manifestFilesList)
                                                 .customFetchContent(customFetchContent)
                                                 .localStoreFileMapContents(localStoreFetchFilesResultMap)
                                                 .manifestOutcomeList(manifestOutcomeList)
                                                 .shouldOpenFetchFilesStream(true)
                                                 .shouldCloseFetchFilesStream(false)
                                                 .build();

    UnitProgressData unitProgressData = UnitProgressData.builder().build();
    HelmValuesFetchResponse helmValuesFetchResponse = HelmValuesFetchResponse.builder()
                                                          .helmChartValuesFileMapContent(helmChartValuesFileMapContent)
                                                          .commandExecutionStatus(SUCCESS)
                                                          .unitProgressData(unitProgressData)
                                                          .build();
    Map<String, ResponseData> responseDataMap = ImmutableMap.of("helm-value-fetch-response", helmValuesFetchResponse);
    ThrowingSupplier responseDataSuplier = StrategyHelper.buildResponseDataSupplier(responseDataMap);

    nativeHelmStepHelper.executeNextLink(
        nativeHelmStepExecutor, ambiance, stepElementParams, passThroughData, responseDataSuplier);

    ArgumentCaptor<List> valuesFilesContentCaptor = ArgumentCaptor.forClass(List.class);
    verify(nativeHelmStepExecutor, times(1))
        .executeHelmTask(eq(passThroughData.getManifestOutcome()), eq(ambiance), eq(stepElementParams),
            valuesFilesContentCaptor.capture(),
            eq(NativeHelmExecutionPassThroughData.builder()
                    .infrastructure(passThroughData.getInfrastructure())
                    .lastActiveUnitProgressData(unitProgressData)
                    .manifestFiles(manifestFilesList)
                    .build()),
            eq(passThroughData.getShouldOpenFetchFilesStream()), eq(unitProgressData));

    List<String> valuesFilesContent = valuesFilesContentCaptor.getValue();
    assertThat(valuesFilesContent).isNotEmpty();
    assertThat(valuesFilesContent)
        .isEqualTo(asList("values yaml payload", "values yaml payload", "values yaml payload"));
  }

  @Test
  @Owner(developers = PRATYUSH)
  @Category(UnitTests.class)
  public void shouldHandleHelmValueFetchResponseWithAggregateManifestOutcome() throws Exception {
    StepElementParameters stepElementParams =
        StepElementParameters.builder().spec(HelmDeployStepParams.infoBuilder().build()).build();

    String manifestIdentifier = "manifest-identifier";
    HelmFetchFileResult valuesYamlList =
        HelmFetchFileResult.builder()
            .valuesFileContents(new ArrayList<>(asList("values yaml payload", "values yaml payload")))
            .build();
    LocalStoreFetchFilesResult localStoreFetchFilesResult =
        LocalStoreFetchFilesResult.builder()
            .LocalStoreFileContents(new ArrayList<>(asList("values yaml payload")))
            .build();

    Map<String, HelmFetchFileResult> helmChartValuesFileMapContent = new HashMap<>();
    helmChartValuesFileMapContent.put(manifestIdentifier, valuesYamlList);
    helmChartValuesFileMapContent.put("helmOverride2",
        HelmFetchFileResult.builder().valuesFileContents(new ArrayList<>(asList("values yaml payload"))).build());

    List<String> files = asList("org:/path/to/helm/chart");
    HarnessStore harnessStore = HarnessStore.builder().files(ParameterField.createValueField(files)).build();
    Map<String, LocalStoreFetchFilesResult> localStoreFetchFilesResultMap = new HashMap<>();
    localStoreFetchFilesResultMap.put("helmOverride3", localStoreFetchFilesResult);

    HttpStoreConfig httpStore =
        HttpStoreConfig.builder().connectorRef(ParameterField.createValueField("http-connector")).build();
    HelmChartManifestOutcome helmChartManifestOutcome =
        HelmChartManifestOutcome.builder()
            .identifier(manifestIdentifier)
            .store(httpStore)
            .chartName(ParameterField.createValueField("chart"))
            .valuesPaths(ParameterField.createValueField(asList("valuesOverride.yaml")))
            .build();
    List<String> overridePaths = new ArrayList<>(asList("folderPath/values2.yaml", "folderPath/values3.yaml"));
    GitStore gitStore2 = GitStore.builder()
                             .branch(ParameterField.createValueField("master"))
                             .paths(ParameterField.createValueField(overridePaths))
                             .connectorRef(ParameterField.createValueField("git-connector"))
                             .build();
    ValuesManifestOutcome valuesManifestOutcome1 =
        ValuesManifestOutcome.builder().identifier("helmOverride").store(gitStore2).build();
    InheritFromManifestStoreConfig inheritFromManifestStore =
        InheritFromManifestStoreConfig.builder().paths(ParameterField.createValueField(asList("values4.yaml"))).build();
    ValuesManifestOutcome valuesManifestOutcome2 =
        ValuesManifestOutcome.builder().identifier("helmOverride2").store(inheritFromManifestStore).build();
    ValuesManifestOutcome valuesManifestOutcome3 =
        ValuesManifestOutcome.builder().identifier("helmOverride3").store(harnessStore).build();
    Map<String, ManifestOutcome> manifestOutcomeMap = ImmutableMap.of("helm", helmChartManifestOutcome, "helmOverride",
        valuesManifestOutcome1, "helmOverride2", valuesManifestOutcome2, "helmOverride3", valuesManifestOutcome3);
    ManifestsOutcome manifestOutcomes = (ManifestsOutcome) OptionalOutcome.builder()
                                            .found(true)
                                            .outcome(new ManifestsOutcome(manifestOutcomeMap))
                                            .build()
                                            .getOutcome();
    Collection<ManifestOutcome> manifestOutcomes1 = manifestOutcomes.values();
    List<ManifestOutcome> manifestOutcome = manifestOutcomes1.stream()
                                                .sorted(Comparator.comparingInt(ManifestOutcome::getOrder))
                                                .collect(Collectors.toCollection(LinkedList::new));
    List<ValuesManifestOutcome> aggregatedValuesManifests =
        K8sHelmCommonStepHelper.getAggregatedValuesManifests(manifestOutcome);

    K8sStepPassThroughData passThroughData =
        K8sStepPassThroughData.builder()
            .manifestOutcome(HelmChartManifestOutcome.builder().identifier(manifestIdentifier).build())
            .infrastructure(K8sDirectInfrastructureOutcome.builder().build())
            .manifestOutcomeList(new ArrayList<>(aggregatedValuesManifests))
            .helmValuesFileMapContents(helmChartValuesFileMapContent)
            .localStoreFileMapContents(localStoreFetchFilesResultMap)
            .build();

    UnitProgressData unitProgressData = UnitProgressData.builder().build();
    HelmValuesFetchResponse helmValuesFetchResponse = HelmValuesFetchResponse.builder()
                                                          .helmChartValuesFileMapContent(helmChartValuesFileMapContent)
                                                          .commandExecutionStatus(SUCCESS)
                                                          .unitProgressData(unitProgressData)
                                                          .build();
    Map<String, ResponseData> responseDataMap = ImmutableMap.of("helm-value-fetch-response", helmValuesFetchResponse);
    ThrowingSupplier responseDataSuplier = StrategyHelper.buildResponseDataSupplier(responseDataMap);

    TaskRequest taskRequest = TaskRequest.getDefaultInstance();
    TaskChainResponse taskChainResponse = TaskChainResponse.builder().chainEnd(false).taskRequest(taskRequest).build();
    doReturn(taskChainResponse).when(nativeHelmStepHelper).executeValuesFetchTask(any(), any(), any(), any(), any());
    nativeHelmStepHelper.executeNextLink(
        nativeHelmStepExecutor, ambiance, stepElementParams, passThroughData, responseDataSuplier);

    ArgumentCaptor<Map> valuesFilesContentCaptor = ArgumentCaptor.forClass(Map.class);
    ArgumentCaptor<K8sStepPassThroughData> valuesFilesContentCaptor2 =
        ArgumentCaptor.forClass(K8sStepPassThroughData.class);
    verify(nativeHelmStepHelper, times(1))
        .executeValuesFetchTask(eq(ambiance), eq(stepElementParams), eq(passThroughData.getValuesManifestOutcomes()),
            valuesFilesContentCaptor.capture(), valuesFilesContentCaptor2.capture());

    Map<String, HelmFetchFileResult> duplicatehelmChartValuesFileMapContent = valuesFilesContentCaptor.getValue();
    assertThat(duplicatehelmChartValuesFileMapContent).isNotEmpty();
    assertThat(duplicatehelmChartValuesFileMapContent).isEqualTo(helmChartValuesFileMapContent);
    K8sStepPassThroughData nativeHelmStepPassThroughData = valuesFilesContentCaptor2.getValue();
    assertThat(nativeHelmStepPassThroughData.getLocalStoreFileMapContents().size()).isEqualTo(1);
    assertThat(nativeHelmStepPassThroughData.getLocalStoreFileMapContents().get("helmOverride3"))
        .isEqualTo(localStoreFetchFilesResult);
  }

  @Test
  @Owner(developers = PRATYUSH)
  @Category(UnitTests.class)
  public void shouldhandleGitFetchFilesResponseFromHandleHelmValueFetchResponse() throws Exception {
    StepElementParameters stepElementParams =
        StepElementParameters.builder().spec(HelmDeployStepParams.infoBuilder().build()).build();

    String manifestIdentifier = "manifest-identifier";
    HelmFetchFileResult valuesYamlList =
        HelmFetchFileResult.builder()
            .valuesFileContents(new ArrayList<>(asList("values yaml payload", "values yaml payload")))
            .build();
    LocalStoreFetchFilesResult localStoreFetchFilesResult =
        LocalStoreFetchFilesResult.builder()
            .LocalStoreFileContents(new ArrayList<>(asList("values yaml payload")))
            .build();
    Map<String, HelmFetchFileResult> helmChartValuesFileMapContent = new HashMap<>();
    helmChartValuesFileMapContent.put(manifestIdentifier, valuesYamlList);
    helmChartValuesFileMapContent.put("helmOverride2",
        HelmFetchFileResult.builder().valuesFileContents(new ArrayList<>(asList("values yaml payload"))).build());

    Map<String, FetchFilesResult> filesFromMultipleRepo = new HashMap<>();
    filesFromMultipleRepo.put("helmOverride",
        FetchFilesResult.builder()
            .files(asList(
                GitFile.builder().fileContent("values yaml payload").filePath("folderPath/values2.yaml").build()))
            .build());

    List<String> files = asList("org:/path/to/helm/chart");
    HarnessStore harnessStore = HarnessStore.builder().files(ParameterField.createValueField(files)).build();
    Map<String, LocalStoreFetchFilesResult> localStoreFetchFilesResultMap = new HashMap<>();
    localStoreFetchFilesResultMap.put("helmOverride3", localStoreFetchFilesResult);

    StoreConfig store = CustomRemoteStoreConfig.builder().build();
    Map<String, Collection<CustomSourceFile>> valuesFilesContentMap = new HashMap<>();
    valuesFilesContentMap.put("helmOverride4",
        asList(CustomSourceFile.builder().fileContent("values yaml payload").filePath("path/to/values.yaml").build()));

    HttpStoreConfig httpStore =
        HttpStoreConfig.builder().connectorRef(ParameterField.createValueField("http-connector")).build();
    HelmChartManifestOutcome helmChartManifestOutcome =
        HelmChartManifestOutcome.builder()
            .identifier(manifestIdentifier)
            .store(httpStore)
            .chartName(ParameterField.createValueField("chart"))
            .valuesPaths(ParameterField.createValueField(asList("valuesOverride.yaml")))
            .build();
    List<String> overridePaths = new ArrayList<>(asList("folderPath/values2.yaml"));
    GitStore gitStore2 = GitStore.builder()
                             .branch(ParameterField.createValueField("master"))
                             .paths(ParameterField.createValueField(overridePaths))
                             .connectorRef(ParameterField.createValueField("git-connector"))
                             .build();
    ValuesManifestOutcome valuesManifestOutcome1 =
        ValuesManifestOutcome.builder().identifier("helmOverride").store(gitStore2).build();
    InheritFromManifestStoreConfig inheritFromManifestStore =
        InheritFromManifestStoreConfig.builder().paths(ParameterField.createValueField(asList("values4.yaml"))).build();
    ValuesManifestOutcome valuesManifestOutcome2 =
        ValuesManifestOutcome.builder().identifier("helmOverride2").store(inheritFromManifestStore).build();
    ValuesManifestOutcome valuesManifestOutcome3 =
        ValuesManifestOutcome.builder().identifier("helmOverride3").store(harnessStore).build();
    ValuesManifestOutcome valuesManifestOutcome4 =
        ValuesManifestOutcome.builder().identifier("helmOverride4").store(store).build();
    Map<String, ManifestOutcome> manifestOutcomeMap =
        ImmutableMap.of("helm", helmChartManifestOutcome, "helmOverride", valuesManifestOutcome1, "helmOverride2",
            valuesManifestOutcome2, "helmOverride3", valuesManifestOutcome3, "helmOverride4", valuesManifestOutcome4);
    ManifestsOutcome manifestOutcomes = (ManifestsOutcome) OptionalOutcome.builder()
                                            .found(true)
                                            .outcome(new ManifestsOutcome(manifestOutcomeMap))
                                            .build()
                                            .getOutcome();
    Collection<ManifestOutcome> manifestOutcomes1 = manifestOutcomes.values();
    List<ManifestOutcome> manifestOutcome = manifestOutcomes1.stream()
                                                .sorted(Comparator.comparingInt(ManifestOutcome::getOrder))
                                                .collect(Collectors.toCollection(LinkedList::new));
    List<ValuesManifestOutcome> aggregatedValuesManifests =
        K8sHelmCommonStepHelper.getAggregatedValuesManifests(manifestOutcome);

    K8sStepPassThroughData passThroughData =
        K8sStepPassThroughData.builder()
            .manifestOutcome(HelmChartManifestOutcome.builder().identifier(manifestIdentifier).build())
            .infrastructure(K8sDirectInfrastructureOutcome.builder().build())
            .manifestOutcomeList(new ArrayList<>(aggregatedValuesManifests))
            .helmValuesFileMapContents(helmChartValuesFileMapContent)
            .localStoreFileMapContents(localStoreFetchFilesResultMap)
            .customFetchContent(valuesFilesContentMap)
            .zippedManifestFileId("helmOverride4")
            .shouldOpenFetchFilesStream(false)
            .shouldCloseFetchFilesStream(false)
            .build();

    UnitProgressData unitProgressData = UnitProgressData.builder().build();
    GitFetchResponse gitFetchResponse = GitFetchResponse.builder()
                                            .filesFromMultipleRepo(filesFromMultipleRepo)
                                            .taskStatus(TaskStatus.SUCCESS)
                                            .unitProgressData(unitProgressData)
                                            .build();
    Map<String, ResponseData> responseDataMap = ImmutableMap.of("git-fetch-response", gitFetchResponse);
    ThrowingSupplier responseDataSuplier = StrategyHelper.buildResponseDataSupplier(responseDataMap);

    nativeHelmStepHelper.executeNextLink(
        nativeHelmStepExecutor, ambiance, stepElementParams, passThroughData, responseDataSuplier);

    ArgumentCaptor<List> valuesFilesContentCaptor = ArgumentCaptor.forClass(List.class);
    verify(nativeHelmStepExecutor, times(1))
        .executeHelmTask(eq(passThroughData.getManifestOutcome()), eq(ambiance), eq(stepElementParams),
            valuesFilesContentCaptor.capture(),
            eq(NativeHelmExecutionPassThroughData.builder()
                    .infrastructure(passThroughData.getInfrastructure())
                    .lastActiveUnitProgressData(unitProgressData)
                    .zippedManifestId("helmOverride4")
                    .build()),
            eq(passThroughData.getShouldOpenFetchFilesStream()), eq(unitProgressData));

    List<String> valuesFilesContent = valuesFilesContentCaptor.getValue();
    assertThat(valuesFilesContent).isNotEmpty();
    assertThat(valuesFilesContent.size()).isEqualTo(6);
  }

  @Test
  @Owner(developers = PRATYUSH)
  @Category(UnitTests.class)
  public void shouldHandleGitFetchFilesResponse() throws Exception {
    StepElementParameters stepElementParams =
        StepElementParameters.builder().spec(HelmDeployStepParams.infoBuilder().build()).build();

    String manifestIdentifier = "manifest-identifier";
    GitFile gitFile = GitFile.builder().fileContent("values yaml payload").filePath("folderPath/values2.yaml").build();
    FetchFilesResult dummyFetchfileResults = FetchFilesResult.builder().files(asList(gitFile)).build();
    FetchFilesResult dummyDoubleFetchfileResults = FetchFilesResult.builder().files(asList(gitFile, gitFile)).build();
    Map<String, FetchFilesResult> filesFromMultipleRepo = new HashMap<>();
    filesFromMultipleRepo.put(manifestIdentifier, dummyDoubleFetchfileResults);
    filesFromMultipleRepo.put("helmOverride2", dummyFetchfileResults);
    filesFromMultipleRepo.put("helmOverride", dummyFetchfileResults);

    GitStore gitStore = GitStore.builder()
                            .branch(ParameterField.createValueField("master"))
                            .folderPath(ParameterField.createValueField("path/to/helm/chart"))
                            .connectorRef(ParameterField.createValueField("git-connector"))
                            .build();
    HelmChartManifestOutcome helmChartManifestOutcome =
        HelmChartManifestOutcome.builder()
            .identifier(manifestIdentifier)
            .store(gitStore)
            .valuesPaths(ParameterField.createValueField(asList("valuesOverride.yaml")))
            .build();
    List<String> overridePaths = new ArrayList<>(asList("folderPath/values2.yaml"));
    GitStore gitStore2 = GitStore.builder()
                             .branch(ParameterField.createValueField("master"))
                             .paths(ParameterField.createValueField(overridePaths))
                             .connectorRef(ParameterField.createValueField("git-connector"))
                             .build();
    ValuesManifestOutcome valuesManifestOutcome1 =
        ValuesManifestOutcome.builder().identifier("helmOverride").store(gitStore2).build();
    InheritFromManifestStoreConfig inheritFromManifestStore =
        InheritFromManifestStoreConfig.builder().paths(ParameterField.createValueField(asList("values4.yaml"))).build();
    ValuesManifestOutcome valuesManifestOutcome2 =
        ValuesManifestOutcome.builder().identifier("helmOverride2").store(inheritFromManifestStore).build();
    Map<String, ManifestOutcome> manifestOutcomeMap = ImmutableMap.of(
        "k8s", helmChartManifestOutcome, "k8sOverride", valuesManifestOutcome1, "k8sOverride2", valuesManifestOutcome2);
    ManifestsOutcome manifestOutcomes = (ManifestsOutcome) OptionalOutcome.builder()
                                            .found(true)
                                            .outcome(new ManifestsOutcome(manifestOutcomeMap))
                                            .build()
                                            .getOutcome();
    Collection<ManifestOutcome> manifestOutcomes1 = manifestOutcomes.values();
    List<ManifestOutcome> manifestOutcome = manifestOutcomes1.stream()
                                                .sorted(Comparator.comparingInt(ManifestOutcome::getOrder))
                                                .collect(Collectors.toCollection(LinkedList::new));
    List<ValuesManifestOutcome> aggregatedValuesManifests =
        K8sHelmCommonStepHelper.getAggregatedValuesManifests(manifestOutcome);
    LinkedList<ValuesManifestOutcome> orderedValuesManifests = new LinkedList<>(aggregatedValuesManifests);
    ValuesManifestOutcome valuesManifestOutcome =
        ValuesManifestOutcome.builder().identifier(helmChartManifestOutcome.getIdentifier()).store(gitStore).build();
    orderedValuesManifests.addFirst(valuesManifestOutcome);

    K8sStepPassThroughData passThroughData =
        K8sStepPassThroughData.builder()
            .manifestOutcome(HelmChartManifestOutcome.builder().identifier(manifestIdentifier).build())
            .infrastructure(K8sDirectInfrastructureOutcome.builder().build())
            .manifestOutcomeList(new ArrayList<>(orderedValuesManifests))
            .shouldOpenFetchFilesStream(false)
            .shouldCloseFetchFilesStream(false)
            .build();

    UnitProgressData unitProgressData = UnitProgressData.builder().build();
    GitFetchResponse gitFetchResponse = GitFetchResponse.builder()
                                            .filesFromMultipleRepo(filesFromMultipleRepo)
                                            .taskStatus(TaskStatus.SUCCESS)
                                            .unitProgressData(unitProgressData)
                                            .build();
    Map<String, ResponseData> responseDataMap = ImmutableMap.of("git-fetch-response", gitFetchResponse);
    ThrowingSupplier responseDataSuplier = StrategyHelper.buildResponseDataSupplier(responseDataMap);

    nativeHelmStepHelper.executeNextLink(
        nativeHelmStepExecutor, ambiance, stepElementParams, passThroughData, responseDataSuplier);

    ArgumentCaptor<List> valuesFilesContentCaptor = ArgumentCaptor.forClass(List.class);
    verify(nativeHelmStepExecutor, times(1))
        .executeHelmTask(eq(passThroughData.getManifestOutcome()), eq(ambiance), eq(stepElementParams),
            valuesFilesContentCaptor.capture(),
            eq(NativeHelmExecutionPassThroughData.builder()
                    .infrastructure(passThroughData.getInfrastructure())
                    .lastActiveUnitProgressData(unitProgressData)
                    .build()),
            eq(passThroughData.getShouldOpenFetchFilesStream()), eq(unitProgressData));

    List<String> valuesFilesContent = valuesFilesContentCaptor.getValue();
    assertThat(valuesFilesContent).isNotEmpty();
    assertThat(valuesFilesContent.size()).isEqualTo(4);
  }

  @Test
  @Owner(developers = ACASIAN)
  @Category(UnitTests.class)
  public void shouldHandleHelmValueFetchResponseFailure() throws Exception {
    StepElementParameters rollingStepElementParams =
        StepElementParameters.builder().spec(HelmDeployStepParams.infoBuilder().build()).build();

    K8sStepPassThroughData passThroughData = K8sStepPassThroughData.builder()
                                                 .manifestOutcome(HelmChartManifestOutcome.builder().build())
                                                 .infrastructure(K8sDirectInfrastructureOutcome.builder().build())
                                                 .build();

    HelmValuesFetchResponse helmValuesFetchResponse =
        HelmValuesFetchResponse.builder().commandExecutionStatus(FAILURE).errorMessage("Something went wrong").build();
    Map<String, ResponseData> responseDataMap = ImmutableMap.of("helm-value-fetch-response", helmValuesFetchResponse);
    ThrowingSupplier responseDataSuplier = StrategyHelper.buildResponseDataSupplier(responseDataMap);

    TaskChainResponse response = nativeHelmStepHelper.executeNextLink(
        nativeHelmStepExecutor, ambiance, rollingStepElementParams, passThroughData, responseDataSuplier);

    assertThat(response.getPassThroughData()).isNotNull();
    assertThat(response.isChainEnd()).isTrue();
    assertThat(response.getPassThroughData()).isInstanceOf(HelmValuesFetchResponsePassThroughData.class);
    HelmValuesFetchResponsePassThroughData helmPassThroughData =
        (HelmValuesFetchResponsePassThroughData) response.getPassThroughData();
    assertThat(helmPassThroughData.getErrorMsg()).isEqualTo("Something went wrong");
  }

  @Test
  @Owner(developers = PRATYUSH)
  @Category(UnitTests.class)
  public void shouldHandleCustomManifestValueFetchResponseFailure() throws Exception {
    StepElementParameters rollingStepElementParams =
        StepElementParameters.builder().spec(HelmDeployStepParams.infoBuilder().build()).build();

    K8sStepPassThroughData passThroughData = K8sStepPassThroughData.builder()
                                                 .manifestOutcome(HelmChartManifestOutcome.builder().build())
                                                 .infrastructure(K8sDirectInfrastructureOutcome.builder().build())
                                                 .build();

    CustomManifestValuesFetchResponse customManifestValuesFetchResponse = CustomManifestValuesFetchResponse.builder()
                                                                              .commandExecutionStatus(FAILURE)
                                                                              .errorMessage("Something went wrong")
                                                                              .build();
    Map<String, ResponseData> responseDataMap =
        ImmutableMap.of("helm-value-fetch-response", customManifestValuesFetchResponse);
    ThrowingSupplier responseDataSuplier = StrategyHelper.buildResponseDataSupplier(responseDataMap);

    TaskChainResponse response = nativeHelmStepHelper.executeNextLink(
        nativeHelmStepExecutor, ambiance, rollingStepElementParams, passThroughData, responseDataSuplier);

    assertThat(response.getPassThroughData()).isNotNull();
    assertThat(response.isChainEnd()).isTrue();
    assertThat(response.getPassThroughData()).isInstanceOf(CustomFetchResponsePassThroughData.class);
    CustomFetchResponsePassThroughData helmPassThroughData =
        (CustomFetchResponsePassThroughData) response.getPassThroughData();
    assertThat(helmPassThroughData.getErrorMsg()).isEqualTo("Something went wrong");
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testExecuteNextLinkInternalStepException() throws Exception {
    StepElementParameters stepElementParameters =
        StepElementParameters.builder().spec(HelmDeployStepParams.infoBuilder().build()).build();
    UnitProgressData unitProgressData =
        UnitProgressData.builder()
            .unitProgresses(
                asList(UnitProgress.newBuilder().setUnitName("Fetch Files").setStatus(UnitStatus.RUNNING).build(),
                    UnitProgress.newBuilder().setUnitName("Some Unit").setStatus(UnitStatus.SUCCESS).build()))
            .build();

    K8sStepPassThroughData passThroughData = K8sStepPassThroughData.builder()
                                                 .manifestOutcome(HelmChartManifestOutcome.builder().build())
                                                 .infrastructure(K8sDirectInfrastructureOutcome.builder().build())
                                                 .shouldOpenFetchFilesStream(true)
                                                 .build();

    GitFetchResponse gitFetchResponse = GitFetchResponse.builder()
                                            .filesFromMultipleRepo(emptyMap())
                                            .taskStatus(TaskStatus.SUCCESS)
                                            .unitProgressData(unitProgressData)
                                            .build();
    Map<String, ResponseData> responseDataMap = ImmutableMap.of("git-fetch-response", gitFetchResponse);
    ThrowingSupplier responseDataSuplier = StrategyHelper.buildResponseDataSupplier(responseDataMap);
    RuntimeException thrownException = new RuntimeException("Failed to do something");

    doThrow(thrownException)
        .when(nativeHelmStepExecutor)
        .executeHelmTask(passThroughData.getManifestOutcome(), ambiance, stepElementParameters, Collections.emptyList(),
            NativeHelmExecutionPassThroughData.builder()
                .infrastructure(passThroughData.getInfrastructure())
                .lastActiveUnitProgressData(unitProgressData)
                .build(),
            false, unitProgressData);

    TaskChainResponse response = nativeHelmStepHelper.executeNextLink(
        nativeHelmStepExecutor, ambiance, stepElementParameters, passThroughData, responseDataSuplier);

    assertThat(response.getPassThroughData()).isInstanceOf(StepExceptionPassThroughData.class);
    StepExceptionPassThroughData stepExceptionData = (StepExceptionPassThroughData) response.getPassThroughData();
    assertThat(stepExceptionData.getErrorMessage()).isEqualTo(ExceptionUtils.getMessage(thrownException));
    List<UnitProgress> unitProgresses = stepExceptionData.getUnitProgressData().getUnitProgresses();
    assertThat(unitProgresses).hasSize(2);
    assertThat(unitProgresses.get(0).getEndTime()).isNotZero();
    assertThat(unitProgresses.get(0).getStatus()).isEqualTo(UnitStatus.FAILURE);

    verify(mockLogCallback, times(1))
        .saveExecutionLog(ExceptionUtils.getMessage(thrownException), LogLevel.ERROR, FAILURE);
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testHandleStepExceptionFailure() {
    List<UnitProgress> progressList = Collections.singletonList(UnitProgress.newBuilder().build());
    StepExceptionPassThroughData data =
        StepExceptionPassThroughData.builder()
            .unitProgressData(UnitProgressData.builder().unitProgresses(progressList).build())
            .errorMessage("Something went wrong")
            .build();

    StepResponse result = cdStepHelper.handleStepExceptionFailure(data);

    assertThat(result.getUnitProgressList()).isEqualTo(progressList);
    assertThat(result.getStatus()).isEqualTo(Status.FAILED);
    assertThat(result.getFailureInfo().getFailureDataList()).hasSize(1);
    FailureData failureData = result.getFailureInfo().getFailureData(0);
    assertThat(failureData.getFailureTypesList()).contains(FailureType.APPLICATION_FAILURE);
    assertThat(failureData.getCode()).isEqualTo(GENERAL_ERROR.name());
    assertThat(failureData.getMessage()).isEqualTo("Something went wrong");
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testHandleTaskException() throws Exception {
    NativeHelmExecutionPassThroughData executionPassThroughData =
        NativeHelmExecutionPassThroughData.builder()
            .lastActiveUnitProgressData(
                UnitProgressData.builder()
                    .unitProgresses(
                        asList(UnitProgress.newBuilder().setUnitName("Completed").setStatus(UnitStatus.SUCCESS).build(),
                            UnitProgress.newBuilder().setUnitName("Running").setStatus(UnitStatus.RUNNING).build()))
                    .build())
            .build();

    Exception exception = new GeneralException("Something went wrong");

    StepResponse stepResponse = nativeHelmStepHelper.handleTaskException(ambiance, executionPassThroughData, exception);
    assertThat(stepResponse.getStatus()).isEqualTo(Status.FAILED);
    assertThat(stepResponse.getFailureInfo().getFailureDataList()).hasSize(1);
    assertThat(stepResponse.getFailureInfo().getFailureData(0).getMessage())
        .isEqualTo(ExceptionUtils.getMessage(exception));
    assertThat(stepResponse.getUnitProgressList()).hasSize(2);
    assertThat(stepResponse.getUnitProgressList().get(0).getStatus()).isEqualTo(UnitStatus.SUCCESS);
    assertThat(stepResponse.getUnitProgressList().get(1).getStatus()).isEqualTo(UnitStatus.FAILURE);
    assertThat(stepResponse.getUnitProgressList().get(1).getEndTime()).isNotZero();
  }

  @Test
  @Owner(developers = ACASIAN)
  @Category(UnitTests.class)
  public void testShouldCreateTaskRequestWithNonProdEnvType() {
    doReturn(NON_PROD).when(stepHelper).getEnvironmentType(ambiance);
    doReturn(Optional.empty()).when(cdStepHelper).getServiceHooksOutcome(ambiance);
    HelmSpecParameters stepParams = HelmDeployStepParams.infoBuilder().build();
    TaskChainResponse taskChainResponse = nativeHelmStepHelper.queueNativeHelmTask(
        StepElementParameters.builder().spec(stepParams).build(),
        HelmInstallCommandRequestNG.builder()
            .commandName("Rolling Deploy")
            .k8sInfraDelegateConfig(
                DirectK8sInfraDelegateConfig.builder()
                    .encryptionDataDetails(Collections.emptyList())
                    .kubernetesClusterConfigDTO(
                        KubernetesClusterConfigDTO.builder()
                            .credential(KubernetesCredentialDTO.builder()
                                            .kubernetesCredentialType(KubernetesCredentialType.INHERIT_FROM_DELEGATE)
                                            .build())
                            .build())
                    .build())
            .build(),
        ambiance, NativeHelmExecutionPassThroughData.builder().build());
    assertThat(
        taskChainResponse.getTaskRequest().getDelegateTaskRequest().getRequest().getSetupAbstractions().containsValues(
            "envType"))
        .isTrue();
    String value = taskChainResponse.getTaskRequest()
                       .getDelegateTaskRequest()
                       .getRequest()
                       .getSetupAbstractions()
                       .getValuesOrThrow("envType");
    assertThat(value).isEqualTo(NON_PROD.name());
  }

  @Test
  @Owner(developers = ACASIAN)
  @Category(UnitTests.class)
  public void testShouldCreateTaskRequestWithProdEnvType() {
    doReturn(PROD).when(stepHelper).getEnvironmentType(ambiance);
    doReturn(Optional.empty()).when(cdStepHelper).getServiceHooksOutcome(ambiance);
    HelmSpecParameters helmSpecParameters = HelmDeployStepParams.infoBuilder().build();
    TaskChainResponse taskChainResponse = nativeHelmStepHelper.queueNativeHelmTask(
        StepElementParameters.builder().spec(helmSpecParameters).build(),
        HelmInstallCommandRequestNG.builder()
            .commandName("Rolling Deploy")
            .k8sInfraDelegateConfig(
                DirectK8sInfraDelegateConfig.builder()
                    .encryptionDataDetails(Collections.emptyList())
                    .kubernetesClusterConfigDTO(
                        KubernetesClusterConfigDTO.builder()
                            .credential(KubernetesCredentialDTO.builder()
                                            .kubernetesCredentialType(KubernetesCredentialType.INHERIT_FROM_DELEGATE)
                                            .build())
                            .build())
                    .build())
            .build(),
        ambiance, NativeHelmExecutionPassThroughData.builder().build());
    assertThat(
        taskChainResponse.getTaskRequest().getDelegateTaskRequest().getRequest().getSetupAbstractions().containsValues(
            "envType"))
        .isTrue();
    String value = taskChainResponse.getTaskRequest()
                       .getDelegateTaskRequest()
                       .getRequest()
                       .getSetupAbstractions()
                       .getValuesOrThrow("envType");
    assertThat(value).isEqualTo(PROD.name());
  }

  @Test
  @Owner(developers = ACASIAN)
  @Category(UnitTests.class)
  public void testShouldPublishReleaseNameStepDetails() {
    nativeHelmStepHelper.publishReleaseNameStepDetails(ambiance, "test-release-name");

    ArgumentCaptor<NativeHelmReleaseDetailsInfo> releaseDetailsCaptor =
        ArgumentCaptor.forClass(NativeHelmReleaseDetailsInfo.class);
    ArgumentCaptor<String> releaseNameCaptor = ArgumentCaptor.forClass(String.class);
    verify(sdkGraphVisualizationDataService, times(1))
        .publishStepDetailInformation(eq(ambiance), releaseDetailsCaptor.capture(), releaseNameCaptor.capture());

    assertThat(releaseDetailsCaptor.getValue().getReleaseName()).isEqualTo("test-release-name");
    assertThat(releaseNameCaptor.getValue()).isEqualTo(RELEASE_NAME);
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testGetReleaseHistoryPrefix() {
    doReturn(false)
        .when(cdFeatureFlagHelper)
        .isEnabled("test-account", FeatureName.CDS_RENAME_HARNESS_RELEASE_HISTORY_RESOURCE_NATIVE_HELM_NG);
    assertThat(nativeHelmStepHelper.getReleaseHistoryPrefix(ambiance)).isNullOrEmpty();

    doReturn(true)
        .when(cdFeatureFlagHelper)
        .isEnabled("test-account", FeatureName.CDS_RENAME_HARNESS_RELEASE_HISTORY_RESOURCE_NATIVE_HELM_NG);
    assertThat(nativeHelmStepHelper.getReleaseHistoryPrefix(ambiance))
        .isEqualTo(NativeHelmStepHelper.RELEASE_HISTORY_PREFIX);
  }

  public Ambiance getAmbiance() {
    Map<String, String> setupAbstractions = new HashMap<>();

    setupAbstractions.put(SetupAbstractionKeys.accountId, "account1");
    setupAbstractions.put(SetupAbstractionKeys.orgIdentifier, "org1");
    setupAbstractions.put(SetupAbstractionKeys.projectIdentifier, "project1");

    return Ambiance.newBuilder().putAllSetupAbstractions(setupAbstractions).build();
  }

  @Test
  @Owner(developers = ACHYUTH)
  @Category(UnitTests.class)
  public void testExecuteValuesFetchTask() {
    Ambiance ambiance = getAmbiance();
    StepElementParameters stepElementParameters =
        StepElementParameters.builder().spec(HelmDeployStepParams.infoBuilder().build()).build();
    ConnectorInfoDTO connectorDTO = ConnectorInfoDTO.builder().connectorType(GITHUB).build();
    Optional<ConnectorResponseDTO> connectorDTOOptional =
        Optional.of(ConnectorResponseDTO.builder().connector(connectorDTO).build());
    doReturn(connectorDTOOptional).when(connectorService).get("account1", "org1", "project1", "abcConnector");

    K8sDirectInfrastructureOutcome outcomeBuilder =
        K8sDirectInfrastructureOutcome.builder().connectorRef("abcConnector").namespace("valid").build();

    HelmChartManifestOutcome manifestOutcome = HelmChartManifestOutcome.builder().build();

    List<ValuesManifestOutcome> aggregatedValuesManifests = new ArrayList<>();

    Map<String, HelmFetchFileResult> helmChartFetchFilesResultMap = new HashMap<>();

    K8sStepPassThroughData nativeHelmStepPassThroughData =
        K8sStepPassThroughData.builder().infrastructure(outcomeBuilder).manifestOutcome(manifestOutcome).build();

    assertThatCode(()
                       -> nativeHelmStepHelper.executeValuesFetchTask(ambiance, stepElementParameters,
                           aggregatedValuesManifests, helmChartFetchFilesResultMap, nativeHelmStepPassThroughData));
  }
  @Test
  @Owner(developers = TARUN_UBA)
  @Category(UnitTests.class)
  public void shouldHandleCustomManifestValuesAndGitValuesWithHarnessStore() throws Exception {
    StepElementParameters stepElementParams =
        StepElementParameters.builder().spec(HelmDeployStepParams.infoBuilder().build()).build();

    String manifestIdentifier = "manifest-identifier";
    LocalStoreFetchFilesResult localStoreFetchFilesResult =
        LocalStoreFetchFilesResult.builder()
            .LocalStoreFileContents(new ArrayList<>(asList("values yaml payload")))
            .build();
    List<String> files = asList("org:/path/to/helm/chart");
    HarnessStore harnessStore = HarnessStore.builder().files(ParameterField.createValueField(files)).build();
    Map<String, LocalStoreFetchFilesResult> localStoreFetchFilesResultMap = new HashMap<>();
    localStoreFetchFilesResultMap.put("helmOverride3", localStoreFetchFilesResult);
    HelmChartManifestOutcome helmChartManifestOutcome =
        HelmChartManifestOutcome.builder()
            .identifier(manifestIdentifier)
            .store(harnessStore)
            .chartName(ParameterField.createValueField("chart"))
            .valuesPaths(ParameterField.createValueField(asList("valuesOverride.yaml")))
            .build();
    List<String> overridePaths = new ArrayList<>(asList("folderPath/values2.yaml", "folderPath/values3.yaml"));
    GitStore gitStore2 = GitStore.builder()
                             .branch(ParameterField.createValueField("master"))
                             .paths(ParameterField.createValueField(overridePaths))
                             .connectorRef(ParameterField.createValueField("git-connector"))
                             .build();
    String extractionScript = "git clone something.git";
    List<TaskSelectorYaml> delegateSelector = asList(new TaskSelectorYaml("sample-delegate"));
    CustomRemoteStoreConfig customRemoteStoreConfig =
        CustomRemoteStoreConfig.builder()
            .filePath(ParameterField.createValueField("folderPath/values.yaml"))
            .extractionScript(ParameterField.createValueField(extractionScript))
            .delegateSelectors(ParameterField.createValueField(delegateSelector))
            .build();
    ValuesManifestOutcome valuesManifestOutcome1 =
        ValuesManifestOutcome.builder().identifier("helmOverride1").store(gitStore2).build();
    ValuesManifestOutcome valuesManifestOutcome2 =
        ValuesManifestOutcome.builder().identifier("helmOverride2").store(customRemoteStoreConfig).build();
    Map<String, Collection<CustomSourceFile>> valuesFilesContentMap = new HashMap<>();
    CustomSourceFile customSourceFile =
        CustomSourceFile.builder().fileContent("values yaml payload").filePath("path/to/values.yaml").build();
    valuesFilesContentMap.put("helmOverride2", asList(customSourceFile));
    Map<String, ManifestOutcome> manifestOutcomeMap = ImmutableMap.of("helm", helmChartManifestOutcome, "helmOverride2",
        valuesManifestOutcome2, "helmOverride1", valuesManifestOutcome1);
    ManifestsOutcome manifestOutcomes = (ManifestsOutcome) OptionalOutcome.builder()
                                            .found(true)
                                            .outcome(new ManifestsOutcome(manifestOutcomeMap))
                                            .build()
                                            .getOutcome();
    Collection<ManifestOutcome> manifestOutcomes1 = manifestOutcomes.values();
    List<ManifestOutcome> manifestOutcome = manifestOutcomes1.stream()
                                                .sorted(Comparator.comparingInt(ManifestOutcome::getOrder))
                                                .collect(Collectors.toCollection(LinkedList::new));
    List<ValuesManifestOutcome> aggregatedValuesManifests =
        K8sHelmCommonStepHelper.getAggregatedValuesManifests(manifestOutcome);

    K8sStepPassThroughData passThroughData = K8sStepPassThroughData.builder()
                                                 .manifestOutcome(helmChartManifestOutcome)
                                                 .infrastructure(K8sDirectInfrastructureOutcome.builder().build())
                                                 .manifestOutcomeList(new ArrayList<>(aggregatedValuesManifests))
                                                 .localStoreFileMapContents(localStoreFetchFilesResultMap)
                                                 .build();
    UnitProgressData unitProgressData = UnitProgressData.builder().build();
    CustomManifestValuesFetchResponse customManifestValuesFetchResponse =
        CustomManifestValuesFetchResponse.builder()
            .valuesFilesContentMap(valuesFilesContentMap)
            .zippedManifestFileId("zip")
            .commandExecutionStatus(SUCCESS)
            .unitProgressData(unitProgressData)
            .build();
    Map<String, ResponseData> responseDataMap =
        ImmutableMap.of("custom-value-fetch-response", customManifestValuesFetchResponse);
    ThrowingSupplier responseDataSupplier = StrategyHelper.buildResponseDataSupplier(responseDataMap);

    TaskRequest taskRequest = TaskRequest.getDefaultInstance();
    TaskChainResponse taskChainResponse = TaskChainResponse.builder().chainEnd(false).taskRequest(taskRequest).build();
    doReturn(taskChainResponse).when(nativeHelmStepHelper).executeValuesFetchTask(any(), any(), any(), any(), any());
    nativeHelmStepHelper.executeNextLink(
        nativeHelmStepExecutor, ambiance, stepElementParams, passThroughData, responseDataSupplier);

    ValuesManifestOutcome valuesManifestOutcome = ValuesManifestOutcome.builder()
                                                      .identifier(helmChartManifestOutcome.getIdentifier())
                                                      .store(harnessStore)
                                                      .build();
    LinkedList<ValuesManifestOutcome> orderedValuesManifests = new LinkedList<>(aggregatedValuesManifests);
    orderedValuesManifests.addFirst(valuesManifestOutcome);
    ArgumentCaptor<K8sStepPassThroughData> valuesFilesContentCaptor2 =
        ArgumentCaptor.forClass(K8sStepPassThroughData.class);
    verify(nativeHelmStepHelper, times(1))
        .executeValuesFetchTask(eq(ambiance), eq(stepElementParams), eq(orderedValuesManifests), eq(emptyMap()),
            valuesFilesContentCaptor2.capture());

    K8sStepPassThroughData nativeHelmStepPassThroughData = valuesFilesContentCaptor2.getValue();
    assertThat(nativeHelmStepPassThroughData.getLocalStoreFileMapContents().size()).isEqualTo(1);
    assertThat(nativeHelmStepPassThroughData.getLocalStoreFileMapContents().get("helmOverride3"))
        .isEqualTo(localStoreFetchFilesResult);
    assertThat(nativeHelmStepPassThroughData.getCustomFetchContent().get("helmOverride2"))
        .isEqualTo(valuesFilesContentMap.get("helmOverride2"));
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
    return FolderNodeDTO.builder().name(name).identifier("identifier").parentIdentifier("helm").path(path).build();
  }

  private UnitProgressData getUnitProgressData() {
    CommandUnitProgress commandUnitProgress =
        CommandUnitProgress.builder().status(CommandExecutionStatus.SUCCESS).build();
    LinkedHashMap<String, CommandUnitProgress> commandUnitProgressMap = new LinkedHashMap<>();
    commandUnitProgressMap.put(K8sCommandUnitConstants.FetchFiles, commandUnitProgress);
    return UnitProgressDataMapper.toUnitProgressData(
        CommandUnitsProgress.builder().commandUnitProgressMap(commandUnitProgressMap).build());
  }

  @Test
  @Owner(developers = ABHINAV2)
  @Category(UnitTests.class)
  public void testTaskTypeRancher() {
    K8sInfraDelegateConfig k8sInfraDelegateConfig = RancherK8sInfraDelegateConfig.builder().build();
    TaskType expectedTaskType = TaskType.HELM_COMMAND_TASK_NG_RANCHER;
    checkTaskType(k8sInfraDelegateConfig, expectedTaskType);
  }

  @Test
  @Owner(developers = ABHINAV2)
  @Category(UnitTests.class)
  public void testTaskTypeHooks() {
    K8sInfraDelegateConfig k8sInfraDelegateConfig = DirectK8sInfraDelegateConfig.builder().build();
    TaskType expectedTaskType = TaskType.HELM_COMMAND_TASK_NG_V2;
    doReturn(true).when(cdFeatureFlagHelper).isEnabled(any(), any(FeatureName.class));
    checkTaskType(k8sInfraDelegateConfig, expectedTaskType);
  }

  @Test
  @Owner(developers = ABHINAV2)
  @Category(UnitTests.class)
  public void testTaskType() {
    K8sInfraDelegateConfig k8sInfraDelegateConfig = DirectK8sInfraDelegateConfig.builder().build();
    TaskType expectedTaskType = TaskType.HELM_COMMAND_TASK_NG;
    checkTaskType(k8sInfraDelegateConfig, expectedTaskType);
  }

  private void checkTaskType(K8sInfraDelegateConfig k8sInfraDelegateConfig, TaskType expectedTaskType) {
    try (MockedStatic<K8sTaskCapabilityHelper> mockK8s = mockStatic(K8sTaskCapabilityHelper.class);
         MockedStatic<RancherTaskCapabilityHelper> mockRancher = mockStatic(RancherTaskCapabilityHelper.class)) {
      mockK8s.when(() -> K8sTaskCapabilityHelper.fetchRequiredExecutionCapabilities(any(), any(), anyBoolean()))
          .thenReturn(emptyList());
      mockRancher.when(() -> RancherTaskCapabilityHelper.fetchRequiredExecutionCapabilities(any(), any()))
          .thenReturn(emptyList());
      doReturn(Optional.empty()).when(cdStepHelper).getServiceHooksOutcome(ambiance);
      HelmSpecParameters stepParams = HelmDeployStepParams.infoBuilder().build();
      ServiceHookDelegateConfig hook = mock(ServiceHookDelegateConfig.class);
      TaskChainResponse taskChainResponse =
          nativeHelmStepHelper.queueNativeHelmTask(StepElementParameters.builder().spec(stepParams).build(),
              HelmInstallCommandRequestNG.builder()
                  .commandName("Rolling Deploy")
                  .k8sInfraDelegateConfig(k8sInfraDelegateConfig)
                  .serviceHooks(List.of(hook))
                  .build(),
              ambiance, NativeHelmExecutionPassThroughData.builder().build());
      assertThat(taskChainResponse.getTaskRequest()
                     .getDelegateTaskRequest()
                     .getRequest()
                     .getDetails()
                     .getType()
                     .getType()
                     .trim())
          .isEqualTo(expectedTaskType.name());
    }
  }
}
