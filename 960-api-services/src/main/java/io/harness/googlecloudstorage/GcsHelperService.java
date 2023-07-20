/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.googlecloudstorage;

import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.eraro.ErrorCode.INVALID_CLOUD_PROVIDER;
import static io.harness.exception.WingsException.USER;

import static software.wings.helpers.ext.jenkins.BuildDetails.Builder.aBuildDetails;

import static java.lang.String.format;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.artifact.ArtifactMetadataKeys;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.WingsException;
import io.harness.gcp.helpers.GcpHttpTransportHelperService;
import io.harness.network.Http;

import software.wings.helpers.ext.jenkins.BuildDetails;

import com.google.api.gax.paging.Page;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.storage.Blob;
import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.Bucket;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageOptions;
import com.google.common.collect.Lists;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(HarnessTeam.CDP)
@Singleton
@Slf4j
public class GcsHelperService {
  private static final String GOOGLE_APIS_HOST = "googleapis.com";

  @Inject private GcpHttpTransportHelperService gcpHttpTransportHelperService;
  @Inject private GcpCredentialsHelper gcpCredentialsHelper;
  private static final String INVALID_BUCKET_PROJECT__ERROR =
      "Unable to checkout bucket: %s in Google Cloud Storage. Please "
      + "ensure that provided project and bucket exists in GCP";

  /**
   * Gets a GCS Service
   *
   * @param serviceAccountKeyFileContent
   * @param isUseDelegate
   *
   * @return the gcs storage service
   */
  public Storage getGcsStorageService(char[] serviceAccountKeyFileContent, boolean isUseDelegate, String projectId) {
    try {
      GoogleCredentials credentials =
          gcpCredentialsHelper.getGoogleCredentials(serviceAccountKeyFileContent, isUseDelegate);
      StorageOptions.Builder storageOptions =
          StorageOptions.newBuilder().setProjectId(projectId).setCredentials(credentials);
      if (Http.getProxyHostName() != null && !Http.shouldUseNonProxy(GOOGLE_APIS_HOST)) {
        storageOptions.setTransportOptions(GcpCredentialsHelper.getHttpTransportOptionsForProxy());
      }
      return storageOptions.build().getService();
    } catch (GeneralSecurityException e) {
      log.error("Security exception getting Google storage service", e);
      throw new WingsException(INVALID_CLOUD_PROVIDER, USER)
          .addParam("message", "Invalid Google Cloud Platform credentials.");
    } catch (IOException e) {
      log.error("Error getting Google storage service", e);
      throw new WingsException(INVALID_CLOUD_PROVIDER, USER)
          .addParam("message", "Invalid Google Cloud Platform credentials.");
    }
  }

  public List<BuildDetails> listBuilds(GcsInternalConfig gcsInternalConfig) {
    boolean isVersioningEnabled = false;
    String nextPageToken = "";
    List<BuildDetails> buildDetailsFinalList = new ArrayList<>();
    Storage storage = getGcsStorageService(gcsInternalConfig.getServiceAccountKeyFileContent(),
        gcsInternalConfig.isUseDelegate(), gcsInternalConfig.getProject());
    Bucket bucket = storage.get(gcsInternalConfig.getBucket());
    if (bucket == null) {
      throw new InvalidRequestException(format(INVALID_BUCKET_PROJECT__ERROR, gcsInternalConfig.getBucket()));
    }
    if (bucket.versioningEnabled() != null) {
      isVersioningEnabled = bucket.versioningEnabled();
    }
    do {
      Page<Blob> blobs = bucket.list(
          Storage.BlobListOption.versions(isVersioningEnabled), Storage.BlobListOption.pageToken(nextPageToken));
      buildDetailsFinalList.addAll(getArtifactBuildDetails(blobs));
      nextPageToken = blobs.getNextPageToken();
    } while (isNotEmpty(nextPageToken));
    return buildDetailsFinalList;
  }

  public Map<String, String> listBuckets(GcsInternalConfig gcsInternalConfig) {
    Map<String, String> bucketsMap = new HashMap<>();
    Storage storage = getGcsStorageService(gcsInternalConfig.getServiceAccountKeyFileContent(),
        gcsInternalConfig.isUseDelegate(), gcsInternalConfig.getProject());
    String nextPageToken = "";
    do {
      Page<Bucket> buckets = storage.list();
      addBucketsToMap(buckets, bucketsMap);
      nextPageToken = buckets.getNextPageToken();
    } while (isNotEmpty(nextPageToken));
    return bucketsMap;
  }

  private void addBucketsToMap(Page<Bucket> buckets, Map<String, String> bucketsMap) {
    if (buckets == null) {
      return;
    }
    buckets.iterateAll().forEach(bucket -> bucketsMap.put(bucket.getGeneratedId(), bucket.getName()));
  }

  private List<BuildDetails> getArtifactBuildDetails(Page<Blob> blobs) {
    if (blobs == null) {
      return Lists.newArrayList();
    }
    List<BuildDetails> buildDetailsList = Lists.newArrayList();
    blobs.iterateAll().forEach(blob -> {
      if (blob != null) {
        buildDetailsList.add(mapBlobToBuildDetails(blob));
      }
    });
    return buildDetailsList;
  }

  private BuildDetails mapBlobToBuildDetails(Blob blob) {
    Map<String, String> artifactMetaDataMap = new HashMap<>();

    artifactMetaDataMap.put(ArtifactMetadataKeys.url, blob.getSelfLink());
    artifactMetaDataMap.put(ArtifactMetadataKeys.artifactName, blob.getName());
    artifactMetaDataMap.put(ArtifactMetadataKeys.bucket, blob.getBucket());
    artifactMetaDataMap.put(ArtifactMetadataKeys.artifactFileSize, String.valueOf(blob.getSize()));
    return aBuildDetails()
        .withArtifactPath(blob.getName())
        .withArtifactFileSize(String.valueOf(blob.getSize()))
        .withMetadata(artifactMetaDataMap)
        .withUpdateTime(blob.getUpdateTime())
        .withBuildUrl(blob.getSelfLink())
        .build();
  }

  public InputStream downloadObject(GcsInternalConfig gcsInternalConfig, String fileName) throws Exception {
    Storage storage = getGcsStorageService(gcsInternalConfig.getServiceAccountKeyFileContent(),
        gcsInternalConfig.isUseDelegate(), gcsInternalConfig.getProject());
    Bucket bucket = storage.get(gcsInternalConfig.getBucket());
    if (bucket == null) {
      throw new InvalidRequestException(format(INVALID_BUCKET_PROJECT__ERROR, gcsInternalConfig.getBucket()));
    }

    Blob blob = storage.get(BlobId.of(gcsInternalConfig.getBucket(), fileName));
    return new ByteArrayInputStream(blob.getContent());
  }
}
