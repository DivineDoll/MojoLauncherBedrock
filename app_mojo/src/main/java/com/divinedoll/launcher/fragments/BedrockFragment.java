package com.divinedoll.launcher.fragments;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;

import java.io.File;
import java.util.List;

import com.divinedoll.launcher.MojoApplication;
import com.divinedoll.launcher.R;
import com.divinedoll.launcher.Tools;
import com.divinedoll.launcher.authenticator.accounts.SkinLibrary;
import com.divinedoll.launcher.prefs.LauncherPreferences;
import com.divinedoll.launcher.utils.AccentColor;
import com.divinedoll.launcher.utils.BedrockContent;
import com.divinedoll.launcher.utils.ForkUi;
import com.divinedoll.launcher.utils.McStyle;
import com.divinedoll.launcher.utils.BannerManager;
import com.divinedoll.launcher.utils.ColorPicker;
import com.divinedoll.launcher.utils.OnlineSkins;
import com.divinedoll.launcher.utils.PixelArtEditor;
import com.divinedoll.launcher.utils.PlayColor;
import com.divinedoll.launcher.utils.ThemeColors;

/** Bedrock section laid out like the Java one: play, add-ons, textures and skins. */
public class BedrockFragment extends Fragment {
    public static final String TAG = "BedrockFragment";
    private static final int TAB_PLAY = 0, TAB_ADDONS = 1, TAB_TEXTURES = 2, TAB_SKINS = 3;

    private FrameLayout mContent;
    private FrameLayout mHeaderHost;
    private View mBanner;
    private int mTab = TAB_PLAY;
    private int mLoadId = 0;

    public BedrockFragment() {
        super(R.layout.fragment_bedrock);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        Context c = requireContext();
        view.setBackgroundColor(ThemeColors.background());
        view.findViewById(R.id.bd_sidebar_scroll).setBackgroundColor(ThemeColors.sidebar());
        mContent = view.findViewById(R.id.bd_overlay);
        mContent.setBackgroundColor(ThemeColors.background());
        mHeaderHost = view.findViewById(R.id.launcher_tabs_container);
        mBanner = view.findViewById(R.id.bd_banner);
        boolean custom = BannerManager.apply(c, (android.widget.ImageView) mBanner);
        BannerManager.setTitleVisible(view.findViewById(R.id.bd_banner_title), custom);

        TextView status = view.findViewById(R.id.bd_status);
        boolean installed = BedrockContent.isInstalled(c);
        status.setText(installed ? R.string.fork_bedrock_installed : R.string.fork_bedrock_not_installed);
        status.setTextColor(installed ? 0xFF7CD65A : 0xFFE08A5A);
        McStyle.text(status);

        Button play = view.findViewById(R.id.bd_play_button);
        McStyle.headingText(play);
        play.setBackground(PlayColor.background(c));
        play.setOnClickListener(v -> openBedrockOrStore());

        view.findViewById(R.id.bd_wiki_button).setOnClickListener(v -> Tools.openURL(requireActivity(), "https://minecraft.wiki/w/Bedrock_Edition"));
        view.findViewById(R.id.bd_java_button).setOnClickListener(v -> Tools.backToMainMenu(requireActivity()));
        view.findViewById(R.id.bd_addons_button).setOnClickListener(v -> select(TAB_ADDONS));
        view.findViewById(R.id.bd_textures_button).setOnClickListener(v -> select(TAB_TEXTURES));
        view.findViewById(R.id.bd_skins_button).setOnClickListener(v -> select(TAB_SKINS));
        view.findViewById(R.id.bd_pixel_button).setOnClickListener(v -> PixelArtEditor.show(requireContext()));
        view.findViewById(R.id.bd_import_button).setOnClickListener(v -> mPackPicker.launch("*/*"));
        view.findViewById(R.id.bd_appearance_button).setOnClickListener(v -> showAppearance());
        view.findViewById(R.id.bd_appinfo_button).setOnClickListener(v -> openAppInfo());
        view.findViewById(R.id.bd_downloads_button).setOnClickListener(v -> openDownloads());

        select(TAB_PLAY);
    }

    private final androidx.activity.result.ActivityResultLauncher<String> mPackPicker =
            registerForActivityResult(new androidx.activity.result.contract.ActivityResultContracts.GetContent(), uri -> {
                if(uri == null || !isAdded()) return;
                Context c = requireContext();
                MojoApplication.sExecutorService.execute(() -> {
                    try {
                        File file = BedrockContent.copyToCache(c, uri);
                        Tools.runOnUiThread(() -> {
                            if(isAdded() && !BedrockContent.openWithBedrock(requireContext(), file)) toast(R.string.fork_bedrock_open_failed);
                        });
                    } catch(Exception e) {
                        Tools.runOnUiThread(() -> toast(getString(R.string.fork_bedrock_error, String.valueOf(e.getMessage()))));
                    }
                });
            });

    private final androidx.activity.result.ActivityResultLauncher<String> mThemePicker =
            registerForActivityResult(new androidx.activity.result.contract.ActivityResultContracts.GetContent(), uri -> {
                if(uri == null || !isAdded()) return;
                boolean ok = com.divinedoll.launcher.utils.ThemeManager.importFrom(requireContext(), uri);
                toast(ok ? R.string.fork_theme_imported : R.string.fork_theme_error);
                if(ok) requireActivity().recreate();
            });

    private void showAppearance() {
        Context c = requireContext();
        String[] items = {getString(R.string.fork_accent_title), getString(R.string.fork_bg_color_title),
                getString(R.string.fork_sidebar_color_title), getString(R.string.fork_play_color_title),
                getString(R.string.fork_theme_menu), getString(R.string.fork_text_color_title), getString(R.string.fork_extra_menu)};
        new AlertDialog.Builder(c).setTitle(R.string.fork_appearance_title).setItems(items, (d, which) -> {
            Runnable recreate = () -> requireActivity().recreate();
            if(which == 0) AccentColor.choose(c, recreate);
            else if(which == 1) ColorPicker.show(c, R.string.fork_bg_color_title, ThemeColors.background(),
                    ThemeColors.DEFAULT_BACKGROUND, color -> { ThemeColors.set(ThemeColors.PREF_BACKGROUND, color); recreate.run(); });
            else if(which == 2) ColorPicker.show(c, R.string.fork_sidebar_color_title, ThemeColors.sidebar(),
                    ThemeColors.DEFAULT_SIDEBAR, color -> { ThemeColors.set(ThemeColors.PREF_SIDEBAR, color); recreate.run(); });
            else if(which == 3) PlayColor.choose(c, recreate);
            else if(which == 4) com.divinedoll.launcher.utils.ThemeManager.show(c, recreate, () -> mThemePicker.launch("*/*"));
            else if(which == 6) com.divinedoll.launcher.utils.ExtraOptions.show(c, null);
            else ColorPicker.show(c, R.string.fork_text_color_title, ThemeColors.text(), ThemeColors.DEFAULT_TEXT,
                    color -> { ThemeColors.set(ThemeColors.PREF_TEXT, color); recreate.run(); });
        }).show();
    }

    private void openAppInfo() {
        try {
            startActivity(new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:" + BedrockContent.BEDROCK_PACKAGE)));
        } catch(Exception e) {
            toast(R.string.fork_bedrock_missing);
        }
    }

    private void openDownloads() {
        try {
            startActivity(new Intent(android.app.DownloadManager.ACTION_VIEW_DOWNLOADS));
        } catch(Exception e) {
            toast(R.string.fork_bedrock_open_failed);
        }
    }

    /** The header has the same title and tabs as the Java one. Play shows the banner, the rest open a panel. */
    private void select(int tab) {
        mTab = tab;
        mLoadId++;
        Context c = requireContext();
        LinearLayout box = new LinearLayout(c);
        box.setOrientation(LinearLayout.VERTICAL);
        TextView title = ForkUi.text(c, getString(R.string.fork_header_bedrock), 12, true);
        title.setAllCaps(true);
        title.setLetterSpacing(0.06f);
        title.setPadding(ForkUi.dp(c, 8), ForkUi.dp(c, 6), 0, 0);
        box.addView(title);
        LinearLayout tabs = new LinearLayout(c);
        tabs.setOrientation(LinearLayout.HORIZONTAL);
        box.addView(tabs);
        mHeaderHost.removeAllViews();
        mHeaderHost.addView(box);
        int[] labels = {R.string.fork_tab_play, R.string.fork_bedrock_addons, R.string.fork_bedrock_textures, R.string.fork_skin_title};
        for(int i = 0; i < labels.length; i++) {
            final int index = i;
            TextView t = ForkUi.text(c, getString(labels[i]), 15, false);
            t.setTextColor(i == tab ? Color.WHITE : ForkUi.TAB_IDLE);
            t.setPadding(ForkUi.dp(c, 14), ForkUi.dp(c, 8), ForkUi.dp(c, 14), ForkUi.dp(c, 8));
            t.setGravity(Gravity.CENTER);
            if(i == tab) {
                android.graphics.drawable.GradientDrawable line = new android.graphics.drawable.GradientDrawable();
                line.setColor(AccentColor.get());
                android.graphics.drawable.LayerDrawable layers = new android.graphics.drawable.LayerDrawable(
                        new android.graphics.drawable.Drawable[]{line});
                layers.setLayerInset(0, ForkUi.dp(c, 8), ForkUi.dp(c, 34), ForkUi.dp(c, 8), 0);
                t.setBackground(layers);
            } else {
                t.setOnClickListener(v -> select(index));
            }
            tabs.addView(t);
        }
        mContent.removeAllViews();
        if(tab == TAB_PLAY) {
            mContent.setVisibility(View.GONE);
            return;
        }
        mContent.setVisibility(View.VISIBLE);
        int pad = ForkUi.dp(c, 12);
        View pane = tab == TAB_SKINS ? buildSkins(c) : buildBrowser(c, tab == TAB_ADDONS ? "addon" : "texture");
        pane.setPadding(pad, pad, pad, pad);
        mContent.addView(pane);
    }

    private LinearLayout card(Context c) {
        LinearLayout card = new LinearLayout(c);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(ForkUi.box(c, ThemeColors.panel(), 0, 0));
        int p = ForkUi.dp(c, 12);
        card.setPadding(p, p, p, p);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = ForkUi.dp(c, 8);
        card.setLayoutParams(lp);
        return card;
    }

    private void toast(int res) {
        if(isAdded()) Toast.makeText(requireContext(), res, Toast.LENGTH_LONG).show();
    }

    private void toast(String text) {
        if(isAdded()) Toast.makeText(requireContext(), text, Toast.LENGTH_LONG).show();
    }

    // Play

    private void openBedrockOrStore() {
        Context context = requireContext();
        Intent launch = context.getPackageManager().getLaunchIntentForPackage(BedrockContent.BEDROCK_PACKAGE);
        try {
            if(launch != null) {
                startActivity(launch);
            } else {
                try {
                    startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=" + BedrockContent.BEDROCK_PACKAGE)));
                } catch(Exception e) {
                    startActivity(new Intent(Intent.ACTION_VIEW,
                            Uri.parse("https://play.google.com/store/apps/details?id=" + BedrockContent.BEDROCK_PACKAGE)));
                }
            }
        } catch(Exception e) {
            toast(R.string.fork_bedrock_missing);
        }
    }

    // Add-ons and textures

    private View buildBrowser(Context c, String kind) {
        LinearLayout box = new LinearLayout(c);
        box.setOrientation(LinearLayout.VERTICAL);

        LinearLayout bar = ForkUi.row(c);
        EditText query = new EditText(c);
        query.setHint(R.string.fork_bedrock_search_hint);
        query.setHintTextColor(0xFF888888);
        query.setTextColor(Color.WHITE);
        query.setSingleLine();
        query.setInputType(InputType.TYPE_CLASS_TEXT);
        McStyle.text(query);
        bar.addView(query, ForkUi.weight(1f));
        Button search = ForkUi.button(c, getString(R.string.fork_bedrock_search), true);
        Button key = ForkUi.button(c, getString(R.string.fork_bedrock_key), false);
        bar.addView(search);
        bar.addView(key);
        box.addView(bar);

        TextView status = ForkUi.text(c, "", 14, false);
        status.setPadding(0, ForkUi.dp(c, 6), 0, ForkUi.dp(c, 6));
        box.addView(status);
        ScrollView scroll = new ScrollView(c);
        LinearLayout list = new LinearLayout(c);
        list.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(list);
        box.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        key.setOnClickListener(v -> askKey());
        Runnable run = () -> runSearch(kind, query.getText().toString().trim(), status, list);
        search.setOnClickListener(v -> run.run());
        query.setOnEditorActionListener((v, a, e) -> { run.run(); return true; });
        run.run();
        return box;
    }

    private void askKey() {
        Context c = requireContext();
        EditText input = new EditText(c);
        input.setHint("CurseForge API key");
        input.setSingleLine();
        input.setText(LauncherPreferences.DEFAULT_PREF.getString(BedrockContent.PREF_KEY, ""));
        new AlertDialog.Builder(c)
                .setTitle(R.string.fork_bedrock_key)
                .setMessage(R.string.fork_bedrock_key_help)
                .setView(input)
                .setNegativeButton(android.R.string.cancel, null)
                .setNeutralButton(R.string.fork_bedrock_key_get, (d, w) -> Tools.openURL(requireActivity(), "https://console.curseforge.com/"))
                .setPositiveButton(android.R.string.ok, (d, w) -> {
                    LauncherPreferences.DEFAULT_PREF.edit().putString(BedrockContent.PREF_KEY, input.getText().toString().trim()).apply();
                    select(mTab);
                })
                .show();
    }

    private void runSearch(String kind, String query, TextView status, LinearLayout list) {
        final int id = ++mLoadId;
        Context c = requireContext();
        list.removeAllViews();
        status.setText(R.string.fork_bedrock_loading);
        MojoApplication.sExecutorService.execute(() -> {
            try {
                List<BedrockContent.Item> items = BedrockContent.search(c, kind, query);
                Tools.runOnUiThread(() -> {
                    if(!isAdded() || id != mLoadId) return;
                    status.setText(items.isEmpty() ? getString(R.string.fork_bedrock_no_results) : "");
                    for(BedrockContent.Item item : items) list.addView(itemCard(c, item));
                });
            } catch(Exception e) {
                boolean noKey = "NO_KEY".equals(e.getMessage());
                Tools.runOnUiThread(() -> {
                    if(!isAdded() || id != mLoadId) return;
                    status.setText(noKey ? getString(R.string.fork_bedrock_need_key)
                            : getString(R.string.fork_bedrock_error, String.valueOf(e.getMessage())));
                });
            }
        });
    }

    private View itemCard(Context c, BedrockContent.Item item) {
        LinearLayout card = card(c);
        card.addView(ForkUi.text(c, item.name, 15, true));
        if(!item.author.isEmpty()) {
            TextView by = ForkUi.text(c, item.author + "  -  " + item.downloads + " downloads", 11, false);
            by.setTextColor(ForkUi.TAB_IDLE);
            card.addView(by);
        }
        TextView summary = ForkUi.text(c, item.summary, 12, false);
        summary.setPadding(0, ForkUi.dp(c, 4), 0, ForkUi.dp(c, 6));
        card.addView(summary);
        Button get = ForkUi.button(c, getString(R.string.fork_bedrock_install), true);
        get.setOnClickListener(v -> {
            get.setEnabled(false);
            get.setText(R.string.fork_bedrock_loading);
            MojoApplication.sExecutorService.execute(() -> {
                try {
                    File file = BedrockContent.download(c, item);
                    Tools.runOnUiThread(() -> {
                        get.setEnabled(true);
                        get.setText(R.string.fork_bedrock_install);
                        if(isAdded() && !BedrockContent.openWithBedrock(requireContext(), file)) toast(R.string.fork_bedrock_open_failed);
                    });
                } catch(Exception e) {
                    Tools.runOnUiThread(() -> {
                        get.setEnabled(true);
                        get.setText(R.string.fork_bedrock_install);
                        toast(getString(R.string.fork_bedrock_error, String.valueOf(e.getMessage())));
                    });
                }
            });
        });
        card.addView(get);
        return card;
    }

    // Skins

    private View buildSkins(Context c) {
        LinearLayout box = new LinearLayout(c);
        box.setOrientation(LinearLayout.VERTICAL);

        LinearLayout bar = ForkUi.row(c);
        EditText name = new EditText(c);
        name.setHint(R.string.fork_bedrock_skin_hint);
        name.setHintTextColor(0xFF888888);
        name.setTextColor(Color.WHITE);
        name.setSingleLine();
        McStyle.text(name);
        bar.addView(name, ForkUi.weight(1f));
        Button get = ForkUi.button(c, getString(R.string.fork_bedrock_skin_download), true);
        bar.addView(get);
        box.addView(bar);

        TextView hint = ForkUi.text(c, getString(R.string.fork_bedrock_skin_info), 12, false);
        hint.setTextColor(ForkUi.TAB_IDLE);
        hint.setPadding(0, ForkUi.dp(c, 6), 0, ForkUi.dp(c, 6));
        box.addView(hint);

        ScrollView scroll = new ScrollView(c);
        LinearLayout list = new LinearLayout(c);
        list.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(list);
        box.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        for(SkinLibrary.Entry entry : SkinLibrary.list(c)) {
            LinearLayout card = card(c);
            LinearLayout row = ForkUi.row(c);
            row.addView(ForkUi.text(c, entry.name, 14, false), ForkUi.weight(1f));
            Button install = ForkUi.button(c, getString(R.string.fork_bedrock_install), false);
            install.setOnClickListener(v -> installSkin(entry.name, SkinLibrary.load(entry)));
            row.addView(install);
            card.addView(row);
            list.addView(card);
        }

        get.setOnClickListener(v -> {
            String n = name.getText().toString().trim();
            if(n.isEmpty()) return;
            get.setEnabled(false);
            MojoApplication.sExecutorService.execute(() -> {
                try {
                    SkinLibrary.Entry entry = OnlineSkins.download(c, n);
                    Bitmap bmp = SkinLibrary.load(entry);
                    Tools.runOnUiThread(() -> {
                        get.setEnabled(true);
                        installSkin(n, bmp);
                    });
                } catch(Exception e) {
                    Tools.runOnUiThread(() -> {
                        get.setEnabled(true);
                        toast(getString(R.string.fork_bedrock_error, String.valueOf(e.getMessage())));
                    });
                }
            });
        });
        return box;
    }

    private void installSkin(String name, Bitmap skin) {
        if(skin == null) {
            toast(R.string.fork_bedrock_open_failed);
            return;
        }
        Context c = requireContext();
        MojoApplication.sExecutorService.execute(() -> {
            try {
                File pack = BedrockContent.buildSkinPack(c, name, skin, SkinLibrary.isSlim(skin));
                Tools.runOnUiThread(() -> {
                    if(isAdded() && !BedrockContent.openWithBedrock(requireContext(), pack)) toast(R.string.fork_bedrock_open_failed);
                });
            } catch(Exception e) {
                Tools.runOnUiThread(() -> toast(getString(R.string.fork_bedrock_error, String.valueOf(e.getMessage()))));
            }
        });
    }
}
