# Limitations

Harness deployment logging has the following limitations:

* A hard limit of 25MB for logs produced by 1 Stage step. Logs beyond this limit will be skipped and not available for download as well.
* Harness always saves the final log line that contains the status (Success, Failure, etc) even if logs go beyond the limit.
* In cases where the final log line is itself very large and logs are already beyond max limit (25MB), Harness shows a limited portion of the line from the end (10KB of data for the log line).

