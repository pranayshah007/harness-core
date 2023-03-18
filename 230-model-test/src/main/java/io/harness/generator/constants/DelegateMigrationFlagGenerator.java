/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.generator.constants;

import static io.harness.annotations.dev.HarnessTeam.DEL;

import io.harness.annotations.dev.OwnedBy;
import io.harness.migration.DelegateMigrationFlag;
import io.harness.persistence.HPersistence;

import com.google.inject.Inject;
import java.util.Arrays;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@OwnedBy(DEL)
public class DelegateMigrationFlagGenerator {
  private final HPersistence persistence;
  private final List<String> classesHavingMigrationEnabled =
      Arrays.asList("software.wings.delegatetasks.validation.core.DelegateConnectionResult",
          "io.harness.delegate.beans.DelegateGroup", "io.harness.delegate.beans.DelegateProfile",
          "io.harness.delegate.beans.DelegateRing", "io.harness.delegate.beans.DelegateScope",
          "software.wings.beans.DelegateSequenceConfig", "io.harness.delegate.beans.DelegateToken",
          "io.harness.delegate.beans.Delegate", "io.harness.perpetualtask.internal.PerpetualTaskRecord",
          "io.harness.delegate.beans.perpetualtask.PerpetualTaskScheduleConfig",
          "io.harness.delegate.beans.VersionOverride", "io.harness.delegate.beans.TaskSelectorMap", "delegateTask");

  @Inject
  public DelegateMigrationFlagGenerator(HPersistence persistence) {
    this.persistence = persistence;
  }

  public void populateDelegateMigrationFlags() {
    classesHavingMigrationEnabled.forEach(this::enableMigrationFlag);
  }

  private void enableMigrationFlag(String className) {
    DelegateMigrationFlag flag = new DelegateMigrationFlag(className, true);
    persistence.save(flag);
  }
}
