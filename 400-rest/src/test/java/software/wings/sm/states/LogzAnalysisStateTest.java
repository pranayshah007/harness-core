/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.sm.states;

import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.rule.OwnerRule.SOWMYA;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.beans.DelegateTask;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;
import io.harness.waiter.WaitNotifyEngine;

import software.wings.beans.Application;
import software.wings.beans.SettingAttribute;
import software.wings.beans.config.LogzConfig;
import software.wings.service.impl.logz.LogzDataCollectionInfo;
import software.wings.service.intfc.AppService;
import software.wings.service.intfc.DelegateService;
import software.wings.service.intfc.SettingsService;
import software.wings.service.intfc.security.SecretManager;
import software.wings.verification.VerificationStateAnalysisExecutionData;

import java.util.HashSet;
import java.util.Set;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

public class LogzAnalysisStateTest extends APMStateVerificationTestBase {
  @Mock private SettingsService settingsService;
  @Mock private AppService appService;
  @Mock private WaitNotifyEngine waitNotifyEngine;
  @Mock private DelegateService delegateService;
  @Mock private SecretManager secretManager;

  private LogzAnalysisState logzAnalysisState;
  private LogzConfig logzConfig;
  private String configId;
  private VerificationStateAnalysisExecutionData executionData;
  private Set<String> hosts;

  @Before
  public void setUp() throws IllegalAccessException {
    MockitoAnnotations.initMocks(this);

    String accountId = generateUuid();
    String appId = generateUuid();
    configId = generateUuid();
    logzAnalysisState = Mockito.spy(new LogzAnalysisState("logzState"));
    logzConfig = LogzConfig.builder().accountId(accountId).build();
    SettingAttribute settingAttribute = SettingAttribute.Builder.aSettingAttribute()
                                            .withAccountId(accountId)
                                            .withAppId(appId)
                                            .withValue(logzConfig)
                                            .build();
    executionData = VerificationStateAnalysisExecutionData.builder().build();
    hosts = new HashSet<>();
    hosts.add("host1");

    Application app = new Application();
    app.setName("name");

    FieldUtils.writeField(logzAnalysisState, "settingsService", settingsService, true);
    FieldUtils.writeField(logzAnalysisState, "appService", appService, true);
    FieldUtils.writeField(logzAnalysisState, "waitNotifyEngine", waitNotifyEngine, true);
    FieldUtils.writeField(logzAnalysisState, "delegateService", delegateService, true);
    FieldUtils.writeField(logzAnalysisState, "secretManager", secretManager, true);

    when(settingsService.get(configId)).thenReturn(settingAttribute);
    when(appService.get(any())).thenReturn(app);

    doReturn(configId).when(logzAnalysisState).getResolvedConnectorId(any(), any(), any());
    doReturn(workflowId).when(logzAnalysisState).getWorkflowId(any());
    doReturn(serviceId).when(logzAnalysisState).getPhaseServiceId(any());
  }

  @Test
  @Owner(developers = SOWMYA)
  @Category(UnitTests.class)
  public void testTriggerAnalysisDataCollection_whenConnectorIdIsValid() {
    logzAnalysisState.triggerAnalysisDataCollection(executionContext, executionData, hosts);

    ArgumentCaptor<DelegateTask> delegateTaskArgumentCaptor = ArgumentCaptor.forClass(DelegateTask.class);
    verify(delegateService, times(1)).queueTaskV2(delegateTaskArgumentCaptor.capture());

    DelegateTask task = delegateTaskArgumentCaptor.getValue();
    LogzDataCollectionInfo dataCollectionInfo = (LogzDataCollectionInfo) task.getData().getParameters()[0];

    assertThat(dataCollectionInfo.getLogzConfig()).isEqualTo(logzConfig);
  }

  @Test
  @Owner(developers = SOWMYA)
  @Category(UnitTests.class)
  public void testTriggerAnalysisDataCollection_whenConnectorIdIsInValid() {
    when(settingsService.get(configId)).thenReturn(null);
    assertThatThrownBy(() -> logzAnalysisState.triggerAnalysisDataCollection(executionContext, executionData, hosts))
        .isInstanceOf(NullPointerException.class)
        .hasMessage("No logz config setting with id: " + configId + " found");
  }
}
