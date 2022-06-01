/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.beans.sso;

import static io.harness.annotations.dev.HarnessModule._950_NG_AUTHENTICATION_SERVICE;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;

/**
 * Contract for any ldap config which will be used for searching
 *
 * @author Swapnil
 */
@OwnedBy(HarnessTeam.PL)
@TargetModule(_950_NG_AUTHENTICATION_SERVICE)
public interface LdapSearchConfig {
  String getBaseDN();

  void setBaseDN(String dn);

  String[] getReturnAttrs();
}
