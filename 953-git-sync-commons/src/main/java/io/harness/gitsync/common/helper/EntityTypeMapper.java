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
