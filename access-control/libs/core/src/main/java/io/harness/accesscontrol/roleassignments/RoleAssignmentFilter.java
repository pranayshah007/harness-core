/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.accesscontrol.roleassignments;

import static io.harness.accesscontrol.common.filter.ManagedFilter.NO_FILTER;
import static io.harness.annotations.dev.HarnessTeam.PL;

import io.harness.accesscontrol.common.filter.ManagedFilter;
import io.harness.accesscontrol.principals.Principal;
import io.harness.accesscontrol.principals.PrincipalType;
import io.harness.accesscontrol.roleassignments.validator.ValidRoleAssignmentFilter;
import io.harness.annotations.dev.OwnedBy;

import java.util.HashSet;
import java.util.Set;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import lombok.experimental.FieldDefaults;
import org.hibernate.validator.constraints.NotEmpty;

@OwnedBy(PL)
@Getter
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
@AllArgsConstructor
@ToString
@EqualsAndHashCode
@ValidRoleAssignmentFilter
public class RoleAssignmentFilter {
  final String scopeFilter;
  final boolean includeChildScopes;
  @Setter @Builder.Default @NotNull @Size(max = 100) Set<ScopeFilter> scopeFilters = new HashSet<>();
  @Builder.Default @NotNull @Size(max = 100) final Set<String> scopeLevelFilter = new HashSet<>();
  @Builder.Default @NotNull @Size(max = 100) final Set<String> resourceGroupFilter = new HashSet<>();
  @Builder.Default @NotNull @Size(max = 100) final Set<String> roleFilter = new HashSet<>();
  @Setter @Builder.Default @NotNull @Size(max = 100) Set<PrincipalType> principalTypeFilter = new HashSet<>();
  @Setter @Builder.Default @NotNull @Size(max = 100) Set<String> principalScopeLevelFilter = new HashSet<>();
  @Setter @Builder.Default @NotNull @Size(max = 100) Set<Principal> principalFilter = new HashSet<>();
  @Builder.Default @NotNull @Valid final ManagedFilter managedFilter = NO_FILTER;
  @Builder.Default @NotNull @Size(max = 100) final Set<Boolean> disabledFilter = new HashSet<>();
}
