/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package software.wings.delegatetasks.terraform;

import io.harness.delegate.clienttools.TerraformConfigInspectVersion;

import java.util.Collection;

/*
 ** Created by Yogesh on 05/07/2019
 */
public interface TerraformConfigInspectClient {
  enum BLOCK_TYPE { VARIABLES, MANAGED_RESOURCES, DIAGNOSTICS, DETAIL, SEVERITY, POS, FILENAME, LINE, SUMMARY }
  enum ERROR_TYPE { ERROR, WARNING }
  Collection<String> parseFieldsUnderBlock(
      String directory, String category, TerraformConfigInspectVersion terraformConfigInspectVersion);
}
