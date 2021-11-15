package org.telegram.ui.Components;

import android.app.Activity;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.text.TextPaint;
import android.util.Log;
import android.util.SparseArray;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.ColorUtils;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.stripe.android.util.DateUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.DownloadController;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.ImageLocation;
import org.telegram.messenger.ImageReceiver;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.messenger.UserObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BackDrawable;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.SimpleTextView;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ActionBar.ThemeDescription;
import org.telegram.ui.Cells.CheckBoxCell;

import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Calendar;

public class DeleteCalendarActivity extends BaseFragment {

    FrameLayout contentView;
    Button clear;
    Paint paint;
    RecyclerListView listView;
    LinearLayoutManager layoutManager;
    TextPaint textPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    TextPaint activeTextPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    TextPaint textPaint2 = new TextPaint(Paint.ANTI_ALIAS_FLAG);

    Paint blackoutPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private long dialogId;
    private boolean loading;
    private boolean checkEnterItems;
    private boolean selectingDays = false;

    int startFromYear;
    int startFromMonth;
    int monthCount;

    CalendarAdapter adapter;
    Callback callback;

    int selectedDayStart =0;
    int selectedDayEnd =0;

    SparseArray<SparseArray<PeriodDay>> messagesByYearMounth = new SparseArray<>();
    boolean endReached;
    int lastId;
    int minMontYear;
    private boolean isOpened;
    int selectedYear;
    int selectedMonth;
    int photosVideosTypeFilter = 0;

    public static void show(BaseFragment profileActivity, long dialog_id, int date)
    {
        Bundle bundle = new Bundle();
        bundle.putLong("dialog_id", dialog_id);
        DeleteCalendarActivity calendarActivity = new DeleteCalendarActivity(bundle, date);
        profileActivity.presentFragment(calendarActivity);
    }


    public DeleteCalendarActivity(Bundle args, int selectedDate) {
        super(args);

        if (selectedDate != 0) {
            Calendar calendar = Calendar.getInstance();
            calendar.setTimeInMillis(selectedDate * 1000L);
            selectedYear = calendar.get(Calendar.YEAR);
            selectedMonth = calendar.get(Calendar.MONTH);
        }
    }

    @Override
    public boolean onFragmentCreate() {
        dialogId = getArguments().getLong("dialog_id");
        return super.onFragmentCreate();
    }

    @Override
    public View createView(Context context) {

        paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(AndroidUtilities.dp(2));

        textPaint.setTextSize(AndroidUtilities.dp(16));
        textPaint.setTextAlign(Paint.Align.CENTER);

        textPaint2.setTextSize(AndroidUtilities.dp(11));
        textPaint2.setTextAlign(Paint.Align.CENTER);
        textPaint2.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));

        activeTextPaint.setTextSize(AndroidUtilities.dp(16));
        activeTextPaint.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        activeTextPaint.setTextAlign(Paint.Align.CENTER);

        contentView = new FrameLayout(context);
        createActionBar(context);
        contentView.addView(actionBar);
        actionBar.setTitle(LocaleController.getString("Calendar", R.string.Calendar));
        actionBar.setCastShadows(false);

        listView = new RecyclerListView(context) {
            @Override
            protected void dispatchDraw(Canvas canvas) {
                super.dispatchDraw(canvas);
                checkEnterItems = false;
            }
        };
        listView.setLayoutManager(layoutManager = new LinearLayoutManager(context));
        layoutManager.setReverseLayout(true);
        listView.setAdapter(adapter = new CalendarAdapter());
        listView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                super.onScrolled(recyclerView, dx, dy);
                checkLoadNext();
            }
        });
        setCallback(new Callback() {
            @Override
            public void onDateSelected(int messageId, int startOffset, boolean longPressed) {
                if(selectingDays) {
                    //ChooseDay
                    if(startOffset == selectedDayStart || startOffset == selectedDayEnd) {
                        selectedDayStart = selectedDayEnd;
                        selectedDayEnd = 0;
                    }else if(selectedDayStart == 0 || startOffset < selectedDayStart)
                        selectedDayStart = startOffset;
                    else
                        selectedDayEnd = startOffset;

                    listView.invalidate();
                    adapter.notifyDataSetChanged();
                    Log.e("onDateSelected", selectedDayStart+" "+selectedDayEnd);
                    if(selectedDayStart != 0) {
                        clear.setEnabled(true);
                        clear.setAlpha(1f);
                    }else{
                        clear.setEnabled(false);
                        clear.setAlpha(0.75f);
                    }
                }else if(longPressed){
                    showChatPreview(startOffset);
                }
            }
        });

        FrameLayout fl = new FrameLayout(context);
        fl.setBackgroundColor(Color.parseColor("#efefef"));
        clear = new Button(context, null, android.R.attr.borderlessButtonStyle);
        clear.setBackgroundDrawable(Theme.getSelectorDrawable(!Theme.isCurrentThemeDark()));

        clear.setTextColor(Theme.getColor(Theme.key_dialogTextBlue));
        clear.setText(R.string.SelectDaysButton);

        clear.setOnClickListener(l -> {
            Log.e("DeletChatActivity", "setOnClickListener");
            if(selectingDays)
            {
                deleteHistory();
            }else{
                onSelectingOpen();
            }
        });


        fl.addView(clear, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.CENTER,1,1,1,1));
        contentView.addView(fl, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 60, Gravity.BOTTOM, 0, 0, 0, 0));

        contentView.addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, 0, 0, 36, 0, 60));

        final String[] daysOfWeek = new String[]{
                LocaleController.getString("CalendarWeekNameShortMonday", R.string.CalendarWeekNameShortMonday),
                LocaleController.getString("CalendarWeekNameShortTuesday", R.string.CalendarWeekNameShortTuesday),
                LocaleController.getString("CalendarWeekNameShortWednesday", R.string.CalendarWeekNameShortWednesday),
                LocaleController.getString("CalendarWeekNameShortThursday", R.string.CalendarWeekNameShortThursday),
                LocaleController.getString("CalendarWeekNameShortFriday", R.string.CalendarWeekNameShortFriday),
                LocaleController.getString("CalendarWeekNameShortSaturday", R.string.CalendarWeekNameShortSaturday),
                LocaleController.getString("CalendarWeekNameShortSunday", R.string.CalendarWeekNameShortSunday),
        };

        Drawable headerShadowDrawable = ContextCompat.getDrawable(context, R.drawable.header_shadow).mutate();

        View calendarSignatureView = new View(context) {

            @Override
            protected void onDraw(Canvas canvas) {
                super.onDraw(canvas);
                float xStep = getMeasuredWidth() / 7f;
                for (int i = 0; i < 7; i++) {
                    float cx = xStep * i + xStep / 2f;
                    float cy = (getMeasuredHeight() - AndroidUtilities.dp(2)) / 2f;
                    canvas.drawText(daysOfWeek[i], cx, cy + AndroidUtilities.dp(5), textPaint2);
                }
                headerShadowDrawable.setBounds(0, getMeasuredHeight() - AndroidUtilities.dp(3), getMeasuredWidth(), getMeasuredHeight());
                headerShadowDrawable.draw(canvas);
            }
        };

        contentView.addView(calendarSignatureView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 38, 0, 0, 0, 0, 0));
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                Log.e("DeletChatActivity", "setOnClickListener");
                if (id == -1) {
                    if(!selectingDays)
                    {
                        finishFragment();
                    }else{
                        onSelectingClose();
                    }
                }
            }
        });

        fragmentView = contentView;

        Calendar calendar = Calendar.getInstance();
        startFromYear = calendar.get(Calendar.YEAR);
        startFromMonth = calendar.get(Calendar.MONTH);

        if (selectedYear != 0) {
            monthCount = (startFromYear - selectedYear) * 12 + startFromMonth - selectedMonth + 1;
            layoutManager.scrollToPositionWithOffset(monthCount - 1, AndroidUtilities.dp(120));
        }
        if (monthCount < 3) {
            monthCount = 3;
        }


        loadNext();
        updateColors();
        activeTextPaint.setColor(Color.WHITE);
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        return fragmentView;
    }

    private void showChatPreview(int day_offset)
    {

    }


    private void onSelectingOpen()
    {
        selectingDays = true;
        clear.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteRedText5));
        clear.setText(R.string.ClearHistoryButton);
        actionBar.setTitle(LocaleController.getString("SelectDaysButton", R.string.SelectDaysButton));
        actionBar.setBackButtonDrawable(new BackDrawable(true));
        selectedDayEnd = 0;
        selectedDayStart = 0;
        clear.setEnabled(false);
        clear.setAlpha(0.75f);
    }

    private void onSelectingClose()
    {
        selectingDays = false;
        clear.setTextColor(Theme.getColor(Theme.key_dialogTextBlue));
        clear.setText(R.string.SelectDaysButton);
        actionBar.setTitle(LocaleController.getString("Calendar", R.string.Calendar));
        actionBar.setBackButtonDrawable(new BackDrawable(false));
        selectedDayEnd = 0;
        selectedDayStart = 0;
        clear.setEnabled(true);
        clear.setAlpha(1f);
        adapter.notifyDataSetChanged();
    }

    public boolean onlyHistory = false;
    private void deleteHistory()
    {
        TLRPC.User user = getMessagesController().getUser(dialogId);
        Activity activity = this.getParentActivity();
        AlertDialog.Builder builder = new AlertDialog.Builder(activity, getResourceProvider());
        int count  = 1;
        FrameLayout frameLayout = new FrameLayout(activity);
        CheckBoxCell cell = new CheckBoxCell(activity, 1, getResourceProvider());
        cell.setBackgroundDrawable(Theme.getSelectorDrawable(false));
        cell.setText(LocaleController.formatString("DeleteMessagesOptionAlso", R.string.DeleteMessagesOptionAlso, UserObject.getFirstName(user)), "", false, false);
        cell.setPadding(LocaleController.isRTL ? AndroidUtilities.dp(16) : AndroidUtilities.dp(8), 0, LocaleController.isRTL ? AndroidUtilities.dp(8) : AndroidUtilities.dp(16), 0);
        frameLayout.addView(cell, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 48, Gravity.TOP | Gravity.LEFT, 0, 0, 0, 0));
        cell.setOnClickListener(v -> {
            CheckBoxCell cell1 = (CheckBoxCell) v;
            onlyHistory = !onlyHistory;
            cell1.setChecked(onlyHistory, true);
        });
        builder.setView(frameLayout);
        builder.setCustomViewOffset(9);
        if (count == 1) {
            builder.setTitle(LocaleController.getString("DeleteSingleMessagesTitle", R.string.DeleteSingleMessagesTitle));
        } else {
            builder.setTitle(LocaleController.formatString("DeleteMessagesTitle", R.string.DeleteMessagesTitle, LocaleController.formatPluralString("messages", count)));
        }

        builder.setPositiveButton(LocaleController.getString("Delete", R.string.Delete), (dialogInterface, i) -> {
            TLRPC.TL_messages_deleteHistory req = new TLRPC.TL_messages_deleteHistory();
            req.peer = getMessagesController().getInputPeer(user);
            req.max_id = Integer.MAX_VALUE;
            req.just_clear = !onlyHistory;
            req.revoke = onlyHistory;
            req.min_date = selectedDayStart - (selectedDayStart % 86400);
            if(selectedDayEnd != 0)
                req.max_date = selectedDayEnd + (86400 - (selectedDayEnd % 86400));
            else
                req.max_date = req.min_date + 86400;

            req.flags |= 4 | 8;
            getConnectionsManager().sendRequest(req, (response, error) -> {
                if(error == null)
                {
                    AlertsCreator.showSimpleToast(this, "Success");
                    getMessagesController().loadFullChat(dialogId, 0, true);
                    loading = false;
                    endReached = false;
                    loadNext();
                }else{
                    Log.e("Delete", "error "+error.text+" "+error.code);
                }
            });
            onSelectingClose();
        });

        builder.setMessage(LocaleController.formatString("AreYouSureClearHistoryDays", R.string.AreYouSureClearHistoryDays, count));

        builder.setNegativeButton(LocaleController.getString("Cancel", R.string.Cancel), null);
        AlertDialog dialog = builder.create();
        this.showDialog(dialog);
        TextView button = (TextView) dialog.getButton(DialogInterface.BUTTON_POSITIVE);
        if (button != null) {
            button.setTextColor(Theme.getColor(Theme.key_dialogTextRed2));
        }
    }

    private void updateColors() {
        actionBar.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        activeTextPaint.setColor(Color.WHITE);
        textPaint.setColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        textPaint2.setColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        actionBar.setTitleColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setItemsColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText), false);
        actionBar.setItemsBackgroundColor(Theme.getColor(Theme.key_listSelector), false);
    }

    private void loadNext() {
        if (loading || endReached) {
            return;
        }
        loading = true;
        TLRPC.TL_messages_getSearchResultsCalendar req = new TLRPC.TL_messages_getSearchResultsCalendar();
        if (photosVideosTypeFilter == SharedMediaLayout.FILTER_PHOTOS_ONLY) {
            req.filter = new TLRPC.TL_inputMessagesFilterPhotos();
        } else if (photosVideosTypeFilter == SharedMediaLayout.FILTER_VIDEOS_ONLY) {
            req.filter = new TLRPC.TL_inputMessagesFilterVideo();
        } else {
            req.filter = new TLRPC.TL_inputMessagesFilterPhotoVideo();
        }

        req.peer = MessagesController.getInstance(currentAccount).getInputPeer(dialogId);
        req.offset_id = lastId;

        Calendar calendar = Calendar.getInstance();
        listView.setItemAnimator(null);
        getConnectionsManager().sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
            if (error == null) {
                TLRPC.TL_messages_searchResultsCalendar res = (TLRPC.TL_messages_searchResultsCalendar) response;

                int last_date = 0;
                for (int i = 0; i < res.periods.size(); i++) {
                    TLRPC.TL_searchResultsCalendarPeriod period = res.periods.get(i);
                    if(last_date < period.date)
                        last_date = period.date;
                    calendar.setTimeInMillis(period.date * 1000L);
                    int month = calendar.get(Calendar.YEAR) * 100 + calendar.get(Calendar.MONTH);
                    SparseArray<PeriodDay> messagesByDays = messagesByYearMounth.get(month);
                    if (messagesByDays == null) {
                        messagesByDays = new SparseArray<>();
                        messagesByYearMounth.put(month, messagesByDays);
                    }
                    PeriodDay periodDay = new PeriodDay();
                    MessageObject messageObject = new MessageObject(currentAccount, res.messages.get(i), false, false);
                    periodDay.messageObject = messageObject;
                    periodDay.startOffset = period.date;
                    int index = calendar.get(Calendar.DAY_OF_MONTH) - 1;
                    if (messagesByDays.get(index, null) == null) {
                        messagesByDays.put(index, periodDay);
                    }
                    if (month < minMontYear || minMontYear == 0) {
                        minMontYear = month;
                    }

                }

                //fill clear day
                for(int sd = res.min_date - (res.min_date%86400); sd < (int) (System.currentTimeMillis() / 1000L); sd += 86400)
                {
                    calendar.setTimeInMillis(sd * 1000L);
                    int month = calendar.get(Calendar.YEAR) * 100 + calendar.get(Calendar.MONTH);
                    SparseArray<PeriodDay> messagesByDays = messagesByYearMounth.get(month);
                    if (messagesByDays == null) {
                        messagesByDays = new SparseArray<>();
                        messagesByYearMounth.put(month, messagesByDays);
                    }

                    PeriodDay periodDay = new PeriodDay();
                    periodDay.messageObject = null;
                    periodDay.startOffset = sd;
                    int index = calendar.get(Calendar.DAY_OF_MONTH) - 1;
                    if (messagesByDays.get(index, null) == null) {
                        messagesByDays.put(index, periodDay);
                    }
                }

                loading = false;
                if (!res.messages.isEmpty()) {
                    lastId = res.messages.get(res.messages.size() - 1).id;
                    endReached = false;
                    checkLoadNext();
                } else {
                    endReached = true;
                }
                if (isOpened) {
                    checkEnterItems = true;
                }
                listView.invalidate();

                int newMonthCount = (int) ((last_date - res.min_date) / 2592000) + 2;
                adapter.notifyItemRangeChanged(0, monthCount);
                Log.e("DeleteCalendarActivity", ""+res.min_date+" "+monthCount+" "+newMonthCount);
                if (newMonthCount > monthCount) {
                    adapter.notifyItemRangeInserted(monthCount + 1, newMonthCount);
                    monthCount = newMonthCount;
                }
                if (endReached) {
                    resumeDelayedFragmentAnimation();
                }
            }
        }));
    }

    private void checkLoadNext() {
        if (loading || endReached) {
            return;
        }
        int listMinMonth = Integer.MAX_VALUE;
        for (int i = 0; i < listView.getChildCount(); i++) {
            View child = listView.getChildAt(i);
            if (child instanceof MonthView) {
                int currentMonth = ((MonthView) child).currentYear * 100 + ((MonthView) child).currentMonthInYear;
                if (currentMonth < listMinMonth) {
                    listMinMonth = currentMonth;
                }
            }
        };
        int min1 = (minMontYear / 100 * 12) + minMontYear % 100;
        int min2 = (listMinMonth / 100 * 12) + listMinMonth % 100;
        if (min1 + 3 >= min2) {
            loadNext();
        }
    }

    public class SquareButton extends Button {

        public SquareButton(Context context) {
            super(context);
        }

        @Override
        public void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            super.onMeasure(widthMeasureSpec, widthMeasureSpec);
            int width = MeasureSpec.getSize(widthMeasureSpec);
            int height = MeasureSpec.getSize(heightMeasureSpec);
            int size = width > height ? height : width;
            setMeasuredDimension(size, size); // make it square
        }
    }

    private class CalendarAdapter extends RecyclerView.Adapter {

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new RecyclerListView.Holder(new MonthView(parent.getContext()));
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            MonthView monthView = (MonthView) holder.itemView;

            int year = startFromYear - position / 12;
            int month = startFromMonth - position % 12;
            if (month < 0) {
                month += 12;
                year--;
            }
            boolean animated = monthView.currentYear == year && monthView.currentMonthInYear == month;
            monthView.setDate(year, month, messagesByYearMounth.get(year * 100 + month), animated);
        }

        @Override
        public long getItemId(int position) {
            int year = startFromYear - position / 12;
            int month = startFromMonth - position % 12;
            return year * 100L + month;
        }

        @Override
        public int getItemCount() {
            return monthCount;
        }
    }

    private class ImageDate
    {
        public ImageReceiver imageReceiver;
        public int date;
        public boolean fake;
    }

    private class MonthView extends FrameLayout {

        SimpleTextView titleView;
        int currentYear;
        int currentMonthInYear;
        int daysInMonth;
        int startDayOfWeek;
        int cellCount;
        int startMonthTime;

        SparseArray<PeriodDay> messagesByDays = new SparseArray<>();
        SparseArray<ImageDate> imagesByDays = new SparseArray<>();

        boolean attached;

        public MonthView(Context context) {
            super(context);
            setWillNotDraw(false);
            titleView = new SimpleTextView(context);
            titleView.setTextSize(15);
            titleView.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
            titleView.setGravity(Gravity.CENTER);
            titleView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
            addView(titleView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 28, 0, 0, 12, 0, 4));
        }

        public GradientDrawable getCircle() {
            GradientDrawable shape = new GradientDrawable();
            shape.setShape(GradientDrawable.OVAL);
            shape.setCornerRadii(new float[]{0, 0, 0, 0, 0, 0, 0, 0});
            shape.setColor(Theme.getColor(Theme.key_windowBackgroundWhite));
            return shape;
        }

        public void setDate(int year, int monthInYear, SparseArray<PeriodDay> messagesByDays, boolean animated) {
            boolean dateChanged = year != currentYear && monthInYear != currentMonthInYear;
            currentYear = year;
            currentMonthInYear = monthInYear;
            this.messagesByDays = messagesByDays;

            if (dateChanged) {
                if (imagesByDays != null) {
                    for (int i = 0; i < imagesByDays.size(); i++) {
                        imagesByDays.valueAt(i).imageReceiver.onDetachedFromWindow();
                        imagesByDays.valueAt(i).imageReceiver.setParentView(null);
                    }
                    imagesByDays = null;
                }
            }
            if (messagesByDays != null) {
                if (imagesByDays == null) {
                    imagesByDays = new SparseArray<>();
                }

                for (int i = 0; i < messagesByDays.size(); i++) {
                    int key = messagesByDays.keyAt(i);
                    if (imagesByDays.get(key, null) != null) {
                        continue;
                    }
                    ImageReceiver receiver = new ImageReceiver();
                    receiver.setParentView(this);
                    PeriodDay periodDay = messagesByDays.get(key);
                    MessageObject messageObject = periodDay.messageObject;
                    ImageDate img = new ImageDate();
                    img.imageReceiver = receiver;
                    img.date = periodDay.startOffset;
                    img.fake = false;
                    if (messageObject != null) {
                        if (messageObject.isVideo()) {
                            TLRPC.Document document = messageObject.getDocument();
                            TLRPC.PhotoSize thumb = FileLoader.getClosestPhotoSizeWithSize(document.thumbs, 50);
                            TLRPC.PhotoSize qualityThumb = FileLoader.getClosestPhotoSizeWithSize(document.thumbs, 320);
                            if (thumb == qualityThumb) {
                                qualityThumb = null;
                            }
                            if (thumb != null) {
                                if (messageObject.strippedThumb != null) {
                                    receiver.setImage(ImageLocation.getForDocument(qualityThumb, document), "44_44", messageObject.strippedThumb, null, messageObject, 0);
                                } else {
                                    receiver.setImage(ImageLocation.getForDocument(qualityThumb, document), "44_44", ImageLocation.getForDocument(thumb, document), "b", (String) null, messageObject, 0);
                                }
                            }
                        } else if (messageObject.messageOwner.media instanceof TLRPC.TL_messageMediaPhoto && messageObject.messageOwner.media.photo != null && !messageObject.photoThumbs.isEmpty()) {
                            TLRPC.PhotoSize currentPhotoObjectThumb = FileLoader.getClosestPhotoSizeWithSize(messageObject.photoThumbs, 50);
                            TLRPC.PhotoSize currentPhotoObject = FileLoader.getClosestPhotoSizeWithSize(messageObject.photoThumbs, 320, false, currentPhotoObjectThumb, false);
                            if (messageObject.mediaExists || DownloadController.getInstance(currentAccount).canDownloadMedia(messageObject)) {
                                if (currentPhotoObject == currentPhotoObjectThumb) {
                                    currentPhotoObjectThumb = null;
                                }
                                if (messageObject.strippedThumb != null) {
                                    receiver.setImage(ImageLocation.getForObject(currentPhotoObject, messageObject.photoThumbsObject), "44_44", null, null, messageObject.strippedThumb, currentPhotoObject != null ? currentPhotoObject.size : 0, null, messageObject, messageObject.shouldEncryptPhotoOrVideo() ? 2 : 1);
                                } else {
                                    receiver.setImage(ImageLocation.getForObject(currentPhotoObject, messageObject.photoThumbsObject), "44_44", ImageLocation.getForObject(currentPhotoObjectThumb, messageObject.photoThumbsObject), "b", currentPhotoObject != null ? currentPhotoObject.size : 0, null, messageObject, messageObject.shouldEncryptPhotoOrVideo() ? 2 : 1);
                                }
                            } else {
                                if (messageObject.strippedThumb != null) {
                                    receiver.setImage(null, null, messageObject.strippedThumb, null, messageObject, 0);
                                } else {
                                    receiver.setImage(null, null, ImageLocation.getForObject(currentPhotoObjectThumb, messageObject.photoThumbsObject), "b", (String) null, messageObject, 0);
                                }
                            }
                        }
                        receiver.setRoundRadius(AndroidUtilities.dp(22));
                        imagesByDays.put(key, img);
                    }else {
                        img.fake = true;
                        receiver.setImageBitmap(getCircle());
                        receiver.setRoundRadius(AndroidUtilities.dp(22));
                        imagesByDays.put(key, img);
                    }
                }
            }

            YearMonth yearMonthObject = YearMonth.of(year, monthInYear + 1);
            daysInMonth = yearMonthObject.lengthOfMonth();

            Calendar calendar = Calendar.getInstance();
            calendar.set(year, monthInYear, 0);
            startDayOfWeek = (calendar.get(Calendar.DAY_OF_WEEK) + 6) % 7;
            startMonthTime= (int) (calendar.getTimeInMillis() / 1000L);

            int totalColumns = daysInMonth + startDayOfWeek;
            cellCount = (int) (totalColumns / 7f) + (totalColumns % 7 == 0 ? 0 : 1);
            calendar.set(year, monthInYear + 1, 0);
            titleView.setText(LocaleController.formatYearMont(calendar.getTimeInMillis() / 1000, true));
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(cellCount * (44 + 8) + 44), MeasureSpec.EXACTLY));
        }

        boolean pressed;
        float pressedX;
        float pressedY;

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            boolean long_pressed = false;
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                pressed = true;
                pressedX = event.getX();
                pressedY = event.getY();
            } else if (event.getAction() == MotionEvent.ACTION_UP) {
                if (pressed) {
                    for (int i = 0; i < imagesByDays.size(); i++) {
                        if (imagesByDays.valueAt(i).imageReceiver.getDrawRegion().contains(pressedX, pressedY)) {
                            if (callback != null) {
                                PeriodDay periodDay = messagesByDays.valueAt(i);
                                callback.onDateSelected(periodDay.messageObject!=null?periodDay.messageObject.getId():0, periodDay.startOffset, long_pressed);
                                break;
                            }
                        }
                    }
                }
                pressed = false;
            } else if (event.getAction() == MotionEvent.ACTION_CANCEL) {
                pressed = false;
            }
            return pressed;
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);

            int currentCell = 0;
            int currentColumn = startDayOfWeek;

            float xStep = getMeasuredWidth() / 7f;
            float yStep = AndroidUtilities.dp(44 + 8);
            for (int i = 0; i < daysInMonth; i++) {
                float cx = xStep * currentColumn + xStep / 2f;
                float cy = yStep * currentCell + yStep / 2f + AndroidUtilities.dp(44);
                int nowTime = (int) (System.currentTimeMillis() / 1000L);
                if (nowTime < startMonthTime + (i + 1) * 86400) {
                    int oldAlpha = textPaint.getAlpha();
                    textPaint.setAlpha((int) (oldAlpha * 0.3f));
                    canvas.drawText(Integer.toString(i + 1), cx, cy + AndroidUtilities.dp(5), textPaint);
                    textPaint.setAlpha(oldAlpha);
                } else if (messagesByDays != null && messagesByDays.get(i, null) != null) {
                    float alpha = 1f;
                    if (imagesByDays.get(i) != null) {
                        int dayTime = imagesByDays.get(i).date;
                        if(selectedDayEnd != 0 && selectedDayEnd > dayTime && selectedDayStart <= dayTime )
                        {
                            float cx2 = xStep * (currentColumn + 1) + xStep / 2f;
                            canvas.save();
                            paint.setColor(Theme.getColor(Theme.key_avatar_backgroundBlue));
                            paint.setAlpha(127);
                            if(currentColumn > 0 && currentColumn < 6) {
                                paint.setStyle(Paint.Style.FILL);
                                canvas.drawRect(cx, cy - AndroidUtilities.dp(46)/2f, cx2, cy + AndroidUtilities.dp(46)/2f, paint);
                                paint.setStyle(Paint.Style.STROKE);
                            }else {
                                paint.setStyle(Paint.Style.FILL);
                                if(currentColumn == 0)
                                    canvas.drawRect(cx, cy - AndroidUtilities.dp(46)/2f, cx2, cy + AndroidUtilities.dp(46)/2f, paint);

                                paint.setColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                                canvas.drawCircle(cx, cy, (AndroidUtilities.dp(44) / 2f), paint);
                                paint.setStyle(Paint.Style.STROKE);
                                paint.setColor(Theme.getColor(Theme.key_avatar_backgroundBlue));

                                canvas.drawCircle(cx, cy, (AndroidUtilities.dp(44) / 2f), paint);
                            }
                            paint.setAlpha(255);
                            canvas.restore();
                        }

                        if (checkEnterItems && !messagesByDays.get(i).wasDrawn) {
                            messagesByDays.get(i).enterAlpha = 0f;
                            messagesByDays.get(i).startEnterDelay = (cy + getY()) / listView.getMeasuredHeight() * 150;
                        }
                        if (messagesByDays.get(i).startEnterDelay > 0) {
                            messagesByDays.get(i).startEnterDelay -= 16;
                            if (messagesByDays.get(i).startEnterDelay < 0) {
                                messagesByDays.get(i).startEnterDelay = 0;
                            } else {
                                invalidate();
                            }
                        }
                        if (messagesByDays.get(i).startEnterDelay == 0 && messagesByDays.get(i).enterAlpha != 1f) {
                            messagesByDays.get(i).enterAlpha += 16 / 220f;
                            if (messagesByDays.get(i).enterAlpha > 1f) {
                                messagesByDays.get(i).enterAlpha = 1f;
                            } else {
                                invalidate();
                            }
                        }
                        alpha = messagesByDays.get(i).enterAlpha;
                        if (alpha != 1f) {
                            canvas.save();
                            float s = 0.8f + 0.2f * alpha;
                            canvas.scale(s, s,cx, cy);
                        }
                        int sub = 0;
                        if(selectedDayEnd != 0 && selectedDayEnd > dayTime && selectedDayStart < dayTime )
                            sub = 8;

                        if(!imagesByDays.get(i).fake) {
                            imagesByDays.get(i).imageReceiver.setAlpha(messagesByDays.get(i).enterAlpha);
                            imagesByDays.get(i).imageReceiver.setImageCoords(cx - AndroidUtilities.dp(44 - sub) / 2f, cy - AndroidUtilities.dp(44 - sub) / 2f, AndroidUtilities.dp(44- sub), AndroidUtilities.dp(44-sub));
                            imagesByDays.get(i).imageReceiver.draw(canvas);
                            blackoutPaint.setColor(ColorUtils.setAlphaComponent(Color.BLACK, (int) (messagesByDays.get(i).enterAlpha * 80)));
                            canvas.drawCircle(cx, cy, AndroidUtilities.dp(44 - sub) / 2f, blackoutPaint);
                        }else{
                            imagesByDays.get(i).imageReceiver.setAlpha(0);
                            imagesByDays.get(i).imageReceiver.setImageCoords(cx - AndroidUtilities.dp(44 - sub) / 2f, cy - AndroidUtilities.dp(44 - sub) / 2f, AndroidUtilities.dp(44- sub), AndroidUtilities.dp(44-sub));
                            imagesByDays.get(i).imageReceiver.draw(canvas);
                        }

                        if(selectedDayStart == dayTime || selectedDayEnd == dayTime) {
                            if(imagesByDays.get(i).fake) {
                                paint.setStyle(Paint.Style.FILL);
                                paint.setColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                                canvas.drawCircle(cx, cy, (AndroidUtilities.dp(44) / 2f) - 1, paint);

                                paint.setColor(Theme.getColor(Theme.key_avatar_backgroundBlue));
                                canvas.drawCircle(cx, cy, (AndroidUtilities.dp(36) / 2f) + 1, paint);

                                paint.setStyle(Paint.Style.STROKE);
                            }else {
                                paint.setColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                                canvas.drawCircle(cx, cy, (AndroidUtilities.dp(44) / 2f) - 1, paint);
                            }
                            paint.setColor(Theme.getColor(Theme.key_avatar_backgroundBlue));
                            canvas.drawCircle(cx, cy, (AndroidUtilities.dp(44) / 2f) + 1, paint);
                        }
                        messagesByDays.get(i).wasDrawn = true;
                        if (alpha != 1f) {
                            canvas.restore();
                        }
                    }
                    if(!imagesByDays.get(i).fake) {
                        if (alpha != 1f) {
                            int oldAlpha = textPaint.getAlpha();
                            textPaint.setAlpha((int) (oldAlpha * (1f - alpha)));
                            canvas.drawText(Integer.toString(i + 1), cx, cy + AndroidUtilities.dp(5), textPaint);
                            textPaint.setAlpha(oldAlpha);

                            oldAlpha = textPaint.getAlpha();
                            activeTextPaint.setAlpha((int) (oldAlpha * alpha));
                            canvas.drawText(Integer.toString(i + 1), cx, cy + AndroidUtilities.dp(5), activeTextPaint);
                            activeTextPaint.setAlpha(oldAlpha);
                        } else {
                            canvas.drawText(Integer.toString(i + 1), cx, cy + AndroidUtilities.dp(5), activeTextPaint);
                        }
                    }else{
                        canvas.drawText(Integer.toString(i + 1), cx, cy + AndroidUtilities.dp(5), textPaint);
                    }
                } else {
                    canvas.drawText(Integer.toString(i + 1), cx, cy + AndroidUtilities.dp(5), textPaint);
                }

                currentColumn++;
                if (currentColumn >= 7) {
                    currentColumn = 0;
                    currentCell++;
                }
            }
        }

        @Override
        protected void onAttachedToWindow() {
            super.onAttachedToWindow();
            attached = true;
            if (imagesByDays != null) {
                for (int i = 0; i < imagesByDays.size(); i++) {
                    imagesByDays.valueAt(i).imageReceiver.onAttachedToWindow();
                }
            }
        }

        @Override
        protected void onDetachedFromWindow() {
            super.onDetachedFromWindow();
            attached = false;
            if (imagesByDays != null) {
                for (int i = 0; i < imagesByDays.size(); i++) {
                    imagesByDays.valueAt(i).imageReceiver.onDetachedFromWindow();
                }
            }
        }
    }

    public void setCallback(Callback callback) {
        this.callback = callback;
    }

    public interface Callback {
        void onDateSelected(int messageId, int startOffset, boolean longPressed);
    }

    private class PeriodDay {
        MessageObject messageObject;
        int startOffset;
        float enterAlpha = 1f;
        float startEnterDelay = 1f;
        boolean wasDrawn;
    }

    @Override
    public ArrayList<ThemeDescription> getThemeDescriptions() {

        ThemeDescription.ThemeDescriptionDelegate descriptionDelegate = new ThemeDescription.ThemeDescriptionDelegate() {
            @Override
            public void didSetColor() {
                updateColors();
            }
        };
        ArrayList<ThemeDescription> themeDescriptions = new ArrayList<>();
        new ThemeDescription(null, 0, null, null, null, descriptionDelegate, Theme.key_windowBackgroundWhite);
        new ThemeDescription(null, 0, null, null, null, descriptionDelegate, Theme.key_windowBackgroundWhiteBlackText);
        new ThemeDescription(null, 0, null, null, null, descriptionDelegate, Theme.key_listSelector);


        return super.getThemeDescriptions();
    }

    @Override
    public boolean needDelayOpenAnimation() {
        return true;
    }

    @Override
    protected void onTransitionAnimationStart(boolean isOpen, boolean backward) {
        super.onTransitionAnimationStart(isOpen, backward);
        isOpened = true;
    }
}
