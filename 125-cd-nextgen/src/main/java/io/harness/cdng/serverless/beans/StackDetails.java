/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.serverless.beans;

import java.util.List;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class StackDetails {
  private final String stackId;
  private final String stackName;
  private String templateBody;
  private final List<Parameter> parameters;
  private String stackPolicyBody;
  private final RollbackConfiguration rollbackConfiguration;
  private final String stackStatus;
  private final String stackStatusReason;
  private final Boolean disableRollback;
  private final List<String> notificationARNs;
  private final List<String> capabilities;
  private final String roleARN;
  private final List<Tag> tags;
}
