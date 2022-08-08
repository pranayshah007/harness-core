/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.intfc;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.TargetModule;
import io.harness.beans.PageRequest;
import io.harness.beans.PageResponse;

import software.wings.beans.Log;
import software.wings.service.intfc.ownership.OwnedByActivity;

import java.io.File;
import java.util.List;
import javax.validation.Valid;
import org.hibernate.validator.constraints.NotEmpty;

/**
 * Created by peeyushaggarwal on 5/27/16.
 */
@TargetModule(HarnessModule._870_CG_ORCHESTRATION)
public interface LogService extends OwnedByActivity {
  /**
   * List.
   *
   *
   * @param appId
   * @param pageRequest the page request  @return the page response
   * @return the page response
   */
  PageResponse<Log> list(String appId, PageRequest<Log> pageRequest);

  /**
   * Export logs file.
   *
   * @param appId      the app id
   * @param activityId the activity id
   * @return the file
   */
  File exportLogs(@NotEmpty String appId, @NotEmpty String activityId);

  /**
   * Batched save.
   *
   * @param logs the logs
   */
  void batchedSave(@Valid List<Log> logs);

  /**
   * Batched save command unit logs.
   *  @param activityId the activity id
   * @param unitName   the unit name
   * @param logs       the logs
   */
  boolean batchedSaveCommandUnitLogs(@NotEmpty String activityId, @NotEmpty String unitName, @Valid Log logs);
}
