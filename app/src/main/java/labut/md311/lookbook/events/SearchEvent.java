package labut.md311.lookbook.events;

//helper class to notify calling activity about book search event
public class SearchEvent {
    private String book_name;

    public SearchEvent(String book_name) {
        this.book_name = book_name;
    }

    public String bookName() {
        return this.book_name;
    }
}
