/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ng.core;

import io.harness.EntityType;
import io.harness.beans.EntityReference;
import io.harness.ng.core.entitydetail.EntityDetailDeserializer;
import io.harness.ng.core.entitydetail.EntityGitMetadata;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldNameConstants;

@Data
@NoArgsConstructor
@AllArgsConstructor
@FieldNameConstants(innerTypeName = "EntityDetailKeys")
@JsonDeserialize(using = EntityDetailDeserializer.class)
public class EntityDetail {
  EntityType type;
  EntityReference entityRef;
  String name;
  EntityGitMetadata entityGitMetadata;
  @Builder
  public EntityDetail(EntityType type, EntityReference entityRef, String name) {
    this.type = type;
    this.entityRef = entityRef;
    this.name = name;
  }
}
