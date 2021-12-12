package io.harness.pms.instrumentaion;

import static io.harness.instrumentation.ServiceInstrumentationConstants.ACTIVE_SERVICES_COUNT_EVENT;
import static io.harness.instrumentation.ServiceInstrumentationConstants.SERVICE_USED_EVENT;
import static io.harness.rule.OwnerRule.BRIJESH;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyLong;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.harness.CategoryTest;
import io.harness.account.services.AccountService;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.dto.FailureInfoDTO;
import io.harness.engine.executions.plan.PlanExecutionService;
import io.harness.eraro.ResponseMessage;
import io.harness.exception.FailureType;
import io.harness.execution.PlanExecution;
import io.harness.instrumentation.ServiceInstrumentationConstants;
import io.harness.ng.core.dto.AccountDTO;
import io.harness.notification.PipelineEventType;
import io.harness.notification.bean.NotificationRules;
import io.harness.notification.bean.PipelineEvent;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.plan.ExecutionMetadata;
import io.harness.pms.contracts.plan.ExecutionTriggerInfo;
import io.harness.pms.contracts.plan.TriggerType;
import io.harness.pms.contracts.plan.TriggeredBy;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.execution.ExecutionStatus;
import io.harness.pms.notification.NotificationInstrumentationHelper;
import io.harness.pms.plan.execution.SetupAbstractionKeys;
import io.harness.pms.plan.execution.beans.PipelineExecutionSummaryEntity;
import io.harness.pms.plan.execution.beans.dto.GraphLayoutNodeDTO;
import io.harness.pms.plan.execution.service.PMSExecutionService;
import io.harness.rule.Owner;
import io.harness.telemetry.TelemetryReporter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.bson.Document;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@OwnedBy(HarnessTeam.PIPELINE)
public class InstrumentationPipelineEndEventHandlerTest extends CategoryTest {
  @Mock TelemetryReporter telemetryReporter;
  @Mock PMSExecutionService pmsExecutionService;
  @Mock NotificationInstrumentationHelper notificationInstrumentationHelper;
  @Mock AccountService accountService;
  @Mock PlanExecutionService planExecutionService;
  @InjectMocks InstrumentationPipelineEndEventHandler instrumentationPipelineEndEventHandler;
  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void testOnEnd() {
    Ambiance ambiance =
        Ambiance.newBuilder()
            .setPlanExecutionId("planExecutionId")
            .putSetupAbstractions(SetupAbstractionKeys.accountId, "accountId")
            .putSetupAbstractions(SetupAbstractionKeys.orgIdentifier, "orgId")
            .putSetupAbstractions(SetupAbstractionKeys.projectIdentifier, "projectId")
            .setMetadata(ExecutionMetadata.newBuilder()
                             .setPipelineIdentifier("pipelineTestId")
                             .setTriggerInfo(ExecutionTriggerInfo.newBuilder()
                                                 .setTriggeredBy(TriggeredBy.newBuilder()
                                                                     .setIdentifier("admin")
                                                                     .putExtraInfo("email", "admin@harness.io")
                                                                     .build())
                                                 .build())
                             .build())
            .build();

    List serviceIdentifiers = new ArrayList();
    serviceIdentifiers.add("TestVirtualService1_instance1");
    serviceIdentifiers.add("TestVirtualService1_instance2");
    serviceIdentifiers.add("TestVirtualService1_instance3");
    serviceIdentifiers.add("TestVirtualService1_instance1");
    Document document = new Document();
    document.put("serviceIdentifiers", serviceIdentifiers);
    Map<String, Document> moduleInfo = new HashMap<>();
    moduleInfo.put("cd", document);

    PipelineExecutionSummaryEntity pipelineExecutionSummaryEntity =
        PipelineExecutionSummaryEntity.builder()
            .pipelineIdentifier("pipelineTestId")
            .layoutNodeMap(
                Collections.singletonMap("stage1", GraphLayoutNodeDTO.builder().status(ExecutionStatus.FAILED).build()))
            .executionTriggerInfo(
                ExecutionTriggerInfo.newBuilder().setTriggerType(TriggerType.MANUAL).setIsRerun(false).build())
            .failureInfo(
                FailureInfoDTO.builder()
                    .responseMessages(Collections.singletonList(ResponseMessage.builder().message("message").build()))
                    .failureTypeList(EnumSet.of(FailureType.AUTHENTICATION))
                    .build())
            .startTs(1000L)
            .endTs(2000L)
            .status(ExecutionStatus.FAILED)
            .moduleInfo(moduleInfo)
            .build();
    List<NotificationRules> notificationRulesList = Collections.singletonList(
        NotificationRules.builder()
            .pipelineEvents(
                Collections.singletonList(PipelineEvent.builder().type(PipelineEventType.PIPELINE_END).build()))
            .build());
    Set<String> notificationMethods = Collections.singleton("slack");
    doReturn(pipelineExecutionSummaryEntity)
        .when(pmsExecutionService)
        .getPipelineExecutionSummaryEntity("accountId", "orgId", "projectId", "planExecutionId", false);
    doReturn(notificationRulesList)
        .when(notificationInstrumentationHelper)
        .getNotificationRules("planExecutionId", ambiance);
    doReturn(notificationMethods)
        .when(notificationInstrumentationHelper)
        .getNotificationMethodTypes(notificationRulesList);

    AccountDTO accountDTO = AccountDTO.builder().name("TestAccountName").build();
    doReturn(accountDTO)
            .when(accountService)
            .getAccount(any());

    List<PlanExecution> planExecutionList = new ArrayList<>();
    Map<String, String> planExecution1 = new HashMap<>();
    planExecution1.put(SetupAbstractionKeys.accountId, "TestPlanExecution1Id");
    planExecution1.put(SetupAbstractionKeys.orgIdentifier, "TestOrganizationId");
    planExecution1.put(SetupAbstractionKeys.projectIdentifier, "TestProjectId");

    Map<String, String> planExecution2 = new HashMap<>();
    planExecution2.put(SetupAbstractionKeys.accountId, "TestPlanExecution2Id");
    planExecution2.put(SetupAbstractionKeys.orgIdentifier, "TestOrganizationId");
    planExecution2.put(SetupAbstractionKeys.projectIdentifier, "TestProjectId");

    planExecutionList.add(PlanExecution.builder()
            .uuid("qwertyuiop11111")
            .setupAbstractions(planExecution1)
            .build());
    planExecutionList.add(PlanExecution.builder()
            .uuid("qwertyuiop22222")
            .setupAbstractions(planExecution2)
            .build());
    doReturn(planExecutionList)
            .when(planExecutionService)
            .findAllByAccountIdAndOrgIdAndProjectIdAndLastUpdatedAtInBetweenTimestamps(any(), any(), any(), anyLong(), anyLong());


    List serviceIdentifiers1 = new ArrayList();
    serviceIdentifiers1.add("TestVirtualService1_instance1");
    serviceIdentifiers1.add("TestVirtualService1_instance2");
    Document document1 = new Document();
    document1.put("serviceIdentifiers", serviceIdentifiers1);
    Map<String, Document> moduleInfo1 = new HashMap<>();
    moduleInfo1.put("cd", document1);
    PipelineExecutionSummaryEntity pipelineExecutionSummaryEntity1 = PipelineExecutionSummaryEntity.builder()
            .moduleInfo(moduleInfo1)
            .pipelineIdentifier("TestPipelineA")
            .build();

    doReturn(pipelineExecutionSummaryEntity1)
            .when(pmsExecutionService)
            .getPipelineExecutionSummaryEntity("TestPlanExecution1Id", "TestOrganizationId", "TestProjectId", "qwertyuiop11111");

    List serviceIdentifiers2 = new ArrayList();
    serviceIdentifiers2.add("TestVirtualService2_instance1");
    Document document2 = new Document();
    document2.put("serviceIdentifiers", serviceIdentifiers2);
    Map<String, Document> moduleInfo2 = new HashMap<>();
    moduleInfo2.put("cd", document2);
    PipelineExecutionSummaryEntity pipelineExecutionSummaryEntity2 = PipelineExecutionSummaryEntity.builder()
            .moduleInfo(moduleInfo2)
            .pipelineIdentifier("TestPipelineB")
            .build();
    doReturn(pipelineExecutionSummaryEntity2)
            .when(pmsExecutionService)
            .getPipelineExecutionSummaryEntity("TestPlanExecution2Id", "TestOrganizationId", "TestProjectId", "qwertyuiop22222");

    instrumentationPipelineEndEventHandler.onEnd(ambiance);

    ArgumentCaptor<HashMap> argumentCaptor = ArgumentCaptor.forClass(HashMap.class);
    verify(telemetryReporter, times(1))
        .sendTrackEvent(eq(PipelineInstrumentationConstants.PIPELINE_EXECUTION), eq("admin@harness.io"),
            eq("accountId"), argumentCaptor.capture(), any(), any(), any());
    HashMap<String, Object> propertiesMap = argumentCaptor.getValue();
    EnumSet<io.harness.pms.contracts.execution.failure.FailureType> returnedFailureTypes =
        (EnumSet<io.harness.pms.contracts.execution.failure.FailureType>) propertiesMap.get(
            PipelineInstrumentationConstants.FAILURE_TYPES);
    List<String> returnedErrorMessages =
        (List<String>) propertiesMap.get(PipelineInstrumentationConstants.ERROR_MESSAGES);
    Set<String> returnedNotificationMethods =
        (Set<String>) propertiesMap.get(PipelineInstrumentationConstants.NOTIFICATION_METHODS);
    assertEquals(propertiesMap.get(PipelineInstrumentationConstants.LEVEL), StepCategory.PIPELINE);
    assertEquals(propertiesMap.get(PipelineInstrumentationConstants.STATUS), ExecutionStatus.FAILED);
    assertEquals(propertiesMap.get(PipelineInstrumentationConstants.EXECUTION_TIME), 1L);
    assertEquals(propertiesMap.get(PipelineInstrumentationConstants.ACCOUNT_NAME), "TestAccountName");
    assertEquals(returnedNotificationMethods, notificationMethods);
    assertTrue(returnedFailureTypes.contains(FailureType.AUTHENTICATION));
    assertEquals(returnedErrorMessages.get(0), "message");

    verify(telemetryReporter, times(1))
        .sendTrackEvent(eq(PipelineInstrumentationConstants.PIPELINE_NOTIFICATION), eq("admin@harness.io"),
            eq("accountId"), argumentCaptor.capture(), any(), any(), any());
    propertiesMap = argumentCaptor.getValue();
    Set<String> eventTypes = (Set<String>) propertiesMap.get(PipelineInstrumentationConstants.EVENT_TYPES);
    assertEquals(propertiesMap.get(PipelineInstrumentationConstants.ACCOUNT_NAME), "TestAccountName");

    assertEquals(eventTypes.size(), 1);
    assertTrue(eventTypes.contains(PipelineEventType.PIPELINE_END));

    verify(telemetryReporter, times(3))
            .sendTrackEvent(eq(SERVICE_USED_EVENT), any(),
                    eq("accountId"), any(), any(), any(), any());

    verify(telemetryReporter, times(1))
            .sendTrackEvent(eq(ACTIVE_SERVICES_COUNT_EVENT), any(),
                    eq("accountId"), argumentCaptor.capture(), any(), any(), any());
    propertiesMap = argumentCaptor.getValue();
    assertEquals(propertiesMap.get(ServiceInstrumentationConstants.ACTIVE_SERVICES_ACCOUNT_NAME), "TestAccountName");
    assertEquals(propertiesMap.get(ServiceInstrumentationConstants.ACTIVE_SERVICES_PIPELINE_ID), "pipelineTestId");
    assertEquals(propertiesMap.size(), 6);
  }
}
