/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.distribution.constraint;

import javax.validation.constraints.NotNull;
import lombok.experimental.UtilityClass;

@UtilityClass
public class ConsumerContextUtils {
  public static boolean hasContext(@NotNull Consumer consumer) {
    return consumer.getContext() != null;
  }

  public static String getReleaseEntityType(@NotNull Consumer consumer) {
    return hasContext(consumer) ? (String) consumer.getContext().get("releaseEntityType") : null;
  }

  public static String getReleaseEntityId(@NotNull Consumer consumer) {
    return hasContext(consumer) ? (String) consumer.getContext().get("releaseEntityId") : null;
  }
}
