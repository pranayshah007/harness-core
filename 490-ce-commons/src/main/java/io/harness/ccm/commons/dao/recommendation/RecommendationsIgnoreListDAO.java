/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ccm.commons.dao.recommendation;

import static io.harness.annotations.dev.HarnessTeam.CE;
import static io.harness.persistence.HQuery.excludeValidate;

import io.harness.annotations.dev.OwnedBy;
import io.harness.ccm.commons.entities.recommendations.RecommendationsIgnoreList;
import io.harness.ccm.commons.entities.recommendations.RecommendationsIgnoreList.RecommendationsIgnoreListKeys;
import io.harness.persistence.HPersistence;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.Optional;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Singleton
@OwnedBy(CE)
public class RecommendationsIgnoreListDAO {
  @Inject private HPersistence hPersistence;

  @NonNull
  public Optional<RecommendationsIgnoreList> get(String accountId) {
    return Optional.ofNullable(hPersistence.createQuery(RecommendationsIgnoreList.class, excludeValidate)
                                   .filter(RecommendationsIgnoreListKeys.accountId, accountId)
                                   .get());
  }

  public boolean save(RecommendationsIgnoreList recommendationsIgnoreList) {
    return hPersistence.save(recommendationsIgnoreList) != null;
  }
}
