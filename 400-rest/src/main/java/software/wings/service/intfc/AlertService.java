/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.intfc;

import io.harness.alert.AlertData;
import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.TargetModule;
import io.harness.beans.PageRequest;
import io.harness.beans.PageResponse;

import software.wings.beans.alert.Alert;
import software.wings.beans.alert.AlertType;
import software.wings.service.intfc.ownership.OwnedByAccount;
import software.wings.service.intfc.ownership.OwnedByApplication;

import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Future;
import javax.ws.rs.QueryParam;

@TargetModule(HarnessModule._470_ALERT)
public interface AlertService extends OwnedByAccount, OwnedByApplication {
  PageResponse<Alert> list(PageRequest<Alert> pageRequest);

  List<AlertType> listCategoriesAndTypes(@QueryParam("accountId") String accountId);

  Future openAlert(String accountId, String appId, AlertType alertType, AlertData alertData);

  Future openAlertWithTTL(String accountId, String appId, AlertType alertType, AlertData alertData, Date validUntil);

  Future closeExistingAlertsAndOpenNew(
      String accountId, String appId, AlertType alertType, AlertData alertData, Date validUntil);

  void closeAlert(String accountId, String appId, AlertType alertType, AlertData alertData);

  void close(Alert alert);

  void closeAllAlerts(String accountId, String appId, AlertType alertType, AlertData alertData);

  void closeAlertsOfType(String accountId, String appId, AlertType alertType);

  void deploymentCompleted(String appId, String executionId);

  Optional<Alert> findExistingAlert(String accountId, String appId, AlertType alertType, AlertData alertData);

  void deleteByArtifactStream(String appId, String artifactStreamId);

  void deleteArtifactStreamAlertForAccount(String accountId);
}
