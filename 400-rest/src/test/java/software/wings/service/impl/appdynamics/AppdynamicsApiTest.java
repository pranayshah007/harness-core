/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl.appdynamics;

import static io.harness.annotations.dev.HarnessTeam.CV;
import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.rule.OwnerRule.RAGHU;

import static software.wings.beans.dto.ThirdPartyApiCallLog.createApiCallLog;

import static java.util.Collections.emptyList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyBoolean;
import static org.mockito.Matchers.anyLong;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.delegate.beans.connector.appdynamicsconnector.AppDynamicsConnectorDTO;
import io.harness.delegate.task.common.DataCollectionExecutorService;
import io.harness.persistence.HPersistence;
import io.harness.rest.RestResponse;
import io.harness.rule.Owner;
import io.harness.secretmanagerclient.services.api.SecretManagerClientService;
import io.harness.security.encryption.SecretDecryptionService;

import software.wings.WingsBaseTest;
import software.wings.beans.AppDynamicsConfig;
import software.wings.beans.SettingAttribute;
import software.wings.beans.SettingAttribute.SettingCategory;
import software.wings.beans.SyncTaskContext;
import software.wings.beans.dto.ThirdPartyApiCallLog;
import software.wings.delegatetasks.DelegateLogService;
import software.wings.delegatetasks.DelegateProxyFactory;
import software.wings.delegatetasks.cv.RequestExecutor;
import software.wings.helpers.ext.appdynamics.AppdynamicsRestClient;
import software.wings.resources.AppdynamicsResource;
import software.wings.service.impl.appdynamics.AppdynamicsMetric.AppdynamicsMetricType;
import software.wings.service.impl.newrelic.NewRelicApplication;
import software.wings.service.intfc.appdynamics.AppdynamicsDelegateService;
import software.wings.service.intfc.appdynamics.AppdynamicsService;
import software.wings.service.intfc.security.EncryptionService;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.inject.Inject;
import java.io.IOException;
import java.security.SecureRandom;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import okhttp3.Request;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;
import retrofit2.Call;
import retrofit2.Response;

/**
 * Created by rsingh on 4/24/18.
 */
@OwnedBy(CV)
@Slf4j
public class AppdynamicsApiTest extends WingsBaseTest {
  private static final SecureRandom random = new SecureRandom();

  @Inject private AppdynamicsResource appdynamicsResource;
  @Inject private AppdynamicsService appdynamicsService;
  @Inject private HPersistence persistence;
  @Inject private EncryptionService encryptionService;
  @Inject private SecretDecryptionService secretDecryptionService;
  @Inject private RequestExecutor requestExecutor;
  @Inject private DataCollectionExecutorService dataCollectionService;
  @Mock private SecretManagerClientService ngSecretService;
  @Mock private DelegateProxyFactory delegateProxyFactory;
  @Mock private AppdynamicsRestClient appdynamicsRestClient;
  @Mock private DelegateLogService delegateLogService;

  private AppdynamicsDelegateServiceImpl delegateService;
  private String accountId;

  @Before
  public void setup() throws IllegalAccessException {
    delegateService = spy(new AppdynamicsDelegateServiceImpl());
    doReturn(appdynamicsRestClient).when(delegateService).getAppdynamicsRestClient(anyString());
    doReturn(appdynamicsRestClient).when(delegateService).getAppdynamicsRestClient(any(AppDynamicsConnectorDTO.class));
    when(delegateProxyFactory.getV2(eq(AppdynamicsDelegateService.class), any(SyncTaskContext.class)))
        .thenReturn(delegateService);
    doNothing().when(delegateLogService).save(anyString(), any(ThirdPartyApiCallLog.class));

    FieldUtils.writeField(appdynamicsService, "delegateProxyFactory", delegateProxyFactory, true);
    FieldUtils.writeField(appdynamicsService, "ngSecretService", ngSecretService, true);
    FieldUtils.writeField(appdynamicsResource, "appdynamicsService", appdynamicsService, true);
    FieldUtils.writeField(delegateService, "encryptionService", encryptionService, true);
    FieldUtils.writeField(delegateService, "secretDecryptionService", secretDecryptionService, true);
    FieldUtils.writeField(delegateService, "delegateLogService", delegateLogService, true);
    FieldUtils.writeField(delegateService, "requestExecutor", requestExecutor, true);
    FieldUtils.writeField(delegateService, "dataCollectionService", dataCollectionService, true);
    accountId = "TrPOn4AoS022KD8HzSDefA";
    when(ngSecretService.getEncryptionDetails(any(), any())).thenReturn(emptyList());
  }

  @Test
  @Owner(developers = RAGHU)
  @Category(UnitTests.class)
  public void testNullApplicationName() throws IOException {
    Call<List<NewRelicApplication>> restCall = mock(Call.class);
    handleClone(restCall);
    List<NewRelicApplication> allApplications =
        Lists.newArrayList(NewRelicApplication.builder().id(123).name(generateUuid()).build(),
            NewRelicApplication.builder().id(345).build());
    when(restCall.execute()).thenReturn(Response.success(allApplications));
    when(appdynamicsRestClient.listAllApplications(anyString())).thenReturn(restCall);

    String savedAttributeId = saveAppdynamicsConfig();
    SettingAttribute settingAttribute = persistence.get(SettingAttribute.class, savedAttributeId);
    ((AppDynamicsConfig) settingAttribute.getValue()).setPassword(UUID.randomUUID().toString().toCharArray());
    final List<NewRelicApplication> applications = appdynamicsService.getApplications(savedAttributeId);
    assertThat(applications.size()).isEqualTo(1);
    assertThat(applications.get(0).getId()).isEqualTo(123);
  }

  @Test
  @Owner(developers = RAGHU)
  @Category(UnitTests.class)
  public void testGetApplications() throws IOException {
    List<NewRelicApplication> applications = Lists.newArrayList(
        NewRelicApplication.builder().name(UUID.randomUUID().toString()).id(random.nextInt()).build(),
        NewRelicApplication.builder().name(UUID.randomUUID().toString()).id(random.nextInt()).build());

    List<NewRelicApplication> sortedApplicationsByName =
        applications.stream().sorted(Comparator.comparing(NewRelicApplication::getName)).collect(Collectors.toList());

    String savedAttributeId = saveAppdynamicsConfig();

    Call<List<NewRelicApplication>> restCall = mock(Call.class);
    handleClone(restCall);
    when(restCall.execute()).thenReturn(Response.success(applications));
    when(appdynamicsRestClient.listAllApplications(anyString())).thenReturn(restCall);
    RestResponse<List<NewRelicApplication>> allApplications =
        appdynamicsResource.getAllApplications(accountId, savedAttributeId);
    assertThat(allApplications.getResponseMessages().isEmpty()).isTrue();
    assertThat(allApplications.getResource()).isEqualTo(sortedApplicationsByName);
  }

  @Test
  @Owner(developers = RAGHU)
  @Category(UnitTests.class)
  public void testGetTiers() throws IOException {
    Set<AppdynamicsTier> tiers =
        Sets.newHashSet(AppdynamicsTier.builder().name(UUID.randomUUID().toString()).id(random.nextInt()).build(),
            AppdynamicsTier.builder().name(UUID.randomUUID().toString()).id(random.nextInt()).build());
    Call<Set<AppdynamicsTier>> restCall = mock(Call.class);
    handleClone(restCall);
    when(restCall.execute()).thenReturn(Response.success(tiers));
    when(appdynamicsRestClient.listTiers(anyString(), anyLong())).thenReturn(restCall);

    String savedAttributeId = saveAppdynamicsConfig();

    RestResponse<Set<AppdynamicsTier>> allTiers =
        appdynamicsResource.getAllTiers(accountId, savedAttributeId, random.nextLong());
    assertThat(allTiers.getResponseMessages().isEmpty()).isTrue();
    assertThat(allTiers.getResource()).isEqualTo(tiers);
  }

  @Test
  @Owner(developers = RAGHU)
  @Category(UnitTests.class)
  public void testGetBTs() throws IOException {
    Call<List<AppdynamicsTier>> tierRestCall = mock(Call.class);
    handleClone(tierRestCall);
    when(tierRestCall.execute())
        .thenReturn(Response.success(Lists.newArrayList(
            AppdynamicsTier.builder().name(UUID.randomUUID().toString()).id(random.nextInt()).build())));
    when(appdynamicsRestClient.getTierDetails(anyString(), anyLong(), anyLong())).thenReturn(tierRestCall);

    Call<List<AppdynamicsMetric>> btsCall = mock(Call.class);
    handleClone(btsCall);
    List<AppdynamicsMetric> bts = Lists.newArrayList(
        AppdynamicsMetric.builder().name(UUID.randomUUID().toString()).type(AppdynamicsMetricType.leaf).build(),
        AppdynamicsMetric.builder().name(UUID.randomUUID().toString()).type(AppdynamicsMetricType.leaf).build());
    when(btsCall.execute()).thenReturn(Response.success(bts));
    when(appdynamicsRestClient.listMetrices(anyString(), anyLong(), anyString())).thenReturn(btsCall);

    AppDynamicsConfig appDynamicsConfig = AppDynamicsConfig.builder()
                                              .accountId(accountId)
                                              .controllerUrl(UUID.randomUUID().toString())
                                              .username(UUID.randomUUID().toString())
                                              .password(UUID.randomUUID().toString().toCharArray())
                                              .accountname(UUID.randomUUID().toString())
                                              .build();
    List<AppdynamicsMetric> tierBTMetrics = delegateService.getTierBTMetrics(
        appDynamicsConfig, random.nextLong(), random.nextLong(), emptyList(), createApiCallLog(accountId, null));
    assertThat(tierBTMetrics).hasSize(2);
    assertThat(tierBTMetrics).isEqualTo(bts);
  }

  @Test
  @Owner(developers = RAGHU)
  @Category(UnitTests.class)
  public void testGetBTData() throws IOException {
    Call<List<AppdynamicsTier>> tierRestCall = mock(Call.class);
    handleClone(tierRestCall);
    AppdynamicsTier tier = AppdynamicsTier.builder().name(UUID.randomUUID().toString()).id(random.nextInt()).build();
    List<AppdynamicsTier> tiers = Lists.newArrayList(tier);
    when(tierRestCall.execute()).thenReturn(Response.success(tiers));
    when(appdynamicsRestClient.getTierDetails(anyString(), anyLong(), anyLong())).thenReturn(tierRestCall);

    Call<List<AppdynamicsMetricData>> btDataCall = mock(Call.class);
    handleClone(btDataCall);
    List<AppdynamicsMetricData> btData =
        Lists.newArrayList(AppdynamicsMetricData.builder()
                               .metricId(random.nextLong())
                               .frequency(UUID.randomUUID().toString())
                               .metricName(UUID.randomUUID().toString())
                               .metricPath(UUID.randomUUID().toString())
                               .metricValues(Lists.newArrayList(AppdynamicsMetricDataValue.builder()
                                                                    .count(random.nextLong())
                                                                    .current(random.nextLong())
                                                                    .max(random.nextLong())
                                                                    .min(random.nextLong())
                                                                    .occurrences(random.nextInt())
                                                                    .standardDeviation(random.nextDouble())
                                                                    .sum(random.nextLong())
                                                                    .startTimeInMillis(random.nextLong())
                                                                    .useRange(random.nextBoolean())
                                                                    .value(random.nextDouble())
                                                                    .build(),
                                   AppdynamicsMetricDataValue.builder()
                                       .count(random.nextLong())
                                       .current(random.nextLong())
                                       .max(random.nextLong())
                                       .min(random.nextLong())
                                       .occurrences(random.nextInt())
                                       .standardDeviation(random.nextDouble())
                                       .sum(random.nextLong())
                                       .startTimeInMillis(random.nextLong())
                                       .useRange(random.nextBoolean())
                                       .value(random.nextDouble())
                                       .build()))
                               .build(),
            AppdynamicsMetricData.builder()
                .metricId(random.nextLong())
                .frequency(UUID.randomUUID().toString())
                .metricName(UUID.randomUUID().toString())
                .metricPath(UUID.randomUUID().toString())
                .metricValues(Lists.newArrayList(AppdynamicsMetricDataValue.builder()
                                                     .count(random.nextLong())
                                                     .current(random.nextLong())
                                                     .max(random.nextLong())
                                                     .min(random.nextLong())
                                                     .occurrences(random.nextInt())
                                                     .standardDeviation(random.nextDouble())
                                                     .sum(random.nextLong())
                                                     .startTimeInMillis(random.nextLong())
                                                     .useRange(random.nextBoolean())
                                                     .value(random.nextDouble())
                                                     .build(),
                    AppdynamicsMetricDataValue.builder()
                        .count(random.nextLong())
                        .current(random.nextLong())
                        .max(random.nextLong())
                        .min(random.nextLong())
                        .occurrences(random.nextInt())
                        .standardDeviation(random.nextDouble())
                        .sum(random.nextLong())
                        .startTimeInMillis(random.nextLong())
                        .useRange(random.nextBoolean())
                        .value(random.nextDouble())
                        .build()))
                .build());
    when(btDataCall.request()).thenReturn(new Request.Builder().url("http://harness-test.appd.com").build());
    when(btDataCall.execute()).thenReturn(Response.success(btData));
    when(appdynamicsRestClient.getMetricDataTimeRange(
             anyString(), anyLong(), anyString(), anyLong(), anyLong(), anyBoolean()))
        .thenReturn(btDataCall);

    AppDynamicsConfig appDynamicsConfig = AppDynamicsConfig.builder()
                                              .accountId(accountId)
                                              .controllerUrl(UUID.randomUUID().toString())
                                              .username(UUID.randomUUID().toString())
                                              .password(UUID.randomUUID().toString().toCharArray())
                                              .accountname(UUID.randomUUID().toString())
                                              .build();
    List<AppdynamicsMetricData> tierBTMetricData =
        delegateService.getTierBTMetricData(appDynamicsConfig, random.nextLong(), generateUuid(), generateUuid(),
            generateUuid(), System.currentTimeMillis() - random.nextInt(), System.currentTimeMillis(), emptyList(),
            createApiCallLog(accountId, null));
    assertThat(tierBTMetricData).hasSize(2);
    assertThat(tierBTMetricData).isEqualTo(btData);
  }

  private String saveAppdynamicsConfig() {
    final AppDynamicsConfig appDynamicsConfig = AppDynamicsConfig.builder()
                                                    .accountId(accountId)
                                                    .controllerUrl("https://www.google.com")
                                                    .username(UUID.randomUUID().toString())
                                                    .password(UUID.randomUUID().toString().toCharArray())
                                                    .accountname(UUID.randomUUID().toString())
                                                    .build();

    SettingAttribute settingAttribute = SettingAttribute.Builder.aSettingAttribute()
                                            .withAccountId(appDynamicsConfig.getAccountId())
                                            .withValue(appDynamicsConfig)
                                            .withAppId(UUID.randomUUID().toString())
                                            .withCategory(SettingCategory.CONNECTOR)
                                            .withEnvId(UUID.randomUUID().toString())
                                            .withName(UUID.randomUUID().toString())
                                            .build();

    return persistence.save(settingAttribute);
  }

  private void handleClone(Call restCall) {
    when(restCall.clone()).thenReturn(restCall);
    when(restCall.request()).thenReturn(new Request.Builder().url("https://google.com").build());
  }
}
