// Copyright 2021 Harness Inc. All rights reserved.
// Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
// that can be found in the licenses directory at the root of this repository, also available at
// https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.

package types

// The following structs represent a set of structs needed to support ti version info.

// Version infomation.
type VersionInfo struct{
    BuildNo string   `json:"buildNo"`
    GitBranch string `json:"gitBranch"`
    GitCommit string `json:"gitCommit"`
    Patch string     `json:"patch"`
    Timestamp string `json:"timestamp"`
    Version string   `json:"version"`
}

// Runtime information.
type RuntimeInfo struct{
    DeployMode string     `json:"deployMode"`
    Primary bool          `json:"primary"`
    PrimaryVersion string `json:"primaryVersion"`
}

// Version package is a struct with version information and runtime information.
type VersionPackage struct{
    VersionInfo VersionInfo `json:"versionInfo"`
    RuntimeInfo RuntimeInfo `json:"runtimeInfo"`
}

// Stack trace element.
type StackTraceElement struct{
    ClassLoaderName string `json:"classLoaderName"`
    ClassName string       `json:"className"`
    FileName string        `json:"fileName"`
    LineNumber int         `json:"lineNumber"`
    MethodName string      `json:"methodName"`
    ModuleName string      `json:"moduleName"`
    ModuleVersion string   `json:"moduleVersion"`
    NativeMethod bool      `json:"nativeMethod"`
}

// Throwable represents a recursive type that could be thrown by a program.
type Throwable struct{
    Cause *Throwable               `json:"cause"`
    LocalizedMessage string        `json:"localizedMessage"`
    Message string                 `json:"message"`
    StackTrace []StackTraceElement `json:"stackTrace"`
    Suppressed *[]Throwable        `json:"suppressed"`
}

// ResponseMessage is a response message contained in a version response.
type ResponseMessage struct{
    Code string           `json:"code"`
    Exception Throwable   `json:"exception"`
    FailureTypes []string `json:"failureTypes"`
    Level string          `json:"level"`
    Message string        `json:"message"`
}

// RestResponseVersionPackage is the response object returned by a version query.
type RestResponseVersionPackage struct {
    MetaData map[string]string         `json:"metaData"`
    Resource VersionPackage            `json:"resource"`
    ResponseMessages []ResponseMessage `json:"responseMessages"`
}