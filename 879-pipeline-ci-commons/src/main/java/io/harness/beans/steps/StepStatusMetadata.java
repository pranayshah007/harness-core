/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.beans.steps;

import static io.harness.annotations.dev.HarnessTeam.CI;

import io.harness.annotation.HarnessEntity;
import io.harness.annotations.StoreIn;
import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.task.stepstatus.StepExecutionStatus;
import io.harness.mongo.index.FdTtlIndex;
import io.harness.persistence.PersistentEntity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import dev.morphia.annotations.Entity;
import dev.morphia.annotations.Id;
import java.time.OffsetDateTime;
import java.util.Date;
import java.util.List;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.FieldNameConstants;
import org.springframework.data.annotation.TypeAlias;
import org.springframework.data.mongodb.core.mapping.Document;
@FieldNameConstants(innerTypeName = "StepStatusMetadataKeys")

@OwnedBy(CI)
@Data
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
@StoreIn("harnessci")
@Entity(value = "stepStatusMetadata", noClassnameStored = true)
@Document("stepStatusMetadata")
@TypeAlias("stepStatusMetadata")
@HarnessEntity(exportable = true)
public class StepStatusMetadata implements PersistentEntity {
  @Id @org.springframework.data.annotation.Id String uuid;

  String stageExecutionId;

  StepExecutionStatus status;

  List<String> failedSteps;

  @Builder.Default
  @FdTtlIndex
  private Date expireAfter = Date.from(OffsetDateTime.now().plusSeconds(36000).toInstant());
}