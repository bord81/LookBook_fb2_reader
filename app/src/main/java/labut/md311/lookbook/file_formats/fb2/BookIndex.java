package labut.md311.lookbook.file_formats.fb2;


import java.util.List;

//data class with headers information from FB2, i.e. title, ISBN
public class BookIndex {

    private final List<String> headers;

    public BookIndex(List<String> headers) {
        this.headers = headers;
    }

    public List<String> index() {
        return this.headers;
    }
}
