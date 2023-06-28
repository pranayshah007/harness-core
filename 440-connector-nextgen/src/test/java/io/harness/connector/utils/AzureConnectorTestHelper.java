/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.connector.utils;

import io.harness.connector.ConnectorDTO;
import io.harness.connector.entities.embedded.ceazure.BillingExportDetails;
import io.harness.connector.entities.embedded.ceazure.CEAzureConfig;
import io.harness.delegate.beans.connector.CEFeatures;
import io.harness.delegate.beans.connector.ConnectorType;
import io.harness.delegate.beans.connector.ceazure.BillingExportSpecDTO;
import io.harness.delegate.beans.connector.ceazure.CEAzureConnectorDTO;

import com.google.common.collect.ImmutableList;
import java.util.Collections;
import java.util.List;
import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import lombok.experimental.UtilityClass;

@UtilityClass
@FieldDefaults(makeFinal = true, level = AccessLevel.PUBLIC)
public class AzureConnectorTestHelper {
  String SUBSCRIPTION_ID = "subscriptionId";
  String TENANT_ID = "tenantId";
  List<CEFeatures> FEATURES_ENABLED =
      ImmutableList.of(CEFeatures.OPTIMIZATION, CEFeatures.BILLING, CEFeatures.GOVERNANCE);

  String CONTAINER_NAME = "containerName";
  String REPORT_NAME = "reportName";
  String DIRECTORY_NAME = "directoryName";
  String STORAGE_ACCOUNT_NAME = "storageAccountName";

  public ConnectorDTO createConnectorDTO() {
    return CommonTestHelper.createConnectorDTO(ConnectorType.CE_AZURE, createCEAzureConnectorDTO());
  }

  public CEAzureConfig createCEAzureConfig() {
    return CEAzureConfig.builder()
        .subscriptionId(SUBSCRIPTION_ID)
        .tenantId(TENANT_ID)
        .featuresEnabled(FEATURES_ENABLED)
        .billingExportDetails(createBillingExportDetails())
        .build();
  }

  public CEAzureConfig createCEAzureConfigBillingOnly() {
    return CEAzureConfig.builder()
        .subscriptionId(SUBSCRIPTION_ID)
        .tenantId(TENANT_ID)
        .featuresEnabled(Collections.singletonList(CEFeatures.BILLING))
        .billingExportDetails(createBillingExportDetails())
        .build();
  }

  public CEAzureConfig createCEAzureConfigGovernanceOnly() {
    return CEAzureConfig.builder()
        .subscriptionId(SUBSCRIPTION_ID)
        .tenantId(TENANT_ID)
        .featuresEnabled(Collections.singletonList(CEFeatures.GOVERNANCE))
        .build();
  }

  public CEAzureConfig createCEAzureConfigOptimizationOnly() {
    return CEAzureConfig.builder()
        .subscriptionId(SUBSCRIPTION_ID)
        .tenantId(TENANT_ID)
        .featuresEnabled(Collections.singletonList(CEFeatures.OPTIMIZATION))
        .build();
  }

  public BillingExportSpecDTO createBillingExportSpecDTO() {
    return BillingExportSpecDTO.builder()
        .storageAccountName(STORAGE_ACCOUNT_NAME)
        .directoryName(DIRECTORY_NAME)
        .containerName(CONTAINER_NAME)
        .reportName(REPORT_NAME)
        .subscriptionId(SUBSCRIPTION_ID)
        .build();
  }

  public BillingExportDetails createBillingExportDetails() {
    return BillingExportDetails.builder()
        .storageAccountName(STORAGE_ACCOUNT_NAME)
        .containerName(CONTAINER_NAME)
        .directoryName(DIRECTORY_NAME)
        .reportName(REPORT_NAME)
        .subscriptionId(SUBSCRIPTION_ID)
        .build();
  }

  public CEAzureConnectorDTO createCEAzureConnectorDTO() {
    return CEAzureConnectorDTO.builder()
        .featuresEnabled(FEATURES_ENABLED)
        .subscriptionId(SUBSCRIPTION_ID)
        .tenantId(TENANT_ID)
        .billingExportSpec(createBillingExportSpecDTO())
        .build();
  }

  public CEAzureConnectorDTO createCEAzureConnectorDTOBillingOnly() {
    return CEAzureConnectorDTO.builder()
        .featuresEnabled(Collections.singletonList(CEFeatures.BILLING))
        .subscriptionId(SUBSCRIPTION_ID)
        .tenantId(TENANT_ID)
        .billingExportSpec(createBillingExportSpecDTO())
        .build();
  }

  public CEAzureConnectorDTO createCEAzureConnectorDTOGovernanceOnly() {
    return CEAzureConnectorDTO.builder()
        .featuresEnabled(Collections.singletonList(CEFeatures.GOVERNANCE))
        .subscriptionId(SUBSCRIPTION_ID)
        .tenantId(TENANT_ID)
        .build();
  }

  public CEAzureConnectorDTO createCEAzureConnectorDTOOptimizationOnly() {
    return CEAzureConnectorDTO.builder()
        .featuresEnabled(Collections.singletonList(CEFeatures.OPTIMIZATION))
        .subscriptionId(SUBSCRIPTION_ID)
        .tenantId(TENANT_ID)
        .build();
  }
}
