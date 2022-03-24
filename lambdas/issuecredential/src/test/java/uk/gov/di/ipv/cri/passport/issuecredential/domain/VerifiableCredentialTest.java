package uk.gov.di.ipv.cri.passport.issuecredential.domain;

import org.junit.jupiter.api.Test;
import uk.gov.di.ipv.cri.passport.library.domain.DcsResponse;
import uk.gov.di.ipv.cri.passport.library.domain.PassportAttributes;
import uk.gov.di.ipv.cri.passport.library.domain.verifiablecredential.Evidence;
import uk.gov.di.ipv.cri.passport.library.domain.verifiablecredential.Gpg45Evidence;
import uk.gov.di.ipv.cri.passport.library.domain.verifiablecredential.VerifiableCredential;
import uk.gov.di.ipv.cri.passport.library.persistence.item.PassportCheckDao;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class VerifiableCredentialTest {

    public static final String FAMILY_NAME = "familyName";
    public static final List<String> GIVEN_NAMES = List.of("givenNames");
    public static final String PASSPORT_NUMBER = "passportNumber";
    public static final LocalDate DATE_OF_BIRTH = LocalDate.now();
    public static final LocalDate EXPIRY_DATE = LocalDate.now();
    public static final String RESOURCE_ID = "resourceId";

    @Test
    void shouldConvertPassportCheckDaoToPassportCredentialIssuerResponse() {

        PassportAttributes attributes =
                new PassportAttributes(
                        PASSPORT_NUMBER, FAMILY_NAME, GIVEN_NAMES, DATE_OF_BIRTH, EXPIRY_DATE);
        Evidence evidence = new Evidence(new Gpg45Evidence(4, 4));
        attributes.setDcsResponse(
                new DcsResponse(
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        true,
                        false,
                        Collections.emptyList()));
        PassportCheckDao passportCheckDao = new PassportCheckDao(RESOURCE_ID, attributes, evidence);

        VerifiableCredential verifiableCredential =
                VerifiableCredential.fromPassportCheckDao(passportCheckDao);

        assertEquals(RESOURCE_ID, verifiableCredential.getResourceId());
        assertEquals(
                FAMILY_NAME,
                verifiableCredential
                        .getCredentialSubject()
                        .getName()
                        .getNameParts()
                        .get(1)
                        .getValue());
        assertEquals(
                GIVEN_NAMES.get(0),
                verifiableCredential
                        .getCredentialSubject()
                        .getName()
                        .getNameParts()
                        .get(0)
                        .getValue());
        assertEquals(
                PASSPORT_NUMBER, verifiableCredential.getCredentialSubject().getPassportNumber());
        assertEquals(DATE_OF_BIRTH, verifiableCredential.getCredentialSubject().getBirthDate());
        assertEquals(EXPIRY_DATE, verifiableCredential.getCredentialSubject().getExpiryDate());
        assertEquals(
                passportCheckDao.getAttributes().getRequestId(),
                verifiableCredential.getCredentialSubject().getRequestId());
        assertEquals(
                passportCheckDao.getAttributes().getCorrelationId(),
                verifiableCredential.getCredentialSubject().getCorrelationId());
        assertEquals(
                passportCheckDao.getAttributes().getDcsResponse(),
                verifiableCredential.getCredentialSubject().getDcsResponse());
    }
}
