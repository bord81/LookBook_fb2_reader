package labut.md311.lookbook;

import android.app.ActionBar;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.Process;
import android.os.SystemClock;
import android.preference.PreferenceManager;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentStatePagerAdapter;
import android.support.v4.content.ContextCompat;
import android.support.v4.view.PagerAdapter;
import android.support.v4.view.ViewPager;
import android.text.Html;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.style.AlignmentSpan;
import android.text.style.ImageSpan;
import android.util.Base64;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;

import org.arabidopsis.ahocorasick.AhoCorasick;
import org.arabidopsis.ahocorasick.SearchResult;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import de.greenrobot.event.EventBus;
import labut.md311.lookbook.book_search.BookSearch;
import labut.md311.lookbook.events.SearchEvent;
import labut.md311.lookbook.file_formats.fb2.BookBinary;
import labut.md311.lookbook.file_formats.fb2.BookBody;
import labut.md311.lookbook.file_system.FileFragment;
import labut.md311.lookbook.events.ParseEvent;
import labut.md311.lookbook.file_system.FileSelectActivity;
import labut.md311.lookbook.options_menu.InfoDialog;
import labut.md311.lookbook.zxing.IntentIntegrator;
import labut.md311.lookbook.zxing.IntentResult;

//main activity class
public class BookDisplayAct extends FragmentActivity {
    private volatile boolean book_full_load = false;
    private volatile boolean book_first_load = false;
    private volatile int current_chap = 0;
    private volatile int chap_count = 0;
    private int tv_pad_left = 40;
    private int tv_pad_top = 70;
    private int tv_pad_right = 40;
    private int tv_pad_bottom = 40;
    private ActionBar actionBar;
    private TextView textView;
    public FileFragment fileFragment = null;
    private static final String FB2_FILE = "fb2_file";
    private static final long TV_DELAY = 2000;
    private static int PAGE_LENGTH;
    private Handler handler;
    private int targetWidth;
    private int FB2_PARSE_DONE = 400;
    private int JUSTIFY_DONE = 500;
    private int FULL_BOOK_PARSE = 700;
    private int FULL_BOOK_JUSTIFY = 800;
    private volatile int current_orient;
    private ViewPager pager;
    private PagerAdapter pagerAdapter;
    private volatile int tv_height;
    private volatile int tv_width;
    private volatile int tv_line_height;
    private volatile float tv_line_se;
    private volatile float tv_line_sm;
    ArrayList<Pair<String>> lines_for_web = new ArrayList<Pair<String>>();
    private SharedPreferences sharedPreferences;
    private String book_id;
    private String book_file;
    private String prev_book = "PREV_BOOK_SAVED";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        Bundle bundle_in = getIntent().getExtras();
        if (bundle_in == null || !bundle_in.containsKey("fileName")) {
            if (!sharedPreferences.getBoolean(prev_book, false))
                startActivity(new Intent(getApplicationContext(), FileSelectActivity.class));
        }
        onCreateHelper();
    }

    //separate method to offload the lifecycle method and make refactoring easier
    private void onCreateHelper() {
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        setContentView(R.layout.main);
        pager = (ViewPager) findViewById(R.id.pager);
        book_first_load = true;
        findViewById(R.id.main_view).setBackgroundColor(ContextCompat.getColor(getApplicationContext(), R.color.backgr_main_1));
        current_orient = getResources().getConfiguration().orientation;
        handler = new Handler() {
            @Override
            public void handleMessage(Message msg) {
                if (msg.what == JUSTIFY_DONE) {
                    setupPager();
                } else if (msg.what == FB2_PARSE_DONE) {
                    textView.setText((SpannableStringBuilder) msg.obj);
                    tv_height = textView.getMeasuredHeight();
                    tv_width = textView.getMeasuredWidth();
                    tv_pad_right = tv_pad_left = tv_width / 30;
                    tv_pad_top = tv_pad_bottom = tv_height / 30;
                    textView.setPadding(tv_pad_left, tv_pad_top, tv_pad_right, tv_pad_bottom);
                    if (current_orient == Configuration.ORIENTATION_LANDSCAPE && tv_height > tv_width) {
                        textView.setMaxHeight(tv_width);
                        textView.setMaxWidth(tv_height);
                        int temp = tv_width;
                        tv_width = tv_height;
                        tv_height = temp;
                    } else if (current_orient == Configuration.ORIENTATION_PORTRAIT && tv_height < tv_width) {
                        textView.setMaxHeight(tv_width);
                        textView.setMaxWidth(tv_height);
                        int temp = tv_width;
                        tv_width = tv_height;
                        tv_height = temp;
                    }
                    tv_line_height = textView.getLineHeight();
                    tv_line_se = textView.getLineSpacingExtra();
                    tv_line_sm = textView.getLineSpacingMultiplier();
                    PAGE_LENGTH = (tv_height - tv_pad_bottom - tv_pad_top) / (tv_line_height * (int) tv_line_sm + (int) tv_line_se) - 3;
                    new Justify((SpannableStringBuilder) msg.obj, fileFragment.bookContents().bookBinaries()).start();
                } else if (msg.what == FULL_BOOK_PARSE) {
                    new Justify((SpannableStringBuilder) msg.obj, fileFragment.bookContents().bookBinaries()).start();
                } else if (msg.what == FULL_BOOK_JUSTIFY) {
                    lines_for_web = (ArrayList<Pair<String>>) msg.obj;
                    fileFragment.savePreparedBook((ArrayList<Pair<String>>) msg.obj);
                    pagerAdapter.notifyDataSetChanged();
                    if (book_first_load) {
                        int saved_page = sharedPreferences.getInt(book_id, -1);
                        if (saved_page > 0) {
                            pager.setCurrentItem(saved_page);
                        }
                        book_first_load = false;
                    }
                }
            }
        };
        textView = (TextView) findViewById(R.id.textViewMain);
        actionBar = getActionBar();
        if (actionBar != null)
            actionBar.hide();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.options, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.about:
                InfoDialog infoDialog = new InfoDialog();
                infoDialog.show(getFragmentManager(), "about");
                return (true);
            case R.id.scan:
                scanBook();
                return (true);
            case R.id.open_file:
                startActivity(new Intent(getApplicationContext(), FileSelectActivity.class));
                return (true);
        }
        return super.onOptionsItemSelected(item);
    }


    @Override
    protected void onResume() {
        super.onResume();
        EventBus.getDefault().register(this);
        Bundle bundle_in = getIntent().getExtras();
        fileFragment = (FileFragment) getFragmentManager().findFragmentByTag(FB2_FILE);
        if (bundle_in != null && bundle_in.containsKey("fileName")) {
            String fileName = bundle_in.getString("fileName");
            getIntent().getExtras().clear();
            if (fileFragment == null) {
                fileFragment = FileFragment.getInstance(fileName);
                getFragmentManager().beginTransaction().add(fileFragment, FB2_FILE).commit();
            } else {
                fileFragment.loadNew(fileName);
            }
        } else {
            fileFragment = (FileFragment) getFragmentManager().findFragmentByTag(FB2_FILE);
            if (fileFragment == null) {
                book_full_load = false;
                fileFragment = FileFragment.getInstance(sharedPreferences.getString("book_file", book_file));
                getFragmentManager().beginTransaction().add(fileFragment, FB2_FILE).commit();
            } else if (fileFragment != null) {
                if (fileFragment.getPreparedBook() != null) {
                    lines_for_web = fileFragment.getPreparedBook().get();
                    book_full_load = true;
                    setupPager();
                }
            }
        }
    }

    @Override
    protected void onPause() {
        EventBus.getDefault().unregister(this);
        super.onPause();
    }

    //Eventbus method for handling parse events
    public void onEventMainThread(ParseEvent event) {
        if (fileFragment != null) {
            BookBody bookBody = fileFragment.bookContents();
            SharedPreferences.Editor editor = sharedPreferences.edit();
            editor.putBoolean(prev_book, true);
            editor.putString("book_file", bookBody.bookHeaders().get("file-name"));
            editor.apply();
            book_id = new String();
            if (bookBody.bookHeaders().size() == 1) {
                book_id = bookBody.bookHeaders().get("file-name");
            } else {
                for (String s : bookBody.bookHeaders().keySet()
                        ) {
                    if (!s.equals("file-name"))
                        book_id += bookBody.bookHeaders().get(s);
                }
            }
            chap_count = bookBody.bookChapters().size();
            SpannableStringBuilder spannableString = bookBody.bookChapters().get(current_chap).chapter().get();
            Message message = new Message();
            message.what = FB2_PARSE_DONE;
            message.obj = spannableString;
            handler.sendMessage(message);
        }
    }

    //Eventbus method for handling book search events
    public void onEventMainThread(SearchEvent event) {
        Uri uri = Uri.parse("https://www.google.com/search?q=" + event.bookName());
        Intent intent = new Intent(Intent.ACTION_VIEW, uri);
        startActivity(intent);
    }

    private void setupPager() {
        if (pagerAdapter == null) {
            pagerAdapter = new WebFragmentPagerAdapter(getSupportFragmentManager());
        }
        pager.setAdapter(pagerAdapter);
        int saved_page = sharedPreferences.getInt(book_id, -1);
        if (saved_page > 0 && book_full_load) {
            pager.setCurrentItem(saved_page);
        }
        pager.addOnPageChangeListener(new ViewPager.OnPageChangeListener() {

            @Override
            public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {
            }

            @Override
            public void onPageSelected(int position) {
                SharedPreferences.Editor editor = sharedPreferences.edit();
                if (!sharedPreferences.contains(book_id)) {
                    editor.putInt(book_id, position);
                } else {
                    editor.remove(book_id);
                    editor.putInt(book_id, position);
                }
                editor.apply();
            }

            @Override
            public void onPageScrollStateChanged(int state) {

            }
        });
        findViewById(R.id.progressBar1).setVisibility(View.GONE);
        pager.setVisibility(View.VISIBLE);
        if (!book_full_load) {
            BookBody bookBody = fileFragment.bookContents();
            SpannableStringBuilder spannableString = new SpannableStringBuilder();
            for (int i = 0; i < chap_count; i++) {
                SpannableStringBuilder spannableString_source = bookBody.bookChapters().get(i).chapter().get();
                int offset = spannableString.length();
                spannableString.append(spannableString_source.subSequence(0, spannableString_source.length()));
                Object[] spans = spannableString_source.getSpans(0, spannableString_source.length(), Object.class);
                int sp_start, sp_end, flags;
                for (int j = 0; j < spans.length; j++) {
                    sp_start = spannableString_source.getSpanStart(spans[j]);
                    int calc_sp_start = sp_start + offset;
                    sp_end = spannableString_source.getSpanEnd(spans[j]);
                    int calc_sp_end = sp_end + offset;
                    if (calc_sp_end > spannableString.length() - 1)
                        calc_sp_end = spannableString.length() - 1;
                    flags = spannableString_source.getSpanFlags(spans[j]);
                    if ((spannableString.getSpanStart(spans[j]) == calc_sp_start && spannableString.getSpanEnd(spans[j]) == calc_sp_end) || (spannableString.getSpanStart(spans[j]) == calc_sp_start && spannableString.getSpanEnd(spans[j]) == calc_sp_end + 1))
                        continue;
                    while (spans[j].getClass().equals(AlignmentSpan.Standard.class)) {
                        if ((int) spannableString.subSequence(calc_sp_start, calc_sp_start + 1).charAt(0) == 10)
                            calc_sp_start++;
                        if ((int) spannableString.subSequence(calc_sp_start, calc_sp_start + 1).charAt(0) != 10)
                            break;
                    }
                    spannableString.setSpan(spans[j], calc_sp_start, calc_sp_end, flags);
                }
            }
            book_full_load = true;
            Message message = new Message();
            message.what = FULL_BOOK_PARSE;
            message.obj = spannableString;
            handler.sendMessage(message);
        }
    }

    //main utility method to adjust text properties
    private void prepareText(Spannable spannable, List<BookBinary> bookBinaries) {
        int JUST_PAGE_LENGTH = PAGE_LENGTH * 8 / 10;
        long l = SystemClock.currentThreadTimeMillis();
        while (true) {
            if (textView.getMeasuredWidth() > 0) {
                targetWidth = tv_width - textView.getCompoundPaddingLeft() - textView.getCompoundPaddingRight();
                break;
            } else if ((SystemClock.currentThreadTimeMillis() - l) > TV_DELAY) {
                break;
            }
        }

        ArrayList<Pair<String>> lines_for_web = new ArrayList<Pair<String>>();
        SpannableStringBuilder spannableString = new SpannableStringBuilder();
        int start_offset = 0;
        int end_offset = 0;
        int end_seq = 0;
        boolean stop_cycle = false;
        int max_length = 0;
        if (current_orient == Configuration.ORIENTATION_PORTRAIT) {
            JUST_PAGE_LENGTH = PAGE_LENGTH;
        } else if (current_orient == Configuration.ORIENTATION_LANDSCAPE) {
            JUST_PAGE_LENGTH = PAGE_LENGTH * 2;
        }
        max_length = JUST_PAGE_LENGTH * JUST_PAGE_LENGTH;

        while (true) {
            String image = new String();
            String image_type = new String();
            spannableString = new SpannableStringBuilder();
            int first_real_char = -1;
            boolean verse = false;
            int verse_value = 0;
            int short_lines = 0;
            int line_length = 0;

            end_seq = max_length + start_offset;
            if (end_seq > spannable.length()) {
                end_seq = spannable.length();
                stop_cycle = true;
            }
            CharSequence charSequence = spannable.subSequence(start_offset, end_seq);
            int breaks_count = 0;
            for (int i = 0; i < charSequence.length(); i++) {
                if (first_real_char < 0 && charSequence.charAt(i) != 10 && charSequence.charAt(i) != 9 && charSequence.charAt(i) != 160 && charSequence.charAt(i) != 65532)
                    first_real_char = i;
                if (charSequence.charAt(i) == 10)
                    breaks_count++;
            }
            if (first_real_char < 0) {
                start_offset = end_seq;
                continue;
            }
            int refine_end = spannable.length();
            if (breaks_count > 0) {
                int line_count = 0;
                line_length = 0;
                int[] char_line = new int[JUST_PAGE_LENGTH * 2];
                for (int i = 0; i < charSequence.length(); i++) {
                    if (charSequence.charAt(i) != 10 && charSequence.charAt(i) != 160 && charSequence.charAt(i) != 65532)
                        line_length++;
                    if (charSequence.charAt(i) == 10) {
                        if (line_count > char_line.length * 3 / 4)
                            char_line = Arrays.copyOf(char_line, char_line.length * 2);
                        char_line[line_count] = line_length;
                        line_count++;
                        line_length = 0;
                    }
                }
                line_length = 0;
                short_lines = 0;
                for (int i = 0; i < line_count; i++) {
                    if (char_line[i] <= JUST_PAGE_LENGTH && char_line[i] > 0) {
                        short_lines++;
                        line_length += char_line[i];
                    }
                }
                if (short_lines > 0) {
                    verse = true;
                    line_length = (line_length / short_lines);
                    if ((double) line_length / JUST_PAGE_LENGTH < 0.6) {
                        end_offset = max_length + start_offset - (JUST_PAGE_LENGTH - line_length) * breaks_count + (JUST_PAGE_LENGTH - line_length) * short_lines;
                        verse_value = end_offset;
                    } else {
                        end_offset = max_length + start_offset - (JUST_PAGE_LENGTH - line_length) * breaks_count - line_length;
                        verse_value = end_offset;
                    }

                } else {
                    end_offset = max_length - breaks_count * JUST_PAGE_LENGTH + start_offset;
                }
            } else {
                end_offset = max_length + start_offset;
            }
            if (stop_cycle) {
                end_offset = refine_end;
            } else if (!verse) {
                for (int i = end_offset; i < refine_end; i++) {
                    if (spannable.charAt(i) == ';' || spannable.charAt(i) == ':' || spannable.charAt(i) == '.' || spannable.charAt(i) == '?' || spannable.charAt(i) == '!') {
                        end_offset = i + 1;
                        break;
                    } else if (spannable.charAt(i) == '(' || spannable.charAt(i) == '"' || spannable.charAt(i) == '\'') {
                        end_offset = i;
                        break;
                    }
                }
                if (end_offset > max_length - breaks_count * JUST_PAGE_LENGTH + start_offset) {

                    for (int i = max_length - breaks_count * JUST_PAGE_LENGTH + start_offset; i < end_offset; i++) {
                        if (spannable.charAt(i) == ',' || spannable.charAt(i) == '-' || spannable.charAt(i) == ';' || spannable.charAt(i) == ':') {
                            end_offset = i + 1;
                            break;
                        }
                    }
                }
            } else if (verse) {
                for (int i = end_offset; i < refine_end; i++) {
                    if (spannable.charAt(i) == ';' || spannable.charAt(i) == ':' || spannable.charAt(i) == '.' || spannable.charAt(i) == '?' || spannable.charAt(i) == '!') {
                        end_offset = i + 1;
                        break;
                    } else if (spannable.charAt(i) == '(' || spannable.charAt(i) == '"' || spannable.charAt(i) == '\'') {
                        end_offset = i;
                        break;
                    }
                }
                if (end_offset > verse_value - JUST_PAGE_LENGTH * breaks_count / 4 && breaks_count < JUST_PAGE_LENGTH / 3) {
                    for (int i = verse_value - JUST_PAGE_LENGTH * breaks_count / 4; i < end_offset; i++) {
                        if (spannable.charAt(i) == ',' || spannable.charAt(i) == '-' || spannable.charAt(i) == ';' || spannable.charAt(i) == ':') {
                            end_offset = i + 1;
                            break;
                        }
                    }
                } else if (breaks_count > JUST_PAGE_LENGTH / 3) {
                    for (int i = verse_value - JUST_PAGE_LENGTH * line_length / 4; i < end_offset; i++) {
                        if (spannable.charAt(i) == ',' || spannable.charAt(i) == '-' || spannable.charAt(i) == ';' || spannable.charAt(i) == ':') {
                            end_offset = i + 1;
                            break;
                        }
                    }
                }
            }
            ImageSpan[] imageSpans = spannable.getSpans(start_offset, end_offset - 1, ImageSpan.class);
            int min_span_ind = 0;
            int min_span_val = Integer.MAX_VALUE;
            for (int i = 0; i < imageSpans.length; i++) {
                if (min_span_val > spannable.getSpanStart(imageSpans[i])) {
                    min_span_val = spannable.getSpanStart(imageSpans[i]);
                    min_span_ind = i;
                }
            }
            if (imageSpans.length > 0) {
                if (start_offset == spannable.getSpanStart(imageSpans[min_span_ind]) || spannable.getSpanStart(imageSpans[min_span_ind]) < first_real_char) {
                    String img_index = imageSpans[min_span_ind].getSource();
                    if (img_index.contains("#")) {
                        img_index = img_index.substring(1, img_index.length());
                    }
                    for (BookBinary binary : bookBinaries
                            ) {
                        if (binary.contentId().equals(img_index)) {
                            image = Base64.encodeToString(binary.binaryContent(), Base64.DEFAULT);
                            image_type = binary.contentType();
                            break;
                        }
                    }
                    lines_for_web.add(new Pair<String>(image_type, image));
                    start_offset = spannable.getSpanEnd(imageSpans[min_span_ind]);
                    continue;
                } else {
                    end_offset = spannable.getSpanStart(imageSpans[min_span_ind]);
                }
            }
            charSequence = spannable.subSequence(start_offset, end_offset);
            Object[] spans = spannable.getSpans(start_offset, end_offset - 1, Object.class);
            spannableString.append(charSequence);
            int sp_start, sp_end, flags;
            for (int i = 0; i < spans.length; i++) {
                sp_start = spannable.getSpanStart(spans[i]);
                sp_end = spannable.getSpanEnd(spans[i]);
                flags = spannable.getSpanFlags(spans[i]);
                int calc_sp_start = sp_start - start_offset;
                int calc_sp_end = sp_end - start_offset;
                if (calc_sp_start < 0)
                    calc_sp_start = 0;
                if (calc_sp_end > spannableString.length() - 1)
                    calc_sp_end = spannableString.length() - 1;
                if ((spannableString.getSpanStart(spans[i]) == calc_sp_start && spannableString.getSpanEnd(spans[i]) == calc_sp_end) || (spannableString.getSpanStart(spans[i]) == calc_sp_start && spannableString.getSpanEnd(spans[i]) == calc_sp_end + 1))
                    continue;
                spannableString.setSpan(spans[i], calc_sp_start, calc_sp_end, flags);
            }
            start_offset = end_offset;
            String text_to_form = "<head><meta charset=\"utf-8\"><body style='text-align: justify;'></head>" + Html.toHtml(spannableString);
            AhoCorasick tree = new AhoCorasick();
            ArrayList<int[]> found_matches = new ArrayList<int[]>();
            int[] note_found = new int[2];
            ArrayList<String> note_ids = new ArrayList<String>();
            boolean ahref_found = false;
            String note_begin = "<a href=\"";
            String note_end = "\">";
            tree.add(note_begin.getBytes(), note_begin);
            tree.add(note_end.getBytes(), note_end);
            tree.prepare();
            Iterator<SearchResult> searcher = tree.search(text_to_form.getBytes());
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
                    String next_note = text_to_form.substring(found_matches.get(found_matches.size() - 1)[0], found_matches.get(found_matches.size() - 1)[1] - note_end.length());
                    note_ids.add(next_note);
                }
            }
            if (!found_matches.isEmpty()) {
                for (int i = 0; i < found_matches.size(); i++) {
                    StringBuilder replace = new StringBuilder(note_begin);
                    replace.append(note_ids.get(i));
                    replace.append(note_end);
                    StringBuilder with_id = new StringBuilder(note_begin);
                    with_id.append(note_ids.get(i));
                    with_id.append("\"");
                    with_id.append(" id=\"" + note_ids.get(i));
                    with_id.append(note_end);
                    text_to_form = text_to_form.replace(replace.toString(), with_id.toString());
                }
            }
            lines_for_web.add(new Pair<String>("txt", text_to_form));
            if (stop_cycle) {
                break;
            }
        }
        if (!book_full_load) {
            synchronized (this) {
                this.lines_for_web = lines_for_web;
            }
            Message message = new Message();
            message.what = JUSTIFY_DONE;
            handler.sendMessage(message);
        } else {
            Message message = new Message();
            message.what = FULL_BOOK_JUSTIFY;
            message.obj = lines_for_web;
            handler.sendMessage(message);
        }
    }

    private class Justify extends Thread {
        private Spannable spannable;
        private List<BookBinary> binaries;


        public Justify(Spannable spannable, List<BookBinary> binaries) {
            super();
            this.spannable = spannable;
            this.binaries = binaries;
        }


        @Override
        public void run() {
            Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND);
            prepareText(spannable, binaries);
        }
    }

    private class WebFragmentPagerAdapter extends FragmentStatePagerAdapter {

        public WebFragmentPagerAdapter(FragmentManager fm) {
            super(fm);
        }

        @Override
        public Fragment getItem(int position) {
            if (!book_full_load) {
                return PageWebFragment.newInstance(lines_for_web.get(position), position, -1, fileFragment.bookContents().bookNotes(), tv_pad_left, tv_pad_top, tv_pad_right, tv_pad_bottom);
            } else {
                return PageWebFragment.newInstance(lines_for_web.get(position), position, lines_for_web.size(), fileFragment.bookContents().bookNotes(), tv_pad_left, tv_pad_top, tv_pad_right, tv_pad_bottom);
            }
        }

        @Override
        public int getCount() {
            return lines_for_web.size();
        }
    }

    private void scanBook() {
        new IntentIntegrator(this).initiateScan();
    }

    public void onActivityResult(int request, int result, Intent i) {
        IntentResult scan = IntentIntegrator.parseActivityResult(request, result, i);
        if (scan != null) {
            if (scan.getFormatName().equals("EAN_13")) {
                new BookSearch().findBook(scan.getContents());
            }
        }
    }
}
