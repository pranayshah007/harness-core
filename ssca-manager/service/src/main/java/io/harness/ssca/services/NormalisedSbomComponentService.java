/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ssca.services;

import io.harness.spec.server.ssca.v1.model.Artifact;
import io.harness.spec.server.ssca.v1.model.ArtifactComponentViewRequestBody;
import io.harness.ssca.entities.ArtifactEntity;
import io.harness.ssca.entities.NormalizedSBOMComponentEntity;

import javax.ws.rs.core.Response;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface NormalisedSbomComponentService {
  Response listNormalizedSbomComponent(
      String orgIdentifier, String projectIdentifier, Integer page, Integer limit, Artifact body, String accountId);

  Page<NormalizedSBOMComponentEntity> getNormalizedSbomComponents(String accountId, String orgIdentifier,
      String projectIdentifier, ArtifactEntity artifact, ArtifactComponentViewRequestBody filterBody,
      Pageable pageable);
}
