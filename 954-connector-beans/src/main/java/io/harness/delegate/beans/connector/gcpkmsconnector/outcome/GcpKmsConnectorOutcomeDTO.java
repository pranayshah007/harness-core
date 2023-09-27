/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.beans.connector.gcpkmsconnector.outcome;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.SecretManagerDescriptionConstants;
import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.OwnedBy;
import io.harness.connector.DelegateSelectable;
import io.harness.delegate.beans.connector.ConnectorConfigOutcomeDTO;
import io.harness.delegate.beans.connector.ConnectorType;
import io.harness.encryption.SecretRefData;
import io.harness.secret.SecretReference;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Set;
import javax.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.hibernate.validator.constraints.NotBlank;

@OwnedBy(CDP)
@Data
@Builder
@EqualsAndHashCode(callSuper = true)
@JsonIgnoreProperties(ignoreUnknown = true)
@RecasterAlias("io.harness.delegate.beans.connector.gcpkmsconnector.outcome.GcpKmsConnectorOutcomeDTO")
public class GcpKmsConnectorOutcomeDTO extends ConnectorConfigOutcomeDTO implements DelegateSelectable {
  @NotNull
  @NotBlank
  @Schema(description = SecretManagerDescriptionConstants.GCP_KMS_PROJECT_ID)
  private String projectId;

  @NotNull @NotBlank @Schema(description = SecretManagerDescriptionConstants.GCP_KMS_REGION) private String region;

  @NotNull @NotBlank @Schema(description = SecretManagerDescriptionConstants.GCP_KEYRING) private String keyRing;

  @NotNull @NotBlank @Schema(description = SecretManagerDescriptionConstants.GCP_KEYNAME) private String keyName;

  @Schema(description = SecretManagerDescriptionConstants.GCP_CRED_FILE)
  @NotNull
  @SecretReference
  SecretRefData credentials;
  @Schema(description = SecretManagerDescriptionConstants.DEFAULT) private boolean isDefault;

  private boolean harnessManaged;
  @Schema(description = SecretManagerDescriptionConstants.DELEGATE_SELECTORS) private Set<String> delegateSelectors;
  @Builder.Default private ConnectorType connectorType = ConnectorType.GCP_KMS;
}
