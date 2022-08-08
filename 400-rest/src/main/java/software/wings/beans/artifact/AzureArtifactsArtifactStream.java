/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.beans.artifact;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.exception.WingsException.USER;

import static software.wings.beans.CGConstants.GLOBAL_APP_ID;
import static software.wings.beans.artifact.ArtifactStreamType.AZURE_ARTIFACTS;
import static software.wings.service.impl.artifact.ArtifactServiceImpl.metadataOnlyBehindFlag;

import static java.lang.String.format;

import io.harness.annotations.dev.BreakDependencyOn;
import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.beans.EmbeddedUser;
import io.harness.exception.InvalidRequestException;
import io.harness.ff.FeatureFlagService;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonTypeName;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Set;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.hibernate.validator.constraints.NotEmpty;

@OwnedBy(CDC)
@TargetModule(HarnessModule._957_CG_BEANS)
@JsonTypeName("AZURE_ARTIFACTS")
@JsonIgnoreProperties(ignoreUnknown = true)
@Data
@EqualsAndHashCode(callSuper = false)
@BreakDependencyOn("io.harness.ff.FeatureFlagService")
public class AzureArtifactsArtifactStream extends ArtifactStream {
  @NotEmpty private String protocolType;
  private String project;
  @NotEmpty private String feed;
  @NotEmpty private String packageId;
  @NotEmpty private String packageName;

  public AzureArtifactsArtifactStream() {
    super(AZURE_ARTIFACTS.name());
    setMetadataOnly(true);
  }

  @Builder
  public AzureArtifactsArtifactStream(String uuid, String appId, EmbeddedUser createdBy, long createdAt,
      EmbeddedUser lastUpdatedBy, long lastUpdatedAt, String entityYamlPath, String sourceName, String settingId,
      String name, boolean autoPopulate, String serviceId, String protocolType, String project, String feed,
      String packageId, String packageName, String accountId, Set<String> keywords, boolean sample) {
    super(uuid, appId, createdBy, createdAt, lastUpdatedBy, lastUpdatedAt, entityYamlPath, AZURE_ARTIFACTS.name(),
        sourceName, settingId, name, autoPopulate, serviceId, true, accountId, keywords, sample);
    this.protocolType = protocolType;
    this.project = project;
    this.feed = feed;
    this.packageId = packageId;
    this.packageName = packageName;
  }

  @Override
  public String generateSourceName() {
    return packageName != null
            && (AzureArtifactsArtifactStreamProtocolType.maven.name().equals(protocolType)
                || AzureArtifactsArtifactStreamProtocolType.nuget.name().equals(protocolType))
        ? packageName
        : "";
  }

  @Override
  public String fetchArtifactDisplayName(String buildNo) {
    return format("%s_%s_%s", getSourceName(), buildNo, new SimpleDateFormat(dateFormat).format(new Date()));
  }

  @Override
  public ArtifactStreamAttributes fetchArtifactStreamAttributes(FeatureFlagService featureFlagService) {
    return ArtifactStreamAttributes.builder()
        .artifactStreamType(getArtifactStreamType())
        .metadataOnly(metadataOnlyBehindFlag(featureFlagService, getAccountId(), isMetadataOnly()))
        .protocolType(protocolType)
        .project(project)
        .feed(feed)
        .packageId(packageId)
        .packageName(packageName)
        .build();
  }

  @Override
  public boolean checkIfStreamParameterized() {
    return validateParameters(feed, packageId, packageName);
  }

  @Override
  public boolean artifactSourceChanged(ArtifactStream artifactStream) {
    if (super.artifactSourceChanged(artifactStream)) {
      return true;
    }

    AzureArtifactsArtifactStream azureArtifactsArtifactStream = (AzureArtifactsArtifactStream) artifactStream;
    return strChanged(project, azureArtifactsArtifactStream.getProject())
        || strChanged(feed, azureArtifactsArtifactStream.getFeed())
        || strChanged(packageId, azureArtifactsArtifactStream.getPackageId());
  }

  @Override
  public void validateRequiredFields() {
    if (GLOBAL_APP_ID.equals(appId)) {
      if (isEmpty(feed)) {
        throw new InvalidRequestException("Feed cannot be empty", USER);
      }
      if (isEmpty(packageId)) {
        throw new InvalidRequestException("Package cannot be empty", USER);
      }

      if (isEmpty(protocolType)) {
        throw new InvalidRequestException("Protocol type cannot be empty", USER);
      } else if (AzureArtifactsArtifactStreamProtocolType.maven.name().equals(protocolType)
          || AzureArtifactsArtifactStreamProtocolType.nuget.name().equals(protocolType)) {
        if (isEmpty(packageName)) {
          throw new InvalidRequestException("Package name cannot be empty", USER);
        }
      } else {
        throw new InvalidRequestException("Invalid protocol type", USER);
      }
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
        .protocolType(protocolType)
        .project(project)
        .feed(feed)
        .packageName(packageName)
        .packageId(packageId)
        .build();
  }

  @Override
  public boolean shouldValidate() {
    return true;
  }

  private boolean strChanged(String oldStr, String newStr) {
    if (isEmpty(oldStr) || isEmpty(newStr)) {
      return isNotEmpty(oldStr) || isNotEmpty(newStr);
    }
    return !oldStr.equals(newStr);
  }

  @Data
  @NoArgsConstructor
  @EqualsAndHashCode(callSuper = true)
  public static class Yaml extends ArtifactStream.Yaml {
    private String packageType;
    private String project;
    private String feed;
    private String packageId;
    private String packageName;

    @Builder
    public Yaml(String harnessApiVersion, String serverName, String packageType, String project, String feed,
        String packageId, String packageName) {
      super(AZURE_ARTIFACTS.name(), harnessApiVersion, serverName);
      this.packageType = packageType;
      this.project = project;
      this.feed = feed;
      this.packageId = packageId;
      this.packageName = packageName;
    }
  }
}
