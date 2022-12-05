/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.graphql.datafetcher.secrets;

import static io.harness.expression.SecretString.SECRET_MASK;

import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.TargetModule;
import io.harness.beans.EncryptedData;
import io.harness.beans.SecretText;
import io.harness.exception.InvalidRequestException;
import io.harness.security.encryption.common.EncryptedDataParams;

import software.wings.graphql.schema.mutation.secrets.input.QLCreateSecretInput;
import software.wings.graphql.schema.type.secrets.QLEncryptedText;
import software.wings.graphql.schema.type.secrets.QLEncryptedTextInput;
import software.wings.graphql.schema.type.secrets.QLEncryptedTextUpdate;
import software.wings.graphql.schema.type.secrets.QLSecretType;
import software.wings.graphql.schema.type.secrets.QLUsageScope;
import software.wings.security.UsageRestrictions;
import software.wings.service.intfc.security.SecretManager;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.Set;
import javax.validation.constraints.NotNull;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

@Slf4j
@Singleton
@TargetModule(HarnessModule._380_CG_GRAPHQL)
public class EncryptedTextController {
  @Inject SecretManager secretManager;
  @Inject UsageScopeController usageScopeController;

  public QLEncryptedText populateEncryptedText(@NotNull EncryptedData encryptedText) {
    return QLEncryptedText.builder()
        .id(encryptedText.getUuid())
        .secretType(QLSecretType.ENCRYPTED_TEXT)
        .secretManagerId(encryptedText.getKmsId())
        .name(encryptedText.getName())
        .secretReference(encryptedText.getPath())
        .usageScope(usageScopeController.populateUsageScope(encryptedText.getUsageRestrictions()))
        .inheritScopesFromSM(encryptedText.isInheritScopesFromSM())
        .build();
  }

  public String createEncryptedText(QLCreateSecretInput input, String accountId) {
    QLEncryptedTextInput encryptedText = input.getEncryptedText();
    if (encryptedText == null) {
      throw new InvalidRequestException("No encrypted text input provided in the request");
    }

    String secretMangerId = encryptedText.getSecretManagerId();
    String secretName = encryptedText.getName();
    if (isBlank(secretName)) {
      throw new InvalidRequestException("The name of the secret can not be blank");
    }

    String secretValue = encryptedText.getValue();
    String path = encryptedText.getSecretReference();
    Set<EncryptedDataParams> secretParameters = encryptedText.getParameters();

    int isSecretValueSet = isNotBlank(secretValue) ? 1 : 0;
    int isSecretPathSet = isNotBlank(path) ? 1 : 0;
    int isSecretParametersSet = secretParameters != null ? 1 : 0;

    if (isSecretValueSet + isSecretPathSet + isSecretParametersSet != 1) {
      throw new InvalidRequestException(
          "Exactly ONE out of secret value, secret reference or secret parameters is to be passed");
    }

    SecretText secretText =
        SecretText.builder()
            .value(secretValue)
            .path(path)
            .name(secretName)
            .parameters(secretParameters)
            .kmsId(secretMangerId)
            .usageRestrictions(usageScopeController.populateUsageRestrictions(encryptedText.getUsageScope(), accountId))
            .scopedToAccount(encryptedText.isScopedToAccount())
            .inheritScopesFromSM(encryptedText.isInheritScopesFromSM())
            .build();

    return secretManager.saveSecretText(accountId, secretText, true);
  }

  public void updateEncryptedText(QLEncryptedTextUpdate encryptedTextUpdate, String encryptedTextId, String accountId) {
    if (encryptedTextUpdate == null) {
      throw new InvalidRequestException(
          "No encrypted text input provided with the request with secretType ENCRYPTED_TEXT");
    }

    EncryptedData exitingEncryptedData = secretManager.getSecretById(accountId, encryptedTextId);
    if (exitingEncryptedData == null) {
      throw new InvalidRequestException(String.format("No encrypted text exists with the id %s", encryptedTextId));
    }
    String name = exitingEncryptedData.getName();
    if (encryptedTextUpdate.getName().isPresent()) {
      name = encryptedTextUpdate.getName().getValue().map(StringUtils::strip).orElse(null);
      if (isBlank(name)) {
        throw new InvalidRequestException("Cannot set the value of encrypted text name as blank");
      }
    }

    // Updating the secret value
    if (encryptedTextUpdate.getValue().isPresent() && encryptedTextUpdate.getSecretReference().isPresent()) {
      throw new InvalidRequestException("Cannot update both value and secret reference for the encrypted text secret");
    }

    String secretReference = exitingEncryptedData.getPath();
    // If we do not want to change the value variable, then its value will be SECRET_MASK if value is already set
    String value = secretReference == null ? SECRET_MASK : null;

    // Updating the value
    if (encryptedTextUpdate.getValue().isPresent()) {
      value = encryptedTextUpdate.getValue().getValue().orElse(null);
      secretReference = null;
      if (isBlank(value)) {
        throw new InvalidRequestException("Cannot set the value of encrypted text value as blank");
      }
    }

    // Updating the path
    if (encryptedTextUpdate.getSecretReference().isPresent()) {
      secretReference = encryptedTextUpdate.getSecretReference().getValue().orElse(null);
      value = null;
      if (isBlank(secretReference)) {
        throw new InvalidRequestException("Cannot set the value of encrypted text reference as blank");
      }
    }

    // Updating the usage Restrictions
    UsageRestrictions usageRestrictions = exitingEncryptedData.getUsageRestrictions();
    if (encryptedTextUpdate.getUsageScope().isPresent()) {
      QLUsageScope usageScopeUpdate = encryptedTextUpdate.getUsageScope().getValue().orElse(null);
      usageRestrictions = usageScopeController.populateUsageRestrictions(usageScopeUpdate, accountId);
    }

    boolean scopedToAccount = exitingEncryptedData.isScopedToAccount();
    if (encryptedTextUpdate.getScopedToAccount() != null && encryptedTextUpdate.getScopedToAccount().isPresent()) {
      scopedToAccount =
          encryptedTextUpdate.getScopedToAccount().getValue().orElse(exitingEncryptedData.isScopedToAccount());
    }

    boolean inheritScopesFromSM = exitingEncryptedData.isInheritScopesFromSM();
    if (encryptedTextUpdate.getInheritScopesFromSM() != null
        && encryptedTextUpdate.getInheritScopesFromSM().isPresent()) {
      inheritScopesFromSM =
          encryptedTextUpdate.getInheritScopesFromSM().getValue().orElse(exitingEncryptedData.isScopedToAccount());
    }

    SecretText secretText = SecretText.builder()
                                .value(value)
                                .path(secretReference)
                                .name(name)
                                .usageRestrictions(usageRestrictions)
                                .scopedToAccount(scopedToAccount)
                                .inheritScopesFromSM(inheritScopesFromSM)
                                .build();
    secretManager.updateSecretText(accountId, encryptedTextId, secretText, true);
  }
}
