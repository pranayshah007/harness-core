/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.delegatetasks.spotinst.taskhandler;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.rule.OwnerRule.SATYAM;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.category.element.UnitTests;
import io.harness.delegate.task.spotinst.request.SpotInstGetElastigroupJsonParameters;
import io.harness.delegate.task.spotinst.request.SpotInstListElastigroupInstancesParameters;
import io.harness.delegate.task.spotinst.request.SpotInstListElastigroupNamesParameters;
import io.harness.delegate.task.spotinst.request.SpotInstTaskParameters;
import io.harness.delegate.task.spotinst.response.SpotInstGetElastigroupJsonResponse;
import io.harness.delegate.task.spotinst.response.SpotInstListElastigroupInstancesResponse;
import io.harness.delegate.task.spotinst.response.SpotInstListElastigroupNamesResponse;
import io.harness.delegate.task.spotinst.response.SpotInstTaskExecutionResponse;
import io.harness.delegate.task.spotinst.response.SpotInstTaskResponse;
import io.harness.rule.Owner;
import io.harness.spotinst.SpotInstHelperServiceDelegate;
import io.harness.spotinst.model.ElastiGroup;

import software.wings.WingsBaseTest;
import software.wings.beans.AwsConfig;
import software.wings.beans.SpotInstConfig;
import software.wings.delegatetasks.DelegateLogService;
import software.wings.service.intfc.aws.delegate.AwsEc2HelperServiceDelegate;
import software.wings.service.intfc.aws.delegate.AwsElbHelperServiceDelegate;

import com.amazonaws.services.ec2.model.Instance;
import com.google.common.util.concurrent.TimeLimiter;
import com.google.inject.Inject;
import java.util.Collections;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;

@TargetModule(HarnessModule._930_DELEGATE_TASKS)
@OwnedBy(CDP)
public class SpotInstSyncTaskHandlerTest extends WingsBaseTest {
  @Mock private DelegateLogService mockDelegateLogService;
  @Mock private SpotInstHelperServiceDelegate mockSpotInstHelperServiceDelegate;
  @Mock private AwsElbHelperServiceDelegate mockAwsElbHelperServiceDelegate;
  @Mock private TimeLimiter mockTimeLimiter;
  @Mock private AwsEc2HelperServiceDelegate mockAwsEc2HelperServiceDelegate;

  @Spy @Inject @InjectMocks SpotInstSyncTaskHandler handler;

  @Test
  @Owner(developers = SATYAM)
  @Category(UnitTests.class)
  public void testListElastigroups() throws Exception {
    doReturn(Collections.singletonList(ElastiGroup.builder().id("id").build()))
        .when(mockSpotInstHelperServiceDelegate)
        .listAllElstiGroups(anyString(), anyString());
    SpotInstTaskParameters parameters = SpotInstListElastigroupNamesParameters.builder().build();
    SpotInstTaskExecutionResponse response = handler.executeTaskInternal(parameters,
        SpotInstConfig.builder().spotInstAccountId("SPOTINST_ACCOUNT_ID").spotInstToken(new char[] {'a', 'b'}).build(),
        AwsConfig.builder().build());
    assertThat(response).isNotNull();
    SpotInstTaskResponse spotInstTaskResponse = response.getSpotInstTaskResponse();
    assertThat(spotInstTaskResponse).isNotNull();
    assertThat(spotInstTaskResponse instanceof SpotInstListElastigroupNamesResponse).isTrue();
    SpotInstListElastigroupNamesResponse listElastigroupNamesResponse =
        (SpotInstListElastigroupNamesResponse) spotInstTaskResponse;
    assertThat(listElastigroupNamesResponse.getElastigroups().size()).isEqualTo(1);
    assertThat(listElastigroupNamesResponse.getElastigroups().get(0).getId()).isEqualTo("id");
  }

  @Test
  @Owner(developers = SATYAM)
  @Category(UnitTests.class)
  public void testgetElastigroupJson() throws Exception {
    doReturn("JSON").when(mockSpotInstHelperServiceDelegate).getElastigroupJson(anyString(), anyString(), anyString());
    SpotInstTaskParameters parameters = SpotInstGetElastigroupJsonParameters.builder().elastigroupId("id").build();
    SpotInstTaskExecutionResponse response = handler.executeTaskInternal(parameters,
        SpotInstConfig.builder().spotInstAccountId("SPOTINST_ACCOUNT_ID").spotInstToken(new char[] {'a', 'b'}).build(),
        AwsConfig.builder().build());
    assertThat(response).isNotNull();
    SpotInstTaskResponse spotInstTaskResponse = response.getSpotInstTaskResponse();
    assertThat(spotInstTaskResponse).isNotNull();
    assertThat(spotInstTaskResponse instanceof SpotInstGetElastigroupJsonResponse).isTrue();
    SpotInstGetElastigroupJsonResponse jsonResponse = (SpotInstGetElastigroupJsonResponse) spotInstTaskResponse;
    assertThat(jsonResponse.getElastigroupJson()).isEqualTo("JSON");
  }

  @Test
  @Owner(developers = SATYAM)
  @Category(UnitTests.class)
  public void testgetElastigroupInstances() throws Exception {
    doReturn(Collections.singletonList(new Instance().withInstanceId("id-1234")))
        .when(handler)
        .getAllEc2InstancesOfElastiGroup(any(), anyString(), anyString(), anyString(), anyString());
    SpotInstListElastigroupInstancesParameters parameters =
        SpotInstListElastigroupInstancesParameters.builder().elastigroupId("id").awsRegion("west-1").build();
    SpotInstTaskExecutionResponse response = handler.executeTaskInternal(parameters,
        SpotInstConfig.builder().spotInstAccountId("SPOTINST_ACCOUNT_ID").spotInstToken(new char[] {'a', 'b'}).build(),
        AwsConfig.builder().build());
    assertThat(response).isNotNull();
    SpotInstTaskResponse spotInstTaskResponse = response.getSpotInstTaskResponse();
    assertThat(spotInstTaskResponse).isNotNull();
    assertThat(spotInstTaskResponse instanceof SpotInstListElastigroupInstancesResponse).isTrue();
    SpotInstListElastigroupInstancesResponse instancesResponse =
        (SpotInstListElastigroupInstancesResponse) spotInstTaskResponse;
    assertThat(instancesResponse.getElastigroupInstances().size()).isEqualTo(1);
    assertThat(instancesResponse.getElastigroupInstances().get(0).getInstanceId()).isEqualTo("id-1234");
  }
}
