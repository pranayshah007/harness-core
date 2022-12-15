/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.accesscontrol.roleassignments;

import io.harness.accesscontrol.principals.PrincipalType;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.data.validator.EntityIdentifier;

import javax.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import lombok.experimental.FieldDefaults;
import org.hibernate.validator.constraints.NotEmpty;

@OwnedBy(HarnessTeam.PL)
@Getter
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
@AllArgsConstructor
@ToString
@EqualsAndHashCode
public class RoleAssignment {
  @EntityIdentifier final String identifier;
  @NotEmpty final String scopeIdentifier;
  final String scopeLevel;
  @NotEmpty final String resourceGroupIdentifier;
  @NotEmpty final String roleIdentifier;
  final String principalScopeLevel;
  @NotEmpty final String principalIdentifier;
  @NotNull final PrincipalType principalType;
  @Setter boolean managed;
  @Setter boolean internal;
  final boolean disabled;
  final Long createdAt;
  final Long lastModifiedAt;
  @Setter Long version;
}
