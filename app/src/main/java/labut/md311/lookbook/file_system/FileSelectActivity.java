package labut.md311.lookbook.file_system;

import android.app.ListActivity;
import android.content.Intent;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FilenameFilter;

import de.greenrobot.event.EventBus;
import labut.md311.lookbook.BookDisplayAct;
import labut.md311.lookbook.R;
import labut.md311.lookbook.book_search.BookSearch;
import labut.md311.lookbook.events.SearchEvent;
import labut.md311.lookbook.options_menu.InfoDialog;
import labut.md311.lookbook.zxing.IntentIntegrator;
import labut.md311.lookbook.zxing.IntentResult;

//activity class to show the file system tree
public class FileSelectActivity extends ListActivity {
    private File[] file_list;
    private String[] file_str;
    private FileListAdapter fileListAdapter;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        openInitialView();
        Toast.makeText(getApplicationContext(), "Select a FB2 file to open.", Toast.LENGTH_LONG).show();
    }

    @Override
    protected void onResume() {
        super.onResume();
        EventBus.getDefault().register(this);
    }

    @Override
    protected void onPause() {
        EventBus.getDefault().unregister(this);
        super.onPause();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.f_menu, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.about:
                InfoDialog infoDialog = new InfoDialog();
                infoDialog.show(getFragmentManager(), "about");
                return true;
            case R.id.scan:
                scanBook();
                return true;
        }
        return super.onOptionsItemSelected(item);
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

    //Eventbus method for handling book search events
    public void onEventMainThread(SearchEvent event) {
        Uri uri = Uri.parse("https://www.google.com/search?q=" + event.bookName());
        Intent intent = new Intent(Intent.ACTION_VIEW, uri);
        startActivity(intent);
    }

    @Override
    protected void onListItemClick(ListView l, View v, int position, long id) {
        if (file_list[position].isDirectory()) {
            fileListAdapter.notifyDataSetInvalidated();
            new LoadFilesList(file_list[position]).execute((Void) null);
        } else {
            Intent intent = new Intent(getApplicationContext(), BookDisplayAct.class);
            intent.putExtra("fileName", file_list[position].getAbsolutePath());
            startActivity(intent);
        }
    }

    private void openInitialView() {
        new LoadFilesList(Environment.getExternalStorageDirectory()).execute((Void) null);
    }

    private void setupAdapter() {

        fileListAdapter = new FileListAdapter();
        setListAdapter(fileListAdapter);
    }

    class FileListAdapter extends ArrayAdapter<String> {

        public FileListAdapter() {
            super(FileSelectActivity.this, R.layout.activity_file_select, R.id.file_name, file_str);
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            View row = super.getView(position, convertView, parent);
            TextView file_name = (TextView) row.findViewById(R.id.file_name);
            if (file_list[position].isDirectory()) {
                file_name.setTypeface(Typeface.DEFAULT_BOLD);
            } else {
                file_name.setTypeface(Typeface.DEFAULT);
            }
            return row;
        }
    }

    //AsyncTask class to load list of files and directories, sort it in ascending order and show only FB2 files
    class LoadFilesList extends AsyncTask<Void, Void, Boolean> {
        File targetDir;
        File[] files;
        File[] with_root;
        boolean with_rt = false;

        public LoadFilesList(File target) {
            this.targetDir = target;
        }

        @Override
        protected Boolean doInBackground(Void... voids) {
            files = targetDir.listFiles(new FilenameFilter() {
                @Override
                public boolean accept(File file, String s) {
                    File chk_dir = new File(file + File.separator + s);
                    if (s.endsWith(".fb2") || chk_dir.isDirectory()) {
                        return true;
                    } else {
                        return false;
                    }
                }
            });
            if (files != null) {
                for (int i = 1; i < files.length; i++) {
                    for (int j = i; j > 0; j--) {
                        if (files[j].isDirectory() && !files[j - 1].isDirectory()) {
                            File x = files[j];
                            files[j] = files[j - 1];
                            files[j - 1] = x;
                        } else {
                            break;
                        }
                    }
                }
                for (int i = 1; i < files.length; i++) {
                    for (int j = i; j > 0; j--) {
                        if (files[j].getName().compareTo(files[j - 1].getName()) < 0 && ((files[j].isDirectory() && files[j - 1].isDirectory()) || (!files[j].isDirectory() && !files[j - 1].isDirectory()))) {
                            File x = files[j];
                            files[j] = files[j - 1];
                            files[j - 1] = x;
                        } else {
                            break;
                        }
                    }
                }
                if (!targetDir.equals(Environment.getExternalStorageDirectory())) {
                    with_root = new File[files.length + 1];
                    with_root[0] = targetDir.getParentFile();
                    for (int i = 0; i < files.length; i++) {
                        with_root[i + 1] = files[i];
                    }
                    with_rt = true;
                } else {
                    with_rt = false;
                }
                return true;
            } else {
                return false;
            }
        }

        @Override
        protected void onPostExecute(Boolean aBoolean) {
            if (aBoolean) {
                if (with_rt) {
                    file_list = with_root;
                    file_str = new String[file_list.length];
                    for (int i = 0; i < file_str.length; i++) {
                        file_str[i] = file_list[i].getName();
                    }
                    file_str[0] = "..";
                } else {
                    file_list = files;
                    file_str = new String[file_list.length];
                    for (int i = 0; i < file_str.length; i++) {
                        file_str[i] = file_list[i].getName();
                    }
                }
                setupAdapter();
            }
        }
    }
}
