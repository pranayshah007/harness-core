/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.delegate.beans.TaskData.DEFAULT_ASYNC_CALL_TIMEOUT;
import static io.harness.microservice.NotifyEngineTarget.GENERAL;

import io.harness.annotations.dev.BreakDependencyOn;
import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.beans.Cd1SetupFields;
import io.harness.beans.CgEventConfig;
import io.harness.beans.DelegateTask;
import io.harness.beans.Event;
import io.harness.beans.EventStatus;
import io.harness.beans.EventType;
import io.harness.beans.FeatureName;
import io.harness.beans.GenericEventDetail;
import io.harness.beans.KeyValuePair;
import io.harness.beans.WebHookEventConfig;
import io.harness.data.algorithm.HashGenerator;
import io.harness.delegate.beans.TaskData;
import io.harness.delegate.task.http.HttpTaskParameters;
import io.harness.expression.ExpressionReflectionUtils;
import io.harness.expression.LateBindingMap;
import io.harness.ff.FeatureFlagService;
import io.harness.serializer.JsonUtils;
import io.harness.service.EventConfigService;
import io.harness.service.EventService;
import io.harness.waiter.WaitNotifyEngine;

import software.wings.api.HttpStateExecutionData;
import software.wings.api.HttpStateExecutionData.HttpStateExecutionDataBuilder;
import software.wings.beans.Account;
import software.wings.beans.Application;
import software.wings.beans.TaskType;
import software.wings.delegatetasks.event.EventsDeliveryCallback;
import software.wings.expression.ManagerExpressionEvaluator;
import software.wings.expression.ManagerPreviewExpressionEvaluator;
import software.wings.expression.SecretFunctor;
import software.wings.service.intfc.AccountService;
import software.wings.service.intfc.AppService;
import software.wings.service.intfc.DelegateService;
import software.wings.service.intfc.security.ManagerDecryptionService;
import software.wings.service.intfc.security.SecretManager;
import software.wings.sm.StateExecutionContext;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(CDC)
@TargetModule(HarnessModule._815_CG_TRIGGERS)
@Singleton
@Slf4j
@BreakDependencyOn("software.wings.service.intfc.DelegateService")
public class EventDeliveryService {
  public static final Long DELEGATE_QUEUE_TIMEOUT = Duration.ofSeconds(6).toMillis();
  @Inject private DelegateService delegateService;
  @Inject private WaitNotifyEngine waitNotifyEngine;
  @Inject private EventConfigService eventConfigService;
  @Inject private EventService eventService;
  @Inject private FeatureFlagService featureFlagService;
  @Inject private SecretManager secretManager;
  @Inject private ManagerDecryptionService managerDecryptionService;
  @Inject private AccountService accountService;
  @Inject private AppService appService;
  @Inject private ManagerExpressionEvaluator evaluator;

  public void deliveryEvent(Event event, String permitId) {
    if (event == null) {
      return;
    }
    CgEventConfig eventConfig =
        eventConfigService.getEventsConfig(event.getAccountId(), event.getAppId(), event.getEventConfigId());
    if (eventConfig == null) {
      log.warn(
          "App telemetry feature is disabled or the event config does not exist. Marking the event as skipped. Event ID - {}",
          event.getUuid());
      eventService.updateEventStatus(event.getUuid(), EventStatus.SKIPPED,
          new GenericEventDetail("App telemetry feature is disabled or the event config does not exist"));
      return;
    }
    if (!eventConfig.isEnabled() && !EventType.TEST.name().equals(event.getPayload().getEventType())) {
      log.warn("The event config is disabled. Marking the event as skipped. Event ID - {}", event.getUuid());
      eventService.updateEventStatus(event.getUuid(), EventStatus.SKIPPED,
          new GenericEventDetail("The event config is set to disabled. Hence skipping the delivery"));
      return;
    }

    String accountId = eventConfig.getAccountId();

    String waitId = generateUuid();
    Account account = accountService.getAccountWithDefaults(accountId);
    boolean isCertValidationRequired = featureFlagService.isEnabled(FeatureName.ENABLE_CERT_VALIDATION, accountId);
    boolean useHeaderForCapabilityCheck =
        featureFlagService.isEnabled(FeatureName.HTTP_HEADERS_CAPABILITY_CHECK, accountId);

    WebHookEventConfig webHookEventConfig = eventConfig.getConfig();
    List<KeyValuePair> headers =
        isNotEmpty(eventConfig.getConfig().getHeaders()) ? eventConfig.getConfig().getHeaders() : new ArrayList<>();
    headers.add(KeyValuePair.builder().key("Content-Type").value("application/json").build());
    HttpTaskParameters httpTaskParameters = HttpTaskParameters.builder()
                                                .method("POST")
                                                .body(JsonUtils.asJson(event.getPayload()))
                                                .url(eventConfig.getConfig().getUrl())
                                                .headers(headers)
                                                .socketTimeoutMillis(eventConfig.getConfig().getSocketTimeoutMillis())
                                                .useProxy(eventConfig.getConfig().isUseProxy())
                                                .isCertValidationRequired(isCertValidationRequired)
                                                .useHeaderForCapabilityCheck(useHeaderForCapabilityCheck)
                                                .build();

    HttpStateExecutionDataBuilder executionDataBuilder =
        HttpStateExecutionData.builder().useProxy(webHookEventConfig.isUseProxy());
    Application app = appService.getApplicationWithDefaults(eventConfig.getAppId());
    int expressionFunctorToken = HashGenerator.generateIntegerHash();
    ExpressionReflectionUtils.applyExpression(httpTaskParameters,
        (secretMode, value)
            -> renderExpression(account, app, value,
                StateExecutionContext.builder()
                    .stateExecutionData(executionDataBuilder.build())
                    .adoptDelegateDecryption(true)
                    .expressionFunctorToken(expressionFunctorToken)
                    .build()));
    ManagerPreviewExpressionEvaluator expressionEvaluator = new ManagerPreviewExpressionEvaluator();

    if (isNotEmpty(httpTaskParameters.getHeaders())) {
      List<KeyValuePair> httpHeaders =
          httpTaskParameters.getHeaders()
              .stream()
              .map(pair
                  -> KeyValuePair.builder()
                         .key(expressionEvaluator.substitute(pair.getKey(), Collections.emptyMap()))
                         .value(expressionEvaluator.substitute(pair.getValue(), Collections.emptyMap()))
                         .build())
              .collect(Collectors.toList());
      executionDataBuilder.headers(httpHeaders);
    }
    executionDataBuilder.httpUrl(expressionEvaluator.substitute(httpTaskParameters.getUrl(), Collections.emptyMap()))
        .httpMethod(expressionEvaluator.substitute(httpTaskParameters.getMethod(), Collections.emptyMap()))
        .warningMessage("warningMessage");
    DelegateTask delegateTask = DelegateTask.builder()
                                    .accountId(accountId)
                                    .description("Http Execution")
                                    .waitId(waitId)
                                    .tags(eventConfig.getDelegateSelectors())
                                    .setupAbstraction(Cd1SetupFields.APP_ID_FIELD, eventConfig.getAppId())
                                    .data(TaskData.builder()
                                              .async(true)
                                              .taskType(TaskType.HTTP.name())
                                              .parameters(new Object[] {httpTaskParameters})
                                              .timeout(DEFAULT_ASYNC_CALL_TIMEOUT)
                                              .expressionFunctorToken(expressionFunctorToken)
                                              .build())
                                    .selectionLogsTrackingEnabled(true)
                                    .build();

    waitNotifyEngine.waitForAllOn(GENERAL,
        new EventsDeliveryCallback(accountId, event.getAppId(), eventConfig.getUuid(), event.getUuid(), permitId),
        waitId);
    log.info("Queuing delegate task for event delivery with waitId {}", waitId);
    String taskId = delegateService.queueTask(delegateTask);
    log.info("Queued delegate taskId {} for event delivery", taskId);
  }

  private String renderExpression(
      Account account, Application app, String expression, StateExecutionContext stateExecutionContext) {
    Map<String, Object> contextMap = new LateBindingMap();
    if (app != null) {
      contextMap.put("app", app);
      contextMap.put("account", account);
      contextMap.put("secrets",
          SecretFunctor.builder()
              .managerDecryptionService(managerDecryptionService)
              .secretManager(secretManager)
              .accountId(app.getAccountId())
              .appId(app.getUuid())
              .envId(null)
              .adoptDelegateDecryption(true)
              .expressionFunctorToken(
                  stateExecutionContext == null ? 0 : stateExecutionContext.getExpressionFunctorToken())
              .build());
    }
    return evaluator.substitute(expression, contextMap);
  }
}
