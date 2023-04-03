/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.artifacts.gar.service;

import static io.harness.exception.WingsException.USER;

import io.harness.exception.InvalidArtifactServerException;
import io.harness.exception.NestedExceptionUtils;

import lombok.experimental.UtilityClass;
import retrofit2.Response;

@UtilityClass
public class GARUtils {
  private static final String RESPONSE_NULL = "Response Is Null";
  public boolean checkIfResponseNull(Response<?> response) {
    if (response == null) {
      throw NestedExceptionUtils.hintWithExplanationException(RESPONSE_NULL,
          "Please Check Whether Artifact exists or not", new InvalidArtifactServerException(RESPONSE_NULL, USER));
    }
    return false;
  }

  public boolean isSHA(String s) {
    if (s.indexOf(':') == -1) {
      return false;
    }
    return true;
  }
}
