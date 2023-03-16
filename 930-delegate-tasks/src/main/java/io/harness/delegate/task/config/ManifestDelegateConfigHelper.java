/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.task;

import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import io.harness.beans.DecryptableEntity;
import io.harness.connector.task.git.GitDecryptionHelper;
import io.harness.delegate.beans.connector.scm.adapter.ScmConnectorMapper;
import io.harness.delegate.beans.connector.scm.genericgitconnector.GitConfigDTO;
import io.harness.delegate.beans.storeconfig.GcsHelmStoreDelegateConfig;
import io.harness.delegate.beans.storeconfig.GitStoreDelegateConfig;
import io.harness.delegate.beans.storeconfig.HttpHelmStoreDelegateConfig;
import io.harness.delegate.beans.storeconfig.OciHelmStoreDelegateConfig;
import io.harness.delegate.beans.storeconfig.S3HelmStoreDelegateConfig;
import io.harness.delegate.beans.storeconfig.StoreDelegateConfig;
import io.harness.delegate.task.k8s.ManifestDelegateConfig;
import io.harness.exception.sanitizer.ExceptionMessageSanitizer;
import io.harness.security.encryption.SecretDecryptionService;

import com.google.inject.Inject;
import java.util.List;

public class ManifestDelegateConfigHelper {
  @Inject private GitDecryptionHelper gitDecryptionHelper;
  @Inject private SecretDecryptionService decryptionService;

  public void decryptManifestDelegateConfig(ManifestDelegateConfig manifestDelegateConfig) {
    if (manifestDelegateConfig == null) {
      return;
    }

    StoreDelegateConfig storeDelegateConfig = manifestDelegateConfig.getStoreDelegateConfig();
    switch (storeDelegateConfig.getType()) {
      case HARNESS:
        break;
      case CUSTOM_REMOTE:
        // TODO: do we need to decrypt anything here?
        break;
      case GIT:
        GitStoreDelegateConfig gitStoreDelegateConfig = (GitStoreDelegateConfig) storeDelegateConfig;
        GitConfigDTO gitConfigDTO = ScmConnectorMapper.toGitConfigDTO(gitStoreDelegateConfig.getGitConfigDTO());
        gitDecryptionHelper.decryptGitConfig(gitConfigDTO, gitStoreDelegateConfig.getEncryptedDataDetails());
        ExceptionMessageSanitizer.storeAllSecretsForSanitizing(
            gitConfigDTO, gitStoreDelegateConfig.getEncryptedDataDetails());
        break;

      case HTTP_HELM:
        HttpHelmStoreDelegateConfig httpHelmStoreConfig = (HttpHelmStoreDelegateConfig) storeDelegateConfig;
        for (DecryptableEntity entity : httpHelmStoreConfig.getHttpHelmConnector().getDecryptableEntities()) {
          decryptionService.decrypt(entity, httpHelmStoreConfig.getEncryptedDataDetails());
          ExceptionMessageSanitizer.storeAllSecretsForSanitizing(entity, httpHelmStoreConfig.getEncryptedDataDetails());
        }
        break;

      case OCI_HELM:
        OciHelmStoreDelegateConfig ociHelmStoreConfig = (OciHelmStoreDelegateConfig) storeDelegateConfig;
        for (DecryptableEntity entity : ociHelmStoreConfig.getOciHelmConnector().getDecryptableEntities()) {
          decryptionService.decrypt(entity, ociHelmStoreConfig.getEncryptedDataDetails());
          ExceptionMessageSanitizer.storeAllSecretsForSanitizing(entity, ociHelmStoreConfig.getEncryptedDataDetails());
        }
        break;

      case S3_HELM:
        S3HelmStoreDelegateConfig s3HelmStoreConfig = (S3HelmStoreDelegateConfig) storeDelegateConfig;
        List<DecryptableEntity> s3DecryptableEntityList = s3HelmStoreConfig.getAwsConnector().getDecryptableEntities();
        if (isNotEmpty(s3DecryptableEntityList)) {
          for (DecryptableEntity decryptableEntity : s3DecryptableEntityList) {
            decryptionService.decrypt(decryptableEntity, s3HelmStoreConfig.getEncryptedDataDetails());
            ExceptionMessageSanitizer.storeAllSecretsForSanitizing(
                decryptableEntity, s3HelmStoreConfig.getEncryptedDataDetails());
          }
        }
        break;

      case GCS_HELM:
        GcsHelmStoreDelegateConfig gcsHelmStoreDelegateConfig = (GcsHelmStoreDelegateConfig) storeDelegateConfig;
        List<DecryptableEntity> gcsDecryptableEntityList =
            gcsHelmStoreDelegateConfig.getGcpConnector().getDecryptableEntities();
        if (isNotEmpty(gcsDecryptableEntityList)) {
          for (DecryptableEntity decryptableEntity : gcsDecryptableEntityList) {
            decryptionService.decrypt(decryptableEntity, gcsHelmStoreDelegateConfig.getEncryptedDataDetails());
            ExceptionMessageSanitizer.storeAllSecretsForSanitizing(
                decryptableEntity, gcsHelmStoreDelegateConfig.getEncryptedDataDetails());
          }
        }
        break;

      default:
        throw new UnsupportedOperationException(
            String.format("Unsupported Manifest type: [%s]", manifestDelegateConfig.getManifestType().name()));
    }
  }
}
