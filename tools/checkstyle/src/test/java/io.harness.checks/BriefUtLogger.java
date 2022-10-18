package io.harness.checks;

import com.puppycrawl.tools.checkstyle.DefaultLogger;
import com.puppycrawl.tools.checkstyle.api.AuditEvent;
import java.io.OutputStream;

/**
 * A brief logger that only display info about errors.
 */
public class BriefUtLogger extends DefaultLogger {
  /**
   * Creates BriefLogger object.
   *
   * @param out output stream for info messages and errors.
   */
  public BriefUtLogger(OutputStream out) {
    super(out, OutputStreamOptions.CLOSE, out, OutputStreamOptions.NONE, new AuditEventUtFormatter());
  }

  @Override
  public void auditStarted(AuditEvent event) {
    // has to NOT log audit started event
  }
}
