/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.beans.connector.customseceretmanager;

import io.harness.SecretManagerDescriptionConstants;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.DecryptableEntity;
import io.harness.connector.DelegateSelectable;
import io.harness.connector.ManagerExecutable;
import io.harness.delegate.beans.connector.ConnectorConfigDTO;
import io.harness.encryption.SecretRefData;
import io.harness.encryption.SecretReference;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.FieldDefaults;
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@JsonIgnoreProperties(ignoreUnknown = true)
@ToString(exclude = {"connectorRef"})
@FieldDefaults(level = AccessLevel.PRIVATE)
@ApiModel("CustomSecretManager")
@OwnedBy(HarnessTeam.DX)
@Schema(name = "CustomSecretManager", description = "This contains details of Custom Secret Manager connectors")
public class CustomSecretManagerConnectorDTO
    extends ConnectorConfigDTO implements DelegateSelectable, ManagerExecutable {
  Set<String> delegateSelectors;
  @JsonProperty("onDelegate") @Builder.Default Boolean executeOnDelegate = true;
  @Schema(description = SecretManagerDescriptionConstants.DEFAULT) private boolean isDefault = false;
  @Schema @JsonIgnore private boolean harnessManaged;

  @SecretReference
  @ApiModelProperty(dataType = "string")
  @Schema(description = SecretManagerDescriptionConstants.CUSTOM_AUTH_TOKEN)
  private SecretRefData connectorRef;

  private String host;
  private String workingDirectory;
  private TemplateLinkConfig template;
  @Override
  public List<DecryptableEntity> getDecryptableEntities() {
    return Collections.singletonList(this);
  }
}