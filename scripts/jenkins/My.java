/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.factory;

import java.io.Closeable;
import java.util.Iterator;
import java.util.LinkedList;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ClosingFactory implements AutoCloseable {
  private LinkedList<Closeable> servers = new LinkedList<>();

  public synchronized void addServer(Closeable server) {
    servers.add(server);
  }

  public synchronized void stopServers() {
    for (Iterator<Closeable> closeableIterator = servers.descendingIterator(); closeableIterator.hasNext();) {
      Closeable server = closeableIterator.next();
      try {
        server.close();
      } catch (Exception exception) {
        log.error("Error when closing", exception);
      }
    }
    servers.clear();
  }

  @Override
  public void close() throws Exception {
    stopServers();
  }
}
