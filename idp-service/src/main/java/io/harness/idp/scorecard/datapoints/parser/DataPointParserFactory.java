/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.scorecard.datapoints.parser;

import static io.harness.idp.scorecard.datapoints.constants.DataPoints.GITHUB_PR_MMTM;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;

@OwnedBy(HarnessTeam.IDP)
public class DataPointParserFactory {
  private GithubMeanTimeToMergeParser githubMeanTimeToMergeParser;

  public DataPointParser getParser(String identifier) {
    switch (identifier) {
      case GITHUB_PR_MMTM:
        return githubMeanTimeToMergeParser;
      // Add more cases for other parsers
      default:
        throw new UnsupportedOperationException(String.format("Could not find DataPoint parser for %s", identifier));
    }
  }
}
