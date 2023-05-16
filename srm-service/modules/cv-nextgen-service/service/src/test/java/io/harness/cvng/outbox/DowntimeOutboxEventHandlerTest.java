/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.outbox;

import static io.harness.rule.OwnerRule.VARSHA_LALWANI;

import io.harness.CvNextGenTestBase;
import io.harness.audit.ResourceTypeConstants;
import io.harness.category.element.UnitTests;
import io.harness.cvng.BuilderFactory;
import io.harness.cvng.core.beans.monitoredService.MonitoredServiceDTO;
import io.harness.cvng.core.beans.params.ProjectParams;
import io.harness.cvng.core.services.api.monitoredService.MonitoredServiceService;
import io.harness.cvng.downtime.beans.DowntimeDTO;
import io.harness.cvng.downtime.services.api.DowntimeService;
import io.harness.cvng.events.downtime.DowntimeCreateEvent;
import io.harness.cvng.events.downtime.DowntimeDeleteEvent;
import io.harness.cvng.events.downtime.DowntimeUpdateEvent;
import io.harness.cvng.servicelevelobjective.beans.ServiceLevelObjectiveV2DTO;
import io.harness.cvng.servicelevelobjective.services.api.ServiceLevelObjectiveV2Service;
import io.harness.ng.core.ProjectScope;
import io.harness.ng.core.Resource;
import io.harness.ng.core.ResourceScope;
import io.harness.outbox.OutboxEvent;
import io.harness.rule.Owner;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Inject;
import io.serializer.HObjectMapper;
import javax.annotation.Nullable;
import org.assertj.core.api.Assertions;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class DowntimeOutboxEventHandlerTest extends CvNextGenTestBase {
  @Inject MonitoredServiceService monitoredServiceService;

  @Inject ServiceLevelObjectiveV2Service serviceLevelObjectiveV2Service;

  @Inject DowntimeService downtimeService;

  @Inject CVServiceOutboxEventHandler cvServiceOutboxEventHandler;

  private BuilderFactory builderFactory;
  ServiceLevelObjectiveV2DTO sloDTO;

  @Before
  public void setup() throws IllegalAccessException {
    builderFactory = BuilderFactory.getDefault();
    ProjectParams projectParams = builderFactory.getProjectParams();
    sloDTO = builderFactory.getSimpleServiceLevelObjectiveV2DTOBuilder().build();
    createMonitoredService();
    serviceLevelObjectiveV2Service.create(projectParams, sloDTO);
  }

  @Test
  @Owner(developers = VARSHA_LALWANI)
  @Category(UnitTests.class)
  public void testHandle_DowntimeCreateEvent() throws JsonProcessingException {
    @Nullable ObjectMapper objectMapper;
    objectMapper = HObjectMapper.NG_DEFAULT_OBJECT_MAPPER;
    DowntimeDTO recurringDowntimeDTO = builderFactory.getRecurringDowntimeDTO();
    downtimeService.create(builderFactory.getProjectParams(), recurringDowntimeDTO);

    DowntimeCreateEvent downtimeCreateEvent = DowntimeCreateEvent.builder()
                                                  .resourceName(recurringDowntimeDTO.getName())
                                                  .downtimeIdentifier(recurringDowntimeDTO.getIdentifier())
                                                  .accountIdentifier(recurringDowntimeDTO.getIdentifier())
                                                  .orgIdentifier(recurringDowntimeDTO.getOrgIdentifier())
                                                  .projectIdentifier(recurringDowntimeDTO.getProjectIdentifier())
                                                  .downtimeDTO(recurringDowntimeDTO)
                                                  .build();
    String createEventString = objectMapper.writeValueAsString(downtimeCreateEvent);
    ResourceScope resourceScope = new ProjectScope(downtimeCreateEvent.getDowntimeIdentifier(),
        downtimeCreateEvent.getOrgIdentifier(), downtimeCreateEvent.getProjectIdentifier());
    OutboxEvent outboxEvent = OutboxEvent.builder()
                                  .eventType("DowntimeCreateEvent")
                                  .resourceScope(resourceScope)
                                  .eventData(createEventString)
                                  .createdAt(System.currentTimeMillis())
                                  .resource(Resource.builder().type(ResourceTypeConstants.DOWNTIME).build())
                                  .build();
    Boolean returnValue = cvServiceOutboxEventHandler.handle(outboxEvent);
    Assertions.assertThat(returnValue).isTrue();
  }

  @Test
  @Owner(developers = VARSHA_LALWANI)
  @Category(UnitTests.class)
  public void testHandle_DowntimeUpdateEvent() throws JsonProcessingException {
    @Nullable ObjectMapper objectMapper;
    objectMapper = HObjectMapper.NG_DEFAULT_OBJECT_MAPPER;
    DowntimeDTO recurringDowntimeDTO = builderFactory.getRecurringDowntimeDTO();
    downtimeService.create(builderFactory.getProjectParams(), recurringDowntimeDTO);

    DowntimeUpdateEvent downtimeUpdateEvent = DowntimeUpdateEvent.builder()
                                                  .resourceName(recurringDowntimeDTO.getName())
                                                  .downtimeIdentifier(recurringDowntimeDTO.getIdentifier())
                                                  .accountIdentifier(recurringDowntimeDTO.getIdentifier())
                                                  .orgIdentifier(recurringDowntimeDTO.getOrgIdentifier())
                                                  .projectIdentifier(recurringDowntimeDTO.getProjectIdentifier())
                                                  .oldDowntimeDTO(recurringDowntimeDTO)
                                                  .newDowntimeDTO(recurringDowntimeDTO)
                                                  .build();
    String createEventString = objectMapper.writeValueAsString(downtimeUpdateEvent);
    ResourceScope resourceScope = new ProjectScope(downtimeUpdateEvent.getDowntimeIdentifier(),
        downtimeUpdateEvent.getOrgIdentifier(), downtimeUpdateEvent.getProjectIdentifier());
    OutboxEvent outboxEvent = OutboxEvent.builder()
                                  .eventType("DowntimeUpdateEvent")
                                  .resourceScope(resourceScope)
                                  .eventData(createEventString)
                                  .createdAt(System.currentTimeMillis())
                                  .resource(Resource.builder().type(ResourceTypeConstants.DOWNTIME).build())
                                  .build();
    Boolean returnValue = cvServiceOutboxEventHandler.handle(outboxEvent);
    Assertions.assertThat(returnValue).isTrue();
  }

  @Test
  @Owner(developers = VARSHA_LALWANI)
  @Category(UnitTests.class)
  public void testHandle_DowntimeDeleteEvent() throws JsonProcessingException {
    @Nullable ObjectMapper objectMapper;
    objectMapper = HObjectMapper.NG_DEFAULT_OBJECT_MAPPER;
    DowntimeDTO recurringDowntimeDTO = builderFactory.getRecurringDowntimeDTO();
    downtimeService.create(builderFactory.getProjectParams(), recurringDowntimeDTO);

    DowntimeDeleteEvent downtimeDeleteEvent = DowntimeDeleteEvent.builder()
                                                  .resourceName(recurringDowntimeDTO.getName())
                                                  .downtimeIdentifier(recurringDowntimeDTO.getIdentifier())
                                                  .accountIdentifier(recurringDowntimeDTO.getIdentifier())
                                                  .orgIdentifier(recurringDowntimeDTO.getOrgIdentifier())
                                                  .projectIdentifier(recurringDowntimeDTO.getProjectIdentifier())
                                                  .downtimeDTO(recurringDowntimeDTO)
                                                  .build();
    String createEventString = objectMapper.writeValueAsString(downtimeDeleteEvent);
    ResourceScope resourceScope = new ProjectScope(downtimeDeleteEvent.getDowntimeIdentifier(),
        downtimeDeleteEvent.getOrgIdentifier(), downtimeDeleteEvent.getProjectIdentifier());
    OutboxEvent outboxEvent = OutboxEvent.builder()
                                  .eventType("DowntimeDeleteEvent")
                                  .resourceScope(resourceScope)
                                  .eventData(createEventString)
                                  .createdAt(System.currentTimeMillis())
                                  .resource(Resource.builder().type(ResourceTypeConstants.DOWNTIME).build())
                                  .build();
    Boolean returnValue = cvServiceOutboxEventHandler.handle(outboxEvent);
    Assertions.assertThat(returnValue).isTrue();
  }

  private void createMonitoredService() {
    MonitoredServiceDTO monitoredServiceDTO =
        builderFactory.monitoredServiceDTOBuilder()
            .identifier(builderFactory.getContext().getMonitoredServiceIdentifier())
            .build();
    monitoredServiceDTO.setSources(MonitoredServiceDTO.Sources.builder().build());
    monitoredServiceService.create(builderFactory.getContext().getAccountId(), monitoredServiceDTO);
  }
}
