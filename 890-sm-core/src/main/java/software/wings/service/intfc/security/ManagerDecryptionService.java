/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.intfc.security;

import static io.harness.annotations.dev.HarnessTeam.PL;

import io.harness.annotations.dev.OwnedBy;
import io.harness.security.encryption.EncryptedDataDetail;
import io.harness.security.encryption.setting.EncryptableSettingWithEncryptionDetails;

import software.wings.annotation.EncryptableSetting;

import java.util.List;

/**
 * Created by rsingh on 6/7/18.
 */
@OwnedBy(PL)
public interface ManagerDecryptionService {
  void decrypt(EncryptableSetting object, List<EncryptedDataDetail> encryptedDataDetails);

  void decrypt(
      String accountId, List<EncryptableSettingWithEncryptionDetails> encryptableSettingWithEncryptionDetailsList);
}
