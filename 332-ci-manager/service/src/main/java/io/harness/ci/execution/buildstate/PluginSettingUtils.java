/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.buildstate;

import static io.harness.beans.serializer.RunTimeInputHandler.UNRESOLVED_PARAMETER;
import static io.harness.beans.serializer.RunTimeInputHandler.resolveArchiveFormat;
import static io.harness.beans.serializer.RunTimeInputHandler.resolveBooleanParameter;
import static io.harness.beans.serializer.RunTimeInputHandler.resolveIntegerParameter;
import static io.harness.beans.serializer.RunTimeInputHandler.resolveJsonNodeMapParameter;
import static io.harness.beans.serializer.RunTimeInputHandler.resolveListParameter;
import static io.harness.beans.serializer.RunTimeInputHandler.resolveMapParameter;
import static io.harness.beans.serializer.RunTimeInputHandler.resolveStringParameter;
import static io.harness.beans.steps.CIStepInfoType.GIT_CLONE;
import static io.harness.ci.commonconstants.BuildEnvironmentConstants.DRONE_BUILD_EVENT;
import static io.harness.ci.commonconstants.BuildEnvironmentConstants.DRONE_COMMIT_BRANCH;
import static io.harness.ci.commonconstants.BuildEnvironmentConstants.DRONE_COMMIT_SHA;
import static io.harness.ci.commonconstants.BuildEnvironmentConstants.DRONE_NETRC_MACHINE;
import static io.harness.ci.commonconstants.BuildEnvironmentConstants.DRONE_REMOTE_URL;
import static io.harness.ci.commonconstants.BuildEnvironmentConstants.DRONE_TAG;
import static io.harness.ci.commonconstants.CIExecutionConstants.CLIENT_CERTIFICATE;
import static io.harness.ci.commonconstants.CIExecutionConstants.CLIENT_ID;
import static io.harness.ci.commonconstants.CIExecutionConstants.CLIENT_SECRET;
import static io.harness.ci.commonconstants.CIExecutionConstants.DRONE_WORKSPACE;
import static io.harness.ci.commonconstants.CIExecutionConstants.GIT_CLONE_DEPTH_ATTRIBUTE;
import static io.harness.ci.commonconstants.CIExecutionConstants.GIT_CLONE_MANUAL_DEPTH;
import static io.harness.ci.commonconstants.CIExecutionConstants.GIT_CLONE_STEP_ID;
import static io.harness.ci.commonconstants.CIExecutionConstants.GIT_SSL_NO_VERIFY;
import static io.harness.ci.commonconstants.CIExecutionConstants.PATH_SEPARATOR;
import static io.harness.ci.commonconstants.CIExecutionConstants.PLUGIN_ACCESS_KEY;
import static io.harness.ci.commonconstants.CIExecutionConstants.PLUGIN_ARTIFACT_FILE_VALUE;
import static io.harness.ci.commonconstants.CIExecutionConstants.PLUGIN_ASSUME_ROLE;
import static io.harness.ci.commonconstants.CIExecutionConstants.PLUGIN_ENV_PREFIX;
import static io.harness.ci.commonconstants.CIExecutionConstants.PLUGIN_EXTERNAL_ID;
import static io.harness.ci.commonconstants.CIExecutionConstants.PLUGIN_JSON_KEY;
import static io.harness.ci.commonconstants.CIExecutionConstants.PLUGIN_PASSW;
import static io.harness.ci.commonconstants.CIExecutionConstants.PLUGIN_SECRET_KEY;
import static io.harness.ci.commonconstants.CIExecutionConstants.PLUGIN_URL;
import static io.harness.ci.commonconstants.CIExecutionConstants.PLUGIN_USERNAME;
import static io.harness.ci.commonconstants.CIExecutionConstants.STEP_MOUNT_PATH;
import static io.harness.ci.commonconstants.CIExecutionConstants.TENANT_ID;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import static java.lang.String.format;
import static org.springframework.util.StringUtils.trimLeadingCharacter;
import static org.springframework.util.StringUtils.trimTrailingCharacter;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.executionargs.CIExecutionArgs;
import io.harness.beans.plugin.compatible.PluginCompatibleStep;
import io.harness.beans.serializer.RunTimeInputHandler;
import io.harness.beans.steps.CIStepInfoType;
import io.harness.beans.steps.stepinfo.ACRStepInfo;
import io.harness.beans.steps.stepinfo.DockerStepInfo;
import io.harness.beans.steps.stepinfo.ECRStepInfo;
import io.harness.beans.steps.stepinfo.GCRStepInfo;
import io.harness.beans.steps.stepinfo.GitCloneStepInfo;
import io.harness.beans.steps.stepinfo.RestoreCacheGCSStepInfo;
import io.harness.beans.steps.stepinfo.RestoreCacheS3StepInfo;
import io.harness.beans.steps.stepinfo.SaveCacheGCSStepInfo;
import io.harness.beans.steps.stepinfo.SaveCacheS3StepInfo;
import io.harness.beans.steps.stepinfo.SecurityStepInfo;
import io.harness.beans.steps.stepinfo.UploadToArtifactoryStepInfo;
import io.harness.beans.steps.stepinfo.UploadToGCSStepInfo;
import io.harness.beans.steps.stepinfo.UploadToS3StepInfo;
import io.harness.beans.steps.stepinfo.security.BlackDuckStepInfo;
import io.harness.beans.steps.stepinfo.security.BurpStepInfo;
import io.harness.beans.steps.stepinfo.security.CheckmarxStepInfo;
import io.harness.beans.steps.stepinfo.security.FortifyOnDemandStepInfo;
import io.harness.beans.steps.stepinfo.security.PrismaCloudStepInfo;
import io.harness.beans.steps.stepinfo.security.SnykStepInfo;
import io.harness.beans.steps.stepinfo.security.SonarqubeStepInfo;
import io.harness.beans.steps.stepinfo.security.VeracodeStepInfo;
import io.harness.beans.steps.stepinfo.security.ZapStepInfo;
import io.harness.beans.steps.stepinfo.security.shared.STOGenericStepInfo;
import io.harness.beans.steps.stepinfo.security.shared.STOYamlAdvancedSettings;
import io.harness.beans.steps.stepinfo.security.shared.STOYamlArgs;
import io.harness.beans.steps.stepinfo.security.shared.STOYamlAuth;
import io.harness.beans.steps.stepinfo.security.shared.STOYamlBlackduckToolData;
import io.harness.beans.steps.stepinfo.security.shared.STOYamlCheckmarxToolData;
import io.harness.beans.steps.stepinfo.security.shared.STOYamlFODToolData;
import io.harness.beans.steps.stepinfo.security.shared.STOYamlImage;
import io.harness.beans.steps.stepinfo.security.shared.STOYamlIngestion;
import io.harness.beans.steps.stepinfo.security.shared.STOYamlInstance;
import io.harness.beans.steps.stepinfo.security.shared.STOYamlJavaParameters;
import io.harness.beans.steps.stepinfo.security.shared.STOYamlLog;
import io.harness.beans.steps.stepinfo.security.shared.STOYamlSonarqubeToolData;
import io.harness.beans.steps.stepinfo.security.shared.STOYamlTarget;
import io.harness.beans.steps.stepinfo.security.shared.STOYamlVeracodeToolData;
import io.harness.beans.steps.stepinfo.security.shared.STOYamlZapToolData;
import io.harness.beans.sweepingoutputs.StageInfraDetails.Type;
import io.harness.beans.yaml.extended.ArchiveFormat;
import io.harness.ci.integrationstage.BuildEnvironmentUtils;
import io.harness.ci.serializer.SerializerUtils;
import io.harness.delegate.beans.ci.pod.ConnectorDetails;
import io.harness.delegate.beans.ci.pod.EnvVariableEnum;
import io.harness.exception.InvalidArgumentsException;
import io.harness.exception.ngexception.CIStageExecutionException;
import io.harness.exception.ngexception.CIStageExecutionUserException;
import io.harness.ng.core.NGAccess;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.yaml.ParameterField;
import io.harness.yaml.extended.ci.codebase.Build;
import io.harness.yaml.extended.ci.codebase.BuildType;
import io.harness.yaml.extended.ci.codebase.impl.BranchBuildSpec;
import io.harness.yaml.extended.ci.codebase.impl.PRBuildSpec;
import io.harness.yaml.extended.ci.codebase.impl.TagBuildSpec;
import io.harness.yaml.sto.variables.STOYamlAuthType;
import io.harness.yaml.sto.variables.STOYamlGenericConfig;
import io.harness.yaml.sto.variables.STOYamlImageType;
import io.harness.yaml.sto.variables.STOYamlLogLevel;
import io.harness.yaml.sto.variables.STOYamlScanMode;
import io.harness.yaml.sto.variables.STOYamlTargetType;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;

@OwnedBy(HarnessTeam.CI)
@Singleton
public class PluginSettingUtils {
  public static final String PLUGIN_REGISTRY = "PLUGIN_REGISTRY";
  public static final String TAG_BUILD_EVENT = "tag";

  public static final String REPOSITORY = "REPOSITORY";
  public static final String PLUGIN_REPO = "PLUGIN_REPO";

  public static final String SUBSCRIPTION_ID = "SUBSCRIPTION_ID";
  public static final String PLUGIN_TAGS = "PLUGIN_TAGS";
  public static final String PLUGIN_DOCKERFILE = "PLUGIN_DOCKERFILE";
  public static final String PLUGIN_CONTEXT = "PLUGIN_CONTEXT";
  public static final String PLUGIN_TARGET = "PLUGIN_TARGET";
  public static final String PLUGIN_FLAT = "PLUGIN_FLAT";
  public static final String PLUGIN_STRIP_PREFIX = "PLUGIN_STRIP_PREFIX";
  public static final String PLUGIN_CACHE_REPO = "PLUGIN_CACHE_REPO";
  public static final String PLUGIN_ENABLE_CACHE = "PLUGIN_ENABLE_CACHE";
  public static final String PLUGIN_BUILD_ARGS = "PLUGIN_BUILD_ARGS";
  public static final String PLUGIN_CUSTOM_LABELS = "PLUGIN_CUSTOM_LABELS";
  public static final String PLUGIN_MOUNT = "PLUGIN_MOUNT";
  public static final String PLUGIN_BUCKET = "PLUGIN_BUCKET";
  public static final String PLUGIN_ENDPOINT = "PLUGIN_ENDPOINT";
  public static final String PLUGIN_REGION = "PLUGIN_REGION";
  public static final String PLUGIN_SOURCE = "PLUGIN_SOURCE";
  public static final String PLUGIN_STEP_ID = "PLUGIN_STEP_ID";
  public static final String PLUGIN_RESTORE = "PLUGIN_RESTORE";
  public static final String PLUGIN_REBUILD = "PLUGIN_REBUILD";
  public static final String PLUGIN_EXIT_CODE = "PLUGIN_EXIT_CODE";
  public static final String PLUGIN_PATH_STYLE = "PLUGIN_PATH_STYLE";
  public static final String PLUGIN_FAIL_RESTORE_IF_KEY_NOT_PRESENT = "PLUGIN_FAIL_RESTORE_IF_KEY_NOT_PRESENT";
  public static final String PLUGIN_SNAPSHOT_MODE = "PLUGIN_SNAPSHOT_MODE";
  public static final String REDO_SNAPSHOT_MODE = "redo";
  public static final String SECURITY_ENV_PREFIX = "SECURITY_";
  public static final String PLUGIN_BACKEND_OPERATION_TIMEOUT = "PLUGIN_BACKEND_OPERATION_TIMEOUT";
  public static final String PLUGIN_CACHE_KEY = "PLUGIN_CACHE_KEY";
  public static final String PLUGIN_AUTO_DETECT_CACHE = "PLUGIN_AUTO_CACHE";
  public static final String PLUGIN_AUTO_CACHE_ACCOUNT_ID = "PLUGIN_ACCOUNT_ID";
  public static final String PLUGIN_BACKEND = "PLUGIN_BACKEND";
  public static final String PLUGIN_OVERRIDE = "PLUGIN_OVERRIDE";
  public static final String PLUGIN_ARCHIVE_FORMAT = "PLUGIN_ARCHIVE_FORMAT";
  public static final String PLUGIN_ARTIFACT_FILE = "PLUGIN_ARTIFACT_FILE";
  public static final String PLUGIN_DAEMON_OFF = "PLUGIN_DAEMON_OFF";
  public static final String ECR_REGISTRY_PATTERN = "%s.dkr.ecr.%s.amazonaws.com";

  @Inject private CodebaseUtils codebaseUtils;

  public Map<String, String> getPluginCompatibleEnvVariables(
      PluginCompatibleStep stepInfo, String identifier, long timeout, Ambiance ambiance, Type infraType) {
    switch (stepInfo.getNonYamlInfo().getStepInfoType()) {
      case ECR:
        return getECRStepInfoEnvVariables((ECRStepInfo) stepInfo, identifier, infraType);
      case ACR:
        return getACRStepInfoEnvVariables((ACRStepInfo) stepInfo, identifier, infraType);
      case GCR:
        return getGCRStepInfoEnvVariables((GCRStepInfo) stepInfo, identifier, infraType);
      case DOCKER:
        return getDockerStepInfoEnvVariables((DockerStepInfo) stepInfo, identifier, infraType);
      case UPLOAD_ARTIFACTORY:
        return getUploadToArtifactoryStepInfoEnvVariables((UploadToArtifactoryStepInfo) stepInfo, identifier);
      case UPLOAD_GCS:
        return getUploadToGCSStepInfoEnvVariables((UploadToGCSStepInfo) stepInfo, identifier);
      case UPLOAD_S3:
        return getUploadToS3StepInfoEnvVariables((UploadToS3StepInfo) stepInfo, identifier);
      case SAVE_CACHE_GCS:
        return getSaveCacheGCSStepInfoEnvVariables((SaveCacheGCSStepInfo) stepInfo, identifier, timeout);
      case SECURITY:
        return getSecurityStepInfoEnvVariables((SecurityStepInfo) stepInfo, identifier);
      case RESTORE_CACHE_GCS:
        return getRestoreCacheGCSStepInfoEnvVariables((RestoreCacheGCSStepInfo) stepInfo, identifier, timeout);
      case SAVE_CACHE_S3:
        return getSaveCacheS3StepInfoEnvVariables((SaveCacheS3StepInfo) stepInfo, identifier, timeout);
      case RESTORE_CACHE_S3:
        return getRestoreCacheS3StepInfoEnvVariables((RestoreCacheS3StepInfo) stepInfo, identifier, timeout);
      case GIT_CLONE:
        return getGitCloneStepInfoEnvVariables((GitCloneStepInfo) stepInfo, ambiance, identifier);
      default:
        throw new IllegalStateException("Unexpected value: " + stepInfo.getNonYamlInfo().getStepInfoType());
    }
  }

  public static String getConnectorRef(PluginCompatibleStep stepInfo) {
    String stepType = stepInfo.getNonYamlInfo().getStepInfoType().getDisplayName();
    return RunTimeInputHandler.resolveStringParameter(
        "connectorRef", stepType, stepInfo.getIdentifier(), stepInfo.getConnectorRef(), true);
  }

  public static List<String> getBaseImageConnectorRefs(PluginCompatibleStep stepInfo) {
    String stepType = stepInfo.getNonYamlInfo().getStepInfoType().getDisplayName();
    return RunTimeInputHandler.resolveListParameter(
        "baseImageConnectorRefs", stepType, stepInfo.getIdentifier(), stepInfo.getBaseImageConnectorRefs(), false);
  }

  public static Map<EnvVariableEnum, String> getConnectorSecretEnvMap(CIStepInfoType stepInfoType) {
    Map<EnvVariableEnum, String> map = new HashMap<>();
    switch (stepInfoType) {
      case ECR:
        map.put(EnvVariableEnum.AWS_ACCESS_KEY, PLUGIN_ACCESS_KEY);
        map.put(EnvVariableEnum.AWS_SECRET_KEY, PLUGIN_SECRET_KEY);
        map.put(EnvVariableEnum.AWS_CROSS_ACCOUNT_ROLE_ARN, PLUGIN_ASSUME_ROLE);
        map.put(EnvVariableEnum.AWS_CROSS_ACCOUNT_EXTERNAL_ID, PLUGIN_EXTERNAL_ID);
        return map;
      case RESTORE_CACHE_S3:
      case SAVE_CACHE_S3:
      case UPLOAD_S3:
        map.put(EnvVariableEnum.AWS_ACCESS_KEY, PLUGIN_ACCESS_KEY);
        map.put(EnvVariableEnum.AWS_SECRET_KEY, PLUGIN_SECRET_KEY);
        map.put(EnvVariableEnum.AWS_CROSS_ACCOUNT_ROLE_ARN, PLUGIN_ASSUME_ROLE);
        return map;
      case ACR:
        map.put(EnvVariableEnum.AZURE_APP_SECRET, CLIENT_SECRET);
        map.put(EnvVariableEnum.AZURE_APP_ID, CLIENT_ID);
        map.put(EnvVariableEnum.AZURE_TENANT_ID, TENANT_ID);
        map.put(EnvVariableEnum.AZURE_CERT, CLIENT_CERTIFICATE);
        return map;
      case GCR:
      case RESTORE_CACHE_GCS:
      case SAVE_CACHE_GCS:
      case UPLOAD_GCS:
        map.put(EnvVariableEnum.GCP_KEY, PLUGIN_JSON_KEY);
        return map;
      case SECURITY:
      case DOCKER:
        map.put(EnvVariableEnum.DOCKER_USERNAME, PLUGIN_USERNAME);
        map.put(EnvVariableEnum.DOCKER_PASSWORD, PLUGIN_PASSW);
        map.put(EnvVariableEnum.DOCKER_REGISTRY, PLUGIN_REGISTRY);
        return map;
      case UPLOAD_ARTIFACTORY:
        map.put(EnvVariableEnum.ARTIFACTORY_ENDPOINT, PLUGIN_URL);
        map.put(EnvVariableEnum.ARTIFACTORY_USERNAME, PLUGIN_USERNAME);
        map.put(EnvVariableEnum.ARTIFACTORY_PASSWORD, PLUGIN_PASSW);
        return map;
      case GIT_CLONE:
        return map;
      default:
        throw new IllegalStateException("Unexpected value: " + stepInfoType);
    }
  }

  private static Map<String, String> getGCRStepInfoEnvVariables(
      GCRStepInfo stepInfo, String identifier, Type infraType) {
    Map<String, String> map = new HashMap<>();

    String host = resolveStringParameter("host", "BuildAndPushGCR", identifier, stepInfo.getHost(), true);
    String projectID =
        resolveStringParameter("projectID", "BuildAndPushGCR", identifier, stepInfo.getProjectID(), true);
    String registry = null;
    if (isNotEmpty(host) && isNotEmpty(projectID)) {
      registry = format("%s/%s", trimTrailingCharacter(host, '/'), trimLeadingCharacter(projectID, '/'));
    }
    setMandatoryEnvironmentVariable(map, PLUGIN_REGISTRY, registry);

    setMandatoryEnvironmentVariable(map, PLUGIN_REPO,
        resolveStringParameter("imageName", "BuildAndPushGCR", identifier, stepInfo.getImageName(), true));

    setMandatoryEnvironmentVariable(map, PLUGIN_TAGS,
        listToStringSlice(resolveListParameter("tags", "BuildAndPushGCR", identifier, stepInfo.getTags(), true)));

    String dockerfile =
        resolveStringParameter("dockerfile", "BuildAndPushGCR", identifier, stepInfo.getDockerfile(), false);
    if (dockerfile != null && !dockerfile.equals(UNRESOLVED_PARAMETER)) {
      setOptionalEnvironmentVariable(map, PLUGIN_DOCKERFILE, dockerfile);
    }

    String context = resolveStringParameter("context", "BuildAndPushGCR", identifier, stepInfo.getContext(), false);
    if (context != null && !context.equals(UNRESOLVED_PARAMETER)) {
      setOptionalEnvironmentVariable(map, PLUGIN_CONTEXT, context);
    }

    String target = resolveStringParameter("target", "BuildAndPushGCR", identifier, stepInfo.getTarget(), false);
    if (target != null && !target.equals(UNRESOLVED_PARAMETER)) {
      setOptionalEnvironmentVariable(map, PLUGIN_TARGET, target);
    }

    Map<String, String> buildArgs =
        resolveMapParameter("buildArgs", "BuildAndPushGCR", identifier, stepInfo.getBuildArgs(), false);
    if (isNotEmpty(buildArgs)) {
      setOptionalEnvironmentVariable(map, PLUGIN_BUILD_ARGS, mapToStringSlice(buildArgs));
    }

    Map<String, String> labels =
        resolveMapParameter("labels", "BuildAndPushGCR", identifier, stepInfo.getLabels(), false);
    if (isNotEmpty(labels)) {
      setOptionalEnvironmentVariable(map, PLUGIN_CUSTOM_LABELS, mapToStringSlice(labels));
    }

    if (infraType == Type.K8) {
      boolean optimize = resolveBooleanParameter(stepInfo.getOptimize(), true);
      if (optimize) {
        setOptionalEnvironmentVariable(map, PLUGIN_SNAPSHOT_MODE, REDO_SNAPSHOT_MODE);
      }

      String remoteCacheImage = resolveStringParameter(
          "remoteCacheImage", "BuildAndPushGCR", identifier, stepInfo.getRemoteCacheImage(), false);
      if (remoteCacheImage != null && !remoteCacheImage.equals(UNRESOLVED_PARAMETER)) {
        setOptionalEnvironmentVariable(map, PLUGIN_ENABLE_CACHE, "true");
        setOptionalEnvironmentVariable(map, PLUGIN_CACHE_REPO, remoteCacheImage);
      }

      setOptionalEnvironmentVariable(map, PLUGIN_ARTIFACT_FILE, PLUGIN_ARTIFACT_FILE_VALUE);
    } else if (infraType == Type.VM) {
      setMandatoryEnvironmentVariable(map, PLUGIN_DAEMON_OFF, "true");
    }

    return map;
  }

  private static Map<String, String> getACRStepInfoEnvVariables(
      ACRStepInfo stepInfo, String identifier, Type infraType) {
    Map<String, String> map = new HashMap<>();
    String pluginRepo =
        resolveStringParameter(REPOSITORY, "BuildAndPushACR", identifier, stepInfo.getRepository(), true);
    String subscriptionId =
        resolveStringParameter(REPOSITORY, "SubscriptionId", identifier, stepInfo.getSubscriptionId(), false);
    if (StringUtils.isNotBlank(subscriptionId)) {
      setOptionalEnvironmentVariable(map, SUBSCRIPTION_ID, subscriptionId);
    }
    String pluginRegistry = StringUtils.substringBefore(pluginRepo, "/");
    setMandatoryEnvironmentVariable(map, PLUGIN_REGISTRY, pluginRegistry);
    setMandatoryEnvironmentVariable(map, PLUGIN_REPO, pluginRepo);

    setMandatoryEnvironmentVariable(map, PLUGIN_TAGS,
        listToStringSlice(resolveListParameter("tags", "BuildAndPushECR", identifier, stepInfo.getTags(), true)));

    String dockerfile =
        resolveStringParameter("dockerfile", "BuildAndPushACR", identifier, stepInfo.getDockerfile(), false);
    if (dockerfile != null && !dockerfile.equals(UNRESOLVED_PARAMETER)) {
      setOptionalEnvironmentVariable(map, PLUGIN_DOCKERFILE, dockerfile);
    }

    String context = resolveStringParameter("context", "BuildAndPushACR", identifier, stepInfo.getContext(), false);
    if (context != null && !context.equals(UNRESOLVED_PARAMETER)) {
      setOptionalEnvironmentVariable(map, PLUGIN_CONTEXT, context);
    }

    String target = resolveStringParameter("target", "BuildAndPushACR", identifier, stepInfo.getTarget(), false);
    if (target != null && !target.equals(UNRESOLVED_PARAMETER)) {
      setOptionalEnvironmentVariable(map, PLUGIN_TARGET, target);
    }

    Map<String, String> buildArgs =
        resolveMapParameter("buildArgs", "BuildAndPushACR", identifier, stepInfo.getBuildArgs(), false);
    if (isNotEmpty(buildArgs)) {
      setOptionalEnvironmentVariable(map, PLUGIN_BUILD_ARGS, mapToStringSlice(buildArgs));
    }

    Map<String, String> labels =
        resolveMapParameter("labels", "BuildAndPushACR", identifier, stepInfo.getLabels(), false);
    if (isNotEmpty(labels)) {
      setOptionalEnvironmentVariable(map, PLUGIN_CUSTOM_LABELS, mapToStringSlice(labels));
    }

    if (infraType == Type.K8) {
      getACRStepInfoVariablesForK8s(stepInfo, identifier, map);
    } else if (infraType == Type.VM) {
      setMandatoryEnvironmentVariable(map, PLUGIN_DAEMON_OFF, "true");
    }
    return map;
  }

  private static void getACRStepInfoVariablesForK8s(ACRStepInfo stepInfo, String identifier, Map<String, String> map) {
    boolean optimize = resolveBooleanParameter(stepInfo.getOptimize(), true);
    if (optimize) {
      setOptionalEnvironmentVariable(map, PLUGIN_SNAPSHOT_MODE, REDO_SNAPSHOT_MODE);
    }

    String remoteCacheImage = resolveStringParameter(
        "remoteCacheImage", "BuildAndPushACR", identifier, stepInfo.getRemoteCacheImage(), false);
    if (remoteCacheImage != null && !remoteCacheImage.equals(UNRESOLVED_PARAMETER)) {
      setOptionalEnvironmentVariable(map, PLUGIN_ENABLE_CACHE, "true");
      setOptionalEnvironmentVariable(map, PLUGIN_CACHE_REPO, remoteCacheImage);
    }

    setOptionalEnvironmentVariable(map, PLUGIN_ARTIFACT_FILE, PLUGIN_ARTIFACT_FILE_VALUE);
  }

  private static Map<String, String> getECRStepInfoEnvVariables(
      ECRStepInfo stepInfo, String identifier, Type infraType) {
    Map<String, String> map = new HashMap<>();
    String account = resolveStringParameter("account", "BuildAndPushECR", identifier, stepInfo.getAccount(), true);
    String region = resolveStringParameter("region", "BuildAndPushECR", identifier, stepInfo.getRegion(), true);
    String registry = null;
    if (isNotEmpty(account) && isNotEmpty(region)) {
      registry = format(ECR_REGISTRY_PATTERN, account, region);
    }

    setMandatoryEnvironmentVariable(map, PLUGIN_REGISTRY, registry);

    setMandatoryEnvironmentVariable(map, PLUGIN_REPO,
        resolveStringParameter("imageName", "BuildAndPushECR", identifier, stepInfo.getImageName(), true));
    setMandatoryEnvironmentVariable(map, PLUGIN_TAGS,
        listToStringSlice(resolveListParameter("tags", "BuildAndPushECR", identifier, stepInfo.getTags(), true)));

    if (isNotEmpty(region) && !region.equals(UNRESOLVED_PARAMETER)) {
      setOptionalEnvironmentVariable(map, PLUGIN_REGION, region);
    }

    String dockerfile =
        resolveStringParameter("dockerfile", "BuildAndPushECR", identifier, stepInfo.getDockerfile(), false);
    if (dockerfile != null && !dockerfile.equals(UNRESOLVED_PARAMETER)) {
      setOptionalEnvironmentVariable(map, PLUGIN_DOCKERFILE, dockerfile);
    }

    String context = resolveStringParameter("context", "BuildAndPushECR", identifier, stepInfo.getContext(), false);
    if (context != null && !context.equals(UNRESOLVED_PARAMETER)) {
      setOptionalEnvironmentVariable(map, PLUGIN_CONTEXT, context);
    }

    String target = resolveStringParameter("target", "BuildAndPushECR", identifier, stepInfo.getTarget(), false);
    if (target != null && !target.equals(UNRESOLVED_PARAMETER)) {
      setOptionalEnvironmentVariable(map, PLUGIN_TARGET, target);
    }

    Map<String, String> buildArgs =
        resolveMapParameter("buildArgs", "BuildAndPushECR", identifier, stepInfo.getBuildArgs(), false);
    if (isNotEmpty(buildArgs)) {
      setOptionalEnvironmentVariable(map, PLUGIN_BUILD_ARGS, mapToStringSlice(buildArgs));
    }

    Map<String, String> labels =
        resolveMapParameter("labels", "BuildAndPushECR", identifier, stepInfo.getLabels(), false);
    if (isNotEmpty(labels)) {
      setOptionalEnvironmentVariable(map, PLUGIN_CUSTOM_LABELS, mapToStringSlice(labels));
    }

    if (infraType == Type.K8) {
      boolean optimize = resolveBooleanParameter(stepInfo.getOptimize(), true);
      if (optimize) {
        setOptionalEnvironmentVariable(map, PLUGIN_SNAPSHOT_MODE, REDO_SNAPSHOT_MODE);
      }

      String remoteCacheImage = resolveStringParameter(
          "remoteCacheImage", "BuildAndPushECR", identifier, stepInfo.getRemoteCacheImage(), false);
      if (remoteCacheImage != null && !remoteCacheImage.equals(UNRESOLVED_PARAMETER)) {
        setOptionalEnvironmentVariable(map, PLUGIN_ENABLE_CACHE, "true");
        setOptionalEnvironmentVariable(map, PLUGIN_CACHE_REPO, remoteCacheImage);
      }

      setOptionalEnvironmentVariable(map, PLUGIN_ARTIFACT_FILE, PLUGIN_ARTIFACT_FILE_VALUE);
    } else if (infraType == Type.VM) {
      setMandatoryEnvironmentVariable(map, PLUGIN_DAEMON_OFF, "true");
    }

    return map;
  }

  private static Map<String, String> getDockerStepInfoEnvVariables(
      DockerStepInfo stepInfo, String identifier, Type infraType) {
    Map<String, String> map = new HashMap<>();

    setMandatoryEnvironmentVariable(map, PLUGIN_REPO,
        resolveStringParameter("repo", "BuildAndPushDockerRegistry", identifier, stepInfo.getRepo(), true));
    setMandatoryEnvironmentVariable(map, PLUGIN_TAGS,
        listToStringSlice(
            resolveListParameter("tags", "BuildAndPushDockerRegistry", identifier, stepInfo.getTags(), true)));

    String dockerFile =
        resolveStringParameter("dockerfile", "BuildAndPushDockerRegistry", identifier, stepInfo.getDockerfile(), false);
    if (dockerFile != null && !dockerFile.equals(UNRESOLVED_PARAMETER)) {
      setOptionalEnvironmentVariable(map, PLUGIN_DOCKERFILE, dockerFile);
    }

    String context =
        resolveStringParameter("context", "BuildAndPushDockerRegistry", identifier, stepInfo.getContext(), false);
    if (context != null && !context.equals(UNRESOLVED_PARAMETER)) {
      setOptionalEnvironmentVariable(map, PLUGIN_CONTEXT, context);
    }

    String target =
        resolveStringParameter("target", "BuildAndPushDockerRegistry", identifier, stepInfo.getTarget(), false);
    if (target != null && !target.equals(UNRESOLVED_PARAMETER)) {
      setOptionalEnvironmentVariable(map, PLUGIN_TARGET, target);
    }

    Map<String, String> buildArgs =
        resolveMapParameter("buildArgs", "BuildAndPushDockerRegistry", identifier, stepInfo.getBuildArgs(), false);
    if (isNotEmpty(buildArgs)) {
      setOptionalEnvironmentVariable(map, PLUGIN_BUILD_ARGS, mapToStringSlice(buildArgs));
    }

    Map<String, String> labels =
        resolveMapParameter("labels", "BuildAndPushDockerRegistry", identifier, stepInfo.getLabels(), false);
    if (isNotEmpty(labels)) {
      setOptionalEnvironmentVariable(map, PLUGIN_CUSTOM_LABELS, mapToStringSlice(labels));
    }

    if (infraType == Type.K8) {
      boolean optimize = resolveBooleanParameter(stepInfo.getOptimize(), true);
      if (optimize) {
        setOptionalEnvironmentVariable(map, PLUGIN_SNAPSHOT_MODE, REDO_SNAPSHOT_MODE);
      }
      setOptionalEnvironmentVariable(map, PLUGIN_ARTIFACT_FILE, PLUGIN_ARTIFACT_FILE_VALUE);
      String remoteCacheRepo = resolveStringParameter(
          "remoteCacheRepo", "BuildAndPushDockerRegistry", identifier, stepInfo.getRemoteCacheRepo(), false);
      if (remoteCacheRepo != null && !remoteCacheRepo.equals(UNRESOLVED_PARAMETER)) {
        setOptionalEnvironmentVariable(map, PLUGIN_ENABLE_CACHE, "true");
        setOptionalEnvironmentVariable(map, PLUGIN_CACHE_REPO, remoteCacheRepo);
      }
    } else if (infraType == Type.VM) {
      setMandatoryEnvironmentVariable(map, PLUGIN_DAEMON_OFF, "true");
    }

    return map;
  }

  private static Map<String, String> getRestoreCacheGCSStepInfoEnvVariables(
      RestoreCacheGCSStepInfo stepInfo, String identifier, long timeout) {
    Map<String, String> map = new HashMap<>();

    setMandatoryEnvironmentVariable(
        map, PLUGIN_CACHE_KEY, resolveStringParameter("key", "RestoreCacheGCS", identifier, stepInfo.getKey(), true));
    setMandatoryEnvironmentVariable(map, PLUGIN_BUCKET,
        resolveStringParameter("bucket", "RestoreCacheGCS", identifier, stepInfo.getBucket(), true));

    setMandatoryEnvironmentVariable(map, PLUGIN_RESTORE, "true");
    setMandatoryEnvironmentVariable(map, PLUGIN_EXIT_CODE, "true");

    ArchiveFormat archiveFormat = resolveArchiveFormat(stepInfo.getArchiveFormat());
    setMandatoryEnvironmentVariable(map, PLUGIN_ARCHIVE_FORMAT, archiveFormat.toString());

    boolean failIfKeyNotFound = resolveBooleanParameter(stepInfo.getFailIfKeyNotFound(), false);
    setOptionalEnvironmentVariable(map, PLUGIN_FAIL_RESTORE_IF_KEY_NOT_PRESENT, String.valueOf(failIfKeyNotFound));
    setMandatoryEnvironmentVariable(map, PLUGIN_BACKEND, "gcs");
    setMandatoryEnvironmentVariable(map, PLUGIN_BACKEND_OPERATION_TIMEOUT, format("%ss", timeout));

    return map;
  }

  private static String getSTOKey(String value) {
    return SECURITY_ENV_PREFIX + value.toUpperCase();
  }

  private static Map<String, String> processSTOAuthFields(STOYamlAuth authData, String stepType, String identifier) {
    Map<String, String> map = new HashMap<>();

    if (authData != null) {
      STOYamlAuthType authType = authData.getType();

      map.put(getSTOKey("product_auth_type"),
          authType != null ? authType.getYamlName() : STOYamlAuthType.REPOSITORY.getYamlName());
      map.put(getSTOKey("product_domain"),
          resolveStringParameter("auth.domain", stepType, identifier, authData.getDomain(), false));
      map.put(getSTOKey("product_api_version"),
          resolveStringParameter("image.region", stepType, identifier, authData.getVersion(), false));
      map.put(getSTOKey("product_access_id"),
          resolveStringParameter("image.access_id", stepType, identifier, authData.getAccessId(), false));
      map.put(getSTOKey("product_access_token"),
          resolveStringParameter("image.access_token", stepType, identifier, authData.getAccessToken(), false));
    }

    return map;
  }

  private static Map<String, String> processSTOImageFields(STOYamlImage imageData, String stepType, String identifier) {
    Map<String, String> map = new HashMap<>();

    if (imageData != null) {
      STOYamlImageType imageType = imageData.getType();

      map.put(getSTOKey("container_type"),
          imageType != null ? imageType.getYamlName() : STOYamlImageType.DOCKER_V2.getYamlName());
      map.put(getSTOKey("container_domain"),
          resolveStringParameter("image.domain", stepType, identifier, imageData.getDomain(), false));
      map.put(getSTOKey("container_region"),
          resolveStringParameter("image.region", stepType, identifier, imageData.getRegion(), false));
      map.put(getSTOKey("container_access_id"),
          resolveStringParameter("image.access_id", stepType, identifier, imageData.getAccessId(), false));
      map.put(getSTOKey("container_access_token"),
          resolveStringParameter("image.access_token", stepType, identifier, imageData.getAccessToken(), false));
      map.put(getSTOKey("container_image_name"),
          resolveStringParameter("image.name", stepType, identifier, imageData.getName(), false));
      map.put(getSTOKey("container_image_tag"),
          resolveStringParameter("image.tag", stepType, identifier, imageData.getTag(), false));
    }

    return map;
  }

  private static Map<String, String> processSTOInstanceFields(
      STOYamlInstance instanceData, String stepType, String identifier) {
    Map<String, String> map = new HashMap<>();

    if (instanceData != null) {
      map.put(getSTOKey("instance_domain"),
          resolveStringParameter("instance.domain", stepType, identifier, instanceData.getDomain(), false));
      map.put(getSTOKey("instance_path"),
          resolveStringParameter("instance.path", stepType, identifier, instanceData.getPath(), false));
      map.put(getSTOKey("instance_protocol"),
          resolveStringParameter("instance.protocol", stepType, identifier, instanceData.getProtocol(), false));
      map.put(getSTOKey("instance_port"), String.valueOf(resolveIntegerParameter(instanceData.getPort(), 80)));
      map.put(getSTOKey("instance_access_id"),
          resolveStringParameter("instance.access_id", stepType, identifier, instanceData.getAccessId(), false));
      map.put(getSTOKey("instance_access_token"),
          resolveStringParameter("instance.access_token", stepType, identifier, instanceData.getAccessToken(), false));
    }

    return map;
  }

  private static Map<String, String> processSTOTargetFields(STOYamlTarget target, String stepType, String identifier) {
    Map<String, String> map = new HashMap<>();

    if (target != null) {
      Boolean targetSsl = resolveBooleanParameter(target.getSsl(), Boolean.TRUE);

      map.put(getSTOKey("workspace"),
          resolveStringParameter("target.workspace", stepType, identifier, target.getWorkspace(), false));
      map.put(getSTOKey("bypass_ssl_check"), String.valueOf(!targetSsl));

      STOYamlTargetType targetType = target.getType();
      map.put(getSTOKey("scan_type"),
          targetType != null ? targetType.getYamlName() : STOYamlTargetType.REPOSITORY.getYamlName());

      String targetName = resolveStringParameter("target.name", stepType, identifier, target.getName(), true);
      String targetVariant = resolveStringParameter("target.variant", stepType, identifier, target.getVariant(), true);

      switch (target.getType()) {
        case INSTANCE:
          map.put(getSTOKey("instance_identifier"), targetName);
          map.put(getSTOKey("instance_environment"), targetVariant);
          break;
        case REPOSITORY:
          map.put(getSTOKey("repository_project"), targetName);
          map.put(getSTOKey("repository_branch"), targetVariant);
          break;
        case CONTAINER:
          map.put(getSTOKey("container_project"), targetName);
          map.put(getSTOKey("container_tag"), targetVariant);
          break;
        case CONFIGURATION:
          map.put(getSTOKey("configuration_type"), targetName);
          map.put(getSTOKey("configuration_environment"), targetVariant);
          break;
        default:
          break;
      }
    }

    return map;
  }

  private static Map<String, String> processSTOAdvancedSettings(
      STOYamlAdvancedSettings advancedSettings, String stepType, String identifier) {
    Map<String, String> map = new HashMap<>();

    if (advancedSettings != null) {
      STOYamlLog logData = advancedSettings.getLog();
      if (logData != null) {
        STOYamlLogLevel logLevel = logData.getLevel();

        map.put(getSTOKey("log_level"), logLevel != null ? logLevel.getYamlName() : STOYamlLogLevel.INFO.getYamlName());
        map.put(getSTOKey("log_serializer"),
            resolveStringParameter("log.serializer", stepType, identifier, logData.getSerializer(), false));
      }

      STOYamlArgs argsData = advancedSettings.getArgs();
      if (argsData != null) {
        map.put(
            getSTOKey("tool_args"), resolveStringParameter("args.cli", stepType, identifier, argsData.getCli(), false));
        map.put(getSTOKey("tool_passthrough"),
            resolveStringParameter("args.passthrough", stepType, identifier, argsData.getPassthrough(), false));
      }

      map.put(getSTOKey("fail_on_severity"),
          String.valueOf(resolveIntegerParameter(advancedSettings.getFailOnSeverity(), 0)));
      map.put(getSTOKey("include_raw"),
          String.valueOf(resolveBooleanParameter(advancedSettings.getIncludeRaw(), Boolean.TRUE)));

      Boolean advancedSsl = resolveBooleanParameter(advancedSettings.getSsl(), Boolean.TRUE);
      map.put(getSTOKey("verify_ssl"), String.valueOf(!advancedSsl));
    }

    return map;
  }

  private static Map<String, String> processSTOIngestionFields(
      STOYamlIngestion ingestion, String stepType, String identifier) {
    Map<String, String> map = new HashMap<>();

    if (ingestion != null) {
      map.put(getSTOKey("ingestion_file"),
          resolveStringParameter("ingestion.file", stepType, identifier, ingestion.getFile(), false));
    }

    return map;
  }

  private static Map<String, String> processSTOBlackDuckFields(
      BlackDuckStepInfo stepInfo, String stepType, String identifier) {
    Map<String, String> map = new HashMap<>();

    map.putAll(processSTOAuthFields(stepInfo.getAuth(), stepType, identifier));
    map.putAll(processSTOImageFields(stepInfo.getImage(), stepType, identifier));

    STOYamlBlackduckToolData toolData = stepInfo.getTool();

    if (toolData != null) {
      map.put(getSTOKey("product_project_name"),
          resolveStringParameter("tool.project_name", stepType, identifier, toolData.getProjectName(), false));
      map.put(getSTOKey("product_project_version"),
          resolveStringParameter("tool.project_version", stepType, identifier, toolData.getProjectVersion(), false));
    }

    return map;
  }

  private static Map<String, String> processSTOBurpFields(BurpStepInfo stepInfo, String stepType, String identifier) {
    Map<String, String> map = new HashMap<>();

    map.putAll(processSTOInstanceFields(stepInfo.getInstance(), stepType, identifier));

    return map;
  }

  private static Map<String, String> processSTOCheckmarxFields(
      CheckmarxStepInfo stepInfo, String stepType, String identifier) {
    Map<String, String> map = new HashMap<>();

    map.putAll(processSTOAuthFields(stepInfo.getAuth(), stepType, identifier));
    map.putAll(processSTOImageFields(stepInfo.getImage(), stepType, identifier));

    STOYamlCheckmarxToolData toolData = stepInfo.getTool();

    if (toolData != null) {
      map.put(getSTOKey("product_team_name"),
          resolveStringParameter("tool.team_name", stepType, identifier, toolData.getTeamName(), false));
      map.put(getSTOKey("product_project_name"),
          resolveStringParameter("tool.project_name", stepType, identifier, toolData.getProjectName(), false));
    }

    return map;
  }

  private static Map<String, String> processSTOFODFields(
      FortifyOnDemandStepInfo stepInfo, String stepType, String identifier) {
    Map<String, String> map = new HashMap<>();

    map.putAll(processSTOAuthFields(stepInfo.getAuth(), stepType, identifier));
    map.putAll(processSTOImageFields(stepInfo.getImage(), stepType, identifier));

    STOYamlFODToolData toolData = stepInfo.getTool();

    if (toolData != null) {
      map.put(getSTOKey("product_ap_name"),
          resolveStringParameter("tool.app_name", stepType, identifier, toolData.getAppName(), false));
      map.put(getSTOKey("product_audit_type"),
          resolveStringParameter("tool.audit_type", stepType, identifier, toolData.getAuditType(), false));
      map.put(getSTOKey("product_data_center"),
          resolveStringParameter("tool.data_center", stepType, identifier, toolData.getDataCenter(), false));
      map.put(getSTOKey("product_lookup_type"),
          resolveStringParameter("tool.lookup_type", stepType, identifier, toolData.getLoookupType(), false));
      map.put(getSTOKey("product_release_name"),
          resolveStringParameter("tool.release_name", stepType, identifier, toolData.getReleaseName(), false));
      map.put(getSTOKey("product_entitlement"),
          resolveStringParameter("tool.entitlement", stepType, identifier, toolData.getEntitlement(), false));
      map.put(getSTOKey("product_owner_id"),
          resolveStringParameter("tool.owner_id", stepType, identifier, toolData.getOwnerId(), false));
      map.put(getSTOKey("product_scan_settings"),
          resolveStringParameter("tool.scan_settings", stepType, identifier, toolData.getScanSettings(), false));
      map.put(getSTOKey("product_scan_type"),
          resolveStringParameter("tool.scan_type", stepType, identifier, toolData.getScanType(), false));
      map.put(getSTOKey("product_target_language"),
          resolveStringParameter("tool.target_language", stepType, identifier, toolData.getTargetLanguage(), false));
      map.put(getSTOKey("product_target_language_version"),
          resolveStringParameter(
              "tool.target_language_version", stepType, identifier, toolData.getTargetLanguageVersion(), false));
    }

    return map;
  }

  private static Map<String, String> processSTOPrismaCloudFields(
      PrismaCloudStepInfo stepInfo, String stepType, String identifier) {
    Map<String, String> map = new HashMap<>();

    map.putAll(processSTOAuthFields(stepInfo.getAuth(), stepType, identifier));
    map.putAll(processSTOImageFields(stepInfo.getImage(), stepType, identifier));

    return map;
  }

  private static Map<String, String> processSTOSonarqubeFields(
      SonarqubeStepInfo stepInfo, String stepType, String identifier) {
    Map<String, String> map = new HashMap<>();

    map.putAll(processSTOAuthFields(stepInfo.getAuth(), stepType, identifier));

    STOYamlSonarqubeToolData toolData = stepInfo.getTool();

    if (toolData != null) {
      map.put(getSTOKey("product_exclude"),
          resolveStringParameter("tool.exclude", stepType, identifier, toolData.getExclude(), false));
      map.put(getSTOKey("product_include"),
          resolveStringParameter("tool.include", stepType, identifier, toolData.getInclude(), false));

      STOYamlJavaParameters javaParameters = toolData.getJava();

      if (javaParameters != null) {
        map.put(getSTOKey("product_java_binaries"),
            resolveStringParameter("tool.java.binaries", stepType, identifier, javaParameters.getBinaries(), false));
        map.put(getSTOKey("product_java_libraries"),
            resolveStringParameter("tool.java.libraries", stepType, identifier, javaParameters.getLibraries(), false));
      }
    }

    return map;
  }

  private static Map<String, String> processSTOSnykFields(SnykStepInfo stepInfo, String stepType, String identifier) {
    Map<String, String> map = new HashMap<>();

    map.putAll(processSTOAuthFields(stepInfo.getAuth(), stepType, identifier));
    map.putAll(processSTOImageFields(stepInfo.getImage(), stepType, identifier));

    return map;
  }

  private static Map<String, String> processSTOVeracodeFields(
      VeracodeStepInfo stepInfo, String stepType, String identifier) {
    Map<String, String> map = new HashMap<>();

    map.putAll(processSTOAuthFields(stepInfo.getAuth(), stepType, identifier));

    STOYamlVeracodeToolData toolData = stepInfo.getTool();

    if (toolData != null) {
      map.put(getSTOKey("product_app_id"),
          resolveStringParameter("tool.app_id", stepType, identifier, toolData.getAppId(), false));
      map.put(getSTOKey("product_project_name"),
          resolveStringParameter("tool.project_name", stepType, identifier, toolData.getProjectName(), false));
    }

    return map;
  }

  private static Map<String, String> processSTOZapFields(ZapStepInfo stepInfo, String stepType, String identifier) {
    Map<String, String> map = new HashMap<>();

    map.putAll(processSTOInstanceFields(stepInfo.getInstance(), stepType, identifier));

    STOYamlZapToolData toolData = stepInfo.getTool();

    if (toolData != null) {
      map.put(getSTOKey("product_context"),
          resolveStringParameter("tool.context", stepType, identifier, toolData.getContext(), false));
      map.put(getSTOKey("zap_custom_port"), String.valueOf(resolveIntegerParameter(toolData.getPort(), 8080)));
    }

    return map;
  }

  private static Map<String, String> getSecurityStepInfoEnvVariables(SecurityStepInfo stepInfo, String identifier) {
    Map<String, String> map = new HashMap<>();

    Map<String, JsonNode> settings =
        resolveJsonNodeMapParameter("settings", "Security", identifier, stepInfo.getSettings(), false);

    if (stepInfo instanceof STOGenericStepInfo) {
      String stepType = stepInfo.getStepType().getType();
      STOGenericStepInfo stepData = (STOGenericStepInfo) stepInfo;

      STOYamlGenericConfig config = stepData.getConfig();
      STOYamlScanMode scanMode = stepData.getMode();

      map.put(getSTOKey("product_config_name"),
          config != null ? config.getYamlName() : STOYamlGenericConfig.DEFAULT.getYamlName());
      map.put(getSTOKey("policy_type"),
          scanMode != null ? scanMode.getYamlName() : STOYamlScanMode.ORCHESTRATION.getYamlName());

      map.putAll(processSTOTargetFields(stepData.getTarget(), stepType, identifier));
      map.putAll(processSTOAdvancedSettings(stepData.getAdvanced(), stepType, identifier));
      map.putAll(processSTOIngestionFields(stepData.getIngestion(), stepType, identifier));

      if (stepInfo instanceof BlackDuckStepInfo) {
        map.putAll(processSTOBlackDuckFields((BlackDuckStepInfo) stepInfo, stepType, identifier));
      } else if (stepInfo instanceof BurpStepInfo) {
        map.putAll(processSTOBurpFields((BurpStepInfo) stepInfo, stepType, identifier));
      } else if (stepInfo instanceof CheckmarxStepInfo) {
        map.putAll(processSTOCheckmarxFields((CheckmarxStepInfo) stepInfo, stepType, identifier));
      } else if (stepInfo instanceof FortifyOnDemandStepInfo) {
        map.putAll(processSTOFODFields((FortifyOnDemandStepInfo) stepInfo, stepType, identifier));
      } else if (stepInfo instanceof PrismaCloudStepInfo) {
        map.putAll(processSTOPrismaCloudFields((PrismaCloudStepInfo) stepInfo, stepType, identifier));
      } else if (stepInfo instanceof SonarqubeStepInfo) {
        map.putAll(processSTOSonarqubeFields((SonarqubeStepInfo) stepInfo, stepType, identifier));
      } else if (stepInfo instanceof SnykStepInfo) {
        map.putAll(processSTOSnykFields((SnykStepInfo) stepInfo, stepType, identifier));
      } else if (stepInfo instanceof VeracodeStepInfo) {
        map.putAll(processSTOVeracodeFields((VeracodeStepInfo) stepInfo, stepType, identifier));
      } else if (stepInfo instanceof ZapStepInfo) {
        map.putAll(processSTOZapFields((ZapStepInfo) stepInfo, stepType, identifier));
      }
    }
    if (!isEmpty(settings)) {
      for (Map.Entry<String, JsonNode> entry : settings.entrySet()) {
        String key = SECURITY_ENV_PREFIX + entry.getKey().toUpperCase();
        map.put(key, SerializerUtils.convertJsonNodeToString(entry.getKey(), entry.getValue()));
      }
    }

    setMandatoryEnvironmentVariable(map, PLUGIN_STEP_ID, identifier);
    return map;
  }

  private static Map<String, String> getSaveCacheGCSStepInfoEnvVariables(
      SaveCacheGCSStepInfo stepInfo, String identifier, long timeout) {
    Map<String, String> map = new HashMap<>();

    setMandatoryEnvironmentVariable(
        map, PLUGIN_CACHE_KEY, resolveStringParameter("key", "SaveCacheGCS", identifier, stepInfo.getKey(), true));

    setMandatoryEnvironmentVariable(
        map, PLUGIN_BUCKET, resolveStringParameter("bucket", "SaveCacheGCS", identifier, stepInfo.getBucket(), true));

    List<String> sourcePaths =
        resolveListParameter("sourcePaths", "SaveCacheGCS", identifier, stepInfo.getSourcePaths(), true);

    ArchiveFormat archiveFormat = resolveArchiveFormat(stepInfo.getArchiveFormat());
    setMandatoryEnvironmentVariable(map, PLUGIN_ARCHIVE_FORMAT, archiveFormat.toString());

    boolean override = resolveBooleanParameter(stepInfo.getOverride(), false);
    setMandatoryEnvironmentVariable(map, PLUGIN_OVERRIDE, String.valueOf(override));
    setMandatoryEnvironmentVariable(map, PLUGIN_MOUNT, listToStringSlice(sourcePaths));
    setMandatoryEnvironmentVariable(map, PLUGIN_REBUILD, "true");
    setMandatoryEnvironmentVariable(map, PLUGIN_EXIT_CODE, "true");
    setMandatoryEnvironmentVariable(map, PLUGIN_BACKEND, "gcs");
    setMandatoryEnvironmentVariable(map, PLUGIN_BACKEND_OPERATION_TIMEOUT, format("%ss", timeout));

    return map;
  }

  private static Map<String, String> getRestoreCacheS3StepInfoEnvVariables(
      RestoreCacheS3StepInfo stepInfo, String identifier, long timeout) {
    Map<String, String> map = new HashMap<>();

    setMandatoryEnvironmentVariable(
        map, PLUGIN_CACHE_KEY, resolveStringParameter("key", "RestoreCacheS3", identifier, stepInfo.getKey(), true));
    setMandatoryEnvironmentVariable(
        map, PLUGIN_BUCKET, resolveStringParameter("bucket", "RestoreCacheS3", identifier, stepInfo.getBucket(), true));
    setMandatoryEnvironmentVariable(map, PLUGIN_RESTORE, "true");

    String endpoint = resolveStringParameter("endpoint", "RestoreCacheS3", identifier, stepInfo.getEndpoint(), false);
    if (endpoint != null && !endpoint.equals(UNRESOLVED_PARAMETER)) {
      setOptionalEnvironmentVariable(map, PLUGIN_ENDPOINT, endpoint);
    }

    String region = resolveStringParameter("region", "RestoreCacheS3", identifier, stepInfo.getRegion(), true);
    if (region != null && !region.equals(UNRESOLVED_PARAMETER)) {
      setOptionalEnvironmentVariable(map, PLUGIN_REGION, region);
    }

    ArchiveFormat archiveFormat = resolveArchiveFormat(stepInfo.getArchiveFormat());
    setMandatoryEnvironmentVariable(map, PLUGIN_ARCHIVE_FORMAT, archiveFormat.toString());

    boolean pathStyle = resolveBooleanParameter(stepInfo.getPathStyle(), false);
    setOptionalEnvironmentVariable(map, PLUGIN_PATH_STYLE, String.valueOf(pathStyle));

    boolean failIfKeyNotFound = resolveBooleanParameter(stepInfo.getFailIfKeyNotFound(), false);
    setOptionalEnvironmentVariable(map, PLUGIN_FAIL_RESTORE_IF_KEY_NOT_PRESENT, String.valueOf(failIfKeyNotFound));

    setMandatoryEnvironmentVariable(map, PLUGIN_EXIT_CODE, "true");
    setMandatoryEnvironmentVariable(map, PLUGIN_BACKEND, "s3");
    setMandatoryEnvironmentVariable(map, PLUGIN_BACKEND_OPERATION_TIMEOUT, format("%ss", timeout));

    return map;
  }

  private static Map<String, String> getSaveCacheS3StepInfoEnvVariables(
      SaveCacheS3StepInfo stepInfo, String identifier, long timeout) {
    Map<String, String> map = new HashMap<>();

    setMandatoryEnvironmentVariable(
        map, PLUGIN_CACHE_KEY, resolveStringParameter("key", "SaveCacheS3", identifier, stepInfo.getKey(), true));
    setMandatoryEnvironmentVariable(
        map, PLUGIN_BUCKET, resolveStringParameter("bucket", "SaveCacheS3", identifier, stepInfo.getBucket(), true));
    setMandatoryEnvironmentVariable(map, PLUGIN_MOUNT,
        listToStringSlice(
            resolveListParameter("sourcePaths", "SaveCacheS3", identifier, stepInfo.getSourcePaths(), true)));
    setMandatoryEnvironmentVariable(map, PLUGIN_REBUILD, "true");

    String endpoint = resolveStringParameter("endpoint", "SaveCacheS3", identifier, stepInfo.getEndpoint(), false);
    if (endpoint != null && !endpoint.equals(UNRESOLVED_PARAMETER)) {
      setOptionalEnvironmentVariable(map, PLUGIN_ENDPOINT, endpoint);
    }

    String region = resolveStringParameter("region", "SaveCacheS3", identifier, stepInfo.getRegion(), true);
    if (region != null && !region.equals(UNRESOLVED_PARAMETER)) {
      setOptionalEnvironmentVariable(map, PLUGIN_REGION, region);
    }

    ArchiveFormat archiveFormat = resolveArchiveFormat(stepInfo.getArchiveFormat());
    setMandatoryEnvironmentVariable(map, PLUGIN_ARCHIVE_FORMAT, archiveFormat.toString());

    boolean pathStyle = resolveBooleanParameter(stepInfo.getPathStyle(), false);
    setOptionalEnvironmentVariable(map, PLUGIN_PATH_STYLE, String.valueOf(pathStyle));

    boolean override = resolveBooleanParameter(stepInfo.getOverride(), false);
    setMandatoryEnvironmentVariable(map, PLUGIN_OVERRIDE, String.valueOf(override));

    setMandatoryEnvironmentVariable(map, PLUGIN_EXIT_CODE, "true");
    setMandatoryEnvironmentVariable(map, PLUGIN_BACKEND, "s3");
    setMandatoryEnvironmentVariable(map, PLUGIN_BACKEND_OPERATION_TIMEOUT, format("%ss", timeout));

    return map;
  }

  private static Map<String, String> getUploadToArtifactoryStepInfoEnvVariables(
      UploadToArtifactoryStepInfo stepInfo, String identifier) {
    Map<String, String> map = new HashMap<>();

    setMandatoryEnvironmentVariable(map, PLUGIN_SOURCE,
        resolveStringParameter("sourcePath", "ArtifactoryUpload", identifier, stepInfo.getSourcePath(), true));
    setMandatoryEnvironmentVariable(map, PLUGIN_TARGET,
        resolveStringParameter("target", "ArtifactoryUpload", identifier, stepInfo.getTarget(), true));
    setMandatoryEnvironmentVariable(map, PLUGIN_FLAT, "true");
    setOptionalEnvironmentVariable(map, PLUGIN_ARTIFACT_FILE, PLUGIN_ARTIFACT_FILE_VALUE);

    return map;
  }

  private static Map<String, String> getUploadToGCSStepInfoEnvVariables(
      UploadToGCSStepInfo stepInfo, String identifier) {
    Map<String, String> map = new HashMap<>();

    setMandatoryEnvironmentVariable(map, PLUGIN_SOURCE,
        resolveStringParameter("sourcePaths", "GCSUpload", identifier, stepInfo.getSourcePath(), true));
    String target = null;
    String stepInfoBucket = resolveStringParameter("bucket", "GCSUpload", identifier, stepInfo.getBucket(), true);
    String stepInfoTarget = resolveStringParameter("target", "GCSUpload", identifier, stepInfo.getTarget(), false);

    if (stepInfoTarget != null && !stepInfoTarget.equals(UNRESOLVED_PARAMETER)) {
      target = format("%s/%s", trimTrailingCharacter(stepInfoBucket, '/'), trimLeadingCharacter(stepInfoTarget, '/'));
    } else {
      target = format("%s", trimTrailingCharacter(stepInfoBucket, '/'));
    }
    setMandatoryEnvironmentVariable(map, PLUGIN_TARGET, target);
    setOptionalEnvironmentVariable(map, PLUGIN_ARTIFACT_FILE, PLUGIN_ARTIFACT_FILE_VALUE);

    return map;
  }

  private static Map<String, String> getUploadToS3StepInfoEnvVariables(UploadToS3StepInfo stepInfo, String identifier) {
    Map<String, String> map = new HashMap<>();

    setMandatoryEnvironmentVariable(
        map, PLUGIN_BUCKET, resolveStringParameter("bucket", "S3Upload", identifier, stepInfo.getBucket(), true));
    setMandatoryEnvironmentVariable(map, PLUGIN_SOURCE,
        resolveStringParameter("sourcePath", "S3Upload", identifier, stepInfo.getSourcePath(), true));

    String target = resolveStringParameter("target", "S3Upload", identifier, stepInfo.getTarget(), false);
    if (target != null && !target.equals(UNRESOLVED_PARAMETER)) {
      setOptionalEnvironmentVariable(map, PLUGIN_TARGET, target);
    }

    String endpoint = resolveStringParameter("endpoint", "S3Upload", identifier, stepInfo.getEndpoint(), false);
    if (endpoint != null && !endpoint.equals(UNRESOLVED_PARAMETER)) {
      setOptionalEnvironmentVariable(map, PLUGIN_ENDPOINT, endpoint);
    }

    String region = resolveStringParameter("region", "S3Upload", identifier, stepInfo.getRegion(), true);
    if (region != null && !region.equals(UNRESOLVED_PARAMETER)) {
      setOptionalEnvironmentVariable(map, PLUGIN_REGION, region);
    }

    String stripPrefix =
        resolveStringParameter("stripPrefix", "S3Upload", identifier, stepInfo.getStripPrefix(), false);
    if (stripPrefix != null && !stripPrefix.equals(UNRESOLVED_PARAMETER)) {
      setOptionalEnvironmentVariable(map, PLUGIN_STRIP_PREFIX, stripPrefix);
    }

    setOptionalEnvironmentVariable(map, PLUGIN_ARTIFACT_FILE, PLUGIN_ARTIFACT_FILE_VALUE);

    return map;
  }

  private Map<String, String> getGitCloneStepInfoEnvVariables(
      GitCloneStepInfo stepInfo, Ambiance ambiance, String identifier) {
    Map<String, String> map = new HashMap<>();

    final String connectorRef = stepInfo.getConnectorRef().getValue();
    final NGAccess ngAccess = AmbianceUtils.getNgAccess(ambiance);
    final ConnectorDetails gitConnector = codebaseUtils.getGitConnector(ngAccess, connectorRef);

    String repoName = stepInfo.getRepoName().getValue();
    // Overwrite all codebase env variables by setting to blank
    map.putAll(getBlankCodebaseEnvVars());

    map.putAll(getSslVerifyEnvVars(stepInfo.getSslVerify()));
    map.putAll(getGitEnvVars(gitConnector, repoName));
    map.putAll(getBuildEnvVars(ambiance, gitConnector, stepInfo));
    map.putAll(getCloneDirEnvVars(stepInfo.getCloneDirectory(), repoName, map.get(DRONE_REMOTE_URL), identifier));
    map.putAll(getPluginDepthEnvVars(stepInfo.getDepth()));

    return map;
  }

  private static Map<String, String> getBlankCodebaseEnvVars() {
    Map<String, String> map = new HashMap<>();
    map.put(DRONE_TAG, "");
    map.put(DRONE_NETRC_MACHINE, "");
    map.put(DRONE_BUILD_EVENT, "");
    map.put(DRONE_COMMIT_BRANCH, "");
    map.put(DRONE_REMOTE_URL, "");
    map.put(DRONE_COMMIT_SHA, "");
    return map;
  }

  private static Map<String, String> getSslVerifyEnvVars(ParameterField<Boolean> sslVerifyParameter) {
    Map<String, String> map = new HashMap<>();
    boolean sslVerify = resolveBooleanParameter(sslVerifyParameter, true);
    setOptionalEnvironmentVariable(map, GIT_SSL_NO_VERIFY, String.valueOf(!sslVerify));
    return map;
  }

  private Map<String, String> getGitEnvVars(ConnectorDetails gitConnector, String repoName) {
    // Get the Git Connector Reference environment variables
    return codebaseUtils.getGitEnvVariables(gitConnector, repoName);
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
          map.putAll(codebaseUtils.getRuntimeCodebaseVars(ambiance, gitConnector));
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

  private static Map<String, String> getPluginDepthEnvVars(ParameterField<Integer> depthParameter) {
    Map<String, String> map = new HashMap<>();
    Integer depth = GIT_CLONE_MANUAL_DEPTH;
    if (depthParameter != null && depthParameter.getValue() != null) {
      depth = depthParameter.getValue();
    }
    if (depth != null && depth != 0) {
      String pluginDepthKey = PLUGIN_ENV_PREFIX + GIT_CLONE_DEPTH_ATTRIBUTE.toUpperCase();
      map.put(pluginDepthKey, depth.toString());
    }
    return map;
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

  // converts map "key1":"value1","key2":"value2" to string "key1=value1,key2=value2"
  private static String mapToStringSlice(Map<String, String> map) {
    if (isEmpty(map)) {
      return "";
    }
    StringBuilder mapAsString = new StringBuilder();
    for (Map.Entry<String, String> entry : map.entrySet()) {
      mapAsString.append(entry.getKey()).append('=').append(entry.getValue()).append(',');
    }
    mapAsString.deleteCharAt(mapAsString.length() - 1);
    return mapAsString.toString();
  }

  // converts list "value1", "value2" to string "value1,value2"
  private static String listToStringSlice(List<String> stringList) {
    return String.join(",", stringList);
  }

  private static void setOptionalEnvironmentVariable(Map<String, String> envVarMap, String var, String value) {
    if (isEmpty(value)) {
      return;
    }
    envVarMap.put(var, value);
  }

  private static void setMandatoryEnvironmentVariable(Map<String, String> envVarMap, String var, String value) {
    if (isEmpty(value)) {
      throw new InvalidArgumentsException(format("Environment variable %s can't be empty or null", var));
    }
    envVarMap.put(var, value);
  }

  /**
   * Get Build Environment Variables
   * CiExecutionArgs contains codebase tag and branch variables, these are not included in the ciExecutionArgsCopy for
   * the GIT_CLONE step, so they do not conflict with the GIT_CLONE step build env variables
   *
   * @return build environment variables
   */
  public static Map<String, String> getBuildEnvironmentVariables(
      PluginCompatibleStep stepInfo, CIExecutionArgs ciExecutionArgs) {
    CIExecutionArgs ciExecutionArgsCopy = ciExecutionArgs;
    if (GIT_CLONE.equals(stepInfo.getNonYamlInfo().getStepInfoType())) {
      ciExecutionArgsCopy = CIExecutionArgs.builder().runSequence(ciExecutionArgs.getRunSequence()).build();
    }
    return BuildEnvironmentUtils.getBuildEnvironmentVariables(ciExecutionArgsCopy);
  }
}
