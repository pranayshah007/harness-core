/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.statemachine.services.impl;

import static io.harness.cvng.CVNGTestConstants.TIME_FOR_TESTS;
import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.rule.OwnerRule.VARSHA_LALWANI;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CvNextGenTestBase;
import io.harness.category.element.UnitTests;
import io.harness.cvng.BuilderFactory;
import io.harness.cvng.core.beans.monitoredService.MonitoredServiceDTO;
import io.harness.cvng.core.services.api.VerificationTaskService;
import io.harness.cvng.core.services.api.monitoredService.MonitoredServiceService;
import io.harness.cvng.servicelevelobjective.beans.ErrorBudgetRisk;
import io.harness.cvng.servicelevelobjective.beans.SLIMetricType;
import io.harness.cvng.servicelevelobjective.beans.SLIMissingDataType;
import io.harness.cvng.servicelevelobjective.beans.ServiceLevelObjectiveDetailsDTO;
import io.harness.cvng.servicelevelobjective.beans.ServiceLevelObjectiveV2DTO;
import io.harness.cvng.servicelevelobjective.beans.slimetricspec.RatioSLIMetricEventType;
import io.harness.cvng.servicelevelobjective.beans.slimetricspec.RatioSLIMetricSpec;
import io.harness.cvng.servicelevelobjective.beans.slimetricspec.ThresholdType;
import io.harness.cvng.servicelevelobjective.beans.slospec.CompositeServiceLevelObjectiveSpec;
import io.harness.cvng.servicelevelobjective.beans.slospec.SimpleServiceLevelObjectiveSpec;
import io.harness.cvng.servicelevelobjective.beans.slotargetspec.WindowBasedServiceLevelIndicatorSpec;
import io.harness.cvng.servicelevelobjective.entities.AbstractServiceLevelObjective;
import io.harness.cvng.servicelevelobjective.entities.CompositeSLORecord;
import io.harness.cvng.servicelevelobjective.entities.CompositeSLORecord.CompositeSLORecordKeys;
import io.harness.cvng.servicelevelobjective.entities.CompositeServiceLevelObjective;
import io.harness.cvng.servicelevelobjective.entities.SLIRecord;
import io.harness.cvng.servicelevelobjective.entities.SLIRecord.SLIRecordKeys;
import io.harness.cvng.servicelevelobjective.entities.SLIRecord.SLIState;
import io.harness.cvng.servicelevelobjective.entities.SLOHealthIndicator;
import io.harness.cvng.servicelevelobjective.entities.SimpleServiceLevelObjective;
import io.harness.cvng.servicelevelobjective.services.api.ServiceLevelIndicatorService;
import io.harness.cvng.servicelevelobjective.services.api.ServiceLevelObjectiveV2Service;
import io.harness.cvng.servicelevelobjective.services.impl.SLOHealthIndicatorServiceImpl;
import io.harness.cvng.statemachine.beans.AnalysisInput;
import io.harness.cvng.statemachine.beans.AnalysisState.StateType;
import io.harness.cvng.statemachine.beans.AnalysisStatus;
import io.harness.cvng.statemachine.entities.CompositeSLORestoreMetricAnalysisState;
import io.harness.cvng.statemachine.services.api.AnalysisStateExecutor;
import io.harness.persistence.HPersistence;
import io.harness.rule.Owner;

import com.google.inject.Inject;
import dev.morphia.query.UpdateOperations;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.MockitoAnnotations;

public class CompositeSLORestoreMetricAnalysisStateExecutorTest extends CvNextGenTestBase {
  @Inject Map<StateType, AnalysisStateExecutor> stateTypeAnalysisStateExecutorMap;
  @Inject private HPersistence hPersistence;
  @Inject private ServiceLevelObjectiveV2Service serviceLevelObjectiveV2Service;
  @Inject private MonitoredServiceService monitoredServiceService;
  @Inject private SLOHealthIndicatorServiceImpl sloHealthIndicatorService;
  @Inject ServiceLevelIndicatorService serviceLevelIndicatorService;
  @Inject VerificationTaskService verificationTaskService;
  BuilderFactory builderFactory;
  private Instant startTime;
  private Instant endTime;
  private String verificationTaskId;

  AnalysisStateExecutor sloMetricAnalysisStateExecutor;
  private CompositeSLORestoreMetricAnalysisState sloMetricAnalysisState;
  ServiceLevelObjectiveV2DTO serviceLevelObjectiveV2DTO;

  ServiceLevelObjectiveV2DTO serviceLevelObjectiveV2DTOWithConsecutiveMinutes;

  CompositeServiceLevelObjective compositeServiceLevelObjective;

  CompositeServiceLevelObjective compositeServiceLevelObjectiveWithConsecutiveMinutes;

  ServiceLevelObjectiveV2DTO simpleServiceLevelObjectiveDTO1;
  ServiceLevelObjectiveV2DTO simpleServiceLevelObjectiveDTO2;
  ServiceLevelObjectiveV2DTO simpleServiceLevelObjectiveDTO3;

  ServiceLevelObjectiveV2DTO simpleServiceLevelObjectiveDTO4;

  SimpleServiceLevelObjective simpleServiceLevelObjective1;
  SimpleServiceLevelObjective simpleServiceLevelObjective2;
  SimpleServiceLevelObjective simpleServiceLevelObjective3;

  SimpleServiceLevelObjective simpleServiceLevelObjective4;

  @Before
  public void setup() throws IllegalAccessException {
    MockitoAnnotations.initMocks(this);

    builderFactory = BuilderFactory.getDefault();
    builderFactory.getContext().setOrgIdentifier("orgIdentifier");
    builderFactory.getContext().setProjectIdentifier("project");
    sloMetricAnalysisStateExecutor =
        stateTypeAnalysisStateExecutorMap.get(StateType.COMPOSOITE_SLO_RESTORE_METRIC_ANALYSIS);

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

    MonitoredServiceDTO monitoredServiceDTO3 = builderFactory.monitoredServiceDTOBuilder()
                                                   .sources(MonitoredServiceDTO.Sources.builder().build())
                                                   .serviceRef("service2")
                                                   .environmentRef("env2")
                                                   .identifier("service2_env2")
                                                   .build();
    monitoredServiceService.create(builderFactory.getContext().getAccountId(), monitoredServiceDTO3);
    simpleServiceLevelObjectiveDTO3 =
        builderFactory.getSimpleServiceLevelObjectiveV2DTOBuilder().identifier("sloIdentifier3").build();
    SimpleServiceLevelObjectiveSpec simpleServiceLevelObjectiveSpec3 =
        (SimpleServiceLevelObjectiveSpec) simpleServiceLevelObjectiveDTO3.getSpec();
    simpleServiceLevelObjectiveSpec3.setMonitoredServiceRef(monitoredServiceDTO3.getIdentifier());
    simpleServiceLevelObjectiveSpec3.setHealthSourceRef(generateUuid());
    WindowBasedServiceLevelIndicatorSpec windowBasedServiceLevelIndicatorSpec =
        WindowBasedServiceLevelIndicatorSpec.builder()
            .sliMissingDataType(SLIMissingDataType.GOOD)
            .type(SLIMetricType.RATIO)
            .spec(RatioSLIMetricSpec.builder()
                      .thresholdType(ThresholdType.GREATER_THAN)
                      .thresholdValue(20.0)
                      .eventType(RatioSLIMetricEventType.GOOD)
                      .metric1("metric1")
                      .metric2("metric2")
                      .considerConsecutiveMinutes(2)
                      .considerAllConsecutiveMinutesFromStartAsBad(true)
                      .build())
            .build();
    simpleServiceLevelObjectiveSpec3.getServiceLevelIndicators().get(0).setSpec(windowBasedServiceLevelIndicatorSpec);
    simpleServiceLevelObjectiveSpec3.getServiceLevelIndicators().get(0).setIdentifier("sli_identifier");
    simpleServiceLevelObjectiveDTO3.setSpec(simpleServiceLevelObjectiveSpec3);
    serviceLevelObjectiveV2Service.create(builderFactory.getProjectParams(), simpleServiceLevelObjectiveDTO3);
    simpleServiceLevelObjective3 = (SimpleServiceLevelObjective) serviceLevelObjectiveV2Service.getEntity(
        builderFactory.getProjectParams(), simpleServiceLevelObjectiveDTO3.getIdentifier());

    simpleServiceLevelObjectiveDTO4 =
        builderFactory.getSimpleServiceLevelObjectiveV2DTOBuilder().identifier("sloIdentifier4").build();
    SimpleServiceLevelObjectiveSpec simpleServiceLevelObjectiveSpec4 =
        (SimpleServiceLevelObjectiveSpec) simpleServiceLevelObjectiveDTO4.getSpec();
    simpleServiceLevelObjectiveSpec4.setMonitoredServiceRef(monitoredServiceDTO1.getIdentifier());
    simpleServiceLevelObjectiveSpec4.setHealthSourceRef(generateUuid());
    windowBasedServiceLevelIndicatorSpec = WindowBasedServiceLevelIndicatorSpec.builder()
                                               .sliMissingDataType(SLIMissingDataType.GOOD)
                                               .type(SLIMetricType.RATIO)
                                               .spec(RatioSLIMetricSpec.builder()
                                                         .thresholdType(ThresholdType.GREATER_THAN)
                                                         .thresholdValue(20.0)
                                                         .eventType(RatioSLIMetricEventType.GOOD)
                                                         .metric1("metric1")
                                                         .metric2("metric2")
                                                         .considerConsecutiveMinutes(3)
                                                         .considerAllConsecutiveMinutesFromStartAsBad(true)
                                                         .build())
                                               .build();
    simpleServiceLevelObjectiveSpec4.getServiceLevelIndicators().get(0).setSpec(windowBasedServiceLevelIndicatorSpec);
    simpleServiceLevelObjectiveSpec4.getServiceLevelIndicators().get(0).setIdentifier("sli_identifier2");
    simpleServiceLevelObjectiveDTO4.setSpec(simpleServiceLevelObjectiveSpec4);
    serviceLevelObjectiveV2Service.create(builderFactory.getProjectParams(), simpleServiceLevelObjectiveDTO4);
    simpleServiceLevelObjective4 = (SimpleServiceLevelObjective) serviceLevelObjectiveV2Service.getEntity(
        builderFactory.getProjectParams(), simpleServiceLevelObjectiveDTO4.getIdentifier());

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

    serviceLevelObjectiveV2DTOWithConsecutiveMinutes =
        builderFactory.getCompositeServiceLevelObjectiveV2DTOBuilder()
            .identifier("compositeSloIdentifier2")
            .spec(CompositeServiceLevelObjectiveSpec.builder()
                      .serviceLevelObjectivesDetails(
                          Arrays.asList(ServiceLevelObjectiveDetailsDTO.builder()
                                            .serviceLevelObjectiveRef(simpleServiceLevelObjectiveDTO3.getIdentifier())
                                            .weightagePercentage(75.0)
                                            .accountId(simpleServiceLevelObjective3.getAccountId())
                                            .orgIdentifier(simpleServiceLevelObjective3.getOrgIdentifier())
                                            .projectIdentifier(simpleServiceLevelObjective3.getProjectIdentifier())
                                            .build(),
                              ServiceLevelObjectiveDetailsDTO.builder()
                                  .serviceLevelObjectiveRef(simpleServiceLevelObjective4.getIdentifier())
                                  .weightagePercentage(25.0)
                                  .accountId(simpleServiceLevelObjective4.getAccountId())
                                  .orgIdentifier(simpleServiceLevelObjective4.getOrgIdentifier())
                                  .projectIdentifier(simpleServiceLevelObjective4.getProjectIdentifier())
                                  .build()))
                      .build())
            .build();

    serviceLevelObjectiveV2Service.create(builderFactory.getProjectParams(), serviceLevelObjectiveV2DTO);
    serviceLevelObjectiveV2Service.create(
        builderFactory.getProjectParams(), serviceLevelObjectiveV2DTOWithConsecutiveMinutes);

    startTime = TIME_FOR_TESTS.minus(10, ChronoUnit.MINUTES);
    endTime = TIME_FOR_TESTS.minus(5, ChronoUnit.MINUTES);
    compositeServiceLevelObjective = (CompositeServiceLevelObjective) serviceLevelObjectiveV2Service.getEntity(
        builderFactory.getProjectParams(), serviceLevelObjectiveV2DTO.getIdentifier());
    compositeServiceLevelObjectiveWithConsecutiveMinutes =
        (CompositeServiceLevelObjective) serviceLevelObjectiveV2Service.getEntity(
            builderFactory.getProjectParams(), serviceLevelObjectiveV2DTOWithConsecutiveMinutes.getIdentifier());
    UpdateOperations<CompositeServiceLevelObjective> updateOperations =
        hPersistence.createUpdateOperations(CompositeServiceLevelObjective.class)
            .set(AbstractServiceLevelObjective.ServiceLevelObjectiveV2Keys.startedAt, startTime.toEpochMilli());
    compositeServiceLevelObjective = (CompositeServiceLevelObjective) serviceLevelObjectiveV2Service.getEntity(
        builderFactory.getProjectParams(), serviceLevelObjectiveV2DTO.getIdentifier());
    compositeServiceLevelObjectiveWithConsecutiveMinutes =
        (CompositeServiceLevelObjective) serviceLevelObjectiveV2Service.getEntity(
            builderFactory.getProjectParams(), serviceLevelObjectiveV2DTOWithConsecutiveMinutes.getIdentifier());
    verificationTaskId = compositeServiceLevelObjective.getUuid();
    hPersistence.update(compositeServiceLevelObjective, updateOperations);
    hPersistence.update(compositeServiceLevelObjectiveWithConsecutiveMinutes, updateOperations);
    AnalysisInput input =
        AnalysisInput.builder().verificationTaskId(verificationTaskId).startTime(startTime).endTime(endTime).build();

    String sliId1 = serviceLevelIndicatorService
                        .getServiceLevelIndicator(builderFactory.getProjectParams(),
                            simpleServiceLevelObjective1.getServiceLevelIndicators().get(0))
                        .getUuid();
    String sliId2 = serviceLevelIndicatorService
                        .getServiceLevelIndicator(builderFactory.getProjectParams(),
                            simpleServiceLevelObjective2.getServiceLevelIndicators().get(0))
                        .getUuid();
    String sliId3 = serviceLevelIndicatorService
                        .getServiceLevelIndicator(builderFactory.getProjectParams(),
                            simpleServiceLevelObjective3.getServiceLevelIndicators().get(0))
                        .getUuid();
    String sliId4 = serviceLevelIndicatorService
                        .getServiceLevelIndicator(builderFactory.getProjectParams(),
                            simpleServiceLevelObjective4.getServiceLevelIndicators().get(0))
                        .getUuid();
    generateSLIRecords(sliId1, sliId2);
    generateSLIRecords(sliId3, sliId4);
    sloMetricAnalysisState = CompositeSLORestoreMetricAnalysisState.builder().build();
    sloMetricAnalysisState.setInputs(input);
  }

  @Test
  @Owner(developers = VARSHA_LALWANI)
  @Category(UnitTests.class)
  public void testExecute() {
    verificationTaskService.createCompositeSLOVerificationTask(
        builderFactory.getContext().getAccountId(), compositeServiceLevelObjective.getUuid(), new HashMap<>());
    sloMetricAnalysisState =
        (CompositeSLORestoreMetricAnalysisState) sloMetricAnalysisStateExecutor.execute(sloMetricAnalysisState);
    List<CompositeSLORecord> sloRecordList =
        hPersistence.createQuery(CompositeSLORecord.class)
            .filter(CompositeSLORecordKeys.sloId, compositeServiceLevelObjective.getUuid())
            .field(SLIRecordKeys.timestamp)
            .greaterThanOrEq(startTime)
            .field(SLIRecordKeys.timestamp)
            .lessThan(endTime)
            .asList();
    assertThat(sloMetricAnalysisState.getStatus().name()).isEqualTo(AnalysisStatus.SUCCESS.name());
    assertThat(sloRecordList.size()).isEqualTo(5);
    SLOHealthIndicator sloHealthIndicator = sloHealthIndicatorService.getBySLOIdentifier(
        builderFactory.getProjectParams(), serviceLevelObjectiveV2DTO.getIdentifier());
    assertThat(sloHealthIndicator.getErrorBudgetRemainingPercentage()).isEqualTo(863800.00 / 8640);
    assertThat(sloHealthIndicator.getErrorBudgetRemainingMinutes()).isEqualTo(8638);
    assertThat(sloHealthIndicator.getErrorBudgetRisk()).isEqualTo(ErrorBudgetRisk.HEALTHY);
  }

  @Test
  @Owner(developers = VARSHA_LALWANI)
  @Category(UnitTests.class)
  public void testExecuteWithConsecutiveMinutes() {
    verificationTaskId = compositeServiceLevelObjectiveWithConsecutiveMinutes.getUuid();
    AnalysisInput input =
        AnalysisInput.builder().verificationTaskId(verificationTaskId).startTime(startTime).endTime(endTime).build();
    sloMetricAnalysisState.setInputs(input);
    verificationTaskService.createCompositeSLOVerificationTask(builderFactory.getContext().getAccountId(),
        compositeServiceLevelObjectiveWithConsecutiveMinutes.getUuid(), new HashMap<>());
    sloMetricAnalysisState =
        (CompositeSLORestoreMetricAnalysisState) sloMetricAnalysisStateExecutor.execute(sloMetricAnalysisState);
    List<CompositeSLORecord> sloRecordList =
        hPersistence.createQuery(CompositeSLORecord.class)
            .filter(CompositeSLORecordKeys.sloId, compositeServiceLevelObjectiveWithConsecutiveMinutes.getUuid())
            .field(SLIRecordKeys.timestamp)
            .greaterThanOrEq(startTime)
            .field(SLIRecordKeys.timestamp)
            .lessThan(endTime)
            .asList();
    assertThat(sloMetricAnalysisState.getStatus().name()).isEqualTo(AnalysisStatus.SUCCESS.name());
    assertThat(sloRecordList.size()).isEqualTo(3);
    SLOHealthIndicator sloHealthIndicator = sloHealthIndicatorService.getBySLOIdentifier(
        builderFactory.getProjectParams(), serviceLevelObjectiveV2DTOWithConsecutiveMinutes.getIdentifier());
    assertThat(sloHealthIndicator.getErrorBudgetRemainingPercentage()).isEqualTo(863900.00 / 8640);
    assertThat(sloHealthIndicator.getErrorBudgetRemainingMinutes()).isEqualTo(8639);
    assertThat(sloHealthIndicator.getErrorBudgetRisk()).isEqualTo(ErrorBudgetRisk.HEALTHY);
  }

  private void generateSLIRecords(String sliId1, String sliId2) {
    List<SLIRecord> sliRecordList = new ArrayList<>();
    List<SLIState> sliStateList1 =
        Arrays.asList(SLIState.GOOD, SLIState.GOOD, SLIState.BAD, SLIState.NO_DATA, SLIState.BAD);
    List<SLIState> sliStateList2 =
        Arrays.asList(SLIState.BAD, SLIState.GOOD, SLIState.NO_DATA, SLIState.BAD, SLIState.GOOD);
    int index = 0;
    for (Instant instant = startTime; instant.isBefore(endTime); instant = instant.plus(1, ChronoUnit.MINUTES)) {
      SLIRecord sliRecord1 = SLIRecord.builder()
                                 .verificationTaskId(verificationTaskId)
                                 .sliId(sliId1)
                                 .version(0)
                                 .sliState(sliStateList1.get(index))
                                 .runningBadCount(0)
                                 .runningGoodCount(1)
                                 .sliVersion(0)
                                 .timestamp(instant)
                                 .build();
      sliRecordList.add(sliRecord1);
      SLIRecord sliRecord2 = SLIRecord.builder()
                                 .verificationTaskId(verificationTaskId)
                                 .sliId(sliId2)
                                 .version(0)
                                 .sliState(sliStateList2.get(index))
                                 .runningBadCount(0)
                                 .runningGoodCount(1)
                                 .sliVersion(0)
                                 .timestamp(instant)
                                 .build();
      sliRecordList.add(sliRecord2);
      index++;
    }
    hPersistence.save(sliRecordList);
  }
}
