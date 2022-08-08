/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.resourcegroup.framework.v1.remote.resource;

import static io.harness.annotations.dev.HarnessTeam.PL;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.ScopeLevel;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.resourcegroup.framework.v1.service.ResourceTypeService;
import io.harness.resourcegroup.v1.remote.dto.ResourceTypeDTO;
import io.harness.resourcegroup.v1.remote.resource.HarnessResourceTypeResource;
import io.harness.security.annotations.NextGenManagerAuth;

import com.google.inject.Inject;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;

@AllArgsConstructor(access = AccessLevel.PRIVATE, onConstructor = @__({ @Inject }))
@NextGenManagerAuth
@OwnedBy(PL)
public class HarnessResourceTypeResourceImpl implements HarnessResourceTypeResource {
  ResourceTypeService resourceTypeService;

  public ResponseDTO<ResourceTypeDTO> get(String accountIdentifier, String orgIdentifier, String projectIdentifier) {
    return ResponseDTO.newResponse(
        resourceTypeService.getResourceTypes(ScopeLevel.of(accountIdentifier, orgIdentifier, projectIdentifier)));
  }
}
