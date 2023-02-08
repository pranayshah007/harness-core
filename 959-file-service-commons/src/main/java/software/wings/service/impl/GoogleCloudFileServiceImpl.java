/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package software.wings.service.impl;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.data.structure.EmptyPredicate.isEmpty;

import static software.wings.service.impl.FileServiceUtils.FILE_PATH_SEPARATOR;
import static software.wings.service.impl.FileServiceUtils.GCS_ID_PREFIX;
import static software.wings.service.impl.FileServiceUtils.GoogleCloudFileIdComponent;
import static software.wings.service.impl.FileServiceUtils.parseGoogleCloudFileId;

import io.harness.annotations.dev.OwnedBy;
import io.harness.data.structure.UUIDGenerator;
import io.harness.delegate.beans.ChecksumType;
import io.harness.delegate.beans.FileBucket;
import io.harness.delegate.beans.FileMetadata;
import io.harness.eraro.ErrorCode;
import io.harness.exception.GCPStorageFileReadException;
import io.harness.exception.WingsException;
import io.harness.file.CommonGcsHarnessFileMetadata;
import io.harness.file.GcsHarnessFileMetadata;
import io.harness.file.HarnessFile;
import io.harness.file.dao.GcsHarnessFileMetadataDao;
import io.harness.stream.BoundedInputStream;

import software.wings.service.intfc.FileService;

import com.google.api.gax.paging.Page;
import com.google.cloud.ReadChannel;
import com.google.cloud.storage.Blob;
import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.Bucket;
import com.google.cloud.storage.BucketInfo;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.Storage.BucketListOption;
import com.google.cloud.storage.StorageClass;
import com.google.cloud.storage.StorageOptions;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.channels.Channels;
import java.nio.charset.Charset;
import java.util.Base64;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;

/**
 * Use Google Cloud Storage as file/blob storage.
 *
 * @author marklu on 11/16/18
 */
@Singleton
@Slf4j
@OwnedBy(PL)
public class GoogleCloudFileServiceImpl implements FileService {
  private static final String METADATA_FILE_NAME = "fileName";
  private static final String GOOGLE_APPLICATION_CREDENTIALS_PATH = "GOOGLE_APPLICATION_CREDENTIALS";
  public static final String FILE_SERVICE_CLUSTER_NAME = "FILE_SERVICE_CLUSTER_NAME";

  private GcsHarnessFileMetadataDao gcsHarnessFileMetadataDao;
  private String clusterName;
  private volatile Storage storage;

  @Inject
  public GoogleCloudFileServiceImpl(
      GcsHarnessFileMetadataDao gcsHarnessFileMetadataDao, @Named(FILE_SERVICE_CLUSTER_NAME) String clusterName) {
    this.gcsHarnessFileMetadataDao = gcsHarnessFileMetadataDao;
    this.clusterName = clusterName;
  }

  @Override
  public String saveFile(FileMetadata fileMetadata, InputStream in, FileBucket fileBucket) {
    String accountId = fileMetadata.getAccountId();
    String fileUuid = fileMetadata.getFileUuid();
    if (isEmpty(fileUuid)) {
      fileUuid = UUIDGenerator.generateUuid();
    }
    String gcsFileName = accountId + FILE_PATH_SEPARATOR + fileUuid;
    BlobId blobId = BlobId.of(getBucketName(fileBucket), gcsFileName);

    Map<String, String> metadata = new HashMap<>();
    metadata.put(METADATA_FILE_NAME, fileMetadata.getFileName());

    BlobInfo blobInfo = BlobInfo.newBuilder(blobId)
                            .setContentType(fileMetadata.getMimeType())
                            .setMd5(fileMetadata.getChecksum())
                            .setMetadata(metadata)
                            .build();
    try {
      Blob blob = getStorage().create(blobInfo, IOUtils.toByteArray(in));
      String gcsFileId = generateFileId(blobId);
      fileMetadata.setChecksum(blob.getMd5());
      fileMetadata.setFileLength(blob.getSize());
      fileMetadata.setFileUuid(gcsFileId);
      saveGcsFileMetadata(fileMetadata, fileBucket, null, gcsFileId);
      log.info("File '{}' of type {} is saved in GCS with id {}", fileMetadata.getFileName(), fileBucket, gcsFileId);
      return gcsFileId;
    } catch (IOException e) {
      throw new WingsException(ErrorCode.SAVE_FILE_INTO_GCP_STORAGE_FAILED, e);
    }
  }

  @Override
  public String saveFile(HarnessFile baseFile, InputStream uploadedInputStream, FileBucket fileBucket) {
    String accountId = baseFile.getAccountId();
    String fileUuid = baseFile.getFileUuid();
    if (isEmpty(fileUuid)) {
      fileUuid = UUIDGenerator.generateUuid();
    }
    String gcsFileName = accountId + FILE_PATH_SEPARATOR + fileUuid;
    BlobId blobId = BlobId.of(getBucketName(fileBucket), gcsFileName);

    Map<String, String> metadata = new HashMap<>();
    metadata.put(METADATA_FILE_NAME, baseFile.getFileName());

    BlobInfo blobInfo = BlobInfo.newBuilder(blobId)
                            .setContentType(baseFile.getMimeType())
                            .setMd5(baseFile.getChecksum())
                            .setMetadata(metadata)
                            .build();
    try {
      Blob blob = getStorage().create(blobInfo, IOUtils.toByteArray(uploadedInputStream));
      String gcsFileId = generateFileId(blobId);
      baseFile.setChecksum(blob.getMd5());
      baseFile.setSize(blob.getSize());
      baseFile.setFileUuid(gcsFileId);
      saveGcsFileMetadata(baseFile, fileBucket, null, gcsFileId);
      log.info("File '{}' of type {} is saved in GCS with id {}", baseFile.getFileName(), fileBucket, gcsFileId);
      return gcsFileId;
    } catch (IOException e) {
      throw new WingsException(ErrorCode.SAVE_FILE_INTO_GCP_STORAGE_FAILED);
    }
  }

  @Override
  public void deleteFile(String fileId, FileBucket fileBucket) {
    log.info("Deleting file {}", fileId);
    // delete gcs file metadata first.
    gcsHarnessFileMetadataDao.deleteGcsFileMetadataByGcsFileId(fileId);

    BlobId blobId = getBlobIdFromFileId(fileId, fileBucket);
    getStorage().delete(blobId);
    log.info("Deleted file {}", fileId);
  }

  @Override
  public File download(String fileId, File file, FileBucket fileBucket) {
    BlobId blobId = getBlobIdFromFileId(fileId, fileBucket);
    Blob blob = getStorage().get(blobId);
    if (blob == null) {
      throw new WingsException(
          " File with id " + fileId + " or blob id " + blobId + " can't be found in Google Cloud Storage.");
    }

    blob.downloadTo(file.toPath());
    return file;
  }

  @Override
  public void downloadToStream(String fileId, OutputStream op, FileBucket fileBucket) {
    BlobId blobId = getBlobIdFromFileId(fileId, fileBucket);
    Blob blob = getStorage().get(blobId);
    if (blob == null) {
      log.error(" File with id " + fileId + " or blob id " + blobId + " can't be found in Google Cloud Storage.");
      throw new WingsException(ErrorCode.FILE_NOT_FOUND_ERROR);
    }

    try (ReadChannel reader = blob.reader()) {
      InputStream in = Channels.newInputStream(reader);
      IOUtils.copy(in, op);
    } catch (IOException e) {
      throw new GCPStorageFileReadException(e);
    }
  }

  @Override
  public InputStream openDownloadStream(String fileId, FileBucket fileBucket) {
    BlobId blobId = getBlobIdFromFileId(fileId, fileBucket);
    Blob blob = getStorage().get(blobId);
    if (blob == null) {
      log.error(" File with id " + fileId + " or blob id " + blobId + " can't be found in Google Cloud Storage.");
      throw new WingsException(ErrorCode.FILE_NOT_FOUND_ERROR);
    }

    byte[] content = blob.getContent();
    return new ByteArrayInputStream(content);
  }

  @Override
  public FileMetadata getFileMetadata(String fileId, FileBucket fileBucket) {
    GoogleCloudFileIdComponent component = parseGoogleCloudFileId(fileId);
    BlobId blobId = BlobId.of(getBucketName(fileBucket), component.filePath);

    GcsHarnessFileMetadata gcsFileMetadata = gcsHarnessFileMetadataDao.getFileMetadataByGcsFileId(fileId);
    if (gcsFileMetadata == null) {
      log.error(" File with id " + fileId + " or blob id " + blobId + " can't be found in Google Cloud Storage.");
      throw new WingsException(ErrorCode.FILE_NOT_FOUND_ERROR);
    } else {
      // Old 'gcsFileMetadata' doesn't have the file length field set. It has to be read from GCS bucket entries'
      // metadata.
      if (gcsFileMetadata.getFileLength() == 0) {
        Blob blob = getStorage().get(blobId);
        if (blob == null) {
          log.error(" File with id " + fileId + " or blob id " + blobId + " can't be found in Google Cloud Storage.");
          throw new WingsException(ErrorCode.FILE_NOT_FOUND_ERROR);
        } else {
          gcsFileMetadata.setFileLength(blob.getSize());
          gcsFileMetadata.setChecksum(blob.getMd5());
          gcsFileMetadata.setChecksumType(ChecksumType.MD5);
        }
      }
      return FileMetadata.builder()
          .fileName(gcsFileMetadata.getFileName())
          .fileLength(gcsFileMetadata.getFileLength())
          .checksum(gcsFileMetadata.getChecksum())
          .checksumType(gcsFileMetadata.getChecksumType())
          .mimeType(gcsFileMetadata.getMimeType())
          .accountId(component.accountId)
          .fileUuid(component.fileUuid)
          .metadata(gcsFileMetadata.getOthers())
          .build();
    }
  }

  @Override
  public boolean updateParentEntityIdAndVersion(Class entityClass, String entityId, Integer version, String fileId,
      Map<String, Object> others, FileBucket fileBucket) {
    // Note that 'entityClass' won't be persisted since it's not used at all.
    // Update the gcs file metadata with entity class/id/version info.
    return gcsHarnessFileMetadataDao.updateGcsFileMetadata(fileId, entityId, version, others);
  }

  @Override
  public List<String> getAllFileIds(String entityId, FileBucket fileBucket) {
    return gcsHarnessFileMetadataDao.getAllGcsFileIdsFromGcsFileMetadata(entityId, fileBucket);
  }

  @Override
  public String getLatestFileId(String entityId, FileBucket fileBucket) {
    return gcsHarnessFileMetadataDao.getLatestGcsFileIdFromGcsFileMetadata(entityId, fileBucket);
  }

  @Override
  public String getLatestFileIdByQualifier(String entityId, FileBucket fileBucket, String qualifier) {
    return gcsHarnessFileMetadataDao.getLatestGcsFileIdFromGcsFileMetadataByQualifier(entityId, fileBucket, qualifier);
  }

  @Override
  public String getFileIdByVersion(String entityId, int version, FileBucket fileBucket) {
    return gcsHarnessFileMetadataDao.getGcsFileIdFromGcsFileMetadataByVersion(entityId, version, fileBucket);
  }

  @Override
  public String uploadFromStream(
      String filename, BoundedInputStream in, FileBucket fileBucket, Map<String, Object> metaData) {
    Map<String, String> stringMap = new HashMap<>();
    for (Entry<String, Object> entry : metaData.entrySet()) {
      // Only put string format metadata in as GCS only access String/String metadata mappings
      if (entry.getValue() instanceof String) {
        stringMap.put(entry.getKey(), (String) entry.getValue());
      }
    }

    BlobId blobId = BlobId.of(getBucketName(fileBucket), filename);
    BlobInfo blobInfo = BlobInfo.newBuilder(blobId).setMetadata(stringMap).build();
    try {
      getStorage().create(blobInfo, IOUtils.toByteArray(in));
      return generateFileId(blobId);
    } catch (IOException e) {
      throw new WingsException(ErrorCode.SAVE_FILE_INTO_GCP_STORAGE_FAILED, e);
    }
  }

  @Override
  public void deleteAllFilesForEntity(String entityId, FileBucket fileBucket) {
    getAllFileIds(entityId, fileBucket).forEach(fileId -> { deleteFile(fileId, fileBucket); });
  }

  private String generateFileId(BlobId blobId) {
    String idString = GCS_ID_PREFIX + blobId.getName();
    return Base64.getEncoder().encodeToString(idString.getBytes(Charset.defaultCharset()));
  }

  private BlobId getBlobIdFromFileId(String fileId, FileBucket fileBucket) {
    GoogleCloudFileIdComponent component = parseGoogleCloudFileId(fileId);
    return BlobId.of(getBucketName(fileBucket), component.filePath);
  }

  void initialize() {
    String googleCredentialsPath = System.getenv(GOOGLE_APPLICATION_CREDENTIALS_PATH);
    if (isEmpty(googleCredentialsPath) || !new File(googleCredentialsPath).exists()) {
      throw new WingsException("Invalid credentials found at " + googleCredentialsPath);
    }

    if (isEmpty(clusterName)) {
      throw new WingsException("Cluster name should be configured when google cloud storage is used for file storage!");
    }

    createFileBucketsInGCS();

    log.info("Google cloud storage based file service has been initialized.");
  }

  void saveGcsFileMetadata(HarnessFile baseFile, FileBucket fileBucket, String mongoFileId, String gcsFileId) {
    GoogleCloudFileIdComponent component = parseGoogleCloudFileId(gcsFileId);
    BlobId blobId = BlobId.of(getBucketName(fileBucket), component.filePath);
    Blob blob = getStorage().get(blobId);
    if (blob == null) {
      throw new WingsException(
          " File with id " + gcsFileId + " or blob id " + blobId + " can't be found in Google Cloud Storage.");
    }

    CommonGcsHarnessFileMetadata gcsFileMetadata = CommonGcsHarnessFileMetadata.builder()
                                                       .accountId(baseFile.getAccountId())
                                                       .fileId(mongoFileId)
                                                       .gcsFileId(gcsFileId)
                                                       .fileName(baseFile.getFileName())
                                                       .fileLength(blob.getSize())
                                                       .checksum(blob.getMd5())
                                                       .checksumType(ChecksumType.MD5)
                                                       .mimeType(baseFile.getMimeType())
                                                       .fileBucket(fileBucket)
                                                       .build();
    gcsHarnessFileMetadataDao.save(gcsFileMetadata);
  }

  void saveGcsFileMetadata(FileMetadata fileMetadata, FileBucket fileBucket, String mongoFileId, String gcsFileId) {
    GoogleCloudFileIdComponent component = parseGoogleCloudFileId(gcsFileId);
    BlobId blobId = BlobId.of(getBucketName(fileBucket), component.filePath);
    Blob blob = getStorage().get(blobId);
    if (blob == null) {
      log.error(" File with id " + gcsFileId + " or blob id " + blobId + " can't be found in Google Cloud Storage.");
      throw new WingsException(ErrorCode.FILE_NOT_FOUND_ERROR);
    }

    CommonGcsHarnessFileMetadata gcsFileMetadata = CommonGcsHarnessFileMetadata.builder()
                                                       .accountId(fileMetadata.getAccountId())
                                                       .fileId(mongoFileId)
                                                       .gcsFileId(gcsFileId)
                                                       .fileName(fileMetadata.getFileName())
                                                       .fileLength(blob.getSize())
                                                       .checksum(blob.getMd5())
                                                       .checksumType(ChecksumType.MD5)
                                                       .mimeType(fileMetadata.getMimeType())
                                                       .others(fileMetadata.getMetadata())
                                                       .fileBucket(fileBucket)
                                                       .build();
    gcsHarnessFileMetadataDao.save(gcsFileMetadata);
  }

  /**
   * This method should create the buckets for all FileBucket types in Google Cloud Storage post Guice initialization of
   * this class. Default to NEARLINE storage class. See pricing in the following page:
   *    https://cloud.google.com/storage/docs/storage-classes
   */
  private void createFileBucketsInGCS() {
    Page<Bucket> buckets = getStorage().list(BucketListOption.pageSize(500), BucketListOption.prefix(clusterName));
    Set<String> bucketNames = new HashSet<>();
    for (Bucket bucket : buckets.iterateAll()) {
      bucketNames.add(bucket.getName());
    }

    FileBucket[] fileBuckets = FileBucket.values();
    for (FileBucket fileBucket : fileBuckets) {
      String bucketName = getBucketName(fileBucket);
      if (bucketNames.contains(bucketName)) {
        log.info("Bucket with name '{}' exists in Google Cloud Storage already.", bucketName);
      } else {
        try {
          getStorage().create(
              BucketInfo.newBuilder(bucketName).setStorageClass(StorageClass.NEARLINE).setLocation("us").build());
          log.info("Bucket with name '{}' created in Google Cloud Storage.", bucketName);
        } catch (Exception e) {
          log.warn(
              "Creation of bucket in Google Cloud Storage '{}' failed with error: '{}', the bucket may have been created already "
                  + "or the current account doesn't have permission to create bucket.",
              bucketName, e.getMessage());
        }
      }
    }
  }

  private String getBucketName(FileBucket fileBucket) {
    return clusterName + "-" + fileBucket.representationName();
  }

  private Storage getStorage() {
    Storage result = storage;
    if (result == null) {
      synchronized (GoogleCloudFileServiceImpl.class) {
        result = storage;
        if (result == null) {
          storage = result = StorageOptions.getDefaultInstance().getService();
        }
      }
    }
    return result;
  }
}
