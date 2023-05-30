/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.utils;

import static io.harness.data.structure.EmptyPredicate.isEmpty;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.IdentifierRef;
import io.harness.ci.buildstate.ConnectorUtils;
import io.harness.delegate.beans.ci.pod.ConnectorDetails;
import io.harness.exception.FunctorException;
import io.harness.expression.functors.ExpressionFunctor;
import io.harness.ng.core.NGAccess;
import io.harness.utils.IdentifierRefHelper;

import java.util.HashMap;
import java.util.Map;
import lombok.Builder;
import lombok.Getter;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.digest.DigestUtils;

@Builder
@Slf4j
@OwnedBy(HarnessTeam.CI)
public class GithubApiFunctor implements ExpressionFunctor {
  private final ConnectorUtils connectorUtils;
  private final NGAccess ngAccess;
  private final GithubApiFunctor.Config githubApiFunctorConfig;

  @Getter @Builder.Default private final Map<String, ConnectorDetails> connectorDetailsMap = new HashMap<>();

  public Object token() {
    if (isEmpty(githubApiFunctorConfig.getCodeBaseConnectorRef())) {
      throw new FunctorException("Cannot evaluate expression with empty codebase connector : <+gitApp.token()>");
    }
    return token(githubApiFunctorConfig.getCodeBaseConnectorRef());
  }

  public Object token(String connectorRef) {
    try {
      if (isEmpty(connectorRef)) {
        throw new FunctorException(
            "Cannot evaluate expression with empty connector: <+gitApp.token(\"" + connectorRef + "\")>");
      }
      IdentifierRef identifierRef = IdentifierRefHelper.getIdentifierRef(
          connectorRef, ngAccess.getAccountIdentifier(), ngAccess.getOrgIdentifier(), ngAccess.getProjectIdentifier());
      identifierRef.getFullyQualifiedName();
      String tokenVariable = generateTokenVariableName(connectorRef);

      if (githubApiFunctorConfig.isFetchConnector()) {
        if (connectorUtils == null) {
          throw new FunctorException("ConnectorUtils must be provided to functor when fetch connector is true");
        }
        ConnectorDetails connectorDetails = connectorUtils.getConnectorDetails(ngAccess, connectorRef, true);
        connectorDetailsMap.put(tokenVariable, connectorDetails);
      }

      return "${" + tokenVariable + "}";
    } catch (Exception ex) {
      throw new FunctorException("Error occurred while evaluating the secret [" + connectorRef + "]", ex);
    }
  }

  private String generateTokenVariableName(String fullyQualifiedConnectorId) {
    String sha256hex = DigestUtils.sha256Hex(fullyQualifiedConnectorId).substring(0, 10);
    return "GIT_APP_TOKEN_" + sha256hex.toUpperCase();
  }

  @Value
  @Builder
  public static class Config {
    String codeBaseConnectorRef;
    boolean fetchConnector;
  }
}
