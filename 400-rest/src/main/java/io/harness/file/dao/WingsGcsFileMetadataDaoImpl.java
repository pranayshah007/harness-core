/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.file.dao;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.persistence.HQuery.excludeAuthority;

import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.beans.FileBucket;
import io.harness.file.CommonGcsHarnessFileMetadata;
import io.harness.file.GcsHarnessFileMetadata;

import software.wings.beans.GcsFileMetadata;
import software.wings.beans.GcsFileMetadata.GcsFileMetadataKeys;
import software.wings.dl.WingsPersistence;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import dev.morphia.query.Sort;
import dev.morphia.query.UpdateOperations;
import dev.morphia.query.UpdateResults;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;

@Singleton
@Slf4j
@OwnedBy(PL)
public class WingsGcsFileMetadataDaoImpl implements GcsHarnessFileMetadataDao {
  private final WingsPersistence wingsPersistence;

  @Inject
  public WingsGcsFileMetadataDaoImpl(WingsPersistence wingsPersistence) {
    this.wingsPersistence = wingsPersistence;
  }

  @Override
  public String getGcsFileIdByMongoFileId(String mongoFileId) {
    GcsFileMetadata mapping =
        wingsPersistence.createQuery(GcsFileMetadata.class).filter(GcsFileMetadataKeys.fileId, mongoFileId).get();
    return mapping == null ? null : mapping.getGcsFileId();
  }

  @Override
  public String getMongoFileIdByGcsFileId(String gcsFileId) {
    GcsFileMetadata mapping =
        wingsPersistence.createQuery(GcsFileMetadata.class).filter(GcsFileMetadataKeys.gcsFileId, gcsFileId).get();
    return mapping == null ? null : mapping.getFileId();
  }

  @Override
  public void deleteGcsFileMetadataByMongoFileId(String mongoFileId) {
    GcsFileMetadata mapping =
        wingsPersistence.createQuery(GcsFileMetadata.class).filter(GcsFileMetadataKeys.fileId, mongoFileId).get();
    if (mapping != null) {
      wingsPersistence.delete(mapping);
    }
  }

  @Override
  public boolean updateGcsFileMetadata(String gcsFileId, String entityId, Integer version, Map<String, Object> others) {
    log.info("Updating GCS file '{}' with parent entity '{}' and version '{}' with {} other metadata entries.",
        gcsFileId, entityId, version, others == null ? 0 : others.size());
    GcsFileMetadata gcsFileMetadata =
        wingsPersistence.createQuery(GcsFileMetadata.class).filter(GcsFileMetadataKeys.gcsFileId, gcsFileId).get();
    if (gcsFileMetadata == null) {
      log.warn("Can't update GCS file metadata since no corresponding entry is found for file with id '{}'", gcsFileId);
      return false;
    }

    UpdateOperations<GcsFileMetadata> updateOperations = wingsPersistence.createUpdateOperations(GcsFileMetadata.class);
    if (entityId != null) {
      updateOperations.set("entityId", entityId);
    }
    if (version != null) {
      updateOperations.set("version", version);
    }
    if (isNotEmpty(others)) {
      updateOperations.set("others", others);
    }

    UpdateResults results = wingsPersistence.update(gcsFileMetadata, updateOperations);
    return results.getUpdatedCount() > 0;
  }

  @Override
  public GcsHarnessFileMetadata getFileMetadataByGcsFileId(String gcsFileId) {
    return wingsPersistence.createQuery(GcsFileMetadata.class, excludeAuthority)
        .filter(GcsFileMetadataKeys.gcsFileId, gcsFileId)
        .get();
  }

  @Override
  public List<String> getAllGcsFileIdsFromGcsFileMetadata(String entityId, FileBucket fileBucket) {
    List<GcsFileMetadata> gcsFileMetadatas = wingsPersistence.createQuery(GcsFileMetadata.class, excludeAuthority)
                                                 .filter(GcsFileMetadataKeys.entityId, entityId)
                                                 .filter(GcsFileMetadataKeys.fileBucket, fileBucket)
                                                 .asList();

    return gcsFileMetadatas.stream().map(GcsFileMetadata::getGcsFileId).distinct().collect(Collectors.toList());
  }

  @Override
  public String getLatestGcsFileIdFromGcsFileMetadata(String entityId, FileBucket fileBucket) {
    GcsFileMetadata gcsFileMetadata = wingsPersistence.createQuery(GcsFileMetadata.class, excludeAuthority)
                                          .filter(GcsFileMetadataKeys.entityId, entityId)
                                          .filter(GcsFileMetadataKeys.fileBucket, fileBucket)
                                          .order(Sort.descending(GcsFileMetadataKeys.createdAt))
                                          .get();

    return gcsFileMetadata == null ? null : gcsFileMetadata.getGcsFileId();
  }

  @Override
  public String getLatestGcsFileIdFromGcsFileMetadataByQualifier(
      String entityId, FileBucket fileBucket, String qualifier) {
    GcsFileMetadata gcsFileMetadata = wingsPersistence.createQuery(GcsFileMetadata.class, excludeAuthority)
                                          .filter(GcsFileMetadataKeys.entityId, entityId)
                                          .filter(GcsFileMetadataKeys.fileBucket, fileBucket)
                                          .filter("others.qualifier", qualifier)
                                          .order(Sort.descending(GcsFileMetadataKeys.createdAt))
                                          .get();

    return gcsFileMetadata == null ? null : gcsFileMetadata.getGcsFileId();
  }

  @Override
  public String getGcsFileIdFromGcsFileMetadataByVersion(String entityId, Integer version, FileBucket fileBucket) {
    GcsFileMetadata gcsFileMetadata = wingsPersistence.createQuery(GcsFileMetadata.class, excludeAuthority)
                                          .filter(GcsFileMetadataKeys.entityId, entityId)
                                          .filter(GcsFileMetadataKeys.fileBucket, fileBucket)
                                          .filter(GcsFileMetadataKeys.version, version)
                                          .get();

    return gcsFileMetadata == null ? null : gcsFileMetadata.getGcsFileId();
  }

  @Override
  public void deleteGcsFileMetadataByGcsFileId(String gcsFileId) {
    GcsFileMetadata mapping =
        wingsPersistence.createQuery(GcsFileMetadata.class).filter(GcsFileMetadataKeys.gcsFileId, gcsFileId).get();
    if (mapping != null) {
      wingsPersistence.delete(mapping);
    }
  }

  @Override
  public void save(GcsHarnessFileMetadata gcsHarnessFileMetadata) {
    CommonGcsHarnessFileMetadata commonGcsHarnessFileMetadata = (CommonGcsHarnessFileMetadata) gcsHarnessFileMetadata;
    GcsFileMetadata gcsFileMetadata = GcsFileMetadata.builder()
                                          .accountId(commonGcsHarnessFileMetadata.getAccountId())
                                          .fileId(commonGcsHarnessFileMetadata.getFileId())
                                          .gcsFileId(commonGcsHarnessFileMetadata.getGcsFileId())
                                          .fileName(commonGcsHarnessFileMetadata.getFileName())
                                          .fileLength(commonGcsHarnessFileMetadata.getFileLength())
                                          .checksum(commonGcsHarnessFileMetadata.getChecksum())
                                          .checksumType(commonGcsHarnessFileMetadata.getChecksumType())
                                          .mimeType(commonGcsHarnessFileMetadata.getMimeType())
                                          .fileBucket(commonGcsHarnessFileMetadata.getFileBucket())
                                          .build();
    wingsPersistence.save(gcsFileMetadata);
  }
}
