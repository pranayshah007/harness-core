/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.beans.account;

import static io.harness.annotations.dev.HarnessTeam.DX;
import static io.harness.data.structure.EmptyPredicate.isEmpty;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;

import org.apache.commons.lang3.StringUtils;

/**
 * Account status.
 * @author rktummala on 08/30/18
 */
// Note: This is intentionally not made enum
@OwnedBy(DX)
@TargetModule(HarnessModule._955_ACCOUNT_MGMT)
public interface AccountStatus {
  String ACTIVE = "ACTIVE";
  String EXPIRED = "EXPIRED";
  String MARKED_FOR_DELETION = "MARKED-FOR-DELETION";
  String DELETED = "DELETED";
  String INACTIVE = "INACTIVE";

  static boolean isValid(String status) {
    if (isEmpty(status)) {
      return false;
    }

    switch (StringUtils.upperCase(status)) {
      case ACTIVE:
      case EXPIRED:
      case MARKED_FOR_DELETION:
      case DELETED:
      case INACTIVE:
        return true;
      default:
        return false;
    }
  }
}
