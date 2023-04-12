/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.deploydetective.mapper.release;

import lombok.experimental.UtilityClass;

@UtilityClass
public class ReleaseVersionUtil {
  public static String stripPatchVersion(String releaseVersion) {
    return releaseVersion.substring(0, releaseVersion.lastIndexOf("."));
  }

  public static String stripMajorMinorVersion(String releaseVersion) {
    return releaseVersion.substring(1 + releaseVersion.lastIndexOf("."));
  }
}
