/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.gitsync.core.beans;

import static io.harness.annotations.dev.HarnessTeam.DX;

import io.harness.annotations.dev.OwnedBy;
import io.harness.mongo.CollationLocale;
import io.harness.mongo.CollationStrength;
import io.harness.mongo.index.Collation;
import io.harness.mongo.index.CompoundMongoIndex;
import io.harness.mongo.index.MongoIndex;

import com.google.common.collect.ImmutableList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldNameConstants;
import org.mongodb.morphia.annotations.Entity;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.annotation.TypeAlias;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = false)
@Entity(value = "gitFullSyncConfig", noClassnameStored = true)
@Document("gitFullSyncConfig")
@TypeAlias("io.harness.gitsync.core.beans.gitFullSyncConfig")
@FieldNameConstants(innerTypeName = "GitFullSyncConfigKeys")
@OwnedBy(DX)
public class GitFullSyncConfig {
  @Id @org.mongodb.morphia.annotations.Id private String id;
  @CreatedDate private long createdAt;
  @LastModifiedDate private long lastModifiedAt;
  private String branch;
  private String baseBranch;
  private String prTitle;
  private String accountIdentifier;
  private String orgIdentifier;
  private String projectIdentifier;
  private boolean createPullRequest;
  private String yamlGitConfigIdentifier;
  private String targetBranch;
  private boolean isNewBranch;
  private String rootFolder;

  public static List<MongoIndex> mongoIndexes() {
    return ImmutableList.<MongoIndex>builder()
        .add(CompoundMongoIndex.builder()
                 .name("unique_accountIdentifier_organizationIdentifier_projectIdentifier")
                 .field(GitFullSyncConfigKeys.accountIdentifier)
                 .field(GitFullSyncConfigKeys.orgIdentifier)
                 .field(GitFullSyncConfigKeys.projectIdentifier)
                 .unique(true)
                 .collation(
                     Collation.builder().locale(CollationLocale.ENGLISH).strength(CollationStrength.PRIMARY).build())
                 .build())
        .build();
  }
}
