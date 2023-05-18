/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.beans.connector.customsecretmanager;

import io.harness.SecretManagerDescriptionConstants;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.DecryptableEntity;
import io.harness.connector.DelegateSelectable;
import io.harness.connector.utils.TemplateDetails;
import io.harness.delegate.beans.connector.ConnectorConfigDTO;
import io.harness.delegate.beans.connector.ConnectorConfigOutcomeDTO;
import io.harness.delegate.beans.connector.customsecretmanager.outcome.CustomSecretManagerConnectorOutcomeDTO;
import io.harness.encryption.SecretRefData;
import io.harness.secret.SecretReference;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import javax.validation.constraints.NotNull;
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
@ToString(exclude = {"connectorRef"})
@EqualsAndHashCode(callSuper = true)
@JsonIgnoreProperties(ignoreUnknown = true)
@FieldDefaults(level = AccessLevel.PRIVATE)
@ApiModel("CustomSecretManager")
@OwnedBy(HarnessTeam.PL)
@Schema(name = "CustomSecretManager", description = "This contains details of Custom Secret Manager connectors")
public class CustomSecretManagerConnectorDTO extends ConnectorConfigDTO implements DelegateSelectable {
  Set<String> delegateSelectors;
  @Builder.Default Boolean onDelegate = Boolean.FALSE;
  @Schema(description = SecretManagerDescriptionConstants.DEFAULT) private boolean isDefault;
  @Schema @JsonIgnore private boolean harnessManaged;

  @SecretReference
  @ApiModelProperty(dataType = "string")
  @Schema(description = SecretManagerDescriptionConstants.CUSTOM_AUTH_TOKEN)
  private SecretRefData connectorRef;

  private String host;
  private String workingDirectory;
  @NotNull private TemplateLinkConfigForCustomSecretManager template;
  @Override
  public List<DecryptableEntity> getDecryptableEntities() {
    return Collections.singletonList(this);
  }

  @Override
  public List<TemplateDetails> getTemplateDetails() {
    return Collections.singletonList(TemplateDetails.builder()
                                         .templateRef(template.getTemplateRef())
                                         .versionLabel(template.getVersionLabel())
                                         .build());
  }

  @Override
  public ConnectorConfigOutcomeDTO toOutcome() {
    return CustomSecretManagerConnectorOutcomeDTO.builder()
        .delegateSelectors(delegateSelectors)
        .onDelegate(onDelegate)
        .isDefault(isDefault)
        .harnessManaged(harnessManaged)
        .connectorRef(connectorRef)
        .host(host)
        .workingDirectory(workingDirectory)
        .template(template)
        .build();
  }
}