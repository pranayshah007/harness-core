/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.aws.asg;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.rule.OwnerRule.LOVISH_BANSAL;

import static junit.framework.TestCase.assertEquals;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.delegate.aws.asg.AsgCommandTaskNGHandler;
import io.harness.delegate.beans.logstreaming.ILogStreamingTaskClient;
import io.harness.delegate.exception.AsgNGException;
import io.harness.delegate.exception.TaskNGDataException;
import io.harness.rule.Owner;

import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

@OwnedBy(CDP)

public class AsgDelegateTaskHelperTest extends CategoryTest {
  @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();
  @InjectMocks private AsgDelegateTaskHelper asgDelegateTaskHelper = new AsgDelegateTaskHelper();

  @Mock AsgInfraConfigHelper asgInfraConfigHelper;

  @Test
  @Owner(developers = LOVISH_BANSAL)
  @Category(UnitTests.class)
  public void testGetAsgCommandResponse() {
    AsgCommandTaskNGHandler commandTaskHandler = mock(AsgCommandTaskNGHandler.class);
    AsgCommandRequest asgCommandRequest = mock(AsgCommandRequest.class);
    ILogStreamingTaskClient iLogStreamingTaskClient = mock(ILogStreamingTaskClient.class);

    AsgCommandResponse expectedResponse = mock(AsgCommandResponse.class);
    when(commandTaskHandler.executeTask(any(), any(), any())).thenReturn(expectedResponse);
    doNothing().when(asgInfraConfigHelper).decryptAsgInfraConfig(any());

    AsgCommandResponse actualResponse =
        asgDelegateTaskHelper.getAsgCommandResponse(commandTaskHandler, asgCommandRequest, iLogStreamingTaskClient);

    assertEquals(expectedResponse, actualResponse);
  }

  @Test
  @Owner(developers = LOVISH_BANSAL)
  @Category(UnitTests.class)
  public void testGetAsgCommandResponse_throwsException() {
    AsgCommandTaskNGHandler commandTaskHandler = mock(AsgCommandTaskNGHandler.class);
    AsgCommandRequest asgCommandRequest = mock(AsgCommandRequest.class);
    ILogStreamingTaskClient iLogStreamingTaskClient = mock(ILogStreamingTaskClient.class);

    AsgNGException exception = new AsgNGException(null);
    when(commandTaskHandler.executeTask(any(), any(), any())).thenThrow(exception);
    doNothing().when(asgInfraConfigHelper).decryptAsgInfraConfig(any());

    assertThatThrownBy(()
                           -> asgDelegateTaskHelper.getAsgCommandResponse(
                               commandTaskHandler, asgCommandRequest, iLogStreamingTaskClient))
        .isInstanceOf(TaskNGDataException.class);
  }
}
