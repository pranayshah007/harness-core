/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.googlefunction;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import static java.lang.String.format;

import io.harness.annotations.dev.OwnedBy;
import io.harness.exception.NestedExceptionUtils;
import io.harness.serializer.YamlUtils;

import lombok.experimental.UtilityClass;

@UtilityClass
@OwnedBy(CDP)
public class GoogleFunctionUtils {
  private static final YamlUtils yamlUtils = new YamlUtils();

  public <T> T parseYamlToObjectSchema(String yaml, Class<T> tClass, String schema) {
    T object;
    try {
      object = yamlUtils.read(yaml, tClass);
    } catch (Exception e) {
      throw NestedExceptionUtils.hintWithExplanationException(
          format("Please check yaml configured matches schema %s", schema),
          format("Error while parsing yaml %s. Its expected to be matching %s schema. Please check Harness "
                  + "documentation https://docs.harness.io for more details",
              yaml, schema),
          e);
    }
    return object;
  }
}
