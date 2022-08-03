/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.rollback;

import static io.harness.rule.OwnerRule.TMACARI;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.cdng.CDNGTestBase;
import io.harness.cdng.rollback.service.RollbackDataServiceImpl;
import io.harness.repositories.rollback.RollbackDataRepository;
import io.harness.rule.Owner;
import io.harness.utils.StageStatus;

import com.mongodb.client.result.UpdateResult;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;

@OwnedBy(HarnessTeam.CDP)
public class RollbackDataServiceImplTest extends CDNGTestBase {
  @Mock private RollbackDataRepository rollbackDataRepository;
  @InjectMocks private RollbackDataServiceImpl rollbackDataService;

  @Test
  @Owner(developers = TMACARI)
  @Category(UnitTests.class)
  public void saveRollbackData() {
    RollbackData rollbackData = RollbackData.builder().build();

    rollbackDataService.saveRollbackData(RollbackData.builder().build());

    verify(rollbackDataRepository).save(eq(rollbackData));
  }

  @Test
  @Owner(developers = TMACARI)
  @Category(UnitTests.class)
  public void testListLatestRollbackData() {
    RollbackDeploymentInfoKey rollbackDeploymentInfoKey = mock(RollbackDeploymentInfoKey.class);
    doReturn("key").when(rollbackDeploymentInfoKey).getKey();

    rollbackDataService.listLatestRollbackData(rollbackDeploymentInfoKey.getKey(), StageStatus.SUCCEEDED, 1);

    verify(rollbackDataRepository).listRollbackDataOrderedByCreatedAt(any(), any(), anyInt());
  }

  @Test
  @Owner(developers = TMACARI)
  @Category(UnitTests.class)
  public void testUpdateStatus() {
    doReturn(UpdateResult.acknowledged(1, null, null))
        .when(rollbackDataRepository)
        .updateStatus("executionId", StageStatus.SUCCEEDED);

    rollbackDataService.updateStatus("executionId", StageStatus.SUCCEEDED);

    verify(rollbackDataRepository).updateStatus("executionId", StageStatus.SUCCEEDED);
  }
}
