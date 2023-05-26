/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.sanitizer;

import org.apache.commons.text.StringEscapeUtils;
import org.owasp.html.HtmlPolicyBuilder;
import org.owasp.html.PolicyFactory;

public class HtmlInputSanitizer implements InputSanitizer {
  private PolicyFactory htmlPolicy;

  public HtmlInputSanitizer() {
    this.htmlPolicy = new HtmlPolicyBuilder().toFactory();
  }

  public String sanitizeInput(String input) {
    return StringEscapeUtils.unescapeHtml4(this.htmlPolicy.sanitize(input));
  }
}
