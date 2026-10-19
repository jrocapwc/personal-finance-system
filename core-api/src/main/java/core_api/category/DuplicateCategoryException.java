package core_api.category;

public class DuplicateCategoryException extends RuntimeException {
    public DuplicateCategoryException() {
        super("A category with this name and type already exists");
    }
}
