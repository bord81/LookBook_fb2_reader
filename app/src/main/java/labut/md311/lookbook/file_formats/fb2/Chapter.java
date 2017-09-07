package labut.md311.lookbook.file_formats.fb2;

import android.text.SpannableStringBuilder;

import java.lang.ref.WeakReference;

//data class for storing text body, returns weak reference to a chunk of text, making a defensive copy
public class Chapter {

    private final SpannableStringBuilder singleChapter;

    public Chapter(SpannableStringBuilder singleChapter) {
        this.singleChapter = singleChapter;
    }

    public WeakReference<SpannableStringBuilder> chapter() {
        WeakReference<SpannableStringBuilder> sbuilder_ref;
        SpannableStringBuilder sbuilder = new SpannableStringBuilder();
        sbuilder_ref = new WeakReference<SpannableStringBuilder>(sbuilder);
        CharSequence charSequence = singleChapter.subSequence(0, singleChapter.length());
        Object[] spans = singleChapter.getSpans(0, singleChapter.length() - 1, Object.class);
        sbuilder_ref.get().insert(0, charSequence);
        int sp_start, sp_end, flags;
        for (int i = 0; i < spans.length; i++) {
            sp_start = singleChapter.getSpanStart(spans[i]);
            sp_end = singleChapter.getSpanEnd(spans[i]);
            flags = singleChapter.getSpanFlags(spans[i]);
            sbuilder_ref.get().setSpan(spans[i], sp_start, sp_end, flags);
        }
        return sbuilder_ref;
    }


}
