/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.scorecard.datapoints.parser;

import static io.harness.idp.scorecard.datapoints.constants.DataPoints.*;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;

import com.google.inject.Inject;
import lombok.AllArgsConstructor;

@OwnedBy(HarnessTeam.IDP)
@AllArgsConstructor(onConstructor = @__({ @Inject }))
public class GitlabDataPointParserFactory implements DataPointParserFactory {
  private GitlabMeanTimeToMergeParser gitlabMeanTimeToMergeParser;
  private GitlabIsBranchProtectedParser gitlabIsBranchProtectedParser;
  private GitlabFileExistsParser gitlabFileExistsParser;

  public DataPointParser getParser(String identifier) {
    switch (identifier) {
      case PULL_REQUEST_MEAN_TIME_TO_MERGE:
        return gitlabMeanTimeToMergeParser;
      case IS_BRANCH_PROTECTED:
        return gitlabIsBranchProtectedParser;
      case IS_FILE_EXISTS:
        return gitlabFileExistsParser;
      // Add more cases for other parsers
      default:
        throw new UnsupportedOperationException(String.format("Could not find DataPoint parser for %s", identifier));
    }
  }
}
