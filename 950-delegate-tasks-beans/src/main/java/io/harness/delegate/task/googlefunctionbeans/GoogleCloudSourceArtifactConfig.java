/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.googlefunctionbeans;

import static io.harness.expression.Expression.ALLOW_SECRETS;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.delegate.task.artifacts.googlecloudsource.GoogleCloudSourceFetchType;
import io.harness.expression.Expression;
import io.harness.security.encryption.EncryptedDataDetail;

import java.util.List;
import lombok.Builder;
import lombok.Value;
import lombok.experimental.NonFinal;

@Value
@Builder
@OwnedBy(HarnessTeam.CDP)
public class GoogleCloudSourceArtifactConfig implements GoogleFunctionArtifactConfig {
  @NonFinal @Expression(ALLOW_SECRETS) String project;
  @NonFinal @Expression(ALLOW_SECRETS) String repository;
  @NonFinal @Expression(ALLOW_SECRETS) String sourceDirectory;
  String identifier;
  ConnectorInfoDTO connectorDTO;
  List<EncryptedDataDetail> encryptedDataDetails;
  GoogleCloudSourceFetchType googleCloudSourceFetchType;
  @NonFinal @Expression(ALLOW_SECRETS) String branch;
  @NonFinal @Expression(ALLOW_SECRETS) String tag;
  @NonFinal @Expression(ALLOW_SECRETS) String commitId;
}
