/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.terraform.beans;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import static java.util.regex.Pattern.CASE_INSENSITIVE;

import io.harness.annotations.dev.OwnedBy;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.annotation.Nullable;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
@OwnedBy(CDP)
public class TerraformVersion {
  public static final Pattern TF_VERSION_REGEX = Pattern.compile("v(\\d+).(\\d+).(\\d+)", CASE_INSENSITIVE);

  @Nullable Integer patch;
  @Nullable Integer minor;
  @Nullable Integer major;

  public boolean minVersion(int major, int minor) {
    int diff = this.major != null ? this.major - major : 0;
    if (diff == 0) {
      return this.minor == null || this.minor >= minor;
    }

    return diff > 0;
  }

  public static TerraformVersion create(String output) {
    Matcher match = TF_VERSION_REGEX.matcher(output);
    if (!match.find()) {
      return createDefault();
    }

    return TerraformVersion.builder()
        .major(Integer.valueOf(match.group(1)))
        .minor(Integer.valueOf(match.group(2)))
        .patch(Integer.valueOf(match.group(3)))
        .build();
  }

  public static TerraformVersion create(int major, int minor, int patch) {
    return TerraformVersion.builder().major(major).minor(minor).patch(patch).build();
  }

  public static TerraformVersion createDefault() {
    return TerraformVersion.builder().build();
  }
}
