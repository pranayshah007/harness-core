/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.k8s;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.cdng.k8s.yaml.YamlUtility.REDACTED_BY_HARNESS;
import static io.harness.rule.OwnerRule.ABOSII;
import static io.harness.rule.OwnerRule.ACHYUTH;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.cdng.manifest.ManifestType;
import io.harness.rule.Owner;

import java.util.List;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(CDP)
public class K8SFilesCommentsHandlerTest extends CategoryTest {
  @Test
  @Owner(developers = ACHYUTH)
  @Category(UnitTests.class)
  public void shouldNotRemoveCommentsK8s() {
    shouldNotRemoveComments(ManifestType.K8Manifest);
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void shouldNotRemoveCommentsHelm() {
    shouldNotRemoveComments(ManifestType.HelmChart);
  }

  private void shouldNotRemoveComments(String manifestType) {
    String badIntentYaml = "key: value\n"
        + "metadata:\n"
        + "   name: global-route\n"
        + "  namespace: default\n";

    List<String> valuesFiles = List.of(badIntentYaml);
    List<String> renderedValuesFiles = K8sFilesCommentsHandler.removeComments(valuesFiles, manifestType);
    assertThat(renderedValuesFiles).isNotEmpty();
    assertThat(renderedValuesFiles).containsExactlyInAnyOrder(badIntentYaml);
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void shouldRemoveCommentsK8s() {
    shouldRemoveComments(ManifestType.K8Manifest);
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void shouldRemoveCommentsHelm() {
    shouldRemoveComments(ManifestType.HelmChart);
  }

  private void shouldRemoveComments(String manifestType) {
    final String originalYaml = "# this is the index value\n"
        + "id: \"0\"\n"
        + "percentage: \"<+variables.weight>\" # weight of deployment\n"
        + "config: # deployment config\n\n"
        + "\tcanary: true # is canary deployment\n"
        + "\tname: test deployment # deployment name";

    final String expectedYaml = "# " + REDACTED_BY_HARNESS + "\n"
        + "id: \"0\"\n"
        + "percentage: \"<+variables.weight>\" "
        + "# " + REDACTED_BY_HARNESS + "\n"
        + "config: "
        + "# " + REDACTED_BY_HARNESS + "\n\n"
        + "\tcanary: true "
        + "# " + REDACTED_BY_HARNESS + "\n"
        + "\tname: test deployment "
        + "# " + REDACTED_BY_HARNESS;

    List<String> valuesFiles = List.of(originalYaml);
    List<String> renderedValuesFiles = K8sFilesCommentsHandler.removeComments(valuesFiles, manifestType);
    assertThat(renderedValuesFiles).isNotEmpty();
    assertThat(renderedValuesFiles).containsExactlyInAnyOrder(expectedYaml);
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void shouldSkipRemoveComments() {
    final String fileWithComments = "# just a comment\n"
        + "key: value";

    List<String> valuesFile = List.of(fileWithComments);
    List<String> result = K8sFilesCommentsHandler.removeComments(valuesFile, ManifestType.Kustomize);
    assertThat(result).isNotEmpty();
    assertThat(result).isSameAs(valuesFile);
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void shouldPreserverFilesOrder() {
    final List<String> valuesFile =
        List.of(yamlId(1, "a"), yamlId(2, "b"), yamlId(3, "c"), yamlId(4, "4"), yamlId(5, "e"), yamlId(6, "f"));
    final List<String> expectedValuesFiles =
        List.of(yamlId(1, REDACTED_BY_HARNESS), yamlId(2, REDACTED_BY_HARNESS), yamlId(3, REDACTED_BY_HARNESS),
            yamlId(4, REDACTED_BY_HARNESS), yamlId(5, REDACTED_BY_HARNESS), yamlId(6, REDACTED_BY_HARNESS));

    List<String> result = K8sFilesCommentsHandler.removeComments(valuesFile, ManifestType.K8Manifest);
    assertThat(result).containsExactlyElementsOf(expectedValuesFiles);
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void shouldNotFallbackSameYamlWithRemovingComments() {
    String sourceYaml = "# start comment"
        + "key: value\n"
        + "nested: \n"
        + "  key: value\n"
        + "jsonList: [\"a\", \"b\", \"c\"]\n"
        + "list: \n"
        + "- key: value\n"
        + "  key2: value2\n"
        + "- keyn: valuen\n"
        + "  \"keyn+1\": \"valuen+1\"\n"
        + "folded: >\n"
        + "  this is a string\n"
        + "  this is another string\n"
        + "literal: |-\n"
        + "  just a string\n"
        + "  and another one\n";

    String yaml1 = sourceYaml + "\n"
        + "# comment at the end of file";
    String yaml2 = "# comment at the start of file\n" + sourceYaml;

    String result = K8sFilesCommentsHandler.matchOrFallback(yaml1, yaml2);
    assertThat(result).isEqualTo(yaml1);
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void shouldNotFallbackSameYamlRemoveCommentsIntegration() {
    String yaml = "# start comment"
        + "key: value\n"
        + "# block comment\n"
        + "nested: \n"
        + "  key: value\n"
        + "jsonList: [\"a\", \"b\", \"c\"] # simple comment\n"
        + "list: \n"
        + "- # list comment"
        + "  key: value\n"
        + "  key2: value2\n"
        + "- keyn: valuen # inline comment\n"
        + "  \"keyn+1\": \"valuen+1\"\n"
        + "folded: >\n"
        + "  this is a string\n"
        + "  this is another string with not # a comment\n"
        + "literal: |-\n"
        + "  just a string\n"
        + "  and another one\n";
    List<String> yamlWithoutComments = K8sFilesCommentsHandler.removeComments(List.of(yaml), ManifestType.K8Manifest);

    String result = K8sFilesCommentsHandler.matchOrFallback(yamlWithoutComments.get(0), yaml);
    assertThat(result).isEqualTo(yamlWithoutComments.get(0));
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void shouldFallbackDueToMismatchingYaml() {
    String yaml = "key: value1";
    String fallbackYaml = "key: value2";

    String result = K8sFilesCommentsHandler.matchOrFallback(yaml, fallbackYaml);
    assertThat(result).isEqualTo(fallbackYaml);
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void shouldFallbackDueToMismatchingYamlNested() {
    String yaml = "key: \n"
        + "  nested1: value1\n"
        + "  nested2: \n"
        + "    key1: value1";

    String fallbackYaml = "key: \n"
        + "  nested1: value1\n"
        + "  nested2: \n"
        + "    key2: value1\n";

    String result = K8sFilesCommentsHandler.matchOrFallback(yaml, fallbackYaml);
    assertThat(result).isEqualTo(fallbackYaml);
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void shouldFallbackDueToMismatchingYamlNestedDifferentValues() {
    String yaml = "key: \n"
        + "  nested1: value1\n"
        + "  nested2: \n"
        + "    key1: value1";

    String fallbackYaml = "key: \n"
        + "  nested1: value1\n"
        + "  nested2: \n"
        + "    key1: |-\n"
        + "      value2\n";

    String result = K8sFilesCommentsHandler.matchOrFallback(yaml, fallbackYaml);
    assertThat(result).isEqualTo(fallbackYaml);
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void shouldFallbackDueToMismatchYamlList() {
    String yaml = "key: \n"
        + "  list: \n"
        + "  - item1\n"
        + "  - item2\n"
        + "  - item3\n";

    String fallbackYaml = "key: \n"
        + "  list: \n"
        + "  - item1\n"
        + "  - itemn\n"
        + "  - itemn1";

    String result = K8sFilesCommentsHandler.matchOrFallback(yaml, fallbackYaml);
    assertThat(result).isEqualTo(fallbackYaml);
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void shouldFallbackDueToInvalidYaml() {
    String yaml = "key:\n"
        + "  list: "
        + "  - item1\n"
        + "  - item2\n"
        + "  - item3\n";

    String fallbackYaml = "key:\n"
        + "  list: \n"
        + "  - item1\n"
        + "  - item2\n"
        + "  - item3\n";

    String result = K8sFilesCommentsHandler.matchOrFallback(yaml, fallbackYaml);
    assertThat(result).isEqualTo(fallbackYaml);
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void shouldFallbackDueToIncorrectSize() {
    String yaml = "key1: value1\n"
        + "key2: value2";
    String fallbackYaml = "key: value1\n";

    String result = K8sFilesCommentsHandler.matchOrFallback(yaml, fallbackYaml);
    assertThat(result).isEqualTo(fallbackYaml);
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void shouldFallbackDueToEmpty() {
    String emptyYaml = "";
    String validYaml = "key: value1";

    assertThat(K8sFilesCommentsHandler.matchOrFallback(emptyYaml, validYaml)).isEqualTo(validYaml);
    assertThat(K8sFilesCommentsHandler.matchOrFallback(validYaml, emptyYaml)).isEqualTo(emptyYaml);
  }

  private String yamlId(int id, String comment) {
    return String.format("id: %d # %s", id, comment);
  }
}
