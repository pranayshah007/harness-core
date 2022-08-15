/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.accesscontrol.acl.persistence;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;

import lombok.AccessLevel;
import lombok.Builder;
import lombok.Data;
import lombok.Getter;
import lombok.experimental.FieldDefaults;
import lombok.experimental.FieldNameConstants;

@Data
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
@FieldNameConstants(innerTypeName = "SourceMetadataKeys")
@OwnedBy(HarnessTeam.PL)
public class SourceMetadata {
  String userGroupIdentifier;
  String userGroupScopeLevel;
  String roleIdentifier;
  String roleAssignmentIdentifier;
  String resourceGroupIdentifier;
  @Getter(value = AccessLevel.NONE) Boolean implicitlyCreatedForScopeAccess;

  public boolean getImplicitlyCreatedForScopeAccess() {
    return Boolean.TRUE.equals(implicitlyCreatedForScopeAccess);
  }
}
