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
import io.harness.delegate.beans.connector.ConnectorConfigDTO;
import io.harness.encryption.SecretRefData;
import io.harness.encryption.SecretReference;
import io.harness.eraro.ErrorCode;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.WingsException;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Collections;
import java.util.LinkedList;
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
  @Builder.Default Boolean onDelegate = Boolean.TRUE;
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
  public void validate() {
    if (onDelegate) {
      boolean throwException = false;
      StringBuilder errorMessageBuilder =
          new StringBuilder("Target machine information should be absent if execution is on delegate. Found ");
      if (host != null) {
        throwException = true;
        errorMessageBuilder.append(String.format("host = %s ", host));
      }
      if (connectorRef != null) {
        throwException = true;
        errorMessageBuilder.append(String.format("connector ref = %s ", connectorRef));
      }
      if (workingDirectory != null) {
        throwException = true;
        errorMessageBuilder.append(String.format("working directory = %s ", workingDirectory));
      }
      if (throwException) {
        throw new InvalidRequestException(
            errorMessageBuilder.toString(), ErrorCode.INVALID_REQUEST, WingsException.USER);
      }
    } else {
      List<String> targetMachineNullFields = new LinkedList<>();
      if (host == null) {
        targetMachineNullFields.add("host");
      }
      if (workingDirectory == null) {
        targetMachineNullFields.add("working directory");
      }
      if (connectorRef == null) {
        targetMachineNullFields.add("connector ref");
      }
      String errorMessage =
          "Target machine information should be present if execution is not on delegate. But the following values are absent. "
          + targetMachineNullFields;
      if (targetMachineNullFields.size() > 0) {
        throw new InvalidRequestException(errorMessage, ErrorCode.INVALID_REQUEST, WingsException.USER);
      }
    }
  }
}