/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.jobs;

import static io.harness.eventsframework.EventsFrameworkConstants.INTERNAL_CHANGE_EVENT_FF;

import io.harness.eventsframework.api.Producer;
import io.harness.eventsframework.featureflag.EventDetails;
import io.harness.eventsframework.featureflag.FeatureFlagEvent;
import io.harness.eventsframework.producer.Message;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import java.util.List;
import lombok.Data;

public class FakeFeatureFlagSRMProducer {
  @Inject @Named(INTERNAL_CHANGE_EVENT_FF) private Producer eventProducer;

  public void publishEvent(FFEventBody ffEventBody) {
    FeatureFlagEvent featureFlagEvent =
        FeatureFlagEvent.newBuilder()
            .setAccountID(ffEventBody.getAccountId())
            .setOrgIdentifier(ffEventBody.getOrgIdentifier())
            .setProjectIdentifier(ffEventBody.getProjectIdentifier())
            .addAllServiceIdentifiers(ffEventBody.getServiceRefs())
            .addAllEnvironmentIdentifiers(ffEventBody.getEnvRefs())
            .setEventDetails(EventDetails.newBuilder()
                                 .setName(ffEventBody.getName())
                                 .setIdentifier("test_Identifier")
                                 .setEventType("test_event_type")
                                 .addAllEventDetails(ffEventBody.getDescriptions())
                                 .setChangeEventDetailsLink(ffEventBody.getChangeEventLink())
                                 .setInternalLinkToEntity(ffEventBody.getInternalChangeLink())
                                 .setUser(ffEventBody.getUser())
                                 .build())
            .setExecutionTime(ffEventBody.getStartTime())
            .setType(ffEventBody.getType())
            .build();

    Message message = Message.newBuilder().setData(featureFlagEvent.toByteString()).build();
    eventProducer.send(message);
  }

  @Data
  public static class FFEventBody {
    String accountId;
    String orgIdentifier;
    String projectIdentifier;
    List<String> serviceRefs;
    List<String> envRefs;
    long startTime;
    long endTime;
    String name;
    String user;
    List<String> descriptions;
    String changeEventLink;
    String internalChangeLink;
    String type;
  }
}
