/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.beans.connector.customsecretmanager.outcome;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.SecretManagerDescriptionConstants;
import io.harness.annotations.dev.OwnedBy;
import io.harness.connector.DelegateSelectable;
import io.harness.delegate.beans.connector.ConnectorConfigOutcomeDTO;
import io.harness.delegate.beans.connector.customsecretmanager.TemplateLinkConfigForCustomSecretManager;
import io.harness.encryption.SecretRefData;
import io.harness.secret.SecretReference;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Set;
import javax.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;

@OwnedBy(CDP)
@Data
@Builder
@EqualsAndHashCode(callSuper = true)
@JsonIgnoreProperties(ignoreUnknown = true)
public class CustomSecretManagerConnectorOutcomeDTO extends ConnectorConfigOutcomeDTO implements DelegateSelectable {
  Set<String> delegateSelectors;
  @Builder.Default Boolean onDelegate = Boolean.FALSE;
  @Schema(description = SecretManagerDescriptionConstants.DEFAULT) private boolean isDefault;
  @Schema private boolean harnessManaged;

  @SecretReference
  @Schema(description = SecretManagerDescriptionConstants.CUSTOM_AUTH_TOKEN)
  private SecretRefData connectorRef;

  private String host;
  private String workingDirectory;
  @NotNull private TemplateLinkConfigForCustomSecretManager template;
}
