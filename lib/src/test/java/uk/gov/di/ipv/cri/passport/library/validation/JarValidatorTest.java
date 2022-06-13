package uk.gov.di.ipv.cri.passport.library.validation;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWEObject;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.ECDSASigner;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jwt.JWTClaimNames;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.nimbusds.oauth2.sdk.ErrorObject;
import com.nimbusds.oauth2.sdk.OAuth2Error;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.ssm.model.ParameterNotFoundException;
import uk.gov.di.ipv.cri.passport.library.exceptions.JarValidationException;
import uk.gov.di.ipv.cri.passport.library.exceptions.RecoverableJarValidationException;
import uk.gov.di.ipv.cri.passport.library.service.ConfigurationService;
import uk.gov.di.ipv.cri.passport.library.service.KmsRsaDecrypter;

import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.text.ParseException;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.Collections;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static uk.gov.di.ipv.cri.passport.library.helpers.fixtures.TestFixtures.EC_PRIVATE_KEY_1;
import static uk.gov.di.ipv.cri.passport.library.helpers.fixtures.TestFixtures.EC_PUBLIC_JWK_1;
import static uk.gov.di.ipv.cri.passport.library.helpers.fixtures.TestFixtures.EC_PUBLIC_JWK_2;
import static uk.gov.di.ipv.cri.passport.library.helpers.fixtures.TestFixtures.RSA_PRIVATE_KEY;
import static uk.gov.di.ipv.cri.passport.library.helpers.fixtures.TestFixtures.RSA_PUBLIC_KEY;

@ExtendWith(MockitoExtension.class)
class JarValidatorTest {

    @Mock private ConfigurationService configurationService;

    @Mock private KmsRsaDecrypter kmsRsaDecrypter;

    private JarValidator jarValidator;

    private final String audienceClaim = "test-audience";
    private final String issuerClaim = "test-issuer";
    private final String subjectClaim = "test-subject";
    private final String responseTypeClaim = "code";
    private final String clientIdClaim = "test-client-id";
    private final String redirectUriClaim = "https://example.com";
    private final String stateClaim = "af0ifjsldkj";

    @BeforeEach
    void setUp() {
        jarValidator = new JarValidator(kmsRsaDecrypter, configurationService);
    }

    @Test
    void shouldReturnSignedJwtOnSuccessfulDecryption()
            throws JOSEException, NoSuchAlgorithmException, InvalidKeySpecException, ParseException,
                    JarValidationException {
        SignedJWT signedJWT = generateJWT(getValidClaimsSetValues());
        when(kmsRsaDecrypter.decrypt(any(), any(), any(), any(), any()))
                .thenReturn(signedJWT.serialize().getBytes(StandardCharsets.UTF_8));

        String jweObjectString =
                "eyJ0eXAiOiJKV0UiLCJlbmMiOiJBMTI4Q0JDLUhTMjU2IiwiYWxnIjoiUlNBLU9BRVAtMjU2In0.ZpVOfw61XyBBgsR4CRNRMn2oj_S65pMJO-iaEHpR6QrPcIuD4ysZexolo28vsZyZNR-kfVdw_5CjQanwMS-yw3U3nSUvXUrTs3uco-FSXulIeDYTRbBtQuDyvBMVoos6DyIfC6eBj30GMe5g6DF5KJ1Q0eXQdF0kyM9olg76uYAUqZ5rW52rC_SOHb5_tMj7UbO2IViIStdzLgVfgnJr7Ms4bvG0C8-mk4Otd7m2Km2-DNyGaNuFQSKclAGu7Zgg-qDyhH4V1Z6WUHt79TuG4TxseUr-6oaFFVD23JYSBy7Aypt0321ycq13qcN-PBiOWtumeW5-_CQuHLaPuOc4-w.RO9IB2KcS2hD3dWlKXSreQ.93Ntu3e0vNSYv4hoMwZ3Aw.YRvWo4bwsP_l7dL_29imGg";

        SignedJWT decryptedJwt = jarValidator.decryptJWE(JWEObject.parse(jweObjectString));

        JWTClaimsSet claimsSet = decryptedJwt.getJWTClaimsSet();
        assertEquals(redirectUriClaim, claimsSet.getStringClaim("redirect_uri"));
        assertEquals(Collections.singletonList(audienceClaim), claimsSet.getAudience());
        assertEquals(subjectClaim, claimsSet.getSubject());
    }

    @Test
    void shouldThrowExceptionIfDecryptionFails() throws ParseException {
        String jweObjectString =
                "eyJ0eXAiOiJKV0UiLCJlbmMiOiJBMTI4Q0JDLUhTMjU2IiwiYWxnIjoiUlNBLU9BRVAtMjU2In0.ZpVOfw61XyBBgsR4CRNRMn2oj_S65pMJO-iaEHpR6QrPcIuD4ysZexolo28vsZyZNR-kfVdw_5CjQanwMS-yw3U3nSUvXUrTs3uco-FSXulIeDYTRbBtQuDyvBMVoos6DyIfC6eBj30GMe5g6DF5KJ1Q0eXQdF0kyM9olg76uYAUqZ5rW52rC_SOHb5_tMj7UbO2IViIStdzLgVfgnJr7Ms4bvG0C8-mk4Otd7m2Km2-DNyGaNuFQSKclAGu7Zgg-qDyhH4V1Z6WUHt79TuG4TxseUr-6oaFFVD23JYSBy7Aypt0321ycq13qcN-PBiOWtumeW5-_CQuHLaPuOc4-w.RO9IB2KcS2hD3dWlKXSreQ.93Ntu3e0vNSYv4hoMwZ3Aw.YRvWo4bwsP_l7dL_29imGg";
        try {
            jarValidator.decryptJWE(JWEObject.parse(jweObjectString));
            fail("Should throw a SharedAttributesValidationException");
        } catch (JarValidationException e) {
            assertEquals(
                    OAuth2Error.INVALID_REQUEST_OBJECT.getCode(), e.getErrorObject().getCode());
        }
    }

    @Test
    void shouldPassValidationChecksOnValidJARRequest()
            throws NoSuchAlgorithmException, InvalidKeySpecException, JOSEException,
                    ParseException {
        when(configurationService.getClientSigningPublicJwk(anyString()))
                .thenReturn(ECKey.parse(EC_PUBLIC_JWK_1));
        when(configurationService.getAudienceForClients()).thenReturn(audienceClaim);
        when(configurationService.getClientIssuer(anyString())).thenReturn(issuerClaim);
        when(configurationService.getMaxClientAuthTokenTtl()).thenReturn("1500");
        when(configurationService.getClientRedirectUrls(anyString()))
                .thenReturn(Collections.singletonList(redirectUriClaim));

        SignedJWT signedJWT = generateJWT(getValidClaimsSetValues());

        try {
            jarValidator.validateRequestJwt(signedJWT, clientIdClaim);
        } catch (JarValidationException e) {
            e.printStackTrace();
            fail();
        }
    }

    @Test
    void shouldFailValidationChecksOnInvalidClientId()
            throws NoSuchAlgorithmException, InvalidKeySpecException, JOSEException,
                    ParseException {
        when(configurationService.getClientIssuer(anyString()))
                .thenThrow(ParameterNotFoundException.builder().build());

        SignedJWT signedJWT = generateJWT(getValidClaimsSetValues());

        try {
            jarValidator.validateRequestJwt(signedJWT, clientIdClaim);
            fail();
        } catch (RecoverableJarValidationException e) {
            fail("Error should not be recoverable");
        } catch (JarValidationException e) {
            ErrorObject errorObject = e.getErrorObject();
            assertEquals(
                    OAuth2Error.INVALID_CLIENT.getHTTPStatusCode(),
                    errorObject.getHTTPStatusCode());
            assertEquals(OAuth2Error.INVALID_CLIENT.getCode(), errorObject.getCode());
            assertEquals("Unknown client id was provided", errorObject.getDescription());
        }
    }

    @Test
    void shouldFailValidationChecksOnValidJWTalgHeader()
            throws NoSuchAlgorithmException, InvalidKeySpecException, JOSEException,
                    ParseException {
        RSASSASigner signer = new RSASSASigner(getRsaPrivateKey());

        SignedJWT signedJWT =
                new SignedJWT(
                        new JWSHeader.Builder(JWSAlgorithm.RS256).type(JOSEObjectType.JWT).build(),
                        generateClaimsSet(getValidClaimsSetValues()));
        signedJWT.sign(signer);

        try {
            jarValidator.validateRequestJwt(signedJWT, clientIdClaim);
            fail();
        } catch (RecoverableJarValidationException e) {
            fail("Error should not be recoverable");
        } catch (JarValidationException e) {
            ErrorObject errorObject = e.getErrorObject();
            assertEquals(
                    OAuth2Error.INVALID_REQUEST_OBJECT.getHTTPStatusCode(),
                    errorObject.getHTTPStatusCode());
            assertEquals(OAuth2Error.INVALID_REQUEST_OBJECT.getCode(), errorObject.getCode());
            assertEquals(
                    "Signing algorithm used does not match required algorithm",
                    errorObject.getDescription());
        }
    }

    @Test
    void shouldFailValidationChecksOnInvalidJWTSignature()
            throws NoSuchAlgorithmException, InvalidKeySpecException, JOSEException,
                    ParseException {
        when(configurationService.getClientSigningPublicJwk(anyString()))
                .thenReturn(ECKey.parse(EC_PUBLIC_JWK_2));

        SignedJWT signedJWT = generateJWT(getValidClaimsSetValues());

        try {
            jarValidator.validateRequestJwt(signedJWT, clientIdClaim);
            fail();
        } catch (RecoverableJarValidationException e) {
            fail("Error should not be recoverable");
        } catch (JarValidationException e) {
            ErrorObject errorObject = e.getErrorObject();
            assertEquals(
                    OAuth2Error.INVALID_REQUEST_OBJECT.getHTTPStatusCode(),
                    errorObject.getHTTPStatusCode());
            assertEquals(OAuth2Error.INVALID_REQUEST_OBJECT.getCode(), errorObject.getCode());
            assertEquals("JWT signature validation failed", errorObject.getDescription());
        }
    }

    @Test
    void shouldFailValidationChecksOnInvalidPublicJwk()
            throws NoSuchAlgorithmException, InvalidKeySpecException, JOSEException,
                    ParseException {
        when(configurationService.getClientSigningPublicJwk(anyString()))
                .thenThrow(new ParseException("test-error", 0));

        SignedJWT signedJWT = generateJWT(getValidClaimsSetValues());

        try {
            jarValidator.validateRequestJwt(signedJWT, clientIdClaim);
            fail();
        } catch (RecoverableJarValidationException e) {
            fail("Error should not be recoverable");
        } catch (JarValidationException e) {
            ErrorObject errorObject = e.getErrorObject();
            assertEquals(
                    OAuth2Error.INVALID_REQUEST_OBJECT.getHTTPStatusCode(),
                    errorObject.getHTTPStatusCode());
            assertEquals(OAuth2Error.INVALID_REQUEST_OBJECT.getCode(), errorObject.getCode());
            assertEquals(
                    "Failed to parse JWT when attempting signature validation",
                    errorObject.getDescription());
        }
    }

    @Test
    void shouldFailValidationChecksOnMissingRequiredClaimWithRecoverableError()
            throws NoSuchAlgorithmException, InvalidKeySpecException, JOSEException, ParseException,
                    JarValidationException {
        when(configurationService.getClientRedirectUrls(anyString()))
                .thenReturn(Collections.singletonList(redirectUriClaim));
        when(configurationService.getClientSigningPublicJwk(anyString()))
                .thenReturn(ECKey.parse(EC_PUBLIC_JWK_1));
        when(configurationService.getAudienceForClients()).thenReturn(audienceClaim);
        when(configurationService.getClientIssuer(anyString())).thenReturn(issuerClaim);

        ECDSASigner signer = new ECDSASigner(getPrivateKey());

        JWTClaimsSet claimsSet =
                new JWTClaimsSet.Builder()
                        .claim(JWTClaimNames.AUDIENCE, audienceClaim)
                        .claim("redirect_uri", redirectUriClaim)
                        .build();

        SignedJWT signedJWT =
                new SignedJWT(
                        new JWSHeader.Builder(JWSAlgorithm.ES256).type(JOSEObjectType.JWT).build(),
                        claimsSet);
        signedJWT.sign(signer);

        try {
            jarValidator.validateRequestJwt(signedJWT, clientIdClaim);
            fail();
        } catch (RecoverableJarValidationException e) {
            ErrorObject errorObject = e.getErrorObject();
            assertEquals(
                    OAuth2Error.INVALID_GRANT.getHTTPStatusCode(), errorObject.getHTTPStatusCode());
            assertEquals(OAuth2Error.INVALID_GRANT.getCode(), errorObject.getCode());
            assertEquals(
                    "JWT missing required claims: [exp, iat, iss, nbf, response_type, sub]",
                    errorObject.getDescription());
            assertEquals(redirectUriClaim, e.getRedirectUri());
        }
    }

    @Test
    void shouldFailValidationChecksOnInvalidAudienceClaimWithRecoverableError()
            throws NoSuchAlgorithmException, InvalidKeySpecException, JOSEException, ParseException,
                    JarValidationException {
        when(configurationService.getClientRedirectUrls(anyString()))
                .thenReturn(Collections.singletonList(redirectUriClaim));
        when(configurationService.getClientSigningPublicJwk(anyString()))
                .thenReturn(ECKey.parse(EC_PUBLIC_JWK_1));
        when(configurationService.getAudienceForClients()).thenReturn(audienceClaim);
        when(configurationService.getClientIssuer(anyString())).thenReturn(issuerClaim);

        Map<String, Object> invalidAudienceClaims =
                Map.of(
                        JWTClaimNames.EXPIRATION_TIME,
                        fifteenMinutesFromNow(),
                        JWTClaimNames.ISSUED_AT,
                        OffsetDateTime.now().toEpochSecond(),
                        JWTClaimNames.NOT_BEFORE,
                        OffsetDateTime.now().toEpochSecond(),
                        JWTClaimNames.AUDIENCE,
                        "invalid-audience",
                        JWTClaimNames.ISSUER,
                        issuerClaim,
                        JWTClaimNames.SUBJECT,
                        subjectClaim,
                        "response_type",
                        responseTypeClaim,
                        "client_id",
                        clientIdClaim,
                        "redirect_uri",
                        redirectUriClaim,
                        "state",
                        stateClaim);

        SignedJWT signedJWT = generateJWT(invalidAudienceClaims);

        try {
            jarValidator.validateRequestJwt(signedJWT, clientIdClaim);
            fail();
        } catch (RecoverableJarValidationException e) {
            ErrorObject errorObject = e.getErrorObject();
            assertEquals(
                    OAuth2Error.INVALID_GRANT.getHTTPStatusCode(), errorObject.getHTTPStatusCode());
            assertEquals(OAuth2Error.INVALID_GRANT.getCode(), errorObject.getCode());
            assertEquals("JWT audience rejected: [invalid-audience]", errorObject.getDescription());
            assertEquals(redirectUriClaim, e.getRedirectUri());
        }
    }

    @Test
    void shouldFailValidationChecksOnInvalidIssuerClaimWithRecoverableError()
            throws NoSuchAlgorithmException, InvalidKeySpecException, JOSEException, ParseException,
                    JarValidationException {
        when(configurationService.getClientRedirectUrls(anyString()))
                .thenReturn(Collections.singletonList(redirectUriClaim));
        when(configurationService.getClientSigningPublicJwk(anyString()))
                .thenReturn(ECKey.parse(EC_PUBLIC_JWK_1));
        when(configurationService.getAudienceForClients()).thenReturn(audienceClaim);
        when(configurationService.getClientIssuer(anyString())).thenReturn(issuerClaim);

        Map<String, Object> invalidAudienceClaims =
                Map.of(
                        JWTClaimNames.EXPIRATION_TIME,
                        fifteenMinutesFromNow(),
                        JWTClaimNames.ISSUED_AT,
                        OffsetDateTime.now().toEpochSecond(),
                        JWTClaimNames.NOT_BEFORE,
                        OffsetDateTime.now().toEpochSecond(),
                        JWTClaimNames.AUDIENCE,
                        audienceClaim,
                        JWTClaimNames.ISSUER,
                        "invalid-issuer",
                        JWTClaimNames.SUBJECT,
                        subjectClaim,
                        "response_type",
                        responseTypeClaim,
                        "client_id",
                        clientIdClaim,
                        "redirect_uri",
                        redirectUriClaim,
                        "state",
                        stateClaim);

        SignedJWT signedJWT = generateJWT(invalidAudienceClaims);

        try {
            jarValidator.validateRequestJwt(signedJWT, clientIdClaim);
            fail();
        } catch (RecoverableJarValidationException e) {
            ErrorObject errorObject = e.getErrorObject();
            assertEquals(
                    OAuth2Error.INVALID_GRANT.getHTTPStatusCode(), errorObject.getHTTPStatusCode());
            assertEquals(OAuth2Error.INVALID_GRANT.getCode(), errorObject.getCode());
            assertEquals(
                    "JWT iss claim has value invalid-issuer, must be test-issuer",
                    errorObject.getDescription());
            assertEquals(redirectUriClaim, e.getRedirectUri());
        }
    }

    @Test
    void shouldFailValidationChecksOnInvalidResponseTypeClaimWithRecoverableError()
            throws NoSuchAlgorithmException, InvalidKeySpecException, JOSEException, ParseException,
                    JarValidationException {
        when(configurationService.getClientRedirectUrls(anyString()))
                .thenReturn(Collections.singletonList(redirectUriClaim));
        when(configurationService.getClientSigningPublicJwk(anyString()))
                .thenReturn(ECKey.parse(EC_PUBLIC_JWK_1));
        when(configurationService.getAudienceForClients()).thenReturn(audienceClaim);
        when(configurationService.getClientIssuer(anyString())).thenReturn(issuerClaim);

        Map<String, Object> invalidAudienceClaims =
                Map.of(
                        JWTClaimNames.EXPIRATION_TIME,
                        fifteenMinutesFromNow(),
                        JWTClaimNames.ISSUED_AT,
                        OffsetDateTime.now().toEpochSecond(),
                        JWTClaimNames.NOT_BEFORE,
                        OffsetDateTime.now().toEpochSecond(),
                        JWTClaimNames.AUDIENCE,
                        audienceClaim,
                        JWTClaimNames.ISSUER,
                        issuerClaim,
                        JWTClaimNames.SUBJECT,
                        subjectClaim,
                        "response_type",
                        "invalid-response-type",
                        "client_id",
                        clientIdClaim,
                        "redirect_uri",
                        redirectUriClaim,
                        "state",
                        stateClaim);

        SignedJWT signedJWT = generateJWT(invalidAudienceClaims);

        try {
            jarValidator.validateRequestJwt(signedJWT, clientIdClaim);
            fail();
        } catch (RecoverableJarValidationException e) {
            ErrorObject errorObject = e.getErrorObject();
            assertEquals(
                    OAuth2Error.INVALID_GRANT.getHTTPStatusCode(), errorObject.getHTTPStatusCode());
            assertEquals(OAuth2Error.INVALID_GRANT.getCode(), errorObject.getCode());
            assertEquals(
                    "JWT response_type claim has value invalid-response-type, must be code",
                    errorObject.getDescription());
            assertEquals(redirectUriClaim, e.getRedirectUri());
        }
    }

    @Test
    void shouldFailValidationChecksOnExpiredJWTWithRecoverableError()
            throws NoSuchAlgorithmException, InvalidKeySpecException, JOSEException, ParseException,
                    JarValidationException {
        when(configurationService.getClientRedirectUrls(anyString()))
                .thenReturn(Collections.singletonList(redirectUriClaim));
        when(configurationService.getClientSigningPublicJwk(anyString()))
                .thenReturn(ECKey.parse(EC_PUBLIC_JWK_1));
        when(configurationService.getAudienceForClients()).thenReturn(audienceClaim);
        when(configurationService.getClientIssuer(anyString())).thenReturn(issuerClaim);

        Map<String, Object> invalidAudienceClaims =
                Map.of(
                        JWTClaimNames.EXPIRATION_TIME,
                        fifteenMinutesInPast(),
                        JWTClaimNames.ISSUED_AT,
                        OffsetDateTime.now().toEpochSecond(),
                        JWTClaimNames.NOT_BEFORE,
                        OffsetDateTime.now().toEpochSecond(),
                        JWTClaimNames.AUDIENCE,
                        audienceClaim,
                        JWTClaimNames.ISSUER,
                        issuerClaim,
                        JWTClaimNames.SUBJECT,
                        subjectClaim,
                        "response_type",
                        responseTypeClaim,
                        "client_id",
                        clientIdClaim,
                        "redirect_uri",
                        redirectUriClaim,
                        "state",
                        stateClaim);

        SignedJWT signedJWT = generateJWT(invalidAudienceClaims);

        try {
            jarValidator.validateRequestJwt(signedJWT, clientIdClaim);
            fail();
        } catch (RecoverableJarValidationException e) {
            ErrorObject errorObject = e.getErrorObject();
            assertEquals(
                    OAuth2Error.INVALID_GRANT.getHTTPStatusCode(), errorObject.getHTTPStatusCode());
            assertEquals(OAuth2Error.INVALID_GRANT.getCode(), errorObject.getCode());
            assertEquals("Expired JWT", errorObject.getDescription());
            assertEquals(redirectUriClaim, e.getRedirectUri());
        }
    }

    @Test
    void shouldFailValidationChecksOnFutureNbfClaimWithRecoverableError()
            throws NoSuchAlgorithmException, InvalidKeySpecException, JOSEException, ParseException,
                    JarValidationException {
        when(configurationService.getClientRedirectUrls(anyString()))
                .thenReturn(Collections.singletonList(redirectUriClaim));
        when(configurationService.getClientSigningPublicJwk(anyString()))
                .thenReturn(ECKey.parse(EC_PUBLIC_JWK_1));
        when(configurationService.getAudienceForClients()).thenReturn(audienceClaim);
        when(configurationService.getClientIssuer(anyString())).thenReturn(issuerClaim);

        Map<String, Object> invalidAudienceClaims =
                Map.of(
                        JWTClaimNames.EXPIRATION_TIME,
                        fifteenMinutesFromNow(),
                        JWTClaimNames.ISSUED_AT,
                        OffsetDateTime.now().toEpochSecond(),
                        JWTClaimNames.NOT_BEFORE,
                        fifteenMinutesFromNow(),
                        JWTClaimNames.AUDIENCE,
                        audienceClaim,
                        JWTClaimNames.ISSUER,
                        issuerClaim,
                        JWTClaimNames.SUBJECT,
                        subjectClaim,
                        "response_type",
                        responseTypeClaim,
                        "client_id",
                        clientIdClaim,
                        "redirect_uri",
                        redirectUriClaim,
                        "state",
                        stateClaim);

        SignedJWT signedJWT = generateJWT(invalidAudienceClaims);

        try {
            jarValidator.validateRequestJwt(signedJWT, clientIdClaim);
            fail();
        } catch (RecoverableJarValidationException e) {
            ErrorObject errorObject = e.getErrorObject();
            assertEquals(
                    OAuth2Error.INVALID_GRANT.getHTTPStatusCode(), errorObject.getHTTPStatusCode());
            assertEquals(OAuth2Error.INVALID_GRANT.getCode(), errorObject.getCode());
            assertEquals("JWT before use time", errorObject.getDescription());
            assertEquals(redirectUriClaim, e.getRedirectUri());
        }
    }

    @Test
    void shouldFailValidationChecksOnExpiryClaimToFarInFutureWithRecoverableError()
            throws NoSuchAlgorithmException, InvalidKeySpecException, JOSEException, ParseException,
                    JarValidationException {
        when(configurationService.getClientRedirectUrls(anyString()))
                .thenReturn(Collections.singletonList(redirectUriClaim));
        when(configurationService.getClientSigningPublicJwk(anyString()))
                .thenReturn(ECKey.parse(EC_PUBLIC_JWK_1));
        when(configurationService.getAudienceForClients()).thenReturn(audienceClaim);
        when(configurationService.getClientIssuer(anyString())).thenReturn(issuerClaim);
        when(configurationService.getMaxClientAuthTokenTtl()).thenReturn("1500");

        Map<String, Object> invalidAudienceClaims =
                Map.of(
                        JWTClaimNames.EXPIRATION_TIME,
                        OffsetDateTime.now().plusYears(100).toEpochSecond(),
                        JWTClaimNames.ISSUED_AT,
                        OffsetDateTime.now().toEpochSecond(),
                        JWTClaimNames.NOT_BEFORE,
                        OffsetDateTime.now().toEpochSecond(),
                        JWTClaimNames.AUDIENCE,
                        audienceClaim,
                        JWTClaimNames.ISSUER,
                        issuerClaim,
                        JWTClaimNames.SUBJECT,
                        subjectClaim,
                        "response_type",
                        responseTypeClaim,
                        "client_id",
                        clientIdClaim,
                        "redirect_uri",
                        redirectUriClaim,
                        "state",
                        stateClaim);

        SignedJWT signedJWT = generateJWT(invalidAudienceClaims);

        try {
            jarValidator.validateRequestJwt(signedJWT, clientIdClaim);
            fail();
        } catch (RecoverableJarValidationException e) {
            ErrorObject errorObject = e.getErrorObject();
            assertEquals(
                    OAuth2Error.INVALID_GRANT.getHTTPStatusCode(), errorObject.getHTTPStatusCode());
            assertEquals(OAuth2Error.INVALID_GRANT.getCode(), errorObject.getCode());
            assertEquals(
                    "The client JWT expiry date has surpassed the maximum allowed ttl value",
                    errorObject.getDescription());
            assertEquals(redirectUriClaim, e.getRedirectUri());
        }
    }

    @Test
    void shouldFailValidationChecksOnInvalidRedirectUriClaim()
            throws NoSuchAlgorithmException, InvalidKeySpecException, JOSEException,
                    ParseException {
        when(configurationService.getClientSigningPublicJwk(anyString()))
                .thenReturn(ECKey.parse(EC_PUBLIC_JWK_1));
        when(configurationService.getClientRedirectUrls(anyString()))
                .thenReturn(Collections.singletonList("test-redirect-uri"));

        SignedJWT signedJWT = generateJWT(getValidClaimsSetValues());

        try {
            jarValidator.validateRequestJwt(signedJWT, clientIdClaim);
            fail();
        } catch (RecoverableJarValidationException e) {
            fail("Error should not be recoverable");
        } catch (JarValidationException e) {
            ErrorObject errorObject = e.getErrorObject();
            assertEquals(
                    OAuth2Error.INVALID_GRANT.getHTTPStatusCode(), errorObject.getHTTPStatusCode());
            assertEquals(OAuth2Error.INVALID_GRANT.getCode(), errorObject.getCode());
            assertEquals(
                    "Invalid redirct_uri claim provided for configured client",
                    errorObject.getDescription());
        }
    }

    @Test
    void shouldFailValidationChecksOnParseFailureOfRedirectUri()
            throws NoSuchAlgorithmException, InvalidKeySpecException, JOSEException,
                    ParseException {
        when(configurationService.getClientSigningPublicJwk(anyString()))
                .thenReturn(ECKey.parse(EC_PUBLIC_JWK_1));

        Map<String, Object> claims =
                Map.of(
                        JWTClaimNames.EXPIRATION_TIME,
                        fifteenMinutesFromNow(),
                        JWTClaimNames.ISSUED_AT,
                        OffsetDateTime.now().toEpochSecond(),
                        JWTClaimNames.NOT_BEFORE,
                        OffsetDateTime.now().toEpochSecond(),
                        JWTClaimNames.AUDIENCE,
                        audienceClaim,
                        JWTClaimNames.ISSUER,
                        issuerClaim,
                        JWTClaimNames.SUBJECT,
                        subjectClaim,
                        "response_type",
                        responseTypeClaim,
                        "client_id",
                        clientIdClaim,
                        "redirect_uri",
                        "({[]})./sd-234345////invalid-redirect-uri",
                        "state",
                        stateClaim);

        SignedJWT signedJWT = generateJWT(claims);

        try {
            jarValidator.validateRequestJwt(signedJWT, clientIdClaim);
            fail();
        } catch (RecoverableJarValidationException e) {
            fail("Error should not be recoverable");
        } catch (JarValidationException e) {
            ErrorObject errorObject = e.getErrorObject();
            assertEquals(
                    OAuth2Error.INVALID_REQUEST_OBJECT.getHTTPStatusCode(),
                    errorObject.getHTTPStatusCode());
            assertEquals(OAuth2Error.INVALID_REQUEST_OBJECT.getCode(), errorObject.getCode());
            assertEquals(
                    "Failed to parse JWT claim set in order to access redirect_uri claim",
                    errorObject.getDescription());
        }
    }

    private SignedJWT generateJWT(Map<String, Object> claimsSetValues)
            throws NoSuchAlgorithmException, InvalidKeySpecException, JOSEException {
        ECDSASigner signer = new ECDSASigner(getPrivateKey());

        SignedJWT signedJWT =
                new SignedJWT(
                        new JWSHeader.Builder(JWSAlgorithm.ES256).type(JOSEObjectType.JWT).build(),
                        generateClaimsSet(claimsSetValues));
        signedJWT.sign(signer);

        return signedJWT;
    }

    private Map<String, Object> getValidClaimsSetValues() {
        return Map.of(
                JWTClaimNames.EXPIRATION_TIME,
                fifteenMinutesFromNow(),
                JWTClaimNames.ISSUED_AT,
                OffsetDateTime.now().toEpochSecond(),
                JWTClaimNames.NOT_BEFORE,
                OffsetDateTime.now().toEpochSecond(),
                JWTClaimNames.AUDIENCE,
                audienceClaim,
                JWTClaimNames.ISSUER,
                issuerClaim,
                JWTClaimNames.SUBJECT,
                subjectClaim,
                "response_type",
                responseTypeClaim,
                "client_id",
                clientIdClaim,
                "redirect_uri",
                redirectUriClaim,
                "state",
                stateClaim);
    }

    private JWTClaimsSet generateClaimsSet(Map<String, Object> claimsSetValues) {
        return new JWTClaimsSet.Builder()
                .claim(
                        JWTClaimNames.EXPIRATION_TIME,
                        claimsSetValues.get(JWTClaimNames.EXPIRATION_TIME))
                .claim(JWTClaimNames.ISSUED_AT, claimsSetValues.get(JWTClaimNames.ISSUED_AT))
                .claim(JWTClaimNames.NOT_BEFORE, claimsSetValues.get(JWTClaimNames.NOT_BEFORE))
                .claim(JWTClaimNames.AUDIENCE, claimsSetValues.get(JWTClaimNames.AUDIENCE))
                .claim(JWTClaimNames.ISSUER, claimsSetValues.get(JWTClaimNames.ISSUER))
                .claim(JWTClaimNames.SUBJECT, claimsSetValues.get(JWTClaimNames.SUBJECT))
                .claim("response_type", claimsSetValues.get("response_type"))
                .claim("client_id", claimsSetValues.get("client_id"))
                .claim("redirect_uri", claimsSetValues.get("redirect_uri"))
                .claim("state", claimsSetValues.get("state"))
                .build();
    }

    private ECPrivateKey getPrivateKey() throws InvalidKeySpecException, NoSuchAlgorithmException {
        return (ECPrivateKey)
                KeyFactory.getInstance("EC")
                        .generatePrivate(
                                new PKCS8EncodedKeySpec(
                                        Base64.getDecoder().decode(EC_PRIVATE_KEY_1)));
    }

    private RSAPrivateKey getRsaPrivateKey()
            throws NoSuchAlgorithmException, InvalidKeySpecException {
        return (RSAPrivateKey)
                KeyFactory.getInstance("RSA")
                        .generatePrivate(
                                new PKCS8EncodedKeySpec(
                                        Base64.getDecoder().decode(RSA_PRIVATE_KEY)));
    }

    private RSAPublicKey getPublicKey() throws NoSuchAlgorithmException, InvalidKeySpecException {
        return (RSAPublicKey)
                KeyFactory.getInstance("RSA")
                        .generatePublic(
                                new X509EncodedKeySpec(Base64.getDecoder().decode(RSA_PUBLIC_KEY)));
    }

    private static long fifteenMinutesFromNow() {
        return OffsetDateTime.now().plusSeconds(15 * 60).toEpochSecond();
    }

    private static long fifteenMinutesInPast() {
        return OffsetDateTime.now().minusSeconds(15 * 60).toEpochSecond();
    }
}
