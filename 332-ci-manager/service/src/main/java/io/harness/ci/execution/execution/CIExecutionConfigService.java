/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.execution.execution;

import static java.lang.String.format;
import static java.util.Collections.emptyList;

import io.harness.beans.plugin.compatible.PluginCompatibleStep;
import io.harness.beans.steps.CIStepInfoType;
import io.harness.beans.sweepingoutputs.StageInfraDetails.Type;
import io.harness.ci.beans.entities.CIExecutionConfig;
import io.harness.ci.beans.entities.CIExecutionImages;
import io.harness.ci.beans.entities.CIExecutionImages.CIExecutionImagesBuilder;
import io.harness.ci.config.CIExecutionServiceConfig;
import io.harness.ci.config.CIStepConfig;
import io.harness.ci.config.Operation;
import io.harness.ci.config.PluginField;
import io.harness.ci.config.StepImageConfig;
import io.harness.ci.config.VmContainerlessStepConfig;
import io.harness.ci.config.VmImageConfig;
import io.harness.ci.execution.DeprecatedImageInfo;
import io.harness.ci.execution.buildstate.PluginSettingUtils;
import io.harness.repositories.CIExecutionConfigRepository;

import com.google.inject.Inject;
import de.skuzzle.semantic.Version;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import javax.ws.rs.BadRequestException;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.util.Strings;

public class CIExecutionConfigService {
  @Inject CIExecutionConfigRepository configRepository;
  @Inject CIExecutionServiceConfig ciExecutionServiceConfig;
  @Inject private PluginSettingUtils pluginSettingUtils;

  private static final String UNEXPECTED_ERR_FORMAT = "Unexpected value: %s";

  private static final String GLOBAL_ACCOUNT_ID = "__GLOBAL_ACCOUNT_ID__";

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
      case BUILD_PUSH_GAR:
        executionConfig.setBuildAndPushGARImage(value);
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
      case PROVENANCE:
        executionConfig.setProvenanceTag(value);
        break;
      case SLSA_VERIFICATION:
        executionConfig.setSlsaVerificationTag(value);
        break;
      case PROVENANCE_GCR:
        executionConfig.setProvenanceGcrTag(value);
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
      case BUILD_PUSH_GAR:
        vmImageConfig.setBuildAndPushGAR(value);
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
      case SSCA_ORCHESTRATION:
        vmImageConfig.setSscaOrchestration(value);
        break;
      case SSCA_ENFORCEMENT:
        vmImageConfig.setSscaEnforcement(value);
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
    if (Strings.isNotBlank(overriddenConfig.getBuildAndPushGARTag())) {
      defaultConfig.setBuildAndPushGARTag(overriddenConfig.getBuildAndPushGARTag());
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
        .buildAndPushGARTag(vmImageConfig.getBuildAndPushGAR())
        .buildAndPushGCRTag(vmImageConfig.getBuildAndPushGCR())
        .gcsUploadTag(vmImageConfig.getGcsUpload())
        .s3UploadTag(vmImageConfig.getS3Upload())
        .artifactoryUploadTag(vmImageConfig.getArtifactoryUpload())
        .cacheGCSTag(vmImageConfig.getCacheGCS())
        .cacheS3Tag(vmImageConfig.getCacheS3())
        .securityTag(vmImageConfig.getSecurity())
        .sscaOrchestrationTag(vmImageConfig.getSscaOrchestration())
        .sscaEnforcementTag(vmImageConfig.getSscaEnforcement())
        .slsaVerificationTag(vmImageConfig.getSlsaVerification())
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
        .buildAndPushGARTag(vmImageConfig.getBuildAndPushGAR())
        .buildAndPushACRTag(vmImageConfig.getBuildAndPushACR())
        .gcsUploadTag(vmImageConfig.getGcsUpload())
        .s3UploadTag(vmImageConfig.getS3Upload())
        .artifactoryUploadTag(vmImageConfig.getArtifactoryUpload())
        .cacheGCSTag(vmImageConfig.getCacheGCS())
        .cacheS3Tag(vmImageConfig.getCacheS3())
        .securityTag(vmImageConfig.getSecurity())
        .sscaOrchestrationTag(vmImageConfig.getSscaOrchestration())
        .sscaEnforcementTag(vmImageConfig.getSscaEnforcement())
        .slsaVerificationTag(vmImageConfig.getSlsaVerification())
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
        .buildAndPushGARTag(config.getBuildAndPushGARConfig().getImage())
        .gcsUploadTag(config.getGcsUploadConfig().getImage())
        .s3UploadTag(config.getS3UploadConfig().getImage())
        .artifactoryUploadTag(config.getArtifactoryUploadConfig().getImage())
        .cacheGCSTag(config.getCacheGCSConfig().getImage())
        .cacheS3Tag(config.getCacheS3Config().getImage())
        .securityTag(config.getSecurityConfig().getImage())
        .sscaOrchestrationTag(config.getSscaOrchestrationConfig().getImage())
        .sscaEnforcementTag(config.getSscaEnforcementConfig().getImage())
        .provenanceTag(config.getProvenanceConfig().getImage())
        .provenanceGcrTag(config.getProvenanceGcrConfig().getImage())
        .slsaVerificationTag(config.getSlsaVerificationConfig().getImage())
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
        .buildAndPushGARTag(config.getBuildAndPushGARImage())
        .gcsUploadTag(config.getGcsUploadImage())
        .s3UploadTag(config.getS3UploadImage())
        .artifactoryUploadTag(config.getArtifactoryUploadTag())
        .cacheGCSTag(config.getCacheGCSTag())
        .cacheS3Tag(config.getCacheS3Tag())
        .securityTag(config.getSecurityImage())
        .sscaOrchestrationTag(config.getSscaOrchestrationTag())
        .sscaEnforcementTag(config.getSscaEnforcementTag())
        .provenanceTag(config.getProvenanceTag())
        .provenanceGcrTag(config.getProvenanceGcrTag())
        .slsaVerificationTag(config.getSlsaVerificationTag())
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
  private static boolean doesStepSupportGlobalAccountConfig(CIStepInfoType stepInfoType) {
    // TODO: We can enhance the CIStepInfoType enum to have this as a property.
    switch (stepInfoType) {
      case SSCA_ORCHESTRATION:
      case SSCA_ENFORCEMENT:
      case PROVENANCE:
      case PROVENANCE_GCR:
      case SLSA_VERIFICATION:
        return true;
      default:
        return false;
    }
  }

  private static String getApplicableImage(CIStepInfoType stepInfoType, String accountLevelImage, String globalImage) {
    if (doesStepSupportGlobalAccountConfig(stepInfoType)) {
      return StringUtils.isBlank(accountLevelImage) ? globalImage : accountLevelImage;
    } else {
      return accountLevelImage;
    }
  }

  private String getImageForK8(CIStepInfoType stepInfoType, String accountId) {
    CIExecutionConfig accountLevelExecutionConfig =
        configRepository.findFirstByAccountIdentifier(accountId).orElse(CIExecutionConfig.builder().build());
    CIExecutionConfig globalExecutionConfig =
        configRepository.findFirstByAccountIdentifier(GLOBAL_ACCOUNT_ID).orElse(CIExecutionConfig.builder().build());
    switch (stepInfoType) {
      case DOCKER:
        return getApplicableImage(stepInfoType, accountLevelExecutionConfig.getBuildAndPushDockerRegistryImage(),
            globalExecutionConfig.getBuildAndPushDockerRegistryImage());
      case GCR:
        return getApplicableImage(stepInfoType, accountLevelExecutionConfig.getBuildAndPushGCRImage(),
            globalExecutionConfig.getBuildAndPushGCRImage());
      case GAR:
        return getApplicableImage(stepInfoType, accountLevelExecutionConfig.getBuildAndPushGARImage(),
            globalExecutionConfig.getBuildAndPushGARImage());
      case ECR:
        return getApplicableImage(stepInfoType, accountLevelExecutionConfig.getBuildAndPushECRImage(),
            globalExecutionConfig.getBuildAndPushECRImage());
      case ACR:
        return getApplicableImage(stepInfoType, accountLevelExecutionConfig.getBuildAndPushACRImage(),
            globalExecutionConfig.getBuildAndPushACRImage());
      case RESTORE_CACHE_S3:
      case SAVE_CACHE_S3:
        return getApplicableImage(
            stepInfoType, accountLevelExecutionConfig.getCacheS3Tag(), globalExecutionConfig.getCacheS3Tag());
      case UPLOAD_S3:
        return getApplicableImage(
            stepInfoType, accountLevelExecutionConfig.getS3UploadImage(), globalExecutionConfig.getS3UploadImage());
      case UPLOAD_GCS:
        return getApplicableImage(
            stepInfoType, accountLevelExecutionConfig.getGcsUploadImage(), globalExecutionConfig.getGcsUploadImage());
      case SAVE_CACHE_GCS:
      case RESTORE_CACHE_GCS:
        return getApplicableImage(
            stepInfoType, accountLevelExecutionConfig.getCacheGCSTag(), globalExecutionConfig.getCacheGCSTag());
      case SECURITY:
        return getApplicableImage(
            stepInfoType, accountLevelExecutionConfig.getSecurityImage(), globalExecutionConfig.getSecurityImage());
      case UPLOAD_ARTIFACTORY:
        return getApplicableImage(stepInfoType, accountLevelExecutionConfig.getArtifactoryUploadTag(),
            globalExecutionConfig.getArtifactoryUploadTag());
      case GIT_CLONE:
        return getApplicableImage(
            stepInfoType, accountLevelExecutionConfig.getGitCloneImage(), globalExecutionConfig.getGitCloneImage());
      case SSCA_ORCHESTRATION:
        return getApplicableImage(stepInfoType, accountLevelExecutionConfig.getSscaOrchestrationTag(),
            globalExecutionConfig.getSscaOrchestrationTag());
      case SSCA_ENFORCEMENT:
        return getApplicableImage(stepInfoType, accountLevelExecutionConfig.getSscaEnforcementTag(),
            globalExecutionConfig.getSscaEnforcementTag());
      case PROVENANCE:
        return getApplicableImage(
            stepInfoType, accountLevelExecutionConfig.getProvenanceTag(), globalExecutionConfig.getProvenanceTag());
      case PROVENANCE_GCR:
        return getApplicableImage(stepInfoType, accountLevelExecutionConfig.getProvenanceGcrTag(),
            globalExecutionConfig.getProvenanceGcrTag());
      case SLSA_VERIFICATION:
        return getApplicableImage(stepInfoType, accountLevelExecutionConfig.getSlsaVerificationTag(),
            globalExecutionConfig.getSlsaVerificationTag());
      default:
        throw new BadRequestException(format(UNEXPECTED_ERR_FORMAT, stepInfoType));
    }
  }
  public StepImageConfig getPluginVersionForK8(CIStepInfoType stepInfoType, String accountId) {
    StepImageConfig stepImageConfig = getStepImageConfigForK8(stepInfoType, ciExecutionServiceConfig);
    String image = getImageForK8(stepInfoType, accountId);
    if (StringUtils.isBlank(image)) {
      image = stepImageConfig.getImage();
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
      case GAR:
        return ciExecutionServiceConfig.getStepConfig().getBuildAndPushGARConfig();
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
      case PROVENANCE:
        return ciExecutionServiceConfig.getStepConfig().getProvenanceConfig();
      case PROVENANCE_GCR:
        return ciExecutionServiceConfig.getStepConfig().getProvenanceGcrConfig();
      case SLSA_VERIFICATION:
        return ciExecutionServiceConfig.getStepConfig().getSlsaVerificationConfig();
      case IACM_TERRAFORM_PLUGIN:
      case IACM_APPROVAL:
        return ciExecutionServiceConfig.getStepConfig().getIacmTerraform();
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
    String image = getImageForVM(stepInfoType, accountId);
    return StringUtils.isBlank(image) ? getStepImageConfigForVM(stepInfoType, ciExecutionServiceConfig) : image;
  }

  private String getImageForVM(CIStepInfoType stepInfoType, String accountId) {
    VmImageConfig accountLevelImageConfig =
        configRepository.findFirstByAccountIdentifier(accountId)
            .orElse(CIExecutionConfig.builder().vmImageConfig(VmImageConfig.builder().build()).build())
            .getVmImageConfig();
    VmImageConfig globalImageConfig =
        configRepository.findFirstByAccountIdentifier(GLOBAL_ACCOUNT_ID)
            .orElse(CIExecutionConfig.builder().vmImageConfig(VmImageConfig.builder().build()).build())
            .getVmImageConfig();
    switch (stepInfoType) {
      case DOCKER:
        return getApplicableImage(stepInfoType, accountLevelImageConfig.getBuildAndPushDockerRegistry(),
            globalImageConfig.getBuildAndPushDockerRegistry());
      case GCR:
        return getApplicableImage(
            stepInfoType, accountLevelImageConfig.getBuildAndPushGCR(), globalImageConfig.getBuildAndPushGCR());
      case GAR:
        return getApplicableImage(
            stepInfoType, accountLevelImageConfig.getBuildAndPushGAR(), globalImageConfig.getBuildAndPushGAR());
      case ECR:
        return getApplicableImage(
            stepInfoType, accountLevelImageConfig.getBuildAndPushECR(), globalImageConfig.getBuildAndPushECR());
      case ACR:
        return getApplicableImage(
            stepInfoType, accountLevelImageConfig.getBuildAndPushACR(), globalImageConfig.getBuildAndPushACR());
      case RESTORE_CACHE_S3:
      case SAVE_CACHE_S3:
        return getApplicableImage(stepInfoType, accountLevelImageConfig.getCacheS3(), globalImageConfig.getCacheS3());
      case UPLOAD_S3:
        return getApplicableImage(stepInfoType, accountLevelImageConfig.getS3Upload(), globalImageConfig.getS3Upload());
      case UPLOAD_GCS:
        return getApplicableImage(
            stepInfoType, accountLevelImageConfig.getGcsUpload(), globalImageConfig.getGcsUpload());
      case SAVE_CACHE_GCS:
      case RESTORE_CACHE_GCS:
        return getApplicableImage(stepInfoType, accountLevelImageConfig.getCacheGCS(), globalImageConfig.getCacheGCS());
      case SECURITY:
        return getApplicableImage(stepInfoType, accountLevelImageConfig.getSecurity(), globalImageConfig.getSecurity());
      case UPLOAD_ARTIFACTORY:
        return getApplicableImage(
            stepInfoType, accountLevelImageConfig.getArtifactoryUpload(), globalImageConfig.getArtifactoryUpload());
      case GIT_CLONE:
        return getApplicableImage(stepInfoType, accountLevelImageConfig.getGitClone(), globalImageConfig.getGitClone());
      case IACM:
        return getApplicableImage(
            stepInfoType, accountLevelImageConfig.getIacmTerraform(), globalImageConfig.getIacmTerraform());
      case SSCA_ORCHESTRATION:
        return getApplicableImage(
            stepInfoType, accountLevelImageConfig.getSscaOrchestration(), globalImageConfig.getSscaOrchestration());
      case SSCA_ENFORCEMENT:
        return getApplicableImage(
            stepInfoType, accountLevelImageConfig.getSscaEnforcement(), globalImageConfig.getSscaEnforcement());
      default:
        throw new BadRequestException(format(UNEXPECTED_ERR_FORMAT, stepInfoType));
    }
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
      case GAR:
        if (pluginSettingUtils.buildxRequired(pluginCompatibleStep)) {
          name = vmContainerlessStepConfig.getDockerBuildxGarConfig().getName();
        }
        break;
      case GCR:
        if (pluginSettingUtils.buildxRequired(pluginCompatibleStep)) {
          name = vmContainerlessStepConfig.getDockerBuildxGcrConfig().getName();
        }
        break;
      case ACR:
        if (pluginSettingUtils.buildxRequired(pluginCompatibleStep)) {
          name = vmContainerlessStepConfig.getDockerBuildxAcrConfig().getName();
        }
        break;
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
      case GAR:
        return vmImageConfig.getBuildAndPushGAR();
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
