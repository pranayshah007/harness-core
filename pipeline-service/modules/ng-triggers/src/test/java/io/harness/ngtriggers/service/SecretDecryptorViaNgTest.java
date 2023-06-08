/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngtriggers.service;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.rule.OwnerRule.VINICIUS;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.DecryptableEntity;
import io.harness.category.element.UnitTests;
import io.harness.delegate.beans.connector.scm.github.GithubConnectorDTO;
import io.harness.ngtriggers.service.impl.SecretDecryptorViaNg;
import io.harness.ngtriggers.utils.SecretUtils;
import io.harness.rule.Owner;
import io.harness.security.encryption.EncryptedDataDetail;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@OwnedBy(PIPELINE)
public class SecretDecryptorViaNgTest extends CategoryTest {
  @Mock private SecretUtils secretUtils;
  @InjectMocks private SecretDecryptorViaNg secretDecryptorViaNg;

  @Before
  public void setUp() throws IOException {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = VINICIUS)
  @Category(UnitTests.class)
  public void testDecrypt() {
    when(secretUtils.decrypt(any(), any(), any(), any())).thenReturn(null);
    DecryptableEntity connectorDTO = GithubConnectorDTO.builder().build();
    List<EncryptedDataDetail> encryptedDataDetails = Collections.singletonList(EncryptedDataDetail.builder().build());
    secretDecryptorViaNg.decrypt(connectorDTO, encryptedDataDetails);
    verify(secretUtils, times(1)).decrypt(connectorDTO, encryptedDataDetails, "random", null);
  }
}
