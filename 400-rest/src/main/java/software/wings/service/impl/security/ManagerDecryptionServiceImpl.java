/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl.security;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.delegate.beans.TaskData.DEFAULT_ASYNC_CALL_TIMEOUT;
import static io.harness.eraro.ErrorCode.ENCRYPT_DECRYPT_ERROR;
import static io.harness.exception.WingsException.USER;
import static io.harness.reflection.ReflectionUtils.getFieldByName;
import static io.harness.security.SimpleEncryption.CHARSET;

import static software.wings.beans.CGConstants.GLOBAL_APP_ID;

import io.harness.annotations.dev.OwnedBy;
import io.harness.data.encoding.EncodingUtils;
import io.harness.exception.ExceptionUtils;
import io.harness.exception.SecretManagementException;
import io.harness.exception.WingsException;
import io.harness.security.SimpleEncryption;
import io.harness.security.encryption.EncryptedDataDetail;
import io.harness.security.encryption.EncryptionType;
import io.harness.security.encryption.setting.EncryptableSettingWithEncryptionDetails;

import software.wings.annotation.EncryptableSetting;
import software.wings.beans.SyncTaskContext;
import software.wings.delegatetasks.DelegateProxyFactory;
import software.wings.service.intfc.security.EncryptionService;
import software.wings.service.intfc.security.ManagerDecryptionService;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.lang.reflect.Field;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;

/**
 * Created by rsingh on 6/7/18.
 */
@OwnedBy(PL)
@Singleton
@Slf4j
public class ManagerDecryptionServiceImpl implements ManagerDecryptionService {
  private final DelegateProxyFactory delegateProxyFactory;

  @Inject
  public ManagerDecryptionServiceImpl(DelegateProxyFactory delegateProxyFactory) {
    this.delegateProxyFactory = delegateProxyFactory;
  }

  @Override
  public void decrypt(EncryptableSetting object, List<EncryptedDataDetail> encryptedDataDetails) {
    if (isEmpty(encryptedDataDetails)) {
      return;
    }
    // decrypt locally encrypted variables in manager
    encryptedDataDetails.stream()
        .filter(
            encryptedDataDetail -> encryptedDataDetail.getEncryptedData().getEncryptionType() == EncryptionType.LOCAL)
        .forEach(encryptedDataDetail -> {
          SimpleEncryption encryption = new SimpleEncryption(encryptedDataDetail.getEncryptedData().getEncryptionKey());
          char[] decryptChars = encryption.decryptChars(encryptedDataDetail.getEncryptedData().getEncryptedValue());
          if (encryptedDataDetail.getEncryptedData().isBase64Encoded()) {
            byte[] decodedBytes = EncodingUtils.decodeBase64(decryptChars);
            decryptChars = CHARSET.decode(ByteBuffer.wrap(decodedBytes)).array();
          }
          Field f = getFieldByName(object.getClass(), encryptedDataDetail.getFieldName());
          if (f != null) {
            f.setAccessible(true);
            try {
              f.set(object, decryptChars);
            } catch (IllegalAccessException e) {
              log.error("Decryption failed for {}", encryptedDataDetail.toString(), e);
            }
          }
        });

    // filter non local encrypted values and send to delegate to decrypt
    List<EncryptedDataDetail> nonLocalEncryptedDetails =
        encryptedDataDetails.stream()
            .filter(encryptedDataDetail
                -> encryptedDataDetail.getEncryptedData().getEncryptionType() != EncryptionType.LOCAL)
            .collect(Collectors.toList());

    // if nothing left to decrypt return
    if (isEmpty(nonLocalEncryptedDetails)) {
      object.setDecrypted(true);
      return;
    }
    SyncTaskContext syncTaskContext = SyncTaskContext.builder()
                                          .accountId(object.getAccountId())
                                          .appId(GLOBAL_APP_ID)
                                          .timeout(DEFAULT_ASYNC_CALL_TIMEOUT)
                                          .build();
    try {
      EncryptableSetting decrypted = delegateProxyFactory.getV2(EncryptionService.class, syncTaskContext)
                                         .decrypt(object, nonLocalEncryptedDetails, false);
      replaceEncryptedFieldsWithDecryptedValues(nonLocalEncryptedDetails, object, decrypted);
    } catch (WingsException e) {
      throw e;
    } catch (Exception e) {
      throw new SecretManagementException(ENCRYPT_DECRYPT_ERROR, ExceptionUtils.getMessage(e), e, USER);
    }
  }

  @Override
  public void decrypt(
      String accountId, List<EncryptableSettingWithEncryptionDetails> encryptableSettingWithEncryptionDetailsList) {
    if (isEmpty(encryptableSettingWithEncryptionDetailsList)) {
      return;
    }

    log.info("Batch decrypting {} encrypted settings", encryptableSettingWithEncryptionDetailsList.size());
    SyncTaskContext syncTaskContext =
        SyncTaskContext.builder().accountId(accountId).appId(GLOBAL_APP_ID).timeout(DEFAULT_ASYNC_CALL_TIMEOUT).build();
    try {
      List<EncryptableSettingWithEncryptionDetails> detailsList =
          delegateProxyFactory.getV2(EncryptionService.class, syncTaskContext)
              .decrypt(encryptableSettingWithEncryptionDetailsList, false);

      Map<String, EncryptableSetting> detailsMap =
          detailsList.stream().collect(Collectors.toMap(EncryptableSettingWithEncryptionDetails::getDetailId,
              EncryptableSettingWithEncryptionDetails::getEncryptableSetting));

      for (EncryptableSettingWithEncryptionDetails encryptableSettingWithEncryptionDetails :
          encryptableSettingWithEncryptionDetailsList) {
        EncryptableSetting encryptableSetting = encryptableSettingWithEncryptionDetails.getEncryptableSetting();
        EncryptableSetting decryptedSetting = detailsMap.get(encryptableSettingWithEncryptionDetails.getDetailId());
        if (decryptedSetting == null) {
          // Should not happen.
          throw new SecretManagementException(ENCRYPT_DECRYPT_ERROR,
              "Did not find any matching decrypted setting for encryptable setting " + encryptableSetting, USER);
        }

        List<EncryptedDataDetail> encryptedDataDetails =
            encryptableSettingWithEncryptionDetails.getEncryptedDataDetails();
        replaceEncryptedFieldsWithDecryptedValues(encryptedDataDetails, encryptableSetting, decryptedSetting);
      }
    } catch (WingsException e) {
      throw e;
    } catch (Exception e) {
      throw new SecretManagementException(ENCRYPT_DECRYPT_ERROR, ExceptionUtils.getMessage(e), e, USER);
    }
  }

  private void replaceEncryptedFieldsWithDecryptedValues(List<EncryptedDataDetail> encryptedDataDetails,
      EncryptableSetting encryptedSetting, EncryptableSetting decryptedSetting) throws Exception {
    for (EncryptedDataDetail encryptedDataDetail : encryptedDataDetails) {
      Field f = getFieldByName(encryptedSetting.getClass(), encryptedDataDetail.getFieldName());
      if (f != null) {
        f.setAccessible(true);
        f.set(encryptedSetting, f.get(decryptedSetting));
      }
    }
    encryptedSetting.setDecrypted(true);
  }
}
