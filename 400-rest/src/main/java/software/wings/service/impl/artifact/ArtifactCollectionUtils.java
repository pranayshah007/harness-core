/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl.artifact;

import static io.harness.annotations.dev.HarnessModule._870_CG_ORCHESTRATION;
import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.beans.FeatureName.ARTIFACT_STREAM_DELEGATE_TIMEOUT;
import static io.harness.data.encoding.EncodingUtils.decodeBase64;
import static io.harness.data.encoding.EncodingUtils.encodeBase64;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.exception.WingsException.USER;
import static io.harness.mongo.MongoConfig.NO_LIMIT;
import static io.harness.validation.Validator.notNullCheck;

import static software.wings.beans.CGConstants.GLOBAL_APP_ID;
import static software.wings.beans.artifact.ArtifactStreamType.ACR;
import static software.wings.beans.artifact.ArtifactStreamType.AMAZON_S3;
import static software.wings.beans.artifact.ArtifactStreamType.AMI;
import static software.wings.beans.artifact.ArtifactStreamType.ARTIFACTORY;
import static software.wings.beans.artifact.ArtifactStreamType.AZURE_ARTIFACTS;
import static software.wings.beans.artifact.ArtifactStreamType.AZURE_MACHINE_IMAGE;
import static software.wings.beans.artifact.ArtifactStreamType.BAMBOO;
import static software.wings.beans.artifact.ArtifactStreamType.CUSTOM;
import static software.wings.beans.artifact.ArtifactStreamType.DOCKER;
import static software.wings.beans.artifact.ArtifactStreamType.ECR;
import static software.wings.beans.artifact.ArtifactStreamType.GCR;
import static software.wings.beans.artifact.ArtifactStreamType.GCS;
import static software.wings.beans.artifact.ArtifactStreamType.JENKINS;
import static software.wings.beans.artifact.ArtifactStreamType.NEXUS;
import static software.wings.beans.artifact.ArtifactStreamType.SFTP;
import static software.wings.beans.artifact.ArtifactStreamType.SMB;
import static software.wings.expression.SecretFunctor.Mode.CASCADING;
import static software.wings.helpers.ext.jenkins.BuildDetails.Builder.aBuildDetails;
import static software.wings.persistence.artifact.Artifact.Builder.anArtifact;
import static software.wings.service.impl.ArtifactoryBuildServiceImpl.MANUAL_PULL_ARTIFACTORY_LIMIT;
import static software.wings.service.intfc.BuildService.ARTIFACT_RETENTION_SIZE;

import static java.lang.String.format;
import static java.util.Arrays.asList;
import static java.util.Optional.ofNullable;
import static java.util.stream.Collectors.toList;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.artifact.ArtifactCollectionResponseHandler;
import io.harness.artifact.ArtifactUtilities;
import io.harness.artifacts.docker.service.DockerRegistryUtils;
import io.harness.beans.ArtifactMetadata;
import io.harness.beans.Cd1SetupFields;
import io.harness.beans.DelegateTask;
import io.harness.beans.DelegateTask.DelegateTaskBuilder;
import io.harness.beans.FeatureName;
import io.harness.configuration.DeployMode;
import io.harness.data.structure.EmptyPredicate;
import io.harness.delegate.beans.DelegateTaskRank;
import io.harness.delegate.beans.TaskData;
import io.harness.delegate.beans.TaskData.TaskDataBuilder;
import io.harness.eraro.ErrorCode;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.WingsException;
import io.harness.expression.ExpressionEvaluator;
import io.harness.ff.FeatureFlagService;
import io.harness.k8s.model.ImageDetails;
import io.harness.k8s.model.ImageDetails.ImageDetailsBuilder;
import io.harness.network.Http;
import io.harness.persistence.HIterator;
import io.harness.security.encryption.EncryptedDataDetail;

import software.wings.annotation.EncryptableSetting;
import software.wings.app.MainConfiguration;
import software.wings.beans.AwsConfig;
import software.wings.beans.AzureConfig;
import software.wings.beans.DockerConfig;
import software.wings.beans.EcrConfig;
import software.wings.beans.GcpConfig;
import software.wings.beans.Service;
import software.wings.beans.SettingAttribute;
import software.wings.beans.TaskType;
import software.wings.beans.artifact.AcrArtifactStream;
import software.wings.beans.artifact.ArtifactMetadataKeys;
import software.wings.beans.artifact.ArtifactStream;
import software.wings.beans.artifact.ArtifactStreamAttributes;
import software.wings.beans.artifact.ArtifactStreamCollectionStatus;
import software.wings.beans.artifact.ArtifactoryArtifactStream;
import software.wings.beans.artifact.CustomArtifactStream;
import software.wings.beans.artifact.CustomArtifactStream.Action;
import software.wings.beans.artifact.CustomArtifactStream.Script;
import software.wings.beans.artifact.DockerArtifactStream;
import software.wings.beans.artifact.EcrArtifactStream;
import software.wings.beans.artifact.GcrArtifactStream;
import software.wings.beans.artifact.NexusArtifactStream;
import software.wings.beans.config.ArtifactoryConfig;
import software.wings.beans.config.NexusConfig;
import software.wings.beans.template.TemplateHelper;
import software.wings.beans.template.artifactsource.CustomRepositoryMapping.AttributeMapping;
import software.wings.delegatetasks.buildsource.BuildCollectParameters;
import software.wings.delegatetasks.buildsource.BuildSourceParameters;
import software.wings.delegatetasks.buildsource.BuildSourceParameters.BuildSourceParametersBuilder;
import software.wings.delegatetasks.buildsource.BuildSourceParameters.BuildSourceRequestType;
import software.wings.expression.SecretFunctor;
import software.wings.helpers.ext.azure.AzureDelegateHelperService;
import software.wings.helpers.ext.ecr.EcrClassicService;
import software.wings.helpers.ext.jenkins.BuildDetails;
import software.wings.persistence.artifact.Artifact;
import software.wings.persistence.artifact.Artifact.Builder;
import software.wings.service.impl.AwsHelperService;
import software.wings.service.intfc.ArtifactService;
import software.wings.service.intfc.ArtifactStreamService;
import software.wings.service.intfc.ArtifactStreamServiceBindingService;
import software.wings.service.intfc.SettingsService;
import software.wings.service.intfc.aws.manager.AwsEcrHelperServiceManager;
import software.wings.service.intfc.security.ManagerDecryptionService;
import software.wings.service.intfc.security.SecretManager;
import software.wings.settings.SettingValue;
import software.wings.settings.SettingVariableTypes;
import software.wings.utils.ArtifactType;
import software.wings.utils.DelegateArtifactCollectionUtils;
import software.wings.utils.RepositoryType;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;

@TargetModule(_870_CG_ORCHESTRATION)
@OwnedBy(CDC)
@Singleton
@Slf4j
public class ArtifactCollectionUtils {
  @Inject private SettingsService settingsService;
  @Inject private ArtifactStreamService artifactStreamService;
  @Inject private ManagerDecryptionService managerDecryptionService;
  @Inject private SecretManager secretManager;
  @Inject private AwsHelperService awsHelperService;
  @Inject private AzureDelegateHelperService azureDelegateHelperService;
  @Inject private EcrClassicService ecrClassicService;
  @Inject private AwsEcrHelperServiceManager awsEcrHelperServiceManager;
  @Inject private ExpressionEvaluator evaluator;
  @Inject private ArtifactStreamServiceBindingService artifactStreamServiceBindingService;
  @Inject private ArtifactService artifactService;
  @Inject private FeatureFlagService featureFlagService;
  @Inject private ArtifactStreamPTaskHelper artifactStreamPTaskHelper;
  @Inject private MainConfiguration mainConfiguration;

  public static final Long DELEGATE_QUEUE_TIMEOUT = Duration.ofSeconds(60).toMillis();

  static final List<String> metadataOnlyStreams = Collections.unmodifiableList(
      asList(DOCKER.name(), ECR.name(), GCR.name(), NEXUS.name(), AMI.name(), ACR.name(), AMAZON_S3.name(), GCS.name(),
          SMB.name(), SFTP.name(), AZURE_ARTIFACTS.name(), AZURE_MACHINE_IMAGE.name(), CUSTOM.name()));

  public long getDelegateQueueTimeout(String accountId) {
    long timeout = DELEGATE_QUEUE_TIMEOUT;
    if (featureFlagService.isEnabled(ARTIFACT_STREAM_DELEGATE_TIMEOUT, accountId)) {
      timeout = Duration.ofSeconds(60).toMillis();
    }
    if (DeployMode.isOnPrem(mainConfiguration.getDeployMode().name())) {
      timeout = Duration.ofSeconds(60).toMillis();
    }
    return System.currentTimeMillis() + timeout;
  }

  public Artifact getArtifact(ArtifactStream artifactStream, BuildDetails buildDetails) {
    String accountId = null;
    String settingId = artifactStream.getSettingId();
    if (settingId != null) {
      SettingAttribute settingAttribute = settingsService.get(settingId);
      if (settingAttribute == null) {
        throw new InvalidRequestException(
            format("Setting attribute not found for artifact stream %s", artifactStream.getName()), USER);
      }
      accountId = settingAttribute.getAccountId();
    }

    Builder builder = anArtifact()
                          .withAppId(artifactStream.fetchAppId())
                          .withArtifactStreamId(artifactStream.getUuid())
                          .withArtifactSourceName(artifactStream.getSourceName())
                          .withDisplayName(getDisplayName(artifactStream, buildDetails))
                          .withDescription(buildDetails.getDescription())
                          .withMetadata(getMetadata(artifactStream, buildDetails))
                          .withRevision(buildDetails.getRevision())
                          .withArtifactStreamType(artifactStream.getArtifactStreamType())
                          .withUiDisplayName(buildDetails.getUiDisplayName())
                          .withArtifactDownloadMetadata(buildDetails.getArtifactFileMetadataList());
    if (settingId != null) {
      builder.withSettingId(settingId);
    }
    if (accountId != null) {
      builder.withAccountId(accountId);
    }
    return builder.build();
  }

  private String getDisplayName(ArtifactStream artifactStream, BuildDetails buildDetails) {
    if (isNotEmpty(buildDetails.getBuildDisplayName())) {
      return buildDetails.getBuildDisplayName();
    }
    if (artifactStream.getArtifactStreamType().equals(ARTIFACTORY.name())) {
      if (buildDetails.getArtifactPath() != null) {
        return artifactStream.fetchArtifactDisplayName(buildDetails.getArtifactPath());
      }
    } else if (artifactStream.getArtifactStreamType().equals(AMAZON_S3.name())) {
      return artifactStream.fetchArtifactDisplayName(ofNullable(buildDetails.getArtifactPath()).orElse(""));
    }

    return artifactStream.fetchArtifactDisplayName(buildDetails.getNumber());
  }

  private ArtifactMetadata getMetadata(ArtifactStream artifactStream, BuildDetails buildDetails) {
    String artifactStreamType = artifactStream.getArtifactStreamType();

    ArtifactMetadata metadata =
        buildDetails.getMetadata() == null ? new ArtifactMetadata() : new ArtifactMetadata(buildDetails.getMetadata());
    if (artifactStreamType.equals(ARTIFACTORY.name())) {
      if (buildDetails.getArtifactPath() != null) {
        metadata.put(ArtifactMetadataKeys.artifactPath, buildDetails.getArtifactPath());
        metadata.put(ArtifactMetadataKeys.artifactFileName,
            buildDetails.getNumber().substring(buildDetails.getNumber().lastIndexOf('/') + 1));
        if (buildDetails.getArtifactFileSize() != null) {
          metadata.put(ArtifactMetadataKeys.artifactFileSize, buildDetails.getArtifactFileSize());
        }
      }
      if (isNotEmpty(buildDetails.getBuildUrl())) {
        metadata.put(ArtifactMetadataKeys.url, buildDetails.getBuildUrl());
      }
      metadata.put(ArtifactMetadataKeys.buildNo, buildDetails.getNumber());
      return metadata;
    } else if (artifactStreamType.equals(AMAZON_S3.name()) || artifactStreamType.equals(GCS.name())) {
      Map<String, String> buildParameters = buildDetails.getBuildParameters();
      metadata.put(ArtifactMetadataKeys.artifactPath, buildParameters.get(ArtifactMetadataKeys.artifactPath));
      metadata.put(ArtifactMetadataKeys.artifactFileName,
          buildDetails.getNumber().substring(buildDetails.getNumber().lastIndexOf('/') + 1));
      metadata.put(ArtifactMetadataKeys.buildNo, buildParameters.get(ArtifactMetadataKeys.buildNo));
      metadata.put(ArtifactMetadataKeys.bucketName, buildParameters.get(ArtifactMetadataKeys.bucketName));
      metadata.put(ArtifactMetadataKeys.key, buildParameters.get(ArtifactMetadataKeys.key));
      metadata.put(ArtifactMetadataKeys.url, buildParameters.get(ArtifactMetadataKeys.url));
      metadata.put(ArtifactMetadataKeys.artifactFileSize, buildParameters.get(ArtifactMetadataKeys.artifactFileSize));
      return metadata;
    } else if (artifactStreamType.equals(JENKINS.name()) || artifactStreamType.equals(BAMBOO.name())) {
      metadata.putAll(buildDetails.getBuildParameters());
      metadata.put(ArtifactMetadataKeys.buildNo, buildDetails.getNumber());
      metadata.put(ArtifactMetadataKeys.buildFullDisplayName, buildDetails.getBuildFullDisplayName());
      metadata.put(ArtifactMetadataKeys.url, buildDetails.getBuildUrl());
      return metadata;
    } else if (artifactStreamType.equals(SMB.name()) || artifactStreamType.equals(SFTP.name())) {
      metadata.putAll(buildDetails.getBuildParameters());
      metadata.put(ArtifactMetadataKeys.buildNo, buildDetails.getNumber());
      metadata.put(ArtifactMetadataKeys.artifactPath, metadata.get(ArtifactMetadataKeys.artifactPath));
      metadata.put(ArtifactMetadataKeys.buildFullDisplayName, buildDetails.getBuildFullDisplayName());
      metadata.put(ArtifactMetadataKeys.url, buildDetails.getBuildUrl());
      return metadata;
    } else if (artifactStreamType.equals(NEXUS.name())) {
      metadata.put(ArtifactMetadataKeys.buildNo, buildDetails.getNumber());
      if (isNotEmpty(buildDetails.getBuildUrl())) {
        metadata.put(ArtifactMetadataKeys.url, buildDetails.getBuildUrl());
      }
      if (isNotEmpty(buildDetails.getArtifactFileMetadataList())) {
        metadata.put(
            ArtifactMetadataKeys.artifactFileName, buildDetails.getArtifactFileMetadataList().get(0).getFileName());
      }
      return metadata;
    }

    metadata.put(ArtifactMetadataKeys.buildNo, buildDetails.getNumber());
    return metadata;
  }

  public ImageDetails fetchContainerImageDetails(Artifact artifact, String workflowExecutionId) {
    ArtifactStream artifactStream = artifactStreamService.get(artifact.getArtifactStreamId());
    if (artifactStream == null) {
      throw new InvalidRequestException("Artifact Stream [" + artifact.getArtifactSourceName() + "] was deleted");
    }

    ImageDetails imageDetails = getDockerImageDetailsInternal(artifactStream, workflowExecutionId);
    imageDetails.setTag(artifact.getBuildNo());
    return imageDetails;
  }

  public String getDockerConfig(String artifactStreamId) {
    return getDockerConfig(artifactStreamService.get(artifactStreamId));
  }

  private String getDockerConfig(ArtifactStream artifactStream) {
    if (artifactStream == null) {
      return "";
    }

    ImageDetails imageDetails = getDockerImageDetailsInternal(artifactStream, null);
    if (isNotBlank(imageDetails.getRegistryUrl()) && isNotBlank(imageDetails.getUsername())
        && isNotBlank(imageDetails.getPassword())) {
      return encodeBase64(DockerRegistryUtils.getDockerRegistryCredentials(imageDetails));
    }
    return "";
  }

  public DelegateTaskBuilder fetchCustomDelegateTask(String waitId, ArtifactStream artifactStream,
      ArtifactStreamAttributes artifactStreamAttributes, boolean isCollection,
      BuildSourceRequestType buildSourceRequestType) {
    return fetchCustomDelegateTask(
        waitId, artifactStream, artifactStreamAttributes, isCollection, buildSourceRequestType, null);
  }

  public DelegateTaskBuilder fetchCustomDelegateTask(String waitId, ArtifactStream artifactStream,
      ArtifactStreamAttributes artifactStreamAttributes, boolean isCollection,
      BuildSourceRequestType buildSourceRequestType, BuildCollectParameters buildCollectParameters) {
    String accountId = artifactStreamAttributes.getAccountId();
    DelegateTaskBuilder delegateTaskBuilder =
        DelegateTask.builder().waitId(waitId).expiry(getDelegateQueueTimeout(accountId));
    if (featureFlagService.isEnabled(FeatureName.ARTIFACT_STREAM_DELEGATE_SCOPING, artifactStream.getAccountId())) {
      delegateTaskBuilder.setupAbstraction(Cd1SetupFields.APP_ID_FIELD, artifactStream.getAppId());
    } else {
      delegateTaskBuilder.setupAbstraction(Cd1SetupFields.APP_ID_FIELD, GLOBAL_APP_ID);
    }

    final TaskDataBuilder dataBuilder = TaskData.builder().async(true).taskType(TaskType.BUILD_SOURCE_TASK.name());

    BuildSourceParametersBuilder buildSourceParametersBuilder =
        BuildSourceParameters.builder()
            .accountId(artifactStreamAttributes.getAccountId())
            .appId(artifactStream.fetchAppId())
            .artifactStreamAttributes(artifactStreamAttributes)
            .artifactStreamType(artifactStream.getArtifactStreamType())
            .buildSourceRequestType(buildSourceRequestType)
            .limit(ArtifactCollectionUtils.getLimit(
                artifactStream.getArtifactStreamType(), buildSourceRequestType, isCollection))
            .buildCollectParameters(buildCollectParameters)
            .isCollection(isCollection);

    if (isCollection) {
      buildSourceParametersBuilder.savedBuildDetailsKeys(getArtifactsKeys(artifactStream, artifactStreamAttributes));
    }
    List<String> tags = ((CustomArtifactStream) artifactStream).getTags();
    if (isNotEmpty(tags)) {
      // To remove if any empty tags in case saved for custom artifact stream
      tags = tags.stream().filter(EmptyPredicate::isNotEmpty).distinct().collect(toList());
    }

    // Set timeout. Labels are not fetched for CUSTOM artifact streams.
    long timeout = isEmpty(artifactStreamAttributes.getCustomScriptTimeout())
        ? Long.parseLong(CustomArtifactStream.DEFAULT_SCRIPT_TIME_OUT)
        : Long.parseLong(artifactStreamAttributes.getCustomScriptTimeout());
    dataBuilder.parameters(new Object[] {buildSourceParametersBuilder.build()})
        .timeout(Duration.ofSeconds(timeout).toMillis());
    delegateTaskBuilder.tags(tags);
    delegateTaskBuilder.rank(DelegateTaskRank.OPTIONAL);
    delegateTaskBuilder.accountId(accountId);
    delegateTaskBuilder.data(dataBuilder.build());

    return delegateTaskBuilder;
  }

  private ImageDetails getDockerImageDetailsInternal(ArtifactStream artifactStream, String workflowExecutionId) {
    log.info("Entering getDockerImageDetailsInternal: artifactStreamName {}", artifactStream.getName());

    try {
      ImageDetailsBuilder imageDetailsBuilder = ImageDetails.builder();
      String settingId = artifactStream.getSettingId();
      if (artifactStream.getArtifactStreamType().equals(DOCKER.name())) {
        DockerArtifactStream dockerArtifactStream = (DockerArtifactStream) artifactStream;
        DockerConfig dockerConfig = (DockerConfig) settingsService.get(settingId).getValue();
        managerDecryptionService.decrypt(
            dockerConfig, secretManager.getEncryptionDetails(dockerConfig, null, workflowExecutionId));

        String domainName = Http.getDomainWithPort(dockerConfig.getDockerRegistryUrl());
        String imageName = dockerArtifactStream.getImageName();

        if (dockerConfig.hasCredentials()) {
          imageDetailsBuilder.name(imageName)
              .sourceName(dockerArtifactStream.getSourceName())
              .registryUrl(dockerConfig.getDockerRegistryUrl())
              .username(dockerConfig.getUsername())
              .password(new String(dockerConfig.getPassword()))
              .domainName(domainName);
        } else {
          imageDetailsBuilder.name(imageName)
              .sourceName(dockerArtifactStream.getSourceName())
              .registryUrl(dockerConfig.getDockerRegistryUrl())
              .domainName(domainName);
        }
      } else if (artifactStream.getArtifactStreamType().equals(ECR.name())) {
        EcrArtifactStream ecrArtifactStream = (EcrArtifactStream) artifactStream;
        String imageUrl = getImageUrl(ecrArtifactStream, workflowExecutionId);
        // name should be 830767422336.dkr.ecr.us-east-1.amazonaws.com/todolist
        // sourceName should be todolist
        // registryUrl should be https://830767422336.dkr.ecr.us-east-1.amazonaws.com/
        imageDetailsBuilder.name(imageUrl)
            .sourceName(ecrArtifactStream.getSourceName())
            .registryUrl("https://" + imageUrl + (imageUrl.endsWith("/") ? "" : "/"))
            .username("AWS");
        SettingValue settingValue = settingsService.get(settingId).getValue();

        // All the new ECR artifact streams use cloud provider AWS settings for accesskey and secret
        if (SettingVariableTypes.AWS.name().equals(settingValue.getType())) {
          AwsConfig awsConfig = (AwsConfig) settingsService.get(settingId).getValue();
          String authToken =
              getAmazonEcrAuthToken(awsConfig, secretManager.getEncryptionDetails(awsConfig, null, workflowExecutionId),
                  imageUrl.substring(0, imageUrl.indexOf('.')), ecrArtifactStream.getRegion());
          imageDetailsBuilder.password(authToken);
          if (featureFlagService.isEnabled(FeatureName.AMAZON_ECR_AUTH_REFACTOR, artifactStream.getAccountId())) {
            // https://github.com/aws/aws-sdk-java/issues/818
            String decodedString = new String(decodeBase64(authToken));
            String username = decodedString.split(":")[0];
            String password = decodedString.split(":")[1];
            imageDetailsBuilder.username(username);
            imageDetailsBuilder.password(password);
            imageDetailsBuilder.registryUrl("https://" + imageUrl);
          }
        } else {
          // There is a point when old ECR artifact streams would be using the old ECR Artifact Server
          // definition until migration happens. The deployment code handles both the cases.
          EcrConfig ecrConfig = (EcrConfig) settingsService.get(settingId).getValue();
          imageDetailsBuilder.password(awsHelperService.getAmazonEcrAuthToken(
              ecrConfig, secretManager.getEncryptionDetails(ecrConfig, null, workflowExecutionId)));
        }
      } else if (artifactStream.getArtifactStreamType().equals(GCR.name())) {
        GcrArtifactStream gcrArtifactStream = (GcrArtifactStream) artifactStream;
        String imageName = gcrArtifactStream.getRegistryHostName() + "/" + gcrArtifactStream.getDockerImageName();
        imageDetailsBuilder.name(imageName).sourceName(imageName).registryUrl(imageName);
        GcpConfig gcpConfig = (GcpConfig) settingsService.get(settingId).getValue();
        managerDecryptionService.decrypt(
            gcpConfig, secretManager.getEncryptionDetails(gcpConfig, null, workflowExecutionId));
        if (gcpConfig.getServiceAccountKeyFileContent() != null) {
          // Create password as the service account file content (without newlines) as username as _json_key.
          imageDetailsBuilder.username("_json_key")
              .password(new String(gcpConfig.getServiceAccountKeyFileContent()).replaceAll("\n", ""));
        }
      } else if (artifactStream.getArtifactStreamType().equals(ACR.name())) {
        AcrArtifactStream acrArtifactStream = (AcrArtifactStream) artifactStream;
        AzureConfig azureConfig = (AzureConfig) settingsService.get(settingId).getValue();
        managerDecryptionService.decrypt(
            azureConfig, secretManager.getEncryptionDetails(azureConfig, null, workflowExecutionId));
        String loginServer = isNotEmpty(acrArtifactStream.getRegistryHostName())
            ? acrArtifactStream.getRegistryHostName()
            : azureDelegateHelperService.getLoginServerForRegistry(azureConfig,
                secretManager.getEncryptionDetails(azureConfig, null, workflowExecutionId),
                acrArtifactStream.getSubscriptionId(), acrArtifactStream.getRegistryName());

        imageDetailsBuilder.registryUrl(azureDelegateHelperService.getUrl(loginServer))
            .sourceName(acrArtifactStream.getRepositoryName())
            .name(loginServer + "/" + acrArtifactStream.getRepositoryName())
            .username(azureConfig.getClientId())
            .password(new String(azureConfig.getKey()));
      } else if (artifactStream.getArtifactStreamType().equals(ARTIFACTORY.name())) {
        ArtifactoryArtifactStream artifactoryArtifactStream = (ArtifactoryArtifactStream) artifactStream;
        ArtifactoryConfig artifactoryConfig = (ArtifactoryConfig) settingsService.get(settingId).getValue();
        managerDecryptionService.decrypt(
            artifactoryConfig, secretManager.getEncryptionDetails(artifactoryConfig, null, workflowExecutionId));
        String registryUrl = ArtifactUtilities.getArtifactoryRegistryUrl(artifactoryConfig.getArtifactoryUrl(),
            artifactoryArtifactStream.getDockerRepositoryServer(), artifactoryArtifactStream.getJobname());
        String repositoryName = ArtifactUtilities.getArtifactoryRepositoryName(artifactoryConfig.getArtifactoryUrl(),
            artifactoryArtifactStream.getDockerRepositoryServer(), artifactoryArtifactStream.getJobname(),
            artifactoryArtifactStream.getImageName());

        if (artifactoryConfig.hasCredentials()) {
          imageDetailsBuilder.name(repositoryName)
              .sourceName(artifactoryArtifactStream.getSourceName())
              .registryUrl(registryUrl)
              .username(artifactoryConfig.getUsername())
              .password(new String(artifactoryConfig.getPassword()));
        } else {
          imageDetailsBuilder.name(repositoryName)
              .sourceName(artifactoryArtifactStream.getSourceName())
              .registryUrl(registryUrl);
        }
      } else if (artifactStream.getArtifactStreamType().equals(NEXUS.name())) {
        NexusArtifactStream nexusArtifactStream = (NexusArtifactStream) artifactStream;
        NexusConfig nexusConfig = (NexusConfig) settingsService.get(settingId).getValue();
        managerDecryptionService.decrypt(
            nexusConfig, secretManager.getEncryptionDetails(nexusConfig, null, workflowExecutionId));

        String registryUrl = ArtifactUtilities.getNexusRegistryUrl(
            nexusConfig.getNexusUrl(), nexusArtifactStream.getDockerPort(), nexusArtifactStream.getDockerRegistryUrl());
        String repositoryName =
            ArtifactUtilities.getNexusRepositoryName(nexusConfig.getNexusUrl(), nexusArtifactStream.getDockerPort(),
                nexusArtifactStream.getDockerRegistryUrl(), nexusArtifactStream.getImageName());
        log.info("Nexus Registry url: " + registryUrl);
        if (nexusConfig.hasCredentials()) {
          imageDetailsBuilder.name(repositoryName)
              .sourceName(nexusArtifactStream.getSourceName())
              .registryUrl(registryUrl)
              .username(nexusConfig.getUsername())
              .password(new String(nexusConfig.getPassword()));
        } else {
          imageDetailsBuilder.name(repositoryName)
              .sourceName(nexusArtifactStream.getSourceName())
              .registryUrl(registryUrl);
        }
      } else if (artifactStream.getArtifactStreamType().equals(CUSTOM.name())) {
        imageDetailsBuilder.sourceName(artifactStream.getSourceName());
      } else {
        throw new InvalidRequestException(
            artifactStream.getArtifactStreamType() + " artifact source can't be used for containers");
      }
      return imageDetailsBuilder.build();
    } finally {
      log.info("Exiting getDockerImageDetailsInternal: artifactStreamName {}", artifactStream.getName());
    }
  }

  private String getImageUrl(EcrArtifactStream ecrArtifactStream, String workflowExecutionId) {
    SettingAttribute settingAttribute = settingsService.get(ecrArtifactStream.getSettingId());
    SettingValue value = settingAttribute.getValue();
    if (SettingVariableTypes.AWS.name().equals(value.getType())) {
      AwsConfig awsConfig = (AwsConfig) value;
      return getEcrImageUrl(awsConfig, secretManager.getEncryptionDetails(awsConfig, null, workflowExecutionId),
          ecrArtifactStream.getRegion(), ecrArtifactStream);
    } else {
      EcrConfig ecrConfig = (EcrConfig) value;
      return ecrClassicService.getEcrImageUrl(ecrConfig, ecrArtifactStream.getImageName());
    }
  }

  private String getEcrImageUrl(AwsConfig awsConfig, List<EncryptedDataDetail> encryptionDetails, String region,
      EcrArtifactStream ecrArtifactStream) {
    return awsEcrHelperServiceManager.getEcrImageUrl(
        awsConfig, encryptionDetails, region, ecrArtifactStream.getImageName(), null);
  }

  private String getAmazonEcrAuthToken(
      AwsConfig awsConfig, List<EncryptedDataDetail> encryptionDetails, String awsAccount, String region) {
    return awsEcrHelperServiceManager.getAmazonEcrAuthToken(awsConfig, encryptionDetails, awsAccount, region, null);
  }

  public ArtifactStreamAttributes renderCustomArtifactScriptString(CustomArtifactStream customArtifactStream) {
    notNullCheck("Account does not exist", customArtifactStream.getAccountId(), USER);

    Map<String, Object> context = new HashMap<>();
    context.put("secrets",
        SecretFunctor.builder()
            .managerDecryptionService(managerDecryptionService)
            .secretManager(secretManager)
            .accountId(customArtifactStream.getAccountId())
            .appId(customArtifactStream.getAppId())
            .mode(CASCADING)
            .build());

    // Find the FETCH VERSION Script from artifact stream
    Script versionScript =
        customArtifactStream.getScripts()
            .stream()
            .filter(script -> script.getAction() == null || script.getAction() == Action.FETCH_VERSIONS)
            .findFirst()
            .orElse(null);
    if (isNotEmpty(customArtifactStream.getTemplateVariables())) {
      Map<String, Object> templateVariableMap =
          TemplateHelper.convertToVariableMap(customArtifactStream.getTemplateVariables());
      if (isNotEmpty(templateVariableMap)) {
        context.putAll(templateVariableMap);
      }
    }
    notNullCheck("Fetch Version script is missing", versionScript, USER);

    ArtifactStreamAttributes artifactStreamAttributes =
        customArtifactStream.fetchArtifactStreamAttributes(featureFlagService);

    String scriptString = versionScript.getScriptString();
    notNullCheck("Script string can not be empty", scriptString, USER);
    artifactStreamAttributes.setCustomArtifactStreamScript(evaluator.substitute(scriptString, context));
    artifactStreamAttributes.setAccountId(customArtifactStream.getAccountId());
    artifactStreamAttributes.setCustomScriptTimeout(evaluator.substitute(versionScript.getTimeout(), context));
    if (versionScript.getCustomRepositoryMapping() != null) {
      validateAttributeMapping(versionScript.getCustomRepositoryMapping().getArtifactRoot(),
          versionScript.getCustomRepositoryMapping().getBuildNoPath());
      artifactStreamAttributes.setCustomAttributeMappingNeeded(true);
      artifactStreamAttributes.setArtifactRoot(
          evaluator.substitute(versionScript.getCustomRepositoryMapping().getArtifactRoot(), context));
      artifactStreamAttributes.setBuildNoPath(
          evaluator.substitute(versionScript.getCustomRepositoryMapping().getBuildNoPath(), context));
      Map<String, String> map = new HashMap<>();
      if (EmptyPredicate.isNotEmpty(versionScript.getCustomRepositoryMapping().getArtifactAttributes())) {
        for (AttributeMapping attributeMapping : versionScript.getCustomRepositoryMapping().getArtifactAttributes()) {
          String relativePath = null;
          if (isNotEmpty(attributeMapping.getRelativePath())) {
            relativePath = evaluator.substitute(attributeMapping.getRelativePath(), context);
          }
          String mappedAttribute = null;
          if (isNotEmpty(attributeMapping.getMappedAttribute())) {
            mappedAttribute = evaluator.substitute(attributeMapping.getMappedAttribute(), context);
          }
          map.put(relativePath, mappedAttribute);
        }
      }

      artifactStreamAttributes.setArtifactAttributes(map);
    }
    return artifactStreamAttributes;
  }

  private void validateAttributeMapping(String artifactRoot, String buildNoPath) {
    if (isEmpty(artifactRoot)) {
      throw new WingsException(ErrorCode.INVALID_ARTIFACT_SERVER, WingsException.USER)
          .addParam("message", "Artifacts Array Path cannot be empty. Please refer the documentation.");
    }
    if (isEmpty(buildNoPath)) {
      throw new WingsException(ErrorCode.INVALID_ARTIFACT_SERVER, WingsException.USER)
          .addParam("message", "BuildNo. Path cannot be empty. Please refer the documentation.");
    }
  }

  public static BuildDetails prepareDockerBuildDetails(DockerConfig dockerConfig, String imageName, String tag) {
    String tagUrl = dockerConfig.getDockerRegistryUrl().endsWith("/")
        ? dockerConfig.getDockerRegistryUrl() + imageName + "/tags/"
        : dockerConfig.getDockerRegistryUrl() + "/" + imageName + "/tags/";

    String domainName = Http.getDomainWithPort(dockerConfig.getDockerRegistryUrl());
    Map<String, String> metadata = new HashMap<>();
    metadata.put(ArtifactMetadataKeys.image, domainName + "/" + imageName + ":" + tag);
    metadata.put(ArtifactMetadataKeys.tag, tag);
    return aBuildDetails().withNumber(tag).withMetadata(metadata).withBuildUrl(tagUrl + tag).build();
  }

  public ArtifactStreamAttributes getArtifactStreamAttributes(ArtifactStream artifactStream, boolean isMultiArtifact) {
    if (isMultiArtifact) {
      return artifactStream.fetchArtifactStreamAttributes(featureFlagService);
    } else {
      Service service =
          artifactStreamServiceBindingService.getService(artifactStream.fetchAppId(), artifactStream.getUuid(), true);
      return getArtifactStreamAttributes(artifactStream, service);
    }
  }

  private ArtifactStreamAttributes getArtifactStreamAttributes(ArtifactStream artifactStream, Service service) {
    ArtifactStreamAttributes artifactStreamAttributes =
        artifactStream.fetchArtifactStreamAttributes(featureFlagService);
    artifactStreamAttributes.setArtifactType(service.getArtifactType());
    return artifactStreamAttributes;
  }

  private boolean isArtifactoryDockerOrGeneric(ArtifactStream artifactStream, ArtifactType artifactType) {
    if (ARTIFACTORY.name().equals(artifactStream.getArtifactStreamType())) {
      return ArtifactType.DOCKER == artifactType
          || !"maven".equals(artifactStream.fetchArtifactStreamAttributes(featureFlagService).getRepositoryType());
    }
    return false;
  }

  private boolean isArtifactoryDockerOrGeneric(ArtifactStream artifactStream) {
    return ARTIFACTORY.name().equals(artifactStream.getArtifactStreamType())
        && (artifactStream.fetchArtifactStreamAttributes(featureFlagService)
                .getRepositoryType()
                .equals(RepositoryType.docker.name())
            || !"maven".equals(artifactStream.fetchArtifactStreamAttributes(featureFlagService).getRepositoryType()));
  }

  public BuildSourceRequestType getRequestType(ArtifactStream artifactStream, ArtifactType artifactType) {
    String artifactStreamType = artifactStream.getArtifactStreamType();

    if (metadataOnlyStreams.contains(artifactStreamType)
        || isArtifactoryDockerOrGeneric(artifactStream, artifactType)) {
      return BuildSourceRequestType.GET_BUILDS;
    } else {
      return BuildSourceRequestType.GET_LAST_SUCCESSFUL_BUILD;
    }
  }

  private BuildSourceRequestType getRequestType(ArtifactStream artifactStream) {
    String artifactStreamType = artifactStream.getArtifactStreamType();

    if (metadataOnlyStreams.contains(artifactStreamType) || isArtifactoryDockerOrGeneric(artifactStream)
        || artifactStreamType.equals(JENKINS.name()) || artifactStreamType.equals(BAMBOO.name())) {
      return BuildSourceRequestType.GET_BUILDS;
    } else {
      return BuildSourceRequestType.GET_LAST_SUCCESSFUL_BUILD;
    }
  }

  public static int getLimit(String artifactStreamType, BuildSourceRequestType requestType, boolean isCollection) {
    if (ARTIFACTORY.name().equals(artifactStreamType) && BuildSourceRequestType.GET_BUILDS == requestType) {
      return isCollection ? ARTIFACT_RETENTION_SIZE : MANUAL_PULL_ARTIFACTORY_LIMIT;
    }
    return -1;
  }

  public BuildSourceParameters getBuildSourceParameters(ArtifactStream artifactStream,
      SettingAttribute settingAttribute, boolean isCollection, boolean withSavedBuildDetailsKeys) {
    String artifactStreamType = artifactStream.getArtifactStreamType();
    SettingValue settingValue = settingAttribute.getValue();
    List<EncryptedDataDetail> encryptedDataDetails =
        secretManager.getEncryptionDetails((EncryptableSetting) settingValue, null, null);

    String appId = artifactStream.fetchAppId();
    boolean multiArtifact = multiArtifactEnabled(settingAttribute.getAccountId());
    ArtifactStreamAttributes artifactStreamAttributes = getArtifactStreamAttributes(artifactStream, multiArtifact);

    BuildSourceRequestType requestType =
        getBuildSourceRequestType(artifactStream, multiArtifact, artifactStreamAttributes);

    int failedCronAttempts = artifactStream.getFailedCronAttempts();

    BuildSourceParametersBuilder buildSourceParametersBuilder =
        BuildSourceParameters.builder()
            .accountId(settingAttribute.getAccountId())
            .appId(appId)
            .artifactStreamAttributes(artifactStreamAttributes)
            .artifactStreamType(artifactStreamType)
            .artifactStreamId(artifactStream.getUuid())
            .settingValue(settingValue)
            .encryptedDataDetails(encryptedDataDetails)
            .buildSourceRequestType(requestType)
            .limit(getLimit(artifactStream.getArtifactStreamType(), requestType, isCollection))
            .isCollection(isCollection)
            .shouldFetchSecretFromCache(failedCronAttempts < 2 || failedCronAttempts % 5 != 0);

    if (withSavedBuildDetailsKeys) {
      buildSourceParametersBuilder.savedBuildDetailsKeys(getArtifactsKeys(artifactStream, artifactStreamAttributes));
    }
    return buildSourceParametersBuilder.build();
  }

  private boolean multiArtifactEnabled(String accountId) {
    return false;
  }

  private BuildSourceRequestType getBuildSourceRequestType(
      ArtifactStream artifactStream, boolean multiArtifact, ArtifactStreamAttributes artifactStreamAttributes) {
    BuildSourceRequestType requestType;
    if (multiArtifact) {
      requestType = getRequestType(artifactStream);
    } else {
      requestType = getRequestType(artifactStream, artifactStreamAttributes.getArtifactType());
    }
    return requestType;
  }

  public DelegateTask prepareValidateTask(String artifactStreamId, String accountId) {
    ArtifactStream artifactStream = artifactStreamService.get(artifactStreamId);
    if (artifactStream == null) {
      artifactStreamService.deletePerpetualTaskByArtifactStream(accountId, artifactStreamId);
      throw new InvalidRequestException("Artifact stream does not exist");
    }
    BuildSourceParametersBuilder parametersBuilder = BuildSourceParameters.builder()
                                                         .appId(artifactStream.getAppId())
                                                         .artifactStreamType(artifactStream.getArtifactStreamType());
    SettingValue settingValue;
    List<String> tags = new ArrayList<>();
    if (CUSTOM.name().equals(artifactStream.getArtifactStreamType())) {
      parametersBuilder.accountId(artifactStream.getAccountId())
          .buildSourceRequestType(BuildSourceRequestType.GET_BUILDS)
          .artifactStreamAttributes(artifactStream.fetchArtifactStreamAttributes(featureFlagService));
      tags = ((CustomArtifactStream) artifactStream).getTags();
      if (isNotEmpty(tags)) {
        // To remove if any empty tags in case saved for custom artifact stream
        tags = tags.stream().filter(EmptyPredicate::isNotEmpty).distinct().collect(toList());
      }

    } else {
      SettingAttribute settingAttribute = settingsService.get(artifactStream.getSettingId());
      if (settingAttribute == null) {
        artifactStreamService.deletePerpetualTaskByArtifactStream(accountId, artifactStreamId);
        throw new InvalidRequestException("Connector / Cloud Provider associated to Artifact Stream was deleted");
      }
      settingValue = settingAttribute.getValue();
      List<EncryptedDataDetail> encryptedDataDetails =
          secretManager.getEncryptionDetails((EncryptableSetting) settingValue, null, null);
      List<String> delegateSelectors = settingsService.getDelegateSelectors(settingAttribute);
      if (isNotEmpty(delegateSelectors)) {
        tags = isNotEmpty(tags) ? tags : new ArrayList<>();
        tags.addAll(delegateSelectors);
        tags = tags.stream().filter(EmptyPredicate::isNotEmpty).distinct().collect(toList());
      }
      accountId = settingAttribute.getAccountId();
      boolean multiArtifactEnabled = multiArtifactEnabled(accountId);

      ArtifactStreamAttributes artifactStreamAttributes =
          getArtifactStreamAttributes(artifactStream, multiArtifactEnabled);

      BuildSourceRequestType requestType =
          getBuildSourceRequestType(artifactStream, multiArtifactEnabled, artifactStreamAttributes);

      parametersBuilder = BuildSourceParameters.builder()
                              .accountId(settingAttribute.getAccountId())
                              .appId(artifactStream.getAppId())
                              .artifactStreamAttributes(artifactStreamAttributes)
                              .artifactStreamType(artifactStream.getArtifactStreamType())
                              .settingValue(settingValue)
                              .encryptedDataDetails(encryptedDataDetails)
                              .buildSourceRequestType(requestType);
    }

    DelegateTaskBuilder delegateTaskBuilder = DelegateTask.builder()
                                                  .accountId(accountId)
                                                  .rank(DelegateTaskRank.OPTIONAL)
                                                  .data(TaskData.builder()
                                                            .async(false)
                                                            .taskType(TaskType.BUILD_SOURCE_TASK.name())
                                                            .parameters(new Object[] {parametersBuilder.build()})
                                                            .timeout(TimeUnit.MINUTES.toMillis(1))
                                                            .build())
                                                  .tags(tags)
                                                  .expiry(getDelegateQueueTimeout(accountId));
    if (featureFlagService.isEnabled(FeatureName.ARTIFACT_STREAM_DELEGATE_SCOPING, accountId)) {
      delegateTaskBuilder.setupAbstraction(Cd1SetupFields.APP_ID_FIELD, artifactStream.getAppId());
    }
    return delegateTaskBuilder.build();
  }

  /**
   * getArtifactsKeys returns a set of Artifact keys so that they can be compared against incoming BuildDetails objects.
   *
   * @param artifactStream           the artifact stream
   * @param artifactStreamAttributes the artifact stream attributes - used only for ARTIFACTORY
   * @return the set of artifact keys
   */
  public Set<String> getArtifactsKeys(
      ArtifactStream artifactStream, ArtifactStreamAttributes artifactStreamAttributes) {
    if (artifactStream == null || artifactStream.getArtifactStreamType() == null) {
      return Collections.emptySet();
    }

    String artifactStreamType = artifactStream.getArtifactStreamType();
    Function<Artifact, String> keyFn = getArtifactKeyFn(artifactStreamType, artifactStreamAttributes);
    Set<String> artifactKeys = new HashSet<>();
    try (HIterator<Artifact> artifacts = new HIterator<>(
             artifactService.prepareArtifactWithMetadataQuery(artifactStream, true).limit(NO_LIMIT).fetch())) {
      for (Artifact artifact : artifacts) {
        String key = keyFn.apply(artifact);
        if (key != null) {
          artifactKeys.add(key);
        }
      }
    }
    return artifactKeys;
  }

  /**
   * getArtifactKeyFn returns a function that can extract a unique key for an Artifact object so that it can be compared
   * with a BuildDetails object.
   *
   * @param artifactStreamType       the artifact stream type
   * @param artifactStreamAttributes the artifact stream attributes - used only for ARTIFACTORY
   * @return the function that can used to get the key for an Artifact
   */
  public static Function<Artifact, String> getArtifactKeyFn(
      String artifactStreamType, ArtifactStreamAttributes artifactStreamAttributes) {
    if (AMI.name().equals(artifactStreamType)) {
      return Artifact::getRevision;
    } else if (DelegateArtifactCollectionUtils.isGenericArtifactStream(artifactStreamType, artifactStreamAttributes)) {
      return Artifact::getArtifactPath;
    } else {
      return Artifact::getBuildNo;
    }
  }

  /**
   * getNewBuildDetails returns new BuildDetails after removing Artifact already present in DB.
   *
   * @param savedBuildDetailsKeys    the artifact keys for artifacts already stored in DB
   * @param buildDetails             the new build details fetched from third-party repo
   * @param artifactStreamType       the artifact stream type
   * @param artifactStreamAttributes the artifact stream attributes - used only for ARTIFACTORY
   * @return the new build details
   */
  public static List<BuildDetails> getNewBuildDetails(Set<String> savedBuildDetailsKeys,
      List<BuildDetails> buildDetails, String artifactStreamType, ArtifactStreamAttributes artifactStreamAttributes) {
    if (isEmpty(buildDetails)) {
      return Collections.emptyList();
    }
    if (isEmpty(savedBuildDetailsKeys)) {
      return buildDetails;
    }

    Function<BuildDetails, String> keyFn =
        DelegateArtifactCollectionUtils.getBuildDetailsKeyFn(artifactStreamType, artifactStreamAttributes);
    return buildDetails.stream()
        .filter(singleBuildDetails -> !savedBuildDetailsKeys.contains(keyFn.apply(singleBuildDetails)))
        .collect(Collectors.toList());
  }

  public boolean skipArtifactStreamIteration(ArtifactStream artifactStream, boolean isCollection) {
    String prefix = isCollection ? "ASYNC_ARTIFACT_CRON" : "ASYNC_ARTIFACT_CLEANUP_CRON";
    String action = isCollection ? "collection" : "cleanup";
    String accountId = artifactStream.getAccountId();
    String artifactStreamId = artifactStream.getUuid();
    if (featureFlagService.isEnabled(FeatureName.ARTIFACT_PERPETUAL_TASK, accountId)) {
      if (isNotEmpty(artifactStream.getPerpetualTaskId())) {
        log.info("Perpetual task enabled for the artifact stream {}, skipping the artifact {} through iterator",
            artifactStreamId, action);
        return true;
      }
    } else if (isNotEmpty(artifactStream.getPerpetualTaskId())) {
      // If perpetual task is not enabled but the artifact stream still has a perpetual task id, delete the perpetual
      // task.
      artifactStreamPTaskHelper.deletePerpetualTask(accountId, artifactStream.getPerpetualTaskId());
    }

    if (artifactStream.getFailedCronAttempts() > ArtifactCollectionResponseHandler.MAX_FAILED_ATTEMPTS) {
      log.warn(
          "{}: Artifact {} disabled for artifactStream due to too many failures, type: {}, id: {}, failed count: {}",
          prefix, action, artifactStream.getArtifactStreamType(), artifactStreamId,
          artifactStream.getFailedCronAttempts());
      if (featureFlagService.isEnabled(FeatureName.ARTIFACT_COLLECTION_CONFIGURABLE, accountId)
          && !ArtifactStreamCollectionStatus.STOPPED.name().equals(artifactStream.getCollectionStatus())) {
        artifactStreamService.updateCollectionStatus(
            accountId, artifactStreamId, ArtifactStreamCollectionStatus.STOPPED.name());
      }
      return true;
    }

    if (EmptyPredicate.isEmpty(accountId)) {
      // Ideally, we should clean up these artifact streams.
      log.warn("{}: Artifact {} disabled for artifactStream due to empty accountId, type: {}, id: {}, failed count: {}",
          prefix, action, artifactStream.getArtifactStreamType(), artifactStreamId,
          artifactStream.getFailedCronAttempts());
      return true;
    }

    if (!CUSTOM.name().equals(artifactStream.getArtifactStreamType())) {
      SettingAttribute settingAttribute = settingsService.getOnlyConnectivityError(artifactStream.getSettingId());
      if (settingAttribute == null) {
        throw new InvalidRequestException(
            format("%s: Invalid artifact stream setting: %s", prefix, artifactStream.getSettingId()), USER);
      }
      if (isNotBlank(settingAttribute.getConnectivityError())) {
        log.info("{}: Skipping {} for artifact stream: {}, because of connectivity error in setting: {} and error: {}",
            prefix, action, artifactStreamId, artifactStream.getSettingId(), settingAttribute.getConnectivityError());
        return true;
      }
    }

    if (artifactStream.isArtifactStreamParameterized()) {
      log.info(
          format("Skipping artifact collection through iterator for artifact stream [%s] since it is parameterized",
              artifactStreamId));
      return true;
    }

    return false;
  }

  public BuildSourceParameters prepareBuildSourceParameters(String artifactStreamId) {
    ArtifactStream artifactStream = artifactStreamService.get(artifactStreamId);
    if (artifactStream == null) {
      throw new InvalidRequestException("Artifact stream was deleted");
    }
    return prepareBuildSourceParameters(artifactStream);
  }

  private BuildSourceParameters prepareBuildSourceParameters(ArtifactStream artifactStream) {
    String artifactStreamType = artifactStream.getArtifactStreamType();
    if (CUSTOM.name().equals(artifactStreamType)) {
      return prepareCustomBuildSourceParameters(artifactStream);
    } else {
      SettingAttribute settingAttribute = settingsService.get(artifactStream.getSettingId());
      if (settingAttribute == null) {
        throw new InvalidRequestException("Connector / Cloud Provider associated to Artifact Stream was deleted");
      }
      return getBuildSourceParameters(artifactStream, settingAttribute, true, true);
    }
  }

  private BuildSourceParameters prepareCustomBuildSourceParameters(ArtifactStream artifactStream) {
    ArtifactStreamAttributes artifactStreamAttributes =
        renderCustomArtifactScriptString((CustomArtifactStream) artifactStream);
    BuildSourceRequestType requestType = BuildSourceRequestType.GET_BUILDS;

    BuildSourceParametersBuilder buildSourceParametersBuilder =
        BuildSourceParameters.builder()
            .accountId(artifactStreamAttributes.getAccountId())
            .appId(artifactStream.fetchAppId())
            .artifactStreamId(artifactStream.getUuid())
            .artifactStreamAttributes(artifactStreamAttributes)
            .artifactStreamType(artifactStream.getArtifactStreamType())
            .buildSourceRequestType(requestType)
            .limit(ArtifactCollectionUtils.getLimit(artifactStream.getArtifactStreamType(), requestType, true))
            .isCollection(true);

    buildSourceParametersBuilder.savedBuildDetailsKeys(getArtifactsKeys(artifactStream, artifactStreamAttributes));

    return buildSourceParametersBuilder.build();
  }

  public List<Artifact> processBuilds(ArtifactStream artifactStream, List<BuildDetails> builds) {
    if (isEmpty(builds)) {
      return new ArrayList<>();
    }
    if (artifactStream == null) {
      log.info("Artifact stream does not exist. Returning");
      return new ArrayList<>();
    }

    // New build are filtered at the delegate. So all the builds coming in the BuildSourceExecutionResponse are the ones
    // not present in the DB.
    return builds.stream()
        .map(buildDetails -> artifactService.create(getArtifact(artifactStream, buildDetails)))
        .collect(Collectors.toList());
  }
}
