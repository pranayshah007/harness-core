/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.remote;

import io.harness.beans.SortOrder;
import io.harness.ng.core.common.beans.NGTag;
import io.harness.ng.core.dto.OrganizationDTO;
import io.harness.ng.core.entities.Organization;
import io.harness.spec.server.ng.model.CreateOrganizationRequest;
import io.harness.spec.server.ng.model.OrganizationResponse;
import io.harness.spec.server.ng.model.UpdateOrganizationRequest;
import io.harness.utils.PageUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.data.domain.Pageable;

public class OrganizationApiMapper {
  public static OrganizationDTO getOrganizationDto(CreateOrganizationRequest request) {
    OrganizationDTO organizationDto = new OrganizationDTO();
    organizationDto.setIdentifier(request.getSlug());
    organizationDto.setName(request.getName());
    organizationDto.setDescription(request.getDescription());
    organizationDto.setTags(request.getTags());
    return organizationDto;
  }

  public static OrganizationDTO getOrganizationDto(String slug, UpdateOrganizationRequest request) {
    OrganizationDTO organizationDto = new OrganizationDTO();
    organizationDto.setIdentifier(slug);
    organizationDto.setName(request.getName());
    organizationDto.setDescription(request.getDescription());
    organizationDto.setTags(request.getTags());
    return organizationDto;
  }

  public static io.harness.spec.server.ng.model.OrganizationResponse getOrganizationResponse(
      Organization organization) {
    io.harness.spec.server.ng.model.OrganizationResponse organizationResponse = new OrganizationResponse();
    organizationResponse.setSlug(organization.getIdentifier());
    organizationResponse.setName(organization.getName());
    organizationResponse.setDescription(organization.getDescription());
    organizationResponse.setTags(getTags(organization.getTags()));
    organizationResponse.setCreatedAt(organization.getCreatedAt());
    organizationResponse.setLastModifiedAt(organization.getLastModifiedAt());
    organizationResponse.setHarnessManaged(organization.getHarnessManaged());
    return organizationResponse;
  }

  private static Map<String, String> getTags(List<NGTag> tags) {
    return tags.stream().collect(Collectors.toMap(NGTag::getKey, NGTag::getValue));
  }

  public static Pageable getPageRequest(Integer page, Integer limit) {
    List<SortOrder> sortOrders = new ArrayList<>();
    SortOrder harnessManagedOrder =
        SortOrder.Builder.aSortOrder()
            .withField(Organization.OrganizationKeys.harnessManaged, SortOrder.OrderType.DESC)
            .build();
    SortOrder nameOrder =
        SortOrder.Builder.aSortOrder().withField(Organization.OrganizationKeys.name, SortOrder.OrderType.ASC).build();
    sortOrders.add(harnessManagedOrder);
    sortOrders.add(nameOrder);

    List<String> orders = new ArrayList<>();
    for (SortOrder sortOrder : sortOrders) {
      orders.add(sortOrder.getFieldName() + PageUtils.COMMA_SEPARATOR + sortOrder.getOrderType());
    }
    return PageUtils.getPageRequest(page, limit, orders);
  }
}
