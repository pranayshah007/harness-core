/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.resourcegroup.resourceclient.streamingdestination;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.resourcegroup.beans.ValidatorType.BY_RESOURCE_IDENTIFIER;
import static io.harness.resourcegroup.beans.ValidatorType.BY_RESOURCE_TYPE;
import static io.harness.resourcegroup.beans.ValidatorType.BY_RESOURCE_TYPE_INCLUDING_CHILD_SCOPES;

import static java.util.stream.Collectors.toList;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.Scope;
import io.harness.beans.ScopeLevel;
import io.harness.eventsframework.EventsFrameworkMetadataConstants;
import io.harness.eventsframework.consumer.Message;
import io.harness.eventsframework.entity_crud.EntityChangeDTO;
import io.harness.resourcegroup.beans.ValidatorType;
import io.harness.resourcegroup.framework.v1.service.Resource;
import io.harness.resourcegroup.framework.v1.service.ResourceInfo;
import io.harness.resourcegroup.v2.model.AttributeFilter;

import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;
import com.google.protobuf.InvalidProtocolBufferException;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(PL)
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@AllArgsConstructor(access = AccessLevel.PUBLIC, onConstructor = @__({ @Inject }))
@Slf4j
public class StreamingDestinationResourceImpl implements Resource {
  public static final String type = "STREAMING_DESTINATION";

  @Override
  public List<Boolean> validate(List<String> resourceIds, Scope scope) {
    //        TODO: REPLACE THIS TO DO ACTUAL VALIDATION
    return resourceIds.stream().map(resourceId -> true).collect(toList());
  }

  @Override
  public Map<ScopeLevel, EnumSet<ValidatorType>> getSelectorKind() {
    return ImmutableMap.of(ScopeLevel.ACCOUNT,
        EnumSet.of(BY_RESOURCE_IDENTIFIER, BY_RESOURCE_TYPE, BY_RESOURCE_TYPE_INCLUDING_CHILD_SCOPES));
  }

  @Override
  public String getType() {
    return type;
  }

  @Override
  public Set<ScopeLevel> getValidScopeLevels() {
    return EnumSet.of(ScopeLevel.ACCOUNT);
  }

  @Override
  public Optional<String> getEventFrameworkEntityType() {
    return Optional.of(EventsFrameworkMetadataConstants.STREAMING_DESTINATION);
  }

  @Override
  public ResourceInfo getResourceInfoFromEvent(Message message) {
    EntityChangeDTO entityChangeDTO;
    try {
      entityChangeDTO = EntityChangeDTO.parseFrom(message.getMessage().getData());
    } catch (InvalidProtocolBufferException e) {
      log.error("Exception in unpacking EntityChangeDTO for key {}", message.getId(), e);
      return null;
    }
    if (Objects.isNull(entityChangeDTO)) {
      return null;
    }
    return ResourceInfo.builder()
        .accountIdentifier(String.valueOf(entityChangeDTO.getAccountIdentifier()))
        .resourceType(getType())
        .resourceIdentifier(String.valueOf(entityChangeDTO.getIdentifier()))
        .build();
  }

  @Override
  public boolean isValidAttributeFilter(AttributeFilter attributeFilter) {
    return false;
  }
}
