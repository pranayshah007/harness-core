/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl;
import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.beans.FeatureName.CDS_DISABLE_ALL_CG_TRIGGERS;
import static io.harness.beans.FeatureName.GITHUB_WEBHOOK_AUTHENTICATION;
import static io.harness.beans.FeatureName.WEBHOOK_TRIGGER_AUTHORIZATION;
import static io.harness.beans.WorkflowType.PIPELINE;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.delegate.beans.TaskData.DEFAULT_SYNC_CALL_TIMEOUT;
import static io.harness.exception.WingsException.ExecutionContext.MANAGER;
import static io.harness.exception.WingsException.USER;

import static software.wings.beans.TaskType.WEBHOOK_TRIGGER_TASK;
import static software.wings.beans.trigger.WebhookSource.BITBUCKET;
import static software.wings.beans.trigger.WebhookSource.GITHUB;
import static software.wings.security.AuthenticationFilter.API_KEY_HEADER;
import static software.wings.service.impl.trigger.WebhookEventUtils.X_HUB_SIGNATURE_256;

import static java.lang.String.format;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.annotations.dev.TargetModule;
import io.harness.beans.DelegateTask;
import io.harness.beans.ExecutionStatus;
import io.harness.beans.FeatureName;
import io.harness.beans.SecretUsageLog;
import io.harness.beans.WorkflowType;
import io.harness.data.structure.EmptyPredicate;
import io.harness.delegate.beans.TaskData;
import io.harness.exception.ExceptionLogger;
import io.harness.exception.ExceptionUtils;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.WingsException;
import io.harness.ff.FeatureFlagService;
import io.harness.security.encryption.EncryptedDataDetail;
import io.harness.serializer.JsonUtils;

import software.wings.app.MainConfiguration;
import software.wings.beans.Application;
import software.wings.beans.Service;
import software.wings.beans.WebHookRequest;
import software.wings.beans.WebHookResponse;
import software.wings.beans.WorkflowExecution;
import software.wings.beans.appmanifest.ManifestSummary;
import software.wings.beans.instance.dashboard.ArtifactSummary;
import software.wings.beans.trigger.GithubAction;
import software.wings.beans.trigger.ReleaseAction;
import software.wings.beans.trigger.Trigger;
import software.wings.beans.trigger.TriggerConditionType;
import software.wings.beans.trigger.TriggerExecution;
import software.wings.beans.trigger.TriggerExecution.Status;
import software.wings.beans.trigger.TriggerExecution.TriggerExecutionBuilder;
import software.wings.beans.trigger.TriggerExecution.WebhookEventDetails;
import software.wings.beans.trigger.WebHookTriggerCondition;
import software.wings.beans.trigger.WebHookTriggerResponseData;
import software.wings.beans.trigger.WebhookEventType;
import software.wings.beans.trigger.WebhookSource;
import software.wings.beans.trigger.WebhookSource.BitBucketEventType;
import software.wings.beans.trigger.WebhookTriggerParameters;
import software.wings.dl.WingsPersistence;
import software.wings.expression.ManagerExpressionEvaluator;
import software.wings.service.impl.trigger.WebhookEventUtils;
import software.wings.service.impl.trigger.WebhookTriggerProcessor;
import software.wings.service.intfc.AppService;
import software.wings.service.intfc.ApplicationManifestService;
import software.wings.service.intfc.ServiceResourceService;
import software.wings.service.intfc.TriggerService;
import software.wings.service.intfc.WebHookService;
import software.wings.service.intfc.security.SecretManager;
import software.wings.service.intfc.trigger.TriggerExecutionService;

import com.fasterxml.jackson.core.type.TypeReference;
import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import dev.morphia.annotations.Transient;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.regex.Pattern;
import javax.validation.executable.ValidateOnExecution;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import lombok.extern.slf4j.Slf4j;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_FIRST_GEN})
@OwnedBy(CDC)
@ValidateOnExecution
@Singleton
@Slf4j
@TargetModule(HarnessModule._815_CG_TRIGGERS)
public class WebHookServiceImpl implements WebHookService {
  public static final String X_GIT_HUB_EVENT = "X-GitHub-Event";
  public static final String X_GIT_LAB_EVENT = "X-Gitlab-Event";
  public static final String X_BIT_BUCKET_EVENT = "X-Event-Key";

  @Inject private TriggerService triggerService;
  @Inject private AppService appService;
  @Inject private MainConfiguration configuration;
  @Inject private WingsPersistence wingsPersistence;
  @Inject private ManagerExpressionEvaluator expressionEvaluator;
  @Inject private WebhookEventUtils webhookEventUtils;
  @Inject private WebhookTriggerProcessor webhookTriggerProcessor;
  @Inject private TriggerExecutionService triggerExecutionService;
  @Inject private ServiceResourceService serviceResourceService;
  @Transient @Inject protected FeatureFlagService featureFlagService;
  @Inject private SecretManager secretManager;
  @Inject private DelegateServiceImpl delegateService;
  @Inject private ApplicationManifestService applicationManifestService;

  private String getBaseUrlUI() {
    String baseUrl = configuration.getPortal().getUrl().trim();
    if (!baseUrl.endsWith("/")) {
      baseUrl += "/";
    }
    return baseUrl;
  }

  private String getUiUrl(
      WorkflowType workflowType, String accountId, String appId, String envId, String workflowExecutionId) {
    if (workflowType == PIPELINE) {
      return format("%s#/account/%s/app/%s/pipeline-execution/%s/workflow-execution/undefined/details", getBaseUrlUI(),
          accountId, appId, workflowExecutionId);
    } else {
      return format("%s#/account/%s/app/%s/env/%s/executions/%s/details", getBaseUrlUI(), accountId, appId, envId,
          workflowExecutionId);
    }
  }

  private String getUiSetupUrl(WorkflowType workflowType, String accountId, String appId, String workflowId) {
    if (workflowType == PIPELINE) {
      return format("%s#/account/%s/app/%s/pipelines/%s/edit", getBaseUrlUI(), accountId, appId, workflowId);
    } else {
      return format("%s#/account/%s/app/%s/workflows/%s/details", getBaseUrlUI(), accountId, appId, workflowId);
    }
  }

  private String getApiUrl(String accountId, String appId, String workflowExecutionId) {
    return format("%sapi/external/v1/executions/%s/status?accountId=%s&appId=%s", getBaseUrlAPI(), workflowExecutionId,
        accountId, appId);
  }

  private String getBaseUrlAPI() {
    String baseUrl = configuration.getApiUrl().trim();
    if (!baseUrl.endsWith("/")) {
      baseUrl += "/";
    }
    return baseUrl;
  }

  @Override
  public Response execute(String token, WebHookRequest webHookRequest, HttpHeaders httpHeaders) {
    try {
      if (webHookRequest == null) {
        log.warn("Payload is mandatory");
        WebHookResponse webHookResponse = WebHookResponse.builder().error("Payload is mandatory").build();

        return prepareResponse(webHookResponse, Response.Status.BAD_REQUEST);
      }

      log.info("Received input Webhook Request {}  ", webHookRequest);
      Trigger trigger = triggerService.getTriggerByWebhookToken(token);
      if (trigger == null) {
        WebHookResponse webHookResponse =
            WebHookResponse.builder().error("No trigger associated with the given token").build();

        return prepareResponse(webHookResponse, Response.Status.BAD_REQUEST);
      }
      if (trigger.isDisabled()) {
        return prepareRejectedResponse();
      }
      Application app = appService.get(trigger.getAppId());
      if (app == null) {
        WebHookResponse webHookResponse =
            WebHookResponse.builder().error("No App associated with the given token").build();

        return prepareResponse(webHookResponse, Response.Status.BAD_REQUEST);
      }

      if (webhookTriggerProcessor.checkFileContentOptionSelected(trigger)) {
        WebHookResponse webHookResponse =
            WebHookResponse.builder()
                .error(
                    "Trigger configured with deploy only if files changed option cannot be executed by manual trigger")
                .build();

        return prepareResponse(webHookResponse, Response.Status.BAD_REQUEST);
      }

      if (featureFlagService.isEnabled(WEBHOOK_TRIGGER_AUTHORIZATION, app.getAccountId())
          && Boolean.TRUE.equals(app.getIsManualTriggerAuthorized())
          && isEmpty(httpHeaders.getHeaderString(API_KEY_HEADER))) {
        WebHookResponse webHookResponse = WebHookResponse.builder().error("Api Key cannot be empty").build();
        return prepareResponse(webHookResponse, Response.Status.UNAUTHORIZED);
      }

      if (featureFlagService.isEnabled(CDS_DISABLE_ALL_CG_TRIGGERS, app.getAccountId())) {
        return prepareResponse(
            WebHookResponse.builder().error("Trigger cannot be fired after migration to NextGen").build(),
            Response.Status.MOVED_PERMANENTLY);
      }
      return executeTriggerWebRequest(trigger.getAppId(), token, app, webHookRequest);

    } catch (WingsException ex) {
      ExceptionLogger.logProcessedMessages(ex, MANAGER, log);
      return prepareResponse(
          WebHookResponse.builder().error(ExceptionUtils.getMessage(ex)).build(), Response.Status.BAD_REQUEST);
    } catch (Exception ex) {
      log.warn("Webhook Request call failed", ex);
      return prepareResponse(WebHookResponse.builder().error(ExceptionUtils.getMessage(ex)).build(),
          Response.Status.INTERNAL_SERVER_ERROR);
    }
  }

  private Response executeTriggerWebRequest(
      String appId, String token, Application app, WebHookRequest webHookRequest) {
    Map<String, ArtifactSummary> serviceBuildNumbers = new HashMap<>();
    try {
      Map<String, ManifestSummary> serviceManifestMapping =
          featureFlagService.isEnabled(FeatureName.HELM_CHART_AS_ARTIFACT, app.getAccountId())
          ? resolveServiceHelmChartVersion(appId, webHookRequest)
          : new HashMap<>();
      Response response = resolveServiceBuildNumbers(appId, webHookRequest, serviceBuildNumbers);
      if (response != null) {
        return response;
      }
      WorkflowExecution workflowExecution = triggerService.triggerExecutionByWebHook(appId, token, serviceBuildNumbers,
          serviceManifestMapping, TriggerExecution.builder().build(), webHookRequest.getParameters());

      return constructSuccessResponse(appId, app.getAccountId(), workflowExecution);
    } catch (InvalidRequestException ire) {
      return prepareResponse(WebHookResponse.builder().error(ire.getMessage()).build(), Response.Status.BAD_REQUEST);
    }
  }

  private boolean isGithubPingEvent(HttpHeaders httpHeaders) {
    if (httpHeaders == null) {
      return false;
    }
    WebhookSource webhookSource = webhookEventUtils.obtainWebhookSource(httpHeaders);

    if (WebhookSource.GITHUB != webhookSource) {
      return false;
    }

    return WebhookEventType.PING.getValue().equals(webhookEventUtils.obtainEventType(webhookSource, httpHeaders));
  }

  @Override
  public Response executeByEvent(String token, String webhookEventPayload, HttpHeaders httpHeaders) {
    log.info("Received the Webhook Request for token token {}  ", token);
    TriggerExecutionBuilder triggerExecutionBuilder = TriggerExecution.builder();
    TriggerExecution triggerExecution = triggerExecutionBuilder.build();
    try {
      Trigger trigger = triggerService.getTriggerByWebhookToken(token);
      String accountId = getAccountId(trigger);
      if (accountId == null) {
        WebHookResponse webHookResponse =
            WebHookResponse.builder().error("Trigger or account not associated to the given token").build();

        return prepareResponse(webHookResponse, Response.Status.BAD_REQUEST);
      }

      return executeTrigger(webhookEventPayload, httpHeaders, triggerExecution, trigger);

    } catch (WingsException ex) {
      ExceptionLogger.logProcessedMessages(ex, MANAGER, log);
      triggerExecution.setStatus(Status.FAILED);
      triggerExecution.setMessage(ExceptionUtils.getMessage(ex));
      triggerExecutionService.save(triggerExecution);
      WebHookResponse webHookResponse = WebHookResponse.builder().error(triggerExecution.getMessage()).build();

      return prepareResponse(webHookResponse, Response.Status.BAD_REQUEST);
    } catch (Exception ex) {
      log.error("Webhook Request call failed", ex);
      triggerExecution.setStatus(Status.FAILED);
      triggerExecution.setMessage(ExceptionUtils.getMessage(ex));
      triggerExecutionService.save(triggerExecution);
      WebHookResponse webHookResponse = WebHookResponse.builder().error(triggerExecution.getMessage()).build();

      return prepareResponse(webHookResponse, Response.Status.INTERNAL_SERVER_ERROR);
    }
  }

  private String getAccountId(Trigger trigger) {
    if (trigger == null) {
      return null;
    } else {
      return appService.getAccountIdByAppId(trigger.getAppId());
    }
  }

  private Response executeTrigger(
      String webhookEventPayload, HttpHeaders httpHeaders, TriggerExecution triggerExecution, Trigger trigger) {
    if (trigger == null) {
      WebHookResponse webHookResponse =
          WebHookResponse.builder().error("Trigger not associated to the given token").build();

      return prepareResponse(webHookResponse, Response.Status.BAD_REQUEST);
    }

    if (isGithubPingEvent(httpHeaders)) {
      return prepareResponse(WebHookResponse.builder().message("Received ping event").build(), Response.Status.OK);
    }

    if (trigger.isDisabled()) {
      return prepareRejectedResponse();
    }

    triggerExecution.setAppId(trigger.getAppId());
    triggerExecution.setWebhookToken(trigger.getWebHookToken());
    triggerExecution.setTriggerId(trigger.getUuid());
    triggerExecution.setTriggerName(trigger.getName());
    triggerExecution.setWebhookEventDetails(WebhookEventDetails.builder().build());
    triggerExecution.setWorkflowId(trigger.getWorkflowId());
    triggerExecution.setWorkflowType(trigger.getWorkflowType());

    Map<String, String> resolvedParameters;
    try {
      resolvedParameters = resolveWebhookParameters(
          webhookEventPayload, httpHeaders, trigger, triggerExecution.getWebhookEventDetails());
      // Validate the give branch name matches the one with selected one
      webhookTriggerProcessor.validateBranchName(trigger, triggerExecution);
    } catch (WingsException ex) {
      ExceptionLogger.logProcessedMessages(ex, MANAGER, log);
      triggerExecution.setMessage(ExceptionUtils.getMessage(ex));
      triggerExecution.setStatus(Status.REJECTED);
      triggerExecutionService.save(triggerExecution);
      WebHookResponse webHookResponse = WebHookResponse.builder().error(triggerExecution.getMessage()).build();

      return prepareResponse(webHookResponse, Response.Status.BAD_REQUEST);
    }

    log.info("Trigger execution for the trigger {}", trigger.getUuid());
    WorkflowExecution workflowExecution =
        triggerService.triggerExecutionByWebHook(trigger, resolvedParameters, triggerExecution);

    String accountId = isEmpty(trigger.getAccountId()) ? getAccountId(trigger) : trigger.getAccountId();
    if (featureFlagService.isEnabled(GITHUB_WEBHOOK_AUTHENTICATION, accountId)) {
      updateRuntimeUsageForSecret(trigger, workflowExecution.getUuid(), accountId);
    }
    if (webhookTriggerProcessor.checkFileContentOptionSelected(trigger)) {
      WebHookResponse webHookResponse =
          WebHookResponse.builder()
              .message("Request received. Deployment will be triggered if the file content changed")
              .build();

      return prepareResponse(webHookResponse, Response.Status.OK);
    } else {
      log.info("Execution trigger success. Saving trigger execution");
      WebHookResponse webHookResponse = WebHookResponse.builder()
                                            .requestId(workflowExecution.getUuid())
                                            .status(workflowExecution.getStatus().name())
                                            .build();

      return prepareResponse(webHookResponse, Response.Status.OK);
    }
  }

  private void updateRuntimeUsageForSecret(Trigger trigger, String workflowExecutionId, String accountId) {
    if (trigger.getCondition().getConditionType() == TriggerConditionType.WEBHOOK) {
      WebHookTriggerCondition webHookTriggerCondition = (WebHookTriggerCondition) trigger.getCondition();
      if (isNotEmpty(webHookTriggerCondition.getWebHookSecret()) && isNotEmpty(workflowExecutionId)) {
        WorkflowExecution workflowExecution = wingsPersistence.get(WorkflowExecution.class, workflowExecutionId);
        if (workflowExecution == null) {
          log.warn("No workflow execution with id {} found.", workflowExecutionId);
        } else {
          SecretUsageLog usageLog = SecretUsageLog.builder()
                                        .encryptedDataId(webHookTriggerCondition.getWebHookSecret())
                                        .workflowExecutionId(workflowExecutionId)
                                        .accountId(accountId)
                                        .appId(workflowExecution.getAppId())
                                        .envId(workflowExecution.getEnvId())
                                        .pipelineExecution(workflowExecution.getWorkflowType() == PIPELINE)
                                        .build();
          wingsPersistence.save(usageLog);
        }
      }
    }
  }

  private Response prepareRejectedResponse() {
    WebHookResponse webHookResponse =
        WebHookResponse.builder().error("Trigger rejected due to slowness in the product. Please try again").build();

    return prepareResponse(webHookResponse, Response.Status.SERVICE_UNAVAILABLE);
  }

  Response resolveServiceBuildNumbers(
      String appId, WebHookRequest webHookRequest, Map<String, ArtifactSummary> serviceArtifactMapping) {
    List<Map<String, Object>> artifacts = webHookRequest.getArtifacts();
    if (artifacts != null) {
      for (Map<String, Object> artifact : artifacts) {
        String serviceName = (String) artifact.get("service");
        String buildNumber = (String) artifact.get("buildNumber");
        String artifactStreamName = (String) artifact.get("artifactSourceName");
        Map<String, Object> parameterMap = null;
        parameterMap = (Map<String, Object>) artifact.get("artifactVariables");
        if (isNotEmpty(parameterMap)) {
          parameterMap.put("buildNo", buildNumber);
        }

        log.info("WebHook params Service name {}, Build Number {} and Artifact Source Name {}", serviceName,
            buildNumber, artifactStreamName);
        if (serviceName != null) {
          Service service = serviceResourceService.getServiceByName(appId, serviceName, false);
          if (service == null) {
            return prepareResponse(
                WebHookResponse.builder().error("Service Name [" + serviceName + "] does not exist").build(),
                Response.Status.BAD_REQUEST);
          }
          if (isNotEmpty(parameterMap)) {
            serviceArtifactMapping.put(service.getUuid(),
                ArtifactSummary.builder()
                    .name(artifactStreamName)
                    .buildNo(buildNumber)
                    .artifactParameters(parameterMap)
                    .build());
          } else {
            serviceArtifactMapping.put(
                service.getUuid(), ArtifactSummary.builder().name(artifactStreamName).buildNo(buildNumber).build());
          }
        }
      }
    }
    return null;
  }

  Map<String, ManifestSummary> resolveServiceHelmChartVersion(String appId, WebHookRequest webHookRequest) {
    Map<String, ManifestSummary> serviceManifestMapping = new HashMap<>();
    List<Map<String, Object>> manifests = webHookRequest.getManifests();
    if (manifests != null) {
      for (Map<String, Object> manifest : manifests) {
        String serviceName = (String) manifest.get("service");
        String versionNumber = (String) manifest.get("versionNumber");
        String appManifestName = (String) manifest.get("appManifestName");
        log.info("WebHook params Service name {} and Versions Number {}", serviceName, versionNumber);
        if (serviceName != null) {
          Service service = serviceResourceService.getServiceByName(appId, serviceName, false);
          if (service == null) {
            throw new InvalidRequestException("Service Name [" + serviceName + "] does not exist");
          }
          serviceManifestMapping.put(service.getUuid(),
              ManifestSummary.builder().appManifestName(appManifestName).versionNo(versionNumber).build());
        }
      }
    }
    return serviceManifestMapping;
  }

  private Map<String, String> resolveWebhookParameters(
      String payload, HttpHeaders httpHeaders, Trigger trigger, WebhookEventDetails webhookEventDetails) {
    WebHookTriggerCondition webhookTriggerCondition = (WebHookTriggerCondition) trigger.getCondition();

    // Web hook parameters saved
    Map<String, String> webhookParameters =
        webhookTriggerCondition.getParameters() == null ? new HashMap<>() : webhookTriggerCondition.getParameters();

    // Add the workflow variables to evaluate from Payload
    Map<String, String> workflowVariables =
        trigger.getWorkflowVariables() == null ? new HashMap<>() : trigger.getWorkflowVariables();
    for (Map.Entry<String, String> parameterEntry : workflowVariables.entrySet()) {
      if (webhookParameters.containsKey(parameterEntry.getKey())) {
        // Override it from parameters
        workflowVariables.put(parameterEntry.getKey(), webhookParameters.get(parameterEntry.getKey()));
      }
    }

    Map<String, String> resolvedParameters = new HashMap<>();
    WebhookSource webhookSource = webhookEventUtils.obtainWebhookSource(httpHeaders);

    if (webhookSource != webhookTriggerCondition.getWebhookSource()) {
      String msg = "Trigger [" + trigger.getName() + "] is set for source ["
          + webhookTriggerCondition.getWebhookSource() + "] not associate with the in coming source   [" + webhookSource
          + "]";
      throw new InvalidRequestException(msg, USER);
    }

    Map<String, Object> payLoadMap = JsonUtils.asObject(payload, new TypeReference<Map<String, Object>>() {});

    List<String> branchNames = new ArrayList<>();
    if (webhookSource == BITBUCKET) {
      BitBucketEventType bitBucketEventType = webhookEventUtils.getBitBucketEventType(httpHeaders);
      if (bitBucketEventType == BitBucketEventType.PUSH) {
        branchNames = webhookEventUtils.obtainBranchNameFromBitBucketPush(payLoadMap);
      } else {
        branchNames.add(webhookEventUtils.obtainBranchName(webhookSource, httpHeaders, payLoadMap));
      }
    } else {
      branchNames.add(webhookEventUtils.obtainBranchName(webhookSource, httpHeaders, payLoadMap));
    }
    String storedBranchRegex = webhookTriggerCondition.getBranchRegex();
    if (EmptyPredicate.isNotEmpty(storedBranchRegex) && EmptyPredicate.isNotEmpty(branchNames)) {
      validateBranchWithRegex(storedBranchRegex, branchNames);
    }
    validateWebHook(webhookSource, trigger, webhookTriggerCondition, payLoadMap, httpHeaders, payload);
    webhookEventDetails.setPayload(payload);
    webhookEventDetails.setBranchNames(branchNames);
    webhookEventDetails.setCommitId(webhookEventUtils.obtainCommitId(webhookSource, httpHeaders, payLoadMap));
    webhookEventDetails.setWebhookSource(webhookSource.name());
    webhookEventDetails.setWebhookEventType(webhookEventUtils.obtainEventType(webhookSource, httpHeaders));
    webhookEventDetails.setPrAction(webhookEventUtils.obtainPrAction(webhookSource, payLoadMap));
    webhookEventDetails.setGitConnectorId(webhookTriggerCondition.getGitConnectorId());
    webhookEventDetails.setFilePaths(webhookTriggerCondition.getFilePaths());
    webhookEventDetails.setRepoName(webhookTriggerCondition.getRepoName());

    for (Entry<String, String> entry : workflowVariables.entrySet()) {
      String param = entry.getKey();
      String paramValue = entry.getValue();
      try {
        if (isNotEmpty(paramValue)) {
          Object evalutedValue = expressionEvaluator.substitute(paramValue, payLoadMap);
          if (evalutedValue != null) {
            resolvedParameters.put(param, String.valueOf(evalutedValue));
          } else {
            resolvedParameters.put(param, paramValue);
          }
        }
      } catch (Exception e) {
        log.warn("Failed to resolve the param {} in Json {}", param, payload);
      }
    }
    webhookEventDetails.setParameters(resolvedParameters);
    return resolvedParameters;
  }

  @VisibleForTesting
  void validateWebHook(WebhookSource webhookSource, Trigger trigger, WebHookTriggerCondition triggerCondition,
      Map<String, Object> payLoadMap, HttpHeaders httpHeaders, String payload) {
    if (WebhookSource.GITHUB == webhookSource) {
      validateGitHubWebhook(trigger, triggerCondition, payLoadMap, httpHeaders);

      String accountId = getAccountId(trigger);
      if (featureFlagService.isEnabled(GITHUB_WEBHOOK_AUTHENTICATION, accountId)) {
        String gitHubHashedPayload = httpHeaders == null ? null : httpHeaders.getHeaderString(X_HUB_SIGNATURE_256);
        validateWebHookSecret(webhookSource, trigger, triggerCondition, gitHubHashedPayload, payload, accountId);
      }
    } else if (WebhookSource.BITBUCKET == webhookSource) {
      validateBitBucketWebhook(trigger, triggerCondition, httpHeaders);
    }
  }

  private void validateWebHookSecret(WebhookSource webhookSource, Trigger trigger,
      WebHookTriggerCondition triggerCondition, String hashedPayload, String payLoad, String accountId) {
    String webHookSecret = triggerCondition.getWebHookSecret();

    Application app = appService.get(trigger.getAppId());

    if (Boolean.TRUE.equals(app.getAreWebHookSecretsMandated())) {
      if (isEmpty(webHookSecret) || isEmpty(hashedPayload)) {
        throw new InvalidRequestException("WebHookSecrets are mandated for Harness trigger! " + webhookSource);
      }
    } else {
      if (isEmpty(webHookSecret) && isEmpty(hashedPayload)) {
        return;
      }

      if (isNotEmpty(webHookSecret) && isEmpty(hashedPayload)) {
        throw new InvalidRequestException("Harness trigger has webhook secret but its not present in " + webhookSource);
      }
      if (isEmpty(webHookSecret) && isNotEmpty(hashedPayload)) {
        throw new InvalidRequestException(
            "Webhook secret is present in " + webhookSource + " but harness trigger doesn't have it");
      }
    }
    Optional<EncryptedDataDetail> encryptedDataDetail =
        secretManager.encryptedDataDetails(accountId, null, webHookSecret, null);
    if (!encryptedDataDetail.isPresent()) {
      throw new InvalidRequestException("Error fetching the secret from database");
    }

    DelegateTask delegateTask =
        DelegateTask.builder()
            .accountId(accountId)
            .data(TaskData.builder()
                      .async(false)
                      .taskType(WEBHOOK_TRIGGER_TASK.name())
                      .timeout(DEFAULT_SYNC_CALL_TIMEOUT)
                      .parameters(new Object[] {WebhookTriggerParameters.builder()
                                                    .eventPayload(payLoad)
                                                    .webhookSource(webhookSource)
                                                    .encryptedDataDetail(encryptedDataDetail.get())
                                                    .hashedPayload(hashedPayload)
                                                    .build()})
                      .build())
            .build();

    WebHookTriggerResponseData webHookTriggerResponseData = null;
    try {
      webHookTriggerResponseData = delegateService.executeTaskV2(delegateTask);
    } catch (InterruptedException e) {
      log.error("Delegate Service executre task : validateWebHookSecret" + e);
    }

    if (ExecutionStatus.FAILED.equals(webHookTriggerResponseData.getExecutionStatus())) {
      throw new InvalidRequestException(
          "Error in webhook authentication: " + webHookTriggerResponseData.getErrorMessage());
    }
    if (!webHookTriggerResponseData.isWebhookAuthenticated()) {
      throw new InvalidRequestException(
          "Webhook secret present in Harness and " + webhookSource + " might be different");
    }
    log.info("Webhook is Authenticated");
  }

  private void validateBranchWithRegex(String storedBranch, List<String> inputBranchNames) {
    Pattern branchPattern = Pattern.compile(storedBranch);
    if (inputBranchNames.stream().anyMatch(inputBranchName -> branchPattern.matcher(inputBranchName).matches())) {
      return;
    }
    String msg = String.format(
        "WebHook event branch name filter [%s] does not match with the trigger condition branch name [%s]",
        inputBranchNames, storedBranch);
    throw new InvalidRequestException(msg, WingsException.USER);
  }

  private void validateBitBucketWebhook(
      Trigger trigger, WebHookTriggerCondition triggerCondition, HttpHeaders headers) {
    WebhookSource webhookSource = triggerCondition.getWebhookSource();
    if (BITBUCKET == webhookSource) {
      log.info("Trigger is set for BitBucket. Checking the http headers for the request type");
      String bitBucketEvent = headers == null ? null : headers.getHeaderString(X_BIT_BUCKET_EVENT);
      log.info("X-Event-Key is {} ", bitBucketEvent);
      if (bitBucketEvent == null) {
        throw new InvalidRequestException("Header [X-Event-Key] is missing", USER);
      }

      BitBucketEventType bitBucketEventType = BitBucketEventType.find(bitBucketEvent);
      String errorMsg = "Trigger [" + trigger.getName() + "] is not associated with the received BitBucket event ["
          + bitBucketEvent + "]";

      if (triggerCondition.getBitBucketEvents() != null
          && ((triggerCondition.getBitBucketEvents().contains(bitBucketEventType)
              || (BitBucketEventType.containsAllEvent(triggerCondition.getBitBucketEvents()))))) {
        return;
      } else {
        throw new InvalidRequestException(errorMsg, USER);
      }
    }
  }

  private void validateGitHubWebhook(
      Trigger trigger, WebHookTriggerCondition triggerCondition, Map<String, Object> content, HttpHeaders headers) {
    WebhookSource webhookSource = triggerCondition.getWebhookSource();
    if (GITHUB == webhookSource) {
      log.info("Trigger is set for GitHub. Checking the http headers for the request type");
      String gitHubEvent = headers == null ? null : headers.getHeaderString(X_GIT_HUB_EVENT);
      log.info("X-GitHub-Event is {} ", gitHubEvent);
      if (gitHubEvent == null) {
        throw new InvalidRequestException("Header [X-GitHub-Event] is missing", USER);
      }
      WebhookEventType webhookEventType = WebhookEventType.find(gitHubEvent);

      String errorMsg =
          "Trigger [" + trigger.getName() + "] is not associated with the received GitHub event [" + gitHubEvent + "]";

      validateEventType(triggerCondition, content, errorMsg, webhookEventType);
      Object gitEventAction = content.get("action");
      if (gitEventAction != null) {
        validateGitEventAction(triggerCondition, gitEventAction.toString(), webhookEventType, trigger.getName());
      } else {
        if (triggerCondition.getReleaseActions() != null || triggerCondition.getActions() != null) {
          throw new InvalidRequestException("action is missing in payload but is required for the trigger", USER);
        }
      }
    }
  }

  private void validateGitEventAction(WebHookTriggerCondition triggerCondition, String gitEventAction,
      WebhookEventType webhookEventType, String triggerName) {
    String msg =
        "Trigger [" + triggerName + "] is not associated with the received GitHub action [" + gitEventAction + "]";
    switch (webhookEventType) {
      case PULL_REQUEST:
        if (triggerCondition.getActions() != null
            && !triggerCondition.getActions().contains(GithubAction.find(gitEventAction))) {
          throw new InvalidRequestException(msg, USER);
        }
        break;
      case RELEASE:
        if (triggerCondition.getReleaseActions() != null
            && !triggerCondition.getReleaseActions().contains(ReleaseAction.find(gitEventAction))) {
          throw new InvalidRequestException(msg, USER);
        }
        break;
      case PACKAGE:
        String eventAction = webhookEventType.getValue() + ":" + gitEventAction;
        if (triggerCondition.getActions() != null
            && !triggerCondition.getActions().contains(GithubAction.find(eventAction))) {
          throw new InvalidRequestException(msg, USER);
        }
        break;
      default:
        log.warn("Action" + gitEventAction + "present not present in trigger" + triggerName
            + "but provided in github webhook payload");
    }
  }

  private void validateEventType(WebHookTriggerCondition triggerCondition, Map<String, Object> content,
      String errorMessage, WebhookEventType webhookEventType) {
    if (triggerCondition.getEventTypes() != null && !triggerCondition.getEventTypes().contains(webhookEventType)) {
      throw new InvalidRequestException(errorMessage, USER);
    }
  }

  @VisibleForTesting
  public Response constructSuccessResponse(String appId, String accountId, WorkflowExecution workflowExecution) {
    WebHookResponse webHookResponse = WebHookResponse.builder()
                                          .requestId(workflowExecution.getUuid())
                                          .status(workflowExecution.getStatus().name())
                                          .apiUrl(getApiUrl(accountId, appId, workflowExecution.getUuid()))
                                          .uiUrl(getUiUrl(workflowExecution.getWorkflowType(), accountId, appId,
                                              workflowExecution.getEnvId(), workflowExecution.getUuid()))
                                          .uiSetupUrl(getUiSetupUrl(workflowExecution.getWorkflowType(), accountId,
                                              appId, workflowExecution.getWorkflowId()))
                                          .build();

    return prepareResponse(webHookResponse, Response.Status.OK);
  }

  private Response prepareResponse(WebHookResponse webhookResponse, Response.Status status) {
    return Response.status(status).entity(webhookResponse).type(MediaType.APPLICATION_JSON).build();
  }
}
