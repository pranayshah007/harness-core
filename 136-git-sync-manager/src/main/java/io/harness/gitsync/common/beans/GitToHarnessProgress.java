/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.gitsync.common.beans;

import static io.harness.annotations.dev.HarnessTeam.DX;

import io.harness.annotation.StoreIn;
import io.harness.annotations.dev.OwnedBy;
import io.harness.mongo.index.FdIndex;
import io.harness.ng.DbAliases;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;
import javax.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.FieldNameConstants;
import org.mongodb.morphia.annotations.Entity;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.annotation.TypeAlias;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@Builder
@EqualsAndHashCode(callSuper = false)
@StoreIn(DbAliases.NG_MANAGER)
@Document("gitToHarnessProgress")
@TypeAlias("io.harness.gitsync.common.beans.GitToHarnessProcessingProgress")
@JsonIgnoreProperties(ignoreUnknown = true)
@Entity(value = "gitToHarnessProgress", noClassnameStored = true)
@FieldNameConstants(innerTypeName = "GitToHarnessProgressKeys")
@OwnedBy(DX)
public class GitToHarnessProgress {
  @org.springframework.data.annotation.Id @org.mongodb.morphia.annotations.Id private String uuid;
  @NotNull private String accountIdentifier;
  @FdIndex @NotNull private String yamlChangeSetId;
  @NotNull private String repoUrl;
  @NotNull private String branch;
  @NotNull private String commitId;
  @NotNull private YamlChangeSetEventType eventType;
  @NotNull private GitToHarnessProcessingStepType stepType;
  @NotNull private GitToHarnessProcessingStepStatus stepStatus;
  @NotNull private Long stepStartingTime;
  @NotNull private GitToHarnessProgressStatus gitToHarnessProgressStatus;
  private String processingCommitId;
  List<GitToHarnessFileProcessingRequest> gitFileChanges;
  List<GitToHarnessProcessingResponseDTO> processingResponse;
  @Version Long version;
  @EqualsAndHashCode.Exclude @CreatedDate private long createdAt;
  @EqualsAndHashCode.Exclude @LastModifiedDate private long lastUpdatedAt;
}
