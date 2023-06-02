/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.sm.states;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.beans.ExecutionStatus.FAILED;
import static io.harness.beans.ExecutionStatus.SUCCESS;
import static io.harness.beans.FeatureName.ADD_MANIFEST_COLLECTION_STEP;
import static io.harness.beans.FeatureName.ARTIFACT_COLLECTION_CONFIGURABLE;
import static io.harness.beans.FeatureName.SAVE_ARTIFACT_TO_DB;
import static io.harness.beans.FeatureName.SORT_ARTIFACTS_IN_UPDATED_ORDER;
import static io.harness.beans.FeatureName.SPG_CG_TIMEOUT_FAILURE_AT_WORKFLOW;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.validation.Validator.notNullCheck;

import static software.wings.beans.CGConstants.GLOBAL_APP_ID;
import static software.wings.beans.artifact.ArtifactStreamType.CUSTOM;
import static software.wings.sm.StateType.ARTIFACT_COLLECTION;

import static java.lang.String.valueOf;
import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static org.apache.commons.lang3.StringUtils.isBlank;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.beans.Cd1SetupFields;
import io.harness.beans.DelegateTask;
import io.harness.beans.DelegateTask.DelegateTaskBuilder;
import io.harness.beans.ExecutionStatus;
import io.harness.beans.FeatureName;
import io.harness.delay.DelayEventHelper;
import io.harness.delegate.beans.DelegateResponseData;
import io.harness.delegate.beans.TaskData;
import io.harness.delegate.task.manifests.request.ManifestCollectionParams;
import io.harness.exception.ExceptionUtils;
import io.harness.exception.FailureType;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.WingsException;
import io.harness.expression.ExpressionEvaluator;
import io.harness.ff.FeatureFlagService;
import io.harness.logging.AutoLogContext;
import io.harness.logging.AutoLogContext.OverrideBehavior;
import io.harness.logging.CommandExecutionStatus;
import io.harness.tasks.ResponseData;

import software.wings.api.AppManifestCollectionExecutionData;
import software.wings.api.ArtifactCollectionExecutionData;
import software.wings.beans.BuildExecutionSummary;
import software.wings.beans.CollectionEntityType;
import software.wings.beans.EntityType;
import software.wings.beans.SettingAttribute;
import software.wings.beans.TaskType;
import software.wings.beans.TemplateExpression;
import software.wings.beans.appmanifest.ApplicationManifest;
import software.wings.beans.appmanifest.HelmChart;
import software.wings.beans.artifact.ArtifactMetadataKeys;
import software.wings.beans.artifact.ArtifactStream;
import software.wings.beans.artifact.ArtifactStreamAttributes;
import software.wings.beans.artifact.CustomArtifactStream;
import software.wings.common.TemplateExpressionProcessor;
import software.wings.delegatetasks.buildsource.BuildCollectParameters;
import software.wings.delegatetasks.buildsource.BuildSourceExecutionResponse;
import software.wings.delegatetasks.buildsource.BuildSourceParameters;
import software.wings.delegatetasks.buildsource.BuildSourceParameters.BuildSourceRequestType;
import software.wings.delegatetasks.buildsource.BuildSourceResponse;
import software.wings.helpers.ext.helm.request.HelmChartCollectionParams;
import software.wings.helpers.ext.helm.response.HelmCollectChartResponse;
import software.wings.helpers.ext.jenkins.BuildDetails;
import software.wings.persistence.artifact.Artifact;
import software.wings.service.ArtifactStreamHelper;
import software.wings.service.impl.ShellScriptUtils;
import software.wings.service.impl.WorkflowExecutionLogContext;
import software.wings.service.impl.applicationmanifest.ManifestCollectionUtils;
import software.wings.service.impl.artifact.ArtifactCollectionUtils;
import software.wings.service.intfc.ApplicationManifestService;
import software.wings.service.intfc.ArtifactService;
import software.wings.service.intfc.ArtifactStreamService;
import software.wings.service.intfc.BuildSourceService;
import software.wings.service.intfc.DelegateService;
import software.wings.service.intfc.SettingsService;
import software.wings.service.intfc.WorkflowExecutionService;
import software.wings.service.intfc.applicationmanifest.HelmChartService;
import software.wings.sm.ExecutionContext;
import software.wings.sm.ExecutionResponse;
import software.wings.sm.State;
import software.wings.stencils.DefaultValue;
import software.wings.utils.MappingUtils;

import com.github.reinert.jjschema.Attributes;
import com.github.reinert.jjschema.SchemaIgnore;
import com.google.inject.Inject;
import dev.morphia.annotations.Transient;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.FieldNameConstants;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(CDC)
@Slf4j
@TargetModule(HarnessModule._870_CG_ORCHESTRATION)
@FieldNameConstants(innerTypeName = "ArtifactCollectionStateKeys")
public class ArtifactCollectionState extends State {
  @Attributes(title = "Entity Type") @Getter @Setter private EntityType entityType;
  @Attributes(title = "Entity") @Getter @Setter private String entityId;
  @Attributes(title = "Service") @Getter @Setter private String serviceId;
  @Attributes(title = "Artifact Variable Name") @Getter @Setter private String artifactVariableName;

  @Attributes(title = "Artifact Source") @Getter @Setter private String artifactStreamId;
  @Attributes(title = "Source Type") @Getter @Setter private CollectionEntityType sourceType;
  @Attributes(title = "Application Manifest") @Getter @Setter private String appManifestId;

  @Attributes(title = "Regex") @Getter @Setter private boolean regex;
  @Attributes(title = "Build / Tag") @Getter @Setter private String buildNo;

  @SchemaIgnore private Map<String, Object> runtimeValues;

  @Inject private transient ArtifactStreamService artifactStreamService;
  @Inject private transient ApplicationManifestService applicationManifestService;
  @Inject private transient ArtifactService artifactService;
  @Inject private transient HelmChartService helmChartService;
  @Inject private transient WorkflowExecutionService workflowExecutionService;
  @Inject private transient DelayEventHelper delayEventHelper;
  @Inject private transient FeatureFlagService featureFlagService;
  @Inject private transient ArtifactStreamHelper artifactStreamHelper;
  @Inject private transient ExecutorService executorService;
  @Inject private transient BuildSourceService buildSourceService;
  @Inject private transient ArtifactCollectionUtils artifactCollectionUtils;

  @Inject private transient ManifestCollectionUtils manifestCollectionUtils;
  @Inject private transient TemplateExpressionProcessor templateExpressionProcessor;
  @Inject private transient SettingsService settingsService;
  @Inject @Transient private DelegateService delegateService;

  private static int DELAY_TIME_IN_SEC = 60;
  public static final long DEFAULT_ARTIFACT_COLLECTION_STATE_TIMEOUT_MILLIS = 5L * 60L * 1000L; // 5 minutes

  public ArtifactCollectionState(String name) {
    super(name, ARTIFACT_COLLECTION.name());
  }

  @Override
  public ExecutionResponse execute(ExecutionContext context) {
    try (AutoLogContext ignore =
             new WorkflowExecutionLogContext(context.getWorkflowExecutionId(), OverrideBehavior.OVERRIDE_ERROR)) {
      return executeInternal(context);
    } catch (WingsException e) {
      throw e;
    } catch (Exception e) {
      throw new InvalidRequestException(ExceptionUtils.getMessage(e), e);
    }
  }

  private ExecutionResponse executeArtifact(ExecutionContext context) {
    if (isNotEmpty(getTemplateExpressions())) {
      resolveArtifactStreamId(context);
    }

    ArtifactStream artifactStream = artifactStreamService.get(artifactStreamId);
    if (artifactStream == null) {
      artifactStream = artifactStreamService.fetchByArtifactSourceVariableValue(context.getAppId(), artifactStreamId);
      if (artifactStream != null && artifactStream.isArtifactStreamParameterized()
          && isNotEmpty(getTemplateExpressions())) {
        log.info("Artifact Stream {} is Parameterized", artifactStreamId);
        return ExecutionResponse.builder()
            .executionStatus(ExecutionStatus.FAILED)
            .errorMessage("Parameterized Artifact Source " + artifactStream.getName()
                + " cannot be used as a value for templatized artifact variable")
            .build();
      }
    }
    if (artifactStream == null) {
      log.info("Artifact Stream {} might have been deleted", artifactStreamId);
      return ExecutionResponse.builder()
          .executionStatus(ExecutionStatus.FAILED)
          .errorMessage("Artifact source might have been deleted. Please update with the right artifact source.")
          .build();
    }

    String evaluatedBuildNo;

    if (artifactStream.isArtifactStreamParameterized()) {
      if (isEmpty(runtimeValues)) {
        log.info("Artifact Source {} parameterized. However, runtime values not provided", artifactStream.getName());
        return ExecutionResponse.builder()
            .executionStatus(ExecutionStatus.FAILED)
            .errorMessage("Artifact source parameterized. Please provide runtime values.")
            .build();
      }
      if (isBlank(buildNo)) {
        log.info("Artifact Source {} parameterized. However, Build Number not provided", artifactStream.getName());
        return ExecutionResponse.builder()
            .executionStatus(ExecutionStatus.FAILED)
            .errorMessage("Artifact source parameterized. Please provide Build Number.")
            .build();
      }
    }

    if (shouldCollectArtifact(context)) {
      return collectArtifact(context, artifactStream);
    }

    Artifact lastCollectedArtifact;
    if (artifactStream.isArtifactStreamParameterized()) {
      evaluatedBuildNo = context.renderExpression(buildNo);
      populateRuntimeValuesAndArtifactStreamParameters(context, artifactStream, evaluatedBuildNo);
      lastCollectedArtifact = fetchCollectedArtifactForParameterizedArtifactStream(artifactStream, evaluatedBuildNo);
      if (lastCollectedArtifact == null) {
        BuildDetails buildDetails = buildSourceService.getBuild(
            artifactStream.getAppId(), artifactStreamId, artifactStream.getSettingId(), runtimeValues);
        if (buildDetails == null) {
          log.info("Failed to get Build Number {} for Artifact stream {}", evaluatedBuildNo, artifactStream.getName());
          return ExecutionResponse.builder()
              .executionStatus(ExecutionStatus.FAILED)
              .errorMessage(
                  "Failed to get Build Number " + evaluatedBuildNo + " for Artifact Source " + artifactStream.getName())
              .build();
        }
        lastCollectedArtifact = artifactService.create(
            artifactCollectionUtils.getArtifact(artifactStream, buildDetails), artifactStream, false);
      }
    } else {
      evaluatedBuildNo = getEvaluatedBuildNo(context);
      lastCollectedArtifact = fetchCollectedArtifact(artifactStream, evaluatedBuildNo);
    }

    if (lastCollectedArtifact != null) {
      return prepareResponseForLastCollectedArtifact(context, artifactStream, lastCollectedArtifact);
    }

    ArtifactCollectionExecutionData artifactCollectionExecutionData =
        ArtifactCollectionExecutionData.builder()
            .timeout(getTimeoutMillis() != null ? valueOf(getTimeoutMillis()) : null)
            .artifactSource(artifactStream.getSourceName())
            .buildNo(evaluatedBuildNo)
            .message(String.format("Waiting for [%s] to be collected from [%s] repository",
                evaluatedBuildNo == null ? "latest artifact" : evaluatedBuildNo,
                artifactStream.getArtifactStreamType()))
            .build();

    String resumeId = delayEventHelper.delay(60, Collections.emptyMap());

    return ExecutionResponse.builder()
        .async(true)
        .correlationIds(singletonList(resumeId))
        .stateExecutionData(artifactCollectionExecutionData)
        .build();
  }

  private void populateRuntimeValuesAndArtifactStreamParameters(
      ExecutionContext context, ArtifactStream artifactStream, String evaluatedBuildNo) {
    runtimeValues.put("buildNo", evaluatedBuildNo);
    runtimeValues.replaceAll((k, v) -> context.renderExpression((String) runtimeValues.get(k)));
    artifactStreamHelper.resolveArtifactStreamRuntimeValues(artifactStream, runtimeValues);
    artifactStream.setSourceName(artifactStream.generateSourceName());
  }

  private ExecutionResponse prepareResponseForLastCollectedArtifact(
      ExecutionContext context, ArtifactStream artifactStream, Artifact lastCollectedArtifact) {
    ArtifactCollectionExecutionData artifactCollectionExecutionData =
        ArtifactCollectionExecutionData.builder().artifactStreamId(artifactStreamId).build();
    if (getTimeoutMillis() != null) {
      artifactCollectionExecutionData.setTimeout(valueOf(getTimeoutMillis()));
    }
    artifactCollectionExecutionData.setArtifactSource(artifactStream.getSourceName());
    artifactCollectionExecutionData.setRevision(lastCollectedArtifact.getRevision());
    artifactCollectionExecutionData.setBuildNo(lastCollectedArtifact.getBuildNo());
    artifactCollectionExecutionData.setMetadata(lastCollectedArtifact.getMetadata());
    artifactCollectionExecutionData.setArtifactId(lastCollectedArtifact.getUuid());
    updateArtifactCollectionExecutionData(context, artifactCollectionExecutionData);

    addBuildExecutionSummary(context, artifactCollectionExecutionData, artifactStream);
    return ExecutionResponse.builder()
        .executionStatus(ExecutionStatus.SUCCESS)
        .stateExecutionData(artifactCollectionExecutionData)
        .errorMessage("Collected artifact [" + lastCollectedArtifact.getBuildNo() + "] for artifact source ["
            + lastCollectedArtifact.getArtifactSourceName() + "]")
        .build();
  }

  private boolean shouldCollectArtifact(ExecutionContext context) {
    return featureFlagService.isEnabled(ARTIFACT_COLLECTION_CONFIGURABLE, context.getAccountId())
        || featureFlagService.isEnabled(SORT_ARTIFACTS_IN_UPDATED_ORDER, context.getAccountId());
  }

  private boolean shouldCollectManifest(ExecutionContext context) {
    return featureFlagService.isEnabled(ADD_MANIFEST_COLLECTION_STEP, context.getAccountId());
  }

  private ExecutionResponse collectArtifact(ExecutionContext context, ArtifactStream artifactStream) {
    String evaluatedBuildNo = getEvaluatedBuildNo(context);
    String waitId = generateUuid();
    boolean isTimeoutFailureSupported =
        featureFlagService.isEnabled(SPG_CG_TIMEOUT_FAILURE_AT_WORKFLOW, context.getAccountId());

    // if collection enabled and buildno is empty, get last collected artifact from db and return.
    if (!Boolean.FALSE.equals(artifactStream.getCollectionEnabled()) && (isBlank(evaluatedBuildNo) || isRegex())) {
      Artifact lastCollectedArtifact = fetchCollectedArtifact(artifactStream, evaluatedBuildNo);
      if (lastCollectedArtifact != null) {
        return prepareResponseForLastCollectedArtifact(context, artifactStream, lastCollectedArtifact);
      }
    }

    if (!artifactStream.isArtifactStreamParameterized()
        && featureFlagService.isEnabled(FeatureName.SPG_FETCH_ARTIFACT_FROM_DB, context.getAccountId())) {
      Artifact lastCollectedArtifact = fetchCollectedArtifact(artifactStream, evaluatedBuildNo);
      if (lastCollectedArtifact != null) {
        return prepareResponseForLastCollectedArtifact(context, artifactStream, lastCollectedArtifact);
      }
    }

    Integer timeout = getTimeoutMillis();
    DelegateTaskBuilder delegateTaskBuilder;

    if (CUSTOM.name().equals(artifactStream.getArtifactStreamType())) {
      CustomArtifactStream customArtifactStream = (CustomArtifactStream) artifactStream;
      CustomArtifactStream.Script versionScript =
          customArtifactStream.getScripts()
              .stream()
              .filter(script
                  -> script.getAction() == null || script.getAction() == CustomArtifactStream.Action.FETCH_VERSIONS)
              .findFirst()
              .orElse(CustomArtifactStream.Script.builder().build());
      if (Boolean.FALSE.equals(artifactStream.getCollectionEnabled())
          && ShellScriptUtils.isNoopScript(versionScript.getScriptString())) {
        return saveCustomArtifactResponse(customArtifactStream, evaluatedBuildNo, timeout);
      }
      ArtifactStreamAttributes artifactStreamAttributes =
          artifactCollectionUtils.renderCustomArtifactScriptString((CustomArtifactStream) artifactStream);
      artifactStreamAttributes.setCustomScriptTimeout(valueOf(timeout));
      delegateTaskBuilder = artifactCollectionUtils.fetchCustomDelegateTask(waitId, artifactStream,
          artifactStreamAttributes, false, BuildSourceRequestType.GET_BUILD,
          BuildCollectParameters.builder().buildNo(evaluatedBuildNo).isRegex(regex).build());
    } else {
      SettingAttribute settingAttribute = settingsService.get(artifactStream.getSettingId());
      if (settingAttribute == null) {
        return ExecutionResponse.builder()
            .executionStatus(ExecutionStatus.FAILED)
            .errorMessage("Artifact server was deleted for Artifact Source " + artifactStream.getName())
            .build();
      }

      if (artifactStream.isArtifactStreamParameterized()) {
        evaluatedBuildNo = context.renderExpression(buildNo);
        populateRuntimeValuesAndArtifactStreamParameters(context, artifactStream, evaluatedBuildNo);
      }

      BuildSourceParameters buildSourceRequest =
          artifactCollectionUtils.getBuildSourceParameters(artifactStream, settingAttribute, false, false);
      buildSourceRequest.setBuildSourceRequestType(BuildSourceRequestType.GET_BUILD);
      buildSourceRequest.setShouldFetchSecretFromCache(false);
      buildSourceRequest.setTimeoutSupported(isTimeoutFailureSupported);
      buildSourceRequest.setTimeout(getTimeoutMillis());
      buildSourceRequest.setBuildCollectParameters(
          BuildCollectParameters.builder()
              .buildNo(evaluatedBuildNo)
              .isRegex(regex)
              .delegateSelectors(new HashSet<>(settingsService.getDelegateSelectors(settingAttribute)))
              .build());
      delegateTaskBuilder = DelegateTask.builder()
                                .accountId(settingAttribute.getAccountId())
                                .waitId(waitId)
                                .expiry(artifactCollectionUtils.getDelegateQueueTimeout(artifactStream.getAccountId()))
                                .setupAbstraction(Cd1SetupFields.APP_ID_FIELD,
                                    featureFlagService.isEnabled(
                                        FeatureName.ARTIFACT_STREAM_DELEGATE_SCOPING, artifactStream.getAccountId())
                                        ? artifactStream.getAppId()
                                        : GLOBAL_APP_ID)
                                .data(TaskData.builder()
                                          .async(true)
                                          .taskType(TaskType.BUILD_SOURCE_TASK.name())
                                          .parameters(new Object[] {buildSourceRequest})
                                          .timeout(timeout)
                                          .build());
    }

    String delegateTaskId = delegateService.queueTaskV2(delegateTaskBuilder.build());

    ArtifactCollectionExecutionData artifactCollectionExecutionData =
        ArtifactCollectionExecutionData.builder()
            .timeout(valueOf(timeout))
            .artifactSource(artifactStream.getSourceName())
            .buildNo(evaluatedBuildNo)
            .build();

    return ExecutionResponse.builder()
        .async(true)
        .correlationIds(singletonList(waitId))
        .stateExecutionData(artifactCollectionExecutionData)
        .delegateTaskId(delegateTaskId)
        .build();
  }

  private ExecutionResponse saveCustomArtifactResponse(
      CustomArtifactStream customArtifactStream, String buildNo, Integer timeout) {
    BuildDetails buildDetails = BuildDetails.Builder.aBuildDetails().withNumber(buildNo).build();

    Artifact artifact = artifactService.create(artifactCollectionUtils.getArtifact(customArtifactStream, buildDetails));
    ArtifactCollectionExecutionData artifactCollectionExecutionData =
        ArtifactCollectionExecutionData.builder()
            .timeout(valueOf(timeout))
            .artifactSource(customArtifactStream.getSourceName())
            .buildNo(buildNo)
            .artifactId(artifact.getUuid())
            .artifactSource(customArtifactStream.getSourceName())
            .build();
    return ExecutionResponse.builder()
        .executionStatus(SUCCESS)
        .stateExecutionData(artifactCollectionExecutionData)
        .build();
  }

  private ExecutionResponse executeManifest(ExecutionContext context) {
    if (isNotEmpty(getTemplateExpressions())) {
      resolveAppManifestId(context);
    }
    String appId = context.getAppId();
    ApplicationManifest applicationManifest = applicationManifestService.getById(appId, appManifestId);
    if (applicationManifest == null) {
      log.info("Application manifest {} might have been deleted", appManifestId);
      return ExecutionResponse.builder()
          .executionStatus(ExecutionStatus.FAILED)
          .errorMessage(
              "Application Manifest might have been deleted. Please update with the right application manifest source.")
          .build();
    }
    String evaluatedBuildNo;
    HelmChart lastCollectedHelmChart;
    evaluatedBuildNo = getEvaluatedBuildNo(context);

    if (shouldCollectManifest(context)) {
      return collectManifest(context, applicationManifest, evaluatedBuildNo);
    }

    lastCollectedHelmChart = fetchCollectedAppManifest(applicationManifest, evaluatedBuildNo);

    if (lastCollectedHelmChart != null) {
      return prepareExecutionResponseForLastCollectedHelmChart(context, applicationManifest, lastCollectedHelmChart);
    }

    AppManifestCollectionExecutionData appManifestCollectionData =
        AppManifestCollectionExecutionData.builder()
            .timeout(getTimeoutMillis() != null ? valueOf(getTimeoutMillis()) : null)
            .appManifestId(appManifestId)
            .buildNo(evaluatedBuildNo)
            .message(String.format("Waiting for [%s] to be collected from [%s] application manifest",
                evaluatedBuildNo == null ? "latest manifest" : evaluatedBuildNo, applicationManifest.getName()))
            .build();

    String resumeId = delayEventHelper.delay(60, Collections.emptyMap());

    return ExecutionResponse.builder()
        .async(true)
        .correlationIds(singletonList(resumeId))
        .stateExecutionData(appManifestCollectionData)
        .build();
  }

  private ExecutionResponse prepareExecutionResponseForLastCollectedHelmChart(
      ExecutionContext context, ApplicationManifest applicationManifest, HelmChart lastCollectedHelmChart) {
    AppManifestCollectionExecutionData appManifestCollectionData =
        AppManifestCollectionExecutionData.builder().appManifestId(appManifestId).build();
    if (getTimeoutMillis() != null) {
      appManifestCollectionData.setTimeout(valueOf(getTimeoutMillis()));
    }
    appManifestCollectionData.setVersion(lastCollectedHelmChart.getVersion());
    appManifestCollectionData.setBuildNo(lastCollectedHelmChart.getVersion());
    appManifestCollectionData.setMetadata(lastCollectedHelmChart.getMetadata());
    appManifestCollectionData.setChartId(lastCollectedHelmChart.getUuid());
    appManifestCollectionData.setAppManifestName(applicationManifest.getName());
    if (applicationManifest.getHelmChartConfig() != null) {
      appManifestCollectionData.setChartName(applicationManifest.getHelmChartConfig().getChartName());
    }

    addBuildExecutionSummary(context, appManifestCollectionData, applicationManifest);
    return ExecutionResponse.builder()
        .executionStatus(ExecutionStatus.SUCCESS)
        .stateExecutionData(appManifestCollectionData)
        .errorMessage("Collected chart [" + lastCollectedHelmChart.getVersion() + "] for manifest source ["
            + applicationManifest.getName() + "]")
        .build();
  }

  private ExecutionResponse collectManifest(
      ExecutionContext context, ApplicationManifest applicationManifest, String evaluatedBuildNo) {
    String waitId = generateUuid();

    // if collection enabled and buildno is empty, get last collected artifact from db and return.
    if (!Boolean.FALSE.equals(applicationManifest.getEnableCollection()) && isBlank(evaluatedBuildNo)) {
      HelmChart lastCollectHelmChart =
          helmChartService.getLastCollectedManifest(context.getAccountId(), applicationManifest.getUuid());
      if (lastCollectHelmChart != null) {
        return prepareExecutionResponseForLastCollectedHelmChart(context, applicationManifest, lastCollectHelmChart);
      }
    }

    Integer timeout = getTimeoutMillis();

    ManifestCollectionParams manifestCollectionParams =
        manifestCollectionUtils.prepareCollectTaskParamsWithChartVersion(applicationManifest.getUuid(),
            applicationManifest.getAppId(), HelmChartCollectionParams.HelmChartCollectionType.SPECIFIC_VERSION,
            evaluatedBuildNo);
    HelmChartCollectionParams helmChartCollectionParams = (HelmChartCollectionParams) manifestCollectionParams;
    helmChartCollectionParams.setRegex(isRegex());

    DelegateTaskBuilder delegateTaskBuilder =
        DelegateTask.builder()
            .accountId(applicationManifest.getAccountId())
            .waitId(waitId)
            .expiry(artifactCollectionUtils.getDelegateQueueTimeout(applicationManifest.getAccountId()))
            .data(TaskData.builder()
                      .async(true)
                      .taskType(TaskType.HELM_COLLECT_CHART.name())
                      .parameters(new Object[] {manifestCollectionParams})
                      .timeout(timeout)
                      .build());

    String delegateTaskId = delegateService.queueTaskV2(delegateTaskBuilder.build());

    AppManifestCollectionExecutionData appManifestCollectionExecutionData =
        AppManifestCollectionExecutionData.builder()
            .timeout(valueOf(timeout))
            .appManifestName(applicationManifest.getName())
            .buildNo(evaluatedBuildNo)
            .build();

    return ExecutionResponse.builder()
        .async(true)
        .correlationIds(singletonList(waitId))
        .stateExecutionData(appManifestCollectionExecutionData)
        .delegateTaskId(delegateTaskId)
        .build();
  }

  private ExecutionResponse executeInternal(ExecutionContext context) {
    if (CollectionEntityType.MANIFEST.equals(sourceType)) {
      return executeManifest(context);
    } else {
      return executeArtifact(context);
    }
  }

  private void resolveArtifactStreamId(ExecutionContext context) {
    TemplateExpression artifactStreamExp = templateExpressionProcessor.getTemplateExpression(
        getTemplateExpressions(), ArtifactCollectionStateKeys.artifactStreamId);
    if (artifactStreamExp != null) {
      artifactStreamId = templateExpressionProcessor.resolveTemplateExpression(context, artifactStreamExp);
    }
  }

  private void resolveAppManifestId(ExecutionContext context) {
    TemplateExpression templateExpression = templateExpressionProcessor.getTemplateExpression(
        getTemplateExpressions(), ArtifactCollectionStateKeys.appManifestId);
    if (templateExpression != null) {
      appManifestId = templateExpressionProcessor.resolveTemplateExpression(context, templateExpression);
    }
  }

  private ExecutionResponse handleAsyncResponseArtifact(ExecutionContext context, Map<String, ResponseData> response) {
    if (isNotEmpty(getTemplateExpressions())) {
      resolveArtifactStreamId(context);
    }
    ArtifactStream artifactStream = artifactStreamService.get(artifactStreamId);
    if (artifactStream == null) {
      artifactStream = artifactStreamService.fetchByArtifactSourceVariableValue(context.getAppId(), artifactStreamId);
      if (artifactStream != null && artifactStream.isArtifactStreamParameterized()
          && isNotEmpty(getTemplateExpressions())) {
        log.info("Artifact Stream {} is Parameterized", artifactStreamId);
        return ExecutionResponse.builder()
            .executionStatus(ExecutionStatus.FAILED)
            .errorMessage("Parameterized Artifact Source " + artifactStream.getName()
                + " cannot be used as a value for templatized artifact variable")
            .build();
      }
    }
    notNullCheck("ArtifactStream was deleted", artifactStream);

    String evaluatedBuildNo = getEvaluatedBuildNo(context);

    if (shouldCollectArtifact(context)) {
      return handleArtifactCollectionResponse(context, response, artifactStream, evaluatedBuildNo);
    }

    Artifact lastCollectedArtifact = fetchCollectedArtifact(artifactStream, evaluatedBuildNo);

    ArtifactCollectionExecutionData artifactCollectionExecutionData =
        ArtifactCollectionExecutionData.builder().artifactStreamId(artifactStreamId).build();

    if (getTimeoutMillis() != null) {
      artifactCollectionExecutionData.setTimeout(valueOf(getTimeoutMillis()));
    }
    artifactCollectionExecutionData.setArtifactSource(artifactStream.getSourceName());

    if (lastCollectedArtifact == null || !lastCollectedArtifact.getStatus().isFinalStatus()) {
      artifactCollectionExecutionData.setMessage(String.format("Waiting for [%s] to be collected from [%s] repository",
          evaluatedBuildNo == null ? "latest artifact" : evaluatedBuildNo, artifactStream.getArtifactStreamType()));
      artifactCollectionExecutionData.setBuildNo(evaluatedBuildNo);

      String resumeId = delayEventHelper.delay(DELAY_TIME_IN_SEC, Collections.emptyMap());

      return ExecutionResponse.builder()
          .async(true)
          .correlationIds(asList(resumeId))
          .stateExecutionData(artifactCollectionExecutionData)
          .build();
    }

    artifactCollectionExecutionData.setRevision(lastCollectedArtifact.getRevision());
    artifactCollectionExecutionData.setBuildNo(lastCollectedArtifact.getBuildNo());
    artifactCollectionExecutionData.setMetadata(lastCollectedArtifact.getMetadata());
    artifactCollectionExecutionData.setArtifactId(lastCollectedArtifact.getUuid());
    updateArtifactCollectionExecutionData(context, artifactCollectionExecutionData);

    addBuildExecutionSummary(context, artifactCollectionExecutionData, artifactStream);
    return ExecutionResponse.builder()
        .stateExecutionData(artifactCollectionExecutionData)
        .executionStatus(SUCCESS)
        .build();
  }

  private ExecutionResponse handleArtifactCollectionResponse(ExecutionContext context,
      Map<String, ResponseData> response, ArtifactStream artifactStream, String evaluatedBuildNo) {
    DelegateResponseData notifyResponseData = (DelegateResponseData) response.values().iterator().next();
    if (notifyResponseData instanceof BuildSourceExecutionResponse) {
      BuildSourceExecutionResponse buildSourceExecutionResponse = (BuildSourceExecutionResponse) notifyResponseData;
      BuildSourceResponse buildSourceResponse = buildSourceExecutionResponse.getBuildSourceResponse();
      if (CommandExecutionStatus.SUCCESS.equals(buildSourceExecutionResponse.getCommandExecutionStatus())
          && buildSourceResponse != null && isNotEmpty(buildSourceResponse.getBuildDetails())) {
        BuildDetails buildDetail = buildSourceResponse.getBuildDetails().get(0);
        if (artifactStream.isArtifactStreamParameterized()) {
          populateRuntimeValuesAndArtifactStreamParameters(context, artifactStream, evaluatedBuildNo);
        }
        Artifact artifact = artifactCollectionUtils.getArtifact(artifactStream, buildDetail);
        Artifact savedArtifact = artifactService.create(artifact, artifactStream, false);
        Map<String, String> metadata = MappingUtils.safeCopy(artifact.getMetadata());
        if (savedArtifact.isDuplicate()) {
          if (shouldUpdateMetadata(artifact, savedArtifact)) {
            artifactService.updateMetadataAndRevision(
                savedArtifact.getUuid(), savedArtifact.getAccountId(), metadata, artifact.getRevision());
          } else if (featureFlagService.isEnabled(SORT_ARTIFACTS_IN_UPDATED_ORDER, context.getAccountId())) {
            artifactService.updateLastUpdatedAt(savedArtifact.getUuid(), savedArtifact.getAccountId());
          }
        }
        ArtifactCollectionExecutionData artifactCollectionExecutionData =
            ArtifactCollectionExecutionData.builder()
                .artifactStreamId(artifactStreamId)
                .buildNo(artifact.getBuildNo())
                .metadata(metadata)
                .artifactSource(artifactStream.getSourceName())
                .revision(artifact.getRevision())
                .artifactId(savedArtifact.getUuid())
                .build();

        addBuildExecutionSummary(context, artifactCollectionExecutionData, artifactStream);
        return ExecutionResponse.builder()
            .stateExecutionData(artifactCollectionExecutionData)
            .executionStatus(SUCCESS)
            .build();
      } else {
        if (isNotEmpty(evaluatedBuildNo)) {
          Artifact artifact = artifactService.getArtifactByBuildNumber(artifactStream, evaluatedBuildNo, isRegex());
          if (artifact == null && featureFlagService.isEnabled(SAVE_ARTIFACT_TO_DB, context.getAccountId())
              && !isRegex()) {
            artifact = artifactService.create(artifactCollectionUtils.getArtifact(
                artifactStream, BuildDetails.Builder.aBuildDetails().withNumber(evaluatedBuildNo).build()));
          }
          if (artifact != null) {
            Map<String, String> metadata =
                artifact.getMetadata() != null ? MappingUtils.safeCopy(artifact.getMetadata()) : new HashMap<>();
            ArtifactCollectionExecutionData artifactCollectionExecutionData =
                ArtifactCollectionExecutionData.builder()
                    .artifactStreamId(artifactStreamId)
                    .buildNo(artifact.getBuildNo())
                    .metadata(metadata)
                    .artifactSource(artifactStream.getSourceName())
                    .revision(artifact.getRevision())
                    .artifactId(artifact.getUuid())
                    .build();

            addBuildExecutionSummary(context, artifactCollectionExecutionData, artifactStream);
            return ExecutionResponse.builder()
                .stateExecutionData(artifactCollectionExecutionData)
                .executionStatus(SUCCESS)
                .build();
          }
        }
        String errorMessage = buildSourceExecutionResponse.getErrorMessage();
        if (buildSourceExecutionResponse.isTimeoutError()) {
          return ExecutionResponse.builder()
              .executionStatus(FAILED)
              .failureTypes(FailureType.TIMEOUT)
              .errorMessage(isEmpty(errorMessage) ? String.format("Collect artifact stream %s, buildNo %s timed out",
                                artifactStream.getName(), evaluatedBuildNo)
                                                  : errorMessage)
              .build();
        }
        return ExecutionResponse.builder()
            .executionStatus(FAILED)
            .errorMessage(isEmpty(errorMessage)
                    ? String.format("Failed to collect build version %s for artifact source %s", evaluatedBuildNo,
                        artifactStream.getName())
                    : errorMessage)
            .build();
      }
    } else {
      log.error("Unhandled DelegateResponseData class " + notifyResponseData.getClass().getCanonicalName(),
          new Exception(""));
    }
    return ExecutionResponse.builder()
        .executionStatus(FAILED)
        .errorMessage(String.format(
            "Failed to collect build version %s for artifact source %s", evaluatedBuildNo, artifactStream.getName()))
        .build();
  }

  private boolean shouldUpdateMetadata(Artifact artifact, Artifact savedArtifact) {
    return (savedArtifact.getMetadata() != null && !savedArtifact.getMetadata().equals(artifact.getMetadata()))
        || (savedArtifact.getRevision() != null && !savedArtifact.getRevision().equals(artifact.getRevision()));
  }

  private ExecutionResponse handleAsyncResponseManifest(ExecutionContext context, Map<String, ResponseData> response) {
    if (isNotEmpty(getTemplateExpressions())) {
      resolveAppManifestId(context);
    }
    ApplicationManifest applicationManifest = applicationManifestService.getById(context.getAppId(), appManifestId);
    notNullCheck("Application manifest was deleted", applicationManifest);

    String evaluatedBuildNo = getEvaluatedBuildNo(context);

    if (shouldCollectManifest(context)) {
      return handleManifestCollectionResponse(context, response, applicationManifest, evaluatedBuildNo);
    }

    HelmChart lastCollectedChart = fetchCollectedAppManifest(applicationManifest, evaluatedBuildNo);

    AppManifestCollectionExecutionData manifestCollectionExecutionData =
        AppManifestCollectionExecutionData.builder().appManifestId(appManifestId).build();

    if (getTimeoutMillis() != null) {
      manifestCollectionExecutionData.setTimeout(valueOf(getTimeoutMillis()));
    }
    manifestCollectionExecutionData.setAppManifestName(applicationManifest.getName());
    if (applicationManifest.getHelmChartConfig() != null) {
      manifestCollectionExecutionData.setChartName(applicationManifest.getHelmChartConfig().getChartName());
    }

    manifestCollectionExecutionData.setBuildNo(evaluatedBuildNo);
    manifestCollectionExecutionData.setAppManifestName(applicationManifest.getName());

    if (lastCollectedChart == null) {
      manifestCollectionExecutionData.setMessage(String.format("Waiting for [%s] to be collected from [%s]",
          evaluatedBuildNo == null ? "latest chart" : evaluatedBuildNo, applicationManifest.getName()));

      String resumeId = delayEventHelper.delay(DELAY_TIME_IN_SEC, Collections.emptyMap());

      return ExecutionResponse.builder()
          .async(true)
          .correlationIds(asList(resumeId))
          .stateExecutionData(manifestCollectionExecutionData)
          .build();
    }
    manifestCollectionExecutionData.setVersion(lastCollectedChart.getVersion());
    manifestCollectionExecutionData.setChartId(lastCollectedChart.getUuid());
    manifestCollectionExecutionData.setBuildNo(lastCollectedChart.getVersion());
    manifestCollectionExecutionData.setMetadata(lastCollectedChart.getMetadata());

    addBuildExecutionSummary(context, manifestCollectionExecutionData, applicationManifest);
    return ExecutionResponse.builder()
        .stateExecutionData(manifestCollectionExecutionData)
        .executionStatus(SUCCESS)
        .build();
  }

  private ExecutionResponse handleManifestCollectionResponse(ExecutionContext context,
      Map<String, ResponseData> response, ApplicationManifest applicationManifest, String evaluatedBuildNo) {
    DelegateResponseData notifyResponseData = (DelegateResponseData) response.values().iterator().next();
    if (notifyResponseData instanceof HelmCollectChartResponse) {
      HelmCollectChartResponse helmCollectChartResponse = (HelmCollectChartResponse) notifyResponseData;
      if (CommandExecutionStatus.SUCCESS.equals(helmCollectChartResponse.getCommandExecutionStatus())
          && isNotEmpty(helmCollectChartResponse.getHelmCharts())) {
        HelmChart helmChart = HelmChart.fromDto(helmCollectChartResponse.getHelmCharts().get(0));
        HelmChart savedHelmChart = helmChartService.createOrUpdateAppVersion(helmChart);
        AppManifestCollectionExecutionData appManifestCollectionExecutionData =
            AppManifestCollectionExecutionData.builder()
                .appManifestId(appManifestId)
                .buildNo(helmChart.getVersion())
                .version(helmChart.getVersion())
                .appManifestName(applicationManifest.getName())
                .chartId(savedHelmChart.getUuid())
                .build();

        addBuildExecutionSummary(context, appManifestCollectionExecutionData, applicationManifest);
        return ExecutionResponse.builder()
            .stateExecutionData(appManifestCollectionExecutionData)
            .executionStatus(SUCCESS)
            .build();
      } else {
        String errorMessage = helmCollectChartResponse.getErrorMessage();
        if (helmCollectChartResponse.isTimeoutError()) {
          return ExecutionResponse.builder()
              .executionStatus(FAILED)
              .failureTypes(FailureType.TIMEOUT)
              .errorMessage(errorMessage.isEmpty()
                      ? String.format("Collect build version %s for manifest source %s timed out", evaluatedBuildNo,
                          applicationManifest.getName())
                      : errorMessage)
              .build();
        }
        return ExecutionResponse.builder()
            .executionStatus(FAILED)
            .errorMessage(isEmpty(errorMessage)
                    ? String.format("Failed to collect build version %s for manifest source %s", evaluatedBuildNo,
                        applicationManifest.getName())
                    : errorMessage)
            .build();
      }
    } else {
      log.error("Unhandled DelegateResponseData class " + notifyResponseData.getClass().getCanonicalName(),
          new Exception(""));
    }
    return ExecutionResponse.builder()
        .executionStatus(FAILED)
        .errorMessage(String.format("Failed to collect build version %s for manifest source %s", evaluatedBuildNo,
            applicationManifest.getName()))
        .build();
  }

  @Override
  public ExecutionResponse handleAsyncResponse(ExecutionContext context, Map<String, ResponseData> response) {
    if (CollectionEntityType.MANIFEST.equals(sourceType)) {
      return handleAsyncResponseManifest(context, response);
    } else {
      return handleAsyncResponseArtifact(context, response);
    }
  }

  private String getEvaluatedBuildNo(ExecutionContext context) {
    String evaluatedBuildNo;
    if (isBlank(buildNo)) {
      evaluatedBuildNo = buildNo;
    } else {
      evaluatedBuildNo = context.renderExpression(buildNo);
    }
    return evaluatedBuildNo;
  }

  private void addBuildExecutionSummary(ExecutionContext context,
      ArtifactCollectionExecutionData artifactCollectionExecutionData, ArtifactStream artifactStream) {
    Map<String, String> metadata = new HashMap<>();
    if (isNotEmpty(artifactCollectionExecutionData.getMetadata())) {
      metadata.putAll(artifactCollectionExecutionData.getMetadata());
    }
    String buildUrl = metadata.get(ArtifactMetadataKeys.url);
    // Rove the the following as no need to store in build execution summary
    metadata.remove(ArtifactMetadataKeys.buildNo);
    metadata.remove(ArtifactMetadataKeys.url);
    BuildExecutionSummary buildExecutionSummary =
        BuildExecutionSummary.builder()
            .artifactSource(artifactCollectionExecutionData.getArtifactSource())
            .revision(artifactCollectionExecutionData.getRevision() == null
                    ? "N/A"
                    : artifactCollectionExecutionData.getRevision())
            .metadata(metadata.isEmpty()
                    ? "N/A"
                    : artifactCollectionExecutionData.getArtifactSource() + " " + metadata.toString())
            .artifactStreamId(artifactStream.getUuid())
            .buildName(artifactCollectionExecutionData.getArtifactSource() + " ("
                + artifactCollectionExecutionData.getBuildNo() + ")")
            .buildUrl(buildUrl)
            .build();
    workflowExecutionService.refreshBuildExecutionSummary(context.getWorkflowExecutionId(), buildExecutionSummary);
  }

  private void addBuildExecutionSummary(ExecutionContext context,
      AppManifestCollectionExecutionData appManifestCollectionExecutionData, ApplicationManifest applicationManifest) {
    Map<String, String> metadata = new HashMap<>();
    if (isNotEmpty(appManifestCollectionExecutionData.getMetadata())) {
      metadata.putAll(appManifestCollectionExecutionData.getMetadata());
    }
    String buildUrl = metadata.get(ArtifactMetadataKeys.url);
    // Rove the the following as no need to store in build execution summary
    metadata.remove(ArtifactMetadataKeys.buildNo);
    metadata.remove(ArtifactMetadataKeys.url);
    String chartName = null;
    if (applicationManifest.getHelmChartConfig() != null) {
      chartName = applicationManifest.getHelmChartConfig().getChartName();
    }
    BuildExecutionSummary buildExecutionSummary =
        BuildExecutionSummary.builder()
            .revision(appManifestCollectionExecutionData.getVersion() == null
                    ? "N/A"
                    : appManifestCollectionExecutionData.getVersion())
            .buildUrl(buildUrl)
            .sourceType(CollectionEntityType.MANIFEST.equals(sourceType) ? CollectionEntityType.MANIFEST.name()
                                                                         : CollectionEntityType.ARTIFACT.name())
            .appManifestId(appManifestId)
            .appManifestSource(applicationManifest.getName())
            .buildName(chartName
                + (isBlank(appManifestCollectionExecutionData.getVersion())
                        ? ""
                        : " (" + appManifestCollectionExecutionData.getVersion() + ")"))
            .build();
    workflowExecutionService.refreshBuildExecutionSummary(context.getWorkflowExecutionId(), buildExecutionSummary);
  }

  @Override
  public void handleAbortEvent(ExecutionContext context) {
    if (context == null || context.getStateExecutionData() == null) {
      return;
    }
    log.info("Action aborted either due to timeout or manual user abort");
    if (context.getStateExecutionData() instanceof AppManifestCollectionExecutionData) {
      AppManifestCollectionExecutionData executionData =
          (AppManifestCollectionExecutionData) context.getStateExecutionData();
      executionData.setMessage("Failed to collect manifest from Application Manifest. Please verify chart version ["
          + executionData.getBuildNo() + "] exists");
    } else {
      ArtifactCollectionExecutionData artifactCollectionExecutionData =
          (ArtifactCollectionExecutionData) context.getStateExecutionData();
      artifactCollectionExecutionData.setMessage(
          "Failed to collect artifact from Artifact Server. Please verify Build No/Tag ["
          + artifactCollectionExecutionData.getBuildNo() + "] exists");
    }
  }

  @Override
  public Map<String, String> validateFields() {
    Map<String, String> invalidFields = new HashMap<>();
    if (CollectionEntityType.MANIFEST.equals(sourceType)) {
      if (isBlank(appManifestId) && fieldTemplatized(ArtifactCollectionStateKeys.appManifestId)) {
        invalidFields.put("appManifest", "Application manifest should not be empty");
      }
    } else {
      if (isBlank(artifactStreamId) && fieldTemplatized(ArtifactCollectionStateKeys.artifactStreamId)) {
        invalidFields.put("artifactSource", "Artifact Source should not be empty");
      }
    }
    return invalidFields;
  }

  private boolean fieldTemplatized(String field) {
    return isEmpty(getTemplateExpressions())
        || getTemplateExpressions().stream().map(TemplateExpression::getFieldName).noneMatch(field::equals);
  }

  @Attributes(title = "Timeout (ms)")
  @DefaultValue("" + DEFAULT_ARTIFACT_COLLECTION_STATE_TIMEOUT_MILLIS)
  @Override
  public Integer getTimeoutMillis() {
    if (super.getTimeoutMillis() == null) {
      return Math.toIntExact(DEFAULT_ARTIFACT_COLLECTION_STATE_TIMEOUT_MILLIS);
    }
    return super.getTimeoutMillis();
  }

  private void updateArtifactCollectionExecutionData(
      ExecutionContext context, ArtifactCollectionExecutionData artifactCollectionExecutionData) {
    if (isMultiArtifact(context.getAccountId())) {
      artifactCollectionExecutionData.setEntityType(fetchEntityType());
      artifactCollectionExecutionData.setEntityId(fetchEntityId());
      artifactCollectionExecutionData.setServiceId(fetchServiceId());
      artifactCollectionExecutionData.setArtifactVariableName(fetchArtifactVariableName());
    }
  }

  private HelmChart fetchCollectedAppManifest(ApplicationManifest applicationManifest, String buildNo) {
    String accountId = applicationManifest.getAccountId();
    String applicationManifestUuid = applicationManifest.getUuid();
    if (isBlank(buildNo)) {
      return helmChartService.getLastCollectedManifest(accountId, applicationManifestUuid);
    } else {
      if (isRegex()) {
        return helmChartService.getLastCollectedManifestMatchingRegex(accountId, applicationManifestUuid, buildNo);
      } else {
        return helmChartService.getManifestByVersionNumber(accountId, applicationManifestUuid, buildNo);
      }
    }
  }

  private Artifact fetchCollectedArtifact(ArtifactStream artifactStream, String buildNo) {
    if (isBlank(buildNo)) {
      return artifactService.fetchLastCollectedApprovedArtifactForArtifactStream(artifactStream);
    } else {
      return artifactService.getArtifactByBuildNumber(artifactStream, buildNo, isRegex());
    }
  }

  private Artifact fetchCollectedArtifactForParameterizedArtifactStream(ArtifactStream artifactStream, String buildNo) {
    if (isBlank(buildNo)) {
      throw new InvalidRequestException("Artifact stream is parameterized. However, build number not provided");
    } else {
      return artifactService.getArtifactByBuildNumberAndSourceName(
          artifactStream, buildNo, isRegex(), artifactStream.getSourceName());
    }
  }

  private EntityType fetchEntityType() {
    // TODO: ASR: observations:
    //   1. if entityType is present, entityId should not be blank
    //   2. if entityType is WORKFLOW, serviceId is ignored
    return entityType == null ? EntityType.SERVICE : entityType;
  }

  private String fetchEntityId() {
    return isBlank(entityId) ? fetchServiceId() : entityId;
  }

  private String fetchServiceId() {
    return serviceId;
  }

  private String fetchArtifactVariableName() {
    return isBlank(artifactVariableName) ? ExpressionEvaluator.DEFAULT_ARTIFACT_VARIABLE_NAME : artifactVariableName;
  }

  private boolean isMultiArtifact(String accountId) {
    return false;
  }

  public void setRuntimeValues(Map<String, Object> runtimeValues) {
    this.runtimeValues = runtimeValues;
  }
}
