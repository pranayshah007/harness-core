/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.azure.exception;

import static io.harness.rule.OwnerRule.ABOSII;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.exception.ExceptionUtils;
import io.harness.exception.ExplanationException;
import io.harness.exception.GeneralException;
import io.harness.exception.HintException;
import io.harness.exception.WingsException;
import io.harness.exception.ngexception.AzureAppServiceTaskException;
import io.harness.rule.Owner;

import com.microsoft.aad.msal4j.MsalException;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(HarnessTeam.CDP)
public class AzureClientExceptionHandlerTest extends CategoryTest {
  private static final String TENANT_NOT_FOUND_CODE = "AADSTS90002";
  private static final String TENANT_NOT_FOUND =
      "{\"error_description\":\"AADSTS90002: Tenant '00000000-0000-0000-0000-000000000000' not found. Check to make sure you have the correct tenant ID and are signing into the correct cloud. Check with your subscription administrator, this may happen if there are no active subscriptions for the tenant.\\r\\nTrace ID: 00000000-0000-0000-0000-000000000000\\r\\nCorrelation ID: 00000000-0000-0000-0000-000000000000\\r\\nTimestamp: 2022-08-02 11:19:53Z\",\"error\":\"invalid_request\",\"error_uri\":\"https:\\/\\/login.microsoftonline.com\\/error?code=90002\"}";
  private static final String APPLICATION_NOT_FOUND_CODE = "AADSTS700016";
  private static final String APPLICATION_NOT_FOUND =
      "{\"error_description\":\"AADSTS700016: Application with identifier '00000000-0000-0000-0000-000000000000' was not found in the directory 'Test Test'. This can happen if the application has not been installed by the administrator of the tenant or consented to by any user in the tenant. You may have sent your authentication request to the wrong tenant.\\r\\nTrace ID: 00000000-0000-0000-0000-000000000000\\r\\nCorrelation ID: 00000000-0000-0000-0000-000000000000\\r\\nTimestamp: 2022-08-02 11:53:31Z\",\"error\":\"unauthorized_client\",\"error_uri\":\"https:\\/\\/login.microsoftonline.com\\/error?code=700016\"}";
  private static final String INVALID_CREDENTIALS_CODE = "AADSTS7000215";
  private static final String INVALID_CREDENTIALS =
      "{\"error_description\":\"AADSTS7000215: Invalid client secret provided. Ensure the secret being sent in the request is the client secret value, not the client secret ID, for a secret added to app '00000000-0000-0000-0000-000000000000'.\\r\\nTrace ID: 1ac2a64a-16aa-433e-be83-c43c36ac1a01\\r\\nCorrelation ID: da2dd6b7-e090-459b-a2f5-1becad5c5142\\r\\nTimestamp: 2022-08-02 12:02:24Z\",\"error\":\"invalid_client\",\"error_uri\":\"https:\\/\\/login.microsoftonline.com\\/error?code=7000215\"}";
  private static final String RANDOM_ERROR = "Something went wrong";

  private final AzureClientExceptionHandler exceptionHandler = new AzureClientExceptionHandler();

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testHandleNoTenantFound() {
    final MsalException authenticationException = new MsalException(TENANT_NOT_FOUND, TENANT_NOT_FOUND_CODE);
    WingsException result = exceptionHandler.handleException(authenticationException);
    assertMessages(result,
        "Check to make sure you have the correct tenant ID and are signing into the correct cloud. Check with your subscription administrator, this may happen if there are no active subscriptions for the tenant.",
        "Tenant '00000000-0000-0000-0000-000000000000' not found. More details: https://login.microsoftonline.com/error?code=90002");
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testHandleApplicationNotFound() {
    final MsalException authenticationException = new MsalException(APPLICATION_NOT_FOUND, APPLICATION_NOT_FOUND_CODE);
    WingsException result = exceptionHandler.handleException(authenticationException);
    assertMessages(result,
        "This can happen if the application has not been installed by the administrator of the tenant or consented to by any user in the tenant. You may have sent your authentication request to the wrong tenant.",
        "Application with identifier '00000000-0000-0000-0000-000000000000' was not found in the directory 'Test Test'. More details: https://login.microsoftonline.com/error?code=700016");
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testHandleInvalidClientSecret() {
    final MsalException authenticationException = new MsalException(INVALID_CREDENTIALS, INVALID_CREDENTIALS_CODE);
    WingsException result = exceptionHandler.handleException(authenticationException);
    assertMessages(result,
        "Ensure the secret being sent in the request is the client secret value, not the client secret ID, for a secret added to app '00000000-0000-0000-0000-000000000000'.",
        "Invalid client secret provided. More details: https://login.microsoftonline.com/error?code=7000215");
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testHandleRandomError() {
    final MsalException authenticationException = new MsalException(RANDOM_ERROR, "AADXXXXX");
    WingsException result = exceptionHandler.handleException(authenticationException);
    assertThat(result).isInstanceOf(GeneralException.class);
  }

  private void assertMessages(WingsException exception, String expectedHint, String expectedExplanation) {
    HintException hint = ExceptionUtils.cause(HintException.class, exception);
    ExplanationException explanation = ExceptionUtils.cause(ExplanationException.class, exception);
    AzureAppServiceTaskException taskException = ExceptionUtils.cause(AzureAppServiceTaskException.class, exception);

    assertThat(hint.getMessage()).isEqualTo(expectedHint);
    assertThat(explanation.getMessage()).isEqualTo(expectedExplanation);
    assertThat(taskException.getMessage()).isEqualTo("Failed to authenticate in azure cloud");
  }
}