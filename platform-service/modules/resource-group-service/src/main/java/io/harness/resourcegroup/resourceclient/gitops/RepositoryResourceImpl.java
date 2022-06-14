/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.resourcegroup.resourceclient.gitops;

import static io.harness.resourcegroup.beans.ValidatorType.BY_RESOURCE_TYPE;
import static io.harness.resourcegroup.beans.ValidatorType.BY_RESOURCE_TYPE_INCLUDING_CHILD_SCOPES;

import static java.util.stream.Collectors.toList;
import static org.apache.commons.lang3.StringUtils.stripToNull;

import io.harness.beans.Scope;
import io.harness.beans.ScopeLevel;
import io.harness.eventsframework.EventsFrameworkMetadataConstants;
import io.harness.eventsframework.consumer.Message;
import io.harness.eventsframework.entity_crud.EntityChangeDTO;
import io.harness.exception.InvalidRequestException;
import io.harness.gitops.models.Repository;
import io.harness.gitops.models.RepositoryQuery;
import io.harness.gitops.remote.GitopsResourceClient;
import io.harness.ng.beans.PageResponse;
import io.harness.resourcegroup.beans.ValidatorType;
import io.harness.resourcegroup.framework.v1.service.Resource;
import io.harness.resourcegroup.framework.v1.service.ResourceInfo;

import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.protobuf.InvalidProtocolBufferException;
import java.io.IOException;
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
public class RepositoryResourceImpl implements Resource {
  private GitopsResourceClient gitopsResourceClient;
  @Override
  public String getType() {
    return "GITOPS_REPOSITORY";
  }

  @Override
  public Set<ScopeLevel> getValidScopeLevels() {
    return EnumSet.of(ScopeLevel.ACCOUNT, ScopeLevel.ORGANIZATION, ScopeLevel.PROJECT);
  }

  @Override
  public Optional<String> getEventFrameworkEntityType() {
    return Optional.of(EventsFrameworkMetadataConstants.GITOPS_REPOSITORY_ENTITY);
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
    if (resourceIds.isEmpty()) {
      return Collections.EMPTY_LIST;
    }
    Map<String, Object> filter = ImmutableMap.of("identifier", ImmutableMap.of("$in", resourceIds));
    Response<PageResponse<Repository>> response = null;
    try {
      final RepositoryQuery query = RepositoryQuery.builder()
                                        .accountId(scope.getAccountIdentifier())
                                        .orgIdentifier(scope.getOrgIdentifier())
                                        .projectIdentifier(scope.getProjectIdentifier())
                                        .pageIndex(0)
                                        .pageSize(resourceIds.size())
                                        .filter(filter)
                                        .build();
      response = gitopsResourceClient.listRepositories(query).execute();
    } catch (IOException e) {
      throw new InvalidRequestException("failed to verify repository identifiers");
    }
    final List<Repository> repositories = response.body().getContent();
    final Set<String> repositoriesSet =
        repositories.stream().map(Repository::getIdentifier).collect(Collectors.toSet());
    return resourceIds.stream().map(repositoriesSet::contains).collect(toList());
  }

  @Override
  public Map<ScopeLevel, EnumSet<ValidatorType>> getSelectorKind() {
    return ImmutableMap.of(ScopeLevel.ACCOUNT, EnumSet.of(BY_RESOURCE_TYPE_INCLUDING_CHILD_SCOPES),
        ScopeLevel.ORGANIZATION, EnumSet.of(BY_RESOURCE_TYPE_INCLUDING_CHILD_SCOPES), ScopeLevel.PROJECT,
        EnumSet.of(BY_RESOURCE_TYPE));
  }
}
