/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.beans.azure.appservicesettings;

import static io.harness.delegate.beans.azure.appservicesettings.AzureAppServiceSettingConstants.CONNECTION_SETTING_JSON_TYPE;

import io.harness.annotation.RecasterAlias;
import io.harness.azure.model.AzureAppServiceConnectionStringType;
import io.harness.security.encryption.EncryptedRecord;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonTypeName;
import io.swagger.annotations.ApiModel;
import javax.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonTypeName(CONNECTION_SETTING_JSON_TYPE)

@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@FieldDefaults(level = AccessLevel.PRIVATE)
@ApiModel("AzureAppServiceConnectionString")
@RecasterAlias("io.harness.delegate.beans.azure.appservicesettings.AzureAppServiceConnectionStringDTO")
public class AzureAppServiceConnectionStringDTO extends AzureAppServiceSettingDTO {
  @NotNull AzureAppServiceConnectionStringType type;

  @Builder
  public AzureAppServiceConnectionStringDTO(String name, String value, boolean sticky,
      AzureAppServiceConnectionStringType type, EncryptedRecord encryptedRecord, String accountId) {
    super(name, value, sticky, encryptedRecord, accountId);
    this.type = type;
  }
}
