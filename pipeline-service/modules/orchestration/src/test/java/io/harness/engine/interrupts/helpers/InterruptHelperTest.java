/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.engine.interrupts.helpers;

import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.rule.OwnerRule.PRASHANT;

import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.harness.OrchestrationTestBase;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.engine.NoopTaskExecutor;
import io.harness.engine.pms.tasks.TaskExecutor;
import io.harness.execution.NodeExecution;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.ExecutableResponse;
import io.harness.pms.contracts.execution.ExecutionMode;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.execution.TaskExecutableResponse;
import io.harness.pms.contracts.execution.tasks.TaskCategory;
import io.harness.rule.Owner;

import com.google.inject.Inject;
import java.util.HashMap;
import java.util.Map;
import org.assertj.core.api.Assertions;
import org.joor.Reflect;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;

@OwnedBy(HarnessTeam.PIPELINE)
public class InterruptHelperTest extends OrchestrationTestBase {
  @Inject @InjectMocks InterruptHelper interruptHelper;
  @Inject NoopTaskExecutor noopTaskExecutor;
  private Map<TaskCategory, TaskExecutor> taskExecutorMap = new HashMap<>();

  @Before
  public void setup() {
    taskExecutorMap.put(TaskCategory.UNKNOWN_CATEGORY, noopTaskExecutor);
  }

  @Test
  @Owner(developers = PRASHANT)
  @Category(UnitTests.class)
  public void shouldTestDiscontinueTaskIfRequiredForTaskExecutable() {
    Map<TaskCategory, TaskExecutor> spy = spy(taskExecutorMap);
    Reflect.on(interruptHelper).set("taskExecutorMap", spy);
    NodeExecution nodeExecution =
        NodeExecution.builder()
            .uuid(generateUuid())
            .ambiance(Ambiance.newBuilder().setPlanExecutionId(generateUuid()).build())
            .status(Status.RUNNING)
            .mode(ExecutionMode.TASK)
            .executableResponse(ExecutableResponse.newBuilder()
                                    .setTask(TaskExecutableResponse.newBuilder()
                                                 .setTaskId(generateUuid())
                                                 .setTaskCategory(TaskCategory.UNKNOWN_CATEGORY)
                                                 .build())
                                    .build())
            .startTs(123L)
            .build();
    boolean discontinued = interruptHelper.discontinueTaskIfRequired(nodeExecution);
    verify(spy, times(1)).get(TaskCategory.UNKNOWN_CATEGORY);
    Assertions.assertThat(discontinued).isTrue();
  }
}
