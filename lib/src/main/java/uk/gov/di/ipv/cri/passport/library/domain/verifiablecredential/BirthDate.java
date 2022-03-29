package uk.gov.di.ipv.cri.passport.library.domain.verifiablecredential;

import uk.gov.di.ipv.cri.passport.library.annotations.ExcludeFromGeneratedCoverageReport;

@ExcludeFromGeneratedCoverageReport
public class BirthDate {
    private String value;

    public BirthDate() {}

    public BirthDate(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }
}
