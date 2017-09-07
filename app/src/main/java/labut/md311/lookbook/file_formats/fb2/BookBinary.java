package labut.md311.lookbook.file_formats.fb2;

//data class for FB2 binary contents (images)
public class BookBinary {

    private final byte[] binaryContent;
    private final String contentType;
    private final String contentId;

    public BookBinary(byte[] binaryContent, String contentType, String contentId) {
        this.binaryContent = binaryContent;
        this.contentType = contentType;
        this.contentId = contentId;
    }

    public byte[] binaryContent() {
        return this.binaryContent;
    }

    public String contentType() {
        return this.contentType;
    }

    public String contentId() {
        return this.contentId;
    }
}
