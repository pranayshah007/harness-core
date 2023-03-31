/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.utils;

import io.harness.data.structure.EmptyPredicate;

import javax.servlet.http.HttpServletRequest;
import lombok.NonNull;
import lombok.experimental.UtilityClass;

@UtilityClass
public class HttpRequestUtils {
  public static final String X_API_KEY = "X-Api-Key";

  public static boolean hasApiKey(@NonNull HttpServletRequest request) {
    return EmptyPredicate.isNotEmpty(request.getHeader(X_API_KEY));
  }
}
