/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.engine.expressions.functors;

import static io.harness.data.structure.EmptyPredicate.isEmpty;

import io.harness.accesscontrol.NGAccessDeniedException;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.IdentifierRef;
import io.harness.engine.utils.FunctorUtils;
import io.harness.eventsframework.protohelper.IdentifierRefProtoDTOHelper;
import io.harness.eventsframework.schemas.entity.EntityDetailProtoDTO;
import io.harness.eventsframework.schemas.entity.EntityTypeProtoEnum;
import io.harness.exception.EngineFunctorException;
import io.harness.expression.functors.ExpressionFunctor;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.rbac.PipelineRbacHelper;
import io.harness.pms.security.PmsSecurityContextEventGuard;
import io.harness.utils.IdentifierRefHelper;

import java.util.Set;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@OwnedBy(HarnessTeam.PIPELINE)
public class SecretFunctorWithRbac implements ExpressionFunctor {
  PipelineRbacHelper pipelineRbacHelper;
  Ambiance ambiance;

  public SecretFunctorWithRbac(Ambiance ambiance, PipelineRbacHelper pipelineRbacHelper) {
    this.ambiance = ambiance;
    this.pipelineRbacHelper = pipelineRbacHelper;
  }

  @SneakyThrows
  public Object getValue(String secretIdentifier) {
    validateSecret(secretIdentifier);
    try (PmsSecurityContextEventGuard securityContextEventGuard = new PmsSecurityContextEventGuard(ambiance)) {
      IdentifierRef identifierRef =
          IdentifierRefHelper.getIdentifierRef(secretIdentifier, AmbianceUtils.getAccountId(ambiance),
              AmbianceUtils.getOrgIdentifier(ambiance), AmbianceUtils.getProjectIdentifier(ambiance));
      Set<EntityDetailProtoDTO> entityDetails =
          Set.of(EntityDetailProtoDTO.newBuilder()
                     .setType(EntityTypeProtoEnum.SECRETS)
                     .setIdentifierRef(IdentifierRefProtoDTOHelper.fromIdentifierRef(identifierRef))
                     .build());
      pipelineRbacHelper.checkRuntimePermissions(ambiance, entityDetails);
      return FunctorUtils.getSecretExpression(ambiance.getExpressionFunctorToken(), secretIdentifier);
    } catch (NGAccessDeniedException exception) {
      log.error("Encountered error while resolving secret ", exception);
      throw new EngineFunctorException(exception);
    } catch (Exception ex) {
      log.error("Encountered unknown error while resolving secret ", ex);
      throw new EngineFunctorException("Encountered unknown error while resolving secret", ex);
    }
  }

  public void validateSecret(String secretIdentifier) {
    if (isEmpty(secretIdentifier)) {
      throw new EngineFunctorException("Empty secret identifier values are not supported");
    }
  }
}
