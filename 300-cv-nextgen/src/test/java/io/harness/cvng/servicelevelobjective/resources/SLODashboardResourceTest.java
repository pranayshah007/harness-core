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
import io.harness.cvng.servicelevelobjective.beans.SLOTarget;
import io.harness.cvng.servicelevelobjective.beans.SLOTargetType;
import io.harness.cvng.servicelevelobjective.beans.ServiceLevelIndicatorType;
import io.harness.cvng.servicelevelobjective.beans.ServiceLevelObjectiveDTO;
import io.harness.cvng.servicelevelobjective.beans.ServiceLevelObjectiveResponse;
import io.harness.cvng.servicelevelobjective.beans.slotargetspec.CalenderSLOTargetSpec;
import io.harness.cvng.servicelevelobjective.beans.slotargetspec.CalenderSLOTargetSpec.WeeklyCalendarSpec;
import io.harness.cvng.servicelevelobjective.entities.SLOHealthIndicator;
import io.harness.cvng.servicelevelobjective.services.api.ServiceLevelObjectiveService;
import io.harness.persistence.HPersistence;
import io.harness.rule.Owner;
import io.harness.rule.ResourceTestRule;

import com.google.inject.Inject;
import com.google.inject.Injector;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class SLODashboardResourceTest extends CvNextGenTestBase {
  @Inject private Injector injector;
  @Inject private HPersistence hPersistence;
  @Inject private MonitoredServiceService monitoredServiceService;
  @Inject private ServiceLevelObjectiveService serviceLevelObjectiveService;
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
  @Owner(developers = ABHIJITH)
  @Category(UnitTests.class)
  public void testGetRiskCount() {
    ServiceLevelObjectiveDTO sloDTO = builderFactory.getServiceLevelObjectiveDTOBuilder()
                                          .identifier("id1")
                                          .userJourneyRef("uj1")
                                          .type(ServiceLevelIndicatorType.AVAILABILITY)
                                          .build();
    ServiceLevelObjectiveResponse serviceLevelObjectiveResponse =
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
    serviceLevelObjectiveResponse = serviceLevelObjectiveService.create(builderFactory.getProjectParams(), sloDTO);
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
                 .target(SLOTarget.builder()
                             .type(SLOTargetType.CALENDER)
                             .sloTargetPercentage(80.0)
                             .spec(CalenderSLOTargetSpec.builder()
                                       .type(SLOCalenderType.WEEKLY)
                                       .spec(WeeklyCalendarSpec.builder().dayOfWeek(DayOfWeek.MONDAY).build())
                                       .build())
                             .build())
                 .build();
    serviceLevelObjectiveResponse = serviceLevelObjectiveService.create(builderFactory.getProjectParams(), sloDTO);
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
    serviceLevelObjectiveResponse = serviceLevelObjectiveService.create(builderFactory.getProjectParams(), sloDTO);

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
