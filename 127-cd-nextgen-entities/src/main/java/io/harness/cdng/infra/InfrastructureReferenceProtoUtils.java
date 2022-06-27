/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cdng.infra;

import com.google.protobuf.StringValue;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.IdentifierRef;
import io.harness.beans.NGTemplateReference;
import io.harness.encryption.Scope;
import io.harness.eventsframework.schemas.entity.ScopeProtoEnum;
import io.harness.eventsframework.schemas.entity.InfrastructureReferenceProtoDTO;
import lombok.experimental.UtilityClass;

import java.util.Map;

import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

@UtilityClass
@OwnedBy(HarnessTeam.CDC)
public class InfrastructureReferenceProtoUtils {
  public InfrastructureReferenceProtoDTO createInfrastructureReferenceProtoFromIdentifierRef(
      IdentifierRef identifierRef, String versionLabel) {
    return createInfrastructureReferenceProto(identifierRef.getAccountIdentifier(), identifierRef.getOrgIdentifier(),
        identifierRef.getProjectIdentifier(), identifierRef.getIdentifier(), identifierRef.getScope(), versionLabel);
  }

  public InfrastructureReferenceProtoDTO createInfrastructureReferenceProto(String accountId, String orgIdentifier,
                                                                      String projectIdentifier, String templateIdentifier, Scope scope, String versionLabel) {
    InfrastructureReferenceProtoDTO.Builder templateRefBuilder = InfrastructureReferenceProtoDTO.newBuilder()
                                                               .setIdentifier(StringValue.of(templateIdentifier))
                                                               .setAccountIdentifier(StringValue.of(accountId))
                                                               .setScope(ScopeProtoEnum.valueOf(scope.toString()))
                                                               .setEnvironmentIdentifier(StringValue.of(versionLabel));

    if (isNotEmpty(orgIdentifier)) {
      templateRefBuilder.setOrgIdentifier(StringValue.of(orgIdentifier));
    }

    if (isNotEmpty(projectIdentifier)) {
      templateRefBuilder.setProjectIdentifier(StringValue.of(projectIdentifier));
    }

    return templateRefBuilder.build();
  }
}
