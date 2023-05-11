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
import software.wings.service.impl.aws.model.AwsCodeDeployListAppRequest;
import software.wings.service.impl.aws.model.AwsCodeDeployListAppRevisionRequest;
import software.wings.service.impl.aws.model.AwsCodeDeployListDeploymentConfigRequest;
import software.wings.service.impl.aws.model.AwsCodeDeployListDeploymentGroupRequest;
import software.wings.service.impl.aws.model.AwsCodeDeployListDeploymentInstancesRequest;
import software.wings.service.impl.aws.model.AwsCodeDeployRequest;
import software.wings.service.intfc.aws.delegate.AwsCodeDeployHelperServiceDelegate;

import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;

@TargetModule(HarnessModule._930_DELEGATE_TASKS)
@OwnedBy(CDP)
public class AwsCodeDeployTaskTest extends WingsBaseTest {
  @Mock private AwsCodeDeployHelperServiceDelegate mockAwsCodeDeployHelperServiceDelegate;

  @InjectMocks
  private AwsCodeDeployTask task =
      new AwsCodeDeployTask(DelegateTaskPackage.builder()
                                .delegateId("delegateid")
                                .data(TaskData.builder().async(true).timeout(DEFAULT_ASYNC_CALL_TIMEOUT).build())
                                .build(),
          null, notifyResponseData -> {}, () -> true);

  @Before
  public void setUp() throws Exception {
    on(task).set("awsCodeDeployHelperServiceDelegate", mockAwsCodeDeployHelperServiceDelegate);
  }

  @Test
  @Owner(developers = SATYAM)
  @Category(UnitTests.class)
  public void testRun() {
    AwsCodeDeployRequest request = AwsCodeDeployListAppRequest.builder().build();
    task.run(new Object[] {request});
    verify(mockAwsCodeDeployHelperServiceDelegate).listApplications(any(), any(), any());
    request = AwsCodeDeployListDeploymentConfigRequest.builder().build();
    task.run(new Object[] {request});
    verify(mockAwsCodeDeployHelperServiceDelegate).listDeploymentConfiguration(any(), any(), any());
    request = AwsCodeDeployListDeploymentGroupRequest.builder().build();
    task.run(new Object[] {request});
    verify(mockAwsCodeDeployHelperServiceDelegate).listDeploymentGroups(any(), any(), any(), any());
    request = AwsCodeDeployListDeploymentInstancesRequest.builder().build();
    task.run(new Object[] {request});
    verify(mockAwsCodeDeployHelperServiceDelegate).listDeploymentInstances(any(), any(), any(), any());
    request = AwsCodeDeployListAppRevisionRequest.builder().build();
    task.run(new Object[] {request});
    verify(mockAwsCodeDeployHelperServiceDelegate).listAppRevision(any(), any(), any(), any(), any());
  }
}
