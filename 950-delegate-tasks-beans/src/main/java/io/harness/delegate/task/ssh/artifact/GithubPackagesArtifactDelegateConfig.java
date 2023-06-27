/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.ssh.artifact;

import static io.harness.expression.Expression.ALLOW_SECRETS;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.expression.Expression;
import io.harness.reflection.ExpressionReflectionUtils.NestedAnnotationResolver;
import io.harness.security.encryption.EncryptedDataDetail;

import java.util.List;
import java.util.Map;
import lombok.Builder;
import lombok.Value;
import lombok.experimental.NonFinal;

@Value
@Builder
@OwnedBy(HarnessTeam.CDP)
public class GithubPackagesArtifactDelegateConfig implements SshWinRmArtifactDelegateConfig, NestedAnnotationResolver {
  @NonFinal @Expression(ALLOW_SECRETS) String artifactUrl;
  String identifier;
  ConnectorInfoDTO connectorDTO;
  List<EncryptedDataDetail> encryptedDataDetails;
  Map<String, String> metadata;
  String packageType;
  String packageName;
  String version;
  String image;

  @Override
  public SshWinRmArtifactType getArtifactType() {
    return SshWinRmArtifactType.GITHUB_PACKAGE;
  }

  @Override
  public String getArtifactPath() {
    return artifactUrl;
  }
}
