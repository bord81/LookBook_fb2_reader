package labut.md311.lookbook.file_system;

import android.app.Activity;
import android.app.Fragment;
import android.os.Bundle;
import android.os.Process;
import android.support.annotation.Nullable;
import android.text.Html;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.util.Base64;
import android.util.Log;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlPullParserFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Stack;

import de.greenrobot.event.EventBus;
import labut.md311.lookbook.Pair;
import labut.md311.lookbook.events.ParseEvent;
import labut.md311.lookbook.file_formats.fb2.BookBinary;
import labut.md311.lookbook.file_formats.fb2.BookBody;
import labut.md311.lookbook.file_formats.fb2.BookNote;
import labut.md311.lookbook.file_formats.fb2.Chapter;

//fragment class responsible for file loading, parsing and storing in memory
public class FileFragment extends Fragment {

    private BookBody bookBody;
    private final int TEXT_CHUNK_SIZE = 15000;
    private final int CHAPTER_SIZE = 17000;
    private final int CHAPTER_SIZE_SMALL = 2000;
    private final int INDENT = 4;
    private ArrayList<Pair<String>> lines_for_web;
    private String file_name;
    private String FB2 = "FileFragment";
    private final Map<String, String> subTagsMapStart = new HashMap();
    private final Map<String, String> subTagsMapEnd = new HashMap();
    private final String[] headerNames = {"isbn", "book-name", "book-title"};
    private final String[] bookBodyTags = {"annotation", "epigraph", "body"};
    private final String[] notesAttrs = {"notes"};
    private final String[] notesTags = {"body"};
    private final String[] notesSubAttrTags = {"section"};
    private final String[] notesSubAttrNames = {"id"};
    private final String[] notesTitleTags = {"title"};
    private final String[] binTags = {"binary"};
    private final String[] binContAttrs = {"content-type"};
    private final String[] binIdAttrs = {"id"};

    //tags initialization which will be considered during parsing run
    private void initializeMap() {
        subTagsMapStart.put("title", "<div style=\"text-align:center;\"><h3>");
        subTagsMapStart.put("emphasis", "<em>");
        subTagsMapStart.put("strong", "<strong>");
        subTagsMapStart.put("strikethrough", "<strike>");
        subTagsMapStart.put("sub", "<sub>");
        subTagsMapStart.put("sup", "<sup>");
        subTagsMapStart.put("empty-line", "<br>");
        subTagsMapStart.put("v", "<br>");
        subTagsMapStart.put("a", "<a>");
        subTagsMapStart.put("p", "<br>");

        subTagsMapEnd.put("title", "</h3></div>");
        subTagsMapEnd.put("emphasis", "</em>");
        subTagsMapEnd.put("strong", "</strong>");
        subTagsMapEnd.put("strikethrough", "</strike>");
        subTagsMapEnd.put("sub", "</sub>");
        subTagsMapEnd.put("sup", "</sup>");
        subTagsMapEnd.put("empty-line", "");
        subTagsMapEnd.put("v", "<br>");
        subTagsMapEnd.put("a", "</a>");
        subTagsMapEnd.put("p", "");
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setRetainInstance(true);
        initializeMap();
    }

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        if (file_name != null) {
            if (bookBody == null) {
                new LoadFile(file_name).start();
            } else {
                EventBus.getDefault().post(new ParseEvent());
            }
        }
    }

    public static FileFragment getInstance(String filename) {
        FileFragment fileFragment = new FileFragment();
        fileFragment.file_name = filename;
        return fileFragment;
    }

    public void loadNew(String new_file) {
        new LoadFile(new_file).start();
    }

    @Override
    public void onDetach() {
        super.onDetach();
    }

    synchronized public BookBody bookContents() {
        if (bookBody != null) {
            return bookBody;
        }
        return null;
    }

    //return full text
    synchronized public WeakReference<ArrayList<Pair<String>>> getPreparedBook() {
        if (lines_for_web != null) {
            return new WeakReference<ArrayList<Pair<String>>>(lines_for_web);
        } else {
            return null;
        }
    }

    //store full text
    synchronized public void savePreparedBook(ArrayList<Pair<String>> lines_for_web) {
        this.lines_for_web = new ArrayList<Pair<String>>();
        for (Pair<String> pair : lines_for_web
                ) {
            this.lines_for_web.add(new Pair<String>(pair.first_value(), pair.second_value()));
        }
    }

    //thread to load file and parse it in background
    private class LoadFile extends Thread {
        String file;

        LoadFile(String file) {
            super();
            this.file = file;
        }

        @Override
        public void run() {
            Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND);
            File to_open = new File(file);
            try {
                if (to_open.exists()) {
                    InputStream stream;
                    stream = new FileInputStream(to_open);
                    BookBody p_result = parseFb2(stream);
                    synchronized (this) {
                        bookBody = p_result;
                    }
                    EventBus.getDefault().post(new ParseEvent());
                    stream.close();
                }
            } catch (IOException e) {
                Log.e(FB2, "I/O Exception during parsing", e);
            }
            Thread.currentThread().interrupt();
        }
    }

    //method to parse FB2 using XmlPullParser
    private BookBody parseFb2(InputStream inputStream) {
        Spanned body;
        Map<String, String> headers = new HashMap<String, String>();
        String noteId = "";
        String binContent = "";
        String binContentType = "";
        String binContentId = "";
        ArrayList<BookNote> bookNotes = new ArrayList<BookNote>();
        ArrayList<Chapter> arrayList = new ArrayList<Chapter>();
        ArrayList<BookBinary> bookBinaries = new ArrayList<BookBinary>();
        XmlPullParserFactory parserFactory = null;
        boolean bookIsOpen = false;
        boolean headerFound = false;
        boolean notesFound = false;
        boolean noteSectionFound = false;
        boolean binFound = false;
        int headerNumber = -1;
        StringBuilder builder = new StringBuilder();
        StringBuilder builder_notes = new StringBuilder();
        Stack<String> tagStack = new Stack<String>();
        Stack<String> bodyTagStack = new Stack<String>();

        try {
            parserFactory = XmlPullParserFactory.newInstance();
            parserFactory.setNamespaceAware(false);
            XmlPullParser parser = parserFactory.newPullParser();
            parser.setInput(inputStream, null);
            int eventType = parser.getEventType();
            headers.put("file-name", file_name);
            while (true) {
                //check if end of document is reached
                if (eventType == XmlPullParser.END_DOCUMENT) {
                    break;
                }
                //check headers
                for (int i = 0; i < headerNames.length; i++) {
                    if (eventType == XmlPullParser.START_TAG && parser.getName().equals(headerNames[i])) {
                        eventType = parser.next();
                        headerFound = true;
                        headerNumber = i;
                        break;
                    }
                }

                for (int i = 0; i < headerNames.length; i++) {
                    if (eventType == XmlPullParser.END_TAG && parser.getName().equals(headerNames[i])) {
                        eventType = parser.next();
                        headerFound = false;
                        headerNumber = -1;
                        break;
                    }
                }
                //check the 'book's main contents' tags
                for (int i = 0; i < bookBodyTags.length; i++) {
                    //ignore all start tags with attributes for main text tags, e.g. body
                    if (eventType == XmlPullParser.START_TAG && parser.getAttributeCount() == 0 && parser.getName().equals(bookBodyTags[i])) {
                        bookIsOpen = true;
                        bodyTagStack.push(bookBodyTags[i]);
                        eventType = parser.next();
                        break;
                    }
                }

                for (int i = 0; i < bookBodyTags.length; i++) {
                    if (!notesFound && eventType == XmlPullParser.END_TAG && parser.getName().equals(bookBodyTags[i])) {
                        if (!bodyTagStack.empty() && bodyTagStack.peek().equals(parser.getName()))
                            bodyTagStack.pop();
                        if (bodyTagStack.empty())
                            bookIsOpen = false;
                        eventType = parser.next();
                        break;
                    }
                }
                //check notes
                for (int i = 0; i < notesTags.length; i++) {
                    boolean stop = false;
                    //take start tags with existing attributes
                    if (eventType == XmlPullParser.START_TAG && parser.getAttributeCount() > 0 && parser.getName().equals(notesTags[i])) {
                        int attrCount = parser.getAttributeCount();
                        for (int j = 0; j < attrCount; j++) {
                            if (stop) {
                                break;
                            }
                            for (int k = 0; k < notesAttrs.length; k++) {
                                if (parser.getAttributeValue(j).equals(notesAttrs[k])) {
                                    notesFound = true;
                                    eventType = parser.next();
                                    stop = true;
                                    break;
                                }
                            }
                        }
                        if (stop) {
                            break;
                        }
                    }
                }

                for (int i = 0; i < notesTags.length; i++) {
                    if (notesFound && eventType == XmlPullParser.END_TAG && parser.getName().equals(notesTags[i])) {
                        notesFound = false;
                        eventType = parser.next();
                        break;
                    }
                }
                // extract note id from section tag
                for (int i = 0; i < notesSubAttrTags.length; i++) {
                    boolean stop = false;
                    if (notesFound && eventType == XmlPullParser.START_TAG && parser.getName().equals(notesSubAttrTags[i])) {
                        noteSectionFound = true;
                        int attrCount = parser.getAttributeCount();
                        for (int j = 0; j < attrCount; j++) {
                            if (stop) {
                                break;
                            }
                            for (int k = 0; k < notesSubAttrNames.length; k++) {
                                if (parser.getAttributeName(j).equals(notesSubAttrNames[k])) {
                                    noteId = parser.getAttributeValue(j);
                                    eventType = parser.next();
                                    stop = true;
                                    break;
                                }
                            }
                        }
                        if (stop) {
                            break;
                        }
                    }
                }
                // add a book note
                for (int i = 0; i < notesSubAttrTags.length; i++) {
                    if (noteSectionFound && eventType == XmlPullParser.END_TAG && parser.getName().equals(notesSubAttrTags[i])) {
                        noteSectionFound = false;
                        bookNotes.add(new BookNote(noteId, builder_notes.toString()));
                        noteId = "";
                        builder_notes = new StringBuilder();
                        break;
                    }
                }
                //check binaries
                for (int i = 0; i < binTags.length; i++) {
                    if (eventType == XmlPullParser.START_TAG && parser.getName().equals(binTags[i])) {
                        boolean contTypeFound = false;
                        boolean contIdFound = false;
                        binFound = true;
                        int attrCount = parser.getAttributeCount();
                        if (attrCount > 0) {
                            for (int j = 0; j < attrCount; j++) {
                                for (int k = 0; k < binContAttrs.length; k++) {
                                    if (parser.getAttributeName(j).equals(binContAttrs[k]) && !contTypeFound) {
                                        binContentType = parser.getAttributeValue(j);
                                        contTypeFound = true;
                                        break;
                                    }
                                }
                                for (int k = 0; k < binIdAttrs.length; k++) {
                                    if (parser.getAttributeName(j).equals(binIdAttrs[k]) && !contIdFound) {
                                        binContentId = parser.getAttributeValue(j);
                                        contIdFound = true;
                                        break;
                                    }
                                }
                            }
                        }
                        eventType = parser.next();
                        break;
                    }
                }

                for (int i = 0; i < binTags.length; i++) {
                    if (eventType == XmlPullParser.END_TAG && parser.getName().equals(binTags[i])) {
                        byte[] bytes = Base64.decode(binContent, Base64.DEFAULT);
                        bookBinaries.add(new BookBinary(bytes, binContentType, binContentId));
                        binContent = "";
                        binContentId = "";
                        binContentType = "";
                        binFound = false;
                        break;
                    }
                }
                //check text
                if (eventType == XmlPullParser.TEXT && headerFound && headerNumber > -1) {
                    headers.put(headerNames[headerNumber], parser.getText());
                }

                if (eventType == XmlPullParser.TEXT && binFound) {
                    binContent = parser.getText();
                }

                if (noteSectionFound && eventType == XmlPullParser.TEXT) {
                    builder_notes.append(parser.getText() + " ");
                }

                //parse main contents text
                if (eventType == XmlPullParser.TEXT && bookIsOpen) {
                    String currentPiece = parser.getText();
                    if (currentPiece.length() > TEXT_CHUNK_SIZE) {
                        if (tagStack.empty() || !tagStack.peek().equals("a")) {
                            if (builder.length() < CHAPTER_SIZE_SMALL) {
                                builder.append(currentPiece);
                                if (!tagStack.empty()) {
                                    for (int i = 0; i < tagStack.size(); i++) {
                                        builder.append(subTagsMapEnd.get(tagStack.pop()));
                                    }
                                }
                                arrayList.add(new Chapter(new SpannableStringBuilder(Html.fromHtml(builder.toString()))));
                                builder = new StringBuilder();
                            } else {
                                if (!tagStack.empty()) {
                                    Stack<String> tagStackCopy = new Stack<String>();
                                    for (int i = 0; i < tagStack.size(); i++) {
                                        String x = tagStack.pop();
                                        tagStackCopy.push(x);
                                        builder.append(subTagsMapEnd.get(x));
                                    }
                                    arrayList.add(new Chapter(new SpannableStringBuilder(Html.fromHtml(builder.toString()))));
                                    builder = new StringBuilder();
                                    for (int i = 0; i < tagStackCopy.size(); i++) {
                                        String y = tagStackCopy.pop();
                                        tagStack.push(y);
                                        builder.append(subTagsMapStart.get(y));
                                    }
                                    builder.append(currentPiece);
                                    for (int i = 0; i < tagStack.size(); i++) {
                                        builder.append(subTagsMapEnd.get(tagStack.pop()));
                                    }
                                    arrayList.add(new Chapter(new SpannableStringBuilder(Html.fromHtml(builder.toString()))));
                                    builder = new StringBuilder();
                                } else {
                                    arrayList.add(new Chapter(new SpannableStringBuilder(Html.fromHtml(builder.toString()))));
                                    builder = new StringBuilder();
                                    builder.append(currentPiece);
                                    arrayList.add(new Chapter(new SpannableStringBuilder(Html.fromHtml(builder.toString()))));
                                    builder = new StringBuilder();
                                }
                            }
                        }
                    } else if (builder.length() > CHAPTER_SIZE) {
                        if (tagStack.empty() || !tagStack.peek().equals("a")) {
                            if (!tagStack.empty()) {
                                Stack<String> tagStackCopy = new Stack<String>();
                                for (int i = 0; i < tagStack.size(); i++) {
                                    String x = tagStack.pop();
                                    tagStackCopy.push(x);
                                    builder.append(subTagsMapEnd.get(x));
                                }
                                arrayList.add(new Chapter(new SpannableStringBuilder(Html.fromHtml(builder.toString()))));
                                builder = new StringBuilder();
                                for (int i = 0; i < tagStackCopy.size(); i++) {
                                    String y = tagStackCopy.pop();
                                    tagStack.push(y);
                                    builder.append(subTagsMapStart.get(y));
                                }
                                builder.append(currentPiece);
                            } else {
                                arrayList.add(new Chapter(new SpannableStringBuilder(Html.fromHtml(builder.toString()))));
                                builder = new StringBuilder();
                                builder.append(currentPiece);
                            }
                        }
                    } else {
                        builder.append(currentPiece);
                    }
                } else if (bookIsOpen && eventType == XmlPullParser.START_TAG) {
                    String c_sub_tag = parser.getName();
                    if (c_sub_tag.equals("a")) {
                        String note_id = parser.getAttributeValue(null, "l:href");
                        builder.append("<a href='" + note_id + "'>");
                        tagStack.push("a");
                    }
                    if (c_sub_tag.equals("image")) {
                        builder.append("<img src='" + parser.getAttributeValue(null, "l:href") + "'/>");
                    }
                    for (Map.Entry<String, String> entry : subTagsMapStart.entrySet()
                            ) {
                        if (entry.getKey().equals(c_sub_tag) && !c_sub_tag.equals("a")) {
                            builder.append(entry.getValue());
                            if (c_sub_tag.equals("p")) {
                                if (tagStack.empty() || !tagStack.peek().equals("title")) {
                                    for (int i = 0; i < INDENT; i++) {
                                        builder.append('\u0009');
                                    }
                                }
                            }
                            tagStack.push(entry.getKey());
                        }
                    }
                } else if (bookIsOpen && eventType == XmlPullParser.END_TAG) {
                    String c_sub_tag = parser.getName();
                    for (Map.Entry<String, String> entry : subTagsMapEnd.entrySet()
                            ) {
                        if (entry.getKey().equals(c_sub_tag)) {
                            builder.append(entry.getValue());
                            if (!tagStack.empty() && tagStack.peek().equals(entry.getKey())) {
                                tagStack.pop();
                            }
                        }
                    }
                } else if (!bookIsOpen && eventType == XmlPullParser.START_TAG) {
                    if (parser.getName().equals("image")) {
                        if (arrayList.isEmpty()) {
                            builder.insert(0, "<img src='" + parser.getAttributeValue(null, "l:href") + "'/>");
                        } else {
                            builder.append("<img src='" + parser.getAttributeValue(null, "l:href") + "'/>");
                        }
                    }
                }
                eventType = parser.next();
            }
        } catch (XmlPullParserException e) {
            Log.d(FB2, "XmlPullParserException", e);
            body = Html.fromHtml("Error reading file");
        } catch (IOException e) {
            Log.d(FB2, "I/O XmlPullParserException", e);
            body = Html.fromHtml("Error reading file");
        }
        arrayList.add(new Chapter(new SpannableStringBuilder(Html.fromHtml(builder.toString()))));
        BookBody bookBody = new BookBody(headers, arrayList, bookNotes, bookBinaries);
        return bookBody;
    }
}
