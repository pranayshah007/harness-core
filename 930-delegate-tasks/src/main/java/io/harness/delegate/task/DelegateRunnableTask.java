/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.task;

import io.harness.delegate.beans.DelegateResponseData;

import java.io.IOException;
import org.jose4j.lang.JoseException;

public interface DelegateRunnableTask extends Runnable {
  @Deprecated DelegateResponseData run(Object[] parameters);
  DelegateResponseData run(TaskParameters parameters) throws IOException, JoseException;
}
