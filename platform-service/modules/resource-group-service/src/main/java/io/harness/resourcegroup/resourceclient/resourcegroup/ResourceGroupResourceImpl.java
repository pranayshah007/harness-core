/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.resourcegroup.resourceclient.resourcegroup;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.resourcegroup.beans.ValidatorType.BY_RESOURCE_IDENTIFIER;
import static io.harness.resourcegroup.beans.ValidatorType.BY_RESOURCE_TYPE;
import static io.harness.resourcegroup.beans.ValidatorType.BY_RESOURCE_TYPE_INCLUDING_CHILD_SCOPES;

import static java.util.stream.Collectors.toList;
import static org.apache.commons.lang3.StringUtils.stripToNull;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.Scope;
import io.harness.beans.ScopeLevel;
import io.harness.eventsframework.EventsFrameworkMetadataConstants;
import io.harness.eventsframework.consumer.Message;
import io.harness.eventsframework.entity_crud.resourcegroup.ResourceGroupEntityChangeDTO;
import io.harness.remote.client.NGRestUtils;
import io.harness.resourcegroup.beans.ValidatorType;
import io.harness.resourcegroup.framework.v1.service.Resource;
import io.harness.resourcegroup.framework.v1.service.ResourceInfo;
import io.harness.resourcegroup.v1.remote.dto.ResourceGroupFilterDTO;
import io.harness.resourcegroup.v2.model.AttributeFilter;
import io.harness.resourcegroup.v2.remote.dto.ResourceGroupResponse;
import io.harness.resourcegroupclient.remote.ResourceGroupClient;

import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;
import com.google.inject.name.Named;
import com.google.protobuf.InvalidProtocolBufferException;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(PL)
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Slf4j
public class ResourceGroupResourceImpl implements Resource {
  ResourceGroupClient resourceGroupClient;

  @Inject
  public ResourceGroupResourceImpl(@Named("PRIVILEGED") ResourceGroupClient resourceGroupClient) {
    this.resourceGroupClient = resourceGroupClient;
  }

  @Override
  public List<Boolean> validate(List<String> resourceIds, Scope scope) {
    if (isEmpty(resourceIds)) {
      return Collections.emptyList();
    }
    ResourceGroupFilterDTO resourceGroupFilterDTO = ResourceGroupFilterDTO.builder()
                                                        .accountIdentifier(scope.getAccountIdentifier())
                                                        .orgIdentifier(scope.getOrgIdentifier())
                                                        .projectIdentifier(scope.getProjectIdentifier())
                                                        .identifierFilter(new HashSet<>(resourceIds))
                                                        .build();
    List<ResourceGroupResponse> resourceGroups =
        NGRestUtils
            .getResponse(resourceGroupClient.getFilteredResourceGroups(
                resourceGroupFilterDTO, scope.getAccountIdentifier(), 0, resourceIds.size()))
            .getContent();
    Set<Object> validResourceIds =
        resourceGroups.stream().map(e -> e.getResourceGroup().getIdentifier()).collect(Collectors.toSet());
    return resourceIds.stream().map(validResourceIds::contains).collect(toList());
  }

  @Override
  public Map<ScopeLevel, EnumSet<ValidatorType>> getSelectorKind() {
    return ImmutableMap.of(ScopeLevel.ACCOUNT,
        EnumSet.of(BY_RESOURCE_IDENTIFIER, BY_RESOURCE_TYPE, BY_RESOURCE_TYPE_INCLUDING_CHILD_SCOPES),
        ScopeLevel.ORGANIZATION,
        EnumSet.of(BY_RESOURCE_IDENTIFIER, BY_RESOURCE_TYPE, BY_RESOURCE_TYPE_INCLUDING_CHILD_SCOPES),
        ScopeLevel.PROJECT, EnumSet.of(BY_RESOURCE_IDENTIFIER, BY_RESOURCE_TYPE));
  }

  @Override
  public String getType() {
    return "RESOURCEGROUP";
  }

  @Override
  public Set<ScopeLevel> getValidScopeLevels() {
    return EnumSet.of(ScopeLevel.ACCOUNT, ScopeLevel.ORGANIZATION, ScopeLevel.PROJECT);
  }

  @Override
  public Optional<String> getEventFrameworkEntityType() {
    return Optional.of(EventsFrameworkMetadataConstants.RESOURCE_GROUP);
  }

  @Override
  public ResourceInfo getResourceInfoFromEvent(Message message) {
    ResourceGroupEntityChangeDTO entityChangeDTO = null;

    try {
      entityChangeDTO = ResourceGroupEntityChangeDTO.parseFrom(message.getMessage().getData());
    } catch (InvalidProtocolBufferException e) {
      log.error("Exception in unpacking EntityChangeDTO for resource group event with key {}", message.getId(), e);
    }

    if (Objects.isNull(entityChangeDTO)) {
      return null;
    }

    if (isEmpty(stripToNull(entityChangeDTO.getAccountIdentifier()))) {
      return null;
    }

    return ResourceInfo.builder()
        .accountIdentifier(stripToNull(entityChangeDTO.getAccountIdentifier()))
        .orgIdentifier(stripToNull(entityChangeDTO.getOrgIdentifier()))
        .projectIdentifier(stripToNull(entityChangeDTO.getProjectIdentifier()))
        .resourceType(getType())
        .resourceIdentifier(entityChangeDTO.getIdentifier())
        .build();
  }

  @Override
  public boolean isValidAttributeFilter(AttributeFilter attributeFilter) {
    return false;
  }
}
