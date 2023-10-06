/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ssca.services;

import io.harness.entities.Instance;
import io.harness.spec.server.ssca.v1.model.ArtifactDeploymentViewRequestBody;
import io.harness.ssca.entities.ArtifactEntity;
import io.harness.ssca.entities.CdInstanceSummary;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface CdInstanceSummaryService {
  boolean upsertInstance(Instance instance);

  boolean removeInstance(Instance instance);

  Page<CdInstanceSummary> getCdInstanceSummaries(String accountId, String orgIdentifier, String projectIdentifier,
      ArtifactEntity artifact, ArtifactDeploymentViewRequestBody filterBody, Pageable pageable);

  CdInstanceSummary getCdInstanceSummary(String accountId, String orgIdentifier, String projectIdentifier,
      String artifactCorrelationId, String envIdentifier);
}
