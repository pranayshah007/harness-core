/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.faktory;

import com.google.gson.Gson;
import java.io.IOException;

public class FaktoryClient {
  private final FaktoryConnection connection;
  public static String url = "tcp://localhost:7419";

  public FaktoryClient(final String urlParam) {
    url = urlParam == null ? url : urlParam;
    connection = new FaktoryConnection(url);
  }

  public FaktoryClient() {
    this(System.getenv("FAKTORY_URL"));
  }

  public void push(final FaktoryJob job) throws IOException {
    final String payload = new Gson().toJson(job);
    connection.connect();
    connection.send("PUSH " + payload);
    connection.close();
  }
}
