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
public class IdentifyEventInterceptorTest extends TelemetrySdkTestBase {
  @Mock SegmentReporterImpl segmentReporterImpl;
  @InjectMocks IdentifyEventInterceptor identifyEventInterceptor;
  @Mock SendIdentifyEvent sendIdentifyEvent;
  @Captor private ArgumentCaptor<String> identityCapture;
  @Captor private ArgumentCaptor<HashMap<String, Object>> propertyCapture;
  @Captor private ArgumentCaptor<Map<Destination, Boolean>> destinationCapture;

  private static final String IDENTITY_NAME = "test";
  private static final Object[] ARGUMENTS = new Object[] {IDENTITY_NAME, null};
  private Input defaultInput;
  private EventProperty defaultEventProperty;

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
  }

  @Test
  @Owner(developers = ZHUO)
  @Category(UnitTests.class)
  public void testSendIdentifyEventWithIdentity() {
    Mockito.when(sendIdentifyEvent.identity()).thenReturn(new Input() {
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
    Mockito.when(sendIdentifyEvent.properties()).thenReturn(new EventProperty[] {defaultEventProperty});
    Mockito.when(sendIdentifyEvent.destinations()).thenReturn(new Destination[] {Destination.SALESFORCE});

    identifyEventInterceptor.processIdentifyEvent(sendIdentifyEvent, ARGUMENTS);
    Mockito.verify(segmentReporterImpl)
        .sendIdentifyEvent(identityCapture.capture(), propertyCapture.capture(), destinationCapture.capture());
    assertThat(identityCapture.getValue()).isEqualTo(IDENTITY_NAME);
    assertThat(propertyCapture.getValue().size()).isEqualTo(0);
    assertThat(destinationCapture.getValue().get(Destination.SALESFORCE)).isEqualTo(true);
    assertThat(destinationCapture.getValue().get(Destination.ALL)).isEqualTo(false);
  }

  @Test
  @Owner(developers = ZHUO)
  @Category(UnitTests.class)
  public void testSendIdentifyEventWithWrongIdentity() {
    Mockito.when(sendIdentifyEvent.identity()).thenReturn(new Input() {
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
    Mockito.when(sendIdentifyEvent.destinations()).thenReturn(new Destination[] {Destination.NATERO});

    identifyEventInterceptor.processIdentifyEvent(sendIdentifyEvent, ARGUMENTS);
    Mockito.verifyZeroInteractions(segmentReporterImpl);
  }
}
