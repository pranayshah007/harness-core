/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.template;

import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.IdentifierRef;
import io.harness.beans.NGTemplateReference;
import io.harness.encryption.Scope;
import io.harness.eventsframework.schemas.entity.ScopeProtoEnum;
import io.harness.eventsframework.schemas.entity.TemplateReferenceProtoDTO;

import com.google.protobuf.StringValue;
import java.util.Map;
import lombok.experimental.UtilityClass;

@UtilityClass
@OwnedBy(HarnessTeam.CDC)
public class TemplateReferenceProtoUtils {
  public TemplateReferenceProtoDTO createTemplateReferenceProtoFromIdentifierRef(
      IdentifierRef identifierRef, String versionLabel) {
    return createTemplateReferenceProto(identifierRef.getAccountIdentifier(), identifierRef.getOrgIdentifier(),
        identifierRef.getProjectIdentifier(), identifierRef.getIdentifier(), identifierRef.getScope(), versionLabel,
        null);
  }

  public TemplateReferenceProtoDTO createTemplateReferenceProtoFromIdentifierRef(
      IdentifierRef identifierRef, String versionLabel, Map<String, String> metaData) {
    return createTemplateReferenceProto(identifierRef.getAccountIdentifier(), identifierRef.getOrgIdentifier(),
        identifierRef.getProjectIdentifier(), identifierRef.getIdentifier(), identifierRef.getScope(), versionLabel,
        metaData);
  }

  public TemplateReferenceProtoDTO createTemplateReferenceProtoFromTemplateReference(
      NGTemplateReference templateReference) {
    return createTemplateReferenceProto(templateReference.getAccountIdentifier(), templateReference.getOrgIdentifier(),
        templateReference.getProjectIdentifier(), templateReference.getIdentifier(), templateReference.getScope(),
        templateReference.getVersionLabel(), null);
  }

  public TemplateReferenceProtoDTO createTemplateReferenceProto(String accountId, String orgIdentifier,
      String projectIdentifier, String templateIdentifier, Scope scope, String versionLabel,
      Map<String, String> metaData) {
    TemplateReferenceProtoDTO.Builder templateRefBuilder = TemplateReferenceProtoDTO.newBuilder()
                                                               .setIdentifier(StringValue.of(templateIdentifier))
                                                               .setAccountIdentifier(StringValue.of(accountId))
                                                               .setScope(ScopeProtoEnum.valueOf(scope.toString()))
                                                               .setVersionLabel(StringValue.of(versionLabel));

    if (isNotEmpty(orgIdentifier)) {
      templateRefBuilder.setOrgIdentifier(StringValue.of(orgIdentifier));
    }

    if (isNotEmpty(projectIdentifier)) {
      templateRefBuilder.setProjectIdentifier(StringValue.of(projectIdentifier));
    }

    if (isNotEmpty(metaData)) {
      templateRefBuilder.putAllMetadata(metaData);
    }

    return templateRefBuilder.build();
  }
}
