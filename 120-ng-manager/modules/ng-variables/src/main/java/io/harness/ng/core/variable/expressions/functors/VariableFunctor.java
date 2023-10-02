/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.variable.expressions.functors;

import io.harness.beans.Scope;
import io.harness.beans.ScopeLevel;
import io.harness.exception.InvalidArgumentsException;
import io.harness.exception.UnknownEnumTypeException;
import io.harness.ng.core.NGAccess;
import io.harness.ng.core.variable.dto.VariableDTO;
import io.harness.ng.core.variable.dto.VariableResponseDTO;
import io.harness.ng.core.variable.services.VariableService;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.sdk.core.execution.expression.SdkFunctor;

import com.google.inject.Inject;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import javax.ws.rs.NotFoundException;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class VariableFunctor implements SdkFunctor {
  public static final String VARIABLE = "variable";
  public static final String VARIABLE_SCOPE_ACCOUNT = "account";
  public static final String VARIABLE_SCOPE_ORG = "org";
  @Inject VariableService variableService;

  @Override
  public Object get(Ambiance ambiance, String... args) {
    log.info("Fetching ng variables with args: {}", Arrays.asList(args));
    NGAccess ngAccess = AmbianceUtils.getNgAccess(ambiance);
    Scope ambianceScope =
        Scope.of(ngAccess.getAccountIdentifier(), ngAccess.getOrgIdentifier(), ngAccess.getProjectIdentifier());
    try {
      ScopeLevel variableScopeLevel = getScope(args[0]);
      validateAccess(ScopeLevel.of(ambianceScope), variableScopeLevel);
      return getVariableValue(ngAccess, variableScopeLevel);
    } catch (UnknownEnumTypeException e) {
      log.info("Argument [{}] is not a valid scope. Assuming it as variable identifier and proceeding", args[0]);
    }
    String identifier = args[0];
    return getVariableValue(ngAccess, ScopeLevel.of(ambianceScope), identifier);
  }

  private Map<String, Object> getVariableValue(NGAccess ngAccess, ScopeLevel variableScopeLevel) {
    log.info("Fetching variables of scope [{}]", variableScopeLevel);
    List<VariableDTO> variableDTOList;
    switch (variableScopeLevel) {
      case ACCOUNT:
        variableDTOList = variableService.list(ngAccess.getAccountIdentifier(), null, null);
        break;
      case ORGANIZATION:
        variableDTOList = variableService.list(ngAccess.getAccountIdentifier(), ngAccess.getOrgIdentifier(), null);
        break;
      case PROJECT:
        variableDTOList = variableService.list(
            ngAccess.getAccountIdentifier(), ngAccess.getOrgIdentifier(), ngAccess.getProjectIdentifier());
        break;
      default:
        throw new UnsupportedOperationException(
            String.format("Variables are not supported in [%s] scope", variableScopeLevel));
    }
    return variableDTOList.stream().collect(
        Collectors.toMap(VariableDTO::getIdentifier, variableDTO -> variableDTO.getVariableConfig().getValue()));
  }

  private Object getVariableValue(NGAccess ngAccess, ScopeLevel scopeLevel, String identifier) {
    log.info("Fetching variable [{}] in scope [{}]", identifier, scopeLevel);
    Optional<VariableResponseDTO> responseDTOOptional;
    switch (scopeLevel) {
      case ACCOUNT:
        responseDTOOptional = variableService.get(ngAccess.getAccountIdentifier(), null, null, identifier);
        break;
      case ORGANIZATION:
        responseDTOOptional =
            variableService.get(ngAccess.getAccountIdentifier(), ngAccess.getOrgIdentifier(), null, identifier);
        break;
      case PROJECT:
        responseDTOOptional = variableService.get(
            ngAccess.getAccountIdentifier(), ngAccess.getOrgIdentifier(), ngAccess.getProjectIdentifier(), identifier);
        break;
      default:
        throw new UnsupportedOperationException(String.format("Variables are not supported in [%s] scope", scopeLevel));
    }
    String notFoundMessage = String.format(
        "Variable with identifier [%s] not found in scope [%s] (account : %s, org: %s, project: %s)", identifier,
        scopeLevel, ngAccess.getAccountIdentifier(), ngAccess.getOrgIdentifier(), ngAccess.getProjectIdentifier());
    if (!responseDTOOptional.isPresent()) {
      throw new NotFoundException(notFoundMessage);
    }
    VariableResponseDTO variableResponse = responseDTOOptional.get();
    if (variableResponse.getVariable() != null && variableResponse.getVariable().getVariableConfig() != null) {
      return responseDTOOptional.get().getVariable().getVariableConfig().getValue();
    } else {
      log.error("Response variable has null data for variable [{}] in scope [{}] (account : {}, org: {}, project: {})",
          identifier, scopeLevel, ngAccess.getAccountIdentifier(), ngAccess.getOrgIdentifier(),
          ngAccess.getProjectIdentifier());
      throw new NotFoundException(notFoundMessage);
    }
  }

  private void validateAccess(ScopeLevel ambianceScopeLevel, ScopeLevel variableScopeLevel) {
    log.info("Validating scope bases access. Ambiance Scope: {} | Variable scope: {}", ambianceScopeLevel,
        variableScopeLevel);
    if (variableScopeLevel.ordinal() > ambianceScopeLevel.ordinal()) {
      throw new InvalidArgumentsException(
          String.format("Variable of %s scope cannot be used at %s scope", variableScopeLevel, ambianceScopeLevel));
    }
  }

  private ScopeLevel getScope(String scope) {
    switch (scope) {
      case VARIABLE_SCOPE_ACCOUNT:
        return ScopeLevel.ACCOUNT;
      case VARIABLE_SCOPE_ORG:
        return ScopeLevel.ORGANIZATION;
      default:
        throw new UnknownEnumTypeException("ScopeLevel", scope);
    }
  }
}
