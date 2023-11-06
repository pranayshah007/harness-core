// Copyright 2022 Harness Inc. All rights reserved.
// Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
// that can be found in the licenses directory at the root of this repository, also available at
// https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.

package client

import (
	"context"
)

// Error represents a json-encoded API error.
type Error struct {
	Code    int    `json:"code"`
	Message string `json:"message"`
}

func (e *Error) Error() string {
	return e.Message
}

type Account struct {
	MetaData struct {
	} `json:"metaData"`
	Resource struct {
		UUID                    string        `json:"uuid"`
		AppID                   string        `json:"appId"`
		CreatedBy               interface{}   `json:"createdBy"`
		CreatedAt               int64         `json:"createdAt"`
		LastUpdatedBy           interface{}   `json:"lastUpdatedBy"`
		LastUpdatedAt           int64         `json:"lastUpdatedAt"`
		CompanyName             string        `json:"companyName"`
		NextGenEnabled          bool          `json:"nextGenEnabled"`
		AccountName             string        `json:"accountName"`
		WhitelistedDomains      []interface{} `json:"whitelistedDomains"`
		LicenseID               interface{}   `json:"licenseId"`
		DataRetentionDurationMs int           `json:"dataRetentionDurationMs"`
		LicenseInfo             struct {
			AccountType   string `json:"accountType"`
			AccountStatus string `json:"accountStatus"`
			ExpiryTime    int64  `json:"expiryTime"`
			LicenseUnits  int    `json:"licenseUnits"`
		} `json:"licenseInfo"`
		TrustLevel    int `json:"trustLevel"`
		CeLicenseInfo struct {
			LicenseType string `json:"licenseType"`
			ExpiryTime  int64  `json:"expiryTime"`
		} `json:"ceLicenseInfo"`
		AccountEvents           interface{} `json:"accountEvents"`
		SubdomainURL            string      `json:"subdomainUrl"`
		TwoFactorAdminEnforced  bool        `json:"twoFactorAdminEnforced"`
		ForImport               bool        `json:"forImport"`
		MigratedToClusterURL    interface{} `json:"migratedToClusterUrl"`
		DefaultExperience       string      `json:"defaultExperience"`
		CreatedFromNG           bool        `json:"createdFromNG"`
		AccountActivelyUsed     bool        `json:"accountActivelyUsed"`
		SmpAccount              bool        `json:"smpAccount"`
		SessionTimeOutInMinutes int         `json:"sessionTimeOutInMinutes"`
		PublicAccessEnabled     bool        `json:"publicAccessEnabled"`
		LocalEncryptionEnabled  bool        `json:"localEncryptionEnabled"`
		DelegateConfiguration   struct {
			DelegateVersions     []string    `json:"delegateVersions"`
			Action               interface{} `json:"action"`
			ValidUntil           int64       `json:"validUntil"`
			ValidTillNextRelease bool        `json:"validTillNextRelease"`
		} `json:"delegateConfiguration"`
		TechStacks             interface{} `json:"techStacks"`
		OauthEnabled           bool        `json:"oauthEnabled"`
		RingName               string      `json:"ringName"`
		AccountPreferences     interface{} `json:"accountPreferences"`
		CloudCostEnabled       bool        `json:"cloudCostEnabled"`
		CeAutoCollectK8SEvents bool        `json:"ceAutoCollectK8sEvents"`
		TrialSignupOptions     struct {
			ProductsSelected []string `json:"productsSelected"`
			AssistedOption   bool     `json:"assistedOption"`
		} `json:"trialSignupOptions"`
		ServiceGuardLimit           int         `json:"serviceGuardLimit"`
		ServiceAccountConfig        interface{} `json:"serviceAccountConfig"`
		GlobalDelegateAccount       bool        `json:"globalDelegateAccount"`
		ImmutableDelegateEnabled    bool        `json:"immutableDelegateEnabled"`
		OptionalDelegateTaskLimit   interface{} `json:"optionalDelegateTaskLimit"`
		ImportantDelegateTaskLimit  interface{} `json:"importantDelegateTaskLimit"`
		AuthenticationMechanism     string      `json:"authenticationMechanism"`
		ProductLed                  bool        `json:"productLed"`
		PovAccount                  bool        `json:"povAccount"`
		HarnessSupportAccessAllowed bool        `json:"harnessSupportAccessAllowed"`
		Defaults                    struct {
		} `json:"defaults"`
		Encryption                   interface{} `json:"encryption"`
		CrossGenerationAccessEnabled bool        `json:"crossGenerationAccessEnabled"`
	} `json:"resource"`
	ResponseMessages []interface{} `json:"responseMessages"`
}

// Client defines a log service client.
type Client interface {
	// Validate apikey of an account for auth.
	ValidateApiKey(ctx context.Context, accountID, routingId, apiKey string) error
}
