package io.harness.delegate.app.modules;
import dagger.MapKey;

@MapKey
public @interface ExceptionKey {
    Class<? extends Exception> value();
}
