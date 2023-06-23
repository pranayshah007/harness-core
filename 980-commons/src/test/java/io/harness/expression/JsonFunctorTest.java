/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.expression;

import static io.harness.rule.OwnerRule.ABHINAV_MITTAL;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(HarnessTeam.CDC)
public class JsonFunctorTest extends CategoryTest {
  @Test
  @Owner(developers = ABHINAV_MITTAL)
  @Category(UnitTests.class)
  public void shouldNotResolveObjectsViaJsonSelect() {
    JsonFunctor jsonFunctor = new JsonFunctor(buildContextMap(false));
    assertThat(
        jsonFunctor.select("[0].releaseCommitId", "[{\"releaseCommitId\":\"6a68065\"},{\"releaseCommitId\":123}]"))
        .isEqualTo("6a68065");
    assertThat(
        jsonFunctor.select(".[0].releaseCommitId", "[{\"releaseCommitId\":\"6a68065\"},{\"releaseCommitId\":123}]"))
        .isNull();
    assertThat(
        jsonFunctor.select("[1].releaseCommitId", "[{\"releaseCommitId\":\"6a68065\"},{\"releaseCommitId\":123}]"))
        .isNull();
    assertThat(
        jsonFunctor.select(".[1].releaseCommitId", "[{\"releaseCommitId\":\"6a68065\"},{\"releaseCommitId\":123}]"))
        .isNull();
  }

  @Test
  @Owner(developers = ABHINAV_MITTAL)
  @Category(UnitTests.class)
  public void shouldResolveObjectsViaJsonSelect() {
    JsonFunctor jsonFunctor = new JsonFunctor(buildContextMap(true));
    List<Object> values = new LinkedList<>();
    values.add("6a68065");
    values.add(123);
    assertThat(
        jsonFunctor.select("[0].releaseCommitId", "[{\"releaseCommitId\":\"6a68065\"},{\"releaseCommitId\":123}]"))
        .isEqualTo("6a68065");
    assertThat(
        jsonFunctor.select(".[0].releaseCommitId", "[{\"releaseCommitId\":\"6a68065\"},{\"releaseCommitId\":123}]"))
        .isEqualTo(values);
    assertThat(
        jsonFunctor.select("[1].releaseCommitId", "[{\"releaseCommitId\":\"6a68065\"},{\"releaseCommitId\":123}]"))
        .isEqualTo(123);
    assertThat(
        jsonFunctor.select(".[1].releaseCommitId", "[{\"releaseCommitId\":\"6a68065\"},{\"releaseCommitId\":123}]"))
        .isEqualTo(values);
  }

  private Map<String, Object> buildContextMap(boolean resolveObjectsViaJSONSelect) {
    if (resolveObjectsViaJSONSelect) {
      Map<String, Object> contextMap = new HashMap<>();
      contextMap.put("resolveObjectsViaJSONSelect", String.valueOf(true));
      return contextMap;
    }
    return null;
  }
}
