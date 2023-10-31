/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.scorecard.datapoints.parser;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.idp.scorecard.datapoints.constants.DataPoints;

import com.google.inject.Inject;
import lombok.AllArgsConstructor;

@OwnedBy(HarnessTeam.IDP)
@AllArgsConstructor(onConstructor = @__({ @Inject }))
public class GithubDataPointParserFactory implements DataPointParserFactory {
  private GithubMeanTimeToMergeParser githubMeanTimeToMergeParser;
  private GithubIsBranchProtectedParser githubIsBranchProtectedParser;
  private GithubFileExistsParser githubFileExistsParser;
  private GithubWorkflowsCountParser githubWorkflowsCountParser;
  private GithubWorkflowSuccessRateParser githubWorkflowSuccessRateParser;
  private GithubMeanTimeToCompleteWorkflowRunsParser githubMeanTimeToCompleteWorkflowRunsParser;
  private GithubAlertsCountParser githubAlertsCountParser;
  private GithubPullRequestsCountParser githubPullRequestsCountParser;
  private GithubFileContentsParser githubFileContentsParser;
  private GithubFileContainsParser githubFileContainsParser;

  public DataPointParser getParser(String identifier) {
    switch (identifier) {
      case DataPoints.PULL_REQUEST_MEAN_TIME_TO_MERGE:
        return githubMeanTimeToMergeParser;
      case DataPoints.IS_BRANCH_PROTECTED:
        return githubIsBranchProtectedParser;
      case DataPoints.IS_FILE_EXISTS:
        return githubFileExistsParser;
      case DataPoints.FILE_CONTENTS:
        return githubFileContentsParser;
      case DataPoints.FILE_CONTAINS:
        return githubFileContainsParser;
      case DataPoints.WORKFLOWS_COUNT:
        return githubWorkflowsCountParser;
      case DataPoints.WORKFLOW_SUCCESS_RATE:
        return githubWorkflowSuccessRateParser;
      case DataPoints.MEAN_TIME_TO_COMPLETE_WORKFLOW_RUNS:
      case DataPoints.MEAN_TIME_TO_COMPLETE_SUCCESS_WORKFLOW_RUNS:
        return githubMeanTimeToCompleteWorkflowRunsParser;
      case DataPoints.OPEN_DEPENDABOT_ALERTS:
      case DataPoints.OPEN_CODE_SCANNING_ALERTS:
      case DataPoints.OPEN_SECRET_SCANNING_ALERTS:
        return githubAlertsCountParser;
      case DataPoints.OPEN_PULL_REQUESTS_BY_ACCOUNT:
        return githubPullRequestsCountParser;
      // Add more cases for other parsers
      default:
        throw new UnsupportedOperationException(String.format("Could not find DataPoint parser for %s", identifier));
    }
  }
}
