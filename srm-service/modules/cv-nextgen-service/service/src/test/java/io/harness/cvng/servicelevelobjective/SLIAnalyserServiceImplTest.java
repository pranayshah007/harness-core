/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.servicelevelobjective;

import static io.harness.rule.OwnerRule.ARPITJ;
import static io.harness.rule.OwnerRule.DEEPAK_CHHIKARA;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CvNextGenTestBase;
import io.harness.category.element.UnitTests;
import io.harness.cvng.BuilderFactory;
import io.harness.cvng.servicelevelobjective.beans.SLIMetricType;
import io.harness.cvng.servicelevelobjective.beans.ServiceLevelIndicatorDTO;
import io.harness.cvng.servicelevelobjective.beans.ServiceLevelIndicatorSpec;
import io.harness.cvng.servicelevelobjective.beans.slimetricspec.RatioSLIMetricEventType;
import io.harness.cvng.servicelevelobjective.beans.slimetricspec.RatioSLIMetricSpec;
import io.harness.cvng.servicelevelobjective.beans.slimetricspec.ThresholdSLIMetricSpec;
import io.harness.cvng.servicelevelobjective.beans.slimetricspec.ThresholdType;
import io.harness.cvng.servicelevelobjective.entities.SLIRecord.SLIState;
import io.harness.cvng.servicelevelobjective.services.impl.RatioAnalyserServiceImpl;
import io.harness.cvng.servicelevelobjective.services.impl.ThresholdAnalyserServiceImpl;
import io.harness.rule.Owner;

import com.google.inject.Inject;
import java.util.HashMap;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class SLIAnalyserServiceImplTest extends CvNextGenTestBase {
  BuilderFactory builderFactory;
  Double thresholdValue;
  @Inject private ThresholdAnalyserServiceImpl thresholdAnalyserServiceImpl;
  @Inject private RatioAnalyserServiceImpl ratioAnalyserService;

  @Before
  public void setup() {
    builderFactory = BuilderFactory.getDefault();
    thresholdValue = 20.0;
  }

  @Test
  @Owner(developers = DEEPAK_CHHIKARA)
  @Category(UnitTests.class)
  public void testThresholdAnalyser_Success() {
    ServiceLevelIndicatorDTO serviceLevelIndicatorDTO = createThresholdServiceLevelIndicator();
    Map<String, Double> requestMap = new HashMap<>();
    requestMap.put("metric1", 225.0);
    SLIState sliState = thresholdAnalyserServiceImpl.analyse(
        requestMap, (ThresholdSLIMetricSpec) serviceLevelIndicatorDTO.getSpec().getSpec());
    assertThat(SLIState.GOOD).isEqualTo(sliState);
  }

  @Test
  @Owner(developers = DEEPAK_CHHIKARA)
  @Category(UnitTests.class)
  public void testThresholdAnalyser_Failure() {
    ServiceLevelIndicatorDTO serviceLevelIndicatorDTO = createThresholdServiceLevelIndicator();
    Map<String, Double> requestMap = new HashMap<>();
    requestMap.put("metric2", 225.0);
    SLIState sliState = thresholdAnalyserServiceImpl.analyse(
        requestMap, (ThresholdSLIMetricSpec) serviceLevelIndicatorDTO.getSpec().getSpec());
    assertThat(SLIState.NO_DATA).isEqualTo(sliState);
  }

  @Test
  @Owner(developers = DEEPAK_CHHIKARA)
  @Category(UnitTests.class)
  public void testRatioAnalyser_Success() {
    ServiceLevelIndicatorDTO serviceLevelIndicatorDTO = createRatioServiceLevelIndicator();
    Map<String, Double> requestMap = new HashMap<>();
    requestMap.put("metric1", 46.0);
    requestMap.put("metric2", 50.0);
    SLIState sliState =
        ratioAnalyserService.analyse(requestMap, (RatioSLIMetricSpec) serviceLevelIndicatorDTO.getSpec().getSpec());
    assertThat(SLIState.GOOD).isEqualTo(sliState);
  }

  @Test
  @Owner(developers = DEEPAK_CHHIKARA)
  @Category(UnitTests.class)
  public void testRatioAnalyser_MissingData() {
    ServiceLevelIndicatorDTO serviceLevelIndicatorDTO = createRatioServiceLevelIndicator();
    Map<String, Double> requestMap = new HashMap<>();
    requestMap.put("metric1", 46.0);
    requestMap.put("metric2", 0.0);
    SLIState sliState =
        ratioAnalyserService.analyse(requestMap, (RatioSLIMetricSpec) serviceLevelIndicatorDTO.getSpec().getSpec());
    assertThat(SLIState.NO_DATA).isEqualTo(sliState);
  }

  @Test
  @Owner(developers = ARPITJ)
  @Category(UnitTests.class)
  public void testRatioAnalyser_BadEventSuccess() {
    ServiceLevelIndicatorDTO serviceLevelIndicatorDTO = createBadEventRatioServiceLevelIndicator();
    Map<String, Double> requestMap = new HashMap<>();
    requestMap.put("metric1", 4.0);
    requestMap.put("metric2", 50.0);
    SLIState sliState =
        ratioAnalyserService.analyse(requestMap, (RatioSLIMetricSpec) serviceLevelIndicatorDTO.getSpec().getSpec());
    assertThat(SLIState.GOOD).isEqualTo(sliState);
  }

  private ServiceLevelIndicatorDTO createRatioServiceLevelIndicator() {
    return ServiceLevelIndicatorDTO.builder()
        .identifier("sliIndicator")
        .name("sliName")
        .spec(ServiceLevelIndicatorSpec.builder()
                  .type(SLIMetricType.RATIO)
                  .spec(RatioSLIMetricSpec.builder()
                            .eventType(RatioSLIMetricEventType.GOOD)
                            .metric1("metric1")
                            .metric2("metric2")
                            .thresholdValue(90.0)
                            .thresholdType(ThresholdType.GREATER_THAN)
                            .build())
                  .build())
        .build();
  }

  private ServiceLevelIndicatorDTO createBadEventRatioServiceLevelIndicator() {
    return ServiceLevelIndicatorDTO.builder()
        .identifier("sliIndicator")
        .name("sliName")
        .spec(ServiceLevelIndicatorSpec.builder()
                  .type(SLIMetricType.RATIO)
                  .spec(RatioSLIMetricSpec.builder()
                            .eventType(RatioSLIMetricEventType.BAD)
                            .metric1("metric1")
                            .metric2("metric2")
                            .thresholdValue(90.0)
                            .thresholdType(ThresholdType.GREATER_THAN)
                            .build())
                  .build())
        .build();
  }

  private ServiceLevelIndicatorDTO createThresholdServiceLevelIndicator() {
    return ServiceLevelIndicatorDTO.builder()
        .identifier("sliIndicator")
        .name("sliName")
        .spec(ServiceLevelIndicatorSpec.builder()
                  .type(SLIMetricType.THRESHOLD)
                  .spec(ThresholdSLIMetricSpec.builder()
                            .metric1("metric1")
                            .thresholdValue(50.0)
                            .thresholdType(ThresholdType.GREATER_THAN)
                            .build())
                  .build())
        .build();
  }
}
