/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.delegatetasks;

import static io.harness.delegate.beans.TaskData.DEFAULT_SYNC_CALL_TIMEOUT;
import static io.harness.rule.OwnerRule.VUK;

import static software.wings.beans.TaskType.CAPABILITY_VALIDATION;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.TargetModule;
import io.harness.category.element.UnitTests;
import io.harness.delegate.beans.DelegateTaskPackage;
import io.harness.delegate.beans.TaskData;
import io.harness.delegate.beans.executioncapability.CapabilityResponse;
import io.harness.delegate.beans.executioncapability.CapabilityType;
import io.harness.delegate.beans.executioncapability.ExecutionCapability;
import io.harness.delegate.task.TaskParameters;
import io.harness.delegate.task.executioncapability.CapabilityCheck;
import io.harness.rule.Owner;
import io.harness.utils.Functions;

import software.wings.WingsBaseTest;
import software.wings.delegatetasks.delegatecapability.capabilitycheck.CapabilityCheckFactory;
import software.wings.delegatetasks.tasks.PerpetualTaskCapabilityCheckTask;
import software.wings.service.impl.realization.PerpetualTaskCapabilityCheckResponse;

import org.apache.commons.lang3.NotImplementedException;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;

@TargetModule(HarnessModule._930_DELEGATE_TASKS)
public class PerpetualTaskCapabilityCheckTaskTest extends WingsBaseTest {
  @Mock CapabilityCheck capabilityCheck;
  @Mock CapabilityCheckFactory capabilityCheckFactory;
  @Mock ExecutionCapability executionCapability;

  @InjectMocks
  private final PerpetualTaskCapabilityCheckTask task =
      spy(new PerpetualTaskCapabilityCheckTask(DelegateTaskPackage.builder()
                                                   .delegateId("delegateId")
                                                   .data(TaskData.builder()
                                                             .async(false)
                                                             .taskType(CAPABILITY_VALIDATION.name())
                                                             .timeout(DEFAULT_SYNC_CALL_TIMEOUT)
                                                             .build())
                                                   .build(),
          null, Functions::doNothing, Functions::staticTruth));

  @Test
  @Owner(developers = VUK)
  @Category(UnitTests.class)
  public void shouldThrowNotImplementedException() {
    assertThatThrownBy(() -> task.run(Mockito.mock(TaskParameters.class))).isInstanceOf(NotImplementedException.class);
  }

  @Test
  @Owner(developers = VUK)
  @Category(UnitTests.class)
  public void shouldRunPerpetualTaskCapabilityCheckTask() {
    Object[] params = new Object[1];
    params[0] = executionCapability;

    CapabilityResponse capabilityResponse = CapabilityResponse.builder().validated(true).build();

    when(executionCapability.getCapabilityType()).thenReturn(CapabilityType.ALWAYS_TRUE);
    when(capabilityCheckFactory.obtainCapabilityCheck(CapabilityType.ALWAYS_TRUE)).thenReturn(capabilityCheck);
    when(capabilityCheck.performCapabilityCheck(executionCapability)).thenReturn(capabilityResponse);

    PerpetualTaskCapabilityCheckResponse perpetualTaskCapabilityCheckResponse =
        (PerpetualTaskCapabilityCheckResponse) task.run(params);

    assertThat(perpetualTaskCapabilityCheckResponse.isAbleToExecutePerpetualTask()).isTrue();
  }

  @Test
  @Owner(developers = VUK)
  @Category(UnitTests.class)
  public void shouldNotRunPerpetualTaskCapabilityCheckTask() {
    Object[] params = new Object[1];
    params[0] = executionCapability;

    when(executionCapability.getCapabilityType()).thenReturn(CapabilityType.ALWAYS_TRUE);
    when(capabilityCheckFactory.obtainCapabilityCheck(CapabilityType.ALWAYS_TRUE)).thenReturn(null);

    PerpetualTaskCapabilityCheckResponse perpetualTaskCapabilityCheckResponse =
        (PerpetualTaskCapabilityCheckResponse) task.run(params);

    assertThat(perpetualTaskCapabilityCheckResponse.isAbleToExecutePerpetualTask()).isFalse();
  }
}
