package io.harness.cdng.pipeline.steps;

import io.harness.cdng.creator.plan.stage.DeploymentStageConfig;
import io.harness.cdng.service.beans.ServiceYamlV2;
import io.harness.serializer.JsonUtils;

import java.util.HashMap;
import java.util.Map;
import lombok.experimental.UtilityClass;

@UtilityClass
public class MultiDeploymentSpawnerUtils {
  private static final String SERVICE_REF = "serviceRef";
  private static final String SERVICE_INPUTS = "serviceInputs";
  private static final String USE_FROM_STAGE = "useFromStage";

  public static final String SERVICE_REF_EXPRESSION = "<+matrix." + SERVICE_REF + ">";
  public static final String SERVICE_INPUTS_EXPRESSION = "<+matrix." + SERVICE_INPUTS + ">";
  public static final String USE_FROM_STAGE_EXPRESSION = "<+matrix." + USE_FROM_STAGE + ">";

  Map<String, String> getMapFromServiceYaml(ServiceYamlV2 service) {
    Map<String, String> matrixMetadataMap = new HashMap<>();
    matrixMetadataMap.put(SERVICE_REF, service.getServiceRef().getValue());
    matrixMetadataMap.put(SERVICE_INPUTS, JsonUtils.asJson(service.getServiceInputs().getValue()));
    matrixMetadataMap.put(USE_FROM_STAGE, JsonUtils.asJson(service.getUseFromStage()));
    return matrixMetadataMap;
  }

  public String getUuidForMultiDeployment(DeploymentStageConfig config) {
    if (config.getServices() != null) {
      return config.getServices().getUuid();
    }
    if (config.getEnvironments() != null) {
      return config.getEnvironments().getUuid();
    }
    if (config.getEnvironmentGroup() != null) {
      return config.getEnvironmentGroup().getUuid();
    }
    return config.getUuid();
  }
}
