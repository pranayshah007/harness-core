/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.resources;

import static io.harness.rule.OwnerRule.ABHIJITH;
import static io.harness.rule.OwnerRule.ARPITJ;
import static io.harness.rule.OwnerRule.DEEPAK_CHHIKARA;
import static io.harness.rule.OwnerRule.KAMAL;
import static io.harness.rule.OwnerRule.KAPIL;
import static io.harness.rule.OwnerRule.KARAN_SARASWAT;
import static io.harness.rule.OwnerRule.VARSHA_LALWANI;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CvNextGenTestBase;
import io.harness.category.element.UnitTests;
import io.harness.cvng.BuilderFactory;
import io.harness.cvng.CVNGTestConstants;
import io.harness.cvng.beans.CVMonitoringCategory;
import io.harness.cvng.beans.MonitoredServiceDataSourceType;
import io.harness.cvng.beans.MonitoredServiceType;
import io.harness.cvng.beans.cvnglog.CVNGLogDTO;
import io.harness.cvng.core.beans.HealthSourceMetricDefinition;
import io.harness.cvng.core.beans.PrometheusMetricDefinition;
import io.harness.cvng.core.beans.monitoredService.DurationDTO;
import io.harness.cvng.core.beans.monitoredService.HealthSource;
import io.harness.cvng.core.beans.monitoredService.MetricDTO;
import io.harness.cvng.core.beans.monitoredService.MonitoredServiceDTO;
import io.harness.cvng.core.beans.monitoredService.MonitoredServiceResponse;
import io.harness.cvng.core.beans.monitoredService.TimeSeriesMetricPackDTO;
import io.harness.cvng.core.beans.monitoredService.healthSouceSpec.DatadogMetricHealthSourceSpec;
import io.harness.cvng.core.beans.monitoredService.healthSouceSpec.PrometheusHealthSourceSpec;
import io.harness.cvng.core.beans.monitoredService.healthSouceSpec.SplunkMetricHealthSourceSpec;
import io.harness.cvng.core.beans.params.MonitoredServiceParams;
import io.harness.cvng.core.entities.CVConfig;
import io.harness.cvng.core.entities.MonitoredService;
import io.harness.cvng.core.services.api.CVConfigService;
import io.harness.cvng.core.services.api.CVNGLogService;
import io.harness.cvng.core.services.api.MetricPackService;
import io.harness.cvng.core.services.api.VerificationTaskService;
import io.harness.cvng.core.services.api.monitoredService.MonitoredServiceService;
import io.harness.cvng.notification.beans.NotificationRuleDTO;
import io.harness.cvng.notification.beans.NotificationRuleResponse;
import io.harness.cvng.notification.beans.NotificationRuleType;
import io.harness.cvng.notification.services.api.NotificationRuleService;
import io.harness.persistence.HPersistence;
import io.harness.rest.RestResponse;
import io.harness.rule.Owner;
import io.harness.rule.ResourceTestRule;
import io.harness.utils.InvalidResourceData;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import com.google.inject.Inject;
import com.google.inject.Injector;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.GenericType;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.experimental.categories.Category;

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
  @Inject private HPersistence hPersistence;
  private MonitoredServiceDTO monitoredServiceDTO;

  @ClassRule
  public static final ResourceTestRule RESOURCES = ResourceTestRule.builder()
                                                       .addResource(monitoredServiceResource)

                                                       .build();
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
        + "  environmentRef: env1\n"
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
    assertThat(monitoredServiceDTO.getIdentifier()).isEqualTo("service1_env1");
    assertThat(monitoredServiceDTO.getProjectIdentifier())
        .isEqualTo(builderFactory.getContext().getProjectIdentifier());
    assertThat(monitoredServiceDTO.getOrgIdentifier()).isEqualTo(builderFactory.getContext().getOrgIdentifier());
  }

  @Test
  @Owner(developers = DEEPAK_CHHIKARA)
  @Category(UnitTests.class)
  public void testCreateFromYamlWithMetricThresholds() throws IOException {
    String monitoredServiceYaml = getResource("monitoredservice/monitored-service-with-metric-threshold.yaml");

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
  @Owner(developers = VARSHA_LALWANI)
  @Category(UnitTests.class)
  public void testCreateFromYamlWithDataDogMetricThresholds() throws IOException {
    String monitoredServiceYaml = getResource("monitoredservice/monitored-service-with-datadog-metric-threshold.yaml");

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
    assertThat(healthSource.getType()).isEqualTo(MonitoredServiceDataSourceType.DATADOG_METRICS);
    HealthSourceMetricDefinition healthSourceMetricDefinition =
        ((DatadogMetricHealthSourceSpec) healthSource.getSpec()).getMetricDefinitions().get(0);
    assertThat(healthSourceMetricDefinition.getIdentifier()).isEqualTo("metric");
    TimeSeriesMetricPackDTO metricPackDTO =
        ((DatadogMetricHealthSourceSpec) healthSource.getSpec()).getMetricPacks().iterator().next();
    assertThat(metricPackDTO.getMetricThresholds()).hasSize(1);
    assertThat(metricPackDTO.getMetricThresholds().get(0).getMetricType()).isEqualTo("Custom");
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
        + "  environmentRef: env1\n"
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
        + "  environmentRef: env1\n"
        + "  tags: {}\n"
        + "  sources:\n"
        + "    healthSources:\n"
        + "    changeSources: \n";
    Response updateResponse = RESOURCES.client()
                                  .target("http://localhost:9998/monitored-service/service1_env1/yaml")
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
  public void testSaveMonitoredServiceScopedService() throws IOException {
    String monitoredServiceYaml = getResource("monitoredservice/monitored-service-scoped-service.yaml");

    Response response = RESOURCES.client()
                            .target("http://localhost:9998/monitored-service/")
                            .queryParam("accountId", builderFactory.getContext().getAccountId())
                            .request(MediaType.APPLICATION_JSON_TYPE)
                            .post(Entity.json(convertToJson(monitoredServiceYaml)));
    assertThat(response.getStatus()).isEqualTo(200);
    RestResponse<MonitoredServiceResponse> restResponse =
        response.readEntity(new GenericType<RestResponse<MonitoredServiceResponse>>() {});
    MonitoredServiceDTO monitoredServiceDTO = restResponse.getResource().getMonitoredServiceDTO();
    assertThat(monitoredServiceDTO.getIdentifier())
        .isEqualTo("account.cvng_service_UxrHvd7oNa_cvng_env_prod_NWceMzD9XM");
    assertThat(monitoredServiceDTO.getServiceRef()).isEqualTo("account.cvng_service_UxrHvd7oNa");
    assertThat(monitoredServiceDTO.getEnvironmentRefList())
        .isEqualTo(Collections.singletonList("cvng_env_prod_NWceMzD9XM"));
  }

  @Test
  @Owner(developers = DEEPAK_CHHIKARA)
  @Category(UnitTests.class)
  public void testSaveMonitoredServiceScopeOrg() throws IOException {
    String monitoredServiceYaml = getResource("monitoredservice/monitored-service-scoped-org.yaml");

    Response response = RESOURCES.client()
                            .target("http://localhost:9998/monitored-service/")
                            .queryParam("accountId", builderFactory.getContext().getAccountId())
                            .request(MediaType.APPLICATION_JSON_TYPE)
                            .post(Entity.json(convertToJson(monitoredServiceYaml)));
    assertThat(response.getStatus()).isEqualTo(200);
    RestResponse<MonitoredServiceResponse> restResponse =
        response.readEntity(new GenericType<RestResponse<MonitoredServiceResponse>>() {});
    MonitoredServiceDTO monitoredServiceDTO = restResponse.getResource().getMonitoredServiceDTO();
    assertThat(monitoredServiceDTO.getIdentifier()).isEqualTo("cvng_service_UxrHvd7oNa_org.cvng_env_prod_NWceMzD9XM");
    assertThat(monitoredServiceDTO.getServiceRef()).isEqualTo("cvng_service_UxrHvd7oNa");
    assertThat(monitoredServiceDTO.getEnvironmentRefList())
        .isEqualTo(Collections.singletonList("org.cvng_env_prod_NWceMzD9XM"));
  }

  @Test
  @Owner(developers = DEEPAK_CHHIKARA)
  @Category(UnitTests.class)
  public void testSaveMonitoredService_defaultEnabled() throws IOException {
    String monitoredServiceYaml = getResource("monitoredservice/monitored-service-default-enabled.yaml");

    Response response = RESOURCES.client()
                            .target("http://localhost:9998/monitored-service/")
                            .queryParam("accountId", builderFactory.getContext().getAccountId())
                            .request(MediaType.APPLICATION_JSON_TYPE)
                            .post(Entity.json(convertToJson(monitoredServiceYaml)));
    assertThat(response.getStatus()).isEqualTo(200);
    RestResponse<MonitoredServiceResponse> restResponse = response.readEntity(new GenericType<>() {});
    MonitoredServiceDTO monitoredServiceDTO = restResponse.getResource().getMonitoredServiceDTO();
    assertThat(monitoredServiceDTO.getIdentifier()).isEqualTo("cvng_service_UxrHvd7oNa_cvng_env_prod_NWceMzD9XM");
    assertThat(monitoredServiceDTO.getServiceRef()).isEqualTo("cvng_service_UxrHvd7oNa");
    assertThat(monitoredServiceDTO.getEnvironmentRefList())
        .isEqualTo(Collections.singletonList("cvng_env_prod_NWceMzD9XM"));
    assertThat(monitoredServiceDTO.isEnabled()).isFalse();
    assertThat(hPersistence.createQuery(MonitoredService.class).asList().get(0).isEnabled()).isFalse();
    response = RESOURCES.client()
                   .target("http://localhost:9998/monitored-service/" + monitoredServiceDTO.getIdentifier())
                   .queryParam("accountId", builderFactory.getContext().getAccountId())
                   .queryParam("projectIdentifier", builderFactory.getContext().getProjectIdentifier())
                   .queryParam("orgIdentifier", builderFactory.getContext().getOrgIdentifier())
                   .request(MediaType.APPLICATION_JSON_TYPE)
                   .put(Entity.json(convertToJson(monitoredServiceYaml)));
    restResponse = response.readEntity(new GenericType<>() {});
    monitoredServiceDTO = restResponse.getResource().getMonitoredServiceDTO();
    assertThat(monitoredServiceDTO.isEnabled()).isFalse();
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
  @Owner(developers = KARAN_SARASWAT)
  @Category(UnitTests.class)
  public void monitoredServiceTemplateValidation() throws IOException {
    String monitoredServiceYaml = getResource("monitoredservice/monitored-service-appd-validation.yaml");
    List<InvalidResourceData> invalidResourceDataList = new ArrayList<>();
    invalidResourceDataList.add(InvalidResourceData.builder()
                                    .path("monitoredService")
                                    .property("serviceRef")
                                    .replacementValue(null)
                                    .expectedResponseCode(500)
                                    .build());
    invalidResourceDataList.add(InvalidResourceData.builder()
                                    .path("monitoredService")
                                    .property("serviceRef")
                                    .replacementValue("")
                                    .expectedResponseCode(500)
                                    .build());
    invalidResourceDataList.add(InvalidResourceData.builder()
                                    .path("monitoredService")
                                    .property("type")
                                    .replacementValue(null)
                                    .expectedResponseCode(500)
                                    .build());
    invalidResourceDataList.add(InvalidResourceData.builder()
                                    .path("monitoredService")
                                    .property("type")
                                    .replacementValue("")
                                    .expectedResponseCode(500)
                                    .build());
    invalidResourceDataList.add(InvalidResourceData.builder()
                                    .path("monitoredService")
                                    .property("environmentRef")
                                    .replacementValue(null)
                                    .expectedResponseCode(500)
                                    .build());
    invalidResourceDataList.add(InvalidResourceData.builder()
                                    .path("monitoredService")
                                    .property("environmentRef")
                                    .replacementValue("")
                                    .expectedResponseCode(500)
                                    .build());
    invalidResourceDataList.add(InvalidResourceData.builder()
                                    .path("monitoredService\\sources\\healthSources")
                                    .property("identifier")
                                    .replacementValue("")
                                    .expectedResponseCode(500)
                                    .build());
    invalidResourceDataList.add(InvalidResourceData.builder()
                                    .path("monitoredService\\sources\\healthSources")
                                    .property("identifier")
                                    .replacementValue(null)
                                    .expectedResponseCode(500)
                                    .build());
    invalidResourceDataList.add(InvalidResourceData.builder()
                                    .path("monitoredService\\sources\\healthSources")
                                    .property("name")
                                    .replacementValue("")
                                    .expectedResponseCode(500)
                                    .build());
    invalidResourceDataList.add(InvalidResourceData.builder()
                                    .path("monitoredService\\sources\\healthSources")
                                    .property("name")
                                    .replacementValue(null)
                                    .expectedResponseCode(500)
                                    .build());
    invalidResourceDataList.add(InvalidResourceData.builder()
                                    .path("monitoredService\\sources\\healthSources\\spec")
                                    .property("connectorRef")
                                    .replacementValue(null)
                                    .expectedResponseCode(500)
                                    .build());
    invalidResourceDataList.add(InvalidResourceData.builder()
                                    .path("monitoredService\\sources\\healthSources\\spec")
                                    .property("connectorRef")
                                    .replacementValue("")
                                    .expectedResponseCode(500)
                                    .build());
    invalidResourceDataList.add(InvalidResourceData.builder()
                                    .path("monitoredService\\sources\\healthSources\\spec\\metricDefinitions")
                                    .property("groupName")
                                    .replacementValue(null)
                                    .expectedResponseCode(500)
                                    .build());
    invalidResourceDataList.add(InvalidResourceData.builder()
                                    .path("monitoredService\\sources\\healthSources\\spec\\metricDefinitions")
                                    .property("groupName")
                                    .replacementValue(null)
                                    .expectedResponseCode(500)
                                    .build());
    invalidResourceDataList.add(InvalidResourceData.builder()
                                    .path("monitoredService\\sources\\healthSources")
                                    .property("type")
                                    .replacementValue("")
                                    .expectedResponseCode(500)
                                    .build());
    invalidResourceDataList.add(InvalidResourceData.builder()
                                    .path("monitoredService\\sources\\healthSources")
                                    .property("type")
                                    .replacementValue("random")
                                    .expectedResponseCode(500)
                                    .build());
    for (InvalidResourceData invalidResourceData : invalidResourceDataList) {
      String msJson = InvalidResourceData.replace(monitoredServiceYaml, invalidResourceData);
      String msYaml = convertToYaml(msJson);
      Response response = RESOURCES.client()
                              .target("http://localhost:9998/monitored-service/yaml")
                              .queryParam("accountId", builderFactory.getContext().getAccountId())
                              .queryParam("orgIdentifier", builderFactory.getContext().getOrgIdentifier())
                              .queryParam("projectIdentifier", builderFactory.getContext().getProjectIdentifier())
                              .request(MediaType.APPLICATION_JSON_TYPE)
                              .post(Entity.text(msYaml));
      assertThat(response.getStatus()).isEqualTo(invalidResourceData.getExpectedResponseCode());
    }
  }

  @Test
  @Owner(developers = KARAN_SARASWAT)
  @Category(UnitTests.class)
  public void monitoredServiceAppDTemplateValidation() throws IOException {
    String monitoredServiceYaml = getResource("monitoredservice/monitored-service-appd-validation.yaml");
    List<InvalidResourceData> invalidResourceDataList = new ArrayList<>();
    invalidResourceDataList.add(InvalidResourceData.builder()
                                    .path("monitoredService\\sources\\healthSources\\spec")
                                    .property("applicationName")
                                    .replacementValue(null)
                                    .expectedResponseCode(500)
                                    .build());
    invalidResourceDataList.add(InvalidResourceData.builder()
                                    .path("monitoredService\\sources\\healthSources\\spec")
                                    .property("applicationName")
                                    .replacementValue("")
                                    .expectedResponseCode(500)
                                    .build());
    invalidResourceDataList.add(InvalidResourceData.builder()
                                    .path("monitoredService\\sources\\healthSources\\spec")
                                    .property("tierName")
                                    .replacementValue(null)
                                    .expectedResponseCode(500)
                                    .build());
    invalidResourceDataList.add(InvalidResourceData.builder()
                                    .path("monitoredService\\sources\\healthSources\\spec")
                                    .property("tierName")
                                    .replacementValue("")
                                    .expectedResponseCode(500)
                                    .build());
    invalidResourceDataList.add(InvalidResourceData.builder()
                                    .path("monitoredService\\sources\\healthSources\\spec\\metricDefinitions\\analysis")
                                    .property("riskProfile")
                                    .replacementValue(null)
                                    .expectedResponseCode(500)
                                    .build());
    invalidResourceDataList.add(InvalidResourceData.builder()
                                    .path("monitoredService\\sources\\healthSources\\spec")
                                    .property("feature")
                                    .replacementValue(null)
                                    .expectedResponseCode(500)
                                    .build());
    invalidResourceDataList.add(InvalidResourceData.builder()
                                    .path("monitoredService\\sources\\healthSources\\spec")
                                    .property("connectorRef")
                                    .replacementValue(null)
                                    .expectedResponseCode(500)
                                    .build());
    invalidResourceDataList.add(InvalidResourceData.builder()
                                    .path("monitoredService\\sources\\healthSources\\spec")
                                    .property("connectorRef")
                                    .replacementValue("")
                                    .expectedResponseCode(500)
                                    .build());
    for (InvalidResourceData invalidResourceData : invalidResourceDataList) {
      String msJson = InvalidResourceData.replace(monitoredServiceYaml, invalidResourceData);
      String msYaml = convertToYaml(msJson);
      Response response = RESOURCES.client()
                              .target("http://localhost:9998/monitored-service/yaml")
                              .queryParam("accountId", builderFactory.getContext().getAccountId())
                              .queryParam("orgIdentifier", builderFactory.getContext().getOrgIdentifier())
                              .queryParam("projectIdentifier", builderFactory.getContext().getProjectIdentifier())
                              .request(MediaType.APPLICATION_JSON_TYPE)
                              .post(Entity.text(msYaml));
      assertThat(response.getStatus()).isEqualTo(invalidResourceData.getExpectedResponseCode());
    }
  }

  @Test
  @Owner(developers = KARAN_SARASWAT)
  @Category(UnitTests.class)
  public void monitoredServicePrometheusTemplateValidation() throws IOException {
    String monitoredServiceYaml = getResource("monitoredservice/monitored-service-prometheus-validation.yaml");
    List<InvalidResourceData> invalidResourceDataList = new ArrayList<>();
    invalidResourceDataList.add(InvalidResourceData.builder()
                                    .path("monitoredService\\sources\\healthSources\\spec\\metricDefinitions")
                                    .property("metricName")
                                    .replacementValue(null)
                                    .expectedResponseCode(500)
                                    .build());
    for (InvalidResourceData invalidResourceData : invalidResourceDataList) {
      String msJson = InvalidResourceData.replace(monitoredServiceYaml, invalidResourceData);
      String msYaml = convertToYaml(msJson);
      Response response = RESOURCES.client()
                              .target("http://localhost:9998/monitored-service/yaml")
                              .queryParam("accountId", builderFactory.getContext().getAccountId())
                              .queryParam("orgIdentifier", builderFactory.getContext().getOrgIdentifier())
                              .queryParam("projectIdentifier", builderFactory.getContext().getProjectIdentifier())
                              .request(MediaType.APPLICATION_JSON_TYPE)
                              .post(Entity.text(msYaml));
      assertThat(response.getStatus()).isEqualTo(invalidResourceData.getExpectedResponseCode());
    }
  }

  @Test
  @Owner(developers = KARAN_SARASWAT)
  @Category(UnitTests.class)
  public void monitoredServiceNewRelicTemplateValidation() throws IOException {
    String monitoredServiceYaml = getResource("monitoredservice/monitored-service-newrelic-validation.yaml");
    List<InvalidResourceData> invalidResourceDataList = new ArrayList<>();
    invalidResourceDataList.add(InvalidResourceData.builder()
                                    .path("monitoredService\\sources\\healthSources\\spec")
                                    .property("applicationName")
                                    .replacementValue(null)
                                    .expectedResponseCode(500)
                                    .build());
    invalidResourceDataList.add(InvalidResourceData.builder()
                                    .path("monitoredService\\sources\\healthSources\\spec")
                                    .property("applicationId")
                                    .replacementValue(null)
                                    .expectedResponseCode(500)
                                    .build());
    invalidResourceDataList.add(InvalidResourceData.builder()
                                    .path("monitoredService\\sources\\healthSources\\spec")
                                    .property("applicationId")
                                    .replacementValue("")
                                    .expectedResponseCode(500)
                                    .build());
    //    invalidResourceDataList.add(
    //            InvalidResourceData.builder().path("monitoredService\\sources\\healthSources\\spec\\newRelicMetricDefinitions\\responseMapping").property("metricValueJsonPath").replacementValue(null).expectedResponseCode(500).build());
    //    invalidResourceDataList.add(
    //            InvalidResourceData.builder().path("monitoredService\\sources\\healthSources\\spec\\newRelicMetricDefinitions\\responseMapping").property("timestampJsonPath").replacementValue(null).expectedResponseCode(500).build());
    for (InvalidResourceData invalidResourceData : invalidResourceDataList) {
      String msJson = InvalidResourceData.replace(monitoredServiceYaml, invalidResourceData);
      String msYaml = convertToYaml(msJson);
      Response response = RESOURCES.client()
                              .target("http://localhost:9998/monitored-service/yaml")
                              .queryParam("accountId", builderFactory.getContext().getAccountId())
                              .queryParam("orgIdentifier", builderFactory.getContext().getOrgIdentifier())
                              .queryParam("projectIdentifier", builderFactory.getContext().getProjectIdentifier())
                              .request(MediaType.APPLICATION_JSON_TYPE)
                              .post(Entity.text(msYaml));
      assertThat(response.getStatus()).isEqualTo(invalidResourceData.getExpectedResponseCode());
    }
  }

  @Test
  @Owner(developers = KARAN_SARASWAT)
  @Category(UnitTests.class)
  public void monitoredServiceDatadogLogsTemplateValidation() throws IOException {
    String monitoredServiceYaml = getResource("monitoredservice/monitored-service-datadog-logs-validation.yaml");
    List<InvalidResourceData> invalidResourceDataList = new ArrayList<>();
    invalidResourceDataList.add(InvalidResourceData.builder()
                                    .path("monitoredService\\sources\\healthSources\\spec")
                                    .property("queries")
                                    .replacementValue(null)
                                    .expectedResponseCode(500)
                                    .build());
    invalidResourceDataList.add(InvalidResourceData.builder()
                                    .path("monitoredService\\sources\\healthSources\\spec\\queries")
                                    .property("query")
                                    .replacementValue(null)
                                    .expectedResponseCode(500)
                                    .build());
    invalidResourceDataList.add(InvalidResourceData.builder()
                                    .path("monitoredService\\sources\\healthSources\\spec\\queries")
                                    .property("serviceInstanceIdentifier")
                                    .replacementValue(null)
                                    .expectedResponseCode(500)
                                    .build());
    invalidResourceDataList.add(InvalidResourceData.builder()
                                    .path("monitoredService\\sources\\healthSources\\spec\\queries")
                                    .property("indexes")
                                    .replacementValue(null)
                                    .expectedResponseCode(500)
                                    .build());
    invalidResourceDataList.add(InvalidResourceData.builder()
                                    .path("monitoredService\\sources\\healthSources\\spec")
                                    .property("connectorRef")
                                    .replacementValue(null)
                                    .expectedResponseCode(500)
                                    .build());
    invalidResourceDataList.add(InvalidResourceData.builder()
                                    .path("monitoredService\\sources\\healthSources\\spec")
                                    .property("connectorRef")
                                    .replacementValue("")
                                    .expectedResponseCode(500)
                                    .build());
    for (InvalidResourceData invalidResourceData : invalidResourceDataList) {
      String msJson = InvalidResourceData.replace(monitoredServiceYaml, invalidResourceData);
      String msYaml = convertToYaml(msJson);
      Response response = RESOURCES.client()
                              .target("http://localhost:9998/monitored-service/yaml")
                              .queryParam("accountId", builderFactory.getContext().getAccountId())
                              .queryParam("orgIdentifier", builderFactory.getContext().getOrgIdentifier())
                              .queryParam("projectIdentifier", builderFactory.getContext().getProjectIdentifier())
                              .request(MediaType.APPLICATION_JSON_TYPE)
                              .post(Entity.text(msYaml));
      assertThat(response.getStatus()).isEqualTo(invalidResourceData.getExpectedResponseCode());
    }
  }

  @Test
  @Owner(developers = KARAN_SARASWAT)
  @Category(UnitTests.class)
  public void monitoredServiceDynatraceTemplateValidation() throws IOException {
    String monitoredServiceYaml = getResource("monitoredservice/monitored-service-dynatrace-validation.yaml");
    List<InvalidResourceData> invalidResourceDataList = new ArrayList<>();
    invalidResourceDataList.add(InvalidResourceData.builder()
                                    .path("monitoredService\\sources\\healthSources\\spec")
                                    .property("serviceId")
                                    .replacementValue(null)
                                    .expectedResponseCode(500)
                                    .build());
    invalidResourceDataList.add(InvalidResourceData.builder()
                                    .path("monitoredService\\sources\\healthSources\\spec")
                                    .property("serviceName")
                                    .replacementValue(null)
                                    .expectedResponseCode(500)
                                    .build());
    //    invalidResourceDataList.add(
    //            InvalidResourceData.builder().path("monitoredService\\sources\\healthSources\\spec").property("metricPacks").replacementValue(null).expectedResponseCode(500).build());
    //    invalidResourceDataList.add(
    //            InvalidResourceData.builder().path("monitoredService\\sources\\healthSources\\spec\\metricPacks").property("identifier").replacementValue("random").expectedResponseCode(500).build());
    //    invalidResourceDataList.add(
    //            InvalidResourceData.builder().path("monitoredService\\sources\\healthSources").property("serviceMethodIds").replacementValue(null).expectedResponseCode(500).build());
    invalidResourceDataList.add(InvalidResourceData.builder()
                                    .path("monitoredService\\sources\\healthSources\\spec")
                                    .property("connectorRef")
                                    .replacementValue("")
                                    .expectedResponseCode(500)
                                    .build());
    for (InvalidResourceData invalidResourceData : invalidResourceDataList) {
      String msJson = InvalidResourceData.replace(monitoredServiceYaml, invalidResourceData);
      String msYaml = convertToYaml(msJson);
      Response response = RESOURCES.client()
                              .target("http://localhost:9998/monitored-service/yaml")
                              .queryParam("accountId", builderFactory.getContext().getAccountId())
                              .queryParam("orgIdentifier", builderFactory.getContext().getOrgIdentifier())
                              .queryParam("projectIdentifier", builderFactory.getContext().getProjectIdentifier())
                              .request(MediaType.APPLICATION_JSON_TYPE)
                              .post(Entity.text(msYaml));
      assertThat(response.getStatus()).isEqualTo(invalidResourceData.getExpectedResponseCode());
    }
  }

  @Test
  @Owner(developers = KARAN_SARASWAT)
  @Category(UnitTests.class)
  public void monitoredServiceGCPLogsTemplateValidation() throws IOException {
    String monitoredServiceYaml = getResource("monitoredservice/monitored-service-gcp-logs-validation.yaml");
    List<InvalidResourceData> invalidResourceDataList = new ArrayList<>();
    invalidResourceDataList.add(InvalidResourceData.builder()
                                    .path("monitoredService\\sources\\healthSources\\spec")
                                    .property("queries")
                                    .replacementValue(null)
                                    .expectedResponseCode(500)
                                    .build());
    invalidResourceDataList.add(InvalidResourceData.builder()
                                    .path("monitoredService\\sources\\healthSources\\spec\\queries")
                                    .property("query")
                                    .replacementValue(null)
                                    .expectedResponseCode(500)
                                    .build());
    invalidResourceDataList.add(InvalidResourceData.builder()
                                    .path("monitoredService\\sources\\healthSources\\spec\\queries")
                                    .property("serviceInstanceIdentifier")
                                    .replacementValue(null)
                                    .expectedResponseCode(500)
                                    .build());
    //    invalidResourceDataList.add(
    //            InvalidResourceData.builder().path("monitoredService\\sources\\healthSources\\spec\\queries").property("identifier").replacementValue(null).expectedResponseCode(500).build());
    for (InvalidResourceData invalidResourceData : invalidResourceDataList) {
      String msJson = InvalidResourceData.replace(monitoredServiceYaml, invalidResourceData);
      String msYaml = convertToYaml(msJson);
      Response response = RESOURCES.client()
                              .target("http://localhost:9998/monitored-service/yaml")
                              .queryParam("accountId", builderFactory.getContext().getAccountId())
                              .queryParam("orgIdentifier", builderFactory.getContext().getOrgIdentifier())
                              .queryParam("projectIdentifier", builderFactory.getContext().getProjectIdentifier())
                              .request(MediaType.APPLICATION_JSON_TYPE)
                              .post(Entity.text(msYaml));
      assertThat(response.getStatus()).isEqualTo(invalidResourceData.getExpectedResponseCode());
    }
  }

  @Test
  @Owner(developers = KARAN_SARASWAT)
  @Category(UnitTests.class)
  public void monitoredServiceGCPMetricsTemplateValidation() throws IOException {
    String monitoredServiceYaml = getResource("monitoredservice/monitored-service-gcp-metrics-validation.yaml");
    List<InvalidResourceData> invalidResourceDataList = new ArrayList<>();
    invalidResourceDataList.add(InvalidResourceData.builder()
                                    .path("monitoredService\\sources\\healthSources\\spec\\metricDefinitions")
                                    .property("jsonMetricDefinition")
                                    .replacementValue(null)
                                    .expectedResponseCode(500)
                                    .build());
    invalidResourceDataList.add(InvalidResourceData.builder()
                                    .path("monitoredService\\sources\\healthSources\\spec\\metricDefinitions")
                                    .property("serviceInstanceField")
                                    .replacementValue(null)
                                    .expectedResponseCode(500)
                                    .build());
    for (InvalidResourceData invalidResourceData : invalidResourceDataList) {
      String msJson = InvalidResourceData.replace(monitoredServiceYaml, invalidResourceData);
      String msYaml = convertToYaml(msJson);
      Response response = RESOURCES.client()
                              .target("http://localhost:9998/monitored-service/yaml")
                              .queryParam("accountId", builderFactory.getContext().getAccountId())
                              .queryParam("orgIdentifier", builderFactory.getContext().getOrgIdentifier())
                              .queryParam("projectIdentifier", builderFactory.getContext().getProjectIdentifier())
                              .request(MediaType.APPLICATION_JSON_TYPE)
                              .post(Entity.text(msYaml));
      assertThat(response.getStatus()).isEqualTo(invalidResourceData.getExpectedResponseCode());
    }
  }

  @Test
  @Owner(developers = ARPITJ)
  @Category(UnitTests.class)
  public void monitoredServiceElasticSearchValidation() throws IOException {
    String monitoredServiceYaml = getResource("monitoredservice/monitored-service-elasticsearch-validation.yaml");
    List<InvalidResourceData> invalidResourceDataList = new ArrayList<>();
    invalidResourceDataList.add(InvalidResourceData.builder()
                                    .path("monitoredService\\sources\\healthSources\\spec")
                                    .property("queries")
                                    .replacementValue(null)
                                    .expectedResponseCode(500)
                                    .build());
    invalidResourceDataList.add(InvalidResourceData.builder()
                                    .path("monitoredService\\sources\\healthSources\\spec\\queries")
                                    .property("query")
                                    .replacementValue(null)
                                    .expectedResponseCode(500)
                                    .build());
    invalidResourceDataList.add(InvalidResourceData.builder()
                                    .path("monitoredService\\sources\\healthSources\\spec\\queries")
                                    .property("serviceInstanceIdentifier")
                                    .replacementValue(null)
                                    .expectedResponseCode(500)
                                    .build());
    invalidResourceDataList.add(InvalidResourceData.builder()
                                    .path("monitoredService\\sources\\healthSources\\spec\\queries")
                                    .property("index")
                                    .replacementValue(null)
                                    .expectedResponseCode(500)
                                    .build());
    invalidResourceDataList.add(InvalidResourceData.builder()
                                    .path("monitoredService\\sources\\healthSources\\spec\\queries")
                                    .property("index")
                                    .replacementValue("")
                                    .expectedResponseCode(500)
                                    .build());
    invalidResourceDataList.add(InvalidResourceData.builder()
                                    .path("monitoredService\\sources\\healthSources\\spec\\queries")
                                    .property("name")
                                    .replacementValue(null)
                                    .expectedResponseCode(500)
                                    .build());
    invalidResourceDataList.add(InvalidResourceData.builder()
                                    .path("monitoredService\\sources\\healthSources\\spec\\queries")
                                    .property("name")
                                    .replacementValue("")
                                    .expectedResponseCode(500)
                                    .build());
    invalidResourceDataList.add(InvalidResourceData.builder()
                                    .path("monitoredService\\sources\\healthSources\\spec")
                                    .property("connectorRef")
                                    .replacementValue(null)
                                    .expectedResponseCode(500)
                                    .build());
    invalidResourceDataList.add(InvalidResourceData.builder()
                                    .path("monitoredService\\sources\\healthSources\\spec")
                                    .property("feature")
                                    .replacementValue(null)
                                    .expectedResponseCode(500)
                                    .build());
    invalidResourceDataList.add(InvalidResourceData.builder()
                                    .path("monitoredService\\sources\\healthSources\\spec")
                                    .property("feature")
                                    .replacementValue("")
                                    .expectedResponseCode(500)
                                    .build());
    invalidResourceDataList.add(InvalidResourceData.builder()
                                    .path("monitoredService\\sources\\healthSources\\spec")
                                    .property("connectorRef")
                                    .replacementValue("")
                                    .expectedResponseCode(500)
                                    .build());
    for (InvalidResourceData invalidResourceData : invalidResourceDataList) {
      String msJson = InvalidResourceData.replace(monitoredServiceYaml, invalidResourceData);
      String msYaml = convertToYaml(msJson);
      Response response = RESOURCES.client()
                              .target("http://localhost:9998/monitored-service/yaml")
                              .queryParam("accountId", builderFactory.getContext().getAccountId())
                              .queryParam("orgIdentifier", builderFactory.getContext().getOrgIdentifier())
                              .queryParam("projectIdentifier", builderFactory.getContext().getProjectIdentifier())
                              .request(MediaType.APPLICATION_JSON_TYPE)
                              .post(Entity.text(msYaml));
      assertThat(response.getStatus()).isEqualTo(invalidResourceData.getExpectedResponseCode());
    }
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
  @Owner(developers = KARAN_SARASWAT)
  @Category(UnitTests.class)
  public void testListMonitoredServicePlatform_listAPIAcrossHarnessApp() {
    monitoredServiceService.createDefault(builderFactory.getProjectParams(), "service1", "env1");
    monitoredServiceService.createDefault(builderFactory.getProjectParams(), "service2", "env2");
    monitoredServiceDTO = builderFactory.monitoredServiceDTOBuilder().build();
    monitoredServiceDTO.setType(MonitoredServiceType.INFRASTRUCTURE);
    monitoredServiceDTO.setIdentifier("service3");
    monitoredServiceDTO.setServiceRef("service3");
    monitoredServiceDTO.setEnvironmentRef(null);
    monitoredServiceDTO.setEnvironmentRefList(List.of("env1", "env2"));
    monitoredServiceService.create(builderFactory.getProjectParams().getAccountIdentifier(), monitoredServiceDTO);

    WebTarget webTarget = RESOURCES.client()
                              .target("http://localhost:9998/monitored-service/platform/list")
                              .queryParam("accountId", builderFactory.getContext().getAccountId())
                              .queryParam("orgIdentifier", builderFactory.getContext().getOrgIdentifier())
                              .queryParam("projectIdentifier", builderFactory.getContext().getProjectIdentifier())
                              .queryParam("offset", 0)
                              .queryParam("pageSize", 10);
    webTarget = webTarget.queryParam("monitoredServiceType", "Application");
    Response response = webTarget.request(MediaType.APPLICATION_JSON_TYPE).get();
    assertThat(response.getStatus()).isEqualTo(200);
    assertThat(response.readEntity(String.class)).contains("\"totalItems\":3");
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
  @Owner(developers = VARSHA_LALWANI)
  @Category(UnitTests.class)
  public void testGetMonitoredService_WithProjectParamsIncorrect() {
    monitoredServiceService.createDefault(builderFactory.getProjectParams(), "service1", "env1");

    Response response = RESOURCES.client()
                            .target("http://localhost:9998/monitored-service/service1_env1")
                            .queryParam("accountId", builderFactory.getContext().getAccountId())
                            .queryParam("orgIdentifier", builderFactory.getContext().getOrgIdentifier())
                            .request(MediaType.APPLICATION_JSON_TYPE)
                            .get();
    assertThat(response.getStatus()).isEqualTo(400);
    assertThat(response.readEntity(String.class))
        .contains("\"field\":\"projectIdentifier\",\"message\":\"must not be null\"");
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
        IntStream.range(0, 1)
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

  @Test
  @Owner(developers = DEEPAK_CHHIKARA)
  @Category(UnitTests.class)
  public void testCreate_allHealthSources() throws IOException {
    String[] healthSources = {"monitoredservice/healthsources/app-dynamics.yaml"};
    for (String file : healthSources) {
      String monitoredServiceYaml = getResource(file);
      monitoredServiceYaml =
          monitoredServiceYaml.replace("$orgIdentifier", builderFactory.getContext().getOrgIdentifier());
      monitoredServiceYaml =
          monitoredServiceYaml.replace("$projectIdentifier", builderFactory.getContext().getProjectIdentifier());
      monitoredServiceYaml = monitoredServiceYaml.replace("$enabled", "false");
      Response response = RESOURCES.client()
                              .target("http://localhost:9998/monitored-service/")
                              .queryParam("accountId", builderFactory.getContext().getAccountId())
                              .request(MediaType.APPLICATION_JSON_TYPE)
                              .post(Entity.json(convertToJson(monitoredServiceYaml)));
      assertThat(response.getStatus()).isEqualTo(200);
    }
  }

  @Test
  @Owner(developers = DEEPAK_CHHIKARA)
  @Category(UnitTests.class)
  public void testCreate_WithDefaultChangeSource() throws IOException {
    String[] healthSources = {"monitoredservice/healthsources/app-dynamics-with-default-change-source.yaml"};
    for (String file : healthSources) {
      String monitoredServiceYaml = getResource(file);
      monitoredServiceYaml =
          monitoredServiceYaml.replace("$orgIdentifier", builderFactory.getContext().getOrgIdentifier());
      monitoredServiceYaml =
          monitoredServiceYaml.replace("$projectIdentifier", builderFactory.getContext().getProjectIdentifier());
      monitoredServiceYaml = monitoredServiceYaml.replace("$enabled", "false");
      Response response = RESOURCES.client()
                              .target("http://localhost:9998/monitored-service/")
                              .queryParam("accountId", builderFactory.getContext().getAccountId())
                              .request(MediaType.APPLICATION_JSON_TYPE)
                              .post(Entity.json(convertToJson(monitoredServiceYaml)));
      assertThat(response.getStatus()).isEqualTo(200);
    }
  }

  @Test
  @Owner(developers = VARSHA_LALWANI)
  @Category(UnitTests.class)
  public void testGetOverallHealthScore_withNoDurationAndStartTime() {
    WebTarget webTarget = RESOURCES.client()
                              .target("http://localhost:9998/monitored-service/" + monitoredServiceDTO.getIdentifier()
                                  + "/overall-health-score")
                              .queryParam("accountId", builderFactory.getContext().getAccountId())
                              .queryParam("orgIdentifier", builderFactory.getContext().getOrgIdentifier())
                              .queryParam("projectIdentifier", builderFactory.getContext().getProjectIdentifier())
                              .queryParam("endTime", 14300000000L);
    Response response = webTarget.request(MediaType.APPLICATION_JSON_TYPE).get();
    assertThat(response.getStatus()).isEqualTo(500);
    assertThat(response.readEntity(String.class)).contains("One of duration or start time should be present.");
  }

  @Test
  @Owner(developers = VARSHA_LALWANI)
  @Category(UnitTests.class)
  public void testGetOverallHealthScore_withBothDurationAndStartTimeInvalid() {
    WebTarget webTarget = RESOURCES.client()
                              .target("http://localhost:9998/monitored-service/" + monitoredServiceDTO.getIdentifier()
                                  + "/overall-health-score")
                              .queryParam("accountId", builderFactory.getContext().getAccountId())
                              .queryParam("orgIdentifier", builderFactory.getContext().getOrgIdentifier())
                              .queryParam("projectIdentifier", builderFactory.getContext().getProjectIdentifier())
                              .queryParam("duration", DurationDTO.FOUR_HOURS)
                              .queryParam("endTime", 14300000000L)
                              .queryParam("startTime", 14285500000L);
    Response response = webTarget.request(MediaType.APPLICATION_JSON_TYPE).get();
    assertThat(response.getStatus()).isEqualTo(500);
    assertThat(response.readEntity(String.class))
        .contains(
            "Duration field value and duration from the start time and endTime is different. Make sure you pass either one of them or the duration is same from both.");
  }

  @Test
  @Owner(developers = VARSHA_LALWANI)
  @Category(UnitTests.class)
  public void testGetOverallHealthScore_withBothLessThanFiveMinuteDifference() {
    WebTarget webTarget = RESOURCES.client()
                              .target("http://localhost:9998/monitored-service/" + monitoredServiceDTO.getIdentifier()
                                  + "/overall-health-score")
                              .queryParam("accountId", builderFactory.getContext().getAccountId())
                              .queryParam("orgIdentifier", builderFactory.getContext().getOrgIdentifier())
                              .queryParam("projectIdentifier", builderFactory.getContext().getProjectIdentifier())
                              .queryParam("endTime", 14300000000L)
                              .queryParam("startTime", 14299800000L);
    Response response = webTarget.request(MediaType.APPLICATION_JSON_TYPE).get();
    assertThat(response.getStatus()).isEqualTo(500);
    assertThat(response.readEntity(String.class))
        .contains("Start time and endTime should have at least 5 minutes difference");
  }
  @Test
  @Owner(developers = VARSHA_LALWANI)
  @Category(UnitTests.class)
  public void testGetOverallHealthScore_withBothDurationAndStartTimeValid() {
    WebTarget webTarget = RESOURCES.client()
                              .target("http://localhost:9998/monitored-service/" + monitoredServiceDTO.getIdentifier()
                                  + "/overall-health-score")
                              .queryParam("accountId", builderFactory.getContext().getAccountId())
                              .queryParam("orgIdentifier", builderFactory.getContext().getOrgIdentifier())
                              .queryParam("projectIdentifier", builderFactory.getContext().getProjectIdentifier())
                              .queryParam("duration", DurationDTO.FOUR_HOURS)
                              .queryParam("endTime", 14300000000L);
    Response response = webTarget.request(MediaType.APPLICATION_JSON_TYPE).get();
    assertThat(response.getStatus()).isEqualTo(200);
  }

  private static String convertToYaml(String jsonString) throws JsonProcessingException {
    JsonNode jsonNodeTree = new ObjectMapper().readTree(jsonString);
    String jsonAsYaml = new YAMLMapper(new YAMLFactory().enable(YAMLGenerator.Feature.MINIMIZE_QUOTES))
                            .writeValueAsString(jsonNodeTree);
    return jsonAsYaml;
  }
}
