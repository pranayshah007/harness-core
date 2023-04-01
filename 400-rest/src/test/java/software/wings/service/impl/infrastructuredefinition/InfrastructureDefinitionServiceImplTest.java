/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl.infrastructuredefinition;

import static io.harness.beans.PageResponse.PageResponseBuilder.aPageResponse;
import static io.harness.beans.SearchFilter.Operator.EQ;
import static io.harness.beans.SearchFilter.Operator.IN;
import static io.harness.beans.SearchFilter.Operator.NOT_EXISTS;
import static io.harness.beans.SearchFilter.Operator.OR;
import static io.harness.rule.OwnerRule.ACHYUTH;
import static io.harness.rule.OwnerRule.ADWAIT;
import static io.harness.rule.OwnerRule.ANIL;
import static io.harness.rule.OwnerRule.ARVIND;
import static io.harness.rule.OwnerRule.BOJANA;
import static io.harness.rule.OwnerRule.DINESH;
import static io.harness.rule.OwnerRule.GEORGE;
import static io.harness.rule.OwnerRule.NAMAN_TALAYCHA;
import static io.harness.rule.OwnerRule.POOJA;
import static io.harness.rule.OwnerRule.PRASHANT;
import static io.harness.rule.OwnerRule.RAGHVENDRA;
import static io.harness.rule.OwnerRule.RAUNAK;
import static io.harness.rule.OwnerRule.RIHAZ;
import static io.harness.rule.OwnerRule.ROHIT_KUMAR;
import static io.harness.rule.OwnerRule.SATYAM;
import static io.harness.rule.OwnerRule.VAIBHAV_SI;
import static io.harness.rule.OwnerRule.YOGESH;

import static software.wings.beans.InfrastructureType.AWS_ECS;
import static software.wings.beans.InfrastructureType.GCP_KUBERNETES_ENGINE;
import static software.wings.beans.InfrastructureType.PHYSICAL_INFRA;
import static software.wings.beans.InfrastructureType.PHYSICAL_INFRA_WINRM;
import static software.wings.beans.PhysicalDataCenterConfig.Builder.aPhysicalDataCenterConfig;
import static software.wings.beans.SettingAttribute.Builder.aSettingAttribute;
import static software.wings.beans.Variable.VariableBuilder.aVariable;
import static software.wings.common.InfrastructureConstants.INFRA_KUBERNETES_INFRAID_EXPRESSION;
import static software.wings.infra.InfraDefinitionTestConstants.INFRA_DEFINITION_ID;
import static software.wings.infra.InfraDefinitionTestConstants.INFRA_DEFINITION_NAME;
import static software.wings.infra.InfraDefinitionTestConstants.INFRA_PROVISIONER_ID;
import static software.wings.infra.InfraDefinitionTestConstants.REGION;
import static software.wings.utils.WingsTestConstants.ACCOUNT_ID;
import static software.wings.utils.WingsTestConstants.APP_ID;
import static software.wings.utils.WingsTestConstants.COMPUTE_PROVIDER_ID;
import static software.wings.utils.WingsTestConstants.ENV_ID;
import static software.wings.utils.WingsTestConstants.HOST_NAME;
import static software.wings.utils.WingsTestConstants.INFRA_MAPPING_ID;
import static software.wings.utils.WingsTestConstants.INFRA_NAME;
import static software.wings.utils.WingsTestConstants.PROVISIONER_ID;
import static software.wings.utils.WingsTestConstants.SERVICE_ID;
import static software.wings.utils.WingsTestConstants.SETTING_ID;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static org.apache.commons.lang3.StringUtils.EMPTY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.beans.ExecutionStatus;
import io.harness.beans.PageRequest;
import io.harness.beans.PageResponse;
import io.harness.beans.SearchFilter;
import io.harness.category.element.UnitTests;
import io.harness.event.handler.impl.EventPublishHelper;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.WingsException;
import io.harness.queue.QueuePublisher;
import io.harness.rule.Owner;

import software.wings.api.CloudProviderType;
import software.wings.api.DeploymentType;
import software.wings.beans.AwsConfig;
import software.wings.beans.AwsInstanceFilter.AwsInstanceFilterKeys;
import software.wings.beans.Event;
import software.wings.beans.GcpConfig;
import software.wings.beans.NameValuePair;
import software.wings.beans.Service;
import software.wings.beans.SettingAttribute;
import software.wings.beans.SettingAttribute.SettingCategory;
import software.wings.beans.TerraformInfrastructureProvisioner;
import software.wings.beans.Variable;
import software.wings.beans.WorkflowExecution;
import software.wings.beans.customdeployment.CustomDeploymentTypeDTO;
import software.wings.beans.infrastructure.Host;
import software.wings.beans.shellscript.provisioner.ShellScriptInfrastructureProvisioner;
import software.wings.dl.WingsPersistence;
import software.wings.infra.AwsAmiInfrastructure;
import software.wings.infra.AwsAmiInfrastructure.AwsAmiInfrastructureKeys;
import software.wings.infra.AwsEcsInfrastructure;
import software.wings.infra.AwsEcsInfrastructure.AwsEcsInfrastructureKeys;
import software.wings.infra.AwsInstanceInfrastructure;
import software.wings.infra.AwsInstanceInfrastructure.AwsInstanceInfrastructureKeys;
import software.wings.infra.AwsLambdaInfrastructure;
import software.wings.infra.AwsLambdaInfrastructure.AwsLambdaInfrastructureKeys;
import software.wings.infra.AzureInstanceInfrastructure;
import software.wings.infra.AzureWebAppInfra;
import software.wings.infra.AzureWebAppInfra.AzureWebAppInfraKeys;
import software.wings.infra.CustomInfrastructure;
import software.wings.infra.DirectKubernetesInfrastructure;
import software.wings.infra.GoogleKubernetesEngine;
import software.wings.infra.GoogleKubernetesEngine.GoogleKubernetesEngineKeys;
import software.wings.infra.InfrastructureDefinition;
import software.wings.infra.InfrastructureDefinition.InfrastructureDefinitionBuilder;
import software.wings.infra.PcfInfraStructure;
import software.wings.infra.PhysicalInfra;
import software.wings.infra.PhysicalInfraWinrm;
import software.wings.prune.PruneEvent;
import software.wings.service.impl.AwsInfrastructureProvider;
import software.wings.service.impl.aws.model.AwsRoute53HostedZoneData;
import software.wings.service.intfc.AppService;
import software.wings.service.intfc.InfrastructureDefinitionService;
import software.wings.service.intfc.InfrastructureMappingService;
import software.wings.service.intfc.InfrastructureProvider;
import software.wings.service.intfc.InfrastructureProvisionerService;
import software.wings.service.intfc.PipelineService;
import software.wings.service.intfc.ServiceResourceService;
import software.wings.service.intfc.SettingsService;
import software.wings.service.intfc.TriggerService;
import software.wings.service.intfc.WorkflowExecutionService;
import software.wings.service.intfc.WorkflowService;
import software.wings.service.intfc.aws.manager.AwsRoute53HelperServiceManager;
import software.wings.service.intfc.customdeployment.CustomDeploymentTypeService;
import software.wings.service.intfc.security.SecretManager;
import software.wings.service.intfc.yaml.YamlPushService;
import software.wings.sm.ExecutionContext;

import com.amazonaws.regions.Regions;
import com.amazonaws.services.ecs.model.LaunchType;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Maps;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.validation.constraints.NotNull;
import javax.ws.rs.core.AbstractMultivaluedMap;
import javax.ws.rs.core.UriInfo;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;

@OwnedBy(HarnessTeam.CDP)
@TargetModule(HarnessModule._870_CG_ORCHESTRATION)
public class InfrastructureDefinitionServiceImplTest extends CategoryTest {
  @Mock private ExecutionContext executionContext;
  @Mock private WorkflowService workflowService;
  @Mock private PipelineService pipelineService;
  @Mock private TriggerService triggerService;
  @Mock private ServiceResourceService serviceResourceService;
  @Mock private WingsPersistence wingsPersistence;
  @Mock private AppService appService;
  @Mock private SettingsService mockSettingsService;
  @Mock private InfrastructureMappingService infrastructureMappingService;
  @Mock private Map<String, InfrastructureProvider> infrastructureProviderMap;
  @Mock private YamlPushService yamlPushService;
  @Mock private EventPublishHelper eventPublishHelper;
  @Mock private WorkflowExecutionService workflowExecutionService;
  @Mock private CustomDeploymentTypeService customDeploymentTypeService;
  @Mock private InfrastructureProvisionerService infrastructureProvisionerService;
  @Mock private AwsRoute53HelperServiceManager awsRoute53HelperServiceManager;
  @Mock private SecretManager secretManager;
  @Mock private QueuePublisher<PruneEvent> pruneQueue;

  @Spy @InjectMocks private InfrastructureDefinitionServiceImpl infrastructureDefinitionService;

  List<AwsRoute53HostedZoneData> hostedZones1;
  List<AwsRoute53HostedZoneData> hostedZones2;

  private static final String DEFAULT = "default";
  private static final String USER_INPUT_NAMESPACE = "USER_INPUT_NAMESPACE";

  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);
    hostedZones1 = Arrays.asList(AwsRoute53HostedZoneData.builder().hostedZoneName("zone1").build(),
        AwsRoute53HostedZoneData.builder().hostedZoneName("zone2").build());
    hostedZones2 = Arrays.asList(AwsRoute53HostedZoneData.builder().hostedZoneName("zone3").build(),
        AwsRoute53HostedZoneData.builder().hostedZoneName("zone4").build());
  }

  @Test
  @Owner(developers = VAIBHAV_SI)
  @Category(UnitTests.class)
  public void shouldFailOnNonResolutionOfExpressions() {
    String wrongVariable = "${WRONG_VARIABLE}";
    InfrastructureDefinition infrastructureDefinition =
        InfrastructureDefinition.builder()
            .infrastructure(GoogleKubernetesEngine.builder().namespace(wrongVariable).build())
            .build();
    when(executionContext.renderExpression(wrongVariable)).thenReturn(wrongVariable);

    assertThatThrownBy(
        () -> infrastructureDefinitionService.renderExpression(infrastructureDefinition, executionContext))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining(wrongVariable);
  }

  @Test
  @Owner(developers = VAIBHAV_SI)
  @Category(UnitTests.class)
  public void shouldFailOnNullRenderedValueOfExpression() {
    String wrongVariable = "${WRONG_VARIABLE}";
    InfrastructureDefinition infrastructureDefinition =
        InfrastructureDefinition.builder()
            .infrastructure(GoogleKubernetesEngine.builder().namespace(wrongVariable).build())
            .build();
    when(executionContext.renderExpression(wrongVariable)).thenReturn(null);

    assertThatThrownBy(
        () -> infrastructureDefinitionService.renderExpression(infrastructureDefinition, executionContext))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining(wrongVariable);
  }

  @Test
  @Owner(developers = VAIBHAV_SI)
  @Category(UnitTests.class)
  public void shouldFailOnStringNullRenderedValueOfExpression() {
    String wrongVariable = "${WRONG_VARIABLE}";
    InfrastructureDefinition infrastructureDefinition =
        InfrastructureDefinition.builder()
            .infrastructure(GoogleKubernetesEngine.builder().namespace(wrongVariable).build())
            .build();
    when(executionContext.renderExpression(wrongVariable)).thenReturn("null");

    assertThatThrownBy(
        () -> infrastructureDefinitionService.renderExpression(infrastructureDefinition, executionContext))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining(wrongVariable);
  }

  @Test
  @Owner(developers = VAIBHAV_SI)
  @Category(UnitTests.class)
  public void shouldResolveExpressions() {
    String workflowVariable = "abc-${workflow.variables.var}";
    String value = "value";
    InfrastructureDefinition infrastructureDefinition =
        InfrastructureDefinition.builder()
            .infrastructure(GoogleKubernetesEngine.builder().namespace(workflowVariable).build())
            .build();
    when(executionContext.renderExpression(workflowVariable)).thenReturn(value);

    infrastructureDefinitionService.renderExpression(infrastructureDefinition, executionContext);

    assertThat(((GoogleKubernetesEngine) infrastructureDefinition.getInfrastructure()).getNamespace()).isEqualTo(value);
  }

  @Test
  @Owner(developers = VAIBHAV_SI)
  @Category(UnitTests.class)
  public void shouldIgnoreReleaseNameResolutionFailure() {
    InfrastructureDefinition infrastructureDefinition =
        InfrastructureDefinition.builder()
            .infrastructure(GoogleKubernetesEngine.builder().releaseName(INFRA_KUBERNETES_INFRAID_EXPRESSION).build())
            .build();
    when(executionContext.renderExpression(INFRA_KUBERNETES_INFRAID_EXPRESSION))
        .thenReturn(INFRA_KUBERNETES_INFRAID_EXPRESSION);

    infrastructureDefinitionService.renderExpression(infrastructureDefinition, executionContext);

    assertThat(((GoogleKubernetesEngine) infrastructureDefinition.getInfrastructure()).getReleaseName())
        .isEqualTo(INFRA_KUBERNETES_INFRAID_EXPRESSION);
  }

  @Test
  @Owner(developers = VAIBHAV_SI)
  @Category(UnitTests.class)
  public void shouldIgnoreReleaseNameWithSomeConstantResolutionFailure() {
    String releaseName = "release-" + INFRA_KUBERNETES_INFRAID_EXPRESSION;
    InfrastructureDefinition infrastructureDefinition =
        InfrastructureDefinition.builder()
            .infrastructure(GoogleKubernetesEngine.builder().releaseName(releaseName).build())
            .build();
    when(executionContext.renderExpression(releaseName)).thenReturn(releaseName);

    infrastructureDefinitionService.renderExpression(infrastructureDefinition, executionContext);

    assertThat(((GoogleKubernetesEngine) infrastructureDefinition.getInfrastructure()).getReleaseName())
        .isEqualTo(releaseName);
  }

  @Test
  @Owner(developers = RAGHVENDRA)
  @Category(UnitTests.class)
  public void shouldIgnoreConfigFileExpressionConstantFailure() {
    String releaseName = "release"
        + "${configFile.getAsString(\"Service_config_file\")}";
    InfrastructureDefinition infrastructureDefinition =
        InfrastructureDefinition.builder()
            .infrastructure(GoogleKubernetesEngine.builder().releaseName(releaseName).build())
            .build();
    when(executionContext.renderExpression(releaseName)).thenReturn(releaseName);

    infrastructureDefinitionService.renderExpression(infrastructureDefinition, executionContext);

    assertThat(((GoogleKubernetesEngine) infrastructureDefinition.getInfrastructure()).getReleaseName())
        .isEqualTo(releaseName);
  }

  @Test
  @Owner(developers = ARVIND)
  @Category(UnitTests.class)
  public void testFailedRenderExpression() {
    InfrastructureDefinition infrastructureDefinition =
        InfrastructureDefinition.builder()
            .uuid(INFRA_DEFINITION_ID)
            .name(INFRA_NAME)
            .appId(APP_ID)
            .deploymentType(DeploymentType.AMI)
            .cloudProviderType(CloudProviderType.AWS)
            .infrastructure(AwsAmiInfrastructure.builder().cloudProviderId("CLOUD_PROVIDER_ID").build())
            .provisionerId(PROVISIONER_ID)
            .build();

    Map<String, Object> expressionMap = new HashMap<>();
    TerraformInfrastructureProvisioner infrastructureProvisioner = TerraformInfrastructureProvisioner.builder().build();
    doReturn(infrastructureProvisioner).when(infrastructureProvisionerService).get(APP_ID, PROVISIONER_ID);
    doReturn(expressionMap).when(infrastructureProvisionerService).resolveExpressions(any(), any(), any());
    doReturn(false)
        .when(infrastructureProvisionerService)
        .areExpressionsValid(infrastructureProvisioner, expressionMap);
    assertThat(infrastructureDefinitionService.renderExpression(infrastructureDefinition, executionContext)).isFalse();
  }

  @Test(expected = InvalidRequestException.class)
  @Owner(developers = ROHIT_KUMAR)
  @Category(UnitTests.class)
  public void testEnsureSafeToDelete_error() {
    final InfrastructureDefinition infrastructureDefinition =
        InfrastructureDefinition.builder().uuid("infraid").name("infra_name").build();

    doReturn(singletonList("workflow1"))
        .when(workflowService)
        .obtainWorkflowNamesReferencedByInfrastructureDefinition(any(), any());
    infrastructureDefinitionService.ensureSafeToDelete("appid", infrastructureDefinition);
  }

  @Test(expected = InvalidRequestException.class)
  @Owner(developers = ROHIT_KUMAR)
  @Category(UnitTests.class)
  public void testEnsureSafeToDelete_error1() {
    final InfrastructureDefinition infrastructureDefinition =
        InfrastructureDefinition.builder().uuid("infraid").name("infra_name").build();

    doReturn(emptyList()).when(workflowService).obtainWorkflowNamesReferencedByInfrastructureDefinition(any(), any());

    doReturn(singletonList("pipeline1"))
        .when(pipelineService)
        .obtainPipelineNamesReferencedByTemplatedEntity(any(), any());

    infrastructureDefinitionService.ensureSafeToDelete("appid", infrastructureDefinition);
  }

  @Test(expected = InvalidRequestException.class)
  @Owner(developers = ROHIT_KUMAR)
  @Category(UnitTests.class)
  public void testEnsureSafeToDelete_error2() {
    final InfrastructureDefinition infrastructureDefinition =
        InfrastructureDefinition.builder().uuid("infraid").name("infra_name").build();

    doReturn(emptyList()).when(workflowService).obtainWorkflowNamesReferencedByInfrastructureDefinition(any(), any());

    doReturn(emptyList()).when(pipelineService).obtainPipelineNamesReferencedByTemplatedEntity(any(), any());

    doReturn(singletonList("triggerid"))
        .when(triggerService)
        .obtainTriggerNamesReferencedByTemplatedEntityId(any(), any());

    infrastructureDefinitionService.ensureSafeToDelete("appid", infrastructureDefinition);
  }

  @Test(expected = InvalidRequestException.class)
  @Owner(developers = BOJANA)
  @Category(UnitTests.class)
  public void testEnsureSafeToDelete_error3() {
    final InfrastructureDefinition infrastructureDefinition =
        InfrastructureDefinition.builder().uuid("infraid").name("infra_name").build();

    doReturn(singletonList(WorkflowExecution.builder().status(ExecutionStatus.RUNNING).name("Test Workflow").build()))
        .when(workflowExecutionService)
        .getRunningExecutionsForInfraDef(any(), any());

    infrastructureDefinitionService.ensureSafeToDelete("appid", infrastructureDefinition);
  }

  @Test
  @Owner(developers = BOJANA)
  @Category(UnitTests.class)
  public void testEnsureSafeToDelete_error4() {
    final InfrastructureDefinition infrastructureDefinition =
        InfrastructureDefinition.builder().uuid("infraid").name("infra_name").build();

    List<WorkflowExecution> retrievedWorkflowExecutions = new ArrayList<>();
    retrievedWorkflowExecutions.add(
        WorkflowExecution.builder().status(ExecutionStatus.RUNNING).name("Test Workflow 1").build());
    retrievedWorkflowExecutions.add(
        WorkflowExecution.builder().status(ExecutionStatus.PAUSED).name("Test Workflow 2").build());
    retrievedWorkflowExecutions.add(
        WorkflowExecution.builder().status(ExecutionStatus.RUNNING).name("Test Workflow 3").build());

    doReturn(retrievedWorkflowExecutions).when(workflowExecutionService).getRunningExecutionsForInfraDef(any(), any());

    try {
      infrastructureDefinitionService.ensureSafeToDelete("appid", infrastructureDefinition);
    } catch (InvalidRequestException e) {
      assertThat(e.getMessage()).containsOnlyOnce("infra_name");
      assertThat(e.getMessage()).containsOnlyOnce("2 running workflows [Test Workflow 1, Test Workflow 3]");
      assertThat(e.getMessage()).containsOnlyOnce("1 paused workflow [Test Workflow 2]");
    }
  }

  @Test
  @Owner(developers = BOJANA)
  @Category(UnitTests.class)
  public void shouldThrowExceptionOnDeleteReferencedByWorkflowExecution() {
    mockPhysicalInfra();
    when(workflowExecutionService.getRunningExecutionsForInfraDef(APP_ID, INFRA_DEFINITION_ID))
        .thenReturn(asList(WorkflowExecution.builder().status(ExecutionStatus.RUNNING).name("Test Workflow").build()));

    assertThatExceptionOfType(WingsException.class)
        .isThrownBy(() -> infrastructureDefinitionService.delete(APP_ID, INFRA_DEFINITION_ID));
  }

  @Test
  @Owner(developers = BOJANA)
  @Category(UnitTests.class)
  public void testDelete() {
    mockPhysicalInfra();
    when(appService.getAccountIdByAppId(any())).thenReturn(ACCOUNT_ID);
    when(workflowExecutionService.getRunningExecutionsForInfraDef(APP_ID, INFRA_DEFINITION_ID)).thenReturn(asList());
    when(workflowService.obtainWorkflowNamesReferencedByInfrastructureDefinition(APP_ID, INFRA_DEFINITION_ID))
        .thenReturn(asList());
    when(pipelineService.obtainPipelineNamesReferencedByTemplatedEntity(APP_ID, INFRA_DEFINITION_ID))
        .thenReturn(asList());
    when(triggerService.obtainTriggerNamesReferencedByTemplatedEntityId(APP_ID, INFRA_DEFINITION_ID))
        .thenReturn(asList());

    infrastructureDefinitionService.delete(APP_ID, INFRA_DEFINITION_ID);

    verify(wingsPersistence, times(1)).delete(any(), anyString(), anyString());
    verify(yamlPushService, times(1))
        .pushYamlChangeSet(eq(ACCOUNT_ID), any(), eq(null), eq(Event.Type.DELETE), eq(false), eq(false));
    ArgumentCaptor<PruneEvent> captor = ArgumentCaptor.forClass(PruneEvent.class);
    verify(pruneQueue, times(1)).send(captor.capture());
    assertThat(captor.getValue().getEntityId()).isEqualTo(INFRA_DEFINITION_ID);
    assertThat(captor.getValue().getAppId()).isEqualTo(APP_ID);
    assertThat(captor.getValue().getEntityClass()).isEqualTo(InfrastructureDefinition.class.getCanonicalName());
  }

  @Test
  @Owner(developers = GEORGE)
  @Category(UnitTests.class)
  public void testGetDeploymentTypeCloudProviderOptions() {
    assertThat(infrastructureDefinitionService.getDeploymentTypeCloudProviderOptions().size()
        == DeploymentType.values().length)
        .isTrue();
  }

  @Test
  @Owner(developers = ANIL)
  @Category(UnitTests.class)
  public void testValidateAzureWebAppInfraWithProvisioner() {
    AzureWebAppInfra infra = AzureWebAppInfra.builder().build();
    Map<String, String> expressions = new HashMap<>();
    infra.setExpressions(expressions);
    assertThatThrownBy(() -> infrastructureDefinitionService.validateAzureWebAppInfraWithProvisioner(infra));

    expressions.put(AzureWebAppInfraKeys.resourceGroup, "testResourceGroup");
    assertThatThrownBy(() -> infrastructureDefinitionService.validateAzureWebAppInfraWithProvisioner(infra))
        .hasMessage("Subscription Id is required");

    expressions.put(AzureWebAppInfraKeys.subscriptionId, "subcription-id");
    infrastructureDefinitionService.validateAzureWebAppInfraWithProvisioner(infra);
  }

  @Test
  @Owner(developers = VAIBHAV_SI)
  @Category(UnitTests.class)
  public void testValidateAwsEcsInfraWithProvisioner() {
    //    InfrastructureDefinitionServiceImpl infrastructureDefinitionService =
    //        (InfrastructureDefinitionServiceImpl) this.infrastructureDefinitionService;
    String randomValue = "val";

    AwsEcsInfrastructure infra = AwsEcsInfrastructure.builder().build();
    Map<String, String> expressions = new HashMap<>();
    infra.setExpressions(expressions);
    assertThatThrownBy(() -> infrastructureDefinitionService.validateAwsEcsInfraWithProvisioner(infra));

    infra.setLaunchType(LaunchType.EC2.toString());
    expressions.put(AwsEcsInfrastructureKeys.region, randomValue);
    expressions.put(AwsEcsInfrastructureKeys.clusterName, randomValue);
    infrastructureDefinitionService.validateAwsEcsInfraWithProvisioner(infra);

    infra.setLaunchType(LaunchType.FARGATE.toString());
    assertThatThrownBy(() -> infrastructureDefinitionService.validateAwsEcsInfraWithProvisioner(infra));
    expressions.put(AwsEcsInfrastructureKeys.executionRole, randomValue);
    expressions.put(AwsEcsInfrastructureKeys.subnetIds, randomValue);
    expressions.put(AwsEcsInfrastructureKeys.securityGroupIds, randomValue);
    expressions.put(AwsEcsInfrastructureKeys.vpcId, randomValue);
    infrastructureDefinitionService.validateAwsEcsInfraWithProvisioner(infra);
  }

  @Test
  @Owner(developers = VAIBHAV_SI)
  @Category(UnitTests.class)
  public void testValidatePhysicalInfraWithProvisioner() {
    //    InfrastructureDefinitionServiceImpl infrastructureDefinitionService =
    //        (InfrastructureDefinitionServiceImpl) this.infrastructureDefinitionService;
    String randomValue = "val";

    PhysicalInfra infra = PhysicalInfra.builder().build();
    Map<String, String> expressions = new HashMap<>();
    infra.setExpressions(expressions);
    assertThatThrownBy(() -> infrastructureDefinitionService.validatePhysicalInfraWithProvisioner(infra));

    expressions.put(PhysicalInfra.hostname, randomValue);
    expressions.put(PhysicalInfra.hostArrayPath, randomValue);
    infrastructureDefinitionService.validatePhysicalInfraWithProvisioner(infra);
  }

  @Test
  @Owner(developers = VAIBHAV_SI)
  @Category(UnitTests.class)
  public void testValidateGoogleKubernetesEngineInfraWithProvisioner() {
    //    InfrastructureDefinitionServiceImpl infrastructureDefinitionService =
    //        (InfrastructureDefinitionServiceImpl) this.infrastructureDefinitionService;
    String randomValue = "val";

    GoogleKubernetesEngine infra = GoogleKubernetesEngine.builder().build();
    Map<String, String> expressions = new HashMap<>();
    infra.setExpressions(expressions);
    assertThatThrownBy(() -> infrastructureDefinitionService.validateGoogleKubernetesEngineInfraWithProvisioner(infra));

    expressions.put(GoogleKubernetesEngineKeys.clusterName, randomValue);
    expressions.put(GoogleKubernetesEngineKeys.namespace, randomValue);
    infrastructureDefinitionService.validateGoogleKubernetesEngineInfraWithProvisioner(infra);
  }

  @Test
  @Owner(developers = VAIBHAV_SI)
  @Category(UnitTests.class)
  public void testValidateAwsLambdaInfraWithProvisioner() {
    //    InfrastructureDefinitionServiceImpl infrastructureDefinitionService =
    //        (InfrastructureDefinitionServiceImpl) this.infrastructureDefinitionService;
    String randomValue = "val";

    AwsLambdaInfrastructure infra = AwsLambdaInfrastructure.builder().build();
    Map<String, String> expressions = new HashMap<>();
    infra.setExpressions(expressions);
    assertThatThrownBy(() -> infrastructureDefinitionService.validateAwsLambdaInfraWithProvisioner(infra));

    expressions.put(AwsLambdaInfrastructureKeys.region, randomValue);
    expressions.put(AwsLambdaInfrastructureKeys.role, randomValue);
    infrastructureDefinitionService.validateAwsLambdaInfraWithProvisioner(infra);
  }

  @Test
  @Owner(developers = VAIBHAV_SI)
  @Category(UnitTests.class)
  public void testValidateAwsAmiInfraWithProvisioner() {
    //    InfrastructureDefinitionServiceImpl infrastructureDefinitionService =
    //        (InfrastructureDefinitionServiceImpl) this.infrastructureDefinitionService;
    String randomValue = "val";

    AwsAmiInfrastructure infra = AwsAmiInfrastructure.builder().build();
    Map<String, String> expressions = new HashMap<>();
    infra.setExpressions(expressions);
    assertThatThrownBy(() -> infrastructureDefinitionService.validateAwsAmiInfraWithProvisioner(infra));

    expressions.put(AwsAmiInfrastructureKeys.region, randomValue);
    expressions.put(AwsAmiInfrastructureKeys.autoScalingGroupName, randomValue);
    infrastructureDefinitionService.validateAwsAmiInfraWithProvisioner(infra);
  }

  @Test
  @Owner(developers = VAIBHAV_SI)
  @Category(UnitTests.class)
  public void testValidateAwsInstanceInfraWithProvisioner() {
    //    InfrastructureDefinitionServiceImpl infrastructureDefinitionService =
    //        (InfrastructureDefinitionServiceImpl) this.infrastructureDefinitionService;
    String randomValue = "val";

    AwsInstanceInfrastructure infra = AwsInstanceInfrastructure.builder().build();
    Map<String, String> expressions = new HashMap<>();
    infra.setExpressions(expressions);
    assertThatThrownBy(() -> infrastructureDefinitionService.validateAwsInstanceInfraWithProvisioner(infra));

    expressions.put(AwsInstanceInfrastructureKeys.region, randomValue);
    expressions.put(AwsInstanceFilterKeys.tags, randomValue);
    infrastructureDefinitionService.validateAwsInstanceInfraWithProvisioner(infra);

    infra.setProvisionInstances(true);
    infra.setDesiredCapacity(1);
    assertThatThrownBy(() -> infrastructureDefinitionService.validateAwsInstanceInfraWithProvisioner(infra));
    expressions.put(AwsInstanceInfrastructureKeys.autoScalingGroupName, randomValue);
    infrastructureDefinitionService.validateAwsInstanceInfraWithProvisioner(infra);
  }

  @Test
  @Owner(developers = VAIBHAV_SI)
  @Category(UnitTests.class)
  public void testRemoveUnsupportedExpressions() {
    //    InfrastructureDefinitionServiceImpl infrastructureDefinitionService =
    //        (InfrastructureDefinitionServiceImpl) this.infrastructureDefinitionService;
    AwsInstanceInfrastructure awsInstanceInfrastructure = AwsInstanceInfrastructure.builder().build();
    Map<String, String> expressions = new HashMap<>();
    expressions.put(AwsInstanceInfrastructureKeys.region, "randomValue");
    expressions.put("randomKey", "randomValue");
    awsInstanceInfrastructure.setExpressions(expressions);
    infrastructureDefinitionService.removeUnsupportedExpressions(awsInstanceInfrastructure);

    Map<String, String> expectedExpressions = new HashMap<>();
    expectedExpressions.put(AwsInstanceInfrastructureKeys.region, "randomValue");
    assertThat(awsInstanceInfrastructure.getExpressions()).isEqualTo(expectedExpressions);
  }

  @Test
  @Owner(developers = PRASHANT)
  @Category(UnitTests.class)
  public void testCloneAndSaveWith() {
    InfrastructureDefinitionService spyInfrastructureDefinitionService = spy(InfrastructureDefinitionServiceImpl.class);
    InfrastructureDefinition infraDef =
        InfrastructureDefinition.builder().uuid("infra-uuid").envId("envid").appId("appid").name("infra-name").build();

    doReturn(InfrastructureDefinition.builder().build())
        .when(spyInfrastructureDefinitionService)
        .save(any(InfrastructureDefinition.class), any(boolean.class), any(boolean.class));
    doReturn(aPageResponse().withResponse(Collections.singletonList(infraDef)).build())
        .when(spyInfrastructureDefinitionService)
        .list("appid", "envid", null);

    spyInfrastructureDefinitionService.cloneInfrastructureDefinitions("appid", "envid", "appid-1", "envid-1");

    // test
    ArgumentCaptor<InfrastructureDefinition> cloneInfraDef = ArgumentCaptor.forClass(InfrastructureDefinition.class);

    verify(spyInfrastructureDefinitionService, times(1))
        .save(cloneInfraDef.capture(), any(boolean.class), any(boolean.class));
    verify(spyInfrastructureDefinitionService, times(1)).list("appid", "envid", null);

    InfrastructureDefinition value = cloneInfraDef.getValue();
    assertThat(value.getUuid()).isNull();
    assertThat("envid-1").isEqualTo(value.getEnvId());
    assertThat("appid-1").isEqualTo(value.getAppId());
    assertThat("infra-name").isEqualTo(value.getName());
  }

  @Test
  @Owner(developers = PRASHANT)
  @Category(UnitTests.class)
  public void shouldThrowExceptionOnDeleteReferencedByWorkflow() {
    mockPhysicalInfra();
    when(workflowService.obtainWorkflowNamesReferencedByInfrastructureDefinition(APP_ID, INFRA_DEFINITION_ID))
        .thenReturn(asList("Referenced Workflow"));

    assertThatExceptionOfType(WingsException.class)
        .isThrownBy(() -> infrastructureDefinitionService.delete(APP_ID, INFRA_DEFINITION_ID));
  }

  @Test
  @Owner(developers = PRASHANT)
  @Category(UnitTests.class)
  public void shouldThrowExceptionOnDeleteReferencedByTrigger() {
    mockPhysicalInfra();
    when(workflowService.obtainWorkflowNamesReferencedByInfrastructureDefinition(APP_ID, INFRA_DEFINITION_ID))
        .thenReturn(asList());
    when(pipelineService.obtainPipelineNamesReferencedByTemplatedEntity(APP_ID, INFRA_DEFINITION_ID))
        .thenReturn(asList());

    when(triggerService.obtainTriggerNamesReferencedByTemplatedEntityId(APP_ID, INFRA_DEFINITION_ID))
        .thenReturn(asList("Referenced Trigger"));
    assertThatThrownBy(() -> infrastructureDefinitionService.delete(APP_ID, INFRA_DEFINITION_ID))
        .isInstanceOf(WingsException.class);
  }

  @Test
  @Owner(developers = PRASHANT)
  @Category(UnitTests.class)
  public void shouldThrowExceptionOnDeleteReferencedByPipeline() {
    mockPhysicalInfra();
    when(workflowService.obtainWorkflowNamesReferencedByServiceInfrastructure(APP_ID, INFRA_DEFINITION_ID))
        .thenReturn(asList());
    when(pipelineService.obtainPipelineNamesReferencedByTemplatedEntity(APP_ID, INFRA_DEFINITION_ID))
        .thenReturn(asList("Referenced Pipeline"));
    assertThatThrownBy(() -> infrastructureDefinitionService.delete(APP_ID, INFRA_DEFINITION_ID))
        .isInstanceOf(WingsException.class);
  }

  private void mockPhysicalInfra() {
    InfrastructureDefinition infrastructureDefinition = getValidInfra(PHYSICAL_INFRA, false);

    when(wingsPersistence.getWithAppId(InfrastructureDefinition.class, APP_ID, INFRA_DEFINITION_ID))
        .thenReturn(infrastructureDefinition);
  }

  @Test
  @Owner(developers = VAIBHAV_SI)
  @Category(UnitTests.class)
  public void shouldGetExpressionAnnotatedFields() {
    InfrastructureDefinitionServiceImpl infrastructureDefinitionService = this.infrastructureDefinitionService;
    GoogleKubernetesEngine googleKubernetesEngine = GoogleKubernetesEngine.builder().namespace("namespace").build();

    Map<String, Object> allFields =
        infrastructureDefinitionService.getExpressionAnnotatedFields(googleKubernetesEngine);

    assertThat(allFields).hasSize(1);
    assertThat(allFields.get(GoogleKubernetesEngineKeys.namespace).equals("namespace")).isTrue();
  }

  @Test
  @Owner(developers = VAIBHAV_SI)
  @Category(UnitTests.class)
  public void shouldReturnAwsInstanceFilterExpressionAnnotatedFields() {
    InfrastructureDefinitionServiceImpl infrastructureDefinitionService =
        (InfrastructureDefinitionServiceImpl) this.infrastructureDefinitionService;
    AwsInstanceInfrastructure awsInstanceInfra = AwsInstanceInfrastructure.builder().build();

    Map<String, Object> allFields = infrastructureDefinitionService.getExpressionAnnotatedFields(awsInstanceInfra);

    assertThat(allFields.size()).isEqualTo(1);
    assertThat(allFields).containsKey("awsInstanceFilter");
  }

  @Test
  @Owner(developers = VAIBHAV_SI)
  @Category(UnitTests.class)
  public void testApplySearchFilter() {
    UriInfo uriInfo = mock(UriInfo.class);
    Map<String, List<String>> queryParams = new HashMap<>();
    when(uriInfo.getQueryParameters()).thenReturn(new AbstractMultivaluedMap<String, String>(queryParams) {});
    PageRequest<InfrastructureDefinition> pageRequest = new PageRequest<>();
    pageRequest.setUriInfo(uriInfo);

    assertThatThrownBy(
        () -> infrastructureDefinitionService.applyServiceFilter(pageRequest, Collections.singletonList("s1")));

    queryParams.put("appId", Collections.singletonList("app1"));
    Service service = Service.builder().deploymentType(DeploymentType.SSH).build();
    when(serviceResourceService.get(any(), any())).thenReturn(service);
    infrastructureDefinitionService.applyServiceFilter(pageRequest, Collections.singletonList("s1"));
    assertThat(pageRequest.getFilters().size() == 2).isTrue();
  }

  @Test
  @Owner(developers = VAIBHAV_SI)
  @Category(UnitTests.class)
  public void testValidateImmutableFields() {
    InfrastructureDefinition oldInfraDefinition = InfrastructureDefinition.builder()
                                                      .deploymentType(DeploymentType.SSH)
                                                      .cloudProviderType(CloudProviderType.AWS)
                                                      .build();
    InfrastructureDefinition newInfraDefinition = InfrastructureDefinition.builder()
                                                      .deploymentType(DeploymentType.SSH)
                                                      .cloudProviderType(CloudProviderType.AWS)
                                                      .build();

    infrastructureDefinitionService.validateImmutableFields(newInfraDefinition, oldInfraDefinition);

    newInfraDefinition.setDeploymentType(DeploymentType.HELM);
    assertThatThrownBy(
        () -> infrastructureDefinitionService.validateImmutableFields(newInfraDefinition, oldInfraDefinition));
    newInfraDefinition.setDeploymentType(oldInfraDefinition.getDeploymentType());

    newInfraDefinition.setCloudProviderType(CloudProviderType.GCP);
    assertThatThrownBy(
        () -> infrastructureDefinitionService.validateImmutableFields(newInfraDefinition, oldInfraDefinition));
    newInfraDefinition.setCloudProviderType(oldInfraDefinition.getCloudProviderType());
  }

  @Test
  @Owner(developers = YOGESH)
  @Category(UnitTests.class)
  public void testValidateInfraDefinition() {
    InfrastructureDefinition valid = null;
    valid = getValidInfra(PHYSICAL_INFRA, false);
    infrastructureDefinitionService.validateAndPrepareInfraDefinition(valid);
    verify(infrastructureMappingService, times(1)).validateInfraMapping(valid.getInfraMapping(), false, null);
    InfrastructureDefinition inValid_phy = valid.cloneForUpdate();
    inValid_phy.setDeploymentType(DeploymentType.HELM);
    assertThatExceptionOfType(InvalidRequestException.class)
        .isThrownBy(() -> infrastructureDefinitionService.validateAndPrepareInfraDefinition(inValid_phy));

    valid = getValidInfra(PHYSICAL_INFRA, true);
    InfrastructureDefinition inValid_phy_prov = valid.cloneForUpdate();
    for (String key : ImmutableList.of(PhysicalInfra.hostname, PhysicalInfra.hostArrayPath)) {
      ((PhysicalInfra) inValid_phy_prov.getInfrastructure()).getExpressions().put(key, EMPTY);
      assertThatExceptionOfType(InvalidRequestException.class)
          .isThrownBy(() -> infrastructureDefinitionService.validateAndPrepareInfraDefinition(inValid_phy_prov));
      ((PhysicalInfra) inValid_phy_prov.getInfrastructure()).getExpressions().put(key, "default");
    }

    valid = getValidInfra(PHYSICAL_INFRA_WINRM, false);
    when(infrastructureProvisionerService.get(any(), any()))
        .thenReturn(ShellScriptInfrastructureProvisioner.builder().build());
    infrastructureDefinitionService.validateAndPrepareInfraDefinition(valid);
    verify(infrastructureMappingService, times(1)).validateInfraMapping(valid.getInfraMapping(), false, null);
    InfrastructureDefinition inValid_phy_winrm = valid.cloneForUpdate();
    inValid_phy_winrm.setDeploymentType(DeploymentType.HELM);
    assertThatExceptionOfType(InvalidRequestException.class)
        .isThrownBy(() -> infrastructureDefinitionService.validateAndPrepareInfraDefinition(inValid_phy_winrm));

    valid = getValidInfra(GCP_KUBERNETES_ENGINE, true);
    infrastructureDefinitionService.validateAndPrepareInfraDefinition(valid);
    verify(infrastructureMappingService, times(1)).validateInfraMapping(valid.getInfraMapping(), false, null);
    InfrastructureDefinition inValid_gcpK8s = valid.cloneForUpdate();
    InfrastructureDefinition invalid_gcp_k8s_prov = valid.cloneForUpdate();
    for (String key : ImmutableList.of(GoogleKubernetesEngineKeys.clusterName, GoogleKubernetesEngineKeys.namespace)) {
      ((GoogleKubernetesEngine) invalid_gcp_k8s_prov.getInfrastructure()).getExpressions().put(key, EMPTY);
      assertThatExceptionOfType(InvalidRequestException.class)
          .isThrownBy(() -> infrastructureDefinitionService.validateAndPrepareInfraDefinition(invalid_gcp_k8s_prov));
      ((GoogleKubernetesEngine) invalid_gcp_k8s_prov.getInfrastructure()).getExpressions().put(key, "default");
    }
    when(mockSettingsService.getByAccountAndId(any(), any()))
        .thenReturn(SettingAttribute.Builder.aSettingAttribute()
                        .withValue(GcpConfig.builder()
                                       .useDelegateSelectors(true)
                                       .delegateSelectors(Collections.singletonList("abc"))
                                       .build())
                        .build());
    InfrastructureDefinition invalid_gcp_k8s_delegate_selector = valid.cloneForUpdate();
    assertThatExceptionOfType(InvalidRequestException.class)
        .isThrownBy(
            () -> infrastructureDefinitionService.validateAndPrepareInfraDefinition(invalid_gcp_k8s_delegate_selector));

    valid = getValidInfra(AWS_ECS, true);
    infrastructureDefinitionService.validateAndPrepareInfraDefinition(valid);
    verify(infrastructureMappingService, times(1)).validateInfraMapping(valid.getInfraMapping(), false, null);
    InfrastructureDefinition invalid_awsEcs = valid.cloneForUpdate();
    for (String key : ImmutableList.<String>of(AwsEcsInfrastructureKeys.region, AwsEcsInfrastructureKeys.clusterName,
             AwsEcsInfrastructureKeys.executionRole, AwsEcsInfrastructureKeys.vpcId,
             AwsEcsInfrastructureKeys.securityGroupIds, AwsEcsInfrastructureKeys.subnetIds)) {
      ((AwsEcsInfrastructure) invalid_awsEcs.getInfrastructure()).getExpressions().put(key, EMPTY);
      assertThatExceptionOfType(InvalidRequestException.class)
          .isThrownBy(() -> infrastructureDefinitionService.validateAndPrepareInfraDefinition(invalid_awsEcs));
      ((AwsEcsInfrastructure) invalid_awsEcs.getInfrastructure()).getExpressions().put(key, "default");
    }
    ((AwsEcsInfrastructure) invalid_awsEcs.getInfrastructure()).setLaunchType(EMPTY);
    assertThatExceptionOfType(InvalidRequestException.class)
        .isThrownBy(() -> infrastructureDefinitionService.validateAndPrepareInfraDefinition(invalid_awsEcs));
  }

  @Test
  @Owner(developers = NAMAN_TALAYCHA)
  @Category(UnitTests.class)
  public void testValidateInfraDefinitionNegativeCase() {
    InfrastructureDefinition valid = null;
    when(infrastructureProvisionerService.get(any(), any())).thenReturn(null);

    valid = getValidInfra(PHYSICAL_INFRA, true);
    InfrastructureDefinition inValid_phy_prov = valid.cloneForUpdate();
    assertThatExceptionOfType(InvalidRequestException.class)
        .isThrownBy(() -> infrastructureDefinitionService.validateAndPrepareInfraDefinition(inValid_phy_prov));

    valid = getValidInfra(GCP_KUBERNETES_ENGINE, true);
    InfrastructureDefinition inValid_gcp_k8s_engine = valid.cloneForUpdate();
    assertThatExceptionOfType(InvalidRequestException.class)
        .isThrownBy(() -> infrastructureDefinitionService.validateAndPrepareInfraDefinition(inValid_gcp_k8s_engine));

    valid = getValidInfra(AWS_ECS, true);
    InfrastructureDefinition inValid_aws_ecs = valid.cloneForUpdate();
    assertThatExceptionOfType(InvalidRequestException.class)
        .isThrownBy(() -> infrastructureDefinitionService.validateAndPrepareInfraDefinition(inValid_aws_ecs));
  }

  private InfrastructureDefinition getValidInfra(@NotNull String type, boolean withProvisioner) {
    SettingAttribute settingAttribute = SettingAttribute.Builder.aSettingAttribute().withAccountId(ACCOUNT_ID).build();
    when(mockSettingsService.getByAccountAndId(any(), any())).thenReturn(settingAttribute);
    InfrastructureDefinitionBuilder builder = InfrastructureDefinition.builder()
                                                  .name(INFRA_DEFINITION_NAME)
                                                  .uuid(INFRA_DEFINITION_ID)
                                                  .appId(APP_ID)
                                                  .envId(ENV_ID);

    List<String> hosts = ImmutableList.of(HOST_NAME);
    HashMap<String, String> expMap = Maps.<String, String>newHashMap();
    switch (type) {
      case PHYSICAL_INFRA:
        settingAttribute.setValue(aPhysicalDataCenterConfig().build());
        PhysicalInfra infra = null;
        if (withProvisioner) {
          expMap.put(PhysicalInfra.hostArrayPath, "host");
          expMap.put(PhysicalInfra.hostname, "hostname");
          infra = PhysicalInfra.builder()
                      .hostConnectionAttrs("dev-key")
                      .cloudProviderId(SETTING_ID)
                      .expressions(expMap)
                      .build();
          builder.provisionerId(INFRA_PROVISIONER_ID);
        } else {
          infra = PhysicalInfra.builder()
                      .hostConnectionAttrs("dev-key")
                      .cloudProviderId(SETTING_ID)
                      .hostNames(hosts)
                      .build();
        }
        return builder.cloudProviderType(CloudProviderType.PHYSICAL_DATA_CENTER)
            .deploymentType(DeploymentType.SSH)
            .infrastructure(infra)
            .build();
      case PHYSICAL_INFRA_WINRM:
        settingAttribute.setValue(aPhysicalDataCenterConfig().build());
        if (withProvisioner) {
          return null;
        } else {
          return builder.cloudProviderType(CloudProviderType.PHYSICAL_DATA_CENTER)
              .deploymentType(DeploymentType.WINRM)
              .infrastructure(PhysicalInfraWinrm.builder()
                                  .winRmConnectionAttributes("test.key")
                                  .cloudProviderId(SETTING_ID)
                                  .hostNames(hosts)
                                  .build())
              .build();
        }
      case GCP_KUBERNETES_ENGINE:
        settingAttribute.setValue(GcpConfig.builder().build());
        if (withProvisioner) {
          expMap.put("clusterName", "${terraform.cluster}");
          expMap.put("namespace", "${terraform.namespace}");
          return builder.provisionerId(INFRA_PROVISIONER_ID)
              .deploymentType(DeploymentType.KUBERNETES)
              .cloudProviderType(CloudProviderType.GCP)
              .infrastructure(
                  GoogleKubernetesEngine.builder().expressions(expMap).cloudProviderId(COMPUTE_PROVIDER_ID).build())
              .build();
        } else {
          return null;
        }
      case AWS_ECS:
        settingAttribute.setValue(AwsConfig.builder().build());
        if (withProvisioner) {
          expMap.put(AwsEcsInfrastructureKeys.region, "region");
          expMap.put(AwsEcsInfrastructureKeys.clusterName, "cluster");
          expMap.put(AwsEcsInfrastructureKeys.vpcId, "vpc");
          expMap.put(AwsEcsInfrastructureKeys.securityGroupIds, "sgid");
          expMap.put(AwsEcsInfrastructureKeys.subnetIds, "sid");
          expMap.put(AwsEcsInfrastructureKeys.executionRole, "execRole");
          return builder.provisionerId(INFRA_PROVISIONER_ID)
              .deploymentType(DeploymentType.ECS)
              .cloudProviderType(CloudProviderType.AWS)
              .infrastructure(AwsEcsInfrastructure.builder()
                                  .launchType(LaunchType.FARGATE.name())
                                  .expressions(expMap)
                                  .cloudProviderId(COMPUTE_PROVIDER_ID)
                                  .build())
              .build();
        } else {
          return builder.deploymentType(DeploymentType.ECS)
              .cloudProviderType(CloudProviderType.AWS)
              .infrastructure(AwsEcsInfrastructure.builder()
                                  .launchType(LaunchType.FARGATE.name())
                                  .region("us-east-1")
                                  .clusterName("test")
                                  .cloudProviderId(COMPUTE_PROVIDER_ID)
                                  .build())
              .build();
        }
      default:
        throw new InvalidRequestException("Invalid type");
    }
  }

  @Test
  @Owner(developers = ADWAIT)
  @Category(UnitTests.class)
  public void testContainsExpression() {
    assertThat(infrastructureDefinitionService.containsExpression("org")).isFalse();
    assertThat(infrastructureDefinitionService.containsExpression("${serviceVariable.val}")).isTrue();
  }

  @Test
  @Owner(developers = ADWAIT)
  @Category(UnitTests.class)
  public void testListRoutesForPcf() throws Exception {
    InfrastructureDefinitionServiceImpl definitionService = spy(InfrastructureDefinitionServiceImpl.class);
    SettingsService settingsService = mock(SettingsService.class);
    FieldUtils.writeField(definitionService, "settingsService", settingsService, true);
    doReturn(null).when(definitionService).get(any(), any());

    assertThatThrownBy(() -> definitionService.listRoutesForPcf("app", "def"))
        .isNotInstanceOf(NullPointerException.class);

    doReturn(InfrastructureDefinition.builder().build()).when(definitionService).get(any(), any());
    assertThatThrownBy(() -> definitionService.listRoutesForPcf("app", "def"));

    doReturn(InfrastructureDefinition.builder().infrastructure(AwsAmiInfrastructure.builder().build()).build())
        .when(definitionService)
        .get(any(), any());
    assertThatThrownBy(() -> definitionService.listRoutesForPcf("app", "def"))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("Not PcfInfraStructure, invalid type");

    doReturn(InfrastructureDefinition.builder().infrastructure(PcfInfraStructure.builder().build()).build())
        .when(definitionService)
        .get(any(), any());
    doReturn(null).when(settingsService).get(any());
    assertThatThrownBy(() -> definitionService.listRoutesForPcf("app", "def"));

    doReturn(aSettingAttribute().withValue(AwsConfig.builder().build()).build()).when(settingsService).get(any());
    assertThatThrownBy(() -> definitionService.listRoutesForPcf("app", "def"))
        .isInstanceOf(InvalidRequestException.class);
  }

  @Test
  @Owner(developers = YOGESH)
  @Category(UnitTests.class)
  public void testCloudProviderNameForDefinition() {
    SettingAttribute cloudProvider =
        aSettingAttribute()
            .withName("aws-cp")
            .withUuid("aws-cp-id")
            .withAccountId(ACCOUNT_ID)
            .withCategory(SettingCategory.CLOUD_PROVIDER)
            .withValue(
                AwsConfig.builder().accessKey("access-key".toCharArray()).secretKey("secret-key".toCharArray()).build())
            .build();

    InfrastructureDefinition def = getValidInfra(PHYSICAL_INFRA, false);
    ((PhysicalInfra) def.getInfrastructure()).setCloudProviderId(cloudProvider.getUuid());

    when(wingsPersistence.getWithAppId(InfrastructureDefinition.class, def.getAppId(), def.getUuid())).thenReturn(def);
    when(mockSettingsService.get(def.getInfrastructure().getCloudProviderId())).thenReturn(cloudProvider);

    assertThat(infrastructureDefinitionService.cloudProviderNameForDefinition(def.getAppId(), def.getUuid()))
        .isEqualTo(cloudProvider.getName());

    when(mockSettingsService.get(def.getInfrastructure().getCloudProviderId())).thenReturn(null);
    assertThat(infrastructureDefinitionService.cloudProviderNameForDefinition(def.getAppId(), def.getUuid()))
        .isEqualTo(null);
  }

  @Test
  @Owner(developers = ACHYUTH)
  @Category(UnitTests.class)
  public void testInvalidCloudProviderId() {
    InfrastructureDefinition valid;

    valid = getValidInfra(PHYSICAL_INFRA, true);
    InfrastructureDefinition inValid_phy_prov = valid.cloneForUpdate();
    when(mockSettingsService.getByAccountAndId(any(), any())).thenReturn(null);
    assertThatExceptionOfType(InvalidRequestException.class)
        .isThrownBy(() -> infrastructureDefinitionService.validateAndPrepareInfraDefinition(inValid_phy_prov));

    valid = getValidInfra(GCP_KUBERNETES_ENGINE, true);
    InfrastructureDefinition inValid_gcp_k8s_engine = valid.cloneForUpdate();
    when(mockSettingsService.getByAccountAndId(any(), any())).thenReturn(null);
    assertThatExceptionOfType(InvalidRequestException.class)
        .isThrownBy(() -> infrastructureDefinitionService.validateAndPrepareInfraDefinition(inValid_gcp_k8s_engine));

    valid = getValidInfra(AWS_ECS, true);
    InfrastructureDefinition inValid_aws_ecs = valid.cloneForUpdate();
    when(mockSettingsService.getByAccountAndId(any(), any())).thenReturn(null);
    assertThatExceptionOfType(InvalidRequestException.class)
        .isThrownBy(() -> infrastructureDefinitionService.validateAndPrepareInfraDefinition(inValid_aws_ecs));
  }

  @Test
  @Owner(developers = ACHYUTH)
  @Category(UnitTests.class)
  public void testCloudProviderIdForCustomInfra() {
    CustomInfrastructure customInfrastructure =
        CustomInfrastructure.builder()
            .customDeploymentName("weblogic")
            .infraVariables(asList(NameValuePair.builder().name("key").value("foo").build()))
            .build();
    when(mockSettingsService.getByAccountAndId(any(), any())).thenReturn(null);
    InfrastructureDefinition customInfraDefn = InfrastructureDefinition.builder()
                                                   .infrastructure(customInfrastructure)
                                                   .name("infra")
                                                   .deploymentType(DeploymentType.CUSTOM)
                                                   .cloudProviderType(CloudProviderType.CUSTOM)
                                                   .build();
    infrastructureDefinitionService.save(customInfraDefn, true);
    verify(customDeploymentTypeService, times(1)).putCustomDeploymentTypeNameIfApplicable(customInfraDefn);
  }

  @Test
  @Owner(developers = DINESH)
  @Category(UnitTests.class)
  public void testSaveWithSkipValidation() {
    InfrastructureDefinition infraDef =
        InfrastructureDefinition.builder().uuid("infra-uuid").envId("envid").appId("appid").name("infra-name").build();

    when(wingsPersistence.save(any(InfrastructureDefinition.class))).thenReturn(infraDef.getUuid());
    when(appService.getAccountIdByAppId(any())).thenReturn(ACCOUNT_ID);

    infrastructureDefinitionService.save(infraDef, false, true);
    verify(infrastructureDefinitionService, times(0))
        .validateAndPrepareInfraDefinition(any(InfrastructureDefinition.class));
  }

  @Test
  @Owner(developers = SATYAM)
  @Category(UnitTests.class)
  public void asgFieldUpdateShouldThrowExceptionForTraffic() {
    InfrastructureDefinition oldInfraDef =
        InfrastructureDefinition.builder()
            .deploymentType(DeploymentType.AMI)
            .cloudProviderType(CloudProviderType.AWS)
            .infrastructure(AwsAmiInfrastructure.builder().useTrafficShift(true).build())
            .build();
    InfrastructureDefinition newInfraDef =
        InfrastructureDefinition.builder()
            .deploymentType(DeploymentType.AMI)
            .cloudProviderType(CloudProviderType.AWS)
            .infrastructure(AwsAmiInfrastructure.builder().useTrafficShift(false).build())
            .build();

    assertThatThrownBy(() -> infrastructureDefinitionService.validateImmutableFields(newInfraDef, oldInfraDef))
        .isInstanceOf(InvalidRequestException.class);
  }

  @Test
  @Owner(developers = VAIBHAV_SI)
  @Category(UnitTests.class)
  public void asgFieldUpdateShouldThrowException() {
    InfrastructureDefinition oldInfraDef =
        InfrastructureDefinition.builder()
            .deploymentType(DeploymentType.AMI)
            .cloudProviderType(CloudProviderType.AWS)
            .infrastructure(AwsAmiInfrastructure.builder().asgIdentifiesWorkload(true).build())
            .build();
    InfrastructureDefinition newInfraDef =
        InfrastructureDefinition.builder()
            .deploymentType(DeploymentType.AMI)
            .cloudProviderType(CloudProviderType.AWS)
            .infrastructure(AwsAmiInfrastructure.builder().asgIdentifiesWorkload(false).build())
            .build();

    assertThatThrownBy(() -> infrastructureDefinitionService.validateImmutableFields(newInfraDef, oldInfraDef))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("Asg Uniquely Identifies Workload");
  }

  //  @Test
  //  @Owner(emails = UNKNOWN)
  //  @Category(UnitTests.class)
  //  public void testListHostedZones() {
  //    InfrastructureDefinition awsEcsInfra = getValidInfra(AWS_ECS, true);
  //  }

  @Test
  @Owner(developers = RIHAZ)
  @Category(UnitTests.class)
  public void googleKubernetesEngineSetDefaultsTest() {
    InfrastructureDefinition googleKubernetesInfraDef =
        InfrastructureDefinition.builder().infrastructure(GoogleKubernetesEngine.builder().build()).build();

    infrastructureDefinitionService.setMissingValues(googleKubernetesInfraDef);

    assertThat(((GoogleKubernetesEngine) (googleKubernetesInfraDef.getInfrastructure())).getNamespace())
        .isEqualTo(DEFAULT);
  }

  @Test
  @Owner(developers = RIHAZ)
  @Category(UnitTests.class)
  public void googleKubernetesEngineSetDefaultsNoChangeTest() {
    InfrastructureDefinition googleKubernetesInfraDef =
        InfrastructureDefinition.builder()
            .infrastructure(GoogleKubernetesEngine.builder().namespace(USER_INPUT_NAMESPACE).build())
            .build();

    infrastructureDefinitionService.setMissingValues(googleKubernetesInfraDef);

    assertThat(((GoogleKubernetesEngine) (googleKubernetesInfraDef.getInfrastructure())).getNamespace())
        .isEqualTo(USER_INPUT_NAMESPACE);
  }

  @Test
  @Owner(developers = RIHAZ)
  @Category(UnitTests.class)
  public void directKubernetesSetDefaultsTest() {
    InfrastructureDefinition directKubernetesInfraDef =
        InfrastructureDefinition.builder().infrastructure(DirectKubernetesInfrastructure.builder().build()).build();

    infrastructureDefinitionService.setMissingValues(directKubernetesInfraDef);

    assertThat(((DirectKubernetesInfrastructure) (directKubernetesInfraDef.getInfrastructure())).getNamespace())
        .isEqualTo(DEFAULT);
  }

  @Test
  @Owner(developers = RIHAZ)
  @Category(UnitTests.class)
  public void directKubernetesSetDefaultsNoChangeTest() {
    InfrastructureDefinition directKubernetesInfraDef =
        InfrastructureDefinition.builder()
            .infrastructure(DirectKubernetesInfrastructure.builder().namespace(USER_INPUT_NAMESPACE).build())
            .build();

    infrastructureDefinitionService.setMissingValues(directKubernetesInfraDef);

    assertThat(((DirectKubernetesInfrastructure) (directKubernetesInfraDef.getInfrastructure())).getNamespace())
        .isEqualTo(USER_INPUT_NAMESPACE);
  }

  @Test
  @Owner(developers = RAUNAK)
  @Category(UnitTests.class)
  public void testAzureHostNames() {
    InfrastructureDefinitionServiceImpl spyInfrastructureDefinitionService =
        spy(InfrastructureDefinitionServiceImpl.class);

    final InfrastructureDefinition infrastructureDefinition =
        InfrastructureDefinition.builder()
            .uuid("infraid")
            .name("infra_name")
            .infrastructure(AzureInstanceInfrastructure.builder().build())
            .build();

    Host hostWithFQDN =
        Host.Builder.aHost().withHostName("winRM-1").withPublicDns("winRM-1.westus.cloudapp.azure.com").build();
    Host hostWithoutFQDN = Host.Builder.aHost().withHostName("winRM-2").build();

    doReturn(Arrays.asList(hostWithFQDN, hostWithoutFQDN))
        .when(spyInfrastructureDefinitionService)
        .listHosts(infrastructureDefinition);

    List<String> hostNames = spyInfrastructureDefinitionService.getInfrastructureDefinitionHostDisplayNames(
        infrastructureDefinition, "appId", "workflowId");

    assertThat(hostNames.size()).isEqualTo(2);
    assertThat(hostNames.get(0)).isEqualTo("winRM-1.westus.cloudapp.azure.com");
    assertThat(hostNames.get(1)).isEqualTo("winRM-2");
  }

  @Test
  @Owner(developers = RAUNAK)
  @Category(UnitTests.class)
  public void testAzureHostNamesWithNoNode() {
    InfrastructureDefinitionServiceImpl spyInfrastructureDefinitionService =
        spy(InfrastructureDefinitionServiceImpl.class);
    final InfrastructureDefinition infrastructureDefinition =
        InfrastructureDefinition.builder()
            .uuid("infraid")
            .name("infra_name")
            .infrastructure(AzureInstanceInfrastructure.builder().build())
            .build();
    doReturn(null).when(spyInfrastructureDefinitionService).listHosts(infrastructureDefinition);
    List<String> hostNames = spyInfrastructureDefinitionService.getInfrastructureDefinitionHostDisplayNames(
        infrastructureDefinition, "appId", "workflowId");

    assertThat(hostNames).isEmpty();
  }

  @Test
  @Owner(developers = VAIBHAV_SI)
  @Category(UnitTests.class)
  public void shouldReturnZeroContainerInstancesWhenDynamicInfra() {
    final InfrastructureDefinition infrastructureDefinition =
        InfrastructureDefinition.builder()
            .provisionerId(PROVISIONER_ID)
            .infrastructure(AwsInstanceInfrastructure.builder().build())
            .build();
    doReturn(infrastructureDefinition).when(infrastructureDefinitionService).get(APP_ID, INFRA_DEFINITION_ID);

    String count = infrastructureDefinitionService.getContainerRunningInstances(
        APP_ID, INFRA_DEFINITION_ID, SERVICE_ID, "EXPRESSION");

    assertThat(count).isEqualTo("0");
  }

  @Test
  @Owner(developers = POOJA)
  @Category(UnitTests.class)
  public void testListInfraDefinitionErros() {
    InfrastructureDefinition sshInfraDef = InfrastructureDefinition.builder()
                                               .uuid("ssh-id")
                                               .name("ssh")
                                               .deploymentType(DeploymentType.SSH)
                                               .appId(APP_ID)
                                               .build();
    InfrastructureDefinition k8sInfraDef = InfrastructureDefinition.builder()
                                               .uuid("k8s-id")
                                               .name("k8s")
                                               .deploymentType(DeploymentType.KUBERNETES)
                                               .appId(APP_ID)
                                               .build();
    InfrastructureDefinition nullInfraDef =
        InfrastructureDefinition.builder().uuid("null-id").name("null").deploymentType(null).appId(APP_ID).build();
    InfrastructureDefinition scopedSSHInfraDef = InfrastructureDefinition.builder()
                                                     .uuid("ssh-id-scoped")
                                                     .name("ssh-scoped")
                                                     .deploymentType(DeploymentType.SSH)
                                                     .appId(APP_ID)
                                                     .scopedToServices(asList("service1"))
                                                     .build();

    UriInfo uriInfo = mock(UriInfo.class);
    Map<String, List<String>> queryParams = new HashMap<>();
    when(uriInfo.getQueryParameters()).thenReturn(new AbstractMultivaluedMap<String, String>(queryParams) {});
    PageRequest<InfrastructureDefinition> pageRequest = new PageRequest<>();
    pageRequest.setUriInfo(uriInfo);

    PageResponse<InfrastructureDefinition> pageResponse = new PageResponse<>();
    pageResponse.setResponse(asList(sshInfraDef, k8sInfraDef, nullInfraDef, scopedSSHInfraDef));
    when(wingsPersistence.query(InfrastructureDefinition.class, pageRequest)).thenReturn(pageResponse);

    PageResponse<InfrastructureDefinition> response = infrastructureDefinitionService.list(pageRequest);
    assertThat(response).isNotNull();
    assertThat(response.getResponse().size()).isEqualTo(4);

    queryParams.put("serviceId", Collections.singletonList("s1"));
    Service service = Service.builder().deploymentType(DeploymentType.SSH).build();
    when(serviceResourceService.get(any(), any())).thenReturn(service);

    assertThatThrownBy(() -> infrastructureDefinitionService.list(pageRequest))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("AppId is mandatory for service-based filtering");

    queryParams.put("appId", asList("app1", "app2"));
    assertThatThrownBy(() -> infrastructureDefinitionService.list(pageRequest))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("More than 1 app not supported for listing infra definitions");
  }

  @Test
  @Owner(developers = POOJA)
  @Category(UnitTests.class)
  public void testListInfraDefinitionByService() {
    InfrastructureDefinition sshInfraDef = InfrastructureDefinition.builder()
                                               .uuid("ssh-id")
                                               .name("ssh")
                                               .deploymentType(DeploymentType.SSH)
                                               .appId(APP_ID)
                                               .build();
    InfrastructureDefinition k8sInfraDef = InfrastructureDefinition.builder()
                                               .uuid("k8s-id")
                                               .name("k8s")
                                               .deploymentType(DeploymentType.KUBERNETES)
                                               .appId(APP_ID)
                                               .build();
    InfrastructureDefinition nullInfraDef =
        InfrastructureDefinition.builder().uuid("null-id").name("null").deploymentType(null).appId(APP_ID).build();
    InfrastructureDefinition scopedSSHInfraDef = InfrastructureDefinition.builder()
                                                     .uuid("ssh-id-scoped")
                                                     .name("ssh-scoped")
                                                     .deploymentType(DeploymentType.SSH)
                                                     .appId(APP_ID)
                                                     .scopedToServices(asList("service1"))
                                                     .build();

    UriInfo uriInfo = mock(UriInfo.class);
    Map<String, List<String>> queryParams = new HashMap<>();
    when(uriInfo.getQueryParameters()).thenReturn(new AbstractMultivaluedMap<String, String>(queryParams) {});
    PageRequest<InfrastructureDefinition> pageRequest = new PageRequest<>();
    pageRequest.setUriInfo(uriInfo);

    queryParams.put("serviceId", Collections.singletonList("s1"));
    Service service = Service.builder().deploymentType(DeploymentType.SSH).build();
    when(serviceResourceService.get(any(), any())).thenReturn(service);

    queryParams.put("appId", Collections.singletonList(APP_ID));
    PageResponse<InfrastructureDefinition> pageResponse = new PageResponse<>();
    pageResponse.setResponse(asList(sshInfraDef, nullInfraDef, scopedSSHInfraDef));
    when(wingsPersistence.query(InfrastructureDefinition.class, pageRequest)).thenReturn(pageResponse);

    PageResponse<InfrastructureDefinition> response = infrastructureDefinitionService.list(pageRequest);
    assertThat(response).isNotNull();
    assertThat(response.getResponse().size()).isEqualTo(3);

    assertThat(pageRequest.getFilters().size()).isEqualTo(2);
    assertThat(pageRequest.getFilters().get(0).getFieldName()).isEqualTo("deploymentType");
    assertThat(pageRequest.getFilters().get(0).getOp()).isEqualTo(EQ);
    assertThat(pageRequest.getFilters().get(0).getFieldValues().length).isEqualTo(1);
    assertThat(pageRequest.getFilters().get(0).getFieldValues()[0]).isEqualTo("SSH");

    assertThat(pageRequest.getFilters().get(1).getFieldName()).isEqualTo("scopedToServices");
    assertThat(pageRequest.getFilters().get(1).getOp()).isEqualTo(OR);
    assertThat(pageRequest.getFilters().get(1).getFieldValues().length).isEqualTo(2);
    SearchFilter searchFilter1 = (SearchFilter) pageRequest.getFilters().get(1).getFieldValues()[0];
    assertThat(searchFilter1.getOp()).isEqualTo(NOT_EXISTS);
  }

  @Test
  @Owner(developers = POOJA)
  @Category(UnitTests.class)
  public void testListInfraDefinitionTwoServiceDifferentDeploymentType() {
    UriInfo uriInfo = mock(UriInfo.class);
    Map<String, List<String>> queryParams = new HashMap<>();
    when(uriInfo.getQueryParameters()).thenReturn(new AbstractMultivaluedMap<String, String>(queryParams) {});
    PageRequest<InfrastructureDefinition> pageRequest = new PageRequest<>();
    pageRequest.setUriInfo(uriInfo);

    queryParams.put("serviceId", asList("ssh", "k8s"));
    Service sshService = Service.builder().deploymentType(DeploymentType.SSH).name("ssh").build();
    when(serviceResourceService.get(APP_ID, "ssh")).thenReturn(sshService);
    Service k8sService = Service.builder().deploymentType(DeploymentType.KUBERNETES).name("k8s").build();
    when(serviceResourceService.get(APP_ID, "k8s")).thenReturn(k8sService);

    queryParams.put("appId", Collections.singletonList(APP_ID));

    assertThatThrownBy(() -> infrastructureDefinitionService.list(pageRequest))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("Cannot load infra for different deployment type services [ssh, k8s]");
  }

  @Test
  @Owner(developers = ADWAIT)
  @Category(UnitTests.class)
  public void testListElasticLoadBalancers() {
    doReturn(InfrastructureDefinition.builder()
                 .infrastructure(AwsEcsInfrastructure.builder().region(Regions.US_EAST_1.name()).build())
                 .build())
        .when(wingsPersistence)
        .getWithAppId(any(), any(), any());

    doReturn(SettingAttribute.Builder.aSettingAttribute().withCategory(SettingCategory.SETTING).build())
        .when(mockSettingsService)
        .get(any());

    AwsInfrastructureProvider awsInfrastructureProvider = mock(AwsInfrastructureProvider.class);
    doReturn(Arrays.asList("a", "b", "c", "a"))
        .when(awsInfrastructureProvider)
        .listElasticBalancers(any(), any(), any());
    doReturn(awsInfrastructureProvider).when(infrastructureProviderMap).get(any());

    Map<String, String> loadBalancers =
        infrastructureDefinitionService.listElasticLoadBalancers(APP_ID, INFRA_MAPPING_ID);
    assertThat(loadBalancers.keySet()).containsOnly("a", "b", "c");
  }

  @Test
  @Owner(developers = RAGHVENDRA)
  @Category(UnitTests.class)
  public void testListElasticLoadBalancersWithProvisioner() {
    doReturn(InfrastructureDefinition.builder()
                 .infrastructure(AwsEcsInfrastructure.builder().region(Regions.US_EAST_1.name()).build())
                 .provisionerId("provisioner1")
                 .build())
        .when(wingsPersistence)
        .getWithAppId(any(), any(), any());

    doReturn(SettingAttribute.Builder.aSettingAttribute().withCategory(SettingCategory.SETTING).build())
        .when(mockSettingsService)
        .get(any());

    AwsInfrastructureProvider awsInfrastructureProvider = mock(AwsInfrastructureProvider.class);
    doThrow(new RuntimeException("Failed to fetch ELB from AWS"))
        .when(awsInfrastructureProvider)
        .listElasticBalancers(any(), any(), any());
    doReturn(awsInfrastructureProvider).when(infrastructureProviderMap).get(any());

    Map<String, String> loadBalancers =
        infrastructureDefinitionService.listElasticLoadBalancers(APP_ID, INFRA_MAPPING_ID);
    assertThat(loadBalancers).isEmpty();
  }

  @Test
  @Owner(developers = RAGHVENDRA)
  @Category(UnitTests.class)
  public void testListElasticLoadBalancersExceptionCase() {
    doReturn(InfrastructureDefinition.builder()
                 .infrastructure(AwsEcsInfrastructure.builder().region(Regions.US_EAST_1.name()).build())
                 .build())
        .when(wingsPersistence)
        .getWithAppId(any(), any(), any());

    doReturn(SettingAttribute.Builder.aSettingAttribute().withCategory(SettingCategory.SETTING).build())
        .when(mockSettingsService)
        .get(any());

    AwsInfrastructureProvider awsInfrastructureProvider = mock(AwsInfrastructureProvider.class);
    doThrow(new RuntimeException("Failed to fetch ELB from AWS"))
        .when(awsInfrastructureProvider)
        .listElasticBalancers(any(), any(), any());
    doReturn(awsInfrastructureProvider).when(infrastructureProviderMap).get(any());

    try {
      infrastructureDefinitionService.listElasticLoadBalancers(APP_ID, INFRA_MAPPING_ID);
    } catch (Exception ex) {
      assertThat(ex.getMessage()).isEqualTo("Failed to fetch ELB from AWS");
      assertThat(ex).isInstanceOf(RuntimeException.class);
    }
  }

  @Test
  @Owner(developers = RAGHVENDRA)
  @Category(UnitTests.class)
  public void testListTargetGroups() {
    doReturn(InfrastructureDefinition.builder()
                 .infrastructure(AwsEcsInfrastructure.builder().region(Regions.US_EAST_1.name()).build())
                 .build())
        .when(wingsPersistence)
        .getWithAppId(any(), any(), any());

    doReturn(SettingAttribute.Builder.aSettingAttribute().withCategory(SettingCategory.SETTING).build())
        .when(mockSettingsService)
        .get(any());

    AwsInfrastructureProvider awsInfrastructureProvider = mock(AwsInfrastructureProvider.class);
    Map<String, String> targetGroups = new HashMap<>();
    targetGroups.put("arn1", "tg1");
    targetGroups.put("arn2", "tg2");
    doReturn(targetGroups).when(awsInfrastructureProvider).listTargetGroups(any(), any(), eq("lb1"), any());
    doReturn(awsInfrastructureProvider).when(infrastructureProviderMap).get(any());

    Map<String, String> loadBalancers =
        infrastructureDefinitionService.listTargetGroups(APP_ID, INFRA_DEFINITION_ID, "lb1");
    assertThat(loadBalancers.keySet()).containsOnly("arn1", "arn2");
  }

  @Test
  @Owner(developers = RAGHVENDRA)
  @Category(UnitTests.class)
  public void testListTargetGroupsWithProvisioner() {
    doReturn(InfrastructureDefinition.builder()
                 .infrastructure(AwsEcsInfrastructure.builder().region(Regions.US_EAST_1.name()).build())
                 .provisionerId("provisioner1")
                 .build())
        .when(wingsPersistence)
        .getWithAppId(any(), any(), any());

    doReturn(SettingAttribute.Builder.aSettingAttribute().withCategory(SettingCategory.SETTING).build())
        .when(mockSettingsService)
        .get(any());

    AwsInfrastructureProvider awsInfrastructureProvider = mock(AwsInfrastructureProvider.class);
    Map<String, String> targetGroups = new HashMap<>();
    targetGroups.put("arn1", "tg1");
    targetGroups.put("arn2", "tg2");
    doThrow(new RuntimeException("Failed to fetch Target Group from AWS"))
        .when(awsInfrastructureProvider)
        .listTargetGroups(any(), any(), eq("lb1"), any());
    doReturn(awsInfrastructureProvider).when(infrastructureProviderMap).get(any());

    Map<String, String> loadBalancers =
        infrastructureDefinitionService.listTargetGroups(APP_ID, INFRA_DEFINITION_ID, "lb1");
    assertThat(loadBalancers).isEmpty();
  }

  @Test
  @Owner(developers = RAGHVENDRA)
  @Category(UnitTests.class)
  public void testListTargetGroupsExceptionCase() {
    doReturn(InfrastructureDefinition.builder()
                 .infrastructure(AwsEcsInfrastructure.builder().region(Regions.US_EAST_1.name()).build())
                 .build())
        .when(wingsPersistence)
        .getWithAppId(any(), any(), any());

    doReturn(SettingAttribute.Builder.aSettingAttribute().withCategory(SettingCategory.SETTING).build())
        .when(mockSettingsService)
        .get(any());

    AwsInfrastructureProvider awsInfrastructureProvider = mock(AwsInfrastructureProvider.class);
    Map<String, String> targetGroups = new HashMap<>();
    targetGroups.put("arn1", "tg1");
    targetGroups.put("arn2", "tg2");
    doThrow(new RuntimeException("Failed to fetch Target Group from AWS"))
        .when(awsInfrastructureProvider)
        .listTargetGroups(any(), any(), eq("lb1"), any());
    doReturn(awsInfrastructureProvider).when(infrastructureProviderMap).get(any());

    try {
      infrastructureDefinitionService.listTargetGroups(APP_ID, INFRA_DEFINITION_ID, "lb1");
    } catch (Exception ex) {
      assertThat(ex.getMessage()).isEqualTo("Failed to fetch Target Group from AWS");
      assertThat(ex).isInstanceOf(RuntimeException.class);
    }
  }

  @Test
  @Owner(developers = ADWAIT)
  @Category(UnitTests.class)
  public void testListLoadBalancers() {
    doReturn(InfrastructureDefinition.builder()
                 .infrastructure(AwsEcsInfrastructure.builder().region(Regions.US_EAST_1.name()).build())
                 .build())
        .when(wingsPersistence)
        .getWithAppId(any(), any(), any());

    doReturn(SettingAttribute.Builder.aSettingAttribute().withCategory(SettingCategory.SETTING).build())
        .when(mockSettingsService)
        .get(any());

    AwsInfrastructureProvider awsInfrastructureProvider = mock(AwsInfrastructureProvider.class);
    doReturn(Arrays.asList("a", "b", "c", "a")).when(awsInfrastructureProvider).listLoadBalancers(any(), any(), any());
    doReturn(awsInfrastructureProvider).when(infrastructureProviderMap).get(any());

    Map<String, String> loadBalancers = infrastructureDefinitionService.listLoadBalancers(APP_ID, INFRA_MAPPING_ID);
    assertThat(loadBalancers.keySet()).containsOnly("a", "b", "c");
  }

  @Test
  @Owner(developers = POOJA)
  @Category(UnitTests.class)
  public void testListInfraDefinitionByDeploymentType() {
    InfrastructureDefinition sshInfraDef = InfrastructureDefinition.builder()
                                               .uuid("ssh-id")
                                               .name("ssh")
                                               .deploymentType(DeploymentType.SSH)
                                               .appId(APP_ID)
                                               .build();
    InfrastructureDefinition k8sInfraDef = InfrastructureDefinition.builder()
                                               .uuid("k8s-id")
                                               .name("k8s")
                                               .deploymentType(DeploymentType.KUBERNETES)
                                               .appId(APP_ID)
                                               .build();
    InfrastructureDefinition nullInfraDef =
        InfrastructureDefinition.builder().uuid("null-id").name("null").deploymentType(null).appId(APP_ID).build();
    InfrastructureDefinition scopedSSHInfraDef = InfrastructureDefinition.builder()
                                                     .uuid("ssh-id-scoped")
                                                     .name("ssh-scoped")
                                                     .deploymentType(DeploymentType.SSH)
                                                     .appId(APP_ID)
                                                     .scopedToServices(asList("service1"))
                                                     .build();

    UriInfo uriInfo = mock(UriInfo.class);
    Map<String, List<String>> queryParams = new HashMap<>();
    when(uriInfo.getQueryParameters()).thenReturn(new AbstractMultivaluedMap<String, String>(queryParams) {});
    PageRequest<InfrastructureDefinition> pageRequest = new PageRequest<>();
    pageRequest.setUriInfo(uriInfo);

    queryParams.put("deploymentTypeFromMetadata", asList("SSH", "KUBERNETES"));
    PageResponse<InfrastructureDefinition> pageResponse = new PageResponse<>();
    pageResponse.setResponse(asList(sshInfraDef, nullInfraDef, scopedSSHInfraDef, k8sInfraDef));
    when(wingsPersistence.query(InfrastructureDefinition.class, pageRequest)).thenReturn(pageResponse);

    PageResponse<InfrastructureDefinition> response = infrastructureDefinitionService.list(pageRequest);
    assertThat(response).isNotNull();
    assertThat(response.getResponse().size()).isEqualTo(4);

    assertThat(pageRequest.getFilters().size()).isEqualTo(1);
    assertThat(pageRequest.getFilters().get(0).getFieldName()).isEqualTo("deploymentType");
    assertThat(pageRequest.getFilters().get(0).getOp()).isEqualTo(IN);
    assertThat(pageRequest.getFilters().get(0).getFieldValues().length).isEqualTo(2);
    assertThat(pageRequest.getFilters().get(0).getFieldValues()[0]).isEqualTo("SSH");
    assertThat(pageRequest.getFilters().get(0).getFieldValues()[1]).isEqualTo("KUBERNETES");
  }

  @Test
  @Owner(developers = YOGESH)
  @Category(UnitTests.class)
  public void saveCustomInfrastructure() {
    CustomInfrastructure customInfrastructure =
        CustomInfrastructure.builder()
            .customDeploymentName("weblogic")
            .infraVariables(asList(NameValuePair.builder().name("key").value("foo").build()))
            .build();

    InfrastructureDefinition infraDefinition = InfrastructureDefinition.builder()
                                                   .infrastructure(customInfrastructure)
                                                   .name("infra")
                                                   .deploymentType(DeploymentType.CUSTOM)
                                                   .cloudProviderType(CloudProviderType.CUSTOM)
                                                   .build();
    infrastructureDefinitionService.save(infraDefinition, true);

    verify(customDeploymentTypeService, times(1)).putCustomDeploymentTypeNameIfApplicable(infraDefinition);
  }

  @Test
  @Owner(developers = YOGESH)
  @Category(UnitTests.class)
  public void updateCustomInfrastructure() {
    CustomInfrastructure customInfrastructure =
        CustomInfrastructure.builder()
            .customDeploymentName("weblogic")
            .infraVariables(asList(NameValuePair.builder().name("key").value("foo").build()))
            .build();

    InfrastructureDefinition infraDefinition = InfrastructureDefinition.builder()
                                                   .infrastructure(customInfrastructure)
                                                   .name("infra")
                                                   .deploymentType(DeploymentType.CUSTOM)
                                                   .deploymentTypeTemplateId("id")
                                                   .cloudProviderType(CloudProviderType.CUSTOM)
                                                   .build();
    when(mockSettingsService.getByAccountAndId(any(), any())).thenReturn(new SettingAttribute());
    doReturn(infraDefinition).when(wingsPersistence).getWithAppId(eq(InfrastructureDefinition.class), any(), any());
    doReturn(CustomDeploymentTypeDTO.builder()
                 .name("weblogic")
                 .infraVariables(asList(aVariable().name("key").build()))
                 .build())
        .when(customDeploymentTypeService)
        .get(any(), any(), any());
    infrastructureDefinitionService.update(infraDefinition);

    verify(customDeploymentTypeService, atLeastOnce()).putCustomDeploymentTypeNameIfApplicable(infraDefinition);
  }

  @Test
  @Owner(developers = YOGESH)
  @Category(UnitTests.class)
  public void testValidateInfraVariables() {
    final List<Variable> templateVariables =
        Arrays.asList(aVariable().name("var1").build(), aVariable().name("var2").value("val2").build(),
            aVariable().name("val3").value("val3").description("desc3").build());

    // overriding var1
    infrastructureDefinitionService.validateInfraVariables(templateVariables,
        asList(NameValuePair.builder().name("var1").value("val1").build(),
            NameValuePair.builder().name("var2").value("val2").build()));

    // overriding var1,var2 and var3
    infrastructureDefinitionService.validateInfraVariables(templateVariables,
        asList(NameValuePair.builder().name("var1").value("val1").build(),
            NameValuePair.builder().name("var2").value(null).build(),
            NameValuePair.builder().name("val3").value("newVal3").build()));

    infrastructureDefinitionService.validateInfraVariables(templateVariables, null);
    infrastructureDefinitionService.validateInfraVariables(templateVariables, emptyList());
    infrastructureDefinitionService.validateInfraVariables(null, null);
    infrastructureDefinitionService.validateInfraVariables(emptyList(), null);

    //    Variable abc not present in the template
    assertThatExceptionOfType(InvalidRequestException.class)
        .isThrownBy(()
                        -> infrastructureDefinitionService.validateInfraVariables(templateVariables,
                            asList(NameValuePair.builder().name("var1").value("val1").build(),
                                NameValuePair.builder().name("abc").build())));

    assertThatExceptionOfType(InvalidRequestException.class)
        .isThrownBy(()
                        -> infrastructureDefinitionService.validateInfraVariables(
                            templateVariables, asList(NameValuePair.builder().name("abc").build())));
  }

  @Test
  @Owner(developers = YOGESH)
  @Category(UnitTests.class)
  public void testFilterNonOverridenVariables() {
    final List<Variable> templateVariables =
        Arrays.asList(aVariable().name("var1").build(), aVariable().name("var2").value("val2").build(),
            aVariable().name("var3").value("val3").description("desc3").build());

    List<NameValuePair> infraVariables;
    infraVariables = infrastructureDefinitionService.filterNonOverridenVariables(templateVariables,
        asList(NameValuePair.builder().name("var1").value("val1").build(),
            NameValuePair.builder().name("var2").value("val2").build()));
    assertThat(infraVariables).containsExactly(NameValuePair.builder().name("var1").value("val1").build());

    infraVariables = infrastructureDefinitionService.filterNonOverridenVariables(templateVariables,
        asList(NameValuePair.builder().name("var1").value("val1").build(),
            NameValuePair.builder().name("var3").value("val3").build()));
    assertThat(infraVariables).containsExactly(NameValuePair.builder().name("var1").value("val1").build());

    infraVariables = infrastructureDefinitionService.filterNonOverridenVariables(templateVariables,
        asList(NameValuePair.builder().name("var1").value("val1").build(),
            NameValuePair.builder().name("var2").value("val2_").build()));
    assertThat(infraVariables)
        .containsExactlyInAnyOrder(NameValuePair.builder().name("var1").value("val1").build(),
            NameValuePair.builder().name("var2").value("val2_").build());

    infraVariables = infrastructureDefinitionService.filterNonOverridenVariables(templateVariables,
        asList(NameValuePair.builder().name("var1").build(), NameValuePair.builder().name("var2").value("val2").build(),
            NameValuePair.builder().name("var3").value("val3").build()));
    assertThat(infraVariables).isEmpty();

    infraVariables = infrastructureDefinitionService.filterNonOverridenVariables(templateVariables, emptyList());
    assertThat(infraVariables).isEmpty();

    infraVariables = infrastructureDefinitionService.filterNonOverridenVariables(templateVariables, null);
    assertThat(infraVariables).isEmpty();
  }

  @Test
  @Owner(developers = BOJANA)
  @Category(UnitTests.class)
  public void testListedHostedZones() {
    // provisionerId is  empty
    setupHostedZones("us-east-1", null);
    List<AwsRoute53HostedZoneData> hostedZones =
        infrastructureDefinitionService.listHostedZones(APP_ID, INFRA_DEFINITION_ID);
    verify(awsRoute53HelperServiceManager, times(1))
        .listHostedZones(any(AwsConfig.class), anyList(), eq(REGION), eq(APP_ID));
    assertThat(hostedZones).containsSequence(hostedZones1);

    // provisionerId isn't empty
    setupHostedZones("us-east-1", PROVISIONER_ID);
    hostedZones = infrastructureDefinitionService.listHostedZones(APP_ID, INFRA_DEFINITION_ID);
    verify(awsRoute53HelperServiceManager, times(1))
        .listHostedZones(any(AwsConfig.class), anyList(), eq("us-east-1"), eq(APP_ID));
    assertThat(hostedZones).containsSequence(hostedZones2);
  }

  @Test
  @Owner(developers = BOJANA)
  @Category(UnitTests.class)
  public void testListedHostedZonesRegionIsAnExpression() {
    // provisioner is present and region is an expression
    setupHostedZones("${region}", PROVISIONER_ID);
    List<AwsRoute53HostedZoneData> hostedZones =
        infrastructureDefinitionService.listHostedZones(APP_ID, INFRA_DEFINITION_ID);
    verify(awsRoute53HelperServiceManager, never()).listHostedZones(any(), any(), any(), any());
    assertThat(hostedZones).isEqualTo(emptyList());
  }

  private void setupHostedZones(String region, String provisionerId) {
    Map<String, String> expressions = new HashMap<>();
    expressions.put("region", region);
    AwsEcsInfrastructure awsEcsInfrastructure = AwsEcsInfrastructure.builder()
                                                    .expressions(expressions)
                                                    .region(REGION)
                                                    .cloudProviderId("cloudProviderId")
                                                    .build();
    InfrastructureDefinition infrastructureDefinition =
        InfrastructureDefinition.builder().infrastructure(awsEcsInfrastructure).provisionerId(provisionerId).build();
    when(wingsPersistence.getWithAppId(eq(InfrastructureDefinition.class), eq(APP_ID), eq(INFRA_DEFINITION_ID)))
        .thenReturn(infrastructureDefinition);
    when(awsRoute53HelperServiceManager.listHostedZones(any(AwsConfig.class), anyList(), eq(REGION), eq(APP_ID)))
        .thenReturn(hostedZones1);
    when(awsRoute53HelperServiceManager.listHostedZones(any(AwsConfig.class), anyList(), eq("us-east-1"), eq(APP_ID)))
        .thenReturn(hostedZones2);
    SettingAttribute settingAttribute = new SettingAttribute();
    settingAttribute.setValue(AwsConfig.builder().build());
    when(mockSettingsService.get(any())).thenReturn(settingAttribute);
  }
}
