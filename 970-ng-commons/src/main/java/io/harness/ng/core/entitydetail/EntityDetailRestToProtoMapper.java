/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ng.core.entitydetail;

import static io.harness.annotations.dev.HarnessTeam.DX;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.data.structure.HarnessStringUtils.nullIfEmpty;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.IdentifierRef;
import io.harness.beans.InputSetReference;
import io.harness.beans.NGTemplateReference;
import io.harness.encryption.Scope;
import io.harness.eventsframework.schemas.entity.EntityDetailProtoDTO;
import io.harness.eventsframework.schemas.entity.EntityTypeProtoEnum;
import io.harness.eventsframework.schemas.entity.IdentifierRefProtoDTO;
import io.harness.eventsframework.schemas.entity.InputSetReferenceProtoDTO;
import io.harness.eventsframework.schemas.entity.ScopeProtoEnum;
import io.harness.eventsframework.schemas.entity.TemplateReferenceProtoDTO;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.UnknownEnumTypeException;
import io.harness.ng.core.EntityDetail;
import io.harness.ng.core.event.EntityToEntityProtoHelper;

import com.google.inject.Singleton;
import com.google.protobuf.StringValue;

@Singleton
@OwnedBy(DX)
public class EntityDetailRestToProtoMapper {
  public EntityDetailProtoDTO createEntityDetailDTO(EntityDetail entityDetail) {
    final EntityTypeProtoEnum entityTypeProto =
        EntityToEntityProtoHelper.getEntityTypeFromProto(entityDetail.getType());
    final EntityDetailProtoDTO.Builder entityDetailProtoBuilder =
        EntityDetailProtoDTO.newBuilder().setType(entityTypeProto);
    if (entityDetail.getName() != null) {
      entityDetailProtoBuilder.setName(entityDetail.getName());
    }
    if (entityDetail.getEntityRef() instanceof IdentifierRef) {
      return entityDetailProtoBuilder.setIdentifierRef(createIdentifierRef((IdentifierRef) entityDetail.getEntityRef()))
          .build();
    } else if (entityDetail.getEntityRef() instanceof InputSetReference) {
      return entityDetailProtoBuilder.setInputSetRef(createInputSetRef((InputSetReference) entityDetail.getEntityRef()))
          .build();
    } else if (entityDetail.getEntityRef() instanceof NGTemplateReference) {
      return entityDetailProtoBuilder
          .setTemplateRef(createTemplateRef((NGTemplateReference) entityDetail.getEntityRef()))
          .build();
    }
    throw new InvalidRequestException("Entity detail reference " + entityDetail.getEntityRef() + " is not handled");
  }

  private IdentifierRefProtoDTO createIdentifierRef(IdentifierRef identifierRef) {
    IdentifierRefProtoDTO.Builder builder =
        IdentifierRefProtoDTO.newBuilder()
            .setAccountIdentifier(StringValue.newBuilder().setValue(identifierRef.getAccountIdentifier()).build())
            .setScope(mapToScopeProtoEnum(identifierRef.getScope()))
            .setIdentifier(StringValue.of(identifierRef.getIdentifier()));
    if (isNotEmpty(identifierRef.getOrgIdentifier())) {
      builder.setOrgIdentifier(StringValue.of(nullIfEmpty(identifierRef.getOrgIdentifier())));
    }
    if (isNotEmpty(identifierRef.getProjectIdentifier())) {
      builder.setProjectIdentifier(StringValue.of(nullIfEmpty(identifierRef.getProjectIdentifier())));
    }
    return builder.build();
  }

  private ScopeProtoEnum mapToScopeProtoEnum(Scope scope) {
    switch (scope) {
      case ACCOUNT:
        return ScopeProtoEnum.ACCOUNT;
      case ORG:
        return ScopeProtoEnum.ORG;
      case PROJECT:
        return ScopeProtoEnum.PROJECT;
      case UNKNOWN:
        return ScopeProtoEnum.UNKNOWN;
      default:
        throw new UnknownEnumTypeException("scope", String.valueOf(scope));
    }
  }

  private InputSetReferenceProtoDTO createInputSetRef(InputSetReference inputSetReference) {
    return InputSetReferenceProtoDTO.newBuilder()
        .setAccountIdentifier(StringValue.of(inputSetReference.getAccountIdentifier()))
        .setIdentifier(StringValue.of(inputSetReference.getIdentifier()))
        .setOrgIdentifier(StringValue.of(nullIfEmpty(inputSetReference.getOrgIdentifier())))
        .setProjectIdentifier(StringValue.of(nullIfEmpty(inputSetReference.getProjectIdentifier())))
        .setPipelineIdentifier(StringValue.of(inputSetReference.getPipelineIdentifier()))
        .build();
  }

  private TemplateReferenceProtoDTO createTemplateRef(NGTemplateReference templateReference) {
    TemplateReferenceProtoDTO.Builder builder =
        TemplateReferenceProtoDTO.newBuilder()
            .setAccountIdentifier(StringValue.of(templateReference.getAccountIdentifier()))
            .setIdentifier(StringValue.of(templateReference.getIdentifier()))
            .setScope(mapToScopeProtoEnum(templateReference.getScope()))
            .setVersionLabel(StringValue.of(templateReference.getVersionLabel()));
    if (isNotEmpty(templateReference.getOrgIdentifier())) {
      builder.setOrgIdentifier(StringValue.of(nullIfEmpty(templateReference.getOrgIdentifier())));
    }
    if (isNotEmpty(templateReference.getProjectIdentifier())) {
      builder.setProjectIdentifier(StringValue.of(nullIfEmpty(templateReference.getProjectIdentifier())));
    }
    return builder.build();
  }
}
