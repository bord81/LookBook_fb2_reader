package labut.md311.lookbook.book_search;

import android.os.Process;
import android.util.Log;


import org.arabidopsis.ahocorasick.AhoCorasick;
import org.arabidopsis.ahocorasick.SearchResult;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Iterator;
import java.util.Set;

import de.greenrobot.event.EventBus;
import labut.md311.lookbook.events.SearchEvent;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

//class for handling HTTP request for ISBN of the scanned book and returning its result to calling activity
public class BookSearch {
    private String isbn;
    private boolean search_or_goodreads = true;
    private final String baseUrl = "https://www.googleapis.com/customsearch/v1?";
    //google dev api key to be inserted
    private final String devKey = "key=";
    //google custom search engine id to be inserted
    private final String searchEng = "&cx=";
    private final String queryPref = "&q=";


    public void findBook(String isbn, boolean search_or_goodreads) {
        this.isbn = isbn;
        this.search_or_goodreads = search_or_goodreads;
        new LoadBookName(baseUrl + devKey + searchEng + queryPref + isbn).start();
    }

    private class LoadBookName extends Thread {
        String toLoad;

        public LoadBookName(String toLoad) {
            this.toLoad = toLoad;
        }

        @Override
        public void run() {
            Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND);
            try {
                OkHttpClient client = new OkHttpClient();
                Request request = new Request.Builder().url(toLoad).build();
                Response response = client.newCall(request).execute();
                if (response.isSuccessful()) {
                    InputStream stream = response.body().byteStream();
                    ByteArrayOutputStream result = new ByteArrayOutputStream();
                    byte[] buffer = new byte[10240];
                    int length;
                    while ((length = stream.read(buffer)) != -1) {
                        result.write(buffer, 0, length);
                    }
                    byte[] jsonresponse = result.toByteArray();
                    result.close();
                    stream.close();
                    AhoCorasick tree = new AhoCorasick();
                    boolean title_found = false;
                    int[] title_offset = new int[2];
                    String og_title = "\"og:title\": \"";
                    String title_end = "\",";
                    tree.add(og_title.getBytes(), og_title);
                    tree.add(title_end.getBytes(), title_end);
                    tree.prepare();
                    Iterator<SearchResult> searcher = tree.search(jsonresponse);
                    while (searcher.hasNext()) {
                        SearchResult sresult = searcher.next();
                        Set<String> outputs = sresult.getOutputs();
                        if (outputs.contains(og_title)) {
                            title_found = true;
                            title_offset[0] = sresult.getLastIndex();
                        } else if (outputs.contains(title_end) && title_found) {
                            title_offset[1] = sresult.getLastIndex() - 2;
                            break;
                        }
                    }
                    if (title_found) {
                        byte[] name = new byte[title_offset[1] - title_offset[0]];
                        for (int i = 0; i < name.length; i++) {
                            name[i] = jsonresponse[title_offset[0] + i];
                        }
                        String book_name = new String(name);
                        EventBus.getDefault().post(new SearchEvent(book_name, search_or_goodreads));
                    } else {
                        EventBus.getDefault().post(new SearchEvent(new String(), search_or_goodreads));
                    }
                }
            } catch (IOException e) {
                Log.e("BookSearch error", e.getMessage());
            }
        }
    }
}
