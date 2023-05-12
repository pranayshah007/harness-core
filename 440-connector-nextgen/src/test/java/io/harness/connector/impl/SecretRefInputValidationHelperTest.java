/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.connector.impl;

import static io.harness.annotations.dev.HarnessTeam.CI;
import static io.harness.rule.OwnerRule.DEV_MITTAL;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.encryption.Scope;
import io.harness.encryption.SecretRefData;
import io.harness.exception.InvalidRequestException;
import io.harness.ng.core.BaseNGAccess;
import io.harness.ng.core.NGAccess;
import io.harness.ng.core.api.SecretCrudService;
import io.harness.ng.core.dto.secrets.SecretResponseWrapper;
import io.harness.rule.Owner;

import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@Slf4j
@OwnedBy(CI)
public class SecretRefInputValidationHelperTest {
  @Mock private SecretCrudService secretCrudService;
  @InjectMocks SecretRefInputValidationHelper secretRefInputValidationHelper;

  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = DEV_MITTAL)
  @Category(UnitTests.class)
  public void validateOrgLevel() {
    Optional<SecretResponseWrapper> secretResponseWrapper = Optional.of(SecretResponseWrapper.builder().build());
    when(secretCrudService.get(any(), any(), any(), any())).thenReturn(secretResponseWrapper);
    SecretRefData secretRefData = SecretRefData.builder().identifier("secret").scope(Scope.ACCOUNT).build();
    NGAccess ngAccess = BaseNGAccess.builder().accountIdentifier("account").build();
    secretRefInputValidationHelper.validateTheSecretInput(secretRefData, ngAccess);
  }

  @Test
  @Owner(developers = DEV_MITTAL)
  @Category(UnitTests.class)
  public void validateProjectLevel() {
    Optional<SecretResponseWrapper> secretResponseWrapper = Optional.of(SecretResponseWrapper.builder().build());
    when(secretCrudService.get(any(), any(), any(), any())).thenReturn(secretResponseWrapper);
    SecretRefData secretRefData = SecretRefData.builder().identifier("secret").scope(Scope.PROJECT).build();
    NGAccess ngAccess =
        BaseNGAccess.builder().accountIdentifier("account").orgIdentifier("org").projectIdentifier("project").build();
    secretRefInputValidationHelper.validateTheSecretInput(secretRefData, ngAccess);
  }

  @Test(expected = InvalidRequestException.class)
  @Owner(developers = DEV_MITTAL)
  @Category(UnitTests.class)
  public void validateProjectLevelException() {
    Optional<SecretResponseWrapper> secretResponseWrapper = Optional.of(SecretResponseWrapper.builder().build());
    when(secretCrudService.get(any(), any(), any(), any())).thenReturn(secretResponseWrapper);
    SecretRefData secretRefData = SecretRefData.builder().identifier("secret").scope(Scope.PROJECT).build();
    NGAccess ngAccess = BaseNGAccess.builder().accountIdentifier("account").build();
    secretRefInputValidationHelper.validateTheSecretInput(secretRefData, ngAccess);
  }

  @Test(expected = InvalidRequestException.class)
  @Owner(developers = DEV_MITTAL)
  @Category(UnitTests.class)
  public void validateProjectLevelException_() {
    SecretRefData secretRefData = SecretRefData.builder().identifier("secret").scope(Scope.PROJECT).build();
    NGAccess ngAccess = BaseNGAccess.builder().accountIdentifier("account").build();
    secretRefInputValidationHelper.validateTheSecretInput(secretRefData, ngAccess);
  }
}
