/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.batch.processing.view;

import io.harness.batch.processing.shard.AccountShardService;
import io.harness.ccm.views.entities.CEView;
import io.harness.ccm.views.entities.ViewState;
import io.harness.ccm.views.service.CEViewService;

import com.google.inject.Singleton;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
@Singleton
@Slf4j
public class ViewCostUpdateService {
  @Autowired private CEViewService ceViewService;
  @Autowired private AccountShardService accountShardService;

  public void updateTotalCost() {
    List<String> accountIds = accountShardService.getCeEnabledAccountIds();
    accountIds.forEach(accountId -> {
      List<CEView> views = ceViewService.getViewByState(accountId, ViewState.COMPLETED);
      views.forEach(view -> {
        log.info("Updating view {} in account {}", view.getUuid(), accountId);
        try {
          ceViewService.updateTotalCost(view);
        } catch (Exception ex) {
          log.error("Exception while updating cost for view {} in account {}", view.getUuid(), accountId, ex);
        }
      });
    });
    log.info("Updated views for all accounts");
  }
}
