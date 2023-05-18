/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.delegatetasks.spotinst.taskhandler;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.rule.OwnerRule.ARVIND;
import static io.harness.rule.OwnerRule.SATYAM;
import static io.harness.spotinst.model.SpotInstConstants.DEFAULT_ELASTIGROUP_MAX_INSTANCES;
import static io.harness.spotinst.model.SpotInstConstants.DEFAULT_ELASTIGROUP_MIN_INSTANCES;
import static io.harness.spotinst.model.SpotInstConstants.DEFAULT_ELASTIGROUP_TARGET_INSTANCES;

import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.category.element.UnitTests;
import io.harness.delegate.task.spotinst.request.SpotInstDeployTaskParameters;
import io.harness.delegate.task.spotinst.response.SpotInstDeployTaskResponse;
import io.harness.delegate.task.spotinst.response.SpotInstTaskExecutionResponse;
import io.harness.delegate.task.spotinst.response.SpotInstTaskResponse;
import io.harness.rule.Owner;
import io.harness.spotinst.SpotInstHelperServiceDelegate;
import io.harness.spotinst.model.ElastiGroup;
import io.harness.spotinst.model.ElastiGroupCapacity;

import software.wings.WingsBaseTest;
import software.wings.beans.AwsConfig;
import software.wings.beans.SpotInstConfig;
import software.wings.beans.command.ExecutionLogCallback;
import software.wings.delegatetasks.DelegateLogService;
import software.wings.service.intfc.aws.delegate.AwsEc2HelperServiceDelegate;
import software.wings.service.intfc.aws.delegate.AwsElbHelperServiceDelegate;

import com.amazonaws.services.ec2.model.Instance;
import com.google.common.util.concurrent.TimeLimiter;
import com.google.inject.Inject;
import java.util.Optional;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;

@TargetModule(HarnessModule._930_DELEGATE_TASKS)
@OwnedBy(CDP)
public class SpotInstDeployTaskHandlerTest extends WingsBaseTest {
  @Mock private DelegateLogService mockDelegateLogService;
  @Mock private SpotInstHelperServiceDelegate mockSpotInstHelperServiceDelegate;
  @Mock private AwsElbHelperServiceDelegate mockAwsElbHelperServiceDelegate;
  @Mock private TimeLimiter mockTimeLimiter;
  @Mock private AwsEc2HelperServiceDelegate mockAwsEc2HelperServiceDelegate;

  @Spy @Inject @InjectMocks SpotInstDeployTaskHandler deployTaskHandler;

  @Test
  @Owner(developers = SATYAM)
  @Category(UnitTests.class)
  public void testScaleElastigroup() throws Exception {
    doNothing().when(deployTaskHandler).updateElastiGroupAndWait(any(), any(), any(), anyInt(), any(), any(), any());
    ElastiGroup group = ElastiGroup.builder().build();
    deployTaskHandler.scaleElastigroup(
        group, "TOKEN", "ACCOUNT_ID", 5, SpotInstDeployTaskParameters.builder().build(), "SCALE", "WAIT");
    verify(deployTaskHandler).updateElastiGroupAndWait(any(), any(), any(), anyInt(), any(), any(), any());
  }

  @Test
  @Owner(developers = ARVIND)
  @Category(UnitTests.class)
  public void testScaleElastiGroupCapacityUpdate() throws Exception {
    ExecutionLogCallback mockCallback = mock(ExecutionLogCallback.class);
    doReturn(mockCallback).when(deployTaskHandler).getLogCallBack(any(), any());
    doNothing().when(mockCallback).saveExecutionLog(any());
    doNothing().when(mockCallback).saveExecutionLog(any(), any(), any());

    doReturn(Optional.of(ElastiGroup.builder().capacity(ElastiGroupCapacity.builder().build()).build()))
        .when(mockSpotInstHelperServiceDelegate)
        .getElastiGroupById(any(), any(), any());

    doNothing().when(mockSpotInstHelperServiceDelegate).updateElastiGroupCapacity(any(), any(), any(), any());

    ElastiGroup elastiGroup1 = ElastiGroup.builder()
                                   .id("Id")
                                   .name("Name")
                                   .capacity(ElastiGroupCapacity.builder().minimum(3).maximum(1).target(2).build())
                                   .build();
    deployTaskHandler.scaleElastigroup(
        elastiGroup1, "TOKEN", "ACCOUNT_ID", 5, SpotInstDeployTaskParameters.builder().build(), "SCALE", "WAIT");

    assertThat(elastiGroup1.getCapacity().getMinimum()).isEqualTo(2);
    assertThat(elastiGroup1.getCapacity().getTarget()).isEqualTo(2);
    assertThat(elastiGroup1.getCapacity().getMaximum()).isEqualTo(2);

    ElastiGroup elastiGroup2 = ElastiGroup.builder()
                                   .id("Id")
                                   .name("Name")
                                   .capacity(ElastiGroupCapacity.builder().minimum(3).maximum(1).target(-1).build())
                                   .build();
    deployTaskHandler.scaleElastigroup(
        elastiGroup2, "TOKEN", "ACCOUNT_ID", 5, SpotInstDeployTaskParameters.builder().build(), "SCALE", "WAIT");

    assertThat(elastiGroup2.getCapacity().getMinimum()).isEqualTo(DEFAULT_ELASTIGROUP_MIN_INSTANCES);
    assertThat(elastiGroup2.getCapacity().getTarget()).isEqualTo(DEFAULT_ELASTIGROUP_TARGET_INSTANCES);
    assertThat(elastiGroup2.getCapacity().getMaximum()).isEqualTo(DEFAULT_ELASTIGROUP_MAX_INSTANCES);

    ElastiGroup elastiGroup3 = ElastiGroup.builder()
                                   .id("Id")
                                   .name("Name")
                                   .capacity(ElastiGroupCapacity.builder().minimum(30).maximum(100).target(20).build())
                                   .build();
    deployTaskHandler.scaleElastigroup(
        elastiGroup3, "TOKEN", "ACCOUNT_ID", 5, SpotInstDeployTaskParameters.builder().build(), "SCALE", "WAIT");

    assertThat(elastiGroup3.getCapacity().getMinimum()).isEqualTo(20);
    assertThat(elastiGroup3.getCapacity().getTarget()).isEqualTo(20);
    assertThat(elastiGroup3.getCapacity().getMaximum()).isEqualTo(100);

    ElastiGroup elastiGroup4 = ElastiGroup.builder()
                                   .id("Id")
                                   .name("Name")
                                   .capacity(ElastiGroupCapacity.builder().minimum(5).maximum(10).target(20).build())
                                   .build();
    deployTaskHandler.scaleElastigroup(
        elastiGroup4, "TOKEN", "ACCOUNT_ID", 5, SpotInstDeployTaskParameters.builder().build(), "SCALE", "WAIT");

    assertThat(elastiGroup4.getCapacity().getMinimum()).isEqualTo(5);
    assertThat(elastiGroup4.getCapacity().getTarget()).isEqualTo(20);
    assertThat(elastiGroup4.getCapacity().getMaximum()).isEqualTo(20);

    ElastiGroup elastiGroup5 = ElastiGroup.builder()
                                   .id("Id")
                                   .name("Name")
                                   .capacity(ElastiGroupCapacity.builder().minimum(5).maximum(10).target(7).build())
                                   .build();
    deployTaskHandler.scaleElastigroup(
        elastiGroup5, "TOKEN", "ACCOUNT_ID", 5, SpotInstDeployTaskParameters.builder().build(), "SCALE", "WAIT");

    assertThat(elastiGroup5.getCapacity().getMinimum()).isEqualTo(5);
    assertThat(elastiGroup5.getCapacity().getTarget()).isEqualTo(7);
    assertThat(elastiGroup5.getCapacity().getMaximum()).isEqualTo(10);
  }

  @Test
  @Owner(developers = SATYAM)
  @Category(UnitTests.class)
  public void testScaleElastigroupNull() throws Exception {
    doNothing().when(deployTaskHandler).createAndFinishEmptyExecutionLog(any(), any(), any());
    deployTaskHandler.scaleElastigroup(
        null, "TOKEN", "ACCOUNT_ID", 5, SpotInstDeployTaskParameters.builder().build(), "SCALE", "WAIT");
    verify(deployTaskHandler, times(2)).createAndFinishEmptyExecutionLog(any(), any(), any());
  }

  @Test
  @Owner(developers = SATYAM)
  @Category(UnitTests.class)
  public void testExecuteTaskInternal() throws Exception {
    ElastiGroup newElastigroup = ElastiGroup.builder()
                                     .id("newId")
                                     .name("foo__2")
                                     .capacity(ElastiGroupCapacity.builder().minimum(1).maximum(1).target(1).build())
                                     .build();
    ElastiGroup oldElastigroup = ElastiGroup.builder()
                                     .id("oldId")
                                     .name("foo__1")
                                     .capacity(ElastiGroupCapacity.builder().minimum(1).maximum(1).target(1).build())
                                     .build();
    doNothing().when(deployTaskHandler).scaleElastigroup(any(), any(), any(), anyInt(), any(), any(), any());
    doReturn(singletonList(new Instance().withInstanceId("id-new")))
        .doReturn(singletonList(new Instance().withInstanceId("id-old")))
        .when(deployTaskHandler)
        .getAllEc2InstancesOfElastiGroup(any(), any(), any(), any(), any());
    SpotInstDeployTaskParameters parameters = SpotInstDeployTaskParameters.builder()
                                                  .newElastiGroupWithUpdatedCapacity(newElastigroup)
                                                  .oldElastiGroupWithUpdatedCapacity(oldElastigroup)
                                                  .blueGreen(false)
                                                  .timeoutIntervalInMin(10)
                                                  .rollback(false)
                                                  .resizeNewFirst(true)
                                                  .build();
    SpotInstTaskExecutionResponse response = deployTaskHandler.executeTaskInternal(parameters,
        SpotInstConfig.builder().spotInstAccountId("SPOTINST_ACCOUNT_ID").spotInstToken(new char[] {'a', 'b'}).build(),
        AwsConfig.builder().build());
    assertThat(response).isNotNull();
    SpotInstTaskResponse spotInstTaskResponse = response.getSpotInstTaskResponse();
    assertThat(spotInstTaskResponse).isNotNull();
    assertThat(spotInstTaskResponse instanceof SpotInstDeployTaskResponse).isTrue();
    SpotInstDeployTaskResponse deployTaskResponse = (SpotInstDeployTaskResponse) spotInstTaskResponse;
    assertThat(deployTaskResponse.getEc2InstancesAdded().size()).isEqualTo(1);
    assertThat(deployTaskResponse.getEc2InstancesExisting().size()).isEqualTo(1);
  }

  @Test
  @Owner(developers = SATYAM)
  @Category(UnitTests.class)
  public void testExecuteTaskInternalResizeOldFirst() throws Exception {
    ElastiGroup newElastigroup = ElastiGroup.builder()
                                     .id("newId")
                                     .name("foo__2")
                                     .capacity(ElastiGroupCapacity.builder().minimum(1).maximum(1).target(1).build())
                                     .build();
    ElastiGroup oldElastigroup = ElastiGroup.builder()
                                     .id("oldId")
                                     .name("foo__1")
                                     .capacity(ElastiGroupCapacity.builder().minimum(1).maximum(1).target(1).build())
                                     .build();
    doNothing().when(deployTaskHandler).scaleElastigroup(any(), any(), any(), anyInt(), any(), any(), any());
    doReturn(singletonList(new Instance().withInstanceId("id-new")))
        .doReturn(singletonList(new Instance().withInstanceId("id-old")))
        .when(deployTaskHandler)
        .getAllEc2InstancesOfElastiGroup(any(), any(), any(), any(), any());
    SpotInstDeployTaskParameters parameters = SpotInstDeployTaskParameters.builder()
                                                  .newElastiGroupWithUpdatedCapacity(newElastigroup)
                                                  .oldElastiGroupWithUpdatedCapacity(oldElastigroup)
                                                  .timeoutIntervalInMin(10)
                                                  .blueGreen(false)
                                                  .rollback(false)
                                                  .resizeNewFirst(false)
                                                  .build();
    SpotInstTaskExecutionResponse response = deployTaskHandler.executeTaskInternal(parameters,
        SpotInstConfig.builder().spotInstAccountId("SPOTINST_ACCOUNT_ID").spotInstToken(new char[] {'a', 'b'}).build(),
        AwsConfig.builder().build());
    assertThat(response).isNotNull();
    SpotInstTaskResponse spotInstTaskResponse = response.getSpotInstTaskResponse();
    assertThat(spotInstTaskResponse).isNotNull();
    assertThat(spotInstTaskResponse instanceof SpotInstDeployTaskResponse).isTrue();
    SpotInstDeployTaskResponse deployTaskResponse = (SpotInstDeployTaskResponse) spotInstTaskResponse;
    assertThat(deployTaskResponse.getEc2InstancesAdded().size()).isEqualTo(1);
    assertThat(deployTaskResponse.getEc2InstancesExisting().size()).isEqualTo(1);
  }

  @Test
  @Owner(developers = SATYAM)
  @Category(UnitTests.class)
  public void testExecuteTaskInternalRollback() throws Exception {
    ElastiGroup newElastigroup = ElastiGroup.builder()
                                     .id("newId")
                                     .name("foo__2")
                                     .capacity(ElastiGroupCapacity.builder().minimum(0).maximum(0).target(0).build())
                                     .build();
    ElastiGroup oldElastigroup = ElastiGroup.builder()
                                     .id("oldId")
                                     .name("foo__1")
                                     .capacity(ElastiGroupCapacity.builder().minimum(1).maximum(1).target(1).build())
                                     .build();
    doNothing().when(deployTaskHandler).scaleElastigroup(any(), any(), any(), anyInt(), any(), any(), any());
    doReturn(singletonList(new Instance().withInstanceId("id-old")))
        .when(deployTaskHandler)
        .getAllEc2InstancesOfElastiGroup(any(), any(), any(), any(), any());
    SpotInstDeployTaskParameters parameters = SpotInstDeployTaskParameters.builder()
                                                  .newElastiGroupWithUpdatedCapacity(newElastigroup)
                                                  .oldElastiGroupWithUpdatedCapacity(oldElastigroup)
                                                  .blueGreen(false)
                                                  .timeoutIntervalInMin(10)
                                                  .rollback(true)
                                                  .build();
    ExecutionLogCallback mockCallback = mock(ExecutionLogCallback.class);
    doNothing().when(mockCallback).saveExecutionLog(any());
    doNothing().when(mockCallback).saveExecutionLog(any(), any(), any());
    doReturn(mockCallback).when(deployTaskHandler).getLogCallBack(any(), any());
    SpotInstTaskExecutionResponse response = deployTaskHandler.executeTaskInternal(parameters,
        SpotInstConfig.builder().spotInstAccountId("SPOTINST_ACCOUNT_ID").spotInstToken(new char[] {'a', 'b'}).build(),
        AwsConfig.builder().build());
    assertThat(response).isNotNull();
    SpotInstTaskResponse spotInstTaskResponse = response.getSpotInstTaskResponse();
    assertThat(spotInstTaskResponse).isNotNull();
    assertThat(spotInstTaskResponse instanceof SpotInstDeployTaskResponse).isTrue();
    SpotInstDeployTaskResponse deployTaskResponse = (SpotInstDeployTaskResponse) spotInstTaskResponse;
    assertThat(deployTaskResponse.getEc2InstancesAdded().size()).isEqualTo(0);
    assertThat(deployTaskResponse.getEc2InstancesExisting().size()).isEqualTo(1);
    verify(mockSpotInstHelperServiceDelegate).deleteElastiGroup(any(), any(), any());
  }
}
