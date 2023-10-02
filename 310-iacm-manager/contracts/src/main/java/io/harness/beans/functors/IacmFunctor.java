/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.beans.functors;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.exception.WingsException.USER;

import io.harness.exception.IllegalArgumentException;
import io.harness.iacmserviceclient.IACMServiceUtils;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.sdk.core.execution.expression.SdkFunctor;

import com.google.inject.Inject;

public class IacmFunctor implements SdkFunctor {
  public static final String IACM_FUNCTOR_NAME = "workspace";
  @Inject private IACMServiceUtils serviceUtils;

  @Override
  public Object get(Ambiance ambiance, String... args) {
    if (args.length != 1 || isEmpty(args[0])) {
      throw new IllegalArgumentException(
          "Inappropriate usage of 'iacm' functor. The format should be <+workspace.workspace_id.name_of_the_output_to_use>",
          USER);
    }
    String workspaceId = args[0];
    String accountId = AmbianceUtils.getAccountId(ambiance);
    String projectId = AmbianceUtils.getProjectIdentifier(ambiance);
    String orgId = AmbianceUtils.getOrgIdentifier(ambiance);

    return serviceUtils.getIacmWorkspaceOutputs(orgId, projectId, accountId, workspaceId);
  }
}
