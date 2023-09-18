/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.resourcegroup.resourceclient.opa;

import static io.harness.annotations.dev.HarnessTeam.OPA;
import static io.harness.resourcegroup.beans.ValidatorType.BY_RESOURCE_TYPE;
import static io.harness.resourcegroup.beans.ValidatorType.BY_RESOURCE_TYPE_INCLUDING_CHILD_SCOPES;

import static org.apache.commons.lang3.StringUtils.stripToNull;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.Scope;
import io.harness.beans.ScopeLevel;
import io.harness.eventsframework.EventsFrameworkMetadataConstants;
import io.harness.eventsframework.consumer.Message;
import io.harness.eventsframework.entity_crud.EntityChangeDTO;
import io.harness.exception.InvalidRequestException;
import io.harness.network.SafeHttpCall;
import io.harness.opaclient.OpaServiceClient;
import io.harness.opaclient.model.PolicySetData;
import io.harness.resourcegroup.beans.ValidatorType;
import io.harness.resourcegroup.framework.v1.service.Resource;
import io.harness.resourcegroup.framework.v1.service.ResourceInfo;
import io.harness.resourcegroup.v2.model.AttributeFilter;

import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;
import com.google.protobuf.InvalidProtocolBufferException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(OPA)
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@AllArgsConstructor(access = AccessLevel.PUBLIC, onConstructor = @__({ @Inject }))
@Slf4j
public class PolicySetResourceImpl implements Resource {
  private final OpaServiceClient opaServiceClient;
  private final Integer pageSize = 100;
  @Override
  public String getType() {
    return "GOVERNANCEPOLICYSETS";
  }

  @Override
  public Set<ScopeLevel> getValidScopeLevels() {
    return EnumSet.of(ScopeLevel.ACCOUNT, ScopeLevel.PROJECT, ScopeLevel.ORGANIZATION);
  }

  @Override
  public Optional<String> getEventFrameworkEntityType() {
    return Optional.of(EventsFrameworkMetadataConstants.OPA_GOVERNANCE_POLICYSET);
  }

  @Override
  public ResourceInfo getResourceInfoFromEvent(Message message) {
    EntityChangeDTO entityChangeDTO = null;
    try {
      entityChangeDTO = EntityChangeDTO.parseFrom(message.getMessage().getData());
    } catch (InvalidProtocolBufferException e) {
      log.error("Exception in unpacking EntityChangeDTO for policyset with message key {}", message.getId(), e);
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
  public List<Boolean> validate(List<String> resourceIds, Scope scope) {
    if (resourceIds.isEmpty()) {
      return Collections.emptyList();
    }
    try {
      List<PolicySetData> opaPolicySetListResponseResponse =
          SafeHttpCall.executeWithExceptions(opaServiceClient.listOpaPolicySets(
              scope.getAccountIdentifier(), scope.getOrgIdentifier(), scope.getProjectIdentifier(), pageSize));
      final Set<String> policyIdentifiers =
          opaPolicySetListResponseResponse.stream().map(PolicySetData::getIdentifier).collect(Collectors.toSet());
      return resourceIds.stream().map(policyIdentifiers::contains).collect(Collectors.toList());
    } catch (IOException ex) {
      log.error("Exception while listing OPA policies", ex);
      throw new InvalidRequestException("failed to verify policyset identifiers");
    }
  }

  @Override
  public ImmutableMap<ScopeLevel, EnumSet<ValidatorType>> getSelectorKind() {
    return ImmutableMap.of(ScopeLevel.ACCOUNT, EnumSet.of(BY_RESOURCE_TYPE, BY_RESOURCE_TYPE_INCLUDING_CHILD_SCOPES),
        ScopeLevel.ORGANIZATION, EnumSet.of(BY_RESOURCE_TYPE, BY_RESOURCE_TYPE_INCLUDING_CHILD_SCOPES),
        ScopeLevel.PROJECT, EnumSet.of(BY_RESOURCE_TYPE));
  }

  @Override
  public boolean isValidAttributeFilter(AttributeFilter attributeFilter) {
    return false;
  }
}