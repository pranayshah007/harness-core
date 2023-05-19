/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ngtriggers.utils;

import static io.harness.data.structure.HarnessStringUtils.nullIfEmpty;
import static io.harness.ngtriggers.beans.source.ManifestType.HELM_MANIFEST;
import static io.harness.ngtriggers.beans.source.NGTriggerType.ARTIFACT;
import static io.harness.ngtriggers.beans.source.NGTriggerType.MANIFEST;
import static io.harness.ngtriggers.beans.source.NGTriggerType.MULTI_ARTIFACT;
import static io.harness.ngtriggers.beans.source.NGTriggerType.WEBHOOK;

import io.harness.beans.IdentifierRef;
import io.harness.data.structure.EmptyPredicate;
import io.harness.eventsframework.protohelper.IdentifierRefProtoDTOHelper;
import io.harness.eventsframework.schemas.entity.EntityDetailProtoDTO;
import io.harness.eventsframework.schemas.entity.EntityTypeProtoEnum;
import io.harness.eventsframework.schemas.entity.IdentifierRefProtoDTO;
import io.harness.eventsframework.schemas.entity.InputSetReferenceProtoDTO;
import io.harness.ngtriggers.beans.config.NGTriggerConfigV2;
import io.harness.ngtriggers.beans.source.artifact.ArtifactTriggerConfig;
import io.harness.ngtriggers.beans.source.artifact.ArtifactTypeSpec;
import io.harness.ngtriggers.beans.source.artifact.HelmManifestSpec;
import io.harness.ngtriggers.beans.source.artifact.ManifestTriggerConfig;
import io.harness.ngtriggers.beans.source.artifact.MultiArtifactTriggerConfig;
import io.harness.ngtriggers.beans.source.webhook.v2.WebhookTriggerConfigV2;
import io.harness.utils.IdentifierRefHelper;

import com.google.inject.Inject;
import com.google.protobuf.StringValue;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@AllArgsConstructor(access = AccessLevel.PACKAGE, onConstructor = @__({ @Inject }))
@Slf4j
public class TriggerReferenceHelper {
  public List<EntityDetailProtoDTO> getReferences(String accountId, NGTriggerConfigV2 ngTriggerConfigV2) {
    List<EntityDetailProtoDTO> entityDetailProtoDTOList = new ArrayList<>();
    if (EmptyPredicate.isNotEmpty(ngTriggerConfigV2.getEncryptedWebhookSecretIdentifier())) {
      entityDetailProtoDTOList.add(getReferredSecretDetails(ngTriggerConfigV2, accountId));
    }
    if (EmptyPredicate.isNotEmpty(ngTriggerConfigV2.getInputSetRefs())) {
      entityDetailProtoDTOList.addAll(getReferredInputSetRefsDetails(ngTriggerConfigV2, accountId));
    }
    Set<String> connectorRefs = getConnectorRefs(ngTriggerConfigV2);
    for (String connectorRef : connectorRefs) {
      if (EmptyPredicate.isNotEmpty(connectorRefs)) {
        entityDetailProtoDTOList.add(getReferredConnectorDetails(ngTriggerConfigV2, accountId, connectorRef));
      }
    }
    return entityDetailProtoDTOList;
  }

  private List<EntityDetailProtoDTO> getReferredInputSetRefsDetails(
      NGTriggerConfigV2 ngTriggerConfigV2, String accountId) {
    List<EntityDetailProtoDTO> entityDetailProtoDTOList = new ArrayList<>();
    for (String inputSetRef : ngTriggerConfigV2.getInputSetRefs()) {
      InputSetReferenceProtoDTO inputSetReferenceProtoDTO =
          InputSetReferenceProtoDTO.newBuilder()
              .setAccountIdentifier(StringValue.of(accountId))
              .setIdentifier(StringValue.of(inputSetRef))
              .setOrgIdentifier(StringValue.of(nullIfEmpty(ngTriggerConfigV2.getOrgIdentifier())))
              .setProjectIdentifier(StringValue.of(nullIfEmpty(ngTriggerConfigV2.getProjectIdentifier())))
              .setPipelineIdentifier(StringValue.of(ngTriggerConfigV2.getPipelineIdentifier()))
              .build();
      entityDetailProtoDTOList.add(EntityDetailProtoDTO.newBuilder()
                                       .setInputSetRef(inputSetReferenceProtoDTO)
                                       .setType(EntityTypeProtoEnum.INPUT_SETS)
                                       .build());
    }
    return entityDetailProtoDTOList;
  }

  private EntityDetailProtoDTO getReferredSecretDetails(NGTriggerConfigV2 ngTriggerConfigV2, String accountId) {
    IdentifierRef secretIdentifierRef =
        IdentifierRefHelper.getIdentifierRef(ngTriggerConfigV2.getEncryptedWebhookSecretIdentifier(), accountId,
            ngTriggerConfigV2.getOrgIdentifier(), ngTriggerConfigV2.getProjectIdentifier());
    IdentifierRefProtoDTO secretReference =
        IdentifierRefProtoDTOHelper.createIdentifierRefProtoDTO(accountId, secretIdentifierRef.getOrgIdentifier(),
            secretIdentifierRef.getProjectIdentifier(), secretIdentifierRef.getIdentifier());

    return EntityDetailProtoDTO.newBuilder()
        .setIdentifierRef(secretReference)
        .setType(EntityTypeProtoEnum.SECRETS)
        .build();
  }

  private EntityDetailProtoDTO getReferredConnectorDetails(
      NGTriggerConfigV2 ngTriggerConfigV2, String accountId, String connectorRef) {
    IdentifierRef connectorIdentifierRef = IdentifierRefHelper.getIdentifierRef(
        connectorRef, accountId, ngTriggerConfigV2.getOrgIdentifier(), ngTriggerConfigV2.getProjectIdentifier());
    IdentifierRefProtoDTO connectorReference =
        IdentifierRefProtoDTOHelper.createIdentifierRefProtoDTO(accountId, connectorIdentifierRef.getOrgIdentifier(),
            connectorIdentifierRef.getProjectIdentifier(), connectorIdentifierRef.getIdentifier());

    return EntityDetailProtoDTO.newBuilder()
        .setIdentifierRef(connectorReference)
        .setType(EntityTypeProtoEnum.CONNECTORS)
        .build();
  }

  private Set<String> getConnectorRefs(NGTriggerConfigV2 ngTriggerConfigV2) {
    Set<String> connectorRefs = new HashSet<>();
    if (ngTriggerConfigV2.getSource().getType() == WEBHOOK) {
      WebhookTriggerConfigV2 webhookTriggerConfigV2 = (WebhookTriggerConfigV2) ngTriggerConfigV2.getSource().getSpec();
      connectorRefs.add(webhookTriggerConfigV2.getSpec().fetchGitAware().fetchConnectorRef());
    } else if (ngTriggerConfigV2.getSource().getType() == ARTIFACT) {
      ArtifactTriggerConfig artifactTriggerConfig = (ArtifactTriggerConfig) ngTriggerConfigV2.getSource().getSpec();
      connectorRefs.add(artifactTriggerConfig.getSpec().fetchConnectorRef());
    } else if (ngTriggerConfigV2.getSource().getType() == MULTI_ARTIFACT) {
      MultiArtifactTriggerConfig artifactTriggerConfig =
          (MultiArtifactTriggerConfig) ngTriggerConfigV2.getSource().getSpec();
      for (ArtifactTypeSpec artifactSpec : artifactTriggerConfig.getSources()) {
        connectorRefs.add(artifactSpec.fetchConnectorRef());
      }
    } else if (ngTriggerConfigV2.getSource().getType() == MANIFEST) {
      ManifestTriggerConfig manifestTriggerConfig = (ManifestTriggerConfig) ngTriggerConfigV2.getSource().getSpec();
      if (manifestTriggerConfig.getType() == HELM_MANIFEST) {
        HelmManifestSpec helmManifestSpec = (HelmManifestSpec) manifestTriggerConfig.getSpec();
        connectorRefs.add(helmManifestSpec.getStore().fetchConnectorRef());
      }
    }
    return connectorRefs;
  }
}
