/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.graphql.datafetcher.secrets;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.delegate.task.winrm.AuthenticationScheme.KERBEROS;
import static io.harness.delegate.task.winrm.AuthenticationScheme.NTLM;

import static software.wings.beans.CGConstants.GLOBAL_APP_ID;
import static software.wings.settings.SettingVariableTypes.WINRM_CONNECTION_ATTRIBUTES;

import static org.apache.commons.lang3.StringUtils.isBlank;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.delegate.task.winrm.AuthenticationScheme;
import io.harness.exception.InvalidRequestException;
import io.harness.ng.core.dto.secrets.WinRmCommandParameter;

import software.wings.beans.SettingAttribute;
import software.wings.beans.WinRmConnectionAttributes;
import software.wings.graphql.schema.type.secrets.QLAuthScheme;
import software.wings.graphql.schema.type.secrets.QLSecretType;
import software.wings.graphql.schema.type.secrets.QLUsageScope;
import software.wings.graphql.schema.type.secrets.QLWinRMCredential;
import software.wings.graphql.schema.type.secrets.QLWinRMCredentialInput;
import software.wings.graphql.schema.type.secrets.QLWinRMCredentialUpdate;
import software.wings.graphql.schema.type.secrets.QLWinRmCommandParameter;
import software.wings.service.intfc.SettingsService;
import software.wings.service.intfc.security.SecretManager;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.ArrayList;
import java.util.List;
import javax.validation.constraints.NotNull;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

@Slf4j
@Singleton
@TargetModule(HarnessModule._380_CG_GRAPHQL)
@OwnedBy(CDP)
public class WinRMCredentialController {
  @Inject SettingsService settingService;
  @Inject SecretManager secretManager;
  @Inject UsageScopeController usageScopeController;

  public QLWinRMCredential populateWinRMCredential(@NotNull SettingAttribute settingAttribute) {
    WinRmConnectionAttributes winRmConnectionAttributes = (WinRmConnectionAttributes) settingAttribute.getValue();

    return QLWinRMCredential.builder()
        .id(settingAttribute.getUuid())
        .name(settingAttribute.getName())
        .authenticationScheme(QLAuthScheme.valueOf(winRmConnectionAttributes.getAuthenticationScheme().toString()))
        .keyTabFilePath(winRmConnectionAttributes.getAuthenticationScheme() == KERBEROS
                ? winRmConnectionAttributes.getKeyTabFilePath()
                : null)
        .secretType(QLSecretType.WINRM_CREDENTIAL)
        .userName(winRmConnectionAttributes.getUsername())
        .domain(winRmConnectionAttributes.getDomain())
        .useSSL(winRmConnectionAttributes.isUseSSL())
        .skipCertCheck(winRmConnectionAttributes.isSkipCertChecks())
        .port(winRmConnectionAttributes.getPort())
        .usageScope(usageScopeController.populateUsageScope(settingAttribute.getUsageRestrictions()))
        .parameters(populateQLCommandParameters(winRmConnectionAttributes.getParameters()))
        .build();
  }

  private List<QLWinRmCommandParameter> populateQLCommandParameters(List<WinRmCommandParameter> commandParameters) {
    if (commandParameters == null || isEmpty(commandParameters)) {
      return null;
    }

    List<QLWinRmCommandParameter> parameters = new ArrayList<>();
    for (WinRmCommandParameter parameter : commandParameters) {
      QLWinRmCommandParameter commandParameter =
          new QLWinRmCommandParameter(parameter.getParameter(), parameter.getValue());
      parameters.add(commandParameter);
    }
    return parameters;
  }

  private List<WinRmCommandParameter> populateCommandParameters(List<QLWinRmCommandParameter> commandParameters) {
    if (commandParameters == null || isEmpty(commandParameters)) {
      return null;
    }

    List<WinRmCommandParameter> parameters = new ArrayList<>();
    for (QLWinRmCommandParameter parameter : commandParameters) {
      WinRmCommandParameter commandParameter =
          new WinRmCommandParameter(parameter.getParameter(), parameter.getValue());
      parameters.add(commandParameter);
    }
    return parameters;
  }

  private void validateSettingAttribute(QLWinRMCredentialInput winRMCredentialInput, String accountId) {
    if (isBlank(winRMCredentialInput.getUserName())) {
      throw new InvalidRequestException("The username cannot be blank for the winRM credential input");
    }

    if (isBlank(winRMCredentialInput.getPasswordSecretId())
        || secretManager.getSecretById(accountId, winRMCredentialInput.getPasswordSecretId()) == null) {
      throw new InvalidRequestException("The password secret id is invalid for the winRM credential input");
    }

    if (isBlank(winRMCredentialInput.getName())) {
      throw new InvalidRequestException("The name of the winRM credential cannot be blank");
    }
  }

  public SettingAttribute createSettingAttribute(
      @NotNull QLWinRMCredentialInput winRMCredentialInput, String accountId) {
    validateSettingAttribute(winRMCredentialInput, accountId);
    AuthenticationScheme authenticationScheme = NTLM;
    boolean skipCertChecks = true;
    boolean useSSL = true;
    if (winRMCredentialInput.getSkipCertCheck() != null) {
      skipCertChecks = winRMCredentialInput.getSkipCertCheck().booleanValue();
    }
    if (winRMCredentialInput.getUseSSL() != null) {
      useSSL = winRMCredentialInput.getUseSSL().booleanValue();
    }
    String domain = "";
    if (winRMCredentialInput.getDomain() != null) {
      domain = winRMCredentialInput.getDomain();
    }
    int port = 5986;
    if (winRMCredentialInput.getPort() != null) {
      port = winRMCredentialInput.getPort();
    }
    List<WinRmCommandParameter> commandParameters = populateCommandParameters(winRMCredentialInput.getParameters());
    WinRmConnectionAttributes settingValue = WinRmConnectionAttributes.builder()
                                                 .username(winRMCredentialInput.getUserName())
                                                 .password(winRMCredentialInput.getPasswordSecretId().toCharArray())
                                                 .authenticationScheme(authenticationScheme)
                                                 .port(port)
                                                 .skipCertChecks(skipCertChecks)
                                                 .accountId(accountId)
                                                 .useSSL(useSSL)
                                                 .domain(domain)
                                                 .parameters(commandParameters)
                                                 .build();
    settingValue.setSettingType(WINRM_CONNECTION_ATTRIBUTES);
    return SettingAttribute.Builder.aSettingAttribute()
        .withCategory(SettingAttribute.SettingCategory.SETTING)
        .withValue(settingValue)
        .withAccountId(accountId)
        .withName(winRMCredentialInput.getName())
        .withUsageRestrictions(
            usageScopeController.populateUsageRestrictions(winRMCredentialInput.getUsageScope(), accountId))
        .build();
  }

  public SettingAttribute updateWinRMCredential(QLWinRMCredentialUpdate updateInput, String id, String accountId) {
    SettingAttribute existingWinRMCredential = settingService.getByAccount(accountId, id);
    if (existingWinRMCredential == null
        || existingWinRMCredential.getValue().getSettingType() != WINRM_CONNECTION_ATTRIBUTES) {
      throw new InvalidRequestException(String.format("No winRM credential exists with the id %s", id));
    }
    if (updateInput.getName().isPresent()) {
      String name = updateInput.getName().getValue().map(StringUtils::strip).orElse(null);
      if (isBlank(name)) {
        throw new InvalidRequestException("Cannot set the winRM credential name as null");
      }
      existingWinRMCredential.setName(name);
    }

    WinRmConnectionAttributes settingValue = (WinRmConnectionAttributes) existingWinRMCredential.getValue();

    if (updateInput.getDomain().isPresent()) {
      String domain = updateInput.getDomain().getValue().map(StringUtils::strip).orElse(null);
      settingValue.setDomain(domain);
    }

    if (updateInput.getUserName().isPresent()) {
      String userName = updateInput.getUserName().getValue().map(StringUtils::strip).orElse(null);
      if (isBlank(userName)) {
        throw new InvalidRequestException("Cannot set the username in winRM Credential as null");
      }
      settingValue.setUsername(userName);
    }

    if (updateInput.getPasswordSecretId().isPresent()) {
      String password = updateInput.getPasswordSecretId().getValue().map(StringUtils::strip).orElse(null);
      if (isBlank(password) || secretManager.getSecretById(accountId, password) == null) {
        throw new InvalidRequestException("Invalid password in winRM Credential");
      }
      settingValue.setPassword(password.toCharArray());
    }

    if (updateInput.getUseSSL().isPresent()) {
      boolean useSSL = updateInput.getUseSSL().getValue().orElse(true);
      settingValue.setUseSSL(useSSL);
    }

    if (updateInput.getSkipCertCheck().isPresent()) {
      boolean skipCertCheck = updateInput.getSkipCertCheck().getValue().orElse(true);
      settingValue.setSkipCertChecks(skipCertCheck);
    }

    if (updateInput.getPort().isPresent()) {
      Integer port = updateInput.getPort().getValue().orElse(5986);
      settingValue.setPort(port.intValue());
    }

    if (updateInput.getUsageScope().isPresent()) {
      QLUsageScope usageScope = updateInput.getUsageScope().getValue().orElse(null);
      existingWinRMCredential.setUsageRestrictions(
          usageScopeController.populateUsageRestrictions(usageScope, accountId));
    }

    if (updateInput.getParameters().isPresent()) {
      List<WinRmCommandParameter> commandParameters =
          populateCommandParameters(updateInput.getParameters().getValue().orElse(null));
      settingValue.setParameters(commandParameters);
    }

    existingWinRMCredential.setValue(settingValue);
    return settingService.updateWithSettingFields(
        existingWinRMCredential, existingWinRMCredential.getUuid(), GLOBAL_APP_ID);
  }
}
