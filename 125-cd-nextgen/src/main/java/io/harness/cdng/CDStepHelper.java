/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cdng;

import static io.harness.beans.FeatureName.OPTIMIZED_GIT_FETCH_FILES;
import static io.harness.common.ParameterFieldHelper.getBooleanParameterFieldValue;
import static io.harness.common.ParameterFieldHelper.getParameterFieldValue;
import static io.harness.connector.ConnectorModule.DEFAULT_CONNECTOR_SERVICE;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.data.structure.HarnessStringUtils.emptyIfNull;
import static io.harness.data.structure.ListUtils.trimStrings;
import static io.harness.eraro.ErrorCode.GENERAL_ERROR;
import static io.harness.exception.WingsException.USER;
import static io.harness.logging.CommandExecutionStatus.FAILURE;
import static io.harness.logging.UnitStatus.RUNNING;
import static io.harness.ng.core.infrastructure.InfrastructureKind.KUBERNETES_AZURE;
import static io.harness.ng.core.infrastructure.InfrastructureKind.KUBERNETES_DIRECT;
import static io.harness.ng.core.infrastructure.InfrastructureKind.KUBERNETES_GCP;
import static io.harness.validation.Validator.notEmptyCheck;

import static software.wings.beans.appmanifest.ManifestFile.VALUES_YAML_KEY;

import static java.lang.String.format;
import static java.util.Collections.emptyList;
import static java.util.Objects.isNull;
import static org.apache.commons.lang3.StringUtils.trim;
import static org.apache.commons.lang3.StringUtils.trimToEmpty;

import io.harness.beans.DecryptableEntity;
import io.harness.beans.FeatureName;
import io.harness.cdng.artifact.outcome.ArtifactOutcome;
import io.harness.cdng.artifact.outcome.ArtifactsOutcome;
import io.harness.cdng.configfile.steps.ConfigFilesOutcome;
import io.harness.cdng.featureFlag.CDFeatureFlagHelper;
import io.harness.cdng.infra.beans.InfrastructureOutcome;
import io.harness.cdng.infra.beans.K8sAzureInfrastructureOutcome;
import io.harness.cdng.infra.beans.K8sDirectInfrastructureOutcome;
import io.harness.cdng.infra.beans.K8sGcpInfrastructureOutcome;
import io.harness.cdng.k8s.K8sApplyStepParameters;
import io.harness.cdng.k8s.K8sEntityHelper;
import io.harness.cdng.k8s.beans.GitFetchResponsePassThroughData;
import io.harness.cdng.k8s.beans.HelmValuesFetchResponsePassThroughData;
import io.harness.cdng.k8s.beans.StepExceptionPassThroughData;
import io.harness.cdng.manifest.ManifestStoreType;
import io.harness.cdng.manifest.ManifestType;
import io.harness.cdng.manifest.mappers.ManifestOutcomeMapper;
import io.harness.cdng.manifest.mappers.ManifestOutcomeValidator;
import io.harness.cdng.manifest.steps.ManifestsOutcome;
import io.harness.cdng.manifest.yaml.GcsStoreConfig;
import io.harness.cdng.manifest.yaml.GitStoreConfig;
import io.harness.cdng.manifest.yaml.HelmChartManifestOutcome;
import io.harness.cdng.manifest.yaml.HttpStoreConfig;
import io.harness.cdng.manifest.yaml.InheritFromManifestStoreConfig;
import io.harness.cdng.manifest.yaml.InlineStoreConfig;
import io.harness.cdng.manifest.yaml.K8sManifestOutcome;
import io.harness.cdng.manifest.yaml.KustomizeManifestOutcome;
import io.harness.cdng.manifest.yaml.KustomizePatchesManifestOutcome;
import io.harness.cdng.manifest.yaml.ManifestAttributes;
import io.harness.cdng.manifest.yaml.ManifestOutcome;
import io.harness.cdng.manifest.yaml.OciHelmChartConfig;
import io.harness.cdng.manifest.yaml.OpenshiftManifestOutcome;
import io.harness.cdng.manifest.yaml.OpenshiftParamManifestOutcome;
import io.harness.cdng.manifest.yaml.S3StoreConfig;
import io.harness.cdng.manifest.yaml.ValuesManifestOutcome;
import io.harness.cdng.manifest.yaml.storeConfig.StoreConfig;
import io.harness.cdng.ssh.SshEntityHelper;
import io.harness.cdng.stepsdependency.constants.OutcomeExpressionConstants;
import io.harness.common.NGTimeConversionHelper;
import io.harness.common.ParameterFieldHelper;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.connector.helper.GitApiAccessDecryptionHelper;
import io.harness.connector.services.ConnectorService;
import io.harness.connector.validator.scmValidators.GitConfigAuthenticationInfoHelper;
import io.harness.delegate.beans.connector.artifactoryconnector.ArtifactoryConnectorDTO;
import io.harness.delegate.beans.connector.awsconnector.AwsConnectorDTO;
import io.harness.delegate.beans.connector.gcpconnector.GcpConnectorDTO;
import io.harness.delegate.beans.connector.helm.HttpHelmConnectorDTO;
import io.harness.delegate.beans.connector.helm.OciHelmConnectorDTO;
import io.harness.delegate.beans.connector.scm.GitConnectionType;
import io.harness.delegate.beans.connector.scm.ScmConnector;
import io.harness.delegate.beans.connector.scm.adapter.ScmConnectorMapper;
import io.harness.delegate.beans.connector.scm.bitbucket.BitbucketConnectorDTO;
import io.harness.delegate.beans.connector.scm.genericgitconnector.GitConfigDTO;
import io.harness.delegate.beans.connector.scm.github.GithubApiAccessDTO;
import io.harness.delegate.beans.connector.scm.github.GithubApiAccessType;
import io.harness.delegate.beans.connector.scm.github.GithubConnectorDTO;
import io.harness.delegate.beans.connector.scm.github.GithubHttpAuthenticationType;
import io.harness.delegate.beans.connector.scm.github.GithubHttpCredentialsDTO;
import io.harness.delegate.beans.connector.scm.github.GithubTokenSpecDTO;
import io.harness.delegate.beans.connector.scm.github.GithubUsernameTokenDTO;
import io.harness.delegate.beans.connector.scm.gitlab.GitlabApiAccessDTO;
import io.harness.delegate.beans.connector.scm.gitlab.GitlabApiAccessType;
import io.harness.delegate.beans.connector.scm.gitlab.GitlabConnectorDTO;
import io.harness.delegate.beans.connector.scm.gitlab.GitlabHttpAuthenticationType;
import io.harness.delegate.beans.connector.scm.gitlab.GitlabHttpCredentialsDTO;
import io.harness.delegate.beans.connector.scm.gitlab.GitlabTokenSpecDTO;
import io.harness.delegate.beans.connector.scm.gitlab.GitlabUsernameTokenDTO;
import io.harness.delegate.beans.logstreaming.UnitProgressData;
import io.harness.delegate.beans.storeconfig.FetchType;
import io.harness.delegate.beans.storeconfig.GcsHelmStoreDelegateConfig;
import io.harness.delegate.beans.storeconfig.GitStoreDelegateConfig;
import io.harness.delegate.beans.storeconfig.HttpHelmStoreDelegateConfig;
import io.harness.delegate.beans.storeconfig.OciHelmStoreDelegateConfig;
import io.harness.delegate.beans.storeconfig.S3HelmStoreDelegateConfig;
import io.harness.delegate.beans.storeconfig.StoreDelegateConfig;
import io.harness.delegate.task.git.GitFetchFilesConfig;
import io.harness.delegate.task.helm.HelmFetchFileConfig;
import io.harness.delegate.task.helm.HelmFetchFileResult;
import io.harness.delegate.task.k8s.K8sInfraDelegateConfig;
import io.harness.delegate.task.ssh.SshInfraDelegateConfig;
import io.harness.encryption.SecretRefData;
import io.harness.eraro.Level;
import io.harness.eventsframework.schemas.entity.EntityDetailProtoDTO;
import io.harness.exception.InvalidArgumentsException;
import io.harness.exception.InvalidRequestException;
import io.harness.executions.steps.StepConstants;
import io.harness.expression.EngineExpressionEvaluator;
import io.harness.git.model.FetchFilesResult;
import io.harness.git.model.GitFile;
import io.harness.k8s.model.HelmVersion;
import io.harness.logging.LogCallback;
import io.harness.logging.LogLevel;
import io.harness.logging.UnitProgress;
import io.harness.logging.UnitStatus;
import io.harness.logstreaming.LogStreamingStepClientFactory;
import io.harness.logstreaming.NGLogCallback;
import io.harness.ng.core.NGAccess;
import io.harness.ng.core.dto.secrets.SSHKeySpecDTO;
import io.harness.plancreator.steps.common.StepElementParameters;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.execution.failure.FailureData;
import io.harness.pms.contracts.execution.failure.FailureInfo;
import io.harness.pms.contracts.execution.failure.FailureType;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.expression.EngineExpressionService;
import io.harness.pms.rbac.PipelineRbacHelper;
import io.harness.pms.sdk.core.data.OptionalOutcome;
import io.harness.pms.sdk.core.resolver.RefObjectUtils;
import io.harness.pms.sdk.core.resolver.outcome.OutcomeService;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.pms.yaml.ParameterField;
import io.harness.pms.yaml.validation.ExpressionUtils;
import io.harness.secretmanagerclient.services.api.SecretManagerClientService;
import io.harness.security.encryption.EncryptedDataDetail;
import io.harness.steps.EntityReferenceExtractorUtils;
import io.harness.validation.Validator;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import javax.annotation.Nonnull;
import org.apache.commons.lang3.tuple.Pair;
import org.hibernate.validator.constraints.NotEmpty;

public class CDStepHelper {
  public static final String MISSING_INFRASTRUCTURE_ERROR = "Infrastructure section is missing or is not configured";
  @Inject private GitConfigAuthenticationInfoHelper gitConfigAuthenticationInfoHelper;
  @Named("PRIVILEGED") @Inject private SecretManagerClientService secretManagerClientService;
  @Inject protected CDFeatureFlagHelper cdFeatureFlagHelper;
  @Inject private EngineExpressionService engineExpressionService;
  @Named(DEFAULT_CONNECTOR_SERVICE) @Inject private ConnectorService connectorService;
  @Inject private K8sEntityHelper k8sEntityHelper;
  @Inject private SshEntityHelper sshEntityHelper;
  @Inject private LogStreamingStepClientFactory logStreamingStepClientFactory;
  @Inject private EntityReferenceExtractorUtils entityReferenceExtractorUtils;
  @Inject private PipelineRbacHelper pipelineRbacHelper;
  @Inject protected OutcomeService outcomeService;

  public static final String RELEASE_NAME_VALIDATION_REGEX =
      "[a-z0-9]([-a-z0-9]*[a-z0-9])?(\\.[a-z0-9]([-a-z0-9]*[a-z0-9])?)*";

  public static final Pattern releaseNamePattern = Pattern.compile(RELEASE_NAME_VALIDATION_REGEX);

  public HelmVersion getHelmVersionBasedOnFF(HelmVersion helmVersion, String accountId) {
    if (helmVersion == HelmVersion.V2) {
      return helmVersion;
    }
    return cdFeatureFlagHelper.isEnabled(accountId, FeatureName.HELM_VERSION_3_8_0) == true ? HelmVersion.V380
                                                                                            : HelmVersion.V3;
  }

  // Optimised (SCM based) file fetch methods:
  public boolean isGitlabTokenAuth(ScmConnector scmConnector) {
    return scmConnector instanceof GitlabConnectorDTO
        && (((GitlabConnectorDTO) scmConnector).getApiAccess() != null
            || isGitlabUsernameTokenAuth((GitlabConnectorDTO) scmConnector));
  }

  public boolean isGitlabUsernameTokenAuth(GitlabConnectorDTO gitlabConnectorDTO) {
    return gitlabConnectorDTO.getAuthentication().getCredentials() instanceof GitlabHttpCredentialsDTO
        && ((GitlabHttpCredentialsDTO) gitlabConnectorDTO.getAuthentication().getCredentials())
               .getType()
               .equals(GitlabHttpAuthenticationType.USERNAME_AND_TOKEN);
  }

  public boolean isGithubUsernameTokenAuth(GithubConnectorDTO githubConnectorDTO) {
    return githubConnectorDTO.getAuthentication().getCredentials() instanceof GithubHttpCredentialsDTO
        && ((GithubHttpCredentialsDTO) githubConnectorDTO.getAuthentication().getCredentials())
               .getType()
               .equals(GithubHttpAuthenticationType.USERNAME_AND_TOKEN);
  }

  public boolean isGithubTokenAuth(ScmConnector scmConnector) {
    return scmConnector instanceof GithubConnectorDTO
        && (((GithubConnectorDTO) scmConnector).getApiAccess() != null
            || isGithubUsernameTokenAuth((GithubConnectorDTO) scmConnector));
  }

  public SSHKeySpecDTO getSshKeySpecDTO(GitConfigDTO gitConfigDTO, Ambiance ambiance) {
    return gitConfigAuthenticationInfoHelper.getSSHKey(gitConfigDTO, AmbianceUtils.getAccountId(ambiance),
        AmbianceUtils.getOrgIdentifier(ambiance), AmbianceUtils.getProjectIdentifier(ambiance));
  }

  public boolean isOptimizedFilesFetch(@Nonnull ConnectorInfoDTO connectorDTO, String accountId) {
    return cdFeatureFlagHelper.isEnabled(accountId, OPTIMIZED_GIT_FETCH_FILES)
        && (isGithubTokenAuth((ScmConnector) connectorDTO.getConnectorConfig())
            || isGitlabTokenAuth((ScmConnector) connectorDTO.getConnectorConfig()));
  }

  public void addApiAuthIfRequired(ScmConnector scmConnector) {
    if (scmConnector instanceof GithubConnectorDTO && ((GithubConnectorDTO) scmConnector).getApiAccess() == null
        && isGithubUsernameTokenAuth((GithubConnectorDTO) scmConnector)) {
      GithubConnectorDTO githubConnectorDTO = (GithubConnectorDTO) scmConnector;
      SecretRefData tokenRef =
          ((GithubUsernameTokenDTO) ((GithubHttpCredentialsDTO) githubConnectorDTO.getAuthentication().getCredentials())
                  .getHttpCredentialsSpec())
              .getTokenRef();
      GithubApiAccessDTO apiAccessDTO = GithubApiAccessDTO.builder()
                                            .type(GithubApiAccessType.TOKEN)
                                            .spec(GithubTokenSpecDTO.builder().tokenRef(tokenRef).build())
                                            .build();
      githubConnectorDTO.setApiAccess(apiAccessDTO);
    } else if (scmConnector instanceof GitlabConnectorDTO && ((GitlabConnectorDTO) scmConnector).getApiAccess() == null
        && isGitlabUsernameTokenAuth((GitlabConnectorDTO) scmConnector)) {
      GitlabConnectorDTO gitlabConnectorDTO = (GitlabConnectorDTO) scmConnector;
      SecretRefData tokenRef =
          ((GitlabUsernameTokenDTO) ((GitlabHttpCredentialsDTO) gitlabConnectorDTO.getAuthentication().getCredentials())
                  .getHttpCredentialsSpec())
              .getTokenRef();
      GitlabApiAccessDTO apiAccessDTO = GitlabApiAccessDTO.builder()
                                            .type(GitlabApiAccessType.TOKEN)
                                            .spec(GitlabTokenSpecDTO.builder().tokenRef(tokenRef).build())
                                            .build();
      gitlabConnectorDTO.setApiAccess(apiAccessDTO);
    }
  }

  public String getGitRepoUrl(ScmConnector scmConnector, String repoName) {
    repoName = trimToEmpty(repoName);
    notEmptyCheck("Repo name cannot be empty for Account level git connector", repoName);
    String purgedRepoUrl = scmConnector.getUrl().replaceAll("/*$", "");
    String purgedRepoName = repoName.replaceAll("^/*", "");
    return purgedRepoUrl + "/" + purgedRepoName;
  }

  public void convertToRepoGitConfig(GitStoreConfig gitstoreConfig, ScmConnector scmConnector) {
    String repoName = gitstoreConfig.getRepoName() != null ? gitstoreConfig.getRepoName().getValue() : null;
    if (scmConnector instanceof GitConfigDTO) {
      GitConfigDTO gitConfigDTO = (GitConfigDTO) scmConnector;
      if (gitConfigDTO.getGitConnectionType() == GitConnectionType.ACCOUNT) {
        String repoUrl = getGitRepoUrl(gitConfigDTO, repoName);
        gitConfigDTO.setUrl(repoUrl);
        gitConfigDTO.setGitConnectionType(GitConnectionType.REPO);
      }
    } else if (scmConnector instanceof GithubConnectorDTO) {
      GithubConnectorDTO githubConnectorDTO = (GithubConnectorDTO) scmConnector;
      if (githubConnectorDTO.getConnectionType() == GitConnectionType.ACCOUNT) {
        String repoUrl = getGitRepoUrl(githubConnectorDTO, repoName);
        githubConnectorDTO.setUrl(repoUrl);
        githubConnectorDTO.setConnectionType(GitConnectionType.REPO);
      }
    } else if (scmConnector instanceof GitlabConnectorDTO) {
      GitlabConnectorDTO gitlabConnectorDTO = (GitlabConnectorDTO) scmConnector;
      if (gitlabConnectorDTO.getConnectionType() == GitConnectionType.ACCOUNT) {
        String repoUrl = getGitRepoUrl(gitlabConnectorDTO, repoName);
        gitlabConnectorDTO.setUrl(repoUrl);
        gitlabConnectorDTO.setConnectionType(GitConnectionType.REPO);
      }
    }
  }

  public GitStoreDelegateConfig getGitStoreDelegateConfig(@Nonnull GitStoreConfig gitstoreConfig,
      @Nonnull ConnectorInfoDTO connectorDTO, ManifestOutcome manifestOutcome, List<String> paths, Ambiance ambiance) {
    NGAccess basicNGAccessObject = AmbianceUtils.getNgAccess(ambiance);
    ScmConnector scmConnector;
    List<EncryptedDataDetail> apiAuthEncryptedDataDetails = null;
    GitConfigDTO gitConfigDTO = ScmConnectorMapper.toGitConfigDTO((ScmConnector) connectorDTO.getConnectorConfig());
    SSHKeySpecDTO sshKeySpecDTO = getSshKeySpecDTO(gitConfigDTO, ambiance);
    List<EncryptedDataDetail> encryptedDataDetails =
        gitConfigAuthenticationInfoHelper.getEncryptedDataDetails(gitConfigDTO, sshKeySpecDTO, basicNGAccessObject);

    scmConnector = gitConfigDTO;

    boolean optimizedFilesFetch = isOptimizedFilesFetch(connectorDTO, AmbianceUtils.getAccountId(ambiance))
        && !ManifestType.Kustomize.equals(manifestOutcome.getType());

    if (optimizedFilesFetch) {
      scmConnector = (ScmConnector) connectorDTO.getConnectorConfig();
      addApiAuthIfRequired(scmConnector);
      final DecryptableEntity apiAccessDecryptableEntity =
          GitApiAccessDecryptionHelper.getAPIAccessDecryptableEntity(scmConnector);
      apiAuthEncryptedDataDetails =
          secretManagerClientService.getEncryptionDetails(basicNGAccessObject, apiAccessDecryptableEntity);
    }

    convertToRepoGitConfig(gitstoreConfig, scmConnector);
    return GitStoreDelegateConfig.builder()
        .gitConfigDTO(scmConnector)
        .sshKeySpecDTO(sshKeySpecDTO)
        .encryptedDataDetails(encryptedDataDetails)
        .apiAuthEncryptedDataDetails(apiAuthEncryptedDataDetails)
        .fetchType(gitstoreConfig.getGitFetchType())
        .branch(trim(getParameterFieldValue(gitstoreConfig.getBranch())))
        .commitId(trim(getParameterFieldValue(gitstoreConfig.getCommitId())))
        .paths(trimStrings(paths))
        .connectorName(connectorDTO.getName())
        .manifestType(manifestOutcome.getType())
        .manifestId(manifestOutcome.getIdentifier())
        .optimizedFilesFetch(optimizedFilesFetch)
        .build();
  }

  public GitFetchFilesConfig getGitFetchFilesConfig(
      Ambiance ambiance, StoreConfig store, String validationMessage, ManifestOutcome manifestOutcome) {
    GitStoreConfig gitStoreConfig = (GitStoreConfig) store;
    String connectorId = gitStoreConfig.getConnectorRef().getValue();
    ConnectorInfoDTO connectorDTO = getConnector(connectorId, ambiance);
    validateManifest(store.getKind(), connectorDTO, validationMessage);

    List<String> gitFilePaths = getPathsBasedOnManifest(gitStoreConfig, manifestOutcome.getType());
    GitStoreDelegateConfig gitStoreDelegateConfig =
        getGitStoreDelegateConfig(gitStoreConfig, connectorDTO, manifestOutcome, gitFilePaths, ambiance);

    return getGitFetchFilesConfigFromBuilder(
        manifestOutcome.getIdentifier(), manifestOutcome.getType(), false, gitStoreDelegateConfig);
  }

  public GitFetchFilesConfig getGitFetchFilesConfigFromBuilder(String identifier, String manifestType,
      boolean succeedIfFileNotFound, GitStoreDelegateConfig gitStoreDelegateConfig) {
    return GitFetchFilesConfig.builder()
        .identifier(identifier)
        .manifestType(manifestType)
        .succeedIfFileNotFound(succeedIfFileNotFound)
        .gitStoreDelegateConfig(gitStoreDelegateConfig)
        .build();
  }

  public GitFetchFilesConfig getPathsFromInheritFromManifestStoreConfig(
      Ambiance ambiance, String validationMessage, ManifestOutcome manifestOutcome, GitStoreConfig gitStoreConfig) {
    InheritFromManifestStoreConfig inheritFromManifestStoreConfig =
        (InheritFromManifestStoreConfig) manifestOutcome.getStore();
    String connectorId = gitStoreConfig.getConnectorRef().getValue();
    ConnectorInfoDTO connectorDTO = getConnector(connectorId, ambiance);
    validateManifest(gitStoreConfig.getKind(), connectorDTO, validationMessage);
    List<String> gitFilePaths = new ArrayList<>(getParameterFieldValue(inheritFromManifestStoreConfig.getPaths()));

    GitStoreDelegateConfig gitStoreDelegateConfig =
        getGitStoreDelegateConfig(gitStoreConfig, connectorDTO, manifestOutcome, gitFilePaths, ambiance);
    return GitFetchFilesConfig.builder()
        .identifier(manifestOutcome.getIdentifier())
        .manifestType(manifestOutcome.getType())
        .gitStoreDelegateConfig(gitStoreDelegateConfig)
        .build();
  }

  public static HelmFetchFileConfig getInheritFromManifestFetchFileConfig(
      String identifier, String manifestType, InheritFromManifestStoreConfig inheritFromManifestStoreConfig) {
    List<String> filePaths = new ArrayList<>(getParameterFieldValue(inheritFromManifestStoreConfig.getPaths()));

    return HelmFetchFileConfig.builder().identifier(identifier).manifestType(manifestType).filePaths(filePaths).build();
  }

  public List<EncryptedDataDetail> getEncryptedDataDetails(
      @Nonnull GitConfigDTO gitConfigDTO, @Nonnull Ambiance ambiance) {
    return secretManagerClientService.getEncryptionDetails(
        AmbianceUtils.getNgAccess(ambiance), gitConfigDTO.getGitAuth());
  }

  // ParamterFieldBoolean methods:
  public static boolean getParameterFieldBooleanValue(
      ParameterField<?> fieldValue, String fieldName, StepElementParameters stepElement) {
    return getParameterFieldBooleanValue(fieldValue, fieldName,
        String.format("%s step with identifier: %s", stepElement.getType(), stepElement.getIdentifier()));
  }

  public static boolean getParameterFieldBooleanValue(
      ParameterField<?> fieldValue, String fieldName, ManifestOutcome manifestOutcome) {
    return getParameterFieldBooleanValue(fieldValue, fieldName,
        String.format("%s manifest with identifier: %s", manifestOutcome.getType(), manifestOutcome.getIdentifier()));
  }

  public static boolean getParameterFieldBooleanValue(
      ParameterField<?> fieldValue, String fieldName, String description) {
    try {
      return getBooleanParameterFieldValue(fieldValue);
    } catch (Exception e) {
      String message = String.format("%s for field %s in %s", e.getMessage(), fieldName, description);
      throw new InvalidArgumentsException(message);
    }
  }

  // releaseName helper methods:
  public String getReleaseName(Ambiance ambiance, InfrastructureOutcome infrastructure) {
    String releaseName;
    switch (infrastructure.getKind()) {
      case KUBERNETES_DIRECT:
        K8sDirectInfrastructureOutcome k8SDirectInfrastructure = (K8sDirectInfrastructureOutcome) infrastructure;
        releaseName = k8SDirectInfrastructure.getReleaseName();
        break;
      case KUBERNETES_GCP:
        K8sGcpInfrastructureOutcome k8sGcpInfrastructure = (K8sGcpInfrastructureOutcome) infrastructure;
        releaseName = k8sGcpInfrastructure.getReleaseName();
        break;
      case KUBERNETES_AZURE:
        K8sAzureInfrastructureOutcome k8sAzureInfrastructureOutcome = (K8sAzureInfrastructureOutcome) infrastructure;
        releaseName = k8sAzureInfrastructureOutcome.getReleaseName();
        break;
      default:
        throw new UnsupportedOperationException(format("Unknown infrastructure type: [%s]", infrastructure.getKind()));
    }
    if (EngineExpressionEvaluator.hasExpressions(releaseName)) {
      releaseName = engineExpressionService.renderExpression(ambiance, releaseName);
    }

    validateReleaseName(releaseName);
    return releaseName;
  }

  private static void validateReleaseName(String name) {
    if (isEmpty(name)) {
      throw new InvalidArgumentsException(Pair.of("releaseName", "Cannot be empty"));
    }

    if (!ExpressionUtils.matchesPattern(releaseNamePattern, name)) {
      throw new InvalidRequestException(format(
          "Invalid Release name format: %s. Release name must consist of lower case alphanumeric characters, '-' or '.'"
              + ", and must start and end with an alphanumeric character (e.g. 'example.com')",
          name));
    }
  }

  // TimeOut methods:
  public static int getTimeoutInMin(StepElementParameters stepParameters) {
    String timeout = getTimeoutValue(stepParameters);
    return NGTimeConversionHelper.convertTimeStringToMinutes(timeout);
  }

  public static long getTimeoutInMillis(StepElementParameters stepParameters) {
    String timeout = getTimeoutValue(stepParameters);
    return NGTimeConversionHelper.convertTimeStringToMilliseconds(timeout);
  }

  public static String getTimeoutValue(StepElementParameters stepParameters) {
    return stepParameters.getTimeout() == null || isEmpty(stepParameters.getTimeout().getValue())
        ? StepConstants.defaultTimeout
        : stepParameters.getTimeout().getValue();
  }

  // Aggregated Manifest methods
  public static List<ValuesManifestOutcome> getAggregatedValuesManifests(
      @NotEmpty List<ManifestOutcome> manifestOutcomeList) {
    List<ValuesManifestOutcome> aggregateValuesManifests = new ArrayList<>();

    List<ValuesManifestOutcome> serviceValuesManifests =
        manifestOutcomeList.stream()
            .filter(manifestOutcome -> ManifestType.VALUES.equals(manifestOutcome.getType()))
            .map(manifestOutcome -> (ValuesManifestOutcome) manifestOutcome)
            .collect(Collectors.toList());

    if (isNotEmpty(serviceValuesManifests)) {
      aggregateValuesManifests.addAll(serviceValuesManifests);
    }
    return aggregateValuesManifests;
  }

  public static List<HelmFetchFileConfig> mapValuesManifestsToHelmFetchFileConfig(
      List<ValuesManifestOutcome> aggregatedValuesManifests) {
    if (isEmpty(aggregatedValuesManifests)) {
      return emptyList();
    }
    return aggregatedValuesManifests.stream()
        .filter(valuesManifestOutcome
            -> ManifestStoreType.InheritFromManifest.equals(valuesManifestOutcome.getStore().getKind()))
        .map(valuesManifestOutcome
            -> getInheritFromManifestFetchFileConfig(valuesManifestOutcome.getIdentifier(),
                valuesManifestOutcome.getType(), (InheritFromManifestStoreConfig) valuesManifestOutcome.getStore()))
        .collect(Collectors.toList());
  }

  // miscellaneous common methods
  public ConnectorInfoDTO getConnector(String connectorId, Ambiance ambiance) {
    NGAccess ngAccess = AmbianceUtils.getNgAccess(ambiance);
    return k8sEntityHelper.getConnectorInfoDTO(connectorId, ngAccess);
  }

  public void validateManifest(String manifestStoreType, ConnectorInfoDTO connectorInfoDTO, String message) {
    switch (manifestStoreType) {
      case ManifestStoreType.GIT:
        if (!(connectorInfoDTO.getConnectorConfig() instanceof GitConfigDTO)) {
          throw new InvalidRequestException(format("Invalid connector selected in %s. Select Git connector", message));
        }
        break;
      case ManifestStoreType.GITHUB:
        if (!(connectorInfoDTO.getConnectorConfig() instanceof GithubConnectorDTO)) {
          throw new InvalidRequestException(
              format("Invalid connector selected in %s. Select Github connector", message));
        }
        break;
      case ManifestStoreType.GITLAB:
        if (!(connectorInfoDTO.getConnectorConfig() instanceof GitlabConnectorDTO)) {
          throw new InvalidRequestException(
              format("Invalid connector selected in %s. Select GitLab connector", message));
        }
        break;
      case ManifestStoreType.BITBUCKET:
        if (!(connectorInfoDTO.getConnectorConfig() instanceof BitbucketConnectorDTO)) {
          throw new InvalidRequestException(
              format("Invalid connector selected in %s. Select Bitbucket connector", message));
        }
        break;
      case ManifestStoreType.HTTP:
        if (!(connectorInfoDTO.getConnectorConfig() instanceof HttpHelmConnectorDTO)) {
          throw new InvalidRequestException(
              format("Invalid connector selected in %s. Select Http Helm connector", message));
        }
        break;
      case ManifestStoreType.OCI:
        if (!(connectorInfoDTO.getConnectorConfig() instanceof OciHelmConnectorDTO)) {
          throw new InvalidRequestException(
              format("Invalid connector selected in %s. Select Oci Helm connector", message));
        }
        break;

      case ManifestStoreType.S3:
        if (!((connectorInfoDTO.getConnectorConfig()) instanceof AwsConnectorDTO)) {
          throw new InvalidRequestException(
              format("Invalid connector selected in %s. Select Amazon Web Services connector", message));
        }
        break;
      case ManifestStoreType.ARTIFACTORY:
        if (!((connectorInfoDTO.getConnectorConfig()) instanceof ArtifactoryConnectorDTO)) {
          throw new InvalidRequestException(
              format("Invalid connector selected in %s. Select Artifactory connector", message));
        }
        break;

      case ManifestStoreType.GCS:
        if (!(connectorInfoDTO.getConnectorConfig() instanceof GcpConnectorDTO)) {
          throw new InvalidRequestException(
              format("Invalid connector selected in %s. Select Google cloud connector", message));
        }
        break;

      case ManifestStoreType.INLINE:
        break;
      default:
        throw new UnsupportedOperationException(format("Unknown manifest store type: [%s]", manifestStoreType));
    }
  }

  public StoreDelegateConfig getStoreDelegateConfig(
      StoreConfig storeConfig, Ambiance ambiance, ManifestOutcome manifestOutcome, String validationErrorMessage) {
    if (ManifestStoreType.isInGitSubset(storeConfig.getKind())) {
      GitStoreConfig gitStoreConfig = (GitStoreConfig) storeConfig;
      ConnectorInfoDTO connectorDTO = getConnector(getParameterFieldValue(gitStoreConfig.getConnectorRef()), ambiance);
      validateManifest(storeConfig.getKind(), connectorDTO, validationErrorMessage);

      List<String> gitFilePaths = getPathsBasedOnManifest(gitStoreConfig, manifestOutcome.getType());
      return getGitStoreDelegateConfig(gitStoreConfig, connectorDTO, manifestOutcome, gitFilePaths, ambiance);
    }

    if (ManifestStoreType.HTTP.equals(storeConfig.getKind())) {
      HttpStoreConfig httpStoreConfig = (HttpStoreConfig) storeConfig;
      ConnectorInfoDTO helmConnectorDTO =
          getConnector(getParameterFieldValue(httpStoreConfig.getConnectorRef()), ambiance);
      validateManifest(storeConfig.getKind(), helmConnectorDTO, validationErrorMessage);

      return HttpHelmStoreDelegateConfig.builder()
          .repoName(helmConnectorDTO.getIdentifier())
          .repoDisplayName(helmConnectorDTO.getName())
          .httpHelmConnector((HttpHelmConnectorDTO) helmConnectorDTO.getConnectorConfig())
          .encryptedDataDetails(
              k8sEntityHelper.getEncryptionDataDetails(helmConnectorDTO, AmbianceUtils.getNgAccess(ambiance)))
          .build();
    }

    if (ManifestStoreType.OCI.equals(storeConfig.getKind())) {
      if (!isHelmOciEnabled(AmbianceUtils.getAccountId(ambiance))) {
        throw new UnsupportedOperationException(format("Unsupported Store Config type: [%s]", storeConfig.getKind()));
      }
      OciHelmChartConfig ociStoreConfig = (OciHelmChartConfig) storeConfig;
      ConnectorInfoDTO helmConnectorDTO =
          getConnector(getParameterFieldValue(ociStoreConfig.getConnectorReference()), ambiance);
      validateManifest(storeConfig.getKind(), helmConnectorDTO, validationErrorMessage);

      return OciHelmStoreDelegateConfig.builder()
          .repoName(helmConnectorDTO.getIdentifier())
          .basePath(getParameterFieldValue(ociStoreConfig.getBasePath()))
          .repoDisplayName(helmConnectorDTO.getName())
          .ociHelmConnector((OciHelmConnectorDTO) helmConnectorDTO.getConnectorConfig())
          .encryptedDataDetails(
              k8sEntityHelper.getEncryptionDataDetails(helmConnectorDTO, AmbianceUtils.getNgAccess(ambiance)))
          .helmOciEnabled(isHelmOciEnabled(AmbianceUtils.getAccountId(ambiance)))
          .build();
    }

    if (ManifestStoreType.S3.equals(storeConfig.getKind())) {
      S3StoreConfig s3StoreConfig = (S3StoreConfig) storeConfig;
      ConnectorInfoDTO awsConnectorDTO =
          getConnector(getParameterFieldValue(s3StoreConfig.getConnectorRef()), ambiance);
      validateManifest(storeConfig.getKind(), awsConnectorDTO, validationErrorMessage);

      return S3HelmStoreDelegateConfig.builder()
          .repoName(awsConnectorDTO.getIdentifier())
          .repoDisplayName(awsConnectorDTO.getName())
          .bucketName(getParameterFieldValue(s3StoreConfig.getBucketName()))
          .region(getParameterFieldValue(s3StoreConfig.getRegion()))
          .folderPath(getParameterFieldValue(s3StoreConfig.getFolderPath()))
          .awsConnector((AwsConnectorDTO) awsConnectorDTO.getConnectorConfig())
          .encryptedDataDetails(
              k8sEntityHelper.getEncryptionDataDetails(awsConnectorDTO, AmbianceUtils.getNgAccess(ambiance)))
          .useLatestChartMuseumVersion(cdFeatureFlagHelper.isEnabled(
              AmbianceUtils.getAccountId(ambiance), FeatureName.USE_LATEST_CHARTMUSEUM_VERSION))
          .build();
    }

    if (ManifestStoreType.GCS.equals(storeConfig.getKind())) {
      GcsStoreConfig gcsStoreConfig = (GcsStoreConfig) storeConfig;
      ConnectorInfoDTO gcpConnectorDTO =
          getConnector(getParameterFieldValue(gcsStoreConfig.getConnectorRef()), ambiance);
      validateManifest(storeConfig.getKind(), gcpConnectorDTO, validationErrorMessage);

      return GcsHelmStoreDelegateConfig.builder()
          .repoName(gcpConnectorDTO.getIdentifier())
          .repoDisplayName(gcpConnectorDTO.getName())
          .bucketName(getParameterFieldValue(gcsStoreConfig.getBucketName()))
          .folderPath(getParameterFieldValue(gcsStoreConfig.getFolderPath()))
          .gcpConnector((GcpConnectorDTO) gcpConnectorDTO.getConnectorConfig())
          .encryptedDataDetails(
              k8sEntityHelper.getEncryptionDataDetails(gcpConnectorDTO, AmbianceUtils.getNgAccess(ambiance)))
          .useLatestChartMuseumVersion(cdFeatureFlagHelper.isEnabled(
              AmbianceUtils.getAccountId(ambiance), FeatureName.USE_LATEST_CHARTMUSEUM_VERSION))
          .build();
    }

    throw new UnsupportedOperationException(format("Unsupported Store Config type: [%s]", storeConfig.getKind()));
  }

  public List<String> getPathsBasedOnManifest(GitStoreConfig gitstoreConfig, String manifestType) {
    List<String> paths = new ArrayList<>();
    switch (manifestType) {
      case ManifestType.HelmChart:
        paths.add(getParameterFieldValue(gitstoreConfig.getFolderPath()));
        break;
      case ManifestType.Kustomize:
        // Set as repository root
        paths.add("/");
        break;

      default:
        paths.addAll(getParameterFieldValue(gitstoreConfig.getPaths()));
    }

    return paths;
  }

  public K8sInfraDelegateConfig getK8sInfraDelegateConfig(InfrastructureOutcome infrastructure, Ambiance ambiance) {
    NGAccess ngAccess = AmbianceUtils.getNgAccess(ambiance);
    return k8sEntityHelper.getK8sInfraDelegateConfig(infrastructure, ngAccess);
  }

  public SshInfraDelegateConfig getSshInfraDelegateConfig(InfrastructureOutcome infrastructure, Ambiance ambiance) {
    return sshEntityHelper.getSshInfraDelegateConfig(infrastructure, ambiance);
  }

  public boolean isUseLatestKustomizeVersion(String accountId) {
    return cdFeatureFlagHelper.isEnabled(accountId, FeatureName.NEW_KUSTOMIZE_BINARY);
  }

  public boolean isUseNewKubectlVersion(String accountId) {
    return cdFeatureFlagHelper.isEnabled(accountId, FeatureName.NEW_KUBECTL_VERSION);
  }

  public boolean isHelmOciEnabled(String accountId) {
    return cdFeatureFlagHelper.isEnabled(accountId, FeatureName.HELM_OCI_SUPPORT);
  }

  public boolean shouldCleanUpIncompleteCanaryDeployRelease(String accountId) {
    return cdFeatureFlagHelper.isEnabled(accountId, FeatureName.CLEANUP_INCOMPLETE_CANARY_DEPLOY_RELEASE);
  }
  public boolean isSkipAddingTrackSelectorToDeployment(String accountId) {
    return cdFeatureFlagHelper.isEnabled(accountId, FeatureName.SKIP_ADDING_TRACK_LABEL_SELECTOR_IN_ROLLING);
  }

  public List<String> getValuesFileContents(Ambiance ambiance, List<String> valuesFileContents) {
    return valuesFileContents.stream()
        .map(valuesFileContent -> engineExpressionService.renderExpression(ambiance, valuesFileContent, false))
        .collect(Collectors.toList());
  }

  public LogCallback getLogCallback(String commandUnitName, Ambiance ambiance, boolean shouldOpenStream) {
    return new NGLogCallback(logStreamingStepClientFactory, ambiance, commandUnitName, shouldOpenStream);
  }

  public UnitProgressData completeUnitProgressData(
      UnitProgressData currentProgressData, Ambiance ambiance, String exceptionMessage) {
    if (currentProgressData == null) {
      return UnitProgressData.builder().unitProgresses(new ArrayList<>()).build();
    }

    List<UnitProgress> finalUnitProgressList =
        currentProgressData.getUnitProgresses()
            .stream()
            .map(unitProgress -> {
              if (unitProgress.getStatus() == RUNNING) {
                LogCallback logCallback = getLogCallback(unitProgress.getUnitName(), ambiance, false);
                logCallback.saveExecutionLog(exceptionMessage, LogLevel.ERROR, FAILURE);
                return UnitProgress.newBuilder(unitProgress)
                    .setStatus(UnitStatus.FAILURE)
                    .setEndTime(System.currentTimeMillis())
                    .build();
              }

              return unitProgress;
            })
            .collect(Collectors.toList());

    return UnitProgressData.builder().unitProgresses(finalUnitProgressList).build();
  }

  public StepResponse handleGitTaskFailure(GitFetchResponsePassThroughData gitFetchResponse) {
    UnitProgressData unitProgressData = gitFetchResponse.getUnitProgressData();
    return StepResponse.builder()
        .unitProgressList(unitProgressData.getUnitProgresses())
        .status(Status.FAILED)
        .failureInfo(FailureInfo.newBuilder().setErrorMessage(gitFetchResponse.getErrorMsg()).build())
        .build();
  }

  public StepResponse handleHelmValuesFetchFailure(HelmValuesFetchResponsePassThroughData helmValuesFetchResponse) {
    UnitProgressData unitProgressData = helmValuesFetchResponse.getUnitProgressData();
    return StepResponse.builder()
        .unitProgressList(unitProgressData.getUnitProgresses())
        .status(Status.FAILED)
        .failureInfo(FailureInfo.newBuilder().setErrorMessage(helmValuesFetchResponse.getErrorMsg()).build())
        .build();
  }

  public StepResponse handleStepExceptionFailure(StepExceptionPassThroughData stepException) {
    FailureData failureData = FailureData.newBuilder()
                                  .addFailureTypes(FailureType.APPLICATION_FAILURE)
                                  .setLevel(Level.ERROR.name())
                                  .setCode(GENERAL_ERROR.name())
                                  .setMessage(emptyIfNull(stepException.getErrorMessage()))
                                  .build();
    return StepResponse.builder()
        .unitProgressList(stepException.getUnitProgressData().getUnitProgresses())
        .status(Status.FAILED)
        .failureInfo(FailureInfo.newBuilder()
                         .addAllFailureTypes(failureData.getFailureTypesList())
                         .setErrorMessage(failureData.getMessage())
                         .addFailureData(failureData)
                         .build())
        .build();
  }

  public void validateManifestsOutcome(Ambiance ambiance, ManifestsOutcome manifestsOutcome) {
    Set<EntityDetailProtoDTO> entityDetails = new HashSet<>();
    manifestsOutcome.values().forEach(value -> {
      entityDetails.addAll(entityReferenceExtractorUtils.extractReferredEntities(ambiance, value.getStore()));
      ManifestOutcomeValidator.validate(value, false);
    });

    pipelineRbacHelper.checkRuntimePermissions(ambiance, entityDetails);
  }

  public void validateGitStoreConfig(GitStoreConfig gitStoreConfig) {
    Validator.notNullCheck("Git Store Config is null", gitStoreConfig);
    FetchType gitFetchType = gitStoreConfig.getGitFetchType();
    switch (gitFetchType) {
      case BRANCH:
        Validator.notEmptyCheck("Branch is Empty in Git Store config",
            ParameterFieldHelper.getParameterFieldValue(gitStoreConfig.getBranch()));
        break;
      case COMMIT:
        Validator.notEmptyCheck("Commit Id is Empty in Git Store config",
            ParameterFieldHelper.getParameterFieldValue(gitStoreConfig.getCommitId()));
        break;
      default:
        throw new InvalidRequestException(format("Unrecognized git fetch type: [%s]", gitFetchType.name()));
    }
  }

  public static List<HelmFetchFileConfig> mapHelmChartManifestsToHelmFetchFileConfig(
      String identifier, List<String> valuesPaths, String manifestType) {
    List<HelmFetchFileConfig> helmFetchFileConfigList = new ArrayList<>();
    helmFetchFileConfigList.add(
        createHelmFetchFileConfig(identifier, manifestType, Arrays.asList(VALUES_YAML_KEY), true));
    if (isNotEmpty(valuesPaths)) {
      helmFetchFileConfigList.add(createHelmFetchFileConfig(identifier, manifestType, valuesPaths, false));
    }
    return helmFetchFileConfigList;
  }

  public static HelmFetchFileConfig createHelmFetchFileConfig(
      String identifier, String manifestType, List<String> valuesPaths, boolean succeedIfFileNotFound) {
    return HelmFetchFileConfig.builder()
        .identifier(identifier)
        .manifestType(manifestType)
        .filePaths(valuesPaths)
        .succeedIfFileNotFound(succeedIfFileNotFound)
        .build();
  }

  public List<String> getManifestFilesContents(Map<String, FetchFilesResult> gitFetchFilesResultMap,
      List<ManifestOutcome> valuesManifests, Map<String, HelmFetchFileResult> helmChartFetchFilesResultMap) {
    List<String> valuesFileContents = new ArrayList<>();

    for (ManifestOutcome valuesManifest : valuesManifests) {
      StoreConfig store = extractStoreConfigFromManifestOutcome(valuesManifest);
      String valuesIdentifier = valuesManifest.getIdentifier();
      if (ManifestStoreType.INLINE.equals(store.getKind())) {
        valuesFileContents.add(((InlineStoreConfig) store).extractContent());
      } else if (isNotEmpty(gitFetchFilesResultMap) && gitFetchFilesResultMap.containsKey(valuesIdentifier)) {
        FetchFilesResult gitFetchFilesResult = gitFetchFilesResultMap.get(valuesIdentifier);
        if (!isNull(gitFetchFilesResult)) {
          valuesFileContents.addAll(
              gitFetchFilesResult.getFiles().stream().map(GitFile::getFileContent).collect(Collectors.toList()));
        }
      } else if (isNotEmpty(helmChartFetchFilesResultMap)
          && helmChartFetchFilesResultMap.containsKey(valuesIdentifier)) {
        List<String> helmChartValuesFileContent =
            helmChartFetchFilesResultMap.get(valuesIdentifier).getValuesFileContents();
        if (isNotEmpty(helmChartValuesFileContent)) {
          valuesFileContents.addAll(helmChartValuesFileContent);
        }
      }
      // TODO: for local store, add files directly
    }
    return valuesFileContents;
  }

  public StoreConfig extractStoreConfigFromManifestOutcome(ManifestOutcome manifestOutcome) {
    switch (manifestOutcome.getType()) {
      case ManifestType.K8Manifest:
        K8sManifestOutcome k8sManifestOutcome = (K8sManifestOutcome) manifestOutcome;
        return k8sManifestOutcome.getStore();

      case ManifestType.HelmChart:
        HelmChartManifestOutcome helmChartManifestOutcome = (HelmChartManifestOutcome) manifestOutcome;
        return helmChartManifestOutcome.getStore();

      case ManifestType.VALUES:
        ValuesManifestOutcome valuesManifestOutcome = (ValuesManifestOutcome) manifestOutcome;
        return valuesManifestOutcome.getStore();

      case ManifestType.KustomizePatches:
        KustomizePatchesManifestOutcome kustomizePatchesManifestOutcome =
            (KustomizePatchesManifestOutcome) manifestOutcome;
        return kustomizePatchesManifestOutcome.getStore();

      case ManifestType.OpenshiftParam:
        OpenshiftParamManifestOutcome openshiftParamManifestOutcome = (OpenshiftParamManifestOutcome) manifestOutcome;
        return openshiftParamManifestOutcome.getStore();

      case ManifestType.OpenshiftTemplate:
        OpenshiftManifestOutcome openshiftManifestOutcome = (OpenshiftManifestOutcome) manifestOutcome;
        return openshiftManifestOutcome.getStore();

      case ManifestType.Kustomize:
        KustomizeManifestOutcome kustomizeManifestOutcome = (KustomizeManifestOutcome) manifestOutcome;
        return kustomizeManifestOutcome.getStore();

      default:
        throw new UnsupportedOperationException(format("Unsupported Manifest type: [%s]", manifestOutcome.getType()));
    }
  }

  public List<ManifestOutcome> getStepLevelManifestOutcomes(StepElementParameters stepElementParameters) {
    if (!(stepElementParameters.getSpec() instanceof K8sApplyStepParameters)) {
      return Collections.emptyList();
    }
    if (((K8sApplyStepParameters) stepElementParameters.getSpec()).getOverrides() == null) {
      return Collections.emptyList();
    }
    List<ManifestOutcome> manifestOutcomes = new ArrayList<>();
    List<ManifestAttributes> manifestAttributesList =
        ((K8sApplyStepParameters) stepElementParameters.getSpec())
            .getOverrides()
            .stream()
            .map(manifestConfigWrapper -> manifestConfigWrapper.getManifest().getSpec())
            .collect(Collectors.toList());

    for (int i = 0; i < manifestAttributesList.size(); i++) {
      ManifestAttributes manifestAttributes = manifestAttributesList.get(i);
      manifestOutcomes.add(ManifestOutcomeMapper.toManifestOutcome(manifestAttributes, i));
    }
    return manifestOutcomes;
  }

  public Optional<ConfigFilesOutcome> getConfigFilesOutcome(Ambiance ambiance) {
    OptionalOutcome configFilesOutcome = outcomeService.resolveOptional(
        ambiance, RefObjectUtils.getOutcomeRefObject(OutcomeExpressionConstants.CONFIG_FILES));

    if (!configFilesOutcome.isFound()) {
      return Optional.empty();
    }

    return Optional.of((ConfigFilesOutcome) configFilesOutcome.getOutcome());
  }

  public InfrastructureOutcome getInfrastructureOutcome(Ambiance ambiance) {
    OptionalOutcome optionalOutcome = outcomeService.resolveOptional(
        ambiance, RefObjectUtils.getOutcomeRefObject(OutcomeExpressionConstants.INFRASTRUCTURE_OUTCOME));
    if (!optionalOutcome.isFound()) {
      throw new InvalidRequestException(MISSING_INFRASTRUCTURE_ERROR, USER);
    }

    return (InfrastructureOutcome) optionalOutcome.getOutcome();
  }

  public Optional<ArtifactOutcome> resolveArtifactsOutcome(Ambiance ambiance) {
    OptionalOutcome artifactsOutcomeOption = outcomeService.resolveOptional(
        ambiance, RefObjectUtils.getOutcomeRefObject(OutcomeExpressionConstants.ARTIFACTS));
    if (artifactsOutcomeOption.isFound()) {
      ArtifactsOutcome artifactsOutcome = (ArtifactsOutcome) artifactsOutcomeOption.getOutcome();
      if (artifactsOutcome.getPrimary() != null) {
        return Optional.of(artifactsOutcome.getPrimary());
      }
    }
    return Optional.empty();
  }
}
