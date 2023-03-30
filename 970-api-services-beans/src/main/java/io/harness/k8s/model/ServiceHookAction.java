/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.k8s.model;

public enum ServiceHookAction {
  FETCH_FILES("FetchFiles"),
  TEMPLATE_MANIFEST("TemplateManifest"),
  STEADY_STATE_CHECK("SteadyStateCheck");

  private String name;

  ServiceHookAction(String versionName) {
    this.name = versionName;
  }

  /**
   * Gets version name.
   *
   * @return the version name
   */
  public String getActionName() {
    return name;
  }
}
