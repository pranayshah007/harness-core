/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.resourcegroup.framework.v2.service;

import static io.harness.annotations.dev.HarnessTeam.PL;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.Scope;
import io.harness.ng.beans.PageRequest;
import io.harness.resourcegroup.v1.remote.dto.ManagedFilter;
import io.harness.resourcegroup.v1.remote.dto.ResourceGroupFilterDTO;
import io.harness.resourcegroup.v2.model.ResourceGroup;
import io.harness.resourcegroup.v2.remote.dto.ResourceGroupDTO;
import io.harness.resourcegroup.v2.remote.dto.ResourceGroupResponse;

import java.util.List;
import java.util.Optional;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import org.springframework.data.domain.Page;
import org.springframework.data.mongodb.core.query.Criteria;

@OwnedBy(PL)
public interface ResourceGroupService {
  ResourceGroupResponse create(ResourceGroupDTO resourceGroupDTO, boolean harnessManaged);

  Optional<ResourceGroupResponse> get(
      @NotNull Scope scope, @NotEmpty String identifier, @NotNull ManagedFilter managedFilter);

  Optional<ResourceGroupResponse> upsert(ResourceGroup resourceGroup, boolean isInternal);

  Page<ResourceGroupResponse> list(ResourceGroupFilterDTO resourceGroupFilterDTO, PageRequest pageRequest);

  Page<ResourceGroupResponse> list(Scope scope, PageRequest pageRequest, String searchTerm);

  Optional<ResourceGroupResponse> update(ResourceGroupDTO resourceGroupDTO, boolean harnessManaged);

  boolean delete(Scope scope, String identifier);

  void deleteManaged(@NotEmpty String identifier);

  void deleteByScope(Scope scope);

  boolean deleteMulti(Criteria criteria);

  List<ResourceGroup> getPermittedResourceGroups(List<ResourceGroup> resourceGroups);

  Page<ResourceGroup> listAll(ResourceGroupFilterDTO resourceGroupFilterDTO, PageRequest pageRequest);
}
