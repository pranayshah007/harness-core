/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.services.impl;

import static io.harness.rule.OwnerRule.PRAVEEN;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.harness.CvNextGenTestBase;
import io.harness.category.element.UnitTests;
import io.harness.cvng.BuilderFactory;
import io.harness.cvng.core.beans.SampleDataDTO;
import io.harness.cvng.core.beans.TimeSeriesSampleDTO;
import io.harness.cvng.core.services.api.NewRelicServiceImplTest;
import io.harness.cvng.core.services.api.ParseSampleDataService;
import io.harness.rule.Owner;

import com.google.common.base.Charsets;
import com.google.common.io.Resources;
import com.google.inject.Inject;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class ParseSampleDataImplTest extends CvNextGenTestBase {
  @Inject ParseSampleDataService parseSampleDataService;
  private BuilderFactory builderFactory;

  @Before
  public void setup() {
    builderFactory = BuilderFactory.getDefault();
  }

  @Test
  @Owner(developers = PRAVEEN)
  @Category(UnitTests.class)
  public void testParseSampleData() throws Exception {
    String responseObject = Resources.toString(
        NewRelicServiceImplTest.class.getResource("/newrelic/newrelic-custom-response.json"), Charsets.UTF_8);
    String metricValuesPath = "$.data.[*].metricValue";
    String timestampPath = "$.data.[*].timestamp";
    String timestampFormat = null;

    SampleDataDTO sampleDataDTO = SampleDataDTO.builder()
                                      .jsonResponse(responseObject)
                                      .metricValueJSONPath(metricValuesPath)
                                      .timestampFormat(timestampFormat)
                                      .timestampJSONPath(timestampPath)
                                      .groupName("myNRTxn")
                                      .build();

    List<TimeSeriesSampleDTO> sampleData =
        parseSampleDataService.parseSampleData(builderFactory.getProjectParams(), sampleDataDTO);

    assertThat(sampleData).isNotEmpty();
    assertThat(sampleData.size()).isEqualTo(15);
  }

  @Test
  @Owner(developers = PRAVEEN)
  @Category(UnitTests.class)
  public void testParseSampleData_withArrays() throws Exception {
    String responseObject = Resources.toString(
        NewRelicServiceImplTest.class.getResource("/newrelic/newrelic-custom-response-witharray.json"), Charsets.UTF_8);
    String metricValuesPath = "$.timeSeries.[*].results.[0].average";
    String timestampPath = "$.timeSeries.[*].endTimeSeconds";
    String timestampFormat = null;

    SampleDataDTO sampleDataDTO = SampleDataDTO.builder()
                                      .jsonResponse(responseObject)
                                      .metricValueJSONPath(metricValuesPath)
                                      .timestampFormat(timestampFormat)
                                      .timestampJSONPath(timestampPath)
                                      .groupName("myNRTxn")
                                      .build();

    List<TimeSeriesSampleDTO> sampleData =
        parseSampleDataService.parseSampleData(builderFactory.getProjectParams(), sampleDataDTO);

    assertThat(sampleData).isNotEmpty();
    assertThat(sampleData.size()).isEqualTo(30);
  }

  @Test
  @Owner(developers = PRAVEEN)
  @Category(UnitTests.class)
  public void testParseSampleData_badPath() throws Exception {
    String responseObject = Resources.toString(
        NewRelicServiceImplTest.class.getResource("/newrelic/newrelic-custom-response.json"), Charsets.UTF_8);
    String metricValuesPath = "$.data.[*].metricValues";
    String timestampPath = "$.data.[*].timestamp";
    String timestampFormat = null;

    SampleDataDTO sampleDataDTO = SampleDataDTO.builder()
                                      .jsonResponse(responseObject)
                                      .metricValueJSONPath(metricValuesPath)
                                      .timestampFormat(timestampFormat)
                                      .timestampJSONPath(timestampPath)
                                      .groupName("myNRTxn")
                                      .build();

    assertThatThrownBy(() -> parseSampleDataService.parseSampleData(builderFactory.getProjectParams(), sampleDataDTO))
        .isInstanceOf(RuntimeException.class)
        .hasMessageContaining("Unable to parse the response object with the given json paths");
  }
}
