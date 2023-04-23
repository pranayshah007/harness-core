/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl.aws.manager;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.rule.OwnerRule.ROHIT_KUMAR;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.DelegateTask;
import io.harness.beans.ExecutionStatus;
import io.harness.category.element.UnitTests;
import io.harness.delegate.beans.ErrorNotifyResponseData;
import io.harness.exception.WingsException;
import io.harness.rule.Owner;

import software.wings.beans.AwsConfig;
import software.wings.service.impl.aws.model.embed.AwsLambdaDetails;
import software.wings.service.impl.aws.model.request.AwsLambdaDetailsRequest;
import software.wings.service.impl.aws.model.response.AwsLambdaDetailsResponse;
import software.wings.service.intfc.DelegateService;
import software.wings.service.intfc.security.EncryptionService;

import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;

@OwnedBy(CDP)
public class AwsLambdaHelperServiceManagerImplTest extends CategoryTest {
  @Mock private EncryptionService mockEncryptionService;
  @Mock private DelegateService delegateService;

  @Spy @InjectMocks private AwsLambdaHelperServiceManagerImpl awsLambdaHelperServiceManager;

  @Before
  public void setUp() throws Exception {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = ROHIT_KUMAR)
  @Category(UnitTests.class)
  public void getFunctionDetails() throws InterruptedException {
    final AwsLambdaDetailsRequest awsLambdaDetailsRequest =
        AwsLambdaDetailsRequest.builder().awsConfig(AwsConfig.builder().tag("tag").build()).build();
    final AwsLambdaDetails lambdaDetails = AwsLambdaDetails.builder().build();
    final AwsLambdaDetailsResponse awsLambdaDetailsResponse =
        AwsLambdaDetailsResponse.builder().executionStatus(ExecutionStatus.SUCCESS).details(lambdaDetails).build();
    doReturn(awsLambdaDetailsResponse).when(delegateService).executeTaskV2(any(DelegateTask.class));

    assertThat(awsLambdaHelperServiceManager.getFunctionDetails(awsLambdaDetailsRequest)).isEqualTo(lambdaDetails);
  }

  @Test
  @Owner(developers = ROHIT_KUMAR)
  @Category(UnitTests.class)
  public void getFunctionDetails_error() throws InterruptedException {
    final AwsLambdaDetailsRequest awsLambdaDetailsRequest =
        AwsLambdaDetailsRequest.builder().awsConfig(AwsConfig.builder().tag("tag").build()).build();
    final AwsLambdaDetailsResponse awsLambdaDetailsResponse =
        AwsLambdaDetailsResponse.builder().executionStatus(ExecutionStatus.FAILED).errorMessage("error").build();
    doReturn(awsLambdaDetailsResponse).when(delegateService).executeTaskV2(any(DelegateTask.class));

    assertThatExceptionOfType(WingsException.class)
        .isThrownBy(() -> awsLambdaHelperServiceManager.getFunctionDetails(awsLambdaDetailsRequest));

    doReturn(ErrorNotifyResponseData.builder().errorMessage("error").build())
        .when(delegateService)
        .executeTaskV2(any(DelegateTask.class));
    assertThatExceptionOfType(WingsException.class)
        .isThrownBy(() -> awsLambdaHelperServiceManager.getFunctionDetails(awsLambdaDetailsRequest));
  }
}
