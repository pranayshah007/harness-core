/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.delegatetasks.aws.ecs.ecstaskhandler;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.rule.OwnerRule.ARVIND;
import static io.harness.rule.OwnerRule.IVAN;
import static io.harness.rule.OwnerRule.SAINATH;

import static java.lang.String.format;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.category.element.UnitTests;
import io.harness.exception.TimeoutException;
import io.harness.logging.CommandExecutionStatus;
import io.harness.rule.Owner;

import software.wings.WingsBaseTest;
import software.wings.beans.command.ExecutionLogCallback;
import software.wings.helpers.ext.ecs.request.EcsBGListenerUpdateRequest;
import software.wings.helpers.ext.ecs.request.EcsServiceDeployRequest;
import software.wings.helpers.ext.ecs.response.EcsCommandExecutionResponse;
import software.wings.service.intfc.aws.delegate.AwsElbHelperServiceDelegate;

import com.amazonaws.services.elasticloadbalancingv2.model.Action;
import com.amazonaws.services.elasticloadbalancingv2.model.DescribeListenersResult;
import com.amazonaws.services.elasticloadbalancingv2.model.Listener;
import java.util.Arrays;
import java.util.Collections;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;

@OwnedBy(CDP)
@TargetModule(HarnessModule._930_DELEGATE_TASKS)
public class EcsListenerUpdateBGTaskHandlerTest extends WingsBaseTest {
  @Mock private AwsElbHelperServiceDelegate awsElbHelperServiceDelegate;
  @Mock private EcsSwapRoutesCommandTaskHelper ecsSwapRoutesCommandTaskHelper;
  @Mock private ExecutionLogCallback executionLogCallback;

  @InjectMocks private EcsListenerUpdateBGTaskHandler ecsListenerUpdateBGTaskHandler;

  @Test
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void testExecuteTaskInternalWithNotValidEcsCommandRequest() {
    EcsServiceDeployRequest ecsServiceDeployRequest = EcsServiceDeployRequest.builder().build();

    EcsCommandExecutionResponse ecsCommandExecutionResponse = ecsListenerUpdateBGTaskHandler.executeTaskInternal(
        ecsServiceDeployRequest, Collections.emptyList(), executionLogCallback);

    assertThat(ecsCommandExecutionResponse).isNotNull();
    assertThat(ecsCommandExecutionResponse.getCommandExecutionStatus()).isNotNull();
    assertThat(ecsCommandExecutionResponse.getCommandExecutionStatus()).isEqualTo(CommandExecutionStatus.FAILURE);
    assertThat(ecsCommandExecutionResponse.getEcsCommandResponse().getOutput())
        .isEqualTo("Invalid Request Type: Expected was : EcsBGListenerUpdateRequest");
    assertThat(ecsCommandExecutionResponse.getEcsCommandResponse().getCommandExecutionStatus())
        .isEqualTo(CommandExecutionStatus.FAILURE);
  }

  @Test
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void testRollbackWithUpdateRequired() {
    doNothing().when(executionLogCallback).saveExecutionLog(any());
    doNothing()
        .when(ecsSwapRoutesCommandTaskHelper)
        .upsizeOlderService(any(), anyList(), any(), any(), anyInt(), any(), any(), anyInt(), anyBoolean());
    doNothing()
        .when(awsElbHelperServiceDelegate)
        .swapListenersForEcsBG(any(), anyList(), anyBoolean(), any(), any(), any(), any(), any(), any(), any(), any());
    doNothing()
        .when(ecsSwapRoutesCommandTaskHelper)
        .updateServiceTags(any(), anyList(), any(), any(), any(), any(), anyBoolean(), any());
    doReturn(Arrays.asList(new Action()))
        .when(awsElbHelperServiceDelegate)
        .getMatchingTargetGroupForSpecificListenerRuleArn(any(), anyList(), any(), any(), any(), any(), any());

    EcsBGListenerUpdateRequest ecsBGListenerUpdateRequest =
        EcsBGListenerUpdateRequest.builder().rollback(true).isUseSpecificListenerRuleArn(true).build();
    EcsCommandExecutionResponse ecsCommandExecutionResponse = ecsListenerUpdateBGTaskHandler.executeTaskInternal(
        ecsBGListenerUpdateRequest, Collections.emptyList(), executionLogCallback);

    assertThat(ecsCommandExecutionResponse).isNotNull();
    assertThat(ecsCommandExecutionResponse.getCommandExecutionStatus()).isNotNull();
    assertThat(ecsCommandExecutionResponse.getCommandExecutionStatus()).isEqualTo(CommandExecutionStatus.SUCCESS);
  }

  @Test
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void testRollbackWithNoUpdateRequired() {
    doNothing().when(executionLogCallback).saveExecutionLog(any());
    doNothing()
        .when(ecsSwapRoutesCommandTaskHelper)
        .upsizeOlderService(any(), anyList(), any(), any(), anyInt(), any(), any(), anyInt(), anyBoolean());
    doNothing()
        .when(ecsSwapRoutesCommandTaskHelper)
        .updateServiceTags(any(), anyList(), any(), any(), any(), any(), anyBoolean(), any());
    doNothing()
        .when(ecsSwapRoutesCommandTaskHelper)

        .downsizeOlderService(any(), anyList(), any(), any(), any(), any(), any());

    doReturn(Collections.emptyList())
        .when(awsElbHelperServiceDelegate)
        .getMatchingTargetGroupForSpecificListenerRuleArn(any(), anyList(), any(), any(), any(), any(), any());

    EcsBGListenerUpdateRequest ecsBGListenerUpdateRequest =
        EcsBGListenerUpdateRequest.builder().rollback(true).isUseSpecificListenerRuleArn(true).build();
    EcsCommandExecutionResponse ecsCommandExecutionResponse = ecsListenerUpdateBGTaskHandler.executeTaskInternal(
        ecsBGListenerUpdateRequest, Collections.emptyList(), executionLogCallback);

    assertThat(ecsCommandExecutionResponse).isNotNull();
    assertThat(ecsCommandExecutionResponse.getCommandExecutionStatus()).isNotNull();
    assertThat(ecsCommandExecutionResponse.getCommandExecutionStatus()).isEqualTo(CommandExecutionStatus.SUCCESS);
  }

  @Test
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void testDownsizeOldService() {
    doNothing().when(executionLogCallback).saveExecutionLog(any());
    doNothing()
        .when(awsElbHelperServiceDelegate)
        .swapListenersForEcsBG(any(), anyList(), anyBoolean(), any(), any(), any(), any(), any(), any(), any(), any());
    doNothing()
        .when(ecsSwapRoutesCommandTaskHelper)
        .updateServiceTags(any(), anyList(), any(), any(), any(), any(), anyBoolean(), any());
    doNothing()
        .when(ecsSwapRoutesCommandTaskHelper)

        .downsizeOlderService(any(), anyList(), any(), any(), any(), any(), any());

    EcsBGListenerUpdateRequest ecsBGListenerUpdateRequest = EcsBGListenerUpdateRequest.builder()
                                                                .rollback(false)
                                                                .isUseSpecificListenerRuleArn(true)
                                                                .downsizeOldService(true)
                                                                .build();
    EcsCommandExecutionResponse ecsCommandExecutionResponse = ecsListenerUpdateBGTaskHandler.executeTaskInternal(
        ecsBGListenerUpdateRequest, Collections.emptyList(), executionLogCallback);

    assertThat(ecsCommandExecutionResponse).isNotNull();
    assertThat(ecsCommandExecutionResponse.getCommandExecutionStatus()).isNotNull();
    assertThat(ecsCommandExecutionResponse.getCommandExecutionStatus()).isEqualTo(CommandExecutionStatus.SUCCESS);
  }

  @Test
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void testRollbackWithoutSpecificListenerRuleArn() {
    DescribeListenersResult describeListenersResult = new DescribeListenersResult();
    Action action = new Action();
    action.setType("forward");
    action.setTargetGroupArn("targetGroupArn");
    Listener listener = new Listener();
    listener.setDefaultActions(Arrays.asList(action));
    describeListenersResult.setListeners(Arrays.asList(listener));
    doNothing().when(executionLogCallback).saveExecutionLog(any());
    doNothing()
        .when(ecsSwapRoutesCommandTaskHelper)
        .upsizeOlderService(any(), anyList(), any(), any(), anyInt(), any(), any(), anyInt(), anyBoolean());
    doReturn(describeListenersResult)
        .when(awsElbHelperServiceDelegate)
        .describeListenerResult(any(), anyList(), any(), any());
    doNothing()
        .when(awsElbHelperServiceDelegate)
        .swapListenersForEcsBG(any(), anyList(), anyBoolean(), any(), any(), any(), any(), any(), any(), any(), any());
    doNothing()
        .when(ecsSwapRoutesCommandTaskHelper)
        .updateServiceTags(any(), anyList(), any(), any(), any(), any(), anyBoolean(), any());

    EcsBGListenerUpdateRequest ecsBGListenerUpdateRequest =
        EcsBGListenerUpdateRequest.builder().rollback(true).isUseSpecificListenerRuleArn(false).build();
    EcsCommandExecutionResponse ecsCommandExecutionResponse = ecsListenerUpdateBGTaskHandler.executeTaskInternal(
        ecsBGListenerUpdateRequest, Collections.emptyList(), executionLogCallback);

    assertThat(ecsCommandExecutionResponse).isNotNull();
    assertThat(ecsCommandExecutionResponse.getCommandExecutionStatus()).isNotNull();
    assertThat(ecsCommandExecutionResponse.getCommandExecutionStatus()).isEqualTo(CommandExecutionStatus.SUCCESS);
  }

  @Test
  @Owner(developers = ARVIND)
  @Category(UnitTests.class)
  public void testDownsizeOldService_Timeout() {
    doNothing().when(executionLogCallback).saveExecutionLog(any());
    doNothing()
        .when(awsElbHelperServiceDelegate)
        .swapListenersForEcsBG(any(), anyList(), anyBoolean(), any(), any(), any(), any(), any(), any(), any(), any());
    doNothing()
        .when(ecsSwapRoutesCommandTaskHelper)
        .updateServiceTags(any(), anyList(), any(), any(), any(), any(), anyBoolean(), any());
    doThrow(new TimeoutException("", "", null))
        .when(ecsSwapRoutesCommandTaskHelper)
        .downsizeOlderService(any(), anyList(), any(), any(), any(), any(), any());

    EcsBGListenerUpdateRequest ecsBGListenerUpdateRequest = EcsBGListenerUpdateRequest.builder()
                                                                .rollback(false)
                                                                .isUseSpecificListenerRuleArn(true)
                                                                .downsizeOldService(true)
                                                                .timeoutErrorSupported(true)
                                                                .build();
    EcsCommandExecutionResponse ecsCommandExecutionResponse = ecsListenerUpdateBGTaskHandler.executeTaskInternal(
        ecsBGListenerUpdateRequest, Collections.emptyList(), executionLogCallback);

    assertThat(ecsCommandExecutionResponse).isNotNull();
    assertThat(ecsCommandExecutionResponse.getCommandExecutionStatus()).isNotNull();
    assertThat(ecsCommandExecutionResponse.getCommandExecutionStatus()).isEqualTo(CommandExecutionStatus.FAILURE);
    assertThat(ecsCommandExecutionResponse.getEcsCommandResponse().isTimeoutFailure()).isTrue();
  }

  @Test
  @Owner(developers = SAINATH)
  @Category(UnitTests.class)
  public void testDelayWaitBeforeOldServiceDownSize() {
    doNothing().when(executionLogCallback).saveExecutionLog(any());
    doNothing()
        .when(awsElbHelperServiceDelegate)
        .swapListenersForEcsBG(any(), anyList(), anyBoolean(), any(), any(), any(), any(), any(), any(), any(), any());
    doNothing()
        .when(ecsSwapRoutesCommandTaskHelper)
        .updateServiceTags(any(), anyList(), any(), any(), any(), any(), anyBoolean(), any());
    doNothing()
        .when(ecsSwapRoutesCommandTaskHelper)

        .downsizeOlderService(any(), anyList(), any(), any(), any(), any(), any());

    // rollback=false
    EcsBGListenerUpdateRequest ecsBGListenerUpdateRequest = EcsBGListenerUpdateRequest.builder()
                                                                .rollback(false)
                                                                .isUseSpecificListenerRuleArn(true)
                                                                .downsizeOldService(true)
                                                                .downsizeOldServiceDelayInSecs(100l)
                                                                .ecsBgDownsizeDelayEnabled(true)
                                                                .serviceNameDownsized("test1")
                                                                .build();
    EcsCommandExecutionResponse ecsCommandExecutionResponse = ecsListenerUpdateBGTaskHandler.executeTaskInternal(
        ecsBGListenerUpdateRequest, Collections.emptyList(), executionLogCallback);

    verify(executionLogCallback, times(1))
        .saveExecutionLog(format("Waiting for %d seconds before downsizing service %s", 100l, "test1"));
    // rollback=true
    ecsBGListenerUpdateRequest.setRollback(true);
    ecsBGListenerUpdateRequest.setDownsizeOldService(true);
    ecsBGListenerUpdateRequest.setEcsBgDownsizeDelayEnabled(true);
    ecsBGListenerUpdateRequest.setDownsizeOldServiceDelayInSecs(50l);
    ecsBGListenerUpdateRequest.setServiceNameDownsized("test2");

    verify(executionLogCallback, times(0))
        .saveExecutionLog(format("Waiting for %d seconds before downsizing service %s", 50l, "test2"));

    // FF disabled
    ecsBGListenerUpdateRequest.setRollback(false);
    ecsBGListenerUpdateRequest.setDownsizeOldService(true);
    ecsBGListenerUpdateRequest.setEcsBgDownsizeDelayEnabled(false);
    ecsBGListenerUpdateRequest.setDownsizeOldServiceDelayInSecs(30l);
    ecsBGListenerUpdateRequest.setServiceNameDownsized("test3");

    verify(executionLogCallback, times(0))
        .saveExecutionLog(format("Waiting for %d seconds before downsizing service %s", 30l, "test3"));

    // delay 0
    ecsBGListenerUpdateRequest.setRollback(false);
    ecsBGListenerUpdateRequest.setDownsizeOldService(true);
    ecsBGListenerUpdateRequest.setEcsBgDownsizeDelayEnabled(true);
    ecsBGListenerUpdateRequest.setDownsizeOldServiceDelayInSecs(0l);
    ecsBGListenerUpdateRequest.setServiceNameDownsized("test4");

    verify(executionLogCallback, times(0))
        .saveExecutionLog(format("Waiting for %d seconds before downsizing service %s", 0l, "test4"));

    //  downSize service name null
    ecsBGListenerUpdateRequest.setRollback(false);
    ecsBGListenerUpdateRequest.setDownsizeOldService(true);
    ecsBGListenerUpdateRequest.setEcsBgDownsizeDelayEnabled(true);
    ecsBGListenerUpdateRequest.setDownsizeOldServiceDelayInSecs(10l);
    ecsBGListenerUpdateRequest.setServiceNameDownsized(null);

    verify(executionLogCallback, times(0))
        .saveExecutionLog(format("Waiting for %d seconds before downsizing service %s", 10l, null));
  }
}
