/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.execution;

import static java.util.Collections.emptyList;

import io.harness.beans.steps.CIStepInfoType;
import io.harness.ci.beans.entities.CIExecutionConfig;
import io.harness.ci.beans.entities.CIExecutionImages;
import io.harness.ci.config.CIExecutionServiceConfig;
import io.harness.ci.config.CIStepConfig;
import io.harness.ci.config.StepImageConfig;
import io.harness.repositories.CIExecutionConfigRepository;

import com.google.inject.Inject;
import de.skuzzle.semantic.Version;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class CIExecutionConfigService {
  @Inject CIExecutionConfigRepository configRepository;
  @Inject CIExecutionServiceConfig ciExecutionServiceConfig;

  public CIExecutionServiceConfig getCiExecutionServiceConfig() {
    return ciExecutionServiceConfig;
  }

  public Boolean updateCIContainerTag(String accountId, CIExecutionImages ciExecutionImages) {
    CIExecutionConfig executionConfig;
    Optional<CIExecutionConfig> existingConfig = configRepository.findFirstByAccountIdentifier(accountId);
    if (existingConfig.isPresent()) {
      executionConfig = existingConfig.get();
    } else {
      executionConfig = CIExecutionConfig.builder().accountIdentifier(accountId).build();
    }
    executionConfig.setGitCloneImage(ciExecutionImages.getGitCloneTag());
    executionConfig.setAddOnImage(ciExecutionImages.getAddonTag());
    executionConfig.setLiteEngineImage(ciExecutionImages.getLiteEngineTag());
    executionConfig.setArtifactoryUploadTag(ciExecutionImages.getArtifactoryUploadTag());
    executionConfig.setBuildAndPushDockerRegistryImage(ciExecutionImages.getBuildAndPushDockerRegistryTag());
    executionConfig.setCacheGCSTag(ciExecutionImages.getCacheGCSTag());
    executionConfig.setCacheS3Tag(ciExecutionImages.getCacheS3Tag());
    executionConfig.setBuildAndPushECRImage(ciExecutionImages.getBuildAndPushECRTag());
    executionConfig.setBuildAndPushGCRImage(ciExecutionImages.getBuildAndPushGCRTag());
    executionConfig.setGcsUploadImage(ciExecutionImages.getGcsUploadTag());
    executionConfig.setS3UploadImage(ciExecutionImages.getS3UploadTag());
    executionConfig.setSecurityImage(ciExecutionImages.getSecurityTag());
    configRepository.save(executionConfig);
    return true;
  }

  public String getAddonImage(String accountId) {
    Optional<CIExecutionConfig> configOptional = configRepository.findFirstByAccountIdentifier(accountId);
    String image;
    if (configOptional.isPresent()) {
      image = configOptional.get().getAddOnImage();
    } else {
      image = ciExecutionServiceConfig.getAddonImage();
    }
    return image;
  }

  public String getLiteEngineImage(String accountId) {
    Optional<CIExecutionConfig> configOptional = configRepository.findFirstByAccountIdentifier(accountId);
    String image;
    if (configOptional.isPresent()) {
      image = configOptional.get().getLiteEngineImage();
    } else {
      image = ciExecutionServiceConfig.getLiteEngineImage();
    }
    return image;
  }

  public CIExecutionImages getCurrentConfig(String accountId) {
    Optional<CIExecutionConfig> existingConfig = configRepository.findFirstByAccountIdentifier(accountId);
    if (existingConfig.isPresent()) {
      CIExecutionConfig config = existingConfig.get();
      return CIExecutionImages.builder()
          .buildAndPushDockerRegistryTag(config.getBuildAndPushDockerRegistryImage())
          .addonTag(config.getAddOnImage())
          .liteEngineTag(config.getLiteEngineImage())
          .gitCloneTag(config.getGitCloneImage())
          .buildAndPushECRTag(config.getBuildAndPushECRImage())
          .buildAndPushGCRTag(config.getBuildAndPushGCRImage())
          .gcsUploadTag(config.getGcsUploadImage())
          .s3UploadTag(config.getS3UploadImage())
          .artifactoryUploadTag(config.getArtifactoryUploadTag())
          .cacheGCSTag(config.getCacheGCSTag())
          .cacheS3Tag(config.getCacheS3Tag())
          .securityTag(config.getSecurityImage())
          .build();
    } else {
      return CIExecutionImages.builder().build();
    }
  }

  public CIExecutionImages getDefaultConfig() {
    CIStepConfig config = ciExecutionServiceConfig.getStepConfig();
    return CIExecutionImages.builder()
        .buildAndPushDockerRegistryTag(config.getBuildAndPushDockerRegistryConfig().getImage())
        .addonTag(ciExecutionServiceConfig.getAddonImage())
        .liteEngineTag(ciExecutionServiceConfig.getLiteEngineImage())
        .gitCloneTag(config.getGitCloneConfig().getImage())
        .buildAndPushECRTag(config.getBuildAndPushECRConfig().getImage())
        .buildAndPushGCRTag(config.getBuildAndPushGCRConfig().getImage())
        .gcsUploadTag(config.getGcsUploadConfig().getImage())
        .s3UploadTag(config.getS3UploadConfig().getImage())
        .artifactoryUploadTag(config.getArtifactoryUploadConfig().getImage())
        .cacheGCSTag(config.getCacheGCSConfig().getImage())
        .cacheS3Tag(config.getCacheS3Config().getImage())
        .securityTag(config.getSecurityConfig().getImage())
        .build();
  }

  public List<DeprecatedImageInfo> getDeprecatedTags(String accountId) {
    Optional<CIExecutionConfig> configOptional = configRepository.findFirstByAccountIdentifier(accountId);
    List<DeprecatedImageInfo> deprecatedTags = new ArrayList();
    if (configOptional.isPresent()) {
      CIExecutionConfig ciExecutionConfig = configOptional.get();
      if (!checkForCIImage(ciExecutionServiceConfig.getAddonImage(), ciExecutionConfig.getAddOnImage())) {
        deprecatedTags.add(
            DeprecatedImageInfo.builder().tag("AddonImage").version(ciExecutionConfig.getAddOnImage()).build());
      }
      if (!checkForCIImage(ciExecutionServiceConfig.getLiteEngineImage(), ciExecutionConfig.getLiteEngineImage())) {
        deprecatedTags.add(DeprecatedImageInfo.builder()
                               .tag("LiteEngineImage")
                               .version(ciExecutionConfig.getLiteEngineImage())
                               .build());
      }
    }
    return deprecatedTags;
  }

  private boolean checkForCIImage(String defaultImage, String customImage) {
    String defaultImageTag = defaultImage.split(":")[1];
    String customImageTag = customImage.split(":")[1];
    Version defaultVersion = Version.parseVersion(defaultImageTag);
    Version customVersion = Version.parseVersion(customImageTag);
    // we are supporting 2 back versions
    return defaultVersion.isLowerThanOrEqualTo(customVersion.nextMinor().nextMinor());
  }

  public StepImageConfig getPluginVersion(CIStepInfoType stepInfoType, String accountId) {
    Optional<CIExecutionConfig> existingConfig = configRepository.findFirstByAccountIdentifier(accountId);
    StepImageConfig stepImageConfig = getStepImageConfig(stepInfoType, ciExecutionServiceConfig);
    String image = stepImageConfig.getImage();
    switch (stepInfoType) {
      case DOCKER:
        if (existingConfig.isPresent()) {
          image = existingConfig.get().getBuildAndPushDockerRegistryImage();
        }
        break;
      case GCR:
        if (existingConfig.isPresent()) {
          image = existingConfig.get().getBuildAndPushGCRImage();
        }
        break;
      case ECR:
        if (existingConfig.isPresent()) {
          image = existingConfig.get().getBuildAndPushECRImage();
        }
        break;
      case RESTORE_CACHE_S3:
      case SAVE_CACHE_S3:
        if (existingConfig.isPresent()) {
          image = existingConfig.get().getCacheS3Tag();
        }
        break;
      case UPLOAD_S3:
        if (existingConfig.isPresent()) {
          image = existingConfig.get().getS3UploadImage();
        }
        break;
      case UPLOAD_GCS:
        if (existingConfig.isPresent()) {
          image = existingConfig.get().getGcsUploadImage();
        }
        break;
      case SAVE_CACHE_GCS:
      case RESTORE_CACHE_GCS:
        if (existingConfig.isPresent()) {
          image = existingConfig.get().getCacheGCSTag();
        }
        break;
      case SECURITY:
        if (existingConfig.isPresent()) {
          image = existingConfig.get().getSecurityImage();
        }
        break;
      case UPLOAD_ARTIFACTORY:
        if (existingConfig.isPresent()) {
          image = existingConfig.get().getArtifactoryUploadTag();
        }
        break;
      case GIT_CLONE:
        if (existingConfig.isPresent()) {
          image = existingConfig.get().getGitCloneImage();
        }
        break;
      default:
        throw new IllegalStateException("Unexpected value: " + stepInfoType);
    }
    return StepImageConfig.builder()
        .entrypoint(stepImageConfig.getEntrypoint())
        .windowsEntrypoint(Optional.ofNullable(stepImageConfig.getWindowsEntrypoint()).orElse(emptyList()))
        .image(image)
        .build();
  }

  private static StepImageConfig getStepImageConfig(
      CIStepInfoType stepInfoType, CIExecutionServiceConfig ciExecutionServiceConfig) {
    switch (stepInfoType) {
      case DOCKER:
        return ciExecutionServiceConfig.getStepConfig().getBuildAndPushDockerRegistryConfig();
      case GCR:
        return ciExecutionServiceConfig.getStepConfig().getBuildAndPushGCRConfig();
      case ECR:
        return ciExecutionServiceConfig.getStepConfig().getBuildAndPushECRConfig();
      case RESTORE_CACHE_S3:
      case SAVE_CACHE_S3:
        return ciExecutionServiceConfig.getStepConfig().getCacheS3Config();
      case UPLOAD_S3:
        return ciExecutionServiceConfig.getStepConfig().getS3UploadConfig();
      case UPLOAD_GCS:
        return ciExecutionServiceConfig.getStepConfig().getGcsUploadConfig();
      case SAVE_CACHE_GCS:
      case RESTORE_CACHE_GCS:
        return ciExecutionServiceConfig.getStepConfig().getCacheGCSConfig();
      case SECURITY:
        return ciExecutionServiceConfig.getStepConfig().getSecurityConfig();
      case UPLOAD_ARTIFACTORY:
        return ciExecutionServiceConfig.getStepConfig().getArtifactoryUploadConfig();
      case GIT_CLONE:
        return ciExecutionServiceConfig.getStepConfig().getGitCloneConfig();
      default:
        throw new IllegalStateException("Unexpected value: " + stepInfoType);
    }
  }

  public Boolean deleteCIExecutionConfig(String accountIdentifier) {
    Optional<CIExecutionConfig> executionConfig = configRepository.findFirstByAccountIdentifier(accountIdentifier);
    executionConfig.ifPresent(ciExecutionConfig -> configRepository.deleteById(ciExecutionConfig.getUuid()));
    return true;
  }
}
