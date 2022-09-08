/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.gitsync.branching;

import static io.harness.annotations.dev.HarnessTeam.DX;

import io.harness.annotations.StoreIn;
import io.harness.annotations.dev.OwnedBy;
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
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.annotation.TypeAlias;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@Builder
@EqualsAndHashCode(callSuper = false)
@JsonIgnoreProperties(ignoreUnknown = true)
@StoreIn(DbAliases.ALL)
@Document("entityGitBranchMetadata")
@TypeAlias("io.harness.gitsync.beans.entityGitBranchMetadata")
@Entity(value = "entityGitBranchMetadata", noClassnameStored = true)
@FieldNameConstants(innerTypeName = "EntityGitBranchMetadataKeys")
@OwnedBy(DX)
// todo(abhinav): add indexes
public class EntityGitBranchMetadata {
  @Id @org.mongodb.morphia.annotations.Id String uuid;
  @NotNull String uuidOfEntity;
  String entityType;
  List<String> branch;
  String yamlGitConfigId;
  String objectId;
  String orgIdentifier;
  String projectIdentifier;
  String entityFqn;
  String accountId;
  Boolean isDefault;
  @Version Long version;
  @CreatedDate Long createdAt;
  @LastModifiedDate Long lastModifiedAt;
}
