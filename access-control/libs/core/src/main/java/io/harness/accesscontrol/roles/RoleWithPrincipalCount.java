/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.accesscontrol.roles;

import io.harness.accesscontrol.roles.validator.ValidRole;
import io.harness.accesscontrol.scopes.ScopeDTO;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import lombok.experimental.FieldDefaults;

@OwnedBy(HarnessTeam.PL)
@Getter
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
@AllArgsConstructor
@ToString
@EqualsAndHashCode
@ValidRole
public class RoleWithPrincipalCount {
  Role role;
  ScopeDTO scope;
  @Setter Integer roleAssignedToUserCount;
  @Setter Integer roleAssignedToUserGroupCount;
  @Setter Integer roleAssignedToServiceAccountCount;
  boolean harnessManaged;
  Long createdAt;
  Long lastModifiedAt;
}
