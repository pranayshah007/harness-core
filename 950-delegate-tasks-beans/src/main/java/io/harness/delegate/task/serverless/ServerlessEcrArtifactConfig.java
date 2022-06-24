/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.serverless;

import static io.harness.expression.Expression.ALLOW_SECRETS;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.expression.Expression;
import io.harness.expression.ExpressionReflectionUtils.NestedAnnotationResolver;
import io.harness.security.encryption.EncryptedDataDetail;

import java.util.List;
import lombok.Builder;
import lombok.Value;
import lombok.experimental.NonFinal;

@OwnedBy(HarnessTeam.CDP)
@Value
@Builder
public class ServerlessEcrArtifactConfig implements ServerlessArtifactConfig, NestedAnnotationResolver {
  @NonFinal @Expression(ALLOW_SECRETS) String imagePath;
  @NonFinal @Expression(ALLOW_SECRETS) String tag;
  @NonFinal @Expression(ALLOW_SECRETS) String region;
  @NonFinal @Expression(ALLOW_SECRETS) String image;
  String type;
  String identifier;
  ConnectorInfoDTO connectorDTO;
  boolean primaryArtifact;
  List<EncryptedDataDetail> encryptedDataDetails;

  @Override
  public ServerlessArtifactType getServerlessArtifactType() {
    return ServerlessArtifactType.ECR;
  }
}
