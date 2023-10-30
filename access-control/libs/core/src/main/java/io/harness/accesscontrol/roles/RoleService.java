/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.accesscontrol.roles;

import static io.harness.annotations.dev.HarnessTeam.PL;

import io.harness.accesscontrol.common.filter.ManagedFilter;
import io.harness.accesscontrol.roles.filter.RoleFilter;
import io.harness.annotations.dev.OwnedBy;
import io.harness.ng.beans.PageRequest;
import io.harness.ng.beans.PageResponse;

import java.util.Optional;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import org.hibernate.validator.constraints.NotEmpty;

@OwnedBy(PL)
public interface RoleService {
  Role create(@NotNull @Valid Role role);

  PageResponse<Role> list(
      @NotNull PageRequest pageRequest, @Valid @NotNull RoleFilter roleFilter, boolean hideInternal);
  PageResponse<RoleWithPrincipalCount> listWithPrincipalCount(
      @NotNull PageRequest pageRequest, @Valid @NotNull RoleFilter roleFilter, boolean hideInternal);

  Optional<Role> get(@NotEmpty String identifier, String scopeIdentifier, @NotNull ManagedFilter managedFilter);

  Role update(@NotNull @Valid Role role);

  boolean removePermissionFromRoles(@NotEmpty String permissionIdentifier, @Valid @NotNull RoleFilter roleFilter);

  boolean addPermissionToRoles(@NotEmpty String permissionIdentifier, @Valid @NotNull RoleFilter roleFilter);

  Role delete(@NotEmpty String identifier, @NotEmpty String scopeIdentifier);

  Role deleteManaged(@NotEmpty String identifier);

  // NOTE: This method should be used only for deleting roles on scope deletion as here we are generating outbox event
  // for ACLs processing.
  long deleteMulti(@Valid @NotNull RoleFilter roleFilter);
}
