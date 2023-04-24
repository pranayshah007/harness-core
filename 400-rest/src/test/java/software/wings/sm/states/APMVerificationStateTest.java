/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.sm.states;

import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.rule.OwnerRule.PRAVEEN;
import static io.harness.rule.OwnerRule.RAGHU;

import static software.wings.beans.Application.Builder.anApplication;
import static software.wings.beans.Environment.Builder.anEnvironment;
import static software.wings.common.VerificationConstants.URL_BODY_APPENDER;
import static software.wings.common.VerificationConstants.VERIFICATION_HOST_PLACEHOLDER;
import static software.wings.sm.StateExecutionInstance.Builder.aStateExecutionInstance;
import static software.wings.utils.WingsTestConstants.APP_ID;
import static software.wings.utils.WingsTestConstants.APP_NAME;
import static software.wings.utils.WingsTestConstants.ENV_ID;
import static software.wings.utils.WingsTestConstants.ENV_NAME;
import static software.wings.utils.WingsTestConstants.STATE_EXECUTION_ID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.harness.beans.EnvironmentType;
import io.harness.category.element.UnitTests;
import io.harness.context.ContextElementType;
import io.harness.ff.FeatureFlagService;
import io.harness.rule.Owner;
import io.harness.serializer.YamlUtils;

import software.wings.api.ContextElementParamMapperFactory;
import software.wings.api.HostElement;
import software.wings.beans.ApmMetricCollectionInfo;
import software.wings.beans.ApmResponseMapping;
import software.wings.beans.apm.Method;
import software.wings.helpers.ext.url.SubdomainUrlHelperIntfc;
import software.wings.service.impl.apm.APMMetricInfo;
import software.wings.service.intfc.MetricDataAnalysisService;
import software.wings.service.intfc.SettingsService;
import software.wings.sm.ExecutionContextImpl;
import software.wings.sm.StateExecutionInstance;
import software.wings.sm.WorkflowStandardParams;
import software.wings.sm.WorkflowStandardParamsExtensionService;

import com.fasterxml.jackson.core.type.TypeReference;
import com.google.common.base.Charsets;
import com.google.common.io.Resources;
import com.google.inject.Inject;
import com.google.inject.Injector;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class APMVerificationStateTest extends APMStateVerificationTestBase {
  @Inject private Injector injector;
  @Mock private WorkflowStandardParams workflowStandardParameters;
  @Mock private WorkflowStandardParamsExtensionService workflowStandardParamsExtensionService;

  private ExecutionContextImpl context;
  private String accountId;
  @Mock private FeatureFlagService featureFlagService;
  private APMVerificationState apmVerificationState;
  private YamlUtils yamlUtils;
  @Mock private MetricDataAnalysisService metricAnalysisService;
  @Mock protected SettingsService settingsService;
  /**
   * Sets context.
   */
  @Before
  public void setupContext() throws Exception {
    MockitoAnnotations.initMocks(this);
    accountId = generateUuid();
    yamlUtils = new YamlUtils();

    apmVerificationState = new APMVerificationState("dummy");
    StateExecutionInstance stateExecutionInstance =
        aStateExecutionInstance().displayName("healthCheck1").uuid(STATE_EXECUTION_ID).build();
    when(workflowStandardParamsExtensionService.getApp(any()))
        .thenReturn(anApplication().uuid(APP_ID).name(APP_NAME).build());
    when(workflowStandardParamsExtensionService.getEnv(any()))
        .thenReturn(anEnvironment().uuid(ENV_ID).name(ENV_NAME).environmentType(EnvironmentType.NON_PROD).build());
    when(workflowStandardParameters.getAppId()).thenReturn(APP_ID);
    when(workflowStandardParameters.getEnvId()).thenReturn(ENV_ID);

    ContextElementParamMapperFactory contextElementParamMapperFactory = new ContextElementParamMapperFactory(
        injector.getInstance(SubdomainUrlHelperIntfc.class), workflowExecutionService, null, null, null,
        featureFlagService, null, workflowStandardParamsExtensionService);

    when(workflowStandardParameters.getElementType()).thenReturn(ContextElementType.STANDARD);
    context = new ExecutionContextImpl(stateExecutionInstance, null, injector);
    FieldUtils.writeField(
        context, "workflowStandardParamsExtensionService", workflowStandardParamsExtensionService, true);
    FieldUtils.writeField(context, "contextElementParamMapperFactory", contextElementParamMapperFactory, true);
    context.pushContextElement(workflowStandardParameters);
    context.pushContextElement(HostElement.builder().hostName("localhost").build());
    FieldUtils.writeField(apmVerificationState, "featureFlagService", featureFlagService, true);
    FieldUtils.writeField(apmVerificationState, "metricAnalysisService", metricAnalysisService, true);
    FieldUtils.writeField(apmVerificationState, "settingsService", settingsService, true);
    FieldUtils.writeField(
        apmVerificationState, "workflowStandardParamsExtensionService", workflowStandardParamsExtensionService, true);
    setupCvActivityLogService(apmVerificationState);
  }

  @Test
  @Owner(developers = PRAVEEN)
  @Category(UnitTests.class)
  public void metricCollectionInfos() throws IOException {
    YamlUtils yamlUtils = new YamlUtils();
    String yamlStr =
        Resources.toString(APMVerificationStateTest.class.getResource("/apm/apm_config.yml"), Charsets.UTF_8);
    List<ApmMetricCollectionInfo> mcInfo =
        yamlUtils.read(yamlStr, new TypeReference<List<ApmMetricCollectionInfo>>() {});
    apmVerificationState.setMetricCollectionInfos(mcInfo);

    Map<String, List<APMMetricInfo>> apmMetricInfos =
        APMVerificationState.buildMetricInfoMap(apmVerificationState.getMetricCollectionInfos(), Optional.of(context));
    assertThat(3).isEqualTo(apmMetricInfos.size());
    assertThat(2).isEqualTo(apmMetricInfos.get("query").size());
    assertThat(apmMetricInfos.get("query").get(0).getResponseMappers().get("txnName").getFieldValue()).isNotNull();
    assertThat(apmMetricInfos.get("query").get(1).getResponseMappers().get("txnName").getJsonPath()).isNotNull();

    String body = "this is a dummy collection body";
    assertThat(apmMetricInfos.get("queryWithHost" + URL_BODY_APPENDER + body)).hasSize(1);
    assertThat(apmMetricInfos.get("queryWithHost" + URL_BODY_APPENDER + body).get(0).getBody()).isEqualTo(body);
    assertThat(apmMetricInfos.get("queryWithHost" + URL_BODY_APPENDER + body).get(0).getMethod())
        .isEqualTo(Method.POST);

    assertThat(apmMetricInfos.get("queryWithHost")).hasSize(1);
    APMMetricInfo metricWithHost = apmMetricInfos.get("queryWithHost").get(0);
    assertThat(metricWithHost.getResponseMappers().get("host").getJsonPath()).isNotNull();
    assertThat(metricWithHost.getResponseMappers().get("host").getRegexs()).isNotNull();
  }

  @Test
  @Owner(developers = PRAVEEN)
  @Category(UnitTests.class)
  public void testBuildMetricInfoMap_withExpressions() throws IOException, IllegalAccessException {
    YamlUtils yamlUtils = new YamlUtils();
    String yamlStr =
        Resources.toString(APMVerificationStateTest.class.getResource("/apm/apm_config.yml"), Charsets.UTF_8);
    List<ApmMetricCollectionInfo> mcInfo =
        yamlUtils.read(yamlStr, new TypeReference<List<ApmMetricCollectionInfo>>() {});
    mcInfo.get(2).getResponseMapping().setTxnNameJsonPath("${workflow.variable.jsonPath}");
    apmVerificationState.setMetricCollectionInfos(mcInfo);
    ExecutionContextImpl executionContext = mock(ExecutionContextImpl.class);
    FieldUtils.writeField(
        executionContext, "workflowStandardParamsExtensionService", workflowStandardParamsExtensionService, true);
    when(executionContext.renderExpression(anyString()))
        .thenAnswer(invocation -> invocation.getArgument(0, String.class));
    when(executionContext.renderExpression("${workflow.variable.jsonPath}")).thenReturn("$.rendered.jsonPath");
    Map<String, List<APMMetricInfo>> apmMetricInfos = APMVerificationState.buildMetricInfoMap(
        apmVerificationState.getMetricCollectionInfos(), Optional.of(executionContext));
    assertThat(3).isEqualTo(apmMetricInfos.size());
    assertThat(2).isEqualTo(apmMetricInfos.get("query").size());
    assertThat(apmMetricInfos.get("query").get(0).getResponseMappers().get("txnName").getFieldValue()).isNotNull();
    assertThat(apmMetricInfos.get("query").get(1).getResponseMappers().get("txnName").getJsonPath()).isNotNull();

    String body = "this is a dummy collection body";
    assertThat(apmMetricInfos.get("queryWithHost" + URL_BODY_APPENDER + body)).hasSize(1);
    assertThat(apmMetricInfos.get("queryWithHost" + URL_BODY_APPENDER + body).get(0).getBody()).isEqualTo(body);
    assertThat(apmMetricInfos.get("queryWithHost" + URL_BODY_APPENDER + body).get(0).getMethod())
        .isEqualTo(Method.POST);

    assertThat(apmMetricInfos.get("queryWithHost")).hasSize(1);
    APMMetricInfo metricWithHost = apmMetricInfos.get("queryWithHost").get(0);
    assertThat(metricWithHost.getResponseMappers().get("host").getJsonPath()).isNotNull();

    // Validate if the rendered expression is set as the json path.
    assertThat(metricWithHost.getResponseMappers().get("txnName").getJsonPath()).isEqualTo("$.rendered.jsonPath");
  }

  @Test
  @Owner(developers = PRAVEEN)
  @Category(UnitTests.class)
  public void testValidateFields() {
    apmVerificationState.setMetricCollectionInfos(null);
    Map<String, String> invalidFields = apmVerificationState.validateFields();
    assertThat(invalidFields.size() == 1).isTrue();
    assertThat(invalidFields.keySet().iterator().next()).isEqualTo("Metric Collection Info");
  }

  @Test
  @Owner(developers = PRAVEEN)
  @Category(UnitTests.class)
  public void testValidateFieldsResponseMapping() {
    ApmMetricCollectionInfo info = ApmMetricCollectionInfo.builder()
                                       .collectionUrl("This is a sample URL ${host} ${start_time} ${end_time}")
                                       .metricName("name")
                                       .build();
    apmVerificationState.setMetricCollectionInfos(Arrays.asList(info));
    Map<String, String> invalidFields = apmVerificationState.validateFields();
    assertThat(invalidFields.size() == 1).isTrue();
    assertThat(invalidFields.keySet().iterator().next()).isEqualTo("responseMapping");
  }

  @Test
  @Owner(developers = PRAVEEN)
  @Category(UnitTests.class)
  public void testValidateFields_noHost() {
    ApmMetricCollectionInfo info = ApmMetricCollectionInfo.builder()
                                       .collectionUrl("This is a sample URL ${start_time} ${end_time}")
                                       .metricName("name")
                                       .build();
    ApmResponseMapping mapping = ApmResponseMapping.builder()
                                     .metricValueJsonPath("metricValue")
                                     .timestampJsonPath("timestamp")
                                     .txnNameFieldValue("txnName")
                                     .build();
    info.setResponseMapping(mapping);
    apmVerificationState.setMetricCollectionInfos(Arrays.asList(info));
    Map<String, String> invalidFields = apmVerificationState.validateFields();

    assertThat(invalidFields.size() == 1).isTrue();
    assertThat(invalidFields.keySet().iterator().next()).isEqualTo("collectionUrl");
  }

  @Test
  @Owner(developers = PRAVEEN)
  @Category(UnitTests.class)
  public void testValidateFields_noTimePlaceholders() {
    ApmMetricCollectionInfo info = ApmMetricCollectionInfo.builder()
                                       .collectionUrl("This is a sample URL ${host}  ${end_time}")
                                       .metricName("name")
                                       .build();
    ApmResponseMapping mapping = ApmResponseMapping.builder()
                                     .metricValueJsonPath("metricValue")
                                     .timestampJsonPath("timestamp")
                                     .txnNameFieldValue("txnName")
                                     .build();
    info.setResponseMapping(mapping);
    apmVerificationState.setMetricCollectionInfos(Arrays.asList(info));
    Map<String, String> invalidFields = apmVerificationState.validateFields();

    assertThat(invalidFields.size() == 1).isTrue();
    assertThat(invalidFields.keySet().iterator().next()).isEqualTo("collectionUrl");
  }

  @Test
  @Owner(developers = PRAVEEN)
  @Category(UnitTests.class)
  public void testValidateFieldsResponseMappingMetricValue() {
    ApmMetricCollectionInfo info = ApmMetricCollectionInfo.builder()
                                       .collectionUrl("This is a sample URL ${host} ${start_time} ${end_time}")
                                       .metricName("name")
                                       .build();
    ApmResponseMapping mapping =
        ApmResponseMapping.builder().metricValueJsonPath("metricValue").timestampJsonPath("timestamp").build();
    info.setResponseMapping(mapping);
    apmVerificationState.setMetricCollectionInfos(Arrays.asList(info));
    Map<String, String> invalidFields = apmVerificationState.validateFields();
    assertThat(invalidFields.size() == 1).isTrue();
    assertThat(invalidFields.keySet().iterator().next()).isEqualTo("transactionName");
  }

  @Test
  @Owner(developers = PRAVEEN)
  @Category(UnitTests.class)
  public void testValidateFieldsResponseMappingHostName() {
    ApmMetricCollectionInfo info = ApmMetricCollectionInfo.builder()
                                       .collectionUrl("This is a sample URL ${host} ${start_time} ${end_time}")
                                       .metricName("name")
                                       .build();
    ApmResponseMapping mapping = ApmResponseMapping.builder()
                                     .metricValueJsonPath("metricValue")
                                     .timestampJsonPath("timestamp")
                                     .txnNameFieldValue("txnName")
                                     .build();
    info.setResponseMapping(mapping);
    apmVerificationState.setMetricCollectionInfos(Arrays.asList(info));
    Map<String, String> invalidFields = apmVerificationState.validateFields();
    assertThat(invalidFields.size() == 0).isTrue();
  }

  @Test
  @Owner(developers = RAGHU)
  @Category(UnitTests.class)
  public void testHostAndBaseline() {
    String metricName = generateUuid();

    ApmMetricCollectionInfo info =
        ApmMetricCollectionInfo.builder()
            .collectionUrl("${host} ${start_time} ${end_time} This is a sample URL " + VERIFICATION_HOST_PLACEHOLDER)
            .baselineCollectionUrl("some baseline url")
            .metricName(metricName)
            .build();
    ApmResponseMapping mapping = ApmResponseMapping.builder()
                                     .metricValueJsonPath("metricValue")
                                     .timestampJsonPath("timestamp")
                                     .txnNameFieldValue("txnName")
                                     .build();
    info.setResponseMapping(mapping);
    apmVerificationState.setMetricCollectionInfos(Arrays.asList(info));
    Map<String, String> invalidFields = apmVerificationState.validateFields();
    assertThat(invalidFields).hasSize(1);
    assertThat("for " + metricName + " the collection url has " + VERIFICATION_HOST_PLACEHOLDER
        + " and baseline collection url as well")
        .isEqualTo(invalidFields.get("collectionUrl"));
  }

  @Test
  @Owner(developers = RAGHU)
  @Category(UnitTests.class)
  public void testBaselineUrlHasHost() {
    String metricName = generateUuid();

    ApmMetricCollectionInfo info = ApmMetricCollectionInfo.builder()
                                       .collectionUrl("This is a sample URL")
                                       .baselineCollectionUrl("some baseline url " + VERIFICATION_HOST_PLACEHOLDER)
                                       .metricName(metricName)
                                       .build();
    ApmResponseMapping mapping = ApmResponseMapping.builder()
                                     .metricValueJsonPath("metricValue")
                                     .timestampJsonPath("timestamp")
                                     .txnNameFieldValue("txnName")
                                     .build();
    info.setResponseMapping(mapping);
    apmVerificationState.setMetricCollectionInfos(Arrays.asList(info));
    Map<String, String> invalidFields = apmVerificationState.validateFields();
    assertThat(invalidFields).hasSize(1);
    assertThat("Baseline url can only be set for canary verification strategy. For " + metricName
        + " there is baseline url set " + VERIFICATION_HOST_PLACEHOLDER)
        .isEqualTo(invalidFields.get("collectionUrl"));
  }

  @Test
  @Owner(developers = RAGHU)
  @Category(UnitTests.class)
  public void testInvalidMultipleBaselineUrl() {
    String metricName1 = generateUuid();
    String metricName2 = generateUuid();

    ApmMetricCollectionInfo info1 = ApmMetricCollectionInfo.builder()
                                        .collectionUrl("This is a sample URL")
                                        .baselineCollectionUrl("some baseline url")
                                        .metricName(metricName1)
                                        .build();
    ApmMetricCollectionInfo info2 = ApmMetricCollectionInfo.builder()
                                        .collectionUrl("This is a sample URL " + VERIFICATION_HOST_PLACEHOLDER)
                                        .metricName(metricName2)
                                        .build();
    ApmResponseMapping mapping = ApmResponseMapping.builder()
                                     .metricValueJsonPath("metricValue")
                                     .timestampJsonPath("timestamp")
                                     .txnNameFieldValue("txnName")
                                     .build();
    info1.setResponseMapping(mapping);
    info2.setResponseMapping(mapping);
    apmVerificationState.setMetricCollectionInfos(Arrays.asList(info1, info2));
    Map<String, String> invalidFields = apmVerificationState.validateFields();
    assertThat(1).isEqualTo(invalidFields.size());
    assertThat("for " + metricName2 + " the url has " + VERIFICATION_HOST_PLACEHOLDER
        + ". When configuring multi url verification all metrics should follow the same pattern.")
        .isEqualTo(invalidFields.get("collectionUrl"));
  }

  @Test
  @Owner(developers = PRAVEEN)
  @Category(UnitTests.class)
  public void testValidInitialDelay() throws Exception {
    String yamlStr =
        Resources.toString(APMVerificationStateTest.class.getResource("/apm/apm_config.yml"), Charsets.UTF_8);
    List<ApmMetricCollectionInfo> mcInfo =
        yamlUtils.read(yamlStr, new TypeReference<List<ApmMetricCollectionInfo>>() {});
    apmVerificationState.setMetricCollectionInfos(mcInfo);
    apmVerificationState.setInitialAnalysisDelay("4m");
    assertThat(apmVerificationState.validateFields().containsKey("initialAnalysisDelay")).isFalse();
  }

  @Test
  @Owner(developers = PRAVEEN)
  @Category(UnitTests.class)
  public void testValidInitialDelay_Seconds() throws Exception {
    YamlUtils yamlUtils = new YamlUtils();
    String yamlStr =
        Resources.toString(APMVerificationStateTest.class.getResource("/apm/apm_config.yml"), Charsets.UTF_8);
    List<ApmMetricCollectionInfo> mcInfo =
        yamlUtils.read(yamlStr, new TypeReference<List<ApmMetricCollectionInfo>>() {});
    apmVerificationState.setMetricCollectionInfos(mcInfo);
    apmVerificationState.setInitialAnalysisDelay("200s");
    assertThat(apmVerificationState.validateFields().containsKey("initialAnalysisDelay")).isFalse();
  }

  @Test
  @Owner(developers = PRAVEEN)
  @Category(UnitTests.class)
  public void testInValidInitialDelay_Seconds() throws Exception {
    YamlUtils yamlUtils = new YamlUtils();
    String yamlStr =
        Resources.toString(APMVerificationStateTest.class.getResource("/apm/apm_config.yml"), Charsets.UTF_8);
    List<ApmMetricCollectionInfo> mcInfo =
        yamlUtils.read(yamlStr, new TypeReference<List<ApmMetricCollectionInfo>>() {});
    apmVerificationState.setMetricCollectionInfos(mcInfo);
    apmVerificationState.setInitialAnalysisDelay("500s");
    // Now value is hard coded to DELAY_MINUTES
    // https://harness.atlassian.net/browse/CV-3902
    assertThat(apmVerificationState.validateFields().containsKey("initialAnalysisDelay")).isFalse();
  }

  @Test
  @Owner(developers = PRAVEEN)
  @Category(UnitTests.class)
  public void testIfIsHistoricalAnalysis_NotTrue() throws Exception {
    YamlUtils yamlUtils = new YamlUtils();
    String yamlStr = Resources.toString(
        APMVerificationStateTest.class.getResource("/apm/apm_collection_info_not_historical.yml"), Charsets.UTF_8);
    List<ApmMetricCollectionInfo> mcInfo =
        yamlUtils.read(yamlStr, new TypeReference<List<ApmMetricCollectionInfo>>() {});
    apmVerificationState.setMetricCollectionInfos(mcInfo);

    assertThat(apmVerificationState.isHistoricalAnalysis(accountId)).isFalse();
  }

  @Test
  @Owner(developers = PRAVEEN)
  @Category(UnitTests.class)
  public void testIfIsHistoricalAnalysis_NotTrueWithUrl() throws Exception {
    YamlUtils yamlUtils = new YamlUtils();
    String yamlStr = Resources.toString(
        APMVerificationStateTest.class.getResource("/apm/apm_collection_info_not_historical.yml"), Charsets.UTF_8);
    List<ApmMetricCollectionInfo> mcInfo =
        yamlUtils.read(yamlStr, new TypeReference<List<ApmMetricCollectionInfo>>() {});
    mcInfo.forEach(info -> info.setCollectionUrl(info.getCollectionUrl() + VERIFICATION_HOST_PLACEHOLDER));
    apmVerificationState.setMetricCollectionInfos(mcInfo);

    assertThat(apmVerificationState.isHistoricalAnalysis(accountId)).isFalse();
  }

  @Test
  @Owner(developers = PRAVEEN)
  @Category(UnitTests.class)
  public void testIfIsHistoricalAnalysis_NotTrueWithNullUrl() throws Exception {
    YamlUtils yamlUtils = new YamlUtils();
    String yamlStr = Resources.toString(
        APMVerificationStateTest.class.getResource("/apm/apm_collection_info_not_historical.yml"), Charsets.UTF_8);
    List<ApmMetricCollectionInfo> mcInfo =
        yamlUtils.read(yamlStr, new TypeReference<List<ApmMetricCollectionInfo>>() {});
    mcInfo.forEach(info -> info.setCollectionUrl(null));
    apmVerificationState.setMetricCollectionInfos(mcInfo);

    assertThat(apmVerificationState.isHistoricalAnalysis(accountId)).isFalse();
  }

  @Test
  @Owner(developers = PRAVEEN)
  @Category(UnitTests.class)
  public void testIfIsHistoricalAnalysis_FFDisabled() throws Exception {
    FieldUtils.writeField(apmVerificationState, "featureFlagService", featureFlagService, true);
    YamlUtils yamlUtils = new YamlUtils();
    String yamlStr = Resources.toString(
        APMVerificationStateTest.class.getResource("/apm/apm_collection_info_not_historical.yml"), Charsets.UTF_8);
    List<ApmMetricCollectionInfo> mcInfo =
        yamlUtils.read(yamlStr, new TypeReference<List<ApmMetricCollectionInfo>>() {});
    mcInfo.forEach(info -> info.setCollectionUrl(null));
    apmVerificationState.setMetricCollectionInfos(mcInfo);

    assertThat(apmVerificationState.isHistoricalAnalysis(accountId)).isFalse();
  }
}
