/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.resourcegroup.resourceclient.connector;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.resourcegroup.beans.ValidatorType.BY_RESOURCE_IDENTIFIER;
import static io.harness.resourcegroup.beans.ValidatorType.BY_RESOURCE_TYPE;
import static io.harness.resourcegroup.beans.ValidatorType.BY_RESOURCE_TYPE_INCLUDING_CHILD_SCOPES;

import static java.util.stream.Collectors.toList;
import static org.apache.commons.lang3.StringUtils.stripToNull;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.Scope;
import io.harness.beans.ScopeLevel;
import io.harness.connector.ConnectorFilterPropertiesDTO;
import io.harness.connector.ConnectorResourceClient;
import io.harness.connector.ConnectorResponseDTO;
import io.harness.eventsframework.EventsFrameworkMetadataConstants;
import io.harness.eventsframework.consumer.Message;
import io.harness.eventsframework.entity_crud.EntityChangeDTO;
import io.harness.remote.client.NGRestUtils;
import io.harness.resourcegroup.beans.ValidatorType;
import io.harness.resourcegroup.framework.v1.service.Resource;
import io.harness.resourcegroup.framework.v1.service.ResourceInfo;
import io.harness.resourcegroup.v2.model.AttributeFilter;

import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;
import com.google.protobuf.InvalidProtocolBufferException;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import javax.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;

@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@AllArgsConstructor(access = AccessLevel.PUBLIC, onConstructor = @__({ @Inject }))
@Slf4j
@OwnedBy(PL)
public class ConnectorResourceImpl implements Resource {
  ConnectorResourceClient connectorResourceClient;

  @Override
  public List<Boolean> validate(List<String> resourceIds, Scope scope) {
    if (isEmpty(resourceIds)) {
      return Collections.emptyList();
    }
    List<ConnectorResponseDTO> secretManagers =
        NGRestUtils
            .getResponse(connectorResourceClient.listConnectors(scope.getAccountIdentifier(), scope.getOrgIdentifier(),
                scope.getProjectIdentifier(), 0, resourceIds.size(),
                ConnectorFilterPropertiesDTO.builder().connectorIdentifiers(resourceIds).build(), true))
            .getContent();
    Set<String> validResourceIds =
        secretManagers.stream().map(e -> e.getConnector().getIdentifier()).collect(Collectors.toSet());
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
    return "CONNECTOR";
  }

  @Override
  public Set<ScopeLevel> getValidScopeLevels() {
    return EnumSet.of(ScopeLevel.ACCOUNT, ScopeLevel.ORGANIZATION, ScopeLevel.PROJECT);
  }

  @Override
  public Optional<String> getEventFrameworkEntityType() {
    return Optional.of(EventsFrameworkMetadataConstants.CONNECTOR_ENTITY);
  }

  @Override
  public ResourceInfo getResourceInfoFromEvent(Message message) {
    EntityChangeDTO entityChangeDTO = null;
    try {
      entityChangeDTO = EntityChangeDTO.parseFrom(message.getMessage().getData());
    } catch (InvalidProtocolBufferException e) {
      log.error("Exception in unpacking EntityChangeDTO for key {}", message.getId(), e);
    }
    if (Objects.isNull(entityChangeDTO)) {
      return null;
    }
    return ResourceInfo.builder()
        .accountIdentifier(stripToNull(entityChangeDTO.getAccountIdentifier().getValue()))
        .orgIdentifier(stripToNull(entityChangeDTO.getOrgIdentifier().getValue()))
        .projectIdentifier(stripToNull(entityChangeDTO.getProjectIdentifier().getValue()))
        .resourceType(getType())
        .resourceIdentifier(entityChangeDTO.getIdentifier().getValue())
        .build();
  }

  @Override
  public boolean isValidAttributeFilter(@NotNull AttributeFilter attributeFilter) {
    String validAttributeName = "category";
    return validAttributeName.equals(attributeFilter.getAttributeName())
        && isNotEmpty(attributeFilter.getAttributeValues());
  }
}
