/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;
import io.harness.rule.OwnerRule;

import org.junit.Test;
import org.junit.experimental.categories.Category;

public class URLDecoderUtilityTest extends CategoryTest {
  static final String ENCODED = "http%3A%2F%2Fsome.domain%2Fprefix%3Apath-var";
  static final String DECODED = "http://some.domain/prefix:path-var";

  @Test
  @Owner(developers = OwnerRule.VITALIE)
  @Category(UnitTests.class)
  public void getDecodedStringTest() {
    String ret = URLDecoderUtility.getDecodedString(ENCODED);
    assertEquals(DECODED, ret);
  }

  @Test
  @Owner(developers = OwnerRule.VITALIE)
  @Category(UnitTests.class)
  public void getEncodedStringTest() {
    String ret = URLDecoderUtility.getEncodedString(DECODED);
    assertEquals(ENCODED, ret);
  }

  @Test
  @Owner(developers = OwnerRule.VITALIE)
  @Category(UnitTests.class)
  public void getDecodedStringIsNullTest() {
    String ret = URLDecoderUtility.getDecodedString(null);
    assertNull(ret);
    ret = URLDecoderUtility.getDecodedString("");
    assertNull(ret);
  }

  @Test
  @Owner(developers = OwnerRule.VITALIE)
  @Category(UnitTests.class)
  public void getEncodedStringIsNullTest() {
    String ret = URLDecoderUtility.getEncodedString(null);
    assertNull(ret);
    ret = URLDecoderUtility.getEncodedString("");
    assertNull(ret);
  }
}
