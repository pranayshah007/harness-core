/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.execution;

import static java.lang.String.format;
import static java.util.Collections.emptyList;

import io.harness.beans.plugin.compatible.PluginCompatibleStep;
import io.harness.beans.steps.CIStepInfoType;
import io.harness.beans.sweepingoutputs.StageInfraDetails.Type;
import io.harness.ci.beans.entities.CIExecutionConfig;
import io.harness.ci.beans.entities.CIExecutionImages;
import io.harness.ci.beans.entities.CIExecutionImages.CIExecutionImagesBuilder;
import io.harness.ci.buildstate.PluginSettingUtils;
import io.harness.ci.config.CIExecutionServiceConfig;
import io.harness.ci.config.CIStepConfig;
import io.harness.ci.config.Operation;
import io.harness.ci.config.PluginField;
import io.harness.ci.config.StepImageConfig;
import io.harness.ci.config.VmContainerlessStepConfig;
import io.harness.ci.config.VmImageConfig;
import io.harness.repositories.CIExecutionConfigRepository;

import com.google.inject.Inject;
import de.skuzzle.semantic.Version;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import javax.ws.rs.BadRequestException;
import org.apache.logging.log4j.util.Strings;

public class CIExecutionConfigService {
  @Inject CIExecutionConfigRepository configRepository;
  @Inject CIExecutionServiceConfig ciExecutionServiceConfig;
  @Inject private PluginSettingUtils pluginSettingUtils;

  private static final String UNEXPECTED_ERR_FORMAT = "Unexpected value: %s";
  public CIExecutionServiceConfig getCiExecutionServiceConfig() {
    return ciExecutionServiceConfig;
  }

  public Boolean updateCIContainerTags(String accountId, List<Operation> operations, Type infra) {
    CIExecutionConfig executionConfig;
    Optional<CIExecutionConfig> existingConfig = configRepository.findFirstByAccountIdentifier(accountId);
    if (existingConfig.isPresent()) {
      executionConfig = existingConfig.get();
    } else {
      VmImageConfig vmImageConfig = VmImageConfig.builder().build();
      executionConfig = CIExecutionConfig.builder().accountIdentifier(accountId).vmImageConfig(vmImageConfig).build();
    }

    for (Operation operation : operations) {
      set(operation.getField(), operation.getValue(), executionConfig, infra);
    }
    configRepository.save(executionConfig);
    return true;
  }

  public Boolean resetCIContainerTags(String accountId, List<Operation> operations, Type infra) {
    CIExecutionConfig executionConfig;
    Optional<CIExecutionConfig> existingConfig = configRepository.findFirstByAccountIdentifier(accountId);
    if (existingConfig.isPresent()) {
      executionConfig = existingConfig.get();
    } else {
      VmImageConfig vmImageConfig = VmImageConfig.builder().build();
      executionConfig = CIExecutionConfig.builder().accountIdentifier(accountId).vmImageConfig(vmImageConfig).build();
    }

    for (Operation operation : operations) {
      set(operation.getField(), null, executionConfig, infra);
    }
    configRepository.save(executionConfig);
    return true;
  }

  private void set(String field, String value, CIExecutionConfig executionConfig, Type infra) {
    if (infra == Type.VM) {
      setVMField(field, value, executionConfig);
    } else if (infra == Type.K8) {
      setK8Field(field, value, executionConfig);
    } else {
      throw new BadRequestException("Config not supported for infra type: " + infra);
    }
  }

  private void setK8Field(String field, String value, CIExecutionConfig executionConfig) {
    PluginField pluginField = PluginField.getPluginField(field);

    switch (pluginField) {
      case ADDON:
        executionConfig.setAddOnImage(value);
        break;
      case LITE_ENGINE:
        executionConfig.setLiteEngineImage(value);
        break;
      case GIT_CLONE:
        executionConfig.setGitCloneImage(value);
        break;
      case BUILD_PUSH_DOCKER_REGISTRY:
        executionConfig.setBuildAndPushDockerRegistryImage(value);
        break;
      case BUILD_PUSH_ECR:
        executionConfig.setBuildAndPushECRImage(value);
        break;
      case BUILD_PUSH_ACR:
        executionConfig.setBuildAndPushACRImage(value);
        break;
      case BUILD_PUSH_GCR:
        executionConfig.setBuildAndPushGCRImage(value);
        break;
      case GCS_UPLOAD:
        executionConfig.setGcsUploadImage(value);
        break;
      case S3_UPLOAD:
        executionConfig.setS3UploadImage(value);
        break;
      case ARTIFACTORY_UPLOAD:
        executionConfig.setArtifactoryUploadTag(value);
        break;
      case CACHE_GCS:
        executionConfig.setCacheGCSTag(value);
        break;
      case CACHE_S3:
        executionConfig.setCacheS3Tag(value);
        break;
      case SECURITY:
        executionConfig.setSecurityImage(value);
        break;
      case SSCA_ORCHESTRATION:
        executionConfig.setSscaOrchestrationTag(value);
        break;
      case SSCA_ENFORCEMENT:
        executionConfig.setSscaEnforcementTag(value);
        break;
      default:
        throw new BadRequestException(format("Field %s does not exist for infra type: K8", field));
    }
  }

  private void setVMField(String field, String value, CIExecutionConfig executionConfig) {
    PluginField pluginField = PluginField.getPluginField(field);

    VmImageConfig vmImageConfig = executionConfig.getVmImageConfig();
    if (vmImageConfig == null) {
      vmImageConfig = VmImageConfig.builder().build();
      executionConfig.setVmImageConfig(vmImageConfig);
    }

    switch (pluginField) {
      case GIT_CLONE:
        vmImageConfig.setGitClone(value);
        break;
      case BUILD_PUSH_DOCKER_REGISTRY:
        vmImageConfig.setBuildAndPushDockerRegistry(value);
        break;
      case BUILD_PUSH_ACR:
        vmImageConfig.setBuildAndPushACR(value);
        break;
      case BUILD_PUSH_ECR:
        vmImageConfig.setBuildAndPushECR(value);
        break;
      case BUILD_PUSH_GCR:
        vmImageConfig.setBuildAndPushGCR(value);
        break;
      case GCS_UPLOAD:
        vmImageConfig.setGcsUpload(value);
        break;
      case S3_UPLOAD:
        vmImageConfig.setS3Upload(value);
        break;
      case ARTIFACTORY_UPLOAD:
        vmImageConfig.setArtifactoryUpload(value);
        break;
      case CACHE_GCS:
        vmImageConfig.setCacheGCS(value);
        break;
      case CACHE_S3:
        vmImageConfig.setCacheS3(value);
        break;
      case SECURITY:
        vmImageConfig.setSecurity(value);
        break;
      default:
        throw new BadRequestException(format("Field %s does not exist for infra type: VM", field));
    }
  }

  public String getAddonImage(String accountId) {
    Optional<CIExecutionConfig> configOptional = configRepository.findFirstByAccountIdentifier(accountId);
    String image;
    if (configOptional.isPresent() && Strings.isNotBlank(configOptional.get().getAddOnImage())) {
      image = configOptional.get().getAddOnImage();
    } else {
      image = ciExecutionServiceConfig.getAddonImage();
    }
    return image;
  }

  public String getLiteEngineImage(String accountId) {
    Optional<CIExecutionConfig> configOptional = configRepository.findFirstByAccountIdentifier(accountId);
    String image;
    if (configOptional.isPresent() && Strings.isNotBlank(configOptional.get().getLiteEngineImage())) {
      image = configOptional.get().getLiteEngineImage();
    } else {
      image = ciExecutionServiceConfig.getLiteEngineImage();
    }
    return image;
  }

  public CIExecutionImages getCustomerConfig(String accountId, Type infra, boolean overridesOnly) {
    CIExecutionConfig overriddenConfig = null;
    Optional<CIExecutionConfig> existingConfig = configRepository.findFirstByAccountIdentifier(accountId);

    if (existingConfig.isPresent()) {
      overriddenConfig = existingConfig.get();
    }
    if (overridesOnly) {
      return mapConfig(overriddenConfig, infra);
    }
    CIExecutionImages defaultConfig = getDefaultConfig(infra);
    applyOverrides(defaultConfig, mapConfig(overriddenConfig, infra));
    return defaultConfig;
  }

  private void applyOverrides(CIExecutionImages defaultConfig, CIExecutionImages overriddenConfig) {
    if (Strings.isNotBlank(overriddenConfig.getAddonTag())) {
      defaultConfig.setAddonTag(overriddenConfig.getAddonTag());
    }
    if (Strings.isNotBlank(overriddenConfig.getLiteEngineTag())) {
      defaultConfig.setLiteEngineTag(overriddenConfig.getLiteEngineTag());
    }
    if (Strings.isNotBlank(overriddenConfig.getGitCloneTag())) {
      defaultConfig.setGitCloneTag(overriddenConfig.getGitCloneTag());
    }
    if (Strings.isNotBlank(overriddenConfig.getBuildAndPushDockerRegistryTag())) {
      defaultConfig.setBuildAndPushDockerRegistryTag(overriddenConfig.getBuildAndPushDockerRegistryTag());
    }
    if (Strings.isNotBlank(overriddenConfig.getBuildAndPushACRTag())) {
      defaultConfig.setBuildAndPushACRTag(overriddenConfig.getBuildAndPushACRTag());
    }
    if (Strings.isNotBlank(overriddenConfig.getBuildAndPushECRTag())) {
      defaultConfig.setBuildAndPushECRTag(overriddenConfig.getBuildAndPushECRTag());
    }
    if (Strings.isNotBlank(overriddenConfig.getBuildAndPushGCRTag())) {
      defaultConfig.setBuildAndPushGCRTag(overriddenConfig.getBuildAndPushGCRTag());
    }
    if (Strings.isNotBlank(overriddenConfig.getGcsUploadTag())) {
      defaultConfig.setGcsUploadTag(overriddenConfig.getGcsUploadTag());
    }
    if (Strings.isNotBlank(overriddenConfig.getS3UploadTag())) {
      defaultConfig.setS3UploadTag(overriddenConfig.getS3UploadTag());
    }
    if (Strings.isNotBlank(overriddenConfig.getArtifactoryUploadTag())) {
      defaultConfig.setArtifactoryUploadTag(overriddenConfig.getArtifactoryUploadTag());
    }
    if (Strings.isNotBlank(overriddenConfig.getCacheGCSTag())) {
      defaultConfig.setCacheGCSTag(overriddenConfig.getCacheGCSTag());
    }
    if (Strings.isNotBlank(overriddenConfig.getCacheS3Tag())) {
      defaultConfig.setCacheS3Tag(overriddenConfig.getCacheS3Tag());
    }
    if (Strings.isNotBlank(overriddenConfig.getSecurityTag())) {
      defaultConfig.setSecurityTag(overriddenConfig.getSecurityTag());
    }
  }

  private CIExecutionImages mapVMConfig(CIExecutionConfig config) {
    VmImageConfig vmImageConfig = config.getVmImageConfig();
    if (vmImageConfig == null) {
      return CIExecutionImages.builder().build();
    }

    return CIExecutionImages.builder()
        .buildAndPushDockerRegistryTag(vmImageConfig.getBuildAndPushDockerRegistry())
        .gitCloneTag(vmImageConfig.getGitClone())
        .buildAndPushECRTag(vmImageConfig.getBuildAndPushECR())
        .buildAndPushACRTag(vmImageConfig.getBuildAndPushACR())
        .buildAndPushGCRTag(vmImageConfig.getBuildAndPushGCR())
        .gcsUploadTag(vmImageConfig.getGcsUpload())
        .s3UploadTag(vmImageConfig.getS3Upload())
        .artifactoryUploadTag(vmImageConfig.getArtifactoryUpload())
        .cacheGCSTag(vmImageConfig.getCacheGCS())
        .cacheS3Tag(vmImageConfig.getCacheS3())
        .securityTag(vmImageConfig.getSecurity())
        .sscaOrchestrationTag(vmImageConfig.getSscaOrchestration())
        .sscaEnforcementTag(vmImageConfig.getSscaEnforcement())
        .build();
  }

  public CIExecutionImages getDefaultConfig(Type infra) {
    CIStepConfig config = ciExecutionServiceConfig.getStepConfig();
    return mapConfig(config, infra);
  }

  private CIExecutionImages mapConfig(CIStepConfig config, Type infra) {
    if (infra == Type.K8) {
      return mapK8Config(config);
    } else if (infra == Type.VM) {
      return mapVMConfig(config);
    } else {
      throw new BadRequestException("Config not supported for infra type: " + infra);
    }
  }

  private CIExecutionImages mapConfig(CIExecutionConfig config, Type infra) {
    if (config == null) {
      return CIExecutionImages.builder().build();
    }
    if (infra == Type.K8) {
      return mapK8Config(config);
    } else if (infra == Type.VM) {
      return mapVMConfig(config);
    } else {
      throw new BadRequestException("Config not supported for infra type: " + infra);
    }
  }

  private CIExecutionImages mapVMConfig(CIStepConfig config) {
    VmImageConfig vmImageConfig = config.getVmImageConfig();
    if (vmImageConfig == null) {
      return CIExecutionImages.builder().build();
    }
    return CIExecutionImages.builder()
        .buildAndPushDockerRegistryTag(vmImageConfig.getBuildAndPushDockerRegistry())
        .gitCloneTag(vmImageConfig.getGitClone())
        .buildAndPushECRTag(vmImageConfig.getBuildAndPushECR())
        .buildAndPushGCRTag(vmImageConfig.getBuildAndPushGCR())
        .buildAndPushACRTag(vmImageConfig.getBuildAndPushACR())
        .gcsUploadTag(vmImageConfig.getGcsUpload())
        .s3UploadTag(vmImageConfig.getS3Upload())
        .artifactoryUploadTag(vmImageConfig.getArtifactoryUpload())
        .cacheGCSTag(vmImageConfig.getCacheGCS())
        .cacheS3Tag(vmImageConfig.getCacheS3())
        .securityTag(vmImageConfig.getSecurity())
        .sscaOrchestrationTag(vmImageConfig.getSscaOrchestration())
        .sscaEnforcementTag(vmImageConfig.getSscaEnforcement())
        .build();
  }

  private CIExecutionImages mapK8Config(CIStepConfig config) {
    if (config == null) {
      return CIExecutionImages.builder().build();
    }
    return CIExecutionImages.builder()
        .buildAndPushDockerRegistryTag(config.getBuildAndPushDockerRegistryConfig().getImage())
        .addonTag(ciExecutionServiceConfig.getAddonImage())
        .liteEngineTag(ciExecutionServiceConfig.getLiteEngineImage())
        .gitCloneTag(config.getGitCloneConfig().getImage())
        .buildAndPushECRTag(config.getBuildAndPushECRConfig().getImage())
        .buildAndPushGCRTag(config.getBuildAndPushGCRConfig().getImage())
        .buildAndPushACRTag(config.getBuildAndPushACRConfig().getImage())
        .gcsUploadTag(config.getGcsUploadConfig().getImage())
        .s3UploadTag(config.getS3UploadConfig().getImage())
        .artifactoryUploadTag(config.getArtifactoryUploadConfig().getImage())
        .cacheGCSTag(config.getCacheGCSConfig().getImage())
        .cacheS3Tag(config.getCacheS3Config().getImage())
        .securityTag(config.getSecurityConfig().getImage())
        .sscaOrchestrationTag(config.getSscaOrchestrationConfig().getImage())
        .sscaEnforcementTag(config.getSscaEnforcementConfig().getImage())
        .build();
  }

  private CIExecutionImages mapK8Config(CIExecutionConfig config) {
    return CIExecutionImages.builder()
        .buildAndPushDockerRegistryTag(config.getBuildAndPushDockerRegistryImage())
        .addonTag(config.getAddOnImage())
        .liteEngineTag(config.getLiteEngineImage())
        .gitCloneTag(config.getGitCloneImage())
        .buildAndPushECRTag(config.getBuildAndPushECRImage())
        .buildAndPushGCRTag(config.getBuildAndPushGCRImage())
        .buildAndPushACRTag(config.getBuildAndPushACRImage())
        .gcsUploadTag(config.getGcsUploadImage())
        .s3UploadTag(config.getS3UploadImage())
        .artifactoryUploadTag(config.getArtifactoryUploadTag())
        .cacheGCSTag(config.getCacheGCSTag())
        .cacheS3Tag(config.getCacheS3Tag())
        .securityTag(config.getSecurityImage())
        .sscaOrchestrationTag(config.getSscaOrchestrationTag())
        .sscaEnforcementTag(config.getSscaEnforcementTag())
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

  public CIExecutionImages getDeprecatedImages(String accountId) {
    Optional<CIExecutionConfig> configOptional = configRepository.findFirstByAccountIdentifier(accountId);
    CIExecutionImagesBuilder builder = CIExecutionImages.builder();

    if (configOptional.isPresent()) {
      CIExecutionConfig ciExecutionConfig = configOptional.get();
      String addonOverride = ciExecutionConfig.getAddOnImage();
      String liteEngineOverride = ciExecutionConfig.getLiteEngineImage();
      if (Strings.isNotBlank(addonOverride)) {
        if (hasLowerMajorVersion(ciExecutionServiceConfig.getAddonImage(), addonOverride)) {
          builder.addonTag(addonOverride);
        }
      }
      if (Strings.isNotBlank(liteEngineOverride)) {
        if (hasLowerMajorVersion(ciExecutionServiceConfig.getLiteEngineImage(), liteEngineOverride)) {
          builder.liteEngineTag(liteEngineOverride);
        }
      }
    }
    return builder.build();
  }

  private boolean checkForCIImage(String defaultImage, String customImage) {
    String defaultImageTag = defaultImage.split(":")[1];
    String customImageTag = customImage.split(":")[1];
    Version defaultVersion = Version.parseVersion(defaultImageTag);
    Version customVersion = Version.parseVersion(customImageTag);
    // we are supporting 2 back versions
    return defaultVersion.isLowerThanOrEqualTo(customVersion.nextMinor().nextMinor());
  }

  private boolean hasLowerMajorVersion(String defaultImage, String customImage) {
    String defaultImageTag = defaultImage.split(":")[1];
    String customImageTag = customImage.split(":")[1];
    Version defaultVersion = Version.parseVersion(defaultImageTag);
    Version customVersion = Version.parseVersion(customImageTag);
    return customVersion.getMajor() <= defaultVersion.getMajor() - 1;
  }

  public StepImageConfig getPluginVersionForK8(CIStepInfoType stepInfoType, String accountId) {
    Optional<CIExecutionConfig> existingConfig = configRepository.findFirstByAccountIdentifier(accountId);
    StepImageConfig stepImageConfig = getStepImageConfigForK8(stepInfoType, ciExecutionServiceConfig);
    String image = stepImageConfig.getImage();

    if (!existingConfig.isPresent()) {
      return StepImageConfig.builder()
          .entrypoint(stepImageConfig.getEntrypoint())
          .windowsEntrypoint(Optional.ofNullable(stepImageConfig.getWindowsEntrypoint()).orElse(emptyList()))
          .image(image)
          .build();
    }
    CIExecutionConfig ciExecutionConfig = existingConfig.get();

    switch (stepInfoType) {
      case DOCKER:
        if (Strings.isNotBlank(ciExecutionConfig.getBuildAndPushDockerRegistryImage())) {
          image = ciExecutionConfig.getBuildAndPushDockerRegistryImage();
        }
        break;
      case GCR:
        if (Strings.isNotBlank(ciExecutionConfig.getBuildAndPushGCRImage())) {
          image = ciExecutionConfig.getBuildAndPushGCRImage();
        }
        break;
      case ECR:
        if (Strings.isNotBlank(ciExecutionConfig.getBuildAndPushECRImage())) {
          image = ciExecutionConfig.getBuildAndPushECRImage();
        }
        break;
      case ACR:
        if (Strings.isNotBlank(ciExecutionConfig.getBuildAndPushACRImage())) {
          image = ciExecutionConfig.getBuildAndPushACRImage();
        }
        break;
      case RESTORE_CACHE_S3:
      case SAVE_CACHE_S3:
        if (Strings.isNotBlank(ciExecutionConfig.getCacheS3Tag())) {
          image = ciExecutionConfig.getCacheS3Tag();
        }
        break;
      case UPLOAD_S3:
        if (Strings.isNotBlank(ciExecutionConfig.getS3UploadImage())) {
          image = ciExecutionConfig.getS3UploadImage();
        }
        break;
      case UPLOAD_GCS:
        if (Strings.isNotBlank(ciExecutionConfig.getGcsUploadImage())) {
          image = ciExecutionConfig.getGcsUploadImage();
        }
        break;
      case SAVE_CACHE_GCS:
      case RESTORE_CACHE_GCS:
        if (Strings.isNotBlank(ciExecutionConfig.getCacheGCSTag())) {
          image = ciExecutionConfig.getCacheGCSTag();
        }
        break;
      case SECURITY:
        if (Strings.isNotBlank(ciExecutionConfig.getSecurityImage())) {
          image = ciExecutionConfig.getSecurityImage();
        }
        break;
      case UPLOAD_ARTIFACTORY:
        if (Strings.isNotBlank(ciExecutionConfig.getArtifactoryUploadTag())) {
          image = ciExecutionConfig.getArtifactoryUploadTag();
        }
        break;
      case GIT_CLONE:
        if (Strings.isNotBlank(ciExecutionConfig.getGitCloneImage())) {
          image = ciExecutionConfig.getGitCloneImage();
        }
        break;
      case SSCA_ORCHESTRATION:
        if (Strings.isNotBlank(ciExecutionConfig.getSscaOrchestrationTag())) {
          image = ciExecutionConfig.getSscaOrchestrationTag();
        }
        break;
      case SSCA_ENFORCEMENT:
        if (Strings.isNotBlank(ciExecutionConfig.getSscaEnforcementTag())) {
          image = ciExecutionConfig.getSscaEnforcementTag();
        }
        break;
      default:
        throw new BadRequestException(format(UNEXPECTED_ERR_FORMAT, stepInfoType));
    }
    return StepImageConfig.builder()
        .entrypoint(stepImageConfig.getEntrypoint())
        .windowsEntrypoint(Optional.ofNullable(stepImageConfig.getWindowsEntrypoint()).orElse(emptyList()))
        .image(image)
        .build();
  }

  private static StepImageConfig getStepImageConfigForK8(
      CIStepInfoType stepInfoType, CIExecutionServiceConfig ciExecutionServiceConfig) {
    switch (stepInfoType) {
      case DOCKER:
        return ciExecutionServiceConfig.getStepConfig().getBuildAndPushDockerRegistryConfig();
      case GCR:
        return ciExecutionServiceConfig.getStepConfig().getBuildAndPushGCRConfig();
      case ECR:
        return ciExecutionServiceConfig.getStepConfig().getBuildAndPushECRConfig();
      case ACR:
        return ciExecutionServiceConfig.getStepConfig().getBuildAndPushACRConfig();
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
      case SSCA_ORCHESTRATION:
        return ciExecutionServiceConfig.getStepConfig().getSscaOrchestrationConfig();
      case SSCA_ENFORCEMENT:
        return ciExecutionServiceConfig.getStepConfig().getSscaEnforcementConfig();
      default:
        throw new BadRequestException(format(UNEXPECTED_ERR_FORMAT, stepInfoType));
    }
  }

  public Boolean deleteCIExecutionConfig(String accountIdentifier) {
    Optional<CIExecutionConfig> executionConfig = configRepository.findFirstByAccountIdentifier(accountIdentifier);
    executionConfig.ifPresent(ciExecutionConfig -> configRepository.deleteById(ciExecutionConfig.getUuid()));
    return true;
  }

  public String getPluginVersionForVM(CIStepInfoType stepInfoType, String accountId) {
    Optional<CIExecutionConfig> existingConfig = configRepository.findFirstByAccountIdentifier(accountId);
    String image = getStepImageConfigForVM(stepInfoType, ciExecutionServiceConfig);
    if (!existingConfig.isPresent() || existingConfig.get().getVmImageConfig() == null) {
      return image;
    }
    VmImageConfig vmImageConfig = existingConfig.get().getVmImageConfig();
    switch (stepInfoType) {
      case DOCKER:
        if (Strings.isNotBlank(vmImageConfig.getBuildAndPushDockerRegistry())) {
          image = vmImageConfig.getBuildAndPushDockerRegistry();
        }
        break;
      case GCR:
        if (Strings.isNotBlank(vmImageConfig.getBuildAndPushGCR())) {
          image = vmImageConfig.getBuildAndPushGCR();
        }
        break;
      case ECR:
        if (Strings.isNotBlank(vmImageConfig.getBuildAndPushECR())) {
          image = vmImageConfig.getBuildAndPushECR();
        }
        break;
      case ACR:
        if (Strings.isNotBlank(vmImageConfig.getBuildAndPushACR())) {
          image = vmImageConfig.getBuildAndPushACR();
        }
        break;
      case RESTORE_CACHE_S3:
      case SAVE_CACHE_S3:
        if (Strings.isNotBlank(vmImageConfig.getCacheS3())) {
          image = vmImageConfig.getCacheS3();
        }
        break;
      case UPLOAD_S3:
        if (Strings.isNotBlank(vmImageConfig.getS3Upload())) {
          image = vmImageConfig.getS3Upload();
        }
        break;
      case UPLOAD_GCS:
        if (Strings.isNotBlank(vmImageConfig.getGcsUpload())) {
          image = vmImageConfig.getGcsUpload();
        }
        break;
      case SAVE_CACHE_GCS:
      case RESTORE_CACHE_GCS:
        if (Strings.isNotBlank(vmImageConfig.getCacheGCS())) {
          image = vmImageConfig.getCacheGCS();
        }
        break;
      case SECURITY:
        if (Strings.isNotBlank(vmImageConfig.getSecurity())) {
          image = vmImageConfig.getSecurity();
        }
        break;
      case UPLOAD_ARTIFACTORY:
        if (Strings.isNotBlank(vmImageConfig.getArtifactoryUpload())) {
          image = vmImageConfig.getArtifactoryUpload();
        }
        break;
      case GIT_CLONE:
        if (Strings.isNotBlank(vmImageConfig.getGitClone())) {
          image = vmImageConfig.getGitClone();
        }
        break;
      case IACM:
        if (Strings.isNotBlank(vmImageConfig.getIacmTerraform())) {
          image = vmImageConfig.getIacmTerraform();
        }
        break;
      case SSCA_ORCHESTRATION:
        if (Strings.isNotBlank(vmImageConfig.getSscaOrchestration())) {
          image = vmImageConfig.getSscaOrchestration();
        }
        break;
      case SSCA_ENFORCEMENT:
        if (Strings.isNotBlank(vmImageConfig.getSscaEnforcement())) {
          image = vmImageConfig.getSscaEnforcement();
        }
        break;
      default:
        throw new BadRequestException(format(UNEXPECTED_ERR_FORMAT, stepInfoType));
    }
    return image;
  }

  public String getContainerlessPluginNameForVM(
      CIStepInfoType stepInfoType, PluginCompatibleStep pluginCompatibleStep) {
    VmContainerlessStepConfig vmContainerlessStepConfig =
        ciExecutionServiceConfig.getStepConfig().getVmContainerlessStepConfig();
    String name = null;
    switch (stepInfoType) {
      case UPLOAD_S3:
        name = vmContainerlessStepConfig.getS3UploadConfig().getName();
        break;
      case UPLOAD_GCS:
        name = vmContainerlessStepConfig.getGcsUploadConfig().getName();
        break;
      case SAVE_CACHE_S3:
      case RESTORE_CACHE_S3:
        name = vmContainerlessStepConfig.getCacheS3Config().getName();
        break;
      case SAVE_CACHE_GCS:
      case RESTORE_CACHE_GCS:
        name = vmContainerlessStepConfig.getCacheGCSConfig().getName();
        break;
      case GIT_CLONE:
        name = vmContainerlessStepConfig.getGitCloneConfig().getName();
        break;
      case DOCKER:
        if (pluginSettingUtils.buildxRequired(pluginCompatibleStep)) {
          name = vmContainerlessStepConfig.getDockerBuildxConfig().getName();
        }
        break;
      case ECR:
        if (pluginSettingUtils.buildxRequired(pluginCompatibleStep)) {
          name = vmContainerlessStepConfig.getDockerBuildxEcrConfig().getName();
        }
        break;
      case GCR:
        if (pluginSettingUtils.buildxRequired(pluginCompatibleStep)) {
          name = vmContainerlessStepConfig.getDockerBuildxGcrConfig().getName();
        }
        break;
      case ACR:
      case SECURITY:
      case UPLOAD_ARTIFACTORY:
      case IACM:
      case SSCA_ORCHESTRATION:
      case SSCA_ENFORCEMENT:
        break;
      default:
        throw new BadRequestException(format(UNEXPECTED_ERR_FORMAT, stepInfoType));
    }
    return name;
  }

  private String getStepImageConfigForVM(
      CIStepInfoType stepInfoType, CIExecutionServiceConfig ciExecutionServiceConfig) {
    VmImageConfig vmImageConfig = ciExecutionServiceConfig.getStepConfig().getVmImageConfig();
    switch (stepInfoType) {
      case DOCKER:
        return vmImageConfig.getBuildAndPushDockerRegistry();
      case GCR:
        return vmImageConfig.getBuildAndPushGCR();
      case ECR:
        return vmImageConfig.getBuildAndPushECR();
      case ACR:
        return vmImageConfig.getBuildAndPushACR();
      case RESTORE_CACHE_S3:
      case SAVE_CACHE_S3:
        return vmImageConfig.getCacheS3();
      case UPLOAD_S3:
        return vmImageConfig.getS3Upload();
      case UPLOAD_GCS:
        return vmImageConfig.getGcsUpload();
      case SAVE_CACHE_GCS:
      case RESTORE_CACHE_GCS:
        return vmImageConfig.getCacheGCS();
      case SECURITY:
        return vmImageConfig.getSecurity();
      case UPLOAD_ARTIFACTORY:
        return vmImageConfig.getArtifactoryUpload();
      case GIT_CLONE:
        return vmImageConfig.getGitClone();
      case IACM_TERRAFORM_PLUGIN:
      case IACM_APPROVAL:
        return vmImageConfig.getIacmTerraform();
      case SSCA_ORCHESTRATION:
        return vmImageConfig.getSscaOrchestration();
      case SSCA_ENFORCEMENT:
        return vmImageConfig.getSscaEnforcement();
      default:
        throw new BadRequestException(format(UNEXPECTED_ERR_FORMAT, stepInfoType));
    }
  }
}
