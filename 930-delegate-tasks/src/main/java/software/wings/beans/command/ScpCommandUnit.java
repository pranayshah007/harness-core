/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.beans.command;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.expression.Expression.ALLOW_SECRETS;
import static io.harness.logging.CommandExecutionStatus.FAILURE;
import static io.harness.logging.CommandExecutionStatus.RUNNING;
import static io.harness.logging.CommandExecutionStatus.SUCCESS;
import static io.harness.logging.LogLevel.ERROR;
import static io.harness.logging.LogLevel.WARN;

import static software.wings.beans.Log.Builder.aLog;
import static software.wings.beans.command.ScpCommandUnit.ScpFileCategory.ARTIFACTS;

import static java.lang.String.format;
import static java.util.stream.Collectors.toMap;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.delegate.beans.FileBucket;
import io.harness.delegate.beans.artifact.ArtifactFileMetadata;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.WingsException;
import io.harness.expression.Expression;
import io.harness.expression.ExpressionEvaluator;
import io.harness.expression.ExpressionReflectionUtils.NestedAnnotationResolver;
import io.harness.logging.CommandExecutionStatus;
import io.harness.logging.LogLevel;
import io.harness.nexus.NexusRequest;

import software.wings.beans.AppContainer;
import software.wings.beans.BambooConfig;
import software.wings.beans.JenkinsConfig;
import software.wings.beans.artifact.Artifact;
import software.wings.beans.artifact.ArtifactMetadataKeys;
import software.wings.beans.artifact.ArtifactStreamAttributes;
import software.wings.beans.artifact.ArtifactStreamType;
import software.wings.beans.config.NexusConfig;
import software.wings.beans.settings.azureartifacts.AzureArtifactsConfig;
import software.wings.delegatetasks.DelegateLogService;
import software.wings.helpers.ext.azure.devops.AzureArtifactsPackageFileInfo;
import software.wings.helpers.ext.azure.devops.AzureArtifactsService;
import software.wings.helpers.ext.bamboo.BambooService;
import software.wings.helpers.ext.jenkins.BuildDetails;
import software.wings.helpers.ext.jenkins.Jenkins;
import software.wings.helpers.ext.nexus.NexusService;
import software.wings.helpers.ext.nexus.NexusTwoServiceImpl;
import software.wings.service.impl.jenkins.JenkinsUtils;
import software.wings.service.intfc.security.EncryptionService;
import software.wings.service.mappers.artifact.NexusConfigToNexusRequestMapper;
import software.wings.stencils.DataProvider;
import software.wings.stencils.DefaultValue;
import software.wings.stencils.EnumData;
import software.wings.utils.ArtifactType;
import software.wings.utils.RepositoryType;

import com.fasterxml.jackson.annotation.JsonTypeName;
import com.github.reinert.jjschema.Attributes;
import com.github.reinert.jjschema.SchemaIgnore;
import com.google.common.base.MoreObjects;
import com.google.common.collect.Lists;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Stream;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;
import org.mongodb.morphia.annotations.Transient;

/**
 * Created by anubhaw on 7/14/16.
 */
@JsonTypeName("SCP")
@Slf4j
@OwnedBy(CDC)
@TargetModule(HarnessModule._870_CG_ORCHESTRATION)
public class ScpCommandUnit extends SshCommandUnit implements NestedAnnotationResolver {
  private static final String ARTIFACT_STRING = "artifact/";
  private static final String PERIOD_DELIMITER = ".";
  @Inject @Transient private transient DelegateLogService delegateLogService;
  @Inject @Transient private AzureArtifactsService azureArtifactsService;
  @Inject @Transient private EncryptionService encryptionService;
  @Inject @Transient private JenkinsUtils jenkinsUtil;
  @Inject @Transient private BambooService bambooService;
  @Inject @Transient private NexusService nexusService;
  @Inject @Transient private NexusTwoServiceImpl nexusTwoService;

  @Attributes(title = "Source")
  @EnumData(enumDataProvider = ScpCommandDataProvider.class)
  private ScpFileCategory fileCategory;

  @Attributes(title = "Destination Path")
  @DefaultValue("$WINGS_RUNTIME_PATH")
  @Expression(ALLOW_SECRETS)
  private String destinationDirectoryPath;
  private String artifactVariableName = ExpressionEvaluator.DEFAULT_ARTIFACT_VARIABLE_NAME;

  /**
   * Instantiates a new Scp command unit.
   */
  public ScpCommandUnit() {
    super(CommandUnitType.SCP);
  }

  @Override
  protected CommandExecutionStatus executeInternal(ShellCommandExecutionContext context) {
    List<Pair<String, String>> fileIds = Lists.newArrayList();
    Artifact artifact = null;
    FileBucket fileBucket;
    switch (fileCategory) {
      case ARTIFACTS:
        fileBucket = FileBucket.ARTIFACTS;
        ArtifactStreamAttributes artifactStreamAttributes = null;
        if (context.isMultiArtifact()) {
          Map<String, Artifact> multiArtifactMap = context.getMultiArtifactMap();
          Map<String, ArtifactStreamAttributes> artifactStreamAttributesMap = context.getArtifactStreamAttributesMap();
          artifact = multiArtifactMap.get(artifactVariableName);
          if (artifact == null) {
            throw new InvalidRequestException(
                format(
                    "Artifact with variable name [%s] does not exist. Please check artifact variable name provided in Copy Artifact Command.",
                    artifactVariableName),
                WingsException.USER);
          }

          artifactStreamAttributes = artifactStreamAttributesMap.get(artifact.getUuid());

        } else {
          artifactStreamAttributes = context.getArtifactStreamAttributes();
        }
        // TODO: remove artifactStreamType check once code path added for all artifact stream types.

        final String artifactStreamType = artifactStreamAttributes.getArtifactStreamType();
        if (artifactStreamAttributes.isMetadataOnly()) {
          if (artifactStreamType.equalsIgnoreCase(ArtifactStreamType.AMAZON_S3.name())) {
            return context.copyFiles(destinationDirectoryPath, artifactStreamAttributes, context.getAccountId(),
                context.getAppId(), context.getActivityId(), getName(),
                context.getHost() == null ? null : context.getHost().getPublicDns());
          } else if (artifactStreamType.equalsIgnoreCase(ArtifactStreamType.ARTIFACTORY.name())) {
            if (isArtifactTypeAllowedForArtifactory(artifactStreamAttributes.getArtifactType())) {
              return context.copyFiles(destinationDirectoryPath, artifactStreamAttributes, context.getAccountId(),
                  context.getAppId(), context.getActivityId(), getName(),
                  context.getHost() == null ? null : context.getHost().getPublicDns());
            } else {
              log.info("Copy Artifact is not supported for artifact stream type: " + artifactStreamType
                  + " and artifact type: " + artifactStreamAttributes.getArtifactType());
              return CommandExecutionStatus.SUCCESS;
            }
          } else if (artifactStreamType.equalsIgnoreCase(ArtifactStreamType.AZURE_ARTIFACTS.name())) {
            List<AzureArtifactsPackageFileInfo> fileInfos = azureArtifactsService.listFiles(
                (AzureArtifactsConfig) artifactStreamAttributes.getServerSetting().getValue(),
                artifactStreamAttributes.getArtifactServerEncryptedDataDetails(), artifactStreamAttributes,
                artifactStreamAttributes.getMetadata(), false);
            if (isEmpty(fileInfos)) {
              return SUCCESS;
            }

            Map<String, String> metadata = artifactStreamAttributes.getMetadata();
            if (metadata == null) {
              metadata = new HashMap<>();
            }

            for (AzureArtifactsPackageFileInfo fileInfo : fileInfos) {
              metadata.put(ArtifactMetadataKeys.artifactFileName, fileInfo.getName());
              metadata.put(ArtifactMetadataKeys.artifactFileSize, Long.toString(fileInfo.getSize()));
              artifactStreamAttributes.setMetadata(metadata);
              CommandExecutionStatus executionStatus = context.copyFiles(destinationDirectoryPath,
                  artifactStreamAttributes, context.getAccountId(), context.getAppId(), context.getActivityId(),
                  getName(), context.getHost() == null ? null : context.getHost().getPublicDns());
              if (FAILURE == executionStatus) {
                return executionStatus;
              }
            }
            return SUCCESS;
          } else if (artifactStreamType.equalsIgnoreCase(ArtifactStreamType.JENKINS.name())) {
            if (isEmpty(artifactStreamAttributes.getArtifactFileMetadata())) {
              saveExecutionLog(context, WARN, "There are no artifacts to copy");
              return SUCCESS;
            }
            Map<String, String> metadata = artifactStreamAttributes.getMetadata();
            if (metadata == null) {
              throw new InvalidRequestException(
                  "No metadata found for artifact stream. Cannot proceed with copy artifact");
            }
            JenkinsConfig jenkinsConfig = (JenkinsConfig) artifactStreamAttributes.getServerSetting().getValue();
            encryptionService.decrypt(
                jenkinsConfig, artifactStreamAttributes.getArtifactServerEncryptedDataDetails(), false);
            Jenkins jenkins = jenkinsUtil.getJenkins(jenkinsConfig);

            for (ArtifactFileMetadata artifactFileMetadata : artifactStreamAttributes.getArtifactFileMetadata()) {
              metadata.put(ArtifactMetadataKeys.artifactFileName, artifactFileMetadata.getFileName());
              metadata.put(ArtifactMetadataKeys.artifactPath, artifactFileMetadata.getUrl());
              String artifactPathRegex = artifactFileMetadata.getUrl().substring(
                  artifactFileMetadata.getUrl().lastIndexOf(ARTIFACT_STRING) + ARTIFACT_STRING.length());
              metadata.put(ArtifactMetadataKeys.artifactFileSize,
                  String.valueOf(jenkins.getFileSize(artifactStreamAttributes.getJobName(),
                      metadata.get(ArtifactMetadataKeys.buildNo), artifactPathRegex)));
              artifactStreamAttributes.setMetadata(metadata);
              CommandExecutionStatus executionStatus = context.copyFiles(destinationDirectoryPath,
                  artifactStreamAttributes, context.getAccountId(), context.getAppId(), context.getActivityId(),
                  getName(), context.getHost() == null ? null : context.getHost().getPublicDns());
              if (FAILURE == executionStatus) {
                saveExecutionLog(
                    context, ERROR, format("Copy Artifact failed for artifact %s", artifactFileMetadata.getFileName()));
                return executionStatus;
              }
            }
            return SUCCESS;
          } else if (artifactStreamType.equalsIgnoreCase(ArtifactStreamType.BAMBOO.name())) {
            if (isEmpty(artifactStreamAttributes.getArtifactFileMetadata())) {
              saveExecutionLog(context, WARN, "There are no artifacts to copy");
              return SUCCESS;
            }
            Map<String, String> metadata = artifactStreamAttributes.getMetadata();
            if (metadata == null) {
              throw new InvalidRequestException(
                  "No metadata found for artifact stream. Cannot proceed with copy artifact");
            }
            BambooConfig bambooConfig = (BambooConfig) artifactStreamAttributes.getServerSetting().getValue();
            for (ArtifactFileMetadata artifactFileMetadata : artifactStreamAttributes.getArtifactFileMetadata()) {
              metadata.put(ArtifactMetadataKeys.artifactFileName, artifactFileMetadata.getFileName());
              metadata.put(ArtifactMetadataKeys.artifactPath, artifactFileMetadata.getUrl());
              metadata.put(ArtifactMetadataKeys.artifactFileSize,
                  String.valueOf(bambooService.getFileSize(bambooConfig,
                      artifactStreamAttributes.getArtifactServerEncryptedDataDetails(),
                      artifactFileMetadata.getFileName(), artifactFileMetadata.getUrl())));
              artifactStreamAttributes.setMetadata(metadata);
              CommandExecutionStatus executionStatus = context.copyFiles(destinationDirectoryPath,
                  artifactStreamAttributes, context.getAccountId(), context.getAppId(), context.getActivityId(),
                  getName(), context.getHost() == null ? null : context.getHost().getPublicDns());
              if (FAILURE == executionStatus) {
                saveExecutionLog(
                    context, ERROR, format("Copy Artifact failed for artifact %s", artifactFileMetadata.getFileName()));
                return executionStatus;
              }
            }
            return SUCCESS;
          } else if (artifactStreamType.equalsIgnoreCase(ArtifactStreamType.NEXUS.name())) {
            NexusConfig nexusConfig = (NexusConfig) artifactStreamAttributes.getServerSetting().getValue();
            NexusRequest nexusRequest = NexusConfigToNexusRequestMapper.toNexusRequest(
                nexusConfig, encryptionService, artifactStreamAttributes.getArtifactServerEncryptedDataDetails());

            if (isEmpty(artifactStreamAttributes.getArtifactFileMetadata())) {
              // Try once more of to get download url
              try {
                List<BuildDetails> buildDetailsList;
                if (artifactStreamAttributes.getRepositoryType() != null
                    && artifactStreamAttributes.getRepositoryType().equals(RepositoryType.maven.name())) {
                  buildDetailsList = nexusTwoService.getVersion(nexusRequest,
                      artifactStreamAttributes.getRepositoryName(), artifactStreamAttributes.getGroupId(),
                      artifactStreamAttributes.getArtifactName(), artifactStreamAttributes.getExtension(),
                      artifactStreamAttributes.getClassifier(), artifactStreamAttributes.getMetadata().get("buildNo"));
                } else {
                  buildDetailsList = Collections.singletonList(
                      nexusTwoService.getVersion(artifactStreamAttributes.getRepositoryFormat(), nexusRequest,
                          artifactStreamAttributes.getRepositoryName(), artifactStreamAttributes.getNexusPackageName(),
                          artifactStreamAttributes.getMetadata().get("buildNo")));
                }

                if (isEmpty(buildDetailsList) || isEmpty(buildDetailsList.get(0).getArtifactFileMetadataList())) {
                  saveExecutionLog(context, WARN, "There are no artifacts to copy");
                  return SUCCESS;
                } else {
                  artifactStreamAttributes.setArtifactFileMetadata(
                      buildDetailsList.get(0).getArtifactFileMetadataList());
                  log.info("Found metadata for artifact: {}", buildDetailsList.get(0).getUiDisplayName());
                }
              } catch (IOException ioException) {
                log.warn("Exception encountered while fetching download url for artifact", ioException);
              }
            }

            Map<String, String> metadata = artifactStreamAttributes.getMetadata();
            if (metadata == null) {
              throw new InvalidRequestException(
                  "No metadata found for artifact stream. Cannot proceed with copy artifact");
            }
            for (ArtifactFileMetadata artifactFileMetadata : artifactStreamAttributes.getArtifactFileMetadata()) {
              // filter artifacts based on extension and classifier for nexus parameterized artifact stream.
              // No op for non-parameterized artifact stream because we have already filtered artifactFileMetadata
              // before we reach here
              if ((isNotEmpty(artifactStreamAttributes.getExtension())
                      && !artifactFileMetadata.getFileName().endsWith(
                          PERIOD_DELIMITER + artifactStreamAttributes.getExtension()))
                  || (isNotEmpty(artifactStreamAttributes.getClassifier())
                      && !artifactFileMetadata.getFileName().contains(artifactStreamAttributes.getClassifier()))) {
                continue;
              }

              metadata.put(ArtifactMetadataKeys.artifactFileName, artifactFileMetadata.getFileName());
              metadata.put(ArtifactMetadataKeys.artifactPath, artifactFileMetadata.getUrl());
              metadata.put(ArtifactMetadataKeys.artifactFileSize,
                  String.valueOf(nexusService.getFileSize(
                      nexusRequest, artifactFileMetadata.getFileName(), artifactFileMetadata.getUrl())));
              artifactStreamAttributes.setMetadata(metadata);
              CommandExecutionStatus executionStatus = context.copyFiles(destinationDirectoryPath,
                  artifactStreamAttributes, context.getAccountId(), context.getAppId(), context.getActivityId(),
                  getName(), context.getHost() == null ? null : context.getHost().getPublicDns());
              if (FAILURE == executionStatus) {
                saveExecutionLog(
                    context, ERROR, format("Copy Artifact failed for artifact %s", artifactFileMetadata.getFileName()));
                return executionStatus;
              }
            }
            return SUCCESS;
          } else if (artifactStreamType.equalsIgnoreCase(ArtifactStreamType.CUSTOM.name())) {
            saveExecutionLog(context, ERROR, "Copy Artifact is not supported for Custom Repository artifacts");
            throw new InvalidRequestException("Copy Artifact is not supported for Custom Repository artifacts");
          }
        } else {
          if (context.isMultiArtifact()) {
            if (artifact != null && isNotEmpty(artifact.getArtifactFiles())) {
              artifact.getArtifactFiles().forEach(
                  artifactFile -> fileIds.add(Pair.of(artifactFile.getFileUuid(), null)));
            } else {
              throw new InvalidRequestException(
                  format("No artifact files to copy for artifact %s", artifactVariableName));
            }
          } else {
            context.getArtifactFiles().forEach(artifactFile -> fileIds.add(Pair.of(artifactFile.getFileUuid(), null)));
          }
        }
        break;
      case APPLICATION_STACK:
        fileBucket = FileBucket.PLATFORMS;
        AppContainer appContainer = context.getAppContainer();
        fileIds.add(Pair.of(appContainer.getFileUuid(), null));
        break;
      default:
        saveExecutionLog(context, ERROR, "Unsupported file category for copy step");
        throw new InvalidRequestException("Unsupported file category for copy step");
    }
    return context.copyGridFsFiles(destinationDirectoryPath, fileBucket, fileIds);
  }

  private boolean isArtifactTypeAllowedForArtifactory(ArtifactType artifactType) {
    return ArtifactType.JAR == artifactType || ArtifactType.TAR == artifactType || ArtifactType.WAR == artifactType
        || ArtifactType.ZIP == artifactType || ArtifactType.IIS == artifactType || ArtifactType.IIS_APP == artifactType
        || ArtifactType.IIS_VirtualDirectory == artifactType || ArtifactType.RPM == artifactType
        || ArtifactType.OTHER == artifactType || ArtifactType.NUGET == artifactType;
  }

  private void saveExecutionLog(ShellCommandExecutionContext context, LogLevel logLevel, String line) {
    delegateLogService.save(context.getAccountId(),
        aLog()
            .appId(context.getAppId())
            .activityId(context.getActivityId())
            .hostName(context.getHost() == null ? null : context.getHost().getPublicDns())
            .logLevel(logLevel)
            .commandUnitName(getName())
            .logLine(line)
            .executionResult(RUNNING)
            .build());
  }

  @SchemaIgnore
  @Override
  public boolean isArtifactNeeded() {
    return fileCategory != null && fileCategory == ARTIFACTS;
  }

  @SchemaIgnore
  @Override
  public void updateServiceArtifactVariableNames(Set<String> serviceArtifactVariableNames) {
    if (isArtifactNeeded()) {
      serviceArtifactVariableNames.add(getArtifactVariableName());
    }
  }

  /**
   * Gets file category.
   *
   * @return the file category
   */
  @SchemaIgnore
  public ScpFileCategory getFileCategory() {
    return fileCategory;
  }

  /**
   * Sets file category.
   *
   * @param fileCategory the file category
   */
  public void setFileCategory(ScpFileCategory fileCategory) {
    this.fileCategory = fileCategory;
  }

  /**
   * Gets destination directory path.
   *
   * @return the destination directory path
   */
  public String getDestinationDirectoryPath() {
    return destinationDirectoryPath;
  }

  /**
   * Sets destination directory path.
   *
   * @param destinationDirectoryPath the destination directory path
   */
  public void setDestinationDirectoryPath(String destinationDirectoryPath) {
    this.destinationDirectoryPath = destinationDirectoryPath;
  }

  public String getArtifactVariableName() {
    return artifactVariableName == null ? ExpressionEvaluator.DEFAULT_ARTIFACT_VARIABLE_NAME : artifactVariableName;
  }

  public void setArtifactVariableName(String artifactVariableName) {
    this.artifactVariableName = artifactVariableName;
  }

  @Override
  public int hashCode() {
    return Objects.hash(fileCategory, destinationDirectoryPath, getArtifactVariableName());
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    if (obj == null || getClass() != obj.getClass()) {
      return false;
    }
    final ScpCommandUnit other = (ScpCommandUnit) obj;
    return this.fileCategory == other.fileCategory
        && Objects.equals(this.destinationDirectoryPath, other.destinationDirectoryPath)
        && Objects.equals(this.getArtifactVariableName(), other.getArtifactVariableName());
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("fileCategory", fileCategory)
        .add("destinationDirectoryPath", destinationDirectoryPath)
        .add("artifactVariableName", this.getArtifactVariableName())
        .toString();
  }

  /**
   * The enum Scp file category.
   */
  public enum ScpFileCategory {
    /**
     * Artifacts scp file category.
     */
    ARTIFACTS("Application Artifacts"),
    /**
     * The Application stack.
     */
    APPLICATION_STACK("Application Stack");

    private String name;

    ScpFileCategory(String name) {
      this.name = name;
    }

    /**
     * Gets name.
     *
     * @return the name
     */
    public String getName() {
      return name;
    }
  }

  /**
   * The type Builder.
   */
  public static final class Builder {
    private ScpFileCategory fileCategory;
    private String destinationDirectoryPath;
    private String name;
    private CommandUnitType commandUnitType;
    private CommandExecutionStatus commandExecutionStatus;
    private boolean artifactNeeded;
    private String artifactVariableName;

    private Builder() {}

    /**
     * A scp command unit builder.
     *
     * @return the builder
     */
    public static Builder aScpCommandUnit() {
      return new Builder();
    }

    /**
     * With file category builder.
     *
     * @param fileCategory the file category
     * @return the builder
     */
    public Builder withFileCategory(ScpFileCategory fileCategory) {
      this.fileCategory = fileCategory;
      return this;
    }

    /**
     * With destination directory path builder.
     *
     * @param destinationDirectoryPath the destination directory path
     * @return the builder
     */
    public Builder withDestinationDirectoryPath(String destinationDirectoryPath) {
      this.destinationDirectoryPath = destinationDirectoryPath;
      return this;
    }

    /**
     * With name builder.
     *
     * @param name the name
     * @return the builder
     */
    public Builder withName(String name) {
      this.name = name;
      return this;
    }

    /**
     * With command unit type builder.
     *
     * @param commandUnitType the command unit type
     * @return the builder
     */
    public Builder withCommandUnitType(CommandUnitType commandUnitType) {
      this.commandUnitType = commandUnitType;
      return this;
    }

    /**
     * With execution result builder.
     *
     * @param commandExecutionStatus the execution result
     * @return the builder
     */
    public Builder withExecutionResult(CommandExecutionStatus commandExecutionStatus) {
      this.commandExecutionStatus = commandExecutionStatus;
      return this;
    }

    /**
     * With artifact needed builder.
     *
     * @param artifactNeeded the artifact needed
     * @return the builder
     */
    public Builder withArtifactNeeded(boolean artifactNeeded) {
      this.artifactNeeded = artifactNeeded;
      return this;
    }

    public Builder withArtifactVariableName(String artifactVariableName) {
      this.artifactVariableName = artifactVariableName;
      return this;
    }

    /**
     * But builder.
     *
     * @return the builder
     */
    public Builder but() {
      return aScpCommandUnit()
          .withFileCategory(fileCategory)
          .withDestinationDirectoryPath(destinationDirectoryPath)
          .withName(name)
          .withCommandUnitType(commandUnitType)
          .withExecutionResult(commandExecutionStatus)
          .withArtifactNeeded(artifactNeeded)
          .withArtifactVariableName(artifactVariableName);
    }

    /**
     * Build scp command unit.
     *
     * @return the scp command unit
     */
    public ScpCommandUnit build() {
      ScpCommandUnit scpCommandUnit = new ScpCommandUnit();
      scpCommandUnit.setFileCategory(fileCategory);
      scpCommandUnit.setDestinationDirectoryPath(destinationDirectoryPath);
      scpCommandUnit.setName(name);
      scpCommandUnit.setCommandUnitType(commandUnitType);
      scpCommandUnit.setCommandExecutionStatus(commandExecutionStatus);
      scpCommandUnit.setArtifactNeeded(artifactNeeded);
      scpCommandUnit.setArtifactVariableName(artifactVariableName);
      return scpCommandUnit;
    }
  }

  /**
   * The type Scp command data provider.
   */
  @Singleton
  public static class ScpCommandDataProvider implements DataProvider {
    @Override
    public Map<String, String> getData(String appId, Map<String, String> params) {
      return Stream.of(ScpFileCategory.values()).collect(toMap(ScpFileCategory::name, ScpFileCategory::getName));
    }
  }

  @Data
  @EqualsAndHashCode(callSuper = true)
  @JsonTypeName("SCP")
  public static class Yaml extends SshCommandUnit.Yaml {
    // maps to fileCategory
    private String source;
    private String destinationDirectoryPath;
    private String artifactVariableName;

    public Yaml() {
      super(CommandUnitType.SCP.name());
    }

    @lombok.Builder
    public Yaml(String name, String deploymentType, String source, String destinationDirectoryPath,
        String artifactVariableName) {
      super(name, CommandUnitType.SCP.name(), deploymentType);
      this.source = source;
      this.destinationDirectoryPath = destinationDirectoryPath;
      this.artifactVariableName = artifactVariableName;
    }
  }
}
