/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.intfc;

import static io.harness.annotations.dev.HarnessTeam.DEL;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;

import software.wings.persistence.mail.EmailData;

/**
 * Created by peeyushaggarwal on 5/23/16.
 */
@TargetModule(HarnessModule._980_COMMONS)
@OwnedBy(DEL)
public interface EmailNotificationService {
  /**
   * Send.
   *
   * @param emailData the email data
   */
  boolean send(EmailData emailData);

  /**
   * Send async.
   *
   * @param emailData the email data
   */
  void sendAsync(EmailData emailData);

  // For CE team
  boolean sendCeMail(EmailData emailData, boolean isCeMail);
}
