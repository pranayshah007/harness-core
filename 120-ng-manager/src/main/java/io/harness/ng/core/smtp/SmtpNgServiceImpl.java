/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.smtp;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.remote.client.CGRestUtils.getResponse;

import static software.wings.beans.CGConstants.GLOBAL_APP_ID;
import static software.wings.beans.CGConstants.GLOBAL_ENV_ID;

import io.harness.annotations.dev.OwnedBy;
import io.harness.exception.InvalidRequestException;
import io.harness.ng.core.dto.NgSmtpDTO;
import io.harness.ng.core.dto.ValidationResultDTO;
import io.harness.ng.core.mapper.NgSmtpDTOMapper;
import io.harness.ng.core.mapper.ValidationResultDTOMapper;

import software.wings.beans.SettingAttribute;
import software.wings.beans.ValidationResult;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(PL)
@Slf4j
@Singleton
public class SmtpNgServiceImpl implements SmtpNgService {
  private final NgSMTPSettingsHttpClient ngSMTPSettingsHttpClient;

  @Inject
  public SmtpNgServiceImpl(NgSMTPSettingsHttpClient ngSMTPSettingsHttpClient) {
    this.ngSMTPSettingsHttpClient = ngSMTPSettingsHttpClient;
  }

  @Override
  public NgSmtpDTO saveSmtpSettings(NgSmtpDTO variable) throws IOException {
    SettingAttribute settingAttribute = NgSmtpDTOMapper.getSettingAttributeFromNgSmtpDTO(variable);
    SettingAttribute response = getResponse(
        ngSMTPSettingsHttpClient.saveSmtpSettings(GLOBAL_APP_ID, variable.getAccountId(), settingAttribute));
    return NgSmtpDTOMapper.getDTOFromSettingAttribute(response);
  }

  @Override
  public ValidationResultDTO validateSmtpSettings(String name, String accountId) throws IOException {
    Boolean response =
        getResponse(ngSMTPSettingsHttpClient.validateSmtpSettings(name, accountId, GLOBAL_APP_ID, GLOBAL_ENV_ID));
    ValidationResultDTO resultDTO = ValidationResultDTO.builder().valid(response).build();
    if (!response) {
      resultDTO.setErrorMessage("There already exists a ConnectorDisconnectHandler with this name. Please try a different one.");
    }
    return resultDTO;
  }

  @Override
  public NgSmtpDTO updateSmtpSettings(NgSmtpDTO variable) throws IOException {
    if (variable.getUuid().equals("")) {
      throw new InvalidRequestException(
          "A valid UUID is required to update the SMTP configuration. To get the UUID of the existing configuration,"
          + " please use GET API call whose response includes the UUID of the existing configuration.");
    }
    SettingAttribute settingAttribute = NgSmtpDTOMapper.getSettingAttributeFromNgSmtpDTO(variable);
    SettingAttribute response =
        getResponse(ngSMTPSettingsHttpClient.updateSmtpSettings(variable.getUuid(), GLOBAL_APP_ID, settingAttribute));
    return NgSmtpDTOMapper.getDTOFromSettingAttribute(response);
  }

  @Override
  public ValidationResultDTO validateConnectivitySmtpSettings(
      String identifier, String accountId, String to, String subject, String body) throws IOException {
    ValidationResult response = getResponse(ngSMTPSettingsHttpClient.validateConnectivitySmtpSettings(
        identifier, GLOBAL_APP_ID, accountId, to, subject, body));
    return ValidationResultDTOMapper.getDTOFromValidationResult(response);
  }

  @Override
  public Boolean deleteSmtpSettings(String id) throws IOException {
    return getResponse(ngSMTPSettingsHttpClient.deleteSmtpSettings(id, GLOBAL_APP_ID));
  }

  @Override
  public NgSmtpDTO getSmtpSettings(String accountId) throws IOException {
    SettingAttribute response = getResponse(ngSMTPSettingsHttpClient.getSmtpSettings(accountId));
    if (response == null) {
      log.info("Smtp is not configured. Please create a new config");
      return null;
    }
    return NgSmtpDTOMapper.getDTOFromSettingAttribute(response);
  }
}
