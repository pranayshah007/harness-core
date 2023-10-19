/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ssca.services;

import io.harness.spec.server.ssca.v1.model.EnforcementResultDTO;
import io.harness.ssca.beans.AllowList.AllowListItem;
import io.harness.ssca.beans.AllowList.AllowListRuleType;
import io.harness.ssca.beans.DenyList.DenyListItem;
import io.harness.ssca.entities.ArtifactEntity;
import io.harness.ssca.entities.EnforcementResultEntity;
import io.harness.ssca.entities.NormalizedSBOMComponentEntity;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface EnforcementResultService {
  EnforcementResultEntity getEnforcementResults(NormalizedSBOMComponentEntity violatedComponent, String violationType,
      String violationDetails, ArtifactEntity artifact, String enforcementId);

  Page<EnforcementResultEntity> getPolicyViolations(String accountId, String orgIdentifier, String projectIdentifier,
      String enforcementId, String searchText, Pageable pageable);
  String getViolationDetails(
      NormalizedSBOMComponentEntity component, AllowListItem allowListItem, AllowListRuleType type);

  String getViolationDetails(NormalizedSBOMComponentEntity component, DenyListItem denyListItem);

  void create(EnforcementResultDTO enforcementResultDTO);
}
