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
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.verify;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.category.element.UnitTests;
import io.harness.delegate.beans.DelegateTaskPackage;
import io.harness.delegate.beans.TaskData;
import io.harness.rule.Owner;

import software.wings.WingsBaseTest;
import software.wings.delegatetasks.DelegateLogService;
import software.wings.service.impl.aws.model.AwsAmiRequest;
import software.wings.service.impl.aws.model.AwsAmiServiceDeployRequest;
import software.wings.service.impl.aws.model.AwsAmiServiceSetupRequest;
import software.wings.service.impl.aws.model.AwsAmiServiceTrafficShiftAlbDeployRequest;
import software.wings.service.impl.aws.model.AwsAmiServiceTrafficShiftAlbSetupRequest;
import software.wings.service.impl.aws.model.AwsAmiSwitchRoutesRequest;
import software.wings.service.impl.aws.model.AwsAmiTrafficShiftAlbSwitchRouteRequest;
import software.wings.service.intfc.aws.delegate.AwsAmiHelperServiceDelegate;

import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;

@TargetModule(HarnessModule._930_DELEGATE_TASKS)
@OwnedBy(CDP)
public class AwsAmiAsyncTaskTest extends WingsBaseTest {
  @Mock private DelegateLogService mockDelegateLogService;
  @Mock private AwsAmiHelperServiceDelegate mockAwsAmiHelperServiceDelegate;

  @InjectMocks
  private AwsAmiAsyncTask task =
      new AwsAmiAsyncTask(DelegateTaskPackage.builder()
                              .delegateId("delegateid")
                              .data(TaskData.builder().async(true).timeout(DEFAULT_ASYNC_CALL_TIMEOUT).build())
                              .build(),
          null, notifyResponseData -> {}, () -> true);

  @Before
  public void setUp() throws Exception {
    on(task).set("delegateLogService", mockDelegateLogService);
    on(task).set("awsAmiHelperServiceDelegate", mockAwsAmiHelperServiceDelegate);
  }

  @Test
  @Owner(developers = SATYAM)
  @Category(UnitTests.class)
  public void testRun() {
    AwsAmiRequest request = AwsAmiServiceSetupRequest.builder().build();
    task.run(new Object[] {request});
    verify(mockAwsAmiHelperServiceDelegate).setUpAmiService(any(), any());
    request = AwsAmiServiceDeployRequest.builder().build();
    task.run(new Object[] {request});
    verify(mockAwsAmiHelperServiceDelegate).deployAmiService(any(), any());
    request = AwsAmiSwitchRoutesRequest.builder().rollback(false).build();
    task.run(new Object[] {request});
    verify(mockAwsAmiHelperServiceDelegate).switchAmiRoutes(any(), any());
    request = AwsAmiSwitchRoutesRequest.builder().rollback(true).build();
    task.run(new Object[] {request});
    verify(mockAwsAmiHelperServiceDelegate).rollbackSwitchAmiRoutes(any(), any());

    request = AwsAmiServiceTrafficShiftAlbSetupRequest.builder().build();
    task.run(new Object[] {request});
    verify(mockAwsAmiHelperServiceDelegate).setUpAmiServiceTrafficShift(any());
    request = AwsAmiServiceTrafficShiftAlbDeployRequest.builder().build();
    task.run(new Object[] {request});
    verify(mockAwsAmiHelperServiceDelegate).deployAmiServiceTrafficShift(any());
    request = AwsAmiTrafficShiftAlbSwitchRouteRequest.builder().rollback(false).build();
    task.run(new Object[] {request});
    verify(mockAwsAmiHelperServiceDelegate).switchAmiRoutesTrafficShift(any());
    request = AwsAmiTrafficShiftAlbSwitchRouteRequest.builder().rollback(true).build();
    task.run(new Object[] {request});
    verify(mockAwsAmiHelperServiceDelegate).rollbackSwitchAmiRoutesTrafficShift(any());
  }
}
