/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cdng.common.beans;

import lombok.experimental.UtilityClass;

@UtilityClass
public class SetupAbstractionKeys {
  public final String accountId = "accountId";
  public final String orgIdentifier = "orgIdentifier";
  public final String projectIdentifier = "projectIdentifier";
  public final String userId = "userId";
  public final String userName = "userName";
  public final String userEmail = "userEmail";
  public final String ng = "ng";
  public final String owner = "owner";
  public final String pipelineId = "pipelineId";
  public final String runSequence = "runSequence";
}
