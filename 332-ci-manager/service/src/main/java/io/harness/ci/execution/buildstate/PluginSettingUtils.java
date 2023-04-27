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
import static io.harness.beans.serializer.RunTimeInputHandler.resolveJsonNodeMapParameter;
import static io.harness.beans.serializer.RunTimeInputHandler.resolveListParameter;
import static io.harness.beans.serializer.RunTimeInputHandler.resolveMapParameter;
import static io.harness.beans.serializer.RunTimeInputHandler.resolveStringParameter;
import static io.harness.beans.serializer.RunTimeInputHandler.resolveStringParameterV2;
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
import static io.harness.ci.commonconstants.CIExecutionConstants.DOCKER_REGISTRY_V1;
import static io.harness.ci.commonconstants.CIExecutionConstants.DOCKER_REGISTRY_V2;
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
import static io.harness.sto.utils.STOSettingsUtils.getSTOKey;
import static io.harness.sto.utils.STOSettingsUtils.getSTOPluginEnvVariables;

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
import io.harness.beans.steps.stepinfo.security.shared.STOGenericStepInfo;
import io.harness.beans.sweepingoutputs.StageInfraDetails.Type;
import io.harness.beans.yaml.extended.ArchiveFormat;
import io.harness.ci.integrationstage.BuildEnvironmentUtils;
import io.harness.ci.serializer.SerializerUtils;
import io.harness.delegate.beans.ci.pod.ConnectorDetails;
import io.harness.delegate.beans.ci.pod.EnvVariableEnum;
import io.harness.delegate.beans.connector.ConnectorType;
import io.harness.delegate.beans.connector.docker.DockerConnectorDTO;
import io.harness.exception.InvalidArgumentsException;
import io.harness.exception.ngexception.CIStageExecutionException;
import io.harness.exception.ngexception.CIStageExecutionUserException;
import io.harness.ng.core.NGAccess;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.yaml.ParameterField;
import io.harness.ssca.beans.stepinfo.SscaOrchestrationStepInfo;
import io.harness.ssca.execution.SscaOrchestrationPluginUtils;
import io.harness.ssca.execution.orchestration.SscaOrchestrationStepPluginUtils;
import io.harness.yaml.core.variables.SecretNGVariable;
import io.harness.yaml.extended.ci.codebase.Build;
import io.harness.yaml.extended.ci.codebase.BuildType;
import io.harness.yaml.extended.ci.codebase.impl.BranchBuildSpec;
import io.harness.yaml.extended.ci.codebase.impl.PRBuildSpec;
import io.harness.yaml.extended.ci.codebase.impl.TagBuildSpec;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
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
  public static final String PLUGIN_DEBUG = "PLUGIN_DEBUG";
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
  public static final String PLUGIN_DOCKER_REGISTRY = "PLUGIN_DOCKER_REGISTRY";
  public static final String PLUGIN_CACHE_FROM = "PLUGIN_CACHE_FROM";
  public static final String PLUGIN_CACHE_TO = "PLUGIN_CACHE_TO";
  @Inject private CodebaseUtils codebaseUtils;
  @Inject private ConnectorUtils connectorUtils;
  @Inject private SscaOrchestrationPluginUtils sscaOrchestrationPluginUtils;

  public Map<String, String> getPluginCompatibleEnvVariables(PluginCompatibleStep stepInfo, String identifier,
      long timeout, Ambiance ambiance, Type infraType, boolean isMandatory, boolean isContainerizedPlugin) {
    switch (stepInfo.getNonYamlInfo().getStepInfoType()) {
      case ECR:
        return getECRStepInfoEnvVariables(ambiance, (ECRStepInfo) stepInfo, identifier, infraType);
      case ACR:
        return getACRStepInfoEnvVariables((ACRStepInfo) stepInfo, identifier, infraType);
      case GCR:
        return getGCRStepInfoEnvVariables((GCRStepInfo) stepInfo, identifier, infraType);
      case DOCKER:
        return getDockerStepInfoEnvVariables((DockerStepInfo) stepInfo, identifier, infraType, isContainerizedPlugin);
      case UPLOAD_ARTIFACTORY:
        return getUploadToArtifactoryStepInfoEnvVariables((UploadToArtifactoryStepInfo) stepInfo, identifier);
      case UPLOAD_GCS:
        return getUploadToGCSStepInfoEnvVariables((UploadToGCSStepInfo) stepInfo, identifier);
      case UPLOAD_S3:
        return getUploadToS3StepInfoEnvVariables((UploadToS3StepInfo) stepInfo, identifier, isMandatory);
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
      case SSCA_ORCHESTRATION:
        return sscaOrchestrationPluginUtils.getSscaOrchestrationStepEnvVariables(
            (SscaOrchestrationStepInfo) stepInfo, identifier, ambiance);
      default:
        throw new IllegalStateException("Unexpected value: " + stepInfo.getNonYamlInfo().getStepInfoType());
    }
  }

  public Map<String, SecretNGVariable> getPluginCompatibleSecretVars(PluginCompatibleStep step) {
    switch (step.getNonYamlInfo().getStepInfoType()) {
      case SSCA_ORCHESTRATION:
        return SscaOrchestrationPluginUtils.getSscaOrchestrationSecretVars((SscaOrchestrationStepInfo) step);
      default:
        return new HashMap<>();
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
      case SSCA_ORCHESTRATION:
        return SscaOrchestrationStepPluginUtils.getConnectorSecretEnvMap();
      case UPLOAD_ARTIFACTORY:
        map.put(EnvVariableEnum.ARTIFACTORY_ENDPOINT, PLUGIN_URL);
        map.put(EnvVariableEnum.ARTIFACTORY_USERNAME, PLUGIN_USERNAME);
        map.put(EnvVariableEnum.ARTIFACTORY_PASSWORD, PLUGIN_PASSW);
        return map;
      case GIT_CLONE:
        return map;
      case IACM_TERRAFORM:
        map.put(EnvVariableEnum.AWS_ACCESS_KEY, PLUGIN_ACCESS_KEY);
        map.put(EnvVariableEnum.AWS_SECRET_KEY, PLUGIN_SECRET_KEY);
        map.put(EnvVariableEnum.AWS_CROSS_ACCOUNT_ROLE_ARN, PLUGIN_ASSUME_ROLE);
        map.put(EnvVariableEnum.AWS_CROSS_ACCOUNT_EXTERNAL_ID, PLUGIN_EXTERNAL_ID);
        map.put(EnvVariableEnum.AZURE_APP_SECRET, CLIENT_SECRET);
        map.put(EnvVariableEnum.AZURE_APP_ID, CLIENT_ID);
        map.put(EnvVariableEnum.AZURE_TENANT_ID, TENANT_ID);
        map.put(EnvVariableEnum.AZURE_CERT, CLIENT_CERTIFICATE);
        map.put(EnvVariableEnum.GCP_KEY, PLUGIN_JSON_KEY);
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

  private Map<String, String> getECRStepInfoEnvVariables(
      Ambiance ambiance, ECRStepInfo stepInfo, String identifier, Type infraType) {
    NGAccess ngAccess = AmbianceUtils.getNgAccess(ambiance);
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

    List<String> baseImageConnectors = resolveListParameter(
        "baseImageConnectors", "BuildAndPushECR", identifier, stepInfo.getBaseImageConnectorRefs(), false);
    if (isNotEmpty(baseImageConnectors)) {
      // only one baseImageConnector is allowed currently
      ConnectorDetails connectorDetails = connectorUtils.getConnectorDetails(ngAccess, baseImageConnectors.get(0));
      if (connectorDetails != null && connectorDetails.getConnectorType() == ConnectorType.DOCKER) {
        String dockerConnectorUrl = ((DockerConnectorDTO) connectorDetails.getConnectorConfig()).getDockerRegistryUrl();
        if (isNotEmpty(dockerConnectorUrl)) {
          if (DOCKER_REGISTRY_V2.equals(dockerConnectorUrl)) {
            dockerConnectorUrl = DOCKER_REGISTRY_V1;
          }
          setMandatoryEnvironmentVariable(map, PLUGIN_DOCKER_REGISTRY, dockerConnectorUrl);
        }
      }
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
      DockerStepInfo stepInfo, String identifier, Type infraType, boolean isContainerizedPlugin) {
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
      if (!isContainerizedPlugin) {
        // Only populate cache-from and cache-to if we're using the buildx plugin
        List<String> cacheFromList =
            resolveListParameter("cacheFrom", "BuildAndPushDockerRegistry", identifier, stepInfo.getCacheFrom(), false);
        if (!isEmpty(cacheFromList)) {
          setOptionalEnvironmentVariable(map, PLUGIN_CACHE_FROM, listToCustomStringSlice(cacheFromList));
        }
        String cacheTo =
            resolveStringParameterV2("cacheTo", "BuildAndPushDockerRegistry", identifier, stepInfo.getCacheTo(), false);
        if (!isEmpty(cacheTo)) {
          setOptionalEnvironmentVariable(map, PLUGIN_CACHE_TO, cacheTo);
        }
      }
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
  private static Map<String, String> getSecurityStepInfoEnvVariables(SecurityStepInfo stepInfo, String identifier) {
    Map<String, String> map = new HashMap<>();

    Map<String, JsonNode> settings =
        resolveJsonNodeMapParameter("settings", "Security", identifier, stepInfo.getSettings(), false);

    if (stepInfo instanceof STOGenericStepInfo) {
      map.putAll(getSTOPluginEnvVariables((STOGenericStepInfo) stepInfo, identifier));
    }
    if (!isEmpty(settings)) {
      for (Map.Entry<String, JsonNode> entry : settings.entrySet()) {
        map.put(getSTOKey(entry.getKey()), SerializerUtils.convertJsonNodeToString(entry.getKey(), entry.getValue()));
      }
    }

    setMandatoryEnvironmentVariable(map, PLUGIN_STEP_ID, identifier);
    map.values().removeAll(Collections.singleton(null));
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

  private static Map<String, String> getUploadToS3StepInfoEnvVariables(
      UploadToS3StepInfo stepInfo, String identifier, boolean isMandatory) {
    Map<String, String> map = new HashMap<>();

    setMandatoryEnvironmentVariable(map, PLUGIN_BUCKET,
        resolveStringParameterV2("bucket", "S3Upload", identifier, stepInfo.getBucket(), isMandatory));
    setMandatoryEnvironmentVariable(map, PLUGIN_SOURCE,
        resolveStringParameterV2("sourcePath", "S3Upload", identifier, stepInfo.getSourcePath(), isMandatory));

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
    if (!sslVerify) {
      // Set GIT_SSL_NO_VERIFY=true only when ssl verify is false
      setOptionalEnvironmentVariable(map, GIT_SSL_NO_VERIFY, String.valueOf(!sslVerify));
    }
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
      String pluginDepthKey = PLUGIN_ENV_PREFIX + GIT_CLONE_DEPTH_ATTRIBUTE.toUpperCase(Locale.ROOT);
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

  // converts list "value1", "value2" to string "value1;value2"
  private static String listToCustomStringSlice(List<String> stringList) {
    return String.join(";", stringList);
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
