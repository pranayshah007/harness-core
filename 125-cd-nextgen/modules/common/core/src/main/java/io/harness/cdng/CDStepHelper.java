/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cdng;

import static io.harness.beans.FeatureName.CDS_GITHUB_APP_AUTHENTICATION;
import static io.harness.beans.FeatureName.OPTIMIZED_GIT_FETCH_FILES;
import static io.harness.common.ParameterFieldHelper.getBooleanParameterFieldValue;
import static io.harness.common.ParameterFieldHelper.getParameterFieldValue;
import static io.harness.connector.ConnectorModule.DEFAULT_CONNECTOR_SERVICE;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.data.structure.HarnessStringUtils.emptyIfNull;
import static io.harness.data.structure.ListUtils.trimStrings;
import static io.harness.delegate.beans.connector.scm.azurerepo.AzureRepoConnectionTypeDTO.PROJECT;
import static io.harness.delegate.beans.connector.scm.bitbucket.BitbucketApiAccessType.USERNAME_AND_TOKEN;
import static io.harness.eraro.ErrorCode.GENERAL_ERROR;
import static io.harness.exception.WingsException.USER;
import static io.harness.logging.CommandExecutionStatus.FAILURE;
import static io.harness.logging.UnitStatus.RUNNING;
import static io.harness.ng.core.infrastructure.InfrastructureKind.KUBERNETES_AWS;
import static io.harness.ng.core.infrastructure.InfrastructureKind.KUBERNETES_AZURE;
import static io.harness.ng.core.infrastructure.InfrastructureKind.KUBERNETES_DIRECT;
import static io.harness.ng.core.infrastructure.InfrastructureKind.KUBERNETES_GCP;
import static io.harness.ng.core.infrastructure.InfrastructureKind.KUBERNETES_RANCHER;
import static io.harness.validation.Validator.notEmptyCheck;

import static software.wings.beans.LogHelper.color;

import static java.lang.String.format;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.commons.lang3.StringUtils.trim;
import static org.apache.commons.lang3.StringUtils.trimToEmpty;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.ProductModule;
import io.harness.beans.DecryptableEntity;
import io.harness.beans.DelegateTaskRequest;
import io.harness.beans.FeatureName;
import io.harness.beans.FileReference;
import io.harness.cdng.artifact.outcome.ArtifactOutcome;
import io.harness.cdng.artifact.outcome.ArtifactsOutcome;
import io.harness.cdng.configfile.ConfigFilesOutcome;
import io.harness.cdng.expressions.CDExpressionResolver;
import io.harness.cdng.featureFlag.CDFeatureFlagHelper;
import io.harness.cdng.hooks.steps.ServiceHooksOutcome;
import io.harness.cdng.infra.beans.InfrastructureOutcome;
import io.harness.cdng.infra.beans.K8sAwsInfrastructureOutcome;
import io.harness.cdng.infra.beans.K8sAzureInfrastructureOutcome;
import io.harness.cdng.infra.beans.K8sDirectInfrastructureOutcome;
import io.harness.cdng.infra.beans.K8sGcpInfrastructureOutcome;
import io.harness.cdng.infra.beans.K8sRancherInfrastructureOutcome;
import io.harness.cdng.k8s.K8sEntityHelper;
import io.harness.cdng.k8s.beans.GitFetchResponsePassThroughData;
import io.harness.cdng.k8s.beans.StepExceptionPassThroughData;
import io.harness.cdng.manifest.ManifestStoreType;
import io.harness.cdng.manifest.ManifestType;
import io.harness.cdng.manifest.mappers.ManifestOutcomeValidator;
import io.harness.cdng.manifest.steps.outcome.ManifestsOutcome;
import io.harness.cdng.manifest.yaml.AzureRepoStore;
import io.harness.cdng.manifest.yaml.GitStoreConfig;
import io.harness.cdng.manifest.yaml.ManifestOutcome;
import io.harness.cdng.manifest.yaml.S3StoreConfig;
import io.harness.cdng.manifest.yaml.harness.HarnessStore;
import io.harness.cdng.service.steps.ServiceSweepingOutput;
import io.harness.cdng.service.steps.constants.ServiceStepV3Constants;
import io.harness.cdng.ssh.SshEntityHelper;
import io.harness.cdng.stepsdependency.constants.OutcomeExpressionConstants;
import io.harness.cdng.tas.TasEntityHelper;
import io.harness.common.NGTimeConversionHelper;
import io.harness.common.ParameterFieldHelper;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.connector.helper.GitApiAccessDecryptionHelper;
import io.harness.connector.helper.GithubAppDTOToGithubAppSpecDTOMapper;
import io.harness.connector.services.ConnectorService;
import io.harness.connector.task.git.GitAuthenticationDecryptionHelper;
import io.harness.connector.validator.scmValidators.GitConfigAuthenticationInfoHelper;
import io.harness.data.structure.CollectionUtils;
import io.harness.delegate.SubmitTaskRequest;
import io.harness.delegate.TaskSelector;
import io.harness.delegate.beans.TaskData;
import io.harness.delegate.beans.connector.artifactoryconnector.ArtifactoryConnectorDTO;
import io.harness.delegate.beans.connector.awsconnector.AwsConnectorDTO;
import io.harness.delegate.beans.connector.gcpconnector.GcpConnectorDTO;
import io.harness.delegate.beans.connector.helm.HttpHelmConnectorDTO;
import io.harness.delegate.beans.connector.helm.OciHelmConnectorDTO;
import io.harness.delegate.beans.connector.scm.GitAuthType;
import io.harness.delegate.beans.connector.scm.GitConnectionType;
import io.harness.delegate.beans.connector.scm.ScmConnector;
import io.harness.delegate.beans.connector.scm.adapter.ScmConnectorMapper;
import io.harness.delegate.beans.connector.scm.azurerepo.AzureRepoApiAccessDTO;
import io.harness.delegate.beans.connector.scm.azurerepo.AzureRepoApiAccessType;
import io.harness.delegate.beans.connector.scm.azurerepo.AzureRepoConnectionTypeDTO;
import io.harness.delegate.beans.connector.scm.azurerepo.AzureRepoConnectorDTO;
import io.harness.delegate.beans.connector.scm.azurerepo.AzureRepoHttpAuthenticationType;
import io.harness.delegate.beans.connector.scm.azurerepo.AzureRepoHttpCredentialsDTO;
import io.harness.delegate.beans.connector.scm.azurerepo.AzureRepoTokenSpecDTO;
import io.harness.delegate.beans.connector.scm.azurerepo.AzureRepoUsernameTokenDTO;
import io.harness.delegate.beans.connector.scm.bitbucket.BitbucketApiAccessDTO;
import io.harness.delegate.beans.connector.scm.bitbucket.BitbucketConnectorDTO;
import io.harness.delegate.beans.connector.scm.bitbucket.BitbucketHttpAuthenticationType;
import io.harness.delegate.beans.connector.scm.bitbucket.BitbucketHttpCredentialsDTO;
import io.harness.delegate.beans.connector.scm.bitbucket.BitbucketUsernamePasswordDTO;
import io.harness.delegate.beans.connector.scm.bitbucket.BitbucketUsernameTokenApiAccessDTO;
import io.harness.delegate.beans.connector.scm.genericgitconnector.GitConfigDTO;
import io.harness.delegate.beans.connector.scm.github.GithubApiAccessDTO;
import io.harness.delegate.beans.connector.scm.github.GithubApiAccessType;
import io.harness.delegate.beans.connector.scm.github.GithubAppDTO;
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
import io.harness.delegate.beans.logstreaming.CommandUnitProgress;
import io.harness.delegate.beans.logstreaming.CommandUnitsProgress;
import io.harness.delegate.beans.logstreaming.UnitProgressData;
import io.harness.delegate.beans.logstreaming.UnitProgressDataMapper;
import io.harness.delegate.beans.storeconfig.FetchType;
import io.harness.delegate.beans.storeconfig.GitStoreDelegateConfig;
import io.harness.delegate.beans.storeconfig.S3StoreDelegateConfig;
import io.harness.delegate.task.TaskParameters;
import io.harness.delegate.task.git.GitFetchFilesConfig;
import io.harness.delegate.task.k8s.K8sInfraDelegateConfig;
import io.harness.delegate.task.localstore.LocalStoreFetchFilesResult;
import io.harness.delegate.task.pcf.response.TasInfraConfig;
import io.harness.delegate.task.ssh.SshInfraDelegateConfig;
import io.harness.delegate.task.ssh.WinRmInfraDelegateConfig;
import io.harness.encryption.SecretRefData;
import io.harness.eraro.Level;
import io.harness.eventsframework.schemas.entity.EntityDetailProtoDTO;
import io.harness.exception.InvalidArgumentsException;
import io.harness.exception.InvalidRequestException;
import io.harness.executions.steps.StepConstants;
import io.harness.expression.EngineExpressionEvaluator;
import io.harness.filestore.dto.node.FileNodeDTO;
import io.harness.filestore.dto.node.FileStoreNodeDTO;
import io.harness.filestore.service.FileStoreService;
import io.harness.logging.CommandExecutionStatus;
import io.harness.logging.LogCallback;
import io.harness.logging.LogLevel;
import io.harness.logging.UnitProgress;
import io.harness.logging.UnitStatus;
import io.harness.logstreaming.LogStreamingStepClientFactory;
import io.harness.logstreaming.NGLogCallback;
import io.harness.ng.core.NGAccess;
import io.harness.ng.core.dto.secrets.SSHKeySpecDTO;
import io.harness.ng.core.filestore.NGFileType;
import io.harness.ng.core.service.yaml.NGServiceConfig;
import io.harness.ng.core.service.yaml.NGServiceV2InfoConfig;
import io.harness.plancreator.steps.TaskSelectorYaml;
import io.harness.plancreator.steps.common.StepElementParameters;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.execution.failure.FailureData;
import io.harness.pms.contracts.execution.failure.FailureInfo;
import io.harness.pms.contracts.execution.failure.FailureType;
import io.harness.pms.contracts.execution.tasks.TaskRequest;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.expression.EngineExpressionService;
import io.harness.pms.rbac.PipelineRbacHelper;
import io.harness.pms.sdk.core.data.OptionalOutcome;
import io.harness.pms.sdk.core.data.OptionalSweepingOutput;
import io.harness.pms.sdk.core.resolver.RefObjectUtils;
import io.harness.pms.sdk.core.resolver.outcome.OutcomeService;
import io.harness.pms.sdk.core.resolver.outputs.ExecutionSweepingOutputService;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.pms.yaml.ParameterField;
import io.harness.pms.yaml.YamlUtils;
import io.harness.pms.yaml.validation.ExpressionUtils;
import io.harness.secretmanagerclient.services.api.SecretManagerClientService;
import io.harness.security.encryption.EncryptedDataDetail;
import io.harness.serializer.KryoSerializer;
import io.harness.steps.EntityReferenceExtractorUtils;
import io.harness.steps.StepHelper;
import io.harness.steps.TaskRequestsUtils;
import io.harness.validation.Validator;

import software.wings.beans.LogColor;
import software.wings.beans.LogWeight;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import javax.annotation.Nonnull;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;
import org.jetbrains.annotations.NotNull;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_K8S})
@Slf4j
public class CDStepHelper {
  public static final String MISSING_INFRASTRUCTURE_ERROR = "Infrastructure section is missing or is not configured";
  @Inject private GitConfigAuthenticationInfoHelper gitConfigAuthenticationInfoHelper;
  @Named("PRIVILEGED") @Inject private SecretManagerClientService secretManagerClientService;
  @Inject protected CDFeatureFlagHelper cdFeatureFlagHelper;
  @Inject private EngineExpressionService engineExpressionService;
  @Named(DEFAULT_CONNECTOR_SERVICE) @Inject private ConnectorService connectorService;
  @Inject private K8sEntityHelper k8sEntityHelper;
  @Inject private TasEntityHelper tasEntityHelper;
  @Inject private SshEntityHelper sshEntityHelper;
  @Inject private LogStreamingStepClientFactory logStreamingStepClientFactory;
  @Inject private EntityReferenceExtractorUtils entityReferenceExtractorUtils;
  @Inject private PipelineRbacHelper pipelineRbacHelper;
  @Inject private FileStoreService fileStoreService;
  @Inject protected OutcomeService outcomeService;
  @Inject @Named("referenceFalseKryoSerializer") private KryoSerializer referenceFalseKryoSerializer;
  @Inject protected StepHelper stepHelper;
  @Inject private ExecutionSweepingOutputService sweepingOutputService;
  @Inject private CDExpressionResolver cdExpressionResolver;

  public static final String RELEASE_NAME_VALIDATION_REGEX =
      "[a-z0-9]([-a-z0-9]*[a-z0-9])?(\\.[a-z0-9]([-a-z0-9]*[a-z0-9])?)*";

  public static final Pattern releaseNamePattern = Pattern.compile(RELEASE_NAME_VALIDATION_REGEX);
  public static final String GIT = "/_git/";

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

  public boolean isBitbucketTokenAuth(ScmConnector scmConnector) {
    return scmConnector instanceof BitbucketConnectorDTO
        && (((BitbucketConnectorDTO) scmConnector).getApiAccess() != null
            || isBitbucketUsernameTokenAuth((BitbucketConnectorDTO) scmConnector));
  }

  public boolean isBitbucketUsernameTokenAuth(BitbucketConnectorDTO bitbucketConnectorDTO) {
    return bitbucketConnectorDTO.getAuthentication().getCredentials() instanceof BitbucketHttpCredentialsDTO
        && ((BitbucketHttpCredentialsDTO) bitbucketConnectorDTO.getAuthentication().getCredentials())
               .getType()
               .equals(BitbucketHttpAuthenticationType.USERNAME_AND_PASSWORD);
  }

  public boolean isGithubUsernameTokenAuth(GithubConnectorDTO githubConnectorDTO) {
    return githubConnectorDTO.getAuthentication().getCredentials() instanceof GithubHttpCredentialsDTO
        && ((GithubHttpCredentialsDTO) githubConnectorDTO.getAuthentication().getCredentials())
               .getType()
               .equals(GithubHttpAuthenticationType.USERNAME_AND_TOKEN);
  }

  public boolean isAzureRepoUsernameTokenAuth(AzureRepoConnectorDTO azureRepoConnectorDTO) {
    return azureRepoConnectorDTO.getAuthentication().getCredentials() instanceof AzureRepoHttpCredentialsDTO
        && ((AzureRepoHttpCredentialsDTO) azureRepoConnectorDTO.getAuthentication().getCredentials())
               .getType()
               .equals(AzureRepoHttpAuthenticationType.USERNAME_AND_TOKEN);
  }

  public boolean isGithubTokenOrAppAuth(ScmConnector scmConnector) {
    return scmConnector instanceof GithubConnectorDTO
        && (((GithubConnectorDTO) scmConnector).getApiAccess() != null
            || isGithubUsernameTokenAuth((GithubConnectorDTO) scmConnector)
            || isGithubAppAuth((GithubConnectorDTO) scmConnector));
  }

  private boolean isGithubAppAuth(GithubConnectorDTO githubConnectorDTO) {
    return githubConnectorDTO.getAuthentication().getCredentials() instanceof GithubHttpCredentialsDTO
        && (((GithubHttpCredentialsDTO) githubConnectorDTO.getAuthentication().getCredentials()).getType()
            == GithubHttpAuthenticationType.GITHUB_APP);
  }

  public boolean isAzureRepoTokenAuth(ScmConnector scmConnector) {
    return scmConnector instanceof AzureRepoConnectorDTO
        && (((AzureRepoConnectorDTO) scmConnector).getApiAccess() != null
            || isAzureRepoUsernameTokenAuth((AzureRepoConnectorDTO) scmConnector));
  }

  public SSHKeySpecDTO getSshKeySpecDTO(GitConfigDTO gitConfigDTO, Ambiance ambiance) {
    return gitConfigAuthenticationInfoHelper.getSSHKey(gitConfigDTO, AmbianceUtils.getAccountId(ambiance),
        AmbianceUtils.getOrgIdentifier(ambiance), AmbianceUtils.getProjectIdentifier(ambiance));
  }

  public boolean isOptimizedFilesFetch(@Nonnull ConnectorInfoDTO connectorDTO, String accountId) {
    return cdFeatureFlagHelper.isEnabled(accountId, OPTIMIZED_GIT_FETCH_FILES)
        && ((isGithubTokenOrAppAuth((ScmConnector) connectorDTO.getConnectorConfig())
                || isGitlabTokenAuth((ScmConnector) connectorDTO.getConnectorConfig()))
            || (isAzureRepoTokenAuth((ScmConnector) connectorDTO.getConnectorConfig()))
            || (isBitbucketTokenAuth((ScmConnector) connectorDTO.getConnectorConfig())));
  }

  public void addApiAuthIfRequired(ScmConnector scmConnector) {
    if (scmConnector instanceof GithubConnectorDTO && ((GithubConnectorDTO) scmConnector).getApiAccess() == null) {
      GithubConnectorDTO githubConnectorDTO = (GithubConnectorDTO) scmConnector;
      if (isGithubUsernameTokenAuth(githubConnectorDTO)) {
        SecretRefData tokenRef =
            ((GithubUsernameTokenDTO) ((GithubHttpCredentialsDTO) githubConnectorDTO.getAuthentication()
                                           .getCredentials())
                    .getHttpCredentialsSpec())
                .getTokenRef();
        GithubApiAccessDTO apiAccessDTO = GithubApiAccessDTO.builder()
                                              .type(GithubApiAccessType.TOKEN)
                                              .spec(GithubTokenSpecDTO.builder().tokenRef(tokenRef).build())
                                              .build();
        githubConnectorDTO.setApiAccess(apiAccessDTO);
      } else if (isGithubAppAuth(githubConnectorDTO)) {
        githubConnectorDTO.setApiAccess(getGitAppAccessFromGithubAppAuth(githubConnectorDTO));
      }
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
    } else if (scmConnector instanceof BitbucketConnectorDTO
        && ((BitbucketConnectorDTO) scmConnector).getApiAccess() == null
        && isBitbucketUsernameTokenAuth((BitbucketConnectorDTO) scmConnector)) {
      addApiAuthIfRequiredBitbucket(scmConnector);
    } else if (scmConnector instanceof AzureRepoConnectorDTO
        && ((AzureRepoConnectorDTO) scmConnector).getApiAccess() == null && isAzureRepoTokenAuth(scmConnector)) {
      addApiAuthIfRequiredAzureRepo(scmConnector);
    }
  }

  public GithubApiAccessDTO getGitAppAccessFromGithubAppAuth(GithubConnectorDTO githubConnectorDTO) {
    GithubAppDTO githubAppDTO =
        (GithubAppDTO) ((GithubHttpCredentialsDTO) githubConnectorDTO.getAuthentication().getCredentials())
            .getHttpCredentialsSpec();
    return GithubApiAccessDTO.builder()
        .type(GithubApiAccessType.GITHUB_APP)
        .spec(GithubAppDTOToGithubAppSpecDTOMapper.toGitHubSpec(githubAppDTO))
        .build();
  }

  public void addApiAuthIfRequiredAzureRepo(ScmConnector scmConnector) {
    AzureRepoConnectorDTO azureRepoConnectorDTO = (AzureRepoConnectorDTO) scmConnector;
    SecretRefData tokenRef =
        ((AzureRepoUsernameTokenDTO) ((AzureRepoHttpCredentialsDTO) azureRepoConnectorDTO.getAuthentication()
                                          .getCredentials())
                .getHttpCredentialsSpec())
            .getTokenRef();
    AzureRepoApiAccessDTO apiAccessDTO = AzureRepoApiAccessDTO.builder()
                                             .type(AzureRepoApiAccessType.TOKEN)
                                             .spec(AzureRepoTokenSpecDTO.builder().tokenRef(tokenRef).build())
                                             .build();
    azureRepoConnectorDTO.setApiAccess(apiAccessDTO);
  }

  public void addApiAuthIfRequiredBitbucket(ScmConnector scmConnector) {
    BitbucketConnectorDTO bitbucketConnectorDTO = (BitbucketConnectorDTO) scmConnector;
    BitbucketUsernamePasswordDTO credentials =
        (BitbucketUsernamePasswordDTO) ((BitbucketHttpCredentialsDTO) bitbucketConnectorDTO.getAuthentication()
                                            .getCredentials())
            .getHttpCredentialsSpec();
    SecretRefData tokenRef = credentials.getPasswordRef();
    String usernameRef = credentials.getUsername();
    BitbucketApiAccessDTO apiAccessDTO =
        BitbucketApiAccessDTO.builder()
            .type(USERNAME_AND_TOKEN)
            .spec(BitbucketUsernameTokenApiAccessDTO.builder()
                      .tokenRef(tokenRef)
                      .usernameRef(SecretRefData.builder().decryptedValue(usernameRef.toCharArray()).build())
                      .build())
            .build();
    bitbucketConnectorDTO.setApiAccess(apiAccessDTO);
  }

  @NotNull
  public String getGitRepoUrl(ScmConnector scmConnector, String repoName) {
    repoName = trimToEmpty(repoName);
    notEmptyCheck("Repo name cannot be empty for Account level git connector", repoName);
    String purgedRepoUrl = scmConnector.getUrl().replaceAll("/*$", "");
    String purgedRepoName = repoName.replaceAll("^/*", "");
    return purgedRepoUrl + "/" + purgedRepoName;
  }

  @NotNull
  public String getGitRepoUrlForAzureProject(ScmConnector scmConnector, String repoName) {
    repoName = trimToEmpty(repoName);
    notEmptyCheck("Repo name cannot be empty for Account level git connector", repoName);
    String purgedRepoUrl = scmConnector.getUrl().replaceAll("/*$", "");
    String purgedRepoName = repoName.replaceAll("^/*", "");
    return purgedRepoUrl + GIT + purgedRepoName;
  }

  @NotNull
  public String convertGitAccountProjectUrlToRepoUrl(
      GitStoreConfig gitstoreConfig, ScmConnector scmConnector, GitAuthType gitAuthType, String repoName) {
    if (gitstoreConfig instanceof AzureRepoStore && gitAuthType == GitAuthType.HTTP) {
      return getGitRepoUrlForAzureProject(scmConnector, repoName);
    }
    return getGitRepoUrl(scmConnector, repoName);
  }

  public void convertToRepoGitConfig(GitStoreConfig gitstoreConfig, ScmConnector scmConnector) {
    String repoName = gitstoreConfig.getRepoName() != null ? gitstoreConfig.getRepoName().getValue() : null;
    if (scmConnector instanceof GitConfigDTO) {
      GitConfigDTO gitConfigDTO = (GitConfigDTO) scmConnector;
      if (gitConfigDTO.getGitConnectionType() == GitConnectionType.ACCOUNT
          || (gitstoreConfig instanceof AzureRepoStore
              && gitConfigDTO.getGitConnectionType() == GitConnectionType.PROJECT)) {
        String repoUrl =
            convertGitAccountProjectUrlToRepoUrl(gitstoreConfig, gitConfigDTO, gitConfigDTO.getGitAuthType(), repoName);
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
    } else if (scmConnector instanceof BitbucketConnectorDTO) {
      BitbucketConnectorDTO bitbucketConnectorDTO = (BitbucketConnectorDTO) scmConnector;
      if (bitbucketConnectorDTO.getConnectionType() == GitConnectionType.ACCOUNT) {
        String repoUrl = getGitRepoUrl(bitbucketConnectorDTO, repoName);
        bitbucketConnectorDTO.setUrl(repoUrl);
        bitbucketConnectorDTO.setConnectionType(GitConnectionType.REPO);
      }
    } else if (scmConnector instanceof AzureRepoConnectorDTO) {
      AzureRepoConnectorDTO azureRepoConnectorDTO = (AzureRepoConnectorDTO) scmConnector;
      if (azureRepoConnectorDTO.getConnectionType() == PROJECT) {
        String repoUrl = convertGitAccountProjectUrlToRepoUrl(
            gitstoreConfig, azureRepoConnectorDTO, azureRepoConnectorDTO.getAuthentication().getAuthType(), repoName);
        azureRepoConnectorDTO.setUrl(repoUrl);
        azureRepoConnectorDTO.setConnectionType(AzureRepoConnectionTypeDTO.REPO);
      }
    }
  }

  public GitStoreDelegateConfig getGitStoreDelegateConfig(@Nonnull GitStoreConfig gitstoreConfig,
      @Nonnull ConnectorInfoDTO connectorDTO, ManifestOutcome manifestOutcome, List<String> paths, Ambiance ambiance) {
    boolean optimizedFilesFetch = isOptimizedFilesFetch(connectorDTO, AmbianceUtils.getAccountId(ambiance))
        && !ManifestType.Kustomize.equals(manifestOutcome.getType());

    return getGitStoreDelegateConfig(gitstoreConfig, connectorDTO, paths, ambiance, manifestOutcome.getType(),
        manifestOutcome.getIdentifier(), optimizedFilesFetch);
  }

  public GitStoreDelegateConfig getGitStoreDelegateConfig(@Nonnull GitStoreConfig gitstoreConfig,
      @Nonnull ConnectorInfoDTO connectorDTO, List<String> paths, Ambiance ambiance, String manifestType,
      String manifestIdentifier, boolean optimizedFilesFetch) {
    NGAccess basicNGAccessObject = AmbianceUtils.getNgAccess(ambiance);
    ScmConnector scmConnector;
    List<EncryptedDataDetail> apiAuthEncryptedDataDetails = null;
    GitConfigDTO gitConfigDTO = ScmConnectorMapper.toGitConfigDTO((ScmConnector) connectorDTO.getConnectorConfig());
    SSHKeySpecDTO sshKeySpecDTO = getSshKeySpecDTO(gitConfigDTO, ambiance);
    List<EncryptedDataDetail> encryptedDataDetails =
        gitConfigAuthenticationInfoHelper.getEncryptedDataDetails(gitConfigDTO, sshKeySpecDTO, basicNGAccessObject);

    scmConnector = gitConfigDTO;
    boolean githubAppAuthentication =
        GitAuthenticationDecryptionHelper.isGitHubAppAuthentication((ScmConnector) connectorDTO.getConnectorConfig())
        && cdFeatureFlagHelper.isEnabled(basicNGAccessObject.getAccountIdentifier(), CDS_GITHUB_APP_AUTHENTICATION);

    if (optimizedFilesFetch) {
      scmConnector = (ScmConnector) connectorDTO.getConnectorConfig();
      addApiAuthIfRequired(scmConnector);
      final DecryptableEntity apiAccessDecryptableEntity =
          GitApiAccessDecryptionHelper.getAPIAccessDecryptableEntity(scmConnector);
      apiAuthEncryptedDataDetails =
          secretManagerClientService.getEncryptionDetails(basicNGAccessObject, apiAccessDecryptableEntity);
    } else if (githubAppAuthentication) {
      scmConnector = (ScmConnector) connectorDTO.getConnectorConfig();
      encryptedDataDetails =
          gitConfigAuthenticationInfoHelper.getGithubAppEncryptedDataDetail(scmConnector, basicNGAccessObject);
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
        .connectorId(connectorDTO.getIdentifier())
        .connectorName(connectorDTO.getName())
        .manifestType(manifestType)
        .manifestId(manifestIdentifier)
        .optimizedFilesFetch(optimizedFilesFetch)
        .build();
  }

  public GitFetchFilesConfig getGitFetchFilesConfig(
      Ambiance ambiance, GitStoreConfig gitStoreConfig, String manifestType, String manifestIdentifier) {
    String connectorId = gitStoreConfig.getConnectorRef().getValue();
    ConnectorInfoDTO connectorDTO = getConnector(connectorId, ambiance);
    List<String> paths = getParameterFieldValue(gitStoreConfig.getPaths());

    boolean useOptimizedFilesFetch = isOptimizedFilesFetch(connectorDTO, AmbianceUtils.getAccountId(ambiance));

    GitStoreDelegateConfig gitStoreDelegateConfig = getGitStoreDelegateConfig(
        gitStoreConfig, connectorDTO, paths, ambiance, manifestType, manifestIdentifier, useOptimizedFilesFetch);

    return getGitFetchFilesConfigFromBuilder(manifestIdentifier, manifestType, false, gitStoreDelegateConfig);
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

  public S3StoreDelegateConfig getS3StoreDelegateConfig(
      @Nonnull S3StoreConfig s3StoreConfig, @Nonnull ConnectorInfoDTO awsConnectorDTO, Ambiance ambiance) {
    return S3StoreDelegateConfig.builder()
        .bucketName(getParameterFieldValue(s3StoreConfig.getBucketName()))
        .region(getParameterFieldValue(s3StoreConfig.getRegion()))
        .paths(s3StoreConfig.getPaths().getValue())
        .awsConnector((AwsConnectorDTO) awsConnectorDTO.getConnectorConfig())
        .encryptedDataDetails(
            k8sEntityHelper.getEncryptionDataDetails(awsConnectorDTO, AmbianceUtils.getNgAccess(ambiance)))
        .build();
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
      case KUBERNETES_AWS:
        K8sAwsInfrastructureOutcome k8sAwsInfrastructureOutcome = (K8sAwsInfrastructureOutcome) infrastructure;
        releaseName = k8sAwsInfrastructureOutcome.getReleaseName();
        break;
      case KUBERNETES_RANCHER:
        K8sRancherInfrastructureOutcome rancherInfraOutcome = (K8sRancherInfrastructureOutcome) infrastructure;
        releaseName = rancherInfraOutcome.getReleaseName();
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

  public String getFileContentAsBase64(Ambiance ambiance, String scopedFilePath, long allowedBytesFileSize) {
    String content = getFileContentAsString(ambiance, scopedFilePath, allowedBytesFileSize);
    return "${ngBase64Manager.encode(\"" + content + "\")}";
  }

  public String getFileContentAsString(Ambiance ambiance, final String scopedFilePath, long allowedBytesFileSize) {
    return cdExpressionResolver.renderExpression(ambiance,
        fileStoreService.getFileContentAsString(AmbianceUtils.getAccountId(ambiance),
            AmbianceUtils.getOrgIdentifier(ambiance), AmbianceUtils.getProjectIdentifier(ambiance), scopedFilePath,
            allowedBytesFileSize));
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
      case ManifestStoreType.AZURE_REPO:
        if (!(connectorInfoDTO.getConnectorConfig() instanceof AzureRepoConnectorDTO)) {
          throw new InvalidRequestException(
              format("Invalid connector selected in %s. Select Azure_Repo connector", message));
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

  public K8sInfraDelegateConfig getK8sInfraDelegateConfig(InfrastructureOutcome infrastructure, Ambiance ambiance) {
    NGAccess ngAccess = AmbianceUtils.getNgAccess(ambiance);
    return k8sEntityHelper.getK8sInfraDelegateConfig(infrastructure, ngAccess);
  }

  public TasInfraConfig getTasInfraConfig(InfrastructureOutcome infrastructure, Ambiance ambiance) {
    NGAccess ngAccess = AmbianceUtils.getNgAccess(ambiance);
    return tasEntityHelper.getTasInfraConfig(infrastructure, ngAccess);
  }

  public SshInfraDelegateConfig getSshInfraDelegateConfig(InfrastructureOutcome infrastructure, Ambiance ambiance) {
    return sshEntityHelper.getSshInfraDelegateConfig(infrastructure, ambiance);
  }

  public WinRmInfraDelegateConfig getWinRmInfraDelegateConfig(InfrastructureOutcome infrastructure, Ambiance ambiance) {
    return sshEntityHelper.getWinRmInfraDelegateConfig(infrastructure, ambiance);
  }

  public boolean isUseLatestKustomizeVersion(String accountId) {
    return cdFeatureFlagHelper.isEnabled(accountId, FeatureName.NEW_KUSTOMIZE_BINARY);
  }

  public boolean isUseNewKubectlVersion(String accountId) {
    return cdFeatureFlagHelper.isEnabled(accountId, FeatureName.NEW_KUBECTL_VERSION);
  }

  public boolean shouldUseK8sApiForSteadyStateCheck(String accountId) {
    return cdFeatureFlagHelper.isEnabled(accountId, FeatureName.USE_K8S_API_FOR_STEADY_STATE_CHECK);
  }

  public boolean isSkipAddingTrackSelectorToDeployment(String accountId) {
    return cdFeatureFlagHelper.isEnabled(accountId, FeatureName.SKIP_ADDING_TRACK_LABEL_SELECTOR_IN_ROLLING);
  }

  public boolean isEnabledSupportHPAAndPDB(String accountId) {
    return cdFeatureFlagHelper.isEnabled(accountId, FeatureName.CDS_SUPPORT_HPA_AND_PDB_NG);
  }

  public boolean isSkipUnchangedManifest(String accountId, boolean value) {
    return cdFeatureFlagHelper.isEnabled(accountId, FeatureName.CDS_SUPPORT_SKIPPING_BG_DEPLOYMENT_NG) && value;
  }

  public boolean isStoreReleaseHash(String accountId) {
    return cdFeatureFlagHelper.isEnabled(accountId, FeatureName.CDS_SUPPORT_SKIPPING_BG_DEPLOYMENT_NG);
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
              if (unitProgress.getStatus() != UnitStatus.SUCCESS && unitProgress.getStatus() != UnitStatus.FAILURE) {
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

  public Optional<ConfigFilesOutcome> getConfigFilesOutcome(Ambiance ambiance) {
    OptionalOutcome configFilesOutcome = outcomeService.resolveOptional(
        ambiance, RefObjectUtils.getOutcomeRefObject(OutcomeExpressionConstants.CONFIG_FILES));

    if (!configFilesOutcome.isFound()) {
      return Optional.empty();
    }

    return Optional.of((ConfigFilesOutcome) configFilesOutcome.getOutcome());
  }

  public Optional<ServiceHooksOutcome> getServiceHooksOutcome(Ambiance ambiance) {
    OptionalOutcome serviceHooksOutcome = outcomeService.resolveOptional(
        ambiance, RefObjectUtils.getOutcomeRefObject(OutcomeExpressionConstants.SERVICE_HOOKS));

    if (!serviceHooksOutcome.isFound()) {
      return Optional.empty();
    }

    return Optional.of((ServiceHooksOutcome) serviceHooksOutcome.getOutcome());
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

  public TaskRequest prepareTaskRequest(
      Ambiance ambiance, TaskData taskData, List<String> units, String taskName, List<TaskSelector> selectors) {
    return TaskRequestsUtils.prepareCDTaskRequest(ambiance, taskData, referenceFalseKryoSerializer, units, taskName,
        selectors, stepHelper.getEnvironmentType(ambiance));
  }

  @Nonnull
  public Optional<NGServiceV2InfoConfig> fetchServiceConfigFromSweepingOutput(Ambiance ambiance) {
    final OptionalSweepingOutput resolveOptional = sweepingOutputService.resolveOptional(
        ambiance, RefObjectUtils.getOutcomeRefObject(ServiceStepV3Constants.SERVICE_SWEEPING_OUTPUT));
    NGServiceConfig ngServiceConfig = null;
    if (resolveOptional.isFound()) {
      try {
        ngServiceConfig = YamlUtils.read(
            ((ServiceSweepingOutput) resolveOptional.getOutput()).getFinalServiceYaml(), NGServiceConfig.class);
      } catch (IOException e) {
        throw new InvalidRequestException("Failed to read service yaml", e);
      }
    }

    if (ngServiceConfig == null || ngServiceConfig.getNgServiceV2InfoConfig() == null) {
      log.info("No service configuration found in the service sweeping output");
      return Optional.empty();
    }

    return Optional.ofNullable(ngServiceConfig.getNgServiceV2InfoConfig());
  }

  @Nonnull
  public String fetchServiceYamlFromSweepingOutput(Ambiance ambiance) {
    final OptionalSweepingOutput resolveOptional = sweepingOutputService.resolveOptional(
        ambiance, RefObjectUtils.getOutcomeRefObject(ServiceStepV3Constants.SERVICE_SWEEPING_OUTPUT));
    if (!resolveOptional.isFound()) {
      throw new InvalidRequestException(
          "Cannot find service. Make sure this is running in a CD stage with service configured");
    }
    return ((ServiceSweepingOutput) resolveOptional.getOutput()).getFinalServiceYaml();
  }

  public boolean areAllManifestsFromHarnessFileStore(List<? extends ManifestOutcome> manifestOutcomes) {
    boolean retVal = true;
    for (ManifestOutcome manifestOutcome : manifestOutcomes) {
      if (manifestOutcome.getStore() != null) {
        retVal = retVal && ManifestStoreType.HARNESS.equals(manifestOutcome.getStore().getKind());
      }
    }
    return retVal;
  }

  public boolean isAnyGitManifest(List<ManifestOutcome> ecsManifestsOutcomes) {
    for (ManifestOutcome manifest : ecsManifestsOutcomes) {
      if (manifest.getStore() != null && ManifestStoreType.isInGitSubset(manifest.getStore().getKind())) {
        return true;
      }
    }
    return false;
  }

  public List<String> fetchFilesContentFromLocalStore(
      Ambiance ambiance, ManifestOutcome manifestOutcome, LogCallback logCallback) {
    Map<String, LocalStoreFetchFilesResult> localStoreFileMapContents = new HashMap<>();
    LocalStoreFetchFilesResult localStoreFetchFilesResult = null;
    logCallback.saveExecutionLog(color(
        format("Fetching %s from Harness File Store", manifestOutcome.getType()), LogColor.White, LogWeight.Bold));
    if (ManifestStoreType.HARNESS.equals(manifestOutcome.getStore().getKind())) {
      NGAccess ngAccess = AmbianceUtils.getNgAccess(ambiance);
      localStoreFetchFilesResult = getFileContentsFromManifestOutcome(manifestOutcome, ngAccess, logCallback);
      localStoreFileMapContents.put(manifestOutcome.getType(), localStoreFetchFilesResult);
    }
    return localStoreFileMapContents.get(manifestOutcome.getType()).getLocalStoreFileContents();
  }

  private LocalStoreFetchFilesResult getFileContentsFromManifestOutcome(
      ManifestOutcome manifestOutcome, NGAccess ngAccess, LogCallback logCallback) {
    HarnessStore localStoreConfig = (HarnessStore) manifestOutcome.getStore();
    List<String> scopedFilePathList = localStoreConfig.getFiles().getValue();
    return getFileContentsFromManifest(
        ngAccess, scopedFilePathList, manifestOutcome.getType(), manifestOutcome.getIdentifier(), logCallback);
  }

  private LocalStoreFetchFilesResult getFileContentsFromManifest(NGAccess ngAccess, List<String> scopedFilePathList,
      String manifestType, String manifestIdentifier, LogCallback logCallback) {
    List<String> fileContents = new ArrayList<>();
    if (isNotEmpty(scopedFilePathList)) {
      logCallback.saveExecutionLog(
          color(format("Fetching %s files with identifier: %s", manifestType, manifestIdentifier), LogColor.White,
              LogWeight.Bold));
      logCallback.saveExecutionLog(color(format("Fetching following Files :"), LogColor.White));
      printFilesFetchedFromHarnessStore(scopedFilePathList, logCallback);
      logCallback.saveExecutionLog(
          color(format("Successfully fetched following files: "), LogColor.White, LogWeight.Bold));
      for (String scopedFilePath : scopedFilePathList) {
        Optional<FileStoreNodeDTO> valuesFile =
            validateAndFetchFileFromHarnessStore(scopedFilePath, ngAccess, manifestIdentifier);
        FileStoreNodeDTO fileStoreNodeDTO = valuesFile.get();
        if (NGFileType.FILE.equals(fileStoreNodeDTO.getType())) {
          FileNodeDTO file = (FileNodeDTO) fileStoreNodeDTO;
          if (isNotEmpty(file.getContent())) {
            fileContents.add(file.getContent());
          } else {
            throw new InvalidRequestException(
                format("The following file %s in Harness File Store has empty content", scopedFilePath));
          }
          logCallback.saveExecutionLog(color(format("- %s", scopedFilePath), LogColor.White));
        } else {
          throw new UnsupportedOperationException("Only File type is supported. Please enter the correct file path");
        }
      }
    }
    return LocalStoreFetchFilesResult.builder().LocalStoreFileContents(fileContents).build();
  }

  private Optional<FileStoreNodeDTO> validateAndFetchFileFromHarnessStore(
      String scopedFilePath, NGAccess ngAccess, String manifestIdentifier) {
    if (isBlank(scopedFilePath)) {
      throw new InvalidRequestException(
          format("File reference cannot be null or empty, manifest identifier: %s", manifestIdentifier));
    }
    FileReference fileReference = FileReference.of(
        scopedFilePath, ngAccess.getAccountIdentifier(), ngAccess.getOrgIdentifier(), ngAccess.getProjectIdentifier());

    Optional<FileStoreNodeDTO> manifestFile =
        fileStoreService.getWithChildrenByPath(fileReference.getAccountIdentifier(), fileReference.getOrgIdentifier(),
            fileReference.getProjectIdentifier(), fileReference.getPath(), true);
    if (!manifestFile.isPresent()) {
      throw new InvalidRequestException(
          format("File/Folder not found in File Store with path: [%s], scope: [%s], manifest identifier: [%s]",
              fileReference.getPath(), fileReference.getScope(), manifestIdentifier));
    }
    return manifestFile;
  }

  private void printFilesFetchedFromHarnessStore(List<String> scopedFilePathList, LogCallback logCallback) {
    for (String scopedFilePath : scopedFilePathList) {
      logCallback.saveExecutionLog(color(format("- %s", scopedFilePath), LogColor.White));
    }
  }

  public UnitProgressData getCommandUnitProgressData(
      String commandName, CommandExecutionStatus commandExecutionStatus) {
    LinkedHashMap<String, CommandUnitProgress> commandUnitProgressMap = new LinkedHashMap<>();
    CommandUnitProgress commandUnitProgress = CommandUnitProgress.builder().status(commandExecutionStatus).build();
    commandUnitProgressMap.put(commandName, commandUnitProgress);
    CommandUnitsProgress commandUnitsProgress =
        CommandUnitsProgress.builder().commandUnitProgressMap(commandUnitProgressMap).build();
    return UnitProgressDataMapper.toUnitProgressData(commandUnitsProgress);
  }

  public DelegateTaskRequest mapTaskRequestToDelegateTaskRequest(
      TaskRequest taskRequest, TaskData taskData, Set<String> taskSelectors) {
    return mapTaskRequestToDelegateTaskRequest(taskRequest, taskData, taskSelectors, "", false);
  }

  @Nonnull
  public DelegateTaskRequest mapTaskRequestToDelegateTaskRequest(TaskRequest taskRequest, TaskData taskData,
      Set<String> taskSelectors, String baseLogKey, boolean shouldSkipOpenStream) {
    final SubmitTaskRequest submitTaskRequest = taskRequest.getDelegateTaskRequest().getRequest();
    return DelegateTaskRequest.builder()
        .taskParameters((TaskParameters) taskData.getParameters()[0])
        .taskType(taskData.getTaskType())
        .parked(taskData.isParked())
        .accountId(submitTaskRequest.getAccountId().getId())
        .taskSetupAbstractions(submitTaskRequest.getSetupAbstractions().getValuesMap())
        .taskSelectors(taskSelectors)
        .executionTimeout(Duration.ofMillis(taskData.getTimeout()))
        .logStreamingAbstractions(new LinkedHashMap<>(submitTaskRequest.getLogAbstractions().getValuesMap()))
        .forceExecute(submitTaskRequest.getForceExecute())
        .expressionFunctorToken(taskData.getExpressionFunctorToken())
        .eligibleToExecuteDelegateIds(submitTaskRequest.getEligibleToExecuteDelegateIdsList())
        .executeOnHarnessHostedDelegates(submitTaskRequest.getExecuteOnHarnessHostedDelegates())
        .emitEvent(submitTaskRequest.getEmitEvent())
        .stageId(submitTaskRequest.getStageId())
        .baseLogKey(baseLogKey)
        .shouldSkipOpenStream(shouldSkipOpenStream)
        .build();
  }

  public List<TaskSelector> getDelegateSelectors(ConnectorInfoDTO connectorInfoDTO) {
    Set<String> delegateSelectors;
    switch (connectorInfoDTO.getConnectorType()) {
      case GITHUB:
        delegateSelectors = ((GithubConnectorDTO) connectorInfoDTO.getConnectorConfig()).getDelegateSelectors();
        break;
      case GIT:
        delegateSelectors = ((GitConfigDTO) connectorInfoDTO.getConnectorConfig()).getDelegateSelectors();
        break;
      case BITBUCKET:
        delegateSelectors = ((BitbucketConnectorDTO) connectorInfoDTO.getConnectorConfig()).getDelegateSelectors();
        break;
      case GITLAB:
        delegateSelectors = ((GitlabConnectorDTO) connectorInfoDTO.getConnectorConfig()).getDelegateSelectors();
        break;
      default:
        throw new UnsupportedOperationException("Unknown Connector Config for delegate selectors");
    }

    return TaskSelectorYaml.toTaskSelector(CollectionUtils.emptyIfNull(delegateSelectors)
                                               .stream()
                                               .map(TaskSelectorYaml::new)
                                               .collect(Collectors.toList()));
  }

  public ScmConnector getScmConnector(ScmConnector scmConnector, String accountIdentifier, GitConfigDTO gitConfigDTO) {
    if (scmConnector instanceof GithubConnectorDTO && isGithubAppAuth((GithubConnectorDTO) scmConnector)
        && cdFeatureFlagHelper.isEnabled(accountIdentifier, CDS_GITHUB_APP_AUTHENTICATION)) {
      return scmConnector;
    } else {
      return gitConfigDTO;
    }
  }
}
