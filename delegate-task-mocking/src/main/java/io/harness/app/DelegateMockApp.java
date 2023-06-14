/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.app;

import com.google.inject.Guice;
import com.google.inject.Injector;
import io.harness.delegate.configuration.DelegateConfiguration;
import io.harness.delegate.service.DelegateAgentService;
import io.harness.util.CSVParser;
import io.harness.util.DataConfiguration;
import lombok.extern.slf4j.Slf4j;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;


@Slf4j
public class DelegateMockApp implements Runnable{
    private static final String DELEGATE_DATA_CSV =System.getenv().get("DELEGATE_DETAIL_CSV");

    private static final List<DataConfiguration> obj = DELEGATE_DATA_CSV !=null ? CSVParser.readFromString(DELEGATE_DATA_CSV): CSVParser.readFromCSV("delegate.csv");
    private static DelegateConfiguration configuration;

    public void setEnv(String key, String value) {
        try {
            Map<String, String> env = System.getenv();
            Class<?> cl = env.getClass();
            Field field = cl.getDeclaredField("m");
            field.setAccessible(true);
            Map<String, String> writableEnv = (Map<String, String>) field.get(env);
            writableEnv.put(key, value);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to set environment variable", e);
        }
    }

    public static void main(final String... args) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(obj.size()*obj.get(0).getDelegateCount());
        for (DataConfiguration delegateData: obj) {
            for (int i = 0; i < delegateData.getDelegateCount(); i++) {
                Runnable worker = new DelegateMockApp();
                executor.execute(worker);
            }
        }
        executor.shutdown();
    }


    @Override
    public void run() {
        configuration = CSVParser.readConfigFile("config.yaml");
        configuration.setImmutable(true);
        Injector injector = Guice.createInjector(new MockDelegateAppModule(configuration));
        injector.getInstance(DelegateAgentService.class).run(false, true);
    }


}
