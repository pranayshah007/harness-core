/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.delegatetasks.aws;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.delegate.beans.TaskData.DEFAULT_ASYNC_CALL_TIMEOUT;
import static io.harness.rule.OwnerRule.SATYAM;

import static org.joor.Reflect.on;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.category.element.UnitTests;
import io.harness.delegate.beans.DelegateTaskPackage;
import io.harness.delegate.beans.TaskData;
import io.harness.rule.Owner;

import software.wings.WingsBaseTest;
import software.wings.service.impl.aws.model.AwsElbListAppElbsRequest;
import software.wings.service.impl.aws.model.AwsElbListClassicElbsRequest;
import software.wings.service.impl.aws.model.AwsElbListElbsRequest;
import software.wings.service.impl.aws.model.AwsElbListListenerRequest;
import software.wings.service.impl.aws.model.AwsElbListNetworkElbsRequest;
import software.wings.service.impl.aws.model.AwsElbListTargetGroupsRequest;
import software.wings.service.impl.aws.model.AwsElbRequest;
import software.wings.service.intfc.aws.delegate.AwsElbHelperServiceDelegate;

import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;

@TargetModule(HarnessModule._930_DELEGATE_TASKS)
@OwnedBy(CDP)
public class AwsElbTaskTest extends WingsBaseTest {
  @Mock private AwsElbHelperServiceDelegate mockElbHelperServiceDelegate;

  @InjectMocks
  private AwsElbTask task =
      new AwsElbTask(DelegateTaskPackage.builder()
                         .delegateId("delegateid")
                         .data(TaskData.builder().async(true).timeout(DEFAULT_ASYNC_CALL_TIMEOUT).build())
                         .build(),
          null, notifyResponseData -> {}, () -> true);

  @Before
  public void setUp() throws Exception {
    on(task).set("elbHelperServiceDelegate", mockElbHelperServiceDelegate);
  }

  @Test
  @Owner(developers = SATYAM)
  @Category(UnitTests.class)
  public void testRun() {
    AwsElbRequest request = AwsElbListClassicElbsRequest.builder().build();
    task.run(new Object[] {request});
    verify(mockElbHelperServiceDelegate).listClassicLoadBalancers(any(), any(), any());
    request = AwsElbListAppElbsRequest.builder().build();
    task.run(new Object[] {request});
    verify(mockElbHelperServiceDelegate).listApplicationLoadBalancerDetails(any(), any(), any());
    request = AwsElbListTargetGroupsRequest.builder().build();
    task.run(new Object[] {request});
    verify(mockElbHelperServiceDelegate).listTargetGroupsForAlb(any(), any(), any(), any());
    request = AwsElbListElbsRequest.builder().build();
    task.run(new Object[] {request});
    verify(mockElbHelperServiceDelegate).listElasticLoadBalancerDetails(any(), any(), any());
    request = AwsElbListNetworkElbsRequest.builder().build();
    task.run(new Object[] {request});
    verify(mockElbHelperServiceDelegate).listNetworkLoadBalancerDetails(any(), any(), any());
    request = AwsElbListListenerRequest.builder().build();
    task.run(new Object[] {request});
    verify(mockElbHelperServiceDelegate).getElbListenersForLoadBalaner(any(), any(), any(), any());
  }
}
