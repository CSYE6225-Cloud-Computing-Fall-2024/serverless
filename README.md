# AWS Serverless Lambda Function

This Lambda function is designed to handle events triggered by an Amazon SNS topic. The primary purpose of this function is to process messages received from SNS, retrieve the username from the message, send a verification email to the user, and update an Amazon RDS database.

## Linked Repositories

Explore the two additional repositories that complement this project, containing code for the REST-based CRUD operations APIs (Webapp) developed in Java Enterprise Edition (J2EE) and the Infrastructure as Code (IaC) crafted in Terraform.

- [Webapp](https://github.com/CSYE6225-Cloud-Computing-Fall-2024/webapp)
- [Infrastructure as Code](https://github.com/CSYE6225-Cloud-Computing-Fall-2024/tf-aws-infra)

## Functionality

This function performs the following tasks:

1. Retrieve the base64-decoded body from the SNS message
2. Decode the body and parse the JSON to retrieve the username
3. Send a verification email to the user using SMTP from Mailgun
4. Update database to reflect email sent

## Environment Variables

The following environment variables are configured for this Lambda function:

- **SMTP_HOST**: Host for the SMTP service (default: "smtp.mailgun.org").
- **SMTP_PORT**: Port number for the SMTP service (default: 587).
- **SMTP_USERNAME**: SMTP service username.
- **SMTP_PASSWORD**: SMTP service password.
- **SMTP_VERIFICATION_LINK**: URL for the verification link sent to users.
- **SMTP_FROM_EMAIL**: The email address from which the verification email is sent.
- **DB_HOST**: Endpoint of the Amazon RDS instance.
- **DB_USER**: Username for the Amazon RDS instance.
- **DB_PASSWORD**: Password for the Amazon RDS instance.
- **DB_DATABASE**: Name of the database on Amazon RDS.
- **DB_TABLE**: Name of the table in the database.