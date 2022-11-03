/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.servicelevelobjective.resources;

import static io.harness.rule.OwnerRule.ABHIJITH;
import static io.harness.rule.OwnerRule.ARPITJ;
import static io.harness.rule.OwnerRule.KAMAL;
import static io.harness.rule.OwnerRule.KARAN_SARASWAT;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CvNextGenTestBase;
import io.harness.category.element.UnitTests;
import io.harness.cvng.BuilderFactory;
import io.harness.cvng.core.beans.monitoredService.MonitoredServiceDTO;
import io.harness.cvng.core.services.api.MetricPackService;
import io.harness.cvng.core.services.api.monitoredService.MonitoredServiceService;
import io.harness.cvng.servicelevelobjective.beans.DayOfWeek;
import io.harness.cvng.servicelevelobjective.beans.ErrorBudgetRisk;
import io.harness.cvng.servicelevelobjective.beans.SLOCalenderType;
import io.harness.cvng.servicelevelobjective.beans.SLOTargetDTO;
import io.harness.cvng.servicelevelobjective.beans.SLOTargetType;
import io.harness.cvng.servicelevelobjective.beans.ServiceLevelIndicatorType;
import io.harness.cvng.servicelevelobjective.beans.ServiceLevelObjectiveDTO;
import io.harness.cvng.servicelevelobjective.beans.ServiceLevelObjectiveV2DTO;
import io.harness.cvng.servicelevelobjective.beans.slospec.SimpleServiceLevelObjectiveSpec;
import io.harness.cvng.servicelevelobjective.beans.slotargetspec.CalenderSLOTargetSpec;
import io.harness.cvng.servicelevelobjective.beans.slotargetspec.CalenderSLOTargetSpec.WeeklyCalendarSpec;
import io.harness.cvng.servicelevelobjective.entities.SLOHealthIndicator;
import io.harness.cvng.servicelevelobjective.services.api.ServiceLevelObjectiveService;
import io.harness.cvng.servicelevelobjective.services.api.ServiceLevelObjectiveV2Service;
import io.harness.persistence.HPersistence;
import io.harness.rule.Owner;
import io.harness.rule.ResourceTestRule;

import com.google.inject.Inject;
import com.google.inject.Injector;
import java.util.List;
import javax.ws.rs.client.Entity;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class SLODashboardResourceTest extends CvNextGenTestBase {
  @Inject private Injector injector;
  @Inject private HPersistence hPersistence;
  @Inject private MonitoredServiceService monitoredServiceService;
  @Inject private ServiceLevelObjectiveService serviceLevelObjectiveService;
  @Inject private ServiceLevelObjectiveV2Service serviceLevelObjectiveV2Service;
  @Inject private MetricPackService metricPackService;

  private BuilderFactory builderFactory;
  private static SLODashboardResource sloDashboardResource = new SLODashboardResource();

  @ClassRule
  public static final ResourceTestRule RESOURCES = ResourceTestRule.builder().addResource(sloDashboardResource).build();

  @Before
  public void setup() {
    injector.injectMembers(sloDashboardResource);
    builderFactory = BuilderFactory.getDefault();
    metricPackService.createDefaultMetricPackAndThresholds(builderFactory.getContext().getAccountId(),
        builderFactory.getContext().getOrgIdentifier(), builderFactory.getContext().getProjectIdentifier());
    MonitoredServiceDTO monitoredServiceDTO = builderFactory.monitoredServiceDTOBuilder().build();
    monitoredServiceService.create(builderFactory.getContext().getAccountId(), monitoredServiceDTO);
  }

  @Test
  @Owner(developers = KAMAL)
  @Category(UnitTests.class)
  public void testGetSLODashboardWidgets_emptyResponse() {
    Response response = RESOURCES.client()
                            .target("http://localhost:9998/slo-dashboard/widgets")
                            .queryParam("accountId", builderFactory.getContext().getAccountId())
                            .queryParam("orgIdentifier", builderFactory.getContext().getOrgIdentifier())
                            .queryParam("projectIdentifier", builderFactory.getContext().getProjectIdentifier())
                            .request(MediaType.APPLICATION_JSON_TYPE)
                            .get();
    assertThat(response.getStatus()).isEqualTo(200);
  }

  @Test
  @Owner(developers = KARAN_SARASWAT)
  @Category(UnitTests.class)
  public void testGetSLODashboardWidgetsList_emptyResponse() {
    Response response = RESOURCES.client()
                            .target("http://localhost:9998/slo-dashboard/widgets/list")
                            .queryParam("accountId", builderFactory.getContext().getAccountId())
                            .queryParam("orgIdentifier", builderFactory.getContext().getOrgIdentifier())
                            .queryParam("projectIdentifier", builderFactory.getContext().getProjectIdentifier())
                            .request(MediaType.APPLICATION_JSON_TYPE)
                            .get();
    assertThat(response.getStatus()).isEqualTo(200);
  }

  @Test
  @Owner(developers = KARAN_SARASWAT)
  @Category(UnitTests.class)
  public void testGetSLODashboardWidgetsList() {
    MonitoredServiceDTO monitoredServiceDTO = builderFactory.monitoredServiceDTOBuilder().build();

    ServiceLevelObjectiveV2DTO sloDTO1 = builderFactory.getSimpleServiceLevelObjectiveV2DTOBuilder()
                                             .identifier("id10")
                                             .userJourneyRefs(List.of("uj10"))
                                             .build();
    SimpleServiceLevelObjectiveSpec simpleServiceLevelObjectiveSpec =
        (SimpleServiceLevelObjectiveSpec) sloDTO1.getSpec();
    simpleServiceLevelObjectiveSpec.setServiceLevelIndicatorType(ServiceLevelIndicatorType.AVAILABILITY);
    sloDTO1.setSpec(simpleServiceLevelObjectiveSpec);
    serviceLevelObjectiveV2Service.create(builderFactory.getProjectParams(), sloDTO1);

    ServiceLevelObjectiveV2DTO sloDTO2 = builderFactory.getSimpleServiceLevelObjectiveV2DTOBuilder()
                                             .identifier("id5")
                                             .userJourneyRefs(List.of("uj2"))
                                             .build();
    simpleServiceLevelObjectiveSpec = (SimpleServiceLevelObjectiveSpec) sloDTO2.getSpec();
    simpleServiceLevelObjectiveSpec.setServiceLevelIndicatorType(ServiceLevelIndicatorType.LATENCY);
    sloDTO2.setSpec(simpleServiceLevelObjectiveSpec);
    serviceLevelObjectiveV2Service.create(builderFactory.getProjectParams(), sloDTO2);

    ServiceLevelObjectiveV2DTO sloDTO3 = builderFactory.getSimpleServiceLevelObjectiveV2DTOBuilder()
                                             .identifier("id8")
                                             .userJourneyRefs(List.of("uj10"))
                                             .build();
    simpleServiceLevelObjectiveSpec = (SimpleServiceLevelObjectiveSpec) sloDTO3.getSpec();
    simpleServiceLevelObjectiveSpec.setServiceLevelIndicatorType(ServiceLevelIndicatorType.LATENCY);
    sloDTO3.setSpec(simpleServiceLevelObjectiveSpec);
    serviceLevelObjectiveV2Service.create(builderFactory.getProjectParams(), sloDTO3);

    Response response = RESOURCES.client()
                            .target("http://localhost:9998/slo-dashboard/widgets/list")
                            .queryParam("accountId", builderFactory.getContext().getAccountId())
                            .queryParam("orgIdentifier", builderFactory.getContext().getOrgIdentifier())
                            .queryParam("projectIdentifier", builderFactory.getContext().getProjectIdentifier())
                            .queryParam("userJourneyIdentifiers", "uj10")
                            .request(MediaType.APPLICATION_JSON_TYPE)
                            .get();

    assertThat(response.getStatus()).isEqualTo(200);
    String responseString = response.readEntity(String.class);
    JSONObject pageResponse = new JSONObject(responseString);
    pageResponse = (JSONObject) pageResponse.get("data");
    assertThat(pageResponse.get("totalItems")).isEqualTo(2);
    assertThat(pageResponse.get("pageItemCount")).isEqualTo(2);
    JSONArray sloDashboardWidgets = (JSONArray) pageResponse.get("content");
    assertThat(sloDashboardWidgets.length()).isEqualTo(2);
    JSONObject sloDashboardWidget = (JSONObject) sloDashboardWidgets.get(0);
    assertThat(sloDashboardWidget.get("sloIdentifier")).isEqualTo(sloDTO3.getIdentifier());
    assertThat(sloDashboardWidget.get("description")).isEqualTo(sloDTO3.getDescription());
    assertThat(sloDashboardWidget.get("monitoredServiceIdentifier")).isEqualTo(monitoredServiceDTO.getIdentifier());
    assertThat(sloDashboardWidget.get("monitoredServiceName")).isEqualTo(monitoredServiceDTO.getName());
    assertThat(sloDashboardWidget.get("serviceIdentifier")).isEqualTo(monitoredServiceDTO.getServiceRef());
    assertThat(sloDashboardWidget.get("environmentIdentifier")).isEqualTo(monitoredServiceDTO.getEnvironmentRef());
    assertThat(sloDashboardWidget.get("noOfActiveAlerts")).isEqualTo(sloDTO3.getNotificationRuleRefs().size());
    assertThat(sloDashboardWidget.get("serviceName")).isEqualTo("Mocked service name");
    assertThat(sloDashboardWidget.get("environmentName")).isEqualTo("Mocked env name");
  }

  @Test
  @Owner(developers = KARAN_SARASWAT)
  @Category(UnitTests.class)
  public void testGetSLODashboardWidgetsListPostRequest() {
    MonitoredServiceDTO monitoredServiceDTO = builderFactory.monitoredServiceDTOBuilder().build();

    ServiceLevelObjectiveV2DTO sloDTO1 = builderFactory.getSimpleServiceLevelObjectiveV2DTOBuilder()
                                             .identifier("id10")
                                             .userJourneyRefs(List.of("uj10"))
                                             .build();
    SimpleServiceLevelObjectiveSpec simpleServiceLevelObjectiveSpec =
        (SimpleServiceLevelObjectiveSpec) sloDTO1.getSpec();
    simpleServiceLevelObjectiveSpec.setServiceLevelIndicatorType(ServiceLevelIndicatorType.AVAILABILITY);
    sloDTO1.setSpec(simpleServiceLevelObjectiveSpec);
    serviceLevelObjectiveV2Service.create(builderFactory.getProjectParams(), sloDTO1);

    ServiceLevelObjectiveV2DTO sloDTO2 = builderFactory.getSimpleServiceLevelObjectiveV2DTOBuilder()
                                             .identifier("id5")
                                             .userJourneyRefs(List.of("uj2"))
                                             .build();
    simpleServiceLevelObjectiveSpec = (SimpleServiceLevelObjectiveSpec) sloDTO2.getSpec();
    simpleServiceLevelObjectiveSpec.setServiceLevelIndicatorType(ServiceLevelIndicatorType.LATENCY);
    sloDTO2.setSpec(simpleServiceLevelObjectiveSpec);
    serviceLevelObjectiveV2Service.create(builderFactory.getProjectParams(), sloDTO2);

    ServiceLevelObjectiveV2DTO sloDTO3 = builderFactory.getSimpleServiceLevelObjectiveV2DTOBuilder()
                                             .identifier("id8")
                                             .userJourneyRefs(List.of("uj10"))
                                             .build();
    simpleServiceLevelObjectiveSpec = (SimpleServiceLevelObjectiveSpec) sloDTO3.getSpec();
    simpleServiceLevelObjectiveSpec.setServiceLevelIndicatorType(ServiceLevelIndicatorType.LATENCY);
    sloDTO3.setSpec(simpleServiceLevelObjectiveSpec);
    serviceLevelObjectiveV2Service.create(builderFactory.getProjectParams(), sloDTO3);

    JSONObject body = new JSONObject();
    JSONArray userJourneyIdentifiers = new JSONArray();
    userJourneyIdentifiers.put("uj10");
    body.put("userJourneyIdentifiers", userJourneyIdentifiers);

    Response response = RESOURCES.client()
                            .target("http://localhost:9998/slo-dashboard/widgets/list")
                            .queryParam("accountId", builderFactory.getContext().getAccountId())
                            .queryParam("orgIdentifier", builderFactory.getContext().getOrgIdentifier())
                            .queryParam("projectIdentifier", builderFactory.getContext().getProjectIdentifier())
                            .request(MediaType.APPLICATION_JSON_TYPE)
                            .post(Entity.json(body.toString()));

    assertThat(response.getStatus()).isEqualTo(200);
    String responseString = response.readEntity(String.class);
    JSONObject pageResponse = new JSONObject(responseString);
    pageResponse = (JSONObject) pageResponse.get("data");
    assertThat(pageResponse.get("totalItems")).isEqualTo(2);
    assertThat(pageResponse.get("pageItemCount")).isEqualTo(2);
    JSONArray sloDashboardWidgets = (JSONArray) pageResponse.get("content");
    assertThat(sloDashboardWidgets.length()).isEqualTo(2);
    JSONObject sloDashboardWidget = (JSONObject) sloDashboardWidgets.get(0);
    assertThat(sloDashboardWidget.get("sloIdentifier")).isEqualTo(sloDTO3.getIdentifier());
    assertThat(sloDashboardWidget.get("description")).isEqualTo(sloDTO3.getDescription());
    assertThat(sloDashboardWidget.get("monitoredServiceIdentifier")).isEqualTo(monitoredServiceDTO.getIdentifier());
    assertThat(sloDashboardWidget.get("monitoredServiceName")).isEqualTo(monitoredServiceDTO.getName());
    assertThat(sloDashboardWidget.get("serviceIdentifier")).isEqualTo(monitoredServiceDTO.getServiceRef());
    assertThat(sloDashboardWidget.get("environmentIdentifier")).isEqualTo(monitoredServiceDTO.getEnvironmentRef());
    assertThat(sloDashboardWidget.get("noOfActiveAlerts")).isEqualTo(sloDTO3.getNotificationRuleRefs().size());
    assertThat(sloDashboardWidget.get("serviceName")).isEqualTo("Mocked service name");
    assertThat(sloDashboardWidget.get("environmentName")).isEqualTo("Mocked env name");
  }

  //  @Test
  //  @Owner(developers = KARAN_SARASWAT)
  //  @Category(UnitTests.class)
  //  public void testGetSLODashboardWidgetsListforAddingToCompositeSLO() {
  //    SLOTargetDTO calendarSloTarget = SLOTargetDTO.builder()
  //                                         .type(SLOTargetType.CALENDER)
  //                                         .sloTargetPercentage(80.0)
  //                                         .spec(CalenderSLOTargetSpec.builder()
  //                                                   .type(SLOCalenderType.QUARTERLY)
  //                                                   .spec(CalenderSLOTargetSpec.QuarterlyCalenderSpec.builder().build())
  //                                                   .build())
  //                                         .build();
  //
  //    ServiceLevelObjectiveV2DTO sloDTO1 = builderFactory.getSimpleServiceLevelObjectiveV2DTOBuilder().build();
  //    sloDTO1.setSloTarget(calendarSloTarget);
  //    serviceLevelObjectiveV2Service.create(builderFactory.getProjectParams(), sloDTO1);
  //
  //    ServiceLevelObjectiveV2DTO sloDTO2 =
  //        builderFactory.getSimpleServiceLevelObjectiveV2DTOBuilder().identifier("id5").name("new one").build();
  //    sloDTO2.setSloTarget(calendarSloTarget);
  //    serviceLevelObjectiveV2Service.create(builderFactory.getProjectParams(), sloDTO2);
  //
  //    ServiceLevelObjectiveV2DTO sloDTO3 =
  //        builderFactory.getSimpleServiceLevelObjectiveV2DTOBuilder().identifier("id8").name("new two").build();
  //    serviceLevelObjectiveV2Service.create(builderFactory.getProjectParams(), sloDTO3);
  //
  //    ServiceLevelObjectiveV2DTO compositeSLO =
  //        builderFactory.getCompositeServiceLevelObjectiveV2DTOBuilder()
  //            .spec(CompositeServiceLevelObjectiveSpec.builder()
  //                      .serviceLevelObjectivesDetails(
  //                          Arrays.asList(ServiceLevelObjectiveDetailsDTO.builder()
  //                                            .serviceLevelObjectiveRef("id5")
  //                                            .weightagePercentage(75.0)
  //                                            .projectIdentifier(builderFactory.getContext().getProjectIdentifier())
  //                                            .orgIdentifier(builderFactory.getContext().getOrgIdentifier())
  //                                            .accountId(builderFactory.getContext().getAccountId())
  //                                            .build(),
  //                              ServiceLevelObjectiveDetailsDTO.builder()
  //                                  .serviceLevelObjectiveRef("id8")
  //                                  .weightagePercentage(25.0)
  //                                  .projectIdentifier(builderFactory.getContext().getProjectIdentifier())
  //                                  .orgIdentifier(builderFactory.getContext().getOrgIdentifier())
  //                                  .accountId(builderFactory.getContext().getAccountId())
  //                                  .build()))
  //                      .build())
  //            .build();
  //    serviceLevelObjectiveV2Service.create(builderFactory.getProjectParams(), compositeSLO);
  //
  //    JSONObject body = new JSONObject();
  //    body.put("type", "Simple");
  //    body.put("sloTargetFilterDTO", SLOTargetFilterDTO.builder()
  //            .type(SLOTargetType.CALENDER)
  //            .spec(CalenderSLOTargetSpec.builder()
  //                    .type(SLOCalenderType.QUARTERLY)
  //                    .spec(CalenderSLOTargetSpec.QuarterlyCalenderSpec.builder().build())
  //                    .build())
  //            .build());
  //
  //    Response response = RESOURCES.client()
  //                            .target("http://localhost:9998/slo-dashboard/widgets/list")
  //                            .queryParam("accountId", builderFactory.getContext().getAccountId())
  //                            .queryParam("orgIdentifier", builderFactory.getContext().getOrgIdentifier())
  //                            .queryParam("projectIdentifier", builderFactory.getContext().getProjectIdentifier())
  //                            .request(MediaType.APPLICATION_JSON_TYPE)
  //                            .post(Entity.json(body.toString()));
  //
  //    assertThat(response.getStatus()).isEqualTo(200);
  //    String responseString = response.readEntity(String.class);
  //    assertThat(responseString).contains("\"totalItems\":2");
  //    assertThat(responseString).contains("\"pageItemCount\":2");
  //  }

  @Test
  @Owner(developers = ABHIJITH)
  @Category(UnitTests.class)
  public void testGetRiskCount() {
    ServiceLevelObjectiveDTO sloDTO = builderFactory.getServiceLevelObjectiveDTOBuilder()
                                          .identifier("id1")
                                          .userJourneyRef("uj1")
                                          .type(ServiceLevelIndicatorType.AVAILABILITY)
                                          .build();
    serviceLevelObjectiveService.create(builderFactory.getProjectParams(), sloDTO);
    SLOHealthIndicator sloHealthIndicator = builderFactory.sLOHealthIndicatorBuilder()
                                                .serviceLevelObjectiveIdentifier(sloDTO.getIdentifier())
                                                .errorBudgetRemainingPercentage(10)
                                                .errorBudgetRisk(ErrorBudgetRisk.UNHEALTHY)
                                                .build();
    hPersistence.save(sloHealthIndicator);
    sloDTO = builderFactory.getServiceLevelObjectiveDTOBuilder()
                 .identifier("id5")
                 .userJourneyRef("uj2")
                 .type(ServiceLevelIndicatorType.AVAILABILITY)
                 .build();
    serviceLevelObjectiveService.create(builderFactory.getProjectParams(), sloDTO);
    sloHealthIndicator = builderFactory.sLOHealthIndicatorBuilder()
                             .serviceLevelObjectiveIdentifier(sloDTO.getIdentifier())
                             .errorBudgetRemainingPercentage(10)
                             .errorBudgetRisk(ErrorBudgetRisk.UNHEALTHY)
                             .build();
    hPersistence.save(sloHealthIndicator);
    sloDTO = builderFactory.getServiceLevelObjectiveDTOBuilder()
                 .identifier("id2")
                 .userJourneyRef("uj1")
                 .type(ServiceLevelIndicatorType.AVAILABILITY)
                 .target(SLOTargetDTO.builder()
                             .type(SLOTargetType.CALENDER)
                             .sloTargetPercentage(80.0)
                             .spec(CalenderSLOTargetSpec.builder()
                                       .type(SLOCalenderType.WEEKLY)
                                       .spec(WeeklyCalendarSpec.builder().dayOfWeek(DayOfWeek.MONDAY).build())
                                       .build())
                             .build())
                 .build();
    serviceLevelObjectiveService.create(builderFactory.getProjectParams(), sloDTO);
    sloHealthIndicator = builderFactory.sLOHealthIndicatorBuilder()
                             .serviceLevelObjectiveIdentifier(sloDTO.getIdentifier())
                             .errorBudgetRemainingPercentage(-10)
                             .errorBudgetRisk(ErrorBudgetRisk.EXHAUSTED)
                             .build();
    hPersistence.save(sloHealthIndicator);
    sloDTO = builderFactory.getServiceLevelObjectiveDTOBuilder()
                 .identifier("id3")
                 .type(ServiceLevelIndicatorType.AVAILABILITY)
                 .userJourneyRef("uj2")
                 .build();
    serviceLevelObjectiveService.create(builderFactory.getProjectParams(), sloDTO);

    Response response = RESOURCES.client()
                            .target("http://localhost:9998/slo-dashboard/risk-count")
                            .queryParam("accountId", builderFactory.getContext().getAccountId())
                            .queryParam("orgIdentifier", builderFactory.getContext().getOrgIdentifier())
                            .queryParam("projectIdentifier", builderFactory.getContext().getProjectIdentifier())
                            .queryParam("userJourneyIdentifiers", "uj1")
                            .queryParam("sliTypes", "Availability")
                            .queryParam("targetTypes", "Rolling")
                            .request(MediaType.APPLICATION_JSON_TYPE)
                            .get();

    assertThat(response.getStatus()).isEqualTo(200);
    String responseString = response.readEntity(String.class);
    assertThat(responseString).contains("\"totalCount\":1");
    assertThat(responseString).contains("\"count\":1");
    assertThat(responseString).contains("\"identifier\":\"UNHEALTHY\"");
    assertThat(responseString).contains("\"displayName\":\"Unhealthy\"");
  }

  @Test
  @Owner(developers = ARPITJ)
  @Category(UnitTests.class)
  public void test_sloWidgetIdentifier() {
    ServiceLevelObjectiveDTO sloDTO = builderFactory.getServiceLevelObjectiveDTOBuilder()
                                          .identifier("id1")
                                          .userJourneyRef("uj1")
                                          .type(ServiceLevelIndicatorType.AVAILABILITY)
                                          .build();

    serviceLevelObjectiveService.create(builderFactory.getProjectParams(), sloDTO);
    SLOHealthIndicator sloHealthIndicator = builderFactory.sLOHealthIndicatorBuilder()
                                                .serviceLevelObjectiveIdentifier(sloDTO.getIdentifier())
                                                .errorBudgetRemainingPercentage(10)
                                                .errorBudgetRisk(ErrorBudgetRisk.UNHEALTHY)
                                                .build();
    hPersistence.save(sloHealthIndicator);

    Response response = RESOURCES.client()
                            .target("http://localhost:9998/slo-dashboard/widget/id1")
                            .queryParam("accountId", builderFactory.getContext().getAccountId())
                            .queryParam("orgIdentifier", builderFactory.getContext().getOrgIdentifier())
                            .queryParam("projectIdentifier", builderFactory.getContext().getProjectIdentifier())
                            .request(MediaType.APPLICATION_JSON_TYPE)
                            .get();

    assertThat(response.getStatus()).isEqualTo(200);
    String responseString = response.readEntity(String.class);
    assertThat(responseString).contains("\"sloIdentifier\":\"id1\"");
    assertThat(responseString).contains("\"title\":\"sloName\"");
    assertThat(responseString).contains("\"healthSourceName\":\"health source name\"");
  }
}
