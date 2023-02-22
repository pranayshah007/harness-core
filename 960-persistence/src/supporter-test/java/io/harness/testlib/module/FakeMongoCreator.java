/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.testlib.module;

import io.harness.exception.GeneralException;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.mongodb.MongoClient;
import com.mongodb.ServerAddress;
import de.bwaldvogel.mongo.MongoServer;
import de.bwaldvogel.mongo.backend.memory.MemoryBackend;
import java.io.Closeable;
import java.net.InetSocketAddress;
import java.util.ArrayDeque;
import java.util.Queue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import lombok.Builder;
import lombok.Value;

public class FakeMongoCreator {
  public static ExecutorService executorService =
      Executors.newFixedThreadPool(8, new ThreadFactoryBuilder().setNameFormat("FakeMongoCreator-%d").build());

  @Value
  @Builder
  static class FakeMongo implements Closeable {
    MongoServer mongoServer;
    MongoClient mongoClient;

    @Override
    public void close() {
      executorService.submit(() -> {
        mongoClient.close();
        mongoServer.shutdownNow();
      });
    }
  }

  private static FakeMongo fakeMongo() {
    MongoServer mongoServer = new MongoServer(new MemoryBackend());
    mongoServer.bind("localhost", 0);
    InetSocketAddress serverAddress = mongoServer.getLocalAddress();
    MongoClient mongoClient = new MongoClient(new ServerAddress(serverAddress));
    return FakeMongo.builder().mongoServer(mongoServer).mongoClient(mongoClient).build();
  }

  private static Queue<Future<FakeMongo>> futureFakeMongoClient = new ArrayDeque<>();

  static {
    for (int i = 0; i < 10; i++) {
      futureFakeMongoClient.add(executorService.submit(FakeMongoCreator::fakeMongo));
    }
  }

  static FakeMongo takeFakeMongo() {
    Future<FakeMongo> fakeMongo = futureFakeMongoClient.poll();
    futureFakeMongoClient.add(executorService.submit(FakeMongoCreator::fakeMongo));
    try {
      return fakeMongo.get();
    } catch (InterruptedException | ExecutionException exception) {
      throw new GeneralException("", exception);
    }
  }
}
