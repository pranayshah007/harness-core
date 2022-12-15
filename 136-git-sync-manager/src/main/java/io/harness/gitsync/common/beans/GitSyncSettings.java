/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.gitsync.common.beans;

import static io.harness.annotations.dev.HarnessTeam.DX;

import io.harness.annotations.StoreIn;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.EmbeddedUser;
import io.harness.mongo.index.CompoundMongoIndex;
import io.harness.mongo.index.MongoIndex;
import io.harness.ng.DbAliases;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.google.common.collect.ImmutableList;
import java.util.List;
import java.util.Map;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.FieldNameConstants;
import org.mongodb.morphia.annotations.Entity;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.annotation.TypeAlias;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@Builder
@TypeAlias("io.harness.gitsync.common.beans.gitSyncSettings")
@EqualsAndHashCode(callSuper = false)
@JsonIgnoreProperties(ignoreUnknown = true)
@StoreIn(DbAliases.NG_MANAGER)
@Document("gitSyncSettings")
@Entity(value = "gitSyncSettings", noClassnameStored = true)
@FieldNameConstants(innerTypeName = "GitSyncSettingsKeys")
@OwnedBy(DX)
public class GitSyncSettings {
  public static List<MongoIndex> mongoIndexes() {
    return ImmutableList.<MongoIndex>builder()
        .add(CompoundMongoIndex.builder()
                 .name("account_org_project_index")
                 .field(GitSyncSettingsKeys.accountIdentifier)
                 .field(GitSyncSettingsKeys.orgIdentifier)
                 .field(GitSyncSettingsKeys.projectIdentifier)
                 .unique(true)
                 .build())
        .build();
  }

  @org.springframework.data.annotation.Id @org.mongodb.morphia.annotations.Id private String uuid;
  private String accountIdentifier;
  private String projectIdentifier;
  private String orgIdentifier;
  private Map<String, String> settings;
  @CreatedBy private EmbeddedUser createdBy;
  @CreatedDate private long createdAt;
  @LastModifiedBy private EmbeddedUser lastUpdatedBy;
  @LastModifiedDate private long lastUpdatedAt;
  @Version Long version;

  public static final String IS_EXECUTE_ON_DELEGATE = "isExecuteOnDelegate";
  public static final String IS_GIT_SIMPLIFICATION_ENABLED = "isGitSimplificationEnabled";
  public static final String IS_ENABLED_ONLY_FOR_FF = "isEnabledOnlyForFF";
}
