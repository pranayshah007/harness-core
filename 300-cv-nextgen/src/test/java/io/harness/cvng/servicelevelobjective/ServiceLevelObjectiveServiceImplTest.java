package io.harness.cvng.servicelevelobjective;

import static io.harness.rule.OwnerRule.DEEPAK_CHHIKARA;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.harness.CvNextGenTestBase;
import io.harness.category.element.UnitTests;
import io.harness.cvng.BuilderFactory;
import io.harness.cvng.core.beans.monitoredService.MonitoredServiceDTO;
import io.harness.cvng.core.beans.params.ProjectParams;
import io.harness.cvng.core.services.api.monitoredService.MonitoredServiceService;
import io.harness.cvng.servicelevelobjective.beans.DayOfWeek;
import io.harness.cvng.servicelevelobjective.beans.SLIMetricType;
import io.harness.cvng.servicelevelobjective.beans.SLIMissingDataType;
import io.harness.cvng.servicelevelobjective.beans.SLOCalenderType;
import io.harness.cvng.servicelevelobjective.beans.SLOTarget;
import io.harness.cvng.servicelevelobjective.beans.SLOTargetType;
import io.harness.cvng.servicelevelobjective.beans.ServiceLevelIndicatorDTO;
import io.harness.cvng.servicelevelobjective.beans.ServiceLevelIndicatorSpec;
import io.harness.cvng.servicelevelobjective.beans.ServiceLevelIndicatorType;
import io.harness.cvng.servicelevelobjective.beans.ServiceLevelObjectiveDTO;
import io.harness.cvng.servicelevelobjective.beans.ServiceLevelObjectiveResponse;
import io.harness.cvng.servicelevelobjective.beans.slimetricspec.RatioSLIMetricEventType;
import io.harness.cvng.servicelevelobjective.beans.slimetricspec.RatioSLIMetricSpec;
import io.harness.cvng.servicelevelobjective.beans.slimetricspec.ThresholdType;
import io.harness.cvng.servicelevelobjective.beans.slotargetspec.CalenderSLOTargetSpec;
import io.harness.cvng.servicelevelobjective.beans.slotargetspec.CalenderSLOTargetSpec.WeeklyCalendarSpec;
import io.harness.cvng.servicelevelobjective.beans.slotargetspec.RollingSLOTargetSpec;
import io.harness.cvng.servicelevelobjective.services.api.ServiceLevelObjectiveService;
import io.harness.exception.InvalidRequestException;
import io.harness.rule.Owner;

import com.google.inject.Inject;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class ServiceLevelObjectiveServiceImplTest extends CvNextGenTestBase {
  @Inject ServiceLevelObjectiveService serviceLevelObjectiveService;

  @Inject MonitoredServiceService monitoredServiceService;
  String accountId;
  String orgIdentifier;
  String projectIdentifier;
  String identifier;
  String name;
  List<ServiceLevelIndicatorDTO> serviceLevelIndicators;
  SLOTarget sloTarget;
  SLOTarget calendarSloTarget;
  String userJourneyIdentifier;
  String description;
  String monitoredServiceIdentifier;
  String healthSourceIdentifiers;
  ProjectParams projectParams;
  Map<String, String> tags;
  private BuilderFactory builderFactory;

  @Before
  public void setup() throws IllegalAccessException, ParseException {
    builderFactory = BuilderFactory.getDefault();
    accountId = builderFactory.getContext().getAccountId();
    orgIdentifier = builderFactory.getContext().getOrgIdentifier();
    projectIdentifier = builderFactory.getContext().getProjectIdentifier();
    identifier = "sloIdentifier";
    name = "sloName";
    monitoredServiceIdentifier = "monitoredServiceIdentifier";
    healthSourceIdentifiers = "healthSourceIdentifier";
    description = "description";
    tags = new HashMap<>();
    tags.put("tag1", "value1");
    tags.put("tag2", "value2");

    serviceLevelIndicators = Collections.singletonList(ServiceLevelIndicatorDTO.builder()
                                                           .identifier("sliIndicator")
                                                           .name("sliName")
                                                           .type(ServiceLevelIndicatorType.LATENCY)
                                                           .spec(ServiceLevelIndicatorSpec.builder()
                                                                     .type(SLIMetricType.RATIO)
                                                                     .spec(RatioSLIMetricSpec.builder()
                                                                               .eventType(RatioSLIMetricEventType.GOOD)
                                                                               .metric1("metric1")
                                                                               .metric2("metric2")
                                                                               .build())
                                                                     .build())
                                                           .build());

    sloTarget = SLOTarget.builder()
                    .type(SLOTargetType.ROLLING)
                    .sloTargetPercentage(80.0)
                    .spec(RollingSLOTargetSpec.builder().periodLength("30d").build())
                    .build();

    SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
    calendarSloTarget = SLOTarget.builder()
                            .type(SLOTargetType.CALENDER)
                            .sloTargetPercentage(80.0)
                            .spec(CalenderSLOTargetSpec.builder()
                                      .type(SLOCalenderType.WEEKLY)
                                      .spec(WeeklyCalendarSpec.builder().dayOfWeek(DayOfWeek.MONDAY).build())

                                      .build())
                            .build();
    userJourneyIdentifier = "userJourney";

    projectParams = ProjectParams.builder()
                        .accountIdentifier(accountId)
                        .orgIdentifier(orgIdentifier)
                        .projectIdentifier(projectIdentifier)
                        .build();
  }

  @Test
  @Owner(developers = DEEPAK_CHHIKARA)
  @Category(UnitTests.class)
  public void testCreate_Success() {
    ServiceLevelObjectiveDTO sloDTO = createSLOBuilder();
    createMonitoredService();
    ServiceLevelObjectiveResponse serviceLevelObjectiveResponse =
        serviceLevelObjectiveService.create(projectParams, sloDTO);
    assertThat(serviceLevelObjectiveResponse.getServiceLevelObjectiveDTO()).isEqualTo(sloDTO);
  }

  @Test
  @Owner(developers = DEEPAK_CHHIKARA)
  @Category(UnitTests.class)
  public void testCreate_WithoutTagsSuccess() {
    ServiceLevelObjectiveDTO sloDTO = createSLOBuilder();
    sloDTO.setTags(new HashMap<>());
    createMonitoredService();
    ServiceLevelObjectiveResponse serviceLevelObjectiveResponse =
        serviceLevelObjectiveService.create(projectParams, sloDTO);
    assertThat(serviceLevelObjectiveResponse.getServiceLevelObjectiveDTO()).isEqualTo(sloDTO);
  }

  @Test
  @Owner(developers = DEEPAK_CHHIKARA)
  @Category(UnitTests.class)
  public void testCreate_CalendarSLOTargetSuccess() {
    ServiceLevelObjectiveDTO sloDTO = createSLOBuilder();
    sloDTO.setTarget(calendarSloTarget);
    createMonitoredService();
    ServiceLevelObjectiveResponse serviceLevelObjectiveResponse =
        serviceLevelObjectiveService.create(projectParams, sloDTO);
    assertThat(serviceLevelObjectiveResponse.getServiceLevelObjectiveDTO()).isEqualTo(sloDTO);
  }

  @Test
  @Owner(developers = DEEPAK_CHHIKARA)
  @Category(UnitTests.class)
  public void testCreate_withoutMonitoredServiceFailedValidation() {
    ServiceLevelObjectiveDTO sloDTO = createSLOBuilder();
    assertThatThrownBy(() -> serviceLevelObjectiveService.create(projectParams, sloDTO))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage(String.format(
            "Monitored Source Entity with identifier %s is not present", sloDTO.getMonitoredServiceRef()));
  }

  @Test
  @Owner(developers = DEEPAK_CHHIKARA)
  @Category(UnitTests.class)
  public void testDelete_Success() {
    ServiceLevelObjectiveDTO sloDTO = createSLOBuilder();
    createMonitoredService();
    serviceLevelObjectiveService.create(projectParams, sloDTO);
    boolean isDeleted = serviceLevelObjectiveService.delete(projectParams, sloDTO.getIdentifier());
    assertThat(isDeleted).isEqualTo(true);
  }

  @Test
  @Owner(developers = DEEPAK_CHHIKARA)
  @Category(UnitTests.class)
  public void testDelete_validationFailedForIncorrectSLO() {
    ServiceLevelObjectiveDTO serviceLevelObjectiveDTO = createSLOBuilder();
    createMonitoredService();
    serviceLevelObjectiveService.create(projectParams, serviceLevelObjectiveDTO);
    serviceLevelObjectiveDTO.setIdentifier("incorrectSLOIdentifier");
    assertThatThrownBy(
        () -> serviceLevelObjectiveService.delete(projectParams, serviceLevelObjectiveDTO.getIdentifier()))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage(String.format(
            "SLO  with identifier %s, accountId %s, orgIdentifier %s and projectIdentifier %s  is not present",
            serviceLevelObjectiveDTO.getIdentifier(), projectParams.getAccountIdentifier(),
            projectParams.getOrgIdentifier(), projectParams.getProjectIdentifier()));
  }

  @Test
  @Owner(developers = DEEPAK_CHHIKARA)
  @Category(UnitTests.class)
  public void testUpdate_Success() {
    ServiceLevelObjectiveDTO sloDTO = createSLOBuilder();
    createMonitoredService();
    ServiceLevelObjectiveResponse serviceLevelObjectiveResponse =
        serviceLevelObjectiveService.create(projectParams, sloDTO);
    assertThat(serviceLevelObjectiveResponse.getServiceLevelObjectiveDTO()).isEqualTo(sloDTO);
    sloDTO.setDescription("newDescription");
    ServiceLevelObjectiveResponse updateServiceLevelObjectiveResponse =
        serviceLevelObjectiveService.update(projectParams, sloDTO.getIdentifier(), sloDTO);
    assertThat(updateServiceLevelObjectiveResponse.getServiceLevelObjectiveDTO()).isEqualTo(sloDTO);
  }

  @Test
  @Owner(developers = DEEPAK_CHHIKARA)
  @Category(UnitTests.class)
  public void testUpdate_SLIUpdateSuccess() {
    ServiceLevelObjectiveDTO sloDTO = createSLOBuilder();
    createMonitoredService();
    ServiceLevelObjectiveResponse serviceLevelObjectiveResponse =
        serviceLevelObjectiveService.create(projectParams, sloDTO);
    assertThat(serviceLevelObjectiveResponse.getServiceLevelObjectiveDTO()).isEqualTo(sloDTO);
    ServiceLevelIndicatorDTO serviceLevelIndicatorDTO1 = sloDTO.getServiceLevelIndicators().get(0);
    serviceLevelIndicatorDTO1.setType(ServiceLevelIndicatorType.AVAILABILITY);
    RatioSLIMetricSpec ratioSLIMetricSpec = (RatioSLIMetricSpec) serviceLevelIndicatorDTO1.getSpec().getSpec();
    ratioSLIMetricSpec.setMetric1("newMetric");
    serviceLevelIndicatorDTO1.getSpec().setSpec(ratioSLIMetricSpec);
    ServiceLevelIndicatorDTO serviceLevelIndicatorDTO2 = builderFactory.getServiceLevelIndicatorDTOBuilder();
    serviceLevelIndicatorDTO2.setSliMissingDataType(SLIMissingDataType.GOOD);
    serviceLevelIndicatorDTO2.setSpec(ServiceLevelIndicatorSpec.builder()
                                          .type(SLIMetricType.RATIO)
                                          .spec(RatioSLIMetricSpec.builder()
                                                    .thresholdValue(20.0)
                                                    .thresholdType(ThresholdType.GREATER_THAN)
                                                    .eventType(RatioSLIMetricEventType.BAD)
                                                    .metric1("metric4")
                                                    .metric2("metric5")
                                                    .build())
                                          .build());
    List<ServiceLevelIndicatorDTO> serviceLevelIndicatorDTOList = new ArrayList<>();
    serviceLevelIndicatorDTOList.add(serviceLevelIndicatorDTO1);
    serviceLevelIndicatorDTOList.add(serviceLevelIndicatorDTO2);
    sloDTO.setServiceLevelIndicators(serviceLevelIndicatorDTOList);
    ServiceLevelObjectiveResponse updateServiceLevelObjectiveResponse =
        serviceLevelObjectiveService.update(projectParams, sloDTO.getIdentifier(), sloDTO);
    assertThat(updateServiceLevelObjectiveResponse.getServiceLevelObjectiveDTO().getServiceLevelIndicators())
        .isEqualTo(serviceLevelIndicatorDTOList);
  }

  @Test
  @Owner(developers = DEEPAK_CHHIKARA)
  @Category(UnitTests.class)
  public void testUpdate_FailedWithEntityNotPresent() {
    ServiceLevelObjectiveDTO sloDTO = createSLOBuilder();
    createMonitoredService();
    ServiceLevelObjectiveResponse serviceLevelObjectiveResponse =
        serviceLevelObjectiveService.create(projectParams, sloDTO);
    assertThat(serviceLevelObjectiveResponse.getServiceLevelObjectiveDTO()).isEqualTo(sloDTO);
    sloDTO.setIdentifier("newIdentifier");
    sloDTO.setDescription("newDescription");
    assertThatThrownBy(() -> serviceLevelObjectiveService.update(projectParams, sloDTO.getIdentifier(), sloDTO))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage(String.format(
            "SLO  with identifier %s, accountId %s, orgIdentifier %s and projectIdentifier %s  is not present",
            sloDTO.getIdentifier(), projectParams.getAccountIdentifier(), projectParams.getOrgIdentifier(),
            projectParams.getProjectIdentifier()));
  }

  @Test
  @Owner(developers = DEEPAK_CHHIKARA)
  @Category(UnitTests.class)
  public void testGet_IdentifierBasedQuery() {
    ServiceLevelObjectiveDTO sloDTO = createSLOBuilder();
    createMonitoredService();
    serviceLevelObjectiveService.create(projectParams, sloDTO);
    ServiceLevelObjectiveResponse serviceLevelObjectiveResponse =
        serviceLevelObjectiveService.get(projectParams, sloDTO.getIdentifier());
    assertThat(serviceLevelObjectiveResponse.getServiceLevelObjectiveDTO()).isEqualTo(sloDTO);
  }

  private ServiceLevelObjectiveDTO createSLOBuilder() {
    return builderFactory.getServiceLevelObjectiveDTOBuilder().build();
  }

  private void createMonitoredService() {
    MonitoredServiceDTO monitoredServiceDTO = builderFactory.monitoredServiceDTOBuilder().build();
    monitoredServiceDTO.setSources(MonitoredServiceDTO.Sources.builder().build());
    monitoredServiceService.create(builderFactory.getContext().getAccountId(), monitoredServiceDTO);
  }
}
