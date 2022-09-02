/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cdng.artifact.mappers;

import static software.wings.utils.RepositoryFormat.generic;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.artifact.bean.yaml.AcrArtifactConfig;
import io.harness.cdng.artifact.bean.yaml.AmazonS3ArtifactConfig;
import io.harness.cdng.artifact.bean.yaml.ArtifactoryRegistryArtifactConfig;
import io.harness.cdng.artifact.bean.yaml.CustomArtifactConfig;
import io.harness.cdng.artifact.bean.yaml.DockerHubArtifactConfig;
import io.harness.cdng.artifact.bean.yaml.EcrArtifactConfig;
import io.harness.cdng.artifact.bean.yaml.GcrArtifactConfig;
import io.harness.cdng.artifact.bean.yaml.GoogleArtifactRegistryConfig;
import io.harness.cdng.artifact.bean.yaml.JenkinsArtifactConfig;
import io.harness.cdng.artifact.bean.yaml.NexusRegistryArtifactConfig;
import io.harness.cdng.artifact.bean.yaml.customartifact.CustomScriptInlineSource;
import io.harness.data.structure.EmptyPredicate;
import io.harness.delegate.beans.connector.artifactoryconnector.ArtifactoryConnectorDTO;
import io.harness.delegate.beans.connector.awsconnector.AwsConnectorDTO;
import io.harness.delegate.beans.connector.azureconnector.AzureConnectorDTO;
import io.harness.delegate.beans.connector.docker.DockerConnectorDTO;
import io.harness.delegate.beans.connector.gcpconnector.GcpConnectorDTO;
import io.harness.delegate.beans.connector.jenkins.JenkinsConnectorDTO;
import io.harness.delegate.beans.connector.nexusconnector.NexusConnectorDTO;
import io.harness.delegate.task.artifacts.ArtifactDelegateRequestUtils;
import io.harness.delegate.task.artifacts.ArtifactSourceDelegateRequest;
import io.harness.delegate.task.artifacts.ArtifactSourceType;
import io.harness.delegate.task.artifacts.artifactory.ArtifactoryArtifactDelegateRequest;
import io.harness.delegate.task.artifacts.artifactory.ArtifactoryGenericArtifactDelegateRequest;
import io.harness.delegate.task.artifacts.custom.CustomArtifactDelegateRequest;
import io.harness.delegate.task.artifacts.docker.DockerArtifactDelegateRequest;
import io.harness.delegate.task.artifacts.ecr.EcrArtifactDelegateRequest;
import io.harness.delegate.task.artifacts.gar.GarDelegateRequest;
import io.harness.delegate.task.artifacts.gcr.GcrArtifactDelegateRequest;
import io.harness.delegate.task.artifacts.jenkins.JenkinsArtifactDelegateRequest;
import io.harness.delegate.task.artifacts.nexus.NexusArtifactDelegateRequest;
import io.harness.delegate.task.artifacts.s3.S3ArtifactDelegateRequest;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.yaml.ParameterField;
import io.harness.security.encryption.EncryptedDataDetail;
import io.harness.yaml.utils.NGVariablesUtils;

import java.util.Arrays;
import java.util.List;
import lombok.experimental.UtilityClass;
import org.apache.commons.lang3.StringUtils;

@UtilityClass
@OwnedBy(HarnessTeam.PIPELINE)
public class ArtifactConfigToDelegateReqMapper {
  private final String ACCEPT_ALL_REGEX = "\\*";

  public DockerArtifactDelegateRequest getDockerDelegateRequest(DockerHubArtifactConfig artifactConfig,
      DockerConnectorDTO connectorDTO, List<EncryptedDataDetail> encryptedDataDetails, String connectorRef) {
    // If both are empty, regex is latest among all docker artifacts.
    String tagRegex = artifactConfig.getTagRegex() != null ? artifactConfig.getTagRegex().getValue() : "";
    String tag = artifactConfig.getTag() != null ? artifactConfig.getTag().getValue() : "";
    if (EmptyPredicate.isEmpty(tag) && EmptyPredicate.isEmpty(tagRegex)) {
      tagRegex = ACCEPT_ALL_REGEX;
    }
    return ArtifactDelegateRequestUtils.getDockerDelegateRequest(artifactConfig.getImagePath().getValue(), tag,
        tagRegex, null, connectorRef, connectorDTO, encryptedDataDetails, ArtifactSourceType.DOCKER_REGISTRY);
  }

  public S3ArtifactDelegateRequest getAmazonS3DelegateRequest(AmazonS3ArtifactConfig artifactConfig,
      AwsConnectorDTO connectorDTO, List<EncryptedDataDetail> encryptedDataDetails, String connectorRef) {
    // If both are empty, regex is latest among all S3 artifacts.
    String filePathRegex =
        artifactConfig.getFilePathRegex() != null ? artifactConfig.getFilePathRegex().getValue() : "";
    String filePath = artifactConfig.getFilePath() != null ? artifactConfig.getFilePath().getValue() : "";
    if (EmptyPredicate.isEmpty(filePath) && EmptyPredicate.isEmpty(filePathRegex)) {
      filePathRegex = "*";
    }
    return ArtifactDelegateRequestUtils.getAmazonS3DelegateRequest(artifactConfig.getBucketName().getValue(), filePath,
        filePathRegex, null, connectorRef, connectorDTO, encryptedDataDetails, ArtifactSourceType.AMAZONS3,
        artifactConfig.getRegion() != null ? artifactConfig.getRegion().getValue() : "us-east-1");
  }

  public JenkinsArtifactDelegateRequest getJenkinsDelegateRequest(JenkinsArtifactConfig artifactConfig,
      JenkinsConnectorDTO connectorDTO, List<EncryptedDataDetail> encryptedDataDetails, String connectorRef) {
    String artifactPath = artifactConfig.getArtifactPath() != null ? artifactConfig.getArtifactPath().getValue() : "";
    String jobName = artifactConfig.getJobName() != null ? artifactConfig.getJobName().getValue() : "";
    return ArtifactDelegateRequestUtils.getJenkinsDelegateRequest(connectorRef, connectorDTO, encryptedDataDetails,
        ArtifactSourceType.JENKINS, null, null, jobName, Arrays.asList(artifactPath));
  }
  public CustomArtifactDelegateRequest getCustomDelegateRequest(
      CustomArtifactConfig artifactConfig, Ambiance ambiance) {
    CustomScriptInlineSource customScriptInlineSource = (CustomScriptInlineSource) artifactConfig.getScripts()
                                                            .getFetchAllArtifacts()
                                                            .getShellScriptBaseStepInfo()
                                                            .getSource()
                                                            .getSpec();
    return ArtifactDelegateRequestUtils.getCustomDelegateRequest(
        artifactConfig.getScripts().getFetchAllArtifacts().getArtifactsArrayPath().getValue(),
        artifactConfig.getVersionRegex().getValue(),
        artifactConfig.getScripts().getFetchAllArtifacts().getShellScriptBaseStepInfo().getSource().getType(),
        ArtifactSourceType.CUSTOM_ARTIFACT,
        artifactConfig.getScripts().getFetchAllArtifacts().getVersionPath().getValue(),
        customScriptInlineSource.getScript().fetchFinalValue().toString(),
        NGVariablesUtils.getStringMapVariables(artifactConfig.getScripts().getFetchAllArtifacts().getAttributes(), 0L),
        NGVariablesUtils.getStringMapVariables(artifactConfig.getInputs(), 0L), artifactConfig.getVersion().getValue(),
        AmbianceUtils.obtainCurrentRuntimeId(ambiance), artifactConfig.getTimeout().getValue().getTimeoutInMillis(),
        AmbianceUtils.getAccountId(ambiance));
  }

  public GcrArtifactDelegateRequest getGcrDelegateRequest(GcrArtifactConfig gcrArtifactConfig,
      GcpConnectorDTO gcpConnectorDTO, List<EncryptedDataDetail> encryptedDataDetails, String connectorRef) {
    // If both are empty, regex is latest among all gcr artifacts.
    String tagRegex = gcrArtifactConfig.getTagRegex() != null ? gcrArtifactConfig.getTagRegex().getValue() : "";
    String tag = gcrArtifactConfig.getTag() != null ? gcrArtifactConfig.getTag().getValue() : "";
    if (EmptyPredicate.isEmpty(tag) && EmptyPredicate.isEmpty(tagRegex)) {
      tagRegex = ACCEPT_ALL_REGEX;
    }
    return ArtifactDelegateRequestUtils.getGcrDelegateRequest(gcrArtifactConfig.getImagePath().getValue(), tag,
        tagRegex, null, gcrArtifactConfig.getRegistryHostname().getValue(), connectorRef, gcpConnectorDTO,
        encryptedDataDetails, ArtifactSourceType.GCR);
  }
  public GarDelegateRequest getGarDelegateRequest(GoogleArtifactRegistryConfig garArtifactConfig,
      GcpConnectorDTO gcpConnectorDTO, List<EncryptedDataDetail> encryptedDataDetails, String connectorRef) {
    // If both are empty, regex is latest among all gcr artifacts.
    String versionRegex =
        garArtifactConfig.getVersionRegex() != null ? garArtifactConfig.getVersionRegex().getValue() : "";
    String version = garArtifactConfig.getVersion() != null ? garArtifactConfig.getVersion().getValue() : "";
    if (StringUtils.isBlank(version) && StringUtils.isBlank(versionRegex)) {
      versionRegex = ACCEPT_ALL_REGEX;
    }
    return ArtifactDelegateRequestUtils.getGoogleArtifactDelegateRequest(garArtifactConfig.getRegion().getValue(),
        garArtifactConfig.getRepositoryName().getValue(), garArtifactConfig.getProject().getValue(),
        garArtifactConfig.getPkg().getValue(), garArtifactConfig.getVersion().getValue(),
        garArtifactConfig.getVersionRegex().getValue(), gcpConnectorDTO, encryptedDataDetails,
        ArtifactSourceType.GOOGLE_ARTIFACT_REGISTRY, Integer.MAX_VALUE);
  }

  public EcrArtifactDelegateRequest getEcrDelegateRequest(EcrArtifactConfig ecrArtifactConfig,
      AwsConnectorDTO awsConnectorDTO, List<EncryptedDataDetail> encryptedDataDetails, String connectorRef) {
    // If both are empty, regex is latest among all ecr artifacts.
    String tagRegex = ecrArtifactConfig.getTagRegex() != null ? ecrArtifactConfig.getTagRegex().getValue() : "";
    String tag = ecrArtifactConfig.getTag() != null ? ecrArtifactConfig.getTag().getValue() : "";
    if (EmptyPredicate.isEmpty(tag) && EmptyPredicate.isEmpty(tagRegex)) {
      tagRegex = ACCEPT_ALL_REGEX;
    }
    return ArtifactDelegateRequestUtils.getEcrDelegateRequest(ecrArtifactConfig.getImagePath().getValue(), tag,
        tagRegex, null, ecrArtifactConfig.getRegion().getValue(), connectorRef, awsConnectorDTO, encryptedDataDetails,
        ArtifactSourceType.ECR);
  }

  public NexusArtifactDelegateRequest getNexusArtifactDelegateRequest(NexusRegistryArtifactConfig artifactConfig,
      NexusConnectorDTO nexusConnectorDTO, List<EncryptedDataDetail> encryptedDataDetails, String connectorRef) {
    // If both are empty, regex is latest among all docker artifacts.
    String tagRegex = artifactConfig.getTagRegex() != null ? artifactConfig.getTagRegex().getValue() : "";
    String tag = artifactConfig.getTag() != null ? artifactConfig.getTag().getValue() : "";
    if (EmptyPredicate.isEmpty(tag) && EmptyPredicate.isEmpty(tagRegex)) {
      tagRegex = ACCEPT_ALL_REGEX;
    }
    String port = artifactConfig.getRepositoryPort() != null ? artifactConfig.getRepositoryPort().getValue() : null;
    String artifactRepositoryUrl =
        artifactConfig.getRepositoryUrl() != null ? artifactConfig.getRepositoryUrl().getValue() : null;

    return ArtifactDelegateRequestUtils.getNexusArtifactDelegateRequest(artifactConfig.getRepository().getValue(), port,
        artifactConfig.getArtifactPath().getValue(), artifactConfig.getRepositoryFormat().getValue(),
        artifactRepositoryUrl, tag, tagRegex, connectorRef, nexusConnectorDTO, encryptedDataDetails,
        ArtifactSourceType.NEXUS3_REGISTRY);
  }

  public ArtifactSourceDelegateRequest getArtifactoryArtifactDelegateRequest(
      ArtifactoryRegistryArtifactConfig artifactConfig, ArtifactoryConnectorDTO artifactoryConnectorDTO,
      List<EncryptedDataDetail> encryptedDataDetails, String connectorRef) {
    if (artifactConfig.getRepositoryFormat().getValue().equals(generic.name())) {
      return ArtifactConfigToDelegateReqMapper.getArtifactoryGenericArtifactDelegateRequest(
          artifactConfig, artifactoryConnectorDTO, encryptedDataDetails, connectorRef);
    } else {
      return ArtifactConfigToDelegateReqMapper.getArtifactoryDockerArtifactDelegateRequest(
          artifactConfig, artifactoryConnectorDTO, encryptedDataDetails, connectorRef);
    }
  }

  private ArtifactoryArtifactDelegateRequest getArtifactoryDockerArtifactDelegateRequest(
      ArtifactoryRegistryArtifactConfig artifactConfig, ArtifactoryConnectorDTO artifactoryConnectorDTO,
      List<EncryptedDataDetail> encryptedDataDetails, String connectorRef) {
    // If both are empty, regex is latest among all docker artifacts.
    String tagRegex = artifactConfig.getTagRegex() != null ? artifactConfig.getTagRegex().getValue() : "";
    String tag = artifactConfig.getTag() != null ? artifactConfig.getTag().getValue() : "";
    if (EmptyPredicate.isEmpty(tag) && EmptyPredicate.isEmpty(tagRegex)) {
      tagRegex = ACCEPT_ALL_REGEX;
    }

    String artifactRepositoryUrl =
        artifactConfig.getRepositoryUrl() != null ? artifactConfig.getRepositoryUrl().getValue() : null;

    return (ArtifactoryArtifactDelegateRequest) ArtifactDelegateRequestUtils.getArtifactoryArtifactDelegateRequest(
        artifactConfig.getRepository().getValue(), artifactConfig.getArtifactPath().getValue(),
        artifactConfig.getRepositoryFormat().getValue(), artifactRepositoryUrl, tag, tagRegex, connectorRef,
        artifactoryConnectorDTO, encryptedDataDetails, ArtifactSourceType.ARTIFACTORY_REGISTRY);
  }

  private ArtifactoryGenericArtifactDelegateRequest getArtifactoryGenericArtifactDelegateRequest(
      ArtifactoryRegistryArtifactConfig artifactConfig, ArtifactoryConnectorDTO artifactoryConnectorDTO,
      List<EncryptedDataDetail> encryptedDataDetails, String connectorRef) {
    // If both are empty, artifactPathFilter is latest among all artifacts.
    String artifactPathFilter = ParameterField.isNull(artifactConfig.getArtifactPathFilter())
        ? ""
        : artifactConfig.getArtifactPathFilter().getValue();
    String artifactPath =
        ParameterField.isNull(artifactConfig.getArtifactPath()) ? "" : artifactConfig.getArtifactPath().getValue();

    String artifactDirectory = ParameterField.isNull(artifactConfig.getArtifactDirectory())
        ? null
        : artifactConfig.getArtifactDirectory().getValue();

    return ArtifactDelegateRequestUtils.getArtifactoryGenericArtifactDelegateRequest(
        artifactConfig.getRepository().getValue(), artifactConfig.getRepositoryFormat().getValue(), artifactDirectory,
        artifactPath, artifactPathFilter, connectorRef, artifactoryConnectorDTO, encryptedDataDetails,
        ArtifactSourceType.ARTIFACTORY_REGISTRY);
  }

  public static ArtifactSourceDelegateRequest getAcrDelegateRequest(AcrArtifactConfig acrArtifactConfig,
      AzureConnectorDTO azureConnectorDTO, List<EncryptedDataDetail> encryptedDataDetails, String connectorRef) {
    // If both are empty, regex is latest among all acr artifacts.
    String tagRegex =
        ParameterField.isNull(acrArtifactConfig.getTagRegex()) ? "" : acrArtifactConfig.getTagRegex().getValue();
    String tag = ParameterField.isNull(acrArtifactConfig.getTag()) ? "" : acrArtifactConfig.getTag().getValue();
    if (EmptyPredicate.isEmpty(tag) && EmptyPredicate.isEmpty(tagRegex)) {
      tagRegex = ACCEPT_ALL_REGEX;
    }
    return ArtifactDelegateRequestUtils.getAcrDelegateRequest(acrArtifactConfig.getSubscriptionId().getValue(),
        acrArtifactConfig.getRegistry().getValue(), acrArtifactConfig.getRepository().getValue(), azureConnectorDTO,
        tag, tagRegex, null, encryptedDataDetails, ArtifactSourceType.ACR);
  }
}
