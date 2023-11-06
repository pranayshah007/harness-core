/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.batch.processing.datadeletion.azure.step;

import static io.harness.annotations.dev.HarnessTeam.CE;
import static io.harness.ccm.commons.entities.datadeletion.DataDeletionStep.AZURE_STORAGE;

import io.harness.annotations.dev.OwnedBy;
import io.harness.batch.processing.config.AzureStorageSyncConfig;
import io.harness.batch.processing.config.BatchMainConfig;
import io.harness.ccm.commons.entities.datadeletion.DataDeletionRecord;

import com.azure.core.http.rest.PagedResponse;
import com.azure.storage.blob.BlobClient;
import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.BlobServiceClient;
import com.azure.storage.blob.BlobServiceClientBuilder;
import com.azure.storage.blob.models.BlobItem;
import com.azure.storage.blob.models.BlobListDetails;
import com.azure.storage.blob.models.ListBlobsOptions;
import java.util.concurrent.atomic.AtomicLong;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@OwnedBy(CE)
public class AzureStorageCleanup {
  private static final String AZURE_STORAGE_SUFFIX = "blob.core.windows.net";
  private static final String AZURE_STORAGE_URL_FORMAT = "https://%s.%s";

  @Autowired BatchMainConfig configuration;

  public boolean delete(String accountId, DataDeletionRecord dataDeletionRecord, boolean dryRun) {
    BlobContainerClient blobContainerClient = getBlobContainerClient();
    long deletedCount = listBlobsAndDelete(blobContainerClient, accountId, dryRun);
    dataDeletionRecord.getRecords().get(AZURE_STORAGE.name()).setRecordsCount(deletedCount);
    return true;
  }

  private BlobContainerClient getBlobContainerClient() {
    AzureStorageSyncConfig azureStorageSyncConfig = configuration.getAzureStorageSyncConfig();
    String storageAccountName = azureStorageSyncConfig.getAzureStorageAccountName();
    String sasToken = azureStorageSyncConfig.getAzureSasToken();
    String containerName = azureStorageSyncConfig.getAzureStorageContainerName();

    String endpoint = String.format(AZURE_STORAGE_URL_FORMAT, storageAccountName, AZURE_STORAGE_SUFFIX);
    log.info(endpoint);

    BlobServiceClient blobServiceClient =
        new BlobServiceClientBuilder().endpoint(endpoint).sasToken(sasToken).buildClient();
    return blobServiceClient.getBlobContainerClient(containerName);
  }

  private long listBlobsAndDelete(BlobContainerClient blobContainerClient, String prefix, boolean dryRun) {
    ListBlobsOptions options = new ListBlobsOptions().setPrefix(prefix).setMaxResultsPerPage(50).setDetails(
        new BlobListDetails().setRetrieveDeletedBlobs(false));
    AtomicLong deletedCount = new AtomicLong(0L);
    int i = 0;
    Iterable<PagedResponse<BlobItem>> blobPages = blobContainerClient.listBlobs(options, null).iterableByPage();
    for (PagedResponse<BlobItem> page : blobPages) {
      log.info("Page {}", ++i);
      page.getElements().forEach(blob -> {
        log.info("Name: {}, Is deleted? {}", blob.getName(), blob.isDeleted());
        if (!dryRun) {
          BlobClient blobClient = blobContainerClient.getBlobClient(blob.getName());
          blobClient.delete();
          log.info("Deleted blob: {}", blob.getName());
        }
        deletedCount.getAndIncrement();
      });
    }
    return deletedCount.get();
  }
}
