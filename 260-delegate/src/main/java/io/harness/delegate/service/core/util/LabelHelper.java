/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.service.core.util;

import java.util.Locale;
import java.util.regex.Pattern;
import lombok.experimental.UtilityClass;

@UtilityClass
public class LabelHelper {
  private static final Pattern RESOURCE_NAME_NORMALIZER = Pattern.compile("_");

  public static final String HARNESS_NAME_LABEL = "harness.io/name";
  public static final String HARNESS_TASK_GROUP_LABEL = "harness.io/task-group";

  public static String normalizeLabel(final String labelValue) {
    return RESOURCE_NAME_NORMALIZER.matcher(labelValue.trim().toLowerCase(Locale.ROOT)).replaceAll("-");
  }

  public static String getTaskGroupSelector(final String taskGroupId) {
    return HARNESS_TASK_GROUP_LABEL + "=" + normalizeLabel(taskGroupId);
  }
}
