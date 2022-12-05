/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.exceptionhandler.handler;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.eraro.ErrorCode.AZURE_AUTHENTICATION_ERROR;
import static io.harness.eraro.ErrorCode.AZURE_CONFIG_ERROR;
import static io.harness.eraro.ErrorCode.AZURE_KEY_VAULT_OPERATION_ERROR;
import static io.harness.exception.NestedExceptionUtils.hintWithExplanationException;
import static io.harness.exception.WingsException.USER;

import io.harness.annotations.dev.OwnedBy;
import io.harness.exception.ExplanationException;
import io.harness.exception.HintException;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.WingsException;
import io.harness.exception.exceptionmanager.exceptionhandler.ExceptionHandler;

import com.azure.core.exception.AzureException;
import com.azure.core.exception.ClientAuthenticationException;
import com.azure.security.keyvault.administration.implementation.models.KeyVaultErrorException;
import com.google.common.collect.ImmutableSet;
import com.google.inject.Singleton;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@OwnedBy(CDP)
@Singleton
public class AzureExceptionHandler implements ExceptionHandler {
  public static Set<Class<? extends Exception>> exceptions() {
    return ImmutableSet.<Class<? extends Exception>>builder()
        .add(AzureException.class)
        .build(); // AzureException is core exception
  }

  @Override
  public WingsException handleException(Exception exception) {
    AzureException ex = (AzureException) exception;
    if (ex instanceof ClientAuthenticationException) {
      return hintWithExplanationException(HintException.HINT_AZURE_AUTHENTICATION_ISSUE,
          ExplanationException.INVALID_PARAMETER,
          new InvalidRequestException(ex.getMessage(), AZURE_AUTHENTICATION_ERROR, USER));
    } else if (ex instanceof KeyVaultErrorException) {
      return hintWithExplanationException(HintException.HINT_AZURE_VAULT_SM_CRUD_DENIED,
          ExplanationException.AZURE_SM_VAULT_ENGINE_PERMISSION,
          new InvalidRequestException(ex.getMessage(), AZURE_KEY_VAULT_OPERATION_ERROR, USER));
    } else {
      return hintWithExplanationException(HintException.HINT_AZURE_GENERIC_ISSUE, ExplanationException.AZURE_INVALID,
          new InvalidRequestException(ex.getMessage(), AZURE_CONFIG_ERROR, USER));
    }
  }
}
