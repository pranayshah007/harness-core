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
import io.harness.cvng.beans.CVMonitoringCategory;
import io.harness.cvng.core.beans.monitoredService.MonitoredServiceDTO;
import io.harness.cvng.core.beans.monitoredService.MonitoredServiceDTO.MonitoredServiceDTOBuilder;
import io.harness.cvng.core.services.api.monitoredService.MonitoredServiceService;
import io.harness.cvng.events.monitoredservice.MonitoredServiceCreateEvent;
import io.harness.cvng.events.monitoredservice.MonitoredServiceDeleteEvent;
import io.harness.cvng.events.monitoredservice.MonitoredServiceToggleEvent;
import io.harness.cvng.events.monitoredservice.MonitoredServiceUpdateEvent;
import io.harness.ng.core.ProjectScope;
import io.harness.ng.core.Resource;
import io.harness.ng.core.ResourceScope;
import io.harness.outbox.OutboxEvent;
import io.harness.rule.Owner;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Inject;
import io.serializer.HObjectMapper;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import org.assertj.core.api.Assertions;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@FieldDefaults(level = AccessLevel.PRIVATE)
public class MonitoredServiceOutboxEventHandlerTest extends CvNextGenTestBase {
  @Inject MonitoredServiceService monitoredServiceService;

  @Inject CVServiceOutboxEventHandler cvServiceOutboxEventHandler;

  private BuilderFactory builderFactory;
  String environmentIdentifier;
  String serviceIdentifier;
  String monitoredServiceName;
  String monitoredServiceIdentifier;
  Map<String, String> tags;

  @Before
  public void setup() throws IllegalAccessException {
    builderFactory = BuilderFactory.getDefault();
    String accountId = builderFactory.getContext().getAccountId();
    String orgIdentifier = builderFactory.getContext().getOrgIdentifier();
    String projectIdentifier = builderFactory.getContext().getProjectIdentifier();
    environmentIdentifier = builderFactory.getContext().getEnvIdentifier();
    serviceIdentifier = builderFactory.getContext().getServiceIdentifier();
    monitoredServiceName = "monitoredServiceName";
    monitoredServiceIdentifier =
        builderFactory.getContext().getMonitoredServiceParams().getMonitoredServiceIdentifier();
    tags = new HashMap<String, String>() {
      {
        put("tag1", "value1");
        put("tag2", "");
      }
    };
  }

  @Test
  @Owner(developers = NAVEEN)
  @Category(UnitTests.class)
  public void testHandle_MonitoredServiceCreateEvent() throws JsonProcessingException {
    @Nullable ObjectMapper objectMapper;
    objectMapper = HObjectMapper.NG_DEFAULT_OBJECT_MAPPER;
    MonitoredServiceDTO monitoredServiceDTO = createMonitoredServiceDTO();
    MonitoredServiceCreateEvent monitoredServiceCreateEvent =
        MonitoredServiceCreateEvent.builder()
            .resourceName(monitoredServiceDTO.getName())
            .monitoredServiceIdentifier(monitoredServiceDTO.getIdentifier())
            .accountIdentifier(monitoredServiceDTO.getIdentifier())
            .orgIdentifier(monitoredServiceDTO.getOrgIdentifier())
            .projectIdentifier(monitoredServiceDTO.getProjectIdentifier())
            .build();
    String createEventString = objectMapper.writeValueAsString(monitoredServiceCreateEvent);
    ResourceScope resourceScope = new ProjectScope(monitoredServiceCreateEvent.getMonitoredServiceIdentifier(),
        monitoredServiceCreateEvent.getOrgIdentifier(), monitoredServiceCreateEvent.getProjectIdentifier());
    OutboxEvent outboxEvent = OutboxEvent.builder()
                                  .eventType("MonitoredServiceCreateEvent")
                                  .resourceScope(resourceScope)
                                  .eventData(createEventString)
                                  .createdAt(System.currentTimeMillis())
                                  .resource(Resource.builder().type(ResourceTypeConstants.MONITORED_SERVICE).build())
                                  .build();
    Boolean returnValue = cvServiceOutboxEventHandler.handle(outboxEvent);
    Assertions.assertThat(returnValue).isEqualTo(true);
  }

  @Test
  @Owner(developers = NAVEEN)
  @Category(UnitTests.class)
  public void testHandle_MonitoredServiceUpdateEvent() throws JsonProcessingException {
    @Nullable ObjectMapper objectMapper;
    objectMapper = HObjectMapper.NG_DEFAULT_OBJECT_MAPPER;
    MonitoredServiceDTO monitoredServiceDTO = createMonitoredServiceDTO();
    MonitoredServiceUpdateEvent monitoredServiceUpdateEvent =
        MonitoredServiceUpdateEvent.builder()
            .resourceName(monitoredServiceDTO.getName())
            .monitoredServiceIdentifier(monitoredServiceDTO.getIdentifier())
            .accountIdentifier(monitoredServiceDTO.getIdentifier())
            .orgIdentifier(monitoredServiceDTO.getOrgIdentifier())
            .projectIdentifier(monitoredServiceDTO.getProjectIdentifier())
            .build();
    String createEventString = objectMapper.writeValueAsString(monitoredServiceUpdateEvent);
    ResourceScope resourceScope = new ProjectScope(monitoredServiceUpdateEvent.getMonitoredServiceIdentifier(),
        monitoredServiceUpdateEvent.getOrgIdentifier(), monitoredServiceUpdateEvent.getProjectIdentifier());
    OutboxEvent outboxEvent = OutboxEvent.builder()
                                  .eventType("MonitoredServiceUpdateEvent")
                                  .resourceScope(resourceScope)
                                  .eventData(createEventString)
                                  .createdAt(System.currentTimeMillis())
                                  .resource(Resource.builder().type(ResourceTypeConstants.MONITORED_SERVICE).build())
                                  .build();
    Boolean returnValue = cvServiceOutboxEventHandler.handle(outboxEvent);
    Assertions.assertThat(returnValue).isEqualTo(true);
  }

  @Test
  @Owner(developers = NAVEEN)
  @Category(UnitTests.class)
  public void testHandle_MonitoredServiceToggleEvent() throws JsonProcessingException {
    @Nullable ObjectMapper objectMapper;
    objectMapper = HObjectMapper.NG_DEFAULT_OBJECT_MAPPER;
    MonitoredServiceDTO monitoredServiceDTO = createMonitoredServiceDTO();
    MonitoredServiceToggleEvent monitoredServiceToggleEvent =
        MonitoredServiceToggleEvent.builder()
            .resourceName(monitoredServiceDTO.getName())
            .monitoredServiceIdentifier(monitoredServiceDTO.getIdentifier())
            .accountIdentifier(monitoredServiceDTO.getIdentifier())
            .orgIdentifier(monitoredServiceDTO.getOrgIdentifier())
            .projectIdentifier(monitoredServiceDTO.getProjectIdentifier())
            .build();
    String createEventString = objectMapper.writeValueAsString(monitoredServiceToggleEvent);
    ResourceScope resourceScope = new ProjectScope(monitoredServiceToggleEvent.getMonitoredServiceIdentifier(),
        monitoredServiceToggleEvent.getOrgIdentifier(), monitoredServiceToggleEvent.getProjectIdentifier());
    OutboxEvent outboxEvent = OutboxEvent.builder()
                                  .eventType("MonitoredServiceToggleEvent")
                                  .resourceScope(resourceScope)
                                  .eventData(createEventString)
                                  .createdAt(System.currentTimeMillis())
                                  .resource(Resource.builder().type(ResourceTypeConstants.MONITORED_SERVICE).build())
                                  .build();
    Boolean returnValue = cvServiceOutboxEventHandler.handle(outboxEvent);
    Assertions.assertThat(returnValue).isEqualTo(true);
  }

  @Test
  @Owner(developers = NAVEEN)
  @Category(UnitTests.class)
  public void testHandle_MonitoredServiceDeleteEvent() throws JsonProcessingException {
    @Nullable ObjectMapper objectMapper;
    MonitoredServiceDTO monitoredServiceDTO = createMonitoredServiceDTO();
    objectMapper = HObjectMapper.NG_DEFAULT_OBJECT_MAPPER;
    MonitoredServiceDeleteEvent monitoredServiceDeleteEvent =
        MonitoredServiceDeleteEvent.builder()
            .resourceName(monitoredServiceDTO.getName())
            .monitoredServiceIdentifier(monitoredServiceDTO.getIdentifier())
            .accountIdentifier(monitoredServiceDTO.getIdentifier())
            .orgIdentifier(monitoredServiceDTO.getOrgIdentifier())
            .projectIdentifier(monitoredServiceDTO.getProjectIdentifier())
            .build();
    String createEventString = objectMapper.writeValueAsString(monitoredServiceDeleteEvent);
    ResourceScope resourceScope = new ProjectScope(monitoredServiceDeleteEvent.getMonitoredServiceIdentifier(),
        monitoredServiceDeleteEvent.getOrgIdentifier(), monitoredServiceDeleteEvent.getProjectIdentifier());
    OutboxEvent outboxEvent = OutboxEvent.builder()
                                  .eventType("MonitoredServiceToggleEvent")
                                  .resourceScope(resourceScope)
                                  .eventData(createEventString)
                                  .createdAt(System.currentTimeMillis())
                                  .resource(Resource.builder().type(ResourceTypeConstants.MONITORED_SERVICE).build())
                                  .build();
    Boolean returnValue = cvServiceOutboxEventHandler.handle(outboxEvent);
    Assertions.assertThat(returnValue).isEqualTo(true);
  }

  MonitoredServiceDTO createMonitoredServiceDTO() {
    return createMonitoredServiceDTOBuilder()
        .sources(MonitoredServiceDTO.Sources.builder()
                     .healthSources(Stream.of(builderFactory.createHealthSource(CVMonitoringCategory.ERRORS))
                                        .collect(Collectors.toSet()))
                     .changeSources(new HashSet<>(Collections.singletonList(
                         builderFactory.getHarnessCDCurrentGenChangeSourceDTOBuilder().build())))
                     .build())
        .build();
  }

  private MonitoredServiceDTOBuilder createMonitoredServiceDTOBuilder() {
    return builderFactory.monitoredServiceDTOBuilder()
        .identifier(monitoredServiceIdentifier)
        .serviceRef(serviceIdentifier)
        .environmentRef(environmentIdentifier)
        .name(monitoredServiceName)
        .tags(tags);
  }
}
