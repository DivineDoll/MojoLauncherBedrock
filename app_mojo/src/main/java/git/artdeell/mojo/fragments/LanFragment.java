package git.artdeell.mojo.fragments;

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

import git.artdeell.mojo.MojoApplication;
import git.artdeell.mojo.R;
import git.artdeell.mojo.authenticator.accounts.Account;
import git.artdeell.mojo.authenticator.accounts.Accounts;
import git.artdeell.mojo.lan.TerracottaManager;

/**
 * "LAN" screen: host a room (Hacer LAN) or join one with a code (Entrar a LAN).
 * The networking is done by Terracotta (EasyTier), see TERRACOTTA_NOTICE.md.
 */
public class LanFragment extends Fragment implements TerracottaManager.Listener {
    public static final String TAG = "LanFragment";

    private View mHostPanel, mGuestPanel;
    private Button mHostTab, mGuestTab;
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

    private Drawable stateDrawable(int normal, int pressed) {
        StateListDrawable d = new StateListDrawable();
        d.addState(new int[]{android.R.attr.state_pressed}, ContextCompat.getDrawable(requireContext(), pressed));
        d.addState(new int[]{}, ContextCompat.getDrawable(requireContext(), normal));
        return d;
    }

    private Button button(int text, boolean primary) {
        Button b = new Button(requireContext());
        b.setText(text);
        b.setAllCaps(false);
        b.setTextColor(Color.WHITE);
        b.setTypeface(Typeface.DEFAULT_BOLD);
        b.setBackground(primary
                ? stateDrawable(R.drawable.bedrock_play_normal, R.drawable.bedrock_play_pressed)
                : stateDrawable(R.drawable.bedrock_button_normal, R.drawable.bedrock_button_pressed));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48));
        lp.topMargin = dp(8);
        b.setLayoutParams(lp);
        return b;
    }

    private TextView text(CharSequence content, int sp, boolean bold) {
        TextView t = new TextView(requireContext());
        t.setText(content);
        t.setTextColor(Color.WHITE);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        if(bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(8);
        t.setLayoutParams(lp);
        return t;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        Context context = requireContext();
        ScrollView scroll = new ScrollView(context);
        scroll.setBackgroundColor(ContextCompat.getColor(context, R.color.background_app));
        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(16), dp(16), dp(24));
        scroll.addView(root);

        root.addView(text(getString(R.string.fork_lan_title), 22, true));
        root.addView(text(getString(R.string.fork_lan_intro), 14, false));

        LinearLayout tabs = new LinearLayout(context);
        tabs.setOrientation(LinearLayout.HORIZONTAL);
        mHostTab = button(R.string.fork_lan_host_tab, false);
        mGuestTab = button(R.string.fork_lan_guest_tab, false);
        LinearLayout.LayoutParams tabLp = new LinearLayout.LayoutParams(0, dp(48), 1f);
        tabLp.topMargin = dp(12);
        tabLp.rightMargin = dp(4);
        mHostTab.setLayoutParams(tabLp);
        LinearLayout.LayoutParams tabLp2 = new LinearLayout.LayoutParams(0, dp(48), 1f);
        tabLp2.topMargin = dp(12);
        tabLp2.leftMargin = dp(4);
        mGuestTab.setLayoutParams(tabLp2);
        tabs.addView(mHostTab);
        tabs.addView(mGuestTab);
        root.addView(tabs);

        // --- Host panel ---
        LinearLayout host = new LinearLayout(context);
        host.setOrientation(LinearLayout.VERTICAL);
        host.addView(text(getString(R.string.fork_lan_host_steps), 14, false));
        Button create = button(R.string.fork_lan_host_create, true);
        host.addView(create);
        mHostStatus = text("", 15, true);
        host.addView(mHostStatus);
        mHostCode = text("", 26, true);
        mHostCode.setGravity(Gravity.CENTER);
        mHostCode.setTextIsSelectable(true);
        host.addView(mHostCode);
        mCopyCode = button(R.string.fork_lan_copy_code, false);
        host.addView(mCopyCode);
        Button hostStop = button(R.string.fork_lan_stop, false);
        host.addView(hostStop);
        mHostPanel = host;
        root.addView(host);

        // --- Guest panel ---
        LinearLayout guest = new LinearLayout(context);
        guest.setOrientation(LinearLayout.VERTICAL);
        guest.addView(text(getString(R.string.fork_lan_guest_steps), 14, false));
        mCodeInput = new EditText(context);
        mCodeInput.setHint(R.string.fork_lan_code_hint);
        mCodeInput.setHintTextColor(Color.LTGRAY);
        mCodeInput.setTextColor(Color.WHITE);
        mCodeInput.setSingleLine(true);
        mCodeInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        guest.addView(mCodeInput);
        Button join = button(R.string.fork_lan_guest_join, true);
        guest.addView(join);
        mGuestStatus = text("", 15, true);
        guest.addView(mGuestStatus);
        mGuestAddress = text("", 22, true);
        mGuestAddress.setGravity(Gravity.CENTER);
        mGuestAddress.setTextIsSelectable(true);
        guest.addView(mGuestAddress);
        mCopyAddress = button(R.string.fork_lan_copy_address, false);
        guest.addView(mCopyAddress);
        Button guestStop = button(R.string.fork_lan_stop, false);
        guest.addView(guestStop);
        mGuestPanel = guest;
        root.addView(guest);

        TextView credit = text(getString(R.string.fork_lan_credit), 12, false);
        credit.setTextColor(Color.LTGRAY);
        root.addView(credit);

        mHostTab.setOnClickListener(v -> showPanel(true));
        mGuestTab.setOnClickListener(v -> showPanel(false));
        create.setOnClickListener(v -> startHost());
        join.setOnClickListener(v -> startGuest());
        hostStop.setOnClickListener(v -> stopAll());
        guestStop.setOnClickListener(v -> stopAll());
        mCopyCode.setOnClickListener(v -> copy(mHostCode.getText().toString(), R.string.fork_lan_copied_code));
        mCopyAddress.setOnClickListener(v -> copy(mGuestAddress.getText().toString(), R.string.fork_lan_copied_address));

        showPanel(true);
        render(TerracottaManager.get().getState());
        return scroll;
    }

    private void showPanel(boolean host) {
        mHostPanel.setVisibility(host ? View.VISIBLE : View.GONE);
        mGuestPanel.setVisibility(host ? View.GONE : View.VISIBLE);
        mHostTab.setAlpha(host ? 1f : 0.6f);
        mGuestTab.setAlpha(host ? 0.6f : 1f);
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
        mCopyCode.setVisibility(hostOk ? View.VISIBLE : View.GONE);
        mGuestStatus.setText(guest || (state != null && state.name.equals("exception")) ? description : "");
        mGuestAddress.setText(guestOk ? state.url : "");
        mCopyAddress.setVisibility(guestOk ? View.VISIBLE : View.GONE);
        if(host) showPanel(true);
        if(guest) showPanel(false);
    }
}
