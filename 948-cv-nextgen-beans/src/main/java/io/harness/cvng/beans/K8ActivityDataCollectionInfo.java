/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cvng.beans;

import io.harness.cvng.beans.activity.ActivitySourceDTO;

import lombok.AccessLevel;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.experimental.SuperBuilder;

@Data
@SuperBuilder
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@EqualsAndHashCode(callSuper = true)
public class K8ActivityDataCollectionInfo extends CVDataCollectionInfo {
  @Deprecated ActivitySourceDTO activitySourceDTO;
  String projectIdentifier;
  String orgIdentifier;
  @Deprecated String serviceIdentifier;
  @Deprecated String envIdentifier;
  String monitoredServiceIdentifier;
  String changeSourceIdentifier;
}
