/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.releaseradar.resources;

import com.google.inject.Inject;
import io.harness.releaseradar.dto.EventRequestDTO;
import io.harness.releaseradar.dto.EventResponseDTO;
import io.harness.releaseradar.entities.EventEntity;
import io.harness.releaseradar.mapper.EventMapper;
import io.harness.releaseradar.services.EventProcessor;
import io.harness.repositories.EventRepository;
import io.harness.security.annotations.PublicApi;
import io.swagger.annotations.Api;
import lombok.extern.slf4j.Slf4j;

import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

@Api("events")
@Path("/events")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@PublicApi
@Slf4j
public class EventResource {
  @Inject private EventRepository eventRepository;
  @Inject private EventProcessor eventProcessor;
  @POST
  public EventResponseDTO capture(EventRequestDTO request) {
    EventEntity save = eventRepository.save(EventMapper.toDTO(request));

    eventProcessor.process(save);

    return EventMapper.toDTO(save);
  }
}
