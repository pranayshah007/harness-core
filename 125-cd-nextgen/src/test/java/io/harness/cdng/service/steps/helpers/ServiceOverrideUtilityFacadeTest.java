/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.service.steps.helpers;

import static io.harness.rule.OwnerRule.TATHAGAT;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.harness.category.element.UnitTests;
import io.harness.cdng.CDNGTestBase;
import io.harness.cdng.manifest.ManifestConfigType;
import io.harness.cdng.manifest.yaml.GitStore;
import io.harness.cdng.manifest.yaml.ManifestConfig;
import io.harness.cdng.manifest.yaml.ManifestConfigWrapper;
import io.harness.cdng.manifest.yaml.kinds.K8sManifest;
import io.harness.cdng.manifest.yaml.storeConfig.StoreConfigType;
import io.harness.cdng.manifest.yaml.storeConfig.StoreConfigWrapper;
import io.harness.exception.InvalidRequestException;
import io.harness.ng.core.environment.beans.Environment;
import io.harness.ng.core.environment.beans.EnvironmentType;
import io.harness.ng.core.serviceoverride.beans.NGServiceOverridesEntity;
import io.harness.ng.core.serviceoverridev2.beans.NGServiceOverrideConfigV2;
import io.harness.ng.core.serviceoverridev2.beans.ServiceOverridesSpec;
import io.harness.ng.core.serviceoverridev2.beans.ServiceOverridesType;
import io.harness.pms.yaml.ParameterField;
import io.harness.rule.Owner;
import io.harness.yaml.core.variables.NGVariable;
import io.harness.yaml.core.variables.NGVariableType;
import io.harness.yaml.core.variables.StringNGVariable;

import com.google.inject.Inject;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class ServiceOverrideUtilityFacadeTest extends CDNGTestBase {
  @Inject private ServiceOverrideUtilityFacade serviceOverrideUtilityFacade;
  private static final String ENVIRONMENT_REF = "envRef";

  private static final String SERVICE_REF = "serviceRef";

  private static final String ACCOUNT_IDENTIFIER = "accountId";
  private static final String ORG_IDENTIFIER = "orgIdentifier";
  private static final String PROJECT_IDENTIFIER = "projectId";
  private static final String VARIABLES = "variables";
  private static final String MANIFESTS = "manifests";

  private static final NGServiceOverridesEntity basicOverrideEntity =
      NGServiceOverridesEntity.builder()
          .identifier(ENVIRONMENT_REF + "_" + SERVICE_REF)
          .accountId(ACCOUNT_IDENTIFIER)
          .orgIdentifier(ORG_IDENTIFIER)
          .projectIdentifier(PROJECT_IDENTIFIER)
          .type(ServiceOverridesType.ENV_SERVICE_OVERRIDE)
          .environmentRef(ENVIRONMENT_REF)
          .serviceRef(SERVICE_REF)
          .build();

  @Test
  @Owner(developers = TATHAGAT)
  @Category(UnitTests.class)
  public void testGetOverrideVariablesV2() {
    basicOverrideEntity.setIsV2(true);
    basicOverrideEntity.setSpec(ServiceOverridesSpec.builder().build());
    // no variables
    assertThat(serviceOverrideUtilityFacade.getOverrideVariables(basicOverrideEntity)).isNull();
    basicOverrideEntity.setSpec(
        ServiceOverridesSpec.builder()
            .variables(
                List.of(StringNGVariable.builder().name("varA").value(ParameterField.createValueField("valA")).build(),
                    StringNGVariable.builder().name("varB").value(ParameterField.createValueField("valB")).build()))
            .build());
    assertThat(serviceOverrideUtilityFacade.getOverrideVariables(basicOverrideEntity)).hasSize(2);
    assertThat(serviceOverrideUtilityFacade.getOverrideVariables(basicOverrideEntity)
                   .stream()
                   .map(NGVariable::getName)
                   .collect(Collectors.toList()))
        .containsExactlyInAnyOrder("varA", "varB");
  }

  @Test
  @Owner(developers = TATHAGAT)
  @Category(UnitTests.class)
  public void testGetOverrideVariablesV1() {
    basicOverrideEntity.setIsV2(false);
    basicOverrideEntity.setYaml(
        "serviceOverrides:\n  orgIdentifier: orgIdentifier\n  projectIdentifier: projectIdentifier\n  environmentRef: envIdentifier\n  serviceRef: serviceIdentifier\n");
    // no variables
    assertThat(serviceOverrideUtilityFacade.getOverrideVariables(basicOverrideEntity)).isNull();
    basicOverrideEntity.setYaml(
        "serviceOverrides:\n  orgIdentifier: orgIdentifier\n  projectIdentifier: projectIdentifier\n  environmentRef: envIdentifier\n  serviceRef: serviceIdentifier\n  variables: \n    - name: op1\n      value: var1\n      type: String\n    - name: op2\n      value: op2\n      type: String");

    assertThat(serviceOverrideUtilityFacade.getOverrideVariables(basicOverrideEntity)).hasSize(2);
    assertThat(serviceOverrideUtilityFacade.getOverrideVariables(basicOverrideEntity)
                   .stream()
                   .map(NGVariable::getName)
                   .collect(Collectors.toList()))
        .containsExactlyInAnyOrder("op1", "op2");
  }

  @Test
  @Owner(developers = TATHAGAT)
  @Category(UnitTests.class)
  public void testMergeServiceOverridesInputsV2() {
    basicOverrideEntity.setIsV2(true);

    basicOverrideEntity.setSpec(ServiceOverridesSpec.builder().variables(getTestNGVariable()).build());
    HashMap<String, Object> overrideInputs = new HashMap<>();
    Map<String, String> ngVariableInput = new LinkedHashMap<>();
    ngVariableInput.put("name", "varA");
    ngVariableInput.put("value", "valueA");
    ngVariableInput.put("type", "String");

    overrideInputs.put(VARIABLES, List.of(ngVariableInput));

    NGServiceOverrideConfigV2 configV2 =
        serviceOverrideUtilityFacade.mergeServiceOverridesInputs(basicOverrideEntity, overrideInputs);
    assertThat(configV2).isNotNull();
    assertThat(configV2.getSpec()
                   .getVariables()
                   .stream()
                   .map(NGVariable::fetchValue)
                   .map(ParameterField::isExpression)
                   .collect(Collectors.toList()))
        .containsExactlyInAnyOrder(false, false);
    assertThat(configV2.getSpec()
                   .getVariables()
                   .stream()
                   .map(NGVariable::fetchValue)
                   .map(paramVal -> (String) paramVal.getValue())
                   .collect(Collectors.toList()))
        .containsExactlyInAnyOrder("valueA", "valueB");
  }

  @Test
  @Owner(developers = TATHAGAT)
  @Category(UnitTests.class)
  public void testMergeServiceOverridesInputsV1() {
    basicOverrideEntity.setIsV2(false);
    basicOverrideEntity.setYaml(
        "serviceOverrides:\n  orgIdentifier: orgIdentifier\n  projectIdentifier: projectIdentifier\n  environmentRef: envIdentifier\n  serviceRef: serviceIdentifier\n  variables: \n    - name: varA\n      value: <+input>\n      type: String\n    - name: varB\n      value: valueB\n      type: String");

    basicOverrideEntity.setSpec(ServiceOverridesSpec.builder().variables(getTestNGVariable()).build());
    HashMap<String, Object> overrideInputs = new HashMap<>();
    Map<String, String> ngVariableInput = new LinkedHashMap<>();
    ngVariableInput.put("name", "varA");
    ngVariableInput.put("value", "valueAFromYaml");
    ngVariableInput.put("type", "String");

    overrideInputs.put(VARIABLES, List.of(ngVariableInput));

    NGServiceOverrideConfigV2 configV2 =
        serviceOverrideUtilityFacade.mergeServiceOverridesInputs(basicOverrideEntity, overrideInputs);
    assertThat(configV2).isNotNull();
    assertThat(configV2.getSpec()
                   .getVariables()
                   .stream()
                   .map(NGVariable::fetchValue)
                   .map(ParameterField::isExpression)
                   .collect(Collectors.toList()))
        .containsExactlyInAnyOrder(false, false);
    assertThat(configV2.getSpec()
                   .getVariables()
                   .stream()
                   .map(NGVariable::fetchValue)
                   .map(paramVal -> (String) paramVal.getValue())
                   .collect(Collectors.toList()))
        .containsExactlyInAnyOrder("valueAFromYaml", "valueB");
  }

  @Test
  @Owner(developers = TATHAGAT)
  @Category(UnitTests.class)
  public void testMergeServiceOverridesInputsV1NoInputs() {
    basicOverrideEntity.setIsV2(false);
    basicOverrideEntity.setYaml(
        "serviceOverrides:\n  orgIdentifier: orgIdentifier\n  projectIdentifier: projectIdentifier\n  environmentRef: envIdentifier\n  serviceRef: serviceIdentifier\n  variables: \n    - name: varA\n      value: <+input>\n      type: String\n    - name: varB\n      value: valueB\n      type: String");
    NGServiceOverrideConfigV2 configV2 =
        serviceOverrideUtilityFacade.mergeServiceOverridesInputs(basicOverrideEntity, null);
    assertThat(configV2).isNotNull();
    assertThat(configV2.getSpec()
                   .getVariables()
                   .stream()
                   .map(NGVariable::fetchValue)
                   .map(ParameterField::isExpression)
                   .collect(Collectors.toList()))
        .containsExactlyInAnyOrder(true, false);
    assertThat(configV2.getSpec()
                   .getVariables()
                   .stream()
                   .map(NGVariable::fetchValue)
                   .map(paramVal -> (String) paramVal.getValue())
                   .collect(Collectors.toList()))
        .containsExactlyInAnyOrder(null, "valueB");
  }

  @Test
  @Owner(developers = TATHAGAT)
  @Category(UnitTests.class)
  public void testMergeServiceOverridesInputsV2NoInputs() {
    basicOverrideEntity.setIsV2(true);
    basicOverrideEntity.setSpec(ServiceOverridesSpec.builder().variables(getTestNGVariable()).build());
    basicOverrideEntity.setYaml(null);
    NGServiceOverrideConfigV2 configV2 =
        serviceOverrideUtilityFacade.mergeServiceOverridesInputs(basicOverrideEntity, null);
    assertThat(configV2).isNotNull();
    assertThat(configV2.getSpec()
                   .getVariables()
                   .stream()
                   .map(NGVariable::fetchValue)
                   .map(ParameterField::isExpression)
                   .collect(Collectors.toList()))
        .containsExactlyInAnyOrder(true, false);
    assertThat(configV2.getSpec()
                   .getVariables()
                   .stream()
                   .map(NGVariable::fetchValue)
                   .map(paramVal -> (String) paramVal.getValue())
                   .collect(Collectors.toList()))
        .containsExactlyInAnyOrder(null, "valueB");
  }

  @Test
  @Owner(developers = TATHAGAT)
  @Category(UnitTests.class)
  public void testMergeEnvInputsV2() throws IOException {
    basicOverrideEntity.setIsV2(true);
    basicOverrideEntity.setSpec(
        ServiceOverridesSpec.builder().variables(getTestNGVariable()).manifests(List.of(getTestManifest())).build());
    Environment envEntity = Environment.builder().identifier(ENVIRONMENT_REF).isMigratedToOverride(true).build();

    HashMap<String, Object> overrideInputs = new HashMap<>();
    overrideInputs.put("identifier", ENVIRONMENT_REF);
    overrideInputs.put("type", "Production");

    Map<String, Object> ngVariableInput = new LinkedHashMap<>();
    ngVariableInput.put("name", "varA");
    ngVariableInput.put("value", "valueA");
    ngVariableInput.put("type", "String");
    overrideInputs.put(VARIABLES, List.of(ngVariableInput));

    Map<String, Object> overrideNodeInput = getOverrideNodeInput();

    overrideInputs.put("overrides", overrideNodeInput);

    NGServiceOverrideConfigV2 configV2 =
        serviceOverrideUtilityFacade.mergeEnvironmentInputs(basicOverrideEntity, envEntity, overrideInputs);
    assertThat(configV2).isNotNull();
    assertThat(configV2.getSpec()
                   .getVariables()
                   .stream()
                   .map(NGVariable::fetchValue)
                   .map(ParameterField::isExpression)
                   .collect(Collectors.toList()))
        .containsExactlyInAnyOrder(false, false);
    assertThat(configV2.getSpec()
                   .getVariables()
                   .stream()
                   .map(NGVariable::fetchValue)
                   .map(paramVal -> (String) paramVal.getValue())
                   .collect(Collectors.toList()))
        .containsExactlyInAnyOrder("valueA", "valueB");
    assertThat(configV2.getSpec().getManifests().get(0).getManifest().getIdentifier()).isEqualTo("manifest1");
    assertThat(configV2.getSpec()
                   .getManifests()
                   .get(0)
                   .getManifest()
                   .getSpec()
                   .getStoreConfig()
                   .getConnectorReference()
                   .isExpression())
        .isFalse();

    assertThat(configV2.getSpec()
                   .getManifests()
                   .get(0)
                   .getManifest()
                   .getSpec()
                   .getStoreConfig()
                   .getConnectorReference()
                   .getValue())
        .isEqualTo("connectorRefFromInput");
  }

  @Test
  @Owner(developers = TATHAGAT)
  @Category(UnitTests.class)
  public void testMergeEnvInputsV2OnlyVariables() throws IOException {
    basicOverrideEntity.setIsV2(true);
    basicOverrideEntity.setSpec(ServiceOverridesSpec.builder().variables(getTestNGVariable()).build());
    Environment envEntity = Environment.builder().identifier(ENVIRONMENT_REF).isMigratedToOverride(true).build();

    HashMap<String, Object> overrideInputs = new HashMap<>();
    overrideInputs.put("identifier", ENVIRONMENT_REF);
    overrideInputs.put("type", "Production");

    Map<String, Object> ngVariableInput = new LinkedHashMap<>();
    ngVariableInput.put("name", "varA");
    ngVariableInput.put("value", "valueAFromInput");
    ngVariableInput.put("type", "String");
    overrideInputs.put(VARIABLES, List.of(ngVariableInput));

    NGServiceOverrideConfigV2 configV2 =
        serviceOverrideUtilityFacade.mergeEnvironmentInputs(basicOverrideEntity, envEntity, overrideInputs);
    assertThat(configV2).isNotNull();
    assertThat(configV2.getSpec()
                   .getVariables()
                   .stream()
                   .map(NGVariable::fetchValue)
                   .map(ParameterField::isExpression)
                   .collect(Collectors.toList()))
        .containsExactlyInAnyOrder(false, false);
    assertThat(configV2.getSpec()
                   .getVariables()
                   .stream()
                   .map(NGVariable::fetchValue)
                   .map(paramVal -> (String) paramVal.getValue())
                   .collect(Collectors.toList()))
        .containsExactlyInAnyOrder("valueAFromInput", "valueB");
  }

  @Test
  @Owner(developers = TATHAGAT)
  @Category(UnitTests.class)
  public void testMergeEnvInputsV1() throws IOException {
    Environment envEntity =
        Environment.builder()
            .accountId(ACCOUNT_IDENTIFIER)
            .orgIdentifier(ORG_IDENTIFIER)
            .projectIdentifier(PROJECT_IDENTIFIER)
            .identifier(ENVIRONMENT_REF)
            .isMigratedToOverride(false)
            .type(EnvironmentType.Production)
            .yaml(
                "environment:\n  name: envRef\n  identifier: env1\n  description: \"\"\n  tags: {}\n  type: Production\n  orgIdentifier: default\n  projectIdentifier: Project1\n  variables:\n    - name: varA\n      type: String\n      value: <+input>\n      description: \"\"\n    - name: varB\n      type: String\n      value: valueB\n      description: \"\"\n  overrides:\n    manifests:\n      - manifest:\n          identifier: manifest1\n          type: Values\n          spec:\n            store:\n              type: Github\n              spec:\n                connectorRef: <+input>\n                gitFetchType: Branch\n                paths:\n                  - random\n                repoName: random\n                branch: random\n")
            .build();

    HashMap<String, Object> overrideInputs = new HashMap<>();
    overrideInputs.put("identifier", ENVIRONMENT_REF);
    overrideInputs.put("type", "Production");

    Map<String, Object> ngVariableInput = new LinkedHashMap<>();
    ngVariableInput.put("name", "varA");
    ngVariableInput.put("value", "valueA");
    ngVariableInput.put("type", "String");
    overrideInputs.put(VARIABLES, List.of(ngVariableInput));

    Map<String, Object> overrideNodeInput = getOverrideNodeInput();

    overrideInputs.put("overrides", overrideNodeInput);

    NGServiceOverrideConfigV2 configV2 =
        serviceOverrideUtilityFacade.mergeEnvironmentInputs(null, envEntity, overrideInputs);

    assertThat(configV2).isNotNull();
    assertThat(configV2.getSpec()
                   .getVariables()
                   .stream()
                   .map(NGVariable::fetchValue)
                   .map(ParameterField::isExpression)
                   .collect(Collectors.toList()))
        .containsExactlyInAnyOrder(false, false);
    assertThat(configV2.getSpec()
                   .getVariables()
                   .stream()
                   .map(NGVariable::fetchValue)
                   .map(paramVal -> (String) paramVal.getValue())
                   .collect(Collectors.toList()))
        .containsExactlyInAnyOrder("valueA", "valueB");
    assertThat(configV2.getSpec().getManifests().get(0).getManifest().getIdentifier()).isEqualTo("manifest1");
    assertThat(configV2.getSpec()
                   .getManifests()
                   .get(0)
                   .getManifest()
                   .getSpec()
                   .getStoreConfig()
                   .getConnectorReference()
                   .isExpression())
        .isFalse();

    assertThat(configV2.getSpec()
                   .getManifests()
                   .get(0)
                   .getManifest()
                   .getSpec()
                   .getStoreConfig()
                   .getConnectorReference()
                   .getValue())
        .isEqualTo("connectorRefFromInput");
  }

  @Test
  @Owner(developers = TATHAGAT)
  @Category(UnitTests.class)
  public void testMergeEnvInputsV2NoInputs() throws IOException {
    basicOverrideEntity.setIsV2(true);
    basicOverrideEntity.setSpec(ServiceOverridesSpec.builder().variables(getTestNGVariable()).build());
    Environment envEntity = Environment.builder().identifier(ENVIRONMENT_REF).isMigratedToOverride(true).build();

    NGServiceOverrideConfigV2 configV2 =
        serviceOverrideUtilityFacade.mergeEnvironmentInputs(basicOverrideEntity, envEntity, null);
    assertThat(configV2).isNotNull();
    assertThat(configV2.getSpec()
                   .getVariables()
                   .stream()
                   .map(NGVariable::fetchValue)
                   .map(ParameterField::isExpression)
                   .collect(Collectors.toList()))
        .containsExactlyInAnyOrder(true, false);
    assertThat(configV2.getSpec()
                   .getVariables()
                   .stream()
                   .map(NGVariable::fetchValue)
                   .map(paramVal -> (String) paramVal.getValue())
                   .collect(Collectors.toList()))
        .containsExactlyInAnyOrder(null, "valueB");
  }

  @Test
  @Owner(developers = TATHAGAT)
  @Category(UnitTests.class)
  public void testMergeEnvInputsV1NoInputs() throws IOException {
    Environment envEntity =
        Environment.builder()
            .accountId(ACCOUNT_IDENTIFIER)
            .orgIdentifier(ORG_IDENTIFIER)
            .projectIdentifier(PROJECT_IDENTIFIER)
            .identifier(ENVIRONMENT_REF)
            .isMigratedToOverride(false)
            .yaml(
                "environment:\n  name: envRef\n  identifier: env1\n  description: \"\"\n  tags: {}\n  type: Production\n  orgIdentifier: default\n  projectIdentifier: Project1\n  variables:\n    - name: varA\n      type: String\n      value: <+input>\n      description: \"\"\n    - name: varB\n      type: String\n      value: valueBFromEnv\n      description: \"\"\n  overrides:\n    manifests:\n      - manifest:\n          identifier: manifest1\n          type: Values\n          spec:\n            store:\n              type: Github\n              spec:\n                connectorRef: <+input>\n                gitFetchType: Branch\n                paths:\n                  - random\n                repoName: random\n                branch: random\n")
            .build();

    NGServiceOverrideConfigV2 configV2 = serviceOverrideUtilityFacade.mergeEnvironmentInputs(null, envEntity, null);
    assertThat(configV2).isNotNull();
    assertThat(configV2.getSpec()
                   .getVariables()
                   .stream()
                   .map(NGVariable::fetchValue)
                   .map(ParameterField::isExpression)
                   .collect(Collectors.toList()))
        .containsExactlyInAnyOrder(true, false);
    assertThat(configV2.getSpec()
                   .getVariables()
                   .stream()
                   .map(NGVariable::fetchValue)
                   .map(paramVal -> (String) paramVal.getValue())
                   .collect(Collectors.toList()))
        .containsExactlyInAnyOrder(null, "valueBFromEnv");
  }

  @Test
  @Owner(developers = TATHAGAT)
  @Category(UnitTests.class)
  public void testMergeEnvInputsV2NoOverrideEntity() {
    Environment envEntity = Environment.builder().identifier(ENVIRONMENT_REF).isMigratedToOverride(true).build();
    assertThatThrownBy(() -> serviceOverrideUtilityFacade.mergeEnvironmentInputs(null, envEntity, null))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("Environment is migrated to support overrides V2, but override does not exist");
  }

  private static Map<String, Object> getOverrideNodeInput() {
    Map<String, Object> overrideNodeInput = new HashMap<>();
    List<Object> manifestsList = new ArrayList<>();

    manifestsList.add(Map.of("manifest",
        Map.of("identifier", "manifest1", "type", "Values", "spec",
            Map.of("store", Map.of("type", "Github", "spec", Map.of("connectorRef", "connectorRefFromInput"))))));
    overrideNodeInput.put("manifests", manifestsList);
    return overrideNodeInput;
  }

  private static List<NGVariable> getTestNGVariable() {
    return List.of(StringNGVariable.builder()
                       .name("varA")
                       .value(ParameterField.createExpressionField(true, "<+input>", null, true))
                       .type(NGVariableType.STRING)
                       .build(),
        StringNGVariable.builder()
            .name("varB")
            .value(ParameterField.createValueField("valueB"))
            .type(NGVariableType.STRING)
            .build());
  }

  private static ManifestConfigWrapper getTestManifest() {
    return ManifestConfigWrapper.builder()
        .manifest(ManifestConfig.builder()
                      .identifier("manifest1")
                      .type(ManifestConfigType.KUSTOMIZE_PATCHES)
                      .spec(K8sManifest.builder()
                                .identifier("manifest1")
                                .store(ParameterField.createValueField(
                                    StoreConfigWrapper.builder()
                                        .type(StoreConfigType.GIT)
                                        .spec(GitStore.builder()
                                                  .folderPath(ParameterField.createValueField("manifests/"))
                                                  .connectorRef(ParameterField.createExpressionField(
                                                      true, "<+input>", null, true))
                                                  .branch(ParameterField.createValueField("main"))
                                                  .paths(ParameterField.createValueField(List.of("path1", "path2")))
                                                  .build())
                                        .build()))
                                .build())
                      .build())
        .build();
  }
}
