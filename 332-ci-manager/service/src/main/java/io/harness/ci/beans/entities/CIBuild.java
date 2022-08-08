/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.beans.entities;

import io.harness.annotation.HarnessEntity;
import io.harness.annotation.StoreIn;
import io.harness.beans.build.CIPipelineDetails;
import io.harness.beans.build.PublishedArtifact;
import io.harness.beans.execution.ExecutionSource;
import io.harness.beans.inputset.InputSet;
import io.harness.mongo.index.FdIndex;
import io.harness.persistence.CreatedAtAware;
import io.harness.persistence.PersistentEntity;
import io.harness.persistence.UuidAware;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.github.reinert.jjschema.SchemaIgnore;
import java.time.Instant;
import java.util.List;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.FieldNameConstants;
import org.hibernate.validator.constraints.NotEmpty;
import org.mongodb.morphia.annotations.Entity;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
@FieldNameConstants(innerTypeName = "Build")
@Entity(value = "cibuild", noClassnameStored = true)
@StoreIn("harnessci")
@Document("cibuild")
@HarnessEntity(exportable = true)
public class CIBuild implements PersistentEntity, UuidAware, CreatedAtAware {
  @Id @org.mongodb.morphia.annotations.Id String uuid;
  @NotEmpty Long buildNumber;
  @NotEmpty String pipelineIdentifier;
  @JsonIgnore private transient CIPipelineDetails pipelineDetails;
  @NotEmpty @Builder.Default private long triggerTime = Instant.now().toEpochMilli();

  @NotEmpty private ExecutionSource executionSource;
  private List<PublishedArtifact> publishedArtifacts;
  private InputSet inputSet;
  @NotEmpty String executionId;
  @NotEmpty String accountIdentifier;
  @NotEmpty String orgIdentifier;
  @NotEmpty String projectIdentifier;
  @SchemaIgnore @FdIndex @CreatedDate private long createdAt;
}
