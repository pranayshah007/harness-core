/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.releaseradar.beans;

public enum Environment {
  PRE_QA("https://stress.harness.io/{SERVICE}/api/version"),
  QA("https://qa.harness.io/{SERVICE}/api/version"),
  UAT("https://uat.harness.io/{SERVICE}/api/version"),
  PROD1("https://app.harness.io/{SERVICE}/api/version"),
  PROD2("https://app.harness.io/gratis/{SERVICE}/api/version"),
  PROD3("https://app3.harness.io/gratis/{SERVICE}/api/version");

  String versionUrlTemplate;

  Environment(String versionUrlTemplate) {
    this.versionUrlTemplate = versionUrlTemplate;
  }
}
