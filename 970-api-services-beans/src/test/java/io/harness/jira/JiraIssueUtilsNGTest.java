/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.jira;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.rule.OwnerRule.DEEPAK_PUTHRAYA;
import static io.harness.rule.OwnerRule.GARVIT;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.data.Offset.offset;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.exception.JiraClientException;
import io.harness.rule.Owner;
import io.harness.serializer.JsonUtils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableMap;
import com.google.common.io.Resources;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import org.assertj.core.api.Assertions;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(CDC)
public class JiraIssueUtilsNGTest extends CategoryTest {
  @Test
  @Owner(developers = GARVIT)
  @Category(UnitTests.class)
  public void testSplitByComma() {
    assertThat(JiraIssueUtilsNG.splitByComma(null)).isEmpty();
    assertThat(JiraIssueUtilsNG.splitByComma(null)).isEmpty();
    assertThat(JiraIssueUtilsNG.splitByComma("  ")).isEmpty();
    assertThat(JiraIssueUtilsNG.splitByComma("abc")).containsExactly("abc");
    assertThat(JiraIssueUtilsNG.splitByComma("abc,  def")).containsExactly("abc", "def");
    assertThat(JiraIssueUtilsNG.splitByComma("abc,  def, \"ghi, jkl\"")).containsExactly("abc", "def", "ghi, jkl");
  }

  @Test
  @Owner(developers = GARVIT)
  @Category(UnitTests.class)
  public void testUpdateFieldValues() throws IOException {
    String createMetadataJson = getResource("create_metadata.json");
    JsonNode node = JsonUtils.readTree(createMetadataJson);
    JiraIssueCreateMetadataNG createMetadata = new JiraIssueCreateMetadataNG(node);
    JiraIssueTypeNG issueType = createMetadata.getProjects().get("JEL").getIssueTypes().get("Story");
    assertThat(issueType).isNotNull();
    assertThat(issueType.getFields().size()).isEqualTo(22);

    issueType.removeField(JiraConstantsNG.STATUS_NAME);
    assertThat(issueType.getFields().size()).isEqualTo(21);

    Map<String, Object> currFieldsTmp = new HashMap<>();
    assertThatThrownBy(()
                           -> JiraIssueUtilsNG.updateFieldValues(
                               currFieldsTmp, issueType.getFields(), ImmutableMap.of("Component/s", "ds"), true))
        .isNotNull();
    assertThatThrownBy(()
                           -> JiraIssueUtilsNG.updateFieldValues(
                               currFieldsTmp, issueType.getFields(), ImmutableMap.of("number", "ds"), true))
        .isNotNull();
    assertThatThrownBy(()
                           -> JiraIssueUtilsNG.updateFieldValues(
                               currFieldsTmp, issueType.getFields(), ImmutableMap.of("Custom Date", "ds"), true))
        .isNotNull();
    assertThatThrownBy(()
                           -> JiraIssueUtilsNG.updateFieldValues(
                               currFieldsTmp, issueType.getFields(), ImmutableMap.of("customtime", "ds"), true))
        .isNotNull();

    Map<String, String> fields = new HashMap<>();
    fields.put("Summary", "summary");
    fields.put("Description", "description");
    fields.put("Component/s", "test, TestComp1");
    fields.put("Fix Version/s", "Version 1.0, Version 3.0");
    fields.put("Labels", "abc, def, \"hij, klm\"");
    fields.put("Priority", "Low");
    fields.put("Epic Link", "JEL-197");
    fields.put("CustomArray", "56.89");
    fields.put("testcustomfields", "1");
    fields.put("Test Custom Fields", "18");
    fields.put("test custom fields", "19.5");
    fields.put("number", "18.18");
    fields.put("Custom Date", "2021-03-25");
    fields.put("customtime", "2021-03-25T18:58:16.535+0000");
    fields.put("Original Estimate", "3d");
    fields.put("Remaining Estimate", "2d");
    fields.put("Reporter", "userid");

    Map<String, Object> currFields = new HashMap<>();
    JiraIssueUtilsNG.updateFieldValues(currFields, issueType.getFields(), fields, true);
    assertThat(currFields.size()).isEqualTo(16);
    assertThat(currFields.get("summary")).isEqualTo("summary");
    assertThat(currFields.get("description")).isEqualTo("description");
    assertThat(((List<JiraFieldAllowedValueNG>) currFields.get("components"))
                   .stream()
                   .map(JiraFieldAllowedValueNG::getId)
                   .collect(Collectors.toList()))
        .containsExactly("10001", "10000");
    assertThat(((List<JiraFieldAllowedValueNG>) currFields.get("fixVersions"))
                   .stream()
                   .map(JiraFieldAllowedValueNG::getId)
                   .collect(Collectors.toList()))
        .containsExactly("10000", "10002");
    assertThat((List<String>) currFields.get("labels")).containsExactly("abc", "def", "hij, klm");
    assertThat(((JiraFieldAllowedValueNG) currFields.get("priority")).getId()).isEqualTo("4");
    assertThat(currFields.get("customfield_10102")).isEqualTo("JEL-197");
    assertThat((Double) currFields.get("customfield_10212")).isCloseTo(56.89, offset(0.000001));
    assertThat(((List<JiraFieldAllowedValueNG>) currFields.get("customfield_10204"))
                   .stream()
                   .map(JiraFieldAllowedValueNG::getId)
                   .collect(Collectors.toList()))
        .containsExactly("10102");
    assertThat((Long) currFields.get("customfield_10206")).isEqualTo(18);
    assertThat((Double) currFields.get("customfield_10203")).isCloseTo(19.5, offset(0.000001));
    assertThat((Double) currFields.get("customfield_10207")).isCloseTo(18.18, offset(0.000001));
    assertThat(currFields.get("customfield_10210")).isEqualTo("2021-03-25");
    assertThat(currFields.get("customfield_10211")).isEqualTo("2021-03-25T18:58:16.535+0000");
    JiraFieldUserPickerNG reporter = (JiraFieldUserPickerNG) currFields.get("reporter");
    assertThat(reporter.getAccountId()).isEqualTo("userid");

    assertThat(((JiraTimeTrackingFieldNG) currFields.get("timetracking")).getOriginalEstimate()).isEqualTo("3d");
    assertThat(((JiraTimeTrackingFieldNG) currFields.get("timetracking")).getRemainingEstimate()).isEqualTo("2d");
  }

  @Test
  @Owner(developers = GARVIT)
  @Category(UnitTests.class)
  public void testUpdateFieldValuesEmptyCheck() {
    Map<String, Object> fields = new HashMap<>();
    assertThatCode(() -> JiraIssueUtilsNG.updateFieldValues(fields, null, null, true)).doesNotThrowAnyException();
    assertThatCode(
        () -> JiraIssueUtilsNG.updateFieldValues(fields, Collections.emptyMap(), Collections.emptyMap(), true))
        .doesNotThrowAnyException();
  }

  @Test
  @Owner(developers = GARVIT)
  @Category(UnitTests.class)
  public void testUpdateFieldValuesInvalidFieldsCheck() {
    Map<String, Object> fields = new HashMap<>();
    assertThatCode(()
                       -> JiraIssueUtilsNG.updateFieldValues(fields,
                           ImmutableMap.of("n1", createOptionalField(1), "n2", createOptionalField(2)), null, true))
        .doesNotThrowAnyException();
    assertThatCode(()
                       -> JiraIssueUtilsNG.updateFieldValues(fields,
                           ImmutableMap.of("n1", createOptionalField(1), "n2", createOptionalField(2)),
                           ImmutableMap.of("n1", "abc"), true))
        .doesNotThrowAnyException();
    assertThatCode(()
                       -> JiraIssueUtilsNG.updateFieldValues(fields,
                           ImmutableMap.of("n1", createOptionalField(1), "n2", createOptionalField(2)),
                           ImmutableMap.of("n2", "abc"), true))
        .doesNotThrowAnyException();
    assertThatCode(()
                       -> JiraIssueUtilsNG.updateFieldValues(fields,
                           ImmutableMap.of("n1", createOptionalField(1), "n2", createOptionalField(2)),
                           ImmutableMap.of("n1", "abc", "n2", "abc"), true))
        .doesNotThrowAnyException();
    assertThatThrownBy(()
                           -> JiraIssueUtilsNG.updateFieldValues(fields,
                               ImmutableMap.of("n1", createOptionalField(1), "n2", createOptionalField(2)),
                               ImmutableMap.of("n1", "abc", "n3", "abc"), true))
        .isInstanceOf(JiraClientException.class);
    assertThatThrownBy(()
                           -> JiraIssueUtilsNG.updateFieldValues(fields,
                               ImmutableMap.of("n1", createOptionalField(1), "n2", createOptionalField(2)),
                               ImmutableMap.of("n3", "abc"), true))
        .isInstanceOf(JiraClientException.class);
    assertThatThrownBy(() -> JiraIssueUtilsNG.updateFieldValues(fields, null, ImmutableMap.of("n3", "abc"), true))
        .isInstanceOf(JiraClientException.class);
    assertThatThrownBy(
        () -> JiraIssueUtilsNG.updateFieldValues(fields, Collections.emptyMap(), ImmutableMap.of("n3", "abc"), true))
        .isInstanceOf(JiraClientException.class);
  }

  @Test
  @Owner(developers = GARVIT)
  @Category(UnitTests.class)
  public void testUpdateFieldValuesRequiredFieldsCheck() {
    Map<String, Object> fields = new HashMap<>();
    assertThatCode(
        () -> JiraIssueUtilsNG.updateFieldValues(fields, ImmutableMap.of("n1", createOptionalField(1)), null, true))
        .doesNotThrowAnyException();
    assertThatCode(
        () -> JiraIssueUtilsNG.updateFieldValues(fields, ImmutableMap.of("n1", createOptionalField(1)), null, false))
        .doesNotThrowAnyException();
    assertThatThrownBy(
        () -> JiraIssueUtilsNG.updateFieldValues(fields, ImmutableMap.of("n1", createRequiredField(1)), null, true))
        .isInstanceOf(JiraClientException.class);
    assertThatCode(
        () -> JiraIssueUtilsNG.updateFieldValues(fields, ImmutableMap.of("n1", createRequiredField(1)), null, false))
        .doesNotThrowAnyException();
    assertThatCode(()
                       -> JiraIssueUtilsNG.updateFieldValues(
                           fields, ImmutableMap.of("n1", createRequiredField(1)), ImmutableMap.of("n1", "abc"), true))
        .doesNotThrowAnyException();
    assertThatCode(()
                       -> JiraIssueUtilsNG.updateFieldValues(fields,
                           ImmutableMap.of("n1", createRequiredField(1), "n2", createOptionalField(2)),
                           ImmutableMap.of("n1", "abc"), true))
        .doesNotThrowAnyException();
    assertThatThrownBy(()
                           -> JiraIssueUtilsNG.updateFieldValues(fields,
                               ImmutableMap.of("n1", createRequiredField(1), "n2", createOptionalField(2)),
                               ImmutableMap.of("n2", "abc"), true))
        .isInstanceOf(JiraClientException.class);
    assertThatCode(()
                       -> JiraIssueUtilsNG.updateFieldValues(fields,
                           ImmutableMap.of("n1", createRequiredField(1), "n2", createOptionalField(2)),
                           ImmutableMap.of("n2", "abc"), false))
        .doesNotThrowAnyException();
  }

  private JiraFieldNG createRequiredField(int index) {
    return createField(index, true);
  }

  private JiraFieldNG createOptionalField(int index) {
    return createField(index, false);
  }

  private JiraFieldNG createField(int index, boolean required) {
    return JiraFieldNG.builder()
        .key("k" + index)
        .name("n" + index)
        .required(required)
        .schema(JiraFieldSchemaNG.builder().type(JiraFieldTypeNG.STRING).build())
        .build();
  }

  @Test
  @Owner(developers = DEEPAK_PUTHRAYA)
  @Category(UnitTests.class)
  public void testGetIssueTicket() throws IOException {
    JsonNode json = new ObjectMapper().readTree(getResource("jira-api-output.json"));
    JiraIssueNG issue = new JiraIssueNG(json);
    Assertions.assertThat(issue).isNotNull();
    Assertions.assertThat(issue.getFields()).isNotEmpty();
    Assertions.assertThat(issue.getKey()).isEqualTo("EDNK-6594");
    Assertions.assertThat(issue.getFields().get("Status")).isEqualTo("Done");
  }

  private String getResource(String path) throws IOException {
    return Resources.toString(
        Objects.requireNonNull(getClass().getClassLoader().getResource("jira/" + path)), StandardCharsets.UTF_8);
  }
}
