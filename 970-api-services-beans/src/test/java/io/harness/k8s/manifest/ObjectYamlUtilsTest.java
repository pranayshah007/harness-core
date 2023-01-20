/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.k8s.manifest;

import static io.harness.govern.Switch.noop;
import static io.harness.k8s.utils.ObjectYamlUtils.encodeDot;
import static io.harness.k8s.utils.ObjectYamlUtils.getField;
import static io.harness.k8s.utils.ObjectYamlUtils.getFields;
import static io.harness.k8s.utils.ObjectYamlUtils.readYaml;
import static io.harness.k8s.utils.ObjectYamlUtils.setField;
import static io.harness.k8s.utils.ObjectYamlUtils.splitYamlFile;
import static io.harness.k8s.utils.ObjectYamlUtils.transformField;
import static io.harness.k8s.utils.ObjectYamlUtils.tryReadYaml;
import static io.harness.rule.OwnerRule.PUNEET;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;

import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.io.Resources;
import java.net.URL;
import java.util.List;
import java.util.function.UnaryOperator;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class ObjectYamlUtilsTest extends CategoryTest {
  @Test
  @Owner(developers = PUNEET)
  @Category(UnitTests.class)
  public void encodeDotTest() {
    assertThat(encodeDot("harness.io")).isEqualTo("harness[dot]io");
    assertThat(encodeDot("harness-io")).isEqualTo("harness-io");
  }

  @Test
  @Owner(developers = PUNEET)
  @Category(UnitTests.class)
  public void tryReadYamlTest() {
    Object object = tryReadYaml("Hello: World");

    assertThat(object).isNotNull();

    object = tryReadYaml(":");

    assertThat(object).isNull();
  }

  @Test
  @Owner(developers = PUNEET)
  @Category(UnitTests.class)
  public void sanityGetFieldTest() throws Exception {
    URL url = this.getClass().getResource("/sample.yaml");
    String fileContents = Resources.toString(url, Charsets.UTF_8);
    Object object = readYaml(fileContents).get(0);

    Object field = getField(null, "persons[0].name");

    assertThat(field).isNull();

    field = getField(object, "");

    assertThat(field).isNull();
  }

  @Test
  @Owner(developers = PUNEET)
  @Category(UnitTests.class)
  public void sanityGetFieldTrowsForArrayTest() throws Exception {
    URL url = this.getClass().getResource("/sample.yaml");
    String fileContents = Resources.toString(url, Charsets.UTF_8);
    Object object = readYaml(fileContents).get(0);

    try {
      getField(object, "persons[]");
      fail("Should have thrown.");
    } catch (IllegalArgumentException e) {
      noop();
    }
  }

  @Test
  @Owner(developers = PUNEET)
  @Category(UnitTests.class)
  public void sanityGetFieldsTest() throws Exception {
    URL url = this.getClass().getResource("/sample.yaml");
    String fileContents = Resources.toString(url, Charsets.UTF_8);
    Object object = readYaml(fileContents).get(0);

    List<Object> fields = getFields(null, "persons[0].name");

    assertThat(fields.size()).isEqualTo(0);

    fields = getFields(object, "");

    assertThat(fields.size()).isEqualTo(0);

    fields = getFields(object, "persons[]");

    assertThat(fields.size()).isEqualTo(3);
  }

  @Test
  @Owner(developers = PUNEET)
  @Category(UnitTests.class)
  public void basicYamlGetFieldTest() throws Exception {
    URL url = this.getClass().getResource("/sample.yaml");
    String fileContents = Resources.toString(url, Charsets.UTF_8);
    Object object = readYaml(fileContents).get(0);

    List<Object> fields = getFields(object, "persons[0].name");

    assertThat(fields.size()).isEqualTo(1);
    assertThat(fields.get(0)).isEqualTo("joe");

    fields = getFields(object, "persons[2].name");

    assertThat(fields.size()).isEqualTo(1);
    assertThat(fields.get(0)).isEqualTo("john");

    fields = getFields(object, "persons[].name");

    assertThat(fields.size()).isEqualTo(3);
    assertThat(fields).contains("nick");

    fields = getFields(object, "persons[0].favorites.fruits");

    assertThat(fields.size()).isEqualTo(1);
    assertThat(fields.get(0)).isEqualTo(ImmutableList.of("mango"));

    fields = getFields(object, "persons[0].favorites.drinks");

    assertThat(fields.size()).isEqualTo(1);
    assertThat(fields.get(0)).isEqualTo(ImmutableList.of("tea", "coffee"));
  }

  @Test
  @Owner(developers = PUNEET)
  @Category(UnitTests.class)
  public void arraysYamlGetFieldTest() throws Exception {
    URL url = this.getClass().getResource("/array-sample.yaml");
    String fileContents = Resources.toString(url, Charsets.UTF_8);
    Object object = readYaml(fileContents).get(0);

    List<Object> fields = getFields(object, "[].name");

    assertThat(fields.size()).isEqualTo(2);
    assertThat(fields.get(0)).isEqualTo("nginx");
    assertThat(fields.get(1)).isEqualTo("debian");

    fields = getFields(object, "[].ports[]");
    assertThat(fields.size()).isEqualTo(4);
    assertThat(fields.get(0)).isEqualTo(ImmutableMap.of("containerPort", "80"));
    assertThat(fields.get(1)).isEqualTo(ImmutableMap.of("containerPort", "8080"));
    assertThat(fields.get(2)).isEqualTo(ImmutableMap.of("containerPort", "80"));
    assertThat(fields.get(3)).isEqualTo(ImmutableMap.of("containerPort", "8080"));

    fields = getFields(object, "[].restartPolicy");
    assertThat(fields.size()).isEqualTo(1);
    assertThat(fields.get(0)).isEqualTo("never");

    fields = getFields(object, "[1].restartPolicy");
    assertThat(fields.size()).isEqualTo(1);
    assertThat(fields.get(0)).isEqualTo("never");
  }

  @Test
  @Owner(developers = PUNEET)
  @Category(UnitTests.class)
  public void sanitySetFieldTest() throws Exception {
    URL url = this.getClass().getResource("/sample.yaml");
    String fileContents = Resources.toString(url, Charsets.UTF_8);
    Object object = readYaml(fileContents).get(0);

    setField(object, "persons[0].name", "hello");

    List<Object> fields = getFields(object, "persons[0].name");

    assertThat(fields.size()).isEqualTo(1);
    assertThat(fields.get(0)).isEqualTo("hello");

    object = readYaml(fileContents).get(0);
    setField(object, "persons[].name", "dummy");
    fields = getFields(object, "persons[].name");

    assertThat(fields.size()).isEqualTo(3);
    assertThat(fields.get(0)).isEqualTo("dummy");
    assertThat(fields.get(1)).isEqualTo("dummy");
    assertThat(fields.get(2)).isEqualTo("dummy");
  }

  @Test
  @Owner(developers = PUNEET)
  @Category(UnitTests.class)
  public void arraysYamlSetFieldTest() throws Exception {
    URL url = this.getClass().getResource("/array-sample.yaml");
    String fileContents = Resources.toString(url, Charsets.UTF_8);
    Object object = readYaml(fileContents).get(0);

    setField(object, "[].restartPolicy", "always");

    List<Object> fields = getFields(object, "[].restartPolicy");
    assertThat(fields.size()).isEqualTo(2);
    assertThat(fields.get(0)).isEqualTo("always");
    assertThat(fields.get(1)).isEqualTo("always");
  }

  @Test
  @Owner(developers = PUNEET)
  @Category(UnitTests.class)
  public void sanityTranformFieldTest() throws Exception {
    URL url = this.getClass().getResource("/sample.yaml");
    String fileContents = Resources.toString(url, Charsets.UTF_8);
    Object object = readYaml(fileContents).get(0);

    UnaryOperator<Object> appendOne = t -> t + "-1";

    List<Object> fieldsBefore = getFields(object, "persons[0].name");

    object = readYaml(fileContents).get(0);
    transformField(object, "persons[0].name", appendOne);
    List<Object> fieldsAfter = getFields(object, "persons[0].name");

    assertThat(fieldsBefore.size()).isEqualTo(1);
    assertThat(fieldsAfter.size()).isEqualTo(1);
    assertThat(fieldsAfter.get(0)).isEqualTo(fieldsBefore.get(0) + "-1");

    object = readYaml(fileContents).get(0);

    fieldsBefore = getFields(object, "persons[].name");

    object = readYaml(fileContents).get(0);

    transformField(object, "persons[].name", appendOne);
    fieldsAfter = getFields(object, "persons[].name");

    assertThat(fieldsBefore.size()).isEqualTo(3);
    assertThat(fieldsAfter.size()).isEqualTo(3);
    assertThat(fieldsAfter.get(0)).isEqualTo(fieldsBefore.get(0) + "-1");
    assertThat(fieldsAfter.get(1)).isEqualTo(fieldsBefore.get(1) + "-1");
    assertThat(fieldsAfter.get(2)).isEqualTo(fieldsBefore.get(2) + "-1");
  }

  @Test
  @Owner(developers = PUNEET)
  @Category(UnitTests.class)
  public void arraySetFieldTest() throws Exception {
    URL url = this.getClass().getResource("/sample.yaml");
    String fileContents = Resources.toString(url, Charsets.UTF_8);
    Object object = readYaml(fileContents).get(0);

    setField(object, "persons[0].favorites.fruits[0]", "my-fruit");

    List<Object> fields = getFields(object, "persons[0].favorites.fruits[0]");

    assertThat(fields.size()).isEqualTo(1);
    assertThat(fields.get(0)).isEqualTo("my-fruit");

    object = readYaml(fileContents).get(0);

    setField(object, "persons[].favorites.fruits[0]", "my-fruit");

    fields = getFields(object, "persons[].favorites.fruits[0]");

    assertThat(fields.size()).isEqualTo(3);
    assertThat(fields.get(0)).isEqualTo("my-fruit");
    assertThat(fields.get(1)).isEqualTo("my-fruit");
    assertThat(fields.get(2)).isEqualTo("my-fruit");

    object = readYaml(fileContents).get(0);

    setField(object, "persons", "personA");

    fields = getFields(object, "persons[]");
    assertThat(fields.size()).isEqualTo(0);

    fields = getFields(object, "persons");
    assertThat(fields.size()).isEqualTo(1);
    assertThat(fields.get(0)).isEqualTo("personA");
  }

  @Test
  @Owner(developers = PUNEET)
  @Category(UnitTests.class)
  public void splitYamlFileSanityTest() throws Exception {
    URL url = this.getClass().getResource("/mongo.yaml");
    String fileContents = Resources.toString(url, Charsets.UTF_8);

    List<String> yamlDocs = splitYamlFile(fileContents);
    assertThat(yamlDocs).hasSize(4);

    yamlDocs = splitYamlFile("---");
    assertThat(yamlDocs).isEmpty();

    yamlDocs = splitYamlFile("hello: world");
    assertThat(yamlDocs).hasSize(1);
    assertThat(yamlDocs.get(0)).contains("hello: world");

    yamlDocs = splitYamlFile("---\nhello: world\n---\nfoo: bar\n---");
    assertThat(yamlDocs).hasSize(2);
    assertThat(yamlDocs.get(0)).contains("hello: world");
    assertThat(yamlDocs.get(1)).contains("foo: bar");
  }
}
