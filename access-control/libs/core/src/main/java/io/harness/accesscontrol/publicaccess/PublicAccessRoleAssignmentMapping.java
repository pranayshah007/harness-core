/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.accesscontrol.publicaccess;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;

import javax.validation.constraints.NotEmpty;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Value;

@OwnedBy(HarnessTeam.PL)
@Value
@Builder
@EqualsAndHashCode
public class PublicAccessRoleAssignmentMapping {
  @NotEmpty String resourceType;
  @NotEmpty String roleIdentifier;
  @NotEmpty String principalIdentifier;
}
