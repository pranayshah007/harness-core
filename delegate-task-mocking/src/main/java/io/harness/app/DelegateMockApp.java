/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.app;

import com.google.inject.Guice;
import com.google.inject.Injector;
import io.harness.module.Registration;
import io.harness.serializer.KryoModule;
import io.harness.util.CSVParser;
import io.harness.util.DataConfiguration;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;


@Slf4j
public class DelegateMockApp {
    private static final String DELEGATE_DATA_CSV =System.getenv().get("DELEGATE_DETAIL_CSV");

    private static final List<DataConfiguration> obj = DELEGATE_DATA_CSV !=null ? CSVParser.readFromString(DELEGATE_DATA_CSV): CSVParser.readFromCSV("delegate.csv");

    public static void main(String... args) throws Exception {
        final Injector injector = Guice.createInjector(new MockDelegateAppModule());
        ExecutorService executor = Executors.newFixedThreadPool(obj.size()*obj.get(0).getDelegateCount());
        for (DataConfiguration delegateData: obj) {
            for (int i = 0; i < delegateData.getDelegateCount(); i++) {
                Runnable worker = new Registration(delegateData, injector);
                executor.execute(worker);
            }
        }
        executor.shutdown();
  }
}
