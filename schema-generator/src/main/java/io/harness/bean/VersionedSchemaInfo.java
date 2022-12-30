/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.bean;

import io.harness.EntityType;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.stitcher.SchemaStitcher;

import java.util.List;
import java.util.Map;
import lombok.Builder;
import lombok.Value;

@OwnedBy(HarnessTeam.PIPELINE)
@Value
@Builder
public class VersionedSchemaInfo {
  SchemaVersion schemaVersion;

  // We will generate schema of this entire list at the start and store it with us for quick retrieval.
  List<EntitySchemaInfo> entitySchemaInfoList;

  // Only these entityTypes schemas would be published to remote repository.
  Map<EntityType, SchemaStitcher> rootEntityTypes;
}
