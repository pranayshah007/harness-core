/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl.prometheus;

import static io.harness.rule.OwnerRule.KAMAL;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;
import io.harness.time.Timestamp;

import software.wings.delegatetasks.cv.DelegateCVActivityLogService;
import software.wings.service.impl.newrelic.NewRelicMetricDataRecord;

import com.google.common.collect.TreeBasedTable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class PrometheusMetricDataResponseTest extends CategoryTest {
  @Test
  @Owner(developers = KAMAL)
  @Category(UnitTests.class)
  public void getMetricRecords_withOneMetricValue() {
    PrometheusMetricDataResponse.PrometheusMetric prometheusMetric =
        PrometheusMetricDataResponse.PrometheusMetric.builder().build();
    List<List<Object>> values = new ArrayList<>();
    PrometheusMetricDataResponse.PrometheusMetricData result =
        PrometheusMetricDataResponse.PrometheusMetricData.builder()
            .result(Arrays.asList(PrometheusMetricDataResponse.PrometheusMetricDataResult.builder()
                                      .metric(prometheusMetric)
                                      .values(values)
                                      .build()))
            .build();
    PrometheusMetricDataResponse prometheusMetricDataResponse =
        PrometheusMetricDataResponse.builder().status("success").data(result).build();
    DelegateCVActivityLogService.Logger activityLogger = mock(DelegateCVActivityLogService.Logger.class);
    TreeBasedTable<String, Long, NewRelicMetricDataRecord> newRelicMetricDataRecords =
        prometheusMetricDataResponse.getMetricRecords("hardware", "cpu", uuid(), uuid(), uuid(), uuid(), uuid(), "host",
            "groupName", Timestamp.currentMinuteBoundary(), uuid(), false, "http://promethus-server:8080",
            activityLogger);
    assertThat(newRelicMetricDataRecords.size() == 0).isTrue();
  }

  @Test
  @Owner(developers = KAMAL)
  @Category(UnitTests.class)
  public void getMetricRecords_withMoreThanOneMetricValueLogExceptionAsAnActivityLog() {
    PrometheusMetricDataResponse.PrometheusMetricData result =
        PrometheusMetricDataResponse.PrometheusMetricData.builder()
            .result(Arrays.asList(PrometheusMetricDataResponse.PrometheusMetricDataResult.builder().build(),
                PrometheusMetricDataResponse.PrometheusMetricDataResult.builder().build()))
            .build();
    PrometheusMetricDataResponse prometheusMetricDataResponse =
        PrometheusMetricDataResponse.builder().status("success").data(result).build();
    DelegateCVActivityLogService.Logger activityLogger = mock(DelegateCVActivityLogService.Logger.class);
    prometheusMetricDataResponse.getMetricRecords("hardware", "cpu", uuid(), uuid(), uuid(), uuid(), uuid(), "host",
        "groupName", Timestamp.currentMinuteBoundary(), uuid(), false, "http://promethus-server:8080", activityLogger);
    verify(activityLogger, times(1))
        .error(
            "Multiple time series values are returned for metric name cpu and group name hardware. Please add more filters to your query to return only one time series.");
  }

  private String uuid() {
    return UUID.randomUUID().toString();
  }
}
