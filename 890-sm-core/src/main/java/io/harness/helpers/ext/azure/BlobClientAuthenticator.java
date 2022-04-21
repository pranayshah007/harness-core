package io.harness.helpers.ext.azure;

import static io.harness.annotations.dev.HarnessTeam.PL;

import io.harness.annotations.dev.OwnedBy;

import software.wings.beans.AzureBlobConfig;

import com.azure.identity.ClientSecretCredential;
import com.azure.identity.ClientSecretCredentialBuilder;
import com.azure.storage.blob.BlobClient;
import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.BlobContainerClientBuilder;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(PL)
@UtilityClass
@Slf4j
public class BlobClientAuthenticator {
  public static BlobClient getClient(AzureBlobConfig azureBlobConfig, String blobName) {
    ClientSecretCredential credentials = new ClientSecretCredentialBuilder()
                                             .clientId(azureBlobConfig.getClientId())
                                             .tenantId(azureBlobConfig.getTenantId())
                                             .clientSecret(azureBlobConfig.getSecretKey())
                                             .build();
    BlobContainerClient blobContainerClient = new BlobContainerClientBuilder()
                                                  .endpoint(azureBlobConfig.getContainerURL())
                                                  .credential(credentials)
                                                  .buildClient();
    return blobContainerClient.getBlobClient(blobName);
  }
}
