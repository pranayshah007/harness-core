/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl;

import static io.harness.data.structure.EmptyPredicate.isEmpty;

import io.harness.data.structure.EmptyPredicate;

import java.util.Arrays;
import java.util.stream.Collectors;

public class ShellScriptUtils {
  private static final String COMMENT_OPERATOR = "#";

  public static boolean isNoopScript(String script) {
    return isEmpty(script) || isScriptCommentOrSpaceOnly(script);
  }

  private static boolean isScriptCommentOrSpaceOnly(String script) {
    return Arrays.stream(script.split("\n"))
        .map(String::trim)
        .filter(EmptyPredicate::isNotEmpty)
        .filter(it -> !it.startsWith(COMMENT_OPERATOR))
        .collect(Collectors.toList())
        .isEmpty();
  }
}
