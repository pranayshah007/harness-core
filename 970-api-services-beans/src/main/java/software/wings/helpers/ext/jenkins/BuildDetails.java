/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.helpers.ext.jenkins;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.beans.artifact.ArtifactFileMetadata;

import com.google.common.base.MoreObjects;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.experimental.UtilityClass;

@OwnedBy(HarnessTeam.CDC)
public class BuildDetails {
  @UtilityClass
  public static final class BuildDetailsMetadataKeys {
    public static final String image = "image";
    public static final String tag = "tag";
  }

  private String number;
  private String revision;
  private String description;
  private String artifactPath;
  private String buildUrl;
  private String buildDisplayName;
  private String buildFullDisplayName;
  private String artifactFileSize;
  private String uiDisplayName;
  private Long updateTime;
  private BuildStatus status;
  private Map<String, String> buildParameters = new HashMap<>();
  private Map<String, String> metadata = new HashMap<>();
  private Map<String, String> labels = new HashMap<>();
  private List<ArtifactFileMetadata> artifactFileMetadataList = new ArrayList<>();

  public enum BuildStatus {
    FAILURE("Failure"),
    UNSTABLE("Unstable"),
    SUCCESS("Success");

    BuildStatus(String displayName) {
      this.displayName = displayName;
    }

    private String displayName;

    public String getDisplayName() {
      return displayName;
    }
  }

  public Long getUpdateTime() {
    return updateTime;
  }

  public void setUpdateTime(Long updateTime) {
    this.updateTime = updateTime;
  }

  public String getBuildDisplayName() {
    return buildDisplayName;
  }

  public void setBuildDisplayName(String buildDisplayName) {
    this.buildDisplayName = buildDisplayName;
  }

  public String getBuildFullDisplayName() {
    return buildFullDisplayName;
  }

  public void setBuildFullDisplayName(String buildFullDisplayName) {
    this.buildFullDisplayName = buildFullDisplayName;
  }

  public String getUiDisplayName() {
    return uiDisplayName;
  }

  public void setUiDisplayName(String uiDisplayName) {
    this.uiDisplayName = uiDisplayName;
  }

  public List<ArtifactFileMetadata> getArtifactFileMetadataList() {
    return artifactFileMetadataList;
  }

  public void setArtifactFileMetadataList(List<ArtifactFileMetadata> artifactFileMetadataList) {
    this.artifactFileMetadataList = artifactFileMetadataList;
  }

  /**
   * Gets number.
   *
   * @return the number
   */
  public String getNumber() {
    return number;
  }

  /**
   * Sets number.
   *
   * @param number the number
   */
  public void setNumber(String number) {
    this.number = number;
  }

  /**
   * Gets revision.
   *
   * @return the revision
   */
  public String getRevision() {
    return revision;
  }

  /**
   * Sets revision.
   *
   * @param revision the revision
   */
  public void setRevision(String revision) {
    this.revision = revision;
  }

  /**
   * Get Artifact Path
   * @return
   */
  public String getArtifactPath() {
    return artifactPath;
  }

  /**
   * Set File name
   * @param artifactPath
   */
  public void setArtifactPath(String artifactPath) {
    this.artifactPath = artifactPath;
  }

  public String getBuildUrl() {
    return buildUrl;
  }

  public void setBuildUrl(String buildUrl) {
    this.buildUrl = buildUrl;
  }

  public String getArtifactFileSize() {
    return artifactFileSize;
  }

  public void setArtifactFileSize(String artifactFileSize) {
    this.artifactFileSize = artifactFileSize;
  }

  public BuildStatus getStatus() {
    return status;
  }

  public void setStatus(BuildStatus status) {
    this.status = status;
  }

  @Override
  public int hashCode() {
    return Objects.hash(number, revision);
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    if (obj == null || getClass() != obj.getClass()) {
      return false;
    }
    final BuildDetails other = (BuildDetails) obj;
    return Objects.equals(this.number, other.number) && Objects.equals(this.revision, other.revision);
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this).add("number", number).toString();
  }

  /**
   * Gets description.
   *
   * @return the description
   */
  public String getDescription() {
    return description;
  }

  /**
   * Sets description.
   *
   * @param description the description
   */
  public void setDescription(String description) {
    this.description = description;
  }

  /**
   * Gets build parameters.
   *
   * @return the build parameters
   */
  public Map<String, String> getBuildParameters() {
    return buildParameters;
  }

  public void setBuildParameters(Map<String, String> buildParameters) {
    this.buildParameters = buildParameters;
  }

  public Map<String, String> getMetadata() {
    return metadata;
  }

  public void setMetadata(Map<String, String> metadata) {
    this.metadata = metadata;
  }

  public Map<String, String> getLabels() {
    return labels;
  }

  public void setLabels(Map<String, String> labels) {
    this.labels = labels;
  }

  /**
   * The type Builder.
   */
  public static final class Builder {
    private String number;
    private String revision;
    private String description;
    private Map<String, String> buildParameters = new HashMap<>();
    private String artifactPath;
    private String buildUrl;
    private String buildDisplayName;
    private String buildFullDisplayName;
    private String artifactFileSize;
    private BuildStatus status;
    private Long updateTime;
    private Map<String, String> metadata = new HashMap<>();
    private Map<String, String> labels = new HashMap<>();
    private String uiDisplayName;
    private List<ArtifactFileMetadata> artifactFileMetadata = new ArrayList<>();

    private Builder() {}

    /**
     * A build details builder.
     *
     * @return the builder
     */
    public static Builder aBuildDetails() {
      return new Builder();
    }

    /**
     * With number builder.
     *
     * @param number the number
     * @return the builder
     */
    public Builder withNumber(String number) {
      this.number = number;
      return this;
    }

    /**
     * With revision builder.
     *
     * @param revision the revision
     * @return the builder
     */
    public Builder withRevision(String revision) {
      this.revision = revision;
      return this;
    }

    /**
     * With description builder.
     *
     * @param description the description
     * @return the builder
     */
    public Builder withDescription(String description) {
      this.description = description;
      return this;
    }

    /**
     * With build parameters builder.
     *
     * @param buildParameters the build parameters
     * @return the builder
     */
    public Builder withBuildParameters(Map<String, String> buildParameters) {
      this.buildParameters = buildParameters;
      return this;
    }

    /**
     * With artifact path
     */
    public Builder withArtifactPath(String artifactPath) {
      this.artifactPath = artifactPath;
      return this;
    }

    public Builder withBuildUrl(String buildUrl) {
      this.buildUrl = buildUrl;
      return this;
    }

    public Builder withBuildDisplayName(String buildDisplayName) {
      this.buildDisplayName = buildDisplayName;
      return this;
    }

    public Builder withBuildFullDisplayName(String buildFullDisplayName) {
      this.buildFullDisplayName = buildFullDisplayName;
      return this;
    }

    public Builder withArtifactFileSize(String artifactFileSize) {
      this.artifactFileSize = artifactFileSize;
      return this;
    }

    public Builder withMetadata(Map<String, String> metadata) {
      this.metadata = metadata;
      return this;
    }

    public Builder withLabels(Map<String, String> labels) {
      this.labels = labels;
      return this;
    }

    public Builder withUpdateTime(Long updateTime) {
      this.updateTime = updateTime;
      return this;
    }

    public Builder withStatus(BuildStatus status) {
      this.status = status;
      return this;
    }

    public Builder withUiDisplayName(String uiDisplayName) {
      this.uiDisplayName = uiDisplayName;
      return this;
    }

    public Builder withArtifactDownloadMetadata(List<ArtifactFileMetadata> artifactFileMetadata) {
      this.artifactFileMetadata = artifactFileMetadata;
      return this;
    }

    /**
     * But builder.
     *
     * @return the builder
     */
    public Builder but() {
      return aBuildDetails()
          .withNumber(number)
          .withRevision(revision)
          .withDescription(description)
          .withArtifactPath(artifactPath)
          .withBuildParameters(buildParameters)
          .withBuildUrl(buildUrl)
          .withBuildDisplayName(buildDisplayName)
          .withBuildFullDisplayName(buildFullDisplayName)
          .withArtifactFileSize(artifactFileSize)
          .withMetadata(metadata)
          .withStatus(status)
          .withUpdateTime(updateTime)
          .withUiDisplayName(uiDisplayName)
          .withArtifactDownloadMetadata(artifactFileMetadata);
    }

    /**
     * Build build details.
     *
     * @return the build details
     */
    public BuildDetails build() {
      BuildDetails buildDetails = new BuildDetails();
      buildDetails.setNumber(number);
      buildDetails.setRevision(revision);
      buildDetails.setDescription(description);
      buildDetails.setBuildParameters(buildParameters);
      buildDetails.setArtifactPath(artifactPath);
      buildDetails.setBuildUrl(buildUrl);
      buildDetails.setBuildDisplayName(buildDisplayName);
      buildDetails.setBuildFullDisplayName(buildFullDisplayName);
      buildDetails.setArtifactFileSize(artifactFileSize);
      buildDetails.setMetadata(metadata);
      buildDetails.setStatus(status);
      buildDetails.setUpdateTime(updateTime);
      buildDetails.setUiDisplayName(uiDisplayName);
      buildDetails.setArtifactFileMetadataList(artifactFileMetadata);
      return buildDetails;
    }
  }
}
