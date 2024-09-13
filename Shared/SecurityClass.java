package Shared;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;

public class SecurityClass {

    private static final int GCM_IV_LENGTH = 12;
    private static final int GCM_TAG_LENGTH = 128;
    private static BigInteger secret = BigInteger.valueOf(-1);

    public static BigInteger getSecret() throws IllegalStateException {
        if (secret.equals(BigInteger.valueOf(-1))) {
            throw new IllegalStateException("Compute_C must be called before getting the secret.");
        }
        return secret;
    }

    public static BigInteger computeC(int g, int p) {
        BigInteger P = BigInteger.valueOf(p);
        BigInteger G = BigInteger.valueOf(g);

        BigInteger lowbound = BigInteger.ONE;
        BigInteger highbound = BigInteger.valueOf(p - 1);

        SecureRandom random = new SecureRandom();

        do {
            secret = new BigInteger(highbound.bitLength(), random);
        } while (secret.compareTo(lowbound) < 0 || secret.compareTo(highbound) > 0);

        return G.modPow(secret, P);
    }

    //metodo usato per la cifratura dei dati
    public static byte [] encrypt(String message, String key) {

        byte [] dati;
        try {
            Cipher cphr = Cipher.getInstance("AES/ECB/PKCS5Padding");//recupero l istanza dell AES
            SecretKeySpec sKey = new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "AES");
            cphr.init(Cipher.ENCRYPT_MODE, sKey);//setto il cifrario in modalita cifratura
            dati = cphr.doFinal(message.getBytes(StandardCharsets.UTF_8));//cifro i dati
        }
        catch (Exception e){
            return null;
        }
        return dati;
    }

    //metodo usato per la decifrazione dei dati
    public static String decrypt(byte [] message, String key) {

        String dati = null;
        try {
            Cipher cphr = Cipher.getInstance("AES/ECB/PKCS5Padding");
            SecretKeySpec sKey = new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "AES");
            cphr.init(Cipher.DECRYPT_MODE, sKey);//setto il cifrario in modalita decifrazione
            dati = new String(cphr.doFinal(message), StandardCharsets.UTF_8);
        }
        catch (Exception e) {return null;}
        return dati;
    }

}

