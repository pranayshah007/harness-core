package io.harness.cvng.servicelevelobjective.resources;

import static io.harness.rule.OwnerRule.KAMAL;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CvNextGenTestBase;
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

public class ServiceLevelObjectiveResourceTest extends CvNextGenTestBase {
  @Inject private Injector injector;
  @Inject private MonitoredServiceService monitoredServiceService;
  @Inject private MetricPackService metricPackService;
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
    metricPackService.createDefaultMetricPackAndThresholds(builderFactory.getContext().getAccountId(),
        builderFactory.getContext().getOrgIdentifier(), builderFactory.getContext().getProjectIdentifier());
    monitoredServiceDTO = builderFactory.monitoredServiceDTOBuilder().build();
    monitoredServiceService.create(builderFactory.getContext().getAccountId(), monitoredServiceDTO);
  }

  @Test
  @Owner(developers = KAMAL)
  @Category(UnitTests.class)
  public void testCreate_withRollingSLOTarget() throws IOException {
    String sloYaml = getYAML("slo/slo-with-rolling-target.yaml");
    Response response = RESOURCES.client()
                            .target("http://localhost:9998/slo/")
                            .queryParam("accountId", builderFactory.getContext().getAccountId())
                            .request(MediaType.APPLICATION_JSON_TYPE)
                            .post(Entity.json(convertToJson(sloYaml)));
    assertThat(response.getStatus()).isEqualTo(200);
    String jsonResponse = response.readEntity(String.class);
    // TODO: we need to find a library to assert json responses in a better way.
    assertThat(jsonResponse)
        .contains(
            "{\"type\":\"Threshold\",\"spec\":{\"metric1\":\"metric2\",\"thresholdValue\":4.0,\"thresholdType\":\"<\"}}");
  }

  @Test
  @Owner(developers = KAMAL)
  @Category(UnitTests.class)
  public void testCreate_withCalenderSLOTarget() throws IOException {
    String sloYaml = getYAML("slo/slo-with-calender-target.yaml");
    Response response = RESOURCES.client()
                            .target("http://localhost:9998/slo/")
                            .queryParam("accountId", builderFactory.getContext().getAccountId())
                            .request(MediaType.APPLICATION_JSON_TYPE)
                            .post(Entity.json(convertToJson(sloYaml)));
    assertThat(response.getStatus()).isEqualTo(200);
  }

  @Test
  @Owner(developers = KAMAL)
  @Category(UnitTests.class)
  public void testCreate_withCalenderSLOTargetInvalid() throws IOException {
    String sloYaml = getYAML("slo/slo-with-calender-target-invalid.yaml");
    Response response = RESOURCES.client()
                            .target("http://localhost:9998/slo/")
                            .queryParam("accountId", builderFactory.getContext().getAccountId())
                            .request(MediaType.APPLICATION_JSON_TYPE)
                            .post(Entity.json(convertToJson(sloYaml)));
    assertThat(response.getStatus()).isEqualTo(400);
    assertThat(response.readEntity(String.class)).contains("\"field\":\"dayOfWeek\",\"message\":\"may not be null\"");
  }

  private static String convertToJson(String yamlString) {
    Yaml yaml = new Yaml();
    Map<String, Object> map = yaml.load(yamlString);

    JSONObject jsonObject = new JSONObject(map);
    return jsonObject.toString();
  }

  private String getYAML(String filePath) throws IOException {
    String sloYaml = getResource(filePath);
    sloYaml = sloYaml.replace("$projectIdentifier", builderFactory.getContext().getProjectIdentifier());
    sloYaml = sloYaml.replace("$orgIdentifier", builderFactory.getContext().getOrgIdentifier());
    sloYaml = sloYaml.replace("$monitoredServiceRef", monitoredServiceDTO.getIdentifier());
    sloYaml = sloYaml.replace(
        "$healthSourceRef", monitoredServiceDTO.getSources().getHealthSources().iterator().next().getIdentifier());
    return sloYaml;
  }
}