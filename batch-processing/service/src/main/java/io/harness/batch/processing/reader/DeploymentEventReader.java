/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.batch.processing.reader;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.annotation.BeforeStep;
import org.springframework.batch.item.ItemReader;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class DeploymentEventReader implements ItemReader<List<String>> {
  private AtomicBoolean runOnlyOnce;

  @BeforeStep
  public void beforeStep(final StepExecution stepExecution) {
    runOnlyOnce = new AtomicBoolean(false);
  }

  @Override
  public List<String> read() {
    List<String> distinctInstanceIds = null;
    if (!runOnlyOnce.getAndSet(true)) {
      distinctInstanceIds = new ArrayList<>();
    }
    return distinctInstanceIds;
  }
}
