/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.azure.artifact;

import static java.lang.String.format;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.aws.beans.AwsInternalConfig;
import io.harness.azure.model.AzureAppServiceApplicationSetting;
import io.harness.delegate.beans.connector.awsconnector.AwsConnectorDTO;
import io.harness.delegate.beans.connector.awsconnector.AwsCredentialType;
import io.harness.delegate.task.aws.AwsNgConfigMapper;
import io.harness.exception.InvalidRequestException;
import io.harness.security.encryption.EncryptedDataDetail;
import io.harness.security.encryption.SecretDecryptionService;

import software.wings.service.impl.delegate.AwsEcrApiHelperServiceDelegate;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.Base64;
import java.util.List;
import java.util.Map;

@Singleton
@OwnedBy(HarnessTeam.CDP)
public class AzureElasticContainerRegistrySettingsProvider extends AbstractAzureRegistrySettingsProvider {
  @Inject private AwsNgConfigMapper awsNgConfigMapper;
  @Inject private AwsEcrApiHelperServiceDelegate awsEcrApiHelperServiceDelegate;
  @Inject private SecretDecryptionService secretDecryptionService;
  private static final String ECR_USERNAME = "AWS";

  @Override
  public Map<String, AzureAppServiceApplicationSetting> getContainerSettings(
      AzureContainerArtifactConfig artifactConfig) {
    AwsConnectorDTO awsConnectorDTO = (AwsConnectorDTO) artifactConfig.getConnectorConfig();

    if (AwsCredentialType.MANUAL_CREDENTIALS != awsConnectorDTO.getCredential().getAwsCredentialType()) {
      throw new InvalidRequestException(format("Invalid credentials type, %s are not supported",
          awsConnectorDTO.getCredential().getAwsCredentialType().toString()));
    }
    String region = artifactConfig.getRegion();
    AwsInternalConfig awsInternalConfig =
        getAwsInternalConfig(awsConnectorDTO, region, artifactConfig.getEncryptedDataDetails());
    String registryUrl = getRegistryUrl(artifactConfig.getImage());
    String password = getEcrPassword(awsInternalConfig, region, artifactConfig.getImage());

    validateSettings(artifactConfig, registryUrl, ECR_USERNAME, password);
    return populateDockerSettingMap(registryUrl, ECR_USERNAME, password);
  }

  private AwsInternalConfig getAwsInternalConfig(
      AwsConnectorDTO awsConnectorDTO, String region, List<EncryptedDataDetail> encryptedDataDetails) {
    secretDecryptionService.decrypt(awsConnectorDTO.getDecryptableEntities().get(0), encryptedDataDetails);
    AwsInternalConfig awsInternalConfig = awsNgConfigMapper.createAwsInternalConfig(awsConnectorDTO);
    awsInternalConfig.setDefaultRegion(region);
    return awsInternalConfig;
  }

  private String getRegistryUrl(String imagePath) {
    int indexTagPrefix = imagePath.lastIndexOf(':');
    String imageUrlWithoutTag = imagePath.substring(0, indexTagPrefix);
    return imageUrlToRegistryUrl(imageUrlWithoutTag);
  }

  private String imageUrlToRegistryUrl(String imageUrl) {
    String fullImageUrl = "https://" + imageUrl + (imageUrl.endsWith("/") ? "" : "/");
    fullImageUrl = fullImageUrl.substring(0, fullImageUrl.length() - 1);
    int index = fullImageUrl.lastIndexOf('/');
    return fullImageUrl.substring(0, index + 1);
  }

  private String getEcrPassword(AwsInternalConfig awsInternalConfig, String region, String ecrImageUrl) {
    String authToken = awsEcrApiHelperServiceDelegate.getAmazonEcrAuthToken(
        awsInternalConfig, ecrImageUrl.substring(0, ecrImageUrl.indexOf('.')), region);

    String decoded = new String(Base64.getDecoder().decode(authToken));
    return decoded.split(":")[1];
  }
}
