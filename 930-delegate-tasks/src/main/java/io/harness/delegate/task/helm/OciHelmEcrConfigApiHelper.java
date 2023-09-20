/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.helm;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import static java.lang.String.format;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.aws.AwsClient;
import io.harness.aws.AwsConfig;
import io.harness.aws.beans.AwsInternalConfig;
import io.harness.beans.DecryptableEntity;
import io.harness.data.structure.EmptyPredicate;
import io.harness.delegate.beans.connector.awsconnector.AwsConnectorDTO;
import io.harness.delegate.beans.connector.helm.EcrHelmApiListTagsTaskParams;
import io.harness.delegate.task.aws.AwsNgConfigMapper;
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
import retrofit2.Call;
import retrofit2.Response;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_K8S})
@Slf4j
@Singleton
@OwnedBy(CDP)
public class OciHelmEcrConfigApiHelper {
  @Inject private AwsNgConfigMapper awsNgConfigMapper;
  @Inject private AwsClient awsClient;
  @Inject private OciHelmApiHelperUtils ociHelmApiHelperUtils;
  @Inject private SecretDecryptionService decryptionService;
  private static final String DOT_DELIMITER = "\\.";
  private static final String BASIC = "Basic ";

  public List<String> getChartVersions(
      String accountId, EcrHelmApiListTagsTaskParams ecrHelmApiListTagsTaskParams, int pageSize) {
    AwsConnectorDTO awsConnectorDTO = ecrHelmApiListTagsTaskParams.getAwsConnectorDTO();
    String chartNameNormalized = ociHelmApiHelperUtils.normalizeFieldData(ecrHelmApiListTagsTaskParams.getChartName());

    if (EmptyPredicate.isEmpty(ecrHelmApiListTagsTaskParams.getChartName())) {
      throw new OciHelmDockerApiException("Chart name property is invalid");
    }

    decryptEncryptedDetails(ecrHelmApiListTagsTaskParams);

    OciHelmDockerApiRestClient ociHelmDockerApiRestClient;
    String baseUrl;
    try {
      baseUrl = getBaseUrl(ecrHelmApiListTagsTaskParams, awsConnectorDTO, accountId, chartNameNormalized);
      ociHelmDockerApiRestClient = ociHelmApiHelperUtils.getRestClient(baseUrl);
    } catch (URISyntaxException e) {
      throw new OciHelmDockerApiException(
          format("URL provided in OCI Helm connector is invalid. %s", e.getMessage()), e);
    }

    String credentials = getEcrRepositoryLoginCredentials(ecrHelmApiListTagsTaskParams, awsConnectorDTO, baseUrl);
    String lastTag =
        isNotEmpty(ecrHelmApiListTagsTaskParams.getLastTag()) ? ecrHelmApiListTagsTaskParams.getLastTag() : null;
    log.info(format("Making a request to retrieve OCI Helm list of tags for %s, returning max %d results %s",
        chartNameNormalized, pageSize,
        EmptyPredicate.isEmpty(lastTag) ? " starting from beginning" : format(" continuing from tag %s", lastTag)));

    Call call = ociHelmDockerApiRestClient.getTagsList(credentials, chartNameNormalized, pageSize, lastTag);
    try {
      Response<DockerApiTagsListResponse> response = call.execute();
      if (response.isSuccessful()) {
        List<String> tags = response.body().getTags();
        log.info("Successfully retrieved OCI Helm chart versions for account {} repo {} chart {}. Versions: {}",
            accountId, baseUrl, chartNameNormalized, tags);
        return tags;
      }
      throw new OciHelmDockerApiException(format("Failed to query chart versions. Response code [%d]. %s",
          response.code(), response.errorBody() != null ? response.errorBody().string() : ""));
    } catch (IOException ioException) {
      throw new OciHelmDockerApiException(
          format("Failed to query chart versions. %s", ioException.getMessage()), ioException);
    }
  }

  private String getEcrRepositoryLoginCredentials(
      EcrHelmApiListTagsTaskParams ecrHelmApiListTagsTaskParams, AwsConnectorDTO awsConnectorDTO, String baseUrl) {
    AwsConfig awsConfig = awsNgConfigMapper.mapAwsConfigWithDecryption(awsConnectorDTO.getCredential(),
        awsConnectorDTO.getCredential().getAwsCredentialType(), ecrHelmApiListTagsTaskParams.getEncryptionDetails());
    String account = baseUrl.split(DOT_DELIMITER)[0];
    return BASIC + awsClient.getAmazonEcrAuthToken(awsConfig, account, ecrHelmApiListTagsTaskParams.getRegion());
  }

  private String getBaseUrl(EcrHelmApiListTagsTaskParams ecrHelmApiListTagsTaskParams, AwsConnectorDTO awsConnectorDTO,
      String accountId, String chartNameNormalized) throws URISyntaxException {
    AwsInternalConfig awsInternalConfig = awsNgConfigMapper.createAwsInternalConfig(awsConnectorDTO);
    String baseUrl = awsClient.getEcrImageUrl(awsInternalConfig, ecrHelmApiListTagsTaskParams.getRegistryId(),
        ecrHelmApiListTagsTaskParams.getRegion(), chartNameNormalized);
    log.info("Retrieving OCI Helm chart versions for account {} repo {}", accountId, baseUrl);
    if (isNotEmpty(baseUrl) && baseUrl.charAt(baseUrl.length() - 1) != '/') {
      baseUrl += "/";
    }
    return baseUrl;
  }

  private void decryptEncryptedDetails(EcrHelmApiListTagsTaskParams ecrHelmApiListTagsTaskParams) {
    final List<DecryptableEntity> decryptableEntityList =
        ecrHelmApiListTagsTaskParams.getAwsConnectorDTO().getDecryptableEntities();
    if (decryptableEntityList == null) {
      return;
    }
    for (DecryptableEntity entity : decryptableEntityList) {
      decryptionService.decrypt(entity, ecrHelmApiListTagsTaskParams.getEncryptionDetails());
      ExceptionMessageSanitizer.storeAllSecretsForSanitizing(
          entity, ecrHelmApiListTagsTaskParams.getEncryptionDetails());
    }
  }
}
