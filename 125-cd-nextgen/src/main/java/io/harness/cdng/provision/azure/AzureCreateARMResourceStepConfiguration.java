/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.provision.azure;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.SwaggerConstants;
import io.harness.cdng.manifest.ManifestStoreType;
import io.harness.cdng.manifest.yaml.GitStoreConfig;
import io.harness.cdng.manifest.yaml.harness.HarnessStore;
import io.harness.cdng.manifest.yaml.storeConfig.StoreConfigWrapper;
import io.harness.exception.InvalidRequestException;
import io.harness.pms.yaml.ParameterField;
import io.harness.pms.yaml.YamlNode;
import io.harness.validation.Validator;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.annotations.ApiModelProperty;
import java.util.Objects;
import javax.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Data;
import lombok.Getter;
import lombok.experimental.FieldDefaults;

@Data
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
@OwnedBy(HarnessTeam.CDP)
@RecasterAlias("io.harness.cdng.provision.azure.AzureCreateARMResourceStepConfiguration")
public class AzureCreateARMResourceStepConfiguration {
  private static final String ARM = "ARM";
  @JsonProperty(YamlNode.UUID_FIELD_NAME)
  @Getter(onMethod_ = { @ApiModelProperty(hidden = true) })
  @ApiModelProperty(hidden = true)
  String uuid;

  @NotNull @ApiModelProperty(dataType = SwaggerConstants.STRING_CLASSPATH) ParameterField<String> connectorRef;
  @NotNull AzureTemplateFile template;

  AzureCreateARMResourceParameterFile parameters;

  @NotNull AzureCreateARMResourceStepScope scope;

  public void validateParams() {
    Validator.notNullCheck("Template file can't be empty", template);
    Validator.notNullCheck("Connector ref can't be empty", connectorRef);
    Validator.notNullCheck("Scope can't be empty", scope);
    isNumberOfFilesValid(template.getStore(), "Number of files in template file should be equal to 1");
    isNumberOfFilesValid(parameters.getStore(), "Number of files in parameters file should be equal to 1");
    scope.getSpec().validateParams();
  }

  public String getType() {
    return ARM;
  }

  public AzureCreateARMResourceStepConfigurationParameters toStepParameters() {
    return AzureCreateARMResourceStepConfigurationParameters.builder()
        .connectorRef(connectorRef)
        .templateFile(template)
        .parameters(parameters)
        .scope(scope)
        .build();
  }

  // Check that the number of parameters files is 1.
  private void isNumberOfFilesValid(StoreConfigWrapper store, String errMsg) {
    if (ManifestStoreType.isInGitSubset(store.getSpec().getKind())) {
      GitStoreConfig gitStoreConfig = (GitStoreConfig) store.getSpec();
      if (gitStoreConfig.getPaths().getValue() == null || gitStoreConfig.getPaths().getValue().size() != 1) {
        throw new InvalidRequestException(errMsg);
      } else if (Objects.equals(store.getSpec().getKind(), ManifestStoreType.HARNESS)) {
        HarnessStore harnessStore = (HarnessStore) store.getSpec();
        if (harnessStore.getFiles().getValue() == null || harnessStore.getFiles().getValue().size() != 1) {
          throw new InvalidRequestException(errMsg);
        } else {
          throw new InvalidRequestException("Unsupported store type");
        }
      }
    }
  }
}
