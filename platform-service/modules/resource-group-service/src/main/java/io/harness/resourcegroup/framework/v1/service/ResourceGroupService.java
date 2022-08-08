/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.resourcegroup.framework.v1.service;

import static io.harness.annotations.dev.HarnessTeam.PL;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.Scope;
import io.harness.ng.beans.PageRequest;
import io.harness.resourcegroup.v1.remote.dto.ManagedFilter;
import io.harness.resourcegroup.v1.remote.dto.ResourceGroupDTO;
import io.harness.resourcegroup.v1.remote.dto.ResourceGroupFilterDTO;
import io.harness.resourcegroup.v1.remote.dto.ResourceGroupResponse;

import java.util.Optional;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import org.hibernate.validator.constraints.NotEmpty;
import org.springframework.data.domain.Page;

@OwnedBy(PL)
public interface ResourceGroupService {
  ResourceGroupResponse create(ResourceGroupDTO resourceGroupDTO, boolean harnessManaged);

  Optional<ResourceGroupResponse> get(
      @NotNull Scope scope, @NotEmpty String identifier, @NotNull ManagedFilter managedFilter);

  Page<ResourceGroupResponse> list(Scope scope, PageRequest pageRequest, String searchTerm);

  Page<ResourceGroupResponse> list(
      @NotNull @Valid ResourceGroupFilterDTO resourceGroupFilterDTO, @NotNull PageRequest pageRequest);

  Optional<ResourceGroupResponse> update(
      ResourceGroupDTO resourceGroupDTO, boolean sanitizeResourceSelectors, boolean harnessManaged);

  Optional<ResourceGroupResponse> upsert(ResourceGroupDTO resourceGroupDTO, boolean harnessManaged);

  boolean delete(Scope scope, String identifier);

  void deleteManaged(@NotEmpty String identifier);

  void deleteByScope(Scope scope);
}
