/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.outbox;

import static io.harness.rule.OwnerRule.NAVEEN;

import io.harness.CvNextGenTestBase;
import io.harness.audit.ResourceTypeConstants;
import io.harness.category.element.UnitTests;
import io.harness.cvng.BuilderFactory;
import io.harness.cvng.core.services.api.monitoredService.MonitoredServiceService;
import io.harness.cvng.events.servicelevelobjective.ServiceLevelObjectiveCreateEvent;
import io.harness.cvng.events.servicelevelobjective.ServiceLevelObjectiveDeleteEvent;
import io.harness.cvng.events.servicelevelobjective.ServiceLevelObjectiveUpdateEvent;
import io.harness.cvng.servicelevelobjective.beans.ServiceLevelObjectiveDTO;
import io.harness.cvng.servicelevelobjective.services.api.ServiceLevelObjectiveService;
import io.harness.ng.core.ProjectScope;
import io.harness.ng.core.Resource;
import io.harness.ng.core.ResourceScope;
import io.harness.outbox.OutboxEvent;
import io.harness.rule.Owner;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Inject;
import io.serializer.HObjectMapper;
import java.text.ParseException;
import javax.annotation.Nullable;
import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import org.assertj.core.api.Assertions;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.MockitoAnnotations;

@FieldDefaults(level = AccessLevel.PRIVATE)
public class ServiceLevelObjectiveOutboxEventHandlerTest extends CvNextGenTestBase {
  @Inject ServiceLevelObjectiveService serviceLevelObjectiveService;
  @Inject MonitoredServiceService monitoredServiceService;
  @Inject CVServiceOutboxEventHandler cvServiceOutboxEventHandler;
  private BuilderFactory builderFactory;

  @Before
  public void setup() throws IllegalAccessException, ParseException {
    MockitoAnnotations.initMocks(this);
    builderFactory = BuilderFactory.getDefault();
  }

  @Test
  @Owner(developers = NAVEEN)
  @Category(UnitTests.class)
  public void testOutboxCreateEventHandler() throws JsonProcessingException {
    @Nullable ObjectMapper objectMapper;
    objectMapper = HObjectMapper.NG_DEFAULT_OBJECT_MAPPER;
    ServiceLevelObjectiveDTO sloDTO = createSLOBuilder();
    ServiceLevelObjectiveCreateEvent serviceLevelObjectiveCreateEvent =
        ServiceLevelObjectiveCreateEvent.builder()
            .resourceName(sloDTO.getName())
            .serviceLevelObjectiveIdentifier(sloDTO.getIdentifier())
            .accountIdentifier(sloDTO.getIdentifier())
            .orgIdentifier(sloDTO.getOrgIdentifier())
            .projectIdentifier(sloDTO.getProjectIdentifier())
            .build();
    String createEventString = objectMapper.writeValueAsString(serviceLevelObjectiveCreateEvent);
    ResourceScope resourceScope = new ProjectScope(
        serviceLevelObjectiveCreateEvent.getServiceLevelObjectiveIdentifier(),
        serviceLevelObjectiveCreateEvent.getOrgIdentifier(), serviceLevelObjectiveCreateEvent.getProjectIdentifier());
    OutboxEvent outboxEvent =
        OutboxEvent.builder()
            .eventType("ServiceLevelObjectiveCreateEvent")
            .resourceScope(resourceScope)
            .eventData(createEventString)
            .createdAt(System.currentTimeMillis())
            .resource(Resource.builder().type(ResourceTypeConstants.SERVICE_LEVEL_OBJECTIVE).build())
            .build();
    Boolean returnValue = cvServiceOutboxEventHandler.handle(outboxEvent);
    Assertions.assertThat(returnValue).isEqualTo(true);
  }

  @Test
  @Owner(developers = NAVEEN)
  @Category(UnitTests.class)
  public void testOutboxUpdateEventHandler() throws JsonProcessingException {
    @Nullable ObjectMapper objectMapper;
    objectMapper = HObjectMapper.NG_DEFAULT_OBJECT_MAPPER;
    ServiceLevelObjectiveDTO sloDTO = createSLOBuilder();
    ServiceLevelObjectiveUpdateEvent serviceLevelObjectiveUpdateEvent =
        ServiceLevelObjectiveUpdateEvent.builder()
            .resourceName(sloDTO.getName())
            .serviceLevelObjectiveIdentifier(sloDTO.getIdentifier())
            .accountIdentifier(sloDTO.getIdentifier())
            .orgIdentifier(sloDTO.getOrgIdentifier())
            .projectIdentifier(sloDTO.getProjectIdentifier())
            .build();
    String createEventString = objectMapper.writeValueAsString(serviceLevelObjectiveUpdateEvent);
    ResourceScope resourceScope = new ProjectScope(
        serviceLevelObjectiveUpdateEvent.getServiceLevelObjectiveIdentifier(),
        serviceLevelObjectiveUpdateEvent.getOrgIdentifier(), serviceLevelObjectiveUpdateEvent.getProjectIdentifier());
    OutboxEvent outboxEvent =
        OutboxEvent.builder()
            .eventType("ServiceLevelObjectiveUpdateEvent")
            .resourceScope(resourceScope)
            .eventData(createEventString)
            .createdAt(System.currentTimeMillis())
            .resource(Resource.builder().type(ResourceTypeConstants.SERVICE_LEVEL_OBJECTIVE).build())
            .build();
    Boolean returnValue = cvServiceOutboxEventHandler.handle(outboxEvent);
    Assertions.assertThat(returnValue).isEqualTo(true);
  }

  @Test
  @Owner(developers = NAVEEN)
  @Category(UnitTests.class)
  public void testOutboxDeleteHandler() throws JsonProcessingException {
    @Nullable ObjectMapper objectMapper;
    objectMapper = HObjectMapper.NG_DEFAULT_OBJECT_MAPPER;
    ServiceLevelObjectiveDTO sloDTO = createSLOBuilder();
    ServiceLevelObjectiveDeleteEvent serviceLevelObjectiveDeleteEvent =
        ServiceLevelObjectiveDeleteEvent.builder()
            .resourceName(sloDTO.getName())
            .serviceLevelObjectiveIdentifier(sloDTO.getIdentifier())
            .accountIdentifier(sloDTO.getIdentifier())
            .orgIdentifier(sloDTO.getOrgIdentifier())
            .projectIdentifier(sloDTO.getProjectIdentifier())
            .build();
    String createEventString = objectMapper.writeValueAsString(serviceLevelObjectiveDeleteEvent);
    ResourceScope resourceScope = new ProjectScope(
        serviceLevelObjectiveDeleteEvent.getServiceLevelObjectiveIdentifier(),
        serviceLevelObjectiveDeleteEvent.getOrgIdentifier(), serviceLevelObjectiveDeleteEvent.getProjectIdentifier());
    OutboxEvent outboxEvent =
        OutboxEvent.builder()
            .eventType("ServiceLevelObjectiveDeleteEvent")
            .resourceScope(resourceScope)
            .eventData(createEventString)
            .createdAt(System.currentTimeMillis())
            .resource(Resource.builder().type(ResourceTypeConstants.SERVICE_LEVEL_OBJECTIVE).build())
            .build();
    Boolean returnValue = cvServiceOutboxEventHandler.handle(outboxEvent);
    Assertions.assertThat(returnValue).isEqualTo(true);
  }

  private ServiceLevelObjectiveDTO createSLOBuilder() {
    return builderFactory.getServiceLevelObjectiveDTOBuilder().build();
  }
}
