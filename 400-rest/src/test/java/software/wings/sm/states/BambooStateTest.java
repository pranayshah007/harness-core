/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.sm.states;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.rule.OwnerRule.MILOS;
import static io.harness.rule.OwnerRule.SRINIVAS;

import static software.wings.beans.Application.Builder.anApplication;
import static software.wings.beans.Environment.Builder.anEnvironment;
import static software.wings.beans.TaskType.BAMBOO;
import static software.wings.utils.WingsTestConstants.ACCOUNT_ID;
import static software.wings.utils.WingsTestConstants.ACTIVITY_ID;
import static software.wings.utils.WingsTestConstants.APP_ID;
import static software.wings.utils.WingsTestConstants.ENV_ID;
import static software.wings.utils.WingsTestConstants.SETTING_ID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.beans.DelegateTask;
import io.harness.beans.EmbeddedUser;
import io.harness.beans.ExecutionStatus;
import io.harness.category.element.UnitTests;
import io.harness.context.ContextElementType;
import io.harness.delegate.beans.DelegateTaskDetails;
import io.harness.delegate.beans.TaskData;
import io.harness.delegate.utils.DelegateTaskMigrationHelper;
import io.harness.rule.Owner;

import software.wings.api.BambooExecutionData;
import software.wings.api.JenkinsExecutionData;
import software.wings.beans.Activity;
import software.wings.beans.BambooConfig;
import software.wings.delegatetasks.BambooTask.BambooExecutionResponse;
import software.wings.service.impl.SettingServiceHelper;
import software.wings.service.intfc.ActivityService;
import software.wings.service.intfc.DelegateService;
import software.wings.service.intfc.InfrastructureMappingService;
import software.wings.service.intfc.StateExecutionService;
import software.wings.service.intfc.security.SecretManager;
import software.wings.sm.ExecutionContextImpl;
import software.wings.sm.ExecutionResponse;
import software.wings.sm.WorkflowStandardParams;
import software.wings.sm.WorkflowStandardParamsExtensionService;

import com.google.common.collect.ImmutableMap;
import java.util.Collections;
import java.util.concurrent.TimeUnit;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

@OwnedBy(CDC)
@TargetModule(HarnessModule._870_CG_ORCHESTRATION)
public class BambooStateTest extends CategoryTest {
  private static final Activity ACTIVITY_WITH_ID = Activity.builder().build();

  static {
    ACTIVITY_WITH_ID.setUuid(ACTIVITY_ID);
  }

  @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();

  @Mock private ActivityService activityService;
  @Mock private ExecutionContextImpl executionContext;
  @Mock private DelegateService delegateService;
  @Mock private SecretManager secretManager;
  @Mock private SettingServiceHelper settingServiceHelper;
  @Mock private InfrastructureMappingService infrastructureMappingService;
  @Mock private StateExecutionService stateExecutionService;
  @Mock private WorkflowStandardParamsExtensionService workflowStandardParamsExtensionService;
  @Mock private DelegateTaskMigrationHelper delegateTaskMigrationHelper;

  @InjectMocks private BambooState bambooState = new BambooState("bamboo");

  @Before
  public void setUp() throws Exception {
    bambooState.setBambooConfigId(SETTING_ID);
    bambooState.setPlanName("TOD-TOD");
    when(executionContext.getApp()).thenReturn(anApplication().accountId(ACCOUNT_ID).uuid(APP_ID).build());
    when(executionContext.getEnv()).thenReturn(anEnvironment().uuid(ENV_ID).appId(APP_ID).build());
    when(activityService.save(any(Activity.class))).thenReturn(ACTIVITY_WITH_ID);
    when(executionContext.getGlobalSettingValue(ACCOUNT_ID, SETTING_ID)).thenReturn(getBambooConfig(false));
    when(executionContext.renderExpression(anyString()))
        .thenAnswer(invocation -> invocation.getArgument(0, String.class));

    WorkflowStandardParams workflowStandardParams = WorkflowStandardParams.Builder.aWorkflowStandardParams().build();
    workflowStandardParams.setCurrentUser(EmbeddedUser.builder().name("test").email("test@harness.io").build());
    when(executionContext.getContextElement(ContextElementType.STANDARD)).thenReturn(workflowStandardParams);
  }

  @Test
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  public void shouldExecute() {
    ExecutionResponse executionResponse = bambooState.execute(executionContext);
    assertThat(executionResponse).isNotNull().hasFieldOrPropertyWithValue("async", true);
    ArgumentCaptor<DelegateTask> delegateTaskArgumentCaptor = ArgumentCaptor.forClass(DelegateTask.class);
    verify(delegateService).queueTaskV2(delegateTaskArgumentCaptor.capture());
    assertThat(delegateTaskArgumentCaptor.getValue())
        .isNotNull()
        .hasFieldOrPropertyWithValue("data.taskType", BAMBOO.name());
    assertThat(delegateTaskArgumentCaptor.getValue().isSelectionLogsTrackingEnabled()).isTrue();
    verify(stateExecutionService).appendDelegateTaskDetails(eq(null), any(DelegateTaskDetails.class));
  }

  @Test
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  public void shouldHandleAsyncResponse() {
    when(executionContext.getStateExecutionData()).thenReturn(BambooExecutionData.builder().build());
    bambooState.handleAsyncResponse(executionContext,
        ImmutableMap.of(ACTIVITY_ID,
            BambooExecutionResponse.builder()
                .errorMessage("Err")
                .executionStatus(ExecutionStatus.FAILED)
                .buildStatus("SUCCESS")
                .planUrl("http://bamboo/TOD-TOD")
                .filePathAssertionMap(Collections.emptyList())
                .build()));

    verify(activityService).updateStatus(ACTIVITY_ID, APP_ID, ExecutionStatus.FAILED);
  }

  @Test
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  public void shouldGetTimeout() {
    Integer timeoutMillis = bambooState.getTimeoutMillis();
    assertThat(timeoutMillis).isEqualTo(Math.toIntExact(TaskData.DEFAULT_ASYNC_CALL_TIMEOUT));
  }

  @Test
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  public void shouldGetSetTimeout() {
    bambooState.setTimeoutMillis((int) TimeUnit.HOURS.toMillis(1));
    Integer timeoutMillis = bambooState.getTimeoutMillis();
    assertThat(timeoutMillis).isEqualTo((int) TimeUnit.HOURS.toMillis(1));
  }

  @Test
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  public void shouldHandleAbort() {
    when(executionContext.getStateExecutionData())
        .thenReturn(JenkinsExecutionData.builder().activityId(ACTIVITY_ID).build());
    bambooState.handleAbortEvent(executionContext);
    assertThat(executionContext.getStateExecutionData()).isNotNull();

    // TODO: getErrorMsg returns null - is this expected
    // assertThat(executionContext.getStateExecutionData().getErrorMsg()).isNotBlank();
  }

  @Test
  @Owner(developers = MILOS)
  @Category(UnitTests.class)
  public void shouldExecuteWithCertValidation() {
    when(executionContext.getGlobalSettingValue(ACCOUNT_ID, SETTING_ID)).thenReturn(getBambooConfig(true));
    ExecutionResponse executionResponse = bambooState.execute(executionContext);
    assertThat(executionResponse).isNotNull().hasFieldOrPropertyWithValue("async", true);
    ArgumentCaptor<DelegateTask> delegateTaskArgumentCaptor = ArgumentCaptor.forClass(DelegateTask.class);
    verify(delegateService).queueTaskV2(delegateTaskArgumentCaptor.capture());
    assertThat(delegateTaskArgumentCaptor.getValue())
        .isNotNull()
        .hasFieldOrPropertyWithValue("data.taskType", BAMBOO.name());
  }

  private BambooConfig getBambooConfig(boolean isCertvalidationRequired) {
    BambooConfig bambooConfig = BambooConfig.builder()
                                    .bambooUrl("http://bamboo")
                                    .username("username")
                                    .password("password".toCharArray())
                                    .accountId(ACCOUNT_ID)
                                    .build();

    bambooConfig.setCertValidationRequired(isCertvalidationRequired);
    return bambooConfig;
  }
}
