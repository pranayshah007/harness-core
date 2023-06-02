/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cdng.creator.plan.manifest;

import static io.harness.rule.OwnerRule.PRASHANTSHARMA;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.cdng.CDNGTestBase;
import io.harness.cdng.creator.plan.PlanCreatorConstants;
import io.harness.cdng.manifest.ManifestType;
import io.harness.cdng.manifest.steps.ManifestStepParameters;
import io.harness.cdng.manifest.yaml.ManifestConfig;
import io.harness.cdng.visitor.YamlTypes;
import io.harness.data.structure.UUIDGenerator;
import io.harness.pms.contracts.plan.Dependency;
import io.harness.pms.sdk.core.plan.PlanNode;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationContext;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationResponse;
import io.harness.rule.Owner;
import io.harness.serializer.KryoSerializer;

import com.google.inject.Inject;
import com.google.protobuf.ByteString;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;

@OwnedBy(HarnessTeam.CDC)
public class IndividualManifestPlanCreatorTest extends CDNGTestBase {
  @Inject KryoSerializer kryoSerializer;
  @Inject @InjectMocks IndividualManifestPlanCreator individualManifestPlanCreator;

  @Test
  @Owner(developers = PRASHANTSHARMA)
  @Category(UnitTests.class)
  public void testGetFieldClass() {
    assertThat(individualManifestPlanCreator.getFieldClass()).isEqualTo(ManifestConfig.class);
  }

  @Test
  @Owner(developers = PRASHANTSHARMA)
  @Category(UnitTests.class)
  public void testGetSupportedTypes() {
    Map<String, Set<String>> supportedTypes = individualManifestPlanCreator.getSupportedTypes();
    assertThat(supportedTypes.containsKey(YamlTypes.MANIFEST_CONFIG)).isEqualTo(true);
    assertThat(supportedTypes.get(YamlTypes.MANIFEST_CONFIG).size()).isEqualTo(27);
    Set<String> manifestsSupportedTypes = supportedTypes.get(YamlTypes.MANIFEST_CONFIG);

    Set<String> expectedSupportedTypes = new HashSet<>(Arrays.asList(ManifestType.K8Manifest, ManifestType.VALUES,
        ManifestType.OpenshiftTemplate, ManifestType.KustomizePatches, ManifestType.Kustomize, ManifestType.HelmChart,
        ManifestType.CONFIG_FILE, ManifestType.OpenshiftParam, ManifestType.ServerlessAwsLambda,
        ManifestType.EcsTaskDefinition, ManifestType.EcsServiceDefinition, ManifestType.EcsScalableTargetDefinition,
        ManifestType.EcsScalingPolicyDefinition, ManifestType.TAS_MANIFEST, ManifestType.TAS_VARS,
        ManifestType.TAS_AUTOSCALER, ManifestType.DeploymentRepo, ManifestType.AsgLaunchTemplate,
        ManifestType.AsgConfiguration, ManifestType.AsgScalingPolicy, ManifestType.AsgScheduledUpdateGroupAction,
        ManifestType.GoogleCloudFunctionDefinition, ManifestType.AwsLambdaFunctionDefinition,
        ManifestType.AwsLambdaFunctionAliasDefinition, ManifestType.AwsSamDirectory,
        ManifestType.GoogleCloudFunctionGenOneDefinition));

    assertThat(manifestsSupportedTypes.containsAll(expectedSupportedTypes)).isEqualTo(true);
  }

  @Test
  @Owner(developers = PRASHANTSHARMA)
  @Category(UnitTests.class)
  public void testGetParentNode() {
    HashMap<String, ByteString> metadataDependency = new HashMap<>();
    String uuid = UUIDGenerator.generateUuid();
    String identifier = "manifest1";
    ManifestStepParameters manifestStepParameters = ManifestStepParameters.builder().identifier(identifier).build();

    metadataDependency.put(YamlTypes.UUID, ByteString.copyFrom(kryoSerializer.asDeflatedBytes(uuid)));
    metadataDependency.put(PlanCreatorConstants.MANIFEST_STEP_PARAMETER,
        ByteString.copyFrom(kryoSerializer.asDeflatedBytes(manifestStepParameters)));
    Dependency dependency = Dependency.newBuilder().putAllMetadata(metadataDependency).build();
    PlanCreationContext ctx = PlanCreationContext.builder().dependency(dependency).build();
    PlanCreationResponse sidecarPlanCreationResponse = individualManifestPlanCreator.createPlanForField(ctx, null);
    PlanNode sidecarPlanNode = sidecarPlanCreationResponse.getPlanNode();
    assertThat(sidecarPlanNode.getUuid()).isEqualTo(uuid);
    assertThat(sidecarPlanNode.getIdentifier()).isEqualTo(identifier);
    assertThat(sidecarPlanNode.getStepParameters()).isEqualTo(manifestStepParameters);
  }
}