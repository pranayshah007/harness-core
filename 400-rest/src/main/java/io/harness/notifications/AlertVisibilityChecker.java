/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.notifications;

import software.wings.beans.User;
import software.wings.beans.alert.Alert;

import java.util.List;
import javax.annotation.Nonnull;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;

/**
 * Checks if an alert should be shown to a particular user or not on the UI (under bell icon)
 */
public interface AlertVisibilityChecker {
  boolean shouldAlertBeShownToUser(
      @NotEmpty List<String> userGroupByUserAccountId, @Nonnull Alert alert, String accountId);

  List<String> listUserGroupByUserAccountId(@NotNull String accountId, @Nonnull User user);
}
