/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.setupusage;

import io.harness.eventsframework.schemas.entity.EntityTypeProtoEnum;

import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Value;

/**
 *SetupUsageOwnerEntity represents the 'ReferredBy' entity in a setup usage relation
 */
@Value
@Builder
public class SetupUsageOwnerEntity {
  @NotEmpty String accountId;
  String orgIdentifier;
  String projectIdentifier;
  @NotEmpty String identifier;
  @NotEmpty String name;
  @NotNull EntityTypeProtoEnum type;
}
