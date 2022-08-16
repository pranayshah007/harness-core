/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.buildcleaner.bazel;

public class LoadStatement implements Comparable {
  String bazelExtension;
  String symbol;

  public LoadStatement(String bazelExtension, String symbol) {
    this.bazelExtension = bazelExtension;
    this.symbol = symbol;
  }

  public String toString() {
    StringBuilder response = new StringBuilder();
    return response.append("load(\"").append(bazelExtension).append("\", \"").append(symbol).append("\")").toString();
  }

  @Override
  public int compareTo(Object o) {
    return o.toString().compareTo(toString());
  }
}
