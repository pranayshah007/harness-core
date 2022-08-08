/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package software.wings.helpers.ext.external.comm;

import io.harness.security.encryption.EncryptedDataDetail;

import software.wings.helpers.ext.mail.SmtpConfig;

import java.util.List;

public interface CollaborationHandler {
  CollaborationProviderResponse handle(CollaborationProviderRequest request);

  boolean validateDelegateConnection(CollaborationProviderRequest request);
  boolean validateDelegateConnection(SmtpConfig smtpConfig, List<EncryptedDataDetail> encryptionDetails);
}
