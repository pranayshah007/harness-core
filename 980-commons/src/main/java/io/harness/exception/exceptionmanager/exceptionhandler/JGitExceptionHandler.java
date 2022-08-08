/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.exception.exceptionmanager.exceptionhandler;

import static io.harness.annotations.dev.HarnessTeam.DX;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.exception.WingsException.USER;

import static java.lang.String.format;

import io.harness.annotations.dev.OwnedBy;
import io.harness.exception.ExplanationException;
import io.harness.exception.GitOperationException;
import io.harness.exception.HintException;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.NestedExceptionUtils;
import io.harness.exception.WingsException;
import io.harness.exception.runtime.JGitRuntimeException;

import com.google.common.collect.ImmutableSet;
import com.jcraft.jsch.JSchException;
import java.io.FileNotFoundException;
import java.net.UnknownHostException;
import java.nio.file.NoSuchFileException;
import java.util.Set;
import org.eclipse.jgit.api.errors.InvalidRemoteException;
import org.eclipse.jgit.api.errors.JGitInternalException;
import org.eclipse.jgit.api.errors.RefNotFoundException;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.errors.NoRemoteRepositoryException;
import org.eclipse.jgit.errors.TransportException;

@OwnedBy(DX)
public class JGitExceptionHandler implements ExceptionHandler {
  public static Set<Class<? extends Exception>> exceptions() {
    return ImmutableSet.<Class<? extends Exception>>builder().add(JGitRuntimeException.class).build();
  }
  @Override
  public WingsException handleException(Exception exception) {
    JGitRuntimeException jGitRuntimeException = (JGitRuntimeException) exception;
    Throwable e = jGitRuntimeException.getCause();
    if (e instanceof JGitInternalException && e.getCause() != null) {
      e = e.getCause();
    }

    if (e instanceof InvalidRemoteException || e.getCause() instanceof NoRemoteRepositoryException) {
      return NestedExceptionUtils.hintWithExplanationException(HintException.HINT_INVALID_GIT_REPO,
          ExplanationException.INVALID_GIT_REPO, new InvalidRequestException(e.getMessage(), USER));
    }

    if (e instanceof org.eclipse.jgit.api.errors.TransportException) {
      org.eclipse.jgit.api.errors.TransportException te = (org.eclipse.jgit.api.errors.TransportException) e;
      Throwable cause = te.getCause();
      if (cause instanceof TransportException) {
        TransportException tee = (TransportException) cause;
        if (tee.getCause() instanceof UnknownHostException) {
          return NestedExceptionUtils.hintWithExplanationException(HintException.HINT_INVALID_GIT_HOST,
              ExplanationException.INVALID_GIT_REPO, new InvalidRequestException(e.getMessage(), USER));
        }
        if (tee.getCause() instanceof JSchException) {
          JSchException jSchException = (JSchException) cause.getCause();
          if (jSchException.getMessage().contains("invalid privatekey")) {
            return NestedExceptionUtils.hintWithExplanationException(HintException.HINT_MALFORMED_GIT_SSH_KEY,
                ExplanationException.MALFORMED_GIT_SSH_KEY, new InvalidRequestException(e.getMessage(), USER));
          }
          if (jSchException.getMessage().contains("Auth fail")) {
            return NestedExceptionUtils.hintWithExplanationException(HintException.HINT_INVALID_GIT_SSH_KEY,
                ExplanationException.INVALID_GIT_SSH_AUTHORIZATION, new InvalidRequestException(e.getMessage(), USER));
          }
        }
      }
      if (e.getMessage().contains("not authorized")) {
        return NestedExceptionUtils.hintWithExplanationException(HintException.HINT_INVALID_GIT_AUTHORIZATION,
            ExplanationException.INVALID_GIT_AUTHORIZATION, new InvalidRequestException(e.getMessage(), USER));
      }
      if (e.getMessage().contains("authentication not supported")) {
        return NestedExceptionUtils.hintWithExplanationException(HintException.HINT_INVALID_GIT_AUTHENTICATION,
            ExplanationException.INVALID_GIT_AUTHENTICATION, new InvalidRequestException(e.getMessage(), USER));
      }
    }

    if (e instanceof RefNotFoundException || e instanceof MissingObjectException) {
      if (isNotEmpty(jGitRuntimeException.getBranch())) {
        return NestedExceptionUtils.hintWithExplanationException(HintException.HINT_MISSING_BRANCH,
            format(ExplanationException.EXPLANATION_MISSING_BRANCH, jGitRuntimeException.getBranch()),
            new GitOperationException(e.getMessage()));
      } else {
        return NestedExceptionUtils.hintWithExplanationException(HintException.HINT_MISSING_REFERENCE,
            format(ExplanationException.EXPLANATION_MISSING_REFERENCE, jGitRuntimeException.getCommitId()),
            new GitOperationException(e.getMessage()));
      }
    }

    if (e instanceof FileNotFoundException || e instanceof NoSuchFileException) {
      jGitRuntimeException.setCause(null);
      return NestedExceptionUtils.hintWithExplanationException(
          format(HintException.HINT_GIT_FILE_NOT_FOUND,
              isNotEmpty(jGitRuntimeException.getBranch()) ? "branch: " + jGitRuntimeException.getBranch()
                                                           : "reference: " + jGitRuntimeException.getCommitId()),
          ExplanationException.EXPLANATION_GIT_FILE_NOT_FOUND,
          new GitOperationException(jGitRuntimeException.getMessage()));
    }

    return new InvalidRequestException(e.getMessage(), USER);
  }
}
