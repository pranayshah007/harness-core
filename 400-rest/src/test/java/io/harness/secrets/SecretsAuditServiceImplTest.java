/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.secrets;

import static io.harness.rule.OwnerRule.UTKARSH;

import static software.wings.beans.Event.Type.CREATE;
import static software.wings.beans.Event.Type.UPDATE;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.beans.EncryptedData;
import io.harness.beans.HarnessSecret;
import io.harness.beans.SecretChangeLog;
import io.harness.beans.SecretChangeLog.SecretChangeLogKeys;
import io.harness.beans.SecretUpdateData;
import io.harness.category.element.UnitTests;
import io.harness.data.structure.UUIDGenerator;
import io.harness.rule.Owner;

import software.wings.WingsBaseTest;
import software.wings.beans.User;
import software.wings.dl.WingsPersistence;
import software.wings.security.UserThreadLocal;
import software.wings.service.impl.AuditServiceHelper;

import com.google.inject.Inject;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class SecretsAuditServiceImplTest extends WingsBaseTest {
  @Inject private WingsPersistence wingsPersistence;
  private AuditServiceHelper auditServiceHelper;
  private SecretsAuditService secretsAuditService;

  @Before
  public void setup() {
    User user = mock(User.class);
    when(user.getUuid()).thenReturn(UUIDGenerator.generateUuid());
    when(user.getEmail()).thenReturn(UUIDGenerator.generateUuid());
    when(user.getName()).thenReturn(UUIDGenerator.generateUuid());
    UserThreadLocal.set(user);
    auditServiceHelper = mock(AuditServiceHelper.class);
    secretsAuditService = new SecretsAuditServiceImpl(auditServiceHelper, wingsPersistence);
  }

  @Test
  @Owner(developers = UTKARSH)
  @Category(UnitTests.class)
  public void testLogCreateEvent() {
    EncryptedData encryptedData =
        EncryptedData.builder().uuid(UUIDGenerator.generateUuid()).accountId(UUIDGenerator.generateUuid()).build();
    secretsAuditService.logSecretCreateEvent(encryptedData);
    verify(auditServiceHelper, times(1))
        .reportForAuditingUsingAccountId(encryptedData.getAccountId(), null, encryptedData, CREATE);
    SecretChangeLog secretChangeLog = wingsPersistence.createQuery(SecretChangeLog.class)
                                          .filter(SecretChangeLogKeys.accountId, encryptedData.getAccountId())
                                          .get();
    assertThat(secretChangeLog.getDescription()).isEqualTo("Created");
    assertThat(secretChangeLog.getAccountId()).isEqualTo(encryptedData.getAccountId());
    assertThat(secretChangeLog.getEncryptedDataId()).isEqualTo(encryptedData.getUuid());
  }

  @Test
  @Owner(developers = UTKARSH)
  @Category(UnitTests.class)
  public void testlogSecretUpdateEvent() {
    EncryptedData oldRecord =
        EncryptedData.builder().uuid(UUIDGenerator.generateUuid()).accountId(UUIDGenerator.generateUuid()).build();
    EncryptedData newRecord =
        EncryptedData.builder().uuid(oldRecord.getUuid()).accountId(oldRecord.getAccountId()).build();
    HarnessSecret harnessSecret = HarnessSecret.builder().name(UUIDGenerator.generateUuid()).build();
    SecretUpdateData secretUpdateData = new SecretUpdateData(harnessSecret, oldRecord);
    secretsAuditService.logSecretUpdateEvent(oldRecord, newRecord, secretUpdateData);
    verify(auditServiceHelper, times(1))
        .reportForAuditingUsingAccountId(oldRecord.getAccountId(), oldRecord, newRecord, UPDATE);
    SecretChangeLog secretChangeLog = wingsPersistence.createQuery(SecretChangeLog.class)
                                          .filter(SecretChangeLogKeys.accountId, oldRecord.getAccountId())
                                          .get();
    assertThat(secretChangeLog.getDescription()).isEqualTo("Changed name");
    assertThat(secretChangeLog.getAccountId()).isEqualTo(oldRecord.getAccountId());
    assertThat(secretChangeLog.getEncryptedDataId()).isEqualTo(oldRecord.getUuid());
  }

  @Test
  @Owner(developers = UTKARSH)
  @Category(UnitTests.class)
  public void testlogSecretDeleteEvent() {
    EncryptedData record =
        EncryptedData.builder().uuid(UUIDGenerator.generateUuid()).accountId(UUIDGenerator.generateUuid()).build();
    secretsAuditService.logSecretDeleteEvent(record);
    verify(auditServiceHelper, times(1)).reportDeleteForAuditingUsingAccountId(record.getAccountId(), record);
  }
}
