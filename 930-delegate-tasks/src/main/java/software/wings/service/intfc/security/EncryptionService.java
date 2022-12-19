/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package software.wings.service.intfc.security;

import static io.harness.annotations.dev.HarnessTeam.PL;

import io.harness.annotations.dev.OwnedBy;
import io.harness.security.encryption.EncryptedDataDetail;
import io.harness.security.encryption.setting.EncryptableSettingWithEncryptionDetails;

import software.wings.annotation.EncryptableSetting;
import software.wings.beans.TaskType;
import software.wings.delegatetasks.DelegateTaskType;

import java.util.List;

/**
 * Created by rsingh on 10/18/17.
 */
@OwnedBy(PL)
public interface EncryptionService {
  @DelegateTaskType(TaskType.SECRET_DECRYPT)
  EncryptableSetting decrypt(
      EncryptableSetting object, List<EncryptedDataDetail> encryptedDataDetails, boolean fromCache);

  @DelegateTaskType(TaskType.BATCH_SECRET_DECRYPT)
  List<EncryptableSettingWithEncryptionDetails> decrypt(
      List<EncryptableSettingWithEncryptionDetails> encryptableSettingWithEncryptionDetailsList, boolean fromCache);

  @DelegateTaskType(TaskType.SECRET_DECRYPT_REF)
  char[] getDecryptedValue(EncryptedDataDetail encryptedDataDetail, boolean fromCache);
}
