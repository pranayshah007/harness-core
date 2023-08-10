/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.scorecard.scorecards.service;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.idp.scorecard.scorecards.beans.ScorecardCheckFullDetails;
import io.harness.idp.scorecard.scorecards.entity.ScorecardEntity;
import io.harness.spec.server.idp.v1.model.Scorecard;
import io.harness.spec.server.idp.v1.model.ScorecardDetailsRequest;
import io.harness.spec.server.idp.v1.model.ScorecardDetailsResponse;

import java.util.List;

@OwnedBy(HarnessTeam.IDP)
public interface ScorecardService {
  List<Scorecard> getAllScorecardsAndChecksDetails(String accountIdentifier);
  List<ScorecardCheckFullDetails> getAllScorecardCheckFullDetails(
      String accountIdentifier, List<String> scorecardIdentifiers);

  void saveScorecard(ScorecardDetailsRequest scorecardDetailsRequest, String accountIdentifier);
  void updateScorecard(ScorecardDetailsRequest scorecardDetailsRequest, String accountIdentifier);
  ScorecardDetailsResponse getScorecardDetails(String accountIdentifier, String identifier);
  void deleteScorecard(String accountIdentifier, String identifier);
}
