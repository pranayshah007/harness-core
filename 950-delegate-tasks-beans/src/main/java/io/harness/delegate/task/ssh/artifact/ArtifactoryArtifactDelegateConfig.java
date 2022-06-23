/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.task.ssh.artifact;

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

@Value
@Builder
@OwnedBy(HarnessTeam.CDP)
public class ArtifactoryArtifactDelegateConfig implements SshWinRmArtifactDelegateConfig, NestedAnnotationResolver {
  @NonFinal @Expression(ALLOW_SECRETS) String repositoryName;
  @NonFinal @Expression(ALLOW_SECRETS) String artifactPath;
  @NonFinal @Expression(ALLOW_SECRETS) String artifactDirectory;
  String repositoryFormat; // its value is "generic"
  String identifier;
  ConnectorInfoDTO connectorDTO;
  List<EncryptedDataDetail> encryptedDataDetails;

  @Override
  public SshWinRmArtifactType getArtifactType() {
    return SshWinRmArtifactType.ARTIFACTORY;
  }
}
