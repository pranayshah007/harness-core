/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.polling.mapper;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.data.structure.EmptyPredicate;
import io.harness.exception.InvalidRequestException;
import io.harness.polling.bean.PollingDocument;
import io.harness.polling.bean.PollingDocument.PollingDocumentBuilder;
import io.harness.polling.bean.PollingInfo;
import io.harness.polling.bean.PollingType;
import io.harness.polling.contracts.Category;
import io.harness.polling.contracts.PollingItem;
import io.harness.polling.contracts.PollingPayloadData;
import io.harness.polling.contracts.Qualifier;

import com.google.inject.Inject;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_TRIGGERS})
@OwnedBy(HarnessTeam.CDC)
public class PollingDocumentMapper {
  @Inject PollingInfoBuilderRegistry pollingInfoBuilderRegistry;

  public PollingDocument toPollingDocument(PollingItem pollingItem) {
    PollingInfo pollingInfo;
    PollingDocumentBuilder pollingDocumentBuilder = toBasePollingDocument(pollingItem);
    PollingPayloadData pollingPayloadData = pollingItem.getPollingPayloadData();
    Optional<PollingInfoBuilder> pollingInfoBuilder =
        pollingInfoBuilderRegistry.getPollingInfoBuilder(pollingPayloadData.getType());
    if (pollingInfoBuilder.isPresent()) {
      pollingInfo = pollingInfoBuilder.get().toPollingInfo(pollingPayloadData);
    } else {
      throw new InvalidRequestException("Unsupported polling payload type " + pollingPayloadData.getType());
    }
    return pollingDocumentBuilder.pollingInfo(pollingInfo).build();
  }

  public PollingDocument toPollingDocumentWithoutPollingInfo(PollingItem pollingItem) {
    return toBasePollingDocument(pollingItem).build();
  }

  private PollingDocumentBuilder toBasePollingDocument(PollingItem pollingItem) {
    PollingDocumentBuilder pollingDocumentBuilder = PollingDocument.builder();
    final Category category = pollingItem.getCategory();
    switch (category) {
      case MANIFEST:
        pollingDocumentBuilder.pollingType(PollingType.MANIFEST);
        break;
      case ARTIFACT:
        pollingDocumentBuilder.pollingType(PollingType.ARTIFACT);
        break;
      case GITPOLLING:
        pollingDocumentBuilder.pollingType(PollingType.WEBHOOK_POLLING);
        break;
      default:
        throw new InvalidRequestException("Unsupported category type " + category);
    }

    Qualifier qualifier = pollingItem.getQualifier();
    String signature = pollingItem.getSignature();
    return pollingDocumentBuilder.accountId(qualifier.getAccountId())
        .orgIdentifier(qualifier.getOrganizationId())
        .projectIdentifier(qualifier.getProjectId())
        .signatures(Collections.singletonList(signature))
        .signaturesLock(Map.of(signature, pollingItem.getSignaturesToLockList()))
        .uuid(EmptyPredicate.isEmpty(pollingItem.getPollingDocId()) ? null : pollingItem.getPollingDocId())
        .failedAttempts(0);
  }
}
