/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.resourcegroup.resourceclient.gitops;

import static io.harness.resourcegroup.beans.ValidatorType.BY_RESOURCE_IDENTIFIER;
import static io.harness.resourcegroup.beans.ValidatorType.BY_RESOURCE_TYPE;
import static io.harness.resourcegroup.beans.ValidatorType.BY_RESOURCE_TYPE_INCLUDING_CHILD_SCOPES;

import static java.util.stream.Collectors.toList;
import static org.apache.commons.lang3.StringUtils.stripToNull;

import io.harness.beans.Scope;
import io.harness.beans.ScopeLevel;
import io.harness.data.structure.EmptyPredicate;
import io.harness.eventsframework.EventsFrameworkMetadataConstants;
import io.harness.eventsframework.consumer.Message;
import io.harness.eventsframework.entity_crud.EntityChangeDTO;
import io.harness.exception.InvalidRequestException;
import io.harness.gitops.models.Agent;
import io.harness.gitops.remote.GitopsResourceClient;
import io.harness.ng.beans.PageResponse;
import io.harness.resourcegroup.beans.ValidatorType;
import io.harness.resourcegroup.framework.v1.service.Resource;
import io.harness.resourcegroup.framework.v1.service.ResourceInfo;

import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.protobuf.InvalidProtocolBufferException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import io.harness.resourcegroup.v2.model.AttributeFilter;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import retrofit2.Response;

@Singleton
@AllArgsConstructor(access = AccessLevel.PUBLIC, onConstructor = @__({ @Inject }))
@Slf4j
public class AgentResourceImpl implements Resource {
  private final GitopsResourceClient gitopsResourceClient;
  private final int PAGE_SIZE = 100;

  @Override
  public String getType() {
    return "GITOPS_AGENT";
  }

  @Override
  public Set<ScopeLevel> getValidScopeLevels() {
    return EnumSet.of(ScopeLevel.PROJECT, ScopeLevel.ORGANIZATION, ScopeLevel.ACCOUNT);
  }

  @Override
  public Optional<String> getEventFrameworkEntityType() {
    return Optional.of(EventsFrameworkMetadataConstants.GITOPS_AGENT_ENTITY);
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
  public boolean isValidAttributeFilter(AttributeFilter attributeFilter) {
    return false;
  }

  @Override
  public List<Boolean> validate(List<String> resourceIds, Scope scope) {
    if (EmptyPredicate.isEmpty(resourceIds)) {
      return Collections.EMPTY_LIST;
    }
    int page_idx = 0;
    List<Agent> agents = getAgents(scope, page_idx++);
    List<Agent> allAgents = new ArrayList<>(agents);
    while (agents.size() == PAGE_SIZE) {
      agents = getAgents(scope, page_idx++);
      allAgents.addAll(agents);
    }
    Set<String> validResourceIds = allAgents.stream().map(Agent::getIdentifier).collect(Collectors.toSet());
    return resourceIds.stream().map(validResourceIds::contains).collect(toList());
  }

  private List<Agent> getAgents(Scope scope, int page_idx) {
    List<Agent> agents;
    try {
      Response<PageResponse<Agent>> response = gitopsResourceClient
                                                   .listAgents(scope.getAccountIdentifier(), scope.getOrgIdentifier(),
                                                       scope.getProjectIdentifier(), page_idx, PAGE_SIZE)
                                                   .execute();
      agents = response.body().getContent();
    } catch (Exception e) {
      throw new InvalidRequestException("failed to verify agent identifiers", e);
    }
    return agents;
  }

  @Override
  public Map<ScopeLevel, EnumSet<ValidatorType>> getSelectorKind() {
    return ImmutableMap.of(ScopeLevel.ACCOUNT,
        EnumSet.of(BY_RESOURCE_TYPE, BY_RESOURCE_IDENTIFIER, BY_RESOURCE_TYPE_INCLUDING_CHILD_SCOPES),
        ScopeLevel.ORGANIZATION,
        EnumSet.of(BY_RESOURCE_TYPE, BY_RESOURCE_IDENTIFIER, BY_RESOURCE_TYPE_INCLUDING_CHILD_SCOPES),
        ScopeLevel.PROJECT, EnumSet.of(BY_RESOURCE_TYPE, BY_RESOURCE_IDENTIFIER));
  }
}
