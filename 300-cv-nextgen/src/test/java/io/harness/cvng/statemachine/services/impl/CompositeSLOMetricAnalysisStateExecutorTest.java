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
import io.harness.cvng.servicelevelobjective.beans.ServiceLevelObjectiveDetailsDTO;
import io.harness.cvng.servicelevelobjective.beans.ServiceLevelObjectiveV2DTO;
import io.harness.cvng.servicelevelobjective.beans.slospec.CompositeServiceLevelObjectiveSpec;
import io.harness.cvng.servicelevelobjective.beans.slospec.SimpleServiceLevelObjectiveSpec;
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
import io.harness.cvng.statemachine.entities.CompositeSLOMetricAnalysisState;
import io.harness.cvng.statemachine.services.api.AnalysisStateExecutor;
import io.harness.persistence.HPersistence;
import io.harness.rule.Owner;

import com.google.inject.Inject;
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
import org.mongodb.morphia.query.UpdateOperations;

public class CompositeSLOMetricAnalysisStateExecutorTest extends CvNextGenTestBase {
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
  private CompositeSLOMetricAnalysisState sloMetricAnalysisState;
  ServiceLevelObjectiveV2DTO serviceLevelObjectiveV2DTO;
  CompositeServiceLevelObjective compositeServiceLevelObjective;
  ServiceLevelObjectiveV2DTO simpleServiceLevelObjectiveDTO1;
  ServiceLevelObjectiveV2DTO simpleServiceLevelObjectiveDTO2;
  SimpleServiceLevelObjective simpleServiceLevelObjective1;
  SimpleServiceLevelObjective simpleServiceLevelObjective2;

  @Before
  public void setup() throws IllegalAccessException {
    MockitoAnnotations.initMocks(this);

    builderFactory = BuilderFactory.getDefault();
    sloMetricAnalysisStateExecutor = stateTypeAnalysisStateExecutorMap.get(StateType.COMPOSOITE_SLO_METRIC_ANALYSIS);

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
    startTime = TIME_FOR_TESTS.minus(10, ChronoUnit.MINUTES);
    endTime = TIME_FOR_TESTS.minus(5, ChronoUnit.MINUTES);
    compositeServiceLevelObjective = (CompositeServiceLevelObjective) serviceLevelObjectiveV2Service.getEntity(
        builderFactory.getProjectParams(), serviceLevelObjectiveV2DTO.getIdentifier());
    UpdateOperations<CompositeServiceLevelObjective> updateOperations =
        hPersistence.createUpdateOperations(CompositeServiceLevelObjective.class)
            .set(AbstractServiceLevelObjective.ServiceLevelObjectiveV2Keys.startedAt, startTime.toEpochMilli());
    compositeServiceLevelObjective = (CompositeServiceLevelObjective) serviceLevelObjectiveV2Service.getEntity(
        builderFactory.getProjectParams(), serviceLevelObjectiveV2DTO.getIdentifier());
    verificationTaskId = compositeServiceLevelObjective.getUuid();
    hPersistence.update(compositeServiceLevelObjective, updateOperations);
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
    generateSLIRecords(sliId1, sliId2);
    sloMetricAnalysisState = CompositeSLOMetricAnalysisState.builder().build();
    sloMetricAnalysisState.setInputs(input);
  }

  @Test
  @Owner(developers = VARSHA_LALWANI)
  @Category(UnitTests.class)
  public void testExecute() {
    verificationTaskService.createCompositeSLOVerificationTask(
        builderFactory.getContext().getAccountId(), compositeServiceLevelObjective.getUuid(), new HashMap<>());
    sloMetricAnalysisState =
        (CompositeSLOMetricAnalysisState) sloMetricAnalysisStateExecutor.execute(sloMetricAnalysisState);
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
