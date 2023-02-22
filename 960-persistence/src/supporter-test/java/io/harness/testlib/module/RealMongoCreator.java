/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.testlib.module;

import static java.time.Duration.ofMillis;

import io.harness.exception.GeneralException;
import io.harness.threading.Morpheus;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.mongodb.MongoClient;
import com.mongodb.MongoClientURI;
import de.flapdoodle.embed.mongo.Command;
import de.flapdoodle.embed.mongo.MongodExecutable;
import de.flapdoodle.embed.mongo.MongodStarter;
import de.flapdoodle.embed.mongo.config.DownloadConfigBuilder;
import de.flapdoodle.embed.mongo.config.ExtractedArtifactStoreBuilder;
import de.flapdoodle.embed.mongo.config.IMongodConfig;
import de.flapdoodle.embed.mongo.config.MongodConfigBuilder;
import de.flapdoodle.embed.mongo.config.Net;
import de.flapdoodle.embed.mongo.config.RuntimeConfigBuilder;
import de.flapdoodle.embed.mongo.distribution.Version;
import de.flapdoodle.embed.process.config.IRuntimeConfig;
import de.flapdoodle.embed.process.runtime.Network;
import java.io.Closeable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import lombok.Builder;
import lombok.Value;
import org.slf4j.LoggerFactory;

class RealMongoCreator {
  private static ExecutorService executorService =
      Executors.newSingleThreadExecutor(new ThreadFactoryBuilder().setNameFormat("RealMongoCreator-%d").build());

  private static IRuntimeConfig runtimeConfig =
      new RuntimeConfigBuilder()
          .defaultsWithLogger(Command.MongoD, LoggerFactory.getLogger(io.harness.testlib.RealMongo.class))
          .artifactStore(new ExtractedArtifactStoreBuilder()
                             .defaults(Command.MongoD)
                             .download(new DownloadConfigBuilder()
                                           .defaultsForCommand(Command.MongoD)
                                           .downloadPath("https://storage.googleapis.com/harness-tests/")
                                           .build()))
          .build();

  private static MongodStarter starter = MongodStarter.getInstance(runtimeConfig);

  @Value
  @Builder
  static class RealMongo implements Closeable {
    MongodExecutable mongodExecutable;
    MongoClient mongoClient;
    String temporaryDatabaseName;

    @Override
    public void close() {
      executorService.submit(() -> {
        if (temporaryDatabaseName != null) {
          mongoClient.dropDatabase(temporaryDatabaseName);
        }

        mongoClient.close();
        if (mongodExecutable != null) {
          mongodExecutable.stop();
        }
      });
    }
  }

  private static RealMongo realMongo(String databaseName) throws Exception {
    String testMongoUri = System.getenv("TEST_MONGO_URI");
    if (testMongoUri != null) {
      MongoClientURI mongoClientURI = new MongoClientURI(testMongoUri + "/" + databaseName);
      return RealMongo.builder()
          .mongodExecutable(null)
          .temporaryDatabaseName(databaseName)
          .mongoClient(new MongoClient(mongoClientURI))
          .build();
    }

    Exception persistent = null;

    // FreeServerPort releases the port before it returns it. This creates a race between the moment it is obtain
    // again and reserved for mongo. In rare cases this can cause the function to fail with port already in use
    // exception.
    //
    // There is no good way to eliminate the race, since the port must be free mongo to be able to grab it.
    //
    // Lets retry a number of times to reduce the likelihood almost to zero.
    for (int i = 0; i < 20; i++) {
      int port = Network.getFreeServerPort();
      IMongodConfig mongodConfig = new MongodConfigBuilder()
                                       .version(Version.Main.V3_6)
                                       .net(new Net("127.0.0.1", port, Network.localhostIsIPv6()))
                                       .build();
      try {
        // It seems that the starter is not thread safe. We still have a likelihood for multiprocessor problems
        // but lets at least do what is cheap to have.
        MongodExecutable mongodExecutable;
        synchronized (starter) {
          mongodExecutable = starter.prepare(mongodConfig);
          mongodExecutable.start();
        }
        MongoClient mongoClient = new MongoClient("localhost", port);
        return RealMongo.builder().mongodExecutable(mongodExecutable).mongoClient(mongoClient).build();
      } catch (Exception e) {
        // Note this handles race int the port, but also in the starter prepare
        Morpheus.sleep(ofMillis(250));
        persistent = e;
      }
    }
    throw persistent;
  }

  static RealMongo takeRealMongo(String databaseName) {
    try {
      return realMongo(databaseName);
    } catch (Exception exception) {
      throw new GeneralException("", exception);
    }
  }
}
