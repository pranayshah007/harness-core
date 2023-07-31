/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.k8s.model.customObjects;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.rule.OwnerRule.PRATYUSH;

import static org.mockito.MockitoAnnotations.initMocks;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;

import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.assertj.core.api.Assertions;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

@OwnedBy(CDP)
public class CustomObjectFactoryTest extends CategoryTest {
  @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();
  @InjectMocks @Inject private CustomObjectFactory customObjectFactory;

  @Before
  public void setup() {
    initMocks(this);
  }

  @Test
  @Owner(developers = PRATYUSH)
  @Category(UnitTests.class)
  public void testCreateMapCustomObject() {
    Map<String, String> dummyMap = new HashMap<>();
    CustomObject customObject = customObjectFactory.create(dummyMap);
    Assertions.assertThat(customObject).isInstanceOf(MapCustomObject.class);
  }

  @Test
  @Owner(developers = PRATYUSH)
  @Category(UnitTests.class)
  public void testCreateListCustomObject() {
    List<String> dummyList = new ArrayList<>();
    CustomObject customObject = customObjectFactory.create(dummyList);
    Assertions.assertThat(customObject).isInstanceOf(ListCustomObject.class);
  }

  @Test
  @Owner(developers = PRATYUSH)
  @Category(UnitTests.class)
  public void testCreatePrimitiveCustomObject() {
    String dummyString = "dummy";
    CustomObject customObject = customObjectFactory.create(dummyString);
    Assertions.assertThat(customObject).isInstanceOf(PrimitiveCustomObject.class);
  }
}
