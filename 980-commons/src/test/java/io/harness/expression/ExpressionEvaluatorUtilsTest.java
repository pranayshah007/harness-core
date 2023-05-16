/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.expression;

import static io.harness.rule.OwnerRule.GARVIT;
import static io.harness.rule.OwnerRule.HINGER;

import static java.util.Arrays.asList;
import static junit.framework.TestCase.assertEquals;
import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import lombok.Builder;
import lombok.Data;
import lombok.Value;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class ExpressionEvaluatorUtilsTest extends CategoryTest {
  @Test
  @Owner(developers = GARVIT)
  @Category(UnitTests.class)
  public void testFetchField() {
    Map<String, Object> map = ImmutableMap.of("a", "aVal", "b", DummyA.builder().strVal("bVal").build());
    Optional<Object> optional = ExpressionEvaluatorUtils.fetchField(map, "a");
    assertThat(optional).isPresent();
    assertThat(optional.get()).isEqualTo("aVal");

    optional = ExpressionEvaluatorUtils.fetchField(map, "b");
    assertThat(optional).isPresent();
    assertThat(optional.get()).isInstanceOf(DummyA.class);
    assertThat(((DummyA) optional.get()).getStrVal()).isEqualTo("bVal");

    DummyA dummyA = DummyA.builder().strVal("a").intVal(1).pairVal(Pair.of("b", "c")).build();
    optional = ExpressionEvaluatorUtils.fetchField(dummyA, "strVal");
    assertThat(optional).isPresent();
    assertThat(optional.get()).isEqualTo("a");

    optional = ExpressionEvaluatorUtils.fetchField(dummyA, "intVal");
    assertThat(optional).isPresent();
    assertThat(optional.get()).isEqualTo(1);

    optional = ExpressionEvaluatorUtils.fetchField(dummyA, "pairVal");
    assertThat(optional).isPresent();
    assertThat(optional.get()).isEqualTo(Pair.of("b", "c"));
  }

  @Value
  @Builder
  public static class DummyA {
    String strVal;
    int intVal;
    Pair<String, String> pairVal;
  }

  @Test
  @Owner(developers = GARVIT)
  @Category(UnitTests.class)
  public void testUpdateStringFieldValues() {
    Object obj = ExpressionEvaluatorUtils.updateExpressions(null, DummyFunctor.builder().build());
    assertThat(obj).isNull();

    String original = "original";
    String originalObject = "originalObject";
    String originalInt1 = "originalInt1";
    String originalInt2 = "originalInt2";
    String updated = "updated";
    List<Pair<String, String>> pairs =
        asList(ImmutablePair.of(original, original), ImmutablePair.of(original, original));
    Map<String, Object> map = new HashMap<>(ImmutableMap.of("a", original, "b", 2, "c", ImmutablePair.of(1, original)));
    Set<String> set = ImmutableSet.of("a", original);
    DummyB dummyBInternal = DummyB.builder().strVal(original).strValIgnored(original).build();
    String[][] strArrArr = new String[][] {new String[] {"a", original, "b"}, new String[] {"c", original, original}};
    DummyB dummyB = DummyB.builder()
                        .pairs(pairs)
                        .map(map)
                        .set(set)
                        .intVal(5)
                        .strVal(original)
                        .strValIgnored(original)
                        .obj(dummyBInternal)
                        .strArrArr(strArrArr)
                        .build();
    dummyBInternal.setObj(dummyB);

    DummyC dummyC1 = DummyC.builder()
                         .dummyC(DummyField.createExpressionField("random"))
                         .strVal1(DummyField.createValueField(original))
                         .strVal2(original)
                         .intVal1(DummyField.createExpressionField(originalInt1))
                         .intVal2(15)
                         .build();
    dummyB.setDummyC1(DummyField.createExpressionField(originalObject));

    DummyC dummyC2 = DummyC.builder()
                         .strVal1(DummyField.createValueField(original))
                         .strVal2(original)
                         .intVal1(DummyField.createExpressionField(originalInt2))
                         .intVal2(20)
                         .build();
    dummyB.setDummyC2(dummyC2);

    Map<String, Object> context =
        ImmutableMap.of(original, updated, originalObject, dummyC1, originalInt1, 10, originalInt2, 15);
    ExpressionEvaluatorUtils.updateExpressions(dummyB, DummyFunctor.builder().context(context).build());
    assertThat(dummyB.getDummyC1().isExpression()).isFalse();
    assertThat(dummyB.getDummyC1().getValue()).isEqualTo(dummyC1);
    assertThat(dummyC1.dummyC.isExpression()).isTrue();
    assertThat(dummyC1.strVal1.isExpression()).isFalse();
    assertThat(dummyC1.strVal1.getValue()).isEqualTo(updated);
    assertThat(dummyC1.strVal2).isEqualTo(updated);
    assertThat(dummyC1.intVal1.isExpression()).isFalse();
    assertThat(dummyC1.intVal1.getValue()).isEqualTo(10);
    assertThat(dummyC1.intVal2).isEqualTo(15);
    assertThat(dummyB.getDummyC2()).isEqualTo(dummyC2);
    assertThat(dummyC2.strVal1.isExpression()).isFalse();
    assertThat(dummyC2.strVal1.getValue()).isEqualTo(updated);
    assertThat(dummyC2.strVal2).isEqualTo(updated);
    assertThat(dummyC2.intVal1.isExpression()).isFalse();
    assertThat(dummyC2.intVal1.getValue()).isEqualTo(15);
    assertThat(dummyC2.intVal2).isEqualTo(20);
    assertThat(pairs.get(0).getLeft()).isEqualTo(updated);
    assertThat(pairs.get(0).getRight()).isEqualTo(updated);
    assertThat(pairs.get(1).getLeft()).isEqualTo(updated);
    assertThat(pairs.get(1).getRight()).isEqualTo(updated);
    assertThat(map.get("a")).isEqualTo(updated);
    assertThat(map.get("b")).isEqualTo(2);
    assertThat(set).containsExactlyInAnyOrder("a", updated);
    assertThat(((Pair<Integer, String>) map.get("c")).getLeft()).isEqualTo(1);
    assertThat(((Pair<Integer, String>) map.get("c")).getRight()).isEqualTo(updated);
    assertThat(dummyB.getStrVal()).isEqualTo(updated);
    assertThat(dummyB.getStrValIgnored()).isEqualTo(original);
    assertThat(dummyB.getObj()).isNotNull();
    assertThat(dummyBInternal.getPairs()).isNull();
    assertThat(dummyBInternal.getMap()).isNull();
    assertThat(dummyBInternal.getIntVal()).isEqualTo(0);
    assertThat(dummyBInternal.getStrVal()).isEqualTo(updated);
    assertThat(dummyBInternal.getStrValIgnored()).isEqualTo(original);
    assertThat(dummyBInternal.getObj()).isEqualTo(dummyB);
    assertThat(dummyBInternal.getStrArrArr()).isNull();
    assertThat(strArrArr[0][0]).isEqualTo("a");
    assertThat(strArrArr[0][1]).isEqualTo(updated);
    assertThat(strArrArr[0][2]).isEqualTo("b");
    assertThat(strArrArr[1][0]).isEqualTo("c");
    assertThat(strArrArr[1][1]).isEqualTo(updated);
    assertThat(strArrArr[1][2]).isEqualTo(updated);
  }

  @Test
  @Owner(developers = HINGER)
  @Category(UnitTests.class)
  public void testUpdateMapValues() {
    String originalValue = "<+original_value>";
    String resolvedValue = "updated_value";

    String originalKey = "<+original_key>";
    String resolvedKey = "updated_key";

    // this map has 1 entry with key as expression, 1 entry with value as expression, 1 constant entry
    Map<String, Object> map = new LinkedHashMap<>(ImmutableMap.of(originalKey, "constantValue", "constantKey",
        originalValue, "constA", "constB", "<+non_familiar_key>", "non_familiar_value"));
    DummyB dummyB = DummyB.builder().map(map).build();

    // context knows how to resolve both the key and value
    Map<String, String> context = ImmutableMap.of(originalKey, resolvedKey, originalValue, resolvedValue);

    ExpressionEvaluatorUtils.updateExpressions(dummyB, DummyMapFunctor.builder().context(context).build());

    Map<String, Object> updatedMap = new LinkedHashMap<>(ImmutableMap.of(resolvedKey, "constantValue", "constantKey",
        resolvedValue, "constA", "constB", "<+non_familiar_key>", "non_familiar_value"));
    Iterator<Map.Entry<String, Object>> expectedIterator = map.entrySet().iterator();
    Iterator<Map.Entry<String, Object>> actualIterator = updatedMap.entrySet().iterator();

    while (expectedIterator.hasNext() && actualIterator.hasNext()) {
      Map.Entry<String, Object> expectedEntry = expectedIterator.next();
      Map.Entry<String, Object> actualEntry = actualIterator.next();

      assertEquals(expectedEntry.getKey(), actualEntry.getKey());
      assertEquals(expectedEntry.getValue(), actualEntry.getValue());
    }

    // value is updated to <+updated_value>
    assertThat(map.get("constantKey")).isEqualTo(resolvedValue);

    // key is resolved to <+updated_key>
    assertThat(map.get(resolvedKey)).isEqualTo("constantValue");
    assertThat(map.get("constA")).isEqualTo("constB");

    // unresolvable keys are stored as is
    assertThat(map.get("<+non_familiar_key>")).isEqualTo("non_familiar_value");
  }

  @Data
  @Builder
  private static class DummyB {
    DummyField<DummyC> dummyC1;
    DummyC dummyC2;
    List<Pair<String, String>> pairs;
    Map<String, Object> map;
    Set<String> set;
    int intVal;
    String strVal;
    @NotExpression String strValIgnored;
    Object obj;
    String[][] strArrArr;
  }

  @Value
  @Builder
  private static class DummyC {
    DummyField<DummyC> dummyC;
    DummyField<String> strVal1;
    String strVal2;
    DummyField<Integer> intVal1;
    int intVal2;
  }
}
