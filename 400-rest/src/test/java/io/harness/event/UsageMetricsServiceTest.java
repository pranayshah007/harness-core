/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.event;

import static io.harness.beans.EnvironmentType.NON_PROD;
import static io.harness.beans.EnvironmentType.PROD;
import static io.harness.persistence.HQuery.excludeAuthority;
import static io.harness.rule.OwnerRule.PRAVEEN;
import static io.harness.rule.OwnerRule.SOWMYA;

import static software.wings.beans.AccountStatus.ACTIVE;
import static software.wings.beans.AccountType.COMMUNITY;
import static software.wings.beans.AccountType.PAID;
import static software.wings.common.VerificationConstants.CV_META_DATA;
import static software.wings.common.VerificationConstants.VERIFICATION_PROVIDER_TYPE_LOG;
import static software.wings.common.VerificationConstants.VERIFICATION_PROVIDER_TYPE_METRIC;

import static org.apache.cxf.ws.addressing.ContextUtils.generateUUID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.harness.beans.EnvironmentType;
import io.harness.category.element.UnitTests;
import io.harness.event.handler.impl.EventPublishHelper;
import io.harness.event.model.GenericEvent;
import io.harness.event.usagemetrics.UsageMetricsService;
import io.harness.metrics.HarnessMetricRegistry;
import io.harness.rule.Owner;

import software.wings.WingsBaseTest;
import software.wings.beans.Account;
import software.wings.beans.Account.Builder;
import software.wings.beans.Environment;
import software.wings.beans.LicenseInfo;
import software.wings.dl.WingsPersistence;
import software.wings.scheduler.UsageMetricsHandler;
import software.wings.sm.StateType;
import software.wings.verification.CVConfiguration;
import software.wings.verification.datadog.DatadogCVServiceConfiguration;
import software.wings.verification.newrelic.NewRelicCVServiceConfiguration;

import com.google.common.collect.Lists;
import com.google.inject.Inject;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class UsageMetricsServiceTest extends WingsBaseTest {
  @Mock private HarnessMetricRegistry harnessMetricRegistry;
  @Mock private EventPublishHelper eventPublishHelper;

  @Inject UsageMetricsService usageMetricsService;
  @Inject UsageMetricsHandler usageMetricsHandler;

  @Inject WingsPersistence wingsPersistence;

  private String envId;
  private String serviceId;
  private String appId;
  private String connectorId;
  private String accountId;
  private String accountIdCommunity;
  private Account account;
  private String nonProdEnvId;

  @Before
  public void setup() throws IllegalAccessException {
    accountId = generateUUID();
    accountIdCommunity = generateUUID();
    envId = generateUUID();
    serviceId = generateUUID();
    appId = generateUUID();
    connectorId = generateUUID();
    nonProdEnvId = generateUUID();
    MockitoAnnotations.initMocks(this);
    FieldUtils.writeField(usageMetricsService, "harnessMetricRegistry", harnessMetricRegistry, true);
    FieldUtils.writeField(usageMetricsService, "eventPublishHelper", eventPublishHelper, true);

    account = Builder.anAccount()
                  .withAccountName("test")
                  .withLicenseInfo(LicenseInfo.builder().accountStatus(ACTIVE).accountType(PAID).build())
                  .withUuid(accountId)
                  .build();
    wingsPersistence.save(account);
  }

  private void setBasicInfo(CVConfiguration cvServiceConfiguration, StateType stateType, String name) {
    cvServiceConfiguration.setUuid(generateUUID());
    cvServiceConfiguration.setStateType(stateType);
    cvServiceConfiguration.setAccountId(accountId);
    cvServiceConfiguration.setServiceId(serviceId);
    cvServiceConfiguration.setConnectorId(connectorId);
    cvServiceConfiguration.setEnvId(envId);
    cvServiceConfiguration.setAppId(appId);
    cvServiceConfiguration.setEnabled24x7(true);
    cvServiceConfiguration.setName(name);
  }

  @Test
  @Owner(developers = PRAVEEN)
  @Category(UnitTests.class)
  public void testEmitCVMetricsHappyCase() {
    NewRelicCVServiceConfiguration nrConfig = NewRelicCVServiceConfiguration.builder().applicationId("appId").build();
    setBasicInfo(nrConfig, StateType.NEW_RELIC, "nrConfig");
    DatadogCVServiceConfiguration ddConfig =
        DatadogCVServiceConfiguration.builder().datadogServiceName("testService").build();
    setBasicInfo(ddConfig, StateType.DATA_DOG, "ddConfig");

    wingsPersistence.save(Arrays.asList(nrConfig, ddConfig));

    doNothing().when(harnessMetricRegistry).recordGaugeValue(any(), any(String[].class), anyInt());
    usageMetricsHandler.handle(account);
    ArgumentCaptor<String> taskCaptorName = ArgumentCaptor.forClass(String.class);
    ArgumentCaptor<String[]> taskCaptorParams = ArgumentCaptor.forClass(String[].class);
    ArgumentCaptor<Double> taskCaptorValue = ArgumentCaptor.forClass(Double.class);

    verify(harnessMetricRegistry, times(2))
        .recordGaugeValue(taskCaptorName.capture(), taskCaptorParams.capture(), taskCaptorValue.capture());

    String[] params = taskCaptorParams.getValue();

    assertThat(CV_META_DATA).isEqualTo(taskCaptorName.getValue());
    assertThat(params.length).isEqualTo(3);
    assertThat(taskCaptorValue.getValue().intValue()).isEqualTo(1);

    List<GenericEvent> events = wingsPersistence.createQuery(GenericEvent.class, excludeAuthority).asList();
    assertThat(events).isEmpty();
  }

  @Test
  @Owner(developers = PRAVEEN)
  @Category(UnitTests.class)
  public void testEmitCVMetricsOnePaidOneCommunity() {
    Account comnunityAccount =
        Builder.anAccount()
            .withAccountName("test")
            .withLicenseInfo(LicenseInfo.builder().accountStatus(ACTIVE).accountType(COMMUNITY).build())
            .withUuid(accountIdCommunity)
            .build();
    wingsPersistence.save(Arrays.asList(account, comnunityAccount));

    NewRelicCVServiceConfiguration nrConfig = NewRelicCVServiceConfiguration.builder().applicationId("appId").build();
    setBasicInfo(nrConfig, StateType.NEW_RELIC, "nrConfig");
    DatadogCVServiceConfiguration ddConfig =
        DatadogCVServiceConfiguration.builder().datadogServiceName("testService").build();
    setBasicInfo(ddConfig, StateType.DATA_DOG, "ddConfig");

    wingsPersistence.save(Arrays.asList(nrConfig, ddConfig));

    nrConfig.setAccountId(comnunityAccount.getUuid());
    nrConfig.setUuid("communityId");
    wingsPersistence.save(nrConfig);

    doNothing().when(harnessMetricRegistry).recordGaugeValue(any(), any(String[].class), anyInt());
    usageMetricsHandler.handle(account);
    usageMetricsHandler.handle(comnunityAccount);
    ArgumentCaptor<String> taskCaptorName = ArgumentCaptor.forClass(String.class);
    ArgumentCaptor<String[]> taskCaptorParams = ArgumentCaptor.forClass(String[].class);
    ArgumentCaptor<Double> taskCaptorValue = ArgumentCaptor.forClass(Double.class);

    // validate that this is called only for 2 configs and not 3 since the 3rd one is for a community account
    verify(harnessMetricRegistry, times(2))
        .recordGaugeValue(taskCaptorName.capture(), taskCaptorParams.capture(), taskCaptorValue.capture());
  }

  private Environment createEnv(String id, EnvironmentType type) {
    Environment environment = new Environment();
    environment.setAccountId(accountId);
    environment.setUuid(id);
    environment.setEnvironmentType(type);
    return environment;
  }

  @Test
  @Owner(developers = SOWMYA)
  @Category(UnitTests.class)
  public void testCreateSetupEventsForTimescaleDB() {
    Environment prodEnv = createEnv(envId, PROD);
    Environment nonProdEnv = createEnv(nonProdEnvId, EnvironmentType.NON_PROD);
    wingsPersistence.save(Lists.newArrayList(prodEnv, nonProdEnv));

    NewRelicCVServiceConfiguration nrConfigProdEnabled =
        NewRelicCVServiceConfiguration.builder().applicationId("appId").build();
    setBasicInfo(nrConfigProdEnabled, StateType.NEW_RELIC, "nrConfig");
    DatadogCVServiceConfiguration ddConfigProdEnabled =
        DatadogCVServiceConfiguration.builder().datadogServiceName("testService").build();
    setBasicInfo(ddConfigProdEnabled, StateType.DATA_DOG_LOG, "ddConfig");

    NewRelicCVServiceConfiguration nrConfigProdDisabled =
        NewRelicCVServiceConfiguration.builder().applicationId("appId").build();
    setBasicInfo(nrConfigProdDisabled, StateType.NEW_RELIC, "nrConfig");
    DatadogCVServiceConfiguration ddConfigProdDisabled =
        DatadogCVServiceConfiguration.builder().datadogServiceName("testService").build();
    setBasicInfo(ddConfigProdDisabled, StateType.DATA_DOG_LOG, "ddConfig");
    nrConfigProdDisabled.setEnabled24x7(false);
    ddConfigProdDisabled.setEnabled24x7(false);

    NewRelicCVServiceConfiguration nrConfigNonProd =
        NewRelicCVServiceConfiguration.builder().applicationId("appId").build();
    setBasicInfo(nrConfigNonProd, StateType.NEW_RELIC, "nrConfig");
    DatadogCVServiceConfiguration ddConfigNonProd =
        DatadogCVServiceConfiguration.builder().datadogServiceName("testService").build();
    setBasicInfo(ddConfigNonProd, StateType.DATA_DOG_LOG, "ddConfig");
    nrConfigNonProd.setEnvId(nonProdEnvId);
    ddConfigNonProd.setEnvId(nonProdEnvId);

    wingsPersistence.save(Arrays.asList(nrConfigProdEnabled, ddConfigProdEnabled, nrConfigProdDisabled,
        ddConfigProdDisabled, nrConfigNonProd, ddConfigNonProd));

    usageMetricsService.createSetupEventsForTimescaleDB(account);

    ArgumentCaptor<Account> taskCaptorAccount = ArgumentCaptor.forClass(Account.class);
    ArgumentCaptor<String> taskCaptorVerificationProviderType = ArgumentCaptor.forClass(String.class);
    ArgumentCaptor<List> taskCaptorConfigIds = ArgumentCaptor.forClass(List.class);
    ArgumentCaptor<Long> taskCaptorAlertsSetup = ArgumentCaptor.forClass(Long.class);
    ArgumentCaptor<EnvironmentType> taskCaptorEnvironmentType = ArgumentCaptor.forClass(EnvironmentType.class);
    ArgumentCaptor<Boolean> taskCaptorEnabled = ArgumentCaptor.forClass(Boolean.class);

    verify(eventPublishHelper, times(6))
        .publishServiceGuardSetupEvent(taskCaptorAccount.capture(), taskCaptorVerificationProviderType.capture(),
            taskCaptorConfigIds.capture(), taskCaptorAlertsSetup.capture(), taskCaptorEnvironmentType.capture(),
            taskCaptorEnabled.capture());

    List<Account> accounts = taskCaptorAccount.getAllValues();
    List<String> providerTypes = taskCaptorVerificationProviderType.getAllValues();
    List<List> configIds = taskCaptorConfigIds.getAllValues();
    List<Long> alertsSetup = taskCaptorAlertsSetup.getAllValues();
    List<EnvironmentType> environmentTypes = taskCaptorEnvironmentType.getAllValues();
    List<Boolean> enabled = taskCaptorEnabled.getAllValues();

    int nrConfigProdEnabledIndex = configIds.indexOf(Collections.singletonList(nrConfigProdEnabled.getUuid()));
    assertThat(accounts.get(nrConfigProdEnabledIndex)).isEqualTo(account);
    assertThat(providerTypes.get(nrConfigProdEnabledIndex)).isEqualTo(VERIFICATION_PROVIDER_TYPE_METRIC);
    assertThat(alertsSetup.get(nrConfigProdEnabledIndex)).isEqualTo(0);
    assertThat(environmentTypes.get(nrConfigProdEnabledIndex)).isEqualTo(PROD);
    assertThat(enabled.get(nrConfigProdEnabledIndex)).isTrue();

    int ddConfigProdEnabledIndex = configIds.indexOf(Collections.singletonList(ddConfigProdEnabled.getUuid()));
    assertThat(accounts.get(ddConfigProdEnabledIndex)).isEqualTo(account);
    assertThat(providerTypes.get(ddConfigProdEnabledIndex)).isEqualTo(VERIFICATION_PROVIDER_TYPE_LOG);
    assertThat(alertsSetup.get(ddConfigProdEnabledIndex)).isEqualTo(0);
    assertThat(environmentTypes.get(ddConfigProdEnabledIndex)).isEqualTo(PROD);
    assertThat(enabled.get(ddConfigProdEnabledIndex)).isTrue();

    int nrConfigProdDisabledIndex = configIds.indexOf(Collections.singletonList(nrConfigProdDisabled.getUuid()));
    assertThat(accounts.get(nrConfigProdDisabledIndex)).isEqualTo(account);
    assertThat(providerTypes.get(nrConfigProdDisabledIndex)).isEqualTo(VERIFICATION_PROVIDER_TYPE_METRIC);
    assertThat(alertsSetup.get(nrConfigProdDisabledIndex)).isEqualTo(0);
    assertThat(environmentTypes.get(nrConfigProdDisabledIndex)).isEqualTo(PROD);
    assertThat(enabled.get(nrConfigProdDisabledIndex)).isFalse();

    int ddConfigProdDisabledIndex = configIds.indexOf(Collections.singletonList(ddConfigProdDisabled.getUuid()));
    assertThat(accounts.get(ddConfigProdDisabledIndex)).isEqualTo(account);
    assertThat(providerTypes.get(ddConfigProdDisabledIndex)).isEqualTo(VERIFICATION_PROVIDER_TYPE_LOG);
    assertThat(alertsSetup.get(ddConfigProdDisabledIndex)).isEqualTo(0);
    assertThat(environmentTypes.get(ddConfigProdDisabledIndex)).isEqualTo(PROD);
    assertThat(enabled.get(ddConfigProdDisabledIndex)).isFalse();

    int nrConfigNonProdIndex = configIds.indexOf(Collections.singletonList(nrConfigNonProd.getUuid()));
    assertThat(accounts.get(nrConfigNonProdIndex)).isEqualTo(account);
    assertThat(providerTypes.get(nrConfigNonProdIndex)).isEqualTo(VERIFICATION_PROVIDER_TYPE_METRIC);
    assertThat(alertsSetup.get(nrConfigNonProdIndex)).isEqualTo(0);
    assertThat(environmentTypes.get(nrConfigNonProdIndex)).isEqualTo(NON_PROD);
    assertThat(enabled.get(nrConfigNonProdIndex)).isTrue();

    int ddConfigNonProdIndex = configIds.indexOf(Collections.singletonList(ddConfigNonProd.getUuid()));
    assertThat(accounts.get(ddConfigNonProdIndex)).isEqualTo(account);
    assertThat(providerTypes.get(ddConfigNonProdIndex)).isEqualTo(VERIFICATION_PROVIDER_TYPE_LOG);
    assertThat(alertsSetup.get(ddConfigNonProdIndex)).isEqualTo(0);
    assertThat(environmentTypes.get(ddConfigNonProdIndex)).isEqualTo(NON_PROD);
    assertThat(enabled.get(ddConfigNonProdIndex)).isTrue();
  }
}
