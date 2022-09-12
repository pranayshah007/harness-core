/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.ldap;

import static io.harness.rule.OwnerRule.PRATEEK;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.data.structure.UUIDGenerator;
import io.harness.delegate.beans.DelegateTaskPackage;
import io.harness.delegate.beans.TaskData;
import io.harness.delegate.beans.ldap.NGLdapTestAuthenticationTaskParameters;
import io.harness.delegate.beans.ldap.NGLdapTestAuthenticationTaskResponse;
import io.harness.rule.Owner;
import io.harness.security.encryption.EncryptedDataDetail;

import software.wings.beans.dto.LdapSettings;
import software.wings.helpers.ext.ldap.LdapResponse;
import software.wings.service.intfc.ldap.LdapDelegateService;

import org.apache.commons.lang3.reflect.FieldUtils;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;

@OwnedBy(HarnessTeam.PL)
public class NGLdapTestAuthenticationTaskTest {
  @Mock EncryptedDataDetail encryptedDataDetail;
  @Mock EncryptedDataDetail pwdEncryptedDataDetail;
  @Mock LdapSettings ldapSettings;
  LdapDelegateService ldapDelegateService = mock(LdapDelegateService.class);

  NGLdapTestAuthenticationTaskParameters ldapAuthTaskParams = NGLdapTestAuthenticationTaskParameters.builder()
                                                                  .ldapSettings(ldapSettings)
                                                                  .settingsEncryptedDataDetail(encryptedDataDetail)
                                                                  .passwordEncryptedDataDetail(pwdEncryptedDataDetail)
                                                                  .build();

  DelegateTaskPackage delegateTaskPackage = DelegateTaskPackage.builder()
                                                .delegateId(UUIDGenerator.generateUuid())
                                                .accountId(UUIDGenerator.generateUuid())
                                                .data(TaskData.builder()
                                                          .async(false)
                                                          .parameters(new Object[] {ldapAuthTaskParams})
                                                          .timeout(TaskData.DEFAULT_SYNC_CALL_TIMEOUT)
                                                          .build())
                                                .build();

  @InjectMocks
  NGLdapTestAuthenticationTask ngLdapAuthTask =
      new NGLdapTestAuthenticationTask(delegateTaskPackage, null, delegateTaskResponse -> {}, () -> true);

  @Test
  @Owner(developers = PRATEEK)
  @Category(UnitTests.class)
  public void testRunNgLdapTestAuthenticationTask() throws IllegalAccessException {
    String authSuccessMsg = "Authentication Successful";
    LdapResponse ldapResponse =
        LdapResponse.builder().status(LdapResponse.Status.SUCCESS).message(authSuccessMsg).build();

    FieldUtils.writeField(ngLdapAuthTask, "ldapDelegateService", ldapDelegateService, true);
    when(ldapDelegateService.authenticate(any(), any(), anyString(), any())).thenReturn(ldapResponse);

    NGLdapTestAuthenticationTaskResponse taskResponse =
        (NGLdapTestAuthenticationTaskResponse) ngLdapAuthTask.run(ldapAuthTaskParams);
    assertThat(taskResponse).isNotNull();
  }
}
