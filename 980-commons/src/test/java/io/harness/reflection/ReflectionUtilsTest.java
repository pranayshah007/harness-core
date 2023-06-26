/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.reflection;

import static io.harness.rule.OwnerRule.AADITI;
import static io.harness.rule.OwnerRule.ARVIND;
import static io.harness.rule.OwnerRule.GEORGE;
import static io.harness.rule.OwnerRule.PUNEET;
import static io.harness.rule.OwnerRule.UTKARSH;
import static io.harness.rule.OwnerRule.VAIBHAV_SI;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import org.junit.Assert;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class ReflectionUtilsTest extends CategoryTest {
  private static class FieldBase { public String baseField; }

  @Builder
  private static class Field extends FieldBase {
    public String field;
    public String inheritField;
    @DummyAnnotation public String annotatedField;
    @DummyAnnotation public List<String> annotatedListField;
    @DummyAnnotation public NestedAnnotationClass annotatedNestedField;
    public NestedAnnotationClass nestedField;
    @DummyAnnotation public NonNestedClass annotatedNonNestedField;
    @DummyAnnotation public List<NestedAnnotationClass> annotatedNestedListField;
    @DummyAnnotation public Map<String, NestedAnnotationClass> annotatedNestedMapField;
  }

  public static class NonNestedClass {
    private String field;
    @DummyAnnotation private String annotatedField;
    @DummyAnnotation private List<String> annotatedListField;
    @DummyAnnotation private Map<String, String> annotatedMapField;
  }

  @Builder
  @EqualsAndHashCode
  public static class NestedAnnotationClass implements ExpressionReflectionUtils.NestedAnnotationResolver {
    private String field;
    @DummyAnnotation private String annotatedField;
    @DummyAnnotation private List<String> annotatedListField;
    @DummyAnnotation private Map<String, String> annotatedMapField;
    @DummyAnnotation private List<Object> annotatedObjectListField;
  }

  private static class AccessorsBase {
    private String baseAccessor = "";
    public String getBaseAccessor() {
      return baseAccessor;
    }
  }

  private static class Accessors extends AccessorsBase {
    private String privateMethod;
    private String getPrivateMethod() {
      return privateMethod;
    }

    private String protectedMethod;
    protected String getProtectedMethod() {
      return privateMethod;
    }

    public String get() {
      return null;
    }

    public boolean is() {
      return false;
    }

    public String getNoField() {
      return null;
    }

    private String anyType;
    public String getAnyType() {
      return anyType;
    }

    public String isAnyType() {
      return anyType;
    }

    private boolean booleanType;
    public boolean isBooleanType() {
      return booleanType;
    }

    public boolean getBooleanType() {
      return booleanType;
    }

    private Boolean aBoolean;
    public Boolean isABoolean() {
      return aBoolean;
    }
  }

  interface AccessibleInterface {
    String VALUE_1 = "Value 1";
    String VALUE_2 = "Value 2";
    int VALUE_3 = 3;
    long VALUE_4 = 4;
    void testMethod1();
    String testMethod2();
    Long testMethod3();
  }

  @Retention(RetentionPolicy.RUNTIME)
  @Target(ElementType.FIELD)
  public @interface DummyAnnotation {
    boolean value() default true;
  }

  @Test
  @Owner(developers = GEORGE)
  @Category(UnitTests.class)
  public void getFieldByNameTest() {
    assertThat(ReflectionUtils.getFieldByName(Field.class, "dummy")).isNull();
    assertThat(ReflectionUtils.getFieldByName(Field.class, "inheritField").getName()).isEqualTo("inheritField");
    assertThat(ReflectionUtils.getFieldByName(Field.class, "baseField").getName()).isEqualTo("baseField");
    assertThat(ReflectionUtils.getFieldByName(Field.class, "annotatedField").getName()).isEqualTo("annotatedField");
    assertThat(ReflectionUtils.getFieldByName(Field.class, "annotatedListField").getName())
        .isEqualTo("annotatedListField");
  }

  @Test
  @Owner(developers = GEORGE)
  @Category(UnitTests.class)
  public void getAllDeclaredAndInheritedFields() {
    assertThat(ReflectionUtils.getAllDeclaredAndInheritedFields(Field.class)
                   .stream()
                   .filter(f -> f.getName().contains("ield"))
                   .count())
        .isEqualTo(10);
  }

  @Test
  @Owner(developers = GEORGE)
  @Category(UnitTests.class)
  public void getDeclaredAndInheritedFields() {
    assertThat(ReflectionUtils.getDeclaredAndInheritedFields(Field.class, f -> f.getName().endsWith("Field")))
        .hasSize(9);
  }

  @Test
  @Owner(developers = AADITI)
  @Category(UnitTests.class)
  public void updateField() {
    Field dummy = Field.builder().build();
    dummy.annotatedField = "test";
    ReflectionUtils.updateAnnotatedField(DummyAnnotation.class, dummy, (annotation, value) -> value + " hello world");
    assertThat(dummy.annotatedField).isEqualTo("test hello world");
  }

  @Test
  @Owner(developers = PUNEET)
  @Category(UnitTests.class)
  public void updateListField() {
    Field dummy = Field.builder().build();
    List<String> a = new ArrayList<>();
    a.add("one");
    a.add("two");
    dummy.annotatedListField = a;
    ReflectionUtils.updateAnnotatedField(DummyAnnotation.class, dummy, (annotation, value) -> value + " hello world");
    assertThat(dummy.annotatedListField).isEqualTo(ImmutableList.of("one hello world", "two hello world"));
  }

  @Test
  @Owner(developers = ARVIND)
  @Category(UnitTests.class)
  public void testUpdateNestedField() {
    Field dummy = Field.builder().build();
    dummy.annotatedNestedField = NestedAnnotationClass.builder().build();
    NestedAnnotationClass annotatedNestedField = dummy.annotatedNestedField;

    annotatedNestedField.field = "one";
    annotatedNestedField.annotatedField = "two";

    annotatedNestedField.annotatedListField = new ArrayList<>();
    annotatedNestedField.annotatedListField.add("three");
    annotatedNestedField.annotatedListField.add("four");

    annotatedNestedField.annotatedMapField = new HashMap<>();
    annotatedNestedField.annotatedMapField.put("key1", "five");
    annotatedNestedField.annotatedMapField.put("key2", "six");

    dummy.nestedField = NestedAnnotationClass.builder().build();
    NestedAnnotationClass nestedField = dummy.nestedField;

    nestedField.field = "seven";
    nestedField.annotatedField = "eight";

    ReflectionUtils.Functor<DummyAnnotation> dummyAnnotationFunctor = (annotation, value) -> value + "!";
    ReflectionUtils.updateAnnotatedField(DummyAnnotation.class, dummy, dummyAnnotationFunctor);
    assertThat(annotatedNestedField.field).isEqualTo("one");
    assertThat(annotatedNestedField.annotatedField).isEqualTo("two!");
    assertThat(annotatedNestedField.annotatedListField).isEqualTo(ImmutableList.of("three!", "four!"));
    assertThat(annotatedNestedField.annotatedMapField).isEqualTo(ImmutableMap.of("key1", "five!", "key2", "six!"));
    assertThat(nestedField.field).isEqualTo("seven");
    assertThat(nestedField.annotatedField).isEqualTo("eight");
  }

  @Test
  @Owner(developers = ARVIND)
  @Category(UnitTests.class)
  public void testUpdateNestedFieldList() {
    Field dummy = Field.builder().build();
    NestedAnnotationClass obj1 = NestedAnnotationClass.builder().field("one").annotatedField("two").build();
    NestedAnnotationClass obj2 = NestedAnnotationClass.builder().field("three").annotatedField("four").build();
    Object objectWithNoAnnotatedFields = new Object();
    NestedAnnotationClass obj3 = NestedAnnotationClass.builder()
                                     .field("test")
                                     .annotatedObjectListField(Collections.singletonList(objectWithNoAnnotatedFields))
                                     .build();
    dummy.annotatedNestedListField = new ArrayList<>();
    dummy.annotatedNestedListField.add(obj1);
    dummy.annotatedNestedListField.add(obj2);
    dummy.annotatedNestedListField.add(obj3);

    ReflectionUtils.Functor<DummyAnnotation> dummyAnnotationFunctor = (annotation, value) -> value + "!";
    ReflectionUtils.updateAnnotatedField(DummyAnnotation.class, dummy, dummyAnnotationFunctor);

    assertThat(dummy.annotatedNestedListField)
        .isEqualTo(ImmutableList.of(NestedAnnotationClass.builder().field("one").annotatedField("two!").build(),
            NestedAnnotationClass.builder().field("three").annotatedField("four!").build(),
            NestedAnnotationClass.builder()
                .field("test")
                .annotatedObjectListField(Collections.singletonList(objectWithNoAnnotatedFields))
                .build()));
  }

  @Test
  @Owner(developers = ARVIND)
  @Category(UnitTests.class)
  public void testUpdateNestedFieldMap() {
    Field dummy = Field.builder().build();
    NestedAnnotationClass obj1 = NestedAnnotationClass.builder().field("one").annotatedField("two").build();
    NestedAnnotationClass obj2 = NestedAnnotationClass.builder().field("three").annotatedField("four").build();
    dummy.annotatedNestedMapField = new HashMap<>();
    dummy.annotatedNestedMapField.put("key1", obj1);
    dummy.annotatedNestedMapField.put("key2", obj2);

    ReflectionUtils.Functor<DummyAnnotation> dummyAnnotationFunctor = (annotation, value) -> value + "!";
    ReflectionUtils.updateAnnotatedField(DummyAnnotation.class, dummy, dummyAnnotationFunctor);

    assertThat(dummy.annotatedNestedMapField.get("key1"))
        .isEqualTo(NestedAnnotationClass.builder().field("one").annotatedField("two!").build());
    assertThat(dummy.annotatedNestedMapField.get("key2"))
        .isEqualTo(NestedAnnotationClass.builder().field("three").annotatedField("four!").build());
  }

  @Test
  @Owner(developers = GEORGE)
  @Category(UnitTests.class)
  public void testGetAccessorMethods() {
    final List<String> accessorMethods =
        ReflectionUtils.getAccessorMethods(Accessors.class).stream().map(Method::getName).collect(Collectors.toList());
    accessorMethods.sort(String::compareTo);

    assertThat(accessorMethods).isEqualTo(asList("getAnyType", "getBaseAccessor", "isABoolean", "isBooleanType"));
  }

  @Test
  @Owner(developers = VAIBHAV_SI)
  @Category(UnitTests.class)
  public void shouldGetFieldValues() {
    Field dummy = Field.builder().field("val1").annotatedField("val2").build();
    HashSet<String> fields = new HashSet<>(Arrays.asList("field", "annotatedField", "wrongField"));

    Map<String, Object> fieldValues = ReflectionUtils.getFieldValues(dummy, fields);

    assertThat(fieldValues).hasSize(2);
    assertThat(fieldValues.get("field")).isEqualTo("val1");
    assertThat(fieldValues.get("annotatedField")).isEqualTo("val2");
  }

  @Test
  @Owner(developers = UTKARSH)
  @Category(UnitTests.class)
  public void shouldGetFieldValue() {
    Field dummy = Field.builder().field("val1").annotatedField("val2").build();
    java.lang.reflect.Field dummyField = ReflectionUtils.getFieldByName(dummy.getClass(), "field");
    Object object = ReflectionUtils.getFieldValue(dummy, dummyField);
    assertThat(object).isNotNull();
    assertThat((String) object).isEqualTo("val1");
  }

  @Test
  @Owner(developers = ARVIND)
  @Category(UnitTests.class)
  public void testGetInterfaceFieldValues() {
    // Test case 1: Class with matching interface fields
    Set<String> values1 = ReflectionUtils.getFieldValuesByType(AccessibleInterface.class, String.class);
    Assert.assertNotNull(values1);
    Assert.assertTrue(values1.contains("Value 1"));
    Assert.assertTrue(values1.contains("Value 2"));
    Assert.assertEquals(2, values1.size());

    // Test case 2: Class with no matching interface fields
    Set<String> values2 = ReflectionUtils.getFieldValuesByType(AccessibleInterface.class, Integer.class);
    Assert.assertNotNull(values2);
    Assert.assertTrue(values2.isEmpty());

    // Test case 2: Class with no matching interface fields
    Set<String> values3 = ReflectionUtils.getFieldValuesByType(AccessibleInterface.class, Long.class);
    Assert.assertNotNull(values3);
    Assert.assertTrue(values3.isEmpty());
  }
}
