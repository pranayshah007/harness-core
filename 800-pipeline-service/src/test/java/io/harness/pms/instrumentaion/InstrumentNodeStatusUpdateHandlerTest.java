package io.harness.pms.instrumentaion;

import static io.harness.rule.OwnerRule.BRIJESH;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.harness.CategoryTest;
import io.harness.account.services.AccountService;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.engine.observers.NodeUpdateInfo;
import io.harness.execution.NodeExecution;
import io.harness.ng.core.dto.AccountDTO;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.execution.failure.FailureData;
import io.harness.pms.contracts.execution.failure.FailureInfo;
import io.harness.pms.contracts.execution.failure.FailureType;
import io.harness.pms.contracts.plan.ExecutionMetadata;
import io.harness.pms.contracts.plan.ExecutionTriggerInfo;
import io.harness.pms.contracts.plan.PlanNodeProto;
import io.harness.pms.contracts.plan.TriggeredBy;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.contracts.steps.StepType;
import io.harness.rule.Owner;
import io.harness.telemetry.TelemetryReporter;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@OwnedBy(HarnessTeam.PIPELINE)
public class InstrumentNodeStatusUpdateHandlerTest extends CategoryTest {
  @Mock TelemetryReporter telemetryReporter;
  @InjectMocks InstrumentNodeStatusUpdateHandler instrumentNodeStatusUpdateHandler;
  @Mock AccountService accountService;

  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void testOnNodeStatusUpdate() {
    // identity should be admin
    Ambiance ambiance =
        Ambiance.newBuilder()
            .putSetupAbstractions("accountId", "accountId")
            .setMetadata(
                ExecutionMetadata.newBuilder()
                    .setTriggerInfo(
                        ExecutionTriggerInfo.newBuilder()
                            .setTriggeredBy(
                                TriggeredBy.newBuilder().setIdentifier("admin").putExtraInfo("email", "").build())
                            .build())
                    .build())
            .build();
    NodeExecution nodeExecution =
        NodeExecution.builder()
            .status(Status.SUCCEEDED)
            .node(PlanNodeProto.newBuilder()
                      .setStepType(StepType.newBuilder().setStepCategory(StepCategory.STAGE).build())
                      .build())
            .failureInfo(FailureInfo.newBuilder().build())
            .ambiance(ambiance)
            .startTs(1000L)
            .build();

    AccountDTO accountDTO = AccountDTO.builder().name("TestAccountName").build();
    doReturn(accountDTO).when(accountService).getAccount(any());

    NodeUpdateInfo nodeUpdateInfo = NodeUpdateInfo.builder().nodeExecution(nodeExecution).updatedTs(2000L).build();
    instrumentNodeStatusUpdateHandler.onNodeStatusUpdate(nodeUpdateInfo);
    ArgumentCaptor<HashMap> argumentCaptor = ArgumentCaptor.forClass(HashMap.class);
    verify(telemetryReporter, times(1))
        .sendTrackEvent(any(), eq("admin"), eq("accountId"), argumentCaptor.capture(), any(), any(), any());
    HashMap<String, Object> propertiesMap = argumentCaptor.getValue();
    EnumSet<FailureType> returnedFailureTypes =
        (EnumSet<FailureType>) propertiesMap.get(PipelineInstrumentationConstants.FAILURE_TYPES);
    List<String> returnedErrorMessages =
        (List<String>) propertiesMap.get(PipelineInstrumentationConstants.ERROR_MESSAGES);

    assertEquals(propertiesMap.get(PipelineInstrumentationConstants.LEVEL), StepCategory.STAGE);
    assertEquals(propertiesMap.get(PipelineInstrumentationConstants.STATUS), Status.SUCCEEDED);
    assertEquals(propertiesMap.get(PipelineInstrumentationConstants.EXECUTION_TIME), 1L);
    assertTrue(returnedFailureTypes.isEmpty());
    assertTrue(returnedErrorMessages.isEmpty());

    // identity should be admin@harness.io
    ambiance = Ambiance.newBuilder()
                   .putSetupAbstractions("accountId", "accountId")
                   .setMetadata(ExecutionMetadata.newBuilder()
                                    .setTriggerInfo(ExecutionTriggerInfo.newBuilder()
                                                        .setTriggeredBy(TriggeredBy.newBuilder()
                                                                            .setIdentifier("admin")
                                                                            .putExtraInfo("email", "admin@harness.io")
                                                                            .build())
                                                        .build())
                                    .build())
                   .build();

    EnumSet<FailureType> failureTypes = EnumSet.of(FailureType.AUTHENTICATION_FAILURE);
    nodeExecution =
        NodeExecution.builder()
            .status(Status.FAILED)
            .node(PlanNodeProto.newBuilder()
                      .setStepType(StepType.newBuilder().setStepCategory(StepCategory.STAGE).build())
                      .build())
            .failureInfo(
                FailureInfo.newBuilder()
                    .addFailureData(
                        FailureData.newBuilder().setMessage("message").addAllFailureTypes(failureTypes).build())
                    .build())
            .ambiance(ambiance)
            .startTs(1000L)
            .build();

    nodeUpdateInfo = NodeUpdateInfo.builder().nodeExecution(nodeExecution).updatedTs(2000L).build();

    instrumentNodeStatusUpdateHandler.onNodeStatusUpdate(nodeUpdateInfo);
    argumentCaptor = ArgumentCaptor.forClass(HashMap.class);
    verify(telemetryReporter, times(1))
        .sendTrackEvent(any(), eq("admin@harness.io"), eq("accountId"), argumentCaptor.capture(), any(), any(), any());
    propertiesMap = argumentCaptor.getValue();

    returnedFailureTypes = (EnumSet<FailureType>) propertiesMap.get(PipelineInstrumentationConstants.FAILURE_TYPES);
    returnedErrorMessages = (List<String>) propertiesMap.get(PipelineInstrumentationConstants.ERROR_MESSAGES);

    assertEquals(propertiesMap.get(PipelineInstrumentationConstants.LEVEL), StepCategory.STAGE);
    assertEquals(propertiesMap.get(PipelineInstrumentationConstants.STATUS), Status.FAILED);
    assertTrue(returnedFailureTypes.contains(io.harness.exception.FailureType.AUTHENTICATION));
    assertEquals(returnedErrorMessages.get(0), "message");
    assertEquals(propertiesMap.get(PipelineInstrumentationConstants.EXECUTION_TIME), 1L);
  }
}
