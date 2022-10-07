/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.observer.consumer;

import static io.harness.observer.RemoteObserverConstants.OBSERVER_CLASS_NAME;
import static io.harness.observer.RemoteObserverConstants.SUBJECT_CLASS_NAME;

import io.harness.eventsframework.NgEventLogContext;
import io.harness.eventsframework.consumer.Message;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.UnexpectedException;
import io.harness.logging.AutoLogContext;
import io.harness.logging.AutoLogContext.OverrideBehavior;
import io.harness.observer.Informant;
import io.harness.observer.Informant.InformantCase;
import io.harness.observer.RemoteObserver;
import io.harness.reflection.ReflectionUtils;
import io.harness.serializer.KryoSerializerWrapper;

import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@AllArgsConstructor(onConstructor = @__({ @Inject }), access = AccessLevel.PACKAGE)
@Slf4j
public class RemoteObserverProcessorImpl implements RemoteObserverProcessor {
  Injector injector;
  KryoSerializerWrapper kryoSerializerWrapper;

  @Override
  public boolean process(Message message, Set<RemoteObserver> remoteObservers) {
    try (AutoLogContext ignore1 = new NgEventLogContext(message.getId(), OverrideBehavior.OVERRIDE_ERROR)) {
      final Map<String, String> metadataMap = message.getMessage().getMetadataMap();
      final String subjectClassName = metadataMap.get(SUBJECT_CLASS_NAME);
      final String observerClassName = metadataMap.get(OBSERVER_CLASS_NAME);

      final Set<RemoteObserver> remoteObserversOfInterest =
          getRemoteObserversOfInterest(remoteObservers, subjectClassName, observerClassName);
      remoteObserversOfInterest.forEach(remoteObserver -> informAllObservers(message, remoteObserver));
    } catch (Exception e) {
      log.error("Error in processing message: [{}]", message.getId());
      throw e;
    }
    return true;
  }

  private Set<RemoteObserver> getRemoteObserversOfInterest(
      Set<RemoteObserver> remoteObservers, String subjectClassName, String observerClassName) {
    return remoteObservers.stream()
        .filter(remoteObserver
            -> subjectClassName.equals(remoteObserver.getSubjectCLass().getName())
                && observerClassName.equals(remoteObserver.getObserverClass().getName()))
        .collect(Collectors.toSet());
  }

  private void informAllObservers(Message message, RemoteObserver remoteObserver) {
    final List<Class> observers = remoteObserver.getObservers();
    Informant informant = getInformant(message);
    final String methodName = informant.getMethodName();
    observers.forEach(observer -> inform(message, informant, methodName, observer));
  }

  private void inform(Message message, Informant informant, String methodName, Class observer) {
    final Object observerClassObject = getObserver(observer);
    final InformantCase informantCase = informant.getInformantCase();
    Function<ByteString, Object> objectSupplier =
        byteString -> byteString.equals(ByteString.EMPTY) ? null : kryoSerializerWrapper.asObject(byteString.toByteArray());
    try {
      switch (informantCase) {
        case INFORMANT0:
          final Method method0 = ReflectionUtils.getMethod(observerClassObject.getClass(), methodName);
          method0.invoke(observerClassObject);
          break;
        case INFORMANT1:
          final Object param11 = objectSupplier.apply(informant.getInformant1().getParam1());
          final Class<?> type11 = Class.forName(informant.getInformant1().getType1());
          final Method method1 = ReflectionUtils.getMethod(observerClassObject.getClass(), methodName, type11);
          method1.invoke(observerClassObject, param11);
          break;
        case INFORMANT2:
          final Class<?> type21 = Class.forName(informant.getInformant2().getType1());
          final Class<?> type22 = Class.forName(informant.getInformant2().getType2());
          final Object param21 = objectSupplier.apply(informant.getInformant2().getParam1());
          final Object param22 = objectSupplier.apply(informant.getInformant2().getParam2());
          final Method method2 = ReflectionUtils.getMethod(observerClassObject.getClass(), methodName, type21, type22);
          method2.invoke(observerClassObject, param21, param22);
          break;
        case INFORMANT3:
          final Class<?> type31 = Class.forName(informant.getInformant3().getType1());
          final Class<?> type32 = Class.forName(informant.getInformant3().getType2());
          final Class<?> type33 = Class.forName(informant.getInformant3().getType3());
          final Object param31 = objectSupplier.apply(informant.getInformant3().getParam1());
          final Object param32 = objectSupplier.apply(informant.getInformant3().getParam2());
          final Object param33 = objectSupplier.apply(informant.getInformant3().getParam3());
          final Method method3 =
              ReflectionUtils.getMethod(observerClassObject.getClass(), methodName, type31, type32, type33);
          method3.invoke(observerClassObject, param31, param32, param33);
          break;
        case INFORMANT4:
          final Class<?> type41 = Class.forName(informant.getInformant4().getType1());
          final Class<?> type42 = Class.forName(informant.getInformant4().getType2());
          final Class<?> type43 = Class.forName(informant.getInformant4().getType3());
          final Class<?> type44 = Class.forName(informant.getInformant4().getType4());
          final Object param41 = objectSupplier.apply(informant.getInformant4().getParam1());
          final Object param42 = objectSupplier.apply(informant.getInformant4().getParam2());
          final Object param43 = objectSupplier.apply(informant.getInformant4().getParam3());
          final Object param44 = objectSupplier.apply(informant.getInformant4().getParam4());
          final Method method4 =
              ReflectionUtils.getMethod(observerClassObject.getClass(), methodName, type41, type42, type43, type44);
          method4.invoke(observerClassObject, param41, param42, param43, param44);
          break;
        default:
          throw new UnexpectedException();
      }
    } catch (Exception e) {
      log.error("Error in informing observer {} about message {}", observer, message);
    }
  }

  @VisibleForTesting
  Object getObserver(Class observer) {
    return injector.getInstance(observer);
  }

  private Informant getInformant(Message message) {
    try {
      return Informant.parseFrom(message.getMessage().getData());
    } catch (InvalidProtocolBufferException e) {
      throw new InvalidRequestException(String.format("Invalid message for message id [%s]", message.getId()));
    }
  }
}
