package io.harness.mongo.collation;

public enum CollationCaseLevel {
    TRUE(true),
    FALSE(false);
    private final boolean caseLevel;


    CollationCaseLevel(boolean caseLevel){
        this.caseLevel = caseLevel;
    }

    public boolean getCaseLevel(){
        return this.caseLevel;
    }
}
