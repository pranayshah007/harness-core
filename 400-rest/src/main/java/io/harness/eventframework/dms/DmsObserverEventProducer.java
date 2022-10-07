/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.eventframework.dms;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.eventsframework.EventsFrameworkConstants;
import io.harness.eventsframework.api.Producer;
import io.harness.observer.AbstractRemoteInformer;
import io.harness.observer.RemoteObserverInformer;
import io.harness.serializer.KryoSerializerWrapper;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import java.lang.reflect.Method;

@OwnedBy(HarnessTeam.DEL)
public class DmsObserverEventProducer extends AbstractRemoteInformer implements RemoteObserverInformer {
  @Inject
  public DmsObserverEventProducer(
      KryoSerializerWrapper kryoSerializerWrapper, @Named(EventsFrameworkConstants.OBSERVER_EVENT_CHANNEL) Producer eventProducer) {
    super(kryoSerializerWrapper, eventProducer);
  }

  @Override
  public void sendEvent(Method method, Class<?> subjectClazz, Object... params) {
    super.fireInform(method, subjectClazz, params);
  }
}
