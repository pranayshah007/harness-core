/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.accesscontrol.roleassignments;

import static io.harness.annotations.dev.HarnessTeam.PL;

import io.harness.accesscontrol.roleassignments.validator.RoleAssignmentValidationRequest;
import io.harness.accesscontrol.roleassignments.validator.RoleAssignmentValidationResult;
import io.harness.annotations.dev.OwnedBy;
import io.harness.ng.beans.PageRequest;
import io.harness.ng.beans.PageResponse;

import java.util.Optional;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import org.hibernate.validator.constraints.NotEmpty;

@OwnedBy(PL)
public interface RoleAssignmentService {
  RoleAssignment create(@Valid @NotNull RoleAssignment roleAssignment);

  PageResponse<RoleAssignment> list(
      @NotNull PageRequest pageRequest, @Valid @NotNull RoleAssignmentFilter roleAssignmentFilter);

  PageResponse<RoleAssignment> list(@NotNull PageRequest pageRequest,
      @Valid @NotNull RoleAssignmentFilter roleAssignmentFilter, boolean hideInternal);

  Optional<RoleAssignment> get(@NotEmpty String identifier, @NotEmpty String scopeIdentifier);

  RoleAssignmentUpdateResult update(@NotNull @Valid RoleAssignment roleAssignment);

  Optional<RoleAssignment> delete(@NotEmpty String identifier, @NotEmpty String scopeIdentifier);

  long deleteMulti(@Valid @NotNull RoleAssignmentFilter roleAssignmentFilter);

  RoleAssignmentValidationResult validate(@Valid @NotNull RoleAssignmentValidationRequest validationRequest);
}
