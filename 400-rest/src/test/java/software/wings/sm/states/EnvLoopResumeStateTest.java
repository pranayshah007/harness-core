/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.sm.states;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.rule.OwnerRule.GARVIT;

import static software.wings.sm.StateExecutionInstance.Builder.aStateExecutionInstance;
import static software.wings.utils.WingsTestConstants.APP_ID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;

import software.wings.WingsBaseTest;
import software.wings.sm.ExecutionContextImpl;
import software.wings.sm.ExecutionResponse;
import software.wings.sm.StateExecutionInstance;
import software.wings.sm.StateExecutionInstanceHelper;
import software.wings.sm.resume.ResumeStateUtils;

import com.google.common.collect.ImmutableMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;

@OwnedBy(CDC)
@TargetModule(HarnessModule._870_CG_ORCHESTRATION)
public class EnvLoopResumeStateTest extends WingsBaseTest {
  @Mock private ResumeStateUtils resumeStateUtils;
  @Mock private StateExecutionInstanceHelper stateExecutionInstanceHelper;

  @InjectMocks private EnvLoopResumeState envLoopResumeState = new EnvLoopResumeState("ENV_LOOP_RESUME_STATE");

  private String prevStateExecutionId = generateUuid();
  private String prevPipelineExecutionId = generateUuid();
  private Map<String, String> workflowExecutionIdWithStateExecutionIds =
      ImmutableMap.of(generateUuid(), generateUuid(), generateUuid(), generateUuid());
  private String currStateExecutionId = generateUuid();
  private String currPipelineExecutionId = generateUuid();

  @Before
  public void setUp() {
    envLoopResumeState.setPrevPipelineExecutionId(prevPipelineExecutionId);
    envLoopResumeState.setPrevStateExecutionId(prevStateExecutionId);
    envLoopResumeState.setWorkflowExecutionIdWithStateExecutionIds(workflowExecutionIdWithStateExecutionIds);
  }

  @Test
  @Owner(developers = GARVIT)
  @Category(UnitTests.class)
  public void shouldExecute() {
    ExecutionContextImpl context = mock(ExecutionContextImpl.class);
    when(context.getAppId()).thenReturn(APP_ID);
    when(context.getStateExecutionInstanceId()).thenReturn(currStateExecutionId);
    StateExecutionInstance stateExecutionInstance = aStateExecutionInstance().uuid(currStateExecutionId).build();
    when(context.getStateExecutionInstance()).thenReturn(stateExecutionInstance);
    when(stateExecutionInstanceHelper.clone(stateExecutionInstance)).thenReturn(stateExecutionInstance);
    when(resumeStateUtils.fetchPipelineExecutionId(context)).thenReturn(currPipelineExecutionId);
    when(resumeStateUtils.prepareExecutionResponseBuilder(context, prevStateExecutionId))
        .thenReturn(ExecutionResponse.builder());
    ExecutionResponse response = envLoopResumeState.execute(context);
    verify(resumeStateUtils)
        .copyPipelineStageOutputs(eq(APP_ID), eq(prevPipelineExecutionId), eq(prevStateExecutionId), eq(null),
            eq(currPipelineExecutionId), eq(currStateExecutionId));
    verify(resumeStateUtils).prepareExecutionResponseBuilder(eq(context), eq(prevStateExecutionId));
    assertThat(response.isAsync()).isTrue();
    assertThat(response.getCorrelationIds().size()).isEqualTo(2);
  }

  @Test
  @Owner(developers = GARVIT)
  @Category(UnitTests.class)
  public void shouldGetTimeout() {
    envLoopResumeState.setTimeoutMillis(null);
    Integer timeoutMillis = envLoopResumeState.getTimeoutMillis();
    assertThat(timeoutMillis).isEqualTo(ResumeStateUtils.RESUME_STATE_TIMEOUT_MILLIS);
  }

  @Test
  @Owner(developers = GARVIT)
  @Category(UnitTests.class)
  public void shouldGetSetTimeout() {
    envLoopResumeState.setTimeoutMillis((int) TimeUnit.HOURS.toMillis(1));
    Integer timeoutMillis = envLoopResumeState.getTimeoutMillis();
    assertThat(timeoutMillis).isEqualTo((int) TimeUnit.HOURS.toMillis(1));
  }
}
