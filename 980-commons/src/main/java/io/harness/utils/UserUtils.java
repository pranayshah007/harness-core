/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.utils;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.exception.WingsException.USER;

import static org.apache.commons.lang3.StringUtils.isBlank;

import io.harness.annotations.dev.OwnedBy;
import io.harness.exception.InvalidRequestException;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.experimental.UtilityClass;

@OwnedBy(PL)
@UtilityClass
public class UserUtils {
  private static final Pattern NAME_PATTERN = Pattern.compile("^[^:<>=\\/]{0,255}");

  public void validateUserName(String name) {
    if (isBlank(name)) {
      throw new InvalidRequestException("Name cannot be empty", USER);
    }

    Matcher matcher = NAME_PATTERN.matcher(name);
    if (!matcher.matches()) {
      throw new InvalidRequestException(
          "Name is not valid. It should not be more than 256 characters long and should not contain :, <, >, =, /",
          USER);
    }
  }
}
