/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.serializer.recaster;

import static io.harness.rule.OwnerRule.ALEXEI;
import static io.harness.rule.OwnerRule.ARCHIT;
import static io.harness.rule.OwnerRule.BRIJESH;
import static io.harness.rule.OwnerRule.GARVIT;
import static io.harness.rule.OwnerRule.PRASHANT;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertFalse;
import static junit.framework.TestCase.assertNotNull;
import static junit.framework.TestCase.assertTrue;
import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.annotation.RecasterFieldName;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.pms.contracts.execution.ExecutionErrorInfo;
import io.harness.pms.contracts.execution.failure.FailureType;
import io.harness.pms.yaml.ParameterField;
import io.harness.pms.yaml.ParameterFieldValueWrapper;
import io.harness.rule.Owner;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.Value;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(HarnessTeam.PIPELINE)
public class RecastOrchestrationUtilsTest extends CategoryTest {
  @Test
  @Owner(developers = PRASHANT)
  @Category(UnitTests.class)
  public void shouldTestToSimpleJsonForNull() {
    String simpleJson = RecastOrchestrationUtils.toSimpleJson(null);
    assertThat(simpleJson).isNotNull();
    assertThat(simpleJson).isEqualTo("{}");
  }

  @Test
  @Owner(developers = ARCHIT)
  @Category(UnitTests.class)
  public void shouldTestForStringArray() {
    String[] s = new String[] {"a", "b"};
    String json = RecastOrchestrationUtils.toJson(s);
    Object response = RecastOrchestrationUtils.fromJson(json, Object.class);
    assertThat(response).isInstanceOf(ArrayList.class);
    assertThat(((ArrayList) response).size()).isEqualTo(2);
  }

  @Test
  @Owner(developers = ARCHIT)
  @Category(UnitTests.class)
  public void shouldTestForIntArray() {
    int[] s = new int[] {1, 2};
    String json = RecastOrchestrationUtils.toJson(s);
    Object response = RecastOrchestrationUtils.fromJson(json, Object.class);
    assertThat(response).isInstanceOf(ArrayList.class);
    assertThat((ArrayList) response).containsExactly(1, 2);
  }

  @Test
  @Owner(developers = ARCHIT)
  @Category(UnitTests.class)
  public void shouldTestForBooleanArray() {
    boolean[] s = new boolean[] {true, false};
    String json = RecastOrchestrationUtils.toJson(s);
    Object response = RecastOrchestrationUtils.fromJson(json, Object.class);
    assertThat(response).isInstanceOf(ArrayList.class);
    assertThat((ArrayList) response).containsExactly(true, false);
  }

  @Test
  @Owner(developers = ALEXEI)
  @Category(UnitTests.class)
  public void shouldTestToSimpleJson() {
    DummyB dummyB = DummyB.builder()
                        .pf(ParameterField.createValueField("value"))
                        .intVal(1)
                        .longVal(1242352345234L)
                        .doubleVal(12423532.235254)
                        .listVal(Lists.newArrayList("a", "b", "c"))
                        .mapVal(Maps.newHashMap(ImmutableMap.of("1", 1)))
                        .strVal("string")
                        .build();
    String simpleJson = RecastOrchestrationUtils.toSimpleJson(dummyB);

    String expectedValue =
        "{\"strVal\":\"string\",\"intVal\":1,\"longVal\":1242352345234,\"doubleVal\":1.2423532235254E7,\"listVal\":[\"a\",\"b\",\"c\"],\"mapVal\":{\"1\":1},\"pf\":\"value\"}";
    assertThat(simpleJson).isNotNull();
    assertThat(simpleJson).isEqualTo(expectedValue);
  }

  @Test
  @Owner(developers = ALEXEI)
  @Category(UnitTests.class)
  public void shouldTestToSimpleJsonWithListAsAField() {
    DummyB dummyB = DummyB.builder()
                        .pf(ParameterField.createValueField("value"))
                        .intVal(1)
                        .longVal(1242352345234L)
                        .doubleVal(12423532.235254)
                        .listVal(Lists.newArrayList("a", "b", "c"))
                        .mapVal(Maps.newHashMap(ImmutableMap.of("1", 1)))
                        .strVal("string")
                        .build();

    DummyB dummyB1 = DummyB.builder()
                         .pf(ParameterField.createValueField("value"))
                         .intVal(2)
                         .longVal(1242352345234L)
                         .doubleVal(12423532.235254)
                         .listVal(Lists.newArrayList("a", "b", "c"))
                         .mapVal(Maps.newHashMap(ImmutableMap.of("1", 2)))
                         .strVal("string")
                         .build();

    DummyA dummyA = new DummyA("dasgsregww", 12355235235L, Lists.newArrayList(dummyB, dummyB1));

    String simpleJson = RecastOrchestrationUtils.toSimpleJson(dummyA);

    String expectedValue =
        "{\"strVal\":\"dasgsregww\",\"aLong\":12355235235,\"list\":[{\"strVal\":\"string\",\"intVal\":1,\"longVal\":1242352345234,\"doubleVal\":1.2423532235254E7,\"listVal\":[\"a\",\"b\",\"c\"],\"mapVal\":{\"1\":1},\"pf\":\"value\"},{\"strVal\":\"string\",\"intVal\":2,\"longVal\":1242352345234,\"doubleVal\":1.2423532235254E7,\"listVal\":[\"a\",\"b\",\"c\"],\"mapVal\":{\"1\":2},\"pf\":\"value\"}]}";
    assertThat(simpleJson).isNotNull();
    assertThat(simpleJson).isEqualTo(expectedValue);
  }

  @Test
  @Owner(developers = ALEXEI)
  @Category(UnitTests.class)
  public void shouldTestToSimpleJsonWithParameterField() {
    DummyB dummyB = DummyB.builder()
                        .pf(ParameterField.createValueField("value"))
                        .intVal(1)
                        .longVal(1242352345234L)
                        .doubleVal(12423532.235254)
                        .listVal(Lists.newArrayList("a", "b", "c"))
                        .mapVal(Maps.newHashMap(ImmutableMap.of("1", 1)))
                        .strVal("string")
                        .build();

    DummyB dummyB1 = DummyB.builder()
                         .pf(ParameterField.createValueField("value"))
                         .intVal(2)
                         .longVal(1242352345234L)
                         .doubleVal(12423532.235254)
                         .listVal(Lists.newArrayList("a", "b", "c"))
                         .mapVal(Maps.newHashMap(ImmutableMap.of("1", 2)))
                         .strVal("string")
                         .build();

    ParameterDummy parameterDummy =
        ParameterDummy.builder().list(ParameterField.createValueField(Lists.newArrayList(dummyB, dummyB1))).build();

    String simpleJson = RecastOrchestrationUtils.toSimpleJson(parameterDummy);

    String expectedValue =
        "{\"list\":[{\"strVal\":\"string\",\"intVal\":1,\"longVal\":1242352345234,\"doubleVal\":1.2423532235254E7,\"listVal\":[\"a\",\"b\",\"c\"],\"mapVal\":{\"1\":1},\"pf\":\"value\"},{\"strVal\":\"string\",\"intVal\":2,\"longVal\":1242352345234,\"doubleVal\":1.2423532235254E7,\"listVal\":[\"a\",\"b\",\"c\"],\"mapVal\":{\"1\":2},\"pf\":\"value\"}]}";
    assertThat(simpleJson).isNotNull();
    assertThat(simpleJson).isEqualTo(expectedValue);
  }

  @Test
  @Owner(developers = ALEXEI)
  @Category(UnitTests.class)
  public void shouldTestToSimpleJsonWithEncodedValue() {
    ExecutionErrorInfo executionErrorInfo = ExecutionErrorInfo.newBuilder().setMessage("some-message").build();
    ProtoAsAFieldClass protoAsAFieldClass =
        ProtoAsAFieldClass.builder()
            .executionErrorInfo(executionErrorInfo)
            .failureTypeSet(Sets.newHashSet(FailureType.APPLICATION_FAILURE, FailureType.AUTHORIZATION_FAILURE))
            .build();

    String simpleJson = RecastOrchestrationUtils.toSimpleJson(protoAsAFieldClass);

    String expectedValue =
        "{\"executionErrorInfo\":{\"message\":\"some-message\"},\"failureTypeSet\":[\"APPLICATION_FAILURE\",\"AUTHORIZATION_FAILURE\"]}";
    assertThat(simpleJson).isNotNull();
    assertThat(simpleJson).isEqualTo(expectedValue);
  }

  @Test
  @Owner(developers = ALEXEI)
  @Category(UnitTests.class)
  public void shouldTestToSimpleJsonWithEncodedClass() {
    ExecutionErrorInfo executionErrorInfo = ExecutionErrorInfo.newBuilder().setMessage("some-message").build();

    String simpleJson = RecastOrchestrationUtils.toSimpleJson(executionErrorInfo);

    String expectedValue = "{\n"
        + "  \"message\": \"some-message\"\n"
        + "}";
    assertThat(simpleJson).isNotNull();
    assertThat(simpleJson).isEqualTo(expectedValue);
  }

  @Test
  @Owner(developers = ALEXEI)
  @Category(UnitTests.class)
  public void shouldTestToSimpleJsonWithList() {
    DummyB dummyB = DummyB.builder()
                        .pf(ParameterField.createValueField("value"))
                        .intVal(1)
                        .longVal(1242352345234L)
                        .doubleVal(12423532.235254)
                        .listVal(Lists.newArrayList("a", "b", "c"))
                        .mapVal(Maps.newHashMap(ImmutableMap.of("1", 1)))
                        .strVal("string")
                        .build();

    DummyB dummyB1 = DummyB.builder()
                         .pf(ParameterField.createValueField("value"))
                         .intVal(2)
                         .longVal(1242352345234L)
                         .doubleVal(12423532.235254)
                         .listVal(Lists.newArrayList("a", "b", "c"))
                         .mapVal(Maps.newHashMap(ImmutableMap.of("1", 2)))
                         .strVal("string")
                         .build();

    List<DummyB> list = Lists.newArrayList(dummyB, dummyB1);

    String simpleJson = RecastOrchestrationUtils.toSimpleJson(list);

    String expectedValue =
        "[{\"strVal\":\"string\",\"intVal\":1,\"longVal\":1242352345234,\"doubleVal\":1.2423532235254E7,\"listVal\":[\"a\",\"b\",\"c\"],\"mapVal\":{\"1\":1},\"pf\":\"value\"},{\"strVal\":\"string\",\"intVal\":2,\"longVal\":1242352345234,\"doubleVal\":1.2423532235254E7,\"listVal\":[\"a\",\"b\",\"c\"],\"mapVal\":{\"1\":2},\"pf\":\"value\"}]";

    assertThat(simpleJson).isNotNull();
    assertThat(simpleJson).isEqualTo(expectedValue);
  }

  @Test
  @Owner(developers = ALEXEI)
  @Category(UnitTests.class)
  public void shouldTestToSimpleJsonWithListOfEncodedObjects() {
    ExecutionErrorInfo executionErrorInfo = ExecutionErrorInfo.newBuilder().setMessage("some-message").build();
    ProtoAsAFieldClass protoAsAFieldClass =
        ProtoAsAFieldClass.builder()
            .executionErrorInfo(executionErrorInfo)
            .failureTypeSet(Sets.newHashSet(FailureType.APPLICATION_FAILURE, FailureType.AUTHORIZATION_FAILURE))
            .build();

    ExecutionErrorInfo executionErrorInfo1 = ExecutionErrorInfo.newBuilder().setMessage("some-message1").build();
    ProtoAsAFieldClass protoAsAFieldClass1 =
        ProtoAsAFieldClass.builder()
            .executionErrorInfo(executionErrorInfo1)
            .failureTypeSet(Sets.newHashSet(FailureType.AUTHENTICATION_FAILURE, FailureType.CONNECTIVITY_FAILURE))
            .build();

    List<ProtoAsAFieldClass> list = Lists.newArrayList(protoAsAFieldClass, protoAsAFieldClass1);

    String simpleJson = RecastOrchestrationUtils.toSimpleJson(list);

    String expectedValue =
        "[{\"executionErrorInfo\":{\"message\":\"some-message\"},\"failureTypeSet\":[\"APPLICATION_FAILURE\",\"AUTHORIZATION_FAILURE\"]},{\"executionErrorInfo\":{\"message\":\"some-message1\"},\"failureTypeSet\":[\"AUTHENTICATION_FAILURE\",\"CONNECTIVITY_FAILURE\"]}]";

    assertThat(simpleJson).isNotNull();
    assertThat(simpleJson).isEqualTo(expectedValue);
  }

  @Test
  @Owner(developers = ALEXEI)
  @Category(UnitTests.class)
  public void shouldTestToSimpleJsonWithMap() {
    ExecutionErrorInfo executionErrorInfo = ExecutionErrorInfo.newBuilder().setMessage("some-message").build();
    ExecutionErrorInfo executionErrorInfo1 = ExecutionErrorInfo.newBuilder().setMessage("some-message1").build();
    ExecutionErrorInfo executionErrorInfo2 = ExecutionErrorInfo.newBuilder().setMessage("some-message2").build();

    Map<String, ExecutionErrorInfo> executionErrorInfoMap =
        ImmutableMap.of("0", executionErrorInfo, "1", executionErrorInfo1, "2", executionErrorInfo2);

    String simpleJson = RecastOrchestrationUtils.toSimpleJson(executionErrorInfoMap);

    String expectedValue =
        "{\"0\":{\"message\":\"some-message\"},\"1\":{\"message\":\"some-message1\"},\"2\":{\"message\":\"some-message2\"}}";
    assertThat(simpleJson).isNotNull();
    assertThat(simpleJson).isEqualTo(expectedValue);
  }

  @Test
  @Owner(developers = GARVIT)
  @Category(UnitTests.class)
  public void shouldTestGenericMapInsideWrapper() {
    DummyC dummyC = new DummyC(
        new ParameterFieldValueWrapper<>(ImmutableMap.of("a", 123, "b", ParameterField.createValueField(123))));
    Map<String, Object> map = RecastOrchestrationUtils.toMap(dummyC);
    assertThat(map).isNotNull();

    DummyC dummyC1 = RecastOrchestrationUtils.fromMap(map, DummyC.class);
    assertThat(dummyC1).isNotNull();
    assertThat(dummyC1).isEqualTo(dummyC);
  }

  @Data
  @Builder
  public static class DummyB {
    private String strVal;
    private int intVal;
    private long longVal;
    private double doubleVal;
    private List<String> listVal;
    private Map<String, Integer> mapVal;
    private ParameterField<String> pf;
  }

  @Data
  @AllArgsConstructor
  public static class DummyA {
    private String strVal;
    private long aLong;
    private List<DummyB> list;
  }

  @Data
  @AllArgsConstructor
  public static class DummyC {
    ParameterFieldValueWrapper<Map<String, Object>> wrapper;
  }

  @Data
  @Builder
  public static class ParameterDummy {
    private ParameterField<List<DummyB>> list;
  }

  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  @EqualsAndHashCode
  private static class ProtoAsAFieldClass {
    private ExecutionErrorInfo executionErrorInfo;
    private Set<FailureType> failureTypeSet;
  }

  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void testRecasterWithRecasterFieldName() {
    InnerClassForTestingRecasterFieldName innerObject =
        InnerClassForTestingRecasterFieldName.builder().f1("foo").f2("bar").f3("fooBar").build();
    TestRecasterWithRecasterFieldName object = TestRecasterWithRecasterFieldName.builder()
                                                   .name("foo")
                                                   .value("bar")
                                                   .email("foo.bar")
                                                   .f1("f1 foo")
                                                   .f2(2)
                                                   .f3(3)
                                                   .innerObject1(innerObject)
                                                   .innerObject2(innerObject)
                                                   .build();
    Map map = RecastOrchestrationUtils.toMap(object);

    // Recasting again to object from the map.
    TestRecasterWithRecasterFieldName responseObject =
        RecastOrchestrationUtils.fromMap(map, TestRecasterWithRecasterFieldName.class);
    // Assert that recasted object has exact values as original object for all fields. This ensures that we can retrieve
    // the original object from recasted map.
    assertRecastedObject(object, responseObject);

    Map recastedMap = RecastOrchestrationUtils.toMap(responseObject);
    // Assert that first map(created from original object) and again recastedMap(Original object -> map,
    // map->recasterObject then recastedObject -> recastedMap) are exact same. This ensures reversible conversion
    // between object and map using recaster.
    assertEquals(map, recastedMap);
    assertNotNull(map);

    // Checking that map has the keys that were provided by annotation RecasterFieldName. If not provoded by annotation
    // then fieldName to be used as key.
    assertTrue(map.containsKey("name"));
    assertTrue(map.containsKey("value"));
    assertTrue(map.containsKey("contact"));
    assertFalse(map.containsKey("email"));

    assertTrue(map.containsKey("recasterF3"));
    assertTrue(map.containsKey("innerObject1"));
    assertTrue(map.containsKey("recasterInnerObject"));
    assertFalse(map.containsKey("innerObject2"));

    assertEquals(map.get("name"), "foo");
    assertEquals(map.get("value"), "bar");
    assertEquals(map.get("contact"), "foo.bar");
    assertEquals(map.get("f1"), "f1 foo");
    assertEquals(map.get("f2"), 2);
    assertEquals(map.get("recasterF3"), 3);

    Map innerMap1 = (Map) map.get("innerObject1");

    assertTrue(innerMap1.containsKey("recasterF1"));
    assertTrue(innerMap1.containsKey("f2"));
    assertTrue(innerMap1.containsKey("f3"));

    assertEquals(innerMap1.get("recasterF1"), "foo");
    assertEquals(innerMap1.get("f2"), "bar");
    assertEquals(innerMap1.get("f3"), "fooBar");

    Map innerMap2 = (Map) map.get("recasterInnerObject");

    assertTrue(innerMap2.containsKey("recasterF1"));
    assertTrue(innerMap2.containsKey("f2"));
    assertTrue(innerMap2.containsKey("f3"));

    assertEquals(innerMap2.get("recasterF1"), "foo");
    assertEquals(innerMap2.get("f2"), "bar");
    assertEquals(innerMap2.get("f3"), "fooBar");
  }

  private void assertRecastedObject(
      TestRecasterWithRecasterFieldName object, TestRecasterWithRecasterFieldName responseObject) {
    assertEquals(object.getName(), responseObject.getName());
    assertEquals(object.getValue(), responseObject.getValue());
    assertEquals(object.getEmail(), responseObject.getEmail());
    assertEquals(object.getF1(), responseObject.getF1());
    assertEquals(object.getF2(), responseObject.getF2());
    assertEquals(object.getF3(), responseObject.getF3());

    assertEquals(object.getInnerObject1().getF1(), responseObject.getInnerObject1().getF1());
    assertEquals(object.getInnerObject1().getF2(), responseObject.getInnerObject1().getF2());
    assertEquals(object.getInnerObject1().getF3(), responseObject.getInnerObject1().getF3());

    assertEquals(object.getInnerObject2().getF1(), responseObject.getInnerObject2().getF1());
    assertEquals(object.getInnerObject2().getF2(), responseObject.getInnerObject2().getF2());
    assertEquals(object.getInnerObject2().getF3(), responseObject.getInnerObject2().getF3());
  }

  @Value
  @Builder
  private static class TestRecasterWithRecasterFieldName {
    @RecasterFieldName(name = "name") String name;
    @RecasterFieldName(name = "") String value;
    @RecasterFieldName(name = "contact") String email;
    String f1;
    int f2;
    @RecasterFieldName(name = "recasterF3") int f3;
    InnerClassForTestingRecasterFieldName innerObject1;
    @RecasterFieldName(name = "recasterInnerObject") InnerClassForTestingRecasterFieldName innerObject2;
  }

  @Value
  @Builder
  private static class InnerClassForTestingRecasterFieldName {
    @RecasterFieldName(name = "recasterF1") String f1;
    @RecasterFieldName(name = "") String f2;
    String f3;
  }
}
