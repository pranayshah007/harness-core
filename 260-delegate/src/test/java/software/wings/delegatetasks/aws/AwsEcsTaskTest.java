/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.delegatetasks.aws;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.delegate.beans.TaskData.DEFAULT_ASYNC_CALL_TIMEOUT;
import static io.harness.rule.OwnerRule.IVAN;
import static io.harness.rule.OwnerRule.SATYAM;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.joor.Reflect.on;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.beans.ExecutionStatus;
import io.harness.category.element.UnitTests;
import io.harness.delegate.beans.DelegateTaskPackage;
import io.harness.delegate.beans.TaskData;
import io.harness.exception.InvalidRequestException;
import io.harness.rule.Owner;

import software.wings.WingsBaseTest;
import software.wings.service.impl.aws.model.AwsEcsListClusterServicesRequest;
import software.wings.service.impl.aws.model.AwsEcsListClusterServicesResponse;
import software.wings.service.impl.aws.model.AwsEcsListClustersRequest;
import software.wings.service.impl.aws.model.AwsEcsRequest;
import software.wings.service.impl.aws.model.AwsResponse;
import software.wings.service.intfc.aws.delegate.AwsEcsHelperServiceDelegate;

import com.amazonaws.services.ecs.model.Service;
import java.util.Collections;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;

@TargetModule(HarnessModule._930_DELEGATE_TASKS)
@OwnedBy(CDP)
public class AwsEcsTaskTest extends WingsBaseTest {
  @Mock private AwsEcsHelperServiceDelegate mockEcsHelperServiceDelegate;

  @InjectMocks
  private AwsEcsTask task =
      new AwsEcsTask(DelegateTaskPackage.builder()
                         .delegateId("delegateid")
                         .data(TaskData.builder().async(true).timeout(DEFAULT_ASYNC_CALL_TIMEOUT).build())
                         .build(),
          null, notifyResponseData -> {}, () -> true);

  @Before
  public void setUp() throws Exception {
    on(task).set("ecsHelperServiceDelegate", mockEcsHelperServiceDelegate);
  }

  @Test
  @Owner(developers = SATYAM)
  @Category(UnitTests.class)
  public void testRun() {
    AwsEcsRequest request = AwsEcsListClustersRequest.builder().build();
    task.run(request);
    verify(mockEcsHelperServiceDelegate).listClusters(any(), any(), any());
  }

  @Test
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void testListServicesForCluster() {
    Service service = new Service();
    service.setServiceName("serviceName");
    doReturn(Collections.singletonList(service))
        .when(mockEcsHelperServiceDelegate)
        .listServicesForCluster(any(), any(), any(), any());
    AwsEcsListClusterServicesRequest awsEcsListClusterServicesRequest =
        AwsEcsListClusterServicesRequest.builder().build();

    AwsResponse awsResponse = task.run(awsEcsListClusterServicesRequest);

    assertThat(awsResponse instanceof AwsEcsListClusterServicesResponse).isTrue();
    assertThat(((AwsEcsListClusterServicesResponse) awsResponse).getExecutionStatus())
        .isEqualTo(ExecutionStatus.SUCCESS);
    assertThat(((AwsEcsListClusterServicesResponse) awsResponse).getServices().get(0).getServiceName())
        .isEqualTo("serviceName");
    verify(mockEcsHelperServiceDelegate).listServicesForCluster(any(), any(), any(), any());
  }

  @Test(expected = InvalidRequestException.class)
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void testRunWithInvalidRequestException() {
    doThrow(new RuntimeException("Error msg"))
        .when(mockEcsHelperServiceDelegate)
        .listServicesForCluster(any(), any(), any(), any());
    AwsEcsListClusterServicesRequest awsEcsListClusterServicesRequest =
        AwsEcsListClusterServicesRequest.builder().build();

    AwsResponse awsResponse = task.run(awsEcsListClusterServicesRequest);

    assertThatExceptionOfType(InvalidRequestException.class)
        .isThrownBy(() -> task.run(awsEcsListClusterServicesRequest));
  }

  @Test(expected = InvalidRequestException.class)
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void testRunWithWingsException() {
    doThrow(new InvalidRequestException("Error msg"))
        .when(mockEcsHelperServiceDelegate)
        .listServicesForCluster(any(), any(), any(), any());
    AwsEcsListClusterServicesRequest awsEcsListClusterServicesRequest =
        AwsEcsListClusterServicesRequest.builder().build();

    AwsResponse awsResponse = task.run(awsEcsListClusterServicesRequest);

    assertThatExceptionOfType(InvalidRequestException.class)
        .isThrownBy(() -> task.run(awsEcsListClusterServicesRequest));
  }
}
