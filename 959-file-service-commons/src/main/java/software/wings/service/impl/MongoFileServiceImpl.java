/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package software.wings.service.impl;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.persistence.HPersistence.DEFAULT_STORE;

import static software.wings.service.impl.FileServiceUtils.verifyFileIntegrity;

import static com.google.common.collect.ImmutableMap.of;
import static com.mongodb.client.model.Sorts.descending;
import static com.mongodb.client.model.Sorts.orderBy;
import static java.util.stream.Collectors.toList;
import static java.util.stream.StreamSupport.stream;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.beans.ChecksumType;
import io.harness.delegate.beans.FileBucket;
import io.harness.delegate.beans.FileMetadata;
import io.harness.file.HarnessFile;
import io.harness.persistence.HPersistence;
import io.harness.stream.BoundedInputStream;

import software.wings.service.intfc.FileService;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.mongodb.BasicDBObject;
import com.mongodb.DBCollection;
import com.mongodb.MongoGridFSException;
import com.mongodb.client.gridfs.GridFSBucket;
import com.mongodb.client.gridfs.GridFSBuckets;
import com.mongodb.client.gridfs.GridFSFindIterable;
import com.mongodb.client.gridfs.model.GridFSFile;
import com.mongodb.client.gridfs.model.GridFSUploadOptions;
import com.mongodb.client.model.Filters;
import dev.morphia.AdvancedDatastore;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.stream.Collectors;
import javax.validation.executable.ValidateOnExecution;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.bson.types.ObjectId;

/**
 * Use Mongo's {@see GridFSFile} as file/blob storage.
 */
@ValidateOnExecution
@Singleton
@Slf4j
@OwnedBy(PL)
public class MongoFileServiceImpl implements FileService {
  @Inject private HPersistence hPersistence;

  /**
   * {@inheritDoc}
   */
  @Override
  public File download(String fileId, File file, FileBucket fileBucket) {
    try (FileOutputStream streamToDownload = new FileOutputStream(file)) {
      getOrCreateGridFSBucket(fileBucket.representationName()).downloadToStream(new ObjectId(fileId), streamToDownload);
      return file;
    } catch (IOException ex) {
      log.error("Error in download", ex);
      return null;
    }
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void downloadToStream(String fileId, OutputStream outputStream, FileBucket fileBucket) {
    getOrCreateGridFSBucket(fileBucket.representationName()).downloadToStream(new ObjectId(fileId), outputStream);
  }

  @Override
  public InputStream openDownloadStream(String fileId, FileBucket fileBucket) {
    return getOrCreateGridFSBucket(fileBucket.representationName()).openDownloadStream(new ObjectId(fileId));
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public List<String> getAllFileIds(String entityId, FileBucket fileBucket) {
    GridFSFindIterable filemetaData =
        getOrCreateGridFSBucket(fileBucket.representationName()).find(Filters.eq("metadata.entityId", entityId));
    return stream(filemetaData.sort(descending("uploadDate")).spliterator(), false)
        .map(gridFSFile -> gridFSFile.getObjectId().toHexString())
        .collect(toList());
  }

  @Override
  public String getLatestFileId(String entityId, FileBucket fileBucket) {
    final GridFSFile first = getOrCreateGridFSBucket(fileBucket.representationName())
                                 .find(Filters.and(Filters.eq("metadata.entityId", entityId)))
                                 .sort(orderBy(descending("uploadDate")))
                                 .limit(1)
                                 .first();
    if (first == null) {
      return null;
    }
    return first.getId().asObjectId().getValue().toHexString();
  }

  @Override
  public String getLatestFileIdByQualifier(String entityId, FileBucket fileBucket, String qualifier) {
    final GridFSFile first =
        getOrCreateGridFSBucket(fileBucket.representationName())
            .find(Filters.and(Filters.eq("metadata.entityId", entityId), Filters.eq("qualifier", qualifier)))
            .sort(orderBy(descending("uploadDate")))
            .limit(1)
            .first();
    if (first == null) {
      return null;
    }
    return first.getId().asObjectId().getValue().toHexString();
  }

  @Override
  public String getFileIdByVersion(String entityId, int version, FileBucket fileBucket) {
    final GridFSFile first =
        getOrCreateGridFSBucket(fileBucket.representationName())
            .find(Filters.and(Filters.eq("metadata.entityId", entityId), Filters.eq("metadata.version", version)))
            .limit(1)
            .first();
    if (first == null) {
      return null;
    }
    return first.getId().asObjectId().getValue().toHexString();
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public String uploadFromStream(
      String filename, BoundedInputStream in, FileBucket bucket, Map<String, Object> metaData) {
    GridFSUploadOptions gridFSOptions =
        new GridFSUploadOptions().chunkSizeBytes(bucket.getChunkSize()).metadata(new Document(metaData));
    ObjectId fileId =
        getOrCreateGridFSBucket(bucket.representationName()).uploadFromStream(filename, in, gridFSOptions);
    return fileId.toHexString();
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public FileMetadata getFileMetadata(String fileId, FileBucket fileBucket) {
    FileMetadata fileMetadata = null;

    GridFSFindIterable gridFSFiles =
        getOrCreateGridFSBucket(fileBucket.representationName()).find(Filters.eq("_id", new ObjectId(fileId)));
    GridFSFile gridFSFile = gridFSFiles.first();
    if (gridFSFile != null) {
      Document metadata = gridFSFile.getExtraElements();
      if (metadata == null) {
        fileMetadata = FileMetadata.builder()
                           .fileName(gridFSFile.getFilename())
                           .fileUuid(fileId)
                           .fileLength(gridFSFile.getLength())
                           .checksumType(ChecksumType.MD5)
                           .checksum(gridFSFile.getMD5())
                           .build();
      } else {
        fileMetadata =
            FileMetadata.builder()
                .fileName(gridFSFile.getFilename())
                .fileUuid(fileId)
                .fileLength(gridFSFile.getLength())
                .checksumType(ChecksumType.MD5)
                .checksum(gridFSFile.getMD5())
                .mimeType(metadata.getString("contentType"))
                .metadata(metadata.entrySet().stream().collect(Collectors.toMap(Entry::getKey, Entry::getValue)))
                .build();
      }
    }

    return fileMetadata;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public String saveFile(FileMetadata fileMetadata, InputStream in, FileBucket fileBucket) {
    log.info("Saving file {} ", fileMetadata);
    Document metadata = new Document();

    if (isNotBlank(fileMetadata.getChecksum()) && fileMetadata.getChecksumType() != null) {
      metadata.append("checksum", fileMetadata.getChecksum());
      metadata.append("checksumType", fileMetadata.getChecksumType().name());
    }

    if (isNotBlank(fileMetadata.getMimeType())) {
      metadata.append("mimeType", fileMetadata.getMimeType());
    }

    if (isNotBlank(fileMetadata.getRelativePath())) {
      metadata.append("relativePath", fileMetadata.getRelativePath());
    }

    GridFSUploadOptions options =
        new GridFSUploadOptions().chunkSizeBytes(fileBucket.getChunkSize()).metadata(metadata);

    ObjectId fileId = getOrCreateGridFSBucket(fileBucket.representationName())
                          .uploadFromStream(fileMetadata.getFileName(), in, options);
    log.info("Saved file {}. Returning fileId {}", fileMetadata.getFileName(), fileId.toHexString());
    return fileId.toHexString();
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public boolean updateParentEntityIdAndVersion(Class entityClass, String entityId, Integer version, String fileId,
      Map<String, Object> others, FileBucket fileBucket) {
    DBCollection collection =
        hPersistence.getDatastore(DEFAULT_STORE).getDB().getCollection(fileBucket.representationName() + ".files");

    // TODO: creating this index here makes no sense
    collection.createIndex(
        new BasicDBObject(of("metadata.entityId", 1, "metadata.version", 1)), new BasicDBObject("background", true));

    Map<String, Object> updateMap = new HashMap<>();
    if (isNotEmpty(others)) {
      updateMap.putAll(others);
    }

    if (entityClass != null) {
      updateMap.put("metadata.class", entityClass.getCanonicalName());
    }
    if (entityId != null) {
      updateMap.put("metadata.entityId", entityId);
    }
    if (version != null) {
      updateMap.put("metadata.version", version);
    }

    return collection
               .update(new BasicDBObject("_id", new ObjectId(fileId)),
                   new BasicDBObject("$set", new BasicDBObject(updateMap)))
               .getN()
        > 0;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public String saveFile(HarnessFile baseFile, InputStream inputStream, FileBucket bucket) {
    GridFSUploadOptions gridFSOptions = new GridFSUploadOptions().chunkSizeBytes(bucket.getChunkSize());
    String fileId = getOrCreateGridFSBucket(bucket.representationName())
                        .uploadFromStream(baseFile.getFileName(), inputStream, gridFSOptions)
                        .toHexString();
    FileMetadata fileMetadata = getFileMetadata(fileId, bucket);
    verifyFileIntegrity(baseFile, fileMetadata);
    baseFile.setChecksum(fileMetadata.getChecksum());
    baseFile.setFileUuid(fileId);
    return fileId;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void deleteFile(String fileId, FileBucket fileBucket) {
    log.info("Deleting file {} from bucket {}", fileId, fileBucket);
    try {
      getOrCreateGridFSBucket(fileBucket.representationName()).delete(new ObjectId(fileId));
      log.info("Deleted file {} from bucket {}", fileId, fileBucket);
    } catch (MongoGridFSException e) {
      // HAR-7371: This is a workaround for another bug HAR-7336 which deleted files in GridFS by mistake.
      if (e.getMessage().contains("No file found with the id")) {
        log.info("File {} no longer exist in bucket {}. Skipped.", fileId, fileBucket);
      } else {
        throw e;
      }
    }
  }

  @Override
  public void deleteAllFilesForEntity(String entityId, FileBucket fileBucket) {
    final GridFSBucket bucket = getOrCreateGridFSBucket(fileBucket.representationName());
    getAllFileIds(entityId, fileBucket).forEach(fileUuid -> bucket.delete(new ObjectId(fileUuid)));
  }

  GridFSBucket getOrCreateGridFSBucket(String bucketName) {
    final AdvancedDatastore datastore = hPersistence.getDatastore(DEFAULT_STORE);
    return GridFSBuckets.create(datastore.getMongo().getDatabase(datastore.getDB().getName()), bucketName);
  }
}
