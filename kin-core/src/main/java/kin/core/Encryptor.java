package kin.core;

interface Encryptor {

    String encrypt(String secret) throws CryptoException;

    String decrypt(String encryptedSecret) throws CryptoException;
}
