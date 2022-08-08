/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.resourcegroup.framework.v1.service.impl;

import static io.harness.annotations.dev.HarnessTeam.PL;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.ScopeLevel;
import io.harness.resourcegroup.framework.v1.remote.mapper.ResourceTypeMapper;
import io.harness.resourcegroup.framework.v1.service.Resource;
import io.harness.resourcegroup.framework.v1.service.ResourceTypeService;
import io.harness.resourcegroup.v1.remote.dto.ResourceTypeDTO;
import io.harness.resourcegroup.v1.remote.dto.ResourceTypeDTO.ResourceType;

import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@OwnedBy(PL)
public class ResourceTypeServiceImpl implements ResourceTypeService {
  Map<String, Resource> resources;

  @Inject
  public ResourceTypeServiceImpl(Map<String, Resource> resources) {
    this.resources = resources;
  }

  private static ResourceType toResourceType(Resource resource, ScopeLevel scopeLevel) {
    return ResourceType.builder()
        .name(resource.getType())
        .validatorTypes(resource.getSelectorKind().get(scopeLevel) == null
                ? new ArrayList<>()
                : new ArrayList<>(resource.getSelectorKind().get(scopeLevel)))
        .build();
  }

  @Override
  public ResourceTypeDTO getResourceTypes(ScopeLevel scopeLevel) {
    if (Objects.isNull(scopeLevel)) {
      return null;
    }

    return ResourceTypeMapper.toDTO(resources.values()
                                        .stream()
                                        .filter(resource -> resource.getValidScopeLevels().contains(scopeLevel))
                                        .map(resource -> toResourceType(resource, scopeLevel))
                                        .collect(Collectors.toList()));
  }
}
