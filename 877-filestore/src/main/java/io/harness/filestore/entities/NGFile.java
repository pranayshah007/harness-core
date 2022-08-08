/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.filestore.entities;

import static io.harness.beans.EmbeddedUser.EmbeddedUserKeys;

import io.harness.annotation.HarnessEntity;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.EmbeddedUser;
import io.harness.data.validator.EntityIdentifier;
import io.harness.delegate.beans.ChecksumType;
import io.harness.mongo.CollationLocale;
import io.harness.mongo.CollationStrength;
import io.harness.mongo.index.Collation;
import io.harness.mongo.index.CompoundMongoIndex;
import io.harness.mongo.index.MongoIndex;
import io.harness.ng.core.NGAccountAccess;
import io.harness.ng.core.NGOrgAccess;
import io.harness.ng.core.NGProjectAccess;
import io.harness.ng.core.common.beans.NGTag;
import io.harness.ng.core.filestore.FileUsage;
import io.harness.ng.core.filestore.NGFileType;
import io.harness.persistence.CreatedByAware;
import io.harness.persistence.PersistentEntity;
import io.harness.persistence.UpdatedByAware;
import io.harness.persistence.UuidAware;

import com.google.common.collect.ImmutableList;
import java.util.List;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Data;
import lombok.ToString;
import lombok.experimental.FieldDefaults;
import lombok.experimental.FieldNameConstants;
import lombok.experimental.UtilityClass;
import org.codehaus.jackson.annotate.JsonIgnore;
import org.hibernate.validator.constraints.NotEmpty;
import org.mongodb.morphia.annotations.Entity;
import org.mongodb.morphia.annotations.Id;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.annotation.TypeAlias;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@Builder
@ToString
@FieldNameConstants(innerTypeName = "NGFiles")
@FieldDefaults(level = AccessLevel.PRIVATE)
@Entity(value = "ngFiles", noClassnameStored = true)
@Document("ngFiles")
@TypeAlias("ngFiles")
@HarnessEntity(exportable = true)
@OwnedBy(HarnessTeam.CDP)
public class NGFile implements PersistentEntity, UuidAware, NGAccountAccess, NGOrgAccess, NGProjectAccess,
                               CreatedByAware, UpdatedByAware {
  @org.springframework.data.annotation.Id @Id String uuid;
  @CreatedDate private Long createdAt;
  @LastModifiedDate private Long lastModifiedAt;
  @CreatedBy private EmbeddedUser createdBy;
  @LastModifiedBy private EmbeddedUser lastUpdatedBy;

  @NotEmpty String accountIdentifier;
  @EntityIdentifier(allowBlank = true) String orgIdentifier;
  @EntityIdentifier(allowBlank = true) String projectIdentifier;

  @EntityIdentifier String identifier;
  @Size(max = 1024) String description;
  @Size(max = 128) List<NGTag> tags;
  @NotNull FileUsage fileUsage;
  @NotNull NGFileType type;
  @NotEmpty String parentIdentifier;
  @NotEmpty String fileUuid;
  @NotEmpty String name;
  @NotEmpty String path;
  ChecksumType checksumType;
  String checksum;
  String mimeType;
  Long size;
  Boolean draft;

  public static List<MongoIndex> mongoIndexes() {
    return ImmutableList.<MongoIndex>builder()
        .add(CompoundMongoIndex.builder()
                 .name("unique_idx")
                 .field(NGFiles.accountIdentifier)
                 .field(NGFiles.orgIdentifier)
                 .field(NGFiles.projectIdentifier)
                 .field(NGFiles.parentIdentifier)
                 .field(NGFiles.name)
                 .unique(true)
                 .collation(
                     Collation.builder().locale(CollationLocale.ENGLISH).strength(CollationStrength.PRIMARY).build())
                 .build(),
            CompoundMongoIndex.builder()
                .name("list_files_idx")
                .field(NGFiles.accountIdentifier)
                .field(NGFiles.orgIdentifier)
                .field(NGFiles.projectIdentifier)
                .field(NGFiles.identifier)
                .unique(true)
                .build(),
            CompoundMongoIndex.builder().name("path_idx").field(NGFiles.path).unique(false).build())
        .build();
  }

  @JsonIgnore
  public boolean isFolder() {
    return type == NGFileType.FOLDER;
  }

  @JsonIgnore
  public boolean isFile() {
    return type == NGFileType.FILE;
  }

  @JsonIgnore
  public boolean isDraft() {
    return draft != null && draft;
  }

  @UtilityClass
  public static final class NGFiles {
    public static final String CREATED_BY_NAME = NGFiles.createdBy + "." + EmbeddedUserKeys.name;
    public static final String CREATED_BY_EMAIL = NGFiles.createdBy + "." + EmbeddedUserKeys.email;
  }
}
