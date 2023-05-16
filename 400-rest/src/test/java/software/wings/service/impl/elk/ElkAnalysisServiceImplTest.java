/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl.elk;

import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.rule.OwnerRule.KAMAL;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.harness.category.element.UnitTests;
import io.harness.eraro.ErrorCode;
import io.harness.exception.VerificationOperationException;
import io.harness.exception.WingsException;
import io.harness.rule.Owner;
import io.harness.security.encryption.EncryptedDataDetail;
import io.harness.serializer.JsonUtils;

import software.wings.WingsBaseTest;
import software.wings.beans.ElkConfig;
import software.wings.beans.SettingAttribute;
import software.wings.common.VerificationConstants;
import software.wings.delegatetasks.DelegateProxyFactory;
import software.wings.service.impl.analysis.VerificationNodeDataSetupResponse;
import software.wings.service.impl.apm.MLServiceUtils;
import software.wings.service.intfc.SettingsService;
import software.wings.service.intfc.elk.ElkDelegateService;
import software.wings.service.intfc.security.SecretManager;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class ElkAnalysisServiceImplTest extends WingsBaseTest {
  @Mock private MLServiceUtils mlServiceUtils;
  @Mock SettingsService settingService;
  @Mock SecretManager secretManager;
  @Mock DelegateProxyFactory delegateProxyFactory;
  @InjectMocks ElkAnalysisServiceImpl service;

  @Before
  public void setup() {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = KAMAL)
  @Category(UnitTests.class)
  public void testGetLogDataByHostForTotalHitsAndThreshold() throws IOException {
    String accountId = generateUuid();
    String appId = generateUuid();
    String settingId = generateUuid();
    ElkSetupTestNodeData elkSetupTestNodeData = ElkSetupTestNodeData.builder()
                                                    .settingId(settingId)
                                                    .appId(appId)
                                                    .isServiceLevel(true)
                                                    .timeStampFieldFormat("yyyy-MM-dd'T'HH:mm:ss.SSSX")
                                                    .build();
    List<EncryptedDataDetail> encryptedDataDetails = mock(List.class);
    SettingAttribute settingAttribute = mock(SettingAttribute.class);
    ElkConfig elkConfig = mock(ElkConfig.class);
    when(settingService.get(eq(settingId))).thenReturn(settingAttribute);
    when(settingAttribute.getValue()).thenReturn(elkConfig);
    when(secretManager.getEncryptionDetails(any(), eq(appId), anyString())).thenReturn(encryptedDataDetails);
    ElkDelegateService elkDelegateService = mock(ElkDelegateService.class);
    when(delegateProxyFactory.getV2(eq(ElkDelegateService.class), any())).thenReturn(elkDelegateService);
    Object elkResponse = getELKResponse(15 * 201);
    when(elkDelegateService.search(any(), any(), any(), any(), anyInt())).thenReturn(elkResponse);

    VerificationNodeDataSetupResponse verificationNodeDataSetupResponse =
        service.getLogDataByHost(accountId, elkSetupTestNodeData);
    assertThat((long) verificationNodeDataSetupResponse.getLoadResponse().getTotalHits()).isEqualTo(201);
    assertThat((long) verificationNodeDataSetupResponse.getLoadResponse().getTotalHitsThreshold())
        .isEqualTo(VerificationConstants.TOTAL_HITS_PER_MIN_THRESHOLD);
  }

  @Test
  @Owner(developers = KAMAL)
  @Category(UnitTests.class)
  public void testGetLogDataByHostForTotalHitsAndThresholdVersion() throws IOException {
    String accountId = generateUuid();
    String appId = generateUuid();
    String settingId = generateUuid();
    ElkSetupTestNodeData elkSetupTestNodeData = ElkSetupTestNodeData.builder()
                                                    .settingId(settingId)
                                                    .appId(appId)
                                                    .isServiceLevel(true)
                                                    .timeStampFieldFormat("yyyy-MM-dd'T'HH:mm:ss.SSSX")
                                                    .build();
    List<EncryptedDataDetail> encryptedDataDetails = mock(List.class);
    SettingAttribute settingAttribute = mock(SettingAttribute.class);
    ElkConfig elkConfig = mock(ElkConfig.class);
    when(settingService.get(eq(settingId))).thenReturn(settingAttribute);
    when(settingAttribute.getValue()).thenReturn(elkConfig);
    when(secretManager.getEncryptionDetails(any(), eq(appId), anyString())).thenReturn(encryptedDataDetails);
    ElkDelegateService elkDelegateService = mock(ElkDelegateService.class);
    when(delegateProxyFactory.getV2(eq(ElkDelegateService.class), any())).thenReturn(elkDelegateService);
    Object elkResponse = getELKResponseVersion7(15 * 201);
    when(elkDelegateService.search(any(), any(), any(), any(), anyInt())).thenReturn(elkResponse);

    VerificationNodeDataSetupResponse verificationNodeDataSetupResponse =
        service.getLogDataByHost(accountId, elkSetupTestNodeData);
    assertThat((long) verificationNodeDataSetupResponse.getLoadResponse().getTotalHits()).isEqualTo(201);
    assertThat((long) verificationNodeDataSetupResponse.getLoadResponse().getTotalHitsThreshold())
        .isEqualTo(VerificationConstants.TOTAL_HITS_PER_MIN_THRESHOLD);
  }

  @Test
  @Owner(developers = KAMAL)
  @Category(UnitTests.class)
  public void testValidateQueryPassesIfTotalHitsIsLessThenTheThreshold() throws IOException {
    String accountId = generateUuid();
    String appId = generateUuid();
    String settingId = generateUuid();
    String query = generateUuid();
    List<EncryptedDataDetail> encryptedDataDetails = mock(List.class);
    SettingAttribute settingAttribute = mock(SettingAttribute.class);
    ElkConfig elkConfig = mock(ElkConfig.class);
    when(settingService.get(eq(settingId))).thenReturn(settingAttribute);
    when(settingAttribute.getValue()).thenReturn(elkConfig);
    when(secretManager.getEncryptionDetails(any(), eq(appId), anyString())).thenReturn(encryptedDataDetails);
    ElkDelegateService elkDelegateService = mock(ElkDelegateService.class);
    when(delegateProxyFactory.getV2(eq(ElkDelegateService.class), any())).thenReturn(elkDelegateService);
    Object responseWithoutHost = getELKResponse(15 * 20);
    when(elkDelegateService.search(any(), any(), any(), any(), anyInt())).thenReturn(responseWithoutHost);

    assertThat(service.validateQuery(accountId, appId, settingId, query, "test", null, "hostname", "log", "@timestamp"))
        .isEqualTo(true);
  }

  @Test
  @Owner(developers = KAMAL)
  @Category(UnitTests.class)
  public void testValidateQueryPassesIfTotalHitsIsLessThenTheThresholdVersion7() throws IOException {
    String accountId = generateUuid();
    String appId = generateUuid();
    String settingId = generateUuid();
    String query = generateUuid();
    List<EncryptedDataDetail> encryptedDataDetails = mock(List.class);
    SettingAttribute settingAttribute = mock(SettingAttribute.class);
    ElkConfig elkConfig = mock(ElkConfig.class);
    when(settingService.get(eq(settingId))).thenReturn(settingAttribute);
    when(settingAttribute.getValue()).thenReturn(elkConfig);
    when(secretManager.getEncryptionDetails(any(), eq(appId), anyString())).thenReturn(encryptedDataDetails);
    ElkDelegateService elkDelegateService = mock(ElkDelegateService.class);
    when(delegateProxyFactory.getV2(eq(ElkDelegateService.class), any())).thenReturn(elkDelegateService);
    Object responseWithoutHost = getELKResponseVersion7(15 * 20);
    when(elkDelegateService.search(any(), any(), any(), any(), anyInt())).thenReturn(responseWithoutHost);

    assertThat(service.validateQuery(accountId, appId, settingId, query, "test", null, "hostname", "log", "@timestamp"))
        .isEqualTo(true);
  }

  @Test
  @Owner(developers = KAMAL)
  @Category(UnitTests.class)
  public void testValidateQueryThrowsWingsExceptionIfTotalHitsIsLessThenTheThreshold() throws IOException {
    String accountId = generateUuid();
    String appId = generateUuid();
    String settingId = generateUuid();
    String query = generateUuid();
    List<EncryptedDataDetail> encryptedDataDetails = mock(List.class);
    SettingAttribute settingAttribute = mock(SettingAttribute.class);
    ElkConfig elkConfig = mock(ElkConfig.class);
    when(settingService.get(eq(settingId))).thenReturn(settingAttribute);
    when(settingAttribute.getValue()).thenReturn(elkConfig);
    when(secretManager.getEncryptionDetails(any(), eq(appId), anyString())).thenReturn(encryptedDataDetails);
    ElkDelegateService elkDelegateService = mock(ElkDelegateService.class);
    when(delegateProxyFactory.getV2(eq(ElkDelegateService.class), any())).thenReturn(elkDelegateService);
    Object responseWithoutHost = getELKResponse(1000 * 15 + 1); // per minute 1000
    when(elkDelegateService.search(any(), any(), any(), any(), anyInt())).thenReturn(responseWithoutHost);

    try {
      service.validateQuery(accountId, appId, settingId, query, "test", null, "hostname", "log", "@timestamp");
      fail("validate query should throw wings exception..");
    } catch (VerificationOperationException e) {
      assertThat("Error in Elasticsearch configuration. Too many logs returned using query: '" + query
          + "'. Please refine your query.")
          .isEqualTo(e.getParams().get("reason"));
      assertThat(e.getCode()).isEqualTo(ErrorCode.ELK_CONFIGURATION_ERROR);
    }
  }

  @Test
  @Owner(developers = KAMAL)
  @Category(UnitTests.class)
  public void testValidateQueryThrowsWingsExceptionIfTotalHitsIsLessThenTheThresholdVersion7() throws IOException {
    String accountId = generateUuid();
    String appId = generateUuid();
    String settingId = generateUuid();
    String query = generateUuid();
    List<EncryptedDataDetail> encryptedDataDetails = mock(List.class);
    SettingAttribute settingAttribute = mock(SettingAttribute.class);
    ElkConfig elkConfig = mock(ElkConfig.class);
    when(settingService.get(eq(settingId))).thenReturn(settingAttribute);
    when(settingAttribute.getValue()).thenReturn(elkConfig);
    when(secretManager.getEncryptionDetails(any(), eq(appId), anyString())).thenReturn(encryptedDataDetails);
    ElkDelegateService elkDelegateService = mock(ElkDelegateService.class);
    when(delegateProxyFactory.getV2(eq(ElkDelegateService.class), any())).thenReturn(elkDelegateService);
    Object responseWithoutHost = getELKResponseVersion7(1000 * 15 + 1); // per minute 1000
    when(elkDelegateService.search(any(), any(), any(), any(), anyInt())).thenReturn(responseWithoutHost);

    try {
      service.validateQuery(accountId, appId, settingId, query, "test", null, "hostname", "log", "@timestamp");
      fail("validate query should throw wings exception..");
    } catch (WingsException e) {
      assertThat("Error in Elasticsearch configuration. Too many logs returned using query: '" + query
          + "'. Please refine your query.")
          .isEqualTo(e.getParams().get("reason"));
      assertThat(e.getCode()).isEqualTo(ErrorCode.ELK_CONFIGURATION_ERROR);
    }
  }

  @Test
  @Owner(developers = KAMAL)
  @Category(UnitTests.class)
  public void testValidateQueryThrowsWingsExceptionIfSearchThrowsRuntimeException() throws IOException {
    String accountId = generateUuid();
    String appId = generateUuid();
    String settingId = generateUuid();
    String query = generateUuid();
    List<EncryptedDataDetail> encryptedDataDetails = mock(List.class);
    SettingAttribute settingAttribute = mock(SettingAttribute.class);
    ElkConfig elkConfig = mock(ElkConfig.class);
    when(settingService.get(eq(settingId))).thenReturn(settingAttribute);
    when(settingAttribute.getValue()).thenReturn(elkConfig);
    when(secretManager.getEncryptionDetails(any(), eq(appId), anyString())).thenReturn(encryptedDataDetails);
    ElkDelegateService elkDelegateService = mock(ElkDelegateService.class);
    when(delegateProxyFactory.getV2(eq(ElkDelegateService.class), any())).thenReturn(elkDelegateService);
    when(elkDelegateService.search(any(), any(), any(), any(), anyInt()))
        .thenThrow(new RuntimeException("Search failed from test...."));

    try {
      service.validateQuery(accountId, appId, settingId, query, "test", null, "hostname", "log", "@timestamp");
      fail("validate query should throw wings exception..");
    } catch (WingsException e) {
      assertThat(e.getCode()).isEqualTo(ErrorCode.ELK_CONFIGURATION_ERROR);
    }
  }

  private Object getELKResponse(int total) {
    String resp = "{\n"
        + "  \"took\": 20,\n"
        + "  \"timed_out\": false,\n"
        + "  \"_shards\": {\n"
        + "    \"total\": 275,\n"
        + "    \"successful\": 275,\n"
        + "    \"skipped\": 270,\n"
        + "    \"failed\": 0\n"
        + "  },\n"
        + "  \"hits\": {\n"
        + "    \"total\": " + total + ",\n"
        + "    \"max_score\": 0,\n"
        + "    \"hits\": [\n"
        + "    ]\n"
        + "  }\n"
        + "}";
    return JsonUtils.asObject(resp, Map.class);
  }

  private Object getELKResponseVersion7(int total) {
    String resp = "{\n"
        + "  \"took\": 20,\n"
        + "  \"timed_out\": false,\n"
        + "  \"_shards\": {\n"
        + "    \"total\": 275,\n"
        + "    \"successful\": 275,\n"
        + "    \"skipped\": 270,\n"
        + "    \"failed\": 0\n"
        + "  },\n"
        + "  \"hits\": {\n"
        + "    \"total\": {\n"
        + "    \"value\": " + total + ",\n"
        + "    \"relation\": \"eq\"\n"
        + "  },\n"
        + "    \"max_score\": 0,\n"
        + "    \"hits\": [\n"
        + "    ]\n"
        + "  }\n"
        + "}";
    return JsonUtils.asObject(resp, Map.class);
  }
}
