package io.harness.cdng.pipeline.steps;

import io.harness.cdng.creator.plan.stage.DeploymentStageConfig;
import io.harness.cdng.creator.plan.stage.DeploymentStageNode;
import io.harness.cdng.environment.yaml.EnvironmentYamlV2;
import io.harness.cdng.infra.yaml.InfraStructureDefinitionYaml;
import io.harness.cdng.service.beans.ServiceYamlV2;
import io.harness.data.structure.UUIDGenerator;
import io.harness.pms.yaml.ParameterField;
import io.harness.serializer.JsonUtils;

import java.util.HashMap;
import java.util.Map;
import lombok.experimental.UtilityClass;

@UtilityClass
public class MultiDeploymentSpawnerUtils {
  private static final String SERVICE_REF = "serviceRef";
  private static final String SERVICE_INPUTS = "serviceInputs";
  private static final String USE_FROM_STAGE = "useFromStage";

  private static final String ENVIRONMENT_REF = "environmentRef";
  private static final String ENVIRONMENT_INPUTS = "environmentInputs";
  private static final String SERVICE_OVERRIDE_INPUTS = "serviceOverrideInputs";
  private static final String GIT_OPS_CLUSTERS = "gitOpsClusters";
  private static final String INFRA_IDENTIFIER = "identifier";
  private static final String INFRA_INPUTS = "infraInputs";
  private static final String MATRIX_EXPRESSION = "<+matrix.%s>";

  private static final String ENVIRONMENT_REF_EXPRESSION = String.format(MATRIX_EXPRESSION, "environmentRef");
  private static final String ENVIRONMENT_INPUTS_EXPRESSION = String.format(MATRIX_EXPRESSION, "environmentInputs");
  private static final String SERVICE_OVERRIDE_INPUTS_EXPRESSION =
      String.format(MATRIX_EXPRESSION, "serviceOverrideInputs");
  private static final String GIT_OPS_CLUSTERS_EXPRESSION = String.format(MATRIX_EXPRESSION, "gitOpsClusters");

  private static final String INFRA_IDENTIFIER_EXPRESSION = String.format(MATRIX_EXPRESSION, "identifier");
  private static final String INFRA_INPUTS_EXPRESSION = String.format(MATRIX_EXPRESSION, "infraInputs");

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

  Map<String, String> getMapFromEnvironmentYaml(
      EnvironmentYamlV2 environmentYamlV2, InfraStructureDefinitionYaml infraStructureDefinitionYaml) {
    Map<String, String> matrixMetadataMap = new HashMap<>();
    matrixMetadataMap.put(ENVIRONMENT_REF, environmentYamlV2.getEnvironmentRef().getValue());
    matrixMetadataMap.put(ENVIRONMENT_INPUTS, JsonUtils.asJson(environmentYamlV2.getEnvironmentInputs().getValue()));
    matrixMetadataMap.put(
        SERVICE_OVERRIDE_INPUTS, JsonUtils.asJson(environmentYamlV2.getServiceOverrideInputs().getValue()));
    matrixMetadataMap.put(GIT_OPS_CLUSTERS, JsonUtils.asJson(environmentYamlV2.getGitOpsClusters().getValue()));
    matrixMetadataMap.put(INFRA_IDENTIFIER, infraStructureDefinitionYaml.getIdentifier().getValue());
    matrixMetadataMap.put(INFRA_INPUTS, JsonUtils.asJson(infraStructureDefinitionYaml.getInputs().getValue()));
    return matrixMetadataMap;
  }

  public String getUuidForMultiDeployment(DeploymentStageNode node) {
    DeploymentStageConfig config = node.getDeploymentStageConfig();
    if (config.getServices() != null) {
      return config.getServices().getUuid();
    }
    if (config.getEnvironments() != null) {
      return config.getEnvironments().getUuid();
    }
    if (config.getEnvironmentGroup() != null) {
      return config.getEnvironmentGroup().getUuid();
    }
    return node.getUuid();
  }

  public ServiceYamlV2 getServiceYamlV2Node() {
    return ServiceYamlV2.builder()
        .uuid(UUIDGenerator.generateUuid())
        .serviceRef(ParameterField.createExpressionField(true, SERVICE_REF_EXPRESSION, null, true))
        .serviceInputs(ParameterField.createExpressionField(true, SERVICE_INPUTS_EXPRESSION, null, false))
        .build();
  }

  public EnvironmentYamlV2 getEnvironmentYamlV2Node() {
    InfraStructureDefinitionYaml infraStructureDefinitionYaml =
        InfraStructureDefinitionYaml.builder()
            .identifier(ParameterField.createExpressionField(true, INFRA_IDENTIFIER_EXPRESSION, null, true))
            .inputs(ParameterField.createExpressionField(true, INFRA_INPUTS_EXPRESSION, null, false))
            .build();
    return EnvironmentYamlV2.builder()
        .environmentRef(ParameterField.createExpressionField(true, ENVIRONMENT_REF_EXPRESSION, null, true))
        .environmentInputs(ParameterField.createExpressionField(true, ENVIRONMENT_INPUTS_EXPRESSION, null, false))
        .serviceOverrideInputs(
            ParameterField.createExpressionField(true, SERVICE_OVERRIDE_INPUTS_EXPRESSION, null, false))
        .gitOpsClusters(ParameterField.createExpressionField(true, GIT_OPS_CLUSTERS_EXPRESSION, null, false))
        .infrastructureDefinition(ParameterField.createValueField(infraStructureDefinitionYaml))
        .build();
  }
}
