/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl.aws.delegate;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.rule.OwnerRule.SAINATH;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.powermock.api.mockito.PowerMockito.doReturn;
import static org.powermock.api.mockito.PowerMockito.doThrow;
import static org.powermock.api.mockito.PowerMockito.mock;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.aws.beans.AwsInternalConfig;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;

import com.amazonaws.services.lambda.AWSLambdaClient;
import com.amazonaws.services.lambda.model.ResourceNotFoundException;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;

@OwnedBy(CDP)
public class AwsLambdaHelperServiceDelegateNGImplTest extends CategoryTest {
  @Spy @InjectMocks AwsLambdaHelperServiceDelegateNGImpl awsLambdaHelperServiceDelegateNGImpl;

  @Before
  public void setUp() throws Exception {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = SAINATH)
  @Category(UnitTests.class)
  public void testGetAwsLambdaFunctionDetails() {
    AWSLambdaClient awsLambdaClient = mock(AWSLambdaClient.class);

    doReturn(awsLambdaClient).when(awsLambdaHelperServiceDelegateNGImpl).getAmazonLambdaClient(any(), any());

    doThrow(new ResourceNotFoundException("")).when(awsLambdaClient).getFunction(any());

    assertThat(awsLambdaHelperServiceDelegateNGImpl.getAwsLambdaFunctionDetails(
                   AwsInternalConfig.builder().build(), "function", "region"))
        .isNull();
  }
}
