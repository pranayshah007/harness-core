/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

/**
 *
 */

package io.harness.serializer;

import static io.harness.rule.OwnerRule.GEORGE;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;

import java.io.IOException;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.XPathExpressionException;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.xml.sax.SAXException;

/**
 * The Class XmlUtilsTest.
 *
 * @author Rishi
 */
public class XmlUtilsTest extends CategoryTest {
  /**
   * Should get xpath.
   *
   * @throws XPathExpressionException     the x path expression exception
   * @throws ParserConfigurationException the parser configuration exception
   * @throws SAXException                 the SAX exception
   * @throws IOException                  Signals that an I/O exception has occurred.
   */
  @Test
  @Owner(developers = GEORGE)
  @Category(UnitTests.class)
  public void shouldGetXpath()
      throws XPathExpressionException, ParserConfigurationException, SAXException, IOException {
    String content = "<widgets><widget><manufacturer>abc</manufacturer><dimensions/></widget></widgets>";
    String expression = "//widget/manufacturer/text()";
    String text = XmlUtils.xpath(content, expression);

    assertThat(text).isEqualTo("abc");
  }
}
