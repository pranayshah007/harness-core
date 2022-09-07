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
import static software.wings.beans.artifact.ArtifactStreamType.NEXUS;

import static java.lang.String.format;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.beans.EmbeddedUser;
import io.harness.exception.InvalidRequestException;
import io.harness.ff.FeatureFlagService;

import software.wings.utils.RepositoryFormat;
import software.wings.utils.RepositoryType;

import com.fasterxml.jackson.annotation.JsonTypeName;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/**
 * Created by srinivas on 3/31/17.
 */
@OwnedBy(CDC)
@TargetModule(HarnessModule._957_CG_BEANS)
@JsonTypeName("NEXUS")
@Data
@EqualsAndHashCode(callSuper = true)
public class NexusArtifactStream extends ArtifactStream {
  private static final Pattern wingsVariablePattern = Pattern.compile("\\$\\{[^.{}\\s-]*}");
  private String repositoryType;
  public static final String DOCKER_REGISTRY_URL_KEY = "dockerRegistryUrl";
  private String jobname;
  private String groupId;
  private String imageName;
  private List<String> artifactPaths;
  private String dockerPort;
  private String dockerRegistryUrl;
  private String packageName; // field for nuget and npm
  private String repositoryFormat;
  private String extension;
  private String classifier;

  public NexusArtifactStream() {
    super(NEXUS.name());
  }

  @Builder
  public NexusArtifactStream(String uuid, String appId, EmbeddedUser createdBy, long createdAt,
      EmbeddedUser lastUpdatedBy, long lastUpdatedAt, String entityYamlPath, String sourceName, String settingId,
      String name, boolean autoPopulate, String serviceId, boolean metadataOnly, String jobname, String groupId,
      String imageName, List<String> artifactPaths, String dockerPort, String dockerRegistryUrl, String repositoryType,
      String accountId, Set<String> keywords, String packageName, String repositoryFormat, String extension,
      String classifier, boolean sample) {
    super(uuid, appId, createdBy, createdAt, lastUpdatedBy, lastUpdatedAt, entityYamlPath, NEXUS.name(), sourceName,
        settingId, name, autoPopulate, serviceId, metadataOnly, accountId, keywords, sample);
    this.jobname = jobname;
    this.groupId = groupId;
    this.imageName = imageName;
    this.artifactPaths = artifactPaths;
    this.dockerPort = dockerPort;
    this.dockerRegistryUrl = dockerRegistryUrl;
    this.repositoryType = repositoryType;
    this.packageName = packageName;
    this.repositoryFormat = repositoryFormat;
    this.extension = extension;
    this.classifier = classifier;
  }

  // Do not remove this unless UI changes to start using groupId
  public void setGroupId(String groupId) {
    this.groupId = groupId;
    this.imageName = groupId;
  }
  @Override
  public String fetchArtifactDisplayName(String buildNo) {
    if (isNotEmpty(artifactPaths)) {
      return format("%s_%s_%s", getSourceName(), buildNo, new SimpleDateFormat(dateFormat).format(new Date()));
    }
    if (getRepositoryFormat().equals(RepositoryFormat.docker.name())) {
      return format("%s_%s_%s", getJobname() + "/" + getImageName(), buildNo,
          new SimpleDateFormat(dateFormat).format(new Date()));
    } else if (getRepositoryFormat().equals(RepositoryFormat.nuget.name())
        || getRepositoryFormat().equals(RepositoryFormat.npm.name())) {
      return format("%s_%s_%s", getJobname() + "/" + getPackageName(), buildNo,
          new SimpleDateFormat(dateFormat).format(new Date()));
    } else if (getRepositoryFormat().equals(RepositoryFormat.raw.name())) {
      return getJobname() + "/" + getPackageName();
    }
    return null;
  }

  @Override
  public String generateSourceName() {
    StringBuilder builder = new StringBuilder(getJobname());
    if (isNotEmpty(artifactPaths)) {
      builder.append('/').append(getGroupId());
      getArtifactPaths().forEach(artifactPath -> builder.append('/').append(artifactPath));
    } else {
      if (getRepositoryFormat().equals(RepositoryFormat.docker.name())) {
        builder.append('/').append(getImageName());
      } else if (getRepositoryFormat().equals(RepositoryFormat.nuget.name())
          || getRepositoryFormat().equals(RepositoryFormat.npm.name())) {
        builder.append('/').append(getPackageName());
      }
    }
    return builder.toString();
  }

  @Override
  public String fetchRepositoryName() {
    return imageName;
  }

  // TODO: remove this method after migration of old artifact streams
  // TODO: add validations for repository type
  public String getRepositoryType() {
    if (repositoryType != null) {
      return repositoryType;
    }
    if (isEmpty(artifactPaths)) {
      if (isEmpty(packageName)) {
        repositoryType = RepositoryType.docker.name();
      }
    } else {
      repositoryType = RepositoryType.maven.name();
    }
    return repositoryType;
  }

  public String getRepositoryFormat() {
    if (repositoryFormat != null) {
      return repositoryFormat;
    }
    if (isEmpty(getArtifactPaths())) {
      if (isEmpty(getPackageName())) {
        repositoryFormat = RepositoryFormat.docker.name();
      }
    } else {
      repositoryFormat = RepositoryFormat.maven.name();
    }
    return repositoryFormat;
  }

  @Override
  public boolean artifactSourceChanged(ArtifactStream artifactStream) {
    boolean changed = super.artifactSourceChanged(artifactStream);
    if (getRepositoryFormat().equals(RepositoryFormat.docker.name())) {
      return changed || registryUrlChanged(((NexusArtifactStream) artifactStream).dockerRegistryUrl);
    }
    return changed;
  }

  private boolean registryUrlChanged(String dockerRegistryUrl) {
    if (isEmpty(this.dockerRegistryUrl) && isEmpty(dockerRegistryUrl)) {
      return false;
    } else if ((isEmpty(this.dockerRegistryUrl) && isNotEmpty(dockerRegistryUrl))
        || (isNotEmpty(this.dockerRegistryUrl) && isEmpty(dockerRegistryUrl))) {
      return true;
    }
    return !this.dockerRegistryUrl.equals(dockerRegistryUrl);
  }

  @Override
  public ArtifactStreamAttributes fetchArtifactStreamAttributes(FeatureFlagService featureFlagService) {
    return ArtifactStreamAttributes.builder()
        .artifactStreamType(getArtifactStreamType())
        .jobName(jobname)
        .groupId(groupId)
        .repositoryName(jobname)
        .imageName(imageName)
        .artifactName(artifactPaths == null ? "" : artifactPaths.get(0))
        .nexusDockerPort(dockerPort)
        .nexusDockerRegistryUrl(dockerRegistryUrl)
        .repositoryType(getRepositoryType())
        .nexusPackageName(packageName)
        .repositoryFormat(getRepositoryFormat())
        .extension(extension)
        .classifier(classifier)
        .build();
  }

  @Override
  public void validateRequiredFields() {
    if (appId.equals(GLOBAL_APP_ID)) {
      if (isEmpty(repositoryFormat)) {
        throw new InvalidRequestException("Repository Format cannot be empty", USER);
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
        .repositoryType(repositoryType)
        .jobname(jobname)
        .groupId(groupId)
        .artifactPaths(artifactPaths)
        .imageName(imageName)
        .dockerPort(dockerPort)
        .dockerRegistryUrl(dockerRegistryUrl)
        .packageName(packageName)
        .repositoryFormat(repositoryFormat)
        .extension(extension)
        .classifier(classifier)
        .build();
  }

  @Override
  public boolean shouldValidate() {
    return isNotEmpty(extension) || isNotEmpty(classifier);
  }

  @Override
  public boolean checkIfStreamParameterized() {
    String repoFormat = getRepositoryFormat();
    if (RepositoryFormat.maven.name().equals(repoFormat)) {
      if (isNotEmpty(artifactPaths)) {
        return validateParameters(jobname, groupId, artifactPaths.get(0), extension, classifier);
      }
      return validateParameters(jobname, groupId, extension, classifier);
    } else if (RepositoryFormat.nuget.name().equals(repoFormat) || RepositoryFormat.npm.name().equals(repoFormat)) {
      return validateParameters(jobname, packageName);
    } else if (RepositoryFormat.docker.name().equals(repoFormat)) {
      return validateParameters(jobname, imageName, dockerRegistryUrl);
    }
    return false;
  }

  @Override
  public List<String> fetchArtifactStreamParameters() {
    List<String> parameters = new ArrayList<>();
    if (isNotEmpty(jobname)) {
      extractStringsMatchingPattern(jobname, parameters);
    }
    String repoFormat = getRepositoryFormat();
    if (RepositoryFormat.maven.name().equals(repoFormat)) {
      if (isNotEmpty(groupId)) {
        extractStringsMatchingPattern(groupId, parameters);
      }
      if (isNotEmpty(artifactPaths) && isNotEmpty(artifactPaths.get(0))) {
        extractStringsMatchingPattern(artifactPaths.get(0), parameters);
      }
      if (isNotEmpty(extension)) {
        extractStringsMatchingPattern(extension, parameters);
      }
      if (isNotEmpty(classifier)) {
        extractStringsMatchingPattern(classifier, parameters);
      }
    } else if (RepositoryFormat.nuget.name().equals(repoFormat) || RepositoryFormat.npm.name().equals(repoFormat)) {
      if (isNotEmpty(packageName)) {
        extractStringsMatchingPattern(packageName, parameters);
      }
    } else if (RepositoryFormat.docker.name().equals(repoFormat)) {
      if (isNotEmpty(imageName)) {
        extractStringsMatchingPattern(imageName, parameters);
      }
      if (isNotEmpty(dockerRegistryUrl)) {
        extractStringsMatchingPattern(dockerRegistryUrl, parameters);
      }
    }
    return parameters;
  }

  private static void extractStringsMatchingPattern(String parameter, List<String> parameters) {
    String[] paths = parameter.split("/");
    for (String path : paths) {
      Matcher matcher = extractParametersPattern.matcher(path);
      while (matcher.find()) {
        parameters.add(matcher.group(1));
      }
    }
  }

  @Data
  @NoArgsConstructor
  @EqualsAndHashCode(callSuper = true)
  public static class Yaml extends ArtifactStream.Yaml {
    private String repositoryName;
    private String groupId;
    private List<String> artifactPaths;
    private String imageName;
    private String dockerRegistryUrl;
    private String repositoryType;
    private boolean metadataOnly;
    private String packageName;
    private String repositoryFormat;
    private String extension;
    private String classifier;

    public void setRepositoryFormat(String repositoryFormat) {
      if (repositoryFormat != null) {
        this.repositoryFormat = repositoryFormat;
        return;
      }
      if (isEmpty(getArtifactPaths())) {
        if (isEmpty(getPackageName())) {
          this.repositoryFormat = RepositoryFormat.docker.name();
        }
      } else {
        this.repositoryFormat = RepositoryFormat.maven.name();
      }
    }

    @lombok.Builder
    public Yaml(String harnessApiVersion, String serverName, boolean metadataOnly, String repositoryName,
        String groupId, List<String> artifactPaths, String imageName, String dockerRegistryUrl, String repositoryType,
        String packageName, String repositoryFormat, String extension, String classifier) {
      super(NEXUS.name(), harnessApiVersion, serverName);
      this.repositoryName = repositoryName;
      this.groupId = groupId;
      this.artifactPaths = artifactPaths;
      this.imageName = imageName;
      this.dockerRegistryUrl = dockerRegistryUrl;
      this.repositoryType = repositoryType;
      this.metadataOnly = metadataOnly;
      this.packageName = packageName;
      this.repositoryFormat = repositoryFormat;
      this.extension = extension;
      this.classifier = classifier;
    }
  }
}
