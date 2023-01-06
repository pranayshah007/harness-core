/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package software.wings.notification;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.queue.QueueConsumer;
import io.harness.queue.QueueListener;

import software.wings.persistence.mail.EmailData;
import software.wings.service.intfc.EmailNotificationService;

import com.google.inject.Inject;

/**
 * Created by peeyushaggarwal on 5/24/16.
 *
 * @see EmailData
 */
@OwnedBy(HarnessTeam.PL)
@TargetModule(HarnessModule._830_NOTIFICATION_SERVICE)
public class EmailNotificationListener extends QueueListener<EmailData> {
  @Inject private EmailNotificationService emailNotificationService;

  @Inject
  public EmailNotificationListener(QueueConsumer<EmailData> queueConsumer) {
    super(queueConsumer, true);
  }

  /* (non-Javadoc)
   * @see software.wings.core.queue.QueueListener#onMessage(software.wings.core.queue.Queuable)
   */
  @Override
  public void onMessage(EmailData message) {
    emailNotificationService.send(message);
  }
}
