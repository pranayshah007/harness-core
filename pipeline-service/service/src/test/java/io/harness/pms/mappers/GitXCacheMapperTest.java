/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.mappers;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.rule.OwnerRule.ADITHYA;

import static junit.framework.TestCase.assertFalse;
import static junit.framework.TestCase.assertTrue;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.pms.pipeline.mappers.GitXCacheMapper;
import io.harness.rule.Owner;

import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(PIPELINE)
public class GitXCacheMapperTest extends CategoryTest {
  @Test
  @Owner(developers = ADITHYA)
  @Category(UnitTests.class)
  public void testParseLoadFromCacheHeaderParam() {
    //    when null is passed for string loadFromCache
    assertFalse(GitXCacheMapper.parseLoadFromCacheHeaderParam(null));
    //    when empty is passed for string loadFromCache
    assertFalse(GitXCacheMapper.parseLoadFromCacheHeaderParam(""));
    //    when true is passed for string loadFromCache
    assertTrue(GitXCacheMapper.parseLoadFromCacheHeaderParam("true"));
    //    when false is passed for string loadFromCache
    assertFalse(GitXCacheMapper.parseLoadFromCacheHeaderParam("false"));
    //    when junk value is passed for string loadFromCache
    assertFalse(GitXCacheMapper.parseLoadFromCacheHeaderParam("abcs"));
  }
}
