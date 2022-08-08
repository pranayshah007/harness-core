/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.graphql.schema.type.secrets;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;

import java.util.List;
import lombok.Builder;
import lombok.Value;
import lombok.experimental.FieldNameConstants;

@Value
@Builder
@FieldNameConstants(innerTypeName = "QLWinRMCredentialsKeys")
@TargetModule(HarnessModule._380_CG_GRAPHQL)
@OwnedBy(CDP)
public class QLWinRMCredential implements QLSecret {
  private String id;
  private String name;
  private QLSecretType secretType;
  private QLAuthScheme authenticationScheme;
  private String userName;
  private Boolean useSSL;
  private String domain;
  private Boolean skipCertCheck;
  private Integer port;
  private QLUsageScope usageScope;
  private String keyTabFilePath;
  private List<QLWinRmCommandParameter> parameters;
}
