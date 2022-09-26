package ok.dht.test.gerasimov;

public class ValidationService {
    public boolean checkId(String id) {
        return !id.isBlank() && id.chars().noneMatch(Character::isWhitespace);
    }
}
