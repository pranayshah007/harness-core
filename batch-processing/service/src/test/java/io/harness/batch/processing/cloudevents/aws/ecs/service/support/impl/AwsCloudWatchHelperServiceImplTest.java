/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.batch.processing.cloudevents.aws.ecs.service.support.impl;

import static io.harness.rule.OwnerRule.AVMOHAN;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.refEq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

import io.harness.CategoryTest;
import io.harness.batch.processing.cloudevents.aws.ecs.service.tasklet.support.request.AwsCloudWatchMetricDataRequest;
import io.harness.batch.processing.cloudevents.aws.ecs.service.tasklet.support.response.AwsCloudWatchMetricDataResponse;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;

import software.wings.beans.AwsCrossAccountAttributes;

import com.amazonaws.AmazonClientException;
import com.amazonaws.services.cloudwatch.AmazonCloudWatchClient;
import com.amazonaws.services.cloudwatch.model.GetMetricDataRequest;
import com.amazonaws.services.cloudwatch.model.GetMetricDataResult;
import com.amazonaws.services.cloudwatch.model.MetricDataResult;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;

public class AwsCloudWatchHelperServiceImplTest extends CategoryTest {
  @Spy @InjectMocks private AwsCloudWatchHelperServiceImpl awsCloudWatchHelperService;

  @Before
  public void setUp() throws Exception {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = AVMOHAN)
  @Category(UnitTests.class)
  public void shouldGetMetricData() throws Exception {
    final AmazonCloudWatchClient amazonCloudWatchClientMock = mock(AmazonCloudWatchClient.class);
    final AwsCrossAccountAttributes awsCrossAccountAttributes = AwsCrossAccountAttributes.builder().build();
    doReturn(amazonCloudWatchClientMock)
        .when(awsCloudWatchHelperService)
        .getAwsCloudWatchClient(eq("REGION"), refEq(awsCrossAccountAttributes));

    final MetricDataResult dataResult = new MetricDataResult();
    final GetMetricDataResult getMetricDataResult = new GetMetricDataResult().withMetricDataResults(dataResult);
    //    getMetricDataResult.withDatapoints(datapoint);
    doReturn(getMetricDataResult).when(amazonCloudWatchClientMock).getMetricData(any(GetMetricDataRequest.class));

    final AwsCloudWatchMetricDataRequest awsCloudWatchMetricDataRequest =
        AwsCloudWatchMetricDataRequest.builder()
            .awsCrossAccountAttributes(awsCrossAccountAttributes)
            .region("REGION")
            .build();

    final AwsCloudWatchMetricDataResponse metricDataResponse =
        awsCloudWatchHelperService.getMetricData(awsCloudWatchMetricDataRequest);
    assertThat(metricDataResponse.getMetricDataResults()).hasSize(1).containsExactly(dataResult);
  }

  @Test
  @Owner(developers = AVMOHAN)
  @Category(UnitTests.class)
  public void shouldGetMetricDataWithMultiplePages() throws Exception {
    final AmazonCloudWatchClient amazonCloudWatchClientMock = mock(AmazonCloudWatchClient.class);
    final AwsCrossAccountAttributes awsCrossAccountAttributes = AwsCrossAccountAttributes.builder().build();
    doReturn(amazonCloudWatchClientMock)
        .when(awsCloudWatchHelperService)
        .getAwsCloudWatchClient(eq("REGION"), refEq(awsCrossAccountAttributes));

    MetricDataResult dataResult1 = new MetricDataResult().withId("1");
    MetricDataResult dataResult2 = new MetricDataResult().withId("2");

    doReturn(new GetMetricDataResult().withMetricDataResults(dataResult1).withNextToken("token"))
        .doReturn(new GetMetricDataResult().withMetricDataResults(dataResult2).withNextToken(null))
        .when(amazonCloudWatchClientMock)
        .getMetricData(any(GetMetricDataRequest.class));

    final AwsCloudWatchMetricDataRequest awsCloudWatchMetricDataRequest =
        AwsCloudWatchMetricDataRequest.builder()
            .awsCrossAccountAttributes(awsCrossAccountAttributes)
            .region("REGION")
            .build();

    final AwsCloudWatchMetricDataResponse metricDataResponse =
        awsCloudWatchHelperService.getMetricData(awsCloudWatchMetricDataRequest);
    assertThat(metricDataResponse.getMetricDataResults()).hasSize(2).containsExactly(dataResult1, dataResult2);
  }

  @Test
  @Owner(developers = AVMOHAN)
  @Category(UnitTests.class)
  public void shouldHandleAmazonClientExceptionWhenGetMetricData() {
    final AmazonCloudWatchClient amazonCloudWatchClientMock = mock(AmazonCloudWatchClient.class);
    doReturn(amazonCloudWatchClientMock)
        .when(awsCloudWatchHelperService)
        .getAwsCloudWatchClient(anyString(), any(AwsCrossAccountAttributes.class));

    final AwsCloudWatchMetricDataRequest awsCloudWatchMetricDataRequest =
        AwsCloudWatchMetricDataRequest.builder().build();
    doThrow(new AmazonClientException(""))
        .when(amazonCloudWatchClientMock)
        .getMetricData(any(GetMetricDataRequest.class));

    AwsCloudWatchMetricDataResponse metricData =
        awsCloudWatchHelperService.getMetricData(awsCloudWatchMetricDataRequest);
    assertThat(metricData.getMetricDataResults()).hasSize(0);
  }
}
