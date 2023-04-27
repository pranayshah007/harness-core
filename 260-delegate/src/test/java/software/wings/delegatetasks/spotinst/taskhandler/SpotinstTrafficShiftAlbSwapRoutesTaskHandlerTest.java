/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.delegatetasks.spotinst.taskhandler;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.logging.CommandExecutionStatus.SUCCESS;
import static io.harness.rule.OwnerRule.SATYAM;

import static software.wings.service.impl.aws.model.AwsConstants.MAX_TRAFFIC_SHIFT_WEIGHT;

import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.joor.Reflect.on;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.category.element.UnitTests;
import io.harness.delegate.task.aws.LbDetailsForAlbTrafficShift;
import io.harness.delegate.task.spotinst.request.SpotinstTrafficShiftAlbSwapRoutesParameters;
import io.harness.delegate.task.spotinst.response.SpotInstTaskExecutionResponse;
import io.harness.rule.Owner;
import io.harness.spotinst.SpotInstHelperServiceDelegate;
import io.harness.spotinst.model.ElastiGroup;
import io.harness.spotinst.model.ElastiGroupCapacity;

import software.wings.WingsBaseTest;
import software.wings.beans.AwsConfig;
import software.wings.beans.SpotInstConfig;
import software.wings.beans.command.ExecutionLogCallback;
import software.wings.service.intfc.aws.delegate.AwsElbHelperServiceDelegate;

import org.junit.Test;
import org.junit.experimental.categories.Category;

@TargetModule(HarnessModule._930_DELEGATE_TASKS)
@OwnedBy(CDP)
public class SpotinstTrafficShiftAlbSwapRoutesTaskHandlerTest extends WingsBaseTest {
  @Test
  @Owner(developers = SATYAM)
  @Category(UnitTests.class)
  public void testExecuteInternalDeploy() throws Exception {
    SpotinstTrafficShiftAlbSwapRoutesTaskHandler handler = spy(SpotinstTrafficShiftAlbSwapRoutesTaskHandler.class);
    ExecutionLogCallback mockCallback = mock(ExecutionLogCallback.class);
    doNothing().when(mockCallback).saveExecutionLog(any());
    doNothing().when(mockCallback).saveExecutionLog(any(), any(), any());
    doReturn(mockCallback).when(handler).getLogCallBack(any(), any());
    SpotInstHelperServiceDelegate mockHelper = mock(SpotInstHelperServiceDelegate.class);
    on(handler).set("spotInstHelperServiceDelegate", mockHelper);
    AwsElbHelperServiceDelegate mockElbHelper = mock(AwsElbHelperServiceDelegate.class);
    on(handler).set("awsElbHelperServiceDelegate", mockElbHelper);
    doNothing()
        .when(mockElbHelper)
        .updateRulesForAlbTrafficShift(any(), any(), anyList(), any(), any(), anyInt(), any());
    doNothing().when(handler).updateElastiGroupAndWait(any(), any(), any(), anyInt(), any(), any(), any());
    SpotinstTrafficShiftAlbSwapRoutesParameters parameters =
        SpotinstTrafficShiftAlbSwapRoutesParameters.builder()
            .elastigroupNamePrefix("foo")
            .oldElastigroup(ElastiGroup.builder()
                                .id("oldId")
                                .name("foo")
                                .capacity(ElastiGroupCapacity.builder().minimum(0).maximum(1).target(1).build())
                                .build())
            .newElastigroup(ElastiGroup.builder()
                                .id("newId")
                                .name("foo__STAGE__Harness")
                                .capacity(ElastiGroupCapacity.builder().minimum(0).maximum(1).target(1).build())
                                .build())
            .newElastigroupWeight(MAX_TRAFFIC_SHIFT_WEIGHT)
            .details(singletonList(LbDetailsForAlbTrafficShift.builder().build()))
            .downsizeOldElastigroup(true)
            .timeoutIntervalInMin(10)
            .rollback(false)
            .build();
    SpotInstTaskExecutionResponse response = handler.executeTaskInternal(parameters,
        SpotInstConfig.builder().spotInstAccountId("SPOTINST_ACCOUNT_ID").spotInstToken(new char[] {'a', 'b'}).build(),
        AwsConfig.builder().build());
    verify(mockElbHelper).updateRulesForAlbTrafficShift(any(), any(), anyList(), anyList(), any(), anyInt(), any());
    assertThat(response).isNotNull();
    assertThat(response.getCommandExecutionStatus()).isEqualTo(SUCCESS);
  }

  @Test
  @Owner(developers = SATYAM)
  @Category(UnitTests.class)
  public void testExecuteInternalRollback() throws Exception {
    SpotinstTrafficShiftAlbSwapRoutesTaskHandler handler = spy(SpotinstTrafficShiftAlbSwapRoutesTaskHandler.class);
    ExecutionLogCallback mockCallback = mock(ExecutionLogCallback.class);
    doNothing().when(mockCallback).saveExecutionLog(any());
    doNothing().when(mockCallback).saveExecutionLog(any(), any(), any());
    doReturn(mockCallback).when(handler).getLogCallBack(any(), any());
    SpotInstHelperServiceDelegate mockHelper = mock(SpotInstHelperServiceDelegate.class);
    on(handler).set("spotInstHelperServiceDelegate", mockHelper);
    AwsElbHelperServiceDelegate mockElbHelper = mock(AwsElbHelperServiceDelegate.class);
    on(handler).set("awsElbHelperServiceDelegate", mockElbHelper);
    doNothing()
        .when(mockElbHelper)
        .updateRulesForAlbTrafficShift(any(), any(), anyList(), any(), any(), anyInt(), any());
    doNothing().when(handler).updateElastiGroupAndWait(any(), any(), any(), anyInt(), any(), any(), any());
    SpotinstTrafficShiftAlbSwapRoutesParameters parameters =
        SpotinstTrafficShiftAlbSwapRoutesParameters.builder()
            .elastigroupNamePrefix("foo")
            .oldElastigroup(ElastiGroup.builder()
                                .id("oldId")
                                .name("foo")
                                .capacity(ElastiGroupCapacity.builder().minimum(0).maximum(1).target(1).build())
                                .build())
            .newElastigroup(ElastiGroup.builder()
                                .id("newId")
                                .name("foo__STAGE__Harness")
                                .capacity(ElastiGroupCapacity.builder().minimum(0).maximum(1).target(1).build())
                                .build())
            .newElastigroupWeight(MAX_TRAFFIC_SHIFT_WEIGHT)
            .details(singletonList(LbDetailsForAlbTrafficShift.builder().build()))
            .timeoutIntervalInMin(10)
            .rollback(true)
            .build();
    SpotInstTaskExecutionResponse response = handler.executeTaskInternal(parameters,
        SpotInstConfig.builder().spotInstAccountId("SPOTINST_ACCOUNT_ID").spotInstToken(new char[] {'a', 'b'}).build(),
        AwsConfig.builder().build());
    verify(mockElbHelper).updateRulesForAlbTrafficShift(any(), any(), anyList(), anyList(), any(), anyInt(), any());
    assertThat(response).isNotNull();
    assertThat(response.getCommandExecutionStatus()).isEqualTo(SUCCESS);
  }
}
