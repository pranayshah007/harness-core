/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.merger.helpers;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.rule.OwnerRule.NAMAN;
import static io.harness.rule.OwnerRule.VED;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(PIPELINE)
public class InputSetYamlHelperTest extends CategoryTest {
  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testGetPipelineComponent() {
    String yaml1 = getInputSetYaml(true);
    String pipelineComponent = InputSetYamlHelper.getPipelineComponent(yaml1);
    assertThat(pipelineComponent).isEqualTo(getPipelineYaml());

    String yaml2 = getPipelineYaml();
    assertThatThrownBy(() -> InputSetYamlHelper.getPipelineComponent(yaml2))
        .hasMessage("Yaml provided is not an input set yaml.");

    String yaml3 = getOverlayInputSetYaml(true, true);
    assertThatThrownBy(() -> InputSetYamlHelper.getPipelineComponent(yaml3))
        .hasMessage("Yaml provided is not an input set yaml.");
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testSetPipelineComponent() {
    String yaml1 = getInputSetYaml(true);
    String newPipelineComponent = "pipeline:\n"
        + "  name: n2\n"
        + "  identifier: n2\n"
        + "  description: something here";
    String newYaml = InputSetYamlHelper.setPipelineComponent(yaml1, newPipelineComponent);
    String newExpectedYaml = "inputSet:\n"
        + "  name: n1\n"
        + "  identifier: n1\n"
        + "  pipeline:\n"
        + "    name: n2\n"
        + "    identifier: n2\n"
        + "    description: something here\n";
    assertThat(newYaml).isEqualTo(newExpectedYaml);
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testGetStringField() {
    String yaml1 = getPipelineYaml();
    assertThat(InputSetYamlHelper.getStringField(yaml1, "name", "pipeline")).isEqualTo("n2");
    assertThatThrownBy(() -> InputSetYamlHelper.getStringField(yaml1, "name", "inputSet"))
        .hasMessage("Root node is not inputSet");
    assertThat(InputSetYamlHelper.getStringField(yaml1, "description", "pipeline")).isNull();
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testIsPipelineAbsent() {
    String yaml1 = getInputSetYaml(true);
    assertThat(InputSetYamlHelper.isPipelineAbsent(yaml1)).isFalse();

    String yaml2 = getInputSetYaml(false);
    assertThat(InputSetYamlHelper.isPipelineAbsent(yaml2)).isTrue();

    String yaml3 = getPipelineYaml();
    assertThatThrownBy(() -> InputSetYamlHelper.isPipelineAbsent(yaml3))
        .hasMessage("Yaml provided is not an input set yaml.");
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testGetTags() {
    String yaml1 = getInputSetYaml(true);
    assertThat(InputSetYamlHelper.getTags(yaml1, "inputSet")).isNullOrEmpty();
    assertThatThrownBy(() -> InputSetYamlHelper.getTags(yaml1, "overlayInputSet"))
        .hasMessage("Root node is not overlayInputSet");

    String yaml2 = getInputSetYaml(true, true);
    Map<String, String> tags1 = InputSetYamlHelper.getTags(yaml2, "inputSet");
    assertThat(tags1).isNotNull();
    assertThat(tags1.size()).isEqualTo(1);
    assertThat(tags1.containsKey("a")).isTrue();
    assertThat(tags1.get("a")).isEqualTo("b");

    String yaml3 = getOverlayInputSetYaml(false, true);
    assertThat(InputSetYamlHelper.getTags(yaml3, "overlayInputSet")).isNullOrEmpty();
    assertThatThrownBy(() -> InputSetYamlHelper.getTags(yaml3, "inputSet")).hasMessage("Root node is not inputSet");

    String yaml4 = getOverlayInputSetYaml(true, true);
    Map<String, String> tags2 = InputSetYamlHelper.getTags(yaml4, "overlayInputSet");
    assertThat(tags2).isNotNull();
    assertThat(tags2.size()).isEqualTo(1);
    assertThat(tags2.containsKey("a")).isTrue();
    assertThat(tags2.get("a")).isEqualTo("b");
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testGetRootNodeOfInputSetYaml() {
    String yaml1 = getInputSetYaml(true);
    assertThat(InputSetYamlHelper.getRootNodeOfInputSetYaml(yaml1)).isEqualTo("inputSet");

    String yaml2 = getOverlayInputSetYaml(true, true);
    assertThat(InputSetYamlHelper.getRootNodeOfInputSetYaml(yaml2)).isEqualTo("overlayInputSet");

    String yaml3 = getPipelineYaml();
    assertThatThrownBy(() -> InputSetYamlHelper.getRootNodeOfInputSetYaml(yaml3))
        .hasMessage("Yaml provided is neither an input set nor an overlay input set");
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testGetReferencesFromOverlayInputSetYaml() {
    String yaml1 = getOverlayInputSetYaml(false, true);
    List<String> references1 = InputSetYamlHelper.getReferencesFromOverlayInputSetYaml(yaml1);
    assertThat(references1.size()).isEqualTo(2);
    assertThat(references1.contains("s1")).isTrue();
    assertThat(references1.contains("s2")).isTrue();

    String yaml2 = getOverlayInputSetYaml(false, false);
    List<String> references2 = InputSetYamlHelper.getReferencesFromOverlayInputSetYaml(yaml2);
    assertThat(references2.size()).isEqualTo(0);

    String yaml3 = getInputSetYaml(true);
    assertThatThrownBy(() -> InputSetYamlHelper.getReferencesFromOverlayInputSetYaml(yaml3))
        .hasMessage("Yaml provided is not an overlay input set yaml.");
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testSetReferencesFromOverlayInputSetYaml() {
    String yaml = getOverlayInputSetYaml(false, true);
    List<String> newReferences = Arrays.asList("ref1", "ref2", "ref1NotAgain");
    String newYaml = InputSetYamlHelper.setReferencesFromOverlayInputSetYaml(yaml, newReferences);
    assertThat(newYaml).isEqualTo("overlayInputSet:\n"
        + "  name: n1\n"
        + "  identifier: n1\n"
        + "  inputSetReferences:\n"
        + "    - ref1\n"
        + "    - ref2\n"
        + "    - ref1NotAgain\n");
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testConfirmPipelineIdentifierInInputSet() {
    String yaml1 = getInputSetYaml(true);
    InputSetYamlHelper.confirmPipelineIdentifierInInputSet(yaml1, "n2");
    assertThatThrownBy(() -> InputSetYamlHelper.confirmPipelineIdentifierInInputSet(yaml1, "n1"))
        .hasMessage("Pipeline identifier in input set does not match");

    String yaml2 = getInputSetYaml(false);
    assertThatThrownBy(() -> InputSetYamlHelper.confirmPipelineIdentifierInInputSet(yaml2, "n2"))
        .hasMessage("Input Set provides no values for any runtime input, or the pipeline has no runtime input");
  }

  @Test
  @Owner(developers = VED)
  @Category(UnitTests.class)
  public void testPipelineIdentifierAbsentFromInputSet() {
    String yaml1 = getInputSetYamlWithoutPipelineIdentifier(true);
    assertThatThrownBy(() -> InputSetYamlHelper.confirmPipelineIdentifierInInputSet(yaml1, "n2"))
        .hasMessage("Pipeline identifier is missing in the YAML. Please give a valid Pipeline identifier");
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testConfirmPipelineIdentifierInOverlayInputSet() {
    String yaml1 = getOverlayInputSetYaml(false, true);
    assertThatThrownBy(() -> InputSetYamlHelper.confirmPipelineIdentifierInOverlayInputSet(yaml1, "n2"))
        .hasMessage("Pipeline identifier is missing in the YAML. Please give a valid Pipeline identifier");

    String yaml2 = addPipelineIdentifier(yaml1);
    assertThatThrownBy(() -> InputSetYamlHelper.confirmPipelineIdentifierInOverlayInputSet(yaml2, "n1"))
        .hasMessage("Pipeline identifier in input set does not match");
    InputSetYamlHelper.confirmPipelineIdentifierInOverlayInputSet(yaml2, "n2");
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testConfirmOrgAndProjectIdentifier() {
    String yaml1 = getInputSetYaml(true);
    assertThatThrownBy(() -> InputSetYamlHelper.confirmOrgAndProjectIdentifier(yaml1, "inputSet", "o1", "p1"))
        .hasMessage("Organization identifier is missing in the YAML. Please give a valid Organization identifier");

    String yaml2 = addOrgIdentifier(yaml1);
    assertThatThrownBy(() -> InputSetYamlHelper.confirmOrgAndProjectIdentifier(yaml2, "inputSet", "o1", "p1"))
        .hasMessage("Project identifier is missing in the YAML. Please give a valid Project identifier");

    String yaml3 = addProjectIdentifier(yaml2);
    assertThatThrownBy(() -> InputSetYamlHelper.confirmOrgAndProjectIdentifier(yaml3, "inputSet", "o2", "p1"))
        .hasMessage("Org identifier in input set does not match");

    assertThatThrownBy(() -> InputSetYamlHelper.confirmOrgAndProjectIdentifier(yaml3, "inputSet", "o1", "p2"))
        .hasMessage("Project identifier in input set does not match");
    InputSetYamlHelper.confirmOrgAndProjectIdentifier(yaml3, "inputSet", "o1", "p1");
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testConfirmOrgAndProjectIdentifierForOverlay() {
    String yaml1 = getOverlayInputSetYaml(true, true);
    assertThatThrownBy(() -> InputSetYamlHelper.confirmOrgAndProjectIdentifier(yaml1, "overlayInputSet", "o1", "p1"))
        .hasMessage("Organization identifier is missing in the YAML. Please give a valid Organization identifier");

    String yaml2 = addOrgIdentifier(yaml1);
    assertThatThrownBy(() -> InputSetYamlHelper.confirmOrgAndProjectIdentifier(yaml2, "overlayInputSet", "o1", "p1"))
        .hasMessage("Project identifier is missing in the YAML. Please give a valid Project identifier");

    String yaml3 = addProjectIdentifier(yaml2);
    assertThatThrownBy(() -> InputSetYamlHelper.confirmOrgAndProjectIdentifier(yaml3, "overlayInputSet", "o2", "p1"))
        .hasMessage("Org identifier in input set does not match");

    assertThatThrownBy(() -> InputSetYamlHelper.confirmOrgAndProjectIdentifier(yaml3, "overlayInputSet", "o1", "p2"))
        .hasMessage("Project identifier in input set does not match");
    InputSetYamlHelper.confirmOrgAndProjectIdentifier(yaml3, "overlayInputSet", "o1", "p1");
  }

  @Test
  @Owner(developers = VED)
  @Category(UnitTests.class)
  public void testOverlayInputSetWithEmptyReferences() {
    String yaml1 = getOverlayInputSetYamlWithNullReferences(false);
    assertThatThrownBy(() -> InputSetYamlHelper.getReferencesFromOverlayInputSetYaml(yaml1))
        .hasMessage("Input Set References cannot be empty. Please give valid Input Set References.");
  }

  private String getInputSetYaml(boolean hasPipelineComponent) {
    return getInputSetYaml(hasPipelineComponent, false);
  }

  private String getInputSetYaml(boolean hasPipelineComponent, boolean hasTags) {
    String base = "inputSet:\n"
        + "  name: n1\n"
        + "  identifier: n1\n";
    String tags = "  tags:\n"
        + "    a : b\n";
    String pipelineComponent = "  pipeline:\n"
        + "    name: n2\n"
        + "    identifier: n2\n";
    return base + (hasTags ? tags : "") + (hasPipelineComponent ? pipelineComponent : "");
  }

  private String getPipelineYaml() {
    return "pipeline:\n"
        + "  name: n2\n"
        + "  identifier: n2\n";
  }

  private String getOverlayInputSetYaml(boolean hasTags, boolean hasReferences) {
    String base = "overlayInputSet:\n"
        + "  name: n1\n"
        + "  identifier: n1\n";
    String noReferences = "  inputSetReferences: []\n";
    String references = "  inputSetReferences:\n"
        + "    - s1\n"
        + "    - s2\n";
    String tags = "  tags:\n"
        + "    a : b\n";

    return base + (hasTags ? tags : "") + (hasReferences ? references : noReferences);
  }

  private String getOverlayInputSetYamlWithNullReferences(boolean hasTags) {
    String base = "overlayInputSet:\n"
        + "  name: n1\n"
        + "  identifier: n1\n";
    String tags = "  tags:\n"
        + "    a : b\n";

    return base + (hasTags ? tags : "");
  }

  private String addOrgIdentifier(String yaml) {
    String orgId = "  orgIdentifier: o1\n";
    return yaml + orgId;
  }

  private String addProjectIdentifier(String yaml) {
    String projectId = "  projectIdentifier: p1\n";
    return yaml + projectId;
  }

  private String addPipelineIdentifier(String yaml) {
    String pipelineId = "  pipelineIdentifier: n2\n";
    return yaml + pipelineId;
  }

  private String getInputSetYamlWithoutPipelineIdentifier(boolean hasPipelineComponent) {
    return getInputSetYamlWithoutIdentifier(hasPipelineComponent, false);
  }

  private String getInputSetYamlWithoutIdentifier(boolean hasPipelineComponent, boolean hasTags) {
    String base = "inputSet:\n"
        + "  name: n1\n"
        + "  identifier: n1\n";
    String tags = "  tags:\n"
        + "    a : b\n";
    String pipelineComponent = "  pipeline:\n"
        + "    name: n2\n";
    return base + (hasTags ? tags : "") + (hasPipelineComponent ? pipelineComponent : "");
  }
}
