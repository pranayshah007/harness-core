/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.artifact.steps;

import static io.harness.cdng.artifact.steps.ArtifactsStepV2.ARTIFACTS_STEP_V_2;
import static io.harness.data.structure.CollectionUtils.emptyIfNull;
import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.exception.WingsException.USER;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.anySet;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.powermock.api.mockito.PowerMockito.mock;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.DelegateTaskRequest;
import io.harness.category.element.UnitTests;
import io.harness.cdng.CDNGTestBase;
import io.harness.cdng.CDStepHelper;
import io.harness.cdng.artifact.bean.ArtifactConfig;
import io.harness.cdng.artifact.bean.yaml.AMIArtifactConfig;
import io.harness.cdng.artifact.bean.yaml.AcrArtifactConfig;
import io.harness.cdng.artifact.bean.yaml.AmazonS3ArtifactConfig;
import io.harness.cdng.artifact.bean.yaml.ArtifactListConfig;
import io.harness.cdng.artifact.bean.yaml.ArtifactSource;
import io.harness.cdng.artifact.bean.yaml.AzureArtifactsConfig;
import io.harness.cdng.artifact.bean.yaml.CustomArtifactConfig;
import io.harness.cdng.artifact.bean.yaml.DockerHubArtifactConfig;
import io.harness.cdng.artifact.bean.yaml.EcrArtifactConfig;
import io.harness.cdng.artifact.bean.yaml.GcrArtifactConfig;
import io.harness.cdng.artifact.bean.yaml.GithubPackagesArtifactConfig;
import io.harness.cdng.artifact.bean.yaml.GoogleArtifactRegistryConfig;
import io.harness.cdng.artifact.bean.yaml.NexusRegistryArtifactConfig;
import io.harness.cdng.artifact.bean.yaml.PrimaryArtifact;
import io.harness.cdng.artifact.bean.yaml.SidecarArtifact;
import io.harness.cdng.artifact.bean.yaml.SidecarArtifactWrapper;
import io.harness.cdng.artifact.bean.yaml.customartifact.CustomArtifactScriptInfo;
import io.harness.cdng.artifact.bean.yaml.customartifact.CustomArtifactScriptSourceWrapper;
import io.harness.cdng.artifact.bean.yaml.customartifact.CustomArtifactScripts;
import io.harness.cdng.artifact.bean.yaml.customartifact.CustomScriptInlineSource;
import io.harness.cdng.artifact.bean.yaml.customartifact.FetchAllArtifacts;
import io.harness.cdng.artifact.outcome.ArtifactsOutcome;
import io.harness.cdng.artifact.steps.constants.ArtifactsStepV2Constants;
import io.harness.cdng.artifact.utils.ArtifactStepHelper;
import io.harness.cdng.artifact.utils.ArtifactUtils;
import io.harness.cdng.common.beans.SetupAbstractionKeys;
import io.harness.cdng.expressions.CDExpressionResolver;
import io.harness.cdng.service.beans.KubernetesServiceSpec;
import io.harness.cdng.service.beans.ServiceDefinition;
import io.harness.cdng.service.beans.ServiceDefinitionType;
import io.harness.cdng.service.steps.helpers.ServiceStepsHelper;
import io.harness.cdng.steps.EmptyStepParameters;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.connector.ConnectorResponseDTO;
import io.harness.connector.services.ConnectorService;
import io.harness.data.structure.UUIDGenerator;
import io.harness.delegate.beans.ErrorNotifyResponseData;
import io.harness.delegate.beans.connector.docker.DockerAuthType;
import io.harness.delegate.beans.connector.docker.DockerAuthenticationDTO;
import io.harness.delegate.beans.connector.docker.DockerConnectorDTO;
import io.harness.delegate.task.artifacts.ArtifactSourceType;
import io.harness.delegate.task.artifacts.ArtifactTaskType;
import io.harness.delegate.task.artifacts.custom.CustomArtifactDelegateRequest;
import io.harness.delegate.task.artifacts.docker.DockerArtifactDelegateRequest;
import io.harness.delegate.task.artifacts.docker.DockerArtifactDelegateResponse;
import io.harness.delegate.task.artifacts.request.ArtifactTaskParameters;
import io.harness.delegate.task.artifacts.response.ArtifactBuildDetailsNG;
import io.harness.delegate.task.artifacts.response.ArtifactTaskExecutionResponse;
import io.harness.delegate.task.artifacts.response.ArtifactTaskResponse;
import io.harness.eventsframework.schemas.entity.EntityDetailProtoDTO;
import io.harness.exception.ArtifactServerException;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.exceptionmanager.ExceptionManager;
import io.harness.gitsync.sdk.EntityValidityDetails;
import io.harness.logging.CommandExecutionStatus;
import io.harness.logstreaming.NGLogCallback;
import io.harness.metrics.intfc.DelegateMetricsService;
import io.harness.ng.core.BaseNGAccess;
import io.harness.ng.core.EntityDetail;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.ng.core.entitydetail.EntityDetailProtoToRestMapper;
import io.harness.ng.core.service.yaml.NGServiceConfig;
import io.harness.ng.core.service.yaml.NGServiceV2InfoConfig;
import io.harness.ng.core.template.TemplateApplyRequestDTO;
import io.harness.ng.core.template.TemplateMergeResponseDTO;
import io.harness.ng.core.template.exception.NGTemplateResolveExceptionV2;
import io.harness.ng.core.template.refresh.ValidateTemplateInputsResponseDTO;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.ambiance.Level;
import io.harness.pms.contracts.execution.AsyncExecutableResponse;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.rbac.PipelineRbacHelper;
import io.harness.pms.sdk.core.data.OptionalSweepingOutput;
import io.harness.pms.sdk.core.resolver.RefObjectUtils;
import io.harness.pms.sdk.core.resolver.outputs.ExecutionSweepingOutputService;
import io.harness.pms.sdk.core.steps.io.StepInputPackage;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.pms.yaml.ParameterField;
import io.harness.pms.yaml.YamlUtils;
import io.harness.rule.Owner;
import io.harness.rule.OwnerRule;
import io.harness.secretmanagerclient.services.api.SecretManagerClientService;
import io.harness.serializer.KryoSerializer;
import io.harness.service.DelegateGrpcClientWrapper;
import io.harness.steps.EntityReferenceExtractorUtils;
import io.harness.template.remote.TemplateResourceClient;
import io.harness.utils.NGFeatureFlagHelperService;

import software.wings.beans.SerializationFormat;

import com.google.common.io.Resources;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.assertj.core.api.Assertions;
import org.jooq.tools.reflect.Reflect;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import retrofit2.Call;
import retrofit2.Response;

@OwnedBy(HarnessTeam.CDC)
public class ArtifactsStepV2Test extends CDNGTestBase {
  private static final String ACCOUNT_ID = "ACCOUNT_ID";
  @Mock private NGLogCallback mockNgLogCallback;
  @Mock private ServiceStepsHelper serviceStepsHelper;
  @Mock private DelegateGrpcClientWrapper delegateGrpcClientWrapper;
  @Mock private ExecutionSweepingOutputService mockSweepingOutputService;
  @Mock private CDStepHelper cdStepHelper = Mockito.spy(CDStepHelper.class);
  @Mock private CDExpressionResolver expressionResolver;
  @Mock private KryoSerializer kryoSerializer;
  @Mock private TemplateResourceClient templateResourceClient;
  @Mock EntityDetailProtoToRestMapper entityDetailProtoToRestMapper;
  @Mock private EntityReferenceExtractorUtils entityReferenceExtractorUtils;
  @Mock private PipelineRbacHelper pipelineRbacHelper;
  @InjectMocks private ArtifactsStepV2 step = new ArtifactsStepV2();
  private final ArtifactStepHelper stepHelper = new ArtifactStepHelper();
  @Mock private ConnectorService connectorService;
  @Mock private SecretManagerClientService secretManagerClientService;

  @Mock private DelegateMetricsService delegateMetricsService;

  @Mock private SecretManagerClientService ngSecretService;
  @Mock ExceptionManager exceptionManager;
  @Mock private NGFeatureFlagHelperService ngFeatureFlagHelperService;

  private final EmptyStepParameters stepParameters = new EmptyStepParameters();
  private final StepInputPackage inputPackage = StepInputPackage.builder().build();
  private AutoCloseable mocks;

  private final Ambiance ambiance = buildAmbiance();
  private final ArtifactTaskResponse successResponse = sampleArtifactTaskResponse();
  private final ErrorNotifyResponseData errorNotifyResponse = sampleErrorNotifyResponse();

  private static final String NULL_TAG_TAG_REGEX_MESSAGE = "value for tag and tagRegex is empty or not provided";
  private static final String NULL_VERSION_VERSION_REGEX_MESSAGE =
      "value for version and versionRegex is empty or not provided";
  private static final String NULL_FILEPATH_FILEPATH_REGEX_MESSAGE =
      "value for filePath and filePathRegex is empty or not provided";
  private static final ParameterField<String> CONNECTOR = ParameterField.createValueField("connector");
  private static final ParameterField<String> TAG_NULL = ParameterField.createValueField(null);
  private static final ParameterField<String> TAG_EMPTY = ParameterField.createValueField("");
  private static final ParameterField<String> TAG_INPUT = ParameterField.createValueField("<+input>");

  @Before
  public void setUp() throws Exception {
    mocks = MockitoAnnotations.openMocks(this);

    Reflect.on(stepHelper).set("connectorService", connectorService);
    Reflect.on(stepHelper).set("secretManagerClientService", secretManagerClientService);
    Reflect.on(stepHelper).set("cdExpressionResolver", expressionResolver);
    Reflect.on(stepHelper).set("ngFeatureFlagHelperService", ngFeatureFlagHelperService);
    Reflect.on(step).set("artifactStepHelper", stepHelper);
    doReturn(false).when(ngFeatureFlagHelperService).isEnabled(anyString(), any());

    // setup mock for connector
    doReturn(Optional.of(
                 ConnectorResponseDTO.builder()
                     .entityValidityDetails(EntityValidityDetails.builder().valid(true).build())
                     .connector(
                         ConnectorInfoDTO.builder()
                             .projectIdentifier("projectId")
                             .orgIdentifier("orgId")
                             .connectorConfig(
                                 DockerConnectorDTO.builder()
                                     .dockerRegistryUrl("https://index.docker.com/v1")
                                     .auth(DockerAuthenticationDTO.builder().authType(DockerAuthType.ANONYMOUS).build())
                                     .delegateSelectors(Set.of("d1"))
                                     .build())
                             .build())
                     .build()))
        .when(connectorService)
        .get(anyString(), anyString(), anyString(), eq("connector"));

    // mock serviceStepsHelper
    doReturn(mockNgLogCallback).when(serviceStepsHelper).getServiceLogCallback(Mockito.any());
    doReturn(mockNgLogCallback).when(serviceStepsHelper).getServiceLogCallback(Mockito.any(), Mockito.anyBoolean());

    // mock delegateGrpcClientWrapper
    doAnswer(invocationOnMock -> UUIDGenerator.generateUuid())
        .when(delegateGrpcClientWrapper)
        .submitAsyncTaskV2(any(DelegateTaskRequest.class), any(Duration.class));

    doCallRealMethod().when(cdStepHelper).mapTaskRequestToDelegateTaskRequest(any(), any(), anySet());
    doCallRealMethod()
        .when(cdStepHelper)
        .mapTaskRequestToDelegateTaskRequest(any(), any(), anySet(), anyString(), anyBoolean());

    doAnswer(invocationOnMock -> invocationOnMock.getArgument(1, String.class))
        .when(expressionResolver)
        .renderExpression(any(Ambiance.class), anyString());
  }

  @After
  public void tearDown() throws Exception {
    if (mocks != null) {
      mocks.close();
    }
  }

  private List<ArtifactSourceType> getArtifactSourceTypesHavingTagField() {
    List<ArtifactSourceType> artifactSourceList = new ArrayList<>();
    artifactSourceList.add(ArtifactSourceType.DOCKER_REGISTRY);
    artifactSourceList.add(ArtifactSourceType.ACR);
    artifactSourceList.add(ArtifactSourceType.ECR);
    artifactSourceList.add(ArtifactSourceType.GCR);
    artifactSourceList.add(ArtifactSourceType.NEXUS3_REGISTRY);

    return artifactSourceList;
  }

  private List<ArtifactConfig> getArtifactConfigTagNull() {
    List<ArtifactConfig> artifactConfigList = new ArrayList<>();
    artifactConfigList.add(DockerHubArtifactConfig.builder().connectorRef(CONNECTOR).tag(TAG_NULL).build());
    artifactConfigList.add(AcrArtifactConfig.builder().connectorRef(CONNECTOR).tag(TAG_NULL).build());
    artifactConfigList.add(EcrArtifactConfig.builder().connectorRef(CONNECTOR).tag(TAG_NULL).build());
    artifactConfigList.add(GcrArtifactConfig.builder().connectorRef(CONNECTOR).tag(TAG_NULL).build());
    artifactConfigList.add(NexusRegistryArtifactConfig.builder().connectorRef(CONNECTOR).tag(TAG_NULL).build());

    return artifactConfigList;
  }

  private List<ArtifactConfig> getArtifactConfigTagEmpty() {
    List<ArtifactConfig> artifactConfigList = new ArrayList<>();
    artifactConfigList.add(DockerHubArtifactConfig.builder().connectorRef(CONNECTOR).tag(TAG_EMPTY).build());
    artifactConfigList.add(AcrArtifactConfig.builder().connectorRef(CONNECTOR).tag(TAG_EMPTY).build());
    artifactConfigList.add(EcrArtifactConfig.builder().connectorRef(CONNECTOR).tag(TAG_EMPTY).build());
    artifactConfigList.add(GcrArtifactConfig.builder().connectorRef(CONNECTOR).tag(TAG_EMPTY).build());
    artifactConfigList.add(NexusRegistryArtifactConfig.builder().connectorRef(CONNECTOR).tag(TAG_EMPTY).build());

    return artifactConfigList;
  }

  private List<ArtifactConfig> getArtifactConfigTagRegexNull() {
    List<ArtifactConfig> artifactConfigList = new ArrayList<>();
    artifactConfigList.add(DockerHubArtifactConfig.builder().connectorRef(CONNECTOR).tagRegex(TAG_NULL).build());
    artifactConfigList.add(AcrArtifactConfig.builder().connectorRef(CONNECTOR).tagRegex(TAG_NULL).build());
    artifactConfigList.add(EcrArtifactConfig.builder().connectorRef(CONNECTOR).tagRegex(TAG_NULL).build());
    artifactConfigList.add(GcrArtifactConfig.builder().connectorRef(CONNECTOR).tagRegex(TAG_NULL).build());
    artifactConfigList.add(NexusRegistryArtifactConfig.builder().connectorRef(CONNECTOR).tagRegex(TAG_NULL).build());

    return artifactConfigList;
  }

  private List<ArtifactConfig> getArtifactConfigTagRegexEmpty() {
    List<ArtifactConfig> artifactConfigList = new ArrayList<>();
    artifactConfigList.add(DockerHubArtifactConfig.builder().connectorRef(CONNECTOR).tagRegex(TAG_EMPTY).build());
    artifactConfigList.add(AcrArtifactConfig.builder().connectorRef(CONNECTOR).tagRegex(TAG_EMPTY).build());
    artifactConfigList.add(EcrArtifactConfig.builder().connectorRef(CONNECTOR).tagRegex(TAG_EMPTY).build());
    artifactConfigList.add(GcrArtifactConfig.builder().connectorRef(CONNECTOR).tagRegex(TAG_EMPTY).build());
    artifactConfigList.add(NexusRegistryArtifactConfig.builder().connectorRef(CONNECTOR).tagRegex(TAG_EMPTY).build());

    return artifactConfigList;
  }

  private List<ArtifactConfig> getArtifactConfigTagInput() {
    List<ArtifactConfig> artifactConfigList = new ArrayList<>();
    artifactConfigList.add(DockerHubArtifactConfig.builder().connectorRef(CONNECTOR).tag(TAG_INPUT).build());
    artifactConfigList.add(AcrArtifactConfig.builder().connectorRef(CONNECTOR).tag(TAG_INPUT).build());
    artifactConfigList.add(EcrArtifactConfig.builder().connectorRef(CONNECTOR).tag(TAG_INPUT).build());
    artifactConfigList.add(GcrArtifactConfig.builder().connectorRef(CONNECTOR).tag(TAG_INPUT).build());
    artifactConfigList.add(NexusRegistryArtifactConfig.builder().connectorRef(CONNECTOR).tag(TAG_INPUT).build());

    return artifactConfigList;
  }

  private List<ArtifactConfig> getArtifactConfigTagRegexInput() {
    List<ArtifactConfig> artifactConfigList = new ArrayList<>();
    artifactConfigList.add(DockerHubArtifactConfig.builder().connectorRef(CONNECTOR).tagRegex(TAG_INPUT).build());
    artifactConfigList.add(AcrArtifactConfig.builder().connectorRef(CONNECTOR).tagRegex(TAG_INPUT).build());
    artifactConfigList.add(EcrArtifactConfig.builder().connectorRef(CONNECTOR).tagRegex(TAG_INPUT).build());
    artifactConfigList.add(GcrArtifactConfig.builder().connectorRef(CONNECTOR).tagRegex(TAG_INPUT).build());
    artifactConfigList.add(NexusRegistryArtifactConfig.builder().connectorRef(CONNECTOR).tagRegex(TAG_INPUT).build());

    return artifactConfigList;
  }

  private List<ArtifactSourceType> getArtifactSourceTypesHavingVersionField() {
    List<ArtifactSourceType> artifactSourceList = new ArrayList<>();
    artifactSourceList.add(ArtifactSourceType.AMI);
    artifactSourceList.add(ArtifactSourceType.AZURE_ARTIFACTS);
    artifactSourceList.add(ArtifactSourceType.GITHUB_PACKAGES);
    artifactSourceList.add(ArtifactSourceType.GOOGLE_ARTIFACT_REGISTRY);

    return artifactSourceList;
  }

  private List<ArtifactConfig> getArtifactConfigVersionNull() {
    List<ArtifactConfig> artifactConfigList = new ArrayList<>();
    artifactConfigList.add(AMIArtifactConfig.builder().connectorRef(CONNECTOR).version(TAG_NULL).build());
    artifactConfigList.add(AzureArtifactsConfig.builder().connectorRef(CONNECTOR).version(TAG_NULL).build());
    artifactConfigList.add(GithubPackagesArtifactConfig.builder().connectorRef(CONNECTOR).version(TAG_NULL).build());
    artifactConfigList.add(GoogleArtifactRegistryConfig.builder().connectorRef(CONNECTOR).version(TAG_NULL).build());

    return artifactConfigList;
  }

  private List<ArtifactConfig> getArtifactConfigVersionEmpty() {
    List<ArtifactConfig> artifactConfigList = new ArrayList<>();
    artifactConfigList.add(AMIArtifactConfig.builder().connectorRef(CONNECTOR).version(TAG_EMPTY).build());
    artifactConfigList.add(AzureArtifactsConfig.builder().connectorRef(CONNECTOR).version(TAG_EMPTY).build());
    artifactConfigList.add(GithubPackagesArtifactConfig.builder().connectorRef(CONNECTOR).version(TAG_EMPTY).build());
    artifactConfigList.add(GoogleArtifactRegistryConfig.builder().connectorRef(CONNECTOR).version(TAG_EMPTY).build());

    return artifactConfigList;
  }

  private List<ArtifactConfig> getArtifactConfigVersionInput() {
    List<ArtifactConfig> artifactConfigList = new ArrayList<>();
    artifactConfigList.add(AMIArtifactConfig.builder().connectorRef(CONNECTOR).version(TAG_INPUT).build());
    artifactConfigList.add(AzureArtifactsConfig.builder().connectorRef(CONNECTOR).version(TAG_INPUT).build());
    artifactConfigList.add(GithubPackagesArtifactConfig.builder().connectorRef(CONNECTOR).version(TAG_INPUT).build());
    artifactConfigList.add(GoogleArtifactRegistryConfig.builder().connectorRef(CONNECTOR).version(TAG_INPUT).build());

    return artifactConfigList;
  }

  private List<ArtifactConfig> getArtifactConfigVersionRegexNull() {
    List<ArtifactConfig> artifactConfigList = new ArrayList<>();
    artifactConfigList.add(AMIArtifactConfig.builder().connectorRef(CONNECTOR).versionRegex(TAG_NULL).build());
    artifactConfigList.add(AzureArtifactsConfig.builder().connectorRef(CONNECTOR).versionRegex(TAG_NULL).build());
    artifactConfigList.add(
        GithubPackagesArtifactConfig.builder().connectorRef(CONNECTOR).versionRegex(TAG_NULL).build());
    artifactConfigList.add(
        GoogleArtifactRegistryConfig.builder().connectorRef(CONNECTOR).versionRegex(TAG_NULL).build());

    return artifactConfigList;
  }

  private List<ArtifactConfig> getArtifactConfigVersionRegexEmpty() {
    List<ArtifactConfig> artifactConfigList = new ArrayList<>();
    artifactConfigList.add(AMIArtifactConfig.builder().connectorRef(CONNECTOR).versionRegex(TAG_EMPTY).build());
    artifactConfigList.add(AzureArtifactsConfig.builder().connectorRef(CONNECTOR).versionRegex(TAG_EMPTY).build());
    artifactConfigList.add(
        GithubPackagesArtifactConfig.builder().connectorRef(CONNECTOR).versionRegex(TAG_EMPTY).build());
    artifactConfigList.add(
        GoogleArtifactRegistryConfig.builder().connectorRef(CONNECTOR).versionRegex(TAG_EMPTY).build());

    return artifactConfigList;
  }

  private List<ArtifactConfig> getArtifactConfigVersionRegexInput() {
    List<ArtifactConfig> artifactConfigList = new ArrayList<>();
    artifactConfigList.add(AMIArtifactConfig.builder().connectorRef(CONNECTOR).versionRegex(TAG_INPUT).build());
    artifactConfigList.add(AzureArtifactsConfig.builder().connectorRef(CONNECTOR).versionRegex(TAG_INPUT).build());
    artifactConfigList.add(
        GithubPackagesArtifactConfig.builder().connectorRef(CONNECTOR).versionRegex(TAG_INPUT).build());
    artifactConfigList.add(
        GoogleArtifactRegistryConfig.builder().connectorRef(CONNECTOR).versionRegex(TAG_INPUT).build());

    return artifactConfigList;
  }

  private void checkResponse(ArtifactSource source1, String message) {
    doReturn(getServiceYaml(artifactListConfigHelper(Arrays.asList(source1), source1.getIdentifier())))
        .when(cdStepHelper)
        .fetchServiceYamlFromSweepingOutput(Mockito.any(Ambiance.class));
    assertThatThrownBy(() -> step.executeAsync(ambiance, stepParameters, inputPackage, null))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage(message);
  }

  @Test
  @Owner(developers = OwnerRule.YOGESH)
  @Category(UnitTests.class)
  public void executeAsyncServiceSweepingOutputNotPresent() {
    AsyncExecutableResponse response =
        step.executeAsync(ambiance, stepParameters, StepInputPackage.builder().build(), null);

    assertThat(response.getCallbackIdsCount()).isEqualTo(0);
  }

  @Test
  @Owner(developers = OwnerRule.YOGESH)
  @Category(UnitTests.class)
  public void executeAsyncOnlyPrimary() {
    ArgumentCaptor<ArtifactsStepV2SweepingOutput> captor = ArgumentCaptor.forClass(ArtifactsStepV2SweepingOutput.class);
    ArgumentCaptor<DelegateTaskRequest> delegateTaskRequestArgumentCaptor =
        ArgumentCaptor.forClass(DelegateTaskRequest.class);

    List<EntityDetail> listEntityDetail = new ArrayList<>();

    listEntityDetail.add(EntityDetail.builder().name("docker").build());
    listEntityDetail.add(EntityDetail.builder().name("googleArtifactRegistry").build());

    Set<EntityDetailProtoDTO> setEntityDetail = new HashSet<>();

    doReturn(setEntityDetail).when(entityReferenceExtractorUtils).extractReferredEntities(any(), any());

    doReturn(listEntityDetail)
        .when(entityDetailProtoToRestMapper)
        .createEntityDetailsDTO(new ArrayList<>(emptyIfNull(setEntityDetail)));

    doReturn(getServiceYaml(ArtifactListConfig.builder()
                                .primary(PrimaryArtifact.builder()
                                             .sourceType(ArtifactSourceType.DOCKER_REGISTRY)
                                             .spec(DockerHubArtifactConfig.builder()
                                                       .connectorRef(ParameterField.createValueField("connector"))
                                                       .tag(ParameterField.createValueField("latest"))
                                                       .imagePath(ParameterField.createValueField("nginx"))
                                                       .build())
                                             .build())
                                .build()))
        .when(cdStepHelper)
        .fetchServiceYamlFromSweepingOutput(Mockito.any(Ambiance.class));

    AsyncExecutableResponse response = step.executeAsync(ambiance, stepParameters, inputPackage, null);

    verify(mockSweepingOutputService).consume(any(Ambiance.class), anyString(), captor.capture(), eq(""));
    verify(expressionResolver, times(1)).updateExpressions(any(Ambiance.class), any());
    verify(delegateGrpcClientWrapper, times(1))
        .submitAsyncTaskV2(delegateTaskRequestArgumentCaptor.capture(), eq(Duration.ZERO));

    verify(pipelineRbacHelper, times(1)).checkRuntimePermissions(ambiance, listEntityDetail, true);

    ArtifactsStepV2SweepingOutput output = captor.getValue();

    assertThat(output.getArtifactConfigMap()).hasSize(1);
    assertThat(output.getPrimaryArtifactTaskId()).isNotEmpty();
    assertThat(
        output.getArtifactConfigMap().values().stream().map(ArtifactConfig::getIdentifier).collect(Collectors.toSet()))
        .containsExactly("primary");
    assertThat(response.getCallbackIdsCount()).isEqualTo(1);

    DelegateTaskRequest taskRequest = delegateTaskRequestArgumentCaptor.getValue();
    verifyDockerArtifactRequest(taskRequest, "latest");
  }

  @Test
  @Owner(developers = OwnerRule.TATHAGAT)
  @Category(UnitTests.class)
  public void executeAsyncOnlyPrimaryNullCheck() {
    ArgumentCaptor<ArtifactsStepV2SweepingOutput> captor = ArgumentCaptor.forClass(ArtifactsStepV2SweepingOutput.class);

    doReturn(getServiceYaml(ArtifactListConfig.builder()
                                .primary(PrimaryArtifact.builder().sourceType(null).spec(null).build())
                                .build()))
        .when(cdStepHelper)
        .fetchServiceYamlFromSweepingOutput(Mockito.any(Ambiance.class));

    AsyncExecutableResponse response = step.executeAsync(ambiance, stepParameters, inputPackage, null);

    verify(expressionResolver, never()).updateExpressions(any(Ambiance.class), any());
    verify(delegateGrpcClientWrapper, never()).submitAsyncTaskV2(any(DelegateTaskRequest.class), any(Duration.class));

    assertThat(response.getCallbackIdsCount()).isZero();
  }

  @Test
  @Owner(developers = OwnerRule.YOGESH)
  @Category(UnitTests.class)
  public void executeAsyncOnlyPrimaryNoDelegateTaskNeeded() {
    ArgumentCaptor<ArtifactsStepV2SweepingOutput> captor = ArgumentCaptor.forClass(ArtifactsStepV2SweepingOutput.class);

    doReturn(
        getServiceYaml(
            ArtifactListConfig.builder()
                .primary(
                    PrimaryArtifact.builder()
                        .sourceType(ArtifactSourceType.CUSTOM_ARTIFACT)
                        .spec(CustomArtifactConfig.builder().version(ParameterField.createValueField("1.0")).build())
                        .build())
                .build()))
        .when(cdStepHelper)
        .fetchServiceYamlFromSweepingOutput(Mockito.any(Ambiance.class));

    AsyncExecutableResponse response = step.executeAsync(ambiance, stepParameters, inputPackage, null);

    verifyNoInteractions(delegateGrpcClientWrapper);

    verify(mockSweepingOutputService).consume(any(Ambiance.class), anyString(), captor.capture(), eq(""));
    verify(expressionResolver, times(1)).updateExpressions(any(Ambiance.class), any());

    ArtifactsStepV2SweepingOutput output = captor.getValue();

    assertThat(output.getArtifactConfigMap()).hasSize(0);
    assertThat(output.getPrimaryArtifactTaskId()).isNull();
    assertThat(response.getCallbackIdsCount()).isEqualTo(0);

    assertThat(output.getArtifactConfigMapForNonDelegateTaskTypes()).hasSize(1);
    assertThat(
        ((CustomArtifactConfig) output.getArtifactConfigMapForNonDelegateTaskTypes().get(0)).getVersion().getValue())
        .isEqualTo("1.0");
    assertThat(output.getArtifactConfigMapForNonDelegateTaskTypes().get(0).isPrimaryArtifact()).isTrue();
  }

  @Test
  @Owner(developers = OwnerRule.YOGESH)
  @Category(UnitTests.class)
  public void executeAsyncWithArtifactSources() {
    ArgumentCaptor<ArtifactsStepV2SweepingOutput> captor = ArgumentCaptor.forClass(ArtifactsStepV2SweepingOutput.class);
    ArgumentCaptor<DelegateTaskRequest> delegateTaskRequestArgumentCaptor =
        ArgumentCaptor.forClass(DelegateTaskRequest.class);

    // Prepare test data
    ArtifactSource source1 = ArtifactSource.builder()
                                 .identifier("source1-id")
                                 .sourceType(ArtifactSourceType.DOCKER_REGISTRY)
                                 .spec(DockerHubArtifactConfig.builder()
                                           .connectorRef(ParameterField.createValueField("connector"))
                                           .tag(ParameterField.createValueField("latest"))
                                           .imagePath(ParameterField.createValueField("nginx"))
                                           .build())
                                 .build();
    ArtifactSource source2 = ArtifactSource.builder()
                                 .identifier("source2-id")
                                 .sourceType(ArtifactSourceType.GCR)
                                 .spec(GcrArtifactConfig.builder()
                                           .connectorRef(ParameterField.createValueField("connector"))
                                           .tag(ParameterField.createValueField("latest-1"))
                                           .imagePath(ParameterField.createValueField("nginx"))
                                           .build())
                                 .build();
    doReturn(getServiceYaml(
                 ArtifactListConfig.builder()
                     .primary(PrimaryArtifact.builder()
                                  .sources(List.of(source1, source2))
                                  .primaryArtifactRef(ParameterField.createValueField(source1.getIdentifier()))
                                  .build())
                     .sidecar(SidecarArtifactWrapper.builder()
                                  .sidecar(SidecarArtifact.builder()
                                               .identifier("s1")
                                               .sourceType(ArtifactSourceType.DOCKER_REGISTRY)
                                               .spec(DockerHubArtifactConfig.builder()
                                                         .connectorRef(ParameterField.createValueField("connector"))
                                                         .tag(ParameterField.createValueField("latest-2"))
                                                         .imagePath(ParameterField.createValueField("nginx"))
                                                         .build())
                                               .build())
                                  .build())
                     .sidecar(SidecarArtifactWrapper.builder()
                                  .sidecar(SidecarArtifact.builder()
                                               .identifier("s2")
                                               .sourceType(ArtifactSourceType.DOCKER_REGISTRY)
                                               .spec(DockerHubArtifactConfig.builder()
                                                         .connectorRef(ParameterField.createValueField("connector"))
                                                         .tag(ParameterField.createValueField("latest-3"))
                                                         .imagePath(ParameterField.createValueField("nginx"))
                                                         .build())
                                               .build())
                                  .build())
                     .build()))
        .when(cdStepHelper)
        .fetchServiceYamlFromSweepingOutput(Mockito.any(Ambiance.class));

    AsyncExecutableResponse response = step.executeAsync(ambiance, stepParameters, inputPackage, null);

    verify(delegateGrpcClientWrapper, times(3))
        .submitAsyncTaskV2(delegateTaskRequestArgumentCaptor.capture(), eq(Duration.ZERO));
    verify(mockSweepingOutputService).consume(any(Ambiance.class), anyString(), captor.capture(), eq(""));
    verify(expressionResolver, times(1)).updateExpressions(any(Ambiance.class), any());

    ArtifactsStepV2SweepingOutput output = captor.getValue();

    assertThat(output.getArtifactConfigMap()).hasSize(3);
    assertThat(output.getPrimaryArtifactTaskId()).isNotEmpty();
    assertThat(
        output.getArtifactConfigMap().values().stream().map(ArtifactConfig::getIdentifier).collect(Collectors.toSet()))
        .containsExactlyInAnyOrder("primary", "s1", "s2");
    assertThat(response.getCallbackIdsCount()).isEqualTo(3);

    verifyDockerArtifactRequest(delegateTaskRequestArgumentCaptor.getAllValues().get(0), "latest");
    verifyDockerArtifactRequest(delegateTaskRequestArgumentCaptor.getAllValues().get(1), "latest-2");
    verifyDockerArtifactRequest(delegateTaskRequestArgumentCaptor.getAllValues().get(2), "latest-3");
  }

  @Test
  @Owner(developers = OwnerRule.YOGESH)
  @Category(UnitTests.class)
  public void testPrimaryArtifactRefNotResolved() {
    // Prepare test data
    ArtifactSource source1 = ArtifactSource.builder()
                                 .identifier("source1-id")
                                 .sourceType(ArtifactSourceType.DOCKER_REGISTRY)
                                 .spec(DockerHubArtifactConfig.builder()
                                           .connectorRef(ParameterField.createValueField("connector"))
                                           .tag(ParameterField.createValueField("latest"))
                                           .imagePath(ParameterField.createValueField("nginx"))
                                           .build())
                                 .build();

    ArtifactSource source2 = ArtifactSource.builder()
                                 .identifier("source2-id")
                                 .sourceType(ArtifactSourceType.DOCKER_REGISTRY)
                                 .spec(DockerHubArtifactConfig.builder()
                                           .connectorRef(ParameterField.createValueField("connector"))
                                           .tag(ParameterField.createValueField("latest"))
                                           .imagePath(ParameterField.createValueField("nginx"))
                                           .build())
                                 .build();
    doReturn(getServiceYaml(ArtifactListConfig.builder()
                                .primary(PrimaryArtifact.builder()
                                             .sources(List.of(source1, source2))
                                             .primaryArtifactRef(
                                                 ParameterField.createExpressionField(true, "<+input>", null, true))
                                             .build())
                                .build()))
        .when(cdStepHelper)
        .fetchServiceYamlFromSweepingOutput(Mockito.any(Ambiance.class));

    Assertions.assertThatExceptionOfType(InvalidRequestException.class)
        .isThrownBy(() -> step.executeAsync(ambiance, stepParameters, inputPackage, null))
        .withMessageContaining("Primary artifact ref cannot be runtime or expression inside service");
  }

  @Test
  @Owner(developers = OwnerRule.YOGESH)
  @Category(UnitTests.class)
  public void executeAsyncPrimaryAndSidecars() {
    ArgumentCaptor<ArtifactsStepV2SweepingOutput> captor = ArgumentCaptor.forClass(ArtifactsStepV2SweepingOutput.class);
    ArgumentCaptor<DelegateTaskRequest> delegateTaskRequestArgumentCaptor =
        ArgumentCaptor.forClass(DelegateTaskRequest.class);

    doReturn(getServiceYaml(
                 ArtifactListConfig.builder()
                     .primary(PrimaryArtifact.builder()
                                  .sourceType(ArtifactSourceType.DOCKER_REGISTRY)
                                  .spec(DockerHubArtifactConfig.builder()
                                            .tag(ParameterField.createValueField("latest"))
                                            .connectorRef(ParameterField.createValueField("connector"))
                                            .imagePath(ParameterField.createValueField("nginx"))
                                            .build())
                                  .build())
                     .sidecar(SidecarArtifactWrapper.builder()
                                  .sidecar(SidecarArtifact.builder()
                                               .identifier("s1")
                                               .sourceType(ArtifactSourceType.DOCKER_REGISTRY)
                                               .spec(DockerHubArtifactConfig.builder()
                                                         .connectorRef(ParameterField.createValueField("connector"))
                                                         .tag(ParameterField.createValueField("latest-1"))
                                                         .imagePath(ParameterField.createValueField("nginx"))
                                                         .build())
                                               .build())
                                  .build())
                     .sidecar(SidecarArtifactWrapper.builder()
                                  .sidecar(SidecarArtifact.builder()
                                               .identifier("s2")
                                               .sourceType(ArtifactSourceType.DOCKER_REGISTRY)
                                               .spec(DockerHubArtifactConfig.builder()
                                                         .connectorRef(ParameterField.createValueField("connector"))
                                                         .tag(ParameterField.createValueField("latest-2"))
                                                         .imagePath(ParameterField.createValueField("nginx"))
                                                         .build())
                                               .build())
                                  .build())
                     .sidecar(SidecarArtifactWrapper.builder()
                                  .sidecar(SidecarArtifact.builder()
                                               .identifier("s3")
                                               .sourceType(ArtifactSourceType.CUSTOM_ARTIFACT)
                                               .spec(CustomArtifactConfig.builder()
                                                         .version(ParameterField.createValueField("1.0"))
                                                         .build())
                                               .build())
                                  .build())
                     .build()))
        .when(cdStepHelper)
        .fetchServiceYamlFromSweepingOutput(Mockito.any(Ambiance.class));

    AsyncExecutableResponse response = step.executeAsync(ambiance, stepParameters, inputPackage, null);

    verify(delegateGrpcClientWrapper, times(3))
        .submitAsyncTaskV2(delegateTaskRequestArgumentCaptor.capture(), eq(Duration.ZERO));
    verify(mockSweepingOutputService).consume(any(Ambiance.class), anyString(), captor.capture(), eq(""));
    verify(expressionResolver, times(1)).updateExpressions(any(Ambiance.class), any());

    ArtifactsStepV2SweepingOutput output = captor.getValue();

    assertThat(output.getArtifactConfigMap()).hasSize(3);
    assertThat(output.getPrimaryArtifactTaskId()).isNotEmpty();
    assertThat(
        output.getArtifactConfigMap().values().stream().map(ArtifactConfig::getIdentifier).collect(Collectors.toSet()))
        .containsExactlyInAnyOrder("s1", "s2", "primary");
    assertThat(response.getCallbackIdsCount()).isEqualTo(3);

    assertThat(output.getArtifactConfigMapForNonDelegateTaskTypes()).hasSize(1);
    assertThat(
        ((CustomArtifactConfig) output.getArtifactConfigMapForNonDelegateTaskTypes().get(0)).getVersion().getValue())
        .isEqualTo("1.0");
    assertThat(output.getArtifactConfigMapForNonDelegateTaskTypes().get(0).isPrimaryArtifact()).isFalse();

    verifyDockerArtifactRequest(delegateTaskRequestArgumentCaptor.getAllValues().get(0), "latest");
    verifyDockerArtifactRequest(delegateTaskRequestArgumentCaptor.getAllValues().get(1), "latest-1");
    verifyDockerArtifactRequest(delegateTaskRequestArgumentCaptor.getAllValues().get(2), "latest-2");
  }

  @Test
  @Owner(developers = OwnerRule.YOGESH)
  @Category(UnitTests.class)
  public void executeAsyncOnlySidecars() {
    ArgumentCaptor<ArtifactsStepV2SweepingOutput> captor = ArgumentCaptor.forClass(ArtifactsStepV2SweepingOutput.class);
    ArgumentCaptor<DelegateTaskRequest> delegateTaskRequestArgumentCaptor =
        ArgumentCaptor.forClass(DelegateTaskRequest.class);

    doReturn(getServiceYaml(
                 ArtifactListConfig.builder()
                     .sidecar(SidecarArtifactWrapper.builder()
                                  .sidecar(SidecarArtifact.builder()
                                               .identifier("s1")
                                               .sourceType(ArtifactSourceType.DOCKER_REGISTRY)
                                               .spec(DockerHubArtifactConfig.builder()
                                                         .connectorRef(ParameterField.createValueField("connector"))
                                                         .tag(ParameterField.createValueField("latest-1"))
                                                         .imagePath(ParameterField.createValueField("nginx"))
                                                         .build())
                                               .build())
                                  .build())
                     .sidecar(SidecarArtifactWrapper.builder()
                                  .sidecar(SidecarArtifact.builder()
                                               .identifier("s2")
                                               .sourceType(ArtifactSourceType.DOCKER_REGISTRY)
                                               .spec(DockerHubArtifactConfig.builder()
                                                         .connectorRef(ParameterField.createValueField("connector"))
                                                         .tag(ParameterField.createValueField("latest-2"))
                                                         .imagePath(ParameterField.createValueField("nginx"))
                                                         .build())
                                               .build())
                                  .build())
                     .build()))
        .when(cdStepHelper)
        .fetchServiceYamlFromSweepingOutput(Mockito.any(Ambiance.class));

    AsyncExecutableResponse response = step.executeAsync(ambiance, stepParameters, inputPackage, null);

    verify(delegateGrpcClientWrapper, times(2))
        .submitAsyncTaskV2(delegateTaskRequestArgumentCaptor.capture(), eq(Duration.ZERO));
    verify(mockSweepingOutputService).consume(any(Ambiance.class), anyString(), captor.capture(), eq(""));
    verify(expressionResolver, times(1)).updateExpressions(any(Ambiance.class), any());

    ArtifactsStepV2SweepingOutput output = captor.getValue();

    assertThat(output.getArtifactConfigMap()).hasSize(2);
    assertThat(output.getPrimaryArtifactTaskId()).isNull();
    assertThat(
        output.getArtifactConfigMap().values().stream().map(ArtifactConfig::getIdentifier).collect(Collectors.toSet()))
        .containsExactly("s1", "s2");
    assertThat(response.getCallbackIdsCount()).isEqualTo(2);

    verifyDockerArtifactRequest(delegateTaskRequestArgumentCaptor.getAllValues().get(0), "latest-1");
    verifyDockerArtifactRequest(delegateTaskRequestArgumentCaptor.getAllValues().get(1), "latest-2");
  }

  @Test
  @Owner(developers = OwnerRule.YOGESH)
  @Category(UnitTests.class)
  public void executeAsyncOnlySidecarsNullChecks() {
    ArgumentCaptor<ArtifactsStepV2SweepingOutput> captor = ArgumentCaptor.forClass(ArtifactsStepV2SweepingOutput.class);

    doReturn(
        getServiceYaml(ArtifactListConfig.builder()
                           .sidecar(SidecarArtifactWrapper.builder().sidecar(SidecarArtifact.builder().build()).build())
                           .build()))
        .when(cdStepHelper)
        .fetchServiceYamlFromSweepingOutput(Mockito.any(Ambiance.class));

    AsyncExecutableResponse response = step.executeAsync(ambiance, stepParameters, inputPackage, null);

    verify(delegateGrpcClientWrapper, never()).submitAsyncTaskV2(any(DelegateTaskRequest.class), any(Duration.class));
    verify(mockSweepingOutputService).consume(any(Ambiance.class), anyString(), captor.capture(), eq(""));
    verify(expressionResolver, times(1)).updateExpressions(any(Ambiance.class), any());

    ArtifactsStepV2SweepingOutput output = captor.getValue();

    assertThat(output.getArtifactConfigMap()).hasSize(0);
    assertThat(output.getPrimaryArtifactTaskId()).isNull();
    assertThat(response.getCallbackIdsCount()).isEqualTo(0);
  }

  @Test
  @Owner(developers = OwnerRule.YOGESH)
  @Category(UnitTests.class)
  public void handleAsyncResponseEmpty() {
    StepResponse stepResponse = step.handleAsyncResponse(ambiance, stepParameters, new HashMap<>());

    assertThat(stepResponse.getStatus()).isEqualTo(Status.SKIPPED);
  }

  @Test
  @Owner(developers = OwnerRule.YOGESH)
  @Category(UnitTests.class)
  public void handleAsyncResponsePrimaryOnly() {
    doReturn(OptionalSweepingOutput.builder()
                 .found(true)
                 .output(ArtifactsStepV2SweepingOutput.builder()
                             .primaryArtifactTaskId("taskId-1")
                             .artifactConfigMap(Map.of("taskId-1", sampleDockerConfig("image1")))
                             .build())
                 .build())
        .when(mockSweepingOutputService)
        .resolveOptional(any(Ambiance.class), eq(RefObjectUtils.getSweepingOutputRefObject(ARTIFACTS_STEP_V_2)));

    StepResponse stepResponse = step.handleAsyncResponse(ambiance, stepParameters, Map.of("taskId-1", successResponse));

    final ArgumentCaptor<ArtifactsOutcome> captor = ArgumentCaptor.forClass(ArtifactsOutcome.class);
    verify(mockSweepingOutputService, times(1))
        .consume(any(Ambiance.class), eq("artifacts"), captor.capture(), eq("STAGE"));

    final ArtifactsOutcome outcome = captor.getValue();

    assertThat(stepResponse.getStatus()).isEqualTo(Status.SUCCEEDED);
    assertThat(outcome.getPrimary()).isNotNull();
    assertThat(outcome.getSidecars()).isEmpty();
  }

  @Test
  @Owner(developers = OwnerRule.YOGESH)
  @Category(UnitTests.class)
  public void handleAsyncErrorNotifyResponse() {
    doReturn(OptionalSweepingOutput.builder()
                 .found(true)
                 .output(ArtifactsStepV2SweepingOutput.builder()
                             .primaryArtifactTaskId("taskId-1")
                             .artifactConfigMap(Map.of("taskId-1", sampleDockerConfig("image1")))
                             .build())
                 .build())
        .when(mockSweepingOutputService)
        .resolveOptional(any(Ambiance.class), eq(RefObjectUtils.getSweepingOutputRefObject(ARTIFACTS_STEP_V_2)));

    try {
      step.handleAsyncResponse(
          ambiance, stepParameters, Map.of("taskId-1", errorNotifyResponse, "taskId-2", successResponse));
    } catch (ArtifactServerException ase) {
      assertThat(ase.getMessage()).contains("No Eligible Delegates");
      return;
    }
    fail("ArtifactServerException expected");
  }

  @Test
  @Owner(developers = OwnerRule.YOGESH)
  @Category(UnitTests.class)
  public void handleAsyncResponsePrimaryAndSidecars() {
    doReturn(OptionalSweepingOutput.builder()
                 .found(true)
                 .output(ArtifactsStepV2SweepingOutput.builder()
                             .primaryArtifactTaskId("taskId-1")
                             .artifactConfigMap(Map.of("taskId-1", sampleDockerConfig("image1"), "taskId-2",
                                 sampleDockerConfig("image2"), "taskId-3", sampleDockerConfig("image3")))
                             .build())
                 .build())
        .when(mockSweepingOutputService)
        .resolveOptional(any(Ambiance.class), eq(RefObjectUtils.getSweepingOutputRefObject(ARTIFACTS_STEP_V_2)));

    StepResponse stepResponse = step.handleAsyncResponse(ambiance, stepParameters,
        Map.of("taskId-1", successResponse, "taskId-2", successResponse, "taskId-3", successResponse));

    final ArgumentCaptor<ArtifactsOutcome> captor = ArgumentCaptor.forClass(ArtifactsOutcome.class);
    verify(mockSweepingOutputService, times(1))
        .consume(any(Ambiance.class), eq("artifacts"), captor.capture(), eq("STAGE"));

    final ArtifactsOutcome outcome = captor.getValue();

    assertThat(stepResponse.getStatus()).isEqualTo(Status.SUCCEEDED);
    assertThat(outcome.getPrimary()).isNotNull();
    assertThat(outcome.getSidecars()).hasSize(2);
  }

  @Test
  @Owner(developers = OwnerRule.YOGESH)
  @Category(UnitTests.class)
  public void handleAsyncResponseSidecarsOnly() {
    doReturn(OptionalSweepingOutput.builder()
                 .found(true)
                 .output(ArtifactsStepV2SweepingOutput.builder()
                             .artifactConfigMap(Map.of("taskId-1", sampleDockerConfig("image1"), "taskId-2",
                                 sampleDockerConfig("image2"), "taskId-3", sampleDockerConfig("image3")))
                             .build())
                 .build())
        .when(mockSweepingOutputService)
        .resolveOptional(any(Ambiance.class), eq(RefObjectUtils.getSweepingOutputRefObject(ARTIFACTS_STEP_V_2)));

    StepResponse stepResponse = step.handleAsyncResponse(ambiance, stepParameters,
        Map.of("taskId-1", successResponse, "taskId-2", successResponse, "taskId-3", successResponse));

    final ArgumentCaptor<ArtifactsOutcome> captor = ArgumentCaptor.forClass(ArtifactsOutcome.class);
    verify(mockSweepingOutputService, times(1))
        .consume(any(Ambiance.class), eq("artifacts"), captor.capture(), eq("STAGE"));

    final ArtifactsOutcome outcome = captor.getValue();

    assertThat(stepResponse.getStatus()).isEqualTo(Status.SUCCEEDED);
    assertThat(outcome.getPrimary()).isNull();
    assertThat(outcome.getSidecars()).hasSize(3);
  }

  @Test
  @Owner(developers = OwnerRule.HINGER)
  @Category(UnitTests.class)
  public void testTemplateResolveExceptionWithArtifactSourceTemplateInService() throws IOException {
    String fileName = "service-with-artifact-template-ref.yaml";
    String givenYaml = readFile(fileName);
    Call<ResponseDTO<TemplateMergeResponseDTO>> callRequest = mock(Call.class);
    doReturn(callRequest)
        .when(templateResourceClient)
        .applyTemplatesOnGivenYamlV2("ACCOUNT_ID", "ORG_ID", "PROJECT_ID", null, null, null, null, null, null, null,
            null, null, TemplateApplyRequestDTO.builder().originalEntityYaml(givenYaml).checkForAccess(true).build(),
            false);
    ValidateTemplateInputsResponseDTO validateTemplateInputsResponseDTO =
        ValidateTemplateInputsResponseDTO.builder().build();
    when(callRequest.execute())
        .thenThrow(new NGTemplateResolveExceptionV2(
            "Exception in resolving template refs in given yaml.", USER, validateTemplateInputsResponseDTO, null));
    assertThatThrownBy(() -> step.resolveArtifactSourceTemplateRefs("ACCOUNT_ID", "ORG_ID", "PROJECT_ID", givenYaml))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("Exception in resolving template refs in given service yaml.");
  }

  @Test
  @Owner(developers = OwnerRule.HINGER)
  @Category(UnitTests.class)
  public void testResolveRefsWithArtifactSourceTemplateInService() throws IOException {
    String fileName = "service-with-artifact-template-ref.yaml";
    String givenYaml = readFile(fileName);
    Call<ResponseDTO<TemplateMergeResponseDTO>> callRequest = mock(Call.class);
    doReturn(callRequest)
        .when(templateResourceClient)
        .applyTemplatesOnGivenYamlV2("ACCOUNT_ID", "ORG_ID", "PROJECT_ID", null, null, null, null, null, null, null,
            null, null, TemplateApplyRequestDTO.builder().originalEntityYaml(givenYaml).checkForAccess(true).build(),
            false);
    when(callRequest.execute())
        .thenReturn(Response.success(
            ResponseDTO.newResponse(TemplateMergeResponseDTO.builder().mergedPipelineYaml(givenYaml).build())));
    String resolvedTemplateRefsInService =
        step.resolveArtifactSourceTemplateRefs("ACCOUNT_ID", "ORG_ID", "PROJECT_ID", givenYaml);
    assertThat(resolvedTemplateRefsInService).isEqualTo(givenYaml);
  }

  @Test
  @Owner(developers = OwnerRule.HINGER)
  @Category(UnitTests.class)
  public void testProcessServiceYamlWithPrimaryArtifactRef() {
    String serviceYamlFileName = "service-with-multiple-artifact-sources-template-ref.yaml";
    // merged service yaml
    String serviceYamlFromSweepingOutput = readFile(serviceYamlFileName).replace("$PRIMARY_ARTIFACT_REF", "fromtemp1");

    // primary artifact processed
    String actualServiceYaml = stepHelper.getArtifactProcessedServiceYaml(ambiance, serviceYamlFromSweepingOutput);
    String processedServiceYamlFileName = "service-with-processed-primaryartifact.yaml";
    String expectedServiceYaml = readFile(processedServiceYamlFileName);
    assertThat(actualServiceYaml).isEqualTo(expectedServiceYaml);
  }
  @Test
  @Owner(developers = OwnerRule.YOGESH)
  @Category(UnitTests.class)
  public void testProcessServiceYamlWithPrimaryArtifactRefInputValidator() {
    String serviceYamlFileName = "service-with-multiple-artifact-sources-template-ref.yaml";
    // merged service yaml
    String serviceYamlFromSweepingOutput =
        readFile(serviceYamlFileName)
            .replace("$PRIMARY_ARTIFACT_REF", "fromtemp1.allowedValues(fromtemp1,fromtemp2,gcr)");

    // primary artifact processed
    String actualServiceYaml = stepHelper.getArtifactProcessedServiceYaml(ambiance, serviceYamlFromSweepingOutput);
    String processedServiceYamlFileName = "service-with-processed-primaryartifact.yaml";
    String expectedServiceYaml = readFile(processedServiceYamlFileName);
    assertThat(actualServiceYaml).isEqualTo(expectedServiceYaml);
  }

  @Test
  @Owner(developers = OwnerRule.HINGER)
  @Category(UnitTests.class)
  public void testProcessServiceYamlWithPrimaryArtifactRefAsExpression() {
    String expression = "<+serviceVariables.paf>";
    doReturn("fromtemp1").when(expressionResolver).renderExpression(any(Ambiance.class), eq(expression));
    String serviceYamlFileName = "service-with-multiple-artifact-sources-template-ref.yaml";
    // merged service yaml
    String serviceYamlFromSweepingOutput = readFile(serviceYamlFileName).replace("$PRIMARY_ARTIFACT_REF", expression);

    // primary artifact processed
    String actualServiceYaml = stepHelper.getArtifactProcessedServiceYaml(ambiance, serviceYamlFromSweepingOutput);
    String processedServiceYamlFileName = "service-with-processed-primaryartifact.yaml";
    String expectedServiceYaml = readFile(processedServiceYamlFileName);
    assertThat(actualServiceYaml).isEqualTo(expectedServiceYaml);
  }

  @Test
  @Owner(developers = OwnerRule.YOGESH)
  @Category(UnitTests.class)
  public void testProcessServiceYamlWithSingleArtifactSource() {
    String serviceYamlFileName = "artifactsources/service-with-single-artifact-source.yaml";
    // merged service yaml
    String serviceYamlFromSweepingOutput = readFile(serviceYamlFileName);

    String asRuntime = serviceYamlFromSweepingOutput.replace("$PRIMARY_ARTIFACT_REF", "<+input>");
    String asExpression =
        serviceYamlFromSweepingOutput.replace("$PRIMARY_ARTIFACT_REF", "<+serviceVariables.my_variable>");

    // primary artifact processed
    for (String testString : List.of(asRuntime, asExpression)) {
      String actualServiceYaml = stepHelper.getArtifactProcessedServiceYaml(ambiance, testString);
      String processedServiceYamlFileName = "service-with-processed-primaryartifact.yaml";
      String expectedServiceYaml = readFile(processedServiceYamlFileName);
      assertThat(actualServiceYaml).isEqualTo(expectedServiceYaml);
    }
  }

  @Test
  @Owner(developers = OwnerRule.HINGER)
  @Category(UnitTests.class)
  public void executeAsyncWithArtifactSources_MixedArtifactSources() throws IOException {
    ArgumentCaptor<ArtifactsStepV2SweepingOutput> captor = ArgumentCaptor.forClass(ArtifactsStepV2SweepingOutput.class);
    ArgumentCaptor<DelegateTaskRequest> delegateTaskRequestArgumentCaptor =
        ArgumentCaptor.forClass(DelegateTaskRequest.class);

    String serviceYamlFileName = "service-with-multiple-artifact-sources-template-ref.yaml";
    String serviceYaml = readFile(serviceYamlFileName).replace("$PRIMARY_ARTIFACT_REF", "fromtemp1");

    doReturn(serviceYaml).when(cdStepHelper).fetchServiceYamlFromSweepingOutput(Mockito.any(Ambiance.class));

    Call<ResponseDTO<TemplateMergeResponseDTO>> callRequest = mock(Call.class);
    // processed service with template refs
    String processedServiceWithTemplateRefsFile = "service-with-processed-primaryartifact.yaml";
    String processedServiceYamlWithTemplateRefs = readFile(processedServiceWithTemplateRefsFile);

    // service with resolved template refs
    String resolvedTemplateRefFile = "service-with-resolved-template-ref.yaml";
    String resolvedServiceYaml = readFile(resolvedTemplateRefFile);
    doReturn(callRequest)
        .when(templateResourceClient)
        .applyTemplatesOnGivenYamlV2("ACCOUNT_ID", "orgId", "projectId", null, null, null, null, null, null, null, null,
            null,
            TemplateApplyRequestDTO.builder()
                .originalEntityYaml(processedServiceYamlWithTemplateRefs)
                .checkForAccess(true)
                .build(),
            false);
    when(callRequest.execute())
        .thenReturn(Response.success(ResponseDTO.newResponse(
            TemplateMergeResponseDTO.builder().mergedPipelineYaml(resolvedServiceYaml).build())));

    AsyncExecutableResponse response = step.executeAsync(ambiance, stepParameters, inputPackage, null);

    // 1 primary and 1 sidecar
    verify(delegateGrpcClientWrapper, times(2))
        .submitAsyncTaskV2(delegateTaskRequestArgumentCaptor.capture(), eq(Duration.ZERO));
    verify(mockSweepingOutputService).consume(any(Ambiance.class), anyString(), captor.capture(), eq(""));
    verify(expressionResolver, times(1)).updateExpressions(any(Ambiance.class), any());

    ArtifactsStepV2SweepingOutput output = captor.getValue();

    assertThat(output.getArtifactConfigMap()).hasSize(2);
    assertThat(output.getPrimaryArtifactTaskId()).isNotEmpty();
    assertThat(
        output.getArtifactConfigMap().values().stream().map(ArtifactConfig::getIdentifier).collect(Collectors.toSet()))
        .containsExactlyInAnyOrder("primary", "sidecar1");
    assertThat(response.getCallbackIdsCount()).isEqualTo(2);
  }

  private String readFile(String filename) {
    ClassLoader classLoader = getClass().getClassLoader();
    try {
      return Resources.toString(Objects.requireNonNull(classLoader.getResource(filename)), StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new InvalidRequestException("Could not read resource file: " + filename);
    }
  }

  private DockerHubArtifactConfig sampleDockerConfig(String imagePath) {
    return DockerHubArtifactConfig.builder()
        .identifier(UUIDGenerator.generateUuid())
        .connectorRef(ParameterField.createValueField("dockerhub"))
        .imagePath(ParameterField.createValueField(imagePath))
        .build();
  }

  private ArtifactTaskResponse sampleArtifactTaskResponse() {
    return ArtifactTaskResponse.builder()
        .commandExecutionStatus(CommandExecutionStatus.SUCCESS)
        .artifactTaskExecutionResponse(
            ArtifactTaskExecutionResponse.builder()
                .artifactDelegateResponse(DockerArtifactDelegateResponse.builder()
                                              .sourceType(ArtifactSourceType.DOCKER_REGISTRY)
                                              .buildDetails(ArtifactBuildDetailsNG.builder().number("1").build())
                                              .build())
                .build())
        .build();
  }

  private ErrorNotifyResponseData sampleErrorNotifyResponse() {
    return ErrorNotifyResponseData.builder().errorMessage("No Eligible Delegates").build();
  }

  private String getServiceYaml(ArtifactListConfig artifactListConfig) {
    NGServiceV2InfoConfig config =
        NGServiceV2InfoConfig.builder()
            .identifier("service-id")
            .name("service-name")
            .serviceDefinition(ServiceDefinition.builder()
                                   .type(ServiceDefinitionType.KUBERNETES)
                                   .serviceSpec(KubernetesServiceSpec.builder().artifacts(artifactListConfig).build())
                                   .build())
            .build();
    return YamlUtils.write(NGServiceConfig.builder().ngServiceV2InfoConfig(config).build());
  }

  private ArtifactListConfig artifactListConfigHelper(List<ArtifactSource> artifactSources, String primary) {
    return ArtifactListConfig.builder()
        .primary(PrimaryArtifact.builder()
                     .sources(artifactSources)
                     .primaryArtifactRef(ParameterField.createValueField(primary))
                     .build())
        .build();
  }

  private Ambiance buildAmbiance() {
    List<Level> levels = new ArrayList<>();
    levels.add(Level.newBuilder()
                   .setRuntimeId(generateUuid())
                   .setSetupId(generateUuid())
                   .setStepType(ArtifactsStepV2Constants.STEP_TYPE)
                   .build());
    return Ambiance.newBuilder()
        .setPlanExecutionId(generateUuid())
        .putAllSetupAbstractions(Map.of(SetupAbstractionKeys.accountId, ACCOUNT_ID, SetupAbstractionKeys.orgIdentifier,
            "orgId", SetupAbstractionKeys.projectIdentifier, "projectId"))
        .addAllLevels(levels)
        .setExpressionFunctorToken(1234)
        .build();
  }

  private void verifyDockerArtifactRequest(DelegateTaskRequest taskRequest, String tag) {
    assertThat(taskRequest.isParked()).isFalse();
    assertThat(taskRequest.getTaskSelectors()).containsExactly("d1");
    assertThat(taskRequest.getSerializationFormat()).isEqualTo(SerializationFormat.KRYO);
    assertThat(taskRequest.getAccountId()).isEqualTo(ACCOUNT_ID);
    assertThat(taskRequest.getTaskType()).isEqualTo("DOCKER_ARTIFACT_TASK_NG");
    assertThat(taskRequest.getTaskSetupAbstractions()).hasSize(5);
    assertThat(taskRequest.getTaskParameters())
        .isEqualTo(
            ArtifactTaskParameters.builder()
                .accountId(ACCOUNT_ID)
                .attributes(
                    DockerArtifactDelegateRequest.builder()
                        .imagePath("nginx")
                        .connectorRef("connector")
                        .tag(tag)
                        .encryptedDataDetails(List.of())
                        .sourceType(ArtifactSourceType.DOCKER_REGISTRY)
                        .dockerConnectorDTO(
                            DockerConnectorDTO.builder()
                                .dockerRegistryUrl("https://index.docker.com/v1")
                                .auth(DockerAuthenticationDTO.builder().authType(DockerAuthType.ANONYMOUS).build())
                                .executeOnDelegate(true)
                                .delegateSelectors(Set.of("d1"))
                                .build())
                        .build())
                .artifactTaskType(ArtifactTaskType.GET_LAST_SUCCESSFUL_BUILD)
                .build());
  }

  @Test
  @Owner(developers = OwnerRule.HINGER)
  @Category(UnitTests.class)
  public void testGetSetupAbstractionsForArtifactSourceTasks() {
    BaseNGAccess ngAccess = BaseNGAccess.builder()
                                .accountIdentifier(ACCOUNT_ID)
                                .orgIdentifier("orgId")
                                .projectIdentifier("projectId")
                                .build();

    Map<String, String> abstractions = ArtifactUtils.getTaskSetupAbstractions(ngAccess);
    assertThat(abstractions).hasSize(4);
    assertThat(abstractions.get(SetupAbstractionKeys.projectIdentifier)).isNotNull();
    assertThat(abstractions.get(SetupAbstractionKeys.owner)).isEqualTo("orgId/projectId");

    ngAccess = BaseNGAccess.builder().accountIdentifier(ACCOUNT_ID).orgIdentifier("orgId").build();

    abstractions = ArtifactUtils.getTaskSetupAbstractions(ngAccess);
    assertThat(abstractions).hasSize(3);
    assertThat(abstractions.get(SetupAbstractionKeys.projectIdentifier)).isNull();
    assertThat(abstractions.get(SetupAbstractionKeys.owner)).isEqualTo("orgId");
  }

  @Test
  @Owner(developers = OwnerRule.SHIVAM)
  @Category(UnitTests.class)
  public void testForCustomDelegateRequest() {
    CustomArtifactConfig customArtifactConfig =
        CustomArtifactConfig.builder()
            .identifier("test")
            .primaryArtifact(true)
            .version(ParameterField.createValueField("v1"))
            .versionRegex(ParameterField.createValueField("regex"))
            .scripts(CustomArtifactScripts.builder()
                         .fetchAllArtifacts(
                             FetchAllArtifacts.builder()
                                 .artifactsArrayPath(ParameterField.createValueField("results"))
                                 .versionPath(ParameterField.createValueField("version"))
                                 .shellScriptBaseStepInfo(
                                     CustomArtifactScriptInfo.builder()
                                         .source(CustomArtifactScriptSourceWrapper.builder()
                                                     .type("Inline")
                                                     .spec(CustomScriptInlineSource.builder()
                                                               .script(ParameterField.createValueField("echo test"))
                                                               .build())
                                                     .build())
                                         .build())
                                 .build())
                         .build())
            .build();
    CustomArtifactDelegateRequest artifactSourceDelegateRequest =
        (CustomArtifactDelegateRequest) stepHelper.toSourceDelegateRequest(
            customArtifactConfig, Ambiance.newBuilder().build());
    assertThat(artifactSourceDelegateRequest.getSourceType()).isEqualTo(ArtifactSourceType.CUSTOM_ARTIFACT);
    assertThat(artifactSourceDelegateRequest.getExpressionFunctorToken()).isEqualTo(0);

    customArtifactConfig =
        CustomArtifactConfig.builder()
            .identifier("test")
            .primaryArtifact(true)
            .isFromTrigger(true)
            .version(ParameterField.createValueField(null))
            .versionRegex(ParameterField.createValueField("regex"))
            .scripts(CustomArtifactScripts.builder()
                         .fetchAllArtifacts(
                             FetchAllArtifacts.builder()
                                 .artifactsArrayPath(ParameterField.createValueField("results"))
                                 .versionPath(ParameterField.createValueField("version"))
                                 .shellScriptBaseStepInfo(
                                     CustomArtifactScriptInfo.builder()
                                         .source(CustomArtifactScriptSourceWrapper.builder()
                                                     .type("Inline")
                                                     .spec(CustomScriptInlineSource.builder()
                                                               .script(ParameterField.createValueField("echo test"))
                                                               .build())
                                                     .build())
                                         .build())
                                 .build())
                         .build())
            .build();
    artifactSourceDelegateRequest = (CustomArtifactDelegateRequest) stepHelper.toSourceDelegateRequest(
        customArtifactConfig, Ambiance.newBuilder().build());
    assertThat(artifactSourceDelegateRequest.getSourceType()).isEqualTo(ArtifactSourceType.CUSTOM_ARTIFACT);
    assertThat(artifactSourceDelegateRequest.getExpressionFunctorToken()).isNotEqualTo(0);
  }

  @Test
  @Owner(developers = OwnerRule.ABHISHEK)
  @Category(UnitTests.class)
  public void validateArtifactConfig_Tag() {
    // Prepare test data

    List<ArtifactSourceType> artifactSourceTypes = getArtifactSourceTypesHavingTagField();
    List<ArtifactConfig> artifactConfigListNull = getArtifactConfigTagNull();
    List<ArtifactConfig> artifactConfigListEmpty = getArtifactConfigTagEmpty();
    List<ArtifactConfig> artifactConfigListInput = getArtifactConfigTagInput();
    List<ArtifactConfig> artifactConfigListNullRegex = getArtifactConfigTagRegexNull();
    List<ArtifactConfig> artifactConfigListEmptyRegex = getArtifactConfigTagRegexEmpty();
    List<ArtifactConfig> artifactConfigListInputRegex = getArtifactConfigTagRegexInput();

    for (int i = 0; i < artifactSourceTypes.size(); i++) {
      ArtifactSource source1 = ArtifactSource.builder()
                                   .identifier("source1-id")
                                   .sourceType(artifactSourceTypes.get(i))
                                   .spec(artifactConfigListNull.get(i))
                                   .build();

      checkResponse(source1, NULL_TAG_TAG_REGEX_MESSAGE);
      source1.setSpec(artifactConfigListEmpty.get(i));
      checkResponse(source1, NULL_TAG_TAG_REGEX_MESSAGE);
      source1.setSpec(artifactConfigListInput.get(i));
      checkResponse(source1, NULL_TAG_TAG_REGEX_MESSAGE);
      source1.setSpec(artifactConfigListInputRegex.get(i));
      checkResponse(source1, NULL_TAG_TAG_REGEX_MESSAGE);
      source1.setSpec(artifactConfigListNullRegex.get(i));
      checkResponse(source1, NULL_TAG_TAG_REGEX_MESSAGE);
      source1.setSpec(artifactConfigListEmptyRegex.get(i));
      checkResponse(source1, NULL_TAG_TAG_REGEX_MESSAGE);
    }
  }

  @Test
  @Owner(developers = OwnerRule.ABHISHEK)
  @Category(UnitTests.class)
  public void validateArtifactConfig_Version() {
    // Prepare test data

    List<ArtifactSourceType> artifactSourceTypes = getArtifactSourceTypesHavingVersionField();
    List<ArtifactConfig> artifactConfigListNull = getArtifactConfigVersionNull();
    List<ArtifactConfig> artifactConfigListEmpty = getArtifactConfigVersionEmpty();
    List<ArtifactConfig> artifactConfigListInput = getArtifactConfigVersionInput();
    List<ArtifactConfig> artifactConfigListNullRegex = getArtifactConfigVersionRegexNull();
    List<ArtifactConfig> artifactConfigListEmptyRegex = getArtifactConfigVersionRegexEmpty();
    List<ArtifactConfig> artifactConfigListInputRegex = getArtifactConfigVersionRegexInput();

    for (int i = 0; i < artifactSourceTypes.size(); i++) {
      ArtifactSource source1 = ArtifactSource.builder()
                                   .identifier("source1-id")
                                   .sourceType(artifactSourceTypes.get(i))
                                   .spec(artifactConfigListNull.get(i))
                                   .build();
      checkResponse(source1, NULL_VERSION_VERSION_REGEX_MESSAGE);
      source1.setSpec(artifactConfigListEmpty.get(i));
      checkResponse(source1, NULL_VERSION_VERSION_REGEX_MESSAGE);
      source1.setSpec(artifactConfigListInput.get(i));
      checkResponse(source1, NULL_VERSION_VERSION_REGEX_MESSAGE);
      source1.setSpec(artifactConfigListInputRegex.get(i));
      checkResponse(source1, NULL_VERSION_VERSION_REGEX_MESSAGE);
      source1.setSpec(artifactConfigListNullRegex.get(i));
      checkResponse(source1, NULL_VERSION_VERSION_REGEX_MESSAGE);
      source1.setSpec(artifactConfigListEmptyRegex.get(i));
      checkResponse(source1, NULL_VERSION_VERSION_REGEX_MESSAGE);
    }
  }

  @Test
  @Owner(developers = OwnerRule.ABHISHEK)
  @Category(UnitTests.class)
  public void validateAmazonS3HubArtifactConfig_NullTag() {
    AmazonS3ArtifactConfig amazonS3ArtifactConfig =
        AmazonS3ArtifactConfig.builder().connectorRef(CONNECTOR).filePath(TAG_NULL).build();

    // Prepare test data
    ArtifactSource source1 = ArtifactSource.builder()
                                 .identifier("source1-id")
                                 .sourceType(ArtifactSourceType.AMAZONS3)
                                 .spec(amazonS3ArtifactConfig)
                                 .build();

    checkResponse(source1, NULL_FILEPATH_FILEPATH_REGEX_MESSAGE);

    amazonS3ArtifactConfig.setFilePath(null);
    amazonS3ArtifactConfig.setFilePathRegex(TAG_NULL);

    source1.setSpec(amazonS3ArtifactConfig);

    checkResponse(source1, NULL_FILEPATH_FILEPATH_REGEX_MESSAGE);
  }

  @Test
  @Owner(developers = OwnerRule.ABHISHEK)
  @Category(UnitTests.class)
  public void validateAmazonS3ArtifactConfig_InputFilePath() {
    AmazonS3ArtifactConfig amazonS3ArtifactConfig =
        AmazonS3ArtifactConfig.builder().connectorRef(CONNECTOR).filePath(TAG_INPUT).build();

    // Prepare test data
    ArtifactSource source1 = ArtifactSource.builder()
                                 .identifier("source1-id")
                                 .sourceType(ArtifactSourceType.AMAZONS3)
                                 .spec(amazonS3ArtifactConfig)
                                 .build();

    checkResponse(source1, NULL_FILEPATH_FILEPATH_REGEX_MESSAGE);

    amazonS3ArtifactConfig.setFilePath(null);
    amazonS3ArtifactConfig.setFilePathRegex(TAG_INPUT);

    source1.setSpec(amazonS3ArtifactConfig);

    checkResponse(source1, NULL_FILEPATH_FILEPATH_REGEX_MESSAGE);
  }

  @Test
  @Owner(developers = OwnerRule.ABHISHEK)
  @Category(UnitTests.class)
  public void validateAmazonS3ArtifactConfig_EmptyFilePath() {
    AmazonS3ArtifactConfig amazonS3ArtifactConfig =
        AmazonS3ArtifactConfig.builder().connectorRef(CONNECTOR).filePath(TAG_EMPTY).build();

    // Prepare test data
    ArtifactSource source1 = ArtifactSource.builder()
                                 .identifier("source1-id")
                                 .sourceType(ArtifactSourceType.AMAZONS3)
                                 .spec(amazonS3ArtifactConfig)
                                 .build();

    checkResponse(source1, NULL_FILEPATH_FILEPATH_REGEX_MESSAGE);

    amazonS3ArtifactConfig.setFilePath(null);
    amazonS3ArtifactConfig.setFilePathRegex(TAG_EMPTY);

    source1.setSpec(amazonS3ArtifactConfig);

    checkResponse(source1, NULL_FILEPATH_FILEPATH_REGEX_MESSAGE);
  }
}
