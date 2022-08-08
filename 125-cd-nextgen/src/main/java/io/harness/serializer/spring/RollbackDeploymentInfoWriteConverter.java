/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.serializer.spring;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.rollback.RollbackDeploymentInfo;
import io.harness.serializer.KryoSerializer;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import org.springframework.data.convert.WritingConverter;

@OwnedBy(CDP)
@Singleton
@WritingConverter
public class RollbackDeploymentInfoWriteConverter extends AbstractWriteConverter<RollbackDeploymentInfo> {
  @Inject
  public RollbackDeploymentInfoWriteConverter(KryoSerializer kryoSerializer) {
    super(kryoSerializer);
  }
}
