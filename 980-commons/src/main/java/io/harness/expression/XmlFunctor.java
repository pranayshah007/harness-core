/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.expression;

import io.harness.serializer.XmlUtils;

import java.io.IOException;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.XPathExpressionException;
import org.xml.sax.SAXException;

public class XmlFunctor implements ExpressionFunctor {
  public Object select(String xpath, String xml)
      throws ParserConfigurationException, SAXException, XPathExpressionException, IOException {
    return XmlUtils.xpath(xml, xpath);
  }
}
