/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */
package io.harness.idp.scorecard.datapointsdata.datapointvalueparser.factory;

import static io.harness.idp.scorecard.datapoints.constants.DataPoints.IS_POLICY_EVALUATION_SUCCESSFUL_IN_PIPELINE;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.idp.scorecard.datapointsdata.datapointvalueparser.base.PipelineExecutionInfo;
import io.harness.idp.scorecard.datapointsdata.datapointvalueparser.impl.PipelinePolicyEvaluationParser;

import com.google.inject.Inject;
import lombok.AllArgsConstructor;

@OwnedBy(HarnessTeam.IDP)
@AllArgsConstructor(onConstructor = @__({ @Inject }))
public class PipelineExecutionResponseFactory {
  PipelinePolicyEvaluationParser pipelinePolicyEvaluationParser;

  public PipelineExecutionInfo getResponseParser(String identifier) {
    switch (identifier) {
      case IS_POLICY_EVALUATION_SUCCESSFUL_IN_PIPELINE:
        return pipelinePolicyEvaluationParser;
      default:
        throw new UnsupportedOperationException(String.format("Could not find response parser for %s", identifier));
    }
  }
}
