/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl.aws.manager;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.rule.OwnerRule.SATYAM;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.joor.Reflect.on;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;

import software.wings.beans.AwsConfig;
import software.wings.service.impl.aws.model.AwsAsgGetRunningCountData;
import software.wings.service.impl.aws.model.AwsAsgGetRunningCountResponse;
import software.wings.service.impl.aws.model.AwsAsgListAllNamesResponse;
import software.wings.service.impl.aws.model.AwsAsgListDesiredCapacitiesResponse;
import software.wings.service.impl.aws.model.AwsAsgListInstancesResponse;
import software.wings.service.intfc.DelegateService;

import com.amazonaws.services.ec2.model.Instance;
import com.google.common.collect.ImmutableMap;
import java.util.List;
import java.util.Map;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(CDP)
public class AwsAsgHelperServiceManagerImplTest extends CategoryTest {
  @Test
  @Owner(developers = SATYAM)
  @Category(UnitTests.class)
  public void testListAutoScalingGroupNames() throws InterruptedException {
    AwsAsgHelperServiceManagerImpl service = spy(AwsAsgHelperServiceManagerImpl.class);
    DelegateService mockDelegateService = mock(DelegateService.class);
    on(service).set("delegateService", mockDelegateService);
    AwsHelperServiceManager mockHelper = mock(AwsHelperServiceManager.class);
    on(service).set("helper", mockHelper);
    doNothing().when(mockHelper).validateDelegateSuccessForSyncTask(any());
    doReturn(AwsAsgListAllNamesResponse.builder().aSgNames(asList("foo", "bar")).build())
        .when(mockDelegateService)
        .executeTaskV2(any());
    List<String> asgs =
        service.listAutoScalingGroupNames(AwsConfig.builder().build(), emptyList(), "us-east-1", "appId");
    assertThat(asgs).isNotNull();
    assertThat(asgs.size()).isEqualTo(2);
    assertThat(asgs.contains("foo")).isTrue();
    assertThat(asgs.contains("bar")).isTrue();
  }

  @Test
  @Owner(developers = SATYAM)
  @Category(UnitTests.class)
  public void testListAutoScalingGroupInstances() throws InterruptedException {
    AwsAsgHelperServiceManagerImpl service = spy(AwsAsgHelperServiceManagerImpl.class);
    DelegateService mockDelegateService = mock(DelegateService.class);
    on(service).set("delegateService", mockDelegateService);
    AwsHelperServiceManager mockHelper = mock(AwsHelperServiceManager.class);
    on(service).set("helper", mockHelper);
    doNothing().when(mockHelper).validateDelegateSuccessForSyncTask(any());
    doReturn(AwsAsgListInstancesResponse.builder()
                 .instances(asList(new Instance().withInstanceId("id-1234"), new Instance().withInstanceId("id-2345")))
                 .build())
        .when(mockDelegateService)
        .executeTaskV2(any());
    List<Instance> instances =
        service.listAutoScalingGroupInstances(AwsConfig.builder().build(), emptyList(), "us-east-1", "asg", "appId");
    assertThat(instances).isNotNull();
    assertThat(instances.size()).isEqualTo(2);
  }

  @Test
  @Owner(developers = SATYAM)
  @Category(UnitTests.class)
  public void testGetDesiredCapacitiesOfAsgs() throws InterruptedException {
    AwsAsgHelperServiceManagerImpl service = spy(AwsAsgHelperServiceManagerImpl.class);
    DelegateService mockDelegateService = mock(DelegateService.class);
    on(service).set("delegateService", mockDelegateService);
    doReturn(AwsAsgListDesiredCapacitiesResponse.builder().capacities(ImmutableMap.of("asg_1", 1, "asg_2", 1)).build())
        .when(mockDelegateService)
        .executeTaskV2(any());
    AwsHelperServiceManager mockHelper = mock(AwsHelperServiceManager.class);
    on(service).set("helper", mockHelper);
    doNothing().when(mockHelper).validateDelegateSuccessForSyncTask(any());
    Map<String, Integer> capacities = service.getDesiredCapacitiesOfAsgs(
        AwsConfig.builder().build(), emptyList(), "us-east-1", asList("asg_1", "asg_2"), "appId");
    assertThat(capacities).isNotNull();
    assertThat(capacities.size()).isEqualTo(2);
    assertThat(capacities.containsKey("asg_1")).isTrue();
    assertThat(capacities.containsKey("asg_2")).isTrue();
  }

  @Test
  @Owner(developers = SATYAM)
  @Category(UnitTests.class)
  public void testGetCurrentlyRunningInstanceCount() throws InterruptedException {
    AwsAsgHelperServiceManagerImpl service = spy(AwsAsgHelperServiceManagerImpl.class);
    DelegateService mockDelegateService = mock(DelegateService.class);
    on(service).set("delegateService", mockDelegateService);
    doReturn(AwsAsgGetRunningCountResponse.builder()
                 .data(AwsAsgGetRunningCountData.builder().asgName("foo").asgMin(0).asgMax(1).asgDesired(1).build())
                 .build())
        .when(mockDelegateService)
        .executeTaskV2(any());
    AwsHelperServiceManager mockHelper = mock(AwsHelperServiceManager.class);
    on(service).set("helper", mockHelper);
    doNothing().when(mockHelper).validateDelegateSuccessForSyncTask(any());
    AwsAsgGetRunningCountData data = service.getCurrentlyRunningInstanceCount(
        AwsConfig.builder().build(), emptyList(), "us-east-1", "inf-id", "appId");
    assertThat(data).isNotNull();
    assertThat(data.getAsgName()).isEqualTo("foo");
    assertThat(data.getAsgMin()).isEqualTo(0);
    assertThat(data.getAsgMax()).isEqualTo(1);
    assertThat(data.getAsgDesired()).isEqualTo(1);
  }
}
