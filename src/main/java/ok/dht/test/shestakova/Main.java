package ok.dht.test.shestakova;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class Main {
    static String[] codes = {"0c8c221260cce1db204cdc5a1c1a479c", "3f0949c7aed4ad1fa18ea121736fb045", "2124282ef79054783153cf1f02fe4b13", "9d2cc56a6fa6ea70acbc2d6027a1a18a", "c3ce6a886aaa1ebf68ab6d81af055f1a", "4689f60ebfd19478e498f6681b5a042a", "0bcd0de1ae2ed0e21a2794d74796c3cd", "006e38a3adadc9475166fd1b22ada45f", "33039f6e29b8010765df028e45b28074", "0a962aef54896af110c41185238582ea", "fbd6eff06088fc86c92419f161c55856", "5606bd5315d505bafb21374951d248d6", "99d0bd2d576ee3a3e2d257c94d15a2ef", "b86cc0bdfaeaf8b852c345dfebe5fc60", "adb7368334f237748543c31bd4e57f33", "07dbbc9821438928079e6b3197eace2b", "5f6752efae41bbb2672121c18ed5000b", "217bbcb317f4ee487b4fcf0e7f520a86", "202257728ed121d12ff309c6802ec6e9", "74a13ecae2e836a0376c8174b727ccde", "07c0b183d6791189bbc76c92372ea0b0", "fcdda0e5bbd92fe7bb32c780d958b619", "26f1bddd825a9acd85c8781311303821", "a12484815cea88cc0c2e97c7d4d6ed7e", "4b57cc1f0ad28c92f51ac40788aac251", "f81702309bdcc04be3cfd8b21aacebf5", "c4a0f879f01db0e1a5ec2af3b8661a7e", "9fbc2edd45eac13e5f958bbdc1953f23", "427f546ee5db7221ef0043dd4fe94fb7", "a6d8bcb6bca8f5c2c73ce48eb7b07708", "ff12c99c17717a6dc5e340ab39ff0c04", "5dcc38a76bbe7b5e0e5387c488a224e0", "0c6a3c47be97d4714c0e8e7359064a26", "b7ab364da6025ef895d021518513486d", "94926fc4a52bcc01c131d86a062d9cbe", "6faabb9cdc7534e637cfc323fdd49490", "fad24871e9be65e3f996ce4ff8a00756", "18c0f428a2f7e570defd8bb6d1f970ee", "04334ee88ccda7c1c3b41e92cf569b7c", "ff14852d49429e00e988d4b42e90d847", "cc0dd39ab9a35bc96e3b7b361a530801", "ce8f98236a1083d35919ad565dd894e2", "8277411240793a404f7fa9f5aee97b92", "e79121112a5dc3f9ea40a39bceab7246", "df2f920c2957c7126ca04e3bda75b8f9", "35ad84e62b3234e68e400870db66ca84", "359082443dc656cff5e07bf5fc78508e", "3f5fa0fd78d8b49eb0e04437bbbeedc4", "c3d5b3d9f30bddeb1da8f6ec229570f6", "97221220ee89dcdeeddae561cf3e5379", "1208d1acfb39d019955678cfd72dd7b2"};


    public static void main(String[] args) throws NoSuchAlgorithmException {
        for (int i = 0; i < 10; i++) {
            for (int j = 0; j < 10; j++) {
                for (int k = 0; k < 10; k++) {
                    for (int l = 0; l < 10; l++) {
                        for (int m = 0; m < 10; m++) {
                            for (int n = 0; n < 10; n++) {
                                String code = String.valueOf(i) + String.valueOf(j) + String.valueOf(k)
                                        + String.valueOf(l) + String.valueOf(m) + String.valueOf(n);
                                String code_hash = bytesToHex(
                                        MessageDigest.getInstance("MD5").digest(
                                                ("MySecretNuberIs_" + code).getBytes(StandardCharsets.UTF_8)
                                        )
                                );
                                if (code_hash.equals(codes[0])) {
                                    System.out.println(code_hash);
                                    System.out.println(code);
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte aByte : bytes) {
            sb.append(String.format("%02x", aByte));
        }
        return sb.toString();
    }
}
