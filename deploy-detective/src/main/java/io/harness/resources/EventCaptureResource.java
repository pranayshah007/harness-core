/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.resources;

import io.harness.deploydetective.dto.EventRequestDTO;
import io.harness.deploydetective.dto.EventResponseDTO;
import io.harness.deploydetective.entities.EventEntity;
import io.harness.deploydetective.mapper.EventMapper;
import io.harness.repositories.EventRepository;
import io.harness.security.annotations.PublicApi;

import com.google.inject.Inject;
import io.swagger.annotations.Api;
import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import lombok.extern.slf4j.Slf4j;

@Api("event-capture")
@Path("/event-capture")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@PublicApi
@Slf4j
public class EventCaptureResource {
  @Inject private EventRepository eventRepository;
  @POST
  public EventResponseDTO capture(EventRequestDTO request) {
    EventEntity save = eventRepository.save(EventMapper.toDTO(request));
    return EventMapper.toDTO(save);
  }
}
