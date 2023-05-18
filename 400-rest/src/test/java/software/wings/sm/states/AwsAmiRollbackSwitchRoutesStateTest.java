/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.sm.states;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.rule.OwnerRule.ARVIND;
import static io.harness.rule.OwnerRule.IVAN;
import static io.harness.rule.OwnerRule.TMACARI;

import static software.wings.service.impl.aws.model.AwsConstants.AMI_SERVICE_SETUP_SWEEPING_OUTPUT_NAME;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyBoolean;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.exception.InvalidRequestException;
import io.harness.rule.Owner;

import software.wings.WingsBaseTest;
import software.wings.sm.ExecutionContextImpl;

import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;

@OwnedBy(CDP)
public class AwsAmiRollbackSwitchRoutesStateTest extends WingsBaseTest {
  @Mock AwsAmiServiceStateHelper awsAmiServiceHelper;
  @InjectMocks
  @Spy
  private final AwsAmiRollbackSwitchRoutesState state = new AwsAmiRollbackSwitchRoutesState("stateName");

  @Test
  @Owner(developers = ARVIND)
  @Category(UnitTests.class)
  public void testExecute() throws InterruptedException {
    ExecutionContextImpl mockContext = mock(ExecutionContextImpl.class);
    state.execute(mockContext);
    verify(state).executeInternal(any(), anyBoolean());
  }

  @Test(expected = InvalidRequestException.class)
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void testExecuteWithWingsException() {
    ExecutionContextImpl mockContext = mock(ExecutionContextImpl.class);
    doThrow(new InvalidRequestException("Error msg"))
        .when(awsAmiServiceHelper)
        .getSetupElementFromSweepingOutput(mockContext, AMI_SERVICE_SETUP_SWEEPING_OUTPUT_NAME);
    state.execute(mockContext);
  }

  @Test(expected = InvalidRequestException.class)
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void testExecuteWithInvalidRequestException() {
    ExecutionContextImpl mockContext = mock(ExecutionContextImpl.class);
    doThrow(new RuntimeException("Error msg"))
        .when(awsAmiServiceHelper)
        .getSetupElementFromSweepingOutput(mockContext, AMI_SERVICE_SETUP_SWEEPING_OUTPUT_NAME);
    state.execute(mockContext);
  }

  @Test
  @Owner(developers = TMACARI)
  @Category(UnitTests.class)
  public void testHandleAbortEvent() {
    assertThat(state.isDownsizeOldAsg()).isEqualTo(false);
    state.setDownsizeOldAsg(true);
    assertThat(state.isDownsizeOldAsg()).isEqualTo(true);
    state.setDownsizeOldAsg(false);
    assertThat(state.isDownsizeOldAsg()).isEqualTo(false);
  }
}
