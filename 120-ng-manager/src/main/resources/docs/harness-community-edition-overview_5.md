## Collection at Registration

When a user first installs and signs up in their first Harness CD Community Edition, the system sends a `NEW_SIGNUP` track event and identify event to Harness.

Details of `NEW_SIGNUP` track event:

* Email of the first user who signs up in Harness CD Community Edition.
* Time of registration.
* Public IP address.
* Version of installer.
* Hostname.
* Harness account Id.

Here's an example of the JSON payload:


```json
{  
    "accountId": "tRfsCBTDxxxxxxJcg0g",  
    "category": "SIGNUP",  
    "email": "john.doe@gmail.com",  
    "firstInstallTime": "1641598599622",  
    "groupId": "tRfsCBxxxxxxJcg0g",  
    "hostName": "218864e95d77",  
    "id": "ahnwoP1xxxxxxtROJg",  
    "name": "doe",  
    "remoteIpAddress": "76.22.55.244",  
    "source": "community",  
    "version": "VersionInfo(version=1.0.73225, buildNo=73225, gitCommit=f66ad1343fd5e068615b185caa05c125282826b6, gitBranch=release/on-prem/732xx, timestamp=220105-1908, patch=000)"  
}
```