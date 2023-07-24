/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.serializer;

import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.rule.OwnerRule.FERNANDOD;
import static io.harness.rule.OwnerRule.GEORGE;
import static io.harness.rule.OwnerRule.PRASHANT;
import static io.harness.serializer.MapperUtils.TEMPLATE_VARIABLE_ENTRY;
import static io.harness.serializer.MapperUtils.VARIABLE_DESCRIPTION_FIELD;
import static io.harness.serializer.MapperUtils.VARIABLE_VALUE_FIELD;
import static io.harness.serializer.MapperUtils.mapProperties;
import static io.harness.serializer.MapperUtils.sanitizeTemplateVariables;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;

import com.google.common.collect.Maps;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Data;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class MapperUtilsTest extends CategoryTest {
  @Test
  @Owner(developers = GEORGE)
  @Category(UnitTests.class)
  public void mapObject() throws Exception {
    Map<String, Object> map = Maps.newLinkedHashMap();
    map.put("toAddress", "a@b.com");
    map.put("subject", "test");
    map.put("body", "test");

    EmailState emailState = new EmailState("id");
    MapperUtils.mapObject(map, emailState);
    assertThat(emailState)
        .extracting(EmailState::getToAddress, EmailState::getSubject, EmailState::getBody, EmailState::getName)
        .containsExactly("a@b.com", "test", "test", "id");
  }

  @Test
  @Owner(developers = GEORGE)
  @Category(UnitTests.class)
  public void mapSomeFields() throws Exception {
    EmailState emailState = new EmailState("name1");
    emailState.setBody("body1");

    Map<String, Object> map = new HashMap<>();
    map.put("toAddress", "toAddress1");
    map.put("ccAddress", "ccAddress1");

    MapperUtils.mapObject(map, emailState);

    assertThat(emailState)
        .extracting("name", "body", "toAddress", "ccAddress", "subject")
        .containsExactly("name1", "body1", "toAddress1", "ccAddress1", null);
  }

  @Test
  @Owner(developers = PRASHANT)
  @Category(UnitTests.class)
  public void mapObjectOnlyNonNull() {
    EmailState emailState1 = new EmailState("name1", "toAddress1", "ccAddress1", "subject1", "body1", true);

    EmailState emailState2 = new EmailState("name2", "toAddress2", "ccAddress2", null, null, true);

    MapperUtils.mapObjectOnlyNonNull(emailState2, emailState1);

    assertThat(emailState1)
        .extracting("name", "body", "toAddress", "ccAddress", "subject")
        .containsExactly("name2", "body1", "toAddress2", "ccAddress2", "subject1");
  }

  @Test
  @Owner(developers = FERNANDOD)
  @Category(UnitTests.class)
  public void shouldMapProperties() {
    List<Map<String, String>> templateVariables = new ArrayList<>();
    templateVariables.add(Map.of("className", "io.harness.serializer.MapperUtilsTest.Variable", "name",
        "BENDER_BRANCH_NAME", "description", "any-value", "value", "master"));
    templateVariables.add(
        Map.of("className", "io.harness.serializer.MapperUtilsTest.Variable", "name", "SWITCH_CLOUD", "value", "true"));
    templateVariables.add(Map.of("className", "io.harness.serializer.MapperUtilsTest.Variable", "name", "NO_VALUE",
        "description", "description-text"));

    // io.harness.serializer.MapperUtilsTest.AnyState
    final AnyState target = new AnyState();
    final Map<String, Object> source = new HashMap<>();
    source.put("templateVariables", templateVariables);
    source.put("parentId", generateUuid());
    source.put("rollback", true);

    mapProperties(source, target);

    assertThat(target).isNotNull();
    assertThat(target.getParentId()).isEqualTo(source.get("parentId"));
    assertThat(target.isRollback()).isTrue();
    assertThat(target.getTemplateVariables()).hasSize(3);
    assertThat(target.getTemplateVariables().get(0).getName()).isEqualTo("BENDER_BRANCH_NAME");
    assertThat(target.getTemplateVariables().get(0).getValue()).isEqualTo("master");
    assertThat(target.getTemplateVariables().get(0).getDescription()).isEqualTo("any-value");
    assertThat(target.getTemplateVariables().get(1).getName()).isEqualTo("SWITCH_CLOUD");
    assertThat(target.getTemplateVariables().get(1).getValue()).isEqualTo("true");
    assertThat(target.getTemplateVariables().get(1).getDescription()).isEqualTo("");
    assertThat(target.getTemplateVariables().get(2).getName()).isEqualTo("NO_VALUE");
    assertThat(target.getTemplateVariables().get(2).getValue()).isEqualTo("");
    assertThat(target.getTemplateVariables().get(2).getDescription()).isEqualTo("description-text");
  }

  @Test
  @Owner(developers = FERNANDOD)
  @Category(UnitTests.class)
  public void shouldMapEntriesMissingValue() {
    List<Map<String, String>> templateVariables = new ArrayList<>();
    templateVariables.add(Map.of("className", "io.harness.serializer.MapperUtilsTest.Variable", "name",
        "BENDER_BRANCH_NAME", "value", "master"));
    templateVariables.add(Map.of("className", "io.harness.serializer.MapperUtilsTest.Variable", "name", "NO_VALUE"));

    final AnyState target = new AnyState();
    final Map<String, Object> source = new HashMap<>();
    source.put("templateVariables", templateVariables);

    mapProperties(source, target);

    assertThat(target).isNotNull();
    assertThat(target.getTemplateVariables()).hasSize(2);
    assertThat(target.getTemplateVariables().get(0).getName()).isEqualTo("BENDER_BRANCH_NAME");
    assertThat(target.getTemplateVariables().get(0).getValue()).isEqualTo("master");
    assertThat(target.getTemplateVariables().get(1).getName()).isEqualTo("NO_VALUE");
    assertThat(target.getTemplateVariables().get(1).getValue()).isEqualTo("");
  }

  @Test
  @Owner(developers = FERNANDOD)
  @Category(UnitTests.class)
  public void shouldMapEntriesMissingDescription() {
    List<Map<String, String>> templateVariables = new ArrayList<>();
    templateVariables.add(Map.of("className", "io.harness.serializer.MapperUtilsTest.Variable", "name",
        "BENDER_BRANCH_NAME", "description", "master"));
    templateVariables.add(Map.of("className", "io.harness.serializer.MapperUtilsTest.Variable", "name", "MISSING"));

    final AnyState target = new AnyState();
    final Map<String, Object> source = new HashMap<>();
    source.put("templateVariables", templateVariables);

    mapProperties(source, target);

    assertThat(target).isNotNull();
    assertThat(target.getTemplateVariables()).hasSize(2);
    assertThat(target.getTemplateVariables().get(0).getName()).isEqualTo("BENDER_BRANCH_NAME");
    assertThat(target.getTemplateVariables().get(0).getDescription()).isEqualTo("master");
    assertThat(target.getTemplateVariables().get(1).getName()).isEqualTo("MISSING");
    assertThat(target.getTemplateVariables().get(1).getDescription()).isEqualTo("");
  }

  @Test
  @Owner(developers = FERNANDOD)
  @Category(UnitTests.class)
  public void shouldSanitizeEntryNotRequiredField() {
    Map<String, Object> result = MapperUtils.sanitizeEntry(Map.entry("fieldName", 1410));
    assertThat(result.size()).isEqualTo(1);
    assertThat(result.get("fieldName")).isEqualTo(1410);
  }

  @Test
  @Owner(developers = FERNANDOD)
  @Category(UnitTests.class)
  public void shouldSanitizeEntryTemplateVariables() {
    Map<String, Object> result = MapperUtils.sanitizeEntry(Map.entry(TEMPLATE_VARIABLE_ENTRY, 1410));
    assertThat(result.size()).isEqualTo(1);
    assertThat(result.get(TEMPLATE_VARIABLE_ENTRY)).isEqualTo(1410);
  }

  @Test
  @Owner(developers = FERNANDOD)
  @Category(UnitTests.class)
  public void shouldSanitizeTemplateVariablesHandleClassCast() {
    final Map<String, Object> result = sanitizeTemplateVariables(Map.entry(TEMPLATE_VARIABLE_ENTRY, List.of("A", "B")));
    assertThat(result).isNotNull();
    assertThat(result).containsOnlyKeys(TEMPLATE_VARIABLE_ENTRY);
    assertThat(result.get(TEMPLATE_VARIABLE_ENTRY)).isInstanceOf(List.class);
    assertThat((List<String>) result.get(TEMPLATE_VARIABLE_ENTRY)).containsOnly("A", "B");
  }

  @Test
  @Owner(developers = FERNANDOD)
  @Category(UnitTests.class)
  public void shouldSanitizeTemplateVariablesHandleNullValue() {
    Map<String, Object> source = Collections.singletonMap(TEMPLATE_VARIABLE_ENTRY, null);
    final Map<String, Object> result = sanitizeTemplateVariables(source.entrySet().iterator().next());
    assertThat(result).isNotNull();
    assertThat(result).containsOnlyKeys(TEMPLATE_VARIABLE_ENTRY);
    assertThat(result.get(TEMPLATE_VARIABLE_ENTRY)).isNull();
  }

  @Test
  @Owner(developers = FERNANDOD)
  @Category(UnitTests.class)
  public void shouldSanitizeTemplateVariablesHandleNoDescriptionField() {
    List<Map<String, String>> templateVariables = new ArrayList<>();
    templateVariables.add(
        Map.of("className", "software.wings.beans.Variable", "name", "BENDER_BRANCH_NAME", "value", "master"));
    templateVariables.add(
        Map.of("className", "software.wings.beans.Variable", "name", "SWITCH_CLOUD", "value", "true"));

    final Map<String, Object> result = sanitizeTemplateVariables(Map.entry(TEMPLATE_VARIABLE_ENTRY, templateVariables));

    assertThat(result).isNotNull();
    assertThat(result).containsOnlyKeys(TEMPLATE_VARIABLE_ENTRY);
    assertThat(((List<Map<String, String>>) result.get(TEMPLATE_VARIABLE_ENTRY))
                   .stream()
                   .anyMatch(e -> e.containsKey(VARIABLE_DESCRIPTION_FIELD)))
        .isFalse();
  }

  @Test
  @Owner(developers = FERNANDOD)
  @Category(UnitTests.class)
  public void shouldSanitizeTemplateVariablesHandleNoValueField() {
    List<Map<String, String>> templateVariables = new ArrayList<>();
    templateVariables.add(
        Map.of("className", "software.wings.beans.Variable", "name", "BENDER_BRANCH_NAME", "description", "master"));
    templateVariables.add(
        Map.of("className", "software.wings.beans.Variable", "name", "SWITCH_CLOUD", "description", "true"));

    final Map<String, Object> result = sanitizeTemplateVariables(Map.entry(TEMPLATE_VARIABLE_ENTRY, templateVariables));

    assertThat(result).isNotNull();
    assertThat(result).containsOnlyKeys(TEMPLATE_VARIABLE_ENTRY);
    assertThat(((List<Map<String, String>>) result.get(TEMPLATE_VARIABLE_ENTRY))
                   .stream()
                   .anyMatch(e -> e.containsKey(VARIABLE_VALUE_FIELD)))
        .isFalse();
  }

  @Test
  @Owner(developers = FERNANDOD)
  @Category(UnitTests.class)
  public void shouldSanitizeTemplateVariablesHandleNotRequiredSanitization() {
    List<Map<String, String>> templateVariables = new ArrayList<>();
    templateVariables.add(Map.of("className", "software.wings.beans.Variable", "name", "BENDER_BRANCH_NAME"));
    templateVariables.add(Map.of("className", "software.wings.beans.Variable", "name", "SWITCH_CLOUD"));

    final Map<String, Object> result = sanitizeTemplateVariables(Map.entry(TEMPLATE_VARIABLE_ENTRY, templateVariables));

    assertThat(result).isNotNull();
    assertThat(result).containsOnlyKeys(TEMPLATE_VARIABLE_ENTRY);
    assertThat(((List<Map<String, String>>) result.get(TEMPLATE_VARIABLE_ENTRY))
                   .stream()
                   .anyMatch(e -> e.containsKey(VARIABLE_VALUE_FIELD) || e.containsKey(VARIABLE_DESCRIPTION_FIELD)))
        .isFalse();
  }

  @Test
  @Owner(developers = FERNANDOD)
  @Category(UnitTests.class)
  public void shouldSanitizeTemplateVariablesWhenAtLeastOneDescriptionFieldIsFound() {
    List<Map<String, String>> templateVariables = new ArrayList<>();
    templateVariables.add(Map.of("className", "software.wings.beans.Variable", "name", "BENDER_BRANCH_NAME",
        "description", "any-content", "value", "master"));
    templateVariables.add(
        Map.of("className", "software.wings.beans.Variable", "name", "SWITCH_CLOUD", "value", "true"));
    final Map<String, Object> result = sanitizeTemplateVariables(Map.entry(TEMPLATE_VARIABLE_ENTRY, templateVariables));
    assertThat(result).hasSize(1);
    assertThat(result.get(TEMPLATE_VARIABLE_ENTRY)).isInstanceOf(List.class);

    @SuppressWarnings("unchecked")
    final List<Map<String, String>> content = (List<Map<String, String>>) result.get(TEMPLATE_VARIABLE_ENTRY);
    assertThat(content).hasSize(2);
    //
    assertThat(content.get(0).get("className")).isEqualTo("software.wings.beans.Variable");
    assertThat(content.get(0).get("name")).isEqualTo("BENDER_BRANCH_NAME");
    assertThat(content.get(0).get("value")).isEqualTo("master");
    assertThat(content.get(0).get("description")).isEqualTo("any-content");
    //
    assertThat(content.get(1).get("className")).isEqualTo("software.wings.beans.Variable");
    assertThat(content.get(1).get("name")).isEqualTo("SWITCH_CLOUD");
    assertThat(content.get(1).get("value")).isEqualTo("true");
    assertThat(content.get(1).get("description")).isEqualTo("");
  }

  @Data
  @AllArgsConstructor
  public class EmailState {
    private String name;
    private String toAddress;
    private String ccAddress;
    private String subject;
    private String body;
    private Boolean ignoreDeliveryFailure = true;

    public EmailState(String name) {
      this.name = name;
    }
  }

  @Data
  public static class AnyState {
    private List<Variable> templateVariables = new ArrayList<>();
    private String parentId;
    private boolean rollback;
  }

  @Data
  public static class Variable {
    private String name;
    private String description;
    private String value;
  }
}
