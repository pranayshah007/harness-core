/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.eventsframework.protohelper;

import static io.harness.annotations.dev.HarnessTeam.PL;

import static org.apache.commons.lang3.StringUtils.isNotBlank;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.IdentifierRef;
import io.harness.data.structure.EmptyPredicate;
import io.harness.encryption.Scope;
import io.harness.eventsframework.schemas.entity.IdentifierRefProtoDTO;
import io.harness.eventsframework.schemas.entity.ScopeProtoEnum;
import io.harness.exception.InvalidRequestException;
import io.harness.scope.ScopeHelper;

import com.google.inject.Singleton;
import com.google.protobuf.StringValue;
import java.util.Map;

@OwnedBy(PL)
@Singleton
public class IdentifierRefProtoDTOHelper {
  public static IdentifierRefProtoDTO createIdentifierRefProtoDTO(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, String identifier) {
    return createIdentifierRefProtoDTO(accountIdentifier, orgIdentifier, projectIdentifier, identifier, null);
  }

  public static IdentifierRefProtoDTO createIdentifierRefProtoDTO(String accountIdentifier, String orgIdentifier,
      String projectIdentifier, String identifier, Map<String, String> metadata) {
    Scope scope = ScopeHelper.getScope(accountIdentifier, orgIdentifier, projectIdentifier);
    if (scope == null) {
      throw new InvalidRequestException("Scope can't be null");
    }
    IdentifierRefProtoDTO.Builder identifierRefBuilder = IdentifierRefProtoDTO.newBuilder()
                                                             .setIdentifier(StringValue.of(identifier))
                                                             .setAccountIdentifier(StringValue.of(accountIdentifier))
                                                             .setScope(ScopeProtoEnum.valueOf(scope.toString()));
    if (isNotBlank(orgIdentifier)) {
      identifierRefBuilder.setOrgIdentifier(StringValue.of(orgIdentifier));
    }

    if (isNotBlank(projectIdentifier)) {
      identifierRefBuilder.setProjectIdentifier(StringValue.of(projectIdentifier));
    }

    if (EmptyPredicate.isNotEmpty(metadata)) {
      identifierRefBuilder.putAllMetadata(metadata);
    }

    return identifierRefBuilder.build();
  }

  public static IdentifierRefProtoDTO fromIdentifierRef(IdentifierRef identifierRef) {
    return createIdentifierRefProtoDTO(identifierRef.getAccountIdentifier(), identifierRef.getOrgIdentifier(),
        identifierRef.getProjectIdentifier(), identifierRef.getIdentifier());
  }
}
