/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.datahandler.models;

import io.harness.beans.FeatureFlag;

import dev.morphia.annotations.Id;
import java.util.Objects;
import java.util.Set;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class FeatureFlagBO {
  @Id private String uuid;
  private String name;
  private boolean enabled;
  private boolean obsolete;
  private Set<String> accountIds;
  private long lastUpdatedAt;

  public static FeatureFlagBO fromFeatureFlag(FeatureFlag featureFlag) {
    if (Objects.isNull(featureFlag)) {
      return null;
    }
    return FeatureFlagBO.builder()
        .accountIds(featureFlag.getAccountIds())
        .enabled(featureFlag.isEnabled())
        .name(featureFlag.getName())
        .lastUpdatedAt(featureFlag.getLastUpdatedAt())
        .uuid(featureFlag.getUuid())
        .obsolete(featureFlag.isObsolete())
        .build();
  }
}
