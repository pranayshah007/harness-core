/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.helm;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.connector.ConnectivityStatus;
import io.harness.connector.ConnectorValidationResult;
import io.harness.delegate.beans.connector.helm.OciHelmAuthType;
import io.harness.delegate.beans.connector.helm.OciHelmAuthenticationDTO;
import io.harness.delegate.beans.connector.helm.OciHelmConnectorDTO;
import io.harness.delegate.beans.connector.helm.OciHelmValidationParams;
import io.harness.errorhandling.NGErrorHelper;
import io.harness.rule.Owner;
import io.harness.rule.OwnerRule;

import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;

@OwnedBy(CDP)
public class OciHelmValidationHandlerTest {
  @InjectMocks OciHelmValidationHandler ociHelmValidationHandler;
  @Spy HelmTaskHelperBase helmTaskHelperBase;
  @Mock NGErrorHelper ngErrorHelper;
  private static final String accountId = "accountId";

  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = OwnerRule.PRATYUSH)
  @Category(UnitTests.class)
  public void testValidateAnonymousAuthType() {
    OciHelmValidationParams ociHelmValidationParams =
        OciHelmValidationParams.builder()
            .connectorName("testOciAnonymous")
            .ociHelmConnectorDTO(
                OciHelmConnectorDTO.builder()
                    .helmRepoUrl("public.ecr.aws")
                    .auth(OciHelmAuthenticationDTO.builder().authType(OciHelmAuthType.ANONYMOUS).build())
                    .build())
            .build();
    ConnectorValidationResult connectorValidationResult =
        ociHelmValidationHandler.validate(ociHelmValidationParams, accountId);
    assertThat(connectorValidationResult.getStatus()).isEqualTo(ConnectivityStatus.SUCCESS);
  }

  @Test
  @Owner(developers = OwnerRule.PRATYUSH)
  @Category(UnitTests.class)
  public void testValidateAnonymousAuthTypeFailed() {
    OciHelmValidationParams ociHelmValidationParams =
        OciHelmValidationParams.builder()
            .connectorName("testOciAnonymous")
            .ociHelmConnectorDTO(
                OciHelmConnectorDTO.builder()
                    .helmRepoUrl("invalidUrl")
                    .auth(OciHelmAuthenticationDTO.builder().authType(OciHelmAuthType.ANONYMOUS).build())
                    .build())
            .build();
    ConnectorValidationResult connectorValidationResult =
        ociHelmValidationHandler.validate(ociHelmValidationParams, accountId);
    assertThat(connectorValidationResult.getStatus()).isEqualTo(ConnectivityStatus.FAILURE);
  }
}
