/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.scorecard.datapoints.parser;

import static io.harness.idp.scorecard.datapoints.constants.DataPoints.FILE_CONTAINS;
import static io.harness.idp.scorecard.datapoints.constants.DataPoints.FILE_CONTENTS;
import static io.harness.idp.scorecard.datapoints.constants.DataPoints.IS_BRANCH_PROTECTED;
import static io.harness.idp.scorecard.datapoints.constants.DataPoints.IS_FILE_EXISTS;
import static io.harness.idp.scorecard.datapoints.constants.DataPoints.MEAN_TIME_TO_COMPLETE_SUCCESS_WORKFLOW_RUNS;
import static io.harness.idp.scorecard.datapoints.constants.DataPoints.MEAN_TIME_TO_COMPLETE_WORKFLOW_RUNS;
import static io.harness.idp.scorecard.datapoints.constants.DataPoints.OPEN_CODE_SCANNING_ALERTS;
import static io.harness.idp.scorecard.datapoints.constants.DataPoints.OPEN_DEPENDABOT_ALERTS;
import static io.harness.idp.scorecard.datapoints.constants.DataPoints.OPEN_PULL_REQUESTS_BY_ACCOUNT;
import static io.harness.idp.scorecard.datapoints.constants.DataPoints.OPEN_SECRET_SCANNING_ALERTS;
import static io.harness.idp.scorecard.datapoints.constants.DataPoints.PULL_REQUEST_MEAN_TIME_TO_MERGE;
import static io.harness.idp.scorecard.datapoints.constants.DataPoints.WORKFLOWS_COUNT;
import static io.harness.idp.scorecard.datapoints.constants.DataPoints.WORKFLOW_SUCCESS_RATE;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;

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
      case PULL_REQUEST_MEAN_TIME_TO_MERGE:
        return githubMeanTimeToMergeParser;
      case IS_BRANCH_PROTECTED:
        return githubIsBranchProtectedParser;
      case IS_FILE_EXISTS:
        return githubFileExistsParser;
      case FILE_CONTENTS:
        return githubFileContentsParser;
      case FILE_CONTAINS:
        return githubFileContainsParser;
      case WORKFLOWS_COUNT:
        return githubWorkflowsCountParser;
      case WORKFLOW_SUCCESS_RATE:
        return githubWorkflowSuccessRateParser;
      case MEAN_TIME_TO_COMPLETE_WORKFLOW_RUNS:
      case MEAN_TIME_TO_COMPLETE_SUCCESS_WORKFLOW_RUNS:
        return githubMeanTimeToCompleteWorkflowRunsParser;
      case OPEN_DEPENDABOT_ALERTS:
      case OPEN_CODE_SCANNING_ALERTS:
      case OPEN_SECRET_SCANNING_ALERTS:
        return githubAlertsCountParser;
      case OPEN_PULL_REQUESTS_BY_ACCOUNT:
        return githubPullRequestsCountParser;
      // Add more cases for other parsers
      default:
        throw new UnsupportedOperationException(String.format("Could not find DataPoint parser for %s", identifier));
    }
  }
}
