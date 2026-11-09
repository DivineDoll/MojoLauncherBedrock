package com.divinedoll.launcher.fragments;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.StateListDrawable;
import android.os.Bundle;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.divinedoll.launcher.MojoApplication;
import com.divinedoll.launcher.R;
import com.divinedoll.launcher.authenticator.accounts.Account;
import com.divinedoll.launcher.authenticator.accounts.Accounts;
import com.divinedoll.launcher.lan.TerracottaManager;
import com.divinedoll.launcher.utils.AccentColor;
import com.divinedoll.launcher.utils.ForkUi;
import com.divinedoll.launcher.utils.McStyle;
import com.divinedoll.launcher.utils.ThemeColors;

/**
 * "LAN" screen: host a room (Hacer LAN) or join one with a code (Entrar a LAN).
 * The networking is done by Terracotta (EasyTier), see TERRACOTTA_NOTICE.md.
 */
public class LanFragment extends Fragment implements TerracottaManager.Listener {
    public static final String TAG = "LanFragment";

    private TextView mHostStatus, mHostCode, mGuestStatus, mGuestAddress;
    private Button mCopyCode, mCopyAddress;
    private EditText mCodeInput;

    private final ActivityResultLauncher<android.content.Intent> mVpnLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if(result.getResultCode() == android.app.Activity.RESULT_OK) {
                    TerracottaManager.get().startVpnService();
                } else {
                    TerracottaManager.get().rejectVpn();
                    toast(R.string.fork_lan_vpn_denied);
                }
            });

    private int dp(int v) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, getResources().getDisplayMetrics());
    }

    private void toast(int res) {
        Context c = getContext();
        if(c != null) Toast.makeText(c, res, Toast.LENGTH_LONG).show();
    }

    private Button button(int text, boolean primary) {
        Button b = ForkUi.button(requireContext(), getString(text), primary);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(44));
        lp.topMargin = dp(8);
        b.setLayoutParams(lp);
        return b;
    }

    /** heading = capitalized pixel font, otherwise the normal game font */
    private TextView text(CharSequence content, int sp, boolean heading) {
        TextView t = ForkUi.text(requireContext(), content, sp, heading);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(8);
        t.setLayoutParams(lp);
        return t;
    }

    /** A panel with an accent line on top, the way the other launcher screens look. */
    private LinearLayout card(Context context, int titleRes) {
        LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(ForkUi.box(context, ThemeColors.panel(), 0xFF111111, 1));
        View line = new View(context);
        line.setBackgroundColor(AccentColor.get());
        card.addView(line, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(3)));
        LinearLayout body = new LinearLayout(context);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(14), dp(10), dp(14), dp(14));
        card.addView(body, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        TextView title = text(getString(titleRes), 14, true);
        title.setAllCaps(true);
        ((LinearLayout.LayoutParams) title.getLayoutParams()).topMargin = 0;
        body.addView(title);
        card.setTag(body);
        return card;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        Context context = requireContext();
        ScrollView scroll = new ScrollView(context);
        scroll.setBackgroundColor(ThemeColors.background());
        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(12), dp(16), dp(20));
        scroll.addView(root);

        LinearLayout header = new LinearLayout(context);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        ImageView icon = new ImageView(context);
        icon.setImageResource(R.drawable.ic_px_lan_friends);
        header.addView(icon, new LinearLayout.LayoutParams(dp(40), dp(40)));
        TextView title = text(getString(R.string.fork_lan_title), 20, true);
        title.setAllCaps(true);
        title.setPadding(dp(10), 0, 0, 0);
        header.addView(title, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        root.addView(header);
        root.addView(text(getString(R.string.fork_lan_intro), 13, false));

        LinearLayout columns = new LinearLayout(context);
        columns.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams columnsLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        columnsLp.topMargin = dp(12);
        root.addView(columns, columnsLp);

        // --- Host card ---
        LinearLayout hostCard = card(context, R.string.fork_lan_host_tab);
        LinearLayout host = (LinearLayout) hostCard.getTag();
        host.addView(text(getString(R.string.fork_lan_host_steps), 12, false));
        Button create = button(R.string.fork_lan_host_create, true);
        host.addView(create);
        mHostStatus = text("", 13, false);
        host.addView(mHostStatus);
        mHostCode = text("", 24, true);
        mHostCode.setGravity(Gravity.CENTER);
        mHostCode.setTextIsSelectable(true);
        mHostCode.setBackground(ForkUi.box(context, Color.BLACK, 0xFF777777, 1));
        mHostCode.setPadding(dp(8), dp(8), dp(8), dp(8));
        mHostCode.setVisibility(View.GONE);
        host.addView(mHostCode);
        mCopyCode = button(R.string.fork_lan_copy_code, false);
        host.addView(mCopyCode);
        Button hostStop = button(R.string.fork_lan_stop, false);
        host.addView(hostStop);
        LinearLayout.LayoutParams hostLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        hostLp.rightMargin = dp(6);
        columns.addView(hostCard, hostLp);

        // --- Guest card ---
        LinearLayout guestCard = card(context, R.string.fork_lan_guest_tab);
        LinearLayout guest = (LinearLayout) guestCard.getTag();
        guest.addView(text(getString(R.string.fork_lan_guest_steps), 12, false));
        mCodeInput = new EditText(context);
        mCodeInput.setHint(R.string.fork_lan_code_hint);
        mCodeInput.setHintTextColor(0xFF777777);
        mCodeInput.setTextColor(Color.WHITE);
        mCodeInput.setSingleLine(true);
        mCodeInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        McStyle.text(mCodeInput);
        mCodeInput.setBackground(ForkUi.box(context, Color.BLACK, 0xFFAAAAAA, 2));
        mCodeInput.setPadding(dp(8), 0, dp(8), 0);
        LinearLayout.LayoutParams inputLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(44));
        inputLp.topMargin = dp(8);
        guest.addView(mCodeInput, inputLp);
        Button join = button(R.string.fork_lan_guest_join, true);
        guest.addView(join);
        mGuestStatus = text("", 13, false);
        guest.addView(mGuestStatus);
        mGuestAddress = text("", 20, true);
        mGuestAddress.setGravity(Gravity.CENTER);
        mGuestAddress.setTextIsSelectable(true);
        mGuestAddress.setBackground(ForkUi.box(context, Color.BLACK, 0xFF777777, 1));
        mGuestAddress.setPadding(dp(8), dp(8), dp(8), dp(8));
        mGuestAddress.setVisibility(View.GONE);
        guest.addView(mGuestAddress);
        mCopyAddress = button(R.string.fork_lan_copy_address, false);
        guest.addView(mCopyAddress);
        Button guestStop = button(R.string.fork_lan_stop, false);
        guest.addView(guestStop);
        LinearLayout.LayoutParams guestLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        guestLp.leftMargin = dp(6);
        columns.addView(guestCard, guestLp);

        TextView credit = text(getString(R.string.fork_lan_credit), 11, false);
        credit.setTextColor(0xFFAAAAAA);
        root.addView(credit);

        create.setOnClickListener(v -> startHost());
        join.setOnClickListener(v -> startGuest());
        hostStop.setOnClickListener(v -> stopAll());
        guestStop.setOnClickListener(v -> stopAll());
        mCopyCode.setOnClickListener(v -> copy(mHostCode.getText().toString(), R.string.fork_lan_copied_code));
        mCopyAddress.setOnClickListener(v -> copy(mGuestAddress.getText().toString(), R.string.fork_lan_copied_address));

        render(TerracottaManager.get().getState());
        return scroll;
    }

    private void copy(String value, int toastRes) {
        if(value.isEmpty()) return;
        ClipboardManager cm = (ClipboardManager) requireContext().getSystemService(Context.CLIPBOARD_SERVICE);
        cm.setPrimaryClip(ClipData.newPlainText("lan", value));
        toast(toastRes);
    }

    private String playerName() {
        Account account = Accounts.getCurrent();
        return account != null && account.username != null ? account.username : "Jugador";
    }

    private void startHost() {
        runCore(() -> TerracottaManager.get().startHost(playerName()), null);
    }

    private void startGuest() {
        String code = mCodeInput.getText().toString().trim();
        if(code.isEmpty()) { toast(R.string.fork_lan_code_empty); return; }
        runCore(() -> {
            if(!TerracottaManager.get().startGuest(code, playerName())) {
                throw new IllegalArgumentException(getString(R.string.fork_lan_code_invalid));
            }
        }, null);
    }

    private interface CoreCall { void run() throws Exception; }

    private void runCore(CoreCall call, Runnable after) {
        if(!TerracottaManager.isSupported()) { toast(R.string.fork_lan_needs_android8); return; }
        Context app = requireContext().getApplicationContext();
        MojoApplication.sExecutorService.execute(() -> {
            try {
                TerracottaManager manager = TerracottaManager.get();
                manager.ensureInitialized(app);
                // Make sure a previous room is closed before starting a new one
                TerracottaManager.State state = manager.getState();
                if(state != null && !state.isWaiting()) {
                    manager.stop();
                    Thread.sleep(800);
                }
                call.run();
            } catch (Throwable t) {
                android.util.Log.w(TAG, "LAN operation failed", t);
                String message = t.getMessage() != null ? t.getMessage() : t.getClass().getSimpleName();
                if(isAdded()) requireActivity().runOnUiThread(() ->
                        Toast.makeText(app, getString(R.string.fork_lan_error, message), Toast.LENGTH_LONG).show());
            }
        });
    }

    private void stopAll() {
        TerracottaManager.get().stop();
    }

    @Override
    public void onStart() {
        super.onStart();
        TerracottaManager manager = TerracottaManager.get();
        manager.setVpnPermissionHandler(intent -> {
            if(!isAdded()) return false;
            mVpnLauncher.launch(intent);
            return true;
        });
        manager.addListener(this);
        render(manager.getState());
    }

    @Override
    public void onStop() {
        TerracottaManager manager = TerracottaManager.get();
        manager.removeListener(this);
        manager.setVpnPermissionHandler(null);
        super.onStop();
    }

    @Override
    public void onStateChanged(TerracottaManager.State state) {
        render(state);
    }

    private void render(@Nullable TerracottaManager.State state) {
        if(mHostStatus == null || !isAdded()) return;
        Context c = requireContext();
        String description = TerracottaManager.describe(c, state);
        boolean hostOk = state != null && state.name.equals("host-ok") && state.room != null;
        boolean guestOk = state != null && state.name.equals("guest-ok") && state.url != null;
        boolean host = state != null && state.isHost();
        boolean guest = state != null && state.isGuest();
        mHostStatus.setText(host || (state != null && state.name.equals("exception")) ? description : "");
        mHostCode.setText(hostOk ? state.room : "");
        mHostCode.setVisibility(hostOk ? View.VISIBLE : View.GONE);
        mCopyCode.setVisibility(hostOk ? View.VISIBLE : View.GONE);
        mGuestStatus.setText(guest || (state != null && state.name.equals("exception")) ? description : "");
        mGuestAddress.setText(guestOk ? state.url : "");
        mGuestAddress.setVisibility(guestOk ? View.VISIBLE : View.GONE);
        mCopyAddress.setVisibility(guestOk ? View.VISIBLE : View.GONE);
        boolean error = state != null && state.name.equals("exception");
        int hostColor = error ? 0xFFE06060 : hostOk ? 0xFF7CD65A : Color.WHITE;
        int guestColor = error ? 0xFFE06060 : guestOk ? 0xFF7CD65A : Color.WHITE;
        mHostStatus.setTextColor(hostColor);
        mGuestStatus.setTextColor(guestColor);
    }
}
