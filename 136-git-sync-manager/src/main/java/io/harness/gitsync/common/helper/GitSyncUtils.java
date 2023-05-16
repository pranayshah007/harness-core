/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.gitsync.common.helper;

import static io.harness.annotations.dev.HarnessTeam.DX;
import static io.harness.authorization.AuthorizationServiceHeader.GIT_SYNC_SERVICE;

import io.harness.EntityType;
import io.harness.annotations.dev.OwnedBy;
import io.harness.connector.ManagerExecutable;
import io.harness.delegate.beans.connector.scm.ScmConnector;
import io.harness.exception.InvalidRequestException;
import io.harness.manage.GlobalContextManager;
import io.harness.security.PrincipalContextData;
import io.harness.security.dto.PrincipalType;
import io.harness.security.dto.ServicePrincipal;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import java.io.IOException;
import java.util.NoSuchElementException;
import java.util.Optional;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

@UtilityClass
@Slf4j
@OwnedBy(DX)
public class GitSyncUtils {
  static ObjectMapper objectMapper = new ObjectMapper(new YAMLFactory());

  public EntityType getEntityTypeFromYaml(String yaml) throws InvalidRequestException {
    try {
      final JsonNode jsonNode = objectMapper.readTree(yaml);
      String rootNode = jsonNode.fields().next().getKey();
      return EntityType.getEntityTypeFromYamlRootName(rootNode);
    } catch (IOException | NoSuchElementException e) {
      log.error("Could not process the yaml {}", yaml, e);
      throw new InvalidRequestException("Unable to parse yaml", e);
    }
  }

  public void setGitSyncServicePrincipal() {
    GlobalContextManager.upsertGlobalContextRecord(
        PrincipalContextData.builder().principal(new ServicePrincipal(GIT_SYNC_SERVICE.getServiceId())).build());
  }

  public void setCurrentPrincipalContext(PrincipalContextData principal) {
    if (principal != null) {
      GlobalContextManager.upsertGlobalContextRecord(principal);
    }
  }

  public boolean isExecuteOnDelegate(ScmConnector scmConnector) {
    Boolean executeOnDelegate = Boolean.TRUE;

    if (scmConnector instanceof ManagerExecutable) {
      executeOnDelegate = ((ManagerExecutable) scmConnector).getExecuteOnDelegate();
    }
    return executeOnDelegate;
  }

  public Optional<String> getUserIdentifier() {
    PrincipalContextData currentPrincipal = GlobalContextManager.get(PrincipalContextData.PRINCIPAL_CONTEXT);
    Optional<String> userIdentifier = Optional.empty();
    if (currentPrincipal != null && currentPrincipal.getPrincipal().getType() == PrincipalType.USER) {
      userIdentifier = Optional.of(currentPrincipal.getPrincipal().getName());
    }
    return userIdentifier;
  }
}
