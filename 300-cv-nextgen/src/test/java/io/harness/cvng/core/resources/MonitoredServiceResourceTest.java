/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.resources;

import static io.harness.rule.OwnerRule.ABHIJITH;
import static io.harness.rule.OwnerRule.DEEPAK_CHHIKARA;
import static io.harness.rule.OwnerRule.KAMAL;
import static io.harness.rule.OwnerRule.KAPIL;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CvNextGenTestBase;
import io.harness.category.element.UnitTests;
import io.harness.cvng.BuilderFactory;
import io.harness.cvng.CVNGTestConstants;
import io.harness.cvng.beans.CVMonitoringCategory;
import io.harness.cvng.beans.cvnglog.CVNGLogDTO;
import io.harness.cvng.core.beans.HealthSourceMetricDefinition;
import io.harness.cvng.core.beans.PrometheusMetricDefinition;
import io.harness.cvng.core.beans.monitoredService.HealthSource;
import io.harness.cvng.core.beans.monitoredService.MetricDTO;
import io.harness.cvng.core.beans.monitoredService.MonitoredServiceDTO;
import io.harness.cvng.core.beans.monitoredService.MonitoredServiceResponse;
import io.harness.cvng.core.beans.monitoredService.healthSouceSpec.PrometheusHealthSourceSpec;
import io.harness.cvng.core.beans.monitoredService.healthSouceSpec.SplunkMetricHealthSourceSpec;
import io.harness.cvng.core.beans.params.MonitoredServiceParams;
import io.harness.cvng.core.entities.CVConfig;
import io.harness.cvng.core.services.api.CVConfigService;
import io.harness.cvng.core.services.api.CVNGLogService;
import io.harness.cvng.core.services.api.MetricPackService;
import io.harness.cvng.core.services.api.VerificationTaskService;
import io.harness.cvng.core.services.api.monitoredService.MonitoredServiceService;
import io.harness.cvng.notification.beans.NotificationRuleDTO;
import io.harness.cvng.notification.beans.NotificationRuleResponse;
import io.harness.cvng.notification.beans.NotificationRuleType;
import io.harness.cvng.notification.services.api.NotificationRuleService;
import io.harness.rest.RestResponse;
import io.harness.rule.Owner;
import io.harness.rule.ResourceTestRule;

import com.google.inject.Inject;
import com.google.inject.Injector;
import java.io.IOException;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.GenericType;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.yaml.snakeyaml.Yaml;

public class MonitoredServiceResourceTest extends CvNextGenTestBase {
  @Inject private Injector injector;
  @Inject private MonitoredServiceService monitoredServiceService;
  private BuilderFactory builderFactory;
  private static MonitoredServiceResource monitoredServiceResource = new MonitoredServiceResource();
  @Inject MetricPackService metricPackService;
  @Inject CVNGLogService cvngLogService;
  @Inject private VerificationTaskService verificationTaskService;
  @Inject private CVConfigService cvConfigService;
  @Inject NotificationRuleService notificationRuleService;

  private MonitoredServiceDTO monitoredServiceDTO;

  @ClassRule
  public static final ResourceTestRule RESOURCES =
      ResourceTestRule.builder().addResource(monitoredServiceResource).build();
  @Before
  public void setup() {
    injector.injectMembers(monitoredServiceResource);
    builderFactory = BuilderFactory.getDefault();
    metricPackService.createDefaultMetricPackAndThresholds(builderFactory.getContext().getAccountId(),
        builderFactory.getContext().getOrgIdentifier(), builderFactory.getContext().getProjectIdentifier());
    monitoredServiceDTO = builderFactory.monitoredServiceDTOBuilder().build();
    monitoredServiceService.create(builderFactory.getContext().getAccountId(), monitoredServiceDTO);
  }

  @Test
  @Owner(developers = ABHIJITH)
  @Category(UnitTests.class)
  public void testCreateFromYaml() throws IOException {
    String yaml = "monitoredService:\n"
        + "  identifier: <+monitoredService.serviceRef>\n"
        + "  type: Application\n"
        + "  description: description\n"
        + "  name: <+monitoredService.identifier>\n"
        + "  serviceRef: service1\n"
        + "  environmentRefList:\n"
        + "   - env1\n"
        + "  tags: {}\n"
        + "  sources:\n"
        + "    healthSources:\n"
        + "    changeSources: \n";
    Response createResponse = RESOURCES.client()
                                  .target("http://localhost:9998/monitored-service/yaml")
                                  .queryParam("accountId", builderFactory.getContext().getAccountId())
                                  .queryParam("projectIdentifier", builderFactory.getContext().getProjectIdentifier())
                                  .queryParam("orgIdentifier", builderFactory.getContext().getOrgIdentifier())
                                  .request(MediaType.APPLICATION_JSON_TYPE)
                                  .post(Entity.text(yaml));

    assertThat(createResponse.getStatus()).isEqualTo(200);
    RestResponse<MonitoredServiceResponse> restResponse =
        createResponse.readEntity(new GenericType<RestResponse<MonitoredServiceResponse>>() {});
    MonitoredServiceDTO monitoredServiceDTO = restResponse.getResource().getMonitoredServiceDTO();
    assertThat(monitoredServiceDTO.getIdentifier()).isEqualTo("service1");
    assertThat(monitoredServiceDTO.getProjectIdentifier())
        .isEqualTo(builderFactory.getContext().getProjectIdentifier());
    assertThat(monitoredServiceDTO.getOrgIdentifier()).isEqualTo(builderFactory.getContext().getOrgIdentifier());
  }

  @Test
  @Owner(developers = ABHIJITH)
  @Category(UnitTests.class)
  public void testUpdateFromYaml() throws IOException {
    String yaml = "monitoredService:\n"
        + "  identifier: <+monitoredService.serviceRef>\n"
        + "  type: Application\n"
        + "  description: description\n"
        + "  name: <+monitoredService.identifier>\n"
        + "  serviceRef: service1\n"
        + "  environmentRefList:\n"
        + "   - env1\n"
        + "  tags: {}\n"
        + "  sources:\n"
        + "    healthSources:\n"
        + "    changeSources: \n";
    Response createResponse = RESOURCES.client()
                                  .target("http://localhost:9998/monitored-service/yaml")
                                  .queryParam("accountId", builderFactory.getContext().getAccountId())
                                  .queryParam("projectIdentifier", builderFactory.getContext().getProjectIdentifier())
                                  .queryParam("orgIdentifier", builderFactory.getContext().getOrgIdentifier())
                                  .request(MediaType.APPLICATION_JSON_TYPE)
                                  .post(Entity.text(yaml));
    assertThat(createResponse.getStatus()).isEqualTo(200);

    String updateYaml = "monitoredService:\n"
        + "  identifier: <+monitoredService.serviceRef>\n"
        + "  type: Application\n"
        + "  description: description345\n"
        + "  name: <+monitoredService.identifier>\n"
        + "  serviceRef: service1\n"
        + "  environmentRefList:\n"
        + "   - env1\n"
        + "  tags: {}\n"
        + "  sources:\n"
        + "    healthSources:\n"
        + "    changeSources: \n";
    Response updateResponse = RESOURCES.client()
                                  .target("http://localhost:9998/monitored-service/service1/yaml")
                                  .queryParam("accountId", builderFactory.getContext().getAccountId())
                                  .queryParam("projectIdentifier", builderFactory.getContext().getProjectIdentifier())
                                  .queryParam("orgIdentifier", builderFactory.getContext().getOrgIdentifier())
                                  .request(MediaType.APPLICATION_JSON_TYPE)
                                  .put(Entity.text(updateYaml));
    assertThat(updateResponse.getStatus()).isEqualTo(200);
    RestResponse<MonitoredServiceResponse> restResponse =
        updateResponse.readEntity(new GenericType<RestResponse<MonitoredServiceResponse>>() {});
    MonitoredServiceDTO monitoredServiceDTO = restResponse.getResource().getMonitoredServiceDTO();
    assertThat(monitoredServiceDTO.getDescription()).isEqualTo("description345");
  }

  @Test
  @Owner(developers = ABHIJITH)
  @Category(UnitTests.class)
  public void testGetSloMetrics() throws IOException {
    String monitoredServiceYaml = getResource("monitoredservice/monitored-service-prometheus.yaml");

    Response createResponse = RESOURCES.client()
                                  .target("http://localhost:9998/monitored-service/")
                                  .queryParam("accountId", builderFactory.getContext().getAccountId())
                                  .request(MediaType.APPLICATION_JSON_TYPE)
                                  .post(Entity.json(convertToJson(monitoredServiceYaml)));
    assertThat(createResponse.getStatus()).isEqualTo(200);

    Response response =
        RESOURCES.client()
            .target("http://localhost:9998/monitored-service/MSIdentifier/health-source/test/slo-metrics")
            .queryParam("accountId", builderFactory.getContext().getAccountId())
            .queryParam("projectIdentifier", "cvng_proj_fve79nRfOe")
            .queryParam("orgIdentifier", "cvng_org_gc5qeLWq1W")
            .request(MediaType.APPLICATION_JSON_TYPE)
            .get();
    assertThat(response.getStatus()).isEqualTo(200);
    RestResponse<List<MetricDTO>> restResponse =
        response.readEntity(new GenericType<RestResponse<List<MetricDTO>>>() {});
    List<MetricDTO> metricDTOS = restResponse.getResource();
    assertThat(metricDTOS).hasSize(1);
    assertThat(metricDTOS.get(0).getMetricName()).isEqualTo("Prometheus Metric");
    assertThat(metricDTOS.get(0).getIdentifier()).isEqualTo("PrometheusMetric");
  }

  @Test
  @Owner(developers = DEEPAK_CHHIKARA)
  @Category(UnitTests.class)
  public void test_createDefaultFail() {
    Response response = RESOURCES.client()
                            .target("http://localhost:9998/monitored-service/create-default")
                            .queryParam("accountId", builderFactory.getContext().getAccountId())
                            .queryParam("projectIdentifier", "cvng_proj_fve79nRfOe")
                            .queryParam("orgIdentifier", "cvng_org_gc5qeLWq1W")
                            .queryParam("environmentIdentifier", "")
                            .queryParam("serviceIdentifier", "")
                            .request(MediaType.APPLICATION_JSON_TYPE)
                            .post(Entity.json(null));
    assertThat(response.getStatus()).isEqualTo(400);
  }

  @Test
  @Owner(developers = DEEPAK_CHHIKARA)
  @Category(UnitTests.class)
  public void test_createDefaultFailWithNull() {
    Response response = RESOURCES.client()
                            .target("http://localhost:9998/monitored-service/create-default")
                            .queryParam("accountId", builderFactory.getContext().getAccountId())
                            .queryParam("projectIdentifier", "cvng_proj_fve79nRfOe")
                            .queryParam("orgIdentifier", "cvng_org_gc5qeLWq1W")
                            .request(MediaType.APPLICATION_JSON_TYPE)
                            .post(Entity.json(null));
    assertThat(response.getStatus()).isEqualTo(400);
  }

  @Test
  @Owner(developers = DEEPAK_CHHIKARA)
  @Category(UnitTests.class)
  public void test_createDefault() {
    Response response = RESOURCES.client()
                            .target("http://localhost:9998/monitored-service/create-default")
                            .queryParam("accountId", builderFactory.getContext().getAccountId())
                            .queryParam("projectIdentifier", "cvng_proj_fve79nRfOe")
                            .queryParam("orgIdentifier", "cvng_org_gc5qeLWq1W")
                            .queryParam("environmentIdentifier", "environmentIdentifier")
                            .queryParam("serviceIdentifier", "serviceIdentifier")
                            .request(MediaType.APPLICATION_JSON_TYPE)
                            .post(Entity.json(null));
    assertThat(response.getStatus()).isEqualTo(200);
  }

  @Test
  @Owner(developers = KAMAL)
  @Category(UnitTests.class)
  public void testSaveMonitoredService_withMetricDefIdentifier() throws IOException {
    String monitoredServiceYaml = getResource("monitoredservice/monitored-service-prometheus.yaml");

    Response response = RESOURCES.client()
                            .target("http://localhost:9998/monitored-service/")
                            .queryParam("accountId", builderFactory.getContext().getAccountId())
                            .request(MediaType.APPLICATION_JSON_TYPE)
                            .post(Entity.json(convertToJson(monitoredServiceYaml)));
    assertThat(response.getStatus()).isEqualTo(200);
    RestResponse<MonitoredServiceResponse> restResponse =
        response.readEntity(new GenericType<RestResponse<MonitoredServiceResponse>>() {});
    MonitoredServiceDTO monitoredServiceDTO = restResponse.getResource().getMonitoredServiceDTO();
    assertThat(monitoredServiceDTO.getSources().getHealthSources()).hasSize(1);
    HealthSource healthSource = monitoredServiceDTO.getSources().getHealthSources().iterator().next();
    HealthSourceMetricDefinition healthSourceMetricDefinition =
        ((PrometheusHealthSourceSpec) healthSource.getSpec()).getMetricDefinitions().get(0);
    // assertThat(healthSourceMetricDefinition.getIdentifier()).isEqualTo("prometheus_metric123");
    assertThat(healthSourceMetricDefinition.getIdentifier())
        .isEqualTo("PrometheusMetric"); // TODO: remove this after enabling validation.
  }

  @Test
  @Owner(developers = KAMAL)
  @Category(UnitTests.class)
  public void testSaveMonitoredService_withSplunkMetric() throws IOException {
    String monitoredServiceYaml = getResource("monitoredservice/monitored-service-splunk-metrics.yaml");

    Response response = RESOURCES.client()
                            .target("http://localhost:9998/monitored-service/")
                            .queryParam("accountId", builderFactory.getContext().getAccountId())
                            .request(MediaType.APPLICATION_JSON_TYPE)
                            .post(Entity.json(convertToJson(monitoredServiceYaml)));
    assertThat(response.getStatus()).isEqualTo(200);
    RestResponse<MonitoredServiceResponse> restResponse =
        response.readEntity(new GenericType<RestResponse<MonitoredServiceResponse>>() {});
    MonitoredServiceDTO monitoredServiceDTO = restResponse.getResource().getMonitoredServiceDTO();
    assertThat(monitoredServiceDTO.getSources().getHealthSources()).hasSize(1);
    HealthSource healthSource = monitoredServiceDTO.getSources().getHealthSources().iterator().next();
    assertThat(((SplunkMetricHealthSourceSpec) healthSource.getSpec()).getFeature()).isEqualTo("Splunk Metric");
    HealthSourceMetricDefinition healthSourceMetricDefinition =
        ((SplunkMetricHealthSourceSpec) healthSource.getSpec()).getMetricDefinitions().get(0);
    assertThat(healthSourceMetricDefinition.getIdentifier()).isEqualTo("splunk_response_time");
  }

  @Test
  @Owner(developers = DEEPAK_CHHIKARA)
  @Category(UnitTests.class)
  public void testSaveMonitoredService_withOnlySLIEnabled() throws IOException {
    String monitoredServiceYaml = getResource("monitoredservice/monitored-service-prometheus-only-sli.yaml");

    Response response = RESOURCES.client()
                            .target("http://localhost:9998/monitored-service/")
                            .queryParam("accountId", builderFactory.getContext().getAccountId())
                            .request(MediaType.APPLICATION_JSON_TYPE)
                            .post(Entity.json(convertToJson(monitoredServiceYaml)));
    assertThat(response.getStatus()).isEqualTo(200);
    RestResponse<MonitoredServiceResponse> restResponse =
        response.readEntity(new GenericType<RestResponse<MonitoredServiceResponse>>() {});
    MonitoredServiceDTO monitoredServiceDTO = restResponse.getResource().getMonitoredServiceDTO();
    assertThat(monitoredServiceDTO.getSources().getHealthSources()).hasSize(1);
    PrometheusMetricDefinition metricDefinition = ((PrometheusHealthSourceSpec) monitoredServiceDTO.getSources()
                                                       .getHealthSources()
                                                       .stream()
                                                       .findAny()
                                                       .get()
                                                       .getSpec())
                                                      .getMetricDefinitions()
                                                      .get(0);
    assertThat(metricDefinition.getSli().getEnabled()).isTrue();
    assertThat(metricDefinition.getAnalysis().getRiskProfile().getThresholdTypes()).isEmpty();
    // TODO Need to be remove the default behaviour
    assertThat(metricDefinition.getAnalysis().getRiskProfile().getCategory()).isEqualTo(CVMonitoringCategory.ERRORS);
    assertThat(metricDefinition.getAnalysis().getLiveMonitoring().getEnabled()).isFalse();
  }

  @Test
  @Owner(developers = KAMAL)
  @Category(UnitTests.class)
  public void testSaveMonitoredService_invalidYAMLForMetricDefWithSameIdentifiers() throws IOException {
    String monitoredServiceYaml =
        getResource("monitoredservice/monitored-service-invalid-metric-def-duplicate-identifier.yaml");

    Response response = RESOURCES.client()
                            .target("http://localhost:9998/monitored-service/")
                            .queryParam("accountId", builderFactory.getContext().getAccountId())
                            .request(MediaType.APPLICATION_JSON_TYPE)
                            .post(Entity.json(convertToJson(monitoredServiceYaml)));
    assertThat(response.getStatus()).isEqualTo(400);
    assertThat(response.readEntity(String.class))
        .contains("{\"field\":\"metricDefinitions\",\"message\":\"same identifier is used by multiple entities\"}");
  }

  @Test
  @Owner(developers = KAMAL)
  @Category(UnitTests.class)
  public void testSaveMonitoredService_missingIdentifierHealthSoruce() throws IOException {
    String monitoredServiceYaml =
        getResource("monitoredservice/monitored-service-prometheus-invalid-empty-identifier.yaml");

    Response response = RESOURCES.client()
                            .target("http://localhost:9998/monitored-service/")
                            .queryParam("accountId", builderFactory.getContext().getAccountId())
                            .request(MediaType.APPLICATION_JSON_TYPE)
                            .post(Entity.json(convertToJson(monitoredServiceYaml)));
    assertThat(response.getStatus()).isEqualTo(400);
    assertThat(response.readEntity(String.class)).contains("\"field\":\"identifier\",\"message\":\"cannot be empty\"");
  }

  @Test
  @Owner(developers = KAMAL)
  @Category(UnitTests.class)
  @Ignore("Enable after ui start sending identifier")
  public void testSaveMonitoredService_invalidMetricDefIdentifier() throws IOException {
    String monitoredServiceYaml = getResource("monitoredservice/monitored-service-invalid-metric-def-identifier.yaml");

    Response response = RESOURCES.client()
                            .target("http://localhost:9998/monitored-service/")
                            .queryParam("accountId", builderFactory.getContext().getAccountId())
                            .request(MediaType.APPLICATION_JSON_TYPE)
                            .post(Entity.json(convertToJson(monitoredServiceYaml)));
    assertThat(response.getStatus()).isEqualTo(400);
    assertThat(response.readEntity(String.class))
        .contains(
            "{\"field\":\"identifier\",\"message\":\"can be 64 characters long and can only contain alphanumeric, underscore and $ characters, and not start with a number\"}");
  }

  @Test
  @Owner(developers = KAMAL)
  @Category(UnitTests.class)
  public void testListMonitoredService_listScreenAPI() {
    monitoredServiceService.createDefault(builderFactory.getProjectParams(), "service1", "env1");
    monitoredServiceService.createDefault(builderFactory.getProjectParams(), "service2", "env2");
    monitoredServiceService.createDefault(builderFactory.getProjectParams(), "service3", "env1");

    WebTarget webTarget = RESOURCES.client()
                              .target("http://localhost:9998/monitored-service/")
                              .queryParam("accountId", builderFactory.getContext().getAccountId())
                              .queryParam("orgIdentifier", builderFactory.getContext().getOrgIdentifier())
                              .queryParam("projectIdentifier", builderFactory.getContext().getProjectIdentifier())
                              .queryParam("offset", 0)
                              .queryParam("pageSize", 10);
    webTarget = webTarget.queryParam("environmentIdentifier", "env1");
    Response response = webTarget.request(MediaType.APPLICATION_JSON_TYPE).get();
    assertThat(response.getStatus()).isEqualTo(200);
    assertThat(response.readEntity(String.class)).contains("\"totalItems\":2");
  }

  @Test
  @Owner(developers = KAMAL)
  @Category(UnitTests.class)
  public void testListMonitoredService_dependenciesList() {
    monitoredServiceService.createDefault(builderFactory.getProjectParams(), "service1", "env1");
    monitoredServiceService.createDefault(builderFactory.getProjectParams(), "service2", "env2");
    monitoredServiceService.createDefault(builderFactory.getProjectParams(), "service3", "env1");

    WebTarget webTarget = RESOURCES.client()
                              .target("http://localhost:9998/monitored-service/list")
                              .queryParam("accountId", builderFactory.getContext().getAccountId())
                              .queryParam("orgIdentifier", builderFactory.getContext().getOrgIdentifier())
                              .queryParam("projectIdentifier", builderFactory.getContext().getProjectIdentifier())
                              .queryParam("offset", 0)
                              .queryParam("pageSize", 10);
    for (String envIdentifier : Arrays.asList("env1", "env2")) {
      webTarget = webTarget.queryParam("environmentIdentifiers", envIdentifier);
    }
    Response response = webTarget.request(MediaType.APPLICATION_JSON_TYPE).get();
    assertThat(response.getStatus()).isEqualTo(200);
    assertThat(response.readEntity(String.class)).contains("\"totalItems\":3");
  }

  @Test
  @Owner(developers = KAPIL)
  @Category(UnitTests.class)
  public void testGetMonitoredServiceLogs() {
    Instant startTime = CVNGTestConstants.FIXED_TIME_FOR_TESTS.instant().minusSeconds(5);
    Instant endTime = CVNGTestConstants.FIXED_TIME_FOR_TESTS.instant();
    MonitoredServiceParams monitoredServiceParams =
        MonitoredServiceParams.builderWithProjectParams(builderFactory.getContext().getProjectParams())
            .monitoredServiceIdentifier(monitoredServiceDTO.getIdentifier())
            .build();
    List<String> cvConfigIds =
        cvConfigService.list(monitoredServiceParams).stream().map(CVConfig::getUuid).collect(Collectors.toList());
    List<String> verificationTaskIds = verificationTaskService.getServiceGuardVerificationTaskIds(
        builderFactory.getContext().getAccountId(), cvConfigIds);

    List<CVNGLogDTO> cvngLogDTOs =
        IntStream.range(0, 3)
            .mapToObj(index -> builderFactory.executionLogDTOBuilder().traceableId(verificationTaskIds.get(0)).build())
            .collect(Collectors.toList());
    cvngLogService.save(cvngLogDTOs);

    WebTarget webTarget =
        RESOURCES.client()
            .target("http://localhost:9998/monitored-service/" + monitoredServiceDTO.getIdentifier() + "/logs")
            .queryParam("accountId", builderFactory.getContext().getAccountId())
            .queryParam("orgIdentifier", builderFactory.getContext().getOrgIdentifier())
            .queryParam("projectIdentifier", builderFactory.getContext().getProjectIdentifier())
            .queryParam("logType", "ExecutionLog")
            .queryParam("startTime", startTime.toEpochMilli())
            .queryParam("endTime", endTime.toEpochMilli())
            .queryParam("pageNumber", 0)
            .queryParam("pageSize", 10);

    Response response = webTarget.request(MediaType.APPLICATION_JSON_TYPE).get();
    assertThat(response.getStatus()).isEqualTo(200);
    assertThat(response.readEntity(String.class)).contains("\"totalItems\":1");
  }

  @Test
  @Owner(developers = KAPIL)
  @Category(UnitTests.class)
  public void testGetMonitoredServiceLogs_withIncorrectLogType() {
    Instant startTime = CVNGTestConstants.FIXED_TIME_FOR_TESTS.instant().minusSeconds(5);
    Instant endTime = CVNGTestConstants.FIXED_TIME_FOR_TESTS.instant();
    WebTarget webTarget =
        RESOURCES.client()
            .target("http://localhost:9998/monitored-service/" + monitoredServiceDTO.getIdentifier() + "/logs")
            .queryParam("accountId", builderFactory.getContext().getAccountId())
            .queryParam("orgIdentifier", builderFactory.getContext().getOrgIdentifier())
            .queryParam("projectIdentifier", builderFactory.getContext().getProjectIdentifier())
            .queryParam("logType", "executionlog")
            .queryParam("startTime", startTime.toEpochMilli())
            .queryParam("endTime", endTime.toEpochMilli())
            .queryParam("pageNumber", 0)
            .queryParam("pageSize", 10);

    Response response = webTarget.request(MediaType.APPLICATION_JSON_TYPE).get();
    assertThat(response.getStatus()).isEqualTo(400);
    assertThat(response.readEntity(String.class)).contains("Failed to convert query param logType to CVNGLogType");
  }

  @Test
  @Owner(developers = KAPIL)
  @Category(UnitTests.class)
  public void testGetMonitoredServiceLogs_withNoMonitoredService() {
    Instant startTime = CVNGTestConstants.FIXED_TIME_FOR_TESTS.instant().minusSeconds(5);
    Instant endTime = CVNGTestConstants.FIXED_TIME_FOR_TESTS.instant();
    WebTarget webTarget = RESOURCES.client()
                              .target("http://localhost:9998/monitored-service/"
                                  + "monitoredServiceIdentifier"
                                  + "/logs")
                              .queryParam("accountId", builderFactory.getContext().getAccountId())
                              .queryParam("orgIdentifier", builderFactory.getContext().getOrgIdentifier())
                              .queryParam("projectIdentifier", builderFactory.getContext().getProjectIdentifier())
                              .queryParam("logType", "ExecutionLog")
                              .queryParam("startTime", startTime.toEpochMilli())
                              .queryParam("endTime", endTime.toEpochMilli())
                              .queryParam("pageNumber", 0)
                              .queryParam("pageSize", 10);

    Response response = webTarget.request(MediaType.APPLICATION_JSON_TYPE).get();
    assertThat(response.getStatus()).isEqualTo(404);
    assertThat(response.readEntity(String.class))
        .contains("Monitored Service with identifier monitoredServiceIdentifier not found.");
  }

  @Test
  @Owner(developers = KAPIL)
  @Category(UnitTests.class)
  public void testCreate_withNotificationRules() throws IOException {
    NotificationRuleDTO notificationRuleDTO =
        builderFactory.getNotificationRuleDTOBuilder(NotificationRuleType.MONITORED_SERVICE).build();
    NotificationRuleResponse notificationRuleResponse =
        notificationRuleService.create(builderFactory.getContext().getProjectParams(), notificationRuleDTO);

    String monitoredServiceYaml = getResource("monitoredservice/monitored-service-with-notification-rule.yaml");
    monitoredServiceYaml =
        monitoredServiceYaml.replace("$orgIdentifier", builderFactory.getContext().getOrgIdentifier());
    monitoredServiceYaml =
        monitoredServiceYaml.replace("$projectIdentifier", builderFactory.getContext().getProjectIdentifier());
    monitoredServiceYaml =
        monitoredServiceYaml.replace("$identifier", notificationRuleResponse.getNotificationRule().getIdentifier());
    monitoredServiceYaml = monitoredServiceYaml.replace("$enabled", "false");
    Response response = RESOURCES.client()
                            .target("http://localhost:9998/monitored-service/")
                            .queryParam("accountId", builderFactory.getContext().getAccountId())
                            .request(MediaType.APPLICATION_JSON_TYPE)
                            .post(Entity.json(convertToJson(monitoredServiceYaml)));
    assertThat(response.getStatus()).isEqualTo(200);
    assertThat(response.readEntity(String.class))
        .contains("\"notificationRuleRefs\":[{\"notificationRuleRef\":\"rule\",\"enabled\":false}]");
  }

  @Test
  @Owner(developers = KAPIL)
  @Category(UnitTests.class)
  public void testUpdateMonitoredServiceData_withNotificationRules() throws IOException {
    NotificationRuleDTO notificationRuleDTO =
        builderFactory.getNotificationRuleDTOBuilder(NotificationRuleType.MONITORED_SERVICE).build();
    NotificationRuleResponse notificationRuleResponse =
        notificationRuleService.create(builderFactory.getContext().getProjectParams(), notificationRuleDTO);
    String monitoredServiceYaml = getResource("monitoredservice/monitored-service-with-notification-rule.yaml");
    monitoredServiceYaml =
        monitoredServiceYaml.replace("$orgIdentifier", builderFactory.getContext().getOrgIdentifier());
    monitoredServiceYaml =
        monitoredServiceYaml.replace("$projectIdentifier", builderFactory.getContext().getProjectIdentifier());
    monitoredServiceYaml =
        monitoredServiceYaml.replace("$identifier", notificationRuleResponse.getNotificationRule().getIdentifier());
    monitoredServiceYaml = monitoredServiceYaml.replace("$enabled", "false");

    Response response = RESOURCES.client()
                            .target("http://localhost:9998/monitored-service/")
                            .queryParam("accountId", builderFactory.getContext().getAccountId())
                            .request(MediaType.APPLICATION_JSON_TYPE)
                            .post(Entity.json(convertToJson(monitoredServiceYaml)));
    assertThat(response.getStatus()).isEqualTo(200);
    assertThat(response.readEntity(String.class))
        .contains("\"notificationRuleRefs\":[{\"notificationRuleRef\":\"rule\",\"enabled\":false}]");

    monitoredServiceYaml = getResource("monitoredservice/monitored-service-with-notification-rule.yaml");
    monitoredServiceYaml =
        monitoredServiceYaml.replace("$orgIdentifier", builderFactory.getContext().getOrgIdentifier());
    monitoredServiceYaml =
        monitoredServiceYaml.replace("$projectIdentifier", builderFactory.getContext().getProjectIdentifier());
    monitoredServiceYaml =
        monitoredServiceYaml.replace("$identifier", notificationRuleResponse.getNotificationRule().getIdentifier());
    monitoredServiceYaml = monitoredServiceYaml.replace("$enabled", "true");

    response = RESOURCES.client()
                   .target("http://localhost:9998/monitored-service/"
                       + "MSIdentifier")
                   .queryParam("accountId", builderFactory.getContext().getAccountId())
                   .request(MediaType.APPLICATION_JSON_TYPE)
                   .put(Entity.json(convertToJson(monitoredServiceYaml)));
    assertThat(response.getStatus()).isEqualTo(200);
    assertThat(response.readEntity(String.class))
        .contains("\"notificationRuleRefs\":[{\"notificationRuleRef\":\"rule\",\"enabled\":true}]");
  }

  @Test
  @Owner(developers = KAPIL)
  @Category(UnitTests.class)
  public void testGetNotificationRules() throws IOException {
    NotificationRuleDTO notificationRuleDTO =
        builderFactory.getNotificationRuleDTOBuilder(NotificationRuleType.MONITORED_SERVICE).build();
    NotificationRuleResponse notificationRuleResponse =
        notificationRuleService.create(builderFactory.getContext().getProjectParams(), notificationRuleDTO);

    String monitoredServiceYaml = getResource("monitoredservice/monitored-service-with-notification-rule.yaml");
    monitoredServiceYaml =
        monitoredServiceYaml.replace("$orgIdentifier", builderFactory.getContext().getOrgIdentifier());
    monitoredServiceYaml =
        monitoredServiceYaml.replace("$projectIdentifier", builderFactory.getContext().getProjectIdentifier());
    monitoredServiceYaml =
        monitoredServiceYaml.replace("$identifier", notificationRuleResponse.getNotificationRule().getIdentifier());
    monitoredServiceYaml = monitoredServiceYaml.replace("$enabled", "false");
    Response response = RESOURCES.client()
                            .target("http://localhost:9998/monitored-service/")
                            .queryParam("accountId", builderFactory.getContext().getAccountId())
                            .request(MediaType.APPLICATION_JSON_TYPE)
                            .post(Entity.json(convertToJson(monitoredServiceYaml)));
    assertThat(response.getStatus()).isEqualTo(200);
    assertThat(response.readEntity(String.class))
        .contains("\"notificationRuleRefs\":[{\"notificationRuleRef\":\"rule\",\"enabled\":false}]");

    response = RESOURCES.client()
                   .target("http://localhost:9998/monitored-service/"
                       + "MSIdentifier"
                       + "/notification-rules")
                   .queryParam("accountId", builderFactory.getContext().getAccountId())
                   .queryParam("orgIdentifier", builderFactory.getContext().getOrgIdentifier())
                   .queryParam("projectIdentifier", builderFactory.getContext().getProjectIdentifier())
                   .queryParam("pageNumber", 0)
                   .queryParam("pageSize", 10)
                   .request(MediaType.APPLICATION_JSON_TYPE)
                   .get();
    assertThat(response.getStatus()).isEqualTo(200);
    assertThat(response.readEntity(String.class)).contains("\"totalItems\":1");

    response = RESOURCES.client()
                   .target("http://localhost:9998/monitored-service/"
                       + "MSIdentifier1"
                       + "/notification-rules")
                   .queryParam("accountId", builderFactory.getContext().getAccountId())
                   .queryParam("orgIdentifier", builderFactory.getContext().getOrgIdentifier())
                   .queryParam("projectIdentifier", builderFactory.getContext().getProjectIdentifier())
                   .queryParam("pageNumber", 0)
                   .queryParam("pageSize", 10)
                   .request(MediaType.APPLICATION_JSON_TYPE)
                   .get();
    assertThat(response.getStatus()).isEqualTo(500);
    assertThat(response.readEntity(String.class))
        .contains(String.format(
            "\"message\":\"io.harness.exception.InvalidRequestException: Monitored Service  with identifier MSIdentifier1, "
                + "accountId %s, orgIdentifier %s and projectIdentifier %s  is not present\"",
            builderFactory.getContext().getAccountId(), builderFactory.getContext().getOrgIdentifier(),
            builderFactory.getContext().getProjectIdentifier()));
  }

  private static String convertToJson(String yamlString) {
    Yaml yaml = new Yaml();
    Map<String, Object> map = yaml.load(yamlString);

    JSONObject jsonObject = new JSONObject(map);
    return jsonObject.toString();
  }
}
