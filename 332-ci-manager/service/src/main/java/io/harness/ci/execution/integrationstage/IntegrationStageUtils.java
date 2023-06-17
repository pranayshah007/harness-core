/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.integrationstage;

import static io.harness.beans.execution.WebhookEvent.Type.BRANCH;
import static io.harness.beans.execution.WebhookEvent.Type.PR;
import static io.harness.beans.execution.WebhookEvent.Type.RELEASE;
import static io.harness.beans.serializer.RunTimeInputHandler.resolveOSType;
import static io.harness.beans.serializer.RunTimeInputHandler.resolveStringParameter;
import static io.harness.beans.yaml.extended.infrastrucutre.Infrastructure.Type.HOSTED_VM;
import static io.harness.beans.yaml.extended.infrastrucutre.Infrastructure.Type.KUBERNETES_DIRECT;
import static io.harness.beans.yaml.extended.infrastrucutre.Infrastructure.Type.KUBERNETES_HOSTED;
import static io.harness.beans.yaml.extended.infrastrucutre.Infrastructure.Type.VM;
import static io.harness.ci.commonconstants.CIExecutionConstants.DEFAULT_BUILD_MULTIPLIER;
import static io.harness.ci.commonconstants.CIExecutionConstants.IMAGE_PATH_SPLIT_REGEX;
import static io.harness.ci.commonconstants.CIExecutionConstants.MACOS_BUILD_MULTIPLIER;
import static io.harness.ci.commonconstants.CIExecutionConstants.WINDOWS_BUILD_MULTIPLIER;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.delegate.beans.connector.ConnectorType.AZURE_REPO;
import static io.harness.delegate.beans.connector.ConnectorType.BITBUCKET;
import static io.harness.delegate.beans.connector.ConnectorType.CODECOMMIT;
import static io.harness.delegate.beans.connector.ConnectorType.DOCKER;
import static io.harness.delegate.beans.connector.ConnectorType.GIT;
import static io.harness.delegate.beans.connector.ConnectorType.GITHUB;
import static io.harness.delegate.beans.connector.ConnectorType.GITLAB;
import static io.harness.delegate.beans.connector.ConnectorType.HARNESS;
import static io.harness.delegate.beans.connector.scm.adapter.AzureRepoToGitMapper.mapToGitConnectionType;
import static io.harness.pms.yaml.YAMLFieldNameConstants.CI;
import static io.harness.pms.yaml.YAMLFieldNameConstants.CI_CODE_BASE;
import static io.harness.pms.yaml.YAMLFieldNameConstants.PROPERTIES;

import static java.lang.String.format;
import static org.springframework.util.StringUtils.trimLeadingCharacter;
import static org.springframework.util.StringUtils.trimTrailingCharacter;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.dependencies.CIServiceInfo;
import io.harness.beans.dependencies.DependencyElement;
import io.harness.beans.execution.BranchWebhookEvent;
import io.harness.beans.execution.ExecutionSource;
import io.harness.beans.execution.ManualExecutionSource;
import io.harness.beans.execution.PRWebhookEvent;
import io.harness.beans.execution.ReleaseWebhookEvent;
import io.harness.beans.execution.WebhookEvent;
import io.harness.beans.execution.WebhookExecutionSource;
import io.harness.beans.execution.license.CILicenseService;
import io.harness.beans.plugin.compatible.PluginCompatibleStep;
import io.harness.beans.serializer.RunTimeInputHandler;
import io.harness.beans.stages.IntegrationStageNode;
import io.harness.beans.steps.CIAbstractStepNode;
import io.harness.beans.steps.CIStepInfo;
import io.harness.beans.steps.stepinfo.BackgroundStepInfo;
import io.harness.beans.steps.stepinfo.InitializeStepInfo;
import io.harness.beans.steps.stepinfo.PluginStepInfo;
import io.harness.beans.steps.stepinfo.RunStepInfo;
import io.harness.beans.steps.stepinfo.RunTestsStepInfo;
import io.harness.beans.yaml.extended.infrastrucutre.DockerInfraYaml;
import io.harness.beans.yaml.extended.infrastrucutre.HostedVmInfraYaml;
import io.harness.beans.yaml.extended.infrastrucutre.Infrastructure;
import io.harness.beans.yaml.extended.infrastrucutre.K8sDirectInfraYaml;
import io.harness.beans.yaml.extended.infrastrucutre.OSType;
import io.harness.beans.yaml.extended.infrastrucutre.VmInfraYaml;
import io.harness.beans.yaml.extended.infrastrucutre.VmPoolYaml;
import io.harness.beans.yaml.extended.platform.ArchType;
import io.harness.ci.buildstate.CodebaseUtils;
import io.harness.ci.buildstate.ConnectorUtils;
import io.harness.ci.buildstate.InfraInfoUtils;
import io.harness.ci.pipeline.executions.beans.CIImageDetails;
import io.harness.ci.pipeline.executions.beans.CIInfraDetails;
import io.harness.ci.pipeline.executions.beans.CIScmDetails;
import io.harness.ci.pipeline.executions.beans.TIBuildDetails;
import io.harness.ci.states.RunStep;
import io.harness.ci.states.RunTestsStep;
import io.harness.ci.utils.WebhookTriggerProcessorUtils;
import io.harness.cimanager.stages.IntegrationStageConfig;
import io.harness.delegate.beans.ci.pod.ConnectorDetails;
import io.harness.delegate.beans.connector.ConnectorType;
import io.harness.delegate.beans.connector.docker.DockerConnectorDTO;
import io.harness.delegate.beans.connector.scm.GitConnectionType;
import io.harness.delegate.beans.connector.scm.awscodecommit.AwsCodeCommitConnectorDTO;
import io.harness.delegate.beans.connector.scm.awscodecommit.AwsCodeCommitUrlType;
import io.harness.delegate.beans.connector.scm.azurerepo.AzureRepoConnectorDTO;
import io.harness.delegate.beans.connector.scm.bitbucket.BitbucketConnectorDTO;
import io.harness.delegate.beans.connector.scm.genericgitconnector.GitConfigDTO;
import io.harness.delegate.beans.connector.scm.github.GithubConnectorDTO;
import io.harness.delegate.beans.connector.scm.gitlab.GitlabConnectorDTO;
import io.harness.delegate.beans.connector.scm.harness.HarnessConnectorDTO;
import io.harness.delegate.task.citasks.cik8handler.params.CIConstants;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.ngexception.CIStageExecutionException;
import io.harness.git.GitClientHelper;
import io.harness.jackson.JsonNodeUtils;
import io.harness.k8s.model.ImageDetails;
import io.harness.licensing.Edition;
import io.harness.licensing.beans.summary.LicensesWithSummaryDTO;
import io.harness.ng.core.BaseNGAccess;
import io.harness.ng.core.NGAccess;
import io.harness.plancreator.execution.ExecutionWrapperConfig;
import io.harness.plancreator.steps.ParallelStepElementConfig;
import io.harness.plancreator.steps.StepGroupElementConfig;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.plan.ExecutionTriggerInfo;
import io.harness.pms.contracts.plan.TriggerType;
import io.harness.pms.contracts.triggers.ParsedPayload;
import io.harness.pms.contracts.triggers.TriggerPayload;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationContext;
import io.harness.pms.yaml.ParameterField;
import io.harness.pms.yaml.YamlNode;
import io.harness.pms.yaml.YamlUtils;
import io.harness.utils.CiIntegrationStageUtils;
import io.harness.yaml.extended.ci.codebase.Build;
import io.harness.yaml.extended.ci.codebase.BuildType;
import io.harness.yaml.extended.ci.codebase.CodeBase;
import io.harness.yaml.extended.ci.codebase.impl.BranchBuildSpec;
import io.harness.yaml.extended.ci.codebase.impl.PRBuildSpec;
import io.harness.yaml.extended.ci.codebase.impl.TagBuildSpec;
import io.harness.yaml.utils.JsonPipelineUtils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.fabric8.utils.Strings;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

@OwnedBy(HarnessTeam.CI)
@Slf4j
public class IntegrationStageUtils {
  private static final String TAG_EXPRESSION = "<+trigger.tag>";
  private static final String BRANCH_EXPRESSION = "<+trigger.branch>";
  public static final String PR_EXPRESSION = "<+trigger.prNumber>";

  private static final String HARNESS_HOSTED = "Harness Hosted";
  private static final String SELF_HOSTED = "Self Hosted";

  public static IntegrationStageConfig getIntegrationStageConfig(IntegrationStageNode stageNode) {
    return stageNode.getIntegrationStageConfig();
  }

  public static ParallelStepElementConfig getParallelStepElementConfig(ExecutionWrapperConfig executionWrapperConfig) {
    try {
      return YamlUtils.read(executionWrapperConfig.getParallel().toString(), ParallelStepElementConfig.class);
    } catch (Exception ex) {
      throw new CIStageExecutionException("Failed to deserialize ExecutionWrapperConfig parallel node", ex);
    }
  }

  public static CIAbstractStepNode getStepNode(ExecutionWrapperConfig executionWrapperConfig) {
    try {
      return YamlUtils.read(executionWrapperConfig.getStep().toString(), CIAbstractStepNode.class);
    } catch (Exception ex) {
      String errorMessage = "Failed to deserialize ExecutionWrapperConfig step node";
      Throwable throwable = ex.getCause();
      if (throwable != null && Strings.isNotBlank(throwable.getMessage())) {
        errorMessage = throwable.getMessage();
      }
      throw new CIStageExecutionException(errorMessage, ex);
    }
  }

  public static StepGroupElementConfig getStepGroupElementConfig(ExecutionWrapperConfig executionWrapperConfig) {
    try {
      return YamlUtils.read(executionWrapperConfig.getStepGroup().toString(), StepGroupElementConfig.class);
    } catch (Exception ex) {
      throw new CIStageExecutionException("Failed to deserialize ExecutionWrapperConfig step node", ex);
    }
  }

  public static CodeBase getCiCodeBase(YamlNode ciCodeBase) {
    try {
      return YamlUtils.read(ciCodeBase.toString(), CodeBase.class);
    } catch (IOException e) {
      throw new InvalidRequestException("Invalid yaml", e);
    }
  }

  public static ExecutionSource buildExecutionSource(ExecutionTriggerInfo executionTriggerInfo,
      TriggerPayload triggerPayload, String identifier, ParameterField<Build> parameterFieldBuild,
      String connectorIdentifier, ConnectorUtils connectorUtils, PlanCreationContext ctx, CodeBase codeBase) {
    if (!executionTriggerInfo.getIsRerun()) {
      if (executionTriggerInfo.getTriggerType() == TriggerType.MANUAL
          || executionTriggerInfo.getTriggerType() == TriggerType.SCHEDULER_CRON) {
        return handleManualExecution(parameterFieldBuild, identifier);
      } else if (executionTriggerInfo.getTriggerType() == TriggerType.WEBHOOK) {
        ParsedPayload parsedPayload = triggerPayload.getParsedPayload();
        if (treatWebhookAsManualExecutionWithContext(
                connectorIdentifier, connectorUtils, ctx, parsedPayload, codeBase, triggerPayload.getVersion())) {
          return handleManualExecution(parameterFieldBuild, identifier);
        }

        return WebhookTriggerProcessorUtils.convertWebhookResponse(parsedPayload);
      } else if (executionTriggerInfo.getTriggerType() == TriggerType.WEBHOOK_CUSTOM) {
        return buildCustomExecutionSource(identifier, parameterFieldBuild);
      } else {
        throw new InvalidRequestException(
            "CI stage cannot be triggered by trigger of type: " + executionTriggerInfo.getTriggerType());
      }
    } else {
      if (executionTriggerInfo.getRerunInfo().getRootTriggerType() == TriggerType.MANUAL
          || executionTriggerInfo.getRerunInfo().getRootTriggerType() == TriggerType.SCHEDULER_CRON) {
        return handleManualExecution(parameterFieldBuild, identifier);
      } else if (executionTriggerInfo.getRerunInfo().getRootTriggerType() == TriggerType.WEBHOOK) {
        ParsedPayload parsedPayload = triggerPayload.getParsedPayload();
        if (treatWebhookAsManualExecutionWithContext(
                connectorIdentifier, connectorUtils, ctx, parsedPayload, codeBase, triggerPayload.getVersion())) {
          return handleManualExecution(parameterFieldBuild, identifier);
        }
        return WebhookTriggerProcessorUtils.convertWebhookResponse(parsedPayload);
      } else if (executionTriggerInfo.getRerunInfo().getRootTriggerType() == TriggerType.WEBHOOK_CUSTOM) {
        return buildCustomExecutionSource(identifier, parameterFieldBuild);
      } else {
        throw new InvalidRequestException("CI stage cannot be triggered by trigger of type: "
            + executionTriggerInfo.getRerunInfo().getRootTriggerType());
      }
    }
  }

  public static ExecutionSource buildExecutionSourceV2(Ambiance ambiance, ExecutionTriggerInfo executionTriggerInfo,
      TriggerPayload triggerPayload, String identifier, ParameterField<Build> parameterFieldBuild,
      String connectorIdentifier, ConnectorUtils connectorUtils, CodeBase codeBase, boolean cloneManually) {
    if (!executionTriggerInfo.getIsRerun()) {
      if (executionTriggerInfo.getTriggerType() == TriggerType.MANUAL
          || executionTriggerInfo.getTriggerType() == TriggerType.SCHEDULER_CRON) {
        return handleManualExecution(parameterFieldBuild, identifier);
      } else if (executionTriggerInfo.getTriggerType() == TriggerType.WEBHOOK) {
        ParsedPayload parsedPayload = triggerPayload.getParsedPayload();
        if (cloneManually
            || treatWebhookAsManualExecutionWithContextV2(
                ambiance, connectorIdentifier, connectorUtils, parsedPayload, codeBase, triggerPayload.getVersion())) {
          return handleManualExecution(parameterFieldBuild, identifier);
        }

        return WebhookTriggerProcessorUtils.convertWebhookResponse(parsedPayload);
      } else if (executionTriggerInfo.getTriggerType() == TriggerType.WEBHOOK_CUSTOM) {
        return buildCustomExecutionSource(identifier, parameterFieldBuild);
      } else {
        throw new InvalidRequestException(
            "CI stage cannot be triggered by trigger of type: " + executionTriggerInfo.getTriggerType());
      }
    } else {
      if (executionTriggerInfo.getRerunInfo().getRootTriggerType() == TriggerType.MANUAL
          || executionTriggerInfo.getRerunInfo().getRootTriggerType() == TriggerType.SCHEDULER_CRON) {
        return handleManualExecution(parameterFieldBuild, identifier);
      } else if (executionTriggerInfo.getRerunInfo().getRootTriggerType() == TriggerType.WEBHOOK) {
        ParsedPayload parsedPayload = triggerPayload.getParsedPayload();
        if (cloneManually
            || treatWebhookAsManualExecutionWithContextV2(
                ambiance, connectorIdentifier, connectorUtils, parsedPayload, codeBase, triggerPayload.getVersion())) {
          return handleManualExecution(parameterFieldBuild, identifier);
        }
        return WebhookTriggerProcessorUtils.convertWebhookResponse(parsedPayload);
      } else if (executionTriggerInfo.getRerunInfo().getRootTriggerType() == TriggerType.WEBHOOK_CUSTOM) {
        return buildCustomExecutionSource(identifier, parameterFieldBuild);
      } else {
        throw new InvalidRequestException("CI stage cannot be triggered by trigger of type: "
            + executionTriggerInfo.getRerunInfo().getRootTriggerType());
      }
    }
  }

  /* In case codebase and trigger connectors are different then treat it as manual execution
   */

  public static boolean treatWebhookAsManualExecution(
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

  public static boolean treatWebhookAsManualExecutionV2(
      ConnectorDetails connectorDetails, CodeBase codeBase, ParsedPayload parsedPayload, long version) {
    String url = getGitURLFromConnector(connectorDetails, codeBase);
    WebhookExecutionSource webhookExecutionSource = WebhookTriggerProcessorUtils.convertWebhookResponse(parsedPayload);

    // if url is not same then always treat as manual execution
    if (!isURLSame(webhookExecutionSource, url)) {
      return true;
    }

    Build build = RunTimeInputHandler.resolveBuild(codeBase.getBuild());
    if (build != null) {
      if (build.getType() == BuildType.PR && webhookExecutionSource.getWebhookEvent().getType() == BRANCH) {
        throw new CIStageExecutionException(
            "Building PR with expression <+trigger.prNumber> for push event is not supported");
      }

      if (build.getType() == BuildType.BRANCH) {
        ParameterField<String> branch = ((BranchBuildSpec) build.getSpec()).getBranch();
        String branchString =
            RunTimeInputHandler.resolveStringParameter("branch", "Git Clone", "identifier", branch, false);
        if (isEmpty(branchString)) {
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

    return false;
  }

  public static boolean isURLSame(WebhookExecutionSource webhookExecutionSource, String url) {
    if (webhookExecutionSource.getWebhookEvent().getType() == PR) {
      PRWebhookEvent prWebhookEvent = (PRWebhookEvent) webhookExecutionSource.getWebhookEvent();

      if (prWebhookEvent == null || prWebhookEvent.getRepository() == null
          || prWebhookEvent.getRepository().getHttpURL() == null) {
        return false;
      }
      if (prWebhookEvent.getRepository().getHttpURL().equalsIgnoreCase(url)
          || prWebhookEvent.getRepository().getSshURL().equalsIgnoreCase(url)) {
        return true;
      }
    } else if (webhookExecutionSource.getWebhookEvent().getType() == BRANCH) {
      BranchWebhookEvent branchWebhookEvent = (BranchWebhookEvent) webhookExecutionSource.getWebhookEvent();

      if (branchWebhookEvent == null || branchWebhookEvent.getRepository() == null
          || branchWebhookEvent.getRepository().getHttpURL() == null) {
        return false;
      }
      if (branchWebhookEvent.getRepository().getHttpURL().equalsIgnoreCase(url)
          || branchWebhookEvent.getRepository().getSshURL().equalsIgnoreCase(url)) {
        return true;
      }
    } else if (webhookExecutionSource.getWebhookEvent().getType() == RELEASE) {
      ReleaseWebhookEvent releaseWebhookEvent = (ReleaseWebhookEvent) webhookExecutionSource.getWebhookEvent();

      if (releaseWebhookEvent == null || releaseWebhookEvent.getRepository() == null
          || releaseWebhookEvent.getRepository().getHttpURL() == null) {
        return false;
      }
      if (releaseWebhookEvent.getRepository().getHttpURL().equalsIgnoreCase(url)
          || releaseWebhookEvent.getRepository().getSshURL().equalsIgnoreCase(url)) {
        return true;
      }
    }

    return false;
  }

  private static boolean treatWebhookAsManualExecutionWithContext(String connectorIdentifier,
      ConnectorUtils connectorUtils, PlanCreationContext ctx, ParsedPayload parsedPayload, CodeBase codeBase,
      long version) {
    BaseNGAccess baseNGAccess = IntegrationStageUtils.getBaseNGAccess(
        ctx.getAccountIdentifier(), ctx.getOrgIdentifier(), ctx.getProjectIdentifier());

    ConnectorDetails connectorDetails = connectorUtils.getConnectorDetails(baseNGAccess, connectorIdentifier, true);
    return treatWebhookAsManualExecution(connectorDetails, codeBase, parsedPayload, version);
  }

  private static boolean treatWebhookAsManualExecutionWithContextV2(Ambiance ambiance, String connectorIdentifier,
      ConnectorUtils connectorUtils, ParsedPayload parsedPayload, CodeBase codeBase, long version) {
    NGAccess ngAccess = AmbianceUtils.getNgAccess(ambiance);
    ConnectorDetails connectorDetails = connectorUtils.getConnectorDetails(ngAccess, connectorIdentifier, true);
    return treatWebhookAsManualExecutionV2(connectorDetails, codeBase, parsedPayload, version);
  }

  public static BaseNGAccess getBaseNGAccess(String accountIdentifier, String orgIdentifier, String projectIdentifier) {
    return BaseNGAccess.builder()
        .accountIdentifier(accountIdentifier)
        .orgIdentifier(orgIdentifier)
        .projectIdentifier(projectIdentifier)
        .build();
  }

  public static String getGitURL(CodeBase ciCodebase, GitConnectionType connectionType, String url) {
    if (ciCodebase == null) {
      throw new IllegalArgumentException("CI codebase spec is not set");
    }
    String repoName = ciCodebase.getRepoName().getValue();
    return getGitURL(repoName, connectionType, url);
  }

  public static String getGitURL(String repoName, GitConnectionType connectionType, String url) {
    return CiIntegrationStageUtils.getGitURL(repoName, connectionType, url);
  }

  public static String getGitURLFromConnector(ConnectorDetails gitConnector, CodeBase ciCodebase) {
    if (gitConnector == null) {
      return null;
    }

    String url = "";
    if (gitConnector.getConnectorType() == GITHUB) {
      GithubConnectorDTO gitConfigDTO = (GithubConnectorDTO) gitConnector.getConnectorConfig();
      url = getGitURL(ciCodebase, gitConfigDTO.getConnectionType(), gitConfigDTO.getUrl());
    } else if (gitConnector.getConnectorType() == AZURE_REPO) {
      AzureRepoConnectorDTO gitConfigDTO = (AzureRepoConnectorDTO) gitConnector.getConnectorConfig();
      GitConnectionType gitConnectionType = mapToGitConnectionType(gitConfigDTO.getConnectionType());
      url = getGitURL(ciCodebase, gitConnectionType, gitConfigDTO.getUrl());
    } else if (gitConnector.getConnectorType() == GITLAB) {
      GitlabConnectorDTO gitConfigDTO = (GitlabConnectorDTO) gitConnector.getConnectorConfig();
      url = getGitURL(ciCodebase, gitConfigDTO.getConnectionType(), gitConfigDTO.getUrl());
    } else if (gitConnector.getConnectorType() == BITBUCKET) {
      BitbucketConnectorDTO gitConfigDTO = (BitbucketConnectorDTO) gitConnector.getConnectorConfig();
      url = getGitURL(ciCodebase, gitConfigDTO.getConnectionType(), gitConfigDTO.getUrl());
    } else if (gitConnector.getConnectorType() == CODECOMMIT) {
      AwsCodeCommitConnectorDTO gitConfigDTO = (AwsCodeCommitConnectorDTO) gitConnector.getConnectorConfig();
      GitConnectionType gitConnectionType =
          gitConfigDTO.getUrlType() == AwsCodeCommitUrlType.REPO ? GitConnectionType.REPO : GitConnectionType.ACCOUNT;
      url = getGitURL(ciCodebase, gitConnectionType, gitConfigDTO.getUrl());
    } else if (gitConnector.getConnectorType() == GIT) {
      GitConfigDTO gitConfigDTO = (GitConfigDTO) gitConnector.getConnectorConfig();
      url = getGitURL(ciCodebase, gitConfigDTO.getGitConnectionType(), gitConfigDTO.getUrl());
    } else if (gitConnector.getConnectorType() == HARNESS) {
      HarnessConnectorDTO gitConfigDTO = (HarnessConnectorDTO) gitConnector.getConnectorConfig();
      url = CodebaseUtils.getCompleteHarnessUrl(gitConfigDTO.getUrl(), gitConnector.getOrgIdentifier(),
          gitConnector.getProjectIdentifier(), ciCodebase.getRepoName().getValue());
    } else {
      throw new CIStageExecutionException("Unsupported git connector type" + gitConnector.getConnectorType());
    }

    url = GitClientHelper.convertToHttps(url);
    return url;
  }

  private static ManualExecutionSource handleManualExecution(
      ParameterField<Build> parameterFieldBuild, String identifier) {
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

    return ManualExecutionSource.builder().build();
  }

  public static List<CIAbstractStepNode> getAllSteps(List<ExecutionWrapperConfig> executionWrapperConfigs) {
    List<CIAbstractStepNode> stepNodes = new ArrayList<>();

    if (executionWrapperConfigs == null) {
      return stepNodes;
    }

    for (ExecutionWrapperConfig executionWrapper : executionWrapperConfigs) {
      if (executionWrapper == null) {
        continue;
      }

      if (executionWrapper.getStep() != null && !executionWrapper.getStep().isNull()) {
        stepNodes.add(getStepNode(executionWrapper));
      } else if (executionWrapper.getParallel() != null && !executionWrapper.getParallel().isNull()) {
        ParallelStepElementConfig parallelStepElementConfig = getParallelStepElementConfig(executionWrapper);
        List<CIAbstractStepNode> fromParallel = getAllSteps(parallelStepElementConfig.getSections());
        stepNodes.addAll(fromParallel);
      } else if (executionWrapper.getStepGroup() != null && !executionWrapper.getStepGroup().isNull()) {
        StepGroupElementConfig stepGroupElementConfig = getStepGroupElementConfig(executionWrapper);
        List<CIAbstractStepNode> fromStepGroup = getAllSteps(stepGroupElementConfig.getSteps());
        stepNodes.addAll(fromStepGroup);
      }
    }
    return stepNodes;
  }

  public static void injectLoopEnvVariables(ExecutionWrapperConfig config) {
    if (config == null) {
      return;
    }
    // Read the envVariables from config, and inject Looping
    // releated environment variables.
    if (config.getStep() != null && !config.getStep().isNull()) {
      JsonNode stepNode = config.getStep();
      ObjectNode spec = (ObjectNode) stepNode.get("spec");
      if (spec == null) {
        return;
      }
      ObjectNode envVariables = (ObjectNode) spec.get("envVariables");
      HashMap<String, String> envMap = new HashMap<>();
      envMap.put("HARNESS_STAGE_INDEX", "<+stage.iteration>");
      envMap.put("HARNESS_STAGE_TOTAL", "<+stage.iterations>");
      envMap.put("HARNESS_STEP_INDEX", "<+step.iteration>");
      envMap.put("HARNESS_STEP_TOTAL", "<+step.iterations>");
      envMap.put("HARNESS_NODE_INDEX", "<+strategy.iteration>");
      envMap.put("HARNESS_NODE_TOTAL", "<+strategy.iterations>");
      if (envVariables == null) {
        ObjectMapper mapper = new ObjectMapper();
        JsonNode env = mapper.valueToTree(envMap);
        spec.set("envVariables", env);
      } else {
        JsonNodeUtils.upsertPropertiesInJsonNode((ObjectNode) envVariables, envMap);
      }
    } else if (config.getParallel() != null && !config.getParallel().isNull()) {
      ParallelStepElementConfig parallelStepElementConfig = getParallelStepElementConfig(config);
      for (ExecutionWrapperConfig section : parallelStepElementConfig.getSections()) {
        injectLoopEnvVariables(section);
      }
      JsonNode parallelNode = JsonPipelineUtils.asTree(parallelStepElementConfig);
      ArrayNode arrayNode = JsonPipelineUtils.getMapper().createArrayNode();
      for (ExecutionWrapperConfig section : parallelStepElementConfig.getSections()) {
        arrayNode.add(JsonPipelineUtils.asTree(section));
      }
      config.setParallel(arrayNode);
    } else if (config.getStepGroup() != null && !config.getStepGroup().isNull()) {
      StepGroupElementConfig stepGroupElementConfig = getStepGroupElementConfig(config);
      for (ExecutionWrapperConfig step : stepGroupElementConfig.getSteps()) {
        injectLoopEnvVariables(step);
      }
      JsonNode stepGroupNode = JsonPipelineUtils.asTree(stepGroupElementConfig);
      config.setStepGroup(stepGroupNode);
    }
  }

  public static List<TIBuildDetails> getTiBuildDetails(InitializeStepInfo initializeStepInfo) {
    List<TIBuildDetails> tiBuildDetailsList = new ArrayList<>();
    List<CIAbstractStepNode> stepNodes = getAllSteps(initializeStepInfo.getExecutionElementConfig().getSteps());

    for (CIAbstractStepNode stepNode : stepNodes) {
      if (!(stepNode.getStepSpecType() instanceof CIStepInfo)) {
        continue;
      }
      CIStepInfo ciStepInfo = (CIStepInfo) stepNode.getStepSpecType();
      if (ciStepInfo.getStepType() == RunTestsStep.STEP_TYPE) {
        RunTestsStepInfo runTestsStepInfo = (RunTestsStepInfo) ciStepInfo;
        TIBuildDetails tiBuildDetails = TIBuildDetails.builder()
                                            .buildTool(runTestsStepInfo.getBuildTool().getValue().getYamlName())
                                            .language(runTestsStepInfo.getLanguage().getValue().getYamlName())
                                            .build();
        tiBuildDetailsList.add(tiBuildDetails);
      }
    }
    return tiBuildDetailsList;
  }

  public static List<CIImageDetails> getCiImageDetails(InitializeStepInfo initializeStepInfo) {
    List<CIImageDetails> imageDetailsList = new ArrayList<>();
    List<CIAbstractStepNode> stepNodes = getAllSteps(initializeStepInfo.getExecutionElementConfig().getSteps());
    CIImageDetails imageDetails;

    for (CIAbstractStepNode stepNode : stepNodes) {
      if (!(stepNode.getStepSpecType() instanceof CIStepInfo)) {
        continue;
      }
      CIStepInfo ciStepInfo = (CIStepInfo) stepNode.getStepSpecType();
      if (ciStepInfo.getStepType() == RunStep.STEP_TYPE) {
        imageDetails = getCiImageInfo(((RunStepInfo) ciStepInfo).getImage().getValue());
      } else if (ciStepInfo.getStepType() == RunTestsStep.STEP_TYPE) {
        imageDetails = getCiImageInfo(((RunTestsStepInfo) ciStepInfo).getImage().getValue());
      } else if (ciStepInfo.getStepType() == PluginStepInfo.STEP_TYPE) {
        imageDetails = getCiImageInfo(((PluginStepInfo) ciStepInfo).getImage().getValue());
      } else {
        continue;
      }

      if (imageDetails != null) {
        imageDetailsList.add(imageDetails);
      }
    }
    return imageDetailsList;
  }

  public static CIImageDetails getCiImageInfo(String image) {
    if (isEmpty(image)) {
      return null;
    }

    ImageDetails imagedetails = getImageInfo(image);
    return CIImageDetails.builder().imageName(imagedetails.getName()).imageTag(imagedetails.getTag()).build();
  }

  public static ImageDetails getImageInfo(String image) {
    String tag = "";
    String name = image;

    if (isNotEmpty(image)) {
      if (image.contains(IMAGE_PATH_SPLIT_REGEX)) {
        String[] subTokens = image.split(IMAGE_PATH_SPLIT_REGEX);
        if (subTokens.length > 1) {
          tag = subTokens[subTokens.length - 1];
          String[] nameparts = Arrays.copyOf(subTokens, subTokens.length - 1);
          name = String.join(IMAGE_PATH_SPLIT_REGEX, nameparts);
        }
      }
    } else {
      throw new CIStageExecutionException(format("ConnectorRef and Image should not be empty"));
    }

    return ImageDetails.builder().name(name).tag(tag).build();
  }

  // Returns fully qualified image name with registryURL prepended in the image name.
  public static String getFullyQualifiedImageName(String imageName, ConnectorDetails connectorDetails) {
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

  private static String getImageWithRegistryPath(String imageName, String registryUrl, String connectorId) {
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

  private static ManualExecutionSource buildCustomExecutionSource(
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
  public static OSType getK8OS(Infrastructure infrastructure) {
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

  public static OSType getBuildType(Infrastructure infra) {
    if (infra instanceof VmInfraYaml) {
      VmInfraYaml infrastructure = (VmInfraYaml) infra;
      return RunTimeInputHandler.resolveOSType(((VmPoolYaml) infrastructure.getSpec()).getSpec().getOs());
    } else if (infra instanceof DockerInfraYaml) {
      DockerInfraYaml infrastructure = (DockerInfraYaml) infra;
      return RunTimeInputHandler.resolveOSType(infrastructure.getSpec().getPlatform().getValue().getOs());
    } else if (infra instanceof K8sDirectInfraYaml) {
      K8sDirectInfraYaml infrastructure = (K8sDirectInfraYaml) infra;
      return RunTimeInputHandler.resolveOSType(infrastructure.getSpec().getOs());
    } else if (infra instanceof HostedVmInfraYaml) {
      HostedVmInfraYaml infrastructure = (HostedVmInfraYaml) infra;
      return RunTimeInputHandler.resolveOSType(infrastructure.getSpec().getPlatform().getValue().getOs());
    } else {
      throw new CIStageExecutionException("unexpected type of infra received");
    }
  }

  public static ArrayList<String> populateConnectorIdentifiers(List<ExecutionWrapperConfig> wrappers) {
    ArrayList<String> connectorIdentifiers = new ArrayList<>();
    for (ExecutionWrapperConfig executionWrapper : wrappers) {
      if (executionWrapper.getStep() != null && !executionWrapper.getStep().isNull()) {
        CIAbstractStepNode stepNode = IntegrationStageUtils.getStepNode(executionWrapper);
        String identifier = getConnectorIdentifier(stepNode);
        if (identifier != null) {
          connectorIdentifiers.add(identifier);
        }
      } else if (executionWrapper.getParallel() != null && !executionWrapper.getParallel().isNull()) {
        ParallelStepElementConfig parallelStepElementConfig =
            IntegrationStageUtils.getParallelStepElementConfig(executionWrapper);
        if (isNotEmpty(parallelStepElementConfig.getSections())) {
          ArrayList<String> connectorIdentifiersForParallel =
              populateConnectorIdentifiers(parallelStepElementConfig.getSections());
          if (connectorIdentifiersForParallel != null && connectorIdentifiersForParallel.size() > 0) {
            connectorIdentifiers.addAll(connectorIdentifiersForParallel);
          }
        }
      } else {
        StepGroupElementConfig stepGroupElementConfig =
            IntegrationStageUtils.getStepGroupElementConfig(executionWrapper);
        if (isNotEmpty(stepGroupElementConfig.getSteps())) {
          ArrayList<String> connectorIdentifiersForStepGroup =
              populateConnectorIdentifiers(stepGroupElementConfig.getSteps());
          if (connectorIdentifiersForStepGroup != null && connectorIdentifiersForStepGroup.size() > 0) {
            connectorIdentifiers.addAll(connectorIdentifiersForStepGroup);
          }
        }
      }
    }
    return connectorIdentifiers;
  }

  public static List<String> getStageConnectorRefs(IntegrationStageConfig integrationStageConfig) {
    ArrayList<String> connectorIdentifiers = new ArrayList<>();
    connectorIdentifiers = populateConnectorIdentifiers(integrationStageConfig.getExecution().getSteps());

    if (integrationStageConfig.getServiceDependencies() == null
        || isEmpty(integrationStageConfig.getServiceDependencies().getValue())) {
      return connectorIdentifiers;
    }

    for (DependencyElement dependencyElement : integrationStageConfig.getServiceDependencies().getValue()) {
      if (dependencyElement == null) {
        continue;
      }

      if (dependencyElement.getDependencySpecType() instanceof CIServiceInfo) {
        CIServiceInfo serviceInfo = (CIServiceInfo) dependencyElement.getDependencySpecType();
        String connectorRef = resolveConnectorIdentifier(serviceInfo.getConnectorRef(), serviceInfo.getIdentifier());
        if (connectorRef != null) {
          connectorIdentifiers.add(connectorRef);
        }
      }
    }
    return connectorIdentifiers;
  }

  private static String getConnectorIdentifier(CIAbstractStepNode stepElementConfig) {
    if (stepElementConfig.getStepSpecType() instanceof CIStepInfo) {
      CIStepInfo ciStepInfo = (CIStepInfo) stepElementConfig.getStepSpecType();
      switch (ciStepInfo.getNonYamlInfo().getStepInfoType()) {
        case RUN:
          return resolveConnectorIdentifier(((RunStepInfo) ciStepInfo).getConnectorRef(), ciStepInfo.getIdentifier());
        case BACKGROUND:
          return resolveConnectorIdentifier(
              ((BackgroundStepInfo) ciStepInfo).getConnectorRef(), ciStepInfo.getIdentifier());
        case PLUGIN:
          return resolveConnectorIdentifier(
              ((PluginStepInfo) ciStepInfo).getConnectorRef(), ciStepInfo.getIdentifier());
        case RUN_TESTS:
          return resolveConnectorIdentifier(
              ((RunTestsStepInfo) ciStepInfo).getConnectorRef(), ciStepInfo.getIdentifier());
        case DOCKER:
        case ECR:
        case GCR:
        case ACR:
        case SAVE_CACHE_S3:
        case RESTORE_CACHE_S3:
        case RESTORE_CACHE_GCS:
        case SAVE_CACHE_GCS:
        case SECURITY:
        case UPLOAD_ARTIFACTORY:
        case UPLOAD_S3:
        case UPLOAD_GCS:
        case GIT_CLONE:
          return resolveConnectorIdentifier(
              ((PluginCompatibleStep) ciStepInfo).getConnectorRef(), ciStepInfo.getIdentifier());
        default:
          return null;
      }
    }
    return null;
  }

  private static String resolveConnectorIdentifier(ParameterField<String> connectorRef, String stepIdentifier) {
    if (connectorRef != null) {
      String connectorIdentifier = resolveStringParameter("connectorRef", "Run", stepIdentifier, connectorRef, false);
      if (!StringUtils.isEmpty(connectorIdentifier)) {
        return connectorIdentifier;
      }
    }
    return null;
  }

  public static CIInfraDetails getCiInfraDetails(Infrastructure infrastructure) {
    String infraType = infrastructure.getType().getYamlName();
    String infraOSType = null;
    String infraHostType = null;
    String infraOSArchType = ArchType.Amd64.toString();

    Infrastructure.Type type = infrastructure.getType();
    if (type == KUBERNETES_DIRECT) {
      infraOSType = getK8OS(infrastructure).toString();
      infraHostType = SELF_HOSTED;
    } else if (type == VM || type == Infrastructure.Type.DOCKER) {
      infraOSType = InfraInfoUtils.getInfraOS(infrastructure).toString();
      infraHostType = SELF_HOSTED;
    } else if (infrastructure.getType() == KUBERNETES_HOSTED) {
      infraOSType = getK8OS(infrastructure).toString();
      infraHostType = HARNESS_HOSTED;
    } else if (infrastructure.getType() == HOSTED_VM) {
      infraOSType = VmInitializeUtils.getOS(infrastructure).toString();
      infraOSArchType = VmInitializeUtils.getArchType(infrastructure).toString();
      infraHostType = HARNESS_HOSTED;
    }

    return CIInfraDetails.builder()
        .infraType(infraType)
        .infraOSType(infraOSType)
        .infraHostType(infraHostType)
        .infraArchType(infraOSArchType)
        .build();
  }

  public static CIScmDetails getCiScmDetails(ConnectorUtils connectorUtils, ConnectorDetails connectorDetails) {
    return CIScmDetails.builder()
        .scmProvider(connectorDetails.getConnectorType().getDisplayName())
        .scmAuthType(connectorUtils.getScmAuthType(connectorDetails))
        .scmHostType(connectorUtils.getScmHostType(connectorDetails))
        .build();
  }

  public static Long getStageTtl(CILicenseService ciLicenseService, String accountId, Infrastructure infrastructure) {
    if (infrastructure.getType() != HOSTED_VM && infrastructure.getType() != KUBERNETES_HOSTED) {
      return CIConstants.STAGE_MAX_TTL_SECS;
    }

    LicensesWithSummaryDTO licensesWithSummaryDTO = ciLicenseService.getLicenseSummary(accountId);

    if (licensesWithSummaryDTO == null) {
      throw new CIStageExecutionException("Please enable CI free plan or reach out to support.");
    }

    if (licensesWithSummaryDTO != null && licensesWithSummaryDTO.getEdition() == Edition.FREE) {
      return CIConstants.STAGE_MAX_TTL_SECS_HOSTED_FREE;
    }
    return CIConstants.STAGE_MAX_TTL_SECS;
  }

  public static Double getBuildTimeMultiplierForHostedInfra(Infrastructure infrastructure) {
    CIInfraDetails ciInfraDetails = getCiInfraDetails(infrastructure);
    switch (infrastructure.getType()) {
      case KUBERNETES_HOSTED:
      case HOSTED_VM:
        switch (ciInfraDetails.getInfraOSType()) {
          case "MacOs":
            return MACOS_BUILD_MULTIPLIER;
          case "Windows":
            return WINDOWS_BUILD_MULTIPLIER;
          default:
            return DEFAULT_BUILD_MULTIPLIER;
        }
      default:
    }
    return DEFAULT_BUILD_MULTIPLIER;
  }

  public static CodeBase getCICodebase(PlanCreationContext ctx) {
    CodeBase ciCodeBase = null;
    try {
      YamlNode properties = YamlUtils.getGivenYamlNodeFromParentPath(ctx.getCurrentField().getNode(), PROPERTIES);
      YamlNode ciCodeBaseNode = properties.getField(CI).getNode().getField(CI_CODE_BASE).getNode();
      ciCodeBase = IntegrationStageUtils.getCiCodeBase(ciCodeBaseNode);
    } catch (Exception ex) {
      // Ignore exception because code base is not mandatory in case git clone is false
      log.warn("Failed to retrieve ciCodeBase from pipeline");
    }

    return ciCodeBase;
  }

  public static String retrieveLastCommitSha(WebhookExecutionSource webhookExecutionSource) {
    if (webhookExecutionSource.getWebhookEvent().getType() == WebhookEvent.Type.PR) {
      PRWebhookEvent prWebhookEvent = (PRWebhookEvent) webhookExecutionSource.getWebhookEvent();
      return prWebhookEvent.getBaseAttributes().getAfter();
    } else if (webhookExecutionSource.getWebhookEvent().getType() == WebhookEvent.Type.BRANCH) {
      BranchWebhookEvent branchWebhookEvent = (BranchWebhookEvent) webhookExecutionSource.getWebhookEvent();
      return branchWebhookEvent.getBaseAttributes().getAfter();
    }

    log.error("Non supported event type, status will be empty");
    return "";
  }

  public static boolean shouldCloneManually(CodeBase codeBase) {
    if (codeBase == null) {
      return false;
    }
    Build build = RunTimeInputHandler.resolveBuild(codeBase.getBuild());
    if (build == null) {
      return false;
    }
    switch (build.getType()) {
      case BRANCH:
        ParameterField<String> branch = ((BranchBuildSpec) build.getSpec()).getBranch();
        String branchString =
            RunTimeInputHandler.resolveStringParameter("branch", "Git Clone", "identifier", branch, false);
        return isNotEmpty(branchString) && !branchString.equals(BRANCH_EXPRESSION);
      case PR:
        ParameterField<String> number = ((PRBuildSpec) build.getSpec()).getNumber();
        String numberString =
            RunTimeInputHandler.resolveStringParameter("number", "Git Clone", "identifier", number, false);
        return isNotEmpty(numberString) && !numberString.equals(PR_EXPRESSION);
      case TAG:
        ParameterField<String> tag = ((TagBuildSpec) build.getSpec()).getTag();
        String tagString = RunTimeInputHandler.resolveStringParameter("tag", "Git Clone", "identifier", tag, false);
        return isNotEmpty(tagString) && !tagString.equals(TAG_EXPRESSION);
      default:
    }
    return false;
  }

  public static List<String> getStepIdentifiers(List<ExecutionWrapperConfig> executionWrapperConfigs) {
    List<String> stepIdentifiers = new ArrayList<>();
    executionWrapperConfigs.forEach(executionWrapper -> addStepIdentifier(executionWrapper, stepIdentifiers, ""));
    return stepIdentifiers;
  }

  private static void addStepIdentifier(
      ExecutionWrapperConfig executionWrapper, List<String> stepIdentifiers, String parentId) {
    if (executionWrapper != null) {
      if (executionWrapper.getStep() != null && !executionWrapper.getStep().isNull()) {
        CIAbstractStepNode stepNode = getStepNode(executionWrapper);
        stepIdentifiers.add(parentId + stepNode.getIdentifier());
      } else if (executionWrapper.getParallel() != null && !executionWrapper.getParallel().isNull()) {
        ParallelStepElementConfig parallelStepElementConfig = getParallelStepElementConfig(executionWrapper);
        parallelStepElementConfig.getSections().forEach(
            section -> addStepIdentifier(section, stepIdentifiers, parentId));
      } else if (executionWrapper.getStepGroup() != null && !executionWrapper.getStepGroup().isNull()) {
        StepGroupElementConfig stepGroupElementConfig = getStepGroupElementConfig(executionWrapper);
        for (ExecutionWrapperConfig wrapper : stepGroupElementConfig.getSteps()) {
          addStepIdentifier(wrapper, stepIdentifiers, parentId + stepGroupElementConfig.getIdentifier() + "_");
        }
      } else {
        throw new InvalidRequestException("Only Parallel, StepElement and StepGroup are supported");
      }
    }
  }
}
