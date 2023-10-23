/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.beans.connector.ceazure;

import io.harness.beans.DecryptableEntity;
import io.harness.delegate.beans.connector.CEFeatures;
import io.harness.delegate.beans.connector.ConnectorConfigDTO;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.swagger.annotations.ApiModel;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.FieldDefaults;
import org.hibernate.validator.constraints.NotEmpty;

@Data
@Builder
@EqualsAndHashCode(callSuper = false)
@FieldDefaults(level = AccessLevel.PRIVATE)
@JsonIgnoreProperties(ignoreUnknown = true)
@ApiModel("CEAzureConnector")
@Schema(name = "CEAzureConnector", description = "This contains the cost explorer of Azure ConnectorDisconnectHandler")
public class CEAzureConnectorDTO extends ConnectorConfigDTO {
  @NotEmpty(message = "FeaturesEnabled can't be empty") List<CEFeatures> featuresEnabled;

  @NotNull String tenantId;
  @NotNull String subscriptionId;
  @Valid BillingExportSpecDTO billingExportSpec;

  @Override
  public List<DecryptableEntity> getDecryptableEntities() {
    return null;
  }
}
