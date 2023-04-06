/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.servicelevelobjective.services.impl;

import static io.harness.rule.OwnerRule.KARAN_SARASWAT;
import static io.harness.rule.TestUserProvider.testUserProvider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.harness.CvNextGenTestBase;
import io.harness.beans.EmbeddedUser;
import io.harness.category.element.UnitTests;
import io.harness.cvng.BuilderFactory;
import io.harness.cvng.CVNGTestConstants;
import io.harness.cvng.core.beans.monitoredService.HealthSource;
import io.harness.cvng.core.beans.monitoredService.MonitoredServiceDTO;
import io.harness.cvng.core.services.api.MetricPackService;
import io.harness.cvng.core.services.api.monitoredService.MonitoredServiceService;
import io.harness.cvng.servicelevelobjective.beans.AnnotationDTO;
import io.harness.cvng.servicelevelobjective.beans.AnnotationResponse;
import io.harness.cvng.servicelevelobjective.beans.ServiceLevelObjectiveV2DTO;
import io.harness.cvng.servicelevelobjective.beans.secondaryEvents.SecondaryEventsResponse;
import io.harness.cvng.servicelevelobjective.beans.slospec.SimpleServiceLevelObjectiveSpec;
import io.harness.cvng.servicelevelobjective.entities.Annotation;
import io.harness.cvng.servicelevelobjective.services.api.AnnotationService;
import io.harness.cvng.servicelevelobjective.services.api.ServiceLevelObjectiveV2Service;
import io.harness.rule.Owner;

import com.google.inject.Inject;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class AnnotationServiceImplTest extends CvNextGenTestBase {
  @Inject private MetricPackService metricPackService;
  @Inject private ServiceLevelObjectiveV2Service serviceLevelObjectiveV2Service;
  @Inject private MonitoredServiceService monitoredServiceService;
  @Inject private AnnotationService annotationService;

  private BuilderFactory builderFactory;
  private ServiceLevelObjectiveV2DTO serviceLevelObjective;
  private long startTime;
  private long endTime;

  @Before
  public void setup() {
    builderFactory = BuilderFactory.getDefault();
    metricPackService.createDefaultMetricPackAndThresholds(builderFactory.getContext().getAccountId(),
        builderFactory.getContext().getOrgIdentifier(), builderFactory.getContext().getProjectIdentifier());

    String monitoredServiceIdentifier = "monitoredServiceIdentifier";
    MonitoredServiceDTO monitoredServiceDTO =
        builderFactory.monitoredServiceDTOBuilder().identifier(monitoredServiceIdentifier).build();
    HealthSource healthSource = monitoredServiceDTO.getSources().getHealthSources().iterator().next();
    monitoredServiceService.create(builderFactory.getContext().getAccountId(), monitoredServiceDTO);
    serviceLevelObjective = builderFactory.getSimpleServiceLevelObjectiveV2DTOBuilder().build();
    SimpleServiceLevelObjectiveSpec spec = (SimpleServiceLevelObjectiveSpec) serviceLevelObjective.getSpec();
    spec.setMonitoredServiceRef(monitoredServiceIdentifier);
    spec.setHealthSourceRef(healthSource.getIdentifier());
    serviceLevelObjective.setSpec(spec);
    serviceLevelObjectiveV2Service.create(builderFactory.getProjectParams(), serviceLevelObjective);

    startTime = CVNGTestConstants.FIXED_TIME_FOR_TESTS.instant().getEpochSecond();
    endTime = startTime + Duration.ofMinutes(30).toSeconds();
    testUserProvider.setActiveUser(EmbeddedUser.builder().name("user1").email("user1@harness.io").build());
  }

  @Test
  @Owner(developers = KARAN_SARASWAT)
  @Category(UnitTests.class)
  public void testCreate_Success() {
    AnnotationDTO annotationDTO = builderFactory.getAnnotationDTO();
    AnnotationResponse annotationResponse = annotationService.create(builderFactory.getProjectParams(), annotationDTO);
    assertThat(annotationResponse.getAnnotationDTO()).isEqualTo(annotationDTO);
  }

  @Test
  @Owner(developers = KARAN_SARASWAT)
  @Category(UnitTests.class)
  public void testGetAllInstancesGrouped() {
    AnnotationDTO annotationDTO1 = builderFactory.getAnnotationDTO();
    annotationService.create(builderFactory.getProjectParams(), annotationDTO1);
    AnnotationDTO annotationDTO2 = builderFactory.getAnnotationDTO();
    annotationDTO2.setMessage("new one");
    annotationService.create(builderFactory.getProjectParams(), annotationDTO2);
    AnnotationDTO annotationDTO3 = builderFactory.getAnnotationDTO();
    annotationDTO3.setStartTime(startTime + Duration.ofMinutes(5).toSeconds());
    annotationService.create(builderFactory.getProjectParams(), annotationDTO3);

    List<SecondaryEventsResponse> response = annotationService.getAllInstancesGrouped(builderFactory.getProjectParams(),
        startTime, startTime + Duration.ofMinutes(60).toSeconds(), serviceLevelObjective.getIdentifier());
    assertThat(response.size()).isEqualTo(2);
  }

  @Test
  @Owner(developers = KARAN_SARASWAT)
  @Category(UnitTests.class)
  public void testUpdate_Success() {
    AnnotationDTO annotationDTO = builderFactory.getAnnotationDTO();
    annotationService.create(builderFactory.getProjectParams(), annotationDTO);
    annotationDTO.setMessage("new message");
    List<Annotation> annotationList =
        annotationService.get(builderFactory.getProjectParams(), serviceLevelObjective.getIdentifier());
    String annotationId = annotationList.get(0).getUuid();
    AnnotationResponse annotationResponse = annotationService.update(annotationId, annotationDTO);
    assertThat(annotationResponse.getAnnotationDTO()).isEqualTo(annotationDTO);
  }

  @Test
  @Owner(developers = KARAN_SARASWAT)
  @Category(UnitTests.class)
  public void testUpdate_updateTimeFailure() {
    AnnotationDTO annotationDTO = builderFactory.getAnnotationDTO();
    annotationService.create(builderFactory.getProjectParams(), annotationDTO);
    annotationDTO.setMessage("new message");
    annotationDTO.setEndTime(Instant.now().getEpochSecond());
    String annotationId =
        annotationService.get(builderFactory.getProjectParams(), serviceLevelObjective.getIdentifier())
            .get(0)
            .getUuid();
    assertThatThrownBy(() -> annotationService.update(annotationId, annotationDTO))
        .hasMessage("Can not update the start/end time of a message");
  }

  @Test
  @Owner(developers = KARAN_SARASWAT)
  @Category(UnitTests.class)
  public void testDelete_Success() {
    AnnotationDTO annotationDTO = builderFactory.getAnnotationDTO();
    annotationService.create(builderFactory.getProjectParams(), annotationDTO);
    annotationDTO.setMessage("new message");
    annotationDTO.setEndTime(Instant.now().getEpochSecond());
    String annotationId =
        annotationService.get(builderFactory.getProjectParams(), serviceLevelObjective.getIdentifier())
            .get(0)
            .getUuid();
    annotationService.delete(annotationId);
    List<Annotation> annotations =
        annotationService.get(builderFactory.getProjectParams(), serviceLevelObjective.getIdentifier());
    assertThat(annotations.size()).isEqualTo(0);
  }
}
