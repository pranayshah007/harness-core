/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.accesscontrol.roles.persistence;

import io.harness.accesscontrol.common.filter.ManagedFilter;
import io.harness.accesscontrol.roles.Role;
import io.harness.accesscontrol.roles.filter.RoleFilter;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.ng.beans.PageRequest;
import io.harness.ng.beans.PageResponse;

import java.util.List;
import java.util.Optional;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import org.hibernate.validator.constraints.NotEmpty;

@OwnedBy(HarnessTeam.PL)
public interface RoleDao {
  Role create(@NotNull @Valid Role role);

  PageResponse<Role> list(@NotNull PageRequest pageRequest, @Valid @NotNull RoleFilter roleFilter);

  Optional<Role> get(@NotEmpty String identifier, String scopeIdentifier, @NotNull ManagedFilter managedFilter);

  Role update(@NotNull @Valid Role role);

  Optional<Role> delete(@NotEmpty String identifier, String scopeIdentifier, boolean managed);

  boolean removePermissionFromRoles(@NotEmpty String permissionIdentifier, @Valid @NotNull RoleFilter roleFilter);

  boolean addPermissionToRoles(@NotEmpty String permissionIdentifier, @Valid @NotNull RoleFilter roleFilter);

  long deleteMulti(@Valid @NotNull RoleFilter roleFilter);

  List<Role> getAll();
}
