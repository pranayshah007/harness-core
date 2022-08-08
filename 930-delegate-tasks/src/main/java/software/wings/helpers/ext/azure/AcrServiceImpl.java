/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.helpers.ext.azure;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.eraro.ErrorCode.INVALID_ARGUMENT;
import static io.harness.exception.WingsException.USER;

import static software.wings.helpers.ext.jenkins.BuildDetails.Builder.aBuildDetails;

import static java.util.stream.Collectors.toList;

import io.harness.annotations.dev.OwnedBy;
import io.harness.exception.ExceptionUtils;
import io.harness.exception.InvalidArtifactServerException;
import io.harness.exception.WingsException;
import io.harness.security.encryption.EncryptedDataDetail;

import software.wings.beans.AzureConfig;
import software.wings.beans.artifact.ArtifactMetadataKeys;
import software.wings.beans.artifact.ArtifactStreamAttributes;
import software.wings.helpers.ext.jenkins.BuildDetails;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

@OwnedBy(CDC)
@Singleton
@Slf4j
public class AcrServiceImpl implements AcrService {
  private AzureDelegateHelperService azureDelegateHelperService;

  @Inject
  public AcrServiceImpl(AzureDelegateHelperService azureDelegateHelperService) {
    this.azureDelegateHelperService = azureDelegateHelperService;
  }

  @Override
  public List<String> listRegistries(AzureConfig config, String subscriptionId) {
    try {
      return azureDelegateHelperService.listContainerRegistryNames(config, subscriptionId);
    } catch (Exception e) {
      throw new InvalidArtifactServerException(ExceptionUtils.getMessage(e), USER);
    }
  }

  @Override
  public List<BuildDetails> getBuilds(AzureConfig config, List<EncryptedDataDetail> encryptionDetails,
      ArtifactStreamAttributes artifactStreamAttributes, int maxNumberOfBuilds) {
    try {
      String loginServer = StringUtils.isNotEmpty(artifactStreamAttributes.getRegistryHostName())
          ? artifactStreamAttributes.getRegistryHostName()
          : azureDelegateHelperService.getLoginServerForRegistry(config, encryptionDetails,
              artifactStreamAttributes.getSubscriptionId(), artifactStreamAttributes.getRegistryName());

      String repository = loginServer + "/" + artifactStreamAttributes.getRepositoryName();

      List<BuildDetails> buildDetails =
          azureDelegateHelperService
              .listRepositoryTags(config, encryptionDetails, loginServer, artifactStreamAttributes.getRepositoryName())
              .stream()
              .map(tag -> {
                Map<String, String> metadata = new HashMap();
                metadata.put(ArtifactMetadataKeys.image, repository + ":" + tag);
                metadata.put(ArtifactMetadataKeys.tag, tag);
                return aBuildDetails().withNumber(tag).withMetadata(metadata).withUiDisplayName("Tag# " + tag).build();
              })
              .collect(toList());
      // Reverisng the sorting order to save in DB in correct order
      Collections.reverse(buildDetails);
      return buildDetails;
    } catch (Exception e) {
      throw new InvalidArtifactServerException(ExceptionUtils.getMessage(e), USER);
    }
  }

  @Override
  public BuildDetails getLastSuccessfulBuild(
      AzureConfig config, List<EncryptedDataDetail> encryptionDetails, String imageName) {
    return null;
  }

  @Override
  public boolean verifyImageName(AzureConfig config, List<EncryptedDataDetail> encryptionDetails,
      ArtifactStreamAttributes artifactStreamAttributes) {
    if (!azureDelegateHelperService.isValidSubscription(config, artifactStreamAttributes.getSubscriptionId())) {
      log.info(
          "SubscriptionId [" + artifactStreamAttributes.getSubscriptionId() + "] does not exist in Azure account.");
      throw new WingsException(INVALID_ARGUMENT, USER)
          .addParam("args",
              "SubscriptionId [" + artifactStreamAttributes.getSubscriptionId() + "] does not exist in Azure account.");
    }

    if (!azureDelegateHelperService.isValidContainerRegistry(
            config, artifactStreamAttributes.getSubscriptionId(), artifactStreamAttributes.getRegistryName())) {
      log.info("Registry [" + artifactStreamAttributes.getRegistryName() + "] does not exist in Azure subscription.");
      throw new WingsException(INVALID_ARGUMENT, USER)
          .addParam("args",
              "Registry [" + artifactStreamAttributes.getRegistryName() + "] does not exist in Azure subscription.");
    }

    if (!azureDelegateHelperService
             .listRepositories(
                 config, artifactStreamAttributes.getSubscriptionId(), artifactStreamAttributes.getRegistryName())
             .contains(artifactStreamAttributes.getRepositoryName())) {
      log.info("Repository [" + artifactStreamAttributes.getRepositoryName() + "] does not exist in Azure Registry.");
      throw new WingsException(INVALID_ARGUMENT, USER)
          .addParam("args",
              "Repository [" + artifactStreamAttributes.getRepositoryName() + "] does not exist in Azure Registry.");
    }

    return true;
  }

  @Override
  public boolean validateCredentials(AzureConfig config, List<EncryptedDataDetail> encryptionDetails,
      ArtifactStreamAttributes artifactStreamAttributes) {
    azureDelegateHelperService.listRepositoryTags(config, encryptionDetails,
        artifactStreamAttributes.getSubscriptionId(), artifactStreamAttributes.getRegistryName(),
        artifactStreamAttributes.getRepositoryName());
    return true;
  }
}
