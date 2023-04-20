/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.telemetry.annotation;

import static io.harness.rule.OwnerRule.ZHUO;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.MockitoAnnotations.initMocks;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;
import io.harness.telemetry.Destination;
import io.harness.telemetry.TelemetrySdkTestBase;
import io.harness.telemetry.segment.SegmentReporterImpl;

import java.lang.annotation.Annotation;
import java.util.HashMap;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;

@OwnedBy(HarnessTeam.GTM)
public class TrackEventInterceptorTest extends TelemetrySdkTestBase {
  @Mock SegmentReporterImpl segmentReporterImpl;
  @InjectMocks TrackEventInterceptor trackEventInterceptor;
  @Mock SendTrackEvent sendTrackEvent;
  @Captor private ArgumentCaptor<String> eventNameCaptor;
  @Captor private ArgumentCaptor<String> identityCaptor;
  @Captor private ArgumentCaptor<String> accountCaptor;
  @Captor private ArgumentCaptor<HashMap<String, Object>> propertyCaptor;
  @Captor private ArgumentCaptor<Map<Destination, Boolean>> destinationCaptor;
  @Captor private ArgumentCaptor<String> categoryCaptor;
  private Input defaultInput;
  private EventProperty defaultEventProperty;
  private EventProperty exampletEventProperty;

  private static final String EVENT_NAME = "test_event";
  private static final String IDENTITY_NAME = "test";
  private static final String ACCOUNT_ID = "123";
  private static final String INPUT_KEY = "input_key";
  private static final String INPUT_VALUE = "input_value";
  private static final Object[] ARGUMENTS = new Object[] {IDENTITY_NAME, null, ACCOUNT_ID};

  @Before
  public void setUp() {
    initMocks(this);
    defaultInput = new Input() {
      @Override
      public Class<? extends Annotation> annotationType() {
        return Input.class;
      }

      @Override
      public String value() {
        return "";
      }

      @Override
      public int argumentIndex() {
        return -1;
      }
    };

    defaultEventProperty = new EventProperty() {
      @Override
      public Class<? extends Annotation> annotationType() {
        return EventProperty.class;
      }

      @Override
      public String key() {
        return "";
      }

      @Override
      public Input value() {
        return defaultInput;
      }
    };

    Input constStringInput = new Input() {
      @Override
      public Class<? extends Annotation> annotationType() {
        return Input.class;
      }

      @Override
      public String value() {
        return INPUT_VALUE;
      }

      @Override
      public int argumentIndex() {
        return -1;
      }
    };
    exampletEventProperty = new EventProperty() {
      @Override
      public Class<? extends Annotation> annotationType() {
        return EventProperty.class;
      }

      @Override
      public String key() {
        return INPUT_KEY;
      }

      @Override
      public Input value() {
        return constStringInput;
      }
    };
  }

  @Test
  @Owner(developers = ZHUO)
  @Category(UnitTests.class)
  public void testSendTrackEventWithIdentityAndAccountId() {
    Mockito.when(sendTrackEvent.identity()).thenReturn(new Input() {
      @Override
      public Class<? extends Annotation> annotationType() {
        return Input.class;
      }

      @Override
      public String value() {
        return null;
      }

      @Override
      public int argumentIndex() {
        return 0;
      }
    });
    Mockito.when(sendTrackEvent.accountId()).thenReturn(new Input() {
      @Override
      public Class<? extends Annotation> annotationType() {
        return Input.class;
      }

      @Override
      public String value() {
        return null;
      }

      @Override
      public int argumentIndex() {
        return 2;
      }
    });

    Mockito.when(sendTrackEvent.destinations()).thenReturn(new Destination[] {Destination.NATERO});
    Mockito.when(sendTrackEvent.eventName()).thenReturn(EVENT_NAME);
    Mockito.when(sendTrackEvent.properties()).thenReturn(new EventProperty[] {exampletEventProperty});
    Mockito.when(sendTrackEvent.category()).thenReturn(io.harness.telemetry.Category.SIGN_UP);

    trackEventInterceptor.processTrackEvent(sendTrackEvent, ARGUMENTS);
    Mockito.verify(segmentReporterImpl)
        .sendTrackEvent(eventNameCaptor.capture(), identityCaptor.capture(), accountCaptor.capture(),
            propertyCaptor.capture(), destinationCaptor.capture(), categoryCaptor.capture());
    assertThat(eventNameCaptor.getValue()).isEqualTo(EVENT_NAME);
    assertThat(identityCaptor.getValue()).isEqualTo(IDENTITY_NAME);
    assertThat(accountCaptor.getValue()).isEqualTo(ACCOUNT_ID);
    assertThat(propertyCaptor.getValue().get(INPUT_KEY)).isEqualTo(INPUT_VALUE);
    assertThat(destinationCaptor.getValue().get(Destination.NATERO)).isEqualTo(true);
    assertThat(destinationCaptor.getValue().get(Destination.ALL)).isEqualTo(false);
    assertThat(categoryCaptor.getValue()).isEqualTo(io.harness.telemetry.Category.SIGN_UP);
  }

  @Test
  @Owner(developers = ZHUO)
  @Category(UnitTests.class)
  public void testSendTrackEventWithWrongIdentity() {
    Mockito.when(sendTrackEvent.identity()).thenReturn(new Input() {
      @Override
      public Class<? extends Annotation> annotationType() {
        return Input.class;
      }

      @Override
      public String value() {
        return null;
      }

      @Override
      public int argumentIndex() {
        return 1;
      }
    });
    Mockito.when(sendTrackEvent.accountId()).thenReturn(new Input() {
      @Override
      public Class<? extends Annotation> annotationType() {
        return Input.class;
      }

      @Override
      public String value() {
        return null;
      }

      @Override
      public int argumentIndex() {
        return 0;
      }
    });
    Mockito.when(sendTrackEvent.destinations()).thenReturn(new Destination[] {Destination.NATERO});
    Mockito.when(sendTrackEvent.eventName()).thenReturn(EVENT_NAME);
    Mockito.when(sendTrackEvent.properties()).thenReturn(new EventProperty[] {defaultEventProperty});
    Mockito.when(sendTrackEvent.category()).thenReturn(io.harness.telemetry.Category.GLOBAL);

    trackEventInterceptor.processTrackEvent(sendTrackEvent, ARGUMENTS);
    Mockito.verifyNoInteractions(segmentReporterImpl);
  }

  @Test
  @Owner(developers = ZHUO)
  @Category(UnitTests.class)
  public void testSendTrackEventWithWrongAccountId() {
    Mockito.when(sendTrackEvent.identity()).thenReturn(new Input() {
      @Override
      public Class<? extends Annotation> annotationType() {
        return Input.class;
      }

      @Override
      public String value() {
        return null;
      }

      @Override
      public int argumentIndex() {
        return 0;
      }
    });
    Mockito.when(sendTrackEvent.accountId()).thenReturn(new Input() {
      @Override
      public Class<? extends Annotation> annotationType() {
        return Input.class;
      }

      @Override
      public String value() {
        return null;
      }

      @Override
      public int argumentIndex() {
        return 3;
      }
    });
    Mockito.when(sendTrackEvent.destinations()).thenReturn(new Destination[] {Destination.NATERO});
    Mockito.when(sendTrackEvent.eventName()).thenReturn(EVENT_NAME);
    Mockito.when(sendTrackEvent.properties()).thenReturn(new EventProperty[] {defaultEventProperty});

    trackEventInterceptor.processTrackEvent(sendTrackEvent, ARGUMENTS);
    Mockito.verifyNoInteractions(segmentReporterImpl);
  }

  @Test
  @Owner(developers = ZHUO)
  @Category(UnitTests.class)
  public void testSendTrackEventWithoutIdentityAndAccountId() {
    Mockito.when(sendTrackEvent.identity()).thenReturn(defaultInput);
    Mockito.when(sendTrackEvent.accountId()).thenReturn(defaultInput);
    Mockito.when(sendTrackEvent.destinations()).thenReturn(new Destination[] {Destination.NATERO});
    Mockito.when(sendTrackEvent.eventName()).thenReturn(EVENT_NAME);
    Mockito.when(sendTrackEvent.properties()).thenReturn(new EventProperty[] {defaultEventProperty});
    Mockito.when(sendTrackEvent.category()).thenReturn(io.harness.telemetry.Category.SIGN_UP);

    trackEventInterceptor.processTrackEvent(sendTrackEvent, ARGUMENTS);
    Mockito.verify(segmentReporterImpl)
        .sendTrackEvent(eventNameCaptor.capture(), identityCaptor.capture(), accountCaptor.capture(),
            propertyCaptor.capture(), destinationCaptor.capture(), categoryCaptor.capture());
    assertThat(eventNameCaptor.getValue()).isEqualTo(EVENT_NAME);
    assertThat(identityCaptor.getValue()).isNull();
    assertThat(accountCaptor.getValue()).isNull();
    assertThat(propertyCaptor.getValue().size()).isEqualTo(0);
    assertThat(destinationCaptor.getValue().get(Destination.NATERO)).isEqualTo(true);
    assertThat(destinationCaptor.getValue().get(Destination.ALL)).isEqualTo(false);
    assertThat(categoryCaptor.getValue()).isEqualTo(io.harness.telemetry.Category.SIGN_UP);
  }
}
