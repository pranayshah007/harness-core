/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.plugin.service;

import static io.harness.beans.serializer.RunTimeInputHandler.resolveBooleanParameter;
import static io.harness.beans.serializer.RunTimeInputHandler.resolveListParameter;
import static io.harness.beans.serializer.RunTimeInputHandler.resolveStringParameter;
import static io.harness.ci.commonconstants.BuildEnvironmentConstants.DRONE_BUILD_EVENT;
import static io.harness.ci.commonconstants.BuildEnvironmentConstants.DRONE_COMMIT_BRANCH;
import static io.harness.ci.commonconstants.BuildEnvironmentConstants.DRONE_COMMIT_REF;
import static io.harness.ci.commonconstants.BuildEnvironmentConstants.DRONE_COMMIT_SHA;
import static io.harness.ci.commonconstants.BuildEnvironmentConstants.DRONE_NETRC_MACHINE;
import static io.harness.ci.commonconstants.BuildEnvironmentConstants.DRONE_REMOTE_URL;
import static io.harness.ci.commonconstants.BuildEnvironmentConstants.DRONE_TAG;
import static io.harness.ci.commonconstants.CIExecutionConstants.DRONE_WORKSPACE;
import static io.harness.ci.commonconstants.CIExecutionConstants.GIT_CLONE_DEPTH_ATTRIBUTE;
import static io.harness.ci.commonconstants.CIExecutionConstants.GIT_CLONE_MANUAL_DEPTH;
import static io.harness.ci.commonconstants.CIExecutionConstants.GIT_CLONE_STEP_ID;
import static io.harness.ci.commonconstants.CIExecutionConstants.GIT_SSL_NO_VERIFY;
import static io.harness.ci.commonconstants.CIExecutionConstants.PATH_SEPARATOR;
import static io.harness.ci.commonconstants.ContainerExecutionConstants.PLUGIN_ENV_PREFIX;
import static io.harness.ci.commonconstants.ContainerExecutionConstants.PLUGIN_OUTPUT_FILE_PATHS_CONTENT;
import static io.harness.ci.commonconstants.ContainerExecutionConstants.STEP_MOUNT_PATH;
import static io.harness.data.structure.EmptyPredicate.isEmpty;

import static java.lang.String.format;

import io.harness.beans.plugin.compatible.PluginCompatibleStep;
import io.harness.beans.serializer.RunTimeInputHandler;
import io.harness.beans.steps.stepinfo.GitCloneStepInfo;
import io.harness.beans.sweepingoutputs.StageInfraDetails;
import io.harness.delegate.beans.ci.pod.ConnectorDetails;
import io.harness.exception.InvalidArgumentsException;
import io.harness.exception.ngexception.CIStageExecutionException;
import io.harness.exception.ngexception.CIStageExecutionUserException;
import io.harness.ng.core.NGAccess;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.yaml.ParameterField;
import io.harness.utils.CiCodebaseUtils;
import io.harness.yaml.core.variables.SecretNGVariable;
import io.harness.yaml.extended.ci.codebase.Build;
import io.harness.yaml.extended.ci.codebase.BuildType;
import io.harness.yaml.extended.ci.codebase.impl.BranchBuildSpec;
import io.harness.yaml.extended.ci.codebase.impl.PRBuildSpec;
import io.harness.yaml.extended.ci.codebase.impl.TagBuildSpec;

import com.google.inject.Inject;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.cxf.common.util.CollectionUtils;

public class PluginServiceImpl implements PluginService {
  public static final String TAG_BUILD_EVENT = "tag";
  @Inject CiCodebaseUtils ciCodebaseUtils;

  public Map<String, String> getPluginCompatibleEnvVariables(PluginCompatibleStep stepInfo, String identifier,
      long timeout, Ambiance ambiance, StageInfraDetails.Type infraType, boolean isMandatory,
      boolean isContainerizedPlugin) {
    switch (stepInfo.getNonYamlInfo().getStepInfoType()) {
      case GIT_CLONE:
        final String connectorRef = stepInfo.getConnectorRef().getValue();
        final NGAccess ngAccess = AmbianceUtils.getNgAccess(ambiance);
        final ConnectorDetails gitConnector = ciCodebaseUtils.getGitConnector(ngAccess, connectorRef);
        return getGitCloneStepInfoEnvVariables((GitCloneStepInfo) stepInfo, ambiance, gitConnector, identifier);
      default:
        throw new IllegalStateException("Unexpected value: " + stepInfo.getNonYamlInfo().getStepInfoType());
    }
  }
  private static Map<String, String> getBlankCodebaseEnvVars() {
    Map<String, String> map = new HashMap<>();
    map.put(DRONE_TAG, "");
    map.put(DRONE_NETRC_MACHINE, "");
    map.put(DRONE_BUILD_EVENT, "");
    map.put(DRONE_COMMIT_BRANCH, "");
    map.put(DRONE_REMOTE_URL, "");
    map.put(DRONE_COMMIT_SHA, "");
    map.put(DRONE_COMMIT_REF, "");
    return map;
  }

  private static Map<String, String> getSslVerifyEnvVars(ParameterField<Boolean> sslVerifyParameter) {
    Map<String, String> map = new HashMap<>();
    boolean sslVerify = resolveBooleanParameter(sslVerifyParameter, true);
    if (!sslVerify) {
      // Set GIT_SSL_NO_VERIFY=true only when ssl verify is false
      setOptionalEnvironmentVariable(map, GIT_SSL_NO_VERIFY, String.valueOf(!sslVerify));
    }
    return map;
  }

  public static void setOptionalEnvironmentVariable(Map<String, String> envVarMap, String var, String value) {
    if (isEmpty(value)) {
      return;
    }
    envVarMap.put(var, value);
  }

  public Map<String, String> getGitCloneStepInfoEnvVariables(
      GitCloneStepInfo stepInfo, Ambiance ambiance, ConnectorDetails gitConnector, String identifier) {
    Map<String, String> map = new HashMap<>();

    String repoName = stepInfo.getRepoName().getValue();
    // Overwrite all codebase env variables by setting to blank
    map.putAll(getBlankCodebaseEnvVars());

    map.putAll(getSslVerifyEnvVars(stepInfo.getSslVerify()));
    map.putAll(getGitEnvVars(gitConnector, repoName));
    map.putAll(getBuildEnvVars(ambiance, gitConnector, stepInfo));
    map.putAll(getCloneDirEnvVars(stepInfo.getCloneDirectory(), repoName, map.get(DRONE_REMOTE_URL), identifier));
    map.putAll(getPluginDepthEnvVars(stepInfo.getDepth()));
    map.putAll(getPluginOutputFilePathsContent(stepInfo.getOutputFilePathsContent(), stepInfo.getIdentifier()));

    return map;
  }

  private Map<String, String> getBuildEnvVars(
      Ambiance ambiance, ConnectorDetails gitConnector, GitCloneStepInfo gitCloneStepInfo) {
    final String identifier = gitCloneStepInfo.getIdentifier();
    final String type = gitCloneStepInfo.getStepType().getType();
    Build build = RunTimeInputHandler.resolveBuild(gitCloneStepInfo.getBuild());
    final Pair<BuildType, String> buildTypeAndValue = getBuildTypeAndValue(build);
    Map<String, String> map = new HashMap<>();

    if (buildTypeAndValue != null) {
      switch (buildTypeAndValue.getKey()) {
        case BRANCH:
          setMandatoryEnvironmentVariable(map, DRONE_COMMIT_BRANCH, buildTypeAndValue.getValue());
          break;
        case TAG:
          setMandatoryEnvironmentVariable(map, DRONE_TAG, buildTypeAndValue.getValue());
          setMandatoryEnvironmentVariable(map, DRONE_BUILD_EVENT, TAG_BUILD_EVENT);
          break;
        case PR:
          map.putAll(ciCodebaseUtils.getRuntimeCodebaseVars(ambiance, gitConnector));
          break;
        default:
          throw new CIStageExecutionException(format("%s is not a valid build type in step type %s with identifier %s",
              buildTypeAndValue.getKey(), type, identifier));
      }
    } else {
      throw new CIStageExecutionException("Build environment variables are null");
    }
    return map;
  }

  public Map<String, String> getGitEnvVars(ConnectorDetails gitConnector, String repoName) {
    // Get the Git Connector Reference environment variables
    return ciCodebaseUtils.getGitEnvVariables(gitConnector, repoName);
  }

  public static void setMandatoryEnvironmentVariable(Map<String, String> envVarMap, String var, String value) {
    if (isEmpty(value)) {
      throw new InvalidArgumentsException(format("Environment variable %s can't be empty or null", var));
    }
    envVarMap.put(var, value);
  }

  private static Pair<BuildType, String> getBuildTypeAndValue(Build build) {
    Pair<BuildType, String> buildTypeAndValue = null;
    if (build != null) {
      switch (build.getType()) {
        case PR:
          ParameterField<String> number = ((PRBuildSpec) build.getSpec()).getNumber();
          String numberString = resolveStringParameter("number", "Git Clone", "identifier", number, false);
          buildTypeAndValue = new ImmutablePair<>(BuildType.PR, numberString);
          break;
        case BRANCH:
          ParameterField<String> branch = ((BranchBuildSpec) build.getSpec()).getBranch();
          String branchString = resolveStringParameter("branch", "Git Clone", "identifier", branch, false);
          buildTypeAndValue = new ImmutablePair<>(BuildType.BRANCH, branchString);
          break;
        case TAG:
          ParameterField<String> tag = ((TagBuildSpec) build.getSpec()).getTag();
          String tagString = resolveStringParameter("tag", "Git Clone", "identifier", tag, false);
          buildTypeAndValue = new ImmutablePair<>(BuildType.TAG, tagString);
          break;
        default:
          throw new CIStageExecutionException(format("%s is not a valid build type.", build.getType()));
      }
    }
    return buildTypeAndValue;
  }

  /**
   * Get Clone Directory Env Vars
   *
   * Priority order of DRONE_WORKSPACE value
   *    1. cloneDirParameter if not empty
   *    2. repoName if not empty, appended to STEP_MOUNT_PATH
   *    3. repoName extracted from remoteUrl, appended to STEP_MOUNT_PATH
   *
   *    Note: "/harness" not allowed
   *
   * @return a map containing DRONE_WORKSPACE env variable and value
   */
  private static Map<String, String> getCloneDirEnvVars(
      ParameterField<String> cloneDirParameter, String repoName, String repoUrl, String identifier) {
    Map<String, String> map = new HashMap<>();
    String cloneDir = resolveStringParameter("cloneDirectory", "GitClone", identifier, cloneDirParameter, false);
    if (cloneDir != null) {
      cloneDir = cloneDir.trim();
    }
    if (isEmpty(cloneDir)) {
      if (repoName != null) {
        repoName = repoName.trim();
      }
      if (isEmpty(repoName)) {
        repoName = getRepoNameFromRepoUrl(repoUrl);
      }
      cloneDir = STEP_MOUNT_PATH + PATH_SEPARATOR + repoName;
    }
    if (!identifier.equals(GIT_CLONE_STEP_ID) && STEP_MOUNT_PATH.equals(cloneDir)) {
      throw new CIStageExecutionUserException(
          format("%s is an invalid value for the cloneDirectory field in the GitClone step with identifier %s",
              STEP_MOUNT_PATH, identifier));
    }
    setMandatoryEnvironmentVariable(map, DRONE_WORKSPACE, cloneDir);
    return map;
  }

  /**
   * Get Repo Name from Repo Url.
   *
   * Note that GIT SSH URLs aren't actually URLs or URIs, but rather follow a legacy scp-like syntax
   * @param repoUrl the git url or scp-like ssh path
   * @return the repository from the url or by default "repository"
   */
  public static String getRepoNameFromRepoUrl(String repoUrl) {
    String repoName = "repository";
    if (!isEmpty(repoUrl)) {
      int lastPathSeparatorIndex = repoUrl.lastIndexOf(PATH_SEPARATOR);
      if (lastPathSeparatorIndex != -1) {
        repoUrl = repoUrl.substring(lastPathSeparatorIndex + 1);
      }
      int lastDotIndex = repoUrl.lastIndexOf('.');
      if (lastDotIndex != -1) {
        repoUrl = repoUrl.substring(0, lastDotIndex);
      }
      if (!isEmpty(repoUrl)) {
        repoName = repoUrl;
      }
    }
    return repoName;
  }

  private static Map<String, String> getPluginDepthEnvVars(ParameterField<Integer> depthParameter) {
    Map<String, String> map = new HashMap<>();
    Integer depth = GIT_CLONE_MANUAL_DEPTH;
    if (depthParameter != null && depthParameter.getValue() != null) {
      depth = depthParameter.getValue();
    }
    if (depth != null && depth != 0) {
      String pluginDepthKey = PLUGIN_ENV_PREFIX + GIT_CLONE_DEPTH_ATTRIBUTE.toUpperCase(Locale.ROOT);
      map.put(pluginDepthKey, depth.toString());
    }
    return map;
  }

  static Map<String, String> getPluginOutputFilePathsContent(
      ParameterField<List<String>> outputFilePathsContent, String stepIdentifier) {
    Map<String, String> map = new HashMap<>();
    List<String> outputFilePathsContentList =
        resolveListParameter("outputFilePathsContent", "GitClone", stepIdentifier, outputFilePathsContent, false);

    if (outputFilePathsContentList != null && !CollectionUtils.isEmpty(outputFilePathsContentList)) {
      map.put(PLUGIN_OUTPUT_FILE_PATHS_CONTENT, String.join(",", outputFilePathsContentList));
    }
    return map;
  }

  @Override
  public Map<String, SecretNGVariable> getPluginCompatibleSecretVars(PluginCompatibleStep step, String identifier) {
    return new HashMap<>();
  }
}
