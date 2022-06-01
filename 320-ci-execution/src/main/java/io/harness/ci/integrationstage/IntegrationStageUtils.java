/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.integrationstage;

import static io.harness.beans.execution.WebhookEvent.Type.BRANCH;
import static io.harness.beans.execution.WebhookEvent.Type.PR;
import static io.harness.beans.serializer.RunTimeInputHandler.resolveOSType;
import static io.harness.common.CIExecutionConstants.GIT_URL_SUFFIX;
import static io.harness.common.CIExecutionConstants.IMAGE_PATH_SPLIT_REGEX;
import static io.harness.common.CIExecutionConstants.PATH_SEPARATOR;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.delegate.beans.connector.ConnectorType.BITBUCKET;
import static io.harness.delegate.beans.connector.ConnectorType.CODECOMMIT;
import static io.harness.delegate.beans.connector.ConnectorType.DOCKER;
import static io.harness.delegate.beans.connector.ConnectorType.GIT;
import static io.harness.delegate.beans.connector.ConnectorType.GITHUB;
import static io.harness.delegate.beans.connector.ConnectorType.GITLAB;

import static java.lang.String.format;
import static org.springframework.util.StringUtils.trimLeadingCharacter;
import static org.springframework.util.StringUtils.trimTrailingCharacter;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.execution.BranchWebhookEvent;
import io.harness.beans.execution.ExecutionSource;
import io.harness.beans.execution.ManualExecutionSource;
import io.harness.beans.execution.PRWebhookEvent;
import io.harness.beans.execution.WebhookExecutionSource;
import io.harness.beans.serializer.RunTimeInputHandler;
import io.harness.beans.stages.IntegrationStageConfig;
import io.harness.beans.yaml.extended.infrastrucutre.Infrastructure;
import io.harness.beans.yaml.extended.infrastrucutre.K8sDirectInfraYaml;
import io.harness.beans.yaml.extended.infrastrucutre.OSType;
import io.harness.delegate.beans.ci.pod.ConnectorDetails;
import io.harness.delegate.beans.connector.ConnectorType;
import io.harness.delegate.beans.connector.docker.DockerConnectorDTO;
import io.harness.delegate.beans.connector.scm.GitConnectionType;
import io.harness.delegate.beans.connector.scm.awscodecommit.AwsCodeCommitConnectorDTO;
import io.harness.delegate.beans.connector.scm.awscodecommit.AwsCodeCommitUrlType;
import io.harness.delegate.beans.connector.scm.bitbucket.BitbucketConnectorDTO;
import io.harness.delegate.beans.connector.scm.genericgitconnector.GitConfigDTO;
import io.harness.delegate.beans.connector.scm.github.GithubConnectorDTO;
import io.harness.delegate.beans.connector.scm.gitlab.GitlabConnectorDTO;
import io.harness.exception.InvalidArgumentsException;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.WingsException;
import io.harness.exception.ngexception.CIStageExecutionException;
import io.harness.k8s.model.ImageDetails;
import io.harness.ng.core.BaseNGAccess;
import io.harness.plancreator.execution.ExecutionWrapperConfig;
import io.harness.plancreator.stages.stage.StageElementConfig;
import io.harness.plancreator.steps.ParallelStepElementConfig;
import io.harness.plancreator.steps.StepElementConfig;
import io.harness.pms.contracts.plan.ExecutionTriggerInfo;
import io.harness.pms.contracts.plan.PlanCreationContextValue;
import io.harness.pms.contracts.plan.TriggerType;
import io.harness.pms.contracts.triggers.ParsedPayload;
import io.harness.pms.contracts.triggers.TriggerPayload;
import io.harness.pms.yaml.ParameterField;
import io.harness.pms.yaml.YamlNode;
import io.harness.pms.yaml.YamlUtils;
import io.harness.stateutils.buildstate.ConnectorUtils;
import io.harness.util.WebhookTriggerProcessorUtils;
import io.harness.yaml.extended.ci.codebase.Build;
import io.harness.yaml.extended.ci.codebase.BuildType;
import io.harness.yaml.extended.ci.codebase.CodeBase;
import io.harness.yaml.extended.ci.codebase.impl.BranchBuildSpec;
import io.harness.yaml.extended.ci.codebase.impl.PRBuildSpec;
import io.harness.yaml.extended.ci.codebase.impl.TagBuildSpec;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Arrays;
import lombok.experimental.UtilityClass;

@UtilityClass
@OwnedBy(HarnessTeam.CI)
public class IntegrationStageUtils {
  private static final String TAG_EXPRESSION = "<+trigger.tag>";
  private static final String BRANCH_EXPRESSION = "<+trigger.branch>";
  public static final String PR_EXPRESSION = "<+trigger.prNumber>";

  public IntegrationStageConfig getIntegrationStageConfig(StageElementConfig stageElementConfig) {
    return (IntegrationStageConfig) stageElementConfig.getStageType();
  }

  public ParallelStepElementConfig getParallelStepElementConfig(ExecutionWrapperConfig executionWrapperConfig) {
    try {
      return YamlUtils.read(executionWrapperConfig.getParallel().toString(), ParallelStepElementConfig.class);
    } catch (Exception ex) {
      throw new CIStageExecutionException("Failed to deserialize ExecutionWrapperConfig parallel node", ex);
    }
  }

  public StepElementConfig getStepElementConfig(ExecutionWrapperConfig executionWrapperConfig) {
    try {
      return YamlUtils.read(executionWrapperConfig.getStep().toString(), StepElementConfig.class);
    } catch (Exception ex) {
      throw new CIStageExecutionException("Failed to deserialize ExecutionWrapperConfig step node", ex);
    }
  }

  public CodeBase getCiCodeBase(YamlNode ciCodeBase) {
    try {
      return YamlUtils.read(ciCodeBase.toString(), CodeBase.class);
    } catch (IOException e) {
      throw new InvalidRequestException("Invalid yaml", e);
    }
  }

  public ExecutionSource buildExecutionSource(ExecutionTriggerInfo executionTriggerInfo, TriggerPayload triggerPayload,
      String identifier, ParameterField<Build> parameterFieldBuild, String connectorIdentifier,
      ConnectorUtils connectorUtils, PlanCreationContextValue planCreationContextValue, CodeBase codeBase) {
    if (!executionTriggerInfo.getIsRerun()) {
      if (executionTriggerInfo.getTriggerType() == TriggerType.MANUAL
          || executionTriggerInfo.getTriggerType() == TriggerType.SCHEDULER_CRON) {
        return handleManualExecution(parameterFieldBuild, identifier);
      } else if (executionTriggerInfo.getTriggerType() == TriggerType.WEBHOOK) {
        ParsedPayload parsedPayload = triggerPayload.getParsedPayload();
        if (treatWebhookAsManualExecutionWithContext(connectorIdentifier, connectorUtils, planCreationContextValue,
                parsedPayload, codeBase, triggerPayload.getVersion())) {
          return handleManualExecution(parameterFieldBuild, identifier);
        }

        return WebhookTriggerProcessorUtils.convertWebhookResponse(parsedPayload);
      } else if (executionTriggerInfo.getTriggerType() == TriggerType.WEBHOOK_CUSTOM) {
        return buildCustomExecutionSource(identifier, parameterFieldBuild);
      }
    } else {
      if (executionTriggerInfo.getRerunInfo().getRootTriggerType() == TriggerType.MANUAL
          || executionTriggerInfo.getRerunInfo().getRootTriggerType() == TriggerType.SCHEDULER_CRON) {
        return handleManualExecution(parameterFieldBuild, identifier);
      } else if (executionTriggerInfo.getRerunInfo().getRootTriggerType() == TriggerType.WEBHOOK) {
        ParsedPayload parsedPayload = triggerPayload.getParsedPayload();
        if (treatWebhookAsManualExecutionWithContext(connectorIdentifier, connectorUtils, planCreationContextValue,
                parsedPayload, codeBase, triggerPayload.getVersion())) {
          return handleManualExecution(parameterFieldBuild, identifier);
        }
        return WebhookTriggerProcessorUtils.convertWebhookResponse(parsedPayload);
      } else if (executionTriggerInfo.getRerunInfo().getRootTriggerType() == TriggerType.WEBHOOK_CUSTOM) {
        return buildCustomExecutionSource(identifier, parameterFieldBuild);
      }
    }

    return null;
  }

  /* In case codebase and trigger connectors are different then treat it as manual execution
   */

  public boolean treatWebhookAsManualExecution(
      ConnectorDetails connectorDetails, CodeBase codeBase, ParsedPayload parsedPayload, long version) {
    String url = getGitURLFromConnector(connectorDetails, codeBase);
    WebhookExecutionSource webhookExecutionSource = WebhookTriggerProcessorUtils.convertWebhookResponse(parsedPayload);
    Build build = RunTimeInputHandler.resolveBuild(codeBase.getBuild());
    if (build != null) {
      if (build.getType() == BuildType.PR) {
        ParameterField<String> number = ((PRBuildSpec) build.getSpec()).getNumber();
        String numberString =
            RunTimeInputHandler.resolveStringParameter("number", "Git Clone", "identifier", number, false);
        if (!numberString.equals(PR_EXPRESSION)) {
          return true;
        } else {
          if (webhookExecutionSource.getWebhookEvent().getType() == BRANCH) {
            throw new CIStageExecutionException(
                "Building PR with expression <+trigger.prNumber> for push event is not supported");
          }
        }
      }

      if (build.getType() == BuildType.BRANCH) {
        ParameterField<String> branch = ((BranchBuildSpec) build.getSpec()).getBranch();
        String branchString =
            RunTimeInputHandler.resolveStringParameter("branch", "Git Clone", "identifier", branch, false);
        if (isNotEmpty(branchString)) {
          if (!branchString.equals(BRANCH_EXPRESSION)) {
            return true;
          }
        } else {
          throw new CIStageExecutionException("Branch should not be empty for branch build type");
        }
      }

      if (build.getType() == BuildType.TAG) {
        ParameterField<String> tag = ((TagBuildSpec) build.getSpec()).getTag();
        String tagString = RunTimeInputHandler.resolveStringParameter("tag", "Git Clone", "identifier", tag, false);
        if (isNotEmpty(tagString)) {
          return true;
        } else {
          throw new CIStageExecutionException("Tag should not be empty for tag build type");
        }
      }
    }

    if (isURLSame(webhookExecutionSource, url)) {
      return false;
    } else {
      return true;
    }
  }

  public boolean isURLSame(WebhookExecutionSource webhookExecutionSource, String url) {
    if (webhookExecutionSource.getWebhookEvent().getType() == PR) {
      PRWebhookEvent prWebhookEvent = (PRWebhookEvent) webhookExecutionSource.getWebhookEvent();

      if (prWebhookEvent == null || prWebhookEvent.getRepository() == null
          || prWebhookEvent.getRepository().getHttpURL() == null) {
        return false;
      }
      if (prWebhookEvent.getRepository().getHttpURL().equals(url)
          || prWebhookEvent.getRepository().getSshURL().equals(url)) {
        return true;
      }
    } else if (webhookExecutionSource.getWebhookEvent().getType() == BRANCH) {
      BranchWebhookEvent branchWebhookEvent = (BranchWebhookEvent) webhookExecutionSource.getWebhookEvent();

      if (branchWebhookEvent == null || branchWebhookEvent.getRepository() == null
          || branchWebhookEvent.getRepository().getHttpURL() == null) {
        return false;
      }
      if (branchWebhookEvent.getRepository().getHttpURL().equals(url)
          || branchWebhookEvent.getRepository().getSshURL().equals(url)) {
        return true;
      }
    }

    return false;
  }

  private boolean treatWebhookAsManualExecutionWithContext(String connectorIdentifier, ConnectorUtils connectorUtils,
      PlanCreationContextValue planCreationContextValue, ParsedPayload parsedPayload, CodeBase codeBase, long version) {
    BaseNGAccess baseNGAccess = IntegrationStageUtils.getBaseNGAccess(planCreationContextValue.getAccountIdentifier(),
        planCreationContextValue.getOrgIdentifier(), planCreationContextValue.getProjectIdentifier());

    ConnectorDetails connectorDetails = connectorUtils.getConnectorDetails(baseNGAccess, connectorIdentifier);
    return treatWebhookAsManualExecution(connectorDetails, codeBase, parsedPayload, version);
  }

  private BaseNGAccess getBaseNGAccess(String accountIdentifier, String orgIdentifier, String projectIdentifier) {
    return BaseNGAccess.builder()
        .accountIdentifier(accountIdentifier)
        .orgIdentifier(orgIdentifier)
        .projectIdentifier(projectIdentifier)
        .build();
  }

  public String getGitURL(CodeBase ciCodebase, GitConnectionType connectionType, String url) {
    String gitUrl = retrieveGenericGitConnectorURL(ciCodebase, connectionType, url);

    if (!gitUrl.endsWith(GIT_URL_SUFFIX) && !gitUrl.contains("dev.azure.com")) {
      gitUrl += GIT_URL_SUFFIX;
    }
    return gitUrl;
  }

  public String retrieveGenericGitConnectorURL(CodeBase ciCodebase, GitConnectionType connectionType, String url) {
    String gitUrl;
    if (connectionType == GitConnectionType.REPO) {
      gitUrl = url;
    } else if (connectionType == GitConnectionType.ACCOUNT) {
      if (ciCodebase == null) {
        throw new IllegalArgumentException("CI codebase spec is not set");
      }

      if (isEmpty(ciCodebase.getRepoName().getValue())) {
        throw new IllegalArgumentException("Repo name is not set in CI codebase spec");
      }

      String repoName = ciCodebase.getRepoName().getValue();
      if (url.endsWith(PATH_SEPARATOR)) {
        gitUrl = url + repoName;
      } else {
        gitUrl = url + PATH_SEPARATOR + repoName;
      }
    } else {
      throw new InvalidArgumentsException(
          format("Invalid connection type for git connector: %s", connectionType.toString()), WingsException.USER);
    }

    return gitUrl;
  }

  public String getGitURLFromConnector(ConnectorDetails gitConnector, CodeBase ciCodebase) {
    if (gitConnector == null) {
      return null;
    }

    if (gitConnector.getConnectorType() == GITHUB) {
      GithubConnectorDTO gitConfigDTO = (GithubConnectorDTO) gitConnector.getConnectorConfig();
      return getGitURL(ciCodebase, gitConfigDTO.getConnectionType(), gitConfigDTO.getUrl());
    } else if (gitConnector.getConnectorType() == GITLAB) {
      GitlabConnectorDTO gitConfigDTO = (GitlabConnectorDTO) gitConnector.getConnectorConfig();
      return getGitURL(ciCodebase, gitConfigDTO.getConnectionType(), gitConfigDTO.getUrl());
    } else if (gitConnector.getConnectorType() == BITBUCKET) {
      BitbucketConnectorDTO gitConfigDTO = (BitbucketConnectorDTO) gitConnector.getConnectorConfig();
      return getGitURL(ciCodebase, gitConfigDTO.getConnectionType(), gitConfigDTO.getUrl());
    } else if (gitConnector.getConnectorType() == CODECOMMIT) {
      AwsCodeCommitConnectorDTO gitConfigDTO = (AwsCodeCommitConnectorDTO) gitConnector.getConnectorConfig();
      GitConnectionType gitConnectionType =
          gitConfigDTO.getUrlType() == AwsCodeCommitUrlType.REPO ? GitConnectionType.REPO : GitConnectionType.ACCOUNT;
      return getGitURL(ciCodebase, gitConnectionType, gitConfigDTO.getUrl());
    } else if (gitConnector.getConnectorType() == GIT) {
      GitConfigDTO gitConfigDTO = (GitConfigDTO) gitConnector.getConnectorConfig();
      return getGitURL(ciCodebase, gitConfigDTO.getGitConnectionType(), gitConfigDTO.getUrl());
    } else {
      throw new CIStageExecutionException("Unsupported git connector type" + gitConnector.getConnectorType());
    }
  }

  private ManualExecutionSource handleManualExecution(ParameterField<Build> parameterFieldBuild, String identifier) {
    if (parameterFieldBuild == null) {
      return ManualExecutionSource.builder().build();
    }
    Build build = RunTimeInputHandler.resolveBuild(parameterFieldBuild);
    if (build != null) {
      if (build.getType().equals(BuildType.TAG)) {
        ParameterField<String> tag = ((TagBuildSpec) build.getSpec()).getTag();
        String buildString = RunTimeInputHandler.resolveStringParameter("tag", "Git Clone", identifier, tag, false);
        return ManualExecutionSource.builder().tag(buildString).build();
      } else if (build.getType().equals(BuildType.BRANCH)) {
        ParameterField<String> branch = ((BranchBuildSpec) build.getSpec()).getBranch();
        String branchString =
            RunTimeInputHandler.resolveStringParameter("branch", "Git Clone", identifier, branch, false);
        return ManualExecutionSource.builder().branch(branchString).build();

      } else if (build.getType().equals(BuildType.PR)) {
        ParameterField<String> number = ((PRBuildSpec) build.getSpec()).getNumber();
        String numberString =
            RunTimeInputHandler.resolveStringParameter("number", "Git Clone", identifier, number, false);
        return ManualExecutionSource.builder().prNumber(numberString).build();
      }
    }

    return null;
  }

  public ImageDetails getImageInfo(String image) {
    String tag = "";
    String name = image;

    if (image.contains(IMAGE_PATH_SPLIT_REGEX)) {
      String[] subTokens = image.split(IMAGE_PATH_SPLIT_REGEX);
      if (subTokens.length > 1) {
        tag = subTokens[subTokens.length - 1];
        String[] nameparts = Arrays.copyOf(subTokens, subTokens.length - 1);
        name = String.join(IMAGE_PATH_SPLIT_REGEX, nameparts);
      }
    }

    return ImageDetails.builder().name(name).tag(tag).build();
  }

  // Returns fully qualified image name with registryURL prepended in the image name.
  public String getFullyQualifiedImageName(String imageName, ConnectorDetails connectorDetails) {
    if (connectorDetails == null) {
      return imageName;
    }

    ConnectorType connectorType = connectorDetails.getConnectorType();
    if (connectorType != DOCKER) {
      return imageName;
    }

    DockerConnectorDTO dockerConnectorDTO = (DockerConnectorDTO) connectorDetails.getConnectorConfig();
    String dockerRegistryUrl = dockerConnectorDTO.getDockerRegistryUrl();
    return getImageWithRegistryPath(imageName, dockerRegistryUrl, connectorDetails.getIdentifier());
  }

  private String getImageWithRegistryPath(String imageName, String registryUrl, String connectorId) {
    URL url = null;
    try {
      url = new URL(registryUrl);
    } catch (MalformedURLException e) {
      throw new CIStageExecutionException(
          format("Malformed registryUrl %s in docker connector id: %s", registryUrl, connectorId));
    }

    String registryHostName = url.getHost();
    if (url.getPort() != -1) {
      registryHostName = url.getHost() + ":" + url.getPort();
    }

    if (imageName.contains(registryHostName) || registryHostName.equals("index.docker.io")
        || registryHostName.equals("registry.hub.docker.com")) {
      return imageName;
    }

    String prefixRegistryPath = registryHostName + url.getPath();
    return trimTrailingCharacter(prefixRegistryPath, '/') + '/' + trimLeadingCharacter(imageName, '/');
  }

  private ManualExecutionSource buildCustomExecutionSource(
      String identifier, ParameterField<Build> parameterFieldBuild) {
    if (parameterFieldBuild == null) {
      return ManualExecutionSource.builder().build();
    }
    Build build = RunTimeInputHandler.resolveBuild(parameterFieldBuild);
    if (build != null) {
      if (build.getType().equals(BuildType.TAG)) {
        ParameterField<String> tag = ((TagBuildSpec) build.getSpec()).getTag();
        String buildString = RunTimeInputHandler.resolveStringParameter("tag", "Git Clone", identifier, tag, false);
        return ManualExecutionSource.builder().tag(buildString).build();
      } else if (build.getType().equals(BuildType.BRANCH)) {
        ParameterField<String> branch = ((BranchBuildSpec) build.getSpec()).getBranch();
        String branchString =
            RunTimeInputHandler.resolveStringParameter("branch", "Git Clone", identifier, branch, false);
        return ManualExecutionSource.builder().branch(branchString).build();

      } else if (build.getType().equals(BuildType.PR)) {
        ParameterField<String> number = ((PRBuildSpec) build.getSpec()).getNumber();
        String numberString =
            RunTimeInputHandler.resolveStringParameter("number", "Git Clone", identifier, number, false);
        return ManualExecutionSource.builder().prNumber(numberString).build();
      }
    }

    return null;
  }

  // Returns os for kubernetes infrastructure
  public OSType getK8OS(Infrastructure infrastructure) {
    if (infrastructure == null) {
      throw new CIStageExecutionException("Input infrastructure can not be empty");
    }

    if (infrastructure.getType() != Infrastructure.Type.KUBERNETES_DIRECT) {
      return OSType.Linux;
    }

    if (((K8sDirectInfraYaml) infrastructure).getSpec() == null) {
      throw new CIStageExecutionException("Input infrastructure can not be empty");
    }

    K8sDirectInfraYaml k8sDirectInfraYaml = (K8sDirectInfraYaml) infrastructure;
    return resolveOSType(k8sDirectInfraYaml.getSpec().getOs());
  }
}
