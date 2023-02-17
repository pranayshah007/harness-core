/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.awssam.request;

import static io.harness.expression.Expression.ALLOW_SECRETS;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.beans.logstreaming.CommandUnitsProgress;
import io.harness.delegate.task.awssam.AwsSamCommandType;
import io.harness.delegate.task.awssam.AwsSamInfraConfig;
import io.harness.delegate.task.awssam.AwsSamManifestConfig;
import io.harness.delegate.task.awssam.AwsSamPublishConfig;
import io.harness.expression.Expression;

import lombok.Builder;
import lombok.Value;
import lombok.experimental.NonFinal;

@Value
@Builder
@OwnedBy(HarnessTeam.CDP)
public class AwsSamPublishRequest implements AwsSamCommandRequest {
  String accountId;
  AwsSamCommandType awsSamCommandType;
  String commandName;
  CommandUnitsProgress commandUnitsProgress;
  @NonFinal @Expression(ALLOW_SECRETS) AwsSamManifestConfig awsSamManifestConfig;
  @NonFinal @Expression(ALLOW_SECRETS) AwsSamInfraConfig awsSamInfraConfig;
  @NonFinal @Expression(ALLOW_SECRETS) AwsSamPublishConfig awsSamPublishConfig;
  Integer timeoutIntervalInMin;
  @Expression(ALLOW_SECRETS) String templateFileContent;
  @Expression(ALLOW_SECRETS) String configFileContent;
}
