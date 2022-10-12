/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngtriggers;

import static io.harness.annotations.dev.HarnessTeam.SPG;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.WebhookEncryptedSecretDTO;
import io.harness.security.encryption.EncryptedDataDetail;

import java.util.List;
import lombok.Builder;
import lombok.Data;

@OwnedBy(SPG)
@Data
@Builder
public class WebhookSecretData {
  WebhookEncryptedSecretDTO webhookEncryptedSecretDTO;
  List<EncryptedDataDetail> encryptedDataDetails;
}
