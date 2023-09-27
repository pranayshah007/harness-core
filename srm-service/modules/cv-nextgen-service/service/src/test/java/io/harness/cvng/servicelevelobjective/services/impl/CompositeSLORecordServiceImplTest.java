/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.servicelevelobjective.services.impl;

import static io.harness.cvng.CVNGTestConstants.TIME_FOR_TESTS;
import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.rule.OwnerRule.ARPITJ;
import static io.harness.rule.OwnerRule.KARAN_SARASWAT;
import static io.harness.rule.OwnerRule.VARSHA_LALWANI;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.offset;

import io.harness.CvNextGenTestBase;
import io.harness.category.element.UnitTests;
import io.harness.cvng.BuilderFactory;
import io.harness.cvng.core.beans.monitoredService.MonitoredServiceDTO;
import io.harness.cvng.core.services.api.monitoredService.MonitoredServiceService;
import io.harness.cvng.servicelevelobjective.beans.CompositeSLOFormulaType;
import io.harness.cvng.servicelevelobjective.beans.SLIEvaluationType;
import io.harness.cvng.servicelevelobjective.beans.SLIMissingDataType;
import io.harness.cvng.servicelevelobjective.beans.ServiceLevelIndicatorDTO;
import io.harness.cvng.servicelevelobjective.beans.ServiceLevelObjectiveDetailsDTO;
import io.harness.cvng.servicelevelobjective.beans.ServiceLevelObjectiveV2DTO;
import io.harness.cvng.servicelevelobjective.beans.slospec.CompositeServiceLevelObjectiveSpec;
import io.harness.cvng.servicelevelobjective.beans.slospec.SimpleServiceLevelObjectiveSpec;
import io.harness.cvng.servicelevelobjective.beans.slotargetspec.WindowBasedServiceLevelIndicatorSpec;
import io.harness.cvng.servicelevelobjective.entities.CompositeSLORecord;
import io.harness.cvng.servicelevelobjective.entities.CompositeServiceLevelObjective;
import io.harness.cvng.servicelevelobjective.entities.SLIRecord;
import io.harness.cvng.servicelevelobjective.entities.SLIState;
import io.harness.cvng.servicelevelobjective.entities.SimpleServiceLevelObjective;
import io.harness.cvng.servicelevelobjective.services.api.ServiceLevelIndicatorService;
import io.harness.cvng.servicelevelobjective.services.api.ServiceLevelObjectiveV2Service;
import io.harness.persistence.HPersistence;
import io.harness.rule.Owner;

import com.google.inject.Inject;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;

public class CompositeSLORecordServiceImplTest extends CvNextGenTestBase {
  @Spy @Inject private CompositeSLORecordServiceImpl sloRecordService;

  @Inject private HPersistence hPersistence;
  @Inject private ServiceLevelObjectiveV2Service serviceLevelObjectiveV2Service;
  @Inject private MonitoredServiceService monitoredServiceService;
  @Inject ServiceLevelIndicatorService serviceLevelIndicatorService;
  BuilderFactory builderFactory;
  private Instant startTime;
  private Instant endTime;
  private String verificationTaskId;
  private String leastPerformantVerificationTaskId;
  private String requestVerificationTaskId;

  ServiceLevelObjectiveV2DTO serviceLevelObjectiveV2DTO;
  CompositeServiceLevelObjective compositeServiceLevelObjective;
  CompositeServiceLevelObjective compositeServiceLevelObjective2;
  CompositeServiceLevelObjective leastPerformantCompositeServiceLevelObjective;
  CompositeServiceLevelObjective leastPerformantCompositeServiceLevelObjective2;
  ServiceLevelObjectiveV2DTO simpleServiceLevelObjectiveDTO1;
  ServiceLevelObjectiveV2DTO simpleServiceLevelObjectiveDTO2;
  ServiceLevelObjectiveV2DTO simpleServiceLevelObjectiveDTO3;
  SimpleServiceLevelObjective simpleServiceLevelObjective1;
  SimpleServiceLevelObjective simpleServiceLevelObjective2;
  SimpleServiceLevelObjective simpleServiceLevelObjective3;

  ServiceLevelObjectiveV2DTO requestServiceLevelObjectiveV2DTO;
  CompositeServiceLevelObjective requestCompositeServiceLevelObjective;
  ServiceLevelObjectiveV2DTO requestSimpleServiceLevelObjectiveDTO1;
  ServiceLevelObjectiveV2DTO requestSimpleServiceLevelObjectiveDTO2;
  SimpleServiceLevelObjective requestSimpleServiceLevelObjective1;
  SimpleServiceLevelObjective requestSimpleServiceLevelObjective2;

  List<SLIState> sliStateList3 =
      Arrays.asList(SLIState.NO_DATA, SLIState.NO_DATA, SLIState.NO_DATA, SLIState.NO_DATA, SLIState.NO_DATA);

  @Before
  public void setup() {
    builderFactory = BuilderFactory.getDefault();
    builderFactory.getContext().setOrgIdentifier("orgIdentifier");
    builderFactory.getContext().setProjectIdentifier("project");
    MockitoAnnotations.initMocks(this);

    MonitoredServiceDTO monitoredServiceDTO1 =
        builderFactory.monitoredServiceDTOBuilder().sources(MonitoredServiceDTO.Sources.builder().build()).build();
    monitoredServiceService.create(builderFactory.getContext().getAccountId(), monitoredServiceDTO1);
    simpleServiceLevelObjectiveDTO1 = builderFactory.getSimpleServiceLevelObjectiveV2DTOBuilder().build();
    SimpleServiceLevelObjectiveSpec simpleServiceLevelObjectiveSpec1 =
        (SimpleServiceLevelObjectiveSpec) simpleServiceLevelObjectiveDTO1.getSpec();
    simpleServiceLevelObjectiveSpec1.setMonitoredServiceRef(monitoredServiceDTO1.getIdentifier());
    simpleServiceLevelObjectiveSpec1.setHealthSourceRef(generateUuid());
    simpleServiceLevelObjectiveDTO1.setSpec(simpleServiceLevelObjectiveSpec1);
    serviceLevelObjectiveV2Service.create(builderFactory.getProjectParams(), simpleServiceLevelObjectiveDTO1);
    simpleServiceLevelObjective1 = (SimpleServiceLevelObjective) serviceLevelObjectiveV2Service.getEntity(
        builderFactory.getProjectParams(), simpleServiceLevelObjectiveDTO1.getIdentifier());

    MonitoredServiceDTO monitoredServiceDTO2 = builderFactory.monitoredServiceDTOBuilder()
                                                   .sources(MonitoredServiceDTO.Sources.builder().build())
                                                   .serviceRef("service1")
                                                   .environmentRef("env1")
                                                   .identifier("service1_env1")
                                                   .build();
    monitoredServiceService.create(builderFactory.getContext().getAccountId(), monitoredServiceDTO2);
    simpleServiceLevelObjectiveDTO2 =
        builderFactory.getSimpleServiceLevelObjectiveV2DTOBuilder().identifier("sloIdentifier2").build();
    SimpleServiceLevelObjectiveSpec simpleServiceLevelObjectiveSpec2 =
        (SimpleServiceLevelObjectiveSpec) simpleServiceLevelObjectiveDTO2.getSpec();
    simpleServiceLevelObjectiveSpec2.setMonitoredServiceRef(monitoredServiceDTO2.getIdentifier());
    simpleServiceLevelObjectiveSpec2.setHealthSourceRef(generateUuid());
    simpleServiceLevelObjectiveDTO2.setSpec(simpleServiceLevelObjectiveSpec2);
    serviceLevelObjectiveV2Service.create(builderFactory.getProjectParams(), simpleServiceLevelObjectiveDTO2);
    simpleServiceLevelObjective2 = (SimpleServiceLevelObjective) serviceLevelObjectiveV2Service.getEntity(
        builderFactory.getProjectParams(), simpleServiceLevelObjectiveDTO2.getIdentifier());

    simpleServiceLevelObjectiveDTO3 =
        builderFactory.getSimpleServiceLevelObjectiveV2DTOBuilder().identifier("sloIdentifier3").build();
    SimpleServiceLevelObjectiveSpec simpleServiceLevelObjectiveSpec3 =
        (SimpleServiceLevelObjectiveSpec) simpleServiceLevelObjectiveDTO3.getSpec();
    ServiceLevelIndicatorDTO serviceLevelIndicatorDTO3 = builderFactory.getServiceLevelIndicatorDTO();
    WindowBasedServiceLevelIndicatorSpec windowBasedServiceLevelIndicatorSpec =
        (WindowBasedServiceLevelIndicatorSpec) serviceLevelIndicatorDTO3.getSpec();
    windowBasedServiceLevelIndicatorSpec.setSliMissingDataType(SLIMissingDataType.IGNORE);
    serviceLevelIndicatorDTO3.setSpec(windowBasedServiceLevelIndicatorSpec);
    simpleServiceLevelObjectiveSpec3.setServiceLevelIndicators(Collections.singletonList(serviceLevelIndicatorDTO3));
    simpleServiceLevelObjectiveDTO3.setSpec(simpleServiceLevelObjectiveSpec3);
    serviceLevelObjectiveV2Service.create(builderFactory.getProjectParams(), simpleServiceLevelObjectiveDTO3);
    simpleServiceLevelObjective3 = (SimpleServiceLevelObjective) serviceLevelObjectiveV2Service.getEntity(
        builderFactory.getProjectParams(), simpleServiceLevelObjectiveDTO3.getIdentifier());

    serviceLevelObjectiveV2DTO =
        builderFactory.getCompositeServiceLevelObjectiveV2DTOBuilder()
            .spec(CompositeServiceLevelObjectiveSpec.builder()
                      .serviceLevelObjectivesDetails(
                          Arrays.asList(ServiceLevelObjectiveDetailsDTO.builder()
                                            .serviceLevelObjectiveRef(simpleServiceLevelObjective1.getIdentifier())
                                            .weightagePercentage(75.0)
                                            .accountId(simpleServiceLevelObjective1.getAccountId())
                                            .orgIdentifier(simpleServiceLevelObjective1.getOrgIdentifier())
                                            .projectIdentifier(simpleServiceLevelObjective1.getProjectIdentifier())
                                            .build(),
                              ServiceLevelObjectiveDetailsDTO.builder()
                                  .serviceLevelObjectiveRef(simpleServiceLevelObjective2.getIdentifier())
                                  .weightagePercentage(25.0)
                                  .accountId(simpleServiceLevelObjective2.getAccountId())
                                  .orgIdentifier(simpleServiceLevelObjective2.getOrgIdentifier())
                                  .projectIdentifier(simpleServiceLevelObjective2.getProjectIdentifier())
                                  .build()))
                      .build())
            .build();
    serviceLevelObjectiveV2Service.create(builderFactory.getProjectParams(), serviceLevelObjectiveV2DTO);
    compositeServiceLevelObjective = (CompositeServiceLevelObjective) serviceLevelObjectiveV2Service.getEntity(
        builderFactory.getProjectParams(), serviceLevelObjectiveV2DTO.getIdentifier());

    serviceLevelObjectiveV2DTO =
        builderFactory.getCompositeServiceLevelObjectiveV2DTOBuilder()
            .identifier("compositeSloIdentifier2")
            .spec(CompositeServiceLevelObjectiveSpec.builder()
                      .serviceLevelObjectivesDetails(
                          Arrays.asList(ServiceLevelObjectiveDetailsDTO.builder()
                                            .serviceLevelObjectiveRef(simpleServiceLevelObjective1.getIdentifier())
                                            .weightagePercentage(75.0)
                                            .accountId(simpleServiceLevelObjective1.getAccountId())
                                            .orgIdentifier(simpleServiceLevelObjective1.getOrgIdentifier())
                                            .projectIdentifier(simpleServiceLevelObjective1.getProjectIdentifier())
                                            .build(),
                              ServiceLevelObjectiveDetailsDTO.builder()
                                  .serviceLevelObjectiveRef(simpleServiceLevelObjective3.getIdentifier())
                                  .weightagePercentage(25.0)
                                  .accountId(simpleServiceLevelObjective3.getAccountId())
                                  .orgIdentifier(simpleServiceLevelObjective3.getOrgIdentifier())
                                  .projectIdentifier(simpleServiceLevelObjective3.getProjectIdentifier())
                                  .build()))
                      .build())
            .build();
    serviceLevelObjectiveV2Service.create(builderFactory.getProjectParams(), serviceLevelObjectiveV2DTO);
    compositeServiceLevelObjective2 = (CompositeServiceLevelObjective) serviceLevelObjectiveV2Service.getEntity(
        builderFactory.getProjectParams(), serviceLevelObjectiveV2DTO.getIdentifier());

    serviceLevelObjectiveV2DTO =
        builderFactory.getCompositeServiceLevelObjectiveV2DTOBuilder()
            .identifier("leastPerformantCompositeServiceLevelObjectiveIdentifier")
            .spec(CompositeServiceLevelObjectiveSpec.builder()
                      .sloFormulaType(CompositeSLOFormulaType.LEAST_PERFORMANCE)
                      .serviceLevelObjectivesDetails(
                          Arrays.asList(ServiceLevelObjectiveDetailsDTO.builder()
                                            .serviceLevelObjectiveRef(simpleServiceLevelObjective1.getIdentifier())
                                            .weightagePercentage(100.0)
                                            .accountId(simpleServiceLevelObjective1.getAccountId())
                                            .orgIdentifier(simpleServiceLevelObjective1.getOrgIdentifier())
                                            .projectIdentifier(simpleServiceLevelObjective1.getProjectIdentifier())
                                            .build(),
                              ServiceLevelObjectiveDetailsDTO.builder()
                                  .serviceLevelObjectiveRef(simpleServiceLevelObjective2.getIdentifier())
                                  .weightagePercentage(30.0)
                                  .accountId(simpleServiceLevelObjective2.getAccountId())
                                  .orgIdentifier(simpleServiceLevelObjective2.getOrgIdentifier())
                                  .projectIdentifier(simpleServiceLevelObjective2.getProjectIdentifier())
                                  .build()))
                      .build())
            .build();
    serviceLevelObjectiveV2Service.create(builderFactory.getProjectParams(), serviceLevelObjectiveV2DTO);
    leastPerformantCompositeServiceLevelObjective =
        (CompositeServiceLevelObjective) serviceLevelObjectiveV2Service.getEntity(
            builderFactory.getProjectParams(), serviceLevelObjectiveV2DTO.getIdentifier());

    serviceLevelObjectiveV2DTO =
        builderFactory.getCompositeServiceLevelObjectiveV2DTOBuilder()
            .identifier("leastPerformantCompositeServiceLevelObjectiveIdentifier2")
            .spec(CompositeServiceLevelObjectiveSpec.builder()
                      .sloFormulaType(CompositeSLOFormulaType.LEAST_PERFORMANCE)
                      .serviceLevelObjectivesDetails(
                          Arrays.asList(ServiceLevelObjectiveDetailsDTO.builder()
                                            .serviceLevelObjectiveRef(simpleServiceLevelObjective1.getIdentifier())
                                            .weightagePercentage(100.0)
                                            .accountId(simpleServiceLevelObjective1.getAccountId())
                                            .orgIdentifier(simpleServiceLevelObjective1.getOrgIdentifier())
                                            .projectIdentifier(simpleServiceLevelObjective1.getProjectIdentifier())
                                            .build(),
                              ServiceLevelObjectiveDetailsDTO.builder()
                                  .serviceLevelObjectiveRef(simpleServiceLevelObjective3.getIdentifier())
                                  .weightagePercentage(30.0)
                                  .accountId(simpleServiceLevelObjective3.getAccountId())
                                  .orgIdentifier(simpleServiceLevelObjective3.getOrgIdentifier())
                                  .projectIdentifier(simpleServiceLevelObjective3.getProjectIdentifier())
                                  .build()))
                      .build())
            .build();
    serviceLevelObjectiveV2Service.create(builderFactory.getProjectParams(), serviceLevelObjectiveV2DTO);
    leastPerformantCompositeServiceLevelObjective2 =
        (CompositeServiceLevelObjective) serviceLevelObjectiveV2Service.getEntity(
            builderFactory.getProjectParams(), serviceLevelObjectiveV2DTO.getIdentifier());

    requestSimpleServiceLevelObjectiveDTO1 =
        builderFactory.getSimpleRequestServiceLevelObjectiveV2DTOBuilder().identifier("requestSloIdentifier").build();
    simpleServiceLevelObjectiveSpec1 =
        (SimpleServiceLevelObjectiveSpec) requestSimpleServiceLevelObjectiveDTO1.getSpec();
    simpleServiceLevelObjectiveSpec1.setMonitoredServiceRef(monitoredServiceDTO1.getIdentifier());
    simpleServiceLevelObjectiveSpec1.setHealthSourceRef(generateUuid());
    requestSimpleServiceLevelObjectiveDTO1.setSpec(simpleServiceLevelObjectiveSpec1);
    serviceLevelObjectiveV2Service.create(builderFactory.getProjectParams(), requestSimpleServiceLevelObjectiveDTO1);
    requestSimpleServiceLevelObjective1 = (SimpleServiceLevelObjective) serviceLevelObjectiveV2Service.getEntity(
        builderFactory.getProjectParams(), requestSimpleServiceLevelObjectiveDTO1.getIdentifier());

    requestSimpleServiceLevelObjectiveDTO2 =
        builderFactory.getSimpleRequestServiceLevelObjectiveV2DTOBuilder().identifier("requestSloIdentifier2").build();
    simpleServiceLevelObjectiveSpec2 =
        (SimpleServiceLevelObjectiveSpec) requestSimpleServiceLevelObjectiveDTO2.getSpec();
    simpleServiceLevelObjectiveSpec2.setMonitoredServiceRef(monitoredServiceDTO2.getIdentifier());
    simpleServiceLevelObjectiveSpec2.setHealthSourceRef(generateUuid());
    requestSimpleServiceLevelObjectiveDTO2.setSpec(simpleServiceLevelObjectiveSpec2);
    serviceLevelObjectiveV2Service.create(builderFactory.getProjectParams(), requestSimpleServiceLevelObjectiveDTO2);
    requestSimpleServiceLevelObjective2 = (SimpleServiceLevelObjective) serviceLevelObjectiveV2Service.getEntity(
        builderFactory.getProjectParams(), requestSimpleServiceLevelObjectiveDTO2.getIdentifier());

    requestServiceLevelObjectiveV2DTO =
        builderFactory.getCompositeServiceLevelObjectiveV2DTOBuilder()
            .identifier("requestCompositeSLOIdentifier")
            .spec(CompositeServiceLevelObjectiveSpec.builder()
                      .evaluationType(SLIEvaluationType.REQUEST)
                      .serviceLevelObjectivesDetails(Arrays.asList(
                          ServiceLevelObjectiveDetailsDTO.builder()
                              .serviceLevelObjectiveRef(requestSimpleServiceLevelObjective1.getIdentifier())
                              .weightagePercentage(75.0)
                              .accountId(requestSimpleServiceLevelObjective1.getAccountId())
                              .orgIdentifier(requestSimpleServiceLevelObjective1.getOrgIdentifier())
                              .projectIdentifier(requestSimpleServiceLevelObjective1.getProjectIdentifier())
                              .build(),
                          ServiceLevelObjectiveDetailsDTO.builder()
                              .serviceLevelObjectiveRef(requestSimpleServiceLevelObjective2.getIdentifier())
                              .weightagePercentage(25.0)
                              .accountId(requestSimpleServiceLevelObjective2.getAccountId())
                              .orgIdentifier(requestSimpleServiceLevelObjective2.getOrgIdentifier())
                              .projectIdentifier(requestSimpleServiceLevelObjective2.getProjectIdentifier())
                              .build()))
                      .build())
            .build();
    serviceLevelObjectiveV2Service.create(builderFactory.getProjectParams(), requestServiceLevelObjectiveV2DTO);
    requestCompositeServiceLevelObjective = (CompositeServiceLevelObjective) serviceLevelObjectiveV2Service.getEntity(
        builderFactory.getProjectParams(), requestServiceLevelObjectiveV2DTO.getIdentifier());

    verificationTaskId = compositeServiceLevelObjective.getUuid();
    leastPerformantVerificationTaskId = leastPerformantCompositeServiceLevelObjective.getUuid();
    requestVerificationTaskId = requestCompositeServiceLevelObjective.getUuid();
    startTime = TIME_FOR_TESTS.minus(10, ChronoUnit.MINUTES);
    endTime = TIME_FOR_TESTS.minus(5, ChronoUnit.MINUTES);
  }

  @Test
  @Owner(developers = VARSHA_LALWANI)
  @Category(UnitTests.class)
  public void testCreate_multipleSaves() {
    List<SLIState> sliStateList1 =
        Arrays.asList(SLIState.GOOD, SLIState.GOOD, SLIState.BAD, SLIState.NO_DATA, SLIState.BAD);
    List<SLIState> sliStateList2 =
        Arrays.asList(SLIState.BAD, SLIState.GOOD, SLIState.NO_DATA, SLIState.BAD, SLIState.GOOD);
    String sliId1 = serviceLevelIndicatorService
                        .getServiceLevelIndicator(builderFactory.getProjectParams(),
                            simpleServiceLevelObjective1.getServiceLevelIndicators().get(0))
                        .getUuid();
    String sliId2 = serviceLevelIndicatorService
                        .getServiceLevelIndicator(builderFactory.getProjectParams(),
                            simpleServiceLevelObjective2.getServiceLevelIndicators().get(0))
                        .getUuid();
    createSLIRecords(sliId1, sliStateList1);
    createSLIRecords(sliId2, sliStateList2);
    sloRecordService.create(compositeServiceLevelObjective, startTime, endTime, verificationTaskId);
    List<CompositeSLORecord> sloRecords = sloRecordService.getSLORecords(verificationTaskId, startTime, endTime);
    assertThat(sloRecords.size()).isEqualTo(5);
    assertThat(sloRecords.get(4).getRunningBadCount()).isEqualTo(2.0);
    assertThat(sloRecords.get(4).getRunningGoodCount()).isEqualTo(3.0);
  }

  @Test
  @Owner(developers = KARAN_SARASWAT)
  @Category(UnitTests.class)
  public void testCreate_multipleSaves_withIgnoreMissingDataType() {
    List<SLIState> sliStateList1 =
        Arrays.asList(SLIState.GOOD, SLIState.GOOD, SLIState.BAD, SLIState.NO_DATA, SLIState.BAD);

    String sliId1 = serviceLevelIndicatorService
                        .getServiceLevelIndicator(builderFactory.getProjectParams(),
                            simpleServiceLevelObjective1.getServiceLevelIndicators().get(0))
                        .getUuid();
    String sliId3 = serviceLevelIndicatorService
                        .getServiceLevelIndicator(builderFactory.getProjectParams(),
                            simpleServiceLevelObjective3.getServiceLevelIndicators().get(0))
                        .getUuid();
    createSLIRecords(sliId1, sliStateList1);
    createSLIRecords(sliId3, sliStateList3);
    String verificationTaskId = compositeServiceLevelObjective2.getUuid();
    sloRecordService.create(compositeServiceLevelObjective2, startTime, endTime, verificationTaskId);
    List<CompositeSLORecord> sloRecords = sloRecordService.getSLORecords(verificationTaskId, startTime, endTime);
    assertThat(sloRecords.size()).isEqualTo(5);
    assertThat(sloRecords.get(4).getRunningBadCount()).isEqualTo(0.0);
    assertThat(sloRecords.get(4).getRunningGoodCount()).isEqualTo(0.0);
  }

  @Test
  @Owner(developers = VARSHA_LALWANI)
  @Category(UnitTests.class)
  public void testCreateLeastPerformant_multipleSaves() {
    List<SLIState> sliStateList1 =
        Arrays.asList(SLIState.GOOD, SLIState.GOOD, SLIState.BAD, SLIState.NO_DATA, SLIState.BAD);
    List<SLIState> sliStateList2 =
        Arrays.asList(SLIState.BAD, SLIState.GOOD, SLIState.NO_DATA, SLIState.BAD, SLIState.GOOD);
    String sliId1 = serviceLevelIndicatorService
                        .getServiceLevelIndicator(builderFactory.getProjectParams(),
                            simpleServiceLevelObjective1.getServiceLevelIndicators().get(0))
                        .getUuid();
    String sliId2 = serviceLevelIndicatorService
                        .getServiceLevelIndicator(builderFactory.getProjectParams(),
                            simpleServiceLevelObjective2.getServiceLevelIndicators().get(0))
                        .getUuid();
    createSLIRecords(sliId1, sliStateList1);
    createSLIRecords(sliId2, sliStateList2);
    sloRecordService.create(
        leastPerformantCompositeServiceLevelObjective, startTime, endTime, leastPerformantVerificationTaskId);
    List<CompositeSLORecord> sloRecords =
        sloRecordService.getSLORecords(leastPerformantVerificationTaskId, startTime, endTime);
    assertThat(sloRecords.size()).isEqualTo(5);
    assertThat(sloRecords.get(4).getRunningBadCount()).isEqualTo(2.6, offset(0.001));
    assertThat(sloRecords.get(4).getRunningGoodCount()).isEqualTo(2.4, offset(0.001));
  }

  @Test
  @Owner(developers = KARAN_SARASWAT)
  @Category(UnitTests.class)
  public void testCreateLeastPerformant_multipleSaves_withIgnoreMissingDataType() {
    List<SLIState> sliStateList1 =
        Arrays.asList(SLIState.GOOD, SLIState.GOOD, SLIState.BAD, SLIState.NO_DATA, SLIState.BAD);

    String sliId1 = serviceLevelIndicatorService
                        .getServiceLevelIndicator(builderFactory.getProjectParams(),
                            simpleServiceLevelObjective1.getServiceLevelIndicators().get(0))
                        .getUuid();
    String sliId3 = serviceLevelIndicatorService
                        .getServiceLevelIndicator(builderFactory.getProjectParams(),
                            simpleServiceLevelObjective3.getServiceLevelIndicators().get(0))
                        .getUuid();
    createSLIRecords(sliId1, sliStateList1);
    createSLIRecords(sliId3, sliStateList3);
    String leastPerformantVerificationTaskId = leastPerformantCompositeServiceLevelObjective2.getUuid();
    sloRecordService.create(
        leastPerformantCompositeServiceLevelObjective2, startTime, endTime, leastPerformantVerificationTaskId);
    List<CompositeSLORecord> sloRecords =
        sloRecordService.getSLORecords(leastPerformantVerificationTaskId, startTime, endTime);
    assertThat(sloRecords.size()).isEqualTo(5);
    assertThat(sloRecords.get(4).getRunningBadCount()).isEqualTo(0.0, offset(0.001));
    assertThat(sloRecords.get(4).getRunningGoodCount()).isEqualTo(0.0, offset(0.001));
  }

  @Test
  @Owner(developers = ARPITJ)
  @Category(UnitTests.class)
  public void testCreate_multipleSaves_request() {
    List<SLIState> sliStateList1 =
        Arrays.asList(SLIState.GOOD, SLIState.GOOD, SLIState.GOOD, SLIState.GOOD, SLIState.GOOD);
    List<Long> goodCounts1 = Arrays.asList(100l, 200l, 300l, 0l, 100l);
    List<Long> badCounts1 = Arrays.asList(10l, 20l, 30l, 0l, 10l);

    List<SLIState> sliStateList2 =
        Arrays.asList(SLIState.GOOD, SLIState.GOOD, SLIState.GOOD, SLIState.GOOD, SLIState.GOOD);
    List<Long> goodCounts2 = Arrays.asList(100l, 200l, 300l, 0l, 100l);
    List<Long> badCounts2 = Arrays.asList(0l, 0l, 10l, 0l, 0l);

    String sliId1 = serviceLevelIndicatorService
                        .getServiceLevelIndicator(builderFactory.getProjectParams(),
                            requestSimpleServiceLevelObjective1.getServiceLevelIndicators().get(0))
                        .getUuid();
    String sliId2 = serviceLevelIndicatorService
                        .getServiceLevelIndicator(builderFactory.getProjectParams(),
                            requestSimpleServiceLevelObjective2.getServiceLevelIndicators().get(0))
                        .getUuid();
    createSLIRecords(sliId1, sliStateList1, goodCounts1, badCounts1);
    createSLIRecords(sliId2, sliStateList2, goodCounts2, badCounts2);
    sloRecordService.create(requestCompositeServiceLevelObjective, startTime, endTime, verificationTaskId);

    List<CompositeSLORecord> sloRecords = sloRecordService.getSLORecords(requestVerificationTaskId, startTime, endTime);
    assertThat(sloRecords.size()).isEqualTo(5);
    assertThat(sloRecords.get(4).getRunningBadCount()).isZero();
    assertThat(sloRecords.get(4).getRunningGoodCount()).isZero();
    assertThat(sloRecords.get(4).getScopedIdentifierSLIRecordMap().size()).isEqualTo(2);
    assertThat(sloRecords.get(4)
                   .getScopedIdentifierSLIRecordMap()
                   .get(serviceLevelObjectiveV2Service.getScopedIdentifier(
                       requestCompositeServiceLevelObjective.getServiceLevelObjectivesDetails().get(0)))
                   .getSliId())
        .isEqualTo(sliId1);
    assertThat(sloRecords.get(4)
                   .getScopedIdentifierSLIRecordMap()
                   .get(serviceLevelObjectiveV2Service.getScopedIdentifier(
                       requestCompositeServiceLevelObjective.getServiceLevelObjectivesDetails().get(0)))
                   .getRunningGoodCount())
        .isEqualTo(700);
  }

  @Test
  @Owner(developers = VARSHA_LALWANI)
  @Category(UnitTests.class)
  public void testCreate_SkipData() {
    List<SLIState> sliStateList1 =
        Arrays.asList(SLIState.GOOD, SLIState.GOOD, SLIState.BAD, SLIState.NO_DATA, SLIState.SKIP_DATA);
    List<SLIState> sliStateList2 =
        Arrays.asList(SLIState.BAD, SLIState.SKIP_DATA, SLIState.NO_DATA, SLIState.BAD, SLIState.GOOD);
    String sliId1 = serviceLevelIndicatorService
                        .getServiceLevelIndicator(builderFactory.getProjectParams(),
                            simpleServiceLevelObjective1.getServiceLevelIndicators().get(0))
                        .getUuid();
    String sliId2 = serviceLevelIndicatorService
                        .getServiceLevelIndicator(builderFactory.getProjectParams(),
                            simpleServiceLevelObjective2.getServiceLevelIndicators().get(0))
                        .getUuid();
    createSLIRecords(sliId1, sliStateList1);
    createSLIRecords(sliId2, sliStateList2);
    sloRecordService.create(compositeServiceLevelObjective, startTime, endTime, verificationTaskId);
    List<CompositeSLORecord> sloRecords = sloRecordService.getSLORecords(verificationTaskId, startTime, endTime);
    assertThat(sloRecords.size()).isEqualTo(5);
    assertThat(sloRecords.get(4).getRunningBadCount()).isEqualTo(1.25);
    assertThat(sloRecords.get(4).getRunningGoodCount()).isEqualTo(1.75);
  }

  @Test
  @Owner(developers = ARPITJ)
  @Category(UnitTests.class)
  public void testCreate_SkipData_request() {
    List<SLIState> sliStateList1 =
        Arrays.asList(SLIState.GOOD, SLIState.GOOD, SLIState.SKIP_DATA, SLIState.GOOD, SLIState.GOOD);
    List<Long> goodCounts1 = Arrays.asList(100l, 200l, 0l, 0l, 100l);
    List<Long> badCounts1 = Arrays.asList(10l, 20l, 0l, 0l, 10l);

    List<SLIState> sliStateList2 =
        Arrays.asList(SLIState.GOOD, SLIState.GOOD, SLIState.GOOD, SLIState.GOOD, SLIState.SKIP_DATA);
    List<Long> goodCounts2 = Arrays.asList(100l, 200l, 300l, 0l, 0l);
    List<Long> badCounts2 = Arrays.asList(0l, 0l, 10l, 0l, 0l);

    String sliId1 = serviceLevelIndicatorService
                        .getServiceLevelIndicator(builderFactory.getProjectParams(),
                            requestSimpleServiceLevelObjective1.getServiceLevelIndicators().get(0))
                        .getUuid();
    String sliId2 = serviceLevelIndicatorService
                        .getServiceLevelIndicator(builderFactory.getProjectParams(),
                            requestSimpleServiceLevelObjective2.getServiceLevelIndicators().get(0))
                        .getUuid();
    createSLIRecords(sliId1, sliStateList1, goodCounts1, badCounts1);
    createSLIRecords(sliId2, sliStateList2, goodCounts2, badCounts2);
    sloRecordService.create(requestCompositeServiceLevelObjective, startTime, endTime, verificationTaskId);
    List<CompositeSLORecord> sloRecords = sloRecordService.getSLORecords(requestVerificationTaskId, startTime, endTime);
    assertThat(sloRecords.size()).isEqualTo(3);
    assertThat(sloRecords.get(2).getRunningBadCount()).isEqualTo(0);
    assertThat(sloRecords.get(2).getRunningGoodCount()).isEqualTo(0);
    assertThat(sloRecords.get(2).getScopedIdentifierSLIRecordMap().size()).isEqualTo(2);
    assertThat(sloRecords.get(2)
                   .getScopedIdentifierSLIRecordMap()
                   .get(serviceLevelObjectiveV2Service.getScopedIdentifier(
                       requestCompositeServiceLevelObjective.getServiceLevelObjectivesDetails().get(0)))
                   .getSliId())
        .isEqualTo(sliId1);
    assertThat(sloRecords.get(2)
                   .getScopedIdentifierSLIRecordMap()
                   .get(serviceLevelObjectiveV2Service.getScopedIdentifier(
                       requestCompositeServiceLevelObjective.getServiceLevelObjectivesDetails().get(0)))
                   .getRunningGoodCount())
        .isEqualTo(300);
  }

  @Test
  @Owner(developers = VARSHA_LALWANI)
  @Category(UnitTests.class)
  public void testUpdate_completeOverlap() {
    List<Double> runningGoodCount = Arrays.asList(0.75, 1.75, 1.75, 2.5, 2.75);
    List<Double> runningBadCount = Arrays.asList(0.25, 0.25, 1.25, 1.5, 2.25);
    createSLORecords(startTime, endTime, runningGoodCount, runningBadCount, verificationTaskId);
    List<CompositeSLORecord> sloRecords = sloRecordService.getSLORecords(verificationTaskId, startTime, endTime);
    assertThat(sloRecords.size()).isEqualTo(5);
    assertThat(sloRecords.get(4).getRunningBadCount()).isEqualTo(2.25);
    assertThat(sloRecords.get(4).getRunningGoodCount()).isEqualTo(2.75);

    List<SLIState> sliStateList1 =
        Arrays.asList(SLIState.BAD, SLIState.BAD, SLIState.GOOD, SLIState.NO_DATA, SLIState.GOOD);
    List<SLIState> sliStateList2 =
        Arrays.asList(SLIState.GOOD, SLIState.GOOD, SLIState.NO_DATA, SLIState.BAD, SLIState.BAD);
    String sliId1 = serviceLevelIndicatorService
                        .getServiceLevelIndicator(builderFactory.getProjectParams(),
                            simpleServiceLevelObjective1.getServiceLevelIndicators().get(0))
                        .getUuid();
    String sliId2 = serviceLevelIndicatorService
                        .getServiceLevelIndicator(builderFactory.getProjectParams(),
                            simpleServiceLevelObjective2.getServiceLevelIndicators().get(0))
                        .getUuid();
    createSLIRecords(sliId1, sliStateList1);
    createSLIRecords(sliId2, sliStateList2);
    sloRecordService.create(compositeServiceLevelObjective, startTime, endTime, verificationTaskId);
    List<CompositeSLORecord> sloRecords1 = sloRecordService.getSLORecords(verificationTaskId, startTime, endTime);
    assertThat(sloRecords1.size()).isEqualTo(5);
    assertThat(sloRecords1.get(4).getRunningBadCount()).isEqualTo(2.0);
    assertThat(sloRecords1.get(4).getRunningGoodCount()).isEqualTo(3.0);
    assertThat(sloRecords1.get(4).getSloVersion()).isEqualTo(0);
  }

  @Test
  @Owner(developers = KARAN_SARASWAT)
  @Category(UnitTests.class)
  public void testUpdate_completeOverlap_withIgnoreMissingDataType() {
    String verificationTaskId = compositeServiceLevelObjective2.getUuid();
    List<Double> runningGoodCount = Arrays.asList(0.75, 1.75, 1.75, 2.5, 2.75);
    List<Double> runningBadCount = Arrays.asList(0.25, 0.25, 1.25, 1.5, 2.25);
    createSLORecords(startTime, endTime, runningGoodCount, runningBadCount, verificationTaskId);
    List<CompositeSLORecord> sloRecords = sloRecordService.getSLORecords(verificationTaskId, startTime, endTime);
    assertThat(sloRecords.size()).isEqualTo(5);
    assertThat(sloRecords.get(4).getRunningBadCount()).isEqualTo(2.25);
    assertThat(sloRecords.get(4).getRunningGoodCount()).isEqualTo(2.75);

    List<SLIState> sliStateList1 =
        Arrays.asList(SLIState.BAD, SLIState.BAD, SLIState.GOOD, SLIState.NO_DATA, SLIState.GOOD);
    String sliId1 = serviceLevelIndicatorService
                        .getServiceLevelIndicator(builderFactory.getProjectParams(),
                            simpleServiceLevelObjective1.getServiceLevelIndicators().get(0))
                        .getUuid();
    String sliId3 = serviceLevelIndicatorService
                        .getServiceLevelIndicator(builderFactory.getProjectParams(),
                            simpleServiceLevelObjective3.getServiceLevelIndicators().get(0))
                        .getUuid();
    createSLIRecords(sliId1, sliStateList1);
    createSLIRecords(sliId3, sliStateList3);
    sloRecordService.create(compositeServiceLevelObjective2, startTime, endTime, verificationTaskId);
    List<CompositeSLORecord> sloRecords1 = sloRecordService.getSLORecords(verificationTaskId, startTime, endTime);
    assertThat(sloRecords1.size()).isEqualTo(5);
    assertThat(sloRecords1.get(4).getRunningBadCount()).isEqualTo(0.0);
    assertThat(sloRecords1.get(4).getRunningGoodCount()).isEqualTo(0.0);
    assertThat(sloRecords1.get(4).getSloVersion()).isEqualTo(0);
  }

  @Test
  @Owner(developers = VARSHA_LALWANI)
  @Category(UnitTests.class)
  public void testUpdateLeastPerformant_completeOverlap() {
    List<Double> runningGoodCount = Arrays.asList(0.75, 1.75, 1.75, 2.5, 2.75);
    List<Double> runningBadCount = Arrays.asList(0.25, 0.25, 1.25, 1.5, 2.25);
    createSLORecords(startTime, endTime, runningGoodCount, runningBadCount, leastPerformantVerificationTaskId);
    List<CompositeSLORecord> sloRecords =
        sloRecordService.getSLORecords(leastPerformantVerificationTaskId, startTime, endTime);
    assertThat(sloRecords.size()).isEqualTo(5);
    assertThat(sloRecords.get(4).getRunningBadCount()).isEqualTo(2.25);
    assertThat(sloRecords.get(4).getRunningGoodCount()).isEqualTo(2.75);

    List<SLIState> sliStateList1 =
        Arrays.asList(SLIState.BAD, SLIState.BAD, SLIState.GOOD, SLIState.NO_DATA, SLIState.GOOD);
    List<SLIState> sliStateList2 =
        Arrays.asList(SLIState.BAD, SLIState.GOOD, SLIState.NO_DATA, SLIState.BAD, SLIState.BAD);
    String sliId1 = serviceLevelIndicatorService
                        .getServiceLevelIndicator(builderFactory.getProjectParams(),
                            simpleServiceLevelObjective1.getServiceLevelIndicators().get(0))
                        .getUuid();
    String sliId2 = serviceLevelIndicatorService
                        .getServiceLevelIndicator(builderFactory.getProjectParams(),
                            simpleServiceLevelObjective2.getServiceLevelIndicators().get(0))
                        .getUuid();
    createSLIRecords(sliId1, sliStateList1);
    createSLIRecords(sliId2, sliStateList2);
    sloRecordService.create(
        leastPerformantCompositeServiceLevelObjective, startTime, endTime, leastPerformantVerificationTaskId);
    List<CompositeSLORecord> sloRecords1 =
        sloRecordService.getSLORecords(leastPerformantVerificationTaskId, startTime, endTime);
    assertThat(sloRecords1.size()).isEqualTo(5);
    assertThat(sloRecords1.get(4).getRunningBadCount()).isEqualTo(2.6, offset(0.01));
    assertThat(sloRecords1.get(4).getRunningGoodCount()).isEqualTo(2.4, offset(0.01));
    assertThat(sloRecords1.get(4).getSloVersion()).isEqualTo(0);
  }

  @Test
  @Owner(developers = KARAN_SARASWAT)
  @Category(UnitTests.class)
  public void testUpdateLeastPerformant_completeOverlap_withIgnoreMissingDataType() {
    String leastPerformantVerificationTaskId = leastPerformantCompositeServiceLevelObjective2.getUuid();
    List<Double> runningGoodCount = Arrays.asList(0.75, 1.75, 1.75, 2.5, 2.75);
    List<Double> runningBadCount = Arrays.asList(0.25, 0.25, 1.25, 1.5, 2.25);
    createSLORecords(startTime, endTime, runningGoodCount, runningBadCount, leastPerformantVerificationTaskId);
    List<CompositeSLORecord> sloRecords =
        sloRecordService.getSLORecords(leastPerformantVerificationTaskId, startTime, endTime);
    assertThat(sloRecords.size()).isEqualTo(5);
    assertThat(sloRecords.get(4).getRunningBadCount()).isEqualTo(2.25);
    assertThat(sloRecords.get(4).getRunningGoodCount()).isEqualTo(2.75);

    List<SLIState> sliStateList1 =
        Arrays.asList(SLIState.BAD, SLIState.BAD, SLIState.GOOD, SLIState.NO_DATA, SLIState.GOOD);
    String sliId1 = serviceLevelIndicatorService
                        .getServiceLevelIndicator(builderFactory.getProjectParams(),
                            simpleServiceLevelObjective1.getServiceLevelIndicators().get(0))
                        .getUuid();
    String sliId3 = serviceLevelIndicatorService
                        .getServiceLevelIndicator(builderFactory.getProjectParams(),
                            simpleServiceLevelObjective3.getServiceLevelIndicators().get(0))
                        .getUuid();
    createSLIRecords(sliId1, sliStateList1);
    createSLIRecords(sliId3, sliStateList3);
    sloRecordService.create(
        leastPerformantCompositeServiceLevelObjective2, startTime, endTime, leastPerformantVerificationTaskId);
    List<CompositeSLORecord> sloRecords1 =
        sloRecordService.getSLORecords(leastPerformantVerificationTaskId, startTime, endTime);
    assertThat(sloRecords1.size()).isEqualTo(5);
    assertThat(sloRecords1.get(4).getRunningBadCount()).isEqualTo(0.0, offset(0.001));
    assertThat(sloRecords1.get(4).getRunningGoodCount()).isEqualTo(0.0, offset(0.001));
    assertThat(sloRecords1.get(4).getSloVersion()).isEqualTo(0);
  }

  @Test
  @Owner(developers = ARPITJ)
  @Category(UnitTests.class)
  public void testUpdate_completeOverlap_request() {
    Map<String, SLIRecord> scopedIdentifierToSLIRecordMap = new HashMap<>();
    createSLORecords(startTime, endTime, scopedIdentifierToSLIRecordMap);
    List<CompositeSLORecord> sloRecords = sloRecordService.getSLORecords(requestVerificationTaskId, startTime, endTime);
    assertThat(sloRecords.size()).isEqualTo(5);
    assertThat(sloRecords.get(4).getRunningBadCount()).isEqualTo(0);
    assertThat(sloRecords.get(4).getRunningGoodCount()).isEqualTo(0);
    assertThat(Objects.isNull(sloRecords.get(4).getScopedIdentifierSLIRecordMap())).isEqualTo(true);

    List<SLIState> sliStateList1 =
        Arrays.asList(SLIState.GOOD, SLIState.GOOD, SLIState.GOOD, SLIState.GOOD, SLIState.GOOD);
    List<Long> goodCounts1 = Arrays.asList(100l, 200l, 0l, 0l, 100l);
    List<Long> badCounts1 = Arrays.asList(10l, 20l, 0l, 0l, 10l);

    List<SLIState> sliStateList2 =
        Arrays.asList(SLIState.GOOD, SLIState.GOOD, SLIState.GOOD, SLIState.GOOD, SLIState.GOOD);
    List<Long> goodCounts2 = Arrays.asList(100l, 200l, 300l, 0l, 0l);
    List<Long> badCounts2 = Arrays.asList(0l, 0l, 10l, 0l, 0l);

    String sliId1 = serviceLevelIndicatorService
                        .getServiceLevelIndicator(builderFactory.getProjectParams(),
                            requestSimpleServiceLevelObjective1.getServiceLevelIndicators().get(0))
                        .getUuid();
    String sliId2 = serviceLevelIndicatorService
                        .getServiceLevelIndicator(builderFactory.getProjectParams(),
                            requestSimpleServiceLevelObjective2.getServiceLevelIndicators().get(0))
                        .getUuid();
    createSLIRecords(sliId1, sliStateList1, goodCounts1, badCounts1);
    createSLIRecords(sliId2, sliStateList2, goodCounts2, badCounts2);

    sloRecordService.create(requestCompositeServiceLevelObjective, startTime, endTime, verificationTaskId);
    List<CompositeSLORecord> sloRecords1 =
        sloRecordService.getSLORecords(requestVerificationTaskId, startTime, endTime);
    assertThat(sloRecords1.size()).isEqualTo(5);
    assertThat(sloRecords1.get(2).getRunningBadCount()).isEqualTo(0);
    assertThat(sloRecords1.get(2).getRunningGoodCount()).isEqualTo(0);
    assertThat(sloRecords1.get(2).getScopedIdentifierSLIRecordMap().size()).isEqualTo(2);
    assertThat(sloRecords1.get(2)
                   .getScopedIdentifierSLIRecordMap()
                   .get(serviceLevelObjectiveV2Service.getScopedIdentifier(
                       requestCompositeServiceLevelObjective.getServiceLevelObjectivesDetails().get(0)))
                   .getSliId())
        .isEqualTo(sliId1);
    assertThat(sloRecords1.get(2)
                   .getScopedIdentifierSLIRecordMap()
                   .get(serviceLevelObjectiveV2Service.getScopedIdentifier(
                       requestCompositeServiceLevelObjective.getServiceLevelObjectivesDetails().get(0)))
                   .getRunningGoodCount())
        .isEqualTo(300);
  }

  @Test
  @Owner(developers = VARSHA_LALWANI)
  @Category(UnitTests.class)
  public void testUpdate_partialOverlap() {
    List<Double> runningGoodCount = Arrays.asList(0.75, 1.75, 1.75);
    List<Double> runningBadCount = Arrays.asList(0.25, 0.25, 1.25);
    createSLORecords(startTime, endTime.minusSeconds(120), runningGoodCount, runningBadCount, verificationTaskId);
    List<CompositeSLORecord> sloRecords = sloRecordService.getSLORecords(verificationTaskId, startTime, endTime);
    assertThat(sloRecords.size()).isEqualTo(3);
    assertThat(sloRecords.get(2).getRunningBadCount()).isEqualTo(1.25);
    assertThat(sloRecords.get(2).getRunningGoodCount()).isEqualTo(1.75);

    List<SLIState> sliStateList1 =
        Arrays.asList(SLIState.BAD, SLIState.BAD, SLIState.GOOD, SLIState.NO_DATA, SLIState.GOOD);
    List<SLIState> sliStateList2 =
        Arrays.asList(SLIState.GOOD, SLIState.GOOD, SLIState.NO_DATA, SLIState.BAD, SLIState.BAD);
    String sliId1 = serviceLevelIndicatorService
                        .getServiceLevelIndicator(builderFactory.getProjectParams(),
                            simpleServiceLevelObjective1.getServiceLevelIndicators().get(0))
                        .getUuid();
    String sliId2 = serviceLevelIndicatorService
                        .getServiceLevelIndicator(builderFactory.getProjectParams(),
                            simpleServiceLevelObjective2.getServiceLevelIndicators().get(0))
                        .getUuid();
    createSLIRecords(sliId1, sliStateList1);
    createSLIRecords(sliId2, sliStateList2);
    sloRecordService.create(compositeServiceLevelObjective, startTime, endTime, verificationTaskId);
    List<CompositeSLORecord> sloRecords1 = sloRecordService.getSLORecords(verificationTaskId, startTime, endTime);
    assertThat(sloRecords1.size()).isEqualTo(5);
    assertThat(sloRecords1.get(4).getRunningBadCount()).isEqualTo(2.0);
    assertThat(sloRecords1.get(4).getRunningGoodCount()).isEqualTo(3.0);
    assertThat(sloRecords1.get(4).getSloVersion()).isEqualTo(0);
  }

  @Test
  @Owner(developers = KARAN_SARASWAT)
  @Category(UnitTests.class)
  public void testUpdate_partialOverlap_withIgnoreMissingDataType() {
    String verificationTaskId = compositeServiceLevelObjective2.getUuid();
    List<Double> runningGoodCount = Arrays.asList(0.75, 1.75, 1.75);
    List<Double> runningBadCount = Arrays.asList(0.25, 0.25, 1.25);
    createSLORecords(startTime, endTime.minusSeconds(120), runningGoodCount, runningBadCount, verificationTaskId);
    List<CompositeSLORecord> sloRecords = sloRecordService.getSLORecords(verificationTaskId, startTime, endTime);
    assertThat(sloRecords.size()).isEqualTo(3);
    assertThat(sloRecords.get(2).getRunningBadCount()).isEqualTo(1.25);
    assertThat(sloRecords.get(2).getRunningGoodCount()).isEqualTo(1.75);

    List<SLIState> sliStateList1 =
        Arrays.asList(SLIState.BAD, SLIState.BAD, SLIState.GOOD, SLIState.NO_DATA, SLIState.GOOD);
    String sliId1 = serviceLevelIndicatorService
                        .getServiceLevelIndicator(builderFactory.getProjectParams(),
                            simpleServiceLevelObjective1.getServiceLevelIndicators().get(0))
                        .getUuid();
    String sliId3 = serviceLevelIndicatorService
                        .getServiceLevelIndicator(builderFactory.getProjectParams(),
                            simpleServiceLevelObjective3.getServiceLevelIndicators().get(0))
                        .getUuid();
    createSLIRecords(sliId1, sliStateList1);
    createSLIRecords(sliId3, sliStateList3);
    sloRecordService.create(compositeServiceLevelObjective2, startTime, endTime, verificationTaskId);
    List<CompositeSLORecord> sloRecords1 = sloRecordService.getSLORecords(verificationTaskId, startTime, endTime);
    assertThat(sloRecords1.size()).isEqualTo(5);
    assertThat(sloRecords1.get(4).getRunningBadCount()).isEqualTo(0.0);
    assertThat(sloRecords1.get(4).getRunningGoodCount()).isEqualTo(0.0);
    assertThat(sloRecords1.get(4).getSloVersion()).isEqualTo(0);
  }

  @Test
  @Owner(developers = VARSHA_LALWANI)
  @Category(UnitTests.class)
  public void testUpdateLeastPerformant_partialOverlap() {
    List<Double> runningGoodCount = Arrays.asList(0.75, 1.75, 1.75);
    List<Double> runningBadCount = Arrays.asList(0.25, 0.25, 1.25);
    createSLORecords(
        startTime, endTime.minusSeconds(120), runningGoodCount, runningBadCount, leastPerformantVerificationTaskId);
    List<CompositeSLORecord> sloRecords =
        sloRecordService.getSLORecords(leastPerformantVerificationTaskId, startTime, endTime);
    assertThat(sloRecords.size()).isEqualTo(3);
    assertThat(sloRecords.get(2).getRunningBadCount()).isEqualTo(1.25);
    assertThat(sloRecords.get(2).getRunningGoodCount()).isEqualTo(1.75);

    List<SLIState> sliStateList1 =
        Arrays.asList(SLIState.BAD, SLIState.BAD, SLIState.GOOD, SLIState.SKIP_DATA, SLIState.GOOD);
    List<SLIState> sliStateList2 =
        Arrays.asList(SLIState.GOOD, SLIState.BAD, SLIState.NO_DATA, SLIState.BAD, SLIState.BAD);
    String sliId1 = serviceLevelIndicatorService
                        .getServiceLevelIndicator(builderFactory.getProjectParams(),
                            simpleServiceLevelObjective1.getServiceLevelIndicators().get(0))
                        .getUuid();
    String sliId2 = serviceLevelIndicatorService
                        .getServiceLevelIndicator(builderFactory.getProjectParams(),
                            simpleServiceLevelObjective2.getServiceLevelIndicators().get(0))
                        .getUuid();
    createSLIRecords(sliId1, sliStateList1);
    createSLIRecords(sliId2, sliStateList2);
    sloRecordService.create(
        leastPerformantCompositeServiceLevelObjective, startTime, endTime, leastPerformantVerificationTaskId);
    List<CompositeSLORecord> sloRecords1 =
        sloRecordService.getSLORecords(leastPerformantVerificationTaskId, startTime, endTime);
    assertThat(sloRecords1.size()).isEqualTo(5);
    assertThat(sloRecords1.get(4).getRunningBadCount()).isEqualTo(2.3, offset(0.01));
    assertThat(sloRecords1.get(4).getRunningGoodCount()).isEqualTo(1.7, offset(0.01));
    assertThat(sloRecords1.get(4).getSloVersion()).isEqualTo(0);
  }

  @Test
  @Owner(developers = KARAN_SARASWAT)
  @Category(UnitTests.class)
  public void testUpdateLeastPerformant_partialOverlap_withIgnoreMissingDataType() {
    String leastPerformantVerificationTaskId = leastPerformantCompositeServiceLevelObjective2.getUuid();
    List<Double> runningGoodCount = Arrays.asList(0.75, 1.75, 1.75);
    List<Double> runningBadCount = Arrays.asList(0.25, 0.25, 1.25);
    createSLORecords(
        startTime, endTime.minusSeconds(120), runningGoodCount, runningBadCount, leastPerformantVerificationTaskId);
    List<CompositeSLORecord> sloRecords =
        sloRecordService.getSLORecords(leastPerformantVerificationTaskId, startTime, endTime);
    assertThat(sloRecords.size()).isEqualTo(3);
    assertThat(sloRecords.get(2).getRunningBadCount()).isEqualTo(1.25);
    assertThat(sloRecords.get(2).getRunningGoodCount()).isEqualTo(1.75);

    List<SLIState> sliStateList1 =
        Arrays.asList(SLIState.BAD, SLIState.BAD, SLIState.GOOD, SLIState.SKIP_DATA, SLIState.GOOD);
    String sliId1 = serviceLevelIndicatorService
                        .getServiceLevelIndicator(builderFactory.getProjectParams(),
                            simpleServiceLevelObjective1.getServiceLevelIndicators().get(0))
                        .getUuid();
    String sliId3 = serviceLevelIndicatorService
                        .getServiceLevelIndicator(builderFactory.getProjectParams(),
                            simpleServiceLevelObjective3.getServiceLevelIndicators().get(0))
                        .getUuid();
    createSLIRecords(sliId1, sliStateList1);
    createSLIRecords(sliId3, sliStateList3);
    sloRecordService.create(
        leastPerformantCompositeServiceLevelObjective2, startTime, endTime, leastPerformantVerificationTaskId);
    List<CompositeSLORecord> sloRecords1 =
        sloRecordService.getSLORecords(leastPerformantVerificationTaskId, startTime, endTime);
    assertThat(sloRecords1.size()).isEqualTo(5);
    assertThat(sloRecords1.get(4).getRunningBadCount()).isEqualTo(0.0, offset(0.01));
    assertThat(sloRecords1.get(4).getRunningGoodCount()).isEqualTo(0.0, offset(0.01));
    assertThat(sloRecords1.get(4).getSloVersion()).isEqualTo(0);
  }

  @Test
  @Owner(developers = ARPITJ)
  @Category(UnitTests.class)
  public void testUpdate_partialOverlap_request() {
    Map<String, SLIRecord> scopedIdentifierToSLIRecordMap = new HashMap<>();
    createSLORecords(startTime, endTime.minusSeconds(120), scopedIdentifierToSLIRecordMap);
    List<CompositeSLORecord> sloRecords = sloRecordService.getSLORecords(requestVerificationTaskId, startTime, endTime);
    assertThat(sloRecords.size()).isEqualTo(3);
    assertThat(sloRecords.get(2).getRunningBadCount()).isEqualTo(0);
    assertThat(sloRecords.get(2).getRunningGoodCount()).isEqualTo(0);
    assertThat(Objects.isNull(sloRecords.get(2).getScopedIdentifierSLIRecordMap())).isEqualTo(true);

    List<SLIState> sliStateList1 =
        Arrays.asList(SLIState.BAD, SLIState.BAD, SLIState.GOOD, SLIState.GOOD, SLIState.GOOD);
    List<Long> goodCounts1 = Arrays.asList(100l, 200l, 0l, 0l, 100l);
    List<Long> badCounts1 = Arrays.asList(10l, 20l, 0l, 0l, 10l);
    List<SLIState> sliStateList2 =
        Arrays.asList(SLIState.GOOD, SLIState.GOOD, SLIState.GOOD, SLIState.BAD, SLIState.BAD);
    List<Long> goodCounts2 = Arrays.asList(100l, 200l, 300l, 0l, 0l);
    List<Long> badCounts2 = Arrays.asList(0l, 0l, 10l, 0l, 0l);
    String sliId1 = serviceLevelIndicatorService
                        .getServiceLevelIndicator(builderFactory.getProjectParams(),
                            requestSimpleServiceLevelObjective1.getServiceLevelIndicators().get(0))
                        .getUuid();
    String sliId2 = serviceLevelIndicatorService
                        .getServiceLevelIndicator(builderFactory.getProjectParams(),
                            requestSimpleServiceLevelObjective2.getServiceLevelIndicators().get(0))
                        .getUuid();
    createSLIRecords(sliId1, sliStateList1, goodCounts1, badCounts1);
    createSLIRecords(sliId2, sliStateList2, goodCounts2, badCounts2);
    sloRecordService.create(requestCompositeServiceLevelObjective, startTime, endTime, verificationTaskId);
    List<CompositeSLORecord> sloRecords1 =
        sloRecordService.getSLORecords(requestVerificationTaskId, startTime, endTime);
    assertThat(sloRecords1.size()).isEqualTo(5);
    assertThat(sloRecords1.get(4).getRunningBadCount()).isEqualTo(0.0);
    assertThat(sloRecords1.get(4).getRunningGoodCount()).isEqualTo(0.0);
    assertThat(sloRecords1.get(4).getSloVersion()).isEqualTo(0);
    assertThat(sloRecords1.get(2)
                   .getScopedIdentifierSLIRecordMap()
                   .get(serviceLevelObjectiveV2Service.getScopedIdentifier(
                       requestCompositeServiceLevelObjective.getServiceLevelObjectivesDetails().get(0)))
                   .getSliId())
        .isEqualTo(sliId1);
    assertThat(sloRecords1.get(2)
                   .getScopedIdentifierSLIRecordMap()
                   .get(serviceLevelObjectiveV2Service.getScopedIdentifier(
                       requestCompositeServiceLevelObjective.getServiceLevelObjectivesDetails().get(0)))
                   .getRunningGoodCount())
        .isEqualTo(300);
  }

  @Test
  @Owner(developers = VARSHA_LALWANI)
  @Category(UnitTests.class)
  public void testUpdate_duplicateRecords() {
    List<SLIState> sliStateList1 =
        Arrays.asList(SLIState.GOOD, SLIState.GOOD, SLIState.BAD, SLIState.NO_DATA, SLIState.BAD);
    List<SLIState> sliStateList2 =
        Arrays.asList(SLIState.BAD, SLIState.GOOD, SLIState.NO_DATA, SLIState.BAD, SLIState.GOOD);
    String sliId1 = serviceLevelIndicatorService
                        .getServiceLevelIndicator(builderFactory.getProjectParams(),
                            simpleServiceLevelObjective1.getServiceLevelIndicators().get(0))
                        .getUuid();
    String sliId2 = serviceLevelIndicatorService
                        .getServiceLevelIndicator(builderFactory.getProjectParams(),
                            simpleServiceLevelObjective2.getServiceLevelIndicators().get(0))
                        .getUuid();
    createSLIRecords(sliId1, sliStateList1);
    createSLIRecords(sliId2, sliStateList2);
    sloRecordService.create(compositeServiceLevelObjective, startTime, endTime, verificationTaskId);
    List<CompositeSLORecord> sloRecords =
        sloRecordService.getSLORecords(compositeServiceLevelObjective.getUuid(), startTime, endTime);
    assertThat(sloRecords.size()).isEqualTo(5);
    assertThat(sloRecords.get(4).getRunningBadCount()).isEqualTo(2.0);
    assertThat(sloRecords.get(4).getRunningGoodCount()).isEqualTo(3.0);

    // Insert duplicates
    sloRecords = sloRecordService.getLatestCountSLORecords(compositeServiceLevelObjective.getUuid(), 5);
    assertThat(sloRecords.size()).isEqualTo(5);
    int count = 0;
    for (CompositeSLORecord sloRecord : sloRecords) {
      sloRecord.setUuid(generateUuid());
      sloRecord.setRunningBadCount(0);
      sloRecord.setRunningGoodCount(count);
      count += 1;
    }
    hPersistence.saveBatch(sloRecords);

    // Create again
    sloRecordService.create(compositeServiceLevelObjective, startTime, endTime, verificationTaskId);
    sloRecords = sloRecordService.getSLORecords(
        compositeServiceLevelObjective.getUuid(), endTime.minus(1, ChronoUnit.MINUTES), endTime);
    assertThat(sloRecords.size()).isEqualTo(2);
    sloRecords = sloRecords.stream()
                     .sorted(Comparator.comparingLong(CompositeSLORecord::getLastUpdatedAt).reversed())
                     .collect(Collectors.toList());
    CompositeSLORecord updatedLastRecord = sloRecords.get(0);
    assertThat(updatedLastRecord.getRunningBadCount()).isEqualTo(2.0);
    assertThat(updatedLastRecord.getRunningGoodCount()).isEqualTo(3.0);
  }

  private List<SLIRecord> createSLIRecords(String sliId, List<SLIState> states) {
    int index = 0;
    List<SLIRecord> sliRecords = new ArrayList<>();
    for (Instant instant = startTime; instant.isBefore(endTime); instant = instant.plus(1, ChronoUnit.MINUTES)) {
      SLIRecord sliRecord = SLIRecord.builder()
                                .verificationTaskId(sliId)
                                .sliId(sliId)
                                .version(0)
                                .sliState(states.get(index))
                                .runningBadCount(0)
                                .runningGoodCount(1)
                                .sliVersion(0)
                                .timestamp(instant)
                                .build();
      sliRecords.add(sliRecord);
      index++;
    }
    hPersistence.saveBatch(sliRecords);
    return sliRecords;
  }

  private List<SLIRecord> createSLIRecords(
      String sliId, List<SLIState> states, List<Long> goodCounts, List<Long> badCounts) {
    int index = 0;
    List<SLIRecord> sliRecords = new ArrayList<>();
    long runningGoodCount = 0;
    long runningBadCount = 0;
    for (Instant instant = startTime; instant.isBefore(endTime); instant = instant.plus(1, ChronoUnit.MINUTES)) {
      runningGoodCount += goodCounts.get(index);
      runningBadCount += badCounts.get(index);
      SLIRecord sliRecord = SLIRecord.builder()
                                .verificationTaskId(sliId)
                                .sliId(sliId)
                                .version(0)
                                .sliState(states.get(index))
                                .runningBadCount(runningBadCount)
                                .runningGoodCount(runningGoodCount)
                                .sliVersion(0)
                                .timestamp(instant)
                                .build();
      sliRecords.add(sliRecord);
      index++;
    }
    hPersistence.saveBatch(sliRecords);
    return sliRecords;
  }

  private List<CompositeSLORecord> createSLORecords(Instant start, Instant end, List<Double> runningGoodCount,
      List<Double> runningBadCount, String verificationTaskId) {
    int index = 0;
    List<CompositeSLORecord> sloRecords = new ArrayList<>();
    for (Instant instant = start; instant.isBefore(end); instant = instant.plus(1, ChronoUnit.MINUTES)) {
      CompositeSLORecord sloRecord = CompositeSLORecord.builder()
                                         .verificationTaskId(verificationTaskId)
                                         .sloId(verificationTaskId)
                                         .version(0)
                                         .runningBadCount(runningBadCount.get(index))
                                         .runningGoodCount(runningGoodCount.get(index))
                                         .sloVersion(0)
                                         .timestamp(instant)
                                         .build();
      sloRecords.add(sloRecord);
      index++;
    }
    hPersistence.save(sloRecords);
    return sloRecords;
  }

  private List<CompositeSLORecord> createSLORecords(Instant start, Instant end, Map<String, SLIRecord> test) {
    int index = 0;
    List<CompositeSLORecord> sloRecords = new ArrayList<>();
    for (Instant instant = start; instant.isBefore(end); instant = instant.plus(1, ChronoUnit.MINUTES)) {
      CompositeSLORecord sloRecord = CompositeSLORecord.builder()
                                         .verificationTaskId(requestVerificationTaskId)
                                         .sloId(requestCompositeServiceLevelObjective.getUuid())
                                         .version(0)
                                         .runningBadCount(0)
                                         .runningGoodCount(0)
                                         .sloVersion(0)
                                         .scopedIdentifierSLIRecordMap(test)
                                         .timestamp(instant)
                                         .build();
      sloRecords.add(sloRecord);
      index++;
    }
    hPersistence.save(sloRecords);
    return sloRecords;
  }
}
