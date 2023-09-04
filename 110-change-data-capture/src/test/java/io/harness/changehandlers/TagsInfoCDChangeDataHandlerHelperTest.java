/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.changehandlers;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;
import io.harness.rule.OwnerRule;

import com.mongodb.BasicDBList;
import com.mongodb.BasicDBObject;
import java.util.HashMap;
import java.util.Map;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class TagsInfoCDChangeDataHandlerHelperTest extends CategoryTest {
  @Test
  @Owner(developers = OwnerRule.RISHABH)
  @Category(UnitTests.class)
  public void testInsert() {
    BasicDBList basicDBList = new BasicDBList();
    Map<String, String> map = new HashMap<>();
    map.put("key", "hel<+>?\"\":lo");
    map.put("value", "test");
    Map<String, String> map1 = new HashMap<>();
    map1.put("key", "hello");
    map1.put("value", "hello");
    Map<String, String> map2 = new HashMap<>();
    map2.put("key", "hel<+>?\\\"\":lo");
    map2.put("value", "hello");
    Map<String, String> map3 = new HashMap<>();
    map3.put("key", "hel<+>?\\\"\":lo");
    map3.put("value", "hel<+>?\\\"\":lo");
    basicDBList.add(new BasicDBObject(map));
    basicDBList.add(new BasicDBObject(map1));
    basicDBList.add(new BasicDBObject(map2));
    basicDBList.add(new BasicDBObject(map3));
    BasicDBObject[] tagArray = basicDBList.toArray(new BasicDBObject[basicDBList.size()]);
    String tagString = TagsInfoCDChangeDataHandlerHelper.getTagString(tagArray);
    assertThat(tagString).isEqualTo(
        "{hel<+>?\\\"\\\":lo:test,hello:hello,hel<+>?\\\"\\\":lo:hello,hel<+>?\\\"\\\":lo:hel<+>?\\\"\\\":lo}");
  }

  @Test
  @Owner(developers = OwnerRule.RISHABH)
  @Category(UnitTests.class)
  public void testInsertStageTags() {
    BasicDBList basicDBList = new BasicDBList();
    basicDBList.add("hel<+>?\"\":lo:test");
    basicDBList.add("hello:hello");
    basicDBList.add("hel<+>?\\\"\":lo:hello");
    basicDBList.add("hel<+>?\\\"\":lo:hel<+>?\\\"\":lo");
    String tagString = TagsInfoCDChangeDataHandlerHelper.getStageExecutionTags(basicDBList);
    assertThat(tagString).isEqualTo(
        "{hel<+>?\\\"\\\":lo:test,hello:hello,hel<+>?\\\"\\\":lo:hello,hel<+>?\\\"\\\":lo:hel<+>?\\\"\\\":lo}");
  }
}
