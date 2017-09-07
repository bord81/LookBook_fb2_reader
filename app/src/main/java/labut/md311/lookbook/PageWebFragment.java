package labut.md311.lookbook;


import android.app.ActionBar;
import android.content.res.Configuration;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.content.ContextCompat;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebChromeClient;
import android.webkit.WebView;
import android.widget.Toast;

import org.arabidopsis.ahocorasick.AhoCorasick;
import org.arabidopsis.ahocorasick.SearchResult;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import labut.md311.lookbook.file_formats.fb2.BookNote;

import static android.widget.Toast.LENGTH_LONG;

//fragment class containing WebView for PagerAdapter
public class PageWebFragment extends Fragment {
    WebView book_view;
    Pair<String> chapter;
    int position;
    int total_pages;
    List<BookNote> bookNotes;
    int tv_pad_left = 0;
    int tv_pad_top = 0;
    int tv_pad_right = 0;
    int tv_pad_bottom = 0;
    private String js_portrait_1 = "        if (differenceHeight > differenceWidth)\n";
    private String js_portrait_2 = "        else if (differenceHeight < differenceWidth)\n";
    private String js_landscape_1 = "        if (differenceHeight < differenceWidth)\n";
    private String js_landscape_2 = "        else if (differenceHeight > differenceWidth)\n";

    static PageWebFragment newInstance(Pair<String> chapter, int position, int total_pages, List<BookNote> bookNotes, int tv_pad_left, int tv_pad_top, int tv_pad_right, int tv_pad_bottom) {
        PageWebFragment fragment = new PageWebFragment();
        fragment.chapter = chapter;
        fragment.position = position;
        fragment.total_pages = total_pages;
        fragment.bookNotes = bookNotes;
        fragment.tv_pad_left = tv_pad_left;
        fragment.tv_pad_top = tv_pad_top;
        fragment.tv_pad_right = tv_pad_right;
        fragment.tv_pad_bottom = tv_pad_bottom;
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.web_fragment, null);
        view.setPadding(tv_pad_left, tv_pad_top, tv_pad_right, tv_pad_bottom);
        book_view = (WebView) view.findViewById(R.id.web_text_view);
        book_view.setBackgroundColor(ContextCompat.getColor(getContext(), R.color.backgr_main_1));
        if (chapter.first_value().equals("txt")) {
            AhoCorasick tree = new AhoCorasick();
            ArrayList<int[]> found_matches = new ArrayList<int[]>();
            int[] note_found = new int[2];
            ArrayList<String> note_ids = new ArrayList<String>();
            boolean ahref_found = false;
            String note_begin = "id=\"";
            String note_end = "\">";
            tree.add(note_begin.getBytes(), note_begin);
            tree.add(note_end.getBytes(), note_end);
            tree.prepare();
            Iterator<SearchResult> searcher = tree.search(chapter.second_value().getBytes());
            while (searcher.hasNext()) {
                SearchResult result = searcher.next();
                Set<String> outputs = result.getOutputs();
                if (outputs.contains(note_begin)) {
                    ahref_found = true;
                    note_found[0] = result.getLastIndex();
                    found_matches.add(note_found);
                } else if (outputs.contains(note_end) && ahref_found) {
                    ahref_found = false;
                    found_matches.get(found_matches.size() - 1)[1] = result.getLastIndex();
                    String next_note = chapter.second_value().substring(found_matches.get(found_matches.size() - 1)[0], found_matches.get(found_matches.size() - 1)[1] - note_end.length());
                    note_ids.add(next_note);
                }
            }
            StringBuilder j_script = new StringBuilder();
            if (note_ids.size() > 0) {
                j_script.append("<script type=\"text/javascript\">\nfunction jscallback()\n{");
                for (int i = 0; i < note_ids.size(); i++) {
                    j_script.append("var dv" + i + " = document.getElementById(\"" + note_ids.get(i) + "\");\ndv" + i + ".onclick = function() {JAVA_CODE.showToast(\"" + note_ids.get(i) + "\")};\n");
                }
                j_script.append("}</script>");
                book_view.getSettings().setJavaScriptEnabled(true);
                book_view.addJavascriptInterface(new JSInterface(), "JAVA_CODE");
                if (total_pages > 0) {
                    book_view.loadData("<html><body onload=\"jscallback();\">" + chapter.second_value() + "<p align='center'>" + (position + 1) + "/" + total_pages + "</p></body>" + j_script.toString(), "text/html", null);
                } else {
                    book_view.loadData("<html><body onload=\"jscallback();\">" + chapter.second_value() + "<p align='center'>" + (position + 1) + "</p></body>" + j_script.toString(), "text/html", null);
                }
                book_view.setWebChromeClient(new WebChromeClient());
            } else {
                if (total_pages > 0) {
                    book_view.loadData(chapter.second_value() + "<p align='center'>" + (position + 1) + "/" + total_pages + "</p></body>", "text/html", null);
                } else {
                    book_view.loadData(chapter.second_value() + "<p align='center'>" + (position + 1) + "</p></body>", "text/html", null);
                }
            }
        } else {
            book_view.getSettings().setJavaScriptEnabled(true);
            book_view.getSettings().setLoadWithOverviewMode(true);
            book_view.getSettings().setUseWideViewPort(true);
            book_view.loadData("<html><center><img src='data:" + chapter.first_value() + ";base64," + chapter.second_value() + "' align='center' bgcolor='0d6aa' onload=\"resize(this);\"/><script type=\"text/javascript\">\n" +
                    "\n" +
                    "    function resize(image)\n" +
                    "    {\n" +
                    "        var differenceHeight = document.body.clientHeight - image.clientHeight;\n" +
                    "        var differenceWidth  = document.body.clientWidth  - image.clientWidth;\n" +
                    "\n" +
                    "        if (differenceHeight > differenceWidth || differenceHeight < 0)\n" +
                    "        {\n" +
                    "            image.style['height'] = document.body.clientHeight + 'px';\n" +
                    "        }\n" +
                    "        else if (differenceHeight < differenceWidth || differenceWidth < 0)\n" +
                    "        {\n" +
                    "            image.style['width'] = document.body.clientWidth + 'px';\n" +
                    "        }\n" +
                    "        image.style['margin'] = 0;\n" +
                    "        document.body.style['margin'] = 0;\n" +
                    "    }\n" +
                    "\n" +
                    "</script></html>", "text/html", null);
            book_view.setWebChromeClient(new WebChromeClient());
        }
        book_view.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View view) {
                ActionBar bar = getActivity().getActionBar();
                if (bar != null) {
                    if (bar.isShowing()) {
                        bar.hide();
                    } else {
                        bar.show();
                    }
                }
                return false;
            }
        });
        return view;
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        String js_1 = "        if (differenceHeight > differenceWidth || differenceHeight < 0)\n";
        String js_2 = "        else if (differenceHeight < differenceWidth || differenceWidth < 0)\n";
        if (newConfig.orientation == Configuration.ORIENTATION_PORTRAIT) {
            js_1 = js_portrait_1;
            js_2 = js_portrait_2;
        } else if (newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE) {
            js_1 = js_landscape_1;
            js_2 = js_landscape_2;
        }
        if (!chapter.first_value().equals("txt")) {
            book_view.getSettings().setJavaScriptEnabled(true);
            book_view.getSettings().setLoadWithOverviewMode(true);
            book_view.getSettings().setUseWideViewPort(true);
            book_view.loadData("<html><center><img src='data:" + chapter.first_value() + ";base64," + chapter.second_value() + "' align='center' bgcolor='0d6aa' onload=\"resize(this);\"/><script type=\"text/javascript\">\n" +
                    "\n" +
                    "    function resize(image)\n" +
                    "    {\n" +
                    "        var differenceHeight = document.body.clientHeight - image.clientHeight;\n" +
                    "        var differenceWidth  = document.body.clientWidth  - image.clientWidth;\n" +
                    "\n" +
                    js_1 +
                    "        {\n" +
                    "            image.style['height'] = document.body.clientHeight + 'px';\n" +
                    "        }\n" +
                    js_2 +
                    "        {\n" +
                    "            image.style['width'] = document.body.clientWidth + 'px';\n" +
                    "        }\n" +
                    "        image.style['margin'] = 0;\n" +
                    "        document.body.style['margin'] = 0;\n" +
                    "    }\n" +
                    "\n" +
                    "</script></html>", "text/html", null);
            book_view.setWebChromeClient(new WebChromeClient());
        }

        super.onConfigurationChanged(newConfig);
    }

    private class JSInterface {
        public void showToast(String key) {
            String note = "";
            String note_id = new String(key);
            boolean found = false;
            for (int i = 0; i < bookNotes.size(); i++) {
                if (bookNotes.get(i).noteId().equals(note_id)) {
                    note = bookNotes.get(i).noteSection();
                    found = true;
                    break;
                }
            }
            if (!found) {
                for (int i = 0; i < bookNotes.size(); i++) {
                    if (bookNotes.get(i).noteId().contains(note_id)) {
                        note = bookNotes.get(i).noteSection();
                        found = true;
                        break;
                    }
                }
            }
            if (!found) {
                for (int i = bookNotes.size() - 1; i > -1; i--) {
                    if (note_id.contains(bookNotes.get(i).noteId())) {
                        note = bookNotes.get(i).noteSection();
                        break;
                    }
                }
            }
            if (!note.isEmpty()) {
                Toast.makeText(getContext(), note, LENGTH_LONG).show();
            }
        }
    }
}
