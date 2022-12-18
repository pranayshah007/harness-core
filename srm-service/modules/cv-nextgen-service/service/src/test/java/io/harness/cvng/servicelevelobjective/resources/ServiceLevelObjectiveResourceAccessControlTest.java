/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.servicelevelobjective.resources;

import static io.harness.rule.OwnerRule.KAPIL;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CVNGResourceTestWithoutAccessBase;
import io.harness.category.element.UnitTests;
import io.harness.cvng.BuilderFactory;
import io.harness.cvng.core.beans.monitoredService.MonitoredServiceDTO;
import io.harness.rule.Owner;
import io.harness.rule.ResourceTestRule;

import com.google.inject.Inject;
import com.google.inject.Injector;
import java.io.IOException;
import java.util.Map;
import javax.ws.rs.client.Entity;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.yaml.snakeyaml.Yaml;

public class ServiceLevelObjectiveResourceAccessControlTest extends CVNGResourceTestWithoutAccessBase {
  @Inject private Injector injector;
  private MonitoredServiceDTO monitoredServiceDTO;
  private BuilderFactory builderFactory;
  private static ServiceLevelObjectiveResource serviceLevelObjectiveResource = new ServiceLevelObjectiveResource();

  @ClassRule
  public static final ResourceTestRule RESOURCES =
      ResourceTestRule.builder().addResource(serviceLevelObjectiveResource).build();

  @Before
  public void setup() {
    injector.injectMembers(serviceLevelObjectiveResource);
    builderFactory = BuilderFactory.getDefault();
    monitoredServiceDTO = builderFactory.monitoredServiceDTOBuilder().build();
  }

  @Test
  @Owner(developers = KAPIL)
  @Category(UnitTests.class)
  public void testCreate_withFakeAccessControlClient() throws IOException {
    String sloYaml = getYAML("slo/slo-with-threshold-sli.yaml");
    Response response = RESOURCES.client()
                            .target("http://localhost:9998/slo/")
                            .queryParam("accountId", builderFactory.getContext().getAccountId())
                            .request(MediaType.APPLICATION_JSON_TYPE)
                            .post(Entity.json(convertToJson(sloYaml)));
    assertThat(response.getStatus()).isEqualTo(403);
    assertThat(response.readEntity(String.class)).contains("\"message\":\"Missing permission chi_slo_edit on slo\"");
  }

  private static String convertToJson(String yamlString) {
    Yaml yaml = new Yaml();
    Map<String, Object> map = yaml.load(yamlString);

    JSONObject jsonObject = new JSONObject(map);
    return jsonObject.toString();
  }

  private String getYAML(String filePath) throws IOException {
    return getYAML(filePath, monitoredServiceDTO.getIdentifier());
  }

  private String getYAML(String filePath, String monitoredServiceIdentifier) throws IOException {
    String sloYaml = getResource(filePath);
    sloYaml = sloYaml.replace("$projectIdentifier", builderFactory.getContext().getProjectIdentifier());
    sloYaml = sloYaml.replace("$orgIdentifier", builderFactory.getContext().getOrgIdentifier());
    sloYaml = sloYaml.replace("$monitoredServiceRef", monitoredServiceIdentifier);
    sloYaml = sloYaml.replace(
        "$healthSourceRef", monitoredServiceDTO.getSources().getHealthSources().iterator().next().getIdentifier());
    return sloYaml;
  }
}
