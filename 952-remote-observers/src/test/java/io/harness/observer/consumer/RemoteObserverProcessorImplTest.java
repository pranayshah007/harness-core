/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.observer.consumer;

import static io.harness.rule.OwnerRule.ABHINAV;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.eventsframework.consumer.Message;
import io.harness.kryo.KryoRegistrar;
import io.harness.kryo.KryoSerializer;
import io.harness.observer.Informant;
import io.harness.observer.Informant0;
import io.harness.observer.Informant1;
import io.harness.observer.Informant2;
import io.harness.observer.Informant3;
import io.harness.observer.Informant4;
import io.harness.observer.RemoteObserver;
import io.harness.observer.RemoteObserverConstants;
import io.harness.rule.Owner;

import com.esotericsoftware.kryo.Kryo;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.inject.Injector;
import com.google.protobuf.ByteString;
import java.util.Set;
import lombok.AllArgsConstructor;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.MockitoAnnotations;

@Slf4j
public class RemoteObserverProcessorImplTest extends CategoryTest {
  Injector injector;
  KryoSerializer kryoSerializer;
  RemoteObserverProcessorImpl remoteObserverProcessor;

  @Before
  public void setUp() {
    injector = mock(Injector.class);
    final ImmutableSet<Class<? extends KryoRegistrar>> kryos =
        ImmutableSet.<Class<? extends KryoRegistrar>>builder().add(TestKryoRegistrar.class).build();
    kryoSerializer = new KryoSerializer(kryos);
    remoteObserverProcessor = spy(new RemoteObserverProcessorImpl(injector, kryoSerializer));
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = ABHINAV)
  @Category(UnitTests.class)
  public void process0() {
    Informant informant =
        Informant.newBuilder().setInformant0(Informant0.newBuilder().build()).setMethodName("method0").build();
    processInternal(informant);
  }

  @Test
  @Owner(developers = ABHINAV)
  @Category(UnitTests.class)
  public void process1() {
    Informant informant = Informant.newBuilder()
                              .setInformant1(Informant1.newBuilder().setParam1(getTestObject()).build())
                              .setMethodName("method1")
                              .build();
    processInternal(informant);
  }

  @Test
  @Owner(developers = ABHINAV)
  @Category(UnitTests.class)
  public void process2() {
    Informant informant = Informant.newBuilder()
                              .setInformant2(Informant2.newBuilder()
                                                 .setParam1(getTestObject())
                                                 .setParam2(ByteString.copyFrom(kryoSerializer.asBytes("aac"))))
                              .setMethodName("method2")
                              .build();
    processInternal(informant);
  }

  @Test
  @Owner(developers = ABHINAV)
  @Category(UnitTests.class)
  public void process3() {
    Informant informant = Informant.newBuilder()
                              .setInformant3(Informant3.newBuilder()
                                                 .setParam1(getTestObject())
                                                 .setParam2(ByteString.copyFrom(kryoSerializer.asBytes("aac")))
                                                 .setParam3(ByteString.copyFrom(kryoSerializer.asBytes("aacd"))))
                              .setMethodName("method3")
                              .build();
    processInternal(informant);
  }

  @Test
  @Owner(developers = ABHINAV)
  @Category(UnitTests.class)
  public void process4() {
    Informant informant = Informant.newBuilder()
                              .setInformant4(Informant4.newBuilder()
                                                 .setParam1(getTestObject())
                                                 .setParam2(ByteString.copyFrom(kryoSerializer.asBytes("aac")))
                                                 .setParam3(ByteString.copyFrom(kryoSerializer.asBytes("aacd")))
                                                 .setParam4(ByteString.copyFrom(kryoSerializer.asBytes("aacd1"))))
                              .setMethodName("method3")
                              .build();
    processInternal(informant);
  }

  public void processInternal(Informant informant) {
    final Message consumerMessage = getMessage(informant);

    Set<RemoteObserver> remoteObserverMap = getRemoteObserverMap();
    doReturn(new SampleObserverClass()).when(remoteObserverProcessor).getObserver(SampleObserverClass.class);
    final boolean response = remoteObserverProcessor.process(consumerMessage, remoteObserverMap);
    assertThat(response).isEqualTo(true);
  }

  public Set<RemoteObserver> getRemoteObserverMap() {
    return ImmutableSet.of(RemoteObserver.builder()
                               .subjectCLass(Subject.class)
                               .observerClass(Subject.class)
                               .observer(SampleObserverClass.class)
                               .build());
  }

  public Message getMessage(Informant informant) {
    final io.harness.eventsframework.producer.Message message =
        io.harness.eventsframework.producer.Message.newBuilder()
            .putAllMetadata(ImmutableMap.of(RemoteObserverConstants.SUBJECT_CLASS_NAME, Subject.class.getName(),
                RemoteObserverConstants.OBSERVER_CLASS_NAME, Subject.class.getName()))
            .setData(informant.toByteString())
            .build();
    return Message.newBuilder().setMessage(message).build();
  }

  @Slf4j
  public static class TestKryoRegistrar implements KryoRegistrar {
    @Override
    public void register(Kryo kryo) {
      kryo.register(SampleObserverClass.TestClass.class, 12345);
    }
  }

  public static class Subject {}

  @Slf4j
  public static class SampleObserverClass {
    public void method0() {
      log.info("method0");
    }

    public void method1(TestClass a) {
      log.info("method1");
    }

    public void method2(TestClass a, String b) {
      log.info("method2");
    }

    public void method3(TestClass a, String b, String c) {
      log.info("method3");
    }

    public void method4(TestClass a, String b, String c, String d) {
      log.info("method4");
    }

    @Value
    @AllArgsConstructor
    public static class TestClass {
      String a;
    }
  }

  private ByteString getTestObject() {
    return ByteString.copyFrom(kryoSerializer.asBytes(new SampleObserverClass.TestClass("test")));
  }
}
