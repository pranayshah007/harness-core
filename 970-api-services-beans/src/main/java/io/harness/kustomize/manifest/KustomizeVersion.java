/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.kustomize.manifest;

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
public class KustomizeVersion {
  private static final Pattern KUSTOMIZE_VERSION_REGEX =
      Pattern.compile("kustomize/v(\\d+).(\\d+).(\\d+)", CASE_INSENSITIVE);

  @Nullable Integer patch;
  @Nullable Integer minor;
  @Nullable Integer major;

  public static KustomizeVersion createDefault() {
    return KustomizeVersion.builder().build();
  }

  public static KustomizeVersion getKustomizeVersion(String output) {
    Matcher match = KUSTOMIZE_VERSION_REGEX.matcher(output);
    if (!match.find()) {
      return createDefault();
    }

    return KustomizeVersion.builder()
        .major(Integer.valueOf(match.group(1)))
        .minor(Integer.valueOf(match.group(2)))
        .patch(Integer.valueOf(match.group(3)))
        .build();
  }

  public boolean isGreaterThan(KustomizeVersion version) {
    int diff = this.major - version.getMajor();
    if (diff == 0) {
      diff = this.minor - version.getMinor();
      if (diff == 0) {
        diff = this.patch - version.getPatch();
      }
    }
    return diff > 0;
  }
}
