package labut.md311.lookbook.events;

//helper class to notify calling activity about book search event
public class SearchEvent {
    private String book_name;
    private boolean search_or_goodreads;

    public SearchEvent(String book_name, boolean search_or_goodreads) {
        this.search_or_goodreads = search_or_goodreads;
        this.book_name = book_name;
    }

    public String bookName() {
        return this.book_name;
    }
    public boolean searchOrGoodreads() { return this.search_or_goodreads; }
}
