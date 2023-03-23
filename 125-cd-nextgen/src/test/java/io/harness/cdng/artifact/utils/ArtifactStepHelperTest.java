/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.artifact.utils;

import static io.harness.rule.OwnerRule.ROHITKARELIA;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.cdng.CDNGTestBase;
import io.harness.cdng.common.beans.SetupAbstractionKeys;
import io.harness.cdng.visitor.YamlTypes;
import io.harness.exception.InvalidRequestException;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.yaml.YamlField;
import io.harness.rule.Owner;

import java.io.IOException;
import org.jetbrains.annotations.NotNull;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;

@OwnedBy(HarnessTeam.CDC)
public class ArtifactStepHelperTest extends CDNGTestBase {
  @InjectMocks private ArtifactStepHelper artifactStepHelper;

  private final Ambiance ambiance = Ambiance.newBuilder()
                                        .putSetupAbstractions(SetupAbstractionKeys.accountId, "test-account")
                                        .putSetupAbstractions(SetupAbstractionKeys.orgIdentifier, "test-org")
                                        .putSetupAbstractions(SetupAbstractionKeys.projectIdentifier, "test-project")
                                        .build();

  String serviceEntityYaml = "service:\n"
      + "  name: nginx\n"
      + "  identifier: id\n"
      + "  tags: {}\n"
      + "  serviceDefinition:\n"
      + "    spec:\n"
      + "      artifacts:\n"
      + "        primary:\n"
      + "          primaryArtifactRef: <+input>\n"
      + "          sources:\n"
      + "            - spec:\n"
      + "                connectorRef: docker_hub\n"
      + "                imagePath: library/nginx\n"
      + "                tag: <+input>\n"
      + "              identifier: nginx\n"
      + "              type: DockerRegistry\n"
      + "    type: Kubernetes";

  @Test
  @Owner(developers = ROHITKARELIA)
  @Category(UnitTests.class)
  public void testProcessArtifactsInYamlWithOneArtifactAndPrimaryArtifactRefSet() throws IOException {
    YamlField yamlField = artifactStepHelper.processArtifactsInYaml(ambiance, serviceEntityYaml);
    YamlField serviceDefField =
        yamlField.getNode().getField(YamlTypes.SERVICE_ENTITY).getNode().getField(YamlTypes.SERVICE_DEFINITION);
    YamlField serviceSpecField = serviceDefField.getNode().getField(YamlTypes.SERVICE_SPEC);
    YamlField artifactsField = serviceSpecField.getNode().getField(YamlTypes.ARTIFACT_LIST_CONFIG);
    YamlField primaryArtifactField = artifactsField.getNode().getField(YamlTypes.PRIMARY_ARTIFACT);
    assertThat(yamlField).isNotNull();
    assertThat(primaryArtifactField).isNotNull();
  }

  @Test
  @Owner(developers = ROHITKARELIA)
  @Category(UnitTests.class)
  public void testProcessArtifactsInYamlWithOneArtifactAndPrimaryArtifactRefNull() throws IOException {
    YamlField yamlField =
        artifactStepHelper.processArtifactsInYaml(ambiance, getServiceEntityYamlWithNullPrimaryArtifact());
    YamlField serviceDefField =
        yamlField.getNode().getField(YamlTypes.SERVICE_ENTITY).getNode().getField(YamlTypes.SERVICE_DEFINITION);
    YamlField serviceSpecField = serviceDefField.getNode().getField(YamlTypes.SERVICE_SPEC);
    YamlField artifactsField = serviceSpecField.getNode().getField(YamlTypes.ARTIFACT_LIST_CONFIG);
    YamlField primaryArtifactField = artifactsField.getNode().getField(YamlTypes.PRIMARY_ARTIFACT);
    assertThat(yamlField).isNotNull();
    assertThat(primaryArtifactField).isNotNull();
  }

  @Test
  @Owner(developers = ROHITKARELIA)
  @Category(UnitTests.class)
  public void testProcessArtifactsInYamlWithMultipleArtifactAndPrimaryArtifactRefSet() throws IOException {
    YamlField yamlField = artifactStepHelper.processArtifactsInYaml(
        ambiance, getServiceEntityYamlWithMultipleSourcesAndPrimaryArtifactSet());
    YamlField serviceDefField =
        yamlField.getNode().getField(YamlTypes.SERVICE_ENTITY).getNode().getField(YamlTypes.SERVICE_DEFINITION);
    YamlField serviceSpecField = serviceDefField.getNode().getField(YamlTypes.SERVICE_SPEC);
    YamlField artifactsField = serviceSpecField.getNode().getField(YamlTypes.ARTIFACT_LIST_CONFIG);
    assertThat(yamlField).isNotNull();
    assertThat(artifactsField.getNode().getField(YamlTypes.PRIMARY_ARTIFACT)).isNotNull();
    assertThat(artifactsField.getNode()
                   .getField(YamlTypes.PRIMARY_ARTIFACT)
                   .getNode()
                   .getCurrJsonNode()
                   .get("spec")
                   .get("imagePath")
                   .asText())
        .isEqualTo("library/nginx");
  }

  @Test
  @Owner(developers = ROHITKARELIA)
  @Category(UnitTests.class)
  public void testProcessArtifactsInYamlWithMultipleArtifactAndPrimaryArtifactRefIsNull() {
    assertThatThrownBy(()
                           -> artifactStepHelper.processArtifactsInYaml(
                               ambiance, getServiceEntityYamlWithMultipleArtifactSourcesAndPrimaryArtifactRefIsNull()))
        .isInstanceOf(InvalidRequestException.class);
  }

  @NotNull
  private static String getServiceEntityYamlWithMultipleArtifactSourcesAndPrimaryArtifactRefIsNull() {
    return "service:\n"
        + "  name: nginx\n"
        + "  identifier: id\n"
        + "  tags: {}\n"
        + "  serviceDefinition:\n"
        + "    spec:\n"
        + "      artifacts:\n"
        + "        primary:\n"
        + "          sources:\n"
        + "            - spec:\n"
        + "                connectorRef: docker_hub\n"
        + "                imagePath: library/nginx\n"
        + "                tag: <+input>\n"
        + "              identifier: nginx\n"
        + "              type: DockerRegistry\n"
        + "            - spec:\n"
        + "                connectorRef: docker_hub\n"
        + "                imagePath: library/busybox\n"
        + "                tag: <+input>\n"
        + "              identifier: busybox\n"
        + "              type: DockerRegistry\n"
        + "    type: Kubernetes";
  }

  @NotNull
  private static String getServiceEntityYamlWithNullPrimaryArtifact() {
    String serviceEntityYamlWithNullPrimaryArtifact = "service:\n"
        + "  name: nginx\n"
        + "  identifier: id\n"
        + "  tags: {}\n"
        + "  serviceDefinition:\n"
        + "    spec:\n"
        + "      artifacts:\n"
        + "        primary:\n"
        + "          sources:\n"
        + "            - spec:\n"
        + "                connectorRef: docker_hub\n"
        + "                imagePath: library/nginx\n"
        + "                tag: <+input>\n"
        + "              identifier: nginx\n"
        + "              type: DockerRegistry\n"
        + "    type: Kubernetes";
    return serviceEntityYamlWithNullPrimaryArtifact;
  }

  private static String getServiceEntityYamlWithMultipleSourcesAndPrimaryArtifactSet() {
    return "service:\n"
        + "  name: nginx\n"
        + "  identifier: id\n"
        + "  tags: {}\n"
        + "  serviceDefinition:\n"
        + "    spec:\n"
        + "      artifacts:\n"
        + "        primary:\n"
        + "          primaryArtifactRef: nginx\n"
        + "          sources:\n"
        + "            - spec:\n"
        + "                connectorRef: docker_hub\n"
        + "                imagePath: library/nginx\n"
        + "                tag: <+input>\n"
        + "              identifier: nginx\n"
        + "              type: DockerRegistry\n"
        + "            - spec:\n"
        + "                connectorRef: docker_hub\n"
        + "                imagePath: library/busybox\n"
        + "                tag: <+input>\n"
        + "              identifier: busybox\n"
        + "              type: DockerRegistry\n"
        + "    type: Kubernetes";
  }
}
