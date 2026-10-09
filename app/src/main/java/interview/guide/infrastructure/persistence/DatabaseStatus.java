package interview.guide.infrastructure.persistence;

public record DatabaseStatus(boolean available) {

    public static DatabaseStatus up() {
        return new DatabaseStatus(true);
    }

    public static DatabaseStatus down() {
        return new DatabaseStatus(false);
    }
}

