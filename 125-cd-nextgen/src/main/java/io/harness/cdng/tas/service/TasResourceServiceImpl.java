/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.tas.service;

import static io.harness.delegate.task.pcf.request.CfDataFetchActionType.FETCH_ORG;
import static io.harness.delegate.task.pcf.request.CfDataFetchActionType.FETCH_SPACE;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.ProductModule;
import io.harness.cdng.tas.TasEntityHelper;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.delegate.beans.connector.tasconnector.TasConnectorDTO;
import io.harness.delegate.task.pcf.CfCommandTypeNG;
import io.harness.delegate.task.pcf.request.CfInfraMappingDataRequestNG;
import io.harness.delegate.task.pcf.response.CfInfraMappingDataResponseNG;
import io.harness.delegate.task.pcf.response.TasInfraConfig;
import io.harness.ng.core.BaseNGAccess;
import io.harness.security.encryption.EncryptedDataDetail;

import software.wings.beans.TaskType;

import com.google.inject.Inject;
import java.util.List;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_PCF})
public class TasResourceServiceImpl implements TasResourceService {
  @Inject TasEntityHelper tasEntityHelper;

  @Override
  public List<String> listOrganizations(
      String connectorRef, String accountIdentifier, String orgIdentifier, String projectIdentifier) {
    ConnectorInfoDTO connectorInfoDTO =
        tasEntityHelper.getConnectorInfoDTO(connectorRef, accountIdentifier, orgIdentifier, projectIdentifier);
    TasConnectorDTO tasConnectorDTO = (TasConnectorDTO) connectorInfoDTO.getConnectorConfig();
    BaseNGAccess baseNGAccess = tasEntityHelper.getBaseNGAccess(accountIdentifier, orgIdentifier, projectIdentifier);
    List<EncryptedDataDetail> encryptionDetails =
        tasEntityHelper.getEncryptionDataDetails(connectorInfoDTO, baseNGAccess);
    TasInfraConfig tasInfraConfig =
        TasInfraConfig.builder().tasConnectorDTO(tasConnectorDTO).encryptionDataDetails(encryptionDetails).build();
    CfInfraMappingDataRequestNG taskParamas = CfInfraMappingDataRequestNG.builder()
                                                  .accountId(accountIdentifier)
                                                  .actionType(FETCH_ORG)
                                                  .timeoutIntervalInMin(2)
                                                  .cfCommandTypeNG(CfCommandTypeNG.DATA_FETCH)
                                                  .tasInfraConfig(tasInfraConfig)
                                                  .build();
    CfInfraMappingDataResponseNG delegateResponse = (CfInfraMappingDataResponseNG) tasEntityHelper.executeSyncTask(
        taskParamas, baseNGAccess, TaskType.TAS_DATA_FETCH);
    return delegateResponse.getCfInfraMappingDataResult().getOrganizations();
  }

  @Override
  public List<String> listSpaces(String connectorRef, String accountIdentifier, String orgIdentifier,
      String projectIdentifier, String organization) {
    ConnectorInfoDTO connectorInfoDTO =
        tasEntityHelper.getConnectorInfoDTO(connectorRef, accountIdentifier, orgIdentifier, projectIdentifier);
    TasConnectorDTO tasConnectorDTO = (TasConnectorDTO) connectorInfoDTO.getConnectorConfig();
    BaseNGAccess baseNGAccess = tasEntityHelper.getBaseNGAccess(accountIdentifier, orgIdentifier, projectIdentifier);
    List<EncryptedDataDetail> encryptionDetails =
        tasEntityHelper.getEncryptionDataDetails(connectorInfoDTO, baseNGAccess);
    TasInfraConfig tasInfraConfig = TasInfraConfig.builder()
                                        .organization(organization)
                                        .tasConnectorDTO(tasConnectorDTO)
                                        .encryptionDataDetails(encryptionDetails)
                                        .build();
    CfInfraMappingDataRequestNG taskParamas = CfInfraMappingDataRequestNG.builder()
                                                  .accountId(accountIdentifier)
                                                  .actionType(FETCH_SPACE)
                                                  .timeoutIntervalInMin(2)
                                                  .cfCommandTypeNG(CfCommandTypeNG.DATA_FETCH)
                                                  .tasInfraConfig(tasInfraConfig)
                                                  .build();
    CfInfraMappingDataResponseNG delegateResponse = (CfInfraMappingDataResponseNG) tasEntityHelper.executeSyncTask(
        taskParamas, baseNGAccess, TaskType.TAS_DATA_FETCH);
    return delegateResponse.getCfInfraMappingDataResult().getSpaces();
  }
}
