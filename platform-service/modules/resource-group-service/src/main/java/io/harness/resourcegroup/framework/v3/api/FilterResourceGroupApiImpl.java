/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */
package io.harness.resourcegroup.framework.v3.api;

import static io.harness.resourcegroup.ResourceGroupPermissions.VIEW_RESOURCEGROUP_PERMISSION;
import static io.harness.resourcegroup.ResourceGroupResourceTypes.RESOURCE_GROUP;

import io.harness.accesscontrol.NGAccessControlCheck;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.ng.beans.PageRequest;
import io.harness.resourcegroup.framework.v2.service.ResourceGroupService;
import io.harness.resourcegroup.framework.v3.mapper.ResourceGroupApiUtils;
import io.harness.resourcegroup.v1.remote.dto.ResourceGroupFilterDTO;
import io.harness.resourcegroup.v2.remote.dto.ResourceGroupResponse;
import io.harness.security.annotations.NextGenManagerAuth;
import io.harness.spec.server.resourcegroup.v1.FilterResourceGroupsApi;
import io.harness.spec.server.resourcegroup.v1.model.ResourceGroupFilterRequestBody;

import com.google.inject.Inject;
import java.util.stream.Collectors;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.ResponseBuilder;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import org.springframework.data.domain.Page;

@AllArgsConstructor(access = AccessLevel.PUBLIC, onConstructor = @__({ @Inject }))
@NextGenManagerAuth
@OwnedBy(HarnessTeam.PL)
public class FilterResourceGroupApiImpl implements FilterResourceGroupsApi {
  ResourceGroupService resourceGroupService;

  @Override
  @NGAccessControlCheck(resourceType = RESOURCE_GROUP, permission = VIEW_RESOURCEGROUP_PERMISSION)
  public Response filterResourceGroups(ResourceGroupFilterRequestBody requestBody, String account, Integer page,
      Integer limit, String sort, String order) {
    ResourceGroupFilterDTO resourceGroupFilterDTO = ResourceGroupApiUtils.getResourceFilterDTO(requestBody);
    PageRequest pageRequest = ResourceGroupApiUtils.getPageRequest(page, limit, sort, order);
    Page<ResourceGroupResponse> pageResponse = resourceGroupService.list(resourceGroupFilterDTO, pageRequest);
    ResponseBuilder responseBuilder = Response.ok();
    ResponseBuilder responseBuilderWithLinks = ResourceGroupApiUtils.addLinksHeader(
        responseBuilder, "/v1/resource-groups/filter", pageResponse.getContent().size(), page, limit);
    return responseBuilderWithLinks
        .entity(pageResponse.getContent()
                    .stream()
                    .map(ResourceGroupApiUtils::getResourceGroupResponse)
                    .collect(Collectors.toList()))
        .build();
  }
}
