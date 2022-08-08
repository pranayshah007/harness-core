/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.beans.artifact;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.delegate.beans.azure.AzureMachineImageArtifactDTO.ImageType;
import static io.harness.exception.WingsException.USER;

import static software.wings.beans.artifact.ArtifactStreamType.AZURE_MACHINE_IMAGE;

import static java.lang.String.format;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.beans.EmbeddedUser;
import io.harness.delegate.beans.azure.AzureMachineImageArtifactDTO;
import io.harness.exception.InvalidRequestException;
import io.harness.ff.FeatureFlagService;

import com.fasterxml.jackson.annotation.JsonTypeName;
import java.util.Set;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.apache.commons.lang3.StringUtils;

@OwnedBy(CDC)
@TargetModule(HarnessModule._957_CG_BEANS)
@JsonTypeName("AZURE_MACHINE_IMAGE")
@Data
@EqualsAndHashCode(callSuper = true)
public class AzureMachineImageArtifactStream extends ArtifactStream {
  public enum OSType { LINUX, WINDOWS }

  private OSType osType;
  private AzureMachineImageArtifactDTO.ImageType imageType;
  private String subscriptionId;
  private ImageDefinition imageDefinition;

  public AzureMachineImageArtifactStream() {
    super(AZURE_MACHINE_IMAGE.name());
    super.setMetadataOnly(true);
  }

  @Builder
  public AzureMachineImageArtifactStream(String uuid, String appId, EmbeddedUser createdBy, long createdAt,
      EmbeddedUser lastUpdatedBy, long lastUpdatedAt, String entityYamlPath, String sourceName, String settingId,
      String accountId, Set<String> keywords, boolean sample, String name, boolean autoPopulate, String serviceId,
      OSType osType, ImageType imageType, String subscriptionId, ImageDefinition imageDefinition) {
    super(uuid, appId, createdBy, createdAt, lastUpdatedBy, lastUpdatedAt, entityYamlPath, AZURE_MACHINE_IMAGE.name(),
        sourceName, settingId, name, autoPopulate, serviceId, true, accountId, keywords, sample);
    this.osType = osType;
    this.imageType = imageType;
    this.subscriptionId = subscriptionId;
    this.imageDefinition = imageDefinition;
  }

  @Override
  public String fetchArtifactDisplayName(String imageName) {
    return format("%s_%s", getSourceName(), imageName);
  }

  @Override
  public String generateSourceName() {
    if (ImageType.IMAGE_GALLERY == imageType) {
      return imageDefinition.getImageDefinitionName();
    }

    return imageType.name();
  }

  @Override
  public boolean shouldValidate() {
    return true;
  }

  @Override
  public ArtifactStreamAttributes fetchArtifactStreamAttributes(FeatureFlagService featureFlagService) {
    return ArtifactStreamAttributes.builder()
        .artifactStreamType(getArtifactStreamType())
        .subscriptionId(subscriptionId)
        .osType(osType == null ? null : osType.name())
        .imageType(imageType.name())
        .azureImageDefinition(imageDefinition.getImageDefinitionName())
        .azureImageGalleryName(imageDefinition.getImageGalleryName())
        .azureResourceGroup(imageDefinition.getResourceGroup())
        .build();
  }

  @Override
  public void validateRequiredFields() {
    if (imageType == null) {
      throw new InvalidRequestException("Image type must have a valid value", USER);
    }
    if (ImageType.IMAGE_GALLERY == imageType) {
      if (StringUtils.isBlank(subscriptionId)) {
        throw new InvalidRequestException("Invalid subscription", USER);
      }
      if (imageDefinition == null) {
        throw new InvalidRequestException("Invalid image definition", USER);
      }
      imageDefinition.validate();
    }
  }

  @Override
  public ArtifactStream cloneInternal() {
    return builder()
        .appId(getAppId())
        .accountId(getAccountId())
        .name(getName())
        .sourceName(getSourceName())
        .settingId(getSettingId())
        .keywords(getKeywords())
        .osType(osType)
        .imageType(imageType)
        .subscriptionId(subscriptionId)
        .imageDefinition(imageDefinition)
        .build();
  }

  @Override
  public void inferProperties(ArtifactStreamAttributes attributes) {
    this.osType = OSType.valueOf(attributes.getOsType());
  }

  @Data
  @NoArgsConstructor
  @EqualsAndHashCode(callSuper = true)
  public static class Yml extends ArtifactStream.Yaml {
    private ImageType imageType;
    private String subscriptionId;
    private ImageDefinition imageDefinition;

    @Builder
    public Yml(String harnessApiVersion, String serverName, ImageType imageType, String subscriptionId,
        ImageDefinition imageDefinition) {
      super(AZURE_MACHINE_IMAGE.name(), harnessApiVersion, serverName);
      this.imageType = imageType;
      this.subscriptionId = subscriptionId;
      this.imageDefinition = imageDefinition;
    }
  }

  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  public static class ImageDefinition {
    private String resourceGroup;
    private String imageGalleryName;
    private String imageDefinitionName;

    public void validate() {
      if (StringUtils.isAnyBlank(resourceGroup, imageGalleryName, imageDefinitionName)) {
        throw new InvalidRequestException("Invalid value(s) for Resource group or Gallery or Image definition");
      }
    }
  }
}
