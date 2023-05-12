/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.faktory;
import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.Socket;
import java.net.URI;
import java.util.regex.Pattern;

public class FaktoryConnection {
  private static final Pattern HI_RECEIVED = Pattern.compile("\\+HI\\s\\{\"v\":\\d}");
  private static final Pattern OK_RECEIVED = Pattern.compile("\\+OK");

  private final URI url;
  private Socket socket;
  private BufferedReader fromServer;
  private DataOutputStream toServer;

  public FaktoryConnection(String url) {
    this.url = URI.create(url);
  }

  /**
   * Method used to connect to Faktory using a Socket.
   */

  public void connect() throws IOException {
    socket = openSocket();
    toServer = new DataOutputStream(socket.getOutputStream());
    fromServer = new BufferedReader(new InputStreamReader(socket.getInputStream()));

    if (!HI_RECEIVED.matcher(readFromSocket()).matches()) {
      throw new FaktoryException("Invalid +HI, Expecting:" + HI_RECEIVED);
    }
    send("HELLO {\"v\":2}");
  }

  public void send(String message) throws IOException {
    if (socket == null) {
      throw new FaktoryException("Socket not initialized");
    }
    writeToSocket(message);
    String response = readFromSocket();
    if (!OK_RECEIVED.matcher(response).matches()) {
      throw new FaktoryException("Invalid +OK, Expecting:" + OK_RECEIVED);
    }
  }

  public void close() throws IOException {
    socket.close();
  }

  private Socket openSocket() throws IOException {
    return new Socket(url.getHost(), url.getPort());
  }

  private String readFromSocket() throws IOException {
    return fromServer.readLine();
  }

  private void writeToSocket(String content) throws IOException {
    toServer.writeBytes(content + "\n");
  }
}