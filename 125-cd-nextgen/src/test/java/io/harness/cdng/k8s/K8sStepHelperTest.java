/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.k8s;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.beans.EnvironmentType.NON_PROD;
import static io.harness.beans.EnvironmentType.PROD;
import static io.harness.cdng.k8s.K8sStepHelper.MISSING_INFRASTRUCTURE_ERROR;
import static io.harness.cdng.k8s.K8sStepHelper.RELEASE_NAME;
import static io.harness.cdng.manifest.ManifestType.K8S_SUPPORTED_MANIFEST_TYPES;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.delegate.beans.connector.ConnectorType.AWS;
import static io.harness.delegate.beans.connector.ConnectorType.GCP;
import static io.harness.delegate.beans.connector.ConnectorType.GITHUB;
import static io.harness.delegate.beans.connector.ConnectorType.HTTP_HELM_REPO;
import static io.harness.delegate.beans.connector.ConnectorType.KUBERNETES_CLUSTER;
import static io.harness.logging.CommandExecutionStatus.FAILURE;
import static io.harness.logging.CommandExecutionStatus.SUCCESS;
import static io.harness.rule.OwnerRule.ABOSII;
import static io.harness.rule.OwnerRule.ACASIAN;
import static io.harness.rule.OwnerRule.ACHYUTH;
import static io.harness.rule.OwnerRule.ANSHUL;
import static io.harness.rule.OwnerRule.NAMAN_TALAYCHA;
import static io.harness.rule.OwnerRule.PRATYUSH;
import static io.harness.rule.OwnerRule.VAIBHAV_SI;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyBoolean;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.FeatureName;
import io.harness.category.element.UnitTests;
import io.harness.cdng.CDStepHelper;
import io.harness.cdng.common.beans.SetupAbstractionKeys;
import io.harness.cdng.featureFlag.CDFeatureFlagHelper;
import io.harness.cdng.helm.HelmDeployStepParams;
import io.harness.cdng.infra.beans.K8sDirectInfrastructureOutcome;
import io.harness.cdng.infra.beans.K8sDirectInfrastructureOutcome.K8sDirectInfrastructureOutcomeBuilder;
import io.harness.cdng.k8s.beans.HelmValuesFetchResponsePassThroughData;
import io.harness.cdng.k8s.beans.K8sExecutionPassThroughData;
import io.harness.cdng.k8s.beans.StepExceptionPassThroughData;
import io.harness.cdng.manifest.steps.ManifestsOutcome;
import io.harness.cdng.manifest.yaml.GcsStoreConfig;
import io.harness.cdng.manifest.yaml.GitStore;
import io.harness.cdng.manifest.yaml.GithubStore;
import io.harness.cdng.manifest.yaml.HelmChartManifestOutcome;
import io.harness.cdng.manifest.yaml.HelmCommandFlagType;
import io.harness.cdng.manifest.yaml.HelmManifestCommandFlag;
import io.harness.cdng.manifest.yaml.HttpStoreConfig;
import io.harness.cdng.manifest.yaml.InheritFromManifestStoreConfig;
import io.harness.cdng.manifest.yaml.InlineStoreConfig;
import io.harness.cdng.manifest.yaml.K8sManifestOutcome;
import io.harness.cdng.manifest.yaml.KustomizeManifestOutcome;
import io.harness.cdng.manifest.yaml.KustomizePatchesManifestOutcome;
import io.harness.cdng.manifest.yaml.ManifestConfig;
import io.harness.cdng.manifest.yaml.ManifestConfigWrapper;
import io.harness.cdng.manifest.yaml.ManifestOutcome;
import io.harness.cdng.manifest.yaml.OpenshiftManifestOutcome;
import io.harness.cdng.manifest.yaml.OpenshiftParamManifestOutcome;
import io.harness.cdng.manifest.yaml.S3StoreConfig;
import io.harness.cdng.manifest.yaml.ValuesManifestOutcome;
import io.harness.cdng.manifest.yaml.kinds.ValuesManifest;
import io.harness.cdng.manifest.yaml.storeConfig.StoreConfig;
import io.harness.cdng.manifest.yaml.storeConfig.StoreConfigWrapper;
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
import io.harness.delegate.beans.connector.k8Connector.KubernetesAuthDTO;
import io.harness.delegate.beans.connector.k8Connector.KubernetesClusterConfigDTO;
import io.harness.delegate.beans.connector.k8Connector.KubernetesClusterDetailsDTO;
import io.harness.delegate.beans.connector.k8Connector.KubernetesCredentialDTO;
import io.harness.delegate.beans.connector.k8Connector.KubernetesCredentialType;
import io.harness.delegate.beans.connector.scm.GitAuthType;
import io.harness.delegate.beans.connector.scm.genericgitconnector.GitConfigDTO;
import io.harness.delegate.beans.connector.scm.github.GithubConnectorDTO;
import io.harness.delegate.beans.executioncapability.GitConnectionNGCapability;
import io.harness.delegate.beans.logstreaming.UnitProgressData;
import io.harness.delegate.beans.storeconfig.FetchType;
import io.harness.delegate.beans.storeconfig.GcsHelmStoreDelegateConfig;
import io.harness.delegate.beans.storeconfig.GitStoreDelegateConfig;
import io.harness.delegate.beans.storeconfig.HttpHelmStoreDelegateConfig;
import io.harness.delegate.beans.storeconfig.S3HelmStoreDelegateConfig;
import io.harness.delegate.exception.TaskNGDataException;
import io.harness.delegate.task.TaskParameters;
import io.harness.delegate.task.git.GitFetchFilesConfig;
import io.harness.delegate.task.git.GitFetchRequest;
import io.harness.delegate.task.git.GitFetchResponse;
import io.harness.delegate.task.git.TaskStatus;
import io.harness.delegate.task.helm.HelmFetchFileConfig;
import io.harness.delegate.task.helm.HelmFetchFileResult;
import io.harness.delegate.task.helm.HelmValuesFetchRequest;
import io.harness.delegate.task.helm.HelmValuesFetchResponse;
import io.harness.delegate.task.k8s.DirectK8sInfraDelegateConfig;
import io.harness.delegate.task.k8s.HelmChartManifestDelegateConfig;
import io.harness.delegate.task.k8s.K8sManifestDelegateConfig;
import io.harness.delegate.task.k8s.K8sRollingDeployRequest;
import io.harness.delegate.task.k8s.KustomizeManifestDelegateConfig;
import io.harness.delegate.task.k8s.ManifestDelegateConfig;
import io.harness.delegate.task.k8s.ManifestType;
import io.harness.delegate.task.k8s.OpenshiftManifestDelegateConfig;
import io.harness.eventsframework.schemas.entity.EntityDetailProtoDTO;
import io.harness.exception.ExceptionUtils;
import io.harness.exception.GeneralException;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.KubernetesTaskException;
import io.harness.git.model.FetchFilesResult;
import io.harness.git.model.GitFile;
import io.harness.helm.HelmSubCommandType;
import io.harness.k8s.model.HelmVersion;
import io.harness.logging.LogCallback;
import io.harness.logging.LogLevel;
import io.harness.logging.UnitProgress;
import io.harness.logging.UnitStatus;
import io.harness.plancreator.steps.common.StepElementParameters;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.Status;
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
import io.harness.pms.sdk.core.resolver.RefObjectUtils;
import io.harness.pms.sdk.core.resolver.outcome.OutcomeService;
import io.harness.pms.sdk.core.steps.executables.TaskChainResponse;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.pms.yaml.ParameterField;
import io.harness.rule.Owner;
import io.harness.secretmanagerclient.services.api.SecretManagerClientService;
import io.harness.serializer.KryoSerializer;
import io.harness.steps.EntityReferenceExtractorUtils;
import io.harness.steps.StepHelper;
import io.harness.supplier.ThrowingSupplier;
import io.harness.tasks.ResponseData;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

@OwnedBy(CDP)
public class K8sStepHelperTest extends CategoryTest {
  @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();

  @Mock private ConnectorService connectorService;
  @Mock private EngineExpressionService engineExpressionService;
  @Mock private OutcomeService outcomeService;
  @Mock private K8sStepExecutor k8sStepExecutor;
  @Mock private KryoSerializer kryoSerializer;
  @Mock private StepHelper stepHelper;
  @Mock private EntityReferenceExtractorUtils entityReferenceExtractorUtils;
  @Mock private PipelineRbacHelper pipelineRbacHelper;
  @Mock private SdkGraphVisualizationDataService sdkGraphVisualizationDataService;
  @Mock private StoreConfig storeConfig;
  @Mock private SecretManagerClientService secretManagerClientService;
  @Mock private CDFeatureFlagHelper cdFeatureFlagHelper;

  // used internally -- don't remove
  @Mock private ConnectorInfoDTO connectorInfoDTO;
  @Mock private K8sRollingStep k8sRollingStep;
  @Mock private GitConfigAuthenticationInfoHelper gitConfigAuthenticationInfoHelper;
  @Spy @InjectMocks private K8sEntityHelper k8sEntityHelper;

  @Spy @InjectMocks private K8sStepHelper k8sStepHelper;

  @Mock private LogCallback mockLogCallback;
  private final Ambiance ambiance = Ambiance.newBuilder()
                                        .putSetupAbstractions(SetupAbstractionKeys.accountId, "test-account")
                                        .putSetupAbstractions(SetupAbstractionKeys.orgIdentifier, "test-org")
                                        .putSetupAbstractions(SetupAbstractionKeys.projectIdentifier, "test-project")
                                        .build();
  private static final String SOME_URL = "https://url.com/owner/repo.git";

  @Before
  public void setup() {
    doReturn(mockLogCallback).when(k8sStepHelper).getLogCallback(anyString(), eq(ambiance), anyBoolean());
    doReturn(true)
        .when(cdFeatureFlagHelper)
        .isEnabled(AmbianceUtils.getAccountId(ambiance), FeatureName.USE_LATEST_CHARTMUSEUM_VERSION);
    doAnswer(invocation -> invocation.getArgument(1, String.class))
        .when(engineExpressionService)
        .renderExpression(eq(ambiance), anyString());
  }

  public Ambiance getAmbiance() {
    Map<String, String> setupAbstractions = new HashMap<>();

    setupAbstractions.put(SetupAbstractionKeys.accountId, "account1");
    setupAbstractions.put(SetupAbstractionKeys.orgIdentifier, "org1");
    setupAbstractions.put(SetupAbstractionKeys.projectIdentifier, "project1");

    return Ambiance.newBuilder().putAllSetupAbstractions(setupAbstractions).build();
  }

  @Test
  @Owner(developers = VAIBHAV_SI)
  @Category(UnitTests.class)
  public void testGetK8sManifestsOutcome() {
    assertThatThrownBy(() -> k8sStepHelper.getK8sSupportedManifestOutcome(Collections.emptyList()))
        .hasMessageContaining(
            "Manifests are mandatory for K8s step. Select one from " + String.join(", ", K8S_SUPPORTED_MANIFEST_TYPES));

    K8sManifestOutcome k8sManifestOutcome = K8sManifestOutcome.builder().build();
    ValuesManifestOutcome valuesManifestOutcome = ValuesManifestOutcome.builder().build();
    List<ManifestOutcome> serviceManifestOutcomes = new ArrayList<>();
    serviceManifestOutcomes.add(k8sManifestOutcome);
    serviceManifestOutcomes.add(valuesManifestOutcome);

    ManifestOutcome actualK8sManifest = k8sStepHelper.getK8sSupportedManifestOutcome(serviceManifestOutcomes);
    assertThat(actualK8sManifest).isEqualTo(k8sManifestOutcome);

    K8sManifestOutcome anotherK8sManifest = K8sManifestOutcome.builder().build();
    serviceManifestOutcomes.add(anotherK8sManifest);

    assertThatThrownBy(() -> k8sStepHelper.getK8sSupportedManifestOutcome(serviceManifestOutcomes))
        .hasMessageContaining(
            "There can be only a single manifest. Select one from " + String.join(", ", K8S_SUPPORTED_MANIFEST_TYPES));
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testGetHelmChartManifestsOutcome() {
    HelmChartManifestOutcome helmChartManifestOutcome =
        HelmChartManifestOutcome.builder()
            .helmVersion(HelmVersion.V3)
            .skipResourceVersioning(ParameterField.createValueField(true))
            .build();
    ValuesManifestOutcome valuesManifestOutcome = ValuesManifestOutcome.builder().build();
    List<ManifestOutcome> manifestOutcomes = new ArrayList<>();
    manifestOutcomes.add(helmChartManifestOutcome);
    manifestOutcomes.add(valuesManifestOutcome);

    assertThat(k8sStepHelper.getK8sSupportedManifestOutcome(manifestOutcomes)).isEqualTo(helmChartManifestOutcome);
  }

  @Test
  @Owner(developers = VAIBHAV_SI)
  @Category(UnitTests.class)
  public void testGetAggregatedValuesManifests() {
    K8sManifestOutcome k8sManifestOutcome = K8sManifestOutcome.builder().build();
    ValuesManifestOutcome valuesManifestOutcome = ValuesManifestOutcome.builder().build();
    List<ManifestOutcome> serviceManifestOutcomes = new ArrayList<>();
    serviceManifestOutcomes.add(k8sManifestOutcome);
    serviceManifestOutcomes.add(valuesManifestOutcome);

    List<ValuesManifestOutcome> aggregatedValuesManifests =
        k8sStepHelper.getAggregatedValuesManifests(serviceManifestOutcomes);
    assertThat(aggregatedValuesManifests).hasSize(1);
    assertThat(aggregatedValuesManifests.get(0)).isEqualTo(valuesManifestOutcome);
  }

  @Test
  @Owner(developers = ACASIAN)
  @Category(UnitTests.class)
  public void testGetOpenshiftParamManifests() {
    OpenshiftManifestOutcome openshiftManifestOutcome = OpenshiftManifestOutcome.builder().build();
    OpenshiftParamManifestOutcome openshiftParamManifestOutcome = OpenshiftParamManifestOutcome.builder().build();
    List<ManifestOutcome> serviceManifestOutcomes = new ArrayList<>();
    serviceManifestOutcomes.add(openshiftManifestOutcome);
    serviceManifestOutcomes.add(openshiftParamManifestOutcome);

    List<OpenshiftParamManifestOutcome> openshiftParamManifests =
        k8sStepHelper.getOpenshiftParamManifests(serviceManifestOutcomes);
    assertThat(openshiftParamManifests).hasSize(1);
    assertThat(openshiftParamManifests.get(0)).isEqualTo(openshiftParamManifestOutcome);
  }

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

    doReturn(
        Optional.of(ConnectorResponseDTO.builder()
                        .connector(ConnectorInfoDTO.builder().connectorConfig(GitConfigDTO.builder().build()).build())
                        .build()))
        .when(connectorService)
        .get(nullable(String.class), nullable(String.class), nullable(String.class), nullable(String.class));

    ManifestDelegateConfig delegateConfig = k8sStepHelper.getManifestDelegateConfig(manifestOutcome, ambiance);
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
        .get(nullable(String.class), nullable(String.class), nullable(String.class), nullable(String.class));

    ManifestDelegateConfig delegateConfig = k8sStepHelper.getManifestDelegateConfig(manifestOutcome, ambiance);
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
  @Owner(developers = ACASIAN)
  @Category(UnitTests.class)
  public void testShouldReturnSkipResourceVersioning() {
    boolean result = k8sStepHelper.getSkipResourceVersioning(
        K8sManifestOutcome.builder().skipResourceVersioning(ParameterField.createValueField(true)).build());
    assertThat(result).isTrue();
    result = k8sStepHelper.getSkipResourceVersioning(
        K8sManifestOutcome.builder().skipResourceVersioning(ParameterField.createValueField(false)).build());
    assertThat(result).isFalse();
    result = k8sStepHelper.getSkipResourceVersioning(
        HelmChartManifestOutcome.builder().skipResourceVersioning(ParameterField.createValueField(true)).build());
    assertThat(result).isTrue();
    result = k8sStepHelper.getSkipResourceVersioning(
        HelmChartManifestOutcome.builder().skipResourceVersioning(ParameterField.createValueField(false)).build());
    assertThat(result).isFalse();
    result = k8sStepHelper.getSkipResourceVersioning(
        KustomizeManifestOutcome.builder().skipResourceVersioning(ParameterField.createValueField(true)).build());
    assertThat(result).isTrue();
    result = k8sStepHelper.getSkipResourceVersioning(
        KustomizeManifestOutcome.builder().skipResourceVersioning(ParameterField.createValueField(false)).build());
    assertThat(result).isFalse();
    result = k8sStepHelper.getSkipResourceVersioning(
        OpenshiftManifestOutcome.builder().skipResourceVersioning(ParameterField.createValueField(true)).build());
    assertThat(result).isTrue();
    result = k8sStepHelper.getSkipResourceVersioning(
        OpenshiftManifestOutcome.builder().skipResourceVersioning(ParameterField.createValueField(false)).build());
    assertThat(result).isFalse();

    result = k8sStepHelper.getSkipResourceVersioning(ValuesManifestOutcome.builder().build());
    assertThat(result).isFalse();
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

    doReturn(
        Optional.of(ConnectorResponseDTO.builder()
                        .connector(ConnectorInfoDTO.builder().connectorConfig(GitConfigDTO.builder().build()).build())
                        .build()))
        .when(connectorService)
        .get(nullable(String.class), nullable(String.class), nullable(String.class), nullable(String.class));

    ManifestDelegateConfig delegateConfig = k8sStepHelper.getManifestDelegateConfig(manifestOutcome, ambiance);
    assertThat(delegateConfig.getManifestType()).isEqualTo(ManifestType.KUSTOMIZE);
    assertThat(delegateConfig).isInstanceOf(KustomizeManifestDelegateConfig.class);
    assertThat(delegateConfig.getStoreDelegateConfig()).isNotNull();
    assertThat(delegateConfig.getStoreDelegateConfig()).isInstanceOf(GitStoreDelegateConfig.class);
    KustomizeManifestDelegateConfig kustomizeManifestDelegateConfig = (KustomizeManifestDelegateConfig) delegateConfig;
    assertThat(kustomizeManifestDelegateConfig.getPluginPath()).isEqualTo("/usr/bin/kustomize");
    assertThat(kustomizeManifestDelegateConfig.getKustomizeDirPath()).isEqualTo("kustomize-dir");
    assertThat(kustomizeManifestDelegateConfig.getStoreDelegateConfig()).isInstanceOf(GitStoreDelegateConfig.class);
    GitStoreDelegateConfig gitStoreDelegateConfig =
        (GitStoreDelegateConfig) kustomizeManifestDelegateConfig.getStoreDelegateConfig();
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
                                            .connectorType(HTTP_HELM_REPO)
                                            .connectorConfig(httpHelmConnectorConfig)
                                            .build())
                             .build()))
        .when(connectorService)
        .get(nullable(String.class), nullable(String.class), nullable(String.class), nullable(String.class));

    ManifestDelegateConfig delegateConfig = k8sStepHelper.getManifestDelegateConfig(manifestOutcome, ambiance);
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

    doReturn(
        Optional.of(ConnectorResponseDTO.builder()
                        .connector(ConnectorInfoDTO.builder().connectorConfig(GitConfigDTO.builder().build()).build())
                        .build()))
        .when(connectorService)
        .get(nullable(String.class), nullable(String.class), nullable(String.class), nullable(String.class));

    ManifestDelegateConfig delegateConfig = k8sStepHelper.getManifestDelegateConfig(manifestOutcome, ambiance);
    assertThat(delegateConfig.getManifestType()).isEqualTo(ManifestType.OPENSHIFT_TEMPLATE);
    assertThat(delegateConfig).isInstanceOf(OpenshiftManifestDelegateConfig.class);
    assertThat(delegateConfig.getStoreDelegateConfig()).isNotNull();
    assertThat(delegateConfig.getStoreDelegateConfig()).isInstanceOf(GitStoreDelegateConfig.class);
  }

  @Test
  @Owner(developers = ACASIAN)
  @Category(UnitTests.class)
  public void shouldRenderReversedValuesFilesForOpenshiftManifest() {
    String valueFile1 = "file1";
    String valueFile2 = "file2";
    List<String> valuesFiles = asList(valueFile1, valueFile2);

    doReturn(valueFile1).when(engineExpressionService).renderExpression(any(), eq(valueFile1), anyBoolean());
    doReturn(valueFile2).when(engineExpressionService).renderExpression(any(), eq(valueFile2), anyBoolean());

    List<String> renderedValuesFiles = k8sStepHelper.renderValues(
        OpenshiftManifestOutcome.builder().build(), Ambiance.newBuilder().build(), valuesFiles);
    assertThat(renderedValuesFiles).isNotEmpty();
    assertThat(renderedValuesFiles).containsExactly(valueFile2, valueFile1);
  }

  @Test
  @Owner(developers = NAMAN_TALAYCHA)
  @Category(UnitTests.class)
  public void shouldRenderPatchesFilesForKustomizeManifest() {
    String valueFile1 = "file1";
    String valueFile2 = "file2";
    List<String> valuesFiles = asList(valueFile1, valueFile2);

    doReturn(true).when(cdFeatureFlagHelper).isEnabled(any(), any());
    doReturn(valueFile1).when(engineExpressionService).renderExpression(any(), eq(valueFile1));
    doReturn(valueFile2).when(engineExpressionService).renderExpression(any(), eq(valueFile2));

    List<String> renderedValuesFiles = k8sStepHelper.renderPatches(
        KustomizeManifestOutcome.builder().build(), Ambiance.newBuilder().build(), valuesFiles);
    verify(k8sStepHelper, times(1)).renderPatches(any(), any(), any());
    assertThat(renderedValuesFiles).isNotEmpty();
  }

  @Test
  @Owner(developers = ANSHUL)
  @Category(UnitTests.class)
  public void testGetInfrastructureOutcome() {
    K8sDirectInfrastructureOutcome outcome = K8sDirectInfrastructureOutcome.builder().build();
    doReturn(OptionalOutcome.builder().outcome(outcome).found(true).build())
        .when(outcomeService)
        .resolveOptional(
            ambiance, RefObjectUtils.getOutcomeRefObject(OutcomeExpressionConstants.INFRASTRUCTURE_OUTCOME));
    assertThat(k8sStepHelper.getInfrastructureOutcome(ambiance)).isEqualTo(outcome);

    doReturn(OptionalOutcome.builder().found(false).build())
        .when(outcomeService)
        .resolveOptional(
            ambiance, RefObjectUtils.getOutcomeRefObject(OutcomeExpressionConstants.INFRASTRUCTURE_OUTCOME));
    assertThatThrownBy(() -> k8sStepHelper.getInfrastructureOutcome(ambiance))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining(MISSING_INFRASTRUCTURE_ERROR);
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

    doReturn(
        Optional.of(
            ConnectorResponseDTO.builder()
                .connector(ConnectorInfoDTO.builder().connectorType(AWS).connectorConfig(awsConnectorConfig).build())
                .build()))
        .when(connectorService)
        .get(nullable(String.class), nullable(String.class), nullable(String.class), nullable(String.class));

    ManifestDelegateConfig delegateConfig = k8sStepHelper.getManifestDelegateConfig(manifestOutcome, ambiance);
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

    doReturn(Optional.of(
                 ConnectorResponseDTO.builder()
                     .connector(ConnectorInfoDTO.builder().connectorType(GCP).connectorConfig(gcpConnectorDTO).build())
                     .build()))
        .when(connectorService)
        .get(nullable(String.class), nullable(String.class), nullable(String.class), nullable(String.class));

    ManifestDelegateConfig delegateConfig = k8sStepHelper.getManifestDelegateConfig(helmChartManifestOutcome, ambiance);
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
  public void testShouldPrepareK8sGitValuesFetchTask() {
    K8sDirectInfrastructureOutcome k8sDirectInfrastructureOutcome =
        K8sDirectInfrastructureOutcome.builder().namespace("default").build();
    GitStore gitStore = GitStore.builder()
                            .branch(ParameterField.createValueField("master"))
                            .paths(ParameterField.createValueField(asList("path/to/k8s/manifest/")))
                            .connectorRef(ParameterField.createValueField("git-connector"))
                            .build();
    K8sManifestOutcome k8sManifestOutcome = K8sManifestOutcome.builder().identifier("k8s").store(gitStore).build();
    Map<String, ManifestOutcome> manifestOutcomeMap = ImmutableMap.of("k8s", k8sManifestOutcome);
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

    StepElementParameters rollingStepElementParams =
        StepElementParameters.builder().spec(K8sRollingStepParameters.infoBuilder().build()).build();
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
        .get(nullable(String.class), nullable(String.class), nullable(String.class), nullable(String.class));

    TaskChainResponse taskChainResponse =
        k8sStepHelper.startChainLink(k8sStepExecutor, ambiance, rollingStepElementParams);
    assertThat(taskChainResponse).isNotNull();
    assertThat(taskChainResponse.getPassThroughData()).isNotNull();
    assertThat(taskChainResponse.getPassThroughData()).isInstanceOf(K8sStepPassThroughData.class);
    K8sStepPassThroughData k8sStepPassThroughData = (K8sStepPassThroughData) taskChainResponse.getPassThroughData();
    assertThat(k8sStepPassThroughData.getValuesManifestOutcomes()).isNotEmpty();
    assertThat(k8sStepPassThroughData.getValuesManifestOutcomes().size()).isEqualTo(1);
    ValuesManifestOutcome valuesManifestOutcome = k8sStepPassThroughData.getValuesManifestOutcomes().get(0);
    assertThat(valuesManifestOutcome.getIdentifier()).isEqualTo(k8sManifestOutcome.getIdentifier());
    assertThat(valuesManifestOutcome.getStore()).isEqualTo(k8sManifestOutcome.getStore());
    ArgumentCaptor<Object> argumentCaptor = ArgumentCaptor.forClass(Object.class);
    verify(kryoSerializer, times(2)).asDeflatedBytes(argumentCaptor.capture());
    TaskParameters taskParameters = (TaskParameters) argumentCaptor.getAllValues().get(0);
    assertThat(taskParameters).isInstanceOf(GitFetchRequest.class);
    GitFetchRequest gitFetchRequest = (GitFetchRequest) taskParameters;
    assertThat(gitFetchRequest.getGitFetchFilesConfigs()).isNotEmpty();
    assertThat(gitFetchRequest.getGitFetchFilesConfigs().size()).isEqualTo(1);
    GitFetchFilesConfig gitFetchFilesConfig = gitFetchRequest.getGitFetchFilesConfigs().get(0);
    assertThat(gitFetchFilesConfig.getGitStoreDelegateConfig().getPaths()).isNotEmpty();
    assertThat(gitFetchFilesConfig.getGitStoreDelegateConfig().getPaths().size()).isEqualTo(1);
    assertThat(gitFetchFilesConfig.getGitStoreDelegateConfig().getPaths().get(0))
        .isEqualTo("path/to/k8s/manifest/values.yaml");
    assertThat(argumentCaptor.getAllValues().get(1)).isInstanceOf(GitConnectionNGCapability.class);
  }

  @Test
  @Owner(developers = ACHYUTH)
  @Category(UnitTests.class)
  public void testStepLevelOverrides() throws Exception {
    GitStore gitStoreStepLevel =
        GitStore.builder()
            .branch(ParameterField.createValueField("master"))
            .paths(ParameterField.createValueField(asList("path/to/k8s/manifest/step-values.yaml")))
            .connectorRef(ParameterField.createValueField("git-connector"))
            .build();

    InlineStoreConfig inlineStore =
        InlineStoreConfig.builder().content(ParameterField.createValueField("replicaCount: 3")).build();

    K8sApplyStepParameters applyStepParams = new K8sApplyStepParameters();
    applyStepParams.setSkipDryRun(ParameterField.ofNull());
    applyStepParams.setSkipSteadyStateCheck(ParameterField.ofNull());
    applyStepParams.setFilePaths(ParameterField.createValueField(Arrays.asList("file1.yaml", "file2.yaml")));
    applyStepParams.setOverrides(
        Arrays.asList(ManifestConfigWrapper.builder()
                          .manifest(ManifestConfig.builder()
                                        .spec(ValuesManifest.builder()
                                                  .store(ParameterField.createValueField(
                                                      StoreConfigWrapper.builder().spec(gitStoreStepLevel).build()))
                                                  .build())
                                        .build())
                          .build(),
            ManifestConfigWrapper.builder()
                .manifest(ManifestConfig.builder()
                              .spec(ValuesManifest.builder()
                                        .store(ParameterField.createValueField(
                                            StoreConfigWrapper.builder().spec(inlineStore).build()))
                                        .build())
                              .build())
                .build()));

    StepElementParameters stepElementParametersApplyStep =
        StepElementParameters.builder().spec(applyStepParams).build();

    K8sDirectInfrastructureOutcome k8sDirectInfrastructureOutcome =
        K8sDirectInfrastructureOutcome.builder().namespace("default").build();

    GitStore gitStore = GitStore.builder()
                            .branch(ParameterField.createValueField("master"))
                            .paths(ParameterField.createValueField(asList("path/to/k8s/manifest")))
                            .connectorRef(ParameterField.createValueField("git-connector"))
                            .build();

    K8sManifestOutcome k8sManifestOutcome = K8sManifestOutcome.builder().identifier("k8s").store(gitStore).build();

    Map<String, ManifestOutcome> manifestOutcomeMap = ImmutableMap.of("k8s", k8sManifestOutcome);

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
        .get(anyString(), anyString(), anyString(), anyString());

    TaskChainResponse applyTaskChainResponse =
        k8sStepHelper.startChainLink(k8sStepExecutor, ambiance, stepElementParametersApplyStep);
    assertThat(applyTaskChainResponse.getPassThroughData()).isNotNull();

    K8sStepPassThroughData passThroughData = (K8sStepPassThroughData) applyTaskChainResponse.getPassThroughData();
    assertThat(passThroughData.getValuesManifestOutcomes().get(1))
        .extracting("store")
        .extracting("paths")
        .extracting("value")
        .isEqualTo(Arrays.asList("path/to/k8s/manifest/step-values.yaml"));
    assertThat(passThroughData.getValuesManifestOutcomes().get(2))
        .extracting("store")
        .extracting("content")
        .extracting("value")
        .isEqualTo("replicaCount: 3");
  }

  @Test
  @Owner(developers = ACASIAN)
  @Category(UnitTests.class)
  public void testShouldPrepareK8sGitValuesFetchTaskWithValuesOverride() {
    K8sDirectInfrastructureOutcome k8sDirectInfrastructureOutcome =
        K8sDirectInfrastructureOutcome.builder().namespace("default").build();
    GitStore gitStore = GitStore.builder()
                            .branch(ParameterField.createValueField("master"))
                            .paths(ParameterField.createValueField(asList("path/to/k8s/manifest/")))
                            .connectorRef(ParameterField.createValueField("git-connector"))
                            .build();
    K8sManifestOutcome k8sManifestOutcome =
        K8sManifestOutcome.builder()
            .identifier("k8s")
            .store(gitStore)
            .valuesPaths(ParameterField.createValueField(asList("path/to/k8s/manifest/values2.yaml")))
            .build();
    GitStore gitStore2 = GitStore.builder()
                             .branch(ParameterField.createValueField("master"))
                             .paths(ParameterField.createValueField(asList("path/to/k8s/manifest/values3.yaml")))
                             .connectorRef(ParameterField.createValueField("git-connector"))
                             .build();
    InheritFromManifestStoreConfig inheritFromManifestStore =
        InheritFromManifestStoreConfig.builder()
            .paths(ParameterField.createValueField(asList("path/to/k8s/manifest/values4.yaml")))
            .build();
    ValuesManifestOutcome valuesManifestOutcome1 =
        ValuesManifestOutcome.builder().identifier("override1").store(gitStore2).build();
    ValuesManifestOutcome valuesManifestOutcome2 =
        ValuesManifestOutcome.builder().identifier("override2").store(inheritFromManifestStore).build();
    Map<String, ManifestOutcome> manifestOutcomeMap = ImmutableMap.of(
        "k8s", k8sManifestOutcome, "override1", valuesManifestOutcome1, "override2", valuesManifestOutcome2);
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

    StepElementParameters rollingStepElementParams =
        StepElementParameters.builder().spec(K8sRollingStepParameters.infoBuilder().build()).build();
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
        .get(anyString(), anyString(), anyString(), anyString());

    TaskChainResponse taskChainResponse =
        k8sStepHelper.startChainLink(k8sStepExecutor, ambiance, rollingStepElementParams);
    assertThat(taskChainResponse).isNotNull();
    assertThat(taskChainResponse.getPassThroughData()).isNotNull();
    assertThat(taskChainResponse.getPassThroughData()).isInstanceOf(K8sStepPassThroughData.class);
    K8sStepPassThroughData k8sStepPassThroughData = (K8sStepPassThroughData) taskChainResponse.getPassThroughData();
    assertThat(k8sStepPassThroughData.getValuesManifestOutcomes()).isNotEmpty();
    assertThat(k8sStepPassThroughData.getValuesManifestOutcomes().size()).isEqualTo(3);
    List<ValuesManifestOutcome> valuesManifestOutcomes = k8sStepPassThroughData.getValuesManifestOutcomes();
    assertThat(valuesManifestOutcomes.get(0).getIdentifier()).isEqualTo(k8sManifestOutcome.getIdentifier());
    assertThat(valuesManifestOutcomes.get(0).getStore()).isEqualTo(k8sManifestOutcome.getStore());
    assertThat(valuesManifestOutcomes.get(1).getIdentifier()).isEqualTo(valuesManifestOutcome1.getIdentifier());
    assertThat(valuesManifestOutcomes.get(1).getStore()).isEqualTo(valuesManifestOutcome1.getStore());
    assertThat(valuesManifestOutcomes.get(2).getIdentifier()).isEqualTo(valuesManifestOutcome2.getIdentifier());
    assertThat(valuesManifestOutcomes.get(2).getStore()).isEqualTo(valuesManifestOutcome2.getStore());
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
        .isEqualTo("path/to/k8s/manifest/values.yaml");
    assertThat(gitFetchFilesConfigs.get(0).getGitStoreDelegateConfig().getPaths()).isNotEmpty();
    assertThat(gitFetchFilesConfigs.get(0).getGitStoreDelegateConfig().getPaths().size()).isEqualTo(1);
    assertThat(gitFetchFilesConfigs.get(0).getGitStoreDelegateConfig().getPaths().get(0))
        .isEqualTo("path/to/k8s/manifest/values3.yaml");
    assertThat(gitFetchFilesConfigs.get(2).getGitStoreDelegateConfig().getPaths()).isNotEmpty();
    assertThat(gitFetchFilesConfigs.get(2).getGitStoreDelegateConfig().getPaths().size()).isEqualTo(1);
    assertThat(gitFetchFilesConfigs.get(2).getGitStoreDelegateConfig().getPaths().get(0))
        .isEqualTo("path/to/k8s/manifest/values2.yaml");
    assertThat(gitFetchFilesConfigs.get(3).getGitStoreDelegateConfig().getPaths()).isNotEmpty();
    assertThat(gitFetchFilesConfigs.get(3).getGitStoreDelegateConfig().getPaths().size()).isEqualTo(1);
    assertThat(gitFetchFilesConfigs.get(3).getGitStoreDelegateConfig().getPaths().get(0))
        .isEqualTo("path/to/k8s/manifest/values4.yaml");
    assertThat(argumentCaptor.getAllValues().get(1)).isInstanceOf(GitConnectionNGCapability.class);
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
            .valuesPaths(ParameterField.createValueField(asList("path/to/helm/chart/values2.yaml")))
            .build();
    GitStore gitStore2 = GitStore.builder()
                             .branch(ParameterField.createValueField("master"))
                             .paths(ParameterField.createValueField(asList("path/to/helm/chart/values3.yaml")))
                             .connectorRef(ParameterField.createValueField("git-connector"))
                             .build();
    InheritFromManifestStoreConfig inheritFromManifestStore =
        InheritFromManifestStoreConfig.builder()
            .paths(ParameterField.createValueField(asList("path/to/helm/chart/values4.yaml")))
            .build();
    ValuesManifestOutcome valuesManifestOutcome1 =
        ValuesManifestOutcome.builder().identifier("override1").store(gitStore2).build();
    ValuesManifestOutcome valuesManifestOutcome2 =
        ValuesManifestOutcome.builder().identifier("override2").store(inheritFromManifestStore).build();
    Map<String, ManifestOutcome> manifestOutcomeMap = ImmutableMap.of(
        "helm", helmChartManifestOutcome, "override1", valuesManifestOutcome1, "override2", valuesManifestOutcome2);
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

    StepElementParameters rollingStepElementParams =
        StepElementParameters.builder().spec(K8sRollingStepParameters.infoBuilder().build()).build();
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
        .get(nullable(String.class), nullable(String.class), nullable(String.class), nullable(String.class));

    TaskChainResponse taskChainResponse =
        k8sStepHelper.startChainLink(k8sStepExecutor, ambiance, rollingStepElementParams);
    assertThat(taskChainResponse).isNotNull();
    assertThat(taskChainResponse.getPassThroughData()).isNotNull();
    assertThat(taskChainResponse.getPassThroughData()).isInstanceOf(K8sStepPassThroughData.class);
    K8sStepPassThroughData k8sStepPassThroughData = (K8sStepPassThroughData) taskChainResponse.getPassThroughData();
    assertThat(k8sStepPassThroughData.getValuesManifestOutcomes()).isNotEmpty();
    assertThat(k8sStepPassThroughData.getValuesManifestOutcomes().size()).isEqualTo(3);
    List<ValuesManifestOutcome> valuesManifestOutcomes = k8sStepPassThroughData.getValuesManifestOutcomes();
    assertThat(valuesManifestOutcomes.get(0).getIdentifier()).isEqualTo(helmChartManifestOutcome.getIdentifier());
    assertThat(valuesManifestOutcomes.get(0).getStore()).isEqualTo(helmChartManifestOutcome.getStore());
    assertThat(valuesManifestOutcomes.get(1).getIdentifier()).isEqualTo(valuesManifestOutcome1.getIdentifier());
    assertThat(valuesManifestOutcomes.get(1).getStore()).isEqualTo(valuesManifestOutcome1.getStore());
    assertThat(valuesManifestOutcomes.get(2).getIdentifier()).isEqualTo(valuesManifestOutcome2.getIdentifier());
    assertThat(valuesManifestOutcomes.get(2).getStore()).isEqualTo(valuesManifestOutcome2.getStore());
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
    assertThat(gitFetchFilesConfigs.get(0).getGitStoreDelegateConfig().getPaths()).isNotEmpty();
    assertThat(gitFetchFilesConfigs.get(0).getGitStoreDelegateConfig().getPaths().size()).isEqualTo(1);
    assertThat(gitFetchFilesConfigs.get(0).getGitStoreDelegateConfig().getPaths().get(0))
        .isEqualTo("path/to/helm/chart/values3.yaml");
    assertThat(gitFetchFilesConfigs.get(2).getGitStoreDelegateConfig().getPaths()).isNotEmpty();
    assertThat(gitFetchFilesConfigs.get(2).getGitStoreDelegateConfig().getPaths().size()).isEqualTo(1);
    assertThat(gitFetchFilesConfigs.get(2).getGitStoreDelegateConfig().getPaths().get(0))
        .isEqualTo("path/to/helm/chart/values2.yaml");
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
        K8sDirectInfrastructureOutcome.builder().namespace("default").build();
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
        ValuesManifestOutcome.builder().identifier("helmOverride1").store(gitStore2).build();
    InheritFromManifestStoreConfig inheritFromManifestStore =
        InheritFromManifestStoreConfig.builder().paths(ParameterField.createValueField(asList("values4.yaml"))).build();
    ValuesManifestOutcome valuesManifestOutcome2 =
        ValuesManifestOutcome.builder().identifier("helmOverride2").store(inheritFromManifestStore).build();
    Map<String, ManifestOutcome> manifestOutcomeMap = ImmutableMap.of("helm", helmChartManifestOutcome, "helmOverride1",
        valuesManifestOutcome1, "helmOverride2", valuesManifestOutcome2);
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

    StepElementParameters rollingStepElementParams =
        StepElementParameters.builder().spec(K8sRollingStepParameters.infoBuilder().build()).build();
    OptionalOutcome manifestsOutcome =
        OptionalOutcome.builder().found(true).outcome(new ManifestsOutcome(manifestOutcomeMap)).build();
    doReturn(manifestsOutcome).when(outcomeService).resolveOptional(eq(ambiance), eq(manifests));
    doReturn(k8sDirectInfrastructureOutcome).when(outcomeService).resolve(eq(ambiance), eq(infra));

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
        .get(nullable(String.class), nullable(String.class), nullable(String.class), nullable(String.class));

    TaskChainResponse taskChainResponse =
        k8sStepHelper.startChainLink(k8sStepExecutor, ambiance, rollingStepElementParams);
    assertThat(taskChainResponse).isNotNull();
    assertThat(taskChainResponse.getPassThroughData()).isNotNull();
    assertThat(taskChainResponse.getPassThroughData()).isInstanceOf(K8sStepPassThroughData.class);
    ArgumentCaptor<TaskParameters> taskParametersArgumentCaptor = ArgumentCaptor.forClass(TaskParameters.class);
    verify(kryoSerializer, times(2)).asDeflatedBytes(taskParametersArgumentCaptor.capture());
    TaskParameters taskParameters = taskParametersArgumentCaptor.getAllValues().get(0);
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
    assertThat(s3StoreConfig.getRepoName()).isEqualTo("helm-s3-repo");
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
        K8sDirectInfrastructureOutcome.builder().namespace("default").build();
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
        ValuesManifestOutcome.builder().identifier("helmOverride1").store(gitStore2).build();
    InheritFromManifestStoreConfig inheritFromManifestStore =
        InheritFromManifestStoreConfig.builder().paths(ParameterField.createValueField(asList("values4.yaml"))).build();
    ValuesManifestOutcome valuesManifestOutcome2 =
        ValuesManifestOutcome.builder().identifier("helmOverride2").store(inheritFromManifestStore).build();
    Map<String, ManifestOutcome> manifestOutcomeMap = ImmutableMap.of("helm", helmChartManifestOutcome, "helmOverride1",
        valuesManifestOutcome1, "helmOverride2", valuesManifestOutcome2);
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

    StepElementParameters rollingStepElementParams =
        StepElementParameters.builder().spec(K8sRollingStepParameters.infoBuilder().build()).build();
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
        .get(nullable(String.class), nullable(String.class), nullable(String.class), nullable(String.class));

    TaskChainResponse taskChainResponse =
        k8sStepHelper.startChainLink(k8sStepExecutor, ambiance, rollingStepElementParams);
    assertThat(taskChainResponse).isNotNull();
    assertThat(taskChainResponse.getPassThroughData()).isNotNull();
    assertThat(taskChainResponse.getPassThroughData()).isInstanceOf(K8sStepPassThroughData.class);
    ArgumentCaptor<TaskParameters> taskParametersArgumentCaptor = ArgumentCaptor.forClass(TaskParameters.class);
    verify(kryoSerializer, times(3)).asDeflatedBytes(taskParametersArgumentCaptor.capture());
    TaskParameters taskParameters = taskParametersArgumentCaptor.getAllValues().get(0);
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
    assertThat(gcsStoreConfig.getRepoName()).isEqualTo("helm-gcs-repo");
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
  @Owner(developers = ACASIAN)
  @Category(UnitTests.class)
  public void testShouldPrepareHelmHttpValuesFetchTask() {
    K8sDirectInfrastructureOutcome k8sDirectInfrastructureOutcome =
        K8sDirectInfrastructureOutcome.builder().namespace("default").build();
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
        ValuesManifestOutcome.builder().identifier("helmOverride1").store(gitStore2).build();
    InheritFromManifestStoreConfig inheritFromManifestStore =
        InheritFromManifestStoreConfig.builder().paths(ParameterField.createValueField(asList("values4.yaml"))).build();
    ValuesManifestOutcome valuesManifestOutcome2 =
        ValuesManifestOutcome.builder().identifier("helmOverride2").store(inheritFromManifestStore).build();
    Map<String, ManifestOutcome> manifestOutcomeMap = ImmutableMap.of("helm", helmChartManifestOutcome, "helmOverride1",
        valuesManifestOutcome1, "helmOverride2", valuesManifestOutcome2);
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

    StepElementParameters rollingStepElementParams =
        StepElementParameters.builder().spec(K8sRollingStepParameters.infoBuilder().build()).build();
    OptionalOutcome manifestsOutcome =
        OptionalOutcome.builder().found(true).outcome(new ManifestsOutcome(manifestOutcomeMap)).build();
    doReturn(manifestsOutcome).when(outcomeService).resolveOptional(eq(ambiance), eq(manifests));
    doReturn(k8sDirectInfrastructureOutcome).when(outcomeService).resolve(eq(ambiance), eq(infra));

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
        .get(nullable(String.class), nullable(String.class), nullable(String.class), nullable(String.class));

    TaskChainResponse taskChainResponse =
        k8sStepHelper.startChainLink(k8sStepExecutor, ambiance, rollingStepElementParams);
    assertThat(taskChainResponse).isNotNull();
    assertThat(taskChainResponse.getPassThroughData()).isNotNull();
    assertThat(taskChainResponse.getPassThroughData()).isInstanceOf(K8sStepPassThroughData.class);
    ArgumentCaptor<TaskParameters> taskParametersArgumentCaptor = ArgumentCaptor.forClass(TaskParameters.class);
    verify(kryoSerializer, times(2)).asDeflatedBytes(taskParametersArgumentCaptor.capture());
    TaskParameters taskParameters = taskParametersArgumentCaptor.getAllValues().get(0);
    assertThat(taskParameters).isInstanceOf(HelmValuesFetchRequest.class);
    HelmValuesFetchRequest helmValuesFetchRequest = (HelmValuesFetchRequest) taskParameters;
    assertThat(helmValuesFetchRequest.getTimeout()).isNotNull();
    assertThat(helmValuesFetchRequest.getHelmChartManifestDelegateConfig().getStoreDelegateConfig())
        .isInstanceOf(HttpHelmStoreDelegateConfig.class);
    HttpHelmStoreDelegateConfig httpStoreConfig =
        (HttpHelmStoreDelegateConfig) helmValuesFetchRequest.getHelmChartManifestDelegateConfig()
            .getStoreDelegateConfig();
    assertThat(httpStoreConfig.getRepoName()).isEqualTo("helm-http-repo");
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
  public void shouldHandleHelmValueFetchResponse() throws Exception {
    StepElementParameters rollingStepElementParams =
        StepElementParameters.builder().spec(K8sRollingStepParameters.infoBuilder().build()).build();

    K8sStepPassThroughData passThroughData = K8sStepPassThroughData.builder()
                                                 .k8sManifestOutcome(K8sManifestOutcome.builder().build())
                                                 .infrastructure(K8sDirectInfrastructureOutcome.builder().build())
                                                 .build();

    UnitProgressData unitProgressData = UnitProgressData.builder().build();
    HelmValuesFetchResponse helmValuesFetchResponse = HelmValuesFetchResponse.builder()
                                                          .valuesFileContent("values yaml payload")
                                                          .commandExecutionStatus(SUCCESS)
                                                          .unitProgressData(unitProgressData)
                                                          .build();
    Map<String, ResponseData> responseDataMap = ImmutableMap.of("helm-value-fetch-response", helmValuesFetchResponse);
    ThrowingSupplier responseDataSuplier = StrategyHelper.buildResponseDataSupplier(responseDataMap);

    k8sStepHelper.executeNextLink(
        k8sStepExecutor, ambiance, rollingStepElementParams, passThroughData, responseDataSuplier);

    ArgumentCaptor<List> valuesFilesContentCaptor = ArgumentCaptor.forClass(List.class);
    verify(k8sStepExecutor, times(1))
        .executeK8sTask(eq(passThroughData.getK8sManifestOutcome()), eq(ambiance), eq(rollingStepElementParams),
            valuesFilesContentCaptor.capture(),
            eq(K8sExecutionPassThroughData.builder()
                    .infrastructure(passThroughData.getInfrastructure())
                    .lastActiveUnitProgressData(unitProgressData)
                    .build()),
            eq(false), eq(unitProgressData));

    List<String> valuesFilesContent = valuesFilesContentCaptor.getValue();
    assertThat(valuesFilesContent).isNotEmpty();
    assertThat(valuesFilesContent.get(0)).isEqualTo("values yaml payload");
  }

  @Test
  @Owner(developers = PRATYUSH)
  @Category(UnitTests.class)
  public void shouldHandleHelmValueFetchResponseOnNewDelegate() throws Exception {
    StepElementParameters rollingStepElementParams =
        StepElementParameters.builder().spec(K8sRollingStepParameters.infoBuilder().build()).build();

    String manifestIdentifier = "manifest-identifier";
    HelmFetchFileResult valuesYamlList =
        HelmFetchFileResult.builder().valuesFileContents(new ArrayList<>(asList("values yaml payload"))).build();
    Map<String, HelmFetchFileResult> helmChartValuesFileMapContent = new HashMap<>();
    helmChartValuesFileMapContent.put(manifestIdentifier, valuesYamlList);
    K8sStepPassThroughData passThroughData =
        K8sStepPassThroughData.builder()
            .k8sManifestOutcome(K8sManifestOutcome.builder().identifier(manifestIdentifier).build())
            .infrastructure(K8sDirectInfrastructureOutcome.builder().build())
            .build();

    UnitProgressData unitProgressData = UnitProgressData.builder().build();
    HelmValuesFetchResponse helmValuesFetchResponse = HelmValuesFetchResponse.builder()
                                                          .helmChartValuesFileMapContent(helmChartValuesFileMapContent)
                                                          .commandExecutionStatus(SUCCESS)
                                                          .unitProgressData(unitProgressData)
                                                          .build();
    Map<String, ResponseData> responseDataMap = ImmutableMap.of("helm-value-fetch-response", helmValuesFetchResponse);
    ThrowingSupplier responseDataSuplier = StrategyHelper.buildResponseDataSupplier(responseDataMap);

    k8sStepHelper.executeNextLink(
        k8sStepExecutor, ambiance, rollingStepElementParams, passThroughData, responseDataSuplier);

    ArgumentCaptor<List> valuesFilesContentCaptor = ArgumentCaptor.forClass(List.class);
    verify(k8sStepExecutor, times(1))
        .executeK8sTask(eq(passThroughData.getK8sManifestOutcome()), eq(ambiance), eq(rollingStepElementParams),
            valuesFilesContentCaptor.capture(),
            eq(K8sExecutionPassThroughData.builder()
                    .infrastructure(passThroughData.getInfrastructure())
                    .lastActiveUnitProgressData(unitProgressData)
                    .build()),
            eq(false), eq(unitProgressData));

    List<String> valuesFilesContent = valuesFilesContentCaptor.getValue();
    assertThat(valuesFilesContent).isNotEmpty();
    assertThat(valuesFilesContent).isEqualTo(valuesYamlList.getValuesFileContents());
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
    Map<String, HelmFetchFileResult> helmChartValuesFileMapContent = new HashMap<>();
    helmChartValuesFileMapContent.put(manifestIdentifier, valuesYamlList);
    helmChartValuesFileMapContent.put("helmOverride2",
        HelmFetchFileResult.builder().valuesFileContents(new ArrayList<>(asList("values yaml payload"))).build());

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
        ValuesManifestOutcome.builder().identifier("helmOverride1").store(gitStore2).build();
    InheritFromManifestStoreConfig inheritFromManifestStore =
        InheritFromManifestStoreConfig.builder().paths(ParameterField.createValueField(asList("values4.yaml"))).build();
    ValuesManifestOutcome valuesManifestOutcome2 =
        ValuesManifestOutcome.builder().identifier("helmOverride2").store(inheritFromManifestStore).build();
    Map<String, ManifestOutcome> manifestOutcomeMap = ImmutableMap.of(manifestIdentifier, helmChartManifestOutcome,
        "helmOverride1", valuesManifestOutcome1, "helmOverride2", valuesManifestOutcome2);
    ManifestsOutcome manifestOutcomes = (ManifestsOutcome) OptionalOutcome.builder()
                                            .found(true)
                                            .outcome(new ManifestsOutcome(manifestOutcomeMap))
                                            .build()
                                            .getOutcome();
    Collection<ManifestOutcome> manifestOutcomes1 = manifestOutcomes.values();
    List<ManifestOutcome> manifestOutcome = manifestOutcomes1.stream()
                                                .sorted(Comparator.comparingInt(ManifestOutcome::getOrder))
                                                .collect(Collectors.toCollection(LinkedList::new));
    List<ValuesManifestOutcome> aggregatedValuesManifests = CDStepHelper.getAggregatedValuesManifests(manifestOutcome);

    K8sStepPassThroughData passThroughData =
        K8sStepPassThroughData.builder()
            .k8sManifestOutcome(K8sManifestOutcome.builder().identifier(manifestIdentifier).build())
            .infrastructure(K8sDirectInfrastructureOutcome.builder().build())
            .manifestOutcomeList(new ArrayList<>(aggregatedValuesManifests))
            .helmValuesFileMapContents(helmChartValuesFileMapContent)
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
    doReturn(taskChainResponse).when(k8sStepHelper).executeValuesFetchTask(any(), any(), any(), any(), any(), any());
    k8sStepHelper.executeNextLink(k8sStepExecutor, ambiance, stepElementParams, passThroughData, responseDataSuplier);

    ArgumentCaptor<Map> valuesFilesContentCaptor = ArgumentCaptor.forClass(Map.class);
    verify(k8sStepHelper, times(1))
        .executeValuesFetchTask(eq(ambiance), eq(stepElementParams), eq(passThroughData.getInfrastructure()),
            eq(passThroughData.getK8sManifestOutcome()), eq(passThroughData.getValuesManifestOutcomes()),
            valuesFilesContentCaptor.capture());

    Map<String, HelmFetchFileResult> duplicatehelmChartValuesFileMapContent = valuesFilesContentCaptor.getValue();
    assertThat(duplicatehelmChartValuesFileMapContent).isNotEmpty();
    assertThat(duplicatehelmChartValuesFileMapContent).isEqualTo(helmChartValuesFileMapContent);
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
    List<ValuesManifestOutcome> aggregatedValuesManifests = CDStepHelper.getAggregatedValuesManifests(manifestOutcome);

    K8sStepPassThroughData passThroughData = K8sStepPassThroughData.builder()
                                                 .k8sManifestOutcome(helmChartManifestOutcome)
                                                 .infrastructure(K8sDirectInfrastructureOutcome.builder().build())
                                                 .manifestOutcomeList(new ArrayList<>(aggregatedValuesManifests))
                                                 .helmValuesFileMapContents(helmChartValuesFileMapContent)
                                                 .build();

    UnitProgressData unitProgressData = UnitProgressData.builder().build();
    GitFetchResponse gitFetchResponse = GitFetchResponse.builder()
                                            .filesFromMultipleRepo(filesFromMultipleRepo)
                                            .taskStatus(TaskStatus.SUCCESS)
                                            .unitProgressData(unitProgressData)
                                            .build();
    Map<String, ResponseData> responseDataMap = ImmutableMap.of("git-fetch-response", gitFetchResponse);
    ThrowingSupplier responseDataSuplier = StrategyHelper.buildResponseDataSupplier(responseDataMap);

    k8sStepHelper.executeNextLink(k8sStepExecutor, ambiance, stepElementParams, passThroughData, responseDataSuplier);

    ArgumentCaptor<List> valuesFilesContentCaptor = ArgumentCaptor.forClass(List.class);
    verify(k8sStepExecutor, times(1))
        .executeK8sTask(eq(passThroughData.getK8sManifestOutcome()), eq(ambiance), eq(stepElementParams),
            valuesFilesContentCaptor.capture(),
            eq(K8sExecutionPassThroughData.builder()
                    .infrastructure(passThroughData.getInfrastructure())
                    .lastActiveUnitProgressData(unitProgressData)
                    .build()),
            eq(false), eq(unitProgressData));

    List<String> valuesFilesContent = valuesFilesContentCaptor.getValue();
    assertThat(valuesFilesContent).isNotEmpty();
    assertThat(valuesFilesContent.size()).isEqualTo(4);
  }

  @Test
  @Owner(developers = PRATYUSH)
  @Category(UnitTests.class)
  public void shouldhandleGitFetchFilesResponse() throws Exception {
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
    Map<String, ManifestOutcome> manifestOutcomeMap = ImmutableMap.of(manifestIdentifier, helmChartManifestOutcome,
        "helmOverride", valuesManifestOutcome1, "helmOverride2", valuesManifestOutcome2);
    ManifestsOutcome manifestOutcomes = (ManifestsOutcome) OptionalOutcome.builder()
                                            .found(true)
                                            .outcome(new ManifestsOutcome(manifestOutcomeMap))
                                            .build()
                                            .getOutcome();
    Collection<ManifestOutcome> manifestOutcomes1 = manifestOutcomes.values();
    List<ManifestOutcome> manifestOutcome = manifestOutcomes1.stream()
                                                .sorted(Comparator.comparingInt(ManifestOutcome::getOrder))
                                                .collect(Collectors.toCollection(LinkedList::new));
    List<ValuesManifestOutcome> aggregatedValuesManifests = CDStepHelper.getAggregatedValuesManifests(manifestOutcome);
    LinkedList<ValuesManifestOutcome> orderedValuesManifests = new LinkedList<>(aggregatedValuesManifests);
    ValuesManifestOutcome valuesManifestOutcome =
        ValuesManifestOutcome.builder().identifier(helmChartManifestOutcome.getIdentifier()).store(gitStore).build();
    orderedValuesManifests.addFirst(valuesManifestOutcome);

    K8sStepPassThroughData passThroughData =
        K8sStepPassThroughData.builder()
            .k8sManifestOutcome(K8sManifestOutcome.builder().identifier(manifestIdentifier).build())
            .infrastructure(K8sDirectInfrastructureOutcome.builder().build())
            .manifestOutcomeList(new ArrayList<>(orderedValuesManifests))
            .build();

    UnitProgressData unitProgressData = UnitProgressData.builder().build();
    GitFetchResponse gitFetchResponse = GitFetchResponse.builder()
                                            .filesFromMultipleRepo(filesFromMultipleRepo)
                                            .taskStatus(TaskStatus.SUCCESS)
                                            .unitProgressData(unitProgressData)
                                            .build();
    Map<String, ResponseData> responseDataMap = ImmutableMap.of("git-fetch-response", gitFetchResponse);
    ThrowingSupplier responseDataSuplier = StrategyHelper.buildResponseDataSupplier(responseDataMap);

    k8sStepHelper.executeNextLink(k8sStepExecutor, ambiance, stepElementParams, passThroughData, responseDataSuplier);

    ArgumentCaptor<List> valuesFilesContentCaptor = ArgumentCaptor.forClass(List.class);
    verify(k8sStepExecutor, times(1))
        .executeK8sTask(eq(passThroughData.getK8sManifestOutcome()), eq(ambiance), eq(stepElementParams),
            valuesFilesContentCaptor.capture(),
            eq(K8sExecutionPassThroughData.builder()
                    .infrastructure(passThroughData.getInfrastructure())
                    .lastActiveUnitProgressData(unitProgressData)
                    .build()),
            eq(false), eq(unitProgressData));

    List<String> valuesFilesContent = valuesFilesContentCaptor.getValue();
    assertThat(valuesFilesContent).isNotEmpty();
    assertThat(valuesFilesContent.size()).isEqualTo(4);
  }

  @Test
  @Owner(developers = ACASIAN)
  @Category(UnitTests.class)
  public void shouldHandleHelmValueFetchResponseFailure() throws Exception {
    StepElementParameters rollingStepElementParams =
        StepElementParameters.builder().spec(K8sRollingStepParameters.infoBuilder().build()).build();

    K8sStepPassThroughData passThroughData = K8sStepPassThroughData.builder()
                                                 .k8sManifestOutcome(K8sManifestOutcome.builder().build())
                                                 .infrastructure(K8sDirectInfrastructureOutcome.builder().build())
                                                 .build();

    HelmValuesFetchResponse helmValuesFetchResponse =
        HelmValuesFetchResponse.builder().commandExecutionStatus(FAILURE).errorMessage("Something went wrong").build();
    Map<String, ResponseData> responseDataMap = ImmutableMap.of("helm-value-fetch-response", helmValuesFetchResponse);
    ThrowingSupplier responseDataSuplier = StrategyHelper.buildResponseDataSupplier(responseDataMap);

    TaskChainResponse response = k8sStepHelper.executeNextLink(
        k8sStepExecutor, ambiance, rollingStepElementParams, passThroughData, responseDataSuplier);

    assertThat(response.getPassThroughData()).isNotNull();
    assertThat(response.isChainEnd()).isTrue();
    assertThat(response.getPassThroughData()).isInstanceOf(HelmValuesFetchResponsePassThroughData.class);
    HelmValuesFetchResponsePassThroughData helmPassThroughData =
        (HelmValuesFetchResponsePassThroughData) response.getPassThroughData();
    assertThat(helmPassThroughData.getErrorMsg()).isEqualTo("Something went wrong");
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testExecuteNextLinkInternalStepException() throws Exception {
    StepElementParameters rollingStepElementParams =
        StepElementParameters.builder().spec(K8sRollingStepParameters.infoBuilder().build()).build();
    UnitProgressData unitProgressData =
        UnitProgressData.builder()
            .unitProgresses(
                asList(UnitProgress.newBuilder().setUnitName("Fetch Files").setStatus(UnitStatus.RUNNING).build(),
                    UnitProgress.newBuilder().setUnitName("Some Unit").setStatus(UnitStatus.SUCCESS).build()))
            .build();

    K8sStepPassThroughData passThroughData = K8sStepPassThroughData.builder()
                                                 .k8sManifestOutcome(K8sManifestOutcome.builder().build())
                                                 .infrastructure(K8sDirectInfrastructureOutcome.builder().build())
                                                 .build();

    GitFetchResponse gitFetchResponse = GitFetchResponse.builder()
                                            .filesFromMultipleRepo(Collections.emptyMap())
                                            .taskStatus(TaskStatus.SUCCESS)
                                            .unitProgressData(unitProgressData)
                                            .build();
    Map<String, ResponseData> responseDataMap = ImmutableMap.of("git-fetch-response", gitFetchResponse);
    ThrowingSupplier responseDataSuplier = StrategyHelper.buildResponseDataSupplier(responseDataMap);
    RuntimeException thrownException = new RuntimeException("Failed to do something");

    doThrow(thrownException)
        .when(k8sStepExecutor)
        .executeK8sTask(passThroughData.getK8sManifestOutcome(), ambiance, rollingStepElementParams,
            Collections.emptyList(),
            K8sExecutionPassThroughData.builder()
                .infrastructure(passThroughData.getInfrastructure())
                .lastActiveUnitProgressData(unitProgressData)
                .build(),
            false, unitProgressData);

    TaskChainResponse response = k8sStepHelper.executeNextLink(
        k8sStepExecutor, ambiance, rollingStepElementParams, passThroughData, responseDataSuplier);

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
  public void testGetParameterFieldBooleanValue() {
    assertThat(CDStepHelper.getParameterFieldBooleanValue(
                   ParameterField.createValueField("true"), "testField", StepElementParameters.builder().build()))
        .isTrue();
    assertThat(CDStepHelper.getParameterFieldBooleanValue(
                   ParameterField.createValueField("false"), "testField", StepElementParameters.builder().build()))
        .isFalse();

    assertThatThrownBy(()
                           -> CDStepHelper.getParameterFieldBooleanValue(ParameterField.createValueField("absad"),
                               "testField", StepElementParameters.builder().identifier("test").type("Test").build()))
        .hasMessageContaining("for field testField in Test step with identifier: test");
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testHandleTaskException() throws Exception {
    K8sExecutionPassThroughData executionPassThroughData =
        K8sExecutionPassThroughData.builder()
            .lastActiveUnitProgressData(
                UnitProgressData.builder()
                    .unitProgresses(
                        asList(UnitProgress.newBuilder().setUnitName("Completed").setStatus(UnitStatus.SUCCESS).build(),
                            UnitProgress.newBuilder().setUnitName("Running").setStatus(UnitStatus.RUNNING).build()))
                    .build())
            .build();

    Exception exception = new GeneralException("Something went wrong");

    StepResponse stepResponse = k8sStepHelper.handleTaskException(ambiance, executionPassThroughData, exception);
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
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testHandleTaskExceptionK8sTaskException() {
    Exception thrownException = new TaskNGDataException(null, new KubernetesTaskException("Failed"));
    assertThatThrownBy(() -> k8sStepHelper.handleTaskException(ambiance, null, thrownException))
        .isSameAs(thrownException);
  }

  @Test
  @Owner(developers = ACASIAN)
  @Category(UnitTests.class)
  public void testShouldCreateTaskRequestWithNonProdEnvType() {
    doReturn(NON_PROD).when(stepHelper).getEnvironmentType(ambiance);

    K8sSpecParameters k8sSpecParameters = K8sRollingStepParameters.infoBuilder().build();
    TaskChainResponse taskChainResponse = k8sStepHelper.queueK8sTask(
        StepElementParameters.builder().spec(k8sSpecParameters).build(),
        K8sRollingDeployRequest.builder()
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
        ambiance, K8sExecutionPassThroughData.builder().build());
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

    K8sSpecParameters k8sSpecParameters = K8sRollingStepParameters.infoBuilder().build();
    TaskChainResponse taskChainResponse = k8sStepHelper.queueK8sTask(
        StepElementParameters.builder().spec(k8sSpecParameters).build(),
        K8sRollingDeployRequest.builder()
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
        ambiance, K8sExecutionPassThroughData.builder().build());
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
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testFailRuntimeAccess() {
    K8sManifestOutcome k8s = K8sManifestOutcome.builder().store(sampleGitStore("test1")).build();
    ValuesManifestOutcome values1 = ValuesManifestOutcome.builder().build();
    ValuesManifestOutcome values2 = ValuesManifestOutcome.builder().store(sampleGitStore("test2")).build();
    Set<EntityDetailProtoDTO> k8sEntities = ImmutableSet.of(EntityDetailProtoDTO.newBuilder().build());
    Set<EntityDetailProtoDTO> values2Entities = ImmutableSet.of(EntityDetailProtoDTO.newBuilder().build());
    HashSet<EntityDetailProtoDTO> allEntities = new HashSet<>();
    allEntities.addAll(k8sEntities);
    allEntities.addAll(values2Entities);
    ManifestsOutcome manifests =
        new ManifestsOutcome(ImmutableMap.of("k8s", k8s, "values1", values1, "values2", values2));
    RuntimeException runtimeAccessFailure = new RuntimeException("Unauthorized");

    doReturn(k8sEntities).when(entityReferenceExtractorUtils).extractReferredEntities(ambiance, k8s.getStore());
    doReturn(values2Entities).when(entityReferenceExtractorUtils).extractReferredEntities(ambiance, values2.getStore());
    doThrow(runtimeAccessFailure).when(pipelineRbacHelper).checkRuntimePermissions(ambiance, allEntities);
    OptionalOutcome manifestsOutcome =
        OptionalOutcome.builder().found(true).outcome(new ManifestsOutcome(manifests)).build();
    doReturn(manifestsOutcome)
        .when(outcomeService)
        .resolveOptional(ambiance, RefObjectUtils.getOutcomeRefObject(OutcomeExpressionConstants.MANIFESTS));

    assertThatThrownBy(
        () -> k8sStepHelper.startChainLink(k8sStepExecutor, ambiance, StepElementParameters.builder().build()))
        .isSameAs(runtimeAccessFailure);
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testStartChainLinkOrderedValues() {
    K8sDirectInfrastructureOutcome k8sDirectInfrastructureOutcome =
        K8sDirectInfrastructureOutcome.builder().namespace("default").build();
    Map<String, ManifestOutcome> manifestOutcomeMap = ImmutableMap.of("k8s", manifestWith("k8s", "K8sManifest", -1),
        "values2", manifestWith("values2", "Values", 2), "values3", manifestWith("values3", "Values", 3), "values1",
        manifestWith("values1", "Values", 1), "values4", manifestWith("values4", "Values", 4));
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

    StepElementParameters rollingStepElementParams =
        StepElementParameters.builder().spec(K8sRollingStepParameters.infoBuilder().build()).build();
    OptionalOutcome optionalOutcome =
        OptionalOutcome.builder().outcome(new ManifestsOutcome(manifestOutcomeMap)).found(true).build();
    doReturn(optionalOutcome).when(outcomeService).resolveOptional(eq(ambiance), eq(manifests));
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
        .get(nullable(String.class), nullable(String.class), nullable(String.class), nullable(String.class));

    TaskChainResponse taskChainResponse =
        k8sStepHelper.startChainLink(k8sStepExecutor, ambiance, rollingStepElementParams);
    assertThat(taskChainResponse.getPassThroughData()).isInstanceOf(K8sStepPassThroughData.class);
    K8sStepPassThroughData stepPassThroughData = (K8sStepPassThroughData) taskChainResponse.getPassThroughData();
    assertThat(stepPassThroughData.getValuesManifestOutcomes().stream().map(ManifestOutcome::getIdentifier))
        .containsExactly("k8s", "values1", "values2", "values3", "values4");
  }

  @Test
  @Owner(developers = ACASIAN)
  @Category(UnitTests.class)
  public void testShouldPublishReleaseNameStepDetails() {
    k8sStepHelper.publishReleaseNameStepDetails(ambiance, "test-release-name");

    ArgumentCaptor<K8sReleaseDetailsInfo> releaseDetailsCaptor = ArgumentCaptor.forClass(K8sReleaseDetailsInfo.class);
    ArgumentCaptor<String> releaseNameCaptor = ArgumentCaptor.forClass(String.class);
    verify(sdkGraphVisualizationDataService, times(1))
        .publishStepDetailInformation(eq(ambiance), releaseDetailsCaptor.capture(), releaseNameCaptor.capture());

    assertThat(releaseDetailsCaptor.getValue().getReleaseName()).isEqualTo("test-release-name");
    assertThat(releaseNameCaptor.getValue()).isEqualTo(RELEASE_NAME);
  }

  private GitStore sampleGitStore(String identifier) {
    return GitStore.builder()
        .connectorRef(ParameterField.createValueField(identifier))
        .paths(ParameterField.createValueField(asList("file1", "file2")))
        .gitFetchType(FetchType.BRANCH)
        .branch(ParameterField.createValueField("master"))
        .build();
  }

  private ManifestOutcome manifestWith(String identifier, String type, int order) {
    GitStore store = GitStore.builder()
                         .connectorRef(ParameterField.createValueField(identifier))
                         .paths(ParameterField.createValueField(asList("dir/templates")))
                         .gitFetchType(FetchType.BRANCH)
                         .branch(ParameterField.createValueField("master"))
                         .build();

    switch (type) {
      case "K8sManifest":
        return K8sManifestOutcome.builder().identifier(identifier).store(store).build();
      case "Values":
        return ValuesManifestOutcome.builder().identifier(identifier).store(store).order(order).build();
      default:
        throw new UnsupportedOperationException("Type " + type + " not supported");
    }
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

    assertThatThrownBy(() -> k8sStepHelper.getManifestDelegateConfig(manifestOutcome, ambiance))
        .isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  @Owner(developers = ACHYUTH)
  @Category(UnitTests.class)
  public void testExecuteValuesFetchTask() {
    Ambiance ambiance = getAmbiance();
    StepElementParameters stepElementParameters =
        StepElementParameters.builder().spec(K8sRollingStepParameters.infoBuilder().build()).build();
    ConnectorInfoDTO connectorDTO = ConnectorInfoDTO.builder().connectorType(GITHUB).build();
    Optional<ConnectorResponseDTO> connectorDTOOptional =
        Optional.of(ConnectorResponseDTO.builder().connector(connectorDTO).build());
    doReturn(connectorDTOOptional).when(connectorService).get("account1", "org1", "project1", "abcConnector");

    K8sDirectInfrastructureOutcomeBuilder outcomeBuilder =
        K8sDirectInfrastructureOutcome.builder().connectorRef("abcConnector").namespace("valid");

    K8sManifestOutcome manifestOutcome = K8sManifestOutcome.builder().build();

    List<ValuesManifestOutcome> aggregatedValuesManifests = new ArrayList<>();

    Map<String, HelmFetchFileResult> helmChartFetchFilesResultMap = new HashMap<>();

    assertThatCode(()
                       -> k8sStepHelper.executeValuesFetchTask(ambiance, stepElementParameters, outcomeBuilder.build(),
                           manifestOutcome, aggregatedValuesManifests, helmChartFetchFilesResultMap));
  }

  @Test
  @Owner(developers = ACHYUTH)
  @Category(UnitTests.class)
  public void testPrepareOpenshiftParamFetchTask() {
    Ambiance ambiance = getAmbiance();
    StepElementParameters stepElementParameters =
        StepElementParameters.builder().spec(K8sRollingStepParameters.infoBuilder().build()).build();
    ConnectorInfoDTO connectorDTO = ConnectorInfoDTO.builder().connectorType(GITHUB).build();
    Optional<ConnectorResponseDTO> connectorDTOOptional =
        Optional.of(ConnectorResponseDTO.builder().connector(connectorDTO).build());
    doReturn(connectorDTOOptional).when(connectorService).get("account1", "org1", "project1", "abcConnector");

    K8sDirectInfrastructureOutcomeBuilder outcomeBuilder =
        K8sDirectInfrastructureOutcome.builder().connectorRef("abcConnector").namespace("valid");

    K8sManifestOutcome manifestOutcome = K8sManifestOutcome.builder().build();

    List<OpenshiftParamManifestOutcome> openshiftParamManifests = new ArrayList<>();

    assertThatCode(()
                       -> k8sStepHelper.prepareOpenshiftParamFetchTask(ambiance, stepElementParameters,
                           outcomeBuilder.build(), manifestOutcome, openshiftParamManifests));
  }

  @Test
  @Owner(developers = NAMAN_TALAYCHA)
  @Category(UnitTests.class)
  public void testPrepareKustomizePatchesFetchTask() {
    Ambiance ambiance = getAmbiance();
    StepElementParameters stepElementParameters =
        StepElementParameters.builder().spec(K8sRollingStepParameters.infoBuilder().build()).build();
    ConnectorInfoDTO connectorDTO =
        ConnectorInfoDTO.builder().connectorType(GITHUB).connectorConfig(GithubConnectorDTO.builder().build()).build();
    Optional<ConnectorResponseDTO> connectorDTOOptional =
        Optional.of(ConnectorResponseDTO.builder().connector(connectorDTO).build());
    doReturn(connectorDTOOptional).when(connectorService).get("account1", "org1", "project1", "abcConnector");

    K8sDirectInfrastructureOutcomeBuilder outcomeBuilder =
        K8sDirectInfrastructureOutcome.builder().connectorRef("abcConnector").namespace("valid");

    K8sManifestOutcome manifestOutcome = K8sManifestOutcome.builder().store(GithubStore.builder().build()).build();

    List<KustomizePatchesManifestOutcome> kustomizePatchesManifests = new ArrayList<>();

    assertThatCode(()
                       -> k8sStepHelper.prepareKustomizePatchesFetchTask(k8sStepExecutor, ambiance,
                           stepElementParameters, outcomeBuilder.build(), manifestOutcome, kustomizePatchesManifests));
  }

  @Test
  @Owner(developers = NAMAN_TALAYCHA)
  @Category(UnitTests.class)
  public void testGetKustomizePatchesManifests() {
    KustomizePatchesManifestOutcome kustomizePatchesManifestOutcome =
        KustomizePatchesManifestOutcome.builder().identifier("id1").build();
    List<ManifestOutcome> manifestOutcomeList = new ArrayList<>();
    manifestOutcomeList.add(kustomizePatchesManifestOutcome);
    List<KustomizePatchesManifestOutcome> kustomizePatchesManifests =
        k8sStepHelper.getKustomizePatchesManifests(manifestOutcomeList);
    assertThat(kustomizePatchesManifests.size()).isEqualTo(1);
    assertThat(kustomizePatchesManifests.get(0).getType())
        .isEqualTo(io.harness.cdng.manifest.ManifestType.KustomizePatches);
  }

  @Test
  @Owner(developers = ACHYUTH)
  @Category(UnitTests.class)
  public void shouldHandleGitFetchResponse() throws Exception {
    StepElementParameters rollingStepElementParams =
        StepElementParameters.builder().spec(K8sRollingStepParameters.infoBuilder().build()).build();

    List<ValuesManifestOutcome> valuesManifestOutcomeList = new ArrayList<>();
    valuesManifestOutcomeList.add(ValuesManifestOutcome.builder()
                                      .identifier("abc")
                                      .store(GitStore.builder()
                                                 .branch(ParameterField.createValueField("master"))
                                                 .folderPath(ParameterField.createValueField("abc/"))
                                                 .build())
                                      .build());
    K8sStepPassThroughData passThroughData = K8sStepPassThroughData.builder()
                                                 .k8sManifestOutcome(K8sManifestOutcome.builder().build())
                                                 .manifestOutcomeList(new ArrayList<>(valuesManifestOutcomeList))
                                                 .infrastructure(K8sDirectInfrastructureOutcome.builder().build())
                                                 .build();

    List<GitFile> gitFileList = new ArrayList<>();
    gitFileList.add(GitFile.builder().fileContent("dummy").build());

    Map<String, FetchFilesResult> filesResultMap = new HashMap<>();
    filesResultMap.put("abc", FetchFilesResult.builder().files(gitFileList).build());
    GitFetchResponse gitFetchResponse =
        GitFetchResponse.builder().taskStatus(TaskStatus.SUCCESS).filesFromMultipleRepo(filesResultMap).build();
    Map<String, ResponseData> responseDataMap = ImmutableMap.of("git-fetch-response", gitFetchResponse);
    ThrowingSupplier responseDataSuplier = StrategyHelper.buildResponseDataSupplier(responseDataMap);

    assertThatCode(()
                       -> k8sStepHelper.executeNextLink(
                           k8sStepExecutor, ambiance, rollingStepElementParams, passThroughData, responseDataSuplier));
    verify(k8sStepExecutor, times(1))
        .executeK8sTask(eq(passThroughData.getK8sManifestOutcome()), eq(ambiance), eq(rollingStepElementParams), any(),
            any(), anyBoolean(), any());
  }

  @Test
  @Owner(developers = NAMAN_TALAYCHA)
  @Category(UnitTests.class)
  public void shouldHandleGitFetchResponseKustomizeCase() throws Exception {
    StepElementParameters rollingStepElementParams =
        StepElementParameters.builder().name("Rolling").spec(K8sRollingStepParameters.infoBuilder().build()).build();

    List<KustomizePatchesManifestOutcome> kustomizePatchesManifestOutcomeList = new ArrayList<>();
    kustomizePatchesManifestOutcomeList.add(KustomizePatchesManifestOutcome.builder()
                                                .identifier("abc")
                                                .store(GitStore.builder()
                                                           .branch(ParameterField.createValueField("master"))
                                                           .folderPath(ParameterField.createValueField("abc/"))
                                                           .build())
                                                .build());
    K8sStepPassThroughData passThroughData =
        K8sStepPassThroughData.builder()
            .k8sManifestOutcome(KustomizeManifestOutcome.builder().build())
            .manifestOutcomeList(new ArrayList<>(kustomizePatchesManifestOutcomeList))
            .infrastructure(K8sDirectInfrastructureOutcome.builder().build())
            .build();

    List<GitFile> gitFileList = new ArrayList<>();
    gitFileList.add(GitFile.builder().fileContent("dummy").build());

    Map<String, FetchFilesResult> filesResultMap = new HashMap<>();
    filesResultMap.put("abc", FetchFilesResult.builder().files(gitFileList).build());
    GitFetchResponse gitFetchResponse = GitFetchResponse.builder()
                                            .taskStatus(TaskStatus.SUCCESS)
                                            .unitProgressData(UnitProgressData.builder().build())
                                            .filesFromMultipleRepo(filesResultMap)
                                            .build();
    Map<String, ResponseData> responseDataMap = ImmutableMap.of("git-fetch-response", gitFetchResponse);
    ThrowingSupplier responseDataSuplier = StrategyHelper.buildResponseDataSupplier(responseDataMap);

    assertThatCode(()
                       -> k8sStepHelper.executeNextLink(
                           k8sStepExecutor, ambiance, rollingStepElementParams, passThroughData, responseDataSuplier));

    ArgumentCaptor<StepElementParameters> stepElementParametersCaptor =
        ArgumentCaptor.forClass(StepElementParameters.class);
    ArgumentCaptor<K8sExecutionPassThroughData> k8sExecutionPassThroughDataCaptor =
        ArgumentCaptor.forClass(K8sExecutionPassThroughData.class);
    verify(k8sStepExecutor, times(1))
        .executeK8sTask(any(), any(), stepElementParametersCaptor.capture(), any(),
            k8sExecutionPassThroughDataCaptor.capture(), anyBoolean(), any());
    assertThat(k8sExecutionPassThroughDataCaptor.getValue()).isNotNull();
    assertThat(stepElementParametersCaptor.getValue()).isNotNull();
    assertThat(stepElementParametersCaptor.getValue().getName()).isEqualTo("Rolling");
    assertThat(k8sExecutionPassThroughDataCaptor.getValue().getInfrastructure()).isNotNull();
  }

  @Test
  @Owner(developers = ACHYUTH)
  @Category(UnitTests.class)
  public void testEncryptionDataDetailsForK8sConnector() {
    Ambiance ambiance = getAmbiance();

    ConnectorInfoDTO connectorDTO =
        ConnectorInfoDTO.builder()
            .connectorType(KUBERNETES_CLUSTER)
            .connectorConfig(KubernetesClusterConfigDTO.builder()
                                 .credential(KubernetesCredentialDTO.builder()
                                                 .kubernetesCredentialType(KubernetesCredentialType.MANUAL_CREDENTIALS)
                                                 .config(KubernetesClusterDetailsDTO.builder()
                                                             .auth(KubernetesAuthDTO.builder().build())
                                                             .masterUrl("abc")
                                                             .build())
                                                 .build())
                                 .build())
            .build();

    Optional<ConnectorResponseDTO> connectorDTOOptional =
        Optional.of(ConnectorResponseDTO.builder().connector(connectorDTO).build());
    doReturn(connectorDTOOptional).when(connectorService).get("account1", "org1", "project1", "abcConnector");

    K8sDirectInfrastructureOutcomeBuilder outcomeBuilder =
        K8sDirectInfrastructureOutcome.builder().connectorRef("abcConnector").namespace("valid");

    when(secretManagerClientService.getEncryptionDetails(any(), any())).thenReturn(new ArrayList<>());

    k8sStepHelper.getK8sInfraDelegateConfig(outcomeBuilder.build(), ambiance);

    verify(secretManagerClientService, times(1)).getEncryptionDetails(any(), any());
  }

  private List<OpenshiftParamManifestOutcome> getOpenshiftParamManifestOutcome(
      List<ManifestOutcome> manifestOutcomeList) {
    if (isEmpty(manifestOutcomeList)) {
      return Collections.emptyList();
    }
    List<OpenshiftParamManifestOutcome> openshiftParamManifestOutcomeList = new ArrayList<>();
    for (ManifestOutcome manifestOutcome : manifestOutcomeList) {
      if (io.harness.cdng.manifest.ManifestType.OpenshiftParam.equals(manifestOutcome.getType())) {
        openshiftParamManifestOutcomeList.add((OpenshiftParamManifestOutcome) manifestOutcome);
      }
    }
    return openshiftParamManifestOutcomeList;
  }

  @Test
  @Owner(developers = ACHYUTH)
  @Category(UnitTests.class)
  public void testPrepareOcTemplateWithOcParamManifests() {
    K8sDirectInfrastructureOutcome k8sDirectInfrastructureOutcome =
        K8sDirectInfrastructureOutcome.builder().namespace("default").build();
    GitStore gitStore = GitStore.builder()
                            .branch(ParameterField.createValueField("master"))
                            .paths(ParameterField.createValueField(asList("path/to/k8s/manifest/template.yaml")))
                            .connectorRef(ParameterField.createValueField("git-connector"))
                            .build();
    InheritFromManifestStoreConfig inheritFromManifestStore =
        InheritFromManifestStoreConfig.builder()
            .paths(ParameterField.createValueField(asList("path/to/k8s/manifest/param3.yaml")))
            .build();
    OpenshiftManifestOutcome openshiftManifestOutcome =
        OpenshiftManifestOutcome.builder()
            .identifier("OpenShift")
            .store(gitStore)
            .paramsPaths(ParameterField.createValueField(asList("path/to/k8s/manifest/param2.yaml")))
            .build();
    OpenshiftParamManifestOutcome openshiftParamManifestOutcome1 =
        OpenshiftParamManifestOutcome.builder().identifier("OpenShiftParam1").store(gitStore).build();
    OpenshiftParamManifestOutcome openshiftParamManifestOutcome2 =
        OpenshiftParamManifestOutcome.builder().identifier("OpenShiftParam2").store(inheritFromManifestStore).build();
    Map<String, ManifestOutcome> manifestOutcomeMap = ImmutableMap.of("OpenShift", openshiftManifestOutcome,
        "OpenShiftParam1", openshiftParamManifestOutcome1, "OpenShiftParam2", openshiftParamManifestOutcome2);
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

    StepElementParameters rollingStepElementParams =
        StepElementParameters.builder().spec(K8sRollingStepParameters.infoBuilder().build()).build();
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
        .get(nullable(String.class), nullable(String.class), nullable(String.class), nullable(String.class));

    TaskChainResponse taskChainResponse =
        k8sStepHelper.startChainLink(k8sStepExecutor, ambiance, rollingStepElementParams);
    assertThat(taskChainResponse).isNotNull();
    assertThat(taskChainResponse.isChainEnd()).isEqualTo(false);
    assertThat(taskChainResponse.getTaskRequest().getDelegateTaskRequest().getTaskName())
        .isEqualTo("Git Fetch Files Task");
    assertThat(taskChainResponse.getTaskRequest().getDelegateTaskRequest().getLogKeys(0))
        .isEqualTo(
            "accountId:test-account/orgId:test-org/projectId:test-project/pipelineId:/runSequence:0-commandUnit:Fetch Files");
    assertThat(taskChainResponse.getPassThroughData()).isNotNull();
    assertThat(taskChainResponse.getPassThroughData()).isInstanceOf(K8sStepPassThroughData.class);
    K8sStepPassThroughData k8sStepPassThroughData = (K8sStepPassThroughData) taskChainResponse.getPassThroughData();
    assertThat(getOpenshiftParamManifestOutcome(k8sStepPassThroughData.getManifestOutcomeList())).isNotEmpty();
    assertThat(getOpenshiftParamManifestOutcome(k8sStepPassThroughData.getManifestOutcomeList()).size()).isEqualTo(3);
    List<OpenshiftParamManifestOutcome> openshiftParamManifestOutcome =
        getOpenshiftParamManifestOutcome(k8sStepPassThroughData.getManifestOutcomeList());
    assertThat(openshiftParamManifestOutcome.get(0).getIdentifier())
        .isEqualTo(openshiftManifestOutcome.getIdentifier());
    assertThat(openshiftParamManifestOutcome.get(0).getStore()).isEqualTo(openshiftManifestOutcome.getStore());
    assertThat(openshiftParamManifestOutcome.get(1).getIdentifier())
        .isEqualTo(openshiftParamManifestOutcome1.getIdentifier());
    assertThat(openshiftParamManifestOutcome.get(1).getStore()).isEqualTo(openshiftParamManifestOutcome1.getStore());
    assertThat(openshiftParamManifestOutcome.get(2).getIdentifier())
        .isEqualTo(openshiftParamManifestOutcome2.getIdentifier());
    assertThat(openshiftParamManifestOutcome.get(2).getStore()).isEqualTo(openshiftParamManifestOutcome2.getStore());
    ArgumentCaptor<Object> argumentCaptor = ArgumentCaptor.forClass(Object.class);
    verify(kryoSerializer, times(4)).asDeflatedBytes(argumentCaptor.capture());
    TaskParameters taskParameters = (TaskParameters) argumentCaptor.getAllValues().get(0);
    assertThat(taskParameters).isInstanceOf(GitFetchRequest.class);
    GitFetchRequest gitFetchRequest = (GitFetchRequest) taskParameters;
    assertThat(gitFetchRequest.getGitFetchFilesConfigs()).isNotEmpty();
    assertThat(gitFetchRequest.getGitFetchFilesConfigs().size()).isEqualTo(3);
    List<GitFetchFilesConfig> gitFetchFilesConfigs = gitFetchRequest.getGitFetchFilesConfigs();
    assertThat(gitFetchFilesConfigs.get(1).getGitStoreDelegateConfig().getPaths()).isNotEmpty();
    assertThat(gitFetchFilesConfigs.get(1).getGitStoreDelegateConfig().getPaths().size()).isEqualTo(1);
    assertThat(gitFetchFilesConfigs.get(1).getGitStoreDelegateConfig().getPaths().get(0))
        .isEqualTo("path/to/k8s/manifest/template.yaml");
    assertThat(gitFetchFilesConfigs.get(0).getGitStoreDelegateConfig().getPaths()).isNotEmpty();
    assertThat(gitFetchFilesConfigs.get(0).getGitStoreDelegateConfig().getPaths().size()).isEqualTo(1);
    assertThat(gitFetchFilesConfigs.get(0).getGitStoreDelegateConfig().getPaths().get(0))
        .isEqualTo("path/to/k8s/manifest/param2.yaml");
    assertThat(gitFetchFilesConfigs.get(2).getGitStoreDelegateConfig().getPaths()).isNotEmpty();
    assertThat(gitFetchFilesConfigs.get(2).getGitStoreDelegateConfig().getPaths().size()).isEqualTo(1);
    assertThat(gitFetchFilesConfigs.get(2).getGitStoreDelegateConfig().getPaths().get(0))
        .isEqualTo("path/to/k8s/manifest/param3.yaml");
    assertThat(argumentCaptor.getAllValues().get(1)).isInstanceOf(GitConnectionNGCapability.class);

    // without OpenShift Params
    Map<String, ManifestOutcome> manifestOutcomeMapOnlyTemplate =
        ImmutableMap.of("OpenShift", openshiftManifestOutcome);
    OptionalOutcome manifestsOutcomeOnlyTemplate =
        OptionalOutcome.builder().found(true).outcome(new ManifestsOutcome(manifestOutcomeMapOnlyTemplate)).build();

    doReturn(manifestsOutcomeOnlyTemplate).when(outcomeService).resolveOptional(eq(ambiance), eq(manifests));

    assertThat(k8sStepHelper.startChainLink(k8sStepExecutor, ambiance, rollingStepElementParams)).isNotNull();
  }

  private List<KustomizePatchesManifestOutcome> getKustomizePatchesManifestOutcomes(
      List<ManifestOutcome> manifestOutcomeList) {
    if (isEmpty(manifestOutcomeList)) {
      return Collections.emptyList();
    }
    List<KustomizePatchesManifestOutcome> kustomizePatchesManifestOutcomeList = new ArrayList<>();
    for (ManifestOutcome manifestOutcome : manifestOutcomeList) {
      if (io.harness.cdng.manifest.ManifestType.KustomizePatches.equals(manifestOutcome.getType())) {
        kustomizePatchesManifestOutcomeList.add((KustomizePatchesManifestOutcome) manifestOutcome);
      }
    }
    return kustomizePatchesManifestOutcomeList;
  }

  @Test
  @Owner(developers = NAMAN_TALAYCHA)
  @Category(UnitTests.class)
  public void testStartChainLinkKustomizePatchesCase() {
    K8sDirectInfrastructureOutcome k8sDirectInfrastructureOutcome =
        K8sDirectInfrastructureOutcome.builder().namespace("default").build();
    GitStore gitStore = GitStore.builder()
                            .branch(ParameterField.createValueField("master"))
                            .folderPath(ParameterField.createValueField("path/to/k8s/manifest"))
                            .connectorRef(ParameterField.createValueField("git-connector"))
                            .build();
    GitStore gitStorePatches = GitStore.builder()
                                   .branch(ParameterField.createValueField("master"))
                                   .paths(ParameterField.createValueField(asList("path/to/k8s/manifest/patch1.yaml")))
                                   .connectorRef(ParameterField.createValueField("git-connector"))
                                   .build();
    InheritFromManifestStoreConfig inheritFromManifestStore =
        InheritFromManifestStoreConfig.builder()
            .paths(ParameterField.createValueField(asList("path/to/k8s/manifest/patch2.yaml")))
            .build();
    KustomizeManifestOutcome kustomizeManifestOutcome =
        KustomizeManifestOutcome.builder()
            .identifier("Kustomize")
            .store(gitStore)
            .patchesPaths(ParameterField.createValueField(asList("path/to/k8s/manifest/patch3.yaml")))
            .build();
    KustomizePatchesManifestOutcome kustomizePatchesManifestOutcome1 =
        KustomizePatchesManifestOutcome.builder().identifier("KustomizePatches1").store(gitStorePatches).build();
    KustomizePatchesManifestOutcome kustomizePatchesManifestOutcome2 = KustomizePatchesManifestOutcome.builder()
                                                                           .identifier("KustomizePatches2")
                                                                           .store(inheritFromManifestStore)
                                                                           .build();
    Map<String, ManifestOutcome> manifestOutcomeMap = ImmutableMap.of("Kustomize", kustomizeManifestOutcome,
        "KustomizePatches1", kustomizePatchesManifestOutcome1, "KustomizePatches2", kustomizePatchesManifestOutcome2);
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

    StepElementParameters rollingStepElementParams =
        StepElementParameters.builder().spec(K8sRollingStepParameters.infoBuilder().build()).build();

    OptionalOutcome manifestsOutcome =
        OptionalOutcome.builder().found(true).outcome(new ManifestsOutcome(manifestOutcomeMap)).build();

    doReturn(true).when(cdFeatureFlagHelper).isEnabled(any(), any());
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
        .get(nullable(String.class), nullable(String.class), nullable(String.class), nullable(String.class));
    when(k8sStepExecutor.executeK8sTask(any(), any(), any(), any(), any(), anyBoolean(), any()))
        .thenReturn(TaskChainResponse.builder().chainEnd(true).build());

    TaskChainResponse taskChainResponse =
        k8sStepHelper.startChainLink(k8sStepExecutor, ambiance, rollingStepElementParams);
    assertThat(taskChainResponse).isNotNull();
    assertThat(taskChainResponse.isChainEnd()).isEqualTo(false);
    assertThat(taskChainResponse.getTaskRequest().getDelegateTaskRequest().getTaskName())
        .isEqualTo("Git Fetch Files Task");
    assertThat(taskChainResponse.getTaskRequest().getDelegateTaskRequest().getLogKeys(0))
        .isEqualTo(
            "accountId:test-account/orgId:test-org/projectId:test-project/pipelineId:/runSequence:0-commandUnit:Fetch Files");
    assertThat(taskChainResponse.getPassThroughData()).isNotNull();
    assertThat(taskChainResponse.getPassThroughData()).isInstanceOf(K8sStepPassThroughData.class);
    K8sStepPassThroughData k8sStepPassThroughData = (K8sStepPassThroughData) taskChainResponse.getPassThroughData();
    assertThat(getKustomizePatchesManifestOutcomes(k8sStepPassThroughData.getManifestOutcomeList())).isNotEmpty();
    assertThat(getKustomizePatchesManifestOutcomes(k8sStepPassThroughData.getManifestOutcomeList()).size())
        .isEqualTo(3);
    List<KustomizePatchesManifestOutcome> KustomizePatchesManifestOutcome =
        getKustomizePatchesManifestOutcomes(k8sStepPassThroughData.getManifestOutcomeList());
    assertThat(KustomizePatchesManifestOutcome.get(0).getIdentifier())
        .isEqualTo(kustomizeManifestOutcome.getIdentifier());
    assertThat(KustomizePatchesManifestOutcome.get(0).getStore()).isEqualTo(kustomizeManifestOutcome.getStore());
    assertThat(KustomizePatchesManifestOutcome.get(1).getIdentifier())
        .isEqualTo(kustomizePatchesManifestOutcome2.getIdentifier());
    assertThat(KustomizePatchesManifestOutcome.get(1).getStore())
        .isEqualTo(kustomizePatchesManifestOutcome2.getStore());
    assertThat(KustomizePatchesManifestOutcome.get(2).getIdentifier())
        .isEqualTo(kustomizePatchesManifestOutcome1.getIdentifier());
    assertThat(KustomizePatchesManifestOutcome.get(2).getStore())
        .isEqualTo(kustomizePatchesManifestOutcome1.getStore());
    ArgumentCaptor<Object> argumentCaptor = ArgumentCaptor.forClass(Object.class);
    verify(kryoSerializer, times(4)).asDeflatedBytes(argumentCaptor.capture());
    TaskParameters taskParameters = (TaskParameters) argumentCaptor.getAllValues().get(0);
    assertThat(taskParameters).isInstanceOf(GitFetchRequest.class);
    GitFetchRequest gitFetchRequest = (GitFetchRequest) taskParameters;
    assertThat(gitFetchRequest.getGitFetchFilesConfigs()).isNotEmpty();
    assertThat(gitFetchRequest.getGitFetchFilesConfigs().size()).isEqualTo(3);
    List<GitFetchFilesConfig> gitFetchFilesConfigs = gitFetchRequest.getGitFetchFilesConfigs();
    assertThat(gitFetchFilesConfigs.get(0).getGitStoreDelegateConfig().getPaths()).isNotEmpty();
    assertThat(gitFetchFilesConfigs.get(0).getGitStoreDelegateConfig().getPaths().size()).isEqualTo(1);
    assertThat(gitFetchFilesConfigs.get(0).getGitStoreDelegateConfig().getPaths().get(0))
        .isEqualTo("path/to/k8s/manifest/patch3.yaml");
    assertThat(gitFetchFilesConfigs.get(1).getGitStoreDelegateConfig().getPaths()).isNotEmpty();
    assertThat(gitFetchFilesConfigs.get(1).getGitStoreDelegateConfig().getPaths().size()).isEqualTo(1);
    assertThat(gitFetchFilesConfigs.get(1).getGitStoreDelegateConfig().getPaths().get(0))
        .isEqualTo("path/to/k8s/manifest/patch2.yaml");
    assertThat(gitFetchFilesConfigs.get(2).getGitStoreDelegateConfig().getPaths()).isNotEmpty();
    assertThat(gitFetchFilesConfigs.get(2).getGitStoreDelegateConfig().getPaths().size()).isEqualTo(1);
    assertThat(gitFetchFilesConfigs.get(2).getGitStoreDelegateConfig().getPaths().get(0))
        .isEqualTo("path/to/k8s/manifest/patch1.yaml");
    assertThat(argumentCaptor.getAllValues().get(1)).isInstanceOf(GitConnectionNGCapability.class);

    // without Kustomize Patches
    Map<String, ManifestOutcome> manifestOutcomeMapOnlyTemplate =
        ImmutableMap.of("Kustomize", kustomizeManifestOutcome);
    OptionalOutcome manifestsOutcomeOnlyTemplate =
        OptionalOutcome.builder().found(true).outcome(new ManifestsOutcome(manifestOutcomeMapOnlyTemplate)).build();

    doReturn(manifestsOutcomeOnlyTemplate).when(outcomeService).resolveOptional(eq(ambiance), eq(manifests));
    assertThat(k8sStepHelper.startChainLink(k8sStepExecutor, ambiance, rollingStepElementParams)).isNotNull();
  }
}
