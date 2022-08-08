/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl.apm;

import static io.harness.rule.OwnerRule.KAMAL;
import static io.harness.rule.OwnerRule.PRAVEEN;
import static io.harness.rule.OwnerRule.RAGHU;
import static io.harness.rule.OwnerRule.SRIRAM;

import static software.wings.api.DeploymentType.KUBERNETES;
import static software.wings.service.impl.newrelic.NewRelicMetricDataRecord.DEFAULT_GROUP_NAME;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;
import io.harness.serializer.JsonUtils;
import io.harness.serializer.YamlUtils;

import software.wings.WingsBaseTest;
import software.wings.beans.ApmMetricCollectionInfo;
import software.wings.metrics.MetricType;
import software.wings.service.impl.newrelic.NewRelicMetricDataRecord;
import software.wings.sm.states.DatadogState;

import com.fasterxml.jackson.core.type.TypeReference;
import com.google.common.base.Charsets;
import com.google.common.collect.Lists;
import com.google.common.io.Resources;
import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class APMParserTest extends WingsBaseTest {
  @Test
  @Owner(developers = RAGHU)
  @Category(UnitTests.class)
  public void testJsonParser() throws IOException {
    String textLoad =
        Resources.toString(APMParserTest.class.getResource("/apm/datadog_sample_response_load.json"), Charsets.UTF_8);
    String textMem =
        Resources.toString(APMParserTest.class.getResource("/apm/datadog_sample_response_mem.json"), Charsets.UTF_8);

    Map<String, List<APMMetricInfo>> metricEndpointsInfo = DatadogState.metricEndpointsInfo(Optional.empty(),
        Optional.of(Lists.newArrayList("system.load.1", "system.mem.used")), Optional.empty(), Optional.empty(),
        Optional.of(KUBERNETES));

    Iterator<List<APMMetricInfo>> metricInfoIterator = metricEndpointsInfo.values().iterator();
    Collection<NewRelicMetricDataRecord> records =
        APMResponseParser.extract(Lists.newArrayList(APMResponseParser.APMResponseData.builder()
                                                         .text(textLoad)
                                                         .groupName(DEFAULT_GROUP_NAME)
                                                         .metricInfos(metricInfoIterator.next())
                                                         .build(),
            APMResponseParser.APMResponseData.builder()
                .text(textMem)
                .groupName(DEFAULT_GROUP_NAME)
                .metricInfos(metricInfoIterator.next())
                .build()));

    assertThat(records).hasSize(80);
    records.forEach(record -> record.setValidUntil(null));
    String output = Resources.toString(
        APMParserTest.class.getResource("/apm/datadog_sample_collected_response.json"), Charsets.UTF_8);

    assertThat(JsonUtils.asPrettyJson(records)).isEqualTo(output);
  }

  @Test
  @Owner(developers = RAGHU)
  @Category(UnitTests.class)
  public void testJsonParserStringValue() throws IOException {
    String textLoad =
        Resources.toString(APMParserTest.class.getResource("/apm/sample_response_string_value.json"), Charsets.UTF_8);
    String textMem =
        Resources.toString(APMParserTest.class.getResource("/apm/datadog_sample_response_mem.json"), Charsets.UTF_8);

    Map<String, List<APMMetricInfo>> metricEndpointsInfo = DatadogState.metricEndpointsInfo(Optional.empty(),
        Optional.of(Lists.newArrayList("system.load.1", "system.mem.used")), Optional.empty(), Optional.empty(),
        Optional.of(KUBERNETES));

    Iterator<List<APMMetricInfo>> metricInfoIterator = metricEndpointsInfo.values().iterator();
    Collection<NewRelicMetricDataRecord> records =
        APMResponseParser.extract(Lists.newArrayList(APMResponseParser.APMResponseData.builder()
                                                         .text(textLoad)
                                                         .groupName(DEFAULT_GROUP_NAME)
                                                         .metricInfos(metricInfoIterator.next())
                                                         .build(),
            APMResponseParser.APMResponseData.builder()
                .text(textMem)
                .groupName(DEFAULT_GROUP_NAME)
                .metricInfos(metricInfoIterator.next())
                .build()));

    assertThat(records).hasSize(80);
    records.forEach(record -> record.setValidUntil(null));
    String output = Resources.toString(
        APMParserTest.class.getResource("/apm/datadog_sample_collected_response.json"), Charsets.UTF_8);

    assertThat(JsonUtils.asPrettyJson(records)).isEqualTo(output);
  }

  @Test
  @Owner(developers = SRIRAM)
  @Category(UnitTests.class)
  public void testJsonParserGraphana() throws IOException {
    String text500 =
        Resources.toString(APMParserTest.class.getResource("/apm/graphana_sample_response_500.json"), Charsets.UTF_8);

    Map<String, APMMetricInfo.ResponseMapper> responseMapperMap = new HashMap<>();
    responseMapperMap.put(
        "host", APMMetricInfo.ResponseMapper.builder().fieldName("host").jsonPath("[*].target").build());
    responseMapperMap.put("timestamp",
        APMMetricInfo.ResponseMapper.builder().fieldName("timestamp").jsonPath("[*].datapoints[*].[1]").build());
    responseMapperMap.put(
        "value", APMMetricInfo.ResponseMapper.builder().fieldName("value").jsonPath("[*].datapoints[*].[0]").build());
    responseMapperMap.put(
        "txnName", APMMetricInfo.ResponseMapper.builder().fieldName("txnName").fieldValue("500X").build());

    List<APMMetricInfo> metricInfos = Lists.newArrayList(APMMetricInfo.builder()
                                                             .metricName("500")
                                                             .metricType(MetricType.ERROR)
                                                             .tag("nginix")
                                                             .responseMappers(responseMapperMap)
                                                             .build());

    Collection<NewRelicMetricDataRecord> records =
        APMResponseParser.extract(Lists.newArrayList(APMResponseParser.APMResponseData.builder()
                                                         .text(text500)
                                                         .groupName(DEFAULT_GROUP_NAME)
                                                         .metricInfos(metricInfos)
                                                         .build()));

    assertThat(records).hasSize(61);
    records.forEach(record -> record.setValidUntil(null));
    String output = Resources.toString(
        APMParserTest.class.getResource("/apm/graphana_sample_collected_response_500.json"), Charsets.UTF_8);

    assertThat(JsonUtils.asJson(records)).isEqualTo(output);
  }

  @Test
  @Owner(developers = SRIRAM)
  @Category(UnitTests.class)
  public void testJsonParserGraphanaMapping2() throws IOException {
    String text500 =
        Resources.toString(APMParserTest.class.getResource("/apm/graphana_sample_response_500.json"), Charsets.UTF_8);

    Map<String, APMMetricInfo.ResponseMapper> responseMapperMap = new HashMap<>();
    responseMapperMap.put(
        "host", APMMetricInfo.ResponseMapper.builder().fieldName("host").jsonPath("[0].target").build());
    responseMapperMap.put("timestamp",
        APMMetricInfo.ResponseMapper.builder().fieldName("timestamp").jsonPath("[0].datapoints[*].[1]").build());
    responseMapperMap.put(
        "value", APMMetricInfo.ResponseMapper.builder().fieldName("value").jsonPath("[0].datapoints[*].[0]").build());
    responseMapperMap.put(
        "txnName", APMMetricInfo.ResponseMapper.builder().fieldName("txnName").fieldValue("500X").build());

    List<APMMetricInfo> metricInfos = Lists.newArrayList(APMMetricInfo.builder()
                                                             .metricName("500")
                                                             .metricType(MetricType.ERROR)
                                                             .tag("nginix")
                                                             .responseMappers(responseMapperMap)
                                                             .build());

    Collection<NewRelicMetricDataRecord> records =
        APMResponseParser.extract(Lists.newArrayList(APMResponseParser.APMResponseData.builder()
                                                         .text(text500)
                                                         .groupName(DEFAULT_GROUP_NAME)
                                                         .metricInfos(metricInfos)
                                                         .build()));

    assertThat(records).hasSize(61);
    records.forEach(record -> record.setValidUntil(null));
    String output = Resources.toString(
        APMParserTest.class.getResource("/apm/graphana_sample_collected_response_500.json"), Charsets.UTF_8);

    assertThat(JsonUtils.asJson(records)).isEqualTo(output);
  }

  @Test
  @Owner(developers = SRIRAM)
  @Category(UnitTests.class)
  public void testJsonParserGraphanaMappingArray() throws IOException {
    String text500 = Resources.toString(
        APMParserTest.class.getResource("/apm/graphana_sample_response_500_multiple.json"), Charsets.UTF_8);

    Map<String, APMMetricInfo.ResponseMapper> responseMapperMap = new HashMap<>();
    responseMapperMap.put(
        "host", APMMetricInfo.ResponseMapper.builder().fieldName("host").jsonPath("[*].target").build());
    responseMapperMap.put("timestamp",
        APMMetricInfo.ResponseMapper.builder().fieldName("timestamp").jsonPath("[*].datapoints[*].[1]").build());
    responseMapperMap.put(
        "value", APMMetricInfo.ResponseMapper.builder().fieldName("value").jsonPath("[*].datapoints[*].[0]").build());
    responseMapperMap.put(
        "txnName", APMMetricInfo.ResponseMapper.builder().fieldName("txnName").fieldValue("500X").build());

    List<APMMetricInfo> metricInfos = Lists.newArrayList(APMMetricInfo.builder()
                                                             .metricName("500")
                                                             .metricType(MetricType.ERROR)
                                                             .tag("nginix")
                                                             .responseMappers(responseMapperMap)
                                                             .build());

    Collection<NewRelicMetricDataRecord> records =
        APMResponseParser.extract(Lists.newArrayList(APMResponseParser.APMResponseData.builder()
                                                         .text(text500)
                                                         .groupName(DEFAULT_GROUP_NAME)
                                                         .metricInfos(metricInfos)
                                                         .build()));

    assertThat(records).hasSize(4);
    records.forEach(record -> record.setValidUntil(null));
    String output = Resources.toString(
        APMParserTest.class.getResource("/apm/graphana_sample_collected_response_500_multiple.json"), Charsets.UTF_8);

    assertThat(JsonUtils.asJson(records)).isEqualTo(output);
  }

  @Test
  @Owner(developers = KAMAL)
  @Category(UnitTests.class)
  public void testExtract_loganalytics_sample() throws IOException {
    String text500 =
        Resources.toString(APMParserTest.class.getResource("/apm/loganalytics_sample.json"), Charsets.UTF_8);

    Map<String, APMMetricInfo.ResponseMapper> responseMapperMap = new HashMap<>();
    responseMapperMap.put("timestamp",
        APMMetricInfo.ResponseMapper.builder()
            .fieldName("timestamp")
            .timestampFormat("yyyy-MM-dd'T'HH:mm:ss'Z'")
            .jsonPath("tables[0].rows[*].[1]")
            .build());
    responseMapperMap.put(
        "value", APMMetricInfo.ResponseMapper.builder().fieldName("value").jsonPath("tables[0].rows[*].[2]").build());
    responseMapperMap.put("txnName",
        APMMetricInfo.ResponseMapper.builder().fieldName("txnName").jsonPath("tables[0].rows[*].[0]").build());

    List<APMMetricInfo> metricInfos = Lists.newArrayList(APMMetricInfo.builder()
                                                             .metricName("500")
                                                             .metricType(MetricType.ERROR)
                                                             .tag("nginix")
                                                             .responseMappers(responseMapperMap)
                                                             .build());

    Collection<NewRelicMetricDataRecord> records =
        APMResponseParser.extract(Lists.newArrayList(APMResponseParser.APMResponseData.builder()
                                                         .text(text500)
                                                         .groupName(DEFAULT_GROUP_NAME)
                                                         .metricInfos(metricInfos)
                                                         .build()));
    // TODO: this test is for testing timestamp parsing logic. The metrics names are not parsed correctly
    assertThat(records).hasSize(126);
  }

  @Test
  @Owner(developers = PRAVEEN)
  @Category(UnitTests.class)
  public void testJsonParserInsightsResponse() throws IOException {
    String text500 =
        Resources.toString(APMParserTest.class.getResource("/apm/insights_sample_response.json"), Charsets.UTF_8);

    Map<String, APMMetricInfo.ResponseMapper> responseMapperMap = new HashMap<>();
    responseMapperMap.put(
        "host", APMMetricInfo.ResponseMapper.builder().fieldName("host").jsonPath("facets[*].name[1]").build());
    responseMapperMap.put("timestamp",
        APMMetricInfo.ResponseMapper.builder()
            .fieldName("timestamp")
            .jsonPath("facets[*].timeSeries[*].endTimeSeconds")
            .build());
    responseMapperMap.put("value",
        APMMetricInfo.ResponseMapper.builder()
            .fieldName("value")
            .jsonPath("facets[*].timeSeries[*].results[*].count")
            .build());
    responseMapperMap.put(
        "txnName", APMMetricInfo.ResponseMapper.builder().fieldName("txnName").jsonPath("facets[*].name[0]").build());

    List<APMMetricInfo> metricInfos = Lists.newArrayList(APMMetricInfo.builder()
                                                             .metricName("HttpErrors")
                                                             .metricType(MetricType.ERROR)
                                                             .tag("NRHTTP")
                                                             .responseMappers(responseMapperMap)
                                                             .build());

    Collection<NewRelicMetricDataRecord> records =
        APMResponseParser.extract(Lists.newArrayList(APMResponseParser.APMResponseData.builder()
                                                         .text(text500)
                                                         .metricInfos(metricInfos)
                                                         .groupName(DEFAULT_GROUP_NAME)
                                                         .build()));

    assertThat(records).hasSize(10);
    records.forEach(record -> record.setValidUntil(null));
    String output =
        Resources.toString(APMParserTest.class.getResource("/apm/insights_sample_collected.json"), Charsets.UTF_8);

    assertThat(JsonUtils.asJson(records)).isEqualTo(output);
  }

  @Test
  @Owner(developers = PRAVEEN)
  @Category(UnitTests.class)
  public void testJsonParserAzureAnalyticsResponse() throws IOException {
    String text500 =
        Resources.toString(APMParserTest.class.getResource("/apm/azure-analytics-response.json"), Charsets.UTF_8);
    Map<String, APMMetricInfo.ResponseMapper> responseMapperMap = new HashMap<>();

    responseMapperMap.put("timestamp",
        APMMetricInfo.ResponseMapper.builder()
            .fieldName("timestamp")
            .jsonPath("tables[*].rows[*].[1]")
            .timestampFormat("yyyy-MM-dd'T'HH:mm:ss'Z'")
            .build());
    responseMapperMap.put(
        "value", APMMetricInfo.ResponseMapper.builder().fieldName("value").jsonPath("tables[*].rows[*].[2]").build());
    responseMapperMap.put("txnName",
        APMMetricInfo.ResponseMapper.builder().fieldName("txnName").jsonPath("tables[*].rows[*].[0]").build());

    List<APMMetricInfo> metricInfos = Lists.newArrayList(APMMetricInfo.builder()
                                                             .metricName("HttpErrors")
                                                             .metricType(MetricType.ERROR)
                                                             .tag("NRHTTP")
                                                             .responseMappers(responseMapperMap)
                                                             .build());

    Collection<NewRelicMetricDataRecord> records =
        APMResponseParser.extract(Lists.newArrayList(APMResponseParser.APMResponseData.builder()
                                                         .text(text500)
                                                         .metricInfos(metricInfos)
                                                         .groupName(DEFAULT_GROUP_NAME)
                                                         .build()));
    assertThat(records).hasSize(126);
  }

  @Test
  @Owner(developers = PRAVEEN)
  @Category(UnitTests.class)
  public void testJsonParserArrayInsideArray() throws IOException {
    String text500 =
        Resources.toString(APMParserTest.class.getResource("/apm/insights-variation-1.json"), Charsets.UTF_8);

    Map<String, APMMetricInfo.ResponseMapper> responseMapperMap = new HashMap<>();
    responseMapperMap.put(
        "host", APMMetricInfo.ResponseMapper.builder().fieldName("host").jsonPath("facets[*].name[1]").build());
    responseMapperMap.put("timestamp",
        APMMetricInfo.ResponseMapper.builder()
            .fieldName("timestamp")
            .jsonPath("facets[*].timeSeries[*].endTimeSeconds")
            .build());
    responseMapperMap.put("value",
        APMMetricInfo.ResponseMapper.builder()
            .fieldName("value")
            .jsonPath("facets[*].timeSeries[*].results[*].[*].count")
            .build());
    responseMapperMap.put(
        "txnName", APMMetricInfo.ResponseMapper.builder().fieldName("txnName").jsonPath("facets[*].name[0]").build());

    List<APMMetricInfo> metricInfos = Lists.newArrayList(APMMetricInfo.builder()
                                                             .metricName("HttpErrors")
                                                             .metricType(MetricType.ERROR)
                                                             .tag("NRHTTP")
                                                             .responseMappers(responseMapperMap)
                                                             .build());

    Collection<NewRelicMetricDataRecord> records =
        APMResponseParser.extract(Lists.newArrayList(APMResponseParser.APMResponseData.builder()
                                                         .text(text500)
                                                         .metricInfos(metricInfos)
                                                         .groupName(DEFAULT_GROUP_NAME)
                                                         .build()));

    assertThat(records).hasSize(10);
    records.forEach(record -> record.setValidUntil(null));
    String output =
        Resources.toString(APMParserTest.class.getResource("/apm/insights-variation-1-collected.json"), Charsets.UTF_8);
    assertThat(JsonUtils.asJson(records)).isEqualTo(output);
  }

  @Test
  @Owner(developers = SRIRAM)
  @Category(UnitTests.class)
  public void apmVerificationstateYaml() throws IOException {
    String yaml =
        "- collectionUrl: query?from=${start_time}&to=${end_time}&query=trace.servlet.request.duration{service:todolist, host:${host}}by{resource_name, host}.rollup(avg,60)\n"
        + "  metricName: Request Duration\n"
        + "  metricType: RESP_TIME\n"
        + "  tag: Servlet\n"
        + "  method: POST\n"
        + "  responseMappings:\n"
        + "    txnNameJsonPath: series[*].scope\n"
        + "    txnNameRegex: ((?<=resource_name:)(.*)(?=,))  \n"
        + "    timestampJsonPath: series[*].pointlist[*].[0]\n"
        + "    metricValueJsonPath: series[*].pointlist[*].[1]";
    YamlUtils yamlUtils = new YamlUtils();
    yamlUtils.read(yaml, new TypeReference<List<ApmMetricCollectionInfo>>() {});
  }
}
