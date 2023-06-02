/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.downtime.services.impl;

import static io.harness.rule.OwnerRule.VARSHA_LALWANI;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.harness.CvNextGenTestBase;
import io.harness.category.element.UnitTests;
import io.harness.cvng.BuilderFactory;
import io.harness.cvng.core.beans.params.ProjectParams;
import io.harness.cvng.downtime.beans.EntityUnavailabilityStatusesDTO;
import io.harness.cvng.downtime.entities.EntityUnavailabilityStatuses;
import io.harness.cvng.downtime.services.api.EntityUnavailabilityStatusesService;
import io.harness.persistence.HPersistence;
import io.harness.rule.Owner;

import com.google.inject.Inject;
import java.text.ParseException;
import java.time.Clock;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.MockitoAnnotations;

public class EntityUnavailabilityServicesImplTest extends CvNextGenTestBase {
  private BuilderFactory builderFactory;

  private ProjectParams projectParams;

  @Inject HPersistence hPersistence;

  @Inject private Clock clock;

  @Inject EntityUnavailabilityStatusesService entityUnavailabilityStatusesService;

  EntityUnavailabilityStatusesDTO entityUnavailabilityStatusesDTO;

  @Before
  public void setup() throws IllegalAccessException, ParseException {
    MockitoAnnotations.initMocks(this);
    builderFactory = BuilderFactory.getDefault();
    projectParams = builderFactory.getProjectParams();
    clock = builderFactory.getClock();
    entityUnavailabilityStatusesDTO = builderFactory.getDowntimeEntityUnavailabilityStatusesDTO();
    FieldUtils.writeField(entityUnavailabilityStatusesService, "clock", clock, true);
  }

  @Test
  @Owner(developers = VARSHA_LALWANI)
  @Category(UnitTests.class)
  public void testCreateSuccess() {
    entityUnavailabilityStatusesService.create(
        projectParams, Collections.singletonList(entityUnavailabilityStatusesDTO));
    List<EntityUnavailabilityStatusesDTO> entityUnavailabilityStatusesDTOS =
        entityUnavailabilityStatusesService.getAllInstances(projectParams);
    assertThat(entityUnavailabilityStatusesDTOS.size()).isEqualTo(1);
    assertThat(entityUnavailabilityStatusesDTOS.get(0)).isEqualTo(entityUnavailabilityStatusesDTO);
  }

  @Test
  @Owner(developers = VARSHA_LALWANI)
  @Category(UnitTests.class)
  public void testUpdateSuccess() {
    entityUnavailabilityStatusesDTO.setStartTime(clock.millis() / 1000 + 1);
    entityUnavailabilityStatusesService.create(
        projectParams, Collections.singletonList(entityUnavailabilityStatusesDTO));
    List<EntityUnavailabilityStatusesDTO> entityUnavailabilityStatusesDTOS =
        entityUnavailabilityStatusesService.getAllInstances(projectParams);
    assertThat(entityUnavailabilityStatusesDTOS.size()).isEqualTo(1);
    assertThat(entityUnavailabilityStatusesDTOS.get(0)).isEqualTo(entityUnavailabilityStatusesDTO);

    entityUnavailabilityStatusesDTO.setStartTime(clock.millis() / 1000 + Duration.ofMinutes(10).toSeconds());
    entityUnavailabilityStatusesDTO.setEndTime(clock.millis() / 1000 + Duration.ofMinutes(40).toSeconds());
    entityUnavailabilityStatusesService.update(projectParams, entityUnavailabilityStatusesDTO.getEntityId(),
        Collections.singletonList(entityUnavailabilityStatusesDTO));
    entityUnavailabilityStatusesDTOS = entityUnavailabilityStatusesService.getAllInstances(projectParams);
    assertThat(entityUnavailabilityStatusesDTOS.size()).isEqualTo(1);
    assertThat(entityUnavailabilityStatusesDTOS.get(0)).isEqualTo(entityUnavailabilityStatusesDTO);
  }

  @Test
  @Owner(developers = VARSHA_LALWANI)
  @Category(UnitTests.class)
  public void testDeleteAllInstancesSuccess() {
    entityUnavailabilityStatusesService.create(
        projectParams, Collections.singletonList(entityUnavailabilityStatusesDTO));
    List<EntityUnavailabilityStatusesDTO> entityUnavailabilityStatusesDTOS =
        entityUnavailabilityStatusesService.getAllInstances(projectParams);
    assertThat(entityUnavailabilityStatusesDTOS.size()).isEqualTo(1);
    assertThat(entityUnavailabilityStatusesDTOS.get(0)).isEqualTo(entityUnavailabilityStatusesDTO);
    entityUnavailabilityStatusesService.deleteAllInstances(
        projectParams, entityUnavailabilityStatusesDTO.getEntityId());
    entityUnavailabilityStatusesDTOS = entityUnavailabilityStatusesService.getAllInstances(projectParams);
    assertThat(entityUnavailabilityStatusesDTOS.size()).isEqualTo(0);
  }

  @Test
  @Owner(developers = VARSHA_LALWANI)
  @Category(UnitTests.class)
  public void testDeleteFutureInstancesSuccess() {
    entityUnavailabilityStatusesDTO.setStartTime(clock.millis() / 1000 + 1);
    entityUnavailabilityStatusesService.create(
        projectParams, Collections.singletonList(entityUnavailabilityStatusesDTO));
    List<EntityUnavailabilityStatusesDTO> entityUnavailabilityStatusesDTOS =
        entityUnavailabilityStatusesService.getAllInstances(projectParams);
    assertThat(entityUnavailabilityStatusesDTOS.size()).isEqualTo(1);
    assertThat(entityUnavailabilityStatusesDTOS.get(0)).isEqualTo(entityUnavailabilityStatusesDTO);
    entityUnavailabilityStatusesService.deleteFutureDowntimeInstances(
        projectParams, entityUnavailabilityStatusesDTO.getEntityId());
    entityUnavailabilityStatusesDTOS = entityUnavailabilityStatusesService.getAllInstances(projectParams);
    assertThat(entityUnavailabilityStatusesDTOS.size()).isEqualTo(0);
  }

  @Test
  @Owner(developers = VARSHA_LALWANI)
  @Category(UnitTests.class)
  public void testGetPastInstancesSuccess() {
    entityUnavailabilityStatusesDTO.setEndTime(clock.millis() / 1000 - 1);
    entityUnavailabilityStatusesService.create(
        projectParams, Collections.singletonList(entityUnavailabilityStatusesDTO));
    List<EntityUnavailabilityStatusesDTO> entityUnavailabilityStatusesDTOS =
        entityUnavailabilityStatusesService.getPastInstances(projectParams);
    assertThat(entityUnavailabilityStatusesDTOS.size()).isEqualTo(1);
    assertThat(entityUnavailabilityStatusesDTOS.get(0)).isEqualTo(entityUnavailabilityStatusesDTO);
  }

  @Test
  @Owner(developers = VARSHA_LALWANI)
  @Category(UnitTests.class)
  public void testGetActiveInstancesSuccess() {
    entityUnavailabilityStatusesDTO.setStartTime(clock.millis() / 1000 - 1);
    entityUnavailabilityStatusesDTO.setEndTime(clock.millis() / 1000 + 1);
    entityUnavailabilityStatusesService.create(
        projectParams, Collections.singletonList(entityUnavailabilityStatusesDTO));
    List<EntityUnavailabilityStatusesDTO> entityUnavailabilityStatusesDTOS =
        entityUnavailabilityStatusesService.getActiveOrFirstUpcomingInstance(
            projectParams, Collections.singletonList(entityUnavailabilityStatusesDTO.getEntityId()));
    assertThat(entityUnavailabilityStatusesDTOS.size()).isEqualTo(1);
    assertThat(entityUnavailabilityStatusesDTOS.get(0)).isEqualTo(entityUnavailabilityStatusesDTO);
  }

  @Test
  @Owner(developers = VARSHA_LALWANI)
  @Category(UnitTests.class)
  public void testGetPastAndActiveInstancesSuccess() {
    entityUnavailabilityStatusesDTO.setStartTime(clock.millis() / 1000 - 1);
    entityUnavailabilityStatusesDTO.setEndTime(clock.millis() / 1000 + 1);
    entityUnavailabilityStatusesService.create(
        projectParams, Collections.singletonList(entityUnavailabilityStatusesDTO));

    entityUnavailabilityStatusesDTO.setStartTime(clock.millis() / 1000 - 2);
    entityUnavailabilityStatusesDTO.setEndTime(clock.millis() / 1000 - 1);
    entityUnavailabilityStatusesService.create(
        projectParams, Collections.singletonList(entityUnavailabilityStatusesDTO));
    List<EntityUnavailabilityStatusesDTO> entityUnavailabilityStatusesDTOS =
        entityUnavailabilityStatusesService.getPastAndActiveDowntimeInstances(
            projectParams, Collections.singletonList(entityUnavailabilityStatusesDTO.getEntityId()));
    assertThat(entityUnavailabilityStatusesDTOS.size()).isEqualTo(2);
  }

  @Test
  @Owner(developers = VARSHA_LALWANI)
  @Category(UnitTests.class)
  public void testDeleteByProjectIdentifier_Success() {
    EntityUnavailabilityStatusesService mockEntityUnavailabilityStatusesService =
        spy(entityUnavailabilityStatusesService);
    entityUnavailabilityStatusesService.create(
        projectParams, Collections.singletonList(entityUnavailabilityStatusesDTO));
    mockEntityUnavailabilityStatusesService.deleteByProjectIdentifier(EntityUnavailabilityStatuses.class,
        projectParams.getAccountIdentifier(), projectParams.getOrgIdentifier(), projectParams.getProjectIdentifier());
    verify(mockEntityUnavailabilityStatusesService, times(1)).deleteAllInstances(any(), any());
  }

  @Test
  @Owner(developers = VARSHA_LALWANI)
  @Category(UnitTests.class)
  public void testDeleteByOrgIdentifier_Success() {
    EntityUnavailabilityStatusesService mockEntityUnavailabilityStatusesService =
        spy(entityUnavailabilityStatusesService);
    entityUnavailabilityStatusesService.create(
        projectParams, Collections.singletonList(entityUnavailabilityStatusesDTO));
    mockEntityUnavailabilityStatusesService.deleteByOrgIdentifier(
        EntityUnavailabilityStatuses.class, projectParams.getAccountIdentifier(), projectParams.getOrgIdentifier());
    verify(mockEntityUnavailabilityStatusesService, times(1)).deleteAllInstances(any(), any());
  }

  @Test
  @Owner(developers = VARSHA_LALWANI)
  @Category(UnitTests.class)
  public void testDeleteByAccountIdentifier_Success() {
    EntityUnavailabilityStatusesService mockEntityUnavailabilityStatusesService =
        spy(entityUnavailabilityStatusesService);
    entityUnavailabilityStatusesService.create(
        projectParams, Collections.singletonList(entityUnavailabilityStatusesDTO));
    mockEntityUnavailabilityStatusesService.deleteByAccountIdentifier(
        EntityUnavailabilityStatuses.class, projectParams.getAccountIdentifier());
    verify(mockEntityUnavailabilityStatusesService, times(1)).deleteAllInstances(any(), any());
  }
}
