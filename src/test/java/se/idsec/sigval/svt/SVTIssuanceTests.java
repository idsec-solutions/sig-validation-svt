package se.idsec.sigval.svt;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.junit.Before;
import org.junit.Test;
import se.idsec.sigval.svt.claims.*;
import se.idsec.sigval.svt.enums.SignerKeyStore;
import se.idsec.sigval.svt.enums.TestData;
import se.idsec.sigval.svt.issuer.SVTIssuer;
import se.idsec.sigval.svt.issuer.SVTModel;

import java.io.InputStream;
import java.math.BigInteger;
import java.security.Security;
import java.security.cert.X509Certificate;
import java.util.*;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import static org.junit.Assert.*;

public class SVTIssuanceTests {

  @Before
  public void init() {
    Security.addProvider(new BouncyCastleProvider());
  }

  @Test
  public void testSvtIssuance() throws Exception {

    InputStream propIs = getClass().getResourceAsStream("/ks.properties");
    Properties prop = new Properties();
    prop.load(propIs);

    SignerKeyStore ecc521KeyStore = new SignerKeyStore(prop.getProperty("keystore.ec521.location"),
      prop.getProperty("keystore.ec521.password"));
    SignerKeyStore rsaKeyStore = new SignerKeyStore(prop.getProperty("keystore.rsa.location"), prop.getProperty("keystore.rsa.password"));

    SVTModel[] model = new SVTModel[] {
      SVTModel.builder()
        .svtIssuerId("https://example.com/svt-issuer")
        .validityPeriod(Long.valueOf("31708800000"))
        .audience(Arrays.asList("http://example.com/audience1"))
        .certRef(true)
        .build(),
      SVTModel.builder()
        .svtIssuerId("https://example.com/svt-issuer")
        .audience(Arrays.asList("http://example.com/audience1"))
        .build()
    };


    SVTIssuer[] svtIssuer = new SVTIssuer[] {
      new SVTSigValClaimsIssuer(JWSAlgorithm.RS256, rsaKeyStore.getPrivate(), rsaKeyStore.getChain()),
      new SVTSigValClaimsIssuer(JWSAlgorithm.PS384, rsaKeyStore.getPrivate(), rsaKeyStore.getChain()),
      new SVTSigValClaimsIssuer(JWSAlgorithm.PS512, rsaKeyStore.getPrivate(), rsaKeyStore.getChain()),
      new SVTSigValClaimsIssuer(JWSAlgorithm.ES512, ecc521KeyStore.getPrivate(), ecc521KeyStore.getChain()),
      new SVTSigValClaimsIssuer(JWSAlgorithm.ES256, ecc521KeyStore.getPrivate(), ecc521KeyStore.getChain()),
    };

    //Model fail test
    try {
      new SVTSigValClaimsIssuer(JWSAlgorithm.HS256, ecc521KeyStore.getPrivate(), ecc521KeyStore.getChain());
      fail("Unsupported algorithm");
    }
    catch (Exception ignored) {
    }

    //Perform tests
    performTest(svtIssuer[0], model[0], 0);
    performTest(svtIssuer[0], model[0], 1);
    performTest(svtIssuer[1], model[0], 2);
    performTest(svtIssuer[2], model[0], 3);
    performTest(svtIssuer[3], model[1], 4);
    performTest(svtIssuer[4], model[1], 5);
  }

  private void performTest(SVTIssuer svtIssuer, SVTModel model, int idx) throws Exception {

    try {
      SignedJWT signedSvtJWT = svtIssuer.getSignedSvtJWT(new byte[] {}, model);

      String headerJson = signedSvtJWT.getHeader().toJSONObject().toJSONString();
      JWTClaimsSet jwtClaimsSet = signedSvtJWT.getJWTClaimsSet();
      String jwtid = jwtClaimsSet.getJWTID();
      BigInteger jtiInt = new BigInteger(jwtid, 16);
      assertTrue("JWT ID is to short", jtiInt.bitLength() > 120);

      Date issueTime = jwtClaimsSet.getIssueTime();
      Date expirationTime = jwtClaimsSet.getExpirationTime();
      //Test issue time
      assertTrue("Issue time is too soon", issueTime.after(new Date(System.currentTimeMillis() - 10000)));
      assertTrue("Issue time is too late", issueTime.before(new Date(System.currentTimeMillis() + 10000)));
      if (expirationTime != null) {
        Calendar validTo = Calendar.getInstance();
        validTo.add(Calendar.YEAR, 1);
        assertTrue("Expiration time is too soon", expirationTime.after(validTo.getTime()));
        validTo.add(Calendar.MONTH, 1);
        assertTrue("Expiration time is too late", expirationTime.before(validTo.getTime()));
      }

      String iatStr = String.valueOf(issueTime.getTime() / 1000);
      String expStr = expirationTime == null ? "NULL" : String.valueOf(expirationTime.getTime() / 1000);

      String claimsJson = jwtClaimsSet.toJSONObject().toJSONString();

      switch (idx) {
      case 0:
        assertEquals("Wrong SVT JOSE Header content", TestData.JSON_HEADER_0, headerJson);
        assertEquals("Wrong SVT JOSE Claims content", TestData.JSON_CLAIMS_0
            .replace("###JWTID###", jwtid)
            .replace("###IAT###", iatStr)
            .replace("###EXP###", expStr)
          , claimsJson);
        break;
      case 2:
        assertEquals("Wrong SVT JOSE Header content", TestData.JSON_HEADER_2, headerJson);
        assertEquals("Wrong SVT JOSE Claims content", TestData.JSON_CLAIMS_2
            .replace("###JWTID###", jwtid)
            .replace("###IAT###", iatStr)
            .replace("###EXP###", expStr)
          , claimsJson);
        break;
      case 3:
        assertEquals("Wrong SVT JOSE Header content", TestData.JSON_HEADER_3, headerJson);
        assertEquals("Wrong SVT JOSE Claims content", TestData.JSON_CLAIMS_3
            .replace("###JWTID###", jwtid)
            .replace("###IAT###", iatStr)
            .replace("###EXP###", expStr)
          , claimsJson);
        break;
      case 4:
        assertEquals("Wrong SVT JOSE Header content", TestData.JSON_HEADER_4, headerJson);
        assertEquals("Wrong SVT JOSE Claims content", TestData.JSON_CLAIMS_4
            .replace("###JWTID###", jwtid)
            .replace("###IAT###", iatStr)
            .replace("###EXP###", expStr)
          , claimsJson);
        break;
      default:
        fail("The present test case should have failed with a thrown exception");
      }

    }
    catch (Exception ex) {
      switch (idx) {
      case 1:
      case 5:
        // This was an expected exception
        break;
      default:
        fail("The present test case resulted in an unexpected exception: " + ex.getMessage());
      }
    }
    Logger.getLogger(SVTIssuanceTests.class.getName()).info("Passed SVT test " + idx);
  }

  private static class SVTSigValClaimsIssuer extends SVTIssuer<byte[]> {

    static List<SignatureClaims> claimsDataList;
    static List<SVTProfile> svtProfiles;
    static int sigCounter = 0;
    static int profileCounter = 0;

    static {
      claimsDataList = getClaimsData();
      svtProfiles = Arrays.asList(SVTProfile.XML, SVTProfile.XML, SVTProfile.XML, SVTProfile.PDF);
    }

    public SVTSigValClaimsIssuer(JWSAlgorithm algorithm, Object privateKey, List<X509Certificate> certificates)
      throws Exception {
      super(algorithm, privateKey, certificates);
    }

    @Override public List<SignatureClaims> verify(byte[] signedDocument, String hashAlgoUri) throws Exception {
      try {
        return Arrays.asList(claimsDataList.get(sigCounter++));
      } catch (Exception ex) {
        return Arrays.asList(claimsDataList.get(claimsDataList.size() -1));
      }
    }

    @Override public SVTProfile getSvtProfile() {
      try {
        return svtProfiles.get(profileCounter++);
      } catch (Exception ex) {
        return svtProfiles.get(svtProfiles.size()-1);
      }
    }

    private static List<SignatureClaims> getClaimsData() {
      String certHash = "NSuFM/vJ+beBlQtQTzmcYh5x7L8WC9E1KPHRA1ioNOlKVGbla9URzYcsisAx2bcsqOhkvVTc3mK9E6ag07hfaw==";
      String sbHash = "3GHV73gElWk1yPZRjFtCPtEfEAGRX/kaJWL3I5fm43tkFo3+1FKdqIA6apYFZz7xT2awj/zvWudHa4OyBaP7aA==";
      String sigHash = "Vdypzu0SfeCiB+FNDicTHbq7e8oKKET+1nWgC+jzyZgjmGOfWXi/5/3El0WmnNJfZ65E+eLjkpeA8gWH23UNVw==";
      String sdHash = "Tw3rePgAhYSHtccYJyRRSzSqEIWMKktI5NWJPzf+KJ1CDUDrmHpO9RSKvwdMForF0gYNAvzuUpEYCzJxgKvSaw==";
      String docRef = "0 74697 79699 37908";
      String pol = "http://id.swedenconnect.se/svt/sigval-policy/chain/01";

      SigReferenceClaims sigReferenceClaims = SigReferenceClaims.builder()
        .sb_hash(sbHash)
        .sig_hash(sigHash)
        .build();

      List<PolicyValidationClaims> policyValidationClaims = Arrays.asList(PolicyValidationClaims.builder()
        .pol(pol)
        .msg("Passed basic validation")
        .res(ValidationConclusion.PASSED)
        .build());

      List<SignedDataClaims> signedDataClaims = Arrays.asList(SignedDataClaims.builder()
        .ref(docRef)
        .hash(sdHash)
        .build());

      List<CertReferenceClaims> certReferenceClaimsList = Arrays.asList(CertReferenceClaims.builder()
          .type(CertReferenceClaims.CertRefType.chain_hash.name())
          .ref(Arrays.asList(certHash))
          .build(),
        null,
        CertReferenceClaims.builder()
          .type(CertReferenceClaims.CertRefType.chain_hash.name())
          .ref(Arrays.asList(certHash))
          .build(),
        CertReferenceClaims.builder()
          .type(CertReferenceClaims.CertRefType.chain_hash.name())
          .ref(Arrays.asList(certHash))
          .build());

      List<SignatureClaims> signatureClaimsList = certReferenceClaimsList.stream()
        .map(certReferenceClaims -> SignatureClaims.builder()
          .sig_ref(sigReferenceClaims)
          .sig_data_ref(signedDataClaims)
          .sig_val(policyValidationClaims)
          .time_val(new ArrayList<>())
          .signer_cert_ref(certReferenceClaims)
          .build())
        .collect(Collectors.toList());

      return signatureClaimsList;
    }
  }

}