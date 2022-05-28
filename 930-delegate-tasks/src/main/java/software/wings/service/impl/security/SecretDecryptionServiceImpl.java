/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl.security;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.eraro.ErrorCode.ENCRYPT_DECRYPT_ERROR;
import static io.harness.exception.WingsException.USER;
import static io.harness.reflection.ReflectionUtils.getFieldByName;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.DecryptableEntity;
import io.harness.delegate.exception.DelegateRetryableException;
import io.harness.encryption.SecretRefData;
import io.harness.exception.ExceptionUtils;
import io.harness.exception.SecretManagementDelegateException;
import io.harness.exception.SecretManagementException;
import io.harness.security.encryption.EncryptedDataDetail;
import io.harness.security.encryption.SecretDecryptionService;

import software.wings.annotation.EncryptableSetting;
import software.wings.service.intfc.security.EncryptionService;

import com.google.inject.Inject;
import java.lang.reflect.Field;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(PL)
@Slf4j
public class SecretDecryptionServiceImpl implements SecretDecryptionService {
  @Inject private EncryptionService encryptionService;

  @Override
  public DecryptableEntity decrypt(DecryptableEntity object, List<EncryptedDataDetail> encryptedDataDetails) {
    if (isEmpty(encryptedDataDetails)) {
      return object;
    }
    for (EncryptedDataDetail encryptedDataDetail : encryptedDataDetails) {
      try {
        char[] decryptedValue;

        Field f = getFieldByName(object.getClass(), encryptedDataDetail.getFieldName());
        if (f == null) {
          log.warn("Could not find field {} in class {}", encryptedDataDetail.getFieldName(), object.getClass());
          continue;
        }
        f.setAccessible(true);

        decryptedValue = getDecryptedValue(encryptedDataDetail);
        SecretRefData secretRefData = (SecretRefData) f.get(object);
        secretRefData.setDecryptedValue(decryptedValue);
        f.set(object, secretRefData);
      } catch (DelegateRetryableException | SecretManagementDelegateException e) {
        throw e;
      } catch (Exception e) {
        // Log the root cause exception of failed decryption attempts.
        log.error("Failed to decrypt encrypted settings.", e);
        throw new SecretManagementException(ENCRYPT_DECRYPT_ERROR, ExceptionUtils.getMessage(e), USER);
      }
    }
    object.setDecrypted(true);
    return object;
  }

  @Override
  public char[] getDecryptedValue(EncryptedDataDetail encryptedDataDetail) {
    return encryptionService.getDecryptedValue(encryptedDataDetail, false);
  }

  @Override
  public EncryptableSetting decrypt(
      EncryptableSetting object, List<EncryptedDataDetail> encryptedDataDetails, boolean fromCache) {
    return encryptionService.decrypt(object, encryptedDataDetails, fromCache);
  }
}
