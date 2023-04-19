/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.delegatetasks.aws.ecs.ecstaskhandler;

import static io.harness.logging.CommandExecutionStatus.SUCCESS;
import static io.harness.rule.OwnerRule.ARVIND;
import static io.harness.rule.OwnerRule.RAGHVENDRA;

import static software.wings.beans.command.EcsSetupParams.EcsSetupParamsBuilder.anEcsSetupParams;
import static software.wings.utils.WingsTestConstants.SERVICE_ID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.TargetModule;
import io.harness.category.element.UnitTests;
import io.harness.delegate.task.aws.AwsElbListener;
import io.harness.delegate.task.aws.AwsElbListenerRuleData;
import io.harness.logging.CommandExecutionStatus;
import io.harness.rule.Owner;

import software.wings.WingsBaseTest;
import software.wings.beans.command.ExecutionLogCallback;
import software.wings.cloudprovider.aws.EcsContainerService;
import software.wings.delegatetasks.DelegateFileManager;
import software.wings.delegatetasks.DelegateLogService;
import software.wings.helpers.ext.ecs.request.EcsBGServiceSetupRequest;
import software.wings.helpers.ext.ecs.request.EcsServiceSetupRequest;
import software.wings.helpers.ext.ecs.response.EcsCommandExecutionResponse;
import software.wings.service.impl.AwsHelperService;
import software.wings.service.intfc.aws.delegate.AwsElbHelperServiceDelegate;
import software.wings.service.intfc.security.EncryptionService;

import com.amazonaws.services.ecs.model.TaskDefinition;
import com.amazonaws.services.elasticloadbalancingv2.model.Action;
import com.amazonaws.services.elasticloadbalancingv2.model.Listener;
import com.amazonaws.services.elasticloadbalancingv2.model.TargetGroup;
import com.google.inject.Inject;
import java.util.Arrays;
import java.util.Collections;
import java.util.Optional;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;

@TargetModule(HarnessModule._930_DELEGATE_TASKS)
public class EcsBlueGreenSetupCommandHandlerTest extends WingsBaseTest {
  private final Listener forwardListener =
      new Listener().withDefaultActions(new Action().withTargetGroupArn("arn").withType("forward"));
  private final Listener nonForwardListener =
      new Listener().withDefaultActions(new Action().withTargetGroupArn("arn").withType("default"));
  private final String PROD_LISTENER_RULE_ARN = "prodlistenerRulearn";
  private final String STAGE_LISTENER_RULE_ARN = "stagelistenerRulearn";
  private final String PROD_LISTENER_ARN = "prodlistenerarn";
  private final String STAGE_LISTENER_ARN = "stagelistenerarn";
  private final String TARGET_GROUP_1_ARN = "targetGroup1Arn";
  private final String TARGET_GROUP_2_ARN = "targetGroup2Arn";

  private final AwsElbListenerRuleData defaultRuleData =
      AwsElbListenerRuleData.builder().ruleArn("defaultRuleData").build();
  private final AwsElbListenerRuleData ruleDataProd =
      AwsElbListenerRuleData.builder().ruleArn("ruleDataWithTg").build();
  private final AwsElbListenerRuleData ruleDataStage =
      AwsElbListenerRuleData.builder().ruleArn("ruleDataWithTg").build();
  private final String PROD_PORT = "80";
  private final String STAGE_PORT = "81";

  private final AwsElbListener prodSpecificListener = AwsElbListener.builder()
                                                          .listenerArn(PROD_LISTENER_ARN)
                                                          .rules(Arrays.asList(defaultRuleData))
                                                          .port(Integer.parseInt(PROD_PORT))
                                                          .build();
  private final AwsElbListener stageSpecificListener = AwsElbListener.builder()
                                                           .listenerArn(STAGE_LISTENER_ARN)
                                                           .rules(Arrays.asList(defaultRuleData))
                                                           .port(Integer.parseInt(STAGE_PORT))
                                                           .build();

  @InjectMocks @Inject private EcsBlueGreenSetupCommandHandler handler;
  @Mock private AwsHelperService mockAwsHelperService;
  @Mock private EcsSetupCommandTaskHelper mockEcsSetupCommandTaskHelper;
  @Mock private AwsElbHelperServiceDelegate mockAwsElbHelperServiceDelegate;
  @Mock private EcsContainerService mockEcsContainerService;
  @Mock private DelegateFileManager mockDelegateFileManager;
  @Mock private EncryptionService mockEncryptionService;
  @Mock private DelegateLogService mockDelegateLogService;

  @Test
  @Owner(developers = ARVIND)
  @Category(UnitTests.class)
  public void testExecuteTaskInternalFailure() {
    ExecutionLogCallback mockCallback = mock(ExecutionLogCallback.class);
    EcsServiceSetupRequest request = EcsServiceSetupRequest.builder().build();
    EcsCommandExecutionResponse response = handler.executeTaskInternal(request, null, mockCallback);
    assertThat(response).isNotNull();
    assertThat(response.getCommandExecutionStatus()).isEqualTo(CommandExecutionStatus.FAILURE);
    assertThat(response.getEcsCommandResponse().getOutput())
        .isEqualTo("Invalid Request Type: Expected was : EcsBGServiceSetupRequest");
    assertThat(response.getEcsCommandResponse().getCommandExecutionStatus()).isEqualTo(CommandExecutionStatus.FAILURE);
  }

  @Test
  @Owner(developers = ARVIND)
  @Category(UnitTests.class)
  public void testExecute_NoActionSetForListenerForwardTargetGroup() {
    ExecutionLogCallback mockCallback = mock(ExecutionLogCallback.class);
    EcsBGServiceSetupRequest request =
        EcsBGServiceSetupRequest.builder().ecsSetupParams(anEcsSetupParams().build()).build();

    doReturn(nonForwardListener).when(mockAwsElbHelperServiceDelegate).getElbListener(any(), any(), any(), any());

    EcsCommandExecutionResponse response = handler.executeTaskInternal(request, null, mockCallback);
    assertThat(response).isNotNull();
    assertThat(response.getCommandExecutionStatus()).isEqualTo(CommandExecutionStatus.FAILURE);
    assertThat(response.getEcsCommandResponse().getCommandExecutionStatus()).isEqualTo(CommandExecutionStatus.FAILURE);
  }

  @Test
  @Owner(developers = RAGHVENDRA)
  @Category(UnitTests.class)
  public void testExecute_NoActionSetForSpecificListenerTargetGroup_isUseSpecificListenerRuleArnFalse() {
    ExecutionLogCallback mockCallback = mock(ExecutionLogCallback.class);
    EcsBGServiceSetupRequest request =
        EcsBGServiceSetupRequest.builder().ecsSetupParams(anEcsSetupParams().build()).build();

    doReturn(nonForwardListener).when(mockAwsElbHelperServiceDelegate).getElbListener(any(), any(), any(), any());
    doReturn(Arrays.asList(prodSpecificListener, stageSpecificListener))
        .when(mockAwsElbHelperServiceDelegate)
        .getElbListenersForLoadBalaner(any(), any(), any(), any());

    EcsCommandExecutionResponse response = handler.executeTaskInternal(request, null, mockCallback);
    assertThat(response).isNotNull();
    assertThat(request.getEcsSetupParams().isUseSpecificListenerRuleArn()).isFalse();
    assertThat(response.getCommandExecutionStatus()).isEqualTo(CommandExecutionStatus.FAILURE);
    assertThat(response.getEcsCommandResponse().getCommandExecutionStatus()).isEqualTo(CommandExecutionStatus.FAILURE);
  }

  @Test
  @Owner(developers = RAGHVENDRA)
  @Category(UnitTests.class)
  public void testExecute_NoTargetGroupSetForSpecificListener_isUseSpecificListenerRuleArnTrue() {
    ExecutionLogCallback mockCallback = mock(ExecutionLogCallback.class);
    EcsBGServiceSetupRequest request =
        EcsBGServiceSetupRequest.builder()
            .ecsSetupParams(
                anEcsSetupParams().withProdListenerArn(PROD_LISTENER_ARN).withUseSpecificListenerRuleArn(true).build())
            .build();

    doReturn(nonForwardListener).when(mockAwsElbHelperServiceDelegate).getElbListener(any(), any(), any(), any());
    doReturn(Arrays.asList(prodSpecificListener))
        .when(mockAwsElbHelperServiceDelegate)
        .getElbListenersForLoadBalaner(any(), any(), any(), any());
    doReturn(null)
        .when(mockAwsElbHelperServiceDelegate)
        .fetchTargetGroupForSpecificRules(any(), any(), any(), any(), any(), any());

    EcsCommandExecutionResponse response = handler.executeTaskInternal(request, null, mockCallback);
    assertThat(response).isNotNull();
    assertThat(request.getEcsSetupParams().isUseSpecificListenerRuleArn()).isTrue();
    assertThat(response.getCommandExecutionStatus()).isEqualTo(CommandExecutionStatus.FAILURE);
    assertThat(response.getEcsCommandResponse().getCommandExecutionStatus()).isEqualTo(CommandExecutionStatus.FAILURE);
  }

  @Test
  @Owner(developers = RAGHVENDRA)
  @Category(UnitTests.class)
  public void testExecute_TargetGroupSetForSpecificListener_isUseSpecificListenerRuleArnTrue() {
    ExecutionLogCallback mockCallback = mock(ExecutionLogCallback.class);
    final TargetGroup targetGroup1 = new TargetGroup();
    final TargetGroup targetGroup2 = new TargetGroup();
    targetGroup2.setTargetGroupArn(TARGET_GROUP_2_ARN);
    targetGroup2.setPort(Integer.parseInt(PROD_PORT));
    EcsBGServiceSetupRequest request = EcsBGServiceSetupRequest.builder()
                                           .ecsSetupParams(anEcsSetupParams()
                                                               .withProdListenerArn(PROD_LISTENER_ARN)
                                                               .withStageListenerArn(STAGE_LISTENER_ARN)
                                                               .withTargetPort(PROD_PORT)
                                                               .withStageListenerPort(STAGE_PORT)
                                                               .withStageListenerRuleArn(STAGE_LISTENER_RULE_ARN)
                                                               .withProdListenerRuleArn(PROD_LISTENER_RULE_ARN)
                                                               .withTargetGroupArn2(TARGET_GROUP_2_ARN)
                                                               .withTargetGroupArn(TARGET_GROUP_1_ARN)
                                                               .withUseSpecificListenerRuleArn(true)
                                                               .build())
                                           .build();

    doReturn(nonForwardListener).when(mockAwsElbHelperServiceDelegate).getElbListener(any(), any(), any(), any());
    doReturn(Arrays.asList(prodSpecificListener))
        .when(mockAwsElbHelperServiceDelegate)
        .getElbListenersForLoadBalaner(any(), any(), any(), any());
    doReturn(targetGroup2)
        .when(mockAwsElbHelperServiceDelegate)
        .fetchTargetGroupForSpecificRules(any(), any(), any(), any(), any(), any());
    doReturn(Optional.of(targetGroup2))
        .when(mockAwsElbHelperServiceDelegate)
        .getTargetGroup(any(), any(), any(), eq(TARGET_GROUP_2_ARN));
    doReturn(Optional.of(targetGroup1))
        .when(mockAwsElbHelperServiceDelegate)
        .getTargetGroup(any(), any(), any(), eq(TARGET_GROUP_1_ARN));

    EcsCommandExecutionResponse response = handler.executeTaskInternal(request, null, mockCallback);
    assertThat(response).isNotNull();
    assertThat(request.getEcsSetupParams().isUseSpecificListenerRuleArn()).isTrue();
    assertThat(response.getCommandExecutionStatus()).isEqualTo(CommandExecutionStatus.FAILURE);
    assertThat(response.getEcsCommandResponse().getCommandExecutionStatus()).isEqualTo(CommandExecutionStatus.FAILURE);
  }

  @Test
  @Owner(developers = ARVIND)
  @Category(UnitTests.class)
  public void testExecute_StageListenerArn() {
    ExecutionLogCallback mockCallback = mock(ExecutionLogCallback.class);
    EcsBGServiceSetupRequest request = EcsBGServiceSetupRequest.builder()
                                           .ecsSetupParams(anEcsSetupParams().withStageListenerArn("arn").build())
                                           .build();

    doReturn(forwardListener).when(mockAwsElbHelperServiceDelegate).getElbListener(any(), any(), any(), any());

    linearClosure(mockCallback, request);
  }

  @Test
  @Owner(developers = ARVIND)
  @Category(UnitTests.class)
  public void testExecute_NoStageListenerArn_WrongTargetGroupArn() {
    ExecutionLogCallback mockCallback = mock(ExecutionLogCallback.class);
    EcsBGServiceSetupRequest request =
        EcsBGServiceSetupRequest.builder().ecsSetupParams(anEcsSetupParams().withTargetGroupArn("arn").build()).build();

    doReturn(forwardListener).when(mockAwsElbHelperServiceDelegate).getElbListener(any(), any(), any(), any());

    doReturn(Optional.empty()).when(mockAwsElbHelperServiceDelegate).getTargetGroup(any(), any(), any(), any());

    EcsCommandExecutionResponse response = handler.executeTaskInternal(request, null, mockCallback);
    assertThat(response).isNotNull();
    assertThat(response.getCommandExecutionStatus()).isEqualTo(CommandExecutionStatus.FAILURE);
    assertThat(response.getEcsCommandResponse().getCommandExecutionStatus()).isEqualTo(CommandExecutionStatus.FAILURE);
  }

  @Test
  @Owner(developers = ARVIND)
  @Category(UnitTests.class)
  public void testExecute_NoStageListenerArn_TargetGroupArn() {
    ExecutionLogCallback mockCallback = mock(ExecutionLogCallback.class);
    EcsBGServiceSetupRequest request =
        EcsBGServiceSetupRequest.builder().ecsSetupParams(anEcsSetupParams().withTargetGroupArn("arn").build()).build();

    doReturn(forwardListener).when(mockAwsElbHelperServiceDelegate).getElbListener(any(), any(), any(), any());

    doReturn(Optional.of(new TargetGroup()))
        .when(mockAwsElbHelperServiceDelegate)
        .getTargetGroup(any(), any(), any(), any());

    linearClosure(mockCallback, request);
  }

  @Test
  @Owner(developers = ARVIND)
  @Category(UnitTests.class)
  public void testExecute_NoStageListenerArn_NoTargetGroupArn_Clone_ProdListenerTrue() {
    ExecutionLogCallback mockCallback = mock(ExecutionLogCallback.class);
    EcsBGServiceSetupRequest request = EcsBGServiceSetupRequest.builder()
                                           .ecsSetupParams(anEcsSetupParams()
                                                               .withRegion("region-1")
                                                               .withLoadBalancerName("lb-1")
                                                               .withStageListenerPort("8080")
                                                               .withTargetGroupArn2("arn2")
                                                               .build())
                                           .build();

    doReturn(forwardListener).when(mockAwsElbHelperServiceDelegate).getElbListener(any(), any(), any(), any());

    doReturn(Collections.singletonList(AwsElbListener.builder().port(8080).build()))
        .when(mockAwsElbHelperServiceDelegate)
        .getElbListenersForLoadBalaner(any(), any(), any(), any());

    linearClosure(mockCallback, request);

    verify(mockAwsElbHelperServiceDelegate, times(2)).getElbListener(any(), any(), any(), any());
    verify(mockEcsSetupCommandTaskHelper).getTargetGroupForDefaultAction(any(), any());
  }

  @Test
  @Owner(developers = ARVIND)
  @Category(UnitTests.class)
  public void testExecute_NoStageListenerArn_NoTargetGroupArn_Clone_ProdListenerFalse_StageTGTrue() {
    ExecutionLogCallback mockCallback = mock(ExecutionLogCallback.class);
    EcsBGServiceSetupRequest request = EcsBGServiceSetupRequest.builder()
                                           .ecsSetupParams(anEcsSetupParams()
                                                               .withRegion("region-1")
                                                               .withLoadBalancerName("lb-1")
                                                               .withStageListenerPort("8080")
                                                               .withTargetGroupArn2("arn2")
                                                               .build())
                                           .build();

    doReturn(forwardListener).when(mockAwsElbHelperServiceDelegate).getElbListener(any(), any(), any(), any());

    doReturn(Collections.singletonList(AwsElbListener.builder().port(80).build()))
        .when(mockAwsElbHelperServiceDelegate)
        .getElbListenersForLoadBalaner(any(), any(), any(), any());

    doReturn(Optional.of(new TargetGroup()))
        .when(mockAwsElbHelperServiceDelegate)
        .getTargetGroup(any(), any(), any(), any());

    doReturn(Optional.of(new TargetGroup()))
        .when(mockAwsElbHelperServiceDelegate)
        .getTargetGroupByName(any(), any(), any(), any());

    doReturn(forwardListener)
        .when(mockAwsElbHelperServiceDelegate)
        .createStageListener(any(), any(), any(), any(), anyInt(), any());

    linearClosure(mockCallback, request);

    verify(mockAwsElbHelperServiceDelegate).getElbListener(any(), any(), any(), any());
    verify(mockAwsElbHelperServiceDelegate).createStageListener(any(), any(), any(), any(), anyInt(), any());
  }

  @Test
  @Owner(developers = ARVIND)
  @Category(UnitTests.class)
  public void testExecute_NoStageListenerArn_NoTargetGroupArn_Clone_ProdListenerFalse_StageTGFalse() {
    ExecutionLogCallback mockCallback = mock(ExecutionLogCallback.class);
    EcsBGServiceSetupRequest request = EcsBGServiceSetupRequest.builder()
                                           .ecsSetupParams(anEcsSetupParams()
                                                               .withRegion("region-1")
                                                               .withLoadBalancerName("lb-1")
                                                               .withStageListenerPort("8080")
                                                               .withTargetGroupArn2("arn2")
                                                               .build())
                                           .build();

    doReturn(forwardListener).when(mockAwsElbHelperServiceDelegate).getElbListener(any(), any(), any(), any());

    doReturn(Collections.singletonList(AwsElbListener.builder().port(80).build()))
        .when(mockAwsElbHelperServiceDelegate)
        .getElbListenersForLoadBalaner(any(), any(), any(), any());

    doReturn(Optional.of(new TargetGroup()))
        .when(mockAwsElbHelperServiceDelegate)
        .getTargetGroup(any(), any(), any(), any());

    doReturn(new TargetGroup())
        .when(mockAwsElbHelperServiceDelegate)
        .cloneTargetGroup(any(), any(), any(), any(), any());

    doReturn(Optional.empty()).when(mockAwsElbHelperServiceDelegate).getTargetGroupByName(any(), any(), any(), any());

    doReturn(forwardListener)
        .when(mockAwsElbHelperServiceDelegate)
        .createStageListener(any(), any(), any(), any(), anyInt(), any());

    linearClosure(mockCallback, request);

    verify(mockAwsElbHelperServiceDelegate).getElbListener(any(), any(), any(), any());
    verify(mockAwsElbHelperServiceDelegate).createStageListener(any(), any(), any(), any(), anyInt(), any());
  }

  private void linearClosure(ExecutionLogCallback mockCallback, EcsBGServiceSetupRequest request) {
    doReturn(new TaskDefinition())
        .when(mockEcsSetupCommandTaskHelper)
        .createTaskDefinition(any(), any(), any(), any(), any(), any());

    doReturn(SERVICE_ID).when(mockEcsSetupCommandTaskHelper).createEcsService(any(), any(), any(), any(), any(), any());

    EcsCommandExecutionResponse response = handler.executeTaskInternal(request, null, mockCallback);
    verify(mockEcsSetupCommandTaskHelper).deleteExistingServicesOtherThanBlueVersion(any(), any(), any(), any());
    verify(mockEcsSetupCommandTaskHelper).createEcsService(any(), any(), any(), any(), any(), any());

    verify(mockEcsSetupCommandTaskHelper)
        .storeCurrentServiceNameAndCountInfo(eq(request.getAwsConfig()), any(), any(), any(), eq(SERVICE_ID));
    verify(mockEcsSetupCommandTaskHelper).backupAutoScalarConfig(any(), any(), any(), any(), any(), any());
    verify(mockEcsSetupCommandTaskHelper).logLoadBalancerInfo(any(), any());

    assertThat(response).isNotNull();
    assertThat(response.getCommandExecutionStatus()).isEqualTo(SUCCESS);
    assertThat(response.getEcsCommandResponse().getCommandExecutionStatus()).isEqualTo(SUCCESS);
  }
}
