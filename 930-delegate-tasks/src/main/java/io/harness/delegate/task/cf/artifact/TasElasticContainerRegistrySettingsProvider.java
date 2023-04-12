/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.cf.artifact;

import static java.lang.String.format;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.aws.beans.AwsInternalConfig;
import io.harness.connector.helper.DecryptionHelper;
import io.harness.delegate.beans.connector.awsconnector.AwsConnectorDTO;
import io.harness.delegate.beans.connector.awsconnector.AwsCredentialType;
import io.harness.delegate.task.aws.AwsNgConfigMapper;
import io.harness.delegate.task.pcf.artifact.TasContainerArtifactConfig;
import io.harness.exception.InvalidRequestException;
import io.harness.security.encryption.EncryptedDataDetail;
import io.harness.security.encryption.SecretDecryptionService;

import software.wings.service.impl.delegate.AwsEcrApiHelperServiceDelegate;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.List;

@Singleton
@OwnedBy(HarnessTeam.CDP)
public class TasElasticContainerRegistrySettingsProvider extends AbstractTasRegistrySettingsProvider {
  @Inject private AwsNgConfigMapper awsNgConfigMapper;
  @Inject private AwsEcrApiHelperServiceDelegate awsEcrApiHelperServiceDelegate;
  @Inject private SecretDecryptionService secretDecryptionService;
  private static final String ECR_USERNAME = "AWS";
  @Inject DecryptionHelper decryptionHelper;

  @Override
  public TasArtifactCreds getContainerSettings(
      TasContainerArtifactConfig artifactConfig, DecryptionHelper decryptionHelper) {
    AwsConnectorDTO awsConnectorDTO = (AwsConnectorDTO) artifactConfig.getConnectorConfig();

    if (AwsCredentialType.MANUAL_CREDENTIALS != awsConnectorDTO.getCredential().getAwsCredentialType()) {
      throw new InvalidRequestException(format("Invalid credentials type, %s are not supported",
          awsConnectorDTO.getCredential().getAwsCredentialType().toString()));
    }
    decryptEntity(decryptionHelper, awsConnectorDTO.getDecryptableEntities(), artifactConfig.getEncryptedDataDetails());

    String region = artifactConfig.getRegion();
    AwsInternalConfig awsInternalConfig =
        getAwsInternalConfig(awsConnectorDTO, region, artifactConfig.getEncryptedDataDetails());
    String registryUrl = getRegistryUrl(artifactConfig.getImage());
    String password = String.valueOf(awsInternalConfig.getSecretKey());

    validateSettings(artifactConfig, registryUrl, String.valueOf(awsInternalConfig.getAccessKey()), password);
    return populateDockerSettings(registryUrl, String.valueOf(awsInternalConfig.getAccessKey()), password);
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
}
