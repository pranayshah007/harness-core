/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.beans.connector.awssecretmanager.outcome;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.SecretManagerDescriptionConstants;
import io.harness.annotations.dev.OwnedBy;
import io.harness.connector.DelegateSelectable;
import io.harness.delegate.beans.connector.ConnectorConfigOutcomeDTO;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Set;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;

@OwnedBy(CDP)
@Data
@Builder
@EqualsAndHashCode(callSuper = true)
@JsonIgnoreProperties(ignoreUnknown = true)
public class AwsSecretManagerOutcomeDTO extends ConnectorConfigOutcomeDTO implements DelegateSelectable {
  @Schema(description = SecretManagerDescriptionConstants.AWS_AUTH_CRED_SM)
  @Valid
  @NotNull
  AwsSecretManagerCredentialOutcomeDTO credential;

  @Schema(description = SecretManagerDescriptionConstants.AWS_REGION_SM) @NotNull private String region;
  @Schema(description = SecretManagerDescriptionConstants.DEFAULT) private boolean isDefault;
  @Schema(description = SecretManagerDescriptionConstants.HARNESS_MANAGED) @JsonIgnore private boolean harnessManaged;

  @Schema(description = SecretManagerDescriptionConstants.AWS_SECRET_NAME_PREFIX) private String secretNamePrefix;
  @Schema(description = SecretManagerDescriptionConstants.DELEGATE_SELECTORS) private Set<String> delegateSelectors;
}
