/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.jobs;

import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.rule.OwnerRule.KAMAL;
import static io.harness.rule.OwnerRule.NAVEEN;
import static io.harness.rule.OwnerRule.VUK;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CvNextGenTestBase;
import io.harness.agent.sdk.HarnessAlwaysRun;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.cvng.BuilderFactory;
import io.harness.cvng.VerificationApplication;
import io.harness.cvng.beans.DataSourceType;
import io.harness.cvng.beans.MonitoredServiceType;
import io.harness.cvng.core.beans.monitoredService.MonitoredServiceDTO;
import io.harness.cvng.core.beans.monitoredService.MonitoredServiceDTO.MonitoredServiceDTOBuilder;
import io.harness.cvng.core.beans.params.MonitoredServiceParams;
import io.harness.cvng.core.beans.params.ProjectParams;
import io.harness.cvng.core.entities.CVConfig;
import io.harness.cvng.core.entities.MetricPack;
import io.harness.cvng.core.entities.TimeSeriesThreshold;
import io.harness.cvng.core.services.api.CVConfigService;
import io.harness.cvng.core.services.api.MetricPackService;
import io.harness.cvng.core.services.api.monitoredService.MonitoredServiceService;
import io.harness.cvng.servicelevelobjective.beans.ServiceLevelObjectiveV2DTO;
import io.harness.cvng.servicelevelobjective.services.api.ServiceLevelObjectiveV2Service;
import io.harness.eventsframework.entity_crud.project.ProjectEntityChangeDTO;
import io.harness.persistence.PersistentEntity;
import io.harness.reflection.HarnessReflections;
import io.harness.rule.Owner;

import com.google.inject.Inject;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.commons.lang3.StringUtils;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.MockitoAnnotations;
@OwnedBy(HarnessTeam.CV)
public class ProjectChangeEventMessageProcessorTest extends CvNextGenTestBase {
  @Inject private ProjectChangeEventMessageProcessor projectChangeEventMessageProcessor;
  @Inject private CVConfigService cvConfigService;
  @Inject private MetricPackService metricPackService;
  @Inject private MonitoredServiceService monitoredServiceService;
  @Inject private ServiceLevelObjectiveV2Service serviceLevelObjectiveV2Service;
  private BuilderFactory builderFactory;

  @Before
  public void setup() throws IllegalAccessException {
    MockitoAnnotations.initMocks(this);
    builderFactory = BuilderFactory.getDefault();
  }

  @Test
  @Owner(developers = KAMAL)
  @Category(UnitTests.class)
  public void testProcessDeleteAction() {
    String accountId = generateUuid();
    String orgIdentifier = generateUuid();
    CVConfig cvConfig1 = createCVConfig(accountId, orgIdentifier, "project1");
    CVConfig cvConfig2 = createCVConfig(accountId, orgIdentifier, "project2");
    cvConfigService.save(cvConfig1);
    cvConfigService.save(cvConfig2);
    projectChangeEventMessageProcessor.processDeleteAction(ProjectEntityChangeDTO.newBuilder()
                                                               .setAccountIdentifier(accountId)
                                                               .setOrgIdentifier(orgIdentifier)
                                                               .setIdentifier("project1")
                                                               .build());
    assertThat(cvConfigService.get(cvConfig1.getUuid())).isNull();
    assertThat(cvConfigService.get(cvConfig2.getUuid())).isNotNull();

    // For every message processing, idemptotency is assumed - Redelivery of a message produces the same result and
    // there are no side effects
    CVConfig retrievedCVConfig1 = cvConfigService.get(cvConfig1.getUuid());
    CVConfig retrievedCVConfig2 = cvConfigService.get(cvConfig2.getUuid());

    projectChangeEventMessageProcessor.processDeleteAction(ProjectEntityChangeDTO.newBuilder()
                                                               .setAccountIdentifier(accountId)
                                                               .setOrgIdentifier(orgIdentifier)
                                                               .setIdentifier("project1")
                                                               .build());
    assertThat(retrievedCVConfig1).isNull();
    assertThat(retrievedCVConfig2).isNotNull();
  }

  @Test
  @Owner(developers = VUK)
  @Category(UnitTests.class)
  public void testProcessDeleteActionWithoutProjectIdentifier() {
    String accountId = generateUuid();
    String orgIdentifier = generateUuid();
    CVConfig cvConfig1 = createCVConfig(accountId, orgIdentifier, "project1");
    CVConfig cvConfig2 = createCVConfig(accountId, orgIdentifier, "project2");
    cvConfigService.save(cvConfig1);
    cvConfigService.save(cvConfig2);
    projectChangeEventMessageProcessor.processDeleteAction(
        ProjectEntityChangeDTO.newBuilder().setAccountIdentifier(accountId).setOrgIdentifier(orgIdentifier).build());
    assertThat(cvConfigService.get(cvConfig1.getUuid())).isNotNull();
    assertThat(cvConfigService.get(cvConfig2.getUuid())).isNotNull();
  }

  @Test
  @Owner(developers = NAVEEN)
  @Category(UnitTests.class)
  public void testProcessDeleteAction_monitoredServices() {
    String accountId = generateUuid();
    String orgIdentifier = generateUuid();
    MonitoredServiceDTO monitoredServiceDTO1 = createMonitoredServiceDTO(orgIdentifier, "project1");
    MonitoredServiceDTO monitoredServiceDTO2 = createMonitoredServiceDTO(orgIdentifier, "project2");

    monitoredServiceService.create(accountId, monitoredServiceDTO1);
    monitoredServiceService.create(accountId, monitoredServiceDTO2);

    projectChangeEventMessageProcessor.processDeleteAction(ProjectEntityChangeDTO.newBuilder()
                                                               .setAccountIdentifier(accountId)
                                                               .setOrgIdentifier(orgIdentifier)
                                                               .setIdentifier("project1")
                                                               .build());

    MonitoredServiceParams monitoredServiceParams1 =
        MonitoredServiceParams.builder()
            .monitoredServiceIdentifier(monitoredServiceDTO1.getIdentifier())
            .orgIdentifier(orgIdentifier)
            .projectIdentifier("project1")
            .accountIdentifier(accountId)
            .build();
    MonitoredServiceParams monitoredServiceParams2 =
        MonitoredServiceParams.builder()
            .monitoredServiceIdentifier(monitoredServiceDTO1.getIdentifier())
            .orgIdentifier(orgIdentifier)
            .projectIdentifier("project2")
            .accountIdentifier(accountId)
            .build();

    assertThat(monitoredServiceService.getMonitoredServiceDTO(monitoredServiceParams1)).isNull();
    assertThat(monitoredServiceService.getMonitoredServiceDTO(monitoredServiceParams2)).isNull();
  }

  @Test
  @Owner(developers = NAVEEN)
  @Category(UnitTests.class)
  public void testProcessDeleteAction_serviceLevelObjective() {
    ServiceLevelObjectiveV2DTO sloDTO = builderFactory.getSimpleServiceLevelObjectiveV2DTOBuilder().build();
    MonitoredServiceDTO monitoredServiceDTO = builderFactory.monitoredServiceDTOBuilder().build();
    monitoredServiceDTO.setSources(MonitoredServiceDTO.Sources.builder().build());
    String accountId = builderFactory.getContext().getAccountId();
    String orgIdentifier = builderFactory.getContext().getOrgIdentifier();
    String projectIdentifier = builderFactory.getContext().getProjectIdentifier();
    ProjectParams projectParams = ProjectParams.builder()
                                      .accountIdentifier(accountId)
                                      .orgIdentifier(orgIdentifier)
                                      .projectIdentifier(projectIdentifier)
                                      .build();
    monitoredServiceService.create(accountId, monitoredServiceDTO);
    serviceLevelObjectiveV2Service.create(projectParams, sloDTO);

    projectChangeEventMessageProcessor.processDeleteAction(ProjectEntityChangeDTO.newBuilder()
                                                               .setAccountIdentifier(accountId)
                                                               .setOrgIdentifier(orgIdentifier)
                                                               .setIdentifier(projectIdentifier)
                                                               .build());
    assertThat(serviceLevelObjectiveV2Service.getByMonitoredServiceIdentifier(
                   projectParams, monitoredServiceDTO.getIdentifier()))
        .isEmpty();
    MonitoredServiceParams monitoredServiceParams = MonitoredServiceParams.builder()
                                                        .monitoredServiceIdentifier(monitoredServiceDTO.getIdentifier())
                                                        .projectIdentifier(projectIdentifier)
                                                        .orgIdentifier(orgIdentifier)
                                                        .accountIdentifier(accountId)
                                                        .build();
    assertThat(monitoredServiceService.getMonitoredServiceDTO(monitoredServiceParams)).isNull();
  }

  @Test
  @Owner(developers = KAMAL)
  @Category(UnitTests.class)
  public void testProcessCreateAction() throws IllegalAccessException {
    String accountId = generateUuid();
    String orgIdentifier = generateUuid();
    String projectIdentifier = generateUuid();
    projectChangeEventMessageProcessor.processCreateAction(ProjectEntityChangeDTO.newBuilder()
                                                               .setAccountIdentifier(accountId)
                                                               .setOrgIdentifier(orgIdentifier)
                                                               .setIdentifier(projectIdentifier)
                                                               .build());

    List<MetricPack> metricPacks =
        metricPackService.getMetricPacks(accountId, orgIdentifier, projectIdentifier, DataSourceType.APP_DYNAMICS);
    assertThat(metricPacks).isNotEmpty();

    List<TimeSeriesThreshold> metricPackThresholds = metricPackService.getMetricPackThresholds(
        accountId, orgIdentifier, projectIdentifier, metricPacks.get(0).getIdentifier(), DataSourceType.APP_DYNAMICS);

    assertThat(metricPackThresholds).isNotEmpty();
    assertThat(metricPackThresholds).size().isEqualTo(2);
  }

  @Test
  @Owner(developers = KAMAL)
  @Category(UnitTests.class)
  @HarnessAlwaysRun
  public void testProcessDeleteAction_entitiesList() {
    Set<Class<? extends PersistentEntity>> entitiesWithVerificationTaskId = new HashSet<>();
    entitiesWithVerificationTaskId.addAll(ProjectChangeEventMessageProcessor.ENTITIES_MAP.keySet());
    Set<Class<? extends PersistentEntity>> reflections =
        HarnessReflections.get()
            .getSubTypesOf(PersistentEntity.class)
            .stream()
            .filter(klazz
                -> StringUtils.startsWithAny(
                    klazz.getPackage().getName(), VerificationApplication.class.getPackage().getName()))
            .collect(Collectors.toSet());
    Set<Class<? extends PersistentEntity>> withProjectIdentifier = new HashSet<>();
    reflections.forEach(entity -> {
      if (doesClassContainField(entity, "accountId") && doesClassContainField(entity, "orgIdentifier")
          && doesClassContainField(entity, "projectIdentifier")
          && !OrganizationChangeEventMessageProcessor.EXCEPTIONS.contains(entity)) {
        withProjectIdentifier.add(entity);
      }
    });
    assertThat(entitiesWithVerificationTaskId)
        .isEqualTo(withProjectIdentifier)
        .withFailMessage("Entities with projectIdentifier found which is not added to ENTITIES_MAP");
  }

  private boolean doesClassContainField(Class<?> clazz, String fieldName) {
    return Arrays.stream(clazz.getDeclaredFields()).anyMatch(f -> f.getName().equals(fieldName));
  }

  private CVConfig createCVConfig(String accountId, String orgIdentifier, String projectIdentifier) {
    return builderFactory.splunkCVConfigBuilder()
        .accountId(accountId)
        .projectIdentifier(projectIdentifier)
        .orgIdentifier(orgIdentifier)
        .build();
  }

  MonitoredServiceDTO createMonitoredServiceDTO(String orgIdentifier, String projectIdentifier) {
    MonitoredServiceDTOBuilder monitoredServiceDTOBuilder = MonitoredServiceDTO.builder();
    monitoredServiceDTOBuilder.enabled(false);
    monitoredServiceDTOBuilder.serviceRef(generateUuid());
    monitoredServiceDTOBuilder.environmentRef(generateUuid());
    monitoredServiceDTOBuilder.orgIdentifier(orgIdentifier);
    monitoredServiceDTOBuilder.projectIdentifier(projectIdentifier);
    monitoredServiceDTOBuilder.identifier(generateUuid());
    monitoredServiceDTOBuilder.type(MonitoredServiceType.INFRASTRUCTURE);
    monitoredServiceDTOBuilder.sources(MonitoredServiceDTO.Sources.builder().build());
    return monitoredServiceDTOBuilder.build();
  }
}
