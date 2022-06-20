/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.resourcegroup.resourceclient.organization;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.remote.client.NGRestUtils.getResponse;

import static java.util.stream.Collectors.toList;
import static org.apache.commons.lang3.StringUtils.stripToNull;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.Scope;
import io.harness.beans.ScopeLevel;
import io.harness.eventsframework.EventsFrameworkMetadataConstants;
import io.harness.eventsframework.consumer.Message;
import io.harness.eventsframework.entity_crud.organization.OrganizationEntityChangeDTO;
import io.harness.ng.beans.PageResponse;
import io.harness.ng.core.dto.OrganizationResponse;
import io.harness.organization.remote.OrganizationClient;
import io.harness.resourcegroup.beans.ValidatorType;
import io.harness.resourcegroup.framework.v1.service.Resource;
import io.harness.resourcegroup.framework.v1.service.ResourceInfo;
import io.harness.resourcegroup.v2.model.AttributeFilter;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import com.google.protobuf.InvalidProtocolBufferException;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;

@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Slf4j
@OwnedBy(PL)
public class OrganizationResourceImpl implements Resource {
  OrganizationClient organizationClient;

  @Inject
  public OrganizationResourceImpl(@Named("PRIVILEGED") OrganizationClient organizationClient) {
    this.organizationClient = organizationClient;
  }

  @Override
  public List<Boolean> validate(List<String> resourceIds, Scope scope) {
    if (isEmpty(resourceIds)) {
      return Collections.emptyList();
    }
    PageResponse<OrganizationResponse> organizations =
        getResponse(organizationClient.listOrganizations(scope.getAccountIdentifier(), resourceIds));
    Set<String> validResourceIds =
        organizations.getContent().stream().map(e -> e.getOrganization().getIdentifier()).collect(Collectors.toSet());
    return resourceIds.stream()
        .map(resourceId -> validResourceIds.contains(resourceId) && scope.getOrgIdentifier().equals(resourceId))
        .collect(toList());
  }

  @Override
  public Map<ScopeLevel, EnumSet<ValidatorType>> getSelectorKind() {
    return Collections.emptyMap();
  }

  @Override
  public String getType() {
    return "ORGANIZATION";
  }

  @Override
  public Set<ScopeLevel> getValidScopeLevels() {
    return Collections.emptySet();
  }

  @Override
  public Optional<String> getEventFrameworkEntityType() {
    return Optional.of(EventsFrameworkMetadataConstants.ORGANIZATION_ENTITY);
  }

  @Override
  public ResourceInfo getResourceInfoFromEvent(Message message) {
    OrganizationEntityChangeDTO organizationEntityChangeDTO = null;
    try {
      organizationEntityChangeDTO = OrganizationEntityChangeDTO.parseFrom(message.getMessage().getData());
    } catch (InvalidProtocolBufferException e) {
      log.error("Exception in unpacking EntityChangeDTO for key {}", message.getId(), e);
    }
    if (Objects.isNull(organizationEntityChangeDTO)) {
      return null;
    }
    return ResourceInfo.builder()
        .accountIdentifier(stripToNull(organizationEntityChangeDTO.getAccountIdentifier()))
        .orgIdentifier(stripToNull(organizationEntityChangeDTO.getIdentifier()))
        .resourceType(getType())
        .resourceIdentifier(stripToNull(organizationEntityChangeDTO.getIdentifier()))
        .build();
  }

  @Override
  public boolean isValidAttributeFilter(AttributeFilter attributeFilter) {
    return false;
  }
}
