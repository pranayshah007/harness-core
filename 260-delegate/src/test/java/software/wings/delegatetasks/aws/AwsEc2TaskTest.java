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
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.verify;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.category.element.UnitTests;
import io.harness.delegate.beans.DelegateTaskPackage;
import io.harness.delegate.beans.TaskData;
import io.harness.rule.Owner;

import software.wings.WingsBaseTest;
import software.wings.service.impl.aws.model.AwsEc2ListInstancesRequest;
import software.wings.service.impl.aws.model.AwsEc2ListRegionsRequest;
import software.wings.service.impl.aws.model.AwsEc2ListSGsRequest;
import software.wings.service.impl.aws.model.AwsEc2ListSubnetsRequest;
import software.wings.service.impl.aws.model.AwsEc2ListTagsRequest;
import software.wings.service.impl.aws.model.AwsEc2ListVpcsRequest;
import software.wings.service.impl.aws.model.AwsEc2Request;
import software.wings.service.impl.aws.model.AwsEc2ValidateCredentialsRequest;
import software.wings.service.intfc.aws.delegate.AwsEc2HelperServiceDelegate;

import java.util.Collections;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;

@TargetModule(HarnessModule._930_DELEGATE_TASKS)
@OwnedBy(CDP)
public class AwsEc2TaskTest extends WingsBaseTest {
  @Mock private AwsEc2HelperServiceDelegate mockEc2ServiceDelegate;

  @InjectMocks
  private AwsEc2Task task =
      new AwsEc2Task(DelegateTaskPackage.builder()
                         .delegateId("delegateid")
                         .data(TaskData.builder().async(true).timeout(DEFAULT_ASYNC_CALL_TIMEOUT).build())
                         .build(),
          null, notifyResponseData -> {}, () -> true);

  @Before
  public void setUp() throws Exception {
    on(task).set("ec2ServiceDelegate", mockEc2ServiceDelegate);
  }

  @Test
  @Owner(developers = SATYAM)
  @Category(UnitTests.class)
  public void testRun() {
    AwsEc2Request request = AwsEc2ValidateCredentialsRequest.builder().build();
    task.run(new Object[] {request});
    verify(mockEc2ServiceDelegate).validateAwsAccountCredential(any(), any());
    request = AwsEc2ListRegionsRequest.builder().build();
    task.run(new Object[] {request});
    verify(mockEc2ServiceDelegate).listRegions(any(), any());
    request = AwsEc2ListVpcsRequest.builder().build();
    task.run(new Object[] {request});
    verify(mockEc2ServiceDelegate).listVPCs(any(), any(), any());
    request = AwsEc2ListSubnetsRequest.builder().build();
    task.run(new Object[] {request});
    verify(mockEc2ServiceDelegate).listSubnets(any(), any(), any(), any());
    request = AwsEc2ListSGsRequest.builder().build();
    task.run(new Object[] {request});
    verify(mockEc2ServiceDelegate).listSGs(any(), any(), any(), any());
    request = AwsEc2ListTagsRequest.builder().build();
    task.run(new Object[] {request});
    verify(mockEc2ServiceDelegate).listTags(any(), any(), any(), any());
    request = AwsEc2ListInstancesRequest.builder().filters(Collections.emptyList()).build();
    task.run(new Object[] {request});
    verify(mockEc2ServiceDelegate).listEc2Instances(any(), any(), any(), anyList(), anyBoolean());
  }
}
