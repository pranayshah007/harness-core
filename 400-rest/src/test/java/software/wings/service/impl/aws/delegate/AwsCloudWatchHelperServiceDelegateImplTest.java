/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl.aws.delegate;

import static io.harness.rule.OwnerRule.AVMOHAN;
import static io.harness.rule.OwnerRule.ROHIT_KUMAR;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.joor.Reflect.on;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.refEq;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

import io.harness.CategoryTest;
import io.harness.aws.util.AwsCallTracker;
import io.harness.category.element.UnitTests;
import io.harness.exception.WingsException;
import io.harness.rule.Owner;

import software.wings.beans.AwsConfig;
import software.wings.service.impl.aws.model.request.AwsCloudWatchMetricDataRequest;
import software.wings.service.impl.aws.model.request.AwsCloudWatchStatisticsRequest;
import software.wings.service.impl.aws.model.response.AwsCloudWatchMetricDataResponse;
import software.wings.service.impl.aws.model.response.AwsCloudWatchStatisticsResponse;
import software.wings.service.impl.delegate.AwsEcrApiHelperServiceDelegateBase;
import software.wings.service.intfc.security.EncryptionService;

import com.amazonaws.AmazonClientException;
import com.amazonaws.AmazonServiceException;
import com.amazonaws.services.cloudwatch.AmazonCloudWatchClient;
import com.amazonaws.services.cloudwatch.model.Datapoint;
import com.amazonaws.services.cloudwatch.model.GetMetricDataRequest;
import com.amazonaws.services.cloudwatch.model.GetMetricDataResult;
import com.amazonaws.services.cloudwatch.model.GetMetricStatisticsResult;
import com.amazonaws.services.cloudwatch.model.MetricDataResult;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;

public class AwsCloudWatchHelperServiceDelegateImplTest extends CategoryTest {
  @Mock private EncryptionService mockEncryptionService;
  @Mock private AwsCallTracker mockTracker;
  @Mock private AwsEcrApiHelperServiceDelegateBase awsEcrApiHelperServiceDelegateBase;

  @Spy @InjectMocks private AwsCloudWatchHelperServiceDelegateImpl awsCloudWatchHelperServiceDelegate;

  @Before
  public void setUp() throws Exception {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = ROHIT_KUMAR)
  @Category(UnitTests.class)
  public void getMetricStatistics() {
    doReturn(null).when(mockEncryptionService).decrypt(any(AwsConfig.class), any(), eq(false));
    final AmazonCloudWatchClient amazonCloudWatchClientMock = mock(AmazonCloudWatchClient.class);
    doReturn(amazonCloudWatchClientMock).when(awsCloudWatchHelperServiceDelegate).getAwsCloudWatchClient(any(), any());
    doNothing().when(mockTracker).trackCloudWatchCall(any());

    final GetMetricStatisticsResult getMetricStatisticsResult = new GetMetricStatisticsResult();
    final Datapoint datapoint = new Datapoint();
    getMetricStatisticsResult.withDatapoints(datapoint);
    doReturn(getMetricStatisticsResult).when(amazonCloudWatchClientMock).getMetricStatistics(any());

    final AwsCloudWatchStatisticsRequest awsCloudWatchStatisticsRequest =
        AwsCloudWatchStatisticsRequest.builder().build();

    final AwsCloudWatchStatisticsResponse metricStatistics =
        awsCloudWatchHelperServiceDelegate.getMetricStatistics(awsCloudWatchStatisticsRequest);

    assertThat(metricStatistics.getDatapoints().get(0)).isEqualTo(datapoint);
  }

  @Test
  @Owner(developers = ROHIT_KUMAR)
  @Category(UnitTests.class)
  public void getMetricStatistics_error() {
    doReturn(null).when(mockEncryptionService).decrypt(any(AwsConfig.class), any(), eq(false));
    final AmazonCloudWatchClient amazonCloudWatchClientMock = mock(AmazonCloudWatchClient.class);
    doReturn(amazonCloudWatchClientMock).when(awsCloudWatchHelperServiceDelegate).getAwsCloudWatchClient(any(), any());
    doNothing().when(mockTracker).trackCloudWatchCall(any());

    doThrow(new AmazonServiceException("")).when(amazonCloudWatchClientMock).getMetricStatistics(any());

    final AwsCloudWatchStatisticsRequest awsCloudWatchStatisticsRequest =
        AwsCloudWatchStatisticsRequest.builder().build();

    on(awsCloudWatchHelperServiceDelegate)
        .set("awsEcrApiHelperServiceDelegateBase", awsEcrApiHelperServiceDelegateBase);
    doCallRealMethod().when(awsEcrApiHelperServiceDelegateBase).handleAmazonServiceException(any());
    assertThatExceptionOfType(WingsException.class)
        .isThrownBy(() -> awsCloudWatchHelperServiceDelegate.getMetricStatistics(awsCloudWatchStatisticsRequest));

    doThrow(new AmazonClientException("")).when(amazonCloudWatchClientMock).getMetricStatistics(any());
    doCallRealMethod().when(awsEcrApiHelperServiceDelegateBase).handleAmazonClientException(any());
    assertThatExceptionOfType(WingsException.class)
        .isThrownBy(() -> awsCloudWatchHelperServiceDelegate.getMetricStatistics(awsCloudWatchStatisticsRequest));
  }

  @Test
  @Owner(developers = AVMOHAN)
  @Category(UnitTests.class)
  public void shouldGetMetricData() throws Exception {
    final AmazonCloudWatchClient amazonCloudWatchClientMock = mock(AmazonCloudWatchClient.class);
    final AwsConfig awsConfig = AwsConfig.builder().build();
    doReturn(amazonCloudWatchClientMock)
        .when(awsCloudWatchHelperServiceDelegate)
        .getAwsCloudWatchClient(eq("REGION"), refEq(awsConfig));

    final MetricDataResult dataResult = new MetricDataResult();
    final GetMetricDataResult getMetricDataResult = new GetMetricDataResult().withMetricDataResults(dataResult);
    //    getMetricDataResult.withDatapoints(datapoint);
    doReturn(getMetricDataResult).when(amazonCloudWatchClientMock).getMetricData(any(GetMetricDataRequest.class));

    final AwsCloudWatchMetricDataRequest awsCloudWatchMetricDataRequest =
        AwsCloudWatchMetricDataRequest.builder().awsConfig(awsConfig).region("REGION").build();

    final AwsCloudWatchMetricDataResponse metricDataResponse =
        awsCloudWatchHelperServiceDelegate.getMetricData(awsCloudWatchMetricDataRequest);
    assertThat(metricDataResponse.getMetricDataResults()).hasSize(1).containsExactly(dataResult);
  }

  @Test
  @Owner(developers = AVMOHAN)
  @Category(UnitTests.class)
  public void shouldGetMetricDataWithMultiplePages() throws Exception {
    final AmazonCloudWatchClient amazonCloudWatchClientMock = mock(AmazonCloudWatchClient.class);
    final AwsConfig awsConfig = AwsConfig.builder().build();
    doReturn(amazonCloudWatchClientMock)
        .when(awsCloudWatchHelperServiceDelegate)
        .getAwsCloudWatchClient(eq("REGION"), refEq(awsConfig));

    MetricDataResult dataResult1 = new MetricDataResult().withId("1");
    MetricDataResult dataResult2 = new MetricDataResult().withId("2");

    doReturn(new GetMetricDataResult().withMetricDataResults(dataResult1).withNextToken("token"))
        .doReturn(new GetMetricDataResult().withMetricDataResults(dataResult2).withNextToken(null))
        .when(amazonCloudWatchClientMock)
        .getMetricData(any(GetMetricDataRequest.class));

    final AwsCloudWatchMetricDataRequest awsCloudWatchMetricDataRequest =
        AwsCloudWatchMetricDataRequest.builder().awsConfig(awsConfig).region("REGION").build();

    final AwsCloudWatchMetricDataResponse metricDataResponse =
        awsCloudWatchHelperServiceDelegate.getMetricData(awsCloudWatchMetricDataRequest);
    assertThat(metricDataResponse.getMetricDataResults()).hasSize(2).containsExactly(dataResult1, dataResult2);
  }

  @Test
  @Owner(developers = AVMOHAN)
  @Category(UnitTests.class)
  public void shouldHandleAmazonServiceExceptionWhenGetMetricData() {
    final AmazonCloudWatchClient amazonCloudWatchClientMock = mock(AmazonCloudWatchClient.class);
    doReturn(amazonCloudWatchClientMock).when(awsCloudWatchHelperServiceDelegate).getAwsCloudWatchClient(any(), any());

    final AwsCloudWatchMetricDataRequest awsCloudWatchMetricDataRequest =
        AwsCloudWatchMetricDataRequest.builder().build();

    doThrow(new AmazonServiceException(""))
        .when(amazonCloudWatchClientMock)
        .getMetricData(any(GetMetricDataRequest.class));
    doCallRealMethod().when(awsEcrApiHelperServiceDelegateBase).handleAmazonServiceException(any());
    assertThatExceptionOfType(WingsException.class)
        .isThrownBy(() -> awsCloudWatchHelperServiceDelegate.getMetricData(awsCloudWatchMetricDataRequest));
  }

  @Test
  @Owner(developers = AVMOHAN)
  @Category(UnitTests.class)
  public void shouldHandleAmazonClientExceptionWhenGetMetricData() {
    final AmazonCloudWatchClient amazonCloudWatchClientMock = mock(AmazonCloudWatchClient.class);
    doReturn(amazonCloudWatchClientMock)
        .when(awsCloudWatchHelperServiceDelegate)
        .getAwsCloudWatchClient(any(), any(AwsConfig.class));

    final AwsCloudWatchMetricDataRequest awsCloudWatchMetricDataRequest =
        AwsCloudWatchMetricDataRequest.builder().build();

    doThrow(new AmazonClientException(""))
        .when(amazonCloudWatchClientMock)
        .getMetricData(any(GetMetricDataRequest.class));
    doCallRealMethod().when(awsEcrApiHelperServiceDelegateBase).handleAmazonClientException(any());
    assertThatExceptionOfType(WingsException.class)
        .isThrownBy(() -> awsCloudWatchHelperServiceDelegate.getMetricData(awsCloudWatchMetricDataRequest));
  }
}
