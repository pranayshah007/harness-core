/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.aws.asg;

import com.amazonaws.services.autoscaling.AmazonAutoScalingClient;
import com.amazonaws.services.autoscaling.model.AutoScalingGroup;
import com.amazonaws.services.autoscaling.model.DescribeAutoScalingGroupsResult;
import com.amazonaws.services.autoscaling.model.Instance;
import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.aws.AwsCallTracker;
import io.harness.aws.beans.AwsInternalConfig;
import io.harness.category.element.UnitTests;
import io.harness.delegate.aws.asg.AsgCommandTaskNGHandler;
import io.harness.delegate.beans.connector.awsconnector.AwsConnectorDTO;
import io.harness.delegate.beans.connector.awsconnector.AwsCredentialDTO;
import io.harness.delegate.beans.connector.awsconnector.AwsCredentialType;
import io.harness.delegate.beans.connector.awsconnector.AwsListASGInstancesTaskParamsRequest;
import io.harness.delegate.beans.connector.awsconnector.AwsListASGNamesTaskResponse;
import io.harness.delegate.beans.connector.awsconnector.AwsListEC2InstancesTaskResponse;
import io.harness.delegate.beans.connector.awsconnector.AwsManualConfigSpecDTO;
import io.harness.delegate.beans.connector.awsconnector.AwsTaskType;
import io.harness.delegate.beans.logstreaming.ILogStreamingTaskClient;
import io.harness.delegate.exception.TaskNGDataException;
import io.harness.delegate.task.aws.AwsASGDelegateTaskHelper;
import io.harness.delegate.task.aws.AwsListEC2InstancesDelegateTaskHelper;
import io.harness.encryption.SecretRefData;
import io.harness.exception.AwsInstanceException;
import io.harness.rule.Owner;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import software.wings.service.impl.AwsUtils;
import software.wings.service.impl.aws.model.AwsEC2Instance;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.rule.OwnerRule.LOVISH_BANSAL;
import static io.harness.rule.OwnerRule.VITALIE;
import static junit.framework.TestCase.assertEquals;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.joor.Reflect.on;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

@OwnedBy(CDP)


public class AsgDelegateTaskHelperTest extends CategoryTest {
  private AsgDelegateTaskHelper asgDelegateTaskHelper = new AsgDelegateTaskHelper();

  @Test
  @Owner(developers = LOVISH_BANSAL)
  @Category(UnitTests.class)
  void testGetAsgCommandResponse() {
    AsgCommandTaskNGHandler commandTaskHandler = mock(AsgCommandTaskNGHandler.class);
    AsgCommandRequest asgCommandRequest = mock(AsgCommandRequest.class);
    ILogStreamingTaskClient iLogStreamingTaskClient = mock(ILogStreamingTaskClient.class);

    AsgCommandResponse expectedResponse = mock(AsgCommandResponse.class);
    when(commandTaskHandler.executeTask(any(), any(), any())).thenReturn(expectedResponse);

    AsgCommandResponse actualResponse = asgDelegateTaskHelper.getAsgCommandResponse(commandTaskHandler, asgCommandRequest, iLogStreamingTaskClient);

    assertEquals(expectedResponse, actualResponse);
  }

  @Test
  @Owner(developers = LOVISH_BANSAL)
  @Category(UnitTests.class)
  void testGetAsgCommandResponse_throwsException() {
    AsgCommandTaskNGHandler commandTaskHandler = mock(AsgCommandTaskNGHandler.class);
    AsgCommandRequest asgCommandRequest = mock(AsgCommandRequest.class);
    ILogStreamingTaskClient iLogStreamingTaskClient = mock(ILogStreamingTaskClient.class);

    Exception exception = new Exception("test exception");
    when(commandTaskHandler.executeTask(any(), any(), any())).thenThrow(exception);

    assertThrows(TaskNGDataException.class, () -> {
      asgDelegateTaskHelper.getAsgCommandResponse(commandTaskHandler, asgCommandRequest, iLogStreamingTaskClient);
    });
  }

}
