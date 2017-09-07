package labut.md311.lookbook.file_formats.fb2;

import java.util.List;
import java.util.Map;

//'main' data class for parsed FB2 book holding all extracted data
public class BookBody {

    private final Map<String, String> headers;
    private final List<Chapter> chapters;
    private final List<BookNote> notes;
    private final List<BookBinary> bookBinaries;

   public BookBody(Map<String, String> headers, List<Chapter> chapters, List<BookNote> notes, List<BookBinary> bookBinaries) {
        this.headers = headers;
        this.chapters = chapters;
        this.notes = notes;
        this.bookBinaries = bookBinaries;
    }

    public Map<String, String> bookHeaders() {
        return this.headers;
    }

    public List<Chapter> bookChapters() {
        return this.chapters;
    }

    public List<BookNote> bookNotes() {
        return this.notes;
    }

    public List<BookBinary> bookBinaries() {
        return this.bookBinaries;
    }
}
