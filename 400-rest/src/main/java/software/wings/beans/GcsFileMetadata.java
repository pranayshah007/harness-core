/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.beans;

import static io.harness.annotations.dev.HarnessTeam.PL;

import io.harness.annotation.HarnessEntity;
import io.harness.annotations.StoreIn;
import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.beans.ChecksumType;
import io.harness.delegate.beans.FileBucket;
import io.harness.file.GcsHarnessFileMetadata;
import io.harness.mongo.index.FdIndex;
import io.harness.ng.DbAliases;
import io.harness.persistence.AccountAccess;

import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldNameConstants;
import lombok.experimental.UtilityClass;
import org.hibernate.validator.constraints.NotEmpty;
import org.mongodb.morphia.annotations.Entity;

/**
 * Since artifacts etc. will be initially dual-written into Mongo GridFs and Google Cloud Storage, and each file id has
 * different format, we will need to use this mapping to store the file id mappings. So that we could use each format of
 * file id to look up stored files from either storage.
 *
 * Also GCS is not good at searching entries based on extra metadata such as 'entityId' and 'version', we have to save
 * this data in this Mongo collection in an indexed manner to respond to queries based on 'entityId' and 'version'
 * filter.
 *
 * @author marklu on 2018-12-04
 */
@OwnedBy(PL)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = false)
@StoreIn(DbAliases.HARNESS)
@Entity(value = "gcsFileMetadata", noClassnameStored = true)
@HarnessEntity(exportable = true)
@FieldNameConstants(innerTypeName = "GcsFileMetadataKeys")
public class GcsFileMetadata extends Base implements AccountAccess, GcsHarnessFileMetadata {
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

  @UtilityClass
  public static final class GcsFileMetadataKeys {
    public static final String createdAt = "createdAt";
  }
}
