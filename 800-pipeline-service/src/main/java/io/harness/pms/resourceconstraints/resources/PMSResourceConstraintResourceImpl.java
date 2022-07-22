/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.resourceconstraints.resources;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.pms.annotations.PipelineServiceAuth;
import io.harness.pms.resourceconstraints.response.ResourceConstraintExecutionInfoDTO;
import io.harness.pms.resourceconstraints.service.PMSResourceConstraintService;

import com.google.inject.Inject;
import javax.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;

@AllArgsConstructor(access = AccessLevel.PACKAGE, onConstructor = @__({ @Inject }))
@PipelineServiceAuth
@OwnedBy(HarnessTeam.PIPELINE)
public class PMSResourceConstraintResourceImpl implements PMSResourceConstraintResource {
  private final PMSResourceConstraintService pmsResourceConstraintService;

  public ResponseDTO<ResourceConstraintExecutionInfoDTO> getResourceConstraintsExecutionInfo(
      @NotNull String accountId, @NotNull String resourceUnit) {
    ResourceConstraintExecutionInfoDTO response =
        pmsResourceConstraintService.getResourceConstraintExecutionInfo(accountId, resourceUnit);
    return ResponseDTO.newResponse(response);
  }
}
