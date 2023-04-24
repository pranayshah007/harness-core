/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service;

import static io.harness.beans.EnvironmentType.PROD;
import static io.harness.beans.PageRequest.PageRequestBuilder.aPageRequest;
import static io.harness.rule.OwnerRule.GARVIT;
import static io.harness.rule.OwnerRule.GEORGE;
import static io.harness.rule.OwnerRule.RAGHU;

import static software.wings.beans.command.CleanupSshCommandUnit.CLEANUP_UNIT;
import static software.wings.beans.command.CommandUnitDetails.CommandUnitType.COMMAND;
import static software.wings.beans.command.CommandUnitType.EXEC;
import static software.wings.beans.command.ExecCommandUnit.Builder.anExecCommandUnit;
import static software.wings.beans.command.InitSshCommandUnit.INITIALIZE_UNIT;
import static software.wings.utils.WingsTestConstants.ACCOUNT_ID;
import static software.wings.utils.WingsTestConstants.ACTIVITY_ID;
import static software.wings.utils.WingsTestConstants.APP_ID;
import static software.wings.utils.WingsTestConstants.APP_NAME;
import static software.wings.utils.WingsTestConstants.ARTIFACT_ID;
import static software.wings.utils.WingsTestConstants.ARTIFACT_NAME;
import static software.wings.utils.WingsTestConstants.ARTIFACT_STREAM_NAME;
import static software.wings.utils.WingsTestConstants.COMMAND_NAME;
import static software.wings.utils.WingsTestConstants.COMMAND_UNIT_NAME;
import static software.wings.utils.WingsTestConstants.ENV_ID;
import static software.wings.utils.WingsTestConstants.ENV_NAME;
import static software.wings.utils.WingsTestConstants.HOST_NAME;
import static software.wings.utils.WingsTestConstants.SERVICE_ID;
import static software.wings.utils.WingsTestConstants.SERVICE_INSTANCE_ID;
import static software.wings.utils.WingsTestConstants.SERVICE_NAME;
import static software.wings.utils.WingsTestConstants.TEMPLATE_ID;
import static software.wings.utils.WingsTestConstants.TEMPLATE_NAME;
import static software.wings.utils.WingsTestConstants.WORKFLOW_ID;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.harness.beans.ExecutionStatus;
import io.harness.beans.SearchFilter.Operator;
import io.harness.beans.TriggeredBy;
import io.harness.beans.WorkflowType;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;

import software.wings.WingsBaseTest;
import software.wings.beans.Activity;
import software.wings.beans.Activity.ActivityKeys;
import software.wings.beans.command.CleanupSshCommandUnit;
import software.wings.beans.command.CommandUnit;
import software.wings.beans.command.CommandUnitDetails;
import software.wings.beans.command.CommandUnitType;
import software.wings.beans.command.InitSshCommandUnit;
import software.wings.dl.WingsPersistence;
import software.wings.service.intfc.ActivityService;
import software.wings.service.intfc.AppService;
import software.wings.service.intfc.LogService;
import software.wings.service.intfc.ServiceInstanceService;

import com.google.inject.Inject;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;

/**
 * Created by peeyushaggarwal on 5/27/16.
 */
public class ActivityServiceTest extends WingsBaseTest {
  @Inject private WingsPersistence wingsPersistence;

  @Mock private ServiceInstanceService serviceInstanceService;
  @Mock private LogService logService;

  @Mock private AppService appService;
  @Inject @InjectMocks private ActivityService activityService;

  @Before
  public void setup() {
    doReturn(ACCOUNT_ID).when(appService).getAccountIdByAppId(APP_ID);
  }

  /**
   * Should list activities.
   */
  @Test
  @Owner(developers = RAGHU)
  @Category(UnitTests.class)
  public void shouldListActivities() {
    Activity activity = Activity.builder()
                            .environmentId(ENV_ID)
                            .environmentName(ENV_NAME)
                            .environmentType(PROD)
                            .applicationName(APP_NAME)
                            .artifactId(ARTIFACT_ID)
                            .artifactName(ARTIFACT_NAME)
                            .commandName(COMMAND_NAME)
                            .commandType(EXEC.name())
                            .hostName(HOST_NAME)
                            .artifactStreamId(ARTIFACT_STREAM_NAME)
                            .serviceName(SERVICE_NAME)
                            .serviceId(SERVICE_ID)
                            .serviceTemplateName(TEMPLATE_NAME)
                            .serviceTemplateId(TEMPLATE_ID)
                            .status(ExecutionStatus.RUNNING)
                            .workflowExecutionId("WORKFLOW_ID")
                            .workflowExecutionName("Workflow 1")
                            .workflowType(WorkflowType.ORCHESTRATION)
                            .stateExecutionInstanceId("STATE_ID")
                            .stateExecutionInstanceName("STATE")
                            .workflowId(WORKFLOW_ID)
                            .serviceInstanceId(SERVICE_INSTANCE_ID)
                            .commandUnits(Collections.emptyList())
                            .triggeredBy(TriggeredBy.builder().name("test").email("email@test.com").build())
                            .build();
    activity.setAppId(APP_ID);
    wingsPersistence.save(activity);
    assertThat(activityService.list(aPageRequest().addFilter(ActivityKeys.appId, Operator.EQ, APP_ID).build()))
        .hasSize(1)
        .containsExactly(activity);
  }

  /**
   * Should get activity.
   */
  @Test
  @Owner(developers = RAGHU)
  @Category(UnitTests.class)
  public void shouldGetActivity() {
    Activity activity = Activity.builder()
                            .environmentId(ENV_ID)
                            .environmentName(ENV_NAME)
                            .environmentType(PROD)
                            .applicationName(APP_NAME)
                            .artifactId(ARTIFACT_ID)
                            .artifactName(ARTIFACT_NAME)
                            .commandName(COMMAND_NAME)
                            .commandType(EXEC.name())
                            .hostName(HOST_NAME)
                            .artifactStreamId(ARTIFACT_STREAM_NAME)
                            .serviceName(SERVICE_NAME)
                            .serviceId(SERVICE_ID)
                            .serviceTemplateName(TEMPLATE_NAME)
                            .serviceTemplateId(TEMPLATE_ID)
                            .status(ExecutionStatus.RUNNING)
                            .workflowExecutionId("WORKFLOW_ID")
                            .workflowExecutionName("Workflow 1")
                            .workflowType(WorkflowType.ORCHESTRATION)
                            .stateExecutionInstanceId("STATE_ID")
                            .stateExecutionInstanceName("STATE")
                            .workflowId(WORKFLOW_ID)
                            .serviceInstanceId(SERVICE_INSTANCE_ID)
                            .commandUnits(Collections.emptyList())
                            .triggeredBy(TriggeredBy.builder().name("test").email("email@test.com").build())
                            .build();
    activity.setAppId(APP_ID);
    wingsPersistence.save(activity);
    assertThat(activityService.get(activity.getUuid(), activity.getAppId())).isEqualTo(activity);
  }

  /**
   * Should save activity.
   */
  @Test
  @Owner(developers = RAGHU)
  @Category(UnitTests.class)
  public void shouldSaveActivity() {
    Activity activity = Activity.builder()
                            .environmentId(ENV_ID)
                            .environmentName(ENV_NAME)
                            .environmentType(PROD)
                            .applicationName(APP_NAME)
                            .artifactId(ARTIFACT_ID)
                            .artifactName(ARTIFACT_NAME)
                            .commandName(COMMAND_NAME)
                            .commandType(EXEC.name())
                            .hostName(HOST_NAME)
                            .artifactStreamId(ARTIFACT_STREAM_NAME)
                            .serviceName(SERVICE_NAME)
                            .serviceId(SERVICE_ID)
                            .serviceTemplateName(TEMPLATE_NAME)
                            .serviceTemplateId(TEMPLATE_ID)
                            .status(ExecutionStatus.RUNNING)
                            .workflowExecutionId("WORKFLOW_ID")
                            .workflowExecutionName("Workflow 1")
                            .workflowType(WorkflowType.ORCHESTRATION)
                            .stateExecutionInstanceId("STATE_ID")
                            .stateExecutionInstanceName("STATE")
                            .workflowId(WORKFLOW_ID)
                            .serviceInstanceId(SERVICE_INSTANCE_ID)
                            .commandUnits(Collections.emptyList())
                            .commandUnits(Collections.emptyList())
                            .triggeredBy(TriggeredBy.builder().name("test").email("email@test.com").build())
                            .build();
    activity.setAppId(APP_ID);
    activityService.save(activity);
    assertThat(wingsPersistence.getWithAppId(Activity.class, activity.getAppId(), activity.getUuid()))
        .isEqualTo(activity);
    verify(serviceInstanceService).updateActivity(activity);
  }

  /**
   * Should get activity command units.
   */
  @Test
  @Owner(developers = RAGHU)
  @Category(UnitTests.class)
  public void shouldGetActivityCommandUnits() {
    List<CommandUnit> commandUnitList = asList(new InitSshCommandUnit(),
        anExecCommandUnit().withName(COMMAND_UNIT_NAME).withCommandString("./bin/start.sh").build(),
        new CleanupSshCommandUnit());
    Activity activity = Activity.builder()
                            .environmentId(ENV_ID)
                            .environmentName(ENV_NAME)
                            .environmentType(PROD)
                            .applicationName(APP_NAME)
                            .artifactId(ARTIFACT_ID)
                            .artifactName(ARTIFACT_NAME)
                            .commandName(COMMAND_NAME)
                            .commandType(EXEC.name())
                            .hostName(HOST_NAME)
                            .artifactStreamId(ARTIFACT_STREAM_NAME)
                            .serviceName(SERVICE_NAME)
                            .serviceId(SERVICE_ID)
                            .serviceTemplateName(TEMPLATE_NAME)
                            .serviceTemplateId(TEMPLATE_ID)
                            .status(ExecutionStatus.RUNNING)
                            .workflowExecutionId("WORKFLOW_ID")
                            .workflowExecutionName("Workflow 1")
                            .workflowType(WorkflowType.ORCHESTRATION)
                            .stateExecutionInstanceId("STATE_ID")
                            .stateExecutionInstanceName("STATE")
                            .workflowId(WORKFLOW_ID)
                            .serviceInstanceId(SERVICE_INSTANCE_ID)
                            .commandName(COMMAND_NAME)
                            .commandType(CommandUnitType.COMMAND.name())
                            .commandUnits(commandUnitList)
                            .triggeredBy(TriggeredBy.builder().name("test").email("email@test.com").build())
                            .build();
    activity.setAppId(APP_ID);

    String activityId = wingsPersistence.save(activity);
    List<CommandUnitDetails> commandUnits = activityService.getCommandUnits(APP_ID, activityId);
    assertThat(commandUnits)
        .hasSize(3)
        .extracting(CommandUnitDetails::getCommandUnitType, CommandUnitDetails::getName)
        .contains(tuple(COMMAND, INITIALIZE_UNIT), tuple(COMMAND, COMMAND_UNIT_NAME), tuple(COMMAND, CLEANUP_UNIT));
  }

  @Test
  @Owner(developers = GARVIT)
  @Category(UnitTests.class)
  public void testGetActivityCommandUnits() {
    Map<String, List<CommandUnitDetails>> commandUnitsMap =
        activityService.getCommandUnitsMapUsingSecondary(Collections.emptyList());
    assertThat(commandUnitsMap).isNotNull();
    assertThat(commandUnitsMap).isEmpty();

    commandUnitsMap = activityService.getCommandUnitsMapUsingSecondary(Collections.singletonList("random-0919"));
    assertThat(commandUnitsMap).isNotNull();
    assertThat(commandUnitsMap).isEmpty();

    List<CommandUnit> commandUnitList1 = asList(new InitSshCommandUnit(),
        anExecCommandUnit().withName(COMMAND_UNIT_NAME).withCommandString("./bin/start.sh").build());
    List<CommandUnit> commandUnitList2 = Collections.singletonList(new CleanupSshCommandUnit());
    Activity activity1 = Activity.builder().commandUnits(commandUnitList1).build();
    activity1.setAppId(APP_ID);
    Activity activity2 = Activity.builder().commandUnits(commandUnitList2).build();
    activity2.setAppId(APP_ID);

    String activityId1 = wingsPersistence.save(activity1);
    String activityId2 = wingsPersistence.save(activity2);

    commandUnitsMap = activityService.getCommandUnitsMapUsingSecondary(asList(activityId1, activityId2, "random-0919"));
    assertThat(commandUnitsMap).isNotEmpty();
    assertThat(commandUnitsMap.keySet()).containsExactlyInAnyOrder(activityId1, activityId2);
    assertThat(commandUnitsMap.get(activityId1))
        .hasSize(2)
        .extracting(CommandUnitDetails::getCommandUnitType, CommandUnitDetails::getName)
        .containsExactly(tuple(COMMAND, INITIALIZE_UNIT), tuple(COMMAND, COMMAND_UNIT_NAME));
    assertThat(commandUnitsMap.get(activityId2))
        .hasSize(1)
        .extracting(CommandUnitDetails::getCommandUnitType, CommandUnitDetails::getName)
        .containsExactly(tuple(COMMAND, CLEANUP_UNIT));
  }

  /**
   * Shouldget last activity for service.
   */
  @Test
  @Owner(developers = RAGHU)
  @Category(UnitTests.class)
  public void shouldGetLastActivityForService() {
    Activity activity = Activity.builder()
                            .environmentId(ENV_ID)
                            .environmentName(ENV_NAME)
                            .environmentType(PROD)
                            .applicationName(APP_NAME)
                            .artifactId(ARTIFACT_ID)
                            .artifactName(ARTIFACT_NAME)
                            .commandName(COMMAND_NAME)
                            .commandType(EXEC.name())
                            .hostName(HOST_NAME)
                            .artifactStreamId(ARTIFACT_STREAM_NAME)
                            .serviceName(SERVICE_NAME)
                            .serviceId(SERVICE_ID)
                            .serviceTemplateName(TEMPLATE_NAME)
                            .serviceTemplateId(TEMPLATE_ID)
                            .status(ExecutionStatus.RUNNING)
                            .workflowExecutionId("WORKFLOW_ID")
                            .workflowExecutionName("Workflow 1")
                            .workflowType(WorkflowType.ORCHESTRATION)
                            .stateExecutionInstanceId("STATE_ID")
                            .stateExecutionInstanceName("STATE")
                            .workflowId(WORKFLOW_ID)
                            .serviceInstanceId(SERVICE_INSTANCE_ID)
                            .commandUnits(Collections.emptyList())
                            .triggeredBy(TriggeredBy.builder().name("test").email("email@test.com").build())
                            .build();
    activity.setAppId(APP_ID);
    wingsPersistence.save(activity);
    Activity activityForService = activityService.getLastActivityForService(APP_ID, SERVICE_ID);
    assertThat(activityForService).isEqualTo(activity);
  }

  /**
   * Shouldget last production activity for service.
   */
  @Test
  @Owner(developers = RAGHU)
  @Category(UnitTests.class)
  public void shouldGetLastProductionActivityForService() {
    Activity activity = Activity.builder()
                            .environmentId(ENV_ID)
                            .environmentName(ENV_NAME)
                            .environmentType(PROD)
                            .applicationName(APP_NAME)
                            .artifactId(ARTIFACT_ID)
                            .artifactName(ARTIFACT_NAME)
                            .commandName(COMMAND_NAME)
                            .commandType(EXEC.name())
                            .hostName(HOST_NAME)
                            .artifactStreamId(ARTIFACT_STREAM_NAME)
                            .serviceName(SERVICE_NAME)
                            .serviceId(SERVICE_ID)
                            .serviceTemplateName(TEMPLATE_NAME)
                            .serviceTemplateId(TEMPLATE_ID)
                            .status(ExecutionStatus.RUNNING)
                            .workflowExecutionId("WORKFLOW_ID")
                            .workflowExecutionName("Workflow 1")
                            .workflowType(WorkflowType.ORCHESTRATION)
                            .stateExecutionInstanceId("STATE_ID")
                            .stateExecutionInstanceName("STATE")
                            .workflowId(WORKFLOW_ID)
                            .serviceInstanceId(SERVICE_INSTANCE_ID)
                            .commandUnits(Collections.emptyList())
                            .triggeredBy(TriggeredBy.builder().name("test").email("email@test.com").build())
                            .build();
    activity.setAppId(APP_ID);
    activity.setEnvironmentType(PROD);
    wingsPersistence.save(activity);
    Activity lastProductionActivityForService = activityService.getLastProductionActivityForService(APP_ID, SERVICE_ID);
    assertThat(lastProductionActivityForService).isEqualTo(activity);
  }

  /**
   * Should update activity status.
   */
  @Test
  @Owner(developers = RAGHU)
  @Category(UnitTests.class)
  public void shouldUpdateActivityStatus() {
    Activity activity = Activity.builder()
                            .environmentId(ENV_ID)
                            .environmentName(ENV_NAME)
                            .environmentType(PROD)
                            .applicationName(APP_NAME)
                            .artifactId(ARTIFACT_ID)
                            .artifactName(ARTIFACT_NAME)
                            .commandName(COMMAND_NAME)
                            .commandType(EXEC.name())
                            .hostName(HOST_NAME)
                            .artifactStreamId(ARTIFACT_STREAM_NAME)
                            .serviceName(SERVICE_NAME)
                            .serviceId(SERVICE_ID)
                            .serviceTemplateName(TEMPLATE_NAME)
                            .serviceTemplateId(TEMPLATE_ID)
                            .status(ExecutionStatus.RUNNING)
                            .workflowExecutionId("WORKFLOW_ID")
                            .workflowExecutionName("Workflow 1")
                            .workflowType(WorkflowType.ORCHESTRATION)
                            .stateExecutionInstanceId("STATE_ID")
                            .stateExecutionInstanceName("STATE")
                            .workflowId(WORKFLOW_ID)
                            .commandUnits(Collections.emptyList())
                            .serviceInstanceId(SERVICE_INSTANCE_ID)
                            .triggeredBy(TriggeredBy.builder().name("test").email("email@test.com").build())
                            .build();
    activity.setAppId(APP_ID);
    activity.setUuid(ACTIVITY_ID);
    activityService.save(activity);
    assertThat(wingsPersistence.getWithAppId(Activity.class, activity.getAppId(), activity.getUuid()))
        .isEqualTo(activity);

    activityService.updateStatus(activity.getUuid(), activity.getAppId(), ExecutionStatus.SUCCESS);

    activity.setStatus(ExecutionStatus.SUCCESS);
    verify(serviceInstanceService, times(2)).updateActivity(any());
  }

  @Test
  @Owner(developers = GEORGE)
  @Category(UnitTests.class)
  public void shouldPruneDescendingObjects() {
    activityService.pruneDescendingEntities(APP_ID, ACTIVITY_ID);
    InOrder inOrder = inOrder(logService);
    inOrder.verify(logService).pruneByActivity(APP_ID, ACTIVITY_ID);
  }
}
