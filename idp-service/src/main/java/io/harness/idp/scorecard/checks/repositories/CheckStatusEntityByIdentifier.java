/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.scorecard.checks.repositories;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.idp.scorecard.checks.entity.CheckStatusEntity;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@OwnedBy(HarnessTeam.IDP)
public class CheckStatusEntityByIdentifier {
  private String identifier;
  private boolean isCustom;
  private CheckStatusEntity checkStatusEntity;
}
