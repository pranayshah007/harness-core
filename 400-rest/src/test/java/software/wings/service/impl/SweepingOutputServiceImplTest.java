/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl;

import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.rule.OwnerRule.ADWAIT;
import static io.harness.rule.OwnerRule.GEORGE;
import static io.harness.rule.OwnerRule.KAMAL;
import static io.harness.rule.OwnerRule.PRASHANT;

import static software.wings.beans.Application.Builder.anApplication;
import static software.wings.sm.StateExecutionInstance.Builder.aStateExecutionInstance;
import static software.wings.utils.WingsTestConstants.ACCOUNT_ID;
import static software.wings.utils.WingsTestConstants.APP_NAME;
import static software.wings.utils.WingsTestConstants.ENV_ID;
import static software.wings.utils.WingsTestConstants.ENV_NAME;

import static org.assertj.core.api.Assertions.assertThat;
import static org.joor.Reflect.on;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.isA;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import io.harness.beans.SweepingOutput;
import io.harness.beans.SweepingOutputInstance;
import io.harness.beans.SweepingOutputInstance.Scope;
import io.harness.beans.SweepingOutputInstance.SweepingOutputInstanceBuilder;
import io.harness.category.element.UnitTests;
import io.harness.deployment.InstanceDetails;
import io.harness.ff.FeatureFlagService;
import io.harness.rule.Owner;
import io.harness.serializer.KryoSerializer;

import software.wings.WingsBaseTest;
import software.wings.api.ContextElementParamMapper;
import software.wings.api.ContextElementParamMapperFactory;
import software.wings.api.InstanceElement;
import software.wings.api.InstanceElementParamMapper;
import software.wings.api.NoopContextElementParamMapper;
import software.wings.api.PcfInstanceElement;
import software.wings.api.PhaseElement;
import software.wings.api.instancedetails.InstanceInfoVariables;
import software.wings.beans.Application;
import software.wings.beans.Environment;
import software.wings.common.VariableProcessor;
import software.wings.expression.ManagerExpressionEvaluator;
import software.wings.expression.MapTestSweepingOutput;
import software.wings.expression.SweepingOutputData;
import software.wings.service.intfc.sweepingoutput.SweepingOutputInquiry;
import software.wings.service.intfc.sweepingoutput.SweepingOutputService;
import software.wings.sm.ContextElement;
import software.wings.sm.ExecutionContext;
import software.wings.sm.ExecutionContextImpl;
import software.wings.sm.StateExecutionInstance;
import software.wings.sm.StateType;
import software.wings.sm.WorkflowStandardParams;
import software.wings.sm.WorkflowStandardParamsExtensionService;
import software.wings.sm.states.pcf.PcfStateHelper;

import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;

public class SweepingOutputServiceImplTest extends WingsBaseTest {
  private static final String SWEEPING_OUTPUT_NAME = "SWEEPING_OUTPUT_NAME";
  private static final String MAP_SWEEPING_OUTPUT_NAME = "MAP_SWEEPING_OUTPUT_NAME";
  private static final String SWEEPING_OUTPUT_CONTENT = "SWEEPING_OUTPUT_CONTENT";

  private final String infraDefinitionId = generateUuid();
  private final String workflowExecutionUuid = generateUuid();
  private final String pipelineExecutionUuid = generateUuid();
  private final String appId = generateUuid();
  private final String stateExecutionInstanceId = generateUuid();
  private final String phaseElementId = generateUuid();
  private final String phaseName = "Phase 1";

  @InjectMocks @Inject private SweepingOutputService sweepingOutputService;
  @Inject private PcfStateHelper pcfStateHelper;

  private SweepingOutputInstance sweepingOutputInstance;
  private StateExecutionInstance stateExecutionInstance;
  @Inject KryoSerializer kryoSerializer;

  @Before
  public void setup() {
    LinkedList<ContextElement> contextElements = new LinkedList<>();
    ContextElement phaseElement = PhaseElement.builder()
                                      .uuid(phaseElementId)
                                      .infraDefinitionId(infraDefinitionId)
                                      .rollback(false)
                                      .phaseName(phaseName)
                                      .phaseNameForRollback("Rollback Phase 1")
                                      .onDemandRollback(false)
                                      .build();
    contextElements.add(phaseElement);
    stateExecutionInstance = aStateExecutionInstance()
                                 .uuid(stateExecutionInstanceId)
                                 .appId(appId)
                                 .executionUuid(workflowExecutionUuid)
                                 .stateType(StateType.AWS_NODE_SELECT.name())
                                 .displayName(StateType.AWS_NODE_SELECT.name())
                                 .stateName(StateType.AWS_NODE_SELECT.name())
                                 .contextElements(contextElements)
                                 .build();
    String phaseExecutionId = workflowExecutionUuid + phaseElementId + "Phase 1";
    SweepingOutputInstanceBuilder sweepingOutputBuilder = SweepingOutputServiceImpl.prepareSweepingOutputBuilder(appId,
        pipelineExecutionUuid, workflowExecutionUuid, phaseExecutionId, stateExecutionInstanceId, Scope.WORKFLOW);

    sweepingOutputInstance =
        sweepingOutputService.save(sweepingOutputBuilder.name(SWEEPING_OUTPUT_NAME)
                                       .output(kryoSerializer.asBytes(SWEEPING_OUTPUT_CONTENT))
                                       .value(SweepingOutputData.builder().text(SWEEPING_OUTPUT_CONTENT).build())
                                       .build());
  }

  @Test
  @Owner(developers = GEORGE)
  @Category(UnitTests.class)
  public void testSweepingOutputObtainValue() {
    SweepingOutputInstance savedSweepingOutputInstance =
        sweepingOutputService.find(SweepingOutputInquiry.builder()
                                       .name(SWEEPING_OUTPUT_NAME)
                                       .appId(sweepingOutputInstance.getAppId())
                                       .phaseExecutionId(sweepingOutputInstance.getPipelineExecutionId())
                                       .workflowExecutionId(sweepingOutputInstance.getWorkflowExecutionIds().get(0))
                                       .build());

    assertThat(((SweepingOutputData) savedSweepingOutputInstance.getValue()).getText())
        .isEqualTo(SWEEPING_OUTPUT_CONTENT);

    assertThat(savedSweepingOutputInstance.getValueOutput()).isNotEmpty();
    SweepingOutput sweepingOutput =
        (SweepingOutput) kryoSerializer.asObject(savedSweepingOutputInstance.getValueOutput());
    assertThat(sweepingOutput).isNotNull();
    assertThat(sweepingOutput).isInstanceOf(SweepingOutputData.class);
    assertThat(((SweepingOutputData) sweepingOutput).getText()).isEqualTo(SWEEPING_OUTPUT_CONTENT);
  }

  @Test
  @Owner(developers = PRASHANT)
  @Category(UnitTests.class)
  public void testSweepingOutputObtainValueForMap() {
    String phaseExecutionId = workflowExecutionUuid + phaseElementId + "Phase 1";
    SweepingOutputInstanceBuilder sweepingOutputBuilder = SweepingOutputServiceImpl.prepareSweepingOutputBuilder(appId,
        pipelineExecutionUuid, workflowExecutionUuid, phaseExecutionId, stateExecutionInstanceId, Scope.WORKFLOW);
    MapTestSweepingOutput mapSweepingOutput = new MapTestSweepingOutput();
    mapSweepingOutput.put("testKey1", "testValue1");
    mapSweepingOutput.put("testKey2", SweepingOutputData.builder().text(SWEEPING_OUTPUT_CONTENT).build());
    SweepingOutputInstance mapSweepingOutputInstance = sweepingOutputService.save(
        sweepingOutputBuilder.name(MAP_SWEEPING_OUTPUT_NAME).value(mapSweepingOutput).build());
    SweepingOutputInstance savedSweepingOutputInstance =
        sweepingOutputService.find(SweepingOutputInquiry.builder()
                                       .name(MAP_SWEEPING_OUTPUT_NAME)
                                       .appId(mapSweepingOutputInstance.getAppId())
                                       .phaseExecutionId(mapSweepingOutputInstance.getPipelineExecutionId())
                                       .workflowExecutionId(mapSweepingOutputInstance.getWorkflowExecutionIds().get(0))
                                       .build());

    assertThat(savedSweepingOutputInstance.getValue()).isInstanceOf(MapTestSweepingOutput.class);
    assertThat(((MapTestSweepingOutput) savedSweepingOutputInstance.getValue()).get("testKey1"))
        .isEqualTo("testValue1");

    assertThat(((MapTestSweepingOutput) savedSweepingOutputInstance.getValue()).get("testKey2"))
        .isInstanceOf(SweepingOutputData.class);

    assertThat(((SweepingOutputData) ((MapTestSweepingOutput) savedSweepingOutputInstance.getValue()).get("testKey2"))
                   .getText())
        .isEqualTo(SWEEPING_OUTPUT_CONTENT);
  }

  @Test
  @Owner(developers = PRASHANT)
  @Category(UnitTests.class)
  public void testCopyOutputsForAnotherWorkflowExecution() {
    final String anotherWorkflowId = generateUuid();
    sweepingOutputService.copyOutputsForAnotherWorkflowExecution(
        sweepingOutputInstance.getAppId(), sweepingOutputInstance.getWorkflowExecutionIds().get(0), anotherWorkflowId);

    SweepingOutputInstance savedSweepingOutputInstance1 =
        sweepingOutputService.find(SweepingOutputInquiry.builder()
                                       .name(SWEEPING_OUTPUT_NAME)
                                       .appId(sweepingOutputInstance.getAppId())
                                       .phaseExecutionId(sweepingOutputInstance.getPipelineExecutionId())
                                       .workflowExecutionId(sweepingOutputInstance.getWorkflowExecutionIds().get(0))
                                       .build());
    assertThat(savedSweepingOutputInstance1).isNotNull();
    assertThat(savedSweepingOutputInstance1.getWorkflowExecutionIds())
        .containsExactly(sweepingOutputInstance.getWorkflowExecutionIds().get(0), anotherWorkflowId);

    SweepingOutputInstance savedSweepingOutputInstance2 =
        sweepingOutputService.find(SweepingOutputInquiry.builder()
                                       .name(SWEEPING_OUTPUT_NAME)
                                       .appId(sweepingOutputInstance.getAppId())
                                       .phaseExecutionId(sweepingOutputInstance.getPipelineExecutionId())
                                       .workflowExecutionId(anotherWorkflowId)
                                       .build());
    assertThat(savedSweepingOutputInstance2).isNotNull();
    assertThat(savedSweepingOutputInstance2.getWorkflowExecutionIds())
        .containsExactly(sweepingOutputInstance.getWorkflowExecutionIds().get(0), anotherWorkflowId);

    assertThat(savedSweepingOutputInstance1.getUuid()).isEqualTo(sweepingOutputInstance.getUuid());
    assertThat(savedSweepingOutputInstance2.getUuid()).isEqualTo(sweepingOutputInstance.getUuid());

    assertThat(savedSweepingOutputInstance1.getOutput()).isEqualTo(sweepingOutputInstance.getOutput());
    assertThat(savedSweepingOutputInstance2.getOutput()).isEqualTo(sweepingOutputInstance.getOutput());
  }

  @Test
  @Owner(developers = GEORGE)
  @Category(UnitTests.class)
  public void testCopyOutputsForAnotherWorkflowExecutionForSameExecution() {
    sweepingOutputService.copyOutputsForAnotherWorkflowExecution(sweepingOutputInstance.getAppId(),
        sweepingOutputInstance.getWorkflowExecutionIds().get(0),
        sweepingOutputInstance.getWorkflowExecutionIds().get(0));

    SweepingOutputInstance savedSweepingOutputInstance1 =
        sweepingOutputService.find(SweepingOutputInquiry.builder()
                                       .name(SWEEPING_OUTPUT_NAME)
                                       .appId(sweepingOutputInstance.getAppId())
                                       .phaseExecutionId(sweepingOutputInstance.getPipelineExecutionId())
                                       .workflowExecutionId(sweepingOutputInstance.getWorkflowExecutionIds().get(0))
                                       .build());
    assertThat(savedSweepingOutputInstance1).isNotNull();
  }

  @Test
  @Owner(developers = PRASHANT)
  @Category(UnitTests.class)
  public void testFindSweepingOutput() {
    SweepingOutput sweepingOutput = sweepingOutputService.findSweepingOutput(
        SweepingOutputInquiry.builder()
            .name(SWEEPING_OUTPUT_NAME)
            .appId(sweepingOutputInstance.getAppId())
            .phaseExecutionId(sweepingOutputInstance.getPipelineExecutionId())
            .workflowExecutionId(sweepingOutputInstance.getWorkflowExecutionIds().get(0))
            .build());
    assertThat(sweepingOutput).isNotNull();
    assertThat(sweepingOutput).isInstanceOf(SweepingOutputData.class);
    assertThat(((SweepingOutputData) sweepingOutput).getText()).isEqualTo(SWEEPING_OUTPUT_CONTENT);
  }

  @Test
  @Owner(developers = PRASHANT)
  @Category(UnitTests.class)
  public void testFindSweepingOutputForNull() {
    SweepingOutput sweepingOutput = sweepingOutputService.findSweepingOutput(
        SweepingOutputInquiry.builder()
            .name("Some Name")
            .appId(sweepingOutputInstance.getAppId())
            .phaseExecutionId(sweepingOutputInstance.getPipelineExecutionId())
            .workflowExecutionId(sweepingOutputInstance.getWorkflowExecutionIds().get(0))
            .build());
    assertThat(sweepingOutput).isNull();
  }

  @Test
  @Owner(developers = PRASHANT)
  @Category(UnitTests.class)
  public void testCleanForStateExecutionInstance() {
    sweepingOutputService.cleanForStateExecutionInstance(stateExecutionInstance);
    SweepingOutput sweepingOutput =
        sweepingOutputService.findSweepingOutput(SweepingOutputInquiry.builder()
                                                     .name(SWEEPING_OUTPUT_NAME)
                                                     .appId(appId)
                                                     .phaseExecutionId(sweepingOutputInstance.getPhaseExecutionId())
                                                     .workflowExecutionId(workflowExecutionUuid)
                                                     .stateExecutionId(stateExecutionInstanceId)
                                                     .build());
    assertThat(sweepingOutput).isNull();
  }

  @Test
  @Owner(developers = ADWAIT)
  @Category(UnitTests.class)
  public void testInstanceDetailsFetchFromSweepingOutput() {
    sweepingOutputService.cleanForStateExecutionInstance(stateExecutionInstance);

    List<PcfInstanceElement> pcfInstanceElements = Arrays.asList(PcfInstanceElement.builder()
                                                                     .isUpsize(false)
                                                                     .displayName("pcf_0")
                                                                     .instanceIndex("0")
                                                                     .applicationId("id0")
                                                                     .build(),
        PcfInstanceElement.builder()
            .isUpsize(true)
            .displayName("pcf_1")
            .instanceIndex("1")
            .applicationId("id1")
            .build(),
        PcfInstanceElement.builder()
            .isUpsize(true)
            .displayName("pcf_1")
            .instanceIndex("2")
            .applicationId("id1")
            .build());

    VariableProcessor variableProcessor = mock(VariableProcessor.class);
    doReturn(Collections.emptyMap()).when(variableProcessor).getVariables(any(), anyString());
    ExecutionContext context = generateExecutionContext();
    on(context).set("variableProcessor", variableProcessor);
    on(context).set("evaluator", new ManagerExpressionEvaluator());
    on(context).set("sweepingOutputService", sweepingOutputService);

    saveSweepingOutput(InstanceInfoVariables.builder()
                           .instanceElements(pcfStateHelper.generateInstanceElement(pcfInstanceElements))
                           .instanceDetails(pcfStateHelper.generateInstanceDetails(pcfInstanceElements))
                           .build());
    saveSweepingOutput(InstanceInfoVariables.builder().newInstanceTrafficPercent(10).build());

    SweepingOutputInquiry sweepingOutputInquiry = SweepingOutputInquiry.builder()
                                                      .name(InstanceInfoVariables.SWEEPING_OUTPUT_NAME)
                                                      .appId(appId)
                                                      .phaseExecutionId(phaseElementId)
                                                      .workflowExecutionId(workflowExecutionUuid)
                                                      .stateExecutionId(stateExecutionInstanceId)
                                                      .build();

    SweepingOutput sweepingOutput = sweepingOutputService.findSweepingOutput(sweepingOutputInquiry);

    // All (New + Existing) Instances
    assertThat(sweepingOutput instanceof InstanceInfoVariables).isTrue();
    List<InstanceDetails> instanceDetails =
        sweepingOutputService.fetchInstanceDetailsFromSweepingOutput(sweepingOutputInquiry, false);
    assertThat(instanceDetails.size()).isEqualTo(3);
    List<InstanceElement> instanceElements =
        sweepingOutputService.fetchInstanceElementsFromSweepingOutput(sweepingOutputInquiry, false);
    assertThat(instanceElements.size()).isEqualTo(3);

    assertThat(context.renderExpressionsForInstanceDetails("${instance.hostName}", true).getNewInstanceTrafficPercent())
        .isEqualTo(Optional.of(10));
    assertThat(
        context.renderExpressionsForInstanceDetails("${instance.hostName}", false).getNewInstanceTrafficPercent())
        .isEqualTo(Optional.of(10));
    assertThat(context.renderExpressionsForInstanceDetails("${instance.hostName}", false).getInstances())
        .containsExactly("pcf_0:0", "pcf_1:1", "pcf_1:2");
    assertThat(context
                   .renderExpressionsForInstanceDetails("${instance.hostName}"
                           + "-"
                           + "${instanceDetails.pcf.instanceIndex}",
                       false)
                   .getInstances())
        .containsExactly("pcf_0:0-0", "pcf_1:1-1", "pcf_1:2-2");
    assertThat(context.renderExpressionsForInstanceDetails("${instance.newInstance}", false).getInstances())
        .containsExactly("false", "true", "true");
    assertThat(context.renderExpressionsForInstanceDetails("${host.hostName}", false).getInstances())
        .containsExactly("pcf_0:0", "pcf_1:1", "pcf_1:2");
    assertThat(context.renderExpressionsForInstanceDetails("${instanceDetails.hostName}", false).getInstances())
        .containsExactly("pcf_0:0", "pcf_1:1", "pcf_1:2");
    assertThat(context.renderExpressionsForInstanceDetails("${instanceDetails.newInstance}", false).getInstances())
        .containsExactly("false", "true", "true");
    assertThat(
        context.renderExpressionsForInstanceDetails("${instanceDetails.pcf.applicationName}", false).getInstances())
        .containsExactly("pcf_0", "pcf_1", "pcf_1");
    assertThat(
        context.renderExpressionsForInstanceDetails("${instanceDetails.pcf.applicationId}", false).getInstances())
        .containsExactly("id0", "id1", "id1");
    assertThat(context.renderExpressionsForInstanceDetails("${pcfinstance.applicationId}", false).getInstances())
        .containsExactly("id0", "id1", "id1");

    // Only New Instances
    instanceDetails = sweepingOutputService.fetchInstanceDetailsFromSweepingOutput(sweepingOutputInquiry, true);
    assertThat(instanceDetails.size()).isEqualTo(2);
    instanceElements = sweepingOutputService.fetchInstanceElementsFromSweepingOutput(sweepingOutputInquiry, true);
    assertThat(instanceElements.size()).isEqualTo(2);

    assertThat(context.renderExpressionsForInstanceDetails("${instance.hostName}", true).getInstances())
        .containsExactly("pcf_1:1", "pcf_1:2");
    assertThat(context.renderExpressionsForInstanceDetails("${instance.newInstance}", true).getInstances())
        .containsExactly("true", "true");
    assertThat(context.renderExpressionsForInstanceDetails("${host.hostName}", true).getInstances())
        .containsExactly("pcf_1:1", "pcf_1:2");
    assertThat(context.renderExpressionsForInstanceDetails("${instanceDetails.hostName}", true).getInstances())
        .containsExactly("pcf_1:1", "pcf_1:2");
    assertThat(context.renderExpressionsForInstanceDetails("${instanceDetails.newInstance}", true).getInstances())
        .containsExactly("true", "true");
    assertThat(
        context.renderExpressionsForInstanceDetails("${instanceDetails.pcf.applicationName}", true).getInstances())
        .containsExactly("pcf_1", "pcf_1");
    assertThat(context.renderExpressionsForInstanceDetails("${instanceDetails.pcf.applicationId}", true).getInstances())
        .containsExactly("id1", "id1");

    assertThat(context.renderExpressionsForInstanceDetails("${pcfinstance.applicationId}", true).getInstances())
        .containsExactly("id1", "id1");
  }

  @Test
  @Owner(developers = KAMAL)
  @Category(UnitTests.class)
  public void testFindInstanceDetailsForWorkflowExecution() {
    sweepingOutputService.cleanForStateExecutionInstance(stateExecutionInstance);

    List<PcfInstanceElement> pcfInstanceElements = Arrays.asList(PcfInstanceElement.builder()
                                                                     .isUpsize(false)
                                                                     .displayName("pcf_0")
                                                                     .instanceIndex("0")
                                                                     .applicationId("id0")
                                                                     .build(),
        PcfInstanceElement.builder()
            .isUpsize(true)
            .displayName("pcf_1")
            .instanceIndex("1")
            .applicationId("id1")
            .build());

    VariableProcessor variableProcessor = mock(VariableProcessor.class);
    doReturn(Collections.emptyMap()).when(variableProcessor).getVariables(any(), anyString());
    ExecutionContext context = generateExecutionContext();
    on(context).set("variableProcessor", variableProcessor);
    on(context).set("evaluator", new ManagerExpressionEvaluator());
    on(context).set("sweepingOutputService", sweepingOutputService);

    saveSweepingOutput(InstanceInfoVariables.builder()
                           .instanceDetails(pcfStateHelper.generateInstanceDetails(pcfInstanceElements))
                           .build());
    saveSweepingOutput(InstanceInfoVariables.builder()
                           .instanceDetails(pcfStateHelper.generateInstanceDetails(pcfInstanceElements))
                           .newInstanceTrafficPercent(10)
                           .build());

    List<InstanceDetails> instanceDetails =
        sweepingOutputService.findInstanceDetailsForWorkflowExecution(appId, workflowExecutionUuid);

    assertThat(instanceDetails.size()).isEqualTo(2);
    for (int i = 0; i < pcfInstanceElements.size(); i++) {
      PcfInstanceElement pcfInstanceElement = pcfInstanceElements.get(i);
      assertThat(instanceDetails.get(i).getHostName())
          .isEqualTo(pcfInstanceElement.getDisplayName() + ":" + pcfInstanceElement.getInstanceIndex());
      assertThat(instanceDetails.get(i).getInstanceType()).isEqualTo(InstanceDetails.InstanceType.PCF);
    }
  }

  private void saveSweepingOutput(InstanceInfoVariables instanceInfoVariables) {
    sweepingOutputService.save(SweepingOutputInstance.builder()
                                   .phaseExecutionId(workflowExecutionUuid + phaseElementId + phaseName)
                                   .stateExecutionId(stateExecutionInstanceId)
                                   .workflowExecutionId(workflowExecutionUuid)
                                   .name(InstanceInfoVariables.SWEEPING_OUTPUT_NAME)
                                   .appId(appId)
                                   .value(instanceInfoVariables)
                                   .build());
  }

  private ExecutionContext generateExecutionContext() {
    WorkflowStandardParams workflowStandardParams = spy(WorkflowStandardParams.class);
    doReturn(appId).when(workflowStandardParams).getAppId();

    Application app = new Application();
    app.setAccountId(ACCOUNT_ID);

    WorkflowStandardParamsExtensionService workflowStandardParamsExtensionService =
        spy(new WorkflowStandardParamsExtensionService(null, null, null, null, null, null, null));
    doReturn(app).when(workflowStandardParamsExtensionService).getApp(workflowStandardParams);
    doReturn(null).when(workflowStandardParamsExtensionService).getEnv(workflowStandardParams);

    // mock paramMapper and factory
    ContextElementParamMapper workflowStandardParamsMapper = spy(ContextElementParamMapper.class);
    doReturn(ImmutableMap.<String, Object>builder()
                 .put(ContextElement.APP,
                     anApplication().uuid(appId).appId(appId).accountId(ACCOUNT_ID).name(APP_NAME).build())
                 .put(ContextElement.ENV,
                     Environment.Builder.anEnvironment()
                         .uuid(ENV_ID)
                         .appId(appId)
                         .accountId(ACCOUNT_ID)
                         .name(ENV_NAME)
                         .build())
                 .build())
        .when(workflowStandardParamsMapper)
        .paramMap(any());

    ContextElementParamMapperFactory contextElementParamMapperFactory = spy(new ContextElementParamMapperFactory(
        null, null, null, null, null, null, null, workflowStandardParamsExtensionService));
    when(contextElementParamMapperFactory.getParamMapper(any())).thenReturn(new NoopContextElementParamMapper());
    when(contextElementParamMapperFactory.getParamMapper(isA(InstanceElement.class)))
        .thenAnswer(
            invocationOnMock -> new InstanceElementParamMapper((InstanceElement) invocationOnMock.getArguments()[0]));
    when(contextElementParamMapperFactory.getParamMapper(workflowStandardParams))
        .thenReturn(workflowStandardParamsMapper);

    StateExecutionInstance stateExecutionInstance = new StateExecutionInstance();
    stateExecutionInstance.setDisplayName("name");
    stateExecutionInstance.setUuid(stateExecutionInstanceId);
    stateExecutionInstance.setExecutionUuid(workflowExecutionUuid);
    stateExecutionInstance.getContextElements().add(workflowStandardParams);
    stateExecutionInstance.getContextElements().add(
        PhaseElement.builder().appId(appId).uuid(phaseElementId).phaseName(phaseName).build());
    ExecutionContextImpl context = new ExecutionContextImpl(stateExecutionInstance);
    on(context).set("contextElementParamMapperFactory", contextElementParamMapperFactory);
    on(context).set("workflowStandardParamsExtensionService", workflowStandardParamsExtensionService);
    FeatureFlagService ffService = mock(FeatureFlagService.class);
    on(context).set("featureFlagService", ffService);

    return context;
  }
}
