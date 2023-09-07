/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.cache;

import io.harness.ModuleType;
import io.harness.beans.cache.api.CacheMetadataDetail;
import io.harness.beans.cache.api.CacheMetadataInfo;
import io.harness.beans.cache.api.DeleteCacheResponse;
import io.harness.ci.config.CICacheIntelligenceConfig;
import io.harness.ci.config.CIExecutionServiceConfig;
import io.harness.ci.execution.execution.CIDockerLayerCachingConfigService;
import io.harness.licensing.entities.modules.CIModuleLicense;
import io.harness.licensing.entities.modules.ModuleLicense;
import io.harness.repositories.ModuleLicenseRepository;

import com.google.api.gax.paging.Page;
import com.google.auth.oauth2.ServiceAccountCredentials;
import com.google.cloud.storage.Blob;
import com.google.cloud.storage.Blob.BlobSourceOption;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageOptions;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

@Singleton
@Slf4j

public class CICacheManagementServiceImpl implements CICacheManagementService {
  private final CIDockerLayerCachingConfigService ciDockerLayerCachingConfigService;
  private final CIExecutionServiceConfig ciExecutionServiceConfig;
  private final ModuleLicenseRepository moduleLicenseRepository;
  private final Storage storage;
  private final long DEFAULT_ALLOWANCE = 2147483648L;
  private final String UNIT_BYTES = "Bytes";
  private final String DEFAULT_SERVICE_KEY = "gcp_service_key";

  @Inject
  CICacheManagementServiceImpl(CIExecutionServiceConfig ciExecutionServiceConfig,
      ModuleLicenseRepository moduleLicenseRepository,
      CIDockerLayerCachingConfigService ciDockerLayerCachingConfigService) {
    this.ciExecutionServiceConfig = ciExecutionServiceConfig;
    this.moduleLicenseRepository = moduleLicenseRepository;
    this.ciDockerLayerCachingConfigService = ciDockerLayerCachingConfigService;
    CICacheIntelligenceConfig cacheIntelligenceConfig = ciExecutionServiceConfig.getCacheIntelligenceConfig();
    // workaround for local when service key isn't needed
    if (cacheIntelligenceConfig.getServiceKey().equals(DEFAULT_SERVICE_KEY)) {
      storage = StorageOptions.getDefaultInstance().getService();
      return;
    }
    File credentialsFile = new File(cacheIntelligenceConfig.getServiceKey());
    ServiceAccountCredentials credentials = null;
    try (FileInputStream serviceAccountStream = new FileInputStream(credentialsFile)) {
      credentials = ServiceAccountCredentials.fromStream(serviceAccountStream);
    } catch (FileNotFoundException e) {
      log.error("Failed to find Google credential file for the GCS service account in the specified path.", e);
    } catch (IOException e) {
      log.error("Failed to get Google credential file for the GCS service account.", e);
    }
    storage = StorageOptions.newBuilder().setCredentials(credentials).build().getService();
  }

  public CacheMetadataInfo getCacheMetadata(String accountId) {
    CICacheIntelligenceConfig cacheIntelligenceConfig = ciExecutionServiceConfig.getCacheIntelligenceConfig();
    long allowance = getAllowance(accountId);
    String bucketName = cacheIntelligenceConfig.getBucket();
    Page<Blob> blobs = storage.list(bucketName, Storage.BlobListOption.prefix(accountId));

    List<CacheMetadataDetail> details = new ArrayList<>();
    long total = 0;
    for (Blob blob : blobs.iterateAll()) {
      total += blob.getSize();
      String[] pathList = blob.getName().split("/");
      String path = String.join("/", Arrays.copyOfRange(pathList, 2, pathList.length));
      CacheMetadataDetail detail = CacheMetadataDetail.builder().cachePath(path).size(blob.getSize()).build();
      details.add(detail);
    }

    return CacheMetadataInfo.builder()
        .used(total)
        .total(allowance)
        .available(allowance - total)
        .unit(UNIT_BYTES)
        .details(details)
        .build();
  }

  public DeleteCacheResponse deleteCache(String accountId, String path, String cacheType) {
    if (CacheType.DLC.getName().equals(cacheType)) {
      List<CacheMetadataDetail> deletedList = ciDockerLayerCachingConfigService.purgeDockerLayerCache(accountId);
      return DeleteCacheResponse.builder().deleted(deletedList).build();
    }

    CICacheIntelligenceConfig cacheIntelligenceConfig = ciExecutionServiceConfig.getCacheIntelligenceConfig();
    String bucketName = cacheIntelligenceConfig.getBucket();
    Page<Blob> blobs = storage.list(bucketName, Storage.BlobListOption.prefix(accountId));
    List<CacheMetadataDetail> deletedList = new ArrayList<>();
    for (Blob blob : blobs.iterateAll()) {
      if (path == null || blob.getName().contains(path)) {
        boolean deleted = blob.delete(BlobSourceOption.generationMatch());
        if (deleted) {
          String[] pathList = blob.getName().split("/");
          String deletedPath = String.join("/", Arrays.copyOfRange(pathList, 2, pathList.length));
          CacheMetadataDetail detail =
              CacheMetadataDetail.builder().cachePath(deletedPath).size(blob.getSize()).build();
          deletedList.add(detail);
        }
      }
    }
    return DeleteCacheResponse.builder().deleted(deletedList).build();
  }

  long getAllowance(String accountIdentifier) {
    List<ModuleLicense> licenses =
        moduleLicenseRepository.findByAccountIdentifierAndModuleType(accountIdentifier, ModuleType.CI);
    long expiryTime = 0;
    long latestAllowance = DEFAULT_ALLOWANCE;
    for (ModuleLicense license : licenses) {
      CIModuleLicense ciModuleLicense = (CIModuleLicense) license;
      if (ciModuleLicense.getExpiryTime() > expiryTime) {
        expiryTime = ciModuleLicense.getExpiryTime();
        latestAllowance = ciModuleLicense.getCacheAllowance();
      }
    }
    return latestAllowance;
  }
}
