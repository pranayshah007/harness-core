/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.scorecard.scores.service;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.eventsframework.entity_crud.EntityChangeDTO;
import io.harness.spec.server.idp.v1.model.ScorecardRecalibrateInfo;

@OwnedBy(HarnessTeam.IDP)
public interface AsyncScoreComputationService {
  ScorecardRecalibrateInfo getStartTimeOfInProgressScoreComputation(
      String harnessAccount, String scorecardIdentifier, String entityIdentifier);

  ScorecardRecalibrateInfo logScoreComputationRequestAndPublishEvent(
      String harnessAccount, String scorecardIdentifier, String entityIdentifier);

  void deleteScoreComputationRequest(String harnessAccount, String scorecardIdentifier, String entityIdentifier);

  void triggerScoreComputation(EntityChangeDTO entityChangeDTO);
}
