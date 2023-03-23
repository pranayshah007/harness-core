/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.downtime.resources;

import static io.harness.rule.OwnerRule.VARSHA_LALWANI;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CvNextGenTestBase;
import io.harness.category.element.UnitTests;
import io.harness.cvng.BuilderFactory;
import io.harness.cvng.CVNGTestConstants;
import io.harness.cvng.core.beans.monitoredService.MonitoredServiceDTO;
import io.harness.cvng.core.beans.params.ProjectParams;
import io.harness.cvng.core.services.api.monitoredService.MonitoredServiceService;
import io.harness.cvng.downtime.beans.DowntimeDTO;
import io.harness.cvng.downtime.beans.DowntimeResponse;
import io.harness.cvng.downtime.services.api.DowntimeService;
import io.harness.cvng.servicelevelobjective.beans.MSDropdownResponse;
import io.harness.cvng.servicelevelobjective.beans.MonitoredServiceDetail;
import io.harness.ng.beans.PageResponse;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.rest.RestResponse;
import io.harness.rule.Owner;
import io.harness.rule.ResourceTestRule;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Inject;
import com.google.inject.Injector;
import java.time.temporal.ChronoUnit;
import java.util.List;
import javax.ws.rs.client.Entity;
import javax.ws.rs.core.GenericType;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class DowntimeProjectLevelResourceTest extends CvNextGenTestBase {
  @Inject private Injector injector;

  @Inject private MonitoredServiceService monitoredServiceService;

  @Inject private DowntimeService downtimeService;

  private ProjectParams projectParams;
  private String monitoredServiceIdentifier;

  private DowntimeDTO recurringDowntimeDTO;
  private static DowntimeProjectLevelResource downtimeProjectLevelResource = new DowntimeProjectLevelResource();

  @ClassRule
  public static final ResourceTestRule RESOURCES =
      ResourceTestRule.builder().addResource(downtimeProjectLevelResource).build();

  private String baseUrl;

  private String baseUrlWithIdentifier;
  private ObjectMapper objectMapper;

  @Before
  public void setup() {
    injector.injectMembers(downtimeProjectLevelResource);
    BuilderFactory builderFactory = BuilderFactory.getDefault();
    projectParams = builderFactory.getProjectParams();
    objectMapper = new ObjectMapper();
    baseUrl = String.format("http://localhost:9998/account/%s/org/%s/project/%s/downtime",
        projectParams.getAccountIdentifier(), projectParams.getOrgIdentifier(), projectParams.getProjectIdentifier());
    recurringDowntimeDTO = builderFactory.getRecurringDowntimeDTO();
    baseUrlWithIdentifier = baseUrl + String.format("/identifier/%s", recurringDowntimeDTO.getIdentifier());
    MonitoredServiceDTO monitoredServiceDTO = builderFactory.monitoredServiceDTOBuilder().build();
    monitoredServiceDTO.setSources(MonitoredServiceDTO.Sources.builder().build());
    monitoredServiceService.create(projectParams.getAccountIdentifier(), monitoredServiceDTO);
    monitoredServiceIdentifier = monitoredServiceDTO.getIdentifier();
  }
  @Test
  @Owner(developers = VARSHA_LALWANI)
  @Category(UnitTests.class)
  public void testSaveDowntime() throws JsonProcessingException {
    Response response = RESOURCES.client()
                            .target(baseUrl)
                            .request(MediaType.APPLICATION_JSON_TYPE)
                            .post(Entity.json(objectMapper.writeValueAsString(recurringDowntimeDTO)));
    assertThat(response.getStatus()).isEqualTo(200);
    DowntimeResponse downtimeResponse =
        response.readEntity(new GenericType<RestResponse<DowntimeResponse>>() {}).getResource();
    assertThat(downtimeResponse.getDowntimeDTO()).isEqualTo(recurringDowntimeDTO);
  }

  @Test
  @Owner(developers = VARSHA_LALWANI)
  @Category(UnitTests.class)
  public void testGetDowntime() {
    downtimeService.create(projectParams, recurringDowntimeDTO);
    Response response = RESOURCES.client().target(baseUrlWithIdentifier).request(MediaType.APPLICATION_JSON_TYPE).get();
    assertThat(response.getStatus()).isEqualTo(200);
    DowntimeResponse downtimeResponse =
        response.readEntity(new GenericType<RestResponse<DowntimeResponse>>() {}).getResource();
    assertThat(downtimeResponse.getDowntimeDTO()).isEqualTo(recurringDowntimeDTO);
  }

  @Test
  @Owner(developers = VARSHA_LALWANI)
  @Category(UnitTests.class)
  public void testGetAssociatedMonitoredServices() {
    downtimeService.create(projectParams, recurringDowntimeDTO);
    Response response = RESOURCES.client()
                            .target(baseUrl + "/monitored-services/" + recurringDowntimeDTO.getIdentifier())
                            .request(MediaType.APPLICATION_JSON_TYPE)
                            .get();
    assertThat(response.getStatus()).isEqualTo(200);
    List<MonitoredServiceDetail> monitoredServiceDetails =
        response.readEntity(new GenericType<RestResponse<List<MonitoredServiceDetail>>>() {}).getResource();
    assertThat(monitoredServiceDetails.size()).isEqualTo(1);
    assertThat(monitoredServiceDetails.get(0).getMonitoredServiceIdentifier()).isEqualTo(monitoredServiceIdentifier);
  }

  @Test
  @Owner(developers = VARSHA_LALWANI)
  @Category(UnitTests.class)
  public void testUpdateDowntimeData() throws JsonProcessingException {
    downtimeService.create(projectParams, recurringDowntimeDTO);
    recurringDowntimeDTO.setName("New downtime");
    Response response = RESOURCES.client()
                            .target(baseUrlWithIdentifier)
                            .request(MediaType.APPLICATION_JSON_TYPE)
                            .put(Entity.json(objectMapper.writeValueAsString(recurringDowntimeDTO)));
    assertThat(response.getStatus()).isEqualTo(200);
    DowntimeResponse downtimeResponse =
        response.readEntity(new GenericType<RestResponse<DowntimeResponse>>() {}).getResource();
    assertThat(downtimeResponse.getDowntimeDTO()).isEqualTo(recurringDowntimeDTO);
  }

  @Test
  @Owner(developers = VARSHA_LALWANI)
  @Category(UnitTests.class)
  public void testDeleteDowntimeData() {
    recurringDowntimeDTO.getSpec().getSpec().setStartTime(
        CVNGTestConstants.FIXED_TIME_FOR_TESTS.instant().plus(1, ChronoUnit.HOURS).getEpochSecond());
    downtimeService.create(projectParams, recurringDowntimeDTO);
    Response response = RESOURCES.client()
                            .target(baseUrlWithIdentifier)
                            .queryParam("identifier", recurringDowntimeDTO.getIdentifier())
                            .request(MediaType.APPLICATION_JSON_TYPE)
                            .delete();
    assertThat(response.getStatus()).isEqualTo(200);
    assertThat(response.getStatus()).isEqualTo(200);
    Boolean booleanResponse = response.readEntity(new GenericType<RestResponse<Boolean>>() {}).getResource();
    assertThat(booleanResponse).isEqualTo(true);
  }

  @Test
  @Owner(developers = VARSHA_LALWANI)
  @Category(UnitTests.class)
  public void testListDowntimes() {
    downtimeService.create(projectParams, recurringDowntimeDTO);
    Response response = RESOURCES.client()
                            .target(baseUrl + "/list")
                            .queryParam("pageIndex", 0)
                            .queryParam("pageSize", 20)
                            .request(MediaType.APPLICATION_JSON_TYPE)
                            .get();
    assertThat(response.getStatus()).isEqualTo(200);
  }

  @Test
  @Owner(developers = VARSHA_LALWANI)
  @Category(UnitTests.class)
  public void testGetHistory() {
    downtimeService.create(projectParams, recurringDowntimeDTO);
    Response response = RESOURCES.client()
                            .target(baseUrl + "/history")
                            .queryParam("pageIndex", 0)
                            .queryParam("pageSize", 20)
                            .request(MediaType.APPLICATION_JSON_TYPE)
                            .get();
    assertThat(response.getStatus()).isEqualTo(200);
  }

  @Test
  @Owner(developers = VARSHA_LALWANI)
  @Category(UnitTests.class)
  public void testUpdateDowntimeEnabled() {
    downtimeService.create(projectParams, recurringDowntimeDTO);
    Response response = RESOURCES.client()
                            .target(baseUrl + "/flag/" + recurringDowntimeDTO.getIdentifier())
                            .queryParam("enable", false)
                            .request(MediaType.APPLICATION_JSON_TYPE)
                            .put(Entity.text(""));
    assertThat(response.getStatus()).isEqualTo(200);
    recurringDowntimeDTO.setEnabled(false);
    DowntimeResponse downtimeResponse =
        response.readEntity(new GenericType<RestResponse<DowntimeResponse>>() {}).getResource();
    assertThat(downtimeResponse.getDowntimeDTO()).isEqualTo(recurringDowntimeDTO);
  }

  @Test
  @Owner(developers = VARSHA_LALWANI)
  @Category(UnitTests.class)
  public void testGetSLOAssociatedMonitoredServices() {
    downtimeService.create(projectParams, recurringDowntimeDTO);
    Response response = RESOURCES.client()
                            .target(baseUrl + "/monitored-services")
                            .queryParam("enable", false)
                            .queryParam("pageIndex", 0)
                            .queryParam("pageSize", 20)
                            .request(MediaType.APPLICATION_JSON_TYPE)
                            .get();
    assertThat(response.getStatus()).isEqualTo(200);
    PageResponse<MSDropdownResponse> msDropdownResponsePageResponse =
        response.readEntity(new GenericType<ResponseDTO<PageResponse<MSDropdownResponse>>>() {}).getData();
    assertThat(msDropdownResponsePageResponse.getTotalItems()).isEqualTo(1);
    assertThat(msDropdownResponsePageResponse.getContent().get(0).getIdentifier())
        .isEqualTo(monitoredServiceIdentifier);
  }
}
