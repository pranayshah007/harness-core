/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.resources;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.eraro.ErrorCode.ENCRYPT_DECRYPT_ERROR;
import static io.harness.eraro.ErrorCode.SECRET_MANAGEMENT_ERROR;
import static io.harness.exception.WingsException.USER;
import static io.harness.expression.SecretString.SECRET_MASK;

import static software.wings.beans.CGConstants.GLOBAL_APP_ID;
import static software.wings.beans.CGConstants.GLOBAL_ENV_ID;

import static javax.ws.rs.core.MediaType.APPLICATION_JSON;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.SecretManagerConfig;
import io.harness.beans.SecretText;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.SecretManagementException;
import io.harness.rest.RestResponse;
import io.harness.secretmanagers.SecretManagerConfigService;
import io.harness.security.annotations.InternalApi;
import io.harness.security.annotations.NextGenManagerAuth;

import software.wings.annotation.EncryptableSetting;
import software.wings.beans.SettingAttribute;
import software.wings.beans.ValidationResult;
import software.wings.helpers.ext.mail.SmtpConfig;
import software.wings.service.impl.SettingServiceHelper;
import software.wings.service.impl.SettingValidationService;
import software.wings.service.impl.security.auth.SettingAuthHandler;
import software.wings.service.intfc.SettingsService;
import software.wings.service.intfc.security.SecretManager;
import software.wings.settings.SettingVariableTypes;
import software.wings.settings.validation.SmtpConnectivityValidationAttributes;

import com.codahale.metrics.annotation.ExceptionMetered;
import com.codahale.metrics.annotation.Timed;
import com.google.inject.Inject;
import io.swagger.annotations.Api;
import java.lang.reflect.Field;
import java.util.List;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(PL)
@Api(value = "/ng/settings", hidden = true)
@Path("/ng/settings")
@Consumes(APPLICATION_JSON)
@Produces(APPLICATION_JSON)
@NextGenManagerAuth
@Slf4j
public class SettingResourceNg {
  @Inject private SettingAuthHandler settingAuthHandler;
  @Inject private SettingsService settingsService;
  @Inject private SettingServiceHelper settingServiceHelper;
  @Inject private SettingValidationService settingValidationService;
  @Inject private SecretManagerConfigService secretManagerConfigService;
  @Inject private SecretManager secretManager;
  static final String NG_SMTP_SETTINGS_PREFIX = "ngSmtpConfig-";

  @POST
  @Timed
  @ExceptionMetered
  @InternalApi
  public RestResponse<SettingAttribute> save(@DefaultValue(GLOBAL_APP_ID) @QueryParam("appId") String appId,
      @QueryParam("accountId") String accountId, SettingAttribute variable) {
    RestResponse<SettingAttribute> existingConfig = get(accountId);
    if (existingConfig.getResource() != null) {
      throw new InvalidRequestException(
          "SMTP is already configured for this Account. Each Account can have only one SMTP configuration. Use the UPDATE API call to modify this configuration.");
    }
    SmtpConfig smtpConfig = (SmtpConfig) variable.getValue();
    SecretManagerConfig secretManagerConfig = secretManagerConfigService.getDefaultSecretManager(accountId);
    if (isNotEmpty(smtpConfig.getPassword())) {
      SecretText secretText = new SecretText();
      String password = new String(smtpConfig.getPassword());
      secretText.setValue(password);
      String secretName = variable.getName() + "-" + accountId + "-SmtpSecret";
      secretName = secretName.replaceAll("[^a-zA-Z0-9_-]", "-");
      secretText.setName(secretName);
      secretText.setScopedToAccount(true);
      secretText.setKmsId(secretManagerConfig.getUuid());
      try {
        smtpConfig.setPassword(secretManager.saveSecretText(accountId, secretText, true).toCharArray());
      } catch (SecretManagementException ex) {
        log.error(" Exception received while setting SMTP password {}", ex);
        throw new SecretManagementException(SECRET_MANAGEMENT_ERROR, ex.getMessage(), USER);
      }
    }
    variable.setValue(smtpConfig);
    settingAuthHandler.authorize(variable, appId);
    SettingAttribute savedSettingAttribute = settingsService.saveWithPruning(variable, appId, accountId);
    settingServiceHelper.updateSettingAttributeBeforeResponse(savedSettingAttribute, false);
    maskEncryptedFields((EncryptableSetting) savedSettingAttribute.getValue());
    return new RestResponse<>(savedSettingAttribute);
  }

  @POST
  @Path("validateName")
  @Timed
  @ExceptionMetered
  @InternalApi
  public RestResponse<Boolean> validate(@QueryParam("name") String name, @QueryParam("accountId") String accountId,
      @DefaultValue(GLOBAL_APP_ID) @QueryParam("appId") String appId,
      @DefaultValue(GLOBAL_ENV_ID) @QueryParam("envId") String envId) {
    return new RestResponse<>(settingValidationService.validateConnectorName(name, accountId, appId, envId));
  }

  @POST
  @Path("validate-connectivity")
  @Timed
  @ExceptionMetered
  @InternalApi
  public RestResponse<ValidationResult> validateConnectivity(@QueryParam("attrId") String attrId,
      @DefaultValue(GLOBAL_APP_ID) @QueryParam("appId") String appId, @QueryParam("accountId") String accountId,
      @QueryParam("to") String to, @QueryParam("subject") String subject, @QueryParam("body") String body) {
    SettingAttribute existingAttribute = settingsService.get(appId, attrId);
    SmtpConnectivityValidationAttributes smtpAttr =
        SmtpConnectivityValidationAttributes.builder().to(to).body(body).subject(subject).build();
    existingAttribute.setValidationAttributes(smtpAttr);
    settingAuthHandler.authorize(existingAttribute, appId);
    ValidationResult response = settingsService.validateConnectivityWithPruning(existingAttribute, appId, accountId);
    return new RestResponse<>(response);
  }

  @GET
  @Timed
  @ExceptionMetered
  @InternalApi
  public RestResponse<SettingAttribute> get(@QueryParam("accountId") String accountId) {
    List<SettingAttribute> smtpAttribute =
        settingsService.getGlobalSettingAttributesByType(accountId, SettingVariableTypes.SMTP.name());
    SettingAttribute existingConfigWithNgSmtpSettingsPrefix = null;
    for (SettingAttribute settingAttribute : smtpAttribute) {
      String smtpAttributeName = settingAttribute.getName();
      if (smtpAttributeName != null && smtpAttributeName.length() >= 13
          && smtpAttributeName.startsWith(NG_SMTP_SETTINGS_PREFIX)) {
        existingConfigWithNgSmtpSettingsPrefix = settingAttribute;
        break;
      }
    }
    if (existingConfigWithNgSmtpSettingsPrefix != null) {
      settingServiceHelper.updateSettingAttributeBeforeResponse(existingConfigWithNgSmtpSettingsPrefix, true);
      maskEncryptedFields((EncryptableSetting) existingConfigWithNgSmtpSettingsPrefix.getValue());
    }
    return new RestResponse<>(existingConfigWithNgSmtpSettingsPrefix);
  }

  @PUT
  @Path("{attrId}")
  @Timed
  @ExceptionMetered
  @InternalApi
  public RestResponse<SettingAttribute> update(@PathParam("attrId") String attrId,
      @DefaultValue(GLOBAL_APP_ID) @QueryParam("appId") String appId, SettingAttribute variable) {
    SettingAttribute existingAttribute = settingsService.get(appId, attrId);
    if (existingAttribute == null) {
      throw new InvalidRequestException(
          "SMTP configuration with this ID does not exist. Enter a valid Configuration ID.");
    }
    SmtpConfig existingSmtpConfig = (SmtpConfig) existingAttribute.getValue();
    SmtpConfig smtpConfig = (SmtpConfig) variable.getValue();
    SecretManagerConfig secretManagerConfig =
        secretManagerConfigService.getDefaultSecretManager(variable.getAccountId());
    if (isNotEmpty(smtpConfig.getPassword())) {
      SecretText secretText = new SecretText();
      String password = (smtpConfig.getPassword() == null) ? "" : new String(smtpConfig.getPassword());
      secretText.setValue(password);
      String secretName = variable.getName() + "-" + variable.getAccountId() + "-SmtpSecret";
      secretText.setName(secretName);
      secretText.setScopedToAccount(true);
      secretText.setKmsId(secretManagerConfig.getUuid());
      String secretId = existingSmtpConfig.getEncryptedPassword();
      Boolean isSuccessful = secretManager.updateSecretText(variable.getAccountId(), secretId, secretText, true);
      smtpConfig.setPassword(secretId.toCharArray());
    }
    variable.setValue(smtpConfig);
    settingAuthHandler.authorize(variable, appId);
    SettingAttribute updatedSettingAttribute = settingsService.updateWithSettingFields(variable, attrId, appId);
    settingServiceHelper.updateSettingAttributeBeforeResponse(updatedSettingAttribute, false);
    maskEncryptedFields((EncryptableSetting) updatedSettingAttribute.getValue());
    return new RestResponse<>(updatedSettingAttribute);
  }

  @DELETE
  @Path("{attrId}")
  @Timed
  @ExceptionMetered
  @InternalApi
  public RestResponse<Boolean> delete(
      @PathParam("attrId") String attrId, @DefaultValue(GLOBAL_APP_ID) @QueryParam("appId") String appId) {
    SettingAttribute existingAttribute = settingsService.get(appId, attrId);
    if (existingAttribute == null) {
      throw new InvalidRequestException(
          "SMTP configuration with this ID does not exist. Enter a valid Configuration ID.");
    }
    SmtpConfig existingSmtpConfig = (SmtpConfig) existingAttribute.getValue();
    String storedSecretId = existingSmtpConfig.getEncryptedPassword();
    settingAuthHandler.authorize(appId, attrId);
    settingsService.delete(appId, attrId);
    if (isNotEmpty(storedSecretId)) {
      secretManager.deleteSecret(existingAttribute.getAccountId(), storedSecretId, null, true);
    }
    return new RestResponse<>(true);
  }

  public void maskEncryptedFields(EncryptableSetting object) {
    List<Field> encryptedFields = object.getEncryptedFields();
    try {
      for (Field f : encryptedFields) {
        f.setAccessible(true);
        f.set(object, SECRET_MASK.toCharArray());
      }
    } catch (IllegalAccessException e) {
      throw new SecretManagementException(ENCRYPT_DECRYPT_ERROR, e, USER);
    }
  }
}
