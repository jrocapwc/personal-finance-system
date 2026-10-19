package core_api.category;

public class CategoryInUseException extends RuntimeException {
    public CategoryInUseException() { super("Category is referenced by transactions"); }
}
