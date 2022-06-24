package io.harness;

import com.google.inject.AbstractModule;
import io.harness.ngsettings.services.SettingsService;
import io.harness.ngsettings.services.impl.SettingsServiceImpl;

import java.util.concurrent.atomic.AtomicReference;

public class NGSettingsModule extends AbstractModule {
    private static final AtomicReference<NGSettingsModule> instanceRef = new AtomicReference<>();

    public NGSettingsModule() {}

    @Override protected void configure() {
        bind(SettingsService.class).to(SettingsServiceImpl.class);
    }

    public static NGSettingsModule getInstance() {
        if(instanceRef.get() == null) {
            instanceRef.compareAndSet(null, new NGSettingsModule());
        }
        return instanceRef.get();
    }
}
