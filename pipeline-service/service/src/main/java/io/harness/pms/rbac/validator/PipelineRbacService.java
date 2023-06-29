/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.rbac.validator;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.ng.core.EntityDetail;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;

@OwnedBy(HarnessTeam.PIPELINE)
public interface PipelineRbacService {
  /**
   * validates the rbac on static referred entities for the given yaml
   * It does not validate rbac on runtime expressions
   */
  void extractAndValidateStaticallyReferredEntities(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, String pipelineId, String pipelineYaml);

  void extractAndValidateStaticallyReferredEntities(String accountIdentifier, String orgIdentifier,
      String projectIdentifier, String pipelineId, JsonNode pipelineJsonNode);

  void validateStaticallyReferredEntities(List<EntityDetail> entityDetails);
}
