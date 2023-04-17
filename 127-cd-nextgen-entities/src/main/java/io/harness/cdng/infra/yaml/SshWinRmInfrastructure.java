/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.infra.yaml;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.annotations.dev.OwnedBy;
import io.harness.filters.WithConnectorRef;
import io.harness.filters.WithSecretRef;
import io.harness.pms.yaml.ParameterField;
import io.harness.walktree.visitor.Visitable;

@OwnedBy(CDP)
public interface SshWinRmInfrastructure extends Infrastructure, Visitable, WithConnectorRef, WithSecretRef {
  ParameterField<String> getCredentialsRef();
}
