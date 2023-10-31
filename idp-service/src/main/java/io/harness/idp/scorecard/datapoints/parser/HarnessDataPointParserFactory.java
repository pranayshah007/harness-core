/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.scorecard.datapoints.parser;

import static io.harness.idp.scorecard.datapoints.constants.DataPoints.IS_POLICY_EVALUATION_SUCCESSFUL_IN_PIPELINE;
import static io.harness.idp.scorecard.datapoints.constants.DataPoints.PERCENTAGE_OF_CI_PIPELINE_FAILING_IN_SEVEN_DAYS;
import static io.harness.idp.scorecard.datapoints.constants.DataPoints.PIPELINE_TEST_FAILING_IN_CI_IS_ZERO;
import static io.harness.idp.scorecard.datapoints.constants.DataPoints.STO_ADDED_IN_PIPELINE;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;

import com.google.inject.Inject;
import lombok.AllArgsConstructor;

@OwnedBy(HarnessTeam.IDP)
@AllArgsConstructor(onConstructor = @__({ @Inject }))
public class HarnessDataPointParserFactory implements DataPointParserFactory {
  private PipelineStoStageAddedParser pipelineStoStageAddedParser;
  private PipelineIsPolicyEvaluationSuccessfulParser pipelineIsPolicyEvaluationSuccessfulParser;
  private PipelinePercentageOfCIPipelineFailingInSevenDaysParser pipelinePercentageOfCIPipelineFailingInSevenDaysParser;
  private PipelineTestFailingInCiIsZeroParser pipelineTestFailingInCiIsZeroParser;

  public DataPointParser getParser(String identifier) {
    switch (identifier) {
      case STO_ADDED_IN_PIPELINE:
        return pipelineStoStageAddedParser;
      case IS_POLICY_EVALUATION_SUCCESSFUL_IN_PIPELINE:
        return pipelineIsPolicyEvaluationSuccessfulParser;
      case PERCENTAGE_OF_CI_PIPELINE_FAILING_IN_SEVEN_DAYS:
        return pipelinePercentageOfCIPipelineFailingInSevenDaysParser;
      case PIPELINE_TEST_FAILING_IN_CI_IS_ZERO:
        return pipelineTestFailingInCiIsZeroParser;
      default:
        throw new UnsupportedOperationException(String.format("Could not find DataPoint parser for %s", identifier));
    }
  }
}
