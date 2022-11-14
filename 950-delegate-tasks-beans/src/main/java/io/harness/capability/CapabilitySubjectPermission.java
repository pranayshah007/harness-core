/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.capability;

import java.util.Date;
import lombok.Builder;
import lombok.Data;

// The set of capability that is being used
@Data
@Builder
public final class CapabilitySubjectPermission {
  // ID for individual entry
  private String uuid;
  private String accountId;

  // The only valid entity type is delegate right now
  private String delegateId;
  private String capabilityId;

  // Moment in time until the existing check of the capability can be considered as valid
  private long maxValidUntil;

  // Moment in time after which the capability should be re-validated again
  private long revalidateAfter;

  // This is when mongo will delete the record
  private Date validUntil;

  // Capability result: whether it is valid or not
  public enum PermissionResult { ALLOWED, DENIED, UNCHECKED }
  private PermissionResult permissionResult;
}
