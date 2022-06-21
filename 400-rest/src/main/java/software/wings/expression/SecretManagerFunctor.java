/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.expression;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.exception.WingsException.USER;
import static io.harness.security.encryption.EncryptionType.LOCAL;

import static software.wings.beans.ServiceVariableType.ENCRYPTED_TEXT;
import static software.wings.expression.SecretManagerFunctorInterface.obtainConfigFileExpression;
import static software.wings.expression.SecretManagerFunctorInterface.obtainExpression;

import static java.lang.String.format;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.beans.EncryptedData;
import io.harness.beans.FeatureName;
import io.harness.data.encoding.EncodingUtils;
import io.harness.delegate.beans.SecretDetail;
import io.harness.exception.FunctorException;
import io.harness.exception.InvalidRequestException;
import io.harness.expression.ExpressionFunctor;
import io.harness.ff.FeatureFlagService;
import io.harness.security.SimpleEncryption;
import io.harness.security.encryption.EncryptedDataDetail;
import io.harness.security.encryption.EncryptedRecordData;
import io.harness.security.encryption.EncryptionConfig;
import io.harness.security.encryption.EncryptionType;

import software.wings.beans.ServiceVariable;
import software.wings.service.intfc.security.ManagerDecryptionService;
import software.wings.service.intfc.security.SecretManager;

import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import javax.cache.Cache;
import lombok.Builder;
import lombok.Builder.Default;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;

@Value
@Builder
@Slf4j
@OwnedBy(CDC)
@TargetModule(HarnessModule._940_SECRET_MANAGER_CLIENT)
public class SecretManagerFunctor implements ExpressionFunctor, SecretManagerFunctorInterface {
  private SecretManagerMode mode;
  private FeatureFlagService featureFlagService;
  private ManagerDecryptionService managerDecryptionService;
  private SecretManager secretManager;
  private final Cache<String, EncryptedRecordData> secretsCache;
  private String accountId;
  private String appId;
  private String envId;
  private String workflowExecutionId;
  private int expressionFunctorToken;

  @Default private Map<String, String> evaluatedSecrets = new HashMap<>();
  @Default private Map<String, String> evaluatedDelegateSecrets = new HashMap<>();
  @Default private Map<String, EncryptionConfig> encryptionConfigs = new HashMap<>();
  @Default private Map<String, SecretDetail> secretDetails = new HashMap<>();

  @Override
  public Object obtain(String secretName, int token) {
    if (token != expressionFunctorToken) {
      throw new FunctorException("Inappropriate usage of internal functor");
    }
    try {
      return obtainInternal(secretName);
    } catch (Exception ex) {
      throw new FunctorException("Error occurred while evaluating the secret [" + secretName + "]", ex);
    }
  }

  @Override
  public Object obtainConfigFileAsString(String path, String encryptedFileId, int token) {
    if (token != expressionFunctorToken) {
      throw new FunctorException("Inappropriate usage of internal functor");
    }

    String key = format("//text:/%s", path);
    if (evaluatedSecrets.containsKey(key)) {
      return returnConfigFileValue("obtainConfigFileAsString", path, encryptedFileId, evaluatedSecrets.get(key));
    }

    byte[] fileContent = secretManager.getFileContents(accountId, encryptedFileId);
    String text = new String(fileContent, Charset.forName("UTF-8"));
    evaluatedSecrets.put(key, text);
    return returnConfigFileValue("obtainConfigFileAsString", path, encryptedFileId, text);
  }

  @Override
  public Object obtainConfigFileAsBase64(String path, String encryptedFileId, int token) {
    if (token != expressionFunctorToken) {
      throw new FunctorException("Inappropriate usage of internal functor");
    }

    String key = format("//base64:/%s", path);
    if (evaluatedSecrets.containsKey(key)) {
      return returnConfigFileValue("obtainConfigFileAsBase64", path, encryptedFileId, evaluatedSecrets.get(key));
    }

    byte[] fileContent = secretManager.getFileContents(accountId, encryptedFileId);
    String encodeBase64 = EncodingUtils.encodeBase64(fileContent);
    evaluatedSecrets.put(key, encodeBase64);
    return returnConfigFileValue("obtainConfigFileAsBase64", path, encryptedFileId, encodeBase64);
  }

  private Object returnConfigFileValue(String method, String path, String encryptedFileId, Object value) {
    if (mode == SecretManagerMode.DRY_RUN) {
      return obtainConfigFileExpression(method, path, encryptedFileId, expressionFunctorToken);
    } else if (mode == SecretManagerMode.CHECK_FOR_SECRETS) {
      return format(SecretManagerPreviewFunctor.SECRET_NAME_FORMATTER, path);
    }
    return value;
  }

  private Object returnSecretValue(String secretName, Object value) {
    if (mode == SecretManagerMode.DRY_RUN) {
      return obtainExpression(secretName, expressionFunctorToken);
    } else if (mode == SecretManagerMode.CHECK_FOR_SECRETS) {
      return format(SecretManagerPreviewFunctor.SECRET_NAME_FORMATTER, secretName);
    }
    return value;
  }

  private Object obtainInternal(String secretName) {
    if (evaluatedSecrets.containsKey(secretName)) {
      return returnSecretValue(secretName, evaluatedSecrets.get(secretName));
    }
    if (evaluatedDelegateSecrets.containsKey(secretName)) {
      return returnSecretValue(secretName, evaluatedDelegateSecrets.get(secretName));
    }

    EncryptedData encryptedData = secretManager.getSecretMappedToAppByName(accountId, appId, envId, secretName);
    if (encryptedData == null) {
      throw new InvalidRequestException("No secret found with name + [" + secretName + "]", USER);
    }
    ServiceVariable serviceVariable = ServiceVariable.builder()
                                          .accountId(accountId)
                                          .type(ENCRYPTED_TEXT)
                                          .encryptedValue(encryptedData.getUuid())
                                          .secretTextName(secretName)
                                          .build();

    List<EncryptedDataDetail> encryptedDataDetails =
        secretManager.getEncryptionDetails(serviceVariable, appId, workflowExecutionId);

    boolean enabled = featureFlagService.isEnabled(FeatureName.THREE_PHASE_SECRET_DECRYPTION, accountId);

    List<EncryptedDataDetail> localEncryptedDetails =
        encryptedDataDetails.stream()
            .filter(encryptedDataDetail
                -> !enabled || encryptedDataDetail.getEncryptedData().getEncryptionType() == EncryptionType.LOCAL)
            .collect(Collectors.toList());

    if (isNotEmpty(localEncryptedDetails)) {
      EncryptedRecordData locallyEncryptedData = secretsCache.get(encryptedData.getUuid());
      String secretValue;
      if (locallyEncryptedData == null) {
        log.debug("Cache miss for secret with name: {}", serviceVariable.getSecretTextName());
        managerDecryptionService.decrypt(serviceVariable, localEncryptedDetails);
        secretValue = new String(serviceVariable.getValue());
        String localEncryptionKey = generateUuid();
        char[] reEncryptedValue = new SimpleEncryption(localEncryptionKey).encryptChars(serviceVariable.getValue());
        locallyEncryptedData = EncryptedRecordData.builder()
                                   .uuid(encryptedData.getUuid())
                                   .name(encryptedData.getName())
                                   .encryptionType(LOCAL)
                                   .encryptionKey(localEncryptionKey)
                                   .encryptedValue(reEncryptedValue)
                                   .base64Encoded(encryptedData.isBase64Encoded())
                                   .build();
        secretsCache.put(encryptedData.getUuid(), locallyEncryptedData);
      } else {
        // Cache hit. Decrypt the value locally with previously saved encryption key.
        secretValue = new String(new SimpleEncryption(locallyEncryptedData.getEncryptionKey())
                                     .decryptChars(locallyEncryptedData.getEncryptedValue()));
      }
      evaluatedSecrets.put(secretName, secretValue);
      return returnSecretValue(secretName, secretValue);
    }

    List<EncryptedDataDetail> nonLocalEncryptedDetails =
        encryptedDataDetails.stream()
            .filter(encryptedDataDetail
                -> encryptedDataDetail.getEncryptedData().getEncryptionType() != EncryptionType.LOCAL)
            .collect(Collectors.toList());

    if (nonLocalEncryptedDetails.size() != 1) {
      throw new InvalidRequestException("More than one encrypted records associated with + [" + secretName + "]", USER);
    }

    EncryptedDataDetail encryptedDataDetail = nonLocalEncryptedDetails.get(0);

    String encryptionConfigUuid = encryptedDataDetail.getEncryptionConfig().getUuid();
    encryptionConfigs.put(encryptionConfigUuid, encryptedDataDetail.getEncryptionConfig());

    SecretDetail secretDetail = SecretDetail.builder()
                                    .configUuid(encryptionConfigUuid)
                                    .encryptedRecord(EncryptedRecordData.builder()
                                                         .uuid(encryptedData.getUuid())
                                                         .name(encryptedData.getName())
                                                         .path(encryptedData.getPath())
                                                         .parameters(encryptedData.getParameters())
                                                         .encryptionKey(encryptedData.getEncryptionKey())
                                                         .encryptedValue(encryptedData.getEncryptedValue())
                                                         .kmsId(encryptedData.getKmsId())
                                                         .encryptionType(encryptedData.getEncryptionType())
                                                         .backupEncryptedValue(encryptedData.getBackupEncryptedValue())
                                                         .backupEncryptionKey(encryptedData.getBackupEncryptionKey())
                                                         .backupKmsId(encryptedData.getBackupKmsId())
                                                         .backupEncryptionType(encryptedData.getBackupEncryptionType())
                                                         .base64Encoded(encryptedData.isBase64Encoded())
                                                         .build())
                                    .build();

    String secretDetailsUuid = generateUuid();

    secretDetails.put(secretDetailsUuid, secretDetail);
    evaluatedDelegateSecrets.put(
        secretName, "${secretDelegate.obtain(\"" + secretDetailsUuid + "\", " + expressionFunctorToken + ")}");
    return returnSecretValue(secretName, evaluatedDelegateSecrets.get(secretName));
  }
}
