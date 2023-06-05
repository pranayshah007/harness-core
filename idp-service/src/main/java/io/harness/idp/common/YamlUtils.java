/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.common;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.exception.UnexpectedException;
import io.harness.utils.YamlPipelineUtils;

import java.io.IOException;
import lombok.experimental.UtilityClass;

@UtilityClass
@OwnedBy(HarnessTeam.IDP)
public class YamlUtils {
  public static String writeObjectAsYaml(Object obj) {
    return YamlPipelineUtils.writeYamlString(obj);
  }

  public static <T> T read(String value, Class<T> cls) {
    try {
      return YamlPipelineUtils.read(value, cls);
    } catch (IOException e) {
      throw new UnexpectedException("Error reading the content", e);
    }
  }
}
