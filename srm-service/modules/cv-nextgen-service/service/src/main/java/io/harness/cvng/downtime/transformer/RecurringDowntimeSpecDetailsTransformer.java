/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */
package io.harness.cvng.downtime.transformer;

import io.harness.cvng.downtime.beans.RecurringDowntimeSpecDetails;
import io.harness.cvng.downtime.entities.Downtime.RecurringDowntimeDetails;
import io.harness.cvng.utils.DateTimeParser;

public class RecurringDowntimeSpecDetailsTransformer
    implements DowntimeSpecDetailsTransformer<RecurringDowntimeDetails, RecurringDowntimeSpecDetails> {
  @Override
  public RecurringDowntimeDetails getDowntimeDetails(RecurringDowntimeSpecDetails spec) {
    return RecurringDowntimeDetails.builder()
        .recurrenceEndTime(DateTimeParser.getEpochSecond(spec.getRecurrenceEndTime(), spec.getTimezone()))
        .downtimeDuration(spec.getDowntimeDuration())
        .downtimeRecurrence(spec.getDowntimeRecurrence())
        .build();
  }

  @Override
  public RecurringDowntimeSpecDetails getDowntimeSpec(RecurringDowntimeDetails entity) {
    return RecurringDowntimeSpecDetails.builder()
        .recurrenceEndTime(DateTimeParser.getDateTime(entity.getRecurrenceEndTime(), entity.getTimezone()))
        .downtimeDuration(entity.getDowntimeDuration())
        .downtimeRecurrence(entity.getDowntimeRecurrence())
        .build();
  }
}
