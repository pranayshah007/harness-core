/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.sm.states;

import static io.harness.annotations.dev.HarnessModule._870_CG_ORCHESTRATION;
import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.beans.OrchestrationWorkflowType.BUILD;
import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.delegate.beans.pcf.ResizeStrategy.RESIZE_NEW_FIRST;
import static io.harness.delegate.task.cloudformation.CloudformationBaseHelperImpl.CLOUDFORMATION_STACK_CREATE_BODY;
import static io.harness.exception.WingsException.USER;
import static io.harness.rule.OwnerRule.ADWAIT;
import static io.harness.rule.OwnerRule.RAFAEL;
import static io.harness.rule.OwnerRule.SRINIVAS;
import static io.harness.rule.OwnerRule.TATHAGAT;

import static software.wings.api.CommandStateExecutionData.Builder.aCommandStateExecutionData;
import static software.wings.beans.Application.Builder.anApplication;
import static software.wings.beans.AwsInfrastructureMapping.Builder.anAwsInfrastructureMapping;
import static software.wings.beans.CloudFormationSourceType.TEMPLATE_BODY;
import static software.wings.beans.Environment.Builder.anEnvironment;
import static software.wings.beans.SettingAttribute.Builder.aSettingAttribute;
import static software.wings.beans.command.Command.Builder.aCommand;
import static software.wings.beans.command.ServiceCommand.Builder.aServiceCommand;
import static software.wings.persistence.artifact.Artifact.Builder.anArtifact;
import static software.wings.sm.StateExecutionInstance.Builder.aStateExecutionInstance;
import static software.wings.sm.WorkflowStandardParams.Builder.aWorkflowStandardParams;
import static software.wings.utils.WingsTestConstants.ACCOUNT_ID;
import static software.wings.utils.WingsTestConstants.ACTIVITY_ID;
import static software.wings.utils.WingsTestConstants.APP_ID;
import static software.wings.utils.WingsTestConstants.APP_NAME;
import static software.wings.utils.WingsTestConstants.ARTIFACT_ID;
import static software.wings.utils.WingsTestConstants.ARTIFACT_STREAM_ID;
import static software.wings.utils.WingsTestConstants.ENV_ID;
import static software.wings.utils.WingsTestConstants.ENV_NAME;
import static software.wings.utils.WingsTestConstants.INFRA_DEFINITION_ID;
import static software.wings.utils.WingsTestConstants.INFRA_MAPPING_ID;
import static software.wings.utils.WingsTestConstants.PCF_SERVICE_NAME;
import static software.wings.utils.WingsTestConstants.SERVICE_ID;
import static software.wings.utils.WingsTestConstants.SERVICE_NAME;
import static software.wings.utils.WingsTestConstants.SETTING_ID;
import static software.wings.utils.WingsTestConstants.STATE_NAME;
import static software.wings.utils.WingsTestConstants.WORKFLOW_EXECUTION_ID;

import static com.amazonaws.services.cloudformation.model.StackStatus.ROLLBACK_COMPLETE;
import static java.util.Arrays.asList;
import static java.util.Collections.emptyMap;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.joor.Reflect.on;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.beans.ArtifactMetadata;
import io.harness.beans.DelegateTask;
import io.harness.beans.EmbeddedUser;
import io.harness.beans.ExecutionStatus;
import io.harness.beans.FeatureName;
import io.harness.beans.SweepingOutputInstance;
import io.harness.beans.TriggeredBy;
import io.harness.category.element.UnitTests;
import io.harness.context.ContextElementType;
import io.harness.delegate.utils.DelegateTaskMigrationHelper;
import io.harness.exception.InvalidRequestException;
import io.harness.ff.FeatureFlagService;
import io.harness.rule.Owner;

import software.wings.WingsBaseTest;
import software.wings.api.ContainerServiceElement;
import software.wings.api.ContextElementParamMapperFactory;
import software.wings.api.DeploymentType;
import software.wings.api.PhaseElement;
import software.wings.api.ServiceElement;
import software.wings.api.WorkflowElement;
import software.wings.app.MainConfiguration;
import software.wings.app.PortalConfig;
import software.wings.beans.Activity;
import software.wings.beans.Application;
import software.wings.beans.AwsConfig;
import software.wings.beans.AwsInfrastructureMapping;
import software.wings.beans.BlueprintProperty;
import software.wings.beans.CloudFormationInfrastructureProvisioner;
import software.wings.beans.EntityType;
import software.wings.beans.Environment;
import software.wings.beans.InfraMappingSweepingOutput;
import software.wings.beans.InfrastructureMappingBlueprint;
import software.wings.beans.Service;
import software.wings.beans.ServiceVariable;
import software.wings.beans.ServiceVariableType;
import software.wings.beans.SettingAttribute;
import software.wings.beans.TemplateExpression;
import software.wings.beans.WorkflowExecution;
import software.wings.beans.artifact.ArtifactMetadataKeys;
import software.wings.beans.artifact.ArtifactStream;
import software.wings.beans.artifact.JenkinsArtifactStream;
import software.wings.beans.command.CommandType;
import software.wings.beans.command.ServiceCommand;
import software.wings.common.InfrastructureConstants;
import software.wings.common.TemplateExpressionProcessor;
import software.wings.common.VariableProcessor;
import software.wings.expression.ManagerExpressionEvaluator;
import software.wings.helpers.ext.cloudformation.request.CloudFormationCommandRequest.CloudFormationCommandType;
import software.wings.helpers.ext.cloudformation.request.CloudFormationCreateStackRequest;
import software.wings.helpers.ext.cloudformation.request.CloudFormationDeleteStackRequest;
import software.wings.helpers.ext.url.SubdomainUrlHelperIntfc;
import software.wings.infra.AwsAmiInfrastructure;
import software.wings.infra.InfrastructureDefinition;
import software.wings.persistence.artifact.Artifact;
import software.wings.service.ServiceHelper;
import software.wings.service.intfc.AccountService;
import software.wings.service.intfc.ActivityService;
import software.wings.service.intfc.AppService;
import software.wings.service.intfc.ArtifactService;
import software.wings.service.intfc.ArtifactStreamService;
import software.wings.service.intfc.ArtifactStreamServiceBindingService;
import software.wings.service.intfc.DelegateService;
import software.wings.service.intfc.EnvironmentService;
import software.wings.service.intfc.InfrastructureDefinitionService;
import software.wings.service.intfc.InfrastructureMappingService;
import software.wings.service.intfc.InfrastructureProvisionerService;
import software.wings.service.intfc.ServiceResourceService;
import software.wings.service.intfc.ServiceTemplateService;
import software.wings.service.intfc.SettingsService;
import software.wings.service.intfc.StateExecutionService;
import software.wings.service.intfc.WorkflowExecutionService;
import software.wings.service.intfc.security.SecretManager;
import software.wings.service.intfc.sweepingoutput.SweepingOutputService;
import software.wings.settings.SettingVariableTypes;
import software.wings.sm.ExecutionContext;
import software.wings.sm.ExecutionContextImpl;
import software.wings.sm.ExecutionResponse;
import software.wings.sm.StateExecutionInstance;
import software.wings.sm.WorkflowStandardParams;
import software.wings.sm.WorkflowStandardParamsExtensionService;
import software.wings.sm.states.provision.CloudFormationCreateStackState;
import software.wings.sm.states.provision.CloudFormationDeleteStackState;
import software.wings.sm.states.provision.CloudFormationState.CloudFormationStateKeys;

import com.amazonaws.regions.Regions;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.inject.Inject;
import java.util.Arrays;
import java.util.List;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;

@OwnedBy(CDP)
@TargetModule(_870_CG_ORCHESTRATION)
public class CloudFormationStateTest extends WingsBaseTest {
  private static final String BASE_URL = "https://env.harness.io/";
  public static final String ENV_ID_CF = "abcdefgh";
  public static final String INFRA_PROV_ID = "12345678";
  public static final String EXPECTED_SUFFIX = "abcdefgh12345678";
  private static final String PHASE_NAME = "phaseName";

  private static final String CLOUD_PROVIDER_EXPRESSION = "${infra.cloudProvider.name}";

  @Mock private SettingsService settingsService;
  @Mock private DelegateService delegateService;
  @Mock private ServiceResourceService serviceResourceService;
  @Mock private ActivityService activityService;
  @Mock private InfrastructureDefinitionService infrastructureDefinitionService;
  @Mock private InfrastructureMappingService infrastructureMappingService;
  @Mock private AppService appService;
  @Mock private EnvironmentService environmentService;
  @Mock private ServiceTemplateService serviceTemplateService;
  @Mock private SecretManager secretManager;
  @Mock private WorkflowExecutionService workflowExecutionService;
  @Mock private ArtifactService artifactService;
  @Mock private ArtifactStreamService artifactStreamService;
  @Mock private ArtifactStreamServiceBindingService artifactStreamServiceBindingService;
  @Mock private VariableProcessor variableProcessor;
  @Inject private ManagerExpressionEvaluator evaluator;
  @Mock private ServiceHelper serviceHelper;
  @Mock private InfrastructureProvisionerService infrastructureProvisionerService;
  @Mock private AccountService accountService;
  @Inject @InjectMocks private TemplateExpressionProcessor templateExpressionProcessor;
  @Mock private ExecutionContextImpl executionContext;
  @Mock private FeatureFlagService featureFlagService;
  @Mock private SweepingOutputService sweepingOutputService;
  @Mock private SubdomainUrlHelperIntfc subdomainUrlHelper;
  @Mock private StateExecutionService stateExecutionService;
  @Mock private DelegateTaskMigrationHelper delegateTaskMigrationHelper;

  @InjectMocks
  private CloudFormationCreateStackState cloudFormationCreateStackState = new CloudFormationCreateStackState("name");
  @InjectMocks
  private CloudFormationDeleteStackState cloudFormationDeleteStackState = new CloudFormationDeleteStackState("name");

  @Mock private MainConfiguration configuration;

  private ExecutionContext context;

  private WorkflowStandardParams workflowStandardParams =
      aWorkflowStandardParams()
          .withAppId(APP_ID)
          .withEnvId(ENV_ID)
          .withArtifactIds(Lists.newArrayList(ARTIFACT_ID))
          .withWorkflowElement(
              WorkflowElement.builder().variables(ImmutableMap.of("CF_AWS_Config", SETTING_ID)).build())
          .build();

  private ServiceElement serviceElement = ServiceElement.builder().uuid(SERVICE_ID).name(SERVICE_NAME).build();

  @InjectMocks
  private PhaseElement phaseElement = PhaseElement.builder()
                                          .uuid(generateUuid())
                                          .serviceElement(serviceElement)
                                          .infraMappingId(INFRA_MAPPING_ID)
                                          .appId(APP_ID)
                                          .deploymentType(DeploymentType.SSH.name())
                                          .workflowExecutionId(WORKFLOW_EXECUTION_ID)
                                          .phaseName(PHASE_NAME)
                                          .build();

  private StateExecutionInstance stateExecutionInstance =
      aStateExecutionInstance()
          .displayName(STATE_NAME)
          .addContextElement(workflowStandardParams)
          .addContextElement(phaseElement)
          .addContextElement(ContainerServiceElement.builder()
                                 .uuid(serviceElement.getUuid())
                                 .maxInstances(10)
                                 .name(PCF_SERVICE_NAME)
                                 .resizeStrategy(RESIZE_NEW_FIRST)
                                 .infraMappingId(INFRA_MAPPING_ID)
                                 .deploymentType(DeploymentType.PCF)
                                 .build())
          .addStateExecutionData(aCommandStateExecutionData().build())
          .build();

  private Application app = anApplication().uuid(APP_ID).name(APP_NAME).accountId(ACCOUNT_ID).build();
  private Environment env = anEnvironment().appId(APP_ID).uuid(ENV_ID_CF).name(ENV_NAME).build();
  private Service service = Service.builder()
                                .appId(APP_ID)
                                .uuid(SERVICE_ID)
                                .name(SERVICE_NAME)
                                .artifactStreamIds(singletonList(ARTIFACT_STREAM_ID))
                                .build();
  private Artifact artifact =
      anArtifact()
          .withArtifactSourceName("source")
          .withMetadata(new ArtifactMetadata(ImmutableMap.of(ArtifactMetadataKeys.buildNo, "bn")))
          .withArtifactStreamId(ARTIFACT_STREAM_ID)
          .build();
  private ArtifactStream artifactStream =
      JenkinsArtifactStream.builder().appId(APP_ID).sourceName("").jobname("").artifactPaths(null).build();

  private SettingAttribute awsConfig = aSettingAttribute().withValue(AwsConfig.builder().build()).build();

  private List<ServiceVariable> serviceVariableList = asList(
      ServiceVariable.builder().type(ServiceVariableType.TEXT).name("VAR_1").value("value1".toCharArray()).build(),
      ServiceVariable.builder()
          .type(ServiceVariableType.ENCRYPTED_TEXT)
          .name("VAR_2")
          .value("value2".toCharArray())
          .build());

  private List<ServiceVariable> safeDisplayServiceVariableList = asList(
      ServiceVariable.builder().type(ServiceVariableType.TEXT).name("VAR_1").value("value1".toCharArray()).build(),
      ServiceVariable.builder()
          .type(ServiceVariableType.ENCRYPTED_TEXT)
          .name("VAR_2")
          .value("*******".toCharArray())
          .build());

  private String outputName = InfrastructureConstants.PHASE_INFRA_MAPPING_KEY_NAME + phaseElement.getUuid();
  private SweepingOutputInstance sweepingOutputInstance =
      SweepingOutputInstance.builder()
          .appId(APP_ID)
          .name(outputName)
          .uuid(generateUuid())
          .workflowExecutionId(WORKFLOW_EXECUTION_ID)
          .stateExecutionId(null)
          .pipelineExecutionId(null)
          .value(InfraMappingSweepingOutput.builder().infraMappingId(INFRA_MAPPING_ID).build())
          .build();

  @Before
  public void setup() throws IllegalAccessException {
    when(infrastructureProvisionerService.get(nullable(String.class), nullable(String.class)))
        .thenReturn(
            CloudFormationInfrastructureProvisioner.builder()
                .uuid(INFRA_PROV_ID)
                .appId(APP_ID)
                .awsConfigId("id")
                .name("InfraMaappingProvisioner")
                .templateBody("Template Body")
                .sourceType(TEMPLATE_BODY.name())
                .mappingBlueprints(Arrays.asList(InfrastructureMappingBlueprint.builder()
                                                     .serviceId(SERVICE_ID)
                                                     .properties(Arrays.asList(BlueprintProperty.builder()
                                                                                   .name("$(cloudformation.region)")
                                                                                   .value(Regions.US_EAST_1.name())
                                                                                   .build()))
                                                     .build()))
                .build());

    when(appService.get(APP_ID)).thenReturn(app);
    when(appService.getApplicationWithDefaults(APP_ID)).thenReturn(app);
    when(serviceResourceService.getWithDetails(APP_ID, SERVICE_ID)).thenReturn(service);
    when(artifactStreamServiceBindingService.listArtifactStreamIds(APP_ID, SERVICE_ID))
        .thenReturn(singletonList(ARTIFACT_STREAM_ID));
    when(artifactStreamServiceBindingService.listArtifactStreamIds(SERVICE_ID))
        .thenReturn(singletonList(ARTIFACT_STREAM_ID));
    when(environmentService.get(APP_ID, ENV_ID, false)).thenReturn(env);
    when(sweepingOutputService.find(any())).thenReturn(sweepingOutputInstance);

    ServiceCommand serviceCommand =
        aServiceCommand()
            .withCommand(aCommand().withCommandType(CommandType.SETUP).withName("Setup Service Cluster").build())
            .build();
    when(serviceResourceService.getCommandByName(APP_ID, SERVICE_ID, ENV_ID, "Setup Service Cluster"))
        .thenReturn(serviceCommand);

    WorkflowStandardParamsExtensionService workflowStandardParamsExtensionService =
        new WorkflowStandardParamsExtensionService(appService, accountService, artifactService, environmentService,
            artifactStreamServiceBindingService, null, featureFlagService);

    on(cloudFormationCreateStackState)
        .set("workflowStandardParamsExtensionService", workflowStandardParamsExtensionService);
    on(cloudFormationDeleteStackState)
        .set("workflowStandardParamsExtensionService", workflowStandardParamsExtensionService);

    ContextElementParamMapperFactory contextElementParamMapperFactory = new ContextElementParamMapperFactory(
        subdomainUrlHelper, workflowExecutionService, artifactService, artifactStreamService, null,

        featureFlagService, null, workflowStandardParamsExtensionService);

    workflowStandardParams.setCurrentUser(EmbeddedUser.builder().name("test").email("test@harness.io").build());
    when(executionContext.getContextElement(ContextElementType.STANDARD)).thenReturn(workflowStandardParams);

    when(artifactService.get(any())).thenReturn(artifact);
    when(artifactStreamService.get(any())).thenReturn(artifactStream);

    final AwsInfrastructureMapping infraMapping = anAwsInfrastructureMapping().build();
    infraMapping.setInfrastructureDefinitionId(INFRA_DEFINITION_ID);
    when(infrastructureMappingService.get(APP_ID, INFRA_MAPPING_ID)).thenReturn(infraMapping);
    doReturn(InfrastructureDefinition.builder().infrastructure(AwsAmiInfrastructure.builder().build()).build())
        .when(infrastructureDefinitionService)
        .get(APP_ID, INFRA_DEFINITION_ID);

    Activity activity =
        Activity.builder().triggeredBy(TriggeredBy.builder().name("test").email("test@harness.io").build()).build();
    activity.setUuid(ACTIVITY_ID);
    when(activityService.save(any(Activity.class))).thenReturn(activity);

    when(settingsService.get(any())).thenReturn(awsConfig);

    FieldUtils.writeField(cloudFormationCreateStackState, "secretManager", secretManager, true);
    FieldUtils.writeField(
        cloudFormationCreateStackState, "templateExpressionProcessor", templateExpressionProcessor, true);
    FieldUtils.writeField(
        cloudFormationDeleteStackState, "templateExpressionProcessor", templateExpressionProcessor, true);

    when(workflowExecutionService.getExecutionDetails(
             nullable(String.class), nullable(String.class), anyBoolean(), anyBoolean()))
        .thenReturn(WorkflowExecution.builder().build());
    context = new ExecutionContextImpl(stateExecutionInstance);
    on(context).set("variableProcessor", variableProcessor);
    on(context).set("evaluator", evaluator);
    on(context).set("infrastructureMappingService", infrastructureMappingService);
    on(context).set("infrastructureDefinitionService", infrastructureDefinitionService);
    on(context).set("serviceResourceService", serviceResourceService);
    on(context).set("sweepingOutputService", sweepingOutputService);
    on(context).set("featureFlagService", featureFlagService);
    on(context).set("settingsService", settingsService);
    on(context).set("workflowStandardParamsExtensionService", workflowStandardParamsExtensionService);
    on(context).set("contextElementParamMapperFactory", contextElementParamMapperFactory);

    when(variableProcessor.getVariables(any(), any())).thenReturn(emptyMap());
    //    when(evaluator.substitute(nullable(String.class), anyMap(), nullable(String.class))).thenAnswer(i ->
    //    i.getArguments()[0]);
    PortalConfig portalConfig = new PortalConfig();
    portalConfig.setUrl(BASE_URL);
    when(configuration.getPortal()).thenReturn(portalConfig);
    doNothing().when(serviceHelper).addPlaceholderTexts(any());
    when(featureFlagService.isEnabled(FeatureName.SKIP_BASED_ON_STACK_STATUSES, ACCOUNT_ID)).thenReturn(true);
    when(subdomainUrlHelper.getPortalBaseUrl(any())).thenReturn("baseUrl");
    doNothing().when(stateExecutionService).appendDelegateTaskDetails(nullable(String.class), any());
  }

  @Test
  @Owner(developers = ADWAIT)
  @Category(UnitTests.class)
  public void testExecute_createStackState() {
    cloudFormationCreateStackState.setRegion(Regions.US_EAST_1.name());
    cloudFormationCreateStackState.setTimeoutMillis(1000);
    cloudFormationCreateStackState.setSkipBasedOnStackStatus(true);
    cloudFormationCreateStackState.setStackStatusesToMarkAsSuccess(singletonList("ROLLBACK_COMPLETE"));
    verifyCreateStackRequest();
  }

  @Test
  @Owner(developers = TATHAGAT)
  @Category(UnitTests.class)
  public void testExecute_createStackInBuildWorkflowWithNoEnvId() {
    when(environmentService.get(APP_ID, ENV_ID, false)).thenReturn(null);
    StateExecutionInstance stateExecutionInstance = aStateExecutionInstance()
                                                        .displayName(STATE_NAME)
                                                        .addContextElement(workflowStandardParams)
                                                        .addContextElement(phaseElement)
                                                        .addContextElement(ContainerServiceElement.builder()
                                                                               .uuid(serviceElement.getUuid())
                                                                               .maxInstances(10)
                                                                               .name(PCF_SERVICE_NAME)
                                                                               .resizeStrategy(RESIZE_NEW_FIRST)
                                                                               .infraMappingId(INFRA_MAPPING_ID)
                                                                               .deploymentType(DeploymentType.PCF)
                                                                               .build())
                                                        .addStateExecutionData(aCommandStateExecutionData().build())
                                                        .orchestrationWorkflowType(BUILD)
                                                        .build();
    on(context).set("stateExecutionInstance", stateExecutionInstance);

    cloudFormationCreateStackState.setRegion(Regions.US_EAST_1.name());
    cloudFormationCreateStackState.setTimeoutMillis(1000);

    ExecutionResponse executionResponse = cloudFormationCreateStackState.execute(context);
    assertThat(executionResponse.getExecutionStatus()).isEqualTo(ExecutionStatus.SUCCESS);
  }

  @Test
  @Owner(developers = RAFAEL)
  @Category(UnitTests.class)
  public void shouldResolveInfrastructureProviderExpression() {
    cloudFormationCreateStackState.setInfraCloudProviderAsExpression(true);
    cloudFormationCreateStackState.setInfraCloudProviderExpression(CLOUD_PROVIDER_EXPRESSION);

    doReturn("AWS").when(executionContext).renderExpression(CLOUD_PROVIDER_EXPRESSION);
    doReturn(ACCOUNT_ID).when(executionContext).getAccountId();
    doReturn(awsConfig).when(settingsService).getSettingAttributeByName(ACCOUNT_ID, "AWS");

    assertThat(cloudFormationCreateStackState.resolveInfraStructureProviderFromExpression(executionContext))
        .isEqualTo(awsConfig.getValue());
  }

  @Test
  @Owner(developers = RAFAEL)
  @Category(UnitTests.class)
  public void shouldThrowInvalidRequestWhenInfraCloudProviderExpressionIsNull() {
    assertThatThrownBy(() -> {
      cloudFormationCreateStackState.setInfraCloudProviderExpression(null);
      cloudFormationCreateStackState.resolveInfraStructureProviderFromExpression(context);
    })
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("Infrastructure Provider expression is set but value not provided")
        .hasFieldOrPropertyWithValue("reportTargets", USER);
  }

  @Test
  @Owner(developers = RAFAEL)
  @Category(UnitTests.class)
  public void shouldThrowInvalidRequestWhenInfraCloudProviderExpressionIsEmpty() {
    assertThatThrownBy(() -> {
      cloudFormationCreateStackState.setInfraCloudProviderExpression("");
      cloudFormationCreateStackState.resolveInfraStructureProviderFromExpression(context);
    })
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("Infrastructure Provider expression is set but value not provided")
        .hasFieldOrPropertyWithValue("reportTargets", USER);
  }

  @Test
  @Owner(developers = RAFAEL)
  @Category(UnitTests.class)
  public void shouldThrowInvalidRequestWhenRenderedExpressionIsEmpty() {
    String expression = "${dummyExpression}";
    cloudFormationCreateStackState.setInfraCloudProviderExpression(expression);
    cloudFormationCreateStackState.setInfraCloudProviderAsExpression(true);

    assertThatThrownBy(() -> {
      doReturn("").when(executionContext).renderExpression(expression);
      cloudFormationCreateStackState.resolveInfraStructureProviderFromExpression(executionContext);
    })
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("Infrastructure provider expression is invalid")
        .hasFieldOrPropertyWithValue("reportTargets", USER);
  }

  @Test
  @Owner(developers = RAFAEL)
  @Category(UnitTests.class)
  public void shouldThrowInvalidExpressionWhenRenderedExpressionIsNotEmpty() {
    String expression = "${dummyExpression}";
    cloudFormationCreateStackState.setInfraCloudProviderExpression(expression);
    cloudFormationCreateStackState.setInfraCloudProviderAsExpression(true);

    assertThatThrownBy(() -> {
      when(executionContext.renderExpression(expression)).thenReturn("path/test");
      when(settingsService.getSettingAttributeByName(ACCOUNT_ID, "path/test")).thenReturn(null);
      cloudFormationCreateStackState.resolveInfraStructureProviderFromExpression(executionContext);
    })
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("Infrastructure provider expression doesn't contains valid AWS configuration")
        .hasFieldOrPropertyWithValue("reportTargets", USER);
  }

  @Test
  @Owner(developers = RAFAEL)
  @Category(UnitTests.class)
  public void shouldExecuteCreateStateWithAwsExpression() {
    cloudFormationCreateStackState.setRegion(Regions.US_EAST_1.name());
    cloudFormationCreateStackState.setTimeoutMillis(1000);
    cloudFormationCreateStackState.setInfraCloudProviderExpression(CLOUD_PROVIDER_EXPRESSION);
    cloudFormationCreateStackState.setInfraCloudProviderAsExpression(true);

    when(settingsService.getSettingAttributeByName(
             ACCOUNT_ID, "InfraMappingSweepingOutput(infraMappingId=INFRA_MAPPING_ID)"))
        .thenReturn(awsConfig);

    cloudFormationCreateStackState.setSkipBasedOnStackStatus(true);
    cloudFormationCreateStackState.setStackStatusesToMarkAsSuccess(singletonList("ROLLBACK_COMPLETE"));

    verifyCreateStackRequest();
    verify(settingsService)
        .getSettingAttributeByName(ACCOUNT_ID, "InfraMappingSweepingOutput(infraMappingId=INFRA_MAPPING_ID)");
  }

  @Test
  @Owner(developers = RAFAEL)
  @Category(UnitTests.class)
  public void shouldExecuteDeleteStateWithAwsExpression() {
    cloudFormationDeleteStackState.setRegion(Regions.US_EAST_1.name());
    cloudFormationDeleteStackState.setTimeoutMillis(1000);
    cloudFormationDeleteStackState.setInfraCloudProviderExpression(CLOUD_PROVIDER_EXPRESSION);
    cloudFormationDeleteStackState.setInfraCloudProviderAsExpression(true);

    when(settingsService.getSettingAttributeByName(
             ACCOUNT_ID, "InfraMappingSweepingOutput(infraMappingId=INFRA_MAPPING_ID)"))
        .thenReturn(awsConfig);

    verifyDeleteStackRequest();
    verify(settingsService)
        .getSettingAttributeByName(ACCOUNT_ID, "InfraMappingSweepingOutput(infraMappingId=INFRA_MAPPING_ID)");
  }

  @Test
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  public void testExecute_createStackStateWithAwsTemplatized() {
    cloudFormationCreateStackState.setRegion(Regions.US_EAST_1.name());
    cloudFormationCreateStackState.setTimeoutMillis(1000);

    cloudFormationCreateStackState.setTemplateExpressions(
        asList(TemplateExpression.builder()
                   .fieldName(CloudFormationStateKeys.awsConfigId)
                   .expression("${CF_AWS_Config}")
                   .metadata(ImmutableMap.of("entityType", EntityType.CF_AWS_CONFIG_ID))
                   .build()));

    when(settingsService.get(SETTING_ID)).thenReturn(null);

    when(settingsService.fetchSettingAttributeByName(ACCOUNT_ID, SETTING_ID, SettingVariableTypes.AWS))
        .thenReturn(awsConfig);
    cloudFormationCreateStackState.setSkipBasedOnStackStatus(true);
    cloudFormationCreateStackState.setStackStatusesToMarkAsSuccess(singletonList("ROLLBACK_COMPLETE"));

    verifyCreateStackRequest();
    verify(settingsService).fetchSettingAttributeByName(ACCOUNT_ID, SETTING_ID, SettingVariableTypes.AWS);
  }

  private void verifyCreateStackRequest() {
    ExecutionResponse executionResponse = cloudFormationCreateStackState.execute(context);
    assertThat(executionResponse.getExecutionStatus()).isEqualTo(ExecutionStatus.SUCCESS);

    ArgumentCaptor<DelegateTask> captor = ArgumentCaptor.forClass(DelegateTask.class);
    verify(delegateService).queueTaskV2(captor.capture());
    DelegateTask delegateTask = captor.getValue();

    CloudFormationCreateStackRequest cloudFormationCreateStackRequest =
        (CloudFormationCreateStackRequest) delegateTask.getData().getParameters()[0];
    assertThat(cloudFormationCreateStackRequest).isNotNull();
    assertThat(cloudFormationCreateStackRequest.getRegion()).isEqualTo(Regions.US_EAST_1.name());
    assertThat(cloudFormationCreateStackRequest.getCommandType()).isEqualTo(CloudFormationCommandType.CREATE_STACK);
    assertThat(cloudFormationCreateStackRequest.getAppId()).isEqualTo(APP_ID);
    assertThat(cloudFormationCreateStackRequest.getAccountId()).isEqualTo(ACCOUNT_ID);
    assertThat(cloudFormationCreateStackRequest.getCommandName()).isEqualTo("Create Stack");
    assertThat(cloudFormationCreateStackRequest.getStackStatusesToMarkAsSuccess()).containsExactly(ROLLBACK_COMPLETE);
    assertThat(cloudFormationCreateStackRequest.getCreateType()).isEqualTo(CLOUDFORMATION_STACK_CREATE_BODY);
    assertThat(cloudFormationCreateStackRequest.getData()).isEqualTo("Template Body");
    assertThat(cloudFormationCreateStackRequest.getTimeoutInMs()).isEqualTo(1000);
  }

  @Test
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  public void testExecute_deleteStackState() {
    cloudFormationDeleteStackState.setRegion(Regions.US_EAST_1.name());
    cloudFormationDeleteStackState.setTimeoutMillis(1000);

    verifyDeleteStackRequest();
  }

  @Test
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  public void testExecute_deleteStackStateAwsTempaltized() {
    cloudFormationDeleteStackState.setRegion(Regions.US_EAST_1.name());
    cloudFormationDeleteStackState.setTimeoutMillis(1000);

    cloudFormationDeleteStackState.setTemplateExpressions(
        asList(TemplateExpression.builder()
                   .fieldName(CloudFormationStateKeys.awsConfigId)
                   .expression("${CF_AWS_Config}")
                   .metadata(ImmutableMap.of("entityType", EntityType.CF_AWS_CONFIG_ID))
                   .build()));

    when(settingsService.get(SETTING_ID)).thenReturn(null);
    when(settingsService.fetchSettingAttributeByName(ACCOUNT_ID, SETTING_ID, SettingVariableTypes.AWS))
        .thenReturn(awsConfig);

    verifyDeleteStackRequest();

    verify(settingsService).fetchSettingAttributeByName(ACCOUNT_ID, SETTING_ID, SettingVariableTypes.AWS);
  }

  private void verifyDeleteStackRequest() {
    ExecutionResponse executionResponse = cloudFormationDeleteStackState.execute(context);
    assertThat(executionResponse.getExecutionStatus()).isEqualTo(ExecutionStatus.SUCCESS);

    ArgumentCaptor<DelegateTask> captor = ArgumentCaptor.forClass(DelegateTask.class);
    verify(delegateService).queueTaskV2(captor.capture());
    DelegateTask delegateTask = captor.getValue();

    CloudFormationDeleteStackRequest cloudFormationDeleteStackRequest =
        (CloudFormationDeleteStackRequest) delegateTask.getData().getParameters()[0];
    assertThat(cloudFormationDeleteStackRequest).isNotNull();
    assertThat(cloudFormationDeleteStackRequest.getRegion()).isEqualTo(Regions.US_EAST_1.name());
    assertThat(cloudFormationDeleteStackRequest.getCommandType()).isEqualTo(CloudFormationCommandType.DELETE_STACK);
    assertThat(cloudFormationDeleteStackRequest.getAppId()).isEqualTo(APP_ID);
    assertThat(cloudFormationDeleteStackRequest.getAccountId()).isEqualTo(ACCOUNT_ID);
    assertThat(cloudFormationDeleteStackRequest.getCommandName()).isEqualTo("Delete Stack");
    assertThat(cloudFormationDeleteStackRequest.getStackNameSuffix()).isEqualTo(EXPECTED_SUFFIX);
    assertThat(cloudFormationDeleteStackRequest.getTimeoutInMs()).isEqualTo(1000);
  }

  @Test
  @Owner(developers = TATHAGAT)
  @Category(UnitTests.class)
  public void testValidation() {
    // create stack
    assertThat(cloudFormationCreateStackState.validateFields().size()).isEqualTo(1);
    cloudFormationCreateStackState.setProvisionerId("test provisioner");
    assertThat(cloudFormationCreateStackState.validateFields().size()).isEqualTo(0);

    // delete stack
    assertThat(cloudFormationDeleteStackState.validateFields().size()).isEqualTo(1);
    cloudFormationDeleteStackState.setProvisionerId("test provisioner");
    assertThat(cloudFormationDeleteStackState.validateFields().size()).isEqualTo(0);
  }
}
