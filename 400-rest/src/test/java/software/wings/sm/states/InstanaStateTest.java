/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.sm.states;

import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.rule.OwnerRule.KAMAL;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;
import io.harness.serializer.JsonUtils;

import software.wings.beans.AccountType;
import software.wings.beans.Application;
import software.wings.delegatetasks.DelegateStateType;
import software.wings.service.impl.analysis.AnalysisContext;
import software.wings.service.impl.instana.InstanaApplicationParams;
import software.wings.service.impl.instana.InstanaDataCollectionInfo;
import software.wings.service.impl.instana.InstanaInfraParams;
import software.wings.service.impl.instana.InstanaTagFilter;
import software.wings.service.intfc.AccountService;
import software.wings.service.intfc.AppService;
import software.wings.service.intfc.MetricDataAnalysisService;
import software.wings.service.intfc.verification.CVActivityLogger;
import software.wings.verification.VerificationStateAnalysisExecutionData;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class InstanaStateTest extends APMStateVerificationTestBase {
  private InstanaState instanaState;
  private List<InstanaTagFilter> instanaTagFilters;
  private String analysisServerConfigId;
  @Mock private CVActivityLogger activityLogger;
  @Mock private MetricDataAnalysisService metricAnalysisService;

  @Before
  public void setup() throws IllegalAccessException {
    setupCommon();
    MockitoAnnotations.initMocks(this);
    setupCommonMocks();
    analysisServerConfigId = generateUuid();
    AppService appService = mock(AppService.class);
    when(appService.getAccountIdByAppId(anyString())).thenReturn(generateUuid());
    when(appService.get(anyString()))
        .thenReturn(Application.Builder.anApplication().name(generateUuid()).accountId(accountId).build());

    AccountService accountService = mock(AccountService.class);
    when(accountService.getAccountType(anyString())).thenReturn(Optional.of(AccountType.PAID));

    instanaState = new InstanaState("InstanaState");
    InstanaInfraParams infraParams = InstanaInfraParams.builder()
                                         .query("entity.kubernetes.pod.name:${host}")
                                         .metrics(Lists.newArrayList("cpu.total_usage", "memory.usage"))
                                         .build();
    instanaState.setInfraParams(infraParams);
    instanaTagFilters = Lists.newArrayList(InstanaTagFilter.builder()
                                               .name("kubernetes.cluster.name")
                                               .operator(InstanaTagFilter.Operator.EQUALS)
                                               .value("harness-test")
                                               .build());
    InstanaApplicationParams applicationParams =
        InstanaApplicationParams.builder().hostTagFilter("kubernetes.pod.name").tagFilters(instanaTagFilters).build();
    instanaState.setApplicationParams(applicationParams);
    instanaState.setTimeDuration("15");
    instanaState.setAnalysisServerConfigId(analysisServerConfigId);
    setupCommonFields(instanaState);
    FieldUtils.writeField(instanaState, "accountService", accountService, true);
    FieldUtils.writeField(instanaState, "metricAnalysisService", metricAnalysisService, true);
    when(cvActivityLogService.getLoggerByStateExecutionId(anyString(), anyString())).thenReturn(activityLogger);
  }

  @Test
  @Owner(developers = KAMAL)
  @Category(UnitTests.class)
  public void testCreateDataCollectionInfo() {
    Map<String, String> hosts = new HashMap<>();
    hosts.put("host1", "default");
    InstanaDataCollectionInfo instanaDataCollectionInfo =
        (InstanaDataCollectionInfo) instanaState.createDataCollectionInfo(executionContext, hosts);
    assertThat(DelegateStateType.INSTANA).isEqualTo(instanaDataCollectionInfo.getStateType());
    assertThat(instanaDataCollectionInfo.getInstanaConfig()).isNull();
    assertThat(instanaDataCollectionInfo.getStateExecutionId())
        .isEqualTo(executionContext.getStateExecutionInstanceId());
    assertThat(instanaDataCollectionInfo.getQuery()).isEqualTo(instanaState.getInfraParams().getQuery());
    assertThat(instanaDataCollectionInfo.getMetrics()).isEqualTo(instanaState.getInfraParams().getMetrics());
    assertThat(instanaDataCollectionInfo.getConnectorId()).isEqualTo(instanaState.getAnalysisServerConfigId());
    assertThat(instanaDataCollectionInfo.getHosts()).isEqualTo(Sets.newHashSet("host1"));
    assertThat(instanaDataCollectionInfo.getHostsToGroupNameMap()).isEqualTo(hosts);
    assertThat(instanaDataCollectionInfo.getTagFilters()).isEqualTo(instanaTagFilters);
    assertThat(instanaDataCollectionInfo.getHostTagFilter()).isEqualTo("kubernetes.pod.name");
  }

  @Test
  @Owner(developers = KAMAL)
  @Category(UnitTests.class)
  public void testCreateDataCollectionInfo_withNullHostTagFilter() {
    Map<String, String> hosts = new HashMap<>();
    hosts.put("host1", "default");
    instanaState.getApplicationParams().setTagFilters(null);
    InstanaDataCollectionInfo instanaDataCollectionInfo =
        (InstanaDataCollectionInfo) instanaState.createDataCollectionInfo(executionContext, hosts);
    assertThat(instanaDataCollectionInfo.getTagFilters()).isEqualTo(Collections.emptyList());
  }

  @Test
  @Owner(developers = KAMAL)
  @Category(UnitTests.class)
  public void testCreateDataCollectionInfo_withNullInfraParams() {
    Map<String, String> hosts = new HashMap<>();
    hosts.put("host1", "default");
    instanaState.setInfraParams(null);
    InstanaDataCollectionInfo instanaDataCollectionInfo =
        (InstanaDataCollectionInfo) instanaState.createDataCollectionInfo(executionContext, hosts);
    assertThat(instanaDataCollectionInfo.getQuery()).isEqualTo(null);
    assertThat(instanaDataCollectionInfo.getMetrics()).isEqualTo(Collections.emptyList());
  }

  @Test
  @Owner(developers = KAMAL)
  @Category(UnitTests.class)
  public void testCreateDataCollectionInfo_withNullApplicationParams() {
    Map<String, String> hosts = new HashMap<>();
    hosts.put("host1", "default");
    instanaState.setApplicationParams(null);
    InstanaDataCollectionInfo instanaDataCollectionInfo =
        (InstanaDataCollectionInfo) instanaState.createDataCollectionInfo(executionContext, hosts);
    assertThat(instanaDataCollectionInfo.getTagFilters()).isEqualTo(Collections.emptyList());
  }

  @Test
  @Owner(developers = KAMAL)
  @Category(UnitTests.class)
  public void testValidateConfig_whenApplicationMetricsAndInfraMetricsBothAreNotDefined() {
    instanaState.setInfraParams(null);
    instanaState.setApplicationParams(null);
    String expected = "{\n"
        + "  \"infraParams\": \"At least one metrics configuration should be defined\",\n"
        + "  \"applicationParams\": \"At least one metrics configuration should be defined\"\n"
        + "}";
    Map<String, String> errors = instanaState.validateFields();

    assertThat(errors).isEqualTo(JsonUtils.asObject(expected, Map.class));
  }

  @Test
  @Owner(developers = KAMAL)
  @Category(UnitTests.class)
  public void testValidateConfig_queryShouldContainHostPlaceholder() {
    instanaState.getInfraParams().setQuery("query.withouthostplaceholder");
    String expected = "{\n"
        + "  \"infraParams.query\": \"query should contain ${host}\"\n"
        + "}";
    Map<String, String> errors = instanaState.validateFields();

    assertThat(errors).isEqualTo(JsonUtils.asObject(expected, Map.class));
  }

  @Test
  @Owner(developers = KAMAL)
  @Category(UnitTests.class)
  public void testValidateConfig_whenInfraMetricsAreNotDefined() {
    instanaState.setInfraParams(null);
    Map<String, String> errors = instanaState.validateFields();
    assertThat(errors).isEmpty();
  }

  @Test
  @Owner(developers = KAMAL)
  @Category(UnitTests.class)
  public void testValidateConfig_whenApplicationMetricsAreNotDefined() {
    instanaState.setApplicationParams(null);
    Map<String, String> errors = instanaState.validateFields();
    assertThat(errors).isEmpty();
  }

  @Test
  @Owner(developers = KAMAL)
  @Category(UnitTests.class)
  public void testValidateConfig_whenInvalidApplicationParams() {
    instanaState.getApplicationParams().setHostTagFilter(null);
    InstanaTagFilter invalid = InstanaTagFilter.builder().build();
    instanaState.getApplicationParams().setTagFilters(Lists.newArrayList(invalid));
    String expected = "{\n"
        + "  \"applicationParams.tagFilter.name\": \"tagFilter.name is a required field.\",\n"
        + "  \"applicationParams.hostTagFilter\": \"hostTagFilter is a required field.\",\n"
        + "  \"applicationParams.tagFilter.value\": \"tagFilter.value is a required field.\",\n"
        + "  \"applicationParams.tagFilter.operator\": \"tagFilter.operator is a required field.\"\n"
        + "}";
    Map<String, String> errors = instanaState.validateFields();
    assertThat(errors).isEqualTo(JsonUtils.asObject(expected, Map.class));
  }

  @Test
  @Owner(developers = KAMAL)
  @Category(UnitTests.class)
  public void testValidateConfig_whenInvalidInfraParams() {
    instanaState.getInfraParams().setQuery(null);
    instanaState.getInfraParams().setMetrics(null);
    String expected = "{\n"
        + "  \"infraParams.query\": \"query is a required field.\",\n"
        + "  \"infraParams.metrics\": \"select at least one metric value.\"\n"
        + "}";
    Map<String, String> errors = instanaState.validateFields();
    assertThat(errors).isEqualTo(JsonUtils.asObject(expected, Map.class));
  }

  @Test
  @Owner(developers = KAMAL)
  @Category(UnitTests.class)
  public void testIsCVTaskEnqueuingEnabled() {
    assertThat(instanaState.isCVTaskEnqueuingEnabled(generateUuid())).isTrue();
  }

  @Test
  @Owner(developers = KAMAL)
  @Category(UnitTests.class)
  public void testValidateConfig_whenValuesAreValid() {
    assertThat(instanaState.validateFields()).isEmpty();
  }

  @Test
  @Owner(developers = KAMAL)
  @Category(UnitTests.class)
  public void testCreateDataCollectionInfo_resolveExpressionToGetServerConfigId() {
    instanaState.setAnalysisServerConfigId("${workflow.variables.instana_server}");
    InstanaState spyState = spy(instanaState);
    when(spyState.getResolvedConnectorId(
             any(), eq("analysisServerConfigId"), eq("${workflow.variables.instana_server}")))
        .thenReturn(analysisServerConfigId);

    Map<String, String> hosts = new HashMap<>();
    hosts.put("host1", "default");
    InstanaDataCollectionInfo instanaDataCollectionInfo =
        (InstanaDataCollectionInfo) spyState.createDataCollectionInfo(executionContext, hosts);
    assertThat(instanaDataCollectionInfo.getStateType()).isEqualTo(DelegateStateType.INSTANA);
    assertThat(instanaDataCollectionInfo.getInstanaConfig()).isNull();
    assertThat(instanaDataCollectionInfo.getConnectorId()).isEqualTo(analysisServerConfigId);
  }

  @Test
  @Owner(developers = KAMAL)
  @Category(UnitTests.class)
  public void testCreateDataCollectionInfo_resolveQueryExpression() {
    instanaState.getInfraParams().setQuery(
        "entity.kubernetes.pod.name:${host.hostName} AND entity.kubernetes.cluster.name:${workflow.variables.cluster}");
    when(executionContext.renderExpression(contains("${workflow.variables.cluster}")))
        .thenReturn("entity.kubernetes.pod.name:${host.hostName} AND entity.kubernetes.cluster.name:harness");
    Map<String, String> hosts = new HashMap<>();
    hosts.put("host1", "default");
    InstanaDataCollectionInfo instanaDataCollectionInfo =
        (InstanaDataCollectionInfo) instanaState.createDataCollectionInfo(executionContext, hosts);
    assertThat(instanaDataCollectionInfo.getStateType()).isEqualTo(DelegateStateType.INSTANA);
    assertThat(instanaDataCollectionInfo.getQuery())
        .isEqualTo("entity.kubernetes.pod.name:${host.hostName} AND entity.kubernetes.cluster.name:harness");
  }

  @Test
  @Owner(developers = KAMAL)
  @Category(UnitTests.class)
  public void testCreateDataCollectionInfo_resolveTagFilterValueExpression() {
    ArrayList<InstanaTagFilter> tagFilters = new ArrayList<>();
    tagFilters.add(InstanaTagFilter.builder()
                       .name("service.name")
                       .operator(InstanaTagFilter.Operator.EQUALS)
                       .value("${workflow.variables.service_name}")
                       .build());
    instanaState.getApplicationParams().setTagFilters(tagFilters);
    when(executionContext.renderExpression(eq("${workflow.variables.service_name}"))).thenReturn("todolist");
    Map<String, String> hosts = new HashMap<>();
    hosts.put("host1", "default");
    InstanaDataCollectionInfo instanaDataCollectionInfo =
        (InstanaDataCollectionInfo) instanaState.createDataCollectionInfo(executionContext, hosts);
    assertThat(instanaDataCollectionInfo.getStateType()).isEqualTo(DelegateStateType.INSTANA);
    assertThat(instanaDataCollectionInfo.getTagFilters())
        .isEqualTo(Arrays.asList(InstanaTagFilter.builder()
                                     .name("service.name")
                                     .operator(InstanaTagFilter.Operator.EQUALS)
                                     .value("todolist")
                                     .build()));
  }

  @Test
  @Owner(developers = KAMAL)
  @Category(UnitTests.class)
  public void testTriggerAnalysisDataCollection() {
    Map<String, String> hosts = new HashMap<>();
    hosts.put("host1", "default");
    assertThatThrownBy(
        ()
            -> instanaState.triggerAnalysisDataCollection(executionContext, AnalysisContext.builder().build(),
                mock(VerificationStateAnalysisExecutionData.class), hosts))
        .isInstanceOf(UnsupportedOperationException.class);
  }
}
