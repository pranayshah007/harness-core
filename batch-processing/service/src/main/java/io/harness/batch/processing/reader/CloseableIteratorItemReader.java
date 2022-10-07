/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.batch.processing.reader;

import java.util.Iterator;
import org.springframework.batch.item.support.IteratorItemReader;

public class CloseableIteratorItemReader<T> extends IteratorItemReader<T> implements AutoCloseable {
  private AutoCloseable autoCloseable;

  public CloseableIteratorItemReader(Iterator<T> iterator) {
    super(iterator);
    if (iterator instanceof AutoCloseable) {
      this.autoCloseable = (AutoCloseable) iterator;
    }
  }

  @Override
  public void close() throws Exception {
    if (autoCloseable != null) {
      autoCloseable.close();
    }
  }
}
