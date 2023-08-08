/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cdng.artifact.mappers;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.exception.WingsException.USER;

import static software.wings.utils.RepositoryFormat.generic;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.cdng.artifact.bean.yaml.AMIArtifactConfig;
import io.harness.cdng.artifact.bean.yaml.AcrArtifactConfig;
import io.harness.cdng.artifact.bean.yaml.AmazonS3ArtifactConfig;
import io.harness.cdng.artifact.bean.yaml.ArtifactoryRegistryArtifactConfig;
import io.harness.cdng.artifact.bean.yaml.AzureArtifactsConfig;
import io.harness.cdng.artifact.bean.yaml.CustomArtifactConfig;
import io.harness.cdng.artifact.bean.yaml.DockerHubArtifactConfig;
import io.harness.cdng.artifact.bean.yaml.EcrArtifactConfig;
import io.harness.cdng.artifact.bean.yaml.GcrArtifactConfig;
import io.harness.cdng.artifact.bean.yaml.GithubPackagesArtifactConfig;
import io.harness.cdng.artifact.bean.yaml.GoogleArtifactRegistryConfig;
import io.harness.cdng.artifact.bean.yaml.GoogleCloudSourceArtifactConfig;
import io.harness.cdng.artifact.bean.yaml.GoogleCloudStorageArtifactConfig;
import io.harness.cdng.artifact.bean.yaml.JenkinsArtifactConfig;
import io.harness.cdng.artifact.bean.yaml.NexusRegistryArtifactConfig;
import io.harness.cdng.artifact.bean.yaml.customartifact.CustomScriptInlineSource;
import io.harness.cdng.artifact.bean.yaml.nexusartifact.BambooArtifactConfig;
import io.harness.cdng.artifact.bean.yaml.nexusartifact.Nexus2RegistryArtifactConfig;
import io.harness.cdng.artifact.bean.yaml.nexusartifact.NexusRegistryDockerConfig;
import io.harness.cdng.artifact.bean.yaml.nexusartifact.NexusRegistryMavenConfig;
import io.harness.cdng.artifact.bean.yaml.nexusartifact.NexusRegistryNpmConfig;
import io.harness.cdng.artifact.bean.yaml.nexusartifact.NexusRegistryNugetConfig;
import io.harness.cdng.artifact.bean.yaml.nexusartifact.NexusRegistryRawConfig;
import io.harness.cdng.expressionEvaluator.CustomScriptSecretExpressionEvaluator;
import io.harness.cdng.expressionEvaluator.NgCustomSecretExpressionEvaluator;
import io.harness.common.ParameterFieldHelper;
import io.harness.data.algorithm.HashGenerator;
import io.harness.data.structure.EmptyPredicate;
import io.harness.delegate.beans.SecretDetail;
import io.harness.delegate.beans.connector.artifactoryconnector.ArtifactoryConnectorDTO;
import io.harness.delegate.beans.connector.awsconnector.AwsConnectorDTO;
import io.harness.delegate.beans.connector.azureartifacts.AzureArtifactsConnectorDTO;
import io.harness.delegate.beans.connector.azureconnector.AzureConnectorDTO;
import io.harness.delegate.beans.connector.bamboo.BambooConnectorDTO;
import io.harness.delegate.beans.connector.docker.DockerConnectorDTO;
import io.harness.delegate.beans.connector.gcpconnector.GcpConnectorDTO;
import io.harness.delegate.beans.connector.jenkins.JenkinsConnectorDTO;
import io.harness.delegate.beans.connector.nexusconnector.NexusConnectorDTO;
import io.harness.delegate.beans.connector.scm.github.GithubConnectorDTO;
import io.harness.delegate.task.artifacts.ArtifactDelegateRequestUtils;
import io.harness.delegate.task.artifacts.ArtifactSourceDelegateRequest;
import io.harness.delegate.task.artifacts.ArtifactSourceType;
import io.harness.delegate.task.artifacts.ami.AMIArtifactDelegateRequest;
import io.harness.delegate.task.artifacts.ami.AMIFilter;
import io.harness.delegate.task.artifacts.ami.AMITag;
import io.harness.delegate.task.artifacts.artifactory.ArtifactoryArtifactDelegateRequest;
import io.harness.delegate.task.artifacts.artifactory.ArtifactoryGenericArtifactDelegateRequest;
import io.harness.delegate.task.artifacts.azureartifacts.AzureArtifactsDelegateRequest;
import io.harness.delegate.task.artifacts.bamboo.BambooArtifactDelegateRequest;
import io.harness.delegate.task.artifacts.custom.CustomArtifactDelegateRequest;
import io.harness.delegate.task.artifacts.docker.DockerArtifactDelegateRequest;
import io.harness.delegate.task.artifacts.ecr.EcrArtifactDelegateRequest;
import io.harness.delegate.task.artifacts.gar.GarDelegateRequest;
import io.harness.delegate.task.artifacts.gcr.GcrArtifactDelegateRequest;
import io.harness.delegate.task.artifacts.githubpackages.GithubPackagesArtifactDelegateRequest;
import io.harness.delegate.task.artifacts.googlecloudsource.GoogleCloudSourceArtifactDelegateRequest;
import io.harness.delegate.task.artifacts.googlecloudsource.GoogleCloudSourceFetchType;
import io.harness.delegate.task.artifacts.googlecloudstorage.GoogleCloudStorageArtifactDelegateRequest;
import io.harness.delegate.task.artifacts.jenkins.JenkinsArtifactDelegateRequest;
import io.harness.delegate.task.artifacts.nexus.NexusArtifactDelegateRequest;
import io.harness.delegate.task.artifacts.s3.S3ArtifactDelegateRequest;
import io.harness.eraro.Level;
import io.harness.exception.InvalidArtifactServerException;
import io.harness.exception.InvalidRequestException;
import io.harness.expression.common.ExpressionMode;
import io.harness.metrics.intfc.DelegateMetricsService;
import io.harness.ng.core.NGAccess;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.yaml.ParameterField;
import io.harness.pms.yaml.validation.InputSetValidator;
import io.harness.secretmanagerclient.services.api.SecretManagerClientService;
import io.harness.security.encryption.EncryptedDataDetail;
import io.harness.security.encryption.EncryptionConfig;
import io.harness.yaml.core.timeout.Timeout;
import io.harness.yaml.utils.NGVariablesUtils;

import software.wings.expression.NgSecretManagerFunctor;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import lombok.experimental.UtilityClass;
import org.apache.commons.lang3.StringUtils;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_ARTIFACTS})
@UtilityClass
@OwnedBy(HarnessTeam.PIPELINE)
public class ArtifactConfigToDelegateReqMapper {
  private final String ACCEPT_ALL_REGEX = "\\*";
  private final String LAST_PUBLISHED_EXPRESSION = "<+lastPublished.tag>";
  private final long TIME_OUT = 600000L;

  public DockerArtifactDelegateRequest getDockerDelegateRequest(DockerHubArtifactConfig artifactConfig,
      DockerConnectorDTO connectorDTO, List<EncryptedDataDetail> encryptedDataDetails, String connectorRef) {
    // If both are empty, regex is latest among all docker artifacts.
    String tagRegex =
        artifactConfig.getTagRegex() != null ? (String) artifactConfig.getTagRegex().fetchFinalValue() : "";
    String tag = artifactConfig.getTag() != null ? (String) artifactConfig.getTag().fetchFinalValue() : "";

    if (isLastPublishedExpression(tag)) {
      tagRegex = getTagRegex(tag);
    }

    if (ParameterField.isNotNull(artifactConfig.getTag())
        && tagHasInputValidator(artifactConfig.getTag().getInputSetValidator(), tag)) {
      tagRegex = artifactConfig.getTag().getInputSetValidator().getParameters();
    }

    if (isEmpty(tag) && isEmpty(tagRegex)) {
      tagRegex = ACCEPT_ALL_REGEX;
    }

    boolean shouldFetchDockerV2DigestSHA256 =
        artifactConfig.getDigest() != null && isNotEmpty((String) artifactConfig.getDigest().fetchFinalValue());
    return ArtifactDelegateRequestUtils.getDockerDelegateRequest(
        (String) artifactConfig.getImagePath().fetchFinalValue(), tag, tagRegex, null, connectorRef, connectorDTO,
        encryptedDataDetails, ArtifactSourceType.DOCKER_REGISTRY, shouldFetchDockerV2DigestSHA256);
  }

  public boolean isLastPublishedExpression(String tag) {
    if (EmptyPredicate.isNotEmpty(tag) && tag.equals(LAST_PUBLISHED_EXPRESSION)) {
      return true;
    } else {
      return false;
    }
  }

  public boolean tagHasInputValidator(InputSetValidator inputSetValidator, String tag) {
    if (isLastPublishedExpression(tag) && inputSetValidator != null && isNotEmpty(inputSetValidator.getParameters())) {
      return true;
    }
    return false;
  }

  public String getTagRegex(String tag) {
    return tag.equals(LAST_PUBLISHED_EXPRESSION) ? ".*?" : tag;
  }

  public S3ArtifactDelegateRequest getAmazonS3DelegateRequest(AmazonS3ArtifactConfig artifactConfig,
      AwsConnectorDTO connectorDTO, List<EncryptedDataDetail> encryptedDataDetails, String connectorRef) {
    String bucket = (String) artifactConfig.getBucketName().fetchFinalValue();
    String filePath = (String) artifactConfig.getFilePath().fetchFinalValue();
    String filePathRegex = (String) artifactConfig.getFilePathRegex().fetchFinalValue();

    if (isLastPublishedExpression(filePath)) {
      if (ParameterField.isNotNull(artifactConfig.getFilePath())
          && tagHasInputValidator(artifactConfig.getFilePath().getInputSetValidator(), filePath)) {
        filePathRegex = artifactConfig.getFilePath().getInputSetValidator().getParameters();
      } else {
        filePathRegex = "*";
      }
      filePath = "";
    }

    if (StringUtils.isBlank(bucket)) {
      throw new InvalidRequestException("Please input bucketName.");
    }

    if (StringUtils.isAllBlank(filePathRegex, filePath)) {
      throw new InvalidRequestException("Please input one of the field - filePath or filePathRegex.");
    }

    if (StringUtils.isBlank(filePath)) {
      filePath = "";
    }

    if (StringUtils.isBlank(filePathRegex)) {
      filePathRegex = "";
    }
    return ArtifactDelegateRequestUtils.getAmazonS3DelegateRequest(
        (String) artifactConfig.getBucketName().fetchFinalValue(), filePath, filePathRegex, null, connectorRef,
        connectorDTO, encryptedDataDetails, ArtifactSourceType.AMAZONS3,
        artifactConfig.getRegion() != null ? (String) artifactConfig.getRegion().fetchFinalValue() : "us-east-1");
  }

  public GithubPackagesArtifactDelegateRequest getGithubPackagesDelegateRequest(
      GithubPackagesArtifactConfig artifactConfig, GithubConnectorDTO connectorDTO,
      List<EncryptedDataDetail> encryptedDataDetails, String connectorRef) {
    String versionRegex = artifactConfig.getVersionRegex() != null
        ? (StringUtils.isBlank((String) artifactConfig.getVersionRegex().fetchFinalValue())
                ? ""
                : (String) artifactConfig.getVersionRegex().fetchFinalValue())
        : "";
    String version = artifactConfig.getVersion() != null
        ? StringUtils.isBlank((String) artifactConfig.getVersion().fetchFinalValue())
            ? ""
            : (String) artifactConfig.getVersion().fetchFinalValue()
        : "";

    if (isLastPublishedExpression(version)) {
      versionRegex =
          version.equals(LAST_PUBLISHED_EXPRESSION) ? version.replace(LAST_PUBLISHED_EXPRESSION, "*") : version;
    }

    if (ParameterField.isNotNull(artifactConfig.getVersion())
        && tagHasInputValidator(artifactConfig.getVersion().getInputSetValidator(), version)) {
      versionRegex = artifactConfig.getVersion().getInputSetValidator().getParameters();
    }

    // If both version and versionRegex are empty, versionRegex is latest among all versions.
    if (isEmpty(version) && isEmpty(versionRegex)) {
      versionRegex = "*";
    }

    if (ParameterField.isNotNull(artifactConfig.getPackageType())
        && artifactConfig.getPackageType().fetchFinalValue().equals("maven")) {
      verifyArtifactConfigForGithubPackagesMavenType(artifactConfig);
      String user =
          ParameterField.isNotNull(artifactConfig.getUser()) ? (String) artifactConfig.getUser().fetchFinalValue() : "";
      String org =
          ParameterField.isNotNull(artifactConfig.getOrg()) ? (String) artifactConfig.getOrg().fetchFinalValue() : "";
      if (StringUtils.isBlank(user) && StringUtils.isBlank(org)) {
        throw new InvalidRequestException("Please provide User or Organization field");
      }
      return ArtifactDelegateRequestUtils.getGithubPackagesDelegateRequest(
          (String) artifactConfig.getPackageName().fetchFinalValue(),
          (String) artifactConfig.getPackageType().fetchFinalValue(), version, versionRegex, org, connectorRef,
          connectorDTO, encryptedDataDetails, ArtifactSourceType.GITHUB_PACKAGES,
          (String) artifactConfig.getArtifactId().fetchFinalValue(), "repository", user,
          ParameterField.isNotNull(artifactConfig.getExtension())
              ? (String) artifactConfig.getExtension().fetchFinalValue()
              : "",
          (String) artifactConfig.getGroupId().fetchFinalValue());
    }
    return ArtifactDelegateRequestUtils.getGithubPackagesDelegateRequest(
        (String) artifactConfig.getPackageName().fetchFinalValue(),
        (String) artifactConfig.getPackageType().fetchFinalValue(), version, versionRegex,
        (String) artifactConfig.getOrg().fetchFinalValue(), connectorRef, connectorDTO, encryptedDataDetails,
        ArtifactSourceType.GITHUB_PACKAGES);
  }
  void verifyArtifactConfigForGithubPackagesMavenType(GithubPackagesArtifactConfig artifactConfig) {
    if (ParameterField.isBlank(artifactConfig.getArtifactId())) {
      throw new InvalidRequestException("ArtifactId field cannot be blank");
    }

    if (ParameterField.isBlank(artifactConfig.getGroupId())) {
      throw new InvalidRequestException("GroupId field cannot be blank");
    }
  }
  public AzureArtifactsDelegateRequest getAzureArtifactsDelegateRequest(AzureArtifactsConfig artifactConfig,
      AzureArtifactsConnectorDTO connectorDTO, List<EncryptedDataDetail> encryptedDataDetails, String connectorRef) {
    String versionRegex = (String) artifactConfig.getVersionRegex().fetchFinalValue();

    if (StringUtils.isBlank(versionRegex)) {
      versionRegex = "";
    }

    String version = (String) artifactConfig.getVersion().fetchFinalValue();

    if (isLastPublishedExpression(version)) {
      versionRegex = getTagRegex(version);
    }

    if (StringUtils.isBlank(version)) {
      version = "";
    }

    // If both version and versionRegex are empty, throw exception.
    if (StringUtils.isAllBlank(version, versionRegex)) {
      versionRegex = "*";
    }

    if (ParameterField.isNotNull(artifactConfig.getVersion())
        && tagHasInputValidator(artifactConfig.getVersion().getInputSetValidator(), version)) {
      versionRegex = artifactConfig.getVersion().getInputSetValidator().getParameters();
    }

    String scope = null;
    if (artifactConfig.getScope() != null) {
      scope = (String) artifactConfig.getScope().fetchFinalValue();
    }

    return ArtifactDelegateRequestUtils.getAzureArtifactsDelegateRequest(
        (String) artifactConfig.getPackageName().fetchFinalValue(),
        (String) artifactConfig.getPackageType().fetchFinalValue(), version, versionRegex,
        (String) artifactConfig.getProject().fetchFinalValue(), scope,
        (String) artifactConfig.getFeed().fetchFinalValue(), connectorRef, connectorDTO, encryptedDataDetails,
        ArtifactSourceType.AZURE_ARTIFACTS);
  }

  public AMIArtifactDelegateRequest getAMIDelegateRequest(AMIArtifactConfig artifactConfig,
      AwsConnectorDTO connectorDTO, List<EncryptedDataDetail> encryptedDataDetails, String connectorRef) {
    String versionRegex = "";

    if (artifactConfig.getVersionRegex() != null
        && isNotEmpty((String) artifactConfig.getVersionRegex().fetchFinalValue())) {
      versionRegex = (String) artifactConfig.getVersionRegex().fetchFinalValue();
    }

    if (artifactConfig.getVersionRegex() != null && artifactConfig.getVersionRegex().getInputSetValidator() != null) {
      versionRegex = artifactConfig.getVersionRegex().getInputSetValidator().getParameters();
    }

    String version = (String) artifactConfig.getVersion().fetchFinalValue();

    if (isLastPublishedExpression(version)) {
      versionRegex = (String) artifactConfig.getVersion().fetchFinalValue();
    }

    if (StringUtils.isBlank(version)) {
      version = "";
    }

    // If both version and versionRegex are empty, throw exception.
    if (StringUtils.isAllBlank(version, versionRegex)) {
      versionRegex = "*";
    }

    if (ParameterField.isNotNull(artifactConfig.getVersion())
        && tagHasInputValidator(artifactConfig.getVersion().getInputSetValidator(), version)) {
      versionRegex = artifactConfig.getVersion().getInputSetValidator().getParameters();
    }

    return ArtifactDelegateRequestUtils.getAMIArtifactDelegateRequest(
        (List<AMITag>) artifactConfig.getTags().fetchFinalValue(),
        (List<AMIFilter>) artifactConfig.getFilters().fetchFinalValue(),
        (String) artifactConfig.getRegion().fetchFinalValue(), version, versionRegex, connectorRef, connectorDTO,
        encryptedDataDetails, ArtifactSourceType.AMI);
  }

  public JenkinsArtifactDelegateRequest getJenkinsDelegateRequest(JenkinsArtifactConfig artifactConfig,
      JenkinsConnectorDTO connectorDTO, List<EncryptedDataDetail> encryptedDataDetails, String connectorRef) {
    String artifactPath =
        artifactConfig.getArtifactPath() != null ? (String) artifactConfig.getArtifactPath().fetchFinalValue() : "";
    String jobName = artifactConfig.getJobName() != null ? (String) artifactConfig.getJobName().fetchFinalValue() : "";
    String buildNumber = artifactConfig.getBuild() != null ? (String) artifactConfig.getBuild().fetchFinalValue() : "";
    String buildRegex = null;
    if (isLastPublishedExpression(buildNumber)) {
      if (ParameterField.isNotNull(artifactConfig.getBuild())
          && tagHasInputValidator(artifactConfig.getBuild().getInputSetValidator(), buildNumber)) {
        buildRegex = artifactConfig.getBuild().getInputSetValidator().getParameters();
        buildNumber = artifactConfig.getBuild().getInputSetValidator().getParameters();
      } else {
        buildRegex = "";
        buildNumber = "";
      }
    }

    return ArtifactDelegateRequestUtils.getJenkinsDelegateArtifactRequest(connectorRef, connectorDTO,
        encryptedDataDetails, ArtifactSourceType.JENKINS, null, null, jobName, Arrays.asList(artifactPath), buildNumber,
        buildRegex);
  }

  public BambooArtifactDelegateRequest getBambooDelegateRequest(BambooArtifactConfig artifactConfig,
      BambooConnectorDTO connectorDTO, List<EncryptedDataDetail> encryptedDataDetails, String connectorRef) {
    List<String> artifactPath = artifactConfig.getArtifactPaths() != null
        ? (List<String>) artifactConfig.getArtifactPaths().fetchFinalValue()
        : Collections.emptyList();
    String planKey = artifactConfig.getPlanKey() != null ? (String) artifactConfig.getPlanKey().fetchFinalValue() : "";
    String buildNumber = artifactConfig.getBuild() != null ? (String) artifactConfig.getBuild().fetchFinalValue() : "";
    String buildRegex = null;
    if (isLastPublishedExpression(buildNumber)) {
      if (ParameterField.isNotNull(artifactConfig.getBuild())
          && tagHasInputValidator(artifactConfig.getBuild().getInputSetValidator(), buildNumber)) {
        buildNumber = artifactConfig.getBuild().getInputSetValidator().getParameters();
        buildRegex = artifactConfig.getBuild().getInputSetValidator().getParameters();
      } else {
        buildNumber = getTagRegex(buildNumber);
        buildRegex = getTagRegex(buildNumber);
      }
    }
    return ArtifactDelegateRequestUtils.getBambooDelegateArtifactRequest(connectorRef, connectorDTO,
        encryptedDataDetails, ArtifactSourceType.BAMBOO, planKey, artifactPath, buildNumber, buildRegex);
  }
  public CustomArtifactDelegateRequest getCustomDelegateRequest(
      CustomArtifactConfig artifactConfig, Ambiance ambiance) {
    CustomScriptInlineSource customScriptInlineSource = getCustomScriptInlineSource(artifactConfig);
    long timeout = TIME_OUT;
    String version = (String) artifactConfig.getVersion().fetchFinalValue();
    String versionRegex = "";
    if (artifactConfig.getVersionRegex() != null
        && isNotEmpty((String) artifactConfig.getVersionRegex().fetchFinalValue())) {
      versionRegex = (String) artifactConfig.getVersionRegex().fetchFinalValue();
    }

    if (artifactConfig.getTimeout() != null && artifactConfig.getTimeout().fetchFinalValue() != null
        && isNotEmpty(artifactConfig.getTimeout().fetchFinalValue().toString())) {
      timeout = ((Timeout) artifactConfig.getTimeout().fetchFinalValue()).getTimeoutInMillis();
    }
    if (isNotEmpty(version) && isLastPublishedExpression(version)) {
      versionRegex = getTagRegex(version);
    }

    if (ParameterField.isNotNull(artifactConfig.getVersion())
        && tagHasInputValidator(artifactConfig.getVersion().getInputSetValidator(), version)) {
      versionRegex = artifactConfig.getVersion().getInputSetValidator().getParameters();
    }

    String script = customScriptInlineSource.getScript().fetchFinalValue().toString();
    return ArtifactDelegateRequestUtils.getCustomDelegateRequest(
        artifactConfig.getScripts().getFetchAllArtifacts().getArtifactsArrayPath().fetchFinalValue().toString(),
        versionRegex,
        artifactConfig.getScripts().getFetchAllArtifacts().getShellScriptBaseStepInfo().getSource().getType(),
        ArtifactSourceType.CUSTOM_ARTIFACT,
        (String) artifactConfig.getScripts().getFetchAllArtifacts().getVersionPath().fetchFinalValue(), script,
        NGVariablesUtils.getStringMapVariables(artifactConfig.getScripts().getFetchAllArtifacts().getAttributes(), 0L),
        NGVariablesUtils.getStringMapVariables(artifactConfig.getInputs(), 0L), version,
        ambiance != null ? AmbianceUtils.obtainCurrentRuntimeId(ambiance) : "", timeout,
        AmbianceUtils.getAccountId(ambiance));
  }

  public CustomArtifactDelegateRequest getCustomDelegateRequest(CustomArtifactConfig artifactConfig, Ambiance ambiance,
      DelegateMetricsService delegateMetricsService, SecretManagerClientService ngSecretService) {
    String versionRegex = (String) artifactConfig.getVersionRegex().fetchFinalValue();
    String version = (String) artifactConfig.getVersion().fetchFinalValue();

    if (StringUtils.isBlank(versionRegex)) {
      versionRegex = "";
    }

    if (isNotEmpty(version) && isLastPublishedExpression(version)) {
      versionRegex = getTagRegex(version);
    }

    if (ParameterField.isNotNull(artifactConfig.getVersion())
        && tagHasInputValidator(artifactConfig.getVersion().getInputSetValidator(), version)) {
      versionRegex = artifactConfig.getVersion().getInputSetValidator().getParameters();
    }

    NGAccess ngAccess = AmbianceUtils.getNgAccess(ambiance);
    int secretFunctorToken = HashGenerator.generateIntegerHash();
    CustomScriptInlineSource customScriptInlineSource = getCustomScriptInlineSource(artifactConfig);
    String script = customScriptInlineSource.getScript().fetchFinalValue().toString();
    script = resolveNGSecretExpression(script, secretFunctorToken);
    if (isNotEmpty(version) && isLastPublishedExpression(version)) {
      version = getTagRegex(version);
    }
    NgCustomSecretExpressionEvaluator ngCustomSecretExpressionEvaluator =
        new NgCustomSecretExpressionEvaluator(ngAccess.getAccountIdentifier(), ngAccess.getOrgIdentifier(),
            ngAccess.getProjectIdentifier(), secretFunctorToken, delegateMetricsService, ngSecretService);
    script = ngCustomSecretExpressionEvaluator.substitute(script, Collections.emptyMap());
    NgSecretManagerFunctor ngSecretManagerFunctor =
        (NgSecretManagerFunctor) ngCustomSecretExpressionEvaluator.getExpressionFunctor();
    Map<String, EncryptionConfig> encryptionConfigs = ngSecretManagerFunctor.getEncryptionConfigs();
    Map<String, SecretDetail> secretDetails = ngSecretManagerFunctor.getSecretDetails();

    return ArtifactDelegateRequestUtils.getCustomDelegateRequest(
        artifactConfig.getScripts().getFetchAllArtifacts().getArtifactsArrayPath().fetchFinalValue().toString(),
        versionRegex,
        artifactConfig.getScripts().getFetchAllArtifacts().getShellScriptBaseStepInfo().getSource().getType(),
        ArtifactSourceType.CUSTOM_ARTIFACT,
        (String) artifactConfig.getScripts().getFetchAllArtifacts().getVersionPath().fetchFinalValue(), script,
        NGVariablesUtils.getStringMapVariables(artifactConfig.getScripts().getFetchAllArtifacts().getAttributes(), 0L),
        NGVariablesUtils.getStringMapVariables(artifactConfig.getInputs(), 0L), version,
        ambiance != null ? AmbianceUtils.obtainCurrentRuntimeId(ambiance) : "",
        artifactConfig.getTimeout() != null
            ? ((Timeout) artifactConfig.getTimeout().fetchFinalValue()).getTimeoutInMillis()
            : TIME_OUT,
        AmbianceUtils.getAccountId(ambiance), encryptionConfigs, secretDetails,
        ngSecretManagerFunctor.getExpressionFunctorToken());
  }

  public GoogleCloudSourceArtifactDelegateRequest getGoogleCloudSourceArtifactDelegateRequest(
      GoogleCloudSourceArtifactConfig artifactConfig, GcpConnectorDTO gcpConnectorDTO,
      List<EncryptedDataDetail> encryptedDataDetails, String connectorRef) {
    String project = (String) artifactConfig.getProject().fetchFinalValue();
    String repository = (String) artifactConfig.getRepository().fetchFinalValue();
    String sourceDirectory = (String) artifactConfig.getSourceDirectory().fetchFinalValue();
    String branch = ParameterFieldHelper.getParameterFieldFinalValueString(artifactConfig.getBranch());
    String commitId = ParameterFieldHelper.getParameterFieldFinalValueString(artifactConfig.getCommitId());
    String tag = ParameterFieldHelper.getParameterFieldFinalValueString(artifactConfig.getTag());
    if (StringUtils.isBlank(project)) {
      throw new InvalidRequestException("Please input project name.");
    }
    if (StringUtils.isBlank(repository)) {
      throw new InvalidRequestException("Please input repository name.");
    }
    if (StringUtils.isBlank(sourceDirectory)) {
      throw new InvalidRequestException("Please input sourceDirectory path.");
    }
    if (StringUtils.isAllBlank(branch, commitId, tag)) {
      throw new InvalidRequestException("Please input one of these three, branch, commitId, Tag.");
    }
    return ArtifactDelegateRequestUtils.getGoogleCloudSourceArtifactDelegateRequest(repository, project,
        sourceDirectory,
        GoogleCloudSourceFetchType.valueOf(StringUtils.upperCase(artifactConfig.getFetchType().getName())), branch,
        commitId, tag, gcpConnectorDTO, connectorRef, encryptedDataDetails,
        ArtifactSourceType.GOOGLE_CLOUD_SOURCE_ARTIFACT);
  }

  private String resolveNGSecretExpression(String script, int secretFunctor) {
    CustomScriptSecretExpressionEvaluator customScriptSecretExpressionEvaluator =
        new CustomScriptSecretExpressionEvaluator(script, secretFunctor);
    return customScriptSecretExpressionEvaluator.renderExpression(
        script, ExpressionMode.RETURN_ORIGINAL_EXPRESSION_IF_UNRESOLVED);
  }

  public CustomScriptInlineSource getCustomScriptInlineSource(CustomArtifactConfig artifactConfig) {
    if (artifactConfig.getScripts() != null && artifactConfig.getScripts().getFetchAllArtifacts() != null
        && artifactConfig.getScripts().getFetchAllArtifacts().getShellScriptBaseStepInfo() != null
        && artifactConfig.getScripts().getFetchAllArtifacts().getShellScriptBaseStepInfo().getSource() != null
        && artifactConfig.getScripts().getFetchAllArtifacts().getShellScriptBaseStepInfo().getSource().getSpec()
            != null) {
      CustomScriptInlineSource customScriptInlineSource = (CustomScriptInlineSource) artifactConfig.getScripts()
                                                              .getFetchAllArtifacts()
                                                              .getShellScriptBaseStepInfo()
                                                              .getSource()
                                                              .getSpec();
      if (customScriptInlineSource != null && customScriptInlineSource.getScript() != null
          && isNotEmpty((String) customScriptInlineSource.getScript().fetchFinalValue())) {
        if (artifactConfig.getScripts().getFetchAllArtifacts().getArtifactsArrayPath() == null
            || isEmpty((String) artifactConfig.getScripts()
                           .getFetchAllArtifacts()
                           .getArtifactsArrayPath()
                           .fetchFinalValue())) {
          throw new InvalidArtifactServerException("Artifacts Array Path is missing", Level.ERROR, USER);
        }
        if (artifactConfig.getScripts().getFetchAllArtifacts().getVersionPath() == null
            || isEmpty(
                (String) artifactConfig.getScripts().getFetchAllArtifacts().getVersionPath().fetchFinalValue())) {
          throw new InvalidArtifactServerException("Version Path is missing", Level.ERROR, USER);
        }
      }
      return customScriptInlineSource;
    }
    throw new InvalidArtifactServerException("Invalid Custom Artifact Yaml", Level.ERROR, USER);
  }

  public GcrArtifactDelegateRequest getGcrDelegateRequest(GcrArtifactConfig gcrArtifactConfig,
      GcpConnectorDTO gcpConnectorDTO, List<EncryptedDataDetail> encryptedDataDetails, String connectorRef) {
    // If both are empty, regex is latest among all gcr artifacts.
    String tagRegex =
        gcrArtifactConfig.getTagRegex() != null ? (String) gcrArtifactConfig.getTagRegex().fetchFinalValue() : "";
    String tag = gcrArtifactConfig.getTag() != null ? (String) gcrArtifactConfig.getTag().fetchFinalValue() : "";

    if (isLastPublishedExpression(tag)) {
      tagRegex = getTagRegex(tag);
    }
    if (isEmpty(tag) && isEmpty(tagRegex)) {
      tagRegex = ACCEPT_ALL_REGEX;
    }

    if (ParameterField.isNotNull(gcrArtifactConfig.getTag())
        && tagHasInputValidator(gcrArtifactConfig.getTag().getInputSetValidator(), tag)) {
      tagRegex = gcrArtifactConfig.getTag().getInputSetValidator().getParameters();
    }

    return ArtifactDelegateRequestUtils.getGcrDelegateRequest(
        (String) gcrArtifactConfig.getImagePath().fetchFinalValue(), tag, tagRegex, null,
        (String) gcrArtifactConfig.getRegistryHostname().fetchFinalValue(), connectorRef, gcpConnectorDTO,
        encryptedDataDetails, ArtifactSourceType.GCR);
  }
  public GarDelegateRequest getGarDelegateRequest(GoogleArtifactRegistryConfig garArtifactConfig,
      GcpConnectorDTO gcpConnectorDTO, List<EncryptedDataDetail> encryptedDataDetails, String connectorRef) {
    // If both are empty, regex is latest among all gcr artifacts.
    String versionRegex = garArtifactConfig.getVersionRegex() != null
        ? (String) garArtifactConfig.getVersionRegex().fetchFinalValue()
        : "";
    String version =
        garArtifactConfig.getVersion() != null ? (String) garArtifactConfig.getVersion().fetchFinalValue() : "";

    if (isLastPublishedExpression(version)) {
      versionRegex = getTagRegex(version);
      version = "";
    }
    if (StringUtils.isBlank(version) && StringUtils.isBlank(versionRegex)) {
      versionRegex = "/*";
    }
    if (ParameterField.isNotNull(garArtifactConfig.getVersion())
        && tagHasInputValidator(garArtifactConfig.getVersion().getInputSetValidator(),
            ParameterField.isNotNull(garArtifactConfig.getVersion())
                ? (String) garArtifactConfig.getVersion().fetchFinalValue()
                : version)) {
      versionRegex = garArtifactConfig.getVersion().getInputSetValidator().getParameters();
    }

    return ArtifactDelegateRequestUtils.getGoogleArtifactDelegateRequest(
        (String) garArtifactConfig.getRegion().fetchFinalValue(),
        (String) garArtifactConfig.getRepositoryName().fetchFinalValue(),
        (String) garArtifactConfig.getProject().fetchFinalValue(),
        (String) garArtifactConfig.getPkg().fetchFinalValue(), version, versionRegex, gcpConnectorDTO,
        encryptedDataDetails, ArtifactSourceType.GOOGLE_ARTIFACT_REGISTRY, Integer.MAX_VALUE);
  }

  public EcrArtifactDelegateRequest getEcrDelegateRequest(EcrArtifactConfig ecrArtifactConfig,
      AwsConnectorDTO awsConnectorDTO, List<EncryptedDataDetail> encryptedDataDetails, String connectorRef) {
    // If both are empty, regex is latest among all ecr artifacts.
    String tagRegex =
        ecrArtifactConfig.getTagRegex() != null ? (String) ecrArtifactConfig.getTagRegex().fetchFinalValue() : "";
    String tag = ecrArtifactConfig.getTag() != null ? (String) ecrArtifactConfig.getTag().fetchFinalValue() : "";
    if (isLastPublishedExpression(tag)) {
      tagRegex = tag.equals(LAST_PUBLISHED_EXPRESSION) ? "*" : tag;
    }
    if (isEmpty(tag) && isEmpty(tagRegex)) {
      tagRegex = ACCEPT_ALL_REGEX;
    }
    if (ParameterField.isNotNull(ecrArtifactConfig.getTag())
        && tagHasInputValidator(ecrArtifactConfig.getTag().getInputSetValidator(), tag)) {
      tagRegex = ecrArtifactConfig.getTag().getInputSetValidator().getParameters();
    }
    String registryId = null;
    if (ParameterField.isNotNull(ecrArtifactConfig.getRegistryId())) {
      registryId = (String) ecrArtifactConfig.getRegistryId().fetchFinalValue();
    }

    return ArtifactDelegateRequestUtils.getEcrDelegateRequest(registryId,
        (String) ecrArtifactConfig.getImagePath().fetchFinalValue(), tag, tagRegex, null,
        (String) ecrArtifactConfig.getRegion().fetchFinalValue(), connectorRef, awsConnectorDTO, encryptedDataDetails,
        ArtifactSourceType.ECR);
  }

  public NexusArtifactDelegateRequest getNexusArtifactDelegateRequest(NexusRegistryArtifactConfig artifactConfig,
      NexusConnectorDTO nexusConnectorDTO, List<EncryptedDataDetail> encryptedDataDetails, String connectorRef) {
    // If both are empty, regex is latest among all docker artifacts.
    String tagRegex =
        artifactConfig.getTagRegex() != null ? (String) artifactConfig.getTagRegex().fetchFinalValue() : "";
    String tag = artifactConfig.getTag() != null ? (String) artifactConfig.getTag().fetchFinalValue() : "";
    if (isLastPublishedExpression(tag)) {
      tagRegex = getTagRegex(tag);
    }
    if (isEmpty(tag) && isEmpty(tagRegex)) {
      tagRegex = ACCEPT_ALL_REGEX;
    }

    if (ParameterField.isNotNull(artifactConfig.getTag())
        && tagHasInputValidator(artifactConfig.getTag().getInputSetValidator(), tag)) {
      tagRegex = artifactConfig.getTag().getInputSetValidator().getParameters();
    }

    String packageName = null;
    String groupId = null;
    String artifactId = null;
    String extension = null;
    String classifier = null;
    String port = null;
    String artifactRepositoryUrl = null;
    String group = null;
    if (((String) artifactConfig.getRepositoryFormat().fetchFinalValue()).equalsIgnoreCase("npm")) {
      NexusRegistryNpmConfig nexusRegistryNpmConfig =
          (NexusRegistryNpmConfig) artifactConfig.getNexusRegistryConfigSpec();
      packageName = (String) nexusRegistryNpmConfig.getPackageName().fetchFinalValue();
    } else if (((String) artifactConfig.getRepositoryFormat().fetchFinalValue()).equalsIgnoreCase("nuget")) {
      NexusRegistryNugetConfig nexusRegistryNugetConfig =
          (NexusRegistryNugetConfig) artifactConfig.getNexusRegistryConfigSpec();
      packageName = (String) nexusRegistryNugetConfig.getPackageName().fetchFinalValue();
    } else if (((String) artifactConfig.getRepositoryFormat().fetchFinalValue()).equalsIgnoreCase("raw")) {
      NexusRegistryRawConfig nexusRegistryRawConfig =
          (NexusRegistryRawConfig) artifactConfig.getNexusRegistryConfigSpec();
      group = (String) nexusRegistryRawConfig.getGroup().fetchFinalValue();
    } else if (((String) artifactConfig.getRepositoryFormat().fetchFinalValue()).equalsIgnoreCase("docker")) {
      NexusRegistryDockerConfig nexusRegistryDockerConfig =
          (NexusRegistryDockerConfig) artifactConfig.getNexusRegistryConfigSpec();
      port = nexusRegistryDockerConfig.getRepositoryPort() != null
          ? (String) nexusRegistryDockerConfig.getRepositoryPort().fetchFinalValue()
          : null;
      artifactRepositoryUrl = nexusRegistryDockerConfig.getRepositoryUrl() != null
          ? (String) nexusRegistryDockerConfig.getRepositoryUrl().fetchFinalValue()
          : null;
      artifactId = nexusRegistryDockerConfig.getArtifactPath() != null
          ? (String) nexusRegistryDockerConfig.getArtifactPath().fetchFinalValue()
          : null;
    } else {
      NexusRegistryMavenConfig nexusRegistryMavenConfig =
          (NexusRegistryMavenConfig) artifactConfig.getNexusRegistryConfigSpec();
      groupId = (String) nexusRegistryMavenConfig.getGroupId().fetchFinalValue();
      artifactId = (String) nexusRegistryMavenConfig.getArtifactId().fetchFinalValue();
      extension = (String) nexusRegistryMavenConfig.getExtension().fetchFinalValue();
      classifier = (String) nexusRegistryMavenConfig.getClassifier().fetchFinalValue();
    }

    return ArtifactDelegateRequestUtils.getNexusArtifactDelegateRequest(
        (String) artifactConfig.getRepository().fetchFinalValue(), port, artifactId,
        (String) artifactConfig.getRepositoryFormat().fetchFinalValue(), artifactRepositoryUrl, tag, tagRegex,
        connectorRef, nexusConnectorDTO, encryptedDataDetails, ArtifactSourceType.NEXUS3_REGISTRY, groupId, artifactId,
        extension, classifier, packageName, group);
  }

  public NexusArtifactDelegateRequest getNexus2ArtifactDelegateRequest(Nexus2RegistryArtifactConfig artifactConfig,
      NexusConnectorDTO nexusConnectorDTO, List<EncryptedDataDetail> encryptedDataDetails, String connectorRef) {
    // If both are empty, regex is latest among all docker artifacts.
    String tagRegex =
        artifactConfig.getTagRegex() != null ? (String) artifactConfig.getTagRegex().fetchFinalValue() : "";

    String tag = artifactConfig.getTag() != null ? (String) artifactConfig.getTag().fetchFinalValue() : "";
    if (isLastPublishedExpression(tag)) {
      tagRegex = getTagRegex(tag);
    }
    if (isEmpty(tag) && isEmpty(tagRegex)) {
      tagRegex = ACCEPT_ALL_REGEX;
    }

    if (ParameterField.isNotNull(artifactConfig.getTag())
        && tagHasInputValidator(artifactConfig.getTag().getInputSetValidator(), tag)) {
      tagRegex = artifactConfig.getTag().getInputSetValidator().getParameters();
    }

    String packageName = null;
    String groupId = null;
    String artifactId = null;
    String extension = null;
    String classifier = null;
    if (((String) artifactConfig.getRepositoryFormat().fetchFinalValue()).equalsIgnoreCase("npm")) {
      NexusRegistryNpmConfig nexusRegistryNpmConfig =
          (NexusRegistryNpmConfig) artifactConfig.getNexusRegistryConfigSpec();
      packageName = (String) nexusRegistryNpmConfig.getPackageName().fetchFinalValue();
    } else if (((String) artifactConfig.getRepositoryFormat().fetchFinalValue()).equalsIgnoreCase("nuget")) {
      NexusRegistryNugetConfig nexusRegistryNugetConfig =
          (NexusRegistryNugetConfig) artifactConfig.getNexusRegistryConfigSpec();
      packageName = (String) nexusRegistryNugetConfig.getPackageName().fetchFinalValue();
    } else {
      NexusRegistryMavenConfig nexusRegistryMavenConfig =
          (NexusRegistryMavenConfig) artifactConfig.getNexusRegistryConfigSpec();
      groupId = (String) nexusRegistryMavenConfig.getGroupId().fetchFinalValue();
      artifactId = (String) nexusRegistryMavenConfig.getArtifactId().fetchFinalValue();
      extension = (String) nexusRegistryMavenConfig.getExtension().fetchFinalValue();
      classifier = (String) nexusRegistryMavenConfig.getClassifier().fetchFinalValue();
    }

    return ArtifactDelegateRequestUtils.getNexusArtifactDelegateRequest(
        (String) artifactConfig.getRepository().fetchFinalValue(), null, null,
        (String) artifactConfig.getRepositoryFormat().fetchFinalValue(), null, tag, tagRegex, connectorRef,
        nexusConnectorDTO, encryptedDataDetails, ArtifactSourceType.NEXUS2_REGISTRY, groupId, artifactId, extension,
        classifier, packageName, "");
  }

  public ArtifactSourceDelegateRequest getArtifactoryArtifactDelegateRequest(
      ArtifactoryRegistryArtifactConfig artifactConfig, ArtifactoryConnectorDTO artifactoryConnectorDTO,
      List<EncryptedDataDetail> encryptedDataDetails, String connectorRef) {
    if (artifactConfig.getRepositoryFormat().fetchFinalValue().equals(generic.name())) {
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
    String tagRegex =
        artifactConfig.getTagRegex() != null ? (String) artifactConfig.getTagRegex().fetchFinalValue() : "";
    String tag = artifactConfig.getTag() != null ? (String) artifactConfig.getTag().fetchFinalValue() : "";

    if (isLastPublishedExpression(tag)) {
      tagRegex = ACCEPT_ALL_REGEX;
    }
    if (isEmpty(tag) && isEmpty(tagRegex)) {
      tagRegex = ACCEPT_ALL_REGEX;
    }

    if (ParameterField.isNotNull(artifactConfig.getTag())
        && tagHasInputValidator(artifactConfig.getTag().getInputSetValidator(), tag)) {
      tagRegex = artifactConfig.getTag().getInputSetValidator().getParameters();
    }

    String artifactRepositoryUrl =
        artifactConfig.getRepositoryUrl() != null ? (String) artifactConfig.getRepositoryUrl().fetchFinalValue() : null;

    return (ArtifactoryArtifactDelegateRequest) ArtifactDelegateRequestUtils.getArtifactoryArtifactDelegateRequest(
        (String) artifactConfig.getRepository().fetchFinalValue(),
        (String) artifactConfig.getArtifactPath().fetchFinalValue(),
        (String) artifactConfig.getRepositoryFormat().fetchFinalValue(), artifactRepositoryUrl, tag, tagRegex,
        connectorRef, artifactoryConnectorDTO, encryptedDataDetails, ArtifactSourceType.ARTIFACTORY_REGISTRY);
  }

  private ArtifactoryGenericArtifactDelegateRequest getArtifactoryGenericArtifactDelegateRequest(
      ArtifactoryRegistryArtifactConfig artifactConfig, ArtifactoryConnectorDTO artifactoryConnectorDTO,
      List<EncryptedDataDetail> encryptedDataDetails, String connectorRef) {
    // If both are empty, artifactPathFilter is latest among all artifacts.
    String artifactPathFilter = ParameterField.isNull(artifactConfig.getArtifactPathFilter())
        ? ""
        : (String) artifactConfig.getArtifactPathFilter().fetchFinalValue();
    String artifactPath = ParameterField.isNull(artifactConfig.getArtifactPath())
        ? ""
        : (String) artifactConfig.getArtifactPath().fetchFinalValue();

    String artifactDirectory = ParameterField.isNull(artifactConfig.getArtifactDirectory())
        ? null
        : (String) artifactConfig.getArtifactDirectory().fetchFinalValue();

    if (isLastPublishedExpression(artifactPath)) {
      artifactPathFilter = "*";
    }

    if (ParameterField.isNotNull(artifactConfig.getArtifactPath())
        && tagHasInputValidator(artifactConfig.getArtifactPath().getInputSetValidator(), artifactPath)) {
      artifactPathFilter = artifactConfig.getArtifactPath().getInputSetValidator().getParameters();
    }

    return ArtifactDelegateRequestUtils.getArtifactoryGenericArtifactDelegateRequest(
        (String) artifactConfig.getRepository().fetchFinalValue(),
        (String) artifactConfig.getRepositoryFormat().fetchFinalValue(), artifactDirectory, artifactPath,
        artifactPathFilter, connectorRef, artifactoryConnectorDTO, encryptedDataDetails,
        ArtifactSourceType.ARTIFACTORY_REGISTRY);
  }

  public static ArtifactSourceDelegateRequest getAcrDelegateRequest(AcrArtifactConfig acrArtifactConfig,
      AzureConnectorDTO azureConnectorDTO, List<EncryptedDataDetail> encryptedDataDetails, String connectorRef) {
    // If both are empty, regex is latest among all acr artifacts.
    String tagRegex = ParameterField.isNull(acrArtifactConfig.getTagRegex())
        ? ""
        : (String) acrArtifactConfig.getTagRegex().fetchFinalValue();
    String tag =
        ParameterField.isNull(acrArtifactConfig.getTag()) ? "" : (String) acrArtifactConfig.getTag().fetchFinalValue();
    if (isLastPublishedExpression(tag)) {
      tagRegex = getTagRegex(tag);
    }
    if (isEmpty(tag) && isEmpty(tagRegex)) {
      tagRegex = ACCEPT_ALL_REGEX;
    }

    if (ParameterField.isNotNull(acrArtifactConfig.getTag())
        && tagHasInputValidator(acrArtifactConfig.getTag().getInputSetValidator(), tag)) {
      tagRegex = acrArtifactConfig.getTag().getInputSetValidator().getParameters();
    }

    return ArtifactDelegateRequestUtils.getAcrDelegateRequest(
        (String) acrArtifactConfig.getSubscriptionId().fetchFinalValue(),
        (String) acrArtifactConfig.getRegistry().fetchFinalValue(),
        (String) acrArtifactConfig.getRepository().fetchFinalValue(), azureConnectorDTO, tag, tagRegex, null,
        encryptedDataDetails, ArtifactSourceType.ACR);
  }

  public GoogleCloudStorageArtifactDelegateRequest getGoogleCloudStorageArtifactDelegateRequest(
      GoogleCloudStorageArtifactConfig artifactConfig, GcpConnectorDTO gcpConnectorDTO,
      List<EncryptedDataDetail> encryptedDataDetails, String connectorRef) {
    String project = (String) artifactConfig.getProject().fetchFinalValue();
    String bucket = (String) artifactConfig.getBucket().fetchFinalValue();
    String artifactPath = (String) artifactConfig.getArtifactPath().fetchFinalValue();
    if (StringUtils.isBlank(project)) {
      throw new InvalidRequestException("Please input project name.");
    }
    if (StringUtils.isBlank(bucket)) {
      throw new InvalidRequestException("Please input bucket name.");
    }

    String artifactPathRegex = null;
    if (isLastPublishedExpression(artifactPath)) {
      if (ParameterField.isNotNull(artifactConfig.getArtifactPath())
          && tagHasInputValidator(artifactConfig.getArtifactPath().getInputSetValidator(), artifactPath)) {
        artifactPathRegex = artifactConfig.getArtifactPath().getInputSetValidator().getParameters();
      } else {
        artifactPathRegex = getTagRegex(artifactPath);
      }
      artifactPath = "";
    }
    return ArtifactDelegateRequestUtils.getGoogleCloudStorageArtifactDelegateRequest(bucket, project, artifactPath,
        artifactPathRegex, gcpConnectorDTO, connectorRef, encryptedDataDetails,
        ArtifactSourceType.GOOGLE_CLOUD_STORAGE_ARTIFACT);
  }
}
