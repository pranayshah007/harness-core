/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.pms.yaml;

import io.harness.exception.InvalidYamlVersionException;

public enum YamlVersion {
  V0(0, null),
  V1(1, null);

  final int intVersion;
  final String suffix;

  YamlVersion(int intVersion, String suffix) {
    this.intVersion = intVersion;
    this.suffix = suffix;
  }

  /**
   * This method takes a string and return valid yaml version is not able to convert throws an exception
   * Valid Yaml versions are of the format
   * x.y
   *
   * x -> int
   * y -> string
   *
   * Few example of valid versions are
   * 1
   * 2.beta1
   * 2.rc
   * 2.0
   * 2
   *
   * @param versionString
   * @return YamlVersion
   */
  public static YamlVersion fromString(String versionString) {
    if (!versionString.isEmpty()
        && (versionString.charAt(0) == '.' || versionString.charAt(versionString.length() - 1) == '.')) {
      throw new InvalidYamlVersionException(String.format("%s is invalid yaml version format", versionString));
    }

    if (!versionString.contains(".")) {
      return YamlVersion.valueOf(String.format("V%s", versionString));
    } else {
      String[] split = versionString.split("\\.");
      if (split.length != 2) {
        throw new InvalidYamlVersionException(String.format("%s is invalid yaml version format", versionString));
      }
      return YamlVersion.valueOf(String.format("V%s%s", split[0], split[1].toUpperCase()));
    }
  }
}