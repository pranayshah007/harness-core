/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */
package io.harness.idp.scorecard.datapointsdata.dsldataprovider.factory;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.idp.scorecard.datapointsdata.dsldataprovider.base.DataSourceDsl;
import io.harness.idp.scorecard.datapointsdata.dsldataprovider.base.DslDataProvider;
import io.harness.idp.scorecard.datapointsdata.dsldataprovider.impl.HarnessPipelineSuccessPercent;
import io.harness.idp.scorecard.datapointsdata.dsldataprovider.impl.HarnessPolicyEvaluationDsl;
import io.harness.idp.scorecard.datapointsdata.dsldataprovider.impl.HarnessStoScanDsl;
import io.harness.idp.scorecard.datapointsdata.dsldataprovider.impl.HarnessTestPassingInCi;

import com.google.inject.Inject;
import lombok.AllArgsConstructor;

@OwnedBy(HarnessTeam.IDP)
@AllArgsConstructor(onConstructor = @__({ @Inject }))
public class HarnessDslFactory implements DataSourceDsl {
  HarnessStoScanDsl harnessStoScanDsl;
  HarnessPolicyEvaluationDsl harnessPolicyEvaluationDsl;
  HarnessPipelineSuccessPercent harnessPipelineSuccessPercent;
  HarnessTestPassingInCi harnessTestPassingInCi;
  private static final String HARNESS_STO_SCAN_SETUP_DSL = "harness_sto_scan_dsl";
  private static final String HARNESS_POLICY_EVALUATION_DSL = "harness_policy_evaluation_dsl";
  private static final String HARNESS_CI_SUCCESS_PERCENT_IN_SEVEN_DAYS = "harness_ci_success_percent_in_seven_days";

  private static final String HARNESS_TEST_PASSING_ON_CI_IS_ZERO = "harness_test_passing_on_ci_is_zero";

  @Override
  public DslDataProvider getDslDataProvider(String dslIdentifier) {
    switch (dslIdentifier) {
      case HARNESS_STO_SCAN_SETUP_DSL:
        return harnessStoScanDsl;
      case HARNESS_POLICY_EVALUATION_DSL:
        return harnessPolicyEvaluationDsl;
      case HARNESS_CI_SUCCESS_PERCENT_IN_SEVEN_DAYS:
        return harnessPipelineSuccessPercent;
      case HARNESS_TEST_PASSING_ON_CI_IS_ZERO:
        return harnessTestPassingInCi;
      default:
        throw new UnsupportedOperationException(
            String.format("For data source - Harness , Dsl is not supported - %s", dslIdentifier));
    }
  }
}
