/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.gitsync.common.helper;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.gitsync.EntityType;

import lombok.experimental.UtilityClass;

@OwnedBy(HarnessTeam.PL)
@UtilityClass
public class EntityTypeMapper {
  public EntityType getEntityType(io.harness.EntityType entityType) {
    if (io.harness.EntityType.PIPELINES.equals(entityType)) {
      return EntityType.PIPELINE;
    } else if (io.harness.EntityType.INPUT_SETS.equals(entityType)) {
      return EntityType.PIPELINE_INPUT_SETS;
    } else if (io.harness.EntityType.TEMPLATE.equals(entityType)) {
      return EntityType.TEMPLATE;
    }
    return EntityType.UNKNOWN_ENTITY;
  }
}
