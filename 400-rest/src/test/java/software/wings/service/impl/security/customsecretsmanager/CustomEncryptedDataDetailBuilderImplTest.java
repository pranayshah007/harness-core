/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl.security.customsecretsmanager;

import static io.harness.rule.OwnerRule.UTKARSH;

import static software.wings.service.impl.security.customsecretsmanager.CustomSecretsManagerUtils.obtainConfig;
import static software.wings.settings.SettingVariableTypes.HOST_CONNECTION_ATTRIBUTES;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

import io.harness.CategoryTest;
import io.harness.beans.EncryptedData;
import io.harness.category.element.UnitTests;
import io.harness.expression.ExpressionEvaluator;
import io.harness.ff.FeatureFlagService;
import io.harness.rule.Owner;
import io.harness.security.encryption.EncryptedDataDetail;

import software.wings.annotation.EncryptableSetting;
import software.wings.security.encryption.secretsmanagerconfigs.CustomSecretsManagerConfig;
import software.wings.service.intfc.security.ManagerDecryptionService;
import software.wings.service.intfc.security.SecretManager;

import com.google.inject.Inject;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;

public class CustomEncryptedDataDetailBuilderImplTest extends CategoryTest {
  @Mock private CustomSecretsManagerConnectorHelper customSecretsManagerConnectorHelper;
  @Mock private SecretManager secretManager;
  @Mock private ManagerDecryptionService managerDecryptionService;
  @Mock private ExpressionEvaluator expressionEvaluator;
  @Mock private FeatureFlagService featureFlagService;
  @Inject @InjectMocks private CustomEncryptedDataDetailBuilderImpl customSecretsManagerEncryptionService;

  @Before
  public void setup() {
    initMocks(this);
  }

  @Test
  @Owner(developers = UTKARSH)
  @Category(UnitTests.class)
  public void test_buildEncryptedDataDetail() {
    CustomSecretsManagerConfig config = obtainConfig(HOST_CONNECTION_ATTRIBUTES);
    config.setConnectorTemplatized(true);
    EncryptedData encryptedData = mock(EncryptedData.class);
    String shellScript = config.getCustomSecretsManagerShellScript().getScriptString();
    when(expressionEvaluator.substitute(eq(shellScript), any())).thenReturn(shellScript);
    EncryptedDataDetail encryptedDataDetail =
        customSecretsManagerEncryptionService.buildEncryptedDataDetail(encryptedData, config);
    assertThat(encryptedDataDetail).isNotNull();
    assertThat(encryptedDataDetail.getEncryptionConfig()).isEqualTo(config);
    assertThat(encryptedDataDetail.getEncryptedData()).isNotNull();

    verify(expressionEvaluator, times(2)).substitute(eq(shellScript), any());
    verify(customSecretsManagerConnectorHelper, times(1)).setConnectorInConfig(eq(config), any());
    verify(managerDecryptionService, times(1)).decrypt(any(EncryptableSetting.class), any());
    verify(secretManager, times(1)).getEncryptionDetails(any(EncryptableSetting.class));
  }
}
