/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.expression;

import static io.harness.rule.OwnerRule.AADITI;

import static software.wings.beans.ServiceVariableType.ENCRYPTED_TEXT;
import static software.wings.expression.SecretFunctor.Mode.CASCADING;
import static software.wings.utils.WingsTestConstants.ACCOUNT_ID;
import static software.wings.utils.WingsTestConstants.APP_ID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.beans.EncryptedData;
import io.harness.category.element.UnitTests;
import io.harness.data.structure.UUIDGenerator;
import io.harness.expression.SecretString;
import io.harness.rule.Owner;
import io.harness.security.encryption.EncryptedDataDetail;
import io.harness.security.encryption.EncryptionType;

import software.wings.WingsBaseTest;
import software.wings.beans.ServiceVariable;
import software.wings.service.intfc.security.ManagerDecryptionService;
import software.wings.service.intfc.security.SecretManager;

import java.util.Arrays;
import java.util.List;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.stubbing.Answer;

public class SecretFunctorTest extends WingsBaseTest {
  @Mock private ManagerDecryptionService managerDecryptionService;
  @Mock private SecretManager secretManager;

  @Test
  @Owner(developers = AADITI)
  @Category(UnitTests.class)
  public void shouldGetAccountScopedSecretsWithCascadingMode() {
    final String secretName = "MySecretName";
    final String decryptedString = "DecryptedValue";
    SecretFunctor secretFunctor = SecretFunctor.builder()
                                      .managerDecryptionService(managerDecryptionService)
                                      .secretManager(secretManager)
                                      .accountId(ACCOUNT_ID)
                                      .appId(APP_ID)
                                      .mode(CASCADING)
                                      .build();

    EncryptedData encryptedData = EncryptedData.builder()
                                      .encryptionType(EncryptionType.LOCAL)
                                      .accountId(ACCOUNT_ID)
                                      .scopedToAccount(true)
                                      .build();
    encryptedData.setUuid(UUIDGenerator.generateUuid());

    when(secretManager.getSecretMappedToAppByName(ACCOUNT_ID, APP_ID, null, secretName)).thenReturn(null);
    when(secretManager.getSecretMappedToAccountByName(ACCOUNT_ID, secretName)).thenReturn(encryptedData);
    ServiceVariable serviceVariable = buildServiceVariable(secretName, encryptedData);

    List<EncryptedDataDetail> localEncryptedDetails = Arrays.asList(
        EncryptedDataDetail.builder().encryptedData(SecretManager.buildRecordData(encryptedData)).build());

    when(secretManager.getEncryptionDetails(serviceVariable, null, null)).thenReturn(localEncryptedDetails);

    Mockito
        .doAnswer((Answer<Void>) invocation -> {
          Object[] args = invocation.getArguments();
          ServiceVariable serviceVariable1 = (ServiceVariable) args[0];
          serviceVariable1.setValue(decryptedString.toCharArray());
          return null;
        })
        .when(managerDecryptionService)
        .decrypt(serviceVariable, localEncryptedDetails);
    SecretString decryptedValue = (SecretString) secretFunctor.getValue(secretName);
    assertThat(decryptedValue.toString()).isNotNull().isEqualTo(decryptedString);
    verify(secretManager, times(1)).getSecretMappedToAppByName(ACCOUNT_ID, APP_ID, null, secretName);
    verify(secretManager, times(1)).getSecretMappedToAccountByName(ACCOUNT_ID, secretName);
  }

  @Test
  @Owner(developers = AADITI)
  @Category(UnitTests.class)
  public void shouldGetAppScopedSecretsWithCascadingMode() {
    final String secretName = "MySecretName";
    final String decryptedString = "DecryptedValue";
    SecretFunctor secretFunctor = SecretFunctor.builder()
                                      .managerDecryptionService(managerDecryptionService)
                                      .secretManager(secretManager)
                                      .accountId(ACCOUNT_ID)
                                      .appId(APP_ID)
                                      .mode(CASCADING)
                                      .build();

    EncryptedData encryptedData = EncryptedData.builder()
                                      .encryptionType(EncryptionType.LOCAL)
                                      .accountId(ACCOUNT_ID)
                                      .scopedToAccount(false)
                                      .build();
    encryptedData.setUuid(UUIDGenerator.generateUuid());

    when(secretManager.getSecretMappedToAppByName(ACCOUNT_ID, APP_ID, null, secretName)).thenReturn(encryptedData);
    ServiceVariable serviceVariable = buildServiceVariable(secretName, encryptedData);

    List<EncryptedDataDetail> localEncryptedDetails = Arrays.asList(
        EncryptedDataDetail.builder().encryptedData(SecretManager.buildRecordData(encryptedData)).build());

    when(secretManager.getEncryptionDetails(serviceVariable, null, null)).thenReturn(localEncryptedDetails);

    Mockito
        .doAnswer((Answer<Void>) invocation -> {
          Object[] args = invocation.getArguments();
          ServiceVariable serviceVariable1 = (ServiceVariable) args[0];
          serviceVariable1.setValue(decryptedString.toCharArray());
          return null;
        })
        .when(managerDecryptionService)
        .decrypt(serviceVariable, localEncryptedDetails);
    SecretString decryptedValue = (SecretString) secretFunctor.getValue(secretName);
    assertThat(decryptedValue.toString()).isNotNull().isEqualTo(decryptedString);
    verify(secretManager, times(1)).getSecretMappedToAppByName(ACCOUNT_ID, APP_ID, null, secretName);
    verify(secretManager, times(0)).getSecretMappedToAccountByName(ACCOUNT_ID, secretName);
  }

  @Test
  @Owner(developers = AADITI)
  @Category(UnitTests.class)
  public void shouldGetAccountScopedSecretsWithAlternatingMode() {
    final String secretName = "MySecretName";
    final String decryptedString = "DecryptedValue";
    SecretFunctor secretFunctor = SecretFunctor.builder()
                                      .managerDecryptionService(managerDecryptionService)
                                      .secretManager(secretManager)
                                      .accountId(ACCOUNT_ID)
                                      .appId(APP_ID)
                                      .build();

    EncryptedData encryptedData = EncryptedData.builder()
                                      .encryptionType(EncryptionType.LOCAL)
                                      .accountId(ACCOUNT_ID)
                                      .scopedToAccount(false)
                                      .build();
    encryptedData.setUuid(UUIDGenerator.generateUuid());

    when(secretManager.getSecretMappedToAppByName(ACCOUNT_ID, APP_ID, null, secretName)).thenReturn(encryptedData);
    ServiceVariable serviceVariable = buildServiceVariable(secretName, encryptedData);

    List<EncryptedDataDetail> localEncryptedDetails = Arrays.asList(
        EncryptedDataDetail.builder().encryptedData(SecretManager.buildRecordData(encryptedData)).build());

    when(secretManager.getEncryptionDetails(serviceVariable, null, null)).thenReturn(localEncryptedDetails);

    Mockito
        .doAnswer((Answer<Void>) invocation -> {
          Object[] args = invocation.getArguments();
          ServiceVariable serviceVariable1 = (ServiceVariable) args[0];
          serviceVariable1.setValue(decryptedString.toCharArray());
          return null;
        })
        .when(managerDecryptionService)
        .decrypt(serviceVariable, localEncryptedDetails);
    SecretString decryptedValue = (SecretString) secretFunctor.getValue(secretName);
    assertThat(decryptedValue.toString()).isNotNull().isEqualTo(decryptedString);
    verify(secretManager, times(1)).getSecretMappedToAppByName(ACCOUNT_ID, APP_ID, null, secretName);
    verify(secretManager, times(0)).getSecretMappedToAccountByName(ACCOUNT_ID, secretName);
  }

  private ServiceVariable buildServiceVariable(String secretName, EncryptedData encryptedData) {
    return ServiceVariable.builder()
        .accountId(ACCOUNT_ID)
        .type(ENCRYPTED_TEXT)
        .encryptedValue(encryptedData.getUuid())
        .secretTextName(secretName)
        .build();
  }
}
