## Assets

https://s3.amazonaws.com/wings-assets/email-assets/logo.png
https://s3.amazonaws.com/wings-assets/email-assets/background.png
https://s3.amazonaws.com/wings-assets/email-assets/twitter.png
https://s3.amazonaws.com/wings-assets/email-assets/facebook.png
https://s3.amazonaws.com/wings-assets/email-assets/linkedin.png

## Links

https://www.facebook.com/harness.io
https://www.linkedin.com/company/18249313/
https://twitter.com/harnessio

## Fonts

font-family: 'Source Sans Pro', Tahoma, Verdana, Segoe, sans-serif;

## Tools

Use this tool to convert CSS in generic-template-source.html into inline CSS.

https://htmlemail.io/inline/

## Pages

### add_account-body.ftl

Hi ${(name!"there")?capitalize},
Welcome to ${company} account!

ACCESS YOUR ${(company)?upper_case} ACCOUNT


### add_group-body.ftl


Hi ${(name!"there")?capitalize},
You have been assigned new user groups.
      
ACCESS YOUR ${(company)?upper_case} ACCOUNT

### invite-body.ftl

Hi ${(name!"there")?capitalize},
Welcome to ${company} account! Click below to accept invitation.

SIGNUP

### reset_password-body.ftl

Hi ${(name!"there")?capitalize},
Click below to reset your password. Link expires in 4 hours.

RESET PASSWORD

### password_expiration_warning-body.ftl

Hi ${(name!"there")?capitalize},
Your password will expire in ${passExpirationDays}.
Please click this ${url} if you wish to change your password.

### password_expiration-body.ftl

Hi ${(name!"there")?capitalize},
Your password has expired.

Please click this ${url} if you wish to reset your password.

### signup-body.ftl

Hi ${(name!"there")?capitalize},
Welcome to Harness!

ACTIVATE YOUR HARNESS ACCOUNT