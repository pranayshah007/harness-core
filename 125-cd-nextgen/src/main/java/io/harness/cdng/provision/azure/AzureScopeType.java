/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.provision.azure;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import static com.fasterxml.jackson.annotation.JsonTypeInfo.As.EXTERNAL_PROPERTY;
import static com.fasterxml.jackson.annotation.JsonTypeInfo.Id.NAME;

import io.harness.annotations.dev.OwnedBy;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

@JsonTypeInfo(use = NAME, property = "type", include = EXTERNAL_PROPERTY, visible = true)
@JsonSubTypes({
  @JsonSubTypes.Type(value = AzureResourceGroupSpec.class, name = AzureScopeTypesNames.ResourceGroup)
  , @JsonSubTypes.Type(value = AzureSubscriptionSpec.class, name = AzureScopeTypesNames.Subscription),
      @JsonSubTypes.Type(value = AzureManagementSpec.class, name = AzureScopeTypesNames.ManagementGroup),
      @JsonSubTypes.Type(value = AzureTenantSpec.class, name = AzureScopeTypesNames.Tenant)
})
@OwnedBy(CDP)
public interface AzureScopeType {
  void validateParams();
}
