/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.engine.expressions.functors;

import static io.harness.rule.OwnerRule.SHALINI;

import static junit.framework.TestCase.assertEquals;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;

import io.harness.CategoryTest;
import io.harness.accesscontrol.NGAccessDeniedException;
import io.harness.accesscontrol.acl.api.PermissionCheckDTO;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.eventsframework.protohelper.IdentifierRefProtoDTOHelper;
import io.harness.eventsframework.schemas.entity.IdentifierRefProtoDTO;
import io.harness.exception.EngineFunctorException;
import io.harness.exception.WingsException;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.rbac.PipelineRbacHelper;
import io.harness.rule.Owner;

import java.util.EnumSet;
import java.util.List;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
@OwnedBy(HarnessTeam.PIPELINE)
public class SecretFunctorWithRbacTest extends CategoryTest {
  Ambiance ambiance = Ambiance.newBuilder()
                          .putSetupAbstractions("accountId", "accountId")
                          .putSetupAbstractions("orgIdentifier", "orgIdentifier")
                          .putSetupAbstractions("projectIdentifier", "projectIdentifier")
                          .setExpressionFunctorToken(234)
                          .build();
  @Mock PipelineRbacHelper pipelineRbacHelper;

  @Test
  @Owner(developers = SHALINI)
  @Category(UnitTests.class)
  public void testGetValue() {
    SecretFunctorWithRbac secretFunctorWithRbac = new SecretFunctorWithRbac(ambiance, pipelineRbacHelper);
    MockedStatic<IdentifierRefProtoDTOHelper> mockedStatic = Mockito.mockStatic(IdentifierRefProtoDTOHelper.class);
    mockedStatic.when(() -> IdentifierRefProtoDTOHelper.fromIdentifierRef(any()))
        .thenReturn(IdentifierRefProtoDTO.newBuilder().build());
    doThrow(new NGAccessDeniedException("could not resolve secret as user doesn't have access permission",
                EnumSet.of(WingsException.ReportTarget.UNIVERSAL),
                List.of(PermissionCheckDTO.builder()
                            .resourceType("secret")
                            .resourceIdentifier("secret1")
                            .permission("core_secret_access")
                            .build())))
        .when(pipelineRbacHelper)
        .checkRuntimePermissions(any(), any());
    assertThatThrownBy(() -> secretFunctorWithRbac.getValue("secret1"))
        .isInstanceOf(EngineFunctorException.class)
        .hasMessage("could not resolve secret as user doesn't have access permission");
    doNothing().when(pipelineRbacHelper).checkRuntimePermissions(any(), any());
    assertEquals("${ngSecretManager.obtain(\""
            + "secret1"
            + "\", " + ambiance.getExpressionFunctorToken() + ")}",
        secretFunctorWithRbac.getValue("secret1"));
  }
}
