/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.api;

import static io.harness.annotations.dev.HarnessModule._957_CG_BEANS;
import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.context.ContextElementType;

import software.wings.beans.command.CodeDeployParams;
import software.wings.sm.ContextElement;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
@OwnedBy(CDP)
@TargetModule(_957_CG_BEANS)
public class AwsCodeDeployRequestElement implements ContextElement {
  public static final String AWS_CODE_DEPLOY_REQUEST_PARAM = "AWS_CODE_DEPLOY_REQUEST_PARAM";

  private CodeDeployParams codeDeployParams;
  private CodeDeployParams oldCodeDeployParams;

  @Override
  public ContextElementType getElementType() {
    return ContextElementType.PARAM;
  }

  @Override
  public String getUuid() {
    return null;
  }

  @Override
  public String getName() {
    return AWS_CODE_DEPLOY_REQUEST_PARAM;
  }

  @Override
  public ContextElement cloneMin() {
    return this;
  }
}
