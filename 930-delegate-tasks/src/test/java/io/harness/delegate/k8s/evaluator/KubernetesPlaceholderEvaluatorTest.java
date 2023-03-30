/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.k8s.evaluator;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.rule.OwnerRule.ABOSII;

import static com.google.common.collect.ImmutableMap.of;
import static java.lang.String.format;
import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.k8s.KubernetesPlaceholder;
import io.harness.k8s.KubernetesReleaseDetails;
import io.harness.k8s.model.K8sYamlUtils;
import io.harness.rule.Owner;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.yaml.snakeyaml.Yaml;

@OwnedBy(CDP)
public class KubernetesPlaceholderEvaluatorTest extends CategoryTest {
  private static final String MULTILINE_FORMAT = "Lorem ipsum dolor %s\nsit amet, consectetur %s\n%s adipiscing";
  // Contains 8 string formats
  private static final String MULTIFORMAT_TEXT = "Lorem ipsum dolor %s amet, consectetur adipiscing elit.\n"
      + "Cras ante tellus, auctor et tincidunt sed, %s nec lacus. Mauris finibus ante nec ex %s.\n"
      + "Morbi eget felis nunc. Nam eget velit %s amet erat tempor %s. "
      + "Sed eget purus ac arcu vehicula %s a sed orci. Vivamus %s justo fringilla suscipit %s.";

  private static final String PLACEHOLDER_ORDER_FORMAT = "Text order %d with placeholder %s";

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testReplacePlaceholderInFreeText() {
    final Map<String, String> placeholders = of("$__PLACEHOLDER_1__", "placeholder1-value", "$__PLACEHOLDER_2__",
        "placeholder2-value", "$__PLACEHOLDER_3__", "placeholder3-value");
    final KubernetesPlaceholderEvaluator evaluator = KubernetesPlaceholderEvaluator.from(placeholders);

    String result =
        evaluator.evaluate(format(MULTILINE_FORMAT, "$__PLACEHOLDER_1__", "$__PLACEHOLDER_2__", "$__PLACEHOLDER_3__"));

    assertThat(result).isEqualTo(
        format(MULTILINE_FORMAT, "placeholder1-value", "placeholder2-value", "placeholder3-value"));
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testReplacePlaceholderInvalidCases() {
    final Map<String, String> placeholders = of("$__PLACEHOLDER__", "placeholder-value");
    final String input = format(MULTIFORMAT_TEXT, "__PLACEHOLDER__", "PLACEHOLDER", "$__PLACE HOLDER__",
        "$__ PLACEHOLDER__", "__PLACEHOLDER__$", "$PLACEHOLDER", "$__PLACEHOLDER1__", "$__PLACEHOLDER__");
    final String expectedResult = format(MULTIFORMAT_TEXT, "__PLACEHOLDER__", "PLACEHOLDER", "$__PLACE HOLDER__",
        "$__ PLACEHOLDER__", "__PLACEHOLDER__$", "$PLACEHOLDER", "$__PLACEHOLDER1__", "placeholder-value");
    final KubernetesPlaceholderEvaluator evaluator = KubernetesPlaceholderEvaluator.from(placeholders);

    String result = evaluator.evaluate(input);
    assertThat(result).isEqualTo(expectedResult);
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testReplacePlaceholderAllPreservingOrder() {
    final List<String> orderedTextList = Arrays.asList(format(PLACEHOLDER_ORDER_FORMAT, 0, "$__PLACEHOLDER1__"),
        format(PLACEHOLDER_ORDER_FORMAT, 1, "$__PLACEHOLDER2__"),
        format(PLACEHOLDER_ORDER_FORMAT, 2, "$__PLACEHOLDER3__"),
        format(PLACEHOLDER_ORDER_FORMAT, 3, "$__PLACEHOLDERN__"),
        format(PLACEHOLDER_ORDER_FORMAT, 4, "$__PLACEHOLDERN__"),
        format(PLACEHOLDER_ORDER_FORMAT, 5, "$__PLACEHOLDERN__"),
        format(PLACEHOLDER_ORDER_FORMAT, 6, "$__PLACEHOLDERN__"));
    final List<String> expectedOrderedTextList = Arrays.asList(format(PLACEHOLDER_ORDER_FORMAT, 0, "value1"),
        format(PLACEHOLDER_ORDER_FORMAT, 1, "value2"), format(PLACEHOLDER_ORDER_FORMAT, 2, "value3"),
        format(PLACEHOLDER_ORDER_FORMAT, 3, "valuen"), format(PLACEHOLDER_ORDER_FORMAT, 4, "valuen"),
        format(PLACEHOLDER_ORDER_FORMAT, 5, "valuen"), format(PLACEHOLDER_ORDER_FORMAT, 6, "valuen"));
    final Map<String, String> placeholders = of("$__PLACEHOLDER1__", "value1", "$__PLACEHOLDER2__", "value2",
        "$__PLACEHOLDER3__", "value3", "$__PLACEHOLDERN__", "valuen");

    List<String> result = KubernetesPlaceholderEvaluator.evaluateAllStatic(orderedTextList, placeholders);
    assertThat(result).containsExactlyElementsOf(expectedOrderedTextList);
    // control assertion even that containsExactlyElementsOf is expected to fail if order doesn't match
    assertThat(result.get(4)).isEqualTo(format(PLACEHOLDER_ORDER_FORMAT, 4, "valuen"));
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testReplacePlaceholdersInYaml() {
    final String yamlInput = createYamlInputWithValues(
        KubernetesPlaceholder.REVISION_NUMBER.getPlaceholder(), KubernetesPlaceholder.RELEASE_COLOR.getPlaceholder());
    final KubernetesReleaseDetails releaseDetails =
        KubernetesReleaseDetails.builder().releaseNumber(3).color("blue").build();
    final KubernetesPlaceholderEvaluator evaluator = KubernetesPlaceholderEvaluator.from(releaseDetails.toContextMap());

    String stringResult = evaluator.evaluate(yamlInput);
    assertThat(stringResult).isEqualTo(createYamlInputWithValues("3", "blue"));
  }

  private String createYamlInputWithValues(String revision, String color) {
    final Yaml yaml = K8sYamlUtils.createYamlWithCustomConstructor();

    Map<String, Object> input = of("value", format("r-%s", revision), "name", format("test-%s", revision), "release",
        of("data", of("revision", format("r-%s", revision), "color", color)), "revisionNumber",
        format("r-%s", revision), "revisionMultiline", format(MULTILINE_FORMAT, revision, color, revision));

    return yaml.dump(input);
  }
}