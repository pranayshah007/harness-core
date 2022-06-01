/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl.ldap;

import static io.harness.annotations.dev.HarnessTeam.PL;

import io.harness.annotations.dev.OwnedBy;
import io.harness.threading.ThreadPool;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import lombok.Getter;
import lombok.experimental.FieldNameConstants;

/***
 * This is singleton class for re-using the executor service
 * for ldap based queries.
 *
 * Ideally, I want to mark the class as final but I am not sure
 * whether lombok will work correctly with it. Will test it.
 */
@OwnedBy(PL)
@FieldNameConstants(innerTypeName = "LdapExecutorServiceKeys")
public class LdapExecutorService {
  private static final LdapExecutorService INSTANCE = new LdapExecutorService();

  @Getter(lazy = true) private final ExecutorService executorService = createExecutorService();

  private LdapExecutorService() {}

  public static LdapExecutorService getInstance() {
    return INSTANCE;
  }

  private ExecutorService createExecutorService() {
    int processorsCount = Runtime.getRuntime().availableProcessors();
    int maxPoolSize = 2 * processorsCount;
    ExecutorService threadPoolExecutor = ThreadPool.create(processorsCount, maxPoolSize, 1, TimeUnit.SECONDS,
        new ThreadFactoryBuilder().setNameFormat("ldap-search-task-%d").setPriority(Thread.NORM_PRIORITY).build());
    Runtime.getRuntime().addShutdownHook(new Thread(() -> { threadPoolExecutor.shutdownNow(); }));
    return threadPoolExecutor;
  }
}
