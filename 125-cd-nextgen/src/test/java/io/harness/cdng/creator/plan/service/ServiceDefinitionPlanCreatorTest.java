/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.creator.plan.service;

import static io.harness.rule.OwnerRule.IVAN;
import static io.harness.rule.OwnerRule.PRASHANTSHARMA;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.MockitoAnnotations.initMocks;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.cdng.CDNGTestBase;
import io.harness.cdng.service.beans.ServiceConfig;
import io.harness.cdng.service.beans.ServiceUseFromStage;
import io.harness.cdng.visitor.YamlTypes;
import io.harness.ng.core.environment.services.EnvironmentService;
import io.harness.ng.core.k8s.ServiceSpecType;
import io.harness.ng.core.serviceoverride.services.ServiceOverrideService;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationResponse;
import io.harness.pms.yaml.YamlField;
import io.harness.pms.yaml.YamlUtils;
import io.harness.rule.Owner;
import io.harness.serializer.KryoSerializer;

import com.google.inject.Inject;
import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Scanner;
import java.util.Set;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;

@OwnedBy(HarnessTeam.CDC)
public class ServiceDefinitionPlanCreatorTest extends CDNGTestBase {
  @InjectMocks @Inject ServiceDefinitionPlanCreator serviceDefinitionPlanCreator;
  @Mock private EnvironmentService environmentService;
  @Mock private ServiceOverrideService serviceOverrideService;
  @Inject private KryoSerializer kryoSerializer;

  private static final String ACCOUNT_ID = "account_id";
  private static final String ORG_IDENTIFIER = "orgId";
  private static final String PROJ_IDENTIFIER = "projId";
  private static final String SVC_REF = "SVC_REF";
  private static final String ENV_REF = "ENV_REF";

  @Before
  public void setupMocks() {
    initMocks(this);
  }

  @Test
  @Owner(developers = PRASHANTSHARMA)
  @Category(UnitTests.class)
  public void testGetFieldClass() {
    assertThat(serviceDefinitionPlanCreator.getFieldClass()).isEqualTo(YamlField.class);
  }

  @Test
  @Owner(developers = PRASHANTSHARMA)
  @Category(UnitTests.class)
  public void testGetSupportedTypes() {
    Map<String, Set<String>> supportedTypes = serviceDefinitionPlanCreator.getSupportedTypes();
    assertThat(supportedTypes.containsKey(YamlTypes.SERVICE_DEFINITION)).isEqualTo(true);
    assertThat(supportedTypes.get(YamlTypes.SERVICE_DEFINITION).size()).isEqualTo(11);
    assertThat(supportedTypes.get(YamlTypes.SERVICE_DEFINITION))
        .containsOnly(ServiceSpecType.KUBERNETES, ServiceSpecType.SSH, ServiceSpecType.WINRM,
            ServiceSpecType.NATIVE_HELM, ServiceSpecType.SERVERLESS_AWS_LAMBDA, ServiceSpecType.AZURE_WEBAPP,
            ServiceSpecType.ECS, ServiceSpecType.CUSTOM_DEPLOYMENT, ServiceSpecType.ELASTIGROUP, ServiceSpecType.TAS,
            ServiceSpecType.ASG);
  }

  private void checksForDependencies(PlanCreationResponse planCreationResponse, String nodeUuid) {
    assertThat(planCreationResponse.getDependencies().getDependenciesMap().containsKey(nodeUuid)).isEqualTo(true);
    assertThat(planCreationResponse.getDependencies().getDependencyMetadataMap().get(nodeUuid).getMetadataMap().size())
        .isEqualTo(2);
    assertThat(
        planCreationResponse.getDependencies().getDependencyMetadataMap().get(nodeUuid).getMetadataMap().containsKey(
            YamlTypes.UUID))
        .isEqualTo(true);
    assertThat(
        planCreationResponse.getDependencies().getDependencyMetadataMap().get(nodeUuid).getMetadataMap().containsKey(
            YamlTypes.SERVICE_CONFIG))
        .isEqualTo(true);
  }

  @Test
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void testAddDependenciesForConfigFilesWithServiceDefinition() throws IOException {
    LinkedHashMap<String, PlanCreationResponse> planCreationResponseMap = new LinkedHashMap<>();

    ClassLoader classLoader = this.getClass().getClassLoader();
    InputStream yamlFile =
        classLoader.getResourceAsStream("cdng/plan/configfiles/configfiles_test_with_service_definition.yml");
    assertThat(yamlFile).isNotNull();

    String yaml = new Scanner(yamlFile, "UTF-8").useDelimiter("\\A").next();
    yaml = YamlUtils.injectUuid(yaml);
    YamlField serviceField = YamlUtils.readTree(yaml);

    ServiceConfig serviceConfig = ServiceConfig.builder().build();
    String nodeUuid = serviceDefinitionPlanCreator.addDependenciesForConfigFiles(
        serviceField.getNode(), planCreationResponseMap, serviceConfig);

    assertThat(planCreationResponseMap.size()).isEqualTo(1);
    assertThat(planCreationResponseMap.containsKey(nodeUuid)).isEqualTo(true);
    PlanCreationResponse planCreationResponse = planCreationResponseMap.get(nodeUuid);
    checksForDependencies(planCreationResponse, nodeUuid);
    assertThat(planCreationResponse.getDependencies().getDependenciesMap().get(nodeUuid))
        .isEqualTo("serviceDefinition/spec/configFiles");
    assertThat(planCreationResponse.getYamlUpdates()).isNull();
  }

  @Test
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void testAddDependenciesForConfigFilesUnderStagesOverrides() throws IOException {
    LinkedHashMap<String, PlanCreationResponse> planCreationResponseMap = new LinkedHashMap<>();

    ServiceConfig serviceConfig =
        ServiceConfig.builder().useFromStage(ServiceUseFromStage.builder().stage("stage1").build()).build();
    ClassLoader classLoader = this.getClass().getClassLoader();
    InputStream yamlFile = classLoader.getResourceAsStream(
        "cdng/plan/configfiles/configfiles_test_with_configfiles_under_stageoverride.yml");
    assertThat(yamlFile).isNotNull();

    String yaml = new Scanner(yamlFile, "UTF-8").useDelimiter("\\A").next();
    yaml = YamlUtils.injectUuid(yaml);
    YamlField serviceField = YamlUtils.readTree(yaml);

    String nodeUuid = serviceDefinitionPlanCreator.addDependenciesForConfigFiles(
        serviceField.getNode(), planCreationResponseMap, serviceConfig);

    assertThat(planCreationResponseMap.size()).isEqualTo(1);
    assertThat(planCreationResponseMap.containsKey(nodeUuid)).isEqualTo(true);
    PlanCreationResponse planCreationResponse1 = planCreationResponseMap.get(nodeUuid);
    checksForDependencies(planCreationResponse1, nodeUuid);
    assertThat(planCreationResponse1.getDependencies().getDependenciesMap().get(nodeUuid))
        .isEqualTo("stageOverrides/configFiles");

    assertThat(planCreationResponse1.getYamlUpdates()).isNull();
  }

  @Test
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void testAddDependenciesForConfigFilesWithStageOverrideHavingEmptyConfigFiles() throws IOException {
    LinkedHashMap<String, PlanCreationResponse> planCreationResponseMap = new LinkedHashMap<>();

    ServiceConfig actualServiceConfig =
        ServiceConfig.builder().useFromStage(ServiceUseFromStage.builder().stage("stage1").build()).build();
    ClassLoader classLoader = this.getClass().getClassLoader();
    InputStream yamlFile =
        classLoader.getResourceAsStream("cdng/plan/configfiles/configfiles_test_stage_override_configfiles_empty.yml");
    assertThat(yamlFile).isNotNull();

    String yaml = new Scanner(yamlFile, "UTF-8").useDelimiter("\\A").next();
    yaml = YamlUtils.injectUuid(yaml);
    YamlField serviceField = YamlUtils.readTree(yaml);

    String nodeUuid = serviceDefinitionPlanCreator.addDependenciesForConfigFiles(
        serviceField.getNode(), planCreationResponseMap, actualServiceConfig);

    assertThat(planCreationResponseMap.size()).isEqualTo(1);
    assertThat(planCreationResponseMap.containsKey(nodeUuid)).isEqualTo(true);
    PlanCreationResponse planCreationResponse1 = planCreationResponseMap.get(nodeUuid);
    checksForDependencies(planCreationResponse1, nodeUuid);
    assertThat(planCreationResponse1.getDependencies().getDependenciesMap().get(nodeUuid))
        .isEqualTo("stageOverrides/configFiles");

    assertThat(planCreationResponse1.getYamlUpdates()).isNotNull();
  }

  @Test
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void testAddDependenciesForConfigFilesWithStageOverrideHavingWithoutConfigFiles() throws IOException {
    LinkedHashMap<String, PlanCreationResponse> planCreationResponseMap = new LinkedHashMap<>();

    ServiceConfig actualServiceConfig =
        ServiceConfig.builder().useFromStage(ServiceUseFromStage.builder().stage("stage1").build()).build();
    ClassLoader classLoader = this.getClass().getClassLoader();
    InputStream yamlFile = classLoader.getResourceAsStream(
        "cdng/plan/configfiles/configfiles_test_stage_override_without_configfiles.yml");
    assertThat(yamlFile).isNotNull();

    String yaml = new Scanner(yamlFile, "UTF-8").useDelimiter("\\A").next();
    yaml = YamlUtils.injectUuid(yaml);
    YamlField serviceField = YamlUtils.readTree(yaml);

    String nodeUuid = serviceDefinitionPlanCreator.addDependenciesForConfigFiles(
        serviceField.getNode(), planCreationResponseMap, actualServiceConfig);

    assertThat(planCreationResponseMap.size()).isEqualTo(1);
    assertThat(planCreationResponseMap.containsKey(nodeUuid)).isEqualTo(true);
    PlanCreationResponse planCreationResponse1 = planCreationResponseMap.get(nodeUuid);
    checksForDependencies(planCreationResponse1, nodeUuid);
    assertThat(planCreationResponse1.getDependencies().getDependenciesMap().get(nodeUuid))
        .isEqualTo("stageOverrides/configFiles");

    assertThat(planCreationResponse1.getYamlUpdates()).isNotNull();
  }

  @Test
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void testAddDependenciesForConfigFilesWithoutStageOverride() throws IOException {
    LinkedHashMap<String, PlanCreationResponse> planCreationResponseMap = new LinkedHashMap<>();

    ServiceConfig serviceConfig =
        ServiceConfig.builder().useFromStage(ServiceUseFromStage.builder().stage("stage1").build()).build();
    ClassLoader classLoader = this.getClass().getClassLoader();
    InputStream yamlFile =
        classLoader.getResourceAsStream("cdng/plan/configfiles/configfiles_test_without_stage_override.yml");
    assertThat(yamlFile).isNotNull();

    String yaml = new Scanner(yamlFile, "UTF-8").useDelimiter("\\A").next();
    yaml = YamlUtils.injectUuid(yaml);
    YamlField serviceField = YamlUtils.readTree(yaml);

    String nodeUuid = serviceDefinitionPlanCreator.addDependenciesForConfigFiles(
        serviceField.getNode(), planCreationResponseMap, serviceConfig);

    assertThat(planCreationResponseMap.size()).isEqualTo(1);
    assertThat(planCreationResponseMap.containsKey(nodeUuid)).isEqualTo(true);
    PlanCreationResponse planCreationResponse1 = planCreationResponseMap.get(nodeUuid);
    checksForDependencies(planCreationResponse1, nodeUuid);
    assertThat(planCreationResponse1.getDependencies().getDependenciesMap().get(nodeUuid))
        .isEqualTo("stageOverrides/configFiles");

    assertThat(planCreationResponse1.getYamlUpdates()).isNotNull();
  }
}
