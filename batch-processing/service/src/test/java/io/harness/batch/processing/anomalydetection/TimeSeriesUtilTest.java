/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.batch.processing.anomalydetection;

import static io.harness.annotations.dev.HarnessTeam.CE;
import static io.harness.rule.OwnerRule.SANDESH;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.batch.processing.anomalydetection.helpers.TimeSeriesUtils;
import io.harness.batch.processing.anomalydetection.models.StatsModel;
import io.harness.category.element.UnitTests;
import io.harness.ccm.anomaly.entities.Anomaly;
import io.harness.rule.Owner;

import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
@OwnedBy(CE)
public class TimeSeriesUtilTest extends CategoryTest {
  StatsModel statsModel;

  @Before
  public void setup() {
    statsModel = new StatsModel();
  }

  @Test
  @Owner(developers = SANDESH)
  @Category(UnitTests.class)
  public void shouldValidateNewTimeSeries() {
    TimeSeriesMetaData timeSeriesMetaData = AnomalyStub.getTimeSeriesMetaData();
    AnomalyDetectionTimeSeries timeSeries = AnomalyStub.getNewEntityTimeSeries();
    assertThat(TimeSeriesUtils.validate(timeSeries, timeSeriesMetaData)).isTrue();
    assertThat(timeSeries.isNewEntity()).isTrue();
  }

  @Test
  @Owner(developers = SANDESH)
  @Category(UnitTests.class)
  public void shouldProcessNewTimeSeries() {
    TimeSeriesMetaData timeSeriesMetaData = AnomalyStub.getTimeSeriesMetaData();
    AnomalyDetectionTimeSeries timeSeries = AnomalyStub.getNewEntityTimeSeries();
    TimeSeriesUtils.validateTimeSeriesTrainData(timeSeries, timeSeriesMetaData);
    Anomaly anomaly = statsModel.detectAnomaly(timeSeries);
    assertThat(anomaly.isAnomaly()).isTrue();
  }
}
