/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.releaseradar.resources;

import io.harness.releaseradar.entities.EventEntity;
import io.harness.repositories.EventRepository;
import io.harness.security.annotations.PublicApi;

import com.google.common.collect.Lists;
import com.google.inject.Inject;
import io.swagger.annotations.Api;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import lombok.extern.slf4j.Slf4j;

@Api("list")
@Path("/list")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@PublicApi
@Slf4j
public class EventAttributesResource {
  @Inject private EventRepository eventRepository;

  @GET
  @Path("/environments")
  public List<String> listEnvironments() {
    ArrayList<EventEntity> events = Lists.newArrayList(eventRepository.findAll());
    return events.stream().map(EventEntity::getEnvironment).map(Enum::name).distinct().collect(Collectors.toList());
  }

  @GET
  @Path("/services")
  public List<String> listServices() {
    ArrayList<EventEntity> events = Lists.newArrayList(eventRepository.findAll());
    return events.stream().map(EventEntity::getServiceName).distinct().collect(Collectors.toList());
  }

  @GET
  @Path("/releases")
  public List<String> listReleases() {
    ArrayList<EventEntity> events = Lists.newArrayList(eventRepository.findAll());
    return events.stream().map(EventEntity::getRelease).distinct().collect(Collectors.toList());
  }

  @GET
  @Path("/versions")
  public List<String> listVersions() {
    ArrayList<EventEntity> events = Lists.newArrayList(eventRepository.findAll());
    return events.stream().map(EventEntity::getBuildVersion).distinct().collect(Collectors.toList());
  }
}
