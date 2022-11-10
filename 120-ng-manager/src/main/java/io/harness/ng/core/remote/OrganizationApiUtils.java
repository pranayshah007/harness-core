/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.remote;

import static io.harness.NGCommonEntityConstants.NEXT_REL;
import static io.harness.NGCommonEntityConstants.PAGE;
import static io.harness.NGCommonEntityConstants.PAGE_SIZE;
import static io.harness.NGCommonEntityConstants.PREVIOUS_REL;
import static io.harness.NGCommonEntityConstants.SELF_REL;
import static io.harness.beans.SortOrder.Builder.aSortOrder;
import static io.harness.beans.SortOrder.OrderType.DESC;
import static io.harness.ng.core.entities.Organization.OrganizationKeys;

import static javax.ws.rs.core.UriBuilder.fromPath;

import io.harness.beans.SortOrder;
import io.harness.ng.beans.PageRequest;
import io.harness.ng.core.common.beans.NGTag;
import io.harness.ng.core.dto.OrganizationDTO;
import io.harness.ng.core.entities.Organization;
import io.harness.spec.server.ng.v1.model.CreateOrganizationRequest;
import io.harness.spec.server.ng.v1.model.OrganizationResponse;
import io.harness.spec.server.ng.v1.model.UpdateOrganizationRequest;
import io.harness.utils.PageUtils;

import com.google.common.collect.ImmutableList;
import com.google.inject.Inject;
import io.dropwizard.jersey.validation.JerseyViolationException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import javax.validation.ConstraintViolation;
import javax.validation.Validator;
import javax.ws.rs.core.Link;
import javax.ws.rs.core.Response.ResponseBuilder;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.EnumUtils;
import org.springframework.data.domain.Pageable;

public class OrganizationApiUtils {
  public static final int FIRST_PAGE = 1;

  private final Validator validator;

  @Inject
  public OrganizationApiUtils(Validator validator) {
    this.validator = validator;
  }

  public OrganizationDTO getOrganizationDto(CreateOrganizationRequest request) {
    OrganizationDTO organizationDto = new OrganizationDTO();
    organizationDto.setIdentifier(request.getOrg().getSlug());
    organizationDto.setName(request.getOrg().getName());
    organizationDto.setDescription(request.getOrg().getDescription());
    organizationDto.setTags(request.getOrg().getTags());

    Set<ConstraintViolation<OrganizationDTO>> violations = validator.validate(organizationDto);
    if (!violations.isEmpty()) {
      throw new JerseyViolationException(violations, null);
    }
    return organizationDto;
  }

  public OrganizationDTO getOrganizationDto(UpdateOrganizationRequest request) {
    OrganizationDTO organizationDto = new OrganizationDTO();
    organizationDto.setIdentifier(request.getOrg().getSlug());
    organizationDto.setName(request.getOrg().getName());
    organizationDto.setDescription(request.getOrg().getDescription());
    organizationDto.setTags(request.getOrg().getTags());

    Set<ConstraintViolation<OrganizationDTO>> violations = validator.validate(organizationDto);
    if (!violations.isEmpty()) {
      throw new JerseyViolationException(violations, null);
    }
    return organizationDto;
  }

  public OrganizationResponse getOrganizationResponse(Organization organization) {
    OrganizationResponse organizationResponse = new OrganizationResponse();
    io.harness.spec.server.ng.v1.model.Organization org = new io.harness.spec.server.ng.v1.model.Organization();
    org.setSlug(organization.getIdentifier());
    org.setName(organization.getName());
    org.setDescription(organization.getDescription());
    org.setTags(getTags(organization.getTags()));
    organizationResponse.setOrg(org);
    organizationResponse.setCreated(organization.getCreatedAt());
    organizationResponse.setUpdated(organization.getLastModifiedAt());
    organizationResponse.setHarnessManaged(organization.getHarnessManaged());
    return organizationResponse;
  }

  private Map<String, String> getTags(List<NGTag> tags) {
    if (CollectionUtils.isEmpty(tags)) {
      return Collections.emptyMap();
    }
    return tags.stream().collect(Collectors.toMap(NGTag::getKey, NGTag::getValue));
  }

  public Pageable getPageRequest(int page, int limit, String sort, String order) {
    List<SortOrder> sortOrders;
    String mappedFieldName = getFieldName(sort);
    if (mappedFieldName != null) {
      SortOrder.OrderType fieldOrder = EnumUtils.getEnum(SortOrder.OrderType.class, order, DESC);
      sortOrders = ImmutableList.of(aSortOrder().withField(mappedFieldName, fieldOrder).build());
    } else {
      sortOrders = ImmutableList.of(aSortOrder().withField(OrganizationKeys.lastModifiedAt, DESC).build());
    }
    return PageUtils.getPageRequest(new PageRequest(page, limit, sortOrders));
  }

  private String getFieldName(String sort) {
    PageUtils.SortFields sortField = PageUtils.SortFields.fromValue(sort);
    if (sortField == null) {
      sortField = PageUtils.SortFields.UNSUPPORTED;
    }
    switch (sortField) {
      case SLUG:
        return OrganizationKeys.identifier;
      case NAME:
        return OrganizationKeys.name;
      case CREATED:
        return OrganizationKeys.createdAt;
      case UPDATED:
        return OrganizationKeys.lastModifiedAt;
      case UNSUPPORTED:
      default:
        return null;
    }
  }

  public ResponseBuilder addLinksHeader(
      ResponseBuilder responseBuilder, String path, int currentResultCount, int page, int limit) {
    ArrayList<Link> links = new ArrayList<>();

    links.add(
        Link.fromUri(fromPath(path).queryParam(PAGE, page).queryParam(PAGE_SIZE, limit).build()).rel(SELF_REL).build());

    if (page >= FIRST_PAGE) {
      links.add(Link.fromUri(fromPath(path).queryParam(PAGE, page - 1).queryParam(PAGE_SIZE, limit).build())
                    .rel(PREVIOUS_REL)
                    .build());
    }
    if (limit == currentResultCount) {
      links.add(Link.fromUri(fromPath(path).queryParam(PAGE, page + 1).queryParam(PAGE_SIZE, limit).build())
                    .rel(NEXT_REL)
                    .build());
    }
    return responseBuilder.links(links.toArray(new Link[links.size()]));
  }
}