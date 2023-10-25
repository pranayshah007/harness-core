/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.autodiscovery.resources;

import static io.harness.rule.OwnerRule.DEEPAK_CHHIKARA;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CvNextGenTestBase;
import io.harness.category.element.UnitTests;
import io.harness.cvng.BuilderFactory;
import io.harness.cvng.autodiscovery.beans.AutoDiscoveryAsyncResponseDTO;
import io.harness.cvng.autodiscovery.beans.AutoDiscoveryAsyncResponseDTO.AsyncStatus;
import io.harness.cvng.autodiscovery.beans.AutoDiscoveryRequestDTO;
import io.harness.cvng.autodiscovery.beans.AutoDiscoveryResponseDTO;
import io.harness.cvng.core.services.api.monitoredService.MonitoredServiceService;
import io.harness.rest.RestResponse;
import io.harness.rule.Owner;
import io.harness.rule.ResourceTestRule;

import com.google.inject.Inject;
import com.google.inject.Injector;
import javax.ws.rs.client.Entity;
import javax.ws.rs.core.GenericType;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class AutoDiscoveryResourceTest extends CvNextGenTestBase {
  @Inject private Injector injector;
  @Inject MonitoredServiceService monitoredServiceService;
  private BuilderFactory builderFactory;
  private static final AutoDiscoveryResource autoDiscoveryResource = new AutoDiscoveryResource();

  @ClassRule
  public static final ResourceTestRule RESOURCES =
      ResourceTestRule.builder().addResource(autoDiscoveryResource).build();

  @Before
  public void setup() {
    injector.injectMembers(autoDiscoveryResource);
    builderFactory = BuilderFactory.getDefault();
  }

  @Test
  @Owner(developers = DEEPAK_CHHIKARA)
  @Category(UnitTests.class)
  public void createAutoDiscovery() {
    AutoDiscoveryRequestDTO autoDiscoveryRequestDTO = builderFactory.getAutoDiscoveryRequestDTO();
    autoDiscoveryRequestDTO.setAgentIdentifier("agent1");
    Response response = RESOURCES.client()
                            .target("http://localhost:9998/auto-discovery/"
                                + "create")
                            .queryParam("accountId", builderFactory.getContext().getAccountId())
                            .queryParam("orgIdentifier", builderFactory.getContext().getOrgIdentifier())
                            .queryParam("projectIdentifier", builderFactory.getContext().getProjectIdentifier())
                            .request(MediaType.APPLICATION_JSON_TYPE)
                            .post(Entity.json(autoDiscoveryRequestDTO));
    assertThat(response.getStatus()).isEqualTo(200);
    RestResponse<AutoDiscoveryResponseDTO> restResponse = response.readEntity(new GenericType<>() {});
    assertThat(restResponse.getResource().getMonitoredServicesCreated()).contains("service2_envIdentifier");
    assertThat(restResponse.getResource().getMonitoredServicesCreated()).contains("service1_envIdentifier");
    assertThat(restResponse.getResource().getServiceDependenciesImported()).isEqualTo(1L);
  }

  @Test
  @Owner(developers = DEEPAK_CHHIKARA)
  @Category(UnitTests.class)
  public void createAutoDiscoveryWithExistingMonitoredService() {
    monitoredServiceService.createDefault(builderFactory.getProjectParams(), "service1", "envIdentifier");
    AutoDiscoveryRequestDTO autoDiscoveryRequestDTO = builderFactory.getAutoDiscoveryRequestDTO();
    autoDiscoveryRequestDTO.setAgentIdentifier("agent2");
    Response response = RESOURCES.client()
                            .target("http://localhost:9998/auto-discovery/"
                                + "create")
                            .queryParam("accountId", builderFactory.getContext().getAccountId())
                            .queryParam("orgIdentifier", builderFactory.getContext().getOrgIdentifier())
                            .queryParam("projectIdentifier", builderFactory.getContext().getProjectIdentifier())
                            .request(MediaType.APPLICATION_JSON_TYPE)
                            .post(Entity.json(autoDiscoveryRequestDTO));
    assertThat(response.getStatus()).isEqualTo(200);
    RestResponse<AutoDiscoveryResponseDTO> restResponse = response.readEntity(new GenericType<>() {});
    assertThat(restResponse.getResource().getMonitoredServicesCreated()).contains("service2_envIdentifier");
    assertThat(restResponse.getResource().getMonitoredServicesCreated()).doesNotContain("service1_envIdentifier");
    assertThat(restResponse.getResource().getServiceDependenciesImported()).isEqualTo(1L);
  }

  @Test
  @Owner(developers = DEEPAK_CHHIKARA)
  @Category(UnitTests.class)
  public void reImportAutoDiscovery() {
    AutoDiscoveryRequestDTO autoDiscoveryRequestDTO = builderFactory.getAutoDiscoveryRequestDTO();
    autoDiscoveryRequestDTO.setAgentIdentifier("agent1");
    Response response = RESOURCES.client()
                            .target("http://localhost:9998/auto-discovery/"
                                + "re-import")
                            .queryParam("accountId", builderFactory.getContext().getAccountId())
                            .queryParam("orgIdentifier", builderFactory.getContext().getOrgIdentifier())
                            .queryParam("projectIdentifier", builderFactory.getContext().getProjectIdentifier())
                            .request(MediaType.APPLICATION_JSON_TYPE)
                            .post(Entity.json(autoDiscoveryRequestDTO));
    assertThat(response.getStatus()).isEqualTo(200);
    RestResponse<AutoDiscoveryAsyncResponseDTO> restResponse = response.readEntity(new GenericType<>() {});
    assertThat(restResponse.getResource().getStatus()).isEqualTo(AsyncStatus.RUNNING);
  }

  @Test
  @Owner(developers = DEEPAK_CHHIKARA)
  @Category(UnitTests.class)
  public void reImportStatusAutoDiscovery() {
    AutoDiscoveryRequestDTO autoDiscoveryRequestDTO = builderFactory.getAutoDiscoveryRequestDTO();
    autoDiscoveryRequestDTO.setAgentIdentifier("agent1");
    Response response = RESOURCES.client()
                            .target("http://localhost:9998/auto-discovery/"
                                + "re-import")
                            .queryParam("accountId", builderFactory.getContext().getAccountId())
                            .queryParam("orgIdentifier", builderFactory.getContext().getOrgIdentifier())
                            .queryParam("projectIdentifier", builderFactory.getContext().getProjectIdentifier())
                            .request(MediaType.APPLICATION_JSON_TYPE)
                            .post(Entity.json(autoDiscoveryRequestDTO));
    assertThat(response.getStatus()).isEqualTo(200);
    RestResponse<AutoDiscoveryAsyncResponseDTO> restResponse = response.readEntity(new GenericType<>() {});
    assertThat(restResponse.getResource().getStatus()).isEqualTo(AsyncStatus.RUNNING);
    String correlationId = restResponse.getResource().getCorrelationId();
    response = RESOURCES.client()
                   .target("http://localhost:9998/auto-discovery/"
                       + "status/" + correlationId)
                   .queryParam("accountId", builderFactory.getContext().getAccountId())
                   .queryParam("orgIdentifier", builderFactory.getContext().getOrgIdentifier())
                   .queryParam("projectIdentifier", builderFactory.getContext().getProjectIdentifier())
                   .request(MediaType.APPLICATION_JSON_TYPE)
                   .get();
    restResponse = response.readEntity(new GenericType<>() {});
    assertThat(restResponse.getResource().getStatus()).isEqualTo(AsyncStatus.COMPLETED);
  }
}
