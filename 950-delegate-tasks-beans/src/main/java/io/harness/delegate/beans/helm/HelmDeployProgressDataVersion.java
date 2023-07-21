/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.beans.helm;

public enum HelmDeployProgressDataVersion {
  V1("v1");

  private String name;

  HelmDeployProgressDataVersion(String name) {
    this.name = name;
  }

  public String getVersionName() {
    return this.name;
  }
}
