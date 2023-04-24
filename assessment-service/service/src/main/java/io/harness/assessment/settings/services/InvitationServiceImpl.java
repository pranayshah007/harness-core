/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.assessment.settings.services;

import static io.harness.data.structure.EmptyPredicate.isEmpty;

import static org.apache.commons.lang3.StringUtils.stripToNull;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.assessment.settings.beans.dto.AssessmentInviteDTO;
import io.harness.assessment.settings.beans.entities.Assessment;
import io.harness.assessment.settings.beans.entities.Organization;
import io.harness.assessment.settings.beans.entities.User;
import io.harness.assessment.settings.beans.entities.UserInvitation;
import io.harness.assessment.settings.repositories.AssessmentRepository;
import io.harness.assessment.settings.repositories.OrganizationRepository;
import io.harness.assessment.settings.repositories.UserInvitationRepository;
import io.harness.assessment.settings.repositories.UserRepository;
import io.harness.delegate.beans.NotificationProcessingResponse;
import io.harness.exception.ExceptionUtils;
import io.harness.notification.SmtpConfig;

import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableList;
import com.google.common.io.Resources;
import com.google.inject.Inject;
import com.google.inject.name.Named;
import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import javax.mail.internet.AddressException;
import javax.mail.internet.InternetAddress;
import javax.ws.rs.BadRequestException;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.mail.DefaultAuthenticator;
import org.apache.commons.mail.Email;
import org.apache.commons.mail.EmailException;
import org.apache.commons.mail.HtmlEmail;

@OwnedBy(HarnessTeam.SEI)
@AllArgsConstructor(onConstructor = @__({ @Inject }))
@Slf4j
public class InvitationServiceImpl implements InvitationService {
  private static final String EMAIL_TEMPLATE = "templates/email_invite.txt";
  private UserRepository userRepository;
  private OrganizationRepository organizationRepository;
  private AssessmentRepository assessmentRepository;
  private SmtpConfig smtpConfig;
  @Inject @Named("baseUrl") private String baseUrl;

  private UserInvitationRepository userInvitationRepository;

  @Override
  public AssessmentInviteDTO sendAssessmentInvite(AssessmentInviteDTO assessmentInviteDTO) {
    // call crunchbase or some other API
    String assessmentId = assessmentInviteDTO.getAssessmentId();
    Optional<Assessment> assessmentOptional =
        assessmentRepository.findFirstByAssessmentIdOrderByVersionDesc(assessmentId);
    if (assessmentOptional.isEmpty()) {
      throw new BadRequestException("Assessment not found, for Id : " + assessmentId);
    }
    // add code to reject generic emails TODO
    String encoded;
    // check for repeat emails and reject those TODO
    for (String userEmail : assessmentInviteDTO.getEmails()) {
      try {
        String organizationId = StringUtils.substringAfter(userEmail, "@");
        Optional<Organization> organizationOptional = organizationRepository.findById(organizationId);
        if (organizationOptional.isEmpty()) {
          organizationRepository.save(
              Organization.builder().id(organizationId).organizationName(organizationId).build());
        }
        encoded = TokenGenerationUtil.generateInviteFromEmail(userEmail, assessmentId);
        String invitedBy = assessmentInviteDTO.getInvitedBy().orElse("Self");
        // check user is not already there TODO
        // tie to correct org.
        Optional<User> optionalUser = userRepository.findOneByUserId(userEmail);
        if (optionalUser.isEmpty()) {
          User user = User.builder().userId(userEmail).organizationId(organizationId).build();
          userRepository.save(user);
        }
        UserInvitation userInvitation = UserInvitation.builder()
                                            .userId(userEmail)
                                            .generatedCode(encoded)
                                            .assessmentId(assessmentId)
                                            .invitedBy(invitedBy)
                                            .build();
        userInvitationRepository.save(userInvitation);
        String surveyLink = baseUrl + "assessment/" + userInvitation.getGeneratedCode();
        String emailBody = Resources.toString(getClass().getClassLoader().getResource(EMAIL_TEMPLATE), Charsets.UTF_8);
        emailBody =
            emailBody.replace("${name!}", assessmentInviteDTO.getEmails().get(0)).replace("${surveyLink!}", surveyLink);

        send(assessmentInviteDTO.getEmails(), new ArrayList<>(), "Invitation for Harness DevOps Efficiency Survey",
            emailBody, smtpConfig);
      } catch (NoSuchAlgorithmException e) {
        //        throw new RuntimeException(e);
        log.error("Cannot invite : {} for assessment {}", userEmail, assessmentId);
      } catch (IOException e) {
        e.printStackTrace();
      }
    }
    // TODO generate a unique code and send it to email.
    // Create the user in DB
    // call sending invite.
    return assessmentInviteDTO;
  }

  private NotificationProcessingResponse send(
      List<String> emailIds, List<String> ccEmailIds, String subject, String body, SmtpConfig smtpConfig) {
    try {
      if (Objects.isNull(stripToNull(body))) {
        log.error("No email body available. Aborting notification request {}", emailIds.toString());
        return NotificationProcessingResponse.trivialResponseWithNoRetries;
      }

      Email email = new HtmlEmail();
      email.setHostName(smtpConfig.getHost());
      email.setSmtpPort(smtpConfig.getPort());

      if (!isEmpty(smtpConfig.getPassword())) {
        email.setAuthenticator(
            new DefaultAuthenticator(smtpConfig.getUsername(), new String(smtpConfig.getPassword())));
      }
      email.setSSLOnConnect(smtpConfig.isUseSSL());
      email.setStartTLSEnabled(smtpConfig.isStartTLS());
      if (smtpConfig.isUseSSL()) {
        email.setSslSmtpPort(Integer.toString(smtpConfig.getPort()));
      }

      try {
        email.setReplyTo(ImmutableList.of(new InternetAddress(smtpConfig.getFromAddress())));
      } catch (AddressException | EmailException e) {
        log.error(ExceptionUtils.getMessage(e), e);
      }
      email.setFrom(smtpConfig.getFromAddress(), "Harness Inc");
      for (String emailId : emailIds) {
        email.addTo(emailId);
      }
      for (String ccEmailId : ccEmailIds) {
        email.addCc(ccEmailId);
      }

      email.setSubject(subject);
      ((HtmlEmail) email).setHtmlMsg(body);
      email.send();
    } catch (EmailException e) {
      log.error("Failed to send email. Check SMTP configuration. notificationId: {}\n{}", emailIds.toString(),
          ExceptionUtils.getMessage(e));
      return NotificationProcessingResponse.nonSent(emailIds.size() + ccEmailIds.size());
    }
    return NotificationProcessingResponse.allSent(emailIds.size() + ccEmailIds.size());
  }
}
