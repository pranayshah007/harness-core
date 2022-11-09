/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.wait;

import io.harness.annotations.StoreIn;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.mongo.index.FdUniqueIndex;
import io.harness.ng.DbAliases;

import lombok.Builder;
import lombok.Data;
import lombok.experimental.FieldNameConstants;
import org.mongodb.morphia.annotations.Entity;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.TypeAlias;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@Builder
@StoreIn(DbAliases.PMS)
@Entity(value = "waitStepInstance", noClassnameStored = true)
@Document("waitStepInstance")
@FieldNameConstants(innerTypeName = "WaitStepInstanceKeys")
@TypeAlias("waitStepInstance")
@OwnedBy(HarnessTeam.PIPELINE)
public class WaitStepInstance {
  @Id @org.mongodb.morphia.annotations.Id String waitStepInstanceId;
  @FdUniqueIndex String nodeExecutionId;
  Long createdAt;
  int duration;
}