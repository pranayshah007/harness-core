/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.resources;

import static io.harness.rule.OwnerRule.KAPIL;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CVNGResourceTestWithoutAccessBase;
import io.harness.category.element.UnitTests;
import io.harness.cvng.BuilderFactory;
import io.harness.cvng.core.beans.monitoredService.MonitoredServiceDTO;
import io.harness.cvng.core.services.api.MetricPackService;
import io.harness.cvng.core.services.api.monitoredService.MonitoredServiceService;
import io.harness.rule.Owner;
import io.harness.rule.ResourceTestRule;

import com.google.inject.Inject;
import com.google.inject.Injector;
import java.io.IOException;
import javax.ws.rs.client.Entity;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class MonitoredServiceResourceAccessControlTest extends CVNGResourceTestWithoutAccessBase {
  @Inject private Injector injector;
  @Inject private MonitoredServiceService monitoredServiceService;
  @Inject MetricPackService metricPackService;
  private BuilderFactory builderFactory;
  private static MonitoredServiceResource monitoredServiceResource = new MonitoredServiceResource();
  private MonitoredServiceDTO monitoredServiceDTO;

  @Before
  public void setup() {
    injector.injectMembers(monitoredServiceResource);
    builderFactory = BuilderFactory.getDefault();
    metricPackService.createDefaultMetricPackAndThresholds(builderFactory.getContext().getAccountId(),
        builderFactory.getContext().getOrgIdentifier(), builderFactory.getContext().getProjectIdentifier());
    monitoredServiceDTO = builderFactory.monitoredServiceDTOBuilder().build();
    monitoredServiceService.create(builderFactory.getContext().getAccountId(), monitoredServiceDTO);
  }

  @ClassRule
  public static final ResourceTestRule RESOURCES =
      ResourceTestRule.builder().addResource(monitoredServiceResource).build();

  @Test
  @Owner(developers = KAPIL)
  @Category(UnitTests.class)
  public void testSaveMonitoredService_withFakeAccessControlClient() throws IOException {
    String monitoredServiceYaml = getResource("monitoredservice/monitored-service-prometheus.yaml");
    Response response = RESOURCES.client()
                            .target("http://localhost:9998/monitored-service/")
                            .queryParam("accountId", builderFactory.getContext().getAccountId())
                            .request(MediaType.APPLICATION_JSON_TYPE)
                            .post(Entity.json(convertToJson(monitoredServiceYaml)));

    assertThat(response.getStatus()).isEqualTo(403);
    assertThat(response.readEntity(String.class))
        .contains("\"message\":\"Missing permission chi_monitoredservice_edit on monitoredservice\"");
  }

  @Test
  @Owner(developers = KAPIL)
  @Category(UnitTests.class)
  public void testUpdateMonitoredService_withFakeAccessControlClient() throws IOException {
    String monitoredServiceYaml = getResource("monitoredservice/monitored-service-prometheus.yaml");
    Response response = RESOURCES.client()
                            .target("http://localhost:9998/monitored-service/"
                                + "MSIdentifier")
                            .queryParam("accountId", builderFactory.getContext().getAccountId())
                            .request(MediaType.APPLICATION_JSON_TYPE)
                            .put(Entity.json(convertToJson(monitoredServiceYaml)));

    assertThat(response.getStatus()).isEqualTo(403);
    assertThat(response.readEntity(String.class))
        .contains(
            "\"message\":\"Missing permission chi_monitoredservice_edit on monitoredservice with identifier MSIdentifier\"");
  }
}
