/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.beans.connector.gcpkmsconnector;

import static io.harness.annotations.dev.HarnessTeam.PL;

import io.harness.SecretManagerDescriptionConstants;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.DecryptableEntity;
import io.harness.connector.DelegateSelectable;
import io.harness.delegate.beans.connector.ConnectorConfigDTO;
import io.harness.delegate.beans.connector.ConnectorConfigOutcomeDTO;
import io.harness.delegate.beans.connector.gcpkmsconnector.outcome.GcpKmsConnectorOutcomeDTO;
import io.harness.encryption.SecretRefData;
import io.harness.secret.SecretReference;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.annotations.ApiModelProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import javax.validation.constraints.NotNull;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.hibernate.validator.constraints.NotBlank;

@OwnedBy(PL)
@Getter
@Setter
@NoArgsConstructor
@ToString(exclude = {"credentials"})
@EqualsAndHashCode(callSuper = true)
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(name = "GcpKmsConnector", description = "This contains GCP KMS SecretManager configuration.")
public class GcpKmsConnectorDTO extends ConnectorConfigDTO implements DelegateSelectable {
  @NotNull
  @NotBlank
  @Schema(description = SecretManagerDescriptionConstants.GCP_KMS_PROJECT_ID)
  private String projectId;

  @NotNull @NotBlank @Schema(description = SecretManagerDescriptionConstants.GCP_KMS_REGION) private String region;

  @NotNull @NotBlank @Schema(description = SecretManagerDescriptionConstants.GCP_KEYRING) private String keyRing;

  @NotNull @NotBlank @Schema(description = SecretManagerDescriptionConstants.GCP_KEYNAME) private String keyName;

  @Schema(description = SecretManagerDescriptionConstants.GCP_CRED_FILE)
  @ApiModelProperty(dataType = "string")
  @NotNull
  @SecretReference
  SecretRefData credentials;
  @Schema(description = SecretManagerDescriptionConstants.DEFAULT) private boolean isDefault;
  @JsonIgnore private boolean harnessManaged;
  @Schema(description = SecretManagerDescriptionConstants.DELEGATE_SELECTORS) private Set<String> delegateSelectors;

  @Builder
  public GcpKmsConnectorDTO(String projectId, String region, String keyRing, String keyName, SecretRefData credentials,
      boolean isDefault, Set<String> delegateSelectors) {
    this.projectId = projectId;
    this.region = region;
    this.keyRing = keyRing;
    this.keyName = keyName;
    this.credentials = credentials;
    this.isDefault = isDefault;
    this.delegateSelectors = delegateSelectors;
  }

  @Override
  public List<DecryptableEntity> getDecryptableEntities() {
    return Collections.singletonList(this);
  }

  @Override
  public ConnectorConfigOutcomeDTO toOutcome() {
    return GcpKmsConnectorOutcomeDTO.builder()
        .projectId(projectId)
        .region(region)
        .keyRing(keyRing)
        .keyName(keyName)
        .credentials(credentials)
        .isDefault(isDefault)
        .harnessManaged(harnessManaged)
        .delegateSelectors(delegateSelectors)
        .build();
  }
}
