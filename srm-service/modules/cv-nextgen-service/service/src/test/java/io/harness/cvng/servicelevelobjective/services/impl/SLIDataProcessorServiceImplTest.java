/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.servicelevelobjective.services.impl;

import static io.harness.rule.OwnerRule.ABHIJITH;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CvNextGenTestBase;
import io.harness.category.element.UnitTests;
import io.harness.cvng.BuilderFactory;
import io.harness.cvng.servicelevelobjective.beans.SLIAnalyseRequest;
import io.harness.cvng.servicelevelobjective.beans.SLIAnalyseResponse;
import io.harness.cvng.servicelevelobjective.beans.slimetricspec.SLIMetricSpec;
import io.harness.cvng.servicelevelobjective.entities.SLIRecord.SLIState;
import io.harness.cvng.servicelevelobjective.services.api.SLIDataProcessorService;
import io.harness.rule.Owner;

import com.google.inject.Inject;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class SLIDataProcessorServiceImplTest extends CvNextGenTestBase {
  @Inject SLIDataProcessorService sliDataProcessorService;

  Clock clock;

  BuilderFactory builderFactory;

  @Before
  public void setUp() throws Exception {
    builderFactory = BuilderFactory.getDefault();
    clock = builderFactory.getClock();
  }

  @Test
  @Owner(developers = ABHIJITH)
  @Category(UnitTests.class)
  public void testProcess_withMissingDataAsGood() {
    Instant endTime = clock.instant();
    Instant startTime = endTime.minus(Duration.ofMinutes(4));
    String metricIdentifier = "metricIdenitifer";
    SLIMetricSpec sliMetricSpec = builderFactory.getThresholdSLIMetricSpecBuilder().metric1(metricIdentifier).build();

    SLIAnalyseRequest sliAnalyseRequest1 =
        SLIAnalyseRequest.builder().timeStamp(startTime.plus(Duration.ofMinutes(1))).metricValue(50).build();
    SLIAnalyseRequest sliAnalyseRequest2 =
        SLIAnalyseRequest.builder().timeStamp(startTime.plus(Duration.ofMinutes(2))).metricValue(120).build();

    Map<String, List<SLIAnalyseRequest>> sliAnalyseRequests = new HashMap<String, List<SLIAnalyseRequest>>() {
      { put(metricIdentifier, Arrays.asList(sliAnalyseRequest1, sliAnalyseRequest2)); }
    };
    List<SLIAnalyseResponse> responses =
        sliDataProcessorService.process(sliAnalyseRequests, sliMetricSpec, startTime, endTime);

    assertThat(responses).hasSize(4);
    assertThat(responses.get(0).getTimeStamp()).isEqualTo(startTime);
    assertThat(responses.get(1).getTimeStamp()).isEqualTo(startTime.plus(Duration.ofMinutes(1)));
    assertThat(responses.get(2).getTimeStamp()).isEqualTo(startTime.plus(Duration.ofMinutes(2)));
    assertThat(responses.get(3).getTimeStamp()).isEqualTo(startTime.plus(Duration.ofMinutes(3)));

    assertThat(responses.get(0).getRunningBadCount()).isEqualTo(0);
    assertThat(responses.get(1).getRunningBadCount()).isEqualTo(1);
    assertThat(responses.get(2).getRunningBadCount()).isEqualTo(1);
    assertThat(responses.get(3).getRunningBadCount()).isEqualTo(1);

    assertThat(responses.get(0).getRunningGoodCount()).isEqualTo(0);
    assertThat(responses.get(1).getRunningGoodCount()).isEqualTo(0);
    assertThat(responses.get(2).getRunningGoodCount()).isEqualTo(1);
    assertThat(responses.get(3).getRunningGoodCount()).isEqualTo(1);

    assertThat(responses.get(0).getSliState()).isEqualTo(SLIState.NO_DATA);
    assertThat(responses.get(1).getSliState()).isEqualTo(SLIState.BAD);
    assertThat(responses.get(2).getSliState()).isEqualTo(SLIState.GOOD);
    assertThat(responses.get(3).getSliState()).isEqualTo(SLIState.NO_DATA);
  }

  @Test
  @Owner(developers = ABHIJITH)
  @Category(UnitTests.class)
  public void testProcess_withRatioBasedSpec() {
    Instant endTime = clock.instant();
    Instant startTime = endTime.minus(Duration.ofMinutes(4));
    SLIMetricSpec sliMetricSpec = builderFactory.getRatioSLIMetricSpecBuilder().build();

    SLIAnalyseRequest sliAnalyseRequest11 = SLIAnalyseRequest.builder().timeStamp(startTime).metricValue(50).build();
    SLIAnalyseRequest sliAnalyseRequest12 = SLIAnalyseRequest.builder().timeStamp(startTime).metricValue(100).build();
    SLIAnalyseRequest sliAnalyseRequest21 =
        SLIAnalyseRequest.builder().timeStamp(startTime.plus(Duration.ofMinutes(2))).metricValue(120).build();
    SLIAnalyseRequest sliAnalyseRequest22 =
        SLIAnalyseRequest.builder().timeStamp(startTime.plus(Duration.ofMinutes(2))).metricValue(1).build();

    Map<String, List<SLIAnalyseRequest>> sliAnalyseRequests = new HashMap<String, List<SLIAnalyseRequest>>() {
      {
        put("metric1", Arrays.asList(sliAnalyseRequest11, sliAnalyseRequest21));
        put("metric2", Arrays.asList(sliAnalyseRequest12, sliAnalyseRequest22));
      }
    };

    List<SLIAnalyseResponse> responses =
        sliDataProcessorService.process(sliAnalyseRequests, sliMetricSpec, startTime, endTime);

    assertThat(responses).hasSize(4);

    assertThat(responses.get(0).getRunningBadCount()).isEqualTo(0);
    assertThat(responses.get(1).getRunningBadCount()).isEqualTo(0);
    assertThat(responses.get(2).getRunningBadCount()).isEqualTo(0);
    assertThat(responses.get(3).getRunningBadCount()).isEqualTo(0);

    assertThat(responses.get(0).getRunningGoodCount()).isEqualTo(1);
    assertThat(responses.get(1).getRunningGoodCount()).isEqualTo(1);
    assertThat(responses.get(2).getRunningGoodCount()).isEqualTo(2);
    assertThat(responses.get(3).getRunningGoodCount()).isEqualTo(2);
  }
}
