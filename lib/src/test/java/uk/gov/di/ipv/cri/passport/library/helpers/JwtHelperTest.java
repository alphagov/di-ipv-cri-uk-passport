package uk.gov.di.ipv.cri.passport.library.helpers;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.di.ipv.cri.passport.library.domain.DcsResponse;
import uk.gov.di.ipv.cri.passport.library.domain.verifiablecredential.CredentialSubject;
import uk.gov.di.ipv.cri.passport.library.domain.verifiablecredential.Evidence;
import uk.gov.di.ipv.cri.passport.library.domain.verifiablecredential.Name;
import uk.gov.di.ipv.cri.passport.library.domain.verifiablecredential.NamePartType;
import uk.gov.di.ipv.cri.passport.library.domain.verifiablecredential.NameParts;
import uk.gov.di.ipv.cri.passport.library.domain.verifiablecredential.VerifiableCredential;

import java.io.ByteArrayInputStream;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.text.ParseException;
import java.time.LocalDate;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(MockitoExtension.class)
class JwtHelperTest {
    public static final String BIRTH_DATE = "2020-02-03";
    public static final String PASSPORT_NUMBER = "passportNumber";
    public static final String VALID_FROM = "2020-03-03";
    public static final String VALID_UNTIL = "2021-04-04";
    public static final String GIVEN_NAME = "Paul";
    public static final String EXPIRY_DATE = "2020-01-01";
    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final String BASE64_PRIVATE_KEY =
            "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";

    private static final String BASE64_CERT =
            "MIIFVjCCAz4CCQDGbJ/u6uFT6DANBgkqhkiG9w0BAQsFADBtMQswCQYDVQQGEwJHQjENMAsGA1UECAwEVGVzdDENMAsGA1UEBwwEVGVzdDENMAsGA1UECgwEVEVzdDENMAsGA1UECwwEVEVzdDENMAsGA1UEAwwEVEVzdDETMBEGCSqGSIb3DQEJARYEVGVzdDAeFw0yMjAxMDcxNTM0NTlaFw0yMzAxMDcxNTM0NTlaMG0xCzAJBgNVBAYTAkdCMQ0wCwYDVQQIDARUZXN0MQ0wCwYDVQQHDARUZXN0MQ0wCwYDVQQKDARURXN0MQ0wCwYDVQQLDARURXN0MQ0wCwYDVQQDDARURXN0MRMwEQYJKoZIhvcNAQkBFgRUZXN0MIICIjANBgkqhkiG9w0BAQEFAAOCAg8AMIICCgKCAgEAy1cVZ1KfFmgFlDQyf/R3LF/Js6jAS2Zzbs8WGSS0ys6Z+XR4x5DTIznZp5cHuuQmqOFylXSw5oGBwMXd2L6NimG9rJnJ4w8Gy5A6ImGsiDZC+3AXRBb5hq/IdDTBjbUqRxAKokSVwotWZt554BdSRPTmlYDujzxnClNKA06Xb/X3rTsgCUmZhUnSVtOzKytP3Bdv88VI5gq5tlZOtKXCB0PnJOqRbBmuL1RNkeTny4ZJW3I2ywSATwDDyDm4pJ8XGGNFKaYYTwr6uNTQ2VHb1FVC33oWbg+Zu9D4p5l7ONicCCF3V+GbvmyeCmHGnXznz0nYX1LFqaKtruEh3/GXyLy5X03Jzq6HhTf1SNFBmzziuCovhbR4v5aFDqAYNPWz+ajOdTUfP1I18c5jR1xGUxEiiLKBZWU1J5mhqCa+0CdI0mi3HwFmluudh47I2Xw++JiqZQpxRqNGcKJOPnWDgKOKXQ/ag37aJkxqoYWk9pQ/pXOdIKm//+B//8nWGo8BA/bfdmMHyzhWWxqtydjie2EZ5ODSdQ+yu1xU5cwP59BEQoU7FKVEGiJa4kzrsI2cgloUPlsPfLENMa5i09exDo//eDB/zNy9ACgGCriov1ex3uv4vHp3WtpZYe+akGEJeP0N5dejs0hkBuX+LUcM30TnQ424tEzcuaJ1F7r4FP0CAwEAATANBgkqhkiG9w0BAQsFAAOCAgEAUh5gZx8S/XoZZoQai2uTyW/lyr1LpXMQyfvdWRr5+/OtFuASG3fAPXOTiUfuqH6Uma8BaXPRbSGWxBOFg0EbyvUY4UczZXZgVqyzkGjD2bVcnGra1OHz2AkcJm7OvzjMUvmXdDiQ8WcKIH16BZVsJFveTffJbM/KxL9UUdSLT0fNw1OvZWN1LxRj+X16B26ZnmaXPdmEC8MfwNcEU63qSlIbAvLg9Dp03weqO1qWR1vI/n1jwqidCUVwT0XF88/pJrds8/8guKlawhp9Yv+jMVYaawBiALR+5PFN56DivtmSVI5uv3oFh5tqJXXn9PhsPcIq0YKGQvvcdZl7vCikS65VzmswXBVFJNsYeeZ5NmiH2ANQd4+BLetgLAoXZxaOJ4nK+3Ml+gMwpZRRAbtixKJQDtVy+Ahuh1TEwTS1CERDYq43LhVYbMcgxdOLpZLvMew2tvJc3HfSWQKuF+NjGn/RwG54GyhjpdbfNZMB/EJXNJMt1j9RSVbPLsWjaENUkZoXE0otSou9tJOR0fwoqBJGUi5GCp98+iBdIQMAvXW5JkoDS6CM1FOfSv9ZXLvfXHOuBfKTDeVNy7u3QvyJ+BdkSc0iH4gj1F2zLHNIaZbDzwRzcDf2s3D1wTtoJ/WxfRSLGBMuUsXSduh9Md1S862N3Ce6wpri1IsgySCP84Y=";

    @Test
    void shouldCreateValidSignedJWT()
            throws JOSEException, ParseException, CertificateException, InvalidKeySpecException,
                    NoSuchAlgorithmException, JsonProcessingException {
        RSASSASigner rsaSigner = new RSASSASigner(getPrivateKey());

        VerifiableCredential verifiableCredential =
                new VerifiableCredential(
                        "test",
                        new CredentialSubject(
                                new Name(
                                        List.of(
                                                new NameParts(
                                                        GIVEN_NAME,
                                                        NamePartType.GIVEN_NAME.getName(),
                                                        VALID_FROM,
                                                        VALID_UNTIL))),
                                PASSPORT_NUMBER,
                                LocalDate.parse(BIRTH_DATE),
                                LocalDate.parse(EXPIRY_DATE),
                                UUID.randomUUID(),
                                UUID.randomUUID(),
                                new DcsResponse()),
                        new Evidence());

        SignedJWT signedJWT = JwtHelper.createSignedJwtFromObject(verifiableCredential, rsaSigner);
        JWTClaimsSet generatedClaims = signedJWT.getJWTClaimsSet();

        assertTrue(
                signedJWT.verify(
                        new RSASSAVerifier((RSAPublicKey) getCertificate().getPublicKey())));

        JsonNode claimsSet = objectMapper.readTree(generatedClaims.toString());

        JsonNode credentialSubjectNode = claimsSet.get("credentialSubject");
        JsonNode nameNode = credentialSubjectNode.get("name");

        assertEquals(GIVEN_NAME, nameNode.get("nameParts").get(0).get("value").asText());
        assertEquals(
                NamePartType.GIVEN_NAME.getName(),
                nameNode.get("nameParts").get(0).get("type").asText());
        assertEquals(VALID_FROM, nameNode.get("nameParts").get(0).get("validFrom").asText());
        assertEquals(VALID_UNTIL, nameNode.get("nameParts").get(0).get("validUntil").asText());
        assertEquals(BIRTH_DATE, credentialSubjectNode.get("birthDate").asText());
        assertEquals(EXPIRY_DATE, credentialSubjectNode.get("expiryDate").asText());
    }

    private Certificate getCertificate() throws CertificateException {
        byte[] binaryCertificate = Base64.getDecoder().decode(BASE64_CERT);
        CertificateFactory factory = CertificateFactory.getInstance("X.509");
        return factory.generateCertificate(new ByteArrayInputStream(binaryCertificate));
    }

    private RSAPrivateKey getPrivateKey() throws InvalidKeySpecException, NoSuchAlgorithmException {
        return (RSAPrivateKey)
                KeyFactory.getInstance("RSA")
                        .generatePrivate(
                                new PKCS8EncodedKeySpec(
                                        Base64.getDecoder().decode(BASE64_PRIVATE_KEY)));
    }
}
