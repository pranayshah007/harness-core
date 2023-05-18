/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.resources;

import static io.harness.expression.SecretString.SECRET_MASK;
import static io.harness.rule.OwnerRule.PRATEEK;

import static software.wings.resources.SSOResourceNG.CLIENT_SECRET_NAME_PREFIX;
import static software.wings.utils.WingsTestConstants.ACCOUNT_ID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyBoolean;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

import io.harness.CategoryTest;
import io.harness.beans.EncryptedData;
import io.harness.beans.SecretManagerConfig;
import io.harness.beans.SecretText;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;
import io.harness.secretmanagers.SecretManagerConfigService;

import software.wings.service.intfc.security.SecretManager;

import java.io.IOException;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;

public class SSOResourceNGTest extends CategoryTest {
  @InjectMocks private SSOResourceNG ssoResourceNG;

  @Mock private SecretManagerConfigService secretManagerConfigService;

  @Mock private SecretManager secretManager;

  @Before
  public void setUp() throws IOException {
    initMocks(this);
  }

  @Test
  @Owner(developers = PRATEEK)
  @Category(UnitTests.class)
  public void testGetCGSecretManagerRefForClientSecretCreate() {
    final String secretRefId = "clientSecretRefId";
    final String uuid = java.util.UUID.randomUUID().toString();
    SecretManagerConfig secretManagerConfig = mock(SecretManagerConfig.class);
    when(secretManagerConfigService.getDefaultSecretManager(anyString())).thenReturn(secretManagerConfig);
    when(secretManagerConfig.getUuid()).thenReturn(uuid);
    doReturn(null).when(secretManager).getSecretByName(ACCOUNT_ID, CLIENT_SECRET_NAME_PREFIX + ACCOUNT_ID);

    when(secretManager.saveSecretText(anyString(), any(SecretText.class), anyBoolean())).thenReturn(secretRefId);
    final String result =
        ssoResourceNG.getCGSecretManagerRefForClientSecret(ACCOUNT_ID, true, "clientId", "clientSecret");

    assertThat(result).isEqualTo(secretRefId);
  }

  @Test
  @Owner(developers = PRATEEK)
  @Category(UnitTests.class)
  public void testGetCGSecretManagerRefForClientSecretUpdate() {
    final String secretRefId = "clientSecretRefId";
    EncryptedData ed = new EncryptedData();
    ed.setUuid(secretRefId);
    SecretManagerConfig secretManagerConfig = mock(SecretManagerConfig.class);
    when(secretManagerConfigService.getDefaultSecretManager(anyString())).thenReturn(secretManagerConfig);
    when(secretManagerConfig.getUuid()).thenReturn(java.util.UUID.randomUUID().toString());
    when(secretManager.updateSecretText(anyString(), anyString(), any(SecretText.class), anyBoolean()))
        .thenReturn(true);
    doReturn(ed).when(secretManager).getSecretByName(ACCOUNT_ID, CLIENT_SECRET_NAME_PREFIX + ACCOUNT_ID);
    final String result =
        ssoResourceNG.getCGSecretManagerRefForClientSecret(ACCOUNT_ID, false, "newClientId", "clientSecret");

    assertThat(result).isEqualTo(secretRefId);
  }

  @Test
  @Owner(developers = PRATEEK)
  @Category(UnitTests.class)
  public void testGetCGSecretManagerRefForClientSecretUpdateMaskValue() {
    final String result =
        ssoResourceNG.getCGSecretManagerRefForClientSecret(ACCOUNT_ID, false, "clientId", SECRET_MASK);
    assertThat(result).isEqualTo(SECRET_MASK);
  }
}
