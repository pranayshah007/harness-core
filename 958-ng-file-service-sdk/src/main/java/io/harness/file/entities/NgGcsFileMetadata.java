/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.file.entities;

import static io.harness.annotations.dev.HarnessTeam.PL;

import io.harness.annotations.StoreIn;
import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.beans.ChecksumType;
import io.harness.delegate.beans.FileBucket;
import io.harness.file.GcsHarnessFileMetadata;
import io.harness.mongo.index.FdIndex;
import io.harness.ng.DbAliases;
import io.harness.persistence.PersistentEntity;

import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldNameConstants;
import org.hibernate.validator.constraints.NotEmpty;
import org.mongodb.morphia.annotations.Entity;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.annotation.TypeAlias;
import org.springframework.data.mongodb.core.mapping.Document;

@OwnedBy(PL)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = false)
@StoreIn(DbAliases.NG_MANAGER)
@Entity(value = "ngGcsFileMetadata", noClassnameStored = true)
@FieldNameConstants(innerTypeName = "NgGcsFileMetadataKeys")
@Document("ngGcsFileMetadata")
@TypeAlias("ngGcsFileMetadata")
public class NgGcsFileMetadata implements PersistentEntity, GcsHarnessFileMetadata {
  @Id @org.mongodb.morphia.annotations.Id String id;
  @NotEmpty @FdIndex private String accountId;
  @NotEmpty @FdIndex private String fileId; // Mongo GridFs fileId.
  @NotEmpty @FdIndex private String gcsFileId;
  @NotEmpty private String fileName;
  @NotEmpty private FileBucket fileBucket;
  @FdIndex private String entityId;
  private int version;
  private long fileLength;
  private String mimeType;
  private ChecksumType checksumType;
  private String checksum;
  private Map<String, Object> others; // Additional metadata, typically used by TerraformState.

  @CreatedDate Long createdAt;
  @LastModifiedDate Long lastModifiedAt;
}
