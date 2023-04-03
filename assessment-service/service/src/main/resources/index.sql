-- Copyright 2023 Harness Inc. All rights reserved.
-- Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
-- that can be found in the licenses directory at the root of this repository, also available at
-- https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.

db.organizations.createIndex( { "organizationId": 1 }, { unique: true } )
db.users.createIndex( { "userId": 1 }, { unique: true } )
db.userInvitations.createIndex( {userId : 1, assessmentId:1}, { unique: true } )
db.userInvitations.createIndex( { "generatedCode": 1 }, { unique: true } )
db.assessments.createIndex( {assessmentId : 1, version:1}, { unique: true } )
db.assessmentResponses.createIndex( {assessmentId : 1, userId: 1, version:1}, { unique: true } )
db.organizationEvaluations.createIndex( {organizationId: 1, assessmentId : 1, version:1}, { unique: true } )
