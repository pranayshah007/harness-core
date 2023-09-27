/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.helm;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import static java.lang.String.format;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.beans.DecryptableEntity;
import io.harness.data.structure.EmptyPredicate;
import io.harness.delegate.beans.connector.helm.OciHelmAuthType;
import io.harness.delegate.beans.connector.helm.OciHelmConnectorDTO;
import io.harness.delegate.beans.connector.helm.OciHelmDockerApiListTagsTaskParams;
import io.harness.delegate.beans.connector.helm.OciHelmUsernamePasswordDTO;
import io.harness.docker.DockerApiTagsListResponse;
import io.harness.exception.OciHelmDockerApiException;
import io.harness.exception.sanitizer.ExceptionMessageSanitizer;
import io.harness.security.encryption.SecretDecryptionService;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import okhttp3.Credentials;
import retrofit2.Call;
import retrofit2.Response;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_K8S})
@Slf4j
@Singleton
@OwnedBy(CDP)
public class OciHelmDockerApiHelper {
  @Inject private SecretDecryptionService decryptionService;
  @Inject private OciHelmApiHelperUtils ociHelmApiHelperUtils;

  public List<String> getChartVersions(
      String accountId, OciHelmDockerApiListTagsTaskParams ociHelmDockerApiListTagsTaskParams, int pageSize) {
    OciHelmConnectorDTO ociHelmConnector = ociHelmDockerApiListTagsTaskParams.getOciHelmConnector();
    log.info("Retrieving OCI Helm chart versions for account {} repo {}", accountId, ociHelmConnector.getHelmRepoUrl());

    if (EmptyPredicate.isEmpty(ociHelmDockerApiListTagsTaskParams.getChartName())) {
      throw new OciHelmDockerApiException("Chart name property is invalid");
    }

    decryptEncryptedDetails(ociHelmDockerApiListTagsTaskParams);
    String repoUrl = ociHelmConnector.getHelmRepoUrl();
    String chartNameNormalized =
        ociHelmApiHelperUtils.normalizeFieldData(ociHelmDockerApiListTagsTaskParams.getChartName());

    String credentials = null;
    OciHelmDockerApiRestClient ociHelmDockerApiRestClient;
    try {
      //  if repo url contains path info extract it and add it to the chartName
      chartNameNormalized = ociHelmApiHelperUtils.formatChartNameWithUrlPath(repoUrl, chartNameNormalized);

      ociHelmDockerApiRestClient = ociHelmApiHelperUtils.getRestClient(repoUrl);
    } catch (URISyntaxException e) {
      throw new OciHelmDockerApiException(
          format("URL provided in OCI Helm connector is invalid. %s", e.getMessage()), e);
    }
    if (ociHelmConnector.getAuth().getAuthType().getDisplayName().equalsIgnoreCase(
            OciHelmAuthType.USER_PASSWORD.getDisplayName())) {
      OciHelmUsernamePasswordDTO ociHelmUsernamePasswordDTO =
          (OciHelmUsernamePasswordDTO) ociHelmConnector.getAuth().getCredentials();
      credentials = Credentials.basic(ociHelmUsernamePasswordDTO.getUsername(),
          String.valueOf(ociHelmUsernamePasswordDTO.getPasswordRef().getDecryptedValue()));
    }

    String lastTag = EmptyPredicate.isNotEmpty(ociHelmDockerApiListTagsTaskParams.getLastTag())
        ? ociHelmDockerApiListTagsTaskParams.getLastTag()
        : null;

    log.info(format("Making a request to retrieve OCI Helm list of tags for %s, returning max %d results %s",
        chartNameNormalized, pageSize,
        EmptyPredicate.isEmpty(lastTag) ? " starting from beginning" : format(" continuing from tag %s", lastTag)));

    Call call;
    if (EmptyPredicate.isNotEmpty(credentials)) {
      call = ociHelmDockerApiRestClient.getTagsList(credentials, chartNameNormalized, pageSize, lastTag);
    } else {
      call = ociHelmDockerApiRestClient.getTagsListAsAnonymous(chartNameNormalized, pageSize, lastTag);
    }

    try {
      Response<DockerApiTagsListResponse> response = call.execute();
      if (response.isSuccessful()) {
        List<String> tags = response.body().getTags();
        log.info("Successfully retrieved OCI Helm chart versions for account {} repo {} chart {}. Versions: {}",
            accountId, ociHelmConnector.getHelmRepoUrl(), chartNameNormalized, tags);
        return tags;
      }
      throw new OciHelmDockerApiException(format("Failed to query chart versions. Response code [%d]. %s",
          response.code(), response.errorBody() != null ? response.errorBody().string() : ""));
    } catch (IOException ioException) {
      throw new OciHelmDockerApiException(
          format("Failed to query chart versions. %s", ioException.getMessage()), ioException);
    }
  }

  private void decryptEncryptedDetails(OciHelmDockerApiListTagsTaskParams ociHelmDockerApiListTagsTaskParams) {
    final List<DecryptableEntity> decryptableEntityList =
        ociHelmDockerApiListTagsTaskParams.getOciHelmConnector().getDecryptableEntities();
    if (decryptableEntityList == null) {
      return;
    }
    for (DecryptableEntity entity : decryptableEntityList) {
      decryptionService.decrypt(entity, ociHelmDockerApiListTagsTaskParams.getEncryptionDetails());
      ExceptionMessageSanitizer.storeAllSecretsForSanitizing(
          entity, ociHelmDockerApiListTagsTaskParams.getEncryptionDetails());
    }
  }
}