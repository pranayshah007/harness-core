/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.k8s.model.customObjects;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.rule.OwnerRule.PRATYUSH;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.MockitoAnnotations.initMocks;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.assertj.core.api.Assertions;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

@OwnedBy(CDP)
public class CustomObjectTest extends CategoryTest {
  @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();
  @Mock private CustomObjectFactory customObjectFactory;

  @Before
  public void setup() {
    initMocks(this);
  }

  @Test
  @Owner(developers = PRATYUSH)
  @Category(UnitTests.class)
  public void testPrimitiveCustomObjectGetNode() {
    String value = "value";
    CustomObject customObject = new PrimitiveCustomObject(value).getNode("level1_primitive");
    Assertions.assertThat(customObject).isNull();
  }

  @Test
  @Owner(developers = PRATYUSH)
  @Category(UnitTests.class)
  public void testListCustomObjectGetNode() {
    CustomObject customObject = new ListCustomObject(new ArrayList<>()).getNode("level1_primitive");
    Assertions.assertThat(customObject).isNull();

    customObject = new ListCustomObject(new ArrayList<>()).getNode("level1_primitive");
    Assertions.assertThat(customObject).isNull();

    Mockito.doReturn(new PrimitiveCustomObject("value")).when(customObjectFactory).create(any());
    ListCustomObject listCustomObject = new ListCustomObject(Collections.singletonList("value"));
    listCustomObject.customObjectFactory = customObjectFactory;
    customObject = listCustomObject.getNode("level1_primitive");
    Assertions.assertThat(customObject).isNull();
    Mockito.verify(customObjectFactory, Mockito.times(1)).create(any());
  }

  @Test
  @Owner(developers = PRATYUSH)
  @Category(UnitTests.class)
  public void testMapCustomObjectGetNode() {
    Map<String, Object> dummyMap = new HashMap<>();
    CustomObject customObject = new MapCustomObject(dummyMap).getNode("level1_primitive");
    Assertions.assertThat(customObject).isNull();

    dummyMap.put("level1_primitive", "value");
    ListCustomObject listCustomObject = new ListCustomObject("value");
    Mockito.doReturn(listCustomObject).when(customObjectFactory).create(any());
    MapCustomObject mapCustomObject = new MapCustomObject(dummyMap);
    mapCustomObject.customObjectFactory = customObjectFactory;
    customObject = mapCustomObject.getNode("level1_primitive");
    Assertions.assertThat(customObject).isEqualTo(listCustomObject);
    Mockito.verify(customObjectFactory, Mockito.times(1)).create(any());

    Map<String, Object> testMap = createRandomMap("level1");
    Mockito.doReturn(new PrimitiveCustomObject("value")).when(customObjectFactory).create(any());
    mapCustomObject = new MapCustomObject(testMap);
    mapCustomObject.customObjectFactory = customObjectFactory;
    customObject = mapCustomObject.getNode("primitive");
    Assertions.assertThat(customObject).isEqualTo(null);
    Mockito.verify(customObjectFactory, Mockito.times(4)).create(any());
  }

  private Map<String, Object> createRandomMap(String level) {
    Map<String, Object> randomMap = new HashMap<>();
    randomMap.put(level + "_primitive", "value");
    randomMap.put(level + "_list", Collections.singletonList("value"));
    Map<String, Object> dummyMap = new HashMap<>();
    dummyMap.put("primitive", "value");
    randomMap.put(level + "_map", dummyMap);
    return randomMap;
  }
}
