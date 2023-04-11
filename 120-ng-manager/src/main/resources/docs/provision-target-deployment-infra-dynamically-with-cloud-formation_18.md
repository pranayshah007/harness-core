## Tags

Tags are arbitrary key-value pairs that can be used to identify your stack for purposes such as cost allocation.

A **Key** consists of any alphanumeric characters or spaces. Tag keys can be up to 127 characters long.

A **Value** consists of any alphanumeric characters or spaces. Tag values can be up to 255 characters long.

1. Enter the tags in JSON or YAML (lowercase is required).

JSON example:

```json
{
  "Key" : String,
  "Value" : String
}
```

YAML example:

```yaml
Key: String
Value: String
```

Harness supports [CloudFormation-compliant JSON or YAML for tags](https://docs.aws.amazon.com/AWSCloudFormation/latest/UserGuide/aws-properties-resource-tags.html).
