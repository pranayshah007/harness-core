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
import static io.harness.delegate.beans.connector.ConnectorType.OCI_HELM_REPO;
import static io.harness.logging.CommandExecutionStatus.FAILURE;
import static io.harness.logging.CommandExecutionStatus.SUCCESS;
import static io.harness.rule.OwnerRule.ABHINAV2;
import static io.harness.rule.OwnerRule.ABOSII;
import static io.harness.rule.OwnerRule.ACASIAN;
import static io.harness.rule.OwnerRule.ACHYUTH;
import static io.harness.rule.OwnerRule.ANSHUL;
import static io.harness.rule.OwnerRule.NAMAN_TALAYCHA;
import static io.harness.rule.OwnerRule.PRATYUSH;
import static io.harness.rule.OwnerRule.TARUN_UBA;
import static io.harness.rule.OwnerRule.VAIBHAV_SI;

import static java.lang.String.format;
import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.emptyMap;
import static java.util.Collections.singletonList;
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
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.FeatureName;
import io.harness.category.element.UnitTests;
import io.harness.cdng.CDStepHelper;
import io.harness.cdng.K8sHelmCommonStepHelper;
import io.harness.cdng.common.beans.SetupAbstractionKeys;
import io.harness.cdng.featureFlag.CDFeatureFlagHelper;
import io.harness.cdng.helm.HelmDeployStepParams;
import io.harness.cdng.infra.beans.K8sDirectInfrastructureOutcome;
import io.harness.cdng.infra.beans.K8sDirectInfrastructureOutcome.K8sDirectInfrastructureOutcomeBuilder;
import io.harness.cdng.k8s.beans.CustomFetchResponsePassThroughData;
import io.harness.cdng.k8s.beans.HelmValuesFetchResponsePassThroughData;
import io.harness.cdng.k8s.beans.K8sExecutionPassThroughData;
import io.harness.cdng.k8s.beans.StepExceptionPassThroughData;
import io.harness.cdng.manifest.ManifestConfigType;
import io.harness.cdng.manifest.steps.outcome.ManifestsOutcome;
import io.harness.cdng.manifest.yaml.CustomRemoteStoreConfig;
import io.harness.cdng.manifest.yaml.GcsStoreConfig;
import io.harness.cdng.manifest.yaml.GitStore;
import io.harness.cdng.manifest.yaml.GithubStore;
import io.harness.cdng.manifest.yaml.HelmChartManifestOutcome;
import io.harness.cdng.manifest.yaml.HelmCommandFlagType;
import io.harness.cdng.manifest.yaml.HelmManifestCommandFlag;
import io.harness.cdng.manifest.yaml.HttpStoreConfig;
import io.harness.cdng.manifest.yaml.InheritFromManifestStoreConfig;
import io.harness.cdng.manifest.yaml.InlineStoreConfig;
import io.harness.cdng.manifest.yaml.K8sCommandFlagType;
import io.harness.cdng.manifest.yaml.K8sManifestOutcome;
import io.harness.cdng.manifest.yaml.K8sStepCommandFlag;
import io.harness.cdng.manifest.yaml.KustomizeManifestOutcome;
import io.harness.cdng.manifest.yaml.KustomizePatchesManifestOutcome;
import io.harness.cdng.manifest.yaml.ManifestConfig;
import io.harness.cdng.manifest.yaml.ManifestConfigWrapper;
import io.harness.cdng.manifest.yaml.ManifestOutcome;
import io.harness.cdng.manifest.yaml.OciHelmChartConfig;
import io.harness.cdng.manifest.yaml.OciHelmChartStoreGenericConfig;
import io.harness.cdng.manifest.yaml.OpenshiftManifestOutcome;
import io.harness.cdng.manifest.yaml.OpenshiftParamManifestOutcome;
import io.harness.cdng.manifest.yaml.S3StoreConfig;
import io.harness.cdng.manifest.yaml.ValuesManifestOutcome;
import io.harness.cdng.manifest.yaml.harness.HarnessStore;
import io.harness.cdng.manifest.yaml.kinds.KustomizePatchesManifest;
import io.harness.cdng.manifest.yaml.kinds.ValuesManifest;
import io.harness.cdng.manifest.yaml.kinds.kustomize.OverlayConfiguration;
import io.harness.cdng.manifest.yaml.oci.OciHelmChartStoreConfigWrapper;
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
import io.harness.delegate.beans.connector.helm.OciHelmAuthType;
import io.harness.delegate.beans.connector.helm.OciHelmAuthenticationDTO;
import io.harness.delegate.beans.connector.helm.OciHelmConnectorDTO;
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
import io.harness.delegate.beans.storeconfig.CustomRemoteStoreDelegateConfig;
import io.harness.delegate.beans.storeconfig.FetchType;
import io.harness.delegate.beans.storeconfig.GcsHelmStoreDelegateConfig;
import io.harness.delegate.beans.storeconfig.GitStoreDelegateConfig;
import io.harness.delegate.beans.storeconfig.HttpHelmStoreDelegateConfig;
import io.harness.delegate.beans.storeconfig.LocalFileStoreDelegateConfig;
import io.harness.delegate.beans.storeconfig.OciHelmStoreDelegateConfig;
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
import io.harness.delegate.task.localstore.LocalStoreFetchFilesResult;
import io.harness.delegate.task.localstore.ManifestFiles;
import io.harness.delegate.task.manifests.request.CustomManifestValuesFetchParams;
import io.harness.delegate.task.manifests.response.CustomManifestValuesFetchResponse;
import io.harness.eventsframework.schemas.entity.EntityDetailProtoDTO;
import io.harness.exception.ExceptionUtils;
import io.harness.exception.GeneralException;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.KubernetesTaskException;
import io.harness.filestore.dto.node.FileNodeDTO;
import io.harness.filestore.dto.node.FileStoreNodeDTO;
import io.harness.filestore.dto.node.FolderNodeDTO;
import io.harness.filestore.service.FileStoreService;
import io.harness.filestore.utils.FileStoreNodeUtils;
import io.harness.git.model.FetchFilesResult;
import io.harness.git.model.GitFile;
import io.harness.helm.HelmSubCommandType;
import io.harness.k8s.model.HelmVersion;
import io.harness.k8s.model.KubernetesResourceId;
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
import io.harness.pms.contracts.execution.tasks.TaskRequest;
import io.harness.pms.contracts.plan.ExpressionMode;
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

import software.wings.beans.TaskType;

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
import org.powermock.api.mockito.PowerMockito;

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
  @Mock private FileStoreService fileStoreService;
  @Spy @InjectMocks private K8sEntityHelper k8sEntityHelper;
  @Spy @InjectMocks private CDStepHelper cdStepHelper;
  @Spy @InjectMocks private K8sStepHelper k8sStepHelper;

  @Mock private LogCallback mockLogCallback;
  private final Ambiance ambiance = Ambiance.newBuilder()
                                        .putSetupAbstractions(SetupAbstractionKeys.accountId, "test-account")
                                        .putSetupAbstractions(SetupAbstractionKeys.orgIdentifier, "test-org")
                                        .putSetupAbstractions(SetupAbstractionKeys.projectIdentifier, "test-project")
                                        .build();
  private static final String SOME_URL = "https://url.com/owner/repo.git";

  private final String ENCODED_REPO_NAME = "c26979e4-1d8c-344e-8181-45f484c57fe5";
  private final String NAMESPACE = "default";
  private final String INFRA_KEY = "svcId_envId";
  @Before
  public void setup() {
    doReturn(mockLogCallback).when(cdStepHelper).getLogCallback(anyString(), eq(ambiance), anyBoolean());
    doReturn(true)
        .when(cdFeatureFlagHelper)
        .isEnabled(AmbianceUtils.getAccountId(ambiance), FeatureName.USE_LATEST_CHARTMUSEUM_VERSION);
    doAnswer(invocation -> invocation.getArgument(1, String.class))
        .when(engineExpressionService)
        .renderExpression(eq(ambiance), anyString());
    Reflect.on(k8sStepHelper).set("cdStepHelper", cdStepHelper);
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

    K8sManifestOutcome k8sManifestOutcome = K8sManifestOutcome.builder().store(GitStore.builder().build()).build();
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
  @Owner(developers = NAMAN_TALAYCHA)
  @Category(UnitTests.class)
  public void testGetPrunedResourcesIds() {
    doReturn(true).when(cdFeatureFlagHelper).isEnabled(any(), any());
    List<KubernetesResourceId> prunedResourceIds = k8sStepHelper.getPrunedResourcesIds(true, null);
    assertThat(prunedResourceIds).isEmpty();
    List<KubernetesResourceId> kubernetesResourceIds =
        Collections.singletonList(KubernetesResourceId.builder().kind("Deployment").build());
    prunedResourceIds = k8sStepHelper.getPrunedResourcesIds(true, kubernetesResourceIds);
    assertThat(prunedResourceIds.get(0).getKind()).isEqualTo("Deployment");
  }

  @Test
  @Owner(developers = NAMAN_TALAYCHA)
  @Category(UnitTests.class)
  public void testResolveManifestsConfigExpressions() {
    List<ManifestConfigWrapper> manifestConfigWrappers = Arrays.asList(
        ManifestConfigWrapper.builder()
            .manifest(ManifestConfig.builder()
                          .identifier("identifier")
                          .type(ManifestConfigType.VALUES)
                          .spec(ValuesManifest.builder()
                                    .store(ParameterField.createValueField(
                                        StoreConfigWrapper.builder()
                                            .spec(GithubStore.builder()
                                                      .paths(ParameterField.createValueField(Arrays.asList(
                                                          "k8s/<+pipeline.variables.sample>/values.yaml")))
                                                      .build())
                                            .build()))
                                    .build())
                          .build())
            .build());
    k8sStepHelper.resolveManifestsConfigExpressions(ambiance, manifestConfigWrappers);
    verify(engineExpressionService)
        .renderExpression(eq(ambiance), eq("k8s/<+pipeline.variables.sample>/values.yaml"),
            eq(ExpressionMode.RETURN_ORIGINAL_EXPRESSION_IF_UNRESOLVED));
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testGetHelmChartManifestsOutcome() {
    HelmChartManifestOutcome helmChartManifestOutcome =
        HelmChartManifestOutcome.builder()
            .helmVersion(HelmVersion.V3)
            .skipResourceVersioning(ParameterField.createValueField(true))
            .store(GitStore.builder().build())
            .build();
    ValuesManifestOutcome valuesManifestOutcome = ValuesManifestOutcome.builder().build();
    List<ManifestOutcome> manifestOutcomes = new ArrayList<>();
    manifestOutcomes.add(helmChartManifestOutcome);
    manifestOutcomes.add(valuesManifestOutcome);

    assertThat(k8sStepHelper.getK8sSupportedManifestOutcome(manifestOutcomes)).isEqualTo(helmChartManifestOutcome);
  }

  @Test
  @Owner(developers = PRATYUSH)
  @Category(UnitTests.class)
  public void testGetOpenshiftManifestOutcomeWithHarnessAndInheritFromManifestStore() {
    OpenshiftManifestOutcome openshiftManifestOutcome =
        OpenshiftManifestOutcome.builder()
            .identifier("OcTemplate")
            .skipResourceVersioning(ParameterField.createValueField(true))
            .store(HarnessStore.builder().build())
            .build();
    ValuesManifestOutcome valuesManifestOutcome =
        ValuesManifestOutcome.builder().store(InheritFromManifestStoreConfig.builder().build()).build();
    List<ManifestOutcome> manifestOutcomes = new ArrayList<>();
    manifestOutcomes.add(openshiftManifestOutcome);
    manifestOutcomes.add(valuesManifestOutcome);

    assertThatThrownBy(() -> k8sStepHelper.getK8sSupportedManifestOutcome(manifestOutcomes))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage(
            "InheritFromManifest store type is not supported with Manifest identifier: OcTemplate, Manifest type: OpenshiftTemplate, Manifest store type: Harness");
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
    ManifestDelegateConfig delegateConfig = k8sStepHelper.getManifestDelegateConfig(manifestOutcome, ambiance);
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

    ManifestDelegateConfig delegateConfig = k8sStepHelper.getManifestDelegateConfig(manifestOutcome, ambiance);
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
  @Owner(developers = ABHINAV2)
  @Category(UnitTests.class)
  public void testShouldReturnEnableDeclarativeRollback() {
    boolean result = k8sStepHelper.isDeclarativeRollbackEnabled(
        K8sManifestOutcome.builder().enableDeclarativeRollback(ParameterField.createValueField(true)).build());
    assertThat(result).isTrue();
    result = k8sStepHelper.isDeclarativeRollbackEnabled(
        K8sManifestOutcome.builder().enableDeclarativeRollback(ParameterField.createValueField(false)).build());
    assertThat(result).isFalse();
    result = k8sStepHelper.isDeclarativeRollbackEnabled(
        HelmChartManifestOutcome.builder().enableDeclarativeRollback(ParameterField.createValueField(true)).build());
    assertThat(result).isTrue();
    result = k8sStepHelper.isDeclarativeRollbackEnabled(
        HelmChartManifestOutcome.builder().enableDeclarativeRollback(ParameterField.createValueField(false)).build());
    assertThat(result).isFalse();
    result = k8sStepHelper.isDeclarativeRollbackEnabled(
        KustomizeManifestOutcome.builder().enableDeclarativeRollback(ParameterField.createValueField(true)).build());
    assertThat(result).isTrue();
    result = k8sStepHelper.isDeclarativeRollbackEnabled(
        KustomizeManifestOutcome.builder().enableDeclarativeRollback(ParameterField.createValueField(false)).build());
    assertThat(result).isFalse();
    result = k8sStepHelper.isDeclarativeRollbackEnabled(
        OpenshiftManifestOutcome.builder().enableDeclarativeRollback(ParameterField.createValueField(true)).build());
    assertThat(result).isTrue();
    result = k8sStepHelper.isDeclarativeRollbackEnabled(
        OpenshiftManifestOutcome.builder().enableDeclarativeRollback(ParameterField.createValueField(false)).build());
    assertThat(result).isFalse();

    result = k8sStepHelper.isDeclarativeRollbackEnabled(ValuesManifestOutcome.builder().build());
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

    ManifestDelegateConfig delegateConfig = k8sStepHelper.getManifestDelegateConfig(manifestOutcome, ambiance);
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

    doReturn(Optional.of(ConnectorResponseDTO.builder()
                             .connector(ConnectorInfoDTO.builder()
                                            .identifier("http-helm-connector")
                                            .connectorType(HTTP_HELM_REPO)
                                            .connectorConfig(httpHelmConnectorConfig)
                                            .build())
                             .build()))
        .when(connectorService)
        .get(nullable(String.class), nullable(String.class), nullable(String.class), nullable(String.class));

    doReturn(K8sDirectInfrastructureOutcome.builder().namespace(NAMESPACE).infrastructureKey(INFRA_KEY).build())
        .when(cdStepHelper)
        .getInfrastructureOutcome(ambiance);

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
    ManifestDelegateConfig delegateConfig = k8sStepHelper.getManifestDelegateConfig(manifestOutcome, ambiance);
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
    assertThat(cdStepHelper.getInfrastructureOutcome(ambiance)).isEqualTo(outcome);

    doReturn(OptionalOutcome.builder().found(false).build())
        .when(outcomeService)
        .resolveOptional(
            ambiance, RefObjectUtils.getOutcomeRefObject(OutcomeExpressionConstants.INFRASTRUCTURE_OUTCOME));
    assertThatThrownBy(() -> cdStepHelper.getInfrastructureOutcome(ambiance))
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

    doReturn(Optional.of(ConnectorResponseDTO.builder()
                             .connector(ConnectorInfoDTO.builder()
                                            .identifier("aws-helm-connector")
                                            .connectorType(AWS)
                                            .connectorConfig(awsConnectorConfig)
                                            .build())
                             .build()))
        .when(connectorService)
        .get(nullable(String.class), nullable(String.class), nullable(String.class), nullable(String.class));

    doReturn(K8sDirectInfrastructureOutcome.builder().namespace(NAMESPACE).infrastructureKey(INFRA_KEY).build())
        .when(cdStepHelper)
        .getInfrastructureOutcome(ambiance);

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

    doReturn(Optional.of(ConnectorResponseDTO.builder()
                             .connector(ConnectorInfoDTO.builder()
                                            .identifier("gcp-helm-connector")
                                            .connectorType(GCP)
                                            .connectorConfig(gcpConnectorDTO)
                                            .build())
                             .build()))
        .when(connectorService)
        .get(nullable(String.class), nullable(String.class), nullable(String.class), nullable(String.class));

    doReturn(K8sDirectInfrastructureOutcome.builder().namespace(NAMESPACE).infrastructureKey(INFRA_KEY).build())
        .when(cdStepHelper)
        .getInfrastructureOutcome(ambiance);

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
        K8sDirectInfrastructureOutcome.builder().namespace(NAMESPACE).build();
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
  @Owner(developers = NAMAN_TALAYCHA)
  @Category(UnitTests.class)
  public void testApplyStepHarnessStoreOverride() {
    K8sDirectInfrastructureOutcome k8sDirectInfrastructureOutcome =
        K8sDirectInfrastructureOutcome.builder().namespace(NAMESPACE).build();
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

    StepElementParameters ApplyStepElementParams =
        StepElementParameters.builder()
            .spec(K8sApplyStepParameters.infoBuilder()
                      .overrides(
                          asList(ManifestConfigWrapper.builder()
                                     .manifest(ManifestConfig.builder()
                                                   .spec(ValuesManifest.builder()
                                                             .store(ParameterField.createValueField(
                                                                 StoreConfigWrapper.builder()
                                                                     .spec(HarnessStore.builder()
                                                                               .files(ParameterField.createValueField(
                                                                                   Arrays.asList("/values.yaml")))
                                                                               .build())
                                                                     .build()))
                                                             .build())
                                                   .build())
                                     .build()))
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
        .get(nullable(String.class), nullable(String.class), nullable(String.class), nullable(String.class));

    ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);

    doReturn(Optional.of(getFileStoreNode("path/to/k8s/values.yaml", "values.yaml")))
        .when(fileStoreService)
        .getWithChildrenByPath(any(), any(), any(), captor.capture(), eq(true));

    TaskChainResponse taskChainResponse =
        k8sStepHelper.startChainLink(k8sStepExecutor, ambiance, ApplyStepElementParams);
    assertThat(captor.getValue()).isNotNull();
    assertThat(captor.getValue()).isEqualTo("/values.yaml");
    assertThat(taskChainResponse.getPassThroughData()).isNotNull();
    assertThat(taskChainResponse.getPassThroughData()).isInstanceOf(K8sStepPassThroughData.class);
    K8sStepPassThroughData k8sStepPassThroughData = (K8sStepPassThroughData) taskChainResponse.getPassThroughData();
    assertThat(k8sStepPassThroughData.getValuesManifestOutcomes()).isNotEmpty();
    assertThat(k8sStepPassThroughData.getValuesManifestOutcomes().size()).isEqualTo(2);
  }

  @Test
  @Owner(developers = NAMAN_TALAYCHA)
  @Category(UnitTests.class)
  public void testStartChainLinkApplyStepOverrideKustomizePatch() {
    K8sDirectInfrastructureOutcome k8sDirectInfrastructureOutcome =
        K8sDirectInfrastructureOutcome.builder().namespace(NAMESPACE).build();
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

    StepElementParameters ApplyStepElementParams =
        StepElementParameters.builder()
            .spec(K8sApplyStepParameters.infoBuilder()
                      .overrides(
                          asList(ManifestConfigWrapper.builder()
                                     .manifest(ManifestConfig.builder()
                                                   .spec(KustomizePatchesManifest.builder()
                                                             .store(ParameterField.createValueField(
                                                                 StoreConfigWrapper.builder()
                                                                     .spec(HarnessStore.builder()
                                                                               .files(ParameterField.createValueField(
                                                                                   Arrays.asList("/patch3.yaml")))
                                                                               .build())
                                                                     .build()))
                                                             .build())
                                                   .build())
                                     .build()))
                      .build())
            .build();

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

    ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);

    doReturn(Optional.of(getFileStoreNode("path/to/k8s/manifest/patch3.yaml", "patch3.yaml")))
        .when(fileStoreService)
        .getWithChildrenByPath(any(), any(), any(), captor.capture(), eq(true));

    TaskChainResponse taskChainResponse =
        k8sStepHelper.startChainLink(k8sStepExecutor, ambiance, ApplyStepElementParams);
    assertThat(captor.getValue()).isNotNull();
    assertThat(captor.getValue()).isEqualTo("/patch3.yaml");
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
        .isEqualTo(4);
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
        K8sDirectInfrastructureOutcome.builder().namespace(NAMESPACE).build();

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
  @Owner(developers = PRATYUSH)
  @Category(UnitTests.class)
  public void testShouldPrepareK8sGitValuesFetchTaskWithValuesOverride() {
    K8sDirectInfrastructureOutcome k8sDirectInfrastructureOutcome =
        K8sDirectInfrastructureOutcome.builder().namespace(NAMESPACE).build();
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
    List<String> files = asList("org:/path/to/k8s/templates/deploy.yaml");
    HarnessStore harnessStore = HarnessStore.builder().files(ParameterField.createValueField(files)).build();
    ValuesManifestOutcome valuesManifestOutcome3 =
        ValuesManifestOutcome.builder().identifier("override3").store(harnessStore).build();
    Map<String, ManifestOutcome> manifestOutcomeMap = ImmutableMap.of("k8s", k8sManifestOutcome, "override1",
        valuesManifestOutcome1, "override2", valuesManifestOutcome2, "override3", valuesManifestOutcome3);
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

    doReturn(Optional.of(getFileStoreNode("path/to/k8s/values5.yaml", "values5.yaml")))
        .when(fileStoreService)
        .getWithChildrenByPath(any(), any(), any(), any(), eq(true));

    TaskChainResponse taskChainResponse =
        k8sStepHelper.startChainLink(k8sStepExecutor, ambiance, rollingStepElementParams);
    assertThat(taskChainResponse).isNotNull();
    assertThat(taskChainResponse.getPassThroughData()).isNotNull();
    assertThat(taskChainResponse.getPassThroughData()).isInstanceOf(K8sStepPassThroughData.class);
    K8sStepPassThroughData k8sStepPassThroughData = (K8sStepPassThroughData) taskChainResponse.getPassThroughData();
    assertThat(k8sStepPassThroughData.getValuesManifestOutcomes()).isNotEmpty();
    assertThat(k8sStepPassThroughData.getValuesManifestOutcomes().size()).isEqualTo(4);
    List<ValuesManifestOutcome> valuesManifestOutcome = k8sStepPassThroughData.getValuesManifestOutcomes();
    assertThat(valuesManifestOutcome.get(0).getIdentifier()).isEqualTo(k8sManifestOutcome.getIdentifier());
    assertThat(valuesManifestOutcome.get(0).getStore()).isEqualTo(k8sManifestOutcome.getStore());
    assertThat(valuesManifestOutcome.get(1).getIdentifier()).isEqualTo(valuesManifestOutcome1.getIdentifier());
    assertThat(valuesManifestOutcome.get(1).getStore()).isEqualTo(valuesManifestOutcome1.getStore());
    assertThat(valuesManifestOutcome.get(2).getIdentifier()).isEqualTo(valuesManifestOutcome2.getIdentifier());
    assertThat(valuesManifestOutcome.get(2).getStore()).isEqualTo(valuesManifestOutcome2.getStore());
    assertThat(valuesManifestOutcome.get(3).getIdentifier()).isEqualTo(valuesManifestOutcome3.getIdentifier());
    assertThat(valuesManifestOutcome.get(3).getStore()).isEqualTo(valuesManifestOutcome3.getStore());
    Map<String, LocalStoreFetchFilesResult> localStoreFetchFilesResultMap =
        k8sStepPassThroughData.getLocalStoreFileMapContents();
    assertThat(localStoreFetchFilesResultMap.size()).isEqualTo(1);
    assertThat(localStoreFetchFilesResultMap.get("override3").getLocalStoreFileContents().size()).isEqualTo(1);
    assertThat(localStoreFetchFilesResultMap.get("override3").getLocalStoreFileContents().get(0)).isEqualTo("Test");
    List<ManifestFiles> manifestFiles = k8sStepPassThroughData.getManifestFiles();
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
        K8sDirectInfrastructureOutcome.builder().namespace(NAMESPACE).build();
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
        k8sStepHelper.startChainLink(k8sStepExecutor, ambiance, stepElementParameters);
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
        K8sDirectInfrastructureOutcome.builder().namespace(NAMESPACE).build();
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
    ValuesManifestOutcome valuesManifestOutcome1 =
        ValuesManifestOutcome.builder().identifier("helmOverride").store(gitStore2).build();
    InheritFromManifestStoreConfig inheritFromManifestStore =
        InheritFromManifestStoreConfig.builder()
            .paths(ParameterField.createValueField(asList("path/to/helm/chart/values4.yaml")))
            .build();
    ValuesManifestOutcome valuesManifestOutcome2 =
        ValuesManifestOutcome.builder().identifier("helmOverride2").store(inheritFromManifestStore).build();
    List<String> files = asList("org:/folderPath/values5.yaml");
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
        k8sStepHelper.startChainLink(k8sStepExecutor, ambiance, stepElementParameters);
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
  @Owner(developers = PRATYUSH)
  @Category(UnitTests.class)
  public void testShouldPrepareHelmGitValuesFetchTaskWithHarnessStore() {
    List<String> files = asList("org:/path/to/helm/chart");
    K8sDirectInfrastructureOutcome k8sDirectInfrastructureOutcome =
        K8sDirectInfrastructureOutcome.builder().namespace(NAMESPACE).build();
    HarnessStore harnessStore = HarnessStore.builder().files(ParameterField.createValueField(files)).build();
    HelmChartManifestOutcome helmChartManifestOutcome =
        HelmChartManifestOutcome.builder()
            .identifier("helm")
            .store(harnessStore)
            .chartName(ParameterField.createValueField("Todolist"))
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
        StepElementParameters.builder().spec(K8sRollingStepParameters.infoBuilder().build()).build();
    OptionalOutcome manifestsOutcome =
        OptionalOutcome.builder().found(true).outcome(new ManifestsOutcome(manifestOutcomeMap)).build();
    doReturn(manifestsOutcome).when(outcomeService).resolveOptional(eq(ambiance), eq(manifests));
    doReturn(k8sDirectInfrastructureOutcome).when(outcomeService).resolve(eq(ambiance), eq(infra));

    doReturn(Optional.of(getFileStoreNode("path/to/helm/chart/values.yaml", "values.yaml")))
        .doReturn(Optional.of(getFileStoreNode("path/to/helm/chart/valuesOverride.yaml", "valuesOverride.yaml")))
        .doReturn(Optional.of(getFolderStoreNode("/path/to/helm/chart", "chart")))
        .when(fileStoreService)
        .getWithChildrenByPath(any(), any(), any(), any(), eq(true));

    MockedStatic fileStoreNodeUtils = mockStatic(FileStoreNodeUtils.class);
    ManifestFiles manifestFiles = ManifestFiles.builder()
                                      .filePath("/path/to/helm/chart/chart.yaml")
                                      .fileName("chart.yaml")
                                      .fileContent("Chart File")
                                      .build();
    PowerMockito.when(FileStoreNodeUtils.mapFileNodes(any(), any())).thenReturn(asList(manifestFiles));

    k8sStepHelper.startChainLink(k8sStepExecutor, ambiance, stepElementParameters);
    fileStoreNodeUtils.close();
    ArgumentCaptor<List> valuesFilesContentCaptor = ArgumentCaptor.forClass(List.class);
    verify(k8sStepExecutor, times(1))
        .executeK8sTask(eq(helmChartManifestOutcome), eq(ambiance), eq(stepElementParameters),
            valuesFilesContentCaptor.capture(),
            eq(K8sExecutionPassThroughData.builder()
                    .infrastructure(k8sDirectInfrastructureOutcome)
                    .manifestFiles(asList(manifestFiles))
                    .lastActiveUnitProgressData(null)
                    .build()),
            eq(false), eq(null));
    List<String> valuesFilesContent = valuesFilesContentCaptor.getValue();
    assertThat(valuesFilesContent).isNotEmpty();
    assertThat(valuesFilesContent.size()).isEqualTo(2);
  }

  @Test
  @Owner(developers = ACHYUTH)
  @Category(UnitTests.class)
  public void testShouldPrepareK8sCustomManifestValuesFetchTask() {
    String extractionScript = "git clone something.git";
    List<TaskSelectorYaml> delegateSelector = asList(new TaskSelectorYaml("sample-delegate"));
    CustomRemoteStoreConfig customRemoteStoreConfig =
        CustomRemoteStoreConfig.builder()
            .filePath(ParameterField.createValueField("folderPath/values.yaml"))
            .extractionScript(ParameterField.createValueField(extractionScript))
            .delegateSelectors(ParameterField.createValueField(delegateSelector))
            .build();

    K8sDirectInfrastructureOutcome k8sDirectInfrastructureOutcome =
        K8sDirectInfrastructureOutcome.builder().namespace(NAMESPACE).build();
    K8sManifestOutcome k8sManifestOutcome = K8sManifestOutcome.builder()
                                                .identifier("k8s")
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

    Map<String, ManifestOutcome> manifestOutcomeMap = ImmutableMap.of("k8s", k8sManifestOutcome);
    StepElementParameters stepElementParameters =
        StepElementParameters.builder()
            .spec(K8sRollingStepParameters.infoBuilder()
                      .delegateSelectors(ParameterField.createValueField(delegateSelector))
                      .build())
            .build();
    OptionalOutcome manifestsOutcome =
        OptionalOutcome.builder().found(true).outcome(new ManifestsOutcome(manifestOutcomeMap)).build();
    doReturn(manifestsOutcome).when(outcomeService).resolveOptional(eq(ambiance), eq(manifests));
    doReturn(k8sDirectInfrastructureOutcome).when(outcomeService).resolve(eq(ambiance), eq(infra));

    TaskChainResponse taskChainResponse =
        k8sStepHelper.startChainLink(k8sStepExecutor, ambiance, stepElementParameters);
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
  public void testShouldPrepareK8sCustomManifestValuesFetchTaskWithValuesOverride() {
    String extractionScript = "git clone something.git";
    List<TaskSelectorYaml> delegateSelector = asList(new TaskSelectorYaml("sample-delegate"));
    CustomRemoteStoreConfig customRemoteStoreConfig =
        CustomRemoteStoreConfig.builder()
            .filePath(ParameterField.createValueField("folderPath/values.yaml"))
            .extractionScript(ParameterField.createValueField(extractionScript))
            .delegateSelectors(ParameterField.createValueField(delegateSelector))
            .build();

    K8sDirectInfrastructureOutcome k8sDirectInfrastructureOutcome =
        K8sDirectInfrastructureOutcome.builder().namespace(NAMESPACE).build();

    GitStore gitStore = GitStore.builder()
                            .branch(ParameterField.createValueField("master"))
                            .paths(ParameterField.createValueField(asList("path/to/k8s")))
                            .connectorRef(ParameterField.createValueField("git-connector"))
                            .build();
    K8sManifestOutcome k8sManifestOutcome =
        K8sManifestOutcome.builder()
            .identifier("k8s")
            .store(gitStore)
            .valuesPaths(ParameterField.createValueField(asList("path/to/k8s/valuesOverride.yaml")))
            .build();
    ValuesManifestOutcome valuesManifestOutcome =
        ValuesManifestOutcome.builder().identifier("k8sOverride").store(customRemoteStoreConfig).build();

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
        ImmutableMap.of("k8s", k8sManifestOutcome, "k8sOverride", valuesManifestOutcome);
    StepElementParameters stepElementParameters =
        StepElementParameters.builder()
            .spec(K8sRollingStepParameters.infoBuilder()
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
        k8sStepHelper.startChainLink(k8sStepExecutor, ambiance, stepElementParameters);
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
  public void testShouldPrepareHelmS3ValuesFetchTask() {
    K8sDirectInfrastructureOutcome k8sDirectInfrastructureOutcome =
        K8sDirectInfrastructureOutcome.builder().namespace(NAMESPACE).build();
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

    doReturn(K8sDirectInfrastructureOutcome.builder().namespace(NAMESPACE).infrastructureKey(INFRA_KEY).build())
        .when(cdStepHelper)
        .getInfrastructureOutcome(ambiance);

    TaskChainResponse taskChainResponse =
        k8sStepHelper.startChainLink(k8sStepExecutor, ambiance, stepElementParameters);
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
        K8sDirectInfrastructureOutcome.builder().namespace(NAMESPACE).build();
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

    doReturn(K8sDirectInfrastructureOutcome.builder().namespace(NAMESPACE).infrastructureKey(INFRA_KEY).build())
        .when(cdStepHelper)
        .getInfrastructureOutcome(ambiance);

    TaskChainResponse taskChainResponse =
        k8sStepHelper.startChainLink(k8sStepExecutor, ambiance, stepElementParameters);
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
        K8sDirectInfrastructureOutcome.builder().namespace(NAMESPACE).build();
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

    doReturn(K8sDirectInfrastructureOutcome.builder().namespace(NAMESPACE).infrastructureKey(INFRA_KEY).build())
        .when(cdStepHelper)
        .getInfrastructureOutcome(ambiance);

    TaskChainResponse taskChainResponse =
        k8sStepHelper.startChainLink(k8sStepExecutor, ambiance, stepElementParameters);
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
        K8sDirectInfrastructureOutcome.builder().namespace(NAMESPACE).build();
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

    doReturn(Optional.of(getFileStoreNode("path/to/helm/chart/values5.yaml", "values5.yaml")))
        .when(fileStoreService)
        .getWithChildrenByPath(any(), any(), any(), any(), eq(true));

    doReturn(K8sDirectInfrastructureOutcome.builder().namespace(NAMESPACE).infrastructureKey(INFRA_KEY).build())
        .when(cdStepHelper)
        .getInfrastructureOutcome(ambiance);

    TaskChainResponse taskChainResponse =
        k8sStepHelper.startChainLink(k8sStepExecutor, ambiance, stepElementParameters);
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
        k8sStepHelper.startChainLink(k8sStepExecutor, ambiance, stepElementParameters);
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
  public void shouldHandleCustomManifestValuesFetchResponse() throws Exception {
    StepElementParameters stepElementParams =
        StepElementParameters.builder().spec(K8sRollingStepParameters.infoBuilder().build()).build();

    StoreConfig store = CustomRemoteStoreConfig.builder().build();
    HelmChartManifestOutcome helmChartManifestOutcome =
        HelmChartManifestOutcome.builder().identifier("id").store(store).build();
    K8sStepPassThroughData passThroughData = K8sStepPassThroughData.builder()
                                                 .manifestOutcome(helmChartManifestOutcome)
                                                 .infrastructure(K8sDirectInfrastructureOutcome.builder().build())
                                                 .shouldOpenFetchFilesStream(true)
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

    k8sStepHelper.executeNextLink(k8sStepExecutor, ambiance, stepElementParams, passThroughData, responseDataSuplier);

    K8sStepPassThroughData updatedK8sStepPassThroughData =
        passThroughData.toBuilder()
            .customFetchContent(customManifestValuesFetchResponse.getValuesFilesContentMap())
            .zippedManifestFileId(customManifestValuesFetchResponse.getZippedManifestFileId())
            .shouldOpenFetchFilesStream(false)
            .build();
    ArgumentCaptor<List> valuesFilesContentCaptor = ArgumentCaptor.forClass(List.class);
    verify(k8sStepExecutor, times(1))
        .executeK8sTask(eq(helmChartManifestOutcome), eq(ambiance), eq(stepElementParams),
            valuesFilesContentCaptor.capture(),
            eq(K8sExecutionPassThroughData.builder()
                    .infrastructure(updatedK8sStepPassThroughData.getInfrastructure())
                    .zippedManifestId(updatedK8sStepPassThroughData.getZippedManifestFileId())
                    .lastActiveUnitProgressData(null)
                    .build()),
            eq(updatedK8sStepPassThroughData.getShouldOpenFetchFilesStream()), eq(null));
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

    TaskChainResponse taskChainResponse = k8sStepHelper.executeNextLink(
        k8sStepExecutor, ambiance, stepElementParams, passThroughData, responseDataSuplier);

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

    doReturn(K8sDirectInfrastructureOutcome.builder().namespace(NAMESPACE).infrastructureKey(INFRA_KEY).build())
        .when(cdStepHelper)
        .getInfrastructureOutcome(ambiance);

    ValuesManifestOutcome valuesManifestOutcome =
        ValuesManifestOutcome.builder().identifier("helm").store(CustomRemoteStoreConfig.builder().build()).build();
    K8sStepPassThroughData passThroughData = K8sStepPassThroughData.builder()
                                                 .manifestOutcome(manifestOutcome)
                                                 .manifestOutcomeList(asList(valuesManifestOutcome))
                                                 .infrastructure(K8sDirectInfrastructureOutcome.builder().build())
                                                 .shouldOpenFetchFilesStream(true)
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

    TaskChainResponse taskChainResponse = k8sStepHelper.executeNextLink(
        k8sStepExecutor, ambiance, stepElementParams, passThroughData, responseDataSuplier);

    K8sStepPassThroughData updatedK8sStepPassThroughData =
        passThroughData.toBuilder()
            .manifestOutcomeList(asList(valuesManifestOutcome))
            .customFetchContent(customManifestValuesFetchResponse.getValuesFilesContentMap())
            .zippedManifestFileId(customManifestValuesFetchResponse.getZippedManifestFileId())
            .shouldOpenFetchFilesStream(false)
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
  public void testShouldPrepareHelmHttpValuesFetchTask() {
    K8sDirectInfrastructureOutcome k8sDirectInfrastructureOutcome =
        K8sDirectInfrastructureOutcome.builder().namespace(NAMESPACE).build();
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

    doReturn(K8sDirectInfrastructureOutcome.builder().namespace(NAMESPACE).infrastructureKey(INFRA_KEY).build())
        .when(cdStepHelper)
        .getInfrastructureOutcome(ambiance);

    TaskChainResponse taskChainResponse =
        k8sStepHelper.startChainLink(k8sStepExecutor, ambiance, stepElementParameters);
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
  public void testShouldPrepareHelmHttpValuesFetchTaskWithValuesOverride() {
    K8sDirectInfrastructureOutcome k8sDirectInfrastructureOutcome =
        K8sDirectInfrastructureOutcome.builder().namespace(NAMESPACE).build();
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

    doReturn(K8sDirectInfrastructureOutcome.builder().namespace(NAMESPACE).infrastructureKey(INFRA_KEY).build())
        .when(cdStepHelper)
        .getInfrastructureOutcome(ambiance);

    TaskChainResponse taskChainResponse =
        k8sStepHelper.startChainLink(k8sStepExecutor, ambiance, stepElementParameters);
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
  public void shouldHandleHelmValueFetchResponse() throws Exception {
    StepElementParameters rollingStepElementParams =
        StepElementParameters.builder().spec(K8sRollingStepParameters.infoBuilder().build()).build();

    K8sStepPassThroughData passThroughData = K8sStepPassThroughData.builder()
                                                 .manifestOutcome(K8sManifestOutcome.builder().build())
                                                 .infrastructure(K8sDirectInfrastructureOutcome.builder().build())
                                                 .shouldOpenFetchFilesStream(true)
                                                 .build();

    UnitProgressData unitProgressData = UnitProgressData.builder().build();
    HelmValuesFetchResponse helmValuesFetchResponse = HelmValuesFetchResponse.builder()
                                                          .valuesFileContent("values yaml payload")
                                                          .commandExecutionStatus(SUCCESS)
                                                          .unitProgressData(unitProgressData)
                                                          .build();
    List<ManifestFiles> manifestFilesList = asList(ManifestFiles.builder().build());
    Map<String, ResponseData> responseDataMap = ImmutableMap.of("helm-value-fetch-response", helmValuesFetchResponse);
    ThrowingSupplier responseDataSuplier = StrategyHelper.buildResponseDataSupplier(responseDataMap);

    k8sStepHelper.executeNextLink(
        k8sStepExecutor, ambiance, rollingStepElementParams, passThroughData, responseDataSuplier);

    ArgumentCaptor<List> valuesFilesContentCaptor = ArgumentCaptor.forClass(List.class);
    verify(k8sStepExecutor, times(1))
        .executeK8sTask(eq(passThroughData.getManifestOutcome()), eq(ambiance), eq(rollingStepElementParams),
            valuesFilesContentCaptor.capture(),
            eq(K8sExecutionPassThroughData.builder()
                    .infrastructure(passThroughData.getInfrastructure())
                    .manifestFiles(manifestFilesList)
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
    StepElementParameters rollingStepElementParams =
        StepElementParameters.builder().spec(K8sRollingStepParameters.infoBuilder().build()).build();

    String manifestIdentifier = "manifest-identifier";
    HelmFetchFileResult valuesYamlList =
        HelmFetchFileResult.builder().valuesFileContents(new ArrayList<>(asList("values yaml payload"))).build();
    Map<String, HelmFetchFileResult> helmChartValuesFileMapContent = new HashMap<>();
    helmChartValuesFileMapContent.put(manifestIdentifier, valuesYamlList);
    K8sStepPassThroughData passThroughData =
        K8sStepPassThroughData.builder()
            .manifestOutcome(K8sManifestOutcome.builder().identifier(manifestIdentifier).build())
            .infrastructure(K8sDirectInfrastructureOutcome.builder().build())
            .build();

    UnitProgressData unitProgressData = UnitProgressData.builder().build();
    HelmValuesFetchResponse helmValuesFetchResponse = HelmValuesFetchResponse.builder()
                                                          .helmChartValuesFileMapContent(helmChartValuesFileMapContent)
                                                          .commandExecutionStatus(SUCCESS)
                                                          .unitProgressData(unitProgressData)
                                                          .build();
    List<ManifestFiles> manifestFilesList = asList(ManifestFiles.builder().build());
    Map<String, ResponseData> responseDataMap = ImmutableMap.of("helm-value-fetch-response", helmValuesFetchResponse);
    ThrowingSupplier responseDataSuplier = StrategyHelper.buildResponseDataSupplier(responseDataMap);

    k8sStepHelper.executeNextLink(
        k8sStepExecutor, ambiance, rollingStepElementParams, passThroughData, responseDataSuplier);

    ArgumentCaptor<List> valuesFilesContentCaptor = ArgumentCaptor.forClass(List.class);
    verify(k8sStepExecutor, times(1))
        .executeK8sTask(eq(passThroughData.getManifestOutcome()), eq(ambiance), eq(rollingStepElementParams),
            valuesFilesContentCaptor.capture(),
            eq(K8sExecutionPassThroughData.builder()
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
    StepElementParameters rollingStepElementParams =
        StepElementParameters.builder().spec(K8sRollingStepParameters.infoBuilder().build()).build();

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
                                                 .shouldOpenFetchFilesStream(false)
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
        .executeK8sTask(eq(passThroughData.getManifestOutcome()), eq(ambiance), eq(rollingStepElementParams),
            valuesFilesContentCaptor.capture(),
            eq(K8sExecutionPassThroughData.builder()
                    .zippedManifestId(passThroughData.getZippedManifestFileId())
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
        ValuesManifestOutcome.builder().identifier("helmOverride1").store(gitStore2).build();
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
            .manifestOutcome(K8sManifestOutcome.builder().identifier(manifestIdentifier).build())
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
    doReturn(taskChainResponse).when(k8sStepHelper).executeValuesFetchTask(any(), any(), any(), any(), any());
    k8sStepHelper.executeNextLink(k8sStepExecutor, ambiance, stepElementParams, passThroughData, responseDataSuplier);

    ArgumentCaptor<Map> valuesFilesContentCaptor = ArgumentCaptor.forClass(Map.class);
    ArgumentCaptor<K8sStepPassThroughData> valuesFilesContentCaptor2 =
        ArgumentCaptor.forClass(K8sStepPassThroughData.class);
    verify(k8sStepHelper, times(1))
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
  public void shouldHandleGitFetchFilesResponseFromHandleHelmValueFetchResponse() throws Exception {
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

    Map<String, FetchFilesResult> filesFromMultipleRepo = new HashMap<>();
    filesFromMultipleRepo.put("helmOverride",
        FetchFilesResult.builder()
            .files(asList(
                GitFile.builder().fileContent("values yaml payload").filePath("folderPath/values2.yaml").build()))
            .build());

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

    K8sStepPassThroughData passThroughData = K8sStepPassThroughData.builder()
                                                 .manifestOutcome(helmChartManifestOutcome)
                                                 .infrastructure(K8sDirectInfrastructureOutcome.builder().build())
                                                 .manifestOutcomeList(new ArrayList<>(aggregatedValuesManifests))
                                                 .helmValuesFileMapContents(helmChartValuesFileMapContent)
                                                 .localStoreFileMapContents(localStoreFetchFilesResultMap)
                                                 .customFetchContent(valuesFilesContentMap)
                                                 .zippedManifestFileId("helmOverride4")
                                                 .shouldOpenFetchFilesStream(false)
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
        .executeK8sTask(eq(passThroughData.getManifestOutcome()), eq(ambiance), eq(stepElementParams),
            valuesFilesContentCaptor.capture(),
            eq(K8sExecutionPassThroughData.builder()
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
    List<ValuesManifestOutcome> aggregatedValuesManifests =
        K8sHelmCommonStepHelper.getAggregatedValuesManifests(manifestOutcome);
    LinkedList<ValuesManifestOutcome> orderedValuesManifests = new LinkedList<>(aggregatedValuesManifests);
    ValuesManifestOutcome valuesManifestOutcome =
        ValuesManifestOutcome.builder().identifier(helmChartManifestOutcome.getIdentifier()).store(gitStore).build();
    orderedValuesManifests.addFirst(valuesManifestOutcome);

    K8sStepPassThroughData passThroughData =
        K8sStepPassThroughData.builder()
            .manifestOutcome(K8sManifestOutcome.builder().identifier(manifestIdentifier).build())
            .infrastructure(K8sDirectInfrastructureOutcome.builder().build())
            .manifestOutcomeList(new ArrayList<>(orderedValuesManifests))
            .shouldOpenFetchFilesStream(true)
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
        .executeK8sTask(eq(passThroughData.getManifestOutcome()), eq(ambiance), eq(stepElementParams),
            valuesFilesContentCaptor.capture(),
            eq(K8sExecutionPassThroughData.builder()
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
        StepElementParameters.builder().spec(K8sRollingStepParameters.infoBuilder().build()).build();

    K8sStepPassThroughData passThroughData = K8sStepPassThroughData.builder()
                                                 .manifestOutcome(K8sManifestOutcome.builder().build())
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
                                                 .manifestOutcome(K8sManifestOutcome.builder().build())
                                                 .infrastructure(K8sDirectInfrastructureOutcome.builder().build())
                                                 .shouldOpenFetchFilesStream(true)
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
        .executeK8sTask(passThroughData.getManifestOutcome(), ambiance, rollingStepElementParams,
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
    assertThat(stepExceptionData.getErrorMessage())
        .isEqualTo(format("Error while fetching values yaml: %s", ExceptionUtils.getMessage(thrownException)));
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

    TaskChainResponse response = k8sStepHelper.executeNextLink(
        k8sStepExecutor, ambiance, rollingStepElementParams, passThroughData, responseDataSuplier);

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
        K8sDirectInfrastructureOutcome.builder().namespace(NAMESPACE).build();
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

    K8sDirectInfrastructureOutcome outcomeBuilder =
        K8sDirectInfrastructureOutcome.builder().connectorRef("abcConnector").namespace("valid").build();

    K8sManifestOutcome manifestOutcome = K8sManifestOutcome.builder().build();

    List<ValuesManifestOutcome> aggregatedValuesManifests = new ArrayList<>();

    Map<String, HelmFetchFileResult> helmChartFetchFilesResultMap = new HashMap<>();

    K8sStepPassThroughData k8sStepPassThroughData =
        K8sStepPassThroughData.builder().infrastructure(outcomeBuilder).manifestOutcome(manifestOutcome).build();

    assertThatCode(()
                       -> k8sStepHelper.executeValuesFetchTask(ambiance, stepElementParameters,
                           aggregatedValuesManifests, helmChartFetchFilesResultMap, k8sStepPassThroughData));
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

    K8sDirectInfrastructureOutcome outcomeBuilder =
        K8sDirectInfrastructureOutcome.builder().connectorRef("abcConnector").namespace("valid").build();

    K8sManifestOutcome manifestOutcome = K8sManifestOutcome.builder().build();

    List<OpenshiftParamManifestOutcome> openshiftParamManifests = new ArrayList<>();

    K8sStepPassThroughData k8sStepPassThroughData =
        K8sStepPassThroughData.builder().manifestOutcome(manifestOutcome).infrastructure(outcomeBuilder).build();

    assertThatCode(()
                       -> k8sStepHelper.prepareOpenshiftParamFetchTask(
                           ambiance, stepElementParameters, openshiftParamManifests, k8sStepPassThroughData));
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

    K8sStepPassThroughData k8sStepPassThroughData = K8sStepPassThroughData.builder()
                                                        .manifestOutcome(manifestOutcome)
                                                        .infrastructure(outcomeBuilder.build())
                                                        .build();

    assertThatCode(()
                       -> k8sStepHelper.prepareKustomizePatchesFetchTask(ambiance, k8sStepExecutor,
                           stepElementParameters, kustomizePatchesManifests, k8sStepPassThroughData));
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
                                                 .manifestOutcome(K8sManifestOutcome.builder().build())
                                                 .manifestOutcomeList(new ArrayList<>(valuesManifestOutcomeList))
                                                 .infrastructure(K8sDirectInfrastructureOutcome.builder().build())
                                                 .shouldOpenFetchFilesStream(true)
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
        .executeK8sTask(eq(passThroughData.getManifestOutcome()), eq(ambiance), eq(rollingStepElementParams), any(),
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
            .manifestOutcome(KustomizeManifestOutcome.builder().build())
            .manifestOutcomeList(new ArrayList<>(kustomizePatchesManifestOutcomeList))
            .infrastructure(K8sDirectInfrastructureOutcome.builder().build())
            .shouldOpenFetchFilesStream(true)
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

    cdStepHelper.getK8sInfraDelegateConfig(outcomeBuilder.build(), ambiance);

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
        K8sDirectInfrastructureOutcome.builder().namespace(NAMESPACE).build();
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
    List<String> files = asList("org:/path/to/k8s/manifest/param3.yaml");
    HarnessStore harnessStore = HarnessStore.builder().files(ParameterField.createValueField(files)).build();
    OpenshiftParamManifestOutcome openshiftParamManifestOutcome3 =
        OpenshiftParamManifestOutcome.builder().identifier("OpenShiftParam3").store(harnessStore).build();
    Map<String, ManifestOutcome> manifestOutcomeMap =
        ImmutableMap.of("OpenShift", openshiftManifestOutcome, "OpenShiftParam1", openshiftParamManifestOutcome1,
            "OpenShiftParam2", openshiftParamManifestOutcome2, "OpenShiftParam3", openshiftParamManifestOutcome3);
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

    doReturn(Optional.of(getFileStoreNode("path/to/k8s/manifest/param3.yaml", "param3.yaml")))
        .when(fileStoreService)
        .getWithChildrenByPath(any(), any(), any(), any(), eq(true));
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
    assertThat(getOpenshiftParamManifestOutcome(k8sStepPassThroughData.getManifestOutcomeList()).size()).isEqualTo(4);
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
    assertThat(openshiftParamManifestOutcome.get(3).getIdentifier())
        .isEqualTo(openshiftParamManifestOutcome3.getIdentifier());
    assertThat(openshiftParamManifestOutcome.get(3).getStore()).isEqualTo(openshiftParamManifestOutcome3.getStore());
    Map<String, LocalStoreFetchFilesResult> localStoreFetchFilesResultMap =
        k8sStepPassThroughData.getLocalStoreFileMapContents();
    assertThat(localStoreFetchFilesResultMap.get("OpenShiftParam3").getLocalStoreFileContents().size()).isEqualTo(1);
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
    openshiftManifestOutcome = OpenshiftManifestOutcome.builder().identifier("OpenShift").store(gitStore).build();
    Map<String, ManifestOutcome> manifestOutcomeMapOnlyTemplate =
        ImmutableMap.of("OpenShift", openshiftManifestOutcome);
    OptionalOutcome manifestsOutcomeOnlyTemplate =
        OptionalOutcome.builder().found(true).outcome(new ManifestsOutcome(manifestOutcomeMapOnlyTemplate)).build();

    doReturn(manifestsOutcomeOnlyTemplate).when(outcomeService).resolveOptional(eq(ambiance), eq(manifests));

    k8sStepHelper.startChainLink(k8sStepExecutor, ambiance, rollingStepElementParams);
    ArgumentCaptor<List> valuesFilesContentCaptor = ArgumentCaptor.forClass(List.class);
    verify(k8sStepExecutor, times(1))
        .executeK8sTask(eq(openshiftManifestOutcome), eq(ambiance), eq(rollingStepElementParams),
            valuesFilesContentCaptor.capture(),
            eq(K8sExecutionPassThroughData.builder()
                    .infrastructure(k8sDirectInfrastructureOutcome)
                    .manifestFiles(emptyList())
                    .lastActiveUnitProgressData(null)
                    .build()),
            eq(true), eq(null));
    List<String> valuesFilesContent = valuesFilesContentCaptor.getValue();
    assertThat(valuesFilesContent).isEmpty();
  }

  @Test
  @Owner(developers = ACHYUTH)
  @Category(UnitTests.class)
  public void testPrepareOcTemplateWithHarnessStore() {
    K8sDirectInfrastructureOutcome k8sDirectInfrastructureOutcome =
        K8sDirectInfrastructureOutcome.builder().namespace(NAMESPACE).build();
    HarnessStore harnessStore =
        HarnessStore.builder()
            .files(ParameterField.createValueField(asList("org:/path/to/k8s/manifest/template.yaml")))
            .build();
    OpenshiftManifestOutcome openshiftManifestOutcome = OpenshiftManifestOutcome.builder()
                                                            .identifier("OpenShift")
                                                            .store(harnessStore)
                                                            .paramsPaths(ParameterField.createValueField(null))
                                                            .build();

    Map<String, ManifestOutcome> manifestOutcomeMap = ImmutableMap.of("OpenShift", openshiftManifestOutcome);
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

    ManifestFiles manifestFiles = ManifestFiles.builder()
                                      .fileName("template.yaml")
                                      .filePath("path/to/k8s/manifest/template.yaml")
                                      .fileContent("Test")
                                      .build();
    doReturn(Optional.of(getFileStoreNode(manifestFiles.getFilePath(), manifestFiles.getFileName())))
        .when(fileStoreService)
        .getWithChildrenByPath(any(), any(), any(), any(), eq(true));
    k8sStepHelper.startChainLink(k8sStepExecutor, ambiance, rollingStepElementParams);
    ArgumentCaptor<List> valuesFilesContentCaptor = ArgumentCaptor.forClass(List.class);
    verify(k8sStepExecutor, times(1))
        .executeK8sTask(eq(openshiftManifestOutcome), eq(ambiance), eq(rollingStepElementParams),
            valuesFilesContentCaptor.capture(),
            eq(K8sExecutionPassThroughData.builder()
                    .infrastructure(k8sDirectInfrastructureOutcome)
                    .manifestFiles(asList(manifestFiles))
                    .lastActiveUnitProgressData(null)
                    .build()),
            eq(false), eq(null));
    List<String> valuesFilesContent = valuesFilesContentCaptor.getValue();
    assertThat(valuesFilesContent).isEmpty();
    assertThat(valuesFilesContent.size()).isEqualTo(0);
  }

  @Test
  @Owner(developers = ACHYUTH)
  @Category(UnitTests.class)
  public void testPrepareOcTemplateWithCustomRemoteStore() {
    String extractionScript = "git clone something.git";
    List<TaskSelectorYaml> delegateSelector = asList(new TaskSelectorYaml("sample-delegate"));
    K8sDirectInfrastructureOutcome k8sDirectInfrastructureOutcome =
        K8sDirectInfrastructureOutcome.builder().namespace(NAMESPACE).build();
    StoreConfig store = CustomRemoteStoreConfig.builder()
                            .filePath(ParameterField.createValueField("folderPath/template.yaml"))
                            .extractionScript(ParameterField.createValueField(extractionScript))
                            .delegateSelectors(ParameterField.createValueField(delegateSelector))
                            .build();
    OpenshiftManifestOutcome openshiftManifestOutcome = OpenshiftManifestOutcome.builder()
                                                            .identifier("OpenShift")
                                                            .store(store)
                                                            .paramsPaths(ParameterField.createValueField(null))
                                                            .build();

    Map<String, ManifestOutcome> manifestOutcomeMap = ImmutableMap.of("OpenShift", openshiftManifestOutcome);
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
        StepElementParameters.builder()
            .spec(
                K8sRollingStepParameters.infoBuilder().delegateSelectors(ParameterField.createValueField(null)).build())
            .build();
    OptionalOutcome manifestsOutcome =
        OptionalOutcome.builder().found(true).outcome(new ManifestsOutcome(manifestOutcomeMap)).build();

    doReturn(manifestsOutcome).when(outcomeService).resolveOptional(eq(ambiance), eq(manifests));
    doReturn(k8sDirectInfrastructureOutcome).when(outcomeService).resolve(eq(ambiance), eq(infra));

    doReturn(Optional.of(getFileStoreNode("path/to/k8s/manifest/template.yaml", "template.yaml")))
        .when(fileStoreService)
        .getWithChildrenByPath(any(), any(), any(), any(), eq(true));
    TaskChainResponse taskChainResponse =
        k8sStepHelper.startChainLink(k8sStepExecutor, ambiance, rollingStepElementParams);
    assertThat(taskChainResponse).isNotNull();
    assertThat(taskChainResponse.isChainEnd()).isEqualTo(false);
    assertThat(taskChainResponse.getTaskRequest().getDelegateTaskRequest().getTaskName())
        .isEqualTo(TaskType.CUSTOM_MANIFEST_VALUES_FETCH_TASK_NG.getDisplayName());
    assertThat(taskChainResponse.getTaskRequest().getDelegateTaskRequest().getLogKeys(0))
        .isEqualTo(
            "accountId:test-account/orgId:test-org/projectId:test-project/pipelineId:/runSequence:0-commandUnit:Fetch Files");
    assertThat(taskChainResponse.getPassThroughData()).isNotNull();
    assertThat(taskChainResponse.getPassThroughData()).isInstanceOf(K8sStepPassThroughData.class);
    K8sStepPassThroughData k8sStepPassThroughData = (K8sStepPassThroughData) taskChainResponse.getPassThroughData();
    Map<String, LocalStoreFetchFilesResult> localStoreFetchFilesResultMap =
        k8sStepPassThroughData.getLocalStoreFileMapContents();
    assertThat(localStoreFetchFilesResultMap).isEmpty();
    assertThat(k8sStepPassThroughData.getValuesManifestOutcomes()).isEmpty();
    assertThat(k8sStepPassThroughData.getCustomFetchContent()).isNull();
    assertThat(k8sStepPassThroughData.getZippedManifestFileId()).isNull();
    ArgumentCaptor<Object> argumentCaptor = ArgumentCaptor.forClass(Object.class);
    verify(kryoSerializer, times(1)).asDeflatedBytes(argumentCaptor.capture());
    TaskParameters taskParameters = (TaskParameters) argumentCaptor.getAllValues().get(0);
    assertThat(taskParameters).isInstanceOf(CustomManifestValuesFetchParams.class);
    CustomManifestValuesFetchParams customManifestValuesFetchRequest = (CustomManifestValuesFetchParams) taskParameters;
    assertThat(customManifestValuesFetchRequest.getCustomManifestSource().getAccountId()).isEqualTo("test-account");
    assertThat(customManifestValuesFetchRequest.getCustomManifestSource().getScript()).isEqualTo(extractionScript);
    assertThat(customManifestValuesFetchRequest.getCustomManifestSource().getFilePaths())
        .isEqualTo(asList("folderPath/template.yaml"));
    assertThat(customManifestValuesFetchRequest.getCustomManifestSource().getZippedManifestFileId()).isNull();
    assertThat(customManifestValuesFetchRequest.getFetchFilesList().size()).isEqualTo(0);
  }

  @Test
  @Owner(developers = PRATYUSH)
  @Category(UnitTests.class)
  public void shouldHandleCustomManifestValuesFetchResponseForOcTemplateWithGitStore() throws Exception {
    StepElementParameters rollingStepElementParams =
        StepElementParameters.builder().spec(K8sRollingStepParameters.infoBuilder().build()).build();
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

    StoreConfig store = GitStore.builder().connectorRef(ParameterField.createValueField("connectorRef")).build();
    OpenshiftParamManifestOutcome openshiftParamManifestOutcome = OpenshiftParamManifestOutcome.builder()
                                                                      .identifier("OpenshiftParam")
                                                                      .store(CustomRemoteStoreConfig.builder().build())
                                                                      .build();
    OpenshiftManifestOutcome openshiftManifestOutcome =
        OpenshiftManifestOutcome.builder()
            .store(store)
            .paramsPaths(ParameterField.createValueField(Collections.singletonList("path/to/param.yaml")))
            .build();
    K8sStepPassThroughData passThroughData = K8sStepPassThroughData.builder()
                                                 .manifestOutcome(openshiftManifestOutcome)
                                                 .manifestOutcomeList(asList(openshiftParamManifestOutcome))
                                                 .infrastructure(K8sDirectInfrastructureOutcome.builder().build())
                                                 .shouldOpenFetchFilesStream(true)
                                                 .build();
    Map<String, Collection<CustomSourceFile>> valuesFilesContentMap = new HashMap<>();
    valuesFilesContentMap.put("OpenshiftParam",
        asList(CustomSourceFile.builder().fileContent("param yaml payload").filePath("path/to/param.yaml").build()));
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

    TaskChainResponse taskChainResponse = k8sStepHelper.executeNextLink(
        k8sStepExecutor, ambiance, rollingStepElementParams, passThroughData, responseDataSuplier);

    K8sStepPassThroughData updatedK8sStepPassThroughData =
        passThroughData.toBuilder()
            .manifestOutcomeList(
                asList(OpenshiftParamManifestOutcome.builder().store(store).build(), openshiftParamManifestOutcome))
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

    // Without params path in Openshift Template manifest
    K8sDirectInfrastructureOutcome k8sDirectInfrastructureOutcome =
        K8sDirectInfrastructureOutcome.builder().namespace(NAMESPACE).build();
    openshiftManifestOutcome = OpenshiftManifestOutcome.builder().identifier("OpenShift").store(store).build();
    updatedK8sStepPassThroughData =
        passThroughData.toBuilder()
            .manifestOutcome(openshiftManifestOutcome)
            .manifestOutcomeList(singletonList(openshiftParamManifestOutcome))
            .customFetchContent(customManifestValuesFetchResponse.getValuesFilesContentMap())
            .zippedManifestFileId(customManifestValuesFetchResponse.getZippedManifestFileId())
            .manifestFiles(emptyList())
            .shouldOpenFetchFilesStream(false)
            .infrastructure(k8sDirectInfrastructureOutcome)
            .build();

    k8sStepHelper.executeNextLink(
        k8sStepExecutor, ambiance, rollingStepElementParams, updatedK8sStepPassThroughData, responseDataSuplier);
    ArgumentCaptor<List> valuesFilesContentCaptor = ArgumentCaptor.forClass(List.class);
    verify(k8sStepExecutor, times(1))
        .executeK8sTask(eq(openshiftManifestOutcome), eq(ambiance), eq(rollingStepElementParams),
            valuesFilesContentCaptor.capture(),
            eq(K8sExecutionPassThroughData.builder()
                    .infrastructure(k8sDirectInfrastructureOutcome)
                    .manifestFiles(emptyList())
                    .lastActiveUnitProgressData(null)
                    .zippedManifestId("zip")
                    .build()),
            eq(false), eq(null));
    List<String> valuesFilesContent = valuesFilesContentCaptor.getValue();
    assertThat(valuesFilesContent).isEqualTo(singletonList("param yaml payload"));
  }

  @Test
  @Owner(developers = PRATYUSH)
  @Category(UnitTests.class)
  public void shouldHandleCustomManifestValuesFetchResponseForOcTemplate() throws Exception {
    StepElementParameters rollingStepElementParams =
        StepElementParameters.builder().spec(K8sRollingStepParameters.infoBuilder().build()).build();
    K8sDirectInfrastructureOutcome k8sDirectInfrastructureOutcome = K8sDirectInfrastructureOutcome.builder().build();
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

    StoreConfig store = GitStore.builder().connectorRef(ParameterField.createValueField("connectorRef")).build();
    OpenshiftManifestOutcome openshiftManifestOutcome =
        OpenshiftManifestOutcome.builder()
            .identifier("OpenshiftTemplate")
            .store(CustomRemoteStoreConfig.builder().build())
            .paramsPaths(ParameterField.createValueField(asList("path/to/param.yaml")))
            .build();
    K8sStepPassThroughData passThroughData = K8sStepPassThroughData.builder()
                                                 .manifestOutcome(openshiftManifestOutcome)
                                                 .manifestOutcomeList(null)
                                                 .infrastructure(k8sDirectInfrastructureOutcome)
                                                 .shouldOpenFetchFilesStream(true)
                                                 .build();
    Map<String, Collection<CustomSourceFile>> valuesFilesContentMap = new HashMap<>();
    valuesFilesContentMap.put("OpenshiftTemplate",
        asList(CustomSourceFile.builder().fileContent("param yaml payload").filePath("path/to/param.yaml").build()));
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

    k8sStepHelper.executeNextLink(
        k8sStepExecutor, ambiance, rollingStepElementParams, passThroughData, responseDataSuplier);

    ArgumentCaptor<List> valuesFilesContentCaptor = ArgumentCaptor.forClass(List.class);
    verify(k8sStepExecutor, times(1))
        .executeK8sTask(eq(openshiftManifestOutcome), eq(ambiance), eq(rollingStepElementParams),
            valuesFilesContentCaptor.capture(),
            eq(K8sExecutionPassThroughData.builder()
                    .infrastructure(k8sDirectInfrastructureOutcome)
                    .lastActiveUnitProgressData(null)
                    .zippedManifestId("zip")
                    .build()),
            eq(false), eq(null));
    List<String> valuesFilesContent = valuesFilesContentCaptor.getValue();
    assertThat(valuesFilesContent).isNotEmpty();
    assertThat(valuesFilesContent.size()).isEqualTo(1);
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
        K8sDirectInfrastructureOutcome.builder().namespace(NAMESPACE).build();
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
    kustomizeManifestOutcome = KustomizeManifestOutcome.builder().identifier("Kustomize").store(gitStore).build();
    Map<String, ManifestOutcome> manifestOutcomeMapOnlyTemplate =
        ImmutableMap.of("Kustomize", kustomizeManifestOutcome);
    OptionalOutcome manifestsOutcomeOnlyTemplate =
        OptionalOutcome.builder().found(true).outcome(new ManifestsOutcome(manifestOutcomeMapOnlyTemplate)).build();

    doReturn(manifestsOutcomeOnlyTemplate).when(outcomeService).resolveOptional(eq(ambiance), eq(manifests));
    k8sStepHelper.startChainLink(k8sStepExecutor, ambiance, rollingStepElementParams);
    ArgumentCaptor<List> valuesFilesContentCaptor = ArgumentCaptor.forClass(List.class);
    verify(k8sStepExecutor, times(1))
        .executeK8sTask(eq(kustomizeManifestOutcome), eq(ambiance), eq(rollingStepElementParams),
            valuesFilesContentCaptor.capture(),
            eq(K8sExecutionPassThroughData.builder()
                    .infrastructure(k8sDirectInfrastructureOutcome)
                    .manifestFiles(emptyList())
                    .lastActiveUnitProgressData(null)
                    .build()),
            eq(true), eq(null));
    List<String> valuesFilesContent = valuesFilesContentCaptor.getValue();
    assertThat(valuesFilesContent).isEmpty();
  }

  @Test
  @Owner(developers = PRATYUSH)
  @Category(UnitTests.class)
  public void testStartChainLinkKustomizeWithHarnessStore() {
    K8sDirectInfrastructureOutcome k8sDirectInfrastructureOutcome =
        K8sDirectInfrastructureOutcome.builder().namespace(NAMESPACE).build();
    HarnessStore harnessStore =
        HarnessStore.builder().files(ParameterField.createValueField(asList("/path/to/kustomize"))).build();
    OverlayConfiguration overlayConfiguration =
        OverlayConfiguration.builder()
            .kustomizeYamlFolderPath(ParameterField.createValueField("/path/to/kustomize/kustomization"))
            .build();
    KustomizeManifestOutcome kustomizeManifestOutcome =
        KustomizeManifestOutcome.builder()
            .identifier("Kustomize")
            .store(harnessStore)
            .patchesPaths(ParameterField.createValueField(null))
            .overlayConfiguration(ParameterField.createValueField(overlayConfiguration))
            .build();
    Map<String, ManifestOutcome> manifestOutcomeMap = ImmutableMap.of("Kustomize", kustomizeManifestOutcome);
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
    ManifestFiles manifestFiles =
        ManifestFiles.builder().fileName("kustomize").filePath("/path/to/kustomize").fileContent("Test").build();
    doReturn(Optional.of(getFolderStoreNode("/path/to/kustomize", "kustomize")))
        .when(fileStoreService)
        .getWithChildrenByPath(any(), any(), any(), any(), eq(true));
    MockedStatic fileStoreNodeUtils = mockStatic(FileStoreNodeUtils.class);
    PowerMockito.when(FileStoreNodeUtils.mapFileNodes(any(), any())).thenReturn(asList(manifestFiles));
    k8sStepHelper.startChainLink(k8sStepExecutor, ambiance, rollingStepElementParams);
    fileStoreNodeUtils.close();
    ArgumentCaptor<List> valuesFilesContentCaptor = ArgumentCaptor.forClass(List.class);
    verify(k8sStepExecutor, times(1))
        .executeK8sTask(eq(kustomizeManifestOutcome), eq(ambiance), eq(rollingStepElementParams),
            valuesFilesContentCaptor.capture(),
            eq(K8sExecutionPassThroughData.builder()
                    .infrastructure(k8sDirectInfrastructureOutcome)
                    .manifestFiles(asList(manifestFiles))
                    .lastActiveUnitProgressData(null)
                    .build()),
            eq(false), eq(null));
    List<String> valuesFilesContent = valuesFilesContentCaptor.getValue();
    assertThat(valuesFilesContent).isEmpty();
    assertThat(valuesFilesContent.size()).isEqualTo(0);
  }

  @Test
  @Owner(developers = TARUN_UBA)
  @Category(UnitTests.class)
  public void testGetDelegateK8sCommandFlag() {
    List<K8sStepCommandFlag> commandFlags =
        Collections.singletonList(K8sStepCommandFlag.builder()
                                      .commandType(K8sCommandFlagType.Apply)
                                      .flag(ParameterField.createValueField("--server-side"))
                                      .build());
    Map<String, String> k8sCommandFlagExpected = ImmutableMap.of("Apply", "--server-side");
    Map<String, String> k8sCommandFlag = k8sStepHelper.getDelegateK8sCommandFlag(commandFlags);
    assertThat(k8sCommandFlag).isEqualTo(k8sCommandFlagExpected);
  }
  @Test
  @Owner(developers = TARUN_UBA)
  @Category(UnitTests.class)
  public void testExecuteNextLinkInternalStepExceptionCommitId() throws Exception {
    Map<String, String> commitIdsMap = Collections.singletonMap("Service", "CommitId");
    StepElementParameters rollingStepElementParams =
        StepElementParameters.builder().spec(K8sRollingStepParameters.infoBuilder().build()).build();
    UnitProgressData unitProgressData =
        UnitProgressData.builder()
            .unitProgresses(
                asList(UnitProgress.newBuilder().setUnitName("Fetch Files").setStatus(UnitStatus.RUNNING).build(),
                    UnitProgress.newBuilder().setUnitName("Some Unit").setStatus(UnitStatus.SUCCESS).build()))
            .build();
    K8sStepPassThroughData passThroughData = K8sStepPassThroughData.builder()
                                                 .manifestOutcome(K8sManifestOutcome.builder().build())
                                                 .infrastructure(K8sDirectInfrastructureOutcome.builder().build())
                                                 .shouldOpenFetchFilesStream(true)
                                                 .build();
    K8sGitFetchInfo k8sGitFetchInfo = getK8sGitFetchInfo(commitIdsMap);
    GitFetchResponse gitFetchResponse = GitFetchResponse.builder()
                                            .filesFromMultipleRepo(Collections.emptyMap())
                                            .taskStatus(TaskStatus.SUCCESS)
                                            .unitProgressData(unitProgressData)
                                            .fetchedCommitIdsMap(commitIdsMap)
                                            .build();
    Map<String, ResponseData> responseDataMap = ImmutableMap.of("git-fetch-response", gitFetchResponse);
    ThrowingSupplier responseDataSuplier = StrategyHelper.buildResponseDataSupplier(responseDataMap);
    ArgumentCaptor<K8sExecutionPassThroughData> k8sExecutionPassThroughDataCaptor =
        ArgumentCaptor.forClass(K8sExecutionPassThroughData.class);
    k8sStepHelper.executeNextLink(
        k8sStepExecutor, ambiance, rollingStepElementParams, passThroughData, responseDataSuplier);
    when(k8sStepExecutor.executeK8sTask(any(), any(), any(), any(), any(), anyBoolean(), any()))
        .thenReturn(TaskChainResponse.builder().chainEnd(true).build());
    verify(k8sStepExecutor, times(1))
        .executeK8sTask(any(), any(), any(), any(), k8sExecutionPassThroughDataCaptor.capture(), anyBoolean(), any());
    assertThat(k8sExecutionPassThroughDataCaptor.getValue().getK8sGitFetchInfo()).isEqualTo(k8sGitFetchInfo);
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
    doReturn(taskChainResponse).when(k8sStepHelper).executeValuesFetchTask(any(), any(), any(), any(), any());
    k8sStepHelper.executeNextLink(k8sStepExecutor, ambiance, stepElementParams, passThroughData, responseDataSupplier);

    ValuesManifestOutcome valuesManifestOutcome = ValuesManifestOutcome.builder()
                                                      .identifier(helmChartManifestOutcome.getIdentifier())
                                                      .store(harnessStore)
                                                      .build();
    LinkedList<ValuesManifestOutcome> orderedValuesManifests = new LinkedList<>(aggregatedValuesManifests);
    orderedValuesManifests.addFirst(valuesManifestOutcome);
    ArgumentCaptor<K8sStepPassThroughData> valuesFilesContentCaptor2 =
        ArgumentCaptor.forClass(K8sStepPassThroughData.class);
    verify(k8sStepHelper, times(1))
        .executeValuesFetchTask(eq(ambiance), eq(stepElementParams), eq(orderedValuesManifests), eq(emptyMap()),
            valuesFilesContentCaptor2.capture());

    K8sStepPassThroughData nativeHelmStepPassThroughData = valuesFilesContentCaptor2.getValue();
    assertThat(nativeHelmStepPassThroughData.getLocalStoreFileMapContents().size()).isEqualTo(1);
    assertThat(nativeHelmStepPassThroughData.getLocalStoreFileMapContents().get("helmOverride3"))
        .isEqualTo(localStoreFetchFilesResult);
    assertThat(nativeHelmStepPassThroughData.getCustomFetchContent().get("helmOverride2"))
        .isEqualTo(valuesFilesContentMap.get("helmOverride2"));
  }

  @Test
  @Owner(developers = TARUN_UBA)
  @Category(UnitTests.class)
  public void testStepLevelInlineOverridesForHarnessStore() throws Exception {
    HarnessStore harnessStore =
        HarnessStore.builder()
            .files(ParameterField.createValueField(asList("org:/path/to/k8s/manifest/template.yaml")))
            .build();
    InlineStoreConfig inlineStore =
        InlineStoreConfig.builder().content(ParameterField.createValueField("replicaCount: 3")).build();

    K8sApplyStepParameters applyStepParams = new K8sApplyStepParameters();
    applyStepParams.setSkipDryRun(ParameterField.ofNull());
    applyStepParams.setSkipSteadyStateCheck(ParameterField.ofNull());
    applyStepParams.setOverrides(
        Arrays.asList(ManifestConfigWrapper.builder()
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
        K8sDirectInfrastructureOutcome.builder().namespace(NAMESPACE).build();

    K8sManifestOutcome k8sManifestOutcome = K8sManifestOutcome.builder().identifier("k8s").store(harnessStore).build();

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
    ManifestFiles manifestFiles = ManifestFiles.builder()
                                      .fileName("template.yaml")
                                      .filePath("path/to/k8s/manifest/template.yaml")
                                      .fileContent("Test")
                                      .build();

    doReturn(Optional.of(getFileStoreNode(manifestFiles.getFilePath(), manifestFiles.getFileName())))
        .when(fileStoreService)
        .getWithChildrenByPath(any(), any(), any(), any(), eq(true));
    doReturn(manifestsOutcome).when(outcomeService).resolveOptional(eq(ambiance), eq(manifests));
    doReturn(k8sDirectInfrastructureOutcome).when(outcomeService).resolve(eq(ambiance), eq(infra));
    k8sStepHelper.startChainLink(k8sStepExecutor, ambiance, stepElementParametersApplyStep);
    ArgumentCaptor<List> valuesFilesContentCaptor = ArgumentCaptor.forClass(List.class);
    verify(k8sStepExecutor, times(1))
        .executeK8sTask(eq(k8sManifestOutcome), eq(ambiance), eq(stepElementParametersApplyStep),
            valuesFilesContentCaptor.capture(),
            eq(K8sExecutionPassThroughData.builder()
                    .infrastructure(k8sDirectInfrastructureOutcome)
                    .manifestFiles(asList(manifestFiles))
                    .lastActiveUnitProgressData(null)
                    .build()),
            eq(false), eq(null));
    List<String> valuesFilesContent = valuesFilesContentCaptor.getValue();
    assertThat(valuesFilesContent.size()).isEqualTo(2);
    assertThat(valuesFilesContent.get(1)).isEqualTo("replicaCount: 3");
  }

  @Test
  @Owner(developers = TARUN_UBA)
  @Category(UnitTests.class)
  public void testStepLevelGitOverridesForHarnessStore() throws Exception {
    HarnessStore harnessStore =
        HarnessStore.builder()
            .files(ParameterField.createValueField(asList("org:/path/to/k8s/manifest/template.yaml")))
            .build();
    GitStore gitStoreStepLevel =
        GitStore.builder()
            .branch(ParameterField.createValueField("master"))
            .paths(ParameterField.createValueField(asList("path/to/k8s/manifest/step-values.yaml")))
            .connectorRef(ParameterField.createValueField("git-connector"))
            .build();

    K8sApplyStepParameters applyStepParams = new K8sApplyStepParameters();
    applyStepParams.setSkipDryRun(ParameterField.ofNull());
    applyStepParams.setSkipSteadyStateCheck(ParameterField.ofNull());
    applyStepParams.setOverrides(
        Arrays.asList(ManifestConfigWrapper.builder()
                          .manifest(ManifestConfig.builder()
                                        .spec(ValuesManifest.builder()
                                                  .store(ParameterField.createValueField(
                                                      StoreConfigWrapper.builder().spec(gitStoreStepLevel).build()))
                                                  .build())
                                        .build())
                          .build()));

    StepElementParameters stepElementParametersApplyStep =
        StepElementParameters.builder().spec(applyStepParams).build();

    K8sDirectInfrastructureOutcome k8sDirectInfrastructureOutcome =
        K8sDirectInfrastructureOutcome.builder().namespace(NAMESPACE).build();

    K8sManifestOutcome k8sManifestOutcome = K8sManifestOutcome.builder().identifier("k8s").store(harnessStore).build();

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

    ManifestFiles manifestFiles = ManifestFiles.builder()
                                      .fileName("template.yaml")
                                      .filePath("path/to/k8s/manifest/template.yaml")
                                      .fileContent("Test")
                                      .build();
    doReturn(Optional.of(getFileStoreNode(manifestFiles.getFilePath(), manifestFiles.getFileName())))
        .when(fileStoreService)
        .getWithChildrenByPath(any(), any(), any(), any(), eq(true));
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
    doReturn(manifestsOutcome).when(outcomeService).resolveOptional(eq(ambiance), eq(manifests));
    doReturn(k8sDirectInfrastructureOutcome).when(outcomeService).resolve(eq(ambiance), eq(infra));
    TaskChainResponse taskChainResponse =
        k8sStepHelper.startChainLink(k8sStepExecutor, ambiance, stepElementParametersApplyStep);
    K8sStepPassThroughData k8sStepPassThroughData = (K8sStepPassThroughData) taskChainResponse.getPassThroughData();
    assertThat(k8sStepPassThroughData.getManifestOutcomeList().size()).isEqualTo(2);
    assertThat(k8sStepPassThroughData.getManifestOutcomeList().get(1).getType()).isEqualTo("Values");
    assertThat(k8sStepPassThroughData.getManifestOutcomeList().get(1).getStore().getKind()).isEqualTo("Git");
    assertThat(
        ((GitStore) k8sStepPassThroughData.getManifestOutcomeList().get(1).getStore()).getPaths().getValue().get(0))
        .isEqualTo("path/to/k8s/manifest/step-values.yaml");
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

  private K8sGitFetchInfo getK8sGitFetchInfo(Map<String, String> commitIdsMap) {
    K8sGitFetchInfo k8sGitFetchInfo = K8sGitFetchInfo.builder().build();
    Map<String, K8sGitInfo> variables = new HashMap<>();
    commitIdsMap.forEach(
        (String keys, String values) -> { variables.put(keys, K8sGitInfo.builder().commitId(values).build()); });
    k8sGitFetchInfo.putAll(variables);
    return k8sGitFetchInfo;
  }
}
