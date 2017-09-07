package labut.md311.lookbook.file_formats.fb2;

//data class holding notes to show them in Toasts
public class BookNote {
    private final String id;
    private final String section;

    public BookNote(String id, String section) {
        this.id = id;
        this.section = section;
    }

    public String noteId() {
        return this.id;
    }

    public String noteSection() {
        return this.section;
    }
}
