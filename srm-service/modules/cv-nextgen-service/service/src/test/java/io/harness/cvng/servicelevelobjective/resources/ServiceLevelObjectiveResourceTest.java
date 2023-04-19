/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.servicelevelobjective.resources;

import static io.harness.rule.OwnerRule.DEEPAK_CHHIKARA;
import static io.harness.rule.OwnerRule.KAPIL;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CvNextGenTestBase;
import io.harness.category.element.UnitTests;
import io.harness.cvng.BuilderFactory;
import io.harness.cvng.CVNGTestConstants;
import io.harness.cvng.beans.cvnglog.CVNGLogDTO;
import io.harness.cvng.core.beans.monitoredService.MonitoredServiceDTO;
import io.harness.cvng.core.services.api.CVNGLogService;
import io.harness.cvng.core.services.api.MetricPackService;
import io.harness.cvng.core.services.api.VerificationTaskService;
import io.harness.cvng.core.services.api.monitoredService.MonitoredServiceService;
import io.harness.cvng.notification.beans.NotificationRuleDTO;
import io.harness.cvng.notification.beans.NotificationRuleRefDTO;
import io.harness.cvng.notification.beans.NotificationRuleResponse;
import io.harness.cvng.notification.beans.NotificationRuleType;
import io.harness.cvng.notification.services.api.NotificationRuleService;
import io.harness.cvng.servicelevelobjective.beans.ServiceLevelIndicatorDTO;
import io.harness.cvng.servicelevelobjective.beans.ServiceLevelObjectiveV2DTO;
import io.harness.cvng.servicelevelobjective.beans.slospec.SimpleServiceLevelObjectiveSpec;
import io.harness.cvng.servicelevelobjective.entities.ServiceLevelIndicator;
import io.harness.cvng.servicelevelobjective.services.api.ServiceLevelIndicatorService;
import io.harness.cvng.servicelevelobjective.services.api.ServiceLevelObjectiveV2Service;
import io.harness.rule.Owner;
import io.harness.rule.ResourceTestRule;

import com.google.inject.Inject;
import com.google.inject.Injector;
import java.io.IOException;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.yaml.snakeyaml.Yaml;

public class ServiceLevelObjectiveResourceTest extends CvNextGenTestBase {
  @Inject ServiceLevelObjectiveV2Service serviceLevelObjectiveV2Service;
  @Inject VerificationTaskService verificationTaskService;
  @Inject ServiceLevelIndicatorService serviceLevelIndicatorService;
  @Inject private Injector injector;
  @Inject private MonitoredServiceService monitoredServiceService;
  @Inject private MetricPackService metricPackService;
  @Inject CVNGLogService cvngLogService;
  @Inject NotificationRuleService notificationRuleService;
  private MonitoredServiceDTO monitoredServiceDTO;
  private BuilderFactory builderFactory;
  private static final ServiceLevelObjectiveResource serviceLevelObjectiveResource =
      new ServiceLevelObjectiveResource();

  @ClassRule
  public static final ResourceTestRule RESOURCES =
      ResourceTestRule.builder().addResource(serviceLevelObjectiveResource).build();

  private static final ServiceLevelObjectiveV2Resource serviceLevelObjectiveV2Resource =
      new ServiceLevelObjectiveV2Resource();

  @ClassRule
  public static final ResourceTestRule V2_RESOURCES =
      ResourceTestRule.builder().addResource(serviceLevelObjectiveV2Resource).build();
  @Before
  public void setup() {
    injector.injectMembers(serviceLevelObjectiveResource);
    injector.injectMembers(serviceLevelObjectiveV2Resource);

    builderFactory = BuilderFactory.getDefault();
    metricPackService.createDefaultMetricPackAndThresholds(builderFactory.getContext().getAccountId(),
        builderFactory.getContext().getOrgIdentifier(), builderFactory.getContext().getProjectIdentifier());
    monitoredServiceDTO = builderFactory.monitoredServiceDTOBuilder().build();
    monitoredServiceService.create(builderFactory.getContext().getAccountId(), monitoredServiceDTO);
  }

  @Test
  @Owner(developers = KAPIL)
  @Category(UnitTests.class)
  public void testGetServiceLevelObjectiveLogs_withNoSLO() throws IOException {
    Instant startTime = CVNGTestConstants.FIXED_TIME_FOR_TESTS.instant().minusSeconds(5);
    Instant endTime = CVNGTestConstants.FIXED_TIME_FOR_TESTS.instant();
    WebTarget webTarget = RESOURCES.client()
                              .target("http://localhost:9998/slo/"
                                  + "SLOIdentifier"
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
    assertThat(response.readEntity(String.class)).contains("SLO with identifier SLOIdentifier not found.");
  }

  @Test
  @Owner(developers = KAPIL)
  @Category(UnitTests.class)
  public void testGetServiceLevelObjectiveLogs() throws IOException {
    Instant startTime = CVNGTestConstants.FIXED_TIME_FOR_TESTS.instant().minusSeconds(5);
    Instant endTime = CVNGTestConstants.FIXED_TIME_FOR_TESTS.instant();
    ServiceLevelObjectiveV2DTO sloDTO = builderFactory.getSimpleServiceLevelObjectiveV2DTOBuilder().build();
    serviceLevelObjectiveV2Service.create(builderFactory.getContext().getProjectParams(), sloDTO);
    List<String> serviceLevelIndicators = ((SimpleServiceLevelObjectiveSpec) sloDTO.getSpec())
                                              .getServiceLevelIndicators()
                                              .stream()
                                              .map(ServiceLevelIndicatorDTO::getIdentifier)
                                              .collect(Collectors.toList());
    List<String> sliIds =
        serviceLevelIndicatorService.getEntities(builderFactory.getContext().getProjectParams(), serviceLevelIndicators)
            .stream()
            .map(ServiceLevelIndicator::getUuid)
            .collect(Collectors.toList());
    List<String> verificationTaskIds =
        verificationTaskService.getSLIVerificationTaskIds(builderFactory.getContext().getAccountId(), sliIds);
    List<CVNGLogDTO> cvngLogDTOs =
        Arrays.asList(builderFactory.executionLogDTOBuilder().traceableId(verificationTaskIds.get(0)).build());
    cvngLogService.save(cvngLogDTOs);

    WebTarget webTarget = RESOURCES.client()
                              .target("http://localhost:9998/slo/" + sloDTO.getIdentifier() + "/logs")
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
  public void testGetServiceLevelObjectiveLogs_withIncorrectLogType() throws IOException {
    Instant startTime = CVNGTestConstants.FIXED_TIME_FOR_TESTS.instant().minusSeconds(5);
    Instant endTime = CVNGTestConstants.FIXED_TIME_FOR_TESTS.instant();
    ServiceLevelObjectiveV2DTO sloDTO = builderFactory.getSimpleServiceLevelObjectiveV2DTOBuilder().build();
    serviceLevelObjectiveV2Service.create(builderFactory.getContext().getProjectParams(), sloDTO);
    WebTarget webTarget = RESOURCES.client()
                              .target("http://localhost:9998/slo/" + sloDTO.getIdentifier() + "/logs")
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
  public void testCreate_withNotificationRules() throws IOException {
    NotificationRuleDTO notificationRuleDTO =
        builderFactory.getNotificationRuleDTOBuilder(NotificationRuleType.SLO).build();
    NotificationRuleResponse notificationRuleResponse =
        notificationRuleService.create(builderFactory.getProjectParams(), notificationRuleDTO);

    ServiceLevelObjectiveV2DTO sloDTO = builderFactory.getSimpleServiceLevelObjectiveV2DTOBuilder().build();
    sloDTO.setNotificationRuleRefs(
        Collections.singletonList(NotificationRuleRefDTO.builder()
                                      .notificationRuleRef(notificationRuleDTO.getIdentifier())
                                      .enabled(true)
                                      .build()));
    Response response = V2_RESOURCES.client()
                            .target("http://localhost:9998/slo/v2/")
                            .queryParam("accountId", builderFactory.getContext().getAccountId())
                            .request(MediaType.APPLICATION_JSON_TYPE)
                            .post(Entity.json(sloDTO));
    assertThat(response.getStatus()).isEqualTo(200);
    assertThat(response.readEntity(String.class))
        .contains("\"notificationRuleRefs\":[{\"notificationRuleRef\":\"rule\",\"enabled\":true}]");
  }

  @Test
  @Owner(developers = KAPIL)
  @Category(UnitTests.class)
  public void testUpdateSLOData_withNotificationRules() throws IOException {
    NotificationRuleDTO notificationRuleDTO =
        builderFactory.getNotificationRuleDTOBuilder(NotificationRuleType.SLO).build();
    NotificationRuleResponse notificationRuleResponse =
        notificationRuleService.create(builderFactory.getProjectParams(), notificationRuleDTO);

    ServiceLevelObjectiveV2DTO sloDTO = builderFactory.getSimpleServiceLevelObjectiveV2DTOBuilder().build();
    sloDTO.setIdentifier("slo");
    sloDTO.setNotificationRuleRefs(
        Collections.singletonList(NotificationRuleRefDTO.builder()
                                      .notificationRuleRef(notificationRuleDTO.getIdentifier())
                                      .enabled(false)
                                      .build()));
    Response response = V2_RESOURCES.client()
                            .target("http://localhost:9998/slo/v2/")
                            .queryParam("accountId", builderFactory.getContext().getAccountId())
                            .request(MediaType.APPLICATION_JSON_TYPE)
                            .post(Entity.json(sloDTO));
    assertThat(response.getStatus()).isEqualTo(200);
    assertThat(response.readEntity(String.class))
        .contains("\"notificationRuleRefs\":[{\"notificationRuleRef\":\"rule\",\"enabled\":false}]");

    sloDTO.setNotificationRuleRefs(
        Collections.singletonList(NotificationRuleRefDTO.builder()
                                      .notificationRuleRef(notificationRuleDTO.getIdentifier())
                                      .enabled(true)
                                      .build()));
    response = V2_RESOURCES.client()
                   .target("http://localhost:9998/slo/v2/"
                       + "slo")
                   .queryParam("accountId", builderFactory.getContext().getAccountId())
                   .queryParam("orgIdentifier", builderFactory.getContext().getOrgIdentifier())
                   .queryParam("projectIdentifier", builderFactory.getContext().getProjectIdentifier())
                   .request(MediaType.APPLICATION_JSON_TYPE)
                   .put(Entity.json(sloDTO));
    assertThat(response.getStatus()).isEqualTo(200);
    assertThat(response.readEntity(String.class))
        .contains("\"notificationRuleRefs\":[{\"notificationRuleRef\":\"rule\",\"enabled\":true}]");
  }

  @Test
  @Owner(developers = KAPIL)
  @Category(UnitTests.class)
  public void testGetNotificationRules() throws IOException {
    NotificationRuleDTO notificationRuleDTO =
        builderFactory.getNotificationRuleDTOBuilder(NotificationRuleType.SLO).build();
    NotificationRuleResponse notificationRuleResponse =
        notificationRuleService.create(builderFactory.getProjectParams(), notificationRuleDTO);

    ServiceLevelObjectiveV2DTO sloDTO = builderFactory.getSimpleServiceLevelObjectiveV2DTOBuilder().build();
    sloDTO.setIdentifier("slo");
    sloDTO.setNotificationRuleRefs(
        Collections.singletonList(NotificationRuleRefDTO.builder()
                                      .notificationRuleRef(notificationRuleDTO.getIdentifier())
                                      .enabled(false)
                                      .build()));
    Response response = V2_RESOURCES.client()
                            .target("http://localhost:9998/slo/v2/")
                            .queryParam("accountId", builderFactory.getContext().getAccountId())
                            .request(MediaType.APPLICATION_JSON_TYPE)
                            .post(Entity.json(sloDTO));
    assertThat(response.getStatus()).isEqualTo(200);
    assertThat(response.readEntity(String.class))
        .contains("\"notificationRuleRefs\":[{\"notificationRuleRef\":\"rule\",\"enabled\":false}]");

    response = RESOURCES.client()
                   .target("http://localhost:9998/slo/"
                       + "slo"
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
                   .target("http://localhost:9998/slo/"
                       + "slo1"
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
            "\"message\":\"io.harness.exception.InvalidRequestException: SLO with identifier slo1, accountId %s, orgIdentifier %s, and projectIdentifier %s  is not present.\"",
            builderFactory.getContext().getAccountId(), builderFactory.getContext().getOrgIdentifier(),
            builderFactory.getContext().getProjectIdentifier()));
  }

  @Test
  @Owner(developers = DEEPAK_CHHIKARA)
  @Category(UnitTests.class)
  public void testCreateSLO_withEmptySLIType() throws IOException {
    String sloYaml = getYAML("slo/slo-with-empty-sli-type.yaml");

    Response createResponse = V2_RESOURCES.client()
                                  .target("http://localhost:9998/slo/v2")
                                  .queryParam("accountId", builderFactory.getContext().getAccountId())
                                  .queryParam("projectIdentifier", builderFactory.getContext().getProjectIdentifier())
                                  .queryParam("orgIdentifier", builderFactory.getContext().getOrgIdentifier())
                                  .request(MediaType.APPLICATION_JSON_TYPE)
                                  .post(Entity.json(convertToJson(sloYaml)));
    assertThat(createResponse.getStatus()).isEqualTo(200);
  }

  private static String convertToJson(String yamlString) {
    Yaml yaml = new Yaml();
    Map<String, Object> map = yaml.load(yamlString);

    JSONObject jsonObject = new JSONObject(map);
    return jsonObject.toString();
  }

  private String getYAML(String filePath) throws IOException {
    String sloYaml = getResource(filePath);
    sloYaml = sloYaml.replace("$projectIdentifier", builderFactory.getContext().getProjectIdentifier());
    sloYaml = sloYaml.replace("$orgIdentifier", builderFactory.getContext().getOrgIdentifier());
    sloYaml = sloYaml.replace("$monitoredServiceRef", monitoredServiceDTO.getIdentifier());
    sloYaml = sloYaml.replace(
        "$healthSourceRef", monitoredServiceDTO.getSources().getHealthSources().iterator().next().getIdentifier());
    return sloYaml;
  }
}
