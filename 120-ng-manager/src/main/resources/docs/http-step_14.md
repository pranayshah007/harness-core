# Header capability check

When Harness runs an HTTP step and connects to a service, it checks to make sure that an HTTP connection can be established.

Some services require HTTP headers to be included in connections. Without the headers, the HTTP connections fail and simple HTTP verification cannot be performed.

Harness performs an HTTP capability check with the headers included on the target service.

If the target host server require headers and you do not include headers in the **Headers** setting of the HTTP step, the Harness delegate will fail the deployment with the error `No eligible delegates could perform this task` (`error 400`).

Add the required headers in **Headers** and run the deployment. Adding the headers will prevent the `400` error.

Harness secrets are not used during capability checks. They are used in actual steps only. If you have a step configured with URLs and multiple headers like:

`x-api-key : <+secret.getValue('apikey')>`   
`content-type : application/json`

During a capability check, the non-secret headers are used as is but the secret headers are masked. Harness makes the HTTP request with the URL and headers as follows:

`x-api-key:<<<api_key>>>`  
`content-type:application/json`

This results in a `401 Unauthorized` response due to an incorrect api key. However, the capability check will be successful and the task will be assigned to the Harness delegate. 

:::note
Using `<<<and>>>` in HTTP requests might result in bad requests on the server side. In such cases, follow these workarounds.

* Use FF `CDS_NOT_USE_HEADERS_FOR_HTTP_CAPABILTY` to not use headers for capability checks.
* Use Shell script to run cURL command and manually process the response. 

:::

Capability checks are basic accessibilty checks and do not follow multiple redirects. Hence, Harness returns from the first `302 Found` response during capability checks. 

