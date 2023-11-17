/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.utils;

import static io.harness.rule.OwnerRule.ABHIJITH;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.category.element.UnitTests;
import io.harness.cvng.analysis.beans.DeploymentTimeSeriesAnalysisDTO;
import io.harness.cvng.cdng.beans.v2.AnalysisReason;
import io.harness.rule.Owner;

import java.util.Arrays;
import java.util.HashMap;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class VerifyStepMetricsAnalysisUtilsTest {
  @Test
  @Owner(developers = ABHIJITH)
  @Category(UnitTests.class)
  void testGetReasonForFailure_forNoControlData() {
    AnalysisReason analysisReason = VerifyStepMetricsAnalysisUtils.getReasonForFailure(
        DeploymentTimeSeriesAnalysisDTO.HostData.builder().testData(Arrays.asList(1D)).build(), new HashMap<>());
    assertThat(analysisReason).isEqualTo(AnalysisReason.NO_CONTROL_DATA);
  }

  @Test
  @Owner(developers = ABHIJITH)
  @Category(UnitTests.class)
  void testGetReasonForFailure_forNoTestData() {
    AnalysisReason analysisReason = VerifyStepMetricsAnalysisUtils.getReasonForFailure(
        DeploymentTimeSeriesAnalysisDTO.HostData.builder().build(), new HashMap<>());
    assertThat(analysisReason).isEqualTo(AnalysisReason.NO_TEST_DATA);
  }

  @Test
  @Owner(developers = ABHIJITH)
  @Category(UnitTests.class)
  void testGetReasonForFailure_MLFailure() {
    AnalysisReason analysisReason =
        VerifyStepMetricsAnalysisUtils.getReasonForFailure(DeploymentTimeSeriesAnalysisDTO.HostData.builder()
                                                               .testData(Arrays.asList(1D))
                                                               .controlData(Arrays.asList(1D))
                                                               .build(),
            new HashMap<>());
    assertThat(analysisReason).isEqualTo(AnalysisReason.ML_ANALYSIS);
  }
}
