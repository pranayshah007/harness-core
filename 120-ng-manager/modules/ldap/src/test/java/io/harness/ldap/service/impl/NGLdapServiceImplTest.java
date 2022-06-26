package io.harness.ldap.service.impl;

import static io.harness.rule.OwnerRule.SHASHANK;

import static software.wings.beans.sso.LdapTestResponse.Status.FAILURE;
import static software.wings.beans.sso.LdapTestResponse.Status.SUCCESS;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertNotNull;
import static junit.framework.TestCase.assertNull;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.failBecauseExceptionWasNotThrown;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.delegate.beans.ErrorNotifyResponseData;
import io.harness.delegate.beans.ldap.NGLdapDelegateTaskResponse;
import io.harness.delegate.utils.TaskSetupAbstractionHelper;
import io.harness.exception.WingsException;
import io.harness.rule.Owner;
import io.harness.service.DelegateGrpcClientWrapper;

import software.wings.beans.dto.LdapSettings;
import software.wings.beans.sso.LdapTestResponse;
import software.wings.service.impl.ldap.LdapDelegateException;

import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Spy;
import org.mockito.runners.MockitoJUnitRunner;

@OwnedBy(HarnessTeam.PL)
@RunWith(MockitoJUnitRunner.class)
public class NGLdapServiceImplTest extends CategoryTest {
  public static final String INVAILD_CREDENTIALS = "Invaild Credentials";
  public static final String UNKNOWN_RESPONSE_FROM_DELEGATE = "Unknown Response from delegate";
  TaskSetupAbstractionHelper taskSetupAbstractionHelper = mock(TaskSetupAbstractionHelper.class);
  DelegateGrpcClientWrapper delegateGrpcClientWrapper = mock(DelegateGrpcClientWrapper.class);

  @Spy @InjectMocks private NGLdapServiceImpl ngLdapService;

  @Before
  public void setup() {
    initMocks(this);
  }

  @Test
  @Owner(developers = SHASHANK)
  @Category(UnitTests.class)
  public void testLdapConnectionSuccessfulAndUnsuccessful() {
    final String accountId = "testAccountId";
    LdapSettings ldapSettings = LdapSettings.builder().accountId(accountId).build();
    LdapTestResponse successfulTestResponse =
        LdapTestResponse.builder().status(SUCCESS).message("Connection Successful").build();

    when(delegateGrpcClientWrapper.executeSyncTask(any()))
        .thenReturn(NGLdapDelegateTaskResponse.builder().ldapTestResponse(successfulTestResponse).build());

    LdapTestResponse ldapTestResponse =
        ngLdapService.validateLdapConnectionSettings(accountId, null, null, ldapSettings);

    assertNotNull(ldapTestResponse);
    assertEquals(successfulTestResponse.getStatus(), ldapTestResponse.getStatus());

    LdapTestResponse unsuccessfulTestResponse =
        LdapTestResponse.builder().status(FAILURE).message("Invalid Credential").build();

    when(delegateGrpcClientWrapper.executeSyncTask(any()))
        .thenReturn(NGLdapDelegateTaskResponse.builder().ldapTestResponse(unsuccessfulTestResponse).build());

    ldapTestResponse = ngLdapService.validateLdapConnectionSettings(accountId, null, null, ldapSettings);

    assertNotNull(ldapTestResponse);
    assertEquals(unsuccessfulTestResponse.getStatus(), ldapTestResponse.getStatus());
  }

  @Test
  @Owner(developers = SHASHANK)
  @Category(UnitTests.class)
  public void testLdapConnectionException() {
    final String accountId = "testAccountId";
    LdapSettings ldapSettings = LdapSettings.builder().accountId(accountId).build();

    when(delegateGrpcClientWrapper.executeSyncTask(any())).thenReturn(buildErrorNotifyResponseData());

    LdapTestResponse ldapTestResponse = null;
    try {
      ldapTestResponse = ngLdapService.validateLdapConnectionSettings(accountId, null, null, ldapSettings);
      failBecauseExceptionWasNotThrown(WingsException.class);
    } catch (Exception e) {
      assertNull(ldapTestResponse);
      assertThat(e).isInstanceOf(LdapDelegateException.class);
      assertEquals(e.getMessage(), UNKNOWN_RESPONSE_FROM_DELEGATE);
    }
  }

  private ErrorNotifyResponseData buildErrorNotifyResponseData() {
    return ErrorNotifyResponseData.builder().errorMessage(INVAILD_CREDENTIALS).build();
  }
}