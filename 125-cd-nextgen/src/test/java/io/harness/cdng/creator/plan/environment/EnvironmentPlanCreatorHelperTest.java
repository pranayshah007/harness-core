/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cdng.creator.plan.environment;

import static io.harness.rule.OwnerRule.PRASHANTSHARMA;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doReturn;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.cdng.CDNGTestBase;
import io.harness.cdng.environment.helper.EnvironmentPlanCreatorHelper;
import io.harness.cdng.environment.yaml.EnvironmentPlanCreatorConfig;
import io.harness.cdng.environment.yaml.EnvironmentYamlV2;
import io.harness.cdng.visitor.YamlTypes;
import io.harness.data.structure.UUIDGenerator;
import io.harness.exception.InvalidRequestException;
import io.harness.ng.core.environment.beans.Environment;
import io.harness.ng.core.environment.beans.EnvironmentType;
import io.harness.ng.core.environment.services.EnvironmentService;
import io.harness.ng.core.infrastructure.services.InfrastructureEntityService;
import io.harness.ng.core.serviceoverride.services.ServiceOverrideService;
import io.harness.pms.contracts.plan.PlanCreationContextValue;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationContext;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationResponse;
import io.harness.pms.yaml.YAMLFieldNameConstants;
import io.harness.pms.yaml.YamlField;
import io.harness.pms.yaml.YamlNode;
import io.harness.pms.yaml.YamlUtils;
import io.harness.rule.Owner;
import io.harness.serializer.KryoSerializer;
import io.harness.utils.YamlPipelineUtils;

import com.google.common.io.Resources;
import com.google.inject.Inject;
import com.google.protobuf.ByteString;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;

@OwnedBy(HarnessTeam.CDC)
public class EnvironmentPlanCreatorHelperTest extends CDNGTestBase {
  @Inject KryoSerializer kryoSerializer;
  @Mock private EnvironmentService environmentService;
  @Mock private InfrastructureEntityService infrastructure;
  @Mock private ServiceOverrideService serviceOverrideService;
  private String createYamlFromPath(String filename) {
    ClassLoader classLoader = getClass().getClassLoader();
    try {
      return Resources.toString(Objects.requireNonNull(classLoader.getResource(filename)), StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new InvalidRequestException("Could not read resource file: " + filename);
    }
  }

  private YamlField getYamlFieldFromPath(String path) throws IOException {
    String yaml = createYamlFromPath(path);
    yaml = YamlUtils.injectUuid(yaml);
    return YamlUtils.readTree(yaml);
  }

  @Test
  @Owner(developers = PRASHANTSHARMA)
  @Category(UnitTests.class)
  public void testMergeEnvironmentInputs() throws IOException {
    String originalYamlWithRunTimeInputs =
        createYamlFromPath("cdng/plan/environment/originalEnvironmentWithRuntimeValue.yml");
    String inputValueYaml = createYamlFromPath("cdng/plan/environment/runtimeInputValueEnvironment.yml");

    Map<String, Object> read = YamlPipelineUtils.read(inputValueYaml, Map.class);
    assertThatCode(() -> EnvironmentPlanCreatorHelper.mergeEnvironmentInputs(originalYamlWithRunTimeInputs, read))
        .doesNotThrowAnyException();
  }

  @Test
  @Owner(developers = PRASHANTSHARMA)
  @Category(UnitTests.class)
  public void testPrepareMetadata() {
    String environmentUuid = UUIDGenerator.generateUuid();
    String infraSectionUuid = UUIDGenerator.generateUuid();
    String serviceSpecNodeId = UUIDGenerator.generateUuid();
    Map<String, ByteString> resultedMetadata = EnvironmentPlanCreatorHelper.prepareMetadata(
        environmentUuid, infraSectionUuid, serviceSpecNodeId, false, false, kryoSerializer);
    assertThat(resultedMetadata.size()).isEqualTo(5);

    assertThat(kryoSerializer.asInflatedObject(resultedMetadata.get(YamlTypes.NEXT_UUID).toByteArray()))
        .isEqualTo(serviceSpecNodeId);
    assertThat(kryoSerializer.asInflatedObject(resultedMetadata.get(YamlTypes.UUID).toByteArray()))
        .isEqualTo(environmentUuid);
    assertThat(kryoSerializer.asInflatedObject(resultedMetadata.get(YamlTypes.INFRA_SECTION_UUID).toByteArray()))
        .isEqualTo(infraSectionUuid);
    assertThat(
        kryoSerializer.asInflatedObject(resultedMetadata.get(YAMLFieldNameConstants.GITOPS_ENABLED).toByteArray()))
        .isEqualTo(false);
    assertThat(
        kryoSerializer.asInflatedObject(resultedMetadata.get(YAMLFieldNameConstants.SKIP_INSTANCES).toByteArray()))
        .isEqualTo(false);

    // GitOps = true
    resultedMetadata = EnvironmentPlanCreatorHelper.prepareMetadata(
        environmentUuid, infraSectionUuid, serviceSpecNodeId, true, true, kryoSerializer);
    assertThat(resultedMetadata.size()).isEqualTo(5);

    assertThat(
        kryoSerializer.asInflatedObject(resultedMetadata.get(YAMLFieldNameConstants.GITOPS_ENABLED).toByteArray()))
        .isEqualTo(true);
    assertThat(
        kryoSerializer.asInflatedObject(resultedMetadata.get(YAMLFieldNameConstants.SKIP_INSTANCES).toByteArray()))
        .isEqualTo(true);
  }

  @Test
  @Owner(developers = PRASHANTSHARMA)
  @Category(UnitTests.class)
  public void testFetchEnvironmentPlanCreatorConfigYaml() throws IOException {
    YamlField environmentYamlV2 = getYamlFieldFromPath("cdng/plan/environment/environmentYamlV2WithInfra.yml");

    String envPlanCreatorConfigYaml =
        createYamlFromPath("cdng/plan/environment/environmentPlanCreatorConfigWithInfra.yml");
    EnvironmentPlanCreatorConfig environmentPlanCreatorConfig =
        YamlUtils.read(envPlanCreatorConfigYaml, EnvironmentPlanCreatorConfig.class);
    YamlField updatedEnvironmentYamlField = EnvironmentPlanCreatorHelper.fetchEnvironmentPlanCreatorConfigYaml(
        environmentPlanCreatorConfig, environmentYamlV2);
    assertThat(updatedEnvironmentYamlField).isNotNull();
    assertThat(updatedEnvironmentYamlField.getNode().getFieldName()).isEqualTo(YamlTypes.ENVIRONMENT_YAML);
    assertThat(updatedEnvironmentYamlField.getNode().getField("environmentRef").getNode().asText())
        .isEqualTo(environmentPlanCreatorConfig.getEnvironmentRef().getValue());
    List<YamlNode> infrastructureDefinitions =
        updatedEnvironmentYamlField.getNode().getField("infrastructureDefinitions").getNode().asArray();
    assertThat(infrastructureDefinitions.size()).isEqualTo(2);
    assertThat(infrastructureDefinitions.get(0).getField("ref").getNode().asText())
        .isEqualTo(environmentPlanCreatorConfig.getInfrastructureDefinitions().get(0).getRef());
    assertThat(infrastructureDefinitions.get(1).getField("ref").getNode().asText())
        .isEqualTo(environmentPlanCreatorConfig.getInfrastructureDefinitions().get(1).getRef());
  }

  @Test
  @Owner(developers = PRASHANTSHARMA)
  @Category(UnitTests.class)
  public void testAddEnvironmentV2Dependency() throws IOException {
    YamlField environmentYamlV2 = getYamlFieldFromPath("cdng/plan/environment/environmentYamlV2WithInfra.yml");

    String envPlanCreatorConfigYaml =
        createYamlFromPath("cdng/plan/environment/environmentPlanCreatorConfigWithInfra.yml");
    EnvironmentPlanCreatorConfig environmentPlanCreatorConfig =
        YamlUtils.read(envPlanCreatorConfigYaml, EnvironmentPlanCreatorConfig.class);
    LinkedHashMap<String, PlanCreationResponse> planCreationResponseMap = new LinkedHashMap<>();
    EnvironmentPlanCreatorHelper.addEnvironmentV2Dependency(planCreationResponseMap, environmentPlanCreatorConfig,
        environmentYamlV2, false, false, "environmentUuid", "infraSectionUuid", "serviceSpecNodeUuid", kryoSerializer);
    assertThat(planCreationResponseMap.size()).isEqualTo(1);
    String key = planCreationResponseMap.keySet().iterator().next();
    assertThat(planCreationResponseMap.get(key).getYamlUpdates().getFqnToYamlCount()).isEqualTo(1);
    assertThat(planCreationResponseMap.get(key).getDependencies().getDependenciesMap().size()).isEqualTo(1);

    Map<String, ByteString> dependencyMetadata =
        planCreationResponseMap.get(key).getDependencies().getDependencyMetadataMap().get(key).getMetadataMap();

    assertThat(dependencyMetadata.size()).isEqualTo(5);
    assertThat(dependencyMetadata.containsKey(YamlTypes.UUID)).isTrue();
    assertThat(dependencyMetadata.get(YamlTypes.UUID))
        .isEqualTo(ByteString.copyFrom(kryoSerializer.asDeflatedBytes(key)));

    assertThat(dependencyMetadata.containsKey(YamlTypes.NEXT_UUID)).isTrue();
    assertThat(dependencyMetadata.get(YamlTypes.NEXT_UUID))
        .isEqualTo(ByteString.copyFrom(kryoSerializer.asDeflatedBytes("serviceSpecNodeUuid")));

    assertThat(dependencyMetadata.containsKey(YamlTypes.INFRA_SECTION_UUID)).isTrue();
    assertThat(dependencyMetadata.get(YamlTypes.INFRA_SECTION_UUID))
        .isEqualTo(ByteString.copyFrom(kryoSerializer.asDeflatedBytes("infraSectionUuid")));
  }

  @Test
  @Owner(developers = PRASHANTSHARMA)
  @Category(UnitTests.class)
  public void testGetResolvedEnvRefs() throws IOException {
    String accountId = "accId";
    String projectId = "projectId";
    String orgId = "orgId";

    String envYamlV2 = createYamlFromPath("cdng/plan/environment/environmentYamlV2WithInfra.yml");
    EnvironmentYamlV2 envYamlObj = YamlUtils.read(envYamlV2, EnvironmentYamlV2.class);
    String envId = envYamlObj.getEnvironmentRef().getValue();
    PlanCreationContextValue planCreationContext = PlanCreationContextValue.newBuilder()
                                                       .setAccountIdentifier(accountId)
                                                       .setOrgIdentifier(orgId)
                                                       .setProjectIdentifier(projectId)
                                                       .build();
    // no env with given envRef
    assertThatThrownBy(
        ()
            -> EnvironmentPlanCreatorHelper.getResolvedEnvRefs(
                PlanCreationContext.builder().globalContext(Map.of("metadata", planCreationContext)).build(),
                envYamlObj, false, "serviceRef", null, environmentService, infrastructure))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("No environment found with " + envId + " identifier in " + projectId + " project in " + orgId
            + " org and " + accountId + " account");

    // with environment. Number of infrastructure equals 0
    Environment environment = Environment.builder()
                                  .identifier(envId)
                                  .accountId(accountId)
                                  .projectIdentifier(projectId)
                                  .orgIdentifier(orgId)
                                  .type(EnvironmentType.Production)
                                  .build();
    doReturn(Optional.of(environment)).when(environmentService).get(accountId, orgId, projectId, envId, false);

    assertThatThrownBy(
        ()
            -> EnvironmentPlanCreatorHelper.getResolvedEnvRefs(
                PlanCreationContext.builder().globalContext(Map.of("metadata", planCreationContext)).build(),
                envYamlObj, false, "serviceRef", serviceOverrideService, environmentService, infrastructure))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("Infrastructure linked with environment " + envId + " does not exists");
  }
}