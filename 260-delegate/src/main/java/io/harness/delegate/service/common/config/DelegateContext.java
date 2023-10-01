/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.service.common.config;

import lombok.Data;

@Data
public class DelegateContext {
  private final String hostName;
  private final String instanceId;
  private final String connectionId;

  private final boolean ng;
  private final String type;
  private final String name;
  private final String groupName;
  private final String orgIdentifier;
  private final String projectIdentifier;
  private final String groupId;
  private final String tags;
}
