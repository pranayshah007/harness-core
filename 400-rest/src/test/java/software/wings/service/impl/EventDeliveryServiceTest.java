/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.delegate.beans.TaskData.DEFAULT_ASYNC_CALL_TIMEOUT;
import static io.harness.rule.OwnerRule.DEEPAK_PUTHRAYA;

import static junit.framework.TestCase.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.beans.Cd1SetupFields;
import io.harness.beans.CgEventConfig;
import io.harness.beans.DelegateTask;
import io.harness.beans.Event;
import io.harness.beans.EventPayload;
import io.harness.beans.EventStatus;
import io.harness.beans.EventType;
import io.harness.beans.KeyValuePair;
import io.harness.beans.WebHookEventConfig;
import io.harness.beans.event.TestEventPayload;
import io.harness.category.element.UnitTests;
import io.harness.delegate.beans.TaskData;
import io.harness.delegate.task.http.HttpTaskParameters;
import io.harness.ff.FeatureFlagService;
import io.harness.rule.Owner;
import io.harness.serializer.JsonUtils;
import io.harness.service.EventConfigService;
import io.harness.service.EventService;
import io.harness.waiter.WaitNotifyEngine;

import software.wings.WingsBaseTest;
import software.wings.beans.Account;
import software.wings.beans.Application;
import software.wings.beans.TaskType;
import software.wings.expression.ManagerExpressionEvaluator;
import software.wings.service.intfc.AccountService;
import software.wings.service.intfc.AppService;
import software.wings.service.intfc.DelegateService;
import software.wings.service.intfc.security.ManagerDecryptionService;
import software.wings.service.intfc.security.SecretManager;

import com.google.inject.Inject;
import java.util.Collections;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;

@OwnedBy(CDC)
@TargetModule(HarnessModule._815_CG_TRIGGERS)
public class EventDeliveryServiceTest extends WingsBaseTest {
  @Mock private DelegateService delegateService;
  @Mock private WaitNotifyEngine waitNotifyEngine;
  @Mock private EventConfigService eventConfigService;
  @Mock private EventService eventService;
  @Mock private FeatureFlagService featureFlagService;
  @Mock private SecretManager secretManager;
  @Mock private ManagerDecryptionService managerDecryptionService;
  @Mock private AccountService accountService;
  @Mock private AppService appService;
  @Mock private ManagerExpressionEvaluator evaluator;

  @Inject @InjectMocks private EventDeliveryService eventDeliveryService;

  private static final String PERMIT_ID = "__PERMIT_ID__";
  private static final String EVENT_ID = "__EVENT_ID__";
  private static final String EVENT_CONFIG_ID = "__EVENT_CONFIG_ID__";
  private static final String APP_ID = "__APP_ID__";
  private static final String ACCOUNT_ID = "__ACCOUNT_ID__";
  private static final String URL = "__URL__";
  private static final String WAIT_ID = "__WAIT_ID__";
  private static final WebHookEventConfig WEBHOOK_CONFIG = new WebHookEventConfig();
  private static final EventPayload TEST_EVENT_PAYLOAD =
      EventPayload.builder().version("v1").eventType(EventType.TEST.name()).data(new TestEventPayload()).build();
  private static final EventPayload NON_TEST_EVENT_PAYLOAD =
      EventPayload.builder().version("v1").eventType(EventType.PIPELINE_START.name()).data(null).build();

  @Before
  public void setUp() {
    WEBHOOK_CONFIG.setUrl(URL);
    WEBHOOK_CONFIG.setSocketTimeoutMillis(60000);
  }

  @Test
  @Owner(developers = DEEPAK_PUTHRAYA)
  @Category(UnitTests.class)
  public void shouldDeliveryEvent() {
    when(eventConfigService.getEventsConfig(anyString(), anyString(), eq(EVENT_CONFIG_ID)))
        .thenReturn(
            CgEventConfig.builder().accountId(ACCOUNT_ID).appId(APP_ID).enabled(false).config(WEBHOOK_CONFIG).build());
    when(accountService.getAccountWithDefaults(eq(ACCOUNT_ID))).thenReturn(Account.Builder.anAccount().build());
    when(appService.getApplicationWithDefaults(eq(APP_ID))).thenReturn(Application.Builder.anApplication().build());
    // Mocking the evaulator method to return return the expressions as is without modifying.
    doAnswer(invocation -> invocation.getArguments()[0].toString()).when(evaluator).substitute(anyString(), anyMap());

    Event event = Event.builder()
                      .uuid(EVENT_ID)
                      .accountId(ACCOUNT_ID)
                      .appId(APP_ID)
                      .eventConfigId(EVENT_CONFIG_ID)
                      .payload(TEST_EVENT_PAYLOAD)
                      .build();
    eventDeliveryService.deliveryEvent(event, PERMIT_ID);

    List<KeyValuePair> headers =
        Collections.singletonList(KeyValuePair.builder().key("Content-Type").value("application/json").build());

    HttpTaskParameters httpTaskParameters = HttpTaskParameters.builder()
                                                .method("POST")
                                                .body(JsonUtils.asJson(event.getPayload()))
                                                .url(URL)
                                                .headers(headers)
                                                .socketTimeoutMillis(60000)
                                                .useProxy(false)
                                                .isCertValidationRequired(false)
                                                .useHeaderForCapabilityCheck(false)
                                                .build();

    DelegateTask delegateTask = DelegateTask.builder()
                                    .accountId(ACCOUNT_ID)
                                    .description("Http Execution")
                                    .waitId(WAIT_ID)
                                    .tags(null)
                                    .setupAbstraction(Cd1SetupFields.APP_ID_FIELD, APP_ID)
                                    .data(TaskData.builder()
                                              .async(true)
                                              .taskType(TaskType.HTTP.name())
                                              .parameters(new Object[] {httpTaskParameters})
                                              .timeout(DEFAULT_ASYNC_CALL_TIMEOUT)
                                              .expressionFunctorToken(0)
                                              .build())
                                    .selectionLogsTrackingEnabled(true)
                                    .build();

    // We capture the argument and modify the waitId & expressionFunctorToken because the their values are auto
    // generated We are making those values constant & then proceed to compare
    ArgumentCaptor<DelegateTask> argument = ArgumentCaptor.forClass(DelegateTask.class);
    verify(delegateService, times(1)).queueTaskV2(argument.capture());
    DelegateTask capturedValue = argument.getValue();
    capturedValue.setWaitId(WAIT_ID);
    capturedValue.getData().setExpressionFunctorToken(0);
    assertEquals(argument.getValue(), delegateTask);
  }

  @Test
  @Owner(developers = DEEPAK_PUTHRAYA)
  @Category(UnitTests.class)
  public void shouldNotDeliverEvent() {
    when(eventConfigService.getEventsConfig(anyString(), anyString(), eq("doesNotExist"))).thenReturn(null);
    Event event1 =
        Event.builder().uuid(EVENT_ID).accountId(ACCOUNT_ID).appId(APP_ID).eventConfigId("doesNotExist").build();
    eventDeliveryService.deliveryEvent(event1, PERMIT_ID);

    when(eventConfigService.getEventsConfig(anyString(), anyString(), eq(EVENT_CONFIG_ID)))
        .thenReturn(CgEventConfig.builder().enabled(false).build());
    Event event2 = Event.builder()
                       .uuid(EVENT_ID)
                       .accountId(ACCOUNT_ID)
                       .appId(APP_ID)
                       .eventConfigId(EVENT_CONFIG_ID)
                       .payload(NON_TEST_EVENT_PAYLOAD)
                       .build();
    eventDeliveryService.deliveryEvent(event2, PERMIT_ID);

    verify(delegateService, never()).queueTaskV2(any());
    verify(eventService, times(2)).updateEventStatus(anyString(), eq(EventStatus.SKIPPED), any());
  }
}
