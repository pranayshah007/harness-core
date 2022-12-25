/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.notification;

import static io.harness.rule.OwnerRule.ANUBHAW;

import static org.mockito.Mockito.verify;

import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;

import software.wings.WingsBaseTest;
import software.wings.persistence.mail.EmailData;
import software.wings.service.intfc.EmailNotificationService;

import com.google.inject.Inject;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;

/**
 * Created by peeyushaggarwal on 5/25/16.
 */
public class EmailNotificationListenerTest extends WingsBaseTest {
  private static final EmailData testEmailData = EmailData.builder().build();
  @Mock private EmailNotificationService emailNotificationService;

  @InjectMocks @Inject private EmailNotificationListener emailNotificationListener;

  /**
   * Should send email on receiving message.
   *
   * @throws Exception the exception
   */
  @Test
  @Owner(developers = ANUBHAW)
  @Category(UnitTests.class)
  public void shouldSendEmailOnReceivingMessage() throws Exception {
    emailNotificationListener.onMessage(testEmailData);
    verify(emailNotificationService).send(testEmailData);
  }
}
