/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.stateutils.buildstate;

import static io.harness.beans.serializer.RunTimeInputHandler.UNRESOLVED_PARAMETER;
import static io.harness.beans.serializer.RunTimeInputHandler.resolveArchiveFormat;
import static io.harness.beans.serializer.RunTimeInputHandler.resolveBooleanParameter;
import static io.harness.beans.serializer.RunTimeInputHandler.resolveJsonNodeMapParameter;
import static io.harness.beans.serializer.RunTimeInputHandler.resolveListParameter;
import static io.harness.beans.serializer.RunTimeInputHandler.resolveMapParameter;
import static io.harness.beans.serializer.RunTimeInputHandler.resolveStringParameter;
import static io.harness.common.CIExecutionConstants.AWS_ROLE_ARN;
import static io.harness.common.CIExecutionConstants.PLUGIN_ACCESS_KEY;
import static io.harness.common.CIExecutionConstants.PLUGIN_ARTIFACT_FILE_VALUE;
import static io.harness.common.CIExecutionConstants.PLUGIN_ASSUME_ROLE;
import static io.harness.common.CIExecutionConstants.PLUGIN_JSON_KEY;
import static io.harness.common.CIExecutionConstants.PLUGIN_PASSW;
import static io.harness.common.CIExecutionConstants.PLUGIN_SECRET_KEY;
import static io.harness.common.CIExecutionConstants.PLUGIN_URL;
import static io.harness.common.CIExecutionConstants.PLUGIN_USERNAME;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import static java.lang.String.format;
import static org.springframework.util.StringUtils.trimLeadingCharacter;
import static org.springframework.util.StringUtils.trimTrailingCharacter;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.plugin.compatible.PluginCompatibleStep;
import io.harness.beans.serializer.RunTimeInputHandler;
import io.harness.beans.steps.CIStepInfoType;
import io.harness.beans.steps.stepinfo.DockerStepInfo;
import io.harness.beans.steps.stepinfo.ECRStepInfo;
import io.harness.beans.steps.stepinfo.GCRStepInfo;
import io.harness.beans.steps.stepinfo.RestoreCacheGCSStepInfo;
import io.harness.beans.steps.stepinfo.RestoreCacheS3StepInfo;
import io.harness.beans.steps.stepinfo.SaveCacheGCSStepInfo;
import io.harness.beans.steps.stepinfo.SaveCacheS3StepInfo;
import io.harness.beans.steps.stepinfo.SecurityStepInfo;
import io.harness.beans.steps.stepinfo.UploadToArtifactoryStepInfo;
import io.harness.beans.steps.stepinfo.UploadToGCSStepInfo;
import io.harness.beans.steps.stepinfo.UploadToS3StepInfo;
import io.harness.beans.sweepingoutputs.StageInfraDetails.Type;
import io.harness.beans.yaml.extended.ArchiveFormat;
import io.harness.ci.serializer.SerializerUtils;
import io.harness.delegate.beans.ci.pod.EnvVariableEnum;
import io.harness.exception.InvalidArgumentsException;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.experimental.UtilityClass;

@UtilityClass
@OwnedBy(HarnessTeam.CI)
public class PluginSettingUtils {
  public static final String PLUGIN_REGISTRY = "PLUGIN_REGISTRY";
  public static final String PLUGIN_REPO = "PLUGIN_REPO";
  public static final String PLUGIN_TAGS = "PLUGIN_TAGS";
  public static final String PLUGIN_DOCKERFILE = "PLUGIN_DOCKERFILE";
  public static final String PLUGIN_CONTEXT = "PLUGIN_CONTEXT";
  public static final String PLUGIN_TARGET = "PLUGIN_TARGET";
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
  public static final String PLUGIN_BACKEND = "PLUGIN_BACKEND";
  public static final String PLUGIN_OVERRIDE = "PLUGIN_OVERRIDE";
  public static final String PLUGIN_ARCHIVE_FORMAT = "PLUGIN_ARCHIVE_FORMAT";
  public static final String PLUGIN_ARTIFACT_FILE = "PLUGIN_ARTIFACT_FILE";
  public static final String PLUGIN_DAEMON_OFF = "PLUGIN_DAEMON_OFF";
  public static final String ECR_REGISTRY_PATTERN = "%s.dkr.ecr.%s.amazonaws.com";

  public static Map<String, String> getPluginCompatibleEnvVariables(
      PluginCompatibleStep stepInfo, String identifier, long timeout, Type infraType) {
    switch (stepInfo.getNonYamlInfo().getStepInfoType()) {
      case ECR:
        return getECRStepInfoEnvVariables((ECRStepInfo) stepInfo, identifier, infraType);
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
      default:
        throw new IllegalStateException("Unexpected value: " + stepInfo.getNonYamlInfo().getStepInfoType());
    }
  }

  public String getConnectorRef(PluginCompatibleStep stepInfo) {
    String stepType = stepInfo.getNonYamlInfo().getStepInfoType().getDisplayName();
    return RunTimeInputHandler.resolveStringParameter(
        "connectorRef", stepType, stepInfo.getIdentifier(), stepInfo.getConnectorRef(), true);
  }

  public List<String> getBaseImageConnectorRefs(PluginCompatibleStep stepInfo) {
    String stepType = stepInfo.getNonYamlInfo().getStepInfoType().getDisplayName();
    return RunTimeInputHandler.resolveListParameter(
        "baseImageConnectorRefs", stepType, stepInfo.getIdentifier(), stepInfo.getBaseImageConnectorRefs(), false);
  }

  public Map<EnvVariableEnum, String> getConnectorSecretEnvMap(CIStepInfoType stepInfoType) {
    Map<EnvVariableEnum, String> map = new HashMap<>();
    switch (stepInfoType) {
      case ECR:
        map.put(EnvVariableEnum.AWS_ACCESS_KEY, PLUGIN_ACCESS_KEY);
        map.put(EnvVariableEnum.AWS_SECRET_KEY, PLUGIN_SECRET_KEY);
        map.put(EnvVariableEnum.AWS_CROSS_ACCOUNT_ROLE_ARN, AWS_ROLE_ARN);
        return map;
      case RESTORE_CACHE_S3:
      case SAVE_CACHE_S3:
      case UPLOAD_S3:
        map.put(EnvVariableEnum.AWS_ACCESS_KEY, PLUGIN_ACCESS_KEY);
        map.put(EnvVariableEnum.AWS_SECRET_KEY, PLUGIN_SECRET_KEY);
        map.put(EnvVariableEnum.AWS_CROSS_ACCOUNT_ROLE_ARN, PLUGIN_ASSUME_ROLE);
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

  private static Map<String, String> getSecurityStepInfoEnvVariables(SecurityStepInfo stepInfo, String identifier) {
    Map<String, String> map = new HashMap<>();

    Map<String, JsonNode> settings =
        resolveJsonNodeMapParameter("settings", "Security", identifier, stepInfo.getSettings(), false);

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

  // converts map "key1":"value1","key2":"value2" to string "key1=value1,key2=value2"
  private String mapToStringSlice(Map<String, String> map) {
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
  private String listToStringSlice(List<String> stringList) {
    if (isEmpty(stringList)) {
      return "";
    }
    StringBuilder listAsString = new StringBuilder();
    for (String value : stringList) {
      listAsString.append(value).append(',');
    }
    listAsString.deleteCharAt(listAsString.length() - 1);
    return listAsString.toString();
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
}
