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

import software.wings.beans.AwsConfig;
import software.wings.beans.Tag;
import software.wings.sm.ContextElement;

import java.util.List;
import lombok.Builder;
import lombok.Value;

/**
 * The type Aws lambda context element.
 */
@Value
@Builder
@OwnedBy(CDP)
@TargetModule(_957_CG_BEANS)
public class AwsLambdaContextElement implements ContextElement {
  public static final String AWS_LAMBDA_REQUEST_PARAM = "AWS_LAMBDA_REQUEST_PARAM";

  @Value
  @Builder
  public static class FunctionMeta {
    private String functionName;
    private String functionArn;
    private String version;
  }

  private AwsConfig awsConfig;
  private String region;
  private List<FunctionMeta> functionArns;
  private List<String> aliases;
  private List<Tag> tags;

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
    return AWS_LAMBDA_REQUEST_PARAM;
  }

  @Override
  public ContextElement cloneMin() {
    return this;
  }
}
