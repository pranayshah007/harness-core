/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngsettings.services.impl.validators;

import static java.lang.Boolean.parseBoolean;

import io.harness.connector.ConnectorCategory;
import io.harness.connector.ConnectorFilterPropertiesDTO;
import io.harness.connector.ConnectorResourceClient;
import io.harness.connector.ConnectorResponseDTO;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.UnexpectedException;
import io.harness.ngsettings.dto.SettingDTO;
import io.harness.ngsettings.services.SettingValidator;

import com.google.inject.Inject;
import java.util.Arrays;
import java.util.List;

public class DisableBuiltInHarnessSMValidator implements SettingValidator {
  @Inject ConnectorResourceClient connectorResourceClient;

  @Override
  public void validate(String accountIdentifier, SettingDTO oldSettingDTO, SettingDTO newSettingDTO) {
    int page = 0;
    int size = 5;

    if (!parseBoolean(newSettingDTO.getValue())) {
      return;
    }
    List<ConnectorResponseDTO> connectorResponseDTOList = null;

    try {
      connectorResponseDTOList = connectorResourceClient
                                     .listConnectors(accountIdentifier, null, null, page, size,
                                         ConnectorFilterPropertiesDTO.builder()
                                             .categories(Arrays.asList(ConnectorCategory.SECRET_MANAGER))
                                             .build(),
                                         false)
                                     .execute()
                                     .body()
                                     .getData()
                                     .getContent();
    } catch (Exception e) {
      throw new UnexpectedException(
          String.format("Exception occurred while fetching connectors for account- %s", accountIdentifier), e);
    }

    if (connectorResponseDTOList.size() < 2) {
      throw new InvalidRequestException(String.format(
          "Cannot disable Harness Built-in Secret Manager as no other secret manager is found in the account %s",
          accountIdentifier));
    }
  }
}
