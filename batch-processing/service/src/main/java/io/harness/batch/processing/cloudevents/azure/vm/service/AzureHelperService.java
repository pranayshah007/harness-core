/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.batch.processing.cloudevents.azure.vm.service;

import io.harness.ccm.commons.entities.azure.AzureRecommendation;
import io.harness.ccm.governance.entities.RecommendationAdhocDTO;

import software.wings.beans.AzureAccountAttributes;

import java.util.List;

public interface AzureHelperService {
  List<AzureRecommendation> getRecommendations(String accountId, AzureAccountAttributes request);
  List<String> getValidRegions(String accountId, RecommendationAdhocDTO recommendationAdhocDTO);
}
