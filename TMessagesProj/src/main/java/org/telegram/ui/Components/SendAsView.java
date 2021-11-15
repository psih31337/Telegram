package org.telegram.ui.Components;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.animation.Animation;
import android.view.animation.ScaleAnimation;
import android.widget.EdgeEffect;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.ColorUtils;

import com.google.android.exoplayer2.util.Log;

import org.telegram.messenger.AccountInstance;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ChatObject;
import org.telegram.messenger.ImageLocation;
import org.telegram.messenger.ImageReceiver;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.UserObject;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.AvatarPreviewer;
import org.telegram.ui.Cells.GroupCreateUserCell;
import org.telegram.ui.Cells.HeaderCell;
import org.telegram.ui.Cells.UserCell;
import org.telegram.ui.ProfileActivity;

import java.lang.reflect.Field;

public class SendAsView extends FrameLayout implements NotificationCenter.NotificationCenterDelegate {
    private final int avatarRadius = AndroidUtilities.dp(21);
    private final int MAX_CHATS = 10;

    private int maxSize = 0;
    private long dialog_id = 0;
    private int currentAccount = 0;
    private long selected_id = 0;

    private LinearLayout scrollView;
    private ScrollView accsView;
    private FrameLayout window;
    private UserSpan avatar;
    private FrameLayout messageEditText;

    public SendAsView(@NonNull Context context, FrameLayout parent, FrameLayout messageEditText)  {
        super(context);
        this.messageEditText = messageEditText;

        Drawable shadowDrawable = ContextCompat.getDrawable(context, R.drawable.popup_fixed_alert).mutate();
        shadowDrawable.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_actionBarDefaultSubmenuBackground), PorterDuff.Mode.MULTIPLY));

        scrollView = new LinearLayout(context);
        scrollView.setOrientation(LinearLayout.VERTICAL);

        accsView = new ScrollView(context);
        accsView.setPadding(0, 5, 0, 0);
        accsView.setVerticalFadingEdgeEnabled(true);
        accsView.setFadingEdgeLength(10);
        accsView.setOverScrollMode(OVER_SCROLL_NEVER);
        accsView.setVerticalScrollBarEnabled(false);
        accsView.setHorizontalScrollBarEnabled(false);
        accsView.addView(scrollView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        HeaderCell header = new HeaderCell(getContext());
        header.setText(LocaleController.getString("SendMsgAs", R.string.SendMsgAs));

        window = new FrameLayout(context);
        window.addView(header, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        window.addView(accsView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.TOP, 0, 40,0,0));
        window.setBackground(shadowDrawable);
        window.setVisibility(GONE);
        parent.addView(window, LayoutHelper.createFrame(300, 400, Gravity.BOTTOM | Gravity.LEFT));

        avatar = new UserSpan(context, null);
        avatar.setOnClickListener(view -> {
            if(window.isShown()) {
                hideList();
            }else {
                showList();
            }
        });
        addView(avatar, LayoutHelper.createFrame(avatarRadius, avatarRadius, Gravity.LEFT | Gravity.BOTTOM, 7, 0, 0, 7));
    }

    int prevSize = 0;
    int prevEditSize = 0;
    public void onWindowSizeChanged(int size)
    {
        maxSize = (size-messageEditText.getHeight() - AndroidUtilities.dp(60));
        if(prevSize != size || prevEditSize != messageEditText.getHeight()) {
            prevSize = size;
            prevEditSize = messageEditText.getHeight();

            if(window.isShown()) {

                int calcSize = (AndroidUtilities.dp(60)*Math.min(scrollView.getChildCount(), 7)) + AndroidUtilities.dp(50);
                ValueAnimator anim = ValueAnimator.ofInt(window.getHeight(), Math.min(calcSize, maxSize));
                anim.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                    @Override
                    public void onAnimationUpdate(ValueAnimator valueAnimator) {
                        int val = (Integer) valueAnimator.getAnimatedValue();
                        ViewGroup.LayoutParams layoutParams = window.getLayoutParams();
                        layoutParams.height = val;
                        window.setLayoutParams(layoutParams);
                    }
                });
                anim.setDuration(150);
                anim.start();

                window.animate().translationY(-messageEditText.getHeight()).setDuration(150).setInterpolator(CubicBezierInterpolator.DEFAULT).start();
            }
        }
    }

    public void hideList()
    {
        avatar.animate().scaleY(0.01f).scaleX(0.01f).withEndAction(() -> {
            avatar.animate().scaleY(1f).scaleX(1f).setDuration(175).setInterpolator(CubicBezierInterpolator.DEFAULT).start();
            avatar.cancelDeleteAnimation();
        }).setDuration(175).setInterpolator(CubicBezierInterpolator.DEFAULT).start();

        window.setPivotY(window.getHeight());
        window.animate().alpha(0).scaleY(0).translationY(0).withEndAction(() -> {window.setVisibility(GONE);}).setDuration(250).setInterpolator(CubicBezierInterpolator.DEFAULT).start();
    }

    public void showList()
    {
        avatar.animate().scaleY(0.01f).scaleX(0.01f).withEndAction(() -> {
            avatar.animate().scaleY(1f).scaleX(1f).setDuration(175).setInterpolator(CubicBezierInterpolator.DEFAULT).start();
            avatar.startDeleteAnimation();
        }).setDuration(175).setInterpolator(CubicBezierInterpolator.DEFAULT).start();


        window.setVisibility(VISIBLE);
        window.setPivotY(window.getHeight());
        ViewGroup.LayoutParams layoutParams = window.getLayoutParams();
        layoutParams.height = Math.min((AndroidUtilities.dp(60)*Math.min(scrollView.getChildCount(), 7)) + AndroidUtilities.dp(50), maxSize);
        window.setLayoutParams(layoutParams);
        window.animate().alpha(1).scaleY(1).translationY(-messageEditText.getHeight()).setDuration(350).withEndAction(() -> {}).setInterpolator(CubicBezierInterpolator.DEFAULT).start();

    }

    public int getAvatarWidth()
    {
        return avatarRadius + 7;
    }

    public void setAvatar(int account, long dialog_id)
    {
        this.setVisibility(GONE);
        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.chatInfoDidLoad);
        currentAccount = account;
        this.dialog_id = dialog_id;

        TLRPC.Chat currentChat = MessagesController.getInstance(account).getChat(-dialog_id);
        if(currentChat == null || !(
                                    (!TextUtils.isEmpty(currentChat.username) && (currentChat.megagroup || currentChat.has_geo)) ||
                                    (currentChat.megagroup && currentChat.has_link)
                                   )
        ) {
            return;
        }
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.chatInfoDidLoad);
    }

    private void loadSendAs()
    {
        TLRPC.TL_channels_getSendAs getSendAs = new TLRPC.TL_channels_getSendAs();
        getSendAs.peer = MessagesController.getInstance(currentAccount).getInputPeer(dialog_id);
        AccountInstance.getInstance(currentAccount).getConnectionsManager().sendRequest(getSendAs, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
            if(error == null)
            {
                TLRPC.TL_channels_sendAsPeers sendAsPeers = (TLRPC.TL_channels_sendAsPeers)response;
                applyPeers(sendAsPeers);
            }
        }));
    }

    private void onSelectSendAs(long id)
    {
        TLRPC.TL_messages_saveDefaultSendAs req = new TLRPC.TL_messages_saveDefaultSendAs();
        req.peer =  MessagesController.getInstance(currentAccount).getInputPeer(dialog_id);
        req.send_as = MessagesController.getInstance(currentAccount).getInputPeer(id);

        AccountInstance.getInstance(currentAccount).getConnectionsManager().sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
            if(error == null)
            {
                selected_id = Math.abs(id);
                TLRPC.ChatFull chatFull = MessagesController.getInstance(currentAccount).getChatFull(-dialog_id);
                chatFull.default_send_as = MessagesController.getInstance(currentAccount).getPeer(id);
                chatFull.flags |= 1 << 29;
                hideList();
                updateSelected();
            }
        }));
    }

    private void setAvatarImage(TLObject currentObject)
    {
        avatar.setUserOrChat(currentObject);
    }

    private void updateSelected()
    {
        int count = scrollView.getChildCount();
        if(count == 1)
        {
            this.setVisibility(GONE);
            return;
        }
        for (int a = 0; a < count; a++) {
            View child = scrollView.getChildAt(a);
            if (child instanceof GroupCreateUserCell) {
                GroupCreateUserCell cell = ((GroupCreateUserCell) child);
                Object obj = cell.getObject();
                long id = -1;
                if(obj instanceof TLRPC.User)
                    id = ((TLRPC.User)obj).id;
                else if(obj instanceof TLRPC.Chat)
                    id = ((TLRPC.Chat)obj).id;
                if(id != -1) {
                    cell.setChecked(id == selected_id, true);
                    if(id == selected_id){
                        setAvatarImage((TLObject) obj);
                    }
                }
            }
        }
        this.setVisibility(VISIBLE);
    }

    private void applyPeers(TLRPC.TL_channels_sendAsPeers sendAsPeers)
    {
        scrollView.removeAllViews();
        for(TLRPC.User chat: sendAsPeers.users){
            GroupCreateUserCell userCell = new GroupCreateUserCell(getContext(), 2, 5, false);
            userCell.setObject(chat, chat.username, "personal account");
            userCell.setOnClickListener(view -> {
                if(accsView.isEnabled())
                    onSelectSendAs(chat.id);
            });
            scrollView.addView(userCell);
        }

        for(TLRPC.Chat chat: sendAsPeers.chats){
            if(TextUtils.isEmpty(chat.username)) continue;

            GroupCreateUserCell userCell = new GroupCreateUserCell(getContext(), 2, 5, false);
            userCell.setObject(chat, chat.title, null);
            userCell.setOnClickListener(view -> {
                if(accsView.isEnabled())
                    onSelectSendAs(-chat.id);
            });
            scrollView.addView(userCell);

            if(scrollView.getChildCount() == MAX_CHATS)
                break;
        }
        updateSelected();
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if(id == NotificationCenter.chatInfoDidLoad)
        {
            TLRPC.ChatFull chatFull = (TLRPC.ChatFull) args[0];
            if(-dialog_id == chatFull.id && chatFull.default_send_as != null)
            {
                selected_id = chatFull.default_send_as.user_id;
                if(chatFull.default_send_as.user_id == 0) {
                    selected_id = chatFull.default_send_as.channel_id;
                }

                loadSendAs();
            }
        }
    }
}
