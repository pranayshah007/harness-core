/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.sm.states;

import static io.harness.beans.FeatureName.DEPLOY_TO_INLINE_HOSTS;
import static io.harness.beans.FeatureName.DEPLOY_TO_SPECIFIC_HOSTS;
import static io.harness.beans.PageResponse.PageResponseBuilder.aPageResponse;
import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.rule.OwnerRule.ANIL;
import static io.harness.rule.OwnerRule.ANSHUL;
import static io.harness.rule.OwnerRule.SRINIVAS;
import static io.harness.rule.OwnerRule.TMACARI;
import static io.harness.rule.OwnerRule.VAIBHAV_SI;
import static io.harness.rule.OwnerRule.YOGESH;

import static software.wings.api.InstanceElement.Builder.anInstanceElement;
import static software.wings.beans.AccountType.COMMUNITY;
import static software.wings.beans.Application.Builder.anApplication;
import static software.wings.beans.AwsInfrastructureMapping.Builder.anAwsInfrastructureMapping;
import static software.wings.beans.AzureInfrastructureMapping.Builder.anAzureInfrastructureMapping;
import static software.wings.beans.Environment.Builder.anEnvironment;
import static software.wings.beans.PhysicalInfrastructureMapping.Builder.aPhysicalInfrastructureMapping;
import static software.wings.beans.ServiceInstance.Builder.aServiceInstance;
import static software.wings.beans.ServiceInstanceSelectionParams.Builder.aServiceInstanceSelectionParams;
import static software.wings.beans.ServiceTemplate.Builder.aServiceTemplate;
import static software.wings.beans.infrastructure.Host.Builder.aHost;
import static software.wings.service.impl.workflow.WorkflowServiceHelper.SELECT_NODE_NAME;
import static software.wings.sm.WorkflowStandardParams.Builder.aWorkflowStandardParams;
import static software.wings.utils.WingsTestConstants.ACCOUNT_ID;
import static software.wings.utils.WingsTestConstants.APP_ID;
import static software.wings.utils.WingsTestConstants.ARTIFACT_ID;
import static software.wings.utils.WingsTestConstants.ENV_ID;
import static software.wings.utils.WingsTestConstants.INFRA_DEFINITION_ID;
import static software.wings.utils.WingsTestConstants.INFRA_MAPPING_ID;
import static software.wings.utils.WingsTestConstants.SERVICE_ID;
import static software.wings.utils.WingsTestConstants.TEMPLATE_ID;
import static software.wings.utils.WingsTestConstants.WORKFLOW_EXECUTION_ID;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.beans.ExecutionStatus;
import io.harness.beans.OrchestrationWorkflowType;
import io.harness.beans.PageRequest;
import io.harness.beans.PageResponse;
import io.harness.beans.SweepingOutputInstance;
import io.harness.beans.SweepingOutputInstance.Scope;
import io.harness.category.element.UnitTests;
import io.harness.context.ContextElementType;
import io.harness.deployment.InstanceDetails;
import io.harness.exception.InvalidRequestException;
import io.harness.ff.FeatureFlagService;
import io.harness.rule.Owner;

import software.wings.WingsBaseTest;
import software.wings.api.InstanceElement;
import software.wings.api.PhaseElement;
import software.wings.api.SelectedNodeExecutionData;
import software.wings.api.ServiceElement;
import software.wings.api.ServiceInstanceArtifactParam;
import software.wings.beans.Account;
import software.wings.beans.AwsInfrastructureMapping;
import software.wings.beans.AzureInfrastructureMapping;
import software.wings.beans.HostConnectionType;
import software.wings.beans.InfrastructureMappingType;
import software.wings.beans.InstanceUnitType;
import software.wings.beans.LicenseInfo;
import software.wings.beans.PhysicalInfrastructureMapping;
import software.wings.beans.ServiceInstance;
import software.wings.beans.ServiceInstanceSelectionParams;
import software.wings.beans.ServiceInstanceSelectionParams.Builder;
import software.wings.beans.ServiceTemplate;
import software.wings.beans.infrastructure.Host;
import software.wings.beans.infrastructure.instance.Instance;
import software.wings.beans.infrastructure.instance.key.HostInstanceKey;
import software.wings.common.InstanceExpressionProcessor;
import software.wings.persistence.artifact.Artifact;
import software.wings.service.intfc.AccountService;
import software.wings.service.intfc.AppService;
import software.wings.service.intfc.ArtifactService;
import software.wings.service.intfc.HostService;
import software.wings.service.intfc.InfrastructureMappingService;
import software.wings.service.intfc.StateExecutionService;
import software.wings.service.intfc.WorkflowExecutionService;
import software.wings.service.intfc.instance.InstanceService;
import software.wings.service.intfc.sweepingoutput.SweepingOutputService;
import software.wings.sm.ContextElement;
import software.wings.sm.ExecutionContextImpl;
import software.wings.sm.ExecutionResponse;
import software.wings.sm.StateExecutionInstance;
import software.wings.sm.WorkflowStandardParams;
import software.wings.utils.WingsTestConstants;

import com.amazonaws.regions.Regions;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;

public class NodeSelectStateTest extends WingsBaseTest {
  @Mock private ExecutionContextImpl context;
  @Mock private InfrastructureMappingService infrastructureMappingService;
  @Mock private ContextElement contextElement;
  @Mock private ServiceInstanceArtifactParam serviceInstanceArtifactParam;
  @Mock private Artifact artifact;
  @Mock private ArtifactService artifactService;
  @Mock private InstanceService instanceService;
  @Mock private WorkflowStandardParams workflowStandardParams;
  @Mock private AppService appService;
  @Mock private WorkflowExecutionService workflowExecutionService;
  @Mock private FeatureFlagService featureFlagService;
  @Mock private StateExecutionService stateExecutionService;
  @Mock private SweepingOutputService sweepingOutputService;
  @Mock private InstanceExpressionProcessor instanceExpressionProcessor;
  @Mock private HostService hostService;
  @Mock private AccountService accountService;

  @InjectMocks private NodeSelectState nodeSelectState = new DcNodeSelectState("DC_NODE_SELECT");
  private static final String INSTANCE_COUNT_EXPRESSION = "${workflow.variables.count}";

  StateExecutionInstance stateExecutionInstance = new StateExecutionInstance();
  AwsInfrastructureMapping awsInfrastructureMapping =
      anAwsInfrastructureMapping()
          .withInfraMappingType(InfrastructureMappingType.AWS_SSH.name())
          .withRegion(Regions.US_EAST_1.getName())
          .withUsePublicDns(true)
          .withHostConnectionType(HostConnectionType.PUBLIC_DNS.name())
          .build();

  PhysicalInfrastructureMapping physicalSSHInfrastructureMapping =
      aPhysicalInfrastructureMapping()
          .withInfraMappingType(InfrastructureMappingType.PHYSICAL_DATA_CENTER_SSH.name())
          .build();

  PhysicalInfrastructureMapping physicalWinRmInfrastructureMapping =
      aPhysicalInfrastructureMapping()
          .withInfraMappingType(InfrastructureMappingType.PHYSICAL_DATA_CENTER_WINRM.name())
          .build();

  AzureInfrastructureMapping azureInfrastructureMapping =
      anAzureInfrastructureMapping().withInfraMappingType(InfrastructureMappingType.AZURE_INFRA.name()).build();

  private ServiceTemplate SERVICE_TEMPLATE =
      aServiceTemplate().withUuid(TEMPLATE_ID).withName("template").withServiceId(SERVICE_ID).build();

  HostInstanceKey hostInstanceKey =
      HostInstanceKey.builder().hostName("host1").infraMappingId(INFRA_MAPPING_ID).build();
  Instance instance = Instance.builder().hostInstanceKey(hostInstanceKey).build();

  ServiceInstance instance1 = aServiceInstance()
                                  .withUuid(generateUuid())
                                  .withHost(aHost().withHostName("host1").build())
                                  .withServiceTemplate(SERVICE_TEMPLATE)
                                  .build();
  ServiceInstance instance2 = aServiceInstance()
                                  .withUuid(generateUuid())
                                  .withHost(aHost().withHostName("host2").build())
                                  .withServiceTemplate(SERVICE_TEMPLATE)
                                  .build();
  ServiceInstance instance3 = aServiceInstance()
                                  .withUuid(generateUuid())
                                  .withHost(aHost().withHostName("host3").build())
                                  .withServiceTemplate(SERVICE_TEMPLATE)
                                  .build();

  List<ServiceInstance> instances = Lists.newArrayList(instance1, instance2, instance3);

  @Before
  public void setUp() throws Exception {
    stateExecutionInstance.setUuid(generateUuid());
    stateExecutionInstance.setDisplayName("DC_NODE_SELECT");
    when(context.getApp()).thenReturn(anApplication().uuid(APP_ID).build());
    when(context.getContextElement(ContextElementType.PARAM, PhaseElement.PHASE_PARAM))
        .thenReturn(PhaseElement.builder()
                        .serviceElement(ServiceElement.builder().uuid(SERVICE_ID).build())
                        .infraMappingId(INFRA_MAPPING_ID)
                        .build());
    when(context.getStateExecutionInstance()).thenReturn(stateExecutionInstance);
    when(infrastructureMappingService.selectServiceInstances(
             nullable(String.class), nullable(String.class), nullable(String.class), any()))
        .thenReturn(instances);
    when(infrastructureMappingService.listHostDisplayNames(
             nullable(String.class), nullable(String.class), nullable(String.class)))
        .thenReturn(asList("Host1", "Host2", "Host3"));

    when(context.getContextElement(ContextElementType.INSTANCE)).thenReturn(contextElement);
    when(context.getContextElement(
             ContextElementType.PARAM, ServiceInstanceArtifactParam.SERVICE_INSTANCE_ARTIFACT_PARAMS))
        .thenReturn(serviceInstanceArtifactParam);
    when(context.getContextElement(ContextElementType.STANDARD)).thenReturn(workflowStandardParams);
    when(hostService.getHostsByHostIds(nullable(String.class), nullable(String.class), anyList()))
        .thenAnswer(invocationOnMock -> {
          return invocationOnMock.getArgument(2, List.class)
              .stream()
              .map(item -> Host.Builder.aHost().withUuid((String) item).build())
              .collect(Collectors.toList());
        });
    when(context.prepareSweepingOutputBuilder(any(SweepingOutputInstance.Scope.class)))
        .thenReturn(SweepingOutputInstance.builder());
  }

  @Test
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  public void shouldTestDoNotExcludeHostsWithSameArtifact() {
    nodeSelectState.setInstanceCount("3");
    PhaseElement phaseElement = PhaseElement.builder()
                                    .infraDefinitionId(INFRA_DEFINITION_ID)
                                    .rollback(false)
                                    .phaseName("Phase 1")
                                    .phaseNameForRollback("Rollback Phase 1")
                                    .serviceElement(ServiceElement.builder().uuid(generateUuid()).build())
                                    .build();
    when(context.getContextElement(ContextElementType.PARAM, PhaseElement.PHASE_PARAM)).thenReturn(phaseElement);
    when(infrastructureMappingService.get(APP_ID, INFRA_MAPPING_ID)).thenReturn(awsInfrastructureMapping);
    when(context.getAppId()).thenReturn(APP_ID);
    when(context.renderExpression("3")).thenReturn("3");
    when(context.fetchRequiredEnvironment()).thenReturn(anEnvironment().uuid(ENV_ID).build());
    when(context.fetchInfraMappingId()).thenReturn(INFRA_MAPPING_ID);
    when(context.prepareSweepingOutputBuilder(Scope.WORKFLOW)).thenReturn(SweepingOutputInstance.builder());
    when(contextElement.getUuid()).thenReturn(instance1.getUuid());
    when(serviceInstanceArtifactParam.getInstanceArtifactMap())
        .thenReturn(ImmutableMap.of(instance1.getUuid(), ARTIFACT_ID));
    when(artifactService.get(ARTIFACT_ID)).thenReturn(artifact);
    when(workflowStandardParams.isExcludeHostsWithSameArtifact()).thenReturn(false);

    PageResponse<Instance> pageResponse = aPageResponse().withResponse(asList(instance)).build();

    when(instanceService.list(any(PageRequest.class))).thenReturn(pageResponse);

    ExecutionResponse executionResponse = nodeSelectState.execute(context);
    assertThat(executionResponse.getExecutionStatus()).isEqualTo(ExecutionStatus.SUCCESS);
    assertThat(executionResponse.getStateExecutionData()).isNotNull();

    SelectedNodeExecutionData selectedNodeExecutionData =
        (SelectedNodeExecutionData) executionResponse.getStateExecutionData();
    assertThat(selectedNodeExecutionData).isNotNull();
    assertThat(selectedNodeExecutionData.getServiceInstanceList()).size().isEqualTo(3);
  }

  @Test
  @Owner(developers = TMACARI)
  @Category(UnitTests.class)
  public void shouldTestExecuteWithInlineHosts() {
    nodeSelectState.setSpecificHosts(true);
    nodeSelectState.setHostNames(Arrays.asList("test-host1", "test-host2", "${two-more-hosts}"));
    ServiceInstance testInstance1 = aServiceInstance()
                                        .withUuid(generateUuid())
                                        .withHost(aHost().withHostName("test-host1").build())
                                        .withServiceTemplate(SERVICE_TEMPLATE)
                                        .build();
    ServiceInstance testInstance2 = aServiceInstance()
                                        .withUuid(generateUuid())
                                        .withHost(aHost().withHostName("test-host2").build())
                                        .withServiceTemplate(SERVICE_TEMPLATE)
                                        .build();
    ServiceInstance testInstance3 = aServiceInstance()
                                        .withUuid(generateUuid())
                                        .withHost(aHost().withHostName("test-host3").build())
                                        .withServiceTemplate(SERVICE_TEMPLATE)
                                        .build();
    ServiceInstance testInstance4 = aServiceInstance()
                                        .withUuid(generateUuid())
                                        .withHost(aHost().withHostName("test-host4").build())
                                        .withServiceTemplate(SERVICE_TEMPLATE)
                                        .build();
    PhaseElement phaseElement = PhaseElement.builder()
                                    .infraDefinitionId(INFRA_DEFINITION_ID)
                                    .rollback(false)
                                    .phaseName("Phase 1")
                                    .phaseNameForRollback("Rollback Phase 1")
                                    .serviceElement(ServiceElement.builder().uuid(generateUuid()).build())
                                    .build();
    when(context.getContextElement(ContextElementType.PARAM, PhaseElement.PHASE_PARAM)).thenReturn(phaseElement);
    when(infrastructureMappingService.get(APP_ID, INFRA_MAPPING_ID)).thenReturn(physicalSSHInfrastructureMapping);
    when(context.getAppId()).thenReturn(APP_ID);
    when(infrastructureMappingService.selectServiceInstances(
             nullable(String.class), nullable(String.class), nullable(String.class), any()))
        .thenReturn(Arrays.asList(testInstance1, testInstance2, testInstance3, testInstance4))
        .thenReturn(instances);
    when(context.renderExpression("3")).thenReturn("3");
    when(context.renderExpression("test-host1")).thenReturn("test-host1");
    when(context.renderExpression("test-host2")).thenReturn("test-host2");
    when(context.renderExpression("${two-more-hosts}")).thenReturn("test-host3 ,test-host4");
    when(context.fetchRequiredEnvironment()).thenReturn(anEnvironment().uuid(ENV_ID).build());
    when(context.fetchInfraMappingId()).thenReturn(INFRA_MAPPING_ID);
    when(context.prepareSweepingOutputBuilder(Scope.WORKFLOW)).thenReturn(SweepingOutputInstance.builder());
    when(contextElement.getUuid()).thenReturn(instance1.getUuid());
    when(serviceInstanceArtifactParam.getInstanceArtifactMap())
        .thenReturn(ImmutableMap.of(instance1.getUuid(), ARTIFACT_ID));
    when(artifactService.get(ARTIFACT_ID)).thenReturn(artifact);
    when(workflowStandardParams.isExcludeHostsWithSameArtifact()).thenReturn(true);
    doReturn(true).when(featureFlagService).isEnabled(eq(DEPLOY_TO_INLINE_HOSTS), any());

    PageResponse<Instance> pageResponse = aPageResponse().withResponse(asList(instance)).build();

    when(instanceService.list(any(PageRequest.class))).thenReturn(pageResponse);

    ExecutionResponse executionResponse = nodeSelectState.execute(context);
    ArgumentCaptor<ServiceInstanceSelectionParams> argumentCaptor =
        ArgumentCaptor.forClass(ServiceInstanceSelectionParams.class);
    verify(infrastructureMappingService, times(2))
        .selectServiceInstances(any(), any(), any(), argumentCaptor.capture());
    assertThat(argumentCaptor.getAllValues().get(0).getHostNames().size()).isEqualTo(4);
    assertThat(executionResponse.getExecutionStatus()).isEqualTo(ExecutionStatus.SUCCESS);
    assertThat(executionResponse.getStateExecutionData()).isNotNull();
    SelectedNodeExecutionData selectedNodeExecutionData =
        (SelectedNodeExecutionData) executionResponse.getStateExecutionData();
    assertThat(selectedNodeExecutionData).isNotNull();
    assertThat(selectedNodeExecutionData.getServiceInstanceList()).size().isEqualTo(4);
  }

  @Test
  @Owner(developers = TMACARI)
  @Category(UnitTests.class)
  public void shouldTestExecuteWithInlineExecutionHosts() {
    nodeSelectState.setSpecificHosts(true);
    nodeSelectState.setHostNames(Arrays.asList("test-host1", "test-host2"));
    ServiceInstance testInstance1 = aServiceInstance()
                                        .withUuid(generateUuid())
                                        .withHost(aHost().withHostName("test-host1").build())
                                        .withServiceTemplate(SERVICE_TEMPLATE)
                                        .build();
    ServiceInstance testInstance2 = aServiceInstance()
                                        .withUuid(generateUuid())
                                        .withHost(aHost().withHostName("test-host2").build())
                                        .withServiceTemplate(SERVICE_TEMPLATE)
                                        .build();
    ServiceInstance testInstance3 = aServiceInstance()
                                        .withUuid(generateUuid())
                                        .withHost(aHost().withHostName("test-host3").build())
                                        .withServiceTemplate(SERVICE_TEMPLATE)
                                        .build();
    ServiceInstance testInstance4 = aServiceInstance()
                                        .withUuid(generateUuid())
                                        .withHost(aHost().withHostName("test-host4").build())
                                        .withServiceTemplate(SERVICE_TEMPLATE)
                                        .build();
    PhaseElement phaseElement = PhaseElement.builder()
                                    .infraDefinitionId(INFRA_DEFINITION_ID)
                                    .rollback(false)
                                    .phaseName("Phase 1")
                                    .phaseNameForRollback("Rollback Phase 1")
                                    .serviceElement(ServiceElement.builder().uuid(generateUuid()).build())
                                    .build();
    WorkflowStandardParams workflowStandardParams =
        aWorkflowStandardParams()
            .withExecutionHosts(Arrays.asList("test-host1", "test-host2", "${two-more-hosts}"))
            .build();
    workflowStandardParams.setExcludeHostsWithSameArtifact(true);
    when(context.getContextElement(ContextElementType.PARAM, PhaseElement.PHASE_PARAM)).thenReturn(phaseElement);
    when(infrastructureMappingService.get(APP_ID, INFRA_MAPPING_ID)).thenReturn(physicalSSHInfrastructureMapping);
    when(context.getAppId()).thenReturn(APP_ID);
    when(infrastructureMappingService.selectServiceInstances(
             nullable(String.class), nullable(String.class), nullable(String.class), any()))
        .thenReturn(Arrays.asList(testInstance1, testInstance2, testInstance3, testInstance4))
        .thenReturn(instances);
    when(context.renderExpression("3")).thenReturn("3");
    when(context.renderExpression("test-host1")).thenReturn("test-host1");
    when(context.renderExpression("test-host2")).thenReturn("test-host2");
    when(context.renderExpression("${two-more-hosts}")).thenReturn("test-host3 ,test-host4");
    when(context.fetchRequiredEnvironment()).thenReturn(anEnvironment().uuid(ENV_ID).build());
    when(context.fetchInfraMappingId()).thenReturn(INFRA_MAPPING_ID);
    when(context.prepareSweepingOutputBuilder(Scope.WORKFLOW)).thenReturn(SweepingOutputInstance.builder());
    when(contextElement.getUuid()).thenReturn(instance1.getUuid());
    when(serviceInstanceArtifactParam.getInstanceArtifactMap())
        .thenReturn(ImmutableMap.of(instance1.getUuid(), ARTIFACT_ID));
    when(artifactService.get(ARTIFACT_ID)).thenReturn(artifact);
    doReturn(true).when(featureFlagService).isEnabled(eq(DEPLOY_TO_INLINE_HOSTS), any());
    doReturn(true).when(featureFlagService).isEnabled(eq(DEPLOY_TO_SPECIFIC_HOSTS), any());

    PageResponse<Instance> pageResponse = aPageResponse().withResponse(asList(instance)).build();

    when(instanceService.list(any(PageRequest.class))).thenReturn(pageResponse);
    when(context.getContextElement(ContextElementType.STANDARD)).thenReturn(workflowStandardParams);
    doReturn(Collections.singletonList(stateExecutionInstance))
        .when(workflowExecutionService)
        .getStateExecutionInstancesForPhases(any());

    ExecutionResponse executionResponse = nodeSelectState.execute(context);
    ArgumentCaptor<ServiceInstanceSelectionParams> argumentCaptor =
        ArgumentCaptor.forClass(ServiceInstanceSelectionParams.class);
    verify(infrastructureMappingService, times(2))
        .selectServiceInstances(any(), any(), any(), argumentCaptor.capture());
    assertThat(argumentCaptor.getAllValues().get(0).getHostNames().size()).isEqualTo(4);
    assertThat(executionResponse.getExecutionStatus()).isEqualTo(ExecutionStatus.SUCCESS);
    assertThat(executionResponse.getStateExecutionData()).isNotNull();
    SelectedNodeExecutionData selectedNodeExecutionData =
        (SelectedNodeExecutionData) executionResponse.getStateExecutionData();
    assertThat(selectedNodeExecutionData).isNotNull();
    assertThat(selectedNodeExecutionData.getServiceInstanceList()).size().isEqualTo(4);
  }

  @Test
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  public void shouldTestDonotExcludeHostsWithSameArtifactForRolling() {
    nodeSelectState.setInstanceCount("3");
    PhaseElement phaseElement = PhaseElement.builder()
                                    .infraDefinitionId(INFRA_DEFINITION_ID)
                                    .rollback(false)
                                    .phaseName("Phase 1")
                                    .phaseNameForRollback("Rollback Phase 1")
                                    .serviceElement(ServiceElement.builder().uuid(generateUuid()).build())
                                    .build();
    when(context.getContextElement(ContextElementType.PARAM, PhaseElement.PHASE_PARAM)).thenReturn(phaseElement);
    when(infrastructureMappingService.get(APP_ID, INFRA_MAPPING_ID)).thenReturn(awsInfrastructureMapping);
    when(context.getAppId()).thenReturn(APP_ID);
    when(context.renderExpression("3")).thenReturn("3");
    when(context.fetchRequiredEnvironment()).thenReturn(anEnvironment().uuid(ENV_ID).build());
    when(context.fetchInfraMappingId()).thenReturn(INFRA_MAPPING_ID);
    when(context.prepareSweepingOutputBuilder(Scope.WORKFLOW)).thenReturn(SweepingOutputInstance.builder());
    when(context.getOrchestrationWorkflowType()).thenReturn(OrchestrationWorkflowType.ROLLING);
    when(contextElement.getUuid()).thenReturn(instance1.getUuid());
    when(serviceInstanceArtifactParam.getInstanceArtifactMap())
        .thenReturn(ImmutableMap.of(instance1.getUuid(), ARTIFACT_ID));
    when(artifactService.get(ARTIFACT_ID)).thenReturn(artifact);
    when(workflowStandardParams.isExcludeHostsWithSameArtifact()).thenReturn(true);

    PageResponse<Instance> pageResponse = aPageResponse().withResponse(asList(instance)).build();

    when(instanceService.list(any(PageRequest.class))).thenReturn(pageResponse);

    ExecutionResponse executionResponse = nodeSelectState.execute(context);
    assertThat(executionResponse.getExecutionStatus()).isEqualTo(ExecutionStatus.SUCCESS);
    assertThat(executionResponse.getStateExecutionData()).isNotNull();

    SelectedNodeExecutionData selectedNodeExecutionData =
        (SelectedNodeExecutionData) executionResponse.getStateExecutionData();
    assertThat(selectedNodeExecutionData).isNotNull();
    assertThat(selectedNodeExecutionData.getServiceInstanceList()).size().isEqualTo(3);
  }

  @Test
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  public void shouldTestExcludeHostsForPhysicalSshInfra() {
    nodeSelectState.setInstanceCount("3");
    PhaseElement phaseElement = PhaseElement.builder()
                                    .infraDefinitionId(INFRA_DEFINITION_ID)
                                    .rollback(false)
                                    .phaseName("Phase 1")
                                    .phaseNameForRollback("Rollback Phase 1")
                                    .serviceElement(ServiceElement.builder().uuid(generateUuid()).build())
                                    .build();
    when(context.getContextElement(ContextElementType.PARAM, PhaseElement.PHASE_PARAM)).thenReturn(phaseElement);
    when(infrastructureMappingService.get(APP_ID, INFRA_MAPPING_ID)).thenReturn(physicalSSHInfrastructureMapping);
    when(context.getAppId()).thenReturn(APP_ID);
    when(context.renderExpression("3")).thenReturn("3");
    when(context.fetchRequiredEnvironment()).thenReturn(anEnvironment().uuid(ENV_ID).build());
    when(context.fetchInfraMappingId()).thenReturn(INFRA_MAPPING_ID);
    when(context.prepareSweepingOutputBuilder(Scope.WORKFLOW)).thenReturn(SweepingOutputInstance.builder());
    when(contextElement.getUuid()).thenReturn(instance1.getUuid());
    when(serviceInstanceArtifactParam.getInstanceArtifactMap())
        .thenReturn(ImmutableMap.of(instance1.getUuid(), ARTIFACT_ID));
    when(artifactService.get(ARTIFACT_ID)).thenReturn(artifact);
    when(workflowStandardParams.isExcludeHostsWithSameArtifact()).thenReturn(true);

    PageResponse<Instance> pageResponse = aPageResponse().withResponse(asList(instance)).build();

    when(instanceService.list(any(PageRequest.class))).thenReturn(pageResponse);

    ExecutionResponse executionResponse = nodeSelectState.execute(context);

    assertResponse(executionResponse);
  }

  @Test
  @Owner(developers = TMACARI)
  @Category(UnitTests.class)
  public void shouldTestExcludeHostsForAzureInfra() {
    nodeSelectState.setInstanceCount("3");
    PhaseElement phaseElement = PhaseElement.builder()
                                    .infraDefinitionId(INFRA_DEFINITION_ID)
                                    .rollback(false)
                                    .phaseName("Phase 1")
                                    .phaseNameForRollback("Rollback Phase 1")
                                    .serviceElement(ServiceElement.builder().uuid(generateUuid()).build())
                                    .build();
    when(context.getContextElement(ContextElementType.PARAM, PhaseElement.PHASE_PARAM)).thenReturn(phaseElement);
    when(infrastructureMappingService.get(APP_ID, INFRA_MAPPING_ID)).thenReturn(azureInfrastructureMapping);
    when(context.getAppId()).thenReturn(APP_ID);
    when(context.renderExpression("3")).thenReturn("3");
    when(context.fetchRequiredEnvironment()).thenReturn(anEnvironment().uuid(ENV_ID).build());
    when(context.fetchInfraMappingId()).thenReturn(INFRA_MAPPING_ID);
    when(context.prepareSweepingOutputBuilder(Scope.WORKFLOW)).thenReturn(SweepingOutputInstance.builder());
    when(contextElement.getUuid()).thenReturn(instance1.getUuid());
    when(serviceInstanceArtifactParam.getInstanceArtifactMap())
        .thenReturn(ImmutableMap.of(instance1.getUuid(), ARTIFACT_ID));
    when(artifactService.get(ARTIFACT_ID)).thenReturn(artifact);
    when(workflowStandardParams.isExcludeHostsWithSameArtifact()).thenReturn(true);

    PageResponse<Instance> pageResponse = aPageResponse().withResponse(asList(instance)).build();

    when(instanceService.list(any(PageRequest.class))).thenReturn(pageResponse);

    ExecutionResponse executionResponse = nodeSelectState.execute(context);

    assertResponse(executionResponse);
  }

  @Test
  @Owner(developers = TMACARI)
  @Category(UnitTests.class)
  public void shouldTestExcludeHostsForPhysicalWinrmInfra() {
    nodeSelectState.setInstanceCount("3");
    PhaseElement phaseElement = PhaseElement.builder()
                                    .infraDefinitionId(INFRA_DEFINITION_ID)
                                    .rollback(false)
                                    .phaseName("Phase 1")
                                    .phaseNameForRollback("Rollback Phase 1")
                                    .serviceElement(ServiceElement.builder().uuid(generateUuid()).build())
                                    .build();
    when(context.getContextElement(ContextElementType.PARAM, PhaseElement.PHASE_PARAM)).thenReturn(phaseElement);
    when(infrastructureMappingService.get(APP_ID, INFRA_MAPPING_ID)).thenReturn(physicalWinRmInfrastructureMapping);
    when(context.getAppId()).thenReturn(APP_ID);
    when(context.renderExpression("3")).thenReturn("3");
    when(context.fetchRequiredEnvironment()).thenReturn(anEnvironment().uuid(ENV_ID).build());
    when(context.fetchInfraMappingId()).thenReturn(INFRA_MAPPING_ID);
    when(context.prepareSweepingOutputBuilder(Scope.WORKFLOW)).thenReturn(SweepingOutputInstance.builder());
    when(contextElement.getUuid()).thenReturn(instance1.getUuid());
    when(serviceInstanceArtifactParam.getInstanceArtifactMap())
        .thenReturn(ImmutableMap.of(instance1.getUuid(), ARTIFACT_ID));
    when(artifactService.get(ARTIFACT_ID)).thenReturn(artifact);
    when(workflowStandardParams.isExcludeHostsWithSameArtifact()).thenReturn(true);

    PageResponse<Instance> pageResponse = aPageResponse().withResponse(asList(instance)).build();

    when(instanceService.list(any(PageRequest.class))).thenReturn(pageResponse);

    ExecutionResponse executionResponse = nodeSelectState.execute(context);

    assertResponse(executionResponse);
  }

  @Test
  @Owner(developers = TMACARI)
  @Category(UnitTests.class)
  public void should() {
    nodeSelectState.setInstanceCount("3");
    PhaseElement phaseElement = PhaseElement.builder()
                                    .infraDefinitionId(INFRA_DEFINITION_ID)
                                    .rollback(false)
                                    .phaseName("Phase 1")
                                    .phaseNameForRollback("Rollback Phase 1")
                                    .serviceElement(ServiceElement.builder().uuid(generateUuid()).build())
                                    .build();
    when(context.getContextElement(ContextElementType.INSTANCE)).thenReturn(null);
    when(context.getContextElement(ContextElementType.PARAM, PhaseElement.PHASE_PARAM)).thenReturn(phaseElement);
    when(infrastructureMappingService.get(APP_ID, INFRA_MAPPING_ID)).thenReturn(physicalWinRmInfrastructureMapping);
    when(context.getAppId()).thenReturn(APP_ID);
    when(context.renderExpression("3")).thenReturn("3");
    when(context.fetchRequiredEnvironment()).thenReturn(anEnvironment().uuid(ENV_ID).build());
    when(context.fetchInfraMappingId()).thenReturn(INFRA_MAPPING_ID);
    when(context.prepareSweepingOutputBuilder(Scope.WORKFLOW)).thenReturn(SweepingOutputInstance.builder());
    when(contextElement.getUuid()).thenReturn(instance1.getUuid());
    when(serviceInstanceArtifactParam.getInstanceArtifactMap())
        .thenReturn(ImmutableMap.of(instance1.getUuid(), ARTIFACT_ID));
    when(artifactService.get(ARTIFACT_ID)).thenReturn(artifact);
    when(workflowStandardParams.isExcludeHostsWithSameArtifact()).thenReturn(true);

    PageResponse<Instance> pageResponse = aPageResponse().withResponse(asList(instance)).build();

    when(instanceService.list(any(PageRequest.class))).thenReturn(pageResponse);
    nodeSelectState.execute(context);
    verify(context, times(1)).getArtifactForService(any());
  }

  private void assertResponse(ExecutionResponse executionResponse) {
    assertThat(executionResponse.getExecutionStatus()).isEqualTo(ExecutionStatus.SUCCESS);
    assertThat(executionResponse.getStateExecutionData()).isNotNull();
    SelectedNodeExecutionData selectedNodeExecutionData =
        (SelectedNodeExecutionData) executionResponse.getStateExecutionData();
    assertThat(selectedNodeExecutionData).isNotNull();
    assertThat(selectedNodeExecutionData.getServiceInstanceList()).size().isEqualTo(2);
  }

  @Test
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  public void shouldTestExcludeHostsWithSameArtifact() {
    nodeSelectState.setInstanceCount("3");
    PhaseElement phaseElement = PhaseElement.builder()
                                    .infraDefinitionId(INFRA_DEFINITION_ID)
                                    .rollback(false)
                                    .phaseName("Phase 1")
                                    .phaseNameForRollback("Rollback Phase 1")
                                    .serviceElement(ServiceElement.builder().uuid(generateUuid()).build())
                                    .build();
    when(context.getContextElement(ContextElementType.PARAM, PhaseElement.PHASE_PARAM)).thenReturn(phaseElement);
    when(infrastructureMappingService.get(APP_ID, INFRA_MAPPING_ID)).thenReturn(awsInfrastructureMapping);
    when(context.getAppId()).thenReturn(APP_ID);
    when(context.renderExpression("3")).thenReturn("3");
    when(context.fetchRequiredEnvironment()).thenReturn(anEnvironment().uuid(ENV_ID).build());
    when(context.fetchInfraMappingId()).thenReturn(INFRA_MAPPING_ID);
    when(context.prepareSweepingOutputBuilder(Scope.WORKFLOW)).thenReturn(SweepingOutputInstance.builder());
    when(contextElement.getUuid()).thenReturn(instance1.getUuid());
    when(serviceInstanceArtifactParam.getInstanceArtifactMap())
        .thenReturn(ImmutableMap.of(instance1.getUuid(), ARTIFACT_ID));
    when(artifactService.get(ARTIFACT_ID)).thenReturn(artifact);
    when(workflowStandardParams.isExcludeHostsWithSameArtifact()).thenReturn(true);

    PageResponse<Instance> pageResponse = aPageResponse().withResponse(asList(instance)).build();

    when(instanceService.list(any(PageRequest.class))).thenReturn(pageResponse);

    ExecutionResponse executionResponse = nodeSelectState.execute(context);

    assertResponse(executionResponse);
  }

  @Test
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  public void shouldSucceedForPartialPercentageInstances() {
    nodeSelectState.setInstanceCount("1");
    nodeSelectState.setInstanceUnitType(InstanceUnitType.PERCENTAGE);
    PhaseElement phaseElement = PhaseElement.builder()
                                    .infraDefinitionId(INFRA_DEFINITION_ID)
                                    .rollback(false)
                                    .phaseName("Phase 1")
                                    .phaseNameForRollback("Rollback Phase 1")
                                    .serviceElement(ServiceElement.builder().uuid(generateUuid()).build())
                                    .build();
    when(context.getContextElement(ContextElementType.PARAM, PhaseElement.PHASE_PARAM)).thenReturn(phaseElement);
    when(context.renderExpression("1")).thenReturn("1");
    when(infrastructureMappingService.get(APP_ID, INFRA_MAPPING_ID)).thenReturn(physicalSSHInfrastructureMapping);
    when(infrastructureMappingService.selectServiceInstances(
             nullable(String.class), nullable(String.class), nullable(String.class), any()))
        .thenReturn(emptyList());
    when(context.getAppId()).thenReturn(APP_ID);
    when(context.fetchInfraMappingId()).thenReturn(INFRA_MAPPING_ID);
    when(context.fetchRequiredEnvironment()).thenReturn(anEnvironment().uuid(ENV_ID).build());
    when(context.prepareSweepingOutputBuilder(Scope.WORKFLOW)).thenReturn(SweepingOutputInstance.builder());
    when(contextElement.getUuid()).thenReturn(instance1.getUuid());
    when(serviceInstanceArtifactParam.getInstanceArtifactMap())
        .thenReturn(ImmutableMap.of(instance1.getUuid(), ARTIFACT_ID));
    when(artifactService.get(ARTIFACT_ID)).thenReturn(artifact);
    when(workflowStandardParams.isExcludeHostsWithSameArtifact()).thenReturn(true);

    PageResponse<Instance> pageResponse = aPageResponse().withResponse(asList(instance)).build();

    when(instanceService.list(any(PageRequest.class))).thenReturn(pageResponse);

    ExecutionResponse executionResponse = nodeSelectState.execute(context);

    assertThat(executionResponse.getExecutionStatus()).isEqualTo(ExecutionStatus.SUCCESS);
    assertThat(executionResponse.getErrorMessage())
        .isEqualTo("No nodes selected (Nodes already deployed with the same artifact)");
    assertThat(executionResponse.getStateExecutionData()).isNotNull();
    SelectedNodeExecutionData selectedNodeExecutionData =
        (SelectedNodeExecutionData) executionResponse.getStateExecutionData();
    assertThat(selectedNodeExecutionData).isNotNull();
    assertThat(selectedNodeExecutionData.getServiceInstanceList()).size().isEqualTo(0);
  }

  @Test
  @Owner(developers = TMACARI)
  @Category(UnitTests.class)
  public void shouldSucceedForPartialPercentageInstancesWithErrorMessage() {
    nodeSelectState.setInstanceCount("1");
    nodeSelectState.setInstanceUnitType(InstanceUnitType.PERCENTAGE);
    PhaseElement phaseElement = PhaseElement.builder()
                                    .infraDefinitionId(INFRA_DEFINITION_ID)
                                    .rollback(false)
                                    .phaseName("Phase 1")
                                    .phaseNameForRollback("Rollback Phase 1")
                                    .serviceElement(ServiceElement.builder().uuid(generateUuid()).build())
                                    .build();
    when(context.getContextElement(ContextElementType.PARAM, PhaseElement.PHASE_PARAM)).thenReturn(phaseElement);
    when(context.renderExpression("1")).thenReturn("1");
    when(infrastructureMappingService.get(APP_ID, INFRA_MAPPING_ID)).thenReturn(physicalSSHInfrastructureMapping);
    when(infrastructureMappingService.selectServiceInstances(
             nullable(String.class), nullable(String.class), nullable(String.class), any()))
        .thenReturn(emptyList());
    when(context.getAppId()).thenReturn(APP_ID);
    when(context.fetchInfraMappingId()).thenReturn(INFRA_MAPPING_ID);
    when(context.fetchRequiredEnvironment()).thenReturn(anEnvironment().uuid(ENV_ID).build());
    when(context.prepareSweepingOutputBuilder(Scope.WORKFLOW)).thenReturn(SweepingOutputInstance.builder());
    when(contextElement.getUuid()).thenReturn(instance1.getUuid());
    when(serviceInstanceArtifactParam.getInstanceArtifactMap())
        .thenReturn(ImmutableMap.of(instance1.getUuid(), ARTIFACT_ID));
    when(artifactService.get(ARTIFACT_ID)).thenReturn(artifact);
    when(workflowStandardParams.isExcludeHostsWithSameArtifact()).thenReturn(false);

    PageResponse<Instance> pageResponse = aPageResponse().withResponse(asList(instance)).build();

    when(instanceService.list(any(PageRequest.class))).thenReturn(pageResponse);

    ExecutionResponse executionResponse = nodeSelectState.execute(context);

    assertThat(executionResponse.getExecutionStatus()).isEqualTo(ExecutionStatus.SUCCESS);
    assertThat(executionResponse.getErrorMessage()).isEqualTo("No nodes selected");
    assertThat(executionResponse.getStateExecutionData()).isNotNull();
  }

  @Test
  @Owner(developers = TMACARI)
  @Category(UnitTests.class)
  public void shouldSucceedForPartialPercentageInstancesWithOverrideErrorMessage() {
    nodeSelectState.setInstanceCount("1");
    nodeSelectState.setInstanceUnitType(InstanceUnitType.PERCENTAGE);
    PhaseElement phaseElement = PhaseElement.builder()
                                    .infraDefinitionId(INFRA_DEFINITION_ID)
                                    .rollback(false)
                                    .phaseName("Phase 1")
                                    .phaseNameForRollback("Rollback Phase 1")
                                    .serviceElement(ServiceElement.builder().uuid(generateUuid()).build())
                                    .build();
    when(context.getContextElement(ContextElementType.PARAM, PhaseElement.PHASE_PARAM)).thenReturn(phaseElement);
    when(context.getContextElement(ContextElementType.STANDARD))
        .thenReturn(aWorkflowStandardParams().withExecutionHosts(Arrays.asList("host1", "host2")).build());
    when(context.renderExpression("1")).thenReturn("1");
    when(infrastructureMappingService.get(APP_ID, INFRA_MAPPING_ID)).thenReturn(physicalSSHInfrastructureMapping);
    when(infrastructureMappingService.selectServiceInstances(
             nullable(String.class), nullable(String.class), nullable(String.class), any()))
        .thenReturn(emptyList());
    when(context.getAppId()).thenReturn(APP_ID);
    when(context.fetchInfraMappingId()).thenReturn(INFRA_MAPPING_ID);
    when(context.fetchRequiredEnvironment()).thenReturn(anEnvironment().uuid(ENV_ID).build());
    when(context.prepareSweepingOutputBuilder(Scope.WORKFLOW)).thenReturn(SweepingOutputInstance.builder());
    when(contextElement.getUuid()).thenReturn(instance1.getUuid());
    when(serviceInstanceArtifactParam.getInstanceArtifactMap())
        .thenReturn(ImmutableMap.of(instance1.getUuid(), ARTIFACT_ID));
    when(artifactService.get(ARTIFACT_ID)).thenReturn(artifact);
    when(workflowStandardParams.isExcludeHostsWithSameArtifact()).thenReturn(false);

    PageResponse<Instance> pageResponse = aPageResponse().withResponse(asList(instance)).build();

    when(instanceService.list(any(PageRequest.class))).thenReturn(pageResponse);
    doReturn(ACCOUNT_ID).when(appService).getAccountIdByAppId(APP_ID);
    doReturn(true).when(featureFlagService).isEnabled(DEPLOY_TO_SPECIFIC_HOSTS, ACCOUNT_ID);
    StateExecutionInstance stateExecutionInstance = StateExecutionInstance.Builder.aStateExecutionInstance().build();
    doReturn(Collections.singletonList(stateExecutionInstance))
        .when(workflowExecutionService)
        .getStateExecutionInstancesForPhases(WORKFLOW_EXECUTION_ID);

    ExecutionResponse executionResponse = nodeSelectState.execute(context);

    assertThat(executionResponse.getExecutionStatus()).isEqualTo(ExecutionStatus.SUCCESS);
    assertThat(executionResponse.getErrorMessage())
        .isEqualTo("No nodes selected as targeted nodes have already been deployed");
    assertThat(executionResponse.getStateExecutionData()).isNotNull();
  }

  @Test
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  public void shouldFailForZeroTotalInstances() {
    nodeSelectState.setInstanceCount("100");
    nodeSelectState.setInstanceUnitType(InstanceUnitType.PERCENTAGE);
    when(infrastructureMappingService.get(APP_ID, INFRA_MAPPING_ID)).thenReturn(physicalSSHInfrastructureMapping);
    when(infrastructureMappingService.selectServiceInstances(
             nullable(String.class), nullable(String.class), nullable(String.class), any()))
        .thenReturn(emptyList());
    when(infrastructureMappingService.listHostDisplayNames(
             nullable(String.class), nullable(String.class), nullable(String.class)))
        .thenReturn(emptyList());
    when(context.getAppId()).thenReturn(APP_ID);
    when(context.renderExpression("100")).thenReturn("100");
    when(context.fetchRequiredEnvironment()).thenReturn(anEnvironment().uuid(ENV_ID).build());
    when(context.fetchInfraMappingId()).thenReturn(INFRA_MAPPING_ID);
    when(contextElement.getUuid()).thenReturn(instance1.getUuid());
    when(serviceInstanceArtifactParam.getInstanceArtifactMap())
        .thenReturn(ImmutableMap.of(instance1.getUuid(), ARTIFACT_ID));
    when(artifactService.get(ARTIFACT_ID)).thenReturn(artifact);
    when(workflowStandardParams.isExcludeHostsWithSameArtifact()).thenReturn(true);

    PageResponse<Instance> pageResponse = aPageResponse().withResponse(asList(instance)).build();

    when(instanceService.list(any(PageRequest.class))).thenReturn(pageResponse);

    ExecutionResponse executionResponse = nodeSelectState.execute(context);

    assertThat(executionResponse.getExecutionStatus()).isEqualTo(ExecutionStatus.FAILED);
    assertThat(executionResponse.getStateExecutionData()).isNull();
  }

  @Test
  @Owner(developers = VAIBHAV_SI)
  @Category(UnitTests.class)
  public void shouldReturnFalseWhenExecutionHostsNotPresent() {
    WorkflowStandardParams workflowStandardParams = aWorkflowStandardParams().build();

    boolean nodesOverridden = nodeSelectState.processExecutionHosts(APP_ID, aServiceInstanceSelectionParams(),
        workflowStandardParams, new StringBuilder(), WingsTestConstants.WORKFLOW_EXECUTION_ID);

    assertThat(nodesOverridden).isFalse();
  }

  @Test
  @Owner(developers = VAIBHAV_SI)
  @Category(UnitTests.class)
  public void shouldOverrideForFirstPhase() {
    WorkflowStandardParams workflowStandardParams =
        aWorkflowStandardParams().withExecutionHosts(Arrays.asList("host1", "host2")).build();
    doReturn(ACCOUNT_ID).when(appService).getAccountIdByAppId(APP_ID);
    doReturn(true).when(featureFlagService).isEnabled(DEPLOY_TO_SPECIFIC_HOSTS, ACCOUNT_ID);
    StateExecutionInstance stateExecutionInstance = StateExecutionInstance.Builder.aStateExecutionInstance().build();
    doReturn(Collections.singletonList(stateExecutionInstance))
        .when(workflowExecutionService)
        .getStateExecutionInstancesForPhases(WORKFLOW_EXECUTION_ID);
    StringBuilder message = new StringBuilder();
    Builder selectionParams = aServiceInstanceSelectionParams();

    boolean nodesOverridden = nodeSelectState.processExecutionHosts(
        APP_ID, selectionParams, workflowStandardParams, message, WingsTestConstants.WORKFLOW_EXECUTION_ID);

    assertThat(nodesOverridden).isTrue();
    assertThat(message.toString()).isEqualTo("Targeted nodes have overridden configured nodes");
    assertThat(selectionParams.build().getCount()).isEqualTo(2);
  }

  @Test
  @Owner(developers = VAIBHAV_SI)
  @Category(UnitTests.class)
  public void shouldSkipSubsequentPhases() {
    WorkflowStandardParams workflowStandardParams =
        aWorkflowStandardParams().withExecutionHosts(Collections.singletonList("host1")).build();
    doReturn(ACCOUNT_ID).when(appService).getAccountIdByAppId(APP_ID);
    doReturn(true).when(featureFlagService).isEnabled(DEPLOY_TO_SPECIFIC_HOSTS, ACCOUNT_ID);
    StateExecutionInstance stateExecutionInstance1 = StateExecutionInstance.Builder.aStateExecutionInstance().build();
    StateExecutionInstance stateExecutionInstance2 = StateExecutionInstance.Builder.aStateExecutionInstance().build();
    doReturn(Arrays.asList(stateExecutionInstance1, stateExecutionInstance2))
        .when(workflowExecutionService)
        .getStateExecutionInstancesForPhases(WORKFLOW_EXECUTION_ID);
    StringBuilder message = new StringBuilder();
    Builder selectionParams = aServiceInstanceSelectionParams();

    boolean nodesOverridden = nodeSelectState.processExecutionHosts(
        APP_ID, selectionParams, workflowStandardParams, message, WingsTestConstants.WORKFLOW_EXECUTION_ID);

    assertThat(nodesOverridden).isTrue();
    assertThat(message.toString()).isEqualTo("No nodes selected as targeted nodes have already been deployed");
    assertThat(selectionParams.build().getCount()).isEqualTo(0);
  }

  @Test
  @Owner(developers = YOGESH)
  @Category(UnitTests.class)
  public void testGetInstanceElements() {
    testGetInstanceElementsForNoServiceInstance();
    testGetInstanceDetailsForPartialRollout();
    testGetInstanceDetailsForPartialRolloutSpecificHosts();
  }

  private void testGetInstanceDetailsForPartialRollout() {
    List<ServiceInstance> allAvailable = asList(ServiceInstance.Builder.aServiceInstance().withUuid("id-1").build(),
        aServiceInstance().withUuid("id-2").build(), aServiceInstance().withUuid("id-3").build());
    List<InstanceElement> allInstanceElements =
        allAvailable.stream()
            .map(instance -> anInstanceElement().uuid(instance.getUuid()).build())
            .collect(Collectors.toList());
    List<ServiceInstance> deployed = asList(allAvailable.get(1));

    when(instanceExpressionProcessor.convertToInstanceElements(allAvailable)).thenReturn(allInstanceElements);

    final List<InstanceElement> instanceElements = nodeSelectState.getInstanceElements(deployed, allAvailable, false);
    assertThat(instanceElements).hasSize(3);
    assertThat(instanceElements.stream().filter(InstanceElement::isNewInstance).collect(Collectors.toList()))
        .containsExactly(allInstanceElements.get(1));
  }

  private void testGetInstanceDetailsForPartialRolloutSpecificHosts() {
    List<ServiceInstance> infraInstances = asList(aServiceInstance().withUuid("id-1").build());
    List<ServiceInstance> deployed =
        asList(aServiceInstance().withUuid("specific-1").build(), aServiceInstance().withUuid("specific-2").build());
    List<InstanceElement> infraInstanceElems =
        infraInstances.stream()
            .map(instance -> anInstanceElement().uuid(instance.getUuid()).build())
            .collect(Collectors.toList());
    List<InstanceElement> specificHostsInstanceElems =
        deployed.stream()
            .map(instance -> anInstanceElement().uuid(instance.getUuid()).build())
            .collect(Collectors.toList());

    when(instanceExpressionProcessor.convertToInstanceElements(infraInstances)).thenReturn(infraInstanceElems);
    when(instanceExpressionProcessor.convertToInstanceElements(deployed)).thenReturn(specificHostsInstanceElems);

    final List<InstanceElement> instanceElements = nodeSelectState.getInstanceElements(deployed, infraInstances, true);
    assertThat(instanceElements).hasSize(2);
    assertThat(instanceElements.stream().filter(InstanceElement::isNewInstance).collect(Collectors.toList()))
        .containsExactly(specificHostsInstanceElems.get(0), specificHostsInstanceElems.get(1));
  }

  private void testGetInstanceElementsForNoServiceInstance() {
    when(instanceExpressionProcessor.convertToInstanceElements(emptyList())).thenReturn(emptyList());
    List<InstanceElement> instanceElements = nodeSelectState.getInstanceElements(emptyList(), emptyList(), false);
    assertThat(instanceElements).isEmpty();

    // if instanceExpressionProcessor returns null for some reason
    when(instanceExpressionProcessor.convertToInstanceElements(emptyList())).thenReturn(null);
    instanceElements = nodeSelectState.getInstanceElements(emptyList(), emptyList(), false);
    assertThat(instanceElements).isEmpty();
  }

  @Test
  @Owner(developers = YOGESH)
  @Category(UnitTests.class)
  public void testGenerateSelectionParamsForAllInstances() {
    testSelectionParamsForSpecificHosts();
    testSelectionParamsForCountBasedHosts();
  }

  private void testSelectionParamsForCountBasedHosts() {
    Builder builder = aServiceInstanceSelectionParams()
                          .withSelectSpecificHosts(false)
                          .withExcludedServiceInstanceIds(asList("host-1"))
                          .withCount(5);
    final ServiceInstanceSelectionParams selectionParams =
        nodeSelectState.generateSelectionParamsForAllInstances(builder, 5);
    assertThat(selectionParams.isSelectSpecificHosts()).isFalse();
    assertThat(selectionParams.getHostNames()).isEmpty();
    assertThat(selectionParams.getCount()).isEqualTo(5);
    assertThat(selectionParams.getExcludedServiceInstanceIds()).isEmpty();
  }

  private void testSelectionParamsForSpecificHosts() {
    Builder builder = aServiceInstanceSelectionParams()
                          .withSelectSpecificHosts(true)
                          .withHostNames(asList("host-1", "host-2"))
                          .withCount(2);
    final ServiceInstanceSelectionParams selectionParams =
        nodeSelectState.generateSelectionParamsForAllInstances(builder, 5);
    assertThat(selectionParams.isSelectSpecificHosts()).isFalse();
    assertThat(selectionParams.getHostNames()).isEmpty();
    assertThat(selectionParams.getCount()).isEqualTo(5);
    assertThat(selectionParams.getExcludedServiceInstanceIds()).isEmpty();
  }

  @Test
  @Owner(developers = YOGESH)
  @Category(UnitTests.class)
  public void testGetInstanceDetails() {
    getInstanceDetailsWhenNoNewerInstancesDeployed();
    getInstanceDetailsForPartialRollout();
    getInstanceDetailsForNoInstances();
    getInstanceDetailsSpecificHosts();
  }

  private void getInstanceDetailsWhenNoNewerInstancesDeployed() {
    List<InstanceDetails> instanceDetails = nodeSelectState.getInstanceDetails(APP_ID, ENV_ID, emptyList(),
        Collections.singletonList(aServiceInstance().withUuid("id-3").withHostId("host-3").build()), false);
    assertThat(instanceDetails).isNotEmpty();
    assertThat(instanceDetails.get(0).getPhysicalHost().getInstanceId()).isEqualTo("host-3");
  }

  private void getInstanceDetailsForNoInstances() {
    assertThat(nodeSelectState.getInstanceDetails(APP_ID, ENV_ID, emptyList(), emptyList(), false)).isEmpty();
  }

  private void getInstanceDetailsForPartialRollout() {
    List<ServiceInstance> allAvailable =
        asList(ServiceInstance.Builder.aServiceInstance().withUuid("id-1").withHostId("host-1").build(),
            aServiceInstance().withUuid("id-2").withHostId("host-2").build(),
            aServiceInstance().withUuid("id-3").withHostId("host-3").build());
    List<ServiceInstance> deployed = asList(allAvailable.get(1));
    final List<InstanceDetails> instanceDetails =
        nodeSelectState.getInstanceDetails(APP_ID, ENV_ID, deployed, allAvailable, false);
    assertThat(instanceDetails).hasSize(3);
    assertThat(instanceDetails.stream().filter(InstanceDetails::isNewInstance).collect(Collectors.toList())).hasSize(1);
  }

  private void getInstanceDetailsSpecificHosts() {
    List<ServiceInstance> infraInstances = asList(aServiceInstance().withUuid("id-1").withHostId("host-1").build());
    List<ServiceInstance> deployed = asList(aServiceInstance().withUuid("id-2").withHostId("specific-1").build(),
        aServiceInstance().withUuid("id-3").withHostId("specific-2").build());
    final List<InstanceDetails> instanceDetails =
        nodeSelectState.getInstanceDetails(APP_ID, ENV_ID, deployed, infraInstances, true);
    assertThat(instanceDetails).hasSize(2);
    assertThat(instanceDetails.stream()
                   .filter(InstanceDetails::isNewInstance)
                   .map(InstanceDetails::getPhysicalHost)
                   .map(InstanceDetails.PHYSICAL_HOST::getInstanceId)
                   .collect(Collectors.toList()))
        .contains("specific-1", "specific-2")
        .hasSize(2);
  }

  @Test
  @Owner(developers = YOGESH)
  @Category(UnitTests.class)
  public void testGenerateInstanceDetailsFromServiceInstances() {
    assertThat(
        nodeSelectState.generateInstanceDetailsFromServiceInstances(
            asList(aServiceInstance().withHostId("host-1").build(), aServiceInstance().withHostId("host-2").build()),
            APP_ID, ENV_ID, true))
        .hasSize(2);

    assertThat(nodeSelectState.generateInstanceDetailsFromServiceInstances(emptyList(), APP_ID, ENV_ID, true))
        .isEmpty();

    when(hostService.getHostsByHostIds(nullable(String.class), nullable(String.class), anyList())).thenReturn(null);

    assertThat(nodeSelectState.generateInstanceDetailsFromServiceInstances(
                   asList(aServiceInstance().build()), APP_ID, ENV_ID, true))
        .isEmpty();
  }

  @Test
  @Owner(developers = YOGESH)
  @Category(UnitTests.class)
  public void testBuildInstanceDetailFromHost() {
    testBuildInstanceDetailFromAwsHost();
    testBuildInstanceDetailFromPhysicalHost();
  }

  private void testBuildInstanceDetailFromAwsHost() {
    Host host = aHost()
                    .withUuid("id-1")
                    .withHostName("ip-42")
                    .withEc2Instance(new com.amazonaws.services.ec2.model.Instance())
                    .withPublicDns("ec2-000.compute-1.amazonaws.com")
                    .build();
    InstanceDetails instanceDetails = nodeSelectState.buildInstanceDetailFromHost(host, true);
    assertThat(instanceDetails.isNewInstance()).isTrue();
    assertThat(instanceDetails.getInstanceType()).isEqualTo(InstanceDetails.InstanceType.AWS);
    assertThat(instanceDetails.getAws().getEc2Instance()).isNotNull();
    assertThat(instanceDetails.getPhysicalHost()).isNull();
    assertThat(instanceDetails.getAws().getPublicDns()).isEqualTo(host.getPublicDns());

    assertThat(nodeSelectState.buildInstanceDetailFromHost(host, false).isNewInstance()).isFalse();
  }

  private void testBuildInstanceDetailFromPhysicalHost() {
    Host host = aHost()
                    .withUuid("id-1")
                    .withHostName("ip-42")
                    .withPublicDns("harness-linux-ssh-test.westus2.cloudapp.azure.com")
                    .build();

    InstanceDetails instanceDetails = nodeSelectState.buildInstanceDetailFromHost(host, true);
    assertThat(instanceDetails.isNewInstance()).isTrue();
    assertThat(instanceDetails.getInstanceType()).isEqualTo(InstanceDetails.InstanceType.PHYSICAL_HOST);
    assertThat(instanceDetails.getAws()).isNull();
    assertThat(instanceDetails.getPhysicalHost().getPublicDns()).isEqualTo(host.getPublicDns());

    assertThat(nodeSelectState.buildInstanceDetailFromHost(host, false).isNewInstance()).isFalse();
  }

  @Test
  @Owner(developers = ANSHUL)
  @Category(UnitTests.class)
  public void testErrorMessageWithSpecificHosts() {
    nodeSelectState.setSpecificHosts(true);
    nodeSelectState.setHostNames(asList("host-10"));

    when(context.getAppId()).thenReturn(APP_ID);
    when(context.fetchRequiredEnvironment()).thenReturn(anEnvironment().uuid(ENV_ID).build());
    when(context.fetchInfraMappingId()).thenReturn(INFRA_MAPPING_ID);
    when(contextElement.getUuid()).thenReturn(instance1.getUuid());
    when(workflowStandardParams.isExcludeHostsWithSameArtifact()).thenReturn(true);

    when(infrastructureMappingService.get(APP_ID, INFRA_MAPPING_ID)).thenReturn(physicalSSHInfrastructureMapping);
    when(infrastructureMappingService.selectServiceInstances(
             nullable(String.class), nullable(String.class), nullable(String.class), any()))
        .thenReturn(emptyList());
    when(infrastructureMappingService.listHostDisplayNames(
             nullable(String.class), nullable(String.class), nullable(String.class)))
        .thenReturn(asList("host-1"));
    when(serviceInstanceArtifactParam.getInstanceArtifactMap())
        .thenReturn(ImmutableMap.of(instance1.getUuid(), ARTIFACT_ID));
    when(artifactService.get(ARTIFACT_ID)).thenReturn(artifact);

    PageResponse<Instance> pageResponse = aPageResponse().withResponse(asList(instance)).build();
    when(instanceService.list(any(PageRequest.class))).thenReturn(pageResponse);

    ExecutionResponse executionResponse = nodeSelectState.execute(context);
    assertThat(executionResponse.getExecutionStatus()).isEqualTo(ExecutionStatus.FAILED);
    assertThat(executionResponse.getErrorMessage())
        .isEqualTo(
            "No nodes were selected. 'Use Specific Hosts' was chosen with host [host-10] and 0 instances have already been deployed. \n"
            + "\n"
            + "The service infrastructure [null] does not have this host.\n"
            + "\n"
            + "Check whether you've selected a unique set of host names for each phase. ");
  }

  @Test
  @Owner(developers = TMACARI)
  @Category(UnitTests.class)
  public void testErrorMessageNoHostSpecified() {
    nodeSelectState.setSpecificHosts(true);
    nodeSelectState.setHostNames(new ArrayList<>());

    when(context.getAppId()).thenReturn(APP_ID);
    when(context.fetchRequiredEnvironment()).thenReturn(anEnvironment().uuid(ENV_ID).build());
    when(context.fetchInfraMappingId()).thenReturn(INFRA_MAPPING_ID);
    when(contextElement.getUuid()).thenReturn(instance1.getUuid());
    when(workflowStandardParams.isExcludeHostsWithSameArtifact()).thenReturn(true);

    when(infrastructureMappingService.get(APP_ID, INFRA_MAPPING_ID)).thenReturn(physicalSSHInfrastructureMapping);
    when(infrastructureMappingService.selectServiceInstances(
             nullable(String.class), nullable(String.class), nullable(String.class), any()))
        .thenReturn(emptyList());
    when(infrastructureMappingService.listHostDisplayNames(
             nullable(String.class), nullable(String.class), nullable(String.class)))
        .thenReturn(asList("host-1"));
    when(serviceInstanceArtifactParam.getInstanceArtifactMap())
        .thenReturn(ImmutableMap.of(instance1.getUuid(), ARTIFACT_ID));
    when(artifactService.get(ARTIFACT_ID)).thenReturn(artifact);

    PageResponse<Instance> pageResponse = aPageResponse().withResponse(asList(instance)).build();
    when(instanceService.list(any(PageRequest.class))).thenReturn(pageResponse);

    ExecutionResponse executionResponse = nodeSelectState.execute(context);
    assertThat(executionResponse.getExecutionStatus()).isEqualTo(ExecutionStatus.FAILED);
    assertThat(executionResponse.getErrorMessage())
        .isEqualTo(
            "No nodes were selected. 'Use Specific Hosts' was chosen but no host names were specified.  and 0 instances have already been deployed. \n"
            + "\n"
            + "\n"
            + "\n"
            + "Check whether you've selected a unique set of host names for each phase. ");
  }

  @Test
  @Owner(developers = TMACARI)
  @Category(UnitTests.class)
  public void testErrorMessageNoSpecificHosts() {
    nodeSelectState.setSpecificHosts(false);
    nodeSelectState.setHostNames(new ArrayList<>());

    when(context.getAppId()).thenReturn(APP_ID);
    when(context.fetchRequiredEnvironment()).thenReturn(anEnvironment().uuid(ENV_ID).build());
    when(context.fetchInfraMappingId()).thenReturn(INFRA_MAPPING_ID);
    when(contextElement.getUuid()).thenReturn(instance1.getUuid());
    when(workflowStandardParams.isExcludeHostsWithSameArtifact()).thenReturn(true);

    when(infrastructureMappingService.get(APP_ID, INFRA_MAPPING_ID)).thenReturn(physicalSSHInfrastructureMapping);
    when(infrastructureMappingService.selectServiceInstances(
             nullable(String.class), nullable(String.class), nullable(String.class), any()))
        .thenReturn(emptyList());
    when(infrastructureMappingService.listHostDisplayNames(
             nullable(String.class), nullable(String.class), nullable(String.class)))
        .thenReturn(asList("host-1"));
    when(serviceInstanceArtifactParam.getInstanceArtifactMap())
        .thenReturn(ImmutableMap.of(instance1.getUuid(), ARTIFACT_ID));
    when(artifactService.get(ARTIFACT_ID)).thenReturn(artifact);

    PageResponse<Instance> pageResponse = aPageResponse().withResponse(asList(instance)).build();
    when(instanceService.list(any(PageRequest.class))).thenReturn(pageResponse);

    ExecutionResponse executionResponse = nodeSelectState.execute(context);
    assertThat(executionResponse.getExecutionStatus()).isEqualTo(ExecutionStatus.FAILED);
    assertThat(executionResponse.getErrorMessage())
        .isEqualTo(
            "No nodes were selected. This phase deploys to 0 instances (cumulative) and 0 instances have already been deployed. \n"
            + "\n");
  }

  @Test
  @Owner(developers = TMACARI)
  @Category(UnitTests.class)
  public void testErrorMessageNoSpecificHostsWithAwsInfraMappingWithNonProvisionedInfrastructure() {
    nodeSelectState.setSpecificHosts(false);
    nodeSelectState.setHostNames(new ArrayList<>());

    when(context.getAppId()).thenReturn(APP_ID);
    when(context.fetchRequiredEnvironment()).thenReturn(anEnvironment().uuid(ENV_ID).build());
    when(context.fetchInfraMappingId()).thenReturn(INFRA_MAPPING_ID);
    when(contextElement.getUuid()).thenReturn(instance1.getUuid());
    when(workflowStandardParams.isExcludeHostsWithSameArtifact()).thenReturn(true);

    when(infrastructureMappingService.get(APP_ID, INFRA_MAPPING_ID)).thenReturn(awsInfrastructureMapping);
    when(infrastructureMappingService.selectServiceInstances(
             nullable(String.class), nullable(String.class), nullable(String.class), any()))
        .thenReturn(emptyList());
    when(infrastructureMappingService.listHostDisplayNames(
             nullable(String.class), nullable(String.class), nullable(String.class)))
        .thenReturn(asList("host-1"));
    when(serviceInstanceArtifactParam.getInstanceArtifactMap())
        .thenReturn(ImmutableMap.of(instance1.getUuid(), ARTIFACT_ID));
    when(artifactService.get(ARTIFACT_ID)).thenReturn(artifact);

    PageResponse<Instance> pageResponse = aPageResponse().withResponse(asList(instance)).build();
    when(instanceService.list(any(PageRequest.class))).thenReturn(pageResponse);

    ExecutionResponse executionResponse = nodeSelectState.execute(context);
    assertThat(executionResponse.getExecutionStatus()).isEqualTo(ExecutionStatus.FAILED);
    assertThat(executionResponse.getErrorMessage())
        .isEqualTo(
            "No nodes were selected. This phase deploys to 0 instances (cumulative) and 0 instances have already been deployed. \n"
            + "\n"
            + "\n"
            + "\n"
            + "Check whether the filters specified in your service infrastructure are correct. ");
  }

  @Test
  @Owner(developers = TMACARI)
  @Category(UnitTests.class)
  public void testErrorMessageNoSpecificHostsWithAwsInfraMappingWithProvisionedInfrastructure() {
    nodeSelectState.setSpecificHosts(false);
    nodeSelectState.setHostNames(new ArrayList<>());
    awsInfrastructureMapping.setProvisionInstances(true);

    when(context.getAppId()).thenReturn(APP_ID);
    when(context.fetchRequiredEnvironment()).thenReturn(anEnvironment().uuid(ENV_ID).build());
    when(context.fetchInfraMappingId()).thenReturn(INFRA_MAPPING_ID);
    when(contextElement.getUuid()).thenReturn(instance1.getUuid());
    when(workflowStandardParams.isExcludeHostsWithSameArtifact()).thenReturn(true);

    when(infrastructureMappingService.get(APP_ID, INFRA_MAPPING_ID)).thenReturn(awsInfrastructureMapping);
    when(infrastructureMappingService.selectServiceInstances(
             nullable(String.class), nullable(String.class), nullable(String.class), any()))
        .thenReturn(emptyList());
    when(infrastructureMappingService.listHostDisplayNames(
             nullable(String.class), nullable(String.class), nullable(String.class)))
        .thenReturn(asList("host-1"));
    when(serviceInstanceArtifactParam.getInstanceArtifactMap())
        .thenReturn(ImmutableMap.of(instance1.getUuid(), ARTIFACT_ID));
    when(artifactService.get(ARTIFACT_ID)).thenReturn(artifact);

    PageResponse<Instance> pageResponse = aPageResponse().withResponse(asList(instance)).build();
    when(instanceService.list(any(PageRequest.class))).thenReturn(pageResponse);

    ExecutionResponse executionResponse = nodeSelectState.execute(context);
    assertThat(executionResponse.getExecutionStatus()).isEqualTo(ExecutionStatus.FAILED);
    assertThat(executionResponse.getErrorMessage())
        .isEqualTo(
            "No nodes were selected. This phase deploys to 0 instances (cumulative) and 0 instances have already been deployed. \n"
            + "\n"
            + "\n"
            + "\n"
            + "Check whether your Auto Scale group [null] capacity has changed. ");
  }

  @Test
  @Owner(developers = TMACARI)
  @Category(UnitTests.class)
  public void testErrorMessageTooManyNodesSelected() {
    nodeSelectState.setSpecificHosts(false);
    nodeSelectState.setHostNames(new ArrayList<>());
    awsInfrastructureMapping.setProvisionInstances(true);

    when(context.getAppId()).thenReturn(APP_ID);
    when(context.fetchRequiredEnvironment()).thenReturn(anEnvironment().uuid(ENV_ID).build());
    when(context.fetchInfraMappingId()).thenReturn(INFRA_MAPPING_ID);
    when(contextElement.getUuid()).thenReturn(instance1.getUuid());
    when(workflowStandardParams.isExcludeHostsWithSameArtifact()).thenReturn(true);
    when(featureFlagService.isNotEnabled(eq(DEPLOY_TO_INLINE_HOSTS), any())).thenReturn(true);

    when(infrastructureMappingService.get(APP_ID, INFRA_MAPPING_ID)).thenReturn(awsInfrastructureMapping);
    when(infrastructureMappingService.selectServiceInstances(
             nullable(String.class), nullable(String.class), nullable(String.class), any()))
        .thenReturn(instances);
    when(infrastructureMappingService.listHostDisplayNames(
             nullable(String.class), nullable(String.class), nullable(String.class)))
        .thenReturn(asList("host-1"));
    when(serviceInstanceArtifactParam.getInstanceArtifactMap())
        .thenReturn(ImmutableMap.of(instance1.getUuid(), ARTIFACT_ID));
    when(artifactService.get(ARTIFACT_ID)).thenReturn(artifact);

    PageResponse<Instance> pageResponse = aPageResponse().withResponse(asList(instance)).build();
    when(instanceService.list(any(PageRequest.class))).thenReturn(pageResponse);

    ExecutionResponse executionResponse = nodeSelectState.execute(context);
    assertThat(executionResponse.getExecutionStatus()).isEqualTo(ExecutionStatus.FAILED);
    assertThat(executionResponse.getErrorMessage())
        .isEqualTo(
            "Too many nodes selected. Did you change service infrastructure without updating Select Nodes in the workflow?");
  }

  @Test
  @Owner(developers = TMACARI)
  @Category(UnitTests.class)
  public void testErrorMessageLicenceConstraint() {
    nodeSelectState.setSpecificHosts(false);
    nodeSelectState.setHostNames(new ArrayList<>());
    awsInfrastructureMapping.setProvisionInstances(true);
    Account account = new Account();
    LicenseInfo licenseInfo = new LicenseInfo();
    licenseInfo.setAccountType(COMMUNITY);
    account.setLicenseInfo(licenseInfo);

    when(context.getAppId()).thenReturn(APP_ID);
    when(context.fetchRequiredEnvironment()).thenReturn(anEnvironment().uuid(ENV_ID).build());
    when(context.fetchInfraMappingId()).thenReturn(INFRA_MAPPING_ID);
    when(contextElement.getUuid()).thenReturn(instance1.getUuid());
    when(workflowStandardParams.isExcludeHostsWithSameArtifact()).thenReturn(true);
    doReturn(account).when(accountService).get(any());

    when(infrastructureMappingService.get(APP_ID, INFRA_MAPPING_ID)).thenReturn(awsInfrastructureMapping);
    when(infrastructureMappingService.selectServiceInstances(
             nullable(String.class), nullable(String.class), nullable(String.class), any()))
        .thenReturn(Lists.newArrayList(instance1, instance2, instance3, instance3, instance3, instance3, instance3,
            instance3, instance3, instance3, instance3));
    when(infrastructureMappingService.listHostDisplayNames(
             nullable(String.class), nullable(String.class), nullable(String.class)))
        .thenReturn(asList("host-1", "host-2", "host-3", "host-3", "host-3", "host-3", "host-3", "host-3", "host-3",
            "host-3", "host-3"));
    when(serviceInstanceArtifactParam.getInstanceArtifactMap())
        .thenReturn(ImmutableMap.of(instance1.getUuid(), ARTIFACT_ID));
    when(artifactService.get(ARTIFACT_ID)).thenReturn(artifact);

    PageResponse<Instance> pageResponse = aPageResponse().withResponse(asList(instance)).build();
    when(instanceService.list(any(PageRequest.class))).thenReturn(pageResponse);

    ExecutionResponse executionResponse = nodeSelectState.execute(context);
    assertThat(executionResponse.getExecutionStatus()).isEqualTo(ExecutionStatus.FAILED);
    assertThat(executionResponse.getErrorMessage())
        .isEqualTo(
            "The license for this account does not allow more than 10 concurrent instance deployments. Please contact Harness Support.");
  }

  @Test
  @Owner(developers = TMACARI)
  @Category(UnitTests.class)
  public void testExecuteForRollingWorkflowInstancesToAddIsInstanceCountTotal() {
    nodeSelectState.setSpecificHosts(false);
    nodeSelectState.setInstanceCount("100");
    nodeSelectState.setInstanceUnitType(InstanceUnitType.COUNT);
    StateExecutionInstance stateExecutionInstance = new StateExecutionInstance();
    stateExecutionInstance.setUuid(generateUuid());
    stateExecutionInstance.setDisplayName("DC_NODE_SELECT");
    stateExecutionInstance.setOrchestrationWorkflowType(OrchestrationWorkflowType.ROLLING);

    ArgumentCaptor<ServiceInstanceSelectionParams> argumentCaptor =
        ArgumentCaptor.forClass(ServiceInstanceSelectionParams.class);

    when(context.getStateExecutionInstance()).thenReturn(stateExecutionInstance);
    when(context.renderExpression("100")).thenReturn("100");
    when(context.getAppId()).thenReturn(APP_ID);
    when(context.fetchRequiredEnvironment()).thenReturn(anEnvironment().uuid(ENV_ID).build());
    when(context.fetchInfraMappingId()).thenReturn(INFRA_MAPPING_ID);
    when(contextElement.getUuid()).thenReturn(instance1.getUuid());
    when(workflowStandardParams.isExcludeHostsWithSameArtifact()).thenReturn(true);

    when(infrastructureMappingService.get(APP_ID, INFRA_MAPPING_ID)).thenReturn(physicalSSHInfrastructureMapping);
    when(infrastructureMappingService.selectServiceInstances(
             nullable(String.class), nullable(String.class), nullable(String.class), any()))
        .thenReturn(emptyList());
    when(infrastructureMappingService.listHostDisplayNames(
             nullable(String.class), nullable(String.class), nullable(String.class)))
        .thenReturn(asList("host-1"));
    when(serviceInstanceArtifactParam.getInstanceArtifactMap())
        .thenReturn(ImmutableMap.of(instance1.getUuid(), ARTIFACT_ID));
    when(artifactService.get(ARTIFACT_ID)).thenReturn(artifact);

    PageResponse<Instance> pageResponse = aPageResponse().withResponse(asList(instance)).build();
    when(instanceService.list(any(PageRequest.class))).thenReturn(pageResponse);

    nodeSelectState.execute(context);
    verify(infrastructureMappingService, times(2))
        .selectServiceInstances(
            nullable(String.class), nullable(String.class), nullable(String.class), argumentCaptor.capture());
    assertThat(argumentCaptor.getAllValues().get(0).getCount()).isEqualTo(100);
  }

  @Test
  @Owner(developers = TMACARI)
  @Category(UnitTests.class)
  public void testThrowExceptionWhenAutoScalingGroup() {
    nodeSelectState.setSpecificHosts(true);
    nodeSelectState.setHostNames(asList("host-10"));

    when(context.getAppId()).thenReturn(APP_ID);
    when(context.fetchRequiredEnvironment()).thenReturn(anEnvironment().uuid(ENV_ID).build());
    when(context.fetchInfraMappingId()).thenReturn(INFRA_MAPPING_ID);
    when(contextElement.getUuid()).thenReturn(instance1.getUuid());
    when(workflowStandardParams.isExcludeHostsWithSameArtifact()).thenReturn(true);

    when(infrastructureMappingService.get(APP_ID, INFRA_MAPPING_ID))
        .thenReturn(anAwsInfrastructureMapping().withProvisionInstances(true).build());
    when(infrastructureMappingService.listHostDisplayNames(
             nullable(String.class), nullable(String.class), nullable(String.class)))
        .thenReturn(asList("host-1"));

    assertThatThrownBy(() -> nodeSelectState.execute(context))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("Cannot specify hosts when using an auto scale group");
  }

  @Test
  @Owner(developers = TMACARI)
  @Category(UnitTests.class)
  public void testValidateFields() {
    NodeSelectState nodeSelectState = new DcNodeSelectState("NodeSelect");
    nodeSelectState.setSpecificHosts(true);
    nodeSelectState.setHostNames(new ArrayList<>());

    Map<String, String> result = nodeSelectState.validateFields();
    assertThat(result.get(SELECT_NODE_NAME)).isEqualTo("Hostnames must be specified");

    nodeSelectState.setSpecificHosts(false);
    nodeSelectState.setHostNames(new ArrayList<>());
    Map<String, String> result2 = nodeSelectState.validateFields();

    assertThat(result2.size()).isEqualTo(0);

    nodeSelectState.setSpecificHosts(true);
    nodeSelectState.setHostNames(asList("hostname"));
    Map<String, String> result3 = nodeSelectState.validateFields();

    assertThat(result3.size()).isEqualTo(0);
  }

  @Test
  @Owner(developers = ANIL)
  @Category(UnitTests.class)
  public void testInstanceCountExpressionRendering() {
    // Test for instance count
    nodeSelectState.setInstanceCount(null);
    nodeSelectState.setInstanceUnitType(InstanceUnitType.COUNT);
    assertThat(nodeSelectState.renderInstanceCount(context)).isEqualTo(0);

    nodeSelectState.setInstanceCount("2");
    when(context.renderExpression("2")).thenReturn("2");
    assertThat(nodeSelectState.renderInstanceCount(context)).isEqualTo(2);

    nodeSelectState.setInstanceCount(INSTANCE_COUNT_EXPRESSION);
    when(context.renderExpression(INSTANCE_COUNT_EXPRESSION)).thenReturn("150");
    assertThat(nodeSelectState.renderInstanceCount(context)).isEqualTo(150);

    when(context.renderExpression(INSTANCE_COUNT_EXPRESSION)).thenReturn("0");
    assertThatThrownBy(() -> nodeSelectState.renderInstanceCount(context)).isInstanceOf(InvalidRequestException.class);

    when(context.renderExpression(INSTANCE_COUNT_EXPRESSION)).thenReturn("Count");
    assertThatThrownBy(() -> nodeSelectState.renderInstanceCount(context)).isInstanceOf(InvalidRequestException.class);

    // Test for instance percentage
    nodeSelectState.setInstanceUnitType(InstanceUnitType.PERCENTAGE);

    when(context.renderExpression(INSTANCE_COUNT_EXPRESSION)).thenReturn("50");
    assertThat(nodeSelectState.renderInstanceCount(context)).isEqualTo(50);

    when(context.renderExpression(INSTANCE_COUNT_EXPRESSION)).thenReturn("101");
    assertThatThrownBy(() -> nodeSelectState.renderInstanceCount(context)).isInstanceOf(InvalidRequestException.class);
  }
}
