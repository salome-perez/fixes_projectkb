public class RandomSignerSecretProvider {
  protected byte[] generateNewSecret() {
    byte[] secret = new byte[32]; // 32 bytes = 256 bits
    rand.nextBytes(secret);
    return secret;
  }

}