## Input values V2

### Scenario 1: Data point with and without input values
Check 1: github.isBranchProtected==true\
Check 2: github.isBranchProtected.develop==true

#### Response Data
```
github: {
    isBranchProtected: { 
        value: true,
        error_messages: ""
    }
}
```
```
github: {
    isBranchProtected: {
        develop: { 
            value: true,
            error_messages: ""
        }
    }
}
```

#### Problem
The second response will overwrite the first one. So currently we cannot support checks where we have one with input value and once without.

#### Proposed Fix
For default branch, we could use some keyword which cannot be used as a branch name. So, instead of directly adding the data, we can add it against this keyword.\
For example: `ref/`
```
github: {
    isBranchProtected: {
        "ref/": { 
            value: true,
            error_messages: ""
        },
        develop: { 
            value: true,
            error_messages: ""
        }
    }
}
```

### Scenario 2: Multiple input values
#### Checks
Check 1: github.fileExists.develop.LICENSE==true\
Check 2: github.fileExists.main.\"README.md\"==true\
Check 3: github.fileExists.develop.\"README.md\"==true

#### Problem
We have 2 inputs here:\
branch = develop/main\
file = LICENSE/README.md

We don't support multiple inputs currently.

#### Solution:
We can chain the input values as shown above.
DSLocation needs to use these chained input values and fetch the required data.
DSParser needs to construct the response in the below format -

```
github: {
    fileExists: {
        develop: { 
            LICENSE: {
                value: true
                error_messages: ""
            },
            README.md: {
                value: false
                error_messages: ""
            }
        }
        main: {
            README.md: {
                value: true
                error_messages: ""
            }
        }
    }
}
```

### Scenario 3
Check: github.fileContents.develop.\"package.json\".engines.node>=18

branch = develop\
file = package.json\
key = engines.node

1. What kind of files will we support? YAML and JSON can be handled easily as we can convert it to map which our jexl parser understands.
2. Many formats are supported - ```engines: {node: 16 || 18}```, ```engines: {node: >=16 <18}```
3. We might need custom handlers for each case. Java version from bazelrc would need its own way of parsing.
4. What about regex?